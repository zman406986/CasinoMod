package data.scripts.casino.cards.poker2;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;
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
import data.scripts.casino.cards.poker2.PokerGame.PokerState;
import data.scripts.casino.cards.pokerShared.PokerAction;
import data.scripts.casino.cards.pokerShared.PokerHandEvaluator;
import data.scripts.casino.cards.pokerShared.PokerHandEvaluator.HandScore;
import data.scripts.casino.cards.pokerShared.PokerUIUtils;
import data.scripts.casino.cards.pokerShared.PokerUtils;
import data.scripts.casino.cards.pokerShared.PokerRound;

import static data.scripts.casino.shared.CardRenderingUtils.*;

public class PokerPanelUI extends BaseCardGamePanelUI<PokerGame> {

    private static final String POKER_FOLD = "poker_fold";
    private static final String POKER_CHECK_CALL = "poker_check_call";
    private static final String POKER_RAISE_PREFIX = "poker_raise_";
    private static final String POKER_NEXT_HAND = "poker_next_hand";
    private static final String POKER_SUSPEND = "poker_suspend";
    private static final String POKER_HOW_TO_PLAY = "poker_how_to_play";
    private static final String POKER_FLIP_TABLE = "poker_flip_table";

    private static final float RAISE_BUTTON_WIDTH = 180f;
    private static final int HAND_SIZE = 2;
    private static final int MAX_COMMUNITY_CARDS = 5;

    private final PokerActionCallback actionCallback;

    private boolean waitingForOpponent = false;
    private float opponentThinkTimer = 0f;
    private static final float OPPONENT_THINK_DELAY = 0.8f;

    private final CardFlipAnimation[] playerCardAnimations = new CardFlipAnimation[HAND_SIZE];
    private final CardFlipAnimation[] opponentCardAnimations = new CardFlipAnimation[HAND_SIZE];
    private final CardFlipAnimation[] communityCardAnimations = new CardFlipAnimation[MAX_COMMUNITY_CARDS];

    private PokerRound lastAnimatedRound = null;
    private int lastAnimatedCommunityCount = 0;
    private boolean playerCardsAnimated = false;

    private final int[] raiseOptionsBuffer = new int[MAX_COMMUNITY_CARDS];
    private int raiseOptionsCount = 0;

    private boolean cachedWasShowdown = false;
    private int cachedCallAmount = -1;
    private int cachedPlayerStack = -1;
    private int cachedOpponentStack = -1;
    private int cachedPot = -1;
    private int cachedBigBlind = -1;
    private int cachedOpponentBet = -1;
    private int cachedPlayerBet = -1;

    public final void resetCardAnimations() {
        PokerUIUtils.resetAnimations(playerCardAnimations, null, communityCardAnimations);
        for (int i = 0; i < HAND_SIZE; i++) {
            opponentCardAnimations[i].reset();
        }
        lastAnimatedRound = null;
        lastAnimatedCommunityCount = 0;
        playerCardsAnimated = false;
    }

    private void checkAndTriggerAnimations(PokerState state, PokerRound previousRound, int previousCommunityCount) {
        if (!playerCardsAnimated && state.playerHand != null && !state.playerHand.isEmpty()) {
            playerCardsAnimated = true;
            PokerUIUtils.triggerPlayerCardAnimations(playerCardAnimations, state.playerHand.size(), CardFlipAnimation.STAGGER_DELAY);
        }

        final int currentCommunityCount = state.communityCards != null ? state.communityCards.size() : 0;
        PokerUIUtils.triggerCommunityAnimations(communityCardAnimations, previousCommunityCount, currentCommunityCount, CardFlipAnimation.STAGGER_DELAY);

        if (state.round == PokerRound.SHOWDOWN && previousRound != PokerRound.SHOWDOWN && state.folder == null) {
            PokerUIUtils.triggerOpponentAnimations(opponentCardAnimations, HAND_SIZE, CardFlipAnimation.STAGGER_DELAY);
        }
    }

