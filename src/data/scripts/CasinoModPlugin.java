package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import data.scripts.casino.CasinoConfig;
import data.scripts.casino.CasinoDebtScript;
import data.scripts.casino.CasinoVIPManager;
import data.scripts.casino.CasinoMarketInteractionListener;

public class CasinoModPlugin extends BaseModPlugin {

    @Override
    public void onApplicationLoad() {
        Global.getLogger(this.getClass()).info("The Casino Mod Loaded");
        // Load configuration settings from JSON file
        CasinoConfig.loadSettings();
    }

    @Override
    public void onGameLoad(boolean newGame) {
        Global.getLogger(this.getClass()).info("The Casino Mod: Game Loaded");
        // Initialize the debt system when the game loads
        CasinoVIPManager.initializeDebtSystem();
        // Start the background debt management script that runs continuously
        // This manages debt interest, debt collector spawning, and other persistent debt-related game logic
        Global.getSector().addTransientScript(new CasinoDebtScript());

        // Register the casino market interaction listener
        // This adds the "Visit Private Lounge" option to compatible markets
        Global.getSector().getListenerManager().addListener(new CasinoMarketInteractionListener(), true);
    }
}