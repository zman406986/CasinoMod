package data.scripts.casino.interaction;

import com.fs.starfarer.api.Global;
import data.scripts.casino.CasinoConfig;
import data.scripts.casino.CasinoVIPManager;
import data.scripts.casino.Strings;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Handler for Stargem top-up: purchasing gem packages with credits.
 * Package sizes and costs defined in CasinoConfig.GEM_PACKAGES.
 */
public class TopupHandler {

    private final CasinoInteraction main;
    private final Map<String, OptionHandler> handlers = new HashMap<>();
    private final Map<Predicate<String>, OptionHandler> predicateHandlers = new HashMap<>();

    public TopupHandler(CasinoInteraction main) {
        this.main = main;
        initializeHandlers();
    }

    private void initializeHandlers() {
        handlers.put("topup_menu", option -> showTopUpMenu());
        handlers.put("back_menu", option -> main.showMenu());
        handlers.put("how_to_topup", option -> main.help.showTopupHelp());
        handlers.put("buy_vip_from_topup", option -> showVIPConfirmFromTopup());
        handlers.put("confirm_buy_vip_topup", option -> purchaseVIPPassFromTopup());

        predicateHandlers.put(option -> option.startsWith("topup_pack_"), option -> {
            int index = Integer.parseInt(option.replace("topup_pack_", ""));
            showTopUpConfirm(index);
        });
        predicateHandlers.put(option -> option.startsWith("confirm_topup_pack_"), option -> {
            int index = Integer.parseInt(option.replace("confirm_topup_pack_", ""));
            purchaseGemPack(CasinoConfig.GEM_PACKAGES.get(index).gems(), CasinoConfig.GEM_PACKAGES.get(index).cost());
        });
    }

    public void handle(String option) {
        OptionHandler handler = handlers.get(option);
        if (handler != null) {
            handler.handle(option);
            return;
        }

        for (Map.Entry<Predicate<String>, OptionHandler> entry : predicateHandlers.entrySet()) {
            if (entry.getKey().test(option)) {
                entry.getValue().handle(option);
                return;
            }
        }
    }

    public void showTopUpMenu() {
        main.getOptions().clearOptions();
        main.getTextPanel().addPara(Strings.get("topup.title"), Color.GREEN);
        main.getTextPanel().addPara(Strings.get("topup.description"), Color.GRAY);
        main.getTextPanel().addPara("");

        int currentBalance = CasinoVIPManager.getBalance();
        int daysRemaining = CasinoVIPManager.getDaysRemaining();
        Color balanceColor = currentBalance >= 0 ? Color.GREEN : Color.RED;
        main.getTextPanel().addPara(Strings.format("topup.current_balance", currentBalance, daysRemaining), balanceColor);
        main.getTextPanel().addPara("");

        if (!CasinoConfig.GEM_PACKAGES.isEmpty()) {
            for (int i = 0; i < CasinoConfig.GEM_PACKAGES.size(); i++) {
                CasinoConfig.GemPackage pack = CasinoConfig.GEM_PACKAGES.get(i);
                main.getOptions().addOption(Strings.format("topup.package_btn", pack.gems(), pack.cost()), "topup_pack_" + i);
            }
        } else {
            main.getTextPanel().addPara(Strings.get("topup.no_packages"), Color.ORANGE);
        }

        main.getOptions().addOption(Strings.get("topup.purchase_vip"), "buy_vip_from_topup");
        main.getOptions().addOption(Strings.get("topup.about_stargems"), "how_to_topup");
        main.getOptions().addOption(Strings.get("common.back"), "back_menu");
        main.setState(CasinoInteraction.State.TOPUP);
    }

    private void showTopUpConfirm(int index) {
        main.getOptions().clearOptions();
        CasinoConfig.GemPackage pack = CasinoConfig.GEM_PACKAGES.get(index);
        main.getTextPanel().addPara(Strings.format("topup.confirm_purchase", pack.gems(), pack.cost()), Color.YELLOW);
        main.getOptions().addOption(Strings.get("topup.confirm_btn"), "confirm_topup_pack_" + index);
        main.getOptions().addOption(Strings.get("common.cancel"), "topup_menu");
    }

    private void purchaseGemPack(int gems, int cost) {
        if (Global.getSector().getPlayerFleet().getCargo().getCredits().get() < cost) {
            main.getTextPanel().addPara(Strings.get("topup.insufficient_credits"), Color.RED);
            showTopUpMenu();
            return;
        }
        Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(cost);
        CasinoVIPManager.addToBalance(gems);
        main.getTextPanel().addPara(Strings.format("topup.purchased", gems), Color.GREEN);
        showTopUpMenu();
    }

    private void showVIPConfirmFromTopup() {
        main.getOptions().clearOptions();
        int currentDays = CasinoVIPManager.getDaysRemaining();
        String message = Strings.format("financial_vip.confirm_purchase", CasinoConfig.VIP_PASS_DAYS, CasinoConfig.VIP_PASS_COST);
        if (currentDays > 0) {
            message += " " + Strings.format("financial_vip.current_vip", currentDays);
        }
        main.getTextPanel().addPara(message, Color.YELLOW);
        main.getOptions().addOption(Strings.get("financial_vip.confirm_purchase_btn"), "confirm_buy_vip_topup");
        main.getOptions().addOption(Strings.get("common.cancel"), "topup_menu");
    }

    private void purchaseVIPPassFromTopup() {
        int cost = CasinoConfig.VIP_PASS_COST;
        if (Global.getSector().getPlayerFleet().getCargo().getCredits().get() < cost) {
            main.getTextPanel().addPara(Strings.get("financial_vip.insufficient_credits"), Color.RED);
            showTopUpMenu();
            return;
        }
        
        Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(cost);
        CasinoVIPManager.addSubscriptionDays(CasinoConfig.VIP_PASS_DAYS);
        
        int daysAfter = CasinoVIPManager.getDaysRemaining();
        main.getTextPanel().addPara(Strings.format("financial_vip.activated", daysAfter), Color.CYAN);
        showTopUpMenu();
    }
}
