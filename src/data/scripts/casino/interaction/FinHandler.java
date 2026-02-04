package data.scripts.casino.interaction;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FleetMemberPickerListener;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import data.scripts.casino.CasinoConfig;
import data.scripts.casino.CasinoVIPManager;
import java.awt.Color;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

public class FinHandler {

    private final CasinoInteraction main;
    private final Random random = new Random();

    private final Map<String, OptionHandler> handlers = new HashMap<>();
    private final Map<Predicate<String>, OptionHandler> predicateHandlers = new HashMap<>();

    // CAPTCHA question class
    private static class CaptchaQuestion {
        final String question;
        final String[] options;
        final int correctIndex;

        CaptchaQuestion(String question, String[] options, int correctIndex) {
            this.question = question;
            this.options = options;
            this.correctIndex = correctIndex;
        }
    }

    // Pool of CAPTCHA questions
    private final List<CaptchaQuestion> captchaQuestions = new ArrayList<>();
    private int currentCaptchaIndex = -1;
    private int holdWaitCount = 0;

    public FinHandler(CasinoInteraction main) {
        this.main = main;
        initializeCaptchaQuestions();
        initializeHandlers();
    }

    private void initializeCaptchaQuestions() {
        captchaQuestions.add(new CaptchaQuestion(
            "Security Verification: What is 15 + 27?",
            new String[]{"42", "52", "412"},
            1
        ));
        captchaQuestions.add(new CaptchaQuestion(
            "Security Verification: What is (7 × 8) + 3?",
            new String[]{"59", "77", "83"},
            0
        ));
        captchaQuestions.add(new CaptchaQuestion(
            "Security Verification: What is the square root of 144?",
            new String[]{"12", "14", "24"},
            0
        ));
        captchaQuestions.add(new CaptchaQuestion(
            "Security Verification: What is 100 - 37?",
            new String[]{"53", "63", "73"},
            1
        ));
        captchaQuestions.add(new CaptchaQuestion(
            "Security Verification: What is 9 × 7?",
            new String[]{"56", "63", "72"},
            1
        ));
        captchaQuestions.add(new CaptchaQuestion(
            "Security Verification: Solve for X: X + 23 = 50",
            new String[]{"27", "33", "37"},
            0
        ));
        captchaQuestions.add(new CaptchaQuestion(
            "Security Verification: What is 144 ÷ 12?",
            new String[]{"10", "11", "12"},
            2
        ));
        captchaQuestions.add(new CaptchaQuestion(
            "Security Verification: What is 8² + 4?",
            new String[]{"64", "68", "72"},
            1
        ));
    }
    
