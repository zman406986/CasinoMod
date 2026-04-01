package data.scripts.casino.arena;

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
import com.fs.starfarer.api.ui.TooltipMakerAPI.ActionListenerDelegate;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.Misc;

import data.scripts.casino.CasinoConfig;
import data.scripts.casino.Strings;
import data.scripts.casino.arena.SpiralAbyssArena.SpiralGladiator;
import data.scripts.casino.interaction.ArenaHandler.BetInfo;
import data.scripts.casino.interaction.ArenaHandler.BetValidationResult;

import static data.scripts.casino.interaction.ArenaHandler.validateBet;

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
    private static final String ARENA_CONFIRM_DIALOG = "arena_confirm_overdraft";
    private static final String ARENA_CANCEL_OVERDRAFT = "arena_cancel_overdraft";
    private static final String ARENA_DISMISS_ERROR = "arena_dismiss_error";

    @SuppressWarnings("unused")
    private static class ParsedLogEntry {
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
            } else if (rawEntry.startsWith("[CRIT]")) {
                type = "HIT";
                isCrit = true;
                String content = rawEntry.substring(6).trim();
                parseAttackLine(content, combatants);
            } else if (rawEntry.startsWith("[MISS]")) {
                type = "MISS";
                String content = rawEntry.substring(6).trim();
                parseMissLine(content, combatants);
            } else if (rawEntry.startsWith("[HIT]")) {
                String content = rawEntry.substring(5).trim();
                if (content.contains("takes")) {
                    type = "EVENT_HIT";
                    parseEventHitLine(content, combatants);
                } else {
                    type = "HIT";
                    parseAttackLine(content, combatants);
                }
            } else if (rawEntry.contains("--- SHIP STATUS ---") || rawEntry.contains("HP:")) {
                type = "STATUS";
            } else {
                type = "";
            }
        }
        
        private static final Pattern DAMAGE_PATTERN = Pattern.compile("(\\d+)");
        private static final Pattern EVENT_DAMAGE_PATTERN = Pattern.compile("\\(-?(\\d+)");
        
        private int extractDamage(String line, Pattern pattern) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
            return 0;
        }
        
        private SpiralGladiator[] findShipsByPosition(String line, List<SpiralGladiator> combatants) {
            if (combatants == null || line == null) return new SpiralGladiator[2];
            
            int firstPos = Integer.MAX_VALUE;
            int secondPos = Integer.MAX_VALUE;
            SpiralGladiator firstShip = null;
            SpiralGladiator secondShip = null;
            
            for (SpiralGladiator ship : combatants) {
                int pos = line.indexOf(ship.shortName);
                if (pos >= 0) {
                    if (pos < firstPos) {
                        secondPos = firstPos;
                        secondShip = firstShip;
                        firstPos = pos;
                        firstShip = ship;
                    } else if (pos < secondPos && !ship.hullId.equals(firstShip.hullId)) {
                        secondPos = pos;
                        secondShip = ship;
                    }
                }
            }
            return new SpiralGladiator[] { firstShip, secondShip };
        }
        
        void parseAttackLine(String line, List<SpiralGladiator> combatants) {
            SpiralGladiator[] ships = findShipsByPosition(line, combatants);
            if (ships[0] != null) attackerHullId = ships[0].hullId;
            if (ships[1] != null) targetHullId = ships[1].hullId;
            damage = extractDamage(line, DAMAGE_PATTERN);
        }
        
        void parseKillLine(String line, List<SpiralGladiator> combatants) {
            SpiralGladiator[] ships = findShipsByPosition(line, combatants);
            if (ships[0] != null) attackerHullId = ships[0].hullId;
            if (ships[1] != null) targetHullId = ships[1].hullId;
        }
        
        void parseMissLine(String line, List<SpiralGladiator> combatants) {
            SpiralGladiator[] ships = findShipsByPosition(line, combatants);
            if (ships[0] != null) targetHullId = ships[0].hullId;
            if (ships[1] != null) attackerHullId = ships[1].hullId;
        }
        
        void parseEventLine(String line, List<SpiralGladiator> combatants) {
            description = line;
            
            if (combatants != null) {
                for (SpiralGladiator ship : combatants) {
                    if (line.contains(ship.shortName)) {
                        attackerHullId = ship.hullId;
                        break;
                    }
                }
            }
            
            damage = extractDamage(line, EVENT_DAMAGE_PATTERN);
        }
        
        void parseEventHitLine(String line, List<SpiralGladiator> combatants) {
            if (combatants != null) {
                for (SpiralGladiator ship : combatants) {
                    if (line.contains(ship.shortName)) {
                        attackerHullId = ship.hullId;
                        break;
                    }
                }
            }
            
            damage = extractDamage(line, DAMAGE_PATTERN);
        }
    }

    private final ArenaActionCallback actionCallback;
    private CustomPanelAPI panel;
    
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
    
    private static final float PANEL_WIDTH = 1000f;
    private static final float PANEL_HEIGHT = 700f;
    
    private static final float SHIP_COLUMN_WIDTH = 300f;
    private static final float CENTER_COLUMN_WIDTH = 450f;

    private static final float BOX_WIDTH = 150f;
    private static final float BOX_HEIGHT = 65f;
    private static final float BOX_SPACING = 3f;
    private static final float ENTRY_SPACING = 10f;
    
    private static final float MARGIN = 20f;
    
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
    private static final Color NET_NEGATIVE_COLOR = new Color(255, 100, 50);
    private static final Color KILL_COLOR = new Color(255, 150, 50);
    
    private static final Color BATTLE_HIT_COLOR_CRIT = new Color(255, 100, 100);
    private static final Color BATTLE_HIT_COLOR = new Color(255, 255, 100);
    private static final Color BATTLE_MISS_COLOR = new Color(150, 150, 150);
    private static final Color BATTLE_EVENT_COLOR = new Color(100, 200, 255);
    private static final Color BATTLE_EVENT_HIT_COLOR = new Color(255, 200, 50);
    private static final Color BATTLE_ROUND_COLOR = new Color(180, 180, 200);
    private static final Color DISABLED_BTN_COLOR = new Color(255, 200, 0);
    
    private static final Color COLOR_BG_DARK = new Color(15, 15, 20);
    private static final Color COLOR_SIDEBAR = new Color(25, 25, 35);
    private static final Color COLOR_TINT_DEAD = new Color(150, 50, 50);
    private static final Color COLOR_TINT_DAMAGED = new Color(255, 200, 100);
    
    private boolean isPrefixPositive(String prefix) {
        if (prefix == null) return true;
        return Strings.getList("arena_prefixes.positive").contains(prefix);
    }
    
    private boolean isAffixPositive(String affix) {
        if (affix == null) return true;
        return Strings.getList("arena_affixes.positive").contains(affix);
    }
    
    private Color getPrefixColor(String prefix) {
        return isPrefixPositive(prefix) ? PREFIX_POSITIVE_COLOR : PREFIX_NEGATIVE_COLOR;
    }
    
    private Color getAffixColor(String affix) {
        return isAffixPositive(affix) ? AFFIX_POSITIVE_COLOR : AFFIX_NEGATIVE_COLOR;
    }
    
    private void applyShipNameHighlighting(LabelAPI label, SpiralGladiator ship) {
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
    
    private LabelAPI roundLabel;
    private LabelAPI betLabel;
    
    private final LabelAPI[] shipNameLabels = new LabelAPI[5];
    private final LabelAPI[] shipHpLabels = new LabelAPI[5];
    private final LabelAPI[] shipOddsLabels = new LabelAPI[5];
    
    private final LabelAPI[] battleLogTextLabels = new LabelAPI[12];
    
    private static final float LOG_SPRITE_SIZE = 28f;
    private static final float LOG_LINE_HEIGHT = 32f;
    private static final float LOG_SPRITE_GAP = 4f;
    private static final float LOG_LEFT_MARGIN = 5f;
    
    private LabelAPI resultLabel;
    
    private static final int MAX_REWARD_LINES = 25;
    private final LabelAPI[] rewardBreakdownLabels = new LabelAPI[MAX_REWARD_LINES];
    
    private LabelAPI instructionLabel;

    private ButtonAPI watchNextButton;
    private ButtonAPI nextGameButton;
    private ButtonAPI skipToEndButton;
    private ButtonAPI addBetButton;
    private ButtonAPI suspendButton;
    private ButtonAPI leaveButton;
    private ButtonAPI startBattleButton;
    
    private final List<ButtonAPI> championSelectButtons = new ArrayList<>();
    private final List<ButtonAPI> betAmountButtons = new ArrayList<>();
    
    private boolean buttonsCreated = false;
    
    private static final int[] BET_AMOUNTS = {100, 500, 1000, 2000, 5000};
    
    private final Map<String, SpriteAPI> spriteCache = new HashMap<>();
    private static final float SPRITE_SCALE = 0.75f;
    
    private final float[] cachedOdds = new float[5];
    private boolean oddsCached = false;
    
    private boolean showingBetAmounts = false;
    private boolean addingBetDuringBattle = false;
    
    private boolean showingOverdraftConfirmation = false;
    private boolean showingErrorMessage = false;
    private int pendingBetAmount = 0;
    private int pendingChampionIndex = -1;
    private String currentErrorMessage = "";
    private String currentOverdraftMessage = "";
    
    private LabelAPI balanceLabel;
    private LabelAPI messageLabel;
    
    private ButtonAPI confirmOverdraftButton;
    private ButtonAPI cancelOverdraftButton;
    private ButtonAPI dismissErrorButton;
    
    private float logY;

    private final List<ParsedLogEntry> cachedParsedEntries = new ArrayList<>();
    private int lastBattleLogSize = -1;
    
    private int lastCurrentRound = -1;
    private int lastTotalBet = -1;

    private final int[] lastShipHp = new int[5];
    private final int[] lastShipMaxHp = new int[5];
    private final boolean[] lastShipDead = new boolean[5];
    private final float[] lastShipOdds = new float[5];
    private final int[] lastShipBetCount = new int[5];
    private final String[] lastShipHullIds = new String[5];
    private boolean shipStateInitialized = false;

    // Label caching (prevent redundant per-frame updates)
    private int lastBalance = Integer.MIN_VALUE;
    private int lastAvailableCredit = Integer.MIN_VALUE;
    private int lastCreditCeiling = Integer.MIN_VALUE;
    private boolean lastIsVIP = false;
    private String lastInstructionText = null;
    private boolean rewardBreakdownCached = false;

    // Battle Log Animation State
    private float logAnimationTimer = 0f;
    private int displayedLogIndex = 0;
    private boolean isAnimating = false;
    private final List<ParsedLogEntry> pendingEntries = new ArrayList<>();
    private boolean skipRequested = false;

    // Sprite Animation State (sidebar ships)
    private String currentAttackerHullId = null;
    private String currentTargetHullId = null;
    private float spriteAnimTimer = 0f;
    private float attackerNudgeOffset = 0f;
    private boolean targetFlashState = false;

    // HP Animation State
    private final int[] animatedHp = new int[5];
    private final int[] targetAnimatedHp = new int[5];
    private final int[] prevAnimatedHp = new int[5];
    private final float[] hpAnimTimer = new float[5];
    private final boolean[] hpAnimating = new boolean[5];

    // Kill Tracking
    private final Set<String> killedHullIds = new HashSet<>();
    private final Set<String> fadeOutHullIds = new HashSet<>();
    private final float[] fadeOutAlpha = new float[5];
    
    private int getTotalBetOnShip(int shipIndex) {
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
    
    private boolean canBetMoreOnShip(int shipIndex) {
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
        void onNextGame();
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
            public List<BetDetail> betDetails = new ArrayList<>();
            
            public static class BetDetail {
                public int roundPlaced;
                public float multiplier;
                public int amount;
                
                public BetDetail(int roundPlaced, float multiplier, int amount) {
                    this.roundPlaced = roundPlaced;
                    this.multiplier = multiplier;
                    this.amount = amount;
                }
            }
            
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
    
    private boolean isShipEffectivelyDead(SpiralGladiator ship) {
        if (ship == null) return false;
        return killedHullIds.contains(ship.hullId) || (!isAnimating && ship.isDead);
    }
    
    private SpriteAPI getShipSprite(String hullId) {
        if (hullId == null || hullId.isEmpty()) return null;
        
        if (spriteCache.containsKey(hullId)) {
            return spriteCache.get(hullId);
        }
        
        try {
            ShipHullSpecAPI spec = settings.getHullSpec(hullId);
            if (spec == null) {
                spriteCache.put(hullId, null);
                return null;
            }
            
            String spriteName = spec.getSpriteName();
            if (spriteName == null || spriteName.isEmpty()) {
                spriteCache.put(hullId, null);
                return null;
            }
            
            SpriteAPI sprite = settings.getSprite(spriteName);
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

    public void advance(float amount) {
        if (isAnimating && !pendingEntries.isEmpty()) {
            logAnimationTimer += amount;

            boolean shouldAdvance = skipRequested || logAnimationTimer >= CasinoConfig.ARENA_LOG_LINE_DELAY;

            if (shouldAdvance) {
                logAnimationTimer = 0f;

                if (skipRequested) {
                    skipRequested = false;
                    while (displayedLogIndex < pendingEntries.size()) {
                        ParsedLogEntry current = pendingEntries.get(displayedLogIndex);
                        triggerEntryAnimation(current, false);
                        displayedLogIndex++;
                    }
                    currentAttackerHullId = null;
                    currentTargetHullId = null;
                    spriteAnimTimer = 0f;
                    targetFlashState = false;
                } else {
                    if (displayedLogIndex < pendingEntries.size()) {
                        ParsedLogEntry current = pendingEntries.get(displayedLogIndex);
                        triggerEntryAnimation(current, true);
                        displayedLogIndex++;
                    }
                }

                if (displayedLogIndex >= pendingEntries.size()) {
                    isAnimating = false;
                    finalizeAnimationState();
                }
            }
        }

        updateSpriteAnimations(amount);
        updateHpAnimations(amount);
    }

    private void updateSpriteAnimations(float amount) {
        if (spriteAnimTimer > 0) {
            spriteAnimTimer -= amount;
            float progress = 1f - (spriteAnimTimer / CasinoConfig.ARENA_SPRITE_NUDGE_DURATION);
            attackerNudgeOffset = (float) Math.sin(progress * Math.PI) * CasinoConfig.ARENA_SPRITE_NUDGE_AMOUNT;
            
            // Flash is derived from nudge timer - flash twice during the animation
            // Using sine wave: positive = flash on, negative = flash off
            float flashPhase = (float) Math.sin(progress * Math.PI * 4); // 2 full cycles
            targetFlashState = flashPhase > 0;
        } else {
            attackerNudgeOffset = 0f;
            targetFlashState = false;
        }

        for (int i = 0; i < fadeOutAlpha.length; i++) {
            if (fadeOutAlpha[i] > 0.5f) {
                fadeOutAlpha[i] = Math.max(0.5f, fadeOutAlpha[i] - amount * 2f);
            }
        }
    }

    private void updateHpAnimations(float amount) {
        for (int i = 0; i < hpAnimTimer.length; i++) {
            if (hpAnimTimer[i] > 0) {
                hpAnimTimer[i] -= amount;
                float progress = 1f - (hpAnimTimer[i] / CasinoConfig.ARENA_HP_ANIM_DURATION);
                int startHp = prevAnimatedHp[i];
                int finalHp = targetAnimatedHp[i];
                animatedHp[i] = (int) (startHp + (finalHp - startHp) * Math.min(1f, progress));
            }
            hpAnimating[i] = hpAnimTimer[i] > 0;
        }
    }

    private void triggerEntryAnimation(ParsedLogEntry entry, boolean animate) {
        switch (entry.type) {
            case "HIT" -> {
                if (animate) {
                    currentAttackerHullId = entry.attackerHullId;
                    currentTargetHullId = entry.targetHullId;
                    spriteAnimTimer = CasinoConfig.ARENA_SPRITE_NUDGE_DURATION;
                }
                animateHpReduction(entry.targetHullId, entry.damage, animate);
            }
            case "MISS" -> {
                if (animate) {
                    currentAttackerHullId = entry.attackerHullId;
                    currentTargetHullId = entry.targetHullId;
                    spriteAnimTimer = CasinoConfig.ARENA_SPRITE_NUDGE_DURATION;
                }
            }
            case "KILL" -> {
                if (animate) {
                    currentAttackerHullId = entry.attackerHullId;
                    currentTargetHullId = entry.targetHullId;
                    spriteAnimTimer = CasinoConfig.ARENA_SPRITE_NUDGE_DURATION;
                }

                killedHullIds.add(entry.targetHullId);
                fadeOutHullIds.add(entry.targetHullId);
                int targetIdx = findCombatantIndex(entry.targetHullId);
                if (targetIdx >= 0) {
                    fadeOutAlpha[targetIdx] = animate ? 1.0f : 0.5f;
                }
            }
            case "EVENT", "EVENT_HIT" -> {
                if (animate) {
                    currentAttackerHullId = entry.attackerHullId;
                    currentTargetHullId = null;
                    spriteAnimTimer = CasinoConfig.ARENA_SPRITE_NUDGE_DURATION;
                }
                if (entry.damage > 0) {
                    animateHpReduction(entry.attackerHullId, entry.damage, animate);
                }
            }
            case "ROUND" -> {
                if (animate) {
                    currentAttackerHullId = null;
                    currentTargetHullId = null;
                }
            }
        }
    }

    private void animateHpReduction(String hullId, int damage, boolean animate) {
        int idx = findCombatantIndex(hullId);
        if (idx >= 0 && idx < hpAnimTimer.length) {
            if (animate) {
                prevAnimatedHp[idx] = animatedHp[idx];
                targetAnimatedHp[idx] = Math.max(0, animatedHp[idx] - damage);
                hpAnimTimer[idx] = CasinoConfig.ARENA_HP_ANIM_DURATION;
            } else {
                animatedHp[idx] = Math.max(0, animatedHp[idx] - damage);
                targetAnimatedHp[idx] = animatedHp[idx];
            }
        }
    }

    private int findCombatantIndex(String hullId) {
        if (combatants == null || hullId == null) return -1;
        for (int i = 0; i < combatants.size(); i++) {
            if (hullId.equals(combatants.get(i).hullId)) {
                return i;
            }
        }
        return -1;
    }

    private void finalizeAnimationState() {
        if (combatants != null) {
            for (int i = 0; i < combatants.size(); i++) {
                animatedHp[i] = combatants.get(i).hp;
                targetAnimatedHp[i] = combatants.get(i).hp;
                if (combatants.get(i).isDead) {
                    killedHullIds.add(combatants.get(i).hullId);
                    fadeOutHullIds.add(combatants.get(i).hullId);
                    fadeOutAlpha[i] = 0.5f;
                }
            }
        }
    }

    public void startLogAnimation(List<String> newLogEntries) {
        pendingEntries.clear();
        if (newLogEntries != null) {
            for (String entry : newLogEntries) {
                ParsedLogEntry parsed = new ParsedLogEntry(entry);
                parsed.parse(combatants);
                if (!parsed.type.equals("STATUS") && !parsed.type.isEmpty()) {
                    pendingEntries.add(parsed);
                }
            }
        }

        Map<String, Integer> totalDamagePerShip = new HashMap<>();
        for (ParsedLogEntry entry : pendingEntries) {
            if (entry.damage > 0) {
                if ("HIT".equals(entry.type) && entry.targetHullId != null) {
                    totalDamagePerShip.merge(entry.targetHullId, entry.damage, Integer::sum);
                } else if (("EVENT".equals(entry.type) || "EVENT_HIT".equals(entry.type)) && entry.attackerHullId != null) {
                    totalDamagePerShip.merge(entry.attackerHullId, entry.damage, Integer::sum);
                }
            }
        }

        displayedLogIndex = 0;
        logAnimationTimer = 0f;
        isAnimating = !pendingEntries.isEmpty();
        skipRequested = false;

        currentAttackerHullId = null;
        currentTargetHullId = null;
        spriteAnimTimer = 0f;
        attackerNudgeOffset = 0f;
        targetFlashState = false;

        // Note: killedHullIds and fadeOutHullIds are NOT cleared here - they persist across rounds
        // and are only reset in resetAnimationState() when a new match starts

        if (combatants != null) {
            for (int i = 0; i < combatants.size(); i++) {
                String hullId = combatants.get(i).hullId;
                int finalHp = combatants.get(i).hp;
                int damageThisRound = totalDamagePerShip.getOrDefault(hullId, 0);
                int startingHp = Math.min(combatants.get(i).maxHp, finalHp + damageThisRound);

                animatedHp[i] = startingHp;
                prevAnimatedHp[i] = startingHp;
                targetAnimatedHp[i] = startingHp;
                hpAnimTimer[i] = 0f;
            }
        }
    }

    private void createUIElements() {
        if (panel == null) return;
        
        createRoundLabel();
        createBetLabel();
        createBalanceLabel();
        createInstructionLabel();
        createMessageLabel();
        createShipLabels();
        createBattleLogPanel();
        createResultLabel();
        createRewardBreakdownLabels();
        createAllButtonsOnce();
    }
    
    private void createBattleLogPanel() {
        if (panel == null) return;
        
        final float logPanelW = CENTER_COLUMN_WIDTH - MARGIN;
        
        logY = LOG_LINE_HEIGHT;
        float logW = logPanelW - LOG_LEFT_MARGIN * 2;
        
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
    
    private void createAllButtonsOnce() {
        if (panel == null || buttonsCreated) return;
        
        final TooltipMakerAPI btnTp = panel.createUIElement(PANEL_WIDTH, PANEL_HEIGHT, false);
        btnTp.setActionListenerDelegate(this);
        panel.addUIElement(btnTp).inTL(0, 0);
        
        final float startY = MARGIN + 10f;
        final float NAME_HEIGHT = 16f;
        final float HP_HEIGHT = 11f;
        final float ODDS_HEIGHT = 30f;
        final float totalItemHeight = BOX_HEIGHT + BOX_SPACING + NAME_HEIGHT + HP_HEIGHT + ODDS_HEIGHT + ENTRY_SPACING;
        
        for (int i = 0; i < 5; i++) {
            final float shipY = startY + i * totalItemHeight;
            final float buttonY = shipY + (BOX_HEIGHT - CHAMP_BUTTON_HEIGHT) / 2f;
            
            final ButtonAPI btn = btnTp.addButton(
                Strings.get("arena_panel.select"), 
                ARENA_SELECT_DATA + i, 
                CHAMP_BUTTON_WIDTH, CHAMP_BUTTON_HEIGHT, 0f
            );
            btn.setQuickMode(true);
            btn.getPosition().inTL(CHAMP_BUTTON_X, buttonY);
            btn.setOpacity(0f);
            championSelectButtons.add(btn);
        }
        
        // Cancel button (index 0 in betAmountButtons)
        final ButtonAPI cancelBtn = btnTp.addButton(
            Strings.get("common.cancel"), 
            ARENA_BET_CANCEL, 
            BUTTON_WIDTH, BUTTON_HEIGHT, 0f
        );
        cancelBtn.setQuickMode(true);
        cancelBtn.setShortcut(Keyboard.KEY_ESCAPE, false);
        cancelBtn.getPosition().inTL(leftX, bottomY);
        cancelBtn.setOpacity(0f);
        betAmountButtons.add(cancelBtn);
        
        // Bet amount buttons (indices 1-5)
        for (int i = 0; i < BET_AMOUNTS.length; i++) {
            final int amt = BET_AMOUNTS[i];
            final float btnX = leftX + i * (BUTTON_WIDTH + BUTTON_SPACING);
            
            final ButtonAPI btn = btnTp.addButton(
                Strings.format("arena_panel.sg", amt), 
                ARENA_BET_DATA + amt, 
                BUTTON_WIDTH, BUTTON_HEIGHT, 0f
            );
            btn.getPosition().inTL(btnX, bottomY - BUTTON_HEIGHT - BUTTON_SPACING);
            btn.setOpacity(0f);
            betAmountButtons.add(btn);
        }
        
        leaveButton = btnTp.addButton(Strings.get("common.leave"), ARENA_LEAVE_DATA, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        leaveButton.setQuickMode(true);
        leaveButton.setShortcut(Keyboard.KEY_ESCAPE, false);
        leaveButton.getPosition().inTL(leftX, bottomY);
        leaveButton.setOpacity(0f);
        
        suspendButton = btnTp.addButton(Strings.get("arena_panel.suspend"), ARENA_SUSPEND_DATA, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        suspendButton.setQuickMode(true);
        suspendButton.getPosition().inTL(leftX + BUTTON_WIDTH + BUTTON_SPACING, bottomY);
        suspendButton.setOpacity(0f);
        
        addBetButton = btnTp.addButton(Strings.get("arena_panel.add_bet"), ARENA_ADD_BET_DATA, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        addBetButton.setQuickMode(true);
        addBetButton.getPosition().inTL(leftX + (BUTTON_WIDTH + BUTTON_SPACING) * 2, bottomY);
        addBetButton.setOpacity(0f);
        
        skipToEndButton = btnTp.addButton(Strings.get("arena_panel.skip_to_end"), ARENA_SKIP_DATA, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        skipToEndButton.setQuickMode(true);
        skipToEndButton.getPosition().inTL(leftX + (BUTTON_WIDTH + BUTTON_SPACING) * 3, bottomY);
        skipToEndButton.setOpacity(0f);
        
        watchNextButton = btnTp.addButton(Strings.get("arena_panel.next_round"), NEXT_ROUND_DATA, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        watchNextButton.setQuickMode(true);
        watchNextButton.getPosition().inTL(leftX + (BUTTON_WIDTH + BUTTON_SPACING) * 4, bottomY);
        watchNextButton.setOpacity(0f);
        
        nextGameButton = btnTp.addButton(
            Strings.get("arena_panel.next_game"), 
            NEXT_GAME_DATA, 
            Color.BLACK, DISABLED_BTN_COLOR, 
            BUTTON_WIDTH, BUTTON_HEIGHT, 0f
        );
        nextGameButton.setQuickMode(true);
        nextGameButton.getPosition().inTL(leftX + BUTTON_WIDTH + BUTTON_SPACING, bottomY);
        nextGameButton.setOpacity(0f);
        
        startBattleButton = btnTp.addButton(Strings.get("arena_panel.start_battle"), ARENA_START_BATTLE_DATA, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        startBattleButton.setQuickMode(true);
        startBattleButton.getPosition().inTL(leftX + BUTTON_WIDTH + BUTTON_SPACING, bottomY);
        startBattleButton.setOpacity(0f);
        
        confirmOverdraftButton = btnTp.addButton(
            Strings.get("arena_panel.confirm_overdraft"), 
            ARENA_CONFIRM_DIALOG, 
            BUTTON_WIDTH + 30f, BUTTON_HEIGHT, 0f
        );
        confirmOverdraftButton.setQuickMode(true);
        confirmOverdraftButton.getPosition().inTL(leftX, bottomY);
        confirmOverdraftButton.setOpacity(0f);
        
        cancelOverdraftButton = btnTp.addButton(Strings.get("common.cancel"), ARENA_CANCEL_OVERDRAFT, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        cancelOverdraftButton.setQuickMode(true);
        cancelOverdraftButton.setShortcut(Keyboard.KEY_ESCAPE, false);
        cancelOverdraftButton.getPosition().inTL(leftX + BUTTON_WIDTH + 40f + BUTTON_SPACING, bottomY);
        cancelOverdraftButton.setOpacity(0f);
        
        dismissErrorButton = btnTp.addButton(Strings.get("common.ok"), ARENA_DISMISS_ERROR, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
        dismissErrorButton.setQuickMode(true);
        dismissErrorButton.setShortcut(Keyboard.KEY_ESCAPE, false);
        dismissErrorButton.getPosition().inTL((PANEL_WIDTH - BUTTON_WIDTH) / 2f, bottomY);
        dismissErrorButton.setOpacity(0f);
        
        buttonsCreated = true;
    }
    
    private void updateButtonVisibility() {
        final boolean showChampionSelect =
            (currentRound == 0 && !showingBetAmounts && !battleEnded && !showingOverdraftConfirmation && !showingErrorMessage) ||
            (currentRound > 0 && showingBetAmounts && addingBetDuringBattle && selectedChampionIndex < 0 && !battleEnded && !showingOverdraftConfirmation && !showingErrorMessage);
        final boolean showChampionAsBet = currentRound > 0 && showingBetAmounts && addingBetDuringBattle && selectedChampionIndex < 0 && !showingOverdraftConfirmation && !showingErrorMessage;
        final boolean showBetAmounts = showingBetAmounts && selectedChampionIndex >= 0 && !battleEnded && !showingOverdraftConfirmation && !showingErrorMessage;
        
        for (int i = 0; i < championSelectButtons.size(); i++) {
            final ButtonAPI btn = championSelectButtons.get(i);
            if (btn == null) continue;

            if (i < 5) {
                final boolean btnVis = (showChampionSelect || showChampionAsBet) && canBetMoreOnShip(i);
                btn.setOpacity(btnVis ? 1f : 0f);
            } else {
                btn.setOpacity(showChampionSelect ? 1f : 0f);
            }
        }
        
        for (int i = 0; i < betAmountButtons.size(); i++) {
            final ButtonAPI btn = betAmountButtons.get(i);
            if (btn == null) continue;

            if (showBetAmounts) {
                if (i == 0) {
                    btn.setOpacity(1f);
                } else {
                    final int betAmount = BET_AMOUNTS[i - 1];
                    final int currentBetOnShip = getTotalBetOnShip(selectedChampionIndex);
                    btn.setOpacity(currentBetOnShip + betAmount > CasinoConfig.ARENA_MAX_BET_PER_CHAMPION ? 0f : 1f);
                }
            } else {
                btn.setOpacity(0f);
            }
        }
        
        
        final boolean showLeave = battleEnded || (!showingOverdraftConfirmation && !showingErrorMessage && !showingBetAmounts);
        leaveButton.setOpacity(showLeave ? 1f : 0f);
        
        suspendButton.setOpacity(!battleEnded && currentRound > 0 && !showingBetAmounts && !showingOverdraftConfirmation && !showingErrorMessage ? 1f : 0f);

        addBetButton.setOpacity(currentRound > 0 && !showingBetAmounts && !battleEnded && !showingOverdraftConfirmation && !showingErrorMessage ? 1f : 0f);

        skipToEndButton.setOpacity(currentRound > 0 && !showingBetAmounts && !battleEnded && !showingOverdraftConfirmation && !showingErrorMessage ? 1f : 0f);

        watchNextButton.setOpacity(currentRound > 0 && !showingBetAmounts && !battleEnded && !showingOverdraftConfirmation && !showingErrorMessage ? 1f : 0f);

        nextGameButton.setOpacity(battleEnded ? 1f : 0f);

        startBattleButton.setOpacity(currentRound == 0 && !showingBetAmounts && totalBet > 0 && !showingOverdraftConfirmation && !showingErrorMessage ? 1f : 0f);

        confirmOverdraftButton.setOpacity(showingOverdraftConfirmation ? 1f : 0f);

        cancelOverdraftButton.setOpacity(showingOverdraftConfirmation ? 1f : 0f);

        dismissErrorButton.setOpacity(showingErrorMessage ? 1f : 0f);

        messageLabel.setOpacity(showingOverdraftConfirmation || showingErrorMessage ? 1f : 0f);
    }
    
    private void createRoundLabel() {
        if (panel == null) return;
        
        final float LABEL_WIDTH = 150f;
        final float LABEL_HEIGHT = 30f;
        
        final float x = SHIP_COLUMN_WIDTH + MARGIN;

        roundLabel = settings.createLabel(Strings.format("arena_panel.round", 0), Fonts.DEFAULT_SMALL);
        roundLabel.setColor(Color.CYAN);
        roundLabel.setAlignment(Alignment.LMID);
        panel.addComponent((UIComponentAPI) roundLabel).inTL(x, MARGIN)
            .setSize(LABEL_WIDTH, LABEL_HEIGHT);
    }
    
    private void createBetLabel() {
        if (panel == null) return;
        
        final float LABEL_WIDTH = 200f;
        final float LABEL_HEIGHT = 30f;
        
        final float x = SHIP_COLUMN_WIDTH + MARGIN + 160f;

        betLabel = settings.createLabel(Strings.format("arena_panel.total_bet", 0), Fonts.DEFAULT_SMALL);
        betLabel.setColor(Color.YELLOW);
        betLabel.setAlignment(Alignment.LMID);
        panel.addComponent((UIComponentAPI) betLabel).inTL(x, MARGIN)
            .setSize(LABEL_WIDTH, LABEL_HEIGHT);
    }
    
    private void createInstructionLabel() {
        if (panel == null) return;
        
        final float LABEL_WIDTH = 250f;
        final float LABEL_HEIGHT = 30f;
        
        final float x = SHIP_COLUMN_WIDTH + MARGIN + 360f;

        instructionLabel = settings.createLabel(Strings.get("arena_panel.select_champion_bet"), Fonts.DEFAULT_SMALL);
        instructionLabel.setColor(Color.WHITE);
        instructionLabel.setAlignment(Alignment.LMID);
        panel.addComponent((UIComponentAPI) instructionLabel).inTL(x, MARGIN)
            .setSize(LABEL_WIDTH, LABEL_HEIGHT);
    }
    
    private void createBalanceLabel() {
        if (panel == null) return;
        
        final float LABEL_WIDTH = 400f;
        final float LABEL_HEIGHT = 20f;
        
        final float x = SHIP_COLUMN_WIDTH + MARGIN;
        final float y = MARGIN + 35f;

        balanceLabel = settings.createLabel("", Fonts.DEFAULT_SMALL);
        balanceLabel.setAlignment(Alignment.LMID);
        panel.addComponent((UIComponentAPI) balanceLabel).inTL(x, y)
            .setSize(LABEL_WIDTH, LABEL_HEIGHT);
        
        updateBalanceLabel();
    }
    
    private void createMessageLabel() {
        if (panel == null) return;
        
        final float LABEL_WIDTH = 400f;
        final float LABEL_HEIGHT = 50f;
        
        final float x = (PANEL_WIDTH - LABEL_WIDTH) / 2f;
        final float y = bottomY - LABEL_HEIGHT - BUTTON_HEIGHT - BUTTON_SPACING * 2;

        messageLabel = settings.createLabel("", Fonts.DEFAULT_SMALL);
        messageLabel.setAlignment(Alignment.MID);
        panel.addComponent((UIComponentAPI) messageLabel).inTL(x, y)
            .setSize(LABEL_WIDTH, LABEL_HEIGHT);
    }
    
    private void updateBalanceLabel() {
        if (balanceLabel == null) return;
        
        int balance = CasinoVIPManager.getBalance();
        int availableCredit = CasinoVIPManager.getAvailableCredit();
        int creditCeiling = CasinoVIPManager.getCreditCeiling();
        boolean isVIP = CasinoVIPManager.isOverdraftAvailable();
        
        if (balance == lastBalance && availableCredit == lastAvailableCredit 
            && creditCeiling == lastCreditCeiling && isVIP == lastIsVIP) {
            return;
        }
        lastBalance = balance;
        lastAvailableCredit = availableCredit;
        lastCreditCeiling = creditCeiling;
        lastIsVIP = isVIP;
        
        StringBuilder sb = new StringBuilder();
        Color balanceColor;
        
        if (balance >= 0) {
            sb.append(Strings.format("arena_panel.balance", balance));
            balanceColor = Color.GREEN;
        } else {
            sb.append(Strings.format("arena_panel.balance_debt", balance));
            balanceColor = Color.RED;
        }
        
        if (isVIP) {
            sb.append(" | ").append(Strings.format("arena_panel.credit", availableCredit, creditCeiling));
        }
        
        balanceLabel.setText(sb.toString());
        balanceLabel.setColor(balanceColor);
    }
    
    private void createResultLabel() {
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
    
    private void createRewardBreakdownLabels() {
        if (panel == null) return;
        
        final float breakdownX = SHIP_COLUMN_WIDTH + MARGIN + CENTER_COLUMN_WIDTH - MARGIN;
        final float breakdownY = MARGIN + 40f;
        final float breakdownW = PANEL_WIDTH - breakdownX - MARGIN;
        final float lineHeight = 28f;
        final float spacing = 2f;
        
        for (int i = 0; i < MAX_REWARD_LINES; i++) {
            final float y = breakdownY + i * (lineHeight + spacing);
            
            rewardBreakdownLabels[i] = settings.createLabel("", Fonts.DEFAULT_SMALL);
            rewardBreakdownLabels[i].setAlignment(Alignment.LMID);
            panel.addComponent((UIComponentAPI) rewardBreakdownLabels[i]).inTL(breakdownX, y)
                .setSize(breakdownW, lineHeight);
        }
    }
    
    private void createShipLabels() {
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
                String.format(Strings.get("arena_panel_rewards.odds"), displayOdds), Fonts.DEFAULT_SMALL
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
        final PositionAPI pos = panel.getPosition();
        final float x = pos.getX();
        final float y = pos.getY();
        final float w = pos.getWidth();
        final float h = pos.getHeight();
        
        Misc.renderQuad(x, y, w, h, COLOR_BG_DARK, alphaMult);
        
        Misc.renderQuad(x, y, SHIP_COLUMN_WIDTH, h, COLOR_SIDEBAR, alphaMult);
        
        if (combatants != null) {
            renderShipBoxes(x, y, h, alphaMult);
        }
        
        renderBattleLogSprites(x, y, alphaMult);
        
        // Draw divider line between battle log and reward breakdown
        float dividerX = x + SHIP_COLUMN_WIDTH + CENTER_COLUMN_WIDTH - 5f;
        float dividerTop = y + MARGIN + 50f; // Below instruction text area
        float dividerBottom = y + h - MARGIN - 50f; // Above button area
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(0.3f, 0.3f, 0.4f, alphaMult * 0.5f);
        GL11.glLineWidth(1f);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(dividerX, dividerTop);
        GL11.glVertex2f(dividerX, dividerBottom);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        
        updateLabels();
        updateButtonVisibility();
    }
    
    private void renderShipBoxes(float panelX, float panelY, float panelH, float alphaMult) {
        if (combatants == null) return;

        final float NAME_HEIGHT = 16f;
        final float HP_HEIGHT = 11f;
        final float ODDS_HEIGHT = 30f;

        float startY = MARGIN + 10f;
        float totalItemHeight = BOX_HEIGHT + BOX_SPACING + NAME_HEIGHT + HP_HEIGHT + ODDS_HEIGHT + ENTRY_SPACING;

        for (int i = 0; i < combatants.size(); i++) {
            SpiralGladiator ship = combatants.get(i);
            float shipY = startY + i * totalItemHeight;

            boolean isAttacker = ship.hullId != null && ship.hullId.equals(currentAttackerHullId);
            boolean isTarget = ship.hullId != null && ship.hullId.equals(currentTargetHullId);

            float nudgeOffset = 0f;
            if (isAttacker && spriteAnimTimer > 0) {
                nudgeOffset = attackerNudgeOffset;
            }

            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_TEXTURE_2D);

            float screenY = panelY + panelH - shipY - BOX_HEIGHT;
            float renderX = panelX + MARGIN + nudgeOffset;

            int displayHp = (hpAnimating[i] || isAnimating) ? animatedHp[i] : ship.hp;
            float hpPercent;
            Color hpColor;
            if (isShipEffectivelyDead(ship)) {
                hpPercent = 0.08f;
                hpColor = COLOR_DESTROYED;
            } else {
                hpPercent = Math.max(0, Math.min(1, (float) displayHp / ship.maxHp));
                hpColor = hpPercent > 0.5f ? COLOR_HEALTHY : (hpPercent > 0.25f ? COLOR_DAMAGED : COLOR_DESTROYED);
            }

            if (i == selectedChampionIndex) {
                GL11.glColor4f(COLOR_SELECTED.getRed() / 255f, COLOR_SELECTED.getGreen() / 255f,
                    COLOR_SELECTED.getBlue() / 255f, alphaMult * 0.8f);
                GL11.glBegin(GL11.GL_LINE_LOOP);
                GL11.glVertex2f(renderX - 3, screenY - 3);
                GL11.glVertex2f(renderX + BOX_WIDTH + 3, screenY - 3);
                GL11.glVertex2f(renderX + BOX_WIDTH + 3, screenY + BOX_HEIGHT + 3);
                GL11.glVertex2f(renderX - 3, screenY + BOX_HEIGHT + 3);
                GL11.glEnd();
            }

            float boxAlpha = fadeOutHullIds.contains(ship.hullId) ? fadeOutAlpha[i] : 1.0f;
            Misc.renderQuad(renderX, screenY, BOX_WIDTH, BOX_HEIGHT, COLOR_BOX_BG, alphaMult * 0.8f * boxAlpha);

            float fillWidth = (BOX_WIDTH - 2) * hpPercent;
            Misc.renderQuad(renderX + 1, screenY + 1, fillWidth, BOX_HEIGHT - 2, hpColor, alphaMult * 0.6f * boxAlpha);

            GL11.glColor4f(COLOR_BOX_BORDER.getRed() / 255f, COLOR_BOX_BORDER.getGreen() / 255f,
                COLOR_BOX_BORDER.getBlue() / 255f, alphaMult * 0.8f * boxAlpha);
            GL11.glLineWidth(1f);
            GL11.glBegin(GL11.GL_LINE_LOOP);
            GL11.glVertex2f(renderX, screenY);
            GL11.glVertex2f(renderX + BOX_WIDTH, screenY);
            GL11.glVertex2f(renderX + BOX_WIDTH, screenY + BOX_HEIGHT);
            GL11.glVertex2f(renderX, screenY + BOX_HEIGHT);
            GL11.glEnd();
            GL11.glEnable(GL11.GL_TEXTURE_2D);

            SpriteAPI sprite = getShipSprite(ship.hullId);
            if (sprite != null) {
                float spriteWidth = sprite.getWidth();
                float spriteHeight = sprite.getHeight();
                float maxDim = Math.max(spriteWidth, spriteHeight);
                float scale = (BOX_HEIGHT * SPRITE_SCALE) / maxDim;

                float scaledWidth = spriteWidth * scale;
                float scaledHeight = spriteHeight * scale;

                float centerX = renderX + BOX_WIDTH / 2f;
                float centerY = screenY + BOX_HEIGHT / 2f;

                sprite.setSize(scaledWidth, scaledHeight);

                Color tint;
                float spriteAlpha;

                if (isTarget && targetFlashState) {
                    tint = Color.WHITE;
                    spriteAlpha = 1.5f * alphaMult * boxAlpha;
                    sprite.setColor(tint);
                    sprite.setAlphaMult(spriteAlpha);
                    sprite.setAdditiveBlend();
                } else if (isShipEffectivelyDead(ship)) {
                    tint = COLOR_TINT_DEAD;
                    spriteAlpha = 0.6f * alphaMult * boxAlpha;
                    sprite.setColor(tint);
                    sprite.setAlphaMult(spriteAlpha);
                    sprite.setNormalBlend();
                } else if (displayHp < ship.maxHp * 0.5f) {
                    tint = COLOR_TINT_DAMAGED;
                    spriteAlpha = 0.8f * alphaMult * boxAlpha;
                    sprite.setColor(tint);
                    sprite.setAlphaMult(spriteAlpha);
                    sprite.setNormalBlend();
                } else {
                    tint = Color.WHITE;
                    spriteAlpha = alphaMult * boxAlpha;
                    sprite.setColor(tint);
                    sprite.setAlphaMult(spriteAlpha);
                    sprite.setNormalBlend();
                }
                sprite.renderAtCenter(centerX, centerY);
            }

            if (isShipEffectivelyDead(ship)) {
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glColor4f(1f, 0f, 0f, alphaMult * boxAlpha * 0.8f);
                GL11.glLineWidth(3f);
                GL11.glBegin(GL11.GL_LINES);
                GL11.glVertex2f(renderX + 10, screenY + BOX_HEIGHT - 10);
                GL11.glVertex2f(renderX + BOX_WIDTH - 10, screenY + 10);
                GL11.glVertex2f(renderX + BOX_WIDTH - 10, screenY + BOX_HEIGHT - 10);
                GL11.glVertex2f(renderX + 10, screenY + 10);
                GL11.glEnd();
                GL11.glLineWidth(1f);
                GL11.glEnable(GL11.GL_TEXTURE_2D);
            }
        }
    }
    
    private void updateLabels() {
        updateBalanceLabel();
        
        if (currentRound != lastCurrentRound) {
            lastCurrentRound = currentRound;
            roundLabel.setText(Strings.format("arena_panel.round", currentRound));
        }
        
        if (totalBet != lastTotalBet) {
            lastTotalBet = totalBet;
            betLabel.setText(Strings.format("arena_panel.total_bet", totalBet));
        }
        
        boolean hideInstruction = false;
        final String newInstructionText;
        if (showingOverdraftConfirmation) {
            newInstructionText = Strings.get("arena_panel.confirm_overdraft");
        } else if (showingErrorMessage) {
            newInstructionText = "";
            hideInstruction = true;
        } else if (battleEnded) {
            newInstructionText = Strings.get("arena_panel.next_game");
        } else if (currentRound > 0 && addingBetDuringBattle && selectedChampionIndex < 0) {
            newInstructionText = Strings.get("arena_panel.select_champion_bet");
        } else if (currentRound > 0 && addingBetDuringBattle) {
            SpiralGladiator selected = combatants.get(selectedChampionIndex);
            newInstructionText = Strings.format("arena.add_bet_on", selected.hullName);
        } else if (currentRound > 0) {
            newInstructionText = null;
            hideInstruction = true;
        } else if (showingBetAmounts && selectedChampionIndex >= 0) {
            SpiralGladiator selected = combatants.get(selectedChampionIndex);
            newInstructionText = Strings.format("arena.add_bet_on", selected.hullName);
        } else {
            newInstructionText = Strings.get("arena_panel.select_champion_bet");
        }
        
        final String newText = hideInstruction ? null : newInstructionText;
        if (!Objects.equals(lastInstructionText, newText)) {
            lastInstructionText = newText;
            instructionLabel.setText(newText == null ? "" : newText);
        }
        
        if (combatants != null) {
            for (int i = 0; i < combatants.size() && i < 5; i++) {
                SpiralGladiator ship = combatants.get(i);
                
                // Show labels
                shipNameLabels[i].setOpacity(1f);
                shipHpLabels[i].setOpacity(1f);
                shipOddsLabels[i].setOpacity(1f);
                
                // Update ship name if ship changed
                final boolean shipChanged = !shipStateInitialized || 
                    (lastShipHullIds[i] == null || !lastShipHullIds[i].equals(ship.hullId));
                
                if (shipChanged) {
                    lastShipHullIds[i] = ship.hullId;
                    final String fullName = ship.prefix + " " + ship.hullName + " " + ship.affix;
                    shipNameLabels[i].setText(fullName);
                    applyShipNameHighlighting(shipNameLabels[i], ship);
                }
                
                final boolean hpChanged = !shipStateInitialized || 
                    lastShipHp[i] != ship.hp || 
                    lastShipMaxHp[i] != ship.maxHp ||
                    lastShipDead[i] != ship.isDead;
                
                if (hpChanged || isAnimating) {
                    lastShipHp[i] = ship.hp;
                    lastShipMaxHp[i] = ship.maxHp;
                    lastShipDead[i] = ship.isDead;
                    
                    final int displayHp = (isAnimating || hpAnimating[i]) ? animatedHp[i] : ship.hp;
                    shipHpLabels[i].setText(displayHp + "/" + ship.maxHp + " HP");
                    final Color hpColor = (killedHullIds.contains(ship.hullId) || ship.isDead) ? COLOR_DESTROYED : 
                        ((float) displayHp / ship.maxHp > 0.5f ? COLOR_HEALTHY : COLOR_DAMAGED);
                    shipHpLabels[i].setColor(hpColor);
                }
                
                float displayOdds = oddsCached && i < cachedOdds.length ? cachedOdds[i] : ship.getCurrentOdds(currentRound);
                
                int currentBetCount = 0;
                if (bets != null) {
                    for (BetInfo b : bets) {
                        if (b.ship == ship) currentBetCount++;
                    }
                }
                
                boolean oddsChanged = !shipStateInitialized || lastShipOdds[i] != displayOdds || lastShipBetCount[i] != currentBetCount;
                if (oddsChanged) {
                    lastShipOdds[i] = displayOdds;
                    lastShipBetCount[i] = currentBetCount;
                    
                    String oddsText = String.format(Strings.get("arena_panel_rewards.odds"), displayOdds);
                    oddsText += buildBetDisplayText(ship);
                    
                    shipOddsLabels[i].setText(oddsText);
                    shipOddsLabels[i].setColor(ship.isDead ? Color.GRAY : Color.YELLOW);
                }
            }
            
            // Hide labels for ships that no longer exist (e.g., fewer ships in new match)
            for (int i = combatants.size(); i < 5; i++) {
                shipNameLabels[i].setOpacity(0f);
                shipHpLabels[i].setOpacity(0f);
                shipOddsLabels[i].setOpacity(0f);
            }
            
            shipStateInitialized = true;
        }
        
        if (!battleEnded) {
            resultLabel.setText("");
        } else if (rewardBreakdown != null) {
            // Don't show "Battle Complete!" - keep empty
            resultLabel.setText("");
        } else if (winnerIndex >= 0 && winnerIndex < combatants.size()) {
            SpiralGladiator winner = combatants.get(winnerIndex);
            resultLabel.setText(Strings.format("arena_panel_rewards.winner_reward", winner.hullName, totalReward));
            resultLabel.setColor(Color.GREEN);
        } else {
            // Don't show "Battle Complete!" - keep empty
            resultLabel.setText("");
        }
        
        updateRewardBreakdownLabels();
    }
    
    private void updateRewardBreakdownLabels() {
        if (!battleEnded || rewardBreakdown == null) {
            if (!rewardBreakdownCached) {
                for (int i = 0; i < MAX_REWARD_LINES; i++) {
                    rewardBreakdownLabels[i].setText("");
                }
            }
            return;
        }
        
        if (rewardBreakdownCached) return;
        
        for (int i = 0; i < MAX_REWARD_LINES; i++) {
            rewardBreakdownLabels[i].setText("");
        }
        
        int lineIndex = 0;
        
        if (winnerIndex >= 0 && winnerIndex < combatants.size()) {
            SpiralGladiator winner = combatants.get(winnerIndex);
            rewardBreakdownLabels[lineIndex].setText(Strings.format("arena_panel_rewards.winner", winner.hullName));
            rewardBreakdownLabels[lineIndex].setColor(PREFIX_POSITIVE_COLOR);
            lineIndex++;
        }
        
        rewardBreakdownLabels[lineIndex].setText(Strings.get("arena_panel_rewards.header"));
        rewardBreakdownLabels[lineIndex].setColor(BATTLE_EVENT_COLOR);
        lineIndex++;
        
        rewardBreakdownLabels[lineIndex].setText(Strings.format("arena_panel_rewards.total_bet", rewardBreakdown.totalBet));
        rewardBreakdownLabels[lineIndex].setColor(Color.WHITE);
        lineIndex++;
        
        if (rewardBreakdown.winReward > 0) {
            rewardBreakdownLabels[lineIndex].setText(Strings.format("arena_panel_rewards.win_reward", rewardBreakdown.winReward));
            rewardBreakdownLabels[lineIndex].setColor(PREFIX_POSITIVE_COLOR);
            lineIndex++;
        }
        
        if (rewardBreakdown.consolationReward > 0) {
            rewardBreakdownLabels[lineIndex].setText(Strings.format("arena_panel_rewards.consolation", rewardBreakdown.consolationReward));
            rewardBreakdownLabels[lineIndex].setColor(BATTLE_EVENT_HIT_COLOR);
            lineIndex++;
        }
        
        int totalKills = 0;
        for (RewardBreakdown.ShipRewardInfo shipInfo : rewardBreakdown.shipRewards) {
            totalKills += shipInfo.kills;
        }
        
        if (totalKills > 0) {
            float killBonusPct = totalKills * CasinoConfig.ARENA_KILL_BONUS_PER_KILL * 100;
            rewardBreakdownLabels[lineIndex].setText(Strings.format("arena_panel_rewards.kill_bonus", totalKills, killBonusPct));
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
            
            if (!shipInfo.betDetails.isEmpty() && lineIndex < MAX_REWARD_LINES) {
                StringBuilder betDetailLine = new StringBuilder("  -> ");
                for (int i = 0; i < shipInfo.betDetails.size(); i++) {
                    RewardBreakdown.ShipRewardInfo.BetDetail bd = shipInfo.betDetails.get(i);
                    if (i > 0) betDetailLine.append(", ");
                    betDetailLine.append(Strings.format("arena_panel_rewards.bet_detail", bd.roundPlaced, bd.multiplier));
                }
                rewardBreakdownLabels[lineIndex].setText(betDetailLine.toString());
                rewardBreakdownLabels[lineIndex].setColor(Color.GRAY);
                lineIndex++;
            }
        }
        
        int net = rewardBreakdown.netResult;
        Color netColor = net >= 0 ? NET_POSITIVE_COLOR : NET_NEGATIVE_COLOR;
        String netStr = net >= 0 ? "+" + net : String.valueOf(net);
        rewardBreakdownLabels[lineIndex].setText(Strings.format("arena_panel_rewards.net", netStr));
        rewardBreakdownLabels[lineIndex].setColor(netColor);
        rewardBreakdownCached = true;
    }
    
    private void updateCachedParsedEntries() {
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
    
private List<ParsedLogEntry> getFilteredEntries() {
        updateCachedParsedEntries();
        
        final List<ParsedLogEntry> filtered = new ArrayList<>();
        for (ParsedLogEntry entry : cachedParsedEntries) {
            if (entry.type.equals("STATUS") || entry.type.isEmpty()) {
                continue;
            }
            filtered.add(entry);
        }
        return filtered;
    }

    private void renderBattleLogSprites(float panelX, float panelY, float alphaMult) {
        List<ParsedLogEntry> validEntries;
        int entriesToShow;

        if (isAnimating && !pendingEntries.isEmpty()) {
            validEntries = pendingEntries;
            entriesToShow = displayedLogIndex;
        } else {
            validEntries = getFilteredEntries();
            entriesToShow = validEntries.size();
        }

        int maxLines = 12;
        int start = Math.max(0, entriesToShow - maxLines);

        float logPanelX = SHIP_COLUMN_WIDTH + MARGIN;
        float logPanelY = MARGIN + 40f;
        float logPanelW = CENTER_COLUMN_WIDTH - MARGIN;
        float logPanelH = PANEL_HEIGHT - MARGIN * 2 - 80f;

        float currentY = logY;
        float rowSpacing = 4f;

        int lineIndex = 0;

        float leftSpriteX = panelX + logPanelX + LOG_LEFT_MARGIN + LOG_SPRITE_SIZE / 2f;
        float rightSpriteX = panelX + logPanelX + logPanelW - LOG_LEFT_MARGIN - LOG_SPRITE_SIZE / 2f;

        float textStartX_twoSprites = logPanelX + LOG_LEFT_MARGIN + LOG_SPRITE_SIZE + LOG_SPRITE_GAP;
        float textStartX_oneSprite = logPanelX + LOG_LEFT_MARGIN;

        for (int i = start; i < entriesToShow; i++) {
            ParsedLogEntry entry = validEntries.get(i);

            float screenY = panelY + logPanelY + logPanelH - currentY - LOG_LINE_HEIGHT;
            float spriteCenterY = screenY + LOG_LINE_HEIGHT / 2f;

            String labelText;
            Color labelColor;
            float textX;

            switch (entry.type) {
                case "HIT" -> {
                    String hitText = entry.rawEntry;
                    if (hitText.startsWith("[CRIT] ")) {
                        hitText = hitText.substring(7);
                    } else if (hitText.startsWith("[HIT] ")) {
                        hitText = hitText.substring(6);
                    }
                    labelText = shortenDamageText(hitText);
                    labelColor = entry.isCrit ? BATTLE_HIT_COLOR_CRIT : BATTLE_HIT_COLOR;
                    textX = textStartX_twoSprites;

                    drawBattleLogSpriteWithDead(entry.attackerHullId, leftSpriteX, spriteCenterY, alphaMult, false);
                    drawBattleLogSpriteWithDead(entry.targetHullId, rightSpriteX, spriteCenterY, alphaMult, false);
                }
                case "MISS" -> {
                    String missText = entry.rawEntry;
                    if (missText.startsWith("[MISS] ")) {
                        missText = missText.substring(7);
                    }
                    labelText = shortenDamageText(missText);
                    labelColor = BATTLE_MISS_COLOR;
                    textX = textStartX_twoSprites;

                    drawBattleLogSpriteWithDead(entry.targetHullId, leftSpriteX, spriteCenterY, alphaMult, false);
                    drawBattleLogSpriteWithDead(entry.attackerHullId, rightSpriteX, spriteCenterY, alphaMult, false);
                }
                case "KILL" -> {
                    String killText = entry.rawEntry;
                    if (killText.startsWith("[KILL] ")) {
                        killText = killText.substring(7);
                    }
                    labelText = shortenDamageText(killText);
                    labelColor = PREFIX_NEGATIVE_COLOR;
                    textX = textStartX_twoSprites;

                    drawBattleLogSpriteWithDead(entry.attackerHullId, leftSpriteX, spriteCenterY, alphaMult, false);
                    drawBattleLogSpriteWithDead(entry.targetHullId, rightSpriteX, spriteCenterY, alphaMult, true);
                }
                case "EVENT" -> {
                    String eventText = entry.rawEntry;
                    if (eventText.startsWith("[EVENT] ")) {
                        eventText = eventText.substring(8);
                    }
                    labelText = shortenDamageText(eventText);
                    labelColor = BATTLE_EVENT_COLOR;
                    textX = textStartX_oneSprite;

                    drawBattleLogSpriteWithDead(entry.attackerHullId, rightSpriteX, spriteCenterY, alphaMult, false);
                }
                case "EVENT_HIT" -> {
                    String hitText = entry.rawEntry;
                    if (hitText.startsWith("[HIT] ")) {
                        hitText = hitText.substring(6);
                    }
                    labelText = shortenDamageText(hitText);
                    labelColor = BATTLE_EVENT_HIT_COLOR;
                    textX = textStartX_oneSprite;

                    drawBattleLogSpriteWithDead(entry.attackerHullId, rightSpriteX, spriteCenterY, alphaMult, false);
                }
                case "ROUND" -> {
                    String roundText = entry.rawEntry;
                    if (roundText.startsWith("[ROUND] ")) {
                        roundText = roundText.substring(8);
                    }
                    labelText = "-------- " + roundText + " --------";
                    labelColor = BATTLE_ROUND_COLOR;
                    textX = textStartX_oneSprite;
                }
                default -> {
                    labelText = "";
                    labelColor = Color.WHITE;
                    textX = textStartX_twoSprites;
                }
            }

            if (!labelText.isEmpty()) {
                battleLogTextLabels[lineIndex].setText(labelText);
                battleLogTextLabels[lineIndex].setColor(labelColor);
                battleLogTextLabels[lineIndex].setOpacity(1f);

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
    
    private void drawBattleLogSpriteWithDead(String hullId, float cx, float cy, float alphaMult, boolean dead) {
        final SpriteAPI sprite = getShipSprite(hullId);
        if (sprite == null) return;
        
        final float spriteWidth = sprite.getWidth();
        final float spriteHeight = sprite.getHeight();
        final float maxDim = Math.max(spriteWidth, spriteHeight);
        final float scale = LOG_SPRITE_SIZE / maxDim;
        
        sprite.setSize(spriteWidth * scale, spriteHeight * scale);
        
        sprite.setColor(dead ? COLOR_DESTROYED : Color.WHITE);
        sprite.setAlphaMult(alphaMult * (dead ? 0.5f : 1f));
        sprite.setNormalBlend();
        sprite.renderAtCenter(cx, cy);
        
        if (dead) {
            final float halfSize = LOG_SPRITE_SIZE / 2f - 4f;
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

    private String shortenDamageText(String text) {
        if (text == null) return "";
        return text.replace("CRIT damage", "CRIT dmg").replace("damage", "dmg");
    }

    @Override
    public void actionPerformed(Object input, Object source) {
        if (source instanceof ButtonAPI btn) {
            processAction(btn.getCustomData());
        }
        updateButtonVisibility();
    }
    
    private void processAction(Object data) {
        if (data == null) return;
        
        if (data == ARENA_LEAVE_DATA) {
            actionCallback.onLeave();
            return;
        }
        
        if (data == ARENA_BET_CANCEL) {
            showingBetAmounts = false;
            selectedChampionIndex = -1;
            addingBetDuringBattle = false;
            return;
        }
        
        if (data == NEXT_ROUND_DATA) {
            actionCallback.onWatchNextRound();
            return;
        }
        
        if (data == NEXT_GAME_DATA) {
            actionCallback.onNextGame();
            return;
        }
        
        if (data == ARENA_SKIP_DATA) {
            actionCallback.onSkipToEnd();
            return;
        }
        
        if (data == ARENA_ADD_BET_DATA) {
            addingBetDuringBattle = true;
            showingBetAmounts = true;
            selectedChampionIndex = -1;
            return;
        }
        
        if (data == ARENA_SUSPEND_DATA) {
            actionCallback.onSuspend();
            return;
        }
        
        if (data == ARENA_START_BATTLE_DATA) {
            actionCallback.onStartBattle();
            return;
        }
        
        if (data == ARENA_CONFIRM_DIALOG) {
            if (showingOverdraftConfirmation) {
                actionCallback.onConfirmBet(pendingChampionIndex, pendingBetAmount);
            }
            clearOverdraftConfirmation();
            return;
        }
        
        if (data == ARENA_CANCEL_OVERDRAFT) {
            clearOverdraftConfirmation();
            return;
        }
        
        if (data == ARENA_DISMISS_ERROR) {
            clearErrorMessage();
            return;
        }

        if (data instanceof String strData) {
            if (strData.contains(ARENA_SELECT_DATA)) {
                final int idx = Integer.parseInt(strData.replaceAll(".*?(\\d+)$", "$1"));

                if (idx == -1) {
                    addingBetDuringBattle = false;
                    showingBetAmounts = false;
                    selectedChampionIndex = -1;
                } else {
                    selectedChampionIndex = idx;
                    showingBetAmounts = true;

                    if (currentRound == 0) {
                        actionCallback.onSelectChampion(idx);
                    }
                }
                return;
            }

            if (strData.contains(ARENA_BET_DATA)) {
                final int amount = Integer.parseInt(strData.replaceAll(".*?(\\d+)$", "$1"));
                handleBetAmountClick(selectedChampionIndex, amount);
            }
        }
    }

    public final void processInput(List<InputEventAPI> events) {
        for (InputEventAPI event : events) {
            if (event.isConsumed()) continue;

            // Click-to-skip animation
            if (event.isMouseDownEvent() && isAnimating) {
                event.consume();
                skipRequested = true;
            }
        }
    }
    
    private void handleBetAmountClick(int championIndex, int amount) {
        final BetValidationResult validation = validateBet(amount);
        
        if (validation.isAffordable()) {
            actionCallback.onConfirmBet(championIndex, amount);

            showingBetAmounts = false;
            selectedChampionIndex = -1;
            updateBalanceLabel();
        } else if (validation.needsOverdraftConfirmation()) {
            pendingBetAmount = amount;
            pendingChampionIndex = championIndex;
            currentOverdraftMessage = validation.overdraftMessage;
            showingOverdraftConfirmation = true;
            showingBetAmounts = false;
            selectedChampionIndex = -1;
            updateButtonVisibility();
            updateMessageLabel();
        } else if (validation.hasError()) {
            currentErrorMessage = validation.errorMessage;
            showingErrorMessage = true;
            showingBetAmounts = false;
            selectedChampionIndex = -1;
            updateButtonVisibility();
            updateMessageLabel();
        }
    }
    
    private void clearOverdraftConfirmation() {
        showingOverdraftConfirmation = false;
        pendingBetAmount = 0;
        pendingChampionIndex = -1;
        currentOverdraftMessage = "";
        updateButtonVisibility();
        updateMessageLabel();
    }
    
    private void clearErrorMessage() {
        showingErrorMessage = false;
        currentErrorMessage = "";
        updateButtonVisibility();
        updateMessageLabel();
    }
    
    private void updateMessageLabel() {
        if (messageLabel == null) return;
        
        if (showingOverdraftConfirmation) {
            messageLabel.setText(Strings.get("arena_panel_rewards.overdraft_title") + "\n" + currentOverdraftMessage);
            messageLabel.setColor(new Color(255, 200, 50));
        } else if (showingErrorMessage) {
            messageLabel.setText(currentErrorMessage);
            messageLabel.setColor(Color.RED);
        } else {
            messageLabel.setText("");
        }
    }
    
    private void cacheOdds() {
        if (combatants == null) return;
        
        for (int i = 0; i < combatants.size() && i < 5; i++) {
            SpiralGladiator ship = combatants.get(i);
            cachedOdds[i] = ship.getCurrentOdds(currentRound);
        }
        oddsCached = true;
    }
    
    private void syncAnimationStateFromCombatants() {
        if (combatants == null) return;
        
        for (int i = 0; i < combatants.size(); i++) {
            SpiralGladiator ship = combatants.get(i);
            
            // Sync HP to match combatant state (needed when restoring suspended game)
            animatedHp[i] = ship.hp;
            targetAnimatedHp[i] = ship.hp;
            prevAnimatedHp[i] = ship.hp;
            
            // Sync dead state so X and fade persist (needed when restoring suspended game)
            if (ship.isDead) {
                killedHullIds.add(ship.hullId);
                fadeOutHullIds.add(ship.hullId);
                fadeOutAlpha[i] = 0.5f;
            }
        }
    }
    
    public final void updateState(
        List<SpiralGladiator> combatants,
        int currentRound,
        int totalBet,
        List<BetInfo> bets,
        List<String> battleLog
    ) {
        final boolean isRoundProgression = lastCurrentRound >= 0 && currentRound > lastCurrentRound;
        
        if (isRoundProgression) {
            isAnimating = true;
        }
        
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
        
        // Sync animation state for suspended game restoration
        if (currentRound > 0 && !shipStateInitialized) {
            syncAnimationStateFromCombatants();
        }
        
        updateLabels();
    }
    
    public final void setBattleEnded(int winnerIndex, int totalReward, int finalRound) {
        setBattleEnded(winnerIndex, totalReward, null, finalRound);
    }
    
    public final void setBattleEnded(int winnerIndex, int totalReward, RewardBreakdown breakdown,
        int finalRound
    ) {
        this.battleEnded = true;
        this.winnerIndex = winnerIndex;
        this.totalReward = totalReward;
        this.rewardBreakdown = breakdown;
        this.currentRound = finalRound;
        this.showingBetAmounts = false;
        this.addingBetDuringBattle = false;
        this.selectedChampionIndex = -1;
        this.showingOverdraftConfirmation = false;
        this.showingErrorMessage = false;
        this.rewardBreakdownCached = false;
        
        oddsCached = false;
        cacheOdds();
        
        updateLabels();
    }
    
    private void resetAnimationState() {
        logAnimationTimer = 0f;
        displayedLogIndex = 0;
        isAnimating = false;
        pendingEntries.clear();
        skipRequested = false;
        
        currentAttackerHullId = null;
        currentTargetHullId = null;
        spriteAnimTimer = 0f;
        attackerNudgeOffset = 0f;
        targetFlashState = false;
        
        Arrays.fill(animatedHp, 0);
        Arrays.fill(targetAnimatedHp, 0);
        Arrays.fill(prevAnimatedHp, 0);
        Arrays.fill(hpAnimTimer, 0f);
        Arrays.fill(hpAnimating, false);
        
        killedHullIds.clear();
        fadeOutHullIds.clear();
        Arrays.fill(fadeOutAlpha, 1.0f);
    }
    
    public final void resetForNewMatch(
        List<SpiralGladiator> combatants,
        int currentRound,
        int totalBet,
        List<BetInfo> bets,
        List<String> battleLog
    ) {
        resetAnimationState();
        
        this.battleEnded = false;
        this.winnerIndex = -1;
        this.totalReward = 0;
        this.rewardBreakdown = null;
        this.rewardBreakdownCached = false;
        this.showingBetAmounts = false;
        this.addingBetDuringBattle = false;
        this.selectedChampionIndex = -1;
        this.showingOverdraftConfirmation = false;
        this.showingErrorMessage = false;
        this.pendingBetAmount = 0;
        this.pendingChampionIndex = -1;
        this.currentErrorMessage = "";
        this.currentOverdraftMessage = "";
        
        this.combatants = combatants;
        this.currentRound = currentRound;
        this.totalBet = totalBet;
        this.bets = bets;
        this.battleLog = battleLog;
        
        shipStateInitialized = false;
        lastCurrentRound = -1;
        lastTotalBet = -1;
        lastBalance = Integer.MIN_VALUE;
        lastAvailableCredit = Integer.MIN_VALUE;
        lastCreditCeiling = Integer.MIN_VALUE;
        lastIsVIP = false;
        lastInstructionText = null;
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
        
        oddsCached = false;
        cacheOdds();
        
        updateLabels();
        updateButtonVisibility();
        updateMessageLabel();
    }
    
    private String buildBetDisplayText(SpiralGladiator ship) {
        if (bets == null || bets.isEmpty()) return "";
        
        final Map<Integer, List<BetInfo>> betsByRound = new HashMap<>();
        for (BetInfo b : bets) {
            if (b.ship == ship) {
                betsByRound.computeIfAbsent(b.roundPlaced, k -> new ArrayList<>()).add(b);
            }
        }

        final List<String> betStrings = getStrings(betsByRound);

        if (betStrings.isEmpty()) return "";
        
        int displayCount = Math.min(betStrings.size(), 3);
        String displayText = String.join("", betStrings.subList(0, displayCount));
        if (betStrings.size() > 3) {
            displayText += " +" + (betStrings.size() - 3) + " more";
        }
        return " " + displayText;
    }

    private static List<String> getStrings(Map<Integer, List<BetInfo>> betsByRound)
    {
        final List<String> betStrings = new ArrayList<>();
        for (Map.Entry<Integer, List<BetInfo>> entry : betsByRound.entrySet()) {
            List<BetInfo> roundBets = entry.getValue();
            if (!roundBets.isEmpty()) {
                int totalAmount = 0;
                float multiplier = roundBets.get(0).multiplier;
                for (BetInfo b : roundBets) {
                    totalAmount += b.amount;
                }
                betStrings.add(String.format("[%d, %.2fx]", totalAmount, multiplier));
            }
        }
        return betStrings;
    }

    private String getPositionString(int finalPosition) {
        return switch (finalPosition) {
            case 0 -> "1st";
            case 1 -> "2nd";
            case 2 -> "3rd";
            case 3 -> "4th";
            case 4 -> "5th";
            default -> (finalPosition + 1) + "th";
        };
    }
    
    public final void showExternalError(String message) {
        currentErrorMessage = message;
        showingErrorMessage = true;
        showingOverdraftConfirmation = false;
        updateButtonVisibility();
        updateMessageLabel();
    }   
}