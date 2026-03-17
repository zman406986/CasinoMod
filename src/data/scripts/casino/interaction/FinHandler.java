package data.scripts.casino.interaction;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FleetMemberPickerListener;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.casino.CasinoConfig;
import data.scripts.casino.CasinoDebtScript;
import data.scripts.casino.CasinoVIPManager;
import data.scripts.casino.Strings;
import java.awt.Color;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

/**
 * Handler for financial services: VIP subscriptions, ship trading, and the
 * satirical ISP-themed cashout flow. Cashout is deliberately obstructive
 * with hold queues, CAPTCHA verification, and dead-end transfers.
 */
public class FinHandler {

    private final CasinoInteraction main;
    private final Random random = new Random();

    private final Map<String, OptionHandler> handlers = new HashMap<>();
    private final Map<Predicate<String>, OptionHandler> predicateHandlers = new HashMap<>();

    private record CaptchaQuestion(String question, String[] options, int correctIndex)
    {    }

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
        handlers.put("financial_menu", option -> showFinancialMenu());

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
        handlers.put("captcha_wrong_1", option -> performCashOutCaptchaWrong());
        handlers.put("captcha_wrong_2", option -> performCashOutCaptchaWrong());

        handlers.put("buy_vip", option -> showVIPConfirm());
        handlers.put("confirm_buy_vip", option -> purchaseVIPPass());
        handlers.put("buy_ship", option -> openShipTradePicker());
        handlers.put("confirm_ship_trade", option -> confirmShipTrade());
        handlers.put("cancel_ship_trade", option -> showFinancialMenu());
        handlers.put("toggle_vip_notifications", option -> toggleVIPNotifications());
        handlers.put("back_menu", option -> main.showMenu());
        handlers.put("how_to_play_main", option -> main.help.showGeneralHelp());
        handlers.put("how_to_financial", option -> main.help.showFinancialHelp());

        // Predicate-based handlers for pattern matching
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
        