    private void initializeHandlers() {
        // Exact match handlers
        handlers.put("financial_menu", option -> showTopUpMenu());
        handlers.put("buy_chips", option -> showTopUpMenu());

        // New ISP/Tech Support themed cashout flow
        handlers.put("cash_out", option -> performCashOut());
        handlers.put("cash_out_phone", option -> performCashOutPhone());
        handlers.put("cash_out_phone_billing", option -> performCashOutPhoneDeadEnd("billing"));
        handlers.put("cash_out_phone_sales", option -> performCashOutPhoneDeadEnd("sales"));
        handlers.put("cash_out_phone_support", option -> performCashOutHold());
        handlers.put("cash_out_hold", option -> performCashOutHold());
        handlers.put("cash_out_captcha", option -> performCashOutCaptcha());
        handlers.put("cash_out_tier1", option -> performCashOutTier1());
        handlers.put("cash_out_escalate", option -> performCashOutEscalate());
        handlers.put("cash_out_escalate_no", option -> performCashOutEscalate());
        handlers.put("cash_out_escalate_ships", option -> performCashOutEscalate());
        handlers.put("cash_out_final", option -> performCashOutFinal());
        handlers.put("cash_out_return", option -> performCashOutReturn());
        handlers.put("cash_out_return_hold", option -> performCashOutReturnHold());

        // CAPTCHA answer handlers (will be generated dynamically)
        handlers.put("captcha_wrong_1", option -> performCashOutCaptchaWrong(1));
        handlers.put("captcha_wrong_2", option -> performCashOutCaptchaWrong(2));

        handlers.put("buy_vip", option -> showTopUpConfirm(-1, true));
        handlers.put("confirm_buy_vip", option -> purchaseVIPPass());
        handlers.put("buy_ship", option -> openShipTradePicker());
        handlers.put("toggle_vip_notifications", option -> toggleVIPNotifications());
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
        predicateHandlers.put(option -> option.startsWith("captcha_answer_"), option -> {
            int answerIndex = Integer.parseInt(option.replace("captcha_answer_", ""));
            checkCaptchaAnswer(answerIndex);
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
        
        // Display financial status
        displayFinancialInfo();
        
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
        
        // Add notification toggle option
        boolean monthlyMode = CasinoVIPManager.isMonthlyNotificationMode();
        String notifyText = monthlyMode ? "VIP Notifications: Monthly" : "VIP Notifications: Daily";
        main.getOptions().addOption(notifyText, "toggle_vip_notifications");
        
        main.getOptions().addOption("Back", "back_menu");
        main.setState(CasinoInteraction.State.FINANCIAL);
    }
    
    private void displayFinancialInfo() {
        int currentBalance = CasinoVIPManager.getBalance();
        int creditCeiling = CasinoVIPManager.getCreditCeiling();
        int daysRemaining = CasinoVIPManager.getDaysRemaining();
        
        main.getTextPanel().addPara("--- FINANCIAL STATUS ---", Color.CYAN);
        
        // Show balance with color coding
        Color balanceColor = currentBalance >= 0 ? Color.GREEN : Color.RED;
        main.getTextPanel().addPara("Balance: " + currentBalance + " Stargems", balanceColor);
        
        main.getTextPanel().addPara("Credit Ceiling: " + creditCeiling, Color.GRAY);
        
        if (daysRemaining > 0) {
            main.getTextPanel().addPara("VIP Status: " + daysRemaining + " days remaining", Color.CYAN);
        } else {
            main.getTextPanel().addPara("VIP Status: Inactive", Color.GRAY);
        }
        
        // Show interest rate info if in debt
        if (currentBalance < 0) {
            boolean hasVIP = daysRemaining > 0;
            float interestRate = hasVIP ? CasinoConfig.VIP_DAILY_INTEREST_RATE : CasinoConfig.NON_VIP_DAILY_INTEREST_RATE;
            main.getTextPanel().addPara("Daily Interest: " + (int)(interestRate * 100) + "%", Color.YELLOW);
        }
        
        main.getTextPanel().addPara("------------------------", Color.CYAN);
    }
    
    private void toggleVIPNotifications() {
        boolean newMode = CasinoVIPManager.toggleMonthlyNotificationMode();
        if (newMode) {
            main.textPanel.addPara("VIP notifications set to monthly.", Color.CYAN);
        } else {
            main.textPanel.addPara("VIP notifications set to daily.", Color.CYAN);
        }
        showTopUpMenu();
    }
    
    private void showTopUpConfirm(int index, boolean isVIP) {
        main.getOptions().clearOptions();
        if (isVIP) {
            int currentDays = CasinoVIPManager.getDaysRemaining();
            String message = "Purchase VIP Subscription (" + CasinoConfig.VIP_PASS_DAYS + " Days) for " + CasinoConfig.VIP_PASS_COST + " Credits?";
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
        CasinoVIPManager.addToBalance(gems);
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
        CasinoVIPManager.addSubscriptionDays(CasinoConfig.VIP_PASS_DAYS);
        main.textPanel.addPara("VIP Status Activated! Enjoy your benefits.", Color.CYAN);
        showTopUpMenu();
    }

    // ==================== NEW ISP/TECH SUPPORT CASHOUT FLOW ====================

    private void performCashOut() {
        main.getOptions().clearOptions();
        main.textPanel.addPara("Tachy-Impact Customer Support", Color.GREEN);
        main.textPanel.addPara("Thank you for calling Tachy-Impact Customer Support!", Color.YELLOW);
        main.textPanel.addPara("Your credits are important to us. Please listen carefully as our menu options have changed.", Color.GRAY);
        main.textPanel.addPara("", Color.GRAY);
        main.textPanel.addPara("Did you know? You can spend your Stargems on rare ships right now! No waiting required!", Color.CYAN);

        main.getOptions().addOption("Continue to Support Menu", "cash_out_phone");
        main.getOptions().addOption("Hang Up (Cancel)", "financial_menu");
        main.setState(CasinoInteraction.State.FINANCIAL);
    }

    private void performCashOutPhone() {
        main.getOptions().clearOptions();
        main.textPanel.addPara("Tachy-Impact Customer Support", Color.GREEN);
        main.textPanel.addPara("Automated Support System", Color.YELLOW);
        main.textPanel.addPara("For billing inquiries, press 1.", Color.GRAY);
        main.textPanel.addPara("For sales and new accounts, press 2.", Color.GRAY);
        main.textPanel.addPara("For technical support, press 3.", Color.GRAY);
        main.textPanel.addPara("To speak with a representative, press 0.", Color.GRAY);
        main.textPanel.addPara("", Color.GRAY);
        main.textPanel.addPara("*Note: Option 0 is currently unavailable in your sector.*", Color.RED);

        main.getOptions().addOption("Press 1 - Billing", "cash_out_phone_billing");
        main.getOptions().addOption("Press 2 - Sales", "cash_out_phone_sales");
        main.getOptions().addOption("Press 3 - Technical Support", "cash_out_phone_support");
        main.getOptions().addOption("Hang Up", "financial_menu");
        main.setState(CasinoInteraction.State.FINANCIAL);
    }

    private void performCashOutPhoneDeadEnd(String department) {
        main.getOptions().clearOptions();
        main.textPanel.addPara("Tachy-Impact Customer Support", Color.GREEN);

        if (department.equals("billing")) {
            main.textPanel.addPara("Billing Department", Color.YELLOW);
            main.textPanel.addPara("We're sorry, but our billing system is currently experiencing high call volumes.", Color.ORANGE);
            main.textPanel.addPara("Please try again later or visit our website at www.tachy-impact.nope", Color.GRAY);
        } else {
            main.textPanel.addPara("Sales Department", Color.YELLOW);
            main.textPanel.addPara("Great news! We have a special offer on Stargem packages today!", Color.CYAN);
            main.textPanel.addPara("Unfortunately, we cannot process cashouts through this line.", Color.ORANGE);
        }

        main.textPanel.addPara("", Color.GRAY);
        main.textPanel.addPara("Returning to main menu...", Color.GRAY);

        main.getOptions().addOption("Return to Phone Menu", "cash_out_phone");
        main.getOptions().addOption("Hang Up", "financial_menu");
        main.setState(CasinoInteraction.State.FINANCIAL);
    }

    private void performCashOutHold() {
        main.getOptions().clearOptions();
        holdWaitCount++;

        main.textPanel.addPara("Tachy-Impact Customer Support", Color.GREEN);
        main.textPanel.addPara("Please hold for the next available representative...", Color.YELLOW);
        main.textPanel.addPara("", Color.GRAY);

        // Escalating wait times
        String[] waitMessages = {
            "Your estimated wait time is: 4 minutes",
            "Your estimated wait time is: 12 minutes",
            "Your estimated wait time is: 47 minutes",
            "Your estimated wait time is: 2 hours",
            "Your estimated wait time is: UNAVAILABLE"
        };

        String[] holdMusic = {
            "*Elevator music plays softly in the background...*",
            "*A synth version of 'Fractured Spacetime' plays on loop...*",
            "*Static crackles occasionally interrupt the silence...*",
            "*Someone is humming off-key in the distance...*",
            "*The hold music skips and repeats the same 3-second clip...*"
        };

        String[] funFacts = {
            "Did you know? Tachy-Impact processes over 50 transactions per day!",
            "Fun fact: Our VIP members enjoy 5% better hold music quality!",
            "Reminder: Spending Stargems is faster than cashing out!",
            "Tip of the day: Premium ships appreciate in value!",
            "Did you know? 9 out of 10 customers give up before reaching a representative!"
        };

        int index = Math.min(holdWaitCount - 1, waitMessages.length - 1);
        main.textPanel.addPara(waitMessages[index], Color.ORANGE);
        main.textPanel.addPara(holdMusic[index], Color.GRAY);

        if (holdWaitCount >= 3) {
            main.textPanel.addPara(funFacts[random.nextInt(funFacts.length)], Color.CYAN);
        }

        if (holdWaitCount >= 5) {
            // Finally connect to someone
            main.textPanel.addPara("", Color.GRAY);
            main.textPanel.addPara("A representative is now available!", Color.GREEN);
            main.getOptions().addOption("Connect to Representative", "cash_out_captcha");
        } else {
            main.getOptions().addOption("Continue Holding", "cash_out_hold");
        }

        main.getOptions().addOption("Hang Up", "financial_menu");
        main.setState(CasinoInteraction.State.FINANCIAL);
    }

    private void performCashOutCaptcha() {
        main.getOptions().clearOptions();
        holdWaitCount = 0; // Reset for potential future holds

        // Pick a random question
        currentCaptchaIndex = random.nextInt(captchaQuestions.size());
        CaptchaQuestion q = captchaQuestions.get(currentCaptchaIndex);

        main.textPanel.addPara("Tachy-Impact Customer Support", Color.GREEN);
        main.textPanel.addPara("Security Verification Required", Color.YELLOW);
        main.textPanel.addPara("Before we proceed, please complete this security verification:", Color.GRAY);
        main.textPanel.addPara("", Color.GRAY);
        main.textPanel.addPara(q.question, Color.CYAN);

        // Add options for each answer
        for (int i = 0; i < q.options.length; i++) {
            String optionId = (i == q.correctIndex) ? "captcha_answer_" + i : "captcha_wrong_" + i;
            main.getOptions().addOption(q.options[i], optionId);
        }

        main.getOptions().addOption("Hang Up", "financial_menu");
        main.setState(CasinoInteraction.State.FINANCIAL);
    }

    private void checkCaptchaAnswer(int answerIndex) {
        if (currentCaptchaIndex >= 0 && currentCaptchaIndex < captchaQuestions.size()) {
            CaptchaQuestion q = captchaQuestions.get(currentCaptchaIndex);
            if (answerIndex == q.correctIndex) {
                performCashOutTier1();
            } else {
                performCashOutCaptchaWrong(answerIndex);
            }
        }
    }

    private void performCashOutCaptchaWrong(int wrongIndex) {
        main.getOptions().clearOptions();

        String[] wrongResponses = {
            "That answer doesn't match our records. Please try again.",
            "Incorrect. Are you sure you're not a robot?",
            "Wrong answer. Let me transfer you back to the beginning... just kidding, try again!",
            "Error: Mathematical calculation mismatch detected.",
            "That is not the answer we were looking for."
        };

        main.textPanel.addPara("Tachy-Impact Customer Support", Color.GREEN);
        main.textPanel.addPara(wrongResponses[random.nextInt(wrongResponses.length)], Color.RED);
        main.textPanel.addPara("", Color.GRAY);
        main.textPanel.addPara("Please try again:", Color.YELLOW);

        // Show the same question again
        if (currentCaptchaIndex >= 0 && currentCaptchaIndex < captchaQuestions.size()) {
            CaptchaQuestion q = captchaQuestions.get(currentCaptchaIndex);
            main.textPanel.addPara(q.question, Color.CYAN);

            for (int i = 0; i < q.options.length; i++) {
                String optionId = (i == q.correctIndex) ? "captcha_answer_" + i : "captcha_wrong_" + i;
                main.getOptions().addOption(q.options[i], optionId);
            }
        }

        main.getOptions().addOption("Hang Up", "financial_menu");
        main.setState(CasinoInteraction.State.FINANCIAL);
    }

    private void performCashOutTier1() {
        main.getOptions().clearOptions();

        main.textPanel.addPara("Tachy-Impact Customer Support", Color.GREEN);
        main.textPanel.addPara("Representative: 'Steve'", Color.YELLOW);
        main.textPanel.addPara("", Color.GRAY);
        main.textPanel.addPara("Hello! Thank you for holding. My name is Steve.", Color.CYAN);
        main.textPanel.addPara("I understand you'd like to cash out your Stargems.", Color.GRAY);
        main.textPanel.addPara("", Color.GRAY);
        main.textPanel.addPara("Have you tried spending your Stargems on our premium ship collection instead?", Color.ORANGE);
        main.textPanel.addPara("We have some excellent deals right now!", Color.CYAN);

        main.getOptions().addOption("No, I want to cash out", "cash_out_escalate_no");
        main.getOptions().addOption("Tell me about the ships", "cash_out_escalate_ships");
        main.setState(CasinoInteraction.State.FINANCIAL);
    }

    private void performCashOutEscalate() {
        main.getOptions().clearOptions();

        main.textPanel.addPara("Tachy-Impact Customer Support", Color.GREEN);
        main.textPanel.addPara("Representative: 'Steve'", Color.YELLOW);
        main.textPanel.addPara("", Color.GRAY);
        main.textPanel.addPara("I see. Let me transfer you to our Senior Account Specialist.", Color.CYAN);
        main.textPanel.addPara("They have the authority to process cashout requests.", Color.GRAY);
        main.textPanel.addPara("", Color.GRAY);
        main.textPanel.addPara("*Transferring...*", Color.ORANGE);
        main.textPanel.addPara("*Click...*", Color.GRAY);
        main.textPanel.addPara("*Hold music resumes...*", Color.GRAY);

        main.getOptions().addOption("Continue Holding", "cash_out_final");
        main.getOptions().addOption("Hang Up", "financial_menu");
        main.setState(CasinoInteraction.State.FINANCIAL);
    }

    private void performCashOutFinal() {
        main.getOptions().clearOptions();

        main.textPanel.addPara("Tachy-Impact Customer Support", Color.GREEN);
        main.textPanel.addPara("*Hold music continues for several minutes...*", Color.GRAY);
        main.textPanel.addPara("", Color.GRAY);
        main.textPanel.addPara("We're sorry.", Color.ORANGE);
        main.textPanel.addPara("All our senior representatives are currently assisting other customers.", Color.GRAY);
        main.textPanel.addPara("", Color.GRAY);
        main.textPanel.addPara("ERROR CODE: CASH-404", Color.RED);
        main.textPanel.addPara("Description: Representative not found", Color.RED);
        main.textPanel.addPara("", Color.GRAY);
        main.textPanel.addPara("Please call back during normal business hours.", Color.GRAY);
        main.textPanel.addPara("(Normal business hours: 3:00 AM to 3:15 AM, Tuesdays only)", Color.ORANGE);

        main.getOptions().addOption("Hang Up", "financial_menu");
        main.setState(CasinoInteraction.State.FINANCIAL);

        // Reset any stored state
        Global.getSector().getMemoryWithoutUpdate().set("$casino_cashout_stage", 0);
    }

    private void performCashOutReturn() {
        main.getOptions().clearOptions();

        main.textPanel.addPara("Tachy-Impact Customer Support", Color.GREEN);
        main.textPanel.addPara("Welcome back!", Color.YELLOW);
        main.textPanel.addPara("", Color.GRAY);
        main.textPanel.addPara("We see you have a pending cashout request from your previous call.", Color.CYAN);
        main.textPanel.addPara("Please hold while we connect you to a representative...", Color.GRAY);

        main.getOptions().addOption("Continue to Hold", "cash_out_return_hold");
        main.getOptions().addOption("Hang Up", "financial_menu");
        main.setState(CasinoInteraction.State.FINANCIAL);
    }

    private void performCashOutReturnHold() {
        main.getOptions().clearOptions();

        main.textPanel.addPara("Tachy-Impact Customer Support", Color.GREEN);
        main.textPanel.addPara("Please hold...", Color.YELLOW);
        main.textPanel.addPara("", Color.GRAY);
        main.textPanel.addPara("Your previous request ID could not be located in our system.", Color.RED);
        main.textPanel.addPara("Please start the process again from the beginning.", Color.ORANGE);
        main.textPanel.addPara("", Color.GRAY);
        main.textPanel.addPara("Thank you for choosing Tachy-Impact Financial Services!", Color.CYAN);

        main.getOptions().addOption("Start Over", "cash_out");
        main.getOptions().addOption("Hang Up", "financial_menu");
        main.setState(CasinoInteraction.State.FINANCIAL);

        Global.getSector().getMemoryWithoutUpdate().set("$casino_cashout_stage", 0);
    }
    
    private void openShipTradePicker() {
        main.getDialog().showFleetMemberPickerDialog("Select ships to sell for Stargems:", "Trade for Gems", "Cancel", 10, 7, 88, true, true, Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy(), 
            new FleetMemberPickerListener() {
                public void pickedFleetMembers(List<FleetMemberAPI> members) {
                     if (members != null) {
                        for (FleetMemberAPI m : members) {
                            int val = (int)(m.getBaseValue() / CasinoConfig.STARGEM_EXCHANGE_RATE * CasinoConfig.SHIP_SELL_MULTIPLIER);
                            CasinoVIPManager.addToBalance(val);
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
