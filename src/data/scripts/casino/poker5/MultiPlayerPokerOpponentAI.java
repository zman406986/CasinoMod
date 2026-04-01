package data.scripts.casino.poker5;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import data.scripts.casino.CasinoConfig;
import data.scripts.casino.cards.Card;
import data.scripts.casino.cards.Deck;
import data.scripts.casino.cards.GameType;

public class MultiPlayerPokerOpponentAI implements PokerAI5 {
    private final Random random = new Random();
    private final MultiPlayerEquityCalculator equityCalculator = new MultiPlayerEquityCalculator();

    public enum Action { FOLD, CALL, RAISE, CHECK, BET }
    public enum Personality { TIGHT, AGGRESSIVE, CALCULATED }

    public static class AIResponse {
        public Action action;
        public int raiseAmount;
        public AIResponse(Action a, int amt) { action = a; raiseAmount = amt; }
    }

    private static class BettingAction {
        public PokerGame5.Round round;
        public Action action;
        public float betToPotRatio;
        public boolean wasInitiator;

        public BettingAction(PokerGame5.Round r, Action a, float ratio, boolean initiator) {
            this.round = r;
            this.action = a;
            this.betToPotRatio = ratio;
            this.wasInitiator = initiator;
        }
    }

    private static class BettingNarrative {
        public static final int MAX_HISTORY = 6;
        public BettingAction[] history = new BettingAction[MAX_HISTORY];
        public int historyCount = 0;
        public int historyIndex = 0;

        public float aggregateAggression = 0f;
        public boolean hasInitiated = false;
        public float avgBetRatio = 0f;
        public int betCount = 0;

        public void addAction(BettingAction action) {
            history[historyIndex] = action;
            historyIndex = (historyIndex + 1) % MAX_HISTORY;
            if (historyCount < MAX_HISTORY) historyCount++;
            recalculateMetrics();
        }

        public void recalculateMetrics() {
            if (historyCount == 0) return;

            float aggressionSum = 0f;
            float betRatioSum = 0f;
            int betCount = 0;
            hasInitiated = false;

            for (int i = 0; i < historyCount; i++) {
                BettingAction a = history[i];
                if (a.action == Action.RAISE || a.action == Action.BET) {
                    aggressionSum += 1.0f;
                    betRatioSum += a.betToPotRatio;
                    betCount++;
                    if (a.wasInitiator) hasInitiated = true;
                } else if (a.action == Action.CALL) {
                    aggressionSum += 0.3f;
                }
            }

            aggregateAggression = aggressionSum / historyCount;
            avgBetRatio = betCount > 0 ? betRatioSum / betCount : 0f;
            this.betCount = betCount;
        }

        public void reset() {
            historyCount = 0;
            historyIndex = 0;
            aggregateAggression = 0f;
            hasInitiated = false;
            avgBetRatio = 0f;
            betCount = 0;
            Arrays.fill(history, null);
        }
    }

    private static class PlayerProfile {
        public float aggressionConfidence = 0f;
        public float passivityConfidence = 0f;
        public float bluffLikelihood = 0f;
        public float trapLikelihood = 0f;
        public float totalConfidence = 0f;

        public void reset() {
            aggressionConfidence = 0f;
            passivityConfidence = 0f;
            bluffLikelihood = 0f;
            trapLikelihood = 0f;
            totalConfidence = 0f;
        }

        public Personality derivePersonality() {
            if (aggressionConfidence > passivityConfidence + 0.15f) return Personality.TIGHT;
            if (passivityConfidence > aggressionConfidence + 0.15f) return Personality.AGGRESSIVE;
            return Personality.CALCULATED;
        }
    }

    private enum HandCategory {
        PREMIUM,
        STRONG,
        PLAYABLE,
        WEAK
    }

    private Position position;
    private int seatIndex;
    private Personality personality;
    private List<Card> holeCards;
    private int stack;
    private int committedThisRound;
    private int currentBet;
    private boolean isActive;
    private boolean hasActed;
    private boolean declaredAllIn;

    private final PlayerProfile humanProfile = new PlayerProfile();
    private final BettingNarrative humanNarrative = new BettingNarrative();

    private int handsPlayed = 0;
    private int totalPlayerRaises = 0;
    private int totalPlayerCalls = 0;
    private int totalPlayerFolds = 0;
    private int totalPlayerActions = 0;
    private int raisesThisRound = 0;
    private int totalPotThisRound = 0;

    private static final int RECENT_ACTION_SIZE = 10;
    private final int[] recentPlayerActions = new int[RECENT_ACTION_SIZE];
    private int recentActionIndex = 0;
    private int recentActionCount = 0;

