package data.scripts.casino.poker2;

import java.util.*;

import data.scripts.casino.cards.Card;
import data.scripts.casino.cards.Deck;
import data.scripts.casino.cards.GameType;

public class PokerOpponentAI {
    private final Random random = new Random();
    public enum Action { FOLD, CALL, RAISE, CHECK, BET }
    public enum Personality { TIGHT, AGGRESSIVE, CALCULATED }
    public enum PlayerStyle { UNKNOWN, PASSIVE, BALANCED, AGGRESSIVE }
    
    public enum NarrativeType {
        PASSIVE_WEAK, PASSIVE_DRAWING, HIT_THE_BOARD, STRONG_ALL_ALONG, TRAP_UNVEILED, POLARIZED, NEUTRAL
    }
    
    public enum DeceptionMode {
        TRAP, BLUFF_CONTINUATION, BLUFF_INITIATE, FLOAT_BLUFF, HONEST
    }
    
    public static class AIResponse {
        public Action action;
        public int raiseAmount; 
        public AIResponse(Action a, int amt) { action=a; raiseAmount=amt; }
    }
    
    public static class BettingAction {
        public PokerGame.Round round;
        public Action action;
        public float betToPotRatio;
        public boolean wasInitiator;
        
        public BettingAction(PokerGame.Round r, Action a, float ratio, boolean initiator) {
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
            type = classifyNarrative();
        }
        
        public NarrativeType classifyNarrative() {
            if (historyCount < 2) return NarrativeType.NEUTRAL;
            
            boolean allChecks = true;
            for (int i = 0; i < historyCount; i++) {
                if (history[i].action != Action.CHECK)
                {
                    allChecks = false;
                    break;
                }
            }
            if (allChecks) return NarrativeType.PASSIVE_WEAK;
            
            boolean checkCallPattern = true;
            for (int i = 0; i < historyCount; i++) {
                Action a = history[i].action;
                if (a != Action.CHECK && a != Action.CALL)
                {
                    checkCallPattern = false;
                    break;
                }
            }
            if (checkCallPattern && countActions() >= 1) {
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
        
        private boolean hasCheckThenBetPattern() {
            boolean sawCheck = false;
            for (int i = 0; i < historyCount; i++) {
                if (history[i].action == Action.CHECK) sawCheck = true;
                else if (sawCheck && (history[i].action == Action.BET || history[i].action == Action.RAISE)) {
                    return true;
                }
            }
            return false;
        }
        
        private boolean hasTrapPattern() {
            int checksBeforeRaise = 0;
            for (int i = 0; i < historyCount; i++) {
                if (history[i].action == Action.CHECK) checksBeforeRaise++;
                else if (history[i].action == Action.RAISE && checksBeforeRaise >= 2) return true;
            }
            return false;
        }
        
        private boolean hasPolarizedBetSizes() {
            float minRatio = Float.MAX_VALUE;
            float maxRatio = -Float.MAX_VALUE;
            for (int i = 0; i < historyCount; i++) {
                if (history[i].action == Action.RAISE || history[i].action == Action.BET) {
                    minRatio = Math.min(minRatio, history[i].betToPotRatio);
                    maxRatio = Math.max(maxRatio, history[i].betToPotRatio);
                }
            }
            return (maxRatio - minRatio) > 1.0f;
        }
        
        private int countActions() {
            int count = 0;
            for (int i = 0; i < historyCount; i++) {
                if (history[i].action == Action.CALL) count++;
            }
            return count;
        }
    }
    
    // Monte Carlo result holder
    @SuppressWarnings("unused")
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

        // Note: These probability methods are available for future use in AI decision-making
        public float getWinProbability() { return (float) wins / samples; }
        public float getTieProbability() { return (float) ties / samples; }
        public float getLossProbability() { return (float) losses / samples; }
        public float getTotalEquity() { return (wins + ties * 0.5f) / samples; }
    }

    // Opponent hand estimate for hand reading
    public static class OpponentHandEstimate {
        public float premiumProbability;      // AA-TT, AK (top 5% of hands)
        public float strongProbability;       // 99-22, AQ, KQ, suited connectors (next 15%)
        public float playableProbability;     // Weak pairs, suited aces, broadways (next 25%)
        public float weakProbability;         // Everything else (55%)
        public float bluffProbability;        // Probability current action is a bluff
        public float valueProbability;        // Probability current action is for value

