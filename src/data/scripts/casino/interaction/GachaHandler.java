package data.scripts.casino.interaction;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FleetMemberPickerListener;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import data.scripts.casino.CasinoConfig;
import data.scripts.casino.CasinoGachaManager;
import data.scripts.casino.CasinoVIPManager;
import data.scripts.casino.GachaAnimation;
import data.scripts.casino.GachaAnimationDialogDelegate;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class GachaHandler {

    private static final String OPTION_GACHA_MENU = "gacha_menu";
    private static final String OPTION_PULL_1 = "pull_1";
    private static final String OPTION_PULL_10 = "pull_10";
    private static final String OPTION_AUTO_CONVERT = "auto_convert";
    private static final String OPTION_HOW_TO_GACHA = "how_to_gacha";
    private static final String OPTION_BACK_MENU = "back_menu";
    private static final String OPTION_EXPLAIN_IPC_CREDIT = "explain_ipc_credit";
    private static final String PREFIX_CONFIRM_PULL = "confirm_pull_";

    private static final int RARITY_5_STAR = 5;
    private static final int RARITY_4_STAR = 4;
    private static final int RARITY_3_STAR = 3;
    private static final int RARITY_2_STAR = 2;

    private final CasinoInteraction main;
    private final Map<String, OptionHandler> handlers = new HashMap<>();

    private boolean justCompletedPull = false;
    private final List<FleetMemberAPI> shipsAwaitingConversionDecision = new ArrayList<>();

    public GachaHandler(CasinoInteraction main) {
        this.main = main;
        initializeHandlers();
    }

    private void initializeHandlers() {
        handlers.put(OPTION_GACHA_MENU, option -> showGachaMenu());
        handlers.put(OPTION_PULL_1, option -> showGachaConfirm(1));
        handlers.put(OPTION_PULL_10, option -> showGachaConfirm(10));
        handlers.put(OPTION_AUTO_CONVERT, option -> openAutoConvertPicker());
        handlers.put(OPTION_HOW_TO_GACHA, option -> main.help.showGachaHelp());
        handlers.put(OPTION_BACK_MENU, option -> main.showMenu());
        handlers.put(OPTION_EXPLAIN_IPC_CREDIT, option -> showIPCCreditExplanation());
    }

    private boolean isConfirmPullOption(String option) {
        return option != null && option.startsWith(PREFIX_CONFIRM_PULL);
    }

    private void handleConfirmPull(String option) {
        int rounds = Integer.parseInt(option.replace(PREFIX_CONFIRM_PULL, ""));
        performGachaPull(rounds);
    }

    private boolean canAffordTransaction(int amount) {
        return CasinoVIPManager.getAvailableCredit() >= amount;
    }

    private void displayFinancialInfo() {
        int currentBalance = CasinoVIPManager.getBalance();
        int creditCeiling = CasinoVIPManager.getCreditCeiling();
        int availableCredit = CasinoVIPManager.getAvailableCredit();
        int daysRemaining = CasinoVIPManager.getDaysRemaining();

        main.textPanel.addPara("--- FINANCIAL STATUS ---", Color.CYAN);

        Color balanceColor = currentBalance >= 0 ? Color.GREEN : Color.RED;
        main.textPanel.addPara("Balance: " + currentBalance + " Stargems", balanceColor);
        main.textPanel.addPara("Credit Ceiling: " + creditCeiling, Color.GRAY);
        main.textPanel.addPara("Available Credit: " + availableCredit, Color.YELLOW);

        if (daysRemaining > 0) {
            main.textPanel.addPara("VIP: " + daysRemaining + " days", Color.CYAN);
        }

        main.textPanel.addPara("------------------------", Color.CYAN);
    }

    public void handle(String option) {
        OptionHandler handler = handlers.get(option);
        if (handler != null) {
            handler.handle(option);
            return;
        }

        if (isConfirmPullOption(option)) {
            handleConfirmPull(option);
        }
    }

    public void showGachaMenu() {
        main.options.clearOptions();

        if (justCompletedPull && !shipsAwaitingConversionDecision.isEmpty()) {
            justCompletedPull = false;
            showConvertSelectionPicker(shipsAwaitingConversionDecision);
            return;
        }

        if (justCompletedPull) {
            justCompletedPull = false;
            showEmptyPullResult();
            return;
        }

        showStandardGachaMenu();
    }

    private void showEmptyPullResult() {
        main.textPanel.addPara("Gacha Results:", Color.CYAN);
        main.textPanel.addPara("No ships obtained from your pull.", Color.GRAY);
        main.options.addOption("Pull Again", OPTION_GACHA_MENU);
        main.options.addOption("Back to Main Menu", OPTION_BACK_MENU);
    }

    private void showStandardGachaMenu() {
        main.textPanel.addPara("Tachy-Impact Protocol", Color.CYAN);

        CasinoGachaManager manager = new CasinoGachaManager();
        manager.checkRotation();
        CasinoGachaManager.GachaData data = manager.getData();

        if (data.featuredCapital != null) {
            ShipHullSpecAPI capSpec = Global.getSettings().getHullSpec(data.featuredCapital);
            String capName = capSpec != null ? capSpec.getHullName() : data.featuredCapital;
            main.textPanel.addPara("FEATURED 5*: " + capName, Color.ORANGE);
        }

        main.textPanel.addPara("Pity Status:", Color.GRAY);
        main.textPanel.addPara("- 5* Pity: " + data.pity5 + "/" + CasinoConfig.PITY_HARD_5);
        main.textPanel.addPara("- 4* Pity: " + data.pity4 + "/" + CasinoConfig.PITY_HARD_4);

        displayFinancialInfo();
        addPullOptions();

        main.options.addOption("View Ship Pool", OPTION_AUTO_CONVERT);
        main.options.addOption("Gacha Handbook", OPTION_HOW_TO_GACHA);
        main.options.addOption("Back", OPTION_BACK_MENU);
        main.setState(CasinoInteraction.State.GACHA);
    }

    private void addPullOptions() {
        int availableCredit = CasinoVIPManager.getAvailableCredit();

        if (availableCredit <= 0) {
            main.textPanel.addPara("Your credit facility is exhausted. You cannot afford even a single pull.", Color.RED);
            main.textPanel.addPara("Please visit Stargem Top-up to purchase more gems.", Color.YELLOW);
            return;
        }

        if (availableCredit < CasinoConfig.GACHA_COST) {
            main.textPanel.addPara("Your available credit (" + availableCredit + " Stargems) is insufficient for a single pull (" + CasinoConfig.GACHA_COST + " Stargems).", Color.RED);
            main.textPanel.addPara("Please visit Stargem Top-up to purchase more gems.", Color.YELLOW);
            return;
        }

        if (canAffordTransaction(CasinoConfig.GACHA_COST)) {
            main.options.addOption("Pull 1x (" + CasinoConfig.GACHA_COST + " Gems)", OPTION_PULL_1);
        }
        if (canAffordTransaction(CasinoConfig.GACHA_COST * 10)) {
            main.options.addOption("Pull 10x (" + (CasinoConfig.GACHA_COST * 10) + " Gems)", OPTION_PULL_10);
        }
    }

    private void showGachaConfirm(int times) {
        main.options.clearOptions();
        int cost = times * CasinoConfig.GACHA_COST;
        int currentBalance = CasinoVIPManager.getBalance();
        int availableCredit = CasinoVIPManager.getAvailableCredit();

        if (currentBalance >= cost) {
            main.textPanel.addPara("Confirm initiating Warp Sequence " + times + "x for " + cost + " Stargems?", Color.YELLOW);
            main.options.addOption("Confirm Warp", PREFIX_CONFIRM_PULL + times);
            main.options.addOption("Cancel", OPTION_GACHA_MENU);
            return;
        }

        if (availableCredit <= 0) {
            main.textPanel.addPara("Your credit facility is exhausted. You cannot afford this pull.", Color.RED);
            main.textPanel.addPara("Please visit Stargem Top-up to purchase more gems.", Color.YELLOW);
            main.options.addOption("Back", OPTION_GACHA_MENU);
            return;
        }

        if (availableCredit < cost) {
            int maxAffordablePulls = availableCredit / CasinoConfig.GACHA_COST;
            main.textPanel.addPara("Your available credit (" + availableCredit + " Stargems) is insufficient for " + times + "x pull (" + cost + " Stargems).", Color.RED);
            if (maxAffordablePulls > 0) {
                main.textPanel.addPara("You can afford up to " + maxAffordablePulls + " pull(s) with your current credit.", Color.YELLOW);
            }
            main.textPanel.addPara("Please select a smaller pull amount or visit Stargem Top-up.", Color.YELLOW);
            main.options.addOption("Back", OPTION_GACHA_MENU);
            return;
        }

        int overdraftAmount = cost - currentBalance;
        showOverdraftConfirm(times, cost, overdraftAmount);
    }

    private void showOverdraftConfirm(int times, int cost, int overdraftAmount) {
        main.options.clearOptions();

        if (!CasinoVIPManager.isOverdraftAvailable()) {
            showVIPPromotionForOverdraft(times, cost);
            return;
        }

        main.textPanel.addPara("IPC CREDIT ALERT", Color.ORANGE);
        main.textPanel.addPara("Your Stargem balance is insufficient for this transaction.", Color.YELLOW);
        main.textPanel.addPara("Current Balance: " + CasinoVIPManager.getBalance(), Color.GRAY);
        main.textPanel.addPara("Transaction Cost: " + cost + " Stargems", Color.GRAY);
        main.textPanel.addPara("Required Overdraft: " + overdraftAmount + " Stargems", Color.RED);
        main.textPanel.addPara("");
        main.textPanel.addPara("The IPC extends credit facilities to valued customers. Your balance will go negative.", Color.CYAN);
        main.textPanel.addPara("Note: Negative balances accrue " + (int)(CasinoConfig.VIP_DAILY_INTEREST_RATE * 100) + "% daily interest. The Corporate Reconciliation Team may contact you regarding payment.", Color.YELLOW);

        main.options.addOption("Authorize Overdraft", PREFIX_CONFIRM_PULL + times);
        main.options.addOption("What is IPC Credit?", OPTION_EXPLAIN_IPC_CREDIT);
        main.options.addOption("Cancel", OPTION_GACHA_MENU);
    }

    private void showVIPPromotionForOverdraft(int times, int cost) {
        main.textPanel.addPara("INSUFFICIENT STARGEMS", Color.RED);
        main.textPanel.addPara("");
        main.textPanel.addPara("Your Stargem balance is insufficient for this transaction.", Color.YELLOW);
        main.textPanel.addPara("Current Balance: " + CasinoVIPManager.getBalance(), Color.GRAY);
        main.textPanel.addPara("Transaction Cost: " + cost + " Stargems", Color.GRAY);
        main.textPanel.addPara("");
        main.textPanel.addPara("IPC CREDIT FACILITY", Color.CYAN);
        main.textPanel.addPara("Overdraft protection is exclusively available to VIP Pass subscribers.", Color.WHITE);
        main.textPanel.addPara("");
        main.textPanel.addPara("VIP PASS BENEFITS:", Color.GREEN);
        main.textPanel.addPara("- Access to IPC Credit Facility (overdraft protection)", Color.GRAY);
        main.textPanel.addPara("- " + CasinoConfig.VIP_DAILY_REWARD + " Stargems daily reward", Color.GRAY);
        main.textPanel.addPara("- Reduced debt interest rate (" + (int)(CasinoConfig.VIP_DAILY_INTEREST_RATE * 100) + "% daily)", Color.GRAY);
        main.textPanel.addPara("- Increased credit ceiling per purchase", Color.GRAY);
        main.textPanel.addPara("");
        main.textPanel.addPara("Purchase a VIP Pass from Financial Services to unlock overdraft protection!", Color.YELLOW);

        main.options.addOption("Go to Stargem Top-up", "topup_menu");
        main.options.addOption("Cancel", OPTION_GACHA_MENU);
    }

    private void showIPCCreditExplanation() {
        main.options.clearOptions();

        main.textPanel.addPara("IPC CREDIT FACILITY", Color.CYAN);
        main.textPanel.addPara("");
        main.textPanel.addPara("The IPC provides flexible credit options for valued customers experiencing temporary liquidity constraints.", Color.WHITE);
        main.textPanel.addPara("");
        main.textPanel.addPara("HOW IT WORKS:", Color.YELLOW);
        main.textPanel.addPara("- Your account has a credit ceiling based on your VIP status and purchase history.", Color.GRAY);
        main.textPanel.addPara("- You may spend beyond your current balance up to this ceiling.", Color.GRAY);
        main.textPanel.addPara("- Your balance becomes negative when using credit.", Color.GRAY);
        main.textPanel.addPara("");
        main.textPanel.addPara("INTEREST & COLLECTIONS:", Color.YELLOW);
        main.textPanel.addPara("- Negative balances accrue daily interest.", Color.GRAY);
        main.textPanel.addPara("- Continued delinquency may prompt Corporate Reconciliation Team intervention.", Color.GRAY);
        main.textPanel.addPara("- Earn Stargems through gameplay or purchase packages to restore positive balance.", Color.GRAY);
        main.textPanel.addPara("");
        main.textPanel.addPara("INCREASING YOUR CREDIT CEILING:", Color.YELLOW);
        main.textPanel.addPara("- Purchase VIP Passes for permanent ceiling increases.", Color.GRAY);
        main.textPanel.addPara("- Regular top-ups and purchases demonstrate creditworthiness.", Color.GRAY);

        main.options.addOption("Back", OPTION_GACHA_MENU);
    }

    private void performGachaPull(int times) {
        if (justCompletedPull) {
            showGachaMenu();
            return;
        }

        int cost = times * CasinoConfig.GACHA_COST;
        int availableCredit = CasinoVIPManager.getAvailableCredit();

        if (availableCredit < cost) {
            main.textPanel.addPara("IPC CREDIT DENIED: Transaction exceeds your credit ceiling.", Color.RED);
            main.textPanel.addPara("Please contact Financial Services to increase your credit limit.", Color.YELLOW);
            showGachaMenu();
            return;
        }

        CasinoVIPManager.addToBalance(-cost);

        CasinoGachaManager manager = new CasinoGachaManager();
        main.textPanel.addPara("Initiating Warp Sequence...", Color.CYAN);

        List<FleetMemberAPI> obtainedShips = new ArrayList<>();
        List<String> pullResults = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            String result = manager.performPullDetailed(obtainedShips);
            pullResults.add(result);
        }

        showGachaAnimation(pullResults, obtainedShips);
    }

    private void showGachaAnimation(List<String> pullResults, List<FleetMemberAPI> obtainedShips) {
        List<GachaAnimation.GachaItem> itemsToAnimate = new ArrayList<>();

        for (String result : pullResults) {
            int rarity = getRarityFromResult(result);
            GachaAnimation.GachaItem item = new GachaAnimation.GachaItem(
                "item_" + System.currentTimeMillis() + "_" + itemsToAnimate.size(),
                result,
                rarity
            );
            itemsToAnimate.add(item);
        }

        this.shipsAwaitingConversionDecision.clear();
        this.shipsAwaitingConversionDecision.addAll(obtainedShips);

        GachaAnimation animation = new GachaAnimation(itemsToAnimate, results -> {
            justCompletedPull = true;
        });

        GachaAnimationDialogDelegate delegate = createAnimationDialog(animation, () -> {
            showGachaMenu();
        });

        main.getDialog().showCustomVisualDialog(800f, 600f, delegate);
    }

    private int getRarityFromResult(String result) {
        if (result.contains("5*") || result.toLowerCase().contains("legendary")) {
            return RARITY_5_STAR;
        }
        if (result.contains("4*") || result.toLowerCase().contains("rare")) {
            return RARITY_4_STAR;
        }
        if (result.contains("2*") || result.toLowerCase().contains("common")) {
            return RARITY_2_STAR;
        }
        return RARITY_3_STAR;
    }

    private GachaAnimationDialogDelegate createAnimationDialog(GachaAnimation animation, Runnable onDismissCallback) {
        return new GachaAnimationDialogDelegate(null, animation, main.getDialog(), null, onDismissCallback);
    }

    private void showConvertSelectionPicker(List<FleetMemberAPI> obtainedShips) {
        main.getDialog().showFleetMemberPickerDialog(
            "Select ships you want to convert to Stargems:",
            "Confirm Selection",
            "Cancel",
            10,
            7,
            88,
            true,
            true,
            obtainedShips,
            new FleetMemberPickerListener() {
                public void pickedFleetMembers(List<FleetMemberAPI> selectedMembers) {
                    List<String> selectedHullIds = new ArrayList<>();
                    if (selectedMembers != null) {
                        for (FleetMemberAPI member : selectedMembers) {
                            if (member != null && member.getHullId() != null) {
                                selectedHullIds.add(member.getHullId());
                            }
                        }
                    }

                    for (FleetMemberAPI ship : obtainedShips) {
                        if (ship == null) continue;

                        if (selectedHullIds.contains(ship.getHullId())) {
                            int val = (int)(ship.getHullSpec().getBaseValue() / CasinoConfig.SHIP_TRADE_RATE * CasinoConfig.SHIP_SELL_MULTIPLIER);
                            CasinoVIPManager.addToBalance(val);
                            main.textPanel.addPara("Converted " + (ship.getShipName() != null ? ship.getShipName() : "Unknown Ship") + " to " + val + " Stargems.", Color.GREEN);
                        } else {
                            Global.getSector().getPlayerFleet().getFleetData().addFleetMember(ship);
                        }
                    }

                    showPostPullOptions();
                }

                public void cancelledFleetMemberPicking() {
                    for (FleetMemberAPI ship : obtainedShips) {
                        if (ship != null) {
                            Global.getSector().getPlayerFleet().getFleetData().addFleetMember(ship);
                        }
                    }
                    showPostPullOptions();
                }
            });
    }

    private void showPostPullOptions() {
        main.options.clearOptions();
        main.options.addOption("Pull Again", OPTION_GACHA_MENU);
        main.options.addOption("Back to Main Menu", OPTION_BACK_MENU);
    }

    private void openAutoConvertPicker() {
        CasinoGachaManager manager = new CasinoGachaManager();
        List<FleetMemberAPI> potentialDrops = manager.getPotentialDrops();

        main.textPanel.addPara("Current Ship Pool (" + potentialDrops.size() + " ships):", Color.CYAN);
        main.textPanel.addPara("These are the ships currently available in the gacha pool.", Color.GRAY);

        main.getDialog().showFleetMemberPickerDialog("View Ship Pool:", null, "Back", 10, 7, 88, false, false, potentialDrops,
            new FleetMemberPickerListener() {
                public void pickedFleetMembers(List<FleetMemberAPI> members) {
                     showGachaMenu();
                }
                public void cancelledFleetMemberPicking() { showGachaMenu(); }
            });
    }

    @FunctionalInterface
    private interface OptionHandler {
        void handle(String option);
    }
}
