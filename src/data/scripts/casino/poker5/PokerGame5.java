package data.scripts.casino.poker5;

import java.util.*;

import data.scripts.casino.cards.Card;
import data.scripts.casino.cards.Deck;
import data.scripts.casino.cards.GameType;
import data.scripts.casino.cards.Rank;
import data.scripts.casino.cards.Suit;

public class PokerGame5 {

    public static final int NUM_PLAYERS = 5;
    public static final int HUMAN_PLAYER_INDEX = 0;

    public enum Action { FOLD, CHECK, CALL, RAISE, ALL_IN }

    public enum Round { PREFLOP, FLOP, TURN, RIVER, SHOWDOWN }

    public static class SidePot {
        public int amount;
        public Set<Integer> eligiblePlayers;
        public int capPerPlayer;

        public SidePot(int amount, Set<Integer> eligible, int cap) {
            this.amount = amount;
            this.eligiblePlayers = new HashSet<>(eligible);
            this.capPerPlayer = cap;
        }
    }

    public static class PokerState5 {
        public List<Card>[] hands;
        public List<Card> communityCards;
        public int[] stacks;
        public int[] bets;
        public int[] displayBets;
        public int[] totalContributions;
        public boolean[] hasActed;
        public boolean[] declaredAllIn;
        public Set<Integer> foldedPlayers;
        public Set<Integer> activePlayers;
        public List<SidePot> sidePots;
        public int pot;
        public int mainPotCap;
        public int buttonPosition;
        public Round round;
        public int currentPlayerIndex;
        public int lastRaiseAmount;
        public int lastRaisePlayerIndex;
        public int lastPotWon;
        public int bigBlind;
        public PokerGameLogic.HandRank[] handRanks;
        public int[] winners;

        @SuppressWarnings("unchecked")
        public PokerState5() {
            hands = (List<Card>[]) new List[NUM_PLAYERS];
            for (int i = 0; i < NUM_PLAYERS; i++) {
                hands[i] = new ArrayList<>();
            }
            communityCards = new ArrayList<>();
            stacks = new int[NUM_PLAYERS];
            bets = new int[NUM_PLAYERS];
            displayBets = new int[NUM_PLAYERS];
            totalContributions = new int[NUM_PLAYERS];
            hasActed = new boolean[NUM_PLAYERS];
            declaredAllIn = new boolean[NUM_PLAYERS];
            foldedPlayers = new HashSet<>();
            activePlayers = new HashSet<>();
            sidePots = new ArrayList<>();
            handRanks = new PokerGameLogic.HandRank[NUM_PLAYERS];
            winners = new int[0];
        }
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
    }

    private final PokerState5 state;
    private final PokerAI5[] aiPlayers;
    private Deck deck;
    private int bigBlindAmount;

    public PokerGame5(int[] startingStacks) {
        this(startingStacks, false);
    }

    @SuppressWarnings("unchecked")
    PokerGame5(int[] startingStacks, boolean suspendedGameMarker) {
        state = new PokerState5();
        aiPlayers = new PokerAI5[NUM_PLAYERS];
        aiPlayers[HUMAN_PLAYER_INDEX] = null;

        for (int i = 1; i < NUM_PLAYERS; i++) {
            aiPlayers[i] = new MultiPlayerPokerOpponentAI(i);
        }

        int avgStack = 0;
        for (int s : startingStacks) avgStack += s;
        avgStack /= NUM_PLAYERS;
        this.bigBlindAmount = calculateBigBlind(avgStack);

        for (int i = 0; i < NUM_PLAYERS; i++) {
            state.stacks[i] = startingStacks[i];
        }
        state.bigBlind = bigBlindAmount;
        state.buttonPosition = NUM_PLAYERS - 1;

        if (!suspendedGameMarker) {
            startNewHand();
        }
    }

    private static int calculateBigBlind(int avgStack) {
        int calculatedBB = avgStack / 100;
        calculatedBB = Math.max(10, calculatedBB);
        return ((calculatedBB + 5) / 10) * 10;
    }

    public PokerState5 getState() {
        return state;
    }

