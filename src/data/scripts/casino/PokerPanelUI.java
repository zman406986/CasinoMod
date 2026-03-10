package data.scripts.casino;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate.DialogCallbacks;
import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.campaign.CustomDialogDelegate.CustomDialogCallback;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Poker UI Panel - renders the poker table, cards, and handles player input.
 * 
 * ARCHITECTURE NOTES:
 * - Extends BaseCustomUIPanelPlugin (base game pattern, see DuelPanel, GachaAnimation)
 * - Uses CustomVisualDialogDelegate wrapper for integration with interaction dialogs
 * - Renders in renderBelow() for content, render() for overlays (following DuelPanel pattern)
 * - Uses GL11.glScissor() to clip all rendering to panel boundaries
 * - Button creation follows the panel->tooltip->button hierarchy pattern
 * 
 * KEY RENDERING ORDER:
 * 1. renderBelow(): Background -> Table -> Community Cards -> Player Cards -> UI Elements
 * 2. render(): Overlays, effects, animations (if any)
 * 
 * STATE MANAGEMENT:
 * - PokerHandler owns the game state (PokerGame instance)
 * - This panel is purely for rendering and input
 * - All game logic flows through PokerHandler -> PokerGame
 */
public class PokerPanelUI extends BaseCustomUIPanelPlugin {

    // ============================================================================
    // CORE REFERENCES
    // ============================================================================
    protected InteractionDialogAPI dialog;
    protected DialogCallbacks callbacks;
    protected CustomPanelAPI panel;
    protected PositionAPI p;
    
    // External callback when player makes an action
    protected PokerActionCallback actionCallback;
    
    // ============================================================================
    // UI STATE
    // ============================================================================
    protected PokerGame game;
    protected int selectedRaiseAmount = 0;
    protected boolean waitingForOpponent = false;
    protected float opponentThinkTimer = 0f;
    protected static final float OPPONENT_THINK_DELAY = 0.8f; // seconds before opponent acts
    
    // Track if buttons have been created to avoid duplicates
    protected boolean buttonsCreated = false;
    
    // ============================================================================
    // CARD FLIP ANIMATION
    // ============================================================================
    public static class CardFlipAnimation {
        public enum Phase { HIDDEN, FLIPPING, REVEALED }
        
        public Phase phase = Phase.HIDDEN;
        public float progress = 0f;
        public float delay = 0f;
        public boolean triggered = false;
        
        public static final float FLIP_DURATION = 0.4f;
        public static final float STAGGER_DELAY = 0.08f;
        
        public boolean isAnimating() { return phase == Phase.FLIPPING; }
        public boolean isRevealed() { return phase == Phase.REVEALED; }
        public boolean shouldShowBack() { 
            return phase == Phase.HIDDEN || (phase == Phase.FLIPPING && progress < 0.5f); 
        }
        
        public float getWidthScale() {
            if (phase != Phase.FLIPPING) return 1f;
            return (float) Math.abs(Math.cos(progress * Math.PI));
        }
        
        public void advance(float amount) {
            // Cap delta to prevent instant animation completion on lag/pause
            // Max 100ms per frame ensures smooth animation even with large time deltas
            float maxDelta = 0.1f;
            amount = Math.min(amount, maxDelta);

            // Only progress if animation has been explicitly triggered
            if (phase == Phase.HIDDEN && triggered) {
                if (delay > 0) {
                    delay -= amount;
                    if (delay <= 0) {
                        delay = 0;
                        phase = Phase.FLIPPING;
                        Global.getLogger(PokerPanelUI.class).info("Animation starting FLIPPING phase");
                    }
                } else {
                    phase = Phase.FLIPPING;
                    Global.getLogger(PokerPanelUI.class).info("Animation starting FLIPPING phase (no delay)");
                }
            }
            if (phase == Phase.FLIPPING) {
                float oldProgress = progress;
                progress += amount / FLIP_DURATION;
                if (progress >= 1f) {
                    progress = 1f;
                    phase = Phase.REVEALED;
                    Global.getLogger(PokerPanelUI.class).info("Animation completed REVEALED phase");
                } else if (progress > oldProgress + 0.1f) {
                    // Log significant progress updates (every 10%)
                    Global.getLogger(PokerPanelUI.class).info("Animation progress: " + String.format("%.2f", progress));
                }
            }
        }
        
        public void triggerFlip(float staggerDelay) {
            this.delay = staggerDelay;
            this.progress = 0f;
            this.phase = Phase.HIDDEN;
            this.triggered = true;
            Global.getLogger(PokerPanelUI.class).info("triggerFlip called: delay=" + staggerDelay + " phase=" + phase);
        }
        
        public void reset() {
            phase = Phase.HIDDEN;
            progress = 0f;
            delay = 0f;
            triggered = false;
        }
    }
    
    protected CardFlipAnimation[] playerCardAnimations = new CardFlipAnimation[2];
    protected CardFlipAnimation[] opponentCardAnimations = new CardFlipAnimation[2];
    protected CardFlipAnimation[] communityCardAnimations = new CardFlipAnimation[5];
    
    protected PokerGame.Round lastAnimatedRound = null;
    protected int lastAnimatedCommunityCount = 0;
    protected boolean playerCardsAnimated = false;
    protected boolean opponentCardsAnimated = false;
    
    public void resetCardAnimations() {
        for (int i = 0; i < 2; i++) {
            playerCardAnimations[i].reset();
            opponentCardAnimations[i].reset();
        }
        for (int i = 0; i < 5; i++) {
            communityCardAnimations[i].reset();
        }
        lastAnimatedRound = null;
        lastAnimatedCommunityCount = 0;
        playerCardsAnimated = false;
        opponentCardsAnimated = false;
    }
    
    protected void checkAndTriggerAnimations(PokerGame.PokerState state, PokerGame.Round previousRound, int previousCommunityCount) {
        Global.getLogger(PokerPanelUI.class).info("checkAndTriggerAnimations: prevRound=" + previousRound + 
            " currRound=" + state.round + " prevCommunity=" + previousCommunityCount + 
            " currCommunity=" + (state.communityCards != null ? state.communityCards.size() : 0));
        
        // Player cards - trigger when hand is dealt and not yet animated
        // This uses a simple boolean flag that resets only on new hands
        if (!playerCardsAnimated && state.playerHand != null && !state.playerHand.isEmpty()) {
            playerCardsAnimated = true;
            for (int i = 0; i < state.playerHand.size() && i < 2; i++) {
                playerCardAnimations[i].triggerFlip(i * CardFlipAnimation.STAGGER_DELAY);
                Global.getLogger(PokerPanelUI.class).info("Triggered player card " + i + " animation");
            }
        }

        // Community cards - trigger for any NEW cards (detected by comparing with previous count)
        // Use previousCommunityCount (captured BEFORE update) instead of lastAnimatedCommunityCount
        int currentCommunityCount = state.communityCards != null ? state.communityCards.size() : 0;
        if (currentCommunityCount > previousCommunityCount) {
            for (int i = previousCommunityCount; i < currentCommunityCount && i < 5; i++) {
                // Only trigger if animation hasn't started yet
                if (communityCardAnimations[i].phase == CardFlipAnimation.Phase.HIDDEN) {
                    // Stagger delay based on how many new cards (0, 1, or 2 previous cards)
                    float staggerDelay = (i - previousCommunityCount) * CardFlipAnimation.STAGGER_DELAY;
                    communityCardAnimations[i].triggerFlip(staggerDelay);
                    Global.getLogger(PokerPanelUI.class).info("Triggered community card " + i + " animation, phase=" + communityCardAnimations[i].phase);
                }
            }
            // NOTE: lastAnimatedCommunityCount is now updated in updateGameState AFTER this call
        }

        // Opponent cards - trigger at showdown for any hidden cards
        // Check transition INTO showdown (previousRound was not SHOWDOWN, now it is)
        // This ensures animation triggers exactly once when entering showdown
        // Also handles initial state (previousRound is null) - if we're already at showdown, animate
        boolean enteringShowdown = state.round == PokerGame.Round.SHOWDOWN && 
                                   (previousRound == null || previousRound != PokerGame.Round.SHOWDOWN);
        Global.getLogger(PokerPanelUI.class).info("enteringShowdown=" + enteringShowdown + " folder=" + state.folder);
        if (enteringShowdown && state.folder == null) {
            boolean anyTriggered = false;
            for (int i = 0; i < 2; i++) {
                if (opponentCardAnimations[i].phase == CardFlipAnimation.Phase.HIDDEN) {
                    opponentCardAnimations[i].triggerFlip(i * CardFlipAnimation.STAGGER_DELAY);
                    anyTriggered = true;
                    Global.getLogger(PokerPanelUI.class).info("Triggered opponent card " + i + " animation");
                }
            }
            if (anyTriggered) {
                opponentCardsAnimated = true;
            }
        }
    }
    
    // ============================================================================
    // CACHED STRING VALUES - Avoid string formatting every frame
    // ============================================================================
    protected int lastPlayerStack = -1;
    protected int lastOpponentStack = -1;
    protected int lastPlayerBet = -1;
    protected int lastOpponentBet = -1;
    protected int lastPot = -1;
    protected int lastBigBlind = -1;
    protected PokerGame.Round lastRound = null;
    protected String lastPlayerStackText = "";
    protected String lastOpponentStackText = "";
    protected String lastRoundText = "";
    
    // ============================================================================
    // CACHED CARD STATES - Avoid recalculating card positions every frame
    // ============================================================================
    protected String[] lastPlayerCardRanks = new String[2];
    protected String[] lastOpponentCardRanks = new String[2];
    protected String[] lastCommunityCardRanks = new String[5];
    protected int lastPlayerHandSize = -1;
    protected int lastOpponentHandSize = -1;
    protected int lastCommunityCardsSize = -1;
    protected PokerGame.Round lastCardUpdateRound = null;
    
    // ============================================================================
    // CACHED RESULT EVALUATION - Avoid re-evaluating hands at showdown
    // ============================================================================
    protected PokerGame.PokerGameLogic.HandScore cachedPlayerScore = null;
    protected PokerGame.PokerGameLogic.HandScore cachedOpponentScore = null;
    protected String cachedResultText = null;
    protected Color cachedResultColor = null;
    protected boolean resultCached = false;
    
    // ============================================================================
    // UI ELEMENTS - Buttons are recreated each update, references stored for state checks
    // ============================================================================
    protected ButtonAPI foldButton;
    protected ButtonAPI checkCallButton;
    protected ButtonAPI raiseButton;
    protected ButtonAPI allInButton;
    protected List<ButtonAPI> raiseOptionButtons = new ArrayList<>();
    protected List<CustomPanelAPI> raiseOptionPanels = new ArrayList<>();
    protected ButtonAPI backButton;
    protected ButtonAPI suspendButton;
    protected ButtonAPI howToPlayButton;
    protected ButtonAPI flipTableButton;
    protected CustomPanelAPI flipTablePanel;
    protected CustomPanelAPI suspendPanel;
    protected CustomPanelAPI howToPlayPanel;
    protected CustomPanelAPI foldPanel;
    protected CustomPanelAPI checkCallPanel;
    
    // Text labels for game info (pot, stacks, etc.) - created via TooltipMakerAPI
    // These are actual UI components that render readable text, unlike the broken renderText()
    protected LabelAPI potLabel;
    protected CustomPanelAPI potDisplayPanel;
    
    // Stack display labels
    protected LabelAPI playerStackLabel;
    protected LabelAPI opponentStackLabel;
    protected CustomPanelAPI playerStackPanel;
    protected CustomPanelAPI opponentStackPanel;
    
    // Round indicator label
    protected LabelAPI roundLabel;
    protected CustomPanelAPI roundPanel;
    
    // Waiting indicator label
    protected LabelAPI waitingLabel;
    protected CustomPanelAPI waitingPanel;
    
    // Opponent action display label
    protected LabelAPI opponentActionLabel;
    protected CustomPanelAPI opponentActionPanel;
    protected String lastOpponentAction = "";
    
    // Player action display label
    protected LabelAPI playerActionLabel;
    protected CustomPanelAPI playerActionPanel;
    protected String lastPlayerAction = "";
    
    // AI personality label
    protected LabelAPI aiPersonalityLabel;
    protected CustomPanelAPI aiPersonalityPanel;
    
    // Return from suspend message
    protected LabelAPI returnMessageLabel;
    protected CustomPanelAPI returnMessagePanel;
    
    // Card rank labels (for displaying actual text on cards)
    protected LabelAPI[] playerCardRankLabels = new LabelAPI[2];
    protected LabelAPI[] opponentCardRankLabels = new LabelAPI[2];
    protected LabelAPI[] communityCardRankLabels = new LabelAPI[5];
    protected CustomPanelAPI[] playerCardPanels = new CustomPanelAPI[2];
    protected CustomPanelAPI[] opponentCardPanels = new CustomPanelAPI[2];
    protected CustomPanelAPI[] communityCardPanels = new CustomPanelAPI[5];
    
    // Result labels for showdown
    protected LabelAPI resultLabel;
    protected CustomPanelAPI resultPanel;
    
    // "Next Hand" button for after showdown
    protected ButtonAPI nextHandButton;
    protected CustomPanelAPI nextHandPanel;
    
    // ============================================================================
    // LAYOUT CONSTANTS - Relative positioning for resolution independence
    // ============================================================================
    // Panel dimensions
    protected static final float PANEL_WIDTH = 1000f;
    protected static final float PANEL_HEIGHT = 700f;
    
    // Card dimensions (aspect ratio ~0.7 for standard playing cards)
    protected static final float CARD_WIDTH = 70f;
    protected static final float CARD_HEIGHT = 98f;
    protected static final float CARD_SPACING = 12f;
    
