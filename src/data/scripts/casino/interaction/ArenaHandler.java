package data.scripts.casino.interaction;

import com.fs.starfarer.api.Global;
import data.scripts.casino.CasinoConfig;
import data.scripts.casino.CasinoGachaManager;
import data.scripts.casino.CasinoVIPManager;
import data.scripts.casino.SpiralAbyssArena;
import data.scripts.CasinoUIPanels.ArenaUIPanel;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

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

    public ArenaHandler(CasinoInteraction main) {
        this.main = main;
    }

    /**
     * Handles arena menu options including lobby, betting, and battle simulation
     */
    public void handle(String option) {
        if ("arena_lobby".equals(option)) {
            showArenaLobby();
        } else if ("arena_select_bet_menu".equals(option)) {
            showBetAmountMenu();
        } else if (option.startsWith("arena_select_ship_")) {
            int idx = Integer.parseInt(option.replace("arena_select_ship_", ""));
            showArenaConfirm(idx);
        } else if (option.startsWith("arena_bet_") && !option.contains("_bet")) {
            int idx = Integer.parseInt(option.replace("arena_bet_", ""));
            showArenaConfirm(idx);
        } else if (option.startsWith("confirm_arena_bet_")) {
            int idx = Integer.parseInt(option.replace("confirm_arena_bet_", ""));
            startArenaBattle(idx);
        } else if ("arena_watch_next".equals(option)) {
            simulateArenaStep();
        } else if ("arena_skip".equals(option)) {
            while (simulateArenaStep()) {}
        } else if ("arena_switch".equals(option)) {
            // Handle switching champion
            showArenaSwitchMenu();
        } else if ("arena_increase_bet".equals(option)) {
            // Handle increasing bet on current champion
            showIncreaseBetMenu();
        } else if (option.startsWith("arena_confirm_increase_bet_")) {
            int amount = Integer.parseInt(option.replace("arena_confirm_increase_bet_", ""));
            performBetIncrease(amount);
        } else if ("arena_custom_increase_bet".equals(option)) {
            showCustomIncreaseBetMenu();
        } else if ("arena_status".equals(option)) {
            showArenaStatus();
        } else if (option.startsWith("arena_confirm_switch_")) {
            int idx = Integer.parseInt(option.replace("arena_confirm_switch_", ""));
            performChampionSwitch(idx);
        } else if ("arena_small_bet".equals(option)) {
            currentBetAmount = CasinoConfig.ARENA_ENTRY_FEE / 2;
            showArenaLobby();
        } else if ("arena_standard_bet".equals(option)) {
            currentBetAmount = CasinoConfig.ARENA_ENTRY_FEE;
            showArenaLobby();
        } else if ("arena_large_bet".equals(option)) {
            currentBetAmount = CasinoConfig.ARENA_ENTRY_FEE * 2;
            showArenaLobby();
        } else if ("arena_custom_bet".equals(option)) {
            showCustomBetMenu();
        } else if (option.startsWith("arena_select_bet_")) {
            int betAmount = Integer.parseInt(option.replace("arena_select_bet_", ""));
            currentBetAmount = betAmount;
            showArenaLobby();
        } else if ("how_to_arena".equals(option)) {
            main.help.showArenaHelp();
        } else if ("back_menu".equals(option)) {
            main.showMenu();
        }
        // ... Dispatch other arena options ...
    }

    /**
     * Displays the arena lobby with ship selection and betting options
     */
    public void showArenaLobby() {
        main.options.clearOptions();
        int fee = CasinoConfig.ARENA_ENTRY_FEE;
        
        // Only generate new ships if arena hasn't been initialized yet
        if (activeArena == null || arenaCombatants == null || arenaCombatants.isEmpty()) {
            activeArena = new SpiralAbyssArena();
            arenaCombatants = activeArena.generateCombatants(new CasinoGachaManager());
        }
        
        main.textPanel.addParagraph("Spiral Abyss Arena - Today's Match Card", Color.CYAN);
        main.textPanel.addParagraph("Current Bet Amount: " + currentBetAmount + " Stargems", Color.YELLOW);
        main.textPanel.addParagraph("\nSelect a champion to bet on:", Color.YELLOW);
        
        for (int i=0; i<arenaCombatants.size(); i++) {
            SpiralAbyssArena.SpiralGladiator g = arenaCombatants.get(i);
            // Create properly formatted string with colored parts
            String coloredName = formatColoredShipName(g);
            main.textPanel.addParagraph((i+1) + ". " + coloredName + " (" + g.hullName + ")");
            main.options.addOption((i+1) + ". " + g.fullName, "arena_select_ship_" + i);
        }
        
        main.options.addOption("Change Bet Amount", "arena_select_bet_menu");
        main.options.addOption("How it Works", "how_to_arena");
        main.options.addOption("Back", "back_menu");
        main.setState(CasinoInteraction.State.ARENA);
    }

    /**
     * Shows bet amount selection menu
     */
    private void showBetAmountMenu() {
        main.options.clearOptions();
        int fee = CasinoConfig.ARENA_ENTRY_FEE;
        main.textPanel.addParagraph("\nSelect Bet Amount:", Color.YELLOW);
        main.textPanel.addParagraph("Current Bet Amount: " + currentBetAmount + " Stargems", Color.CYAN);
        main.textPanel.addParagraph("Balance: " + CasinoVIPManager.getStargems() + " Stargems", Color.CYAN);
        
        // Add betting amount selection
        main.options.addOption("Small Bet (" + (fee/2) + " Gems)", "arena_small_bet");
        main.options.addOption("Standard Bet (" + fee + " Gems)", "arena_standard_bet");
        main.options.addOption("Large Bet (" + (fee*2) + " Gems)", "arena_large_bet");
        main.options.addOption("Custom Bet...", "arena_custom_bet");
        main.options.addOption("Back to Lobby", "arena_lobby");
    }

    /**
     * Shows custom bet amount selection menu
     */
    private void showCustomBetMenu() {
        main.options.clearOptions();
        main.textPanel.addParagraph("\nSelect Custom Bet Amount:", Color.YELLOW);
        int playerBalance = CasinoVIPManager.getStargems();
        main.textPanel.addParagraph("Balance: " + playerBalance + " Stargems", Color.CYAN);
        
        // Offer a range of bet amounts based on player's balance
        int baseAmount = CasinoConfig.ARENA_ENTRY_FEE;
        int[] betOptions = {baseAmount/4, baseAmount/2, baseAmount, baseAmount*2, baseAmount*3, baseAmount*5};
        
        for (int amount : betOptions) {
            if (amount <= playerBalance) {
                main.options.addOption(amount + " Gems", "arena_select_bet_" + amount);
            }
        }
        
        // Add percentage-based options for larger bets
        int[] percentages = {10, 30, 50, 70, 100}; // 10%, 30%, 50%, 70%, 100%
        for (int percent : percentages) {
            int amount = (playerBalance * percent) / 100;
            if (amount > 0 && amount <= playerBalance) {
                main.options.addOption(percent + "% of Balance (" + amount + " Gems)", "arena_select_bet_" + amount);
            }
        }
        
        main.options.addOption("Back to Bet Selection", "arena_select_bet_menu");
    }

    /**
     * Shows confirmation dialog for arena battle
     */
    private void showArenaConfirm(int idx) {
        main.options.clearOptions();
        SpiralAbyssArena.SpiralGladiator g = arenaCombatants.get(idx);
        
        main.textPanel.addParagraph("Bet " + currentBetAmount + " Gems on ", Color.YELLOW);
        main.textPanel.setFontInsignia();
        main.textPanel.addParagraph(g.prefix + " ", Color.GREEN);
        main.textPanel.addParagraph(g.hullName + " ", Color.WHITE);
        main.textPanel.addParagraph(g.affix, Color.BLUE);
        main.textPanel.addParagraph("?", Color.YELLOW);
        main.textPanel.addParagraph("Balance after bet: " + (CasinoVIPManager.getStargems() - currentBetAmount) + " Stargems", Color.CYAN);
        
        main.options.addOption("Confirm", "confirm_arena_bet_" + idx);
        main.options.addOption("Cancel", "arena_lobby");
    }

    /**
     * Starts the arena battle with selected champion
     */
    private void startArenaBattle(int chosenIdx) {
        // Deduct the current bet amount from player's gems
        CasinoVIPManager.addStargems(-currentBetAmount);
        chosenChampion = arenaCombatants.get(chosenIdx);
        arenaBets.clear();
        arenaBets.add(new BetInfo(currentBetAmount, 1.0f)); // Use the current bet amount instead of fixed fee
        opponentsDefeated = 0;
        turnsSurvived = 0;
        
        // Announce the battle with the chosen champion
        main.textPanel.addParagraph("The battle begins! Your champion ", Color.RED);
        main.textPanel.setFontInsignia();
        main.textPanel.addParagraph(chosenChampion.prefix + " ", Color.GREEN);
        main.textPanel.addParagraph(chosenChampion.hullName + " ", Color.WHITE);
        main.textPanel.addParagraph(chosenChampion.affix, Color.BLUE);
        main.textPanel.addParagraph(" enters the arena! (Bet: " + currentBetAmount + " Stargems)", Color.RED);
        
        main.dialog.getVisualPanel().showCustomPanel(400, 600, new ArenaUIPanel(main));
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
            // Process the log entry to highlight ship names with colors
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
        int baseReward = currentBetAmount; // Use the current bet amount instead of fixed fee
        int survivalReward = turnsSurvived * CasinoConfig.ARENA_SURVIVAL_REWARD_PER_TURN; // Configurable gems per turn survived
        int killReward = chosenChampion.kills * CasinoConfig.ARENA_KILL_REWARD_PER_KILL; // Configurable gems per kill
        int performanceBonus = survivalReward + killReward;
        
        if (championWon) {
            // Winner gets base reward plus performance bonus
            float totalMultiplier = 1.0f;
            // Calculate the total multiplier - should use the final multiplier in the list
            if (!arenaBets.isEmpty()) {
                totalMultiplier = arenaBets.get(arenaBets.size()-1).multiplier; // Use the current active multiplier
            }
            int winReward = (int)(baseReward * totalMultiplier * CasinoConfig.ARENA_SURVIVAL_REWARD_MULT); 
            int totalReward = winReward + performanceBonus;
            CasinoVIPManager.addStargems(totalReward);
            
            main.textPanel.addParagraph("VICTORY! ", Color.CYAN);
            main.textPanel.setFontInsignia();
            // Color the winning ship name
            String prefix = chosenChampion.prefix;
            String hullName = chosenChampion.hullName;
            String affix = chosenChampion.affix;
            main.textPanel.addParagraph(prefix + " ", Color.GREEN);
            main.textPanel.addParagraph(hullName + " ", Color.WHITE);
            main.textPanel.addParagraph(affix, Color.BLUE);
            main.textPanel.addParagraph(" is the lone survivor.", Color.CYAN);
            
            main.getTextPanel().addParagraph("Base Win Reward: " + winReward + " Stargems", Color.GREEN);
        } else {
            // Loser still gets performance rewards
            CasinoVIPManager.addStargems(performanceBonus);
            main.getTextPanel().addParagraph("DEFEAT. Your champion has been decommissioned.", Color.RED);
            
            if (performanceBonus > 0) {
                main.getTextPanel().addParagraph("However, you earned " + performanceBonus + " Stargems for performance:", Color.YELLOW);
            } else {
                main.getTextPanel().addParagraph("Unfortunately, no performance rewards were earned.", Color.GRAY);
            }
        }
        
        // Display performance statistics
        main.getTextPanel().addParagraph("Performance Summary:", Color.YELLOW);
        main.getTextPanel().addParagraph("  - Original Bet: " + currentBetAmount + " Stargems", Color.WHITE);
        main.getTextPanel().addParagraph("  - Turns Survived: " + turnsSurvived + " (" + survivalReward + " Stargems)", Color.WHITE);
        main.getTextPanel().addParagraph("  - Kills Made: " + chosenChampion.kills + " (" + killReward + " Stargems)", Color.WHITE);
        main.getTextPanel().addParagraph("  - Total Performance Bonus: " + performanceBonus + " Stargems", Color.CYAN);
        if (championWon) {
            float finalMultiplier = 1.0f;
            if (!arenaBets.isEmpty()) {
                finalMultiplier = arenaBets.get(arenaBets.size()-1).multiplier; // Use the final multiplier
            }
            int totalWinReward = (int)(currentBetAmount * finalMultiplier * CasinoConfig.ARENA_SURVIVAL_REWARD_MULT);
            main.getTextPanel().addParagraph("Total Reward: " + (performanceBonus + totalWinReward) + " Stargems", Color.GREEN);
            main.getTextPanel().addParagraph("Net Profit: " + (performanceBonus + totalWinReward - currentBetAmount) + " Stargems", Color.GREEN);
        } else {
            main.getTextPanel().addParagraph("Net Result: " + (performanceBonus - currentBetAmount) + " Stargems", Color.ORANGE);
        }
        
        main.getOptions().addOption("Return to Lobby", "arena_lobby");
        main.getOptions().addOption("Back to Main Menu", "back_menu");
    }
    
    /**
     * Shows menu for switching to a different champion
     */
    private void showArenaSwitchMenu() {
        main.getOptions().clearOptions();
        main.getTextPanel().addParagraph("Select a new champion to switch to:", Color.YELLOW);
        main.getTextPanel().addParagraph("Current Bet: " + currentBetAmount + " Stargems", Color.CYAN);
        
        for (int i = 0; i < arenaCombatants.size(); i++) {
            SpiralAbyssArena.SpiralGladiator g = arenaCombatants.get(i);
            if (!g.isDead && g != chosenChampion) {
                // Apply the switching penalty: 50% fee and halved multiplier
                float penaltyFee = arenaBets.get(arenaBets.size()-1).amount * 0.5f;
                float newMultiplier = arenaBets.get(arenaBets.size()-1).multiplier * 0.5f;
                
                // Add colored name
                main.textPanel.addParagraph((i+1) + ". ");
                main.textPanel.setFontInsignia();
                main.textPanel.addParagraph(g.prefix + " ", Color.GREEN);
                main.textPanel.addParagraph(g.hullName + " ", Color.WHITE);
                main.textPanel.addParagraph(g.affix + " ", Color.BLUE);
                main.textPanel.addParagraph("(Fee: " + (int)penaltyFee + " Gems, Multiplier: x" + newMultiplier + ")", Color.GRAY);
                
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
     * Formats ship name with appropriate coloring
     */
    private String formatColoredShipName(SpiralAbyssArena.SpiralGladiator gladiator) {
        // For now, return the full name since Starsector text panels don't easily support inline colored text
        // We'll improve this in the future by handling the battle logs specially
        return gladiator.fullName;
    }
    
    /**
     * Processes battle log entry with appropriate formatting
     */
    private void processLogEntry(String logEntry) {
        // For now, just add the paragraph as is
        // Future enhancement: parse logEntry to find ship names and color them appropriately
        main.textPanel.addParagraph(logEntry);
    }
    
    /**
     * Shows menu for increasing bet on current champion
     */
    private void showIncreaseBetMenu() {
        main.getOptions().clearOptions();
        main.getTextPanel().addParagraph("Increase your bet on current champion:", Color.YELLOW);
        main.getTextPanel().addParagraph("Current Champion: " + chosenChampion.fullName, Color.CYAN);
        main.getTextPanel().addParagraph("Current Total Bet: " + getCurrentTotalBet() + " Stargems", Color.CYAN);
        main.getTextPanel().addParagraph("Balance: " + CasinoVIPManager.getStargems() + " Stargems", Color.CYAN);
        
        // Add options for different bet increases
        int[] increaseAmounts = {50, 100, 200, 500, 1000};
        
        for (int amount : increaseAmounts) {
            if (CasinoVIPManager.getStargems() >= amount) {
                main.getOptions().addOption("Add " + amount + " Stargems", "arena_confirm_increase_bet_" + amount);
            }
        }
        
        // Add custom bet option
        main.getOptions().addOption("Custom Amount...", "arena_custom_increase_bet");
        main.getOptions().addOption("Cancel", "arena_status");
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
        // Deduct the additional bet amount from player's gems
        CasinoVIPManager.addStargems(-additionalAmount);
        
        // Get the current multiplier
        float currentMultiplier = 1.0f;
        if (!arenaBets.isEmpty()) {
            currentMultiplier = arenaBets.get(arenaBets.size()-1).multiplier;
        }
        
        // Add a new bet with the same multiplier
        arenaBets.add(new BetInfo(additionalAmount, currentMultiplier));
        
        main.getTextPanel().addParagraph("Increased bet by " + additionalAmount + " Stargems on " + chosenChampion.fullName + ".", Color.YELLOW);
        showArenaStatus();
    }
    
    /**
     * Shows custom bet increase menu
     */
    private void showCustomIncreaseBetMenu() {
        main.options.clearOptions();
        main.textPanel.addParagraph("\nEnter Custom Bet Increase Amount:", Color.YELLOW);
        int playerBalance = CasinoVIPManager.getStargems();
        main.textPanel.addParagraph("Balance: " + playerBalance + " Stargems", Color.CYAN);
        int currentBet = getCurrentTotalBet();
        main.textPanel.addParagraph("Current Total Bet: " + currentBet + " Stargems", Color.CYAN);
        
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