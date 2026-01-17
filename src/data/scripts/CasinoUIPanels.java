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

public class CasinoUIPanels {



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
            element.addPara("Guaranteed 5-star after " + data.scripts.casino.util.ConfigManager.PITY_HARD_5 + " pulls", 5f, 
                Misc.getHighlightColor(), "" + data.scripts.casino.util.ConfigManager.PITY_HARD_5);
            element.addPara("Guaranteed 4-star after " + data.scripts.casino.util.ConfigManager.PITY_HARD_4 + " pulls", 5f, 
                Misc.getHighlightColor(), "" + data.scripts.casino.util.ConfigManager.PITY_HARD_4);
            element.addSpacer(10f);
            
            // Cost information
            element.addSectionHeading("Pull Costs", Alignment.MID, 5f);
            element.addPara("Single Pull: " + data.scripts.casino.util.ConfigManager.GACHA_COST + " Stargems", 5f, 
                Misc.getHighlightColor(), "" + data.scripts.casino.util.ConfigManager.GACHA_COST);
            element.addPara("Ten Pulls: " + (data.scripts.casino.util.ConfigManager.GACHA_COST * 10) + " Stargems", 5f, 
                Misc.getHighlightColor(), "" + (data.scripts.casino.util.ConfigManager.GACHA_COST * 10));
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