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
        handlers.put("how_to_arena", option -> showArenaHelp("arena_lobby"));
        handlers.put("how_to_gacha", option -> showGachaHelp());
        handlers.put("how_to_financial", option -> showFinancialHelp());
        handlers.put("how_to_topup", option -> showTopupHelp());
        handlers.put("back_menu", option -> main.showMenu());
        handlers.put("gacha_menu", main.gacha::handle);
        handlers.put("play", main.poker::handle);
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
        main.options.addOption(Strings.get("help.about_arena"), "how_to_arena");
        main.options.addOption(Strings.get("help.about_gacha"), "how_to_gacha");
        main.options.addOption(Strings.get("common.back"), "back_menu");
        main.setState(CasinoInteraction.State.HELP);
    }

    public void showPokerHelp() {
        main.options.clearOptions();
        main.textPanel.addPara("\n--- Texas Hold'em ---", Color.CYAN);
        main.textPanel.addPara("Make the best 5-card hand using your 2 hole cards and 5 community cards.");
        main.textPanel.addPara("");
        main.textPanel.addPara("Hand Rankings (Strongest to Weakest):", Color.GRAY);
        main.textPanel.addPara("1. Royal Flush - A, K, Q, J, 10, same suit");
        main.textPanel.addPara("2. Straight Flush - Five consecutive same suit");
        main.textPanel.addPara("3. Four of a Kind - Four same rank");
        main.textPanel.addPara("4. Full House - Three of a kind + pair");
        main.textPanel.addPara("5. Flush - Five same suit");
        main.textPanel.addPara("6. Straight - Five consecutive");
        main.textPanel.addPara("7. Three of a Kind - Three same rank");
        main.textPanel.addPara("8. Two Pair - Two different pairs");
        main.textPanel.addPara("9. One Pair - Two same rank");
        main.textPanel.addPara("10. High Card - Highest single card");
        main.textPanel.addPara("");
        main.textPanel.addPara("Betting Actions:", Color.GRAY);
        main.textPanel.addPara("- Call: Match the current bet");
        main.textPanel.addPara("- Check: Pass (only if no bet to call)");
        main.textPanel.addPara("- Raise: Increase the bet");
        main.textPanel.addPara("- Fold: Surrender and forfeit bets");
        main.textPanel.addPara("");
        main.textPanel.addPara("Leaving mid-hand or flipping the table forfeits your bet!", Color.RED);

        if (main.poker.getPokerGame() != null &&
            main.poker.getPokerGame().getState() != null &&
            main.poker.getPokerGame().getState().playerStack > 0 &&
            main.poker.getPokerGame().getState().playerHand != null &&
            !main.poker.getPokerGame().getState().playerHand.isEmpty()) {
            main.options.addOption("Back to Game", "poker_back_action");
        } else {
            main.options.addOption("Back", "play");
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
