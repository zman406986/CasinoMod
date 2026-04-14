package data.scripts.casino.interaction;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FleetMemberPickerListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import data.scripts.casino.CasinoConfig;
import data.scripts.casino.gacha.CasinoGachaManager;
import data.scripts.casino.CasinoVIPManager;
import data.scripts.casino.gacha.GachaAnimation;
import data.scripts.casino.gacha.GachaAnimationDialogDelegate;
import data.scripts.casino.Strings;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    private static final String MEMORY_KEY_AUTO_CONVERT = "$ipc_gacha_auto_convert";

    @SuppressWarnings("unchecked")
    private Set<String> getAutoConvertHullIds() {
        MemoryAPI memory = Global.getSector().getMemoryWithoutUpdate();
        Set<String> hullIds = new HashSet<>();
        if (memory.contains(MEMORY_KEY_AUTO_CONVERT)) {
            Object data = memory.get(MEMORY_KEY_AUTO_CONVERT);
            if (data instanceof Set) {
                hullIds.addAll((Set<String>) data);
            }
        }
        return hullIds;
    }

    private void saveAutoConvertHullIds(Set<String> hullIds) {
        MemoryAPI memory = Global.getSector().getMemoryWithoutUpdate();
        memory.set(MEMORY_KEY_AUTO_CONVERT, hullIds);
    }

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

        main.textPanel.addPara(Strings.get("financial_status.header"), Color.CYAN);

        Color balanceColor = currentBalance >= 0 ? Color.GREEN : Color.RED;
        main.textPanel.addPara(Strings.format("financial_status.balance", currentBalance), balanceColor);
        main.textPanel.addPara(Strings.format("financial_status.credit_ceiling", creditCeiling), Color.GRAY);
        main.textPanel.addPara(Strings.format("financial_status.available_credit", availableCredit), Color.YELLOW);

        if (daysRemaining > 0) {
            main.textPanel.addPara(Strings.format("financial_status.vip_days", daysRemaining), Color.CYAN);
        }

        main.textPanel.addPara(Strings.get("financial_status.divider"), Color.CYAN);
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
        main.textPanel.addPara(Strings.get("gacha.result_header"), Color.CYAN);
        main.textPanel.addPara(Strings.get("gacha.no_results"), Color.GRAY);
        main.options.addOption(Strings.get("gacha.pull_again"), OPTION_GACHA_MENU);
        main.options.addOption(Strings.get("gacha.back_to_main"), OPTION_BACK_MENU);
    }

    private void showStandardGachaMenu() {
        main.textPanel.addPara(Strings.get("gacha.title"), Color.CYAN);

        CasinoGachaManager manager = new CasinoGachaManager();
        manager.checkRotation();
        CasinoGachaManager.GachaData data = manager.getData();

        if (data.featuredCapital != null) {
            ShipHullSpecAPI capSpec = Global.getSettings().getHullSpec(data.featuredCapital);
            String capName = capSpec != null ? capSpec.getHullName() : data.featuredCapital;
            main.textPanel.addPara(Strings.format("gacha.featured_5star", capName), Color.ORANGE);
        }

        main.textPanel.addPara(Strings.get("gacha.pity_status"), Color.GRAY);
        main.textPanel.addPara(Strings.format("gacha.pity_5star", data.pity5, CasinoConfig.PITY_HARD_5));
        main.textPanel.addPara(Strings.format("gacha.pity_4star", data.pity4, CasinoConfig.PITY_HARD_4));

        Set<String> autoConvertHullIds = getAutoConvertHullIds();
        if (!autoConvertHullIds.isEmpty()) {
            main.textPanel.addPara(Strings.format("gacha.auto_convert_count", autoConvertHullIds.size()), Color.GRAY);
        }

        displayFinancialInfo();
        addPullOptions();

        main.options.addOption(Strings.get("gacha.auto_convert_menu"), OPTION_AUTO_CONVERT);
        main.options.addOption(Strings.get("gacha.handbook"), OPTION_HOW_TO_GACHA);
        main.options.addOption(Strings.get("common.back"), OPTION_BACK_MENU);
        main.setState(CasinoInteraction.State.GACHA);
    }

    private void addPullOptions() {
        int availableCredit = CasinoVIPManager.getAvailableCredit();

        if (availableCredit <= 0) {
            main.textPanel.addPara(Strings.get("gacha.credit_exhausted"), Color.RED);
            main.textPanel.addPara(Strings.get("gacha.please_topup"), Color.YELLOW);
            return;
        }

        if (availableCredit < CasinoConfig.GACHA_COST) {
            main.textPanel.addPara(Strings.format("gacha.insufficient_credit", availableCredit, CasinoConfig.GACHA_COST), Color.RED);
            main.textPanel.addPara(Strings.get("gacha.please_topup"), Color.YELLOW);
            return;
        }

        if (canAffordTransaction(CasinoConfig.GACHA_COST)) {
            main.options.addOption(Strings.format("gacha.pull_1x", CasinoConfig.GACHA_COST), OPTION_PULL_1);
        }
        if (canAffordTransaction(CasinoConfig.GACHA_COST * 10)) {
            main.options.addOption(Strings.format("gacha.pull_10x", CasinoConfig.GACHA_COST * 10), OPTION_PULL_10);
        }
    }

    private void showGachaConfirm(int times) {
        main.options.clearOptions();
        int cost = times * CasinoConfig.GACHA_COST;
        int currentBalance = CasinoVIPManager.getBalance();
        int availableCredit = CasinoVIPManager.getAvailableCredit();

        if (currentBalance >= cost) {
            main.textPanel.addPara(Strings.format("gacha.confirm_warp", times, cost), Color.YELLOW);
            main.options.addOption(Strings.get("gacha.confirm_warp_btn"), PREFIX_CONFIRM_PULL + times);
            main.options.addOption(Strings.get("common.cancel"), OPTION_GACHA_MENU);
            return;
        }

        if (availableCredit <= 0) {
            main.textPanel.addPara(Strings.get("gacha_overdraft.credit_exhausted"), Color.RED);
            main.textPanel.addPara(Strings.get("gacha.please_topup"), Color.YELLOW);
            main.options.addOption(Strings.get("common.back"), OPTION_GACHA_MENU);
            return;
        }

        if (availableCredit < cost) {
            int maxAffordablePulls = availableCredit / CasinoConfig.GACHA_COST;
            main.textPanel.addPara(Strings.format("gacha.insufficient_credit", availableCredit + " Stargems") + " insufficient for " + times + "x pull (" + cost + " Stargems).", Color.RED);
            if (maxAffordablePulls > 0) {
                main.textPanel.addPara(Strings.format("gacha.can_afford", maxAffordablePulls), Color.YELLOW);
            }
            main.textPanel.addPara(Strings.get("gacha.select_smaller"), Color.YELLOW);
            main.options.addOption(Strings.get("common.back"), OPTION_GACHA_MENU);
            return;
        }

        int overdraftAmount = cost - currentBalance;
        showOverdraftConfirm(times, cost, overdraftAmount);
    }

    private void showOverdraftConfirm(int times, int cost, int overdraftAmount) {
        main.options.clearOptions();

        if (!CasinoVIPManager.isOverdraftAvailable()) {
            showVIPPromotionForOverdraft(cost);
            return;
        }

        main.textPanel.addPara(Strings.get("gacha_overdraft.alert_title"), Color.ORANGE);
        main.textPanel.addPara(Strings.get("gacha_overdraft.insufficient_balance"), Color.YELLOW);
        main.textPanel.addPara(Strings.format("gacha_overdraft.current_balance", CasinoVIPManager.getBalance()), Color.GRAY);
        main.textPanel.addPara(Strings.format("gacha_overdraft.transaction_cost", cost), Color.GRAY);
        main.textPanel.addPara(Strings.format("gacha_overdraft.required_overdraft", overdraftAmount), Color.RED);
        main.textPanel.addPara("");
        main.textPanel.addPara(Strings.get("gacha_overdraft.credit_explanation"), Color.CYAN);
        main.textPanel.addPara(Strings.format("gacha_overdraft.interest_warning", CasinoConfig.VIP_DAILY_INTEREST_RATE * 100), Color.YELLOW);

        main.options.addOption(Strings.get("gacha_overdraft.authorize_btn"), PREFIX_CONFIRM_PULL + times);
        main.options.addOption(Strings.get("gacha_overdraft.what_is_ipc"), OPTION_EXPLAIN_IPC_CREDIT);
        main.options.addOption(Strings.get("common.cancel"), OPTION_GACHA_MENU);
    }

    private void showVIPPromotionForOverdraft(int cost) {
        main.textPanel.addPara(Strings.get("vip_promo.insufficient_title"), Color.RED);
        main.textPanel.addPara("");
        main.textPanel.addPara(Strings.get("vip_promo.insufficient_msg"), Color.YELLOW);
        main.textPanel.addPara(Strings.format("vip_promo.current_balance", CasinoVIPManager.getBalance()), Color.GRAY);
        main.textPanel.addPara(Strings.format("vip_promo.required", "", cost), Color.GRAY);
        main.textPanel.addPara("");
        main.textPanel.addPara(Strings.get("vip_promo.credit_facility_title"), Color.CYAN);
        main.textPanel.addPara(Strings.get("vip_promo.overdraft_vip_only"), Color.WHITE);
        main.textPanel.addPara("");
        main.textPanel.addPara(Strings.get("vip_promo.benefits_title"), Color.GREEN);
        main.textPanel.addPara(Strings.get("vip_promo.benefit_1"), Color.GRAY);
        main.textPanel.addPara(Strings.format("vip_promo.benefit_2", CasinoConfig.VIP_DAILY_REWARD), Color.GRAY);
        main.textPanel.addPara(Strings.format("vip_promo.benefit_3", CasinoConfig.VIP_DAILY_INTEREST_RATE * 100), Color.GRAY);
        main.textPanel.addPara(Strings.get("vip_promo.benefit_4"), Color.GRAY);
        main.textPanel.addPara("");
        main.textPanel.addPara(Strings.get("vip_promo.purchase_prompt"), Color.YELLOW);

        main.options.addOption(Strings.get("vip_promo.go_topup"), "topup_menu");
        main.options.addOption(Strings.get("common.cancel"), OPTION_GACHA_MENU);
    }

    private void showIPCCreditExplanation() {
        main.options.clearOptions();

        main.textPanel.addPara(Strings.get("gacha_ipc_credit.title"), Color.CYAN);
        main.textPanel.addPara("");
        main.textPanel.addPara(Strings.get("gacha_ipc_credit.description"), Color.WHITE);
        main.textPanel.addPara("");
        main.textPanel.addPara(Strings.get("gacha_ipc_credit.how_works"), Color.YELLOW);
        main.textPanel.addPara(Strings.get("gacha_ipc_credit.how_works_1"), Color.GRAY);
        main.textPanel.addPara(Strings.get("gacha_ipc_credit.how_works_2"), Color.GRAY);
        main.textPanel.addPara(Strings.get("gacha_ipc_credit.how_works_3"), Color.GRAY);
        main.textPanel.addPara("");
        main.textPanel.addPara(Strings.get("gacha_ipc_credit.interest_collections"), Color.YELLOW);
        main.textPanel.addPara(Strings.get("gacha_ipc_credit.interest_1"), Color.GRAY);
        main.textPanel.addPara(Strings.get("gacha_ipc_credit.interest_2"), Color.GRAY);
        main.textPanel.addPara(Strings.get("gacha_ipc_credit.interest_3"), Color.GRAY);
        main.textPanel.addPara("");
        main.textPanel.addPara(Strings.get("gacha_ipc_credit.increasing_ceiling"), Color.YELLOW);
        main.textPanel.addPara(Strings.get("gacha_ipc_credit.increase_1"), Color.GRAY);
        main.textPanel.addPara(Strings.get("gacha_ipc_credit.increase_2"), Color.GRAY);

        main.options.addOption(Strings.get("common.back"), OPTION_GACHA_MENU);
    }

    private void performGachaPull(int times) {
        if (justCompletedPull) {
            showGachaMenu();
            return;
        }

        int cost = times * CasinoConfig.GACHA_COST;
        int availableCredit = CasinoVIPManager.getAvailableCredit();

        if (availableCredit < cost) {
            main.textPanel.addPara(Strings.get("gacha.credit_denied"), Color.RED);
            main.textPanel.addPara(Strings.get("gacha.contact_financial"), Color.YELLOW);
            showGachaMenu();
            return;
        }

        CasinoVIPManager.addToBalance(-cost);

        CasinoGachaManager manager = new CasinoGachaManager();
        main.textPanel.addPara(Strings.get("gacha.initiating"), Color.CYAN);

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

        for (int i = 0; i < pullResults.size(); i++) {
            String result = pullResults.get(i);
            int rarity = getRarityFromResult(result);
            GachaAnimation.GachaItem item = new GachaAnimation.GachaItem(
                "item_" + System.currentTimeMillis() + "_" + i,
                result,
                rarity
            );

            if (i < obtainedShips.size() && obtainedShips.get(i) != null) {
                FleetMemberAPI ship = obtainedShips.get(i);
                item.setHullId(ship.getHullId());
            }

            itemsToAnimate.add(item);
        }

        this.shipsAwaitingConversionDecision.clear();
        this.shipsAwaitingConversionDecision.addAll(obtainedShips);

        GachaAnimation animation = new GachaAnimation(itemsToAnimate, results -> justCompletedPull = true);

        CasinoGachaManager poolManager = new CasinoGachaManager();
        List<FleetMemberAPI> poolShips = poolManager.getPotentialDrops();
        List<String> poolHullIds = new ArrayList<>();
        for (FleetMemberAPI member : poolShips) {
            if (member != null && member.getHullId() != null) {
                poolHullIds.add(member.getHullId());
            }
        }
        animation.setPoolHullIds(poolHullIds);

        GachaAnimationDialogDelegate delegate = createAnimationDialog(animation, this::showGachaMenu);

        main.getDialog().showCustomVisualDialog(1000f, 700f, delegate);
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
        Set<String> autoConvertHullIds = getAutoConvertHullIds();

        main.getDialog().showFleetMemberPickerDialog(
            Strings.get("gacha.select_convert"),
            Strings.get("gacha.convert"),
            Strings.get("common.cancel"),
            8,
            7,
            80,
            true,
            true,
            obtainedShips,
            new FleetMemberPickerListener() {
                public void pickedFleetMembers(List<FleetMemberAPI> selectedMembers) {
                    Set<String> convertHullIds = new HashSet<>();
                    if (selectedMembers != null) {
                        for (FleetMemberAPI member : selectedMembers) {
                            if (member != null && member.getHullId() != null) {
                                convertHullIds.add(member.getHullId());
                            }
                        }
                    }
                    convertHullIds.addAll(autoConvertHullIds);

                    int autoAppliedCount = 0;
                    for (FleetMemberAPI ship : obtainedShips) {
                        if (ship == null) continue;

                        if (convertHullIds.contains(ship.getHullId())) {
                            int val = (int)(ship.getHullSpec().getBaseValue() / CasinoConfig.SHIP_TRADE_RATE * CasinoConfig.SHIP_SELL_MULTIPLIER);
                            CasinoVIPManager.addToBalance(val);
                            String shipName = ship.getShipName() != null ? ship.getShipName() : Strings.get("gacha.unknown_ship");
                            main.textPanel.addPara(Strings.format("gacha.converted", shipName, val), Color.GREEN);
                            
                            if (autoConvertHullIds.contains(ship.getHullId()) && 
                                (selectedMembers == null || !selectedMembers.contains(ship))) {
                                autoAppliedCount++;
                            }
                        } else {
                            Global.getSector().getPlayerFleet().getFleetData().addFleetMember(ship);
                        }
                    }

                    if (autoAppliedCount > 0) {
                        main.textPanel.addPara(Strings.format("gacha.auto_convert_applied", autoAppliedCount), Color.CYAN);
                    }

                    showPostPullOptions();
                }

                public void cancelledFleetMemberPicking() {
                    Set<String> convertHullIds = new HashSet<>(autoConvertHullIds);
                    int autoAppliedCount = 0;

                    for (FleetMemberAPI ship : obtainedShips) {
                        if (ship == null) continue;

                        if (convertHullIds.contains(ship.getHullId())) {
                            int val = (int)(ship.getHullSpec().getBaseValue() / CasinoConfig.SHIP_TRADE_RATE * CasinoConfig.SHIP_SELL_MULTIPLIER);
                            CasinoVIPManager.addToBalance(val);
                            String shipName = ship.getShipName() != null ? ship.getShipName() : Strings.get("gacha.unknown_ship");
                            main.textPanel.addPara(Strings.format("gacha.converted", shipName, val), Color.GREEN);
                            autoAppliedCount++;
                        } else {
                            Global.getSector().getPlayerFleet().getFleetData().addFleetMember(ship);
                        }
                    }

                    if (autoAppliedCount > 0) {
                        main.textPanel.addPara(Strings.format("gacha.auto_convert_applied", autoAppliedCount), Color.CYAN);
                    }

                    showPostPullOptions();
                }
            });
    }

    private void showPostPullOptions() {
        main.options.clearOptions();
        main.options.addOption(Strings.get("gacha.pull_again"), OPTION_GACHA_MENU);
        main.options.addOption(Strings.get("gacha.back_to_main"), OPTION_BACK_MENU);
    }

    private void openAutoConvertPicker() {
        CasinoGachaManager manager = new CasinoGachaManager();
        List<FleetMemberAPI> potentialDrops = manager.getPotentialDrops();

        main.textPanel.addPara(Strings.format("gacha.current_pool", potentialDrops.size()), Color.CYAN);
        main.textPanel.addPara(Strings.get("gacha.auto_convert_desc"), Color.GRAY);

        main.getDialog().showFleetMemberPickerDialog(
            Strings.get("gacha.auto_convert_title"),
            Strings.get("gacha.auto_convert_save"),
            Strings.get("common.cancel"),
            8,
            7,
            80,
            true,
            true,
            potentialDrops,
            new FleetMemberPickerListener() {
                public void pickedFleetMembers(List<FleetMemberAPI> members) {
                    Set<String> hullIds = new HashSet<>();
                    if (members != null) {
                        for (FleetMemberAPI member : members) {
                            if (member != null && member.getHullId() != null) {
                                hullIds.add(member.getHullId());
                            }
                        }
                    }
                    saveAutoConvertHullIds(hullIds);
                    main.textPanel.addPara(Strings.format("gacha.auto_convert_saved", hullIds.size()), Color.GREEN);
                    showGachaMenu();
                }
                public void cancelledFleetMemberPicking() {
                    showGachaMenu();
                }
            });
    }

    @FunctionalInterface
    private interface OptionHandler {
        void handle(String option);
    }
}
