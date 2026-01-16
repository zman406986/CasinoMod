package data.scripts.casino.interaction;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FleetMemberPickerListener;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import data.scripts.casino.CasinoConfig;
import data.scripts.casino.CasinoGachaManager;
import data.scripts.casino.CasinoVIPManager;
import data.scripts.CasinoUIPanels;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Manages the gacha interaction flow including pulls, pity system, and ship conversion
 */
public class GachaHandler {

    private final CasinoInteraction main;

    private final Map<String, OptionHandler> handlers = new HashMap<>();
    private final Map<Predicate<String>, OptionHandler> predicateHandlers = new HashMap<>();
    
    public GachaHandler(CasinoInteraction main) {
        this.main = main;
        initializeHandlers();
    }
    
    private void initializeHandlers() {
        // Exact match handlers
        handlers.put("gacha_menu", option -> showGachaMenu());
        handlers.put("pull_1", option -> showGachaConfirm(1));
        handlers.put("pull_10", option -> showGachaConfirm(10));
        handlers.put("auto_convert", option -> openAutoConvertPicker());
        handlers.put("how_to_gacha", option -> main.help.showGachaHelp());
        handlers.put("back_menu", option -> main.showMenu());
        
        // Predicate-based handlers for pattern matching
        predicateHandlers.put(option -> option.startsWith("confirm_pull_"), option -> {
            int rounds = Integer.parseInt(option.replace("confirm_pull_", ""));
            performGachaPull(rounds);
        });
    }

    /**
     * Routes different gacha-related options to appropriate methods
     */
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

    /**
     * Displays the main gacha menu with options for pulls and auto-conversion settings
     */
    public void showGachaMenu() {
        main.options.clearOptions();
        main.dialog.getVisualPanel().showCustomPanel(400, 500, new CasinoUIPanels.GachaUIPanel());
        
        main.textPanel.addPara("Tachy-Impact Warp Beacon Protocol", Color.CYAN);
        
        CasinoGachaManager manager = new CasinoGachaManager();
        manager.checkRotation();
        CasinoGachaManager.GachaData data = manager.getData();
        
        if (data.featuredCapital != null) {
            ShipHullSpecAPI capSpec = Global.getSettings().getHullSpec(data.featuredCapital);
            String capName = capSpec != null ? capSpec.getHullName() : data.featuredCapital;
            main.textPanel.addPara("FEATURED 5*: " + capName, Color.ORANGE);
        }
        
        main.textPanel.addPara("Pity Status:", Color.GRAY);
        main.textPanel.addPara("- 5* Pity: " + data.pity5 + "/" + CasinoConfig.PITY_HARD_5);
        main.textPanel.addPara("- 4* Pity: " + data.pity4 + "/" + CasinoConfig.PITY_HARD_4);
        
        // Only show 1x and 10x pull options as requested
        int gems = CasinoVIPManager.getStargems();
        if (gems >= CasinoConfig.GACHA_COST) {
            main.options.addOption("Pull 1x (" + CasinoConfig.GACHA_COST + " Gems)", "pull_1");
        }
        if (gems >= (CasinoConfig.GACHA_COST * 10)) {
            main.options.addOption("Pull 10x (" + (CasinoConfig.GACHA_COST * 10) + " Gems)", "pull_10");
        }
        
        main.options.addOption("View Ship Pool and Select Auto-Convert", "auto_convert");
        main.options.addOption("Gacha Handbook", "how_to_gacha");
        main.options.addOption("Back", "back_menu");
        main.setState(CasinoInteraction.State.GACHA);
    }
    
    /**
     * Shows confirmation dialog for gacha pull
     */
    private void showGachaConfirm(int times) {
        main.options.clearOptions();
        int cost = times * CasinoConfig.GACHA_COST;
        main.textPanel.addPara("Confirm initiating Warp Sequence " + times + "x for " + cost + " Stargems?", Color.YELLOW);
        
        main.options.addOption("Confirm Warp", "confirm_pull_" + times);
        main.options.addOption("Cancel", "gacha_menu");
    }
    
    /**
     * Performs gacha pull(s) and shows ship conversion picker for obtained ships
     */
    private void performGachaPull(int times) {
        int cost = times * CasinoConfig.GACHA_COST;
        int currentGems = CasinoVIPManager.getStargems();
        
        if (currentGems < cost) {
            main.textPanel.addPara("Insufficient Stargems!", Color.RED);
            showGachaMenu();
            return;
        }
        
        CasinoVIPManager.addStargems(-cost);
        CasinoGachaManager manager = new CasinoGachaManager();
        main.textPanel.addPara("Initiating Warp Sequence...", Color.CYAN);
        
        // Collect ships that were obtained from the pulls
        List<FleetMemberAPI> obtainedShips = new ArrayList<>();
        
        // Perform pulls and collect results for animation
        List<String> pullResults = new ArrayList<>();
        for (int i=0; i<times; i++) {
            String result = manager.performPullDetailed(obtainedShips); // Modified method to collect ships
            pullResults.add(result);
        }
        
        // Process results directly without animation
        for (String result : pullResults) {
            main.textPanel.addPara(" > " + result);
        }
        
        // If no ships were obtained, just show the normal options
        if (obtainedShips.isEmpty()) {
            main.options.clearOptions(); // Clear any previous options to prevent UI confusion
            main.options.addOption("Pull Again", "gacha_menu");
            main.options.addOption("Back to Main Menu", "back_menu");
            return;
        }
        
        // Show ship picker for ships they don't want to convert
        showConvertSelectionPicker(obtainedShips);
    }
    
