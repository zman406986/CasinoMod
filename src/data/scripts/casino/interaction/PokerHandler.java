package data.scripts.casino.interaction;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import data.scripts.casino.CasinoConfig;
import data.scripts.casino.CasinoVIPManager;
import data.scripts.casino.PokerGame;
import data.scripts.casino.PokerGame.PokerGameLogic;
import data.scripts.CasinoUIPanels.PokerUIPanel;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

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

    public PokerHandler(CasinoInteraction main) {
        this.main = main;
        this.ai = new PokerGame.SimplePokerAI();
    }

    /**
     * Handles poker game options including ante, actions, and navigation
     */
    public void handle(String option) {
        if ("play".equals(option)) {
            showPokerConfirm();
        } else if ("confirm_poker_ante".equals(option)) {
            setupGame();
        } else if ("next_hand".equals(option)) {
            setupGame();
        } else if ("how_to_poker".equals(option)) {
            main.help.showPokerHelp();
        } else {
            // Handle other poker actions
            handlePokerAction(option);
        }
    }

    /**
     * Shows confirmation dialog for poker ante
     */
    public void showPokerConfirm() {
        main.options.clearOptions();
        main.textPanel.addParagraph("The IPC Dealer prepares to deal. Ante up " + bigBlind + " Stargems?", Color.YELLOW);
        main.options.addOption("Ante Up & Start Hand", "confirm_poker_ante");
        main.options.addOption("How to Play Poker", "how_to_poker");  // Add help option
        main.options.addOption("Wait... (Cancel)", "back_menu");
        main.setState(CasinoInteraction.State.POKER);
    }

    /**
     * Sets up a new poker hand with fresh cards and initial bets
     */
    public void setupGame() {
        deck = new PokerGameLogic.Deck();
        deck.shuffle();
        playerHand = new ArrayList<>();
        opponentHand = new ArrayList<>();
        communityCards = new ArrayList<>();
        
        playerHand.add(deck.draw());
        opponentHand.add(deck.draw());
        playerHand.add(deck.draw());
        opponentHand.add(deck.draw());
        
        int currentGems = CasinoVIPManager.getStargems();
        playerStack = Math.min(currentGems, 100000); 
        opponentStack = playerStack; 
        
        potSize = 0;
        currentBetToCall = 0;
        playerBet = 0;
        opponentBet = 0;
        
        playerIsDealer = !playerIsDealer; 
        
        if (playerIsDealer) {
            CasinoVIPManager.addStargems(-smallBlind); playerBet = smallBlind;
            opponentStack -= bigBlind; opponentBet = bigBlind; 
        } else {
            opponentStack -= smallBlind; opponentBet = smallBlind;
            CasinoVIPManager.addStargems(-bigBlind); playerBet = bigBlind;
        }
        
        potSize = smallBlind + bigBlind;
        currentBetToCall = bigBlind;
        
        // Reset AI aggression tracking for new game
        this.ai = new PokerGame.SimplePokerAI();
        
        // Visual Panel
        main.dialog.getVisualPanel().showCustomPanel(400, 500, new PokerUIPanel(main));
        
        updateUI();
    }

    /**
     * Updates the poker UI with current game state
     */
    public void updateUI() {
        main.getOptions().clearOptions();
        main.getTextPanel().addParagraph("------------------------------------------------");
        main.getTextPanel().addParagraph("Pot: " + potSize + " Stargems", Color.GREEN);
        main.getTextPanel().addParagraph("Your Stack: " + playerStack + " Stargems", Color.CYAN);
        main.getTextPanel().addParagraph("Opponent Stack: " + opponentStack + " Stargems", Color.ORANGE);
        
        main.getTextPanel().addParagraph("Your Hand: " +  getCardsString(playerHand));
        if (!communityCards.isEmpty()) {
            main.getTextPanel().addParagraph("Community: " + getCardsString(communityCards));
        }
        
        int callAmount = opponentBet - playerBet;
        if (callAmount > 0) {
            main.getOptions().addOption("Call (" + callAmount + ")", "poker_call");
            main.getOptions().addOption("Fold", "poker_fold");
        } else {
            main.getOptions().addOption("Check", "poker_check");
        }
        
        main.getOptions().addOption("Raise", "poker_raise_menu");
        main.getOptions().addOption("How to Play Poker", "how_to_poker");  // Add help option during gameplay
        main.getOptions().addOption("Tell Them to Wait (Suspend)", "poker_suspend");
    }

    /**
     * Handles poker-specific actions like calls, checks, folds, and raises
     */
    public void handlePokerAction(String option) {
        if ("poker_call".equals(option)) {
            int callAmount = opponentBet - playerBet;
            CasinoVIPManager.addStargems(-callAmount);
            playerBet += callAmount;
            potSize += callAmount;

            // Track player action (not a raise, not a fold)
            ai.trackPlayerAction(false, false);
            
            // After player calls, betting round is complete - move to next street
            advanceStreet();
        } else if ("poker_check".equals(option)) {
            // Track player check action (not a raise, not a fold)
            ai.trackPlayerAction(false, false);
            
            // After player checks, let AI respond
            PokerGame.SimplePokerAI.AIResponse res = ai.decide(opponentHand, communityCards, playerBet - opponentBet, potSize, opponentStack, playerStack);
            if (res.action == PokerGame.SimplePokerAI.Action.FOLD) {
                // Check if AI is already all-in before allowing fold
                if (opponentStack <= 0) {
                    // Cannot fold when already all-in
                    int callAmount = Math.min(playerBet - opponentBet, opponentStack);
                    if (callAmount > 0) {
                        opponentStack -= callAmount;
                        opponentBet += callAmount;
                        potSize += callAmount;
                        main.getTextPanel().addParagraph("Opponent calls (all-in) with " + callAmount + " Stargems.", Color.YELLOW);
                    }
                } else {
                    main.getTextPanel().addParagraph("Opponent folds! You win.", Color.CYAN);
                    endHand(true);
                }
            } else if (res.action == PokerGame.SimplePokerAI.Action.RAISE) {
                // AI raises after player checks
                int raiseAmount = Math.min(res.raiseAmount, opponentStack);
                if (raiseAmount > 0) {
                    opponentStack -= raiseAmount;
                    opponentBet += raiseAmount;
                    potSize += raiseAmount;
                    main.getTextPanel().addParagraph("Opponent raises by " + raiseAmount + " Stargems.", Color.YELLOW);
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
                    main.getTextPanel().addParagraph("Opponent calls with " + callAmount + " Stargems.", Color.YELLOW);
                }
                advanceStreet();
            } else if (res.action == PokerGame.SimplePokerAI.Action.CHECK) {
                main.getTextPanel().addParagraph("Opponent checks.", Color.YELLOW);
                advanceStreet(); // Both players checked, move to next street
            }
        } else if ("poker_fold".equals(option)) {
            main.getTextPanel().addParagraph("You fold. The IPC Dealer scoops the pot.", Color.GRAY);
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
        // Using configurable raise amounts from CasinoConfig
        for (int r : CasinoConfig.POKER_RAISE_AMOUNTS) main.getOptions().addOption("Raise " + r, "poker_raise_" + r);
        main.getOptions().addOption("Back", "poker_back_action");
    }

    /**
     * Performs a raise action with specified amount
     */
    private void performRaise(int amt) {
        CasinoVIPManager.addStargems(-amt);
        playerBet += amt;
        potSize += amt;
        
        // Track player raise action
        ai.trackPlayerAction(true, false);
        
        // After player raises, let AI respond
        PokerGame.SimplePokerAI.AIResponse res = ai.decide(opponentHand, communityCards, playerBet - opponentBet, potSize, opponentStack, playerStack);
        if (res.action == PokerGame.SimplePokerAI.Action.FOLD) {
            // Check if AI is already all-in before allowing fold
            if (opponentStack <= 0) {
                // Cannot fold when already all-in
                int callAmount = Math.min(playerBet - opponentBet, opponentStack);
                if (callAmount > 0) {
                    opponentStack -= callAmount;
                    opponentBet += callAmount;
                    potSize += callAmount;
                    main.getTextPanel().addParagraph("Opponent calls (all-in) with " + callAmount + " Stargems.", Color.YELLOW);
                }
            } else {
                main.getTextPanel().addParagraph("Opponent folds! You win.", Color.CYAN);
                endHand(true);
            }
        } else if (res.action == PokerGame.SimplePokerAI.Action.RAISE) {
            // AI raises against the player's raise - this continues the betting round
            int raiseAmount = Math.min(res.raiseAmount, opponentStack);
            if (raiseAmount > 0) {
                opponentStack -= raiseAmount;
                opponentBet += raiseAmount;
                potSize += raiseAmount;
                main.getTextPanel().addParagraph("Opponent raises by " + raiseAmount + " Stargems.", Color.YELLOW);
                updateUI(); // Now player must respond again
            } else {
                // AI can't raise, so they call
                int callAmount = Math.min(playerBet - opponentBet, opponentStack);
                if (callAmount > 0) {
                    opponentStack -= callAmount;
                    opponentBet += callAmount;
                    potSize += callAmount;
                    main.getTextPanel().addParagraph("Opponent calls with " + callAmount + " Stargems.", Color.YELLOW);
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
                main.getTextPanel().addParagraph("Opponent calls your raise with " + callAmount + " Stargems.", Color.YELLOW);
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
        
        main.getTextPanel().addParagraph("The Dealer nods. 'The cards will stay as they are. Don't be long.'", Color.YELLOW);
        main.getOptions().clearOptions();
        main.getOptions().addOption("Leave", "leave_now");
    }

    /**
     * Restores a suspended poker game from memory
     */
    public void restoreSuspendedGame() {
        // Since we can't reliably store complex objects like decks and hands in MemoryAPI,
        // we'll start a new game instead to avoid the getObject error
        setupGame();
    }

    /**
     * Advances the game to next street (flop, turn, river)
     */
    private void advanceStreet() {
        // Reset betting round tracking for the new street
        playerBet = 0;
        opponentBet = 0;
        
        if (communityCards.size() == 0) { // Flop
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
        
        main.getTextPanel().addParagraph("Opponent reveals: " + getCardsString(opponentHand));
        main.getTextPanel().addParagraph("Your Best: " + playerResult.rank.name());
        main.getTextPanel().addParagraph("Opponent Best: " + opponentResult.rank.name());
        
        // Show stack sizes before determining winner
        main.getTextPanel().addParagraph("Your Stack: " + playerStack + " Stargems", Color.CYAN);
        main.getTextPanel().addParagraph("Opponent Stack: " + opponentStack + " Stargems", Color.ORANGE);
        
        int cmp = playerResult.compareTo(opponentResult);
        if (cmp > 0) {
            main.getTextPanel().addParagraph("VICTORY! You take the pot.", Color.CYAN);
            endHand(true);
        } else if (cmp < 0) {
            main.getTextPanel().addParagraph("DEFEAT. The IPC Dealer wins.", Color.RED);
            endHand(false);
        } else {
            main.getTextPanel().addParagraph("SPLIT POT. It's a draw.", Color.YELLOW);
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
     * Ends the current hand and shows options for next action
     */
    private void endHand(boolean playerWon) {
        if (playerWon) {
            CasinoVIPManager.addStargems(potSize);
            main.getTextPanel().addParagraph("You win the pot of " + potSize + " Stargems!", Color.CYAN);
        }
        
        // Ensure stack values don't go negative
        if (playerStack < 0) playerStack = 0;
        if (opponentStack < 0) opponentStack = 0;
        
        main.getOptions().clearOptions();
        main.getOptions().addOption("Next Hand", "next_hand");
        main.getOptions().addOption("Leave Table", "back_menu");
    }
}