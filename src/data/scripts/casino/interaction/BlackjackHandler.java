package data.scripts.casino.interaction;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import data.scripts.casino.CasinoVIPManager;
import data.scripts.casino.Strings;
import data.scripts.casino.BlackjackDialogDelegate;
import data.scripts.casino.BlackjackGame;
import data.scripts.casino.BlackjackGame.Action;
import data.scripts.casino.BlackjackGame.GameState;

import java.awt.Color;
import java.util.Map;
import java.util.HashMap;
import java.util.function.Predicate;

public class BlackjackHandler {

    private final CasinoInteraction main;
    private BlackjackGame blackjackGame;
    private BlackjackDialogDelegate currentDelegate;

    private static final int COOLDOWN_DAYS = 1;

    private int handsPlayedThisSession = 0;
    private static final String BLACKJACK_COOLDOWN_KEY = "$ipc_blackjack_cooldown_until";

    private final Map<String, OptionHandler> handlers = new HashMap<>();
    private final Map<Predicate<String>, OptionHandler> predicateHandlers = new HashMap<>();

    public BlackjackHandler(CasinoInteraction main) {
        this.main = main;
        initializeHandlers();
    }

    private void initializeHandlers() {
        handlers.put("blackjack_play", option -> showBlackjackConfirm());
        handlers.put("blackjack_how_to", option -> main.help.showBlackjackHelp());
        handlers.put("blackjack_deal", option -> setupGame());
        handlers.put("blackjack_new_hand", option -> startNewHand());
        handlers.put("blackjack_suspend", option -> suspendGame());
        handlers.put("blackjack_leave", option -> handleLeaveTable());
        handlers.put("blackjack_resume_continue", option -> {
            clearSuspendedGameMemory();
            showBlackjackVisualPanel();
        });
        handlers.put("blackjack_resume_wait", option -> main.showMenu());
        handlers.put("blackjack_back_to_menu", option -> handleBackToMenu());
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

    public void showBlackjackConfirm() {
        main.options.clearOptions();

        if (hasSuspendedBlackjack()) {
            main.setState(CasinoInteraction.State.BLACKJACK);
            main.textPanel.addPara(Strings.get("blackjack.resuming"), Color.YELLOW);
            restoreSuspendedGame();
            return;
        }

        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();

        if (mem.contains(BLACKJACK_COOLDOWN_KEY)) {
            long cooldownStart = mem.getLong(BLACKJACK_COOLDOWN_KEY);
            float elapsedDays = Global.getSector().getClock().getElapsedDaysSince(cooldownStart);
            if (elapsedDays < COOLDOWN_DAYS) {
                float daysRemaining = COOLDOWN_DAYS - elapsedDays;
                main.textPanel.addPara(Strings.get("blackjack.dealer_eyes_coldly"), Color.RED);
                main.textPanel.addPara(Strings.get("blackjack.left_early_warning"), Color.YELLOW);
                main.textPanel.addPara(Strings.format("blackjack.return_after_days", daysRemaining), Color.GRAY);
                main.options.addOption(Strings.get("common.back"), "back_menu");
                return;
            } else {
                mem.unset(BLACKJACK_COOLDOWN_KEY);
            }
        }

        setupGame();
    }

    public void setupGame() {
        int currentBalance = CasinoVIPManager.getBalance();
        int availableCredit = CasinoVIPManager.getAvailableCredit();
        int minBet = 100;

        if (currentBalance < minBet && availableCredit < minBet) {
            main.textPanel.addPara(Strings.format("blackjack.no_credit", minBet), Color.RED);
            main.textPanel.addPara(Strings.get("blackjack.please_topup"), Color.YELLOW);
            main.options.addOption(Strings.get("common.back"), "back_menu");
            return;
        }

        int stackSize = Math.max(availableCredit, 0);
        if (stackSize < minBet) {
            main.textPanel.addPara(Strings.format("blackjack.insufficient_credit", stackSize, minBet), Color.RED);
            main.textPanel.addPara(Strings.get("blackjack.please_topup"), Color.YELLOW);
            main.options.addOption(Strings.get("common.back"), "back_menu");
            return;
        }

        handsPlayedThisSession = 0;

        int creditCeiling = CasinoVIPManager.getCreditCeiling();
        int originalBalance = Math.max(currentBalance, 0);

        CasinoVIPManager.addToBalance(-stackSize);

        blackjackGame = new BlackjackGame(stackSize);
        blackjackGame.getState().originalBalance = originalBalance;
        blackjackGame.getState().creditCeiling = creditCeiling;
        blackjackGame.getState().creditBorrowed = Math.max(0, stackSize - originalBalance);
        blackjackGame.getState().overdraftEnabled = false;
        blackjackGame.startNewHand();

        showBlackjackVisualPanel();
    }

    private void showBlackjackVisualPanel() {
        if (blackjackGame == null) return;

        currentDelegate = new BlackjackDialogDelegate(
            blackjackGame,
            main.getDialog(),
            null,
            this::handleBlackjackPanelDismissed,
            this
        );

        main.getDialog().showCustomVisualDialog(1000f, 700f, currentDelegate);
    }

    private void handleBlackjackPanelDismissed() {
        if (currentDelegate == null) return;

        if (currentDelegate.getPendingAction() != null) {
            Action action = currentDelegate.getPendingAction();
            processPlayerAction(action);
            return;
        }

        if (currentDelegate.getPendingNewHand()) {
            startNewHand();
            return;
        }

        if (currentDelegate.getPendingLeave()) {
            handleLeaveTable();
        }
    }

    private void processPlayerAction(Action action) {
        if (blackjackGame == null) return;

        switch (action) {
            case HIT -> blackjackGame.playerHit();
            case STAND -> blackjackGame.playerStand();
            case DOUBLE_DOWN -> blackjackGame.playerDoubleDown();
            case SPLIT -> blackjackGame.playerSplit();
        }

        GameState state = blackjackGame.getGameState();

        currentDelegate.refreshAfterStateChange(blackjackGame);

        if (state == GameState.RESULT) {
            handleResult();
        }
    }

    private void handleResult() {
        if (blackjackGame == null) return;

        BlackjackGame.GameStateData state = blackjackGame.getState();
        boolean isSplitGame = state.splitHands != null && !state.splitHands.isEmpty();

        if (isSplitGame) {
            handleSplitResult(state);
        } else {
            handleSingleResult(state);
        }
    }

    private void handleSplitResult(BlackjackGame.GameStateData state) {
        boolean dealerBust = state.dealerHand.isBust();

        int wins = 0, losses = 0, pushes = 0;
        if (state.splitHandResults != null) {
            for (int result : state.splitHandResults) {
                if (result > 0) wins++;
                else if (result < 0) losses++;
                else pushes++;
            }
        }

        int netResult = state.lastPotWon;

        if (wins == 0 && losses > 0 && pushes == 0 && netResult < 0) {
            main.textPanel.addPara(Strings.format("blackjack_result.split_all_bust", Math.abs(netResult)), Color.RED);
        } else if (dealerBust && netResult > 0) {
            main.textPanel.addPara(Strings.get("blackjack_result.split_dealer_bust"), Color.GREEN);
            main.textPanel.addPara(Strings.format("blackjack_result.split_summary", wins, losses, pushes), Color.YELLOW);
            main.textPanel.addPara(Strings.format("blackjack_result.win_stargems", netResult), Color.GREEN);
        } else {
            main.textPanel.addPara(Strings.format("blackjack_result.split_summary", wins, losses, pushes), Color.CYAN);
            if (netResult > 0) {
                main.textPanel.addPara(Strings.format("blackjack_result.win_stargems", netResult), Color.GREEN);
            } else if (netResult < 0) {
                main.textPanel.addPara(Strings.format("blackjack_result.lose_stargems", Math.abs(netResult)), Color.RED);
            } else {
                main.textPanel.addPara(Strings.get("blackjack_result.push"), Color.YELLOW);
            }
        }
    }

    private void handleSingleResult(BlackjackGame.GameStateData state) {
        if (state.playerHand.isBlackjack() && !state.dealerHand.isBlackjack()) {
            main.textPanel.addPara(Strings.get("blackjack_result.blackjack"), Color.CYAN);
        } else if (state.playerHand.isBust()) {
            main.textPanel.addPara(Strings.get("blackjack_result.player_bust"), Color.RED);
        } else if (state.dealerHand.isBust()) {
            main.textPanel.addPara(Strings.get("blackjack_result.dealer_bust"), Color.GREEN);
        } else if (state.lastPotWon > 0) {
            main.textPanel.addPara(Strings.format("blackjack_result.win_stargems", state.lastPotWon), Color.GREEN);
        } else if (state.lastPotWon < 0) {
            main.textPanel.addPara(Strings.format("blackjack_result.lose_stargems", Math.abs(state.lastPotWon)), Color.RED);
        } else {
            main.textPanel.addPara(Strings.get("blackjack_result.push"), Color.YELLOW);
        }
    }

    private void startNewHand() {
        showBlackjackConfirm();
    }

    private void suspendGame() {
        if (blackjackGame == null) return;
        BlackjackGame.GameStateData state = blackjackGame.getState();
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();

        mem.set("$ipc_suspended_game_type", "Blackjack");
        mem.set("$ipc_blackjack_pot", state.pot);
        mem.set("$ipc_blackjack_player_stack", state.playerStack);
        mem.set("$ipc_blackjack_current_bet", state.currentBet);
        mem.set("$ipc_blackjack_credit_borrowed", state.creditBorrowed);
        mem.set("$ipc_blackjack_hands_played", handsPlayedThisSession);
        mem.set("$ipc_blackjack_suspend_time", Global.getSector().getClock().getTimestamp());

        main.textPanel.addPara(Strings.get("blackjack_suspend.stand_up"), Color.YELLOW);
        main.textPanel.addPara(Strings.get("blackjack_suspend.dealer_eyebrow"), Color.CYAN);
        main.textPanel.addPara(Strings.get("blackjack_suspend.dont_be_long"), Color.GRAY);
        main.options.clearOptions();
        main.options.addOption(Strings.get("blackjack_suspend.leave"), "blackjack_leave");
    }

    public void restoreSuspendedGame() {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();

        if (!mem.contains("$ipc_blackjack_player_stack") || !mem.contains("$ipc_blackjack_suspend_time")) {
            clearSuspendedGameMemory();
            main.getTextPanel().addPara(Strings.get("errors.corrupted_blackjack_data"), Color.RED);
            showBlackjackConfirm();
            return;
        }

        int playerStack = mem.getInt("$ipc_blackjack_player_stack");
        int handsPlayed = mem.getInt("$ipc_blackjack_hands_played");
        int creditBorrowed = mem.contains("$ipc_blackjack_credit_borrowed") ? mem.getInt("$ipc_blackjack_credit_borrowed") : 0;

        handsPlayedThisSession = handsPlayed;

        blackjackGame = new BlackjackGame(playerStack);
        blackjackGame.getState().creditBorrowed = creditBorrowed;
        blackjackGame.getState().overdraftEnabled = false;

        long suspendTime = mem.getLong("$ipc_blackjack_suspend_time");
        float daysAway = Global.getSector().getClock().getElapsedDaysSince(suspendTime);

        main.getTextPanel().addPara(Strings.get("blackjack_suspend.dealer_stares"), Color.CYAN);
        if (daysAway >= 30) {
            main.getTextPanel().addPara(Strings.format("blackjack_suspend.gone_30_days", daysAway), Color.YELLOW);
        } else if (daysAway >= 7) {
            main.getTextPanel().addPara(Strings.format("blackjack_suspend.gone_7_days", daysAway), Color.YELLOW);
        } else if (daysAway >= 1) {
            main.getTextPanel().addPara(Strings.format("blackjack_suspend.gone_1_day", daysAway), Color.YELLOW);
        } else {
            main.getTextPanel().addPara(Strings.format("blackjack_suspend.gone_hours", daysAway * 24), Color.YELLOW);
        }
        main.getTextPanel().addPara(Strings.get("blackjack_suspend.cards_havent_moved"), Color.GRAY);

        main.options.clearOptions();
        main.options.addOption(Strings.get("blackjack_resume.continue"), "blackjack_resume_continue");
        main.options.addOption(Strings.get("blackjack_resume.wait"), "blackjack_resume_wait");
    }

    public boolean hasSuspendedBlackjack() {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        String suspendedGameType = mem.getString("$ipc_suspended_game_type");
        return "Blackjack".equals(suspendedGameType);
    }

    private void clearSuspendedGameMemory() {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        mem.unset("$ipc_suspended_game_type");
        mem.unset("$ipc_blackjack_pot");
        mem.unset("$ipc_blackjack_player_stack");
        mem.unset("$ipc_blackjack_current_bet");
        mem.unset("$ipc_blackjack_credit_borrowed");
        mem.unset("$ipc_blackjack_hands_played");
        mem.unset("$ipc_blackjack_suspend_time");
    }

    private void handleLeaveTable() {
        if (blackjackGame != null && blackjackGame.getState().playerStack > 0) {
            int stackToReturn = blackjackGame.getState().playerStack;
            CasinoVIPManager.addToBalance(stackToReturn);
            main.getTextPanel().addPara(Strings.get("blackjack_result.flip_table_crash"), Color.RED);
            main.getTextPanel().addPara(Strings.get("blackjack_result.typical_player"), Color.GRAY);
            main.getTextPanel().addPara(Strings.format("blackjack_result.cash_out_storm", stackToReturn), Color.GREEN);
            main.getTextPanel().addPara(Strings.format("blackjack_result.new_balance", CasinoVIPManager.getBalance()), Color.YELLOW);
            blackjackGame.getState().playerStack = 0;
        }

        handsPlayedThisSession = 0;
        clearSuspendedGameMemory();
        blackjackGame = null;
        currentDelegate = null;

        main.showMenu();
    }

    private void handleBackToMenu() {
        if (blackjackGame != null && blackjackGame.getState().playerStack > 0) {
            int stackToReturn = blackjackGame.getState().playerStack;
            CasinoVIPManager.addToBalance(stackToReturn);
            blackjackGame.getState().playerStack = 0;
        }

        handsPlayedThisSession = 0;
        clearSuspendedGameMemory();
        blackjackGame = null;
        currentDelegate = null;

        main.showMenu();
    }

    public void processPlayerActionInPlace(Action action, BlackjackDialogDelegate delegate) {
        if (blackjackGame == null) return;

        switch (action) {
            case HIT -> blackjackGame.playerHit();
            case STAND -> blackjackGame.playerStand();
            case DOUBLE_DOWN -> blackjackGame.playerDoubleDown();
            case SPLIT -> blackjackGame.playerSplit();
        }

        GameState state = blackjackGame.getGameState();

        delegate.refreshAfterStateChange(blackjackGame);

        if (state == GameState.RESULT) {
        }
    }

    public void startNewHandInPlace(BlackjackDialogDelegate delegate) {
        if (blackjackGame == null) return;

        BlackjackGame.GameStateData state = blackjackGame.getState();
        if (state.playerStack < 100) {
            delegate.closeDialog();
            handleLeaveTable();
            return;
        }

        blackjackGame.startNewHand();
        handsPlayedThisSession++;

        delegate.updateGame(blackjackGame);
    }

    public void showHowToPlay(BlackjackDialogDelegate delegate) {
        delegate.closeDialog();
        main.help.showBlackjackHelp();
    }

    public void placeBetInPlace(int betSize, BlackjackDialogDelegate delegate) {
        if (blackjackGame == null) return;

        BlackjackGame.GameStateData state = blackjackGame.getState();
        if (state.state != GameState.BETTING) return;

        if (state.playerStack < betSize) {
            return;
        }

        boolean success = blackjackGame.placeBet(betSize);
        if (success) {
            delegate.updateGame(blackjackGame);
        }
    }
}