    // Button dimensions
    protected static final float BUTTON_WIDTH = 120f;
    protected static final float BUTTON_HEIGHT = 35f;
    protected static final float BUTTON_SPACING = 12f;
    
    // Raise option buttons (preset amounts) - wider for new label format
    protected static final float RAISE_BUTTON_WIDTH = 180f;
    
    // Margins and padding
    protected static final float MARGIN = 20f;
    
    // ============================================================================
    // COLORS
    // ============================================================================
    protected static final Color COLOR_TABLE = new Color(20, 80, 40);       // Dark green felt
    protected static final Color COLOR_TABLE_BORDER = new Color(139, 90, 43); // Wood border
    protected static final Color COLOR_POT = new Color(255, 215, 0);        // Gold
    protected static final Color COLOR_PLAYER = new Color(100, 200, 255);   // Cyan-blue
    protected static final Color COLOR_OPPONENT = new Color(255, 100, 100); // Red
    protected static final Color COLOR_CARD_BACK = new Color(30, 60, 120);  // Dark blue
    protected static final Color COLOR_CARD_FRONT = new Color(250, 250, 245); // Off-white
    protected static final Color COLOR_SPADES = new Color(30, 30, 30);
    protected static final Color COLOR_HEARTS = new Color(200, 30, 30);
    protected static final Color COLOR_DIAMONDS = new Color(200, 30, 30);
    protected static final Color COLOR_CLUBS = new Color(30, 30, 30);
    
    // Pre-computed float values for GL11 color calls (avoids division every frame)
    protected static final float[] GL_COLOR_SPADES = {30/255f, 30/255f, 30/255f};
    protected static final float[] GL_COLOR_HEARTS = {200/255f, 30/255f, 30/255f};
    protected static final float[] GL_COLOR_DIAMONDS = {200/255f, 30/255f, 30/255f};
    protected static final float[] GL_COLOR_CLUBS = {30/255f, 30/255f, 30/255f};
    protected static final float[] GL_COLOR_CARD_BACK = {30/255f, 60/255f, 120/255f};
    
    protected static final Color COLOR_BG_DARK = new Color(15, 15, 20);
    protected static final Color COLOR_ROUND_PREFLOP = new Color(150, 150, 200);
    protected static final Color COLOR_ROUND_FLOP = new Color(100, 200, 100);
    protected static final Color COLOR_ROUND_TURN = new Color(200, 200, 100);
    protected static final Color COLOR_ROUND_RIVER = new Color(200, 150, 100);
    protected static final Color COLOR_ROUND_SHOWDOWN = new Color(255, 200, 50);
    protected static final Color COLOR_WAITING = new Color(255, 200, 100);
    protected static final Color COLOR_CARD_SHADOW = new Color(0, 0, 0, 150);
    protected static final Color COLOR_RANK_ACE = new Color(255, 215, 0);
    protected static final Color COLOR_RANK_KING = new Color(200, 50, 50);
    protected static final Color COLOR_RANK_QUEEN = new Color(150, 50, 200);
    protected static final Color COLOR_RANK_JACK = new Color(50, 150, 200);
    protected static final Color COLOR_RANK_TEN = new Color(50, 200, 100);
    protected static final Color COLOR_RANK_LOW = new Color(100, 100, 100);
    
    // ============================================================================
    // CALLBACK INTERFACE
    // ============================================================================
    public interface PokerActionCallback {
        void onPlayerAction(PokerGame.Action action, int raiseAmount);
        void onBackToMenu();
        void onNextHand();
        void onSuspend();
        void onHowToPlay();
        void onFlipTable();
    }
    
    // ============================================================================
    // INITIALIZATION
    // ============================================================================
    
public PokerPanelUI(PokerGame game, PokerActionCallback callback) {
        this.game = game;
        this.actionCallback = callback;
        
        for (int i = 0; i < 2; i++) {
            playerCardAnimations[i] = new CardFlipAnimation();
            opponentCardAnimations[i] = new CardFlipAnimation();
        }
        for (int i = 0; i < 5; i++) {
            communityCardAnimations[i] = new CardFlipAnimation();
        }
    }
    
    /**
     * Called by PokerDialogDelegate.init() - this is where we create UI elements.
     * IMPORTANT: Unlike other implementations, we create buttons here, not in positionChanged(),
     * because positionChanged() may not be reliably called by the framework.
     * 
     * INIT SEQUENCE:
     * 1. DialogDelegate.init() -> PokerPanelUI.init()
     * 2. We store panel reference and create buttons immediately
     * 3. positionChanged() may be called later (optional)
     */
    public void init(CustomPanelAPI panel, DialogCallbacks callbacks, InteractionDialogAPI dialog) {
        this.panel = panel;
        this.callbacks = callbacks;
        this.dialog = dialog;
        
        // Reset state for new panel
        waitingForOpponent = false;
        opponentThinkTimer = 0f;
        
        // Set up fade timing for smooth transitions
        callbacks.getPanelFader().setDurationOut(0.5f);
        
        // CRITICAL: Create buttons NOW, not in positionChanged()
        // The framework may not call positionChanged reliably
        // Use default dimensions that will be adjusted when position is known
        createButtonsInInit();
    }
    
    /**
     * Called when panel position/dimensions change.
     * NOTE: Since we create buttons in init() now, this method just updates
     * the position reference for rendering purposes. We don't recreate buttons
     * here to avoid duplicates.
     */
    public void positionChanged(PositionAPI position) {
        this.p = position;
    }
    
    // ============================================================================
    // BUTTON CREATION
    // ============================================================================
    
