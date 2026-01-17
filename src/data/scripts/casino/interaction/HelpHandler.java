package data.scripts.casino.interaction;

import data.scripts.casino.CasinoConfig;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class HelpHandler {

    private final CasinoInteraction main;

    private final Map<String, OptionHandler> handlers = new HashMap<>();
    
    public HelpHandler(CasinoInteraction main) {
        this.main = main;
        initializeHandlers();
    }
    
    private void initializeHandlers() {
        // Exact match handlers
        handlers.put("how_to_play_main", option -> showGeneralHelp());
        handlers.put("how_to_poker", option -> showPokerHelp());
        handlers.put("how_to_arena", option -> showArenaHelp());
        handlers.put("how_to_gacha", option -> showGachaHelp());
        handlers.put("back_menu", option -> main.showMenu());
        handlers.put("gacha_menu", option -> main.gacha.handle(option));
        handlers.put("play", option -> main.poker.handle(option));
        handlers.put("arena_lobby", option -> main.arena.handle(option));
    }

    public void handle(String option) {
        OptionHandler handler = handlers.get(option);
        if (handler != null) {
            handler.handle(option);
            return;
        }
        
        // Default fallback - show main menu
        main.showMenu();
    }

    public void showIntroPage() {
        main.options.clearOptions();
        main.textPanel.addPara("--- Interastral Peace Casino Handbook ---", Color.CYAN);
        main.textPanel.addPara("Welcome to the IPC Casino! This handbook will guide you through all the available games and features.");
        main.textPanel.addPara("");
        main.textPanel.addPara("Games Available:", Color.YELLOW);
        main.textPanel.addPara("- Texas Hold'em Poker: Classic card game with strategic betting");
        main.textPanel.addPara("- Spiral Abyss Arena: Battle royale-style arena combat");
        main.textPanel.addPara("- Tachy-Impact Gacha: Collect ships and equipment with pity system");
        main.textPanel.addPara("");
        main.textPanel.addPara("Currency & Economy:", Color.YELLOW);
        main.textPanel.addPara("- Stargems are the local currency (1 Gem = " + (int)CasinoConfig.STARGEM_EXCHANGE_RATE + " Credits)");
        main.textPanel.addPara("- VIP Pass grants exclusive reputation bonuses");
        main.textPanel.addPara("");
        main.textPanel.addPara("Click 'How to Play' anytime for detailed game rules and tips.");
        main.options.addOption("Continue to Casino", "back_menu");
    }

    public void showMainMenu() {
        showGeneralHelp();
    }

    public void showGeneralHelp() {
        main.options.clearOptions();
        main.textPanel.addPara("--- Interastral Peace Casino Handbook ---", Color.CYAN);
        main.textPanel.addPara("Stargems & Credits:", Color.GRAY);
        main.textPanel.addPara("- Stargems are the local currency. 1 Gem = " + (int)CasinoConfig.STARGEM_EXCHANGE_RATE + " Credits.");
        main.textPanel.addPara("- VIP Pass (30 Days) grants exclusive reputation bonuses.");
        
        main.textPanel.addPara("Financial Laws:", Color.GRAY);
        main.textPanel.addPara("- Debt Ceiling: Based on total Credits spent + Tri-Tachyon Rep.");
        main.textPanel.addPara("- Interest: 5% accumulated on the 15th if balance is negative.");
        
        main.textPanel.addPara("Continuity (Save/Resume):", Color.GRAY);
        main.textPanel.addPara("- You can 'Tell Them to Wait' to suspend an active game.");
        main.textPanel.addPara("- Suspended games persist in memory and can be resumed later.");
        main.textPanel.addPara("- WARNING: Leaving mid-hand or flipping the table forfeits your current bet!", Color.RED);
        
        main.options.addOption("About Poker (Hands & Rules)", "how_to_poker");
        main.options.addOption("About Spiral Abyss Arena", "how_to_arena");
        main.options.addOption("About Tachy-Impact", "how_to_gacha");
        main.options.addOption("Back", "back_menu");
        main.setState(CasinoInteraction.State.HELP);
    }

    public void showPokerHelp() {
        main.options.clearOptions();
        main.textPanel.addPara("\n--- TEXAS HOLD'EM GUIDE ---", Color.CYAN);
        main.textPanel.addPara("Objective:", Color.GRAY);
        main.textPanel.addPara("- Make the best 5-card hand using your 2 hole cards and 5 community cards.");
        main.textPanel.addPara("");
        main.textPanel.addPara("Hand Rankings (Strongest to Weakest):", Color.GRAY);
        main.textPanel.addPara("1. Royal Flush - A, K, Q, J, 10, all same suit");
        main.textPanel.addPara("2. Straight Flush - Five consecutive cards of same suit");
        main.textPanel.addPara("3. Four of a Kind - Four cards of same rank");
        main.textPanel.addPara("4. Full House - Three of a kind + one pair");
        main.textPanel.addPara("5. Flush - Five cards of same suit");
        main.textPanel.addPara("6. Straight - Five consecutive cards");
        main.textPanel.addPara("7. Three of a Kind - Three cards of same rank");
        main.textPanel.addPara("8. Two Pair - Two different pairs");
        main.textPanel.addPara("9. One Pair - Two cards of same rank");
        main.textPanel.addPara("10. High Card - Highest single card if no other hand");
        main.textPanel.addPara("");
        main.textPanel.addPara("Game Flow:", Color.CYAN);
        main.textPanel.addPara("- Pre-Flop: Initial 2 cards dealt to each player.");
        main.textPanel.addPara("- Betting Round 1: Players bet based on initial hand.");
        main.textPanel.addPara("- Flop: First 3 community cards revealed.");
        main.textPanel.addPara("- Betting Round 2: More betting occurs.");
        main.textPanel.addPara("- Turn: 4th community card revealed.");
        main.textPanel.addPara("- Betting Round 3: Continued betting.");
        main.textPanel.addPara("- River: 5th and final community card revealed.");
        main.textPanel.addPara("- Final Betting Round: Last opportunity to bet.");
        main.textPanel.addPara("- Showdown: Remaining players reveal hands.");
        main.textPanel.addPara("");
        main.textPanel.addPara("Betting Actions:", Color.CYAN);
        main.textPanel.addPara("- Call: Match the current bet amount.");
        main.textPanel.addPara("- Check: Pass action without betting (only if no bet to call).");
        main.textPanel.addPara("- Raise: Increase the current bet amount.");
        main.textPanel.addPara("- Fold: Surrender hand and forfeit any bets made.");
        main.textPanel.addPara("");
        main.textPanel.addPara("Tips:", Color.YELLOW);
        main.textPanel.addPara("- Blinds: Small blind is posted by player after dealer, big blind by next player.");
        main.textPanel.addPara("- Position: Being the dealer (or acting last) gives strategic advantage.");
        main.textPanel.addPara("- Pot Odds: Consider the ratio of current bet to potential winnings.");
        main.textPanel.addPara("- Bankroll: Manage your stack carefully to stay competitive.");
        main.textPanel.addPara("- The IPC Dealer calculates pot odds. Don't let them bully you!", Color.ORANGE);
        
        main.options.addOption("Back", "play"); // Go back to poker menu
    }

    public void showArenaHelp() {
        main.options.clearOptions();
        main.textPanel.addPara("\n--- SPIRAL ABYSS ARENA: SURVIVAL ---", Color.CYAN);
        main.textPanel.addPara("Chaos Events:", Color.GRAY);
        main.textPanel.addPara("- Mid-battle events can trigger meteor strikes, repairs, or combat buffs.");
        main.textPanel.addPara("- Pay attention to the ticker to anticipate game-changing shifts!");
        
        main.textPanel.addPara("Strategy:", Color.GRAY);
        main.textPanel.addPara("- Switching: You can switch champions, but it costs 50% of the ante and halves the multiplier.");
        main.textPanel.addPara("- Scaling: Multipliers drop as ships die. Bet early for maximum profit!", Color.ORANGE);
        
        main.options.addOption("Back", "arena_lobby"); // Go back to arena menu
    }

    public void showGachaHelp() {
        main.options.clearOptions();
        main.textPanel.addPara("\n--- TACHY-IMPACT: WARP PROTOCOL ---", Color.CYAN);
        main.textPanel.addPara("Pity Mechanics:", Color.GRAY);
        main.textPanel.addPara("- 5* Pity: Guaranteed at " + CasinoConfig.PITY_HARD_5 + " pulls. Chance increases after " + CasinoConfig.PITY_SOFT_START_5 + ".");
        main.textPanel.addPara("- 50/50 Rule: If your 5* isn't the featured ship, the next one IS guaranteed featuring ship.");
        
        main.textPanel.addPara("Inventory:", Color.GRAY);
        main.textPanel.addPara("- Duplicate chips are converted to Stargems automatically via the Blacklist.");
        main.textPanel.addPara("- Featured ships rotate bi-weekly. Pity carries over between rotations!", Color.GREEN);
        
        main.options.addOption("Back", "gacha_menu"); // Go back to gacha menu
    }
    
    @FunctionalInterface
    private interface OptionHandler {
        void handle(String option);
    }
}