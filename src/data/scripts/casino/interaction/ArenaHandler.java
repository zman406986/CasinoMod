package data.scripts.casino.interaction;

import com.fs.starfarer.api.Global;
import data.scripts.casino.CasinoConfig;
import data.scripts.casino.CasinoGachaManager;
import data.scripts.casino.CasinoVIPManager;
import data.scripts.casino.SpiralAbyssArena;
import data.scripts.casino.util.LogFormatter;
import data.scripts.CasinoUIPanels;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public class ArenaHandler {

    private final CasinoInteraction main;
    
    protected SpiralAbyssArena activeArena;
    protected List<SpiralAbyssArena.SpiralGladiator> arenaCombatants;
    protected SpiralAbyssArena.SpiralGladiator chosenChampion;
    protected int opponentsDefeated;
    protected int currentRound;
    protected int currentBetAmount = CasinoConfig.ARENA_ENTRY_FEE;
    
    public static class BetInfo {
        public int amount;
        public float multiplier;
        public SpiralAbyssArena.SpiralGladiator ship;
        public int roundPlaced;
        public BetInfo(int a, float m, SpiralAbyssArena.SpiralGladiator s) { amount = a; multiplier = m; ship = s; roundPlaced = 0; }
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

        handlers.put("arena_watch_next", option -> simulateArenaStep());
        handlers.put("arena_skip", option -> {
            boolean result;
            do {
                result = simulateArenaStep();
            } while (result);
        });
        handlers.put("arena_suspend", option -> suspendArena());
        handlers.put("arena_switch", option -> showArenaSwitchMenu());
        handlers.put("arena_add_another_bet", option -> showAddAnotherBetMenu());
        handlers.put("arena_status", option -> showArenaStatus());
        handlers.put("start_battle_with_current_bet", option -> {
            int chosenIdx = -1;
            for (int i = 0; i < arenaCombatants.size(); i++) {
                if (arenaCombatants.get(i) == chosenChampion) {
                    chosenIdx = i;
                    break;
                }
            }
            if (chosenIdx != -1) {
                startArenaBattle(chosenIdx);
            }
        });
        
        predicateHandlers.put(option -> option.startsWith("arena_select_ship_"), option -> {
            int parsedIdx = Integer.parseInt(option.replace("arena_select_ship_", ""));
            showArenaConfirm(parsedIdx);
        });
        predicateHandlers.put(option -> option.startsWith("arena_bet_") && !option.contains("_bet"), option -> {
            int idx = Integer.parseInt(option.replace("arena_bet_", ""));
            showArenaConfirm(idx);
        });
        predicateHandlers.put(option -> option.startsWith("confirm_arena_bet_"), option -> {
            int parsedIdx = Integer.parseInt(option.replace("confirm_arena_bet_", ""));
            startArenaBattle(parsedIdx);
        });
        predicateHandlers.put(option -> option.startsWith("arena_confirm_add_another_bet_"), option -> {
            int parsedAmount = Integer.parseInt(option.replace("arena_confirm_add_another_bet_", ""));
            performAddAnotherBet(parsedAmount);
        });
        predicateHandlers.put(option -> option.startsWith("confirm_arena_add_another_bet_"), option -> {
            int parsedAmount = Integer.parseInt(option.replace("confirm_arena_add_another_bet_", ""));
            performAddAnotherBet(parsedAmount);
        });
        predicateHandlers.put(option -> option.startsWith("arena_add_bet_") && !option.equals("arena_add_bet_menu"), option -> {
            int parsedAdditionalAmount = Integer.parseInt(option.replace("arena_add_bet_", ""));
            confirmAddBet(parsedAdditionalAmount);
        });
        predicateHandlers.put(option -> option.startsWith("confirm_arena_add_bet_"), option -> {
            int parsedAdditionalAmount = Integer.parseInt(option.replace("confirm_arena_add_bet_", ""));
            addIncrementalBet(parsedAdditionalAmount);
        });
        predicateHandlers.put(option -> option.startsWith("arena_confirm_switch_"), option -> {
            int parsedIdx = Integer.parseInt(option.replace("arena_confirm_switch_", ""));
            performChampionSwitch(parsedIdx);
        });
        predicateHandlers.put(option -> option.startsWith("arena_select_champion_for_bet_"), option -> {
            int parsedIdx = Integer.parseInt(option.replace("arena_select_champion_for_bet_", ""));
            showBetAmountSelection(parsedIdx);
        });
        predicateHandlers.put(option -> option.startsWith("arena_confirm_add_bet_to_champ_"), option -> {
            // Parse both champion index and bet amount from the option string
            String[] parts = option.replace("arena_confirm_add_bet_to_champ_", "").split("_");
            int championIndex = Integer.parseInt(parts[0]);
            int amount = Integer.parseInt(parts[1]);
            performAddBetToChampion(championIndex, amount);
        });
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

    public void showArenaLobby() {
        main.options.clearOptions();
        
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
            
            // Create the full ship name string with odds
            String shipEntry = (i+1) + ". ";
            String prefixText = g.prefix + " ";
            String hullNameText = g.hullName + " ";
            String affixText = g.affix;
            String oddsText = " [" + g.getOddsString() + "]";
            String parenText = " (" + g.hullName + ")";
            
            // Add the entire string as one paragraph with default color (will be overridden by highlights)
            main.textPanel.addPara(shipEntry + prefixText + hullNameText + affixText + oddsText + parenText);
            
            // Highlight the hull name in yellow
            main.textPanel.highlightInLastPara(Color.YELLOW, hullNameText.trim());
            
            // Highlight the prefix in green/red based on whether it's positive or negative
            Color prefixHighlightColor = isPositiveAffix(g.prefix) ? Color.GREEN : Color.RED;
            main.textPanel.highlightInLastPara(prefixHighlightColor, prefixText.trim());
            
            // Highlight the affix in green/red based on whether it's positive or negative
            Color affixHighlightColor = isPositiveAffix(g.affix) ? Color.GREEN : Color.RED;
            main.textPanel.highlightInLastPara(affixHighlightColor, affixText);
            
            // Highlight the odds in yellow to make them stand out
            main.textPanel.highlightInLastPara(Color.YELLOW, oddsText);
            
            // Highlight the hull name in parentheses in gray
            main.textPanel.highlightInLastPara(Color.GRAY, parenText.trim());
            
            main.options.addOption(g.fullName + " [" + g.getOddsString() + "]", "arena_select_ship_" + i, Color.YELLOW, null);
        }
        
        main.options.addOption("How it Works", "how_to_arena");
        main.options.addOption("Back", "back_menu");
        main.setState(CasinoInteraction.State.ARENA);
    }

    /**
     * Determines if an affix or prefix is positive or negative based on the configuration
     */
    private boolean isPositiveAffix(String affixOrPrefix) {
        // Check if it's in the positive affix list
        if (CasinoConfig.ARENA_AFFIX_POS.contains(affixOrPrefix)) {
            return true;
        }
        
        // Check if it's in the negative affix list
        if (CasinoConfig.ARENA_AFFIX_NEG.contains(affixOrPrefix)) {
            return false;
        }
        
        // Check if it's in the positive prefix list
        if (CasinoConfig.ARENA_PREFIX_STRONG_POS.contains(affixOrPrefix)) {
            return true;
        }
        
        // Check if it's in the negative prefix list
        if (CasinoConfig.ARENA_PREFIX_STRONG_NEG.contains(affixOrPrefix)) {
            return false;
        }
        
        // Default to positive if not found in either list
        return true;
    }


    private void showArenaConfirm(int idx) {
        main.options.clearOptions();
        SpiralAbyssArena.SpiralGladiator g = arenaCombatants.get(idx);
        
        // Store the selected champion index temporarily
        chosenChampion = arenaCombatants.get(idx); // Set the champion here
        
        // Reset the bet amount to 0 and reset arena bets when a new champion is selected
        currentBetAmount = 0;
        arenaBets.clear();
        
        main.textPanel.setFontInsignia();
        
        // Combine champion selection text into a single paragraph
        String championText = "Selected champion: ";
        String prefixText = g.prefix + " ";
        String hullNameText = g.hullName + " ";
        String affixText = g.affix;
        
        main.textPanel.addPara(championText + prefixText + hullNameText + affixText);
        
        // Highlight the hull name in yellow
        main.textPanel.highlightInLastPara(Color.YELLOW, hullNameText.trim());
        
        // Highlight the prefix in green/red based on whether it's positive or negative
        Color prefixHighlightColor = isPositiveAffix(g.prefix) ? Color.GREEN : Color.RED;
        main.textPanel.highlightInLastPara(prefixHighlightColor, prefixText.trim());
        
        // Highlight the affix with appropriate color
        Color affixHighlightColor = isPositiveAffix(g.affix) ? Color.GREEN : Color.RED;
        main.textPanel.highlightInLastPara(affixHighlightColor, affixText);
        
        main.textPanel.addPara("Now add your bet amount:", Color.CYAN);
        
        // Go to add bet menu which loops back to itself until "Start Battle" is chosen
        showAddBetMenu();
    }
    
    private void addIncrementalBet(int additionalAmount) {
        // Check if player has enough gems or available credit
        int availableCredit = CasinoVIPManager.getAvailableCredit();
        int currentGems = CasinoVIPManager.getStargems();
        
        if (currentGems < additionalAmount && availableCredit < additionalAmount) {
            main.textPanel.addPara("Not enough Stargems or available credit! You have " + currentGems + " gems and " + availableCredit + " available credit.", Color.RED);
            showArenaStatus();
            return;
        }
        
        // Deduct the additional bet amount from player's gems (will go into debt if needed)
        CasinoVIPManager.addStargems(-additionalAmount);
        
        // Add the new bet with the same multiplier as the last bet, tracking the round
        float currentMultiplier = 1.0f;
        if (!arenaBets.isEmpty()) {
            currentMultiplier = arenaBets.get(arenaBets.size()-1).multiplier;
        }
        
        arenaBets.add(new BetInfo(additionalAmount, currentMultiplier, chosenChampion, currentRound));
        
        main.textPanel.addPara("Added " + additionalAmount + " Stargems to your bet. Total bet: " + getCurrentTotalBet() + " Stargems.", Color.GREEN);
        showArenaStatus();
    }
    
    private void showAddBetMenu() {
        main.options.clearOptions();
        main.textPanel.addPara("Add to your bet:", Color.YELLOW);
        main.textPanel.addPara("Current Bet: " + getCurrentTotalBet() + " Stargems", Color.CYAN);
        main.textPanel.addPara("Balance: " + CasinoVIPManager.getStargems() + " Stargems", Color.CYAN);
        
        int playerBalance = CasinoVIPManager.getStargems();
        
        // Create fixed increment options based on project specifications
        if (playerBalance >= 100) {
            main.options.addOption("Add 100 Stargems", "arena_add_bet_100");
        }
        if (playerBalance >= 500) {
            main.options.addOption("Add 500 Stargems", "arena_add_bet_500");
        }
        if (playerBalance >= 2000) {
            main.options.addOption("Add 2000 Stargems", "arena_add_bet_2000");
        }
        
        // Add percentage-based options based on player's available credit (remaining debt capacity) if in debt
        int availableCredit = CasinoVIPManager.getAvailableCredit();
        if (availableCredit > 0) {
            // Player has available credit, show percentage options based on that
            int tenPercent = (availableCredit * 10) / 100;
            if (tenPercent > 0 && playerBalance >= tenPercent) {
                main.options.addOption("Add " + tenPercent + " Stargems (10% of remaining credit)", "arena_add_bet_" + tenPercent);
            }
            
            int fiftyPercent = (availableCredit * 50) / 100;
            if (fiftyPercent > 0 && playerBalance >= fiftyPercent) {
                main.options.addOption("Add " + fiftyPercent + " Stargems (50% of remaining credit)", "arena_add_bet_" + fiftyPercent);
            }
        } else {
            // Player has no available credit, show percentage options based on current balance
            int tenPercent = (playerBalance * 10) / 100;
            if (tenPercent > 0 && playerBalance >= tenPercent) {
                main.options.addOption("Add " + tenPercent + " Stargems (10% of account)", "arena_add_bet_" + tenPercent);
            }
            
            int fiftyPercent = (playerBalance * 50) / 100;
            if (fiftyPercent > 0 && playerBalance >= fiftyPercent) {
                main.options.addOption("Add " + fiftyPercent + " Stargems (50% of account)", "arena_add_bet_" + fiftyPercent);
            }
        }
        
        // Add option to start battle with current bet amount
        if (getCurrentTotalBet() > 0) {
            main.options.addOption("Start Battle (" + getCurrentTotalBet() + " Stargems)", "start_battle_with_current_bet");
        }
        
        main.options.addOption("Cancel & Return to Arena Lobby", "arena_lobby");
    }
    
    private void confirmAddBet(int additionalAmount) {
        main.options.clearOptions();
        main.textPanel.addPara("Confirm additional bet amount:", Color.YELLOW);
        main.textPanel.addPara("Additional Bet: " + additionalAmount + " Stargems", Color.CYAN);
        main.textPanel.addPara("Total Bet after addition: " + (getCurrentTotalBet() + additionalAmount) + " Stargems", Color.CYAN);
        main.textPanel.addPara("Balance after bet: " + (CasinoVIPManager.getStargems() - additionalAmount) + " Stargems", Color.CYAN);
        
        main.options.addOption("Confirm Addition", "confirm_arena_add_bet_" + additionalAmount);
        main.options.addOption("Cancel", "arena_add_bet_menu");
    }

    private void startArenaBattle(int chosenIdx) {
        // Make sure we have a valid champion
        if (chosenIdx >= 0 && chosenIdx < arenaCombatants.size()) {
            chosenChampion = arenaCombatants.get(chosenIdx);
        }
        
        // Deduct the total bet amount from player's gems
        int totalBet = getCurrentTotalBet();
        
        // If no bets have been placed, add a default bet of 50
        if (totalBet <= 0) {
            totalBet = 50;
            arenaBets.add(new BetInfo(50, 1.0f, chosenChampion, 0));
        }
        
        // Make sure player has enough gems or available credit
        int availableCredit = CasinoVIPManager.getAvailableCredit();
        int currentGems = CasinoVIPManager.getStargems();
        
        if (currentGems < totalBet && availableCredit < totalBet) {
            main.textPanel.addPara("Not enough Stargems or available credit! You have " + currentGems + " gems and " + availableCredit + " available credit.", Color.RED);
            showAddBetMenu();
            return;
        }
        
        CasinoVIPManager.addStargems(-totalBet);
        
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
            
            main.textPanel.addPara("  - " + prefixText + hullNameText + affixText + betText);
            
            // Highlight the hull name in yellow
            main.textPanel.highlightInLastPara(Color.YELLOW, hullNameText.trim());
            
            // Highlight the prefix in green/red based on whether it's positive or negative
            Color prefixHighlightColor = isPositiveAffix(ship.prefix) ? Color.GREEN : Color.RED;
            main.textPanel.highlightInLastPara(prefixHighlightColor, prefixText.trim());
            
            // Highlight the affix with appropriate color
            Color affixHighlightColor = isPositiveAffix(ship.affix) ? Color.GREEN : Color.RED;
            main.textPanel.highlightInLastPara(affixHighlightColor, affixText);
            
            // Highlight the bet amount in yellow
            main.textPanel.highlightInLastPara(Color.YELLOW, getBetAmountForShip(ship) + " Stargems");
        }
        
        main.textPanel.addPara("Total Bet: " + totalBet + " Stargems", Color.YELLOW);
        
        main.dialog.getVisualPanel().showCustomPanel(400, 600, new CasinoUIPanels.ArenaUIPanel(main));
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
        boolean anyWinner = false;
        
        // Apply survival reward multiplier from config
        float survivalRewardMultiplier = CasinoConfig.ARENA_SURVIVAL_REWARD_MULT;
        
        for (SpiralAbyssArena.SpiralGladiator ship : betShips) {
            if (!ship.isDead) {
                // This ship won - calculate its reward
                // Performance bonuses are proportional modifiers to odds
                float survivalBonusMult = 1.0f + (ship.turnsSurvived * CasinoConfig.ARENA_SURVIVAL_BONUS_PER_TURN);
                float killBonusMult = 1.0f + (ship.kills * CasinoConfig.ARENA_KILL_BONUS_PER_KILL);
                float performanceMultiplier = survivalBonusMult * killBonusMult;
                
                for (BetInfo bet : arenaBets) {
                    if (bet.ship == ship) {
                        // Calculate diminishing returns for mid-round bets
                        // The later the bet is placed, the lower the effective multiplier
                        float diminishingReturns = 1.0f;
                        if (bet.roundPlaced > 0) {
                            // Each round after the first reduces the effective multiplier by 10%
                            diminishingReturns = 1.0f - (bet.roundPlaced * 0.1f);
                            diminishingReturns = Math.max(0.5f, diminishingReturns); // Minimum 50% effectiveness
                        }
                        
                        // Base multiplier is the ship's odds, modified by performance and diminishing returns
                        float effectiveMultiplier = ship.odds * performanceMultiplier * diminishingReturns;
                        int betReward = (int)(bet.amount * effectiveMultiplier * survivalRewardMultiplier);
                        totalWinReward += betReward;
                    }
                }
                anyWinner = true;
            }
        }
        
        if (anyWinner) {
            CasinoVIPManager.addStargems(totalWinReward);
            
            main.textPanel.setFontInsignia();
            main.textPanel.addPara("VICTORY! Your champions have triumphed!", Color.GREEN);
            
            // Display each winning ship
            for (SpiralAbyssArena.SpiralGladiator ship : betShips) {
                if (!ship.isDead) {
                    String prefixText = ship.prefix + " ";
                    String hullNameText = ship.hullName + " ";
                    String affixText = ship.affix;
                    
                    main.textPanel.addPara("  - " + prefixText + hullNameText + affixText + " survived!");
                    
                    // Highlight the hull name in yellow
                    main.textPanel.highlightInLastPara(Color.YELLOW, hullNameText.trim());
                    
                    // Highlight the prefix in green/red based on whether it's positive or negative
                    Color prefixHighlightColor = isPositiveAffix(ship.prefix) ? Color.GREEN : Color.RED;
                    main.textPanel.highlightInLastPara(prefixHighlightColor, prefixText.trim());
                    
                    // Highlight the affix with appropriate color
                    Color affixHighlightColor = isPositiveAffix(ship.affix) ? Color.GREEN : Color.RED;
                    main.textPanel.highlightInLastPara(affixHighlightColor, affixText);
                }
            }
            
            main.getTextPanel().addPara("Total Win Reward: " + totalWinReward + " Stargems", Color.GREEN);
        } else {
            main.getTextPanel().addPara("DEFEAT. All your champions have been decommissioned.", Color.RED);
            main.getTextPanel().addPara("No rewards earned for defeat.", Color.GRAY);
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
            main.getTextPanel().addPara("    Performance Bonus: +" + performanceMultiplierBonus + " to multiplier", Color.CYAN);
        }
        
        if (anyWinner) {
            main.getTextPanel().addPara("Net Profit: " + (totalWinReward - totalBet) + " Stargems", Color.GREEN);
        } else {
            main.getTextPanel().addPara("Net Result: " + (-totalBet) + " Stargems", Color.ORANGE);
        }
        
        // Show the winner announcement panel
        int netResult = anyWinner ? (totalWinReward - totalBet) : (-totalBet);
        
        // Create the winner announcement panel with actual winner's stats
        data.scripts.CasinoUIPanels.ArenaWinnerAnnouncementPanel winnerPanel = 
            new data.scripts.CasinoUIPanels.ArenaWinnerAnnouncementPanel(
                anyWinner,
                winnerName,
                actualWinner != null ? actualWinner.turnsSurvived : 0,
                actualWinner != null ? actualWinner.kills : 0,
                anyWinner ? totalWinReward : 0,
                netResult
            );
        
        // Show the custom panel using LunaLib
        try {
            main.dialog.getVisualPanel().showCustomPanel(600, 800, winnerPanel);
        } catch (Exception e) {
            Global.getLogger(this.getClass()).warn("Could not show winner announcement popup: " + e.getMessage());
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
    
    private void showArenaSwitchMenu() {
        main.getOptions().clearOptions();
        main.getTextPanel().addPara("Select a new champion to switch to:", Color.YELLOW);
        main.getTextPanel().addPara("Current Bet: " + currentBetAmount + " Stargems", Color.CYAN);
        
        for (int i = 0; i < arenaCombatants.size(); i++) {
            SpiralAbyssArena.SpiralGladiator g = arenaCombatants.get(i);
            if (!g.isDead && g != chosenChampion) {
                // Check if there are any bets before applying switching penalty
                float penaltyFee = 0f;
                float newMultiplier = 1.0f;
                if (!arenaBets.isEmpty()) {
                    // Apply the switching penalty: 50% fee and halved multiplier
                    penaltyFee = arenaBets.get(arenaBets.size()-1).amount * 0.5f;
                    newMultiplier = arenaBets.get(arenaBets.size()-1).multiplier * 0.5f;
                }
                
                main.textPanel.setFontInsignia();
                
                // Combine all ship name parts into a single paragraph to avoid excessive line breaks
                String shipInfo = (i+1) + ". ";
                String prefixText = g.prefix + " ";
                String hullNameText = g.hullName + " ";
                String affixText = g.affix;
                String oddsText = " [" + g.getOddsString() + "]";
                String extraInfo = " (Fee: " + (int)penaltyFee + " Gems, Multiplier: x" + newMultiplier + ")";
                
                main.textPanel.addPara(shipInfo + prefixText + hullNameText + affixText + oddsText + extraInfo);
                
                // Highlight the hull name in yellow
                main.textPanel.highlightInLastPara(Color.YELLOW, hullNameText.trim());
                
                // Highlight the prefix in green/red based on whether it's positive or negative
                Color prefixHighlightColor = isPositiveAffix(g.prefix) ? Color.GREEN : Color.RED;
                main.textPanel.highlightInLastPara(prefixHighlightColor, prefixText.trim());
                
                // Highlight the affix in green/red based on whether it's positive or negative
                Color affixHighlightColor = isPositiveAffix(g.affix) ? Color.GREEN : Color.RED;
                main.textPanel.highlightInLastPara(affixHighlightColor, affixText);
                
                // Highlight the odds in yellow to make them stand out
                main.textPanel.highlightInLastPara(Color.YELLOW, oddsText);
                
                // Use the full name for the option ID but display text can be customized
                main.getOptions().addOption("Switch to " + g.prefix + " " + g.hullName + " " + g.affix + " [" + g.getOddsString() + "]", "arena_confirm_switch_" + i, Color.YELLOW, null);
            }
        }
        main.getOptions().addOption("Cancel", "arena_status");
    }
    
    private void performChampionSwitch(int newChampionIndex) {
        // Check if there are any bets before calculating penalty
        if (arenaBets.isEmpty()) {
            main.getTextPanel().addPara("No active bet to switch. Please place a bet first.", Color.RED);
            showArenaStatus();
            return;
        }
        
        // Calculate switching penalty
        int penaltyFee = (int)(arenaBets.get(arenaBets.size()-1).amount * 0.5);
        
        // Check if player has enough gems to pay the switching penalty
        if (CasinoVIPManager.getStargems() < penaltyFee) {
            main.getTextPanel().addPara("Not enough Stargems to switch champion! You need " + penaltyFee + " but only have " + CasinoVIPManager.getStargems() + ".", Color.RED);
            showArenaStatus(); // Return to the arena status menu
            return;
        }
        
        CasinoVIPManager.addStargems(-penaltyFee);
        
        // Update multiplier to half of current
        float currentMultiplier = arenaBets.get(arenaBets.size()-1).multiplier;
        float newMultiplier = currentMultiplier * 0.5f;
        
        // Set the new champion
        chosenChampion = arenaCombatants.get(newChampionIndex);
        
        // Add a new bet with the reduced multiplier - separate bet like horse betting
        arenaBets.add(new BetInfo(penaltyFee, newMultiplier, chosenChampion, currentRound));
        
        main.getTextPanel().addPara("Switched to " + chosenChampion.fullName + ". Applied switching penalties (50% fee).", Color.YELLOW);
        showArenaStatus();
    }
    
    private void showBetAmountSelection(int championIndex) {
        // Store the selected champion for betting
        SpiralAbyssArena.SpiralGladiator selectedChampion = arenaCombatants.get(championIndex);
        
        main.getOptions().clearOptions();
        main.getTextPanel().addPara("Add a bet on " + selectedChampion.fullName + ":", Color.YELLOW);
        
        // Show champion details with proper color coding
        main.textPanel.setFontInsignia();
        String prefixText = selectedChampion.prefix + " ";
        String hullNameText = selectedChampion.hullName + " ";
        String affixText = selectedChampion.affix;
        String oddsText = " [" + selectedChampion.getOddsString() + "]";
        
        main.textPanel.addPara(prefixText + hullNameText + affixText + oddsText);
        
        // Highlight the hull name in yellow
        main.textPanel.highlightInLastPara(Color.YELLOW, hullNameText.trim());
        
        // Highlight the prefix in green/red based on whether it's positive or negative
        Color prefixHighlightColor = isPositiveAffix(selectedChampion.prefix) ? Color.GREEN : Color.RED;
        main.textPanel.highlightInLastPara(prefixHighlightColor, prefixText.trim());
        
        // Highlight the affix in green/red based on whether it's positive or negative
        Color affixHighlightColor = isPositiveAffix(selectedChampion.affix) ? Color.GREEN : Color.RED;
        main.textPanel.highlightInLastPara(affixHighlightColor, affixText);
        
        // Highlight the odds in yellow to make them stand out
        main.textPanel.highlightInLastPara(Color.YELLOW, oddsText);
        
        main.getTextPanel().addPara("Current Total Bet: " + getCurrentTotalBet() + " Stargems", Color.CYAN);
        main.getTextPanel().addPara("Balance: " + CasinoVIPManager.getStargems() + " Stargems", Color.CYAN);
        
        int playerBalance = CasinoVIPManager.getStargems();
        
        // Create fixed increment options based on project specifications (same as in showAddBetMenu)
        if (playerBalance >= 100) {
            main.getOptions().addOption("Add 100 Stargems", "arena_confirm_add_bet_to_champ_" + championIndex + "_100");
        }
        if (playerBalance >= 500) {
            main.getOptions().addOption("Add 500 Stargems", "arena_confirm_add_bet_to_champ_" + championIndex + "_500");
        }
        if (playerBalance >= 2000) {
            main.getOptions().addOption("Add 2000 Stargems", "arena_confirm_add_bet_to_champ_" + championIndex + "_2000");
        }
        
        // Add percentage-based options based on player's available credit (remaining debt capacity) if in debt
        int availableCredit = CasinoVIPManager.getAvailableCredit();
        if (availableCredit > 0) {
            // Player has available credit, show percentage options based on that
            int tenPercent = (availableCredit * 10) / 100;
            if (tenPercent > 0 && playerBalance >= tenPercent) {
                main.getOptions().addOption("Add " + tenPercent + " Stargems (10% of remaining credit)", "arena_confirm_add_bet_to_champ_" + championIndex + "_" + tenPercent);
            }
            
            int fiftyPercent = (availableCredit * 50) / 100;
            if (fiftyPercent > 0 && playerBalance >= fiftyPercent) {
                main.getOptions().addOption("Add " + fiftyPercent + " Stargems (50% of remaining credit)", "arena_confirm_add_bet_to_champ_" + championIndex + "_" + fiftyPercent);
            }
        } else {
            // Player has no available credit, show percentage options based on current balance
            int tenPercent = (playerBalance * 10) / 100;
            if (tenPercent > 0 && playerBalance >= tenPercent) {
                main.getOptions().addOption("Add " + tenPercent + " Stargems (10% of account)", "arena_confirm_add_bet_to_champ_" + championIndex + "_" + tenPercent);
            }
            
            int fiftyPercent = (playerBalance * 50) / 100;
            if (fiftyPercent > 0 && playerBalance >= fiftyPercent) {
                main.getOptions().addOption("Add " + fiftyPercent + " Stargems (50% of account)", "arena_confirm_add_bet_to_champ_" + championIndex + "_" + fiftyPercent);
            }
        }
        
        main.getOptions().addOption("Back to Champion Selection", "arena_add_another_bet");
    }
    
    private void processLogEntry(String logEntry) {
        // Use the LogFormatter utility to process the log entry
        LogFormatter.processLogEntry(logEntry, main.textPanel, arenaCombatants);
        
        // Display bet amount and current odds if the player bet on this ship
        for (SpiralAbyssArena.SpiralGladiator g : arenaCombatants) {
            if (logEntry.startsWith(g.shortName + ": ")) {
                // Check if player bet on this ship
                int totalBetOnShip = 0;
                for (BetInfo bet : arenaBets) {
                    if (bet.ship == g) {
                        totalBetOnShip += bet.amount;
                    }
                }
                if (totalBetOnShip > 0) {
                    // Player bet on this ship - show bet amount and current odds
                    String betText = " [Bet: " + totalBetOnShip + " Stargems, Odds: " + g.getOddsString() + "]";
                    main.textPanel.addPara(betText, Color.CYAN);
                } else {
                    // Player did not bet on this ship
                    String betText = " [No Bet]";
                    main.textPanel.addPara(betText, Color.GRAY);
                }
                break;
            }
        }
    }
    
    private void showAddAnotherBetMenu() {
        // First, show champions to bet on
        main.getOptions().clearOptions();
        main.getTextPanel().addPara("Choose a champion to place a bet on:", Color.YELLOW);
        main.getTextPanel().addPara("Current Total Bet: " + getCurrentTotalBet() + " Stargems", Color.CYAN);
        main.getTextPanel().addPara("Balance: " + CasinoVIPManager.getStargems() + " Stargems", Color.CYAN);
        
        // Show all alive champions as options
        for (int i = 0; i < arenaCombatants.size(); i++) {
            SpiralAbyssArena.SpiralGladiator g = arenaCombatants.get(i);
            if (!g.isDead) { // Only show alive champions
                // Combine all ship name parts into a single paragraph to avoid excessive line breaks
                main.textPanel.setFontInsignia();
                
                String prefixText = g.prefix + " ";
                String hullNameText = g.hullName + " ";
                String affixText = g.affix;
                String oddsText = " [" + g.getOddsString() + "]";
                
                main.textPanel.addPara(prefixText + hullNameText + affixText + oddsText);
                
                // Highlight the hull name in yellow
                main.textPanel.highlightInLastPara(Color.YELLOW, hullNameText.trim());
                
                // Highlight the prefix in green/red based on whether it's positive or negative
                Color prefixHighlightColor = isPositiveAffix(g.prefix) ? Color.GREEN : Color.RED;
                main.textPanel.highlightInLastPara(prefixHighlightColor, prefixText.trim());
                
                // Highlight the affix in green/red based on whether it's positive or negative
                Color affixHighlightColor = isPositiveAffix(g.affix) ? Color.GREEN : Color.RED;
                main.textPanel.highlightInLastPara(affixHighlightColor, affixText);
                
                // Highlight the odds in yellow to make them stand out
                main.textPanel.highlightInLastPara(Color.YELLOW, oddsText);
                
                // Add option to select this champion for betting
                main.getOptions().addOption("Bet on " + g.prefix + " " + g.hullName + " " + g.affix + " [" + g.getOddsString() + "]", "arena_select_champion_for_bet_" + i, Color.YELLOW, null);
            }
        }
        
        main.getOptions().addOption("Back to Battle Options", "arena_status");
    }
    
    /**
     * Gets the total amount currently bet on the champion
     */
    private int getCurrentTotalBet() {
        int total = 0;
        for (BetInfo bet : arenaBets) {
            total += bet.amount;
        }
        return total;
    }
    
    private void performAddAnotherBet(int additionalAmount) {
        // Check if player has enough gems to add another bet
        if (CasinoVIPManager.getStargems() < additionalAmount) {
            main.getTextPanel().addPara("Not enough Stargems! You need " + additionalAmount + " but only have " + CasinoVIPManager.getStargems() + ".", Color.RED);
            showArenaStatus(); // Return to the arena status menu
            return;
        }
        
        // Deduct the additional bet amount from player's gems
        CasinoVIPManager.addStargems(-additionalAmount);
        
        // Add a new bet with a base multiplier of 1.0 - each bet is locked in separately like horse betting
        arenaBets.add(new BetInfo(additionalAmount, 1.0f, chosenChampion, currentRound));
        
        main.getTextPanel().addPara("Added another bet of " + additionalAmount + " Stargems on " + chosenChampion.fullName + ".", Color.YELLOW);
        showArenaStatus();
    }
    
    private void performAddBetToChampion(int championIndex, int additionalAmount) {
        // Check if player has enough gems to add another bet
        if (CasinoVIPManager.getStargems() < additionalAmount) {
            main.getTextPanel().addPara("Not enough Stargems! You need " + additionalAmount + " but only have " + CasinoVIPManager.getStargems() + ".", Color.RED);
            showArenaStatus(); // Return to the arena status menu
            return;
        }
        
        // Check if the champion is still alive
        SpiralAbyssArena.SpiralGladiator targetChampion = arenaCombatants.get(championIndex);
        if (targetChampion.isDead) {
            main.getTextPanel().addPara("Cannot place bet on " + targetChampion.fullName + ", the champion has been defeated!", Color.RED);
            showArenaStatus(); // Return to the arena status menu
            return;
        }
        
        // Deduct the additional bet amount from player's gems
        CasinoVIPManager.addStargems(-additionalAmount);
        
        // Add a new bet with a base multiplier of 1.0 - each bet is locked in separately like horse betting
        arenaBets.add(new BetInfo(additionalAmount, 1.0f, targetChampion, currentRound));
        
        main.getTextPanel().addPara("Added another bet of " + additionalAmount + " Stargems on " + targetChampion.fullName + ".", Color.YELLOW);
        showArenaStatus();
    }
    
    private void showCustomAddAnotherBetMenu() {
        main.options.clearOptions();
        main.textPanel.addPara("Enter Custom Bet Amount:", Color.YELLOW);
        int playerBalance = CasinoVIPManager.getStargems();
        main.textPanel.addPara("Balance: " + playerBalance + " Stargems", Color.CYAN);
        int currentBet = getCurrentTotalBet();
        main.textPanel.addPara("Current Total Bet: " + currentBet + " Stargems", Color.CYAN);
        
        // Add some suggested amounts based on current bet (similar to initial bet menu)
        int[] suggestedAmounts = {currentBet/4, currentBet/2, currentBet, currentBet*2, currentBet*3, currentBet*5};
        
        for (int amount : suggestedAmounts) {
            if (amount > 0 && amount <= playerBalance) {
                main.options.addOption("Add " + amount + " Stargems", "arena_confirm_add_another_bet_" + amount);
            }
        }
        
        // Add percentage-based options for larger increases
        int[] percentages = {10, 30, 50, 70, 100}; // 10%, 30%, 50%, 70%, 100%
        for (int percent : percentages) {
            int amount = (playerBalance * percent) / 100;
            if (amount > 0 && amount <= playerBalance) {
                main.options.addOption(percent + "% of Balance (" + amount + " Gems)", "arena_confirm_add_another_bet_" + amount);
            }
        }
        
        main.options.addOption("Back to Add Another Bet Menu", "arena_add_another_bet");
    }
    
    private void confirmAddAnotherBet(int additionalAmount) {
        // Check if player has enough gems to add another bet
        if (CasinoVIPManager.getStargems() < additionalAmount) {
            main.getTextPanel().addPara("Not enough Stargems! You need " + additionalAmount + " but only have " + CasinoVIPManager.getStargems() + ".", Color.RED);
            showArenaStatus(); // Return to the arena status menu
            return;
        }
        
        // Deduct the additional bet amount from player's gems
        CasinoVIPManager.addStargems(-additionalAmount);
        
        // Add a new bet with a base multiplier of 1.0 - each bet is locked in separately like horse betting
        arenaBets.add(new BetInfo(additionalAmount, 1.0f, chosenChampion, currentRound));
        
        main.getTextPanel().addPara("Added another bet of " + additionalAmount + " Stargems.", Color.YELLOW);
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
        main.getOptions().addOption("Leave", "leave_now");
    }
    
    public void restoreSuspendedArena() {
        com.fs.starfarer.api.campaign.rules.MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
        
        // Check if arena game state exists in memory
        if (mem.contains("$ipc_arena_current_round")) {
            // Restore the game state from memory
            currentRound = mem.getInt("$ipc_arena_current_round");
            opponentsDefeated = mem.getInt("$ipc_arena_opponents_defeated");
            
            // Restore combatants
            int combatantCount = mem.getInt("$ipc_arena_combatant_count");
            arenaCombatants = new ArrayList<>();
            for (int i = 0; i < combatantCount; i++) {
                String prefix = mem.getString("$ipc_arena_combatant_" + i + "_prefix");
                String hullName = mem.getString("$ipc_arena_combatant_" + i + "_hull_name");
                String affix = mem.getString("$ipc_arena_combatant_" + i + "_affix");
                int hp = mem.getInt("$ipc_arena_combatant_" + i + "_hp");
                int maxHp = mem.getInt("$ipc_arena_combatant_" + i + "_max_hp");
                int power = mem.getInt("$ipc_arena_combatant_" + i + "_power");
                float agility = mem.getFloat("$ipc_arena_combatant_" + i + "_agility");
                float bravery = mem.getFloat("$ipc_arena_combatant_" + i + "_bravery");
                boolean isDead = mem.getBoolean("$ipc_arena_combatant_" + i + "_is_dead");
                int kills = mem.getInt("$ipc_arena_combatant_" + i + "_kills");
                int turnsSurvived = mem.getInt("$ipc_arena_combatant_" + i + "_turns_survived");
                
                SpiralAbyssArena.SpiralGladiator g = new SpiralAbyssArena.SpiralGladiator(prefix, hullName, affix, hp, power, agility, bravery);
                g.maxHp = maxHp;
                g.isDead = isDead;
                g.kills = kills;
                g.turnsSurvived = turnsSurvived;
                arenaCombatants.add(g);
            }
            
            // Restore bets
            int betCount = mem.getInt("$ipc_arena_bets_count");
            arenaBets = new ArrayList<>();
            for (int i = 0; i < betCount; i++) {
                int amount = mem.getInt("$ipc_arena_bet_" + i + "_amount");
                float multiplier = mem.getFloat("$ipc_arena_bet_" + i + "_multiplier");
                int roundPlaced = mem.getInt("$ipc_arena_bet_" + i + "_round_placed");
                String shipName = mem.getString("$ipc_arena_bet_" + i + "_ship_name");
                
                // Find the corresponding ship in the restored combatants
                SpiralAbyssArena.SpiralGladiator targetShip = null;
                if (shipName != null) {
                    for (SpiralAbyssArena.SpiralGladiator g : arenaCombatants) {
                        if (g.fullName.equals(shipName)) {
                            targetShip = g;
                            break;
                        }
                    }
                }
                
                BetInfo bet = new BetInfo(amount, multiplier, targetShip, roundPlaced);
                arenaBets.add(bet);
            }
            
            // Calculate how long player was away for the joke
            long suspendTime = mem.getLong("$ipc_arena_suspend_time");
            long currentTime = Global.getSector().getClock().getTimestamp();
            float daysAway = (currentTime - suspendTime) / 30f; // Approximate days (30 days per month)
            
            // Visual Panel
            main.dialog.getVisualPanel().showCustomPanel(400, 600, new CasinoUIPanels.ArenaUIPanel(main));
            
            // Inform player that game has been resumed with joke
            main.getTextPanel().addPara("The arena announcer's voice echoes through the hall, dripping with sarcasm.", Color.CYAN);
            main.getTextPanel().addPara("'Ladies and gentlemen, our honored guest has finally returned after " + String.format("%.1f", daysAway) + " days.'", Color.YELLOW);
            main.getTextPanel().addPara("'The combatants have been standing here waiting. Let's continue... grudgingly.'", Color.GRAY);
            main.getTextPanel().addPara("The crowd boos half-heartedly.", Color.GRAY);
            
            // Update UI with restored game state
            showArenaStatus();
        } else {
            // If no stored state, start a fresh arena
            showArenaLobby();
        }
    }
}