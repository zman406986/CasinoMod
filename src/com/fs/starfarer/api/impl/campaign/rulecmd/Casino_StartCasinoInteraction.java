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

/**
 * Rule command plugin that initiates the casino interaction.
 * 
 * INTEGRATION:
 * This class is referenced from data/campaign/rules.csv using the command:
 * Casino_StartCasinoInteraction
 * 
 * The rule command is triggered by the "Visit Private Lounge" option that appears
 * in market interactions when the casino conditions are met.
 * 
 * MARKET REQUIREMENTS:
 * - Player-owned markets: Minimum size 4 (MARKET_SIZE_MIN_FOR_PLAYER_CASINO)
 * - NPC markets: Minimum size 3 (MARKET_SIZE_MIN_FOR_GENERAL_CASINO)
 * 
 * If the market is too small, the option will not appear or will show a
 * rejection message.
 * 
 * AI_AGENT NOTES:
 * - This is the ENTRY POINT for all casino interactions
 * - Never call CasinoInteraction directly - always go through this rule command
 * - Market size checks prevent casinos from appearing in tiny outposts
 * - All exceptions are caught and logged to prevent dialog crashes
 * 
 * ERROR HANDLING:
 * - Returns false if dialog is null
 * - Returns false if market size requirements not met
 * - Returns false if casino interaction fails to start
 * - Logs all errors to help with debugging
 * 
 * @see CasinoInteraction
 * @see CasinoConfig
 */
public class Casino_StartCasinoInteraction extends BaseCommandPlugin {

    /**
     * Executes the rule command to start casino interaction.
     * 
     * EXECUTION FLOW:
     * 1. Validate dialog is not null
     * 2. Check market size requirements
     * 3. Start casino interaction via CasinoInteraction.startCasinoInteraction()
     * 4. Return true on success, false on any failure
     * 
     * AI_AGENT NOTE: This method is called by the game's rule engine.
     * Do not call it directly from other code.
     * 
     * @param ruleId The ID of the rule being executed
     * @param dialog The interaction dialog API
     * @param params Parameters passed from the rule (unused)
     * @param memoryMap Memory map for the interaction (unused)
     * @return true if casino interaction started successfully, false otherwise
     */
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
