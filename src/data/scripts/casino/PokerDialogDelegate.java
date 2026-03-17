package data.scripts.casino;

import java.util.Map;

import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import data.scripts.casino.interaction.PokerHandler;

/**
 * Dialog delegate for the Poker UI Panel.
 * <p>
 * ARCHITECTURE NOTES:
 * - Implements CustomVisualDialogDelegate (base game interface)
 * - Wraps PokerPanelUI for integration with the interaction dialog system
 * - Follows the same pattern as DuelDialogDelegate and GachaAnimationDialogDelegate
 * <p>
 * LIFECYCLE:
 * 1. Dialog is shown via dialog.showCustomDialog()
 * 2. init() is called with the panel - we initialize PokerPanelUI here
 * 3. advance() is called every frame - we check for game completion
 * 4. reportDismissed() is called when dialog closes - cleanup and callbacks
 * <p>
 * COMMUNICATION FLOW:
 * PokerHandler -> PokerDialogDelegate -> PokerPanelUI
 *                    ↑                       ↓
 *                 PokerActionCallback ───────┘
 */
public class PokerDialogDelegate implements CustomVisualDialogDelegate {
    
// ============================================================================
    // STATE
    // ============================================================================
    protected DialogCallbacks callbacks;
    protected float endDelay = 1.5f;
    protected boolean finished = false;
    protected boolean gameEnded = false;
    
    // ============================================================================
    // REFERENCES
    // ============================================================================
    protected PokerPanelUI pokerPanel;
    protected InteractionDialogAPI dialog;
    protected Map<String, MemoryAPI> memoryMap;
    protected PokerGame game;
    protected PokerHandler handler;
    
    // ============================================================================
    // CALLBACKS
    // ============================================================================
    protected Runnable onDismissCallback;
    protected PokerPanelUI.PokerActionCallback actionCallback;
    
    // ============================================================================
    // CONSTRUCTION
    // ============================================================================
    
    /**
     * Creates a new PokerDialogDelegate.
     * CRITICAL: The PokerPanelUI must be created HERE, not in init()!
     * The framework calls getCustomPanelPlugin() BEFORE init(), so if we
     * create the panel in init(), getCustomPanelPlugin() would return null.
     * This matches GachaAnimationDialogDelegate which receives the animation
     * as a constructor parameter (already created before delegate).
     * @param game The poker game instance (shared with PokerHandler)
     * @param dialog The interaction dialog
     * @param memoryMap Memory map for event firing
     * @param onDismissCallback Called when dialog is dismissed
     */
    public PokerDialogDelegate(PokerGame game, InteractionDialogAPI dialog,
            Map<String, MemoryAPI> memoryMap, Runnable onDismissCallback) {
        this(game, dialog, memoryMap, onDismissCallback, null);
    }
    
    /**
     * Creates a new PokerDialogDelegate with handler reference for in-place updates.
     * <p>
     * This constructor is preferred for smooth gameplay - actions are processed
     * in-place without dismissing the dialog, avoiding visual flash.
     * <p>
     * @param game The poker game instance (shared with PokerHandler)
     * @param dialog The interaction dialog
     * @param memoryMap Memory map for event firing
     * @param onDismissCallback Called when dialog is dismissed
     * @param handler Reference to PokerHandler for in-place action processing
     */
    public PokerDialogDelegate(PokerGame game, InteractionDialogAPI dialog,
            Map<String, MemoryAPI> memoryMap, Runnable onDismissCallback, PokerHandler handler) {
        this.game = game;
        this.dialog = dialog;
        this.memoryMap = memoryMap;
        this.onDismissCallback = onDismissCallback;
        this.handler = handler;
        
        actionCallback = new PokerPanelUI.PokerActionCallback() {
            @Override
            public void onPlayerAction(PokerGame.Action action, int raiseAmount) {
                handlePlayerAction(action, raiseAmount);
            }
            
            @Override
            public void onBackToMenu() {
                if (onDismissCallback != null) {
                    onDismissCallback.run();
                }
            }
            
            @Override
            public void onNextHand() {
                handleNextHand();
            }
            
            @Override
            public void onSuspend() {
                handleSuspend();
            }
            
            @Override
            public void onHowToPlay() {
                handleHowToPlay();
            }
            
            @Override
            public void onFlipTable() {
                handleFlipTable();
            }
        };
        
        pokerPanel = new PokerPanelUI(game, actionCallback);
    }
    
    // ============================================================================
    // CustomVisualDialogDelegate IMPLEMENTATION
    // ============================================================================
    
