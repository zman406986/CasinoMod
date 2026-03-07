package data.scripts.casino;

import java.util.*;

public class PokerGame {

    public enum Action { FOLD, CHECK, CALL, RAISE, ALL_IN }

    public enum Dealer { PLAYER, OPPONENT }

    public enum Round { PREFLOP, FLOP, TURN, RIVER, SHOWDOWN }

    public enum CurrentPlayer { PLAYER, OPPONENT }

    public static class PokerState {
        public List<PokerGameLogic.Card> playerHand;
        public List<PokerGameLogic.Card> opponentHand;
        public List<PokerGameLogic.Card> communityCards;
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
        public CurrentPlayer folder = null; // Tracks who folded (if anyone)
    }

    private final PokerState state;
    private final SimplePokerAI ai;
    private PokerGameLogic.Deck deck;
    private final int smallBlind;
    private final int bigBlindAmount;

    public PokerGame() {
        this(1000, 1000, 10, 20);
    }

    @SuppressWarnings("unused")
    public PokerGame(int playerStack, int opponentStack, int unusedSmallBlind, int unusedBigBlind) {
        ai = new SimplePokerAI();
        state = new PokerState();

        // Note: unusedSmallBlind and unusedBigBlind are intentionally ignored.
        // The game calculates blinds proportionally to stack sizes for balanced gameplay.
        int avgStack = (playerStack + opponentStack) / 2;
        int calculatedBB = calculateBigBlind(avgStack);

        this.bigBlindAmount = calculatedBB;
        this.smallBlind = calculatedBB / 2;
        
        state.playerStack = playerStack;
        state.opponentStack = opponentStack;
        state.bigBlind = bigBlindAmount;

        startNewHand();
    }

    /**
     * Calculate big blind amount based on average stack size.
     * Uses proportional sizing for balanced gameplay:
     * - Small games (<= 5000): BB = stack / 80, rounded to nearest 10
     * - Medium games (<= 20000): BB = stack / 120, rounded to nearest 10
     * - Large games: BB = stack / 200, rounded to nearest 50
     */
    private static int calculateBigBlind(int avgStack) {
        if (avgStack <= 5000) {
            // Small game: BB = 1/80 of stack, round to nearest 10
            int calculatedBB = Math.max(10, avgStack / 80);
            return (calculatedBB / 10) * 10; // Round down to nearest 10
        } else if (avgStack <= 20000) {
            // Medium game: BB = 1/120 of stack, round to nearest 10
            int calculatedBB = Math.max(50, avgStack / 120);
            return (calculatedBB / 10) * 10; // Round down to nearest 10
        } else {
            // Large game: BB = 1/200 of stack, round to nearest 50
            int calculatedBB = Math.max(100, avgStack / 200);
            return (calculatedBB / 50) * 50; // Round down to nearest 50
        }
    }

    public static class PokerGameLogic {
        public enum Suit { SPADES, HEARTS, DIAMONDS, CLUBS }
        
        public enum Rank { 
            TWO(2, "2"), THREE(3, "3"), FOUR(4, "4"), FIVE(5, "5"), SIX(6, "6"), 
            SEVEN(7, "7"), EIGHT(8, "8"), NINE(9, "9"), TEN(10, "10"), 
            JACK(11, "J"), QUEEN(12, "Q"), KING(13, "K"), ACE(14, "A");
            
            public final int value;
            public final String symbol;
            Rank(int v, String s) { value = v; symbol = s; }
        }
        
        public enum HandRank {
            HIGH_CARD(1), PAIR(2), TWO_PAIR(3), THREE_OF_A_KIND(4), STRAIGHT(5), 
            FLUSH(6), FULL_HOUSE(7), FOUR_OF_A_KIND(8), STRAIGHT_FLUSH(9);
            public final int value;
            HandRank(int v) { value = v; }
        }
    
        public static class Card {
            public final Rank rank;
            public final Suit suit;
            public Card(Rank r, Suit s) { rank = r; suit = s; }
            @Override public String toString() {
                String s = switch(suit) {
                    case SPADES -> "Spades";
                    case HEARTS -> "Hearts";
                    case DIAMONDS -> "Diamonds";
                    case CLUBS -> "Clubs";
                };
                return "[" + rank.symbol + " of " + s + "]";
            }
        }
    
