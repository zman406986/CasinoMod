package data.scripts.casino.arena;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import com.fs.starfarer.api.ui.CustomPanelAPI;

import data.scripts.casino.arena.ArenaPanelUI.ArenaActionCallback;
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
    protected boolean pendingSuspend = false;
    protected boolean pendingBattleEnd = false;
    
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
        
        if (pendingBattleEnd && !arenaPanel.isAnimating()) {
            handler.finishArenaBattleInPlace(this);
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
        this.pendingBattleEnd = false;
        
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
    
    public boolean getPendingSuspend() {
        return pendingSuspend;
    }
    
    public void setPendingBattleEnd(boolean pending) {
        this.pendingBattleEnd = pending;
    }
    
    public boolean getPendingBattleEnd() {
        return pendingBattleEnd;
    }
    
    public void clearPendingActions() {
        pendingLeave = false;
        pendingSuspend = false;
    }
    
    public boolean isFinished() {
        return finished;
    }
    
    public final void showErrorMessage(String message) {
        arenaPanel.showExternalError(message);
    }
 
    @Override
    public void onSelectChampion(int championIndex) {
    }
    
    @Override
    public void onConfirmBet(int championIndex, int amount) {
        handler.performAddBetToChampionInPlace(this, championIndex, amount);
    }
    
    @Override
    public void onWatchNextRound() {
        handler.simulateArenaStepInPlace(this);
    }
    
    @Override
    public void onSkipToEnd() {
        handler.simulateAllRemainingStepsInPlace(this);
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
        handler.startNewArenaMatchInPlace(this);
    }
    
    @Override
    public void onStartBattle() {
        handler.startArenaBattleInPlace(this);
    }
}