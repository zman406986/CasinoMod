package data.scripts.casino.cards.poker2;

import java.util.*;

import data.scripts.casino.CasinoConfig;
import data.scripts.casino.cards.Card;
import data.scripts.casino.cards.Deck;
import data.scripts.casino.cards.GameType;
import data.scripts.casino.cards.pokerShared.AbstractPokerAI;
import data.scripts.casino.cards.pokerShared.PokerAIUtils;
import data.scripts.casino.cards.pokerShared.MonteCarloUtility;
import data.scripts.casino.cards.pokerShared.PokerHandEvaluator;
import data.scripts.casino.cards.pokerShared.PokerAICommon;
import data.scripts.casino.cards.pokerShared.PokerRound;

public class PokerOpponentAI extends AbstractPokerAI {

    private static final Card[] allCardsArray = MonteCarloUtility.getAllCards();
    private static final float AGGRESSION_WEIGHT_SUM = 2.91389f;
    private static final Map<Card, Integer> cardIndexMap = new HashMap<>();

    static {
        for (int i = 0; i < 52; i++) {
            cardIndexMap.put(allCardsArray[i], i);
        }
    }

    private float aggressionMeter = 0.5f;
    private final float[] aggressionHistory = new float[10];
    private int historyIndex = 0;
    private int playerStyle = 0;
    private int totalPlayerActions = 0;
    private int totalRaises = 0;
    private int totalCalls = 0;
    private int vpipCount = 0;
    private int pfrCount = 0;

    private boolean isInPosition = false;
    
    private int playerBetsWithoutShowdown = 0;
    private int playerBetsTotal = 0;
    private int consecutiveBluffsCaught = 0;
    private int suspiciousModeHands = 0;
    private boolean isSuspicious = false;
    private int playerCheckRaises = 0;
    private int handsSinceLastShowdown = 0;
    private int timesBluffedByPlayer = 0;
    private int largeBetsWithoutShowdown = 0;
    private boolean aiHasRaisedThisRound = false;

    public PokerOpponentAI() {
        super();
        Arrays.fill(aggressionHistory, 0.5f);
    }

    @Override
    protected int getShortStackAllInAmount() {
        return 0;
    }
    
    void addCommittedThisRound(int amount) {
        committedThisRound += amount;
    }

    @Override
    protected void updateProfile() {
        profile.reset();
        contributeFromRecentActions();
        contributeFromShowdownHistory();
        contributeFromLongTermStats();
        contributeFromAllInTracking();
        personality = profile.derivePersonality();
    }

    @Override
    protected void contributeFromLongTermStats() {
        if (handsPlayed < 3 || totalPlayerActions < 5) return;
        
        float vpip = (float) vpipCount / handsPlayed;
        float pfr = (float) pfrCount / handsPlayed;
        float af = totalCalls > 0 ? (float) totalRaises / totalCalls : totalRaises;
        
        float confidence = Math.min(handsPlayed * 0.03f, 0.3f);
        
        if (vpip < 0.25f && pfr < 0.15f) {
            profile.tightPlayLikelihood += 0.3f;
            profile.passivityConfidence += confidence;
            playerStyle = 1;
        } else if (vpip > 0.40f && af > 1.5f) {
            profile.loosePlayLikelihood += 0.3f;
            profile.aggressionConfidence += confidence;
            playerStyle = 3;
        } else {
            playerStyle = 2;
        }
        
        profile.totalConfidence += confidence;
    }
    
    private void contributeFromAllInTracking() {
        if (playerAllInOpportunities < 3) return;
        
        float allInFreq = (float) playerAllInCount / playerAllInOpportunities;
        if (allInFreq > 0.25f) {
            float confidence = Math.min(playerAllInOpportunities * 0.04f, 0.25f);
            profile.loosePlayLikelihood += allInFreq * 0.5f;
            profile.aggressionConfidence += confidence;
            profile.totalConfidence += confidence;
            playerIsAllInLoose = true;
        }
    }
    
    public void trackShowdownDetails(int handRankValue, float lastBetRatio, boolean won, boolean wasBluff) {
        trackShowdownRecord(handRankValue, lastBetRatio, won, wasBluff);
    }
    
    private void recordBettingAction(PokerRound round, PokerAICommon.InternalAction action, int betAmount, int potSize, boolean wasInitiator) {
        float betRatio = (action == PokerAICommon.InternalAction.RAISE || action == PokerAICommon.InternalAction.BET) ? (float) betAmount / Math.max(1, potSize) : 0f;
        PokerAICommon.BettingAction ba = new PokerAICommon.BettingAction(round, action, betRatio, wasInitiator);
        narrative.addAction(ba);
    }
    
    @Override
    protected float getPerceivedStrength(PokerAICommon.NarrativeType narrativeType, boolean wetBoard) {
        float baseStrength = switch (narrativeType) {
            case PASSIVE_WEAK -> 0.20f;
            case PASSIVE_DRAWING -> 0.35f;
            case HIT_THE_BOARD -> 0.55f;
            case STRONG_ALL_ALONG -> 0.70f;
            case TRAP_UNVEILED -> 0.75f;
            case POLARIZED -> 0.45f;
            case NEUTRAL -> 0.50f;
        };
        
        if (wetBoard) {
            if (narrativeType == PokerAICommon.NarrativeType.PASSIVE_DRAWING) {
                baseStrength += 0.10f;
            }
            if (narrativeType == PokerAICommon.NarrativeType.HIT_THE_BOARD) {
                baseStrength -= 0.05f;
            }
        }
        
        if (playerStyle == 3) {
            if (baseStrength > 0.60f) baseStrength -= 0.10f;
        } else if (playerStyle == 1) {
            if (baseStrength > 0.60f) baseStrength += 0.05f;
        }
        
        return Math.min(0.85f, baseStrength);
    }
    
