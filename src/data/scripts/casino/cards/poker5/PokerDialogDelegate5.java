package data.scripts.casino.cards.poker5;

import java.util.Map;

import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;

import data.scripts.casino.cards.poker5.PokerPanelUI5.PokerActionCallback5;
import data.scripts.casino.interaction.PokerHandler5;
import data.scripts.casino.cards.pokerShared.PokerAction;
import data.scripts.casino.cards.pokerShared.PokerRound;
import data.scripts.casino.shared.BaseGameDelegate;

public class PokerDialogDelegate5 extends BaseGameDelegate implements PokerActionCallback5 {
    private static final String COMPLETION_STR = "Poker5GameCompleted";

    protected final PokerPanelUI5 pokerPanel;
    protected final PokerHandler5 handler;
    protected PokerGame5 game;

    protected boolean gameEnded = false;
    protected boolean pendingSuspend = false;
    protected boolean pendingHowToPlay = false;
    protected boolean pendingFlipTable = false;

    public PokerDialogDelegate5(PokerGame5 game, InteractionDialogAPI dialog,
            Map<String, MemoryAPI> memoryMap, Runnable onDismissCallback, PokerHandler5 handler) {
        super(dialog, memoryMap, onDismissCallback);
        this.game = game;
        this.handler = handler;

        pokerPanel = new PokerPanelUI5(game, this);
    }

    public CustomUIPanelPlugin getCustomPanelPlugin() {
        return pokerPanel;
    }

    @Override
    public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
        super.init(panel, callbacks);

        pokerPanel.init(panel, callbacks);
        pokerPanel.updateGameState(game);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        pokerPanel.advance(amount);

        final PokerGame5.PokerState5 state = game.getState();
        final boolean playerBust = state.stacks[PokerGame5.HUMAN_PLAYER_INDEX] < game.getBigBlindAmount();

        if ((state.round == PokerRound.SHOWDOWN || playerBust) && !gameEnded) {
            gameEnded = true;
        }
    }

    @Override
    protected String getCompletionEventName() {
        return COMPLETION_STR;
    }

    public void updateGame(PokerGame5 newGame) {
        this.game = newGame;
        pokerPanel.updateGameState(newGame);
        gameEnded = false;
        resetState();
    }

    public void refreshAfterStateChange(PokerGame5 updatedGame) {
        this.game = updatedGame;
        pokerPanel.refreshAfterStateChange(updatedGame);
    }

    @Override
    public void onPlayerAction(PokerAction action, int raiseAmount) {
        handler.processPlayerActionInPlace(action, raiseAmount, this);
    }

    @Override
    public void onNextHand() {
        handler.startNextHandInPlace(this);
    }

    @Override
    public void onSuspend() {
        pendingSuspend = true;
        callbacks.dismissDialog();
    }

    @Override
    public void onHowToPlay() {
        pendingHowToPlay = true;
        callbacks.dismissDialog();
    }

    @Override
    public void onFlipTable() {
        final boolean isShowdown = game != null && game.getState() != null &&
            game.getState().round == PokerRound.SHOWDOWN;

        if (isShowdown) {
            handler.handleCleanLeaveInPlace(this);
        } else {
            pendingFlipTable = true;
            callbacks.dismissDialog();
        }
    }

    @Override
    public void processAITurns() {
        handler.processAITurnsInPlace(this);
    }

    public void startAITurn(int playerIndex) {
        pokerPanel.startAITurn(playerIndex);
    }

    public boolean getPendingSuspend() {
        return pendingSuspend;
    }

    public boolean getPendingHowToPlay() {
        return pendingHowToPlay;
    }

    public boolean getPendingFlipTable() {
        return pendingFlipTable;
    }

}
