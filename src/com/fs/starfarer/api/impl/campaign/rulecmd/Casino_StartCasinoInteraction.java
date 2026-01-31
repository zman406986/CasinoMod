package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.casino.interaction.CasinoInteraction;
import data.scripts.casino.CasinoConfig;

import java.awt.Color;
import java.util.List;
import java.util.Map;

public class Casino_StartCasinoInteraction extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;
        
        try {
            // Check market size requirements
            MarketAPI currentMarket = dialog.getInteractionTarget().getMarket();
            if (currentMarket != null) {
                int marketSize = currentMarket.getSize();
                boolean isPlayerMarket = currentMarket.getFaction().isPlayerFaction();
                
                int minSize = isPlayerMarket ? CasinoConfig.MARKET_SIZE_MIN_FOR_PLAYER_CASINO : CasinoConfig.MARKET_SIZE_MIN_FOR_GENERAL_CASINO;
                
                if (marketSize < minSize) {
                    // Market is too small for casino
                    dialog.getTextPanel().addPara("This market is too small to support a casino establishment.", Color.RED);
                    dialog.getOptionPanel().addOption("Leave", "leave");
                    return false;
                }
            }
            
            // Log that we're attempting to start the casino interaction
            Global.getLogger(this.getClass()).info("Attempting to start casino interaction via Casino_StartCasinoInteraction rule command");
            
            // Start the casino interaction
            CasinoInteraction.startCasinoInteraction(dialog);
            return true;
        } catch (Exception e) {
            Global.getLogger(this.getClass()).error("Error starting casino interaction", e);
            return false;
        }
    }
}