package data.scripts.casino.interaction;

import com.fs.starfarer.api.Global;
import data.scripts.casino.arena.ArenaDialogDelegate;
import data.scripts.casino.arena.ArenaPanelUI;
import data.scripts.casino.arena.SpiralAbyssArena;
import data.scripts.casino.CasinoConfig;
import data.scripts.casino.gacha.CasinoGachaManager;
import data.scripts.casino.CasinoVIPManager;
import data.scripts.casino.Strings;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;
import java.util.function.Predicate;

public class ArenaHandler {

    private static final int MIN_BET_INCREMENT = 100;
    private static final int[] BET_AMOUNTS = {100, 500, 2000};
    private static final int PERCENT_10 = 10;
    private static final int PERCENT_50 = 50;

    // Option prefixes for menu handlers
    private static final String OPTION_ARENA_LOBBY = "arena_lobby";
    private static final String OPTION_ARENA_SELECT_SHIP = "arena_select_ship_";
    private static final String OPTION_ARENA_ADD_BET = "arena_add_bet_";
    private static final String OPTION_ARENA_CONFIRM_ADD_BET = "arena_confirm_add_bet_";
    private static final String OPTION_ARENA_CONFIRM_ADD_BET_TO_CHAMP = "arena_confirm_add_bet_to_champ_";
    private static final String OPTION_ARENA_SELECT_CHAMPION_FOR_BET = "arena_select_champion_for_bet_";
    private static final String OPTION_ARENA_START_BATTLE = "arena_start_battle_with_current_bet";
    private static final String OPTION_ARENA_WATCH_NEXT = "arena_watch_next";
    private static final String OPTION_ARENA_SKIP = "arena_skip";
    private static final String OPTION_ARENA_SUSPEND = "arena_suspend";
    private static final String OPTION_ARENA_RESUME = "arena_resume";
    private static final String OPTION_ARENA_ADD_ANOTHER_BET = "arena_add_another_bet";
    private static final String OPTION_ARENA_STATUS = "arena_status";
    private static final String OPTION_ARENA_LEAVE_NOW = "arena_leave_now";
    private static final String OPTION_BACK_MENU = "back_menu";
    private static final String OPTION_HOW_TO_ARENA = "how_to_arena";
    private static final String OPTION_TOPUP_MENU = "topup_menu";

    // Memory keys for suspended arena state
    private static final String MEM_SUSPENDED_GAME_TYPE = "$ipc_suspended_game_type";
    private static final String MEM_ARENA_CURRENT_ROUND = "$ipc_arena_current_round";
    private static final String MEM_ARENA_OPPONENTS_DEFEATED = "$ipc_arena_opponents_defeated";
    private static final String MEM_ARENA_COMBATANT_COUNT = "$ipc_arena_combatant_count";
    private static final String MEM_ARENA_COMBATANT_PREFIX = "$ipc_arena_combatant_";
    private static final String MEM_ARENA_BETS_COUNT = "$ipc_arena_bets_count";
    private static final String MEM_ARENA_SUSPEND_TIME = "$ipc_arena_suspend_time";

private final CasinoInteraction main;
    private ArenaDialogDelegate currentDelegate;
    
    protected SpiralAbyssArena activeArena;
    protected List<SpiralAbyssArena.SpiralGladiator> arenaCombatants;
    protected SpiralAbyssArena.SpiralGladiator chosenChampion;
    protected int opponentsDefeated;
    protected int currentRound;
    protected int currentBetAmount = CasinoConfig.ARENA_ENTRY_FEE;
    protected int cachedTotalBet = 0;

    public static class BetInfo {
        public int amount;
        public float multiplier;
        public SpiralAbyssArena.SpiralGladiator ship;
        public int roundPlaced;

        public BetInfo(int amount, float multiplier, SpiralAbyssArena.SpiralGladiator ship, int roundPlaced) {
            this.amount = amount;
            this.multiplier = multiplier;
            this.ship = ship;
            this.roundPlaced = roundPlaced;
        }
    }

    public static class BetValidationResult {
        public enum ResultType {
            CAN_AFFORD,
            NEEDS_OVERDRAFT,
            ERROR
        }
        
        public final ResultType type;
        public final String errorMessage;
        public final String overdraftMessage;
        public final int newBalance;
        public final int overdraftAmount;
        
        private BetValidationResult(ResultType type, String errorMessage, String overdraftMessage, 
                                    int newBalance, int overdraftAmount) {
            this.type = type;
            this.errorMessage = errorMessage;
            this.overdraftMessage = overdraftMessage;
            this.newBalance = newBalance;
            this.overdraftAmount = overdraftAmount;
        }
        
        public static BetValidationResult canAfford() {
            return new BetValidationResult(ResultType.CAN_AFFORD, null, null, 0, 0);
        }
        
        public static BetValidationResult needsOverdraft(String message, int newBalance, int overdraftAmount) {
            return new BetValidationResult(ResultType.NEEDS_OVERDRAFT, null, message, newBalance, overdraftAmount);
        }
        
        public static BetValidationResult error(String message) {
            return new BetValidationResult(ResultType.ERROR, message, null, 0, 0);
        }
        
        public boolean isAffordable() { return type == ResultType.CAN_AFFORD; }
        public boolean needsOverdraftConfirmation() { return type == ResultType.NEEDS_OVERDRAFT; }
        public boolean hasError() { return type == ResultType.ERROR; }
    }

    public static BetValidationResult validateBet(int amount) {
        int balance = CasinoVIPManager.getBalance();
        int availableCredit = CasinoVIPManager.getAvailableCredit();
        boolean isVIP = CasinoVIPManager.isOverdraftAvailable();
        
        if (balance >= amount) {
            return BetValidationResult.canAfford();
        }
        
        if (!isVIP) {
            return BetValidationResult.error(
                Strings.get("arena_errors.overdraft_vip_only")
            );
        }
        
        if (availableCredit <= 0) {
            return BetValidationResult.error(
                Strings.get("arena_errors.credit_exhausted_topup")
            );
        }
        
        if (availableCredit < amount) {
            return BetValidationResult.error(
                Strings.format("arena_errors.credit_insufficient", availableCredit, amount)
            );
        }
        
        int newBalance = balance - amount;
        int overdraftAmount = -newBalance;

        String msg = Strings.format("arena_errors.overdraft_balance_change", balance, newBalance, overdraftAmount);
        
        return BetValidationResult.needsOverdraft(msg, newBalance, overdraftAmount);
    }
    
protected List<BetInfo> arenaBets = new ArrayList<>();
    protected List<String> battleLog = new ArrayList<>();
    
    private final Map<String, OptionHandler> handlers = new HashMap<>();
    private final Map<Predicate<String>, OptionHandler> predicateHandlers = new HashMap<>();

    public ArenaHandler(CasinoInteraction main) {
        this.main = main;
        initializeHandlers();
    }
    
