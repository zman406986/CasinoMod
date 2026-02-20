package data.scripts.casino.interaction;

import data.scripts.casino.CasinoConfig;
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
        handlers.put("gacha_menu", option -> main.gacha.handle(option));
        handlers.put("play", option -> main.poker.handle(option));
        handlers.put("arena_lobby", option -> main.arena.handle(option));
        handlers.put("financial_menu", option -> main.financial.handle(option));
        handlers.put("topup_menu", option -> main.topup.handle(option));
        handlers.put("arena_status", option -> main.arena.handle(option));
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

    public void showIntroPage() {
        main.options.clearOptions();
        main.textPanel.addPara("--- Interastral Peace Casino ---", Color.CYAN);
        main.textPanel.addPara("Welcome! Use 'How to Play' anytime to learn about the games and features.");
        main.options.addOption("Continue to Casino", "back_menu");
    }

    public void showMainMenu() {
        showGeneralHelp();
    }

    public void showGeneralHelp() {
        main.options.clearOptions();
        main.textPanel.addPara("--- How to Play ---", Color.CYAN);
        
        main.textPanel.addPara("Stargems:", Color.YELLOW);
        main.textPanel.addPara("- Casino currency (1 Gem = " + (int)CasinoConfig.STARGEM_EXCHANGE_RATE + " Credits)");
        main.textPanel.addPara("- Buy Stargems at the Top-Up terminal");
        main.textPanel.addPara("- Sell ships at Financial Services for Stargems");
        
        main.textPanel.addPara("VIP Pass:", Color.YELLOW);
        main.textPanel.addPara("- Lasts " + CasinoConfig.VIP_PASS_DAYS + " days");
        main.textPanel.addPara("- " + CasinoConfig.VIP_DAILY_REWARD + " Stargems daily reward");
        main.textPanel.addPara("- Overdraft access (spend beyond balance)");
        main.textPanel.addPara("- Reduced interest on debt");
        
        main.textPanel.addPara("Games:", Color.GRAY);
        main.textPanel.addPara("- Poker: Texas Hold'em against the dealer");
        main.textPanel.addPara("- Arena: Bet on ships in a battle royale");
        main.textPanel.addPara("- Gacha: Pull for ships with pity system");

        main.options.addOption("About Poker", "how_to_poker");
        main.options.addOption("About Arena", "how_to_arena");
        main.options.addOption("About Gacha", "how_to_gacha");
        main.options.addOption("Back", "back_menu");
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
        main.textPanel.addPara("\n--- Spiral Abyss Arena ---", Color.CYAN);
        main.textPanel.addPara("Bet on ships to be the last one standing in a battle royale!");
        main.textPanel.addPara("");
        main.textPanel.addPara("How it works:", Color.GRAY);
        main.textPanel.addPara("- Bet on as many ships as you want");
        main.textPanel.addPara("- Your bet is locked at the odds you see when placing it");
        main.textPanel.addPara("- You can keep betting between rounds");
        main.textPanel.addPara("- Ships get bonuses for kills and surviving longer");
        main.textPanel.addPara("- Even eliminated ships can earn consolation prizes");
        main.textPanel.addPara("- Random chaos events may occur between rounds");

        main.options.addOption("Back", returnTo);
    }

    public void showArenaHelp() {
        showArenaHelp("arena_lobby");
    }

    public void showGachaHelp() {
        main.options.clearOptions();
        main.textPanel.addPara("\n--- Tachy-Impact ---", Color.CYAN);
        
        main.textPanel.addPara("How it works:", Color.YELLOW);
        main.textPanel.addPara("- Pull for ships using Stargems (" + CasinoConfig.GACHA_COST + " per pull)");
        main.textPanel.addPara("- Choose 1x or 10x pull");
        
        main.textPanel.addPara("Ship Rarities:", Color.GRAY);
        main.textPanel.addPara("- 5*: Capital ships (rare)");
        main.textPanel.addPara("- 4*: Cruisers (uncommon)");
        main.textPanel.addPara("- 3*: Destroyers and frigates (common)");
        
        main.textPanel.addPara("Pity System:", Color.GRAY);
        main.textPanel.addPara("- 5* guaranteed by pull " + CasinoConfig.PITY_HARD_5);
        main.textPanel.addPara("- 4* guaranteed by pull " + CasinoConfig.PITY_HARD_4);
        main.textPanel.addPara("- 50/50: If you get a non-featured 5*, next 5* is guaranteed featured");
        
        main.textPanel.addPara("After Pulling:", Color.GRAY);
        main.textPanel.addPara("- Keep ships for your fleet or convert to Stargems");

        main.options.addOption("Back", "gacha_menu");
    }

    public void showFinancialHelp() {
        main.options.clearOptions();
        main.textPanel.addPara("\n--- Financial Services ---", Color.CYAN);
        
        main.textPanel.addPara("VIP Pass:", Color.YELLOW);
        main.textPanel.addPara("- Cost: " + CasinoConfig.VIP_PASS_COST + " Credits for " + CasinoConfig.VIP_PASS_DAYS + " days");
        main.textPanel.addPara("- Daily Reward: " + CasinoConfig.VIP_DAILY_REWARD + " Stargems");
        main.textPanel.addPara("- Overdraft access: Spend beyond your balance");
        main.textPanel.addPara("- Reduced interest on debt");
        main.textPanel.addPara("- Higher credit ceiling");
        main.textPanel.addPara("");

        main.textPanel.addPara("Debt & Interest:", Color.YELLOW);
        main.textPanel.addPara("- Negative balances accrue daily interest");
        main.textPanel.addPara("- Debt has a maximum limit");
        main.textPanel.addPara("- Corporate Reconciliation Teams may be dispatched for severe debt!", Color.RED);
        main.textPanel.addPara("");

        main.textPanel.addPara("Ship Trading:", Color.YELLOW);
        main.textPanel.addPara("- Sell ships for Stargems");
        main.textPanel.addPara("- Ships cannot be bought back!", Color.RED);

        main.options.addOption("Back", "financial_menu");
    }

    public void showTopupHelp() {
        main.options.clearOptions();
        main.textPanel.addPara("\n--- Stargem Top-Up ---", Color.CYAN);
        
        main.textPanel.addPara("Stargems:", Color.YELLOW);
        main.textPanel.addPara("- Casino's premium currency");
        main.textPanel.addPara("- Used for all casino games");
        main.textPanel.addPara("- Exchange Rate: 1 Stargem = " + (int)CasinoConfig.STARGEM_EXCHANGE_RATE + " Credits");
        main.textPanel.addPara("");

        main.textPanel.addPara("Purchasing Stargems:", Color.YELLOW);
        main.textPanel.addPara("- Buy gem packages with credits");
        main.textPanel.addPara("- Larger packages offer better value");
        main.textPanel.addPara("");

        main.textPanel.addPara("Other Ways to Get Stargems:", Color.GRAY);
        main.textPanel.addPara("- VIP daily reward");
        main.textPanel.addPara("- Sell ships at Financial Services");
        main.textPanel.addPara("- Win at Poker or Arena");

        main.options.addOption("Back", "topup_menu");
    }
}