    private int lastPlayerStack = -1;
    private int lastOpponentStack = -1;
    private int lastPlayerBet = -1;
    private int lastOpponentBet = -1;
    private int lastPot = -1;
    private int lastBigBlind = -1;
    private PokerRound lastRound = null;

    private boolean resultLblCached = false;

    private final List<ButtonAPI> raiseOptionButtons = new ArrayList<>();
    private ButtonAPI flipTableButton;
    private ButtonAPI foldBtn;
    private ButtonAPI checkCallBtn;
    private ButtonAPI nextHandBtn;

    private LabelAPI playerStackLabel;
    private LabelAPI opponentStackLabel;
    private LabelAPI roundLabel;
    private LabelAPI waitingLabel;
    private LabelAPI opponentActionLabel;
    private LabelAPI playerActionLabel;
    private LabelAPI returnMessageLabel;
    private LabelAPI resultLabel;

    public interface PokerActionCallback {
        void onPlayerAction(PokerAction action, int raiseAmount);
        void onNextHand();
        void onSuspend();
        void onHowToPlay();
        void onFlipTable();
    }

    public PokerPanelUI(PokerGame game, PokerActionCallback callback) {
        super(game);
        this.actionCallback = callback;

        for (int i = 0; i < HAND_SIZE; i++) {
            playerCardAnimations[i] = new CardFlipAnimation();
            opponentCardAnimations[i] = new CardFlipAnimation();
        }
        for (int i = 0; i < MAX_COMMUNITY_CARDS; i++) {
            communityCardAnimations[i] = new CardFlipAnimation();
        }
    }

