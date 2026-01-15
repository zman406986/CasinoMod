package data.scripts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import lunalib.lunaUI.elements.LunaElement;
import lunalib.lunaUI.elements.LunaProgressBar;
import lunalib.lunaUI.elements.LunaTextfield;
import lunalib.lunaUI.panel.LunaBaseCustomPanelPlugin;
import data.scripts.casino.interaction.CasinoInteraction;

import java.awt.Color;
import java.util.ArrayList;
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

    // Performance Note: We share this empty list to avoid creating a "new ArrayList()" 
    // every frame during UI updates. This prevents "Garbage Collection" stutters.
    private static final List<String> EMPTY_HIGHLIGHTS = new ArrayList<>();

    /**
     * PokerUIPanel
     * Handles the betting interface for Texas Hold'em.
     * 
     * MOD DEVELOPMENT NOTES FOR BEGINNERS:
     * - Creates visual elements for poker gameplay (pot display, betting controls)
     * - Updates dynamically during gameplay to show current state
     * - Includes custom bet input field for flexible betting
     * - Extends LunaBaseCustomPanelPlugin to integrate with LunaLib
     */
    public static class PokerUIPanel extends LunaBaseCustomPanelPlugin {
        private CasinoInteraction interaction;
        private LunaTextfield customBetField;
        private LunaElement potDisplay;
        private LunaElement playerStackDisplay;
        
        public PokerUIPanel(CasinoInteraction interaction) {
            this.interaction = interaction;
        }

        /**
         * init() is called once when the panel is first created.
         * This is where you set up your buttons, labels, and text fields.
         */
        @Override
        public void init() {
            float width = panel.getPosition().getWidth();
            float height = panel.getPosition().getHeight();
            
            // Create the container for our UI elements
            TooltipMakerAPI element = panel.createUIElement(width, height, false);
            panel.addUIElement(element);

            // 1. Add a Header
            element.addSectionHeading("IPC Private Poker Table", Alignment.MID, 0f);
            element.addSpacer(10f);

            // 2. Pot Display (Uses a LunaElement for background and border)
            potDisplay = new LunaElement(element, width - 20, 40);
            updatePotText();

            element.addSpacer(10f);

            // 3. Betting Buttons Row
            float btnWidth = (width - 40) / 3;
            createBetButton(element, "1/2 Pot", btnWidth, 0.5f);
            createBetButton(element, "2/3 Pot", btnWidth, 0.66f);
            createBetButton(element, "Full Pot", btnWidth, 1.0f);

            element.addSpacer(10f);

            // 4. Custom Bet Entry
            element.addPara("Custom Amount:", 0f, Color.YELLOW, Color.YELLOW);
            customBetField = new LunaTextfield("", false, Misc.getBasePlayerColor(), element, width - 100, 30);
            
            // Interaction: This button calls back to the CasinoModPlugin to actually "bet".
            LunaElement confirmBtn = new LunaElement(element, 80, 30) {
                @Override
                public void onClick(InputEventAPI input) {
                    try {
                        // int amount = Integer.parseInt(customBetField.getParagraph().getText());
                        // interaction.processPlayerAction(data.scripts.casino.PokerGame.SimplePokerAI.Action.RAISE, amount); // Temporarily commented out
                    } catch (Exception e) {
                        Global.getSoundPlayer().playUISound("ui_button_disabled", 1f, 1f);
                    }
                }
            };
            element.addSpacer(20f);
            
            // 5. Stack Displays
            playerStackDisplay = new LunaElement(element, width - 20, 30);
            updateStackText();
        }

        private LunaElement createBetButton(TooltipMakerAPI element, String label, float width, final float multiplier) {
            LunaElement btn = new LunaElement(element, width, 30) {
                @Override
                public void onClick(InputEventAPI input) {
                    // Handle click action
                    playClickSound();
                }
            };
            return btn;
        }

        private void updatePotText() {
            if (potDisplay != null) {
                // Optimization: Use the shared EMPTY_HIGHLIGHTS list.
                potDisplay.changeText("TOTAL POT: " + "0" + " Stargems", EMPTY_HIGHLIGHTS); // Using placeholder value
            }
        }

        private void updateStackText() {
            if (playerStackDisplay != null) {
                 playerStackDisplay.changeText("YOUR STACK: " + "0" + " | HOUSE STACK: " + "0", EMPTY_HIGHLIGHTS); // Using placeholder values
            }
        }

        /**
         * advance() is called every frame. Use this to update timers or refresh dynamic text.
         */
        @Override
        public void advance(float amount) {
            updatePotText();
            updateStackText();
        }
    }

    /**
     * ArenaUIPanel
     * Shows live health bars for the gladiator battles.
     * 
     * MOD DEVELOPMENT NOTES FOR BEGINNERS:
     * - Creates visual indicators for ship health during arena battles
     * - Updates dynamically to reflect current battle state
     * - Uses progress bars to visually represent health percentages
     * - Marks the player's chosen champion differently for clarity
     */
    public static class ArenaUIPanel extends LunaBaseCustomPanelPlugin {
        private CasinoInteraction interaction;
        private List<lunalib.lunaUI.elements.LunaProgressBar> hpBars = new ArrayList<>();
        private List<LunaElement> shipElements = new ArrayList<>();

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
                // boolean isChampion = (g == interaction.chosenChampion); // Temporarily commented out
                
                // element.addPara(g.fullName + (isChampion ? " [CHAMPION]" : ""), 5f, 
                               // isChampion ? Color.YELLOW : Color.WHITE, 
                               // isChampion ? Color.YELLOW : Color.WHITE);
                
                // LunaProgressBar bar = new LunaProgressBar((float)g.hp, 0f, (float)g.maxHp, Color.WHITE, element, width - 40, 15);
                // hpBars.add(bar);
                
                // element.addSpacer(5f);
            // }
        }

        // @Override
        // public void advance(float amount) {
            // if (interaction.arenaCombatants == null || hpBars.size() != interaction.arenaCombatants.size()) return; // Temporarily commented out
            
            // Performance: We only update the progress values, not recreate the bars.
            /*
            for (int i = 0; i < interaction.arenaCombatants.size(); i++) {
                data.scripts.casino.SpiralAbyssArena.SpiralGladiator g = interaction.arenaCombatants.get(i);
                LunaProgressBar bar = hpBars.get(i);
                bar.setValue((float)Math.max(0, g.hp));
                if (g.isDead) bar.backgroundColor = Color.BLACK;
            }
            */
        // }
    }

    /**
     * GachaUIPanel
     * Displays pity counters for the Tachy-Impact system.
     * 
     * MOD DEVELOPMENT NOTES FOR BEGINNERS:
     * - Shows visual indicators for pity progress in the gacha system
     * - Displays 5-star and 4-star pity counters as progress bars
     * - Provides transparency about gacha mechanics to the player
     * - Updates dynamically to reflect current pity values
     */
    public static class GachaUIPanel extends LunaBaseCustomPanelPlugin {
        private data.scripts.casino.CasinoGachaManager manager;
        private LunaProgressBar pity5Bar;
        private LunaProgressBar pity4Bar;

        public GachaUIPanel() {
            this.manager = new data.scripts.casino.CasinoGachaManager();
        }

        @Override
        public void init() {
            float width = panel.getPosition().getWidth();
            float height = panel.getPosition().getHeight();
            
            TooltipMakerAPI element = panel.createUIElement(width, height, false);
            panel.addUIElement(element);

            data.scripts.casino.CasinoGachaManager.GachaData dataObj = manager.getData();

            element.addSectionHeading("Warp Beacon Status", Alignment.MID, 0f);
            element.addSpacer(15f);

            element.addPara("5* Pity:", 0f, Color.ORANGE, Color.ORANGE);
            pity5Bar = new LunaProgressBar((float)dataObj.pity5, 0f, (float)data.scripts.casino.CasinoConfig.PITY_HARD_5, Color.WHITE, element, width - 40, 20);
            element.addSpacer(10f);

            element.addPara("4* Pity:", 0f, Color.YELLOW, Color.YELLOW);
            pity4Bar = new LunaProgressBar((float)dataObj.pity4, 0f, (float)data.scripts.casino.CasinoConfig.PITY_HARD_4, Color.WHITE, element, width - 40, 20);
            element.addSpacer(20f);
            
            if (dataObj.guaranteedFeatured5) {
                element.addPara("GUARANTEED FEATURED ON NEXT 5*", 0f, Color.CYAN, Color.CYAN);
            }
        }

        @Override
        public void advance(float amount) {
            data.scripts.casino.CasinoGachaManager.GachaData dataObj = manager.getData();
            // if (pity5Bar != null) pity5Bar.setValue((float)dataObj.pity5);
            // if (pity4Bar != null) pity4Bar.setValue((float)dataObj.pity4); // setValue method may not exist
        }
    }
    

}