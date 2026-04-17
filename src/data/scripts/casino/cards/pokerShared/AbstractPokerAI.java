package data.scripts.casino.cards.pokerShared;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import data.scripts.casino.CasinoConfig;
import data.scripts.casino.cards.Card;

public abstract class AbstractPokerAI {

    protected final Random random = new Random();
    protected PokerAICommon.Personality personality = PokerAICommon.Personality.CALCULATED;
    protected PokerAICommon.PlayerProfile profile = new PokerAICommon.PlayerProfile();
    protected PokerAICommon.BettingNarrative narrative = new PokerAICommon.BettingNarrative();
    protected int handsPlayed = 0;
    protected int committedThisRound = 0;
    protected int raisesThisRound = 0;
    protected int totalPotThisRound = 0;

    protected static final int RECENT_ACTION_SIZE = 10;
    protected final int[] recentActions = new int[RECENT_ACTION_SIZE];
    protected int recentActionIndex = 0;
    protected int recentActionCount = 0;

    protected static final int SHOWDOWN_HISTORY_SIZE = 10;
    protected final PokerAICommon.ShowdownRecord[] showdownHistory = new PokerAICommon.ShowdownRecord[SHOWDOWN_HISTORY_SIZE];
    protected int showdownHistoryIndex = 0;
    protected int showdownHistoryCount = 0;

    protected int totalPlayerActions = 0;
    protected int totalPlayerRaises = 0;
    protected int totalPlayerCalls = 0;

    protected int playerAllInCount = 0;
    protected int playerAllInOpportunities = 0;
    protected boolean playerIsAllInLoose = false;

    protected int vpipCount = 0;
    protected int pfrCount = 0;
    protected int playerStyle = 0;
    protected int playerBetsWithoutShowdown = 0;
    protected int playerBetsTotal = 0;
    protected int consecutiveBluffsCaught = 0;
    protected int suspiciousModeHands = 0;
    protected boolean isSuspicious = false;
    protected int playerCheckRaises = 0;
    protected int handsSinceLastShowdown = 0;
    protected int timesBluffedByPlayer = 0;
    protected int largeBetsWithoutShowdown = 0;

    protected AbstractPokerAI() {
        Arrays.fill(recentActions, 0);
    }

    protected void updateProfile() {
        profile.reset();
        contributeFromRecentActions();
        contributeFromShowdownHistory();
        contributeFromLongTermStats();
        personality = profile.derivePersonality();
    }

