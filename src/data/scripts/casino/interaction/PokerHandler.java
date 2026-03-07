package data.scripts.casino.interaction;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import data.scripts.casino.CasinoConfig;
import data.scripts.casino.CasinoVIPManager;
import data.scripts.casino.PokerGame;
import data.scripts.casino.PokerGame.PokerGameLogic;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Handler for Texas Hold'em poker: game setup, betting rounds, AI opponent,
 * and suspend/resume functionality. Early departure triggers cooldown penalty.
 */
public class PokerHandler {

    private final CasinoInteraction main;
    private PokerGame pokerGame;

    private static final int COOLDOWN_DAYS = 1;
    private static final int MIN_HANDS_BEFORE_LEAVE = 3;

    public PokerGame getPokerGame() {
        return pokerGame;
    }

    protected int playerWallet;

    private int pendingStackSize = 0;
    private int pendingOverdraftAmount = 0;

    private int handsPlayedThisSession = 0;
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
        handlers.put("how_to_poker", option -> main.help.showPokerHelp());
        handlers.put("poker_call", option -> handlePokerCall());
        handlers.put("poker_check", option -> handlePokerCheck());
        handlers.put("poker_fold", option -> handlePokerFold());
        handlers.put("poker_raise_menu", option -> showRaiseOptions());
        handlers.put("poker_back_action", option -> updateUI());
        handlers.put("poker_suspend", option -> suspendGame());
        handlers.put("poker_back_to_menu", option -> handleLeaveTable());
        handlers.put("leave_now", option -> handleSuspendLeave());
        handlers.put("poker_abandon_confirm", option -> showAbandonConfirm());
        handlers.put("poker_abandon_confirm_leave", option -> handleLeaveTable());
        handlers.put("poker_abandon_cancel", option -> updateUI());
        handlers.put("suspend_abandon_confirm", option -> showSuspendAbandonConfirm());
        handlers.put("suspend_abandon_leave", option -> abandonSuspendedGame());
        handlers.put("suspend_abandon_cancel", option -> {
            main.textPanel.addPara("You sit back down. The game continues.", Color.CYAN);
            updateUI();
        });
        handlers.put("back_menu", option -> main.showMenu());
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
        
        // Check predicate-based handlers
        for (Map.Entry<Predicate<String>, OptionHandler> entry : predicateHandlers.entrySet()) {
            if (entry.getKey().test(option)) {
                entry.getValue().handle(option);
                return;
            }
        }
        