        public static class Deck {
            public final List<Card> cards = new ArrayList<>();
            public Deck() {
                for(Suit s : Suit.values()) for(Rank r : Rank.values()) cards.add(new Card(r, s));
            }
            public void shuffle() { Collections.shuffle(cards); }
            public Card draw() { return cards.isEmpty() ? null : cards.remove(cards.size()-1); }
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
            if (all.size() < 5) return new HandScore(HandRank.HIGH_CARD, new ArrayList<>());
            all.sort((o1, o2) -> Integer.compare(o2.rank.value, o1.rank.value));
            return analyzeHand(all);
        }
        
        private static HandScore analyzeHand(List<Card> sevenCards) {
            List<Card> flushCards = null;
            for(Suit s : Suit.values()) {
                 List<Card> suitCards = new ArrayList<>();
                 for(Card c : sevenCards) if(c.suit == s) suitCards.add(c);
                 if(suitCards.size() >= 5) { 
                     flushCards = suitCards; 
                     flushCards.sort((o1, o2) -> Integer.compare(o2.rank.value, o1.rank.value));
                     break; 
                 }
            }
            
            List<Integer> ranks = new ArrayList<>();
            for(Card c : sevenCards) if(!ranks.contains(c.rank.value)) ranks.add(c.rank.value);
            ranks.sort(Collections.reverseOrder());
            if (ranks.contains(14)) ranks.add(1); 
            
            List<Integer> straightHigh = null;
            int seq = 0;
            for(int i=0; i<ranks.size()-1; i++) {
                if (ranks.get(i) - ranks.get(i+1) == 1) {
                    seq++;
                    if (seq >= 4) { 
                        straightHigh = new ArrayList<>();
                        straightHigh.add(ranks.get(i-3));
                        break;
                    }
                } else {
                    seq = 0;
                }
            }
            
            int[] counts = new int[15];
            for(Card c : sevenCards) counts[c.rank.value]++;
            
            boolean four = false;
            boolean three = false;
            boolean pair = false;
            boolean secondPair = false;
            int fourRank = 0, threeRank = 0, pairRank = 0, secondPairRank = 0;
            
            for(int r=14; r>=2; r--) {
                if(counts[r] == 4) { four=true; fourRank=r; }
                else if(counts[r] == 3) { 
                    if(!three) { three=true; threeRank=r; }
                    else if(!pair) { pair=true; pairRank=r; } 
                }
                else if(counts[r] == 2) {
                    if(!pair) { pair=true; pairRank=r; }
                    else if(!secondPair) { secondPair=true; secondPairRank=r; }
                }
            }
            
            List<Integer> tie = new ArrayList<>();
            
            if(four) {
                tie.add(fourRank);
                for(Card c : sevenCards) if(c.rank.value != fourRank) { tie.add(c.rank.value); break; }
                return new HandScore(HandRank.FOUR_OF_A_KIND, tie);
            }
            else if(three && pair) { 
                 tie.add(threeRank);
                 tie.add(pairRank);
                 return new HandScore(HandRank.FULL_HOUSE, tie);
            }
             else if(flushCards != null) {
                 List<Integer> flushRanks = new ArrayList<>();
                 for(Card c : flushCards) flushRanks.add(c.rank.value);
                 if (flushRanks.contains(14)) flushRanks.add(1);
                 
                 int fseq = 0;
                 for(int i=0; i<flushRanks.size()-1; i++) {
                     if (flushRanks.get(i) - flushRanks.get(i+1) == 1) {
                         fseq++;
                         if (fseq >= 4) {
                             tie.add(flushRanks.get(i-3));
                             return new HandScore(HandRank.STRAIGHT_FLUSH, tie);
                         }
                     } else {
                         fseq = 0;
                     }
                 }

                 for(int i=0; i<5; i++) tie.add(flushCards.get(i).rank.value);
                 return new HandScore(HandRank.FLUSH, tie);
             }
            else if(straightHigh != null) {
                tie.add(straightHigh.get(0));
                return new HandScore(HandRank.STRAIGHT, tie);
            }
            else if(three) {
                tie.add(threeRank);
                int k=0;
                for(Card c: sevenCards) {
                    if(c.rank.value != threeRank) {
                        tie.add(c.rank.value);
                        k++;
                        if(k==2) break;
                    }
                }
                return new HandScore(HandRank.THREE_OF_A_KIND, tie);
            }
            else if(pair && secondPair) {
                tie.add(pairRank);
                tie.add(secondPairRank);
                for(Card c : sevenCards) {
                    if(c.rank.value != pairRank && c.rank.value != secondPairRank) {
                         tie.add(c.rank.value); break;
                    }
                }
                return new HandScore(HandRank.TWO_PAIR, tie);
            }
            else if(pair) {
                tie.add(pairRank);
                 int k=0;
                for(Card c : sevenCards) {
                    if(c.rank.value != pairRank) {
                         tie.add(c.rank.value); 
                         k++;
                         if(k==3) break;
                    }
                }
                return new HandScore(HandRank.PAIR, tie);
            }
            
            for(int i=0; i<5 && i<sevenCards.size(); i++) tie.add(sevenCards.get(i).rank.value);
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
            // Initialize aggression history array with Arrays.fill()
            Arrays.fill(aggressionHistory, 0.5f);
        }
        
