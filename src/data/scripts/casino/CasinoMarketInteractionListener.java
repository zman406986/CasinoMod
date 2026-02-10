package data.scripts.casino;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyInteractionListener;

public class CasinoMarketInteractionListener implements ColonyInteractionListener {

    private static final String CASINO_OPTION_ID = "visit_casino";
    
    @Override
    public void reportPlayerOpenedMarket(MarketAPI market) {
        // Check if this market is suitable for the casino interaction
        if (shouldAddCasinoOption(market)) {
            // Add the casino interaction option to the market
            addCasinoOptionToMarket(market);
        }
    }

    @Override
    public void reportPlayerClosedMarket(MarketAPI market) {
        // Cleanup if needed when player closes the market
    }
    
    @Override
    public void reportPlayerOpenedMarketAndCargoUpdated(MarketAPI market) {
        // This is called after cargo is updated, we can add the option here too
        if (shouldAddCasinoOption(market)) {
            addCasinoOptionToMarket(market);
        }
    }
    
    @Override
    public void reportPlayerMarketTransaction(PlayerMarketTransaction transaction) {
        // Handle transaction events if needed
    }
    
    private boolean shouldAddCasinoOption(MarketAPI market) {
        // Casino is only available on player-owned markets and Tri-Tachyon markets
        if (market == null || market.getFaction() == null) {
            return false;
        }
        
        String factionId = market.getFactionId();
        boolean isPlayerMarket = market.getFaction().isPlayerFaction();
        boolean isTriTachyon = "tritachyon".equals(factionId);
        
        return isPlayerMarket || isTriTachyon;
    }

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