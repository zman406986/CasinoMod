package data.scripts.casino.cards.pokerShared;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

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
    protected int totalPlayerFolds = 0;

    protected int playerAllInCount = 0;
    protected int playerAllInOpportunities = 0;
    protected boolean playerIsAllInLoose = false;

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
}