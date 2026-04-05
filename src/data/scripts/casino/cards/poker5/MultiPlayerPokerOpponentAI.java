package data.scripts.casino.cards.poker5;

import java.util.ArrayList;
import java.util.List;

import data.scripts.casino.CasinoConfig;
import data.scripts.casino.cards.Card;
import data.scripts.casino.cards.pokerShared.AbstractPokerAI;
import data.scripts.casino.cards.pokerShared.PokerAIUtils;
import data.scripts.casino.cards.pokerShared.PokerAction;
import data.scripts.casino.cards.pokerShared.PokerAICommon;
import data.scripts.casino.cards.pokerShared.PokerRound;

public class MultiPlayerPokerOpponentAI extends AbstractPokerAI implements PokerAI5 {
    private static final PokerAICommon.AIResponse FOLD_RESPONSE = new PokerAICommon.AIResponse(PokerAICommon.InternalAction.FOLD, 0);
    private static final PokerAICommon.AIResponse CHECK_RESPONSE = new PokerAICommon.AIResponse(PokerAICommon.InternalAction.CHECK, 0);
    private static final PokerAICommon.AIResponse CALL_RESPONSE = new PokerAICommon.AIResponse(PokerAICommon.InternalAction.CALL, 0);

    private final MultiPlayerEquityCalculator equityCalculator = new MultiPlayerEquityCalculator();

    private final int seatIndex;
    private Position position;
    private List<Card> holeCards;
    private int stack;
    private int currentBet;
    private boolean isActive;
    private boolean declaredAllIn;

    public MultiPlayerPokerOpponentAI(int seatIndex) {
        super();
        this.seatIndex = seatIndex;
        this.holeCards = new ArrayList<>();
        this.stack = 0;
        this.currentBet = 0;
        this.isActive = true;
        this.declaredAllIn = false;
        assignRandomPersonality();
    }

    public void assignRandomPersonality() {
        int roll = random.nextInt(3);
        this.personality = switch (roll) {
            case 0 -> PokerAICommon.Personality.TIGHT;
            case 1 -> PokerAICommon.Personality.AGGRESSIVE;
            default -> PokerAICommon.Personality.CALCULATED;
        };
    }

