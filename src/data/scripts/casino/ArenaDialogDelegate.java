package data.scripts.casino;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import com.fs.starfarer.api.ui.CustomPanelAPI;

import data.scripts.casino.ArenaPanelUI.ArenaActionCallback;
import data.scripts.casino.interaction.ArenaHandler;
import data.scripts.casino.interaction.ArenaHandler.BetInfo;

public class ArenaDialogDelegate implements CustomVisualDialogDelegate, ArenaActionCallback {
    
    protected DialogCallbacks callbacks;
    protected boolean finished = false;
    protected boolean battleEnded = false;
    
    protected final ArenaPanelUI arenaPanel;
    protected final InteractionDialogAPI dialog;
    protected final Map<String, MemoryAPI> memoryMap;
    
    protected final Runnable onDismissCallback;
    protected final ArenaHandler handler;
    
    protected List<SpiralAbyssArena.SpiralGladiator> combatants;
    protected int currentRound;
    protected int totalBet;
    protected List<BetInfo> bets;
    protected List<String> battleLog;
    
    protected boolean pendingLeave = false;
    protected boolean pendingNextGame = false;
    protected boolean pendingWatchNext = false;
    protected boolean pendingSkipToEnd = false;
    protected boolean pendingSuspend = false;
    protected boolean pendingStartBattle = false;
    protected int pendingBetAmount = 0;
    protected int pendingChampionIndex = -1;
    
    protected int winnerIndex = -1;
    protected int totalReward = 0;

    public ArenaDialogDelegate(
            List<SpiralAbyssArena.SpiralGladiator> combatants,
            int currentRound,
            int totalBet,
            List<BetInfo> bets,
            List<String> battleLog,
            InteractionDialogAPI dialog,
            Map<String, MemoryAPI> memoryMap,
            Runnable onDismissCallback,
            ArenaHandler handler) {
        
        this.combatants = combatants;
        this.currentRound = currentRound;
        this.totalBet = totalBet;
        this.bets = bets;
        this.battleLog = battleLog;
        this.dialog = dialog;
        this.memoryMap = memoryMap;
        this.onDismissCallback = onDismissCallback;
        this.handler = handler;
    
        arenaPanel = new ArenaPanelUI(combatants, currentRound, totalBet, bets, battleLog, this);
    }
    
    public CustomUIPanelPlugin getCustomPanelPlugin() {
        return arenaPanel;
    }
    
    public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
        this.callbacks = callbacks;
        
        callbacks.getPanelFader().setDurationOut(0.5f);
        
