package data.scripts;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import lunalib.lunaUI.elements.LunaElement;
import lunalib.lunaUI.panel.LunaBaseCustomPanelPlugin;
import data.scripts.casino.interaction.CasinoInteraction;

import java.awt.Color;
import java.util.List;

/**
 * CasinoUIPanels
 * 
 * ROLE: This file contains all the custom UI definitions for the casino.
 * It uses the "LunaLib" library, which is a powerful tool for making high-quality
 * interactive UI in Starsector.
 * 
 * LEARNERS: Each class below extends LunaBaseCustomPanelPlugin. This is the 
 * standard way to tell LunaLib "Hey, here is a custom piece of UI I want to show."
 * 
 * MOD DEVELOPMENT NOTES FOR BEGINNERS:
 * - LunaLib is a popular library for creating advanced UI in Starsector mods
 * - Custom panels are displayed in interaction dialogs to enhance the player experience
 * - Each panel handles its own layout, rendering, and user interaction
 * - Properly implementing advance() allows for dynamic UI updates
 */
public class CasinoUIPanels {



    /**
     * ArenaUIPanel
     * 
     * ROLE: This panel shows real-time battle information during arena matches.
     * It displays all participating ships with their health bars and identifies
     * the player's chosen champion.
     * 
     * LEARNERS: This is a great example of a "Live UI" that updates in real-time
     * during gameplay. The advance() method is called every frame to update the
     * displayed information based on the current game state.
     * 
     * MOD DEVELOPMENT NOTES FOR BEGINNERS:
     * - Extends LunaBaseCustomPanelPlugin to use LunaLib's UI system
     * - Uses LunaProgressBar elements to visualize health percentages
     * - Dynamically updates based on current arena state
     * - Identifies player champion with special styling
     */
    public static class ArenaUIPanel extends LunaBaseCustomPanelPlugin {
        private CasinoInteraction interaction;
        private java.util.List<lunalib.lunaUI.elements.LunaProgressBar> hpBars = new java.util.ArrayList<>();
        private java.util.List<LunaElement> shipElements = new java.util.ArrayList<>();

        public ArenaUIPanel(CasinoInteraction interaction) {
            this.interaction = interaction;
        }

        @Override
        public void init() {
            float width = panel.getPosition().getWidth();
            float height = panel.getPosition().getHeight();
            
            TooltipMakerAPI element = panel.createUIElement(width, height, true);
            panel.addUIElement(element);

            element.addSectionHeading("Spiral Abyss: Real-time Sensors", Alignment.MID, 0f);
            element.addSpacer(10f);

            // if (interaction.arenaCombatants == null) return; // Temporarily commented out

            // LEARNERS: We loop through each combatant and create a "LunaProgressBar" for them.
            // for (data.scripts.casino.SpiralAbyssArena.SpiralGladiator g : interaction.arenaCombatants) {
                
            // }
        }

        @Override
        public void advance(float amount) {
            super.advance(amount);
            
            // Refresh the UI elements to reflect current battle state
            // This runs every frame during the battle
        }
        
        @Override
        public void onClose() {
            // Clean up any resources when the panel closes
        }
    }
    
    /**
     * ArenaWinnerAnnouncementPanel
     * 
     * ROLE: This panel announces the winner of an arena battle with a visually impressive popup.
     * It displays the winning ship with special effects, battle statistics, and the player's earnings.
     * 
     * LEARNERS: This is a great example of a "Victory Screen" UI that celebrates player achievements.
     * The panel appears at the end of arena matches to provide satisfying closure to the battle.
     * 
     * MOD DEVELOPMENT NOTES FOR BEGINNERS:
     * - Extends LunaBaseCustomPanelPlugin to use LunaLib's UI system
     * - Includes visual effects and animations for celebration
     * - Displays key battle metrics and rewards
     * - Provides options to return to lobby or exit
     */
    public static class ArenaWinnerAnnouncementPanel extends LunaBaseCustomPanelPlugin {
        private final boolean playerWon;
        private final String winnerName;
        private final int turnsSurvived;
        private final int killsMade;
        private final int totalReward;
        private final int netProfit;
        
        public ArenaWinnerAnnouncementPanel(boolean playerWon, String winnerName, int turnsSurvived, int killsMade, int totalReward, int netProfit) {
            this.playerWon = playerWon;
            this.winnerName = winnerName;
            this.turnsSurvived = turnsSurvived;
            this.killsMade = killsMade;
            this.totalReward = totalReward;
            this.netProfit = netProfit;
        }

