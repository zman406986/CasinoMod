package data.scripts.casino.cards.poker5;

import java.util.*;

import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import data.scripts.casino.cards.Card;
import data.scripts.casino.cards.pokerShared.PokerRound;
import data.scripts.casino.cards.pokerShared.PokerUtils;

public class PokerGame5Factory {

    public static PokerGame5 restoreFromMemory(MemoryAPI mem) {
        if (!mem.contains("$ipc_poker5_pot")) return null;

        int[] stacks = new int[PokerGame5.NUM_PLAYERS];
        int[] bets = new int[PokerGame5.NUM_PLAYERS];
        boolean[] folded = new boolean[PokerGame5.NUM_PLAYERS];
        boolean[] allIn = new boolean[PokerGame5.NUM_PLAYERS];
        boolean[] hasActed = new boolean[PokerGame5.NUM_PLAYERS];
        List<Card>[] hands = new ArrayList[PokerGame5.NUM_PLAYERS];

        int buttonPosition = mem.getInt("$ipc_poker5_button_position");
        int bigBlind = mem.getInt("$ipc_poker5_big_blind");
        int pot = mem.getInt("$ipc_poker5_pot");
        PokerRound round = PokerRound.valueOf(mem.getString("$ipc_poker5_round"));
        int currentPlayer = mem.getInt("$ipc_poker5_current_player");

        List<Card> communityCards = new ArrayList<>();
        int communityCount = mem.getInt("$ipc_poker5_community_count");
        for (int i = 0; i < communityCount; i++) {
            Card card = PokerUtils.stringToCard(mem.getString("$ipc_poker5_community_" + i));
            if (card != null) communityCards.add(card);
        }

        for (int i = 0; i < PokerGame5.NUM_PLAYERS; i++) {
            stacks[i] = mem.getInt("$ipc_poker5_stack_" + i);
            bets[i] = mem.getInt("$ipc_poker5_bet_" + i);
            folded[i] = mem.getBoolean("$ipc_poker5_folded_" + i);
            allIn[i] = mem.getBoolean("$ipc_poker5_allin_" + i);
            hasActed[i] = mem.getBoolean("$ipc_poker5_has_acted_" + i);

            hands[i] = new ArrayList<>();
            int handCount = mem.getInt("$ipc_poker5_hand_count_" + i);
            for (int j = 0; j < handCount; j++) {
                Card card = PokerUtils.stringToCard(mem.getString("$ipc_poker5_hand_" + i + "_" + j));
                if (card != null) hands[i].add(card);
            }
        }

        return createSuspendedGame(stacks, bets, folded, allIn, hasActed, hands, communityCards,
            buttonPosition, bigBlind, pot, round, currentPlayer);
    }

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
            PokerRound round,
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
