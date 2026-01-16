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
        
        public static class AIResponse {
            public Action action;
            public int raiseAmount; 
            public AIResponse(Action a, int amt) { action=a; raiseAmount=amt; }
        }

        private final Personality personality;
        
        // Track player aggression patterns
        private int consecutiveRaises = 0;
        private int consecutiveFoldsByPlayer = 0;
        private int totalPlayerActions = 0;
        private int aggressiveActions = 0;
        private float playerAggressionLevel = 0.0f; // 0.0 to 1.0

        public SimplePokerAI() {
            Personality[] p = Personality.values();
            this.personality = p[random.nextInt(p.length)];
        }
        
        public AIResponse decide(List<PokerGameLogic.Card> holeCards, List<PokerGameLogic.Card> communityCards, 
                                int currentBetToCall, int potSize, int stackSize, int opponentStackSize) {
            
            // Calculate equity-based decisions instead of simple threshold checks
            float equity = calculateEquity(holeCards, communityCards, stackSize, opponentStackSize);
            
            // Consider pot odds
            float potOdds = (float)currentBetToCall / (potSize + currentBetToCall);
            
            // Adjust behavior based on player aggression
            float aggressionAdjustment = 1.0f;
            float playerAggression = getPlayerAggressionLevel();
            int consecutivePlayerRaises = getConsecutivePlayerRaises();
            
            // If player has been very aggressive (bluffing/raising frequently), adjust AI to call/raise more often
            if (playerAggression > 0.6f) { // Player is aggressive
                // If AI has decent equity, it should be more willing to call or raise
                if (equity > 40) { // If AI has reasonable hand strength
                    aggressionAdjustment = 1.3f; // Be more aggressive against player bluffs
                }
            } else if (consecutivePlayerRaises >= 2) { // Player raised multiple times in a row
                // This could indicate strength or bluffing - adjust based on AI's hand strength
                if (equity > 50) { // If AI has strong hand, fight back
                    aggressionAdjustment = 1.4f; // Call or raise more aggressively
                } else if (equity > 30) { // If marginal hand, might call to catch bluffs
                    aggressionAdjustment = 1.2f;
                }
            }
            
            // Personality adjustments
            float personalityModifier = 1.0f;
            if (personality == Personality.TIGHT) personalityModifier = 0.7f;
            else if (personality == Personality.AGGRESSIVE) personalityModifier = 1.3f;
            
            // Combine aggression adjustment with personality
            float aggressionModifier = personalityModifier * aggressionAdjustment;
            
            // Decision logic based on equity vs pot odds
            if (equity < potOdds * 0.7f) { // Much higher threshold to fold - be more willing to call
                if (currentBetToCall == 0) return new AIResponse(Action.CHECK, 0);
                // If already all-in, can't fold
                if (stackSize <= 0) return new AIResponse(Action.CALL, 0);
                return new AIResponse(Action.FOLD, 0);
            } else if (equity > potOdds * 1.2f) { // Raise if equity is significantly higher than pot odds
                int maxRaise = Math.max(0, stackSize - currentBetToCall);
                if (maxRaise > 0) {
                    // Calculate raise amount based on equity, pot size, and aggression
                    int raiseAmount = (int)(potSize * (equity - potOdds) * aggressionModifier);
                    raiseAmount = Math.max(CasinoConfig.POKER_AI_MIN_RAISE_VALUE, 
                                          Math.min(maxRaise, raiseAmount));
                    return new AIResponse(Action.RAISE, raiseAmount);
                } else {
                    return new AIResponse(Action.CALL, 0); // All-in
                }
            } else { // Equity close to pot odds - call
                if (currentBetToCall == 0) return new AIResponse(Action.CHECK, 0);
                // If player is aggressive and AI has decent equity, be more willing to call
                if (playerAggression > 0.6f && equity > 35) {
                    return new AIResponse(Action.CALL, 0);
                }
                return new AIResponse(Action.CALL, 0);
            }
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
                consecutiveRaises++;
                consecutiveFoldsByPlayer = 0; // Reset consecutive folds counter
            } else if (isFold) {
                consecutiveFoldsByPlayer++;
                consecutiveRaises = 0; // Reset consecutive raises counter
            } else {
                consecutiveRaises = 0; // Reset when player doesn't raise
                consecutiveFoldsByPlayer = 0; // Reset when player doesn't fold
            }
            
            // Calculate aggression level as percentage of aggressive actions
            if (totalPlayerActions > 0) {
                playerAggressionLevel = (float)aggressiveActions / totalPlayerActions;
            }
            
            // Cap the aggression level at 1.0
            playerAggressionLevel = Math.min(1.0f, playerAggressionLevel);
        }
        
        /**
         * Get the current player aggression level
         */
        public float getPlayerAggressionLevel() {
            return playerAggressionLevel;
        }
        
        /**
         * Get the number of consecutive raises by player
         */
        public int getConsecutivePlayerRaises() {
            return consecutiveRaises;
        }
    }
}