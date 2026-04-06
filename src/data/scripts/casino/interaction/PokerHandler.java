package data.scripts.casino.interaction;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import data.scripts.casino.CasinoConfig;
import data.scripts.casino.CasinoVIPManager;
import data.scripts.casino.shared.CasinoFinancials;
import data.scripts.casino.cards.poker2.PokerDialogDelegate;
import data.scripts.casino.cards.poker2.PokerGame;
import static data.scripts.casino.cards.pokerShared.PokerAICommon.InternalAction;
import data.scripts.casino.cards.pokerShared.PokerAction;
import data.scripts.casino.cards.pokerShared.PokerAICommon;
import data.scripts.casino.cards.pokerShared.PokerHandEvaluator;
import data.scripts.casino.cards.pokerShared.PokerRound;
import data.scripts.casino.cards.Card;
import data.scripts.casino.Strings;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

public class PokerHandler {

    private final CasinoInteraction main;
    private PokerGame pokerGame;
    private PokerDialogDelegate currentDelegate;

    private static final int COOLDOWN_DAYS = 1;
    private static final int MIN_HANDS_BEFORE_LEAVE = 3;

    protected int playerWallet;

    private int pendingStackSize = 0;
    private int pendingOverdraftAmount = 0;

private int handsPlayedThisSession = 0;
    private String pendingOpponentAction = "";
    private String pendingPlayerAction = "";
    private static final String POKER_COOLDOWN_KEY = "$ipc_poker_cooldown_until";

    private final Map<String, OptionHandler> handlers = new HashMap<>();
    private final Map<Predicate<String>, OptionHandler> predicateHandlers = new HashMap<>();
    
    public PokerHandler(CasinoInteraction main) {
        this.main = main;
        initializeHandlers();
    }
    
