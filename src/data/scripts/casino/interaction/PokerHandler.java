package data.scripts.casino.interaction;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import data.scripts.casino.CasinoConfig;
import data.scripts.casino.CasinoVIPManager;
import data.scripts.CasinoUIPanels;
import data.scripts.casino.PokerGame;
import data.scripts.casino.PokerGame.PokerGameLogic;


import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Manages the Texas Hold'em interaction flow
 * Now includes player aggression tracking for AI adaptation
 */
public class PokerHandler {

    private final CasinoInteraction main;
    
    // State
    /**
     * The deck of cards used for the game
     */
    protected PokerGameLogic.Deck deck;
    
    /**
     * Cards in the player's hand
     */
    protected List<PokerGameLogic.Card> playerHand;
    
    /**
     * Cards in the opponent's (AI's) hand
     */
    protected List<PokerGameLogic.Card> opponentHand;
    
    /**
     * Community cards shared by both players
     */
    protected List<PokerGameLogic.Card> communityCards;
    
    /**
     * Current size of the pot
     */
    protected int potSize;
    
    /**
     * Player's total bet in the current round
     */
    protected int playerBet;
    
    /**
     * Opponent's total bet in the current round
     */
    protected int opponentBet;
    
    /**
     * Amount needed to call the current bet
     */
    protected int currentBetToCall; 
    
    /**
     * Player's remaining chip stack
     */
    protected int playerWallet; // Player's total money outside the current game
    protected int playerStack; 
    
    /**
     * Opponent's remaining chip stack
     */
    protected int opponentStack; 
    
    /**
     * AI opponent that makes decisions
     */
    protected PokerGame.SimplePokerAI ai;
    
    /**
     * Big blind amount from config
     */
    protected int bigBlind = CasinoConfig.POKER_BIG_BLIND;
    
    /**
     * Small blind amount from config
     */
    protected int smallBlind = CasinoConfig.POKER_SMALL_BLIND;
    
    /**
     * Tracks which player is the dealer
     */
    protected boolean playerIsDealer;

    private final Map<String, OptionHandler> handlers = new HashMap<>();
    private final Map<Predicate<String>, OptionHandler> predicateHandlers = new HashMap<>();
    
    public PokerHandler(CasinoInteraction main) {
        this.main = main;
        this.ai = new PokerGame.SimplePokerAI();
        initializeHandlers();
    }
    
    private void initializeHandlers() {
        // Exact match handlers
        handlers.put("play", option -> showPokerConfirm());
        handlers.put("confirm_poker_ante", option -> setupGame());
        handlers.put("next_hand", option -> setupGame());
        handlers.put("how_to_poker", option -> main.help.showPokerHelp());
        handlers.put("poker_call", option -> handlePokerCall());
        handlers.put("poker_check", option -> handlePokerCheck());
        handlers.put("poker_fold", option -> handlePokerFold());
        handlers.put("poker_raise_menu", option -> showRaiseOptions());
        handlers.put("poker_back_action", option -> updateUI());
        handlers.put("poker_suspend", option -> suspendGame());
        handlers.put("poker_back_to_menu", option -> {
            // Go back to the poker menu (the initial confirmation screen)
            showPokerConfirm();
        });
        handlers.put("leave_now", option -> main.showMenu());
        handlers.put("back_menu", option -> main.showMenu());
        
        // Predicate-based handlers for pattern matching
        predicateHandlers.put(option -> option.startsWith("poker_raise_"), option -> {
            int amt = Integer.parseInt(option.replace("poker_raise_", ""));
            performRaise(amt);
        });
        
        // Stack selection handlers
        predicateHandlers.put(option -> option.startsWith("poker_stack_"), option -> {
            String stackStr = option.replace("poker_stack_", "");
            int stackSize = "all".equals(stackStr) ? CasinoVIPManager.getStargems() : Integer.parseInt(stackStr);
            setupGame(stackSize);
        });
    }