    public PokerAI5 getAI(int playerIndex) {
        if (playerIndex < 0 || playerIndex >= NUM_PLAYERS) return null;
        return aiPlayers[playerIndex];
    }

    public int getButtonPosition() {
        return state.buttonPosition;
    }

    public int getSBPosition() {
        return (state.buttonPosition + 1) % NUM_PLAYERS;
    }

    public int getBBPosition() {
        return (state.buttonPosition + 2) % NUM_PLAYERS;
    }

    public int getPlayerPosition(int playerIndex) {
        return (playerIndex - state.buttonPosition + NUM_PLAYERS) % NUM_PLAYERS;
    }

    public String getPositionName(int playerIndex) {
        int pos = getPlayerPosition(playerIndex);
        return switch (pos) {
            case 0 -> "BTN";
            case 1 -> "SB";
            case 2 -> "BB";
            case 3 -> "UTG";
            case 4 -> "UTG+1";
            default -> "P" + pos;
        };
    }

    public int getSmallBlindAmount() {
        return bigBlindAmount / 2;
    }

    public int getBigBlindAmount() {
        return bigBlindAmount;
    }

    public void startNewHand() {
        deck = new Deck(GameType.POKER);
        deck.shuffle();

        for (int i = 0; i < NUM_PLAYERS; i++) {
            state.hands[i] = new ArrayList<>();
            state.hands[i].add(deck.draw());
            state.hands[i].add(deck.draw());
        }

        state.communityCards = new ArrayList<>();
        Arrays.fill(state.bets, 0);
        Arrays.fill(state.displayBets, 0);
        Arrays.fill(state.totalContributions, 0);
        Arrays.fill(state.hasActed, false);
        Arrays.fill(state.declaredAllIn, false);
        Arrays.fill(state.handRanks, null);
        state.foldedPlayers.clear();
        state.activePlayers.clear();
        for (int i = 0; i < NUM_PLAYERS; i++) {
            if (state.stacks[i] > 0) {
                state.activePlayers.add(i);
            }
        }
        state.sidePots.clear();
        state.pot = 0;
        state.mainPotCap = Integer.MAX_VALUE;
        state.lastPotWon = 0;
        state.winners = new int[0];
        state.lastRaiseAmount = 0;
        state.lastRaisePlayerIndex = -1;

        state.buttonPosition = (state.buttonPosition + 1) % NUM_PLAYERS;

        postBlinds();

        state.round = Round.PREFLOP;
        state.currentPlayerIndex = getFirstToAct();

        evaluateHands();

        for (int i = 1; i < NUM_PLAYERS; i++) {
            if (aiPlayers[i] != null) {
                aiPlayers[i].newHandStarted(i, state);
            }
        }
    }

    private void postBlinds() {
        int sbPos = getSBPosition();
        int bbPos = getBBPosition();

        int sbAmount = Math.min(bigBlindAmount / 2, state.stacks[sbPos]);
        state.bets[sbPos] = sbAmount;
        state.displayBets[sbPos] = sbAmount;
        state.stacks[sbPos] -= sbAmount;
        state.totalContributions[sbPos] += sbAmount;

        int bbAmount = Math.min(bigBlindAmount, state.stacks[bbPos]);
        state.bets[bbPos] = bbAmount;
        state.displayBets[bbPos] = bbAmount;
        state.stacks[bbPos] -= bbAmount;
        state.totalContributions[bbPos] += bbAmount;

        state.pot = sbAmount + bbAmount;
        state.lastRaiseAmount = bigBlindAmount;
        state.lastRaisePlayerIndex = bbPos;

        if (sbAmount >= state.stacks[sbPos] + sbAmount) {
            state.declaredAllIn[sbPos] = true;
        }
        if (bbAmount >= state.stacks[bbPos] + bbAmount) {
            state.declaredAllIn[bbPos] = true;
        }
    }

    public int getFirstToAct() {
        if (state.round == Round.PREFLOP) {
            return getNextActivePlayer((state.buttonPosition + 2) % NUM_PLAYERS);
        } else {
            return getNextActivePlayer(state.buttonPosition);
        }
    }

    public int getNextActivePlayer(int from) {
        for (int i = 1; i <= NUM_PLAYERS; i++) {
            int next = (from + i) % NUM_PLAYERS;
            if (canAct(next)) {
                return next;
            }
        }
        return -1;
    }

