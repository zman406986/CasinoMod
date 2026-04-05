package data.scripts.casino.interaction;

import data.scripts.casino.CasinoConfig;
import data.scripts.casino.Strings;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class HelpHandler {

    private final CasinoInteraction main;
    private final Map<String, OptionHandler> handlers = new HashMap<>();
    
    public HelpHandler(CasinoInteraction main) {
        this.main = main;
        initializeHandlers();
    }
    
    private void initializeHandlers() {
        handlers.put("how_to_play_main", option -> showGeneralHelp());
        handlers.put("how_to_poker", option -> showPokerHelp());
        handlers.put("how_to_blackjack", option -> showBlackjackHelp());
        handlers.put("how_to_arena", option -> showArenaHelp("arena_lobby"));
        handlers.put("how_to_gacha", option -> showGachaHelp());
        handlers.put("how_to_financial", option -> showFinancialHelp());
        handlers.put("how_to_topup", option -> showTopupHelp());
        handlers.put("back_menu", option -> main.showMenu());
        handlers.put("gacha_menu", main.gacha::handle);
        handlers.put("play", main.poker::handle);
        handlers.put("play5", main.poker5::handle);
        handlers.put("poker5_back_action", option -> main.poker5.handle("poker5_back_action"));
        handlers.put("arena_lobby", main.arena::handle);
        handlers.put("financial_menu", main.financial::handle);
        handlers.put("topup_menu", main.topup::handle);
        handlers.put("arena_status", main.arena::handle);
    }

    public void handle(String option) {
        OptionHandler handler = handlers.get(option);
        if (handler != null) {
            handler.handle(option);
            return;
        }
        
        if (option.startsWith("how_to_arena_")) {
            String returnTo = option.replace("how_to_arena_", "");
            showArenaHelp(returnTo);
            return;
        }
        
        main.showMenu();
    }

    public void showGeneralHelp() {
        main.options.clearOptions();
        main.textPanel.addPara(Strings.get("help.how_to_play"), Color.CYAN);
        
        main.textPanel.addPara(Strings.get("help.stargems_title"), Color.YELLOW);
        main.textPanel.addPara(Strings.format("help.stargems_1", (int)CasinoConfig.STARGEM_EXCHANGE_RATE));
        main.textPanel.addPara(Strings.get("help.stargems_2"));
        main.textPanel.addPara(Strings.get("help.stargems_3"));
        
        main.textPanel.addPara(Strings.get("help.vip_title"), Color.YELLOW);
        main.textPanel.addPara(Strings.format("help.vip_1", CasinoConfig.VIP_PASS_DAYS));
        main.textPanel.addPara(Strings.format("help.vip_2", CasinoConfig.VIP_DAILY_REWARD));
        main.textPanel.addPara(Strings.get("help.vip_3"));
        main.textPanel.addPara(Strings.get("help.vip_4"));
        
        main.textPanel.addPara(Strings.get("help.games_title"), Color.GRAY);
        main.textPanel.addPara(Strings.get("help.games_1"));
        main.textPanel.addPara(Strings.get("help.games_2"));
        main.textPanel.addPara(Strings.get("help.games_3"));

        main.options.addOption(Strings.get("help.about_poker"), "how_to_poker");
        main.options.addOption(Strings.get("help.about_blackjack"), "how_to_blackjack");
        main.options.addOption(Strings.get("help.about_arena"), "how_to_arena");
        main.options.addOption(Strings.get("help.about_gacha"), "how_to_gacha");
        main.options.addOption(Strings.get("common.back"), "back_menu");
        main.setState(CasinoInteraction.State.HELP);
    }

    public void showPokerHelp() {
        showPokerHelp("play");
    }

    public void showPokerHelp(String returnTo) {
        main.options.clearOptions();
        main.textPanel.addPara(Strings.get("poker_help.title"), Color.CYAN);
        main.textPanel.addPara(Strings.get("poker_help.intro"));
        main.textPanel.addPara("");
        main.textPanel.addPara(Strings.get("poker_help.rankings_title"), Color.GRAY);
        main.textPanel.addPara(Strings.get("poker_help.rank_1"));
        main.textPanel.addPara(Strings.get("poker_help.rank_2"));
        main.textPanel.addPara(Strings.get("poker_help.rank_3"));
        main.textPanel.addPara(Strings.get("poker_help.rank_4"));
        main.textPanel.addPara(Strings.get("poker_help.rank_5"));
        main.textPanel.addPara(Strings.get("poker_help.rank_6"));
        main.textPanel.addPara(Strings.get("poker_help.rank_7"));
        main.textPanel.addPara(Strings.get("poker_help.rank_8"));
        main.textPanel.addPara(Strings.get("poker_help.rank_9"));
        main.textPanel.addPara(Strings.get("poker_help.rank_10"));
        main.textPanel.addPara("");
        main.textPanel.addPara(Strings.get("poker_help.actions_title"), Color.GRAY);
        main.textPanel.addPara(Strings.get("poker_help.action_call"));
        main.textPanel.addPara(Strings.get("poker_help.action_check"));
        main.textPanel.addPara(Strings.get("poker_help.action_raise"));
        main.textPanel.addPara(Strings.get("poker_help.action_fold"));
        main.textPanel.addPara("");
        main.textPanel.addPara(Strings.get("poker_help.leave_warning"), Color.RED);

        if ("poker_back_action".equals(returnTo) || "poker5_back_action".equals(returnTo)) {
            main.options.addOption(Strings.get("poker_help.back_to_game"), returnTo);
        } else {
            main.options.addOption(Strings.get("common.back"), returnTo);
        }
    }

    public void showBlackjackHelp() {
        main.options.clearOptions();
        main.textPanel.addPara(Strings.get("blackjack_help.title"), Color.CYAN);
        main.textPanel.addPara(Strings.get("blackjack_help.intro"));
        main.textPanel.addPara("");
        main.textPanel.addPara(Strings.get("blackjack_help.goal_title"), Color.GRAY);
        main.textPanel.addPara(Strings.get("blackjack_help.goal_1"));
        main.textPanel.addPara("");
        main.textPanel.addPara(Strings.get("blackjack_help.values_title"), Color.GRAY);
        main.textPanel.addPara(Strings.get("blackjack_help.value_1"));
        main.textPanel.addPara(Strings.get("blackjack_help.value_2"));
        main.textPanel.addPara(Strings.get("blackjack_help.value_3"));
        main.textPanel.addPara("");
        main.textPanel.addPara(Strings.get("blackjack_help.actions_title"), Color.GRAY);
        main.textPanel.addPara(Strings.get("blackjack_help.action_hit"));
        main.textPanel.addPara(Strings.get("blackjack_help.action_stand"));
        main.textPanel.addPara(Strings.get("blackjack_help.action_double"));
        main.textPanel.addPara(Strings.get("blackjack_help.action_split"));
        main.textPanel.addPara("");
        main.textPanel.addPara(Strings.get("blackjack_help.special_title"), Color.GRAY);
        main.textPanel.addPara(Strings.get("blackjack_help.blackjack_1"));
        main.textPanel.addPara(Strings.get("blackjack_help.push_1"));

        if (main.blackjack.getBlackjackGame() != null &&
            main.blackjack.getBlackjackGame().getState().playerStack > 0) {
            main.options.addOption(Strings.get("blackjack_help.back_to_game"), "blackjack_back_to_game");
        } else {
            main.options.addOption(Strings.get("common.back"), "blackjack_play");
        }
    }

    public void showArenaHelp(String returnTo) {
        main.options.clearOptions();
        main.textPanel.addPara(Strings.get("help.arena_title"), Color.CYAN);
        main.textPanel.addPara(Strings.get("help.arena_desc"));
        main.textPanel.addPara("");
        main.textPanel.addPara(Strings.get("help.arena_how_works"), Color.GRAY);
        main.textPanel.addPara(Strings.get("help.arena_1"));
        main.textPanel.addPara(Strings.get("help.arena_2"));
        main.textPanel.addPara(Strings.get("help.arena_3"));
        main.textPanel.addPara(Strings.get("help.arena_4"));
        main.textPanel.addPara(Strings.get("help.arena_5"));
        main.textPanel.addPara(Strings.get("help.arena_6"));

        main.options.addOption(Strings.get("common.back"), returnTo);
    }

    public void showArenaHelp() {
        showArenaHelp("arena_lobby");
    }

    public void showGachaHelp() {
        main.options.clearOptions();
        main.textPanel.addPara(Strings.get("help.gacha_title"), Color.CYAN);
        
        main.textPanel.addPara(Strings.get("help.gacha_how_works"), Color.YELLOW);
        main.textPanel.addPara(Strings.format("help.gacha_1", CasinoConfig.GACHA_COST));
        main.textPanel.addPara(Strings.get("help.gacha_2"));
        
        main.textPanel.addPara(Strings.get("help.gacha_rarities"), Color.GRAY);
        main.textPanel.addPara(Strings.get("help.gacha_5star"));
        main.textPanel.addPara(Strings.get("help.gacha_4star"));
        main.textPanel.addPara(Strings.get("help.gacha_3star"));
        
        main.textPanel.addPara(Strings.get("help.gacha_pity"), Color.GRAY);
        main.textPanel.addPara(Strings.format("help.gacha_pity_5", CasinoConfig.PITY_HARD_5));
        main.textPanel.addPara(Strings.format("help.gacha_pity_4", CasinoConfig.PITY_HARD_4));
        main.textPanel.addPara(Strings.get("help.gacha_5050"));
        
        main.textPanel.addPara(Strings.get("help.gacha_after"), Color.GRAY);
        main.textPanel.addPara(Strings.get("help.gacha_after_1"));

        main.options.addOption(Strings.get("common.back"), "gacha_menu");
    }

    public void showFinancialHelp() {
        main.options.clearOptions();
        main.textPanel.addPara(Strings.get("help.financial_title"), Color.CYAN);
        
        main.textPanel.addPara(Strings.get("help.financial_vip"), Color.YELLOW);
        main.textPanel.addPara(Strings.format("help.financial_vip_cost", CasinoConfig.VIP_PASS_COST, CasinoConfig.VIP_PASS_DAYS));
        main.textPanel.addPara(Strings.format("help.financial_vip_reward", CasinoConfig.VIP_DAILY_REWARD));
        main.textPanel.addPara(Strings.get("help.financial_vip_overdraft"));
        main.textPanel.addPara(Strings.get("help.financial_vip_interest"));
        main.textPanel.addPara(Strings.get("help.financial_vip_ceiling"));
        main.textPanel.addPara("");

        main.textPanel.addPara(Strings.get("help.financial_debt"), Color.YELLOW);
        main.textPanel.addPara(Strings.get("help.financial_debt_1"));
        main.textPanel.addPara(Strings.get("help.financial_debt_2"));
        main.textPanel.addPara(Strings.get("help.financial_debt_3"), Color.RED);
        main.textPanel.addPara("");

        main.textPanel.addPara(Strings.get("help.financial_trading"), Color.YELLOW);
        main.textPanel.addPara(Strings.get("help.financial_trading_1"));
        main.textPanel.addPara(Strings.get("help.financial_trading_2"), Color.RED);

        main.options.addOption(Strings.get("common.back"), "financial_menu");
    }

    public void showTopupHelp() {
        main.options.clearOptions();
        main.textPanel.addPara(Strings.get("help.topup_title"), Color.CYAN);
        
        main.textPanel.addPara(Strings.get("help.topup_stargems"), Color.YELLOW);
        main.textPanel.addPara(Strings.get("help.topup_stargems_1"));
        main.textPanel.addPara(Strings.get("help.topup_stargems_2"));
        main.textPanel.addPara(Strings.format("help.topup_exchange", (int)CasinoConfig.STARGEM_EXCHANGE_RATE));
        main.textPanel.addPara("");

        main.textPanel.addPara(Strings.get("help.topup_purchasing"), Color.YELLOW);
        main.textPanel.addPara(Strings.get("help.topup_purchase_1"));
        main.textPanel.addPara(Strings.get("help.topup_purchase_2"));
        main.textPanel.addPara("");

        main.textPanel.addPara(Strings.get("help.topup_other"), Color.GRAY);
        main.textPanel.addPara(Strings.get("help.topup_other_1"));
        main.textPanel.addPara(Strings.get("help.topup_other_2"));
        main.textPanel.addPara(Strings.get("help.topup_other_3"));

        main.options.addOption(Strings.get("common.back"), "topup_menu");
    }
}
