package data.scripts.casino;

import java.util.*;

import data.scripts.casino.cards.Card;
import data.scripts.casino.cards.Deck;
import data.scripts.casino.cards.GameType;
import data.scripts.casino.cards.Rank;
import data.scripts.casino.cards.Suit;


public class PokerGame {

    public enum Action { FOLD, CHECK, CALL, RAISE, ALL_IN }

    public enum Dealer { PLAYER, OPPONENT }

    public enum Round { PREFLOP, FLOP, TURN, RIVER, SHOWDOWN }

    public enum CurrentPlayer { PLAYER, OPPONENT }

    public static class PokerState {
        public List<Card> playerHand;
        public List<Card> opponentHand;
        public List<Card> communityCards;
        public int pot;
        public int playerStack;
        public int opponentStack;
        public int playerBet;
        public int opponentBet;
        public Dealer dealer;
        public Round round;
        public PokerGameLogic.HandRank playerHandRank;
        public PokerGameLogic.HandRank opponentHandRank;
        public CurrentPlayer currentPlayer;
        public int bigBlind;
        public CurrentPlayer folder = null;
        public int lastPotWon = 0;
        public boolean playerHasActed = false;
        public boolean opponentHasActed = false;
        public boolean playerDeclaredAllIn = false;
        public boolean opponentDeclaredAllIn = false;
        
        // Display bet values - persist across round transitions for UI display
        // These track what each player has bet in the current betting round
        // Updated when bets change, reset only when new betting action starts
        public int displayPlayerBet = 0;
        public int displayOpponentBet = 0;
    }

    private final PokerState state;
    private final SimplePokerAI ai;
    private Deck deck;
    private int bigBlindAmount;

    public PokerGame() {
        this(1000, 1000, 10, 20);
    }

    public PokerGame(int playerStack, int opponentStack, int unusedSmallBlind, int unusedBigBlind) {
        ai = new SimplePokerAI();
        state = new PokerState();

        // Note: unusedSmallBlind and unusedBigBlind are intentionally ignored.
        // The game calculates blinds proportionally to stack sizes for balanced gameplay.
        int avgStack = (playerStack + opponentStack) / 2;
        int calculatedBB = calculateBigBlind(avgStack);

        this.bigBlindAmount = calculatedBB;

        state.playerStack = playerStack;
        state.opponentStack = opponentStack;
        state.bigBlind = bigBlindAmount;

        startNewHand();
    }
    
    @SuppressWarnings("unused")
    private PokerGame(boolean forSuspend) {
        ai = new SimplePokerAI();
        state = new PokerState();
        deck = new Deck(GameType.POKER);
        deck.shuffle();
    }
    
    public static PokerGame createSuspendedGame(
            int playerStack, int opponentStack, int bigBlind,
            int pot, int playerBet, int opponentBet,
            Dealer dealer, Round round, CurrentPlayer currentPlayer,
            List<Card> playerHand,
            List<Card> opponentHand,
            List<Card> communityCards,
            boolean playerHasActed, boolean opponentHasActed,
            boolean playerDeclaredAllIn, boolean opponentDeclaredAllIn) {
        
        PokerGame game = new PokerGame(true);

        game.bigBlindAmount = bigBlind;

        game.state.playerStack = playerStack;
        game.state.opponentStack = opponentStack;
        game.state.bigBlind = bigBlind;
        game.state.pot = pot;
        game.state.playerBet = playerBet;
        game.state.opponentBet = opponentBet;
        game.state.dealer = dealer;
        game.state.round = round;
        game.state.currentPlayer = currentPlayer;
        game.state.playerHand = new ArrayList<>(playerHand);
        game.state.opponentHand = new ArrayList<>(opponentHand);
        game.state.communityCards = new ArrayList<>(communityCards);
        game.state.playerHasActed = playerHasActed;
        game.state.opponentHasActed = opponentHasActed;
        game.state.playerDeclaredAllIn = playerDeclaredAllIn;
        game.state.opponentDeclaredAllIn = opponentDeclaredAllIn;
        game.state.displayPlayerBet = playerBet;
        game.state.displayOpponentBet = opponentBet;
        
        game.evaluateHands();
        
        game.ai.newHandStarted(game.state.dealer == Dealer.OPPONENT);
        game.ai.resetBettingRoundTracking(pot);
        
        return game;
    }
    
    public static String cardToString(Card card) {
        if (card == null) return "";
        return card.value() + "-" + card.suit.name();
    }
    
    private static final Rank[] RANK_BY_VALUE = new Rank[15];
    static {
        for (Rank r : Rank.values()) {
            RANK_BY_VALUE[r.getValue(GameType.POKER)] = r;
        }
    }
    
    public static Card stringToCard(String str) {
        if (str == null || str.isEmpty()) return null;
        String[] parts = str.split("-");
        if (parts.length != 2) return null;
        int rankValue = Integer.parseInt(parts[0]);
        Suit suit = Suit.valueOf(parts[1]);
        if (rankValue >= 2 && rankValue <= 14) {
            return new Card(RANK_BY_VALUE[rankValue], suit, GameType.POKER);
        }
        return null;
    }

    /**
     * Calculate big blind amount for 100 BB stack depth.
     * BB = stack / 100, rounded to nearest 10, minimum 10.
     */
    private static int calculateBigBlind(int avgStack) {
        int calculatedBB = avgStack / 100;
        calculatedBB = Math.max(10, calculatedBB);
        return ((calculatedBB + 5) / 10) * 10;
    }

    public static class PokerGameLogic {
        public enum HandRank {
            HIGH_CARD(1), PAIR(2), TWO_PAIR(3), THREE_OF_A_KIND(4), STRAIGHT(5), 
            FLUSH(6), FULL_HOUSE(7), FOUR_OF_A_KIND(8), STRAIGHT_FLUSH(9);
            public final int value;
            HandRank(int v) { value = v; }
        }
    
        public static class HandScore implements Comparable<HandScore> {
            public HandRank rank;
            public List<Integer> tieBreakers; 
            
            public HandScore(HandRank r, List<Integer> tb) {
                this.rank = r;
                this.tieBreakers = tb;
            }
    
            @Override
            public int compareTo(HandScore o) {
                if (this.rank.value != o.rank.value) return Integer.compare(this.rank.value, o.rank.value);
                for (int i = 0; i < this.tieBreakers.size() && i < o.tieBreakers.size(); i++) {
                    int cmp = Integer.compare(this.tieBreakers.get(i), o.tieBreakers.get(i));
                    if (cmp != 0) return cmp;
                }
                return 0;
            }
        }
    
