package data.scripts.casino;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;

import java.awt.Color;
import java.util.Map;
import java.util.Random;

public class CasinoVIPManager {
    private static final String DATA_KEY = "CasinoVIPData";
    
    private static final String LAST_REWARD_TIME_KEY = "$ipc_vip_last_reward_time";
    
    // Performance: We track time to avoid running heavy logic every single frame.
    private float daysElapsed = 0;
    private float timer = 0f;
    private final Random random = new Random();

    public boolean isDone() { return false; } // This script never finishes naturally
    
    public boolean runWhilePaused() { return false; } // Don't process time while paused

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

            daysElapsed = days;
        }
    }

    /**
     * Checks if a new day has arrived to give VIP rewards.
     */
    private void checkDaily() {
        int daysRemaining = getDaysRemaining();
        if (daysRemaining <= 0) return;

        CampaignClockAPI clock = Global.getSector().getClock();
        long currentTime = clock.getTimestamp();
        long lastRewardTime = Global.getSector().getPlayerMemoryWithoutUpdate().getLong(LAST_REWARD_TIME_KEY);

        // Only give reward if we haven't given one today (or within last 24 hours)
        // Using 24 hours (86400 seconds) to ensure daily rewards
        if (lastRewardTime == 0 || (currentTime - lastRewardTime) >= 86400L) {
            addStargems(CasinoConfig.VIP_DAILY_REWARD);
            sendNotification();
            
            // Mark current time as the time we gave the reward
            Global.getSector().getPlayerMemoryWithoutUpdate().set(LAST_REWARD_TIME_KEY, currentTime);
        }
    }

    /**
     * Applies 5% interest to debt on the 15th of every month.
     */
    private void checkInterest() {
        int day = Global.getSector().getClock().getDay();
        int month = Global.getSector().getClock().getMonth();
        int year = Global.getSector().getClock().getCycle();
        
        Map<String, Object> data = Global.getSector().getPersistentData();
        // We create a unique key for this year/month so interest only applies once per month.
        String interestKey = "CasinoInterest_" + year + "_" + month;
        
        if (day == 15 && !data.containsKey(interestKey)) {
            int currentDebt = getDebt();
            if (currentDebt > 0) {
                int interest = (int) (currentDebt * CasinoConfig.VIP_INTEREST_RATE); 
                
                // Add interest to debt (not to stargems)
                addDebt(interest);
                
                Global.getSector().getCampaignUI().addMessage("CORPORATE NOTICE: 5% monthly interest applied to your delinquent Stargem account.", Color.YELLOW);
                Global.getSector().getCampaignUI().addMessage("Interest added: " + interest + " Stargems to debt", Color.RED);
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
    
    // Get player's stargem balance (credit wallet)
    public static int getStargems() {
        return Global.getSector().getPlayerMemoryWithoutUpdate().getInt("$ipc_stargems");
    }

    // Get player's debt amount (debt wallet)
    public static int getDebt() {
        return Global.getSector().getPlayerMemoryWithoutUpdate().getInt("$ipc_debt_amount");
    }

    // Get cumulative VIP passes bought
    public static int getCumulativeVIPPurchases() {
        return Global.getSector().getPlayerMemoryWithoutUpdate().getInt("$ipc_cumulative_vip_purchases");
    }

    // Get cumulative top-up amount (including ship sales)
    public static int getCumulativeTopupAmount() {
        return Global.getSector().getPlayerMemoryWithoutUpdate().getInt("$ipc_cumulative_topup_amount");
    }

    // Add to player's stargem balance (credit wallet)
    public static void addStargems(int amount) {
        int current = getStargems();
        int newAmount = Math.max(0, current + amount); // Ensure non-negative balance
        Global.getSector().getPlayerMemoryWithoutUpdate().set("$ipc_stargems", newAmount);
        
        // If adding gems and amount is positive, update cumulative topup amount
        if (amount > 0) {
            addCumulativeTopup(amount);
        }
        
        // Check if player has paid off debt and reset debt collector flag if needed
        checkDebtPayment();
    }

    // Add to player's debt amount (debt wallet)
    public static void addDebt(int amount) {
        int current = getDebt();
        int newAmount = Math.max(0, current + amount); // Ensure non-negative debt value
        Global.getSector().getPlayerMemoryWithoutUpdate().set("$ipc_debt_amount", newAmount);
    }

    // Add to cumulative VIP purchases
    public static void addCumulativeVIPPurchases(int passes) {
        int current = getCumulativeVIPPurchases();
        int newAmount = current + passes;
        Global.getSector().getPlayerMemoryWithoutUpdate().set("$ipc_cumulative_vip_purchases", newAmount);
    }

    // Add to cumulative top-up amount
    public static void addCumulativeTopup(int amount) {
        int current = getCumulativeTopupAmount();
        int newAmount = current + amount;
        Global.getSector().getPlayerMemoryWithoutUpdate().set("$ipc_cumulative_topup_amount", newAmount);
    }

    // Get days remaining for VIP status
    public static int getDaysRemaining() {
        CampaignClockAPI clock = Global.getSector().getClock();
        long startTime = Global.getSector().getPlayerMemoryWithoutUpdate().getLong("$ipc_vip_start_time");
        int duration = Global.getSector().getPlayerMemoryWithoutUpdate().getInt("$ipc_vip_duration");

        if (duration <= 0) return 0;

        float elapsedDays;
        
        // Backward compatibility: if startTime is a small number (old-style day value), convert it
        if (startTime < 1000000000L) {
            // Old format: startTime was stored as clock.getDay() (day of month, 1-31)
            // We can't accurately convert this, so we'll assume it was recent
            // and set remaining days based on duration minus a reasonable elapsed time
            elapsedDays = 0;
        } else {
            // New format: startTime is a timestamp
            elapsedDays = clock.getElapsedDaysSince(startTime);
        }

        int remaining = duration - (int) elapsedDays;
        return Math.max(0, remaining);
    }



    // Add subscription days to VIP status
    public static void addSubscriptionDays(int days) {
        CampaignClockAPI clock = Global.getSector().getClock();
        int newDuration;
        if (getDaysRemaining() <= 0) {
            // No current VIP, start fresh
            Global.getSector().getPlayerMemoryWithoutUpdate().set("$ipc_vip_start_time", clock.getTimestamp());
            newDuration = days;
        } else {
            // Extend current VIP - reset start time to today and add remaining days
            newDuration = getDaysRemaining() + days;
            Global.getSector().getPlayerMemoryWithoutUpdate().set("$ipc_vip_start_time", clock.getTimestamp());
        }

        Global.getSector().getPlayerMemoryWithoutUpdate().set("$ipc_vip_duration", newDuration);
        
        // Reset the last reward time so the player gets a reward on the day they purchase
        Global.getSector().getPlayerMemoryWithoutUpdate().set(LAST_REWARD_TIME_KEY, -1L);
        
        // Add to cumulative VIP purchases
        addCumulativeVIPPurchases(1); // Count this as one VIP purchase
    }

    // Get the debt ceiling based on dynamic calculation
    public static int getDebtCeiling() {
        int baseCeiling = CasinoConfig.BASE_DEBT_CEILING;
        int vipIncrease = getCumulativeVIPPurchases() * CasinoConfig.VIP_PASS_CEILING_INCREASE;
        int topupIncrease = (int) (getCumulativeTopupAmount() * CasinoConfig.TOPUP_CEILING_MULTIPLIER);
        
        return baseCeiling + vipIncrease + topupIncrease;
    }

    // Get available credit (ceiling - current debt)
    public static int getAvailableCredit() {
        int ceiling = getDebtCeiling();
        int debt = getDebt();
        return ceiling - debt;
    }

    // Apply monthly interest to debt
    public static void applyInterest() {
        int currentDebt = getDebt();
        if (currentDebt > 0) {
            float interestAmount = currentDebt * CasinoConfig.VIP_INTEREST_RATE;
            addDebt((int) interestAmount);
        }
    }
    
    // Pay off debt using stargems
    public static boolean payDebt(int amount) {
        int currentStargems = getStargems();
        int currentDebt = getDebt();
        
        if (amount > currentStargems) {
            return false; // Not enough stargems to pay
        }
        
        if (amount > currentDebt) {
            amount = currentDebt; // Can't pay more than debt
        }
        
        addStargems(-amount);
        subtractDebt(amount); // Subtract from debt
        
        // Check if player has paid off debt and reset debt collector flag if needed
        checkDebtPayment();
        
        return true;
    }
    
    // Helper method to subtract from debt (when paying)
    public static void subtractDebt(int amount) {
        int current = getDebt();
        int newAmount = Math.max(0, current - amount); // Ensure non-negative debt value
        Global.getSector().getPlayerMemoryWithoutUpdate().set("$ipc_debt_amount", newAmount);
    }
    
    // Initialize the debt system if not already initialized
    public static void initializeDebtSystem() {
        // Initialize debt amount if not set
        if (!Global.getSector().getPlayerMemoryWithoutUpdate().contains("$ipc_debt_amount")) {
            Global.getSector().getPlayerMemoryWithoutUpdate().set("$ipc_debt_amount", 0);
        }
        
        // Initialize cumulative VIP purchases if not set
        if (!Global.getSector().getPlayerMemoryWithoutUpdate().contains("$ipc_cumulative_vip_purchases")) {
            Global.getSector().getPlayerMemoryWithoutUpdate().set("$ipc_cumulative_vip_purchases", 0);
        }
        
        // Initialize cumulative topup amount if not set
        if (!Global.getSector().getPlayerMemoryWithoutUpdate().contains("$ipc_cumulative_topup_amount")) {
            Global.getSector().getPlayerMemoryWithoutUpdate().set("$ipc_cumulative_topup_amount", 0);
        }
    }
    
    // Check if player has paid off their debt and reset the debt collector flag if needed
    private static void checkDebtPayment() {
        int availableCredit = getAvailableCredit();
        
        // If player has paid off their debt (available credit is positive again), reset the debt collector flag
        if (availableCredit >= 0) {
            // Reset the debt collectors active flag so they can be spawned again if player goes into debt
            Global.getSector().getMemoryWithoutUpdate().unset("$ipc_debt_collectors_active");
        }
    }
}