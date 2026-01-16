package data.scripts.casino.interaction;

import data.scripts.casino.CasinoConfig;
import data.scripts.casino.CasinoGachaManager;
import data.scripts.casino.CasinoVIPManager;
import data.scripts.casino.SpiralAbyssArena;
import data.scripts.CasinoUIPanels;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Manages the Spiral Abyss Arena interaction flow.
 * Interfaces with the SpiralAbyssArena simulation engine.
 * Supports bet increase functionality and percentage-based custom bet amounts.
 */
public class ArenaHandler {

    private final CasinoInteraction main;
    
    // State
    /**
     * The active arena instance running the battle simulation
     */
    protected SpiralAbyssArena activeArena;
    
    /**
     * List of ships participating in the current battle
     */
    protected List<SpiralAbyssArena.SpiralGladiator> arenaCombatants;
    
    /**
     * The player's chosen champion ship
     */
    protected SpiralAbyssArena.SpiralGladiator chosenChampion;
    
    /**
     * Number of opponents defeated in the current battle
     */
    protected int opponentsDefeated;
    
    /**
     * Number of turns the player's champion has survived
     */
    protected int turnsSurvived;
    
    /**
     * Current bet amount selected by the player
     */
    protected int currentBetAmount = CasinoConfig.ARENA_ENTRY_FEE;
    
    /**
     * Information about bets placed during the arena battle
     */
    public static class BetInfo {
        public int amount;      // Amount bet
        public float multiplier; // Current multiplier
        public BetInfo(int a, float m) { amount = a; multiplier = m; }
    }
    
    /**
     * List of all bets placed in the current battle
     */
    protected List<BetInfo> arenaBets = new ArrayList<>();
    
    private final Map<String, OptionHandler> handlers = new HashMap<>();
    private final Map<Predicate<String>, OptionHandler> predicateHandlers = new HashMap<>();

    public ArenaHandler(CasinoInteraction main) {
        this.main = main;
        initializeHandlers();
    }
    
