package data.scripts.casino.cards.pokerShared;

public class PokerAICommon {

    public enum Personality { TIGHT, AGGRESSIVE, CALCULATED }

    public enum NarrativeType {
        PASSIVE_WEAK, PASSIVE_DRAWING, HIT_THE_BOARD, STRONG_ALL_ALONG, 
        TRAP_UNVEILED, POLARIZED, NEUTRAL
    }

    public enum DeceptionMode {
        TRAP, BLUFF_CONTINUATION, BLUFF_INITIATE, FLOAT_BLUFF, HONEST
    }

    public enum PlayerStyle { UNKNOWN, PASSIVE, BALANCED, AGGRESSIVE }

    public enum InternalAction { FOLD, CALL, RAISE, CHECK, BET }

    public static class AIResponse {
        public InternalAction action;
        public int raiseAmount;
        public AIResponse(InternalAction a, int amt) { action = a; raiseAmount = amt; }
    }

    public static class BettingAction {
        public PokerRound round;
        public InternalAction action;
        public float betToPotRatio;
        public boolean wasInitiator;

        public BettingAction(PokerRound r, InternalAction a, float ratio, boolean initiator) {
            this.round = r;
            this.action = a;
            this.betToPotRatio = ratio;
            this.wasInitiator = initiator;
        }
    }

    public static class BettingNarrative {
        public static final int MAX_HISTORY = 6;
        public BettingAction[] history = new BettingAction[MAX_HISTORY];
        public int historyCount = 0;
        public int historyIndex = 0;

        public float aggregateAggression = 0f;
        public boolean hasInitiated = false;
        public float avgBetRatio = 0f;
        public int betCount = 0;
        public NarrativeType type = NarrativeType.NEUTRAL;

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
                if (a.action == InternalAction.RAISE || a.action == InternalAction.BET) {
                    aggressionSum += 1.0f;
                    betRatioSum += a.betToPotRatio;
                    betCount++;
                    if (a.wasInitiator) hasInitiated = true;
                } else if (a.action == InternalAction.CALL) {
                    aggressionSum += 0.3f;
                }
            }

            aggregateAggression = aggressionSum / historyCount;
            avgBetRatio = betCount > 0 ? betRatioSum / betCount : 0f;
            this.betCount = betCount;
            type = classifyNarrative();
        }

        public NarrativeType classifyNarrative() {
            if (historyCount < 2) return NarrativeType.NEUTRAL;

            boolean allChecks = true;
            for (int i = 0; i < historyCount; i++) {
                if (history[i].action != InternalAction.CHECK) {
                    allChecks = false;
                    break;
                }
            }
            if (allChecks) return NarrativeType.PASSIVE_WEAK;

            boolean checkCallPattern = true;
            for (int i = 0; i < historyCount; i++) {
                InternalAction a = history[i].action;
                if (a != InternalAction.CHECK && a != InternalAction.CALL) {
                    checkCallPattern = false;
                    break;
                }
            }
            if (checkCallPattern && countCalls() >= 1) {
                return NarrativeType.PASSIVE_DRAWING;
            }

            if (hasCheckThenBetPattern()) return NarrativeType.HIT_THE_BOARD;
            if (hasTrapPattern()) return NarrativeType.TRAP_UNVEILED;

            if (aggregateAggression > 0.7f) {
                return NarrativeType.STRONG_ALL_ALONG;
            }

            if (betCount >= 2 && hasPolarizedBetSizes()) {
                return NarrativeType.POLARIZED;
            }

            return NarrativeType.NEUTRAL;
        }

        public void reset() {
            historyCount = 0;
            historyIndex = 0;
            aggregateAggression = 0f;
            hasInitiated = false;
            avgBetRatio = 0f;
            betCount = 0;
            type = NarrativeType.NEUTRAL;
            for (int i = 0; i < MAX_HISTORY; i++) history[i] = null;
        }

        private boolean hasCheckThenBetPattern() {
            boolean sawCheck = false;
            for (int i = 0; i < historyCount; i++) {
                if (history[i].action == InternalAction.CHECK) sawCheck = true;
                else if (sawCheck && (history[i].action == InternalAction.BET || history[i].action == InternalAction.RAISE)) {
                    return true;
                }
            }
            return false;
        }

        private boolean hasTrapPattern() {
            int checksBeforeRaise = 0;
            for (int i = 0; i < historyCount; i++) {
                if (history[i].action == InternalAction.CHECK) checksBeforeRaise++;
                else if (history[i].action == InternalAction.RAISE && checksBeforeRaise >= 2) return true;
            }
            return false;
        }

        private boolean hasPolarizedBetSizes() {
            float minRatio = Float.MAX_VALUE;
            float maxRatio = -Float.MAX_VALUE;
            for (int i = 0; i < historyCount; i++) {
                if (history[i].action == InternalAction.RAISE || history[i].action == InternalAction.BET) {
                    minRatio = Math.min(minRatio, history[i].betToPotRatio);
                    maxRatio = Math.max(maxRatio, history[i].betToPotRatio);
                }
            }
            return (maxRatio - minRatio) > 1.0f;
        }

        private int countCalls() {
            int count = 0;
            for (int i = 0; i < historyCount; i++) {
                if (history[i].action == InternalAction.CALL) count++;
            }
            return count;
        }
    }

    public static class ShowdownRecord {
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

    public static class PlayerProfile {
        public float aggressionConfidence = 0f;
        public float passivityConfidence = 0f;
        public float bluffLikelihood = 0f;
        public float trapLikelihood = 0f;
        public float loosePlayLikelihood = 0f;
        public float tightPlayLikelihood = 0f;
        public float totalConfidence = 0f;

        public void reset() {
            aggressionConfidence = 0f;
            passivityConfidence = 0f;
            bluffLikelihood = 0f;
            trapLikelihood = 0f;
            loosePlayLikelihood = 0f;
            tightPlayLikelihood = 0f;
            totalConfidence = 0f;
        }

        public Personality derivePersonality() {
            if (aggressionConfidence > passivityConfidence + 0.15f) return Personality.TIGHT;
            if (passivityConfidence > aggressionConfidence + 0.15f) return Personality.AGGRESSIVE;
            return Personality.CALCULATED;
        }
    }

    public static class OpponentHandEstimate {
        public float premiumProbability;
        public float strongProbability;
        public float playableProbability;
        public float weakProbability;
        public float bluffProbability;
        public float valueProbability;

        public OpponentHandEstimate() {
            this.premiumProbability = 0.05f;
            this.strongProbability = 0.15f;
            this.playableProbability = 0.25f;
            this.weakProbability = 0.55f;
            this.bluffProbability = 0.2f;
            this.valueProbability = 0.8f;
        }

        public float getBluffProbability() { return bluffProbability; }
        public float getValueProbability() { return valueProbability; }
    }

    public static class MonteCarloResult {
        public int wins;
        public int ties;
        public int losses;
        public int samples;

        public MonteCarloResult(int wins, int ties, int losses, int samples) {
            this.wins = wins;
            this.ties = ties;
            this.losses = losses;
            this.samples = samples;
        }

        public float getTotalEquity() { return (wins + ties * 0.5f) / samples; }
    }
}