    /**
     * Creates buttons during init(), before positionChanged() is called.
     * This follows the GachaAnimation pattern - create UI elements in init().
     * 
     * BUTTON LAYOUT (player side - BOTTOM of panel only):
     * - Leave button: top-right corner (always visible)
     * - Action buttons (Fold/Check/Raise): bottom row
     * - Raise options: row above action buttons
     * 
     * NOTE: Buttons are ONLY at the bottom where the player sits.
     * The top of the panel is for the AI opponent's cards/info.
     */
    protected void createButtonsInInit() {
        if (panel == null) {
            return;
        }
        
        if (buttonsCreated) {
            return;
        }
        
        float topRightX = PANEL_WIDTH - BUTTON_WIDTH - MARGIN;
        float topRightY = MARGIN;
        
        flipTablePanel = panel.createCustomPanel(BUTTON_WIDTH, BUTTON_HEIGHT, null);
        TooltipMakerAPI flipTooltip = flipTablePanel.createUIElement(BUTTON_WIDTH, BUTTON_HEIGHT, false);
        flipTableButton = flipTooltip.addButton("Run Away", "poker_flip_table", BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        flipTableButton.getPosition().inTL(0, 0);
        flipTablePanel.addUIElement(flipTooltip).inTL(0, 0);
        panel.addComponent(flipTablePanel).inTL(-1000, -1000);
        
        float suspendX = topRightX - BUTTON_WIDTH - BUTTON_SPACING;
        suspendPanel = panel.createCustomPanel(BUTTON_WIDTH, BUTTON_HEIGHT, null);
        TooltipMakerAPI suspendTooltip = suspendPanel.createUIElement(BUTTON_WIDTH, BUTTON_HEIGHT, false);
        suspendButton = suspendTooltip.addButton("Wait...", "poker_suspend", BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        suspendButton.getPosition().inTL(0, 0);
        suspendPanel.addUIElement(suspendTooltip).inTL(0, 0);
        panel.addComponent(suspendPanel).inTL(-1000, -1000);
        
        float helpX = suspendX - BUTTON_WIDTH - BUTTON_SPACING;
        howToPlayPanel = panel.createCustomPanel(BUTTON_WIDTH, BUTTON_HEIGHT, null);
        TooltipMakerAPI helpTooltip = howToPlayPanel.createUIElement(BUTTON_WIDTH, BUTTON_HEIGHT, false);
        howToPlayButton = helpTooltip.addButton("How to Play", "poker_how_to_play", BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        howToPlayButton.getPosition().inTL(0, 0);
        howToPlayPanel.addUIElement(helpTooltip).inTL(0, 0);
        panel.addComponent(howToPlayPanel).inTL(-1000, -1000);
        
        createPotDisplay(null);
        createStackDisplays();
        createRoundLabel();
        createWaitingLabel();
        createOpponentActionLabel();
        createPlayerActionLabel();
        createAIPersonalityLabel();
        createReturnMessageLabel();
        createCardRankLabels();
        createResultLabel();
        createNextHandButton();
        
        float bottomY = PANEL_HEIGHT - BUTTON_HEIGHT - MARGIN;
        
        foldPanel = panel.createCustomPanel(BUTTON_WIDTH, BUTTON_HEIGHT, null);
        TooltipMakerAPI foldTooltip = foldPanel.createUIElement(BUTTON_WIDTH, BUTTON_HEIGHT, false);
        foldButton = foldTooltip.addButton("Fold", "poker_fold", BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        foldButton.getPosition().inTL(0, 0);
        foldPanel.addUIElement(foldTooltip).inTL(0, 0);
        panel.addComponent(foldPanel).inTL(-1000, -1000);
        
        checkCallPanel = panel.createCustomPanel(BUTTON_WIDTH, BUTTON_HEIGHT, null);
        TooltipMakerAPI checkCallTooltip = checkCallPanel.createUIElement(BUTTON_WIDTH, BUTTON_HEIGHT, false);
        checkCallButton = checkCallTooltip.addButton("Check", "poker_check_call", BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        checkCallButton.getPosition().inTL(0, 0);
        checkCallPanel.addUIElement(checkCallTooltip).inTL(0, 0);
        panel.addComponent(checkCallPanel).inTL(-1000, -1000);
        
        for (int i = 0; i < 5; i++) {
            int amt = i * 100;
            String btnId = "poker_raise_" + amt;
            
            CustomPanelAPI raiseOptPanel = panel.createCustomPanel(RAISE_BUTTON_WIDTH, BUTTON_HEIGHT, null);
            TooltipMakerAPI raiseOptTooltip = raiseOptPanel.createUIElement(RAISE_BUTTON_WIDTH, BUTTON_HEIGHT, false);
            ButtonAPI btn = raiseOptTooltip.addButton("Raise " + amt, btnId, RAISE_BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
            btn.setCustomData(btnId);
            btn.getPosition().inTL(0, 0);
            raiseOptPanel.addUIElement(raiseOptTooltip).inTL(0, 0);
            panel.addComponent(raiseOptPanel).inTL(-1000, -1000);
            raiseOptionPanels.add(raiseOptPanel);
            raiseOptionButtons.add(btn);
        }
        
        buttonsCreated = true;
    }
    
    protected void updateButtonVisibility() {
        if (panel == null || game == null) return;
        
        PokerGame.PokerState state = game.getState();
        if (state == null) return;
        
        float topRightX = PANEL_WIDTH - BUTTON_WIDTH - MARGIN;
        float topRightY = MARGIN;
        
        boolean isShowdown = state.round == PokerGame.Round.SHOWDOWN;
        String flipTableLabel = isShowdown ? "Leave" : "Run Away";
        flipTableButton.setText(flipTableLabel);
        flipTablePanel.getPosition().inTL(topRightX, topRightY);
        
        float suspendX = topRightX - BUTTON_WIDTH - BUTTON_SPACING;
        suspendPanel.getPosition().inTL(suspendX, topRightY);
        
        float helpX = suspendX - BUTTON_WIDTH - BUTTON_SPACING;
        howToPlayPanel.getPosition().inTL(helpX, topRightY);
        
        int callAmount = state.opponentBet - state.playerBet;
        boolean opponentEffectivelyAllIn = state.opponentStack <= state.bigBlind || state.opponentDeclaredAllIn;
        boolean canRaise = state.playerStack > 0 && state.opponentStack > 0 && callAmount < state.playerStack && !opponentEffectivelyAllIn;
        boolean isPlayerTurn = state.currentPlayer == PokerGame.CurrentPlayer.PLAYER && 
                               state.round != PokerGame.Round.SHOWDOWN;
        
        float bottomY = PANEL_HEIGHT - BUTTON_HEIGHT - MARGIN;
        float totalActionWidth = BUTTON_WIDTH * 2 + BUTTON_SPACING;
        float actionStartX = (PANEL_WIDTH - totalActionWidth) / 2f;
        
        if (isPlayerTurn) {
            foldPanel.getPosition().inTL(actionStartX, bottomY);
            
            String checkCallLabel = callAmount > 0 ? "Call " + callAmount : "Check";
            checkCallButton.setText(checkCallLabel);
            float checkCallX = actionStartX + BUTTON_WIDTH + BUTTON_SPACING;
            checkCallPanel.getPosition().inTL(checkCallX, bottomY);
        } else {
            foldPanel.getPosition().inTL(-1000, -1000);
            checkCallPanel.getPosition().inTL(-1000, -1000);
        }
        
        if (canRaise && isPlayerTurn) {
            float[] raiseAmounts = getRaiseOptions(state);
            float raiseOptionsY = bottomY - BUTTON_HEIGHT - BUTTON_SPACING;
            float totalRaiseWidth = RAISE_BUTTON_WIDTH * raiseAmounts.length + BUTTON_SPACING * (raiseAmounts.length - 1);
            float raiseStartX = (PANEL_WIDTH - totalRaiseWidth) / 2f;
            
            for (int i = 0; i < raiseOptionPanels.size(); i++) {
                if (i < raiseAmounts.length) {
                    int amt = (int) raiseAmounts[i];
                    String label = formatRaiseLabel(amt, state.bigBlind, state.pot, state.playerStack, state.opponentBet, state.playerBet);
                    String btnId = "poker_raise_" + amt;
                    float btnX = raiseStartX + (RAISE_BUTTON_WIDTH + BUTTON_SPACING) * i;
                    
                    raiseOptionButtons.get(i).setText(label);
                    raiseOptionButtons.get(i).setCustomData(btnId);
                    raiseOptionPanels.get(i).getPosition().inTL(btnX, raiseOptionsY);
                } else {
                    raiseOptionPanels.get(i).getPosition().inTL(-1000, -1000);
                }
            }
        } else {
            for (CustomPanelAPI pnl : raiseOptionPanels) {
                pnl.getPosition().inTL(-1000, -1000);
            }
        }
    }
    
    /**
     * Creates action buttons at the bottom of the panel.
     * 
     * BUTTON HIERARCHY (per UI tutorial):
     * 1. Create sub-panel from main panel: panel.createCustomPanel()
     * 2. Create TooltipMakerAPI from sub-panel: subPanel.createUIElement()
     * 3. Add button to tooltip: tooltip.addButton()
     * 4. Add tooltip to sub-panel: subPanel.addUIElement(tooltip)
     * 5. Add sub-panel to main panel: panel.addComponent(subPanel)
     * 
     * IMPORTANT: Buttons must be recreated when game state changes.
     * This is called from updateButtons() which clears old buttons first.
     */
    protected void createButtons() {
        if (panel == null || p == null) {
            return;
        }
        
        // Clear old button references
        foldButton = null;
        checkCallButton = null;
        raiseButton = null;
        allInButton = null;
        raiseOptionButtons.clear();
        backButton = null;
        
        float panelWidth = p.getWidth();
        float panelHeight = p.getHeight();
        float bottomY = MARGIN; // Bottom margin
        
        // Get current game state for button labels
        PokerGame.PokerState state = game.getState();
        int callAmount = state.opponentBet - state.playerBet;
        boolean opponentEffectivelyAllIn = state.opponentStack <= state.bigBlind || state.opponentDeclaredAllIn;
        boolean canRaise = state.playerStack > 0 && state.opponentStack > 0 && callAmount < state.playerStack && !opponentEffectivelyAllIn;
        
        // ----------------------------------------------------------------------
        // Action buttons row (Fold, Check/Call, Raise)
        // ----------------------------------------------------------------------
        float actionButtonsY = bottomY;
        float totalActionWidth = BUTTON_WIDTH * 3 + BUTTON_SPACING * 2;
        float actionStartX = (panelWidth - totalActionWidth) / 2f;
        
        // Fold button (always available unless it's not player's turn)
        if (state.currentPlayer == PokerGame.CurrentPlayer.PLAYER && 
            state.round != PokerGame.Round.SHOWDOWN) {
            CustomPanelAPI foldPanel = panel.createCustomPanel(BUTTON_WIDTH, BUTTON_HEIGHT, null);
            TooltipMakerAPI foldTooltip = foldPanel.createUIElement(BUTTON_WIDTH, BUTTON_HEIGHT, false);
            foldButton = foldTooltip.addButton("Fold", "poker_fold", BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
            foldButton.getPosition().inTL(0, 0);
            foldPanel.addUIElement(foldTooltip).inTL(0, 0);
            panel.addComponent(foldPanel).inTL(actionStartX, actionButtonsY);
        }
        
        // Check/Call button
        if (state.currentPlayer == PokerGame.CurrentPlayer.PLAYER && 
            state.round != PokerGame.Round.SHOWDOWN) {
            String checkCallLabel = callAmount > 0 ? 
                "Call " + callAmount : "Check";
            float checkCallX = actionStartX + BUTTON_WIDTH + BUTTON_SPACING;
            
            CustomPanelAPI checkCallPanel = panel.createCustomPanel(BUTTON_WIDTH, BUTTON_HEIGHT, null);
            TooltipMakerAPI checkCallTooltip = checkCallPanel.createUIElement(BUTTON_WIDTH, BUTTON_HEIGHT, false);
            checkCallButton = checkCallTooltip.addButton(checkCallLabel, "poker_check_call", 
                BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
            checkCallButton.getPosition().inTL(0, 0);
            checkCallPanel.addUIElement(checkCallTooltip).inTL(0, 0);
            panel.addComponent(checkCallPanel).inTL(checkCallX, actionButtonsY);
        }
        
        // Raise button
        if (canRaise && state.currentPlayer == PokerGame.CurrentPlayer.PLAYER && 
            state.round != PokerGame.Round.SHOWDOWN) {
            float raiseX = actionStartX + (BUTTON_WIDTH + BUTTON_SPACING) * 2;
            
            CustomPanelAPI raisePanel = panel.createCustomPanel(BUTTON_WIDTH, BUTTON_HEIGHT, null);
            TooltipMakerAPI raiseTooltip = raisePanel.createUIElement(BUTTON_WIDTH, BUTTON_HEIGHT, false);
            raiseButton = raiseTooltip.addButton("Raise", "poker_raise", BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
            raiseButton.getPosition().inTL(0, 0);
            raisePanel.addUIElement(raiseTooltip).inTL(0, 0);
            panel.addComponent(raisePanel).inTL(raiseX, actionButtonsY);
        }
        
        // ----------------------------------------------------------------------
        // Raise amount options (show when raise mode is active)
        // For now, we'll create them inline as separate buttons
        // Label ABOVE the buttons
        // ----------------------------------------------------------------------
        if (canRaise && state.currentPlayer == PokerGame.CurrentPlayer.PLAYER && 
            state.round != PokerGame.Round.SHOWDOWN) {
            float raiseOptionsY = actionButtonsY + BUTTON_HEIGHT + BUTTON_SPACING;
            float[] raiseAmounts = getRaiseOptions(state);
            
            final float LABEL_WIDTH = 200f;
            float totalRaiseWidth = RAISE_BUTTON_WIDTH * raiseAmounts.length + BUTTON_SPACING * (raiseAmounts.length - 1);
            float raiseStartX = (panelWidth - totalRaiseWidth) / 2f;
            
            float labelX = (panelWidth - LABEL_WIDTH) / 2f;
            float labelY = raiseOptionsY + BUTTON_HEIGHT + 5f;
            CustomPanelAPI raiseLabelPanel = panel.createCustomPanel(LABEL_WIDTH, 20f, null);
            TooltipMakerAPI raiseLabelTooltip = raiseLabelPanel.createUIElement(LABEL_WIDTH, 20f, false);
            LabelAPI raiseLabel = raiseLabelTooltip.addPara("Raise options:", new Color(200, 200, 200), 0f);
            raiseLabel.setAlignment(Alignment.MID);
            raiseLabel.getPosition().inTL(0, 0);
            raiseLabelPanel.addUIElement(raiseLabelTooltip).inTL(0, 0);
            panel.addComponent(raiseLabelPanel).inTL(labelX, labelY);
            
            for (int i = 0; i < raiseAmounts.length; i++) {
                int amt = (int) raiseAmounts[i];
                String label = formatRaiseLabel(amt, state.bigBlind, state.pot, state.playerStack, state.opponentBet, state.playerBet);
                String btnId = "poker_raise_" + amt;
                float btnX = raiseStartX + (RAISE_BUTTON_WIDTH + BUTTON_SPACING) * i;
                
                CustomPanelAPI raiseOptPanel = panel.createCustomPanel(RAISE_BUTTON_WIDTH, BUTTON_HEIGHT, null);
                TooltipMakerAPI raiseOptTooltip = raiseOptPanel.createUIElement(RAISE_BUTTON_WIDTH, BUTTON_HEIGHT, false);
                ButtonAPI btn = raiseOptTooltip.addButton(label, btnId, 
                    RAISE_BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
                btn.setCustomData(btnId);
                btn.getPosition().inTL(0, 0);
                raiseOptPanel.addUIElement(raiseOptTooltip).inTL(0, 0);
                panel.addComponent(raiseOptPanel).inTL(btnX, raiseOptionsY);
                raiseOptionButtons.add(btn);
            }
        }
        
        // ----------------------------------------------------------------------
        // Back to menu button (always visible, top-right)
        // ----------------------------------------------------------------------
        float backX = panelWidth - BUTTON_WIDTH - MARGIN;
        float backY = panelHeight - BUTTON_HEIGHT - MARGIN;
        
        CustomPanelAPI backPanel = panel.createCustomPanel(BUTTON_WIDTH, BUTTON_HEIGHT, null);
        TooltipMakerAPI backTooltip = backPanel.createUIElement(BUTTON_WIDTH, BUTTON_HEIGHT, false);
        backButton = backTooltip.addButton("Leave", "poker_leave", BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        backButton.getPosition().inTL(0, 0);
        backPanel.addUIElement(backTooltip).inTL(0, 0);
        panel.addComponent(backPanel).inTL(backX, backY);
    }
    
    /**
     * Calculates raise amount options based on pot size and stack.
     * Returns TOTAL BET amounts (call amount + raise amount), not just raise portion.
     * This matches the text-based UI behavior where "Raise 50" when needing to call 300
     * should result in a total bet of 350.
     */
    protected float[] getRaiseOptions(PokerGame.PokerState state) {
        List<Float> options = new ArrayList<>();
        int pot = state.pot;
        int stack = state.playerStack;
        int bb = state.bigBlind;
        int opponentBet = state.opponentBet;
        
        // Total bet = opponent's bet + raise portion
        // 1. BB (minimum raise) - total bet = opponentBet + bb
        int bbTotal = opponentBet + bb;
        if (bbTotal <= state.playerBet + stack) options.add((float) bbTotal);
        
        // 2. Half Pot - total bet = opponentBet + halfPot
        int halfPot = pot / 2;
        int halfPotTotal = opponentBet + halfPot;
        if (halfPot >= bb && halfPotTotal <= state.playerBet + stack && !options.contains((float) halfPotTotal)) {
            options.add((float) halfPotTotal);
        }
        
        // 3. Pot - total bet = opponentBet + pot
        int potTotal = opponentBet + pot;
        if (pot >= bb && potTotal <= state.playerBet + stack && !options.contains((float) potTotal)) {
            options.add((float) potTotal);
        }
        
        // 4. 2x Pot - total bet = opponentBet + 2xPot
        int twoPot = pot * 2;
        int twoPotTotal = opponentBet + twoPot;
        if (twoPot > pot && twoPotTotal <= state.playerBet + stack && !options.contains((float) twoPotTotal)) {
            options.add((float) twoPotTotal);
        }
        
        // 5. All-in (always last) - total bet = playerBet + remaining stack
        int allInTotal = state.playerBet + stack;
        if (stack > 0 && !options.contains((float) allInTotal)) {
            options.add((float) allInTotal);
        }
        
        // Convert to primitive array
        float[] result = new float[options.size()];
        for (int i = 0; i < options.size(); i++) {
            result[i] = options.get(i);
        }
        return result;
    }
    
    /**
     * Formats raise label to show total bet and raise portion.
     * Format: "Label (total / X.X BB)" where total = callAmount + raisePortion
     */
    protected String formatRaiseLabel(int totalBet, int bigBlind, int pot, int playerStack, int opponentBet, int playerBet) {
        int raisePortion = totalBet - opponentBet;
        
        String label;
        if (raisePortion == bigBlind) {
            label = "BB";
        } else if (pot > 0 && raisePortion == pot / 2) {
            label = "Half Pot";
        } else if (raisePortion == pot) {
            label = "Pot";
        } else if (pot > 0 && raisePortion == pot * 2) {
            label = "2x Pot";
        } else if (totalBet == playerBet + playerStack) {
            label = "All-In";
        } else {
            label = "Raise";
        }
        
        float bbAmount = bigBlind > 0 ? (float) totalBet / bigBlind : 0;
        return String.format("%s (%d / %.1f BB)", label, totalBet, bbAmount);
    }
    
    /**
     * Creates the pot display label - NOW DISABLED.
     * Pot info is now integrated into the round label.
     * This method is kept for compatibility but does nothing.
     */
    protected void createPotDisplay(PokerGame.PokerState state) {
        // Pot is now shown in the round label instead
        // This method is kept for compatibility but does nothing
    }
    
    protected void updatePotDisplay(int pot, int bigBlind) {
        // Pot is now shown in the round label instead
    }
    
    protected void updatePotAndBetsDisplay(int pot, int playerBet, int opponentBet) {
        // Pot is now shown in the round label instead
        // Bet info is now in stack displays
    }
    
    /**
     * Creates stack display labels using LabelAPI.
     * Layout: Opponent at top (Y=70), Player at bottom (Y=580)
     * Shows: "Opponent Stack: 9900 | Opponent current bet: 100"
     */
    protected void createStackDisplays() {
        if (panel == null) return;
        
        final float STACK_PANEL_WIDTH = 420f;
        final float STACK_PANEL_HEIGHT = 30f;
        
        float oppX = MARGIN;
        float oppY = 140f;
        
        opponentStackPanel = panel.createCustomPanel(STACK_PANEL_WIDTH, STACK_PANEL_HEIGHT, null);
        TooltipMakerAPI oppTooltip = opponentStackPanel.createUIElement(STACK_PANEL_WIDTH, STACK_PANEL_HEIGHT, false);
        opponentStackLabel = oppTooltip.addPara("Opponent Stack: 0 | Opponent current bet: 0", COLOR_OPPONENT, 0f);
        opponentStackLabel.setAlignment(Alignment.LMID);
        opponentStackLabel.getPosition().inTL(0, 0);
        opponentStackPanel.addUIElement(oppTooltip).inTL(0, 0);
        panel.addComponent(opponentStackPanel).inTL(oppX, oppY);
        
        float playerX = MARGIN;
        float playerY = PANEL_HEIGHT - 150f;
        
        playerStackPanel = panel.createCustomPanel(STACK_PANEL_WIDTH, STACK_PANEL_HEIGHT, null);
        TooltipMakerAPI playerTooltip = playerStackPanel.createUIElement(STACK_PANEL_WIDTH, STACK_PANEL_HEIGHT, false);
        playerStackLabel = playerTooltip.addPara("Player Stack: 0 | Player current bet: 0", COLOR_PLAYER, 0f);
        playerStackLabel.setAlignment(Alignment.LMID);
        playerStackLabel.getPosition().inTL(0, 0);
        playerStackPanel.addUIElement(playerTooltip).inTL(0, 0);
        panel.addComponent(playerStackPanel).inTL(playerX, playerY);
    }
    
    /**
     * Updates stack display labels with stack and current bet.
     */
    protected void updateStackDisplays(int playerStack, int opponentStack, int playerBet, int opponentBet) {
        if (playerStackLabel != null) {
            if (playerStack != lastPlayerStack || playerBet != lastPlayerBet) {
                lastPlayerStack = playerStack;
                lastPlayerBet = playerBet;
                lastPlayerStackText = String.format("Player Stack: %d | Player current bet: %d", playerStack, playerBet);
            }
            playerStackLabel.setText(lastPlayerStackText);
        }
        if (opponentStackLabel != null) {
            if (opponentStack != lastOpponentStack || opponentBet != lastOpponentBet) {
                lastOpponentStack = opponentStack;
                lastOpponentBet = opponentBet;
                lastOpponentStackText = String.format("Opponent Stack: %d | Opponent current bet: %d", opponentStack, opponentBet);
            }
            opponentStackLabel.setText(lastOpponentStackText);
        }
    }
    
    protected void createBetLabels() {
        // Bet info is now integrated into stack displays
    }
    
    protected void updateBetLabels(int playerBet, int opponentBet) {
        // Bet info is now integrated into stack displays
        // Called from external code for compatibility
    }
    
    /**
     * Creates round indicator label - positioned at center-top above table.
     * Displays: "Round Progress: Flop | Current Pot: 200 | Big Blind (minimal bet): 50"
     */
    protected void createRoundLabel() {
        if (panel == null) return;
        
        final float ROUND_PANEL_WIDTH = 600f;
        final float ROUND_PANEL_HEIGHT = 30f;
        
        float x = (PANEL_WIDTH - ROUND_PANEL_WIDTH) / 2f;
        float y = PANEL_HEIGHT * 0.40f;
        
        roundPanel = panel.createCustomPanel(ROUND_PANEL_WIDTH, ROUND_PANEL_HEIGHT, null);
        TooltipMakerAPI tooltip = roundPanel.createUIElement(ROUND_PANEL_WIDTH, ROUND_PANEL_HEIGHT, false);
        roundLabel = tooltip.addPara("Round Progress: Preflop | Current Pot: 0 | Big Blind (minimal bet): 50", new Color(150, 200, 255), 0f);
        roundLabel.setAlignment(Alignment.MID);
        roundLabel.getPosition().inMid();
        roundPanel.addUIElement(tooltip).inTL(0, 0);
        panel.addComponent(roundPanel).inTL(x, y);
    }
    
    /**
     * Updates round indicator label with round name, pot and BB value.
     */
    protected void updateRoundLabel(PokerGame.Round round, int bigBlind, int pot) {
        if (roundLabel == null) return;
        
        if (round != lastRound || bigBlind != lastBigBlind || pot != lastPot) {
            lastRound = round;
            lastBigBlind = bigBlind;
            lastPot = pot;
            
            String roundName = switch (round) {
                case PREFLOP -> "Preflop";
                case FLOP -> "Flop";
                case TURN -> "Turn";
                case RIVER -> "River";
                case SHOWDOWN -> "Showdown";
            };
            
            lastRoundText = String.format("Round Progress: %s | Current Pot: %d | Big Blind (minimal bet): %d", roundName, pot, bigBlind);
            
            Color roundColor = switch (round) {
                case PREFLOP -> COLOR_ROUND_PREFLOP;
                case FLOP -> COLOR_ROUND_FLOP;
                case TURN -> COLOR_ROUND_TURN;
                case RIVER -> COLOR_ROUND_RIVER;
                case SHOWDOWN -> COLOR_ROUND_SHOWDOWN;
            };
            roundLabel.setColor(roundColor);
        }
        
        roundLabel.setText(lastRoundText);
    }
    
    /**
     * Creates waiting indicator label - center of screen.
     */
    protected void createWaitingLabel() {
        if (panel == null) return;
        
        final float WAIT_PANEL_WIDTH = 160f;
        final float WAIT_PANEL_HEIGHT = 30f;
        
        float x = (PANEL_WIDTH - WAIT_PANEL_WIDTH) / 2f;
        float y = PANEL_HEIGHT * 0.68f; // Below pot display
        
        waitingPanel = panel.createCustomPanel(WAIT_PANEL_WIDTH, WAIT_PANEL_HEIGHT, null);
        TooltipMakerAPI tooltip = waitingPanel.createUIElement(WAIT_PANEL_WIDTH, WAIT_PANEL_HEIGHT, false);
        waitingLabel = tooltip.addPara("Opponent thinking...", new Color(255, 200, 100), 0f);
        waitingLabel.setAlignment(Alignment.MID);
        waitingLabel.getPosition().inMid();
        waitingPanel.addUIElement(tooltip).inTL(0, 0);
        panel.addComponent(waitingPanel).inTL(x, y);
    }
    
    /**
     * Updates waiting indicator visibility.
     */
    protected void updateWaitingLabel(boolean waiting) {
        if (waitingPanel != null) {
            // Move off-screen if not waiting
            waitingPanel.getPosition().setYAlignOffset(waiting ? 0 : -1000);
        }
    }
    
    /**
     * Creates opponent action display label - below opponent stack info.
     * Opponent stack is at y=120, height=30, so action goes below that.
     */
    protected void createOpponentActionLabel() {
        if (panel == null) return;
        
        final float ACTION_PANEL_WIDTH = 250f;
        final float ACTION_PANEL_HEIGHT = 25f;
        
        opponentActionPanel = panel.createCustomPanel(ACTION_PANEL_WIDTH, ACTION_PANEL_HEIGHT, null);
        TooltipMakerAPI tooltip = opponentActionPanel.createUIElement(ACTION_PANEL_WIDTH, ACTION_PANEL_HEIGHT, false);
        opponentActionLabel = tooltip.addPara("", COLOR_OPPONENT, 0f);
        opponentActionLabel.setAlignment(Alignment.LMID);
        opponentActionLabel.getPosition().inTL(0, 0);
        opponentActionPanel.addUIElement(tooltip).inTL(0, 0);
        panel.addComponent(opponentActionPanel).inTL(-1000, -1000);
    }
    
    /**
     * Creates player action display label - above player stack info.
     * Player stack is at y=PANEL_HEIGHT-130, height=30, so action goes above that.
     */
    protected void createPlayerActionLabel() {
        if (panel == null) return;
        
        final float ACTION_PANEL_WIDTH = 250f;
        final float ACTION_PANEL_HEIGHT = 25f;
        
        playerActionPanel = panel.createCustomPanel(ACTION_PANEL_WIDTH, ACTION_PANEL_HEIGHT, null);
        TooltipMakerAPI tooltip = playerActionPanel.createUIElement(ACTION_PANEL_WIDTH, ACTION_PANEL_HEIGHT, false);
        playerActionLabel = tooltip.addPara("", COLOR_PLAYER, 0f);
        playerActionLabel.setAlignment(Alignment.LMID);
        playerActionLabel.getPosition().inTL(0, 0);
        playerActionPanel.addUIElement(tooltip).inTL(0, 0);
        panel.addComponent(playerActionPanel).inTL(-1000, -1000);
    }
    
    /**
     * Updates opponent action display.
     * @param action The action text to display (e.g., "Opponent calls", "Opponent raises by 50")
     */
    public void showOpponentAction(String action) {
        lastOpponentAction = action;
        if (opponentActionLabel != null && opponentActionPanel != null) {
            opponentActionLabel.setText(action);
            opponentActionPanel.getPosition().inTL(160, 200);
        }
    }
    
    /**
     * Hides opponent action display.
     */
    public void hideOpponentAction() {
        if (opponentActionPanel != null) {
            opponentActionPanel.getPosition().inTL(-1000, -1000);
        }
    }
    
    /**
     * Updates player action display.
     * @param action The action text to display (e.g., "You call", "You raise by 50")
     */
    public void showPlayerAction(String action) {
        lastPlayerAction = action;
        if (playerActionLabel != null && playerActionPanel != null) {
            playerActionLabel.setText(action);
            playerActionPanel.getPosition().inTL(160, 480);
        }
    }
    
    /**
     * Hides player action display.
     */
    public void hidePlayerAction() {
        if (playerActionPanel != null) {
            playerActionPanel.getPosition().inTL(-1000, -1000);
        }
    }
    
    /**
     * Creates AI personality display label - at top-right below buttons.
     */
    protected void createAIPersonalityLabel() {
        if (panel == null) return;
        
        final float LABEL_WIDTH = 300f;
        final float LABEL_HEIGHT = 20f;
        
        float x = PANEL_WIDTH - LABEL_WIDTH - MARGIN;
        float y = MARGIN + BUTTON_HEIGHT + 10f;
        
        aiPersonalityPanel = panel.createCustomPanel(LABEL_WIDTH, LABEL_HEIGHT, null);
        TooltipMakerAPI tooltip = aiPersonalityPanel.createUIElement(LABEL_WIDTH, LABEL_HEIGHT, false);
        String personality = game.getAIPersonalityDescription();
        aiPersonalityLabel = tooltip.addPara(personality, new Color(150, 150, 150), 0f);
        aiPersonalityLabel.setAlignment(Alignment.RMID);
        aiPersonalityLabel.getPosition().inTL(0, 0);
        aiPersonalityPanel.addUIElement(tooltip).inTL(0, 0);
        panel.addComponent(aiPersonalityPanel).inTL(x, y);
    }
    
    /**
     * Creates return from suspend message label - center of panel.
     */
    protected void createReturnMessageLabel() {
        if (panel == null) return;
        
        final float MSG_WIDTH = 400f;
        final float MSG_HEIGHT = 30f;
        
        float x = (PANEL_WIDTH - MSG_WIDTH) / 2f;
        float y = PANEL_HEIGHT * 0.3f;
        
        returnMessagePanel = panel.createCustomPanel(MSG_WIDTH, MSG_HEIGHT, null);
        TooltipMakerAPI tooltip = returnMessagePanel.createUIElement(MSG_WIDTH, MSG_HEIGHT, false);
        returnMessageLabel = tooltip.addPara("", new Color(200, 200, 100), 0f);
        returnMessageLabel.setAlignment(Alignment.MID);
        returnMessageLabel.getPosition().inMid();
        returnMessagePanel.addUIElement(tooltip).inTL(0, 0);
        returnMessagePanel.getPosition().setYAlignOffset(-1000);
        panel.addComponent(returnMessagePanel).inTL(x, y);
    }
    
    /**
     * Shows a return from suspend message.
     */
    public void showReturnMessage(String message) {
        if (returnMessageLabel != null && returnMessagePanel != null) {
            returnMessageLabel.setText(message);
            returnMessagePanel.getPosition().setYAlignOffset(0);
        }
    }
    
    /**
     * Hides the return message.
     */
    public void hideReturnMessage() {
        if (returnMessagePanel != null) {
            returnMessagePanel.getPosition().setYAlignOffset(-1000);
        }
    }
    
    /**
     * Creates card rank labels for displaying actual text on cards.
     * These are positioned at card locations when cards are rendered.
     */
    protected void createCardRankLabels() {
        if (panel == null) return;
        
        final float LABEL_WIDTH = 50f;
        final float LABEL_HEIGHT = 20f;
        Color yellowHighlight = new Color(1f, 0.85f, 0f);
        
        // Create labels for player cards (2 cards)
        for (int i = 0; i < 2; i++) {
            playerCardPanels[i] = panel.createCustomPanel(LABEL_WIDTH, LABEL_HEIGHT, null);
            TooltipMakerAPI tooltip = playerCardPanels[i].createUIElement(LABEL_WIDTH, LABEL_HEIGHT, false);
            playerCardRankLabels[i] = tooltip.addPara("??", Color.WHITE, 0f);
            playerCardRankLabels[i].setAlignment(Alignment.MID);
            playerCardRankLabels[i].setHighlightColor(yellowHighlight);
            playerCardRankLabels[i].setHighlight(0, 2);
            playerCardRankLabels[i].getPosition().inMid();
            playerCardPanels[i].addUIElement(tooltip).inTL(0, 0);
            panel.addComponent(playerCardPanels[i]).inTL(-1000, -1000);
        }
        
        // Create labels for opponent cards (2 cards)
        for (int i = 0; i < 2; i++) {
            opponentCardPanels[i] = panel.createCustomPanel(LABEL_WIDTH, LABEL_HEIGHT, null);
            TooltipMakerAPI tooltip = opponentCardPanels[i].createUIElement(LABEL_WIDTH, LABEL_HEIGHT, false);
            opponentCardRankLabels[i] = tooltip.addPara("??", Color.WHITE, 0f);
            opponentCardRankLabels[i].setAlignment(Alignment.MID);
            opponentCardRankLabels[i].setHighlightColor(yellowHighlight);
            opponentCardRankLabels[i].setHighlight(0, 2);
            opponentCardRankLabels[i].getPosition().inMid();
            opponentCardPanels[i].addUIElement(tooltip).inTL(0, 0);
            panel.addComponent(opponentCardPanels[i]).inTL(-1000, -1000);
        }
        
        // Create labels for community cards (5 cards)
        for (int i = 0; i < 5; i++) {
            communityCardPanels[i] = panel.createCustomPanel(LABEL_WIDTH, LABEL_HEIGHT, null);
            TooltipMakerAPI tooltip = communityCardPanels[i].createUIElement(LABEL_WIDTH, LABEL_HEIGHT, false);
            communityCardRankLabels[i] = tooltip.addPara("??", Color.WHITE, 0f);
            communityCardRankLabels[i].setAlignment(Alignment.MID);
            communityCardRankLabels[i].setHighlightColor(yellowHighlight);
            communityCardRankLabels[i].setHighlight(0, 2);
            communityCardRankLabels[i].getPosition().inMid();
            communityCardPanels[i].addUIElement(tooltip).inTL(0, 0);
            panel.addComponent(communityCardPanels[i]).inTL(-1000, -1000);
        }
    }
    
    /**
     * Updates card rank labels based on current game state.
     * OpenGL coordinate system: Y=0 at BOTTOM, Y increases UP.
     * Player cards at visual BOTTOM (LOW Y), Opponent cards at visual TOP (HIGH Y).
     */
    protected void updateCardRankLabels(float panelWidth, float panelHeight, float panelX, float panelY) {
        PokerGame.PokerState state = game.getState();
        
        int playerHandSize = state.playerHand != null ? state.playerHand.size() : 0;
        int opponentHandSize = state.opponentHand != null ? state.opponentHand.size() : 0;
        int communitySize = state.communityCards != null ? state.communityCards.size() : 0;
        boolean showOpponent = state.round == PokerGame.Round.SHOWDOWN;
        
        boolean needsUpdate = lastCardUpdateRound != state.round ||
            lastPlayerHandSize != playerHandSize ||
            lastOpponentHandSize != opponentHandSize ||
            lastCommunityCardsSize != communitySize;
        
        if (!needsUpdate && lastCardUpdateRound == state.round) {
            for (int i = 0; i < playerHandSize && i < 2; i++) {
                PokerGame.PokerGameLogic.Card card = state.playerHand.get(i);
                String rankText = card != null ? getRankString(card.rank) : "??";
                if (!rankText.equals(lastPlayerCardRanks[i])) {
                    needsUpdate = true;
                    break;
                }
            }
        }
        
        if (!needsUpdate) {
            return;
        }
        
        lastCardUpdateRound = state.round;
        lastPlayerHandSize = playerHandSize;
        lastOpponentHandSize = opponentHandSize;
        lastCommunityCardsSize = communitySize;
        
        float cx = panelWidth / 2f;
        
        float playerCardBottomY = panelHeight * 0.25f - CARD_HEIGHT / 2f;
        
        if (state.playerHand != null && !state.playerHand.isEmpty()) {
            float totalWidth = 2 * CARD_WIDTH + CARD_SPACING;
            float startX = cx - totalWidth / 2f;
            
            for (int i = 0; i < Math.min(2, state.playerHand.size()); i++) {
                PokerGame.PokerGameLogic.Card card = state.playerHand.get(i);
                float cardX = startX + i * (CARD_WIDTH + CARD_SPACING);
                
                if (playerCardRankLabels[i] != null && card != null) {
                    String rankText = getRankString(card.rank);
                    lastPlayerCardRanks[i] = rankText;
                    Color color = getSuitColor(card.suit);
                    playerCardRankLabels[i].setText(rankText);
                    playerCardRankLabels[i].setColor(color);
                    playerCardRankLabels[i].setHighlight(0, rankText.length());
                    
                    float labelX = cardX + 10;
                    float labelY = panelHeight - playerCardBottomY - CARD_HEIGHT + 4;
                    playerCardPanels[i].getPosition().inTL(labelX, labelY);
                }
            }
        }
        
        float opponentCardBottomY = panelHeight * 0.75f - CARD_HEIGHT / 2f;
        
        if (showOpponent && state.opponentHand != null && !state.opponentHand.isEmpty()) {
            float totalWidth = 2 * CARD_WIDTH + CARD_SPACING;
            float startX = cx - totalWidth / 2f;
            
            for (int i = 0; i < Math.min(2, state.opponentHand.size()); i++) {
                PokerGame.PokerGameLogic.Card card = state.opponentHand.get(i);
                float cardX = startX + i * (CARD_WIDTH + CARD_SPACING);
                
                if (opponentCardRankLabels[i] != null && card != null) {
                    String rankText = getRankString(card.rank);
                    lastOpponentCardRanks[i] = rankText;
                    Color color = getSuitColor(card.suit);
                    opponentCardRankLabels[i].setText(rankText);
                    opponentCardRankLabels[i].setColor(color);
                    opponentCardRankLabels[i].setHighlight(0, rankText.length());
                    
                    float labelX = cardX + 10;
                    float labelY = panelHeight - opponentCardBottomY - CARD_HEIGHT + 4;
                    opponentCardPanels[i].getPosition().inTL(labelX, labelY);
                }
            }
        } else {
            for (int i = 0; i < 2; i++) {
                if (opponentCardPanels[i] != null) {
                    opponentCardPanels[i].getPosition().inTL(-1000, -1000);
                }
                lastOpponentCardRanks[i] = null;
            }
        }
        
        float commCardBottomY = panelHeight / 2f - CARD_HEIGHT / 2f;
        
        if (state.communityCards != null && !state.communityCards.isEmpty()) {
            int numCards = state.communityCards.size();
            float totalWidth = numCards * CARD_WIDTH + (numCards - 1) * CARD_SPACING;
            float startX = cx - totalWidth / 2f;
            
            for (int i = 0; i < numCards && i < 5; i++) {
                PokerGame.PokerGameLogic.Card card = state.communityCards.get(i);
                float cardX = startX + i * (CARD_WIDTH + CARD_SPACING);
                
                if (communityCardRankLabels[i] != null && card != null) {
                    String rankText = getRankString(card.rank);
                    lastCommunityCardRanks[i] = rankText;
                    Color color = getSuitColor(card.suit);
                    communityCardRankLabels[i].setText(rankText);
                    communityCardRankLabels[i].setColor(color);
                    communityCardRankLabels[i].setHighlight(0, rankText.length());
                    
                    float labelX = cardX + 10;
                    float labelY = panelHeight - commCardBottomY - CARD_HEIGHT + 4;
                    communityCardPanels[i].getPosition().inTL(labelX, labelY);
                }
            }
            
            for (int i = numCards; i < 5; i++) {
                if (communityCardPanels[i] != null) {
                    communityCardPanels[i].getPosition().inTL(-1000, -1000);
                }
                lastCommunityCardRanks[i] = null;
            }
        } else {
            for (int i = 0; i < 5; i++) {
                if (communityCardPanels[i] != null) {
                    communityCardPanels[i].getPosition().inTL(-1000, -1000);
                }
                lastCommunityCardRanks[i] = null;
            }
        }
    }
    
    /**
     * Gets a suit symbol for display.
     */
    protected String getSuitSymbol(PokerGame.PokerGameLogic.Suit suit) {
        return switch (suit) {
            case SPADES -> "♠";
            case HEARTS -> "♥";
            case DIAMONDS -> "♦";
            case CLUBS -> "♣";
        };
    }
    
    /**
     * Creates the result display labels for showdown - showing hand details.
     * Layout: [Player Hand] / [Opponent Hand] on first lines
     *         [Result Text] on next line
     */
    protected void createResultLabel() {
        if (panel == null) return;
        
        final float RESULT_PANEL_WIDTH = 450f;
        final float RESULT_PANEL_HEIGHT = 30f;
        
        // Result label (winner + pot)
        resultPanel = panel.createCustomPanel(RESULT_PANEL_WIDTH, RESULT_PANEL_HEIGHT, null);
        TooltipMakerAPI tooltip = resultPanel.createUIElement(RESULT_PANEL_WIDTH, RESULT_PANEL_HEIGHT, false);
        resultLabel = tooltip.addPara("", Color.WHITE, 0f);
        resultLabel.setAlignment(Alignment.MID);
        resultLabel.getPosition().inTL(0, 5);
        resultPanel.addUIElement(tooltip).inTL(0, 0);
        panel.addComponent(resultPanel).inTL(-1000, -1000);
    }
    
    /**
     * Updates the result display with detailed hand information.
     */
    protected void updateResultLabel(PokerGame.PokerState state) {
        if (resultLabel == null || state == null) return;
        
        if (state.round != PokerGame.Round.SHOWDOWN) {
            if (resultPanel != null) resultPanel.getPosition().inTL(-1000, -1000);
            resultCached = false;
            cachedPlayerScore = null;
            cachedOpponentScore = null;
            return;
        }
        
        if (resultCached && cachedResultText != null) {
            resultLabel.setText(cachedResultText);
            resultLabel.setColor(cachedResultColor);
            resultPanel.getPosition().inTL(PANEL_WIDTH / 2f - 225f, PANEL_HEIGHT * 0.32f);
            return;
        }
        
        String resultText = "";
        Color resultColor = Color.WHITE;
        int potWon = state.lastPotWon;
        
        if (state.folder != null) {
            if (state.folder == PokerGame.CurrentPlayer.PLAYER) {
                resultText = "You folded - Opponent wins " + potWon + " Stargems";
                resultColor = COLOR_OPPONENT;
                hideOpponentAction();
                hidePlayerAction();
            } else {
                resultText = "Opponent folded - You win " + potWon + " Stargems!";
                resultColor = COLOR_PLAYER;
                hideOpponentAction();
                hidePlayerAction();
            }
        } else if (state.playerHandRank != null && state.opponentHandRank != null) {
            cachedPlayerScore = PokerGame.PokerGameLogic.evaluate(state.playerHand, state.communityCards);
            cachedOpponentScore = PokerGame.PokerGameLogic.evaluate(state.opponentHand, state.communityCards);
            
            String playerHandDesc = formatHandDescription(cachedPlayerScore);
            String oppHandDesc = formatHandDescription(cachedOpponentScore);
            
            showPlayerAction("You: " + playerHandDesc);
            showOpponentAction("Opp: " + oppHandDesc);
            
            int cmp = cachedPlayerScore.compareTo(cachedOpponentScore);
            if (cmp > 0) {
                resultText = "YOU WIN " + potWon + " Stargems!";
                resultColor = Color.GREEN;
            } else if (cmp < 0) {
                resultText = "Opponent wins " + potWon + " Stargems";
                resultColor = COLOR_OPPONENT;
            } else {
                resultText = "Split pot! +" + (potWon / 2) + " Stargems each";
                resultColor = Color.YELLOW;
            }
        }
        
        boolean playerBust = state.playerStack < state.bigBlind;
        boolean opponentBust = state.opponentStack < state.bigBlind;
        
        if (playerBust || opponentBust) {
            String bustMessage = playerBust ? 
                " You went BUST! Click 'Leave' to exit." : 
                " Opponent went BUST! Click 'Leave' to exit.";
            resultText += bustMessage;
        }
        
        cachedResultText = resultText;
        cachedResultColor = resultColor;
        resultCached = true;
        
        resultLabel.setText(resultText);
        resultLabel.setColor(resultColor);
        resultPanel.getPosition().inTL(PANEL_WIDTH / 2f - 225f, PANEL_HEIGHT * 0.32f);
    }
    
    /**
     * Formats a hand score into a human-readable description.
     * E.g., "Two Pair - Kings and Tens" or "Flush - Ace high"
     */
    protected String formatHandDescription(PokerGame.PokerGameLogic.HandScore score) {
        if (score == null || score.rank == null) return "Unknown";
        
        String rankName = formatHandRank(score.rank);
        
        if (score.tieBreakers == null || score.tieBreakers.isEmpty()) {
            return rankName;
        }
        
        String highCard = getRankName(score.tieBreakers.get(0));
        
        switch (score.rank) {
            case HIGH_CARD:
                return highCard + " high";
            case PAIR:
                return "Pair of " + highCard + "s";
            case TWO_PAIR:
                if (score.tieBreakers.size() >= 2) {
                    String firstPair = getRankName(score.tieBreakers.get(0));
                    String secondPair = getRankName(score.tieBreakers.get(1));
                    return "Two Pair - " + firstPair + "s and " + secondPair + "s";
                }
                return "Two Pair";
            case THREE_OF_A_KIND:
                return "Three " + highCard + "s";
            case STRAIGHT:
                return "Straight - " + highCard + " high";
            case FLUSH:
                return "Flush - " + highCard + " high";
            case FULL_HOUSE:
                if (score.tieBreakers.size() >= 2) {
                    String trips = getRankName(score.tieBreakers.get(0));
                    String pair = getRankName(score.tieBreakers.get(1));
                    return "Full House - " + trips + "s full of " + pair + "s";
                }
                return "Full House";
            case FOUR_OF_A_KIND:
                return "Four " + highCard + "s";
            case STRAIGHT_FLUSH:
                if (highCard.equals("Ace")) {
                    return "Royal Flush!";
                }
                return "Straight Flush - " + highCard + " high";
            default:
                return rankName;
        }
    }
    
    /**
     * Formats a hand rank enum into a readable string.
     */
    protected String formatHandRank(PokerGame.PokerGameLogic.HandRank rank) {
        if (rank == null) return "Unknown";
        switch (rank) {
            case HIGH_CARD: return "High Card";
            case PAIR: return "Pair";
            case TWO_PAIR: return "Two Pair";
            case THREE_OF_A_KIND: return "Three of a Kind";
            case STRAIGHT: return "Straight";
            case FLUSH: return "Flush";
            case FULL_HOUSE: return "Full House";
            case FOUR_OF_A_KIND: return "Four of a Kind";
            case STRAIGHT_FLUSH: return "Straight Flush";
            default: return rank.name();
        }
    }
    
    /**
     * Converts a numeric rank value to a card rank name.
     */
    protected String getRankName(int rankValue) {
        switch (rankValue) {
            case 14: return "Ace";
            case 13: return "King";
            case 12: return "Queen";
            case 11: return "Jack";
            case 10: return "10";
            case 9: return "9";
            case 8: return "8";
            case 7: return "7";
            case 6: return "6";
            case 5: return "5";
            case 4: return "4";
            case 3: return "3";
            case 2: return "2";
            default: return String.valueOf(rankValue);
        }
    }
    
    /**
     * Formats a list of cards into a short string.
     */
    protected String formatCards(List<PokerGame.PokerGameLogic.Card> cards) {
        if (cards == null || cards.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cards.size(); i++) {
            if (i > 0) sb.append(" ");
            sb.append(cards.get(i).rank.symbol);
        }
        return sb.toString();
    }
    
    /**
     * Creates the "Next Hand" button for after showdown - below community cards, right of result label.
     * Layout: [Result Text] [Next Hand Button] - horizontally arranged below community cards.
     */
    protected void createNextHandButton() {
        if (panel == null) return;
        
        final float BUTTON_W = 120f;
        final float BUTTON_H = 30f;
        
        nextHandPanel = panel.createCustomPanel(BUTTON_W, BUTTON_H, null);
        TooltipMakerAPI tooltip = nextHandPanel.createUIElement(BUTTON_W, BUTTON_H, false);
        nextHandButton = tooltip.addButton("Next Hand", "poker_next_hand", new Color(0, 0, 0), new Color(255, 200, 0), BUTTON_W, BUTTON_H, 0f);
        nextHandButton.setCustomData("poker_next_hand");
        nextHandButton.getPosition().inTL(0, 0);
        nextHandPanel.addUIElement(tooltip).inTL(0, 0);
        panel.addComponent(nextHandPanel).inTL(-1000, -1000);
    }
    
    /**
     * Updates the "Next Hand" button visibility.
     * Always shows at showdown - either to start next hand or to end the game.
     */
    protected void updateNextHandButton(PokerGame.PokerState state) {
        if (nextHandPanel == null) return;
        
        boolean atShowdown = state.round == PokerGame.Round.SHOWDOWN;
        boolean canContinue = state.playerStack >= state.bigBlind && 
                              state.opponentStack >= state.bigBlind;
        
        if (atShowdown) {
            // Always show button at showdown - label changes based on whether game can continue
            if (canContinue) {
                nextHandButton.setText("Next Hand");
            } else {
                nextHandButton.setText("Leave");
            }
            nextHandPanel.getPosition().inTL(PANEL_WIDTH / 2f + 200f, PANEL_HEIGHT / 2f + CARD_HEIGHT / 2f + 70f);
        } else {
            nextHandPanel.getPosition().inTL(-1000, -1000);
        }
    }
    
    // ============================================================================
    // RENDERING
    // ============================================================================
    
    /**
     * Render below UI elements - main content area.
     * RENDERING ORDER (back to front):
     * 1. Background fill
     * 2. Poker table
     * 3. Community cards
     * 4. Player hand
     * 5. Pot and stack displays
     * 
     * NOTE: If position (p) is null, we use hardcoded dimensions matching the
     * dialog size (800x600) as fallback.
     */
    public void renderBelow(float alphaMult) {
        // Fallback dimensions if position not set
        float x, y, w, h, cx, cy;
        
        if (p != null) {
            x = p.getX();
            y = p.getY();
            w = p.getWidth();
            h = p.getHeight();
            cx = p.getCenterX();
            cy = p.getCenterY();
        } else {
            // Use hardcoded dimensions as fallback
            // These match the dialog size from showCustomVisualDialog()
            x = 0;
            y = 0;
            w = PANEL_WIDTH;
            h = PANEL_HEIGHT;
            cx = w / 2f;
            cy = h / 2f;
        }

        // CRITICAL: Enable scissor test to clip rendering to panel boundaries
        // This prevents rendering outside the dialog area
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        float s = Global.getSettings().getScreenScaleMult();
        GL11.glScissor((int)(x * s), (int)(y * s), (int)(w * s), (int)(h * s));
        
        // ----------------------------------------------------------------------
        // 1. Background - dark gradient
        // ----------------------------------------------------------------------
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        Misc.renderQuad(x, y, w, h, COLOR_BG_DARK, alphaMult);
        
        // ----------------------------------------------------------------------
        // 2. Poker table - oval shape in center
        // ----------------------------------------------------------------------
        renderPokerTable(cx, cy, w * 0.8f, h * 0.5f, alphaMult);
        
        // ----------------------------------------------------------------------
        // 3. Get game state for UI updates
        // ----------------------------------------------------------------------
        PokerGame.PokerState state = game.getState();
        
        // ----------------------------------------------------------------------
        // 3b. Update text labels (stacks with bets, round with pot, waiting)
        // ----------------------------------------------------------------------
        updateStackDisplays(state.playerStack, state.opponentStack, state.playerBet, state.opponentBet);
        updateRoundLabel(state.round, state.bigBlind, state.pot);
        updateWaitingLabel(waitingForOpponent);
        
        // ----------------------------------------------------------------------
        // 3c. Update card rank labels (actual text on cards)
        // ----------------------------------------------------------------------
        updateCardRankLabels(w, h, x, y);
        
        // ----------------------------------------------------------------------
        // 3d. Update result label and next hand button for showdown
        // ----------------------------------------------------------------------
        updateResultLabel(state);
        updateNextHandButton(state);
        updateButtonVisibility();
        
        // ----------------------------------------------------------------------
        // 4. Community cards (center of table)
        // ----------------------------------------------------------------------
        if (!state.communityCards.isEmpty()) {
            renderCommunityCards(cx, cy, state.communityCards, alphaMult);
        }
        
        // ----------------------------------------------------------------------
        // 5. Player hand (BOTTOM area - player sits at bottom)
        // OpenGL: Y=0 at BOTTOM, Y increases UP. LOW Y = visual BOTTOM.
        // Card centerline aligned with table's bottom border.
        // Table bottom edge = h * 0.25f (panel-local)
        // Card center = h * 0.25f, Card bottom = h * 0.25f - CARD_HEIGHT/2
        // ----------------------------------------------------------------------
        float playerCardBottomY = h * 0.25f - CARD_HEIGHT / 2f;
        renderPlayerHand(cx, y + playerCardBottomY, state.playerHand, alphaMult);
        
        // ----------------------------------------------------------------------
        // 6. Opponent hand (TOP area - opponent sits at top) - face down unless showdown
        // OpenGL: Y=0 at BOTTOM, Y increases UP. HIGH Y = visual TOP.
        // Card centerline aligned with table's top border.
        // Table top edge = h * 0.75f (panel-local)
        // Card center = h * 0.75f, Card bottom = h * 0.75f - CARD_HEIGHT/2
        // ----------------------------------------------------------------------
        float opponentCardBottomY = h * 0.75f - CARD_HEIGHT / 2f;
        boolean showOpponentCards = state.round == PokerGame.Round.SHOWDOWN && state.folder == null;
        renderOpponentHand(cx, y + opponentCardBottomY, 
            state.opponentHand, showOpponentCards, alphaMult);
        
        // ----------------------------------------------------------------------
        // 7-10. Stack displays, bet indicators, round, waiting - all handled by LabelAPI
        // See createStackDisplays(), createBetLabels(), createRoundLabel(), createWaitingLabel()
        // ----------------------------------------------------------------------
        
        // Disable scissor test after all rendering
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
    
    /**
     * Render overlay elements - effects, animations.
     */
    public void render(float alphaMult) {
        // Reserved for future animations/effects
    }
    
    // ============================================================================
    // RENDERING HELPERS
    // ============================================================================
    
    /**
     * Renders the poker table as an oval shape with wood border.
     */
    protected void renderPokerTable(float cx, float cy, float width, float height, float alphaMult) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        
        // Table felt (inner oval approximation with quad)
        float innerW = width - 20;
        float innerH = height - 20;
        Misc.renderQuad(cx - innerW/2, cy - innerH/2, innerW, innerH, COLOR_TABLE, alphaMult * 0.9f);
        
        Color borderColor = COLOR_TABLE_BORDER;
        
        // Draw border lines
        float borderThickness = 10f;
        // Top border
        Misc.renderQuad(cx - width/2, cy - height/2 - borderThickness, 
            width, borderThickness, borderColor, alphaMult);
        // Bottom border
        Misc.renderQuad(cx - width/2, cy + height/2, 
            width, borderThickness, borderColor, alphaMult);
        // Left border
        Misc.renderQuad(cx - width/2 - borderThickness, cy - height/2, 
            borderThickness, height, borderColor, alphaMult);
        // Right border
        Misc.renderQuad(cx + width/2, cy - height/2, 
            borderThickness, height, borderColor, alphaMult);
    }
    
    /**
     * Renders community cards in a row at center of table.
     */
protected void renderCommunityCards(float cx, float cy, 
            List<PokerGame.PokerGameLogic.Card> cards, float alphaMult) {
        int numCards = cards.size();
        float totalWidth = numCards * CARD_WIDTH + (numCards - 1) * CARD_SPACING;
        float startX = cx - totalWidth / 2f;
        
        for (int i = 0; i < numCards; i++) {
            PokerGame.PokerGameLogic.Card card = cards.get(i);
            float cardX = startX + i * (CARD_WIDTH + CARD_SPACING);
            renderCardAnimated(cardX, cy - CARD_HEIGHT/2, card, communityCardAnimations[i], alphaMult);
        }
    }
    
    /**
     * Renders player's hand at bottom of table.
     */
    protected void renderPlayerHand(float cx, float y, 
            List<PokerGame.PokerGameLogic.Card> cards, float alphaMult) {
        if (cards == null || cards.isEmpty()) return;
        
        float totalWidth = 2 * CARD_WIDTH + CARD_SPACING;
        float startX = cx - totalWidth / 2f;
        
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        
        for (int i = 0; i < cards.size(); i++) {
            PokerGame.PokerGameLogic.Card card = cards.get(i);
            float cardX = startX + i * (CARD_WIDTH + CARD_SPACING);
            renderCardAnimated(cardX, y, card, playerCardAnimations[i], alphaMult);
        }
        
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }
    
    /**
     * Renders opponent's hand at top of table.
     * @param showCards If true, cards are face up (showdown); otherwise face down.
     */
    protected void renderOpponentHand(float cx, float y, 
            List<PokerGame.PokerGameLogic.Card> cards, boolean showCards, float alphaMult) {
        if (cards == null || cards.isEmpty()) return;
        
        float totalWidth = 2 * CARD_WIDTH + CARD_SPACING;
        float startX = cx - totalWidth / 2f;
        
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        
        for (int i = 0; i < cards.size(); i++) {
            PokerGame.PokerGameLogic.Card card = cards.get(i);
            float cardX = startX + i * (CARD_WIDTH + CARD_SPACING);
            if (showCards) {
                renderCardAnimated(cardX, y, card, opponentCardAnimations[i], alphaMult);
            } else {
                renderCardFaceDown(cardX, y, alphaMult);
            }
        }
        
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }
    
    /**
     * Renders a single card face up (optimized for batching).
     */
    protected void renderCardFaceUp(float x, float y, PokerGame.PokerGameLogic.Card card, float alphaMult) {
        Misc.renderQuad(x + 3, y - 3, CARD_WIDTH, CARD_HEIGHT, COLOR_CARD_SHADOW, alphaMult * 0.6f);
        Misc.renderQuad(x, y, CARD_WIDTH, CARD_HEIGHT, COLOR_CARD_FRONT, alphaMult);
        
        GL11.glLineWidth(2f);
        GL11.glColor4f(0.2f, 0.2f, 0.2f, alphaMult);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + CARD_WIDTH, y);
        GL11.glVertex2f(x + CARD_WIDTH, y + CARD_HEIGHT);
        GL11.glVertex2f(x, y + CARD_HEIGHT);
        GL11.glEnd();
        GL11.glLineWidth(1f);
        
        if (card != null) {
            float centerX = x + CARD_WIDTH / 2f;
            float centerY = y + CARD_HEIGHT / 2f;
            float symbolSize = 20f;
            
            float[] suitColor;
            switch (card.suit) {
                case DIAMONDS: suitColor = GL_COLOR_DIAMONDS; break;
                case HEARTS: suitColor = GL_COLOR_HEARTS; break;
                case SPADES: suitColor = GL_COLOR_SPADES; break;
                default: suitColor = GL_COLOR_CLUBS; break;
            }
            GL11.glColor4f(suitColor[0], suitColor[1], suitColor[2], alphaMult);
            
            switch (card.suit) {
                case DIAMONDS: renderDiamond(centerX, centerY, symbolSize); break;
                case HEARTS: renderHeart(centerX, centerY, symbolSize); break;
                case SPADES: renderSpade(centerX, centerY, symbolSize); break;
                default: renderClub(centerX, centerY, symbolSize); break;
            }
        }
    }
    
    /**
     * Renders a single card face down (optimized for batching).
     */
    protected void renderCardFaceDown(float x, float y, float alphaMult) {
        Misc.renderQuad(x + 3, y - 3, CARD_WIDTH, CARD_HEIGHT, COLOR_CARD_SHADOW, alphaMult * 0.6f);
        Misc.renderQuad(x, y, CARD_WIDTH, CARD_HEIGHT, COLOR_CARD_BACK, alphaMult);
        
        GL11.glColor4f(GL_COLOR_CARD_BACK[0] + 0.1f, GL_COLOR_CARD_BACK[1] + 0.1f, GL_COLOR_CARD_BACK[2] + 0.2f, alphaMult);
        
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2f(x + 4, y + 4);
        GL11.glVertex2f(x + CARD_WIDTH - 4, y + 4);
        GL11.glVertex2f(x + CARD_WIDTH - 4, y + CARD_HEIGHT - 4);
        GL11.glVertex2f(x + 4, y + CARD_HEIGHT - 4);
        GL11.glEnd();
        
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x + 10, y + 10);
        GL11.glVertex2f(x + CARD_WIDTH - 10, y + CARD_HEIGHT - 10);
        GL11.glVertex2f(x + CARD_WIDTH - 10, y + 10);
        GL11.glVertex2f(x + 10, y + CARD_HEIGHT - 10);
        GL11.glEnd();
    }
    
    /**
     * Renders a card with flip animation support.
     * Uses GL11 scale transform to simulate 3D card flip from top-down perspective.
     */
    protected void renderCardAnimated(float x, float y, PokerGame.PokerGameLogic.Card card,
            CardFlipAnimation anim, float alphaMult) {
        
        if (anim == null) {
            renderCardFaceUp(x, y, card, alphaMult);
            return;
        }
        
        if (anim.phase == CardFlipAnimation.Phase.HIDDEN) {
            renderCardFaceDown(x, y, alphaMult);
            return;
        }
        
        if (anim.phase == CardFlipAnimation.Phase.REVEALED) {
            renderCardFaceUp(x, y, card, alphaMult);
            return;
        }
        
        float widthScale = anim.getWidthScale();
        float cardCenterX = x + CARD_WIDTH / 2f;
        
        GL11.glPushMatrix();
        GL11.glTranslatef(cardCenterX, y, 0);
        GL11.glScalef(widthScale, 1f, 1f);
        GL11.glTranslatef(-cardCenterX, -y, 0);
        
        if (anim.shouldShowBack()) {
            renderCardFaceDown(x, y, alphaMult);
        } else {
            renderCardFaceUp(x, y, card, alphaMult);
        }
        
        GL11.glPopMatrix();
    }
    
    /**
     * Renders a single card at the specified position.
     * @param faceUp If true, shows card face; otherwise shows card back.
     */
    protected void renderCard(float x, float y, PokerGame.PokerGameLogic.Card card, 
            boolean faceUp, float alphaMult) {
        Misc.renderQuad(x + 3, y - 3, CARD_WIDTH, CARD_HEIGHT, 
            COLOR_CARD_SHADOW, alphaMult * 0.6f);
        
        if (faceUp && card != null) {
            Misc.renderQuad(x, y, CARD_WIDTH, CARD_HEIGHT, COLOR_CARD_FRONT, alphaMult);
            
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glLineWidth(2f);
            GL11.glColor4f(0.2f, 0.2f, 0.2f, alphaMult);
            GL11.glBegin(GL11.GL_LINE_LOOP);
            GL11.glVertex2f(x, y);
            GL11.glVertex2f(x + CARD_WIDTH, y);
            GL11.glVertex2f(x + CARD_WIDTH, y + CARD_HEIGHT);
            GL11.glVertex2f(x, y + CARD_HEIGHT);
            GL11.glEnd();
            GL11.glLineWidth(1f);
            
            float centerX = x + CARD_WIDTH / 2f;
            float centerY = y + CARD_HEIGHT / 2f;
            float symbolSize = 20f;
            
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            
            float[] suitColor;
            switch (card.suit) {
                case DIAMONDS:
                    suitColor = GL_COLOR_DIAMONDS;
                    break;
                case HEARTS:
                    suitColor = GL_COLOR_HEARTS;
                    break;
                case SPADES:
                    suitColor = GL_COLOR_SPADES;
                    break;
                case CLUBS:
                default:
                    suitColor = GL_COLOR_CLUBS;
                    break;
            }
            GL11.glColor4f(suitColor[0], suitColor[1], suitColor[2], alphaMult);
            
            switch (card.suit) {
                case DIAMONDS:
                    renderDiamond(centerX, centerY, symbolSize);
                    break;
                case HEARTS:
                    renderHeart(centerX, centerY, symbolSize);
                    break;
                case SPADES:
                    renderSpade(centerX, centerY, symbolSize);
                    break;
                case CLUBS:
                default:
                    renderClub(centerX, centerY, symbolSize);
                    break;
            }
            
            GL11.glColor4f(1f, 1f, 1f, 1f);
            
        } else {
            Misc.renderQuad(x, y, CARD_WIDTH, CARD_HEIGHT, COLOR_CARD_BACK, alphaMult);
            
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(GL_COLOR_CARD_BACK[0] + 0.1f, GL_COLOR_CARD_BACK[1] + 0.1f, GL_COLOR_CARD_BACK[2] + 0.2f, alphaMult);
            
            GL11.glBegin(GL11.GL_LINE_LOOP);
            GL11.glVertex2f(x + 4, y + 4);
            GL11.glVertex2f(x + CARD_WIDTH - 4, y + 4);
            GL11.glVertex2f(x + CARD_WIDTH - 4, y + CARD_HEIGHT - 4);
            GL11.glVertex2f(x + 4, y + CARD_HEIGHT - 4);
            GL11.glEnd();
            
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex2f(x + 10, y + 10);
            GL11.glVertex2f(x + CARD_WIDTH - 10, y + CARD_HEIGHT - 10);
            GL11.glVertex2f(x + CARD_WIDTH - 10, y + 10);
            GL11.glVertex2f(x + 10, y + CARD_HEIGHT - 10);
            GL11.glEnd();
            
            GL11.glColor4f(1f, 1f, 1f, 1f);
        }
    }
    
    /**
     * Renders a diamond suit shape - simple 4-point diamond.
     */
    protected void renderDiamond(float cx, float cy, float size) {
        GL11.glBegin(GL11.GL_POLYGON);
        GL11.glVertex2f(cx, cy + size);           // Top (low Y = high on screen)
        GL11.glVertex2f(cx + size * 0.6f, cy);    // Right
        GL11.glVertex2f(cx, cy - size);           // Bottom (high Y = low on screen)
        GL11.glVertex2f(cx - size * 0.6f, cy);    // Left
        GL11.glEnd();
    }
    
    /**
     * Renders a heart suit shape using semicircles for bumps.
     * OpenGL coordinate system: Y=0 at BOTTOM, Y increases UP.
     * HIGH Y = visual TOP, LOW Y = visual BOTTOM.
     * Heart = bumps at visual TOP (HIGH Y), point at visual BOTTOM (LOW Y).
     * 
     * Geometry: Triangle width = 1.2 * size, bump radius = triangle_width / 4
     * Bump centers at 1/4 and 3/4 points of triangle bottom.
     * Uses TOP semicircles (bulging upward) for the bumps.
     */
    protected void renderHeart(float cx, float cy, float size) {
        float triangleWidth = size * 1.2f;
        float bumpRadius = triangleWidth * 0.25f;
        int segments = 20;
        
        float leftBumpCenter = cx - triangleWidth * 0.25f;
        float rightBumpCenter = cx + triangleWidth * 0.25f;
        float bumpY = cy + size * 0.25f;
        
        drawTopSemiCircle(leftBumpCenter, bumpY, bumpRadius, segments);
        drawTopSemiCircle(rightBumpCenter, bumpY, bumpRadius, segments);
        
        GL11.glBegin(GL11.GL_POLYGON);
        GL11.glVertex2f(cx - triangleWidth * 0.5f, bumpY);
        GL11.glVertex2f(cx + triangleWidth * 0.5f, bumpY);
        GL11.glVertex2f(cx, cy - size * 0.9f);
        GL11.glEnd();
    }
    
    /**
     * Renders a spade suit shape using semicircles for bumps.
     * OpenGL coordinate system: Y=0 at BOTTOM, Y increases UP.
     * HIGH Y = visual TOP, LOW Y = visual BOTTOM.
     * Spade = point at visual TOP (HIGH Y), bumps at visual BOTTOM (LOW Y), stem below.
     * 
     * Geometry: Triangle width = 1.2 * size, bump radius = triangle_width / 4
     * Bump centers at 1/4 and 3/4 points of triangle bottom.
     * Uses BOTTOM semicircles (bulging downward) for the bumps.
     */
    protected void renderSpade(float cx, float cy, float size) {
        float triangleWidth = size * 1.2f;
        float bumpRadius = triangleWidth * 0.25f;
        int segments = 20;
        
        float leftBumpCenter = cx - triangleWidth * 0.25f;
        float rightBumpCenter = cx + triangleWidth * 0.25f;
        float bumpY = cy - size * 0.1f;
        
        GL11.glBegin(GL11.GL_POLYGON);
        GL11.glVertex2f(cx - triangleWidth * 0.5f, bumpY);
        GL11.glVertex2f(cx + triangleWidth * 0.5f, bumpY);
        GL11.glVertex2f(cx, cy + size * 0.9f);
        GL11.glEnd();
        
        drawBottomSemiCircle(leftBumpCenter, bumpY, bumpRadius, segments);
        drawBottomSemiCircle(rightBumpCenter, bumpY, bumpRadius, segments);
        
        float stemTopY = bumpY - bumpRadius * 0.7f;
        GL11.glBegin(GL11.GL_POLYGON);
        GL11.glVertex2f(cx - size * 0.15f, stemTopY);
        GL11.glVertex2f(cx + size * 0.15f, stemTopY);
        GL11.glVertex2f(cx + size * 0.2f, cy - size * 0.9f);
        GL11.glVertex2f(cx - size * 0.2f, cy - size * 0.9f);
        GL11.glEnd();
    }
    
    /**
     * Draws a filled semicircle.
     * @param cx Center X
     * @param cy Center Y
     * @param radius Radius
     * @param segments Number of segments
     * @param leftHalf If true, draws left half; otherwise draws right half
     */
    protected void drawSemiCircle(float cx, float cy, float radius, int segments, boolean leftHalf) {
        GL11.glBegin(GL11.GL_POLYGON);
        GL11.glVertex2f(cx, cy);
        int startAngle = leftHalf ? (segments / 2) : 0;
        int endAngle = leftHalf ? segments : (segments / 2);
        for (int i = startAngle; i <= endAngle; i++) {
            float angle = (float) (Math.PI * i / segments);
            if (leftHalf) {
                angle += (float) Math.PI;
            }
            GL11.glVertex2f(cx + (float) Math.cos(angle) * radius, 
                           cy + (float) Math.sin(angle) * radius);
        }
        GL11.glEnd();
    }
    
    /**
     * Draws a TOP semicircle (bulging upward).
     * Angle range: 0 to PI (0° to 180°), where sin > 0 for upward.
     * @param cx Center X
     * @param cy Center Y (the flat bottom edge of the semicircle)
     * @param radius Radius
     * @param segments Number of segments for the arc
     */
    protected void drawTopSemiCircle(float cx, float cy, float radius, int segments) {
        GL11.glBegin(GL11.GL_POLYGON);
        GL11.glVertex2f(cx, cy);
        for (int i = 0; i <= segments; i++) {
            float angle = (float) (Math.PI * i / segments);
            GL11.glVertex2f(cx + (float) Math.cos(angle) * radius, 
                           cy + (float) Math.sin(angle) * radius);
        }
        GL11.glEnd();
    }
    
    /**
     * Draws a BOTTOM semicircle (bulging downward).
     * Angle range: PI to 2*PI (180° to 360°), where sin < 0 for downward.
     * @param cx Center X
     * @param cy Center Y (the flat top edge of the semicircle)
     * @param radius Radius
     * @param segments Number of segments for the arc
     */
    protected void drawBottomSemiCircle(float cx, float cy, float radius, int segments) {
        GL11.glBegin(GL11.GL_POLYGON);
        GL11.glVertex2f(cx, cy);
        for (int i = 0; i <= segments; i++) {
            float angle = (float) (Math.PI + Math.PI * i / segments);
            GL11.glVertex2f(cx + (float) Math.cos(angle) * radius, 
                           cy + (float) Math.sin(angle) * radius);
        }
        GL11.glEnd();
    }
    
    /**
     * Renders a club suit shape - three circles in clover pattern with stem.
     */
    protected void renderClub(float cx, float cy, float size) {
        float circleRadius = size * 0.35f;
        int segments = 20;
        
        float topCircleY = cy - size * 0.15f;
        float stemTopY = topCircleY - circleRadius * 0.6f;
        
        GL11.glBegin(GL11.GL_POLYGON);
        GL11.glVertex2f(cx - size * 0.12f, stemTopY);
        GL11.glVertex2f(cx + size * 0.12f, stemTopY);
        GL11.glVertex2f(cx + size * 0.22f, cy - size * 0.9f);
        GL11.glVertex2f(cx - size * 0.22f, cy - size * 0.9f);
        GL11.glEnd();
        
        drawCircle(cx, cy + size * 0.4f, circleRadius, segments);
        
        drawCircle(cx - size * 0.32f, topCircleY, circleRadius, segments);
        
        drawCircle(cx + size * 0.32f, topCircleY, circleRadius, segments);
    }
    
    /**
     * Draws a filled circle using polygon approximation.
     */
    protected void drawCircle(float cx, float cy, float radius, int segments) {
        GL11.glBegin(GL11.GL_POLYGON);
        for (int i = 0; i < segments; i++) {
            float angle = (float) (2 * Math.PI * i / segments);
            GL11.glVertex2f(cx + (float) Math.cos(angle) * radius, 
                           cy + (float) Math.sin(angle) * radius);
        }
        GL11.glEnd();
    }
    
    /**
     * Renders rank as a visual indicator (colored shape) in the corner.
     * Face cards get distinctive shapes, number cards get dots.
     */
    protected void renderRankIndicator(float x, float y, PokerGame.PokerGameLogic.Rank rank, 
            Color suitColor, float alphaMult) {
        float size = 14f;
        
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        
        // Background
        GL11.glColor4f(0.15f, 0.15f, 0.15f, alphaMult);
        GL11.glBegin(GL11.GL_POLYGON);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + size, y);
        GL11.glVertex2f(x + size, y + size);
        GL11.glVertex2f(x, y + size);
        GL11.glEnd();
        
        // Suit-colored indicator based on rank type
        GL11.glColor4f(suitColor.getRed()/255f, suitColor.getGreen()/255f, 
            suitColor.getBlue()/255f, alphaMult);
        
        int rankValue = rank.ordinal(); // 0=Two, 12=Ace
        
        if (rankValue >= 10) {
            // Face cards (J, Q, K, A) - gold border with suit color center
            GL11.glColor4f(1f, 0.85f, 0f, alphaMult); // Gold
            GL11.glBegin(GL11.GL_LINE_LOOP);
            GL11.glVertex2f(x + 1, y + 1);
            GL11.glVertex2f(x + size - 1, y + 1);
            GL11.glVertex2f(x + size - 1, y + size - 1);
            GL11.glVertex2f(x + 1, y + size - 1);
            GL11.glEnd();
            
            // Suit color center
            GL11.glColor4f(suitColor.getRed()/255f, suitColor.getGreen()/255f, 
                suitColor.getBlue()/255f, alphaMult);
            GL11.glBegin(GL11.GL_POLYGON);
            GL11.glVertex2f(x + 3, y + 3);
            GL11.glVertex2f(x + size - 3, y + 3);
            GL11.glVertex2f(x + size - 3, y + size - 3);
            GL11.glVertex2f(x + 3, y + size - 3);
            GL11.glEnd();
            
            // White center dot for distinction
            GL11.glColor4f(1f, 1f, 1f, alphaMult);
            drawCircle(x + size/2, y + size/2, 2f, 8);
        } else {
            // Number cards - dots indicating value
            int dots = Math.min(rankValue + 2, 10); // 2-10
            drawRankDots(x + size/2, y + size/2, dots, 3f);
        }
        
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }
    
    /**
     * Draws dots to indicate card rank value.
     */
    protected void drawRankDots(float cx, float cy, int count, float radius) {
        if (count <= 4) {
            // Single row
            float startX = cx - (count - 1) * radius * 0.8f;
            for (int i = 0; i < count; i++) {
                drawCircle(startX + i * radius * 1.6f, cy, radius * 0.4f, 8);
            }
        } else {
            // Two rows
            int topRow = count / 2;
            int bottomRow = count - topRow;
            float startY = cy - radius * 0.6f;
            
            float startX = cx - (topRow - 1) * radius * 0.6f;
            for (int i = 0; i < topRow; i++) {
                drawCircle(startX + i * radius * 1.2f, startY, radius * 0.35f, 8);
            }
            
            startX = cx - (bottomRow - 1) * radius * 0.6f;
            for (int i = 0; i < bottomRow; i++) {
                drawCircle(startX + i * radius * 1.2f, startY + radius * 1.2f, radius * 0.35f, 8);
            }
        }
    }
    
    /**
     * Gets a color representing the card rank for visual indication.
     */
    protected Color getRankColor(PokerGame.PokerGameLogic.Rank rank) {
        return switch (rank) {
            case ACE -> COLOR_RANK_ACE;
            case KING -> COLOR_RANK_KING;
            case QUEEN -> COLOR_RANK_QUEEN;
            case JACK -> COLOR_RANK_JACK;
            case TEN -> COLOR_RANK_TEN;
            default -> COLOR_RANK_LOW;
        };
    }
    
    protected Color getSuitColor(PokerGame.PokerGameLogic.Suit suit) {
        return switch (suit) {
            case SPADES -> COLOR_SPADES;
            case HEARTS -> COLOR_HEARTS;
            case DIAMONDS -> COLOR_DIAMONDS;
            case CLUBS -> COLOR_CLUBS;
        };
    }
    
    /**
     * Gets the display string for a card rank.
     */
    protected String getRankString(PokerGame.PokerGameLogic.Rank rank) {
        return switch (rank) {
            case ACE -> "A";
            case KING -> "K";
            case QUEEN -> "Q";
            case JACK -> "J";
            case TEN -> "10";
            case NINE -> "9";
            case EIGHT -> "8";
            case SEVEN -> "7";
            case SIX -> "6";
            case FIVE -> "5";
            case FOUR -> "4";
            case THREE -> "3";
            case TWO -> "2";
        };
    }
    
    /**
     * Gets the display character for a suit.
     */
    protected String getSuitString(PokerGame.PokerGameLogic.Suit suit) {
        return switch (suit) {
            case SPADES -> "S";
            case HEARTS -> "H";
            case DIAMONDS -> "D";
            case CLUBS -> "C";
        };
    }
    
    // ============================================================================
    // GAME STATE UPDATE
    // ============================================================================
    
    /**
     * Called by advance() to update game state.
     * Handles opponent turn processing with artificial delay.
     */
    public void advance(float amount) {
        if (p == null) return;
        
        // Handle opponent turn with delay
        if (waitingForOpponent) {
            opponentThinkTimer += amount;
            if (opponentThinkTimer >= OPPONENT_THINK_DELAY) {
                waitingForOpponent = false;
                opponentThinkTimer = 0f;
                // The actual opponent action is handled by PokerHandler
                // This just controls the visual delay
            }
        }
        
        // Advance all card animations
        for (int i = 0; i < 2; i++) {
            playerCardAnimations[i].advance(amount);
            opponentCardAnimations[i].advance(amount);
        }
        for (int i = 0; i < 5; i++) {
            communityCardAnimations[i].advance(amount);
        }
        
        // Note: Animation triggers are now called from updateGameState() 
        // when game state changes, not every frame
    }
    
    /**
     * Triggers opponent thinking animation.
     */
    public void startOpponentTurn() {
        waitingForOpponent = true;
        opponentThinkTimer = 0f;
    }
    
    /**
     * Updates the game reference without recreating buttons.
     * This is used for smooth in-place updates during gameplay.
     */
public void updateGameState(PokerGame game) {
        this.game = game;

        PokerGame.PokerState state = game.getState();

        // CAPTURE old state BEFORE any modifications - critical for animation detection
        PokerGame.Round previousRound = lastAnimatedRound;
        int previousCommunityCount = lastAnimatedCommunityCount;

        // Check if this is a new hand (transition to PREFLOP from non-PREFLOP)
        if (state.round == PokerGame.Round.PREFLOP && previousRound != PokerGame.Round.PREFLOP) {
            resetCardAnimations();
            // Re-capture after reset
            previousRound = lastAnimatedRound;
            previousCommunityCount = lastAnimatedCommunityCount;
        }

        waitingForOpponent = false;
        opponentThinkTimer = 0f;

        resultCached = false;
        cachedPlayerScore = null;
        cachedOpponentScore = null;
        cachedResultText = null;
        cachedResultColor = null;

        lastCardUpdateRound = null;
        lastPlayerHandSize = -1;
        lastOpponentHandSize = -1;
        lastCommunityCardsSize = -1;
        for (int i = 0; i < 2; i++) {
            lastPlayerCardRanks[i] = null;
            lastOpponentCardRanks[i] = null;
        }
        for (int i = 0; i < 5; i++) {
            lastCommunityCardRanks[i] = null;
        }

        // Calculate current values but DON'T update tracking variables yet
        int currentCommunityCount = state.communityCards != null ? state.communityCards.size() : 0;

        // Reset community card animation tracking if cards are cleared (new hand)
        if (lastAnimatedCommunityCount > 0 && state.communityCards != null && state.communityCards.isEmpty()) {
            lastAnimatedCommunityCount = 0;
            previousCommunityCount = 0;
        }

        // Check for new animations using CAPTURED old values
        // This must happen BEFORE we update tracking variables
        checkAndTriggerAnimations(state, previousRound, previousCommunityCount);

        // NOW update tracking variables AFTER animation check
        lastAnimatedRound = state.round;
        lastAnimatedCommunityCount = currentCommunityCount;

        // DON'T recreate buttons - they persist across state changes
        // Button visibility is updated in updateButtonVisibility() called from advance()
    }
    
    // ============================================================================
    // INPUT HANDLING
    // ============================================================================
    
    /**
     * Processes input events for button clicks and keyboard shortcuts.
     * 
     * INPUT PRIORITY:
     * 1. Escape key - dismiss dialog
     * 2. Button clicks - check each button's isChecked()
     * 3. Keyboard shortcuts (F, C, R) for quick actions
     */
    public void processInput(List<InputEventAPI> events) {
        // Don't skip if position is null - buttons should still work
        // The panel UI system handles button positioning independently
        
        for (InputEventAPI event : events) {
            if (event.isConsumed()) continue;
            
            // Handle keyboard shortcuts
            if (event.isKeyDownEvent()) {
                int key = event.getEventValue();
                
                // Escape - dismiss dialog (dev mode or as fallback)
                if (key == Keyboard.KEY_ESCAPE) {
                    event.consume();
                    if (callbacks != null) {
                        callbacks.dismissDialog();
                    }
                    return;
                }
                
                // F - Fold
                if (key == Keyboard.KEY_F && foldButton != null) {
                    event.consume();
                    handleFoldClick();
                    return;
                }
                
                // C - Check/Call
                if (key == Keyboard.KEY_C && checkCallButton != null) {
                    event.consume();
                    handleCheckCallClick();
                    return;
                }
                
                // R - Raise (opens raise menu or does default raise)
                if (key == Keyboard.KEY_R) {
                    event.consume();
                    handleRaiseClick();
                    return;
                }
            }
        }
        
        // Handle button clicks - ALWAYS check, even if events list is empty
        // Buttons maintain their own checked state based on mouse interaction
        checkButtonClicks();
    }
    
    /**
     * Checks all buttons for click state and triggers appropriate actions.
     * Called every frame from processInput().
     */
    protected void checkButtonClicks() {
        // Fold button
        if (foldButton != null && foldButton.isChecked()) {
            foldButton.setChecked(false);
            handleFoldClick();
            return;
        }
        
        // Check/Call button
        if (checkCallButton != null && checkCallButton.isChecked()) {
            checkCallButton.setChecked(false);
            handleCheckCallClick();
            return;
        }
        
        // Raise option buttons (direct raise amounts)
        for (ButtonAPI btn : raiseOptionButtons) {
            if (btn.isChecked()) {
                btn.setChecked(false);
                // Extract raise amount from button ID
                String id = (String) btn.getCustomData();
                if (id != null && id.startsWith("poker_raise_")) {
                    try {
                        int amount = Integer.parseInt(id.substring("poker_raise_".length()));
                        handleRaiseAmountClick(amount);
                    } catch (NumberFormatException e) {
                    }
                }
                return;
            }
        }
        
        // Flip Table button
        if (flipTableButton != null && flipTableButton.isChecked()) {
            flipTableButton.setChecked(false);
            handleFlipTableClick();
            return;
        }
        
        // Suspend button
        if (suspendButton != null && suspendButton.isChecked()) {
            suspendButton.setChecked(false);
            handleSuspendClick();
            return;
        }
        
        // How to Play button
        if (howToPlayButton != null && howToPlayButton.isChecked()) {
            howToPlayButton.setChecked(false);
            handleHowToPlayClick();
            return;
        }
        
        // Back button
        if (backButton != null && backButton.isChecked()) {
            backButton.setChecked(false);
            handleBackClick();
            return;
        }
        
        // Next Hand button
        if (nextHandButton != null && nextHandButton.isChecked()) {
            nextHandButton.setChecked(false);
            handleNextHandClick();
            return;
        }
    }
    
    // ============================================================================
    // ACTION HANDLERS
    // ============================================================================
    
    protected void handleFoldClick() {
        showPlayerAction("You fold");
        if (actionCallback != null) {
            actionCallback.onPlayerAction(PokerGame.Action.FOLD, 0);
        }
    }
    
    protected void handleCheckCallClick() {
        PokerGame.PokerState state = game.getState();
        int callAmount = state.opponentBet - state.playerBet;
        
        if (callAmount > 0) {
            showPlayerAction("You call " + callAmount);
            if (actionCallback != null) {
                actionCallback.onPlayerAction(PokerGame.Action.CALL, 0);
            }
        } else {
            showPlayerAction("You check");
            if (actionCallback != null) {
                actionCallback.onPlayerAction(PokerGame.Action.CHECK, 0);
            }
        }
    }
    
    protected void handleRaiseClick() {
        // For now, just call with minimum raise
        // In a real implementation, this might open a raise dialog
        PokerGame.PokerState state = game.getState();
        int minRaise = state.bigBlind;
        handleRaiseAmountClick(minRaise);
    }
    
    protected void handleRaiseAmountClick(int amount) {
        showPlayerAction("You raise to " + amount);
        if (actionCallback != null) {
            actionCallback.onPlayerAction(PokerGame.Action.RAISE, amount);
        }
    }
    
    protected void handleBackClick() {
        showPlayerAction("You leave the table");
        if (actionCallback != null) {
            actionCallback.onBackToMenu();
        }
    }
    
    protected void handleNextHandClick() {
        PokerGame.PokerState state = game.getState();
        boolean canContinue = state.playerStack >= state.bigBlind && 
                              state.opponentStack >= state.bigBlind;
        
        if (canContinue) {
            if (actionCallback != null) {
                actionCallback.onNextHand();
            }
        } else {
            // Someone is bust - dismiss dialog to return to menu
            if (callbacks != null) {
                callbacks.dismissDialog();
            }
            if (actionCallback != null) {
                actionCallback.onBackToMenu();
            }
        }
    }
    
    protected void handleSuspendClick() {
        if (actionCallback != null) {
            actionCallback.onSuspend();
        }
    }
    
    protected void handleHowToPlayClick() {
        if (actionCallback != null) {
            actionCallback.onHowToPlay();
        }
    }
    
    protected void handleFlipTableClick() {
        if (actionCallback != null) {
            actionCallback.onFlipTable();
        }
    }
}