        for (Map.Entry<Predicate<String>, OptionHandler> entry : predicateHandlers.entrySet()) {
            if (entry.getKey().test(option)) {
                entry.getValue().handle(option);
                return;
            }
        }
    }

    public void showFinancialMenu() {
        main.getOptions().clearOptions();
        main.getTextPanel().addPara(Strings.get("financial.title"), Color.GREEN);

        int cashoutStage = 0;
        if (Global.getSector().getMemoryWithoutUpdate().contains("$casino_cashout_stage")) {
            cashoutStage = Global.getSector().getMemoryWithoutUpdate().getInt("$casino_cashout_stage");
        }

        if (cashoutStage == 1) {
            main.getOptions().addOption(Strings.get("financial.check_cashout"), "cash_out_return");
            main.getOptions().addOption(Strings.get("common.back"), "back_menu");
            main.setState(CasinoInteraction.State.FINANCIAL);
            return;
        }

        displayFinancialInfo();

        main.getOptions().addOption(Strings.get("financial.sell_ships"), "buy_ship");
        main.getOptions().addOption(Strings.get("financial.purchase_vip"), "buy_vip");

        boolean monthlyMode = CasinoVIPManager.isMonthlyNotificationMode();
        String notifyText = monthlyMode ? Strings.get("financial.notifications_monthly") : Strings.get("financial.notifications_daily");
        main.getOptions().addOption(notifyText, "toggle_vip_notifications");

        main.getOptions().addOption(Strings.get("financial.how_works"), "how_to_financial");
        main.getOptions().addOption(Strings.get("financial.cash_out"), "cash_out");

        main.getOptions().addOption(Strings.get("common.back"), "back_menu");
        main.setState(CasinoInteraction.State.FINANCIAL);
    }
    
    private void displayFinancialInfo() {
        int currentBalance = CasinoVIPManager.getBalance();
        int creditCeiling = CasinoVIPManager.getCreditCeiling();
        int daysRemaining = CasinoVIPManager.getDaysRemaining();
        
        main.getTextPanel().addPara(Strings.get("financial_status.header"), Color.CYAN);
        
        Color balanceColor = currentBalance >= 0 ? Color.GREEN : Color.RED;
        main.getTextPanel().addPara(Strings.format("financial_status.balance", currentBalance), balanceColor);
        
        main.getTextPanel().addPara(Strings.format("financial_status.credit_ceiling", creditCeiling), Color.GRAY);
        
        if (daysRemaining > 0) {
            main.getTextPanel().addPara(Strings.format("financial.vip_active", daysRemaining), Color.CYAN);
        } else {
            main.getTextPanel().addPara(Strings.get("financial.vip_inactive"), Color.GRAY);
        }
        
        if (currentBalance < 0) {
            boolean hasVIP = daysRemaining > 0;
            float interestRate = hasVIP ? CasinoConfig.VIP_DAILY_INTEREST_RATE : CasinoConfig.NON_VIP_DAILY_INTEREST_RATE;
            main.getTextPanel().addPara(Strings.format("financial.daily_interest", interestRate * 100), Color.YELLOW);
        }

        if (CasinoDebtScript.isCollectorActive()) {
            main.getTextPanel().addPara(Strings.get("financial.warning_collector_pursuing"), Color.RED);
        } else if (CasinoDebtScript.isCollectorPending()) {
            main.getTextPanel().addPara(Strings.get("financial.caution_dispatched"), Misc.getHighlightColor());
        }

        main.getTextPanel().addPara(Strings.get("financial_status.divider"), Color.CYAN);
    }
    
    private void toggleVIPNotifications() {
        boolean newMode = CasinoVIPManager.toggleMonthlyNotificationMode();
        if (newMode) {
            main.textPanel.addPara(Strings.get("financial.notification_monthly_set"), Color.CYAN);
        } else {
            main.textPanel.addPara(Strings.get("financial.notification_daily_set"), Color.CYAN);
        }
        showFinancialMenu();
    }

    private void showVIPConfirm() {
        main.getOptions().clearOptions();
        int currentDays = CasinoVIPManager.getDaysRemaining();
        String message = Strings.format("financial_vip.confirm_purchase", CasinoConfig.VIP_PASS_DAYS, CasinoConfig.VIP_PASS_COST);
        if (currentDays > 0) {
            message += " " + Strings.format("financial_vip.current_vip", currentDays);
        }
        main.textPanel.addPara(message, Color.YELLOW);
        main.getOptions().addOption(Strings.get("financial_vip.confirm_purchase_btn"), "confirm_buy_vip");
        main.getOptions().addOption(Strings.get("common.cancel"), "financial_menu");
    }

    private void purchaseVIPPass() {
        int cost = CasinoConfig.VIP_PASS_COST;
        if (Global.getSector().getPlayerFleet().getCargo().getCredits().get() < cost) {
            main.textPanel.addPara(Strings.get("financial_vip.insufficient_credits"), Color.RED);
            showFinancialMenu();
            return;
        }
        
        Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(cost);
        CasinoVIPManager.addSubscriptionDays(CasinoConfig.VIP_PASS_DAYS);
        
        int daysAfter = CasinoVIPManager.getDaysRemaining();
        main.textPanel.addPara(Strings.format("financial_vip.activated", daysAfter), Color.CYAN);
        showFinancialMenu();
    }

    // ==================== NEW ISP/TECH SUPPORT CASHOUT FLOW ====================

    private void performCashOut() {
        main.getOptions().clearOptions();
        main.textPanel.addPara(Strings.get("cashout.support_title"), Color.GREEN);
        main.textPanel.addPara(Strings.get("cashout.welcome_1"), Color.YELLOW);
        main.textPanel.addPara(Strings.get("cashout.welcome_2"), Color.GRAY);
        main.textPanel.addPara("", Color.GRAY);
        main.textPanel.addPara(Strings.get("cashout.promo"), Color.CYAN);

        main.getOptions().addOption(Strings.get("cashout.continue_support"), "cash_out_phone");
        main.getOptions().addOption(Strings.get("cashout.hang_up"), "financial_menu");
        main.setState(CasinoInteraction.State.FINANCIAL);
    }

    private void performCashOutPhone() {
        main.getOptions().clearOptions();
        main.textPanel.addPara(Strings.get("cashout.support_title"), Color.GREEN);
        main.textPanel.addPara(Strings.get("cashout.automated_system"), Color.YELLOW);
        main.textPanel.addPara(Strings.get("cashout.menu_billing"), Color.GRAY);
        main.textPanel.addPara(Strings.get("cashout.menu_sales"), Color.GRAY);
        main.textPanel.addPara(Strings.get("cashout.menu_tech"), Color.GRAY);
        main.textPanel.addPara(Strings.get("cashout.menu_representative"), Color.GRAY);
        main.textPanel.addPara("", Color.GRAY);
        main.textPanel.addPara(Strings.get("cashout.note_unavailable"), Color.RED);

        main.getOptions().addOption(Strings.get("cashout.press_1"), "cash_out_phone_billing");
        main.getOptions().addOption(Strings.get("cashout.press_2"), "cash_out_phone_sales");
        main.getOptions().addOption(Strings.get("cashout.press_3"), "cash_out_phone_support");
        main.getOptions().addOption(Strings.get("cashout.hang_up"), "financial_menu");
        main.setState(CasinoInteraction.State.FINANCIAL);
    }

    private void performCashOutPhoneDeadEnd(String department) {
        main.getOptions().clearOptions();
        main.textPanel.addPara(Strings.get("cashout.support_title"), Color.GREEN);

        if (department.equals("billing")) {
            main.textPanel.addPara(Strings.get("cashout.billing_dept"), Color.YELLOW);
            main.textPanel.addPara(Strings.get("cashout.billing_busy"), Color.ORANGE);
            main.textPanel.addPara(Strings.get("cashout.billing_try_later"), Color.GRAY);
        } else {
            main.textPanel.addPara(Strings.get("cashout.sales_dept"), Color.YELLOW);
            main.textPanel.addPara(Strings.get("cashout.sales_offer"), Color.CYAN);
            main.textPanel.addPara(Strings.get("cashout.sales_no_cashout"), Color.ORANGE);
        }

        main.textPanel.addPara("", Color.GRAY);
        main.textPanel.addPara(Strings.get("cashout.returning_menu"), Color.GRAY);

        main.getOptions().addOption(Strings.get("cashout.return_phone_menu"), "cash_out_phone");
        main.getOptions().addOption(Strings.get("cashout.hang_up"), "financial_menu");
        main.setState(CasinoInteraction.State.FINANCIAL);
    }

    private void performCashOutHold() {
        main.getOptions().clearOptions();
        holdWaitCount++;

        main.textPanel.addPara(Strings.get("cashout.support_title"), Color.GREEN);
        main.textPanel.addPara(Strings.get("cashout.please_hold"), Color.YELLOW);
        main.textPanel.addPara("", Color.GRAY);

        String[] waitMessages = {
            Strings.get("cashout.wait_4min"),
            Strings.get("cashout.wait_12min"),
            Strings.get("cashout.wait_47min"),
            Strings.get("cashout.wait_2hrs"),
            Strings.get("cashout.wait_unavailable")
        };

        String[] holdMusic = {
            Strings.get("cashout.music_elevator"),
            Strings.get("cashout.music_synth"),
            Strings.get("cashout.music_static"),
            Strings.get("cashout.music_humming"),
            Strings.get("cashout.music_skips")
        };

        String[] funFacts = {
            Strings.get("cashout.fact_transactions"),
            Strings.get("cashout.fact_vip_music"),
            Strings.get("cashout.fact_spending"),
            Strings.get("cashout.fact_premium"),
            Strings.get("cashout.fact_give_up")
        };

        int index = Math.min(holdWaitCount - 1, waitMessages.length - 1);
        main.textPanel.addPara(waitMessages[index], Color.ORANGE);
        main.textPanel.addPara(holdMusic[index], Color.GRAY);

        if (holdWaitCount >= 3) {
            main.textPanel.addPara(funFacts[random.nextInt(funFacts.length)], Color.CYAN);
        }

        if (holdWaitCount >= 5) {
            main.textPanel.addPara("", Color.GRAY);
            main.textPanel.addPara(Strings.get("cashout.rep_available"), Color.GREEN);
            main.getOptions().addOption(Strings.get("cashout.connect_rep"), "cash_out_captcha");
        } else {
            main.getOptions().addOption(Strings.get("cashout.continue_holding"), "cash_out_hold");
        }

        main.getOptions().addOption(Strings.get("cashout.hang_up"), "financial_menu");
        main.setState(CasinoInteraction.State.FINANCIAL);
    }

    private void performCashOutCaptcha() {
        main.getOptions().clearOptions();
        holdWaitCount = 0;

        currentCaptchaIndex = random.nextInt(captchaQuestions.size());
        CaptchaQuestion q = captchaQuestions.get(currentCaptchaIndex);

        main.textPanel.addPara(Strings.get("cashout.support_title"), Color.GREEN);
        main.textPanel.addPara(Strings.get("cashout.security_title"), Color.YELLOW);
        main.textPanel.addPara(Strings.get("cashout.security_instruction"), Color.GRAY);
        main.textPanel.addPara("", Color.GRAY);
        main.textPanel.addPara(q.question, Color.CYAN);

        for (int i = 0; i < q.options.length; i++) {
            String optionId = (i == q.correctIndex) ? "captcha_answer_" + i : "captcha_wrong_" + i;
            main.getOptions().addOption(q.options[i], optionId);
        }

        main.getOptions().addOption(Strings.get("cashout.hang_up"), "financial_menu");
        main.setState(CasinoInteraction.State.FINANCIAL);
    }

    private void checkCaptchaAnswer(int answerIndex) {
        if (currentCaptchaIndex >= 0 && currentCaptchaIndex < captchaQuestions.size()) {
            CaptchaQuestion q = captchaQuestions.get(currentCaptchaIndex);
            if (answerIndex == q.correctIndex) {
                performCashOutTier1();
            } else {
                performCashOutCaptchaWrong();
            }
        }
    }

    private void performCashOutCaptchaWrong() {
        main.getOptions().clearOptions();

        String[] wrongResponses = {
            Strings.get("cashout.wrong_1"),
            Strings.get("cashout.wrong_2"),
            Strings.get("cashout.wrong_3"),
            Strings.get("cashout.wrong_4"),
            Strings.get("cashout.wrong_5")
        };

        main.textPanel.addPara(Strings.get("cashout.support_title"), Color.GREEN);
        main.textPanel.addPara(wrongResponses[random.nextInt(wrongResponses.length)], Color.RED);
        main.textPanel.addPara("", Color.GRAY);
        main.textPanel.addPara(Strings.get("cashout.try_again"), Color.YELLOW);

        if (currentCaptchaIndex >= 0 && currentCaptchaIndex < captchaQuestions.size()) {
            CaptchaQuestion q = captchaQuestions.get(currentCaptchaIndex);
            main.textPanel.addPara(q.question, Color.CYAN);

            for (int i = 0; i < q.options.length; i++) {
                String optionId = (i == q.correctIndex) ? "captcha_answer_" + i : "captcha_wrong_" + i;
                main.getOptions().addOption(q.options[i], optionId);
            }
        }

        main.getOptions().addOption(Strings.get("cashout.hang_up"), "financial_menu");
        main.setState(CasinoInteraction.State.FINANCIAL);
    }

    private void performCashOutTier1() {
        main.getOptions().clearOptions();

        main.textPanel.addPara(Strings.get("cashout.support_title"), Color.GREEN);
        main.textPanel.addPara(Strings.get("cashout.rep_steve"), Color.YELLOW);
        main.textPanel.addPara("", Color.GRAY);
        main.textPanel.addPara(Strings.get("cashout.steve_greeting"), Color.CYAN);
        main.textPanel.addPara(Strings.get("cashout.steve_acknowledge"), Color.GRAY);
        main.textPanel.addPara("", Color.GRAY);
        main.textPanel.addPara(Strings.get("cashout.steve_upsell_1"), Color.ORANGE);
        main.textPanel.addPara(Strings.get("cashout.steve_upsell_2"), Color.CYAN);

        main.getOptions().addOption(Strings.get("cashout.steve_no_cashout"), "cash_out_escalate_no");
        main.getOptions().addOption(Strings.get("cashout.steve_tell_ships"), "cash_out_escalate_ships");
        main.setState(CasinoInteraction.State.FINANCIAL);
    }

    private void performCashOutEscalate() {
        main.getOptions().clearOptions();

        main.textPanel.addPara(Strings.get("cashout.support_title"), Color.GREEN);
        main.textPanel.addPara(Strings.get("cashout.rep_steve"), Color.YELLOW);
        main.textPanel.addPara("", Color.GRAY);
        main.textPanel.addPara(Strings.get("cashout.steve_transfer"), Color.CYAN);
        main.textPanel.addPara(Strings.get("cashout.steve_authority"), Color.GRAY);
        main.textPanel.addPara("", Color.GRAY);
        main.textPanel.addPara(Strings.get("cashout.transferring"), Color.ORANGE);
        main.textPanel.addPara(Strings.get("cashout.click"), Color.GRAY);
        main.textPanel.addPara(Strings.get("cashout.hold_resumes"), Color.GRAY);

        main.getOptions().addOption(Strings.get("cashout.continue_holding"), "cash_out_final");
        main.getOptions().addOption(Strings.get("cashout.hang_up"), "financial_menu");
        main.setState(CasinoInteraction.State.FINANCIAL);
    }

    private void performCashOutFinal() {
        main.getOptions().clearOptions();

        main.textPanel.addPara(Strings.get("cashout.support_title"), Color.GREEN);
        main.textPanel.addPara(Strings.get("cashout.hold_continues"), Color.GRAY);
        main.textPanel.addPara("", Color.GRAY);
        main.textPanel.addPara(Strings.get("cashout.sorry"), Color.ORANGE);
        main.textPanel.addPara(Strings.get("cashout.all_busy"), Color.GRAY);
        main.textPanel.addPara("", Color.GRAY);
        main.textPanel.addPara(Strings.get("cashout.error_code"), Color.RED);
        main.textPanel.addPara(Strings.get("cashout.error_desc"), Color.RED);
        main.textPanel.addPara("", Color.GRAY);
        main.textPanel.addPara(Strings.get("cashout.call_back_hours"), Color.GRAY);
        main.textPanel.addPara(Strings.get("cashout.business_hours"), Color.ORANGE);

        main.getOptions().addOption(Strings.get("cashout.hang_up"), "financial_menu");
        main.setState(CasinoInteraction.State.FINANCIAL);

        Global.getSector().getMemoryWithoutUpdate().set("$casino_cashout_stage", 0);
    }

    private void performCashOutReturn() {
        main.getOptions().clearOptions();

        main.textPanel.addPara(Strings.get("cashout.support_title"), Color.GREEN);
        main.textPanel.addPara(Strings.get("cashout.welcome_back"), Color.YELLOW);
        main.textPanel.addPara("", Color.GRAY);
        main.textPanel.addPara(Strings.get("cashout.pending_cashout"), Color.CYAN);
        main.textPanel.addPara(Strings.get("cashout.connecting_rep"), Color.GRAY);

        main.getOptions().addOption(Strings.get("cashout.continue_hold"), "cash_out_return_hold");
        main.getOptions().addOption(Strings.get("cashout.hang_up"), "financial_menu");
        main.setState(CasinoInteraction.State.FINANCIAL);
    }

    private void performCashOutReturnHold() {
        main.getOptions().clearOptions();

        main.textPanel.addPara(Strings.get("cashout.support_title"), Color.GREEN);
        main.textPanel.addPara(Strings.get("cashout.please_hold_ellipsis"), Color.YELLOW);
        main.textPanel.addPara("", Color.GRAY);
        main.textPanel.addPara(Strings.get("cashout.request_not_found"), Color.RED);
        main.textPanel.addPara(Strings.get("cashout.start_over"), Color.ORANGE);
        main.textPanel.addPara("", Color.GRAY);
        main.textPanel.addPara(Strings.get("cashout.thank_you"), Color.CYAN);

        main.getOptions().addOption(Strings.get("cashout.start_over_btn"), "cash_out");
        main.getOptions().addOption(Strings.get("cashout.hang_up"), "financial_menu");
        main.setState(CasinoInteraction.State.FINANCIAL);

        Global.getSector().getMemoryWithoutUpdate().set("$casino_cashout_stage", 0);
    }
    
    private List<FleetMemberAPI> pendingShipTrade = null;

    private void openShipTradePicker() {
        main.getDialog().showFleetMemberPickerDialog(Strings.get("financial_ship_trade.select_ships"), Strings.get("financial_ship_trade.sell"), Strings.get("common.cancel"), 8, 7, 80, true, true, Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy(),
            new FleetMemberPickerListener() {
                public void pickedFleetMembers(List<FleetMemberAPI> members) {
                     if (members != null && !members.isEmpty()) {
                        pendingShipTrade = members;
                        showShipTradeConfirmation(members);
                     } else {
                         showFinancialMenu();
                     }
                }
                public void cancelledFleetMemberPicking() { showFinancialMenu(); }
            });
    }

    private void showShipTradeConfirmation(List<FleetMemberAPI> members) {
        main.getOptions().clearOptions();
        main.getTextPanel().addPara(Strings.get("financial_ship_trade.confirm_title"), Color.YELLOW);
        main.getTextPanel().addPara("");

        int totalValue = 0;
        for (FleetMemberAPI m : members) {
            int val = (int)(m.getBaseValue() / CasinoConfig.STARGEM_EXCHANGE_RATE * CasinoConfig.SHIP_SELL_MULTIPLIER);
            totalValue += val;
            main.getTextPanel().addPara(Strings.format("financial_ship_trade.ship_value", m.getShipName(), m.getHullSpec().getHullName(), val), Color.CYAN);
        }

        main.getTextPanel().addPara("");
        main.getTextPanel().addPara(Strings.format("financial_ship_trade.total", totalValue), Color.GREEN);
        main.getTextPanel().addPara("");
        main.getTextPanel().addPara(Strings.get("financial_ship_trade.warning_no_buyback"), Color.RED);
        main.getTextPanel().addPara(Strings.get("financial_ship_trade.permanent"), Color.RED);

        main.getOptions().addOption(Strings.get("financial_ship_trade.confirm_trade"), "confirm_ship_trade");
        main.getOptions().addOption(Strings.get("common.cancel"), "cancel_ship_trade");
        main.setState(CasinoInteraction.State.FINANCIAL);
    }

    private void confirmShipTrade() {
        if (pendingShipTrade != null) {
            for (FleetMemberAPI m : pendingShipTrade) {
                int val = (int)(m.getBaseValue() / CasinoConfig.STARGEM_EXCHANGE_RATE * CasinoConfig.SHIP_SELL_MULTIPLIER);
                CasinoVIPManager.addToBalance(val);
                Global.getSector().getPlayerFleet().getFleetData().removeFleetMember(m);
                main.getTextPanel().addPara(Strings.format("financial_ship_trade.traded", m.getShipName(), val), Color.GREEN);
            }
            pendingShipTrade = null;
        }
        showFinancialMenu();
    }
}