    private void initializeHandlers() {
        handlers.put("arena_visual_panel", option -> initAndShowVisualPanel());
        handlers.put(OPTION_ARENA_LOBBY, option -> {
            // Return any active bets when canceling back to lobby
            returnActiveBets();
            arenaBets.clear();
            cachedTotalBet = 0;
            currentBetAmount = 0;
            chosenChampion = null;
            currentRound = 0;
            opponentsDefeated = 0;
            battleLog.clear();
            showArenaLobby();
        });
        handlers.put("arena_add_bet_menu", option -> showAddBetMenu());
        handlers.put(OPTION_HOW_TO_ARENA, option -> main.help.showArenaHelp());
        handlers.put(OPTION_BACK_MENU, option -> {
            // If leaving without a suspended game, clear any suspended memory
            if (!hasSuspendedArena()) {
                clearSuspendedArenaMemory();
            }
            resetArenaState();
            main.showMenu();
        });
        handlers.put(OPTION_ARENA_RESUME, option -> restoreSuspendedArena());

        handlers.put(OPTION_ARENA_WATCH_NEXT, option -> simulateArenaStep());
        handlers.put(OPTION_ARENA_SKIP, option -> {
            boolean result;
            do {
                result = simulateArenaStep();
            } while (result);
        });
        handlers.put(OPTION_ARENA_SUSPEND, option -> suspendArena());
        handlers.put(OPTION_ARENA_ADD_ANOTHER_BET, option -> showAddAnotherBetMenu());
        handlers.put(OPTION_ARENA_STATUS, option -> showArenaVisualPanel());
        handlers.put(OPTION_ARENA_LEAVE_NOW, option -> {
            if (!hasSuspendedArena()) {
                clearSuspendedArenaMemory();
            }
            resetArenaState();
            main.showMenu();
        });
        handlers.put("arena_resume_continue", option -> {
            clearSuspendedArenaMemory();
            showArenaVisualPanel();
        });
        handlers.put("arena_resume_wait", option -> main.showMenu());
        handlers.put(OPTION_ARENA_START_BATTLE, option -> {
            int chosenIdx = -1;
            for (int i = 0; i < arenaCombatants.size(); i++) {
                if (chosenChampion != null &&
                    arenaCombatants.get(i).fullName.equals(chosenChampion.fullName)) {
                    chosenIdx = i;
                    break;
                }
            }
            if (chosenIdx != -1) {
                startArenaBattle(chosenIdx);
            } else {
                main.textPanel.addPara(Strings.get("errors.champion_not_found"), Color.RED);
                showArenaLobby();
            }
        });

        predicateHandlers.put(option -> option.startsWith(OPTION_ARENA_SELECT_SHIP), option -> {
            int parsedIdx = Integer.parseInt(option.replace(OPTION_ARENA_SELECT_SHIP, ""));
            showArenaConfirm(parsedIdx);
        });
        predicateHandlers.put(option -> option.startsWith(OPTION_ARENA_ADD_BET)
                && !option.equals("arena_add_bet_menu")
                && !option.startsWith(OPTION_ARENA_CONFIRM_ADD_BET), option -> {
            int parsedAdditionalAmount = Integer.parseInt(option.replace(OPTION_ARENA_ADD_BET, ""));
            confirmAddBet(parsedAdditionalAmount);
        });
        predicateHandlers.put(option -> option.startsWith(OPTION_ARENA_CONFIRM_ADD_BET)
                && !option.startsWith(OPTION_ARENA_CONFIRM_ADD_BET_TO_CHAMP), option -> {
            int parsedAdditionalAmount = Integer.parseInt(option.replace(OPTION_ARENA_CONFIRM_ADD_BET, ""));
            addIncrementalBet(parsedAdditionalAmount);
        });
        predicateHandlers.put(option -> option.startsWith(OPTION_ARENA_SELECT_CHAMPION_FOR_BET), option -> {
            int parsedIdx = Integer.parseInt(option.replace(OPTION_ARENA_SELECT_CHAMPION_FOR_BET, ""));
            showBetAmountSelection(parsedIdx);
        });
        predicateHandlers.put(option -> option.startsWith(OPTION_ARENA_CONFIRM_ADD_BET_TO_CHAMP), option -> {
            String[] parts = option.replace(OPTION_ARENA_CONFIRM_ADD_BET_TO_CHAMP, "").split("_");
            int championIndex = Integer.parseInt(parts[0]);
            int amount = Integer.parseInt(parts[1]);
            performAddBetToChampion(championIndex, amount);
        });

        // Handle contextual arena help options
        predicateHandlers.put(option -> option.startsWith("how_to_arena_"), option -> {
            String returnTo = option.replace("how_to_arena_", "");
            main.help.showArenaHelp(returnTo);
        });
    }

    // Color definitions for perks - prefixes (strong) are brighter, affixes (weak) are lighter
    private static final Color PREFIX_POSITIVE_COLOR = new Color(50, 255, 50);    // Bright green for strong positive
    private static final Color PREFIX_NEGATIVE_COLOR = new Color(255, 50, 50);    // Bright red for strong negative
    private static final Color AFFIX_POSITIVE_COLOR = new Color(100, 200, 100);   // Muted green for weak positive
    private static final Color AFFIX_NEGATIVE_COLOR = new Color(255, 150, 150);   // Pink/light red for weak negative

    /**
     * Applies color highlighting to ship name components in the text panel.
     * Uses a "foolproof" approach: scans the text for known perks from config lists
     * and highlights them in the order they appear in the text.
     */
    private void applyShipHighlighting(SpiralAbyssArena.SpiralGladiator ship, String fullText, Object... highlightPairs) {
        // Inner class to hold highlight info with position for sorting
        record HighlightInfo(String text, Color color, int position)
        {
        }

        List<HighlightInfo> highlightInfos = new ArrayList<>();

        List<String> prefixPos = Strings.getList("arena_prefixes.positive");
        List<String> prefixNeg = Strings.getList("arena_prefixes.negative");
        List<String> affixPos = Strings.getList("arena_affixes.positive");
        List<String> affixNeg = Strings.getList("arena_affixes.negative");

        for (String prefix : prefixPos) {
            int pos = fullText.indexOf(prefix);
            if (pos >= 0) {
                highlightInfos.add(new HighlightInfo(prefix, PREFIX_POSITIVE_COLOR, pos));
            }
        }
        for (String prefix : prefixNeg) {
            int pos = fullText.indexOf(prefix);
            if (pos >= 0) {
                highlightInfos.add(new HighlightInfo(prefix, PREFIX_NEGATIVE_COLOR, pos));
            }
        }

        for (String affix : affixPos) {
            int pos = fullText.indexOf(affix);
            if (pos >= 0) {
                highlightInfos.add(new HighlightInfo(affix, AFFIX_POSITIVE_COLOR, pos));
            }
        }
        for (String affix : affixNeg) {
            int pos = fullText.indexOf(affix);
            if (pos >= 0) {
                highlightInfos.add(new HighlightInfo(affix, AFFIX_NEGATIVE_COLOR, pos));
            }
        }

        // Hull name in yellow
        int hullPos = fullText.indexOf(ship.hullName);
        if (hullPos >= 0) {
            highlightInfos.add(new HighlightInfo(ship.hullName, Color.YELLOW, hullPos));
        }

        // Add additional highlights from parameters
        for (int i = 0; i < highlightPairs.length; i += 2) {
            if (i + 1 < highlightPairs.length) {
                String text = (String) highlightPairs[i];
                Color color = (Color) highlightPairs[i + 1];
                int pos = fullText.indexOf(text);
                if (pos >= 0) {
                    highlightInfos.add(new HighlightInfo(text, color, pos));
                }
            }
        }

        // Sort by position in text to ensure correct highlight order
        highlightInfos.sort(Comparator.comparingInt(a -> a.position));

        // Extract sorted highlights and colors
        List<String> highlights = new ArrayList<>();
        List<Color> highlightColors = new ArrayList<>();
        for (HighlightInfo info : highlightInfos) {
            highlights.add(info.text);
            highlightColors.add(info.color);
        }

        // Apply highlights
        if (!highlights.isEmpty()) {
            main.textPanel.setHighlightColorsInLastPara(highlightColors.toArray(new Color[0]));
            main.textPanel.highlightInLastPara(highlights.toArray(new String[0]));
        }
    }

