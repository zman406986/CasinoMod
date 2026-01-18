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

public class PokerHandler {

    private final CasinoInteraction main;
    
    protected PokerGameLogic.Deck deck;
    protected List<PokerGameLogic.Card> playerHand;
    protected List<PokerGameLogic.Card> opponentHand;
    protected List<PokerGameLogic.Card> communityCards;
    protected int potSize;
    protected int playerBet;
    protected int opponentBet;
    protected int currentBetToCall; 
    protected int playerWallet;
    protected int playerStack; 
    protected int opponentStack; 
    protected PokerGame.SimplePokerAI ai;
    protected int bigBlind = CasinoConfig.POKER_BIG_BLIND;
    protected int smallBlind = CasinoConfig.POKER_SMALL_BLIND;
    protected boolean playerIsDealer;

    private final Map<String, OptionHandler> handlers = new HashMap<>();
    private final Map<Predicate<String>, OptionHandler> predicateHandlers = new HashMap<>();
    
    public PokerHandler(CasinoInteraction main) {
        this.main = main;
        this.ai = new PokerGame.SimplePokerAI();
        initializeHandlers();
    }
    
    private void initializeHandlers() {
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
            if (playerStack > 0) {
                CasinoVIPManager.addStargems(playerStack);
                main.getTextPanel().addPara("Returned " + playerStack + " Stargems to your wallet.", Color.YELLOW);
                playerStack = 0;
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
        
        int minRequiredGems = bigBlind;
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
        deck = new PokerGameLogic.Deck();
        deck.shuffle();
        
        // Play shuffle cards sound
        // Removed sound effect as it was removed from sounds.json
// Global.getSoundPlayer().playUISound("shuffle_cards", 1f, 1f);
        
        playerHand = new ArrayList<>();
        opponentHand = new ArrayList<>();
        communityCards = new ArrayList<>();
        
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
        
        if (opponentStack == 0 || opponentStack < bigBlind) {
            opponentStack = Math.max(CasinoConfig.POKER_DEFAULT_OPPONENT_STACK, playerStack);
        } 
        
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
        
        // Deal hole cards to each player (2 cards each, alternating)
        playerHand.add(deck.draw());
        opponentHand.add(deck.draw());
        playerHand.add(deck.draw());
        opponentHand.add(deck.draw());
        
        // Visual Panel
        main.dialog.getVisualPanel().showCustomPanel(400, 500, new CasinoUIPanels.PokerUIPanel(main));
        
        updateUI();
    }

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
        
        // Add percentage-based options based on player's available credit (remaining debt capacity) if in debt
        int availableCredit = CasinoVIPManager.getAvailableCredit();
        if (availableCredit > 0) {
            // Player has available credit, show percentage options based on that
            int tenPercent = (availableCredit * 10) / 100;
            if (tenPercent > 0 && playerStackAvailable >= tenPercent) {
                main.getOptions().addOption("Raise " + tenPercent + " (10% of remaining credit)", "poker_raise_" + tenPercent);
            }
            
            int fiftyPercent = (availableCredit * 50) / 100;
            if (fiftyPercent > 0 && playerStackAvailable >= fiftyPercent) {
                main.getOptions().addOption("Raise " + fiftyPercent + " (50% of remaining credit)", "poker_raise_" + fiftyPercent);
            }
        } else {
            // Player has no available credit, show percentage options based on current stack
            int tenPercent = (playerStackAvailable * 10) / 100;
            if (tenPercent > 0 && playerStackAvailable >= tenPercent) {
                main.getOptions().addOption("Raise " + tenPercent + " (10% of stack)", "poker_raise_" + tenPercent);
            }
            
            int fiftyPercent = (playerStackAvailable * 50) / 100;
            if (fiftyPercent > 0 && playerStackAvailable >= fiftyPercent) {
                main.getOptions().addOption("Raise " + fiftyPercent + " (50% of stack)", "poker_raise_" + fiftyPercent);
            }
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
                    main.getTextPanel().addPara("Opponent calls your raise with " + callAmount + " Stargems.", Color.YELLOW);
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
        
        // Store the time when game was suspended for the joke
        mem.set("$ipc_poker_suspend_time", Global.getSector().getClock().getTimestamp());
        
        main.getTextPanel().addPara("You stand up abruptly. 'Hold that thought! I'll be right back!'", Color.YELLOW);
        main.getTextPanel().addPara("The IPC Dealer raises an eyebrow but nods slowly. 'The cards will stay as they are.'", Color.CYAN);
        main.getTextPanel().addPara("'Don't be long. We have other customers waiting.'", Color.GRAY);
        main.getOptions().clearOptions();
        main.getOptions().addOption("Leave", "leave_now");
    }

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
            
            // Calculate how long player was away for the joke
            long suspendTime = mem.getLong("$ipc_poker_suspend_time");
            long currentTime = Global.getSector().getClock().getTimestamp();
            float daysAway = (currentTime - suspendTime) / 30f; // Approximate days (30 days per month)
            
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
            
            // Inform player that game has been resumed with joke
            main.getTextPanel().addPara("The IPC Dealer looks at you with a mix of irritation and resignation.", Color.CYAN);
            main.getTextPanel().addPara("'Ah, you've returned. We've been standing here waiting for you for " + String.format("%.1f", daysAway) + " days.'", Color.YELLOW);
            main.getTextPanel().addPara("'The cards are still where you left them. Let's continue... grudgingly.'", Color.GRAY);
            
            // Update UI with restored game state
            updateUI();
        } else {
            // If no stored state, start a fresh game
            setupGame();
        }
    }

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

    private String getCardsString(List<PokerGame.PokerGameLogic.Card> cards) {
        StringBuilder sb = new StringBuilder();
        for (PokerGame.PokerGameLogic.Card c : cards) sb.append(c.toString()).append(" ");
        return sb.toString();
    }
    
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

    private void returnStacks() {
        // Return player's remaining stack to wallet
        CasinoVIPManager.addStargems(playerStack);
        
        // Note: opponent's stack doesn't get returned to wallet
        // since it's the AI's own chips
    }
    
    private void endHand(boolean playerWon) {
        if (playerWon) {
            CasinoVIPManager.addStargems(potSize);
            main.getTextPanel().addPara("You win the pot of " + potSize + " Stargems!", Color.CYAN);
        }
        
        // Ensure stack values don't go negative
        if (playerStack < 0) playerStack = 0;
        if (opponentStack < 0) opponentStack = 0;
        
        // Check if either player is busted
        if (playerStack < bigBlind) {
            main.getTextPanel().addPara("You're out of chips! Game over.", Color.RED);
            returnStacks();
        } else if (opponentStack < bigBlind) {
            main.getTextPanel().addPara("Opponent is out of chips! You win!", Color.GREEN);
            returnStacks();
        }
        
        // Play game complete sound
        // Removed sound effect as it was removed from sounds.json
// Global.getSoundPlayer().playUISound("game_complete",1f, 1f);
        
        main.getOptions().clearOptions();
        main.getOptions().addOption("Next Hand", "next_hand");
        main.getOptions().addOption("Leave Table", "back_menu");
    }
}