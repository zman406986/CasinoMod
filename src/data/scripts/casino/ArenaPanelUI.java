package data.scripts.casino;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate.DialogCallbacks;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import data.scripts.casino.interaction.ArenaHandler.BetInfo;

public class ArenaPanelUI extends BaseCustomUIPanelPlugin {

    protected static class ParsedLogEntry {
        String type;
        String attackerHullId;
        String targetHullId;
        int damage;
        String description;
        String rawEntry;
        boolean isCrit;
        
        ParsedLogEntry(String raw) {
            this.rawEntry = raw;
            this.type = "";
            this.isCrit = false;
        }
        
        void parse(List<SpiralAbyssArena.SpiralGladiator> combatants) {
            if (rawEntry == null || rawEntry.isEmpty()) {
                type = "";
                return;
            }
            
            if (rawEntry.startsWith("[KILL]")) {
                type = "KILL";
                String content = rawEntry.substring(6).trim();
                parseKillLine(content, combatants);
            } else if (rawEntry.startsWith("[EVENT]")) {
                type = "EVENT";
                String content = rawEntry.substring(7).trim();
                parseEventLine(content, combatants);
            } else if (rawEntry.startsWith("[HIT]")) {
                type = "EVENT_HIT";
                String content = rawEntry.substring(5).trim();
                parseEventHitLine(content, combatants);
            } else if (rawEntry.contains("CRIT damage")) {
                type = "HIT";
                isCrit = true;
                parseAttackLine(rawEntry, combatants);
            } else if (rawEntry.contains(" damage") || rawEntry.contains(" for ")) {
                type = "HIT";
                parseAttackLine(rawEntry, combatants);
            } else if (rawEntry.contains("missed") || rawEntry.contains("Evaded")) {
                type = "MISS";
                parseMissLine(rawEntry, combatants);
            } else if (rawEntry.contains("--- SHIP STATUS ---") || rawEntry.contains("HP:")) {
                type = "STATUS";
            } else {
                type = "";
            }
        }
        
        void parseAttackLine(String line, List<SpiralAbyssArena.SpiralGladiator> combatants) {
            if (combatants == null) return;
            
            // Find positions of each ship name in the line to determine attacker (first) vs target (second)
            int firstPos = Integer.MAX_VALUE;
            int secondPos = Integer.MAX_VALUE;
            SpiralAbyssArena.SpiralGladiator firstShip = null;
            SpiralAbyssArena.SpiralGladiator secondShip = null;
            
            for (SpiralAbyssArena.SpiralGladiator ship : combatants) {
                int pos = line.indexOf(ship.shortName);
                if (pos >= 0) {
                    if (pos < firstPos) {
                        // Shift current first to second
                        secondPos = firstPos;
                        secondShip = firstShip;
                        // Set new first
                        firstPos = pos;
                        firstShip = ship;
                    } else if (pos < secondPos && !ship.hullId.equals(firstShip != null ? firstShip.hullId : null)) {
                        secondPos = pos;
                        secondShip = ship;
                    }
                }
            }
            
            if (firstShip != null) {
                attackerHullId = firstShip.hullId;
            }
            if (secondShip != null) {
                targetHullId = secondShip.hullId;
            }
            
            java.util.regex.Pattern dmgPattern = java.util.regex.Pattern.compile("(\\d+)");
            java.util.regex.Matcher matcher = dmgPattern.matcher(line);
            if (matcher.find()) {
                try {
                    damage = Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException e) {
                    damage = 0;
                }
            }
        }
        
        void parseKillLine(String line, List<SpiralAbyssArena.SpiralGladiator> combatants) {
            if (combatants == null) return;
            
            // Find positions of each ship name in the line to determine attacker (first) vs target (second)
            int firstPos = Integer.MAX_VALUE;
            int secondPos = Integer.MAX_VALUE;
            SpiralAbyssArena.SpiralGladiator firstShip = null;
            SpiralAbyssArena.SpiralGladiator secondShip = null;
            
            for (SpiralAbyssArena.SpiralGladiator ship : combatants) {
                int pos = line.indexOf(ship.shortName);
                if (pos >= 0) {
                    if (pos < firstPos) {
                        // Shift current first to second
                        secondPos = firstPos;
                        secondShip = firstShip;
                        // Set new first
                        firstPos = pos;
                        firstShip = ship;
                    } else if (pos < secondPos && !ship.hullId.equals(firstShip != null ? firstShip.hullId : null)) {
                        secondPos = pos;
                        secondShip = ship;
                    }
                }
            }
            
            if (firstShip != null) {
                attackerHullId = firstShip.hullId;
            }
            if (secondShip != null) {
                targetHullId = secondShip.hullId;
            }
        }
        
        void parseMissLine(String line, List<SpiralAbyssArena.SpiralGladiator> combatants) {
            if (combatants == null) return;
            
            // For miss lines: target appears first in text, attacker appears second
            // Example: "$target:'Too slow!' ($attacker missed)"
            int firstPos = Integer.MAX_VALUE;
            int secondPos = Integer.MAX_VALUE;
            SpiralAbyssArena.SpiralGladiator firstShip = null;
            SpiralAbyssArena.SpiralGladiator secondShip = null;
            
            for (SpiralAbyssArena.SpiralGladiator ship : combatants) {
                int pos = line.indexOf(ship.shortName);
                if (pos >= 0) {
                    if (pos < firstPos) {
                        // Shift current first to second
                        secondPos = firstPos;
                        secondShip = firstShip;
                        // Set new first
                        firstPos = pos;
                        firstShip = ship;
                    } else if (pos < secondPos && !ship.hullId.equals(firstShip != null ? firstShip.hullId : null)) {
                        secondPos = pos;
                        secondShip = ship;
                    }
                }
            }
            
            // For miss lines: first ship = TARGET, second ship = ATTACKER
            if (firstShip != null) {
                targetHullId = firstShip.hullId;
            }
            if (secondShip != null) {
                attackerHullId = secondShip.hullId;
            }
        }
        
        void parseEventLine(String line, List<SpiralAbyssArena.SpiralGladiator> combatants) {
            description = line;
            
            if (combatants == null) return;
            
            for (SpiralAbyssArena.SpiralGladiator ship : combatants) {
                if (line.contains(ship.shortName)) {
                    attackerHullId = ship.hullId;
                    break;
                }
            }
            
            java.util.regex.Pattern dmgPattern = java.util.regex.Pattern.compile("\\(-?(\\d+)");
            java.util.regex.Matcher matcher = dmgPattern.matcher(line);
            if (matcher.find()) {
                try {
                    damage = Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException e) {
                    damage = 0;
                }
            }
        }
        
        void parseEventHitLine(String line, List<SpiralAbyssArena.SpiralGladiator> combatants) {
            if (combatants == null) return;
            
            for (SpiralAbyssArena.SpiralGladiator ship : combatants) {
                if (line.contains(ship.shortName)) {
                    attackerHullId = ship.hullId;
                    break;
                }
            }
            
            java.util.regex.Pattern dmgPattern = java.util.regex.Pattern.compile("(\\d+)");
            java.util.regex.Matcher matcher = dmgPattern.matcher(line);
            if (matcher.find()) {
                try {
                    damage = Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException e) {
                    damage = 0;
                }
            }
        }
        
        boolean shouldSkipDeadAttacker(Map<String, Boolean> deadStatusMap) {
            if (type.equals("HIT") || type.equals("MISS") || type.equals("KILL")) {
                Boolean isDead = deadStatusMap.get(attackerHullId);
                return isDead != null && isDead;
            }
            return false;
        }
    }

    protected InteractionDialogAPI dialog;
    protected DialogCallbacks callbacks;
    protected CustomPanelAPI panel;
    protected PositionAPI p;
    
    protected ArenaActionCallback actionCallback;
    
    protected List<SpiralAbyssArena.SpiralGladiator> combatants;
    protected int currentRound;
    protected int totalBet;
    protected List<BetInfo> bets;
    protected List<String> battleLog;
    
    protected int selectedChampionIndex = -1;
    protected boolean battleEnded = false;
    protected int winnerIndex = -1;
    protected int totalReward = 0;
    protected RewardBreakdown rewardBreakdown;
    protected boolean readyToClose = false;
    
    protected boolean buttonsCreated = false;
    
    protected boolean lastShowingBetAmounts = false;
    protected boolean lastAddingBetDuringBattle = false;
    protected int lastSelectedChampionIndex = -2;
    protected boolean lastBattleEnded = true;
    protected int lastCurrentRoundForButtons = -1;
    
    protected static final float PANEL_WIDTH = 1000f;
    protected static final float PANEL_HEIGHT = 700f;
    
    protected static final float SHIP_COLUMN_WIDTH = 250f;
    protected static final float CENTER_COLUMN_WIDTH = 500f;
    protected static final float BATTLE_LOG_WIDTH = 280f;
    
    protected static final float BOX_WIDTH = 150f;
    protected static final float BOX_HEIGHT = 65f;
    protected static final float BOX_SPACING = 3f;
    protected static final float ENTRY_SPACING = 25f;
    
    protected static final float CHAMP_BUTTON_WIDTH = 100f;
    protected static final float CHAMP_BUTTON_HEIGHT = 25f;
    
    protected static final float BUTTON_WIDTH = 120f;
    protected static final float BUTTON_HEIGHT = 35f;
    protected static final float BUTTON_SPACING = 10f;
    
    protected static final float MARGIN = 20f;
    
