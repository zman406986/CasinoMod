package data.scripts.casino.interaction;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FleetMemberPickerListener;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import data.scripts.casino.CasinoConfig;
import data.scripts.casino.CasinoVIPManager;
import java.awt.Color;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

public class FinHandler {

    private final CasinoInteraction main;

    private final Map<String, OptionHandler> handlers = new HashMap<>();
    private final Map<Predicate<String>, OptionHandler> predicateHandlers = new HashMap<>();
    
    public FinHandler(CasinoInteraction main) {
        this.main = main;
        initializeHandlers();
    }
    
    private void initializeHandlers() {
        // Exact match handlers
        handlers.put("financial_menu", option -> showTopUpMenu());
        handlers.put("buy_chips", option -> showTopUpMenu());
        handlers.put("cash_out", option -> performCashOut());
        handlers.put("buy_vip", option -> showTopUpConfirm(-1, true));
        handlers.put("confirm_buy_vip", option -> purchaseVIPPass());
        handlers.put("buy_ship", option -> openShipTradePicker());
        handlers.put("back_menu", option -> main.showMenu());
        handlers.put("how_to_play_main", option -> main.help.showGeneralHelp());
        
        // Predicate-based handlers for pattern matching
        predicateHandlers.put(option -> option.startsWith("cash_out_"), option -> processCashOutOption(option));
        predicateHandlers.put(option -> option.startsWith("buy_pack_"), option -> {
            int index = Integer.parseInt(option.replace("buy_pack_", ""));
            showTopUpConfirm(index, false);
        });
        predicateHandlers.put(option -> option.startsWith("confirm_buy_pack_"), option -> {
            int index = Integer.parseInt(option.replace("confirm_buy_pack_", ""));
            purchaseGemPack(CasinoConfig.GEM_PACKAGES.get(index).gems, CasinoConfig.GEM_PACKAGES.get(index).cost);
        });
    }

    public void handle(String option) {
        OptionHandler handler = handlers.get(option);
        if (handler != null) {
            handler.handle(option);
            return;
        }
        
        // Check predicate-based handlers
        for (Map.Entry<Predicate<String>, OptionHandler> entry : predicateHandlers.entrySet()) {
            if (entry.getKey().test(option)) {
                entry.getValue().handle(option);
                return;
            }
        }
    }

    public void showTopUpMenu() {
        main.getOptions().clearOptions();
        main.getTextPanel().addPara("Financial Services Terminal", Color.GREEN);
        
        // Check if gem packages are available and not empty
        if (CasinoConfig.GEM_PACKAGES != null && !CasinoConfig.GEM_PACKAGES.isEmpty()) {
            for (int i=0; i<CasinoConfig.GEM_PACKAGES.size(); i++) {
                CasinoConfig.GemPackage pack = CasinoConfig.GEM_PACKAGES.get(i);
                main.getOptions().addOption(pack.gems + " Gems (" + pack.cost + " Credits)", "buy_pack_" + i);
            }
        } else {
            // If no gem packages are available, inform the player
            main.getTextPanel().addPara("No gem packages currently available.", Color.ORANGE);
        }
        
        main.getOptions().addOption("Sell Ships for Stargems", "buy_ship");
        main.getOptions().addOption("VIP Subscription Pass", "buy_vip");
        main.getOptions().addOption("Cash Out", "cash_out");
        main.getOptions().addOption("Back", "back_menu");
        main.setState(CasinoInteraction.State.FINANCIAL);
    }
    
    private void showTopUpConfirm(int index, boolean isVIP) {
        main.getOptions().clearOptions();
        if (isVIP) {
            int currentDays = CasinoVIPManager.getDaysRemaining();
            String message = "Purchase VIP Subscription (30 Days) for " + CasinoConfig.VIP_PASS_COST + " Credits?";
            if (currentDays > 0) {
                message += " (Current VIP: " + currentDays + " days remaining)";
            }
            main.textPanel.addPara(message, Color.YELLOW);
            main.getOptions().addOption("Confirm Purchase", "confirm_buy_vip");
        } else {
            CasinoConfig.GemPackage pack = CasinoConfig.GEM_PACKAGES.get(index);
            main.textPanel.addPara("Purchase " + pack.gems + " Gems for " + pack.cost + " Credits?", Color.YELLOW);
            main.getOptions().addOption("Confirm Purchase", "confirm_buy_pack_" + index);
        }
        main.getOptions().addOption("Cancel", "financial_menu");
    }
    
    private void purchaseGemPack(int gems, int cost) {
        if (Global.getSector().getPlayerFleet().getCargo().getCredits().get() < cost) {
            main.textPanel.addPara("Insufficient Credits!", Color.RED);
            showTopUpMenu();
            return;
        }
        Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(cost);
        CasinoVIPManager.addStargems(gems);
        main.textPanel.addPara("Purchased " + gems + " Stargems.", Color.GREEN);
        showTopUpMenu();
    }
    
    private void purchaseVIPPass() {
        int cost = CasinoConfig.VIP_PASS_COST;
        if (Global.getSector().getPlayerFleet().getCargo().getCredits().get() < cost) {
            main.textPanel.addPara("Insufficient Credits!", Color.RED);
            showTopUpMenu();
            return;
        }
        Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(cost);
        CasinoVIPManager.addSubscriptionDays(30);
        main.textPanel.addPara("VIP Status Activated! Enjoy your benefits.", Color.CYAN);
        showTopUpMenu();
    }

