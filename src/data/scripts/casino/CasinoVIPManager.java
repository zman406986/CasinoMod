package data.scripts.casino;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Core manager for VIP status, Stargem balance, credit facilities, and daily rewards.
 * Implements EveryFrameScript to run continuously in the background.
 * 
 * RESPONSIBILITIES:
 * 1. Stargem balance management (get/add to balance)
 * 2. VIP subscription tracking (days remaining, start time)
 * 3. Credit ceiling calculation (based on VIP purchases and top-ups)
 * 4. Daily reward distribution (VIP daily gems)
 * 5. Interest application (on negative balances)
 * 6. Notification management (daily/monthly modes)
 * 
 * MEMORY KEYS (all prefixed with "$ipc_"):
 * - $ipc_stargems: Current Stargem balance (can be negative)
 * - $ipc_vip_start_time: Timestamp when VIP pass started (for calculating remaining days)
 * - $ipc_vip_duration: Total VIP duration in days
 * - $ipc_cumulative_vip_purchases: Total VIP passes purchased (affects credit ceiling)
 * - $ipc_cumulative_topup_amount: Total gems added (affects credit ceiling)
 * - $ipc_vip_last_reward_time: Timestamp of last daily reward
 * - $ipc_vip_last_processed_day: Timestamp of last daily processing
 * - $ipc_vip_monthly_notify_mode: Boolean for notification frequency
 * - $ipc_vip_last_monthly_notify: Timestamp of last monthly notification
 * 
 * CRITICAL IMPLEMENTATION NOTES:
 * 
 * AI_AGENT_NOTE: NEVER use clock.getDay() for daily reward tracking!
 * The getDay() method returns day-of-month (1-31), which causes bugs when crossing
 * month boundaries (e.g., Jan 31 to Feb 1 appears as day change from 31 to 1).
 * Always use clock.getTimestamp() which provides continuous time values.
 * 
 * AI_AGENT_NOTE: Credit ceiling formula
 * ceiling = BASE_DEBT_CEILING + (vipPurchases * VIP_PASS_CEILING_INCREASE) + (topupAmount * TOPUP_CEILING_MULTIPLIER)
 * Available credit = ceiling + balance (balance can be negative)
 * 
 * AI_AGENT_NOTE: Interest is applied daily to negative balances
 * VIP rate: 2% daily (CasinoConfig.VIP_DAILY_INTEREST_RATE)
 * Non-VIP rate: 5% daily (CasinoConfig.NORMAL_DAILY_INTEREST_RATE)
 * Interest is subtracted from balance (makes negative balance more negative)
 * 
 * AI_AGENT_NOTE: Overdraft is VIP-only feature
 * Always check isOverdraftAvailable() before allowing negative balance transactions
 * Non-VIP players should see VIP promotion instead of overdraft option
 */
public class CasinoVIPManager implements EveryFrameScript {

    /** Key for storing VIP data structure in player memory */
    private static final String DATA_KEY = "CasinoVIPData";

    /** Memory key for timestamp of last daily reward (used for 24-hour cooldown) */
    private static final String LAST_REWARD_TIME_KEY = "$ipc_vip_last_reward_time";
    /** Memory key for timestamp of last daily processing (used to detect new day) */
    private static final String LAST_PROCESSED_DAY_KEY = "$ipc_vip_last_processed_day";
    /** Memory key for timestamp of last monthly notification */
    private static final String LAST_MONTHLY_NOTIFY_KEY = "$ipc_vip_last_monthly_notify";
    /** Memory key for notification mode preference (boolean: true = monthly, false = daily) */
    private static final String MONTHLY_NOTIFY_MODE_KEY = "$ipc_vip_monthly_notify_mode";
    /** Memory key for storing recent VIP ad messages (to prevent duplicates within 4 messages) */
    private static final String VIP_AD_HISTORY_KEY = "$ipc_vip_ad_history";

    // Performance: We track time to avoid running heavy logic every single frame.
    /** Timer accumulator for throttling daily checks (runs once per second) */
    private float timer = 0f;
    /** Random generator for VIP ad selection */
    private final Random random = new Random();

    /**
     * Required by EveryFrameScript interface.
     * This script runs indefinitely until the game ends.
     */
    @Override
    public boolean isDone() { return false; }

    /**
     * Required by EveryFrameScript interface.
     * Pauses processing when game is paused to avoid time desync.
     */
    @Override
    public boolean runWhilePaused() { return false; }

