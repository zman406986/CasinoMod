package data.scripts.casino.cards.blackjack;

import java.util.Map;

import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;

import data.scripts.casino.cards.blackjack.BlackjackPanelUI.BlackjackActionCallback;
import data.scripts.casino.interaction.BlackjackHandler;
import data.scripts.casino.shared.BaseGameDelegate;

public class BlackjackDialogDelegate extends BaseGameDelegate implements BlackjackActionCallback {

    protected final BlackjackPanelUI blackjackPanel;
    protected final BlackjackHandler handler;
    protected BlackjackGame game;

    protected boolean pendingLeave = false;
    protected boolean pendingHowToPlay = false;

    public BlackjackDialogDelegate(BlackjackGame game, InteractionDialogAPI dialog,
            Map<String, MemoryAPI> memoryMap, Runnable onDismissCallback, BlackjackHandler handler) {
        super(dialog, memoryMap, onDismissCallback);
        this.game = game;
        this.handler = handler;

        blackjackPanel = new BlackjackPanelUI(game, this);
    }

    @Override
    public CustomUIPanelPlugin getCustomPanelPlugin() {
        return blackjackPanel;
    }

    @Override
    public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
        super.init(panel, callbacks);

        blackjackPanel.init(panel, callbacks);
        blackjackPanel.updateGameState(game);
    }

    @Override
    protected String getCompletionEventName() {
        return "BlackjackGameCompleted";
    }

    public void updateGame(BlackjackGame newGame) {
        this.game = newGame;
        
        blackjackPanel.updateGameState(newGame);
        resetState();
    }

    public void refreshAfterStateChange(BlackjackGame updatedGame) {
        this.game = updatedGame;
        blackjackPanel.updateGameState(updatedGame);
    }

    public void onPlayerAction(BlackjackGame.Action action) {
        handler.processPlayerActionInPlace(action, this);
    }

    public void onNewHand() {
        handler.startNewHandInPlace(this);
    }

    public void onLeave() {
        pendingLeave = true;
        callbacks.dismissDialog();
    }

    public void onHowToPlay() {
        pendingHowToPlay = true;
        callbacks.dismissDialog();
    }

    public void onPlaceBet(int amount) {
        handler.placeBetInPlace(amount, this);
    }

    public boolean getPendingLeave() {
        return pendingLeave;
    }

    public boolean getPendingHowToPlay() {
        return pendingHowToPlay;
    }
}
