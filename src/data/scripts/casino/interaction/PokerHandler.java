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

public class PokerHandler {

    private final CasinoInteraction main;
    private PokerGame pokerGame;
    
    public PokerGame getPokerGame() {
        return pokerGame;
    }
    
    // We still track wallet/credit for buy-in logic, but game state is in PokerGame
    protected int playerWallet;

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
        handlers.put("poker_back_to_menu", option -> {
            if (pokerGame != null && pokerGame.getState().playerStack > 0) {
                CasinoVIPManager.addToBalance(pokerGame.getState().playerStack);
                main.getTextPanel().addPara("Returned " + pokerGame.getState().playerStack + " Stargems to your wallet.", Color.YELLOW);
                pokerGame.getState().playerStack = 0;
            }
            showPokerConfirm();
        });
        handlers.put("leave_now", option -> main.showMenu());
        handlers.put("back_menu", option -> main.showMenu());
        
        predicateHandlers.put(option -> option.startsWith("poker_raise_"), option -> {
            int amt = Integer.parseInt(option.replace("poker_raise_", ""));
            performRaise(amt);
        });
        
        predicateHandlers.put(option -> option.startsWith("poker_stack_"), option -> {
            String stackStr = option.replace("poker_stack_", "");
            int stackSize = "all".equals(stackStr) ? CasinoVIPManager.getBalance() : Integer.parseInt(stackStr);
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
        main.textPanel.addPara("The IPC Dealer prepares to deal. How big of a stack would you like to bring to the table?", Color.YELLOW);
        
        // Display financial info
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
        // Only show "Bring All My Stargems" if player has enough balance
        // If balance is below minimum, player must use fixed stack options (which can use overdraft)
        if (currentBalance >= minRequiredGems) {
            main.options.addOption("Bring All My Stargems (" + currentBalance + ")", "poker_stack_all");
        } else if (currentBalance > 0 && currentBalance < minRequiredGems && availableCredit < minRequiredGems) {
            main.textPanel.addPara("You need at least " + minRequiredGems + " Stargems (Big Blind) to play.", Color.RED);
        } else if (currentBalance < minRequiredGems) {
            // Player has insufficient balance but may have credit - show message about using fixed stacks
            main.textPanel.addPara("Your Stargem balance is below the minimum. Choose a fixed stack size to use overdraft.", Color.YELLOW);
        }
        
        main.options.addOption("How to Play Poker", "how_to_poker");
        main.options.addOption("Wait... (Cancel)", "back_menu");
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
        }
        
        // Handle overdraft if needed - simply deduct from balance (can go negative)
        if (playerWallet < stackSize) {
            // Player needs to use overdraft
            int overdraftAmount = stackSize - playerWallet;
            main.textPanel.addPara("IPC Credit Alert: Using " + overdraftAmount + " Stargems of overdraft.", Color.YELLOW);
            main.textPanel.addPara("Balance will go negative. " + (int)(CasinoConfig.VIP_DAILY_INTEREST_RATE * 100) + "% daily interest applies.", Color.GRAY);
        }
        
        // Deduct the stack from balance (can go negative)
        CasinoVIPManager.addToBalance(-stackSize);
        
        int opponentStack = Math.max(CasinoConfig.POKER_DEFAULT_OPPONENT_STACK, stackSize);
        
        pokerGame = new PokerGame(stackSize, opponentStack, CasinoConfig.POKER_SMALL_BLIND, CasinoConfig.POKER_BIG_BLIND);
        
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
        
        main.getOptions().addOption("Go to Financial Services", "financial_menu");
        main.getOptions().addOption("Back", "back_menu");
    }

