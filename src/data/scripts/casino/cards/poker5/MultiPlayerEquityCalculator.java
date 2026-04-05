package data.scripts.casino.cards.poker5;

import java.util.ArrayList;
import java.util.List;

import data.scripts.casino.CasinoConfig;
import data.scripts.casino.cards.Card;
import data.scripts.casino.cards.pokerShared.MonteCarloUtility;
import data.scripts.casino.cards.pokerShared.PokerAICommon;
import data.scripts.casino.cards.pokerShared.PokerHandEvaluator;

public class MultiPlayerEquityCalculator {
    
    private static final Card[] ALL_CARDS = MonteCarloUtility.getAllCards();

    public PokerAICommon.MonteCarloResult calculateMultiWayEquity(List<Card> holeCards, List<Card> communityCards, int opponentCount) {
        int wins = 0;
        int ties = 0;
        int losses = 0;
        int samples = CasinoConfig.POKER_MONTE_CARLO_SAMPLES;

        boolean[] excluded = MonteCarloUtility.createExclusionMask(holeCards, communityCards);

        for (int i = 0; i < samples; i++) {
            int[] shuffledIndices = MonteCarloUtility.shuffleAvailableCards(
                new java.util.Random(), excluded);

            Card[] boardCards = MonteCarloUtility.completeBoard(communityCards, shuffledIndices);

            PokerHandEvaluator.HandScore ourScore = PokerHandEvaluator.evaluateHandFast(holeCards, boardCards);

            boolean isWinner = true;
            boolean isTied = false;
            int oppCardOffset = MonteCarloUtility.getOpponentCardOffset(communityCards.size());

            for (int opp = 0; opp < opponentCount; opp++) {
                Card opp1 = ALL_CARDS[shuffledIndices[oppCardOffset + opp * 2]];
                Card opp2 = ALL_CARDS[shuffledIndices[oppCardOffset + opp * 2 + 1]];
                PokerHandEvaluator.HandScore oppScore = PokerHandEvaluator.evaluateTwoCardsFast(opp1, opp2, boardCards);

                int cmp = ourScore.compareTo(oppScore);
                if (cmp < 0) {
                    isWinner = false;
                    isTied = false;
                    break;
                } else if (cmp == 0) {
                    isTied = true;
                }
            }

            if (isWinner && !isTied) wins++;
            else if (isWinner) ties++;
            else losses++;

            if (MonteCarloUtility.shouldEarlyTerminate(wins, ties, i + 1)) {
                return MonteCarloUtility.createResult(wins, ties, losses, i + 1);
            }
        }

        return MonteCarloUtility.createResult(wins, ties, losses, samples);
    }

    public float calculatePreflopEquity(List<Card> holeCards, int opponentCount) {
        return calculateMultiWayEquity(holeCards, new ArrayList<>(), opponentCount).getTotalEquity();
    }
}