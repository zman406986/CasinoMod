package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import data.scripts.casino.CasinoConfig;
import data.scripts.casino.CasinoDebtScript;
import data.scripts.casino.CasinoVIPManager;
import data.scripts.casino.CasinoMarketInteractionListener;

/**
 * Main entry point for the Interastral Peace Casino mod.
 * 
 * ARCHITECTURE OVERVIEW:
 * This mod implements a casino system with three main games:
 * 1. Texas Hold'em Poker - Card game with AI opponent
 * 2. Spiral Abyss Arena - Battle royale betting simulation
 * 3. Tachy-Impact Gacha - Ship collection with pity system
 * 
 * CURRENCY SYSTEM:
 * - Stargems: Internal casino currency (1 Gem = 1000 Credits exchange rate)
 * - VIP Pass: Subscription that grants daily rewards, overdraft access, and interest discounts
 * - IPC Credit: Overdraft facility allowing negative balances up to a credit ceiling
 * 
 * MEMORY PERSISTENCE:
 * All player data is stored in Global.getSector().getPlayerMemoryWithoutUpdate()
 * using keys prefixed with "$ipc_" (Interastral Peace Casino).
 * These values persist across game sessions and are saved with the player character.
 * 
 * AI_AGENT NOTES:
 * - Always use CasinoVIPManager for balance/credit operations - never modify memory directly
 * - All monetary values are in Stargems (integers), not credits
 * - VIP status affects multiple systems (interest rates, daily rewards, credit ceilings)
 * - Debt collection is handled by CasinoDebtScript running as a transient script
 */
public class CasinoModPlugin extends BaseModPlugin {

    /**
     * Called when the game application loads (before any game is loaded).
     * Loads configuration from modSettings.json.
     * 
     * AI_AGENT NOTE: This runs once per application launch, not per game load.
     * Configuration is static and shared across all save games.
     */
    @Override
    public void onApplicationLoad() {
        Global.getLogger(this.getClass()).info("The Casino Mod Loaded");
        // Load configuration settings from JSON file
        CasinoConfig.loadSettings();
    }

    /**
     * Called when a game is loaded (new or existing save).
     * Initializes all casino systems and registers listeners.
     * 
     * SYSTEM INITIALIZATION ORDER:
     * 1. CasinoVIPManager.initializeSystem() - Sets up VIP data structure if not present
     * 2. CasinoVIPManager (transient script) - Handles daily rewards, interest, VIP countdown
     * 3. CasinoDebtScript (transient script) - Manages debt collectors and persistent debt logic
     * 4. CasinoMarketInteractionListener - Adds casino option to market interactions
     * 
     * AI_AGENT NOTE: Transient scripts are NOT saved - they must be re-added every game load.
     * This is intentional as they contain no state; all state is in player memory.
     */
    @Override
    public void onGameLoad(boolean newGame) {
        Global.getLogger(this.getClass()).info("The Casino Mod: Game Loaded");
        // Initialize the casino system when the game loads
        CasinoVIPManager.initializeSystem();
        // Start the VIP manager script that handles daily rewards, debt interest, and VIP countdown
        Global.getSector().addTransientScript(new CasinoVIPManager());
        // Start the background debt management script that runs continuously
        // This manages debt collector spawning and other persistent debt-related game logic
        Global.getSector().addTransientScript(new CasinoDebtScript());

        // Register the casino market interaction listener
        // This adds the "Visit Private Lounge" option to compatible markets
        Global.getSector().getListenerManager().addListener(new CasinoMarketInteractionListener(), true);
    }
}
