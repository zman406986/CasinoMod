package data.scripts.casino;

import com.fs.starfarer.api.Global;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;

/**
 * Configuration class for the Interastral Peace Casino mod.
 *
 * LOADING PATTERN:
 * All settings are loaded from data/config/casino_settings.json in the onApplicationLoad() phase.
 * This ensures configuration is available before any game logic runs.
 *
 * CONFIGURATION SECTIONS:
 * 1. VIP System - Daily rewards, interest rates, credit ceilings
 * 2. Poker - Blind sizes, stack sizes, AI personality settings
 * 3. Gacha - Pity thresholds, costs, rotation timing
 * 4. Arena - Ship counts, affixes, chaos events
 * 5. Debt - Interest rates, collector thresholds
 *
 * AI_AGENT NOTES:
 * - All lists are initialized with default values and then overwritten from JSON
 * - Never modify these values at runtime - they are configuration constants
 * - Use CasinoConfig.XXX to reference values, never hardcode numbers
 * - If a config key is missing from JSON, the default value is used
 */
public class CasinoConfig {

    // VIP System Configuration
    /** Daily reward amount for VIP subscribers (in Stargems) */
    public static int VIP_DAILY_REWARD = 100;
    /** Number of days a VIP pass lasts */
    public static int VIP_PASS_DAYS = 30;
    /** Daily interest rate for VIP members (0.02 = 2%) */
    public static float VIP_DAILY_INTEREST_RATE = 0.02f;
    /** Daily interest rate for non-VIP members (0.05 = 5%) */
    public static float NORMAL_DAILY_INTEREST_RATE = 0.05f;
    /** Alias for NORMAL_DAILY_INTEREST_RATE - used in some contexts */
    public static float NON_VIP_DAILY_INTEREST_RATE = 0.05f;
    /** Base credit ceiling for new accounts (in Stargems) */
    public static int BASE_DEBT_CEILING = 5000;
    /** Additional credit ceiling per VIP pass purchase (in Stargems) */
    public static int CEILING_INCREASE_PER_VIP = 10000;
    /** Cost of one VIP pass (in credits) */
    public static int VIP_PASS_COST = 100000;

    // Poker Configuration
    /** Small blind amount for poker games (in Stargems) */
    public static int POKER_SMALL_BLIND = 50;
    /** Big blind amount for poker games (in Stargems) */
    public static int POKER_BIG_BLIND = 100;
    /** Default opponent stack size (in Stargems) */
    public static int POKER_DEFAULT_OPPONENT_STACK = 10000;
    /** Available stack sizes for player to choose from (in Stargems) */
    public static int[] POKER_STACK_SIZES = {1000, 2500, 5000, 10000};
    
    // Poker AI Configuration - Thresholds for different AI personalities
    /** Hand strength threshold for TIGHT AI to raise (0.0-1.0) */
    public static float POKER_AI_TIGHT_THRESHOLD_RAISE = 0.75f;
    /** Hand strength threshold for TIGHT AI to fold (0.0-1.0) */
    public static float POKER_AI_TIGHT_THRESHOLD_FOLD = 0.35f;
    /** Stack percentage threshold for TIGHT AI to fold when facing large bets */
    public static float POKER_AI_STACK_PERCENT_FOLD_TIGHT = 0.15f;
    
    /** Hand strength threshold for AGGRESSIVE AI to raise (0.0-1.0) */
    public static float POKER_AI_AGGRESSIVE_THRESHOLD_RAISE = 0.50f;
    /** Hand strength threshold for AGGRESSIVE AI to fold (0.0-1.0) */
    public static float POKER_AI_AGGRESSIVE_THRESHOLD_FOLD = 0.20f;
    
    /** Hand strength threshold for NORMAL AI to raise (0.0-1.0) */
    public static float POKER_AI_NORMAL_THRESHOLD_RAISE = 0.60f;
    /** Hand strength threshold for NORMAL AI to fold (0.0-1.0) */
    public static float POKER_AI_NORMAL_THRESHOLD_FOLD = 0.25f;
    
    /** Maximum stack percentage AI will use for calling (prevents all-in on weak hands) */
    public static float POKER_AI_STACK_PERCENT_CALL_LIMIT = 0.30f;
    
    // Poker Raise Configuration
    /** Available raise amounts as absolute values in Stargems */
    public static int[] POKER_RAISE_AMOUNTS = {200, 400, 1000, 5000};
    /** Maximum random addition to AI raise (in Stargems) */
    public static int POKER_AI_MAX_RAISE_RANDOM_ADDITION = 200;
    /** Minimum raise value (in Stargems) */
    public static int POKER_AI_MIN_RAISE_VALUE = 200;

