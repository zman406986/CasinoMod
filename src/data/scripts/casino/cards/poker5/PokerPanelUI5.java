package data.scripts.casino.cards.poker5;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate.DialogCallbacks;

import data.scripts.casino.Strings;
import data.scripts.casino.cards.Card;
import data.scripts.casino.cards.CardFlipAnimation;
import data.scripts.casino.shared.BaseCardGamePanelUI;
import data.scripts.casino.shared.CardRenderingUtils;
import data.scripts.casino.cards.poker5.PokerGame5.PokerState5;
import data.scripts.casino.cards.pokerShared.PokerAction;
import data.scripts.casino.cards.pokerShared.PokerHandEvaluator;
import data.scripts.casino.cards.pokerShared.PokerUIUtils;
import data.scripts.casino.cards.pokerShared.PokerRound;

import static data.scripts.casino.shared.CardRenderingUtils.*;

public class PokerPanelUI5 extends BaseCardGamePanelUI<PokerGame5> {

    private static final String POKER_FOLD = "poker5_fold";
    private static final String POKER_CHECK_CALL = "poker5_check_call";
    private static final String POKER_RAISE_PREFIX = "poker5_raise_";
    private static final String POKER_NEXT_HAND = "poker5_next_hand";
    private static final String POKER_SUSPEND = "poker5_suspend";
    private static final String POKER_HOW_TO_PLAY = "poker5_how_to_play";
    private static final String POKER_FLIP_TABLE = "poker5_flip_table";

    private static final float RAISE_BUTTON_WIDTH = 180f;
    private static final int HAND_SIZE = 2;
    private static final int MAX_COMMUNITY_CARDS = 5;
    private static final int NUM_OPPONENTS = PokerGame5.NUM_PLAYERS - 1;

    private static final Color COLOR_PLAYER = new Color(100, 200, 255);
    private static final Color COLOR_OPPONENT = new Color(255, 150, 100);
    private static final Color COLOR_FOLDED = new Color(100, 100, 100);
    private static final Color COLOR_ALL_IN = new Color(255, 200, 50);
    private static final Color COLOR_CURRENT_TURN = new Color(255, 255, 100);
    private static final Color COLOR_HIGHLIGHT = new Color(255, 255, 100);

    private final PokerActionCallback5 actionCallback;

    private boolean waitingForAI = false;
    private float aiThinkTimer = 0f;
    private int currentAITurn = -1;
    private boolean skipRequested = false;
    private boolean wasMousePressed = false;
    private boolean justStartedWaiting = false;
    private static final float AI_THINK_DELAY = 5.0f;

    private final CardFlipAnimation[] playerCardAnimations = new CardFlipAnimation[HAND_SIZE];
    private final CardFlipAnimation[][] opponentCardAnimations = new CardFlipAnimation[NUM_OPPONENTS][HAND_SIZE];
    private final CardFlipAnimation[] communityCardAnimations = new CardFlipAnimation[MAX_COMMUNITY_CARDS];

    private PokerRound lastAnimatedRound = null;
    private int lastAnimatedCommunityCount = 0;
    private boolean playerCardsAnimated = false;

    private final List<ButtonAPI> raiseOptionButtons = new ArrayList<>();
    private ButtonAPI flipTableButton;
    private ButtonAPI foldBtn;
    private ButtonAPI checkCallBtn;
    private ButtonAPI nextHandBtn;

    private LabelAPI roundLabel;
    private LabelAPI waitingLabel;
    private LabelAPI resultLabel;

    private final LabelAPI[] playerNameLabels = new LabelAPI[NUM_OPPONENTS];
    private final LabelAPI[] opponentStackLabels = new LabelAPI[NUM_OPPONENTS];
    private final LabelAPI[] opponentActionLabels = new LabelAPI[NUM_OPPONENTS];
    private final LabelAPI[] opponentBetLabels = new LabelAPI[NUM_OPPONENTS];
    private LabelAPI playerStackLabel;
    private LabelAPI playerBetLabel;
    private LabelAPI playerHandRankLabel;

    private int lastPot = -1;
    private PokerRound lastRound = null;
    private int lastBigBlind = -1;
    private boolean resultLblCached = false;
    private int lastCallAmount = -1;
    private String cachedCheckCallLabel = null;
    private int lastCommunityCardCount = -1;
    private float cachedCommunityStartX = 0f;
    private float cachedCommunityY = 0f;
    private float cachedPlayerStartX = 0f;
    private float cachedPlayerY = 0f;

    public interface PokerActionCallback5 {
        void onPlayerAction(PokerAction action, int raiseAmount);
        void onNextHand();
        void onSuspend();
        void onHowToPlay();
        void onFlipTable();
        void processAITurns();
    }

    public PokerPanelUI5(PokerGame5 game, PokerActionCallback5 callback) {
        super(game);
        this.actionCallback = callback;

        for (int i = 0; i < HAND_SIZE; i++) {
            playerCardAnimations[i] = new CardFlipAnimation();
        }
        for (int i = 0; i < NUM_OPPONENTS; i++) {
            for (int j = 0; j < HAND_SIZE; j++) {
                opponentCardAnimations[i][j] = new CardFlipAnimation();
            }
        }
        for (int i = 0; i < MAX_COMMUNITY_CARDS; i++) {
            communityCardAnimations[i] = new CardFlipAnimation();
        }
    }