    private PokerAICommon.DeceptionMode selectDeceptionMode(float trueEquity, float perceivedStrength, boolean wetBoard) {
        if (trueEquity > 0.70f && perceivedStrength < 0.40f) {
            return PokerAICommon.DeceptionMode.TRAP;
        }
        
        if (trueEquity > 0.65f && perceivedStrength < 0.50f && !narrative.hasInitiated) {
            return PokerAICommon.DeceptionMode.TRAP;
        }
        
        if (trueEquity < 0.35f && perceivedStrength > 0.50f) {
            if (!shouldBeSuspicious()) {
                return PokerAICommon.DeceptionMode.BLUFF_CONTINUATION;
            }
        }
        
        if (trueEquity < 0.40f && canPivotToBluff(wetBoard)) {
            return PokerAICommon.DeceptionMode.BLUFF_INITIATE;
        }
        
        if (trueEquity < 0.40f && wetBoard && narrative.type == PokerAICommon.NarrativeType.PASSIVE_DRAWING) {
            return PokerAICommon.DeceptionMode.FLOAT_BLUFF;
        }
        
        return PokerAICommon.DeceptionMode.HONEST;
    }
    
    private float computeDeceptionEquity(PokerAICommon.DeceptionMode mode, float trueEquity, float perceivedStrength) {
        return switch (mode) {
            case TRAP -> perceivedStrength + 0.10f;
            case BLUFF_CONTINUATION -> perceivedStrength;
            case BLUFF_INITIATE -> 0.55f;
            case FLOAT_BLUFF -> 0.60f;
            case HONEST -> trueEquity;
        };
    }
    
    private boolean canPivotToBluff(boolean wetBoard) {
        if (narrative.aggregateAggression > 0.5f) return false;
        
        if (narrative.historyCount == 0) return true;
        
        int lastIndex = (narrative.historyIndex - 1 + PokerAICommon.BettingNarrative.MAX_HISTORY) % PokerAICommon.BettingNarrative.MAX_HISTORY;
        PokerAICommon.BettingAction last = narrative.history[lastIndex];
        
        if (last.action == PokerAICommon.InternalAction.CHECK) return true;
        
        if (last.action == PokerAICommon.InternalAction.CALL && wetBoard) return true;
        
        return false;
    }


    private String estimatePlayerRange() {
        return switch (PokerAICommon.PlayerStyle.values()[playerStyle]) {
            case PASSIVE -> "tight_range";
            case AGGRESSIVE -> "wide_range";
            case BALANCED -> "standard_range";
            default -> "random";
        };
    }

    private PokerAICommon.AIResponse randomDeviationResponse(float equity, float potOdds, int stackSize, int potSize) {
        PokerAICommon.AIResponse deviation = switch (random.nextInt(4) + 1) {
            case 1 -> equity > potOdds * 0.8f ? 
                new PokerAICommon.AIResponse(PokerAICommon.InternalAction.CALL, 0) : null;
            case 2 -> equity < 0.4f && random.nextFloat() < 0.3f ?
                new PokerAICommon.AIResponse(PokerAICommon.InternalAction.RAISE, Math.min((int)(potSize * 0.5f), stackSize)) : null;
            case 3 -> equity > 0.7f ?
                new PokerAICommon.AIResponse(PokerAICommon.InternalAction.CALL, 0) : null;
            case 4 -> equity > 0.75f ?
                new PokerAICommon.AIResponse(PokerAICommon.InternalAction.RAISE, Math.min((int)(potSize * 1.5f), stackSize)) : null;
            default -> null;
        };
        
        return deviation != null ? deviation :
            (equity > potOdds ? new PokerAICommon.AIResponse(PokerAICommon.InternalAction.CALL, 0) :
             new PokerAICommon.AIResponse(PokerAICommon.InternalAction.FOLD, 0));
    }
    
    public PokerAICommon.AIResponse decide(List<Card> holeCards, List<Card> communityCards,
                            int currentBetToCall, int potSize, int stackSize) {
        if (communityCards.isEmpty()) {
            return preFlopDecision(holeCards, currentBetToCall, potSize, stackSize);
        } else {
            return postFlopDecision(holeCards, communityCards, currentBetToCall, potSize, stackSize);
        }
    }
    
    public PokerAICommon.AIResponse decideAllInResponse(List<Card> holeCards, List<Card> communityCards,
                            int currentBetToCall, int potSize) {
        float equity = communityCards.isEmpty() ? 
            calculatePreflopEquity(holeCards) : 
            calculatePostflopEquity(holeCards, communityCards);
        
        float potOdds = (float) currentBetToCall / (potSize + currentBetToCall);
        
        if (equity > potOdds) {
            return new PokerAICommon.AIResponse(PokerAICommon.InternalAction.CALL, 0);
        } else {
            return new PokerAICommon.AIResponse(PokerAICommon.InternalAction.FOLD, 0);
        }
    }
    