    public void setStack(int stack) {
        this.stack = stack;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    public void setActive(boolean active) {
        this.isActive = active;
    }

    public void resetForNewHand() {
        this.holeCards.clear();
        this.committedThisRound = 0;
        this.currentBet = 0;
        this.isActive = true;
        this.declaredAllIn = false;
        this.raisesThisRound = 0;
        narrative.reset();
    }

    public void resetBettingRoundTracking(int potSize) {
        raisesThisRound = 0;
        totalPotThisRound = potSize;
        committedThisRound = 0;
    }

    @Override
    protected int getShortStackAllInAmount() {
        return stack;
    }

    public PokerAICommon.AIResponse decide(TableStateSnapshot table) {
        if (!isActive || declaredAllIn || holeCards.size() < 2) {
            return FOLD_RESPONSE;
        }

        int betToCall = table.getBetToCall(currentBet);
        int opponentCount = table.getActiveOpponentCount();

        if (table.communityCards.isEmpty()) {
            return preFlopDecision(table, betToCall, opponentCount);
        } else {
            return postFlopDecision(table, betToCall, opponentCount);
        }
    }

    private PokerAICommon.AIResponse preFlopDecision(TableStateSnapshot table, int betToCall, int opponentCount) {
        updateProfile();

        float equity = equityCalculator.calculatePreflopEquity(holeCards, opponentCount);
        float adjustedEquity = adjustEquityForPosition(equity, PokerRound.PREFLOP);
        float positionThreshold = getPositionThreshold();

        int bigBlind = table.pot / 6;
        float bbStack = bigBlind > 0 ? (float) stack / bigBlind : stack;

        if (bbStack < 15 && betToCall > 0) {
            PokerAICommon.AIResponse result = shortStackDecision(adjustedEquity, betToCall, table.pot, bbStack);
            recordBettingNarrative(PokerRound.PREFLOP, result.action, result.raiseAmount, table.pot, false);
            return result;
        }

        PokerAICommon.AIResponse decision;

        if (betToCall == 0) {
            float bluffChance = position.isLatePosition() ? 0.35f : 0.20f;

            if (adjustedEquity >= positionThreshold || (adjustedEquity >= positionThreshold - 0.05f && random.nextFloat() < bluffChance)) {
                int raiseAmount = calculateOpenRaiseSize(bigBlind);
                decision = new PokerAICommon.AIResponse(PokerAICommon.InternalAction.RAISE, raiseAmount);
            } else {
                decision = CHECK_RESPONSE;
            }
        } else {
            float potOdds = (float) betToCall / (table.pot + betToCall);

            if (betToCall <= bigBlind) {
                float threeBetThreshold = 0.60f;
                
                TableStateSnapshot.OpponentInfo raiser = table.findLikelyRaiser();
                if (raiser != null) {
                    Position raiserPosition = raiser.getPosition(table.buttonSeat);
                    if (position.isInPositionVs(raiserPosition)) {
                        threeBetThreshold = 0.55f;
                    }
                    
                    if (position.isBlind() && table.isStealAttempt(raiser)) {
                        threeBetThreshold -= CasinoConfig.POKER_BLIND_DEFENSE_THRESHOLD;
                    }
                }
                
                if (adjustedEquity > threeBetThreshold && random.nextFloat() < 0.4f) {
                    int threeBetSize = Math.max(
                        Math.min(bigBlind * 3, stack - betToCall),
                        CasinoConfig.POKER_AI_MIN_RAISE_VALUE
                    );
                    decision = new PokerAICommon.AIResponse(PokerAICommon.InternalAction.RAISE, threeBetSize);
                } else {
                    decision = CALL_RESPONSE;
                }
            } else {
                boolean isAllIn = betToCall >= stack * 0.9f;
                if (isAllIn) {
                    PokerAIUtils.HandCategory handStrength = PokerAIUtils.classifyPreflopHand(holeCards);
                    trackPlayerAllIn(true);
                    decision = shouldCallPreflopAllInExtended(handStrength, potOdds, bbStack) ? CALL_RESPONSE : FOLD_RESPONSE;
                } else {
                    trackPlayerAllIn(false);

                    float evCall = PokerAIUtils.calculateCallEV(adjustedEquity, table.pot, betToCall);
                    float evFold = -committedThisRound;

                    if (adjustedEquity < positionThreshold && potOdds > adjustedEquity) {
                        if (position.isBlind() && random.nextFloat() < CasinoConfig.POKER_BLIND_FOLD_RESISTANCE) {
                            decision = CALL_RESPONSE;
                        } else {
                            decision = FOLD_RESPONSE;
                        }
                    } else if (adjustedEquity > 0.60f && !shouldAvoidRaiseSpiral(stack, table.pot)) {
                        int raiseSize = calculateRaiseSize(table.pot, betToCall);
                        decision = new PokerAICommon.AIResponse(PokerAICommon.InternalAction.RAISE, raiseSize);
                    } else if (evCall > evFold) {
                        decision = CALL_RESPONSE;
                    } else {
                        decision = FOLD_RESPONSE;
                    }
                }
            }
        }

        applyPersonalityToRaise(decision, adjustedEquity, stack);
        boolean wasInitiator = decision.action == PokerAICommon.InternalAction.RAISE && betToCall == 0;
        recordBettingNarrative(PokerRound.PREFLOP, decision.action, decision.raiseAmount, table.pot, wasInitiator);

        return decision;
    }

    private PokerAICommon.AIResponse postFlopDecision(TableStateSnapshot table, int betToCall, int opponentCount) {
        updateProfile();

        float trueEquity = equityCalculator.calculateMultiWayEquity(holeCards, table.communityCards, opponentCount).getTotalEquity();
        float adjustedEquity = adjustEquityForPosition(trueEquity, table.round);

        float impliedOddsBonus = calculateImpliedOddsBonus(holeCards, table.communityCards, trueEquity, opponentCount);
        adjustedEquity += impliedOddsBonus;

        boolean wetBoard = PokerAIUtils.isWetBoard(table.communityCards);

        float evFold = -committedThisRound;
        float evCall = PokerAIUtils.calculateCallEV(adjustedEquity, table.pot, betToCall);

        int[] raiseSizes = calculatePostFlopRaiseSizes(table.pot, wetBoard, adjustedEquity, position.isLatePosition());

        float bestRaiseEV = Float.NEGATIVE_INFINITY;
        int bestRaiseSize = 0;
        boolean canRaise = betToCall < stack;

        if (canRaise) {
            float[] foldProbs = new float[raiseSizes.length];
            for (int i = 0; i < raiseSizes.length; i++) {
                foldProbs[i] = estimateTableFoldProbability(table, raiseSizes[i]);
            }

            for (int i = 0; i < raiseSizes.length; i++) {
                int raiseSize = raiseSizes[i];
                if (raiseSize > stack - betToCall) continue;

                float foldProb = foldProbs[i];
                float raiseEV = PokerAIUtils.calculateRaiseEVMultiWay(adjustedEquity, table.pot, betToCall, raiseSize, foldProb, opponentCount);

                if (adjustedEquity < 0.45f) {
                    float bluffEV = PokerAIUtils.calculateBluffEVMultiWay(foldProb, table.pot, raiseSize, opponentCount);
                    
                    int playersActingAfterMe = table.countPlayersActingAfter(position);
                    if (playersActingAfterMe == 0) {
                        bluffEV += 0.04f * table.pot;
                    } else if (playersActingAfterMe == 1) {
                        bluffEV += 0.02f * table.pot;
                    }
                    
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

        evFold = applyPersonalityEVAdjustment(evFold, PokerAICommon.InternalAction.FOLD);
        evCall = applyPersonalityEVAdjustment(evCall, PokerAICommon.InternalAction.CALL);
        bestRaiseEV = applyPersonalityEVAdjustment(bestRaiseEV, PokerAICommon.InternalAction.RAISE);

        if (shouldAvoidRaiseSpiral(stack, table.pot)) {
            bestRaiseEV = Float.NEGATIVE_INFINITY;
        }

        if (isPotCommitted(stack) && adjustedEquity > 0.40f && bestRaiseEV > evCall) {
            bestRaiseEV = evCall - 0.01f;
        }

        PokerAICommon.AIResponse finalDecision;
        if (bestRaiseEV > evCall && bestRaiseEV > evFold && bestRaiseSize > 0) {
            bestRaiseSize = Math.min(Math.max(bestRaiseSize, CasinoConfig.POKER_AI_MIN_RAISE_VALUE), stack - betToCall);
            finalDecision = new PokerAICommon.AIResponse(PokerAICommon.InternalAction.RAISE, bestRaiseSize);
        } else if (evCall > evFold) {
            finalDecision = CALL_RESPONSE;
        } else {
            finalDecision = FOLD_RESPONSE;
        }

        if (random.nextFloat() < 0.10f) {
            float potOdds = betToCall > 0 ? (float) betToCall / (table.pot + betToCall) : 0f;
            PokerAICommon.AIResponse deviation = randomDeviation(adjustedEquity, potOdds, stack, table.pot);
            if (deviation.action != finalDecision.action) {
                return deviation;
            }
        }

        boolean wasInitiator = finalDecision.action == PokerAICommon.InternalAction.RAISE && betToCall == 0;
        recordBettingNarrative(table.round, finalDecision.action, finalDecision.raiseAmount, table.pot, wasInitiator);

        return finalDecision;
    }

    private float adjustEquityForPosition(float equity, PokerRound round) {
        float positionBonus = switch (position) {
            case BUTTON -> 0.05f;
            case CUT_OFF -> 0.03f;
            case SMALL_BLIND -> -0.02f;
            case BIG_BLIND -> -0.03f;
            case UTG -> -0.05f;
        };

        if (round == PokerRound.PREFLOP) {
            if (position == Position.UTG) positionBonus -= 0.03f;
            if (position == Position.BUTTON) positionBonus += 0.02f;
        }

        return Math.max(0f, Math.min(1f, equity + positionBonus));
    }

    private float getPositionThreshold() {
        float base = 0.50f;

        return switch (position) {
            case UTG -> base + 0.08f;
            case BIG_BLIND -> base + 0.05f;
            case SMALL_BLIND -> base + 0.03f;
            case CUT_OFF -> base - 0.02f;
            case BUTTON -> base - 0.05f;
        };
    }

    private float estimateTableFoldProbability(TableStateSnapshot table, int betSize) {
        float foldProb = 0f;
        int activeCount = 0;

        for (TableStateSnapshot.OpponentInfo opp : table.opponents) {
            if (!opp.isActive || opp.seatIndex == this.seatIndex) continue;

            float baseFoldProb = switch (opp.personality) {
                case TIGHT -> 0.45f;
                case AGGRESSIVE -> 0.20f;
                case CALCULATED -> 0.30f;
            };

            float potOddsForOpp = (betSize - opp.currentBet) / (float) (table.pot + betSize);
            if (potOddsForOpp > 0.5f) {
                baseFoldProb += 0.15f;
            } else if (potOddsForOpp < 0.25f) {
                baseFoldProb -= 0.10f;
            }

            if (opp.stack < betSize) {
                baseFoldProb *= 0.7f;
            }

            Position oppPosition = opp.getPosition(table.buttonSeat);
            if (this.position.isInPositionVs(oppPosition)) {
                baseFoldProb += 0.08f;
            } else if (oppPosition.isInPositionVs(this.position)) {
                baseFoldProb -= 0.12f;
            }

            foldProb += baseFoldProb;
            activeCount++;
        }

        return activeCount > 0 ? foldProb / activeCount : 0.5f;
    }

    private int calculateOpenRaiseSize(int bigBlind) {
        int baseRaise = Math.max(bigBlind * 3, Math.min(stack / 20, stack));

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
        int raiseSize = Math.min(Math.max(currentBet * 3, pot * 2), stack - currentBet);
        return Math.max(CasinoConfig.POKER_AI_MIN_RAISE_VALUE, raiseSize);
    }

    public void trackPlayerAction(PokerAICommon.InternalAction action, int betAmount, int potSize) {
        totalPlayerActions++;

        int actionType = switch (action) {
            case RAISE, BET -> 2;
            case FOLD -> 0;
            case CHECK -> 3;
            case CALL -> 1;
        };

        trackRecentAction(actionType);

        if (action == PokerAICommon.InternalAction.RAISE || action == PokerAICommon.InternalAction.BET) {
            totalPlayerRaises++;
            raisesThisRound++;
        } else if (action == PokerAICommon.InternalAction.FOLD) {
            totalPlayerFolds++;
        } else if (action == PokerAICommon.InternalAction.CALL) {
            totalPlayerCalls++;
        }

        float betRatio = (action == PokerAICommon.InternalAction.RAISE || action == PokerAICommon.InternalAction.BET) 
            ? (float) betAmount / Math.max(1, potSize) : 0f;
        boolean wasInitiator = action == PokerAICommon.InternalAction.RAISE && potSize <= 0;
        PokerAICommon.BettingAction ba = new PokerAICommon.BettingAction(PokerRound.PREFLOP, action, betRatio, wasInitiator);
        narrative.addAction(ba);
    }

    public PokerAICommon.Personality getPersonality() { return personality; }
    public Position getPosition() { return position; }
    public int getStack() { return stack; }
    public boolean isActive() { return isActive; }

    private PokerAction convertAction(PokerAICommon.InternalAction internal) {
        return switch (internal) {
            case FOLD -> PokerAction.FOLD;
            case CHECK -> PokerAction.CHECK;
            case CALL -> PokerAction.CALL;
            case RAISE, BET -> PokerAction.RAISE;
        };
    }

    private TableStateSnapshot createTableSnapshot(PokerGame5.PokerState5 state, int myIndex) {
        List<TableStateSnapshot.OpponentInfo> opponents = new ArrayList<>();
        for (int i = 0; i < PokerGame5.NUM_PLAYERS; i++) {
            if (i != myIndex) {
                PokerAICommon.Personality oppPersonality = PokerAICommon.Personality.CALCULATED;
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
        this.declaredAllIn = state.declaredAllIn[playerIndex];
        this.holeCards = state.hands[playerIndex] != null ? new ArrayList<>(state.hands[playerIndex]) : new ArrayList<>();
        this.position = Position.fromSeatIndex(playerIndex, state.buttonPosition);

        TableStateSnapshot snapshot = createTableSnapshot(state, playerIndex);
        PokerAICommon.AIResponse internalResponse = decide(snapshot);

        PokerAction convertedAction = convertAction(internalResponse.action);
        if (convertedAction == PokerAction.RAISE) {
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
        PokerAICommon.InternalAction internalAction = switch (actionType.toUpperCase()) {
            case "FOLD" -> PokerAICommon.InternalAction.FOLD;
            case "CHECK" -> PokerAICommon.InternalAction.CHECK;
            case "RAISE", "BET" -> PokerAICommon.InternalAction.RAISE;
            default -> PokerAICommon.InternalAction.CALL;
        };
        trackPlayerAction(internalAction, 0, 0);
    }

    @Override
    public void reset() {
        resetForNewHand();
    }
}