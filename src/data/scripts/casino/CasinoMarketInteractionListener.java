package data.scripts.casino;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyInteractionListener;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import data.scripts.casino.interaction.CasinoInteraction;

public class CasinoMarketInteractionListener implements ColonyInteractionListener {

    private static final String CASINO_OPTION_ID = "visit_casino";
    private static final String VISITED_CASINO_KEY = "$ipc_visited_casino";
    
    /**
     * MOD DEVELOPMENT NOTES FOR BEGINNERS:
     * - ColonyInteractionListener monitors when players interact with markets
     * - reportPlayerOpenedMarket() is called when the player opens a market screen
     * - This is the right place to add custom options to the market interface
     */
    @Override
    public void reportPlayerOpenedMarket(MarketAPI market) {
        // Check if this market is suitable for the casino interaction
        if (shouldAddCasinoOption(market)) {
            // Add the casino interaction option to the market
            addCasinoOptionToMarket(market);
        }
    }

    /**
     * Called when the player closes the market interface
     * 
     * MOD DEVELOPMENT NOTES FOR BEGINNERS:
     * - Use this method for cleanup if needed
     * - Typically not required unless you're storing temporary data
     */
    @Override
    public void reportPlayerClosedMarket(MarketAPI market) {
        // Cleanup if needed when player closes the market
    }
    /**
     * Called after the cargo/market data has been updated
     * 
     * MOD DEVELOPMENT NOTES FOR BEGINNERS:
     * - This ensures the market data is fully loaded before adding options
     * - Sometimes needed because the market data might not be ready in reportPlayerOpenedMarket()
     */
    @Override
    public void reportPlayerOpenedMarketAndCargoUpdated(MarketAPI market) {
        // This is called after cargo is updated, we can add the option here too
        if (shouldAddCasinoOption(market)) {
            addCasinoOptionToMarket(market);
        }
    }
    /**
     * Called when the player completes a transaction at the market
     * 
     * MOD DEVELOPMENT NOTES FOR BEGINNERS:
     * - Use this to track player purchases or sales
     * - Good place to trigger conditional logic based on what the player bought/sold
     */
    @Override
    public void reportPlayerMarketTransaction(PlayerMarketTransaction transaction) {
        // Handle transaction events if needed
    }
    /**
     * Determines if the casino option should be available at this market
     * 
     * MOD DEVELOPMENT NOTES FOR BEGINNERS:
     * - MarketAPI provides access to market properties (faction, size, industries, etc.)
     * - This method defines the conditions under which your mod's features appear
     * - Be strategic about placement to make sense in the game world
     */
    private boolean shouldAddCasinoOption(MarketAPI market) {
        // Check if market meets criteria for casino
        // Add casino option to Tritachyon faction markets (since they are tech-oriented)
        if ("tritachyon".equals(market.getFactionId())) {
            return true;
        }
        
        // Or to player markets of size 3+
        if ("player".equals(market.getFactionId()) && market.getSize() >= 3) {
            return true;
        }
        
        // Or to markets with entertainment-related industries
        for (com.fs.starfarer.api.campaign.econ.Industry industry : market.getIndustries()) {
            String industryId = industry.getId();
            if (industryId != null && 
                (industryId.toLowerCase().contains("plex") || 
                 industryId.toLowerCase().contains("entertainment") ||
                 industryId.toLowerCase().contains("luxury") ||
                 industryId.toLowerCase().contains("station"))) {
                return true;
            }
        }
        
        // Or to markets of size 3+ in general
        return market.getSize() >= 3; // Make it available in most markets (size 3+)
    }

    /**
     * Adds the casino option to the market interaction dialog
     * 
     * MOD DEVELOPMENT NOTES FOR BEGINNERS:
     * - Global.getSector().getCampaignUI().getCurrentInteractionDialog() gets the active UI
     * - OptionPanelAPI.addOption() adds clickable options to the dialog
     * - MemoryAPI helps track state to avoid duplicate options
     * - Use unique keys when storing data in memory to avoid conflicts
     */
    private void addCasinoOptionToMarket(MarketAPI market) {
        // Get the interaction dialog for this market
        InteractionDialogAPI dialog = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
        
        if (dialog != null) {
            OptionPanelAPI options = dialog.getOptionPanel();
            
            // Add the casino option
            // The first parameter is the display text, the second is the internal ID
            options.addOption("Visit Casino", CASINO_OPTION_ID);
        }
    }
}