    /**
     * Shows fleet member picker to select ships for conversion
     */
    private void showConvertSelectionPicker(List<FleetMemberAPI> obtainedShips) {
        // Get the auto-convert settings to handle ships that are already flagged for auto-convert
        CasinoGachaManager manager = new CasinoGachaManager();
        CasinoGachaManager.GachaData data = manager.getData();
        
        // Add information about which ships are already in auto-convert
        if (!data.autoConvertIds.isEmpty()) {
            main.textPanel.addPara("Note: Ships already in Auto-Convert list will be automatically converted regardless of selection.", Color.YELLOW);
        }
        
        // Show the fleet member picker with obtained ships
        main.getDialog().showFleetMemberPickerDialog(
            "Select ships you want to convert to Stargems:", 
            "Confirm Selection", 
            "Cancel", 
            10, 
            7, 
            88, 
            true, 
            true, 
            obtainedShips, 
            new FleetMemberPickerListener() {
                public void pickedFleetMembers(List<FleetMemberAPI> selectedMembers) {
                    // Convert ships that were NOT selected, excluding those already in auto-convert list
                    List<String> selectedHullIds = new ArrayList<>();
                    if (selectedMembers != null) {
                        for (FleetMemberAPI member : selectedMembers) {
                            if (member != null && member.getHullId() != null) {
                                selectedHullIds.add(member.getHullId());
                            }
                        }
                    }
                    
                    // Process conversions for ships
                    for (FleetMemberAPI ship : obtainedShips) {
                        if (ship == null) continue; // Skip null ships
                        
                        // Check if ship is in auto-convert list (highest priority)
                        if (data.autoConvertIds.contains(ship.getHullId())) {
                            // Convert the ship to stargems (regardless of selection)
                            int val = (int)(ship.getHullSpec().getBaseValue() / CasinoConfig.SHIP_TRADE_RATE);
                            CasinoVIPManager.addStargems(val);
                            main.textPanel.addPara("Auto-converted " + (ship.getShipName() != null ? ship.getShipName() : "Unknown Ship") + " to " + val + " Stargems.", Color.GREEN);
                        } 
                        // Check if ship was selected to be converted (picked by user)
                        else if (selectedHullIds.contains(ship.getHullId())) {
                            // Convert the ship to stargems
                            int val = (int)(ship.getHullSpec().getBaseValue() / CasinoConfig.SHIP_TRADE_RATE);
                            CasinoVIPManager.addStargems(val);
                            main.textPanel.addPara("Converted " + (ship.getShipName() != null ? ship.getShipName() : "Unknown Ship") + " to " + val + " Stargems.", Color.GREEN);
                        } 
                        // Otherwise, the ship was not selected, so keep it
                        else {
                            // Keep the ship in the fleet
                            Global.getSector().getPlayerFleet().getFleetData().addFleetMember(ship);
                        }
                    }
                    
                    // Show final options
                    showPostPullOptions();
                }
                
                public void cancelledFleetMemberPicking() {
                    // If cancelled, add all ships to fleet without conversion
                    for (FleetMemberAPI ship : obtainedShips) {
                        if (ship != null) {
                            Global.getSector().getPlayerFleet().getFleetData().addFleetMember(ship);
                        }
                    }
                    showPostPullOptions();
                }
            });
    }
    
    /**
     * Shows options after pull is complete
     */
    private void showPostPullOptions() {
        main.options.clearOptions();
        main.options.addOption("Pull Again", "gacha_menu");
        main.options.addOption("Back to Main Menu", "back_menu");
    }
    
    /**
     * Opens fleet member picker to select ships for auto-conversion
     */
    private void openAutoConvertPicker() {
        CasinoGachaManager manager = new CasinoGachaManager();
        List<FleetMemberAPI> potentialDrops = manager.getPotentialDrops();
        
        main.getDialog().showFleetMemberPickerDialog("Select ships to AUTO-CONVERT to Stargems:", "Save Settings", "Cancel", 10, 7, 88, true, true, potentialDrops, 
            new FleetMemberPickerListener() {
                public void pickedFleetMembers(List<FleetMemberAPI> members) {
                     CasinoGachaManager manager = new CasinoGachaManager();
                     CasinoGachaManager.GachaData data = manager.getData();
                     data.autoConvertIds.clear();
                     if (members != null) {
                         for (FleetMemberAPI m : members) {
                             if (m != null && m.getHullId() != null) {
                                 data.autoConvertIds.add(m.getHullId());
                             }
                         }
                     }
                     showGachaMenu();
                }
                public void cancelledFleetMemberPicking() { showGachaMenu(); }
            });
    }
}