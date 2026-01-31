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
    }

    private PokerState state;
    private SimplePokerAI ai;
    private PokerGameLogic.Deck deck;
    private int startingStack;
    private int smallBlind;
    private int bigBlindAmount;

    public PokerGame() {
        this(1000, 1000, 10, 20);
    }

    public PokerGame(int playerStack, int opponentStack, int smallBlind, int bigBlind) {
        ai = new SimplePokerAI();
        state = new PokerState();
        this.startingStack = playerStack; // Keep for reference, though state.playerStack is initialized in startNewHand
        this.smallBlind = smallBlind;
        this.bigBlindAmount = bigBlind;
        
        state.playerStack = playerStack;
        state.opponentStack = opponentStack;
        state.bigBlind = bigBlindAmount;
        
        startNewHand();
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

        // Core AI state variables
        private float aggressionMeter = 0.5f;                    // 0.0 (passive) to 1.0 (aggressive)
        private final float[] aggressionHistory = new float[10];      // Circular buffer for smoothing, made final
        private int historyIndex = 0;                            // Index for circular buffer
        private int playerStyle = 0;                             // 0: unknown, 1: passive, 2: balanced, 3: aggressive
        private final int handCount = 0;                         // Total hands played this session, made final (though not used)
        private final boolean sawFlop = false;                   // Track if flop was reached, made final (though not used)
        private int totalPlayerActions = 0;
        private Personality personality = Personality.CALCULATED;  // AI's current personality, adapts to player style
        // Note: aggressiveActions was never used, so removed

        public SimplePokerAI() {
            // Initialize with CALCULATED personality as default
            personality = Personality.CALCULATED;
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
        
        private float getPersonalityThreshold(String thresholdType) {
            // Get threshold values based on current AI personality
            switch (personality) {
                case TIGHT:
                    switch (thresholdType) {
                        case "raise": return CasinoConfig.POKER_AI_TIGHT_THRESHOLD_RAISE / 100.0f;
                        case "fold": return CasinoConfig.POKER_AI_TIGHT_THRESHOLD_FOLD / 100.0f;
                        case "stackFold": return CasinoConfig.POKER_AI_STACK_PERCENT_FOLD_TIGHT;
                        default: return 0.5f;
                    }
                case AGGRESSIVE:
                    switch (thresholdType) {
                        case "raise": return CasinoConfig.POKER_AI_AGGRESSIVE_THRESHOLD_RAISE / 100.0f;
                        case "fold": return CasinoConfig.POKER_AI_AGGRESSIVE_THRESHOLD_FOLD / 100.0f;
                        case "stackFold": return 0.1f; // Aggressive AI rarely folds
                        default: return 0.5f;
                    }
                case CALCULATED:
                default:
                    switch (thresholdType) {
                        case "raise": return CasinoConfig.POKER_AI_NORMAL_THRESHOLD_RAISE / 100.0f;
                        case "fold": return CasinoConfig.POKER_AI_NORMAL_THRESHOLD_FOLD / 100.0f;
                        case "stackFold": return CasinoConfig.POKER_AI_STACK_PERCENT_CALL_LIMIT;
                        default: return 0.5f;
                    }
            }
        }
        
        private String estimatePlayerRange() {
            // Default range for unknown players
            String baseRange = "random";
            
            switch(PlayerStyle.values()[playerStyle]) {
                case PASSIVE:
                    return "tight_range";  // 25% of hands
                case AGGRESSIVE:
                    return "wide_range";   // 60% of hands
                case BALANCED:
                    return "standard_range";  // 40% of hands
                default:
                    return baseRange;  // Unknown player
            }
        }
        
        public String getCurrentPersonality() {
            // Return current AI personality for display/debugging
            return personality.name();
        }
        
        public String getPersonalityDescription() {
            // Return a human-readable description of current AI personality
            switch (personality) {
                case TIGHT:
                    return "The IPC Dealer is playing conservatively, waiting for premium hands.";
                case AGGRESSIVE:
                    return "The IPC Dealer is playing aggressively, applying pressure with frequent raises.";
                case CALCULATED:
                    return "The IPC Dealer is playing a balanced, calculated strategy.";
                default:
                    return "The IPC Dealer is adapting to your play style.";
            }
        }
        
        private int calculateBetSize(float equity, int potSize, int stackSize) {
            // Use configured raise amounts as base, with pot-based adjustments
            int[] raiseAmounts = CasinoConfig.POKER_RAISE_AMOUNTS;
            
            // Select base raise amount based on equity strength
            int baseRaise;
            if (equity > 0.65f) {
                // High strength - use largest raise or pot-based
                baseRaise = raiseAmounts[Math.min(raiseAmounts.length - 1, 3)];
                int potBasedBet = (int)(potSize * (0.6f + (random.nextFloat() * 0.2f - 0.1f)));
                baseRaise = Math.max(baseRaise, potBasedBet);
            } else if (equity > 0.45f) {
                // Medium strength - use middle raise
                baseRaise = raiseAmounts[Math.min(raiseAmounts.length - 1, 2)];
                int potBasedBet = (int)(potSize * (0.45f + (random.nextFloat() * 0.1f - 0.05f)));
                baseRaise = Math.max(baseRaise, potBasedBet);
            } else {
                // Bluff sizing - use smallest raise
                baseRaise = raiseAmounts[0];
                int potBasedBet = (int)(potSize * (0.35f + (random.nextFloat() * 0.1f - 0.05f)));
                baseRaise = Math.max(baseRaise, potBasedBet);
            }
            
            // Add random variation if configured
            if (CasinoConfig.POKER_AI_MAX_RAISE_RANDOM_ADDITION > 0) {
                baseRaise += random.nextInt(CasinoConfig.POKER_AI_MAX_RAISE_RANDOM_ADDITION);
            }
            
            return Math.min(baseRaise, stackSize); // Don't bet more than we have
        }
        
        private AIResponse randomDeviation(float equity, float potOdds, int stackSize, int potSize) {
            int deviationType = random.nextInt(4) + 1; // 1-4
            int bluffRaise = 0;
            int overbet = 0;
            
            switch(deviationType) {
                case 1: // Hero call
                    if (equity > potOdds * 0.8f) {
                        return new AIResponse(Action.CALL, 0);
                    }
                    break;
                case 2: // Bluff raise
                    if (equity < 0.4f && random.nextFloat() < 0.3f) {
                        bluffRaise = (int)(potSize * 0.5f);
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
                        overbet = (int)(potSize * 1.5f);
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
                                int currentBetToCall, int potSize, int stackSize, int opponentStackSize) {
            // Removed unused opponentStackSize parameter
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
            
            // If player didn't raise (checked or limped), always continue
            if (currentBetToCall == 0) {
                // Player checked or limped - always continue
                if (random.nextFloat() < CasinoConfig.POKER_AI_DRAW_CHANCE && equity >= CasinoConfig.POKER_AI_STRENGTH_MEDIUM / 100.0f) {
                    // Small raise sometimes
                    int raiseAmount = Math.min(stackSize / 20, stackSize - currentBetToCall);
                    raiseAmount = Math.max(CasinoConfig.POKER_AI_MIN_RAISE_VALUE, raiseAmount);
                    return new AIResponse(Action.RAISE, raiseAmount);
                } else {
                    return new AIResponse(Action.CALL, 0); // Call to see flop
                }
            } else {
                // Player bet/raised - use equity-based decision with personality-based thresholds
                float potOdds = (float)currentBetToCall / (potSize + currentBetToCall);
                
                // Use personality-based thresholds
                float raiseThreshold = getPersonalityThreshold("raise");
                float callThreshold = getPersonalityThreshold("fold");
                float bluffFoldThreshold = 1.0f - CasinoConfig.POKER_AI_BLUFF_FOLD_CHANCE;
                
                if (equity > raiseThreshold) {
                    int raiseAmount = (int)(currentBetToCall * 2.5f);
                    raiseAmount = Math.min(stackSize - currentBetToCall, raiseAmount);
                    raiseAmount = Math.max(CasinoConfig.POKER_AI_MIN_RAISE_VALUE, raiseAmount);
                    if (CasinoConfig.POKER_AI_MAX_RAISE_RANDOM_ADDITION > 0) {
                        raiseAmount += random.nextInt(CasinoConfig.POKER_AI_MAX_RAISE_RANDOM_ADDITION);
                    }
                    return new AIResponse(Action.RAISE, raiseAmount);
                } else if (equity > callThreshold) {
                    return new AIResponse(Action.CALL, 0);
                } else if (equity < callThreshold * 0.6f && random.nextFloat() > bluffFoldThreshold) {
                    return new AIResponse(Action.FOLD, 0);
                }
                
                // Default: call with marginal hands (heads-up adjustment)
                return new AIResponse(Action.CALL, 0);
            }
        }
        
        private AIResponse postFlopDecision(List<PokerGameLogic.Card> holeCards, List<PokerGameLogic.Card> communityCards, 
                                          int currentBetToCall, int potSize, int stackSize) {
            // Estimate player range based on history and aggression
            String playerRange = estimatePlayerRange();
            
            // Calculate current equity against estimated range
            float equity = calculateEquityMonteCarlo(holeCards, communityCards, playerRange);
            float potOdds = (float)currentBetToCall / (potSize + currentBetToCall);
            
            return postFlopDecisionLogic(equity, potOdds, stackSize, potSize);
        }
        
        private AIResponse postFlopDecisionLogic(float equity, float potOdds, int stackSize, int potSize) {
            // Update AI personality based on current player style
            updatePersonality();
            
            // Base decision on equity adjusted by aggression meter
            float aggressionFactor = aggressionMeter;
            
            // Use personality-based thresholds
            float strengthHigh = CasinoConfig.POKER_AI_STRENGTH_HIGH / 100.0f;
            float strengthMedium = CasinoConfig.POKER_AI_STRENGTH_MEDIUM / 100.0f;
            float stackPercentCallLimit = getPersonalityThreshold("stackFold");
            float stackPercentCallMedium = CasinoConfig.POKER_AI_STACK_PERCENT_CALL_MEDIUM;
            float potPercentCallDraw = CasinoConfig.POKER_AI_POT_PERCENT_CALL_DRAW;
            float bluffStrengthBoost = CasinoConfig.POKER_AI_BLUFF_STRENGTH_BOOST / 100.0f;
            float checkRaiseChance = CasinoConfig.POKER_AI_CHECK_RAISE_CHANCE;
            
            // Adjust thresholds based on personality
            float callThreshold, raiseThreshold, bluffThreshold;
            
            switch (personality) {
                case TIGHT:
                    // Tight AI: plays fewer hands, higher standards
                    callThreshold = potOdds * (1.0f - aggressionFactor * 0.1f); // Less influenced by aggression
                    raiseThreshold = potOdds + 0.3f; // Higher threshold to raise
                    bluffThreshold = 0.35f; // Less likely to bluff
                    checkRaiseChance *= 0.5f; // Rarely check-raises
                    break;
                case AGGRESSIVE:
                    // Aggressive AI: raises more, bluffs more
                    callThreshold = potOdds * (1.0f - aggressionFactor * 0.2f); // More influenced by aggression
                    raiseThreshold = potOdds + 0.1f; // Lower threshold to raise
                    bluffThreshold = 0.15f; // More likely to bluff
                    checkRaiseChance *= 1.5f; // More likely to check-raise
                    bluffStrengthBoost *= 1.5f; // Stronger bluffs
                    break;
                case CALCULATED:
                default:
                    // Calculated AI: balanced, optimal play
                    callThreshold = potOdds * (1.0f - aggressionFactor * 0.15f);
                    raiseThreshold = potOdds + 0.2f + (aggressionFactor * 0.1f);
                    bluffThreshold = 0.25f - (aggressionFactor * 0.1f);
                    break;
            }
            
            // Random deviation (10% chance)
            if (random.nextFloat() < 0.1f) {
                return randomDeviation(equity, potOdds, stackSize, potSize);
            }
            
            // Decision logic
            if (equity < callThreshold) {
                // Consider bluff catching or folding
                if (equity > bluffThreshold && random.nextFloat() < 0.3f) {
                    return new AIResponse(Action.CALL, 0); // Bluff catch
                }
                return new AIResponse(Action.FOLD, 0);
            }
            
            if (equity >= raiseThreshold) {
                // Value raise
                int betSize = calculateBetSize(equity, potSize, stackSize);
                return new AIResponse(Action.RAISE, betSize);
            }
            
            // Check-raise logic
            if (equity > strengthMedium && random.nextFloat() < checkRaiseChance) {
                int betSize = calculateBetSize(equity + bluffStrengthBoost, potSize, stackSize);
                return new AIResponse(Action.RAISE, betSize);
            }
            
            // Default: call with equity advantage
            return new AIResponse(Action.CALL, 0);
        }
        
        private float calculatePreflopEquity(List<PokerGameLogic.Card> holeCards) {
            // Simplified pre-flop equity calculation based on hand strength
            PokerGameLogic.Card c1 = holeCards.get(0);
            PokerGameLogic.Card c2 = holeCards.get(1);
            int v1 = Math.max(c1.rank.value, c2.rank.value);
            int v2 = Math.min(c1.rank.value, c2.rank.value);
            
            float equity = 0.35f; // Default value
            
            // Premium pairs
            if (v1 == v2) {
                if (v1 >= 11) equity = 0.80f; // J, Q, K, A
                else if (v1 >= 8) equity = 0.70f; // Strong pairs
                else equity = 0.55f; // Small pairs
            }
            // Suited connectors
            else if (c1.suit == c2.suit && Math.abs(v1 - v2) == 1) {
                equity = 0.65f; // Suited connectors
            }
            // Other suited
            else if (c1.suit == c2.suit) {
                if (v1 == 14) equity = 0.60f; // Suited ace
                else if (v1 >= 11) equity = 0.55f; // Suited face cards
                else equity = 0.45f; // Other suited
            }
            // Offsuit connectors
            else if (Math.abs(v1 - v2) == 1) {
                equity = 0.50f; // Connectors
            }
            // High cards
            else if (v1 >= 11) {
                equity = 0.50f; // At least one face card
            }
            
            return equity;
        }
        
        private float calculateEquityMonteCarlo(List<PokerGameLogic.Card> holeCards, List<PokerGameLogic.Card> communityCards, String playerRange) {
            // Simplified Monte Carlo simulation with limited samples
            int wins = 0;
            int samples = 50; // Limited to 50 samples for performance
            
            for (int i = 0; i < samples; i++) {
                // Create temporary deck without known cards
                PokerGameLogic.Deck tempDeck = new PokerGameLogic.Deck();
                // Remove known cards from deck
                tempDeck.cards.removeAll(holeCards);
                tempDeck.cards.removeAll(communityCards);
                
                // Shuffle and complete the board
                Collections.shuffle(tempDeck.cards, random);
                List<PokerGameLogic.Card> completeBoard = new ArrayList<>(communityCards);
                
                // Add remaining cards to complete board (if needed)
                int cardsNeeded = 5 - communityCards.size();
                for (int j = 0; j < cardsNeeded && !tempDeck.cards.isEmpty(); j++) {
                    completeBoard.add(tempDeck.cards.remove(0));
                }
                
                // Generate a random opponent hand from range
                List<PokerGameLogic.Card> opponentHand = generateRandomOpponentHand(playerRange, tempDeck);
                
                // Evaluate hands
                PokerGameLogic.HandScore ourScore = PokerGameLogic.evaluate(holeCards, completeBoard);
                PokerGameLogic.HandScore oppScore = PokerGameLogic.evaluate(opponentHand, completeBoard);
                
                if (ourScore.compareTo(oppScore) > 0) {
                    wins++;
                } else if (ourScore.compareTo(oppScore) == 0) {
                    wins += 0.5f; // Split pot
                }
            }
            
            return (float)wins / samples;
        }
        
        private List<PokerGameLogic.Card> generateRandomOpponentHand(String playerRange, PokerGameLogic.Deck deck) {
            // Generate a random hand based on the player's range
            List<PokerGameLogic.Card> hand = new ArrayList<>();
            
            // For simplicity, pick two random cards from the remaining deck
            if (deck.cards.size() >= 2) {
                hand.add(deck.cards.remove(0));
                hand.add(deck.cards.remove(0));
            } else if (deck.cards.size() == 1) {
                hand.add(deck.cards.remove(0));
                // Add another random card if possible
                PokerGameLogic.Deck backupDeck = new PokerGameLogic.Deck();
                backupDeck.cards.removeAll(hand);
                backupDeck.cards.removeAll(deck.cards);
                if (!backupDeck.cards.isEmpty()) {
                    hand.add(backupDeck.cards.get(random.nextInt(backupDeck.cards.size())));
                }
            }
            
            return hand;
        }
        
        // Removed unused method: calculateEquity(List<PokerGameLogic.Card> holeCards, List<PokerGameLogic.Card> communityCards, int stackSize, int opponentStackSize)
        
        private float estimatePreFlopEquity(List<PokerGameLogic.Card> holeCards) {
            PokerGameLogic.Card c1 = holeCards.get(0);
            PokerGameLogic.Card c2 = holeCards.get(1);
            int v1 = Math.max(c1.rank.value, c2.rank.value);
            int v2 = Math.min(c1.rank.value, c2.rank.value);
            
            float equity = 35; // Default value instead of redundant initialization
            
            // Premium pairs
            if (v1 == v2) {
                if (v1 >= 11) equity = 80; // J, Q, K, A
                else if (v1 >= 8) equity = 70; // Strong pairs
                else equity = 55; // Small pairs
            }
            // Suited connectors
            else if (c1.suit == c2.suit && Math.abs(v1 - v2) == 1) {
                equity = 65; // Suited connectors
            }
            // Other suited
            else if (c1.suit == c2.suit) {
                if (v1 == 14) equity = 60; // Suited ace
                else if (v1 >= 11) equity = 55; // Suited face cards
                else equity = 45; // Other suited
            }
            // Offsuit connectors
            else if (Math.abs(v1 - v2) == 1) {
                equity = 50; // Connectors
            }
            // High cards
            else if (v1 >= 11) {
                equity = 50; // At least one face card
            }
            // Weak hands are covered by default value
            
            return equity;
        }
    


        private float getStrengthPercentage(PokerGameLogic.HandScore hand) {
            float base = switch(hand.rank) {
                case HIGH_CARD -> 10;
                case PAIR -> 35;
                case TWO_PAIR -> 55;
                case THREE_OF_A_KIND -> 70;
                case STRAIGHT -> 85;
                case FLUSH -> 90;
                case FULL_HOUSE -> 95;
                case FOUR_OF_A_KIND -> 99;
                case STRAIGHT_FLUSH -> 100;
            };
            if (!hand.tieBreakers.isEmpty()) {
                base += (hand.tieBreakers.get(0) / 14f) * 5f;
            }
            return Math.min(100f, base);
        }

        private boolean hasFlushDraw(List<PokerGameLogic.Card> hole, List<PokerGameLogic.Card> comm) {
            Map<PokerGameLogic.Suit, Integer> counts = new HashMap<>();
            // Enhanced for loop instead of traditional for loop
            for (PokerGameLogic.Card c : hole) counts.put(c.suit, counts.getOrDefault(c.suit, 0) + 1);
            for (PokerGameLogic.Card c : comm) counts.put(c.suit, counts.getOrDefault(c.suit, 0) + 1);
            for (int val : counts.values()) if (val >= 4) return true;
            return false;
        }

        private boolean hasStraightDraw(List<PokerGameLogic.Card> hole, List<PokerGameLogic.Card> comm) {
            Set<Integer> ranks = new HashSet<>();
            for (PokerGameLogic.Card c : hole) ranks.add(c.rank.value);
            for (PokerGameLogic.Card c : comm) ranks.add(c.rank.value);
            if (ranks.contains(14)) ranks.add(1); 

            List<Integer> sorted = new ArrayList<>(ranks);
            Collections.sort(sorted);
            
            int maxSeq = 0;
            int currentSeq = 1;
            for (int i = 0; i < sorted.size() - 1; i++) {
                if (sorted.get(i+1) - sorted.get(i) == 1) {
                    currentSeq++;
                    maxSeq = Math.max(maxSeq, currentSeq);
                } else {
                    currentSeq = 1;
                }
            }
            return maxSeq >= 4; 
        }
        
        public void trackPlayerAction(boolean isRaise, boolean isFold) {
            // Removed unused isFold parameter
            totalPlayerActions++;
            
            if (isRaise) {
                // Removed aggressiveActions as it was never used
            }
            
            // Update aggression meter based on player actions
            updateAggressionMeter(isRaise);
            
            // Classify player style after sufficient hands
            if (totalPlayerActions > 5) {
                float aggressionMeterLocal = aggressionMeter; // Using local variable instead of field
                if (aggressionMeterLocal < 0.3f) {
                    playerStyle = 1; // PASSIVE
                } else if (aggressionMeterLocal > 0.7f) {
                    playerStyle = 3; // AGGRESSIVE
                } else {
                    playerStyle = 2; // BALANCED
                }
            }
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
            for (int i = 0; i < aggressionHistory.length; i++) {
                recentAggression += weight * aggressionHistory[i];
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

        state.dealer = state.dealer == Dealer.PLAYER ? Dealer.OPPONENT : Dealer.PLAYER;

        postBlinds();

        state.round = Round.PREFLOP;
        state.currentPlayer = state.dealer == Dealer.PLAYER ? CurrentPlayer.OPPONENT : CurrentPlayer.PLAYER;

        evaluateHands();
    }

    private void postBlinds() {
        if (state.dealer == Dealer.PLAYER) {
            state.playerBet = smallBlind;
            state.playerStack -= smallBlind;
            state.opponentBet = bigBlindAmount;
            state.opponentStack -= bigBlindAmount;
        } else {
            state.opponentBet = smallBlind;
            state.opponentStack -= smallBlind;
            state.playerBet = bigBlindAmount;
            state.playerStack -= bigBlindAmount;
        }
        state.pot = state.playerBet + state.opponentBet;
    }

    public void processPlayerAction(Action action, int raiseAmount) {
        switch (action) {
            case FOLD:
                state.opponentStack += state.pot;
                state.pot = 0;
                state.round = Round.SHOWDOWN;
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
                int totalBet = state.opponentBet + raiseAmount;
                int raiseAmountActual = totalBet - state.playerBet;
                state.playerStack -= raiseAmountActual;
                state.playerBet = totalBet;
                state.pot += raiseAmountActual;
                break;
            case ALL_IN:
                int allInAmount = state.playerStack;
                state.pot += allInAmount;
                state.playerBet += allInAmount;
                state.playerStack = 0;
                break;
        }

        ai.trackPlayerAction(action == Action.RAISE || action == Action.ALL_IN, action == Action.FOLD);

        if (state.round != Round.SHOWDOWN) {
            state.currentPlayer = CurrentPlayer.OPPONENT;
            checkRoundProgression();
        }
    }

    public SimplePokerAI.AIResponse getOpponentAction() {
        int currentBetToCall = state.playerBet - state.opponentBet;
        return ai.decide(state.opponentHand, state.communityCards, currentBetToCall, state.pot, state.opponentStack, state.playerStack);
    }

    public void processOpponentAction(SimplePokerAI.AIResponse response) {
        switch (response.action) {
            case FOLD:
                state.playerStack += state.pot;
                state.pot = 0;
                state.round = Round.SHOWDOWN;
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
                int raiseAmount = response.raiseAmount;
                int totalBet = state.playerBet + raiseAmount;
                int raiseAmountActual = totalBet - state.opponentBet;
                state.opponentStack -= raiseAmountActual;
                state.opponentBet = totalBet;
                state.pot += raiseAmountActual;
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
            state.currentPlayer = state.dealer == Dealer.PLAYER ? CurrentPlayer.OPPONENT : CurrentPlayer.PLAYER;
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