    private PokerAICommon.AIResponse preFlopDecision(List<Card> holeCards, int currentBetToCall, int potSize, int stackSize) {
        updateProfile();

        float equity = calculatePreflopEquity(holeCards);

        int bigBlind = Math.max(1, potSize / 3);
        float bbStack = (float) stackSize / bigBlind;

        float positionBonus = isInPosition ? 0.05f : -0.03f;
        equity += positionBonus;

        if (bbStack < 15 && currentBetToCall > 0) {
            PokerAICommon.AIResponse shortStackResult = shortStackDecision(equity, currentBetToCall, potSize, stackSize, bbStack);
            boolean wasInitiator = false;
            recordBettingAction(PokerRound.PREFLOP, shortStackResult.action, shortStackResult.raiseAmount, potSize, wasInitiator);
            return shortStackResult;
        }

        PokerAICommon.AIResponse decision;
        if (currentBetToCall == 0) {
            float openThreshold = isInPosition ? 0.32f : 0.45f;
            float bluffChance = isInPosition ? 0.35f : 0.20f;

            if (equity >= openThreshold || (equity >= 0.30f && random.nextFloat() < bluffChance)) {
                float posMult = isInPosition ? 1.2f : 0.85f;
                int raiseAmount = Math.max(CasinoConfig.POKER_AI_MIN_RAISE_VALUE,
                    (int)(Math.max(bigBlind * 3, Math.min(stackSize / 20, stackSize)) * posMult));
                decision = new PokerAICommon.AIResponse(PokerAICommon.InternalAction.RAISE, raiseAmount);
            } else {
                decision = new PokerAICommon.AIResponse(PokerAICommon.InternalAction.CALL, 0);
            }
        } else {
            float potOdds = (float)currentBetToCall / (potSize + currentBetToCall);

            if (currentBetToCall == bigBlind) {
                if (equity > 0.60f && random.nextFloat() < 0.4f) {
                    int threeBetSize = bigBlind * 3;
                    threeBetSize = Math.min(threeBetSize, stackSize);
                    decision = new PokerAICommon.AIResponse(PokerAICommon.InternalAction.RAISE, Math.max(threeBetSize, CasinoConfig.POKER_AI_MIN_RAISE_VALUE));
                } else {
                    decision = new PokerAICommon.AIResponse(PokerAICommon.InternalAction.CALL, 0);
                }
            } else {
                boolean isAllIn = currentBetToCall >= stackSize * 0.9f;
                if (isAllIn) {
                    PokerAIUtils.HandCategory handStrength = PokerAIUtils.classifyPreflopHand(holeCards);
                    trackPlayerAllIn(true);
                    if (!shouldCallPreflopAllInExtended(handStrength, potOdds, bbStack)) {
                        decision = new PokerAICommon.AIResponse(PokerAICommon.InternalAction.FOLD, 0);
                    } else {
                        decision = new PokerAICommon.AIResponse(PokerAICommon.InternalAction.CALL, 0);
                    }
                } else {
                    trackPlayerAllIn(false);

                    float evCall = PokerAIUtils.calculateCallEV(equity, potSize, currentBetToCall);
                    float evFold = -committedThisRound;

                    int threeBetSize = Math.min(Math.max(currentBetToCall * 3, bigBlind * 9), 
                        stackSize - currentBetToCall);

                    String playerRange = estimatePlayerRange();
                    float foldProb = estimateFoldProbability(playerRange, potSize + currentBetToCall, threeBetSize);
                    float evRaise = PokerAIUtils.calculateRaiseEVWithCommitment(equity, potSize, currentBetToCall, threeBetSize, foldProb, committedThisRound);

                    if (equity < 0.55f) {
                        float bluffEV = PokerAIUtils.calculateBluffEV(foldProb, potSize, threeBetSize);
                        evRaise = Math.max(evRaise, bluffEV);
                    }

                    boolean should3Bet = false;
                    if (equity > 0.70f) {
                        should3Bet = random.nextFloat() < 0.40f;
                    } else if (equity > 0.60f) {
                        should3Bet = random.nextFloat() < 0.20f;
                    } else if (equity > 0.50f && foldProb > 0.50f) {
                        should3Bet = random.nextFloat() < 0.10f;
                    }

                    if (shouldAvoidRaiseSpiral(stackSize, potSize)) {
                        should3Bet = false;
                    }

                    if (isPotCommitted(stackSize) && equity > 0.40f) {
                        should3Bet = false;
                    }

                    if (should3Bet && evRaise > evCall && evRaise > evFold && evRaise > 0) {
                        threeBetSize = Math.max(threeBetSize, CasinoConfig.POKER_AI_MIN_RAISE_VALUE);
                        if (CasinoConfig.POKER_AI_MAX_RAISE_RANDOM_ADDITION > 0) {
                            threeBetSize += random.nextInt(CasinoConfig.POKER_AI_MAX_RAISE_RANDOM_ADDITION);
                        }
                        decision = new PokerAICommon.AIResponse(PokerAICommon.InternalAction.RAISE, threeBetSize);
                    } else if (evCall > evFold) {
                        decision = new PokerAICommon.AIResponse(PokerAICommon.InternalAction.CALL, 0);
                    } else {
                        decision = new PokerAICommon.AIResponse(PokerAICommon.InternalAction.FOLD, 0);
                    }
                }
            }
        }

        boolean wasInitiator = decision.action == PokerAICommon.InternalAction.RAISE && currentBetToCall == 0;
        recordBettingAction(PokerRound.PREFLOP, decision.action, decision.raiseAmount, potSize, wasInitiator);
        return decision;
    }
    
    private PokerAICommon.AIResponse shortStackDecision(float equity, int currentBetToCall, int potSize, int stackSize, float bbStack) {
        float potOdds = (float)currentBetToCall / (potSize + currentBetToCall);
        
        if (bbStack < 10) {
            if (equity > 0.60f || (equity > 0.50f && random.nextFloat() < 0.4f)) {
                return new PokerAICommon.AIResponse(PokerAICommon.InternalAction.RAISE, stackSize);
            } else if (potOdds < 0.20f && equity > 0.45f) {
                return new PokerAICommon.AIResponse(PokerAICommon.InternalAction.CALL, 0);
            } else {
                return new PokerAICommon.AIResponse(PokerAICommon.InternalAction.FOLD, 0);
            }
        }
        
        if (equity > 0.60f || (equity > 0.50f && random.nextFloat() < 0.4f)) {
            return new PokerAICommon.AIResponse(PokerAICommon.InternalAction.RAISE, Math.min(stackSize, currentBetToCall * 3));
        } else if (equity > 0.45f) {
            return new PokerAICommon.AIResponse(PokerAICommon.InternalAction.CALL, 0);
        } else {
            return new PokerAICommon.AIResponse(PokerAICommon.InternalAction.FOLD, 0);
        }
    }
    