    // Gacha Configuration
    /** Cost per gacha pull (in Stargems) */
    public static int GACHA_COST = 160;
    /** Hard pity threshold for 5-star items (guaranteed at this count) */
    public static int PITY_HARD_5 = 90;
    /** Soft pity start threshold for 5-star items (rate increases after this) */
    public static int PITY_SOFT_START_5 = 73;
    /** Hard pity threshold for 4-star items */
    public static int PITY_HARD_4 = 10;
    /** Number of days between featured ship rotations */
    public static int GACHA_ROTATION_DAYS = 14;
    /** Exchange rate for converting ships to Stargems (base_value / this = gems) */
    public static float SHIP_TRADE_RATE = 1000f;
    /** Multiplier applied to ship sell values to create house edge (0.9 = 90% of calculated value) */
    public static float SHIP_SELL_MULTIPLIER = 0.9f;

    // Arena Configuration
    /** Number of ships in each arena battle */
    public static int ARENA_SHIP_COUNT = 5;
    /** Maximum agility value for arena ships (prevents invincibility) */
    public static float ARENA_AGILITY_CAP = 0.75f;
    /** Base odds multiplier for arena betting (1.7 = 1:1.7 return) */
    public static float ARENA_BASE_ODDS = 1.7f;
    /** Odds reduction for positive perks (0.8 = 20% reduction) */
    public static float ARENA_POSITIVE_PERK_MULTIPLIER = 0.8f;
    /** Odds increase for negative perks (1.3 = 30% increase) */
    public static float ARENA_NEGATIVE_PERK_MULTIPLIER = 1.3f;
    /** Minimum odds regardless of perks (prevents 0 return) */
    public static float ARENA_MIN_ODDS = 1.2f;
    /** Cost to switch champions mid-battle (as percentage of current bet, 0.5 = 50%) */
    public static float ARENA_SWITCH_COST_PERCENT = 0.5f;
    /** Multiplier penalty when switching champions (0.5 = halved) */
    public static float ARENA_SWITCH_MULTIPLIER_PENALTY = 0.5f;
    /** Entry fee for arena betting (default bet amount) */
    public static int ARENA_ENTRY_FEE = 100;
    /** Survival reward multiplier for arena betting */
    public static float ARENA_SURVIVAL_REWARD_MULT = 2.0f;
    /** Survival bonus per turn survived in arena */
    public static float ARENA_SURVIVAL_BONUS_PER_TURN = 0.1f;
    /** Kill bonus per kill in arena */
    public static float ARENA_KILL_BONUS_PER_KILL = 0.2f;

    // Debt Configuration
    /** Minimum debt (in credits) before collectors start spawning */
    public static int DEBT_COLLECTOR_THRESHOLD = 100000;
    /** Alias for DEBT_COLLECTOR_THRESHOLD for backward compatibility */
    public static int VIP_DEBT_HUNTER_THRESHOLD = 100000;
    /** Interest is applied on these days of each month */
    public static int[] INTEREST_DAYS = {1, 15};
    
    // Credit Ceiling Configuration
    /** Multiplier for top-up amount when calculating credit ceiling (0.5 = 50% of top-ups add to ceiling) */
    public static float TOPUP_CEILING_MULTIPLIER = 0.5f;

    // Market Configuration
    /** Minimum market size for player-owned casinos */
    public static int MARKET_SIZE_MIN_FOR_PLAYER_CASINO = 4;
    /** Minimum market size for general casinos (NPC markets) */
    public static int MARKET_SIZE_MIN_FOR_GENERAL_CASINO = 3;

    // Exchange Rate
    /** Credits per Stargem when converting (1000 = 1 Gem = 1000 Credits) */
    public static float STARGEM_EXCHANGE_RATE = 1000f;

    // VIP Ads (flavor text for daily notifications)
    /** List of VIP advertisement messages shown with daily rewards */
    public static List<String> VIP_ADS = new ArrayList<>();

    // Gem Packages for Top-up
    /** List of available gem packages for purchase */
    public static List<GemPackage> GEM_PACKAGES = new ArrayList<>();

    /**
     * Represents a gem package available for purchase.
     * Contains the number of gems and the cost in credits.
     */
    public static class GemPackage {
        public int gems;
        public int cost;

