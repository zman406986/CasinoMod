package data.scripts.casino.poker5;

import java.util.*;

import data.scripts.casino.cards.Card;

public class PokerGame5Factory {

    public static PokerGame5 createSuspendedGame(
            int[] stacks,
            int[] bets,
            boolean[] folded,
            boolean[] allIn,
            boolean[] hasActed,
            List<Card>[] hands,
            List<Card> communityCards,
            int buttonPosition,
            int bigBlind,
            int pot,
            PokerGame5.Round round,
            int currentPlayerIndex) {

        PokerGame5 game = new PokerGame5(stacks, true);

        PokerGame5.PokerState5 state = game.getState();

        state.buttonPosition = buttonPosition;
        state.bigBlind = bigBlind;
        state.round = round;
        state.currentPlayerIndex = currentPlayerIndex;
        state.pot = pot;

        for (int i = 0; i < PokerGame5.NUM_PLAYERS; i++) {
            state.stacks[i] = stacks[i];
            state.bets[i] = bets[i];
            state.displayBets[i] = bets[i];
            state.totalContributions[i] = bets[i];
            state.hands[i] = new ArrayList<>(hands[i]);
            state.declaredAllIn[i] = allIn[i];
            state.hasActed[i] = hasActed[i];

            if (folded[i]) {
                state.foldedPlayers.add(i);
                state.activePlayers.remove(i);
            }
        }

        state.communityCards = new ArrayList<>(communityCards);

        game.evaluateHands();

        game.calculateSidePotsFromState();

        return game;
    }
}