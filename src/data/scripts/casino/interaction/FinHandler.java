package data.scripts.casino.interaction;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FleetMemberPickerListener;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import data.scripts.casino.CasinoConfig;
import data.scripts.casino.CasinoVIPManager;
import java.awt.Color;
import java.util.List;

/**
 * Manages Stargem top-ups, ship trades, and cashing out
 */
public class FinHandler {

    private final CasinoInteraction main;

    public FinHandler(CasinoInteraction main) {
        this.main = main;
    }

    /**
     * Handles financial menu options including top-ups, ship sales, and cashouts
     */
    public void handle(String option) {
        if ("financial_menu".equals(option) || "buy_chips".equals(option)) {
            showTopUpMenu();
        } else if ("cash_out".equals(option)) {
            performCashOut();
        } else if (option.startsWith("cash_out_")) {
            processCashOutOption(option);
        } else if (option.startsWith("buy_pack_")) {
            int index = Integer.parseInt(option.replace("buy_pack_", ""));
            showTopUpConfirm(index, false);
        } else if ("buy_vip".equals(option)) {
            showTopUpConfirm(-1, true);
        } else if (option.startsWith("confirm_buy_pack_")) {
            int index = Integer.parseInt(option.replace("confirm_buy_pack_", ""));
            purchaseGemPack(CasinoConfig.GEM_PACKAGES.get(index).gems, CasinoConfig.GEM_PACKAGES.get(index).cost);
        } else if ("confirm_buy_vip".equals(option)) {
            purchaseVIPPass();
        } else if ("buy_ship".equals(option)) {
            openShipTradePicker();
        } else if ("back_menu".equals(option)) {
            main.showMenu();
        } else if ("how_to_play_main".equals(option)) {
            main.help.showGeneralHelp();
        }
    }

    /**
     * Displays financial services menu with gem packages, ship sales, and VIP options
     */
    public void showTopUpMenu() {
        main.getOptions().clearOptions();
        main.getTextPanel().addParagraph("Financial Services Terminal", Color.GREEN);
        
        // Check if gem packages are available and not empty
        if (CasinoConfig.GEM_PACKAGES != null && !CasinoConfig.GEM_PACKAGES.isEmpty()) {
            for (int i=0; i<CasinoConfig.GEM_PACKAGES.size(); i++) {
                CasinoConfig.GemPackage pack = CasinoConfig.GEM_PACKAGES.get(i);
                main.getOptions().addOption(pack.gems + " Gems (" + pack.cost + " Credits)", "buy_pack_" + i);
            }
        } else {
            // If no gem packages are available, inform the player
            main.getTextPanel().addParagraph("No gem packages currently available.", Color.ORANGE);
        }
        
        main.getOptions().addOption("Sell Ships for Stargems", "buy_ship");
        main.getOptions().addOption("VIP Subscription Pass", "buy_vip");
        main.getOptions().addOption("Cash Out", "cash_out");
        main.getOptions().addOption("Back", "back_menu");
        main.setState(CasinoInteraction.State.FINANCIAL);
    }
    
    /**
     * Shows confirmation dialog for gem pack or VIP purchase
     */
    private void showTopUpConfirm(int index, boolean isVIP) {
        main.getOptions().clearOptions();
        if (isVIP) {
            main.getTextPanel().addParagraph("Purchase VIP Subscription (30 Days) for " + CasinoConfig.VIP_PASS_COST + " Credits?", Color.YELLOW);
            main.getOptions().addOption("Confirm Purchase", "confirm_buy_vip");
        } else {
            CasinoConfig.GemPackage pack = CasinoConfig.GEM_PACKAGES.get(index);
            main.getTextPanel().addParagraph("Purchase " + pack.gems + " Gems for " + pack.cost + " Credits?", Color.YELLOW);
            main.getOptions().addOption("Confirm Purchase", "confirm_buy_pack_" + index);
        }
        main.getOptions().addOption("Cancel", "financial_menu");
    }
    
    /**
     * Processes gem pack purchase transaction
     */
    private void purchaseGemPack(int gems, int cost) {
        if (Global.getSector().getPlayerFleet().getCargo().getCredits().get() < cost) {
            main.getTextPanel().addParagraph("Insufficient Credits!", Color.RED);
            showTopUpMenu();
            return;
        }
        Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(cost);
        CasinoVIPManager.addStargems(gems);
        main.getTextPanel().addParagraph("Purchased " + gems + " Stargems.", Color.GREEN);
        showTopUpMenu();
    }
    
