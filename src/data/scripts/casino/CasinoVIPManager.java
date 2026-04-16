package data.scripts.casino;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Manages VIP status, Stargem balance, credit, and daily rewards. */
public class CasinoVIPManager implements EveryFrameScript {

    private static final String LAST_REWARD_TIME_KEY = "$ipc_vip_last_reward_time";
    private static final String LAST_PROCESSED_DAY_KEY = "$ipc_vip_last_processed_day";
    private static final String LAST_MONTHLY_NOTIFY_KEY = "$ipc_vip_last_monthly_notify";
    private static final String MONTHLY_NOTIFY_MODE_KEY = "$ipc_vip_monthly_notify_mode";
    private static final String VIP_AD_HISTORY_KEY = "$ipc_vip_ad_history";
    private static final String LAST_DEBT_WARNING_KEY = "$ipc_last_debt_warning";

    private float timer = 0f;
    private final Random random = new Random();

    @Override
    public boolean isDone() { return false; }

    @Override
    public boolean runWhilePaused() { return false; }

    public void advance(float amount) {
        timer += amount;
        if (timer < 1.0f) return;
        timer -= 1.0f;

        CampaignClockAPI clock = Global.getSector().getClock();
        MemoryAPI memory = Global.getSector().getPlayerMemoryWithoutUpdate();
        
        if (!memory.contains(LAST_PROCESSED_DAY_KEY)) {
            memory.set(LAST_PROCESSED_DAY_KEY, clock.getTimestamp());
            return;
        }
        
        long lastProcessedDay = memory.getLong(LAST_PROCESSED_DAY_KEY);
        if (lastProcessedDay == 0) {
            memory.set(LAST_PROCESSED_DAY_KEY, clock.getTimestamp());
            return;
        }
        
        float daysElapsed = clock.getElapsedDaysSince(lastProcessedDay);
        if (daysElapsed >= 1.0f) {
            checkDaily();
            memory.set(LAST_PROCESSED_DAY_KEY, clock.getTimestamp());
        }
    }

    private void checkDaily() {
        int daysRemaining = getDaysRemaining();
        boolean hasVIP = daysRemaining > 0;
        int currentBalance = getBalance();
        boolean hasDebt = currentBalance < 0;

        CampaignClockAPI clock = Global.getSector().getClock();
        MemoryAPI memory = Global.getSector().getPlayerMemoryWithoutUpdate();
        
        Long lastRewardTime = null;
        if (memory.contains(LAST_REWARD_TIME_KEY)) {
            lastRewardTime = memory.getLong(LAST_REWARD_TIME_KEY);
        }

        float daysSinceLastReward;
        if (lastRewardTime == null || lastRewardTime == 0 || lastRewardTime == -1L) {
            daysSinceLastReward = 0;
        } else {
            daysSinceLastReward = clock.getElapsedDaysSince(lastRewardTime);
        }
        
        if (lastRewardTime == null || lastRewardTime == 0 || daysSinceLastReward >= 1.0f || lastRewardTime == -1L) {
            
            int interestAmount = 0;
            if (hasDebt) {
                int currentDebt = -currentBalance;
                int maxDebt = getMaxDebt();
                
if (currentDebt >= maxDebt) {
                    Global.getSector().getCampaignUI().addMessage(
                        Strings.get("notifications.debt_max_limit"),
                        Color.ORANGE
                    );
                } else {
                    float interestRate = hasVIP ? CasinoConfig.VIP_DAILY_INTEREST_RATE : CasinoConfig.NORMAL_DAILY_INTEREST_RATE;
                    interestAmount = (int) (currentDebt * interestRate);
                    
                    if (currentDebt + interestAmount > maxDebt) {
                        interestAmount = maxDebt - currentDebt;
                    }
                    
                    addToBalance(-interestAmount);
                    
                    Global.getLogger(CasinoVIPManager.class).info(
                        "Interest applied: " + interestAmount + " gems (debt: " + currentDebt + ", rate: " + interestRate + ")"
                    );
                }
            }
            
            if (hasVIP) {
                addToBalance(CasinoConfig.VIP_DAILY_REWARD);
                
                if (shouldShowNotification()) {
                    sendVIPNotification(interestAmount);
                }
            } else if (hasDebt) {
                sendDebtWarningNotification(interestAmount);
                
                checkMonthlyDebtWarning();
            }
            
            memory.set(LAST_REWARD_TIME_KEY, clock.getTimestamp());
        }
    }
    