    /**
     * Returns the panel plugin for the dialog system.
     * CRITICAL: This must return our PokerPanelUI instance.
     */
    public CustomUIPanelPlugin getCustomPanelPlugin() {
        return pokerPanel;
    }
    
    /**
     * Called by the dialog system when the panel is created.
     * INITIALIZATION SEQUENCE:
     * 1. Store callbacks reference
     * 2. Set fade duration for smooth exit
     * 3. Initialize the pokerPanel (created in constructor)
     * @param panel The custom panel created by the dialog system
     * @param callbacks Callbacks for controlling the dialog
     */
    public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
        this.callbacks = callbacks;
        
        // Set fade duration for exit (matching GachaAnimationDialogDelegate)
        callbacks.getPanelFader().setDurationOut(0.5f);
        
        // Initialize the poker panel UI (already created in constructor)
        if (pokerPanel != null) {
            pokerPanel.init(panel, callbacks);
            
            // Initialize game state for animation tracking (critical for first hand)
            pokerPanel.updateGameState(game);
            
            if (lastOpponentAction != null && !lastOpponentAction.isEmpty()) {
                pokerPanel.showOpponentAction(lastOpponentAction);
            }
            
            if (lastPlayerAction != null && !lastPlayerAction.isEmpty()) {
                pokerPanel.showPlayerAction(lastPlayerAction);
            }
            
            if (returnMessage != null && !returnMessage.isEmpty()) {
                pokerPanel.showReturnMessage(returnMessage);
            }
        }
    }
    
    /**
     * Controls background noise/overlay alpha.
     * Return 0 for no noise, higher for more visible noise overlay.
     */
    public float getNoiseAlpha() {
        // Slight noise for better visual integration (matching GachaAnimationDialogDelegate)
        return 0.2f;
    }
    
    /**
     * Called every frame by the dialog system.
     * <p>
     * RESPONSIBILITIES:
     * 1. Track game state for display purposes
     * 2. Do NOT auto-close even when bust - let player see result and click Leave
     * <p>
     * @param amount Frame time in seconds
     */
    public void advance(float amount) {
        if (finished) {
            return;
        }
        
        // Do NOT auto-close when bust - player should see the result and click Leave manually
        // The updateResultLabel in PokerPanelUI shows appropriate bust message
        PokerGame.PokerState state = game.getState();
        
        // Mark game as ended when someone is bust, but don't auto-close
        boolean someoneIsBust = state.playerStack < state.bigBlind || state.opponentStack < state.bigBlind;
        
        if (someoneIsBust && !gameEnded) {
            gameEnded = true;
        }
    }
    
    /**
     * Called when the dialog is dismissed (either by user or programmatically).
     * CLEANUP RESPONSIBILITIES:
     * 1. Fire completion event for rules system
     * 2. Call dismiss callback for PokerHandler cleanup
     */
    public void reportDismissed(int option) {
        // Fire event for rules system (matching GachaAnimationDialogDelegate pattern)
        if (memoryMap != null) {
            FireBest.fire(null, dialog, memoryMap, "PokerGameCompleted");
        }
        
        // Call the dismiss callback
        if (onDismissCallback != null) {
            onDismissCallback.run();
        }
    }
    
    // ============================================================================
    // GAME STATE MANAGEMENT
    // ============================================================================
    
    /**
     * Updates the game reference when a new hand starts.
     */
    public void updateGame(PokerGame newGame) {
        this.game = newGame;
        this.lastOpponentAction = "";
        this.lastPlayerAction = "";
        if (pokerPanel != null) {
            pokerPanel.updateGameState(newGame);
            pokerPanel.hideOpponentAction();
            pokerPanel.hidePlayerAction();
        }
        gameEnded = false;
        finished = false;
        endDelay = 1.5f;
    }
    
    /**
     * Refreshes the panel UI after game state changes without dismissing the dialog.
     * This is the key method for smooth in-place updates.
     */
    public void refreshAfterStateChange(PokerGame updatedGame) {
        this.game = updatedGame;
        if (pokerPanel != null) {
            pokerPanel.updateGameState(updatedGame);
        }
    }
    
    /**
     * Signals that opponent is thinking (triggers visual indicator).
     */
    public void startOpponentTurn() {
        if (pokerPanel != null) {
            pokerPanel.startOpponentTurn();
        }
    }
    
    /**
     * Handles player action from the panel UI.
     * This bridges the UI action to the game logic.
     * IN-PLACE PROCESSING: If handler is available, processes action directly
     * without dismissing the dialog, avoiding visual flash.
     */
    protected void handlePlayerAction(PokerGame.Action action, int raiseAmount) {
        if (handler != null) {
            handler.processPlayerActionInPlace(action, raiseAmount, this);
            return;
        }
        
        pendingAction = action;
        pendingRaiseAmount = raiseAmount;
        
        if (callbacks != null) {
            callbacks.dismissDialog();
        }
    }
    
    // Pending action storage for communication back to PokerHandler
    protected PokerGame.Action pendingAction = null;
    protected int pendingRaiseAmount = 0;
    protected boolean pendingNextHand = false;
    protected boolean pendingSuspend = false;
    protected boolean pendingHowToPlay = false;
    protected boolean pendingFlipTable = false;
    protected boolean pendingCleanLeave = false;
    protected String lastOpponentAction = "";
    protected String lastPlayerAction = "";
    protected String returnMessage = "";
    
    /**
     * Sets the opponent action text to display in the panel.
     * Called by PokerHandler after opponent takes an action.
     */
    public void setLastOpponentAction(String action) {
        this.lastOpponentAction = action;
        
        if (pokerPanel != null && action != null && !action.isEmpty()) {
            pokerPanel.showOpponentAction(action);
        }
    }
    
    /**
     * Sets the player action text to display in the panel.
     * Called by PokerHandler before reopening panel after player action.
     */
    public void setLastPlayerAction(String action) {
        this.lastPlayerAction = action;
    }

    /**
     * Gets the pending action that was triggered by the player.
     * Called by PokerHandler after dialog dismissal.
     * 
     * @return The pending action, or null if no action was taken
     */
    public PokerGame.Action getPendingAction() {
        return pendingAction;
    }
    
    /**
     * Gets the pending raise amount.
     * Only valid if getPendingAction() returns RAISE.
     */
    public int getPendingRaiseAmount() {
        return pendingRaiseAmount;
    }
    
    /**
     * Gets whether the player requested a next hand.
     */
    public boolean getPendingNextHand() {
        return pendingNextHand;
    }
    
    /**
     * Handles the "Next Hand" button click.
     * IN-PLACE PROCESSING: If handler is available, processes directly without dismissing.
     */
    protected void handleNextHand() {
        if (handler != null) {
            handler.startNextHandInPlace(this);
            return;
        }
        
        pendingNextHand = true;
        pendingAction = null;
        
        if (callbacks != null) {
            callbacks.dismissDialog();
        }
    }
    
    /**
     * Handles the "Suspend" button click.
     */
    protected void handleSuspend() {
        pendingSuspend = true;
        pendingAction = null;
        
        if (callbacks != null) {
            callbacks.dismissDialog();
        }
    }
    
    /**
     * Handles the "How to Play" button click.
     */
    protected void handleHowToPlay() {
        pendingHowToPlay = true;
        pendingAction = null;
        
        if (callbacks != null) {
            callbacks.dismissDialog();
        }
    }
    
    /**
     * Handles the "Flip Table" button click.
     * At showdown, this becomes a clean "Leave" with no cooldown.
     * IN-PLACE PROCESSING: If handler is available and at showdown, processes directly.
     */
    protected void handleFlipTable() {
        boolean isShowdown = game != null && game.getState() != null && 
            game.getState().round == PokerGame.Round.SHOWDOWN;
        
        if (handler != null && isShowdown) {
            handler.handleCleanLeaveInPlace(this);
            return;
        }
        
        if (isShowdown) {
            pendingCleanLeave = true;
        } else {
            pendingFlipTable = true;
        }
        pendingAction = null;
        
        if (callbacks != null) {
            callbacks.dismissDialog();
        }
    }
    
    /**
     * Gets whether the player requested suspend.
     */
    public boolean getPendingSuspend() {
        return pendingSuspend;
    }
    
    /**
     * Gets whether the player requested how to play.
     */
    public boolean getPendingHowToPlay() {
        return pendingHowToPlay;
    }
    
    /**
     * Gets whether the player requested flip table.
     */
    public boolean getPendingFlipTable() {
        return pendingFlipTable;
    }
    
    /**
     * Gets whether the player requested a clean leave (at showdown, no cooldown).
     */
    public boolean getPendingCleanLeave() {
        return pendingCleanLeave;
    }
    
    /**
     * Forces the dialog to close immediately.
     * Used when player leaves the table.
     */
    public void closeDialog() {
        if (callbacks != null) {
            callbacks.dismissDialog();
        }
    }
    
    /**
     * Checks if the dialog is still active.
     */
    public boolean isFinished() {
        return finished;
    }
    
    // ============================================================================
    // UTILITY
    // ============================================================================
    
    }