        public GemPackage(int gems, int cost) {
            this.gems = gems;
            this.cost = cost;
        }
    }

    // Arena Base Stats by Hull Size
    /**
     * Base stats for arena ships by hull size.
     * Contains HP, Power, and Agility values.
     */
    public static class ArenaStat {
        public int hp;
        public int power;
        public float agility;
        
        public ArenaStat(int hp, int power, float agility) {
            this.hp = hp;
            this.power = power;
            this.agility = agility;
        }
    }
    
    /** Base stats for arena ships by hull size (FRIGATE, DESTROYER, CRUISER, CAPITAL_SHIP) */
    public static Map<com.fs.starfarer.api.combat.ShipAPI.HullSize, ArenaStat> ARENA_BASE_STATS = new HashMap<>();
    
    // Arena Prefix Multipliers (Strong/Weak)
    /** Multiplier for strong prefix effects (e.g., Giant, Strong) */
    public static float ARENA_PREFIX_MULT_STRONG = 1.3f;
    /** Multiplier for weak prefix effects (e.g., Tiny, Weak) */
    public static float ARENA_PREFIX_MULT_WEAK = 0.7f;
    /** Agility bonus/penalty from Swift/Clumsy prefixes */
    public static float ARENA_PREFIX_AGILITY_BONUS = 0.15f;
    /** Bravery bonus/penalty from Fierce/Cowardly prefixes */
    public static float ARENA_PREFIX_BRAVERY_BONUS = 0.10f;
    
    // Arena Affix Multipliers (of Might/of Frailty, etc.)
    /** Multiplier for strong affix effects */
    public static float ARENA_AFFIX_MULT_STRONG = 1.2f;
    /** Multiplier for weak affix effects */
    public static float ARENA_AFFIX_MULT_WEAK = 0.8f;
    /** Agility bonus/penalty from Speed/Slowness affixes */
    public static float ARENA_AFFIX_AGILITY_BONUS = 0.10f;
    /** Bravery bonus/penalty from Courage/Fear affixes */
    public static float ARENA_AFFIX_BRAVERY_BONUS = 0.08f;
    
    // Arena Chaos Event Configuration
    /** Chance per simulation step for a chaos event to occur (0.1 = 10%) */
    public static float ARENA_CHAOS_EVENT_CHANCE = 0.1f;
    /** Hull damage percentage from Hull Breach event (0.2 = 20% of max HP) */
    public static float ARENA_HULL_BREACH_DAMAGE_PERCENT = 0.2f;
    
    // Flavor Text Lists (initialized with defaults, overwritten from JSON)
    /** Attack flavor text lines for arena combat (referenced as ARENA_FLAVOR_TEXTS in arena code) */
    public static List<String> ARENA_ATTACK_LINES = new ArrayList<>();
    /** Alias for ARENA_ATTACK_LINES - used by SpiralAbyssArena */
    public static List<String> ARENA_FLAVOR_TEXTS = ARENA_ATTACK_LINES;
    /** Miss flavor text lines for arena combat (referenced as ARENA_MISS_FLAVOR_TEXTS in arena code) */
    public static List<String> ARENA_MISS_LINES = new ArrayList<>();
    /** Alias for ARENA_MISS_LINES - used by SpiralAbyssArena */
    public static List<String> ARENA_MISS_FLAVOR_TEXTS = ARENA_MISS_LINES;
    /** Critical hit flavor text lines for arena combat (referenced as ARENA_CRIT_FLAVOR_TEXTS in arena code) */
    public static List<String> ARENA_CRIT_LINES = new ArrayList<>();
    /** Alias for ARENA_CRIT_LINES - used by SpiralAbyssArena */
    public static List<String> ARENA_CRIT_FLAVOR_TEXTS = ARENA_CRIT_LINES;
    /** Kill flavor text lines for arena combat (referenced as ARENA_KILL_FLAVOR_TEXTS in arena code) */
    public static List<String> ARENA_KILL_LINES = new ArrayList<>();
    /** Alias for ARENA_KILL_LINES - used by SpiralAbyssArena */
    public static List<String> ARENA_KILL_FLAVOR_TEXTS = ARENA_KILL_LINES;
    /** Positive prefixes that buff arena ships */
    public static List<String> ARENA_PREFIX_STRONG_POS = new ArrayList<>();
    /** Negative prefixes that debuff arena ships */
    public static List<String> ARENA_PREFIX_STRONG_NEG = new ArrayList<>();
    /** Positive affixes that buff arena ships */
    public static List<String> ARENA_AFFIX_POS = new ArrayList<>();
    /** Negative affixes that debuff arena ships */
    public static List<String> ARENA_AFFIX_NEG = new ArrayList<>();