    public boolean canAct(int playerIndex) {
        return !state.foldedPlayers.contains(playerIndex) &&
               !state.declaredAllIn[playerIndex] &&
               state.stacks[playerIndex] > 0 &&
               state.activePlayers.contains(playerIndex);
    }

    public boolean isActive(int playerIndex) {
        return !state.foldedPlayers.contains(playerIndex) &&
               state.activePlayers.contains(playerIndex);
    }

    public int getCurrentBet() {
        int maxBet = 0;
        for (int i = 0; i < NUM_PLAYERS; i++) {
            if (!state.foldedPlayers.contains(i) && state.bets[i] > maxBet) {
                maxBet = state.bets[i];
            }
        }
        return maxBet;
    }

    public int getCallAmount(int playerIndex) {
        return getCurrentBet() - state.bets[playerIndex];
    }

    public int getMinRaiseAmount(int playerIndex) {
        int currentBet = getCurrentBet();
        int callAmount = currentBet - state.bets[playerIndex];
        int minRaiseIncrement = Math.max(state.lastRaiseAmount, bigBlindAmount);
        return currentBet + minRaiseIncrement;
    }

    public void processAction(int playerIndex, Action action, int raiseAmount) {
        if (playerIndex < 0 || playerIndex >= NUM_PLAYERS) return;
        if (state.currentPlayerIndex != playerIndex && state.round != Round.SHOWDOWN) return;
        if (state.foldedPlayers.contains(playerIndex)) return;

        switch (action) {
            case FOLD -> processFold(playerIndex);
            case CHECK -> processCheck(playerIndex);
            case CALL -> processCall(playerIndex);
            case RAISE -> processRaise(playerIndex, raiseAmount);
            case ALL_IN -> processAllIn(playerIndex);
        }

        state.hasActed[playerIndex] = true;

        if (state.round != Round.SHOWDOWN) {
            advanceTurn();
        }
    }

    private void processFold(int playerIndex) {
        state.foldedPlayers.add(playerIndex);
        state.activePlayers.remove(playerIndex);
    }

    private void processCheck(int playerIndex) {
    }

    private void processCall(int playerIndex) {
        int callAmount = getCallAmount(playerIndex);
        int cappedCall = Math.min(callAmount, state.stacks[playerIndex]);

        state.stacks[playerIndex] -= cappedCall;
        state.bets[playerIndex] += cappedCall;
        state.displayBets[playerIndex] = state.bets[playerIndex];
        state.totalContributions[playerIndex] += cappedCall;
        state.pot += cappedCall;

        if (state.stacks[playerIndex] <= 0) {
            state.declaredAllIn[playerIndex] = true;
        }

        calculateSidePots();
    }

    private void processRaise(int playerIndex, int totalBetAmount) {
        int currentBet = getCurrentBet();
        int callAmount = currentBet - state.bets[playerIndex];
        int raiseIncrement = totalBetAmount - currentBet;
        int actualAmount = Math.min(totalBetAmount - state.bets[playerIndex], state.stacks[playerIndex]);

        state.stacks[playerIndex] -= actualAmount;
        state.bets[playerIndex] += actualAmount;
        state.displayBets[playerIndex] = state.bets[playerIndex];
        state.totalContributions[playerIndex] += actualAmount;
        state.pot += actualAmount;

        state.lastRaiseAmount = raiseIncrement;
        state.lastRaisePlayerIndex = playerIndex;

        if (state.stacks[playerIndex] <= 0) {
            state.declaredAllIn[playerIndex] = true;
        }

        for (int i = 0; i < NUM_PLAYERS; i++) {
            if (i != playerIndex && canAct(i)) {
                state.hasActed[i] = false;
            }
        }

        calculateSidePots();
    }