    /**
     * Handles poker game options including ante, actions, and navigation
     */
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

    /**
     * Shows confirmation dialog for poker ante
     */
    public void showPokerConfirm() {
        main.options.clearOptions();
        main.textPanel.addPara("The IPC Dealer prepares to deal. How big of a stack would you like to bring to the table?", Color.YELLOW);
        
        int currentGems = CasinoVIPManager.getStargems();
        
        // Offer different stack sizes based on player's available gems
        if (currentGems >= 1000) {
            main.options.addOption("Small Stack (1,000 Stargems)", "poker_stack_1000");
        }
        if (currentGems >= 5000) {
            main.options.addOption("Medium Stack (5,000 Stargems)", "poker_stack_5000");
        }
        if (currentGems >= 10000) {
            main.options.addOption("Large Stack (10,000 Stargems)", "poker_stack_10000");
        }
        if (currentGems >= 25000) {
            main.options.addOption("Huge Stack (25,000 Stargems)", "poker_stack_25000");
        }
        
        // Add custom stack option if player has gems
        if (currentGems > 0) {
            main.options.addOption("Bring All My Stargems (" + currentGems + ")", "poker_stack_all");
        }
        
        main.options.addOption("How to Play Poker", "how_to_poker");  // Add help option
        main.options.addOption("Wait... (Cancel)", "back_menu");
        main.setState(CasinoInteraction.State.POKER);
    }

    /**
     * Sets up a new poker hand with fresh cards and initial bets
     */
    public void setupGame() {
        // Default to 10000 stack if called without parameters
        setupGame(10000);
    }
    
    public void setupGame(int stackSize) {
        deck = new PokerGameLogic.Deck();
        deck.shuffle();
        
        // Play shuffle cards sound
        // Removed sound effect as it was removed from sounds.json
// Global.getSoundPlayer().playUISound("shuffle_cards", 1f, 1f);
        
        playerHand = new ArrayList<>();
        opponentHand = new ArrayList<>();
        communityCards = new ArrayList<>();
        
        playerHand.add(deck.draw());
        opponentHand.add(deck.draw());
        playerHand.add(deck.draw());
        opponentHand.add(deck.draw());
        
        playerWallet = CasinoVIPManager.getStargems();
        
        // Check if player has enough in wallet for the desired stack
        if (playerWallet < stackSize) {
            main.textPanel.addPara("Not enough Stargems in wallet! You have " + playerWallet + " but requested " + stackSize + ".", Color.RED);
            showPokerConfirm(); // Return to the confirmation screen
            return;
        }
        
        // Deduct the stack from wallet and set it as player's stack
        CasinoVIPManager.addStargems(-stackSize);
        playerStack = stackSize;
        
        // Ensure opponent has at least 10k gems regardless of player's situation
        opponentStack = Math.max(10000, playerStack); 
        
        potSize = 0;
        currentBetToCall = 0;
        playerBet = 0;
        opponentBet = 0;
        
        playerIsDealer = !playerIsDealer; 
        
        // Check if player has enough gems for blinds before setting up the game
        if (playerIsDealer) {
            // Player is dealer, so they post small blind, opponent posts big blind
            if (playerStack < smallBlind) {
                main.textPanel.addPara("Not enough Stargems in stack for small blind! You need " + smallBlind + " but only have " + playerStack + ".", Color.RED);
                returnStacks();
                showPokerConfirm(); // Return to the confirmation screen
                return;
            }
            playerStack -= smallBlind; 
            playerBet = smallBlind;
            
            if (opponentStack < bigBlind) {
                main.textPanel.addPara("Opponent doesn't have enough for big blind! Game cannot continue.", Color.RED);
                returnStacks();
                showPokerConfirm(); // Return to the confirmation screen
                return;
            }
            opponentStack -= bigBlind; 
            opponentBet = bigBlind; 
        } else {
            // Opponent is dealer, so opponent posts small blind, player posts big blind
            if (opponentStack < smallBlind) {
                main.textPanel.addPara("Opponent doesn't have enough for small blind! Game cannot continue.", Color.RED);
                returnStacks();
                showPokerConfirm(); // Return to the confirmation screen
                return;
            }
            opponentStack -= smallBlind; 
            opponentBet = smallBlind;
            
            if (playerStack < bigBlind) {
                main.textPanel.addPara("Not enough Stargems in stack for big blind! You need " + bigBlind + " but only have " + playerStack + ".", Color.RED);
                returnStacks();
                showPokerConfirm(); // Return to the confirmation screen
                return;
            }
            playerStack -= bigBlind;
            playerBet = bigBlind;
        }
        
        potSize = smallBlind + bigBlind;
        currentBetToCall = bigBlind;
        
        // Reset AI aggression tracking for new game
        this.ai = new PokerGame.SimplePokerAI();
        
        // Visual Panel
        main.dialog.getVisualPanel().showCustomPanel(400, 500, new CasinoUIPanels.PokerUIPanel(main));
        
        updateUI();
    }