    // Ship Blacklist (hulls excluded from gacha pool)
    /** Ship hull IDs that should never appear in gacha pulls (from JSON - deprecated) */
    public static Set<String> GACHA_SHIP_BLACKLIST = new HashSet<>();
    /** Ship hull IDs that should never appear in gacha pulls (from CSV - preferred, allows mod merging) */
    public static Set<String> GACHA_SHIP_BLACKLIST_CSV = new HashSet<>();
    
    // Gacha Probability Configuration
    /** Base probability for 5-star (capital ship) pull (0.006 = 0.6%) */
    public static float PROB_5_STAR = 0.006f;
    /** Base probability for 4-star (cruiser) pull (0.051 = 5.1%) */
    public static float PROB_4_STAR = 0.051f;
    
    // CSV File Path for Gacha Ship Blacklist
    private static final String GACHA_SHIPS_BLACKLIST_CSV = "data/config/gacha_ships_blacklist.csv";

    // Static initialization block for default values that need complex initialization
    static {
        // Initialize arena base stats with default values
        ARENA_BASE_STATS.put(com.fs.starfarer.api.combat.ShipAPI.HullSize.FRIGATE, new ArenaStat(80, 25, 0.35f));
        ARENA_BASE_STATS.put(com.fs.starfarer.api.combat.ShipAPI.HullSize.DESTROYER, new ArenaStat(120, 35, 0.25f));
        ARENA_BASE_STATS.put(com.fs.starfarer.api.combat.ShipAPI.HullSize.CRUISER, new ArenaStat(180, 50, 0.15f));
        ARENA_BASE_STATS.put(com.fs.starfarer.api.combat.ShipAPI.HullSize.CAPITAL_SHIP, new ArenaStat(250, 70, 0.10f));
        
        // Initialize default flavor text for arena
        ARENA_ATTACK_LINES.add("$attacker hits $target for $dmg damage!");
        ARENA_ATTACK_LINES.add("$attacker strikes $target, dealing $dmg damage!");
        ARENA_ATTACK_LINES.add("$attacker blasts $target for $dmg damage!");
        
        ARENA_MISS_LINES.add("$attacker misses $target!");
        ARENA_MISS_LINES.add("$attacker's shot goes wide of $target!");
        ARENA_MISS_LINES.add("$target dodges $attacker's attack!");
        
        ARENA_CRIT_LINES.add("$attacker CRITS $target for $dmg damage!");
        ARENA_CRIT_LINES.add("$attacker lands a devastating blow on $target for $dmg damage!");
        ARENA_CRIT_LINES.add("$attacker critically strikes $target for $dmg damage!");
        
        ARENA_KILL_LINES.add("$attacker destroys $target!");
        ARENA_KILL_LINES.add("$attacker annihilates $target!");
        ARENA_KILL_LINES.add("$target is blown apart by $attacker!");
        
        // Initialize default prefixes
        ARENA_PREFIX_STRONG_POS.add("Giant");
        ARENA_PREFIX_STRONG_POS.add("Strong");
        ARENA_PREFIX_STRONG_POS.add("Swift");
        ARENA_PREFIX_STRONG_POS.add("Fierce");
        
        ARENA_PREFIX_STRONG_NEG.add("Tiny");
        ARENA_PREFIX_STRONG_NEG.add("Weak");
        ARENA_PREFIX_STRONG_NEG.add("Clumsy");
        ARENA_PREFIX_STRONG_NEG.add("Cowardly");
        
        // Initialize default affixes
        ARENA_AFFIX_POS.add("of the Titan");
        ARENA_AFFIX_POS.add("of Might");
        ARENA_AFFIX_POS.add("of Speed");
        ARENA_AFFIX_POS.add("of Courage");
        
        ARENA_AFFIX_NEG.add("of the Mouse");
        ARENA_AFFIX_NEG.add("of Frailty");
        ARENA_AFFIX_NEG.add("of Slowness");
        ARENA_AFFIX_NEG.add("of Fear");
    }