    private void processAllIn(int playerIndex) {
        int allInAmount = state.stacks[playerIndex];
        int currentBet = getCurrentBet();

        state.stacks[playerIndex] = 0;
        state.bets[playerIndex] += allInAmount;
        state.displayBets[playerIndex] = state.bets[playerIndex];
        state.totalContributions[playerIndex] += allInAmount;
        state.pot += allInAmount;
        state.declaredAllIn[playerIndex] = true;

        if (state.bets[playerIndex] > currentBet) {
            state.lastRaiseAmount = state.bets[playerIndex] - currentBet;
            state.lastRaisePlayerIndex = playerIndex;

            for (int i = 0; i < NUM_PLAYERS; i++) {
                if (i != playerIndex && canAct(i)) {
                    state.hasActed[i] = false;
                }
            }
        }

        calculateSidePots();
    }

    private void calculateSidePots() {
        List<Integer> activeNotFolded = new ArrayList<>();
        for (int i = 0; i < NUM_PLAYERS; i++) {
            if (!state.foldedPlayers.contains(i)) {
                activeNotFolded.add(i);
            }
        }

        if (activeNotFolded.isEmpty()) return;

        activeNotFolded.sort((a, b) -> Integer.compare(state.totalContributions[a], state.totalContributions[b]));

        state.sidePots.clear();
        int prevContribution = 0;

        for (int idx = 0; idx < activeNotFolded.size(); idx++) {
            int playerIdx = activeNotFolded.get(idx);
            int currentContribution = state.totalContributions[playerIdx];

            if (currentContribution > prevContribution) {
                int potAmount = 0;
                Set<Integer> eligible = new HashSet<>();

                for (int j = idx; j < activeNotFolded.size(); j++) {
                    int eligiblePlayer = activeNotFolded.get(j);
                    eligible.add(eligiblePlayer);
                    potAmount += currentContribution - prevContribution;
                }

                for (int j = 0; j < idx; j++) {
                    int cappedPlayer = activeNotFolded.get(j);
                    potAmount += Math.min(currentContribution - prevContribution,
                                          state.totalContributions[cappedPlayer] - prevContribution);
                }

                if (potAmount > 0) {
                    state.sidePots.add(new SidePot(potAmount, eligible, currentContribution));
                }

                prevContribution = currentContribution;
            }
        }
    }

    private void advanceTurn() {
        if (checkBettingRoundComplete()) {
            advanceRound();
        } else {
            int nextPlayer = getNextActivePlayer(state.currentPlayerIndex);
            if (nextPlayer == -1) {
                advanceRound();
            } else {
                state.currentPlayerIndex = nextPlayer;
            }
        }
    }

    private boolean checkBettingRoundComplete() {
        int targetBet = -1;
        int activeCount = 0;

        for (int i = 0; i < NUM_PLAYERS; i++) {
            if (state.foldedPlayers.contains(i)) continue;

            if (canAct(i)) {
                if (!state.hasActed[i]) return false;
                activeCount++;
            }

            if (targetBet == -1) {
                targetBet = state.bets[i];
            } else if (state.bets[i] != targetBet && canAct(i)) {
                return false;
            }
        }

        if (activeCount <= 1) return true;

        for (int i = 0; i < NUM_PLAYERS; i++) {
            if (!state.foldedPlayers.contains(i) && canAct(i)) {
                if (state.bets[i] != targetBet) return false;
            }
        }

        return true;
    }

    private void advanceRound() {
        Arrays.fill(state.bets, 0);
        Arrays.fill(state.displayBets, 0);
        Arrays.fill(state.hasActed, false);

        for (int i = 0; i < NUM_PLAYERS; i++) {
            if (!state.declaredAllIn[i] && state.stacks[i] > 0) {
                state.hasActed[i] = false;
            }
        }

        switch (state.round) {
            case PREFLOP -> {
                state.round = Round.FLOP;
                state.communityCards.add(deck.draw());
                state.communityCards.add(deck.draw());
                state.communityCards.add(deck.draw());
            }
            case FLOP -> {
                state.round = Round.TURN;
                state.communityCards.add(deck.draw());
            }
            case TURN -> {
                state.round = Round.RIVER;
                state.communityCards.add(deck.draw());
            }
            case RIVER -> {
                state.round = Round.SHOWDOWN;
            }
            case SHOWDOWN -> { return; }
        }

        evaluateHands();

        if (state.round != Round.SHOWDOWN) {
            int firstToAct = getFirstToAct();
            if (firstToAct == -1) {
                while (state.round != Round.SHOWDOWN) {
                    advanceRound();
                }
            } else {
                state.currentPlayerIndex = firstToAct;
            }
        }
    }