    /**
     * Processes VIP subscription purchase
     */
    private void purchaseVIPPass() {
        int cost = CasinoConfig.VIP_PASS_COST;
        if (Global.getSector().getPlayerFleet().getCargo().getCredits().get() < cost) {
            main.getTextPanel().addParagraph("Insufficient Credits!", Color.RED);
            showTopUpMenu();
            return;
        }
        Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(cost);
        CasinoVIPManager.addSubscriptionDays(30);
        main.getTextPanel().addParagraph("VIP Status Activated! Enjoy your benefits.", Color.CYAN);
        showTopUpMenu();
    }

    /**
     * Shows cashout selection screen with fixed options
     */
    private void performCashOut() {
        int totalGems = CasinoVIPManager.getStargems();
        if (totalGems <= 0) {
            main.getTextPanel().addParagraph("No Stargems to cash out!", Color.RED);
            main.getOptions().clearOptions();
            main.getOptions().addOption("Back", "financial_menu");
            return;
        }
        
        main.getOptions().clearOptions();
        main.getTextPanel().addParagraph("Select amount of Stargems to cash out", Color.ORANGE);
        
        // Create fixed cashout options based on available gems
        if (totalGems >= 100) {
            int option1 = Math.min(totalGems, 100);
            main.getOptions().addOption(option1 + " Stargems", "cash_out_100");
        }
        if (totalGems >= 500) {
            int option2 = Math.min(totalGems, 500);
            main.getOptions().addOption(option2 + " Stargems", "cash_out_500");
        }
        if (totalGems >= 1000) {
            int option3 = Math.min(totalGems, 1000);
            main.getOptions().addOption(option3 + " Stargems", "cash_out_1000");
        }
        
        // Add option to cash out half of available gems
        int halfGems = totalGems / 2;
        if (halfGems > 0) {
            main.getOptions().addOption(halfGems + " Stargems (Half)", "cash_out_half");
        }
        
        // Add option to cash out all available gems
        main.getOptions().addOption("All " + totalGems + " Stargems", "cash_out_all");
        
        main.getOptions().addOption("Cancel", "financial_menu");
        main.setState(CasinoInteraction.State.FINANCIAL);
    }
    
    /**
     * Processes cash out transaction after confirmation
     */
    private void confirmCashOut(int gemsToCashOut) {
        int currentGems = CasinoVIPManager.getStargems();
        if (gemsToCashOut <= 0) {
            main.getTextPanel().addParagraph("No Stargems selected for cash out!", Color.RED);
            main.getOptions().clearOptions();
            main.getOptions().addOption("Back", "financial_menu");
            return;
        }
        
        if (gemsToCashOut > currentGems) {
            main.getTextPanel().addParagraph("Not enough Stargems! You only have " + currentGems + ".", Color.RED);
            main.getOptions().clearOptions();
            main.getOptions().addOption("Back", "financial_menu");
            return;
        }
        
        float credits = gemsToCashOut * CasinoConfig.STARGEM_EXCHANGE_RATE;
        Global.getSector().getPlayerFleet().getCargo().getCredits().add(credits);
        CasinoVIPManager.addStargems(-gemsToCashOut); // Subtract the cashed-out gems
        
        main.getTextPanel().addParagraph("Cashed out " + gemsToCashOut + " Stargems for " + (int)credits + " Credits.", Color.GREEN);
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
        } else if ("cash_out_1000".equals(option)) {
            gemsToCashOut = Math.min(totalGems, 1000);
        } else if ("cash_out_half".equals(option)) {
            gemsToCashOut = totalGems / 2;
        } else if ("cash_out_all".equals(option)) {
            gemsToCashOut = totalGems;
        }
        
        confirmCashOut(gemsToCashOut);
    }
    
    /**
     * Opens fleet member picker to sell ships for stargems
     */
    private void openShipTradePicker() {
        main.getDialog().showFleetMemberPickerDialog("Select ships to sell for Stargems:", "Trade for Gems", "Cancel", 10, 7, 88, true, true, Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy(), 
            new FleetMemberPickerListener() {
                public void pickedFleetMembers(List<FleetMemberAPI> members) {
                     if (members != null) {
                        for (FleetMemberAPI m : members) {
                            int val = (int)(m.getBaseValue() / CasinoConfig.SHIP_TRADE_RATE);
                            CasinoVIPManager.addStargems(val);
                            Global.getSector().getPlayerFleet().getFleetData().removeFleetMember(m);
                            main.getTextPanel().addParagraph("Traded " + m.getShipName() + " for " + val + " Stargems.", Color.GREEN);
                        }
                     }
                     showTopUpMenu();
                }
                public void cancelledFleetMemberPicking() { showTopUpMenu(); }
            });
    }
}