    private void checkMonthlyDebtWarning() {
        if (shouldSuppressDebtNotification()) {
            return;
        }
        
        CampaignClockAPI clock = Global.getSector().getClock();
        MemoryAPI memory = Global.getSector().getPlayerMemoryWithoutUpdate();
        
        Long lastWarningTime = null;
        if (memory.contains(LAST_DEBT_WARNING_KEY)) {
            lastWarningTime = memory.getLong(LAST_DEBT_WARNING_KEY);
        }
        
        boolean shouldWarn = false;
        if (lastWarningTime == null || lastWarningTime == 0) {
            shouldWarn = true;
        } else {
            float daysSinceWarning = clock.getElapsedDaysSince(lastWarningTime);
            if (daysSinceWarning >= 30f) {
                shouldWarn = true;
            }
        }
        
        if (shouldWarn) {
            int currentDebt = -getBalance();
            int creditCeiling = getCreditCeiling();
            float debtPercent = (float) currentDebt / creditCeiling * 100f;
            
            Global.getSector().getCampaignUI().addMessage(
                Strings.format("notifications.monthly_debt_notice", currentDebt, CasinoConfig.NORMAL_DAILY_INTEREST_RATE * 100),
                Color.YELLOW
            );
            
            if (debtPercent >= 80f) {
                Global.getSector().getCampaignUI().addMessage(
                    Strings.format("notifications.critical_debt_warning", debtPercent),
                    Color.RED
                );
            }
            
            memory.set(LAST_DEBT_WARNING_KEY, clock.getTimestamp());
        }
    }

    private boolean shouldShowNotification() {
        return shouldShowNotificationInternal();
    }
    
    public static boolean shouldSuppressDebtNotification() {
        return !shouldShowNotificationInternal();
    }
    
    private static boolean shouldShowNotificationInternal() {
        MemoryAPI memory = Global.getSector().getPlayerMemoryWithoutUpdate();
        boolean monthlyMode = memory.getBoolean(MONTHLY_NOTIFY_MODE_KEY);
        if (!monthlyMode) {
            return true;
        }
        
        CampaignClockAPI clock = Global.getSector().getClock();
        
        if (!memory.contains(LAST_MONTHLY_NOTIFY_KEY)) {
            memory.set(LAST_MONTHLY_NOTIFY_KEY, clock.getTimestamp());
            return true;
        }
        
        long lastMonthlyNotify = memory.getLong(LAST_MONTHLY_NOTIFY_KEY);
        if (lastMonthlyNotify == 0) {
            memory.set(LAST_MONTHLY_NOTIFY_KEY, clock.getTimestamp());
            return true;
        }
        
        float daysSinceLastNotify = clock.getElapsedDaysSince(lastMonthlyNotify);
        if (daysSinceLastNotify >= 30f) {
            memory.set(LAST_MONTHLY_NOTIFY_KEY, clock.getTimestamp());
            return true;
        }
        
        return false;
    }

    private void sendVIPNotification(int interestAmount) {
        int newBalance = getBalance();
        int daysRemaining = getDaysRemaining();
        
        StringBuilder rewardLine = new StringBuilder();
        rewardLine.append(Strings.format("notifications.daily_gem", CasinoConfig.VIP_DAILY_REWARD));
        
        if (interestAmount > 0) {
            rewardLine.append(", ").append(Strings.format("notifications.interest", interestAmount));
        }
        
        Global.getSector().getCampaignUI().addMessage(rewardLine.toString(), Color.GREEN);
        
        Color balanceColor = newBalance >= 0 ? Color.GREEN : Color.RED;
        String balanceLine = Strings.format("notifications.balance_vip", newBalance, daysRemaining);
        Global.getSector().getCampaignUI().addMessage(balanceLine, balanceColor);
        
        List<String> vipAds = Strings.getList("vip_ads");
        if (!vipAds.isEmpty()) {
            String ad = selectNonDuplicateAd(vipAds);
            Global.getSector().getCampaignUI().addMessage(
                Strings.format("notifications.vip_ad_prefix", ad),
                Color.CYAN
            );
        }
    }
    
    private void sendDebtWarningNotification(int interestAmount) {
        if (shouldSuppressDebtNotification()) {
            return;
        }
        
        int newBalance = getBalance();
        
        if (interestAmount > 0) {
            Global.getSector().getCampaignUI().addMessage(
                Strings.format("notifications.interest", interestAmount),
                Color.RED
            );
        }
        
        Global.getSector().getCampaignUI().addMessage(
            Strings.format("notifications.balance_vip", newBalance, 0),
            Color.RED
        );
        
        Global.getSector().getCampaignUI().addMessage(
            Strings.get("notifications.debt_collector_warning"),
            Color.ORANGE
        );
    }
    