    /**
     * Updates the poker UI with current game state
     */
    public void updateUI() {
        main.getOptions().clearOptions();
        main.getTextPanel().addPara("------------------------------------------------");
        main.getTextPanel().addPara("Pot: " + potSize + " Stargems", Color.GREEN);
        main.getTextPanel().addPara("Your Stack: " + playerStack + " Stargems", Color.CYAN);
        main.getTextPanel().addPara("Opponent Stack: " + opponentStack + " Stargems", Color.ORANGE);
        
        main.getTextPanel().addPara("Your Hand: ");
        displayColoredCards(playerHand); // This now adds the colored cards directly to the panel
        if (!communityCards.isEmpty()) {
            main.getTextPanel().addPara("Community: ");
            displayColoredCards(communityCards); // This now adds the colored cards directly to the panel
        }
        
        int callAmount = opponentBet - playerBet;
        if (callAmount > 0) {
            // Check if player can cover the full call amount
            if (playerStack >= callAmount) {
                main.getOptions().addOption("Call (" + callAmount + ")", "poker_call");
            } else if (playerStack > 0) {
                // Player can't call full amount but has some chips - show all-in option
                main.getOptions().addOption("Call All-In (" + playerStack + " Stargems)", "poker_call");
            } else {
                // Player has no chips left
                main.getOptions().addOption("Call (0 Stargems)", "poker_call");
            }
            main.getOptions().addOption("Fold", "poker_fold");
        } else {
            main.getOptions().addOption("Check", "poker_check");
        }
        
        // Only show raise option if player has chips and the opponent hasn't gone all-in with a huge bet
        if (playerStack > 0 && opponentBet - playerBet < playerStack) {
            main.getOptions().addOption("Raise", "poker_raise_menu");
        }
        
        main.getOptions().addOption("How to Play Poker", "how_to_poker");  // Add help option during gameplay
        main.getOptions().addOption("Back to Poker Menu", "poker_back_to_menu"); // Add option to return to poker menu
        main.getOptions().addOption("Tell Them to Wait (Suspend)", "poker_suspend");
    }

    /**
     * Handles poker-specific actions like calls, checks, folds, and raises
     */
    public void handlePokerCall() {
        int callAmount = opponentBet - playerBet;
        
        // Calculate how much the player can actually call
        int actualCallAmount = Math.min(callAmount, playerStack);
        
        // Deduct the actual call amount from player's stack
        playerStack -= actualCallAmount;
        playerBet += actualCallAmount;
        potSize += actualCallAmount;

        // Track player action (not a raise, not a fold)
        ai.trackPlayerAction(false, false);
        
        // After player calls, betting round is complete - move to next street
        advanceStreet();
    }
    
