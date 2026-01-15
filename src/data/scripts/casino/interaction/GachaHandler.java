package data.scripts.casino.interaction;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FleetMemberPickerListener;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import data.scripts.casino.CasinoConfig;
import data.scripts.casino.CasinoGachaManager;
import data.scripts.casino.CasinoVIPManager;
import data.scripts.CasinoUIPanels.GachaUIPanel;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages the gacha interaction flow including pulls, pity system, and ship conversion
 */
public class GachaHandler {

    private final CasinoInteraction main;

    public GachaHandler(CasinoInteraction main) {
        this.main = main;
    }

    /**
     * Routes different gacha-related options to appropriate methods
     */
    public void handle(String option) {
        if ("gacha_menu".equals(option)) {
            showGachaMenu();
        } else if ("pull_1".equals(option)) {
            showGachaConfirm(1);
        } else if ("pull_10".equals(option)) {
            showGachaConfirm(10);
        } else if (option.startsWith("confirm_pull_")) {
            int rounds = Integer.parseInt(option.replace("confirm_pull_", ""));
            performGachaPull(rounds);
        } else if ("auto_convert".equals(option)) {
            openAutoConvertPicker();
        } else if ("how_to_gacha".equals(option)) {
            main.help.showGachaHelp();
        } else if ("back_menu".equals(option)) {
            main.showMenu();
        }
    }

    /**
     * Displays the main gacha menu with options for pulls and auto-conversion settings
     */
    public void showGachaMenu() {
        main.options.clearOptions();
        main.dialog.getVisualPanel().showCustomPanel(400, 500, new GachaUIPanel());
        
        main.textPanel.addParagraph("Tachy-Impact Warp Beacon Protocol", Color.CYAN);
        
        CasinoGachaManager manager = new CasinoGachaManager();
        manager.checkRotation();
        CasinoGachaManager.GachaData data = manager.getData();
        
        if (data.featuredCapital != null) {
            ShipHullSpecAPI capSpec = Global.getSettings().getHullSpec(data.featuredCapital);
            String capName = capSpec != null ? capSpec.getHullName() : data.featuredCapital;
            main.textPanel.addParagraph("FEATURED 5*: " + capName, Color.ORANGE);
        }
        
        main.textPanel.addParagraph("Pity Status:", Color.GRAY);
        main.textPanel.addParagraph("- 5* Pity: " + data.pity5 + "/" + CasinoConfig.PITY_HARD_5);
        main.textPanel.addParagraph("- 4* Pity: " + data.pity4 + "/" + CasinoConfig.PITY_HARD_4);
        
        main.options.addOption("Pull 1x (" + CasinoConfig.GACHA_COST + " Gems)", "pull_1");
        main.options.addOption("Pull 10x (" + (CasinoConfig.GACHA_COST * 10) + " Gems)", "pull_10");
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
        main.textPanel.addParagraph("Confirm initiating Warp Sequence " + times + "x for " + cost + " Stargems?", Color.YELLOW);
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
            main.textPanel.addParagraph("Insufficient Stargems!", Color.RED);
            showGachaMenu();
            return;
        }
        
        CasinoVIPManager.addStargems(-cost);
        CasinoGachaManager manager = new CasinoGachaManager();
        main.textPanel.addParagraph("Initiating Warp Sequence...", Color.CYAN);
        
        // Collect ships that were obtained from the pulls
        List<FleetMemberAPI> obtainedShips = new ArrayList<>();
        
        for (int i=0; i<times; i++) {
            String result = manager.performPullDetailed(obtainedShips); // Modified method to collect ships
            main.textPanel.addParagraph(" > " + result);
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
            main.textPanel.addParagraph("Note: Ships already in Auto-Convert list will be automatically converted regardless of selection.", Color.YELLOW);
        }
        
        // Show the fleet member picker with obtained ships
        main.getDialog().showFleetMemberPickerDialog(
            "Select ships you do NOT want to convert to Stargems (ships NOT selected will be auto-converted):", 
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
                    
                    // Process conversions for ships that were NOT selected
                    for (FleetMemberAPI ship : obtainedShips) {
                        if (ship == null) continue; // Skip null ships
                        
                        // Skip if ship was selected to be kept (not converted) OR if it's already in auto-convert list
                        if (selectedHullIds.contains(ship.getHullId()) || data.autoConvertIds.contains(ship.getHullId())) {
                            // Keep the ship in the fleet
                            Global.getSector().getPlayerFleet().getFleetData().addFleetMember(ship);
                        } else {
                            // Convert the ship to stargems
                            int val = (int)(ship.getHullSpec().getBaseValue() / CasinoConfig.SHIP_TRADE_RATE);
                            CasinoVIPManager.addStargems(val);
                            main.textPanel.addParagraph("Auto-converted " + (ship.getShipName() != null ? ship.getShipName() : "Unknown Ship") + " to " + val + " Stargems.", Color.GREEN);
                        }
                    }
                    
                    // Show final options
                    showPostPullOptions();
                }
                
                public void cancelledFleetMemberPicking() {
                    // If cancelled, add all ships to fleet without conversion
                    for (FleetMemberAPI ship : obtainedShips) {
                        Global.getSector().getPlayerFleet().getFleetData().addFleetMember(ship);
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
                         for (FleetMemberAPI m : members) data.autoConvertIds.add(m.getHullId());
                     }
                     showGachaMenu();
                }
                public void cancelledFleetMemberPicking() { showGachaMenu(); }
            });
    }
}