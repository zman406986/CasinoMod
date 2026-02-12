package data.scripts.casino.interaction;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import data.scripts.CasinoMusicPlugin;
import data.scripts.casino.CasinoVIPManager;

import java.awt.Color;
import java.util.Map;

/**
 * Main interaction dialog plugin for the Interastral Peace Casino.
 * This is the central hub that manages all casino UI flows and delegates to specialized handlers.
 * 
 * ARCHITECTURE:
 * CasinoInteraction implements InteractionDialogPlugin to integrate with Starsector's dialog system.
 * It uses a state machine (State enum) to track which screen the player is currently viewing.
 * Each game/feature has its own handler class that contains the specific logic.
 * 
 * HANDLER PATTERN:
 * - GachaHandler: Tachy-Impact gacha/pull mechanics
 * - PokerHandler: Texas Hold'em poker game
 * - ArenaHandler: Spiral Abyss Arena betting
 * - FinHandler: Financial services (VIP, credit, debt)
 * - TopupHandler: Stargem top-up/purchasing
 * - HelpHandler: Help documentation and rules
 * 
 * STATE MANAGEMENT:
 * The State enum tracks the current screen for proper back-button behavior.
 * When showing a new screen, always call setState() to update the current state.
 * 
 * SUSPENDED GAMES:
 * Poker games can be suspended mid-hand and resumed later.
 * Game state is stored in player memory with keys prefixed by $ipc_poker_
 * When the player returns, restoreSuspendedGame() is called automatically.
 * 
 * AI_AGENT NOTES:
 * - Always use handlers for game-specific logic - don't inline it here
 * - Call setState() whenever showing a new screen
 * - Use showMenu() as the "home" screen that all features return to
 * - The dialog, textPanel, and options fields are shared across all handlers
 * - Never create new handler instances after initialization - reuse the existing ones
 * 
 * MEMORY KEYS FOR SUSPENDED GAMES:
 * - $ipc_suspended_game_type: Type of suspended game ("Poker")
 * - $ipc_poker_pot_size: Current pot amount
 * - $ipc_poker_player_bet: Player's current bet
 * - $ipc_poker_opponent_bet: Opponent's current bet
 * - $ipc_poker_player_stack: Player's remaining chips
 * - $ipc_poker_opponent_stack: Opponent's remaining chips
 * - $ipc_poker_player_is_dealer: Boolean for dealer position
 * - $ipc_poker_suspend_time: Timestamp when game was suspended
 */
public class CasinoInteraction implements InteractionDialogPlugin {

    /** Reference to the dialog API for UI operations */
    protected InteractionDialogAPI dialog;
    /** Reference to the text panel for displaying messages */
    protected TextPanelAPI textPanel;
    /** Reference to the option panel for showing menu choices */
    protected OptionPanelAPI options;

    // Handler instances - each manages a specific feature
    /** Handler for gacha/pull mechanics */
    protected final GachaHandler gacha;
    /** Handler for poker game */
    protected final PokerHandler poker;
    /** Handler for arena betting */
    protected final ArenaHandler arena;
    /** Handler for financial services */
    protected final FinHandler financial;
    /** Handler for Stargem top-up */
    protected final TopupHandler topup;
    /** Handler for help/documentation */
    protected final HelpHandler help;

    /** Current UI state for navigation tracking */
    private State currentState = State.MAIN_MENU;

    /**
     * Enum representing all possible UI states.
     * Used for tracking navigation and back-button behavior.
     * 
     * AI_AGENT NOTE: When adding new screens, add a corresponding state here
     * and update setState() calls in the appropriate handler.
     */
    public enum State {
        /** Main casino lobby/menu */
        MAIN_MENU,
        /** Gacha/pull interface */
        GACHA,
        /** Poker game interface */
        POKER,
        /** Arena betting interface */
        ARENA,
        /** Financial services interface */
        FINANCIAL,
        /** Stargem top-up interface */
        TOPUP,
        /** Help/documentation interface */
        HELP
    }

    /**
     * Static factory method to start casino interaction from rule commands.
     * This is the entry point called by Casino_StartCasinoInteraction rule command.
     * 
     * AI_AGENT NOTE: This is the ONLY way to start a casino interaction.
     * Never instantiate CasinoInteraction directly - always use this method.
     * 
     * @param dialog The interaction dialog API instance
     */
    public static void startCasinoInteraction(InteractionDialogAPI dialog) {
        CasinoInteraction plugin = new CasinoInteraction();
        dialog.setPlugin(plugin);
        plugin.init(dialog);
    }

    /**
     * Constructor initializes all handlers.
     * Handlers are created once and reused throughout the interaction.
     * 
     * AI_AGENT NOTE: Handlers receive 'this' reference to access shared UI components
     * (dialog, textPanel, options, visual) and to call back to showMenu().
     */
    public CasinoInteraction() {
        this.gacha = new GachaHandler(this);
        this.poker = new PokerHandler(this);
        this.arena = new ArenaHandler(this);
        this.financial = new FinHandler(this);
        this.topup = new TopupHandler(this);
        this.help = new HelpHandler(this);
    }

    /**
     * Initializes the dialog plugin.
     * Called automatically by the game when the interaction starts.
     * 
     * AI_AGENT NOTE: This sets up the shared UI component references that
     * all handlers will use. Must be called before any handler methods.
     */
    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        this.textPanel = dialog.getTextPanel();
        this.options = dialog.getOptionPanel();

        // Check if there's a suspended poker game to resume
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        if (mem.contains("$ipc_suspended_game_type")) {
            String gameType = mem.getString("$ipc_suspended_game_type");
            if ("Poker".equals(gameType)) {
                textPanel.addPara("Resuming your suspended poker game...", Color.YELLOW);
                poker.restoreSuspendedGame();
                return;
            }
        }