    private void initializeHandlers() {
        // Exact match handlers
        handlers.put("arena_lobby", option -> showArenaLobby());
        handlers.put("arena_add_bet_menu", option -> showAddBetMenu());
        handlers.put("how_to_arena", option -> main.help.showArenaHelp());
        handlers.put("back_menu", option -> main.showMenu());

        handlers.put("arena_watch_next", option -> simulateArenaStep());
        handlers.put("arena_skip", option -> {
            boolean result;
            do {
                result = simulateArenaStep();
            } while (result); // Continue simulating until the battle ends
        });
        handlers.put("arena_switch", option -> showArenaSwitchMenu());
        handlers.put("arena_increase_bet", option -> showIncreaseBetMenu());
        handlers.put("arena_status", option -> showArenaStatus());
        handlers.put("start_battle_with_current_bet", option -> {
            // Find the index of the chosen champion
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
        
        // Predicate-based handlers for pattern matching
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
        predicateHandlers.put(option -> option.startsWith("arena_confirm_increase_bet_"), option -> {
            int parsedAmount = Integer.parseInt(option.replace("arena_confirm_increase_bet_", ""));
            performBetIncrease(parsedAmount);
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
    }

    /**
     * Handles arena menu options including lobby, betting, and battle simulation
     */
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

    /**
     * Displays the arena lobby with ship selection and betting options
     */
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
        main.textPanel.addPara("\nSelect a champion to battle with:", Color.YELLOW);
        
        for (int i=0; i<arenaCombatants.size(); i++) {
            SpiralAbyssArena.SpiralGladiator g = arenaCombatants.get(i);
            
            // Combine all ship name parts into a single paragraph to avoid excessive line breaks
            main.textPanel.setFontInsignia();
            
            // Create the full ship name string
            String shipEntry = (i+1) + ". " + g.prefix + " " + g.hullName + " " + g.affix + " (" + g.hullName + ")\n";
            
            // Add the entire string as one paragraph with prefix color as default
            Color prefixColor = isPositiveAffix(g.prefix) ? Color.GREEN : Color.RED;
            main.textPanel.addPara(shipEntry, prefixColor);
            
            // Highlight the hull name in white
            main.textPanel.highlightInLastPara(Color.WHITE, g.hullName + " ");
            
            // Highlight the affix with appropriate color
            Color affixColor = isPositiveAffix(g.affix) ? Color.GREEN : Color.RED;
            main.textPanel.highlightInLastPara(affixColor, g.affix);
            
            // Highlight the hull name in parentheses in gray
            main.textPanel.highlightInLastPara(Color.GRAY, "(" + g.hullName + ")");
            
            main.options.addOption(g.fullName, "arena_select_ship_" + i);
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


    /**
     * Shows confirmation dialog for arena battle
     */
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
        Color prefixColor = isPositiveAffix(g.prefix) ? Color.GREEN : Color.RED;
        Color affixColor = isPositiveAffix(g.affix) ? Color.GREEN : Color.RED;
        
        String championText = "Selected champion: " + g.prefix + " " + g.hullName + " " + g.affix;
        main.textPanel.addPara(championText, prefixColor);
        
        // Highlight the hull name in white
        main.textPanel.highlightInLastPara(Color.WHITE, g.hullName + " ");
        
        // Highlight the affix with appropriate color
        main.textPanel.highlightInLastPara(affixColor, g.affix);
        
        main.textPanel.addPara("Now add your bet amount:", Color.CYAN);
        
        // Go to add bet menu which loops back to itself until "Start Battle" is chosen
        showAddBetMenu();
    }
    
    /**
     * Adds an incremental bet to the current arena battle
     */
    private void addIncrementalBet(int additionalAmount) {
        if (CasinoVIPManager.getStargems() < additionalAmount) {
            main.textPanel.addPara("Not enough Stargems! You only have " + CasinoVIPManager.getStargems() + ".", Color.RED);
            showArenaStatus();
            return;
        }
        
        // Deduct the additional bet amount from player's gems
        CasinoVIPManager.addStargems(-additionalAmount);
        
        // Add the new bet with the same multiplier as the last bet
        float currentMultiplier = 1.0f;
        if (!arenaBets.isEmpty()) {
            currentMultiplier = arenaBets.get(arenaBets.size()-1).multiplier;
        }
        
        arenaBets.add(new BetInfo(additionalAmount, currentMultiplier));
        
        main.textPanel.addPara("Added " + additionalAmount + " Stargems to your bet. Total bet: " + getCurrentTotalBet() + " Stargems.", Color.GREEN);
        showArenaStatus();
    }
    
    /**
     * Shows incremental bet addition menu
     */
    private void showAddBetMenu() {
        main.options.clearOptions();
        main.textPanel.addPara("\nAdd to your bet:", Color.YELLOW);
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
        
        // Add percentage-based options
        int tenPercent = (playerBalance * 10) / 100;
        if (tenPercent > 0 && playerBalance >= tenPercent) {
            main.options.addOption("Add " + tenPercent + " Stargems (10% of account)", "arena_add_bet_" + tenPercent);
        }
        
        int fiftyPercent = (playerBalance * 50) / 100;
        if (fiftyPercent > 0 && playerBalance >= fiftyPercent) {
            main.options.addOption("Add " + fiftyPercent + " Stargems (50% of account)", "arena_add_bet_" + fiftyPercent);
        }
        
        // Add option to start battle with current bet amount
        if (getCurrentTotalBet() > 0) {
            main.options.addOption("Start Battle (" + getCurrentTotalBet() + " Stargems)", "start_battle_with_current_bet");
        }
        
        main.options.addOption("Cancel & Return to Arena Lobby", "arena_lobby");
    }
    
    /**
     * Confirms the selected incremental bet amount with the player
     */
    private void confirmAddBet(int additionalAmount) {
        main.options.clearOptions();
        main.textPanel.addPara("\nConfirm additional bet amount:", Color.YELLOW);
        main.textPanel.addPara("Additional Bet: " + additionalAmount + " Stargems", Color.CYAN);
        main.textPanel.addPara("Total Bet after addition: " + (getCurrentTotalBet() + additionalAmount) + " Stargems", Color.CYAN);
        main.textPanel.addPara("Balance after bet: " + (CasinoVIPManager.getStargems() - additionalAmount) + " Stargems", Color.CYAN);
        
        main.options.addOption("Confirm Addition", "confirm_arena_add_bet_" + additionalAmount);
        main.options.addOption("Cancel", "arena_add_bet_menu");
    }

    /**
     * Starts the arena battle with selected champion
     */
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
            arenaBets.add(new BetInfo(50, 1.0f));
        }
        
        // Make sure player has enough gems
        if (CasinoVIPManager.getStargems() < totalBet) {
            main.textPanel.addPara("Not enough Stargems! You only have " + CasinoVIPManager.getStargems() + ".", Color.RED);
            showAddBetMenu();
            return;
        }
        
        CasinoVIPManager.addStargems(-totalBet);
        
        opponentsDefeated = 0;
        turnsSurvived = 0;
        
        // Announce the battle with the chosen champion using combined text to avoid line breaks
        main.textPanel.setFontInsignia();
        
        Color prefixColor = isPositiveAffix(chosenChampion.prefix) ? Color.GREEN : Color.RED;
        Color affixColor = isPositiveAffix(chosenChampion.affix) ? Color.GREEN : Color.RED;
        
        // Combine all text parts into a single paragraph
        String battleStartText = "The battle begins! Your champion " + chosenChampion.prefix + " " + 
                                 chosenChampion.hullName + " " + chosenChampion.affix + " enters the arena! (Bet: " + 
                                 totalBet + " Stargems)";
        main.textPanel.addPara(battleStartText, prefixColor);
        
        // Highlight the hull name in white
        main.textPanel.highlightInLastPara(Color.WHITE, chosenChampion.hullName + " ");
        
        // Highlight the affix with appropriate color
        main.textPanel.highlightInLastPara(affixColor, chosenChampion.affix);
        
        main.dialog.getVisualPanel().showCustomPanel(400, 600, new CasinoUIPanels.ArenaUIPanel(main));
        simulateArenaStep();
    }

    /**
     * Simulates one step of the arena battle
     */
    private boolean simulateArenaStep() {
        if (!chosenChampion.isDead) turnsSurvived++;
        List<String> logEntries = activeArena.simulateStep(arenaCombatants);
        
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

    /**
     * Shows arena status with options to continue battle
     */
    private void showArenaStatus() {
        main.getOptions().clearOptions();
        main.getOptions().addOption("Watch Next Round", "arena_watch_next");
        main.getOptions().addOption("Skip to End", "arena_skip");
        main.getOptions().addOption("Increase Bet on Current Champion", "arena_increase_bet");
        main.getOptions().addOption("Switch Champion (50% Fee)", "arena_switch");
    }

    /**
     * Processes the end of arena battle and calculates rewards
     */
    private void finishArenaBattle() {
        main.getOptions().clearOptions();
        
        boolean championWon = !chosenChampion.isDead;
        
        // Calculate rewards based on performance (survival and kills) regardless of win/loss
        int totalBet = getCurrentTotalBet(); // Use the total accumulated bet amount
        int survivalReward = turnsSurvived * CasinoConfig.ARENA_SURVIVAL_REWARD_PER_TURN; // Configurable gems per turn survived
        int killReward = chosenChampion.kills * CasinoConfig.ARENA_KILL_REWARD_PER_KILL; // Configurable gems per kill
        int performanceBonus = survivalReward + killReward;
        
        // Determine the actual winner (could be different from chosen champion if player switched)
        SpiralAbyssArena.SpiralGladiator actualWinner = null;
        for (SpiralAbyssArena.SpiralGladiator gladiator : arenaCombatants) {
            if (!gladiator.isDead) {
                actualWinner = gladiator;
                break;
            }
        }
        
        String winnerName = actualWinner != null ? actualWinner.fullName : "Unknown";
        
        if (championWon) {
            // Winner gets base reward plus performance bonus
            float totalMultiplier = 1.0f;
            // Calculate the total multiplier - should use the final multiplier in the list
            if (!arenaBets.isEmpty()) {
                totalMultiplier = arenaBets.get(arenaBets.size()-1).multiplier; // Use the current active multiplier
            }
            int winReward = (int)(totalBet * totalMultiplier * CasinoConfig.ARENA_SURVIVAL_REWARD_MULT); 
            int totalReward = winReward + performanceBonus;
            CasinoVIPManager.addStargems(totalReward);
            
            main.textPanel.setFontInsignia();
            
            // Combine victory text with colored champion name into a single paragraph
            Color prefixColor = isPositiveAffix(chosenChampion.prefix) ? Color.GREEN : Color.RED;
            Color affixColor = isPositiveAffix(chosenChampion.affix) ? Color.GREEN : Color.RED;
            
            String victoryText = "VICTORY! " + chosenChampion.prefix + " " + 
                                chosenChampion.hullName + " " + chosenChampion.affix + " is the lone survivor.";
            main.textPanel.addPara(victoryText, prefixColor);
            
            // Highlight the hull name in white
            main.textPanel.highlightInLastPara(Color.WHITE, chosenChampion.hullName + " ");
            
            // Highlight the affix with appropriate color
            main.textPanel.highlightInLastPara(affixColor, chosenChampion.affix);
            
            main.getTextPanel().addPara("Base Win Reward: " + winReward + " Stargems", Color.GREEN);
        } else {
            // Loser still gets performance rewards
            CasinoVIPManager.addStargems(performanceBonus);
            main.getTextPanel().addPara("DEFEAT. Your champion has been decommissioned.", Color.RED);
            
            if (performanceBonus > 0) {
                main.getTextPanel().addPara("However, you earned " + performanceBonus + " Stargems for performance:", Color.YELLOW);
            } else {
                main.getTextPanel().addPara("Unfortunately, no performance rewards were earned.", Color.GRAY);
            }
        }
        
        // Display performance statistics
        main.getTextPanel().addPara("Performance Summary:", Color.YELLOW);
        main.getTextPanel().addPara("  - Original Bet: " + totalBet + " Stargems", Color.WHITE);
        main.getTextPanel().addPara("  - Turns Survived: " + turnsSurvived + " (" + survivalReward + " Stargems)", Color.WHITE);
        main.getTextPanel().addPara("  - Kills Made: " + chosenChampion.kills + " (" + killReward + " Stargems)", Color.WHITE);
        main.getTextPanel().addPara("  - Total Performance Bonus: " + performanceBonus + " Stargems", Color.CYAN);
        if (championWon) {
            float finalMultiplier = 1.0f;
            if (!arenaBets.isEmpty()) {
                finalMultiplier = arenaBets.get(arenaBets.size()-1).multiplier; // Use the final multiplier
            }
            int totalWinReward = (int)(totalBet * finalMultiplier * CasinoConfig.ARENA_SURVIVAL_REWARD_MULT);
            main.getTextPanel().addPara("Total Reward: " + (performanceBonus + totalWinReward) + " Stargems", Color.GREEN);
            main.getTextPanel().addPara("Net Profit: " + (performanceBonus + totalWinReward - totalBet) + " Stargems", Color.GREEN);
        } else {
            main.getTextPanel().addPara("Net Result: " + (performanceBonus - totalBet) + " Stargems", Color.ORANGE);
        }
        
        // Show the winner announcement panel
        float finalMultiplier = arenaBets.isEmpty() ? 1.0f : arenaBets.get(arenaBets.size()-1).multiplier;
        int netResult = championWon ? (performanceBonus + (int)(totalBet * finalMultiplier * CasinoConfig.ARENA_SURVIVAL_REWARD_MULT) - totalBet) : (performanceBonus - totalBet);
        int totalReward = championWon ? (performanceBonus + (int)(totalBet * finalMultiplier * CasinoConfig.ARENA_SURVIVAL_REWARD_MULT)) : performanceBonus;
        
        // Create the winner announcement panel
        data.scripts.CasinoUIPanels.ArenaWinnerAnnouncementPanel winnerPanel = 
            new data.scripts.CasinoUIPanels.ArenaWinnerAnnouncementPanel(
                championWon,
                winnerName,
                turnsSurvived,
                chosenChampion.kills,
                totalReward,
                netResult
            );
        
        // Show the custom panel using LunaLib
        try {
            main.dialog.getVisualPanel().showCustomPanel(600, 800, winnerPanel);
        } catch (Exception e) {
            // If LunaLib is not available or has issues, continue without the popup
            System.out.println("Could not show winner announcement popup: " + e.getMessage());
        }
        
        // Reset the arena state to generate new ships for the next battle
        activeArena = null;
        arenaCombatants = null;
        chosenChampion = null;
        opponentsDefeated = 0;
        turnsSurvived = 0;
        arenaBets.clear();
        currentBetAmount = CasinoConfig.ARENA_ENTRY_FEE; // Reset to default bet amount for next game
        
        // According to the requirements, after battle, we should show a menu that allows adding bet
        // until "Continue Battle" (which would be "arena_lobby" in this case) is chosen
        main.getOptions().addOption("Return to Lobby", "arena_lobby");
        main.getOptions().addOption("Back to Main Menu", "back_menu");
    }
    
    /**
     * Shows menu for switching to a different champion
     */
    private void showArenaSwitchMenu() {
        main.getOptions().clearOptions();
        main.getTextPanel().addPara("Select a new champion to switch to:", Color.YELLOW);
        main.getTextPanel().addPara("Current Bet: " + currentBetAmount + " Stargems", Color.CYAN);
        
        for (int i = 0; i < arenaCombatants.size(); i++) {
            SpiralAbyssArena.SpiralGladiator g = arenaCombatants.get(i);
            if (!g.isDead && g != chosenChampion) {
                // Apply the switching penalty: 50% fee and halved multiplier
                float penaltyFee = arenaBets.get(arenaBets.size()-1).amount * 0.5f;
                float newMultiplier = arenaBets.get(arenaBets.size()-1).multiplier * 0.5f;
                
                main.textPanel.setFontInsignia();
                
                // Combine all ship name parts into a single paragraph to avoid excessive line breaks
                Color prefixColor = isPositiveAffix(g.prefix) ? Color.GREEN : Color.RED;
                Color affixColor = isPositiveAffix(g.affix) ? Color.GREEN : Color.RED;
                
                String shipInfo = (i+1) + ". " + g.prefix + " " + g.hullName + " " + g.affix + " (Fee: " + (int)penaltyFee + " Gems, Multiplier: x" + newMultiplier + ")\n";
                main.textPanel.addPara(shipInfo, prefixColor);
                
                // Highlight the hull name in white
                main.textPanel.highlightInLastPara(Color.WHITE, g.hullName + " ");
                
                // Highlight the affix with appropriate color
                main.textPanel.highlightInLastPara(affixColor, g.affix);
                
                // Use the full name for the option ID but display text can be customized
                main.getOptions().addOption("Switch to " + g.prefix + " " + g.hullName + " " + g.affix, "arena_confirm_switch_" + i);
            }
        }
        main.getOptions().addOption("Cancel", "arena_lobby");
    }
    
    /**
     * Performs the champion switch with penalties
     */
    private void performChampionSwitch(int newChampionIndex) {
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
        
        // Add a new bet with the reduced multiplier
        arenaBets.add(new BetInfo(penaltyFee, newMultiplier));
        
        main.getTextPanel().addParagraph("Switched to " + chosenChampion.fullName + ". Applied switching penalties (50% fee).", Color.YELLOW);
        showArenaStatus();
    }
    
    /**
     * Processes battle log entry with appropriate formatting
     */
    private void processLogEntry(String logEntry) {
        // Parse the log entry to identify attacker, target, and damage values
        // Use regex to find patterns like "$attacker hits $target for $dmg!"
        
        // First, restore font to default
        main.textPanel.setFontInsignia();
        
        if (logEntry.contains("$attacker") || logEntry.contains("$target") || logEntry.contains("$dmg")) {
            // This shouldn't happen since the replacement should already occur in the simulation
            main.textPanel.addPara(logEntry);
            return;
        }
        
        // Look for damage patterns: "shipName hits shipName for X HP!"
        // We'll use a simple approach looking for "hits" and "for" keywords
        String[] parts = logEntry.split("hits | for |!");
        if (parts.length >= 3 && logEntry.contains("hits") && logEntry.contains("for")) {
            // Format: "Attacker hits Target for Damage!"
            String attacker = parts[0].trim();
            String target = parts[1].trim();
            String damagePart = parts[2].trim() + (logEntry.endsWith("!") ? "!" : "");
            
            // Combine the text into a single paragraph to avoid excessive line breaks
            String combinedText = attacker + " hits " + target + " for " + damagePart;
            main.textPanel.addPara(combinedText, Color.WHITE);
        } else if (logEntry.contains("suffered a Hull Breach") || logEntry.contains("was lost to space decompression")) {
            // Handle hull breach events: "shipName suffered a Hull Breach! (-X HP)"
            String[] parts2 = logEntry.split(" suffered a| was lost");
            if (parts2.length >= 1) {
                String shipName = parts2[0].trim();
                String eventDesc = logEntry.substring(shipName.length()).trim();
                
                // Combine into a single paragraph to avoid excessive line breaks
                String combinedText = shipName + eventDesc;
                main.textPanel.addPara(combinedText, Color.WHITE);
            } else {
                main.textPanel.addPara(logEntry);
            }
        } else if (logEntry.contains("💀") || logEntry.contains("💥") || logEntry.contains("⚠️")) {
            // Handle special events with emojis
            if (logEntry.contains(":")) {
                String[] parts3 = logEntry.split(":", 2);
                if (parts3.length >= 2) {
                    // Combine into a single paragraph to avoid excessive line breaks
                    String combinedText = parts3[0] + ": " + parts3[1];
                    main.textPanel.addPara(combinedText, Color.WHITE);
                } else {
                    main.textPanel.addPara(logEntry);
                }
            } else {
                main.textPanel.addPara(logEntry);
            }
        } else {
            // For other log entries, try to identify ship names
            boolean foundFormatted = false;
            for (SpiralAbyssArena.SpiralGladiator gladiator : arenaCombatants) {
                if (logEntry.contains(gladiator.shortName)) {
                    // Use the original log entry but with consistent coloring to avoid excessive line breaks
                    main.textPanel.addPara(logEntry, Color.WHITE);
                    foundFormatted = true;
                    break;
                }
            }
            if (!foundFormatted) {
                main.textPanel.addPara(logEntry);
            }
        }
    }
    
    /**
     * Shows menu for increasing bet on current champion during battle
     */
    private void showIncreaseBetMenu() {
        main.getOptions().clearOptions();
        main.getTextPanel().addPara("Increase your bet on current champion:", Color.YELLOW);
        main.getTextPanel().addPara("Current Champion: " + chosenChampion.fullName, Color.CYAN);
        main.getTextPanel().addPara("Current Total Bet: " + getCurrentTotalBet() + " Stargems", Color.CYAN);
        main.getTextPanel().addPara("Balance: " + CasinoVIPManager.getStargems() + " Stargems", Color.CYAN);
        
        int playerBalance = CasinoVIPManager.getStargems();
        
        // Create fixed increment options based on project specifications (same as in showAddBetMenu)
        if (playerBalance >= 100) {
            main.getOptions().addOption("Add 100 Stargems", "arena_confirm_increase_bet_100");
        }
        if (playerBalance >= 500) {
            main.getOptions().addOption("Add 500 Stargems", "arena_confirm_increase_bet_500");
        }
        if (playerBalance >= 2000) {
            main.getOptions().addOption("Add 2000 Stargems", "arena_confirm_increase_bet_2000");
        }
        
        // Add percentage-based options
        int tenPercent = (playerBalance * 10) / 100;
        if (tenPercent > 0 && playerBalance >= tenPercent) {
            main.getOptions().addOption("Add " + tenPercent + " Stargems (10% of account)", "arena_confirm_increase_bet_" + tenPercent);
        }
        
        int fiftyPercent = (playerBalance * 50) / 100;
        if (fiftyPercent > 0 && playerBalance >= fiftyPercent) {
            main.getOptions().addOption("Add " + fiftyPercent + " Stargems (50% of account)", "arena_confirm_increase_bet_" + fiftyPercent);
        }
        
        main.getOptions().addOption("Continue Battle Without Increasing Bet", "arena_status");
        main.getOptions().addOption("Cancel & Return to Battle Options", "arena_status");
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
    
    /**
     * Performs the bet increase on the current champion
     */
    private void performBetIncrease(int additionalAmount) {
        // Check if player has enough gems to increase the bet
        if (CasinoVIPManager.getStargems() < additionalAmount) {
            main.getTextPanel().addPara("Not enough Stargems to increase bet! You need " + additionalAmount + " but only have " + CasinoVIPManager.getStargems() + ".", Color.RED);
            showArenaStatus(); // Return to the arena status menu
            return;
        }
        
        // Deduct the additional bet amount from player's gems
        CasinoVIPManager.addStargems(-additionalAmount);
        
        // Get the current multiplier
        float currentMultiplier = 1.0f;
        if (!arenaBets.isEmpty()) {
            currentMultiplier = arenaBets.get(arenaBets.size()-1).multiplier;
        }
        
        // Add a new bet with the same multiplier
        arenaBets.add(new BetInfo(additionalAmount, currentMultiplier));
        
        main.getTextPanel().addPara("Increased bet by " + additionalAmount + " Stargems on " + chosenChampion.fullName + ".", Color.YELLOW);
        showArenaStatus();
    }
    
    /**
     * Shows custom bet increase menu
     */
    private void showCustomIncreaseBetMenu() {
        main.options.clearOptions();
        main.textPanel.addPara("\nEnter Custom Bet Increase Amount:", Color.YELLOW);
        int playerBalance = CasinoVIPManager.getStargems();
        main.textPanel.addPara("Balance: " + playerBalance + " Stargems", Color.CYAN);
        int currentBet = getCurrentTotalBet();
        main.textPanel.addPara("Current Total Bet: " + currentBet + " Stargems", Color.CYAN);
        
        // Add some suggested amounts based on current bet (similar to initial bet menu)
        int[] suggestedAmounts = {currentBet/4, currentBet/2, currentBet, currentBet*2, currentBet*3, currentBet*5};
        
        for (int amount : suggestedAmounts) {
            if (amount > 0 && amount <= playerBalance) {
                main.options.addOption("Add " + amount + " Stargems", "arena_confirm_increase_bet_" + amount);
            }
        }
        
        // Add percentage-based options for larger increases
        int[] percentages = {10, 30, 50, 70, 100}; // 10%, 30%, 50%, 70%, 100%
        for (int percent : percentages) {
            int amount = (playerBalance * percent) / 100;
            if (amount > 0 && amount <= playerBalance) {
                main.options.addOption(percent + "% of Balance (" + amount + " Gems)", "arena_confirm_increase_bet_" + amount);
            }
        }
        
        main.options.addOption("Back to Increase Bet Menu", "arena_increase_bet");
    }
}