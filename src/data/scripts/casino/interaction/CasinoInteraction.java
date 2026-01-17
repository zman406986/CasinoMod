package data.scripts.casino.interaction;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import data.scripts.casino.CasinoVIPManager;
import data.scripts.casino.CasinoConfig;
import data.scripts.CasinoMusicPlugin;
import data.scripts.casino.interaction.OptionHandler;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

public class CasinoInteraction implements InteractionDialogPlugin {
    // Enum for tracking current state
    public enum State {
        MAIN_MENU, POKER, ARENA, GACHA, FINANCIAL, HELP
    }

    // Dependencies
    public final PokerHandler poker;
    public final ArenaHandler arena;
    public final GachaHandler gacha;
    public final FinHandler fin;
    public final HelpHandler help;

    // UI Components
    public InteractionDialogAPI dialog;
    public TextPanelAPI textPanel;
    public OptionPanelAPI options;

    // Handlers maps
    private final Map<String, OptionHandler> handlers = new HashMap<>();
    private final Map<Predicate<String>, OptionHandler> predicateHandlers = new HashMap<>();

    // State tracking
    private State currentState = State.MAIN_MENU;
    private boolean handbookIntroShown = false;

    public CasinoInteraction(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        this.textPanel = dialog.getTextPanel();
        this.options = dialog.getOptionPanel();
        this.poker = new PokerHandler(this);
        this.arena = new ArenaHandler(this);
        this.gacha = new GachaHandler(this);
        this.fin = new FinHandler(this);
        this.help = new HelpHandler(this);
        initializeMainHandlers();
    }

