package data.scripts.casino.cards.poker5;

import java.util.*;

import data.scripts.casino.cards.Card;
import data.scripts.casino.cards.Deck;
import data.scripts.casino.cards.GameType;
import data.scripts.casino.cards.pokerShared.PokerAction;
import data.scripts.casino.cards.pokerShared.PokerHandEvaluator;
import data.scripts.casino.cards.pokerShared.PokerRound;
import data.scripts.casino.cards.pokerShared.PokerUtils;

public class PokerGame5 {

    public static final int NUM_PLAYERS = 5;
    public static final int HUMAN_PLAYER_INDEX = 0;

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
        public PokerRound round;
        public int currentPlayerIndex;
        public int lastRaiseAmount;
        public int lastRaisePlayerIndex;
        public int lastPotWon;
        public int bigBlind;
        public PokerHandEvaluator.HandRank[] handRanks;
        public int[] winners;
        public String[] lastPokerActions;

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
            handRanks = new PokerHandEvaluator.HandRank[NUM_PLAYERS];
            winners = new int[0];
            lastPokerActions = new String[NUM_PLAYERS];
        }
    }

    private final PokerState5 state;
    private final PokerAI5[] aiPlayers;
    private Deck deck;
    private final int bigBlindAmount;

    public PokerGame5(int[] startingStacks) {
        this(startingStacks, false);
    }

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
        this.bigBlindAmount = PokerUtils.calculateBigBlind(avgStack);

        System.arraycopy(startingStacks, 0, state.stacks, 0, NUM_PLAYERS);
        state.bigBlind = bigBlindAmount;
        state.buttonPosition = NUM_PLAYERS - 1;

        if (!suspendedGameMarker) {
            startNewHand();
        }
    }

    public PokerState5 getState() {
        return state;
    }

    public PokerAI5 getAI(int playerIndex) {
        return aiPlayers[playerIndex];
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

    public int getBigBlindAmount() {
        return bigBlindAmount;
    }

    public void startNewHand() {
        deck = new Deck(GameType.POKER);
        deck.shuffle();

        for (int i = 0; i < NUM_PLAYERS; i++) {
            List<Card> hand = new ArrayList<>(2);
            hand.add(deck.draw());
            hand.add(deck.draw());
            state.hands[i] = hand;
        }

        state.communityCards = new ArrayList<>();
        Arrays.fill(state.bets, 0);
        Arrays.fill(state.displayBets, 0);
        Arrays.fill(state.totalContributions, 0);
        Arrays.fill(state.hasActed, false);
        Arrays.fill(state.declaredAllIn, false);
        Arrays.fill(state.handRanks, null);
        Arrays.fill(state.lastPokerActions, null);
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

        state.round = PokerRound.PREFLOP;
        state.currentPlayerIndex = getFirstToAct();

        evaluateHands();

        for (int i = 1; i < NUM_PLAYERS; i++) {
            aiPlayers[i].newHandStarted(i, state);
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

        if (state.stacks[sbPos] <= 0) {
            state.declaredAllIn[sbPos] = true;
        }
        if (state.stacks[bbPos] <= 0) {
            state.declaredAllIn[bbPos] = true;
        }
    }

    public int getFirstToAct() {
        if (state.round == PokerRound.PREFLOP) {
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

    public int getMinRaiseAmount() {
        int currentBet = getCurrentBet();
        int minRaiseIncrement = Math.max(state.lastRaiseAmount, bigBlindAmount);
        return currentBet + minRaiseIncrement;
    }

    public void processPokerAction(int playerIndex, PokerAction action, int raiseAmount) {
        if (state.currentPlayerIndex != playerIndex && state.round != PokerRound.SHOWDOWN) return;
        if (state.foldedPlayers.contains(playerIndex)) return;

        switch (action) {
            case FOLD -> processFold(playerIndex);
            case CHECK -> processCheck(playerIndex);
            case CALL -> processCall(playerIndex);
            case RAISE -> processRaise(playerIndex, raiseAmount);
            case ALL_IN -> processAllIn(playerIndex);
        }

        state.hasActed[playerIndex] = true;
        notifyAIPlayersOfPokerAction(playerIndex, action);

        if (state.round != PokerRound.SHOWDOWN) {
            advanceTurn();
        }
    }

    private void notifyAIPlayersOfPokerAction(int actingPlayer, PokerAction action) {
        String actionStr = action.name();
        for (int i = 1; i < NUM_PLAYERS; i++) {
            if (i != actingPlayer) {
                aiPlayers[i].recordAction(actingPlayer, actionStr);
            }
        }
    }

    private void processFold(int playerIndex) {
        state.foldedPlayers.add(playerIndex);
        state.activePlayers.remove(playerIndex);
    }

    @SuppressWarnings("unused")
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
        List<Integer> allContributors = new ArrayList<>();
        for (int i = 0; i < NUM_PLAYERS; i++) {
            if (state.totalContributions[i] > 0) {
                allContributors.add(i);
            }
        }

        if (allContributors.isEmpty()) return;

        allContributors.sort(Comparator.comparingInt(i -> state.totalContributions[i]));

        state.sidePots.clear();
        int prevContribution = 0;

        for (int idx = 0; idx < allContributors.size(); idx++) {
            int playerIdx = allContributors.get(idx);
            int currentContribution = state.totalContributions[playerIdx];

            if (currentContribution > prevContribution) {
                int potAmount = 0;
                Set<Integer> eligible = new HashSet<>();

                for (int j = idx; j < allContributors.size(); j++) {
                    int player = allContributors.get(j);
                    potAmount += currentContribution - prevContribution;
                    if (!state.foldedPlayers.contains(player)) {
                        eligible.add(player);
                    }
                }

                for (int j = 0; j < idx; j++) {
                    int cappedPlayer = allContributors.get(j);
                    potAmount += Math.min(currentContribution - prevContribution,
                                          state.totalContributions[cappedPlayer] - prevContribution);
                }

                if (potAmount > 0 && !eligible.isEmpty()) {
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
        int maxBetAmongActors = 0;
        int activeActorCount = 0;

        for (int i = 0; i < NUM_PLAYERS; i++) {
            if (state.foldedPlayers.contains(i)) continue;
            if (canAct(i)) {
                activeActorCount++;
                if (state.bets[i] > maxBetAmongActors) {
                    maxBetAmongActors = state.bets[i];
                }
            }
        }

        if (activeActorCount == 0) {
            return true;
        }

        for (int i = 0; i < NUM_PLAYERS; i++) {
            if (state.foldedPlayers.contains(i)) continue;
            if (canAct(i)) {
                if (state.bets[i] != maxBetAmongActors) {
                    return false;
                }
                if (!state.hasActed[i]) {
                    return false;
                }
            }
        }

        return true;
    }

    private void advanceRound() {
        Arrays.fill(state.bets, 0);
        Arrays.fill(state.displayBets, 0);
        Arrays.fill(state.hasActed, false);
        Arrays.fill(state.lastPokerActions, null);

        PokerRound fromRound = state.round;
        switch (state.round) {
            case PREFLOP -> state.round = PokerRound.FLOP;
            case FLOP -> state.round = PokerRound.TURN;
            case TURN -> state.round = PokerRound.RIVER;
            case RIVER -> state.round = PokerRound.SHOWDOWN;
            case SHOWDOWN -> { return; }
        }

        PokerUtils.dealCommunityCards(fromRound, deck, state.communityCards);

        evaluateHands();

        if (state.round != PokerRound.SHOWDOWN) {
            int firstToAct = getFirstToAct();
            if (firstToAct == -1) {
                while (state.round != PokerRound.SHOWDOWN) {
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
                    PokerHandEvaluator.HandScore score = PokerHandEvaluator.evaluate(state.hands[i], state.communityCards);
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

    public void processAIPokerAction(int playerIndex, PokerAI5.AIResponse response) {
        processPokerAction(playerIndex, response.action, response.raiseAmount);
    }

    public void determineWinners() {
        if (state.round != PokerRound.SHOWDOWN) return;

        List<PokerHandEvaluator.PlayerScore> scores = new ArrayList<>();
        for (int i = 0; i < NUM_PLAYERS; i++) {
            if (!state.foldedPlayers.contains(i) && state.hands[i] != null && !state.hands[i].isEmpty()) {
                PokerHandEvaluator.HandScore score = PokerHandEvaluator.evaluate(state.hands[i], state.communityCards);
                scores.add(new PokerHandEvaluator.PlayerScore(i, score));
            }
        }

        if (scores.isEmpty()) return;

        scores.sort((a, b) -> b.score.compareTo(a.score));

        Map<Integer, Integer> winnings = new HashMap<>();

        for (SidePot pot : state.sidePots) {
            List<PokerHandEvaluator.PlayerScore> eligibleScores = new ArrayList<>();
            for (PokerHandEvaluator.PlayerScore ps : scores) {
                if (pot.eligiblePlayers.contains(ps.playerIndex)) {
                    eligibleScores.add(ps);
                }
            }

            if (eligibleScores.isEmpty()) continue;

            eligibleScores.sort((a, b) -> b.score.compareTo(a.score));

            List<Integer> potWinners = new ArrayList<>();
            PokerHandEvaluator.HandScore bestScore = eligibleScores.get(0).score;
            for (PokerHandEvaluator.PlayerScore ps : eligibleScores) {
                if (ps.score.equals(bestScore)) {
                    potWinners.add(ps.playerIndex);
                }
            }

            int share = pot.amount / potWinners.size();
            int remainder = pot.amount % potWinners.size();

            for (int w : potWinners) {
                winnings.merge(w, share, Integer::sum);
            }

            if (remainder > 0) {
                int closestToButton = potWinners.stream()
                    .min((a, b) -> Integer.compare(
                        (a - state.buttonPosition + NUM_PLAYERS) % NUM_PLAYERS,
                        (b - state.buttonPosition + NUM_PLAYERS) % NUM_PLAYERS))
                    .orElse(potWinners.get(0));
                winnings.merge(closestToButton, remainder, Integer::sum);
            }
        }

        for (Map.Entry<Integer, Integer> entry : winnings.entrySet()) {
            state.stacks[entry.getKey()] += entry.getValue();
        }

        state.winners = winnings.keySet().stream().mapToInt(Integer::intValue).toArray();
        state.lastPotWon = state.sidePots.stream().mapToInt(p -> p.amount).sum();
        state.pot = 0;
        state.sidePots.clear();
    }
}