    protected void contributeFromRecentActions() {
        if (recentActionCount < 2) return;

        float weightedRaises = 0, weightedFolds = 0, weightedPassive = 0;
        float totalWeight = 0;

        for (int i = 0; i < recentActionCount; i++) {
            float decay = getRecentActionDecay(i);
            totalWeight += decay;
            int action = recentActions[i];
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
                profile.aggressionConfidence += confidenceBoost * raiseRatio;
                profile.loosePlayLikelihood += 0.15f * raiseRatio;
            }
            if (foldRatio > 0.35f) {
                profile.passivityConfidence += confidenceBoost * foldRatio;
                profile.tightPlayLikelihood += 0.15f * foldRatio;
            }
            if (passiveRatio > 0.5f) {
                profile.passivityConfidence += confidenceBoost * passiveRatio * 0.5f;
            }

            profile.totalConfidence += confidenceBoost;
        }
    }

    protected float getRecentActionDecay(int index) {
        int distance = recentActionCount - 1 - index;
        return Math.max(0.4f, 1.0f - distance * 0.12f);
    }

    protected void contributeFromShowdownHistory() {
        if (showdownHistoryCount == 0) return;

        float weightedBluffs = 0, weightedTraps = 0;
        float totalWeight = 0;

        for (int i = 0; i < showdownHistoryCount; i++) {
            PokerAICommon.ShowdownRecord rec = showdownHistory[i];
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

            profile.bluffLikelihood = bluffRatio;
            profile.trapLikelihood = trapRatio;

            if (bluffRatio > 0.15f) {
                profile.aggressionConfidence += bluffRatio * 0.8f;
                profile.totalConfidence += bluffRatio * 0.6f;
            }
            if (trapRatio > 0.20f) {
                profile.passivityConfidence += trapRatio * 0.5f;
                profile.totalConfidence += trapRatio * 0.4f;
            }

            profile.totalConfidence += Math.min(showdownHistoryCount * 0.1f, 0.4f);
        }
    }

    protected void contributeFromLongTermStats() {
        if (handsPlayed < 3 || totalPlayerActions < 5) return;

        float raiseRate = (float) totalPlayerRaises / totalPlayerActions;
        float confidence = Math.min(handsPlayed * 0.03f, 0.3f);

        if (raiseRate > 0.35f) {
            profile.aggressionConfidence += confidence;
        } else if (raiseRate < 0.15f) {
            profile.passivityConfidence += confidence;
        }

        profile.totalConfidence += confidence;
    }

    protected void trackRecentAction(int actionType) {
        recentActions[recentActionIndex] = actionType;
        recentActionIndex = (recentActionIndex + 1) % RECENT_ACTION_SIZE;
        if (recentActionCount < RECENT_ACTION_SIZE) recentActionCount++;
    }

    protected void trackShowdownRecord(int handRankValue, float lastBetRatio, boolean won, boolean wasBluff) {
        showdownHistory[showdownHistoryIndex] = new PokerAICommon.ShowdownRecord(handRankValue, lastBetRatio, won, wasBluff, handsPlayed);
        showdownHistoryIndex = (showdownHistoryIndex + 1) % SHOWDOWN_HISTORY_SIZE;
        if (showdownHistoryCount < SHOWDOWN_HISTORY_SIZE) showdownHistoryCount++;
        updateProfile();
    }

    protected void recordBettingNarrative(PokerRound round, PokerAICommon.InternalAction action, int betAmount, int potSize, boolean wasInitiator) {
        boolean isBetOrRaise = action == PokerAICommon.InternalAction.RAISE || action == PokerAICommon.InternalAction.BET;
        float betRatio = isBetOrRaise ? (float) betAmount / Math.max(1, potSize) : 0f;
        PokerAICommon.BettingAction ba = new PokerAICommon.BettingAction(round, action, betRatio, wasInitiator);
        narrative.addAction(ba);
    }

    protected float applyPersonalityEVAdjustment(float ev, PokerAICommon.InternalAction action) {
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

        float confidenceScale = Math.min(profile.totalConfidence, 1.0f);
        float personalityStrength = multiplier - 1.0f;
        multiplier = 1.0f + personalityStrength * confidenceScale;

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

    protected PokerAICommon.AIResponse randomDeviation(float equity, float potOdds, int stackSize, int potSize) {
        int deviationType = random.nextInt(4) + 1;

        switch (deviationType) {
            case 1:
                if (equity > potOdds * 0.8f) {
                    return new PokerAICommon.AIResponse(PokerAICommon.InternalAction.CALL, 0);
                }
                break;
            case 2:
                if (equity < 0.4f && random.nextFloat() < 0.3f) {
                    int bluffRaise = (int) (potSize * 0.5f);
                    bluffRaise = Math.min(bluffRaise, stackSize);
                    return new PokerAICommon.AIResponse(PokerAICommon.InternalAction.RAISE, bluffRaise);
                }
                break;
            case 3:
                if (equity > 0.7f) {
                    return new PokerAICommon.AIResponse(PokerAICommon.InternalAction.CALL, 0);
                }
                break;
            case 4:
                if (equity > 0.75f) {
                    int overbet = (int) (potSize * 1.5f);
                    overbet = Math.min(overbet, stackSize);
                    return new PokerAICommon.AIResponse(PokerAICommon.InternalAction.RAISE, overbet);
                }
                break;
        }

        if (equity > potOdds) {
            return new PokerAICommon.AIResponse(PokerAICommon.InternalAction.CALL, 0);
        } else {
            return new PokerAICommon.AIResponse(PokerAICommon.InternalAction.FOLD, 0);
        }
    }

    protected PokerAICommon.AIResponse shortStackDecision(float equity, int betToCall, int pot, float bbStack) {
        float potOdds = (float) betToCall / (pot + betToCall);

        if (bbStack < 10) {
            if (equity > 0.60f || (equity > 0.50f && random.nextFloat() < 0.4f)) {
                return new PokerAICommon.AIResponse(PokerAICommon.InternalAction.RAISE, getShortStackAllInAmount());
            } else if (potOdds < 0.20f && equity > 0.45f) {
                return new PokerAICommon.AIResponse(PokerAICommon.InternalAction.CALL, 0);
            } else {
                return new PokerAICommon.AIResponse(PokerAICommon.InternalAction.FOLD, 0);
            }
        }

        if (equity > 0.60f || (equity > 0.50f && random.nextFloat() < 0.4f)) {
            int raiseAmount = Math.min(getShortStackAllInAmount(), betToCall * 3);
            return new PokerAICommon.AIResponse(PokerAICommon.InternalAction.RAISE, raiseAmount);
        } else if (equity > 0.45f) {
            return new PokerAICommon.AIResponse(PokerAICommon.InternalAction.CALL, 0);
        } else {
            return new PokerAICommon.AIResponse(PokerAICommon.InternalAction.FOLD, 0);
        }
    }

    protected abstract int getShortStackAllInAmount();

    protected boolean shouldAvoidRaiseSpiral(int stackSize, int potSize) {
        if (raisesThisRound >= 2) return true;

        int effectivePotSize = Math.max(1, potSize);
        float spr = (float) (stackSize + committedThisRound) / effectivePotSize;
        if (spr < 3.0f && raisesThisRound == 1) return true;

        if (raisesThisRound == 1 && random.nextFloat() < 0.70f) return true;

        float potGrowth = (float) potSize / Math.max(1, totalPotThisRound);
        return potGrowth > 4.0f && raisesThisRound == 1;
    }

    protected boolean isPotCommitted(int stackSize) {
        return stackSize <= 0 || committedThisRound > 0.3f * (committedThisRound + stackSize);
    }

    protected float calculateImpliedOddsBonus(List<Card> holeCards, List<Card> communityCards, float currentEquity) {
        return calculateImpliedOddsBonus(holeCards, communityCards, currentEquity, 0);
    }

    protected float calculateImpliedOddsBonus(List<Card> holeCards, List<Card> communityCards, float currentEquity, int opponentCount) {
        int outs = PokerAIUtils.countOuts(holeCards, communityCards);
        int streetsRemaining = 5 - communityCards.size();
        float drawEquity = outs * 0.02f * streetsRemaining;

        if (currentEquity >= 0.60f || drawEquity <= 0.05f) {
            return 0f;
        }

        float opponentBonus = opponentCount > 0 ? Math.min(opponentCount * 0.05f, 0.15f) : 0f;
        return Math.min((drawEquity + opponentBonus) * 0.5f, 0.15f);
    }

    protected int[] calculatePostFlopRaiseSizes(int pot, boolean wetBoard, float equity, boolean inLatePosition) {
        float baseMultiplier = 1.0f;

        if (wetBoard && equity > 0.70f) {
            baseMultiplier = 1.3f;
        } else if (!wetBoard && equity < 0.45f) {
            baseMultiplier = 0.75f;
        }

        if (inLatePosition) {
            baseMultiplier *= 1.1f;
        }

        return new int[] {
            (int) (pot * 0.5f * baseMultiplier),
            (int) (pot * 1.0f * baseMultiplier),
            (int) (pot * 2.0f * baseMultiplier)
        };
    }

    protected PokerAICommon.AIResponse handleFreeCheckDecision(
            float equity, int potSize, int stackSize, boolean inLatePosition, boolean wetBoard) {
        float bluffChance = inLatePosition ? 0.35f : 0.20f;
        float threshold = 0.45f;

        if (equity >= threshold || (equity >= threshold - 0.05f && random.nextFloat() < bluffChance)) {
            int[] raiseSizes = calculatePostFlopRaiseSizes(potSize, wetBoard, equity, inLatePosition);
            int raiseAmount = raiseSizes[random.nextInt(raiseSizes.length)];
            raiseAmount = Math.min(Math.max(raiseAmount, CasinoConfig.POKER_AI_MIN_RAISE_VALUE), stackSize);
            return new PokerAICommon.AIResponse(PokerAICommon.InternalAction.RAISE, raiseAmount);
        }

        if (equity < 0.35f && random.nextFloat() < bluffChance * 0.5f) {
            int bluffRaise = (int) (potSize * 0.5f);
            bluffRaise = Math.min(Math.max(bluffRaise, CasinoConfig.POKER_AI_MIN_RAISE_VALUE), stackSize);
            return new PokerAICommon.AIResponse(PokerAICommon.InternalAction.RAISE, bluffRaise);
        }

        return new PokerAICommon.AIResponse(PokerAICommon.InternalAction.CHECK, 0);
    }

    protected void resetForNewHand() {
        committedThisRound = 0;
        raisesThisRound = 0;
        totalPotThisRound = 0;
        narrative.reset();
    }

    protected void resetBettingRoundTracking(int potSize) {
        raisesThisRound = 0;
        totalPotThisRound = potSize;
        committedThisRound = 0;
    }

    protected void applyPersonalityToRaise(PokerAICommon.AIResponse decision, float equity, int stack) {
        if (decision.action == PokerAICommon.InternalAction.RAISE) {
            if (personality == PokerAICommon.Personality.AGGRESSIVE && random.nextFloat() < 0.3f) {
                decision.raiseAmount = (int) (decision.raiseAmount * 1.3f);
            } else if (personality == PokerAICommon.Personality.TIGHT && random.nextFloat() < 0.3f) {
                decision.raiseAmount = (int) (decision.raiseAmount * 0.8f);
            }
            decision.raiseAmount = Math.min(decision.raiseAmount, stack);
        }

        if (personality == PokerAICommon.Personality.AGGRESSIVE 
            && decision.action == PokerAICommon.InternalAction.FOLD && equity > 0.35f) {
            if (random.nextFloat() < 0.2f) {
                decision.action = PokerAICommon.InternalAction.CALL;
            }
        }

        if (personality == PokerAICommon.Personality.TIGHT 
            && decision.action == PokerAICommon.InternalAction.CALL && equity < 0.45f) {
            if (random.nextFloat() < 0.2f) {
                decision.action = PokerAICommon.InternalAction.FOLD;
            }
        }
    }

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

        return Math.min(0.85f, baseStrength);
    }

    protected void trackPlayerAllIn(boolean wentAllIn) {
        playerAllInOpportunities++;
        if (wentAllIn) {
            playerAllInCount++;
        }
        if (playerAllInOpportunities >= 10) {
            float allInFrequency = (float) playerAllInCount / playerAllInOpportunities;
            playerIsAllInLoose = allInFrequency > 0.25f;
        }
    }

    protected boolean shouldCallPreflopAllInExtended(PokerAIUtils.HandCategory handStrength, float potOdds, float bbStack) {
        if (PokerAIUtils.shouldCallPreflopAllIn(handStrength, potOdds, bbStack)) return true;
        if (handStrength == PokerAIUtils.HandCategory.PLAYABLE && playerIsAllInLoose && potOdds >= 0.45f) return true;
        return false;
    }

    public void trackPlayerAction(boolean isRaise, boolean isFold, boolean isCheck, boolean isPreFlop, boolean putMoneyInPot) {
        totalPlayerActions++;
        
        int actionType = isRaise ? 2 : (isFold ? 0 : (isCheck ? 3 : 1));
        trackRecentAction(actionType);
        
        if (isRaise) {
            totalPlayerRaises++;
            if (isPreFlop) pfrCount++;
            playerBetsTotal++;
        } else if (!isFold && !isCheck) {
            totalPlayerCalls++;
            playerBetsTotal++;
        }
        
        if (isRaise && !isPreFlop && !putMoneyInPot) {
            playerCheckRaises++;
        }
        
        if (putMoneyInPot) {
            vpipCount++;
        }
        
        updatePlayerStyle();
    }

    protected void updatePlayerStyle() {
        if (handsPlayed > 3 && totalPlayerActions > 5) {
            float vpip = (float) vpipCount / handsPlayed;
            float pfr = (float) pfrCount / handsPlayed;
            float af = totalPlayerCalls > 0 ? (float) totalPlayerRaises / totalPlayerCalls : totalPlayerRaises;
            
            if (vpip < 0.25f && pfr < 0.15f) {
                playerStyle = 1;
            } else if (vpip > 0.40f && af > 1.5f) {
                playerStyle = 3;
            } else {
                playerStyle = 2;
            }
        }
    }

    protected String estimatePlayerRange() {
        return switch (PokerAICommon.PlayerStyle.values()[playerStyle]) {
            case PASSIVE -> "tight_range";
            case AGGRESSIVE -> "wide_range";
            case BALANCED -> "standard_range";
            default -> "random";
        };
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

    protected boolean shouldBeSuspicious() {
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

    protected boolean shouldMakeStubbornCall(float equity, int betToCall, int potSize) {
        if (betToCall < potSize / 10 && equity > 0.45f) {
            return true;
        }
        
        if (isSuspicious && equity > 0.35f) {
            return random.nextFloat() < 0.5f;
        }
        
        return false;
    }

    protected PokerAICommon.DeceptionMode selectDeceptionMode(float trueEquity, float perceivedStrength, boolean wetBoard) {
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
    
    protected float computeDeceptionEquity(PokerAICommon.DeceptionMode mode, float trueEquity, float perceivedStrength) {
        return switch (mode) {
            case TRAP -> perceivedStrength + 0.10f;
            case BLUFF_CONTINUATION -> perceivedStrength;
            case BLUFF_INITIATE -> 0.55f;
            case FLOAT_BLUFF -> 0.60f;
            case HONEST -> trueEquity;
        };
    }
    
    protected boolean canPivotToBluff(boolean wetBoard) {
        if (narrative.aggregateAggression > 0.5f) return false;
        
        if (narrative.historyCount == 0) return true;
        
        int lastIndex = (narrative.historyIndex - 1 + PokerAICommon.BettingNarrative.MAX_HISTORY) % PokerAICommon.BettingNarrative.MAX_HISTORY;
        PokerAICommon.BettingAction last = narrative.history[lastIndex];
        
        if (last.action == PokerAICommon.InternalAction.CHECK) return true;
        
        if (last.action == PokerAICommon.InternalAction.CALL && wetBoard) return true;
        
        return false;
    }

    protected PokerAICommon.OpponentHandEstimate estimateOpponentHand(String currentAction, int betAmount, int potSize) {
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

        float betToPotRatio = (float) betAmount / Math.max(1, potSize);

        if (currentAction.equals("RAISE") || currentAction.equals("ALL_IN")) {
            if (betToPotRatio > 1.5f) {
                float polarizedBluffRate = estimate.bluffProbability * 1.5f;
                estimate.premiumProbability *= 2.0f;
                estimate.strongProbability *= 1.5f;
                estimate.weakProbability *= 0.5f;
                estimate.bluffProbability = Math.min(polarizedBluffRate, 0.40f);
                estimate.valueProbability = 1.0f - estimate.bluffProbability;
            } else if (betToPotRatio > 0.7f) {
                estimate.premiumProbability *= 1.5f;
                estimate.strongProbability *= 1.3f;
                estimate.playableProbability *= 0.8f;
                estimate.weakProbability *= 0.6f;
                estimate.bluffProbability *= 0.8f;
            } else {
                estimate.premiumProbability *= 1.2f;
                estimate.strongProbability *= 1.1f;
                estimate.bluffProbability *= 1.1f;
            }
        } else if (currentAction.equals("CALL")) {
            estimate.premiumProbability *= 0.5f;
            estimate.strongProbability *= 1.2f;
            estimate.playableProbability *= 1.3f;
            estimate.bluffProbability *= 0.5f;
            estimate.valueProbability = 1.0f - estimate.bluffProbability;
        }

        if (raisesThisRound >= 2) {
            estimate.premiumProbability *= 1.8f;
            estimate.strongProbability *= 1.4f;
            estimate.weakProbability *= 0.3f;
            estimate.bluffProbability *= 0.4f;
        } else if (raisesThisRound == 1) {
            estimate.premiumProbability *= 1.3f;
            estimate.strongProbability *= 1.2f;
            estimate.weakProbability *= 0.7f;
        }

        float totalHandProb = estimate.premiumProbability + estimate.strongProbability +
                             estimate.playableProbability + estimate.weakProbability;
        estimate.premiumProbability /= totalHandProb;
        estimate.strongProbability /= totalHandProb;
        estimate.playableProbability /= totalHandProb;
        estimate.weakProbability /= totalHandProb;

        return estimate;
    }

    protected boolean shouldFoldBasedOnHandReading(PokerAICommon.OpponentHandEstimate estimate, float ourEquity,
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
}