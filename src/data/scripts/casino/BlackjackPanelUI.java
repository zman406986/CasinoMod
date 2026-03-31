package data.scripts.casino;

import java.awt.Color;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate.DialogCallbacks;
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
import data.scripts.casino.BlackjackGame.GameStateData;
import data.scripts.casino.BlackjackGame.Hand;

import static data.scripts.casino.shared.CardGameUI.*;

public class BlackjackPanelUI extends BaseCustomUIPanelPlugin
    implements ActionListenerDelegate
{
    private static final SettingsAPI settings = Global.getSettings();
    private static final String BJ_BET = "bj_bet_";
    private static final String BJ_HIT = "bj_hit";
    private static final String BJ_STAND = "bj_stand";
    private static final String BJ_DOUBLE = "bj_double";
    private static final String BJ_SPLIT = "bj_split";
    private static final String BJ_NEW_HAND = "bj_new_hand";
    private static final String BJ_LEAVE = "bj_leave";
    private static final String BJ_HOW_TO_PLAY = "bj_how_to_play";
    private static final String BJ_OVERDRAFT_ON = "bj_overdraft_on";
    private static final String BJ_OVERDRAFT_OFF = "bj_overdraft_off";
    private static final int[] BET_AMOUNTS = {100, 250, 500, 1000, 2500};

    private static final int HAND_SIZE = 2;

    protected final BlackjackActionCallback actionCallback;
    protected DialogCallbacks callbacks;
    protected CustomPanelAPI panel;

    protected BlackjackGame game;
    protected boolean buttonsCreated = false;

    protected final CardFlipAnimation[] playerCardAnimations = new CardFlipAnimation[10];
    protected final CardFlipAnimation[] dealerCardAnimations = new CardFlipAnimation[10];

    protected int lastPlayerCardCount = 0;
    protected int lastDealerCardCount = 0;
    protected boolean lastDealerHoleRevealed = false;
    protected int[] lastSplitHandCardCounts = new int[HAND_SIZE];
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
    protected final ButtonAPI[] betButtons = new ButtonAPI[5];

    protected LabelAPI playerStackLabel;
    protected LabelAPI resultLabel;
    protected LabelAPI statusLabel;
    protected LabelAPI dealerTotalLabel;
    protected LabelAPI playerTotalLabel;
    protected LabelAPI waitingLabel;
    protected LabelAPI[] splitHandValueLabels = new LabelAPI[HAND_SIZE];
    protected LabelAPI[] splitHandResultLabels = new LabelAPI[HAND_SIZE];

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
    }

    public final void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
        this.panel = panel;
        this.callbacks = callbacks;

        callbacks.getPanelFader().setDurationOut(0.5f);

        createButtonsInInit();
        createLabels();
    }

    private final void createButtonsInInit() {
        if (buttonsCreated) return;
        final PositionAPI pos = panel.getPosition();

        final float toggleWidth = BUTTON_WIDTH + 20f;
        final float bottomY = PANEL_HEIGHT - BUTTON_HEIGHT - MARGIN;
        final float centerX = PANEL_WIDTH / 2f;
        final float newHandWidth = BUTTON_WIDTH * HAND_SIZE + BUTTON_SPACING;

        final TooltipMakerAPI btnTp = panel.createUIElement(
            pos.getWidth(), pos.getHeight(), false
        );
        btnTp.setActionListenerDelegate(this);
        panel.addUIElement(btnTp).inBL(0f, 0f);

        leaveButton = btnTp.addButton(Strings.get("blackjack.leave"), BJ_LEAVE, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        leaveButton.setQuickMode(true);
        leaveButton.setShortcut(Keyboard.KEY_ESCAPE, false);
        leaveButton.getPosition().inTL(PANEL_WIDTH - BUTTON_WIDTH - MARGIN, MARGIN);

        howToPlayButton = btnTp.addButton(Strings.get("blackjack.how_to_play"), BJ_HOW_TO_PLAY, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        howToPlayButton.setQuickMode(true);
        howToPlayButton.getPosition().inTL(PANEL_WIDTH - BUTTON_WIDTH * 2 - BUTTON_SPACING - MARGIN, MARGIN);

        overdraftOffButton = btnTp.addButton(Strings.get("blackjack.overdraft_off"), BJ_OVERDRAFT_OFF, toggleWidth, BUTTON_HEIGHT, 0f);
        overdraftOffButton.setQuickMode(true);
        overdraftOffButton.getPosition().inTL(MARGIN, MARGIN);

        overdraftOnButton = btnTp.addButton(Strings.get("blackjack.overdraft_on"), BJ_OVERDRAFT_ON, toggleWidth, BUTTON_HEIGHT, 0f);
        overdraftOnButton.setQuickMode(true);
        overdraftOnButton.getPosition().inTL(MARGIN, MARGIN);
        overdraftOnButton.setOpacity(0f);

        hitButton = btnTp.addButton(Strings.get("blackjack.hit"), BJ_HIT, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        hitButton.setQuickMode(true);
        hitButton.setShortcut(Keyboard.KEY_H, false);
        hitButton.getPosition().inTL(0, 0);
        hitButton.setOpacity(0f);

        standButton = btnTp.addButton(Strings.get("blackjack.stand"), BJ_STAND, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        standButton.setQuickMode(true);
        standButton.setShortcut(Keyboard.KEY_S, false);
        standButton.getPosition().inTL(0, 0);
        standButton.setOpacity(0f);

        doubleButton = btnTp.addButton(Strings.get("blackjack.double_down"), BJ_DOUBLE, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        doubleButton.setQuickMode(true);
        doubleButton.setShortcut(Keyboard.KEY_D, false);
        doubleButton.getPosition().inTL(0, 0);
        doubleButton.setOpacity(0f);

        splitButton = btnTp.addButton(Strings.get("blackjack.split"), BJ_SPLIT, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        splitButton.setQuickMode(true);
        splitButton.getPosition().inTL(0, 0);
        splitButton.setOpacity(0f);

        newHandButton = btnTp.addButton(Strings.get("blackjack.new_hand"), BJ_NEW_HAND, newHandWidth, BUTTON_HEIGHT, 0f);
        newHandButton.setQuickMode(true);
        newHandButton.getPosition().inTL(centerX - newHandWidth / 2f, bottomY);
        newHandButton.setOpacity(0f);

        final float betBtnWidth = 90f;
        final float betBtnHeight = 40f;
        final float betStartX = centerX - (betBtnWidth * 5 + BUTTON_SPACING * 4) / 2f;
        final float betY = PANEL_HEIGHT * 0.55f;

        for (int i = 0; i < BET_AMOUNTS.length; i++) {
            int amount = BET_AMOUNTS[i];
            betButtons[i] = btnTp.addButton(Strings.format("blackjack.bet_option", amount), BJ_BET + amount, betBtnWidth, betBtnHeight, 0f);
            betButtons[i].setQuickMode(true);
            betButtons[i].getPosition().inTL(betStartX + i * (betBtnWidth + BUTTON_SPACING), betY);
            betButtons[i].setOpacity(0f);
        }

        buttonsCreated = true;
    }

    private final void createLabels() {
        final float dealerLabelY = PANEL_HEIGHT * 0.25f + CARD_HEIGHT + 15f;
        final float cx = PANEL_WIDTH / 2f;

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

        final float resultY = PANEL_HEIGHT * 0.32f;
        resultLabel = settings.createLabel("", Fonts.ORBITRON_20AA);
        resultLabel.setColor(Color.WHITE);
        resultLabel.setAlignment(Alignment.MID);
        panel.addComponent((UIComponentAPI) resultLabel).inTL(cx - 150f, resultY)
            .setSize(300f, 40f);
        resultLabel.setOpacity(0f);

        final float statusY = PANEL_HEIGHT * 0.5f;
        statusLabel = settings.createLabel("", Fonts.DEFAULT_SMALL);
        statusLabel.setColor(Color.YELLOW);
        statusLabel.setAlignment(Alignment.MID);
        panel.addComponent((UIComponentAPI) statusLabel).inTL(cx - 150f, statusY)
            .setSize(300f, 25f);
        statusLabel.setOpacity(0f);

        final float waitingY = PANEL_HEIGHT * 0.50f;
        waitingLabel = settings.createLabel("", Fonts.DEFAULT_SMALL);
        waitingLabel.setColor(new Color(255, 200, 100));
        waitingLabel.setAlignment(Alignment.MID);
        panel.addComponent((UIComponentAPI) waitingLabel).inTL(cx - 100f, waitingY)
            .setSize(200f, 25f);
        waitingLabel.setOpacity(0f);

        final float playerLabelY = PANEL_HEIGHT * 0.75f - CARD_HEIGHT - 15f;
        playerTotalLabel = settings.createLabel("", Fonts.DEFAULT_SMALL);
        playerTotalLabel.setColor(COLOR_PLAYER);
        playerTotalLabel.setAlignment(Alignment.MID);
        panel.addComponent((UIComponentAPI) playerTotalLabel).inTL(cx - 80f, playerLabelY)
            .setSize(160f, 25f);
        playerTotalLabel.setOpacity(0f);

        final float splitSpacing = CARD_WIDTH * 3f;
        final float splitTotalWidth = 2 * CARD_WIDTH * HAND_SIZE + splitSpacing;
        final float splitStartX = cx - splitTotalWidth / 2f;
        
        for (int h = 0; h < HAND_SIZE; h++) {
            final float handCenterX = splitStartX + h * (CARD_WIDTH * HAND_SIZE + splitSpacing) + CARD_WIDTH;
            
            final float valueY = PANEL_HEIGHT * 0.75f - CARD_HEIGHT - 30f;
            splitHandValueLabels[h] = settings.createLabel("", Fonts.DEFAULT_SMALL);
            splitHandValueLabels[h].setColor(COLOR_PLAYER);
            splitHandValueLabels[h].setAlignment(Alignment.MID);
            panel.addComponent((UIComponentAPI) splitHandValueLabels[h]).inTL(handCenterX - 60f, valueY)
                .setSize(120f, 20f);
            splitHandValueLabels[h].setOpacity(0f);
            
            final float splitResultY = PANEL_HEIGHT * 0.75f + CARD_HEIGHT + 5f;
            splitHandResultLabels[h] = settings.createLabel("", Fonts.DEFAULT_SMALL);
            splitHandResultLabels[h].setColor(Color.WHITE);
            splitHandResultLabels[h].setAlignment(Alignment.MID);
            panel.addComponent((UIComponentAPI) splitHandResultLabels[h]).inTL(handCenterX - 60f, splitResultY)
                .setSize(120f, 20f);
            splitHandResultLabels[h].setOpacity(0f);
        }
    }

    private final void updateButtonVisibility() {
        if (game == null) return;

        final GameStateData state = game.getState();

        final float bottomY = PANEL_HEIGHT - BUTTON_HEIGHT - MARGIN;

        switch (state.state) {
        case BETTING -> {
            setGameButtonsOpacity(0f);
            
            overdraftOffButton.setOpacity(state.overdraftEnabled ? 0f : 1f);
            overdraftOnButton.setOpacity(state.overdraftEnabled ? 1f : 0f);
            
            final int effectiveBalance = state.overdraftEnabled ? state.playerStack : state.originalBalance;
            for (int i = 0; i < BET_AMOUNTS.length; i++) {
                betButtons[i].setOpacity(effectiveBalance >= BET_AMOUNTS[i] ? 1f : 0.3f);
            }
        }
        case PLAYER_TURN -> {
            if (state.playerHand == null) return;
            setBetButtonsOpacity(0f);
            overdraftOffButton.setOpacity(0f);
            overdraftOnButton.setOpacity(0f);

            final boolean canDouble = state.playerHand.canDoubleDown() && state.playerStack >= state.playerHand.betAmount;
            final boolean canSplit = state.playerHand.canSplit() && state.playerStack >= state.playerHand.betAmount && state.splitHands.isEmpty();

            final int numButtons = 2 + (canDouble ? 1 : 0) + (canSplit ? 1 : 0);
            final float actionStartX = (PANEL_WIDTH - numButtons * BUTTON_WIDTH - (numButtons - 1) * BUTTON_SPACING) / 2f;

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
    
    private final void setBetButtonsOpacity(float opacity) {
        for (ButtonAPI btn : betButtons) {
            btn.setOpacity(opacity);
        }
    }
    
    private final void setGameButtonsOpacity(float opacity) {
        hitButton.setOpacity(opacity);
        standButton.setOpacity(opacity);
        doubleButton.setOpacity(opacity);
        splitButton.setOpacity(opacity);
        newHandButton.setOpacity(opacity);
    }
    
    private final void setAllButtonsOpacity(float opacity) {
        setBetButtonsOpacity(opacity);
        setGameButtonsOpacity(opacity);
    }

    private final void updateLabels() {
        if (game == null) return;
        final GameStateData state = game.getState();

        updatePlayerStackLabel(state);
        final boolean isSplitMode = state.splitHands != null && !state.splitHands.isEmpty();

        switch (state.state) {
            case BETTING -> handleBettingLabels();
            case RESULT -> handleResultLabels(state, isSplitMode);
            case PLAYER_TURN -> handlePlayerTurnLabels(state, isSplitMode);
            case DEALER_TURN -> handleDealerTurnLabels();
            default -> hideAllDynaLbls();
        }

        updateDealerTotal();
        updatePlayerTotal();
    }
    
    private void updatePlayerStackLabel(GameStateData state) {
        if (playerStackLabel == null) return;
        final int creditPortion = Math.min(state.creditBorrowed, state.playerStack);
        final int walletPortion = state.playerStack - creditPortion;
        
        final String stackText;
        if (state.currentBet > 0) {
            stackText = creditPortion > 0 ? Strings.format("blackjack.stack_bet_credit", walletPortion, creditPortion, state.currentBet) : Strings.format("blackjack.stack_and_bet", state.playerStack, state.currentBet);
        } else {
            stackText = creditPortion > 0 ? Strings.format("blackjack.stack_with_credit", walletPortion, creditPortion) : Strings.format("blackjack.stack_only", state.playerStack);
        }
        playerStackLabel.setText(stackText);
    }
    
    private final void handleBettingLabels() { // Show betting prompt, hide other labels
        resultLabel.setText(Strings.get("blackjack.place_bet"));
        resultLabel.setColor(Color.YELLOW);
        resultLabel.setOpacity(1f);

        statusLabel.setOpacity(0f);
        waitingLabel.setOpacity(0f);
        playerTotalLabel.setOpacity(0f);
        dealerTotalLabel.setOpacity(0f);
        hideSplitLabels();
    }
    
    private final void handleResultLabels(GameStateData state, boolean isSplitMode) {
        // Show result labels
        if (isSplitMode) {
            resultLabel.setOpacity(0f);
            final int totalHands = state.splitHands.size();
            for (int h = 0; h < totalHands && h < HAND_SIZE; h++) {
                final Hand hand = state.splitHands.get(h);
                if (hand == null) continue;
                
                splitHandValueLabels[h].setText(getHandValueText(hand));
                splitHandValueLabels[h].setColor(hand.isBust() ? Color.RED : COLOR_PLAYER);
                splitHandValueLabels[h].setOpacity(1f);
                
                if (h < state.splitHandResults.size()) {
                    final int handResult = state.splitHandResults.get(h);

                    splitHandResultLabels[h].setText(getResultText(handResult));
                    splitHandResultLabels[h].setColor(getResultColor(handResult));
                    splitHandResultLabels[h].setOpacity(1f);
                }
            }
        } else {
            resultLabel.setText(getResultText(state.lastPotWon));
            resultLabel.setColor(getResultColor(state.lastPotWon));
            resultLabel.setOpacity(1f);
            hideSplitLabels();
        }

        if (state.playerStack < 10) {
            statusLabel.setText(Strings.get("blackjack.out_of_chips"));
            statusLabel.setColor(Color.RED);
            statusLabel.setOpacity(1f);
        } else {
            statusLabel.setOpacity(0f);
        }
    }
    
    private final void handlePlayerTurnLabels(GameStateData state, boolean isSplitMode) {
        // Show player turn labels
        resultLabel.setOpacity(0f);
        waitingLabel.setOpacity(0f);
        
        if (isSplitMode) {
            playerTotalLabel.setOpacity(0f);

            statusLabel.setText(Strings.format("blackjack.split_hand_status", state.currentSplitIndex + 1, state.splitHands.size()));
            statusLabel.setColor(Color.CYAN);
            statusLabel.setOpacity(1f);
            
            final int totalHands = state.splitHands.size();
            for (int h = 0; h < totalHands && h < HAND_SIZE; h++) {
                final Hand hand = state.splitHands.get(h);
                if (hand == null) continue;
                
                splitHandValueLabels[h].setText(getHandValueText(hand));
                splitHandValueLabels[h].setColor(hand.isBust() ? Color.RED : COLOR_PLAYER);
                splitHandValueLabels[h].setOpacity(1f);
                splitHandResultLabels[h].setOpacity(0f);
            }
        } else {
            statusLabel.setOpacity(0f);
            hideSplitLabels();
        }
    }
    
    private final void handleDealerTurnLabels() {
        resultLabel.setOpacity(0f);
        statusLabel.setOpacity(0f);
        waitingLabel.setText(Strings.get("blackjack.dealer_playing"));
        waitingLabel.setOpacity(1f);
        hideSplitLabels();
    }
    
    private final void hideAllDynaLbls() {
        resultLabel.setOpacity(0f);
        statusLabel.setOpacity(0f);
        waitingLabel.setOpacity(0f);
        hideSplitLabels();
    }
    
    private final void hideSplitLabels() { // Hide split hand value and result labels
        for (int h = 0; h < HAND_SIZE; h++) {
            splitHandValueLabels[h].setOpacity(0f);
            splitHandResultLabels[h].setOpacity(0f);
        }
    }
    
    private final String getHandValueText(Hand hand) {
        final int value = hand.getValue();
        return hand.isBust() ? Strings.get("blackjack.bust") : 
            (hand.isSoft() ? Strings.format("blackjack.player_total_soft", value) :
            Strings.format("blackjack.player_total", value));
    }
    
    private final String getResultText(int result) {
        if (result == 0) return Strings.get("blackjack.push");
        return Strings.format(result > 0 ? "blackjack.win_stargems" : "blackjack.lose_stargems", Math.abs(result));
    }
    
    private final Color getResultColor(int result) { // Get color for result display
        if (result > 0) return Color.GREEN;
        if (result < 0) return Color.RED;
        return Color.YELLOW;
    }

    private final void updateDealerTotal() {
        if (game == null) return;

        final Hand dealerHand = game.getDealerHand();
        if (dealerHand == null || dealerHand.cards.isEmpty()) {
            dealerTotalLabel.setOpacity(0f);
            return;
        }

        final boolean revealed = game.isDealerHoleCardRevealed();
        if (revealed) {
            final int total = dealerHand.getValue();
            final String totalText = Strings.format("blackjack.dealer_total", total);
            dealerTotalLabel.setText(totalText);
            dealerTotalLabel.setOpacity(1f);
        } else {
            final int visibleValue = dealerHand.cards.get(0).value();
            final String totalText = Strings.format("blackjack.dealer_showing", visibleValue);
            dealerTotalLabel.setText(totalText);
            dealerTotalLabel.setOpacity(1f);
        }
    }

    private final void updatePlayerTotal() {
        if (game == null) return;

        final GameStateData state = game.getState();
        if (state != null && state.splitHands != null && !state.splitHands.isEmpty()) {
            playerTotalLabel.setOpacity(0f);
            return;
        }

        final Hand playerHand = game.getPlayerHand();
        if (playerHand == null || playerHand.cards.isEmpty()) {
            playerTotalLabel.setOpacity(0f);
            return;
        }

        final int total = playerHand.getValue();
        final boolean soft = playerHand.isSoft();
        final String totalText = soft ?
            Strings.format("blackjack.player_total_soft", total) :
            Strings.format("blackjack.player_total", total);
        playerTotalLabel.setText(totalText);
        playerTotalLabel.setOpacity(1f);
    }

    public final void renderBelow(float alphaMult) {
        final PositionAPI pos = panel.getPosition();
        final float x = pos.getX();
        final float y = pos.getY();
        final float w = pos.getWidth();
        final float h = pos.getHeight();
        final float cx = pos.getCenterX();

        CardGameUI.renderTableBackground(x, y, w, h, alphaMult);

        if (game == null) return;

        updateLabels();
        updateButtonVisibility();

        final float playerCardBottomY = h * 0.25f - CARD_HEIGHT / 2f;
        final float dealerCardBottomY = h * 0.75f - CARD_HEIGHT / 2f;

        if (game.getDealerHand() != null && !game.getDealerHand().cards.isEmpty()) {
            renderDealerCards(cx, y + dealerCardBottomY, game.getDealerHand(), alphaMult);
        }

        final GameStateData state = game.getState();
        if (state != null && !state.splitHands.isEmpty()) {
            renderSplitHands(cx, y + playerCardBottomY, state, alphaMult);
        } else if (game.getPlayerHand() != null && !game.getPlayerHand().cards.isEmpty()) {
            renderPlayerCards(cx, y + playerCardBottomY, game.getPlayerHand(), alphaMult);
        }
    }

    private final void renderPlayerCards(float cx, float cardY, Hand hand, float alphaMult) {
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

    private final void renderSplitHands(float cx, float cardY, GameStateData state, float alphaMult) {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        final int numHands = state.splitHands.size();
        if (numHands == 0) return;

        final float splitSpacing = CARD_WIDTH * 3f;
        final float totalWidth = numHands * CARD_WIDTH * 2 + (numHands - 1) * splitSpacing;
        final float startX = cx - totalWidth / 2f;

        for (int h = 0; h < numHands; h++) {
            final Hand hand = state.splitHands.get(h);
            if (hand == null || hand.cards.isEmpty()) continue;

            final float handX = startX + h * (CARD_WIDTH * 2 + splitSpacing);

            for (int i = 0; i < hand.cards.size(); i++) {
                final Card card = hand.cards.get(i);
                final float cardX = handX + i * (CARD_WIDTH + CARD_SPACING / 2f);
                final int animIndex = h * 5 + i;
                final CardFlipAnimation anim = animIndex < playerCardAnimations.length ? playerCardAnimations[animIndex] : null;
                CardGameUI.renderCardAnimated(cardX, cardY, card, anim, alphaMult);
            }
        }
    }

    private final void renderDealerCards(float cx, float cardY, Hand hand, float alphaMult) {
        if (hand == null || hand.cards.isEmpty()) return;

        final int numCards = hand.cards.size();
        final float totalWidth = numCards * CARD_WIDTH + (numCards - 1) * CARD_SPACING;
        final float startX = cx - totalWidth / 2f;

        final boolean revealed = game.isDealerHoleCardRevealed();

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        for (int i = 0; i < numCards; i++) {
            final Card card = hand.cards.get(i);
            final float cardX = startX + i * (CARD_WIDTH + CARD_SPACING);

            if (revealed || i > 0) {
                CardFlipAnimation anim = i < dealerCardAnimations.length ? dealerCardAnimations[i] : null;
                CardGameUI.renderCardAnimated(cardX, cardY, card, anim, alphaMult);
            } else {
                CardGameUI.renderCardFaceDown(cardX, cardY, alphaMult);
            }
        }
    }

    /**
     * Again, the advance method gets called by the panel, which owns the position.
     * So it always exists in this context.
     * 
     * @author WolframSegler
     * TODO remove message after reading
     */

    public final void advance(float amount) {
        for (int i = 0; i < 10; i++) {
            playerCardAnimations[i].advance(amount);
            dealerCardAnimations[i].advance(amount);
        }
    }

    public final void updateGameState(BlackjackGame game) {
        this.game = game;

        final GameStateData state = game.getState();
        if (state == null) return;

        final boolean isSplitMode = !state.splitHands.isEmpty();
        
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
            for (int h = 0; h < state.splitHands.size() && h < HAND_SIZE; h++) {
                final Hand hand = state.splitHands.get(h);
                if (hand == null) continue;
                
                final int currentCount = hand.cards.size();
                final int lastCount = lastSplitHandCardCounts[h];
                
                if (currentCount > lastCount) {
                    for (int i = lastCount; i < currentCount && i < 5; i++) {
                        final int idx = h * 5 + i;
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
            final int currentPlayerCards = state.playerHand != null ? state.playerHand.cards.size() : 0;

            if (currentPlayerCards > lastPlayerCardCount) {
                for (int i = lastPlayerCardCount; i < currentPlayerCards && i < 10; i++) {
                    playerCardAnimations[i].triggerFlip((i - lastPlayerCardCount) * CardFlipAnimation.STAGGER_DELAY);
                }
            }
            lastPlayerCardCount = currentPlayerCards;
            lastSplitHandCardCounts[0] = 0;
            lastSplitHandCardCounts[1] = 0;
        }

        final int currentDealerCards = state.dealerHand != null ? state.dealerHand.cards.size() : 0;
        if (currentDealerCards > lastDealerCardCount) {
            for (int i = lastDealerCardCount; i < currentDealerCards && i < 10; i++) {
                dealerCardAnimations[i].triggerFlip((i - lastDealerCardCount) * CardFlipAnimation.STAGGER_DELAY);
            }
        }
        lastDealerCardCount = currentDealerCards;

        final boolean holeRevealed = state.dealerHoleCardRevealed;
        if (holeRevealed && !lastDealerHoleRevealed) {
            dealerCardAnimations[0].reset();
            dealerCardAnimations[0].triggerFlip(0f);
        }
        lastDealerHoleRevealed = holeRevealed;
    }

    @Override
    public void actionPerformed(Object input, Object source) {
        if (source instanceof ButtonAPI btn) {
            processAction(btn.getCustomData());
        }
        updateButtonVisibility();
    }

    private void processAction(Object data) {
        if (data == null) return;

        if (data == BJ_HIT) {
            actionCallback.onPlayerAction(Action.HIT);
            return;
        }
        if (data == BJ_STAND) {
            actionCallback.onPlayerAction(Action.STAND);
            return;
        }
        if (data == BJ_DOUBLE) {
            actionCallback.onPlayerAction(Action.DOUBLE_DOWN);
            return;
        }
        if (data == BJ_SPLIT) {
            actionCallback.onPlayerAction(Action.SPLIT);
            return;
        }
        if (data == BJ_NEW_HAND) {
            actionCallback.onNewHand();
            return;
        }
        if (data == BJ_LEAVE) {
            actionCallback.onLeave();
            return;
        }
        if (data == BJ_HOW_TO_PLAY) {
            actionCallback.onHowToPlay();
            return;
        }
        if (data == BJ_OVERDRAFT_ON) {
            if (game != null) {
                game.getState().overdraftEnabled = false;
                updateButtonVisibility();
            }
            return;
        }
        if (data == BJ_OVERDRAFT_OFF) {
            if (game != null) {
                game.getState().overdraftEnabled = true;
                updateButtonVisibility();
            }
            return;
        }

        if (data instanceof String strData && strData.contains(BJ_BET) && game != null) {
            final int amount = Integer.parseInt(strData.replaceAll(".*?(\\d+)$", "$1"));
            final GameStateData state = game.getState();
            
            final int effectiveBalance = state.overdraftEnabled ? state.playerStack : state.originalBalance;
            if (effectiveBalance >= amount) {
                actionCallback.onPlaceBet(amount);
            }
        }
    }
}