    public static int getBalance() {
        return Global.getSector().getPlayerMemoryWithoutUpdate().getInt("$ipc_stargems");
    }

    public static int getStargems() {
        return getBalance();
    }

    public static int getCumulativeVIPPurchases() {
        return Global.getSector().getPlayerMemoryWithoutUpdate().getInt("$ipc_cumulative_vip_purchases");
    }

    public static int getCumulativeTopupAmount() {
        return Global.getSector().getPlayerMemoryWithoutUpdate().getInt("$ipc_cumulative_topup_amount");
    }

    public static void addToBalance(int amount) {
        int current = getBalance();
        int newAmount = current + amount;
        Global.getSector().getPlayerMemoryWithoutUpdate().set("$ipc_stargems", newAmount);
        
        if (amount > 0) {
            addCumulativeTopup(amount);
        }
    }

    public static void addCumulativeVIPPurchases(int passes) {
        int current = getCumulativeVIPPurchases();
        int newAmount = current + passes;
        Global.getSector().getPlayerMemoryWithoutUpdate().set("$ipc_cumulative_vip_purchases", newAmount);
    }

    public static void addCumulativeTopup(int amount) {
        int current = getCumulativeTopupAmount();
        int newAmount = current + amount;
        Global.getSector().getPlayerMemoryWithoutUpdate().set("$ipc_cumulative_topup_amount", newAmount);
    }

    public static int getDaysRemaining() {
        CampaignClockAPI clock = Global.getSector().getClock();
        MemoryAPI memory = Global.getSector().getPlayerMemoryWithoutUpdate();
        
        if (!memory.contains("$ipc_vip_duration")) {
            return 0;
        }
        
        int duration = memory.getInt("$ipc_vip_duration");
        if (duration <= 0) return 0;

        if (!memory.contains("$ipc_vip_start_time")) {
            return 0;
        }
        
        long startTime = memory.getLong("$ipc_vip_start_time");
        
        if (startTime == 0) {
            return 0;
        }
        
        float elapsedDays = clock.getElapsedDaysSince(startTime);
        int remaining = duration - (int) elapsedDays;
        
        return Math.max(0, remaining);
    }

    public static void addSubscriptionDays(int days) {
        CampaignClockAPI clock = Global.getSector().getClock();
        MemoryAPI memory = Global.getSector().getPlayerMemoryWithoutUpdate();
        
        long currentTimestamp = clock.getTimestamp();
        
        int newDuration;
        int currentRemaining = getDaysRemaining();
        
        if (currentRemaining <= 0) {
            newDuration = days;
        } else {
            newDuration = currentRemaining + days;
        }
        
        memory.set("$ipc_vip_start_time", currentTimestamp);
        memory.set("$ipc_vip_duration", newDuration);
        
        addCumulativeVIPPurchases(1);
        
        memory.set(LAST_REWARD_TIME_KEY, -1L);
        
        Global.getLogger(CasinoVIPManager.class).info(
            "VIP Pass purchased: startTime=" + currentTimestamp + ", duration=" + newDuration + " days"
        );
        
        processImmediateDailyReward();
    }
    
    private static void processImmediateDailyReward() {
        int daysRemaining = getDaysRemaining();
        boolean hasVIP = daysRemaining > 0;
        int currentBalance = getBalance();
        boolean hasDebt = currentBalance < 0;

        if (hasVIP) {
            addToBalance(CasinoConfig.VIP_DAILY_REWARD);
            
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
            
            CampaignClockAPI clock = Global.getSector().getClock();
            sendVIPNotificationImmediate(interestAmount);
            
            Global.getSector().getPlayerMemoryWithoutUpdate().set(LAST_REWARD_TIME_KEY, clock.getTimestamp());
        }
    }
    
    private static void sendVIPNotificationImmediate(int interestAmount) {
        int newBalance = getBalance();
        int daysRemaining = getDaysRemaining();
        
        StringBuilder rewardLine = new StringBuilder();
        rewardLine.append(Strings.format("notifications.daily_gem_bonus", CasinoConfig.VIP_DAILY_REWARD));
        
        if (interestAmount > 0) {
            rewardLine.append(", ").append(Strings.format("notifications.interest", interestAmount));
        }
        
        Global.getSector().getCampaignUI().addMessage(rewardLine.toString(), Color.GREEN);
        
        Color balanceColor = newBalance >= 0 ? Color.GREEN : Color.RED;
        String balanceLine = Strings.format("notifications.balance_status", newBalance, daysRemaining);
        Global.getSector().getCampaignUI().addMessage(balanceLine, balanceColor);
    }