    public void handlePokerCheck() {
        // Track player check action (not a raise, not a fold)
        ai.trackPlayerAction(false, false);
        
        // After player checks, let AI respond
        PokerGame.SimplePokerAI.AIResponse res = ai.decide(opponentHand, communityCards, playerBet - opponentBet, potSize, opponentStack, playerStack);
        if (res.action == PokerGame.SimplePokerAI.Action.FOLD) {
            // Check if AI is already all-in before allowing fold
            if (opponentStack <= 0) {
                // Cannot fold when already all-in
                // Calculate how much is needed to call
                int neededToCall = playerBet - opponentBet;
                if (neededToCall > 0) {
                    // Since opponent is all-in, they can only call with what they have left
                    // But if opponentStack <= 0, they can't call anything more, so just advance
                    if (opponentStack > 0) {
                        int callAmount = Math.min(neededToCall, opponentStack);
                        if (callAmount > 0) {
                            opponentStack -= callAmount;
                            opponentBet += callAmount;
                            potSize += callAmount;
                            main.getTextPanel().addPara("Opponent calls (all-in) with " + callAmount + " Stargems.", Color.YELLOW);
                        }
                    }
                    // In all-in situations, we should still advance to next street
                    advanceStreet();
                } else {
                    // If no additional call is needed, advance to next street
                    advanceStreet();
                }
            } else {
                main.getTextPanel().addPara("Opponent folds! You win.", Color.CYAN);
                endHand(true);
            }
        } else if (res.action == PokerGame.SimplePokerAI.Action.RAISE) {
            // AI raises after player checks
            int raiseAmount = Math.min(res.raiseAmount, opponentStack);
            if (raiseAmount > 0) {
                opponentStack -= raiseAmount;
                opponentBet += raiseAmount;
                potSize += raiseAmount;
                main.getTextPanel().addPara("Opponent raises by " + raiseAmount + " Stargems.", Color.YELLOW);
                updateUI(); // Now player must respond to the raise
            } else {
                // If AI can't raise, they effectively check
                advanceStreet();
            }
        } else if (res.action == PokerGame.SimplePokerAI.Action.CALL) {
            // This shouldn't typically happen after a check, but handle it
            int callAmount = Math.min(playerBet - opponentBet, opponentStack);
            if (callAmount > 0) {
                opponentStack -= callAmount;
                opponentBet += callAmount;
                potSize += callAmount;
                main.getTextPanel().addPara("Opponent calls with " + callAmount + " Stargems.", Color.YELLOW);
            }
            advanceStreet(); // Both players checked, move to next street
        } else if (res.action == PokerGame.SimplePokerAI.Action.CHECK) {
            main.getTextPanel().addPara("Opponent checks.", Color.YELLOW);
            advanceStreet(); // Both players checked, move to next street
        }
    }
    
    public void handlePokerFold() {
        main.getTextPanel().addPara("You fold. The IPC Dealer scoops the pot.", Color.GRAY);
        // Track player fold action
        ai.trackPlayerAction(false, true);
        endHand(false);
    }
    