    private void performCashOut() {
        int totalGems = CasinoVIPManager.getStargems();
        if (totalGems <= 0) {
            main.textPanel.addPara("No Stargems to cash out!", Color.RED);
            main.getOptions().clearOptions();
            main.getOptions().addOption("Back", "financial_menu");
            return;
        }
        
        main.getOptions().clearOptions();
        main.textPanel.addPara("Select amount of Stargems to cash out", Color.ORANGE);
        
        // Create fixed cashout options based on available gems - following same pattern as betting menus
        if (totalGems >= 100) {
            int option1 = Math.min(totalGems, 100);
            main.getOptions().addOption(option1 + " Stargems", "cash_out_100");
        }
        if (totalGems >= 500) {
            int option2 = Math.min(totalGems, 500);
            main.getOptions().addOption(option2 + " Stargems", "cash_out_500");
        }
        if (totalGems >= 2000) {  // Changed from 1000 to 2000 to match betting pattern
            int option3 = Math.min(totalGems, 2000);
            main.getOptions().addOption(option3 + " Stargems", "cash_out_2000");
        }
        
        // Add option to cash out percentage of available gems based on available credit (remaining debt capacity) if in debt
        int availableCredit = CasinoVIPManager.getAvailableCredit();
        if (availableCredit > 0) {
            // Player has available credit, show percentage options based on that
            int tenPercent = (availableCredit * 10) / 100;
            if (tenPercent > 0) {
                main.getOptions().addOption(tenPercent + " Stargems (10% of remaining credit)", "cash_out_ten_percent");
            }
        } else {
            // Player has no available credit, show percentage options based on current balance
            int tenPercent = (totalGems * 10) / 100;
            if (tenPercent > 0) {
                main.getOptions().addOption(tenPercent + " Stargems (10% of account)", "cash_out_ten_percent");
            }
        }
        
        // Add option to cash out all available gems
        main.getOptions().addOption("All " + totalGems + " Stargems", "cash_out_all");
        
        main.getOptions().addOption("Cancel", "financial_menu");
        main.setState(CasinoInteraction.State.FINANCIAL);
    }
    
    private void confirmCashOut(int gemsToCashOut) {
        int currentGems = CasinoVIPManager.getStargems();
        if (gemsToCashOut <= 0) {
            main.textPanel.addPara("No Stargems selected for cash out!", Color.RED);
            main.getOptions().clearOptions();
            main.getOptions().addOption("Back", "financial_menu");
            return;
        }
        
        if (gemsToCashOut > currentGems) {
            main.textPanel.addPara("Not enough Stargems! You only have " + currentGems + ".", Color.RED);
            main.getOptions().clearOptions();
            main.getOptions().addOption("Back", "financial_menu");
            return;
        }
        
        float credits = gemsToCashOut * CasinoConfig.STARGEM_EXCHANGE_RATE;
        Global.getSector().getPlayerFleet().getCargo().getCredits().add(credits);
        CasinoVIPManager.addStargems(-gemsToCashOut); // Subtract the cashed-out gems
        
        main.textPanel.addPara("Cashed out " + gemsToCashOut + " Stargems for " + (int)credits + " Credits.", Color.GREEN);
        main.getOptions().clearOptions();
        main.getOptions().addOption("Back", "financial_menu");
    }
    
    /**
     * Process the selected cashout option
     */
    private void processCashOutOption(String option) {
        int totalGems = CasinoVIPManager.getStargems();
        int gemsToCashOut = 0;
        
        if ("cash_out_100".equals(option)) {
            gemsToCashOut = Math.min(totalGems, 100);
        } else if ("cash_out_500".equals(option)) {
            gemsToCashOut = Math.min(totalGems, 500);
        } else if ("cash_out_2000".equals(option)) {
            gemsToCashOut = Math.min(totalGems, 2000);
        } else if ("cash_out_ten_percent".equals(option)) {
            // Use available credit if in debt, otherwise use current gems
            int availableCredit = CasinoVIPManager.getAvailableCredit();
            if (availableCredit > 0) {
                gemsToCashOut = (availableCredit * 10) / 100;
            } else {
                gemsToCashOut = (totalGems * 10) / 100;
            }
        } else if ("cash_out_all".equals(option)) {
            gemsToCashOut = totalGems;
        }
        
        confirmCashOut(gemsToCashOut);
    }
    
    private void openShipTradePicker() {
        main.getDialog().showFleetMemberPickerDialog("Select ships to sell for Stargems:", "Trade for Gems", "Cancel", 10, 7, 88, true, true, Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy(), 
            new FleetMemberPickerListener() {
                public void pickedFleetMembers(List<FleetMemberAPI> members) {
                     if (members != null) {
                        for (FleetMemberAPI m : members) {
                            int val = (int)(m.getBaseValue() / CasinoConfig.STARGEM_EXCHANGE_RATE);
                            CasinoVIPManager.addStargems(val);
                            Global.getSector().getPlayerFleet().getFleetData().removeFleetMember(m);
                            main.getTextPanel().addPara("Traded " + m.getShipName() + " for " + val + " Stargems.", Color.GREEN);
                        }
                     }
                     showTopUpMenu();
                }
                public void cancelledFleetMemberPicking() { showTopUpMenu(); }
            });
    }
}