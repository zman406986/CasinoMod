package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import data.scripts.casino.CasinoConfig;
import data.scripts.casino.CasinoDebtScript;
import data.scripts.casino.CasinoVIPManager;
import data.scripts.casino.CasinoMarketInteractionListener;

/**
 * Mod plugin for the Interastral Peace Casino.
 * Implements poker, arena betting, and gacha systems with VIP/credit mechanics.
 */
public class CasinoModPlugin extends BaseModPlugin {

    @Override
    public void onApplicationLoad() {
        Global.getLogger(this.getClass()).info("Interastral Peace Casino Loaded");
        CasinoConfig.loadSettings();
    }

    @Override
    public void onGameLoad(boolean newGame) {
        Global.getLogger(this.getClass()).info("Interastral Peace Casino: Game Loaded");
        CasinoVIPManager.initializeSystem();
        CasinoDebtScript.initializeSystem();
        Global.getSector().addTransientScript(new CasinoVIPManager());
        Global.getSector().addTransientScript(new CasinoDebtScript());
        Global.getSector().getListenerManager().addListener(new CasinoMarketInteractionListener(), true);
    }
}
