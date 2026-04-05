package data.scripts.casino.cards.poker2;

import java.util.Map;

import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;

import data.scripts.casino.cards.poker2.PokerPanelUI.PokerActionCallback;
import data.scripts.casino.interaction.PokerHandler;
import data.scripts.casino.cards.pokerShared.PokerAction;
import data.scripts.casino.cards.pokerShared.PokerRound;
import data.scripts.casino.shared.BaseGameDelegate;

public class PokerDialogDelegate extends BaseGameDelegate implements PokerActionCallback {
    private static final String COMPLETION_STR = "PokerGameCompleted";

    protected final PokerPanelUI pokerPanel;
    protected final PokerHandler handler;
    protected PokerGame game;

    protected boolean gameEnded = false;
    protected boolean pendingSuspend = false;
    protected boolean pendingHowToPlay = false;
    protected boolean pendingFlipTable = false;

    protected String lastOpponentAction = "";
    protected String lastPlayerAction = "";
    protected String returnMessage = "";

    public PokerDialogDelegate(PokerGame game, InteractionDialogAPI dialog,
            Map<String, MemoryAPI> memoryMap, Runnable onDismissCallback, PokerHandler handler) {
        super(dialog, memoryMap, onDismissCallback);
        this.game = game;
        this.handler = handler;

        pokerPanel = new PokerPanelUI(game, this);
    }

    public CustomUIPanelPlugin getCustomPanelPlugin() {
        return pokerPanel;
    }

    @Override
    public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
        super.init(panel, callbacks);

        pokerPanel.init(panel, callbacks);
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

    @Override
    public void advance(float amount) {
        super.advance(amount);

        final PokerGame.PokerState state = game.getState();
        final boolean someoneIsBust = state.playerStack < state.bigBlind || state.opponentStack < state.bigBlind;

        if (someoneIsBust && !gameEnded) {
            gameEnded = true;
        }
    }

    @Override
    protected String getCompletionEventName() {
        return COMPLETION_STR;
    }

    public void updateGame(PokerGame newGame) {
        this.game = newGame;
        this.lastOpponentAction = "";
        this.lastPlayerAction = "";

        pokerPanel.updateGameState(newGame);
        pokerPanel.hideOpponentAction();
        pokerPanel.hidePlayerAction();

        gameEnded = false;
        resetState();
    }

    public void refreshAfterStateChange(PokerGame updatedGame) {
        this.game = updatedGame;
        pokerPanel.updateGameState(updatedGame);
    }

    public void startOpponentTurn() {
        pokerPanel.startOpponentTurn();
    }

    public void onPlayerAction(PokerAction action, int raiseAmount) {
        handler.processPlayerActionInPlace(action, raiseAmount, this);
    }

    public void setLastOpponentAction(String action) {
        this.lastOpponentAction = action;

        if (action != null && !action.isEmpty()) {
            pokerPanel.showOpponentAction(action);
        }
    }

    public void setLastPlayerAction(String action) {
        this.lastPlayerAction = action;
    }

    public void onNextHand() {
        handler.startNextHandInPlace(this);
    }

    public void onSuspend() {
        pendingSuspend = true;
        callbacks.dismissDialog();
    }

    public void onHowToPlay() {
        pendingHowToPlay = true;
        callbacks.dismissDialog();
    }

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