    protected static final float leftX = SHIP_COLUMN_WIDTH + MARGIN;
    protected static final float bottomY = PANEL_HEIGHT - BUTTON_HEIGHT - MARGIN;
    
    protected static final Color COLOR_HEALTHY = new Color(50, 200, 50);
    protected static final Color COLOR_DAMAGED = new Color(200, 150, 50);
    protected static final Color COLOR_DESTROYED = new Color(100, 30, 30);
    protected static final Color COLOR_SELECTED = new Color(255, 215, 0);
    
    protected static final Color PREFIX_POSITIVE_COLOR = new Color(50, 255, 50);
    protected static final Color PREFIX_NEGATIVE_COLOR = new Color(255, 50, 50);
    protected static final Color AFFIX_POSITIVE_COLOR = new Color(100, 200, 100);
    protected static final Color AFFIX_NEGATIVE_COLOR = new Color(255, 150, 150);
    
    protected static final Color COLOR_BG_DARK = new Color(15, 15, 20);
    protected static final Color COLOR_SIDEBAR = new Color(25, 25, 35);
    protected static final Color COLOR_TINT_DEAD = new Color(100, 30, 30);
    protected static final Color COLOR_TINT_DAMAGED = new Color(255, 200, 100);
    protected static final Color COLOR_HP_BAR_BG = new Color(30, 30, 30);
    
    protected boolean isPrefixPositive(String prefix) {
        if (prefix == null) return true;
        return CasinoConfig.ARENA_PREFIX_STRONG_POS.contains(prefix);
    }
    
    protected boolean isAffixPositive(String affix) {
        if (affix == null) return true;
        return CasinoConfig.ARENA_AFFIX_POS.contains(affix);
    }
    
    protected Color getPrefixColor(String prefix) {
        return isPrefixPositive(prefix) ? PREFIX_POSITIVE_COLOR : PREFIX_NEGATIVE_COLOR;
    }
    
    protected Color getAffixColor(String affix) {
        return isAffixPositive(affix) ? AFFIX_POSITIVE_COLOR : AFFIX_NEGATIVE_COLOR;
    }
    
    protected void applyShipNameHighlighting(LabelAPI label, SpiralAbyssArena.SpiralGladiator ship) {
        if (label == null || ship == null) return;
        
        String prefix = ship.prefix != null ? ship.prefix : "";
        String hullName = ship.hullName != null ? ship.hullName : "";
        String affix = ship.affix != null ? ship.affix : "";
        
        List<String> highlights = new ArrayList<>();
        List<Color> colors = new ArrayList<>();
        
        if (!prefix.isEmpty()) {
            highlights.add(prefix);
            colors.add(getPrefixColor(prefix));
        }
        
        if (!hullName.isEmpty()) {
            highlights.add(hullName);
            colors.add(Color.YELLOW);
        }
        
        if (!affix.isEmpty()) {
            highlights.add(affix);
            colors.add(getAffixColor(affix));
        }
        
        if (!highlights.isEmpty()) {
            label.setHighlight(highlights.toArray(new String[0]));
            label.setHighlightColors(colors.toArray(new Color[0]));
        }
    }
    
    protected LabelAPI roundLabel;
    protected CustomPanelAPI roundPanel;
    
    protected LabelAPI betLabel;
    protected CustomPanelAPI betPanel;
    
    protected LabelAPI[] shipNameLabels = new LabelAPI[5];
    protected CustomPanelAPI[] shipNamePanels = new CustomPanelAPI[5];
    
    protected LabelAPI[] shipHpLabels = new LabelAPI[5];
    protected CustomPanelAPI[] shipHpPanels = new CustomPanelAPI[5];
    
    protected LabelAPI[] shipOddsLabels = new LabelAPI[5];
    protected CustomPanelAPI[] shipOddsPanels = new CustomPanelAPI[5];
    
    protected CustomPanelAPI battleLogPanel;
    
    protected LabelAPI[] battleLogTextLabels = new LabelAPI[12];
    protected CustomPanelAPI[] battleLogTextPanels = new CustomPanelAPI[12];
    
    protected static final float LOG_SPRITE_SIZE = 28f;
    protected static final float LOG_LINE_HEIGHT = 32f;
    protected static final float LOG_SPRITE_GAP = 4f;
    protected static final float LOG_LEFT_MARGIN = 5f;
    
    protected LabelAPI resultLabel;
    protected CustomPanelAPI resultPanel;
    
    protected static final int MAX_REWARD_LINES = 25;
    protected LabelAPI[] rewardBreakdownLabels = new LabelAPI[MAX_REWARD_LINES];
    protected CustomPanelAPI[] rewardBreakdownPanels = new CustomPanelAPI[MAX_REWARD_LINES];
    
    protected LabelAPI instructionLabel;
    protected CustomPanelAPI instructionPanel;
    
    protected ButtonAPI watchNextButton;
    protected ButtonAPI skipToEndButton;
    protected ButtonAPI addBetButton;
    protected ButtonAPI suspendButton;
    protected ButtonAPI leaveButton;
    protected ButtonAPI returnToLobbyButton;
    protected ButtonAPI startBattleButton;
    protected ButtonAPI cancelButton;
    
    protected CustomPanelAPI watchNextPanel;
    protected CustomPanelAPI skipToEndPanel;
    protected CustomPanelAPI addBetPanel;
    protected CustomPanelAPI suspendPanel;
    protected CustomPanelAPI leaveButtonPanel;
    protected CustomPanelAPI returnToLobbyPanel;
    protected CustomPanelAPI startBattlePanel;
    protected CustomPanelAPI cancelPanel;
    
    protected List<ButtonAPI> championSelectButtons = new ArrayList<>();
    protected List<ButtonAPI> betAmountButtons = new ArrayList<>();
    protected List<CustomPanelAPI> championSelectPanels = new ArrayList<>();
    protected List<CustomPanelAPI> betAmountPanels = new ArrayList<>();
    
    protected static final int[] BET_AMOUNTS = {100, 500, 1000, 2000, 5000};
    
    protected Map<String, SpriteAPI> spriteCache = new HashMap<>();
    protected static final float SPRITE_SCALE = 0.75f;
    
    protected float[] cachedOdds = new float[5];
    protected boolean oddsCached = false;
    
    protected int pendingBetAmount = 0;
    protected boolean showingBetAmounts = false;
    protected boolean addingBetDuringBattle = false;
    
    protected float logX;
    protected float logY;
    protected float logW;
    protected float logH;
    
    protected List<ParsedLogEntry> cachedParsedEntries = new ArrayList<>();
    protected Map<String, Boolean> hullIdDeadStatus = new HashMap<>();
    protected int lastBattleLogSize = -1;
    
    protected int lastCurrentRound = -1;
    protected int lastTotalBet = -1;
    protected String lastInstructionText = null;
    
    protected int[] lastShipHp = new int[5];
    protected int[] lastShipMaxHp = new int[5];
    protected boolean[] lastShipDead = new boolean[5];
    protected float[] lastShipOdds = new float[5];
    protected int[] lastShipBetCount = new int[5];
    protected boolean shipStateInitialized = false;

    public interface ArenaActionCallback {
        void onSelectChampion(int championIndex);
        void onConfirmBet(int championIndex, int amount);
        void onStartBattle();
        void onWatchNextRound();
        void onSkipToEnd();
        void onAddBetToChampion(int championIndex, int amount);
        void onSuspend();
        void onLeave();
        void onReturnToLobby();
    }
    
    public static class RewardBreakdown {
        public int totalBet;
        public int winReward;
        public int consolationReward;
        public int totalReward;
        public int netResult;
        public List<ShipRewardInfo> shipRewards = new ArrayList<>();
        
        public static class ShipRewardInfo {
            public String shipName;
            public int betAmount;
            public int kills;
            public int finalPosition;
            public boolean isWinner;
            public int reward;
            
            public ShipRewardInfo(String shipName, int betAmount, int kills, int finalPosition, boolean isWinner, int reward) {
                this.shipName = shipName;
                this.betAmount = betAmount;
                this.kills = kills;
                this.finalPosition = finalPosition;
                this.isWinner = isWinner;
                this.reward = reward;
            }
        }
    }
    
    public ArenaPanelUI(
            List<SpiralAbyssArena.SpiralGladiator> combatants,
            int currentRound,
            int totalBet,
            List<BetInfo> bets,
            List<String> battleLog,
            ArenaActionCallback callback) {
        
        this.combatants = combatants;
        this.currentRound = currentRound;
        this.totalBet = totalBet;
        this.bets = bets;
        this.battleLog = battleLog;
        this.actionCallback = callback;
        
        }
    
    protected SpriteAPI getShipSprite(String hullId) {
        if (hullId == null || hullId.isEmpty()) return null;
        
        if (spriteCache.containsKey(hullId)) {
            return spriteCache.get(hullId);
        }
        
        try {
            ShipHullSpecAPI spec = Global.getSettings().getHullSpec(hullId);
            if (spec == null) {
                spriteCache.put(hullId, null);
                return null;
            }
            
            String spriteName = spec.getSpriteName();
            if (spriteName == null || spriteName.isEmpty()) {
                spriteCache.put(hullId, null);
                return null;
            }
            
            SpriteAPI sprite = Global.getSettings().getSprite(spriteName);
            spriteCache.put(hullId, sprite);
            return sprite;
        } catch (Exception e) {
            spriteCache.put(hullId, null);
            return null;
        }
    }
    