        private void updatePersonality() {
            // Adapt AI personality based on detected player style
            PlayerStyle detectedStyle = PlayerStyle.values()[playerStyle];
            
            switch (detectedStyle) {
                case PASSIVE:
                    // Against passive players: play AGGRESSIVE to exploit them
                    personality = Personality.AGGRESSIVE;
                    break;
                case AGGRESSIVE:
                    // Against aggressive players: play TIGHT to trap them
                    personality = Personality.TIGHT;
                    break;
                case BALANCED:
                    // Against balanced players: play CALCULATED for optimal play
                    personality = Personality.CALCULATED;
                    break;
                case UNKNOWN:
                default:
                    // Unknown player: stick with CALCULATED
                    personality = Personality.CALCULATED;
                    break;
            }
        }
        

        private String estimatePlayerRange() {
            // Default range for unknown players
            String baseRange = "random";

            return switch (PlayerStyle.values()[playerStyle])
            {
                case PASSIVE -> "tight_range";  // 25% of hands
                case AGGRESSIVE -> "wide_range";   // 60% of hands
                case BALANCED -> "standard_range";  // 40% of hands
                default -> baseRange;  // Unknown player
            };
        }
        
        public String getCurrentPersonality() {
            // Return current AI personality for display/debugging
            return personality.name();
        }
        