    /**
     * Loads all configuration settings from the casino_settings.json file.
     * Called once during onApplicationLoad().
     *
     * ERROR HANDLING:
     * - If JSON file is missing or malformed, defaults are used
     * - Missing individual keys use default values
     * - Invalid array values are skipped with warning logged
     *
     * AI_AGENT NOTE: This method should never be called after initialization.
     * All config values are static final in practice (though not marked final for JSON loading).
     */
    public static void loadSettings() {
        try {
            JSONObject settings = Global.getSettings().loadJSON("data/config/casino_settings.json", "the_casino_mod");

            // Load VIP settings
            if (settings.has("vipDailyReward")) {
                VIP_DAILY_REWARD = settings.getInt("vipDailyReward");
            }
            if (settings.has("vipPassDays")) {
                VIP_PASS_DAYS = settings.getInt("vipPassDays");
            }
            if (settings.has("vipDailyInterestRate")) {
                VIP_DAILY_INTEREST_RATE = (float) settings.getDouble("vipDailyInterestRate");
            }
            if (settings.has("normalDailyInterestRate")) {
                NORMAL_DAILY_INTEREST_RATE = (float) settings.getDouble("normalDailyInterestRate");
            }
            if (settings.has("baseDebtCeiling")) {
                BASE_DEBT_CEILING = settings.getInt("baseDebtCeiling");
            }
            if (settings.has("ceilingIncreasePerVIP")) {
                CEILING_INCREASE_PER_VIP = settings.getInt("ceilingIncreasePerVIP");
            }
            if (settings.has("vipPassCost")) {
                VIP_PASS_COST = settings.getInt("vipPassCost");
            }

            // Load poker settings
            if (settings.has("pokerSmallBlind")) {
                POKER_SMALL_BLIND = settings.getInt("pokerSmallBlind");
            }
            if (settings.has("pokerBigBlind")) {
                POKER_BIG_BLIND = settings.getInt("pokerBigBlind");
            }
            if (settings.has("pokerDefaultOpponentStack")) {
                POKER_DEFAULT_OPPONENT_STACK = settings.getInt("pokerDefaultOpponentStack");
            }
            if (settings.has("pokerStackSizes")) {
                JSONArray stackSizes = settings.getJSONArray("pokerStackSizes");
                POKER_STACK_SIZES = new int[stackSizes.length()];
                for (int i = 0; i < stackSizes.length(); i++) {
                    POKER_STACK_SIZES[i] = stackSizes.getInt(i);
                }
            }
            
            // Load poker AI settings
            if (settings.has("pokerAITightThresholdRaise")) {
                POKER_AI_TIGHT_THRESHOLD_RAISE = (float) settings.getDouble("pokerAITightThresholdRaise");
            }
            if (settings.has("pokerAITightThresholdFold")) {
                POKER_AI_TIGHT_THRESHOLD_FOLD = (float) settings.getDouble("pokerAITightThresholdFold");
            }
            if (settings.has("pokerAIStackPercentFoldTight")) {
                POKER_AI_STACK_PERCENT_FOLD_TIGHT = (float) settings.getDouble("pokerAIStackPercentFoldTight");
            }
            if (settings.has("pokerAIAggressiveThresholdRaise")) {
                POKER_AI_AGGRESSIVE_THRESHOLD_RAISE = (float) settings.getDouble("pokerAIAggressiveThresholdRaise");
            }
            if (settings.has("pokerAIAggressiveThresholdFold")) {
                POKER_AI_AGGRESSIVE_THRESHOLD_FOLD = (float) settings.getDouble("pokerAIAggressiveThresholdFold");
            }
            if (settings.has("pokerAINormalThresholdRaise")) {
                POKER_AI_NORMAL_THRESHOLD_RAISE = (float) settings.getDouble("pokerAINormalThresholdRaise");
            }
            if (settings.has("pokerAINormalThresholdFold")) {
                POKER_AI_NORMAL_THRESHOLD_FOLD = (float) settings.getDouble("pokerAINormalThresholdFold");
            }
            if (settings.has("pokerAIStackPercentCallLimit")) {
                POKER_AI_STACK_PERCENT_CALL_LIMIT = (float) settings.getDouble("pokerAIStackPercentCallLimit");
            }
            if (settings.has("pokerRaiseAmounts")) {
                JSONArray raiseAmounts = settings.getJSONArray("pokerRaiseAmounts");
                POKER_RAISE_AMOUNTS = new int[raiseAmounts.length()];
                for (int i = 0; i < raiseAmounts.length(); i++) {
                    POKER_RAISE_AMOUNTS[i] = raiseAmounts.getInt(i);
                }
            }
            if (settings.has("pokerAIMaxRaiseRandomAddition")) {
                POKER_AI_MAX_RAISE_RANDOM_ADDITION = settings.getInt("pokerAIMaxRaiseRandomAddition");
            }
            if (settings.has("pokerAIMinRaiseValue")) {
                POKER_AI_MIN_RAISE_VALUE = settings.getInt("pokerAIMinRaiseValue");
            }

            // Load gacha settings
            if (settings.has("gachaCost")) {
                GACHA_COST = settings.getInt("gachaCost");
            }
            if (settings.has("pityHard5")) {
                PITY_HARD_5 = settings.getInt("pityHard5");
            }
            if (settings.has("pitySoftStart5")) {
                PITY_SOFT_START_5 = settings.getInt("pitySoftStart5");
            }
            if (settings.has("pityHard4")) {
                PITY_HARD_4 = settings.getInt("pityHard4");
            }
            if (settings.has("gachaRotationDays")) {
                GACHA_ROTATION_DAYS = settings.getInt("gachaRotationDays");
            }
            if (settings.has("shipTradeRate")) {
                SHIP_TRADE_RATE = (float) settings.getDouble("shipTradeRate");
            }
            if (settings.has("shipSellMultiplier")) {
                SHIP_SELL_MULTIPLIER = (float) settings.getDouble("shipSellMultiplier");
            }

            // Load arena settings
            if (settings.has("arenaShipCount")) {
                ARENA_SHIP_COUNT = settings.getInt("arenaShipCount");
            }
            if (settings.has("arenaAgilityCap")) {
                ARENA_AGILITY_CAP = (float) settings.getDouble("arenaAgilityCap");
            }
            if (settings.has("arenaBaseOdds")) {
                ARENA_BASE_ODDS = (float) settings.getDouble("arenaBaseOdds");
            }
            if (settings.has("arenaPositivePerkMultiplier")) {
                ARENA_POSITIVE_PERK_MULTIPLIER = (float) settings.getDouble("arenaPositivePerkMultiplier");
            }
            if (settings.has("arenaNegativePerkMultiplier")) {
                ARENA_NEGATIVE_PERK_MULTIPLIER = (float) settings.getDouble("arenaNegativePerkMultiplier");
            }
            if (settings.has("arenaMinOdds")) {
                ARENA_MIN_ODDS = (float) settings.getDouble("arenaMinOdds");
            }
            if (settings.has("arenaSwitchCostPercent")) {
                ARENA_SWITCH_COST_PERCENT = (float) settings.getDouble("arenaSwitchCostPercent");
            }
            if (settings.has("arenaSwitchMultiplierPenalty")) {
                ARENA_SWITCH_MULTIPLIER_PENALTY = (float) settings.getDouble("arenaSwitchMultiplierPenalty");
            }
            if (settings.has("arenaEntryFee")) {
                ARENA_ENTRY_FEE = settings.getInt("arenaEntryFee");
            }
            if (settings.has("arenaSurvivalRewardMult")) {
                ARENA_SURVIVAL_REWARD_MULT = (float) settings.getDouble("arenaSurvivalRewardMult");
            }
            if (settings.has("arenaSurvivalBonusPerTurn")) {
                ARENA_SURVIVAL_BONUS_PER_TURN = (float) settings.getDouble("arenaSurvivalBonusPerTurn");
            }
            if (settings.has("arenaKillBonusPerKill")) {
                ARENA_KILL_BONUS_PER_KILL = (float) settings.getDouble("arenaKillBonusPerKill");
            }
            
            // Load arena prefix/affix multipliers
            if (settings.has("arenaPrefixMultStrong")) {
                ARENA_PREFIX_MULT_STRONG = (float) settings.getDouble("arenaPrefixMultStrong");
            }
            if (settings.has("arenaPrefixMultWeak")) {
                ARENA_PREFIX_MULT_WEAK = (float) settings.getDouble("arenaPrefixMultWeak");
            }
            if (settings.has("arenaPrefixAgilityBonus")) {
                ARENA_PREFIX_AGILITY_BONUS = (float) settings.getDouble("arenaPrefixAgilityBonus");
            }
            if (settings.has("arenaPrefixBraveryBonus")) {
                ARENA_PREFIX_BRAVERY_BONUS = (float) settings.getDouble("arenaPrefixBraveryBonus");
            }
            if (settings.has("arenaAffixMultStrong")) {
                ARENA_AFFIX_MULT_STRONG = (float) settings.getDouble("arenaAffixMultStrong");
            }
            if (settings.has("arenaAffixMultWeak")) {
                ARENA_AFFIX_MULT_WEAK = (float) settings.getDouble("arenaAffixMultWeak");
            }
            if (settings.has("arenaAffixAgilityBonus")) {
                ARENA_AFFIX_AGILITY_BONUS = (float) settings.getDouble("arenaAffixAgilityBonus");
            }
            if (settings.has("arenaAffixBraveryBonus")) {
                ARENA_AFFIX_BRAVERY_BONUS = (float) settings.getDouble("arenaAffixBraveryBonus");
            }
            
            // Load arena chaos event settings
            if (settings.has("arenaChaosEventChance")) {
                ARENA_CHAOS_EVENT_CHANCE = (float) settings.getDouble("arenaChaosEventChance");
            }
            if (settings.has("arenaHullBreachDamagePercent")) {
                ARENA_HULL_BREACH_DAMAGE_PERCENT = (float) settings.getDouble("arenaHullBreachDamagePercent");
            }
            
            // Load arena base stats from JSON if provided
            if (settings.has("arenaBaseStats")) {
                try {
                    JSONObject baseStats = settings.getJSONObject("arenaBaseStats");
                    for (com.fs.starfarer.api.combat.ShipAPI.HullSize size : ARENA_BASE_STATS.keySet()) {
                        String key = size.toString().toLowerCase();
                        if (baseStats.has(key)) {
                            JSONObject stat = baseStats.getJSONObject(key);
                            ArenaStat arenaStat = ARENA_BASE_STATS.get(size);
                            if (stat.has("hp")) arenaStat.hp = stat.getInt("hp");
                            if (stat.has("power")) arenaStat.power = stat.getInt("power");
                            if (stat.has("agility")) arenaStat.agility = (float) stat.getDouble("agility");
                        }
                    }
                } catch (JSONException e) {
                    Global.getLogger(CasinoConfig.class).warn("Error loading arenaBaseStats: " + e.getMessage());
                }
            }

            // Load debt settings
            if (settings.has("debtCollectorThreshold")) {
                DEBT_COLLECTOR_THRESHOLD = settings.getInt("debtCollectorThreshold");
                VIP_DEBT_HUNTER_THRESHOLD = DEBT_COLLECTOR_THRESHOLD; // Keep alias in sync
            }
            if (settings.has("interestDays")) {
                JSONArray interestDays = settings.getJSONArray("interestDays");
                INTEREST_DAYS = new int[interestDays.length()];
                for (int i = 0; i < interestDays.length(); i++) {
                    INTEREST_DAYS[i] = interestDays.getInt(i);
                }
            }
            
            // Load credit ceiling settings
            if (settings.has("topupCeilingMultiplier")) {
                TOPUP_CEILING_MULTIPLIER = (float) settings.getDouble("topupCeilingMultiplier");
            }

            // Load market settings
            if (settings.has("marketSizeMinForPlayerCasino")) {
                MARKET_SIZE_MIN_FOR_PLAYER_CASINO = settings.getInt("marketSizeMinForPlayerCasino");
            }
            if (settings.has("marketSizeMinForGeneralCasino")) {
                MARKET_SIZE_MIN_FOR_GENERAL_CASINO = settings.getInt("marketSizeMinForGeneralCasino");
            }

            // Load exchange rate
            if (settings.has("stargemExchangeRate")) {
                STARGEM_EXCHANGE_RATE = (float) settings.getDouble("stargemExchangeRate");
            }

            // Load VIP ads
            loadStringList(settings, "vipAds", VIP_ADS);

            // Load gem packages
            if (settings.has("gemPackages")) {
                try {
                    JSONArray packages = settings.getJSONArray("gemPackages");
                    GEM_PACKAGES.clear();
                    for (int i = 0; i < packages.length(); i++) {
                        JSONObject pkg = packages.getJSONObject(i);
                        int gems = pkg.getInt("gems");
                        int cost = pkg.getInt("cost");
                        GEM_PACKAGES.add(new GemPackage(gems, cost));
                    }
                } catch (JSONException e) {
                    Global.getLogger(CasinoConfig.class).warn("Error loading gemPackages: " + e.getMessage());
                }
            }

            // Sync NON_VIP_DAILY_INTEREST_RATE with NORMAL_DAILY_INTEREST_RATE
            NON_VIP_DAILY_INTEREST_RATE = NORMAL_DAILY_INTEREST_RATE;

            // Load flavor text lists
            loadStringList(settings, "arenaAttackLines", ARENA_ATTACK_LINES);
            loadStringList(settings, "arenaMissLines", ARENA_MISS_LINES);
            loadStringList(settings, "arenaCritLines", ARENA_CRIT_LINES);
            loadStringList(settings, "arenaKillLines", ARENA_KILL_LINES);
            loadStringList(settings, "arenaPrefixStrongPos", ARENA_PREFIX_STRONG_POS);
            loadStringList(settings, "arenaPrefixStrongNeg", ARENA_PREFIX_STRONG_NEG);
            loadStringList(settings, "arenaAffixPos", ARENA_AFFIX_POS);
            loadStringList(settings, "arenaAffixNeg", ARENA_AFFIX_NEG);

            // Load ship blacklist (JSON - deprecated)
            loadStringSet(settings, "shipBlacklist", GACHA_SHIP_BLACKLIST);
            
            // Load gacha probabilities
            if (settings.has("prob5Star")) {
                PROB_5_STAR = (float) settings.getDouble("prob5Star");
            }
            if (settings.has("prob4Star")) {
                PROB_4_STAR = (float) settings.getDouble("prob4Star");
            }

            Global.getLogger(CasinoConfig.class).info("Casino configuration loaded successfully");

        } catch (IOException | JSONException e) {
            Global.getLogger(CasinoConfig.class).error("Error loading casino configuration: " + e.getMessage());
            Global.getLogger(CasinoConfig.class).info("Using default configuration values");
        }
        
        // Load CSV blacklist (outside try-catch to ensure it always runs)
        loadGachaShipsBlacklist();
    }
    
