package data.scripts.casino;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import com.fs.starfarer.api.ui.CustomPanelAPI;

import data.scripts.casino.interaction.ArenaHandler;
import data.scripts.casino.interaction.ArenaHandler.BetInfo;

public class ArenaDialogDelegate implements CustomVisualDialogDelegate {
    
    protected DialogCallbacks callbacks;
    protected boolean finished = false;
    protected boolean battleEnded = false;
    
    protected ArenaPanelUI arenaPanel;
    protected InteractionDialogAPI dialog;
    protected Map<String, MemoryAPI> memoryMap;
    
    protected Runnable onDismissCallback;
    protected ArenaHandler handler;
    
    protected List<SpiralAbyssArena.SpiralGladiator> combatants;
    protected int currentRound;
    protected int totalBet;
    protected List<BetInfo> bets;
    protected List<String> battleLog;
    
    protected boolean pendingLeave = false;
    protected boolean pendingReturnToLobby = false;
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
            Runnable onDismissCallback) {
        this(combatants, currentRound, totalBet, bets, battleLog, dialog, memoryMap, onDismissCallback, null);
    }
    
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
        
        ArenaPanelUI.ArenaActionCallback actionCallback = new ArenaPanelUI.ArenaActionCallback() {
            @Override
            public void onSelectChampion(int championIndex) {
                pendingChampionIndex = championIndex;
            }
            
            @Override
            public void onConfirmBet(int championIndex, int amount) {
                pendingChampionIndex = championIndex;
                pendingBetAmount = amount;
                if (callbacks != null) {
                    callbacks.dismissDialog();
                }
            }
            
            @Override
            public void onWatchNextRound() {
                if (handler != null) {
                    handler.simulateArenaStepInPlace(ArenaDialogDelegate.this);
                } else {
                    pendingWatchNext = true;
                    if (callbacks != null) {
                        callbacks.dismissDialog();
                    }
                }
            }
            
            @Override
            public void onSkipToEnd() {
                if (handler != null) {
                    handler.simulateAllRemainingStepsInPlace(ArenaDialogDelegate.this);
                } else {
                    pendingSkipToEnd = true;
                    if (callbacks != null) {
                        callbacks.dismissDialog();
                    }
                }
            }
            
            @Override
            public void onAddBetToChampion(int championIndex, int amount) {
                pendingChampionIndex = championIndex;
                pendingBetAmount = amount;
                if (callbacks != null) {
                    callbacks.dismissDialog();
                }
            }
            
            @Override
            public void onSuspend() {
                pendingSuspend = true;
                if (callbacks != null) {
                    callbacks.dismissDialog();
                }
            }
            
            @Override
            public void onLeave() {
                pendingLeave = true;
                if (callbacks != null) {
                    callbacks.dismissDialog();
                }
            }
            
            @Override
            public void onReturnToLobby() {
                if (handler != null) {
                    handler.startNewArenaMatchInPlace(ArenaDialogDelegate.this);
                } else {
                    pendingReturnToLobby = true;
                    if (callbacks != null) {
                        callbacks.dismissDialog();
                    }
                }
            }
            
            @Override
            public void onStartBattle() {
                pendingStartBattle = true;
                if (callbacks != null) {
                    callbacks.dismissDialog();
                }
            }
            
            @Override
            public void onEscape() {
                pendingLeave = true;
                if (callbacks != null) {
                    callbacks.dismissDialog();
                }
            }
        };
        
        arenaPanel = new ArenaPanelUI(combatants, currentRound, totalBet, bets, battleLog, actionCallback);
    }
    
    public CustomUIPanelPlugin getCustomPanelPlugin() {
        return arenaPanel;
    }
    
    public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
        this.callbacks = callbacks;
        
        callbacks.getPanelFader().setDurationOut(0.5f);
        
        if (arenaPanel != null) {
            arenaPanel.init(panel, callbacks, dialog);
            
            // Initialize state (follows same pattern as PokerDialogDelegate)
            arenaPanel.updateState(combatants, currentRound, totalBet, bets, battleLog);
        }
    }
    
    public float getNoiseAlpha() {
        return 0.2f;
    }
    
    public void advance(float amount) {
        if (finished) {
            return;
        }
        
        if (battleEnded && arenaPanel != null && arenaPanel.isReadyToClose()) {
            if (callbacks != null) {
                callbacks.getPanelFader().fadeOut();
                if (callbacks.getPanelFader().isFadedOut()) {
                    callbacks.dismissDialog();
                    finished = true;
                }
            }
        }
    }
    
    public void reportDismissed(int option) {
        if (memoryMap != null) {
            FireBest.fire(null, dialog, memoryMap, "ArenaPanelDismissed");
        }
        
        if (onDismissCallback != null) {
            onDismissCallback.run();
        }
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
        
        if (arenaPanel != null) {
            arenaPanel.updateState(combatants, currentRound, totalBet, bets, battleLog);
        }
    }
    
    public void setBattleEnded(int winnerIndex, int totalReward) {
        this.battleEnded = true;
        this.winnerIndex = winnerIndex;
        this.totalReward = totalReward;
        
        if (arenaPanel != null) {
            arenaPanel.setBattleEnded(winnerIndex, totalReward);
        }
    }
    
    public void setBattleEnded(int winnerIndex, int totalReward, ArenaPanelUI.RewardBreakdown breakdown) {
        this.battleEnded = true;
        this.winnerIndex = winnerIndex;
        this.totalReward = totalReward;
        
if (arenaPanel != null) {
            arenaPanel.setBattleEnded(winnerIndex, totalReward, breakdown);
        }

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
        
        if (arenaPanel != null) {
            arenaPanel.resetForNewMatch(combatants, currentRound, totalBet, bets, battleLog);
        }
    }
    
    public boolean getPendingLeave() {
        return pendingLeave;
    }
    
    public boolean getPendingReturnToLobby() {
        return pendingReturnToLobby;
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
        pendingReturnToLobby = false;
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
    
    }
