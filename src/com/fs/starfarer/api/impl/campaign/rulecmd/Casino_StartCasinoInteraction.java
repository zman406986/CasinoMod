package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.casino.interaction.CasinoInteraction;
import data.scripts.casino.CasinoConfig;
import data.scripts.casino.Strings;

import java.awt.Color;
import java.util.List;
import java.util.Map;

/**
 * This class is referenced from data/campaign/rules.csv using the command:Casino_StartCasinoInteraction
 * The rule command is triggered by the "Visit Private Lounge" option that appears
 * in market interactions when the casino conditions are met.
 * AI_AGENT NOTES:
 * - This is the ENTRY POINT for all casino interactions
 * - Never call CasinoInteraction directly - always go through this rule command
 * - Market size checks prevent casinos from appearing in tiny outposts
 * - All exceptions are caught and logged to prevent dialog crashes
 */
public class Casino_StartCasinoInteraction extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;
        
        try {
            MarketAPI currentMarket = dialog.getInteractionTarget().getMarket();
            if (currentMarket != null) {
                int marketSize = currentMarket.getSize();
                boolean isPlayerMarket = currentMarket.getFaction().isPlayerFaction();
                
                int minSize = isPlayerMarket ? CasinoConfig.MARKET_SIZE_MIN_FOR_PLAYER_CASINO : CasinoConfig.MARKET_SIZE_MIN_FOR_GENERAL_CASINO;
                
                if (marketSize < minSize) {
                    dialog.getTextPanel().addPara(Strings.get("errors.market_too_small"), Color.RED);
                    dialog.getOptionPanel().clearOptions();
                    dialog.getOptionPanel().addOption(Strings.get("common.continue"), "casino_error_dismiss");
                    return true;
                }
            }

            Global.getLogger(this.getClass()).info("Attempting to start casino interaction via Casino_StartCasinoInteraction rule command");

            CasinoInteraction.startCasinoInteraction(dialog);
            return true;
        } catch (Exception e) {
            Global.getLogger(this.getClass()).error("Error starting casino interaction", e);
            dialog.getTextPanel().addPara(Strings.get("errors.casino_start_failed"), Color.RED);
            dialog.getOptionPanel().clearOptions();
            dialog.getOptionPanel().addOption(Strings.get("common.continue"), "casino_error_dismiss");
            return true;
        }
    }
}
