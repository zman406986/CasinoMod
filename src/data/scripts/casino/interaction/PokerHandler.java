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
                CasinoVIPManager.addStargems(pokerGame.getState().playerStack);
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
            int stackSize = "all".equals(stackStr) ? CasinoVIPManager.getStargems() : Integer.parseInt(stackStr);
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
        
        int currentGems = CasinoVIPManager.getStargems();
        int[] stackSizes = CasinoConfig.POKER_STACK_SIZES;
        String[] stackLabels = {"Small", "Medium", "Large", "Huge"};
        
        for (int i = 0; i < stackSizes.length && i < stackLabels.length; i++) {
            if (currentGems >= stackSizes[i]) {
                main.options.addOption(stackLabels[i] + " Stack (" + stackSizes[i] + " Stargems)", "poker_stack_" + stackSizes[i]);
            }
        }
        

        int minRequiredGems = CasinoConfig.POKER_BIG_BLIND;
        if (currentGems >= minRequiredGems) {
            main.options.addOption("Bring All My Stargems (" + currentGems + ")", "poker_stack_all");
        } else if (currentGems > 0 && currentGems < minRequiredGems) {
            main.textPanel.addPara("You need at least " + minRequiredGems + " Stargems (Big Blind) to play.", Color.RED);
        }
        
        main.options.addOption("How to Play Poker", "how_to_poker");
        main.options.addOption("Wait... (Cancel)", "back_menu");
        main.setState(CasinoInteraction.State.POKER);
    }

    public void setupGame() {
        int defaultStack = CasinoConfig.POKER_STACK_SIZES.length > 0 ? CasinoConfig.POKER_STACK_SIZES[0] : 10000;
        setupGame(defaultStack);
    }
    
    public void setupGame(int stackSize) {
        playerWallet = CasinoVIPManager.getStargems();
        
        // Check if player has enough in wallet for the desired stack
        if (playerWallet < stackSize) {
            main.textPanel.addPara("Not enough Stargems in wallet! You have " + playerWallet + " but requested " + stackSize + ".", Color.RED);
            showPokerConfirm(); // Return to the confirmation screen
            return;
        }
        
        // Deduct the stack from wallet
        CasinoVIPManager.addStargems(-stackSize);
        
        int opponentStack = Math.max(CasinoConfig.POKER_DEFAULT_OPPONENT_STACK, stackSize);
        
        pokerGame = new PokerGame(stackSize, opponentStack, CasinoConfig.POKER_SMALL_BLIND, CasinoConfig.POKER_BIG_BLIND);
        
        updateUI();
    }

    public void updateUI() {
        if (pokerGame == null) return;
        PokerGame.PokerState state = pokerGame.getState();
        
        main.getOptions().clearOptions();
        main.getTextPanel().addPara("------------------------------------------------");
        main.getTextPanel().addPara("Pot: " + state.pot + " Stargems", Color.GREEN);
        main.getTextPanel().addPara("Your Stack: " + state.playerStack + " Stargems", Color.CYAN);
        main.getTextPanel().addPara("Opponent Stack: " + state.opponentStack + " Stargems", Color.ORANGE);
        
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
        main.getTextPanel().addPara("You fold. The IPC Dealer scoops the pot.", Color.GRAY);
        // End hand logic handled by pokerGame state check in updateGameState or similar?
        // Actually pokerGame.processPlayerAction(FOLD) sets round to SHOWDOWN.
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
        
        if (playerStackAvailable >= 100) main.getOptions().addOption("Raise 100", "poker_raise_100");
        if (playerStackAvailable >= 500) main.getOptions().addOption("Raise 500", "poker_raise_500");
        if (playerStackAvailable >= 2000) main.getOptions().addOption("Raise 2000", "poker_raise_2000");
        
        // Percentage options
        int tenPercent = (playerStackAvailable * 10) / 100;
        if (tenPercent > 0) main.getOptions().addOption("Raise " + tenPercent + " (10% of stack)", "poker_raise_" + tenPercent);
        
        int fiftyPercent = (playerStackAvailable * 50) / 100;
        if (fiftyPercent > 0) main.getOptions().addOption("Raise " + fiftyPercent + " (50% of stack)", "poker_raise_" + fiftyPercent);
        
        // All-in
        if (playerStackAvailable > 0) {
            main.getOptions().addOption("All-In (" + playerStackAvailable + " Stargems)", "poker_raise_" + playerStackAvailable);
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
        
        main.getTextPanel().addPara("Opponent reveals: ");
        displayColoredCards(state.opponentHand);
        main.getTextPanel().addPara("Your Best: " + state.playerHandRank.name());
        main.getTextPanel().addPara("Opponent Best: " + state.opponentHandRank.name());
        
        main.getTextPanel().addPara("Your Stack: " + state.playerStack + " Stargems", Color.CYAN);
        main.getTextPanel().addPara("Opponent Stack: " + state.opponentStack + " Stargems", Color.ORANGE);
        
        // HandRank value: higher is better? 
        // PokerGame logic uses value for comparison
        int playerVal = state.playerHandRank.value;
        int oppVal = state.opponentHandRank.value;
        
        // We need detailed comparison (tie breakers) which PokerGame.evaluate() does.
        // Re-evaluate to get HandScore for comparison
        PokerGame.PokerGameLogic.HandScore playerScore = PokerGame.PokerGameLogic.evaluate(state.playerHand, state.communityCards);
        PokerGame.PokerGameLogic.HandScore oppScore = PokerGame.PokerGameLogic.evaluate(state.opponentHand, state.communityCards);
        
        int cmp = playerScore.compareTo(oppScore);
        
        if (cmp > 0) {
            main.getTextPanel().addPara("VICTORY! You take the pot.", Color.CYAN);
            state.playerStack += state.pot; // Award pot to player stack
            endHand(true);
        } else if (cmp < 0) {
            main.getTextPanel().addPara("DEFEAT. The IPC Dealer wins.", Color.RED);
            state.opponentStack += state.pot; // Award pot to opponent
            endHand(false);
        } else {
            main.getTextPanel().addPara("SPLIT POT. It's a draw.", Color.YELLOW);
            state.playerStack += state.pot / 2;
            state.opponentStack += state.pot / 2;
            CasinoVIPManager.addStargems(state.pot / 2); // Split logic for wallet?
            // Actually chips stay on table mostly. But endHand(false) adds payout?
            // Original code: CasinoVIPManager.addStargems(potSize / 2);
            endHand(false); 
        }
        state.pot = 0;
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
            CasinoVIPManager.addStargems(pokerGame.getState().playerStack);
            pokerGame.getState().playerStack = 0;
        }
    }
    
    private void endHand(boolean playerWon) {
        if (pokerGame == null) return;
        PokerGame.PokerState state = pokerGame.getState();
        
        if (playerWon) {
            // Wait, chips are in stack now. Payout happens when leaving table?
            // Or does "You win the pot" mean it goes to wallet?
            // Original code: CasinoVIPManager.addStargems(potSize);
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
    }}