    public void handlePokerAction(String option) {
        if ("poker_call".equals(option)) {
            handlePokerCall();
        } else if ("poker_check".equals(option)) {
            // Track player check action (not a raise, not a fold)
            ai.trackPlayerAction(false, false);
            
            // After player checks, let AI respond
            PokerGame.SimplePokerAI.AIResponse res = ai.decide(opponentHand, communityCards, playerBet - opponentBet, potSize, opponentStack, playerStack);
            if (res.action == PokerGame.SimplePokerAI.Action.FOLD) {
                // Check if AI is already all-in before allowing fold
                if (opponentStack <= 0) {
                    // Cannot fold when already all-in
                    // Calculate how much is needed to call
                    int neededToCall = playerBet - opponentBet;
                    if (neededToCall > 0) {
                        // Since opponent is all-in, they can only call with what they have left
                        // But if opponentStack <= 0, they can't call anything more, so just advance
                        if (opponentStack > 0) {
                            int callAmount = Math.min(neededToCall, opponentStack);
                            if (callAmount > 0) {
                                opponentStack -= callAmount;
                                opponentBet += callAmount;
                                potSize += callAmount;
                                main.getTextPanel().addPara("Opponent calls (all-in) with " + callAmount + " Stargems.", Color.YELLOW);
                            }
                        }
                        // In all-in situations, we should still advance to next street
                        advanceStreet();
                    } else {
                        // If no additional call is needed, advance to next street
                        advanceStreet();
                    }
                } else {
                    main.getTextPanel().addPara("Opponent folds! You win.", Color.CYAN);
                    endHand(true);
                }
            } else if (res.action == PokerGame.SimplePokerAI.Action.RAISE) {
                // AI raises after player checks
                int raiseAmount = Math.min(res.raiseAmount, opponentStack);
                if (raiseAmount > 0) {
                    opponentStack -= raiseAmount;
                    opponentBet += raiseAmount;
                    potSize += raiseAmount;
                    main.getTextPanel().addPara("Opponent raises by " + raiseAmount + " Stargems.", Color.YELLOW);
                    updateUI(); // Now player must respond to the raise
                } else {
                    // If AI can't raise, they effectively check
                    advanceStreet();
                }
            } else if (res.action == PokerGame.SimplePokerAI.Action.CALL) {
                // This shouldn't typically happen after a check, but handle it
                int callAmount = Math.min(playerBet - opponentBet, opponentStack);
                if (callAmount > 0) {
                    opponentStack -= callAmount;
                    opponentBet += callAmount;
                    potSize += callAmount;
                    main.getTextPanel().addPara("Opponent calls with " + callAmount + " Stargems.", Color.YELLOW);
                }
                advanceStreet();
            } else if (res.action == PokerGame.SimplePokerAI.Action.CHECK) {
                main.getTextPanel().addPara("Opponent checks.", Color.YELLOW);
                advanceStreet(); // Both players checked, move to next street
            }
        } else if ("poker_fold".equals(option)) {
            main.getTextPanel().addPara("You fold. The IPC Dealer scoops the pot.", Color.GRAY);
            // Track player fold action
            ai.trackPlayerAction(false, true);
            endHand(false);
        } else if ("poker_raise_menu".equals(option)) {
            showRaiseOptions();
        } else if (option.startsWith("poker_raise_")) {
            int amt = Integer.parseInt(option.replace("poker_raise_", ""));
            performRaise(amt);
        } else if ("poker_back_action".equals(option)) {
            updateUI();
        } else if ("poker_suspend".equals(option)) {
            suspendGame();
        } else if ("leave_now".equals(option)) {
            main.showMenu();
        } else if ("back_menu".equals(option)) {
            main.showMenu();
        }
    }

    /**
     * Shows available raise options based on configuration
     */
    private void showRaiseOptions() {
        main.getOptions().clearOptions();
        
        // Use consistent betting amounts (100, 500, 2000) plus percentage options
        // Use player's current stack instead of wallet
        int playerStackAvailable = playerStack;
        
        if (playerStackAvailable >= 100) {
            main.getOptions().addOption("Raise 100", "poker_raise_100");
        }
        if (playerStackAvailable >= 500) {
            main.getOptions().addOption("Raise 500", "poker_raise_500");
        }
        if (playerStackAvailable >= 2000) {
            main.getOptions().addOption("Raise 2000", "poker_raise_2000");
        }
        
        // Add percentage-based options
        int tenPercent = (playerStackAvailable * 10) / 100;
        if (tenPercent > 0 && playerStackAvailable >= tenPercent) {
            main.getOptions().addOption("Raise " + tenPercent + " (10% of stack)", "poker_raise_" + tenPercent);
        }
        
        int fiftyPercent = (playerStackAvailable * 50) / 100;
        if (fiftyPercent > 0 && playerStackAvailable >= fiftyPercent) {
            main.getOptions().addOption("Raise " + fiftyPercent + " (50% of stack)", "poker_raise_" + fiftyPercent);
        }
        
        // Also include the original configured raise amounts for variety
        for (int r : CasinoConfig.POKER_RAISE_AMOUNTS) {
            if (r != 100 && r != 500 && r != 2000) { // Avoid duplicates
                if (playerStackAvailable >= r) {
                    main.getOptions().addOption("Raise " + r, "poker_raise_" + r);
                }
            }
        }
        
        // Add all-in option if player has remaining stack
        if (playerStackAvailable > 0) {
            main.getOptions().addOption("All-In (" + playerStackAvailable + " Stargems)", "poker_raise_" + playerStackAvailable);
        }
        
        main.getOptions().addOption("Back", "poker_back_action");
    }

