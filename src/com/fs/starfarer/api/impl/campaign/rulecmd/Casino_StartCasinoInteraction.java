package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.casino.interaction.CasinoInteraction;

import java.util.List;
import java.util.Map;

/**
 * Rule Command to start the Casino Interaction
 * 
 * This can be called from rules.csv or other places to initiate the casino interaction
 */
public class Casino_StartCasinoInteraction extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;
        
        try {
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