        public String getPersonalityDescription() {
            // Return a human-readable description of current AI personality
            return switch (personality)
            {
                case TIGHT -> "The IPC Dealer is playing conservatively, waiting for premium hands.";
                case AGGRESSIVE -> "The IPC Dealer is playing aggressively, applying pressure with frequent raises.";
                case CALCULATED -> "The IPC Dealer is playing a balanced, calculated strategy.";
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
        
        public AIResponse decide(List<PokerGameLogic.Card> holeCards, List<PokerGameLogic.Card> communityCards,
                                int currentBetToCall, int potSize, int stackSize) {
            // Determine if this is pre-flop or post-flop
            if (communityCards.isEmpty()) {
                return preFlopDecision(holeCards, currentBetToCall, potSize, stackSize);
            } else {
                return postFlopDecision(holeCards, communityCards, currentBetToCall, potSize, stackSize);
            }
        }
        
        private AIResponse preFlopDecision(List<PokerGameLogic.Card> holeCards, int currentBetToCall, int potSize, int stackSize) {
            // Update AI personality based on current player style
            updatePersonality();

            // Calculate pre-flop equity
            float equity = calculatePreflopEquity(holeCards);

            // Calculate big blinds for stack size assessment
            int bigBlind = Math.max(1, potSize / 3); // Approximate BB from pot
            float bbStack = (float) stackSize / bigBlind;

            // Position adjustment: play more hands when in position (BB acts last preflop)
            float positionBonus = isInPosition ? 0.05f : 0f;
            equity += positionBonus;

            // Short stack push/fold strategy (< 15 BB)
            if (bbStack < 15 && currentBetToCall > 0) {
                return shortStackDecision(equity, currentBetToCall, potSize, stackSize, bbStack);
            }

            // If player didn't raise (checked or limped), always continue
            if (currentBetToCall == 0) {
                // Player checked or limped - play based on hand strength + position
                float openThreshold = isInPosition ? 0.35f : 0.45f; // Looser when in position

                if (equity >= openThreshold || (equity >= 0.30f && random.nextFloat() < 0.3f)) {
                    // Raise with strong hands or sometimes as a bluff
                    int raiseAmount = Math.min(stackSize / 20, stackSize);
                    raiseAmount = Math.max(bigBlind * 3, raiseAmount);
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
        
        private AIResponse postFlopDecision(List<PokerGameLogic.Card> holeCards, List<PokerGameLogic.Card> communityCards,
                                          int currentBetToCall, int potSize, int stackSize) {
            // Estimate player range based on history and aggression
            String playerRange = estimatePlayerRange();

            // Run Monte Carlo simulation for detailed equity
            MonteCarloResult mcResult = runMonteCarloSimulation(holeCards, communityCards, playerRange);
            float equity = mcResult.getTotalEquity();

            // Calculate implied odds adjustment for draws
            float impliedOddsBonus = calculateImpliedOddsBonus(holeCards, communityCards, equity);
            float adjustedEquity = equity + impliedOddsBonus;

            // Check board texture
            boolean wetBoard = isWetBoard(communityCards);

            // Use EV-based decision-making with adjusted equity for drawing hands
            return postFlopEVDecision(currentBetToCall, potSize, stackSize, playerRange, adjustedEquity, wetBoard);
        }
        
        private float calculateImpliedOddsBonus(List<PokerGameLogic.Card> holeCards, List<PokerGameLogic.Card> communityCards, float currentEquity) {
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
        
        private int countOuts(List<PokerGameLogic.Card> holeCards, List<PokerGameLogic.Card> communityCards) {
            int outs = 0;
            
            // Check for flush draw
            Map<PokerGameLogic.Suit, Integer> suitCounts = new HashMap<>();
            for (PokerGameLogic.Card c : holeCards) {
                suitCounts.put(c.suit, suitCounts.getOrDefault(c.suit, 0) + 1);
            }
            for (PokerGameLogic.Card c : communityCards) {
                suitCounts.put(c.suit, suitCounts.getOrDefault(c.suit, 0) + 1);
            }
            
            for (int count : suitCounts.values()) {
                if (count == 4) {
                    outs += 9; // Flush draw
                }
            }
            
            // Check for straight draw (simplified)
            Set<Integer> ranks = new HashSet<>();
            for (PokerGameLogic.Card c : holeCards) ranks.add(c.rank.value);
            for (PokerGameLogic.Card c : communityCards) ranks.add(c.rank.value);
            if (ranks.contains(14)) ranks.add(1); // Ace low
            
            List<Integer> sorted = new ArrayList<>(ranks);
            Collections.sort(sorted);
            
            int maxSeq = 1;
            int currentSeq = 1;
            for (int i = 0; i < sorted.size() - 1; i++) {
                if (sorted.get(i+1) - sorted.get(i) == 1) {
                    currentSeq++;
                    maxSeq = Math.max(maxSeq, currentSeq);
                } else {
                    currentSeq = 1;
                }
            }
            
            if (maxSeq == 4) {
                outs += 8; // Open-ended straight draw
            } else if (maxSeq == 3 && sorted.size() >= 3) {
                outs += 4; // Gutshot
            }
            
            return outs;
        }

        private boolean isWetBoard(List<PokerGameLogic.Card> communityCards) {
            // Simple check: board is "wet" if there are many draws possible
            // Check for 3 cards of same suit (flush draw possible)
            Map<PokerGameLogic.Suit, Integer> suitCounts = new HashMap<>();
            for (PokerGameLogic.Card c : communityCards) {
                suitCounts.put(c.suit, suitCounts.getOrDefault(c.suit, 0) + 1);
            }
            for (int count : suitCounts.values()) {
                if (count >= 3) return true; // Flush possible
            }

            // Check for connected cards (straight possible)
            Set<Integer> ranks = new HashSet<>();
            for (PokerGameLogic.Card c : communityCards) ranks.add(c.rank.value);
            List<Integer> sorted = new ArrayList<>(ranks);
            Collections.sort(sorted);

            int maxSeq = 1;
            int currentSeq = 1;
            for (int i = 0; i < sorted.size() - 1; i++) {
                if (sorted.get(i+1) - sorted.get(i) == 1) {
                    currentSeq++;
                    maxSeq = Math.max(maxSeq, currentSeq);
                } else {
                    currentSeq = 1;
                }
            }
            return maxSeq >= 3; // Straight possible with 3+ connected cards
        }

        private AIResponse postFlopEVDecision(int currentBetToCall, int potSize, int stackSize, String playerRange, float equity, boolean isWetBoard) {
            updatePersonality();
            
            // Calculate EV for each action
            float evFold = -aiCommittedThisRound; // Lose committed chips when folding
            float evCall = calculateCallEV(equity, potSize, currentBetToCall);
            
            // Calculate raise sizes to consider
            int[] raiseSizes = {
                potSize / 2,  // Half pot
                potSize,      // Pot size
                potSize * 2   // 2x pot
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

            // Apply personality adjustments to EV
            evFold = adjustEVForPersonality(evFold, Action.FOLD);
            evCall = adjustEVForPersonality(evCall, Action.CALL);
            bestRaiseEV = adjustEVForPersonality(bestRaiseEV, Action.RAISE);

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

            // Apply random deviation for unpredictability (15% chance)
            // This makes the AI more human-like with occasional hero calls, bluffs, and slow plays
            if (random.nextFloat() < 0.15f) {
                float potOdds = currentBetToCall > 0 ? (float) currentBetToCall / (potSize + currentBetToCall) : 0f;
                AIResponse deviation = randomDeviation(equity, potOdds, stackSize, potSize);
                // Only apply deviation if it's different from the optimal decision
                if (deviation.action != finalDecision.action || deviation.raiseAmount != finalDecision.raiseAmount) {
                    return deviation;
                }
            }

            return finalDecision;
        }
        
        private float adjustEVForPersonality(float ev, Action action) {
            return switch (personality)
            {
                case TIGHT -> switch (action)
                {
                    case FOLD -> ev * 1.2f; // Tight AI prefers folding
                    case CALL -> ev * 0.95f;
                    case RAISE -> ev * 0.85f; // Tight AI bluffs less
                    default -> ev;
                };
                case AGGRESSIVE -> switch (action)
                {
                    case FOLD -> ev * 0.7f; // Aggressive AI hates folding
                    case CALL -> ev * 1.05f;
                    case RAISE -> ev * 1.15f; // Aggressive AI prefers raising
                    default -> ev;
                };
                default -> ev; // No adjustment for calculated
            };
        }
        
        // Static cache for preflop equities (169 unique starting hands)
        private static final Map<String, Float> preflopEquityCache = new HashMap<>();
        private static boolean preflopCacheInitialized = false;
        
        private void initializePreflopEquityCache() {
            if (preflopCacheInitialized) return;
            
            // Generate all 169 unique starting hand combinations
            PokerGameLogic.Deck deck = new PokerGameLogic.Deck();
            for (int i = 0; i < deck.cards.size(); i++) {
                for (int j = i + 1; j < deck.cards.size(); j++) {
                    PokerGameLogic.Card c1 = deck.cards.get(i);
                    PokerGameLogic.Card c2 = deck.cards.get(j);
                    
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
        
        private String createHandKey(PokerGameLogic.Card c1, PokerGameLogic.Card c2) {
            // Normalize: higher rank first
            int v1 = Math.max(c1.rank.value, c2.rank.value);
            int v2 = Math.min(c1.rank.value, c2.rank.value);
            boolean suited = (c1.suit == c2.suit);
            
            // Key format: "rank1_rank2_suited" or "rank1_rank2_offsuit"
            // For pairs: just "rank_pair"
            if (v1 == v2) {
                return v1 + "_pair";
            }
            return v1 + "_" + v2 + "_" + (suited ? "s" : "o");
        }
        
        private float calculatePreflopEquityMonteCarlo(PokerGameLogic.Card c1, PokerGameLogic.Card c2) {
            // Run quick Monte Carlo for preflop equity (50 samples is enough for preflop)
            List<PokerGameLogic.Card> ourHand = new ArrayList<>();
            ourHand.add(c1);
            ourHand.add(c2);
            
            int wins = 0;
            int ties = 0;
            int samples = 50;

            for (int i = 0; i < samples; i++) {
                PokerGameLogic.Deck deck = new PokerGameLogic.Deck();
                deck.cards.remove(c1);
                deck.cards.remove(c2);
                Collections.shuffle(deck.cards, random);

                // Draw opponent hand
                List<PokerGameLogic.Card> oppHand = new ArrayList<>();
                oppHand.add(deck.cards.remove(0));
                oppHand.add(deck.cards.remove(0));

                // Draw 5 community cards
                List<PokerGameLogic.Card> community = new ArrayList<>();
                for (int j = 0; j < 5; j++) {
                    community.add(deck.cards.remove(0));
                }

                PokerGameLogic.HandScore ourScore = PokerGameLogic.evaluate(ourHand, community);
                PokerGameLogic.HandScore oppScore = PokerGameLogic.evaluate(oppHand, community);

                int cmp = ourScore.compareTo(oppScore);
                if (cmp > 0) wins++;
                else if (cmp == 0) ties++;
            }

            return (wins + ties * 0.5f) / samples;
        }
        
        private float calculatePreflopEquity(List<PokerGameLogic.Card> holeCards) {
            // Initialize cache on first use
            initializePreflopEquityCache();
            
            // Lookup from cache
            PokerGameLogic.Card c1 = holeCards.get(0);
            PokerGameLogic.Card c2 = holeCards.get(1);
            String key = createHandKey(c1, c2);
            
            Float cached = preflopEquityCache.get(key);
            if (cached != null) {
                return cached;
            }
            
            // Fallback to simple calculation (shouldn't happen)
            return calculatePreflopEquitySimple(holeCards);
        }
        
        private float calculatePreflopEquitySimple(List<PokerGameLogic.Card> holeCards) {
            PokerGameLogic.Card c1 = holeCards.get(0);
            PokerGameLogic.Card c2 = holeCards.get(1);
            int v1 = Math.max(c1.rank.value, c2.rank.value);
            int v2 = Math.min(c1.rank.value, c2.rank.value);
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
        private HandCategory classifyPreflopHand(List<PokerGameLogic.Card> holeCards) {
            PokerGameLogic.Card c1 = holeCards.get(0);
            PokerGameLogic.Card c2 = holeCards.get(1);
            int v1 = Math.max(c1.rank.value, c2.rank.value);
            int v2 = Math.min(c1.rank.value, c2.rank.value);
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
                (v1 == 14 && v2 == 12 && !suited)) return HandCategory.STRONG;

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

        private MonteCarloResult runMonteCarloSimulation(
                List<PokerGameLogic.Card> holeCards,
                List<PokerGameLogic.Card> communityCards,
                String playerRange) {
            int wins = 0;
            int ties = 0;
            int losses = 0;
            int simulationCount = CasinoConfig.POKER_MONTE_CARLO_SAMPLES;

            for (int i = 0; i < simulationCount; i++) {
                // Create temporary deck without known cards
                PokerGameLogic.Deck tempDeck = new PokerGameLogic.Deck();
                tempDeck.cards.removeAll(holeCards);
                tempDeck.cards.removeAll(communityCards);

                // Shuffle and complete the board
                Collections.shuffle(tempDeck.cards, random);
                List<PokerGameLogic.Card> completeBoard = new ArrayList<>(communityCards);

                // Add remaining cards to complete board
                int cardsNeeded = 5 - communityCards.size();
                for (int j = 0; j < cardsNeeded && !tempDeck.cards.isEmpty(); j++) {
                    completeBoard.add(tempDeck.cards.remove(0));
                }

                // Generate opponent hand from range
                List<PokerGameLogic.Card> opponentHand = generateRandomOpponentHand(playerRange, tempDeck);

                // Evaluate hands
                PokerGameLogic.HandScore ourScore = PokerGameLogic.evaluate(holeCards, completeBoard);
                PokerGameLogic.HandScore oppScore = PokerGameLogic.evaluate(opponentHand, completeBoard);

                int cmp = ourScore.compareTo(oppScore);
                if (cmp > 0) {
                    wins++;
                } else if (cmp == 0) {
                    ties++;
                } else {
                    losses++;
                }

                // Early exit for extreme equities after 50 samples
                if (i == 49) {
                    float currentEquity = (wins + ties * 0.5f) / (i + 1);
                    if (currentEquity > 0.90f || currentEquity < 0.10f) {
                        // Equity is extreme, no need for more samples
                        return new MonteCarloResult(wins, ties, losses, i + 1);
                    }
                }
            }

            return new MonteCarloResult(wins, ties, losses, simulationCount);
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
        
        private List<PokerGameLogic.Card> generateRandomOpponentHand(String playerRange, PokerGameLogic.Deck deck) {
            // Generate a hand based on the player's estimated range
            List<PokerGameLogic.Card> hand = new ArrayList<>();
            
            // Define hand categories based on poker hand rankings
            List<List<PokerGameLogic.Card>> premiumHands = new ArrayList<>(); // AA, KK, QQ, AK
            List<List<PokerGameLogic.Card>> strongHands = new ArrayList<>();  // JJ, TT, AQ, AJ, KQ
            List<List<PokerGameLogic.Card>> playableHands = new ArrayList<>(); // 99-22, AT, KJ, QJ, suited connectors
            List<List<PokerGameLogic.Card>> weakHands = new ArrayList<>();     // Everything else
            
            // Generate all possible hands and categorize them
            PokerGameLogic.Deck tempDeck = new PokerGameLogic.Deck();
            for (int i = 0; i < tempDeck.cards.size(); i++) {
                for (int j = i + 1; j < tempDeck.cards.size(); j++) {
                    List<PokerGameLogic.Card> possibleHand = new ArrayList<>();
                    possibleHand.add(tempDeck.cards.get(i));
                    possibleHand.add(tempDeck.cards.get(j));
                    
                    float equity = calculatePreflopEquity(possibleHand);
                    
                    if (equity >= 0.70f) {
                        premiumHands.add(possibleHand);
                    } else if (equity >= 0.55f) {
                        strongHands.add(possibleHand);
                    } else if (equity >= 0.40f) {
                        playableHands.add(possibleHand);
                    } else {
                        weakHands.add(possibleHand);
                    }
                }
            }
            
            // Select hand based on player range
            List<List<PokerGameLogic.Card>> selectedPool;
            switch (playerRange) {
                case "tight_range":
                    // 70% premium, 25% strong, 5% playable
                    float tightRoll = random.nextFloat();
                    if (tightRoll < 0.70f && !premiumHands.isEmpty()) {
                        selectedPool = premiumHands;
                    } else if (tightRoll < 0.95f && !strongHands.isEmpty()) {
                        selectedPool = strongHands;
                    } else if (!playableHands.isEmpty()) {
                        selectedPool = playableHands;
                    } else {
                        selectedPool = weakHands;
                    }
                    break;
                case "wide_range":
                    // 20% premium, 30% strong, 30% playable, 20% weak
                    float wideRoll = random.nextFloat();
                    if (wideRoll < 0.20f && !premiumHands.isEmpty()) {
                        selectedPool = premiumHands;
                    } else if (wideRoll < 0.50f && !strongHands.isEmpty()) {
                        selectedPool = strongHands;
                    } else if (wideRoll < 0.80f && !playableHands.isEmpty()) {
                        selectedPool = playableHands;
                    } else {
                        selectedPool = weakHands;
                    }
                    break;
                case "standard_range":
                default:
                    // 35% premium, 35% strong, 25% playable, 5% weak
                    float stdRoll = random.nextFloat();
                    if (stdRoll < 0.35f && !premiumHands.isEmpty()) {
                        selectedPool = premiumHands;
                    } else if (stdRoll < 0.70f && !strongHands.isEmpty()) {
                        selectedPool = strongHands;
                    } else if (stdRoll < 0.95f && !playableHands.isEmpty()) {
                        selectedPool = playableHands;
                    } else {
                        selectedPool = weakHands;
                    }
                    break;
            }
            
            // Try to find a hand from the selected pool that doesn't conflict with known cards
            if (!selectedPool.isEmpty()) {
                // Shuffle the pool for randomness
                Collections.shuffle(selectedPool, random);
                
                for (List<PokerGameLogic.Card> candidateHand : selectedPool) {
                    // Check if both cards are still available in the deck
                    boolean available = true;
                    for (PokerGameLogic.Card c : candidateHand) {
                        if (!deck.cards.contains(c)) {
                            available = false;
                            break;
                        }
                    }
                    
                    if (available) {
                        hand.addAll(candidateHand);
                        deck.cards.removeAll(candidateHand);
                        return hand;
                    }
                }
            }
            
            // Fallback: draw random cards from deck
            if (deck.cards.size() >= 2) {
                hand.add(deck.cards.remove(0));
                hand.add(deck.cards.remove(0));
            }

            return hand;
        }

        public void trackPlayerAction(boolean isRaise, boolean isFold, boolean isCheck, boolean isPreFlop, boolean putMoneyInPot) {
            totalPlayerActions++;
            
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
        public void trackAIFoldedToPlayerBet(int potSize) {
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

    @SuppressWarnings("unused")
    public String getAIPersonality() {
        return ai.getCurrentPersonality();
    }

    public String getAIPersonalityDescription() {
        return ai.getPersonalityDescription();
    }

    public void startNewHand() {
        deck = new PokerGameLogic.Deck();
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
        state.pot = 0;
        state.folder = null; // Reset folder for new hand

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
    @SuppressWarnings("unused")
    public Dealer getSmallBlind() {
        // Small Blind is the dealer
        return state.dealer;
    }

    /**
     * Returns true if it's post-flop (flop, turn, or river)
     */
    @SuppressWarnings("unused")
    public boolean isPostFlop() {
        return state.round == Round.FLOP || state.round == Round.TURN || state.round == Round.RIVER;
    }

    private void postBlinds() {
        if (state.dealer == Dealer.PLAYER) {
            // Small blind (player) - ensure doesn't go negative
            int playerSmallBlind = Math.min(smallBlind, state.playerStack);
            state.playerBet = playerSmallBlind;
            state.playerStack -= playerSmallBlind;
            
            // Big blind (opponent) - ensure doesn't go negative
            int opponentBigBlind = Math.min(bigBlindAmount, state.opponentStack);
            state.opponentBet = opponentBigBlind;
            state.opponentStack -= opponentBigBlind;
        } else {
            // Small blind (opponent) - ensure doesn't go negative
            int opponentSmallBlind = Math.min(smallBlind, state.opponentStack);
            state.opponentBet = opponentSmallBlind;
            state.opponentStack -= opponentSmallBlind;
            
            // Big blind (player) - ensure doesn't go negative
            int playerBigBlind = Math.min(bigBlindAmount, state.playerStack);
            state.playerBet = playerBigBlind;
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
                state.pot += callAmount;
                break;
            case RAISE:
                // raiseAmount is the desired total bet size (e.g., 3x opponent's raise)
                // NOT an additional amount on top of opponent's bet
                int totalBet = raiseAmount;
                int raiseAmountActual = totalBet - state.playerBet;
                // Protect against betting more than available stack
                if (raiseAmountActual > state.playerStack) {
                    raiseAmountActual = state.playerStack;
                    totalBet = state.playerBet + raiseAmountActual;
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
                    state.playerStack += excessToReturn; // Return excess immediately
                    state.playerBet = maxOpponentCanMatch;
                    state.pot += actualRaiseAmount;
                } else {
                    state.playerStack -= raiseAmountActual;
                    state.playerBet = totalBet;
                    state.pot += raiseAmountActual;
                }
                break;
            case ALL_IN:
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
                    state.playerStack = excessToReturn; // Return excess chips
                } else {
                    // Normal all-in where opponent can match or has already matched
                    state.pot += allInAmount;
                    state.playerBet += allInAmount;
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

        if (state.round != Round.SHOWDOWN) {
            state.currentPlayer = CurrentPlayer.OPPONENT;
            checkRoundProgression();
        }
    }

    public SimplePokerAI.AIResponse getOpponentAction() {
        int currentBetToCall = state.playerBet - state.opponentBet;
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
                state.pot += callAmount;
                break;
            case RAISE:
                int totalBet = response.raiseAmount;
                int raiseAmountActual = totalBet - state.opponentBet;
                // Protect against betting more than available stack
                if (raiseAmountActual > state.opponentStack) {
                    raiseAmountActual = state.opponentStack;
                    totalBet = state.opponentBet + raiseAmountActual;
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
                    state.opponentStack += excessToReturn; // Return excess immediately
                    state.opponentBet = maxPlayerCanMatch;
                    state.pot += actualRaiseAmount;
                    // Track committed chips for EV calculation (only what opponent can match)
                    ai.aiCommittedThisRound += actualRaiseAmount;
                } else {
                    state.opponentStack -= raiseAmountActual;
                    state.opponentBet = totalBet;
                    state.pot += raiseAmountActual;
                    // Track committed chips for EV calculation
                    ai.aiCommittedThisRound += raiseAmountActual;
                }
                break;
        }

        if (state.round != Round.SHOWDOWN) {
            state.currentPlayer = CurrentPlayer.PLAYER;
            checkRoundProgression();
        }
    }

    private void checkRoundProgression() {
        if (state.playerBet == state.opponentBet) {
            advanceRound();
        }
    }

    private void advanceRound() {
        state.playerBet = 0;
        state.opponentBet = 0;

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