        public static HandScore evaluate(List<Card> holeCards, List<Card> communityCards) {
            List<Card> all = new ArrayList<>(holeCards);
            all.addAll(communityCards);
            if (all.size() < 7) return new HandScore(HandRank.HIGH_CARD, new ArrayList<>());
            all.sort((o1, o2) -> Integer.compare(o2.rank.getValue(GameType.POKER), o1.rank.getValue(GameType.POKER)));
            return analyzeHand(all);
        }
        
        private static HandScore analyzeHand(List<Card> sevenCards) {
            int[] suitCounts = new int[4];
            int[] rankCounts = new int[15];
            int[][] suitRanks = new int[4][7];
            int[] suitRankCounts = new int[4];
            int[] sortedRanks = new int[7];
            
            for (int i = 0; i < 7; i++) {
                Card c = sevenCards.get(i);
                int suitIdx = c.suit.ordinal();
                int rankVal = c.rank.getValue(GameType.POKER);
                suitCounts[suitIdx]++;
                rankCounts[rankVal]++;
                suitRanks[suitIdx][suitRankCounts[suitIdx]] = rankVal;
                suitRankCounts[suitIdx]++;
                sortedRanks[i] = rankVal;
            }
            
            for (int i = 0; i < 6; i++) {
                for (int j = i + 1; j < 7; j++) {
                    if (sortedRanks[j] > sortedRanks[i]) {
                        int tmp = sortedRanks[i];
                        sortedRanks[i] = sortedRanks[j];
                        sortedRanks[j] = tmp;
                    }
                }
            }
            
            int flushSuit = -1;
            for (int s = 0; s < 4; s++) {
                if (suitCounts[s] >= 5) {
                    flushSuit = s;
                    break;
                }
            }
            
            boolean[] rankPresent = new boolean[15];
            for (int r : sortedRanks) {
                if (!rankPresent[r]) rankPresent[r] = true;
            }
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
            
            List<Integer> tie = new ArrayList<>();
            
            if (hasFour) {
                tie.add(fourRank);
                for (int r : sortedRanks) {
                    if (r != fourRank) { tie.add(r); break; }
                }
                return new HandScore(HandRank.FOUR_OF_A_KIND, tie);
            }
            
            if (hasThree && hasPair) {
                tie.add(threeRank);
                tie.add(pairRank);
                return new HandScore(HandRank.FULL_HOUSE, tie);
            }
            
            if (flushSuit >= 0) {
                int[] flushRankVals = suitRanks[flushSuit];
                int flushCount = suitRankCounts[flushSuit];
                for (int i = 0; i < flushCount - 1; i++) {
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
                flushRankPresent[1] = flushRankPresent[14];
                
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
                    tie.add(flushStraightHigh);
                    return new HandScore(HandRank.STRAIGHT_FLUSH, tie);
                }
                
                for (int i = 0; i < 5 && i < flushCount; i++) tie.add(flushRankVals[i]);
                return new HandScore(HandRank.FLUSH, tie);
            }
            
            if (straightHigh >= 0) {
                tie.add(straightHigh);
                return new HandScore(HandRank.STRAIGHT, tie);
            }
            
            if (hasThree) {
                tie.add(threeRank);
                int kickers = 0;
                for (int r : sortedRanks) {
                    if (r != threeRank) {
                        tie.add(r);
                        kickers++;
                        if (kickers == 2) break;
                    }
                }
                return new HandScore(HandRank.THREE_OF_A_KIND, tie);
            }
            
            if (hasPair && hasSecondPair) {
                tie.add(pairRank);
                tie.add(secondPairRank);
                for (int r : sortedRanks) {
                    if (r != pairRank && r != secondPairRank) {
                        tie.add(r);
                        break;
                    }
                }
                return new HandScore(HandRank.TWO_PAIR, tie);
            }
            
            if (hasPair) {
                tie.add(pairRank);
                int kickers = 0;
                for (int r : sortedRanks) {
                    if (r != pairRank) {
                        tie.add(r);
                        kickers++;
                        if (kickers == 3) break;
                    }
                }
                return new HandScore(HandRank.PAIR, tie);
            }
            
            for (int i = 0; i < 5; i++) tie.add(sortedRanks[i]);
            return new HandScore(HandRank.HIGH_CARD, tie);
        }
    }

    public static class SimplePokerAI {
        private final Random random = new Random();
        public enum Action { FOLD, CALL, RAISE, CHECK }
        public enum Personality { TIGHT, AGGRESSIVE, CALCULATED }
        public enum PlayerStyle { UNKNOWN, PASSIVE, BALANCED, AGGRESSIVE }
        
        public static class AIResponse {
            public Action action;
            public int raiseAmount; 
            public AIResponse(Action a, int amt) { action=a; raiseAmount=amt; }
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
        
        private PlayerProfile profile = new PlayerProfile();

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
        private int[] recentActions = new int[RECENT_ACTION_SIZE];
        private int[] recentActionHand = new int[RECENT_ACTION_SIZE];
        private int recentActionIndex = 0;
        private int recentActionCount = 0;
        
        // Showdown history buffer
        private static final int SHOWDOWN_HISTORY_SIZE = 10;
        private ShowdownRecord[] showdownHistory = new ShowdownRecord[SHOWDOWN_HISTORY_SIZE];
        private int showdownHistoryIndex = 0;
        private int showdownHistoryCount = 0;
        
        // AI betting pattern for pretend layer (deception)
        private static final int AI_PATTERN_SIZE = 5;
        private int[] aiBettingPattern = new int[AI_PATTERN_SIZE]; // 0=check, 1=call, 2=small_raise, 3=large_raise
        private int aiPatternIndex = 0;
        private int aiPatternCount = 0;

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

        public SimplePokerAI() {
            Arrays.fill(aggressionHistory, 0.5f);
        }
        