    /**
     * Performs a raise action with specified amount
     */
    private void performRaise(int amt) {
        // Calculate how much the player can actually raise
        int actualRaiseAmount = Math.min(amt, playerStack);
        
        // Deduct the actual raise amount from player's stack
        playerStack -= actualRaiseAmount;
        playerBet += actualRaiseAmount;
        potSize += actualRaiseAmount;
        
        // Track player raise action
        ai.trackPlayerAction(true, false);
        
        // After player raises, let AI respond
        PokerGame.SimplePokerAI.AIResponse res = ai.decide(opponentHand, communityCards, playerBet - opponentBet, potSize, opponentStack, playerStack);
        if (res.action == PokerGame.SimplePokerAI.Action.FOLD) {
            // Check if AI is already all-in before allowing fold
            if (opponentStack <= 0) {
                // Cannot fold when already all-in
                // Calculate how much is needed to call
                int neededToCall = playerBet - opponentBet;
                if (neededToCall > 0) {
                    // Since opponent is all-in, they can only call with what they have left
                    // But if opponentStack <= 0, they can't call anything more, so just advance
                    if (opponentStack > 0) {
                        int callAmount = Math.min(neededToCall, opponentStack);
                        if (callAmount > 0) {
                            opponentStack -= callAmount;
                            opponentBet += callAmount;
                            potSize += callAmount;
                            main.getTextPanel().addPara("Opponent calls (all-in) with " + callAmount + " Stargems.", Color.YELLOW);
                        }
                    }
                    // In all-in situations, we should still advance to next street
                    advanceStreet();
                } else {
                    // If no additional call is needed, advance to next street
                    advanceStreet();
                }
            } else {
                main.getTextPanel().addPara("Opponent folds! You win.", Color.CYAN);
                endHand(true);
            }
        } else if (res.action == PokerGame.SimplePokerAI.Action.RAISE) {
            // AI raises against the player's raise - this continues the betting round
            int raiseAmount = Math.min(res.raiseAmount, opponentStack);
            if (raiseAmount > 0) {
                opponentStack -= raiseAmount;
                opponentBet += raiseAmount;
                potSize += raiseAmount;
                main.getTextPanel().addPara("Opponent raises by " + raiseAmount + " Stargems.", Color.YELLOW);
                updateUI(); // Now player must respond again
            } else {
                // AI can't raise, so they call
                int callAmount = Math.min(playerBet - opponentBet, opponentStack);
                if (callAmount > 0) {
                    opponentStack -= callAmount;
                    opponentBet += callAmount;
                    potSize += callAmount;
                    main.getTextPanel().addPara("Opponent calls with " + callAmount + " Stargems.", Color.YELLOW);
                }
                advanceStreet(); // Betting round complete
            }
        } else if (res.action == PokerGame.SimplePokerAI.Action.CALL) {
            // AI calls the player's raise
            int callAmount = Math.min(playerBet - opponentBet, opponentStack);
            if (callAmount > 0) {
                opponentStack -= callAmount;
                opponentBet += callAmount;
                potSize += callAmount;
                main.getTextPanel().addPara("Opponent calls your raise with " + callAmount + " Stargems.", Color.YELLOW);
            }
            advanceStreet(); // Betting round complete
        } else if (res.action == PokerGame.SimplePokerAI.Action.CHECK) {
            // This shouldn't happen after a raise, but just in case
            advanceStreet();
        }
    }
    