    private PokerAICommon.AIResponse postFlopDecision(List<Card> holeCards, List<Card> communityCards,
                                      int currentBetToCall, int potSize, int stackSize) {
        updateProfile();
        
        String playerRange = estimatePlayerRange();

        PokerAICommon.MonteCarloResult mcResult = runMonteCarloSimulationFull(holeCards, communityCards);
        float trueEquity = mcResult.getTotalEquity();

        float impliedOddsBonus = calculateImpliedOddsBonus(holeCards, communityCards, trueEquity);
        float adjustedTrueEquity = trueEquity + impliedOddsBonus;

        boolean wetBoard = PokerAIUtils.isWetBoard(communityCards);
        PokerRound currentRound = switch (communityCards.size()) {
            case 3 -> PokerRound.FLOP;
            case 4 -> PokerRound.TURN;
            default -> PokerRound.RIVER;
        };
        
        float perceivedStrength = getPerceivedStrength(narrative.type, wetBoard);
        PokerAICommon.DeceptionMode mode = selectDeceptionMode(trueEquity, perceivedStrength, wetBoard);
        float deceptionEquity = computeDeceptionEquity(mode, adjustedTrueEquity, perceivedStrength);

        return postFlopEVDecision(currentBetToCall, potSize, stackSize, playerRange, 
                                  deceptionEquity, wetBoard, trueEquity, perceivedStrength, mode, currentRound);
    }

    private PokerAICommon.AIResponse postFlopEVDecision(int currentBetToCall, int potSize, int stackSize, String playerRange, float equity, boolean isWetBoard, float trueEquity, float perceivedStrength, PokerAICommon.DeceptionMode mode, PokerRound currentRound) {
        float evFold = -committedThisRound;
        float evCall = PokerAIUtils.calculateCallEV(equity, potSize, currentBetToCall);
        
        float posBonus = isInPosition ? 0.05f : -0.02f;
        evFold += isInPosition ? 0.0f : 0.08f * potSize;
        evCall += isInPosition ? 0.05f * potSize : 0.0f;
        
        float baseRaiseMultiplier = switch (mode) {
            case TRAP -> 0.6f;
            case BLUFF_CONTINUATION -> 1.0f;
            case BLUFF_INITIATE -> 0.75f;
            case FLOAT_BLUFF -> 0.8f;
            case HONEST -> isInPosition ? 1.1f : 0.9f;
        };
        int[] raiseSizes = {
            (int)(potSize * 0.5f * baseRaiseMultiplier),
            (int)(potSize * 1.0f * baseRaiseMultiplier),
            (int)(potSize * 2.0f * baseRaiseMultiplier)
        };

        float bestRaiseEV = Float.NEGATIVE_INFINITY;
        int bestRaiseSize = 0;

        boolean canRaise = currentBetToCall < stackSize;

        if (canRaise) {
            for (int raiseSize : raiseSizes) {
                if (raiseSize > stackSize) continue;

                float foldProb = estimateFoldProbability(playerRange, potSize, raiseSize);
                float raiseEV = PokerAIUtils.calculateRaiseEVWithCommitment(equity, potSize, currentBetToCall, raiseSize, foldProb, committedThisRound);

                if (equity < 0.45f) {
                    float bluffEV = PokerAIUtils.calculateBluffEV(foldProb, potSize, raiseSize);
                    raiseEV = Math.max(raiseEV, bluffEV);
                }

                if (raiseEV > bestRaiseEV) {
                    bestRaiseEV = raiseEV;
                    bestRaiseSize = raiseSize;
                }
        }
        
        if (isWetBoard) {
            if (equity > 0.70f) {
                bestRaiseEV += 0.05f * potSize;
            } else if (equity < 0.50f) {
                evCall -= 0.05f * potSize;
            }
        } else {
            if (equity < 0.45f) {
                bestRaiseEV += 0.03f * potSize;
            }
        }

        bestRaiseEV += posBonus * potSize;
        }

        evFold = adjustEVForPersonality(evFold, PokerAICommon.InternalAction.FOLD);
        evCall = adjustEVForPersonality(evCall, PokerAICommon.InternalAction.CALL);
        bestRaiseEV = adjustEVForPersonality(bestRaiseEV, PokerAICommon.InternalAction.RAISE);
        
        evFold = adjustEVForDeception(evFold, PokerAICommon.InternalAction.FOLD, trueEquity, perceivedStrength, potSize);
        evCall = adjustEVForDeception(evCall, PokerAICommon.InternalAction.CALL, trueEquity, perceivedStrength, potSize);
        bestRaiseEV = adjustEVForDeception(bestRaiseEV, PokerAICommon.InternalAction.RAISE, trueEquity, perceivedStrength, potSize);

        if (shouldBeSuspicious()) {
            evFold -= 0.1f * potSize;
            evCall += 0.05f * potSize;
        }

        if (shouldMakeStubbornCall(equity, currentBetToCall, potSize)) {
            evFold = Float.NEGATIVE_INFINITY;
            evCall += 0.1f * potSize; // Boost call EV
        }

        String playerAction = currentBetToCall > 0 ? "RAISE" : "CHECK";
        PokerAICommon.OpponentHandEstimate handEstimate = estimateOpponentHand(playerAction, currentBetToCall, potSize);

        if (currentBetToCall > 0 && shouldFoldBasedOnHandReading(handEstimate, equity, currentBetToCall, potSize)) {
            evFold = Float.POSITIVE_INFINITY;
            evCall = Float.NEGATIVE_INFINITY;
            bestRaiseEV = Float.NEGATIVE_INFINITY;
        } else if (handEstimate.getBluffProbability() > 0.40f && equity > 0.35f) {
            evFold = Float.NEGATIVE_INFINITY;
            evCall += 0.1f * potSize;
        }

        if (shouldAvoidRaiseSpiral(stackSize, potSize)) {
            bestRaiseEV = Float.NEGATIVE_INFINITY;
        }

        if (isPotCommitted(stackSize) && equity > 0.40f && bestRaiseEV > evCall) {
            bestRaiseEV = evCall - 0.01f;
        }

        PokerAICommon.AIResponse finalDecision;
        if (bestRaiseEV > evCall && bestRaiseEV > evFold && bestRaiseSize > 0) {
            bestRaiseSize = Math.max(bestRaiseSize, CasinoConfig.POKER_AI_MIN_RAISE_VALUE);
            bestRaiseSize = Math.min(bestRaiseSize, stackSize);
            finalDecision = new PokerAICommon.AIResponse(PokerAICommon.InternalAction.RAISE, bestRaiseSize);
        } else if (evCall > evFold) {
            finalDecision = new PokerAICommon.AIResponse(PokerAICommon.InternalAction.CALL, 0);
        } else {
            finalDecision = new PokerAICommon.AIResponse(PokerAICommon.InternalAction.FOLD, 0);
        }

        if (random.nextFloat() < 0.10f) {
            float potOdds = currentBetToCall > 0 ? (float) currentBetToCall / (potSize + currentBetToCall) : 0f;
            PokerAICommon.AIResponse deviation = randomDeviationResponse(equity, potOdds, stackSize, potSize);
            if (deviation.action != finalDecision.action || deviation.raiseAmount != finalDecision.raiseAmount) {
                boolean wasInitiator = deviation.action == PokerAICommon.InternalAction.RAISE && currentBetToCall == 0;
                recordBettingAction(currentRound, deviation.action, deviation.raiseAmount, potSize, wasInitiator);
                return deviation;
            }
        }

        boolean wasInitiator = finalDecision.action == PokerAICommon.InternalAction.RAISE && currentBetToCall == 0;
        recordBettingAction(currentRound, finalDecision.action, finalDecision.raiseAmount, potSize, wasInitiator);

        return finalDecision;
    }