    /**
     * Main update loop called every frame.
     * Throttled to run daily logic once per in-game day.
     * 
     * AI_AGENT_NOTE: The 1-second throttle is for performance.
     * The actual daily check uses clock.getElapsedDaysSince() for accuracy.
     */
    public void advance(float amount) {
        // Optimization: Throttle the check so it only runs once per real-world second.
        timer += amount;
        if (timer < 1.0f) return;
        timer -= 1.0f;

        // Check if a new in-game day has passed using timestamp-based tracking
        CampaignClockAPI clock = Global.getSector().getClock();
        long lastProcessedDay = Global.getSector().getPlayerMemoryWithoutUpdate().getLong(LAST_PROCESSED_DAY_KEY);
        
        // If never processed before, set to current time
        if (lastProcessedDay == 0) {
            Global.getSector().getPlayerMemoryWithoutUpdate().set(LAST_PROCESSED_DAY_KEY, clock.getTimestamp());
            return;
        }
        
        // Check if at least one full day has passed since last processing
        float daysElapsed = clock.getElapsedDaysSince(lastProcessedDay);
        if (daysElapsed >= 1.0f) {
            checkDaily();
            // Update last processed day to current timestamp
            Global.getSector().getPlayerMemoryWithoutUpdate().set(LAST_PROCESSED_DAY_KEY, clock.getTimestamp());
        }
    }

    /**
     * Checks if a new day has arrived to give VIP rewards and apply debt interest.
     * Debt interest applies to all players with negative balance, regardless of VIP status.
     * VIP players get daily gems + interest notification.
     * Non-VIP players with negative balance get warning + interest notification.
     * 
     * AI_AGENT_NOTE: Uses getElapsedDaysSince() to check if at least 1 day has passed.
     * This is the correct way to measure game days in Starsector.
     * Never use raw timestamp arithmetic - timestamps are internal time values, not seconds.
     */
    private void checkDaily() {
        int daysRemaining = getDaysRemaining();
        boolean hasVIP = daysRemaining > 0;
        int currentBalance = getBalance();
        boolean hasDebt = currentBalance < 0;

        CampaignClockAPI clock = Global.getSector().getClock();
        long lastRewardTime = Global.getSector().getPlayerMemoryWithoutUpdate().getLong(LAST_REWARD_TIME_KEY);

        // Only process if we haven't given one today (or within last 24 hours)
        // Using getElapsedDaysSince() to properly measure game days
        // Note: -1L indicates first-time VIP (immediate reward), 0 indicates uninitialized
        float daysSinceLastReward;
        if (lastRewardTime == 0 || lastRewardTime == -1L) {
            daysSinceLastReward = 0;
        } else {
            daysSinceLastReward = clock.getElapsedDaysSince(lastRewardTime);
        }
        if (lastRewardTime == 0 || daysSinceLastReward >= 1.0f || lastRewardTime == -1L) {
            
            // Apply daily interest if player has negative balance (regardless of VIP status)
            int interestAmount = 0;
            if (hasDebt) {
                // Check if debt has hit the ceiling
                int currentDebt = -currentBalance; // Convert negative balance to positive debt
                int maxDebt = getMaxDebt();
                
                if (currentDebt >= maxDebt) {
                    // Debt at ceiling - no more interest accrual
                    Global.getSector().getCampaignUI().addMessage(
                        "Debt has reached maximum limit. Interest accrual paused.",
                        Color.ORANGE
                    );
                } else {
                    // Calculate interest
                    float interestRate = hasVIP ? CasinoConfig.VIP_DAILY_INTEREST_RATE : CasinoConfig.NORMAL_DAILY_INTEREST_RATE;
                    interestAmount = (int) (currentDebt * interestRate);
                    
                    // Cap interest to not exceed max debt
                    if (currentDebt + interestAmount > maxDebt) {
                        interestAmount = maxDebt - currentDebt;
                    }
                    
                    addToBalance(-interestAmount); // Subtract interest from balance (makes it more negative)
                }
            }
            
            if (hasVIP) {
                // VIP player: give daily reward and show combined notification
                addToBalance(CasinoConfig.VIP_DAILY_REWARD);
                
                // Check if we should show notification (daily or monthly mode)
                if (shouldShowNotification()) {
                    sendVIPNotification(interestAmount, currentBalance);
                }
            } else if (hasDebt) {
                // Non-VIP with debt: show debt warning notification
                sendDebtWarningNotification(interestAmount, currentBalance);
            }
            
            // Mark current time as the time we processed the daily check
            Global.getSector().getPlayerMemoryWithoutUpdate().set(LAST_REWARD_TIME_KEY, clock.getTimestamp());
        }
    }