    /**
     * Suspends the current game to resume later
     */
    private void suspendGame() {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        mem.set("$ipc_suspended_game_type", "Poker");
        
        // Save basic game state (avoid storing complex objects that can't be serialized)
        mem.set("$ipc_poker_pot_size", potSize);
        mem.set("$ipc_poker_player_bet", playerBet);
        mem.set("$ipc_poker_opponent_bet", opponentBet);
        mem.set("$ipc_poker_current_bet_to_call", currentBetToCall);
        mem.set("$ipc_poker_player_stack", playerStack);
        mem.set("$ipc_poker_opponent_stack", opponentStack);
        mem.set("$ipc_poker_player_is_dealer", playerIsDealer);
        
        main.getTextPanel().addPara("The Dealer nods. 'The cards will stay as they are. Don't be long.'", Color.YELLOW);
        main.getOptions().clearOptions();
        main.getOptions().addOption("Leave", "leave_now");
    }

    /**
     * Restores a suspended poker game from memory
     */
    public void restoreSuspendedGame() {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        
        // Check if poker game state exists in memory
        if (mem.contains("$ipc_poker_pot_size")) {
            // Restore the game state from memory
            potSize = mem.getInt("$ipc_poker_pot_size");
            playerBet = mem.getInt("$ipc_poker_player_bet");
            opponentBet = mem.getInt("$ipc_poker_opponent_bet");
            currentBetToCall = mem.getInt("$ipc_poker_current_bet_to_call");
            playerStack = mem.getInt("$ipc_poker_player_stack");
            opponentStack = mem.getInt("$ipc_poker_opponent_stack");
            playerIsDealer = mem.getBoolean("$ipc_poker_player_is_dealer");
            
            // Since we can't restore the actual cards/deck state, we'll start a new hand but preserve the stakes
            // Create a fresh deck and hands
            deck = new PokerGameLogic.Deck();
            deck.shuffle();
            playerHand = new ArrayList<>();
            opponentHand = new ArrayList<>();
            communityCards = new ArrayList<>();
            
            // Deal new cards
            playerHand.add(deck.draw());
            opponentHand.add(deck.draw());
            playerHand.add(deck.draw());
            opponentHand.add(deck.draw());
            
            // We already restored the stack values from memory, so we don't reset them
            
            // Restore the AI state as much as possible
            this.ai = new PokerGame.SimplePokerAI();
            
            // Visual Panel
            main.dialog.getVisualPanel().showCustomPanel(400, 500, new CasinoUIPanels.PokerUIPanel(main));
            
            // Inform player that game has been resumed
            main.getTextPanel().addPara("Resumed poker game. Preserved stakes and stacks.", Color.YELLOW);
            
            // Update UI with restored game state
            updateUI();
        } else {
            // If no stored state, start a fresh game
            setupGame();
        }
    }

    /**
     * Advances the game to next street (flop, turn, river)
     */
    private void advanceStreet() {
        // Reset betting round tracking for the new street
        playerBet = 0;
        opponentBet = 0;
        
        if (communityCards.isEmpty()) { // Flop
            for(int i=0; i<3; i++) communityCards.add(deck.draw());
        } else if (communityCards.size() == 3) { // Turn
            communityCards.add(deck.draw());
        } else if (communityCards.size() == 4) { // River
            communityCards.add(deck.draw());
        } else {
            determineWinner();
            return;
        }
        updateUI();
    }