    public static int getCreditCeiling() {
        if (getDaysRemaining() <= 0) {
            return 0;
        }
        
        int playerLevel = Global.getSector().getPlayerStats().getLevel();
        int vipPurchases = getCumulativeVIPPurchases();
        
        return CasinoConfig.BASE_DEBT_CEILING 
            + (vipPurchases * CasinoConfig.CEILING_INCREASE_PER_VIP)
            + (int) (playerLevel * CasinoConfig.OVERDRAFT_CEILING_LEVEL_MULTIPLIER);
    }
    
    public static int getMaxDebt() {
        return (int) (getCreditCeiling() * CasinoConfig.MAX_DEBT_MULTIPLIER);
    }

    public static int getAvailableCredit() {
        int ceiling = getCreditCeiling();
        int balance = getBalance();
        
        return ceiling + balance;
    }

    public static void initializeSystem() {
        MemoryAPI memory = Global.getSector().getPlayerMemoryWithoutUpdate();
        
        if (!memory.contains("$ipc_stargems")) {
            memory.set("$ipc_stargems", 0);
        }
        
        if (!memory.contains("$ipc_cumulative_vip_purchases")) {
            memory.set("$ipc_cumulative_vip_purchases", 0);
        }
        
        if (!memory.contains("$ipc_cumulative_topup_amount")) {
            memory.set("$ipc_cumulative_topup_amount", 0);
        }
        
        if (!memory.contains("$ipc_vip_start_time")) {
            memory.set("$ipc_vip_start_time", 0L);
        }
        
        if (!memory.contains("$ipc_vip_duration")) {
            memory.set("$ipc_vip_duration", 0);
        }
        
        if (!memory.contains(LAST_REWARD_TIME_KEY)) {
            memory.set(LAST_REWARD_TIME_KEY, -1L);
        }
        
        if (!memory.contains(LAST_PROCESSED_DAY_KEY)) {
            memory.set(LAST_PROCESSED_DAY_KEY, Global.getSector().getClock().getTimestamp());
        }
        
        if (!memory.contains(LAST_MONTHLY_NOTIFY_KEY)) {
            memory.set(LAST_MONTHLY_NOTIFY_KEY, 0L);
        }
        
        if (!memory.contains(MONTHLY_NOTIFY_MODE_KEY)) {
            memory.set(MONTHLY_NOTIFY_MODE_KEY, false);
        }
        
        if (!memory.contains(LAST_DEBT_WARNING_KEY)) {
            memory.set(LAST_DEBT_WARNING_KEY, 0L);
        }
    }
    
    public static boolean isOverdraftAvailable() {
        return getDaysRemaining() > 0;
    }
    
    public static int getDebt() {
        int balance = getBalance();
        return balance < 0 ? -balance : 0;
    }
    
    public static boolean toggleMonthlyNotificationMode() {
        MemoryAPI memory = Global.getSector().getPlayerMemoryWithoutUpdate();
        boolean currentMode = memory.getBoolean(MONTHLY_NOTIFY_MODE_KEY);
        boolean newMode = !currentMode;
        memory.set(MONTHLY_NOTIFY_MODE_KEY, newMode);
        
        if (newMode) {
            memory.set(LAST_MONTHLY_NOTIFY_KEY, Global.getSector().getClock().getTimestamp());
        }
        
        return newMode;
    }
    
    public static boolean isMonthlyNotificationMode() {
        return Global.getSector().getPlayerMemoryWithoutUpdate().getBoolean(MONTHLY_NOTIFY_MODE_KEY);
    }

    private String selectNonDuplicateAd(List<String> vipAds) {
        @SuppressWarnings("unchecked")
        List<String> recentAds = (List<String>) Global.getSector().getPlayerMemoryWithoutUpdate().get(VIP_AD_HISTORY_KEY);
        if (recentAds == null) {
            recentAds = new ArrayList<>();
        }

        List<String> availableAds = new ArrayList<>(vipAds);
        availableAds.removeAll(recentAds);

        if (availableAds.isEmpty()) {
            recentAds.clear();
            availableAds.addAll(vipAds);
        }

        String selectedAd = availableAds.get(random.nextInt(availableAds.size()));

        recentAds.add(selectedAd);
        while (recentAds.size() > 4) {
            recentAds.remove(0);
        }

        Global.getSector().getPlayerMemoryWithoutUpdate().set(VIP_AD_HISTORY_KEY, recentAds);

        return selectedAd;
    }
}
