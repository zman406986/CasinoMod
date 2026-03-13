package data.scripts.casino;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;

import static data.scripts.constants.Mods.*;

/**
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
    private static final Logger log = Global.getLogger(CasinoConfig.class);
    private static final String CONFIG_PATH = "data/config/casino_settings.json";
    private static final String GACHA_SHIPS_BLACKLIST_CSV = "data/config/gacha_ships_blacklist.csv";

    // VIP System Configuration
    /** Daily reward amount for VIP subscribers (in Stargems) */
    public static int VIP_DAILY_REWARD;
    /** Number of days a VIP pass lasts */
    public static int VIP_PASS_DAYS ;
    /** Daily interest rate for VIP members */
    public static float VIP_DAILY_INTEREST_RATE;
    /** Daily interest rate for non-VIP members */
    public static float NORMAL_DAILY_INTEREST_RATE;
    /** Alias for NORMAL_DAILY_INTEREST_RATE - used in some contexts */
    public static float NON_VIP_DAILY_INTEREST_RATE;
    /** Base credit ceiling for new accounts (in Stargems) */
    public static int BASE_DEBT_CEILING;
    /** Additional credit ceiling per VIP pass purchase (in Stargems) */
    public static int CEILING_INCREASE_PER_VIP;
    /** Cost of one VIP pass (in credits) */
    public static int VIP_PASS_COST;

    // Poker Configuration
    /** Small blind amount for poker games (in Stargems) */
    public static int POKER_SMALL_BLIND;
    /** Big blind amount for poker games (in Stargems) */
    public static int POKER_BIG_BLIND;
    /** Default opponent stack size (in Stargems) */
    public static int POKER_DEFAULT_OPPONENT_STACK;
    /** Available stack sizes for player to choose from (in Stargems) */
    public static int[] POKER_STACK_SIZES = {1000, 2500, 5000, 10000};

    // Poker Raise Configuration
    /** Maximum random addition to AI raise (in Stargems) */
    public static int POKER_AI_MAX_RAISE_RANDOM_ADDITION;
    /** Minimum raise value (in Stargems) */
    public static int POKER_AI_MIN_RAISE_VALUE;
    /** Number of Monte Carlo simulations for poker equity calculation (higher = more accurate but slower) */
    public static int POKER_MONTE_CARLO_SAMPLES;

    // Gacha Configuration
    /** Cost per gacha pull (in Stargems) */
    public static int GACHA_COST;
    /** Number of ships in the gacha pool (default 32 = 1.6x of traditional 20) */
    public static int GACHA_POOL_SIZE;
    /** Hard pity threshold for 5-star items (guaranteed at this count) */
    public static int PITY_HARD_5;
    /** Soft pity start threshold for 5-star items (rate increases after this) */
    public static int PITY_SOFT_START_5;
    /** Hard pity threshold for 4-star items */
    public static int PITY_HARD_4;
    /** Number of days between featured ship rotations */
    public static int GACHA_ROTATION_DAYS;
    /** Exchange rate for converting ships to Stargems (base_value / this = gems) */
    public static float SHIP_TRADE_RATE;
    /** Multiplier applied to ship sell values to create house edge (0.9 = 90% of calculated value) */
    public static float SHIP_SELL_MULTIPLIER;

    // Arena Configuration
    /** Number of ships in each arena battle */
    public static int ARENA_SHIP_COUNT;
    /** Maximum agility value for arena ships (prevents invincibility) */
    public static float ARENA_AGILITY_CAP;
    /**
     * Base odds multiplier for arena betting.
     * 
     * MATHEMATICAL ANALYSIS for 10% house edge on random bets:
     * - With 5 ships, random bet has 20% win probability
     * - For 10% house edge: player should get 90% of fair return
     * - Fair odds = 1/0.2 = 5.0
     * - Target effective payout = 5.0 * 0.9 = 4.5
     * 
     * Ship prefixes and affixes modify stats, which affects win probability
     * and thus the odds calculated via Monte Carlo simulation.
     */
    public static float ARENA_BASE_ODDS;
    /** Minimum odds regardless of perks (prevents 0 return, 1.01 ensures player gets bet back + 1%) */
    public static float ARENA_MIN_ODDS;
    /** House edge percentage (0.10 = 10% house edge, ensures betting on all ships is not profitable) */
    public static float ARENA_HOUSE_EDGE;
    /** Entry fee for arena betting (default bet amount) */
    public static int ARENA_ENTRY_FEE;
    /** Survival bonus per turn survived in arena (reduced to balance house edge) */
    public static float ARENA_SURVIVAL_BONUS_PER_TURN;
    /** Kill bonus per kill in arena (reduced to balance house edge) */
    public static float ARENA_KILL_BONUS_PER_KILL;
    /** Multiplier for consolation rewards given to defeated champions (0.5 = 50% of calculated value) */
    public static float ARENA_DEFEATED_CONSOLATION_MULT;

    /**
     * Position factors for consolation calculation. Index 0 = 2nd place, 1 = 3rd place, etc.
     * 
     * MATHEMATICAL ANALYSIS:
     * These factors determine what percentage of the odds a player gets back as consolation.
     * Combined with ARENA_DEFEATED_CONSOLATION_MULT, they affect the odds calculation.
     * 
     * Formula: odds = (1 - houseEdge) / (P_win + Σ P_position × positionFactor × consolationMult)
     * 
     * With factors [0.50, 0.25, 0.10, 0.05] and consolationMult 0.5:
     * - 2nd place gets: odds × 0.50 × 0.5 = 25% of odds as consolation
     * - 3rd place gets: odds × 0.25 × 0.5 = 12.5% of odds as consolation
     * 
     * Example with 5 equal ships (20% win each) and 10% house edge:
     * - Total expected return per ship = 0.20 + (0.20×0.50 + 0.20×0.25 + ...)×0.5 = 0.275
     * - Odds = 0.90 / 0.275 = 3.27x
     * - 2nd place return = 100 × 3.27 × 0.50 × 0.5 = 82 (82% of bet returned)
     * - With 2 kills: 82 × 1.2 = 98 (nearly break-even!)
     */
    public static float[] ARENA_CONSOLATION_POSITION_FACTORS = {0.50f, 0.25f, 0.10f, 0.05f};
    /** Action multiplier for arena combat - increases actions per round (1.5 = 50% more actions) */
    public static float ARENA_ACTION_MULTIPLIER;
    /** Diminishing returns per round for late bets (0.20 = 20% reduction per round, min 0.4) */
    public static float ARENA_DIMINISHING_RETURNS_PER_ROUND;
    /** Minimum diminishing returns multiplier (0.25 = 25% of original value) */
    public static float ARENA_DIMINISHING_RETURNS_MIN;
    
    // Arena Simulation Configuration
    /** Number of Monte Carlo simulations for odds calculation (higher = more accurate but slower) */
    public static int ARENA_SIMULATION_COUNT;
    /** Base penalty for mid-round betting (0.5 = 50% reduction) */
    public static float ARENA_MID_ROUND_BASE_PENALTY;
    /** Progressive penalty per round for mid-round betting (0.15 = 15% additional reduction per round) */
    public static float ARENA_MID_ROUND_PROGRESSIVE_PENALTY;
    /** Maximum bet amount on a single champion per round (in Stargems) */
    public static int ARENA_MAX_BET_PER_CHAMPION;
    
    // HP-Based Dynamic Odds Configuration
    /** Factor for how much HP affects odds mid-battle (2.0 = up to 2x difference between low and high HP) */
    public static float ARENA_HP_ODDS_FACTOR;
    /** Maximum odds multiplier from HP factor (prevents extreme odds) */
    public static float ARENA_MAX_HP_ODDS_MULT;
    /** Minimum odds multiplier from HP factor */
    public static float ARENA_MIN_HP_ODDS_MULT;

    // Credit Ceiling Configuration
    /** Maximum debt multiplier - debt cannot exceed credit_ceiling * this value (default 2.0 = 2x ceiling) */
    public static float MAX_DEBT_MULTIPLIER;
    /** Multiplier for player level to calculate overdraft ceiling (default 1000 = level 10 player has 10,000 ceiling) */
    public static float OVERDRAFT_CEILING_LEVEL_MULTIPLIER;
    /** Debt collector threshold as percentage above ceiling (0.0 = 0%, spawn immediately when exceeding ceiling) */
    public static float DEBT_COLLECTOR_THRESHOLD_PERCENT;

    // Market Configuration
    /** Minimum market size for player-owned casinos */
    public static int MARKET_SIZE_MIN_FOR_PLAYER_CASINO;
    /** Minimum market size for general casinos (NPC markets) */
    public static int MARKET_SIZE_MIN_FOR_GENERAL_CASINO;

    // Exchange Rate
    /** Credits per Stargem when converting (1000 = 1 Gem = 1000 Credits) */
    public static float STARGEM_EXCHANGE_RATE;

    // VIP Ads (flavor text for daily notifications)
    /** List of VIP advertisement messages shown with daily rewards */
    public static final List<String> VIP_ADS = new ArrayList<>();

    // Gem Packages for Top-up
    /** List of available gem packages for purchase */
    public static final List<GemPackage> GEM_PACKAGES = new ArrayList<>();

    /**
     * Represents a gem package available for purchase.
     */
    public static class GemPackage {
        public final int gems;
        public final int cost;

        public GemPackage(int gems, int cost) {
            this.gems = gems;
            this.cost = cost;
        }
    }

    /**
     * Base stats for arena ships by hull size.
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
    public static final Map<HullSize, ArenaStat> ARENA_BASE_STATS = new HashMap<>();
    
    // Arena Prefix Multipliers (Strong/Weak)
    /** Multiplier for strong prefix effects (e.g., Giant, Strong) */
    public static float ARENA_PREFIX_MULT_STRONG;
    /** Multiplier for weak prefix effects (e.g., Tiny, Weak) */
    public static float ARENA_PREFIX_MULT_WEAK;
    /** Agility bonus/penalty from Swift/Clumsy prefixes */
    public static float ARENA_PREFIX_AGILITY_BONUS;
    /** Bravery bonus/penalty from Fierce/Cowardly prefixes */
    public static float ARENA_PREFIX_BRAVERY_BONUS;
    
    // Arena Affix Multipliers (of Might/of Frailty, etc.)
    /** Multiplier for strong affix effects */
    public static float ARENA_AFFIX_MULT_STRONG;
    /** Multiplier for weak affix effects */
    public static float ARENA_AFFIX_MULT_WEAK;
    /** Agility bonus/penalty from Speed/Slowness affixes */
    public static float ARENA_AFFIX_AGILITY_BONUS;
    /** Bravery bonus/penalty from Courage/Fear affixes */
    public static float ARENA_AFFIX_BRAVERY_BONUS;
    
    // Arena Chaos Event Configuration
    /** Chance per simulation step for a chaos event to occur (0.1 = 10%) */
    public static float ARENA_CHAOS_EVENT_CHANCE;
    /** Hull damage percentage from single ship damage event (0.15 = 15% of max HP) */
    public static float ARENA_SINGLE_SHIP_DAMAGE_PERCENT;
    /** Hull damage percentage from multi ship damage event (0.10 = 10% of max HP per ship) */
    public static float ARENA_MULTI_SHIP_DAMAGE_PERCENT;
    /** Descriptions for single ship damage events (use $ship for ship name placeholder) */
    public static final List<String> ARENA_SINGLE_SHIP_DAMAGE_DESCRIPTIONS = new ArrayList<>();
    /** Descriptions for multi ship damage events */
    public static final List<String> ARENA_MULTI_SHIP_DAMAGE_DESCRIPTIONS = new ArrayList<>();
    
    // Flavor Text Lists (initialized with defaults, overwritten from JSON)
    /** Attack flavor text lines for arena combat (referenced as ARENA_FLAVOR_TEXTS in arena code) */
    public static final List<String> ARENA_ATTACK_LINES = new ArrayList<>();
    /** Alias for ARENA_ATTACK_LINES - used by SpiralAbyssArena */
    public static final List<String> ARENA_FLAVOR_TEXTS = ARENA_ATTACK_LINES;
    /** Miss flavor text lines for arena combat (referenced as ARENA_MISS_FLAVOR_TEXTS in arena code) */
    public static final List<String> ARENA_MISS_LINES = new ArrayList<>();
    /** Alias for ARENA_MISS_LINES - used by SpiralAbyssArena */
    public static final List<String> ARENA_MISS_FLAVOR_TEXTS = ARENA_MISS_LINES;
    /** Critical hit flavor text lines for arena combat (referenced as ARENA_CRIT_FLAVOR_TEXTS in arena code) */
    public static final List<String> ARENA_CRIT_LINES = new ArrayList<>();
    /** Alias for ARENA_CRIT_LINES - used by SpiralAbyssArena */
    public static final List<String> ARENA_CRIT_FLAVOR_TEXTS = ARENA_CRIT_LINES;
    /** Kill flavor text lines for arena combat (referenced as ARENA_KILL_FLAVOR_TEXTS in arena code) */
    public static final List<String> ARENA_KILL_LINES = new ArrayList<>();
    /** Alias for ARENA_KILL_LINES - used by SpiralAbyssArena */
    public static final List<String> ARENA_KILL_FLAVOR_TEXTS = ARENA_KILL_LINES;
    /** Positive prefixes that buff arena ships */
    public static final List<String> ARENA_PREFIX_STRONG_POS = new ArrayList<>();
    /** Negative prefixes that debuff arena ships */
    public static final List<String> ARENA_PREFIX_STRONG_NEG = new ArrayList<>();
    /** Positive affixes that buff arena ships */
    public static final List<String> ARENA_AFFIX_POS = new ArrayList<>();
    /** Negative affixes that debuff arena ships */
    public static final List<String> ARENA_AFFIX_NEG = new ArrayList<>();

    // Ship Blacklist (hulls excluded from gacha pool)
    /** Ship hull IDs that should never appear in gacha pulls (from CSV - allows mod merging) */
    public static final Set<String> GACHA_SHIP_BLACKLIST_CSV = new HashSet<>();
    
    // Gacha Probability Configuration
    /** Base probability for 5-star (capital ship) pull (0.006 = 0.6%) */
    public static float PROB_5_STAR;
    /** Base probability for 4-star (cruiser) pull (0.051 = 5.1%) */
    public static float PROB_4_STAR;

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
    public static final void loadSettings() {
        try {
            final JSONObject config = Global.getSettings().loadJSON(CONFIG_PATH, CasionMod);

            // Load VIP settings
            VIP_DAILY_REWARD = config.optInt("vipDailyReward", 100);
            VIP_PASS_DAYS = config.optInt("vipPassDays", 30);
            VIP_DAILY_INTEREST_RATE = (float) config.optDouble("vipDailyInterestRate", 0.02);
            NORMAL_DAILY_INTEREST_RATE = (float) config.optDouble("normalDailyInterestRate", 0.05);
            BASE_DEBT_CEILING = config.optInt("baseDebtCeiling", 5000);
            CEILING_INCREASE_PER_VIP = config.optInt("ceilingIncreasePerVIP", 10000);
            VIP_PASS_COST = config.optInt("vipPassCost", 100000);

            // Load poker settings
            POKER_SMALL_BLIND = config.optInt("pokerSmallBlind", 50);
            POKER_BIG_BLIND = config.optInt("pokerBigBlind", 100);
            POKER_DEFAULT_OPPONENT_STACK = config.optInt("pokerDefaultOpponentStack", 10000);
            if (config.has("pokerStackSizes")) {
                final JSONArray stackSizes = config.getJSONArray("pokerStackSizes");
                POKER_STACK_SIZES = new int[stackSizes.length()];
                for (int i = 0; i < stackSizes.length(); i++) {
                    POKER_STACK_SIZES[i] = stackSizes.optInt(i);
                }
            }

            POKER_AI_MAX_RAISE_RANDOM_ADDITION = config.optInt("pokerAIMaxRaiseRandomAddition", 200);
            POKER_AI_MIN_RAISE_VALUE = config.optInt("pokerAIMinRaiseValue", 200);
            POKER_MONTE_CARLO_SAMPLES = config.optInt("pokerMonteCarloSamples", 2000);

            // Load gacha settings
            GACHA_COST = config.optInt("gachaCost", 160);
            GACHA_POOL_SIZE = config.optInt("gachaPoolSize", 32);
            PITY_HARD_5 = config.optInt("pityHard5", 90);
            PITY_SOFT_START_5 = config.optInt("pitySoftStart5", 73);
            PITY_HARD_4 = config.optInt("pityHard4", 10);
            GACHA_ROTATION_DAYS = config.optInt("gachaRotationDays", 14);
            SHIP_TRADE_RATE = (float) config.optDouble("shipTradeRate", 1000);
            SHIP_SELL_MULTIPLIER = (float) config.optDouble("shipSellMultiplier", 0.9);

            // Load arena settings
            ARENA_SHIP_COUNT = config.optInt("arenaShipCount", 5);
            ARENA_AGILITY_CAP = (float) config.optDouble("arenaAgilityCap", 0.75);
            ARENA_BASE_ODDS = (float) config.optDouble("arenaBaseOdds", 5.0);
            ARENA_MIN_ODDS = (float) config.optDouble("arenaMinOdds", 1.01);
            ARENA_HOUSE_EDGE = (float) config.optDouble("arenaHouseEdge", 0.1);
            ARENA_ENTRY_FEE = config.optInt("arenaEntryFee", 100);
            ARENA_SURVIVAL_BONUS_PER_TURN = (float) config.optDouble("arenaSurvivalBonusPerTurn", 0.05);
            ARENA_KILL_BONUS_PER_KILL = (float) config.optDouble("arenaKillBonusPerKill", 0.1);
            ARENA_DEFEATED_CONSOLATION_MULT = (float) config.optDouble("arenaDefeatedConsolationMult", 0.5);

            if (config.has("arenaConsolationPositionFactors")) {
                final JSONArray factors = config.getJSONArray("arenaConsolationPositionFactors");
                ARENA_CONSOLATION_POSITION_FACTORS = new float[factors.length()];
                for (int i = 0; i < factors.length(); i++) {
                    ARENA_CONSOLATION_POSITION_FACTORS[i] = (float) factors.getDouble(i);
                }
            }
            ARENA_ACTION_MULTIPLIER = (float) config.optDouble("arenaActionMultiplier", 1.5);
            ARENA_DIMINISHING_RETURNS_PER_ROUND = (float) config.optDouble("arenaDiminishingReturnsPerRound", 0.2);
            ARENA_DIMINISHING_RETURNS_MIN = (float) config.optDouble("arenaDiminishingReturnsMin", 0.25);
            
            // Load arena simulation settings
            ARENA_SIMULATION_COUNT = config.optInt("arenaSimulationCount", 500);
            ARENA_MID_ROUND_BASE_PENALTY = (float) config.optDouble("arenaMidRoundBasePenalty", 0.5);
            ARENA_MID_ROUND_PROGRESSIVE_PENALTY = (float) config.optDouble("arenaMidRoundProgressivePenalty", 0.15);
            ARENA_MAX_BET_PER_CHAMPION = config.optInt("arenaMaxBetPerChampion", 10000);
            
            // Load HP-based dynamic odds settings
            ARENA_HP_ODDS_FACTOR = (float) config.optDouble("arenaHpOddsFactor", 2.0);
            ARENA_MAX_HP_ODDS_MULT = (float) config.optDouble("arenaMaxHpOddsMult", 3.0);
            ARENA_MIN_HP_ODDS_MULT = (float) config.optDouble("arenaMinHpOddsMult", 0.5);
            
            // Load arena prefix/affix multipliers
            ARENA_PREFIX_MULT_STRONG = (float) config.optDouble("arenaPrefixMultStrong", 1.3);
            ARENA_PREFIX_MULT_WEAK = (float) config.optDouble("arenaPrefixMultWeak", 0.7);
            ARENA_PREFIX_AGILITY_BONUS = (float) config.optDouble("arenaPrefixAgilityBonus", 0.15);
            ARENA_PREFIX_BRAVERY_BONUS = (float) config.optDouble("arenaPrefixBraveryBonus", 0.1);
            ARENA_AFFIX_MULT_STRONG = (float) config.optDouble("arenaAffixMultStrong", 1.2);
            ARENA_AFFIX_MULT_WEAK = (float) config.optDouble("arenaAffixMultWeak", 0.8);
            ARENA_AFFIX_AGILITY_BONUS = (float) config.optDouble("arenaAffixAgilityBonus", 0.1);
            ARENA_AFFIX_BRAVERY_BONUS = (float) config.optDouble("arenaAffixBraveryBonus", 0.08);
            
            // Load arena chaos event settings
            ARENA_CHAOS_EVENT_CHANCE = (float) config.optDouble("arenaChaosEventChance", 0.1);
            ARENA_SINGLE_SHIP_DAMAGE_PERCENT = (float) config.optDouble("arenaSingleShipDamagePercent", 0.15);
            ARENA_MULTI_SHIP_DAMAGE_PERCENT = (float) config.optDouble("arenaMultiShipDamagePercent", 0.1);
            
            // Load arena base stats from JSON if provided
            if (config.has("arenaBaseStats")) {
                final JSONObject baseStats = config.getJSONObject("arenaBaseStats");
                for (HullSize size : ARENA_BASE_STATS.keySet()) {
                    final String key = size.toString().toLowerCase();
                    if (baseStats.has(key)) {
                        final JSONObject stat = baseStats.getJSONObject(key);
                        final ArenaStat arenaStat = ARENA_BASE_STATS.get(size);
                        if (stat.has("hp")) arenaStat.hp = stat.getInt("hp");
                        if (stat.has("power")) arenaStat.power = stat.getInt("power");
                        if (stat.has("agility")) arenaStat.agility = (float) stat.getDouble("agility");
                    }
                }
            }

            // Load credit ceiling settings
            MAX_DEBT_MULTIPLIER = (float) config.optDouble("maxDebtMultiplier", 2.0);
            OVERDRAFT_CEILING_LEVEL_MULTIPLIER = (float) config.optDouble("overdraftCeilingLevelMultiplier", 1000.0);
            DEBT_COLLECTOR_THRESHOLD_PERCENT = (float) config.optDouble("debtCollectorThresholdPercent", 0.0);

            // Load market settings
            MARKET_SIZE_MIN_FOR_PLAYER_CASINO = config.optInt("marketSizeMinForPlayerCasino", 4);
            MARKET_SIZE_MIN_FOR_GENERAL_CASINO = config.optInt("marketSizeMinForGeneralCasino", 3);
            STARGEM_EXCHANGE_RATE = (float) config.optDouble("stargemExchangeRate", 1000.0);

            // Load VIP ads
            loadStringList(config, "vipAds", VIP_ADS);

            // Load gem packages
            if (config.has("gemPackages")) {
                final JSONArray packages = config.getJSONArray("gemPackages");
                GEM_PACKAGES.clear();
                for (int i = 0; i < packages.length(); i++) {
                    final JSONObject pkg = packages.getJSONObject(i);
                    final int gems = pkg.getInt("gems");
                    final int cost = pkg.getInt("cost");
                    GEM_PACKAGES.add(new GemPackage(gems, cost));
                }
            }

            // Sync NON_VIP_DAILY_INTEREST_RATE with NORMAL_DAILY_INTEREST_RATE
            NON_VIP_DAILY_INTEREST_RATE = NORMAL_DAILY_INTEREST_RATE;

            // Load flavor text lists
            loadStringList(config, "arenaAttackLines", ARENA_ATTACK_LINES);
            loadStringList(config, "arenaMissLines", ARENA_MISS_LINES);
            loadStringList(config, "arenaCritLines", ARENA_CRIT_LINES);
            loadStringList(config, "arenaKillLines", ARENA_KILL_LINES);
            loadStringList(config, "arenaPrefixStrongPos", ARENA_PREFIX_STRONG_POS);
            loadStringList(config, "arenaPrefixStrongNeg", ARENA_PREFIX_STRONG_NEG);
            loadStringList(config, "arenaAffixPos", ARENA_AFFIX_POS);
            loadStringList(config, "arenaAffixNeg", ARENA_AFFIX_NEG);
            
            // Load arena event description lists
            loadStringList(config, "arenaSingleShipDamageDescriptions", ARENA_SINGLE_SHIP_DAMAGE_DESCRIPTIONS);
            loadStringList(config, "arenaMultiShipDamageDescriptions", ARENA_MULTI_SHIP_DAMAGE_DESCRIPTIONS);

            // Load gacha probabilities
            PROB_5_STAR = (float) config.optDouble("prob5Star", 0.006);
            PROB_4_STAR = (float) config.optDouble("prob4Star", 0.051);

        } catch (IOException | JSONException e) {
            log.error("Error loading casino configuration: " + e.getMessage());
            log.info("Using default configuration values");
        }
        
        // Load CSV blacklist (outside try-catch to ensure it always runs)
        loadGachaShipsBlacklist();
    }
    
    /**
     * Loads the gacha ship blacklist from CSV file.
     * Uses getMergedSpreadsheetDataForMod() to allow other mods to add entries.
     */
    private static final void loadGachaShipsBlacklist() {
        try {
            GACHA_SHIP_BLACKLIST_CSV.clear();
            final JSONArray csv = Global.getSettings().getMergedSpreadsheetDataForMod(
                "id",  GACHA_SHIPS_BLACKLIST_CSV, CasionMod
            );
            for (int i = 0; i < csv.length(); i++) {
                final JSONObject row = csv.getJSONObject(i);
                final String hullId = row.getString("id");
                if (hullId != null && !hullId.isEmpty()) {
                    GACHA_SHIP_BLACKLIST_CSV.add(hullId);
                }
            }
            log.info("Loaded " + GACHA_SHIP_BLACKLIST_CSV.size() + " blacklisted ships from CSV");
        } catch (Exception e) {
            log.warn("Could not load gacha ship blacklist CSV, using empty set.", e);
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
    private static final void loadStringList(JSONObject settings, String key, List<String> list) {
        if (settings.has(key)) {
            try {
                final JSONArray array = settings.getJSONArray(key);
                list.clear();
                for (int i = 0; i < array.length(); i++) {
                    list.add(array.getString(i));
                }
            } catch (JSONException e) {
                log.warn("Error loading " + key + ": " + e.getMessage());
            }
        }
    }

    static { // Lazy loading
        loadSettings();

        // Arena base stats with default values
        ARENA_BASE_STATS.put(HullSize.FRIGATE, new ArenaStat(80, 25, 0.35f));
        ARENA_BASE_STATS.put(HullSize.DESTROYER, new ArenaStat(120, 35, 0.25f));
        ARENA_BASE_STATS.put(HullSize.CRUISER, new ArenaStat(180, 50, 0.15f));
        ARENA_BASE_STATS.put(HullSize.CAPITAL_SHIP, new ArenaStat(250, 70, 0.10f));
        
        // Default flavor text for arena
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
        
        // Default prefixes (must match casino_settings.json)
        ARENA_PREFIX_STRONG_POS.add("Durable");
        ARENA_PREFIX_STRONG_POS.add("Mightly");
        ARENA_PREFIX_STRONG_POS.add("Swift");
        ARENA_PREFIX_STRONG_POS.add("Fierce");
        
        ARENA_PREFIX_STRONG_NEG.add("Brittle");
        ARENA_PREFIX_STRONG_NEG.add("Feeble");
        ARENA_PREFIX_STRONG_NEG.add("Sluggish");
        ARENA_PREFIX_STRONG_NEG.add("Timid");
        
        // Default affixes (must match casino_settings.json)
        ARENA_AFFIX_POS.add("of Sturdiness");
        ARENA_AFFIX_POS.add("of Strength");
        ARENA_AFFIX_POS.add("of Speed");
        ARENA_AFFIX_POS.add("of Courage");
        
        ARENA_AFFIX_NEG.add("of Fragility");
        ARENA_AFFIX_NEG.add("of Weakness");
        ARENA_AFFIX_NEG.add("of Clumsiness");
        ARENA_AFFIX_NEG.add("of Cowardice");
        
        // Default single ship damage event descriptions
        ARENA_SINGLE_SHIP_DAMAGE_DESCRIPTIONS.add("Maintenance accident damages $ship");
        ARENA_SINGLE_SHIP_DAMAGE_DESCRIPTIONS.add("Asteroid impact hits $ship");
        ARENA_SINGLE_SHIP_DAMAGE_DESCRIPTIONS.add("Micro-meteorite swarm strikes $ship");
        ARENA_SINGLE_SHIP_DAMAGE_DESCRIPTIONS.add("Reactor fluctuation damages $ship");
        ARENA_SINGLE_SHIP_DAMAGE_DESCRIPTIONS.add("Structural fatigue weakens $ship");
        
        // Default multi ship damage event descriptions
        ARENA_MULTI_SHIP_DAMAGE_DESCRIPTIONS.add("Collision between ships causes damage");
        ARENA_MULTI_SHIP_DAMAGE_DESCRIPTIONS.add("Explosive debris strikes the arena");
        ARENA_MULTI_SHIP_DAMAGE_DESCRIPTIONS.add("Chain reaction explosion spreads across ships");
        ARENA_MULTI_SHIP_DAMAGE_DESCRIPTIONS.add("Electromagnetic pulse damages multiple systems");
        ARENA_MULTI_SHIP_DAMAGE_DESCRIPTIONS.add("Arena hazard activation damages combatants");
    }
}