        // Handle other poker actions if no match found
        handlePokerAction(option);
    }

    public void showPokerConfirm() {
        main.options.clearOptions();

        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        
        if (mem.contains("$ipc_suspended_game_type")) {
            String gameType = mem.getString("$ipc_suspended_game_type");
            if ("Poker".equals(gameType)) {
                main.setState(CasinoInteraction.State.POKER);
                main.textPanel.addPara("Resuming your suspended poker game...", Color.YELLOW);
                restoreSuspendedGame();
                return;
            }
        }

        if (mem.contains(POKER_COOLDOWN_KEY)) {
            long cooldownStart = mem.getLong(POKER_COOLDOWN_KEY);
            float elapsedDays = Global.getSector().getClock().getElapsedDaysSince(cooldownStart);
            if (elapsedDays < COOLDOWN_DAYS) {
                float daysRemaining = COOLDOWN_DAYS - elapsedDays;
                main.textPanel.addPara("The IPC Dealer eyes you coldly.", Color.RED);
                main.textPanel.addPara("'You left the table early last time. The IPC Credit Facility does not tolerate table-hoppers.'", Color.YELLOW);
                main.textPanel.addPara("'You may return to play poker in " + String.format("%.1f", daysRemaining) + " days.'", Color.GRAY);
                main.options.addOption("Back", "back_menu");
                return;
            } else {
                mem.unset(POKER_COOLDOWN_KEY);
            }
        }

        main.textPanel.addPara("The IPC Dealer prepares to deal. How big of a stack would you like to bring to the table?", Color.YELLOW);

        displayFinancialInfo();

        int currentBalance = CasinoVIPManager.getBalance();
        int availableCredit = CasinoVIPManager.getAvailableCredit();
        int[] stackSizes = CasinoConfig.POKER_STACK_SIZES;
        String[] stackLabels = {"Small", "Medium", "Large", "Huge"};

        for (int i = 0; i < stackSizes.length && i < stackLabels.length; i++) {
            // Allow option if player has enough balance OR enough available credit (for overdraft)
            if (currentBalance >= stackSizes[i] || availableCredit >= stackSizes[i]) {
                main.options.addOption(stackLabels[i] + " Stack (" + stackSizes[i] + " Stargems)", "poker_stack_" + stackSizes[i]);
            }
        }

        int minRequiredGems = CasinoConfig.POKER_BIG_BLIND;
        int minStackSize = CasinoConfig.POKER_STACK_SIZES.length > 0 ? CasinoConfig.POKER_STACK_SIZES[0] : 1000;

        // Check if player has any credit available at all
        if (availableCredit <= 0) {
            main.textPanel.addPara("Your credit facility is exhausted. You cannot afford even the minimum stack size of " + minStackSize + " Stargems.", Color.RED);
            main.textPanel.addPara("Please visit Stargem Top-up to purchase more gems.", Color.YELLOW);
        } else if (availableCredit < minStackSize) {
            main.textPanel.addPara("Your available credit (" + availableCredit + " Stargems) is insufficient for the minimum stack size of " + minStackSize + " Stargems.", Color.RED);
            main.textPanel.addPara("Please visit Stargem Top-up to purchase more gems.", Color.YELLOW);
        } else if (currentBalance < minRequiredGems && availableCredit >= minRequiredGems) {
            main.textPanel.addPara("Your Stargem balance is below the minimum. Choose a fixed stack size to use overdraft.", Color.YELLOW);
        }

        main.options.addOption("How to Play Poker", "how_to_poker");
        main.options.addOption("Back", "back_menu");
        main.setState(CasinoInteraction.State.POKER);
    }

    private void displayFinancialInfo() {
        int currentBalance = CasinoVIPManager.getBalance();
        int creditCeiling = CasinoVIPManager.getCreditCeiling();
        int availableCredit = CasinoVIPManager.getAvailableCredit();
        int daysRemaining = CasinoVIPManager.getDaysRemaining();
        
        main.textPanel.addPara("--- FINANCIAL STATUS ---", Color.CYAN);
        
        // Show balance with color coding
        Color balanceColor = currentBalance >= 0 ? Color.GREEN : Color.RED;
        main.textPanel.addPara("Balance: " + currentBalance + " Stargems", balanceColor);
        
        main.textPanel.addPara("Credit Ceiling: " + creditCeiling, Color.GRAY);
        main.textPanel.addPara("Available Credit: " + availableCredit, Color.YELLOW);
        
        if (daysRemaining > 0) {
            main.textPanel.addPara("VIP: " + daysRemaining + " days", Color.CYAN);
        }
        
        main.textPanel.addPara("------------------------", Color.CYAN);
    }

    public void setupGame() {
        int defaultStack = CasinoConfig.POKER_STACK_SIZES.length > 0 ? CasinoConfig.POKER_STACK_SIZES[0] : 10000;
        setupGame(defaultStack);
    }
    
    public void setupGame(int stackSize) {
        playerWallet = CasinoVIPManager.getBalance();
        int availableCredit = CasinoVIPManager.getAvailableCredit();
        boolean overdraftAvailable = CasinoVIPManager.isOverdraftAvailable();
        
        // Check if player has enough in wallet
        if (playerWallet < stackSize) {
            // Player doesn't have enough gems, check if overdraft is available
            if (!overdraftAvailable) {
                // No VIP - show promotion and return
                showVIPPromotionForPoker(stackSize);
                return;
            }
            
            // VIP player - check if they have enough available credit
            if (availableCredit < stackSize) {
                main.textPanel.addPara("Not enough Stargems or available credit! You have " + playerWallet + " Stargems and " + availableCredit + " available credit, but requested " + stackSize + ".", Color.RED);
                showPokerConfirm(); // Return to the confirmation screen
                return;
            }
            
            // Overdraft needed - show confirmation first
            int overdraftAmount = stackSize - playerWallet;
            pendingStackSize = stackSize;
            pendingOverdraftAmount = overdraftAmount;
            showOverdraftConfirmation(stackSize, overdraftAmount);
            return;
        }
        
        // No overdraft needed - proceed directly
        startGameWithStack(stackSize);
    }
    
    private void showOverdraftConfirmation(int stackSize, int overdraftAmount) {
        main.options.clearOptions();
        main.textPanel.addPara("OVERDRAFT CONFIRMATION REQUIRED", Color.RED);
        main.textPanel.addPara("");
        main.textPanel.addPara("Your current balance: " + CasinoVIPManager.getBalance() + " Stargems", Color.YELLOW);
        main.textPanel.addPara("Requested stack size: " + stackSize + " Stargems", Color.WHITE);
        main.textPanel.addPara("");
        main.textPanel.addPara("OVERDRAFT DETAILS:", Color.CYAN);
        main.textPanel.addPara("Overdraft amount: " + overdraftAmount + " Stargems", Color.RED);
        main.textPanel.addPara("Interest rate: " + (int)(CasinoConfig.VIP_DAILY_INTEREST_RATE * 100) + "% per day", Color.YELLOW);
        main.textPanel.addPara("Your balance will become: " + (CasinoVIPManager.getBalance() - stackSize) + " Stargems", Color.GRAY);
        main.textPanel.addPara("");
        main.textPanel.addPara("WARNING: Negative balances accrue daily interest. Ensure you can repay this debt promptly!", Color.RED);
        main.textPanel.addPara("");
        
        main.options.addOption("Confirm Overdraft and Start Game", "confirm_overdraft");
        main.options.addOption("Cancel - Choose Different Stack", "cancel_overdraft");
    }
    
    private void processOverdraftConfirmation() {
        if (pendingStackSize <= 0) {
            showPokerConfirm();
            return;
        }
        
        // Deduct the stack from balance (can go negative)
        CasinoVIPManager.addToBalance(-pendingStackSize);
        
        main.textPanel.addPara("IPC Credit Alert: Overdraft of " + pendingOverdraftAmount + " Stargems authorized.", Color.YELLOW);
        
        startGameWithStack(pendingStackSize);
        
        // Clear pending values
        pendingStackSize = 0;
        pendingOverdraftAmount = 0;
    }
    
    private void cancelOverdraft() {
        pendingStackSize = 0;
        pendingOverdraftAmount = 0;
        main.textPanel.addPara("Overdraft cancelled.", Color.GRAY);
        showPokerConfirm();
    }
    
    private void startGameWithStack(int stackSize) {
        int opponentStack = Math.max(CasinoConfig.POKER_DEFAULT_OPPONENT_STACK, stackSize);
        
        pokerGame = new PokerGame(stackSize, opponentStack, CasinoConfig.POKER_SMALL_BLIND, CasinoConfig.POKER_BIG_BLIND);
        
        handsPlayedThisSession = 0;
        
        updateUI();
    }
    
    private void showVIPPromotionForPoker(int stackSize) {
        main.getOptions().clearOptions();
        main.textPanel.addPara("INSUFFICIENT STARGEMS", Color.RED);
        main.textPanel.addPara("");
        main.textPanel.addPara("Your Stargem balance is insufficient for this transaction.", Color.YELLOW);
        main.textPanel.addPara("Current Balance: " + CasinoVIPManager.getBalance(), Color.GRAY);
        main.textPanel.addPara("Required for " + stackSize + " stack: " + stackSize + " Stargems", Color.GRAY);
        main.textPanel.addPara("");
        main.textPanel.addPara("IPC CREDIT FACILITY", Color.CYAN);
        main.textPanel.addPara("Overdraft protection is exclusively available to VIP Pass subscribers.", Color.WHITE);
        main.textPanel.addPara("");
        main.textPanel.addPara("VIP PASS BENEFITS:", Color.GREEN);
        main.textPanel.addPara("- Access to IPC Credit Facility (overdraft protection)", Color.GRAY);
        main.textPanel.addPara("- " + CasinoConfig.VIP_DAILY_REWARD + " Stargems daily reward", Color.GRAY);
        main.textPanel.addPara("- Reduced debt interest rate (" + (int)(CasinoConfig.VIP_DAILY_INTEREST_RATE * 100) + "% daily)", Color.GRAY);
        main.textPanel.addPara("- Increased credit ceiling per purchase", Color.GRAY);
        main.textPanel.addPara("");
        main.textPanel.addPara("Purchase a VIP Pass from Financial Services to unlock overdraft protection!", Color.YELLOW);

        main.getOptions().addOption("Go to Stargem Top-up", "topup_menu");
        main.getOptions().addOption("Back", "back_menu");
    }

    private String formatBB(int amount, int bigBlind) {
        return bigBlind > 0 ? String.format("%.1f", (float) amount / bigBlind) : "0";
    }

    public void updateUI() {
        if (pokerGame == null) return;
        PokerGame.PokerState state = pokerGame.getState();
        // Guard against resuming game when player has no chips
        // Only trigger if there's no active hand (pot=0) or hand is already over (SHOWDOWN)
        // This prevents premature end when player goes all-in (stack=0 but hand still active)
        if (state.playerStack <= 0 && (state.pot == 0 || state.round == PokerGame.Round.SHOWDOWN)) {
            endHand();
            return;
        }
        int bigBlind = state.bigBlind;

        main.getOptions().clearOptions();
        main.getTextPanel().addPara("------------------------------------------------");
        main.getTextPanel().addPara("Pot: %s Stargems (%s BB) | Big Blind: %s", Color.GREEN, 
            String.valueOf(state.pot), formatBB(state.pot, bigBlind), String.valueOf(bigBlind));
        main.getTextPanel().highlightInLastPara(
            String.valueOf(state.pot), formatBB(state.pot, bigBlind), String.valueOf(bigBlind));
        main.getTextPanel().setHighlightColorsInLastPara(Color.GREEN, Color.GREEN, Color.GREEN);
        
        main.getTextPanel().addPara("Your Stack: %s Stargems (%s BB)", Color.CYAN,
            String.valueOf(state.playerStack), formatBB(state.playerStack, bigBlind));
        
        main.getTextPanel().addPara("Opponent Stack: %s Stargems (%s BB)", Color.ORANGE,
            String.valueOf(state.opponentStack), formatBB(state.opponentStack, bigBlind));
        
        // Display AI personality information
        String aiPersonality = pokerGame.getAIPersonalityDescription();
        main.getTextPanel().addPara(aiPersonality, Color.GRAY);
        
        displayColoredCardsOnOneLine(state.playerHand, "Your Hand", Color.CYAN);
        if (!state.communityCards.isEmpty()) {
            displayColoredCardsOnOneLine(state.communityCards, "Community", Color.YELLOW);
        }
        
        int callAmount = state.opponentBet - state.playerBet;
        if (callAmount > 0) {
            if (state.playerStack >= callAmount) {
                main.getOptions().addOption("Call (" + callAmount + ")", "poker_call");
            } else if (state.playerStack > 0) {
                main.getOptions().addOption("Call All-In (" + state.playerStack + " Stargems)", "poker_call");
            } else {
                main.getOptions().addOption("Call (0 Stargems)", "poker_call");
            }
            main.getOptions().addOption("Fold", "poker_fold");
        } else {
            main.getOptions().addOption("Check", "poker_check");
        }
        
        if (state.playerStack > 0 && state.opponentBet - state.playerBet < state.playerStack) {
            main.getOptions().addOption("Raise", "poker_raise_menu");
        }
        
        main.getOptions().addOption("How to Play Poker", "how_to_poker");
        main.getOptions().addOption("Tell Them to Wait (Suspend)", "poker_suspend");
        main.getOptions().addOption("Flip Table and Leave", "poker_abandon_confirm");
    }

    private void startNextHand() {
        if (pokerGame != null) {
            PokerGame.PokerState state = pokerGame.getState();
            if (state.playerStack <= 0 || state.opponentStack <= 0) {
                 // Game over logic if someone is bust
                 main.textPanel.addPara("One of the players is out of chips. Starting new game...", Color.YELLOW);
                 setupGame(state.playerStack > 0 ? state.playerStack : 1000); // Simplistic restart
                 return;
            }
            pokerGame.startNewHand();
            handsPlayedThisSession++;
            updateUI();
        }
    }

    public void handlePokerCall() {
        if (pokerGame == null) return;
        PokerGame.PokerState state = pokerGame.getState();
        int callAmount = Math.min(state.opponentBet - state.playerBet, state.playerStack);
        pokerGame.processPlayerAction(PokerGame.Action.CALL, 0);
        main.getTextPanel().addPara("You call " + callAmount + " Stargems.", Color.CYAN);
        updateGameState();
    }

    public void handlePokerCheck() {
        if (pokerGame == null) return;
        pokerGame.processPlayerAction(PokerGame.Action.CHECK, 0);
        main.getTextPanel().addPara("You check.", Color.CYAN);
        updateGameState();
    }

    public void handlePokerFold() {
        if (pokerGame == null) return;
        pokerGame.processPlayerAction(PokerGame.Action.FOLD, 0);
        main.getTextPanel().addPara("You fold.", Color.GRAY);
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
        
        // Check if we need to show showdown results
        if (state.round == PokerGame.Round.SHOWDOWN) {
            determineWinner();
            return;
        }
        
        // If it's opponent's turn, process it
        if (state.currentPlayer == PokerGame.CurrentPlayer.OPPONENT) {
             PokerGame.SimplePokerAI.AIResponse response = pokerGame.getOpponentAction();
             
             // Log opponent action
             switch(response.action) {
                 case CALL: 
                     main.getTextPanel().addPara("Opponent calls.", Color.YELLOW); break;
                 case RAISE: 
                     main.getTextPanel().addPara("Opponent raises by " + response.raiseAmount + ".", Color.YELLOW); break;
                 case CHECK: 
                     main.getTextPanel().addPara("Opponent checks.", Color.YELLOW); break;
                 case FOLD: 
                     main.getTextPanel().addPara("Opponent folds.", Color.CYAN); break;
             }
             
             // Track if AI is folding to a player bet (for anti-gullibility)
             if (response.action == PokerGame.SimplePokerAI.Action.FOLD) {
                 // Only track if player has bet (not when checking through)
                 if (state.playerBet > state.opponentBet) {
                     pokerGame.getAI().trackAIFoldedToPlayerBet(state.pot);
                 }
             }
             
             pokerGame.processOpponentAction(response);
             
             // Re-check state after opponent action
             state = pokerGame.getState();
             if (state.round == PokerGame.Round.SHOWDOWN) {
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
        
        // Helper to format bet option with value and BB
        java.util.function.BiConsumer<Integer, String> addBetOption = (amount, label) -> {
            if (amount > 0 && amount <= playerStackAvailable) {
                float bbAmount = bigBlind > 0 ? (float)amount / bigBlind : 0;
                String optionText = label + " (" + amount + " / " + String.format("%.1f", bbAmount) + " BB)";
                main.getOptions().addOption(optionText, "poker_raise_" + amount);
            }
        };
        
        // 1. BB (minimum raise)
        addBetOption.accept(bigBlind, "Big Blind");

        // 2. Half Pot
        int halfPot = potSize / 2;
        if (halfPot > bigBlind) {
            addBetOption.accept(halfPot, "Half Pot");
        }

        // 3. Pot
        if (potSize > halfPot) {
            addBetOption.accept(potSize, "Pot");
        }
        
        // 4. 2x Pot
        int twoXPot = potSize * 2;
        if (twoXPot > potSize && twoXPot <= playerStackAvailable) {
            addBetOption.accept(twoXPot, "2x Pot");
        }
        
        // 6. All-in (always show if player has chips)
        if (playerStackAvailable > 0) {
            float allInBB = bigBlind > 0 ? (float)playerStackAvailable / bigBlind : 0;
            String allInText = "All-In (" + playerStackAvailable + " / " + String.format("%.1f", allInBB) + " BB)";
            main.getOptions().addOption(allInText, "poker_raise_" + playerStackAvailable);
        }
        
        main.getOptions().addOption("Back", "poker_back_action");
    }

    private void performRaise(int amt) {
        if (pokerGame == null) return;
        PokerGame.PokerState state = pokerGame.getState();
        int totalBet = state.playerBet + amt;
        pokerGame.processPlayerAction(PokerGame.Action.RAISE, amt);
        main.getTextPanel().addPara("You raise to " + totalBet + " Stargems.", Color.CYAN);
        updateGameState();
    }
    
    private void suspendGame() {
        if (pokerGame == null) return;
        PokerGame.PokerState state = pokerGame.getState();
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        mem.set("$ipc_suspended_game_type", "Poker");

        mem.set("$ipc_poker_pot_size", state.pot);
        mem.set("$ipc_poker_player_bet", state.playerBet);
        mem.set("$ipc_poker_opponent_bet", state.opponentBet);
        // currentBetToCall is derived from bets
        mem.set("$ipc_poker_player_stack", state.playerStack);
        mem.set("$ipc_poker_opponent_stack", state.opponentStack);
        mem.set("$ipc_poker_player_is_dealer", state.dealer == PokerGame.Dealer.PLAYER);

        mem.set("$ipc_poker_hands_played", handsPlayedThisSession);

        mem.set("$ipc_poker_suspend_time", Global.getSector().getClock().getTimestamp());

        main.getTextPanel().addPara("You stand up abruptly. 'Hold that thought! I'll be right back!'", Color.YELLOW);
        main.getTextPanel().addPara("The IPC Dealer raises an eyebrow but nods slowly. 'The cards will stay as they are.'", Color.CYAN);
        main.getTextPanel().addPara("'Don't be long. We have other customers waiting.'", Color.GRAY);
        main.getOptions().clearOptions();
        main.getOptions().addOption("Leave", "leave_now");
    }

    private void showAbandonConfirm() {
        if (pokerGame == null) {
            main.showMenu();
            return;
        }
        PokerGame.PokerState state = pokerGame.getState();
        int stackValue = state.playerStack;
        
        main.getOptions().clearOptions();
        main.getTextPanel().addPara("FLIP TABLE AND LEAVE?", Color.RED);
        main.getTextPanel().addPara("");
        main.getTextPanel().addPara("You are about to flip the table and storm out.", Color.YELLOW);
        main.getTextPanel().addPara("Your stack of " + stackValue + " Stargems will be returned to your balance.", Color.CYAN);
        main.getTextPanel().addPara("");
        main.getTextPanel().addPara("WARNING: If you leave early (less than " + MIN_HANDS_BEFORE_LEAVE + " hands played), you will receive a 1-day cooldown before playing again.", Color.ORANGE);
        main.getTextPanel().addPara("");
        main.getTextPanel().addPara("Consider using 'Tell Them to Wait (Suspend)' instead to pause the game without penalty.", Color.GRAY);
        
        main.getOptions().addOption("Yes, Flip Table", "poker_abandon_confirm_leave");
        main.getOptions().addOption("Go Back to Game", "poker_abandon_cancel");
    }

    private void showSuspendAbandonConfirm() {
        if (pokerGame == null) {
            main.showMenu();
            return;
        }
        PokerGame.PokerState state = pokerGame.getState();
        int stackValue = state.playerStack;
        
        main.getOptions().clearOptions();
        main.getTextPanel().addPara("ABANDON SUSPENDED GAME?", Color.RED);
        main.getTextPanel().addPara("");
        main.getTextPanel().addPara("You are about to abandon the suspended poker game.", Color.YELLOW);
        main.getTextPanel().addPara("Your stack of " + stackValue + " Stargems will be returned to your balance.", Color.CYAN);
        main.getTextPanel().addPara("");
        main.getTextPanel().addPara("WARNING: If you leave early (less than " + MIN_HANDS_BEFORE_LEAVE + " hands played), you will receive a 1-day cooldown before playing again.", Color.ORANGE);
        main.getTextPanel().addPara("");
        main.getTextPanel().addPara("You could also return later to resume the suspended game.", Color.GRAY);
        
        main.getOptions().addOption("Yes, Abandon Game", "suspend_abandon_leave");
        main.getOptions().addOption("Go Back to Table", "suspend_abandon_cancel");
    }

    public void restoreSuspendedGame() {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();

        if (mem.contains("$ipc_poker_pot_size")) {
            int pot = mem.getInt("$ipc_poker_pot_size");
            int pBet = mem.getInt("$ipc_poker_player_bet");
            int oBet = mem.getInt("$ipc_poker_opponent_bet");
            int pStack = mem.getInt("$ipc_poker_player_stack");
            int oStack = mem.getInt("$ipc_poker_opponent_stack");
            boolean pDealer = mem.getBoolean("$ipc_poker_player_is_dealer");

            // Restore hands played counter
            if (mem.contains("$ipc_poker_hands_played")) {
                handsPlayedThisSession = mem.getInt("$ipc_poker_hands_played");
            } else {
                handsPlayedThisSession = 0;
            }

            // Create game instance
            // Using placeholder stack sizes, but then we override state
            pokerGame = new PokerGame(pStack, oStack, CasinoConfig.POKER_SMALL_BLIND, CasinoConfig.POKER_BIG_BLIND);
            PokerGame.PokerState state = pokerGame.getState();
            state.pot = pot;
            state.playerBet = pBet;
            state.opponentBet = oBet;
            state.dealer = pDealer ? PokerGame.Dealer.PLAYER : PokerGame.Dealer.OPPONENT;

            // Cards are reshuffled in new game due to restore limitation mentioned in original code

            long suspendTime = mem.getLong("$ipc_poker_suspend_time");
            float daysAway = Global.getSector().getClock().getElapsedDaysSince(suspendTime);

            // Shaming message based on how long player was away
            main.getTextPanel().addPara("The IPC Dealer stares at you with dead eyes.", Color.CYAN);
            if (daysAway >= 30) {
                main.getTextPanel().addPara("'You've been gone for " + String.format("%.1f", daysAway) + " days.' The dealer gestures to a cobweb-covered chair. 'We've been standing perfectly still, not breathing, not blinking. The other players have started to fossilize.'", Color.YELLOW);
            } else if (daysAway >= 7) {
                main.getTextPanel().addPara("'Welcome back after " + String.format("%.1f", daysAway) + " days.' The dealer cracks their neck audibly. 'We've been practicing our statue impressions. I think I've forgotten how to sit down.'", Color.YELLOW);
            } else if (daysAway >= 1) {
                main.getTextPanel().addPara("'Ah, you've returned.' The dealer cracks their knuckles. 'Only " + String.format("%.1f", daysAway) + " days. We've been holding our breath this whole time. Lightheaded? Very.'", Color.YELLOW);
            } else {
                main.getTextPanel().addPara("'Back already? It's only been " + String.format("%.1f", daysAway * 24) + " hours.' The dealer looks almost disappointed. 'We were just getting comfortable with the silence.'", Color.YELLOW);
            }
            main.getTextPanel().addPara("'The cards haven't moved. Let's finish this...'", Color.GRAY);

            mem.unset("$ipc_suspended_game_type");

            updateUI();
        } else {
            setupGame();
        }
    }

    private void clearSuspendedGameMemory() {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        mem.unset("$ipc_suspended_game_type");
        mem.unset("$ipc_poker_pot_size");
        mem.unset("$ipc_poker_player_bet");
        mem.unset("$ipc_poker_opponent_bet");
        mem.unset("$ipc_poker_player_stack");
        mem.unset("$ipc_poker_opponent_stack");
        mem.unset("$ipc_poker_player_is_dealer");
        mem.unset("$ipc_poker_hands_played");
        mem.unset("$ipc_poker_suspend_time");
    }

    private void determineWinner() {
        if (pokerGame == null) return;
        PokerGame.PokerState state = pokerGame.getState();

        // Check if someone folded - if so, no showdown, just award the pot
        if (state.folder != null) {
            if (state.folder == PokerGame.CurrentPlayer.PLAYER) {
                // Player folded - opponent wins
                main.getTextPanel().addPara("You fold. The IPC Dealer scoops the pot of " + state.pot + " Stargems.", Color.GRAY);
                state.opponentStack += state.pot;
            } else {
                // Opponent folded - player wins
                main.getTextPanel().addPara("The IPC Dealer folds. You scoop the pot of " + state.pot + " Stargems!", Color.CYAN);
                state.playerStack += state.pot;
            }
            state.pot = 0;
            endHand();
            return;
        }

        // Showdown - reveal hands and compare
        // Display community cards first so players can see the complete board
        main.getTextPanel().addPara("Community Cards: ");
        displayColoredCards(state.communityCards);
        
        main.getTextPanel().addPara("Opponent reveals: ");
        displayColoredCards(state.opponentHand);

        // Handle case where hand ranks might be null (shouldn't happen at showdown, but just in case)
        if (state.playerHandRank != null && state.opponentHandRank != null) {
            main.getTextPanel().addPara("Your Best: " + state.playerHandRank.name());
            main.getTextPanel().addPara("Opponent Best: " + state.opponentHandRank.name());
        } else {
            main.getTextPanel().addPara("Hand ended before showdown.", Color.GRAY);
        }

        // We need detailed comparison (tie breakers) which PokerGame.evaluate() does.
        // Re-evaluate to get HandScore for comparison
        PokerGame.PokerGameLogic.HandScore playerScore = PokerGame.PokerGameLogic.evaluate(state.playerHand, state.communityCards);
        PokerGame.PokerGameLogic.HandScore oppScore = PokerGame.PokerGameLogic.evaluate(state.opponentHand, state.communityCards);

        int cmp = playerScore.compareTo(oppScore);

        // Track showdown for anti-gullibility AI
        // Check if player was bluffing (had weak hand but AI folded earlier)
        boolean playerWasBluffing = false;
        if (cmp < 0) {
            // Player lost - they were bluffing if they had weak hand
            playerWasBluffing = playerScore.rank.value <= PokerGame.PokerGameLogic.HandRank.PAIR.value;
        }
        pokerGame.getAI().trackPlayerShowdown(playerWasBluffing);

        if (cmp > 0) {
            main.getTextPanel().addPara("VICTORY! You take the pot.", Color.CYAN);
            state.playerStack += state.pot; // Award pot to player stack
            main.getTextPanel().addPara("You won " + state.pot + " Stargems!", Color.GREEN);
            state.pot = 0;
            main.getTextPanel().addPara("Your Stack: " + state.playerStack + " Stargems", Color.CYAN);
            main.getTextPanel().addPara("Opponent Stack: " + state.opponentStack + " Stargems", Color.ORANGE);
            endHand();
        } else if (cmp < 0) {
            main.getTextPanel().addPara("DEFEAT. The IPC Dealer wins.", Color.RED);
            state.opponentStack += state.pot; // Award pot to opponent
            main.getTextPanel().addPara("Dealer won " + state.pot + " Stargems.", Color.RED);
            state.pot = 0;
            main.getTextPanel().addPara("Your Stack: " + state.playerStack + " Stargems", Color.CYAN);
            main.getTextPanel().addPara("Opponent Stack: " + state.opponentStack + " Stargems", Color.ORANGE);
            endHand();
        } else {
            main.getTextPanel().addPara("SPLIT POT. It's a draw.", Color.YELLOW);
            int halfPot = state.pot / 2;
            int remainder = state.pot % 2;
            state.playerStack += halfPot + remainder; // Player gets remainder if odd
            state.opponentStack += halfPot;
            main.getTextPanel().addPara("You receive " + (halfPot + remainder) + " Stargems.", Color.CYAN);
            main.getTextPanel().addPara("Opponent receives " + halfPot + " Stargems.", Color.ORANGE);
            state.pot = 0;
            main.getTextPanel().addPara("Your Stack: " + state.playerStack + " Stargems", Color.CYAN);
            main.getTextPanel().addPara("Opponent Stack: " + state.opponentStack + " Stargems", Color.ORANGE);
            endHand();
        }
    }

    private void displayColoredCards(List<PokerGameLogic.Card> cards) {
        main.textPanel.setFontInsignia();
        StringBuilder cardText = new StringBuilder();
        for (int i = 0; i < cards.size(); i++) {
            PokerGameLogic.Card c = cards.get(i);
            Color suitColor = switch (c.suit) {
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

    private void displayColoredCardsOnOneLine(List<PokerGameLogic.Card> cards, String prefix, Color prefixColor) {
        if (cards == null || cards.isEmpty()) {
            return;
        }

        main.textPanel.setFontInsignia();

        // Build the full text with actual card strings (not format placeholders)
        StringBuilder fullText = new StringBuilder(prefix + ": ");
        List<String> cardStrings = new ArrayList<>();
        List<Color> highlightColors = new ArrayList<>();

        for (int i = 0; i < cards.size(); i++) {
            PokerGameLogic.Card c = cards.get(i);
            Color suitColor = switch (c.suit) {
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

        // Add paragraph with the full text
        main.textPanel.addPara(fullText.toString(), prefixColor);

        // Highlight the card texts and set their colors
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
            main.getTextPanel().addPara("You flip the table with a loud crash!", Color.RED);
            main.getTextPanel().addPara("A nearby bystander mutters under their breath: \"Tsk, Typical Gachy Impact player.\"", Color.GRAY);
            main.getTextPanel().addPara("You cash out " + stackToReturn + " Stargems and storm out.", Color.GREEN);
            main.getTextPanel().addPara("Your new balance: " + CasinoVIPManager.getBalance() + " Stargems", Color.YELLOW);
            pokerGame.getState().playerStack = 0;
        }

        // Set cooldown if player left early (played less than minimum required hands)
        if (handsPlayedThisSession < MIN_HANDS_BEFORE_LEAVE) {
            MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
            mem.set(POKER_COOLDOWN_KEY, Global.getSector().getClock().getTimestamp());
            main.getTextPanel().addPara("The IPC Dealer makes a note in their ledger. 'Leaving so soon? The IPC Credit Facility remembers early departures.'", Color.YELLOW);
        }

        handsPlayedThisSession = 0;

        // Clear any suspended game memory since player is intentionally leaving
        clearSuspendedGameMemory();

        main.showMenu();
    }

    private void handleSuspendLeave() {
        main.showMenu();
    }
    
    private void abandonSuspendedGame() {
        if (pokerGame != null && pokerGame.getState().playerStack > 0) {
            int stackToReturn = pokerGame.getState().playerStack;
            CasinoVIPManager.addToBalance(stackToReturn);
            main.getTextPanel().addPara("You cash out " + stackToReturn + " Stargems and leave the table.", Color.GREEN);
            pokerGame.getState().playerStack = 0;
        }

        if (handsPlayedThisSession < MIN_HANDS_BEFORE_LEAVE) {
            MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
            mem.set(POKER_COOLDOWN_KEY, Global.getSector().getClock().getTimestamp());
            main.getTextPanel().addPara("The IPC Dealer makes a note in their ledger. 'Leaving so soon? The IPC Credit Facility remembers early departures.'", Color.YELLOW);
        }

        handsPlayedThisSession = 0;
        clearSuspendedGameMemory();
        main.showMenu();
    }
    
    private void endHand() {
        if (pokerGame == null) return;
        PokerGame.PokerState state = pokerGame.getState();

        // Note: Pot has already been awarded in determineWinner(), so state.pot is 0 here
        // The win message is already displayed there with the correct amount

        if (state.playerStack < CasinoConfig.POKER_BIG_BLIND) {
            main.getTextPanel().addPara("You're out of chips! Game over.", Color.RED);
            returnStacks();
            clearSuspendedGameMemory();
            pokerGame = null;
            handsPlayedThisSession = 0;
            main.getOptions().clearOptions();
            main.getOptions().addOption("Leave Table", "back_menu");
        } else if (state.opponentStack < CasinoConfig.POKER_BIG_BLIND) {
            main.getTextPanel().addPara("Opponent is out of chips! You win!", Color.GREEN);
            returnStacks();
            clearSuspendedGameMemory();
            pokerGame = null;
            handsPlayedThisSession = 0;
            main.getOptions().clearOptions();
            main.getOptions().addOption("Leave Table", "back_menu");
        } else {
            main.getOptions().clearOptions();
            main.getOptions().addOption("Next Hand", "next_hand");
            main.getOptions().addOption("Leave Table", "back_menu");
        }
    }
}
