package data.scripts.casino.cards.blackjack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import data.scripts.casino.cards.Card;
import data.scripts.casino.cards.Deck;
import data.scripts.casino.cards.GameType;

public class BlackjackGame {

    public enum Action { HIT, STAND, DOUBLE_DOWN, SPLIT }

    public enum GameState { BETTING, PLAYER_TURN, DEALER_TURN, RESULT }

    private static final float BLACKJACK_PAYOUT = 2.5f; // 3:2 payout for blackjack

    public static class Hand {
        public final List<Card> cards = new ArrayList<>(12);
        public int betAmount;

        public Hand() {}

        public Hand(int bet) {
            this.betAmount = bet;
        }

        public void addCard(Card card) {
            cards.add(card);
        }

        private int[] computeValueAndAces() {
            int total = 0, aces = 0;
            for (Card c : cards) {
                total += c.value();
                if (c.isAce()) aces++;
            }
            return new int[]{total, aces};
        }

        public int getValue() {
            int[] result = computeValueAndAces();
            int total = result[0], aces = result[1];
            while (total > 21 && aces > 0) {
                total -= 10;
                aces--;
            }
            return total;
        }

        public boolean isSoft() {
            int[] result = computeValueAndAces();
            int total = result[0], aces = result[1];
            int reducedAces = 0;
            while (total > 21 && aces > reducedAces) {
                total -= 10;
                reducedAces++;
            }
            return aces > reducedAces;
        }

        public boolean isBust() { return getValue() > 21; }

        public boolean isBlackjack() { return cards.size() == 2 && getValue() == 21; }

        public boolean canSplit() {
            return cards.size() == 2 &&
                   cards.get(0).rank() == cards.get(1).rank();
        }

        public boolean canDoubleDown() { return cards.size() == 2; }
    }

    public static class GameStateData {
        public Hand playerHand;
        public Hand dealerHand;
        public List<Hand> splitHands;
        public int currentSplitIndex;
        public int pot;
        public int playerStack;
        public int currentBet;
        public GameState state;
        public int lastPotWon;
        public boolean dealerHoleCardRevealed;
        public int originalBalance;
        public int creditCeiling;
        public int creditBorrowed;
        public List<Integer> splitHandResults;
        public boolean overdraftEnabled = false;
    }

    private final GameStateData state;
    private Deck deck;
    private int handsSinceShuffle;
    private static final int RESHUFFLE_INTERVAL = 50;

    public BlackjackGame(int playerStack) {
        state = new GameStateData();
        state.playerStack = playerStack;
        state.playerHand = new Hand();
        state.dealerHand = new Hand();
        state.splitHands = new ArrayList<>();
        deck = new Deck(GameType.BLACKJACK);
        deck.shuffle();
        handsSinceShuffle = 0;
    }

    private void resetHandState() {
        state.playerHand = new Hand();
        state.dealerHand = new Hand();
        state.splitHands = new ArrayList<>();
        state.currentSplitIndex = 0;
        state.pot = 0;
        state.lastPotWon = 0;
        state.dealerHoleCardRevealed = false;
        state.state = GameState.BETTING;
        state.currentBet = 0;
    }

    public void startNewHand() {
        handsSinceShuffle++;
        if (handsSinceShuffle >= RESHUFFLE_INTERVAL) {
            deck = new Deck(GameType.BLACKJACK);
            deck.shuffle();
            handsSinceShuffle = 0;
        }
        resetHandState();
    }

    public boolean placeBet(int betAmount) {
        if (state.state != GameState.BETTING) return false;
        if (state.playerStack < betAmount) return false;

        state.playerStack -= betAmount;
        state.currentBet = betAmount;
        state.playerHand = new Hand(betAmount);
        state.dealerHand = new Hand();
        state.splitHands = new ArrayList<>();
        state.currentSplitIndex = 0;
        state.pot = betAmount;
        state.lastPotWon = 0;
        state.dealerHoleCardRevealed = false;

        state.playerHand.addCard(deck.draw());
        state.playerHand.addCard(deck.draw());
        state.dealerHand.addCard(deck.draw());
        state.dealerHand.addCard(deck.draw());

        state.pot += betAmount;

        if (state.playerHand.isBlackjack()) {
            revealDealerHoleCard();
            evaluateDealerHand();
        } else {
            state.state = GameState.PLAYER_TURN;
        }

        return true;
    }

    public void playerHit() {
        if (state.state != GameState.PLAYER_TURN) return;

        Hand currentHand = getCurrentHand();
        if (currentHand == null) return;

        currentHand.addCard(deck.draw());

        if (currentHand.isBust()) {
            handleBust(currentHand);
        }
    }

