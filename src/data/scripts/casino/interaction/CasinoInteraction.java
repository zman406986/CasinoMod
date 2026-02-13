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
 * Routes player selections to specialized handlers for each game/feature.
 * 
 * State machine tracks current screen for navigation. Poker and Arena games
 * can be suspended mid-game and resumed later via sector memory.
 */
public class CasinoInteraction implements InteractionDialogPlugin {

    protected InteractionDialogAPI dialog;
    protected TextPanelAPI textPanel;
    protected OptionPanelAPI options;

    protected final GachaHandler gacha;
    protected final PokerHandler poker;
    protected final ArenaHandler arena;
    protected final FinHandler financial;
    protected final TopupHandler topup;
    protected final HelpHandler help;

    private State currentState = State.MAIN_MENU;

    public enum State {
        MAIN_MENU,
        GACHA,
        POKER,
        ARENA,
        FINANCIAL,
        TOPUP,
        HELP
    }

    public static void startCasinoInteraction(InteractionDialogAPI dialog) {
        CasinoInteraction plugin = new CasinoInteraction();
        dialog.setPlugin(plugin);
        plugin.init(dialog);
    }

    public CasinoInteraction() {
        this.gacha = new GachaHandler(this);
        this.poker = new PokerHandler(this);
        this.arena = new ArenaHandler(this);
        this.financial = new FinHandler(this);
        this.topup = new TopupHandler(this);
        this.help = new HelpHandler(this);
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        this.textPanel = dialog.getTextPanel();
        this.options = dialog.getOptionPanel();
        showMenu();
    }

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

    @Override
    public void optionMousedOver(String optionText, Object optionData) {
    }

    @Override
    public void advance(float amount) {
    }

    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) {
    }

    @Override
    public Object getContext() {
        return null;
    }

    @Override
    public Map<String, MemoryAPI> getMemoryMap() {
        return null;
    }

    public State getState() {
        return currentState;
    }

    public void setState(State state) {
        this.currentState = state;
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
}