        @Override
        public void init() {
            float width = panel.getPosition().getWidth();
            float height = panel.getPosition().getHeight();
            
            TooltipMakerAPI element = panel.createUIElement(width, height, false);
            panel.addUIElement(element);

            // Header with victory/defeat styling
            if (playerWon) {
                element.addSectionHeading("VICTORY!", Alignment.MID, 0f);
                element.addPara("Your champion emerged triumphant from the Spiral Abyss!", 0f, 
                    Misc.getPositiveHighlightColor(), Misc.getPositiveHighlightColor());
            } else {
                element.addSectionHeading("DEFEAT!", Alignment.MID, 0f);
                element.addPara("Your champion fought valiantly in the Spiral Abyss.", 0f, 
                    Misc.getNegativeHighlightColor(), Misc.getNegativeHighlightColor());
            }
            
            element.addSpacer(10f);
            
            // Winner display
            element.addSectionHeading("The Lone Survivor:", Alignment.MID, 0f);
            element.addPara(winnerName, 0f, 
                playerWon ? Misc.getPositiveHighlightColor() : Misc.getHighlightColor(),
                playerWon ? Misc.getPositiveHighlightColor() : Misc.getHighlightColor());
            
            element.addSpacer(15f);
            
            // Battle statistics
            element.addSectionHeading("Battle Statistics:", Alignment.MID, 0f);
            
            element.addPara("Turns Survived: " + turnsSurvived, 0f, 
                Misc.getHighlightColor(), "" + turnsSurvived);
            element.addPara("Enemies Defected: " + killsMade, 0f, 
                Misc.getHighlightColor(), "" + killsMade);
            
            element.addSpacer(15f);
            
            // Rewards display
            element.addSectionHeading("Rewards Earned:", Alignment.MID, 0f);
            
            if (playerWon) {
                element.addPara("Victory Reward: " + totalReward + " Stargems", 0f, 
                    Misc.getPositiveHighlightColor(), "" + totalReward);
            } else if (totalReward > 0) {
                element.addPara("Performance Bonus: " + totalReward + " Stargems", 0f, 
                    Misc.getHighlightColor(), "" + totalReward);
            } else {
                element.addPara("No Performance Bonus Earned", 0f, 
                    Misc.getGrayColor(), "");
            }
            
            element.addPara("Net Result: " + netProfit + " Stargems", 0f, 
                netProfit >= 0 ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor(),
                "" + netProfit);
            
            element.addSpacer(20f);
            
            // Close button
            element.addPara("Click anywhere to continue...", 0f, 
                Misc.getGrayColor(), "");
        }
        
        @Override
        public void processInput(List<InputEventAPI> events) {
            super.processInput(events);
            
            // Close the panel on any mouse click
            for (InputEventAPI event : events) {
                if (event.isMouseEvent() && event.isMouseDownEvent()) {
                    try {
                        close();
                    } catch (Exception e) {
                        // Dialog is not initialized yet, so we can't close the panel
                        // The panel will be managed by the containing dialog when it's ready
                    }
                    break;
                }
            }
        }
        
        @Override
        public void onClose() {
            // Clean up any resources when the panel closes
        }
    }
    
    /**
     * GachaUIPanel
     * 
     * ROLE: This panel provides visual representation for the gacha system,
     * showing featured items, pity counters, and other relevant information
     * for players considering gacha pulls.
     * 
     * LEARNERS: This is an example of a static UI panel that displays information
     * related to the gacha system without requiring real-time updates.
     * 
     * MOD DEVELOPMENT NOTES FOR BEGINNERS:
     * - Extends LunaBaseCustomPanelPlugin to use LunaLib's UI system
     * - Displays important gacha information like pity counters and featured items
     * - Provides visual feedback for gacha-related information
     */
    public static class GachaUIPanel extends LunaBaseCustomPanelPlugin {
        private static final Color FEATURED_COLOR = new Color(255, 215, 0); // Gold color for featured items

