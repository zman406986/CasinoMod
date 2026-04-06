package data.scripts.casino.interaction;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import data.scripts.casino.CasinoConfig;
import data.scripts.casino.CasinoVIPManager;
import data.scripts.casino.Strings;
import data.scripts.casino.cards.poker5.PokerAI5;
import data.scripts.casino.cards.poker5.PokerDialogDelegate5;
import data.scripts.casino.cards.poker5.PokerGame5;
import data.scripts.casino.cards.poker5.PokerGame5Factory;
import data.scripts.casino.cards.pokerShared.PokerAction;
import data.scripts.casino.cards.pokerShared.PokerRound;
import data.scripts.casino.cards.pokerShared.PokerUtils;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

public class PokerHandler5 {

    private final CasinoInteraction main;
    private PokerGame5 pokerGame;
    private PokerDialogDelegate5 currentDelegate;

    private static final int COOLDOWN_DAYS = 1;
    private static final int MIN_HANDS_BEFORE_LEAVE = 3;

    private int handsPlayedThisSession = 0;
    private static final String POKER5_COOLDOWN_KEY = "$ipc_poker5_cooldown_until";

    private final Map<String, OptionHandler> handlers = new HashMap<>();
    private final Map<Predicate<String>, OptionHandler> predicateHandlers = new HashMap<>();

    public PokerHandler5(CasinoInteraction main) {
        this.main = main;
        initializeHandlers();
    }

    private void initializeHandlers() {
        handlers.put("play5", option -> showPoker5Confirm());
        handlers.put("poker5_back_action", option -> showPoker5VisualPanel());
        handlers.put("how_to_poker5", option -> main.help.showPokerHelp("poker5_back_action"));
        handlers.put("how_to_poker5_menu", option -> main.help.showPokerHelp("play5"));
        handlers.put("poker5_resume_continue", option -> {
            clearSuspendedGameMemory();
            showPoker5VisualPanel();
        });
        handlers.put("poker5_resume_wait", option -> main.showMenu());
        handlers.put("back_menu5", option -> {
            pokerGame = null;
            handsPlayedThisSession = 0;
            currentDelegate = null;
            clearSuspendedGameMemory();
            main.showMenu();
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
                main.textPanel.addPara(Strings.get("poker.dealer_eyes_coldly"), Color.RED);
                main.textPanel.addPara(Strings.get("poker.left_early_warning"), Color.YELLOW);
                main.textPanel.addPara(Strings.format("poker.return_after_days", daysRemaining), Color.GRAY);
                main.options.addOption(Strings.get("common.back"), "back_menu5");
                return;
            } else {
                mem.unset(POKER5_COOLDOWN_KEY);
            }
        }

        main.textPanel.addPara(Strings.get("poker.choose_stack") + Strings.get("poker5.table_suffix"), Color.YELLOW);

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

        main.options.addOption(Strings.get("poker.how_to_play"), "how_to_poker5_menu");
        main.options.addOption(Strings.get("common.back"), "back_menu5");
        main.setState(CasinoInteraction.State.POKER);
    }

