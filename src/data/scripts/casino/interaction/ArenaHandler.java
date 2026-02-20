package data.scripts.casino.interaction;

import com.fs.starfarer.api.Global;
import data.scripts.casino.CasinoConfig;
import data.scripts.casino.CasinoGachaManager;
import data.scripts.casino.CasinoVIPManager;
import data.scripts.casino.SpiralAbyssArena;
import data.scripts.casino.util.LogFormatter;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;
import java.util.function.Predicate;

/**
 * Handles all arena-related interactions for the casino mod.
 * Manages champion selection, betting, battle simulation, and reward calculation.
 */
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
    
    protected SpiralAbyssArena activeArena;
    protected List<SpiralAbyssArena.SpiralGladiator> arenaCombatants;
    protected SpiralAbyssArena.SpiralGladiator chosenChampion;
    protected int opponentsDefeated;
    protected int currentRound;
    protected int currentBetAmount = CasinoConfig.ARENA_ENTRY_FEE;
    protected int cachedTotalBet = 0;
    
    /**
     * Represents a bet placed on a champion in the arena.
     * Tracks the bet amount, multiplier at time of bet, target ship, and round placed.
     */
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
    
    protected List<BetInfo> arenaBets = new ArrayList<>();
    
    private final Map<String, OptionHandler> handlers = new HashMap<>();
    private final Map<Predicate<String>, OptionHandler> predicateHandlers = new HashMap<>();

    public ArenaHandler(CasinoInteraction main) {
        this.main = main;
        initializeHandlers();
    }
    
    private void initializeHandlers() {
        handlers.put(OPTION_ARENA_LOBBY, option -> showArenaLobby());
        handlers.put("arena_add_bet_menu", option -> showAddBetMenu());
        handlers.put(OPTION_HOW_TO_ARENA, option -> main.help.showArenaHelp());
        handlers.put(OPTION_BACK_MENU, option -> main.showMenu());
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
        handlers.put(OPTION_ARENA_STATUS, option -> showArenaStatus());
        handlers.put(OPTION_ARENA_LEAVE_NOW, option -> main.showMenu());
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
                main.textPanel.addPara("Error: Selected champion not found. Returning to lobby.", Color.RED);
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
        class HighlightInfo {
            final String text;
            final Color color;
            final int position;
            HighlightInfo(String text, Color color, int position) {
                this.text = text;
                this.color = color;
                this.position = position;
            }
        }

        List<HighlightInfo> highlightInfos = new ArrayList<>();

        // Scan for prefixes (strong perks - bright colors)
        for (String prefix : CasinoConfig.ARENA_PREFIX_STRONG_POS) {
            int pos = fullText.indexOf(prefix);
            if (pos >= 0) {
                highlightInfos.add(new HighlightInfo(prefix, PREFIX_POSITIVE_COLOR, pos));
            }
        }
        for (String prefix : CasinoConfig.ARENA_PREFIX_STRONG_NEG) {
            int pos = fullText.indexOf(prefix);
            if (pos >= 0) {
                highlightInfos.add(new HighlightInfo(prefix, PREFIX_NEGATIVE_COLOR, pos));
            }
        }

        // Scan for affixes (weak perks - dimmer colors)
        for (String affix : CasinoConfig.ARENA_AFFIX_POS) {
            int pos = fullText.indexOf(affix);
            if (pos >= 0) {
                highlightInfos.add(new HighlightInfo(affix, AFFIX_POSITIVE_COLOR, pos));
            }
        }
        for (String affix : CasinoConfig.ARENA_AFFIX_NEG) {
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

    /**
     * Adds bet amount options to the menu based on player's available balance and credit.
     * Includes fixed amounts (100, 500, 2000) and percentage-based options (10%, 50%).
     */
    private boolean addBetOptions(int playerBalance, int availableCredit, String optionPrefix, int championIndex) {
        boolean hasBetOptions = false;
        int referenceAmount = availableCredit > 0 ? availableCredit : playerBalance;
        String percentageLabel = availableCredit > 0 ? "remaining credit" : "account";

        // Add fixed bet amount options
        for (int betAmount : BET_AMOUNTS) {
            if (playerBalance >= betAmount && availableCredit >= betAmount) {
                String optionId = championIndex >= 0
                    ? optionPrefix + championIndex + "_" + betAmount
                    : OPTION_ARENA_ADD_BET + betAmount;
                main.getOptions().addOption("Add " + betAmount + " Stargems", optionId);
                hasBetOptions = true;
            }
        }

        // Add percentage-based options
        int[] percentages = {PERCENT_10, PERCENT_50};
        for (int percent : percentages) {
            int percentAmount = (referenceAmount * percent) / 100;
            if (percentAmount > 0 && playerBalance >= percentAmount && (availableCredit <= 0 || percentAmount <= availableCredit)) {
                String optionId = championIndex >= 0
                    ? optionPrefix + championIndex + "_" + percentAmount
                    : OPTION_ARENA_ADD_BET + percentAmount;
                String label = "Add " + percentAmount + " Stargems (" + percent + "% of " + percentageLabel + ")";
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
                main.textPanel.addPara("Your credit facility is exhausted. You cannot afford this bet of " + amount + " Stargems.", Color.RED);
                main.textPanel.addPara("Please visit Stargem Top-up to purchase more gems.", Color.YELLOW);
                showAddBetMenu();
                return true;
            } else if (availableCredit < amount) {
                main.textPanel.addPara("Your available credit (" + availableCredit + " Stargems) is insufficient for this bet of " + amount + " Stargems.", Color.RED);
                main.textPanel.addPara("Please select a smaller bet amount or visit Stargem Top-up.", Color.YELLOW);
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

    public void showArenaLobby() {
        main.options.clearOptions();

        if (hasSuspendedArena()) {
            restoreSuspendedArena();
            return;
        }

        if (activeArena == null || arenaCombatants == null || arenaCombatants.isEmpty()) {
            activeArena = new SpiralAbyssArena();
            arenaCombatants = activeArena.generateCombatants(new CasinoGachaManager());
            currentBetAmount = CasinoConfig.ARENA_ENTRY_FEE;
        }
        
        main.textPanel.addPara("Spiral Abyss Arena - Today's Match Card", Color.CYAN);
        main.textPanel.addPara("Select a champion to battle with:", Color.YELLOW);
        
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

        main.options.addOption("How it Works", OPTION_HOW_TO_ARENA);
        main.options.addOption("Back", OPTION_BACK_MENU);
        main.setState(CasinoInteraction.State.ARENA);
    }

    private void showArenaConfirm(int idx) {
        // Validate champion index
        if (idx < 0 || idx >= arenaCombatants.size()) {
            main.textPanel.addPara("Error: Invalid champion selection.", Color.RED);
            showArenaLobby();
            return;
        }
        
        main.options.clearOptions();
        SpiralAbyssArena.SpiralGladiator gladiator = arenaCombatants.get(idx);

        // Store the selected champion index temporarily
        chosenChampion = arenaCombatants.get(idx);

        currentBetAmount = 0;
        arenaBets.clear();
        cachedTotalBet = 0;

        main.textPanel.setFontInsignia();

        // Combine champion selection text into a single paragraph
        String championText = "Selected champion: ";
        String prefixText = gladiator.prefix + " ";
        String hullNameText = gladiator.hullName + " ";
        String affixText = gladiator.affix;

        String fullText = championText + prefixText + hullNameText + affixText;
        main.textPanel.addPara(fullText);

        applyShipHighlighting(gladiator, fullText);

        main.textPanel.addPara("Now add your bet amount:", Color.CYAN);
        
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

        main.textPanel.addPara("Added " + additionalAmount + " Stargems to your bet at " + String.format("%.1f", frozenOdds) + "x odds. Total bet: " + getCurrentTotalBet() + " Stargems.", Color.GREEN);
        showAddBetMenu();
    }
    
    private void showVIPPromotionForArena(int requiredAmount, String returnTo) {
        main.getOptions().clearOptions();
        main.textPanel.addPara("INSUFFICIENT STARGEMS", Color.RED);
        main.textPanel.addPara("");
        main.textPanel.addPara("Your Stargem balance is insufficient for this transaction.", Color.YELLOW);
        main.textPanel.addPara("Current Balance: " + CasinoVIPManager.getBalance(), Color.GRAY);
        main.textPanel.addPara("Required: " + requiredAmount + " Stargems", Color.GRAY);
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

        main.getOptions().addOption("Go to Stargem Top-up", OPTION_TOPUP_MENU);
        main.getOptions().addOption("Back", returnTo);
    }
    
    private void showAddBetMenu() {
        main.options.clearOptions();
        main.textPanel.addPara("Add to your bet:", Color.YELLOW);
        displayFinancialInfo();
        main.textPanel.addPara("Current Bet: " + getCurrentTotalBet() + " Stargems", Color.CYAN);

        int playerBalance = CasinoVIPManager.getStargems();
        int availableCredit = CasinoVIPManager.getAvailableCredit();

        if (availableCredit <= 0) {
            main.textPanel.addPara("Your credit facility is exhausted. You cannot afford even the minimum bet of " + MIN_BET_INCREMENT + " Stargems.", Color.RED);
            main.textPanel.addPara("Please visit Stargem Top-up to purchase more gems.", Color.YELLOW);
        } else if (availableCredit < MIN_BET_INCREMENT) {
            main.textPanel.addPara("Your available credit (" + availableCredit + " Stargems) is insufficient for the minimum bet of " + MIN_BET_INCREMENT + " Stargems.", Color.RED);
            main.textPanel.addPara("Please visit Stargem Top-up to purchase more gems.", Color.YELLOW);
        } else {
            boolean hasBetOptions = addBetOptions(playerBalance, availableCredit, "", -1);

            if (!hasBetOptions) {
                main.textPanel.addPara("Your balance (" + playerBalance + " Stargems) is insufficient for any available bet option.", Color.RED);
                main.textPanel.addPara("Please visit Stargem Top-up to purchase more gems.", Color.YELLOW);
            }
        }

        if (getCurrentTotalBet() > 0) {
            main.options.addOption("Start Battle (" + getCurrentTotalBet() + " Stargems)", "arena_start_battle_with_current_bet");
        }

        main.options.addOption("Cancel & Return to Arena Lobby", "arena_lobby");
    }
    
    private void confirmAddBet(int additionalAmount) {
        main.options.clearOptions();
        main.textPanel.addPara("Confirm additional bet amount:", Color.YELLOW);
        main.textPanel.addPara("Additional Bet: " + additionalAmount + " Stargems", Color.CYAN);
        main.textPanel.addPara("Total Bet after addition: " + (getCurrentTotalBet() + additionalAmount) + " Stargems", Color.CYAN);
        main.textPanel.addPara("Balance after bet: " + (CasinoVIPManager.getStargems() - additionalAmount) + " Stargems", Color.CYAN);
        
        main.options.addOption("Confirm Addition", "arena_confirm_add_bet_" + additionalAmount);
        main.options.addOption("Cancel", "arena_add_bet_menu");
    }

    private void startArenaBattle(int chosenIdx) {
        if (chosenIdx < 0 || chosenIdx >= arenaCombatants.size()) {
            main.textPanel.addPara("Error: Invalid champion selection. Returning to lobby.", Color.RED);
            showArenaLobby();
            return;
        }

        chosenChampion = arenaCombatants.get(chosenIdx);

        int totalBet = getCurrentTotalBet();

        if (totalBet <= 0) {
            totalBet = CasinoConfig.ARENA_ENTRY_FEE;
            float frozenOdds = chosenChampion.getCurrentOdds(0);
            arenaBets.add(new BetInfo(CasinoConfig.ARENA_ENTRY_FEE, frozenOdds, chosenChampion, 0));
            cachedTotalBet += CasinoConfig.ARENA_ENTRY_FEE;
        }

        opponentsDefeated = 0;
        currentRound = 0;

        main.textPanel.setFontInsignia();
        main.textPanel.addPara("The battle begins! Your champions enter the arena:", Color.CYAN);

        Set<SpiralAbyssArena.SpiralGladiator> betShips = new HashSet<>();
        for (BetInfo bet : arenaBets) {
            if (bet.ship != null) {
                betShips.add(bet.ship);
            }
        }

        for (SpiralAbyssArena.SpiralGladiator ship : betShips) {
            String prefixText = ship.prefix + " ";
            String hullNameText = ship.hullName + " ";
            String affixText = ship.affix;
            String betText = " (Bet: " + getBetAmountForShip(ship) + " Stargems)";
            String betAmountStr = getBetAmountForShip(ship) + " Stargems";

            String fullText = "  - " + prefixText + hullNameText + affixText + betText;
            main.textPanel.addPara(fullText);

            applyShipHighlighting(ship, fullText, betAmountStr, Color.YELLOW);
        }
        
        main.textPanel.addPara("Total Bet: " + totalBet + " Stargems", Color.YELLOW);
        
        simulateArenaStep();
    }

    private boolean simulateArenaStep() {
        List<String> logEntries = activeArena.simulateStep(arenaCombatants, currentRound);

        currentRound++;

        int aliveCount = 0;
        for (SpiralAbyssArena.SpiralGladiator gladiator : arenaCombatants) {
            if (!gladiator.isDead) {
                gladiator.turnsSurvived++;
                aliveCount++;
            }
        }

        if (logEntries.isEmpty() && aliveCount <= 1) {
            finishArenaBattle();
            return false;
        }

        for (String logEntry : logEntries) {
            processLogEntry(logEntry);
        }
        
        showArenaStatus();
        return true;
    }

    private void showArenaStatus() {
        main.getOptions().clearOptions();
        main.getOptions().addOption("Watch Next Round", "arena_watch_next");
        main.getOptions().addOption("Skip to End", "arena_skip");
        main.getOptions().addOption("Add Bet to Champion", "arena_add_another_bet");
        main.getOptions().addOption("Arena Rules", "how_to_arena_arena_status");
        main.getOptions().addOption("Tell Them to Wait (Suspend)", "arena_suspend");
    }

    private void finishArenaBattle() {
        main.getOptions().clearOptions();

        SpiralAbyssArena.SpiralGladiator actualWinner = findWinner();
        calculateFinalPositions(actualWinner);
        Set<SpiralAbyssArena.SpiralGladiator> betShips = collectBetShips();

        RewardCalculation rewards = calculateRewards(betShips);

        displayBattleResults(rewards, betShips);
        displayPerformanceSummary(betShips, rewards.totalBet);
        displayNetResult(rewards);

        resetArenaState();

        main.getOptions().addOption("Return to Lobby", OPTION_ARENA_LOBBY);
        main.getOptions().addOption("Back to Main Menu", OPTION_BACK_MENU);
    }

    /**
     * Calculates final positions for all combatants based on survival time.
     * Winner gets position 0, last eliminated gets position 1, etc.
     * Ships eliminated in the same round share the same position.
     */
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

        float consolationMultiplier = CasinoConfig.ARENA_DEFEATED_CONSOLATION_MULT;

        for (SpiralAbyssArena.SpiralGladiator ship : betShips) {
            if (!ship.isDead) {
                result.totalWinReward += calculateWinReward(ship);
                result.anyWinner = true;
            } else {
                result.totalConsolationReward += calculateConsolationReward(ship, consolationMultiplier);
                result.anyDefeated = true;
            }
        }

        return result;
    }

    private int calculateWinReward(SpiralAbyssArena.SpiralGladiator ship) {
        int reward = 0;
        float performanceMultiplier = calculatePerformanceMultiplier(ship);

        for (BetInfo bet : arenaBets) {
            if (bet.ship == ship) {
                float effectiveMultiplier = calculateEffectiveMultiplier(bet.multiplier, performanceMultiplier, bet.roundPlaced);
                reward += (int)(bet.amount * effectiveMultiplier);
            }
        }
        return reward;
    }

    private int calculateConsolationReward(SpiralAbyssArena.SpiralGladiator ship, float consolationMultiplier) {
        int reward = 0;

        float positionFactor = getPositionFactor(ship.finalPosition);
        float killBonus = 1.0f + (ship.kills * CasinoConfig.ARENA_KILL_BONUS_PER_KILL);

        for (BetInfo bet : arenaBets) {
            if (bet.ship == ship) {
                float frozenOdds = bet.multiplier;
                float diminishingReturns = calculateDiminishingReturns(bet.roundPlaced);
                
                reward += (int)(bet.amount * frozenOdds * positionFactor * killBonus * diminishingReturns * consolationMultiplier);
            }
        }
        return reward;
    }

    /**
     * Gets the position factor for consolation calculation.
     * Position 1 (2nd place) = highest factor, decreases for lower positions.
     */
    private float getPositionFactor(int finalPosition) {
        if (finalPosition <= 0) return 0.0f;
        
        float[] factors = CasinoConfig.ARENA_CONSOLATION_POSITION_FACTORS;
        int index = finalPosition - 1;
        
        if (index < factors.length) {
            return factors[index];
        }
        return factors[factors.length - 1];
    }

    private float calculateDiminishingReturns(int roundPlaced) {
        if (roundPlaced <= 0) return 1.0f;
        float diminishingReturns = 1.0f - (roundPlaced * CasinoConfig.ARENA_DIMINISHING_RETURNS_PER_ROUND);
        return Math.max(CasinoConfig.ARENA_DIMINISHING_RETURNS_MIN, diminishingReturns);
    }

    private void displayBattleResults(RewardCalculation rewards, Set<SpiralAbyssArena.SpiralGladiator> betShips) {
        if (rewards.anyWinner) {
            CasinoVIPManager.addToBalance(rewards.totalWinReward);
            displayVictoryResults(rewards.totalWinReward, betShips);
        } else {
            main.getTextPanel().addPara("DEFEAT. All your champions have been decommissioned.", Color.RED);
        }

        if (rewards.anyDefeated && rewards.totalConsolationReward > 0) {
            CasinoVIPManager.addToBalance(rewards.totalConsolationReward);
            main.getTextPanel().addPara("Consolation Reward: " + rewards.totalConsolationReward + " Stargems", Color.YELLOW);
            main.getTextPanel().addPara("(Based on performance of defeated champions)", Color.GRAY);
        }
    }

    private void displayVictoryResults(int totalWinReward, Set<SpiralAbyssArena.SpiralGladiator> betShips) {
        main.textPanel.setFontInsignia();
        main.textPanel.addPara("VICTORY! Your champions have triumphed!", Color.GREEN);

        for (SpiralAbyssArena.SpiralGladiator ship : betShips) {
            if (!ship.isDead) {
                String prefixText = ship.prefix + " ";
                String hullNameText = ship.hullName + " ";
                String affixText = ship.affix;
                String fullText = "  - " + prefixText + hullNameText + affixText + " survived!";
                main.textPanel.addPara(fullText);
                applyShipHighlighting(ship, fullText);
            }
        }

        main.getTextPanel().addPara("Win Reward: " + totalWinReward + " Stargems", Color.GREEN);
    }

    private void displayPerformanceSummary(Set<SpiralAbyssArena.SpiralGladiator> betShips, int totalBet) {
        main.getTextPanel().addPara("Performance Summary:", Color.YELLOW);
        main.getTextPanel().addPara("  - Original Bet: " + totalBet + " Stargems", Color.WHITE);

        for (SpiralAbyssArena.SpiralGladiator ship : betShips) {
            displayShipPerformance(ship);
        }
    }

    private void displayShipPerformance(SpiralAbyssArena.SpiralGladiator ship) {
        int shipBet = getBetAmountForShip(ship);
        float killBonusMult = ship.kills * CasinoConfig.ARENA_KILL_BONUS_PER_KILL;
        String positionStr = getPositionString(ship.finalPosition);

        main.getTextPanel().addPara("  - " + ship.fullName + ":", Color.WHITE);
        main.getTextPanel().addPara("    Bet: " + shipBet + " Stargems", Color.WHITE);
        main.getTextPanel().addPara("    Kills Made: " + ship.kills + " (+" + String.format("%.0f", killBonusMult * 100) + "% to consolation)", Color.WHITE);

        if (ship.isDead) {
            float positionFactor = getPositionFactor(ship.finalPosition);
            main.getTextPanel().addPara("    Final Position: " + positionStr + " (Consolation Factor: " + String.format("%.0f", positionFactor * 100) + "%)", Color.RED);
            main.getTextPanel().addPara("    Status: DEFEATED", Color.RED);
        } else {
            main.getTextPanel().addPara("    Final Position: " + positionStr, Color.GREEN);
            main.getTextPanel().addPara("    Status: SURVIVOR", Color.GREEN);
        }
    }

    private String getPositionString(int finalPosition) {
        if (finalPosition == 0) return "1st (Winner)";
        if (finalPosition == 1) return "2nd";
        if (finalPosition == 2) return "3rd";
        if (finalPosition == 3) return "4th";
        if (finalPosition == 4) return "5th";
        return finalPosition + 1 + "th";
    }

    private void displayNetResult(RewardCalculation rewards) {
        int totalReward = rewards.totalWinReward + rewards.totalConsolationReward;

        if (rewards.anyWinner || rewards.totalConsolationReward > 0) {
            int netResult = totalReward - rewards.totalBet;
            Color resultColor = netResult >= 0 ? Color.GREEN : Color.ORANGE;
            main.getTextPanel().addPara("Net Result: " + (netResult >= 0 ? "+" : "") + netResult + " Stargems", resultColor);
        } else {
            main.getTextPanel().addPara("Net Result: -" + rewards.totalBet + " Stargems", Color.ORANGE);
        }
    }

    private void resetArenaState() {
        activeArena = null;
        arenaCombatants = null;
        chosenChampion = null;
        opponentsDefeated = 0;
        arenaBets.clear();
        cachedTotalBet = 0;
        currentBetAmount = CasinoConfig.ARENA_ENTRY_FEE;
    }

    private void showBetAmountSelection(int championIndex) {
        if (championIndex < 0 || championIndex >= arenaCombatants.size()) {
            main.textPanel.addPara("Error: Invalid champion selection.", Color.RED);
            showArenaStatus();
            return;
        }

        SpiralAbyssArena.SpiralGladiator selectedChampion = arenaCombatants.get(championIndex);
        
        main.getOptions().clearOptions();
        main.getTextPanel().addPara("Add a bet on " + selectedChampion.fullName + ":", Color.YELLOW);
        
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

        main.textPanel.addPara("Current odds factor in HP status and round number.", Color.GRAY);
        main.textPanel.addPara("This bet will be locked at the current odds.", Color.GRAY);
        
        displayFinancialInfo();
        main.getTextPanel().addPara("Current Total Bet: " + getCurrentTotalBet() + " Stargems", Color.CYAN);

        int playerBalance = CasinoVIPManager.getBalance();
        int availableCredit = CasinoVIPManager.getAvailableCredit();

        if (availableCredit <= 0) {
            main.textPanel.addPara("Your credit facility is exhausted. You cannot afford even the minimum bet of " + MIN_BET_INCREMENT + " Stargems.", Color.RED);
            main.textPanel.addPara("Please visit Stargem Top-up to purchase more gems.", Color.YELLOW);
        } else if (availableCredit < MIN_BET_INCREMENT) {
            main.textPanel.addPara("Your available credit (" + availableCredit + " Stargems) is insufficient for the minimum bet of " + MIN_BET_INCREMENT + " Stargems.", Color.RED);
            main.textPanel.addPara("Please visit Stargem Top-up to purchase more gems.", Color.YELLOW);
        } else {
            boolean hasBetOptions = addBetOptions(playerBalance, availableCredit, OPTION_ARENA_CONFIRM_ADD_BET_TO_CHAMP, championIndex);

            if (!hasBetOptions) {
                main.textPanel.addPara("Your balance (" + playerBalance + " Stargems) is insufficient for any available bet option.", Color.RED);
                main.textPanel.addPara("Please visit Stargem Top-up to purchase more gems.", Color.YELLOW);
            }
        }

        main.getOptions().addOption("Back to Champion Selection", OPTION_ARENA_ADD_ANOTHER_BET);
        main.getOptions().addOption("Arena Rules", "how_to_arena_arena_add_another_bet");
    }
    
    private void processLogEntry(String logEntry) {
        // Use the LogFormatter utility to process the log entry with color coding
        LogFormatter.processLogEntry(logEntry, main.textPanel, arenaCombatants, arenaBets);
    }
    
    private void showAddAnotherBetMenu() {
        // First, show champions to bet on
        main.getOptions().clearOptions();
        main.getTextPanel().addPara("Choose a champion to place a bet on:", Color.YELLOW);
        main.textPanel.addPara("Odds are dynamic based on current HP and round number.", Color.GRAY);
        main.textPanel.addPara("Higher HP champions have worse odds (lower payout).", Color.GRAY);
        displayFinancialInfo();
        main.getTextPanel().addPara("Current Total Bet: " + getCurrentTotalBet() + " Stargems", Color.CYAN);
        
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

                main.getOptions().addOption("Bet on " + gladiator.prefix + " " + gladiator.hullName + " " + gladiator.affix + " [" + currentOdds + "]", OPTION_ARENA_SELECT_CHAMPION_FOR_BET + i, Color.YELLOW, null);
            }
        }

        main.getOptions().addOption("Back to Battle Options", OPTION_ARENA_STATUS);
        main.getOptions().addOption("Arena Rules", "how_to_arena_arena_add_another_bet");
    }
    
    private int getCurrentTotalBet() {
        return cachedTotalBet;
    }

    private void performAddBetToChampion(int championIndex, int additionalAmount) {
        if (championIndex < 0 || championIndex >= arenaCombatants.size()) {
            main.textPanel.addPara("Error: Invalid champion selection.", Color.RED);
            showArenaStatus();
            return;
        }

        if (isBetInvalid(additionalAmount, "arena_status")) {
            return;
        }

        SpiralAbyssArena.SpiralGladiator targetChampion = arenaCombatants.get(championIndex);
        if (targetChampion.isDead) {
            main.getTextPanel().addPara("Cannot place bet on " + targetChampion.fullName + ", the champion has been defeated!", Color.RED);
            showArenaStatus();
            return;
        }

        CasinoVIPManager.addToBalance(-additionalAmount);
        float frozenOdds = targetChampion.getCurrentOdds(currentRound);
        arenaBets.add(new BetInfo(additionalAmount, frozenOdds, targetChampion, currentRound));
        cachedTotalBet += additionalAmount;

        main.getTextPanel().addPara("Added bet of " + additionalAmount + " Stargems on " + targetChampion.fullName + " at " + String.format("%.1f", frozenOdds) + "x odds.", Color.YELLOW);
        showArenaStatus();
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

    /**
     * Calculates the performance multiplier based on ship survival and kills
     */
    private float calculatePerformanceMultiplier(SpiralAbyssArena.SpiralGladiator ship) {
        float survivalBonusMult = 1.0f + (ship.turnsSurvived * CasinoConfig.ARENA_SURVIVAL_BONUS_PER_TURN);
        float killBonusMult = 1.0f + (ship.kills * CasinoConfig.ARENA_KILL_BONUS_PER_KILL);
        return survivalBonusMult * killBonusMult;
    }

    /**
     * Calculates the effective multiplier for a bet considering frozen odds, performance and diminishing returns
     * Minimum odds is 1.01 to ensure player always gets at least their bet back (minus house edge)
     * 
     * @param frozenOdds The odds at the time the bet was placed (frozen, includes HP and round-based adjustments)
     * @param performanceMultiplier Multiplier from turns survived and kills
     * @param roundPlaced The round when the bet was placed
     * @return The effective payout multiplier
     */
    private float calculateEffectiveMultiplier(float frozenOdds, float performanceMultiplier, int roundPlaced) {
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

        main.getTextPanel().addPara("You stand up abruptly. 'Hold that thought! I'll be right back!'", Color.YELLOW);
        main.getTextPanel().addPara("The arena announcer pauses mid-sentence. The crowd murmurs in confusion.", Color.CYAN);
        main.getTextPanel().addPara("'The combatants will remain where they are. Don't be long.'", Color.GRAY);
        main.getTextPanel().addPara("'We have other matches scheduled.'", Color.GRAY);
        main.getOptions().clearOptions();
        main.getOptions().addOption("Leave", OPTION_ARENA_LEAVE_NOW);
    }

    private void restoreSuspendedArena() {
        com.fs.starfarer.api.campaign.rules.MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();

        // Check if there's a suspended arena
        String suspendedGameType = mem.getString(MEM_SUSPENDED_GAME_TYPE);
        if (!"Arena".equals(suspendedGameType)) {
            main.getTextPanel().addPara("No suspended arena game found.", Color.RED);
            showArenaLobby();
            return;
        }

        // Restore basic arena state
        currentRound = mem.getInt(MEM_ARENA_CURRENT_ROUND);
        opponentsDefeated = mem.getInt(MEM_ARENA_OPPONENTS_DEFEATED);

        // Restore combatants
        int combatantCount = mem.getInt(MEM_ARENA_COMBATANT_COUNT);
        if (combatantCount > 0) {
            arenaCombatants = new ArrayList<>();
            for (int i = 0; i < combatantCount; i++) {
                String prefix = mem.getString(MEM_ARENA_COMBATANT_PREFIX + i + "_prefix");
                String hullName = mem.getString(MEM_ARENA_COMBATANT_PREFIX + i + "_hull_name");
                String affix = mem.getString(MEM_ARENA_COMBATANT_PREFIX + i + "_affix");
                int hp = mem.getInt(MEM_ARENA_COMBATANT_PREFIX + i + "_hp");
                int maxHp = mem.getInt(MEM_ARENA_COMBATANT_PREFIX + i + "_max_hp");
                int power = mem.getInt(MEM_ARENA_COMBATANT_PREFIX + i + "_power");
                float agility = mem.getFloat(MEM_ARENA_COMBATANT_PREFIX + i + "_agility");
                float bravery = mem.getFloat(MEM_ARENA_COMBATANT_PREFIX + i + "_bravery");

                SpiralAbyssArena.SpiralGladiator gladiator = new SpiralAbyssArena.SpiralGladiator(prefix, hullName, affix, maxHp, power, agility, bravery);
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
        
        // Restore chosen champion
        if (!arenaBets.isEmpty()) {
            chosenChampion = arenaBets.get(0).ship;
        }
        
        // Initialize activeArena if null (needed for proper state tracking)
        if (activeArena == null) {
            activeArena = new SpiralAbyssArena();
        }
        
        // Calculate how long player was away for the shaming message
        long suspendTime = mem.getLong(MEM_ARENA_SUSPEND_TIME);
        float daysAway = Global.getSector().getClock().getElapsedDaysSince(suspendTime);
        
        // Clear the suspended game data
        mem.unset("$ipc_suspended_game_type");
        
        // Shaming message based on how long player was away
        if (daysAway >= 30) {
            main.getTextPanel().addPara("The arena announcer's voice crackles over ancient speakers: 'AFTER " + String.format("%.1f", daysAway) + " DAYS... THEY... RETURN.' Dust falls from the ceiling. 'THE COMBATANTS HAVE BEEN STANDING PERFECTLY STILL, AGING GRACEFULLY, WAITING FOR YOU.'", Color.YELLOW);
        } else if (daysAway >= 7) {
            main.getTextPanel().addPara("The arena manager rushes over, eyes bloodshot. '" + String.format("%.1f", daysAway) + " DAYS! Do you know how long it takes to keep a crowd entertained when nothing is happening? We've been doing puppet shows with fighter debris!'", Color.YELLOW);
        } else if (daysAway >= 1) {
            main.getTextPanel().addPara("A maintenance drone bumps into you. 'Welcome back after " + String.format("%.1f", daysAway) + " days,' it drones. 'The combatants have not moved. The crowd has not moved. Time has... mostly not moved. Can we start now?'", Color.YELLOW);
        } else {
            main.getTextPanel().addPara("The arena attendant looks surprised. 'Back already? Only " + String.format("%.1f", daysAway * 24) + " hours have passed.' They whisper to a colleague: 'I lost the bet. I said they'd be gone at least a day.'", Color.YELLOW);
        }
        main.getTextPanel().addPara("The crowd stirs from their torpor as the combatants finally unlock their joints.", Color.CYAN);
        
        // Show the battle status
        showArenaStatus();
    }

    /**
     * Finds a gladiator by their full name in the current combatants list.
     */
    private SpiralAbyssArena.SpiralGladiator findGladiatorByName(String fullName) {
        for (SpiralAbyssArena.SpiralGladiator gladiator : arenaCombatants) {
            if (gladiator.fullName.equals(fullName)) {
                return gladiator;
            }
        }
        return null;
    }

    /**
     * Checks if there's a suspended arena game available to resume
     */
    public boolean hasSuspendedArena() {
        com.fs.starfarer.api.campaign.rules.MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        String suspendedGameType = mem.getString(MEM_SUSPENDED_GAME_TYPE);
        return "Arena".equals(suspendedGameType);
    }
}