    private static final int SHOWDOWN_HISTORY_SIZE = 10;
    private final ShowdownRecord[] showdownHistory = new ShowdownRecord[SHOWDOWN_HISTORY_SIZE];
    private int showdownHistoryIndex = 0;
    private int showdownHistoryCount = 0;

    private static class ShowdownRecord {
        public int handRankValue;
        public float lastBetRatio;
        public boolean won;
        public boolean wasBluff;
        public int recordedAtHand;

        public ShowdownRecord(int handRankValue, float lastBetRatio, boolean won, boolean wasBluff, int handNumber) {
            this.handRankValue = handRankValue;
            this.lastBetRatio = lastBetRatio;
            this.won = won;
            this.wasBluff = wasBluff;
            this.recordedAtHand = handNumber;
        }

        public float getDecayFactor(int currentHand) {
            int age = currentHand - recordedAtHand;
            return Math.max(0.3f, 1.0f - age * 0.1f);
        }
    }

    public MultiPlayerPokerOpponentAI(int seatIndex) {
        this.seatIndex = seatIndex;
        this.personality = Personality.CALCULATED;
        this.holeCards = new ArrayList<>();
        this.stack = 0;
        this.committedThisRound = 0;
        this.currentBet = 0;
        this.isActive = true;
        this.hasActed = false;
        this.declaredAllIn = false;
        Arrays.fill(recentPlayerActions, 0);
    }

    public void assignRandomPersonality() {
        int roll = random.nextInt(3);
        this.personality = switch (roll) {
            case 0 -> Personality.TIGHT;
            case 1 -> Personality.AGGRESSIVE;
            default -> Personality.CALCULATED;
        };
    }

    public void setHoleCards(List<Card> cards) {
        this.holeCards = new ArrayList<>(cards);
    }