    /**
     * Checks if VIP notification should be shown based on user preference.
     * Returns true for daily mode, or once per month for monthly mode.
     * 
     * AI_AGENT NOTE: Monthly mode uses 30-day intervals, not calendar months.
     * This ensures consistent timing regardless of month length.
     */
    private boolean shouldShowNotification() {
        boolean monthlyMode = Global.getSector().getPlayerMemoryWithoutUpdate().getBoolean(MONTHLY_NOTIFY_MODE_KEY);
        if (!monthlyMode) {
            return true; // Daily mode - always show
        }
        
        // Monthly mode - check if a month has passed
        CampaignClockAPI clock = Global.getSector().getClock();
        long lastMonthlyNotify = Global.getSector().getPlayerMemoryWithoutUpdate().getLong(LAST_MONTHLY_NOTIFY_KEY);
        
        if (lastMonthlyNotify == 0) {
            // First time - show and record
            Global.getSector().getPlayerMemoryWithoutUpdate().set(LAST_MONTHLY_NOTIFY_KEY, clock.getTimestamp());
            return true;
        }
        
        float daysSinceLastNotify = clock.getElapsedDaysSince(lastMonthlyNotify);
        if (daysSinceLastNotify >= 30f) {
            // A month has passed - show and update timestamp
            Global.getSector().getPlayerMemoryWithoutUpdate().set(LAST_MONTHLY_NOTIFY_KEY, clock.getTimestamp());
            return true;
        }
        
        return false; // Don't show yet
    }

    /**
     * Sends VIP daily reward notification combined with debt interest info.
     * Format: +100 daily gem (green) -X interest (red) | Balance: Y | [ad]
     * 
     * AI_AGENT NOTE: Each message is sent separately to allow different colors.
     * This creates a visual hierarchy: reward (green), cost (red), balance (context), ad (cyan).
     */
    private void sendVIPNotification(int interestAmount, int oldBalance) {
        int newBalance = getBalance();
        
        // Build the message with color-coded parts
        StringBuilder message = new StringBuilder();
        
        // Daily reward (always shown for VIP)
        Global.getSector().getCampaignUI().addMessage(
            "+" + CasinoConfig.VIP_DAILY_REWARD + " daily gem",
            Color.GREEN
        );
        
        // Interest (only if debt exists)
        if (interestAmount > 0) {
            Global.getSector().getCampaignUI().addMessage(
                "-" + interestAmount + " interest",
                Color.RED
            );
        }
        
        // Current balance
        Color balanceColor = newBalance >= 0 ? Color.GREEN : Color.RED;
        Global.getSector().getCampaignUI().addMessage(
            "Balance: " + newBalance + " Stargems",
            balanceColor
        );
        
        // VIP ad (random, no duplicates within 4 messages)
        if (!CasinoConfig.VIP_ADS.isEmpty()) {
            String ad = selectNonDuplicateAd();
            Global.getSector().getCampaignUI().addMessage(
                "[VIP] " + ad,
                Color.CYAN
            );
        }
    }
    
    /**
     * Sends debt warning notification for non-VIP players with negative balance.
     * Format: -X interest (red) | Balance: Y (red) | Warning
     */
    private void sendDebtWarningNotification(int interestAmount, int oldBalance) {
        int newBalance = getBalance();
        
        // Interest
        if (interestAmount > 0) {
            Global.getSector().getCampaignUI().addMessage(
                "-" + interestAmount + " interest",
                Color.RED
            );
        }
        
        // Current balance (always negative in this case)
        Global.getSector().getCampaignUI().addMessage(
            "Balance: " + newBalance + " Stargems",
            Color.RED
        );
        
        // Warning about debt collector
        Global.getSector().getCampaignUI().addMessage(
            "WARNING: Debt collectors may be dispatched!",
            Color.ORANGE
        );
    }
    
    /**
     * Get player's Stargem balance.
     * Can be negative if player is using overdraft facility.
     *
     * @return Current balance in Stargems (negative = debt)
     */
    public static int getBalance() {
        return Global.getSector().getPlayerMemoryWithoutUpdate().getInt("$ipc_stargems");
    }