    @Override
    public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
        super.init(panel, callbacks);
        waitingForAI = false;
        aiThinkTimer = 0f;
        currentAITurn = -1;
        skipRequested = false;
        wasMousePressed = false;
    }

    @Override
    protected void createButtonsInInit() {
        if (buttonsCreated) return;

        final PositionAPI pos = panel.getPosition();
        final TooltipMakerAPI btnTp = panel.createUIElement(pos.getWidth(), pos.getHeight(), false);
        btnTp.setActionListenerDelegate(this);
        panel.addUIElement(btnTp).inBL(0f, 0f);

        createTopRightButtons(btnTp);
        createCenterLabels();
        createOpponentLabels();
        createPlayerStackLabel();
        createNextHandButton();

        foldBtn = btnTp.addButton(Strings.get("poker_panel.fold_btn"), POKER_FOLD, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        foldBtn.setQuickMode(true);
        foldBtn.setShortcut(Keyboard.KEY_F, false);
        foldBtn.getPosition().inTL(0, 0);
        foldBtn.setOpacity(0f);

        checkCallBtn = btnTp.addButton(Strings.get("poker_panel.check_btn"), POKER_CHECK_CALL, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        checkCallBtn.setQuickMode(true);
        checkCallBtn.setShortcut(Keyboard.KEY_C, false);
        checkCallBtn.getPosition().inTL(0, 0);
        checkCallBtn.setOpacity(0f);

        for (int i = 0; i < 5; i++) {
            final ButtonAPI btn = btnTp.addButton("", POKER_RAISE_PREFIX + "0", RAISE_BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
            btn.setQuickMode(true);
            btn.setCustomData(POKER_RAISE_PREFIX + "0");
            btn.getPosition().inTL(0, 0);
            btn.setOpacity(0f);
            raiseOptionButtons.add(btn);
        }

        buttonsCreated = true;
    }

    private void createTopRightButtons(TooltipMakerAPI btnTp) {
        flipTableButton = btnTp.addButton(Strings.get("poker_panel.run_away"), POKER_FLIP_TABLE, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        flipTableButton.setQuickMode(true);
        flipTableButton.setShortcut(Keyboard.KEY_ESCAPE, false);
        flipTableButton.getPosition().inTL(PANEL_WIDTH - BUTTON_WIDTH - MARGIN, MARGIN);

        final ButtonAPI suspendBtn = btnTp.addButton(Strings.get("poker_panel.wait"), POKER_SUSPEND, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        suspendBtn.setQuickMode(true);
        suspendBtn.getPosition().inTL(PANEL_WIDTH - BUTTON_WIDTH * 2 - BUTTON_SPACING - MARGIN, MARGIN);

        final ButtonAPI helpBtn = btnTp.addButton(Strings.get("poker_panel.how_to_play"), POKER_HOW_TO_PLAY, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        helpBtn.setQuickMode(true);
        helpBtn.getPosition().inTL(PANEL_WIDTH - BUTTON_WIDTH * 3 - BUTTON_SPACING * 2 - MARGIN, MARGIN);
    }

    private void createCenterLabels() {
        final float centerX = PANEL_WIDTH / 2f;

        roundLabel = settings.createLabel(PokerUIUtils.formatRoundProgress(PokerRound.PREFLOP, 0, 50), Fonts.DEFAULT_SMALL);
        roundLabel.setColor(PokerUIUtils.COLOR_ROUND_PREFLOP);
        roundLabel.setAlignment(Alignment.MID);
        panel.addComponent((UIComponentAPI) roundLabel)
            .inTL(centerX - 300f, PANEL_HEIGHT * 0.42f)
            .setSize(600f, 25f);

        waitingLabel = settings.createLabel("", Fonts.DEFAULT_SMALL);
        waitingLabel.setColor(COLOR_CURRENT_TURN);
        waitingLabel.setAlignment(Alignment.MID);
        panel.addComponent((UIComponentAPI) waitingLabel)
            .inTL(centerX - 125f, PANEL_HEIGHT * 0.38f)
            .setSize(250f, 20f);
        waitingLabel.setOpacity(0f);

        resultLabel = settings.createLabel("", Fonts.DEFAULT_SMALL);
        resultLabel.setColor(Color.WHITE);
        resultLabel.setAlignment(Alignment.MID);
        panel.addComponent((UIComponentAPI) resultLabel)
            .inTL(centerX - 225f, PANEL_HEIGHT * 0.35f)
            .setSize(450f, 25f);
        resultLabel.setOpacity(0f);
    }

    private void createOpponentLabels() {
        final float centerX = PANEL_WIDTH / 2f;
        final float arcRadiusX = PANEL_WIDTH * 0.38f;
        final float arcLabelTopY = PANEL_HEIGHT * 0.12f;
        final float arcLabelBottomY = PANEL_HEIGHT * 0.22f;
        final float cornerLabelY = PANEL_HEIGHT * 0.455f;

        for (int i = 0; i < NUM_OPPONENTS; i++) {
            final float t = (float) i / (NUM_OPPONENTS - 1);
            final float angle = (float) (Math.PI * (0.15 + 0.7 * t));

            final float x = centerX - arcRadiusX * (float) Math.cos(angle);
            final float y = (i == 0 || i == 3) ? cornerLabelY : arcLabelBottomY - (arcLabelBottomY - arcLabelTopY) * (1f - (float) Math.sin(angle));

            playerNameLabels[i] = settings.createLabel("", Fonts.DEFAULT_SMALL);
            playerNameLabels[i].setColor(COLOR_OPPONENT);
            playerNameLabels[i].setAlignment(Alignment.MID);
            panel.addComponent((UIComponentAPI) playerNameLabels[i])
                .inTL(x - 70f, y)
                .setSize(140f, 16f);

            opponentStackLabels[i] = settings.createLabel("", Fonts.DEFAULT_SMALL);
            opponentStackLabels[i].setColor(COLOR_OPPONENT);
            opponentStackLabels[i].setAlignment(Alignment.MID);
            panel.addComponent((UIComponentAPI) opponentStackLabels[i])
                .inTL(x - 70f, y + 16f)
                .setSize(140f, 16f);

            opponentActionLabels[i] = settings.createLabel("", Fonts.DEFAULT_SMALL);
            opponentActionLabels[i].setColor(Color.YELLOW);
            opponentActionLabels[i].setAlignment(Alignment.MID);
            panel.addComponent((UIComponentAPI) opponentActionLabels[i])
                .inTL(x - 70f, y + 32f)
                .setSize(140f, 16f);

            opponentBetLabels[i] = settings.createLabel("", Fonts.DEFAULT_SMALL);
            opponentBetLabels[i].setColor(new Color(200, 200, 100));
            opponentBetLabels[i].setAlignment(Alignment.MID);
            panel.addComponent((UIComponentAPI) opponentBetLabels[i])
                .inTL(x - 70f, y + 48f)
                .setSize(140f, 16f);
        }
    }

    private void createPlayerStackLabel() {
        final float centerX = PANEL_WIDTH / 2f;
        final float cardRightEdge = centerX + (HAND_SIZE * CARD_WIDTH + CARD_SPACING) / 2f;
        final float labelX = cardRightEdge + 10f;
        final float cardCenterY = PANEL_HEIGHT * 0.75f;

        playerHandRankLabel = settings.createLabel("", Fonts.DEFAULT_SMALL);
        playerHandRankLabel.setColor(Color.WHITE);
        playerHandRankLabel.setAlignment(Alignment.LMID);
        panel.addComponent((UIComponentAPI) playerHandRankLabel)
            .inTL(labelX, cardCenterY - 32f)
            .setSize(150f, 18f);
        playerHandRankLabel.setOpacity(0f);

        playerStackLabel = settings.createLabel("", Fonts.DEFAULT_SMALL);
        playerStackLabel.setColor(COLOR_PLAYER);
        playerStackLabel.setAlignment(Alignment.LMID);
        panel.addComponent((UIComponentAPI) playerStackLabel)
            .inTL(labelX, cardCenterY - 10f)
            .setSize(150f, 20f);

        playerBetLabel = settings.createLabel("", Fonts.DEFAULT_SMALL);
        playerBetLabel.setColor(Color.YELLOW);
        playerBetLabel.setAlignment(Alignment.LMID);
        panel.addComponent((UIComponentAPI) playerBetLabel)
            .inTL(labelX, cardCenterY + 12f)
            .setSize(150f, 16f);
    }

    private void createNextHandButton() {
        final float BUTTON_W = 120f;
        final float BUTTON_H = 30f;
        final float centerX = PANEL_WIDTH / 2f;

        final TooltipMakerAPI tp = panel.createUIElement(BUTTON_W, BUTTON_H, false);
        tp.setActionListenerDelegate(this);
        nextHandBtn = tp.addButton(Strings.get("poker_panel.next_hand_btn"), POKER_NEXT_HAND, BUTTON_W, BUTTON_H, 0f);
        nextHandBtn.setQuickMode(true);
        panel.addUIElement(tp).inTL(centerX - BUTTON_W / 2f, PANEL_HEIGHT - BUTTON_H - MARGIN);
        nextHandBtn.setOpacity(0f);
    }

    @Override
    protected void updateButtonVisibility() {
        if (game == null) return;
        final PokerState5 state = game.getState();

        final boolean isShowdown = state.round == PokerRound.SHOWDOWN;
        flipTableButton.setText(isShowdown ? Strings.get("poker_panel.leave_btn") : Strings.get("poker_panel.run_away"));

        final int callAmount = game.getCallAmount(PokerGame5.HUMAN_PLAYER_INDEX);
        final boolean canRaise = state.stacks[PokerGame5.HUMAN_PLAYER_INDEX] > 0 &&
                                  !state.declaredAllIn[PokerGame5.HUMAN_PLAYER_INDEX] &&
                                  !state.foldedPlayers.contains(PokerGame5.HUMAN_PLAYER_INDEX);
        final boolean isPlayerTurn = state.currentPlayerIndex == PokerGame5.HUMAN_PLAYER_INDEX &&
                                       state.round != PokerRound.SHOWDOWN &&
                                       game.canAct(PokerGame5.HUMAN_PLAYER_INDEX);

        final float bottomY = PANEL_HEIGHT - BUTTON_HEIGHT - MARGIN;
        final float totalActionWidth = BUTTON_WIDTH * 2 + BUTTON_SPACING;
        final float actionStartX = (PANEL_WIDTH - totalActionWidth) / 2f;

        if (isPlayerTurn && !waitingForAI) {
            foldBtn.getPosition().inTL(actionStartX, bottomY);
            foldBtn.setOpacity(1f);

            if (callAmount != lastCallAmount) {
                lastCallAmount = callAmount;
                cachedCheckCallLabel = callAmount > 0 ?
                    Strings.format("poker_panel.call_btn", callAmount) : Strings.get("poker_panel.check_btn");
            }
            checkCallBtn.setText(cachedCheckCallLabel);
            checkCallBtn.getPosition().inTL(actionStartX + BUTTON_WIDTH + BUTTON_SPACING, bottomY);
            checkCallBtn.setOpacity(1f);
        } else {
            foldBtn.setOpacity(0f);
            checkCallBtn.setOpacity(0f);
        }

        if (canRaise && isPlayerTurn && !waitingForAI) {
            final float[] raiseAmounts = getRaiseOptions(state);
            final float raiseY = bottomY - BUTTON_HEIGHT - BUTTON_SPACING;
            final float totalRaiseWidth = RAISE_BUTTON_WIDTH * raiseAmounts.length + BUTTON_SPACING * (raiseAmounts.length - 1);
            final float raiseStartX = (PANEL_WIDTH - totalRaiseWidth) / 2f;

            for (int i = 0; i < raiseOptionButtons.size(); i++) {
                final ButtonAPI btn = raiseOptionButtons.get(i);
                if (i < raiseAmounts.length) {
                    final int amt = (int) raiseAmounts[i];
                    final String label = formatRaiseLabel(amt, game.getBigBlindAmount());
                    final String btnId = POKER_RAISE_PREFIX + amt;

                    btn.setText(label);
                    btn.setCustomData(btnId);
                    btn.getPosition().inTL(raiseStartX + (RAISE_BUTTON_WIDTH + BUTTON_SPACING) * i, raiseY);
                    btn.setOpacity(1f);
                } else {
                    btn.setOpacity(0f);
                }
            }
        } else {
            for (ButtonAPI btn : raiseOptionButtons) {
                btn.setOpacity(0f);
            }
        }

        updateNextHandButton(state);
    }

    private final int[] raiseOptionsBuffer = new int[5];

    private float[] getRaiseOptions(PokerState5 state) {
        int count = 0;
        final int pot = state.pot;
        final int stack = state.stacks[PokerGame5.HUMAN_PLAYER_INDEX];
        final int currentBet = game.getCurrentBet();
        final int playerBet = state.bets[PokerGame5.HUMAN_PLAYER_INDEX];
        final int maxBet = playerBet + stack;

        final int minRaise = game.getMinRaiseAmount();
        if (minRaise <= maxBet && PokerUIUtils.notContainsInt(raiseOptionsBuffer, count, minRaise)) {
            raiseOptionsBuffer[count++] = minRaise;
        }

        final int halfPotBet = currentBet + pot / 2;
        if (halfPotBet > minRaise && halfPotBet <= maxBet && PokerUIUtils.notContainsInt(raiseOptionsBuffer, count, halfPotBet)) {
            raiseOptionsBuffer[count++] = halfPotBet;
        }

        final int potBet = currentBet + pot;
        if (potBet > minRaise && potBet <= maxBet && PokerUIUtils.notContainsInt(raiseOptionsBuffer, count, potBet)) {
            raiseOptionsBuffer[count++] = potBet;
        }

        final int threePotBet = currentBet + pot * 3;
        if (threePotBet > potBet && threePotBet <= maxBet && PokerUIUtils.notContainsInt(raiseOptionsBuffer, count, threePotBet)) {
            raiseOptionsBuffer[count++] = threePotBet;
        }

        final int allInBet = playerBet + stack;
        if (stack > 0 && PokerUIUtils.notContainsInt(raiseOptionsBuffer, count, allInBet)) {
            raiseOptionsBuffer[count++] = allInBet;
        }

        final float[] result = new float[count];
        for (int i = 0; i < count; i++) {
            result[i] = raiseOptionsBuffer[i];
        }
        return result;
    }

    private String formatRaiseLabel(int totalBet, int bigBlind) {
        final float bbAmount = bigBlind > 0 ? (float) totalBet / bigBlind : 0;
        return Strings.format("poker_raise_labels.format", Strings.get("poker_raise_labels.raise"), totalBet, bbAmount);
    }

    private void updateNextHandButton(PokerState5 state) {
        final boolean atShowdown = state.round == PokerRound.SHOWDOWN;
        final boolean canContinue = state.stacks[PokerGame5.HUMAN_PLAYER_INDEX] > 0;

        nextHandBtn.setOpacity(atShowdown && canContinue ? 1f : 0f);
        nextHandBtn.setClickable(atShowdown && canContinue);
    }

    public final void renderBelow(float alphaMult) {
        final PositionAPI pos = panel.getPosition();
        final float x = pos.getX();
        final float y = pos.getY();
        final float w = pos.getWidth();
        final float h = pos.getHeight();
        final float cx = pos.getCenterX();
        final float cy = pos.getCenterY();

        CardRenderingUtils.renderTableBackground(x, y, w, h, alphaMult);

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        final PokerState5 state = game.getState();

        updateLabels(state);
        updateResultLabel(state);
        updateButtonVisibility();

        renderCommunityCards(cx, cy, state.communityCards, alphaMult);

        final float playerCardY = y + h * 0.25f - CARD_HEIGHT / 2f;
        renderPlayerHand(cx, playerCardY, state.hands[PokerGame5.HUMAN_PLAYER_INDEX], alphaMult);

        renderOpponentHands(y, w, h, cx, state, alphaMult);
    }

    private void updateLabels(PokerState5 state) {
        updateRoundLabel(state);
        updateWaitingLabel(state);
        updatePlayerLabel(state);
        updateOpponentLabels(state);
    }

    private void updateRoundLabel(PokerState5 state) {
        if (state.round == lastRound && state.pot == lastPot && game.getBigBlindAmount() == lastBigBlind) return;
        
        lastRound = state.round;
        lastPot = state.pot;
        lastBigBlind = game.getBigBlindAmount();

        roundLabel.setText(PokerUIUtils.formatRoundProgress(state.round, state.pot, game.getBigBlindAmount()));
        roundLabel.setColor(PokerUIUtils.getRoundColor(state.round));
    }

    private void updateWaitingLabel(PokerState5 state) {
        final int turnIdx = waitingForAI ? currentAITurn : state.currentPlayerIndex;
        final boolean shouldShow = turnIdx >= 0 && turnIdx != PokerGame5.HUMAN_PLAYER_INDEX && state.round != PokerRound.SHOWDOWN && game.canPlay(turnIdx);
        
        if (shouldShow) {
            final String baseStr = waitingForAI ? "poker_panel5.ai_thinking" : "poker_panel5.waiting_for";
            final String posName = game.getPositionName(turnIdx);
            final String oppName = Strings.format("poker5.opponent_name", turnIdx);
            waitingLabel.setText(Strings.format(baseStr, oppName, posName));
            waitingLabel.setOpacity(1f);
        } else {
            waitingLabel.setOpacity(0f);
        }
    }

    private Color getPlayerColor(boolean isFolded, boolean isCurrentTurn, boolean isAllIn, Color baseColor) {
        if (isFolded) return COLOR_FOLDED;
        if (isCurrentTurn) return COLOR_CURRENT_TURN;
        if (isAllIn) return COLOR_ALL_IN;
        return baseColor;
    }

    private void updatePlayerLabel(PokerState5 state) {
        final int playerIdx = PokerGame5.HUMAN_PLAYER_INDEX;
        final int stack = state.stacks[playerIdx];
        final int bet = state.displayBets[playerIdx];
        final boolean isFolded = state.foldedPlayers.contains(playerIdx);
        final boolean isBust = state.round == PokerRound.SHOWDOWN && state.stacks[playerIdx] <= 0;

        final String playerName = Strings.get("poker5.you_name");
        final String posName = game.getPositionName(playerIdx);
        if (isBust) {
            playerStackLabel.setText(playerName + " [" + Strings.get("poker5.bust") + "] | " + Strings.format("poker_panel.stack", stack));
        } else {
            playerStackLabel.setText(Strings.format("poker_panel5.player_pos", playerName, posName) + " | " + Strings.format("poker_panel.stack", stack));
        }

        if (bet > 0) {
            playerBetLabel.setText(Strings.format("poker_panel.bet", bet));
            playerBetLabel.setOpacity(1f);
        } else {
            playerBetLabel.setOpacity(0f);
        }

        final PokerHandEvaluator.HandRank handRank = state.handRanks != null ? state.handRanks[playerIdx] : null;
        if (!isFolded && handRank != null) {
            playerHandRankLabel.setText(formatHandRank(handRank));
            final boolean isWinner = state.round == PokerRound.SHOWDOWN && isPlayerWinner(state.winners, playerIdx);
            playerHandRankLabel.setColor(isWinner ? Color.GREEN : Color.WHITE);
            playerHandRankLabel.setOpacity(1f);
        } else {
            playerHandRankLabel.setOpacity(0f);
        }

        final boolean isCurrentTurn = state.currentPlayerIndex == playerIdx && state.round != PokerRound.SHOWDOWN;
        playerStackLabel.setColor(isBust ? COLOR_FOLDED : getPlayerColor(isFolded, isCurrentTurn, state.declaredAllIn[playerIdx], COLOR_PLAYER));
    }

    private String formatHandRank(PokerHandEvaluator.HandRank rank) {
        if (rank == null) return "";
        return switch (rank) {
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

    private void updateOpponentLabels(PokerState5 state) {
        for (int i = 0; i < NUM_OPPONENTS; i++) {
            final int playerIdx = i + 1;
            final boolean isFolded = state.foldedPlayers.contains(playerIdx);
            final boolean isAllIn = state.declaredAllIn[playerIdx];
            final boolean isCurrentTurn = state.currentPlayerIndex == playerIdx && state.round != PokerRound.SHOWDOWN;
            final int bet = state.displayBets[playerIdx];
            final PokerHandEvaluator.HandRank handRank = state.handRanks != null ? state.handRanks[playerIdx] : null;
            final boolean isBust = state.round == PokerRound.SHOWDOWN && state.stacks[playerIdx] <= 0;

            final String oppNameLabel = Strings.format("poker5.opponent_name", i + 1);
            final String posName = game.getPositionName(playerIdx);
            if (isBust) {
                playerNameLabels[i].setText(oppNameLabel + " [" + Strings.get("poker5.bust") + "]");
                playerNameLabels[i].setColor(COLOR_FOLDED);
            } else {
                playerNameLabels[i].setText(oppNameLabel + " [" + posName + "]");
                playerNameLabels[i].setColor(getPlayerColor(isFolded, isCurrentTurn, isAllIn, COLOR_OPPONENT));
            }

            final int stack = state.stacks[playerIdx];
            opponentStackLabels[i].setText(Strings.format("poker_panel.stack", stack));
            opponentStackLabels[i].setColor(isBust || isFolded ? COLOR_FOLDED : playerNameLabels[i].getColor());

            if (isBust) {
                opponentActionLabels[i].setText(Strings.get("poker5.bust"));
                opponentActionLabels[i].setColor(COLOR_FOLDED);
                opponentActionLabels[i].setOpacity(1f);
            } else if (isFolded) {
                opponentActionLabels[i].setText(Strings.get("poker_panel5.action_fold"));
                opponentActionLabels[i].setColor(COLOR_FOLDED);
                opponentActionLabels[i].setOpacity(1f);
            } else if (state.round == PokerRound.SHOWDOWN && handRank != null) {
                opponentActionLabels[i].setText(formatHandRank(handRank));
                final boolean isWinner = isPlayerWinner(state.winners, playerIdx);
                opponentActionLabels[i].setColor(isWinner ? Color.GREEN : Color.YELLOW);
                opponentActionLabels[i].setOpacity(1f);
            } else if (state.lastPokerActions != null && state.lastPokerActions[playerIdx] != null) {
                opponentActionLabels[i].setText(state.lastPokerActions[playerIdx]);
                opponentActionLabels[i].setColor(Color.YELLOW);
                opponentActionLabels[i].setOpacity(1f);
            } else if (isAllIn) {
                opponentActionLabels[i].setText(Strings.get("poker_panel5.all_in"));
                opponentActionLabels[i].setColor(COLOR_ALL_IN);
                opponentActionLabels[i].setOpacity(1f);
            } else {
                opponentActionLabels[i].setOpacity(0f);
            }

            if (bet > 0 && !isFolded && !isBust) {
                opponentBetLabels[i].setText(Strings.format("poker_panel.bet", bet));
                opponentBetLabels[i].setOpacity(1f);
            } else {
                opponentBetLabels[i].setOpacity(0f);
            }
        }
    }

    private void updateResultLabel(PokerState5 state) {
        if (state.round != PokerRound.SHOWDOWN) {
            resultLabel.setOpacity(0f);
            resultLblCached = false;
            return;
        }

        if (resultLblCached) return;

        final int potWon = state.lastPotWon;
        Color resultColor = Color.WHITE;
        String resultText;

        if (state.foldWinner >= 0) {
            boolean playerWon = state.foldWinner == PokerGame5.HUMAN_PLAYER_INDEX;
            if (playerWon) {
                resultText = Strings.format("poker_panel5.fold_win", potWon);
                resultColor = Color.GREEN;
            } else {
                resultText = Strings.format("poker_panel5.fold_lose", Strings.format("poker5.opponent_name", state.foldWinner), potWon);
                resultColor = COLOR_OPPONENT;
            }
        } else if (state.winners.length > 0) {
            boolean playerWon = false;
            for (int i = 0; i < state.winners.length && !playerWon; i++) {
                playerWon = state.winners[i] == PokerGame5.HUMAN_PLAYER_INDEX;
            }

            if (playerWon) {
                resultText = Strings.format("poker_panel.you_win_stargems", potWon);
                resultColor = Color.GREEN;
            } else {
                final StringBuilder winnerNames = new StringBuilder();
                final String youName = Strings.get("poker5.you_name");
                for (int w : state.winners) {
                    if (!winnerNames.isEmpty()) winnerNames.append(", ");
                    winnerNames.append(w == PokerGame5.HUMAN_PLAYER_INDEX ? youName : Strings.format("poker5.opponent_name", w));
                }
                resultText = Strings.format("poker_panel5.winners_take", winnerNames.toString(), potWon);
                resultColor = COLOR_OPPONENT;
            }
        } else {
            resultText = Strings.get("poker_panel5.hand_complete");
        }

        if (state.stacks[PokerGame5.HUMAN_PLAYER_INDEX] <= 0) {
            resultText += Strings.get("poker_panel.you_bust_leave");
        }

        resultLblCached = true;
        resultLabel.setText(resultText);
        resultLabel.setColor(resultColor);
        resultLabel.setOpacity(1f);
    }

    private void renderCommunityCards(float cx, float cy, List<Card> cards, float alphaMult) {
        if (cards == null || cards.isEmpty()) return;

        final int numCards = cards.size();
        if (numCards != lastCommunityCardCount) {
            lastCommunityCardCount = numCards;
            final float totalWidth = numCards * CARD_WIDTH + (numCards - 1) * CARD_SPACING;
            cachedCommunityStartX = cx - totalWidth / 2f;
            cachedCommunityY = cy - CARD_HEIGHT / 2f - 30f;
        }

        for (int i = 0; i < numCards; i++) {
            final Card card = cards.get(i);
            final float cardX = cachedCommunityStartX + i * (CARD_WIDTH + CARD_SPACING);
            CardRenderingUtils.renderCardAnimated(cardX, cachedCommunityY, card, communityCardAnimations[i], alphaMult);
        }
    }

    private void renderPlayerHand(float cx, float y, List<Card> cards, float alphaMult) {
        if (cards == null || cards.isEmpty()) return;

        if (cachedPlayerY != y) {
            cachedPlayerY = y;
            final float totalWidth = HAND_SIZE * CARD_WIDTH + CARD_SPACING;
            cachedPlayerStartX = cx - totalWidth / 2f;
        }

        final PokerState5 state = game.getState();
        final int playerIdx = PokerGame5.HUMAN_PLAYER_INDEX;
        final int stack = state.stacks[playerIdx];
        final int bet = state.displayBets[playerIdx];
        final int maxStack = state.maxStack;

        CardRenderingUtils.renderStackBars(cachedPlayerStartX, cachedPlayerY, stack, bet, maxStack, alphaMult);

        final boolean isPlayerTurn = state.currentPlayerIndex == PokerGame5.HUMAN_PLAYER_INDEX &&
                                       state.round != PokerRound.SHOWDOWN;
        final boolean isWinner = state.round == PokerRound.SHOWDOWN && isPlayerWinner(state.winners);

        for (int i = 0; i < cards.size() && i < HAND_SIZE; i++) {
            final Card card = cards.get(i);
            final float cardX = cachedPlayerStartX + i * (CARD_WIDTH + CARD_SPACING);
            if (isPlayerTurn || isWinner) {
                CardRenderingUtils.renderCardHighlightBorder(cardX, cachedPlayerY, COLOR_HIGHLIGHT, alphaMult * 0.8f);
            }
            CardRenderingUtils.renderCardAnimated(cardX, cachedPlayerY, card, playerCardAnimations[i], alphaMult);
        }
    }

    private boolean isPlayerWinner(int[] winners) {
        for (int w : winners) {
            if (w == PokerGame5.HUMAN_PLAYER_INDEX) return true;
        }
        return false;
    }

    private boolean isPlayerWinner(int[] winners, int playerIdx) {
        for (int w : winners) {
            if (w == playerIdx) return true;
        }
        return false;
    }

    private void renderOpponentHands(float panelY, float panelW, float panelH, float cx, PokerState5 state, float alphaMult) {
        final float arcRadiusX = panelW * 0.38f;
        final float arcTopY = panelY + panelH * 0.88f;
        final float arcBottomY = panelY + panelH * 0.78f;

        final int currentActor = waitingForAI ? currentAITurn : state.currentPlayerIndex;

        for (int i = 0; i < NUM_OPPONENTS; i++) {
            final int playerIdx = i + 1;
            if (state.foldedPlayers.contains(playerIdx)) continue;

            final List<Card> cards = state.hands[playerIdx];
            if (cards == null || cards.isEmpty()) continue;

            final float t = (float) i / (NUM_OPPONENTS - 1);
            final float angle = (float) (Math.PI * (0.15 + 0.7 * t));

            final float handX = cx - arcRadiusX * (float) Math.cos(angle);
            final float handY = (i == 0 || i == 3) ? panelY + panelH * 0.55f : arcBottomY + (arcTopY - arcBottomY) * (1f - (float) Math.sin(angle));

            final float totalWidth = HAND_SIZE * CARD_WIDTH + CARD_SPACING;
            final float startX = handX - totalWidth / 2f;

            final int stack = state.stacks[playerIdx];
            final int bet = state.displayBets[playerIdx];
            final int maxStack = state.maxStack;

            CardRenderingUtils.renderStackBars(startX, handY, stack, bet, maxStack, alphaMult);

            final boolean showCards = state.round == PokerRound.SHOWDOWN && !state.foldedPlayers.contains(playerIdx);
            final boolean isOpponentTurn = currentActor == playerIdx && state.round != PokerRound.SHOWDOWN;

            for (int j = 0; j < cards.size() && j < HAND_SIZE; j++) {
                final Card card = cards.get(j);
                final float cardX = startX + j * (CARD_WIDTH + CARD_SPACING);
                final boolean isOpponentWinner = state.round == PokerRound.SHOWDOWN && isPlayerWinner(state.winners, playerIdx);
                if (isOpponentTurn || isOpponentWinner) {
                    CardRenderingUtils.renderCardHighlightBorder(cardX, handY, COLOR_HIGHLIGHT, alphaMult * 0.8f);
                }
                if (showCards) {
                    CardRenderingUtils.renderCardAnimated(cardX, handY, card, opponentCardAnimations[i][j], alphaMult);
                } else {
                    CardRenderingUtils.renderCardFaceDown(cardX, handY, alphaMult);
                }
            }
        }
    }

    public final void advance(float amount) {
        final boolean mouseDown = Mouse.isButtonDown(0);
        
        if (waitingForAI) {
            aiThinkTimer += amount;
            
            if (!justStartedWaiting) {
                if (mouseDown && !wasMousePressed) {
                    skipRequested = true;
                }
            } else {
                justStartedWaiting = false;
            }
            wasMousePressed = mouseDown;

            if (skipRequested || aiThinkTimer >= AI_THINK_DELAY) {
                waitingForAI = false;
                aiThinkTimer = 0f;
                skipRequested = false;
                wasMousePressed = false;
                currentAITurn = -1;
                actionCallback.processAITurns();
            }
        } else {
            wasMousePressed = mouseDown;
        }

        for (int i = 0; i < HAND_SIZE; i++) {
            playerCardAnimations[i].advance(amount);
        }
        for (int i = 0; i < NUM_OPPONENTS; i++) {
            for (int j = 0; j < HAND_SIZE; j++) {
                opponentCardAnimations[i][j].advance(amount);
            }
        }
        for (int i = 0; i < MAX_COMMUNITY_CARDS; i++) {
            communityCardAnimations[i].advance(amount);
        }
    }

    public final void startAITurn(int playerIndex) {
        waitingForAI = true;
        aiThinkTimer = 0f;
        currentAITurn = playerIndex;
        justStartedWaiting = true;
    }

    public final void updateGameState(PokerGame5 game) {
        this.game = game;
        final PokerState5 state = game.getState();

        if (state.round == PokerRound.PREFLOP && lastAnimatedRound != PokerRound.PREFLOP) {
            waitingForAI = false;
            aiThinkTimer = 0f;
            currentAITurn = -1;
            skipRequested = false;
            resetCardAnimations();
        }

        final PokerRound previousRound = lastAnimatedRound;
        int previousCommunityCount = lastAnimatedCommunityCount;

        final int currentCommunityCount = state.communityCards != null ? state.communityCards.size() : 0;

        if (lastAnimatedCommunityCount > 0 && state.communityCards != null && state.communityCards.isEmpty()) {
            lastAnimatedCommunityCount = 0;
            previousCommunityCount = 0;
        }

        checkAndTriggerAnimations(state, previousRound, previousCommunityCount);

        lastAnimatedRound = state.round;
        lastAnimatedCommunityCount = currentCommunityCount;

        resultLblCached = false;

        if (state.currentPlayerIndex != PokerGame5.HUMAN_PLAYER_INDEX &&
            state.round != PokerRound.SHOWDOWN && !waitingForAI) {
            startAITurn(state.currentPlayerIndex);
        }
    }

    private void resetCardAnimations() {
        PokerUIUtils.resetAnimations(playerCardAnimations, opponentCardAnimations, communityCardAnimations);
        lastAnimatedRound = null;
        lastAnimatedCommunityCount = 0;
        playerCardsAnimated = false;
    }

    private void checkAndTriggerAnimations(PokerState5 state, PokerRound previousRound, int previousCommunityCount) {
        if (!playerCardsAnimated && state.hands[PokerGame5.HUMAN_PLAYER_INDEX] != null &&
            !state.hands[PokerGame5.HUMAN_PLAYER_INDEX].isEmpty()) {
            playerCardsAnimated = true;
            PokerUIUtils.triggerPlayerCardAnimations(playerCardAnimations, 
                state.hands[PokerGame5.HUMAN_PLAYER_INDEX].size(), CardFlipAnimation.STAGGER_DELAY);
        }

        final int currentCommunityCount = state.communityCards != null ? state.communityCards.size() : 0;
        PokerUIUtils.triggerCommunityAnimations(communityCardAnimations, 
            previousCommunityCount, currentCommunityCount, CardFlipAnimation.STAGGER_DELAY);

        if (state.round == PokerRound.SHOWDOWN && previousRound != PokerRound.SHOWDOWN) {
            for (int i = 0; i < NUM_OPPONENTS; i++) {
                final int playerIdx = i + 1;
                if (!state.foldedPlayers.contains(playerIdx)) {
                    PokerUIUtils.triggerOpponentAnimations(opponentCardAnimations[i], 
                        HAND_SIZE, CardFlipAnimation.STAGGER_DELAY);
                }
            }
        }
    }

    @Override
    protected void processAction(Object data) {
        if (data == null) return;

        if (POKER_FOLD == data) {
            actionCallback.onPlayerAction(PokerAction.FOLD, 0);
            return;
        }
        if (POKER_CHECK_CALL == data) {
            final int callAmount = game.getCallAmount(PokerGame5.HUMAN_PLAYER_INDEX);
            if (callAmount > 0) {
                actionCallback.onPlayerAction(PokerAction.CALL, 0);
            } else {
                actionCallback.onPlayerAction(PokerAction.CHECK, 0);
            }
            return;
        }
        if (POKER_NEXT_HAND == data) {
            final PokerState5 state = game.getState();
            final boolean canContinue = state.stacks[PokerGame5.HUMAN_PLAYER_INDEX] > 0;
            if (canContinue) actionCallback.onNextHand();
            return;
        }
        if (POKER_SUSPEND == data) {
            actionCallback.onSuspend();
            return;
        }
        if (POKER_HOW_TO_PLAY == data) {
            actionCallback.onHowToPlay();
            return;
        }
        if (POKER_FLIP_TABLE == data) {
            actionCallback.onFlipTable();
            return;
        }
        if (data instanceof String strData && strData.startsWith(POKER_RAISE_PREFIX)) {
            final int amount = Integer.parseInt(strData.substring(POKER_RAISE_PREFIX.length()));
            actionCallback.onPlayerAction(PokerAction.RAISE, amount);
        }
    }

    public final void refreshAfterStateChange(PokerGame5 updatedGame) {
        this.game = updatedGame;
        updateGameState(updatedGame);
    }
}
