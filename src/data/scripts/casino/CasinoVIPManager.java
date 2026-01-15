package data.scripts.casino;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.missions.FleetCreatorMission;
import java.awt.Color;
import java.util.Map;
import java.util.Random;

/**
 * CasinoVIPManager
 * 
 * ROLE: This is an "EveryFrameScript" which acts as a background service for the mod.
 * It handles persistent game logic like VIP daily rewards, debt interest, and spawning hunters.
 * 
 * LEARNERS: Notice how it uses Global.getSector().getPersistentData() to save state across saves.
 * 
 * MOD DEVELOPMENT NOTES FOR BEGINNERS:
 * - EveryFrameScript runs continuously while the game is active
 * - Use it for ongoing effects like timers, periodic rewards, or status checks
 * - Implement isDone() to return false to keep it running indefinitely
 * - Use runWhilePaused() to control if it runs when the game is paused
 * - advance() is called every frame, so optimize code to avoid performance issues
 * - PersistentData is the way to store data that survives game saves/reloads
 */
public class CasinoVIPManager implements EveryFrameScript {
    private static final String DATA_KEY = "CasinoVIPData";
    private static final String STARGEM_KEY = "CasinoStargems"; 
    
    // Performance: We track time to avoid running heavy logic every single frame.
    private float daysElapsed = 0;
    private float timer = 0f;
    private Random random = new Random();

    /**
     * Data structure to hold VIP specific info. 
     * Being static makes it easier to reference, but Starsector saves it as a Map entry.
     * 
     * MOD DEVELOPMENT NOTES FOR BEGINNERS:
     * - This class holds persistent data for the VIP system
     * - daysRemaining: How many VIP days are left
     * - lastDailyTime: Timestamp of last daily reward claim
     * - totalTopUps: Total gems purchased by the player
     */
    public static class VIPData {
        public int daysRemaining = 0;
        public long lastDailyTime = 0;
        public int totalTopUps = 0; 
    }

    // EveryFrameScript requirements
    /**
     * Determines if the script should stop running
     * 
     * MOD DEVELOPMENT NOTES FOR BEGINNERS:
     * - Return false to keep the script running indefinitely
     * - Return true to stop the script after the current frame
     */
    @Override 
    public boolean isDone() { return false; } // This script never finishes naturally
    
    /**
     * Determines if the script should run while the game is paused
     * 
     * MOD DEVELOPMENT NOTES FOR BEGINNERS:
     * - Return false to pause script execution when game is paused
     * - Return true to continue running even when game is paused
     */
    @Override 
    public boolean runWhilePaused() { return false; } // Don't process time while paused

    /**
     * advance is called by the game engine every frame when the game is unpaused.
     * @param amount The time in seconds that passed since the last frame.
     * 
     * MOD DEVELOPMENT NOTES FOR BEGINNERS:
     * - This method is called very frequently (60+ times per second)
     * - Optimize code to avoid performance issues
     * - Use throttling mechanisms to run logic less frequently
     * - Global.getSector().getClock() provides access to game time
     */
    @Override
    public void advance(float amount) {
        // Optimization: Throttle the check so it only runs once per real-world second.
        timer += amount;
        if (timer < 1.0f) return;
        timer -= 1.0f;

        // In-game day check
        float days = Global.getSector().getClock().getDay();
        if (days != daysElapsed) {
            checkDaily();
            checkInterest();
            checkDebtHunters();
            daysElapsed = days;
        }
    }

    /**
     * Checks if a new day has arrived to give VIP rewards.
     */
    private void checkDaily() {
        Map<String, Object> data = Global.getSector().getPersistentData();
        VIPData vip = (VIPData) data.get(DATA_KEY);
        if (vip == null) return;

        if (vip.daysRemaining > 0) {
            vip.daysRemaining--;
            addStargems(CasinoConfig.VIP_DAILY_REWARD);
            sendNotification();
            data.put(DATA_KEY, vip); // Always re-put to ensure the map updates
        }
    }