    /**
     * Alias for getBalance().
     * Get player's Stargem balance.
     *
     * @return Current balance in Stargems (negative = debt)
     */
    public static int getStargems() {
        return getBalance();
    }

    /**
     * Get cumulative VIP passes purchased.
     * Used for credit ceiling calculation.
     * 
     * @return Total number of VIP passes purchased
     */
    public static int getCumulativeVIPPurchases() {
        return Global.getSector().getPlayerMemoryWithoutUpdate().getInt("$ipc_cumulative_vip_purchases");
    }

    /**
     * Get cumulative top-up amount (including ship sales).
     * Used for credit ceiling calculation.
     * 
     * @return Total gems added to account
     */
    public static int getCumulativeTopupAmount() {
        return Global.getSector().getPlayerMemoryWithoutUpdate().getInt("$ipc_cumulative_topup_amount");
    }

    /**
     * Add to player's Stargem balance.
     * Positive amount = add gems, Negative amount = spend gems.
     * Can result in negative balance (overdraft) if within credit ceiling.
     * 
     * AI_AGENT_NOTE: This is the ONLY method that should modify $ipc_stargems.
     * Never modify the memory key directly - always use this method.
     * 
     * @param amount Amount to add (positive) or subtract (negative)
     */
    public static void addToBalance(int amount) {
        int current = getBalance();
        int newAmount = current + amount;
        Global.getSector().getPlayerMemoryWithoutUpdate().set("$ipc_stargems", newAmount);
        
        // If adding gems and amount is positive, update cumulative topup amount
        if (amount > 0) {
            addCumulativeTopup(amount);
        }
    }

    /**
     * Add to cumulative VIP purchases counter.
     * Called automatically when VIP pass is purchased.
     * 
     * @param passes Number of passes to add (usually 1)
     */
    public static void addCumulativeVIPPurchases(int passes) {
        int current = getCumulativeVIPPurchases();
        int newAmount = current + passes;
        Global.getSector().getPlayerMemoryWithoutUpdate().set("$ipc_cumulative_vip_purchases", newAmount);
    }

    /**
     * Add to cumulative top-up amount.
     * Called automatically when gems are added (including ship conversions).
     * 
     * @param amount Amount of gems added
     */
    public static void addCumulativeTopup(int amount) {
        int current = getCumulativeTopupAmount();
        int newAmount = current + amount;
        Global.getSector().getPlayerMemoryWithoutUpdate().set("$ipc_cumulative_topup_amount", newAmount);
    }

    /**
     * Get days remaining for VIP status.
     * Calculates based on start time and duration.
     * 
     * @return Number of days remaining (0 if expired)
     */
    public static int getDaysRemaining() {
        CampaignClockAPI clock = Global.getSector().getClock();
        long startTime = Global.getSector().getPlayerMemoryWithoutUpdate().getLong("$ipc_vip_start_time");
        int duration = Global.getSector().getPlayerMemoryWithoutUpdate().getInt("$ipc_vip_duration");

        if (duration <= 0) return 0;

        float elapsedDays;
        
        // Handle uninitialized start_time (fresh VIP activation)
        // In Starsector, getTimestamp() returns game days (not Unix epoch)
        // A value of 0 or negative means VIP was never activated
        if (startTime <= 0) {
            elapsedDays = 0;
        } else {
            elapsedDays = clock.getElapsedDaysSince(startTime);
        }
        
        int remaining = duration - (int) elapsedDays;
        return Math.max(0, remaining);
    }

    /**
     * Add subscription days to VIP status.
     * If no active VIP, starts new subscription from current time.
     * If active VIP, extends current subscription.
     * 
     * AI_AGENT NOTE: Triggers immediate daily reward on purchase by calling checkDaily() directly.
     * This ensures the player gets their first reward immediately rather than waiting for next day.
     * 
     * @param days Number of days to add
     */
    public static void addSubscriptionDays(int days) {
        CampaignClockAPI clock = Global.getSector().getClock();
        int newDuration;
        if (getDaysRemaining() <= 0) {
            // No current VIP, start fresh
            Global.getSector().getPlayerMemoryWithoutUpdate().set("$ipc_vip_start_time", clock.getTimestamp());
            newDuration = days;
        } else {
            // Extend current VIP - calculate new total duration and reset start time to now
            newDuration = getDaysRemaining() + days;
            Global.getSector().getPlayerMemoryWithoutUpdate().set("$ipc_vip_start_time", clock.getTimestamp());
        }

        Global.getSector().getPlayerMemoryWithoutUpdate().set("$ipc_vip_duration", newDuration);
        
        // Add to cumulative VIP purchases
        addCumulativeVIPPurchases(1); // Count this as one VIP purchase
        
        // Trigger immediate reward for the purchase day
        // This ensures player gets their first reward immediately
        Global.getSector().getPlayerMemoryWithoutUpdate().set(LAST_REWARD_TIME_KEY, -1L);
        processImmediateDailyReward();
    }
    