    public void setStack(int stack) {
        this.stack = stack;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    public void setCurrentBet(int bet) {
        this.currentBet = bet;
    }

    public void setActive(boolean active) {
        this.isActive = active;
    }

    public void resetForNewHand() {
        this.holeCards.clear();
        this.committedThisRound = 0;
        this.currentBet = 0;
        this.isActive = true;
        this.hasActed = false;
        this.declaredAllIn = false;
        this.raisesThisRound = 0;
        this.humanNarrative.reset();
    }

    public void newHandStarted(Position newPosition, int potSize) {
        handsPlayed++;
        this.position = newPosition;
        this.committedThisRound = 0;
        this.raisesThisRound = 0;
        this.totalPotThisRound = potSize;
        this.humanNarrative.reset();
    }

    public void resetBettingRoundTracking(int potSize) {
        raisesThisRound = 0;
        totalPotThisRound = potSize;
        committedThisRound = 0;
    }

    public AIResponse decide(TableStateSnapshot table) {
        if (!isActive || holeCards.size() < 2) {
            return new AIResponse(Action.FOLD, 0);
        }

        int betToCall = table.getBetToCall(currentBet);
        int opponentCount = table.getActiveOpponentCount();

        if (table.communityCards.isEmpty()) {
            return preFlopDecision(table, betToCall, opponentCount);
        } else {
            return postFlopDecision(table, betToCall, opponentCount);
        }
    }

    private AIResponse preFlopDecision(TableStateSnapshot table, int betToCall, int opponentCount) {
        updateHumanProfile();

        float equity = equityCalculator.calculatePreflopEquity(holeCards, opponentCount);
        float adjustedEquity = adjustEquityForPosition(equity, PokerGame5.Round.PREFLOP);
        float positionThreshold = getPositionThreshold(PokerGame5.Round.PREFLOP);

        int bigBlind = table.pot / 6;
        float bbStack = (float) stack / Math.max(1, bigBlind);

        if (bbStack < 15 && betToCall > 0) {
            return shortStackDecision(adjustedEquity, betToCall, table.pot, bbStack);
        }

        AIResponse decision;

        if (betToCall == 0) {
            float openThreshold = positionThreshold;
            float bluffChance = position.isLatePosition() ? 0.35f : 0.20f;

            if (adjustedEquity >= openThreshold || (adjustedEquity >= positionThreshold - 0.05f && random.nextFloat() < bluffChance)) {
                int raiseAmount = calculateOpenRaiseSize(table.pot, bigBlind);
                decision = new AIResponse(Action.RAISE, raiseAmount);
            } else {
                decision = new AIResponse(Action.CHECK, 0);
            }
        } else {
            float potOdds = (float) betToCall / (table.pot + betToCall);

            if (betToCall <= bigBlind) {
                if (adjustedEquity > 0.60f && random.nextFloat() < 0.4f) {
                    int threeBetSize = bigBlind * 3;
                    threeBetSize = Math.min(threeBetSize, stack - betToCall);
                    decision = new AIResponse(Action.RAISE, Math.max(threeBetSize, CasinoConfig.POKER_AI_MIN_RAISE_VALUE));
                } else {
                    decision = new AIResponse(Action.CALL, 0);
                }
            } else {
                boolean isAllIn = betToCall >= stack * 0.9f;
                if (isAllIn) {
                    HandCategory handStrength = classifyPreflopHand(holeCards);
                    if (!shouldCallPreflopAllIn(handStrength, potOdds, bbStack)) {
                        decision = new AIResponse(Action.FOLD, 0);
                    } else {
                        decision = new AIResponse(Action.CALL, 0);
                    }
                } else {
                    float evCall = calculateCallEV(adjustedEquity, table.pot, betToCall);
                    float evFold = -committedThisRound;

                    if (adjustedEquity < positionThreshold && potOdds > adjustedEquity) {
                        decision = new AIResponse(Action.FOLD, 0);
                    } else if (adjustedEquity > 0.60f && !shouldAvoidRaiseSpiral(stack, table.pot)) {
                        int raiseSize = calculateRaiseSize(table.pot, betToCall);
                        decision = new AIResponse(Action.RAISE, raiseSize);
                    } else if (evCall > evFold) {
                        decision = new AIResponse(Action.CALL, 0);
                    } else {
                        decision = new AIResponse(Action.FOLD, 0);
                    }
                }
            }
        }

        applyPersonalityAdjustment(decision, adjustedEquity, table.pot);

        return decision;
    }

    private AIResponse postFlopDecision(TableStateSnapshot table, int betToCall, int opponentCount) {
        updateHumanProfile();

        MultiPlayerEquityCalculator.EquityResult equityResult = equityCalculator.calculateMultiWayEquity(holeCards, table.communityCards, opponentCount);
        float trueEquity = equityResult.getTotalEquity();
        float adjustedEquity = adjustEquityForPosition(trueEquity, table.round);

        float impliedOddsBonus = calculateImpliedOddsBonus(holeCards, table.communityCards, trueEquity, opponentCount);
        adjustedEquity += impliedOddsBonus;

        boolean wetBoard = isWetBoard(table.communityCards);
        float tableStrength = estimateTableStrength(table);

        float evFold = -committedThisRound;
        float evCall = calculateCallEV(adjustedEquity, table.pot, betToCall);

        int[] raiseSizes = calculatePostFlopRaiseSizes(table.pot, wetBoard, adjustedEquity);

        float bestRaiseEV = Float.NEGATIVE_INFINITY;
        int bestRaiseSize = 0;
        boolean canRaise = betToCall < stack;

        if (canRaise) {
            for (int raiseSize : raiseSizes) {
                if (raiseSize > stack - betToCall) continue;

                float foldProb = estimateTableFoldProbability(table, raiseSize);
                float raiseEV = calculateRaiseEV(adjustedEquity, table.pot, betToCall, raiseSize, foldProb, opponentCount);

                if (adjustedEquity < 0.45f) {
                    float bluffEV = calculateBluffEV(foldProb, table.pot, raiseSize, opponentCount);
                    raiseEV = Math.max(raiseEV, bluffEV);
                }

                if (raiseEV > bestRaiseEV) {
                    bestRaiseEV = raiseEV;
                    bestRaiseSize = raiseSize;
                }
            }
        }

        if (wetBoard) {
            if (adjustedEquity > 0.70f) {
                bestRaiseEV += 0.05f * table.pot;
            } else if (adjustedEquity < 0.50f) {
                evCall -= 0.05f * table.pot;
            }
        } else {
            if (adjustedEquity < 0.45f) {
                bestRaiseEV += 0.03f * table.pot;
            }
        }

        evFold = adjustEVForPersonality(evFold, Action.FOLD);
        evCall = adjustEVForPersonality(evCall, Action.CALL);
        bestRaiseEV = adjustEVForPersonality(bestRaiseEV, Action.RAISE);

        if (shouldAvoidRaiseSpiral(stack, table.pot)) {
            bestRaiseEV = Float.NEGATIVE_INFINITY;
        }

        if (isPotCommitted(stack) && adjustedEquity > 0.40f && bestRaiseEV > evCall) {
            bestRaiseEV = evCall - 0.01f;
        }

        AIResponse finalDecision;
        if (bestRaiseEV > evCall && bestRaiseEV > evFold && bestRaiseSize > 0) {
            bestRaiseSize = Math.max(bestRaiseSize, CasinoConfig.POKER_AI_MIN_RAISE_VALUE);
            bestRaiseSize = Math.min(bestRaiseSize, stack - betToCall);
            finalDecision = new AIResponse(Action.RAISE, bestRaiseSize);
        } else if (evCall > evFold) {
            finalDecision = new AIResponse(Action.CALL, 0);
        } else {
            finalDecision = new AIResponse(Action.FOLD, 0);
        }

        if (random.nextFloat() < 0.10f) {
            AIResponse deviation = randomDeviation(adjustedEquity, betToCall, stack, table.pot);
            if (deviation.action != finalDecision.action) {
                return deviation;
            }
        }

        return finalDecision;
    }

    private float adjustEquityForPosition(float equity, PokerGame5.Round round) {
        float positionBonus = switch (position) {
            case BUTTON -> 0.05f;
            case CUT_OFF -> 0.03f;
            case SMALL_BLIND -> -0.02f;
            case BIG_BLIND -> -0.03f;
            case UTG -> -0.05f;
        };

        if (round == PokerGame5.Round.PREFLOP) {
            if (position == Position.UTG) positionBonus -= 0.03f;
            if (position == Position.BUTTON) positionBonus += 0.02f;
        }

        return Math.max(0f, Math.min(1f, equity + positionBonus));
    }

    private float getPositionThreshold(PokerGame5.Round round) {
        float base = 0.50f;

        return switch (position) {
            case UTG -> base + 0.08f;
            case BIG_BLIND -> base + 0.05f;
            case SMALL_BLIND -> base + 0.03f;
            case CUT_OFF -> base - 0.02f;
            case BUTTON -> base - 0.05f;
        };
    }

    private float estimateTableStrength(TableStateSnapshot table) {
        float aggregateStrength = 0f;
        int activeCount = 0;

        for (TableStateSnapshot.OpponentInfo opp : table.opponents) {
            if (opp.isActive && opp.seatIndex != this.seatIndex) {
                float strengthContribution = switch (opp.personality) {
                    case TIGHT -> 0.60f;
                    case AGGRESSIVE -> 0.35f;
                    case CALCULATED -> 0.45f;
                };

                if (opp.currentBet > table.currentBet * 0.5f) {
                    strengthContribution += 0.10f;
                }

                aggregateStrength += strengthContribution;
                activeCount++;
            }
        }

        if (activeCount == 0) return 0.5f;
        return aggregateStrength / activeCount;
    }

    private float estimateTableFoldProbability(TableStateSnapshot table, int betSize) {
        float foldProb = 0f;
        int activeCount = 0;

        for (TableStateSnapshot.OpponentInfo opp : table.opponents) {
            if (opp.isActive && opp.seatIndex != this.seatIndex) {
                float baseFoldProb = switch (opp.personality) {
                    case TIGHT -> 0.45f;
                    case AGGRESSIVE -> 0.20f;
                    case CALCULATED -> 0.30f;
                };

                float potOddsForOpp = (float) (betSize - opp.currentBet) / (table.pot + betSize);
                if (potOddsForOpp > 0.5f) {
                    baseFoldProb += 0.15f;
                } else if (potOddsForOpp < 0.25f) {
                    baseFoldProb -= 0.10f;
                }

                if (opp.stack < betSize) {
                    baseFoldProb *= 0.7f;
                }

                foldProb += baseFoldProb;
                activeCount++;
            }
        }

        if (activeCount == 0) return 0.5f;
        return foldProb / activeCount;
    }

    private int calculateOpenRaiseSize(int pot, int bigBlind) {
        int baseRaise = Math.min(stack / 20, stack);
        baseRaise = Math.max(bigBlind * 3, baseRaise);

        if (position.isLatePosition()) {
            baseRaise = (int) (baseRaise * 1.2f);
        } else {
            baseRaise = (int) (baseRaise * 0.85f);
        }

        baseRaise = Math.max(CasinoConfig.POKER_AI_MIN_RAISE_VALUE, baseRaise);
        if (CasinoConfig.POKER_AI_MAX_RAISE_RANDOM_ADDITION > 0) {
            baseRaise += random.nextInt(CasinoConfig.POKER_AI_MAX_RAISE_RANDOM_ADDITION);
        }

        return Math.min(baseRaise, stack);
    }

    private int calculateRaiseSize(int pot, int currentBet) {
        int raiseSize = currentBet * 3;
        raiseSize = Math.max(raiseSize, pot * 2);
        raiseSize = Math.min(raiseSize, stack - currentBet);
        return Math.max(CasinoConfig.POKER_AI_MIN_RAISE_VALUE, raiseSize);
    }

    private int[] calculatePostFlopRaiseSizes(int pot, boolean wetBoard, float equity) {
        float baseMultiplier = 1.0f;

        if (wetBoard && equity > 0.70f) {
            baseMultiplier = 1.3f;
        } else if (!wetBoard && equity < 0.45f) {
            baseMultiplier = 0.75f;
        }

        if (position.isLatePosition()) {
            baseMultiplier *= 1.1f;
        }

        return new int[] {
            (int) (pot * 0.5f * baseMultiplier),
            (int) (pot * 1.0f * baseMultiplier),
            (int) (pot * 2.0f * baseMultiplier)
        };
    }

    private AIResponse shortStackDecision(float equity, int betToCall, int pot, float bbStack) {
        float potOdds = (float) betToCall / (pot + betToCall);

        if (bbStack < 10) {
            if (equity > 0.60f || (equity > 0.50f && random.nextFloat() < 0.4f)) {
                return new AIResponse(Action.RAISE, stack);
            } else if (potOdds < 0.20f && equity > 0.45f) {
                return new AIResponse(Action.CALL, 0);
            } else {
                return new AIResponse(Action.FOLD, 0);
            }
        }

        if (equity > 0.60f || (equity > 0.50f && random.nextFloat() < 0.4f)) {
            return new AIResponse(Action.RAISE, Math.min(stack, betToCall * 3));
        } else if (equity > 0.45f) {
            return new AIResponse(Action.CALL, 0);
        } else {
            return new AIResponse(Action.FOLD, 0);
        }
    }

    private HandCategory classifyPreflopHand(List<Card> holeCards) {
        Card c1 = holeCards.get(0);
        Card c2 = holeCards.get(1);
        int v1 = Math.max(c1.rank().getValue(GameType.POKER), c2.rank().getValue(GameType.POKER));
        int v2 = Math.min(c1.rank().getValue(GameType.POKER), c2.rank().getValue(GameType.POKER));
        boolean suited = (c1.suit() == c2.suit());

        if (v1 == v2) {
            if (v1 >= 11) return HandCategory.PREMIUM;
            if (v1 >= 9) return HandCategory.STRONG;
            return HandCategory.PLAYABLE;
        }

        if (v1 == 14 && v2 == 13) return HandCategory.PREMIUM;

        if ((v1 == 14 && v2 >= 11 && suited) ||
            (v1 == 13 && v2 == 12 && suited) ||
            (v1 == 14 && v2 == 12)) return HandCategory.STRONG;

        if (suited && ((v1 == 14) || (v1 == 13 && v2 >= 10) ||
            (v1 - v2 == 1 && v1 >= 10))) return HandCategory.PLAYABLE;

        return HandCategory.WEAK;
    }

    private boolean shouldCallPreflopAllIn(HandCategory handStrength, float potOdds, float bbStack) {
        if (handStrength == HandCategory.PREMIUM) return true;

        if (handStrength == HandCategory.STRONG) {
            if (potOdds >= 0.40f) return true;
            if (bbStack < 8) return true;
            return false;
        }

        if (handStrength == HandCategory.PLAYABLE) {
            if (potOdds >= 0.45f) return true;
            if (bbStack < 5) return true;
            return false;
        }

        return false;
    }

    private float calculateCallEV(float equity, int potSize, int betToCall) {
        float winAmount = potSize + betToCall;
        return equity * winAmount - (1 - equity) * betToCall;
    }

    private float calculateRaiseEV(float equity, int potSize, int currentBet, int raiseAmount, float foldProbability, int opponentCount) {
        int totalInvestment = currentBet + raiseAmount + committedThisRound;
        int finalPot = potSize + currentBet + raiseAmount;

        float winProbabilityWhenCalled = equity;
        float adjustedFoldProb = (float) Math.pow(foldProbability, opponentCount);

        float evWhenCalled = winProbabilityWhenCalled * finalPot - (1 - winProbabilityWhenCalled) * totalInvestment;
        return adjustedFoldProb * potSize + (1 - adjustedFoldProb) * evWhenCalled;
    }

    private float calculateBluffEV(float foldProbability, int potSize, int bluffAmount, int opponentCount) {
        float adjustedFoldProb = (float) Math.pow(foldProbability, opponentCount);
        return adjustedFoldProb * potSize - (1 - adjustedFoldProb) * bluffAmount;
    }

    private float calculateImpliedOddsBonus(List<Card> holeCards, List<Card> communityCards, float currentEquity, int opponentCount) {
        int outs = countOuts(holeCards, communityCards);
        int streetsRemaining = 5 - communityCards.size();

        float drawEquity = outs * 0.02f * streetsRemaining;

        float opponentBonus = Math.min(opponentCount * 0.05f, 0.15f);

        if (currentEquity < 0.60f && drawEquity > 0.05f) {
            return Math.min((drawEquity + opponentBonus) * 0.5f, 0.15f);
        }
        return 0f;
    }

    private int countOuts(List<Card> holeCards, List<Card> communityCards) {
        int outs = 0;

        int[] suitCounts = new int[4];
        for (Card c : holeCards) suitCounts[c.suit().ordinal()]++;
        for (Card c : communityCards) suitCounts[c.suit().ordinal()]++;

        for (int count : suitCounts) {
            if (count == 4) outs += 9;
        }

        boolean[] rankPresent = new boolean[15];
        for (Card c : holeCards) rankPresent[c.rank().getValue(GameType.POKER)] = true;
        for (Card c : communityCards) rankPresent[c.rank().getValue(GameType.POKER)] = true;
        rankPresent[1] = rankPresent[14];

        int maxSeq = 0;
        int currentSeq = 0;
        for (int r = 1; r <= 14; r++) {
            if (rankPresent[r]) {
                currentSeq++;
                maxSeq = Math.max(maxSeq, currentSeq);
            } else {
                currentSeq = 0;
            }
        }

        if (maxSeq == 4) outs += 8;
        else if (maxSeq == 3) outs += 4;

        return outs;
    }

    private boolean isWetBoard(List<Card> communityCards) {
        int[] suitCounts = new int[4];
        for (Card c : communityCards) suitCounts[c.suit().ordinal()]++;

        for (int count : suitCounts) {
            if (count >= 3) return true;
        }

        boolean[] rankPresent = new boolean[15];
        for (Card c : communityCards) rankPresent[c.rank().getValue(GameType.POKER)] = true;

        int maxSeq = 0;
        int currentSeq = 0;
        for (int r = 2; r <= 14; r++) {
            if (rankPresent[r]) {
                currentSeq++;
                maxSeq = Math.max(maxSeq, currentSeq);
            } else {
                currentSeq = 0;
            }
        }

        return maxSeq >= 3;
    }

    private float adjustEVForPersonality(float ev, Action action) {
        float multiplier = switch (personality) {
            case TIGHT -> switch (action) {
                case FOLD -> 1.2f;
                case CALL -> 0.95f;
                case RAISE -> 0.85f;
                default -> 1.0f;
            };
            case AGGRESSIVE -> switch (action) {
                case FOLD -> 0.7f;
                case CALL -> 1.05f;
                case RAISE -> 1.15f;
                default -> 1.0f;
            };
            case CALCULATED -> 1.0f;
        };

        return ev * multiplier;
    }

    private void applyPersonalityAdjustment(AIResponse decision, float equity, int pot) {
        if (decision.action == Action.RAISE) {
            if (personality == Personality.AGGRESSIVE && random.nextFloat() < 0.3f) {
                decision.raiseAmount = (int) (decision.raiseAmount * 1.3f);
            } else if (personality == Personality.TIGHT && random.nextFloat() < 0.3f) {
                decision.raiseAmount = (int) (decision.raiseAmount * 0.8f);
            }
            decision.raiseAmount = Math.min(decision.raiseAmount, stack);
        }

        if (personality == Personality.AGGRESSIVE && decision.action == Action.FOLD && equity > 0.35f) {
            if (random.nextFloat() < 0.2f) {
                decision.action = Action.CALL;
            }
        }

        if (personality == Personality.TIGHT && decision.action == Action.CALL && equity < 0.45f) {
            if (random.nextFloat() < 0.2f) {
                decision.action = Action.FOLD;
            }
        }
    }

    private AIResponse randomDeviation(float equity, float potOdds, int stackSize, int potSize) {
        int deviationType = random.nextInt(4) + 1;

        switch (deviationType) {
            case 1:
                if (equity > potOdds * 0.8f) {
                    return new AIResponse(Action.CALL, 0);
                }
                break;
            case 2:
                if (equity < 0.4f && random.nextFloat() < 0.3f) {
                    int bluffRaise = (int) (potSize * 0.5f);
                    bluffRaise = Math.min(bluffRaise, stackSize);
                    return new AIResponse(Action.RAISE, bluffRaise);
                }
                break;
            case 3:
                if (equity > 0.7f) {
                    return new AIResponse(Action.CALL, 0);
                }
                break;
            case 4:
                if (equity > 0.75f) {
                    int overbet = (int) (potSize * 1.5f);
                    overbet = Math.min(overbet, stackSize);
                    return new AIResponse(Action.RAISE, overbet);
                }
                break;
        }

        if (equity > potOdds) {
            return new AIResponse(Action.CALL, 0);
        } else {
            return new AIResponse(Action.FOLD, 0);
        }
    }

    private boolean shouldAvoidRaiseSpiral(int stackSize, int potSize) {
        if (raisesThisRound >= 2) return true;

        float spr = (float) (stackSize + committedThisRound) / Math.max(1, potSize);
        if (spr < 3.0f && raisesThisRound == 1) return true;

        if (raisesThisRound == 1 && random.nextFloat() < 0.70f) return true;

        float potGrowth = (float) potSize / Math.max(1, totalPotThisRound);
        return potGrowth > 4.0f && raisesThisRound == 1;
    }

    private boolean isPotCommitted(int stackSize) {
        if (stackSize <= 0) return true;
        float committedPercent = (float) committedThisRound / (committedThisRound + stackSize);
        return committedPercent > 0.30f;
    }

    private void updateHumanProfile() {
        humanProfile.reset();
        contributeFromRecentPlayerActions();
        contributeFromShowdownHistory();
        contributeFromLongTermStats();
    }

    private void contributeFromRecentPlayerActions() {
        if (recentActionCount < 2) return;

        float weightedRaises = 0, weightedFolds = 0, weightedPassive = 0;
        float totalWeight = 0;

        for (int i = 0; i < recentActionCount; i++) {
            float decay = getRecentActionDecay(i);
            totalWeight += decay;
            int action = recentPlayerActions[i];
            if (action == 2) weightedRaises += decay;
            else if (action == 0) weightedFolds += decay;
            else if (action == 1 || action == 3) weightedPassive += decay;
        }

        if (totalWeight > 0) {
            float raiseRatio = weightedRaises / totalWeight;
            float foldRatio = weightedFolds / totalWeight;
            float passiveRatio = weightedPassive / totalWeight;

            float confidenceBoost = Math.min(recentActionCount * 0.08f, 0.5f);

            if (raiseRatio > 0.35f) {
                humanProfile.aggressionConfidence += confidenceBoost * raiseRatio;
            }
            if (foldRatio > 0.35f) {
                humanProfile.passivityConfidence += confidenceBoost * foldRatio;
            }
            if (passiveRatio > 0.5f) {
                humanProfile.passivityConfidence += confidenceBoost * passiveRatio * 0.5f;
            }

            humanProfile.totalConfidence += confidenceBoost;
        }
    }

    private float getRecentActionDecay(int index) {
        int distance = recentActionCount - 1 - index;
        return Math.max(0.4f, 1.0f - distance * 0.12f);
    }

    private void contributeFromShowdownHistory() {
        if (showdownHistoryCount == 0) return;

        float weightedBluffs = 0, weightedTraps = 0;
        float totalWeight = 0;

        for (int i = 0; i < showdownHistoryCount; i++) {
            ShowdownRecord rec = showdownHistory[i];
            float decay = rec.getDecayFactor(handsPlayed);
            totalWeight += decay;

            if (rec.wasBluff) {
                weightedBluffs += decay * 1.5f;
            } else if (rec.handRankValue >= 6 && rec.lastBetRatio < 0.3f) {
                weightedTraps += decay * 1.2f;
            }
        }

        if (totalWeight > 0) {
            float bluffRatio = weightedBluffs / totalWeight;
            float trapRatio = weightedTraps / totalWeight;

            humanProfile.bluffLikelihood = bluffRatio;
            humanProfile.trapLikelihood = trapRatio;

            if (bluffRatio > 0.15f) {
                humanProfile.aggressionConfidence += bluffRatio * 0.8f;
                humanProfile.totalConfidence += bluffRatio * 0.6f;
            }
            if (trapRatio > 0.20f) {
                humanProfile.passivityConfidence += trapRatio * 0.5f;
                humanProfile.totalConfidence += trapRatio * 0.4f;
            }

            humanProfile.totalConfidence += Math.min(showdownHistoryCount * 0.1f, 0.4f);
        }
    }

    private void contributeFromLongTermStats() {
        if (handsPlayed < 3 || totalPlayerActions < 5) return;

        float raiseRate = (float) totalPlayerRaises / totalPlayerActions;
        float confidence = Math.min(handsPlayed * 0.03f, 0.3f);

        if (raiseRate > 0.35f) {
            humanProfile.aggressionConfidence += confidence;
        } else if (raiseRate < 0.15f) {
            humanProfile.passivityConfidence += confidence;
        }

        humanProfile.totalConfidence += confidence;
    }

    public void trackPlayerAction(Action action, int betAmount, int potSize) {
        totalPlayerActions++;

        int actionType = switch (action) {
            case RAISE, BET -> 2;
            case FOLD -> 0;
            case CHECK -> 3;
            case CALL -> 1;
        };

        recentPlayerActions[recentActionIndex] = actionType;
        recentActionIndex = (recentActionIndex + 1) % RECENT_ACTION_SIZE;
        if (recentActionCount < RECENT_ACTION_SIZE) recentActionCount++;

        if (action == Action.RAISE || action == Action.BET) {
            totalPlayerRaises++;
            raisesThisRound++;
        } else if (action == Action.FOLD) {
            totalPlayerFolds++;
        } else if (action == Action.CALL) {
            totalPlayerCalls++;
        }

        float betRatio = (action == Action.RAISE || action == Action.BET) ? (float) betAmount / Math.max(1, potSize) : 0f;
        boolean wasInitiator = action == Action.RAISE && potSize <= 0;
        BettingAction ba = new BettingAction(PokerGame5.Round.PREFLOP, action, betRatio, wasInitiator);
        humanNarrative.addAction(ba);
    }

    public void trackPlayerShowdown(int handRankValue, float lastBetRatio, boolean won, boolean wasBluff) {
        showdownHistory[showdownHistoryIndex] = new ShowdownRecord(handRankValue, lastBetRatio, won, wasBluff, handsPlayed);
        showdownHistoryIndex = (showdownHistoryIndex + 1) % SHOWDOWN_HISTORY_SIZE;
        if (showdownHistoryCount < SHOWDOWN_HISTORY_SIZE) showdownHistoryCount++;

        updateHumanProfile();
    }

    public void recordAIAction(Action action) {
        if (action == Action.RAISE) {
            raisesThisRound++;
            committedThisRound += currentBet;
        }
    }

    public int getSeatIndex() { return seatIndex; }
    public Personality getPersonality() { return personality; }
    public Position getPosition() { return position; }
    public int getStack() { return stack; }
    public int getCurrentBet() { return currentBet; }
    public boolean isActive() { return isActive; }
    public boolean hasActed() { return hasActed; }
    public boolean declaredAllIn() { return declaredAllIn; }

    private PokerGame5.Action convertAction(Action internal) {
        return switch (internal) {
            case FOLD -> PokerGame5.Action.FOLD;
            case CHECK -> PokerGame5.Action.CHECK;
            case CALL -> PokerGame5.Action.CALL;
            case RAISE -> PokerGame5.Action.RAISE;
            case BET -> PokerGame5.Action.RAISE;
        };
    }

    private TableStateSnapshot createTableSnapshot(PokerGame5.PokerState5 state, int myIndex) {
        List<TableStateSnapshot.OpponentInfo> opponents = new ArrayList<>();
        for (int i = 0; i < PokerGame5.NUM_PLAYERS; i++) {
            if (i != myIndex) {
                Personality oppPersonality = Personality.CALCULATED;
                opponents.add(new TableStateSnapshot.OpponentInfo(
                    i, oppPersonality, state.stacks[i], state.bets[i],
                    !state.foldedPlayers.contains(i), state.hasActed[i], state.declaredAllIn[i]
                ));
            }
        }
        int currentBet = 0;
        for (int i = 0; i < PokerGame5.NUM_PLAYERS; i++) {
            if (!state.foldedPlayers.contains(i) && state.bets[i] > currentBet) {
                currentBet = state.bets[i];
            }
        }
        return new TableStateSnapshot(opponents, state.communityCards, state.pot, currentBet,
            state.buttonPosition, state.currentPlayerIndex, state.round);
    }

    @Override
    public PokerAI5.AIResponse decideAction(int playerIndex, PokerGame5.PokerState5 state) {
        this.stack = state.stacks[playerIndex];
        this.currentBet = state.bets[playerIndex];
        this.isActive = !state.foldedPlayers.contains(playerIndex);
        this.hasActed = state.hasActed[playerIndex];
        this.declaredAllIn = state.declaredAllIn[playerIndex];
        this.holeCards = state.hands[playerIndex] != null ? new ArrayList<>(state.hands[playerIndex]) : new ArrayList<>();
        this.position = Position.fromSeatIndex(playerIndex, state.buttonPosition);

        TableStateSnapshot snapshot = createTableSnapshot(state, playerIndex);
        AIResponse internalResponse = decide(snapshot);

        PokerGame5.Action convertedAction = convertAction(internalResponse.action);
        if (convertedAction == PokerGame5.Action.RAISE) {
            return new PokerAI5.AIResponse(convertedAction, internalResponse.raiseAmount);
        }
        return new PokerAI5.AIResponse(convertedAction);
    }

    @Override
    public void newHandStarted(int playerIndex, PokerGame5.PokerState5 state) {
        resetForNewHand();
        this.stack = state.stacks[playerIndex];
        this.holeCards = state.hands[playerIndex] != null ? new ArrayList<>(state.hands[playerIndex]) : new ArrayList<>();
        this.position = Position.fromSeatIndex(playerIndex, state.buttonPosition);
        handsPlayed++;
    }

    @Override
    public void recordAction(int playerIndex, String actionType) {
        Action internalAction = switch (actionType.toUpperCase()) {
            case "FOLD" -> Action.FOLD;
            case "CHECK" -> Action.CHECK;
            case "CALL" -> Action.CALL;
            case "RAISE", "BET" -> Action.RAISE;
            default -> Action.CALL;
        };
        trackPlayerAction(internalAction, 0, 0);
    }

    @Override
    public void reset() {
        resetForNewHand();
    }
}