    // Initialize handlers in constructor or init method
    private void initializeMainHandlers() {
        // Exact match handlers
        handlers.put("visit_casino", option -> showMenu());
        handlers.put("resume_game", option -> {
            // Check debt before resuming any game
            int gems = CasinoVIPManager.getStargems();
            int ceiling = CasinoVIPManager.getDebtCeiling();
            if (gems < -ceiling) {
                textPanel.addPara("Corporate Reconciliation Team is waiting for you...", Color.RED);
                options.addOption("End Interaction", "leave_now");
                return;
            }
            
            MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
            if (mem.contains("$ipc_suspended_game_type")) {
                String type = mem.getString("$ipc_suspended_game_type");
                if ("Poker".equals(type)) {
                    poker.restoreSuspendedGame(); // Restore the suspended poker game
                    mem.unset("$ipc_suspended_game_type");
                }
                else if ("Arena".equals(type)) {
                    arena.showArenaLobby();
                    mem.unset("$ipc_suspended_game_type");
                }
            } else {
                // If no suspended game, just return to main menu
                showMenu();
            }
        });
        handlers.put("forfeit_and_return", option -> {
            // Clear the suspended game memory
            MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
            if (mem.contains("$ipc_suspended_game_type")) {
                mem.unset("$ipc_suspended_game_type");
            }
            // Return to main menu
            showMenu();
        });
        handlers.put("play", option -> {
            // Check debt before entering poker
            int gems = CasinoVIPManager.getStargems();
            int ceiling = CasinoVIPManager.getDebtCeiling();
            if (gems < -ceiling) {
                textPanel.addPara("Corporate Reconciliation Team is waiting for you...", Color.RED);
                options.addOption("End Interaction", "leave_now");
                return;
            }
            
            // Check if there's a suspended game and warn the player
            MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
            if (mem.contains("$ipc_suspended_game_type")) {
                textPanel.addPara("Warning: You have a suspended game. Starting a new game will forfeit the suspended one. Would you like to continue?", Color.RED);
                options.clearOptions();
                options.addOption("Continue with New Game", "confirm_new_poker");
                options.addOption("Resume Suspended Game", "resume_game");
                options.addOption("Back to Main Menu", "back_menu");
            } else {
                poker.handle(option);
            }
        });
        handlers.put("confirm_new_poker", option -> {
            // Check debt before entering poker
            int gems = CasinoVIPManager.getStargems();
            int ceiling = CasinoVIPManager.getDebtCeiling();
            if (gems < -ceiling) {
                textPanel.addPara("Corporate Reconciliation Team is waiting for you...", Color.RED);
                options.addOption("End Interaction", "leave_now");
                return;
            }
            
            // Clear the suspended game memory if any
            MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
            if (mem.contains("$ipc_suspended_game_type")) {
                mem.unset("$ipc_suspended_game_type");
            }
            // Now start the new poker game
            poker.handle("play");
        });
        handlers.put("confirm_poker_ante", option -> {
            // Check debt before entering poker
            int gems = CasinoVIPManager.getStargems();
            int ceiling = CasinoVIPManager.getDebtCeiling();
            if (gems < -ceiling) {
                textPanel.addPara("Corporate Reconciliation Team is waiting for you...", Color.RED);
                options.addOption("End Interaction", "leave_now");
                return;
            }
            poker.handle(option);
        });
        handlers.put("next_hand", option -> {
            // Check debt before entering poker
            int gems = CasinoVIPManager.getStargems();
            int ceiling = CasinoVIPManager.getDebtCeiling();
            if (gems < -ceiling) {
                textPanel.addPara("Corporate Reconciliation Team is waiting for you...", Color.RED);
                options.addOption("End Interaction", "leave_now");
                return;
            }
            poker.handle(option);
        });
        handlers.put("how_to_poker", option -> {
            // Check debt before entering poker help
            int gems = CasinoVIPManager.getStargems();
            int ceiling = CasinoVIPManager.getDebtCeiling();
            if (gems < -ceiling) {
                textPanel.addPara("Corporate Reconciliation Team is waiting for you...", Color.RED);
                options.addOption("End Interaction", "leave_now");
                return;
            }
            poker.handle(option);
        });
        handlers.put("arena_lobby", option -> {
            // Check debt before entering arena
            int gems = CasinoVIPManager.getStargems();
            int ceiling = CasinoVIPManager.getDebtCeiling();
            if (gems < -ceiling) {
                textPanel.addPara("Corporate Reconciliation Team is waiting for you...", Color.RED);
                options.addOption("End Interaction", "leave_now");
                return;
            }
            
            // Check if there's a suspended game and warn the player
            MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
            if (mem.contains("$ipc_suspended_game_type")) {
                textPanel.addPara("Warning: You have a suspended game. Starting a new game will forfeit the suspended one. Would you like to continue?", Color.RED);
                options.clearOptions();
                options.addOption("Continue with New Game", "confirm_new_arena");
                options.addOption("Resume Suspended Game", "resume_game");
                options.addOption("Back to Main Menu", "back_menu");
            } else {
                arena.handle(option);
            }
        });
        handlers.put("confirm_new_arena", option -> {
            // Check debt before entering arena
            int gems = CasinoVIPManager.getStargems();
            int ceiling = CasinoVIPManager.getDebtCeiling();
            if (gems < -ceiling) {
                textPanel.addPara("Corporate Reconciliation Team is waiting for you...", Color.RED);
                options.addOption("End Interaction", "leave_now");
                return;
            }
            
            // Clear the suspended game memory if any
            MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
            if (mem.contains("$ipc_suspended_game_type")) {
                mem.unset("$ipc_suspended_game_type");
            }
            // Now start the new arena game
            arena.handle("arena_lobby");
        });
        handlers.put("arena_watch_next", option -> {
            // Check debt before continuing arena
            int gems = CasinoVIPManager.getStargems();
            int ceiling = CasinoVIPManager.getDebtCeiling();
            if (gems < -ceiling) {
                textPanel.addPara("Corporate Reconciliation Team is waiting for you...", Color.RED);
                options.addOption("End Interaction", "leave_now");
                return;
            }
            arena.handle(option);
        });
        handlers.put("arena_skip", option -> {
            // Check debt before continuing arena
            int gems = CasinoVIPManager.getStargems();
            int ceiling = CasinoVIPManager.getDebtCeiling();
            if (gems < -ceiling) {
                textPanel.addPara("Corporate Reconciliation Team is waiting for you...", Color.RED);
                options.addOption("End Interaction", "leave_now");
                return;
            }
            arena.handle(option);
        });
        handlers.put("arena_switch", option -> {
            // Check debt before continuing arena
            int gems = CasinoVIPManager.getStargems();
            int ceiling = CasinoVIPManager.getDebtCeiling();
            if (gems < -ceiling) {
                textPanel.addPara("Corporate Reconciliation Team is waiting for you...", Color.RED);
                options.addOption("End Interaction", "leave_now");
                return;
            }
            arena.handle(option);
        });
        handlers.put("how_to_arena", option -> {
            // Check debt before entering arena help
            int gems = CasinoVIPManager.getStargems();
            int ceiling = CasinoVIPManager.getDebtCeiling();
            if (gems < -ceiling) {
                textPanel.addPara("Corporate Reconciliation Team is waiting for you...", Color.RED);
                options.addOption("End Interaction", "leave_now");
                return;
            }
            arena.handle(option);
        });
        handlers.put("gacha_menu", option -> {
            // Check debt before entering gacha
            int gems = CasinoVIPManager.getStargems();
            int ceiling = CasinoVIPManager.getDebtCeiling();
            if (gems < -ceiling) {
                textPanel.addPara("Corporate Reconciliation Team is waiting for you...", Color.RED);
                options.addOption("End Interaction", "leave_now");
                return;
            }
            gacha.handle(option);
        });
        handlers.put("pull_1", option -> {
            // Check debt before entering gacha
            int gems = CasinoVIPManager.getStargems();
            int ceiling = CasinoVIPManager.getDebtCeiling();
            if (gems < -ceiling) {
                textPanel.addPara("Corporate Reconciliation Team is waiting for you...", Color.RED);
                options.addOption("End Interaction", "leave_now");
                return;
            }
            gacha.handle(option);
        });
        handlers.put("pull_10", option -> {
            // Check debt before entering gacha
            int gems = CasinoVIPManager.getStargems();
            int ceiling = CasinoVIPManager.getDebtCeiling();
            if (gems < -ceiling) {
                textPanel.addPara("Corporate Reconciliation Team is waiting for you...", Color.RED);
                options.addOption("End Interaction", "leave_now");
                return;
            }
            gacha.handle(option);
        });
        handlers.put("auto_convert", option -> {
            // Check debt before entering gacha
            int gems = CasinoVIPManager.getStargems();
            int ceiling = CasinoVIPManager.getDebtCeiling();
            if (gems < -ceiling) {
                textPanel.addPara("Corporate Reconciliation Team is waiting for you...", Color.RED);
                options.addOption("End Interaction", "leave_now");
                return;
            }
            gacha.handle(option);
        });
        handlers.put("how_to_gacha", option -> {
            // Check debt before entering gacha help
            int gems = CasinoVIPManager.getStargems();
            int ceiling = CasinoVIPManager.getDebtCeiling();
            if (gems < -ceiling) {
                textPanel.addPara("Corporate Reconciliation Team is waiting for you...", Color.RED);
                options.addOption("End Interaction", "leave_now");
                return;
            }
            gacha.handle(option);
        });
        handlers.put("financial_menu", option -> {
            // Check debt before entering financial menu
            int gems = CasinoVIPManager.getStargems();
            int ceiling = CasinoVIPManager.getDebtCeiling();
            if (gems < -ceiling) {
                textPanel.addPara("Corporate Reconciliation Team is waiting for you...", Color.RED);
                options.addOption("End Interaction", "leave_now");
                return;
            }
            fin.handle(option);
        });
        handlers.put("buy_chips", option -> {
            // Check debt before entering financial menu
            int gems = CasinoVIPManager.getStargems();
            int ceiling = CasinoVIPManager.getDebtCeiling();
            if (gems < -ceiling) {
                textPanel.addPara("Corporate Reconciliation Team is waiting for you...", Color.RED);
                options.addOption("End Interaction", "leave_now");
                return;
            }
            fin.handle(option);
        });
        handlers.put("cash_out", option -> {
            // Check debt before entering financial menu
            int gems = CasinoVIPManager.getStargems();
            int ceiling = CasinoVIPManager.getDebtCeiling();
            if (gems < -ceiling) {
                textPanel.addPara("Corporate Reconciliation Team is waiting for you...", Color.RED);
                options.addOption("End Interaction", "leave_now");
                return;
            }
            fin.handle(option);
        });
        handlers.put("buy_vip", option -> {
            // Check debt before entering financial menu
            int gems = CasinoVIPManager.getStargems();
            int ceiling = CasinoVIPManager.getDebtCeiling();
            if (gems < -ceiling) {
                textPanel.addPara("Corporate Reconciliation Team is waiting for you...", Color.RED);
                options.addOption("End Interaction", "leave_now");
                return;
            }
            fin.handle(option);
        });
        handlers.put("confirm_buy_vip", option -> {
            // Check debt before entering financial menu
            int gems = CasinoVIPManager.getStargems();
            int ceiling = CasinoVIPManager.getDebtCeiling();
            if (gems < -ceiling) {
                textPanel.addPara("Corporate Reconciliation Team is waiting for you...", Color.RED);
                options.addOption("End Interaction", "leave_now");
                return;
            }
            fin.handle(option);
        });
        handlers.put("buy_ship", option -> {
            // Check debt before entering financial menu
            int gems = CasinoVIPManager.getStargems();
            int ceiling = CasinoVIPManager.getDebtCeiling();
            if (gems < -ceiling) {
                textPanel.addPara("Corporate Reconciliation Team is waiting for you...", Color.RED);
                options.addOption("End Interaction", "leave_now");
                return;
            }
            fin.handle(option);
        });
        handlers.put("how_to_play_main", option -> {
            // Check debt before entering help
            int gems = CasinoVIPManager.getStargems();
            int ceiling = CasinoVIPManager.getDebtCeiling();
            if (gems < -ceiling) {
                textPanel.addPara("Corporate Reconciliation Team is waiting for you...", Color.RED);
                options.addOption("End Interaction", "leave_now");
                return;
            }
            
            // Only show the intro page if it hasn't been shown yet
            if (!handbookIntroShown) {
                help.showIntroPage();
                handbookIntroShown = true;
            } else {
                // Otherwise show the main help menu
                help.showMainMenu();
            }
        });
        handlers.put("leave", option -> {
            // Stop casino music when leaving the casino
            CasinoMusicPlugin.stopCasinoMusic();
            dialog.dismiss();
        });
        handlers.put("leave_now", option -> {
            // Stop casino music when leaving the casino
            CasinoMusicPlugin.stopCasinoMusic();
            dialog.dismiss();
        });
        handlers.put("back_menu", option -> showMenu());
        
        // Predicate-based handlers for pattern matching
        predicateHandlers.put(option -> option.startsWith("buy_pack_"), option -> {
            // Check debt before financial transaction
            int gems = CasinoVIPManager.getStargems();
            int ceiling = CasinoVIPManager.getDebtCeiling();
            if (gems < -ceiling) {
                textPanel.addPara("Corporate Reconciliation Team is waiting for you...", Color.RED);
                options.addOption("End Interaction", "leave_now");
                return;
            }
            fin.handle(option);
        });
        predicateHandlers.put(option -> option.startsWith("confirm_buy_pack_"), option -> {
            // Check debt before financial transaction
            int gems = CasinoVIPManager.getStargems();
            int ceiling = CasinoVIPManager.getDebtCeiling();
            if (gems < -ceiling) {
                textPanel.addPara("Corporate Reconciliation Team is waiting for you...", Color.RED);
                options.addOption("End Interaction", "leave_now");
                return;
            }
            fin.handle(option);
        });
        
        // State-dependent handlers
        predicateHandlers.put(option -> {
            // Check debt before state-dependent handling
            int gems = CasinoVIPManager.getStargems();
            int ceiling = CasinoVIPManager.getDebtCeiling();
            if (gems < -ceiling) {
                textPanel.addPara("Corporate Reconciliation Team is waiting for you...", Color.RED);
                options.addOption("End Interaction", "leave_now");
                return false;
            }
            return currentState == State.POKER;
        }, option -> poker.handle(option));
        predicateHandlers.put(option -> {
            // Check debt before state-dependent handling
            int gems = CasinoVIPManager.getStargems();
            int ceiling = CasinoVIPManager.getDebtCeiling();
            if (gems < -ceiling) {
                textPanel.addPara("Corporate Reconciliation Team is waiting for you...", Color.RED);
                options.addOption("End Interaction", "leave_now");
                return false;
            }
            return currentState == State.ARENA;
        }, option -> arena.handle(option));
        predicateHandlers.put(option -> {
            // Check debt before state-dependent handling
            int gems = CasinoVIPManager.getStargems();
            int ceiling = CasinoVIPManager.getDebtCeiling();
            if (gems < -ceiling) {
                textPanel.addPara("Corporate Reconciliation Team is waiting for you...", Color.RED);
                options.addOption("End Interaction", "leave_now");
                return false;
            }
            return currentState == State.GACHA;
        }, option -> gacha.handle(option));
        predicateHandlers.put(option -> {
            // Check debt before state-dependent handling
            int gems = CasinoVIPManager.getStargems();
            int ceiling = CasinoVIPManager.getDebtCeiling();
            if (gems < -ceiling) {
                textPanel.addPara("Corporate Reconciliation Team is waiting for you...", Color.RED);
                options.addOption("End Interaction", "leave_now");
                return false;
            }
            return currentState == State.FINANCIAL;
        }, option -> fin.handle(option));
        predicateHandlers.put(option -> {
            // Check debt before state-dependent handling
            int gems = CasinoVIPManager.getStargems();
            int ceiling = CasinoVIPManager.getDebtCeiling();
            if (gems < -ceiling) {
                textPanel.addPara("Corporate Reconciliation Team is waiting for you...", Color.RED);
                options.addOption("End Interaction", "leave_now");
                return false;
            }
            return currentState == State.HELP;
        }, option -> help.handle(option));
    }
    
