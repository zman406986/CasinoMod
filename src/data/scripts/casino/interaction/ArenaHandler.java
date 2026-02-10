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
import java.util.function.Predicate;

public class ArenaHandler {

    private static final int MIN_BET_INCREMENT = 100;
    private static final int BET_AMOUNT_100 = 100;
    private static final int BET_AMOUNT_500 = 500;
    private static final int BET_AMOUNT_2000 = 2000;
    private static final int PERCENT_10 = 10;
    private static final int PERCENT_50 = 50;

    private final CasinoInteraction main;
    
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
        public BetInfo(int a, float m, SpiralAbyssArena.SpiralGladiator s, int r) { amount = a; multiplier = m; ship = s; roundPlaced = r; }
    }
    
    protected List<BetInfo> arenaBets = new ArrayList<>();
    
    private final Map<String, OptionHandler> handlers = new HashMap<>();
    private final Map<Predicate<String>, OptionHandler> predicateHandlers = new HashMap<>();

    public ArenaHandler(CasinoInteraction main) {
        this.main = main;
        initializeHandlers();
    }
    
    private void initializeHandlers() {
        handlers.put("arena_lobby", option -> showArenaLobby());
        handlers.put("arena_add_bet_menu", option -> showAddBetMenu());
        handlers.put("how_to_arena", option -> main.help.showArenaHelp());
        handlers.put("back_menu", option -> main.showMenu());
        handlers.put("arena_resume", option -> restoreSuspendedArena());

        handlers.put("arena_watch_next", option -> simulateArenaStep());
        handlers.put("arena_skip", option -> {
            boolean result;
            do {
                result = simulateArenaStep();
            } while (result);
        });
        handlers.put("arena_suspend", option -> suspendArena());
        handlers.put("arena_add_another_bet", option -> showAddAnotherBetMenu());
        handlers.put("arena_status", option -> showArenaStatus());
        handlers.put("arena_leave_now", option -> main.showMenu());
        handlers.put("arena_start_battle_with_current_bet", option -> {
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
        
        predicateHandlers.put(option -> option.startsWith("arena_select_ship_"), option -> {
            int parsedIdx = Integer.parseInt(option.replace("arena_select_ship_", ""));
            showArenaConfirm(parsedIdx);
        });
        predicateHandlers.put(option -> option.startsWith("arena_add_bet_")
                && !option.equals("arena_add_bet_menu")
                && !option.startsWith("arena_confirm_add_bet_"), option -> {
            int parsedAdditionalAmount = Integer.parseInt(option.replace("arena_add_bet_", ""));
            confirmAddBet(parsedAdditionalAmount);
        });
        predicateHandlers.put(option -> option.startsWith("arena_confirm_add_bet_")
                && !option.startsWith("arena_confirm_add_bet_to_champ_"), option -> {
            int parsedAdditionalAmount = Integer.parseInt(option.replace("arena_confirm_add_bet_", ""));
            addIncrementalBet(parsedAdditionalAmount);
        });
        predicateHandlers.put(option -> option.startsWith("arena_select_champion_for_bet_"), option -> {
            int parsedIdx = Integer.parseInt(option.replace("arena_select_champion_for_bet_", ""));
            showBetAmountSelection(parsedIdx);
        });
        predicateHandlers.put(option -> option.startsWith("arena_confirm_add_bet_to_champ_"), option -> {
            String[] parts = option.replace("arena_confirm_add_bet_to_champ_", "").split("_");
            int championIndex = Integer.parseInt(parts[0]);
            int amount = Integer.parseInt(parts[1]);
            performAddBetToChampion(championIndex, amount);
        });
    }

    private void applyShipHighlighting(SpiralAbyssArena.SpiralGladiator ship, String... additionalHighlights) {
        List<String> highlights = new ArrayList<>();
        List<Color> highlightColors = new ArrayList<>();

        highlights.add(ship.hullName);
        highlightColors.add(Color.YELLOW);

        Color prefixHighlightColor = isPositiveAffix(ship.prefix) ? Color.GREEN : Color.RED;
        highlights.add(ship.prefix);
        highlightColors.add(prefixHighlightColor);

        Color affixHighlightColor = isPositiveAffix(ship.affix) ? Color.GREEN : Color.RED;
        highlights.add(ship.affix);
        highlightColors.add(affixHighlightColor);

        for (String additional : additionalHighlights) {
            highlights.add(additional);
        }

        main.textPanel.setHighlightColorsInLastPara(highlightColors.toArray(new Color[0]));
        main.textPanel.highlightInLastPara(highlights.toArray(new String[0]));
    }

    private void applyShipHighlightingWithColors(SpiralAbyssArena.SpiralGladiator ship, List<String> additionalHighlights, List<Color> additionalColors) {
        List<String> highlights = new ArrayList<>();
        List<Color> highlightColors = new ArrayList<>();

        highlights.add(ship.hullName);
        highlightColors.add(Color.YELLOW);

        Color prefixHighlightColor = isPositiveAffix(ship.prefix) ? Color.GREEN : Color.RED;
        highlights.add(ship.prefix);
        highlightColors.add(prefixHighlightColor);

        Color affixHighlightColor = isPositiveAffix(ship.affix) ? Color.GREEN : Color.RED;
        highlights.add(ship.affix);
        highlightColors.add(affixHighlightColor);

        if (additionalHighlights != null && additionalColors != null) {
            highlights.addAll(additionalHighlights);
            highlightColors.addAll(additionalColors);
        }

        main.textPanel.setHighlightColorsInLastPara(highlightColors.toArray(new Color[0]));
        main.textPanel.highlightInLastPara(highlights.toArray(new String[0]));
    }

    private boolean addBetOptions(int playerBalance, int availableCredit, String optionPrefix, int championIndex) {
        boolean hasBetOptions = false;

        if (playerBalance >= BET_AMOUNT_100 && availableCredit >= BET_AMOUNT_100) {
            if (championIndex >= 0) {
                main.getOptions().addOption("Add " + BET_AMOUNT_100 + " Stargems", optionPrefix + championIndex + "_" + BET_AMOUNT_100);
            } else {
                main.options.addOption("Add " + BET_AMOUNT_100 + " Stargems", "arena_add_bet_" + BET_AMOUNT_100);
            }
            hasBetOptions = true;
        }
        if (playerBalance >= BET_AMOUNT_500 && availableCredit >= BET_AMOUNT_500) {
            if (championIndex >= 0) {
                main.getOptions().addOption("Add " + BET_AMOUNT_500 + " Stargems", optionPrefix + championIndex + "_" + BET_AMOUNT_500);
            } else {
                main.options.addOption("Add " + BET_AMOUNT_500 + " Stargems", "arena_add_bet_" + BET_AMOUNT_500);
            }
            hasBetOptions = true;
        }
        if (playerBalance >= BET_AMOUNT_2000 && availableCredit >= BET_AMOUNT_2000) {
            if (championIndex >= 0) {
                main.getOptions().addOption("Add " + BET_AMOUNT_2000 + " Stargems", optionPrefix + championIndex + "_" + BET_AMOUNT_2000);
            } else {
                main.options.addOption("Add " + BET_AMOUNT_2000 + " Stargems", "arena_add_bet_" + BET_AMOUNT_2000);
            }
            hasBetOptions = true;
        }

        if (availableCredit > 0) {
            int tenPercent = (availableCredit * PERCENT_10) / 100;
            if (tenPercent > 0 && playerBalance >= tenPercent && tenPercent <= availableCredit) {
                if (championIndex >= 0) {
                    main.getOptions().addOption("Add " + tenPercent + " Stargems (" + PERCENT_10 + "% of remaining credit)", optionPrefix + championIndex + "_" + tenPercent);
                } else {
                    main.options.addOption("Add " + tenPercent + " Stargems (" + PERCENT_10 + "% of remaining credit)", "arena_add_bet_" + tenPercent);
                }
                hasBetOptions = true;
            }

            int fiftyPercent = (availableCredit * PERCENT_50) / 100;
            if (fiftyPercent > 0 && playerBalance >= fiftyPercent && fiftyPercent <= availableCredit) {
                if (championIndex >= 0) {
                    main.getOptions().addOption("Add " + fiftyPercent + " Stargems (" + PERCENT_50 + "% of remaining credit)", optionPrefix + championIndex + "_" + fiftyPercent);
                } else {
                    main.options.addOption("Add " + fiftyPercent + " Stargems (" + PERCENT_50 + "% of remaining credit)", "arena_add_bet_" + fiftyPercent);
                }
                hasBetOptions = true;
            }
        } else {
            int tenPercent = (playerBalance * PERCENT_10) / 100;
            if (tenPercent > 0 && playerBalance >= tenPercent) {
                if (championIndex >= 0) {
                    main.getOptions().addOption("Add " + tenPercent + " Stargems (" + PERCENT_10 + "% of account)", optionPrefix + championIndex + "_" + tenPercent);
                } else {
                    main.options.addOption("Add " + tenPercent + " Stargems (" + PERCENT_10 + "% of account)", "arena_add_bet_" + tenPercent);
                }
                hasBetOptions = true;
            }

            int fiftyPercent = (playerBalance * PERCENT_50) / 100;
            if (fiftyPercent > 0 && playerBalance >= fiftyPercent) {
                if (championIndex >= 0) {
                    main.getOptions().addOption("Add " + fiftyPercent + " Stargems (" + PERCENT_50 + "% of account)", optionPrefix + championIndex + "_" + fiftyPercent);
                } else {
                    main.options.addOption("Add " + fiftyPercent + " Stargems (" + PERCENT_50 + "% of account)", "arena_add_bet_" + fiftyPercent);
                }
                hasBetOptions = true;
            }
        }

        return hasBetOptions;
    }

    private boolean validateBetAmount(int amount, String returnToMenu) {
        int availableCredit = CasinoVIPManager.getAvailableCredit();
        int currentBalance = CasinoVIPManager.getBalance();
        boolean overdraftAvailable = CasinoVIPManager.isOverdraftAvailable();

        if (currentBalance < amount) {
            if (!overdraftAvailable) {
                showVIPPromotionForArena(amount, returnToMenu);
                return false;
            }

            if (availableCredit <= 0) {
                main.textPanel.addPara("Your credit facility is exhausted. You cannot afford this bet of " + amount + " Stargems.", Color.RED);
                main.textPanel.addPara("Please visit Stargem Top-up to purchase more gems.", Color.YELLOW);
                showAddBetMenu();
                return false;
            } else if (availableCredit < amount) {
                main.textPanel.addPara("Your available credit (" + availableCredit + " Stargems) is insufficient for this bet of " + amount + " Stargems.", Color.RED);
                main.textPanel.addPara("Please select a smaller bet amount or visit Stargem Top-up.", Color.YELLOW);
                showAddBetMenu();
                return false;
            }
        }

        return true;
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
        
        // Show balance with color coding
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
        
        // Check if there's a suspended arena game to resume
        if (hasSuspendedArena()) {
            restoreSuspendedArena();
            return;
        }
        
        // Only generate new ships if arena hasn't been initialized yet
        if (activeArena == null || arenaCombatants == null || arenaCombatants.isEmpty()) {
            activeArena = new SpiralAbyssArena();
            arenaCombatants = activeArena.generateCombatants(new CasinoGachaManager());
            // Reset bet amount to default for a new championship
            currentBetAmount = CasinoConfig.ARENA_ENTRY_FEE;
        }
        
        main.textPanel.addPara("Spiral Abyss Arena - Today's Match Card", Color.CYAN);
        main.textPanel.addPara("Select a champion to battle with:", Color.YELLOW);
        
        for (int i=0; i<arenaCombatants.size(); i++) {
            SpiralAbyssArena.SpiralGladiator g = arenaCombatants.get(i);

            // Combine all ship name parts into a single paragraph to avoid excessive line breaks
            main.textPanel.setFontInsignia();

            // Create the full ship name string with base odds (pre-battle)
            String shipEntry = (i+1) + ". ";
            String prefixText = g.prefix + " ";
            String hullNameText = g.hullName + " ";
            String affixText = g.affix;
            String oddsText = " [" + g.getBaseOddsString() + "]";
            String parenText = " (" + g.hullName + ")";

            // Add the entire string as one paragraph with default color
            main.textPanel.addPara(shipEntry + prefixText + hullNameText + affixText + oddsText + parenText);

            List<String> additionalHighlights = new ArrayList<>();
            List<Color> additionalColors = new ArrayList<>();

            additionalHighlights.add(oddsText);
            additionalColors.add(Color.YELLOW);

            additionalHighlights.add(parenText.trim());
            additionalColors.add(Color.GRAY);

            applyShipHighlightingWithColors(g, additionalHighlights, additionalColors);

            main.options.addOption(g.fullName + " [" + g.getBaseOddsString() + "]", "arena_select_ship_" + i, Color.YELLOW, null);
        }
        
        main.options.addOption("How it Works", "how_to_arena");
        main.options.addOption("Back", "back_menu");
        main.setState(CasinoInteraction.State.ARENA);
    }

    /**
     * Determines if an affix or prefix is positive or negative based on the configuration
     */
    private boolean isPositiveAffix(String affixOrPrefix) {
        return CasinoConfig.ARENA_AFFIX_POS.contains(affixOrPrefix)
            || CasinoConfig.ARENA_PREFIX_STRONG_POS.contains(affixOrPrefix)
            || !CasinoConfig.ARENA_AFFIX_NEG.contains(affixOrPrefix)
            && !CasinoConfig.ARENA_PREFIX_STRONG_NEG.contains(affixOrPrefix);
    }


    private void showArenaConfirm(int idx) {
        // Validate champion index
        if (idx < 0 || idx >= arenaCombatants.size()) {
            main.textPanel.addPara("Error: Invalid champion selection.", Color.RED);
            showArenaLobby();
            return;
        }
        
        main.options.clearOptions();
        SpiralAbyssArena.SpiralGladiator g = arenaCombatants.get(idx);
        
        // Store the selected champion index temporarily
        chosenChampion = arenaCombatants.get(idx);

        currentBetAmount = 0;
        arenaBets.clear();
        cachedTotalBet = 0;
        
        main.textPanel.setFontInsignia();

        // Combine champion selection text into a single paragraph
        String championText = "Selected champion: ";
        String prefixText = g.prefix + " ";
        String hullNameText = g.hullName + " ";
        String affixText = g.affix;

        main.textPanel.addPara(championText + prefixText + hullNameText + affixText);

        applyShipHighlighting(g);

        main.textPanel.addPara("Now add your bet amount:", Color.CYAN);
        
        // Go to add bet menu which loops back to itself until "Start Battle" is chosen
        showAddBetMenu();
    }
    
    private void addIncrementalBet(int additionalAmount) {
        if (!validateBetAmount(additionalAmount, "arena_add_bet_menu")) {
            return;
        }

        CasinoVIPManager.addToBalance(-additionalAmount);

        float currentMultiplier = 1.0f;
        if (!arenaBets.isEmpty()) {
            currentMultiplier = arenaBets.get(arenaBets.size()-1).multiplier;
        }

        arenaBets.add(new BetInfo(additionalAmount, currentMultiplier, chosenChampion, currentRound));
        cachedTotalBet += additionalAmount;

        main.textPanel.addPara("Added " + additionalAmount + " Stargems to your bet. Total bet: " + getCurrentTotalBet() + " Stargems.", Color.GREEN);
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

        main.getOptions().addOption("Go to Stargem Top-up", "topup_menu");
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
        // Make sure we have a valid champion
        if (chosenIdx < 0 || chosenIdx >= arenaCombatants.size()) {
            main.textPanel.addPara("Error: Invalid champion selection. Returning to lobby.", Color.RED);
            showArenaLobby();
            return;
        }
        
        chosenChampion = arenaCombatants.get(chosenIdx);
        
        // Deduct the total bet amount from player's gems
        int totalBet = getCurrentTotalBet();
        
        // If no bets have been placed, add a default bet using entry fee config
        if (totalBet <= 0) {
            totalBet = CasinoConfig.ARENA_ENTRY_FEE;
            arenaBets.add(new BetInfo(CasinoConfig.ARENA_ENTRY_FEE, 1.0f, chosenChampion, 0));
        }

        // Note: Bet amount was already deducted when placed via addIncrementalBet()
        // No additional deduction or validation needed here
        
        opponentsDefeated = 0;
        currentRound = 0;
        
        // Announce the battle with all ships the player has bet on
        main.textPanel.setFontInsignia();
        main.textPanel.addPara("The battle begins! Your champions enter the arena:", Color.CYAN);
        
        // Get all unique ships the player has bet on
        Set<SpiralAbyssArena.SpiralGladiator> betShips = new HashSet<>();
        for (BetInfo bet : arenaBets) {
            if (bet.ship != null) {
                betShips.add(bet.ship);
            }
        }
        
        // Display each ship the player has bet on
        for (SpiralAbyssArena.SpiralGladiator ship : betShips) {
            String prefixText = ship.prefix + " ";
            String hullNameText = ship.hullName + " ";
            String affixText = ship.affix;
            String betText = " (Bet: " + getBetAmountForShip(ship) + " Stargems)";
            String betAmountStr = getBetAmountForShip(ship) + " Stargems";

            main.textPanel.addPara("  - " + prefixText + hullNameText + affixText + betText);

            List<String> additionalHighlights = new ArrayList<>();
            List<Color> additionalColors = new ArrayList<>();

            additionalHighlights.add(betAmountStr);
            additionalColors.add(Color.YELLOW);

            applyShipHighlightingWithColors(ship, additionalHighlights, additionalColors);
        }
        
        main.textPanel.addPara("Total Bet: " + totalBet + " Stargems", Color.YELLOW);
        
        simulateArenaStep();
    }

    private boolean simulateArenaStep() {
        List<String> logEntries = activeArena.simulateStep(arenaCombatants);
        
        currentRound++;
        
        // Track turns survived for each ship the player bet on
        for (SpiralAbyssArena.SpiralGladiator gladiator : arenaCombatants) {
            if (!gladiator.isDead) {
                gladiator.turnsSurvived++;
            }
        }
        
        // Count surviving ships
        int aliveCount = 0;
        for (SpiralAbyssArena.SpiralGladiator gladiator : arenaCombatants) {
            if (!gladiator.isDead) {
                aliveCount++;
            }
        }
        
        if (logEntries.isEmpty() && aliveCount <= 1) { // If no log entries and only one or fewer ships remain
            finishArenaBattle();
            return false;
        }
        
        // Add battle logs to text panel
        for (String logEntry : logEntries) {
            // Process the log entry to identify attacker, target, and damage values
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
        main.getOptions().addOption("Tell Them to Wait (Suspend)", "arena_suspend");
    }

    private void finishArenaBattle() {
        main.getOptions().clearOptions();
        
        // Determine the actual winner (could be different from chosen champion if player switched)
        SpiralAbyssArena.SpiralGladiator actualWinner = null;
        for (SpiralAbyssArena.SpiralGladiator gladiator : arenaCombatants) {
            if (!gladiator.isDead) {
                actualWinner = gladiator;
                break;
            }
        }
        
        String winnerName = actualWinner != null ? actualWinner.fullName : "Unknown";
        
        // Get all unique ships the player bet on
        Set<SpiralAbyssArena.SpiralGladiator> betShips = new HashSet<>();
        for (BetInfo bet : arenaBets) {
            if (bet.ship != null) {
                betShips.add(bet.ship);
            }
        }
        
        // Calculate rewards for each ship the player bet on
        int totalWinReward = 0;
        int totalConsolationReward = 0;
        boolean anyWinner = false;
        boolean anyDefeated = false;
        
        // Apply survival reward multiplier from config
        float survivalRewardMultiplier = CasinoConfig.ARENA_SURVIVAL_REWARD_MULT;
        float consolationMultiplier = CasinoConfig.ARENA_DEFEATED_CONSOLATION_MULT;
        
        for (SpiralAbyssArena.SpiralGladiator ship : betShips) {
            if (!ship.isDead) {
                // This ship won - calculate its reward using dynamic odds based on when bet was placed
                float performanceMultiplier = calculatePerformanceMultiplier(ship);

                for (BetInfo bet : arenaBets) {
                    if (bet.ship == ship) {
                        // Use the odds that were in effect when the bet was placed
                        float oddsAtBetTime = ship.getCurrentOdds(bet.roundPlaced);
                        float effectiveMultiplier = calculateEffectiveMultiplier(oddsAtBetTime, performanceMultiplier, bet.roundPlaced);
                        int betReward = (int)(bet.amount * effectiveMultiplier * survivalRewardMultiplier);
                        totalWinReward += betReward;
                    }
                }
                anyWinner = true;
            } else {
                // This ship was defeated - calculate consolation reward
                anyDefeated = true;
                
                for (BetInfo bet : arenaBets) {
                    if (bet.ship == ship) {
                        // Calculate performance-based consolation
                        float survivalBonus = ship.turnsSurvived * CasinoConfig.ARENA_SURVIVAL_BONUS_PER_TURN;
                        float killBonus = ship.kills * CasinoConfig.ARENA_KILL_BONUS_PER_KILL;
                        float performanceValue = 1.0f + survivalBonus + killBonus;
                        
                        // Use base odds for consolation calculation (not dynamic HP-based odds)
                        float oddsFactor = 1.0f / Math.max(1.0f, ship.baseOdds);
                        
                        // Apply diminishing returns for late bets
                        float diminishingReturns = 1.0f;
                        if (bet.roundPlaced > 0) {
                            diminishingReturns = 1.0f - (bet.roundPlaced * CasinoConfig.ARENA_DIMINISHING_RETURNS_PER_ROUND);
                            diminishingReturns = Math.max(CasinoConfig.ARENA_DIMINISHING_RETURNS_MIN, diminishingReturns);
                        }
                        
                        int consolationReward = (int)(bet.amount * performanceValue * oddsFactor * diminishingReturns * consolationMultiplier);
                        totalConsolationReward += consolationReward;
                    }
                }
            }
        }
        
        int totalReward = totalWinReward + totalConsolationReward;
        
        if (anyWinner) {
            CasinoVIPManager.addToBalance(totalWinReward);
            
            main.textPanel.setFontInsignia();
            main.textPanel.addPara("VICTORY! Your champions have triumphed!", Color.GREEN);
            
            // Display each winning ship
            for (SpiralAbyssArena.SpiralGladiator ship : betShips) {
                if (!ship.isDead) {
                    String prefixText = ship.prefix + " ";
                    String hullNameText = ship.hullName + " ";
                    String affixText = ship.affix;

                    main.textPanel.addPara("  - " + prefixText + hullNameText + affixText + " survived!");

                    applyShipHighlighting(ship);
                }
            }
            
            main.getTextPanel().addPara("Win Reward: " + totalWinReward + " Stargems", Color.GREEN);
        } else {
            main.getTextPanel().addPara("DEFEAT. All your champions have been decommissioned.", Color.RED);
        }
        
        // Award consolation rewards for defeated champions
        if (anyDefeated && totalConsolationReward > 0) {
            CasinoVIPManager.addToBalance(totalConsolationReward);
            main.getTextPanel().addPara("Consolation Reward: " + totalConsolationReward + " Stargems", Color.YELLOW);
            main.getTextPanel().addPara("(Based on performance of defeated champions)", Color.GRAY);
        }
        
        // Display performance statistics for each ship player bet on
        int totalBet = getCurrentTotalBet();
        main.getTextPanel().addPara("Performance Summary:", Color.YELLOW);
        main.getTextPanel().addPara("  - Original Bet: " + totalBet + " Stargems", Color.WHITE);
        
        for (SpiralAbyssArena.SpiralGladiator ship : betShips) {
            int shipBet = getBetAmountForShip(ship);
            float survivalBonusMult = ship.turnsSurvived * CasinoConfig.ARENA_SURVIVAL_BONUS_PER_TURN;
            float killBonusMult = ship.kills * CasinoConfig.ARENA_KILL_BONUS_PER_KILL;
            float performanceMultiplierBonus = survivalBonusMult + killBonusMult;
            
            main.getTextPanel().addPara("  - " + ship.fullName + ":", Color.WHITE);
            main.getTextPanel().addPara("    Bet: " + shipBet + " Stargems", Color.WHITE);
            main.getTextPanel().addPara("    Turns Survived: " + ship.turnsSurvived + " (+" + survivalBonusMult + " to multiplier)", Color.WHITE);
            main.getTextPanel().addPara("    Kills Made: " + ship.kills + " (+" + killBonusMult + " to multiplier)", Color.WHITE);
            
            if (ship.isDead) {
                // Show consolation calculation for defeated ships
                float oddsFactor = 1.0f / Math.max(1.0f, ship.baseOdds);
                main.getTextPanel().addPara("    Status: DEFEATED (Base Odds Factor: " + String.format("%.2f", oddsFactor) + ")", Color.RED);
                main.getTextPanel().addPara("    Performance Value: " + String.format("%.2f", 1.0f + performanceMultiplierBonus), Color.CYAN);
            } else {
                main.getTextPanel().addPara("    Status: SURVIVOR", Color.GREEN);
                main.getTextPanel().addPara("    Performance Bonus: +" + performanceMultiplierBonus + " to multiplier", Color.CYAN);
            }
        }
        
        if (anyWinner || totalConsolationReward > 0) {
            int netResult = totalReward - totalBet;
            Color resultColor = netResult >= 0 ? Color.GREEN : Color.ORANGE;
            main.getTextPanel().addPara("Net Result: " + (netResult >= 0 ? "+" : "") + netResult + " Stargems", resultColor);
        } else {
            main.getTextPanel().addPara("Net Result: -" + totalBet + " Stargems", Color.ORANGE);
        }
        
        // Play game complete sound
        // Removed sound effect as it was removed from sounds.json
// Global.getSoundPlayer().playUISound("game_complete", 1f, 1f);
        
        // Reset the arena state to generate new ships for the next battle
        activeArena = null;
        arenaCombatants = null;
        chosenChampion = null;
        opponentsDefeated = 0;
        arenaBets.clear();
        currentBetAmount = CasinoConfig.ARENA_ENTRY_FEE; // Reset to default bet amount for next game
        
        // According to the requirements, after battle, we should show a menu that allows adding bet
        // until "Continue Battle" (which would be "arena_lobby" in this case) is chosen
        main.getOptions().addOption("Return to Lobby", "arena_lobby");
        main.getOptions().addOption("Back to Main Menu", "back_menu");
    }
    
    private void showBetAmountSelection(int championIndex) {
        // Validate champion index
        if (championIndex < 0 || championIndex >= arenaCombatants.size()) {
            main.textPanel.addPara("Error: Invalid champion selection.", Color.RED);
            showArenaStatus();
            return;
        }
        
        // Store the selected champion for betting
        SpiralAbyssArena.SpiralGladiator selectedChampion = arenaCombatants.get(championIndex);
        
        main.getOptions().clearOptions();
        main.getTextPanel().addPara("Add a bet on " + selectedChampion.fullName + ":", Color.YELLOW);
        
        // Show champion details with proper color coding
        main.textPanel.setFontInsignia();
        String prefixText = selectedChampion.prefix + " ";
        String hullNameText = selectedChampion.hullName + " ";
        String affixText = selectedChampion.affix;
        // Show current dynamic odds based on HP and round
        String currentOddsStr = selectedChampion.getCurrentOddsString(currentRound);
        String baseOddsStr = selectedChampion.getBaseOddsString();
        String oddsText = " [" + currentOddsStr + " (base: " + baseOddsStr + ")]";
        String hpText = " " + selectedChampion.hp + "/" + selectedChampion.maxHp + " HP";

        main.textPanel.addPara(prefixText + hullNameText + affixText + oddsText + hpText);

        List<String> additionalHighlights = new ArrayList<>();
        List<Color> additionalColors = new ArrayList<>();

        additionalHighlights.add(currentOddsStr);
        additionalColors.add(Color.YELLOW);

        additionalHighlights.add(selectedChampion.hp + "/" + selectedChampion.maxHp + " HP");
        additionalColors.add(Color.CYAN);

        applyShipHighlightingWithColors(selectedChampion, additionalHighlights, additionalColors);
        
        // Show odds explanation
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
            boolean hasBetOptions = addBetOptions(playerBalance, availableCredit, "arena_confirm_add_bet_to_champ_", championIndex);

            if (!hasBetOptions) {
                main.textPanel.addPara("Your balance (" + playerBalance + " Stargems) is insufficient for any available bet option.", Color.RED);
                main.textPanel.addPara("Please visit Stargem Top-up to purchase more gems.", Color.YELLOW);
            }
        }

        main.getOptions().addOption("Back to Champion Selection", "arena_add_another_bet");
    }
    
    private void processLogEntry(String logEntry) {
        // Use the LogFormatter utility to process the log entry with color coding
        LogFormatter.processLogEntry(logEntry, main.textPanel, arenaCombatants);
    }
    
    private void showAddAnotherBetMenu() {
        // First, show champions to bet on
        main.getOptions().clearOptions();
        main.getTextPanel().addPara("Choose a champion to place a bet on:", Color.YELLOW);
        main.textPanel.addPara("Odds are dynamic based on current HP and round number.", Color.GRAY);
        main.textPanel.addPara("Higher HP champions have worse odds (lower payout).", Color.GRAY);
        displayFinancialInfo();
        main.getTextPanel().addPara("Current Total Bet: " + getCurrentTotalBet() + " Stargems", Color.CYAN);
        
        // Show all alive champions as options
        for (int i = 0; i < arenaCombatants.size(); i++) {
            SpiralAbyssArena.SpiralGladiator g = arenaCombatants.get(i);
            if (!g.isDead) { // Only show alive champions
                // Combine all ship name parts into a single paragraph to avoid excessive line breaks
                main.textPanel.setFontInsignia();

                String prefixText = g.prefix + " ";
                String hullNameText = g.hullName + " ";
                String affixText = g.affix;
                // Show current dynamic odds based on HP and current round
                String currentOdds = g.getCurrentOddsString(currentRound);
                String baseOdds = g.getBaseOddsString();
                String oddsText = " [" + currentOdds + " (base: " + baseOdds + ")]";
                String hpText = " " + g.hp + "/" + g.maxHp + " HP";

                main.textPanel.addPara(prefixText + hullNameText + affixText + oddsText + hpText);

                List<String> additionalHighlights = new ArrayList<>();
                List<Color> additionalColors = new ArrayList<>();

                additionalHighlights.add(currentOdds);
                additionalColors.add(Color.YELLOW);

                additionalHighlights.add(g.hp + "/" + g.maxHp + " HP");
                additionalColors.add(Color.CYAN);

                applyShipHighlightingWithColors(g, additionalHighlights, additionalColors);

                // Add option to select this champion for betting with current odds
                main.getOptions().addOption("Bet on " + g.prefix + " " + g.hullName + " " + g.affix + " [" + currentOdds + "]", "arena_select_champion_for_bet_" + i, Color.YELLOW, null);
            }
        }
        
        main.getOptions().addOption("Back to Battle Options", "arena_status");
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

        if (!validateBetAmount(additionalAmount, "arena_status")) {
            return;
        }

        SpiralAbyssArena.SpiralGladiator targetChampion = arenaCombatants.get(championIndex);
        if (targetChampion.isDead) {
            main.getTextPanel().addPara("Cannot place bet on " + targetChampion.fullName + ", the champion has been defeated!", Color.RED);
            showArenaStatus();
            return;
        }

        CasinoVIPManager.addToBalance(-additionalAmount);
        arenaBets.add(new BetInfo(additionalAmount, 1.0f, targetChampion, currentRound));

        main.getTextPanel().addPara("Added another bet of " + additionalAmount + " Stargems on " + targetChampion.fullName + ".", Color.YELLOW);
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
     * Calculates the effective multiplier for a bet considering odds, performance and diminishing returns
     * Minimum odds is 1.01 to ensure player always gets at least their bet back (minus house edge)
     * 
     * @param oddsAtBetTime The odds at the time the bet was placed (includes HP and round-based adjustments)
     * @param performanceMultiplier Multiplier from turns survived and kills
     * @param roundPlaced The round when the bet was placed
     * @return The effective payout multiplier
     */
    private float calculateEffectiveMultiplier(float oddsAtBetTime, float performanceMultiplier, int roundPlaced) {
        // Note: Diminishing returns and HP adjustments are already factored into oddsAtBetTime
        // via ship.getCurrentOdds(roundPlaced), so we only apply performance multiplier here
        float effectiveMultiplier = oddsAtBetTime * performanceMultiplier;
        return Math.max(1.01f, effectiveMultiplier);
    }

    private void suspendArena() {
        com.fs.starfarer.api.campaign.rules.MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        mem.set("$ipc_suspended_game_type", "Arena");
        
        // Save basic arena state
        mem.set("$ipc_arena_current_round", currentRound);
        mem.set("$ipc_arena_opponents_defeated", opponentsDefeated);
        
        // Save combatants state (ship names, health, etc.)
        if (arenaCombatants != null) {
            for (int i = 0; i < arenaCombatants.size(); i++) {
                SpiralAbyssArena.SpiralGladiator g = arenaCombatants.get(i);
                mem.set("$ipc_arena_combatant_" + i + "_prefix", g.prefix);
                mem.set("$ipc_arena_combatant_" + i + "_hull_name", g.hullName);
                mem.set("$ipc_arena_combatant_" + i + "_affix", g.affix);
                mem.set("$ipc_arena_combatant_" + i + "_hp", g.hp);
                mem.set("$ipc_arena_combatant_" + i + "_max_hp", g.maxHp);
                mem.set("$ipc_arena_combatant_" + i + "_power", g.power);
                mem.set("$ipc_arena_combatant_" + i + "_agility", g.agility);
                mem.set("$ipc_arena_combatant_" + i + "_bravery", g.bravery);
                mem.set("$ipc_arena_combatant_" + i + "_is_dead", g.isDead);
                mem.set("$ipc_arena_combatant_" + i + "_kills", g.kills);
                mem.set("$ipc_arena_combatant_" + i + "_turns_survived", g.turnsSurvived);
            }
            mem.set("$ipc_arena_combatant_count", arenaCombatants.size());
        }
        
        // Save bets
        mem.set("$ipc_arena_bets_count", arenaBets.size());
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
        mem.set("$ipc_arena_suspend_time", Global.getSector().getClock().getTimestamp());
        
        main.getTextPanel().addPara("You stand up abruptly. 'Hold that thought! I'll be right back!'", Color.YELLOW);
        main.getTextPanel().addPara("The arena announcer pauses mid-sentence. The crowd murmurs in confusion.", Color.CYAN);
        main.getTextPanel().addPara("'The combatants will remain where they are. Don't be long.'", Color.GRAY);
        main.getTextPanel().addPara("'We have other matches scheduled.'", Color.GRAY);
        main.getOptions().clearOptions();
        main.getOptions().addOption("Leave", "arena_leave_now");
    }

    private void restoreSuspendedArena() {
        com.fs.starfarer.api.campaign.rules.MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        
        // Check if there's a suspended arena
        String suspendedGameType = mem.getString("$ipc_suspended_game_type");
        if (!"Arena".equals(suspendedGameType)) {
            main.getTextPanel().addPara("No suspended arena game found.", Color.RED);
            showArenaLobby();
            return;
        }
        
        // Restore basic arena state
        currentRound = mem.getInt("$ipc_arena_current_round");
        opponentsDefeated = mem.getInt("$ipc_arena_opponents_defeated");
        
        // Restore combatants
        int combatantCount = mem.getInt("$ipc_arena_combatant_count");
        if (combatantCount > 0) {
            arenaCombatants = new ArrayList<>();
            for (int i = 0; i < combatantCount; i++) {
                String prefix = mem.getString("$ipc_arena_combatant_" + i + "_prefix");
                String hullName = mem.getString("$ipc_arena_combatant_" + i + "_hull_name");
                String affix = mem.getString("$ipc_arena_combatant_" + i + "_affix");
                int hp = mem.getInt("$ipc_arena_combatant_" + i + "_hp");
                int power = mem.getInt("$ipc_arena_combatant_" + i + "_power");
                float agility = mem.getFloat("$ipc_arena_combatant_" + i + "_agility");
                float bravery = mem.getFloat("$ipc_arena_combatant_" + i + "_bravery");
                
                SpiralAbyssArena.SpiralGladiator g = new SpiralAbyssArena.SpiralGladiator(prefix, hullName, affix, hp, power, agility, bravery);
                g.hp = hp; // Restore current HP (might be different from max due to damage)
                g.isDead = mem.getBoolean("$ipc_arena_combatant_" + i + "_is_dead");
                g.kills = mem.getInt("$ipc_arena_combatant_" + i + "_kills");
                g.turnsSurvived = mem.getInt("$ipc_arena_combatant_" + i + "_turns_survived");
                arenaCombatants.add(g);
            }
        }
        
        // Restore bets
        int betsCount = mem.getInt("$ipc_arena_bets_count");
        arenaBets.clear();
        for (int i = 0; i < betsCount; i++) {
            int amount = mem.getInt("$ipc_arena_bet_" + i + "_amount");
            float multiplier = mem.getFloat("$ipc_arena_bet_" + i + "_multiplier");
            int roundPlaced = mem.getInt("$ipc_arena_bet_" + i + "_round_placed");
            String shipName = mem.getString("$ipc_arena_bet_" + i + "_ship_name");
            
            // Find the ship in restored combatants
            SpiralAbyssArena.SpiralGladiator betShip = null;
            for (SpiralAbyssArena.SpiralGladiator g : arenaCombatants) {
                if (g.fullName.equals(shipName)) {
                    betShip = g;
                    break;
                }
            }
            
            if (betShip != null) {
                arenaBets.add(new BetInfo(amount, multiplier, betShip, roundPlaced));
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
        
        // Clear the suspended game data
        mem.unset("$ipc_suspended_game_type");
        
        main.getTextPanel().addPara("Welcome back! The arena battle resumes where you left off.", Color.GREEN);
        main.getTextPanel().addPara("The crowd cheers as the combatants ready themselves.", Color.CYAN);
        
        // Show the battle status
        showArenaStatus();
    }

    /**
     * Checks if there's a suspended arena game available to resume
     */
    public boolean hasSuspendedArena() {
        com.fs.starfarer.api.campaign.rules.MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        String suspendedGameType = mem.getString("$ipc_suspended_game_type");
        return "Arena".equals(suspendedGameType);
    }
}