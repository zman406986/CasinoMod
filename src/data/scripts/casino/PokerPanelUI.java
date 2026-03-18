package data.scripts.casino;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate.DialogCallbacks;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.ActionListenerDelegate;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Poker UI Panel - renders the poker table, cards, and handles player input.
 * ARCHITECTURE NOTES:
 * - Extends BaseCustomUIPanelPlugin (base game pattern, see DuelPanel, GachaAnimation)
 * - Uses CustomVisualDialogDelegate wrapper for integration with interaction dialogs
 * - Renders in renderBelow() for content, render() for overlays (following DuelPanel pattern)
 * - Uses GL11.glScissor() to clip all rendering to panel boundaries
 * - Button creation follows the panel->tooltip->button hierarchy pattern
 * KEY RENDERING ORDER:
 * 1. renderBelow(): Background -> Table -> Community Cards -> Player Cards -> UI Elements
 * 2. render(): Overlays, effects, animations (if any)
 * STATE MANAGEMENT:
 * - PokerHandler owns the game state (PokerGame instance)
 * - This panel is purely for rendering and input
 * - All game logic flows through PokerHandler -> PokerGame
 */
public class PokerPanelUI extends BaseCustomUIPanelPlugin
    implements ActionListenerDelegate {

    private static final SettingsAPI settings = Global.getSettings();

    // ============================================================================
    // BUTTON ID CONSTANTS
    // ============================================================================
    private static final String POKER_FOLD = "poker_fold";
    private static final String POKER_CHECK_CALL = "poker_check_call";
    private static final String POKER_RAISE_PREFIX = "poker_raise_";
    private static final String POKER_NEXT_HAND = "poker_next_hand";
    private static final String POKER_SUSPEND = "poker_suspend";
    private static final String POKER_HOW_TO_PLAY = "poker_how_to_play";
    private static final String POKER_FLIP_TABLE = "poker_flip_table";

    // ============================================================================
    // CORE REFERENCES
    // ============================================================================
    protected DialogCallbacks callbacks;
    protected CustomPanelAPI panel;
    protected PositionAPI p;
    
    // External callback when player makes an action
    protected PokerActionCallback actionCallback;
    
    // ============================================================================
    // UI STATE
    // ============================================================================
    protected PokerGame game;
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
                                   previousRound != PokerGame.Round.SHOWDOWN;
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
    // UI ELEMENTS - Panels for buttons with setOpacity, ButtonAPI for dynamic text
    // ============================================================================
    protected ButtonAPI flipTableButton;
    protected ButtonAPI checkCallButton;
    protected List<ButtonAPI> raiseOptionButtons = new ArrayList<>();
    protected List<CustomPanelAPI> raiseOptionPanels = new ArrayList<>();
    protected CustomPanelAPI flipTablePanel;
    protected CustomPanelAPI suspendPanel;
    protected CustomPanelAPI howToPlayPanel;
    protected CustomPanelAPI foldPanel;
    protected CustomPanelAPI checkCallPanel;
    protected CustomPanelAPI nextHandPanel;

    // Stack display labels
    protected LabelAPI playerStackLabel;
    protected LabelAPI opponentStackLabel;
    
    // Round indicator label
    protected LabelAPI roundLabel;
    
    // Waiting indicator label
    protected LabelAPI waitingLabel;
    
    // Opponent action display label
    protected LabelAPI opponentActionLabel;
    protected String lastOpponentAction = "";
    
    // Player action display label
    protected LabelAPI playerActionLabel;
    protected String lastPlayerAction = "";

    // Return from suspend message
    protected LabelAPI returnMessageLabel;
    
    // Card rank labels (for displaying actual text on cards)
    protected LabelAPI[] playerCardRankLabels = new LabelAPI[2];
    protected LabelAPI[] opponentCardRankLabels = new LabelAPI[2];
    protected LabelAPI[] communityCardRankLabels = new LabelAPI[5];
    
    // Result labels for showdown
    protected LabelAPI resultLabel;
    
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
    protected static final Color COLOR_CARD_SHADOW = new Color(0, 0, 0, 150);
    
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
     * INIT SEQUENCE:
     * 1. DialogDelegate.init() -> PokerPanelUI.init()
     * 2. We store panel reference and create buttons immediately
     * 3. positionChanged() may be called later (optional)
     */
    public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
        this.panel = panel;
        this.callbacks = callbacks;
        
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
     * BUTTON LAYOUT (player side - BOTTOM of panel only):
     * - Leave button: top-right corner (always visible)
     * - Action buttons (Fold/Check/Raise): bottom row
     * - Raise options: row above action buttons
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

        flipTablePanel = panel.createCustomPanel(BUTTON_WIDTH, BUTTON_HEIGHT, null);
        TooltipMakerAPI flipTooltip = flipTablePanel.createUIElement(BUTTON_WIDTH, BUTTON_HEIGHT, false);
        flipTooltip.setActionListenerDelegate(this);
        flipTableButton = flipTooltip.addButton(Strings.get("poker_panel.run_away"), POKER_FLIP_TABLE, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        flipTableButton.setQuickMode(true);
        flipTableButton.getPosition().inTL(0, 0);
        flipTablePanel.addUIElement(flipTooltip).inTL(0, 0);
        panel.addComponent(flipTablePanel).inTL(PANEL_WIDTH - BUTTON_WIDTH - MARGIN, MARGIN);

        suspendPanel = panel.createCustomPanel(BUTTON_WIDTH, BUTTON_HEIGHT, null);
        TooltipMakerAPI suspendTooltip = suspendPanel.createUIElement(BUTTON_WIDTH, BUTTON_HEIGHT, false);
        suspendTooltip.setActionListenerDelegate(this);
        ButtonAPI suspendBtn = suspendTooltip.addButton(Strings.get("poker_panel.wait"), POKER_SUSPEND, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        suspendBtn.setQuickMode(true);
        suspendBtn.getPosition().inTL(0, 0);
        suspendPanel.addUIElement(suspendTooltip).inTL(0, 0);
        panel.addComponent(suspendPanel).inTL(PANEL_WIDTH - BUTTON_WIDTH * 2 - BUTTON_SPACING - MARGIN, MARGIN);

        howToPlayPanel = panel.createCustomPanel(BUTTON_WIDTH, BUTTON_HEIGHT, null);
        TooltipMakerAPI helpTooltip = howToPlayPanel.createUIElement(BUTTON_WIDTH, BUTTON_HEIGHT, false);
        helpTooltip.setActionListenerDelegate(this);
        ButtonAPI helpBtn = helpTooltip.addButton(Strings.get("poker_panel.how_to_play"), POKER_HOW_TO_PLAY, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        helpBtn.setQuickMode(true);
        helpBtn.getPosition().inTL(0, 0);
        howToPlayPanel.addUIElement(helpTooltip).inTL(0, 0);
        panel.addComponent(howToPlayPanel).inTL(PANEL_WIDTH - BUTTON_WIDTH * 3 - BUTTON_SPACING * 2 - MARGIN, MARGIN);
        
        createPotDisplay();
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

        foldPanel = panel.createCustomPanel(BUTTON_WIDTH, BUTTON_HEIGHT, null);
        TooltipMakerAPI foldTooltip = foldPanel.createUIElement(BUTTON_WIDTH, BUTTON_HEIGHT, false);
        foldTooltip.setActionListenerDelegate(this);
        ButtonAPI foldBtn = foldTooltip.addButton(Strings.get("poker_panel.fold_btn"), POKER_FOLD, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        foldBtn.setQuickMode(true);
        foldBtn.getPosition().inTL(0, 0);
        foldPanel.addUIElement(foldTooltip).inTL(0, 0);
        panel.addComponent(foldPanel).inTL(0, 0);
        foldPanel.setOpacity(0f);
        
        checkCallPanel = panel.createCustomPanel(BUTTON_WIDTH, BUTTON_HEIGHT, null);
        TooltipMakerAPI checkCallTooltip = checkCallPanel.createUIElement(BUTTON_WIDTH, BUTTON_HEIGHT, false);
        checkCallTooltip.setActionListenerDelegate(this);
        checkCallButton = checkCallTooltip.addButton(Strings.get("poker_panel.check_btn"), POKER_CHECK_CALL, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        checkCallButton.setQuickMode(true);
        checkCallButton.getPosition().inTL(0, 0);
        checkCallPanel.addUIElement(checkCallTooltip).inTL(0, 0);
        panel.addComponent(checkCallPanel).inTL(0, 0);
        checkCallPanel.setOpacity(0f);
        
        for (int i = 0; i < 5; i++) {
            CustomPanelAPI raiseOptPanel = panel.createCustomPanel(RAISE_BUTTON_WIDTH, BUTTON_HEIGHT, null);
            TooltipMakerAPI raiseOptTooltip = raiseOptPanel.createUIElement(RAISE_BUTTON_WIDTH, BUTTON_HEIGHT, false);
            raiseOptTooltip.setActionListenerDelegate(this);
            ButtonAPI btn = raiseOptTooltip.addButton("", POKER_RAISE_PREFIX + "0", RAISE_BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
            btn.setQuickMode(true);
            btn.setCustomData(POKER_RAISE_PREFIX + "0");
            btn.getPosition().inTL(0, 0);
            raiseOptPanel.addUIElement(raiseOptTooltip).inTL(0, 0);
            panel.addComponent(raiseOptPanel).inTL(0, 0);
            raiseOptPanel.setOpacity(0f);
            raiseOptionPanels.add(raiseOptPanel);
            raiseOptionButtons.add(btn);
        }
        
        buttonsCreated = true;
    }
    
    protected void updateButtonVisibility() {
        if (panel == null || game == null) return;
        
        PokerGame.PokerState state = game.getState();
        if (state == null) return;
        
        boolean isShowdown = state.round == PokerGame.Round.SHOWDOWN;
        String flipTableLabel = isShowdown ? Strings.get("poker_panel.leave_btn") : Strings.get("poker_panel.run_away");
        flipTableButton.setText(flipTableLabel);
        
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
            foldPanel.setOpacity(1f);
            
            String checkCallLabel = callAmount > 0 ? Strings.format("poker_panel.call_btn", callAmount) : Strings.get("poker_panel.check_btn");
            checkCallButton.setText(checkCallLabel);
            float checkCallX = actionStartX + BUTTON_WIDTH + BUTTON_SPACING;
            checkCallPanel.getPosition().inTL(checkCallX, bottomY);
            checkCallPanel.setOpacity(1f);
        } else {
            foldPanel.setOpacity(0f);
            checkCallPanel.setOpacity(0f);
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
                    String btnId = POKER_RAISE_PREFIX + amt;
                    float btnX = raiseStartX + (RAISE_BUTTON_WIDTH + BUTTON_SPACING) * i;
                    
                    raiseOptionButtons.get(i).setText(label);
                    raiseOptionButtons.get(i).setCustomData(btnId);
                    raiseOptionPanels.get(i).getPosition().inTL(btnX, raiseOptionsY);
                    raiseOptionPanels.get(i).setOpacity(1f);
                } else {
                    raiseOptionPanels.get(i).setOpacity(0f);
                }
            }
        } else {
            for (CustomPanelAPI pnl : raiseOptionPanels) {
                pnl.setOpacity(0f);
            }
        }
    }
    
    protected float[] getRaiseOptions(PokerGame.PokerState state) {
        List<Float> options = new ArrayList<>();
        int pot = state.pot;
        int stack = state.playerStack;
        int bb = state.bigBlind;
        int opponentBet = state.opponentBet;
        
        int bbTotal = opponentBet + bb;
        if (bbTotal <= state.playerBet + stack) options.add((float) bbTotal);
        
        int halfPot = pot / 2;
        int halfPotTotal = opponentBet + halfPot;
        if (halfPot >= bb && halfPotTotal <= state.playerBet + stack && !options.contains((float) halfPotTotal)) {
            options.add((float) halfPotTotal);
        }
        
        int potTotal = opponentBet + pot;
        if (pot >= bb && potTotal <= state.playerBet + stack && !options.contains((float) potTotal)) {
            options.add((float) potTotal);
        }
        
        int twoPot = pot * 2;
        int twoPotTotal = opponentBet + twoPot;
        if (twoPot > pot && twoPotTotal <= state.playerBet + stack && !options.contains((float) twoPotTotal)) {
            options.add((float) twoPotTotal);
        }
        
        int allInTotal = state.playerBet + stack;
        if (stack > 0 && !options.contains((float) allInTotal)) {
            options.add((float) allInTotal);
        }
        
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
            label = Strings.get("poker_raise_labels.bb");
        } else if (pot > 0 && raisePortion == pot / 2) {
            label = Strings.get("poker_raise_labels.half_pot");
        } else if (raisePortion == pot) {
            label = Strings.get("poker_raise_labels.pot");
        } else if (pot > 0 && raisePortion == pot * 2) {
            label = Strings.get("poker_raise_labels.2x_pot");
        } else if (totalBet == playerBet + playerStack) {
            label = Strings.get("poker_raise_labels.all_in");
        } else {
            label = Strings.get("poker_raise_labels.raise");
        }
        
        float bbAmount = bigBlind > 0 ? (float) totalBet / bigBlind : 0;
        return Strings.format("poker_raise_labels.format", label, totalBet, bbAmount);
    }
    
    /**
     * Creates the pot display label - NOW DISABLED.
     * Pot info is now integrated into the round label.
     * This method is kept for compatibility but does nothing.
     */
    protected void createPotDisplay() {
        // Pot is now shown in the round label instead
        // This method is kept for compatibility but does nothing
    }
    
    /**
     * Creates stack display labels using settings.createLabel() directly.
     * Layout: Opponent at top (Y=140), Player at bottom (Y=PANEL_HEIGHT-150)
     * Shows: "Opponent Stack: 9900 | Opponent current bet: 100"
     */
    protected void createStackDisplays() {
        if (panel == null) return;
        
        final float STACK_LABEL_WIDTH = 450f;
        final float STACK_LABEL_HEIGHT = 25f;

        opponentStackLabel = settings.createLabel(Strings.format("poker_panel.opponent_stack_bet", 0, 0), Fonts.DEFAULT_SMALL);
        opponentStackLabel.setColor(COLOR_OPPONENT);
        opponentStackLabel.setAlignment(Alignment.LMID);
        panel.addComponent((UIComponentAPI) opponentStackLabel).inTL(MARGIN, 140f)
            .setSize(STACK_LABEL_WIDTH, STACK_LABEL_HEIGHT);

        playerStackLabel = settings.createLabel(Strings.format("poker_panel.player_stack_bet", 0, 0), Fonts.DEFAULT_SMALL);
        playerStackLabel.setColor(COLOR_PLAYER);
        playerStackLabel.setAlignment(Alignment.LMID);
        panel.addComponent((UIComponentAPI) playerStackLabel).inTL(MARGIN, PANEL_HEIGHT - 150f)
            .setSize(STACK_LABEL_WIDTH, STACK_LABEL_HEIGHT);
    }
    
    /**
     * Updates display bet tracking. Captures bet values before advanceRound() clears them.
     * Display bets show the amount each player has committed in the current betting round.
     * AI_AGENT_NOTE: The timing issue is that advanceRound() clears bets in the same tick
     * as the betting action, so UI never sees non-zero values after a completed round.
     * We solve this by persisting captured values until new betting action starts.
     * Updates stack display labels with stack and current bet.
     */
    protected void updateStackDisplays(int playerStack, int opponentStack, int playerBet, int opponentBet) {
        if (playerStackLabel != null) {
            if (playerStack != lastPlayerStack || playerBet != lastPlayerBet) {
                lastPlayerStack = playerStack;
                lastPlayerBet = playerBet;
                lastPlayerStackText = Strings.format("poker_panel.player_stack_bet", playerStack, playerBet);
            }
            playerStackLabel.setText(lastPlayerStackText);
        }
        if (opponentStackLabel != null) {
            if (opponentStack != lastOpponentStack || opponentBet != lastOpponentBet) {
                lastOpponentStack = opponentStack;
                lastOpponentBet = opponentBet;
                lastOpponentStackText = Strings.format("poker_panel.opponent_stack_bet", opponentStack, opponentBet);
            }
            opponentStackLabel.setText(lastOpponentStackText);
        }
    }

    /**
     * Creates round indicator label - positioned at center-top above table.
     * Displays: "Round Progress: Flop | Current Pot: 200 | Big Blind (minimal bet): 50"
     */
    protected void createRoundLabel() {
        if (panel == null) return;
        
        final float ROUND_LABEL_WIDTH = 600f;
        final float ROUND_LABEL_HEIGHT = 25f;
        
        float x = (PANEL_WIDTH - ROUND_LABEL_WIDTH) / 2f;
        float y = PANEL_HEIGHT * 0.40f;
        
        roundLabel = settings.createLabel(Strings.format("poker_panel.round_progress", Strings.get("poker_rounds.preflop"), 0, 50), Fonts.DEFAULT_SMALL);
        roundLabel.setColor(new Color(150, 200, 255));
        roundLabel.setAlignment(Alignment.MID);
        panel.addComponent((UIComponentAPI) roundLabel).inTL(x, y)
            .setSize(ROUND_LABEL_WIDTH, ROUND_LABEL_HEIGHT);
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
                case PREFLOP -> Strings.get("poker_rounds.preflop");
                case FLOP -> Strings.get("poker_rounds.flop");
                case TURN -> Strings.get("poker_rounds.turn");
                case RIVER -> Strings.get("poker_rounds.river");
                case SHOWDOWN -> Strings.get("poker_rounds.showdown");
            };
            
            lastRoundText = Strings.format("poker_panel.round_progress", roundName, pot, bigBlind);
            
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
        
        final float WAITING_LABEL_WIDTH = 200f;
        final float WAITING_LABEL_HEIGHT = 25f;
        
        float x = (PANEL_WIDTH - WAITING_LABEL_WIDTH) / 2f;
        float y = PANEL_HEIGHT * 0.68f;
        
        waitingLabel = settings.createLabel(Strings.get("poker_panel.opponent_thinking"), Fonts.DEFAULT_SMALL);
        waitingLabel.setColor(new Color(255, 200, 100));
        waitingLabel.setAlignment(Alignment.MID);
        panel.addComponent((UIComponentAPI) waitingLabel).inTL(x, y)
            .setSize(WAITING_LABEL_WIDTH, WAITING_LABEL_HEIGHT);
        waitingLabel.setOpacity(0f);
    }
    
    /**
     * Updates waiting indicator visibility.
     */
    protected void updateWaitingLabel(boolean waiting) {
        if (waitingLabel != null) {
            waitingLabel.setOpacity(waiting ? 1f : 0f);
        }
    }
    
    /**
     * Creates opponent action display label - below opponent stack info.
     */
    protected void createOpponentActionLabel() {
        if (panel == null) return;
        
        final float ACTION_LABEL_WIDTH = 250f;
        final float ACTION_LABEL_HEIGHT = 25f;
        
        opponentActionLabel = settings.createLabel("", Fonts.DEFAULT_SMALL);
        opponentActionLabel.setColor(COLOR_OPPONENT);
        opponentActionLabel.setAlignment(Alignment.LMID);
        panel.addComponent((UIComponentAPI) opponentActionLabel).inTL(160, 200)
            .setSize(ACTION_LABEL_WIDTH, ACTION_LABEL_HEIGHT);
        opponentActionLabel.setOpacity(0f);
    }
    
    /**
     * Creates player action display label - above player stack info.
     */
    protected void createPlayerActionLabel() {
        if (panel == null) return;
        
        final float ACTION_LABEL_WIDTH = 250f;
        final float ACTION_LABEL_HEIGHT = 25f;
        
        playerActionLabel = settings.createLabel("", Fonts.DEFAULT_SMALL);
        playerActionLabel.setColor(COLOR_PLAYER);
        playerActionLabel.setAlignment(Alignment.LMID);
        panel.addComponent((UIComponentAPI) playerActionLabel).inTL(160, 480)
            .setSize(ACTION_LABEL_WIDTH, ACTION_LABEL_HEIGHT);
        playerActionLabel.setOpacity(0f);
    }
    
    /**
     * Updates opponent action display.
     */
    public void showOpponentAction(String action) {
        lastOpponentAction = action;
        if (opponentActionLabel != null) {
            opponentActionLabel.setText(action);
            opponentActionLabel.setOpacity(1f);
        }
    }
    
    /**
     * Hides opponent action display.
     */
    public void hideOpponentAction() {
        if (opponentActionLabel != null) {
            opponentActionLabel.setOpacity(0f);
        }
    }
    
    /**
     * Updates player action display.
     */
    public void showPlayerAction(String action) {
        lastPlayerAction = action;
        if (playerActionLabel != null) {
            playerActionLabel.setText(action);
            playerActionLabel.setOpacity(1f);
        }
    }
    
    /**
     * Hides player action display.
     */
    public void hidePlayerAction() {
        if (playerActionLabel != null) {
            playerActionLabel.setOpacity(0f);
        }
    }
    
    /**
     * Creates AI personality display label - at top-right below buttons.
     */
    protected void createAIPersonalityLabel() {
        // AI personality display removed - no longer needed
    }
    
    /**
     * Creates return from suspend message label - center of panel.
     */
    protected void createReturnMessageLabel() {
        if (panel == null) return;
        
        final float RETURN_LABEL_WIDTH = 400f;
        final float RETURN_LABEL_HEIGHT = 25f;
        
        float x = (PANEL_WIDTH - RETURN_LABEL_WIDTH) / 2f;
        float y = PANEL_HEIGHT * 0.3f;
        
        returnMessageLabel = settings.createLabel("", Fonts.DEFAULT_SMALL);
        returnMessageLabel.setColor(new Color(200, 200, 100));
        returnMessageLabel.setAlignment(Alignment.MID);
        panel.addComponent((UIComponentAPI) returnMessageLabel).inTL(x, y)
            .setSize(RETURN_LABEL_WIDTH, RETURN_LABEL_HEIGHT);
        returnMessageLabel.setOpacity(0f);
    }
    
    /**
     * Shows a return from suspend message.
     */
    public void showReturnMessage(String message) {
        if (returnMessageLabel != null) {
            returnMessageLabel.setText(message);
            returnMessageLabel.setOpacity(1f);
        }
    }

    /**
     * Creates card rank labels for displaying actual text on cards.
     * These are positioned at card locations when cards are rendered.
     */
    protected void createCardRankLabels() {
        if (panel == null) return;
        
        final float CARD_RANK_WIDTH = 50f;
        final float CARD_RANK_HEIGHT = 20f;
        Color yellowHighlight = new Color(1f, 0.85f, 0f);
        
        for (int i = 0; i < 2; i++) {
            playerCardRankLabels[i] = settings.createLabel("??", Fonts.DEFAULT_SMALL);
            playerCardRankLabels[i].setColor(Color.WHITE);
            playerCardRankLabels[i].setAlignment(Alignment.MID);
            playerCardRankLabels[i].setHighlightColor(yellowHighlight);
            playerCardRankLabels[i].setHighlight(0, 2);
            panel.addComponent((UIComponentAPI) playerCardRankLabels[i]).inTL(-1000, -1000)
                .setSize(CARD_RANK_WIDTH, CARD_RANK_HEIGHT);
            playerCardRankLabels[i].setOpacity(0f);
        }
        
        for (int i = 0; i < 2; i++) {
            opponentCardRankLabels[i] = settings.createLabel("??", Fonts.DEFAULT_SMALL);
            opponentCardRankLabels[i].setColor(Color.WHITE);
            opponentCardRankLabels[i].setAlignment(Alignment.MID);
            opponentCardRankLabels[i].setHighlightColor(yellowHighlight);
            opponentCardRankLabels[i].setHighlight(0, 2);
            panel.addComponent((UIComponentAPI) opponentCardRankLabels[i]).inTL(-1000, -1000)
                .setSize(CARD_RANK_WIDTH, CARD_RANK_HEIGHT);
            opponentCardRankLabels[i].setOpacity(0f);
        }
        
        for (int i = 0; i < 5; i++) {
            communityCardRankLabels[i] = settings.createLabel("??", Fonts.DEFAULT_SMALL);
            communityCardRankLabels[i].setColor(Color.WHITE);
            communityCardRankLabels[i].setAlignment(Alignment.MID);
            communityCardRankLabels[i].setHighlightColor(yellowHighlight);
            communityCardRankLabels[i].setHighlight(0, 2);
            panel.addComponent((UIComponentAPI) communityCardRankLabels[i]).inTL(-1000, -1000)
                .setSize(CARD_RANK_WIDTH, CARD_RANK_HEIGHT);
            communityCardRankLabels[i].setOpacity(0f);
        }
    }
    
    /**
     * Updates card rank labels based on current game state.
     * OpenGL coordinate system: Y=0 at BOTTOM, Y increases UP.
     * Player cards at visual BOTTOM (LOW Y), Opponent cards at visual TOP (HIGH Y).
     */
    protected void updateCardRankLabels(float panelWidth, float panelHeight) {
        PokerGame.PokerState state = game.getState();
        
        int playerHandSize = state.playerHand != null ? state.playerHand.size() : 0;
        int opponentHandSize = state.opponentHand != null ? state.opponentHand.size() : 0;
        int communitySize = state.communityCards != null ? state.communityCards.size() : 0;
        boolean showOpponent = state.round == PokerGame.Round.SHOWDOWN;
        
        boolean needsUpdate = lastCardUpdateRound != state.round ||
            lastPlayerHandSize != playerHandSize ||
            lastOpponentHandSize != opponentHandSize ||
            lastCommunityCardsSize != communitySize;
        
        if (!needsUpdate) {
            for (int i = 0; i < playerHandSize && i < 2; i++) {
                PokerGame.PokerGameLogic.Card card = state.playerHand.get(i);
                String rankText = card != null ? getRankString(card.rank()) : "??";
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
                    String rankText = getRankString(card.rank());
                    lastPlayerCardRanks[i] = rankText;
                    Color color = getSuitColor(card.suit());
                    playerCardRankLabels[i].setText(rankText);
                    playerCardRankLabels[i].setColor(color);
                    playerCardRankLabels[i].setHighlight(0, rankText.length());
                    
                    float labelX = cardX + 10;
                    float labelY = panelHeight - playerCardBottomY - CARD_HEIGHT + 4;
                    playerCardRankLabels[i].getPosition().inTL(labelX, labelY);
                    playerCardRankLabels[i].setOpacity(1f);
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
                    String rankText = getRankString(card.rank());
                    lastOpponentCardRanks[i] = rankText;
                    Color color = getSuitColor(card.suit());
                    opponentCardRankLabels[i].setText(rankText);
                    opponentCardRankLabels[i].setColor(color);
                    opponentCardRankLabels[i].setHighlight(0, rankText.length());
                    
                    float labelX = cardX + 10;
                    float labelY = panelHeight - opponentCardBottomY - CARD_HEIGHT + 4;
                    opponentCardRankLabels[i].getPosition().inTL(labelX, labelY);
                    opponentCardRankLabels[i].setOpacity(1f);
                }
            }
        } else {
            for (int i = 0; i < 2; i++) {
                if (opponentCardRankLabels[i] != null) {
                    opponentCardRankLabels[i].setOpacity(0f);
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
                    String rankText = getRankString(card.rank());
                    lastCommunityCardRanks[i] = rankText;
                    Color color = getSuitColor(card.suit());
                    communityCardRankLabels[i].setText(rankText);
                    communityCardRankLabels[i].setColor(color);
                    communityCardRankLabels[i].setHighlight(0, rankText.length());
                    
                    float labelX = cardX + 10;
                    float labelY = panelHeight - commCardBottomY - CARD_HEIGHT + 4;
                    communityCardRankLabels[i].getPosition().inTL(labelX, labelY);
                    communityCardRankLabels[i].setOpacity(1f);
                }
            }
            
            for (int i = numCards; i < 5; i++) {
                if (communityCardRankLabels[i] != null) {
                    communityCardRankLabels[i].setOpacity(0f);
                }
                lastCommunityCardRanks[i] = null;
            }
        } else {
            for (int i = 0; i < 5; i++) {
                if (communityCardRankLabels[i] != null) {
                    communityCardRankLabels[i].setOpacity(0f);
                }
                lastCommunityCardRanks[i] = null;
            }
        }
    }

    /**
     * Creates the result display label for showdown - showing hand details.
     */
    protected void createResultLabel() {
        if (panel == null) return;
        
        final float RESULT_LABEL_WIDTH = 450f;
        final float RESULT_LABEL_HEIGHT = 25f;
        
        resultLabel = settings.createLabel("", Fonts.DEFAULT_SMALL);
        resultLabel.setColor(Color.WHITE);
        resultLabel.setAlignment(Alignment.MID);
        panel.addComponent((UIComponentAPI) resultLabel).inTL(PANEL_WIDTH / 2f - RESULT_LABEL_WIDTH / 2f, PANEL_HEIGHT * 0.32f)
            .setSize(RESULT_LABEL_WIDTH, RESULT_LABEL_HEIGHT);
        resultLabel.setOpacity(0f);
    }
    
    /**
     * Updates the result display with detailed hand information.
     */
    protected void updateResultLabel(PokerGame.PokerState state) {
        if (resultLabel == null || state == null) return;
        
        if (state.round != PokerGame.Round.SHOWDOWN) {
            resultLabel.setOpacity(0f);
            resultCached = false;
            cachedPlayerScore = null;
            cachedOpponentScore = null;
            return;
        }
        
        if (resultCached && cachedResultText != null) {
            resultLabel.setText(cachedResultText);
            resultLabel.setColor(cachedResultColor);
            resultLabel.setOpacity(1f);
            return;
        }
        
        String resultText = "";
        Color resultColor = Color.WHITE;
        int potWon = state.lastPotWon;
        
        if (state.folder != null) {
            if (state.folder == PokerGame.CurrentPlayer.PLAYER) {
                resultText = Strings.format("poker_panel.you_folded_win", potWon);
                resultColor = COLOR_OPPONENT;
            } else {
                resultText = Strings.format("poker_panel.opponent_folded_win", potWon);
                resultColor = COLOR_PLAYER;
            }
            hideOpponentAction();
            hidePlayerAction();
        } else if (state.playerHandRank != null && state.opponentHandRank != null) {
            cachedPlayerScore = PokerGame.PokerGameLogic.evaluate(state.playerHand, state.communityCards);
            cachedOpponentScore = PokerGame.PokerGameLogic.evaluate(state.opponentHand, state.communityCards);
            
            String playerHandDesc = formatHandDescription(cachedPlayerScore);
            String oppHandDesc = formatHandDescription(cachedOpponentScore);
            
            showPlayerAction("You: " + playerHandDesc);
            showOpponentAction("Opp: " + oppHandDesc);
            
            int cmp = cachedPlayerScore.compareTo(cachedOpponentScore);
            if (cmp > 0) {
                resultText = Strings.format("poker_panel.you_win_stargems", potWon);
                resultColor = Color.GREEN;
            } else if (cmp < 0) {
                resultText = Strings.format("poker_panel.opponent_wins_stargems", potWon);
                resultColor = COLOR_OPPONENT;
            } else {
                int playerShare = (potWon / 2) + (potWon % 2);
                resultText = Strings.format("poker_panel.split_pot_each", playerShare);
                resultColor = Color.YELLOW;
            }
        }
        
        boolean playerBust = state.playerStack < state.bigBlind;
        boolean opponentBust = state.opponentStack < state.bigBlind;
        
        if (playerBust || opponentBust) {
            String bustMessage = playerBust ? 
                Strings.get("poker_panel.you_bust_leave") : 
                Strings.get("poker_panel.opponent_bust_leave");
            resultText += bustMessage;
        }
        
        cachedResultText = resultText;
        cachedResultColor = resultColor;
        resultCached = true;
        
        resultLabel.setText(resultText);
        resultLabel.setColor(resultColor);
        resultLabel.setOpacity(1f);
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

        return switch (score.rank)
        {
            case HIGH_CARD -> Strings.format("poker_hand_desc.high_card", highCard);
            case PAIR -> Strings.format("poker_hand_desc.pair_of", highCard);
            case TWO_PAIR ->
            {
                if (score.tieBreakers.size() >= 2)
                {
                    String firstPair = getRankName(score.tieBreakers.get(0));
                    String secondPair = getRankName(score.tieBreakers.get(1));
                    yield Strings.format("poker_hand_desc.two_pair_and", firstPair, secondPair);
                }
                yield Strings.get("poker_hand_desc.two_pair");
            }
            case THREE_OF_A_KIND -> Strings.format("poker_hand_desc.three", highCard);
            case STRAIGHT -> Strings.format("poker_hand_desc.straight_high", highCard);
            case FLUSH -> Strings.format("poker_hand_desc.flush_high", highCard);
            case FULL_HOUSE ->
            {
                if (score.tieBreakers.size() >= 2)
                {
                    String trips = getRankName(score.tieBreakers.get(0));
                    String pair = getRankName(score.tieBreakers.get(1));
                    yield Strings.format("poker_hand_desc.full_house_full", trips, pair);
                }
                yield Strings.get("poker_hand_desc.full_house");
            }
            case FOUR_OF_A_KIND -> Strings.format("poker_hand_desc.four", highCard);
            case STRAIGHT_FLUSH ->
            {
                if (highCard.equals("Ace"))
                {
                    yield Strings.get("poker_hand_desc.royal_flush");
                }
                yield Strings.format("poker_hand_desc.straight_flush_high", highCard);
            }
        };
    }
    
    /**
     * Formats a hand rank enum into a readable string.
     */
    protected String formatHandRank(PokerGame.PokerGameLogic.HandRank rank) {
        if (rank == null) return "Unknown";
        return switch (rank)
        {
            case HIGH_CARD -> Strings.get("poker_hand_rank.high_card");
            case PAIR -> Strings.get("poker_hand_rank.pair");
            case TWO_PAIR -> Strings.get("poker_hand_rank.two_pair");
            case THREE_OF_A_KIND -> Strings.get("poker_hand_rank.three_of_a_kind");
            case STRAIGHT -> Strings.get("poker_hand_rank.straight");
            case FLUSH -> Strings.get("poker_hand_rank.flush");
            case FULL_HOUSE -> Strings.get("poker_hand_rank.full_house");
            case FOUR_OF_A_KIND -> Strings.get("poker_hand_rank.four_of_a_kind");
            case STRAIGHT_FLUSH -> Strings.get("poker_hand_rank.straight_flush");
        };
    }
    
    /**
     * Converts a numeric rank value to a card rank name.
     */
    protected String getRankName(int rankValue) {
        return switch (rankValue)
        {
            case 14 -> Strings.get("poker_ranks.ace");
            case 13 -> Strings.get("poker_ranks.king");
            case 12 -> Strings.get("poker_ranks.queen");
            case 11 -> Strings.get("poker_ranks.jack");
            case 10 -> "10";
            case 9 -> "9";
            case 8 -> "8";
            case 7 -> "7";
            case 6 -> "6";
            case 5 -> "5";
            case 4 -> "4";
            case 3 -> "3";
            case 2 -> "2";
            default -> String.valueOf(rankValue);
        };
    }

    /**
     * Creates the "Next Hand" button for after showdown - below community cards, right of result label.
     */
    protected void createNextHandButton() {
        if (panel == null) return;
        
        final float BUTTON_W = 120f;
        final float BUTTON_H = 30f;
        
        nextHandPanel = panel.createCustomPanel(BUTTON_W, BUTTON_H, null);
        TooltipMakerAPI tooltip = nextHandPanel.createUIElement(BUTTON_W, BUTTON_H, false);
        tooltip.setActionListenerDelegate(this);
        ButtonAPI btn = tooltip.addButton(Strings.get("poker_panel.next_hand_btn"), POKER_NEXT_HAND, BUTTON_W, BUTTON_H, 0f);
        btn.setQuickMode(true);
        btn.getPosition().inTL(0, 0);
        nextHandPanel.addUIElement(tooltip).inTL(0, 0);
        panel.addComponent(nextHandPanel).inTL(PANEL_WIDTH / 2f + 200f, PANEL_HEIGHT / 2f + CARD_HEIGHT / 2f + 70f);
        nextHandPanel.setOpacity(0f);
    }
    
    /**
     * Updates the "Next Hand" button visibility.
     */
    protected void updateNextHandButton(PokerGame.PokerState state) {
        if (nextHandPanel == null) return;
        
        boolean atShowdown = state.round == PokerGame.Round.SHOWDOWN;

        if (atShowdown) {
            nextHandPanel.setOpacity(1f);
        } else {
            nextHandPanel.setOpacity(0f);
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
        // Display bets are managed by PokerState and persist across round transitions
        // ----------------------------------------------------------------------
        updateStackDisplays(state.playerStack, state.opponentStack, state.displayPlayerBet, state.displayOpponentBet);
        updateRoundLabel(state.round, state.bigBlind, state.pot);
        updateWaitingLabel(waitingForOpponent);
        
        // ----------------------------------------------------------------------
        // 3c. Update card rank labels (actual text on cards)
        // ----------------------------------------------------------------------
        updateCardRankLabels(w, h);
        
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
            
            float[] suitColor = switch (card.suit())
            {
                case DIAMONDS -> GL_COLOR_DIAMONDS;
                case HEARTS -> GL_COLOR_HEARTS;
                case SPADES -> GL_COLOR_SPADES;
                default -> GL_COLOR_CLUBS;
            };
            GL11.glColor4f(suitColor[0], suitColor[1], suitColor[2], alphaMult);
            
            switch (card.suit()) {
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
     * Processes input events for keyboard shortcuts.
     * Button clicks are handled via ActionListenerDelegate.
     */
    public void processInput(List<InputEventAPI> events) {
        for (InputEventAPI event : events) {
            if (event.isConsumed()) continue;
            
            if (event.isKeyDownEvent()) {
                int key = event.getEventValue();
                
                if (key == Keyboard.KEY_ESCAPE) {
                    event.consume();
                    if (callbacks != null) {
                        callbacks.dismissDialog();
                    }
                    return;
                }
                
                if (key == Keyboard.KEY_F) {
                    event.consume();
                    handleFoldClick();
                    return;
                }
                
                if (key == Keyboard.KEY_C) {
                    event.consume();
                    handleCheckCallClick();
                    return;
                }
                
                if (key == Keyboard.KEY_R) {
                    event.consume();
                    handleRaiseClick();
                    return;
                }
            }
        }
    }
    
    @Override
    public void actionPerformed(Object input, Object source) {
        Object data = null;
        if (source instanceof ButtonAPI btn) {
            data = btn.getCustomData();
        }
        processAction(data);
        updateButtonVisibility();
    }
    
    protected void processAction(Object data) {
        if (data == null) return;
        
        String strData = data instanceof String ? (String) data : null;
        
        if (POKER_FOLD.equals(data)) {
            handleFoldClick();
            return;
        }
        if (POKER_CHECK_CALL.equals(data)) {
            handleCheckCallClick();
            return;
        }
        if (POKER_NEXT_HAND.equals(data)) {
            handleNextHandClick();
            return;
        }
        if (POKER_SUSPEND.equals(data)) {
            handleSuspendClick();
            return;
        }
        if (POKER_HOW_TO_PLAY.equals(data)) {
            handleHowToPlayClick();
            return;
        }
        if (POKER_FLIP_TABLE.equals(data)) {
            handleFlipTableClick();
            return;
        }
        if (strData != null && strData.startsWith(POKER_RAISE_PREFIX)) {
            try {
                int amount = Integer.parseInt(strData.substring(POKER_RAISE_PREFIX.length()));
                handleRaiseAmountClick(amount);
            } catch (NumberFormatException e) {
                Global.getLogger(PokerPanelUI.class).warn("Invalid raise button ID: " + strData);
            }
        }
    }
    
    // ============================================================================
    // ACTION HANDLERS
    // ============================================================================
    
    protected void handleFoldClick() {
        showPlayerAction(Strings.get("poker_actions.you_fold"));
        if (actionCallback != null) {
            actionCallback.onPlayerAction(PokerGame.Action.FOLD, 0);
        }
    }
    
    protected void handleCheckCallClick() {
        PokerGame.PokerState state = game.getState();
        int callAmount = state.opponentBet - state.playerBet;
        
        if (callAmount > 0) {
            showPlayerAction(Strings.format("poker_actions.you_call", callAmount));
            if (actionCallback != null) {
                actionCallback.onPlayerAction(PokerGame.Action.CALL, 0);
            }
        } else {
            showPlayerAction(Strings.get("poker_actions.you_check"));
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
        showPlayerAction(Strings.format("poker_actions.you_raise_to", amount));
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