    public void evaluateHands() {
        if (state.communityCards.size() >= 3) {
            for (int i = 0; i < NUM_PLAYERS; i++) {
                if (!state.foldedPlayers.contains(i) && state.hands[i] != null) {
                    PokerGameLogic.HandScore score = PokerGameLogic.evaluate(state.hands[i], state.communityCards);
                    state.handRanks[i] = score.rank;
                }
            }
        } else {
            Arrays.fill(state.handRanks, null);
        }
    }

    public void calculateSidePotsFromState() {
        state.sidePots.clear();
        calculateSidePots();
    }

    public PokerAI5.AIResponse getAIAction(int playerIndex) {
        if (playerIndex < 0 || playerIndex >= NUM_PLAYERS) return null;
        if (aiPlayers[playerIndex] == null) return null;
        if (state.foldedPlayers.contains(playerIndex)) return null;

        return aiPlayers[playerIndex].decideAction(playerIndex, state);
    }

    public void processAIAction(int playerIndex, PokerAI5.AIResponse response) {
        processAction(playerIndex, response.action, response.raiseAmount);
    }

    public int[] determineWinners() {
        if (state.round != Round.SHOWDOWN) return new int[0];

        List<PokerGameLogic.PlayerScore> scores = new ArrayList<>();
        for (int i = 0; i < NUM_PLAYERS; i++) {
            if (!state.foldedPlayers.contains(i) && state.hands[i] != null && !state.hands[i].isEmpty()) {
                PokerGameLogic.HandScore score = PokerGameLogic.evaluate(state.hands[i], state.communityCards);
                scores.add(new PokerGameLogic.PlayerScore(i, score));
            }
        }

        if (scores.isEmpty()) return new int[0];

        scores.sort((a, b) -> b.score.compareTo(a.score));

        int[] allWinners = new int[0];
        Map<Integer, Integer> winnings = new HashMap<>();

        for (SidePot pot : state.sidePots) {
            List<PokerGameLogic.PlayerScore> eligibleScores = new ArrayList<>();
            for (PokerGameLogic.PlayerScore ps : scores) {
                if (pot.eligiblePlayers.contains(ps.playerIndex)) {
                    eligibleScores.add(ps);
                }
            }

            if (eligibleScores.isEmpty()) continue;

            eligibleScores.sort((a, b) -> b.score.compareTo(a.score));

            List<Integer> potWinners = new ArrayList<>();
            PokerGameLogic.HandScore bestScore = eligibleScores.get(0).score;
            for (PokerGameLogic.PlayerScore ps : eligibleScores) {
                if (ps.score.equals(bestScore)) {
                    potWinners.add(ps.playerIndex);
                }
            }

            int share = pot.amount / potWinners.size();
            int remainder = pot.amount % potWinners.size();

            for (int w : potWinners) {
                winnings.merge(w, share, Integer::sum);
            }
            winnings.merge(potWinners.get(0), remainder, Integer::sum);
        }

        for (Map.Entry<Integer, Integer> entry : winnings.entrySet()) {
            state.stacks[entry.getKey()] += entry.getValue();
        }

        state.winners = winnings.keySet().stream().mapToInt(Integer::intValue).toArray();
        state.lastPotWon = state.sidePots.stream().mapToInt(p -> p.amount).sum();
        state.pot = 0;
        state.sidePots.clear();

        return state.winners;
    }

    public boolean isHandComplete() {
        return state.round == Round.SHOWDOWN && state.winners.length > 0;
    }

    public int countActivePlayers() {
        int count = 0;
        for (int i = 0; i < NUM_PLAYERS; i++) {
            if (!state.foldedPlayers.contains(i)) count++;
        }
        return count;
    }

    public int countCanActPlayers() {
        int count = 0;
        for (int i = 0; i < NUM_PLAYERS; i++) {
            if (canAct(i)) count++;
        }
        return count;
    }

    public static String cardToString(Card card) {
        if (card == null) return "";
        return card.value() + "-" + card.suit().name();
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
}