package data.scripts.casino.cards.pokerShared;

import java.util.List;
import java.util.Random;

import data.scripts.casino.cards.Card;
import data.scripts.casino.cards.Deck;
import data.scripts.casino.cards.GameType;

public class MonteCarloUtility {
    
    private static final Card[] ALL_CARDS = new Card[52];
    
    static {
        Deck deck = new Deck(GameType.POKER);
        for (int i = 0; i < 52; i++) {
            ALL_CARDS[i] = deck.cards.get(i);
        }
    }
    
    public static Card[] getAllCards() {
        return ALL_CARDS;
    }
    
    public static int[] shuffleAvailableCards(Random random, boolean[] excluded) {
        int[] indices = new int[52];
        int validCount = 0;
        
        for (int i = 0; i < 52; i++) {
            if (!excluded[i]) {
                indices[validCount++] = i;
            }
        }
        
        for (int j = validCount - 1; j > 0; j--) {
            int swapIdx = random.nextInt(j + 1);
            int tmp = indices[j];
            indices[j] = indices[swapIdx];
            indices[swapIdx] = tmp;
        }
        
        return indices;
    }
    
    public static boolean[] createExclusionMask(List<Card> holeCards, List<Card> communityCards) {
        boolean[] excluded = new boolean[52];
        for (int i = 0; i < 52; i++) {
            excluded[i] = holeCards.contains(ALL_CARDS[i]) || communityCards.contains(ALL_CARDS[i]);
        }
        return excluded;
    }
    
    public static Card[] completeBoard(List<Card> communityCards, int[] shuffledIndices) {
        Card[] board = new Card[5];
        int existing = communityCards.size();
        
        for (int i = 0; i < existing; i++) {
            board[i] = communityCards.get(i);
        }
        
        for (int i = existing; i < 5; i++) {
            board[i] = ALL_CARDS[shuffledIndices[i - existing]];
        }
        
        return board;
    }
    
    public static int getOpponentCardOffset(int communityCardCount) {
        return 5 - communityCardCount;
    }
    
    public static boolean shouldEarlyTerminate(int wins, int ties, int sampleIndex) {
        if (sampleIndex < 50) return false;
        float equity = (wins + ties * 0.5f) / sampleIndex;
        return equity > 0.90f || equity < 0.10f;
    }
    
    public static PokerAICommon.MonteCarloResult createResult(int wins, int ties, int losses, int samples) {
        return new PokerAICommon.MonteCarloResult(wins, ties, losses, samples);
    }
}