package data.scripts.casino.interaction;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FleetMemberPickerListener;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import data.scripts.casino.CasinoConfig;
import data.scripts.casino.CasinoGachaManager;
import data.scripts.casino.CasinoVIPManager;
import data.scripts.casino.GachaAnimation;
import data.scripts.casino.GachaAnimationDialogDelegate;
import data.scripts.CasinoUIPanels;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

public class GachaHandler {

    private final CasinoInteraction main;

    private final Map<String, OptionHandler> handlers = new HashMap<>();
    private final Map<Predicate<String>, OptionHandler> predicateHandlers = new HashMap<>();
    
    // Track if we just completed a gacha pull animation
    private boolean justCompletedPull = false;
    private List<FleetMemberAPI> pendingShipsForConversion = new ArrayList<>();
    
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
    
    private boolean canAffordTransaction(int cost) {
        int availableCredit = CasinoVIPManager.getAvailableCredit();
        return availableCredit >= 0; // Player can afford if they have enough gems or available credit
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

    public void showGachaMenu() {
        main.options.clearOptions();
        
        // Check if we just completed a pull and need to show results
        if (justCompletedPull && !pendingShipsForConversion.isEmpty()) {
            justCompletedPull = false; // Reset the flag
            
            // Show ship picker for ships they don't want to convert
            showConvertSelectionPicker(pendingShipsForConversion);
            return; // Don't continue with normal menu display
        } else if (justCompletedPull) {
            // If we just completed a pull but got no ships, show normal menu
            justCompletedPull = false;
            
            main.textPanel.addPara("Gacha Results:", Color.CYAN);
            main.textPanel.addPara("No ships obtained from your pull.", Color.GRAY);
            
            main.options.addOption("Pull Again", "gacha_menu");
            main.options.addOption("Back to Main Menu", "back_menu");
            return;
        }
        
        // Normal menu display continues here
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
        
        // Show pull options if player can afford them within their available credit
        if (canAffordTransaction(CasinoConfig.GACHA_COST)) {
            main.options.addOption("Pull 1x (" + CasinoConfig.GACHA_COST + " Gems)", "pull_1");
        }
        if (canAffordTransaction(CasinoConfig.GACHA_COST * 10)) {
            main.options.addOption("Pull 10x (" + (CasinoConfig.GACHA_COST * 10) + " Gems)", "pull_10");
        }
        
        main.options.addOption("View Ship Pool and Select Auto-Convert", "auto_convert");
        main.options.addOption("Gacha Handbook", "how_to_gacha");
        main.options.addOption("Back", "back_menu");
        main.setState(CasinoInteraction.State.GACHA);
    }
    
    private void showGachaConfirm(int times) {
        main.options.clearOptions();
        int cost = times * CasinoConfig.GACHA_COST;
        main.textPanel.addPara("Confirm initiating Warp Sequence " + times + "x for " + cost + " Stargems?", Color.YELLOW);
        
        main.options.addOption("Confirm Warp", "confirm_pull_" + times);
        main.options.addOption("Cancel", "gacha_menu");
    }
    
    private void performGachaPull(int times) {
        int cost = times * CasinoConfig.GACHA_COST;
        int currentGems = CasinoVIPManager.getStargems();
        
        // Check if player has enough gems or available credit
        if (currentGems < cost) {
            // Check if they have enough available credit to go into debt
            int availableCredit = CasinoVIPManager.getAvailableCredit();
            if (availableCredit < cost) {
                main.textPanel.addPara("Insufficient Stargems! You have reached your debt ceiling.", Color.RED);
                showGachaMenu();
                return;
            }
            
            // Player can afford by going into debt
            // Add debt equal to the difference
            int debtToAdd = cost - currentGems;
            CasinoVIPManager.addDebt(debtToAdd);
            CasinoVIPManager.addStargems(-currentGems); // Spend all available gems
        } else {
            // Player has enough gems to cover the cost
            CasinoVIPManager.addStargems(-cost);
        }
        
        CasinoGachaManager manager = new CasinoGachaManager();
        main.textPanel.addPara("Initiating Warp Sequence...", Color.CYAN);
        
        // Play gacha pull sound
        // Removed sound effect as it was removed from sounds.json
// Global.getSoundPlayer().playUISound("gacha_pull", 1f, 1f);
        
        // Collect ships that were obtained from the pulls
        List<FleetMemberAPI> obtainedShips = new ArrayList<>();
        
        // Perform pulls and collect results for animation
        List<String> pullResults = new ArrayList<>();
        for (int i=0; i<times; i++) {
            String result = manager.performPullDetailed(obtainedShips); // Modified method to collect ships
            pullResults.add(result);
        }
        
        // Show the gacha animation before displaying results
        showGachaAnimation(pullResults, obtainedShips);
    }
    
    private void showGachaAnimation(List<String> pullResults, List<FleetMemberAPI> obtainedShips) {
        // Create gacha items for animation
        List<GachaAnimation.GachaItem> itemsToAnimate = new ArrayList<>();
        
        for (String result : pullResults) {
            // Parse the result to create gacha items
            // This is a simplified approach - in a real implementation you'd parse the actual results
            int rarity = 3; // Default to 3 stars for demonstration
            if (result.contains("5*") || result.toLowerCase().contains("legendary")) {
                rarity = 5;
            } else if (result.contains("4*") || result.toLowerCase().contains("rare")) {
                rarity = 4;
            } else if (result.contains("3*") || result.toLowerCase().contains("uncommon")) {
                rarity = 3;
            } else if (result.contains("2*") || result.toLowerCase().contains("common")) {
                rarity = 2;
            }
            
            GachaAnimation.GachaItem item = new GachaAnimation.GachaItem(
                "item_" + System.currentTimeMillis() + "_" + itemsToAnimate.size(),
                result,
                rarity
            );
            itemsToAnimate.add(item);
        }
        
        // Store the obtained ships for processing after animation
        this.pendingShipsForConversion.clear();
        this.pendingShipsForConversion.addAll(obtainedShips);
        
        // Create the animation
        GachaAnimation animation = new GachaAnimation(itemsToAnimate, new GachaAnimation.GachaAnimationCallback() {
            @Override
            public void onAnimationComplete(List<GachaAnimation.GachaItem> results) {
                // Mark that we just completed a pull so we know to show results next
                justCompletedPull = true;
                
                // The animation dialog will dismiss itself, and the results will be shown
                // when the user returns to the gacha menu
            }
        });
        
        // Show the animation in a custom dialog
        GachaAnimationDialogDelegate delegate = new GachaAnimationDialogDelegate(null, animation, main.getDialog(), null);
        
        // Show the custom visual dialog
        main.getDialog().showCustomVisualDialog(600f, 400f, delegate);
    }
    
    private void showPullResults(List<GachaAnimation.GachaItem> animatedResults, List<FleetMemberAPI> obtainedShips) {
        // This method may still be called directly in some cases, but we'll handle it by
        // storing the results and redirecting to the gacha menu which will show them
        this.pendingShipsForConversion.clear();
        this.pendingShipsForConversion.addAll(obtainedShips);
        this.justCompletedPull = true;
        
        // Redirect to gacha menu which will handle showing the results
        showGachaMenu();
    }
    
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
                    // Show the post-pull options after cancellation
                    showPostPullOptions();
                }
            });
    }
    
    private void showPostPullOptions() {
        main.options.clearOptions();
        main.options.addOption("Pull Again", "gacha_menu");
        main.options.addOption("Back to Main Menu", "back_menu");
    }
    
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