        public String getProfileDebugInfo() {
            return String.format("AGG:%.2f PASS:%.2f BLUFF:%.2f TRAP:%.2f CONF:%.2f -> %s",
                profile.aggressionConfidence, profile.passivityConfidence,
                profile.bluffLikelihood, profile.trapLikelihood,
                profile.totalConfidence, personality.name());
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
            
            // TODO use or delete
            float weightedBluffs = 0, weightedTraps = 0, weightedWeakValue = 0;
            float totalWeight = 0;
            
            for (int i = 0; i < showdownHistoryCount; i++) {
                ShowdownRecord rec = showdownHistory[i];
                float decay = rec.getDecayFactor(handsPlayed);
                totalWeight += decay;
                
                if (rec.wasBluff) {
                    weightedBluffs += decay * 1.5f;
                } else if (rec.handRankValue >= 6 && rec.lastBetRatio < 0.3f) {
                    weightedTraps += decay * 1.2f;
                } else if (rec.handRankValue <= 2 && rec.lastBetRatio > 0.5f && rec.won) {
                    weightedWeakValue += decay;
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
            recentActionHand[recentActionIndex] = handsPlayed;
            recentActionIndex = (recentActionIndex + 1) % RECENT_ACTION_SIZE;
            if (recentActionCount < RECENT_ACTION_SIZE) recentActionCount++;
        }
        
        public void trackShowdownDetails(int handRankValue, float lastBetRatio, boolean won, boolean wasBluff) {
            showdownHistory[showdownHistoryIndex] = new ShowdownRecord(handRankValue, lastBetRatio, won, wasBluff, handsPlayed);
            showdownHistoryIndex = (showdownHistoryIndex + 1) % SHOWDOWN_HISTORY_SIZE;
            if (showdownHistoryCount < SHOWDOWN_HISTORY_SIZE) showdownHistoryCount++;
            
            updateProfile();
        }
        
        private void updateAIBettingPattern(int actionType, int betSize, int potSize) {
            int patternType = 0;
            if (actionType == 2) {
                float ratio = potSize > 0 ? (float) betSize / potSize : 0;
                if (ratio > 0.75f) patternType = 3;
                else if (ratio > 0.25f) patternType = 2;
                else patternType = 1;
            } else if (actionType == 1) {
                patternType = 1;
            } else {
                patternType = 0;
            }
            
            aiBettingPattern[aiPatternIndex] = patternType;
            aiPatternIndex = (aiPatternIndex + 1) % AI_PATTERN_SIZE;
            if (aiPatternCount < AI_PATTERN_SIZE) aiPatternCount++;
        }
        
        private float calculatePerceivedEquity(List<Card> communityCards, int potSize) {
            if (aiPatternCount < 2) return 0.5f;
            
            float basePerceived = 0.5f;
            int largeRaises = 0, smallRaises = 0, checks = 0, calls = 0;
            
            for (int i = 0; i < aiPatternCount; i++) {
                int p = aiBettingPattern[i];
                if (p == 3) largeRaises++;
                else if (p == 2) smallRaises++;
                else if (p == 0) checks++;
                else calls++;
            }
            
            if (largeRaises > 0) {
                basePerceived += 0.15f * largeRaises;
            }
            if (smallRaises > 0) {
                basePerceived += 0.08f * smallRaises;
            }
            if (checks > calls && aiPatternCount >= 3) {
                basePerceived -= 0.12f;
            }
            
            return Math.max(0.15f, Math.min(0.85f, basePerceived));
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

            // Calculate pre-flop equity
            float equity = calculatePreflopEquity(holeCards);

            // Calculate big blinds for stack size assessment
            int bigBlind = Math.max(1, potSize / 3); // Approximate BB from pot
            float bbStack = (float) stackSize / bigBlind;

            // Position adjustment: play more hands when in position (BB acts last preflop)
            float positionBonus = isInPosition ? 0.05f : -0.03f;
            equity += positionBonus;

            // Short stack push/fold strategy (< 15 BB)
            if (bbStack < 15 && currentBetToCall > 0) {
                return shortStackDecision(equity, currentBetToCall, potSize, stackSize, bbStack);
            }

            // If player didn't raise (checked or limped), always continue
            if (currentBetToCall == 0) {
                // Player checked or limped - play based on hand strength + position
                float openThreshold = isInPosition ? 0.32f : 0.45f; // Looser when in position
                float bluffChance = isInPosition ? 0.35f : 0.20f; // More bluff raises in position

                if (equity >= openThreshold || (equity >= 0.30f && random.nextFloat() < bluffChance)) {
                    int raiseAmount = Math.min(stackSize / 20, stackSize);
                    raiseAmount = Math.max(bigBlind * 3, raiseAmount);
                    // Larger raises in position, smaller OOP
                    if (isInPosition) {
                        raiseAmount = (int)(raiseAmount * 1.2f);
                    } else {
                        raiseAmount = (int)(raiseAmount * 0.85f);
                    }
                    raiseAmount = Math.max(CasinoConfig.POKER_AI_MIN_RAISE_VALUE, raiseAmount);
                    return new AIResponse(Action.RAISE, raiseAmount);
                } else {
                    return new AIResponse(Action.CALL, 0); // Call to see flop
                }
            } else {
                // Player bet/raised - use EV-based decision-making
                float potOdds = (float)currentBetToCall / (potSize + currentBetToCall);

                // SPECIAL RULE: Always call to BB (currentBetToCall == bigBlind) to not deny the game
                // This happens when player raises from SB (limps/raises to BB amount)
                if (currentBetToCall == bigBlind) {
                    // Always complete the BB to not deny the player the game
                    // But still consider raising with strong hands
                    if (equity > 0.60f && random.nextFloat() < 0.4f) {
                        // 3-bet with strong hands sometimes
                        int threeBetSize = bigBlind * 3;
                        threeBetSize = Math.min(threeBetSize, stackSize);
                        return new AIResponse(Action.RAISE, Math.max(threeBetSize, CasinoConfig.POKER_AI_MIN_RAISE_VALUE));
                    }
                    return new AIResponse(Action.CALL, 0);
                }

                // CRITICAL: Check for preflop all-in situation first
                // Use hand classification for tight calling ranges
                boolean isAllIn = currentBetToCall >= stackSize * 0.9f;
                if (isAllIn) {
                    HandCategory handStrength = classifyPreflopHand(holeCards);
                    // Track player all-in tendency
                    trackPlayerAllIn(true);
                    // Use tight ranges to call all-in
                    if (!shouldCallPreflopAllIn(handStrength, potOdds, bbStack)) {
                        return new AIResponse(Action.FOLD, 0);
                    }
                    // Only reach here with premium/strong hands - call the all-in
                    return new AIResponse(Action.CALL, 0);
                } else {
                    // Track that all-in was possible but player didn't use it
                    trackPlayerAllIn(false);
                }

                // Calculate EV of calling
                float evCall = calculateCallEV(equity, potSize, currentBetToCall);
                float evFold = -aiCommittedThisRound; // Lose committed chips when folding

                // Calculate 3-bet sizing and EV
                int threeBetSize = currentBetToCall * 3; // 3x the raise
                threeBetSize = Math.max(threeBetSize, bigBlind * 9); // At least 9x BB
                threeBetSize = Math.min(threeBetSize, stackSize - currentBetToCall);

                // Estimate fold probability based on player style
                String playerRange = estimatePlayerRange();
                float foldProb = estimateFoldProbability(playerRange, potSize + currentBetToCall, threeBetSize);
                float evRaise = calculateRaiseEV(equity, potSize, currentBetToCall, threeBetSize, foldProb);

                // For bluff 3-bets: if equity is marginal, treat as bluff
                if (equity < 0.55f) {
                    float bluffEV = calculateBluffEV(foldProb, potSize, threeBetSize);
                    evRaise = Math.max(evRaise, bluffEV);
                }

                // Apply frequency cap: only 3-bet conservatively when facing a raise
                // This prevents the re-raise spiral and all-in abuse
                boolean should3Bet = false;
                if (equity > 0.70f) {
                    // Premium hands: 3-bet for value 40% of the time (reduced from 60%)
                    should3Bet = random.nextFloat() < 0.40f;
                } else if (equity > 0.60f) {
                    // Strong hands: 3-bet 20% of the time (reduced from 30%)
                    should3Bet = random.nextFloat() < 0.20f;
                } else if (equity > 0.50f && foldProb > 0.50f) {
                    // Bluff 3-bet: only with excellent fold equity, 10% frequency (reduced from 20%)
                    should3Bet = random.nextFloat() < 0.10f;
                }

                // RAISE SPIRAL PREVENTION: Check if we should avoid raising
                if (shouldAvoidRaiseSpiral(stackSize, potSize)) {
                    should3Bet = false;
                }

                // If pot-committed, prefer calling over raising
                if (isPotCommitted(stackSize) && equity > 0.40f) {
                    should3Bet = false;
                }

                // Only 3-bet if EV favors it AND frequency cap allows
                if (should3Bet && evRaise > evCall && evRaise > evFold && evRaise > 0) {
                    threeBetSize = Math.max(threeBetSize, CasinoConfig.POKER_AI_MIN_RAISE_VALUE);
                    if (CasinoConfig.POKER_AI_MAX_RAISE_RANDOM_ADDITION > 0) {
                        threeBetSize += random.nextInt(CasinoConfig.POKER_AI_MAX_RAISE_RANDOM_ADDITION);
                    }
                    return new AIResponse(Action.RAISE, threeBetSize);
                }

                // Decide between call and fold based on EV comparison
                // evFold now accounts for committed chips, making the comparison accurate
                if (evCall > evFold) {
                    return new AIResponse(Action.CALL, 0);
                } else {
                    return new AIResponse(Action.FOLD, 0);
                }
            }
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

            MonteCarloResult mcResult = runMonteCarloSimulationFull(holeCards, communityCards, playerRange);
            float trueEquity = mcResult.getTotalEquity();

            float impliedOddsBonus = calculateImpliedOddsBonus(holeCards, communityCards, trueEquity);
            float adjustedEquity = trueEquity + impliedOddsBonus;

            boolean wetBoard = isWetBoard(communityCards);

            float perceivedEquity = calculatePerceivedEquity(communityCards, potSize);
            float equityGap = Math.abs(trueEquity - perceivedEquity);
            float deceptionEquity = adjustedEquity;
            
            if (equityGap > 0.20f) {
                if (trueEquity > 0.70f && perceivedEquity < 0.50f) {
                    deceptionEquity = perceivedEquity + 0.15f;
                } else if (trueEquity < 0.40f && perceivedEquity > 0.55f) {
                    deceptionEquity = Math.max(adjustedEquity, perceivedEquity - 0.10f);
                }
            }

            return postFlopEVDecision(currentBetToCall, potSize, stackSize, playerRange, deceptionEquity, wetBoard, trueEquity, perceivedEquity);
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
            for (Card c : holeCards) suitCounts[c.suit.ordinal()]++;
            for (Card c : communityCards) suitCounts[c.suit.ordinal()]++;
            
            for (int count : suitCounts) {
                if (count == 4) outs += 9;
            }
            
            boolean[] rankPresent = new boolean[15];
            for (Card c : holeCards) rankPresent[c.rank.getValue(GameType.POKER)] = true;
            for (Card c : communityCards) rankPresent[c.rank.getValue(GameType.POKER)] = true;
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
            for (Card c : communityCards) suitCounts[c.suit.ordinal()]++;
            
            for (int count : suitCounts) {
                if (count >= 3) return true;
            }
            
            boolean[] rankPresent = new boolean[15];
            for (Card c : communityCards) rankPresent[c.rank.getValue(GameType.POKER)] = true;
            
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

        private AIResponse postFlopEVDecision(int currentBetToCall, int potSize, int stackSize, String playerRange, float equity, boolean isWetBoard, float trueEquity, float perceivedEquity) {
            float evFold = -aiCommittedThisRound;
            float evCall = calculateCallEV(equity, potSize, currentBetToCall);
            
            // Position-based adjustments: in position = more aggressive, OOP = more cautious
            float positionFoldPenalty = isInPosition ? 0.0f : 0.08f * potSize;
            float positionCallBonus = isInPosition ? 0.05f * potSize : 0.0f;
            float positionRaiseBonus = isInPosition ? 0.04f * potSize : -0.02f * potSize;
            
            evFold += positionFoldPenalty;
            evCall += positionCallBonus;
            
            // Calculate raise sizes to consider - adjust for position
            float raiseMultiplier = isInPosition ? 1.1f : 0.9f;
            int[] raiseSizes = {
                (int)(potSize * 0.5f * raiseMultiplier),
                (int)(potSize * 1.0f * raiseMultiplier),
                (int)(potSize * 2.0f * raiseMultiplier)
            };
            
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
            evFold = adjustEVForDeception(evFold, Action.FOLD, trueEquity, perceivedEquity, potSize);
            evCall = adjustEVForDeception(evCall, Action.CALL, trueEquity, perceivedEquity, potSize);
            bestRaiseEV = adjustEVForDeception(bestRaiseEV, Action.RAISE, trueEquity, perceivedEquity, potSize);

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
                    int actionType = deviation.action == Action.RAISE ? 2 : (deviation.action == Action.CALL ? 1 : 0);
                    updateAIBettingPattern(actionType, deviation.raiseAmount, potSize);
                    return deviation;
                }
            }

            int actionType = finalDecision.action == Action.RAISE ? 2 : (finalDecision.action == Action.CALL ? 1 : 3);
            updateAIBettingPattern(actionType, finalDecision.raiseAmount, potSize);

            return finalDecision;
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
        
        // Pre-computed hand category caches for generateRandomOpponentHand (initialized at class load)
        private static final Card[] allCardsArray = new Card[52];
        private static final List<int[]> premiumHandIndices = new ArrayList<>();
        private static final List<int[]> strongHandIndices = new ArrayList<>();
        private static final List<int[]> playableHandIndices = new ArrayList<>();
        private static final List<int[]> weakHandIndices = new ArrayList<>();
        private static final int[][] handCategoryByCardIndices = new int[52][52]; // 0=weak, 1=playable, 2=strong, 3=premium
        
        static {
            Deck deck = new Deck(GameType.POKER);
            for (int i = 0; i < 52; i++) {
                allCardsArray[i] = deck.cards.get(i);
            }
            
            for (int i = 0; i < 52; i++) {
                for (int j = i + 1; j < 52; j++) {
                    Card c1 = allCardsArray[i];
                    Card c2 = allCardsArray[j];
                    
                    float equity = calculatePreflopEquitySimpleStatic(
                        Math.max(c1.rank.getValue(GameType.POKER), c2.rank.getValue(GameType.POKER)),
                        Math.min(c1.rank.getValue(GameType.POKER), c2.rank.getValue(GameType.POKER)),
                        c1.suit == c2.suit);
                    
                    int[] indices = new int[]{i, j};
                    int category;
                    if (equity >= 0.70f) {
                        premiumHandIndices.add(indices);
                        category = 3;
                    } else if (equity >= 0.55f) {
                        strongHandIndices.add(indices);
                        category = 2;
                    } else if (equity >= 0.40f) {
                        playableHandIndices.add(indices);
                        category = 1;
                    } else {
                        weakHandIndices.add(indices);
                        category = 0;
                    }
                    handCategoryByCardIndices[i][j] = category;
                }
            }
        }
        
        private static float calculatePreflopEquitySimpleStatic(int highRank, int lowRank, boolean suited) {
            int gap = highRank - lowRank;
            
            if (highRank == lowRank) {
                if (highRank >= 13) return 0.85f;
                if (highRank >= 11) return 0.78f;
                if (highRank >= 9) return 0.70f;
                if (highRank >= 7) return 0.62f;
                if (highRank >= 5) return 0.55f;
                return 0.48f;
            }
            
            if (highRank == 14 && lowRank == 13) return suited ? 0.68f : 0.65f;
            if (highRank == 14 && lowRank == 12) return suited ? 0.63f : 0.60f;
            
            if (suited) {
                if (highRank == 14 && lowRank >= 10) return 0.58f;
                if (highRank == 13 && lowRank == 12) return 0.58f;
                if (highRank == 13 && lowRank >= 10) return 0.50f;
                if (highRank == 12 && lowRank >= 10) return 0.50f;
                if (gap == 1 && highRank >= 5) return 0.52f;
                if (gap == 2 && highRank >= 6) return 0.48f;
                if (gap <= 3 && highRank >= 9) return 0.45f;
                if (highRank >= 10) return 0.45f;
                return 0.40f;
            }
            
            if (highRank == 14 && lowRank >= 10) return 0.55f;
            if (highRank == 13 && lowRank >= 11) return 0.52f;
            if (gap == 1 && highRank >= 12) return 0.48f;
            if (highRank >= 11 && lowRank >= 10) return 0.45f;
            if (gap <= 2 && highRank >= 8) return 0.38f;
            if (highRank >= 10) return 0.35f;
            
            return 0.30f;
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
            int r1 = c1.rank.getValue(GameType.POKER);
            int r2 = c2.rank.getValue(GameType.POKER);
            int v1 = r1 >= r2 ? r1 : r2;
            int v2 = r1 < r2 ? r1 : r2;
            boolean suited = (c1.suit == c2.suit);
            
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
                
                int drawIndex = 0;
                Card[] boardCards = new Card[5];
                for (int j = 0; j < 5; j++) boardCards[j] = allCardsArray[cardIndices[drawIndex++]];
                
                Card opp1 = allCardsArray[cardIndices[drawIndex++]];
                Card opp2 = allCardsArray[cardIndices[drawIndex++]];
                
                PokerGameLogic.HandScore ourScore = evaluateTwoCardsFast(c1, c2, boardCards);
                PokerGameLogic.HandScore oppScore = evaluateTwoCardsFast(opp1, opp2, boardCards);
                
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
            String playerRange = estimatePlayerRange();
            return runMonteCarloSimulation(holeCards, communityCards, playerRange);
        }
        
        private float calculatePreflopEquitySimple(List<Card> holeCards) {
            Card c1 = holeCards.get(0);
            Card c2 = holeCards.get(1);
            int v1 = Math.max(c1.rank.getValue(GameType.POKER), c2.rank.getValue(GameType.POKER));
            int v2 = Math.min(c1.rank.getValue(GameType.POKER), c2.rank.getValue(GameType.POKER));
            boolean suited = (c1.suit == c2.suit);
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

        /**
         * Classifies a preflop hand into categories for decision making.
         * This is more accurate than raw equity for all-in decisions.
         */
        private HandCategory classifyPreflopHand(List<Card> holeCards) {
            Card c1 = holeCards.get(0);
            Card c2 = holeCards.get(1);
            int v1 = Math.max(c1.rank.getValue(GameType.POKER), c2.rank.getValue(GameType.POKER));
            int v2 = Math.min(c1.rank.getValue(GameType.POKER), c2.rank.getValue(GameType.POKER));
            boolean suited = (c1.suit == c2.suit);

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
                List<Card> communityCards,
                String playerRange) {
            int wins = 0;
            int ties = 0;
            int losses = 0;
            int simulationCount = CasinoConfig.POKER_MONTE_CARLO_SAMPLES;
            
            Card[] shuffledCards = new Card[52];
            int[] cardIndices = new int[52];
            boolean[] excluded = new boolean[52];
            
            for (int i = 0; i < 52; i++) {
                shuffledCards[i] = allCardsArray[i];
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
                
                int drawIndex = 0;
                Card[] boardCards = new Card[5];
                int boardStart = communityCards.size();
                for (int j = 0; j < boardStart; j++) {
                    boardCards[j] = communityCards.get(j);
                }
                for (int j = boardStart; j < 5; j++) {
                    boardCards[j] = allCardsArray[cardIndices[drawIndex++]];
                }
                
                int oppCard1Idx = cardIndices[drawIndex++];
                int oppCard2Idx = cardIndices[drawIndex++];
                
                PokerGameLogic.HandScore ourScore = evaluateHandFast(holeCards, boardCards);
                PokerGameLogic.HandScore oppScore = evaluateTwoCardsFast(
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
                List<Card> communityCards,
                String playerRange) {
            int wins = 0;
            int ties = 0;
            int losses = 0;
            int simulationCount = CasinoConfig.POKER_MONTE_CARLO_SAMPLES;
            
            Card[] shuffledCards = new Card[52];
            int[] cardIndices = new int[52];
            boolean[] excluded = new boolean[52];
            
            for (int i = 0; i < 52; i++) {
                shuffledCards[i] = allCardsArray[i];
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
                
                int drawIndex = 0;
                Card[] boardCards = new Card[5];
                int boardStart = communityCards.size();
                for (int j = 0; j < boardStart; j++) {
                    boardCards[j] = communityCards.get(j);
                }
                for (int j = boardStart; j < 5; j++) {
                    boardCards[j] = allCardsArray[cardIndices[drawIndex++]];
                }
                
                int oppCard1Idx = cardIndices[drawIndex++];
                int oppCard2Idx = cardIndices[drawIndex++];
                
                PokerGameLogic.HandScore ourScore = evaluateHandFast(holeCards, boardCards);
                PokerGameLogic.HandScore oppScore = evaluateTwoCardsFast(
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
        
        private PokerGameLogic.HandScore evaluateHandFast(List<Card> holeCards, Card[] board) {
            Card[] allCards = new Card[7];
            allCards[0] = holeCards.get(0);
            allCards[1] = holeCards.get(1);
            for (int i = 0; i < 5; i++) allCards[i + 2] = board[i];
            return analyzeHandFast(allCards);
        }
        
        private PokerGameLogic.HandScore evaluateTwoCardsFast(Card c1, Card c2, Card[] board) {
            Card[] allCards = new Card[7];
            allCards[0] = c1;
            allCards[1] = c2;
            for (int i = 0; i < 5; i++) allCards[i + 2] = board[i];
            return analyzeHandFast(allCards);
        }
        
        private PokerGameLogic.HandScore analyzeHandFast(Card[] sevenCards) {
            int[] suitCounts = new int[4];
            int[] rankCounts = new int[15];
            int[][] suitRanks = new int[4][7];
            int[] suitRankCounts = new int[4];
            
            for (int i = 0; i < 7; i++) {
                Card c = sevenCards[i];
                int suitIdx = c.suit.ordinal();
                int rankVal = c.rank.getValue(GameType.POKER);
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
            for (int i = 0; i < 7; i++) rankPresent[sevenCards[i].rank.getValue(GameType.POKER)] = true;
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
                    if (sevenCards[i].rank.getValue(GameType.POKER) != fourRank && sevenCards[i].rank.getValue(GameType.POKER) > kicker) {
                        kicker = sevenCards[i].rank.getValue(GameType.POKER);
                    }
                }
                return new PokerGameLogic.HandScore(PokerGameLogic.HandRank.FOUR_OF_A_KIND, 
                    new ArrayList<>(List.of(fourRank, kicker)));
            }
            
            if (hasThree && hasPair) {
                return new PokerGameLogic.HandScore(PokerGameLogic.HandRank.FULL_HOUSE,
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
                    return new PokerGameLogic.HandScore(PokerGameLogic.HandRank.STRAIGHT_FLUSH,
                        new ArrayList<>(List.of(flushStraightHigh)));
                }
                
                List<Integer> flushTie = new ArrayList<>();
                for (int i = 0; i < 5 && i < flushCount; i++) flushTie.add(flushRankVals[i]);
                return new PokerGameLogic.HandScore(PokerGameLogic.HandRank.FLUSH, flushTie);
            }
            
            if (straightHigh >= 0) {
                return new PokerGameLogic.HandScore(PokerGameLogic.HandRank.STRAIGHT,
                    new ArrayList<>(List.of(straightHigh)));
            }
            
            if (hasThree) {
                List<Integer> tie = new ArrayList<>();
                tie.add(threeRank);
                int kickers = 0;
                for (int i = 0; i < 7 && kickers < 2; i++) {
                    if (sevenCards[i].rank.getValue(GameType.POKER) != threeRank) {
                        tie.add(sevenCards[i].rank.getValue(GameType.POKER));
                        kickers++;
                    }
                }
                return new PokerGameLogic.HandScore(PokerGameLogic.HandRank.THREE_OF_A_KIND, tie);
            }
            
            if (hasPair && hasSecondPair) {
                List<Integer> tie = new ArrayList<>();
                tie.add(pairRank);
                tie.add(secondPairRank);
                for (int i = 0; i < 7; i++) {
                    if (sevenCards[i].rank.getValue(GameType.POKER) != pairRank && sevenCards[i].rank.getValue(GameType.POKER) != secondPairRank) {
                        tie.add(sevenCards[i].rank.getValue(GameType.POKER));
                        break;
                    }
                }
                return new PokerGameLogic.HandScore(PokerGameLogic.HandRank.TWO_PAIR, tie);
            }
            
            if (hasPair) {
                List<Integer> tie = new ArrayList<>();
                tie.add(pairRank);
                int kickers = 0;
                for (int i = 0; i < 7 && kickers < 3; i++) {
                    if (sevenCards[i].rank.getValue(GameType.POKER) != pairRank) {
                        tie.add(sevenCards[i].rank.getValue(GameType.POKER));
                        kickers++;
                    }
                }
                return new PokerGameLogic.HandScore(PokerGameLogic.HandRank.PAIR, tie);
            }
            
            List<Integer> tie = new ArrayList<>();
            int[] sortedRanks = new int[7];
            for (int i = 0; i < 7; i++) sortedRanks[i] = sevenCards[i].rank.getValue(GameType.POKER);
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
            return new PokerGameLogic.HandScore(PokerGameLogic.HandRank.HIGH_CARD, tie);
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
            // In heads-up poker:
            // - Pre-flop: Dealer (SB) acts FIRST, BB acts last (in position)
            // - Post-flop: Dealer (SB) acts FIRST (out of position), BB acts last (in position)
            // So AI is "in position" (acts last) when AI is NOT the dealer (i.e., AI is BB)
            isInPosition = !aiIsDealer;

            // Decrement suspicious mode counter
            if (suspiciousModeHands > 0) {
                suspiciousModeHands--;
                if (suspiciousModeHands == 0) {
                    isSuspicious = false;
                }
            }

            handsSinceLastShowdown++;

            // Reset committed chips for new hand
            aiCommittedThisRound = 0;
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

    public PokerState getState() {
        return state;
    }

    public SimplePokerAI getAI() {
        return ai;
    }

    public void startNewHand() {
        deck = new Deck(GameType.POKER);
        deck.shuffle();

        state.playerHand = new ArrayList<>();
        state.opponentHand = new ArrayList<>();
        state.communityCards = new ArrayList<>();

        state.playerHand.add(deck.draw());
        state.opponentHand.add(deck.draw());
        state.playerHand.add(deck.draw());
        state.opponentHand.add(deck.draw());



        state.playerBet = 0;
        state.opponentBet = 0;
        state.displayPlayerBet = 0;
        state.displayOpponentBet = 0;
        state.pot = 0;
        state.folder = null;
        state.playerHasActed = false;
        state.opponentHasActed = false;
        state.playerDeclaredAllIn = false;
        state.opponentDeclaredAllIn = false;

        state.dealer = state.dealer == Dealer.PLAYER ? Dealer.OPPONENT : Dealer.PLAYER;

        postBlinds();

        state.round = Round.PREFLOP;
        // Heads-up poker: Small Blind (dealer) acts FIRST pre-flop
        // If player is dealer (SB), player acts first. If opponent is dealer (SB), opponent acts first.
        state.currentPlayer = getBigBlind() == Dealer.PLAYER ? CurrentPlayer.OPPONENT : CurrentPlayer.PLAYER;

        // Notify AI of new hand and position
        // AI is in position (acts last) when AI is the dealer
        ai.newHandStarted(state.dealer == Dealer.OPPONENT);

        // Reset betting round tracking for new hand
        ai.resetBettingRoundTracking(state.pot);

        evaluateHands();
    }

    /**
     * Returns who has the Big Blind position for the current hand
     */
    public Dealer getBigBlind() {
        // Big Blind is the player who is NOT the dealer
        return state.dealer == Dealer.PLAYER ? Dealer.OPPONENT : Dealer.PLAYER;
    }

    /**
     * Returns who has the Small Blind position for the current hand
     */
    public Dealer getSmallBlind() {
        // Small Blind is the dealer
        return state.dealer;
    }

    /**
     * Returns true if it's post-flop (flop, turn, or river)
     */
    public boolean isPostFlop() {
        return state.round == Round.FLOP || state.round == Round.TURN || state.round == Round.RIVER;
    }

    private void postBlinds() {
        if (state.dealer == Dealer.PLAYER) {
            // Small blind (player) - ensure doesn't go negative
            int playerSmallBlind = Math.min(bigBlindAmount / 2, state.playerStack);
            state.playerBet = playerSmallBlind;
            state.displayPlayerBet = playerSmallBlind;
            state.playerStack -= playerSmallBlind;
            
            // Big blind (opponent) - ensure doesn't go negative
            int opponentBigBlind = Math.min(bigBlindAmount, state.opponentStack);
            state.opponentBet = opponentBigBlind;
            state.displayOpponentBet = opponentBigBlind;
            state.opponentStack -= opponentBigBlind;
        } else {
            // Small blind (opponent) - ensure doesn't go negative
            int opponentSmallBlind = Math.min(bigBlindAmount / 2, state.opponentStack);
            state.opponentBet = opponentSmallBlind;
            state.displayOpponentBet = opponentSmallBlind;
            state.opponentStack -= opponentSmallBlind;
            
            // Big blind (player) - ensure doesn't go negative
            int playerBigBlind = Math.min(bigBlindAmount, state.playerStack);
            state.playerBet = playerBigBlind;
            state.displayPlayerBet = playerBigBlind;
            state.playerStack -= playerBigBlind;
        }
        state.pot = state.playerBet + state.opponentBet;
    }

    public void processPlayerAction(Action action, int raiseAmount) {
        switch (action) {
            case RAISE:
                ai.recordPlayerAction("RAISE");
                break;
            case ALL_IN:
                ai.recordPlayerAction("ALL_IN");
                break;
            case CALL:
                ai.recordPlayerAction("CALL");
                break;
            case CHECK:
                ai.recordPlayerAction("CHECK");
                break;
            case FOLD:
                ai.recordPlayerAction("FOLD");
                break;
        }

        switch (action) {
            case FOLD:
                // Pot award is handled by PokerHandler.determineWinner() to avoid double-awarding
                state.round = Round.SHOWDOWN;
                state.folder = CurrentPlayer.PLAYER; // Track that player folded
                break;
            case CHECK:
                break;
            case CALL:
                int callAmount = state.opponentBet - state.playerBet;
                state.playerStack -= callAmount;
                state.playerBet = state.opponentBet;
                state.displayPlayerBet = state.opponentBet;
                state.pot += callAmount;
                break;
case RAISE:
                // Defensive check: if opponent declared all-in, treat as CALL
                if (state.opponentDeclaredAllIn) {
                    int callAmt = state.opponentBet - state.playerBet;
                    state.playerStack -= callAmt;
                    state.playerBet = state.opponentBet;
                    state.displayPlayerBet = state.opponentBet;
                    state.pot += callAmt;
                    break;
                }
                state.opponentHasActed = false; // Opponent must respond to raise
                // raiseAmount is the desired total bet size (e.g., 3x opponent's raise)
                // NOT an additional amount on top of opponent's bet
                int totalBet = raiseAmount;
                int raiseAmountActual = totalBet - state.playerBet;
                // Protect against betting more than available stack
                if (raiseAmountActual > state.playerStack) {
                    raiseAmountActual = state.playerStack;
                    totalBet = state.playerBet + raiseAmountActual;
                }
                
                // Check if this is an all-in (player is betting their entire stack)
                if (raiseAmountActual >= state.playerStack) {
                    state.playerDeclaredAllIn = true;
                }
                
                // Check if player is raising more than opponent can possibly match (side pot)
                int playerContributionAfterRaise = totalBet;
                int currentOpponentContribution = state.opponentBet;
                if (playerContributionAfterRaise > currentOpponentContribution + state.opponentStack) {
                    int maxOpponentCanMatch = currentOpponentContribution + state.opponentStack;
                    int actualRaiseAmount = maxOpponentCanMatch - state.playerBet;
                    int excessToReturn = raiseAmountActual - actualRaiseAmount;
                    
                    // Player only risks what opponent can match
                    state.playerStack -= actualRaiseAmount;
                    // Only return excess if player hasn't declared all-in (all-in means all chips committed)
                    if (!state.playerDeclaredAllIn) {
                        state.playerStack += excessToReturn;
                    }
                    state.playerBet = maxOpponentCanMatch;
                    state.displayPlayerBet = maxOpponentCanMatch;
                    state.pot += actualRaiseAmount;
                } else {
                    state.playerStack -= raiseAmountActual;
                    state.playerBet = totalBet;
                    state.displayPlayerBet = totalBet;
                    state.pot += raiseAmountActual;
                }
                break;
case ALL_IN:
                state.playerDeclaredAllIn = true; // Flag that player has committed to all-in
                state.opponentHasActed = false; // Opponent must respond to all-in
                int allInAmount = state.playerStack;
                int opponentContributionAllIn = state.opponentBet;
                int playerContributionAfterAllIn = state.playerBet + allInAmount;

                // If player is putting in more than opponent can possibly match,
                // return the excess to player immediately (side pot logic)
                if (playerContributionAfterAllIn > opponentContributionAllIn + state.opponentStack) {
                    int maxOpponentCanMatch = opponentContributionAllIn + state.opponentStack;
                    int actualAllInAmount = maxOpponentCanMatch - state.playerBet;
                    int excessToReturn = allInAmount - actualAllInAmount;
                    
                    // Player only risks what opponent can match
                    state.pot += actualAllInAmount;
                    state.playerBet += actualAllInAmount;
                    state.displayPlayerBet = state.playerBet;
                    state.playerStack = excessToReturn; // Return excess chips
                } else {
                    // Normal all-in where opponent can match or has already matched
                    state.pot += allInAmount;
                    state.playerBet += allInAmount;
                    state.displayPlayerBet = state.playerBet;
                    state.playerStack = 0;
                }
                break;
        }

boolean isRaise = action == Action.RAISE || action == Action.ALL_IN;
        boolean isFold = action == Action.FOLD;
        boolean isCheck = action == Action.CHECK;
        boolean isPreFlop = state.round == Round.PREFLOP;
        boolean putMoneyInPot = action == Action.CALL || action == Action.RAISE || action == Action.ALL_IN;
        ai.trackPlayerAction(isRaise, isFold, isCheck, isPreFlop, putMoneyInPot);

        state.playerHasActed = true;

        if (state.round != Round.SHOWDOWN) {
            state.currentPlayer = CurrentPlayer.OPPONENT;
            checkRoundProgression();
        }
    }

    public SimplePokerAI.AIResponse getOpponentAction() {
        int currentBetToCall = state.playerBet - state.opponentBet;
        // If player is all-in (declared all-in OR no chips left), AI can only call or fold, not raise
        if (state.playerDeclaredAllIn || state.playerStack <= 0) {
            return ai.decideAllInResponse(state.opponentHand, state.communityCards, currentBetToCall, state.pot);
        }
        return ai.decide(state.opponentHand, state.communityCards, currentBetToCall, state.pot, state.opponentStack);
    }

    public void processOpponentAction(SimplePokerAI.AIResponse response) {
        switch (response.action) {
            case RAISE:
                ai.recordAIAction("RAISE");
                break;
            case CALL:
                ai.recordAIAction("CALL");
                break;
            case CHECK:
                ai.recordAIAction("CHECK");
                break;
            case FOLD:
                ai.recordAIAction("FOLD");
                break;
        }

        switch (response.action) {
            case FOLD:
                // Pot award is handled by PokerHandler.determineWinner() to avoid double-awarding
                state.round = Round.SHOWDOWN;
                state.folder = CurrentPlayer.OPPONENT; // Track that opponent folded
                break;
            case CHECK:
                break;
            case CALL:
                int callAmount = state.playerBet - state.opponentBet;
                state.opponentStack -= callAmount;
                state.opponentBet = state.playerBet;
                state.displayOpponentBet = state.playerBet;
                state.pot += callAmount;
                break;
case RAISE:
                int totalBet = state.opponentBet + response.raiseAmount;
                int raiseAmountActual = totalBet - state.opponentBet;
                // Protect against betting more than available stack
                if (raiseAmountActual > state.opponentStack) {
                    raiseAmountActual = state.opponentStack;
                    totalBet = state.opponentBet + raiseAmountActual;
                }
                
                // Check if opponent is going all-in (betting their entire stack)
                if (raiseAmountActual >= state.opponentStack) {
                    state.opponentDeclaredAllIn = true;
                }

                // Check if opponent is raising more than player can possibly match (side pot)
                int opponentContributionAfterRaise = totalBet;
                int currentPlayerContribution = state.playerBet;
                if (opponentContributionAfterRaise > currentPlayerContribution + state.playerStack) {
                    int maxPlayerCanMatch = currentPlayerContribution + state.playerStack;
                    int actualRaiseAmount = maxPlayerCanMatch - state.opponentBet;
                    int excessToReturn = raiseAmountActual - actualRaiseAmount;

                    // Opponent only risks what player can match
                    state.opponentStack -= actualRaiseAmount;
                    // Only return excess if opponent hasn't declared all-in (all-in means all chips committed)
                    if (!state.opponentDeclaredAllIn) {
                        state.opponentStack += excessToReturn;
                    }
                    state.opponentBet = maxPlayerCanMatch;
                    state.displayOpponentBet = maxPlayerCanMatch;
                    state.pot += actualRaiseAmount;
                    // Track committed chips for EV calculation (only what opponent can match)
                    ai.aiCommittedThisRound += actualRaiseAmount;
                } else {
                    state.opponentStack -= raiseAmountActual;
                    state.opponentBet = totalBet;
                    state.displayOpponentBet = totalBet;
                    state.pot += raiseAmountActual;
                    // Track committed chips for EV calculation
                    ai.aiCommittedThisRound += raiseAmountActual;
                }

                // If player has no chips left (all-in), they cannot respond to a raise
                // Treat this as both players having acted and progress to showdown
                if (state.playerStack <= 0) {
                    state.playerHasActed = true;
                } else {
                    state.playerHasActed = false; // Player must respond to raise
                }
                break;
}

        state.opponentHasActed = true;

        if (state.round != Round.SHOWDOWN) {
            state.currentPlayer = CurrentPlayer.PLAYER;
            checkRoundProgression();
        }
    }

private void checkRoundProgression() {
        if (state.playerBet == state.opponentBet && state.playerHasActed && state.opponentHasActed) {
            // Check if betting is closed (someone is all-in or declared all-in) - run out all remaining cards
            if (state.playerStack == 0 || state.opponentStack == 0 || 
                state.playerDeclaredAllIn || state.opponentDeclaredAllIn) {
                while (state.round != Round.SHOWDOWN) {
                    advanceRound();
                }
            } else {
                advanceRound();
            }
        }
    }

private void advanceRound() {
        state.playerBet = 0;
        state.opponentBet = 0;
        state.playerHasActed = false;
        state.opponentHasActed = false;
        
        // Note: All-in flags are already false when both players have chips
        // and neither declared all-in, so no reset needed

        // Reset AI's committed chips tracking when advancing to a new betting round
        ai.resetCommittedChips();

        // Reset betting round tracking for raise spiral prevention
        ai.resetBettingRoundTracking(state.pot);

        switch (state.round) {
            case PREFLOP:
                state.round = Round.FLOP;
                state.communityCards.add(deck.draw());
                state.communityCards.add(deck.draw());
                state.communityCards.add(deck.draw());
                break;
            case FLOP:
                state.round = Round.TURN;
                state.communityCards.add(deck.draw());
                break;
            case TURN:
                state.round = Round.RIVER;
                state.communityCards.add(deck.draw());
                break;
            case RIVER:
                state.round = Round.SHOWDOWN;
                break;
            case SHOWDOWN:
                return;
        }

        evaluateHands();

        if (state.round != Round.SHOWDOWN) {
            // Post-flop: Small Blind acts first (the dealer in heads-up)
            // If dealer is PLAYER, player acts first. If dealer is OPPONENT, opponent acts first.
            state.currentPlayer = state.dealer == Dealer.PLAYER ? CurrentPlayer.PLAYER : CurrentPlayer.OPPONENT;
        }
    }

    private void evaluateHands() {
        if (state.communityCards.size() >= 3) {
            PokerGameLogic.HandScore playerScore = PokerGameLogic.evaluate(state.playerHand, state.communityCards);
            PokerGameLogic.HandScore opponentScore = PokerGameLogic.evaluate(state.opponentHand, state.communityCards);
            state.playerHandRank = playerScore.rank;
            state.opponentHandRank = opponentScore.rank;
        } else {
            state.playerHandRank = null;
            state.opponentHandRank = null;
        }
    }
}