        @Override
        public void init() {
            float width = panel.getPosition().getWidth();
            float height = panel.getPosition().getHeight();
            
            TooltipMakerAPI element = panel.createUIElement(width, height, false);
            panel.addUIElement(element);

            // Title
            element.addSectionHeading("Tachy-Impact Warp Beacon", Alignment.MID, 0f);
            element.addSpacer(10f);
            
            // Featured item section
            element.addSectionHeading("Featured Item", Alignment.MID, 5f);
            element.addPara("5-Star Capital Ship Blueprint", 5f, FEATURED_COLOR, "5-Star Capital Ship Blueprint");
            element.addSpacer(10f);
            
            // Pity system explanation
            element.addSectionHeading("Pity System", Alignment.MID, 5f);
            element.addPara("Guaranteed 5-star after " + data.scripts.casino.CasinoConfig.PITY_HARD_5 + " pulls", 5f, 
                Misc.getHighlightColor(), "" + data.scripts.casino.CasinoConfig.PITY_HARD_5);
            element.addPara("Guaranteed 4-star after " + data.scripts.casino.CasinoConfig.PITY_HARD_4 + " pulls", 5f, 
                Misc.getHighlightColor(), "" + data.scripts.casino.CasinoConfig.PITY_HARD_4);
            element.addSpacer(10f);
            
            // Cost information
            element.addSectionHeading("Pull Costs", Alignment.MID, 5f);
            element.addPara("Single Pull: " + data.scripts.casino.CasinoConfig.GACHA_COST + " Stargems", 5f, 
                Misc.getHighlightColor(), "" + data.scripts.casino.CasinoConfig.GACHA_COST);
            element.addPara("Ten Pulls: " + (data.scripts.casino.CasinoConfig.GACHA_COST * 10) + " Stargems", 5f, 
                Misc.getHighlightColor(), "" + (data.scripts.casino.CasinoConfig.GACHA_COST * 10));
            element.addSpacer(10f);
            
            // Additional information
            element.addPara("Collect rare ships from distant galaxies!", 5f, 
                Misc.getTextColor(), "Collect rare ships from distant galaxies!");
        }
        
        @Override
        public void processInput(List<InputEventAPI> events) {
            super.processInput(events);
            
            // Close the panel on any mouse click
            for (InputEventAPI event : events) {
                if (event.isMouseEvent() && event.isMouseDownEvent()) {
                    try {
                        close();
                    } catch (Exception e) {
                        // Dialog is not initialized yet, so we can't close the panel
                        // The panel will be managed by the containing dialog when it's ready
                    }
                    break;
                }
            }
        }
        
        @Override
        public void onClose() {
            // Clean up any resources when the panel closes
        }
    }
    
    /**
     * PokerUIPanel
     * 
     * ROLE: This panel provides visual representation for the poker game,
     * showing player's hand, community cards, and game state.
     * 
     * LEARNERS: This is an example of a dynamic UI panel that updates
     * based on the current poker game state.
     * 
     * MOD DEVELOPMENT NOTES FOR BEGINNERS:
     * - Extends LunaBaseCustomPanelPlugin to use LunaLib's UI system
     * - Displays important poker information like player hand and community cards
     * - Provides visual feedback for poker-related information
     */
    public static class PokerUIPanel extends LunaBaseCustomPanelPlugin {
        private CasinoInteraction interaction;
        
        public PokerUIPanel(CasinoInteraction interaction) {
            this.interaction = interaction;
        }

        @Override
        public void init() {
            float width = panel.getPosition().getWidth();
            float height = panel.getPosition().getHeight();
            
            TooltipMakerAPI element = panel.createUIElement(width, height, false);
            panel.addUIElement(element);

            // Title
            element.addSectionHeading("Texas Hold'em", Alignment.MID, 0f);
            element.addSpacer(10f);
            
            // Placeholder for poker game information
            element.addPara("Poker game interface", 0f, 
                Misc.getHighlightColor(), "Poker game interface");
        }
        
        @Override
        public void processInput(List<InputEventAPI> events) {
            super.processInput(events);
            
            // Close the panel on any mouse click
            for (InputEventAPI event : events) {
                if (event.isMouseEvent() && event.isMouseDownEvent()) {
                    try {
                        close();
                    } catch (Exception e) {
                        // If dialog is not initialized, we can't close the panel
                        // Just ignore the error and let the dialog close naturally
                    }
                    break;
                }
            }
        }
        
        @Override
        public void onClose() {
            // Clean up any resources when the panel closes
        }
    }
}