    private boolean addBetOptions(int playerBalance, int availableCredit, String optionPrefix, int championIndex) {
        boolean hasBetOptions = false;
        boolean isVIP = CasinoVIPManager.isOverdraftAvailable();
        int referenceAmount = availableCredit > 0 ? availableCredit : playerBalance;
        String percentageLabel = isVIP ? "available credit" : "account";

        int currentBetOnChampion = 0;
        int maxBetAllowed = CasinoConfig.ARENA_MAX_BET_PER_CHAMPION;
        if (championIndex >= 0 && championIndex < arenaCombatants.size()) {
            currentBetOnChampion = getBetAmountForShip(arenaCombatants.get(championIndex));
        }

        for (int betAmount : BET_AMOUNTS) {
            if (availableCredit >= betAmount) {
                if (championIndex >= 0 && currentBetOnChampion + betAmount > maxBetAllowed) {
                    continue;
                }
                String optionId = championIndex >= 0
                    ? optionPrefix + championIndex + "_" + betAmount
                    : OPTION_ARENA_ADD_BET + betAmount;
                String label = Strings.format("arena.add_stargems", betAmount);
                if (playerBalance < betAmount && isVIP) {
                    label = Strings.format("arena.add_stargems_overdraft", betAmount);
                }
                main.getOptions().addOption(label, optionId);
                hasBetOptions = true;
            }
        }

        int[] percentages = {PERCENT_10, PERCENT_50};
        for (int percent : percentages) {
            int percentAmount = (referenceAmount * percent) / 100;
            if (percentAmount > 0 && availableCredit >= percentAmount) {
                if (championIndex >= 0 && currentBetOnChampion + percentAmount > maxBetAllowed) {
                    continue;
                }
                String optionId = championIndex >= 0
                    ? optionPrefix + championIndex + "_" + percentAmount
                    : OPTION_ARENA_ADD_BET + percentAmount;
                String label = Strings.format("arena.add_stargems_percent", percentAmount, percent, percentageLabel);
                if (playerBalance < percentAmount && isVIP) {
                    label = Strings.format("arena.add_stargems_overdraft", percentAmount);
                }
                main.getOptions().addOption(label, optionId);
                hasBetOptions = true;
            }
        }

        return hasBetOptions;
    }

    private boolean isBetInvalid(int amount, String returnToMenu) {
        int availableCredit = CasinoVIPManager.getAvailableCredit();
        int currentBalance = CasinoVIPManager.getBalance();
        boolean overdraftAvailable = CasinoVIPManager.isOverdraftAvailable();

        if (currentBalance < amount) {
            if (!overdraftAvailable) {
                showVIPPromotionForArena(amount, returnToMenu);
                return true;
            }

            if (availableCredit <= 0) {
                main.textPanel.addPara(Strings.format("arena_errors.credit_exhausted_bet", amount), Color.RED);
                main.textPanel.addPara(Strings.get("arena_errors.select_smaller_topup"), Color.YELLOW);
                showAddBetMenu();
                return true;
            } else if (availableCredit < amount) {
                main.textPanel.addPara(Strings.format("arena_errors.credit_insufficient_bet", availableCredit, amount), Color.RED);
                main.textPanel.addPara(Strings.get("arena_errors.select_smaller_topup"), Color.YELLOW);
                showAddBetMenu();
                return true;
            }
        }

        return false;
    }

