package data.scripts.casino.poker5;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import data.scripts.casino.cards.Card;
import data.scripts.casino.cards.Deck;
import data.scripts.casino.cards.GameType;

public class MultiPlayerEquityCalculator {
    private final Random random = new Random();
    
    private static final Card[] allCardsArray = new Card[52];
    
    static {
        Deck deck = new Deck(GameType.POKER);
        for (int i = 0; i < 52; i++) {
            allCardsArray[i] = deck.cards.get(i);
        }
    }

    public static class EquityResult {
        public int wins;
        public int ties;
        public int losses;
        public int samples;

        public EquityResult(int wins, int ties, int losses, int samples) {
            this.wins = wins;
            this.ties = ties;
            this.losses = losses;
            this.samples = samples;
        }

        public float getWinProbability() { return (float) wins / samples; }
        public float getTieProbability() { return (float) ties / samples; }
        public float getLossProbability() { return (float) losses / samples; }
        public float getTotalEquity() { return (wins + ties * 0.5f) / samples; }
    }

    public EquityResult calculateMultiWayEquity(List<Card> holeCards, List<Card> communityCards, int opponentCount) {
        int wins = 0;
        int ties = 0;
        int losses = 0;
        int samples = CasinoConfig.POKER_MONTE_CARLO_SAMPLES;

        int[] cardIndices = new int[52];
        boolean[] excluded = new boolean[52];

        for (int i = 0; i < 52; i++) {
            cardIndices[i] = i;
            excluded[i] = holeCards.contains(allCardsArray[i]) || communityCards.contains(allCardsArray[i]);
        }

        for (int i = 0; i < samples; i++) {
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

            PokerGame5.PokerGameLogic.HandScore ourScore = evaluateHandFast(holeCards, boardCards);

            boolean isWinner = true;
            boolean isTied = false;
            int oppCardOffset = 5 - boardStart;

            for (int opp = 0; opp < opponentCount; opp++) {
                Card opp1 = allCardsArray[cardIndices[oppCardOffset + opp * 2]];
                Card opp2 = allCardsArray[cardIndices[oppCardOffset + opp * 2 + 1]];
                PokerGame5.PokerGameLogic.HandScore oppScore = evaluateTwoCardsFast(opp1, opp2, boardCards);

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
            else if (isWinner && isTied) ties++;
            else losses++;

            if (i == 49) {
                float currentEquity = (wins + ties * 0.5f) / (i + 1);
                if (currentEquity > 0.90f || currentEquity < 0.10f) {
                    return new EquityResult(wins, ties, losses, i + 1);
                }
            }
        }

        return new EquityResult(wins, ties, losses, samples);
    }

    public float calculatePreflopEquity(List<Card> holeCards, int opponentCount) {
        return calculateMultiWayEquity(holeCards, new ArrayList<>(), opponentCount).getTotalEquity();
    }

    private PokerGame5.PokerGameLogic.HandScore evaluateHandFast(List<Card> holeCards, Card[] board) {
        Card[] allCards = new Card[7];
        allCards[0] = holeCards.get(0);
        allCards[1] = holeCards.get(1);
        System.arraycopy(board, 0, allCards, 2, 5);
        return analyzeHandFast(allCards);
    }

    private PokerGame5.PokerGameLogic.HandScore evaluateTwoCardsFast(Card c1, Card c2, Card[] board) {
        Card[] allCards = new Card[7];
        allCards[0] = c1;
        allCards[1] = c2;
        System.arraycopy(board, 0, allCards, 2, 5);
        return analyzeHandFast(allCards);
    }

    private PokerGame5.PokerGameLogic.HandScore analyzeHandFast(Card[] sevenCards) {
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
            return new PokerGame5.PokerGameLogic.HandScore(PokerGame5.PokerGameLogic.HandRank.FOUR_OF_A_KIND,
                new ArrayList<>(List.of(fourRank, kicker)));
        }

        if (hasThree && hasPair) {
            return new PokerGame5.PokerGameLogic.HandScore(PokerGame5.PokerGameLogic.HandRank.FULL_HOUSE,
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
                return new PokerGame5.PokerGameLogic.HandScore(PokerGame5.PokerGameLogic.HandRank.STRAIGHT_FLUSH,
                    new ArrayList<>(List.of(flushStraightHigh)));
            }

            List<Integer> flushTie = new ArrayList<>();
            for (int i = 0; i < 5 && i < flushCount; i++) flushTie.add(flushRankVals[i]);
            return new PokerGame5.PokerGameLogic.HandScore(PokerGame5.PokerGameLogic.HandRank.FLUSH, flushTie);
        }

        if (straightHigh >= 0) {
            return new PokerGame5.PokerGameLogic.HandScore(PokerGame5.PokerGameLogic.HandRank.STRAIGHT,
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
            return new PokerGame5.PokerGameLogic.HandScore(PokerGame5.PokerGameLogic.HandRank.THREE_OF_A_KIND, tie);
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
            return new PokerGame5.PokerGameLogic.HandScore(PokerGame5.PokerGameLogic.HandRank.TWO_PAIR, tie);
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
            return new PokerGame5.PokerGameLogic.HandScore(PokerGame5.PokerGameLogic.HandRank.PAIR, tie);
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
        return new PokerGame5.PokerGameLogic.HandScore(PokerGame5.PokerGameLogic.HandRank.HIGH_CARD, tie);
    }
}