    public void setState(State state) {
        this.currentState = state;
    }
    
    public State getState() {
        return this.currentState;
    }
    
    public InteractionDialogAPI getDialog() {
        return dialog;
    }
    
    public TextPanelAPI getTextPanel() {
        return textPanel;
    }
    
    public OptionPanelAPI getOptions() {
        return options;
    }
    
    public void showMenu() {
        options.clearOptions();
        textPanel.addPara("Welcome to the Intergalactic Casino! Choose your entertainment:", Color.WHITE);
        
        options.addOption("Poker Table", "play");
        options.addOption("Arena Combat", "arena_lobby");
        options.addOption("Gacha Terminal", "gacha_menu");
        options.addOption("Financial Services", "financial_menu");
        options.addOption("How to Play", "how_to_play_main");
        options.addOption("Leave Casino", "leave");
        
        setState(State.MAIN_MENU);
    }
    
    /**
     * Static method to start the casino interaction from rule commands
     */
    public static void startCasinoInteraction(InteractionDialogAPI dialog) {
        CasinoInteraction interaction = new CasinoInteraction(dialog);
        // Start casino music when entering
        data.scripts.CasinoMusicPlugin.startCasinoMusic();
        dialog.setPlugin(interaction);
        interaction.showMenu();
    }
    
