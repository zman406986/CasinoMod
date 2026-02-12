package data.scripts.casino.interaction;

import data.scripts.casino.CasinoConfig;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/**
 * Handler for displaying help documentation and game rules.
 * 
 * RESPONSIBILITIES:
 * - Display general casino help (currency, financial laws, continuity)
 * - Display poker rules (hand rankings, game flow, betting actions)
 * - Display arena help (betting system, chaos events, strategy)
 * - Display gacha help (rarities, pity mechanics, rotation)
 * 
 * NAVIGATION:
 * HelpHandler supports returning to active games. When the player views help
 * during an active poker game, the "Back" option returns to the game instead
 * of the main menu.
 * 
 * AI_AGENT NOTES:
 * - All help text uses Color constants for visual hierarchy
 * - Cyan: Headers and section titles
 * - Yellow: Subheaders and important concepts
 * - Gray: Body text and explanations
 * - Green: Positive information (winnings, benefits)
 * - Red: Warnings and negative information
 * 
 * - Help text is static and loaded from CasinoConfig where applicable
 * - Always provide a clear "Back" option that contextually returns to the
 *   appropriate screen (active game or main menu)
 * 
 * @see CasinoInteraction
 * @see CasinoConfig
 */
public class HelpHandler {

    /** Reference to main interaction for UI access and navigation */
    private final CasinoInteraction main;

    /** Map of exact-match option handlers */
    private final Map<String, OptionHandler> handlers = new HashMap<>();
    
    /**
     * Constructor initializes handler map.
     * 
     * @param main Reference to CasinoInteraction for UI access
     */
    public HelpHandler(CasinoInteraction main) {
        this.main = main;
        initializeHandlers();
    }
    
    /**
     * Initializes all option handlers.
     * Maps option IDs to their handling logic using lambda expressions.
     * 
     * AI_AGENT NOTE: When adding new help topics:
     * 1. Add handler mapping here
     * 2. Create display method
     * 3. Add navigation link in appropriate menu
     */
    private void initializeHandlers() {
        // Exact match handlers
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

    /**
     * Main entry point for handling help-related options.
     * Routes to appropriate handler based on option ID.
     * 
     * @param option The option ID to handle
     */
    public void handle(String option) {
        OptionHandler handler = handlers.get(option);
        if (handler != null) {
            handler.handle(option);
            return;
        }
        
        // Handle arena help with contextual return path (e.g., "how_to_arena_arena_status")
        if (option.startsWith("how_to_arena_")) {
            String returnTo = option.replace("how_to_arena_", "");
            showArenaHelp(returnTo);
            return;
        }
        
        // Default fallback - show main menu
        main.showMenu();
    }

    /**
     * Shows the introductory help page.
     * Displayed on first casino visit or via "How to Play" menu.
     *
     * AI_AGENT NOTE: This is a condensed overview. Detailed rules are in
     * the specific help methods (showPokerHelp, showArenaHelp, etc.)
     */
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
        main.textPanel.addPara("- VIP Pass (" + CasinoConfig.VIP_PASS_DAYS + " Days) grants " + CasinoConfig.VIP_DAILY_REWARD + " Stargems daily");
        main.textPanel.addPara("");
        main.textPanel.addPara("Click 'How to Play' anytime for detailed game rules and tips.");
        main.options.addOption("Continue to Casino", "back_menu");
    }

    /**
     * Shows the main help menu.
     * Entry point for accessing all help topics.
     */
    public void showMainMenu() {
        showGeneralHelp();
    }