    /**
     * Applies 5% interest to debt on the 15th of every month.
     */
    private void checkInterest() {
        int day = Global.getSector().getClock().getDay();
        int month = Global.getSector().getClock().getMonth();
        long timestamp = Global.getSector().getClock().getTimestamp();
        
        Map<String, Object> data = Global.getSector().getPersistentData();
        // We create a unique key for this month/timestamp so interest only applies once.
        String interestKey = "CasinoInterest_" + month + "_" + timestamp;
        
        if (day == 15 && !data.containsKey(interestKey)) {
            int gems = getStargems();
            if (gems < 0) {
                int interest = (int) (gems * CasinoConfig.VIP_INTEREST_RATE); 
                
                // Memory markers are great for passing localized flags between methods.
                Global.getSector().getMemoryWithoutUpdate().set("$ipc_processing_interest", true, 0f);
                addStargems(interest); 
                Global.getSector().getMemoryWithoutUpdate().unset("$ipc_processing_interest");
                
                Global.getSector().getCampaignUI().addMessage("CORPORATE NOTICE: 5% monthly interest applied to your delinquent Stargem account.", Color.YELLOW);
                Global.getSector().getCampaignUI().addMessage("Interest added: " + interest + " Stargems", Color.RED);
            }
            data.put(interestKey, true);
        }
    }

    /**
     * Sends a random flavor text advertisement to the player UI.
     */
    private void sendNotification() {
        if (CasinoConfig.VIP_ADS.isEmpty()) return;
        String ad = CasinoConfig.VIP_ADS.get(random.nextInt(CasinoConfig.VIP_ADS.size()));
        Global.getSector().getCampaignUI().addMessage(
            "VIP Pass: " + CasinoConfig.VIP_DAILY_REWARD + " Stargems added. " + ad,
            Color.GREEN,
            CasinoConfig.VIP_DAILY_REWARD + " Stargems",
            "",
            Color.YELLOW,
            Color.WHITE
        );
    }
    
    /**
     * Standard method to modify player balance.
     * amount can be negative for losses/costs.
     * 
     * MOD DEVELOPMENT NOTES FOR BEGINNERS:
     * - Uses PersistentData to store values that persist across saves
     * - Updates the player's gem balance by adding the specified amount
     * - Positive amounts increase balance, negative amounts decrease it
     * - Also tracks total top-ups for credit limit calculation
     * - Includes reputation adjustment for high-value transactions
     */
    public static void addStargems(int amount) {
        Map<String, Object> data = Global.getSector().getPersistentData();
        int current = getStargems();
        data.put(STARGEM_KEY, current + amount);
        
        // Dynamic Ceiling History: We only track positive gains that aren't interest.
        if (amount > 0 && !Global.getSector().getMemoryWithoutUpdate().contains("$ipc_processing_interest")) {
             VIPData vip = (VIPData) data.get(DATA_KEY);
             if (vip == null) vip = new VIPData();
             vip.totalTopUps += amount;
             data.put(DATA_KEY, vip);
        }

        // Reputation Logic: Corporate entities love big spenders.
        if (amount >= 10000) {
            adjustTriTachRep(0.01f * (amount / 10000), "Significant Stargem purchase");
        }
    }

    /**
     * Calculates the player's current credit limit.
     * Formula: 10,000 + 10% of History + Reputation Bonus
     * 
     * MOD DEVELOPMENT NOTES FOR BEGINNERS:
     * - Credit limit determines how deep a player can go into debt
     * - Based on their spending history and relationship with Tritachyon
     * - Higher reputation with Tritachyon increases the limit
     * - Uses totalTopUps (from addStargems) to calculate history bonus
     */
    public static int getDebtCeiling() {
        Map<String, Object> data = Global.getSector().getPersistentData();
        VIPData vip = (VIPData) data.get(DATA_KEY);
        int historyBonus = 0;
        if (vip != null) {
            historyBonus = (int) (vip.totalTopUps * 0.1f);
        }
        
        // Reputation with Tri-Tachyon scales from -1.0 to +1.0
        float rep = Global.getSector().getFaction("tritachyon").getRelationship("player");
        int repBonus = (int) (rep * 50000); // Up to 50k bonus
        
        return 10000 + historyBonus + repBonus;
    }
    
    /**
     * Data Retrieval: Fetches the Stargem balance from PersistentData.
     * 
     * MOD DEVELOPMENT NOTES FOR BEGINNERS:
     * - PersistentData can store various data types (Integer, Float, Double)
     * - Need to check the type and convert appropriately
     * - Returns 0 if no stargem data exists yet
     */
    public static int getStargems() {
        Map<String, Object> data = Global.getSector().getPersistentData();
        if (data.containsKey(STARGEM_KEY)) {
            // PersistentData usually stores integers as Doubles or Longs depending on source,
            // but here we cast directly to (int).
            Object val = data.get(STARGEM_KEY);
            if (val instanceof Integer) return (int) val;
            if (val instanceof Float) return ((Float) val).intValue();
            if (val instanceof Double) return ((Double) val).intValue();
        }
        return 0;
    }
    
