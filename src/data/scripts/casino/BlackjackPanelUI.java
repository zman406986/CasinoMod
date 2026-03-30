package data.scripts.casino;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

import data.scripts.casino.cards.Card;
import data.scripts.casino.cards.CardFlipAnimation;
import data.scripts.casino.shared.CardGameUI;
import data.scripts.casino.BlackjackGame.Action;
import data.scripts.casino.BlackjackGame.GameState;
import data.scripts.casino.BlackjackGame.Hand;

import static data.scripts.casino.shared.CardGameUI.*;

public class BlackjackPanelUI extends BaseCustomUIPanelPlugin
    implements ActionListenerDelegate {

    private static final SettingsAPI settings = Global.getSettings();
    
    private static final int[] BET_AMOUNTS = {100, 250, 500, 1000, 2500}; // Bet amount constants for array access

    private static final String BJ_HIT = "bj_hit";
    private static final String BJ_STAND = "bj_stand";
    private static final String BJ_DOUBLE = "bj_double";
    private static final String BJ_SPLIT = "bj_split";
    private static final String BJ_NEW_HAND = "bj_new_hand";
    private static final String BJ_LEAVE = "bj_leave";
    private static final String BJ_HOW_TO_PLAY = "bj_how_to_play";
    private static final String BJ_OVERDRAFT_ON = "bj_overdraft_on";
    private static final String BJ_OVERDRAFT_OFF = "bj_overdraft_off";
    
    protected DialogCallbacks callbacks;
    protected CustomPanelAPI panel;
    protected PositionAPI pos;

    protected BlackjackActionCallback actionCallback;

    protected BlackjackGame game;
    protected boolean buttonsCreated = false;
    
    private final Map<String, Runnable> actionHandlers = new HashMap<>(); // Handler map for button actions

    protected final CardFlipAnimation[] playerCardAnimations = new CardFlipAnimation[10];
    protected final CardFlipAnimation[] dealerCardAnimations = new CardFlipAnimation[10];

    protected int lastPlayerCardCount = 0;
    protected int lastDealerCardCount = 0;
    protected boolean lastDealerHoleRevealed = false;
    protected int[] lastSplitHandCardCounts = new int[2];
    protected boolean wasSplitMode = false;

    protected ButtonAPI hitButton;
    protected ButtonAPI standButton;
    protected ButtonAPI doubleButton;
    protected ButtonAPI splitButton;
    protected ButtonAPI newHandButton;
    protected ButtonAPI leaveButton;
    protected ButtonAPI howToPlayButton;
    protected ButtonAPI overdraftOnButton;
    protected ButtonAPI overdraftOffButton;
    protected final ButtonAPI[] betButtons = new ButtonAPI[5]; // Array for bet buttons

    protected LabelAPI playerStackLabel;
    protected LabelAPI resultLabel;
    protected LabelAPI statusLabel;
    protected LabelAPI dealerTotalLabel;
    protected LabelAPI playerTotalLabel;
    protected LabelAPI waitingLabel;
    protected LabelAPI[] splitHandValueLabels = new LabelAPI[2];
    protected LabelAPI[] splitHandResultLabels = new LabelAPI[2];

    public interface BlackjackActionCallback {
        void onPlayerAction(Action action);
        void onNewHand();
        void onLeave();
        void onHowToPlay();
        void onPlaceBet(int amount);
    }

    public BlackjackPanelUI(BlackjackGame game, BlackjackActionCallback callback) {
        this.game = game;
        this.actionCallback = callback;

        for (int i = 0; i < 10; i++) {
            playerCardAnimations[i] = new CardFlipAnimation();
            dealerCardAnimations[i] = new CardFlipAnimation();
        }
        
        initializeHandlers(); // Set up action handler map
    }
    
    private void initializeHandlers() {
        actionHandlers.put(BJ_HIT, this::handleHitClick);
        actionHandlers.put(BJ_STAND, this::handleStandClick);
        actionHandlers.put(BJ_DOUBLE, this::handleDoubleClick);
        actionHandlers.put(BJ_SPLIT, this::handleSplitClick);
        actionHandlers.put(BJ_NEW_HAND, this::handleNewHandClick);
        actionHandlers.put(BJ_LEAVE, this::handleLeaveClick);
        actionHandlers.put(BJ_HOW_TO_PLAY, this::handleHowToPlayClick);
        actionHandlers.put(BJ_OVERDRAFT_ON, this::handleOverdraftOnClick);
        actionHandlers.put(BJ_OVERDRAFT_OFF, this::handleOverdraftOffClick);
        for (int i = 0; i < BET_AMOUNTS.length; i++) {
            int amount = BET_AMOUNTS[i];
            actionHandlers.put("bj_bet_" + amount, () -> handleBetClick(amount));
        }
    }

    public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
        this.panel = panel;
        this.callbacks = callbacks;

        callbacks.getPanelFader().setDurationOut(0.5f);

        createButtonsInInit();
        createLabels();
    }

    public void positionChanged(PositionAPI position) {
        this.pos = position;
    }

    private void createButtonsInInit() {
        if (panel == null || buttonsCreated) return;

        final TooltipMakerAPI btnTp = panel.createUIElement(
            pos.getWidth(), pos.getHeight(), false
        );
        btnTp.setActionListenerDelegate(this);
        panel.addUIElement(btnTp).inBL(0f, 0f);

        leaveButton = btnTp.addButton(Strings.get("blackjack.leave"), BJ_LEAVE, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        leaveButton.setQuickMode(true);
        leaveButton.getPosition().inTL(PANEL_WIDTH - BUTTON_WIDTH - MARGIN, MARGIN);

        howToPlayButton = btnTp.addButton(Strings.get("blackjack.how_to_play"), BJ_HOW_TO_PLAY, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        howToPlayButton.setQuickMode(true);
        howToPlayButton.getPosition().inTL(PANEL_WIDTH - BUTTON_WIDTH * 2 - BUTTON_SPACING - MARGIN, MARGIN);

        float toggleWidth = BUTTON_WIDTH + 20f;
        overdraftOffButton = btnTp.addButton(Strings.get("blackjack.overdraft_off"), BJ_OVERDRAFT_OFF, toggleWidth, BUTTON_HEIGHT, 0f);
        overdraftOffButton.setQuickMode(true);
        overdraftOffButton.getPosition().inTL(MARGIN, MARGIN);

        overdraftOnButton = btnTp.addButton(Strings.get("blackjack.overdraft_on"), BJ_OVERDRAFT_ON, toggleWidth, BUTTON_HEIGHT, 0f);
        overdraftOnButton.setQuickMode(true);
        overdraftOnButton.getPosition().inTL(MARGIN, MARGIN);
        overdraftOnButton.setOpacity(0f);

        float bottomY = PANEL_HEIGHT - BUTTON_HEIGHT - MARGIN;
        float centerX = PANEL_WIDTH / 2f;

        hitButton = btnTp.addButton(Strings.get("blackjack.hit"), BJ_HIT, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        hitButton.setQuickMode(true);
        hitButton.getPosition().inTL(0, 0);
        hitButton.setOpacity(0f);

        standButton = btnTp.addButton(Strings.get("blackjack.stand"), BJ_STAND, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        standButton.setQuickMode(true);
        standButton.getPosition().inTL(0, 0);
        standButton.setOpacity(0f);

        doubleButton = btnTp.addButton(Strings.get("blackjack.double_down"), BJ_DOUBLE, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        doubleButton.setQuickMode(true);
        doubleButton.getPosition().inTL(0, 0);
        doubleButton.setOpacity(0f);

        splitButton = btnTp.addButton(Strings.get("blackjack.split"), BJ_SPLIT, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        splitButton.setQuickMode(true);
        splitButton.getPosition().inTL(0, 0);
        splitButton.setOpacity(0f);

        float newHandWidth = BUTTON_WIDTH * 2 + BUTTON_SPACING;
        newHandButton = btnTp.addButton(Strings.get("blackjack.new_hand"), BJ_NEW_HAND, newHandWidth, BUTTON_HEIGHT, 0f);
        newHandButton.setQuickMode(true);
        newHandButton.getPosition().inTL(centerX - newHandWidth / 2f, bottomY);
        newHandButton.setOpacity(0f);

        float betBtnWidth = 90f;
        float betBtnHeight = 40f;
        float betStartX = centerX - (betBtnWidth * 5 + BUTTON_SPACING * 4) / 2f;
        float betY = PANEL_HEIGHT * 0.55f;

        for (int i = 0; i < BET_AMOUNTS.length; i++) { // Create bet buttons in loop
            int amount = BET_AMOUNTS[i];
            betButtons[i] = btnTp.addButton(Strings.format("blackjack.bet_option", amount), "bj_bet_" + amount, betBtnWidth, betBtnHeight, 0f);
            betButtons[i].setQuickMode(true);
            betButtons[i].getPosition().inTL(betStartX + i * (betBtnWidth + BUTTON_SPACING), betY);
            betButtons[i].setOpacity(0f);
        }

        buttonsCreated = true;
    }

    private void createLabels() {
        if (panel == null) return;

        float cx = PANEL_WIDTH / 2f;

        float dealerLabelY = PANEL_HEIGHT * 0.25f + CARD_HEIGHT + 15f;
        dealerTotalLabel = settings.createLabel("", Fonts.DEFAULT_SMALL);
        dealerTotalLabel.setColor(COLOR_DEALER);
        dealerTotalLabel.setAlignment(Alignment.MID);
        panel.addComponent((UIComponentAPI) dealerTotalLabel).inTL(cx - 80f, dealerLabelY)
            .setSize(160f, 25f);
        dealerTotalLabel.setOpacity(0f);

        playerStackLabel = settings.createLabel("", Fonts.DEFAULT_SMALL);
        playerStackLabel.setColor(COLOR_PLAYER);
        playerStackLabel.setAlignment(Alignment.LMID);
        panel.addComponent((UIComponentAPI) playerStackLabel).inTL(MARGIN, MARGIN + 30f)
            .setSize(250f, 25f);

        float resultY = PANEL_HEIGHT * 0.32f;
        resultLabel = settings.createLabel("", Fonts.ORBITRON_20AA);
        resultLabel.setColor(Color.WHITE);
        resultLabel.setAlignment(Alignment.MID);
        panel.addComponent((UIComponentAPI) resultLabel).inTL(cx - 150f, resultY)
            .setSize(300f, 40f);
        resultLabel.setOpacity(0f);

        float statusY = PANEL_HEIGHT * 0.5f;
        statusLabel = settings.createLabel("", Fonts.DEFAULT_SMALL);
        statusLabel.setColor(Color.YELLOW);
        statusLabel.setAlignment(Alignment.MID);
        panel.addComponent((UIComponentAPI) statusLabel).inTL(cx - 150f, statusY)
            .setSize(300f, 25f);
        statusLabel.setOpacity(0f);

        float waitingY = PANEL_HEIGHT * 0.50f;
        waitingLabel = settings.createLabel("", Fonts.DEFAULT_SMALL);
        waitingLabel.setColor(new Color(255, 200, 100));
        waitingLabel.setAlignment(Alignment.MID);
        panel.addComponent((UIComponentAPI) waitingLabel).inTL(cx - 100f, waitingY)
            .setSize(200f, 25f);
        waitingLabel.setOpacity(0f);

        float playerLabelY = PANEL_HEIGHT * 0.75f - CARD_HEIGHT - 15f;
        playerTotalLabel = settings.createLabel("", Fonts.DEFAULT_SMALL);
        playerTotalLabel.setColor(COLOR_PLAYER);
        playerTotalLabel.setAlignment(Alignment.MID);
        panel.addComponent((UIComponentAPI) playerTotalLabel).inTL(cx - 80f, playerLabelY)
            .setSize(160f, 25f);
        playerTotalLabel.setOpacity(0f);

        float splitSpacing = CARD_WIDTH * 3f;
        float splitTotalWidth = 2 * CARD_WIDTH * 2 + splitSpacing;
        float splitStartX = cx - splitTotalWidth / 2f;
        
        for (int h = 0; h < 2; h++) {
            float handCenterX = splitStartX + h * (CARD_WIDTH * 2 + splitSpacing) + CARD_WIDTH;
            
            float valueY = PANEL_HEIGHT * 0.75f - CARD_HEIGHT - 30f;
            splitHandValueLabels[h] = settings.createLabel("", Fonts.DEFAULT_SMALL);
            splitHandValueLabels[h].setColor(COLOR_PLAYER);
            splitHandValueLabels[h].setAlignment(Alignment.MID);
            panel.addComponent((UIComponentAPI) splitHandValueLabels[h]).inTL(handCenterX - 60f, valueY)
                .setSize(120f, 20f);
            splitHandValueLabels[h].setOpacity(0f);
            
            float splitResultY = PANEL_HEIGHT * 0.75f + CARD_HEIGHT + 5f;
            splitHandResultLabels[h] = settings.createLabel("", Fonts.DEFAULT_SMALL);
            splitHandResultLabels[h].setColor(Color.WHITE);
            splitHandResultLabels[h].setAlignment(Alignment.MID);
            panel.addComponent((UIComponentAPI) splitHandResultLabels[h]).inTL(handCenterX - 60f, splitResultY)
                .setSize(120f, 20f);
            splitHandResultLabels[h].setOpacity(0f);
        }
    }

    private void updateButtonVisibility() {
        if (panel == null || game == null) return;

        BlackjackGame.GameStateData state = game.getState();
        if (state == null) return;

        boolean isBetting = state.state == GameState.BETTING;
        boolean isPlayerTurn = state.state == GameState.PLAYER_TURN;
        boolean isResult = state.state == GameState.RESULT;

        float bottomY = PANEL_HEIGHT - BUTTON_HEIGHT - MARGIN;

        switch (state.state) {
            case BETTING -> {
                setGameButtonsOpacity(0f);
                
                overdraftOffButton.setOpacity(state.overdraftEnabled ? 0f : 1f);
                overdraftOnButton.setOpacity(state.overdraftEnabled ? 1f : 0f);
                
                int effectiveBalance = state.overdraftEnabled ? state.playerStack : state.originalBalance;
                for (int i = 0; i < BET_AMOUNTS.length; i++) {
                    betButtons[i].setOpacity(effectiveBalance >= BET_AMOUNTS[i] ? 1f : 0.3f);
                }
            }
            case PLAYER_TURN -> {
                if (state.playerHand == null) return;
                setBetButtonsOpacity(0f);
                overdraftOffButton.setOpacity(0f);
                overdraftOnButton.setOpacity(0f);

                boolean canDouble = state.playerHand.canDoubleDown() && state.playerStack >= state.playerHand.betAmount;
                boolean canSplit = state.playerHand.canSplit() && state.playerStack >= state.playerHand.betAmount && state.splitHands.isEmpty();

                int numButtons = 2 + (canDouble ? 1 : 0) + (canSplit ? 1 : 0);
                float actionStartX = (PANEL_WIDTH - numButtons * BUTTON_WIDTH - (numButtons - 1) * BUTTON_SPACING) / 2f;

                hitButton.getPosition().inTL(actionStartX, bottomY);
                hitButton.setOpacity(1f);
                standButton.getPosition().inTL(actionStartX + BUTTON_WIDTH + BUTTON_SPACING, bottomY);
                standButton.setOpacity(1f);

                int btnIndex = 2;
                if (canDouble) {
                    doubleButton.getPosition().inTL(actionStartX + btnIndex * (BUTTON_WIDTH + BUTTON_SPACING), bottomY);
                    doubleButton.setOpacity(1f);
                    btnIndex++;
                } else {
                    doubleButton.setOpacity(0f);
                }
                if (canSplit) {
                    splitButton.getPosition().inTL(actionStartX + btnIndex * (BUTTON_WIDTH + BUTTON_SPACING), bottomY);
                    splitButton.setOpacity(1f);
                } else {
                    splitButton.setOpacity(0f);
                }
                newHandButton.setOpacity(0f);
            }
            case RESULT -> {
                setBetButtonsOpacity(0f);
                setGameButtonsOpacity(0f);
                overdraftOffButton.setOpacity(0f);
                overdraftOnButton.setOpacity(0f);
                newHandButton.setOpacity(1f);
            }
            default -> {
                setAllButtonsOpacity(0f);
                overdraftOffButton.setOpacity(0f);
                overdraftOnButton.setOpacity(0f);
            }
        }
    }
    
    private void setBetButtonsOpacity(float opacity) { // Batch set bet buttons opacity
        for (ButtonAPI btn : betButtons) {
            if (btn != null) btn.setOpacity(opacity);
        }
    }
    
    private void setGameButtonsOpacity(float opacity) { // Batch set game action buttons opacity
        hitButton.setOpacity(opacity);
        standButton.setOpacity(opacity);
        doubleButton.setOpacity(opacity);
        splitButton.setOpacity(opacity);
        newHandButton.setOpacity(opacity);
    }
    
    private void setAllButtonsOpacity(float opacity) { // Batch set all interactive buttons opacity
        setBetButtonsOpacity(opacity);
        setGameButtonsOpacity(opacity);
    }

    private void updateLabels() {
        if (game == null) return;

        BlackjackGame.GameStateData state = game.getState();
        if (state == null) return;

        updatePlayerStackLabel(state);
        boolean isSplitMode = state.splitHands != null && !state.splitHands.isEmpty();

        switch (state.state) { // Use switch expression for game state handling
            case BETTING -> handleBettingLabels();
            case RESULT -> handleResultLabels(state, isSplitMode);
            case PLAYER_TURN -> handlePlayerTurnLabels(state, isSplitMode);
            case DEALER_TURN -> handleDealerTurnLabels();
            default -> hideAllLabels();
        }

        updateDealerTotal();
        updatePlayerTotal();
    }
    
    private void updatePlayerStackLabel(BlackjackGame.GameStateData state) {
        if (playerStackLabel == null) return;
        int creditPortion = Math.min(state.creditBorrowed, state.playerStack);
        int walletPortion = state.playerStack - creditPortion;
        
        String stackText;
        if (state.currentBet > 0) {
            stackText = creditPortion > 0 ? Strings.format("blackjack.stack_bet_credit", walletPortion, creditPortion, state.currentBet) : Strings.format("blackjack.stack_and_bet", state.playerStack, state.currentBet);
        } else {
            stackText = creditPortion > 0 ? Strings.format("blackjack.stack_with_credit", walletPortion, creditPortion) : Strings.format("blackjack.stack_only", state.playerStack);
        }
        playerStackLabel.setText(stackText);
    }
    
    private void handleBettingLabels() { // Show betting prompt, hide other labels
        if (resultLabel != null) {
            resultLabel.setText(Strings.get("blackjack.place_bet"));
            resultLabel.setColor(Color.YELLOW);
            resultLabel.setOpacity(1f);
        }
        if (statusLabel != null) statusLabel.setOpacity(0f);
        if (waitingLabel != null) waitingLabel.setOpacity(0f);
        if (playerTotalLabel != null) playerTotalLabel.setOpacity(0f);
        if (dealerTotalLabel != null) dealerTotalLabel.setOpacity(0f);
        hideSplitLabels();
    }
    
    private void handleResultLabels(BlackjackGame.GameStateData state, boolean isSplitMode) { // Show result labels
        if (isSplitMode) {
            if (resultLabel != null) resultLabel.setOpacity(0f);
            int totalHands = state.splitHands.size();
            for (int h = 0; h < totalHands && h < 2; h++) {
                Hand hand = state.splitHands.get(h);
                if (hand == null) continue;
                
                if (splitHandValueLabels[h] != null) {
                    splitHandValueLabels[h].setText(getHandValueText(hand));
                    splitHandValueLabels[h].setColor(hand.isBust() ? Color.RED : COLOR_PLAYER);
                    splitHandValueLabels[h].setOpacity(1f);
                }
                
                if (state.splitHandResults != null && h < state.splitHandResults.size()) {
                    int handResult = state.splitHandResults.get(h);
                    if (splitHandResultLabels[h] != null) {
                        splitHandResultLabels[h].setText(getResultText(handResult));
                        splitHandResultLabels[h].setColor(getResultColor(handResult));
                        splitHandResultLabels[h].setOpacity(1f);
                    }
                }
            }
        } else {
            if (resultLabel != null) {
                int potWon = state.lastPotWon;
                resultLabel.setText(getResultText(potWon));
                resultLabel.setColor(getResultColor(potWon));
                resultLabel.setOpacity(1f);
            }
            hideSplitLabels();
        }

        if (statusLabel != null) {
            if (state.playerStack < 10) {
                statusLabel.setText(Strings.get("blackjack.out_of_chips"));
                statusLabel.setColor(Color.RED);
                statusLabel.setOpacity(1f);
            } else {
                statusLabel.setOpacity(0f);
            }
        }
    }
    
    private void handlePlayerTurnLabels(BlackjackGame.GameStateData state, boolean isSplitMode) { // Show player turn labels
        if (resultLabel != null) resultLabel.setOpacity(0f);
        if (waitingLabel != null) waitingLabel.setOpacity(0f);
        
        if (isSplitMode) {
            if (playerTotalLabel != null) playerTotalLabel.setOpacity(0f);
            if (statusLabel != null) {
                statusLabel.setText(Strings.format("blackjack.split_hand_status", state.currentSplitIndex + 1, state.splitHands.size()));
                statusLabel.setColor(Color.CYAN);
                statusLabel.setOpacity(1f);
            }
            
            int totalHands = state.splitHands.size();
            for (int h = 0; h < totalHands && h < 2; h++) {
                Hand hand = state.splitHands.get(h);
                if (hand == null) continue;
                
                if (splitHandValueLabels[h] != null) {
                    splitHandValueLabels[h].setText(getHandValueText(hand));
                    splitHandValueLabels[h].setColor(hand.isBust() ? Color.RED : COLOR_PLAYER);
                    splitHandValueLabels[h].setOpacity(1f);
                }
                if (splitHandResultLabels[h] != null) splitHandResultLabels[h].setOpacity(0f);
            }
        } else {
            if (statusLabel != null) statusLabel.setOpacity(0f);
            hideSplitLabels();
        }
    }
    
    private void handleDealerTurnLabels() { // Show dealer turn waiting message
        if (resultLabel != null) resultLabel.setOpacity(0f);
        if (statusLabel != null) statusLabel.setOpacity(0f);
        if (waitingLabel != null) {
            waitingLabel.setText(Strings.get("blackjack.dealer_playing"));
            waitingLabel.setOpacity(1f);
        }
        hideSplitLabels();
    }
    
    private void hideAllLabels() { // Hide all dynamic labels
        if (resultLabel != null) resultLabel.setOpacity(0f);
        if (statusLabel != null) statusLabel.setOpacity(0f);
        if (waitingLabel != null) waitingLabel.setOpacity(0f);
        hideSplitLabels();
    }
    
    private void hideSplitLabels() { // Hide split hand value and result labels
        for (int h = 0; h < 2; h++) {
            if (splitHandValueLabels[h] != null) splitHandValueLabels[h].setOpacity(0f);
            if (splitHandResultLabels[h] != null) splitHandResultLabels[h].setOpacity(0f);
        }
    }
    
    private String getHandValueText(Hand hand) {
        int value = hand.getValue();
        return hand.isBust() ? Strings.get("blackjack.bust") : 
            (hand.isSoft() ? Strings.format("blackjack.player_total_soft", value) : Strings.format("blackjack.player_total", value));
    }
    
    private String getResultText(int result) { // Format win/lose/push result text
        if (result > 0) return Strings.format("blackjack.win_stargems", result);
        if (result < 0) return Strings.format("blackjack.lose_stargems", Math.abs(result));
        return Strings.get("blackjack.push");
    }
    
    private Color getResultColor(int result) { // Get color for result display
        if (result > 0) return Color.GREEN;
        if (result < 0) return Color.RED;
        return Color.YELLOW;
    }

    private void updateDealerTotal() {
        if (dealerTotalLabel == null || game == null) return;

        Hand dealerHand = game.getDealerHand();
        if (dealerHand == null || dealerHand.cards.isEmpty()) {
            dealerTotalLabel.setOpacity(0f);
            return;
        }

        boolean revealed = game.isDealerHoleCardRevealed();
        if (revealed) {
            int total = dealerHand.getValue();
            String totalText = Strings.format("blackjack.dealer_total", total);
            dealerTotalLabel.setText(totalText);
            dealerTotalLabel.setOpacity(1f);
        } else {
            int visibleValue = dealerHand.cards.get(0).value();
            String totalText = Strings.format("blackjack.dealer_showing", visibleValue);
            dealerTotalLabel.setText(totalText);
            dealerTotalLabel.setOpacity(1f);
        }
    }

    private void updatePlayerTotal() {
        if (playerTotalLabel == null || game == null) return;

        BlackjackGame.GameStateData state = game.getState();
        if (state != null && state.splitHands != null && !state.splitHands.isEmpty()) {
            playerTotalLabel.setOpacity(0f);
            return;
        }

        Hand playerHand = game.getPlayerHand();
        if (playerHand == null || playerHand.cards.isEmpty()) {
            playerTotalLabel.setOpacity(0f);
            return;
        }

        int total = playerHand.getValue();
        boolean soft = playerHand.isSoft();
        String totalText = soft ?
            Strings.format("blackjack.player_total_soft", total) :
            Strings.format("blackjack.player_total", total);
        playerTotalLabel.setText(totalText);
        playerTotalLabel.setOpacity(1f);
    }

    public void renderBelow(float alphaMult) {
        final float x = pos.getX();
        final float y = pos.getY();
        final float w = pos.getWidth();
        final float h = pos.getHeight();
        final float cx = pos.getCenterX();

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        final float s = Global.getSettings().getScreenScaleMult();
        GL11.glScissor((int)(x * s), (int)(y * s), (int)(w * s), (int)(h * s));

        CardGameUI.renderTableBackground(x, y, w, h, alphaMult);

        if (game == null) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            return;
        }

        updateLabels();
        updateButtonVisibility();

        float playerCardBottomY = h * 0.25f - CARD_HEIGHT / 2f;
        float dealerCardBottomY = h * 0.75f - CARD_HEIGHT / 2f;

        if (game.getDealerHand() != null && !game.getDealerHand().cards.isEmpty()) {
            renderDealerCards(cx, y + dealerCardBottomY, game.getDealerHand(), alphaMult);
        }

        BlackjackGame.GameStateData state = game.getState();
        if (state != null && !state.splitHands.isEmpty()) {
            renderSplitHands(cx, y + playerCardBottomY, state, alphaMult);
        } else if (game.getPlayerHand() != null && !game.getPlayerHand().cards.isEmpty()) {
            renderPlayerCards(cx, y + playerCardBottomY, game.getPlayerHand(), alphaMult);
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    public void render(float alphaMult) {}

    private void renderPlayerCards(float cx, float cardY, Hand hand, float alphaMult) {
        if (hand == null || hand.cards.isEmpty()) return;

        final int numCards = hand.cards.size();
        final float totalWidth = numCards * CARD_WIDTH + (numCards - 1) * CARD_SPACING;
        final float startX = cx - totalWidth / 2f;

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        for (int i = 0; i < numCards; i++) {
            final Card card = hand.cards.get(i);
            final float cardX = startX + i * (CARD_WIDTH + CARD_SPACING);
            final CardFlipAnimation anim = i < playerCardAnimations.length ? playerCardAnimations[i] : null;
            CardGameUI.renderCardAnimated(cardX, cardY, card, anim, alphaMult);
        }
    }

    private void renderSplitHands(float cx, float cardY, BlackjackGame.GameStateData state, float alphaMult) {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        int numHands = state.splitHands.size();
        if (numHands == 0) return;

        float splitSpacing = CARD_WIDTH * 3f;
        float totalWidth = numHands * CARD_WIDTH * 2 + (numHands - 1) * splitSpacing;
        float startX = cx - totalWidth / 2f;

        for (int h = 0; h < numHands; h++) {
            Hand hand = state.splitHands.get(h);
            if (hand == null || hand.cards.isEmpty()) continue;

            float handX = startX + h * (CARD_WIDTH * 2 + splitSpacing);

            for (int i = 0; i < hand.cards.size(); i++) {
                Card card = hand.cards.get(i);
                float cardX = handX + i * (CARD_WIDTH + CARD_SPACING / 2f);
                int animIndex = h * 5 + i;
                CardFlipAnimation anim = animIndex < playerCardAnimations.length ? playerCardAnimations[animIndex] : null;
                CardGameUI.renderCardAnimated(cardX, cardY, card, anim, alphaMult);
            }
        }
    }

    private void renderDealerCards(float cx, float cardY, Hand hand, float alphaMult) {
        if (hand == null || hand.cards.isEmpty()) return;

        int numCards = hand.cards.size();
        float totalWidth = numCards * CARD_WIDTH + (numCards - 1) * CARD_SPACING;
        float startX = cx - totalWidth / 2f;

        boolean revealed = game.isDealerHoleCardRevealed();

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        for (int i = 0; i < numCards; i++) {
            Card card = hand.cards.get(i);
            float cardX = startX + i * (CARD_WIDTH + CARD_SPACING);

            if (revealed || i > 0) {
                CardFlipAnimation anim = i < dealerCardAnimations.length ? dealerCardAnimations[i] : null;
                CardGameUI.renderCardAnimated(cardX, cardY, card, anim, alphaMult);
            } else {
                CardGameUI.renderCardFaceDown(cardX, cardY, alphaMult);
            }
        }
    }

    public void advance(float amount) {
        if (pos == null) return;

        for (int i = 0; i < 10; i++) {
            playerCardAnimations[i].advance(amount);
            dealerCardAnimations[i].advance(amount);
        }
    }

    public void updateGameState(BlackjackGame game) {
        this.game = game;

        BlackjackGame.GameStateData state = game.getState();
        if (state == null) return;

        boolean isSplitMode = !state.splitHands.isEmpty();
        
        if (!wasSplitMode && isSplitMode) {
            lastSplitHandCardCounts[0] = 1;
            lastSplitHandCardCounts[1] = 1;
            
            if (playerCardAnimations.length > 0) {
                playerCardAnimations[0].phase = CardFlipAnimation.Phase.REVEALED;
                playerCardAnimations[0].progress = 1f;
                playerCardAnimations[0].triggered = true;
            }
            if (playerCardAnimations.length > 5) {
                playerCardAnimations[5].phase = CardFlipAnimation.Phase.REVEALED;
                playerCardAnimations[5].progress = 1f;
                playerCardAnimations[5].triggered = true;
            }
        }
        wasSplitMode = isSplitMode;

        if (isSplitMode) {
            for (int h = 0; h < state.splitHands.size() && h < 2; h++) {
                Hand hand = state.splitHands.get(h);
                if (hand == null) continue;
                
                int currentCount = hand.cards.size();
                int lastCount = lastSplitHandCardCounts[h];
                
                if (currentCount > lastCount) {
                    for (int i = lastCount; i < currentCount && i < 5; i++) {
                        int idx = h * 5 + i;
                        if (idx < playerCardAnimations.length) {
                            playerCardAnimations[idx].triggerFlip((i - lastCount) * CardFlipAnimation.STAGGER_DELAY);
                        }
                    }
                }
                lastSplitHandCardCounts[h] = currentCount;
            }
            
            int totalSplitCards = 0;
            for (Hand hand : state.splitHands) {
                if (hand != null) {
                    totalSplitCards += hand.cards.size();
                }
            }
            lastPlayerCardCount = totalSplitCards;
        } else {
            int currentPlayerCards = state.playerHand != null ? state.playerHand.cards.size() : 0;

            if (currentPlayerCards > lastPlayerCardCount) {
                for (int i = lastPlayerCardCount; i < currentPlayerCards && i < 10; i++) {
                    playerCardAnimations[i].triggerFlip((i - lastPlayerCardCount) * CardFlipAnimation.STAGGER_DELAY);
                }
            }
            lastPlayerCardCount = currentPlayerCards;
            lastSplitHandCardCounts[0] = 0;
            lastSplitHandCardCounts[1] = 0;
        }

        int currentDealerCards = state.dealerHand != null ? state.dealerHand.cards.size() : 0;
        if (currentDealerCards > lastDealerCardCount) {
            for (int i = lastDealerCardCount; i < currentDealerCards && i < 10; i++) {
                dealerCardAnimations[i].triggerFlip((i - lastDealerCardCount) * CardFlipAnimation.STAGGER_DELAY);
            }
        }
        lastDealerCardCount = currentDealerCards;

        boolean holeRevealed = state.dealerHoleCardRevealed;
        if (holeRevealed && !lastDealerHoleRevealed) {
            dealerCardAnimations[0].reset();
            dealerCardAnimations[0].triggerFlip(0f);
        }
        lastDealerHoleRevealed = holeRevealed;
    }

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

                if (key == Keyboard.KEY_H) {
                    event.consume();
                    handleHitClick();
                    return;
                }

                if (key == Keyboard.KEY_S) {
                    event.consume();
                    handleStandClick();
                    return;
                }

                if (key == Keyboard.KEY_D) {
                    event.consume();
                    handleDoubleClick();
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

    private void processAction(Object data) { // Use handler map for action dispatch
        if (data instanceof String key) {
            Runnable handler = actionHandlers.get(key);
            if (handler != null) handler.run();
        }
    }

    private void handleHitClick() { if (actionCallback != null) actionCallback.onPlayerAction(Action.HIT); }
    
    private void handleStandClick() { if (actionCallback != null) actionCallback.onPlayerAction(Action.STAND); }
    
    private void handleDoubleClick() { if (actionCallback != null) actionCallback.onPlayerAction(Action.DOUBLE_DOWN); }
    
    private void handleSplitClick() { if (actionCallback != null) actionCallback.onPlayerAction(Action.SPLIT); }
    
    private void handleNewHandClick() { if (actionCallback != null) actionCallback.onNewHand(); }
    
    private void handleLeaveClick() { if (actionCallback != null) actionCallback.onLeave(); }
    
    private void handleHowToPlayClick() { if (actionCallback != null) actionCallback.onHowToPlay(); }

    private void handleOverdraftOnClick() {
        if (game != null) {
            game.getState().overdraftEnabled = false;
            updateButtonVisibility();
        }
    }

    private void handleOverdraftOffClick() {
        if (game != null) {
            game.getState().overdraftEnabled = true;
            updateButtonVisibility();
        }
    }

    private void handleBetClick(int amount) {
        if (actionCallback != null && game != null) {
            BlackjackGame.GameStateData state = game.getState();
            if (state != null) {
                int effectiveBalance = state.overdraftEnabled ? state.playerStack : state.originalBalance;
                if (effectiveBalance >= amount) {
                    actionCallback.onPlaceBet(amount);
                }
            }
        }
    }
}