    /**
     * Loads the gacha ship blacklist from CSV file.
     * Uses getMergedSpreadsheetDataForMod() to allow other mods to add entries.
     * 
     * AI_AGENT NOTE: This method is called after JSON config loading.
     * The CSV format allows modders to easily add ships to the blacklist
     * without modifying the core mod files.
     */
    private static void loadGachaShipsBlacklist() {
        try {
            GACHA_SHIP_BLACKLIST_CSV.clear();
            JSONArray csv = Global.getSettings().getMergedSpreadsheetDataForMod("id", 
                    GACHA_SHIPS_BLACKLIST_CSV, "the_casino_mod");
            for (int i = 0; i < csv.length(); i++) {
                JSONObject row = csv.getJSONObject(i);
                String hullId = row.getString("id");
                if (hullId != null && !hullId.isEmpty()) {
                    GACHA_SHIP_BLACKLIST_CSV.add(hullId);
                }
            }
            Global.getLogger(CasinoConfig.class).info("Loaded " + GACHA_SHIP_BLACKLIST_CSV.size() + " blacklisted ships from CSV");
        } catch (Exception e) {
            Global.getLogger(CasinoConfig.class).warn("Could not load gacha ship blacklist CSV, using empty set.", e);
        }
    }

    /**
     * Helper method to load a list of strings from JSON.
     * 
     * @param settings The JSON object containing the configuration
     * @param key The key for the array to load
     * @param list The list to populate with values
     * 
     * AI_AGENT NOTE: This method clears the list before adding new values.
     * Default values are preserved if the JSON key is missing.
     */
    private static void loadStringList(JSONObject settings, String key, List<String> list) {
        if (settings.has(key)) {
            try {
                JSONArray array = settings.getJSONArray(key);
                list.clear();
                for (int i = 0; i < array.length(); i++) {
                    list.add(array.getString(i));
                }
            } catch (JSONException e) {
                Global.getLogger(CasinoConfig.class).warn("Error loading " + key + ": " + e.getMessage());
            }
        }
    }

    /**
     * Helper method to load a set of strings from JSON.
     * 
     * @param settings The JSON object containing the configuration
     * @param key The key for the array to load
     * @param set The set to populate with values
     * 
     * AI_AGENT NOTE: Sets are used for O(1) lookup performance (e.g., ship blacklist).
     */
    private static void loadStringSet(JSONObject settings, String key, Set<String> set) {
        if (settings.has(key)) {
            try {
                JSONArray array = settings.getJSONArray(key);
                set.clear();
                for (int i = 0; i < array.length(); i++) {
                    set.add(array.getString(i));
                }
            } catch (JSONException e) {
                Global.getLogger(CasinoConfig.class).warn("Error loading " + key + ": " + e.getMessage());
            }
        }
    }
}