    // ============================================================
    // InteractionDialogPlugin interface implementation
    // ============================================================
    
    @Override
    public void init(InteractionDialogAPI dialog) {
        // Already initialized in constructor, but we can reinitialize if needed
        this.dialog = dialog;
        this.textPanel = dialog.getTextPanel();
        this.options = dialog.getOptionPanel();
    }
    
    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (optionData == null) return;
        
        String option = optionData.toString();
        
        // Try exact match handlers first
        OptionHandler handler = handlers.get(option);
        if (handler != null) {
            handler.handle(option);
            return;
        }
        
        // Try state-dependent handlers
        for (Map.Entry<Predicate<String>, OptionHandler> entry : predicateHandlers.entrySet()) {
            if (entry.getKey().test(option)) {
                entry.getValue().handle(option);
                return;
            }
        }
        
        // If no handler found, log a warning
        Global.getLogger(this.getClass()).warn("No handler found for option: " + option);
    }
    
    @Override
    public void optionMousedOver(String optionText, Object optionData) {
        // Optional: implement hover behavior if needed
    }
    
    @Override
    public void advance(float amount) {
        // Optional: implement per-frame updates if needed
    }
    
    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) {
        // Handle returning from combat if needed
    }
    
    @Override
    public Object getContext() {
        return null; // Not using context
    }
    
    @Override
    public Map<String, MemoryAPI> getMemoryMap() {
        return null; // Not using custom memory map
    }
}