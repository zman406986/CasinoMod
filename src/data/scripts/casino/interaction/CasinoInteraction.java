package data.scripts.casino.interaction;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import data.scripts.casino.CasinoConfig;
import data.scripts.casino.CasinoVIPManager;
import java.awt.Color;
import java.util.Map;

/**
 * Primary router for the casino interface.
 * Delegates logic to specialized handlers (Poker, Arena, Gacha).
 */
public class CasinoInteraction implements InteractionDialogPlugin {

    protected InteractionDialogAPI dialog;
    protected TextPanelAPI textPanel;
    protected OptionPanelAPI options;
    
    // Sub-Handlers - Each manages a specific aspect of the casino
    protected PokerHandler poker;
    protected ArenaHandler arena;
    protected GachaHandler gacha;
    protected FinHandler fin;
    protected HelpHandler help;

    /**
     * State enum tracks which section of the casino the player is currently in
     */
    public enum State {
        MENU, POKER, ARENA, GACHA, FINANCIAL, HELP, LEAVE_CONFIRM
    }
    protected State currentState = State.MENU;

    /**
     * Initializes the casino interaction when the dialog is opened
     */
    @Override
    public void init(InteractionDialogAPI dialog) {
        // Store references to the dialog components for easy access
        this.dialog = dialog;
        this.textPanel = dialog.getTextPanel();
        this.options = dialog.getOptionPanel();
        
        // Initialize all sub-handlers, passing this instance so they can access shared resources
        this.poker = new PokerHandler(this);
        this.arena = new ArenaHandler(this);
        this.gacha = new GachaHandler(this);
        this.fin = new FinHandler(this);
        this.help = new HelpHandler(this);

        // Check player's financial status to determine if they can access the casino
        int gems = 0;
        int ceiling = 0;
        try {
            gems = CasinoVIPManager.getStargems();
            ceiling = CasinoVIPManager.getDebtCeiling();
        } catch (Exception e) {
            // Fallback values if there's an issue with VIP manager
            Global.getLogger(CasinoInteraction.class).warn("Error accessing VIP manager, using default values", e);
            gems = 0;
            ceiling = 10000;
        }
        
        // If player's debt exceeds their credit limit, they get a warning and must leave
        if (gems < -ceiling) {
            textPanel.addParagraph("Corporate Reconciliation Team is waiting for you...", Color.RED);
            options.addOption("End Interaction", "leave_now");
            return;
        }

        // Welcome message to the player
        textPanel.addParagraph("You enter the Private Lounge.");
        textPanel.addParagraph("Welcome to the IPC Casino!", Color.YELLOW);
        
        // Check if there's a suspended game to resume
        if (!checkSuspendedGame()) {
            showMenu();
        }
    }

    /**
     * Displays the main casino menu with all available options
     */
    public void showMenu() {
        // Get player's current gem balance and debt ceiling
        int gems = 0;
        int ceiling = 0;
        try {
            gems = CasinoVIPManager.getStargems();
            ceiling = CasinoVIPManager.getDebtCeiling();
        } catch (Exception e) {
            // Fallback values if there's an issue with VIP manager
            Global.getLogger(CasinoInteraction.class).warn("Error accessing VIP manager, using default values", e);
            gems = 0;
            ceiling = 10000;
        }
        
        // Clear any existing options to start fresh
        options.clearOptions();
        
        // Display the player's current status
        textPanel.addParagraph("Status: " + gems + " Gems (Limit: " + ceiling + ")");
        textPanel.highlightInLastPara(Color.GREEN, "" + gems);

        // Add all main menu options to the dialog
        options.addOption("Texas Hold'em", "play");
        options.addOption("Spiral Abyss Arena", "arena_lobby");
        options.addOption("Tachy-Impact (Gacha)", "gacha_menu");
        options.addOption("Financial (Top-up/Cash-out)", "financial_menu");
        options.addOption("Handbook", "how_to_play_main");
        options.addOption("Leave", "leave");
        
        // Update the current state to reflect the menu screen
        currentState = State.MENU;
    }

