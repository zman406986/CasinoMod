package data.scripts.casino.interaction;

import data.scripts.casino.CasinoConfig;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Manages the educational handbook for all casino features
 */
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

    /**
     * Handles routing to different help topics
     */
    public void handle(String option) {
        OptionHandler handler = handlers.get(option);
        if (handler != null) {
            handler.handle(option);
            return;
        }
        
        // Default fallback - show main menu
        main.showMenu();
    }

    /**
     * Displays the introductory page for the handbook
     */
    public void showIntroPage() {
        main.options.clearOptions();
        main.textPanel.addParagraph("--- Interastral Peace Casino Handbook ---", Color.CYAN);
        main.textPanel.addParagraph("Welcome to the IPC Casino!");
        main.textPanel.addParagraph("This handbook will guide you through all the available games and features.");
        main.textPanel.addParagraph("");
        main.textPanel.addParagraph("Games Available:", Color.YELLOW);
        main.textPanel.addParagraph("- Texas Hold'em Poker: Classic card game with strategic betting");
        main.textPanel.addParagraph("- Spiral Abyss Arena: Battle royale-style arena combat");
        main.textPanel.addParagraph("- Tachy-Impact Gacha: Collect ships and equipment with pity system");
        main.textPanel.addParagraph("");
        main.textPanel.addParagraph("Currency & Economy:", Color.YELLOW);
        main.textPanel.addParagraph("- Stargems are the local currency (1 Gem = " + (int)CasinoConfig.STARGEM_EXCHANGE_RATE + " Credits)");
        main.textPanel.addParagraph("- VIP Pass grants exclusive reputation bonuses");
        main.textPanel.addParagraph("");
        main.textPanel.addParagraph("Click 'How to Play' anytime for detailed game rules and tips.");
        main.options.addOption("Continue to Casino", "back_menu");
    }

    /**
     * Displays the main help menu with links to specific game help
     */
    public void showMainMenu() {
        showGeneralHelp();
    }

    /**
     * Displays general casino information and mechanics
     */
    public void showGeneralHelp() {
        main.options.clearOptions();
        main.textPanel.addParagraph("--- Interastral Peace Casino Handbook ---", Color.CYAN);
        main.textPanel.addParagraph("Stargems & Credits:", Color.GRAY);
        main.textPanel.addParagraph("- Stargems are the local currency. 1 Gem = " + (int)CasinoConfig.STARGEM_EXCHANGE_RATE + " Credits.");
        main.textPanel.addParagraph("- VIP Pass (30 Days) grants exclusive reputation bonuses.");
        
        main.textPanel.addParagraph("Financial Laws:", Color.GRAY);
        main.textPanel.addParagraph("- Debt Ceiling: Based on total Credits spent + Tri-Tachyon Rep.");
        main.textPanel.addParagraph("- Interest: 5% accumulated on the 15th if balance is negative.");
        
        main.textPanel.addParagraph("Continuity (Save/Resume):", Color.GRAY);
        main.textPanel.addParagraph("- You can 'Tell Them to Wait' to suspend an active game.");
        main.textPanel.addParagraph("- Suspended games persist in memory and can be resumed later.");
        main.textPanel.addParagraph("- WARNING: Leaving mid-hand or flipping the table forfeits your current bet!", Color.RED);
        
        main.options.addOption("About Poker (Hands & Rules)", "how_to_poker");
        main.options.addOption("About Spiral Abyss Arena", "how_to_arena");
        main.options.addOption("About Tachy-Impact", "how_to_gacha");
        main.options.addOption("Back", "back_menu");
        main.setState(CasinoInteraction.State.HELP);
    }

    /**
     * Displays poker rules and gameplay information
     */
    public void showPokerHelp() {
        main.options.clearOptions();
        main.textPanel.addParagraph("\n--- TEXAS HOLD'EM GUIDE ---", Color.CYAN);
        main.textPanel.addParagraph("Objective:", Color.GRAY);
        main.textPanel.addParagraph("- Make the best 5-card hand using your 2 hole cards and 5 community cards.");
        main.textPanel.addParagraph("");
        main.textPanel.addParagraph("Hand Rankings (Strongest to Weakest):", Color.GRAY);
        main.textPanel.addParagraph("1. Royal Flush - A, K, Q, J, 10, all same suit");
        main.textPanel.addParagraph("2. Straight Flush - Five consecutive cards of same suit");
        main.textPanel.addParagraph("3. Four of a Kind - Four cards of same rank");
        main.textPanel.addParagraph("4. Full House - Three of a kind + one pair");
        main.textPanel.addParagraph("5. Flush - Five cards of same suit");
        main.textPanel.addParagraph("6. Straight - Five consecutive cards");
        main.textPanel.addParagraph("7. Three of a Kind - Three cards of same rank");
        main.textPanel.addParagraph("8. Two Pair - Two different pairs");
        main.textPanel.addParagraph("9. One Pair - Two cards of same rank");
        main.textPanel.addParagraph("10. High Card - Highest single card if no other hand");
        main.textPanel.addParagraph("");
        main.textPanel.addParagraph("Game Flow:", Color.CYAN);
        main.textPanel.addParagraph("- Pre-Flop: Initial 2 cards dealt to each player.");
        main.textPanel.addParagraph("- Betting Round 1: Players bet based on initial hand.");
        main.textPanel.addParagraph("- Flop: First 3 community cards revealed.");
        main.textPanel.addParagraph("- Betting Round 2: More betting occurs.");
        main.textPanel.addParagraph("- Turn: 4th community card revealed.");
        main.textPanel.addParagraph("- Betting Round 3: Continued betting.");
        main.textPanel.addParagraph("- River: 5th and final community card revealed.");
        main.textPanel.addParagraph("- Final Betting Round: Last opportunity to bet.");
        main.textPanel.addParagraph("- Showdown: Remaining players reveal hands.");
        main.textPanel.addParagraph("");
        main.textPanel.addParagraph("Betting Actions:", Color.CYAN);
        main.textPanel.addParagraph("- Call: Match the current bet amount.");
        main.textPanel.addParagraph("- Check: Pass action without betting (only if no bet to call).");
        main.textPanel.addParagraph("- Raise: Increase the current bet amount.");
        main.textPanel.addParagraph("- Fold: Surrender hand and forfeit any bets made.");
        main.textPanel.addParagraph("");
        main.textPanel.addParagraph("Tips:", Color.YELLOW);
        main.textPanel.addParagraph("- Blinds: Small blind is posted by player after dealer, big blind by next player.");
        main.textPanel.addParagraph("- Position: Being the dealer (or acting last) gives strategic advantage.");
        main.textPanel.addParagraph("- Pot Odds: Consider the ratio of current bet to potential winnings.");
        main.textPanel.addParagraph("- Bankroll: Manage your stack carefully to stay competitive.");
        main.textPanel.addParagraph("- The IPC Dealer calculates pot odds. Don't let them bully you!", Color.ORANGE);
        
        main.options.addOption("Back", "play"); // Go back to poker menu
    }

    /**
     * Displays arena rules and gameplay information
     */
    public void showArenaHelp() {
        main.options.clearOptions();
        main.textPanel.addParagraph("\n--- SPIRAL ABYSS ARENA: SURVIVAL ---", Color.CYAN);
        main.textPanel.addParagraph("Chaos Events:", Color.GRAY);
        main.textPanel.addParagraph("- Mid-battle events can trigger meteor strikes, repairs, or combat buffs.");
        main.textPanel.addParagraph("- Pay attention to the ticker to anticipate game-changing shifts!");
        
        main.textPanel.addParagraph("Strategy:", Color.GRAY);
        main.textPanel.addParagraph("- Switching: You can switch champions, but it costs 50% of the ante and halves the multiplier.");
        main.textPanel.addParagraph("- Scaling: Multipliers drop as ships die. Bet early for maximum profit!", Color.ORANGE);
        
        main.options.addOption("Back", "arena_lobby"); // Go back to arena menu
    }

    /**
     * Displays gacha mechanics and pity system information
     */
    public void showGachaHelp() {
        main.options.clearOptions();
        main.textPanel.addParagraph("\n--- TACHY-IMPACT: WARP PROTOCOL ---", Color.CYAN);
        main.textPanel.addParagraph("Pity Mechanics:", Color.GRAY);
        main.textPanel.addParagraph("- 5* Pity: Guaranteed at " + CasinoConfig.PITY_HARD_5 + " pulls. Chance increases after " + CasinoConfig.PITY_SOFT_START_5 + ".");
        main.textPanel.addParagraph("- 50/50 Rule: If your 5* isn't the featured ship, the next one IS guaranteed featuring ship.");
        
        main.textPanel.addParagraph("Inventory:", Color.GRAY);
        main.textPanel.addParagraph("- Duplicate chips are converted to Stargems automatically via the Blacklist.");
        main.textPanel.addParagraph("- Featured ships rotate bi-weekly. Pity carries over between rotations!", Color.GREEN);
        
        main.options.addOption("Back", "gacha_menu"); // Go back to gacha menu
    }
    
    @FunctionalInterface
    private interface OptionHandler {
        void handle(String option);
    }
}