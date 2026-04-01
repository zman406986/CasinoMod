package data.scripts.casino.interaction;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import data.scripts.casino.CasinoConfig;
import data.scripts.casino.CasinoVIPManager;
import data.scripts.casino.cards.Card;
import data.scripts.casino.Strings;
import data.scripts.casino.poker5.PokerGame5;
import data.scripts.casino.poker5.PokerGame5Factory;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class PokerHandler5 {

    private final CasinoInteraction main;
    private PokerGame5 pokerGame;

    private static final int COOLDOWN_DAYS = 1;
    private static final int MIN_HANDS_BEFORE_LEAVE = 3;

    public PokerGame5 getPokerGame() {
        return pokerGame;
    }

    protected int playerWallet;
    private int handsPlayedThisSession = 0;
    private int pendingStackSize = 0;
    private static final String POKER5_COOLDOWN_KEY = "$ipc_poker5_cooldown_until";

    private final Map<String, OptionHandler> handlers = new HashMap<>();
    private final Map<Predicate<String>, OptionHandler> predicateHandlers = new HashMap<>();

    public PokerHandler5(CasinoInteraction main) {
        this.main = main;
        initializeHandlers();
    }

    private void initializeHandlers() {
        handlers.put("play5", option -> showPoker5Confirm());
        handlers.put("confirm_poker5_ante", option -> setupGame());
        handlers.put("next_hand5", option -> startNextHand());
        handlers.put("how_to_poker5", option -> main.help.showPokerHelp());
        handlers.put("poker5_call", option -> handlePoker5Call());
        handlers.put("poker5_check", option -> handlePoker5Check());
        handlers.put("poker5_fold", option -> handlePoker5Fold());
        handlers.put("poker5_raise_menu", option -> showRaiseOptions());
        handlers.put("poker5_back_action", option -> showPoker5VisualPanel());
        handlers.put("poker5_suspend", option -> suspendGame());
        handlers.put("poker5_back_to_menu", option -> handleLeaveTable());
        handlers.put("poker5_leave_now", option -> main.showMenu());
        handlers.put("poker5_abandon_confirm", option -> showAbandonConfirm());
        handlers.put("poker5_abandon_confirm_leave", option -> handleLeaveTable());
        handlers.put("poker5_abandon_cancel", option -> showPoker5VisualPanel());
        handlers.put("back_menu5", option -> {
            pokerGame = null;
            handsPlayedThisSession = 0;
            clearSuspendedGameMemory();
            main.showMenu();
        });

        predicateHandlers.put(option -> option.startsWith("poker5_raise_"), option -> {
            int amt = Integer.parseInt(option.replace("poker5_raise_", ""));
            performRaise(amt);
        });

        predicateHandlers.put(option -> option.startsWith("poker5_stack_"), option -> {
            String stackStr = option.replace("poker5_stack_", "");
            int stackSize = Integer.parseInt(stackStr);
            setupGame(stackSize);
        });
    }

    public void handle(String option) {
        OptionHandler handler = handlers.get(option);
        if (handler != null) {
            handler.handle(option);
            return;
        }

        for (Map.Entry<Predicate<String>, OptionHandler> entry : predicateHandlers.entrySet()) {
            if (entry.getKey().test(option)) {
                entry.getValue().handle(option);
                return;
            }
        }
    }

    public void showPoker5Confirm() {
        main.options.clearOptions();

        if (hasSuspendedPoker5()) {
            main.setState(CasinoInteraction.State.POKER);
            main.textPanel.addPara(Strings.get("poker.resuming"), Color.YELLOW);
            restoreSuspendedGame();
            return;
        }

        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();

        if (mem.contains(POKER5_COOLDOWN_KEY)) {
            long cooldownStart = mem.getLong(POKER5_COOLDOWN_KEY);
            float elapsedDays = Global.getSector().getClock().getElapsedDaysSince(cooldownStart);
            if (elapsedDays < COOLDOWN_DAYS) {
                float daysRemaining = COOLDOWN_DAYS - elapsedDays;
                main.textPanel.addPara("5-player poker table is occupied.", Color.RED);
                main.options.addOption(Strings.get("common.back"), "back_menu5");
                return;
            } else {
                mem.unset(POKER5_COOLDOWN_KEY);
            }
        }

        main.textPanel.addPara(Strings.get("poker.choose_stack") + " (5-player table)", Color.YELLOW);

        int currentBalance = CasinoVIPManager.getBalance();
        int[] stackSizes = CasinoConfig.POKER_STACK_SIZES;
        String[] stackLabels = {
            Strings.get("poker.stack_small"),
            Strings.get("poker.stack_medium"),
            Strings.get("poker.stack_large"),
            Strings.get("poker.stack_huge")
        };

        for (int i = 0; i < stackSizes.length && i < stackLabels.length; i++) {
            if (currentBalance >= stackSizes[i]) {
                main.options.addOption(Strings.format("poker.stack_label", stackLabels[i], stackSizes[i]), "poker5_stack_" + stackSizes[i]);
            }
        }

        main.options.addOption(Strings.get("poker.how_to_play"), "how_to_poker5");
        main.options.addOption(Strings.get("common.back"), "back_menu5");
        main.setState(CasinoInteraction.State.POKER);
    }

    public void setupGame() {
        int defaultStack = CasinoConfig.POKER_STACK_SIZES.length > 0 ? CasinoConfig.POKER_STACK_SIZES[0] : 10000;
        setupGame(defaultStack);
    }

    public void setupGame(int stackSize) {
        playerWallet = CasinoVIPManager.getBalance();

        if (playerWallet < stackSize) {
            main.textPanel.addPara("Insufficient funds for 5-player poker.", Color.RED);
            showPoker5Confirm();
            return;
        }

        startGameWithStack(stackSize);
    }

    private void startGameWithStack(int stackSize) {
        CasinoVIPManager.addToBalance(-stackSize);

        int[] startingStacks = new int[PokerGame5.NUM_PLAYERS];
        startingStacks[PokerGame5.HUMAN_PLAYER_INDEX] = stackSize;
        for (int i = 1; i < PokerGame5.NUM_PLAYERS; i++) {
            startingStacks[i] = stackSize;
        }

        pokerGame = new PokerGame5(startingStacks);
        handsPlayedThisSession = 0;

        showPoker5VisualPanel();
    }

    private void showPoker5VisualPanel() {
        if (pokerGame == null) return;
        main.options.clearOptions();
        PokerGame5.PokerState5 state = pokerGame.getState();

        main.textPanel.addPara("=== 5-Player Poker ===", Color.CYAN);
        main.textPanel.addPara(Strings.format("poker_ui.pot_bb", String.valueOf(state.pot), String.valueOf(pokerGame.getBigBlindAmount())), Color.GREEN);

        for (int i = 0; i < PokerGame5.NUM_PLAYERS; i++) {
            String posName = pokerGame.getPositionName(i);
            String playerLabel = i == PokerGame5.HUMAN_PLAYER_INDEX ? "You" : "Opponent " + i;
            String status = state.foldedPlayers.contains(i) ? "(folded)" :
                           state.declaredAllIn[i] ? "(all-in)" : "";
            main.textPanel.addPara(String.format("[%s] %s: %d chips %s", posName, playerLabel, state.stacks[i], status),
                                   i == PokerGame5.HUMAN_PLAYER_INDEX ? Color.CYAN : Color.ORANGE);
        }

        if (!state.communityCards.isEmpty()) {
            displayCommunityCards(state.communityCards);
        }

        if (state.hands[PokerGame5.HUMAN_PLAYER_INDEX] != null && !state.hands[PokerGame5.HUMAN_PLAYER_INDEX].isEmpty()) {
            displayPlayerHand(state.hands[PokerGame5.HUMAN_PLAYER_INDEX]);
        }

        if (state.currentPlayerIndex == PokerGame5.HUMAN_PLAYER_INDEX && pokerGame.canAct(PokerGame5.HUMAN_PLAYER_INDEX)) {
            showPlayerOptions();
        } else if (state.round != PokerGame5.Round.SHOWDOWN) {
            main.textPanel.addPara("Waiting for opponent actions...", Color.YELLOW);
            main.options.addOption("Continue", "poker5_continue_ai");
            handlers.put("poker5_continue_ai", option -> processAITurns());
        } else {
            showEndOfHandOptions();
        }
    }

    private void showPlayerOptions() {
        PokerGame5.PokerState5 state = pokerGame.getState();
        int callAmount = pokerGame.getCallAmount(PokerGame5.HUMAN_PLAYER_INDEX);

        if (callAmount > 0) {
            main.options.addOption(Strings.format("poker_ui.call", callAmount), "poker5_call");
            main.options.addOption(Strings.get("poker_ui.fold"), "poker5_fold");
        } else {
            main.options.addOption(Strings.get("poker_ui.check"), "poker5_check");
        }

        if (state.stacks[PokerGame5.HUMAN_PLAYER_INDEX] > 0) {
            main.options.addOption(Strings.get("poker_ui.raise"), "poker5_raise_menu");
        }

        main.options.addOption(Strings.get("poker.how_to_play"), "how_to_poker5");
        main.options.addOption("Leave table", "poker5_abandon_confirm");
    }

    private void showEndOfHandOptions() {
        PokerGame5.PokerState5 state = pokerGame.getState();

        if (state.winners.length > 0) {
            StringBuilder winnerText = new StringBuilder("Winner(s): ");
            for (int w : state.winners) {
                winnerText.append(w == PokerGame5.HUMAN_PLAYER_INDEX ? "You" : "Opponent " + w).append(" ");
            }
            main.textPanel.addPara(winnerText.toString(), Color.GREEN);
        }

        boolean canContinue = state.stacks[PokerGame5.HUMAN_PLAYER_INDEX] >= pokerGame.getBigBlindAmount();

        if (canContinue) {
            main.options.addOption(Strings.get("poker_result.next_hand"), "next_hand5");
        }
        main.options.addOption(Strings.get("poker_result.leave_table"), "back_menu5");
    }

    private void showRaiseOptions() {
        if (pokerGame == null) return;
        main.options.clearOptions();
        PokerGame5.PokerState5 state = pokerGame.getState();

        int playerStack = state.stacks[PokerGame5.HUMAN_PLAYER_INDEX];
        int bigBlind = pokerGame.getBigBlindAmount();
        int currentBet = pokerGame.getCurrentBet();
        int minRaise = pokerGame.getMinRaiseAmount(PokerGame5.HUMAN_PLAYER_INDEX);

        int[] raiseSizes = { minRaise, currentBet + state.pot / 2, currentBet + state.pot, currentBet + state.pot * 2 };

        for (int raiseSize : raiseSizes) {
            if (raiseSize > currentBet && raiseSize <= playerStack + state.bets[PokerGame5.HUMAN_PLAYER_INDEX]) {
                main.options.addOption("Raise to " + raiseSize, "poker5_raise_" + raiseSize);
            }
        }

        if (playerStack > 0) {
            main.options.addOption("All-in (" + playerStack + ")", "poker5_raise_" + (state.bets[PokerGame5.HUMAN_PLAYER_INDEX] + playerStack));
        }

        main.options.addOption(Strings.get("poker_ui.back"), "poker5_back_action");
    }

    private void processAITurns() {
        if (pokerGame == null) return;
        PokerGame5.PokerState5 state = pokerGame.getState();

        while (state.currentPlayerIndex != PokerGame5.HUMAN_PLAYER_INDEX &&
               state.round != PokerGame5.Round.SHOWDOWN &&
               pokerGame.canAct(state.currentPlayerIndex)) {

            PokerAI5 ai = pokerGame.getAI(state.currentPlayerIndex);
            if (ai != null) {
                PokerAI5.AIResponse response = ai.decideAction(state.currentPlayerIndex, state);
                pokerGame.processAIAction(state.currentPlayerIndex, response);

                String actionText = formatAIAction(state.currentPlayerIndex, response);
                main.textPanel.addPara(actionText, Color.YELLOW);
            }

            state = pokerGame.getState();
        }

        showPoker5VisualPanel();
    }

    private String formatAIAction(int playerIndex, PokerAI5.AIResponse response) {
        String playerName = "Opponent " + playerIndex + " (" + pokerGame.getPositionName(playerIndex) + ")";
        return switch (response.action) {
            case FOLD -> playerName + " folds.";
            case CHECK -> playerName + " checks.";
            case CALL -> playerName + " calls.";
            case RAISE -> playerName + " raises to " + response.raiseAmount + ".";
            case ALL_IN -> playerName + " goes all-in!";
        };
    }

    public void handlePoker5Call() {
        if (pokerGame == null) return;
        int callAmount = pokerGame.getCallAmount(PokerGame5.HUMAN_PLAYER_INDEX);
        pokerGame.processAction(PokerGame5.HUMAN_PLAYER_INDEX, PokerGame5.Action.CALL, 0);
        main.textPanel.addPara(Strings.format("poker_actions.you_call_stargems", callAmount), Color.CYAN);
        processAITurns();
    }

    public void handlePoker5Check() {
        if (pokerGame == null) return;
        pokerGame.processAction(PokerGame5.HUMAN_PLAYER_INDEX, PokerGame5.Action.CHECK, 0);
        main.textPanel.addPara(Strings.get("poker_actions.you_check_dot"), Color.CYAN);
        processAITurns();
    }

    public void handlePoker5Fold() {
        if (pokerGame == null) return;
        pokerGame.processAction(PokerGame5.HUMAN_PLAYER_INDEX, PokerGame5.Action.FOLD, 0);
        main.textPanel.addPara(Strings.get("poker_actions.you_fold_dot"), Color.GRAY);
        processAITurns();
    }

    private void performRaise(int totalBet) {
        if (pokerGame == null) return;
        pokerGame.processAction(PokerGame5.HUMAN_PLAYER_INDEX, PokerGame5.Action.RAISE, totalBet);
        main.textPanel.addPara(Strings.format("poker_actions.you_raise_to", totalBet), Color.CYAN);
        processAITurns();
    }

    private void startNextHand() {
        if (pokerGame != null) {
            PokerGame5.PokerState5 state = pokerGame.getState();
            if (state.stacks[PokerGame5.HUMAN_PLAYER_INDEX] <= pokerGame.getBigBlindAmount()) {
                main.textPanel.addPara("Not enough chips for another hand.", Color.YELLOW);
                handleLeaveTable();
                return;
            }
            pokerGame.startNewHand();
            handsPlayedThisSession++;
            showPoker5VisualPanel();
        }
    }

    private void displayCommunityCards(List<Card> cards) {
        main.textPanel.addPara("Community cards:", Color.YELLOW);
        for (Card c : cards) {
            Color suitColor = getSuitColor(c);
            main.textPanel.addPara("  " + c.toString(), suitColor);
        }
    }

    private void displayPlayerHand(List<Card> cards) {
        main.textPanel.addPara("Your hand:", Color.CYAN);
        for (Card c : cards) {
            Color suitColor = getSuitColor(c);
            main.textPanel.addPara("  " + c.toString(), suitColor);
        }
    }

    private Color getSuitColor(Card c) {
        return switch (c.suit()) {
            case HEARTS -> Color.RED;
            case DIAMONDS -> Color.BLUE;
            case CLUBS -> Color.GREEN;
            case SPADES -> Color.GRAY;
        };
    }

    private void suspendGame() {
        if (pokerGame == null) return;
        PokerGame5.PokerState5 state = pokerGame.getState();
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        mem.set("$ipc_suspended_game_type", "Poker5");

        mem.set("$ipc_poker5_button_position", state.buttonPosition);
        mem.set("$ipc_poker5_big_blind", state.bigBlind);
        mem.set("$ipc_poker5_round", state.round.name());
        mem.set("$ipc_poker5_current_player", state.currentPlayerIndex);
        mem.set("$ipc_poker5_pot", state.pot);
        mem.set("$ipc_poker5_hands_played", handsPlayedThisSession);

        for (int i = 0; i < PokerGame5.NUM_PLAYERS; i++) {
            mem.set("$ipc_poker5_stack_" + i, state.stacks[i]);
            mem.set("$ipc_poker5_bet_" + i, state.bets[i]);
            mem.set("$ipc_poker5_folded_" + i, state.foldedPlayers.contains(i));
            mem.set("$ipc_poker5_allin_" + i, state.declaredAllIn[i]);
            mem.set("$ipc_poker5_has_acted_" + i, state.hasActed[i]);

            if (state.hands[i] != null) {
                mem.set("$ipc_poker5_hand_count_" + i, state.hands[i].size());
                for (int j = 0; j < state.hands[i].size(); j++) {
                    mem.set("$ipc_poker5_hand_" + i + "_" + j, PokerGame5.cardToString(state.hands[i].get(j)));
                }
            }
        }

        mem.set("$ipc_poker5_community_count", state.communityCards.size());
        for (int i = 0; i < state.communityCards.size(); i++) {
            mem.set("$ipc_poker5_community_" + i, PokerGame5.cardToString(state.communityCards.get(i)));
        }

        mem.set("$ipc_poker5_suspend_time", Global.getSector().getClock().getTimestamp());

        main.textPanel.addPara("Game suspended. You can resume later.", Color.YELLOW);
        main.options.clearOptions();
        main.options.addOption("Leave", "poker5_leave_now");
    }

    public void restoreSuspendedGame() {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();

        if (!mem.contains("$ipc_poker5_pot")) {
            clearSuspendedGameMemory();
            main.textPanel.addPara("Corrupted saved game data.", Color.RED);
            showPoker5Confirm();
            return;
        }

        int[] stacks = new int[PokerGame5.NUM_PLAYERS];
        int[] bets = new int[PokerGame5.NUM_PLAYERS];
        boolean[] folded = new boolean[PokerGame5.NUM_PLAYERS];
        boolean[] allIn = new boolean[PokerGame5.NUM_PLAYERS];
        boolean[] hasActed = new boolean[PokerGame5.NUM_PLAYERS];
        List<Card>[] hands = new ArrayList[PokerGame5.NUM_PLAYERS];

        int bigBlind = mem.getInt("$ipc_poker5_big_blind");
        int buttonPosition = mem.getInt("$ipc_poker5_button_position");
        int pot = mem.getInt("$ipc_poker5_pot");
        PokerGame5.Round round = PokerGame5.Round.valueOf(mem.getString("$ipc_poker5_round"));
        int currentPlayer = mem.getInt("$ipc_poker5_current_player");
        handsPlayedThisSession = mem.getInt("$ipc_poker5_hands_played");

        List<Card> communityCards = new ArrayList<>();
        int communityCount = mem.getInt("$ipc_poker5_community_count");
        for (int i = 0; i < communityCount; i++) {
            Card card = PokerGame5.stringToCard(mem.getString("$ipc_poker5_community_" + i));
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
                Card card = PokerGame5.stringToCard(mem.getString("$ipc_poker5_hand_" + i + "_" + j));
                if (card != null) hands[i].add(card);
            }
        }

        pokerGame = PokerGame5Factory.createSuspendedGame(
            stacks, bets, folded, allIn, hasActed, hands, communityCards,
            buttonPosition, bigBlind, pot, round, currentPlayer
        );

        main.textPanel.addPara("Resuming 5-player poker game.", Color.CYAN);
        showPoker5VisualPanel();
    }

    public boolean hasSuspendedPoker5() {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        String suspendedGameType = mem.getString("$ipc_suspended_game_type");
        return "Poker5".equals(suspendedGameType);
    }

    private void clearSuspendedGameMemory() {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        mem.unset("$ipc_suspended_game_type");
        mem.unset("$ipc_poker5_button_position");
        mem.unset("$ipc_poker5_big_blind");
        mem.unset("$ipc_poker5_round");
        mem.unset("$ipc_poker5_current_player");
        mem.unset("$ipc_poker5_pot");
        mem.unset("$ipc_poker5_hands_played");
        mem.unset("$ipc_poker5_suspend_time");

        for (int i = 0; i < PokerGame5.NUM_PLAYERS; i++) {
            mem.unset("$ipc_poker5_stack_" + i);
            mem.unset("$ipc_poker5_bet_" + i);
            mem.unset("$ipc_poker5_folded_" + i);
            mem.unset("$ipc_poker5_allin_" + i);
            mem.unset("$ipc_poker5_has_acted_" + i);
            mem.unset("$ipc_poker5_hand_count_" + i);
            for (int j = 0; j < 2; j++) {
                mem.unset("$ipc_poker5_hand_" + i + "_" + j);
            }
        }

        for (int i = 0; i < 5; i++) {
            mem.unset("$ipc_poker5_community_" + i);
        }
        mem.unset("$ipc_poker5_community_count");
    }

    private void showAbandonConfirm() {
        if (pokerGame == null) {
            main.showMenu();
            return;
        }
        PokerGame5.PokerState5 state = pokerGame.getState();
        int stackValue = state.stacks[PokerGame5.HUMAN_PLAYER_INDEX];

        main.options.clearOptions();
        main.textPanel.addPara("Leave the 5-player table?", Color.RED);
        main.textPanel.addPara(Strings.format("poker_suspend.stack_returned", stackValue), Color.CYAN);

        main.options.addOption("Yes, leave", "poker5_abandon_confirm_leave");
        main.options.addOption("Go back", "poker5_abandon_cancel");
    }

    private void handleLeaveTable() {
        if (pokerGame != null && pokerGame.getState().stacks[PokerGame5.HUMAN_PLAYER_INDEX] > 0) {
            int stackToReturn = pokerGame.getState().stacks[PokerGame5.HUMAN_PLAYER_INDEX];
            CasinoVIPManager.addToBalance(stackToReturn);
            main.textPanel.addPara(Strings.format("poker_result.cash_out_storm", stackToReturn), Color.GREEN);
        }

        if (handsPlayedThisSession < MIN_HANDS_BEFORE_LEAVE) {
            MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
            mem.set(POKER5_COOLDOWN_KEY, Global.getSector().getClock().getTimestamp());
        }

        handsPlayedThisSession = 0;
        clearSuspendedGameMemory();
        pokerGame = null;
        main.showMenu();
    }

    private interface OptionHandler {
        void handle(String option);
    }
}