    /**
     * Handles when the player selects an option from the dialog
     */
    @Override
    public void optionSelected(String optionText, Object optionData) {
        String option = (String) optionData;
        
        // Handle the visit_casino option (for market interaction)
        if ("visit_casino".equals(option)) {
            // We're already in the casino interaction, so show the menu
            showMenu();
            return;
        }
        
        // Handle resuming a suspended game
        if ("resume_game".equals(option)) {
            MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
            String type = mem.getString("$ipc_suspended_game_type");
            if ("Poker".equals(type)) {
                poker.restoreSuspendedGame(); // Restore the suspended poker game
                mem.unset("$ipc_suspended_game_type");
            }
            else if ("Arena".equals(type)) {
                arena.showArenaLobby();
                mem.unset("$ipc_suspended_game_type");
            }
            return;
        }

        // Check if the option belongs to any specific handler FIRST, regardless of current state
        // This ensures that submenu options are properly routed even if not in that state
        if ("play".equals(option) || "confirm_poker_ante".equals(option) || "next_hand".equals(option) ||
            "how_to_poker".equals(option) || currentState == State.POKER) {
            poker.handle(option);
        } else if ("arena_lobby".equals(option) || "arena_watch_next".equals(option) || "arena_skip".equals(option) ||
                   "arena_switch".equals(option) || "how_to_arena".equals(option) || currentState == State.ARENA) {
            arena.handle(option);
        } else if ("gacha_menu".equals(option) || "pull_1".equals(option) || "pull_10".equals(option) ||
                   "auto_convert".equals(option) || "how_to_gacha".equals(option) || currentState == State.GACHA) {
            gacha.handle(option);
        } else if ("financial_menu".equals(option) || "buy_chips".equals(option) || "cash_out".equals(option) ||
                   option.startsWith("buy_pack_") || option.startsWith("confirm_buy_pack_") ||
                   "buy_vip".equals(option) || "confirm_buy_vip".equals(option) || "buy_ship".equals(option) ||
                   currentState == State.FINANCIAL) {
            fin.handle(option);
        } else if ("how_to_play_main".equals(option) || "how_to_poker".equals(option) || "how_to_arena".equals(option) ||
                   "how_to_gacha".equals(option) || currentState == State.HELP) {
            help.handle(option);
        } else if ("leave".equals(option) || "leave_now".equals(option)) {
            // Close the interaction dialog
            dialog.dismiss();
        } else if ("back_menu".equals(option)) {
            // Return to the main menu
            showMenu();
        } else {
            // If we reach here, the option wasn't handled by any specific handler
            // Show the main menu as fallback
            showMenu();
        }
    }

    /**
     * Checks if there's a suspended game that the player can resume
     */
    private boolean checkSuspendedGame() {
        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        if (mem.contains("$ipc_suspended_game_type")) {
            String type = mem.getString("$ipc_suspended_game_type");
            textPanel.addParagraph("You have an active session of " + type + " waiting. Resume?", Color.YELLOW);
            options.addOption("Resume Game", "resume_game");
            options.addOption("Forfeit & Start New", "back_menu");
            return true; 
        }
        return false;
    }

    // Boilerplate for Dialog Plugin
    @Override public void optionMousedOver(String optionText, Object optionData) {}
    @Override public void advance(float amount) {}
    @Override public void backFromEngagement(EngagementResultAPI battleResult) {}
    @Override public Object getContext() { return null; }
    @Override public Map<String, MemoryAPI> getMemoryMap() { return null; }
    
    // Getters for handlers
    public InteractionDialogAPI getDialog() { return dialog; }
    public TextPanelAPI getTextPanel() { return textPanel; }
    public OptionPanelAPI getOptions() { return options; }
    public void setState(State state) { this.currentState = state; }
    
    // Static method to start the casino interaction from anywhere
    public static void startCasinoInteraction(InteractionDialogAPI dialog) {
        CasinoInteraction casinoInteraction = new CasinoInteraction();
        dialog.setPlugin(casinoInteraction);
        casinoInteraction.init(dialog);
    }
}