    public void setupGame(int stackSize) {
        int playerWallet = CasinoVIPManager.getBalance();

        if (playerWallet < stackSize) {
            main.textPanel.addPara(Strings.get("poker5.insufficient_funds"), Color.RED);
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

        PokerGame5.PokerState5 state = pokerGame.getState();

        if (state.stacks[PokerGame5.HUMAN_PLAYER_INDEX] <= 0 &&
            state.pot == 0 && state.round != PokerRound.SHOWDOWN) {
            endHandInPlace();
            return;
        }

        currentDelegate = new PokerDialogDelegate5(pokerGame, main.getDialog(), null, this::handlePoker5PanelDismissed, this);

        main.getDialog().showCustomVisualDialog(1000f, 700f, currentDelegate);
    }

    private void handlePoker5PanelDismissed() {
        if (currentDelegate == null) return;

        if (currentDelegate.getPendingSuspend()) {
            suspendGame();
            return;
        }

        if (currentDelegate.getPendingHowToPlay()) {
            main.help.showPokerHelp("poker5_back_action");
            return;
        }

        if (currentDelegate.getPendingFlipTable()) {
            handleLeaveTable();
            if (handsPlayedThisSession < MIN_HANDS_BEFORE_LEAVE) {
                MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
                mem.set(POKER5_COOLDOWN_KEY, Global.getSector().getClock().getTimestamp());
                main.getTextPanel().addPara(Strings.get("poker_suspend.dealer_note_early"), Color.YELLOW);
            }
        }
    }

    public void processPlayerActionInPlace(PokerAction action, int raiseAmount, PokerDialogDelegate5 delegate) {
        if (pokerGame == null) return;

        pokerGame.processPokerAction(PokerGame5.HUMAN_PLAYER_INDEX, action, raiseAmount);

        processAITurnsInPlace(delegate);
    }

    public void processAITurnsInPlace(PokerDialogDelegate5 delegate) {
        if (pokerGame == null) return;

        PokerGame5.PokerState5 state = pokerGame.getState();

        if (state.round == PokerRound.SHOWDOWN) {
            pokerGame.determineWinners();
            delegate.refreshAfterStateChange(pokerGame);
            return;
        }

        if (state.currentPlayerIndex == PokerGame5.HUMAN_PLAYER_INDEX ||
            !pokerGame.canAct(state.currentPlayerIndex)) {
            delegate.refreshAfterStateChange(pokerGame);
            return;
        }

        int aiPlayerIndex = state.currentPlayerIndex;
        PokerAI5 ai = pokerGame.getAI(aiPlayerIndex);
        if (ai == null) {
            delegate.refreshAfterStateChange(pokerGame);
            return;
        }

        PokerAI5.AIResponse response = ai.decideAction(aiPlayerIndex, state);
        pokerGame.processAIPokerAction(aiPlayerIndex, response);

        state.lastPokerActions[aiPlayerIndex] = formatActionText(response, aiPlayerIndex);

        state = pokerGame.getState();

        if (state.round == PokerRound.SHOWDOWN) {
            pokerGame.determineWinners();
            delegate.refreshAfterStateChange(pokerGame);
            return;
        }

        if (state.currentPlayerIndex != PokerGame5.HUMAN_PLAYER_INDEX &&
            pokerGame.canAct(state.currentPlayerIndex)) {
            delegate.startAITurn(state.currentPlayerIndex);
        } else {
            delegate.refreshAfterStateChange(pokerGame);
        }
    }

    private String formatActionText(PokerAI5.AIResponse response, int playerIndex) {
        return switch (response.action) {
            case FOLD -> Strings.get("poker_panel5.action_fold");
            case CHECK -> Strings.get("poker_panel5.action_check");
            case CALL -> Strings.format("poker_panel5.action_call", pokerGame.getCallAmount(playerIndex));
            case RAISE -> Strings.format("poker_panel5.action_raise", response.raiseAmount);
            case ALL_IN -> Strings.format("poker_panel5.action_all_in", response.raiseAmount);
        };
    }

    public void startNextHandInPlace(PokerDialogDelegate5 delegate) {
        if (pokerGame == null) return;

        PokerGame5.PokerState5 state = pokerGame.getState();

        if (!pokerGame.canStartNewHand()) {
            delegate.closeDialog();
            handleLeaveTable();
            return;
        }

        if (state.stacks[PokerGame5.HUMAN_PLAYER_INDEX] <= 0) {
            delegate.closeDialog();
            handleLeaveTable();
            return;
        }

        pokerGame.startNewHand();
        handsPlayedThisSession++;

        delegate.updateGame(pokerGame);

        processAITurnsInPlace(delegate);
    }

    public void handleCleanLeaveInPlace(PokerDialogDelegate5 delegate) {
        if (pokerGame != null && pokerGame.getState().stacks[PokerGame5.HUMAN_PLAYER_INDEX] > 0) {
            int stackToReturn = pokerGame.getState().stacks[PokerGame5.HUMAN_PLAYER_INDEX];
            CasinoVIPManager.addToBalance(stackToReturn);
        }

        handsPlayedThisSession = 0;
        clearSuspendedGameMemory();
        pokerGame = null;

        delegate.closeDialog();
        main.showMenu();
    }

    private void endHandInPlace() {
        if (pokerGame == null) return;

        PokerGame5.PokerState5 state = pokerGame.getState();

        if (state.stacks[PokerGame5.HUMAN_PLAYER_INDEX] <= 0) {
            main.getTextPanel().addPara(Strings.get("poker_result.out_of_chips"), Color.RED);
            returnRemainingStack();
            clearSuspendedGameMemory();
            pokerGame = null;
            handsPlayedThisSession = 0;
            currentDelegate = null;
            main.getOptions().clearOptions();
            main.getOptions().addOption(Strings.get("poker_result.leave_table"), "back_menu5");
        }
    }

    private void returnRemainingStack() {
        if (pokerGame != null && pokerGame.getState().stacks[PokerGame5.HUMAN_PLAYER_INDEX] > 0) {
            int stackToReturn = pokerGame.getState().stacks[PokerGame5.HUMAN_PLAYER_INDEX];
            CasinoVIPManager.addToBalance(stackToReturn);
            main.getTextPanel().addPara(Strings.format("poker_result.cash_out_storm", stackToReturn), Color.GREEN);
        }
    }

    private void suspendGame() {
        if (pokerGame == null) return;
        PokerGame5.PokerState5 state = pokerGame.getState();
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        mem.set("$ipc_poker5_suspended", true);

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
                    mem.set("$ipc_poker5_hand_" + i + "_" + j, PokerUtils.cardToString(state.hands[i].get(j)));
                }
            }
        }

        mem.set("$ipc_poker5_community_count", state.communityCards.size());
        for (int i = 0; i < state.communityCards.size(); i++) {
            mem.set("$ipc_poker5_community_" + i, PokerUtils.cardToString(state.communityCards.get(i)));
        }

        mem.set("$ipc_poker5_suspend_time", Global.getSector().getClock().getTimestamp());

        main.textPanel.addPara(Strings.get("poker5.suspended_msg"), Color.YELLOW);
        main.options.clearOptions();
        main.options.addOption(Strings.get("common.leave"), "back_menu5");
    }

    public void restoreSuspendedGame() {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();

        if (!mem.contains("$ipc_poker5_pot")) {
            clearSuspendedGameMemory();
            main.textPanel.addPara(Strings.get("errors.corrupted_poker_data"), Color.RED);
            showPoker5Confirm();
            return;
        }

        pokerGame = PokerGame5Factory.restoreFromMemory(mem);

        if (pokerGame == null) {
            clearSuspendedGameMemory();
            main.textPanel.addPara(Strings.get("poker5.restore_failed"), Color.RED);
            showPoker5Confirm();
            return;
        }

        handsPlayedThisSession = mem.getInt("$ipc_poker5_hands_played");

        long suspendTime = mem.getLong("$ipc_poker5_suspend_time");
        float daysAway = Global.getSector().getClock().getElapsedDaysSince(suspendTime);

        main.textPanel.addPara(Strings.get("poker_suspend.dealer_stares"), Color.CYAN);
        if (daysAway >= 1) {
            main.textPanel.addPara(Strings.format("poker_suspend.gone_1_day", daysAway), Color.YELLOW);
        } else {
            main.textPanel.addPara(Strings.format("poker_suspend.gone_hours", daysAway * 24), Color.YELLOW);
        }

        main.options.clearOptions();
        main.options.addOption(Strings.get("poker_resume.continue"), "poker5_resume_continue");
        main.options.addOption(Strings.get("poker_resume.wait"), "poker5_resume_wait");
    }

    public boolean hasSuspendedPoker5() {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        return mem.getBoolean("$ipc_poker5_suspended");
    }

    private void clearSuspendedGameMemory() {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        mem.unset("$ipc_poker5_suspended");
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

    private void handleLeaveTable() {
        returnRemainingStack();

        handsPlayedThisSession = 0;
        clearSuspendedGameMemory();
        pokerGame = null;
        currentDelegate = null;
        main.showMenu();
    }

    private interface OptionHandler {
        void handle(String option);
    }
}
