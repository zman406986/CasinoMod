package data.scripts.casino.interaction;

import com.fs.starfarer.api.Global;
import data.scripts.casino.CasinoConfig;
import data.scripts.casino.CasinoVIPManager;
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
            purchaseGemPack(CasinoConfig.GEM_PACKAGES.get(index).gems, CasinoConfig.GEM_PACKAGES.get(index).cost);
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
        main.getTextPanel().addPara("Stargem Top-up", Color.GREEN);
        main.getTextPanel().addPara("Purchase Stargems with credits. Larger packages offer better value!", Color.GRAY);
        main.getTextPanel().addPara("");

        int currentBalance = CasinoVIPManager.getBalance();
        int daysRemaining = CasinoVIPManager.getDaysRemaining();
        Color balanceColor = currentBalance >= 0 ? Color.GREEN : Color.RED;
        main.getTextPanel().addPara("Current Balance: " + currentBalance + " Stargems (VIP: " + daysRemaining + " days)", balanceColor);
        main.getTextPanel().addPara("");

        if (CasinoConfig.GEM_PACKAGES != null && !CasinoConfig.GEM_PACKAGES.isEmpty()) {
            for (int i = 0; i < CasinoConfig.GEM_PACKAGES.size(); i++) {
                CasinoConfig.GemPackage pack = CasinoConfig.GEM_PACKAGES.get(i);
                main.getOptions().addOption(pack.gems + " Gems (" + pack.cost + " Credits)", "topup_pack_" + i);
            }
        } else {
            main.getTextPanel().addPara("No gem packages currently available.", Color.ORANGE);
        }

        main.getOptions().addOption("Purchase VIP Pass (can stack)", "buy_vip_from_topup");
        main.getOptions().addOption("About Stargems", "how_to_topup");
        main.getOptions().addOption("Back", "back_menu");
        main.setState(CasinoInteraction.State.TOPUP);
    }

    private void showTopUpConfirm(int index) {
        main.getOptions().clearOptions();
        CasinoConfig.GemPackage pack = CasinoConfig.GEM_PACKAGES.get(index);
        main.getTextPanel().addPara("Purchase " + pack.gems + " Gems for " + pack.cost + " Credits?", Color.YELLOW);
        main.getOptions().addOption("Confirm Purchase", "confirm_topup_pack_" + index);
        main.getOptions().addOption("Cancel", "topup_menu");
    }

    private void purchaseGemPack(int gems, int cost) {
        if (Global.getSector().getPlayerFleet().getCargo().getCredits().get() < cost) {
            main.getTextPanel().addPara("Insufficient Credits!", Color.RED);
            showTopUpMenu();
            return;
        }
        Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(cost);
        CasinoVIPManager.addToBalance(gems);
        main.getTextPanel().addPara("Purchased " + gems + " Stargems.", Color.GREEN);
        showTopUpMenu();
    }

    private void showVIPConfirmFromTopup() {
        main.getOptions().clearOptions();
        int currentDays = CasinoVIPManager.getDaysRemaining();
        String message = "Purchase VIP Subscription (" + CasinoConfig.VIP_PASS_DAYS + " Days) for " + CasinoConfig.VIP_PASS_COST + " Credits?";
        if (currentDays > 0) {
            message += " (Current VIP: " + currentDays + " days remaining)";
        }
        main.getTextPanel().addPara(message, Color.YELLOW);
        main.getOptions().addOption("Confirm Purchase", "confirm_buy_vip_topup");
        main.getOptions().addOption("Cancel", "topup_menu");
    }

    private void purchaseVIPPassFromTopup() {
        int cost = CasinoConfig.VIP_PASS_COST;
        if (Global.getSector().getPlayerFleet().getCargo().getCredits().get() < cost) {
            main.getTextPanel().addPara("Insufficient Credits!", Color.RED);
            showTopUpMenu();
            return;
        }
        
        Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(cost);
        CasinoVIPManager.addSubscriptionDays(CasinoConfig.VIP_PASS_DAYS);
        
        int daysAfter = CasinoVIPManager.getDaysRemaining();
        main.getTextPanel().addPara("VIP Status Activated! " + daysAfter + " days remaining.", Color.CYAN);
        showTopUpMenu();
    }
}