        public OpponentHandEstimate() {
            // Default uniform distribution
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

    // Unified Player Profile System - aggregates all subsystem data with confidence & decay
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
    
    private final PlayerProfile profile = new PlayerProfile();

    // Showdown history tracking for player hand analysis (with decay)
    public static class ShowdownRecord {
        public int handRankValue;
        public float lastBetRatio;
        public boolean won;
        public boolean wasBluff;
        public int recordedAtHand; // For decay calculation
        
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

    // Fast action tracking for immediate personality adaptation (with decay)
    private static final int RECENT_ACTION_SIZE = 10;
    private final int[] recentActions = new int[RECENT_ACTION_SIZE];
    private int recentActionIndex = 0;
    private int recentActionCount = 0;
    
    // Showdown history buffer
    private static final int SHOWDOWN_HISTORY_SIZE = 10;
    private final ShowdownRecord[] showdownHistory = new ShowdownRecord[SHOWDOWN_HISTORY_SIZE];
    private int showdownHistoryIndex = 0;
    private int showdownHistoryCount = 0;
    
    // Betting narrative for deception system
    private BettingNarrative narrative = new BettingNarrative();

    // Core AI state variables
    private float aggressionMeter = 0.5f;                    // 0.0 (passive) to 1.0 (aggressive)
    private final float[] aggressionHistory = new float[10];      // Circular buffer for smoothing
    private int historyIndex = 0;                            // Index for circular buffer
    private int playerStyle = 0;                             // 0: unknown, 1: passive, 2: balanced, 3: aggressive
    private int totalPlayerActions = 0;
    private int totalRaises = 0;
    private int totalCalls = 0;
    // Note: totalFolds tracked for potential future statistics display
    @SuppressWarnings("unused")
    private int totalFolds = 0;
    private int handsPlayed = 0;
    private int vpipCount = 0;                               // Voluntarily Put $ In Pot
    private int pfrCount = 0;                                // Pre-Flop Raise count
    private Personality personality = Personality.CALCULATED;

    // Hand strength classification for preflop decisions
    private enum HandCategory {
        PREMIUM,      // AA, KK, QQ, JJ, AKs, AKo - call any all-in
        STRONG,       // TT-99, AQs-AJs, KQs, AQo - call all-in with good odds
        PLAYABLE,     // 88-22, ATs-A2s, KJs-KTs, QJs, JTs - fold to all-in
        WEAK          // Everything else - always fold to all-in
    }

    // Player all-in tracking for adaptation
    private int playerAllInCount = 0;                        // Times player went all-in preflop
    private int playerAllInOpportunities = 0;                // Hands where all-in was possible
    private boolean playerIsAllInLoose = false;              // True if player shoves too much
    
    // Position tracking
    private boolean isInPosition = false;                    // Is AI acting after player (dealer position)
    
    // Anti-gullibility tracking
    private int playerBetsWithoutShowdown = 0;               // Times player bet and AI folded
    private int playerBetsTotal = 0;                         // Total player bets
    private int consecutiveBluffsCaught = 0;                 // Consecutive times AI folded to player bluff
    private int suspiciousModeHands = 0;                     // Remaining hands in suspicious mode
    private boolean isSuspicious = false;                    // Currently suspicious of player bluffs
    private int playerCheckRaises = 0;                       // Track check-raise frequency
    private int handsSinceLastShowdown = 0;                  // Hands without seeing player cards
    private int timesBluffedByPlayer = 0;                    // Times player successfully bluffed AI
    private int largeBetsWithoutShowdown = 0;                // Track oversized bets that didn't show down
    int aiCommittedThisRound = 0;                            // Chips AI has committed in current betting round (package-private for PokerGame access)

    // Betting round tracking to prevent raise spirals
    private int raisesThisRound = 0;                         // Number of raises in current betting round
    private boolean aiHasRaisedThisRound = false;            // Whether AI has already raised this round
    private int totalPotThisRound = 0;                       // Track pot size at start of betting round

    public PokerOpponentAI() {
        Arrays.fill(aggressionHistory, 0.5f);
    }

    private void updateProfile() {
        profile.reset();
        contributeFromRecentActions();
        contributeFromShowdownHistory();
        contributeFromLongTermStats();
        contributeFromAllInTracking();
        personality = profile.derivePersonality();
    }
    
    private void contributeFromRecentActions() {
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
            
            profile.bluffLikelihood = bluffRatio;
            profile.trapLikelihood = trapRatio;
            
            if (bluffRatio > 0.15f) {
                profile.aggressionConfidence += bluffRatio * 0.8f;
                profile.totalConfidence += bluffRatio * 0.6f;
            }
            if (trapRatio > 0.2f) {
                profile.passivityConfidence += trapRatio * 0.5f;
                profile.totalConfidence += trapRatio * 0.4f;
            }
            
            profile.totalConfidence += Math.min(showdownHistoryCount * 0.1f, 0.4f);
        }
    }
    
    private void contributeFromLongTermStats() {
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
    
    private void trackRecentAction(int actionType) {
        recentActions[recentActionIndex] = actionType;
        recentActionIndex = (recentActionIndex + 1) % RECENT_ACTION_SIZE;
        if (recentActionCount < RECENT_ACTION_SIZE) recentActionCount++;
    }
    
    public void trackShowdownDetails(int handRankValue, float lastBetRatio, boolean won, boolean wasBluff) {
        showdownHistory[showdownHistoryIndex] = new ShowdownRecord(handRankValue, lastBetRatio, won, wasBluff, handsPlayed);
        showdownHistoryIndex = (showdownHistoryIndex + 1) % SHOWDOWN_HISTORY_SIZE;
        if (showdownHistoryCount < SHOWDOWN_HISTORY_SIZE) showdownHistoryCount++;
        
        updateProfile();
    }
    
    private void recordBettingAction(PokerGame.Round round, Action action, int betAmount, int potSize, boolean wasInitiator) {
        float betRatio = (action == Action.RAISE || action == Action.BET) ? (float) betAmount / Math.max(1, potSize) : 0f;
        BettingAction ba = new BettingAction(round, action, betRatio, wasInitiator);
        narrative.addAction(ba);
    }
    
    private float getPerceivedStrength(NarrativeType narrativeType, boolean wetBoard) {
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
            if (narrativeType == NarrativeType.PASSIVE_DRAWING) {
                baseStrength += 0.10f;
            }
            if (narrativeType == NarrativeType.HIT_THE_BOARD) {
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
    
    private DeceptionMode selectDeceptionMode(float trueEquity, float perceivedStrength, boolean wetBoard) {
        if (trueEquity > 0.70f && perceivedStrength < 0.40f) {
            return DeceptionMode.TRAP;
        }
        
        if (trueEquity > 0.65f && perceivedStrength < 0.50f && !narrative.hasInitiated) {
            return DeceptionMode.TRAP;
        }
        
        if (trueEquity < 0.35f && perceivedStrength > 0.50f) {
            if (!shouldBeSuspicious()) {
                return DeceptionMode.BLUFF_CONTINUATION;
            }
        }
        
        if (trueEquity < 0.40f && canPivotToBluff(wetBoard)) {
            return DeceptionMode.BLUFF_INITIATE;
        }
        
        if (trueEquity < 0.40f && wetBoard && narrative.type == NarrativeType.PASSIVE_DRAWING) {
            return DeceptionMode.FLOAT_BLUFF;
        }
        
        return DeceptionMode.HONEST;
    }
    
    private float computeDeceptionEquity(DeceptionMode mode, float trueEquity, float perceivedStrength) {
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
        
        int lastIndex = (narrative.historyIndex - 1 + BettingNarrative.MAX_HISTORY) % BettingNarrative.MAX_HISTORY;
        BettingAction last = narrative.history[lastIndex];
        
        if (last.action == Action.CHECK) return true;
        
        if (last.action == Action.CALL && wetBoard) return true;
        
        return false;
    }


    private String estimatePlayerRange() {
        // Default range for unknown players
        String baseRange = "random";

        return switch (PlayerStyle.values()[playerStyle])
        {
            case PASSIVE -> "tight_range";  // 25% of hands
            case AGGRESSIVE -> "wide_range";   // 60% of hands
            case BALANCED -> "standard_range";  // 40% of hands
            default -> baseRange;
        };
    }

    private AIResponse randomDeviation(float equity, float potOdds, int stackSize, int potSize) {
        int deviationType = random.nextInt(4) + 1; // 1-4

        switch(deviationType) {
            case 1: // Hero call
                if (equity > potOdds * 0.8f) {
                    return new AIResponse(Action.CALL, 0);
                }
                break;
            case 2: // Bluff raise
                if (equity < 0.4f && random.nextFloat() < 0.3f) {
                    int bluffRaise = (int)(potSize * 0.5f);
                    bluffRaise = Math.min(bluffRaise, stackSize);
                    return new AIResponse(Action.RAISE, bluffRaise);
                }
                break;
            case 3: // Check with strong hand
                if (equity > 0.7f) {
                    return new AIResponse(Action.CALL, 0); // Slow play - just call
                }
                break;
            case 4: // Overbet
                if (equity > 0.75f) {
                    int overbet = (int)(potSize * 1.5f);
                    overbet = Math.min(overbet, stackSize);
                    return new AIResponse(Action.RAISE, overbet);
                }
                break;
        }

        // Default decision if no condition met
        if (equity > potOdds) {
            return new AIResponse(Action.CALL, 0);
        } else {
            return new AIResponse(Action.FOLD, 0);
        }
    }
    
    public AIResponse decide(List<Card> holeCards, List<Card> communityCards,
                            int currentBetToCall, int potSize, int stackSize) {
        // Determine if this is pre-flop or post-flop
        if (communityCards.isEmpty()) {
            return preFlopDecision(holeCards, currentBetToCall, potSize, stackSize);
        } else {
            return postFlopDecision(holeCards, communityCards, currentBetToCall, potSize, stackSize);
        }
    }
    
    public AIResponse decideAllInResponse(List<Card> holeCards, List<Card> communityCards,
                            int currentBetToCall, int potSize) {
        // Player is all-in - AI can only call or fold, no raising allowed
        float equity = communityCards.isEmpty() ? 
            calculatePreflopEquity(holeCards) : 
            calculatePostflopEquity(holeCards, communityCards);
        
        float potOdds = (float) currentBetToCall / (potSize + currentBetToCall);
        
        // Only call if equity justifies the call
        if (equity > potOdds) {
            return new AIResponse(Action.CALL, 0);
        } else {
            // Fold if pot odds don't justify calling
            return new AIResponse(Action.FOLD, 0);
        }
    }
    
    private AIResponse preFlopDecision(List<Card> holeCards, int currentBetToCall, int potSize, int stackSize) {
        updateProfile();

        float equity = calculatePreflopEquity(holeCards);

        int bigBlind = Math.max(1, potSize / 3);
        float bbStack = (float) stackSize / bigBlind;

        float positionBonus = isInPosition ? 0.05f : -0.03f;
        equity += positionBonus;

        if (bbStack < 15 && currentBetToCall > 0) {
            AIResponse shortStackResult = shortStackDecision(equity, currentBetToCall, potSize, stackSize, bbStack);
            boolean wasInitiator = false;
            recordBettingAction(PokerGame.Round.PREFLOP, shortStackResult.action, shortStackResult.raiseAmount, potSize, wasInitiator);
            return shortStackResult;
        }

        AIResponse decision;
        if (currentBetToCall == 0) {
            float openThreshold = isInPosition ? 0.32f : 0.45f;
            float bluffChance = isInPosition ? 0.35f : 0.20f;

            if (equity >= openThreshold || (equity >= 0.30f && random.nextFloat() < bluffChance)) {
                int raiseAmount = Math.min(stackSize / 20, stackSize);
                raiseAmount = Math.max(bigBlind * 3, raiseAmount);
                if (isInPosition) {
                    raiseAmount = (int)(raiseAmount * 1.2f);
                } else {
                    raiseAmount = (int)(raiseAmount * 0.85f);
                }
                raiseAmount = Math.max(CasinoConfig.POKER_AI_MIN_RAISE_VALUE, raiseAmount);
                decision = new AIResponse(Action.RAISE, raiseAmount);
            } else {
                decision = new AIResponse(Action.CALL, 0);
            }
        } else {
            float potOdds = (float)currentBetToCall / (potSize + currentBetToCall);

            if (currentBetToCall == bigBlind) {
                if (equity > 0.60f && random.nextFloat() < 0.4f) {
                    int threeBetSize = bigBlind * 3;
                    threeBetSize = Math.min(threeBetSize, stackSize);
                    decision = new AIResponse(Action.RAISE, Math.max(threeBetSize, CasinoConfig.POKER_AI_MIN_RAISE_VALUE));
                } else {
                    decision = new AIResponse(Action.CALL, 0);
                }
            } else {
                boolean isAllIn = currentBetToCall >= stackSize * 0.9f;
                if (isAllIn) {
                    HandCategory handStrength = classifyPreflopHand(holeCards);
                    trackPlayerAllIn(true);
                    if (!shouldCallPreflopAllIn(handStrength, potOdds, bbStack)) {
                        decision = new AIResponse(Action.FOLD, 0);
                    } else {
                        decision = new AIResponse(Action.CALL, 0);
                    }
                } else {
                    trackPlayerAllIn(false);

                    float evCall = calculateCallEV(equity, potSize, currentBetToCall);
                    float evFold = -aiCommittedThisRound;

                    int threeBetSize = currentBetToCall * 3;
                    threeBetSize = Math.max(threeBetSize, bigBlind * 9);
                    threeBetSize = Math.min(threeBetSize, stackSize - currentBetToCall);

                    String playerRange = estimatePlayerRange();
                    float foldProb = estimateFoldProbability(playerRange, potSize + currentBetToCall, threeBetSize);
                    float evRaise = calculateRaiseEV(equity, potSize, currentBetToCall, threeBetSize, foldProb);

                    if (equity < 0.55f) {
                        float bluffEV = calculateBluffEV(foldProb, potSize, threeBetSize);
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
                        decision = new AIResponse(Action.RAISE, threeBetSize);
                    } else if (evCall > evFold) {
                        decision = new AIResponse(Action.CALL, 0);
                    } else {
                        decision = new AIResponse(Action.FOLD, 0);
                    }
                }
            }
        }

        boolean wasInitiator = decision.action == Action.RAISE && currentBetToCall == 0;
        recordBettingAction(PokerGame.Round.PREFLOP, decision.action, decision.raiseAmount, potSize, wasInitiator);
        return decision;
    }
    
    private AIResponse shortStackDecision(float equity, int currentBetToCall, int potSize, int stackSize, float bbStack) {
        // Push/fold strategy for short stacks
        float potOdds = (float)currentBetToCall / (potSize + currentBetToCall);
        
        // Very short stack (< 10 BB): push only with strong hands
        // Tightened to prevent easy all-in exploitation
        if (bbStack < 10) {
            if (equity > 0.60f || (equity > 0.50f && random.nextFloat() < 0.4f)) {
                // All-in only with strong hands (reduced from 50% to 60% threshold, 40% bluff vs 70%)
                return new AIResponse(Action.RAISE, stackSize);
            } else if (potOdds < 0.20f && equity > 0.45f) {
                // Call only with excellent pot odds and decent equity
                return new AIResponse(Action.CALL, 0);
            } else {
                return new AIResponse(Action.FOLD, 0);
            }
        }
        
        // Short stack (10-15 BB): more selective to prevent exploitation
        if (equity > 0.60f || (equity > 0.50f && random.nextFloat() < 0.4f)) {
            return new AIResponse(Action.RAISE, Math.min(stackSize, currentBetToCall * 3));
        } else if (equity > 0.45f) {
            return new AIResponse(Action.CALL, 0);
        } else {
            return new AIResponse(Action.FOLD, 0);
        }
    }
    
    private AIResponse postFlopDecision(List<Card> holeCards, List<Card> communityCards,
                                      int currentBetToCall, int potSize, int stackSize) {
        updateProfile();
        
        String playerRange = estimatePlayerRange();

        MonteCarloResult mcResult = runMonteCarloSimulationFull(holeCards, communityCards);
        float trueEquity = mcResult.getTotalEquity();

        float impliedOddsBonus = calculateImpliedOddsBonus(holeCards, communityCards, trueEquity);
        float adjustedTrueEquity = trueEquity + impliedOddsBonus;

        boolean wetBoard = isWetBoard(communityCards);
        PokerGame.Round currentRound = communityCards.size() == 3 ? PokerGame.Round.FLOP : 
                             communityCards.size() == 4 ? PokerGame.Round.TURN : PokerGame.Round.RIVER;
        
        float perceivedStrength = getPerceivedStrength(narrative.type, wetBoard);
        DeceptionMode mode = selectDeceptionMode(trueEquity, perceivedStrength, wetBoard);
        float deceptionEquity = computeDeceptionEquity(mode, adjustedTrueEquity, perceivedStrength);

        return postFlopEVDecision(currentBetToCall, potSize, stackSize, playerRange, 
                                  deceptionEquity, wetBoard, trueEquity, perceivedStrength, mode, currentRound);
    }
    
    private float calculateImpliedOddsBonus(List<Card> holeCards, List<Card> communityCards, float currentEquity) {
        // Add equity for drawing hands based on outs
        int outs = countOuts(holeCards, communityCards);
        int streetsRemaining = 5 - communityCards.size();
        
        // Each out is worth ~2% per street
        float drawEquity = outs * 0.02f * streetsRemaining;
        
        // Only add if we have a reasonable draw but not made hand
        if (currentEquity < 0.60f && drawEquity > 0.05f) {
            return Math.min(drawEquity * 0.5f, 0.15f); // Cap at 15% bonus
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

    private AIResponse postFlopEVDecision(int currentBetToCall, int potSize, int stackSize, String playerRange, float equity, boolean isWetBoard, float trueEquity, float perceivedStrength, DeceptionMode mode, PokerGame.Round currentRound) {
        float evFold = -aiCommittedThisRound;
        float evCall = calculateCallEV(equity, potSize, currentBetToCall);
        
        float positionFoldPenalty = isInPosition ? 0.0f : 0.08f * potSize;
        float positionCallBonus = isInPosition ? 0.05f * potSize : 0.0f;
        float positionRaiseBonus = isInPosition ? 0.04f * potSize : -0.02f * potSize;
        
        evFold += positionFoldPenalty;
        evCall += positionCallBonus;

        int[] raiseSizes = getInts(potSize, mode);

        float bestRaiseEV = Float.NEGATIVE_INFINITY;
        int bestRaiseSize = 0;

        // If AI cannot call the current bet (not enough chips), disable raising
        // AI can only go all-in (call with entire stack) or fold
        boolean canRaise = currentBetToCall < stackSize;

        if (canRaise) {
            for (int raiseSize : raiseSizes) {
                if (raiseSize > stackSize) continue;

                float foldProb = estimateFoldProbability(playerRange, potSize, raiseSize);
                float raiseEV = calculateRaiseEV(equity, potSize, currentBetToCall, raiseSize, foldProb);

                // Adjust for bluffing: if equity is low, this is a bluff
                if (equity < 0.45f) {
                    float bluffEV = calculateBluffEV(foldProb, potSize, raiseSize);
                    raiseEV = Math.max(raiseEV, bluffEV);
                }

                if (raiseEV > bestRaiseEV) {
                    bestRaiseEV = raiseEV;
                    bestRaiseSize = raiseSize;
                }
            }
        }
        
        // Board texture adjustments
        if (isWetBoard) {
            // On wet boards: be more cautious with marginal hands, but bet bigger for protection with strong hands
            if (equity > 0.70f) {
                bestRaiseEV += 0.05f * potSize; // Boost raising with strong hands for protection
            } else if (equity < 0.50f) {
                evCall -= 0.05f * potSize; // Reduce calling with weak hands on wet boards
            }
        } else {
            // On dry boards: bluff more (harder for opponent to have a hand)
            if (equity < 0.45f) {
                bestRaiseEV += 0.03f * potSize; // Slight boost to bluffing on dry boards
            }
        }

        // Apply position-based raise bonus
        bestRaiseEV += positionRaiseBonus;

        // Apply personality adjustments to EV
        evFold = adjustEVForPersonality(evFold, Action.FOLD);
        evCall = adjustEVForPersonality(evCall, Action.CALL);
        bestRaiseEV = adjustEVForPersonality(bestRaiseEV, Action.RAISE);
        
        // Apply deception adjustments (trap/bluff continuation)
        evFold = adjustEVForDeception(evFold, Action.FOLD, trueEquity, perceivedStrength, potSize);
        evCall = adjustEVForDeception(evCall, Action.CALL, trueEquity, perceivedStrength, potSize);
        bestRaiseEV = adjustEVForDeception(bestRaiseEV, Action.RAISE, trueEquity, perceivedStrength, potSize);

        // Anti-gullibility: adjust if suspicious of player bluffs
        if (shouldBeSuspicious()) {
            evFold -= 0.1f * potSize; // Reduce fold EV (make calling more attractive)
            evCall += 0.05f * potSize; // Increase call EV
        }

        // Anti-gullibility: stubborn caller - don't fold to small bets
        if (shouldMakeStubbornCall(equity, currentBetToCall, potSize)) {
            evFold = Float.NEGATIVE_INFINITY; // Never fold
            evCall += 0.1f * potSize; // Boost call EV
        }

        // HAND READING: Estimate opponent hand and adjust decisions
        String playerAction = currentBetToCall > 0 ? "RAISE" : "CHECK";
        OpponentHandEstimate handEstimate = estimateOpponentHand(playerAction, currentBetToCall, potSize);

        // Adjust fold decision based on hand reading
        if (currentBetToCall > 0 && shouldFoldBasedOnHandReading(handEstimate, equity, currentBetToCall, potSize)) {
            evFold = Float.POSITIVE_INFINITY; // Force fold
            evCall = Float.NEGATIVE_INFINITY;
            bestRaiseEV = Float.NEGATIVE_INFINITY;
        } else if (handEstimate.getBluffProbability() > 0.40f && equity > 0.35f) {
            // Opponent likely bluffing, don't fold
            evFold = Float.NEGATIVE_INFINITY;
            evCall += 0.1f * potSize;
        }

        // RAISE SPIRAL PREVENTION
        if (shouldAvoidRaiseSpiral(stackSize, potSize)) {
            bestRaiseEV = Float.NEGATIVE_INFINITY; // Don't raise
        }

        // If pot-committed with decent equity, prefer calling
        if (isPotCommitted(stackSize) && equity > 0.40f && bestRaiseEV > evCall) {
            bestRaiseEV = evCall - 0.01f; // Make call slightly preferred
        }

        // Select best action based on EV
        AIResponse finalDecision;
        if (bestRaiseEV > evCall && bestRaiseEV > evFold && bestRaiseSize > 0) {
            // Ensure minimum raise
            bestRaiseSize = Math.max(bestRaiseSize, CasinoConfig.POKER_AI_MIN_RAISE_VALUE);
            bestRaiseSize = Math.min(bestRaiseSize, stackSize);
            finalDecision = new AIResponse(Action.RAISE, bestRaiseSize);
        } else if (evCall > evFold) {
            finalDecision = new AIResponse(Action.CALL, 0);
        } else {
            finalDecision = new AIResponse(Action.FOLD, 0);
        }

        // Apply random deviation for unpredictability (10% chance)
        // This makes the AI more human-like with occasional hero calls, bluffs, and slow plays
        if (random.nextFloat() < 0.10f) {
            float potOdds = currentBetToCall > 0 ? (float) currentBetToCall / (potSize + currentBetToCall) : 0f;
            AIResponse deviation = randomDeviation(equity, potOdds, stackSize, potSize);
            if (deviation.action != finalDecision.action || deviation.raiseAmount != finalDecision.raiseAmount) {
                boolean wasInitiator = deviation.action == Action.RAISE && currentBetToCall == 0;
                recordBettingAction(currentRound, deviation.action, deviation.raiseAmount, potSize, wasInitiator);
                return deviation;
            }
        }

        boolean wasInitiator = finalDecision.action == Action.RAISE && currentBetToCall == 0;
        recordBettingAction(currentRound, finalDecision.action, finalDecision.raiseAmount, potSize, wasInitiator);

        return finalDecision;
    }

    private int[] getInts(int potSize, DeceptionMode mode)
    {
        float baseRaiseMultiplier = switch (mode) {
            case TRAP -> 0.6f;
            case BLUFF_CONTINUATION -> 1.0f;
            case BLUFF_INITIATE -> 0.75f;
            case FLOAT_BLUFF -> 0.8f;
            case HONEST -> isInPosition ? 1.1f : 0.9f;
        };
        return new int[] {
            (int)(potSize * 0.5f * baseRaiseMultiplier),
            (int)(potSize * 1.0f * baseRaiseMultiplier),
            (int)(potSize * 2.0f * baseRaiseMultiplier)
        };
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
            default -> 1.0f;
        };
        
        float confidenceScale = Math.min(profile.totalConfidence, 1.0f);
        float baseMultiplier = 1.0f;
        float personalityStrength = multiplier - 1.0f;
        multiplier = baseMultiplier + personalityStrength * confidenceScale;
        
        if (profile.bluffLikelihood > 0.2f) {
            float bluffAdjust = profile.bluffLikelihood * confidenceScale;
            if (action == Action.FOLD) {
                multiplier *= (1.0f - bluffAdjust * 0.4f);
            } else if (action == Action.CALL) {
                multiplier *= (1.0f + bluffAdjust * 0.25f);
            }
        }
        
        if (profile.trapLikelihood > 0.25f) {
            float trapAdjust = profile.trapLikelihood * confidenceScale;
            if (action == Action.RAISE) {
                multiplier *= (1.0f - trapAdjust * 0.2f);
            }
        }
        
        return ev * multiplier;
    }
    
    private float adjustEVForDeception(float ev, Action action, float trueEquity, float perceivedEquity, int potSize) {
        float equityGap = Math.abs(trueEquity - perceivedEquity);
        if (equityGap < 0.20f) return ev;
        
        float deceptionBonus = 0f;
        
        if (trueEquity > 0.70f && perceivedEquity < 0.50f) {
            if (action == Action.RAISE) {
                deceptionBonus = -0.08f * potSize;
            } else if (action == Action.CALL) {
                deceptionBonus = 0.05f * potSize;
            }
        }
        
        if (trueEquity < 0.40f && perceivedEquity > 0.55f) {
            if (action == Action.RAISE) {
                deceptionBonus = 0.06f * potSize;
            }
        }
        
        return ev + deceptionBonus;
    }
    
    // Static cache for preflop equities (169 unique starting hands)
    private static final Map<String, Float> preflopEquityCache = new HashMap<>();
    private static boolean preflopCacheInitialized = false;
    
    // Pre-computed card array for Monte Carlo simulations
    private static final Card[] allCardsArray = new Card[52];
    
    static {
        Deck deck = new Deck(GameType.POKER);
        for (int i = 0; i < 52; i++) {
            allCardsArray[i] = deck.cards.get(i);
        }
    }

    private void initializePreflopEquityCache() {
        if (preflopCacheInitialized) return;
        
        // Generate all 169 unique starting hand combinations
        Deck deck = new Deck(GameType.POKER);
        for (int i = 0; i < deck.cards.size(); i++) {
            for (int j = i + 1; j < deck.cards.size(); j++) {
                Card c1 = deck.cards.get(i);
                Card c2 = deck.cards.get(j);
                
                // Create canonical key (sorted by rank, suitedness)
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
        
        int[] cardIndices = new int[52];
        boolean[] excluded = new boolean[52];
        
        int c1Idx = -1, c2Idx = -1;
        for (int i = 0; i < 52; i++) {
            if (allCardsArray[i] == c1) c1Idx = i;
            if (allCardsArray[i] == c2) c2Idx = i;
        }
        
        for (int i = 0; i < 52; i++) {
            cardIndices[i] = i;
            excluded[i] = (i == c1Idx || i == c2Idx);
        }
        
        for (int i = 0; i < samples; i++) {
            int validCount = 0;
            for (int j = 0; j < 52; j++) {
                if (!excluded[j]) cardIndices[validCount++] = j;
            }
            
            for (int j = validCount - 1; j > 0; j--) {
                int swapIdx = random.nextInt(j + 1);
                int tmp = cardIndices[j];
                cardIndices[j] = cardIndices[swapIdx];
                cardIndices[swapIdx] = tmp;
            }
            
            Card[] boardCards = new Card[5];
            for (int j = 0; j < 5; j++) boardCards[j] = allCardsArray[cardIndices[j]];
            
            Card opp1 = allCardsArray[cardIndices[5]];
            Card opp2 = allCardsArray[cardIndices[6]];
            
            PokerGame.PokerGameLogic.HandScore ourScore = evaluateTwoCardsFast(c1, c2, boardCards);
            PokerGame.PokerGameLogic.HandScore oppScore = evaluateTwoCardsFast(opp1, opp2, boardCards);
            
            int cmp = ourScore.compareTo(oppScore);
            if (cmp > 0) wins++;
            else if (cmp == 0) ties++;
        }
        
        return (wins + ties * 0.5f) / samples;
    }
    
    private float calculatePreflopEquity(List<Card> holeCards) {
        // Initialize cache on first use
        initializePreflopEquityCache();
        
        // Lookup from cache
        Card c1 = holeCards.get(0);
        Card c2 = holeCards.get(1);
        String key = createHandKey(c1, c2);
        
        Float cached = preflopEquityCache.get(key);
        if (cached != null) {
            return cached;
        }
        
        // Fallback to simple calculation (shouldn't happen)
        return calculatePreflopEquitySimple(holeCards);
    }
    
    private float calculatePostflopEquity(List<Card> holeCards, List<Card> communityCards) {
        return runMonteCarloSimulation(holeCards, communityCards);
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

        return 0.30f;                                    // Trash
    }

   //Classifies a preflop hand into categories for decision-making,more accurate than raw equity for all-in decisions.
    private HandCategory classifyPreflopHand(List<Card> holeCards) {
        Card c1 = holeCards.get(0);
        Card c2 = holeCards.get(1);
        int v1 = Math.max(c1.rank().getValue(GameType.POKER), c2.rank().getValue(GameType.POKER));
        int v2 = Math.min(c1.rank().getValue(GameType.POKER), c2.rank().getValue(GameType.POKER));
        boolean suited = (c1.suit() == c2.suit());

        // Pairs: JJ+ = PREMIUM, TT-99 = STRONG, 88-22 = PLAYABLE
        if (v1 == v2) {
            if (v1 >= 11) return HandCategory.PREMIUM;
            if (v1 >= 9) return HandCategory.STRONG;
            return HandCategory.PLAYABLE;
        }

        // AK = PREMIUM
        if (v1 == 14 && v2 == 13) return HandCategory.PREMIUM;

        // Strong: AQs-AJs, KQs, AQo
        if ((v1 == 14 && v2 >= 11 && suited) ||
            (v1 == 13 && v2 == 12 && suited) ||
            (v1 == 14 && v2 == 12)) return HandCategory.STRONG;

        // Playable suited: Ax(s), KJ(s)+, QJs, JTs, T9s+
        if (suited && ((v1 == 14) || (v1 == 13 && v2 >= 10) ||
            (v1 - v2 == 1 && v1 >= 10))) return HandCategory.PLAYABLE;

        return HandCategory.WEAK;
    }

    /**
     * Determines if AI should call a preflop all-in based on hand strength and situation.
     * Uses tight ranges to prevent exploitation.
     */
    private boolean shouldCallPreflopAllIn(HandCategory handStrength, float potOdds, float bbStack) {
        // PREMIUM: Always call with JJ+, AK
        if (handStrength == HandCategory.PREMIUM) {
            return true;
        }

        // STRONG: Call TT-99, AQs-AJs, KQs, AQo only with decent pot odds
        if (handStrength == HandCategory.STRONG) {
            // Need at least 40% pot odds (calling 10k to win 15k = 40%)
            // or desperate short stack
            if (potOdds >= 0.40f) return true;
            if (bbStack < 8) return true; // Desperate
            return false;
        }

        // PLAYABLE: Only call if player is loose all-in and we have good odds
        if (handStrength == HandCategory.PLAYABLE) {
            if (playerIsAllInLoose && potOdds >= 0.45f) return true;
            if (bbStack < 5) return true; // Very desperate
            return false;
        }

        // WEAK: Never call all-in
        return false;
    }

    /**
     * Updates player all-in tracking to detect loose shovers.
     */
    private void trackPlayerAllIn(boolean wentAllIn) {
        playerAllInOpportunities++;
        if (wentAllIn) {
            playerAllInCount++;
        }
        // If player goes all-in more than 25% of opportunities, they're loose
        if (playerAllInOpportunities >= 10) {
            float allInFrequency = (float) playerAllInCount / playerAllInOpportunities;
            playerIsAllInLoose = allInFrequency > 0.25f;
        }
    }

    private float runMonteCarloSimulation(
            List<Card> holeCards,
            List<Card> communityCards) {
        int wins = 0;
        int ties = 0;
        int losses = 0;
        int simulationCount = CasinoConfig.POKER_MONTE_CARLO_SAMPLES;
        
        int[] cardIndices = new int[52];
        boolean[] excluded = new boolean[52];
        
        for (int i = 0; i < 52; i++) {
            cardIndices[i] = i;
            excluded[i] = holeCards.contains(allCardsArray[i]) || communityCards.contains(allCardsArray[i]);
        }
        
        for (int i = 0; i < simulationCount; i++) {
            int validCount = 0;
            for (int j = 0; j < 52; j++) {
                if (!excluded[j]) {
                    cardIndices[validCount++] = j;
                }
            }
            
for (int j = validCount - 1; j > 0; j--) {
                int swapIdx = random.nextInt(j + 1);
                int tmp = cardIndices[j];
                cardIndices[j] = cardIndices[swapIdx];
                cardIndices[j] = tmp;
            }
            
            Card[] boardCards = new Card[5];
            int boardStart = communityCards.size();
            for (int j = 0; j < boardStart; j++) {
                boardCards[j] = communityCards.get(j);
            }
            for (int j = boardStart; j < 5; j++) {
                boardCards[j] = allCardsArray[cardIndices[j - boardStart]];
            }
            
            int oppCard1Idx = cardIndices[5 - boardStart];
            int oppCard2Idx = cardIndices[6 - boardStart];
            
            PokerGame.PokerGameLogic.HandScore ourScore = evaluateHandFast(holeCards, boardCards);
            PokerGame.PokerGameLogic.HandScore oppScore = evaluateTwoCardsFast(
                allCardsArray[oppCard1Idx], allCardsArray[oppCard2Idx], boardCards);
            
            int cmp = ourScore.compareTo(oppScore);
            if (cmp > 0) wins++;
            else if (cmp == 0) ties++;
            else losses++;
            
            if (i == 49) {
                float currentEquity = (wins + ties * 0.5f) / (i + 1);
                if (currentEquity > 0.90f || currentEquity < 0.10f) {
                    return new MonteCarloResult(wins, ties, losses, i + 1).getTotalEquity();
                }
            }
        }
        
        return new MonteCarloResult(wins, ties, losses, simulationCount).getTotalEquity();
    }
    
    private MonteCarloResult runMonteCarloSimulationFull(
            List<Card> holeCards,
            List<Card> communityCards) {
        int wins = 0;
        int ties = 0;
        int losses = 0;
        int simulationCount = CasinoConfig.POKER_MONTE_CARLO_SAMPLES;
        
        int[] cardIndices = new int[52];
        boolean[] excluded = new boolean[52];
        
        for (int i = 0; i < 52; i++) {
            cardIndices[i] = i;
            excluded[i] = holeCards.contains(allCardsArray[i]) || communityCards.contains(allCardsArray[i]);
        }
        
        for (int i = 0; i < simulationCount; i++) {
            int validCount = 0;
            for (int j = 0; j < 52; j++) {
                if (!excluded[j]) {
                    cardIndices[validCount++] = j;
                }
            }
            
            for (int j = validCount - 1; j > 0; j--) {
                int swapIdx = random.nextInt(j + 1);
                int tmp = cardIndices[j];
                cardIndices[j] = cardIndices[swapIdx];
                cardIndices[swapIdx] = tmp;
            }
            
            Card[] boardCards = new Card[5];
            int boardStart = communityCards.size();
            for (int j = 0; j < boardStart; j++) {
                boardCards[j] = communityCards.get(j);
            }
            for (int j = boardStart; j < 5; j++) {
                boardCards[j] = allCardsArray[cardIndices[j - boardStart]];
            }
            
            int oppCard1Idx = cardIndices[5 - boardStart];
            int oppCard2Idx = cardIndices[6 - boardStart];
            
            PokerGame.PokerGameLogic.HandScore ourScore = evaluateHandFast(holeCards, boardCards);
            PokerGame.PokerGameLogic.HandScore oppScore = evaluateTwoCardsFast(
                allCardsArray[oppCard1Idx], allCardsArray[oppCard2Idx], boardCards);
            
            int cmp = ourScore.compareTo(oppScore);
            if (cmp > 0) wins++;
            else if (cmp == 0) ties++;
            else losses++;
            
            if (i == 49) {
                float currentEquity = (wins + ties * 0.5f) / (i + 1);
                if (currentEquity > 0.90f || currentEquity < 0.10f) {
                    return new MonteCarloResult(wins, ties, losses, i + 1);
                }
            }
        }
        
        return new MonteCarloResult(wins, ties, losses, simulationCount);
    }
    
    private PokerGame.PokerGameLogic.HandScore evaluateHandFast(List<Card> holeCards, Card[] board) {
        Card[] allCards = new Card[7];
        allCards[0] = holeCards.get(0);
        allCards[1] = holeCards.get(1);
        System.arraycopy(board, 0, allCards, 2, 5);
        return analyzeHandFast(allCards);
    }
    
    private PokerGame.PokerGameLogic.HandScore evaluateTwoCardsFast(Card c1, Card c2, Card[] board) {
        Card[] allCards = new Card[7];
        allCards[0] = c1;
        allCards[1] = c2;
        System.arraycopy(board, 0, allCards, 2, 5);
        return analyzeHandFast(allCards);
    }
    
    private PokerGame.PokerGameLogic.HandScore analyzeHandFast(Card[] sevenCards) {
        int[] suitCounts = new int[4];
        int[] rankCounts = new int[15];
        int[][] suitRanks = new int[4][7];
        int[] suitRankCounts = new int[4];
        
        for (int i = 0; i < 7; i++) {
            Card c = sevenCards[i];
            int suitIdx = c.suit().ordinal();
            int rankVal = c.rank().getValue(GameType.POKER);
            suitCounts[suitIdx]++;
            rankCounts[rankVal]++;
            suitRanks[suitIdx][suitRankCounts[suitIdx]] = rankVal;
            suitRankCounts[suitIdx]++;
        }
        
        int flushSuit = -1;
        for (int s = 0; s < 4; s++) {
            if (suitCounts[s] >= 5) {
                flushSuit = s;
                break;
            }
        }
        
        boolean[] rankPresent = new boolean[15];
        for (int i = 0; i < 7; i++) rankPresent[sevenCards[i].rank().getValue(GameType.POKER)] = true;
        rankPresent[1] = rankPresent[14];
        
        int straightHigh = -1;
        int seq = 0;
        for (int r = 14; r >= 1; r--) {
            if (rankPresent[r]) {
                seq++;
                if (seq >= 5) straightHigh = r + 4;
            } else {
                seq = 0;
            }
        }
        
        int fourRank = 0, threeRank = 0, pairRank = 0, secondPairRank = 0;
        boolean hasFour = false, hasThree = false, hasPair = false, hasSecondPair = false;
        
        for (int r = 14; r >= 2; r--) {
            int cnt = rankCounts[r];
            if (cnt == 4) { hasFour = true; fourRank = r; }
            else if (cnt == 3) {
                if (!hasThree) { hasThree = true; threeRank = r; }
                else if (!hasPair) { hasPair = true; pairRank = r; }
            }
            else if (cnt == 2) {
                if (!hasPair) { hasPair = true; pairRank = r; }
                else if (!hasSecondPair) { hasSecondPair = true; secondPairRank = r; }
            }
        }
        
        if (hasFour) {
            int kicker = 0;
            for (int i = 0; i < 7; i++) {
                if (sevenCards[i].rank().getValue(GameType.POKER) != fourRank && sevenCards[i].rank().getValue(GameType.POKER) > kicker) {
                    kicker = sevenCards[i].rank().getValue(GameType.POKER);
                }
            }
            return new PokerGame.PokerGameLogic.HandScore(PokerGame.PokerGameLogic.HandRank.FOUR_OF_A_KIND, 
                new ArrayList<>(List.of(fourRank, kicker)));
        }
        
        if (hasThree && hasPair) {
            return new PokerGame.PokerGameLogic.HandScore(PokerGame.PokerGameLogic.HandRank.FULL_HOUSE,
                new ArrayList<>(List.of(threeRank, pairRank)));
        }
        
        if (flushSuit >= 0) {
            int[] flushRankVals = suitRanks[flushSuit];
            int flushCount = suitRankCounts[flushSuit];
            for (int i = 0; i < flushCount; i++) {
                for (int j = i + 1; j < flushCount; j++) {
                    if (flushRankVals[j] > flushRankVals[i]) {
                        int tmp = flushRankVals[i];
                        flushRankVals[i] = flushRankVals[j];
                        flushRankVals[j] = tmp;
                    }
                }
            }
            
            boolean[] flushRankPresent = new boolean[15];
            for (int i = 0; i < flushCount; i++) flushRankPresent[flushRankVals[i]] = true;
            if (flushRankPresent[14]) flushRankPresent[1] = true;
            
            int flushSeq = 0;
            int flushStraightHigh = -1;
            for (int r = 14; r >= 1; r--) {
                if (flushRankPresent[r]) {
                    flushSeq++;
                    if (flushSeq >= 5) flushStraightHigh = r + 4;
                } else {
                    flushSeq = 0;
                }
            }
            
            if (flushStraightHigh >= 0) {
                return new PokerGame.PokerGameLogic.HandScore(PokerGame.PokerGameLogic.HandRank.STRAIGHT_FLUSH,
                    new ArrayList<>(List.of(flushStraightHigh)));
            }
            
            List<Integer> flushTie = new ArrayList<>();
            for (int i = 0; i < 5 && i < flushCount; i++) flushTie.add(flushRankVals[i]);
            return new PokerGame.PokerGameLogic.HandScore(PokerGame.PokerGameLogic.HandRank.FLUSH, flushTie);
        }
        
        if (straightHigh >= 0) {
            return new PokerGame.PokerGameLogic.HandScore(PokerGame.PokerGameLogic.HandRank.STRAIGHT,
                new ArrayList<>(List.of(straightHigh)));
        }
        
        if (hasThree) {
            List<Integer> tie = new ArrayList<>();
            tie.add(threeRank);
            int kickers = 0;
            for (int i = 0; i < 7 && kickers < 2; i++) {
                if (sevenCards[i].rank().getValue(GameType.POKER) != threeRank) {
                    tie.add(sevenCards[i].rank().getValue(GameType.POKER));
                    kickers++;
                }
            }
            return new PokerGame.PokerGameLogic.HandScore(PokerGame.PokerGameLogic.HandRank.THREE_OF_A_KIND, tie);
        }
        
        if (hasPair && hasSecondPair) {
            List<Integer> tie = new ArrayList<>();
            tie.add(pairRank);
            tie.add(secondPairRank);
            for (int i = 0; i < 7; i++) {
                if (sevenCards[i].rank().getValue(GameType.POKER) != pairRank && sevenCards[i].rank().getValue(GameType.POKER) != secondPairRank) {
                    tie.add(sevenCards[i].rank().getValue(GameType.POKER));
                    break;
                }
            }
            return new PokerGame.PokerGameLogic.HandScore(PokerGame.PokerGameLogic.HandRank.TWO_PAIR, tie);
        }
        
        if (hasPair) {
            List<Integer> tie = new ArrayList<>();
            tie.add(pairRank);
            int kickers = 0;
            for (int i = 0; i < 7 && kickers < 3; i++) {
                if (sevenCards[i].rank().getValue(GameType.POKER) != pairRank) {
                    tie.add(sevenCards[i].rank().getValue(GameType.POKER));
                    kickers++;
                }
            }
            return new PokerGame.PokerGameLogic.HandScore(PokerGame.PokerGameLogic.HandRank.PAIR, tie);
        }
        
        List<Integer> tie = new ArrayList<>();
        int[] sortedRanks = new int[7];
        for (int i = 0; i < 7; i++) sortedRanks[i] = sevenCards[i].rank().getValue(GameType.POKER);
        for (int i = 0; i < 7; i++) {
            for (int j = i + 1; j < 7; j++) {
                if (sortedRanks[j] > sortedRanks[i]) {
                    int tmp = sortedRanks[i];
                    sortedRanks[i] = sortedRanks[j];
                    sortedRanks[j] = tmp;
                }
            }
        }
        for (int i = 0; i < 5; i++) tie.add(sortedRanks[i]);
        return new PokerGame.PokerGameLogic.HandScore(PokerGame.PokerGameLogic.HandRank.HIGH_CARD, tie);
    }
    
    // EV Calculation methods
    private float calculateCallEV(float equity, int potSize, int betToCall) {
        // EV of calling: equity * (pot + betToCall) - (1 - equity) * betToCall
        float winAmount = potSize + betToCall;
        return equity * winAmount - (1 - equity) * betToCall;
    }
    
    private float calculateRaiseEV(float equity, int potSize, int currentBet, int raiseAmount, float foldProbability) {
        // EV of raising = (foldProbability * pot) + (1 - foldProbability) * [equity * finalPot - (1-equity) * totalInvestment]
        // totalInvestment = what we put in (call current bet + raise amount + already committed this round)
        // finalPot = pot + currentBet + raiseAmount (what we win when opponent calls and we win)
        // CRITICAL: Include aiCommittedThisRound to account for chips already invested in this betting round
        int totalInvestment = currentBet + raiseAmount + aiCommittedThisRound;
        int finalPot = potSize + currentBet + raiseAmount;
        float evWhenCalled = equity * finalPot - (1 - equity) * totalInvestment;
        return foldProbability * potSize + (1 - foldProbability) * evWhenCalled;
    }
    
    private float calculateBluffEV(float foldProbability, int potSize, int bluffAmount) {
        // EV of bluffing = (foldProbability * pot) - (1 - foldProbability) * bluffAmount
        return foldProbability * potSize - (1 - foldProbability) * bluffAmount;
    }
    
    private float estimateFoldProbability(String playerRange, int potSize, int betSize) {
        // Estimate how often player will fold based on their style and pot odds
        float baseFoldProb = switch (playerRange)
        {
            case "tight_range" -> 0.45f; // Tight players fold more
            case "wide_range" -> 0.20f; // Loose players fold less
            default -> 0.30f;
        };

        // Adjust based on player style

        // Adjust based on pot odds (bigger bet = more folds)
        float potOdds = (float) betSize / (potSize + betSize);
        if (potOdds > 0.5f) {
            baseFoldProb += 0.15f; // Large bet, more folds
        } else if (potOdds < 0.25f) {
            baseFoldProb -= 0.10f; // Small bet, fewer folds
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
        } else if (isFold) {
            totalFolds++;
        } else if (!isCheck) {
            // Must be a call
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
        
        // Update aggression meter based on player actions
        updateAggressionMeter(isRaise);
        
        // Classify player style using VPIP/PFR metrics (more accurate)
        if (handsPlayed > 3 && totalPlayerActions > 5) {
            float vpip = (float) vpipCount / handsPlayed;
            float pfr = (float) pfrCount / handsPlayed;
            float af = totalCalls > 0 ? (float) totalRaises / totalCalls : totalRaises;
            
            // Classification based on poker statistics
            if (vpip < 0.25f && pfr < 0.15f) {
                playerStyle = 1; // PASSIVE (tight)
            } else if (vpip > 0.40f && af > 1.5f) {
                playerStyle = 3; // AGGRESSIVE (loose-aggressive)
            } else {
                playerStyle = 2; // BALANCED
            }
        }
    }
    
    // Anti-gullibility: track when AI folds to player bet
    public void trackAIFoldedToPlayerBet() {
        playerBetsWithoutShowdown++;
        consecutiveBluffsCaught++;
        handsSinceLastShowdown++;

        // Enter suspicious mode if player is bluffing too often
        float bluffRate = (float) consecutiveBluffsCaught / Math.max(1, handsSinceLastShowdown);
        if (consecutiveBluffsCaught >= 2 || bluffRate > 0.40f) {
            isSuspicious = true;
            suspiciousModeHands = 2 + random.nextInt(2); // 2-3 hands of suspicion
        }
    }

    // Anti-gullibility: track when player shows down a hand
    public void trackPlayerShowdown(boolean playerWasBluffing) {
        handsSinceLastShowdown = 0;
        consecutiveBluffsCaught = 0;
        largeBetsWithoutShowdown = 0;

        if (playerWasBluffing) {
            timesBluffedByPlayer++;
            // After being bluffed, become more suspicious
            if (timesBluffedByPlayer >= 2) {
                isSuspicious = true;
                suspiciousModeHands = 3;
            }
        }
    }
    
    // Anti-gullibility: check if AI should be suspicious of player bluffs
    private boolean shouldBeSuspicious() {
        if (suspiciousModeHands > 0) {
            return true;
        }

        // Check if player has high bluff success rate
        if (playerBetsTotal > 5) {
            float bluffSuccessRate = (float) playerBetsWithoutShowdown / playerBetsTotal;
            if (bluffSuccessRate > 0.50f) {
                return true; // Player is folding too often, they might be bluffing
            }
        }

        // Check if player frequently makes large bets without showdown (bluff tell)
        if (largeBetsWithoutShowdown >= 2) {
            return true; // Player making oversized bets and not showing down = likely bluffing
        }

        // Check if player check-raises frequently (trap indicator)
        if (handsPlayed > 3 && playerCheckRaises > handsPlayed / 3) {
            return true; // Player check-raises too much, be careful
        }

        return false;
    }
    
    // Anti-gullibility: make stubborn call with weak hand
    private boolean shouldMakeStubbornCall(float equity, int betToCall, int potSize) {
        // Only make stubborn calls against very small bets with decent hands
        // Tightened from potSize/5 (20%) to potSize/10 (10%) and equity 0.25f to 0.45f
        // This prevents AI from calling too wide against small raises
        if (betToCall < potSize / 10 && equity > 0.45f) {
            return true;
        }
        
        // If suspicious, call wider but still require reasonable equity
        if (isSuspicious && equity > 0.35f) {
            return random.nextFloat() < 0.5f; // 50% chance to call when suspicious (reduced from 60%)
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

        aiCommittedThisRound = 0;
        
        narrative = new BettingNarrative();
    }

    /**
     * Resets the committed chips counter when advancing to a new betting round.
     * Called by PokerGame when the round advances (e.g., from FLOP to TURN).
     */
    public void resetCommittedChips() {
        aiCommittedThisRound = 0;
    }

    /**
     * Resets betting round tracking when advancing to a new street.
     * Called by PokerGame.advanceRound().
     */
    public void resetBettingRoundTracking(int potSize) {
        raisesThisRound = 0;
        aiHasRaisedThisRound = false;
        totalPotThisRound = potSize;
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

    /**
     * Determines if the AI should avoid raising to prevent a raise spiral.
     * Returns true if AI should only call or fold, not raise.
     */
    private boolean shouldAvoidRaiseSpiral(int stackSize, int potSize) {
        // Cap raises at 2 per betting round
        if (raisesThisRound >= 2) {
            return true;
        }

        // Check stack-to-pot ratio (SPR)
        float spr = (float) (stackSize + aiCommittedThisRound) / Math.max(1, potSize);
        if (spr < 3.0f && raisesThisRound == 1) {
            return true; // Don't escalate with shallow stacks
        }

        // If AI already raised this round, 70% chance to "cool off"
        if (aiHasRaisedThisRound && raisesThisRound == 1) {
            return random.nextFloat() < 0.70f;
        }

        // If pot is already large relative to starting stack, be cautious
        float potGrowth = (float) potSize / Math.max(1, totalPotThisRound);
        return potGrowth > 4.0f && raisesThisRound == 1;
    }

    /**
     * Checks if AI is pot-committed and should prefer calling over raising.
     */
    private boolean isPotCommitted(int stackSize) {
        if (stackSize <= 0) return true;
        float committedPercent = (float) aiCommittedThisRound / (aiCommittedThisRound + stackSize);
        return committedPercent > 0.30f;
    }

    /**
     * Estimates opponent's hand strength based on betting sequence and player style.
     * Uses Bayesian-style updating: strong actions suggest stronger hand ranges.
     */
    private OpponentHandEstimate estimateOpponentHand(String currentAction, int betAmount, int potSize) {
        OpponentHandEstimate estimate = new OpponentHandEstimate();

        // Start with base range from player style
        switch (PlayerStyle.values()[playerStyle]) {
            case PASSIVE:
                // Passive players: tighter range, more value-heavy
                estimate.premiumProbability = 0.08f;
                estimate.strongProbability = 0.20f;
                estimate.playableProbability = 0.27f;
                estimate.weakProbability = 0.45f;
                estimate.bluffProbability = 0.10f;
                estimate.valueProbability = 0.90f;
                break;
            case AGGRESSIVE:
                // Aggressive players: wider range, more bluffs
                estimate.premiumProbability = 0.03f;
                estimate.strongProbability = 0.12f;
                estimate.playableProbability = 0.30f;
                estimate.weakProbability = 0.55f;
                estimate.bluffProbability = 0.35f;
                estimate.valueProbability = 0.65f;
                break;
            case BALANCED:
            default:
                // Balanced: standard distribution
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
    private boolean shouldFoldBasedOnHandReading(OpponentHandEstimate estimate, float ourEquity,
                                                  int currentBetToCall, int potSize) {
        // If opponent is likely bluffing (>40% bluff probability), be more stubborn
        if (estimate.getBluffProbability() > 0.40f && ourEquity > 0.35f) {
            return false; // Don't fold, call down
        }

        // If opponent likely has value hand (>60%), be more willing to fold with marginal equity
        if (estimate.getValueProbability() > 0.60f && ourEquity < 0.50f) {
            return true; // Fold to strong range
        }

        // If opponent has high probability of premium hand, fold unless we have strong equity
        float strongAndPremiumProb = estimate.premiumProbability + estimate.strongProbability;
        if (strongAndPremiumProb > 0.40f && ourEquity < 0.55f) {
            return true;
        }

        // Default: use standard pot odds calculation
        float potOdds = (float) currentBetToCall / (potSize + currentBetToCall);
        return ourEquity < potOdds * 0.9f;
    }

    private void updateAggressionMeter(boolean isRaise) {
        // Track last 10 actions with exponential decay
        float recentAggression = 0;
        float weight = 1.0f;
        
        // Add the current action to the history
        float currentActionWeight = isRaise ? 1.0f : 0.3f; // Raises are more aggressive than calls
        aggressionHistory[historyIndex] = currentActionWeight;
        historyIndex = (historyIndex + 1) % aggressionHistory.length; // Move to next index
        
        // Calculate weighted average of recent actions
        for (float v : aggressionHistory)
        {
            recentAggression += weight * v;
            weight *= 0.8f; // Decay older actions
        }
        
        // Normalize by the sum of weights
        float totalWeight = 0;
        weight = 1.0f;
        for (int i = 0; i < aggressionHistory.length; i++) {
            totalWeight += weight;
            weight *= 0.8f;
        }
        
        if (totalWeight > 0) {
            recentAggression /= totalWeight;
        }
        
        // Update meter with smoothing
        aggressionMeter = 0.7f * aggressionMeter + 0.3f * recentAggression;
    }
}