    private void initializeHandlers() {
        handlers.put("play", option -> showPokerConfirm());
        handlers.put("confirm_poker_ante", option -> setupGame());
        handlers.put("next_hand", option -> startNextHand());
        handlers.put("how_to_poker", option -> main.help.showPokerHelp("poker_back_action"));
        handlers.put("poker_call", option -> handlePokerCall());
        handlers.put("poker_check", option -> handlePokerCheck());
        handlers.put("poker_fold", option -> handlePokerFold());
        handlers.put("poker_raise_menu", option -> showRaiseOptions());
handlers.put("poker_back_action", option -> showPokerVisualPanel());
        handlers.put("poker_suspend", option -> suspendGame());
        handlers.put("poker_back_to_menu", option -> handleLeaveTable());
        handlers.put("poker_leave_now", option -> handleSuspendLeave());
        handlers.put("poker_abandon_confirm", option -> showAbandonConfirm());
        handlers.put("poker_abandon_confirm_leave", option -> handleLeaveTable());
        handlers.put("poker_abandon_cancel", option -> showPokerVisualPanel());
        handlers.put("poker_resume_continue", option -> {
            clearSuspendedGameMemory();
            showPokerVisualPanel();
        });
        handlers.put("poker_resume_wait", option -> main.showMenu());
        handlers.put("suspend_abandon_confirm", option -> showSuspendAbandonConfirm());
        handlers.put("suspend_abandon_leave", option -> abandonSuspendedGame());
        handlers.put("suspend_abandon_cancel", option -> {
            main.textPanel.addPara(Strings.get("poker_suspend.sit_back_down"), Color.CYAN);
            showPokerVisualPanel();
        });
        handlers.put("back_menu", option -> {
            pokerGame = null;
            handsPlayedThisSession = 0;
            currentDelegate = null;
            pendingStackSize = 0;
            pendingOverdraftAmount = 0;
            clearSuspendedGameMemory();
            main.showMenu();
        });
        handlers.put("confirm_overdraft", option -> processOverdraftConfirmation());
        handlers.put("cancel_overdraft", option -> cancelOverdraft());
        
        predicateHandlers.put(option -> option.startsWith("poker_raise_"), option -> {
            int amt = Integer.parseInt(option.replace("poker_raise_", ""));
            performRaise(amt);
        });
        
        predicateHandlers.put(option -> option.startsWith("poker_stack_"), option -> {
            String stackStr = option.replace("poker_stack_", "");
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
        
        handlePokerAction(option);
    }

    public void showPokerConfirm() {
        main.options.clearOptions();

        if (hasSuspendedPoker()) {
            main.setState(CasinoInteraction.State.POKER);
            main.textPanel.addPara(Strings.get("poker.resuming"), Color.YELLOW);
            restoreSuspendedGame();
            return;
        }

        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();

        if (mem.contains(POKER_COOLDOWN_KEY)) {
            long cooldownStart = mem.getLong(POKER_COOLDOWN_KEY);
            float elapsedDays = Global.getSector().getClock().getElapsedDaysSince(cooldownStart);
            if (elapsedDays < COOLDOWN_DAYS) {
                float daysRemaining = COOLDOWN_DAYS - elapsedDays;
                main.textPanel.addPara(Strings.get("poker.dealer_eyes_coldly"), Color.RED);
                main.textPanel.addPara(Strings.get("poker.left_early_warning"), Color.YELLOW);
                main.textPanel.addPara(Strings.format("poker.return_after_days", daysRemaining), Color.GRAY);
                main.options.addOption(Strings.get("common.back"), "back_menu");
                return;
            } else {
                mem.unset(POKER_COOLDOWN_KEY);
            }
        }

        main.textPanel.addPara(Strings.get("poker.choose_stack"), Color.YELLOW);

        displayFinancialInfo();

        int currentBalance = CasinoVIPManager.getBalance();
        int availableCredit = CasinoVIPManager.getAvailableCredit();
        int[] stackSizes = CasinoConfig.POKER_STACK_SIZES;
        String[] stackLabels = {
            Strings.get("poker.stack_small"),
            Strings.get("poker.stack_medium"),
            Strings.get("poker.stack_large"),
            Strings.get("poker.stack_huge")
        };

        for (int i = 0; i < stackSizes.length && i < stackLabels.length; i++) {
            if (currentBalance >= stackSizes[i] || availableCredit >= stackSizes[i]) {
                main.options.addOption(Strings.format("poker.stack_label", stackLabels[i], stackSizes[i]), "poker_stack_" + stackSizes[i]);
            }
        }

        int minStackSize = CasinoConfig.POKER_STACK_SIZES.length > 0 ? CasinoConfig.POKER_STACK_SIZES[0] : 1000;
        int minRequiredGems = minStackSize / 100;

        if (availableCredit <= 0) {
            main.textPanel.addPara(Strings.format("poker.credit_exhausted", minStackSize), Color.RED);
            main.textPanel.addPara(Strings.get("poker.please_topup"), Color.YELLOW);
        } else if (availableCredit < minStackSize) {
            main.textPanel.addPara(Strings.format("poker.insufficient_credit_stack", availableCredit, minStackSize), Color.RED);
            main.textPanel.addPara(Strings.get("poker.please_topup"), Color.YELLOW);
        } else if (currentBalance < minRequiredGems && availableCredit >= minRequiredGems) {
            main.textPanel.addPara(Strings.get("poker.balance_below_minimum"), Color.YELLOW);
        }

        main.options.addOption(Strings.get("poker.how_to_play"), "how_to_poker");
        main.options.addOption(Strings.get("common.back"), "back_menu");
        main.setState(CasinoInteraction.State.POKER);
    }

    private void displayFinancialInfo() {
        CasinoFinancials.displayFinancialInfo(main.textPanel);
    }

    public void setupGame() {
        int defaultStack = CasinoConfig.POKER_STACK_SIZES.length > 0 ? CasinoConfig.POKER_STACK_SIZES[0] : 10000;
        setupGame(defaultStack);
    }
    
    public void setupGame(int stackSize) {
        playerWallet = CasinoVIPManager.getBalance();
        int availableCredit = CasinoVIPManager.getAvailableCredit();
        boolean overdraftAvailable = CasinoVIPManager.isOverdraftAvailable();
        
        if (playerWallet < stackSize) {
            if (!overdraftAvailable) {
                showVIPPromotionForPoker(stackSize);
                return;
            }
            
            if (availableCredit < stackSize) {
                main.textPanel.addPara(Strings.format("poker.not_enough_credit", playerWallet, availableCredit, stackSize), Color.RED);
                showPokerConfirm();
                return;
            }
            
            int overdraftAmount = stackSize - playerWallet;
            pendingStackSize = stackSize;
            pendingOverdraftAmount = overdraftAmount;
            showOverdraftConfirmation(stackSize, overdraftAmount);
            return;
        }
        
        startGameWithStack(stackSize);
    }
    
    private void showOverdraftConfirmation(int stackSize, int overdraftAmount) {
        main.options.clearOptions();
        main.textPanel.addPara(Strings.get("poker.overdraft_title"), Color.RED);
        main.textPanel.addPara("");
        main.textPanel.addPara(Strings.format("poker.overdraft_current_balance", CasinoVIPManager.getBalance()), Color.YELLOW);
        main.textPanel.addPara(Strings.format("poker.overdraft_requested", stackSize), Color.WHITE);
        main.textPanel.addPara("");
        main.textPanel.addPara(Strings.get("poker.overdraft_details"), Color.CYAN);
        main.textPanel.addPara(Strings.format("poker.overdraft_amount", overdraftAmount), Color.RED);
        main.textPanel.addPara(Strings.format("poker.overdraft_interest", (int)(CasinoConfig.VIP_DAILY_INTEREST_RATE * 100)), Color.YELLOW);
        main.textPanel.addPara(Strings.format("poker.overdraft_new_balance", CasinoVIPManager.getBalance() - stackSize), Color.GRAY);
        main.textPanel.addPara("");
        main.textPanel.addPara(Strings.get("poker.overdraft_warning"), Color.RED);
        main.textPanel.addPara("");
        
        main.options.addOption(Strings.get("poker.confirm_overdraft_btn"), "confirm_overdraft");
        main.options.addOption(Strings.get("poker.cancel_overdraft_btn"), "cancel_overdraft");
    }
    
    private void processOverdraftConfirmation() {
        if (pendingStackSize <= 0) {
            showPokerConfirm();
            return;
        }
        
        main.textPanel.addPara(Strings.format("poker.overdraft_authorized", pendingOverdraftAmount), Color.YELLOW);
        
        startGameWithStack(pendingStackSize);
        
        pendingStackSize = 0;
        pendingOverdraftAmount = 0;
    }
    
    private void cancelOverdraft() {
        pendingStackSize = 0;
        pendingOverdraftAmount = 0;
        main.textPanel.addPara(Strings.get("poker.overdraft_cancelled"), Color.GRAY);
        showPokerConfirm();
    }
    
private void startGameWithStack(int stackSize) {
        CasinoVIPManager.addToBalance(-stackSize);
        
        pokerGame = new PokerGame(stackSize, stackSize);
        
        handsPlayedThisSession = 0;
        
        showPokerVisualPanel();
    }
    
    private void showVIPPromotionForPoker(int stackSize) {
        main.getOptions().clearOptions();
        main.textPanel.addPara(Strings.get("poker.insufficient_title"), Color.RED);
        main.textPanel.addPara("");
        main.textPanel.addPara(Strings.get("poker.insufficient_msg"), Color.YELLOW);
        main.textPanel.addPara(Strings.format("poker.current_balance", CasinoVIPManager.getBalance()), Color.GRAY);
        main.textPanel.addPara(Strings.format("poker.required_for_stack", stackSize, stackSize), Color.GRAY);
        main.textPanel.addPara("");
        main.textPanel.addPara(Strings.get("vip_promo.credit_facility_title"), Color.CYAN);
        main.textPanel.addPara(Strings.get("vip_promo.overdraft_vip_only"), Color.WHITE);
        main.textPanel.addPara("");
        main.textPanel.addPara(Strings.get("poker.vip_benefits_title"), Color.GREEN);
        main.textPanel.addPara(Strings.get("poker.benefit_overdraft"), Color.GRAY);
        main.textPanel.addPara(Strings.format("poker.benefit_daily", CasinoConfig.VIP_DAILY_REWARD), Color.GRAY);
        main.textPanel.addPara(Strings.format("poker.benefit_interest", (int)(CasinoConfig.VIP_DAILY_INTEREST_RATE * 100)), Color.GRAY);
        main.textPanel.addPara(Strings.get("poker.benefit_ceiling"), Color.GRAY);
        main.textPanel.addPara("");
        main.textPanel.addPara(Strings.get("poker.purchase_vip_prompt"), Color.YELLOW);

        main.getOptions().addOption(Strings.get("poker.go_topup"), "topup_menu");
        main.getOptions().addOption(Strings.get("common.back"), "back_menu");
    }

private String formatBB(int amount, int bigBlind) {
        return bigBlind > 0 ? String.format("%.1f", (float) amount / bigBlind) : "0";
    }

    private void showPokerVisualPanel() {
        if (pokerGame == null) return;
        main.options.clearOptions();
        PokerGame.PokerState state = pokerGame.getState();
        
        if (state.playerStack < state.bigBlind && state.pot == 0 && state.round != PokerRound.SHOWDOWN) {
            endHand();
            return;
        }
        
        currentDelegate = new PokerDialogDelegate(pokerGame, main.getDialog(), null, this::handlePokerPanelDismissed, this);
        
        if (!pendingOpponentAction.isEmpty()) {
            currentDelegate.setLastOpponentAction(pendingOpponentAction);
            pendingOpponentAction = "";
        }
        
        if (!pendingPlayerAction.isEmpty()) {
            currentDelegate.setLastPlayerAction(pendingPlayerAction);
            pendingPlayerAction = "";
        }
        
        main.getDialog().showCustomVisualDialog(1000f, 700f, currentDelegate);
    }
    
    private void handlePokerPanelDismissed() {
        if (currentDelegate == null) return;
        
        if (currentDelegate.getPendingSuspend()) {
            suspendGame();
            return;
        }
        
        if (currentDelegate.getPendingHowToPlay()) {
            main.help.showPokerHelp("poker_back_action");
            return;
        }
        
        if (currentDelegate.getPendingFlipTable()) {
            handleLeaveTable();
            if (handsPlayedThisSession < MIN_HANDS_BEFORE_LEAVE) {
                MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
                mem.set(POKER_COOLDOWN_KEY, Global.getSector().getClock().getTimestamp());
                main.getTextPanel().addPara(Strings.get("poker_suspend.dealer_note_early"), Color.YELLOW);
            }
        }
    }

    public void updateUI() {
        if (pokerGame == null) return;
        PokerGame.PokerState state = pokerGame.getState();
        if (state.playerStack <= 0 && (state.pot == 0 || state.round == PokerRound.SHOWDOWN)) {
            endHand();
            return;
        }
        int bigBlind = state.bigBlind;

        main.getOptions().clearOptions();
        main.getTextPanel().addPara("------------------------------------------------");
        main.getTextPanel().addPara(Strings.format("poker_ui.pot_bb", String.valueOf(state.pot), formatBB(state.pot, bigBlind), String.valueOf(bigBlind)), Color.GREEN);
        main.getTextPanel().highlightInLastPara(
            String.valueOf(state.pot), formatBB(state.pot, bigBlind), String.valueOf(bigBlind));
        main.getTextPanel().setHighlightColorsInLastPara(Color.GREEN, Color.GREEN, Color.GREEN);
        
        main.getTextPanel().addPara(Strings.format("poker_ui.your_stack_bb", String.valueOf(state.playerStack), formatBB(state.playerStack, bigBlind)), Color.CYAN);
        
        main.getTextPanel().addPara(Strings.format("poker_ui.opponent_stack_bb", String.valueOf(state.opponentStack), formatBB(state.opponentStack, bigBlind)), Color.ORANGE);
        
        displayColoredCardsOnOneLine(state.playerHand, Strings.get("poker_ui.your_hand"), Color.CYAN);
        if (!state.communityCards.isEmpty()) {
            displayColoredCardsOnOneLine(state.communityCards, Strings.get("poker_ui.community"), Color.YELLOW);
        }
        
        int callAmount = state.opponentBet - state.playerBet;
        if (callAmount > 0) {
            if (state.playerStack >= callAmount) {
                main.getOptions().addOption(Strings.format("poker_ui.call", callAmount), "poker_call");
            } else if (state.playerStack > 0) {
                main.getOptions().addOption(Strings.format("poker_ui.call_allin", state.playerStack), "poker_call");
            } else {
                main.getOptions().addOption(Strings.get("poker_ui.call_zero"), "poker_call");
            }
            main.getOptions().addOption(Strings.get("poker_ui.fold"), "poker_fold");
        } else {
            main.getOptions().addOption(Strings.get("poker_ui.check"), "poker_check");
        }
        
        if (state.playerStack > 0 && state.opponentBet - state.playerBet < state.playerStack && !state.opponentDeclaredAllIn) {
            main.getOptions().addOption(Strings.get("poker_ui.raise"), "poker_raise_menu");
        }
        
        main.getOptions().addOption(Strings.get("poker.how_to_play"), "how_to_poker");
        main.getOptions().addOption(Strings.get("poker_ui.suspend"), "poker_suspend");
        main.getOptions().addOption(Strings.get("poker_ui.flip_table"), "poker_abandon_confirm");
    }

private void startNextHand() {
        if (pokerGame != null) {
            PokerGame.PokerState state = pokerGame.getState();
            if (state.playerStack <= 0 || state.opponentStack <= 0) {
                 main.textPanel.addPara(Strings.get("poker_ui.chips_out_new_game"), Color.YELLOW);
                 setupGame(state.playerStack > 0 ? state.playerStack : 1000);
                 return;
            }
            pokerGame.startNewHand();
            handsPlayedThisSession++;
            showPokerVisualPanel();
        }
    }

    public void handlePokerCall() {
        if (pokerGame == null) return;
        PokerGame.PokerState state = pokerGame.getState();
        int callAmount = Math.min(state.opponentBet - state.playerBet, state.playerStack);
        pokerGame.processPlayerAction(PokerAction.CALL, 0);
        main.getTextPanel().addPara(Strings.format("poker_actions.you_call_stargems", callAmount), Color.CYAN);
        updateGameState();
    }

    public void handlePokerCheck() {
        if (pokerGame == null) return;
        pokerGame.processPlayerAction(PokerAction.CHECK, 0);
        main.getTextPanel().addPara(Strings.get("poker_actions.you_check_dot"), Color.CYAN);
        updateGameState();
    }

    public void handlePokerFold() {
        if (pokerGame == null) return;
        pokerGame.processPlayerAction(PokerAction.FOLD, 0);
        main.getTextPanel().addPara(Strings.get("poker_actions.you_fold_dot"), Color.GRAY);
        updateGameState();
    }
    
    public void handlePokerAction(String option) {
        if (option.startsWith("poker_raise_")) {
            int amt = Integer.parseInt(option.replace("poker_raise_", ""));
            performRaise(amt);
        }
    }
    
    private void updateGameState() {
        if (pokerGame == null) return;
        PokerGame.PokerState state = pokerGame.getState();
        
        if (state.round == PokerRound.SHOWDOWN) {
            determineWinner();
            return;
        }
        
        if (state.currentPlayer == PokerGame.CurrentPlayer.OPPONENT) {
PokerAICommon.AIResponse response = pokerGame.getOpponentAction();
             
switch(response.action) {
                  case CALL: 
                      main.getTextPanel().addPara(Strings.get("poker_actions.opponent_calls_dot"), Color.YELLOW); break;
                  case RAISE: 
                      main.getTextPanel().addPara(Strings.format("poker_actions.opponent_raises_dot", response.raiseAmount), Color.YELLOW); break;
                  case CHECK: 
                      main.getTextPanel().addPara(Strings.get("poker_actions.opponent_checks_dot"), Color.YELLOW); break;
                  case FOLD: 
                      main.getTextPanel().addPara(Strings.get("poker_actions.opponent_folds_dot"), Color.CYAN); break;
              }
             
if (response.action == InternalAction.FOLD && state.playerBet > state.opponentBet) {
                  pokerGame.getAI().trackAIFoldedToPlayerBet();
              }
             
             pokerGame.processOpponentAction(response);
             
state = pokerGame.getState();
             if (state.round == PokerRound.SHOWDOWN) {
                 determineWinner();
                 return;
             }
        }
        
        updateUI();
    }

    private void showRaiseOptions() {
        if (pokerGame == null) return;
        main.getOptions().clearOptions();
        PokerGame.PokerState state = pokerGame.getState();
        
        int playerStackAvailable = state.playerStack;
        int bigBlind = state.bigBlind;
        int potSize = state.pot;
        
        java.util.function.BiConsumer<Integer, String> addBetOption = (amount, label) -> {
            if (amount > 0 && amount <= playerStackAvailable) {
                float bbAmount = bigBlind > 0 ? (float)amount / bigBlind : 0;
                String optionText = label + " (" + amount + " / " + String.format("%.1f", bbAmount) + " BB)";
                main.getOptions().addOption(optionText, "poker_raise_" + amount);
            }
        };
        
        addBetOption.accept(bigBlind, Strings.get("poker_ui.raise_bb"));

        int halfPot = potSize / 2;
        if (halfPot > bigBlind) {
            addBetOption.accept(halfPot, Strings.get("poker_ui.raise_half_pot"));
        }

        if (potSize > halfPot) {
            addBetOption.accept(potSize, Strings.get("poker_ui.raise_pot"));
        }
        
        int twoXPot = potSize * 2;
        if (twoXPot > potSize && twoXPot <= playerStackAvailable) {
            addBetOption.accept(twoXPot, Strings.get("poker_ui.raise_2x_pot"));
        }
        
        if (playerStackAvailable > 0) {
            float allInBB = bigBlind > 0 ? (float)playerStackAvailable / bigBlind : 0;
            String allInText = Strings.format("poker_ui.raise_allin", playerStackAvailable, allInBB);
            main.getOptions().addOption(allInText, "poker_raise_" + playerStackAvailable);
        }
        
        main.getOptions().addOption(Strings.get("poker_ui.back"), "poker_back_action");
    }

    private void performRaise(int amt) {
        if (pokerGame == null) return;
        PokerGame.PokerState state = pokerGame.getState();
        int totalBet = state.playerBet + amt;
        pokerGame.processPlayerAction(PokerAction.RAISE, amt);
        main.getTextPanel().addPara(Strings.format("poker_actions.you_raise_stargems", totalBet), Color.CYAN);
        updateGameState();
    }
    
    private void suspendGame() {
        if (pokerGame == null) return;
        PokerGame.PokerState state = pokerGame.getState();
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        mem.set("$ipc_poker_suspended", true);

        mem.set("$ipc_poker_pot_size", state.pot);
        mem.set("$ipc_poker_player_bet", state.playerBet);
        mem.set("$ipc_poker_opponent_bet", state.opponentBet);
        mem.set("$ipc_poker_player_stack", state.playerStack);
        mem.set("$ipc_poker_opponent_stack", state.opponentStack);
        mem.set("$ipc_poker_player_is_dealer", state.dealer == PokerGame.Dealer.PLAYER);
        mem.set("$ipc_poker_big_blind", state.bigBlind);
        
        mem.set("$ipc_poker_round", state.round.name());
        mem.set("$ipc_poker_current_player", state.currentPlayer.name());
        mem.set("$ipc_poker_player_has_acted", state.playerHasActed);
        mem.set("$ipc_poker_opponent_has_acted", state.opponentHasActed);
        mem.set("$ipc_poker_player_all_in", state.playerDeclaredAllIn);
        mem.set("$ipc_poker_opponent_all_in", state.opponentDeclaredAllIn);

        if (state.playerHand != null) {
            mem.set("$ipc_poker_player_hand_count", state.playerHand.size());
            for (int i = 0; i < state.playerHand.size(); i++) {
                mem.set("$ipc_poker_player_hand_" + i, PokerGame.cardToString(state.playerHand.get(i)));
            }
        }
        
        if (state.opponentHand != null) {
            mem.set("$ipc_poker_opponent_hand_count", state.opponentHand.size());
            for (int i = 0; i < state.opponentHand.size(); i++) {
                mem.set("$ipc_poker_opponent_hand_" + i, PokerGame.cardToString(state.opponentHand.get(i)));
            }
        }
        
        if (state.communityCards != null) {
            mem.set("$ipc_poker_community_count", state.communityCards.size());
            for (int i = 0; i < state.communityCards.size(); i++) {
                mem.set("$ipc_poker_community_" + i, PokerGame.cardToString(state.communityCards.get(i)));
            }
        }

        mem.set("$ipc_poker_hands_played", handsPlayedThisSession);
        mem.set("$ipc_poker_suspend_time", Global.getSector().getClock().getTimestamp());

        main.getTextPanel().addPara(Strings.get("poker_suspend.stand_up"), Color.YELLOW);
        main.getTextPanel().addPara(Strings.get("poker_suspend.dealer_eyebrow"), Color.CYAN);
        main.getTextPanel().addPara(Strings.get("poker_suspend.dont_be_long"), Color.GRAY);
        main.getOptions().clearOptions();
        main.getOptions().addOption(Strings.get("poker_suspend.leave"), "poker_leave_now");
    }

    private void showAbandonConfirm() {
        if (pokerGame == null) {
            main.showMenu();
            return;
        }
        PokerGame.PokerState state = pokerGame.getState();
        int stackValue = state.playerStack;
        
        main.getOptions().clearOptions();
        main.getTextPanel().addPara(Strings.get("poker_suspend.flip_table_title"), Color.RED);
        main.getTextPanel().addPara("");
        main.getTextPanel().addPara(Strings.get("poker_suspend.flip_table_msg"), Color.YELLOW);
        main.getTextPanel().addPara(Strings.format("poker_suspend.stack_returned", stackValue), Color.CYAN);
        main.getTextPanel().addPara("");
        main.getTextPanel().addPara(Strings.format("poker_suspend.early_leave_warning", MIN_HANDS_BEFORE_LEAVE), Color.ORANGE);
        main.getTextPanel().addPara("");
        main.getTextPanel().addPara(Strings.get("poker_suspend.consider_suspend"), Color.GRAY);
        
        main.getOptions().addOption(Strings.get("poker_suspend.yes_flip_table"), "poker_abandon_confirm_leave");
        main.getOptions().addOption(Strings.get("poker_suspend.go_back_game"), "poker_abandon_cancel");
    }

    private void showSuspendAbandonConfirm() {
        if (pokerGame == null) {
            main.showMenu();
            return;
        }
        PokerGame.PokerState state = pokerGame.getState();
        int stackValue = state.playerStack;
        
        main.getOptions().clearOptions();
        main.getTextPanel().addPara(Strings.get("poker_suspend.abandon_title"), Color.RED);
        main.getTextPanel().addPara("");
        main.getTextPanel().addPara(Strings.get("poker_suspend.abandon_msg"), Color.YELLOW);
        main.getTextPanel().addPara(Strings.format("poker_suspend.stack_returned", stackValue), Color.CYAN);
        main.getTextPanel().addPara("");
        main.getTextPanel().addPara(Strings.format("poker_suspend.early_leave_warning", MIN_HANDS_BEFORE_LEAVE), Color.ORANGE);
        main.getTextPanel().addPara("");
        main.getTextPanel().addPara(Strings.get("poker_suspend.could_return"), Color.GRAY);
        
        main.getOptions().addOption(Strings.get("poker_suspend.yes_abandon"), "suspend_abandon_leave");
        main.getOptions().addOption(Strings.get("poker_suspend.go_back_table"), "suspend_abandon_cancel");
    }

    public void restoreSuspendedGame() {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();

        if (!mem.contains("$ipc_poker_pot_size") || !mem.contains("$ipc_poker_suspend_time")) {
            clearSuspendedGameMemory();
            main.getTextPanel().addPara(Strings.get("errors.corrupted_poker_data"), Color.RED);
            showPokerConfirm();
            return;
        }

        int pot = mem.getInt("$ipc_poker_pot_size");
        int pBet = mem.getInt("$ipc_poker_player_bet");
        int oBet = mem.getInt("$ipc_poker_opponent_bet");
        int pStack = mem.getInt("$ipc_poker_player_stack");
        int oStack = mem.getInt("$ipc_poker_opponent_stack");
        boolean pDealer = mem.getBoolean("$ipc_poker_player_is_dealer");
        int bigBlind = mem.contains("$ipc_poker_big_blind") ? 
            mem.getInt("$ipc_poker_big_blind") : CasinoConfig.POKER_BIG_BLIND;

        if (mem.contains("$ipc_poker_hands_played")) {
            handsPlayedThisSession = mem.getInt("$ipc_poker_hands_played");
        } else {
            handsPlayedThisSession = 0;
        }

        PokerRound round = PokerRound.PREFLOP;
        if (mem.contains("$ipc_poker_round")) {
            try {
                round = PokerRound.valueOf(mem.getString("$ipc_poker_round"));
            } catch (Exception ignored) {}
        }
        
        PokerGame.CurrentPlayer currentPlayer = PokerGame.CurrentPlayer.PLAYER;
        if (mem.contains("$ipc_poker_current_player")) {
            try {
                currentPlayer = PokerGame.CurrentPlayer.valueOf(mem.getString("$ipc_poker_current_player"));
            } catch (Exception ignored) {}
        }
        
        boolean playerHasActed = mem.getBoolean("$ipc_poker_player_has_acted");
        boolean opponentHasActed = mem.getBoolean("$ipc_poker_opponent_has_acted");
        boolean playerAllIn = mem.getBoolean("$ipc_poker_player_all_in");
        boolean opponentAllIn = mem.getBoolean("$ipc_poker_opponent_all_in");

        List<Card> playerHand = new ArrayList<>();
        if (mem.contains("$ipc_poker_player_hand_count")) {
            int count = mem.getInt("$ipc_poker_player_hand_count");
            for (int i = 0; i < count; i++) {
                if (mem.contains("$ipc_poker_player_hand_" + i)) {
                    Card card = PokerGame.stringToCard(
                        mem.getString("$ipc_poker_player_hand_" + i));
                    if (card != null) playerHand.add(card);
                }
            }
        }
        
        List<Card> opponentHand = new ArrayList<>();
        if (mem.contains("$ipc_poker_opponent_hand_count")) {
            int count = mem.getInt("$ipc_poker_opponent_hand_count");
            for (int i = 0; i < count; i++) {
                if (mem.contains("$ipc_poker_opponent_hand_" + i)) {
                    Card card = PokerGame.stringToCard(
                        mem.getString("$ipc_poker_opponent_hand_" + i));
                    if (card != null) opponentHand.add(card);
                }
            }
        }
        
        List<Card> communityCards = new ArrayList<>();
        if (mem.contains("$ipc_poker_community_count")) {
            int count = mem.getInt("$ipc_poker_community_count");
            for (int i = 0; i < count; i++) {
                if (mem.contains("$ipc_poker_community_" + i)) {
                    Card card = PokerGame.stringToCard(
                        mem.getString("$ipc_poker_community_" + i));
                    if (card != null) communityCards.add(card);
                }
            }
        }

        PokerGame.Dealer dealer = pDealer ? PokerGame.Dealer.PLAYER : PokerGame.Dealer.OPPONENT;
        
        pokerGame = PokerGame.createSuspendedGame(
            pStack, oStack, bigBlind,
            pot, pBet, oBet,
            dealer, round, currentPlayer,
            playerHand, opponentHand, communityCards,
            playerHasActed, opponentHasActed,
            playerAllIn, opponentAllIn
        );

        long suspendTime = mem.getLong("$ipc_poker_suspend_time");
        float daysAway = Global.getSector().getClock().getElapsedDaysSince(suspendTime);

        main.getTextPanel().addPara(Strings.get("poker_suspend.dealer_stares"), Color.CYAN);
        if (daysAway >= 30) {
            main.getTextPanel().addPara(Strings.format("poker_suspend.gone_30_days", daysAway), Color.YELLOW);
        } else if (daysAway >= 7) {
            main.getTextPanel().addPara(Strings.format("poker_suspend.gone_7_days", daysAway), Color.YELLOW);
        } else if (daysAway >= 1) {
            main.getTextPanel().addPara(Strings.format("poker_suspend.gone_1_day", daysAway), Color.YELLOW);
        } else {
            main.getTextPanel().addPara(Strings.format("poker_suspend.gone_hours", daysAway * 24), Color.YELLOW);
        }
        main.getTextPanel().addPara(Strings.get("poker_suspend.cards_havent_moved"), Color.GRAY);

        main.getOptions().clearOptions();
        main.getOptions().addOption(Strings.get("poker_resume.continue"), "poker_resume_continue");
        main.getOptions().addOption(Strings.get("poker_resume.wait"), "poker_resume_wait");
    }

    public boolean hasSuspendedPoker() {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        return mem.getBoolean("$ipc_poker_suspended");
    }

    private void clearSuspendedGameMemory() {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        mem.unset("$ipc_poker_suspended");
        mem.unset("$ipc_poker_pot_size");
        mem.unset("$ipc_poker_player_bet");
        mem.unset("$ipc_poker_opponent_bet");
        mem.unset("$ipc_poker_player_stack");
        mem.unset("$ipc_poker_opponent_stack");
        mem.unset("$ipc_poker_player_is_dealer");
        mem.unset("$ipc_poker_hands_played");
        mem.unset("$ipc_poker_suspend_time");
        mem.unset("$ipc_poker_big_blind");
        mem.unset("$ipc_poker_round");
        mem.unset("$ipc_poker_current_player");
        mem.unset("$ipc_poker_player_has_acted");
        mem.unset("$ipc_poker_opponent_has_acted");
        mem.unset("$ipc_poker_player_all_in");
        mem.unset("$ipc_poker_opponent_all_in");
        mem.unset("$ipc_poker_player_hand_count");
        mem.unset("$ipc_poker_opponent_hand_count");
        mem.unset("$ipc_poker_community_count");
        for (int i = 0; i < 2; i++) {
            mem.unset("$ipc_poker_player_hand_" + i);
            mem.unset("$ipc_poker_opponent_hand_" + i);
        }
        for (int i = 0; i < 5; i++) {
            mem.unset("$ipc_poker_community_" + i);
        }
    }

    private void determineWinner() {
        if (pokerGame == null) return;
        PokerGame.PokerState state = pokerGame.getState();

        if (state.folder != null) {
            if (state.folder == PokerGame.CurrentPlayer.PLAYER) {
                main.getTextPanel().addPara(Strings.format("poker_result.you_fold_pot", state.pot), Color.GRAY);
                state.opponentStack += state.pot;
            } else {
                main.getTextPanel().addPara(Strings.format("poker_result.dealer_fold_pot", state.pot), Color.CYAN);
                state.playerStack += state.pot;
            }
            state.pot = 0;
            endHand();
            return;
        }

        main.getTextPanel().addPara(Strings.get("poker_result.community_cards"));
        displayColoredCards(state.communityCards);
        
        main.getTextPanel().addPara(Strings.get("poker_result.opponent_reveals"));
        displayColoredCards(state.opponentHand);

        if (state.playerHandRank != null && state.opponentHandRank != null) {
            main.getTextPanel().addPara(Strings.format("poker_result.your_best", state.playerHandRank.name()));
            main.getTextPanel().addPara(Strings.format("poker_result.opponent_best", state.opponentHandRank.name()));
        } else {
            main.getTextPanel().addPara(Strings.get("poker_result.hand_ended_early"), Color.GRAY);
        }

        PokerHandEvaluator.HandScore playerScore = PokerHandEvaluator.evaluate(state.playerHand, state.communityCards);
        PokerHandEvaluator.HandScore oppScore = PokerHandEvaluator.evaluate(state.opponentHand, state.communityCards);

        int cmp = playerScore.compareTo(oppScore);

        boolean playerWasBluffing = false;  // anti-gullibility AI tracking
        if (cmp < 0) {
            playerWasBluffing = playerScore.rank.value <= PokerHandEvaluator.HandRank.PAIR.value;
        }
        pokerGame.getAI().trackPlayerShowdown(playerWasBluffing);

            float lastBetRatio = state.pot > 0 ? (float) state.playerBet / state.pot : 0f;
            boolean playerWon = cmp > 0;
            pokerGame.getAI().trackShowdownDetails(playerScore.rank.value, lastBetRatio, playerWon, playerWasBluffing);

        if (cmp > 0) {
            main.getTextPanel().addPara(Strings.get("poker_result.victory"), Color.CYAN);
            state.playerStack += state.pot;
            main.getTextPanel().addPara(Strings.format("poker_result.won_stargems", state.pot), Color.GREEN);
            state.pot = 0;
            main.getTextPanel().addPara(Strings.format("poker_result.your_stack", state.playerStack), Color.CYAN);
            main.getTextPanel().addPara(Strings.format("poker_result.opponent_stack", state.opponentStack), Color.ORANGE);
            endHand();
        } else if (cmp < 0) {
            main.getTextPanel().addPara(Strings.get("poker_result.defeat"), Color.RED);
            state.opponentStack += state.pot;
            main.getTextPanel().addPara(Strings.format("poker_result.dealer_won", state.pot), Color.RED);
            state.pot = 0;
            main.getTextPanel().addPara(Strings.format("poker_result.your_stack", state.playerStack), Color.CYAN);
            main.getTextPanel().addPara(Strings.format("poker_result.opponent_stack", state.opponentStack), Color.ORANGE);
            endHand();
        } else {
            main.getTextPanel().addPara(Strings.get("poker_result.split_pot"), Color.YELLOW);
            int halfPot = state.pot / 2;
            int remainder = state.pot % 2;
            state.playerStack += halfPot + remainder;
            state.opponentStack += halfPot;
            main.getTextPanel().addPara(Strings.format("poker_result.you_receive", halfPot + remainder), Color.CYAN);
            main.getTextPanel().addPara(Strings.format("poker_result.opponent_receives", halfPot), Color.ORANGE);
            state.pot = 0;
            main.getTextPanel().addPara(Strings.format("poker_result.your_stack", state.playerStack), Color.CYAN);
            main.getTextPanel().addPara(Strings.format("poker_result.opponent_stack", state.opponentStack), Color.ORANGE);
            endHand();
        }
    }

    private void displayColoredCards(List<Card> cards) {
        main.textPanel.setFontInsignia();
        StringBuilder cardText = new StringBuilder();
        for (int i = 0; i < cards.size(); i++) {
            Card c = cards.get(i);
            Color suitColor = switch (c.suit()) {
                case HEARTS -> Color.RED;
                case DIAMONDS -> Color.BLUE;
                case CLUBS -> Color.GREEN;
                default -> Color.GRAY;
            };
            cardText.setLength(0);
            cardText.append(c);
            if (i < cards.size() - 1) cardText.append(" ");
            main.textPanel.addPara(cardText.toString(), suitColor);
        }
    }

    private void displayColoredCardsOnOneLine(List<Card> cards, String prefix, Color prefixColor) {
        if (cards == null || cards.isEmpty()) {
            return;
        }

        main.textPanel.setFontInsignia();

        StringBuilder fullText = new StringBuilder(prefix + ": ");
        List<String> cardStrings = new ArrayList<>();
        List<Color> highlightColors = new ArrayList<>();

        for (int i = 0; i < cards.size(); i++) {
            Card c = cards.get(i);
            Color suitColor = switch (c.suit()) {
                case HEARTS -> Color.RED;
                case DIAMONDS -> Color.BLUE;
                case CLUBS -> Color.GREEN;
                default -> Color.GRAY;
            };

            String cardStr = c.toString();
            if (i > 0) {
                fullText.append(" ");
            }
            fullText.append(cardStr);

            cardStrings.add(cardStr);
            highlightColors.add(suitColor);
        }

        main.textPanel.addPara(fullText.toString(), prefixColor);
        main.textPanel.highlightInLastPara(cardStrings.toArray(new String[0]));
        main.textPanel.setHighlightColorsInLastPara(highlightColors.toArray(new Color[0]));
    }

    private void returnStacks() {
        if (pokerGame != null) {
            CasinoVIPManager.addToBalance(pokerGame.getState().playerStack);
            pokerGame.getState().playerStack = 0;
        }
    }
    
    private void handleLeaveTable() {
        if (pokerGame != null && pokerGame.getState().playerStack > 0) {
            int stackToReturn = pokerGame.getState().playerStack;
            CasinoVIPManager.addToBalance(stackToReturn);
            main.getTextPanel().addPara(Strings.get("poker_result.flip_table_crash"), Color.RED);
            main.getTextPanel().addPara(Strings.get("poker_result.typical_player"), Color.GRAY);
            main.getTextPanel().addPara(Strings.format("poker_result.cash_out_storm", stackToReturn), Color.GREEN);
            main.getTextPanel().addPara(Strings.format("poker_result.new_balance", CasinoVIPManager.getBalance()), Color.YELLOW);
            pokerGame.getState().playerStack = 0;
        }

        if (handsPlayedThisSession < MIN_HANDS_BEFORE_LEAVE) {
            MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
            mem.set(POKER_COOLDOWN_KEY, Global.getSector().getClock().getTimestamp());
            main.getTextPanel().addPara(Strings.get("poker_suspend.dealer_note_early"), Color.YELLOW);
        }

        handsPlayedThisSession = 0;

        clearSuspendedGameMemory();
        pokerGame = null;
        currentDelegate = null;

        main.showMenu();
    }

    private void handleSuspendLeave() {
        main.showMenu();
    }
    
    private void abandonSuspendedGame() {
        if (pokerGame != null && pokerGame.getState().playerStack > 0) {
            int stackToReturn = pokerGame.getState().playerStack;
            CasinoVIPManager.addToBalance(stackToReturn);
            main.getTextPanel().addPara(Strings.format("poker_suspend.cash_out_leave", stackToReturn), Color.GREEN);
            pokerGame.getState().playerStack = 0;
        }

        if (handsPlayedThisSession < MIN_HANDS_BEFORE_LEAVE) {
            MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
            mem.set(POKER_COOLDOWN_KEY, Global.getSector().getClock().getTimestamp());
            main.getTextPanel().addPara(Strings.get("poker_suspend.dealer_note_early"), Color.YELLOW);
        }

        handsPlayedThisSession = 0;
        clearSuspendedGameMemory();
        
        pokerGame = null;
        currentDelegate = null;
        
        main.showMenu();
    }
    
private void endHand() {
        if (pokerGame == null) return;
        PokerGame.PokerState state = pokerGame.getState();

        if (state.playerStack < state.bigBlind) {
            main.getTextPanel().addPara(Strings.get("poker_result.out_of_chips"), Color.RED);
            returnStacks();
            clearSuspendedGameMemory();
            pokerGame = null;
            handsPlayedThisSession = 0;
            main.getOptions().clearOptions();
            main.getOptions().addOption(Strings.get("poker_result.leave_table"), "back_menu");
        } else if (state.opponentStack < state.bigBlind) {
            main.getTextPanel().addPara(Strings.get("poker_result.opponent_out"), Color.GREEN);
            returnStacks();
            clearSuspendedGameMemory();
            pokerGame = null;
            handsPlayedThisSession = 0;
            main.getOptions().clearOptions();
            main.getOptions().addOption(Strings.get("poker_result.leave_table"), "back_menu");
        } else {
            main.getOptions().clearOptions();
            main.getOptions().addOption(Strings.get("poker_result.next_hand"), "next_hand");
            main.getOptions().addOption(Strings.get("poker_result.leave_table"), "back_menu");
        }
    }
    
    public void processPlayerActionInPlace(PokerAction action, int raiseAmount, PokerDialogDelegate delegate) {
        if (pokerGame == null) return;
        
        PokerGame.PokerState state = pokerGame.getState();
        
        switch (action) {
            case FOLD -> {
                pokerGame.processPlayerAction(PokerAction.FOLD, 0);
                delegate.setLastPlayerAction(Strings.get("poker_actions.you_fold"));
                if (state.folder != null) {
                    state.lastPotWon = state.pot;
                    if (state.folder == PokerGame.CurrentPlayer.PLAYER) {
                        state.opponentStack += state.pot;
                    } else {
                        state.playerStack += state.pot;
                    }
                    state.pot = 0;
                }
            }
            case CHECK -> {
                pokerGame.processPlayerAction(PokerAction.CHECK, 0);
                delegate.setLastPlayerAction(Strings.get("poker_actions.you_check"));
            }
            case CALL -> {
                int callAmount = Math.min(state.opponentBet - state.playerBet, state.playerStack);
                pokerGame.processPlayerAction(PokerAction.CALL, 0);
                delegate.setLastPlayerAction(Strings.format("poker_actions.you_call", callAmount));
            }
            case RAISE -> {
                pokerGame.processPlayerAction(PokerAction.RAISE, raiseAmount);
                delegate.setLastPlayerAction(Strings.format("poker_actions.you_raise_to", raiseAmount));
            }
            case ALL_IN -> throw new IllegalArgumentException();
        }
        
        processOpponentTurnInPlace(delegate);
        
        delegate.refreshAfterStateChange(pokerGame);
    }
    
    private void processOpponentTurnInPlace(PokerDialogDelegate delegate) {
        if (pokerGame == null) return;
        
        PokerGame.PokerState state = pokerGame.getState();
        
        if (state.round == PokerRound.SHOWDOWN) {
            determineWinnerInPlace(delegate);
            return;
        }
        
        while (state.currentPlayer == PokerGame.CurrentPlayer.OPPONENT) {
            delegate.startOpponentTurn();
            
PokerAICommon.AIResponse response = pokerGame.getOpponentAction();
            pokerGame.processOpponentAction(response);
            
            String actionText = formatOpponentActionText(response);
            delegate.setLastOpponentAction(actionText);
            
            state = pokerGame.getState();
            if (state.round == PokerRound.SHOWDOWN) {
                determineWinnerInPlace(delegate);
                return;
            }
        }
    }
    
    private String formatOpponentActionText(PokerAICommon.AIResponse response) {
        return switch (response.action) {
            case CALL -> Strings.get("poker_actions.opponent_calls");
            case RAISE -> Strings.format("poker_actions.opponent_raises_by", response.raiseAmount);
            case CHECK -> Strings.get("poker_actions.opponent_checks");
            case FOLD -> Strings.get("poker_actions.opponent_folds");
            case BET -> Strings.format("poker_actions.opponent_bets", response.raiseAmount);
        };
    }
    
    private void determineWinnerInPlace(PokerDialogDelegate delegate) {
        if (pokerGame == null) return;
        PokerGame.PokerState state = pokerGame.getState();
        
        if (state.folder != null) {
            state.lastPotWon = state.pot;
            if (state.folder == PokerGame.CurrentPlayer.PLAYER) {
                state.opponentStack += state.pot;
            } else {
                state.playerStack += state.pot;
            }
            state.pot = 0;
            delegate.refreshAfterStateChange(pokerGame);
            return;
        }
        
        PokerHandEvaluator.HandScore playerScore = PokerHandEvaluator.evaluate(state.playerHand, state.communityCards);
        PokerHandEvaluator.HandScore oppScore = PokerHandEvaluator.evaluate(state.opponentHand, state.communityCards);
        
        state.lastPotWon = state.pot;
        
        int cmp = playerScore.compareTo(oppScore);
        if (cmp > 0) {
            state.playerStack += state.pot;
        } else if (cmp < 0) {
            state.opponentStack += state.pot;
        } else {
            int halfPot = state.pot / 2;
            int remainder = state.pot % 2;
            state.playerStack += halfPot + remainder;
            state.opponentStack += halfPot;
        }
        state.pot = 0;
        
        delegate.refreshAfterStateChange(pokerGame);
    }
    
    public void startNextHandInPlace(PokerDialogDelegate delegate) {
        if (pokerGame == null) return;

        PokerGame.PokerState state = pokerGame.getState();

        if (state.playerStack <= 0 || state.opponentStack <= 0) {
            delegate.closeDialog();
            setupGame(state.playerStack > 0 ? state.playerStack : 1000);
            return;
        }

        pokerGame.startNewHand();
        handsPlayedThisSession++;

        delegate.updateGame(pokerGame);
        processOpponentTurnInPlace(delegate);
    }
    
    public void handleCleanLeaveInPlace(PokerDialogDelegate delegate) {
        if (pokerGame != null && pokerGame.getState().playerStack > 0) {
            int stackToReturn = pokerGame.getState().playerStack;
            CasinoVIPManager.addToBalance(stackToReturn);
            pokerGame.getState().playerStack = 0;
        }
        
        handsPlayedThisSession = 0;
        clearSuspendedGameMemory();
        pokerGame = null;
        
        delegate.closeDialog();
        main.showMenu();
    }
}