    /**
     * Processes daily reward immediately for VIP purchase.
     * Called by addSubscriptionDays() to give immediate first reward.
     */
    private static void processImmediateDailyReward() {
        int daysRemaining = getDaysRemaining();
        boolean hasVIP = daysRemaining > 0;
        int currentBalance = getBalance();
        boolean hasDebt = currentBalance < 0;

        if (hasVIP) {
            // Give daily reward
            addToBalance(CasinoConfig.VIP_DAILY_REWARD);
            
            // Apply interest if in debt
            int interestAmount = 0;
            if (hasDebt) {
                int currentDebt = -currentBalance;
                int maxDebt = getMaxDebt();
                
                if (currentDebt < maxDebt) {
                    float interestRate = CasinoConfig.VIP_DAILY_INTEREST_RATE;
                    interestAmount = (int) (currentDebt * interestRate);
                    
                    if (currentDebt + interestAmount > maxDebt) {
                        interestAmount = maxDebt - currentDebt;
                    }
                    
                    addToBalance(-interestAmount);
                }
            }
            
            // Send notification
            CampaignClockAPI clock = Global.getSector().getClock();
            sendVIPNotificationImmediate(interestAmount, currentBalance);
            
            // Mark reward as processed
            Global.getSector().getPlayerMemoryWithoutUpdate().set(LAST_REWARD_TIME_KEY, clock.getTimestamp());
        }
    }
    
    /**
     * Sends VIP notification for immediate reward (simplified version).
     */
    private static void sendVIPNotificationImmediate(int interestAmount, int oldBalance) {
        int newBalance = getBalance();
        
        // Daily reward
        Global.getSector().getCampaignUI().addMessage(
            "+" + CasinoConfig.VIP_DAILY_REWARD + " daily gem (VIP Purchase Bonus)",
            Color.GREEN
        );
        
        // Interest (only if debt exists)
        if (interestAmount > 0) {
            Global.getSector().getCampaignUI().addMessage(
                "-" + interestAmount + " interest",
                Color.RED
            );
        }
        
        // Current balance
        Color balanceColor = newBalance >= 0 ? Color.GREEN : Color.RED;
        Global.getSector().getCampaignUI().addMessage(
            "Balance: " + newBalance + " Stargems",
            balanceColor
        );
    }

    /**
     * Calculate credit ceiling based on player level, VIP purchases, and topup amount.
     * 
     * Formula: BASE_DEBT_CEILING + (vipPurchases * CEILING_INCREASE_PER_VIP) + (playerLevel * OVERDRAFT_CEILING_LEVEL_MULTIPLIER)
     * Example: Level 10 player with 2 VIP purchases = 5000 + (2 * 10000) + (10 * 1000) = 35,000 Stargems ceiling
     * 
     * @return Maximum credit limit in Stargems
     */
    public static int getCreditCeiling() {
        int playerLevel = Global.getSector().getPlayerStats().getLevel();
        int vipPurchases = getCumulativeVIPPurchases();
        
        return CasinoConfig.BASE_DEBT_CEILING 
            + (vipPurchases * CasinoConfig.CEILING_INCREASE_PER_VIP)
            + (int) (playerLevel * CasinoConfig.OVERDRAFT_CEILING_LEVEL_MULTIPLIER);
    }
    
    /**
     * Get maximum allowed debt (debt ceiling).
     * Debt cannot grow beyond this amount due to interest.
     * 
     * Formula: creditCeiling * MAX_DEBT_MULTIPLIER (default 2x)
     * 
     * @return Maximum debt allowed in Stargems
     */
    public static int getMaxDebt() {
        return (int) (getCreditCeiling() * CasinoConfig.MAX_DEBT_MULTIPLIER);
    }

