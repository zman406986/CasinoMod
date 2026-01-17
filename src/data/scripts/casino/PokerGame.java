package data.scripts.casino;

import java.util.*;

public class PokerGame {

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
            private final List<Card> cards = new ArrayList<>();
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

        private final Personality personality;
        
        // Core AI state variables
        private float aggressionMeter = 0.5f;                    // 0.0 (passive) to 1.0 (aggressive)
        private float[] aggressionHistory = new float[10];      // Circular buffer for smoothing
        private int historyIndex = 0;                            // Index for circular buffer
        private int playerStyle = 0;                             // 0: unknown, 1: passive, 2: balanced, 3: aggressive
        private int handCount = 0;                               // Total hands played this session
        private boolean sawFlop = false;                         // Track if flop was reached
        
        // Track player actions
        private int totalPlayerActions = 0;
        private int aggressiveActions = 0;                       // Number of raises/bets by player

        public SimplePokerAI() {
            Personality[] p = Personality.values();
            this.personality = p[random.nextInt(p.length)];
            // Initialize aggression history array
            for (int i = 0; i < aggressionHistory.length; i++) {
                aggressionHistory[i] = 0.5f;
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
        
        private int calculateBetSize(float equity, int potSize, float aggressionFactor, int stackSize) {
            // Value bet sizing
            if (equity > 0.65f) {
                int bet = (int)(potSize * (0.6f + (random.nextFloat() * 0.2f - 0.1f))); // Add small random variation
                return Math.min(bet, stackSize); // Don't bet more than we have
            }
            
            // Medium strength
            if (equity > 0.45f) {
                int bet = (int)(potSize * (0.45f + (random.nextFloat() * 0.1f - 0.05f))); // Add small random variation
                return Math.min(bet, stackSize);
            }
            
            // Bluff sizing
            int bet = (int)(potSize * (0.35f + (random.nextFloat() * 0.1f - 0.05f))); // Add small random variation
            return Math.min(bet, stackSize);
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
                                int currentBetToCall, int potSize, int stackSize, int opponentStackSize) {
            
            // Determine if this is pre-flop or post-flop
            if (communityCards.isEmpty()) {
                return preFlopDecision(holeCards, currentBetToCall, potSize, stackSize);
            } else {
                return postFlopDecision(holeCards, communityCards, currentBetToCall, potSize, stackSize);
            }
        }
        
        private AIResponse preFlopDecision(List<PokerGameLogic.Card> holeCards, int currentBetToCall, int potSize, int stackSize) {
            // Calculate pre-flop equity
            float equity = calculatePreflopEquity(holeCards);
            
            // If player didn't raise (checked or limped), always continue
            if (currentBetToCall == 0) {
                // Player checked or limped - always continue
                if (random.nextFloat() < 0.3 && equity >= 0.5f) { // MEDIUM strength threshold
                    // Small raise sometimes
                    int raiseAmount = Math.min(stackSize / 20, stackSize - currentBetToCall);
                    raiseAmount = Math.max(CasinoConfig.POKER_AI_MIN_RAISE_VALUE, raiseAmount);
                    return new AIResponse(Action.RAISE, raiseAmount);
                } else {
                    return new AIResponse(Action.CALL, 0); // Call to see flop
                }
            } else {
                // Player bet/raised - use equity-based decision
                float potOdds = (float)currentBetToCall / (potSize + currentBetToCall);
                
                // Adjusted thresholds for heads-up play
                if (equity > 0.55) {
                    int raiseAmount = (int)(currentBetToCall * 2.5f);
                    raiseAmount = Math.min(stackSize - currentBetToCall, raiseAmount);
                    raiseAmount = Math.max(CasinoConfig.POKER_AI_MIN_RAISE_VALUE, raiseAmount);
                    return new AIResponse(Action.RAISE, raiseAmount);
                } else if (equity > 0.35) {
                    return new AIResponse(Action.CALL, 0);
                } else if (equity < 0.25 && random.nextFloat() > 0.15) {
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
            // Base decision on equity adjusted by aggression meter
            float aggressionFactor = aggressionMeter;
            
            // Call threshold: adjust based on opponent aggression
            float callThreshold = potOdds * (1.0f - aggressionFactor * 0.15f);
            
            // Raise threshold: higher vs passive players
            float raiseThreshold = potOdds + 0.2f + (aggressionFactor * 0.1f);
            
            // Bluff threshold: bluff more vs passive players
            float bluffThreshold = 0.25f - (aggressionFactor * 0.1f);
            
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
                int betSize = calculateBetSize(equity, potSize, aggressionFactor, stackSize);
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
        
        private float calculateEquity(List<PokerGameLogic.Card> holeCards, List<PokerGameLogic.Card> communityCards, int stackSize, int opponentStackSize) {
            // Basic equity calculation based on hand strength and draws
            if (communityCards.isEmpty()) { // Changed from communityCards.size() == 0
                // Pre-flop equity estimation
                return estimatePreFlopEquity(holeCards);
            } else {
                // Post-flop equity estimation
                float handStrength = getStrengthPercentage(PokerGameLogic.evaluate(holeCards, communityCards));
                
                // Adjust for draws
                boolean flushDraw = hasFlushDraw(holeCards, communityCards);
                boolean straightDraw = hasStraightDraw(holeCards, communityCards);
                
                if (communityCards.size() < 5) { // Not river yet
                    if (flushDraw) handStrength += 15;
                    if (straightDraw) handStrength += 10;
                }
                
                // Consider stack depth
                float stackFactor = 0.5f; // Base factor
                if (stackSize > 1000 || opponentStackSize > 1000) stackFactor = 0.7f; // Deeper stacks allow more drawing hands
                
                return Math.min(95f, handStrength * stackFactor);
            }
        }
        
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
        
        /**
         * Track player behavior for adaptation
         */
        public void trackPlayerAction(boolean isRaise, boolean isFold) {
            totalPlayerActions++;
            
            if (isRaise) {
                aggressiveActions++;
            }
            
            // Update aggression meter based on player actions
            updateAggressionMeter(isRaise);
            
            // Classify player style after sufficient hands
            if (totalPlayerActions > 5) {
                if (aggressionMeter < 0.3f) {
                    playerStyle = 1; // PASSIVE
                } else if (aggressionMeter > 0.7f) {
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
        
        /**
         * Get the current player aggression level
         */
        public float getPlayerAggressionLevel() {
            return aggressionMeter;
        }
        
        /**
         * Get the number of consecutive raises by player
         */
        public int getConsecutivePlayerRaises() {
            // This method is not directly used in the new implementation
            // but keeping for compatibility
            return 0;
        }
    }
}