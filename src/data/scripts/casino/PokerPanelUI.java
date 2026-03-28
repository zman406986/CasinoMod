package data.scripts.casino;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate.DialogCallbacks;
import com.fs.starfarer.api.graphics.SpriteAPI;
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

import data.scripts.casino.Poker.CardFlipAnimation;
import data.scripts.casino.Poker.CardSprites;
import data.scripts.casino.Poker.CardUtils;
import data.scripts.casino.PokerGame.PokerState;
import data.scripts.casino.PokerGame.Round;
import data.scripts.casino.PokerGame.PokerGameLogic.Card;
import data.scripts.casino.PokerGame.PokerGameLogic.HandScore;

/**
 * Poker UI Panel - renders the poker table, cards, and handles player input.
 * STATE MANAGEMENT:
 * - PokerHandler owns the game state (PokerGame instance)
 * - This panel is purely for rendering and input
 * - All game logic flows through PokerHandler -> PokerGame
 */
public class PokerPanelUI extends BaseCustomUIPanelPlugin
    implements ActionListenerDelegate
{
    private static final Logger logger = Global.getLogger(PokerPanelUI.class);
    private static final SettingsAPI settings = Global.getSettings();
    private static final SpriteAPI POKER_TABLE = settings.getSprite("poker", "table");

    private static final String POKER_FOLD = "poker_fold";
    private static final String POKER_CHECK_CALL = "poker_check_call";
    private static final String POKER_RAISE_PREFIX = "poker_raise_";
    private static final String POKER_NEXT_HAND = "poker_next_hand";
    private static final String POKER_SUSPEND = "poker_suspend";
    private static final String POKER_HOW_TO_PLAY = "poker_how_to_play";
    private static final String POKER_FLIP_TABLE = "poker_flip_table";

    // Panel dimensions
    private static final float MARGIN = 20f;
    private static final float PANEL_WIDTH = 1000f;
    private static final float PANEL_HEIGHT = 700f;
    
    // Card dimensions 1 x sqrt(2)
    private static final float CARD_WIDTH = 65f;
    private static final float CARD_HEIGHT = 94f;
    private static final float CARD_SPACING = 12f;
    
    // Button dimensions
    private static final float BUTTON_WIDTH = 120f;
    private static final float BUTTON_HEIGHT = 35f;
    private static final float BUTTON_SPACING = 12f;
    private static final float RAISE_BUTTON_WIDTH = 180f;
    
    // COLORS
    private static final Color COLOR_PLAYER = new Color(100, 200, 255);
    private static final Color COLOR_OPPONENT = new Color(255, 100, 100);
    private static final Color COLOR_BG_DARK = new Color(15, 15, 20);
    private static final Color COLOR_ROUND_PREFLOP = new Color(150, 150, 200);
    private static final Color COLOR_ROUND_FLOP = new Color(100, 200, 100);
    private static final Color COLOR_ROUND_TURN = new Color(200, 200, 100);
    private static final Color COLOR_ROUND_RIVER = new Color(200, 150, 100);
    private static final Color COLOR_ROUND_SHOWDOWN = new Color(255, 200, 50);
    private static final Color COLOR_CARD_SHADOW = new Color(0, 0, 0, 150);

    // External callback when player makes an action
    private PokerActionCallback actionCallback;
    private DialogCallbacks callbacks;
    private CustomPanelAPI panel;
    private PositionAPI pos;
    
    // ============================================================================
    // UI STATE
    // ============================================================================
    private PokerGame game;
    private boolean waitingForOpponent = false;
    private float opponentThinkTimer = 0f;
    private static final float OPPONENT_THINK_DELAY = 0.8f;
    
    // Track if buttons have been created to avoid duplicates
    private boolean buttonsCreated = false;
        
    private final CardFlipAnimation[] playerCardAnimations = new CardFlipAnimation[2];
    private final CardFlipAnimation[] opponentCardAnimations = new CardFlipAnimation[2];
    private final CardFlipAnimation[] communityCardAnimations = new CardFlipAnimation[5];
    
    private Round lastAnimatedRound = null;
    private int lastAnimatedCommunityCount = 0;
    private boolean playerCardsAnimated = false;

    private static final boolean INFO_LOG_ENABLED = false;
    private static final void log(String txt) {
        if (INFO_LOG_ENABLED) logger.info(txt);
    }
    
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
    }
    
    private void checkAndTriggerAnimations(PokerState state,
        Round previousRound, int previousCommunityCount
    ) {
        log("checkAndTriggerAnimations: prevRound=" + previousRound + 
            " currRound=" + state.round + " prevCommunity=" + previousCommunityCount + 
            " currCommunity=" + (state.communityCards != null ? state.communityCards.size() : 0));
        
        // Player cards - trigger when hand is dealt and not yet animated
        // This uses a simple boolean flag that resets only on new hands
        if (!playerCardsAnimated && state.playerHand != null && !state.playerHand.isEmpty()) {
            playerCardsAnimated = true;
            for (int i = 0; i < state.playerHand.size() && i < 2; i++) {
                playerCardAnimations[i].triggerFlip(i * CardFlipAnimation.STAGGER_DELAY);
                log("Triggered player card " + i + " animation");
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
                    log("Triggered community card " + i + " animation, phase=" + communityCardAnimations[i].phase);
                }
            }
            // NOTE: lastAnimatedCommunityCount is now updated in updateGameState AFTER this call
        }
    }
    
    // CACHED VALUES
    private int lastPlayerStack = -1;
    private int lastOpponentStack = -1;
    private int lastPlayerBet = -1;
    private int lastOpponentBet = -1;
    private int lastPot = -1;
    private int lastBigBlind = -1;
    private Round lastRound = null;
    
    private boolean resultLblCached = false;
    
    // BUTTONS
    private final List<ButtonAPI> raiseOptionButtons = new ArrayList<>();
    private ButtonAPI flipTableButton;
    private ButtonAPI foldBtn;
    private ButtonAPI checkCallBtn;
    private ButtonAPI nextHandBtn;

    // LABELS
    private LabelAPI playerStackLabel;
    private LabelAPI opponentStackLabel;
    private LabelAPI roundLabel;
    private LabelAPI waitingLabel;
    private LabelAPI opponentActionLabel;
    private LabelAPI playerActionLabel;
    private LabelAPI returnMessageLabel;
    private LabelAPI resultLabel;
    
    public interface PokerActionCallback {
        void onPlayerAction(PokerGame.Action action, int raiseAmount);
        void onBackToMenu();
        void onNextHand();
        void onSuspend();
        void onHowToPlay();
        void onFlipTable();
    }
    
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
     */
    public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
        this.panel = panel;
        this.callbacks = callbacks;
        
        // Reset state for new panel
        waitingForOpponent = false;
        opponentThinkTimer = 0f;
        
        callbacks.getPanelFader().setDurationOut(0.5f);

        createButtonsInInit();
    }
    
    public void positionChanged(PositionAPI position) {
        this.pos = position;
    }
    
    // ============================================================================
    // BUTTON CREATION
    // ============================================================================
    
    /**
     * BUTTON LAYOUT (player side - BOTTOM of panel only):
     * - Leave button: top-right corner (always visible)
     * - Action buttons (Fold/Check/Raise): bottom row
     * - Raise options: row above action buttons
     */
    private void createButtonsInInit() {
        if (panel == null || buttonsCreated) {
            return;
        }
        final TooltipMakerAPI btnTp = panel.createUIElement(
            pos.getWidth(), pos.getHeight(), false
        );
        btnTp.setActionListenerDelegate(this);
        panel.addUIElement(btnTp).inBL(0f, 0f);

        flipTableButton = btnTp.addButton(Strings.get("poker_panel.run_away"), POKER_FLIP_TABLE, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        flipTableButton.setQuickMode(true);
        flipTableButton.getPosition().inTL(PANEL_WIDTH - BUTTON_WIDTH - MARGIN, MARGIN);

        final ButtonAPI suspendBtn = btnTp.addButton(Strings.get("poker_panel.wait"), POKER_SUSPEND, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        suspendBtn.setQuickMode(true);
        suspendBtn.getPosition().inTL(PANEL_WIDTH - BUTTON_WIDTH * 2 - BUTTON_SPACING - MARGIN, MARGIN);

        final ButtonAPI helpBtn = btnTp.addButton(Strings.get("poker_panel.how_to_play"), POKER_HOW_TO_PLAY, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        helpBtn.setQuickMode(true);
        helpBtn.getPosition().inTL(PANEL_WIDTH - BUTTON_WIDTH * 3 - BUTTON_SPACING * 2 - MARGIN, MARGIN);
        
        createStackDisplays();
        createRoundLabel();
        createWaitingLabel();
        createOpponentActionLabel();
        createPlayerActionLabel();
        createReturnMessageLabel();
        createResultLabel();
        createNextHandButton();

        foldBtn = btnTp.addButton(Strings.get("poker_panel.fold_btn"), POKER_FOLD, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        foldBtn.setQuickMode(true);
        foldBtn.getPosition().inTL(0, 0);
        foldBtn.setOpacity(0f);
        
        checkCallBtn = btnTp.addButton(Strings.get("poker_panel.check_btn"), POKER_CHECK_CALL, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        checkCallBtn.setQuickMode(true);
        checkCallBtn.getPosition().inTL(0, 0);
        checkCallBtn.setOpacity(0f);
        
        for (int i = 0; i < 5; i++) {
            btnTp.setActionListenerDelegate(this);
            final ButtonAPI btn = btnTp.addButton("", POKER_RAISE_PREFIX + "0", RAISE_BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
            btn.setQuickMode(true);
            btn.setCustomData(POKER_RAISE_PREFIX + "0");
            btn.getPosition().inTL(0, 0);
            btn.setOpacity(0f);
            raiseOptionButtons.add(btn);
        }
        
        buttonsCreated = true;
    }
    
    private void updateButtonVisibility() {
        if (panel == null || game == null) return;
        
        final PokerState state = game.getState();
        if (state == null) return;
        
        final boolean isShowdown = state.round == Round.SHOWDOWN;
        final String flipTableLabel = isShowdown ? Strings.get("poker_panel.leave_btn") : Strings.get("poker_panel.run_away");
        flipTableButton.setText(flipTableLabel);
        
        final int callAmount = state.opponentBet - state.playerBet;
        final boolean opponentEffectivelyAllIn = state.opponentStack <= state.bigBlind || state.opponentDeclaredAllIn;
        final boolean canRaise = state.playerStack > 0 && state.opponentStack > 0 && callAmount < state.playerStack && !opponentEffectivelyAllIn;
        final boolean isPlayerTurn = state.currentPlayer == PokerGame.CurrentPlayer.PLAYER && state.round != Round.SHOWDOWN;
        
        final float bottomY = PANEL_HEIGHT - BUTTON_HEIGHT - MARGIN;
        final float totalActionWidth = BUTTON_WIDTH * 2 + BUTTON_SPACING;
        final float actionStartX = (PANEL_WIDTH - totalActionWidth) / 2f;
        
        if (isPlayerTurn) {
            foldBtn.getPosition().inTL(actionStartX, bottomY);
            foldBtn.setOpacity(1f);
            
            final String checkCallLabel = callAmount > 0 ? Strings.format("poker_panel.call_btn", callAmount) : Strings.get("poker_panel.check_btn");
            checkCallBtn.setText(checkCallLabel);
            final float checkCallX = actionStartX + BUTTON_WIDTH + BUTTON_SPACING;
            checkCallBtn.getPosition().inTL(checkCallX, bottomY);
            checkCallBtn.setOpacity(1f);
        } else {
            foldBtn.setOpacity(0f);
            checkCallBtn.setOpacity(0f);
        }
        
        if (canRaise && isPlayerTurn) {
            final float[] raiseAmounts = getRaiseOptions(state);
            final float raiseOptionsY = bottomY - BUTTON_HEIGHT - BUTTON_SPACING;
            final float totalRaiseWidth = RAISE_BUTTON_WIDTH * raiseAmounts.length + BUTTON_SPACING * (raiseAmounts.length - 1);
            final float raiseStartX = (PANEL_WIDTH - totalRaiseWidth) / 2f;
            
            for (int i = 0; i < raiseOptionButtons.size(); i++) {
                if (i < raiseAmounts.length) {
                    final int amt = (int) raiseAmounts[i];
                    final String label = formatRaiseLabel(amt, state.bigBlind, state.pot, state.playerStack, state.opponentBet, state.playerBet);
                    final String btnId = POKER_RAISE_PREFIX + amt;
                    final float btnX = raiseStartX + (RAISE_BUTTON_WIDTH + BUTTON_SPACING) * i;
                    
                    raiseOptionButtons.get(i).setText(label);
                    raiseOptionButtons.get(i).setCustomData(btnId);
                    raiseOptionButtons.get(i).getPosition().inTL(btnX, raiseOptionsY);
                    raiseOptionButtons.get(i).setOpacity(1f);
                } else {
                    raiseOptionButtons.get(i).setOpacity(0f);
                }
            }
        } else {
            for (ButtonAPI pnl : raiseOptionButtons) {
                pnl.setOpacity(0f);
            }
        }
    }
    
    private float[] getRaiseOptions(PokerState state) {
        final List<Float> options = new ArrayList<>();
        final int pot = state.pot;
        final int stack = state.playerStack;
        final int bb = state.bigBlind;
        final int opponentBet = state.opponentBet;
        
        final int bbTotal = opponentBet + bb;
        if (bbTotal <= state.playerBet + stack) options.add((float) bbTotal);
        
        final int halfPot = pot / 2;
        final int halfPotTotal = opponentBet + halfPot;
        if (halfPot >= bb && halfPotTotal <= state.playerBet + stack && !options.contains((float) halfPotTotal)) {
            options.add((float) halfPotTotal);
        }
        
        final int potTotal = opponentBet + pot;
        if (pot >= bb && potTotal <= state.playerBet + stack && !options.contains((float) potTotal)) {
            options.add((float) potTotal);
        }
        
        final int twoPot = pot * 2;
        final int twoPotTotal = opponentBet + twoPot;
        if (twoPot > pot && twoPotTotal <= state.playerBet + stack && !options.contains((float) twoPotTotal)) {
            options.add((float) twoPotTotal);
        }
        
        final int allInTotal = state.playerBet + stack;
        if (stack > 0 && !options.contains((float) allInTotal)) {
            options.add((float) allInTotal);
        }
        
        final float[] result = new float[options.size()];
        for (int i = 0; i < options.size(); i++) {
            result[i] = options.get(i);
        }
        return result;
    }

    /**
     * Format: "Label (total / X.X BB)" where total = callAmount + raisePortion
     */
    private String formatRaiseLabel(int totalBet, int bigBlind, int pot, int playerStack, int opponentBet, int playerBet) {
        final int raisePortion = totalBet - opponentBet;
        
        final String label;
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
        
        final float bbAmount = bigBlind > 0 ? (float) totalBet / bigBlind : 0;
        return Strings.format("poker_raise_labels.format", label, totalBet, bbAmount);
    }
        
    /**
     * Layout: Opponent at top (Y=140), Player at bottom (Y=PANEL_HEIGHT-150)
     * Shows: "Opponent Stack: 9900 | Opponent current bet: 100"
     */
    private void createStackDisplays() {
        if (panel == null) return;
        
        final float STACK_LABEL_WIDTH = 450f;
        final float STACK_LABEL_HEIGHT = 25f;

        opponentStackLabel = settings.createLabel(Strings.format("poker_panel.opponent_stack_bet", 0, 0), Fonts.DEFAULT_SMALL);
        opponentStackLabel.setColor(COLOR_OPPONENT);
        opponentStackLabel.setAlignment(Alignment.LMID);
        panel.addComponent((UIComponentAPI) opponentStackLabel)
            .setSize(STACK_LABEL_WIDTH, STACK_LABEL_HEIGHT)
            .inTL(MARGIN, MARGIN);

        playerStackLabel = settings.createLabel(Strings.format("poker_panel.player_stack_bet", 0, 0), Fonts.DEFAULT_SMALL);
        playerStackLabel.setColor(COLOR_PLAYER);
        playerStackLabel.setAlignment(Alignment.LMID);
        panel.addComponent((UIComponentAPI) playerStackLabel)
            .setSize(STACK_LABEL_WIDTH, STACK_LABEL_HEIGHT)
            .inTL(MARGIN, MARGIN + 30f);
    }
    
    private void updateStackDisplays(int playerStack, int opponentStack,
        int playerBet, int opponentBet
    ) {
        if (playerStackLabel != null) {
            if (playerStack != lastPlayerStack || playerBet != lastPlayerBet) {
                lastPlayerStack = playerStack;
                lastPlayerBet = playerBet;
                final String txt = Strings.format("poker_panel.player_stack_bet", playerStack, playerBet);
                playerStackLabel.setText(txt);
            }
        }
        if (opponentStackLabel != null && opponentStack != lastOpponentStack ||
            opponentBet != lastOpponentBet
        ) {
            lastOpponentStack = opponentStack;
            lastOpponentBet = opponentBet;
            final String txt = Strings.format("poker_panel.opponent_stack_bet", opponentStack, opponentBet);
            opponentStackLabel.setText(txt);
        }
    }

    /**
     * Displays: "Round Progress: Flop | Current Pot: 200 | Big Blind (minimal bet): 50"
     */
    private void createRoundLabel() {
        if (panel == null) return;
        
        final float ROUND_LABEL_WIDTH = 600f;
        final float ROUND_LABEL_HEIGHT = 25f;
        
        final float x = (PANEL_WIDTH - ROUND_LABEL_WIDTH) / 2f;
        final float y = PANEL_HEIGHT * 0.40f;
        
        roundLabel = settings.createLabel(Strings.format("poker_panel.round_progress", Strings.get("poker_rounds.preflop"), 0, 50), Fonts.DEFAULT_SMALL);
        roundLabel.setColor(new Color(150, 200, 255));
        roundLabel.setAlignment(Alignment.MID);
        panel.addComponent((UIComponentAPI) roundLabel).inTL(x, y)
            .setSize(ROUND_LABEL_WIDTH, ROUND_LABEL_HEIGHT);
    }
    
    private void updateRoundLabel(Round round, int bigBlind, int pot) {
        if (roundLabel == null) return;
        
        if (round != lastRound || bigBlind != lastBigBlind || pot != lastPot) {
            lastRound = round;
            lastBigBlind = bigBlind;
            lastPot = pot;
            
            final String roundName = switch (round) {
                case PREFLOP -> Strings.get("poker_rounds.preflop");
                case FLOP -> Strings.get("poker_rounds.flop");
                case TURN -> Strings.get("poker_rounds.turn");
                case RIVER -> Strings.get("poker_rounds.river");
                case SHOWDOWN -> Strings.get("poker_rounds.showdown");
            };
            
            final String txt = Strings.format("poker_panel.round_progress", roundName, pot, bigBlind);
            roundLabel.setText(txt);
            
            final Color roundColor = switch (round) {
                case PREFLOP -> COLOR_ROUND_PREFLOP;
                case FLOP -> COLOR_ROUND_FLOP;
                case TURN -> COLOR_ROUND_TURN;
                case RIVER -> COLOR_ROUND_RIVER;
                case SHOWDOWN -> COLOR_ROUND_SHOWDOWN;
            };
            roundLabel.setColor(roundColor);
        }
    }
    
    private void createWaitingLabel() {
        if (panel == null) return;
        
        final float WAITING_LABEL_WIDTH = 200f;
        final float WAITING_LABEL_HEIGHT = 25f;
        
        final float x = (PANEL_WIDTH - WAITING_LABEL_WIDTH) / 2f;
        final float y = PANEL_HEIGHT * 0.68f;
        
        waitingLabel = settings.createLabel(Strings.get("poker_panel.opponent_thinking"), Fonts.DEFAULT_SMALL);
        waitingLabel.setColor(new Color(255, 200, 100));
        waitingLabel.setAlignment(Alignment.MID);
        panel.addComponent((UIComponentAPI) waitingLabel).inTL(x, y)
            .setSize(WAITING_LABEL_WIDTH, WAITING_LABEL_HEIGHT);
        waitingLabel.setOpacity(0f);
    }
    
    private void updateWaitingLabel(boolean waiting) {
        if (waitingLabel != null) {
            waitingLabel.setOpacity(waiting ? 1f : 0f);
        }
    }
    
    private void createOpponentActionLabel() {
        if (panel == null) return;
        
        final float ACTION_LABEL_WIDTH = 250f;
        final float ACTION_LABEL_HEIGHT = 25f;
        
        opponentActionLabel = settings.createLabel("", Fonts.DEFAULT_SMALL);
        opponentActionLabel.setColor(COLOR_OPPONENT);
        opponentActionLabel.setAlignment(Alignment.LMID);
        panel.addComponent((UIComponentAPI) opponentActionLabel).inTL(160, 200)
            .setSize(ACTION_LABEL_WIDTH, ACTION_LABEL_HEIGHT);
        hideOpponentAction();
    }
    
    private void createPlayerActionLabel() {
        if (panel == null) return;
        
        final float ACTION_LABEL_WIDTH = 250f;
        final float ACTION_LABEL_HEIGHT = 25f;
        
        playerActionLabel = settings.createLabel("", Fonts.DEFAULT_SMALL);
        playerActionLabel.setColor(COLOR_PLAYER);
        playerActionLabel.setAlignment(Alignment.LMID);
        panel.addComponent((UIComponentAPI) playerActionLabel).inTL(160, 480)
            .setSize(ACTION_LABEL_WIDTH, ACTION_LABEL_HEIGHT);
        hidePlayerAction();
    }
    
    public void showOpponentAction(String action) {
        if (opponentActionLabel != null) {
            opponentActionLabel.setText(action);
            opponentActionLabel.setOpacity(1f);
        }
    }

    public void showPlayerAction(String action) {
        if (playerActionLabel != null) {
            playerActionLabel.setText(action);
            playerActionLabel.setOpacity(1f);
        }
    }
    
    public void hideOpponentAction() {
        if (opponentActionLabel != null) {
            opponentActionLabel.setOpacity(0f);
        }
    }

    public void hidePlayerAction() {
        if (playerActionLabel != null) {
            playerActionLabel.setOpacity(0f);
        }
    }

    private void createReturnMessageLabel() {
        if (panel == null) return;
        
        final float RETURN_LABEL_WIDTH = 400f;
        final float RETURN_LABEL_HEIGHT = 25f;
        
        final float x = (PANEL_WIDTH - RETURN_LABEL_WIDTH) / 2f;
        final float y = PANEL_HEIGHT * 0.3f;
        
        returnMessageLabel = settings.createLabel("", Fonts.DEFAULT_SMALL);
        returnMessageLabel.setColor(new Color(200, 200, 100));
        returnMessageLabel.setAlignment(Alignment.MID);
        panel.addComponent((UIComponentAPI) returnMessageLabel).inTL(x, y)
            .setSize(RETURN_LABEL_WIDTH, RETURN_LABEL_HEIGHT);
        returnMessageLabel.setOpacity(0f);
    }
    
    public void showReturnMessage(String message) {
        if (returnMessageLabel != null) {
            returnMessageLabel.setText(message);
            returnMessageLabel.setOpacity(1f);
        }
    }
    
    private void createResultLabel() {
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

    private void updateResultLabel(PokerState state) {
        if (resultLabel == null || state == null) return;
        
        if (state.round != Round.SHOWDOWN) {
            resultLabel.setOpacity(0f);
            resultLblCached = false;
            return;
        }
        
        if (resultLblCached) return;
        
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
            final HandScore playerScore = PokerGame.PokerGameLogic.evaluate(state.playerHand, state.communityCards);
            final HandScore opponentScore = PokerGame.PokerGameLogic.evaluate(state.opponentHand, state.communityCards);
            
            final String playerHandDesc = formatHandDescription(playerScore);
            final String oppHandDesc = formatHandDescription(opponentScore);
            
            showPlayerAction(Strings.format("poker_ui.you_prefix", playerHandDesc));
            showOpponentAction(Strings.format("poker_ui.opp_prefix", oppHandDesc));
            
            final int cmp = playerScore.compareTo(opponentScore);
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
        
        final boolean playerBust = state.playerStack < state.bigBlind;
        final boolean opponentBust = state.opponentStack < state.bigBlind;
        
        if (playerBust || opponentBust) {
            final String bustMessage = playerBust ? 
                Strings.get("poker_panel.you_bust_leave") : 
                Strings.get("poker_panel.opponent_bust_leave");
            resultText += bustMessage;
        }
        
        resultLblCached = true;
        
        resultLabel.setText(resultText);
        resultLabel.setColor(resultColor);
        resultLabel.setOpacity(1f);
    }
    
    /**
     * Formats a hand score into a human-readable description.
     * E.g., "Two Pair - Kings and Tens" or "Flush - Ace high"
     */
    private String formatHandDescription(HandScore score) {
        if (score == null || score.rank == null) return Strings.get("poker_hand_desc.unknown");
        
        final String rankName = formatHandRank(score.rank);
        
        if (score.tieBreakers == null || score.tieBreakers.isEmpty()) {
            return rankName;
        }
        
        final String highCard = CardUtils.getRankName(score.tieBreakers.get(0));

        return switch (score.rank)
        {
            case HIGH_CARD -> Strings.format("poker_hand_desc.high_card", highCard);
            case PAIR -> Strings.format("poker_hand_desc.pair_of", highCard);
            case TWO_PAIR ->
            {
                if (score.tieBreakers.size() >= 2)
                {
                    final String firstPair = CardUtils.getRankName(score.tieBreakers.get(0));
                    final String secondPair = CardUtils.getRankName(score.tieBreakers.get(1));
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
                    final String trips = CardUtils.getRankName(score.tieBreakers.get(0));
                    final String pair = CardUtils.getRankName(score.tieBreakers.get(1));
                    yield Strings.format("poker_hand_desc.full_house_full", trips, pair);
                }
                yield Strings.get("poker_hand_desc.full_house");
            }
            case FOUR_OF_A_KIND -> Strings.format("poker_hand_desc.four", highCard);
            case STRAIGHT_FLUSH ->
            {
                if (highCard.equals(Strings.get("poker_ranks.ace")))
                {
                    yield Strings.get("poker_hand_desc.royal_flush");
                }
                yield Strings.format("poker_hand_desc.straight_flush_high", highCard);
            }
        };
    }
    
    private String formatHandRank(PokerGame.PokerGameLogic.HandRank rank) {
        if (rank == null) return Strings.get("poker_hand_desc.unknown");
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
    
    private void createNextHandButton() {
        if (panel == null) return;
        
        final float BUTTON_W = 120f;
        final float BUTTON_H = 30f;
        
        final TooltipMakerAPI tp = panel.createUIElement(BUTTON_W, BUTTON_H, false);
        tp.setActionListenerDelegate(this);
        nextHandBtn = tp.addButton(Strings.get("poker_panel.next_hand_btn"), POKER_NEXT_HAND, BUTTON_W, BUTTON_H, 0f);
        nextHandBtn.setQuickMode(true);
        panel.addUIElement(tp).inTL(PANEL_WIDTH / 2f + 200f, PANEL_HEIGHT / 2f + CARD_HEIGHT / 2f + 70f);
        nextHandBtn.setOpacity(0f);
    }
    
    private void updateNextHandButton(PokerState state) {
        if (nextHandBtn == null) return;
        
        final boolean atShowdown = state.round == Round.SHOWDOWN;

        nextHandBtn.setOpacity(atShowdown ? 1f : 0f);
    }
    
    // ============================================================================
    // RENDERING
    // ============================================================================
    
    /**
     * 1. Background fill
     * 2. Poker table
     * 3. Community cards
     * 4. Player hand
     * 5. Pot and stack displays
     */
    public void renderBelow(float alphaMult) {
        final float x = pos.getX();
        final float y = pos.getY();
        final float w = pos.getWidth();
        final float h = pos.getHeight();
        final float cx = pos.getCenterX();
        final float cy = pos.getCenterY();

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        final float s = Global.getSettings().getScreenScaleMult();
        GL11.glScissor((int)(x * s), (int)(y * s), (int)(w * s), (int)(h * s));
        
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        Misc.renderQuad(x, y, w, h, COLOR_BG_DARK, alphaMult);
        
        POKER_TABLE.setSize(w - 6, h * 0.7f);
        POKER_TABLE.render(x + 3, y + h * 0.15f);
        
        final PokerState state = game.getState();
        
        updateStackDisplays(state.playerStack, state.opponentStack, state.displayPlayerBet, state.displayOpponentBet);
        updateRoundLabel(state.round, state.bigBlind, state.pot);
        updateWaitingLabel(waitingForOpponent);

        updateResultLabel(state);
        updateNextHandButton(state);
        updateButtonVisibility();
        
        if (!state.communityCards.isEmpty()) {
            renderCommunityCards(cx, cy, state.communityCards, alphaMult);
        }
        
        final float playerCardBottomY = h * 0.25f - CARD_HEIGHT / 2f;
        renderPlayerHand(cx, y + playerCardBottomY, state.playerHand, alphaMult);
        
        final float opponentCardBottomY = h * 0.75f - CARD_HEIGHT / 2f;
        final boolean showOpponentCards = state.round == Round.SHOWDOWN && state.folder == null;
        renderOpponentHand(cx, y + opponentCardBottomY, 
            state.opponentHand, showOpponentCards, alphaMult);
        
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
    
    public void render(float alphaMult) {}
    
    // ============================================================================
    // RENDERING HELPERS
    // ============================================================================

    private void renderCommunityCards(float cx, float cy, 
        List<Card> cards, float alphaMult
    ) {
        final int numCards = cards.size();
        final float totalWidth = numCards * CARD_WIDTH + (numCards - 1) * CARD_SPACING;
        final float startX = cx - totalWidth / 2f;
        
        for (int i = 0; i < numCards; i++) {
            final Card card = cards.get(i);
            final float cardX = startX + i * (CARD_WIDTH + CARD_SPACING);
            renderCardAnimated(cardX, cy - CARD_HEIGHT/2, card, communityCardAnimations[i], alphaMult);
        }
    }
    
    private void renderPlayerHand(float cx, float y, 
        List<Card> cards, float alphaMult
    ) {
        if (cards == null || cards.isEmpty()) return;
        
        final float totalWidth = 2 * CARD_WIDTH + CARD_SPACING;
        final float startX = cx - totalWidth / 2f;
        
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        
        for (int i = 0; i < cards.size(); i++) {
            final Card card = cards.get(i);
            final float cardX = startX + i * (CARD_WIDTH + CARD_SPACING);
            renderCardAnimated(cardX, y, card, playerCardAnimations[i], alphaMult);
        }
    }
    
    /**
     * @param showCards If true, cards are face up (showdown); otherwise face down.
     */
    private void renderOpponentHand(float cx, float y, 
        List<Card> cards, boolean showCards, float alphaMult
    ) {
        if (cards == null || cards.isEmpty()) return;
        
        final float totalWidth = 2 * CARD_WIDTH + CARD_SPACING;
        final float startX = cx - totalWidth / 2f;
        
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        
        for (int i = 0; i < cards.size(); i++) {
            final Card card = cards.get(i);
            final float cardX = startX + i * (CARD_WIDTH + CARD_SPACING);
            if (showCards) {
                renderCardAnimated(cardX, y, card, opponentCardAnimations[i], alphaMult);
            } else {
                renderCardFaceDown(cardX, y, alphaMult);
            }
        }
    }
    
    private void renderCardFaceUp(float x, float y, Card card, float alphaMult) {
        Misc.renderQuad(x + 3, y - 3, CARD_WIDTH, CARD_HEIGHT, COLOR_CARD_SHADOW, alphaMult * 0.3f);
        
        if (card != null) {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1f, 1f, 1f, 1f);
            final SpriteAPI sprite = CardSprites.get(card.suit(), card.rank());
            sprite.setSize(CARD_WIDTH, CARD_HEIGHT);
            sprite.render(x, y);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
        }
    }
    
    private void renderCardFaceDown(float x, float y, float alphaMult) {
        Misc.renderQuad(x + 3, y - 3, CARD_WIDTH, CARD_HEIGHT, COLOR_CARD_SHADOW, alphaMult * 0.3f);

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        final SpriteAPI back = CardSprites.BACK_RED;
        back.setSize(CARD_WIDTH, CARD_HEIGHT);
        back.render(x, y);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
    }
    
    /**
     * Uses GL11 scale transform to simulate 3D card flip from top-down perspective.
     */
    private void renderCardAnimated(float x, float y, Card card,
        CardFlipAnimation anim, float alphaMult
    ) {
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
        
        final float widthScale = anim.getWidthScale();
        final float cardCenterX = x + CARD_WIDTH / 2f;
        
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

    // ============================================================================
    // GAME STATE UPDATE
    // ============================================================================
    
    public void advance(float amount) {
        if (waitingForOpponent) {
            opponentThinkTimer += amount;
            if (opponentThinkTimer >= OPPONENT_THINK_DELAY) {
                waitingForOpponent = false;
                opponentThinkTimer = 0f;
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
        final PokerState state = game.getState();

        Round previousRound = lastAnimatedRound;
        int previousCommunityCount = lastAnimatedCommunityCount;

        // Check if this is a new hand (transition to PREFLOP from non-PREFLOP)
        if (state.round == Round.PREFLOP && previousRound != Round.PREFLOP) {
            resetCardAnimations();

            previousRound = lastAnimatedRound;
            previousCommunityCount = lastAnimatedCommunityCount;
        }

        waitingForOpponent = false;
        opponentThinkTimer = 0f;

        resultLblCached = false;

        // Calculate current values but DON'T update tracking variables yet
        int currentCommunityCount = state.communityCards != null ? state.communityCards.size() : 0;

        // Reset community card animation tracking if cards are cleared (new hand)
        if (lastAnimatedCommunityCount > 0 && state.communityCards != null && state.communityCards.isEmpty()) {
            lastAnimatedCommunityCount = 0;
            previousCommunityCount = 0;
        }

        // Check for new animations using CAPTURED old values
        // This must happen BEFORE updating tracking variables
        checkAndTriggerAnimations(state, previousRound, previousCommunityCount);

        // NOW update tracking variables AFTER animation check
        lastAnimatedRound = state.round;
        lastAnimatedCommunityCount = currentCommunityCount;
    }
    
    // ============================================================================
    // INPUT HANDLING
    // ============================================================================
    
    public void processInput(List<InputEventAPI> events) {
        for (InputEventAPI event : events) {
            if (event.isConsumed()) continue;
            
            if (event.isKeyDownEvent()) {
                final int key = event.getEventValue();
                
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
    
    private void processAction(Object data) {
        if (data == null) return;
        
        final String strData = data instanceof String ? (String) data : null;
        
        // Compare instance
        if (POKER_FOLD == data) {
            handleFoldClick();
            return;
        }
        if (POKER_CHECK_CALL == data) {
            handleCheckCallClick();
            return;
        }
        if (POKER_NEXT_HAND == data) {
            handleNextHandClick();
            return;
        }
        if (POKER_SUSPEND == data) {
            handleSuspendClick();
            return;
        }
        if (POKER_HOW_TO_PLAY == data) {
            handleHowToPlayClick();
            return;
        }
        if (POKER_FLIP_TABLE == data) {
            handleFlipTableClick();
            return;
        }
        if (strData != null && strData.startsWith(POKER_RAISE_PREFIX)) {
            try {
                final int amount = Integer.parseInt(strData.substring(POKER_RAISE_PREFIX.length()));
                handleRaiseAmountClick(amount);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                    "Invalid raise button ID: " + strData
                );
            }
        }
    }
    
    private void handleFoldClick() {
        showPlayerAction(Strings.get("poker_actions.you_fold"));
        if (actionCallback != null) {
            actionCallback.onPlayerAction(PokerGame.Action.FOLD, 0);
        }
    }
    
    private void handleCheckCallClick() {
        final PokerState state = game.getState();
        final int callAmount = state.opponentBet - state.playerBet;
        
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
    
    private void handleRaiseClick() {
        // TODO for now, just call with minimum raise. In a real implementation, this might open a raise dialog
        final PokerState state = game.getState();
        final int minRaise = state.bigBlind;
        handleRaiseAmountClick(minRaise);
    }
    
    private void handleRaiseAmountClick(int amount) {
        showPlayerAction(Strings.format("poker_actions.you_raise_to", amount));
        if (actionCallback != null) {
            actionCallback.onPlayerAction(PokerGame.Action.RAISE, amount);
        }
    }
    
    private void handleNextHandClick() {
        final PokerState state = game.getState();
        final boolean canContinue = state.playerStack >= state.bigBlind && 
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
    
    private void handleSuspendClick() {
        if (actionCallback != null) {
            actionCallback.onSuspend();
        }
    }
    
    private void handleHowToPlayClick() {
        if (actionCallback != null) {
            actionCallback.onHowToPlay();
        }
    }
    
    private void handleFlipTableClick() {
        if (actionCallback != null) {
            actionCallback.onFlipTable();
        }
    }   
}