    public void init(CustomPanelAPI panel, DialogCallbacks callbacks, InteractionDialogAPI dialog) {
        this.panel = panel;
        this.callbacks = callbacks;
        this.dialog = dialog;
        
        callbacks.getPanelFader().setDurationOut(0.5f);
        
        cacheOdds();
        createUIElements();
        updateLabels();
    }
    
    public void positionChanged(PositionAPI position) {
        this.p = position;
    }
    
    protected void createUIElements() {
        if (panel == null) return;
        
        createRoundLabel();
        createBetLabel();
        createInstructionLabel();
        createShipLabels();
        createBattleLogPanel();
        createResultLabel();
        createRewardBreakdownLabels();
        createAllButtonsOnce();
        
        buttonsCreated = true;
    }
    
    protected void createBattleLogPanel() {
        if (panel == null) return;
        
        // Move battle log to center column, below round/total bet
        float logPanelX = SHIP_COLUMN_WIDTH + MARGIN;
        float logPanelY = MARGIN + 40f;
        float logPanelW = CENTER_COLUMN_WIDTH - MARGIN;
        float logPanelH = PANEL_HEIGHT - MARGIN * 2 - 80f;
        
        logX = LOG_LEFT_MARGIN;
        logY = LOG_LINE_HEIGHT;
        logW = logPanelW - LOG_LEFT_MARGIN * 2;
        logH = logPanelH - LOG_LINE_HEIGHT;
        
        battleLogPanel = panel.createCustomPanel(logPanelW, logPanelH, null);
        panel.addComponent(battleLogPanel).inTL(logPanelX, logPanelY);
        
        float textWidthTwoSprites = logW - LOG_SPRITE_SIZE * 2 - LOG_SPRITE_GAP * 2;
        float textWidthOneSprite = logW - LOG_SPRITE_SIZE - LOG_SPRITE_GAP;
        
        for (int i = 0; i < 12; i++) {
            battleLogTextPanels[i] = panel.createCustomPanel(Math.max(textWidthTwoSprites, textWidthOneSprite), LOG_LINE_HEIGHT, null);
            TooltipMakerAPI tooltip = battleLogTextPanels[i].createUIElement(Math.max(textWidthTwoSprites, textWidthOneSprite), LOG_LINE_HEIGHT, false);
            battleLogTextLabels[i] = tooltip.addPara("", Color.WHITE, 0f);
            battleLogTextLabels[i].setAlignment(Alignment.MID);
            battleLogTextLabels[i].getPosition().inTL(0, 0);
            battleLogTextPanels[i].addUIElement(tooltip).inTL(0, 0);
            panel.addComponent(battleLogTextPanels[i]).inTL(-1000f, -1000f);
        }
    }
    