    private float adjustEVForPersonality(float ev, PokerAICommon.InternalAction action) {
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
            default -> 1.0f;
        };
        
        float confidenceScale = Math.min(profile.totalConfidence, 1.0f);
        float baseMultiplier = 1.0f;
        float personalityStrength = multiplier - 1.0f;
        multiplier = baseMultiplier + personalityStrength * confidenceScale;
        
        if (profile.bluffLikelihood > 0.2f) {
            float bluffAdjust = profile.bluffLikelihood * confidenceScale;
            if (action == PokerAICommon.InternalAction.FOLD) {
                multiplier *= (1.0f - bluffAdjust * 0.4f);
            } else if (action == PokerAICommon.InternalAction.CALL) {
                multiplier *= (1.0f + bluffAdjust * 0.25f);
            }
        }
        
        if (profile.trapLikelihood > 0.25f) {
            float trapAdjust = profile.trapLikelihood * confidenceScale;
            if (action == PokerAICommon.InternalAction.RAISE) {
                multiplier *= (1.0f - trapAdjust * 0.2f);
            }
        }
        
        return ev * multiplier;
    }
    
    private float adjustEVForDeception(float ev, PokerAICommon.InternalAction action, float trueEquity, float perceivedEquity, int potSize) {
        float equityGap = Math.abs(trueEquity - perceivedEquity);
        if (equityGap < 0.20f) return ev;
        
        float deceptionBonus = 0f;
        
        if (trueEquity > 0.70f && perceivedEquity < 0.50f) {
            if (action == PokerAICommon.InternalAction.RAISE) {
                deceptionBonus = -0.08f * potSize;
            } else if (action == PokerAICommon.InternalAction.CALL) {
                deceptionBonus = 0.05f * potSize;
            }
        }
        
        if (trueEquity < 0.40f && perceivedEquity > 0.55f) {
            if (action == PokerAICommon.InternalAction.RAISE) {
                deceptionBonus = 0.06f * potSize;
            }
        }
        
        return ev + deceptionBonus;
    }
    
    private static final Map<String, Float> preflopEquityCache = new HashMap<>();
    private static boolean preflopCacheInitialized = false;

    private void initializePreflopEquityCache() {
        if (preflopCacheInitialized) return;
        
        Deck deck = new Deck(GameType.POKER);
        for (int i = 0; i < deck.cards.size(); i++) {
            for (int j = i + 1; j < deck.cards.size(); j++) {
                Card c1 = deck.cards.get(i);
                Card c2 = deck.cards.get(j);
                
                String key = createHandKey(c1, c2);
                
                if (!preflopEquityCache.containsKey(key)) {
                    float equity = calculatePreflopEquityMonteCarlo(c1, c2);
                    preflopEquityCache.put(key, equity);
                }
            }
        }
        
        preflopCacheInitialized = true;
    }
    
    private String createHandKey(Card c1, Card c2) {
        int r1 = c1.rank().getValue(GameType.POKER);
        int r2 = c2.rank().getValue(GameType.POKER);
        int v1 = Math.max(r1, r2);
        int v2 = Math.min(r1, r2);
        boolean suited = (c1.suit() == c2.suit());
        
        if (v1 == v2) {
            return v1 + "_pair";
        }
        return v1 + "_" + v2 + "_" + (suited ? "s" : "o");
    }
    
    private float calculatePreflopEquityMonteCarlo(Card c1, Card c2) {
        int wins = 0;
        int ties = 0;
        int samples = 250;
        
        int c1Idx = cardIndexMap.get(c1);
        int c2Idx = cardIndexMap.get(c2);
        
        boolean[] excluded = new boolean[52];
        excluded[c1Idx] = true;
        excluded[c2Idx] = true;
        
        for (int i = 0; i < samples; i++) {
            int[] shuffledIndices = MonteCarloUtility.shuffleAvailableCards(random, excluded);
            
            Card[] boardCards = new Card[5];
            for (int j = 0; j < 5; j++) boardCards[j] = allCardsArray[shuffledIndices[j]];
            
            Card opp1 = allCardsArray[shuffledIndices[5]];
            Card opp2 = allCardsArray[shuffledIndices[6]];
            
            PokerHandEvaluator.HandScore ourScore = PokerHandEvaluator.evaluateTwoCardsFast(c1, c2, boardCards);
            PokerHandEvaluator.HandScore oppScore = PokerHandEvaluator.evaluateTwoCardsFast(opp1, opp2, boardCards);
            
            int cmp = ourScore.compareTo(oppScore);
            if (cmp > 0) wins++;
            else if (cmp == 0) ties++;
        }
        
        return (wins + ties * 0.5f) / samples;
}
    
    private float calculatePreflopEquity(List<Card> holeCards) {
        initializePreflopEquityCache();
        
        Card c1 = holeCards.get(0);
        Card c2 = holeCards.get(1);
        String key = createHandKey(c1, c2);
        
        Float cached = preflopEquityCache.get(key);
        if (cached != null) {
            return cached;
        }
        
        return calculatePreflopEquitySimple(holeCards);
    }
    
    private float calculatePostflopEquity(List<Card> holeCards, List<Card> communityCards) {
        return runMonteCarloSimulationFull(holeCards, communityCards).getTotalEquity();
    }
    
    private float calculatePreflopEquitySimple(List<Card> holeCards) {
        Card c1 = holeCards.get(0);
        Card c2 = holeCards.get(1);
        int v1 = Math.max(c1.rank().getValue(GameType.POKER), c2.rank().getValue(GameType.POKER));
        int v2 = Math.min(c1.rank().getValue(GameType.POKER), c2.rank().getValue(GameType.POKER));
        boolean suited = (c1.suit() == c2.suit());
        int gap = v1 - v2;

        // Pairs
        if (v1 == v2) {
            if (v1 >= 13) return 0.85f;      // AA, KK
            if (v1 >= 11) return 0.78f;      // QQ, JJ
            if (v1 >= 9) return 0.70f;       // TT, 99
            if (v1 >= 7) return 0.62f;       // 88, 77
            if (v1 >= 5) return 0.55f;       // 66, 55
            return 0.48f;                     // 44-22
        }

        // Big cards: AK, AQ
        if (v1 == 14 && v2 == 13) return suited ? 0.68f : 0.65f;  // AK
        if (v1 == 14 && v2 == 12) return suited ? 0.63f : 0.60f;  // AQ

        // Suited hands
        if (suited) {
            if (v1 == 14 && v2 >= 10) return 0.58f;     // AJ(s), AT(s)
            if (v1 == 13 && v2 == 12) return 0.58f;     // KQ(s)
            if (v1 == 13 && v2 >= 10) return 0.50f;     // KJ(s), KT(s)
            if (v1 == 12 && v2 >= 10) return 0.50f;     // QJ(s), QT(s)
            if (gap == 1 && v1 >= 5) return 0.52f;      // Suited connectors JTs-54s
            if (gap == 2 && v1 >= 6) return 0.48f;      // One-gap suited
            if (gap <= 3 && v1 >= 9) return 0.45f;      // Two-gap suited
            if (v1 >= 10) return 0.45f;                 // Any suited T+
            return 0.40f;                                // Weak suited
        }

        // Offsuit hands
        if (v1 == 14 && v2 >= 10) return 0.55f;         // AJ, AT
        if (v1 == 13 && v2 >= 11) return 0.52f;         // KQ, KJ
        if (gap == 1 && v1 >= 12) return 0.48f;         // QJo+ connectors
        if (v1 >= 11 && v2 >= 10) return 0.45f;         // Face cards
        if (gap <= 2 && v1 >= 8) return 0.38f;          // Weak connectors
        if (v1 >= 10) return 0.35f;                     // One high card

        return 0.30f;
    }

    private PokerAICommon.MonteCarloResult runMonteCarloSimulationFull(
            List<Card> holeCards,
            List<Card> communityCards) {
        int wins = 0;
        int ties = 0;
        int losses = 0;
        int simulationCount = CasinoConfig.POKER_MONTE_CARLO_SAMPLES;
        
        boolean[] excluded = MonteCarloUtility.createExclusionMask(holeCards, communityCards);
        
        for (int i = 0; i < simulationCount; i++) {
            int[] shuffledIndices = MonteCarloUtility.shuffleAvailableCards(random, excluded);
            
            Card[] boardCards = MonteCarloUtility.completeBoard(communityCards, shuffledIndices);
            
            int oppCardOffset = MonteCarloUtility.getOpponentCardOffset(communityCards.size());
            int oppCard1Idx = shuffledIndices[oppCardOffset];
            int oppCard2Idx = shuffledIndices[oppCardOffset + 1];
            
            PokerHandEvaluator.HandScore ourScore = PokerHandEvaluator.evaluateHandFast(holeCards, boardCards);
            PokerHandEvaluator.HandScore oppScore = PokerHandEvaluator.evaluateTwoCardsFast(
                allCardsArray[oppCard1Idx], allCardsArray[oppCard2Idx], boardCards);
            
            int cmp = ourScore.compareTo(oppScore);
            if (cmp > 0) wins++;
            else if (cmp == 0) ties++;
            else losses++;
            
            if (MonteCarloUtility.shouldEarlyTerminate(wins, ties, i + 1)) {
                return MonteCarloUtility.createResult(wins, ties, losses, i + 1);
            }
        }
        
        return MonteCarloUtility.createResult(wins, ties, losses, simulationCount);
}
    
    private float estimateFoldProbability(String playerRange, int potSize, int betSize) {
        float baseFoldProb = switch (playerRange) {
            case "tight_range" -> 0.45f;
            case "wide_range" -> 0.20f;
            default -> 0.30f;
        };

        float potOdds = (float) betSize / (potSize + betSize);
        if (potOdds > 0.5f) {
            baseFoldProb += 0.15f;
        } else if (potOdds < 0.25f) {
            baseFoldProb -= 0.10f;
        }

        return Math.max(0.1f, baseFoldProb);
    }

    public void trackPlayerAction(boolean isRaise, boolean isFold, boolean isCheck, boolean isPreFlop, boolean putMoneyInPot) {
        totalPlayerActions++;
        
        int actionType = isRaise ? 2 : (isFold ? 0 : (isCheck ? 3 : 1));
        trackRecentAction(actionType);
        
        if (isRaise) {
            totalRaises++;
            if (isPreFlop) pfrCount++;
            playerBetsTotal++;
        } else if (!isFold && !isCheck) {
            totalCalls++;
            playerBetsTotal++;
        }
        
        // Track check-raises
        if (isRaise && !isPreFlop && !putMoneyInPot) {
            playerCheckRaises++;
        }
        
        // Track VPIP (Voluntarily Put $ In Pot) - excludes checking
        if (putMoneyInPot) {
            vpipCount++;
        }
        
        updateAggressionMeter(isRaise);
        
        if (handsPlayed > 3 && totalPlayerActions > 5) {
            float vpip = (float) vpipCount / handsPlayed;
            float pfr = (float) pfrCount / handsPlayed;
            float af = totalCalls > 0 ? (float) totalRaises / totalCalls : totalRaises;
            
            if (vpip < 0.25f && pfr < 0.15f) {
                playerStyle = 1;
            } else if (vpip > 0.40f && af > 1.5f) {
                playerStyle = 3;
            } else {
                playerStyle = 2;
            }
        }
    }
    
    public void trackAIFoldedToPlayerBet() {
        playerBetsWithoutShowdown++;
        consecutiveBluffsCaught++;
        handsSinceLastShowdown++;

        float bluffRate = (float) consecutiveBluffsCaught / Math.max(1, handsSinceLastShowdown);
        if (consecutiveBluffsCaught >= 2 || bluffRate > 0.40f) {
            isSuspicious = true;
            suspiciousModeHands = 2 + random.nextInt(2);
        }
    }

    public void trackPlayerShowdown(boolean playerWasBluffing) {
        handsSinceLastShowdown = 0;
        consecutiveBluffsCaught = 0;
        largeBetsWithoutShowdown = 0;

        if (playerWasBluffing) {
            timesBluffedByPlayer++;
            if (timesBluffedByPlayer >= 2) {
                isSuspicious = true;
                suspiciousModeHands = 3;
            }
        }
    }
    
    private boolean shouldBeSuspicious() {
        if (suspiciousModeHands > 0) {
            return true;
        }

        if (playerBetsTotal > 5) {
            float bluffSuccessRate = (float) playerBetsWithoutShowdown / playerBetsTotal;
            if (bluffSuccessRate > 0.50f) {
                return true;
            }
        }

        if (largeBetsWithoutShowdown >= 2) {
            return true;
        }

        if (handsPlayed > 3 && playerCheckRaises > handsPlayed / 3) {
            return true;
        }

        return false;
    }
    
    private boolean shouldMakeStubbornCall(float equity, int betToCall, int potSize) {
        if (betToCall < potSize / 10 && equity > 0.45f) {
            return true;
        }
        
        if (isSuspicious && equity > 0.35f) {
            return random.nextFloat() < 0.5f;
        }
        
        return false;
    }
    
    public void newHandStarted(boolean aiIsDealer) {
        handsPlayed++;
        isInPosition = !aiIsDealer;

        if (suspiciousModeHands > 0) {
            suspiciousModeHands--;
            if (suspiciousModeHands == 0) {
                isSuspicious = false;
            }
        }

        handsSinceLastShowdown++;

        committedThisRound = 0;
        
        narrative = new PokerAICommon.BettingNarrative();
    }

    /**
     * Resets the committed chips counter when advancing to a new betting round.
     * Called by PokerGame when the round advances (e.g., from FLOP to TURN).
     */
    public void resetCommittedChips() {
        committedThisRound = 0;
    }

    /**
     * Resets betting round tracking when advancing to a new street.
     * Called by PokerGame.advanceRound().
     */
    @Override
    protected void resetBettingRoundTracking(int potSize) {
        super.resetBettingRoundTracking(potSize);
        aiHasRaisedThisRound = false;
    }

    /**
     * Records a player action for betting sequence tracking.
     */
    public void recordPlayerAction(String action) {
        if (action.equals("RAISE") || action.equals("ALL_IN")) {
            raisesThisRound++;
        }
    }

    public void recordAIAction(String action) {
        if (action.equals("RAISE") || action.equals("ALL_IN")) {
            raisesThisRound++;
            aiHasRaisedThisRound = true;
        }
    }

    @Override
    protected boolean shouldAvoidRaiseSpiral(int stackSize, int potSize) {
        if (raisesThisRound >= 2) {
            return true;
        }

        float spr = (float) (stackSize + committedThisRound) / Math.max(1, potSize);
        if (spr < 3.0f && raisesThisRound == 1) {
            return true;
        }

        if (aiHasRaisedThisRound && raisesThisRound == 1) {
            return random.nextFloat() < 0.70f;
        }

        float potGrowth = (float) potSize / Math.max(1, totalPotThisRound);
        return potGrowth > 4.0f && raisesThisRound == 1;
    }

    private PokerAICommon.OpponentHandEstimate estimateOpponentHand(String currentAction, int betAmount, int potSize) {
        PokerAICommon.OpponentHandEstimate estimate = new PokerAICommon.OpponentHandEstimate();

        switch (PokerAICommon.PlayerStyle.values()[playerStyle]) {
            case PASSIVE:
                estimate.premiumProbability = 0.08f;
                estimate.strongProbability = 0.20f;
                estimate.playableProbability = 0.27f;
                estimate.weakProbability = 0.45f;
                estimate.bluffProbability = 0.10f;
                estimate.valueProbability = 0.90f;
                break;
            case AGGRESSIVE:
                estimate.premiumProbability = 0.03f;
                estimate.strongProbability = 0.12f;
                estimate.playableProbability = 0.30f;
                estimate.weakProbability = 0.55f;
                estimate.bluffProbability = 0.35f;
                estimate.valueProbability = 0.65f;
                break;
            case BALANCED:
            default:
                estimate.premiumProbability = 0.05f;
                estimate.strongProbability = 0.15f;
                estimate.playableProbability = 0.25f;
                estimate.weakProbability = 0.55f;
                estimate.bluffProbability = 0.20f;
                estimate.valueProbability = 0.80f;
                break;
        }

        // Adjust based on current action
        float betToPotRatio = (float) betAmount / Math.max(1, potSize);

        if (currentAction.equals("RAISE") || currentAction.equals("ALL_IN")) {
            // Raises suggest stronger hands (with some bluffs)
            if (betToPotRatio > 1.5f) {
                // Large raise: very strong or very weak (polarized)
                float polarizedBluffRate = estimate.bluffProbability * 1.5f;
                estimate.premiumProbability *= 2.0f;
                estimate.strongProbability *= 1.5f;
                estimate.weakProbability *= 0.5f;
                estimate.bluffProbability = Math.min(polarizedBluffRate, 0.40f);
                estimate.valueProbability = 1.0f - estimate.bluffProbability;
            } else if (betToPotRatio > 0.7f) {
                // Medium raise: strong hands or semi-bluffs
                estimate.premiumProbability *= 1.5f;
                estimate.strongProbability *= 1.3f;
                estimate.playableProbability *= 0.8f;
                estimate.weakProbability *= 0.6f;
                estimate.bluffProbability *= 0.8f;
            } else {
                // Small raise: wider range
                estimate.premiumProbability *= 1.2f;
                estimate.strongProbability *= 1.1f;
                estimate.bluffProbability *= 1.1f;
            }
        } else if (currentAction.equals("CALL")) {
            // Calls suggest medium strength or draws
            estimate.premiumProbability *= 0.5f; // Would have raised premium
            estimate.strongProbability *= 1.2f;
            estimate.playableProbability *= 1.3f;
            estimate.bluffProbability *= 0.5f; // Not a bluff if calling
            estimate.valueProbability = 1.0f - estimate.bluffProbability;
        }

        // Adjust based on number of raises this round
        if (raisesThisRound >= 2) {
            // Multiple raises: ranges are stronger
            estimate.premiumProbability *= 1.8f;
            estimate.strongProbability *= 1.4f;
            estimate.weakProbability *= 0.3f;
            estimate.bluffProbability *= 0.4f; // Fewer bluffs in raise wars
        } else if (raisesThisRound == 1) {
            // Single raise: moderately stronger
            estimate.premiumProbability *= 1.3f;
            estimate.strongProbability *= 1.2f;
            estimate.weakProbability *= 0.7f;
        }

        // Normalize probabilities
        float totalHandProb = estimate.premiumProbability + estimate.strongProbability +
                             estimate.playableProbability + estimate.weakProbability;
        estimate.premiumProbability /= totalHandProb;
        estimate.strongProbability /= totalHandProb;
        estimate.playableProbability /= totalHandProb;
        estimate.weakProbability /= totalHandProb;

        return estimate;
    }

    /**
     * Determines if AI should fold based on opponent hand estimate and our equity.
     * Uses hand reading to make better fold decisions.
     */
    private boolean shouldFoldBasedOnHandReading(PokerAICommon.OpponentHandEstimate estimate, float ourEquity,
                                                  int currentBetToCall, int potSize) {
        if (estimate.getBluffProbability() > 0.40f && ourEquity > 0.35f) {
            return false;
        }

        if (estimate.getValueProbability() > 0.60f && ourEquity < 0.50f) {
            return true;
        }

        float strongAndPremiumProb = estimate.premiumProbability + estimate.strongProbability;
        if (strongAndPremiumProb > 0.40f && ourEquity < 0.55f) {
            return true;
        }

        float potOdds = (float) currentBetToCall / (potSize + currentBetToCall);
        return ourEquity < potOdds * 0.9f;
    }

    private void updateAggressionMeter(boolean isRaise) {
        float recentAggression = 0;
        float weight = 1.0f;
        
        aggressionHistory[historyIndex] = isRaise ? 1.0f : 0.3f;
        historyIndex = (historyIndex + 1) % aggressionHistory.length;
        
        for (float v : aggressionHistory) {
            recentAggression += weight * v;
            weight *= 0.8f;
        }
        
        recentAggression /= AGGRESSION_WEIGHT_SUM;
        aggressionMeter = 0.7f * aggressionMeter + 0.3f * recentAggression;
    }
}