    /**
     * Sets the player's stargem balance to a specific amount
     * 
     * MOD DEVELOPMENT NOTES FOR BEGINNERS:
     * - Directly sets the stargem value without incrementing
     * - Use with caution as it replaces the entire balance
     */
    public static void setStargems(int amount) {
        Global.getSector().getPersistentData().put(STARGEM_KEY, amount);
    }

    /**
     * Adds VIP subscription days to the player's account
     * 
     * MOD DEVELOPMENT NOTES FOR BEGINNERS:
     * - Increments the remaining VIP days by the specified amount
     * - Creates VIPData if it doesn't exist yet
     * - Used when purchasing VIP passes
     */
    public static void addSubscriptionDays(int days) {
        Map<String, Object> data = Global.getSector().getPersistentData();
        VIPData vip = (VIPData) data.get(DATA_KEY);
        if (vip == null) vip = new VIPData();
        vip.daysRemaining += days;
        data.put(DATA_KEY, vip);
    }
    
    /**
     * Gets the number of VIP days remaining
     * 
     * MOD DEVELOPMENT NOTES FOR BEGINNERS:
     * - Returns 0 if no VIP data exists
     * - Used to check if VIP benefits should be active
     */
    public static int getDaysRemaining() {
        VIPData vip = (VIPData) Global.getSector().getPersistentData().get(DATA_KEY);
        return vip == null ? 0 : vip.daysRemaining;
    }

    /**
     * Interaction with Faction API.
     */
    public static void adjustTriTachRep(float amount, String reason) {
        Global.getSector().getFaction("tritachyon").adjustRelationship("player", amount);
        Global.getSector().getCampaignUI().addMessage("Tri-Tachyon reputation " + (amount > 0 ? "increased" : "decreased") + ": " + reason, 
                amount > 0 ? Color.CYAN : Color.RED);
    }

    private void checkDebtHunters() {
        // Only spawn debt hunters if the player's debt exceeds the threshold
        // (i.e., if the stargem balance is less than the debt threshold)
        if (getStargems() > CasinoConfig.VIP_DEBT_HUNTER_THRESHOLD) return;
        
        // Use a daily chance to avoid spawning fleets every frame during the "Debt hunting window".
        if (random.nextFloat() < 0.2f) {
            spawnDebtHunter();
        }
    }

    private void spawnDebtHunter() {
        // Show the warning message to the player
        Global.getSector().getCampaignUI().addMessage("SIGNAL DETECTED: A Tri-Tachyon 'Asset Recovery' fleet has been dispatched to your location.", Color.RED);
        Global.getLogger(this.getClass()).info("Debt Hunter Spawned due to high debt: " + getStargems());
        
        // Actually spawn the debt hunter fleet
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        if (playerFleet != null) {
            // Create a simple patrol fleet using FleetCreatorMission
            FleetCreatorMission m = new FleetCreatorMission(new Random());
            m.beginFleet();
            
            // Create a medium-sized fleet with moderate quality
            m.createStandardFleet(5, "tritachyon", playerFleet.getLocationInHyperspace());
            
            // Set the fleet type to bounty hunter to make it aggressive
            m.triggerSetFleetType(FleetTypes.MERC_BOUNTY_HUNTER);
            
            // Set aggressive behavior toward player
            m.triggerSetStandardAggroPirateFlags();
            
            // Create the fleet
            CampaignFleetAPI debtHunterFleet = m.createFleet();
            
            if (debtHunterFleet != null) {
                // Set fleet name
                debtHunterFleet.setName("Tri-Tachyon Asset Recovery Fleet");
                
                // Add the fleet to the hyperspace location
                Global.getSector().getHyperspace().addEntity(debtHunterFleet);
                
                // Set intercept assignment to pursue the player
                debtHunterFleet.addAssignment(FleetAssignment.INTERCEPT, playerFleet, 10f, "collecting debts");
                
                // Log the successful creation
                Global.getLogger(this.getClass()).info("Debt Hunter fleet successfully created and deployed");
            }
        }
    }
}