        arenaPanel.init(panel, callbacks);
        arenaPanel.updateState(combatants, currentRound, totalBet, bets, battleLog);
    }
    
    public float getNoiseAlpha() {
        return 0.2f;
    }
    
    public void advance(float amount) {
        if (finished) {
            return;
        }
        
        arenaPanel.advance(amount);
        
        if (battleEnded) {
            callbacks.getPanelFader().fadeOut();
            if (callbacks.getPanelFader().isFadedOut()) {
                callbacks.dismissDialog();
                finished = true;
            }
        }
    }
    
    public ArenaPanelUI getArenaPanel() {
        return arenaPanel;
    }
    
    public void reportDismissed(int option) {
        if (memoryMap != null) {
            FireBest.fire(null, dialog, memoryMap, "ArenaPanelDismissed");
        }
        
        onDismissCallback.run();
    }
    
    public void updateForBattle(
            List<SpiralAbyssArena.SpiralGladiator> combatants,
            int currentRound,
            int totalBet,
            List<BetInfo> bets,
            List<String> battleLog) {
        
        this.combatants = combatants;
        this.currentRound = currentRound;
        this.totalBet = totalBet;
        this.bets = bets;
        this.battleLog = battleLog;
        
        arenaPanel.updateState(combatants, currentRound, totalBet, bets, battleLog);
    }
    
    public void setBattleEnded(int winnerIndex, int totalReward, int finalRound) {
        this.battleEnded = true;
        this.winnerIndex = winnerIndex;
        this.totalReward = totalReward;
        this.currentRound = finalRound;
        
        arenaPanel.setBattleEnded(winnerIndex, totalReward, finalRound);
    }
    
    public void setBattleEnded(int winnerIndex, int totalReward, ArenaPanelUI.RewardBreakdown breakdown, int finalRound) {
        this.battleEnded = true;
        this.winnerIndex = winnerIndex;
        this.totalReward = totalReward;
        this.currentRound = finalRound;
        
        arenaPanel.setBattleEnded(winnerIndex, totalReward, breakdown, finalRound);
    }
    
    @Deprecated
    public void setBattleEnded(int winnerIndex, int totalReward) {
        setBattleEnded(winnerIndex, totalReward, this.currentRound);
    }
    
    @Deprecated
    public void setBattleEnded(int winnerIndex, int totalReward, ArenaPanelUI.RewardBreakdown breakdown) {
        setBattleEnded(winnerIndex, totalReward, breakdown, this.currentRound);
    }
    
    public void resetForNewMatch(
            List<SpiralAbyssArena.SpiralGladiator> combatants,
            int currentRound,
            int totalBet,
            List<BetInfo> bets,
            List<String> battleLog) {
        
        this.battleEnded = false;
        this.winnerIndex = -1;
        this.totalReward = 0;
        this.finished = false;
        
        this.combatants = combatants;
        this.currentRound = currentRound;
        this.totalBet = totalBet;
        this.bets = bets;
        this.battleLog = battleLog;
        
        clearPendingActions();
        
        arenaPanel.resetForNewMatch(combatants, currentRound, totalBet, bets, battleLog);
    }
    
    public boolean getPendingLeave() {
        return pendingLeave;
    }
    
    public boolean getPendingNextGame() {
        return pendingNextGame;
    }
    
    public boolean getPendingWatchNext() {
        return pendingWatchNext;
    }
    
    public boolean getPendingSkipToEnd() {
        return pendingSkipToEnd;
    }
    
    public boolean getPendingSuspend() {
        return pendingSuspend;
    }
    
    public boolean getPendingStartBattle() {
        return pendingStartBattle;
    }
    
    public int getPendingBetAmount() {
        return pendingBetAmount;
    }
    
    public int getPendingChampionIndex() {
        return pendingChampionIndex;
    }
    
    public void clearPendingActions() {
        pendingLeave = false;
        pendingNextGame = false;
        pendingWatchNext = false;
        pendingSkipToEnd = false;
        pendingSuspend = false;
        pendingStartBattle = false;
        pendingBetAmount = 0;
        pendingChampionIndex = -1;
    }
    
    public boolean isFinished() {
        return finished;
    }
    
    public void showErrorMessage(String message) {
        arenaPanel.showExternalError(message);
    }
 
    @Override
    public void onSelectChampion(int championIndex) {
        pendingChampionIndex = championIndex;
    }
    
    @Override
    public void onConfirmBet(int championIndex, int amount) {
        // FIXME handler is always non null. So is the rest of the code dead?
        if (handler != null) {
            handler.performAddBetToChampionInPlace(ArenaDialogDelegate.this, championIndex, amount);
            return;
        }

        pendingChampionIndex = championIndex;
        pendingBetAmount = amount;
        callbacks.dismissDialog();
    }
    
    @Override
    public void onWatchNextRound() {
        // FIXME handler is always non null. So is the else branch needed?
        if (handler != null) {
            handler.simulateArenaStepInPlace(ArenaDialogDelegate.this);
        } else {
            pendingWatchNext = true;
            callbacks.dismissDialog();
        }
    }
    
    @Override
    public void onSkipToEnd() {
        // FIXME handler is always non null. So is the else branch needed?
        if (handler != null) {
            handler.simulateAllRemainingStepsInPlace(ArenaDialogDelegate.this);
        } else {
            pendingSkipToEnd = true;
            callbacks.dismissDialog();
        }
    }
    

    @Override
    public void onSuspend() {
        pendingSuspend = true;
        callbacks.dismissDialog();
    }
    
    @Override
    public void onLeave() {
        pendingLeave = true;
        callbacks.dismissDialog();
    }
    
    @Override
    public void onNextGame() {
        // FIXME handler is always non null. So is the else branch needed?
        if (handler != null) {
            handler.startNewArenaMatchInPlace(ArenaDialogDelegate.this);
        } else {
            pendingNextGame = true;
            callbacks.dismissDialog();
        }
    }
    
    @Override
    public void onStartBattle() {
        // FIXME handler is always non null. So is the rest needed?
        if (handler != null) {
            handler.startArenaBattleInPlace(ArenaDialogDelegate.this);
            return;
        }
        pendingStartBattle = true;
        callbacks.dismissDialog();
    }
    
    @Override
    public void onEscape() {
        pendingLeave = true;
        callbacks.dismissDialog();
    }
}