        // Show the main menu if no suspended game
        showMenu();
    }

    /**
     * Shows the main casino lobby/menu.
     * This is the "home" screen that all features return to.
     * 
     * AI_AGENT NOTE: Always call this method to return to main menu,
     * never recreate the menu logic elsewhere. This ensures consistency.
     */
    public void showMenu() {
        options.clearOptions();
        
        // Start casino music if not already playing
        if (!CasinoMusicPlugin.isCasinoMusicPlaying()) {
            CasinoMusicPlugin.startCasinoMusic();
        }
        
        textPanel.addPara("Welcome to the Interastral Peace Casino.", Color.CYAN);
        textPanel.addPara("How may we assist you today?", Color.GRAY);

        // Display current balance
        int balance = CasinoVIPManager.getBalance();
        int daysRemaining = CasinoVIPManager.getDaysRemaining();
        
        Color balanceColor = balance >= 0 ? Color.GREEN : Color.RED;
        textPanel.addPara("Current Balance: " + balance + " Stargems", balanceColor);
        
        if (daysRemaining > 0) {
            textPanel.addPara("VIP Status: " + daysRemaining + " days remaining", Color.CYAN);
        }

        // Main menu options
        options.addOption("Tachy-Impact (Gacha)", "gacha_menu");
        options.addOption("Texas Hold'em (Poker)", "play");
        options.addOption("Spiral Abyss (Battle Royale Arena)", "arena_lobby");
        options.addOption("Stargem Top-up", "topup_menu");
        options.addOption("Financial Services", "financial_menu");
        options.addOption("How to Play", "how_to_play_main");
        options.addOption("Leave", "leave");

        setState(State.MAIN_MENU);
    }

    /**
     * Main option selection handler.
     * Routes options to the appropriate handler based on option ID prefix.
     * 
     * AI_AGENT NOTE: This is the central routing method.
     * Option IDs follow pattern: {feature}_{action}_{detail}
     * - "gacha_" -> GachaHandler
     * - "play", "poker_" -> PokerHandler  
     * - "arena_" -> ArenaHandler
     * - "financial_" -> FinHandler
     * - "how_to_" -> HelpHandler
     * - "leave" -> Close dialog
     */
    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (optionData == null) return;
        
        String option = (String) optionData;

        if ("leave".equals(option)) {
            CasinoMusicPlugin.stopCasinoMusic();
            dialog.dismiss();
            return;
        }

        // Route to appropriate handler based on option prefix
        if (option.startsWith("gacha_") || option.startsWith("pull_") || option.startsWith("confirm_pull_") || option.startsWith("auto_convert") || option.startsWith("explain_")) {
            gacha.handle(option);
        } else if (option.startsWith("play") || option.startsWith("poker_") || option.startsWith("confirm_poker") || option.startsWith("next_hand")) {
            poker.handle(option);
        } else if (option.startsWith("arena_")) {
            arena.handle(option);
        } else if (option.startsWith("topup_") || option.startsWith("topup_pack_") || option.startsWith("confirm_topup_pack_")) {
            topup.handle(option);
        } else if (option.startsWith("financial_") || option.startsWith("buy_vip") ||
                   option.startsWith("confirm_buy_vip") ||
                   option.startsWith("buy_ship") || option.startsWith("confirm_ship_trade") ||
                   option.startsWith("cancel_ship_trade") || option.startsWith("toggle_vip_notifications") ||
                   option.startsWith("captcha_answer_") || option.startsWith("captcha_wrong_") ||
                   option.startsWith("cash_out")) {
            financial.handle(option);
        } else if (option.startsWith("how_to_")) {
            help.handle(option);
        } else if (option.startsWith("back_")) {
            showMenu();
        } else {
            // Unknown option - return to menu as fallback
            showMenu();
        }
    }

    /**
     * Called when the player mouses over an option.
     * Currently unused but required by interface.
     */
    @Override
    public void optionMousedOver(String optionText, Object optionData) {
        // No hover effects currently implemented
    }

    /**
     * Called when the dialog is closed.
     * Cleans up any suspended game markers.
     * 
     * AI_AGENT NOTE: If player leaves with an active poker game,
     * it remains suspended and can be resumed later.
     */
    @Override
    public void advance(float amount) {
        // Per-frame updates if needed
    }

    /**
     * Called when the dialog is dismissed.
     */
    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) {
        // Not used - casino has no combat
    }

    /**
     * Returns the context for this interaction.
     */
    @Override
    public Object getContext() {
        return null;
    }

    /**
     * Returns the memory map for rule commands.
     */
    @Override
    public Map<String, MemoryAPI> getMemoryMap() {
        return null;
    }

    /**
     * Gets the current UI state.
     * @return Current State enum value
     */
    public State getState() {
        return currentState;
    }

    /**
     * Sets the current UI state.
     * 
     * AI_AGENT NOTE: Always call this when showing a new screen
     * to ensure proper state tracking for navigation.
     * 
     * @param state The new state to set
     */
    public void setState(State state) {
        this.currentState = state;
    }

    /**
     * Gets the dialog API reference.
     * Used by handlers to access UI components.
     */
    public InteractionDialogAPI getDialog() {
        return dialog;
    }

    /**
     * Gets the text panel for displaying messages.
     * Used by handlers to show game output.
     */
    public TextPanelAPI getTextPanel() {
        return textPanel;
    }

    /**
     * Gets the option panel for showing menu choices.
     * Used by handlers to present options to the player.
     */
    public OptionPanelAPI getOptions() {
        return options;
    }
}
