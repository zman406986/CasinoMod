package data.scripts.casino.util;

import data.scripts.casino.PokerGame;

import java.util.List;

/**
 * Utility class for poker game logic and calculations
 */
public class PokerLogicUtil {
    
    /**
     * Evaluates the strength of a poker hand given hole cards and community cards
     * @param holeCards The player's hole cards (typically 2 cards)
     * @param communityCards The community cards on the board
     * @return The evaluated hand score
     */
    public static PokerGame.PokerGameLogic.HandScore evaluateHand(List<PokerGame.PokerGameLogic.Card> holeCards, List<PokerGame.PokerGameLogic.Card> communityCards) {
        return PokerGame.PokerGameLogic.evaluate(holeCards, communityCards);
    }
    
    /**
     * Calculates the equity of a hand pre-flop
     * @param holeCards The player's hole cards
     * @return Estimated equity (0.0 to 1.0)
     */
    public static float calculatePreflopEquity(List<PokerGame.PokerGameLogic.Card> holeCards) {
        // Simplified pre-flop equity calculation based on hand strength
        PokerGame.PokerGameLogic.Card c1 = holeCards.get(0);
        PokerGame.PokerGameLogic.Card c2 = holeCards.get(1);
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
    
    /**
     * Calculates the strength percentage of a hand score
     * @param hand The evaluated hand
     * @return Strength percentage (0-100)
     */
    public static float getHandStrengthPercentage(PokerGame.PokerGameLogic.HandScore hand) {
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
    
    /**
     * Checks if there's a flush draw possibility with the given cards
     * @param hole The hole cards
     * @param comm The community cards
     * @return True if there's a flush draw (4 cards of the same suit)
     */
    public static boolean hasFlushDraw(List<PokerGame.PokerGameLogic.Card> hole, List<PokerGame.PokerGameLogic.Card> comm) {
        java.util.Map<PokerGame.PokerGameLogic.Suit, Integer> counts = new java.util.HashMap<>();
        for (PokerGame.PokerGameLogic.Card c : hole) counts.put(c.suit, counts.getOrDefault(c.suit, 0) + 1);
        for (PokerGame.PokerGameLogic.Card c : comm) counts.put(c.suit, counts.getOrDefault(c.suit, 0) + 1);
        for (int val : counts.values()) if (val >= 4) return true;
        return false;
    }

    /**
     * Checks if there's a straight draw possibility with the given cards
     * @param hole The hole cards
     * @param comm The community cards
     * @return True if there's a straight draw possibility
     */
    public static boolean hasStraightDraw(List<PokerGame.PokerGameLogic.Card> hole, List<PokerGame.PokerGameLogic.Card> comm) {
        java.util.Set<Integer> ranks = new java.util.HashSet<>();
        for (PokerGame.PokerGameLogic.Card c : hole) ranks.add(c.rank.value);
        for (PokerGame.PokerGameLogic.Card c : comm) ranks.add(c.rank.value);
        if (ranks.contains(14)) ranks.add(1); 

        java.util.List<Integer> sorted = new java.util.ArrayList<>(ranks);
        java.util.Collections.sort(sorted);
        
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
}