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
        handlers.put("cash_out_insist", option -> performCashOutInsist());
        handlers.put("cash_out_queue", option -> performCashOutQueue());
        handlers.put("cash_out_department", option -> performCashOutDepartment());
        handlers.put("cash_out_form", option -> performCashOutForm());
        handlers.put("cash_out_confirm", option -> performCashOutConfirm());
        handlers.put("cash_out_final", option -> performCashOutFinal());
        handlers.put("cash_out_return", option -> performCashOutReturn());
        handlers.put("cash_out_return_queue", option -> performCashOutReturnQueue());
        handlers.put("cash_out_return_department", option -> performCashOutReturnDepartment());
        handlers.put("cash_out_return_error", option -> performCashOutReturnDepartment());
        handlers.put("buy_vip", option -> showTopUpConfirm(-1, true));
        handlers.put("confirm_buy_vip", option -> purchaseVIPPass());
        handlers.put("buy_ship", option -> openShipTradePicker());
        handlers.put("back_menu", option -> main.showMenu());
        handlers.put("how_to_play_main", option -> main.help.showGeneralHelp());
        
        // Predicate-based handlers for pattern matching
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
        
        int cashoutStage = 0;
        if (Global.getSector().getMemoryWithoutUpdate().contains("$casino_cashout_stage")) {
            cashoutStage = Global.getSector().getMemoryWithoutUpdate().getInt("$casino_cashout_stage");
        }
        
        if (cashoutStage == 1) {
            main.getOptions().addOption("Check Cashout Status", "cash_out_return");
            main.getOptions().addOption("Back", "back_menu");
            main.setState(CasinoInteraction.State.FINANCIAL);
            return;
        }
        
        if (CasinoConfig.GEM_PACKAGES != null && !CasinoConfig.GEM_PACKAGES.isEmpty()) {
            for (int i=0; i<CasinoConfig.GEM_PACKAGES.size(); i++) {
                CasinoConfig.GemPackage pack = CasinoConfig.GEM_PACKAGES.get(i);
                main.getOptions().addOption(pack.gems + " Gems (" + pack.cost + " Credits)", "buy_pack_" + i);
            }
        } else {
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
        main.getOptions().clearOptions();
        main.textPanel.addPara("Tachy-Impact Financial Services", Color.GREEN);
        main.textPanel.addPara("We appreciate your business!", Color.YELLOW);
        main.textPanel.addPara("However, we strongly encourage you to spend all your Stargems on ship pulls.", Color.ORANGE);
        main.textPanel.addPara("Our collection of rare ships from across the galaxy is truly unmatched.", Color.CYAN);
        
        main.getOptions().addOption("I insist on cashing out", "cash_out_insist");
        main.getOptions().addOption("Cancel", "financial_menu");
        main.setState(CasinoInteraction.State.FINANCIAL);
    }
    
    private void performCashOutInsist() {
        main.getOptions().clearOptions();
        main.textPanel.addPara("Tachy-Impact Financial Services", Color.GREEN);
        main.textPanel.addPara("Very well. We can process your cashout request.", Color.YELLOW);
        main.textPanel.addPara("However, please note that this transaction requires multiple levels of approval.", Color.ORANGE);
        main.textPanel.addPara("Our banking system processes all requests in the order they are received.", Color.GRAY);
        
        main.getOptions().addOption("Proceed to queue", "cash_out_queue");
        main.getOptions().addOption("Cancel", "financial_menu");
        main.setState(CasinoInteraction.State.FINANCIAL);
    }
    
    private void performCashOutQueue() {
        main.getOptions().clearOptions();
        main.textPanel.addPara("Tachy-Impact Financial Services", Color.GREEN);
        main.textPanel.addPara("Welcome to the Cashout Queue Management System.", Color.YELLOW);
        main.textPanel.addPara("Your position in queue: 4,723", Color.RED);
        main.textPanel.addPara("Estimated wait time: 3-5 business days for queue processing.", Color.ORANGE);
        main.textPanel.addPara("Please select your preferred queue tier:", Color.GRAY);
        
        main.getOptions().addOption("Standard Queue (Free)", "cash_out_department");
        main.getOptions().addOption("Express Queue (500 Credits)", "cash_out_department");
        main.getOptions().addOption("VIP Queue (Requires VIP Status)", "cash_out_department");
        main.getOptions().addOption("Cancel", "financial_menu");
        main.setState(CasinoInteraction.State.FINANCIAL);
    }
    
    private void performCashOutDepartment() {
        main.getOptions().clearOptions();
        main.textPanel.addPara("Tachy-Impact Financial Services", Color.GREEN);
        main.textPanel.addPara("Department Selection", Color.YELLOW);
        main.textPanel.addPara("Please select the appropriate department for your cashout request:", Color.GRAY);
        
        main.getOptions().addOption("Department of Fund Disbursement", "cash_out_form");
        main.getOptions().addOption("Department of Asset Liquidation", "cash_out_form");
        main.getOptions().addOption("Department of Transaction Verification", "cash_out_form");
        main.getOptions().addOption("Department of Regulatory Compliance", "cash_out_form");
        main.getOptions().addOption("Cancel", "financial_menu");
        main.setState(CasinoInteraction.State.FINANCIAL);
    }
    
    private void performCashOutForm() {
        main.getOptions().clearOptions();
        main.textPanel.addPara("Tachy-Impact Financial Services", Color.GREEN);
        main.textPanel.addPara("Form Submission Required", Color.YELLOW);
        main.textPanel.addPara("Please complete the following forms:", Color.GRAY);
        main.textPanel.addPara("- Form 7B: Request for Fund Disbursement", Color.ORANGE);
        main.textPanel.addPara("- Form 12C: Asset Liquidation Authorization", Color.ORANGE);
        main.textPanel.addPara("- Form 24A: Tax Compliance Declaration", Color.ORANGE);
        main.textPanel.addPara("- Form 31D: Risk Assessment Questionnaire", Color.ORANGE);
        main.textPanel.addPara("- Form 45F: Anti-Money Laundering Certification", Color.ORANGE);
        
        main.getOptions().addOption("Submit all forms", "cash_out_confirm");
        main.getOptions().addOption("Cancel", "financial_menu");
        main.setState(CasinoInteraction.State.FINANCIAL);
    }
    
    private void performCashOutConfirm() {
        main.getOptions().clearOptions();
        main.textPanel.addPara("Tachy-Impact Financial Services", Color.GREEN);
        main.textPanel.addPara("Form Submission Successful", Color.YELLOW);
        main.textPanel.addPara("Your request has been submitted for review.", Color.ORANGE);
        main.textPanel.addPara("Processing time: 30 business days.", Color.RED);
        main.textPanel.addPara("Please note: You must return after 30 business days to complete this transaction.", Color.GRAY);
        main.textPanel.addPara("Failure to return within 60 business days will result in request cancellation.", Color.RED);
        
        main.getOptions().addOption("I understand. Return in 30 business days.", "cash_out_final");
        main.setState(CasinoInteraction.State.FINANCIAL);
    }
    
    private void performCashOutFinal() {
        main.getOptions().clearOptions();
        main.textPanel.addPara("Tachy-Impact Financial Services", Color.GREEN);
        main.textPanel.addPara("Request Received", Color.YELLOW);
        main.textPanel.addPara("Your cashout request has been logged in our system.", Color.ORANGE);
        main.textPanel.addPara("Request ID: " + System.currentTimeMillis(), Color.CYAN);
        main.textPanel.addPara("Please return in exactly 30 business days.", Color.RED);
        main.textPanel.addPara("Have a pleasant day.", Color.GRAY);
        
        main.getOptions().addOption("Leave", "leave");
        main.setState(CasinoInteraction.State.FINANCIAL);
        
        Global.getSector().getMemoryWithoutUpdate().set("$casino_cashout_stage", 1);
    }
    
    private void performCashOutReturn() {
        main.getOptions().clearOptions();
        main.textPanel.addPara("Tachy-Impact Financial Services", Color.GREEN);
        main.textPanel.addPara("Welcome back. We see you have a pending cashout request.", Color.YELLOW);
        main.textPanel.addPara("Please proceed to the queue to check on your request status.", Color.ORANGE);
        
        main.getOptions().addOption("Proceed to queue", "cash_out_return_queue");
        main.getOptions().addOption("Cancel", "financial_menu");
        main.setState(CasinoInteraction.State.FINANCIAL);
    }
    
    private void performCashOutReturnQueue() {
        main.getOptions().clearOptions();
        main.textPanel.addPara("Tachy-Impact Financial Services", Color.GREEN);
        main.textPanel.addPara("Queue Management System", Color.YELLOW);
        main.textPanel.addPara("Your position in queue: 8,947", Color.RED);
        main.textPanel.addPara("Estimated wait time: 2-4 business days.", Color.ORANGE);
        main.textPanel.addPara("Please select your department:", Color.GRAY);
        
        main.getOptions().addOption("Department of Request Processing", "cash_out_return_department");
        main.getOptions().addOption("Department of Status Verification", "cash_out_return_department");
        main.getOptions().addOption("Department of Final Approval", "cash_out_return_department");
        main.getOptions().addOption("Cancel", "financial_menu");
        main.setState(CasinoInteraction.State.FINANCIAL);
    }
    
    private void performCashOutReturnDepartment() {
        main.getOptions().clearOptions();
        main.textPanel.addPara("Tachy-Impact Financial Services", Color.GREEN);
        main.textPanel.addPara("Processing Request...", Color.YELLOW);
        main.textPanel.addPara("Accessing database...", Color.GRAY);
        main.textPanel.addPara("Verifying request ID...", Color.GRAY);
        main.textPanel.addPara("Checking compliance status...", Color.GRAY);
        main.textPanel.addPara("", Color.GRAY);
        main.textPanel.addPara("ERROR: SYSTEM MALFUNCTION DETECTED", Color.RED);
        main.textPanel.addPara("Error Code: 0x5F3A2B1C", Color.RED);
        main.textPanel.addPara("Our technical team has been notified.", Color.ORANGE);
        main.textPanel.addPara("Please restart the process from the beginning.", Color.GRAY);
        
        main.getOptions().addOption("Return to main menu", "financial_menu");
        main.setState(CasinoInteraction.State.FINANCIAL);
        
        Global.getSector().getMemoryWithoutUpdate().set("$casino_cashout_stage", 0);
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