    public void updateUI() {
        if (pokerGame == null) return;
        PokerGame.PokerState state = pokerGame.getState();
        int bigBlind = state.bigBlind;
        float playerBB = bigBlind > 0 ? (float)state.playerStack / bigBlind : 0;
        float opponentBB = bigBlind > 0 ? (float)state.opponentStack / bigBlind : 0;
        
        main.getOptions().clearOptions();
        main.getTextPanel().addPara("------------------------------------------------");
        main.getTextPanel().addPara("Pot: " + state.pot + " Stargems (" + String.format("%.1f", bigBlind > 0 ? (float)state.pot / bigBlind : 0) + " BB)", Color.GREEN);
        main.getTextPanel().addPara("Your Stack: " + state.playerStack + " Stargems (" + String.format("%.1f", playerBB) + " BB)", Color.CYAN);
        main.getTextPanel().addPara("Opponent Stack: " + state.opponentStack + " Stargems (" + String.format("%.1f", opponentBB) + " BB)", Color.ORANGE);
        main.getTextPanel().addPara("Big Blind: " + bigBlind + " Stargems", Color.GRAY);
        
        // Display AI personality information
        String aiPersonality = pokerGame.getAIPersonalityDescription();
        main.getTextPanel().addPara(aiPersonality, Color.GRAY);
        
        main.getTextPanel().addPara("Your Hand: ");
        displayColoredCards(state.playerHand);
        if (!state.communityCards.isEmpty()) {
            main.getTextPanel().addPara("Community: ");
            displayColoredCards(state.communityCards);
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
    }

    /**
     * Handles poker-specific actions like calls, checks, folds, and raises
     */
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
            updateUI();
        }
    }

    public void handlePokerCall() {
        if (pokerGame == null) return;
        pokerGame.processPlayerAction(PokerGame.Action.CALL, 0);
        updateGameState();
    }
    
    public void handlePokerCheck() {
        if (pokerGame == null) return;
        pokerGame.processPlayerAction(PokerGame.Action.CHECK, 0);
        updateGameState();
    }
    
    public void handlePokerFold() {
        if (pokerGame == null) return;
        pokerGame.processPlayerAction(PokerGame.Action.FOLD, 0);
        // Fold handling and messaging is done in determineWinner()
        updateGameState();
    }
    
    public void handlePokerAction(String option) {
        if ("poker_call".equals(option)) handlePokerCall();
        else if ("poker_check".equals(option)) handlePokerCheck();
        else if ("poker_fold".equals(option)) handlePokerFold();
        else if ("poker_raise_menu".equals(option)) showRaiseOptions();
        else if (option.startsWith("poker_raise_")) {
            int amt = Integer.parseInt(option.replace("poker_raise_", ""));
            performRaise(amt);
        }
        else if ("poker_back_action".equals(option)) updateUI();
        else if ("poker_suspend".equals(option)) suspendGame();
        else if ("leave_now".equals(option)) main.showMenu();
        else if ("back_menu".equals(option)) main.showMenu();
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
        int currentBetToCall = state.opponentBet - state.playerBet;
        
        // Helper to format bet option with value and BB
        java.util.function.BiConsumer<Integer, String> addBetOption = (amount, label) -> {
            if (amount > 0 && amount <= playerStackAvailable) {
                float bbAmount = bigBlind > 0 ? (float)amount / bigBlind : 0;
                String optionText = label + " (" + amount + " / " + String.format("%.1f", bbAmount) + " BB)";
                main.getOptions().addOption(optionText, "poker_raise_" + amount);
            }
        };
        
        // 1. BB (minimum raise)
        int bbRaise = bigBlind;
        addBetOption.accept(bbRaise, "Big Blind");
        
        // 2. Half Pot
        int halfPot = potSize / 2;
        if (halfPot > bbRaise) {
            addBetOption.accept(halfPot, "Half Pot");
        }
        
        // 3. Pot
        int potBet = potSize;
        if (potBet > halfPot) {
            addBetOption.accept(potBet, "Pot");
        }
        
        // 4. 2x Pot
        int twoXPot = potSize * 2;
        if (twoXPot > potBet && twoXPot <= playerStackAvailable) {
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
        pokerGame.processPlayerAction(PokerGame.Action.RAISE, amt);
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
        
        mem.set("$ipc_poker_suspend_time", Global.getSector().getClock().getTimestamp());
        
        main.getTextPanel().addPara("You stand up abruptly. 'Hold that thought! I'll be right back!'", Color.YELLOW);
        main.getTextPanel().addPara("The IPC Dealer raises an eyebrow but nods slowly. 'The cards will stay as they are.'", Color.CYAN);
        main.getTextPanel().addPara("'Don't be long. We have other customers waiting.'", Color.GRAY);
        main.getOptions().clearOptions();
        main.getOptions().addOption("Leave", "leave_now");
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
            long currentTime = Global.getSector().getClock().getTimestamp();
            float daysAway = (currentTime - suspendTime) / 30f;
            
            main.getTextPanel().addPara("The IPC Dealer looks at you with a mix of irritation and resignation.", Color.CYAN);
            main.getTextPanel().addPara("'Ah, you've returned. We've been standing here waiting for you for " + String.format("%.1f", daysAway) + " days.'", Color.YELLOW);
            main.getTextPanel().addPara("'The cards are still where you left them. Let's continue... grudgingly.'", Color.GRAY);
            
            updateUI();
        } else {
            setupGame();
        }
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
            endHand(state.folder == PokerGame.CurrentPlayer.OPPONENT);
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
            endHand(true);
        } else if (cmp < 0) {
            main.getTextPanel().addPara("DEFEAT. The IPC Dealer wins.", Color.RED);
            state.opponentStack += state.pot; // Award pot to opponent
            main.getTextPanel().addPara("Dealer won " + state.pot + " Stargems.", Color.RED);
            state.pot = 0;
            main.getTextPanel().addPara("Your Stack: " + state.playerStack + " Stargems", Color.CYAN);
            main.getTextPanel().addPara("Opponent Stack: " + state.opponentStack + " Stargems", Color.ORANGE);
            endHand(false);
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
            endHand(false);
        }
    }

    private void displayColoredCards(List<PokerGameLogic.Card> cards) {
        main.textPanel.setFontInsignia();
        StringBuilder cardText = new StringBuilder();
        for (int i = 0; i < cards.size(); i++) {
            PokerGameLogic.Card c = cards.get(i);
            Color suitColor;
            switch (c.suit) {
                case HEARTS: suitColor = Color.RED; break;
                case DIAMONDS: suitColor = Color.BLUE; break;
                case CLUBS: suitColor = Color.GREEN; break;
                default: suitColor = Color.GRAY; break;
            }
            cardText.setLength(0);
            cardText.append(c.toString());
            if (i < cards.size() - 1) cardText.append(" ");
            main.textPanel.addPara(cardText.toString(), suitColor);
        }
    }

    private void returnStacks() {
        if (pokerGame != null) {
            CasinoVIPManager.addToBalance(pokerGame.getState().playerStack);
            pokerGame.getState().playerStack = 0;
        }
    }
    
    private void endHand(boolean playerWon) {
        if (pokerGame == null) return;
        PokerGame.PokerState state = pokerGame.getState();
        
        if (playerWon) {
            // Wait, chips are in stack now. Payout happens when leaving table?
            // Or does "You win the pot" mean it goes to wallet?
            // Original code: CasinoVIPManager.addToBalance(potSize);
            // But this is a tournament/stack style game.
            // If the user wants to cash out, they "Leave".
            // So we just update stacks.
            main.getTextPanel().addPara("You win the pot of " + state.pot + " Stargems!", Color.CYAN);
            // Logic handled in determineWinner for stack update
        }
        
        if (state.playerStack < CasinoConfig.POKER_BIG_BLIND) {
            main.getTextPanel().addPara("You're out of chips! Game over.", Color.RED);
            returnStacks(); // Returns 0 basically
            // Close game
            main.getOptions().clearOptions();
            main.getOptions().addOption("Leave Table", "back_menu");
        } else if (state.opponentStack < CasinoConfig.POKER_BIG_BLIND) {
            main.getTextPanel().addPara("Opponent is out of chips! You win!", Color.GREEN);
            returnStacks(); // Cash out
            main.getOptions().clearOptions();
            main.getOptions().addOption("Leave Table", "back_menu");
        } else {
            main.getOptions().clearOptions();
            main.getOptions().addOption("Next Hand", "next_hand");
            main.getOptions().addOption("Leave Table", "back_menu");
        }
    }
}