    public void playerStand() {
        if (state.state != GameState.PLAYER_TURN) return;

        if (!state.splitHands.isEmpty()) {
            state.currentSplitIndex++;
            if (state.currentSplitIndex < state.splitHands.size()) {
                state.dealerHoleCardRevealed = false;
                return;
            }
        }

        revealDealerHoleCard();
        state.state = GameState.DEALER_TURN;
        evaluateDealerHand();
    }

    public void playerDoubleDown() {
        if (state.state != GameState.PLAYER_TURN) return;

        Hand currentHand = getCurrentHand();
        if (currentHand == null || !currentHand.canDoubleDown()) return;

        if (state.playerStack < currentHand.betAmount) return;

        state.playerStack -= currentHand.betAmount;
        state.pot += currentHand.betAmount;
        currentHand.betAmount *= 2;

        currentHand.addCard(deck.draw());

        if (currentHand.isBust()) {
            handleBust(currentHand);
        } else {
            playerStand();
        }
    }

    public void playerSplit() {
        if (state.state != GameState.PLAYER_TURN) return;

        Hand currentHand = getCurrentHand();
        if (currentHand == null || !currentHand.canSplit()) return;

        if (state.playerStack < currentHand.betAmount) return;

        if (!state.splitHands.isEmpty()) return;

        Card first = currentHand.cards.get(0);
        Card second = currentHand.cards.get(1);

        Hand hand1 = new Hand(currentHand.betAmount);
        hand1.addCard(first);
        hand1.addCard(deck.draw());

        Hand hand2 = new Hand(currentHand.betAmount);
        hand2.addCard(second);
        hand2.addCard(deck.draw());

        state.playerStack -= currentHand.betAmount;
        state.pot += currentHand.betAmount;

        state.splitHands.add(hand1);
        state.splitHands.add(hand2);
        state.currentSplitIndex = 0;
        state.dealerHoleCardRevealed = false;
    }

    private void handleBust(Hand hand) {
        state.pot -= hand.betAmount;
        if (!state.splitHands.isEmpty() && state.currentSplitIndex < state.splitHands.size() - 1) {
            state.currentSplitIndex++;
            state.dealerHoleCardRevealed = false;
        } else {
            revealDealerHoleCard();
            state.state = GameState.RESULT;
            determineWinners();
        }
    }

    private void evaluateDealerHand() {
        while (state.dealerHand.getValue() < 17) {
            state.dealerHand.addCard(deck.draw());
        }
        determineWinners();
    }

    private void determineWinners() {
        int dealerValue = state.dealerHand.getValue();
        boolean dealerBust = state.dealerHand.isBust();
        boolean dealerBlackjack = state.dealerHand.isBlackjack();
        boolean isSplitGame = !state.splitHands.isEmpty();

        int totalWinnings = 0;
        int netProfit = 0;

        state.splitHandResults = new ArrayList<>();

        List<Hand> handsToEvaluate = isSplitGame 
            ? state.splitHands 
            : Collections.singletonList(state.playerHand);

        for (Hand hand : handsToEvaluate) {
            int handProfit = 0;
            int handValue = hand.getValue();

            if (hand.isBust()) {
                handProfit = -hand.betAmount;
                netProfit -= hand.betAmount;
            } else if (hand.isBlackjack() && !dealerBlackjack) {
                int win = isSplitGame ? hand.betAmount * 2 : (int)(hand.betAmount * BLACKJACK_PAYOUT);
                totalWinnings += win;
                handProfit = win - hand.betAmount;
                netProfit += handProfit;
            } else if (dealerBust || handValue > dealerValue) {
                totalWinnings += hand.betAmount * 2;
                handProfit = hand.betAmount;
                netProfit += handProfit;
            } else if (handValue < dealerValue) {
                handProfit = -hand.betAmount;
                netProfit -= hand.betAmount;
            } else {
                totalWinnings += hand.betAmount;
            }

            state.splitHandResults.add(handProfit);
        }

        state.lastPotWon = netProfit;
        state.playerStack += totalWinnings;
        state.pot = 0;
        state.state = GameState.RESULT;
    }

    private void revealDealerHoleCard() {
        state.dealerHoleCardRevealed = true;
    }

    public GameStateData getState() {
        return state;
    }

    public Hand getPlayerHand() {
        return state.playerHand;
    }

    public Hand getDealerHand() {
        return state.dealerHand;
    }

    public boolean isDealerHoleCardRevealed() {
        return state.dealerHoleCardRevealed;
    }

    public Hand getCurrentHand() {
        if (!state.splitHands.isEmpty() && state.currentSplitIndex < state.splitHands.size()) {
            return state.splitHands.get(state.currentSplitIndex);
        }
        return state.playerHand;
    }

    }