    public void handle(String option) {
        // Try exact match handlers first
        OptionHandler handler = handlers.get(option);
        if (handler != null) {
            handler.handle(option);
            return;
        }
        
        // Try predicate-based handlers for pattern matching
        for (Map.Entry<Predicate<String>, OptionHandler> entry : predicateHandlers.entrySet()) {
            if (entry.getKey().test(option)) {
                entry.getValue().handle(option);
                return;
            }
        }
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

    public void initAndShowVisualPanel() {
        main.options.clearOptions();

        if (hasSuspendedArena()) {
            restoreSuspendedArena();
            return;
        }

        if (activeArena == null || arenaCombatants == null || arenaCombatants.isEmpty()) {
            activeArena = new SpiralAbyssArena();
            arenaCombatants = activeArena.generateCombatants(new CasinoGachaManager());
            currentRound = 0;
            opponentsDefeated = 0;
            battleLog.clear();
            currentBetAmount = CasinoConfig.ARENA_ENTRY_FEE;
        }

        main.setState(CasinoInteraction.State.ARENA);
        showArenaVisualPanel();
    }

    public void showArenaLobby() {
        main.options.clearOptions();

        if (hasSuspendedArena()) {
            restoreSuspendedArena();
            return;
        }

        if (activeArena == null || arenaCombatants == null || arenaCombatants.isEmpty()) {
            activeArena = new SpiralAbyssArena();
            arenaCombatants = activeArena.generateCombatants(new CasinoGachaManager());
            currentRound = 0;
            opponentsDefeated = 0;
            battleLog.clear();
            currentBetAmount = CasinoConfig.ARENA_ENTRY_FEE;
        }
        
        main.textPanel.addPara(Strings.get("arena.lobby_title"), Color.CYAN);
        main.textPanel.addPara(Strings.get("arena.select_champion"), Color.YELLOW);
        
        for (int i = 0; i < arenaCombatants.size(); i++) {
            SpiralAbyssArena.SpiralGladiator gladiator = arenaCombatants.get(i);

            // Combine all ship name parts into a single paragraph to avoid excessive line breaks
            main.textPanel.setFontInsignia();

            // Create the full ship name string with base odds (pre-battle)
            String shipEntry = (i + 1) + ". ";
            String prefixText = gladiator.prefix + " ";
            String hullNameText = gladiator.hullName + " ";
            String affixText = gladiator.affix;
            String oddsText = " [" + gladiator.getBaseOddsString() + "]";
            String parenText = " (" + gladiator.hullName + ")";

            // Build full text and add as one paragraph
            String fullText = shipEntry + prefixText + hullNameText + affixText + oddsText + parenText;
            main.textPanel.addPara(fullText);

            applyShipHighlighting(gladiator, fullText, oddsText, Color.YELLOW, parenText, Color.GRAY);

            main.options.addOption(gladiator.fullName + " [" + gladiator.getBaseOddsString() + "]", OPTION_ARENA_SELECT_SHIP + i, Color.YELLOW, null);
        }

        main.options.addOption(Strings.get("arena.how_works"), OPTION_HOW_TO_ARENA);
        main.options.addOption(Strings.get("common.back"), OPTION_BACK_MENU);
        main.setState(CasinoInteraction.State.ARENA);
    }

    private void showArenaConfirm(int idx) {
        if (idx < 0 || idx >= arenaCombatants.size()) {
            main.textPanel.addPara(Strings.get("arena.invalid_champion"), Color.RED);
            showArenaLobby();
            return;
        }
        
        main.options.clearOptions();
        SpiralAbyssArena.SpiralGladiator gladiator = arenaCombatants.get(idx);

        chosenChampion = arenaCombatants.get(idx);

        currentBetAmount = 0;
        arenaBets.clear();
        cachedTotalBet = 0;

        main.textPanel.setFontInsignia();

        String championText = Strings.get("arena.selected_champion").replace("%s", "");
        String prefixText = gladiator.prefix + " ";
        String hullNameText = gladiator.hullName + " ";
        String affixText = gladiator.affix;

        String fullText = championText + prefixText + hullNameText + affixText;
        main.textPanel.addPara(fullText);

        applyShipHighlighting(gladiator, fullText);

        main.textPanel.addPara(Strings.get("arena.add_bet"), Color.CYAN);
        
        // Go to add bet menu which loops back to itself until "Start Battle" is chosen
        showAddBetMenu();
    }
    
    private void addIncrementalBet(int additionalAmount) {
        if (isBetInvalid(additionalAmount, "arena_add_bet_menu")) {
            return;
        }

CasinoVIPManager.addToBalance(-additionalAmount);

        float frozenOdds = chosenChampion.getCurrentOdds(currentRound);
        arenaBets.add(new BetInfo(additionalAmount, frozenOdds, chosenChampion, currentRound));
        cachedTotalBet += additionalAmount;

        // main.textPanel.addPara("Added " + additionalAmount + " Stargems to your bet at " + String.format("%.1f", frozenOdds) + "x odds. Total bet: " + getCurrentTotalBet() + " Stargems.", Color.GREEN);
        showAddBetMenu();
    }
    
    private void showVIPPromotionForArena(int requiredAmount, String returnTo) {
        main.getOptions().clearOptions();
        main.textPanel.addPara(Strings.get("vip_promo.insufficient_title"), Color.RED);
        main.textPanel.addPara("");
        main.textPanel.addPara(Strings.get("vip_promo.insufficient_msg"), Color.YELLOW);
        main.textPanel.addPara(Strings.format("vip_promo.current_balance", CasinoVIPManager.getBalance()), Color.GRAY);
        main.textPanel.addPara(Strings.format("vip_promo.required", "", requiredAmount), Color.GRAY);
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

        main.getOptions().addOption(Strings.get("vip_promo.go_topup"), OPTION_TOPUP_MENU);
        main.getOptions().addOption(Strings.get("common.back"), returnTo);
    }
    
    private void showAddBetMenu() {
        main.options.clearOptions();
        main.textPanel.addPara(Strings.get("arena.add_to_bet"), Color.YELLOW);
        displayFinancialInfo();
        main.textPanel.addPara(Strings.format("arena.current_bet", getCurrentTotalBet()), Color.CYAN);

        int playerBalance = CasinoVIPManager.getStargems();
        int availableCredit = CasinoVIPManager.getAvailableCredit();

        if (availableCredit <= 0) {
            main.textPanel.addPara(Strings.format("arena.credit_exhausted", MIN_BET_INCREMENT), Color.RED);
            main.textPanel.addPara(Strings.get("gacha.please_topup"), Color.YELLOW);
        } else if (availableCredit < MIN_BET_INCREMENT) {
            main.textPanel.addPara(Strings.format("arena.insufficient_credit", availableCredit, MIN_BET_INCREMENT), Color.RED);
            main.textPanel.addPara(Strings.get("gacha.please_topup"), Color.YELLOW);
        } else {
            boolean hasBetOptions = addBetOptions(playerBalance, availableCredit, "", -1);

            if (!hasBetOptions) {
                main.textPanel.addPara(Strings.format("arena.insufficient_balance", playerBalance), Color.RED);
                main.textPanel.addPara(Strings.get("gacha.please_topup"), Color.YELLOW);
            }
        }

        if (getCurrentTotalBet() > 0) {
            main.options.addOption(Strings.format("arena.start_battle", getCurrentTotalBet()), "arena_start_battle_with_current_bet");
        }

        main.options.addOption(Strings.get("arena.cancel_return"), "arena_lobby");
    }
    
    private void confirmAddBet(int additionalAmount) {
        main.options.clearOptions();
        main.textPanel.addPara(Strings.get("arena.confirm_additional"), Color.YELLOW);
        main.textPanel.addPara(Strings.format("arena.additional_bet", additionalAmount), Color.CYAN);
        main.textPanel.addPara(Strings.format("arena.total_bet_after", getCurrentTotalBet() + additionalAmount), Color.CYAN);
        main.textPanel.addPara(Strings.format("arena.balance_after", CasinoVIPManager.getStargems() - additionalAmount), Color.CYAN);
        
        main.options.addOption(Strings.get("arena.confirm_addition"), "arena_confirm_add_bet_" + additionalAmount);
        main.options.addOption(Strings.get("common.cancel"), "arena_add_bet_menu");
    }

private void startArenaBattle(int chosenIdx) {
        if (chosenIdx < 0 || chosenIdx >= arenaCombatants.size()) {
            main.textPanel.addPara(Strings.get("arena.champion_not_found"), Color.RED);
            showArenaLobby();
            return;
        }

        chosenChampion = arenaCombatants.get(chosenIdx);

        int totalBet = getCurrentTotalBet();

        if (totalBet <= 0) {
            float frozenOdds = chosenChampion.getCurrentOdds(0);
            arenaBets.add(new BetInfo(CasinoConfig.ARENA_ENTRY_FEE, frozenOdds, chosenChampion, 0));
            cachedTotalBet += CasinoConfig.ARENA_ENTRY_FEE;
        }

 opponentsDefeated = 0;
        currentRound = 0;

        simulateArenaStep();
    }
    
    public void startArenaBattleInPlace(ArenaDialogDelegate delegate) {
        if (delegate == null) {
            int chosenIdx = -1;
            for (int i = 0; i < arenaCombatants.size(); i++) {
                if (chosenChampion != null &&
                    arenaCombatants.get(i).fullName.equals(chosenChampion.fullName)) {
                    chosenIdx = i;
                    break;
                }
            }
            if (chosenIdx != -1) {
                startArenaBattle(chosenIdx);
            }
            return;
        }
        
        int totalBet = getCurrentTotalBet();
        
        if (totalBet <= 0 && chosenChampion != null) {
            float frozenOdds = chosenChampion.getCurrentOdds(0);
            arenaBets.add(new BetInfo(CasinoConfig.ARENA_ENTRY_FEE, frozenOdds, chosenChampion, 0));
            cachedTotalBet += CasinoConfig.ARENA_ENTRY_FEE;
        }

        opponentsDefeated = 0;
        currentRound = 0;
        
        simulateArenaStepInPlace(delegate);
    }
    
    private void showArenaVisualPanel() {
        if (currentDelegate == null) {
            currentDelegate = new ArenaDialogDelegate(
                arenaCombatants, currentRound, getCurrentTotalBet(), arenaBets, battleLog,
                main.getDialog(), null, this::handleArenaPanelDismissed, this
            );
            activeDialogDelegate = currentDelegate;
            main.getDialog().showCustomVisualDialog(1000f, 700f, currentDelegate);
        } else {
            currentDelegate.updateForBattle(arenaCombatants, currentRound, getCurrentTotalBet(), arenaBets, battleLog);
        }
    }
    
private ArenaDialogDelegate activeDialogDelegate = null;

    private void handleArenaPanelDismissed() {
        if (activeDialogDelegate == null) return;
        
        ArenaDialogDelegate triggeredDelegate = activeDialogDelegate;
        
        if (triggeredDelegate.getPendingSuspend()) {
            suspendArena();
            return;
        }
        
        if (triggeredDelegate.getPendingLeave()) {
            activeDialogDelegate = null;
            currentDelegate = null;
            if (!hasSuspendedArena()) {
                clearSuspendedArenaMemory();
            }
            resetArenaState();
            main.showMenu();
            return;
        }
        
        activeDialogDelegate = null;
        currentDelegate = null;
        main.showMenu();
    }

private boolean simulateArenaStep() {
        List<String> logEntries = activeArena.simulateStep(arenaCombatants, currentRound);
        
        activeArena.invalidateOddsCache();

        currentRound++;

        int aliveCount = 0;
        for (SpiralAbyssArena.SpiralGladiator gladiator : arenaCombatants) {
            if (!gladiator.isDead) {
                gladiator.turnsSurvived++;
                aliveCount++;
            }
        }

        if (aliveCount <= 1) {
            finishArenaBattle();
            return false;
        }

    battleLog.addAll(logEntries);
        
        showArenaVisualPanel();
        return true;
    }

    private void finishArenaBattle() {
        SpiralAbyssArena.SpiralGladiator actualWinner = findWinner();
        calculateFinalPositions(actualWinner);
        Set<SpiralAbyssArena.SpiralGladiator> betShips = collectBetShips();

        RewardCalculation rewards = calculateRewards(betShips);

        CasinoVIPManager.addToBalance(rewards.totalWinReward + rewards.totalConsolationReward);

        int winnerIndex = -1;
        for (int i = 0; i < arenaCombatants.size(); i++) {
            if (arenaCombatants.get(i) == actualWinner) {
                winnerIndex = i;
                break;
            }
        }

        battleEnded = true;
        finalWinnerIndex = winnerIndex;
        finalReward = rewards.totalWinReward + rewards.totalConsolationReward;

        ArenaPanelUI.RewardBreakdown breakdown = buildRewardBreakdown(betShips, rewards);

        if (currentDelegate != null) {
            currentDelegate.setBattleEnded(winnerIndex, finalReward, breakdown, currentRound);
        } else {
            currentDelegate = new ArenaDialogDelegate(
                arenaCombatants, currentRound, getCurrentTotalBet(), arenaBets, battleLog,
                main.getDialog(), null, this::handleArenaPanelDismissed, this
            );
            activeDialogDelegate = currentDelegate;
            currentDelegate.setBattleEnded(winnerIndex, finalReward, breakdown, currentRound);
            main.getDialog().showCustomVisualDialog(1000f, 700f, currentDelegate);
        }
    }

    private ArenaPanelUI.RewardBreakdown buildRewardBreakdown(Set<SpiralAbyssArena.SpiralGladiator> betShips, RewardCalculation rewards) {
        ArenaPanelUI.RewardBreakdown breakdown = new ArenaPanelUI.RewardBreakdown();
        breakdown.totalBet = rewards.totalBet;
        breakdown.winReward = rewards.totalWinReward;
        breakdown.consolationReward = rewards.totalConsolationReward;
        breakdown.totalReward = rewards.totalWinReward + rewards.totalConsolationReward;
        breakdown.netResult = breakdown.totalReward - rewards.totalBet;

        for (SpiralAbyssArena.SpiralGladiator ship : betShips) {
            int betAmount = getBetAmountForShip(ship);
            int shipReward;

            if (!ship.isDead) {
                shipReward = calculateWinReward(ship);
            } else {
                shipReward = calculateConsolationReward(ship);
            }

            ArenaPanelUI.RewardBreakdown.ShipRewardInfo shipInfo = new ArenaPanelUI.RewardBreakdown.ShipRewardInfo(
                ship.hullName, betAmount, ship.kills, ship.finalPosition, !ship.isDead, shipReward
            );
            
            for (BetInfo bet : arenaBets) {
                if (bet.ship == ship) {
                    shipInfo.betDetails.add(new ArenaPanelUI.RewardBreakdown.ShipRewardInfo.BetDetail(
                        bet.roundPlaced, bet.multiplier, bet.amount
                    ));
                }
            }
            
            breakdown.shipRewards.add(shipInfo);
        }

        return breakdown;
    }
    
    protected boolean battleEnded = false;
    protected int finalWinnerIndex = -1;
    protected int finalReward = 0;

    public void simulateArenaStepInPlace(ArenaDialogDelegate delegate) {
        if (delegate == null) {
            return;
        }
        
        if (battleEnded) {
            startNewArenaMatchInPlace(delegate);
            return;
        }
        
        List<String> logEntries = activeArena.simulateStep(arenaCombatants, currentRound);
        
        activeArena.invalidateOddsCache();

        currentRound++;

        int aliveCount = 0;
        for (SpiralAbyssArena.SpiralGladiator gladiator : arenaCombatants) {
            if (!gladiator.isDead) {
                gladiator.turnsSurvived++;
                aliveCount++;
            }
        }

        battleLog.addAll(logEntries);

        if (aliveCount <= 1) {
            finishArenaBattle();
        } else {
            delegate.updateForBattle(arenaCombatants, currentRound, getCurrentTotalBet(), arenaBets, battleLog);
            delegate.getArenaPanel().startLogAnimation(logEntries);
        }
    }
    
    public void simulateAllRemainingStepsInPlace(ArenaDialogDelegate delegate) {
        if (delegate == null) {
            return;
        }
        
        if (battleEnded) {
            startNewArenaMatchInPlace(delegate);
            return;
        }
        
        boolean result;
        do {
            List<String> logEntries = activeArena.simulateStep(arenaCombatants, currentRound);
            
            activeArena.invalidateOddsCache();

            currentRound++;

            int aliveCount = 0;
            for (SpiralAbyssArena.SpiralGladiator gladiator : arenaCombatants) {
                if (!gladiator.isDead) {
                    gladiator.turnsSurvived++;
                    aliveCount++;
                }
            }

            battleLog.addAll(logEntries);
            
            result = aliveCount > 1;
        } while (result);
        
        finishArenaBattle();
    }
    
    public void startNewArenaMatchInPlace(ArenaDialogDelegate delegate) {
        if (delegate == null) {
            startNewArenaMatch();
            return;
        }
        
        cachedTotalBet = 0;
        arenaBets.clear();
        
        activeArena = new SpiralAbyssArena();
        arenaCombatants = activeArena.generateCombatants(new CasinoGachaManager());
        
        // DEBUG: Log generated combatants
        Global.getLogger(this.getClass()).info("=== NEW ARENA MATCH ===");
        Global.getLogger(this.getClass()).info("Generated " + arenaCombatants.size() + " combatants:");
        for (int i = 0; i < arenaCombatants.size(); i++) {
            SpiralAbyssArena.SpiralGladiator g = arenaCombatants.get(i);
            Global.getLogger(this.getClass()).info("  [" + i + "] " + g.fullName + " (" + g.hullId + ") HP:" + g.hp + "/" + g.maxHp);
        }
        
        chosenChampion = null;
        opponentsDefeated = 0;
        currentRound = 0;
        currentBetAmount = CasinoConfig.ARENA_ENTRY_FEE;
        battleLog.clear();
        battleEnded = false;
        finalWinnerIndex = -1;
        finalReward = 0;
        
        clearSuspendedArenaMemory();
        
        delegate.resetForNewMatch(arenaCombatants, currentRound, getCurrentTotalBet(), arenaBets, battleLog);
    }

    private void calculateFinalPositions(SpiralAbyssArena.SpiralGladiator winner) {
        if (winner != null) {
            winner.finalPosition = 0;
        }
        
        List<SpiralAbyssArena.SpiralGladiator> deadShips = new ArrayList<>();
        for (SpiralAbyssArena.SpiralGladiator g : arenaCombatants) {
            if (g.isDead) {
                deadShips.add(g);
            }
        }
        
        deadShips.sort((a, b) -> Integer.compare(b.turnsSurvived, a.turnsSurvived));
        
        int position = 1;
        int lastTurnsSurvived = -1;
        for (SpiralAbyssArena.SpiralGladiator ship : deadShips) {
            if (lastTurnsSurvived != -1 && ship.turnsSurvived != lastTurnsSurvived) {
                position++;
            }
            ship.finalPosition = position;
            lastTurnsSurvived = ship.turnsSurvived;
        }
    }

    private SpiralAbyssArena.SpiralGladiator findWinner() {
        for (SpiralAbyssArena.SpiralGladiator gladiator : arenaCombatants) {
            if (!gladiator.isDead) {
                return gladiator;
            }
        }
        return null;
    }

    private Set<SpiralAbyssArena.SpiralGladiator> collectBetShips() {
        Set<SpiralAbyssArena.SpiralGladiator> betShips = new HashSet<>();
        for (BetInfo bet : arenaBets) {
            if (bet.ship != null) {
                betShips.add(bet.ship);
            }
        }
        return betShips;
    }

    @SuppressWarnings("unused")
    private static class RewardCalculation {
        int totalWinReward;
        int totalConsolationReward;
        int totalBet;
        boolean anyWinner;
        boolean anyDefeated;
    }

    private RewardCalculation calculateRewards(Set<SpiralAbyssArena.SpiralGladiator> betShips) {
        RewardCalculation result = new RewardCalculation();
        result.totalBet = getCurrentTotalBet();

        for (SpiralAbyssArena.SpiralGladiator ship : betShips) {
            if (!ship.isDead) {
                result.totalWinReward += calculateWinReward(ship);
                result.anyWinner = true;
            } else {
                result.totalConsolationReward += calculateConsolationReward(ship);
                result.anyDefeated = true;
            }
        }

        return result;
    }

    private int calculateWinReward(SpiralAbyssArena.SpiralGladiator ship) {
        int reward = 0;

        for (BetInfo bet : arenaBets) {
            if (bet.ship == ship) {
                float performanceMultiplier = calculatePerformanceMultiplier(ship, bet.roundPlaced);
                float effectiveMultiplier = calculateEffectiveMultiplier(bet.multiplier, performanceMultiplier);
                reward += (int)(bet.amount * effectiveMultiplier);
            }
        }
        return reward;
    }

    private int calculateConsolationReward(SpiralAbyssArena.SpiralGladiator ship) {
        int reward = 0;

        float positionFactor = SpiralAbyssArena.getPositionFactor(ship.finalPosition);

        for (BetInfo bet : arenaBets) {
            if (bet.ship == ship) {
                float baseConsolation = CasinoConfig.ARENA_CONSOLATION_BASE * positionFactor;
                
                float killBonusFactor = calculateKillBonusFactor(bet.roundPlaced, ship.kills);
                float consolationRate = baseConsolation + killBonusFactor;
                
                reward += (int)(bet.amount * consolationRate);
            }
        }
        return reward;
    }
    
    /**
     * Calculates the kill bonus factor for consolation.
     * Kill bonus only applies to bets placed before battle started (round 0).
     * 
     * @param roundPlaced The round when the bet was placed
     * @param kills The number of kills the ship achieved
     * @return The kill bonus factor to add to consolation rate
     */
    private float calculateKillBonusFactor(int roundPlaced, int kills) {
        if (kills <= 0 || roundPlaced > 0) return 0.0f;
        
        return CasinoConfig.ARENA_KILL_BONUS_FLAT * kills;
    }

    private void startNewArenaMatch() {
        cachedTotalBet = 0;
        arenaBets.clear();
        
        activeArena = new SpiralAbyssArena();
        arenaCombatants = activeArena.generateCombatants(new CasinoGachaManager());
        
        // DEBUG: Log generated combatants
        Global.getLogger(this.getClass()).info("=== NEW ARENA MATCH (startNewArenaMatch) ===");
        Global.getLogger(this.getClass()).info("Generated " + arenaCombatants.size() + " combatants:");
        for (int i = 0; i < arenaCombatants.size(); i++) {
            SpiralAbyssArena.SpiralGladiator g = arenaCombatants.get(i);
            Global.getLogger(this.getClass()).info("  [" + i + "] " + g.fullName + " (" + g.hullId + ") HP:" + g.hp + "/" + g.maxHp);
        }
        
        chosenChampion = null;
        opponentsDefeated = 0;
        currentRound = 0;
        currentBetAmount = CasinoConfig.ARENA_ENTRY_FEE;
        battleLog.clear();
        battleEnded = false;
        finalWinnerIndex = -1;
        finalReward = 0;
        currentDelegate = null;
        activeDialogDelegate = null;

        clearSuspendedArenaMemory();

        showArenaVisualPanel();
    }

    private void resetArenaState() {
        returnActiveBets();
        
        activeArena = null;
        arenaCombatants = null;
        chosenChampion = null;
        opponentsDefeated = 0;
        arenaBets.clear();
        cachedTotalBet = 0;
        currentBetAmount = CasinoConfig.ARENA_ENTRY_FEE;
        battleLog.clear();
        battleEnded = false;
        finalWinnerIndex = -1;
        finalReward = 0;
        currentDelegate = null;
        activeDialogDelegate = null;
    }

    private void returnActiveBets() {
        if (cachedTotalBet > 0) {
            CasinoVIPManager.addToBalance(cachedTotalBet);
            main.textPanel.addPara(Strings.format("arena_results.bet_returned", cachedTotalBet), Color.GREEN);
        }
    }

    private void clearSuspendedArenaMemory() {
        com.fs.starfarer.api.campaign.rules.MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        mem.unset(MEM_SUSPENDED_GAME_TYPE);
        mem.unset(MEM_ARENA_CURRENT_ROUND);
        mem.unset(MEM_ARENA_OPPONENTS_DEFEATED);
        mem.unset(MEM_ARENA_COMBATANT_COUNT);
        mem.unset(MEM_ARENA_SUSPEND_TIME);
        mem.unset(MEM_ARENA_BETS_COUNT);
        
        // Clear combatant data (up to reasonable max)
        for (int i = 0; i < 10; i++) {
            if (mem.getString(MEM_ARENA_COMBATANT_PREFIX + i + "_hull_id") == null) {
                break;
            }
            mem.unset(MEM_ARENA_COMBATANT_PREFIX + i + "_hull_id");
            mem.unset(MEM_ARENA_COMBATANT_PREFIX + i + "_prefix");
            mem.unset(MEM_ARENA_COMBATANT_PREFIX + i + "_hull_name");
            mem.unset(MEM_ARENA_COMBATANT_PREFIX + i + "_affix");
            mem.unset(MEM_ARENA_COMBATANT_PREFIX + i + "_hp");
            mem.unset(MEM_ARENA_COMBATANT_PREFIX + i + "_max_hp");
            mem.unset(MEM_ARENA_COMBATANT_PREFIX + i + "_power");
            mem.unset(MEM_ARENA_COMBATANT_PREFIX + i + "_agility");
            mem.unset(MEM_ARENA_COMBATANT_PREFIX + i + "_bravery");
            mem.unset(MEM_ARENA_COMBATANT_PREFIX + i + "_is_dead");
            mem.unset(MEM_ARENA_COMBATANT_PREFIX + i + "_kills");
            mem.unset(MEM_ARENA_COMBATANT_PREFIX + i + "_turns_survived");
            mem.unset(MEM_ARENA_COMBATANT_PREFIX + i + "_base_odds");
        }
        
        // Clear bet data
        for (int i = 0; i < 30; i++) {
            if (!mem.contains("$ipc_arena_bet_" + i + "_amount")) {
                break;
            }
            mem.unset("$ipc_arena_bet_" + i + "_amount");
            mem.unset("$ipc_arena_bet_" + i + "_multiplier");
            mem.unset("$ipc_arena_bet_" + i + "_round_placed");
            mem.unset("$ipc_arena_bet_" + i + "_ship_name");
        }
    }

    private void showBetAmountSelection(int championIndex) {
        if (championIndex < 0 || championIndex >= arenaCombatants.size()) {
            main.textPanel.addPara(Strings.get("arena.invalid_selection"), Color.RED);
            showArenaVisualPanel();
            return;
        }

        SpiralAbyssArena.SpiralGladiator selectedChampion = arenaCombatants.get(championIndex);
        
        main.getOptions().clearOptions();
        main.getTextPanel().addPara(Strings.format("arena.add_bet_on", selectedChampion.fullName), Color.YELLOW);
        
        // Show champion details with proper color coding
        main.textPanel.setFontInsignia();
        String prefixText = selectedChampion.prefix + " ";
        String hullNameText = selectedChampion.hullName + " ";
        String affixText = selectedChampion.affix;
        String currentOddsStr = selectedChampion.getCurrentOddsString(currentRound);
        String baseOddsStr = selectedChampion.getBaseOddsString();
        String oddsText = " [" + currentOddsStr + " (base: " + baseOddsStr + ")]";
        String hpText = " " + selectedChampion.hp + "/" + selectedChampion.maxHp + " HP";

        String fullText = prefixText + hullNameText + affixText + oddsText + hpText;
        main.textPanel.addPara(fullText);

        applyShipHighlighting(selectedChampion, fullText, currentOddsStr, Color.YELLOW,
            selectedChampion.hp + "/" + selectedChampion.maxHp + " HP", Color.CYAN);

        main.textPanel.addPara(Strings.get("arena.odds_factor_hp"), Color.GRAY);
        main.textPanel.addPara(Strings.get("arena.bet_locked_odds"), Color.GRAY);
        
        displayFinancialInfo();
        main.getTextPanel().addPara(Strings.format("arena.current_bet", getCurrentTotalBet()), Color.CYAN);

        int playerBalance = CasinoVIPManager.getBalance();
        int availableCredit = CasinoVIPManager.getAvailableCredit();

        if (availableCredit <= 0) {
            main.textPanel.addPara(Strings.format("arena.credit_exhausted", MIN_BET_INCREMENT), Color.RED);
            main.textPanel.addPara(Strings.get("gacha.please_topup"), Color.YELLOW);
        } else if (availableCredit < MIN_BET_INCREMENT) {
            main.textPanel.addPara(Strings.format("arena.insufficient_credit", availableCredit, MIN_BET_INCREMENT), Color.RED);
            main.textPanel.addPara(Strings.get("gacha.please_topup"), Color.YELLOW);
        } else {
            boolean hasBetOptions = addBetOptions(playerBalance, availableCredit, OPTION_ARENA_CONFIRM_ADD_BET_TO_CHAMP, championIndex);

            if (!hasBetOptions) {
                main.textPanel.addPara(Strings.format("arena.insufficient_balance", playerBalance), Color.RED);
                main.textPanel.addPara(Strings.get("gacha.please_topup"), Color.YELLOW);
            }
        }

        main.getOptions().addOption(Strings.get("arena.back_to_battle"), OPTION_ARENA_ADD_ANOTHER_BET);
        main.getOptions().addOption(Strings.get("arena.arena_rules"), "how_to_arena_arena_add_another_bet");
    }

    private void showAddAnotherBetMenu() {
        main.getOptions().clearOptions();
        main.getTextPanel().addPara(Strings.get("arena.select_champion_bet"), Color.YELLOW);
        main.textPanel.addPara(Strings.get("arena.odds_dynamic"), Color.GRAY);
        main.textPanel.addPara(Strings.get("arena.higher_hp_worse_odds"), Color.GRAY);
        displayFinancialInfo();
        main.getTextPanel().addPara(Strings.format("arena.current_bet", getCurrentTotalBet()), Color.CYAN);
        
        for (int i = 0; i < arenaCombatants.size(); i++) {
            SpiralAbyssArena.SpiralGladiator gladiator = arenaCombatants.get(i);
            if (!gladiator.isDead) {
                main.textPanel.setFontInsignia();

                String prefixText = gladiator.prefix + " ";
                String hullNameText = gladiator.hullName + " ";
                String affixText = gladiator.affix;
                String currentOdds = gladiator.getCurrentOddsString(currentRound);
                String baseOdds = gladiator.getBaseOddsString();
                String oddsText = " [" + currentOdds + " (base: " + baseOdds + ")]";
                String hpText = " " + gladiator.hp + "/" + gladiator.maxHp + " HP";

                String fullText = prefixText + hullNameText + affixText + oddsText + hpText;
                main.textPanel.addPara(fullText);

                applyShipHighlighting(gladiator, fullText, currentOdds, Color.YELLOW,
                    gladiator.hp + "/" + gladiator.maxHp + " HP", Color.CYAN);

                main.getOptions().addOption(Strings.format("arena_errors.bet_on", gladiator.prefix, gladiator.hullName, gladiator.affix, currentOdds), OPTION_ARENA_SELECT_CHAMPION_FOR_BET + i, Color.YELLOW, null);
            }
        }

        main.getOptions().addOption(Strings.get("arena.back_to_battle"), OPTION_ARENA_STATUS);
        main.getOptions().addOption(Strings.get("arena.arena_rules"), "how_to_arena_arena_add_another_bet");
    }
    
    private int getCurrentTotalBet() {
        return cachedTotalBet;
    }

private void performAddBetToChampion(int championIndex, int additionalAmount) {
        if (championIndex < 0 || championIndex >= arenaCombatants.size()) {
            main.textPanel.addPara(Strings.get("arena.invalid_selection"), Color.RED);
            showArenaVisualPanel();
            return;
        }

        if (isBetInvalid(additionalAmount, "arena_status")) {
            return;
        }

        SpiralAbyssArena.SpiralGladiator targetChampion = arenaCombatants.get(championIndex);
        if (targetChampion.isDead) {
            main.getTextPanel().addPara(Strings.format("arena_errors.champion_defeated_bet", targetChampion.fullName), Color.RED);
            showArenaVisualPanel();
            return;
        }

        int currentBetOnShip = getBetAmountForShip(targetChampion);
        if (currentBetOnShip + additionalAmount > CasinoConfig.ARENA_MAX_BET_PER_CHAMPION) {
            int maxAllowed = CasinoConfig.ARENA_MAX_BET_PER_CHAMPION - currentBetOnShip;
            main.getTextPanel().addPara(Strings.format("arena.max_bet_per_champion", CasinoConfig.ARENA_MAX_BET_PER_CHAMPION, targetChampion.fullName, currentBetOnShip, maxAllowed), Color.RED);
            showArenaVisualPanel();
            return;
        }

        CasinoVIPManager.addToBalance(-additionalAmount);
        float frozenOdds = targetChampion.getCurrentOdds(currentRound);
        arenaBets.add(new BetInfo(additionalAmount, frozenOdds, targetChampion, currentRound));
        cachedTotalBet += additionalAmount;

        if (chosenChampion == null) {
            chosenChampion = targetChampion;
        }

        showArenaVisualPanel();
    }
    
    public void performAddBetToChampionInPlace(ArenaDialogDelegate delegate, int championIndex, int additionalAmount) {
        if (delegate == null) {
            performAddBetToChampion(championIndex, additionalAmount);
            return;
        }
        
        if (championIndex < 0 || championIndex >= arenaCombatants.size()) {
            delegate.showErrorMessage(Strings.get("arena.invalid_selection"));
            return;
        }

        SpiralAbyssArena.SpiralGladiator targetChampion = arenaCombatants.get(championIndex);
        if (targetChampion.isDead) {
            delegate.showErrorMessage(Strings.format("arena.champion_defeated", targetChampion.fullName));
            return;
        }

        int currentBetOnShip = getBetAmountForShip(targetChampion);
        if (currentBetOnShip + additionalAmount > CasinoConfig.ARENA_MAX_BET_PER_CHAMPION) {
            int maxAllowed = CasinoConfig.ARENA_MAX_BET_PER_CHAMPION - currentBetOnShip;
            delegate.showErrorMessage(Strings.format("arena.max_bet_reached", CasinoConfig.ARENA_MAX_BET_PER_CHAMPION, maxAllowed));
            return;
        }

        CasinoVIPManager.addToBalance(-additionalAmount);
        float frozenOdds = targetChampion.getCurrentOdds(currentRound);
        arenaBets.add(new BetInfo(additionalAmount, frozenOdds, targetChampion, currentRound));
        cachedTotalBet += additionalAmount;

        if (chosenChampion == null) {
            chosenChampion = targetChampion;
        }
        
        delegate.updateForBattle(arenaCombatants, currentRound, getCurrentTotalBet(), arenaBets, battleLog);
    }
    
    private int getBetAmountForShip(SpiralAbyssArena.SpiralGladiator ship) {
        int total = 0;
        for (BetInfo bet : arenaBets) {
            if (bet.ship == ship) {
                total += bet.amount;
            }
        }
        return total;
    }

    private float calculatePerformanceMultiplier(SpiralAbyssArena.SpiralGladiator ship, int roundPlaced) {
        float survivalBonusMult = 1.0f;
        if (roundPlaced == 0) {
            survivalBonusMult = 1.0f + (ship.turnsSurvived * CasinoConfig.ARENA_SURVIVAL_BONUS_PER_TURN);
        }
        
        float killBonusMult = 1.0f;
        if (roundPlaced == 0 && ship.kills > 0) {
            killBonusMult = 1.0f + (ship.kills * CasinoConfig.ARENA_KILL_BONUS_PER_KILL);
        }
        
        return survivalBonusMult * killBonusMult;
    }

    private float calculateEffectiveMultiplier(float frozenOdds, float performanceMultiplier) {
        float effectiveMultiplier = frozenOdds * performanceMultiplier;
        return Math.max(1.01f, effectiveMultiplier);
    }

    private void suspendArena() {
        com.fs.starfarer.api.campaign.rules.MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        mem.set("$ipc_suspended_game_type", "Arena");

        mem.set("$ipc_arena_current_round", currentRound);
        mem.set("$ipc_arena_opponents_defeated", opponentsDefeated);

        if (arenaCombatants != null) {
            for (int i = 0; i < arenaCombatants.size(); i++) {
                SpiralAbyssArena.SpiralGladiator gladiator = arenaCombatants.get(i);
                mem.set(MEM_ARENA_COMBATANT_PREFIX + i + "_hull_id", gladiator.hullId);
                mem.set(MEM_ARENA_COMBATANT_PREFIX + i + "_prefix", gladiator.prefix);
                mem.set(MEM_ARENA_COMBATANT_PREFIX + i + "_hull_name", gladiator.hullName);
                mem.set(MEM_ARENA_COMBATANT_PREFIX + i + "_affix", gladiator.affix);
                mem.set(MEM_ARENA_COMBATANT_PREFIX + i + "_hp", gladiator.hp);
                mem.set(MEM_ARENA_COMBATANT_PREFIX + i + "_max_hp", gladiator.maxHp);
                mem.set(MEM_ARENA_COMBATANT_PREFIX + i + "_power", gladiator.power);
                mem.set(MEM_ARENA_COMBATANT_PREFIX + i + "_agility", gladiator.agility);
                mem.set(MEM_ARENA_COMBATANT_PREFIX + i + "_bravery", gladiator.bravery);
                mem.set(MEM_ARENA_COMBATANT_PREFIX + i + "_is_dead", gladiator.isDead);
                mem.set(MEM_ARENA_COMBATANT_PREFIX + i + "_kills", gladiator.kills);
                mem.set(MEM_ARENA_COMBATANT_PREFIX + i + "_turns_survived", gladiator.turnsSurvived);
                mem.set(MEM_ARENA_COMBATANT_PREFIX + i + "_base_odds", gladiator.baseOdds);
            }
            mem.set(MEM_ARENA_COMBATANT_COUNT, arenaCombatants.size());
        }
        
        // Save bets
        mem.set(MEM_ARENA_BETS_COUNT, arenaBets.size());
        for (int i = 0; i < arenaBets.size(); i++) {
            BetInfo bet = arenaBets.get(i);
            mem.set("$ipc_arena_bet_" + i + "_amount", bet.amount);
            mem.set("$ipc_arena_bet_" + i + "_multiplier", bet.multiplier);
            mem.set("$ipc_arena_bet_" + i + "_round_placed", bet.roundPlaced);
            if (bet.ship != null) {
                mem.set("$ipc_arena_bet_" + i + "_ship_name", bet.ship.fullName);
            }
        }

        // Store the time when arena was suspended for the joke
        mem.set(MEM_ARENA_SUSPEND_TIME, Global.getSector().getClock().getTimestamp());

        main.getTextPanel().addPara(Strings.get("arena_suspend.stand_up"), Color.YELLOW);
        main.getTextPanel().addPara(Strings.get("arena_suspend.announcer_pause"), Color.CYAN);
        main.getTextPanel().addPara(Strings.get("arena_suspend.combatants_wait"), Color.GRAY);
        main.getTextPanel().addPara(Strings.get("arena_suspend.other_matches"), Color.GRAY);
        main.getOptions().clearOptions();
        main.getOptions().addOption(Strings.get("common.leave"), OPTION_ARENA_LEAVE_NOW);
    }

    private void restoreSuspendedArena() {
        com.fs.starfarer.api.campaign.rules.MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();

        if (!mem.contains(MEM_ARENA_SUSPEND_TIME) || !mem.contains(MEM_ARENA_COMBATANT_COUNT)) {
            clearSuspendedArenaMemory();
            main.getTextPanel().addPara(Strings.get("errors.corrupted_arena_data"), Color.RED);
            showArenaLobby();
            return;
        }

        long suspendTime = mem.getLong(MEM_ARENA_SUSPEND_TIME);
        float daysAway = Global.getSector().getClock().getElapsedDaysSince(suspendTime);

        currentRound = mem.getInt(MEM_ARENA_CURRENT_ROUND);
        opponentsDefeated = mem.getInt(MEM_ARENA_OPPONENTS_DEFEATED);

        int combatantCount = mem.getInt(MEM_ARENA_COMBATANT_COUNT);
        if (combatantCount > 0) {
            arenaCombatants = new ArrayList<>();
            for (int i = 0; i < combatantCount; i++) {
                String prefix = mem.getString(MEM_ARENA_COMBATANT_PREFIX + i + "_prefix");
                String hullId = mem.getString(MEM_ARENA_COMBATANT_PREFIX + i + "_hull_id");
                if (hullId == null) {
                    hullId = "";
                }
                String hullName = mem.getString(MEM_ARENA_COMBATANT_PREFIX + i + "_hull_name");
                String affix = mem.getString(MEM_ARENA_COMBATANT_PREFIX + i + "_affix");
                int hp = mem.getInt(MEM_ARENA_COMBATANT_PREFIX + i + "_hp");
                int maxHp = mem.getInt(MEM_ARENA_COMBATANT_PREFIX + i + "_max_hp");
                int power = mem.getInt(MEM_ARENA_COMBATANT_PREFIX + i + "_power");
                float agility = mem.getFloat(MEM_ARENA_COMBATANT_PREFIX + i + "_agility");
                float bravery = mem.getFloat(MEM_ARENA_COMBATANT_PREFIX + i + "_bravery");

                SpiralAbyssArena.SpiralGladiator gladiator = new SpiralAbyssArena.SpiralGladiator(hullId, prefix, hullName, affix, maxHp, power, agility, bravery);
                gladiator.hp = hp;
                gladiator.isDead = mem.getBoolean(MEM_ARENA_COMBATANT_PREFIX + i + "_is_dead");
                gladiator.kills = mem.getInt(MEM_ARENA_COMBATANT_PREFIX + i + "_kills");
                gladiator.turnsSurvived = mem.getInt(MEM_ARENA_COMBATANT_PREFIX + i + "_turns_survived");
                gladiator.baseOdds = mem.getFloat(MEM_ARENA_COMBATANT_PREFIX + i + "_base_odds");
                arenaCombatants.add(gladiator);
            }
        }

        int betsCount = mem.getInt(MEM_ARENA_BETS_COUNT);
        arenaBets.clear();
        cachedTotalBet = 0;
        for (int i = 0; i < betsCount; i++) {
            int amount = mem.getInt("$ipc_arena_bet_" + i + "_amount");
            float multiplier = mem.getFloat("$ipc_arena_bet_" + i + "_multiplier");
            int roundPlaced = mem.getInt("$ipc_arena_bet_" + i + "_round_placed");
            String shipName = mem.getString("$ipc_arena_bet_" + i + "_ship_name");

            SpiralAbyssArena.SpiralGladiator betShip = findGladiatorByName(shipName);

            if (betShip != null) {
                arenaBets.add(new BetInfo(amount, multiplier, betShip, roundPlaced));
                cachedTotalBet += amount;
            }
        }
        
        if (!arenaBets.isEmpty()) {
            chosenChampion = arenaBets.get(0).ship;
        }
        
        if (activeArena == null) {
            activeArena = new SpiralAbyssArena();
        }

        if (daysAway >= 30) {
            main.getTextPanel().addPara(Strings.format("arena_suspend.return_30_days", String.format("%.1f", daysAway)), Color.YELLOW);
        } else if (daysAway >= 7) {
            main.getTextPanel().addPara(Strings.format("arena_suspend.return_7_days", String.format("%.1f", daysAway)), Color.YELLOW);
        } else if (daysAway >= 1) {
            main.getTextPanel().addPara(Strings.format("arena_suspend.return_1_day", String.format("%.1f", daysAway)), Color.YELLOW);
        } else {
            main.getTextPanel().addPara(Strings.format("arena_suspend.return_hours", String.format("%.1f", daysAway * 24)), Color.YELLOW);
        }
        main.getTextPanel().addPara(Strings.get("arena_suspend.crowd_stirs"), Color.CYAN);
        
        main.getOptions().clearOptions();
        main.getOptions().addOption(Strings.get("arena_resume.continue"), "arena_resume_continue");
        main.getOptions().addOption(Strings.get("arena_resume.wait"), "arena_resume_wait");
    }

    private SpiralAbyssArena.SpiralGladiator findGladiatorByName(String fullName) {
        for (SpiralAbyssArena.SpiralGladiator gladiator : arenaCombatants) {
            if (gladiator.fullName.equals(fullName)) {
                return gladiator;
            }
        }
        return null;
    }

    public boolean hasSuspendedArena() {
        com.fs.starfarer.api.campaign.rules.MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        String suspendedGameType = mem.getString(MEM_SUSPENDED_GAME_TYPE);
        return "Arena".equals(suspendedGameType);
    }
}