    /**
     * Determines the winner of the current hand
     */
    private void determineWinner() {
        PokerGame.PokerGameLogic.HandScore playerResult = PokerGame.PokerGameLogic.evaluate(playerHand, communityCards);
        PokerGame.PokerGameLogic.HandScore opponentResult = PokerGame.PokerGameLogic.evaluate(opponentHand, communityCards);
        
        main.getTextPanel().addPara("Opponent reveals: ");
        displayColoredCards(opponentHand); // This now adds the colored cards directly to the panel
        main.getTextPanel().addPara("Your Best: " + playerResult.rank.name());
        main.getTextPanel().addPara("Opponent Best: " + opponentResult.rank.name());
        
        // Show stack sizes before determining winner
        main.getTextPanel().addPara("Your Stack: " + playerStack + " Stargems", Color.CYAN);
        main.getTextPanel().addPara("Opponent Stack: " + opponentStack + " Stargems", Color.ORANGE);
        
        int cmp = playerResult.compareTo(opponentResult);
        if (cmp > 0) {
            main.getTextPanel().addPara("VICTORY! You take the pot.", Color.CYAN);
            endHand(true);
        } else if (cmp < 0) {
            main.getTextPanel().addPara("DEFEAT. The IPC Dealer wins.", Color.RED);
            endHand(false);
        } else {
            main.getTextPanel().addPara("SPLIT POT. It's a draw.", Color.YELLOW);
            CasinoVIPManager.addStargems(potSize / 2);
            endHand(false); 
        }
    }

    /**
     * Converts card list to string representation
     */
    private String getCardsString(List<PokerGame.PokerGameLogic.Card> cards) {
        StringBuilder sb = new StringBuilder();
        for (PokerGame.PokerGameLogic.Card c : cards) sb.append(c.toString()).append(" ");
        return sb.toString();
    }
    
    /**
     * Displays cards with color coding for suits
     */
    private void displayColoredCards(List<PokerGame.PokerGameLogic.Card> cards) {
        main.textPanel.setFontInsignia();
        
        // Add each card separately to ensure proper highlighting
        StringBuilder cardText = new StringBuilder();
        for (int i = 0; i < cards.size(); i++) {
            PokerGame.PokerGameLogic.Card c = cards.get(i);
            
            // Determine color based on suit (distinct colors for better visibility in game)
            Color suitColor;
            switch (c.suit) {
                case HEARTS:
                    suitColor = Color.RED; // Hearts in red
                    break;
                case DIAMONDS:
                    suitColor = Color.BLUE; // Diamonds in blue (as originally coded)
                    break;
                case CLUBS:
                    suitColor = Color.GREEN; // Clubs in green
                    break;
                case SPADES:
                default:
                    suitColor = Color.DARK_GRAY; // Spades in dark gray
                    break;
            }
            
            // Add the card with its specific color
            cardText.setLength(0); // Clear the StringBuilder
            cardText.append(c.toString());
            if (i < cards.size() - 1) {
                cardText.append(" "); // Add space between cards except for the last one
            }
            
            // Add the card text with its specific color
            main.textPanel.addPara(cardText.toString(), suitColor);
        }
    }

    /**
     * Returns all remaining stacks to the player's wallet
     */
    private void returnStacks() {
        // Return player's remaining stack to wallet
        CasinoVIPManager.addStargems(playerStack);
        
        // Note: opponent's stack doesn't get returned to wallet
        // since it's the AI's own chips
    }
    
    /**
     * Ends the current hand and shows options for next action
     */
    private void endHand(boolean playerWon) {
        if (playerWon) {
            CasinoVIPManager.addStargems(potSize);
            main.getTextPanel().addPara("You win the pot of " + potSize + " Stargems!", Color.CYAN);
        }
        
        // Return both players' remaining stacks to their respective wallets/pools
        returnStacks();
        
        // Ensure stack values don't go negative
        if (playerStack < 0) playerStack = 0;
        if (opponentStack < 0) opponentStack = 0;
        
        // Play game complete sound
        // Removed sound effect as it was removed from sounds.json
// Global.getSoundPlayer().playUISound("game_complete", 1f, 1f);
        
        main.getOptions().clearOptions();
        main.getOptions().addOption("Next Hand", "next_hand");
        main.getOptions().addOption("Leave Table", "back_menu");
    }
}