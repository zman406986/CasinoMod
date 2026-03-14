package data.scripts.casino;

import java.awt.Color;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate.DialogCallbacks;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.ActionListenerDelegate;
import com.fs.starfarer.api.util.Misc;

import data.scripts.casino.SpiralAbyssArena.SpiralGladiator;
import data.scripts.casino.interaction.ArenaHandler.BetInfo;

public class ArenaPanelUI extends BaseCustomUIPanelPlugin
    implements ActionListenerDelegate
{
    private static final SettingsAPI settings = Global.getSettings();

    private static final String ARENA_LEAVE_DATA = "arena_leave";
    private static final String ARENA_SELECT_DATA = "arena_champ_data";
    private static final String ARENA_BET_DATA = "arena_bet_data";
    private static final String ARENA_BET_CANCEL = "arena_bet_cancel";
    private static final String NEXT_ROUND_DATA = "arena_watch_next";
    private static final String NEXT_GAME_DATA = "arena_next_game";
    private static final String ARENA_SKIP_DATA = "arena_skip";
    private static final String ARENA_ADD_BET_DATA = "arena_add_bet";
    private static final String ARENA_SUSPEND_DATA = "arena_suspend";
    private static final String ARENA_START_BATTLE_DATA = "arena_start_battle";

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
        
        void parse(List<SpiralGladiator> combatants) {
            if (rawEntry == null || rawEntry.isEmpty()) {
                type = "";
                return;
            }
            
            if (rawEntry.startsWith("[ROUND]")) {
                type = "ROUND";
            } else if (rawEntry.startsWith("[KILL]")) {
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
            } else if (rawEntry.contains("miss") || rawEntry.contains("dodges") || rawEntry.contains("Evaded") || rawEntry.contains("shot goes wide")) {
                type = "MISS";
                parseMissLine(rawEntry, combatants);
            } else if (rawEntry.contains("--- SHIP STATUS ---") || rawEntry.contains("HP:")) {
                type = "STATUS";
            } else {
                type = "";
            }
        }
        
        void parseAttackLine(String line, List<SpiralGladiator> combatants) {
            if (combatants == null) return;
            
            // Find positions of each ship name in the line to determine attacker (first) vs target (second)
            int firstPos = Integer.MAX_VALUE;
            int secondPos = Integer.MAX_VALUE;
            SpiralGladiator firstShip = null;
            SpiralGladiator secondShip = null;
            
            for (SpiralGladiator ship : combatants) {
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
            
            final Pattern dmgPattern = Pattern.compile("(\\d+)");
            final Matcher matcher = dmgPattern.matcher(line);
            if (matcher.find()) {
                try {
                    damage = Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException e) {
                    damage = 0;
                }
            }
        }
        
        void parseKillLine(String line, List<SpiralGladiator> combatants) {
            if (combatants == null) return;
            
            // Find positions of each ship name in the line to determine attacker (first) vs target (second)
            int firstPos = Integer.MAX_VALUE;
            int secondPos = Integer.MAX_VALUE;
            SpiralGladiator firstShip = null;
            SpiralGladiator secondShip = null;
            
            for (SpiralGladiator ship : combatants) {
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
        
        void parseMissLine(String line, List<SpiralGladiator> combatants) {
            if (combatants == null) return;
            
            // For miss lines: target appears first in text, attacker appears second
            // Example: "$target:'Too slow!' ($attacker missed)"
            int firstPos = Integer.MAX_VALUE;
            int secondPos = Integer.MAX_VALUE;
            SpiralGladiator firstShip = null;
            SpiralGladiator secondShip = null;
            
            for (SpiralGladiator ship : combatants) {
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
        
        void parseEventLine(String line, List<SpiralGladiator> combatants) {
            description = line;
            
            if (combatants == null) return;
            
            for (SpiralGladiator ship : combatants) {
                if (line.contains(ship.shortName)) {
                    attackerHullId = ship.hullId;
                    break;
                }
            }
            
            final Pattern dmgPattern = Pattern.compile("\\(-?(\\d+)");
            final Matcher matcher = dmgPattern.matcher(line);
            if (matcher.find()) {
                try {
                    damage = Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException e) {
                    damage = 0;
                }
            }
        }
        
        void parseEventHitLine(String line, List<SpiralGladiator> combatants) {
            if (combatants == null) return;
            
            for (SpiralGladiator ship : combatants) {
                if (line.contains(ship.shortName)) {
                    attackerHullId = ship.hullId;
                    break;
                }
            }
            
            final Pattern dmgPattern = Pattern.compile("(\\d+)");
            final Matcher matcher = dmgPattern.matcher(line);
            if (matcher.find()) {
                try {
                    damage = Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException e) {
                    damage = 0;
                }
            }
        }
        
        boolean shouldSkipDeadAttacker(Map<String, Boolean> deadStatusMap) {
            return false;
        }
    }

    private CustomPanelAPI panel;
    private PositionAPI pos;
    
    private ArenaActionCallback actionCallback;
    
    private List<SpiralGladiator> combatants;
    private int currentRound;
    private int totalBet;
    private List<BetInfo> bets;
    private List<String> battleLog;
    
    private int selectedChampionIndex = -1;
    private boolean battleEnded = false;
    private int winnerIndex = -1;
    private int totalReward = 0;
    private RewardBreakdown rewardBreakdown;
    private boolean readyToClose = false;

    private static final float MARGIN = 20f;
    
    private static final float PANEL_WIDTH = 1000f;
    private static final float PANEL_HEIGHT = 700f;
    
    private static final float SHIP_COLUMN_WIDTH = 300f;
    private static final float CENTER_COLUMN_WIDTH = 450f;

    private static final float BOX_WIDTH = 150f;
    private static final float BOX_HEIGHT = 65f;
    private static final float BOX_SPACING = 3f;
    private static final float ENTRY_SPACING = 10f;
    
    private static final float CHAMP_BUTTON_WIDTH = 100f;
    private static final float CHAMP_BUTTON_HEIGHT = 25f;
    private static final float CHAMP_BUTTON_X = BOX_WIDTH + MARGIN + 15f;
    
    private static final float BUTTON_WIDTH = 120f;
    private static final float BUTTON_HEIGHT = 35f;
    private static final float BUTTON_SPACING = 10f;
    
    private static final float leftX = SHIP_COLUMN_WIDTH + MARGIN;
    private static final float bottomY = PANEL_HEIGHT - BUTTON_HEIGHT - MARGIN;
    
    private static final Color COLOR_HEALTHY = new Color(50, 200, 50);
    private static final Color COLOR_DAMAGED = new Color(200, 150, 50);
    private static final Color COLOR_DESTROYED = new Color(100, 30, 30);
    private static final Color COLOR_BOX_BG = new Color(40, 40, 50);
    private static final Color COLOR_BOX_BORDER = new Color(80, 80, 100);
    private static final Color COLOR_SELECTED = new Color(255, 215, 0);
    
    private static final Color PREFIX_POSITIVE_COLOR = new Color(50, 255, 50);
    private static final Color PREFIX_NEGATIVE_COLOR = new Color(255, 50, 50);
    private static final Color AFFIX_POSITIVE_COLOR = new Color(100, 200, 100);
    private static final Color AFFIX_NEGATIVE_COLOR = new Color(255, 150, 150);
    private static final Color NET_POSITIVE_COLOR = new Color(50, 255, 100);
    private static final Color NET_NEGATOVE_COLOR = new Color(255, 100, 50);
    private static final Color KILL_COLOR = new Color(255, 150, 50);
    
    private static final Color COLOR_BG_DARK = new Color(15, 15, 20);
    private static final Color COLOR_SIDEBAR = new Color(25, 25, 35);
    private static final Color COLOR_TINT_DEAD = new Color(150, 50, 50);
    private static final Color COLOR_TINT_DAMAGED = new Color(255, 200, 100);

    private static final Color BATTLE_HIT_COLOR_CRIT = new Color(255, 100, 100);
    private static final Color BATTLE_HIT_COLOR = new Color(255, 255, 100);
    private static final Color BATTLE_MISS_COLOR = new Color(150,150,150);
    private static final Color BATTLE_EVENT_COLOR = new Color(100,200,255);
    private static final Color BATTLE_EVENT_HIT_COLOR = new Color(255,200,50);
    private static final Color BATTLE_ROUND_COLOR = new Color(180,180,200);
    private static final Color DISABLED_BTN_COLOR = new Color(255, 200, 0);
    
    private final boolean isPrefixPositive(String prefix) {
        if (prefix == null) return true;
        return CasinoConfig.ARENA_PREFIX_STRONG_POS.contains(prefix);
    }
    
    private final boolean isAffixPositive(String affix) {
        if (affix == null) return true;
        return CasinoConfig.ARENA_AFFIX_POS.contains(affix);
    }
    
    private final Color getPrefixColor(String prefix) {
        return isPrefixPositive(prefix) ? PREFIX_POSITIVE_COLOR : PREFIX_NEGATIVE_COLOR;
    }
    
    private final Color getAffixColor(String affix) {
        return isAffixPositive(affix) ? AFFIX_POSITIVE_COLOR : AFFIX_NEGATIVE_COLOR;
    }
    
    private final void applyShipNameHighlighting(LabelAPI label,
        SpiralGladiator ship
    ) {
        if (label == null || ship == null) return;
        
        final String prefix = ship.prefix != null ? ship.prefix : "";
        final String hullName = ship.hullName != null ? ship.hullName : "";
        final String affix = ship.affix != null ? ship.affix : "";
        
        final List<String> highlights = new ArrayList<>();
        final List<Color> colors = new ArrayList<>();
        
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
    
    protected LabelAPI[] shipNameLabels = new LabelAPI[5];
    protected LabelAPI[] shipHpLabels = new LabelAPI[5];
    protected LabelAPI[] shipOddsLabels = new LabelAPI[5];
    
    protected LabelAPI[] battleLogTextLabels = new LabelAPI[12];
    
    protected static final float LOG_SPRITE_SIZE = 28f;
    protected static final float LOG_LINE_HEIGHT = 32f;
    protected static final float LOG_SPRITE_GAP = 4f;
    protected static final float LOG_LEFT_MARGIN = 5f;
    
    protected LabelAPI resultLabel;
    
    protected static final int MAX_REWARD_LINES = 25;
    protected LabelAPI[] rewardBreakdownLabels = new LabelAPI[MAX_REWARD_LINES];
    protected CustomPanelAPI[] rewardBreakdownPanels = new CustomPanelAPI[MAX_REWARD_LINES];
    
    protected LabelAPI instructionLabel;

    protected CustomPanelAPI watchNextPanel;
    protected CustomPanelAPI nextGamePanel;
    protected CustomPanelAPI skipToEndPanel;
    protected CustomPanelAPI addBetPanel;
    protected CustomPanelAPI suspendPanel;
    protected CustomPanelAPI leaveButtonPanel;
    protected CustomPanelAPI startBattlePanel;
    
    protected final List<CustomPanelAPI> championSelectPanels = new ArrayList<>();
    protected final List<CustomPanelAPI> betAmountPanels = new ArrayList<>();
    
    protected static final int[] BET_AMOUNTS = {100, 500, 1000, 2000, 5000};
    
    protected Map<String, SpriteAPI> spriteCache = new HashMap<>();
    protected static final float SPRITE_SCALE = 0.75f;
    
    protected float[] cachedOdds = new float[5];
    protected boolean oddsCached = false;
    
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

    protected int[] lastShipHp = new int[5];
    protected int[] lastShipMaxHp = new int[5];
    protected boolean[] lastShipDead = new boolean[5];
    protected float[] lastShipOdds = new float[5];
    protected int[] lastShipBetCount = new int[5];
    protected String[] lastShipHullIds = new String[5];
    protected boolean shipStateInitialized = false;
    
    protected int getTotalBetOnShip(int shipIndex) {
        if (bets == null || combatants == null || shipIndex < 0 || shipIndex >= combatants.size()) {
            return 0;
        }
        SpiralGladiator ship = combatants.get(shipIndex);
        int total = 0;
        for (BetInfo b : bets) {
            if (b.ship == ship) {
                total += b.amount;
            }
        }
        return total;
    }
    
    protected boolean canBetMoreOnShip(int shipIndex) {
        int currentBet = getTotalBetOnShip(shipIndex);
        return currentBet < CasinoConfig.ARENA_MAX_BET_PER_CHAMPION;
    }

    public interface ArenaActionCallback {
        void onSelectChampion(int championIndex);
        void onConfirmBet(int championIndex, int amount);
        void onStartBattle();
        void onWatchNextRound();
        void onSkipToEnd();
        void onSuspend();
        void onLeave();
        void onReturnToLobby();
        void onNextGame();
        void onEscape();
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
        List<SpiralGladiator> combatants,
        int currentRound,
        int totalBet,
        List<BetInfo> bets,
        List<String> battleLog,
        ArenaActionCallback callback
    ) {    
        this.combatants = combatants;
        this.currentRound = currentRound;
        this.totalBet = totalBet;
        this.bets = bets;
        this.battleLog = battleLog;
        this.actionCallback = callback;
    }
    
    private final SpriteAPI getShipSprite(String hullId) {
        if (hullId == null || hullId.isEmpty()) return null;
        
        if (spriteCache.containsKey(hullId)) {
            return spriteCache.get(hullId);
        }
        
        try {
            final ShipHullSpecAPI spec = settings.getHullSpec(hullId);
            if (spec == null) {
                spriteCache.put(hullId, null);
                return null;
            }
            
            final String spriteName = spec.getSpriteName();
            if (spriteName == null || spriteName.isEmpty()) {
                spriteCache.put(hullId, null);
                return null;
            }
            
            final SpriteAPI sprite = settings.getSprite(spriteName);
            spriteCache.put(hullId, sprite);
            return sprite;
        } catch (Exception e) {
            spriteCache.put(hullId, null);
            return null;
        }
    }
    
    public void init(CustomPanelAPI panel, DialogCallbacks callbacks) {
        this.panel = panel;
        callbacks.getPanelFader().setDurationOut(0.5f);
        
        cacheOdds();
        createUIElements();
        updateLabels();
        updateButtonVisibility();
    }
    
    public void positionChanged(PositionAPI position) {
        this.pos = position;
    }
    
    private final void createUIElements() {
        if (panel == null) return;
        
        createRoundLabel();
        createBetLabel();
        createInstructionLabel();
        createShipLabels();
        createBattleLogPanel();
        createResultLabel();
        createRewardBreakdownLabels();
        createAllButtons();
    }
    
    private final void createBattleLogPanel() {
        if (panel == null) return;
        
        final float logPanelW = CENTER_COLUMN_WIDTH - MARGIN;
        final float logPanelH = PANEL_HEIGHT - MARGIN * 2 - 80f;
        
        logX = LOG_LEFT_MARGIN;
        logY = LOG_LINE_HEIGHT;
        logW = logPanelW - LOG_LEFT_MARGIN * 2;
        logH = logPanelH - LOG_LINE_HEIGHT;
        
        final float textWidthTwoSprites = logW - LOG_SPRITE_SIZE * 2 - LOG_SPRITE_GAP * 2 - 30f;
        final float textWidthOneSprite = logW - LOG_SPRITE_SIZE - LOG_SPRITE_GAP - 30f;
        final float lblW = Math.max(textWidthTwoSprites, textWidthOneSprite);
        
        for (int i = 0; i < 12; i++) {
            final LabelAPI logLbl = settings.createLabel("", Fonts.DEFAULT_SMALL);
            battleLogTextLabels[i] = logLbl;
            logLbl.setColor(Color.WHITE);
            logLbl.setAlignment(Alignment.MID);
            panel.addComponent((UIComponentAPI) logLbl).inTL(0f, 0f)
                .setSize(lblW, LOG_LINE_HEIGHT);
        }
    }
    
    private final void createAllButtons() {
        if (panel == null) return;
        
        final float startY = MARGIN + 10f;
        final float NAME_HEIGHT = 16f;
        final float HP_HEIGHT = 11f;
        final float ODDS_HEIGHT = 30f;
        final float totalItemHeight = BOX_HEIGHT + BOX_SPACING + NAME_HEIGHT + HP_HEIGHT + ODDS_HEIGHT + ENTRY_SPACING;
        
        for (int i = 0; i < 5; i++) {
            final float shipY = startY + i * totalItemHeight;
            final float buttonY = shipY + (BOX_HEIGHT - CHAMP_BUTTON_HEIGHT) / 2f;
            
            final CustomPanelAPI champPanel = panel.createCustomPanel(CHAMP_BUTTON_WIDTH, CHAMP_BUTTON_HEIGHT, null);
            final TooltipMakerAPI champTooltip = champPanel.createUIElement(CHAMP_BUTTON_WIDTH, CHAMP_BUTTON_HEIGHT, false);
            champTooltip.setActionListenerDelegate(this);
            final ButtonAPI btn = champTooltip.addButton("Select", ARENA_SELECT_DATA + i, CHAMP_BUTTON_WIDTH, CHAMP_BUTTON_HEIGHT, 0f);
            btn.setQuickMode(true);
            btn.getPosition().inTL(0, 0);
            champPanel.addUIElement(champTooltip).inTL(0, 0);
            panel.addComponent(champPanel).inTL(CHAMP_BUTTON_X, buttonY);
            champPanel.setOpacity(0f);
            championSelectPanels.add(champPanel);
        }
        
        final CustomPanelAPI betCancelPanel = panel.createCustomPanel(BUTTON_WIDTH, BUTTON_HEIGHT, null);
        final TooltipMakerAPI betCancelTooltip = betCancelPanel.createUIElement(BUTTON_WIDTH, BUTTON_HEIGHT, false);
        betCancelTooltip.setActionListenerDelegate(this);
        betCancelTooltip.addButton("Cancel", ARENA_BET_CANCEL, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        betCancelPanel.addUIElement(betCancelTooltip).inTL(0, 0);
        panel.addComponent(betCancelPanel).inTL(leftX, bottomY);
        betCancelPanel.setOpacity(0f);
        betAmountPanels.add(betCancelPanel);
        
        for (int i = 0; i < BET_AMOUNTS.length; i++) {
            final int amt = BET_AMOUNTS[i];
            final float btnX = leftX + i * (BUTTON_WIDTH + BUTTON_SPACING);
            
            final CustomPanelAPI betBtnPanel = panel.createCustomPanel(BUTTON_WIDTH, BUTTON_HEIGHT, null);
            final TooltipMakerAPI betTooltip = betBtnPanel.createUIElement(BUTTON_WIDTH, BUTTON_HEIGHT, false);
            betTooltip.setActionListenerDelegate(this);
            betTooltip.addButton(amt + " SG", ARENA_BET_DATA + amt, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
            betBtnPanel.addUIElement(betTooltip);
            panel.addComponent(betBtnPanel).inTL(btnX, bottomY - BUTTON_HEIGHT - BUTTON_SPACING);
            betBtnPanel.setOpacity(0f);
            betAmountPanels.add(betBtnPanel);
        }

        { // Leave Button
            leaveButtonPanel = panel.createCustomPanel(BUTTON_WIDTH, BUTTON_HEIGHT, null);
            final TooltipMakerAPI leaveTooltip = leaveButtonPanel.createUIElement(BUTTON_WIDTH, BUTTON_HEIGHT, false);
            leaveTooltip.setActionListenerDelegate(this);
            final ButtonAPI btn = leaveTooltip.addButton("Leave", ARENA_LEAVE_DATA, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
            btn.setQuickMode(true);
            leaveButtonPanel.addUIElement(leaveTooltip).inTL(0, 0);
            panel.addComponent(leaveButtonPanel).inTL(leftX, bottomY);
            leaveButtonPanel.setOpacity(0f);
        }
        
        { // Suspend Button
            suspendPanel = panel.createCustomPanel(BUTTON_WIDTH, BUTTON_HEIGHT, null);
            final TooltipMakerAPI suspendTooltip = suspendPanel.createUIElement(BUTTON_WIDTH, BUTTON_HEIGHT, false);
            suspendTooltip.setActionListenerDelegate(this);
            final ButtonAPI btn = suspendTooltip.addButton("Suspend", ARENA_SUSPEND_DATA, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
            btn.setQuickMode(true);
            suspendPanel.addUIElement(suspendTooltip).inTL(0, 0);
            panel.addComponent(suspendPanel).inTL(leftX + BUTTON_WIDTH + BUTTON_SPACING, bottomY);
            suspendPanel.setOpacity(0f);
        }
        
        { // Add Bet Button
            addBetPanel = panel.createCustomPanel(BUTTON_WIDTH, BUTTON_HEIGHT, null);
            final TooltipMakerAPI addBetTooltip = addBetPanel.createUIElement(BUTTON_WIDTH, BUTTON_HEIGHT, false);
            addBetTooltip.setActionListenerDelegate(this);
            final ButtonAPI btn = addBetTooltip.addButton("Add Bet", ARENA_ADD_BET_DATA, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
            btn.setQuickMode(true);
            addBetPanel.addUIElement(addBetTooltip).inTL(0, 0);
            panel.addComponent(addBetPanel).inTL(leftX + (BUTTON_WIDTH + BUTTON_SPACING) * 2, bottomY);
            addBetPanel.setOpacity(0f);
        }
        
        { // Skip to End Button
            skipToEndPanel = panel.createCustomPanel(BUTTON_WIDTH, BUTTON_HEIGHT, null);
            final TooltipMakerAPI skipTooltip = skipToEndPanel.createUIElement(BUTTON_WIDTH, BUTTON_HEIGHT, false);
            skipTooltip.setActionListenerDelegate(this);
            final ButtonAPI btn = skipTooltip.addButton("Skip to End", ARENA_SKIP_DATA, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
            btn.setQuickMode(true);
            skipToEndPanel.addUIElement(skipTooltip).inTL(0, 0);
            panel.addComponent(skipToEndPanel).inTL(leftX + (BUTTON_WIDTH + BUTTON_SPACING) * 3, bottomY);
            skipToEndPanel.setOpacity(0f);
        }

        { // Watch Next Button
            watchNextPanel = panel.createCustomPanel(BUTTON_WIDTH, BUTTON_HEIGHT, null);
            final TooltipMakerAPI watchTooltip = watchNextPanel.createUIElement(BUTTON_WIDTH, BUTTON_HEIGHT, false);
            watchTooltip.setActionListenerDelegate(this);
            final ButtonAPI btn = watchTooltip.addButton("Next Round", NEXT_ROUND_DATA, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
            btn.setQuickMode(true);
            watchNextPanel.addUIElement(watchTooltip).inTL(0, 0);
            panel.addComponent(watchNextPanel).inTL(leftX + (BUTTON_WIDTH + BUTTON_SPACING) * 4, bottomY);
            watchNextPanel.setOpacity(0f);
        }
        
        { // Next Game Button
            nextGamePanel = panel.createCustomPanel(BUTTON_WIDTH, BUTTON_HEIGHT, null);
            final TooltipMakerAPI nextGameTooltip = nextGamePanel.createUIElement(BUTTON_WIDTH, BUTTON_HEIGHT, false);
            nextGameTooltip.setActionListenerDelegate(this);
            final ButtonAPI btn = nextGameTooltip.addButton("Next Game", NEXT_GAME_DATA, Color.BLACK, DISABLED_BTN_COLOR, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
            btn.setQuickMode(true);
            nextGamePanel.addUIElement(nextGameTooltip).inTL(0, 0);
            panel.addComponent(nextGamePanel).inTL(leftX + BUTTON_WIDTH + BUTTON_SPACING, bottomY);
            nextGamePanel.setOpacity(0f);
        }

        { // Start Battle Button
            startBattlePanel = panel.createCustomPanel(BUTTON_WIDTH, BUTTON_HEIGHT, null);
            final TooltipMakerAPI startTooltip = startBattlePanel.createUIElement(BUTTON_WIDTH, BUTTON_HEIGHT, false);
            startTooltip.setActionListenerDelegate(this);
            final ButtonAPI btn = startTooltip.addButton("Start Battle", ARENA_START_BATTLE_DATA, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
            btn.setQuickMode(true);
            startBattlePanel.addUIElement(startTooltip).inTL(0, 0);
            panel.addComponent(startBattlePanel).inTL(leftX + BUTTON_WIDTH + BUTTON_SPACING, bottomY);
            startBattlePanel.setOpacity(0f);
        }
    }
    
    private final void updateButtonVisibility() {
        final boolean showChampionSelect =
            (currentRound == 0 && !showingBetAmounts && !battleEnded) ||
            (currentRound > 0 && showingBetAmounts && addingBetDuringBattle && selectedChampionIndex < 0 && !battleEnded);

        final boolean showChampionAsBet = currentRound > 0 && showingBetAmounts && addingBetDuringBattle && selectedChampionIndex < 0;
        final boolean showBetAmounts = showingBetAmounts && selectedChampionIndex >= 0 && !battleEnded;
        
        for (int i = 0; i < championSelectPanels.size(); i++) {
            final CustomPanelAPI p = championSelectPanels.get(i);
            if (p == null) continue;

            if (i < 5) {
                final boolean btnVis = (showChampionSelect || showChampionAsBet) && canBetMoreOnShip(i);
                p.setOpacity(btnVis ? 1f : 0f);
            } else {
                p.setOpacity(showChampionSelect ? 1f : 0f);
            }
        }
        
        for (int i = 0; i < betAmountPanels.size(); i++) {
            final CustomPanelAPI p = betAmountPanels.get(i);
            if (p == null) continue;

            if (showBetAmounts) {
                if (i == 0) {
                    p.setOpacity(1f);
                } else {
                    final int betAmount = BET_AMOUNTS[i - 1];
                    final int currentBetOnShip = getTotalBetOnShip(selectedChampionIndex);
                    p.setOpacity(currentBetOnShip + betAmount >
                        CasinoConfig.ARENA_MAX_BET_PER_CHAMPION ? 0f : 1f);
                }
            } else {
                p.setOpacity(0f);
            }
        }
        
        if (leaveButtonPanel != null) {
            leaveButtonPanel.setOpacity(0f);

            if (battleEnded || !showBetAmounts && !showChampionSelect ||
                showChampionSelect || totalBet < 1 && !showBetAmounts
            ) { leaveButtonPanel.setOpacity(1f); }
        }
        
        if (suspendPanel != null) {
            suspendPanel.setOpacity(!battleEnded && currentRound > 0 && !showingBetAmounts ? 1f : 0f);
        }
        
        if (addBetPanel != null) {
            addBetPanel.setOpacity(currentRound > 0 && !showingBetAmounts && !battleEnded ? 1f : 0f);
        }
        
        if (skipToEndPanel != null) {
            skipToEndPanel.setOpacity(currentRound > 0 && !showingBetAmounts && !battleEnded ? 1f : 0f);
        }
        
        if (watchNextPanel != null) {
            watchNextPanel.setOpacity(currentRound > 0 && !showingBetAmounts && !battleEnded ? 1f : 0f);
        }
        
        if (nextGamePanel != null) {
            nextGamePanel.setOpacity(battleEnded ? 1f : 0f);
        }
        
        if (startBattlePanel != null) {
            startBattlePanel.setOpacity(currentRound == 0 && !showingBetAmounts && totalBet > 0 ? 1f : 0f);
        }
    }
    
    private final void createRoundLabel() {
        if (panel == null) return;
        
        final float LABEL_WIDTH = 150f;
        final float LABEL_HEIGHT = 30f;
        
        float x = SHIP_COLUMN_WIDTH + MARGIN;

        roundLabel = settings.createLabel("Round: 0", Fonts.DEFAULT_SMALL);
        roundLabel.setColor(Color.CYAN);
        roundLabel.setAlignment(Alignment.LMID);
        panel.addComponent((UIComponentAPI) roundLabel).inTL(x, MARGIN)
            .setSize(LABEL_WIDTH, LABEL_HEIGHT);
    }
    
    private final void createBetLabel() {
        if (panel == null) return;
        
        final float LABEL_WIDTH = 200f;
        final float LABEL_HEIGHT = 30f;
        
        float x = SHIP_COLUMN_WIDTH + MARGIN + 160f;

        betLabel = settings.createLabel("Total Bet: 0", Fonts.DEFAULT_SMALL);
        betLabel.setColor(Color.YELLOW);
        betLabel.setAlignment(Alignment.LMID);
        panel.addComponent((UIComponentAPI) betLabel).inTL(x, MARGIN)
            .setSize(LABEL_WIDTH, LABEL_HEIGHT);
    }
    
    private final void createInstructionLabel() {
        if (panel == null) return;
        
        final float LABEL_WIDTH = 250f;
        final float LABEL_HEIGHT = 30f;
        
        final float x = SHIP_COLUMN_WIDTH + MARGIN + 360f;

        instructionLabel = settings.createLabel("Select a champion to bet on:", Fonts.DEFAULT_SMALL);
        instructionLabel.setColor(Color.WHITE);
        instructionLabel.setAlignment(Alignment.LMID);
        panel.addComponent((UIComponentAPI) instructionLabel).inTL(x, MARGIN)
            .setSize(LABEL_WIDTH, LABEL_HEIGHT);
    }
    
    private final void createResultLabel() {
        if (panel == null) return;
        
        final float RESULT_WIDTH = 200f;
        final float RESULT_HEIGHT = 50f;
        
        final float x = PANEL_WIDTH - RESULT_WIDTH - MARGIN;
        final float y = bottomY - RESULT_HEIGHT - BUTTON_SPACING;
        
        resultLabel = settings.createLabel("", Fonts.DEFAULT_SMALL);
        resultLabel.setColor(Color.YELLOW);
        resultLabel.setAlignment(Alignment.MID);
        panel.addComponent((UIComponentAPI) resultLabel).inTL(x, y)
            .setSize(RESULT_WIDTH, RESULT_HEIGHT);
    }
    
    private final void createRewardBreakdownLabels() {
        if (panel == null) return;
        
        float breakdownX = SHIP_COLUMN_WIDTH + MARGIN + CENTER_COLUMN_WIDTH - MARGIN;
        float breakdownY = MARGIN + 40f;
        float breakdownW = PANEL_WIDTH - breakdownX - MARGIN;
        float lineHeight = 28f;
        float spacing = 2f;
        
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
    
    private final void createShipLabels() {
        if (panel == null || combatants == null) return;
        
        final float NAME_WIDTH = (SHIP_COLUMN_WIDTH - MARGIN * 2) * 2;
        final float NAME_HEIGHT = 16f;
        final float HP_WIDTH = SHIP_COLUMN_WIDTH - MARGIN * 2 - 5f;
        final float HP_HEIGHT = 11f;
        final float ODDS_WIDTH = (SHIP_COLUMN_WIDTH - MARGIN * 2) * 2;
        final float ODDS_HEIGHT = 30f;
        
        final float startY = MARGIN + 10f;
        final float totalItemHeight = BOX_HEIGHT + BOX_SPACING + NAME_HEIGHT + HP_HEIGHT + ODDS_HEIGHT + ENTRY_SPACING;
        
        for (int i = 0; i < combatants.size() && i < 5; i++) {
            final SpiralGladiator ship = combatants.get(i);
            final float shipY = startY + i * totalItemHeight;

            final String fullName = ship.prefix + " " + ship.hullName + " " + ship.affix;
            final LabelAPI nameLbl = settings.createLabel(fullName, Fonts.DEFAULT_SMALL);
            shipNameLabels[i] = nameLbl;
            nameLbl.setColor(Color.WHITE);
            nameLbl.setAlignment(Alignment.LMID);
            nameLbl.getPosition().setSize(NAME_WIDTH, NAME_HEIGHT).inTL(0f, 0f);
            panel.addComponent((UIComponentAPI) nameLbl).inTL(MARGIN, shipY + BOX_HEIGHT + 2f);
            applyShipNameHighlighting(shipNameLabels[i], ship);

            final LabelAPI hpLbl = settings.createLabel(
                ship.hp + "/" + ship.maxHp + " HP", Fonts.DEFAULT_SMALL
            );
            shipHpLabels[i] = hpLbl;
            hpLbl.setColor(COLOR_HEALTHY);
            hpLbl.setAlignment(Alignment.LMID);
            hpLbl.getPosition().setSize(HP_WIDTH, HP_HEIGHT).inTL(0, 0);
            panel.addComponent((UIComponentAPI) hpLbl).inTL(MARGIN + 5f, shipY + BOX_HEIGHT + NAME_HEIGHT + 4f);
            
            final float displayOdds = oddsCached && i < cachedOdds.length ? 
                cachedOdds[i] : ship.getCurrentOdds(currentRound);
            final LabelAPI oddsLbl = settings.createLabel(
                String.format("Odds: %.2fx", displayOdds), Fonts.DEFAULT_SMALL
            );
            shipOddsLabels[i] = oddsLbl;
            oddsLbl.setColor(Color.YELLOW);
            oddsLbl.setAlignment(Alignment.LMID);
            oddsLbl.getPosition().setSize(ODDS_WIDTH - 10f, ODDS_HEIGHT).inTL(0f, 0f);
            panel.addComponent((UIComponentAPI) oddsLbl).inTL(
                MARGIN + 5f, shipY + BOX_HEIGHT + NAME_HEIGHT + HP_HEIGHT + 6f
            );
        }
    }
    
    public void renderBelow(float alphaMult) {
        final float x, y, w, h;
        
        if (pos != null) {
            x = pos.getX();
            y = pos.getY();
            w = pos.getWidth();
            h = pos.getHeight();
        } else {
            x = 0;
            y = 0;
            w = PANEL_WIDTH;
            h = PANEL_HEIGHT;
        }
        
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        final float s = settings.getScreenScaleMult();
        GL11.glScissor((int)(x * s), (int)(y * s), (int)(w * s), (int)(h * s));
        
        Misc.renderQuad(x, y, w, h, COLOR_BG_DARK, alphaMult);
        
        Misc.renderQuad(x, y, SHIP_COLUMN_WIDTH, h, COLOR_SIDEBAR, alphaMult);
        
        if (combatants != null) {
            renderShipBoxes(x, y, h, alphaMult);
        }
        
        renderBattleLogSprites(x, y, alphaMult);
        
        // Draw divider line between battle log and reward breakdown
        final float dividerX = x + SHIP_COLUMN_WIDTH + CENTER_COLUMN_WIDTH - 5f;
        final float dividerTop = y + MARGIN + 50f; // Below instruction text area
        final float dividerBottom = y + h - MARGIN - 50f; // Above button area
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(0.3f, 0.3f, 0.4f, alphaMult * 0.5f);
        GL11.glLineWidth(1f);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(dividerX, dividerTop);
        GL11.glVertex2f(dividerX, dividerBottom);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        
        updateLabels();
        
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
    
    private final void renderShipBoxes(float panelX, float panelY, float panelH,
        float alphaMult
    ) {
        if (combatants == null) return;
        
        final float NAME_HEIGHT = 16f;
        final float HP_HEIGHT = 11f;
        final float ODDS_HEIGHT = 30f;
        
        final float startY = MARGIN + 10f;
        final float totalItemHeight = BOX_HEIGHT + BOX_SPACING + NAME_HEIGHT
            + HP_HEIGHT + ODDS_HEIGHT + ENTRY_SPACING;
        
        for (int i = 0; i < combatants.size(); i++) {
            final SpiralGladiator ship = combatants.get(i);
            final float shipY = startY + i * totalItemHeight;
            final float shipX = MARGIN;
            
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            
            final float screenY = panelY + panelH - shipY - BOX_HEIGHT;
            
            final float hpPercent;
            final Color hpColor;
            if (ship.isDead) {
                hpPercent = 0.08f; // Small red sliver for dead ships
                hpColor = COLOR_DESTROYED;
            } else {
                hpPercent = Math.max(0, Math.min(1, (float) ship.hp / ship.maxHp));
                hpColor = hpPercent > 0.5f ? COLOR_HEALTHY : (hpPercent > 0.25f ? COLOR_DAMAGED : COLOR_DESTROYED);
            }
            
            // Selection highlight
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
            
            // 1. Render dark gray background (full box)
            Misc.renderQuad(panelX + shipX, screenY, BOX_WIDTH, BOX_HEIGHT, COLOR_BOX_BG, alphaMult * 0.8f);
            
            // 2. Render HP fill (from LEFT, width = BOX_WIDTH * hpPercent)
            float fillWidth = (BOX_WIDTH - 2) * hpPercent;
            Misc.renderQuad(panelX + shipX + 1, screenY + 1, fillWidth, BOX_HEIGHT - 2, hpColor, alphaMult * 0.6f);
            
            // 3. Render border around entire box
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(COLOR_BOX_BORDER.getRed() / 255f, COLOR_BOX_BORDER.getGreen() / 255f,
                COLOR_BOX_BORDER.getBlue() / 255f, alphaMult * 0.8f);
            GL11.glLineWidth(1f);
            GL11.glBegin(GL11.GL_LINE_LOOP);
            GL11.glVertex2f(panelX + shipX, screenY);
            GL11.glVertex2f(panelX + shipX + BOX_WIDTH, screenY);
            GL11.glVertex2f(panelX + shipX + BOX_WIDTH, screenY + BOX_HEIGHT);
            GL11.glVertex2f(panelX + shipX, screenY + BOX_HEIGHT);
            GL11.glEnd();
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            
            final SpriteAPI sprite = getShipSprite(ship.hullId);
            if (sprite != null) {
                final float spriteWidth = sprite.getWidth();
                final float spriteHeight = sprite.getHeight();
                final float maxDim = Math.max(spriteWidth, spriteHeight);
                final float scale = (BOX_HEIGHT * SPRITE_SCALE) / maxDim;
                
                final float centerX = panelX + shipX + BOX_WIDTH / 2f;
                final float centerY = screenY + BOX_HEIGHT / 2f;
                
                sprite.setSize(spriteWidth * scale, spriteHeight * scale);
                
                final Color tint;
                final float spriteAlpha;
                if (ship.isDead) {
                    tint = COLOR_TINT_DEAD;
                    spriteAlpha = 0.6f * alphaMult;
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
                GL11.glEnable(GL11.GL_TEXTURE_2D);
            }
        }
    }
    
    private final void updateLabels() {
        if (roundLabel != null && currentRound != lastCurrentRound) {
            lastCurrentRound = currentRound;
            roundLabel.setText("Round: " + currentRound);
        }
        
        if (betLabel != null && totalBet != lastTotalBet) {
            lastTotalBet = totalBet;
            betLabel.setText("Total Bet: " + totalBet);
        }
        
        if (instructionLabel != null) {
            final String newInstructionText;
            boolean hideInstruction = false;
            
            if (battleEnded) {
                newInstructionText = "Click Next Game for a new match, or Leave to exit";
            } else if (currentRound > 0 && addingBetDuringBattle && selectedChampionIndex < 0) {
                newInstructionText = "Select a champion to add bet on:";
            } else if (currentRound > 0 && addingBetDuringBattle && selectedChampionIndex >= 0) {
                SpiralGladiator selected = combatants.get(selectedChampionIndex);
                newInstructionText = "Adding bet on: " + selected.hullName + " - Select amount:";
            } else if (currentRound > 0) {
                newInstructionText = null;
                hideInstruction = true;
            } else if (showingBetAmounts && selectedChampionIndex >= 0) {
                SpiralGladiator selected = combatants.get(selectedChampionIndex);
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
                final SpiralGladiator ship = combatants.get(i);
                
                // Show panels if they were hidden
                if (shipNameLabels[i] != null) {
                    shipNameLabels[i].setOpacity(1f);
                }
                if (shipHpLabels[i] != null) {
                    shipHpLabels[i].setOpacity(1f);
                }
                if (shipOddsLabels[i] != null) {
                    shipOddsLabels[i].setOpacity(1f);
                }
                
                final boolean shipChanged = !shipStateInitialized || 
                    (lastShipHullIds[i] == null || !lastShipHullIds[i].equals(ship.hullId));
                
                if (shipChanged && shipNameLabels[i] != null) {
                    lastShipHullIds[i] = ship.hullId;
                    final String fullName = ship.prefix + " " + ship.hullName + " " + ship.affix;
                    shipNameLabels[i].setText(fullName);
                    applyShipNameHighlighting(shipNameLabels[i], ship);
                }
                
                final boolean hpChanged = !shipStateInitialized || 
                    lastShipHp[i] != ship.hp || 
                    lastShipMaxHp[i] != ship.maxHp ||
                    lastShipDead[i] != ship.isDead;
                
                if (hpChanged) {
                    lastShipHp[i] = ship.hp;
                    lastShipMaxHp[i] = ship.maxHp;
                    lastShipDead[i] = ship.isDead;
                    
                    if (shipHpLabels[i] != null) {
                        shipHpLabels[i].setText(ship.hp + "/" + ship.maxHp + " HP");
                        shipHpLabels[i].setColor(ship.isDead ?
                            COLOR_DESTROYED : 
                            ((float) ship.hp / ship.maxHp > 0.5f ?
                            COLOR_HEALTHY : COLOR_DAMAGED)
                        );
                    }
                }
                
                final float displayOdds = oddsCached && i < cachedOdds.length ? cachedOdds[i] : ship.getCurrentOdds(currentRound);
                
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
                        Map<Integer, List<BetInfo>> betsByRound = new HashMap<>();
                        for (BetInfo b : bets) {
                            if (b.ship == ship) {
                                betsByRound.computeIfAbsent(b.roundPlaced, k -> new ArrayList<>()).add(b);
                            }
                        }
                        
                        final List<String> betStrings = new ArrayList<>();
                        for (Map.Entry<Integer, List<BetInfo>> entry : betsByRound.entrySet()) {
                            List<BetInfo> roundBets = entry.getValue();
                            if (!roundBets.isEmpty()) {
                                int totalAmount = 0;
                                final float multiplier = roundBets.get(0).multiplier;
                                for (BetInfo b : roundBets) {
                                    totalAmount += b.amount;
                                }
                                betStrings.add(String.format("[%d, %.2fx]", totalAmount, multiplier));
                            }
                        }
                        
                        if (!betStrings.isEmpty()) {
                            // Limit display to prevent overflow - show first 3 bet entries max
                            int displayCount = Math.min(betStrings.size(), 3);
                            String displayText = String.join("", betStrings.subList(0, displayCount));
                            if (betStrings.size() > 3) {
                                displayText += " +" + (betStrings.size() - 3) + " more";
                            }
                            oddsText += " " + displayText;
                        }
                    }
                    
                    shipOddsLabels[i].setText(oddsText);
                    shipOddsLabels[i].setColor(ship.isDead ? Color.GRAY : Color.YELLOW);
                }
            }
            
            // Hide labels for ships that no longer exist (e.g., fewer ships in new match)
            for (int i = combatants.size(); i < 5; i++) {
                if (shipNameLabels[i] != null) {
                    shipNameLabels[i].setOpacity(0f);
                }
                if (shipHpLabels[i] != null) {
                    shipHpLabels[i].setOpacity(0f);
                }
                if (shipOddsLabels[i] != null) {
                    shipOddsLabels[i].setOpacity(0f);
                }
            }
            
            shipStateInitialized = true;
        }
        
        if (resultLabel != null) {
            if (!battleEnded || rewardBreakdown != null) {
                resultLabel.setText("");
            } else {
                if (winnerIndex >= 0 && winnerIndex < Objects.requireNonNull(combatants).size()) {
                    final SpiralGladiator winner = combatants.get(winnerIndex);
                    resultLabel.setText("WINNER: " + winner.hullName + "!\nReward: " + totalReward + " Stargems");
                    resultLabel.setColor(Color.GREEN);
                } else {
                    resultLabel.setText("");
                }
            }
        }
        
        updateRewardBreakdownLabels();
    }
    
    private final void updateRewardBreakdownLabels() {
        for (int i = 0; i < MAX_REWARD_LINES; i++) {
            if (rewardBreakdownLabels[i] != null) {
                rewardBreakdownLabels[i].setText("");
            }
        }
        
        if (!battleEnded || rewardBreakdown == null) return;
        
        if (rewardBreakdownLabels[0] == null) return;
        
        int lineIndex = 0;
        
        if (winnerIndex >= 0 && winnerIndex < combatants.size() && lineIndex < MAX_REWARD_LINES) {
            SpiralGladiator winner = combatants.get(winnerIndex);
            rewardBreakdownLabels[lineIndex].setText("WINNER: " + winner.hullName + "!");
            rewardBreakdownLabels[lineIndex].setColor(PREFIX_POSITIVE_COLOR);
            lineIndex++;
        }
        
        if (lineIndex < MAX_REWARD_LINES) {
            rewardBreakdownLabels[lineIndex].setText("--- Reward Breakdown ---");
            rewardBreakdownLabels[lineIndex].setColor(BATTLE_EVENT_COLOR);
            lineIndex++;
        }
        
        if (lineIndex < MAX_REWARD_LINES) {
            rewardBreakdownLabels[lineIndex].setText("Total Bet: " + rewardBreakdown.totalBet + " SG");
            rewardBreakdownLabels[lineIndex].setColor(Color.WHITE);
            lineIndex++;
        }
        
        if (rewardBreakdown.winReward > 0 && lineIndex < MAX_REWARD_LINES) {
            rewardBreakdownLabels[lineIndex].setText("Win Reward: +" + rewardBreakdown.winReward + " SG");
            rewardBreakdownLabels[lineIndex].setColor(PREFIX_POSITIVE_COLOR);
            lineIndex++;
        }
        
        if (rewardBreakdown.consolationReward > 0 && lineIndex < MAX_REWARD_LINES) {
            rewardBreakdownLabels[lineIndex].setText("Consolation: +" + rewardBreakdown.consolationReward + " SG");
            rewardBreakdownLabels[lineIndex].setColor(BATTLE_EVENT_HIT_COLOR);
            lineIndex++;
        }
        
        int totalKills = 0;
        for (RewardBreakdown.ShipRewardInfo shipInfo : rewardBreakdown.shipRewards) {
            totalKills += shipInfo.kills;
        }
        
        if (totalKills > 0 && lineIndex < MAX_REWARD_LINES) {
            float killBonusPct = totalKills * CasinoConfig.ARENA_KILL_BONUS_PER_KILL * 100;
            rewardBreakdownLabels[lineIndex].setText("Kill Bonus: " + totalKills + " kills (+" + (int)killBonusPct + "%)");
            rewardBreakdownLabels[lineIndex].setColor(KILL_COLOR);
            lineIndex++;
        }
        
        for (RewardBreakdown.ShipRewardInfo shipInfo : rewardBreakdown.shipRewards) {
            if (shipInfo.betAmount <= 0) continue;
            if (lineIndex >= MAX_REWARD_LINES) break;

            Color statusColor = shipInfo.isWinner ? PREFIX_POSITIVE_COLOR : Color.RED;
            String posStr = getPositionString(shipInfo.finalPosition);
            float killBonusPct = shipInfo.kills * CasinoConfig.ARENA_KILL_BONUS_PER_KILL * 100;
            
            String shipLine = String.format("%s: %d SG | %s | %d kills (+%.0f%%)", 
                shipInfo.shipName, shipInfo.betAmount, posStr, shipInfo.kills, killBonusPct);
            rewardBreakdownLabels[lineIndex].setText(shipLine);
            rewardBreakdownLabels[lineIndex].setColor(statusColor);
            lineIndex++;
            
            if (shipInfo.reward > 0 && lineIndex < MAX_REWARD_LINES) {
                String rewardLine = String.format("  -> Reward: +%d SG", shipInfo.reward);
                Color rewardColor = shipInfo.isWinner ? PREFIX_POSITIVE_COLOR : BATTLE_EVENT_HIT_COLOR;
                rewardBreakdownLabels[lineIndex].setText(rewardLine);
                rewardBreakdownLabels[lineIndex].setColor(rewardColor);
                lineIndex++;
            }
        }
        
        if (lineIndex < MAX_REWARD_LINES) {
            int net = rewardBreakdown.netResult;
            Color netColor = net >= 0 ? NET_POSITIVE_COLOR : NET_NEGATOVE_COLOR;
            String netStr = net >= 0 ? "+" + net : String.valueOf(net);
            rewardBreakdownLabels[lineIndex].setText("NET: " + netStr + " SG");
            rewardBreakdownLabels[lineIndex].setColor(netColor);
        }
    }
    
    private final void updateHullIdDeadStatus() {
        hullIdDeadStatus.clear();
        if (combatants != null) {
            for (SpiralGladiator g : combatants) {
                hullIdDeadStatus.put(g.hullId, g.isDead);
            }
        }
    }
    
    private final void updateCachedParsedEntries() {
        final int currentSize = battleLog != null ? battleLog.size() : 0;
        if (currentSize == lastBattleLogSize) {
            return;
        }
        lastBattleLogSize = currentSize;
        
        cachedParsedEntries.clear();
        if (battleLog != null) {
            for (String entry : battleLog) {
                final ParsedLogEntry parsed = new ParsedLogEntry(entry);
                parsed.parse(combatants);
                cachedParsedEntries.add(parsed);
            }
        }
    }
    
    private final List<ParsedLogEntry> getFilteredEntries() {
        updateHullIdDeadStatus();
        updateCachedParsedEntries();
        
        final List<ParsedLogEntry> filtered = new ArrayList<>();
        for (ParsedLogEntry entry : cachedParsedEntries) {
            if (entry.shouldSkipDeadAttacker(hullIdDeadStatus)) {
                continue;
            }
            if (entry.type.equals("STATUS") || entry.type.isEmpty()) {
                continue;
            }
            filtered.add(entry);
        }
        return filtered;
    }

    private final void renderBattleLogSprites(float panelX, float panelY,
        float alphaMult
    ) {
        final List<ParsedLogEntry> validEntries = getFilteredEntries();

        final int maxLines = 12;
        final int start = Math.max(0, validEntries.size() - maxLines);

        final float logPanelX = SHIP_COLUMN_WIDTH + MARGIN;
        final float logPanelY = MARGIN + 40f;
        final float logPanelW = CENTER_COLUMN_WIDTH - MARGIN;
        final float logPanelH = PANEL_HEIGHT - MARGIN * 2 - 80f;

        float currentY = logY;
        final float rowSpacing = 4f;

        int lineIndex = 0;
        
        final float leftSpriteX = panelX + logPanelX + LOG_LEFT_MARGIN + LOG_SPRITE_SIZE / 2f;
        final float rightSpriteX = panelX + logPanelX + logPanelW - LOG_LEFT_MARGIN - LOG_SPRITE_SIZE / 2f;

        final float textStartX_twoSprites = logPanelX + LOG_LEFT_MARGIN + LOG_SPRITE_SIZE + LOG_SPRITE_GAP;
        final float textStartX_oneSprite = logPanelX + LOG_LEFT_MARGIN;

        for (int i = start; i < validEntries.size(); i++) {
            final ParsedLogEntry entry = validEntries.get(i);

            final float screenY = panelY + logPanelY + logPanelH - currentY - LOG_LINE_HEIGHT;
            final float spriteCenterY = screenY + LOG_LINE_HEIGHT / 2f;

            final String labelText;
            final Color labelColor;
            final float textX;
            switch (entry.type)
            {
                default -> {
                    labelText = "";
                    labelColor = Color.WHITE;
                    textX = textStartX_twoSprites;
                }
                case "HIT" ->
                {
                    labelText = shortenDamageText(entry.rawEntry);
                    labelColor = entry.isCrit ? BATTLE_HIT_COLOR_CRIT : BATTLE_HIT_COLOR;
                    textX = textStartX_twoSprites;

                    drawBattleLogSpriteWithDead(entry.attackerHullId, leftSpriteX, spriteCenterY, LOG_SPRITE_SIZE, alphaMult, false);
                    drawBattleLogSpriteWithDead(entry.targetHullId, rightSpriteX, spriteCenterY, LOG_SPRITE_SIZE, alphaMult, false);
                }
                case "MISS" ->
                {
                    labelText = shortenDamageText(entry.rawEntry);
                    labelColor = BATTLE_MISS_COLOR;
                    textX = textStartX_twoSprites;

                    drawBattleLogSpriteWithDead(entry.targetHullId, leftSpriteX, spriteCenterY, LOG_SPRITE_SIZE, alphaMult, false);
                    drawBattleLogSpriteWithDead(entry.attackerHullId, rightSpriteX, spriteCenterY, LOG_SPRITE_SIZE, alphaMult, false);
                }
                case "KILL" ->
                {
                    String killText = entry.rawEntry;
                    if (killText.startsWith("[KILL] "))
                    {
                        killText = killText.substring(7);
                    }
                    labelText = shortenDamageText(killText);
                    labelColor = PREFIX_NEGATIVE_COLOR;
                    textX = textStartX_twoSprites;

                    drawBattleLogSpriteWithDead(entry.attackerHullId, leftSpriteX, spriteCenterY, LOG_SPRITE_SIZE, alphaMult, false);
                    drawBattleLogSpriteWithDead(entry.targetHullId, rightSpriteX, spriteCenterY, LOG_SPRITE_SIZE, alphaMult, true);
                }
                case "EVENT" ->
                {
                    String eventText = entry.rawEntry;
                    labelText = shortenDamageText(eventText);
                    labelColor = BATTLE_EVENT_COLOR;
                    textX = textStartX_oneSprite;

                    drawBattleLogSpriteWithDead(entry.attackerHullId, rightSpriteX, spriteCenterY, LOG_SPRITE_SIZE, alphaMult, false);
                }
                case "EVENT_HIT" ->
                {
                    String hitText = entry.rawEntry;
                    if (hitText.startsWith("[HIT] "))
                    {
                        hitText = hitText.substring(6);
                    }
                    labelText = shortenDamageText(hitText);
                    labelColor = BATTLE_EVENT_HIT_COLOR;
                    textX = textStartX_oneSprite;

                    drawBattleLogSpriteWithDead(entry.attackerHullId, rightSpriteX, spriteCenterY, LOG_SPRITE_SIZE, alphaMult, false);
                }
                case "ROUND" ->
                {
                    String roundText = entry.rawEntry;
                    if (roundText.startsWith("[ROUND] "))
                    {
                        roundText = roundText.substring(8);
                    }
                    labelText = "-------- " + roundText + " --------";
                    labelColor = BATTLE_ROUND_COLOR;
                    textX = textStartX_oneSprite;
                }
            }

            if (battleLogTextLabels[lineIndex] != null && !labelText.isEmpty()) {
                battleLogTextLabels[lineIndex].setText(labelText);
                battleLogTextLabels[lineIndex].setColor(labelColor);
                
                final float textY = logPanelY + currentY + (LOG_LINE_HEIGHT - 14f) / 2f;
                battleLogTextLabels[lineIndex].getPosition().inTL(textX, textY);
            }

            currentY += LOG_LINE_HEIGHT + rowSpacing;
            lineIndex++;
        }

        for (int j = lineIndex; j < 12; j++) {
            battleLogTextLabels[j].setOpacity(0f);
        }
    }
    
    private final void drawBattleLogSpriteWithDead(String hullId, float cx,
        float cy, float maxSize, float alphaMult, boolean dead
    ) {
        final SpriteAPI sprite = getShipSprite(hullId);
        if (sprite != null) {
            final float spriteWidth = sprite.getWidth();
            final float spriteHeight = sprite.getHeight();
            final float maxDim = Math.max(spriteWidth, spriteHeight);
            final float scale = maxSize / maxDim;
            final float scaledWidth = spriteWidth * scale;
            final float scaledHeight = spriteHeight * scale;
            
            sprite.setSize(scaledWidth, scaledHeight);
            
            if (dead) {
                sprite.setColor(COLOR_DESTROYED);
                sprite.setAlphaMult(alphaMult * 0.5f);
            } else {
                sprite.setColor(Color.WHITE);
                sprite.setAlphaMult(alphaMult);
            }
            sprite.setNormalBlend();
            sprite.renderAtCenter(cx, cy);
            
            if (dead) {
                final float halfSize = maxSize / 2f - 4f;
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

    private static final String shortenDamageText(String text) {
        if (text == null) return "";
        return text.replace("CRIT damage", "CRIT dmg").replace("damage", "dmg");
    }

    //* Used by TooltipMakerAPI button click listener */
    @Override
    public final void actionPerformed(Object input, Object source) {
        final Object data;
        if (source instanceof ButtonAPI btn) {
            data = btn.getCustomData();
        } else {
            data = null;
        }

        processAction(data);
        updateButtonVisibility();
    }

    private final void processAction(Object data) {
        if (data == ARENA_LEAVE_DATA) {
            if (actionCallback != null) actionCallback.onLeave();
            return;

        } else if (data == ARENA_BET_CANCEL) {
            showingBetAmounts = false;
            selectedChampionIndex = -1;
            addingBetDuringBattle = false;
            return;

        } else if (data == NEXT_ROUND_DATA) {
            if (actionCallback != null) actionCallback.onWatchNextRound();
            return;

        } else if (data == NEXT_GAME_DATA) {
            if (actionCallback != null) actionCallback.onNextGame();
            return;

        } else if (data == ARENA_SKIP_DATA) {
            if (actionCallback != null) actionCallback.onSkipToEnd();
            return;

        } else if (data == ARENA_ADD_BET_DATA) {
            addingBetDuringBattle = true;
            showingBetAmounts = true;
            selectedChampionIndex = -1;
            return;

        } else if (data == ARENA_SUSPEND_DATA) {
            if (actionCallback != null)  actionCallback.onSuspend();
            return;

        } else if (data == ARENA_START_BATTLE_DATA) {
            if (actionCallback != null) actionCallback.onStartBattle();
            return;
        }

        if (data instanceof String strData) {
            if (strData.contains(ARENA_SELECT_DATA)) {
                final int idx = Integer.parseInt(
                    strData.replaceAll(".*?(\\d+)$", "$1"));

                if (idx == -1) {
                    addingBetDuringBattle = false;
                    showingBetAmounts = false;
                    selectedChampionIndex = -1;
                } else {
                    selectedChampionIndex = idx;
                    showingBetAmounts = true;

                    if (!showingBetAmounts && selectedChampionIndex < 0 && 
                        currentRound == 0 && actionCallback != null
                    ) { actionCallback.onSelectChampion(idx); }
                }
                return;
            }

            if (strData.contains(ARENA_BET_DATA)) {
                int amount;
                try {
                    amount = Integer.parseInt(
                    strData.replaceAll(".*?(\\d+)$", "$1"));
                } catch (Exception e) {
                    amount = 0;
                }

                if (selectedChampionIndex >= 0 && actionCallback != null) {
                    actionCallback.onConfirmBet(selectedChampionIndex, amount);
                    showingBetAmounts = false;
                    selectedChampionIndex = -1;
                }
                return;
            }
        }
    }

    public void processInput(List<InputEventAPI> events) {
        for (InputEventAPI event : events) {
            if (event.isConsumed()) continue;
            
            if (event.isKeyDownEvent()) {
                final int key = event.getEventValue();
                
                if (key == Keyboard.KEY_ESCAPE) {
                    event.consume();
                    if (showingBetAmounts) {
                        showingBetAmounts = false;
                        selectedChampionIndex = -1;
                    } else if (actionCallback != null) {
                        actionCallback.onEscape();
                    }
                    return;
                }
            }
        }
    }
        
    private void cacheOdds() {
        if (combatants == null) return;
        
        for (int i = 0; i < combatants.size() && i < 5; i++) {
            final SpiralGladiator ship = combatants.get(i);
            cachedOdds[i] = ship.getCurrentOdds(currentRound);
        }
        oddsCached = true;
    }
    
    public void updateState(
        List<SpiralGladiator> combatants,
        int currentRound,
        int totalBet,
        List<BetInfo> bets,
        List<String> battleLog
    ) {
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
        
        updateLabels();
    }
    
    public void setBattleEnded(int winnerIndex, int totalReward, int finalRound) {
        this.battleEnded = true;
        this.winnerIndex = winnerIndex;
        this.totalReward = totalReward;
        this.currentRound = finalRound;
        this.showingBetAmounts = false;
        this.addingBetDuringBattle = false;
        this.selectedChampionIndex = -1;
        
        oddsCached = false;
        cacheOdds();
        
        updateLabels();
    }
    
    public void setBattleEnded(int winnerIndex, int totalReward,
        RewardBreakdown breakdown, int finalRound
    ) {
        this.battleEnded = true;
        this.winnerIndex = winnerIndex;
        this.totalReward = totalReward;
        this.rewardBreakdown = breakdown;
        this.currentRound = finalRound;
        this.showingBetAmounts = false;
        this.addingBetDuringBattle = false;
        this.selectedChampionIndex = -1;
        
        oddsCached = false;
        cacheOdds();
        
        updateLabels();
    }
    
    public final void resetForNewMatch(
        List<SpiralGladiator> combatants,
        int currentRound,
        int totalBet,
        List<BetInfo> bets,
        List<String> battleLog
    ) {
        this.battleEnded = false;
        this.winnerIndex = -1;
        this.totalReward = 0;
        this.rewardBreakdown = null;
        this.showingBetAmounts = false;
        this.addingBetDuringBattle = false;
        this.selectedChampionIndex = -1;
        
        this.combatants = combatants;
        this.currentRound = currentRound;
        this.totalBet = totalBet;
        this.bets = bets;
        this.battleLog = battleLog;
        
        shipStateInitialized = false;
        for (int i = 0; i < 5; i++) {
            lastShipHp[i] = -1;
            lastShipMaxHp[i] = -1;
            lastShipDead[i] = false;
            lastShipOdds[i] = -1;
            lastShipBetCount[i] = -1;
            lastShipHullIds[i] = null;
        }
        
        lastBattleLogSize = -1;
        cachedParsedEntries.clear();
        hullIdDeadStatus.clear();
        
        oddsCached = false;
        cacheOdds();
        
        updateLabels();
    }
    
    private static final String getPositionString(int finalPosition) {
        switch (finalPosition) {
            case 0: return "1st";
            case 1: return "2nd";
            case 2: return "3rd";
            case 3: return "4th";
            case 4: return "5th";
            default: return (finalPosition + 1) + "th";
        }
    }
    
    public boolean isReadyToClose() {
        return readyToClose;
    }
}