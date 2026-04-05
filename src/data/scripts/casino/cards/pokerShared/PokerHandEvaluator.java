package data.scripts.casino.cards.pokerShared;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import data.scripts.casino.cards.Card;
import data.scripts.casino.cards.GameType;

public class PokerHandEvaluator {

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

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof HandScore)) return false;
            return compareTo((HandScore) o) == 0;
        }
    }

    public static class PlayerScore {
        public int playerIndex;
        public HandScore score;

        public PlayerScore(int idx, HandScore s) {
            playerIndex = idx;
            score = s;
        }
    }

    public static HandScore evaluate(List<Card> holeCards, List<Card> communityCards) {
        List<Card> all = new ArrayList<>(holeCards);
        all.addAll(communityCards);
        if (all.size() < 5) return new HandScore(HandRank.HIGH_CARD, new ArrayList<>());
        all.sort((o1, o2) -> Integer.compare(o2.rank().getValue(GameType.POKER), o1.rank().getValue(GameType.POKER)));
        return analyzeHand(all);
    }

    private static HandScore analyzeHand(List<Card> cards) {
        int[] suitCounts = new int[4];
        int[] rankCounts = new int[15];
        int[][] suitRanks = new int[4][cards.size()];
        int[] suitRankCounts = new int[4];
        int[] sortedRanks = new int[cards.size()];

        for (int i = 0; i < cards.size(); i++) {
            Card c = cards.get(i);
            int suitIdx = c.suit().ordinal();
            int rankVal = c.rank().getValue(GameType.POKER);
            suitCounts[suitIdx]++;
            rankCounts[rankVal]++;
            suitRanks[suitIdx][suitRankCounts[suitIdx]] = rankVal;
            suitRankCounts[suitIdx]++;
            sortedRanks[i] = rankVal;
        }

        Arrays.sort(sortedRanks);
        for (int i = 0; i < sortedRanks.length / 2; i++) {
            int tmp = sortedRanks[i];
            sortedRanks[i] = sortedRanks[sortedRanks.length - 1 - i];
            sortedRanks[sortedRanks.length - 1 - i] = tmp;
        }

        int flushSuit = -1;
        for (int s = 0; s < 4; s++) {
            if (suitCounts[s] >= 5) {
                flushSuit = s;
                break;
            }
        }

        boolean[] rankPresent = new boolean[15];
        for (int r : sortedRanks) rankPresent[r] = true;
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
            Arrays.sort(flushRankVals, 0, flushCount);
            for (int i = 0; i < flushCount / 2; i++) {
                int tmp = flushRankVals[i];
                flushRankVals[i] = flushRankVals[flushCount - 1 - i];
                flushRankVals[flushCount - 1 - i] = tmp;
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

    public static HandScore evaluateHandFast(List<Card> holeCards, Card[] board) {
        Card[] allCards = new Card[7];
        allCards[0] = holeCards.get(0);
        allCards[1] = holeCards.get(1);
        System.arraycopy(board, 0, allCards, 2, 5);
        return analyzeHandFast(allCards);
    }

    public static HandScore evaluateTwoCardsFast(Card c1, Card c2, Card[] board) {
        Card[] allCards = new Card[7];
        allCards[0] = c1;
        allCards[1] = c2;
        System.arraycopy(board, 0, allCards, 2, 5);
        return analyzeHandFast(allCards);
    }

    public static HandScore analyzeHandFast(Card[] sevenCards) {
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
            return new HandScore(HandRank.FOUR_OF_A_KIND, new ArrayList<>(List.of(fourRank, kicker)));
        }

        if (hasThree && hasPair) {
            return new HandScore(HandRank.FULL_HOUSE, new ArrayList<>(List.of(threeRank, pairRank)));
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
                return new HandScore(HandRank.STRAIGHT_FLUSH, new ArrayList<>(List.of(flushStraightHigh)));
            }

            List<Integer> flushTie = new ArrayList<>();
            for (int i = 0; i < 5 && i < flushCount; i++) flushTie.add(flushRankVals[i]);
            return new HandScore(HandRank.FLUSH, flushTie);
        }

        if (straightHigh >= 0) {
            return new HandScore(HandRank.STRAIGHT, new ArrayList<>(List.of(straightHigh)));
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
            return new HandScore(HandRank.THREE_OF_A_KIND, tie);
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
            return new HandScore(HandRank.TWO_PAIR, tie);
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
            return new HandScore(HandRank.PAIR, tie);
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
        return new HandScore(HandRank.HIGH_CARD, tie);
    }
}
