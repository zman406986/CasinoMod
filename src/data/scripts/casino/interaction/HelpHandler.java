package data.scripts.casino.interaction;

import data.scripts.casino.CasinoConfig;
import java.awt.Color;

/**
 * Manages the educational handbook for all casino features
 */
public class HelpHandler {

    private final CasinoInteraction main;

    public HelpHandler(CasinoInteraction main) {
        this.main = main;
    }

    /**
     * Handles routing to different help topics
     */
    public void handle(String option) {
        if ("how_to_play_main".equals(option)) {
            showGeneralHelp();
        } else if ("how_to_poker".equals(option)) {
            showPokerHelp();
        } else if ("how_to_arena".equals(option)) {
            showArenaHelp();
        } else if ("how_to_gacha".equals(option)) {
            showGachaHelp();
        } else if ("back_menu".equals(option)) {
            main.showMenu();
        } else if ("gacha_menu".equals(option)) {
            // If coming from help, route to gacha handler
            main.gacha.handle(option);
        } else if ("play".equals(option)) {
            // If coming from help, route to poker handler
            main.poker.handle(option);
        } else if ("arena_lobby".equals(option)) {
            // If coming from help, route to arena handler
            main.arena.handle(option);
        } else {
            // Default fallback - show main menu
            main.showMenu();
        }
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
}