    protected void createAllButtonsOnce() {
        if (panel == null) return;
        
        float champButtonX = BOX_WIDTH + MARGIN + 15f;
        float startY = MARGIN + 10f;
        float totalItemHeight = BOX_HEIGHT + BOX_SPACING + 16f + 11f + 13f + ENTRY_SPACING;
        
        for (int i = 0; i < 5; i++) {
            float shipY = startY + i * totalItemHeight;
            float buttonY = shipY + (BOX_HEIGHT - CHAMP_BUTTON_HEIGHT) / 2f;
            
            CustomPanelAPI champPanel = panel.createCustomPanel(CHAMP_BUTTON_WIDTH, CHAMP_BUTTON_HEIGHT, null);
            TooltipMakerAPI champTooltip = champPanel.createUIElement(CHAMP_BUTTON_WIDTH, CHAMP_BUTTON_HEIGHT, false);
            ButtonAPI btn = champTooltip.addButton("Select", "arena_champ_" + i, CHAMP_BUTTON_WIDTH, CHAMP_BUTTON_HEIGHT, 0f);
            btn.setCustomData(i);
            btn.getPosition().inTL(0, 0);
            champPanel.addUIElement(champTooltip).inTL(0, 0);
            panel.addComponent(champPanel).inTL(champButtonX, buttonY);
            champPanel.getPosition().inTL(-1000f, -1000f);
            championSelectPanels.add(champPanel);
            championSelectButtons.add(btn);
        }
        
        {
            float cancelY = startY + 5 * totalItemHeight;
            CustomPanelAPI champCancelPanel = panel.createCustomPanel(BUTTON_WIDTH, BUTTON_HEIGHT, null);
            TooltipMakerAPI champCancelTooltip = champCancelPanel.createUIElement(BUTTON_WIDTH, BUTTON_HEIGHT, false);
            ButtonAPI champCancelBtn = champCancelTooltip.addButton("Cancel", "arena_champ_cancel", BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
            champCancelBtn.setCustomData(-1);
            champCancelBtn.getPosition().inTL(0, 0);
            champCancelPanel.addUIElement(champCancelTooltip).inTL(0, 0);
            panel.addComponent(champCancelPanel).inTL(champButtonX, cancelY);
            champCancelPanel.getPosition().inTL(-1000f, -1000f);
            championSelectPanels.add(champCancelPanel);
            championSelectButtons.add(champCancelBtn);
        }
        
        CustomPanelAPI betCancelPanel = panel.createCustomPanel(BUTTON_WIDTH, BUTTON_HEIGHT, null);
        TooltipMakerAPI betCancelTooltip = betCancelPanel.createUIElement(BUTTON_WIDTH, BUTTON_HEIGHT, false);
        ButtonAPI betCancelBtn = betCancelTooltip.addButton("Cancel", "arena_bet_cancel", BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        betCancelBtn.setCustomData(-1);
        betCancelBtn.getPosition().inTL(0, 0);
        betCancelPanel.addUIElement(betCancelTooltip).inTL(0, 0);
        panel.addComponent(betCancelPanel).inTL(leftX, bottomY);
        betCancelPanel.getPosition().inTL(-1000f, -1000f);
        betAmountPanels.add(betCancelPanel);
        betAmountButtons.add(betCancelBtn);
        
        for (int i = 0; i < BET_AMOUNTS.length; i++) {
            int amt = BET_AMOUNTS[i];
            float btnX = leftX + (i + 1) * (BUTTON_WIDTH + BUTTON_SPACING);
            
            CustomPanelAPI betBtnPanel = panel.createCustomPanel(BUTTON_WIDTH, BUTTON_HEIGHT, null);
            TooltipMakerAPI betTooltip = betBtnPanel.createUIElement(BUTTON_WIDTH, BUTTON_HEIGHT, false);
            ButtonAPI btn = betTooltip.addButton(amt + " SG", "arena_bet_" + amt, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
            btn.setCustomData(amt);
            btn.getPosition().inTL(0, 0);
            betBtnPanel.addUIElement(betTooltip).inTL(0, 0);
            panel.addComponent(betBtnPanel).inTL(btnX, bottomY);
            betBtnPanel.getPosition().inTL(-1000f, -1000f);
            betAmountPanels.add(betBtnPanel);
            betAmountButtons.add(btn);
        }
        
        returnToLobbyPanel = panel.createCustomPanel(BUTTON_WIDTH, BUTTON_HEIGHT, null);
        TooltipMakerAPI returnTooltip = returnToLobbyPanel.createUIElement(BUTTON_WIDTH, BUTTON_HEIGHT, false);
        returnToLobbyButton = returnTooltip.addButton("Return to Lobby", "arena_return_lobby", BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        returnToLobbyButton.getPosition().inTL(0, 0);
        returnToLobbyPanel.addUIElement(returnTooltip).inTL(0, 0);
        panel.addComponent(returnToLobbyPanel).inTL(leftX, bottomY);
        returnToLobbyPanel.getPosition().inTL(-1000f, -1000f);
        
        leaveButtonPanel = panel.createCustomPanel(BUTTON_WIDTH, BUTTON_HEIGHT, null);
        TooltipMakerAPI leaveTooltip = leaveButtonPanel.createUIElement(BUTTON_WIDTH, BUTTON_HEIGHT, false);
        leaveButton = leaveTooltip.addButton("Leave", "arena_leave", BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        leaveButton.getPosition().inTL(0, 0);
        leaveButtonPanel.addUIElement(leaveTooltip).inTL(0, 0);
        panel.addComponent(leaveButtonPanel).inTL(leftX, bottomY);
        leaveButtonPanel.getPosition().inTL(-1000f, -1000f);
        
        suspendPanel = panel.createCustomPanel(BUTTON_WIDTH, BUTTON_HEIGHT, null);
        TooltipMakerAPI suspendTooltip = suspendPanel.createUIElement(BUTTON_WIDTH, BUTTON_HEIGHT, false);
        suspendButton = suspendTooltip.addButton("Suspend", "arena_suspend", BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        suspendButton.getPosition().inTL(0, 0);
        suspendPanel.addUIElement(suspendTooltip).inTL(0, 0);
        panel.addComponent(suspendPanel).inTL(leftX + BUTTON_WIDTH + BUTTON_SPACING, bottomY);
        suspendPanel.getPosition().inTL(-1000f, -1000f);
        
        addBetPanel = panel.createCustomPanel(BUTTON_WIDTH, BUTTON_HEIGHT, null);
        TooltipMakerAPI addBetTooltip = addBetPanel.createUIElement(BUTTON_WIDTH, BUTTON_HEIGHT, false);
        addBetButton = addBetTooltip.addButton("Add Bet", "arena_add_bet", BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        addBetButton.getPosition().inTL(0, 0);
        addBetPanel.addUIElement(addBetTooltip).inTL(0, 0);
        panel.addComponent(addBetPanel).inTL(leftX + (BUTTON_WIDTH + BUTTON_SPACING) * 2, bottomY);
        addBetPanel.getPosition().inTL(-1000f, -1000f);
        
        skipToEndPanel = panel.createCustomPanel(BUTTON_WIDTH, BUTTON_HEIGHT, null);
        TooltipMakerAPI skipTooltip = skipToEndPanel.createUIElement(BUTTON_WIDTH, BUTTON_HEIGHT, false);
        skipToEndButton = skipTooltip.addButton("Skip to End", "arena_skip", BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        skipToEndButton.getPosition().inTL(0, 0);
        skipToEndPanel.addUIElement(skipTooltip).inTL(0, 0);
        panel.addComponent(skipToEndPanel).inTL(leftX + (BUTTON_WIDTH + BUTTON_SPACING) * 3, bottomY);
        skipToEndPanel.getPosition().inTL(-1000f, -1000f);
        
        float topY = bottomY - BUTTON_HEIGHT - BUTTON_SPACING;
        watchNextPanel = panel.createCustomPanel(BUTTON_WIDTH, BUTTON_HEIGHT, null);
        TooltipMakerAPI watchTooltip = watchNextPanel.createUIElement(BUTTON_WIDTH, BUTTON_HEIGHT, false);
        watchNextButton = watchTooltip.addButton("Next Round", "arena_watch_next", BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        watchNextButton.getPosition().inTL(0, 0);
        watchNextPanel.addUIElement(watchTooltip).inTL(0, 0);
        panel.addComponent(watchNextPanel).inTL(leftX + (BUTTON_WIDTH + BUTTON_SPACING) * 4, bottomY);
        watchNextPanel.getPosition().inTL(-1000f, -1000f);
        
        startBattlePanel = panel.createCustomPanel(BUTTON_WIDTH, BUTTON_HEIGHT, null);
        TooltipMakerAPI startTooltip = startBattlePanel.createUIElement(BUTTON_WIDTH, BUTTON_HEIGHT, false);
        startBattleButton = startTooltip.addButton("Start Battle", "arena_start_battle", BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        startBattleButton.getPosition().inTL(0, 0);
        startBattlePanel.addUIElement(startTooltip).inTL(0, 0);
        panel.addComponent(startBattlePanel).inTL(leftX + BUTTON_WIDTH + BUTTON_SPACING, bottomY);
        startBattlePanel.getPosition().inTL(-1000f, -1000f);
    }
    
    protected void updateButtonVisibility() {
        boolean showChampionSelect = (currentRound == 0 && !showingBetAmounts && !battleEnded) ||
                                      (currentRound > 0 && showingBetAmounts && addingBetDuringBattle && selectedChampionIndex < 0 && !battleEnded);
        boolean showChampionAsBet = currentRound > 0 && showingBetAmounts && addingBetDuringBattle && selectedChampionIndex < 0;
        boolean showBetAmounts = showingBetAmounts && selectedChampionIndex >= 0 && !battleEnded;
        
        float champButtonX = BOX_WIDTH + MARGIN + 15f;
        float startY = MARGIN + 10f;
        float totalItemHeight = BOX_HEIGHT + BOX_SPACING + 16f + 11f + 13f + ENTRY_SPACING;
        
        for (int i = 0; i < championSelectPanels.size(); i++) {
            CustomPanelAPI p = championSelectPanels.get(i);
            if (p != null) {
                if (i < 5) {
                    // 5 champion select buttons
                    if (showChampionSelect || showChampionAsBet) {
                        float btnY = startY + i * totalItemHeight + (BOX_HEIGHT - CHAMP_BUTTON_HEIGHT) / 2f;
                        p.getPosition().inTL(champButtonX, btnY);
                    } else {
                        p.getPosition().inTL(-1000f, -1000f);
                    }
                } else {
                    // Cancel button - move to bottom row when champion select is shown
                    if (showChampionSelect) {
                        p.getPosition().inTL(leftX, bottomY);
                    } else {
                        p.getPosition().inTL(-1000f, -1000f);
                    }
                }
            }
        }
        
        float betRowY = bottomY - BUTTON_HEIGHT - BUTTON_SPACING;
        
        for (int i = 0; i < betAmountPanels.size(); i++) {
            CustomPanelAPI p = betAmountPanels.get(i);
            if (p != null) {
                if (showBetAmounts) {
                    float btnX;
                    float btnY;
                    if (i == 0) {
                        // Cancel button - same position as Leave
                        btnX = leftX;
                        btnY = bottomY;
                    } else {
                        // Bet amount buttons - on row above
                        btnX = leftX + (i - 1) * (BUTTON_WIDTH + BUTTON_SPACING);
                        btnY = betRowY;
                    }
                    p.getPosition().inTL(btnX, btnY);
                } else {
                    p.getPosition().inTL(-1000f, -1000f);
                }
            }
        }
        
        if (returnToLobbyPanel != null) {
            if (battleEnded) {
                returnToLobbyPanel.getPosition().inTL(leftX, bottomY);
            } else {
                returnToLobbyPanel.getPosition().inTL(-1000f, -1000f);
            }
        }
        
        if (leaveButtonPanel != null) {
            if (!battleEnded && !showBetAmounts && !showChampionSelect) {
                // Leave at normal position when not showing champion select or bet amounts
                leaveButtonPanel.getPosition().inTL(leftX, bottomY);
            } else if (!battleEnded && showChampionSelect) {
                // Leave to the right of Cancel when showing champion select
                leaveButtonPanel.getPosition().inTL(leftX + BUTTON_WIDTH + BUTTON_SPACING, bottomY);
            } else if (!battleEnded && showBetAmounts) {
                // Leave to the right of Cancel when showing bet amounts
                leaveButtonPanel.getPosition().inTL(leftX + BUTTON_WIDTH + BUTTON_SPACING, bottomY);
            } else {
                leaveButtonPanel.getPosition().inTL(-1000f, -1000f);
            }
        }
        
        if (suspendPanel != null) {
            if (currentRound > 0 && !showingBetAmounts && !battleEnded) {
                suspendPanel.getPosition().inTL(leftX + BUTTON_WIDTH + BUTTON_SPACING, bottomY);
            } else {
                suspendPanel.getPosition().inTL(-1000f, -1000f);
            }
        }
        
        if (addBetPanel != null) {
            if (currentRound > 0 && !showingBetAmounts && !battleEnded) {
                addBetPanel.getPosition().inTL(leftX + (BUTTON_WIDTH + BUTTON_SPACING) * 2, bottomY);
            } else {
                addBetPanel.getPosition().inTL(-1000f, -1000f);
            }
        }
        
        if (skipToEndPanel != null) {
            if (currentRound > 0 && !showingBetAmounts && !battleEnded) {
                skipToEndPanel.getPosition().inTL(leftX + (BUTTON_WIDTH + BUTTON_SPACING) * 3, bottomY);
            } else {
                skipToEndPanel.getPosition().inTL(-1000f, -1000f);
            }
        }
        
        if (watchNextPanel != null) {
            if (currentRound > 0 && !showingBetAmounts && !battleEnded) {
                watchNextPanel.getPosition().inTL(leftX + (BUTTON_WIDTH + BUTTON_SPACING) * 4, bottomY);
            } else {
                watchNextPanel.getPosition().inTL(-1000f, -1000f);
            }
        }
        
        if (startBattlePanel != null) {
            if (currentRound == 0 && !showingBetAmounts && totalBet > 0) {
                startBattlePanel.getPosition().inTL(leftX + BUTTON_WIDTH + BUTTON_SPACING, bottomY);
            } else {
                startBattlePanel.getPosition().inTL(-1000f, -1000f);
            }
        }
    }
    
    protected void createRoundLabel() {
        if (panel == null) return;
        
        final float LABEL_WIDTH = 150f;
        final float LABEL_HEIGHT = 30f;
        
        float x = SHIP_COLUMN_WIDTH + MARGIN;
        float y = MARGIN;
        
        roundPanel = panel.createCustomPanel(LABEL_WIDTH, LABEL_HEIGHT, null);
        TooltipMakerAPI tooltip = roundPanel.createUIElement(LABEL_WIDTH, LABEL_HEIGHT, false);
        roundLabel = tooltip.addPara("Round: 0", Color.CYAN, 0f);
        roundLabel.setAlignment(Alignment.LMID);
        roundLabel.getPosition().inTL(0, 0);
        roundPanel.addUIElement(tooltip).inTL(0, 0);
        panel.addComponent(roundPanel).inTL(x, y);
    }
    
    protected void createBetLabel() {
        if (panel == null) return;
        
        final float LABEL_WIDTH = 200f;
        final float LABEL_HEIGHT = 30f;
        
        float x = SHIP_COLUMN_WIDTH + MARGIN + 160f;
        float y = MARGIN;
        
        betPanel = panel.createCustomPanel(LABEL_WIDTH, LABEL_HEIGHT, null);
        TooltipMakerAPI tooltip = betPanel.createUIElement(LABEL_WIDTH, LABEL_HEIGHT, false);
        betLabel = tooltip.addPara("Total Bet: 0", Color.YELLOW, 0f);
        betLabel.setAlignment(Alignment.LMID);
        betLabel.getPosition().inTL(0, 0);
        betPanel.addUIElement(tooltip).inTL(0, 0);
        panel.addComponent(betPanel).inTL(x, y);
    }
    
    protected void createInstructionLabel() {
        if (panel == null) return;
        
        final float LABEL_WIDTH = 250f;
        final float LABEL_HEIGHT = 30f;
        
        float x = SHIP_COLUMN_WIDTH + MARGIN + 360f;
        float y = MARGIN;
        
        instructionPanel = panel.createCustomPanel(LABEL_WIDTH, LABEL_HEIGHT, null);
        TooltipMakerAPI tooltip = instructionPanel.createUIElement(LABEL_WIDTH, LABEL_HEIGHT, false);
        instructionLabel = tooltip.addPara("Select a champion to bet on:", Color.WHITE, 0f);
        instructionLabel.setAlignment(Alignment.LMID);
        instructionLabel.getPosition().inTL(0, 0);
        instructionPanel.addUIElement(tooltip).inTL(0, 0);
        panel.addComponent(instructionPanel).inTL(x, y);
    }
    
    protected void createResultLabel() {
        if (panel == null) return;
        
        final float RESULT_WIDTH = SHIP_COLUMN_WIDTH - MARGIN * 2;
        final float RESULT_HEIGHT = 50f;
        
        float x = MARGIN;
        float y = MARGIN + 40f;
        
        resultPanel = panel.createCustomPanel(RESULT_WIDTH, RESULT_HEIGHT, null);
        TooltipMakerAPI tooltip = resultPanel.createUIElement(RESULT_WIDTH, RESULT_HEIGHT, false);
        resultLabel = tooltip.addPara("", Color.YELLOW, 0f);
        resultLabel.setAlignment(Alignment.MID);
        resultLabel.getPosition().inMid();
        resultPanel.addUIElement(tooltip).inTL(0, 0);
        panel.addComponent(resultPanel).inTL(x, y);
    }
    
    protected void createRewardBreakdownLabels() {
        if (panel == null) return;
        
        // Move reward breakdown to right column (where battle log was)
        float breakdownX = SHIP_COLUMN_WIDTH + CENTER_COLUMN_WIDTH + MARGIN;
        float breakdownY = MARGIN + 40f;
        float breakdownW = BATTLE_LOG_WIDTH - MARGIN;
        float lineHeight = 14f;
        float spacing = 1f;
        
        for (int i = 0; i < MAX_REWARD_LINES; i++) {
            float y = breakdownY + i * (lineHeight + spacing);
            
            rewardBreakdownPanels[i] = panel.createCustomPanel(breakdownW, lineHeight, null);
            TooltipMakerAPI tooltip = rewardBreakdownPanels[i].createUIElement(breakdownW, lineHeight, false);
            rewardBreakdownLabels[i] = tooltip.addPara("", Color.WHITE, 0f);
            rewardBreakdownLabels[i].setAlignment(Alignment.LMID);
            rewardBreakdownLabels[i].getPosition().inTL(0, 0);
            rewardBreakdownPanels[i].addUIElement(tooltip).inTL(0, 0);
            panel.addComponent(rewardBreakdownPanels[i]).inTL(breakdownX, y);
        }
    }
    
    protected void createShipLabels() {
        if (panel == null || combatants == null) return;
        
        final float NAME_WIDTH = (SHIP_COLUMN_WIDTH - MARGIN * 2) * 2;
        final float NAME_HEIGHT = 16f;
        final float HP_WIDTH = SHIP_COLUMN_WIDTH - MARGIN * 2 - 5f;
        final float HP_HEIGHT = 11f;
        final float ODDS_WIDTH = (SHIP_COLUMN_WIDTH - MARGIN * 2) * 2;
        final float ODDS_HEIGHT = 13f;
        
        float startY = MARGIN + 10f;
        float totalItemHeight = BOX_HEIGHT + BOX_SPACING + NAME_HEIGHT + HP_HEIGHT + ODDS_HEIGHT + ENTRY_SPACING;
        
        for (int i = 0; i < combatants.size() && i < 5; i++) {
            SpiralAbyssArena.SpiralGladiator ship = combatants.get(i);
            float shipY = startY + i * totalItemHeight;
            float labelY = shipY + BOX_HEIGHT + BOX_SPACING;
            
            shipNamePanels[i] = panel.createCustomPanel(NAME_WIDTH, NAME_HEIGHT, null);
            TooltipMakerAPI nameTooltip = shipNamePanels[i].createUIElement(NAME_WIDTH, NAME_HEIGHT, false);
            String fullName = ship.prefix + " " + ship.hullName + " " + ship.affix;
            shipNameLabels[i] = nameTooltip.addPara(fullName, Color.WHITE, 0f);
            shipNameLabels[i].setAlignment(Alignment.LMID);
            shipNameLabels[i].getPosition().inTL(0, 0);
            shipNamePanels[i].addUIElement(nameTooltip).inTL(0, 0);
            panel.addComponent(shipNamePanels[i]).inTL(MARGIN, shipY + BOX_HEIGHT + 2f);
            applyShipNameHighlighting(shipNameLabels[i], ship);
            
            float hpY = labelY + NAME_HEIGHT;
            shipHpPanels[i] = panel.createCustomPanel(HP_WIDTH, HP_HEIGHT, null);
            TooltipMakerAPI hpTooltip = shipHpPanels[i].createUIElement(HP_WIDTH, HP_HEIGHT, false);
            shipHpLabels[i] = hpTooltip.addPara(ship.hp + "/" + ship.maxHp + " HP", COLOR_HEALTHY, 0f);
            shipHpLabels[i].setAlignment(Alignment.LMID);
            shipHpLabels[i].getPosition().inTL(0, 0);
            shipHpPanels[i].addUIElement(hpTooltip).inTL(0, 0);
            panel.addComponent(shipHpPanels[i]).inTL(MARGIN + 5f, shipY + BOX_HEIGHT + NAME_HEIGHT + 4f);
            
            float oddsY = hpY + HP_HEIGHT;
            float displayOdds = oddsCached && i < cachedOdds.length ? cachedOdds[i] : ship.getCurrentOdds(currentRound);
            shipOddsPanels[i] = panel.createCustomPanel(ODDS_WIDTH, ODDS_HEIGHT, null);
            TooltipMakerAPI oddsTooltip = shipOddsPanels[i].createUIElement(ODDS_WIDTH, ODDS_HEIGHT, false);
            shipOddsLabels[i] = oddsTooltip.addPara(String.format("Odds: %.2fx", displayOdds), Color.YELLOW, 0f);
            shipOddsLabels[i].setAlignment(Alignment.LMID);
            shipOddsLabels[i].getPosition().inTL(0, 0);
            shipOddsPanels[i].addUIElement(oddsTooltip).inTL(0, 0);
            panel.addComponent(shipOddsPanels[i]).inTL(MARGIN + 5f, shipY + BOX_HEIGHT + NAME_HEIGHT + HP_HEIGHT + 6f);
        }
        
        buttonsCreated = true;
    }
    
    public void renderBelow(float alphaMult) {
        float x, y, w, h;
        
        if (p != null) {
            x = p.getX();
            y = p.getY();
            w = p.getWidth();
            h = p.getHeight();
        } else {
            x = 0;
            y = 0;
            w = PANEL_WIDTH;
            h = PANEL_HEIGHT;
        }
        
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        float s = Global.getSettings().getScreenScaleMult();
        GL11.glScissor((int)(x * s), (int)(y * s), (int)(w * s), (int)(h * s));
        
        Misc.renderQuad(x, y, w, h, COLOR_BG_DARK, alphaMult);
        
        Misc.renderQuad(x, y, SHIP_COLUMN_WIDTH, h, COLOR_SIDEBAR, alphaMult);
        
        if (combatants != null) {
            renderShipBoxes(x, y, w, h, alphaMult);
        }
        
        renderBattleLogSprites(x, y, w, h, alphaMult);
        
        updateLabels();
        updateButtonVisibility();
        
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
    
    protected void renderShipBoxes(float panelX, float panelY, float panelW, float panelH, float alphaMult) {
        if (combatants == null) return;
        
        float startY = MARGIN + 10f;
        float totalItemHeight = BOX_HEIGHT + BOX_SPACING + 16f + 11f + 13f + ENTRY_SPACING;
        
        for (int i = 0; i < combatants.size(); i++) {
            SpiralAbyssArena.SpiralGladiator ship = combatants.get(i);
            float shipY = startY + i * totalItemHeight;
            float shipX = MARGIN;
            
            float screenY = panelY + panelH - shipY - BOX_HEIGHT;
            
            Color boxColor;
            float opacity;
            
            if (ship.isDead) {
                boxColor = COLOR_DESTROYED;
                opacity = 0.5f;
            } else if (ship.hp < ship.maxHp) {
                boxColor = COLOR_DAMAGED;
                opacity = 0.7f + 0.3f * ((float) ship.hp / ship.maxHp);
            } else {
                boxColor = COLOR_HEALTHY;
                opacity = 1.0f;
            }
            
            if (i == selectedChampionIndex) {
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glColor4f(COLOR_SELECTED.getRed() / 255f, COLOR_SELECTED.getGreen() / 255f, 
                    COLOR_SELECTED.getBlue() / 255f, alphaMult * 0.8f);
                GL11.glBegin(GL11.GL_LINE_LOOP);
                GL11.glVertex2f(panelX + shipX - 3, screenY - 3);
                GL11.glVertex2f(panelX + shipX + BOX_WIDTH + 3, screenY - 3);
                GL11.glVertex2f(panelX + shipX + BOX_WIDTH + 3, screenY + BOX_HEIGHT + 3);
                GL11.glVertex2f(panelX + shipX - 3, screenY + BOX_HEIGHT + 3);
                GL11.glEnd();
            }
            
            Misc.renderQuad(panelX + shipX, screenY, BOX_WIDTH, BOX_HEIGHT, boxColor, alphaMult * opacity * 0.3f);
            
            SpriteAPI sprite = getShipSprite(ship.hullId);
            if (sprite != null) {
                float spriteWidth = sprite.getWidth();
                float spriteHeight = sprite.getHeight();
                float maxDim = Math.max(spriteWidth, spriteHeight);
                float scale = (BOX_HEIGHT * SPRITE_SCALE) / maxDim;
                
                float scaledWidth = spriteWidth * scale;
                float scaledHeight = spriteHeight * scale;
                
                float centerX = panelX + shipX + BOX_WIDTH / 2f;
                float centerY = screenY + BOX_HEIGHT / 2f;
                
                sprite.setSize(scaledWidth, scaledHeight);
                
                Color tint;
                float spriteAlpha;
                
                if (ship.isDead) {
                    tint = COLOR_TINT_DEAD;
                    spriteAlpha = 0.4f * alphaMult;
                } else if (ship.hp < ship.maxHp * 0.5f) {
                    tint = COLOR_TINT_DAMAGED;
                    spriteAlpha = 0.8f * alphaMult;
                } else {
                    tint = Color.WHITE;
                    spriteAlpha = alphaMult;
                }
                
                sprite.setColor(tint);
                sprite.setAlphaMult(spriteAlpha);
                sprite.setNormalBlend();
                sprite.renderAtCenter(centerX, centerY);
            }
            
            if (ship.isDead) {
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glColor4f(1f, 0f, 0f, alphaMult * 0.8f);
                GL11.glLineWidth(3f);
                GL11.glBegin(GL11.GL_LINES);
                GL11.glVertex2f(panelX + shipX + 10, screenY + BOX_HEIGHT - 10);
                GL11.glVertex2f(panelX + shipX + BOX_WIDTH - 10, screenY + 10);
                GL11.glVertex2f(panelX + shipX + BOX_WIDTH - 10, screenY + BOX_HEIGHT - 10);
                GL11.glVertex2f(panelX + shipX + 10, screenY + 10);
                GL11.glEnd();
                GL11.glLineWidth(1f);
            }
            
            float hpBarHeight = 6f;
            float hpPercent = (float) ship.hp / ship.maxHp;
            Color hpColor = hpPercent > 0.5f ? COLOR_HEALTHY : (hpPercent > 0.25f ? COLOR_DAMAGED : COLOR_DESTROYED);
            
            float hpBarY = screenY + BOX_HEIGHT - hpBarHeight - 2;
            Misc.renderQuad(panelX + shipX + 2, hpBarY, BOX_WIDTH - 4, hpBarHeight, 
                COLOR_HP_BAR_BG, alphaMult * 0.8f);
            if (hpPercent > 0) {
                Misc.renderQuad(panelX + shipX + 2, hpBarY, (BOX_WIDTH - 4) * hpPercent, hpBarHeight, 
                    hpColor, alphaMult);
            }
        }
    }
    
    protected void updateLabels() {
        if (roundLabel != null && currentRound != lastCurrentRound) {
            lastCurrentRound = currentRound;
            roundLabel.setText("Round: " + currentRound);
        }
        
        if (betLabel != null && totalBet != lastTotalBet) {
            lastTotalBet = totalBet;
            betLabel.setText("Total Bet: " + totalBet);
        }
        
        if (instructionLabel != null) {
            String newInstructionText;
            boolean hideInstruction = false;
            
            if (battleEnded) {
                newInstructionText = "Click Return to Lobby";
            } else if (currentRound > 0 && addingBetDuringBattle && selectedChampionIndex < 0) {
                newInstructionText = "Select a champion to add bet on:";
            } else if (currentRound > 0 && addingBetDuringBattle && selectedChampionIndex >= 0) {
                SpiralAbyssArena.SpiralGladiator selected = combatants.get(selectedChampionIndex);
                newInstructionText = "Adding bet on: " + selected.hullName + " - Select amount:";
            } else if (currentRound > 0) {
                newInstructionText = null;
                hideInstruction = true;
            } else if (showingBetAmounts && selectedChampionIndex >= 0) {
                SpiralAbyssArena.SpiralGladiator selected = combatants.get(selectedChampionIndex);
                newInstructionText = "Betting on: " + selected.hullName + " - Select amount:";
            } else {
                newInstructionText = "Select a champion to bet on:";
            }
            
            if (hideInstruction) {
                instructionLabel.setText("");
            } else if (newInstructionText != null) {
                instructionLabel.setText(newInstructionText);
            }
        }
        
        if (combatants != null) {
            for (int i = 0; i < combatants.size() && i < 5; i++) {
                SpiralAbyssArena.SpiralGladiator ship = combatants.get(i);
                
                boolean hpChanged = !shipStateInitialized || 
                    lastShipHp[i] != ship.hp || 
                    lastShipMaxHp[i] != ship.maxHp ||
                    lastShipDead[i] != ship.isDead;
                
                if (hpChanged) {
                    lastShipHp[i] = ship.hp;
                    lastShipMaxHp[i] = ship.maxHp;
                    lastShipDead[i] = ship.isDead;
                    
                    if (shipHpLabels[i] != null) {
                        shipHpLabels[i].setText(ship.hp + "/" + ship.maxHp + " HP");
                        Color hpColor = ship.isDead ? COLOR_DESTROYED : 
                            ((float) ship.hp / ship.maxHp > 0.5f ? COLOR_HEALTHY : COLOR_DAMAGED);
                        shipHpLabels[i].setColor(hpColor);
                    }
                }
                
                float displayOdds = oddsCached && i < cachedOdds.length ? cachedOdds[i] : ship.getCurrentOdds(currentRound);
                
                int currentBetCount = 0;
                if (bets != null) {
                    for (BetInfo b : bets) {
                        if (b.ship == ship) currentBetCount++;
                    }
                }
                
                boolean oddsChanged = !shipStateInitialized || lastShipOdds[i] != displayOdds || lastShipBetCount[i] != currentBetCount;
                if (oddsChanged && shipOddsLabels[i] != null) {
                    lastShipOdds[i] = displayOdds;
                    lastShipBetCount[i] = currentBetCount;
                    
                    String oddsText = String.format("Odds: %.2fx", displayOdds);
                    
                    if (bets != null && !bets.isEmpty()) {
                        List<String> betStrings = new ArrayList<>();
                        for (BetInfo b : bets) {
                            if (b.ship == ship) {
                                betStrings.add(String.format("[%d, %.2fx]", b.amount, b.multiplier));
                            }
                        }
                        if (!betStrings.isEmpty()) {
                            oddsText += " " + String.join("", betStrings);
                        }
                    }
                    
                    shipOddsLabels[i].setText(oddsText);
                    shipOddsLabels[i].setColor(ship.isDead ? Color.GRAY : Color.YELLOW);
                }
            }
            shipStateInitialized = true;
        }
        
        if (resultLabel != null) {
            if (!battleEnded) {
                resultLabel.setText("");
            } else if (rewardBreakdown != null) {
                // Don't show "Battle Complete!" - keep empty
                resultLabel.setText("");
            } else {
                if (winnerIndex >= 0 && winnerIndex < combatants.size()) {
                    SpiralAbyssArena.SpiralGladiator winner = combatants.get(winnerIndex);
                    resultLabel.setText("WINNER: " + winner.hullName + "!\nReward: " + totalReward + " Stargems");
                    resultLabel.setColor(Color.GREEN);
                } else {
                    // Don't show "Battle Complete!" - keep empty
                    resultLabel.setText("");
                }
            }
        }
        
        updateRewardBreakdownLabels();
    }
    
    protected void updateRewardBreakdownLabels() {
        for (int i = 0; i < MAX_REWARD_LINES; i++) {
            if (rewardBreakdownLabels[i] != null) {
                rewardBreakdownLabels[i].setText("");
            }
        }
        
        if (!battleEnded || rewardBreakdown == null) return;
        
        if (rewardBreakdownLabels[0] == null) return;
        
        Color headerColor = new Color(100, 200, 255);
        Color winColor = new Color(50, 255, 50);
        Color consolationColor = new Color(255, 200, 50);
        Color netPositiveColor = new Color(50, 255, 100);
        Color netNegativeColor = new Color(255, 100, 50);
        Color killColor = new Color(255, 150, 50);
        
        int lineIndex = 0;
        
        if (winnerIndex >= 0 && winnerIndex < combatants.size() && lineIndex < MAX_REWARD_LINES) {
            SpiralAbyssArena.SpiralGladiator winner = combatants.get(winnerIndex);
            rewardBreakdownLabels[lineIndex].setText("WINNER: " + winner.hullName + "!");
            rewardBreakdownLabels[lineIndex].setColor(winColor);
            lineIndex++;
        }
        
        if (lineIndex < MAX_REWARD_LINES) {
            rewardBreakdownLabels[lineIndex].setText("--- Reward Breakdown ---");
            rewardBreakdownLabels[lineIndex].setColor(headerColor);
            lineIndex++;
        }
        
        if (lineIndex < MAX_REWARD_LINES) {
            rewardBreakdownLabels[lineIndex].setText("Total Bet: " + rewardBreakdown.totalBet + " SG");
            rewardBreakdownLabels[lineIndex].setColor(Color.WHITE);
            lineIndex++;
        }
        
        if (rewardBreakdown.winReward > 0 && lineIndex < MAX_REWARD_LINES) {
            rewardBreakdownLabels[lineIndex].setText("Win Reward: +" + rewardBreakdown.winReward + " SG");
            rewardBreakdownLabels[lineIndex].setColor(winColor);
            lineIndex++;
        }
        
        if (rewardBreakdown.consolationReward > 0 && lineIndex < MAX_REWARD_LINES) {
            rewardBreakdownLabels[lineIndex].setText("Consolation: +" + rewardBreakdown.consolationReward + " SG");
            rewardBreakdownLabels[lineIndex].setColor(consolationColor);
            lineIndex++;
        }
        
        int totalKills = 0;
        for (RewardBreakdown.ShipRewardInfo shipInfo : rewardBreakdown.shipRewards) {
            totalKills += shipInfo.kills;
        }
        
        if (totalKills > 0 && lineIndex < MAX_REWARD_LINES) {
            float killBonusPct = totalKills * CasinoConfig.ARENA_KILL_BONUS_PER_KILL * 100;
            rewardBreakdownLabels[lineIndex].setText("Kill Bonus: " + totalKills + " kills (+" + (int)killBonusPct + "%)");
            rewardBreakdownLabels[lineIndex].setColor(killColor);
            lineIndex++;
        }
        
        for (RewardBreakdown.ShipRewardInfo shipInfo : rewardBreakdown.shipRewards) {
            if (shipInfo.betAmount <= 0) continue;
            if (lineIndex >= MAX_REWARD_LINES) break;
            
            String statusStr = shipInfo.isWinner ? "SURVIVOR" : "DEFEATED";
            Color statusColor = shipInfo.isWinner ? winColor : Color.RED;
            String posStr = getPositionString(shipInfo.finalPosition);
            float killBonusPct = shipInfo.kills * CasinoConfig.ARENA_KILL_BONUS_PER_KILL * 100;
            
            String shipLine = String.format("%s: %d SG | %s | %d kills (+%.0f%%)", 
                shipInfo.shipName, shipInfo.betAmount, posStr, shipInfo.kills, killBonusPct);
            rewardBreakdownLabels[lineIndex].setText(shipLine);
            rewardBreakdownLabels[lineIndex].setColor(statusColor);
            lineIndex++;
            
            if (shipInfo.reward > 0 && lineIndex < MAX_REWARD_LINES) {
                String rewardLine = String.format("  -> Reward: +%d SG", shipInfo.reward);
                Color rewardColor = shipInfo.isWinner ? winColor : consolationColor;
                rewardBreakdownLabels[lineIndex].setText(rewardLine);
                rewardBreakdownLabels[lineIndex].setColor(rewardColor);
                lineIndex++;
            }
        }
        
        if (lineIndex < MAX_REWARD_LINES) {
            int net = rewardBreakdown.netResult;
            Color netColor = net >= 0 ? netPositiveColor : netNegativeColor;
            String netStr = net >= 0 ? "+" + net : String.valueOf(net);
            rewardBreakdownLabels[lineIndex].setText("NET: " + netStr + " SG");
            rewardBreakdownLabels[lineIndex].setColor(netColor);
        }
    }
    
    protected void updateHullIdDeadStatus() {
        hullIdDeadStatus.clear();
        if (combatants != null) {
            for (SpiralAbyssArena.SpiralGladiator g : combatants) {
                hullIdDeadStatus.put(g.hullId, g.isDead);
            }
        }
    }
    
    protected void updateCachedParsedEntries() {
        int currentSize = battleLog != null ? battleLog.size() : 0;
        if (currentSize == lastBattleLogSize) {
            return;
        }
        lastBattleLogSize = currentSize;
        
        cachedParsedEntries.clear();
        if (battleLog != null) {
            for (String entry : battleLog) {
                ParsedLogEntry parsed = new ParsedLogEntry(entry);
                parsed.parse(combatants);
                cachedParsedEntries.add(parsed);
            }
        }
    }
    
    protected List<ParsedLogEntry> getFilteredEntries() {
        updateHullIdDeadStatus();
        updateCachedParsedEntries();
        
        List<ParsedLogEntry> filtered = new ArrayList<>();
        for (ParsedLogEntry entry : cachedParsedEntries) {
            // Skip entries with dead attackers (they can't attack if they're dead)
            if (entry.shouldSkipDeadAttacker(hullIdDeadStatus)) {
                continue;
            }
            // Skip ROUND, STATUS, and empty entries - these are obsolete with sprite-based log
            if (entry.type.equals("ROUND") || entry.type.equals("STATUS") || entry.type.isEmpty()) {
                continue;
            }
            filtered.add(entry);
        }
        return filtered;
    }
    
    protected void updateBattleLog() {
        // Text-based battle log removed - using sprite-based display instead
        // Keeping method stub for potential future use
    }
    
    protected String getShipShortNameByHullId(String hullId) {
        if (combatants == null || hullId == null) return "???";
        for (SpiralAbyssArena.SpiralGladiator g : combatants) {
            if (hullId.equals(g.hullId)) {
                return g.shortName;
            }
        }
        return "???";
    }
    
    protected void renderBattleLogSprites(float panelX, float panelY, float panelW, float panelH, float alphaMult) {
        if (battleLog == null || battleLog.isEmpty()) return;

        List<ParsedLogEntry> validEntries = getFilteredEntries();

        int maxLines = 12;
        int start = Math.max(0, validEntries.size() - maxLines);

        // Updated to match new battle log position in center column
        float logPanelX = SHIP_COLUMN_WIDTH + MARGIN;
        float logPanelY = MARGIN + 40f;
        float logPanelW = CENTER_COLUMN_WIDTH - MARGIN;
        float logPanelH = PANEL_HEIGHT - MARGIN * 2 - 80f;

        float currentY = logY;
        float rowSpacing = 4f;

        int lineIndex = 0;
        
        float leftSpriteX = panelX + logPanelX + LOG_LEFT_MARGIN + LOG_SPRITE_SIZE / 2f;
        float rightSpriteX = panelX + logPanelX + logPanelW - LOG_LEFT_MARGIN - LOG_SPRITE_SIZE / 2f;
        
        float textWidthTwoSprites = logPanelW - LOG_LEFT_MARGIN * 2 - LOG_SPRITE_SIZE * 2 - LOG_SPRITE_GAP * 2;
        float textWidthOneSprite = logPanelW - LOG_LEFT_MARGIN * 2 - LOG_SPRITE_SIZE - LOG_SPRITE_GAP;
        
        float textStartX_twoSprites = logPanelX + LOG_LEFT_MARGIN + LOG_SPRITE_SIZE + LOG_SPRITE_GAP;
        float textStartX_oneSprite = logPanelX + LOG_LEFT_MARGIN;

        for (int i = start; i < validEntries.size(); i++) {
            ParsedLogEntry entry = validEntries.get(i);

            float screenY = panelY + logPanelY + logPanelH - currentY - LOG_LINE_HEIGHT;
            float spriteCenterY = screenY + LOG_LINE_HEIGHT / 2f;

            Boolean targetDeadObj = hullIdDeadStatus.get(entry.targetHullId);
            boolean targetDead = targetDeadObj != null && targetDeadObj;
            
            Boolean attackerDeadObj = hullIdDeadStatus.get(entry.attackerHullId);
            boolean attackerDead = attackerDeadObj != null && attackerDeadObj;

            String labelText = "";
            Color labelColor = Color.WHITE;
            boolean hasTwoSprites = true;
            float textX = textStartX_twoSprites;

            if (entry.type.equals("HIT")) {
                labelText = entry.rawEntry;
                labelColor = entry.isCrit ? new Color(255, 100, 100) : new Color(255, 255, 100);
                
                drawBattleLogSpriteWithDead(entry.attackerHullId, leftSpriteX, spriteCenterY, LOG_SPRITE_SIZE, alphaMult, false);
                drawBattleLogSpriteWithDead(entry.targetHullId, rightSpriteX, spriteCenterY, LOG_SPRITE_SIZE, alphaMult, targetDead);
                
            } else if (entry.type.equals("MISS")) {
                labelText = entry.rawEntry;
                labelColor = new Color(150, 150, 150);
                
                drawBattleLogSpriteWithDead(entry.targetHullId, leftSpriteX, spriteCenterY, LOG_SPRITE_SIZE, alphaMult, false);
                drawBattleLogSpriteWithDead(entry.attackerHullId, rightSpriteX, spriteCenterY, LOG_SPRITE_SIZE, alphaMult, false);
                
            } else if (entry.type.equals("KILL")) {
                String killText = entry.rawEntry;
                if (killText.startsWith("[KILL] ")) {
                    killText = killText.substring(7);
                }
                labelText = killText;
                labelColor = new Color(255, 50, 50);
                
                drawBattleLogSpriteWithDead(entry.attackerHullId, leftSpriteX, spriteCenterY, LOG_SPRITE_SIZE, alphaMult, false);
                drawBattleLogSpriteWithDead(entry.targetHullId, rightSpriteX, spriteCenterY, LOG_SPRITE_SIZE, alphaMult, true);
                
            } else if (entry.type.equals("EVENT")) {
                hasTwoSprites = false;
                String eventText = entry.rawEntry;
                if (eventText.startsWith("[EVENT] ")) {
                }
                labelText = eventText;
                labelColor = new Color(100, 200, 255);
                textX = textStartX_oneSprite;
                
                drawBattleLogSpriteWithDead(entry.attackerHullId, rightSpriteX, spriteCenterY, LOG_SPRITE_SIZE, alphaMult, false);
                
            } else if (entry.type.equals("EVENT_HIT")) {
                hasTwoSprites = false;
                String hitText = entry.rawEntry;
                if (hitText.startsWith("[HIT] ")) {
                    hitText = hitText.substring(6);
                }
                labelText = hitText;
                labelColor = new Color(255, 200, 50);
                textX = textStartX_oneSprite;
                
                drawBattleLogSpriteWithDead(entry.attackerHullId, rightSpriteX, spriteCenterY, LOG_SPRITE_SIZE, alphaMult, false);
            }

            if (battleLogTextLabels[lineIndex] != null && !labelText.isEmpty()) {
                battleLogTextLabels[lineIndex].setText(labelText);
                battleLogTextLabels[lineIndex].setColor(labelColor);
                
                float textY = logPanelY + currentY + (LOG_LINE_HEIGHT - 14f) / 2f;
                battleLogTextPanels[lineIndex].getPosition().inTL(textX, textY);
            }

            currentY += LOG_LINE_HEIGHT + rowSpacing;
            lineIndex++;
        }

        for (int j = lineIndex; j < 12; j++) {
            battleLogTextPanels[j].getPosition().inTL(-1000f, -1000f);
            if (battleLogTextLabels[j] != null) {
                battleLogTextLabels[j].setText("");
            }
        }
    }
    
    protected void drawBattleLogSpriteWithDead(String hullId, float cx, float cy, float maxSize, float alphaMult, boolean dead) {
        SpriteAPI sprite = getShipSprite(hullId);
        if (sprite != null) {
            float spriteWidth = sprite.getWidth();
            float spriteHeight = sprite.getHeight();
            float maxDim = Math.max(spriteWidth, spriteHeight);
            float scale = maxSize / maxDim;
            float scaledWidth = spriteWidth * scale;
            float scaledHeight = spriteHeight * scale;
            
            sprite.setSize(scaledWidth, scaledHeight);
            
            if (dead) {
                sprite.setColor(new Color(100, 30, 30));
                sprite.setAlphaMult(alphaMult * 0.5f);
            } else {
                sprite.setColor(Color.WHITE);
                sprite.setAlphaMult(alphaMult);
            }
            sprite.setNormalBlend();
            sprite.renderAtCenter(cx, cy);
            
            if (dead) {
                float halfSize = maxSize / 2f - 4f;
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glColor4f(1f, 0f, 0f, alphaMult * 0.8f);
                GL11.glLineWidth(2f);
                GL11.glBegin(GL11.GL_LINES);
                GL11.glVertex2f(cx - halfSize, cy - halfSize);
                GL11.glVertex2f(cx + halfSize, cy + halfSize);
                GL11.glVertex2f(cx + halfSize, cy - halfSize);
                GL11.glVertex2f(cx - halfSize, cy + halfSize);
                GL11.glEnd();
                GL11.glLineWidth(1f);
            }
        }
    }
    
    protected boolean isHullIdDead(String hullId) {
        if (combatants == null || hullId == null) return false;
        for (SpiralAbyssArena.SpiralGladiator g : combatants) {
            if (hullId.equals(g.hullId)) {
                return g.isDead;
            }
        }
        return false;
    }
    
    public void render(float alphaMult) {
    }
    
    public void advance(float amount) {
    }
    
    public void processInput(List<InputEventAPI> events) {
        for (InputEventAPI event : events) {
            if (event.isConsumed()) continue;
            
            if (event.isKeyDownEvent()) {
                int key = event.getEventValue();
                
                if (key == Keyboard.KEY_ESCAPE) {
                    event.consume();
                    if (showingBetAmounts) {
                        showingBetAmounts = false;
                        selectedChampionIndex = -1;
                    } else if (callbacks != null) {
                        callbacks.dismissDialog();
                    }
                    return;
                }
            }
        }
        
        checkButtonClicks();
    }
    
    protected void checkButtonClicks() {
        if (leaveButton != null && leaveButton.isChecked()) {
            leaveButton.setChecked(false);
            if (actionCallback != null) {
                actionCallback.onLeave();
            }
            return;
        }
        
        if (currentRound > 0 && addingBetDuringBattle && showingBetAmounts) {
            for (ButtonAPI btn : championSelectButtons) {
                if (btn.isChecked()) {
                    btn.setChecked(false);
                    Integer idx = (Integer) btn.getCustomData();
                    if (idx != null) {
                        if (idx == -1) {
                            addingBetDuringBattle = false;
                            showingBetAmounts = false;
                            selectedChampionIndex = -1;
                        } else {
                            selectedChampionIndex = idx;
                            showingBetAmounts = true;
                        }
                    }
                    return;
                }
            }
        }
        
        if (!showingBetAmounts && selectedChampionIndex < 0 && currentRound == 0) {
            for (ButtonAPI btn : championSelectButtons) {
                if (btn.isChecked()) {
                    btn.setChecked(false);
                    Integer idx = (Integer) btn.getCustomData();
                    if (idx != null) {
                        selectedChampionIndex = idx;
                        showingBetAmounts = true;
                        if (actionCallback != null) {
                            actionCallback.onSelectChampion(idx);
                        }
                    }
                    return;
                }
            }
        }
        
        if (showingBetAmounts && selectedChampionIndex >= 0) {
            for (ButtonAPI btn : betAmountButtons) {
                if (btn.isChecked()) {
                    btn.setChecked(false);
                    Integer amount = (Integer) btn.getCustomData();
                    if (amount != null) {
                        if (amount == -1) {
                            showingBetAmounts = false;
                            selectedChampionIndex = -1;
                            addingBetDuringBattle = false;
                        } else if (selectedChampionIndex >= 0 && actionCallback != null) {
                            actionCallback.onConfirmBet(selectedChampionIndex, amount);
                            showingBetAmounts = false;
                            selectedChampionIndex = -1;
                        }
                    }
                    return;
                }
            }
        }
        
        if (watchNextButton != null && watchNextButton.isChecked()) {
            watchNextButton.setChecked(false);
            if (actionCallback != null) {
                actionCallback.onWatchNextRound();
            }
            return;
        }
        
        if (skipToEndButton != null && skipToEndButton.isChecked()) {
            skipToEndButton.setChecked(false);
            if (actionCallback != null) {
                actionCallback.onSkipToEnd();
            }
            return;
        }
        
        if (addBetButton != null && addBetButton.isChecked()) {
            addBetButton.setChecked(false);
            addingBetDuringBattle = true;
            showingBetAmounts = true;
            selectedChampionIndex = -1;
            return;
        }
        
        if (suspendButton != null && suspendButton.isChecked()) {
            suspendButton.setChecked(false);
            if (actionCallback != null) {
                actionCallback.onSuspend();
            }
            return;
        }
        
        if (returnToLobbyButton != null && returnToLobbyButton.isChecked()) {
            returnToLobbyButton.setChecked(false);
            readyToClose = true;
            if (actionCallback != null) {
                actionCallback.onReturnToLobby();
            }
            return;
        }
        
        if (startBattleButton != null && startBattleButton.isChecked()) {
            startBattleButton.setChecked(false);
            if (actionCallback != null) {
                actionCallback.onStartBattle();
            }
            return;
        }
    }
    
    protected void cacheOdds() {
        if (combatants == null) return;
        
        for (int i = 0; i < combatants.size() && i < 5; i++) {
            SpiralAbyssArena.SpiralGladiator ship = combatants.get(i);
            cachedOdds[i] = ship.getCurrentOdds(currentRound);
        }
        oddsCached = true;
    }
    
    public void updateState(
            List<SpiralAbyssArena.SpiralGladiator> combatants,
            int currentRound,
            int totalBet,
            List<BetInfo> bets,
            List<String> battleLog) {
        
        this.combatants = combatants;
        this.currentRound = currentRound;
        this.totalBet = totalBet;
        this.bets = bets;
        this.battleLog = battleLog;
        
        oddsCached = false;
        cacheOdds();
        
        if (lastCurrentRound == 0 && currentRound > 0) {
            showingBetAmounts = false;
            selectedChampionIndex = -1;
            addingBetDuringBattle = false;
        }
        lastCurrentRound = currentRound;
        
        updateLabels();
    }
    
    public void setBattleEnded(int winnerIndex, int totalReward) {
        this.battleEnded = true;
        this.winnerIndex = winnerIndex;
        this.totalReward = totalReward;
        this.showingBetAmounts = false;
        this.addingBetDuringBattle = false;
        this.selectedChampionIndex = -1;
        
        oddsCached = false;
        cacheOdds();
        
        updateLabels();
    }
    
    public void setBattleEnded(int winnerIndex, int totalReward, RewardBreakdown breakdown) {
        this.battleEnded = true;
        this.winnerIndex = winnerIndex;
        this.totalReward = totalReward;
        this.rewardBreakdown = breakdown;
        this.showingBetAmounts = false;
        this.addingBetDuringBattle = false;
        this.selectedChampionIndex = -1;
        
        oddsCached = false;
        cacheOdds();
        
        updateLabels();
    }
    
    @Deprecated
    protected void createRewardBreakdownDisplay() {
    }
    
    protected String getPositionString(int finalPosition) {
        if (finalPosition == 0) return "1st";
        if (finalPosition == 1) return "2nd";
        if (finalPosition == 2) return "3rd";
        if (finalPosition == 3) return "4th";
        if (finalPosition == 4) return "5th";
        return (finalPosition + 1) + "th";
    }
    
    public boolean isReadyToClose() {
        return readyToClose;
    }
    
    }
