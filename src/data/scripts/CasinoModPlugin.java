package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import data.scripts.casino.CasinoConfig;
import data.scripts.casino.CasinoVIPManager;
import data.scripts.casino.CasinoMarketInteractionListener;

/**
 * The Casino Mod - Main Lifecycle Plugin
 * 
 * ROLE: Entry point for the Starsector mod.
 * This class handles mod loading and background script initialization.
 * 
 * ARCHITECTURE (Modularized):
 * 1. CasinoModPlugin (this): Lifecycle & setup.
 * 2. CasinoInteraction: Main interaction routing.
 * 3. Specialized Handlers: PokerHandler, ArenaHandler, GachaHandler.
 * 4. Managers: CasinoVIPManager (Background), CasinoGachaManager (Data).
 * 5. CasinoMarketInteractionListener: Market interaction management
 * 
 * LEARNERS: Keeping this file clean makes it easier to manage the "brain" 
 * of your mod without getting lost in UI and minigame code.
 * 
 * MOD DEVELOPMENT NOTES FOR BEGINNERS:
 * - BaseModPlugin is the base class for all Starsector mods
 * - onApplicationLoad() is called once when the game starts up
 * - onGameLoad() is called every time a save game is loaded
 * - Global.getLogger() provides logging functionality
 * - Global.getSector() gives access to the game world
 * - addTransientScript() registers scripts that run continuously
 * - ListenerManager tracks game events
 */
public class CasinoModPlugin extends BaseModPlugin {

    /**
     * Called once when Starsector launches.
     * 
     * MOD DEVELOPMENT NOTES FOR BEGINNERS:
     * - This is where you initialize static data that persists across saves
     * - Loading configuration files is a typical use case
     * - Avoid heavy computation here as it blocks game startup
     */
    @Override
    public void onApplicationLoad() {
        Global.getLogger(this.getClass()).info("The Casino Mod Loaded");
        // Load configuration settings from JSON file
        CasinoConfig.loadSettings();
    }

    /**
     * Called whenever a save game is loaded.
     * 
     * MOD DEVELOPMENT NOTES FOR BEGINNERS:
     * - This is where you set up runtime elements that need to be recreated on each game load
     * - Register event listeners, background scripts, and temporary data here
     * - The "newGame" parameter tells you if this is a fresh game vs a save load
     */
    @Override
    public void onGameLoad(boolean newGame) {
        Global.getLogger(this.getClass()).info("The Casino Mod: Game Loaded");
        // Start the background loyalty/debt script that runs continuously
        // This manages VIP daily rewards, debt interest, and other persistent game logic
        Global.getSector().addTransientScript(new CasinoVIPManager());

        // Register the casino market interaction listener
        // This adds the "Visit Private Lounge" option to compatible markets
        Global.getSector().getListenerManager().addListener(new CasinoMarketInteractionListener(), true);
    }
}