    /**
     * Get available credit for spending.
     * This is how much more the player can spend before hitting the ceiling.
     * 
     * AI_AGENT NOTE: Formula is ceiling + balance (balance can be negative).
     * Example: ceiling=5000, balance=-2000 -> available=3000
     * Example: ceiling=5000, balance=1000 -> available=6000
     * 
     * @return Available credit in Stargems
     */
    public static int getAvailableCredit() {
        int ceiling = getCreditCeiling();
        int balance = getBalance();
        
        // If balance is positive, they can spend up to balance + ceiling (overdraft)
        // If balance is negative, they can spend up to ceiling - |balance|
        return ceiling + balance;
    }

    /**
     * Initialize the casino system for a new player or save.
     * Sets default values for all memory keys if not already present.
     * 
     * AI_AGENT NOTE: This is safe to call multiple times - it only sets
     * values if the keys don't already exist (uses contains() check).
     */
    public static void initializeSystem() {
        // Initialize balance if not set
        if (!Global.getSector().getPlayerMemoryWithoutUpdate().contains("$ipc_stargems")) {
            Global.getSector().getPlayerMemoryWithoutUpdate().set("$ipc_stargems", 0);
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
    
    /**
     * Checks if overdraft is available for the player.
     * Overdraft is only available to VIP pass subscribers.
     * 
     * @return true if player has active VIP pass, false otherwise
     */
    public static boolean isOverdraftAvailable() {
        return getDaysRemaining() > 0;
    }
    
    /**
     * Gets the current debt amount (absolute value of negative balance).
     * 
     * @return debt amount (0 if balance is positive)
     */
    public static int getDebt() {
        int balance = getBalance();
        return balance < 0 ? -balance : 0;
    }
    
    /**
     * Toggles between daily and monthly VIP notification modes.
     * 
     * @return true if now in monthly mode, false if daily mode
     */
    public static boolean toggleMonthlyNotificationMode() {
        boolean currentMode = Global.getSector().getPlayerMemoryWithoutUpdate().getBoolean(MONTHLY_NOTIFY_MODE_KEY);
        boolean newMode = !currentMode;
        Global.getSector().getPlayerMemoryWithoutUpdate().set(MONTHLY_NOTIFY_MODE_KEY, newMode);
        
        if (newMode) {
            // When enabling monthly mode, set the last notify time to now
            Global.getSector().getPlayerMemoryWithoutUpdate().set(LAST_MONTHLY_NOTIFY_KEY, Global.getSector().getClock().getTimestamp());
        }
        
        return newMode;
    }
    
    /**
     * Checks if monthly notification mode is enabled.
     *
     * @return true if monthly mode, false if daily mode
     */
    public static boolean isMonthlyNotificationMode() {
        return Global.getSector().getPlayerMemoryWithoutUpdate().getBoolean(MONTHLY_NOTIFY_MODE_KEY);
    }

    /**
     * Selects a VIP ad that hasn't been shown in the last 4 messages.
     * Uses player memory to track recent ad history.
     *
     * AI_AGENT NOTE: This prevents the same ad from appearing twice within 4 messages.
     * If all ads are in history (when total ads <= 4), history is cleared.
     *
     * @return Selected ad message string
     */
    private String selectNonDuplicateAd() {
        @SuppressWarnings("unchecked")
        List<String> recentAds = (List<String>) Global.getSector().getPlayerMemoryWithoutUpdate().get(VIP_AD_HISTORY_KEY);
        if (recentAds == null) {
            recentAds = new ArrayList<>();
        }

        // Create a copy of available ads and remove recently shown ones
        List<String> availableAds = new ArrayList<>(CasinoConfig.VIP_ADS);
        availableAds.removeAll(recentAds);

        // If no ads available (all are in history), clear history
        if (availableAds.isEmpty()) {
            recentAds.clear();
            availableAds.addAll(CasinoConfig.VIP_ADS);
        }

        // Select random ad from available ones
        String selectedAd = availableAds.get(random.nextInt(availableAds.size()));

        // Update history: add new ad and trim to last 4
        recentAds.add(selectedAd);
        while (recentAds.size() > 4) {
            recentAds.remove(0);
        }

        // Store updated history
        Global.getSector().getPlayerMemoryWithoutUpdate().set(VIP_AD_HISTORY_KEY, recentAds);

        return selectedAd;
    }
}