    /**
     * Displays general casino help information.
     * Covers currency, financial laws, and save/resume functionality.
     *
     * AI_AGENT NOTE: Financial law values are pulled from CasinoConfig
     * to ensure consistency with actual game mechanics.
     */
    public void showGeneralHelp() {
        main.options.clearOptions();
        main.textPanel.addPara("--- Interastral Peace Casino Handbook ---", Color.CYAN);
        main.textPanel.addPara("Stargems & Credits:", Color.GRAY);
        main.textPanel.addPara("- Stargems are the local currency. 1 Gem = " + (int)CasinoConfig.STARGEM_EXCHANGE_RATE + " Credits.");
        main.textPanel.addPara("- VIP Pass (" + CasinoConfig.VIP_PASS_DAYS + " Days) grants " + CasinoConfig.VIP_DAILY_REWARD + " Stargems daily.");
        main.textPanel.addPara("- VIP Pass costs " + CasinoConfig.VIP_PASS_COST + " Credits.");

        main.textPanel.addPara("Financial Laws:", Color.GRAY);
        main.textPanel.addPara("- Debt Ceiling: Base " + (int)CasinoConfig.BASE_DEBT_CEILING + " Stargems + " + CasinoConfig.CEILING_INCREASE_PER_VIP + " per VIP Pass purchased.");
        main.textPanel.addPara("- Interest: " + (int)(CasinoConfig.VIP_DAILY_INTEREST_RATE * 100) + "% daily for VIP, " + (int)(CasinoConfig.NORMAL_DAILY_INTEREST_RATE * 100) + "% daily for non-VIP (applied when balance is negative).");
        main.textPanel.addPara("- Max Debt: Cannot exceed " + (int)(CasinoConfig.MAX_DEBT_MULTIPLIER * 100) + "% of your credit ceiling.");

        main.textPanel.addPara("Ship Trading:", Color.GRAY);
        main.textPanel.addPara("- Ships can be converted to Stargems (base value / " + (int)CasinoConfig.SHIP_TRADE_RATE + " = gems).");
        main.textPanel.addPara("- Sell multiplier: " + (int)(CasinoConfig.SHIP_SELL_MULTIPLIER * 100) + "% of calculated value.");

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

    /**
     * Displays comprehensive poker help.
     * Covers hand rankings, game flow, betting actions, and position strategy.
     *
     * AI_AGENT NOTE: This is the authoritative reference for poker rules.
     * When modifying poker mechanics, update this help text to match.
     */
    public void showPokerHelp() {
        main.options.clearOptions();
        main.textPanel.addPara("\n--- TEXAS HOLD'EM GUIDE ---", Color.CYAN);
        main.textPanel.addPara("Objective:", Color.GRAY);
        main.textPanel.addPara("- Make the best 5-card hand using your 2 hole cards and 5 community cards.");
        main.textPanel.addPara("");
        main.textPanel.addPara("Blinds & Stakes:", Color.GRAY);
        main.textPanel.addPara("- Small Blind: " + CasinoConfig.POKER_SMALL_BLIND + " Stargems");
        main.textPanel.addPara("- Big Blind: " + CasinoConfig.POKER_BIG_BLIND + " Stargems");
        main.textPanel.addPara("- Opponent Stack: " + CasinoConfig.POKER_DEFAULT_OPPONENT_STACK + " Stargems");
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
        main.textPanel.addPara("- Raise: Increase the current bet amount (min " + CasinoConfig.POKER_AI_MIN_RAISE_VALUE + " Stargems).");
        main.textPanel.addPara("- Fold: Surrender hand and forfeit any bets made.");
        main.textPanel.addPara("");
        main.textPanel.addPara("Position & Blinds (Heads-Up):", Color.CYAN);
        main.textPanel.addPara("- Dealer Button: The dealer posts the Small Blind and acts FIRST post-flop.");
        main.textPanel.addPara("- Big Blind: The non-dealer posts the Big Blind and acts LAST pre-flop.");
        main.textPanel.addPara("- Pre-Flop: Big Blind acts last (can check or raise after seeing the flop for free).");
        main.textPanel.addPara("- Post-Flop: Dealer (SB) acts first, Big Blind acts last.");
        main.textPanel.addPara("- Acting last is a strategic advantage - you see what your opponent does first!");
        main.textPanel.addPara("");
        main.textPanel.addPara("Tips:", Color.YELLOW);
        main.textPanel.addPara("- Pot Odds: Consider the ratio of current bet to potential winnings.");
        main.textPanel.addPara("- Bankroll: Manage your stack carefully to stay competitive.");
        main.textPanel.addPara("- The IPC Dealer adapts to your play style. Mix up your strategy!");
        main.textPanel.addPara("- The dealer will always call to see the flop when you just complete the Big Blind.");
        main.textPanel.addPara("- The IPC Dealer calculates pot odds. Don't let them bully you!", Color.ORANGE);

        // Check if we're in an active poker game (has cards dealt AND player has chips)
        if (main.poker.getPokerGame() != null &&
            main.poker.getPokerGame().getState() != null &&
            main.poker.getPokerGame().getState().playerStack > 0 &&
            main.poker.getPokerGame().getState().playerHand != null &&
            !main.poker.getPokerGame().getState().playerHand.isEmpty()) {
            main.options.addOption("Back to Game", "poker_back_action"); // Return to active game
        } else {
            main.options.addOption("Back", "play"); // Go back to poker menu when not in a game
        }
    }

    /**
     * Displays comprehensive arena help.
     * Covers betting system, dynamic odds, performance bonuses, and chaos events.
     *
     * AI_AGENT NOTE: Odds calculations and bonus formulas are documented here.
     * When modifying arena mechanics, update this help text to match.
     * 
     * @param returnTo The option ID to return to after viewing help (for contextual navigation)
     */
    public void showArenaHelp(String returnTo) {
        main.options.clearOptions();
        main.textPanel.addPara("\n--- SPIRAL ABYSS ARENA: SURVIVAL ---", Color.CYAN);
        main.textPanel.addPara("Objective:", Color.GRAY);
        main.textPanel.addPara("- Bet on one or more ships to survive the battle royale.");
        main.textPanel.addPara("- Last ship standing wins!");

        main.textPanel.addPara("Entry & Ships:", Color.GRAY);
        main.textPanel.addPara("- Entry Fee: " + CasinoConfig.ARENA_ENTRY_FEE + " Stargems");
        main.textPanel.addPara("- Ships per Battle: " + CasinoConfig.ARENA_SHIP_COUNT);
        main.textPanel.addPara("- Ships have random prefixes and affixes that modify stats!");

        main.textPanel.addPara("Betting System & Dynamic Odds:", Color.YELLOW);
        main.textPanel.addPara("- Base Odds: 1:" + CasinoConfig.ARENA_BASE_ODDS + " (e.g., " + CasinoConfig.ARENA_BASE_ODDS + "x return on win).");
        main.textPanel.addPara("- Positive Perks: Odds reduced by " + (int)((1 - CasinoConfig.ARENA_POSITIVE_PERK_MULTIPLIER) * 100) + "% (stronger ship = lower payout).");
        main.textPanel.addPara("- Negative Perks: Odds increased by " + (int)((CasinoConfig.ARENA_NEGATIVE_PERK_MULTIPLIER - 1) * 100) + "% (weaker ship = higher payout).");
        main.textPanel.addPara("- Minimum Odds: 1:" + CasinoConfig.ARENA_MIN_ODDS);
        main.textPanel.addPara("- You can bet on multiple ships at once.");
        main.textPanel.addPara("");
        main.textPanel.addPara("DYNAMIC ODDS (Mid-Battle Betting):", Color.CYAN);
        main.textPanel.addPara("- Odds change based on current HP and round number!", Color.YELLOW);
        main.textPanel.addPara("- Higher HP = WORSE odds (lower payout) - the ship is more likely to win.");
        main.textPanel.addPara("- Lower HP = BETTER odds (higher payout) - riskier bet, bigger reward.");
        main.textPanel.addPara("- HP Factor Range: " + CasinoConfig.ARENA_MIN_HP_ODDS_MULT + "x to " + CasinoConfig.ARENA_MAX_HP_ODDS_MULT + "x base odds.");
        main.textPanel.addPara("- Later bets have diminishing returns (" + (int)(CasinoConfig.ARENA_DIMINISHING_RETURNS_PER_ROUND * 100) + "% reduction per round, min " + (int)(CasinoConfig.ARENA_DIMINISHING_RETURNS_MIN * 100) + "%).");
        main.textPanel.addPara("- Your bet is LOCKED at the odds shown when you place it.");

        main.textPanel.addPara("Performance Bonuses:", Color.GRAY);
        main.textPanel.addPara("- Survival Bonus: +" + (int)(CasinoConfig.ARENA_SURVIVAL_BONUS_PER_TURN * 100) + "% per turn survived.");
        main.textPanel.addPara("- Kill Bonus: +" + (int)(CasinoConfig.ARENA_KILL_BONUS_PER_KILL * 100) + "% per kill.");
        main.textPanel.addPara("- House Edge: ~10% (survival multiplier: " + CasinoConfig.ARENA_SURVIVAL_REWARD_MULT + "x).");

        main.textPanel.addPara("Consolation Prizes:", Color.GRAY);
        main.textPanel.addPara("- Defeated ships may still earn consolation based on performance.");
        main.textPanel.addPara("- Consolation factor: " + (int)(CasinoConfig.ARENA_DEFEATED_CONSOLATION_MULT * 100) + "% of calculated value.");

        main.textPanel.addPara("Chaos Events:", Color.GRAY);
        main.textPanel.addPara("- Chance per turn: " + (int)(CasinoConfig.ARENA_CHAOS_EVENT_CHANCE * 100) + "%");
        main.textPanel.addPara("- Single Ship Damage: Deals " + (int)(CasinoConfig.ARENA_SINGLE_SHIP_DAMAGE_PERCENT * 100) + "% damage to one ship.");
        main.textPanel.addPara("- Multi Ship Damage: Deals " + (int)(CasinoConfig.ARENA_MULTI_SHIP_DAMAGE_PERCENT * 100) + "% damage to multiple ships.");

        main.textPanel.addPara("Strategy:", Color.YELLOW);
        main.textPanel.addPara("- Bet early for maximum odds (before HP damage and diminishing returns).");
        main.textPanel.addPara("- Look for wounded champions with good perks for high-value mid-battle bets.");
        main.textPanel.addPara("- Higher HP champions are safer but pay less; low HP = high risk, high reward.");
        main.textPanel.addPara("- Watch for chaos events that can change the tide of battle!");

        main.options.addOption("Back", returnTo);
    }

    /**
     * Overload for backward compatibility - returns to arena lobby.
     */
    public void showArenaHelp() {
        showArenaHelp("arena_lobby");
    }

    /**
     * Displays comprehensive gacha help.
     * Covers rarities, pity mechanics, and featured rotation.
     *
     * AI_AGENT NOTE: Pity thresholds and rotation timing are pulled from CasinoConfig.
     * When modifying gacha mechanics, update this help text to match.
     */
    public void showGachaHelp() {
        main.options.clearOptions();
        main.textPanel.addPara("\n--- TACHY-IMPACT: WARP PROTOCOL ---", Color.CYAN);
        main.textPanel.addPara("Cost & Pool:", Color.GRAY);
        main.textPanel.addPara("- Cost per Pull: " + CasinoConfig.GACHA_COST + " Stargems");
        main.textPanel.addPara("- Pool Size: " + CasinoConfig.GACHA_POOL_SIZE + " ships");

        main.textPanel.addPara("Ship Rarities:", Color.GRAY);
        main.textPanel.addPara("- 5* (Capital Ships): " + (CasinoConfig.PROB_5_STAR * 100) + "% base rate. Rare featured ships with high value.");
        main.textPanel.addPara("- 4* (Cruisers): " + (CasinoConfig.PROB_4_STAR * 100) + "% base rate. Featured ships with good value.");
        main.textPanel.addPara("- 3* (Destroyers/Frigates): Common ships fill the remaining rate.");

        main.textPanel.addPara("Pity Mechanics:", Color.GRAY);
        main.textPanel.addPara("- 5* Pity: Guaranteed at " + CasinoConfig.PITY_HARD_5 + " pulls. Soft pity starts at " + CasinoConfig.PITY_SOFT_START_5 + " pulls (rate increases).");
        main.textPanel.addPara("- 4* Pity: Guaranteed at " + CasinoConfig.PITY_HARD_4 + " pulls.");
        main.textPanel.addPara("- 50/50 Rule: If your 5* isn't the featured ship, the next one IS guaranteed to be the featured ship.");

        main.textPanel.addPara("Featured Rotation:", Color.GRAY);
        main.textPanel.addPara("- Featured ships rotate every " + CasinoConfig.GACHA_ROTATION_DAYS + " days.");
        main.textPanel.addPara("- One featured 5* capital and featured 4* cruisers per rotation.");
        main.textPanel.addPara("- Pity carries over between rotations!");

        main.textPanel.addPara("Ship Conversion:", Color.GRAY);
        main.textPanel.addPara("- Ships can be converted to Stargems (base value / " + (int)CasinoConfig.SHIP_TRADE_RATE + " = gems).");
        main.textPanel.addPara("- Sell multiplier: " + (int)(CasinoConfig.SHIP_SELL_MULTIPLIER * 100) + "% of calculated value.");
        main.textPanel.addPara("- You can keep or convert ships after each pull.");

        main.options.addOption("Back", "gacha_menu");
    }

    /**
     * Displays financial services help.
     * Covers VIP subscriptions, credit ceiling, debt mechanics, and ship trading.
     */
    public void showFinancialHelp() {
        main.options.clearOptions();
        main.textPanel.addPara("\n--- FINANCIAL SERVICES GUIDE ---", Color.CYAN);
        
        main.textPanel.addPara("VIP Subscription Pass:", Color.YELLOW);
        main.textPanel.addPara("- Cost: " + CasinoConfig.VIP_PASS_COST + " Credits for " + CasinoConfig.VIP_PASS_DAYS + " days.");
        main.textPanel.addPara("- Daily Reward: " + CasinoConfig.VIP_DAILY_REWARD + " Stargems per day.");
        main.textPanel.addPara("- Overdraft Access: VIPs can spend beyond their balance up to their credit ceiling.");
        main.textPanel.addPara("- Reduced Interest: " + (int)(CasinoConfig.VIP_DAILY_INTEREST_RATE * 100) + "% daily vs " + (int)(CasinoConfig.NON_VIP_DAILY_INTEREST_RATE * 100) + "% for non-VIP.");
        main.textPanel.addPara("- Credit Ceiling Increase: +" + CasinoConfig.CEILING_INCREASE_PER_VIP + " per VIP purchase.");
        main.textPanel.addPara("");

        main.textPanel.addPara("Credit Ceiling & Overdraft:", Color.YELLOW);
        main.textPanel.addPara("- Base Debt Ceiling: " + (int)CasinoConfig.BASE_DEBT_CEILING + " Stargems.");
        main.textPanel.addPara("- Your credit ceiling = Base + (VIP purchases x " + CasinoConfig.CEILING_INCREASE_PER_VIP + ").");
        main.textPanel.addPara("- Available Credit = Credit Ceiling + Current Balance (if negative).");
        main.textPanel.addPara("- VIPs can overdraft (go negative) up to their credit ceiling.");
        main.textPanel.addPara("- Non-VIPs cannot overdraft - must have positive balance to play.");
        main.textPanel.addPara("");

        main.textPanel.addPara("Debt & Interest:", Color.YELLOW);
        main.textPanel.addPara("- Negative balances accrue daily interest.");
        main.textPanel.addPara("- VIP Interest: " + (int)(CasinoConfig.VIP_DAILY_INTEREST_RATE * 100) + "% per day.");
        main.textPanel.addPara("- Non-VIP Interest: " + (int)(CasinoConfig.NON_VIP_DAILY_INTEREST_RATE * 100) + "% per day.");
        main.textPanel.addPara("- Max Debt: Cannot exceed " + (int)(CasinoConfig.MAX_DEBT_MULTIPLIER * 100) + "% of your credit ceiling.");
        main.textPanel.addPara("- Corporate Reconciliation Team: Dispatched for severe debt. They will find you!", Color.RED);
        main.textPanel.addPara("");

        main.textPanel.addPara("Ship Trading:", Color.YELLOW);
        main.textPanel.addPara("- Sell ships for Stargems at Financial Services.");
        main.textPanel.addPara("- Formula: Ship Base Value / " + (int)CasinoConfig.SHIP_TRADE_RATE + " x " + (int)(CasinoConfig.SHIP_SELL_MULTIPLIER * 100) + "% = Stargems received.");
        main.textPanel.addPara("- WARNING: Ships cannot be bought back! This is permanent.", Color.RED);
        main.textPanel.addPara("");

        main.textPanel.addPara("VIP Notifications:", Color.GRAY);
        main.textPanel.addPara("- Toggle between daily or monthly notification mode.");
        main.textPanel.addPara("- Daily: Reminds you each day to collect rewards.");
        main.textPanel.addPara("- Monthly: Single reminder when VIP is about to expire.");

        main.options.addOption("Back", "financial_menu");
    }

    /**
     * Displays Stargem top-up help.
     * Covers exchange rates and package information.
     */
    public void showTopupHelp() {
        main.options.clearOptions();
        main.textPanel.addPara("\n--- STARGEM TOP-UP GUIDE ---", Color.CYAN);
        
        main.textPanel.addPara("What are Stargems?", Color.YELLOW);
        main.textPanel.addPara("- Stargems are the casino's premium currency.");
        main.textPanel.addPara("- Used for all casino games: Poker, Arena, and Gacha.");
        main.textPanel.addPara("- Exchange Rate: 1 Stargem = " + (int)CasinoConfig.STARGEM_EXCHANGE_RATE + " Credits.");
        main.textPanel.addPara("");

        main.textPanel.addPara("Purchasing Stargems:", Color.YELLOW);
        main.textPanel.addPara("- Buy gem packages with credits at the Top-Up terminal.");
        main.textPanel.addPara("- Larger packages offer better value (more gems per credit).");
        main.textPanel.addPara("- Purchases are instant - gems added immediately.");
        main.textPanel.addPara("");

        main.textPanel.addPara("Alternative Ways to Get Stargems:", Color.GRAY);
        main.textPanel.addPara("- VIP Daily Reward: " + CasinoConfig.VIP_DAILY_REWARD + " gems per day with active VIP.");
        main.textPanel.addPara("- Ship Trading: Sell ships at Financial Services.");
        main.textPanel.addPara("- Casino Winnings: Win big at Poker or Arena!");
        main.textPanel.addPara("");

        main.textPanel.addPara("Tips:", Color.CYAN);
        main.textPanel.addPara("- Consider buying VIP first for overdraft access and daily rewards.");
        main.textPanel.addPara("- Larger packages are more cost-effective for frequent players.");
        main.textPanel.addPara("- Keep some gems in reserve for unexpected opportunities.");

        main.options.addOption("Back", "topup_menu");
    }
}