    @Override
    public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
        super.init(panel, callbacks);
        waitingForOpponent = false;
        opponentThinkTimer = 0f;
        cachedWasShowdown = false;
        cachedCallAmount = -1;
        cachedPlayerStack = -1;
        cachedOpponentStack = -1;
        cachedPot = -1;
        cachedBigBlind = -1;
        cachedOpponentBet = -1;
        cachedPlayerBet = -1;
    }

    @Override
    protected void createButtonsInInit() {
        if (buttonsCreated) return;

        final PositionAPI pos = panel.getPosition();
        final TooltipMakerAPI btnTp = panel.createUIElement(pos.getWidth(), pos.getHeight(), false);
        btnTp.setActionListenerDelegate(this);
        panel.addUIElement(btnTp).inBL(0f, 0f);

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
        foldBtn.setShortcut(Keyboard.KEY_F, false);
        foldBtn.getPosition().inTL(0, 0);
        foldBtn.setOpacity(0f);

        checkCallBtn = btnTp.addButton(Strings.get("poker_panel.check_btn"), POKER_CHECK_CALL, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        checkCallBtn.setQuickMode(true);
        checkCallBtn.setShortcut(Keyboard.KEY_C, false);
        checkCallBtn.getPosition().inTL(0, 0);
        checkCallBtn.setOpacity(0f);

        for (int i = 0; i < MAX_COMMUNITY_CARDS; i++) {
            final ButtonAPI btn = btnTp.addButton("", POKER_RAISE_PREFIX + "0", RAISE_BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
            btn.setQuickMode(true);
            btn.setCustomData(POKER_RAISE_PREFIX + "0");
            btn.getPosition().inTL(0, 0);
            btn.setOpacity(0f);
            raiseOptionButtons.add(btn);
        }

        buttonsCreated = true;
    }

    @Override
    protected void updateButtonVisibility() {
        if (game == null) return;
        final PokerState state = game.getState();

        final boolean isShowdown = state.round == PokerRound.SHOWDOWN;
        if (isShowdown != cachedWasShowdown) {
            cachedWasShowdown = isShowdown;
            final String flipTableLabel = isShowdown ? Strings.get("poker_panel.leave_btn") : Strings.get("poker_panel.run_away");
            flipTableButton.setText(flipTableLabel);
        }

        final int callAmount = state.opponentBet - state.playerBet;
        final boolean opponentEffectivelyAllIn = state.opponentStack <= state.bigBlind || state.opponentDeclaredAllIn;
        final boolean canRaise = state.playerStack > 0 && state.opponentStack > 0 && callAmount < state.playerStack && !opponentEffectivelyAllIn;
        final boolean isPlayerTurn = state.currentPlayer == PokerGame.CurrentPlayer.PLAYER && state.round != PokerRound.SHOWDOWN;

        final float bottomY = PANEL_HEIGHT - BUTTON_HEIGHT - MARGIN;
        final float totalActionWidth = BUTTON_WIDTH * 2 + BUTTON_SPACING;
        final float actionStartX = (PANEL_WIDTH - totalActionWidth) / 2f;

        if (isPlayerTurn) {
            foldBtn.getPosition().inTL(actionStartX, bottomY);
            foldBtn.setOpacity(1f);

            if (callAmount != cachedCallAmount) {
                cachedCallAmount = callAmount;
                final String checkCallLabel = callAmount > 0 ? Strings.format("poker_panel.call_btn", callAmount) : Strings.get("poker_panel.check_btn");
                checkCallBtn.setText(checkCallLabel);
            }
            final float checkCallX = actionStartX + BUTTON_WIDTH + BUTTON_SPACING;
            checkCallBtn.getPosition().inTL(checkCallX, bottomY);
            checkCallBtn.setOpacity(1f);
        } else {
            foldBtn.setOpacity(0f);
            checkCallBtn.setOpacity(0f);
        }

        if (canRaise && isPlayerTurn) {
            final boolean stateChanged = state.playerStack != cachedPlayerStack ||
                state.opponentStack != cachedOpponentStack ||
                state.pot != cachedPot ||
                state.bigBlind != cachedBigBlind ||
                state.opponentBet != cachedOpponentBet ||
                state.playerBet != cachedPlayerBet;

            if (stateChanged) {
                cachedPlayerStack = state.playerStack;
                cachedOpponentStack = state.opponentStack;
                cachedPot = state.pot;
                cachedBigBlind = state.bigBlind;
                cachedOpponentBet = state.opponentBet;
                cachedPlayerBet = state.playerBet;
                computeRaiseOptions(state);
            }

            final float raiseOptionsY = bottomY - BUTTON_HEIGHT - BUTTON_SPACING;
            final float totalRaiseWidth = RAISE_BUTTON_WIDTH * raiseOptionsCount + BUTTON_SPACING * (raiseOptionsCount - 1);
            final float raiseStartX = (PANEL_WIDTH - totalRaiseWidth) / 2f;

            for (int i = 0; i < raiseOptionButtons.size(); i++) {
                final ButtonAPI btn = raiseOptionButtons.get(i);
                if (i < raiseOptionsCount) {
                    final int amt = raiseOptionsBuffer[i];
                    final String btnId = POKER_RAISE_PREFIX + amt;
                    final float btnX = raiseStartX + (RAISE_BUTTON_WIDTH + BUTTON_SPACING) * i;

                    btn.setShortcut(amt == state.bigBlind ? Keyboard.KEY_R : Keyboard.KEY_NONE, false);
                    btn.setText(formatRaiseLabel(amt, state.bigBlind, state.pot, state.playerStack, state.opponentBet, state.playerBet));
                    btn.setCustomData(btnId);
                    btn.getPosition().inTL(btnX, raiseOptionsY);
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
    }

    private void computeRaiseOptions(PokerState state) {
        raiseOptionsCount = 0;
        final int pot = state.pot;
        final int stack = state.playerStack;
        final int bb = state.bigBlind;
        final int opponentBet = state.opponentBet;
        final int maxBet = state.playerBet + stack;

        final int bbTotal = opponentBet + bb;
        if (bbTotal <= maxBet && notContainsRaise(bbTotal)) {
            raiseOptionsBuffer[raiseOptionsCount++] = bbTotal;
        }

        final int halfPot = pot / 2;
        final int halfPotTotal = opponentBet + halfPot;
        if (halfPot >= bb && halfPotTotal <= maxBet && notContainsRaise(halfPotTotal)) {
            raiseOptionsBuffer[raiseOptionsCount++] = halfPotTotal;
        }

        final int potTotal = opponentBet + pot;
        if (pot >= bb && potTotal <= maxBet && notContainsRaise(potTotal)) {
            raiseOptionsBuffer[raiseOptionsCount++] = potTotal;
        }

        final int twoPot = pot * 2;
        final int twoPotTotal = opponentBet + twoPot;
        if (twoPot > pot && twoPotTotal <= maxBet && notContainsRaise(twoPotTotal)) {
            raiseOptionsBuffer[raiseOptionsCount++] = twoPotTotal;
        }

        final int allInTotal = state.playerBet + stack;
        if (stack > 0 && notContainsRaise(allInTotal)) {
            raiseOptionsBuffer[raiseOptionsCount++] = allInTotal;
        }
    }

    private boolean notContainsRaise(int value) {
        for (int i = 0; i < raiseOptionsCount; i++) {
            if (raiseOptionsBuffer[i] == value) return false;
        }
        return true;
    }

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

    private void createStackDisplays() {
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

    private void updateStackDisplays(int playerStack, int opponentStack, int playerBet, int opponentBet) {
        if (playerStack != lastPlayerStack || playerBet != lastPlayerBet) {
            lastPlayerStack = playerStack;
            lastPlayerBet = playerBet;
            playerStackLabel.setText(Strings.format("poker_panel.player_stack_bet", playerStack, playerBet));
        }
        if (opponentStack != lastOpponentStack || opponentBet != lastOpponentBet) {
            lastOpponentStack = opponentStack;
            lastOpponentBet = opponentBet;
            opponentStackLabel.setText(Strings.format("poker_panel.opponent_stack_bet", opponentStack, opponentBet));
        }
    }

    private void createRoundLabel() {
        final float ROUND_LABEL_WIDTH = 600f;
        final float ROUND_LABEL_HEIGHT = 25f;

        final float x = (PANEL_WIDTH - ROUND_LABEL_WIDTH) / 2f;
        final float y = PANEL_HEIGHT * 0.40f;

        roundLabel = settings.createLabel(PokerUIUtils.formatRoundProgress(PokerRound.PREFLOP, 0, 50), Fonts.DEFAULT_SMALL);
        roundLabel.setColor(new Color(150, 200, 255));
        roundLabel.setAlignment(Alignment.MID);
        panel.addComponent((UIComponentAPI) roundLabel).inTL(x, y)
            .setSize(ROUND_LABEL_WIDTH, ROUND_LABEL_HEIGHT);
    }

    private void updateRoundLabel(PokerRound round, int bigBlind, int pot) {
        if (round == lastRound && bigBlind == lastBigBlind && pot == lastPot) return;

        lastRound = round;
        lastBigBlind = bigBlind;
        lastPot = pot;

        roundLabel.setText(PokerUIUtils.formatRoundProgress(round, pot, bigBlind));
        roundLabel.setColor(PokerUIUtils.getRoundColor(round));
    }

    private void createWaitingLabel() {
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

    private void createOpponentActionLabel() {
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
        final float ACTION_LABEL_WIDTH = 250f;
        final float ACTION_LABEL_HEIGHT = 25f;

        playerActionLabel = settings.createLabel("", Fonts.DEFAULT_SMALL);
        playerActionLabel.setColor(COLOR_PLAYER);
        playerActionLabel.setAlignment(Alignment.LMID);
        panel.addComponent((UIComponentAPI) playerActionLabel).inTL(160, 480)
            .setSize(ACTION_LABEL_WIDTH, ACTION_LABEL_HEIGHT);
        hidePlayerAction();
    }

    public final void showOpponentAction(String action) {
        opponentActionLabel.setText(action);
        opponentActionLabel.setOpacity(1f);
    }

    public final void showPlayerAction(String action) {
        playerActionLabel.setText(action);
        playerActionLabel.setOpacity(1f);
    }

    public final void hideOpponentAction() {
        opponentActionLabel.setOpacity(0f);
    }

    public final void hidePlayerAction() {
        playerActionLabel.setOpacity(0f);
    }

    private void createReturnMessageLabel() {
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

    public final void showReturnMessage(String message) {
        returnMessageLabel.setText(message);
        returnMessageLabel.setOpacity(1f);
    }

    private void createResultLabel() {
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
        if (state.round != PokerRound.SHOWDOWN) {
            resultLabel.setOpacity(0f);
            resultLblCached = false;
            return;
        }

        if (resultLblCached) return;

        int potWon = state.lastPotWon;
        Color resultColor = Color.WHITE;
        String resultText;
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
            final HandScore playerScore = PokerHandEvaluator.evaluate(state.playerHand, state.communityCards);
            final HandScore opponentScore = PokerHandEvaluator.evaluate(state.opponentHand, state.communityCards);

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
        } else {
            resultText = "";
        }

        final boolean playerBust = state.playerStack < state.bigBlind;
        final boolean opponentBust = state.opponentStack < state.bigBlind;

        if (playerBust || opponentBust) {
            final String bustMsg = playerBust ? Strings.get("poker_panel.you_bust_leave") : Strings.get("poker_panel.opponent_bust_leave");
            resultText = Strings.format("poker_panel.result_with_bust", resultText, bustMsg);
        }

        resultLblCached = true;

        resultLabel.setText(resultText);
        resultLabel.setColor(resultColor);
        resultLabel.setOpacity(1f);
    }

    private String formatHandDescription(HandScore score) {
        if (score == null || score.rank == null) return Strings.get("poker_hand_desc.unknown");

        final String rankName = formatHandRank(score.rank);

        if (score.tieBreakers == null || score.tieBreakers.isEmpty()) {
            return rankName;
        }

        final String highCard = PokerUtils.getRankName(score.tieBreakers.get(0));

        return switch (score.rank) {
            case HIGH_CARD -> Strings.format("poker_hand_desc.high_card", highCard);
            case PAIR -> Strings.format("poker_hand_desc.pair_of", highCard);
            case TWO_PAIR -> {
                if (score.tieBreakers.size() >= 2) {
                    final String firstPair = PokerUtils.getRankName(score.tieBreakers.get(0));
                    final String secondPair = PokerUtils.getRankName(score.tieBreakers.get(1));
                    yield Strings.format("poker_hand_desc.two_pair_and", firstPair, secondPair);
                }
                yield Strings.get("poker_hand_desc.two_pair");
            }
            case THREE_OF_A_KIND -> Strings.format("poker_hand_desc.three", highCard);
            case STRAIGHT -> Strings.format("poker_hand_desc.straight_high", highCard);
            case FLUSH -> Strings.format("poker_hand_desc.flush_high", highCard);
            case FULL_HOUSE -> {
                if (score.tieBreakers.size() >= 2) {
                    final String trips = PokerUtils.getRankName(score.tieBreakers.get(0));
                    final String pair = PokerUtils.getRankName(score.tieBreakers.get(1));
                    yield Strings.format("poker_hand_desc.full_house_full", trips, pair);
                }
                yield Strings.get("poker_hand_desc.full_house");
            }
            case FOUR_OF_A_KIND -> Strings.format("poker_hand_desc.four", highCard);
            case STRAIGHT_FLUSH -> {
                if (highCard.equals(Strings.get("poker_ranks.ace"))) {
                    yield Strings.get("poker_hand_desc.royal_flush");
                }
                yield Strings.format("poker_hand_desc.straight_flush_high", highCard);
            }
        };
    }

    private String formatHandRank(PokerHandEvaluator.HandRank rank) {
        if (rank == null) return Strings.get("poker_hand_desc.unknown");
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

    private void createNextHandButton() {
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
        final boolean atShowdown = state.round == PokerRound.SHOWDOWN;
        final boolean canContinue = state.playerStack >= state.bigBlind &&
            state.opponentStack >= state.bigBlind;

        nextHandBtn.setOpacity(atShowdown && canContinue ? 1f : 0.3f);
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

        final PokerState state = game.getState();

        updateStackDisplays(state.playerStack, state.opponentStack, state.displayPlayerBet, state.displayOpponentBet);
        updateRoundLabel(state.round, state.bigBlind, state.pot);
        waitingLabel.setOpacity(waitingForOpponent ? 1f : 0f);

        updateResultLabel(state);
        updateNextHandButton(state);
        updateButtonVisibility();

        if (!state.communityCards.isEmpty()) {
            renderCommunityCards(cx, cy, state.communityCards, alphaMult);
        }

        final float playerCardBottomY = h * 0.25f - CARD_HEIGHT / 2f;
        renderPlayerHand(cx, y + playerCardBottomY, state.playerHand, alphaMult);

        final float opponentCardBottomY = h * 0.75f - CARD_HEIGHT / 2f;
        final boolean showOpponentCards = state.round == PokerRound.SHOWDOWN && state.folder == null;
        renderOpponentHand(cx, y + opponentCardBottomY, state.opponentHand, showOpponentCards, alphaMult);
    }

    private void renderCommunityCards(float cx, float cy, List<Card> cards, float alphaMult) {
        final int numCards = cards.size();
        final float totalWidth = numCards * CARD_WIDTH + (numCards - 1) * CARD_SPACING;
        final float startX = cx - totalWidth / 2f;

        for (int i = 0; i < numCards; i++) {
            final Card card = cards.get(i);
            final float cardX = startX + i * (CARD_WIDTH + CARD_SPACING);
            CardRenderingUtils.renderCardAnimated(cardX, cy - CARD_HEIGHT/2, card, communityCardAnimations[i], alphaMult);
        }
    }

    private void renderPlayerHand(float cx, float y, List<Card> cards, float alphaMult) {
        final float totalWidth = HAND_SIZE * CARD_WIDTH + CARD_SPACING;
        final float startX = cx - totalWidth / 2f;

        final PokerState state = game.getState();
        final float cardsRightEdge = startX + HAND_SIZE * CARD_WIDTH + (HAND_SIZE - 1) * CARD_SPACING;
        CardRenderingUtils.renderStackBars(cardsRightEdge, y, state.playerStack, state.displayPlayerBet, state.maxStack, alphaMult);

        final int count = Math.min(HAND_SIZE, cards.size());
        for (int i = 0; i < count; i++) {
            final Card card = cards.get(i);
            final float cardX = startX + i * (CARD_WIDTH + CARD_SPACING);
            CardRenderingUtils.renderCardAnimated(cardX, y, card, playerCardAnimations[i], alphaMult);
        }
    }

    private void renderOpponentHand(float cx, float y, List<Card> cards, boolean showCards, float alphaMult) {
        final float totalWidth = HAND_SIZE * CARD_WIDTH + CARD_SPACING;
        final float startX = cx - totalWidth / 2f;

        final PokerState state = game.getState();
        final float cardsRightEdge = startX + HAND_SIZE * CARD_WIDTH + (HAND_SIZE - 1) * CARD_SPACING;
        CardRenderingUtils.renderStackBars(cardsRightEdge, y, state.opponentStack, state.displayOpponentBet, state.maxStack, alphaMult);

        final int count = Math.min(HAND_SIZE, cards.size());
        for (int i = 0; i < count; i++) {
            final Card card = cards.get(i);
            final float cardX = startX + i * (CARD_WIDTH + CARD_SPACING);
            if (showCards) {
                CardRenderingUtils.renderCardAnimated(cardX, y, card, opponentCardAnimations[i], alphaMult);
            } else {
                CardRenderingUtils.renderCardFaceDown(cardX, y, alphaMult);
            }
        }
    }

    public final void advance(float amount) {
        if (waitingForOpponent) {
            opponentThinkTimer += amount;
            if (opponentThinkTimer >= OPPONENT_THINK_DELAY) {
                waitingForOpponent = false;
                opponentThinkTimer = 0f;
            }
        }

        for (int i = 0; i < MAX_COMMUNITY_CARDS; i++) {
            communityCardAnimations[i].advance(amount);
            if (i < HAND_SIZE) {
                playerCardAnimations[i].advance(amount);
                opponentCardAnimations[i].advance(amount);
            }
        }
    }

    public final void startOpponentTurn() {
        waitingForOpponent = true;
        opponentThinkTimer = 0f;
    }

    public final void updateGameState(PokerGame game) {
        this.game = game;
        final PokerState state = game.getState();

        if (state.round == PokerRound.PREFLOP && lastAnimatedRound != PokerRound.PREFLOP) {
            resetCardAnimations();
        }

        final PokerRound previousRound = lastAnimatedRound;
        int previousCommunityCount = lastAnimatedCommunityCount;

        waitingForOpponent = false;
        opponentThinkTimer = 0f;

        resultLblCached = false;

        final int currentCommunityCount = state.communityCards != null ? state.communityCards.size() : 0;

        if (lastAnimatedCommunityCount > 0 && state.communityCards != null && state.communityCards.isEmpty()) {
            lastAnimatedCommunityCount = 0;
            previousCommunityCount = 0;
        }

        checkAndTriggerAnimations(state, previousRound, previousCommunityCount);

        lastAnimatedRound = state.round;
        lastAnimatedCommunityCount = currentCommunityCount;
    }

    @Override
    protected void processAction(Object data) {
        if (data == null) return;

        final PokerState state = game.getState();
        switch (data.toString()) {
            case POKER_FOLD -> handleFoldClick();
            case POKER_CHECK_CALL -> handleCheckCallClick(state);
            case POKER_NEXT_HAND -> {
                if (state.playerStack >= state.bigBlind && state.opponentStack >= state.bigBlind) {
                    actionCallback.onNextHand();
                }
            }
            case POKER_SUSPEND -> actionCallback.onSuspend();
            case POKER_HOW_TO_PLAY -> actionCallback.onHowToPlay();
            case POKER_FLIP_TABLE -> actionCallback.onFlipTable();
            default -> {
                if (data instanceof String strData && strData.startsWith(POKER_RAISE_PREFIX)) {
                    final int amount = Integer.parseInt(strData.substring(POKER_RAISE_PREFIX.length()));
                    handleRaiseAmountClick(amount);
                }
            }
        }
    }

    private void handleFoldClick() {
        showPlayerAction(Strings.get("poker_actions.you_fold"));
        actionCallback.onPlayerAction(PokerAction.FOLD, 0);
    }

    private void handleCheckCallClick(PokerState state) {
        final int callAmount = state.opponentBet - state.playerBet;

        if (callAmount > 0) {
            showPlayerAction(Strings.format("poker_actions.you_call", callAmount));
            actionCallback.onPlayerAction(PokerAction.CALL, 0);
        } else {
            showPlayerAction(Strings.get("poker_actions.you_check"));
            actionCallback.onPlayerAction(PokerAction.CHECK, 0);
        }
    }

    private void handleRaiseAmountClick(int amount) {
        showPlayerAction(Strings.format("poker_actions.you_raise_to", amount));
        actionCallback.onPlayerAction(PokerAction.RAISE, amount);
    }
}