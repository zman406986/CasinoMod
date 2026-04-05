package data.scripts.casino;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;

public class CasinoConfig {
    public static final String MOD_ID = "interastral_peace_casino";
    private static final Logger log = Global.getLogger(CasinoConfig.class);
    private static final String CONFIG_PATH = "data/config/casino_settings.json";
    private static final String GACHA_SHIPS_BLACKLIST_CSV = "data/config/gacha_ships_blacklist.csv";

    // VIP System
    public static int VIP_DAILY_REWARD;
    public static int VIP_PASS_DAYS;
    public static float VIP_DAILY_INTEREST_RATE;
    public static float NORMAL_DAILY_INTEREST_RATE;
    public static float NON_VIP_DAILY_INTEREST_RATE;
    public static int BASE_DEBT_CEILING;
    public static int CEILING_INCREASE_PER_VIP;
    public static int VIP_PASS_COST;

    // Poker
    public static int POKER_SMALL_BLIND;
    public static int POKER_BIG_BLIND;
    public static int POKER_DEFAULT_OPPONENT_STACK;
    public static int[] POKER_STACK_SIZES = {1000, 2500, 5000, 10000};
    public static int POKER_AI_MAX_RAISE_RANDOM_ADDITION;
    public static int POKER_AI_MIN_RAISE_VALUE;
    public static int POKER_MONTE_CARLO_SAMPLES;
    public static float POKER_BLIND_DEFENSE_THRESHOLD;
    public static float POKER_BLIND_FOLD_RESISTANCE;
    public static boolean POKER_AI_DEBUG_LOGGING = true;

    // Gacha
    public static int GACHA_COST;
    public static int GACHA_POOL_SIZE;
    public static int GACHA_POOL_CAPITALS;
    public static int GACHA_POOL_CRUISERS;
    public static int GACHA_POOL_DESTROYERS;
    public static int GACHA_POOL_FRIGATES;
    public static int PITY_HARD_5;
    public static int PITY_SOFT_START_5;
    public static int PITY_HARD_4;
    public static int GACHA_ROTATION_DAYS;
    public static float SHIP_TRADE_RATE;
    public static float SHIP_SELL_MULTIPLIER;
    public static float PROB_5_STAR;
    public static float PROB_4_STAR;

    // Arena - Core
    public static int ARENA_SHIP_COUNT;
    public static float ARENA_AGILITY_CAP;
    public static float ARENA_BASE_ODDS;
    public static float ARENA_MIN_ODDS;
    public static float ARENA_HOUSE_EDGE;
    public static int ARENA_ENTRY_FEE;

    // Arena - Bonuses
    public static float ARENA_SURVIVAL_BONUS_PER_TURN;
    public static float ARENA_KILL_BONUS_PER_KILL;
    public static float ARENA_CONSOLATION_BASE;
    public static float ARENA_KILL_BONUS_FLAT;
    public static float ARENA_KILL_BONUS_DIMINISH_PER_ROUND;
    public static float[] ARENA_CONSOLATION_POSITION_FACTORS = {0.50f, 0.25f, 0.10f, 0.05f};
    public static float ARENA_ACTION_MULTIPLIER;
    public static float ARENA_DIMINISHING_RETURNS_PER_ROUND;
    public static float ARENA_DIMINISHING_RETURNS_MIN;

    // Arena - Simulation
    public static int ARENA_SIMULATION_COUNT;
    public static float ARENA_MID_ROUND_BASE_PENALTY;
    public static float ARENA_MID_ROUND_PROGRESSIVE_PENALTY;
    public static int ARENA_MAX_BET_PER_CHAMPION;
    public static float ARENA_HP_ODDS_FACTOR;
    public static float ARENA_MAX_HP_ODDS_MULT;
    public static float ARENA_MIN_HP_ODDS_MULT;

    // Arena - Prefix/Affix Multipliers
    public static float ARENA_PREFIX_MULT_STRONG;
    public static float ARENA_PREFIX_MULT_WEAK;
    public static float ARENA_PREFIX_AGILITY_BONUS;
    public static float ARENA_PREFIX_BRAVERY_BONUS;
    public static float ARENA_AFFIX_MULT_STRONG;
    public static float ARENA_AFFIX_MULT_WEAK;
    public static float ARENA_AFFIX_AGILITY_BONUS;
    public static float ARENA_AFFIX_BRAVERY_BONUS;

    // Arena - Chaos Events
    public static float ARENA_CHAOS_EVENT_CHANCE;
    public static float ARENA_SINGLE_SHIP_DAMAGE_PERCENT;
    public static float ARENA_MULTI_SHIP_DAMAGE_PERCENT;

    // Arena - Battle Log Animation
    public static float ARENA_LOG_LINE_DELAY;
    public static float ARENA_SPRITE_NUDGE_AMOUNT;
    public static float ARENA_SPRITE_NUDGE_DURATION;
    public static float ARENA_HP_ANIM_DURATION;
    public static int ARENA_FLASH_COUNT;

    // Credit Ceiling
    public static float MAX_DEBT_MULTIPLIER;
    public static float OVERDRAFT_CEILING_LEVEL_MULTIPLIER;
    public static float DEBT_COLLECTOR_THRESHOLD_PERCENT;

    // Market & Exchange
    public static int MARKET_SIZE_MIN_FOR_PLAYER_CASINO;
    public static int MARKET_SIZE_MIN_FOR_GENERAL_CASINO;
    public static float STARGEM_EXCHANGE_RATE;

    // Dynamic Lists
    public static final List<GemPackage> GEM_PACKAGES = new ArrayList<>();

    public record GemPackage(int gems, int cost)
    {
    }

    public static class ArenaStat {
        public int hp;
        public int power;
        public float agility;
        public ArenaStat(int hp, int power, float agility) { this.hp = hp; this.power = power; this.agility = agility; }
    }

    public static final Map<HullSize, ArenaStat> ARENA_BASE_STATS = new HashMap<>();

    // Gacha Blacklist
    public static final Set<String> GACHA_SHIP_BLACKLIST_CSV = new HashSet<>();

    public static void loadSettings() {
        try {
            JSONObject settings = Global.getSettings().loadJSON(CONFIG_PATH, MOD_ID);

            // VIP settings
            VIP_DAILY_REWARD = settings.optInt("vipDailyReward", 100);
            VIP_PASS_DAYS = settings.optInt("vipPassDays", 30);
            VIP_DAILY_INTEREST_RATE = (float) settings.optDouble("vipDailyInterestRate", 0.005);
            NORMAL_DAILY_INTEREST_RATE = (float) settings.optDouble("normalDailyInterestRate", 0.01);
            BASE_DEBT_CEILING = settings.optInt("baseDebtCeiling", 5000);
            CEILING_INCREASE_PER_VIP = settings.optInt("ceilingIncreasePerVIP", 10000);
            VIP_PASS_COST = settings.optInt("vipPassCost", 100000);

            // Poker settings
            POKER_SMALL_BLIND = settings.optInt("pokerSmallBlind", 50);
            POKER_BIG_BLIND = settings.optInt("pokerBigBlind", 100);
            POKER_DEFAULT_OPPONENT_STACK = settings.optInt("pokerDefaultOpponentStack", 10000);
            if (settings.has("pokerStackSizes")) {
                JSONArray stackSizes = settings.getJSONArray("pokerStackSizes");
                POKER_STACK_SIZES = new int[stackSizes.length()];
                for (int i = 0; i < stackSizes.length(); i++) {
                    POKER_STACK_SIZES[i] = stackSizes.optInt(i);
                }
            }
            POKER_AI_MAX_RAISE_RANDOM_ADDITION = settings.optInt("pokerAIMaxRaiseRandomAddition", 200);
            POKER_AI_MIN_RAISE_VALUE = settings.optInt("pokerAIMinRaiseValue", 200);
            POKER_MONTE_CARLO_SAMPLES = settings.optInt("pokerMonteCarloSamples", 2000);
            POKER_BLIND_DEFENSE_THRESHOLD = (float) settings.optDouble("pokerBlindDefenseThreshold", 0.05);
            POKER_BLIND_FOLD_RESISTANCE = (float) settings.optDouble("pokerBlindFoldResistance", 0.12);

            // Gacha settings
            GACHA_COST = settings.optInt("gachaCost", 160);
            GACHA_POOL_SIZE = settings.optInt("gachaPoolSize", 40);
            GACHA_POOL_CAPITALS = settings.optInt("gachaPoolCapitals", 3);
            GACHA_POOL_CRUISERS = settings.optInt("gachaPoolCruisers", 9);
            GACHA_POOL_DESTROYERS = settings.optInt("gachaPoolDestroyers", 12);
            GACHA_POOL_FRIGATES = settings.optInt("gachaPoolFrigates", 16);
            PITY_HARD_5 = settings.optInt("pityHard5", 90);
            PITY_SOFT_START_5 = settings.optInt("pitySoftStart5", 73);
            PITY_HARD_4 = settings.optInt("pityHard4", 10);
            GACHA_ROTATION_DAYS = settings.optInt("gachaRotationDays", 14);
            SHIP_TRADE_RATE = (float) settings.optDouble("shipTradeRate", 1000);
            SHIP_SELL_MULTIPLIER = (float) settings.optDouble("shipSellMultiplier", 0.9);
            PROB_5_STAR = (float) settings.optDouble("prob5Star", 0.006);
            PROB_4_STAR = (float) settings.optDouble("prob4Star", 0.051);

            // Arena settings
            ARENA_SHIP_COUNT = settings.optInt("arenaShipCount", 5);
            ARENA_AGILITY_CAP = (float) settings.optDouble("arenaAgilityCap", 0.75);
            ARENA_BASE_ODDS = (float) settings.optDouble("arenaBaseOdds", 5.0);
            ARENA_MIN_ODDS = (float) settings.optDouble("arenaMinOdds", 1.01);
            ARENA_HOUSE_EDGE = (float) settings.optDouble("arenaHouseEdge", 0.1);
            ARENA_ENTRY_FEE = settings.optInt("arenaEntryFee", 100);
            ARENA_SURVIVAL_BONUS_PER_TURN = (float) settings.optDouble("arenaSurvivalBonusPerTurn", 0.05);
            ARENA_KILL_BONUS_PER_KILL = (float) settings.optDouble("arenaKillBonusPerKill", 0.1);
            ARENA_CONSOLATION_BASE = (float) settings.optDouble("arenaConsolationBase", 0.10);
            ARENA_KILL_BONUS_FLAT = (float) settings.optDouble("arenaKillBonusFlat", 0.10);
            ARENA_KILL_BONUS_DIMINISH_PER_ROUND = (float) settings.optDouble("arenaKillBonusDiminishPerRound", 0.30);

            if (settings.has("arenaConsolationPositionFactors")) {
                JSONArray factors = settings.getJSONArray("arenaConsolationPositionFactors");
                ARENA_CONSOLATION_POSITION_FACTORS = new float[factors.length()];
                for (int i = 0; i < factors.length(); i++) {
                    ARENA_CONSOLATION_POSITION_FACTORS[i] = (float) factors.getDouble(i);
                }
            }
            ARENA_ACTION_MULTIPLIER = (float) settings.optDouble("arenaActionMultiplier", 1.5);
            ARENA_DIMINISHING_RETURNS_PER_ROUND = (float) settings.optDouble("arenaDiminishingReturnsPerRound", 0.2);
            ARENA_DIMINISHING_RETURNS_MIN = (float) settings.optDouble("arenaDiminishingReturnsMin", 0.25);

            // Arena simulation settings
            ARENA_SIMULATION_COUNT = settings.optInt("arenaSimulationCount", 500);
            ARENA_MID_ROUND_BASE_PENALTY = (float) settings.optDouble("arenaMidRoundBasePenalty", 0.5);
            ARENA_MID_ROUND_PROGRESSIVE_PENALTY = (float) settings.optDouble("arenaMidRoundProgressivePenalty", 0.15);
            ARENA_MAX_BET_PER_CHAMPION = settings.optInt("arenaMaxBetPerChampion", 10000);
            ARENA_HP_ODDS_FACTOR = (float) settings.optDouble("arenaHpOddsFactor", 2.0);
            ARENA_MAX_HP_ODDS_MULT = (float) settings.optDouble("arenaMaxHpOddsMult", 3.0);
            ARENA_MIN_HP_ODDS_MULT = (float) settings.optDouble("arenaMinHpOddsMult", 0.5);

            // Arena prefix/affix multipliers
            ARENA_PREFIX_MULT_STRONG = (float) settings.optDouble("arenaPrefixMultStrong", 1.3);
            ARENA_PREFIX_MULT_WEAK = (float) settings.optDouble("arenaPrefixMultWeak", 0.7);
            ARENA_PREFIX_AGILITY_BONUS = (float) settings.optDouble("arenaPrefixAgilityBonus", 0.15);
            ARENA_PREFIX_BRAVERY_BONUS = (float) settings.optDouble("arenaPrefixBraveryBonus", 0.1);
            ARENA_AFFIX_MULT_STRONG = (float) settings.optDouble("arenaAffixMultStrong", 1.2);
            ARENA_AFFIX_MULT_WEAK = (float) settings.optDouble("arenaAffixMultWeak", 0.8);
            ARENA_AFFIX_AGILITY_BONUS = (float) settings.optDouble("arenaAffixAgilityBonus", 0.1);
            ARENA_AFFIX_BRAVERY_BONUS = (float) settings.optDouble("arenaAffixBraveryBonus", 0.08);

            // Arena chaos event settings
            ARENA_CHAOS_EVENT_CHANCE = (float) settings.optDouble("arenaChaosEventChance", 0.1);
            ARENA_SINGLE_SHIP_DAMAGE_PERCENT = (float) settings.optDouble("arenaSingleShipDamagePercent", 0.15);
            ARENA_MULTI_SHIP_DAMAGE_PERCENT = (float) settings.optDouble("arenaMultiShipDamagePercent", 0.1);

            // Arena battle log animation settings
            ARENA_LOG_LINE_DELAY = (float) settings.optDouble("arenaLogLineDelay", 0.7f);
            ARENA_SPRITE_NUDGE_AMOUNT = (float) settings.optDouble("arenaSpriteNudgeAmount", 12.0f);
            ARENA_SPRITE_NUDGE_DURATION = (float) settings.optDouble("arenaSpriteNudgeDuration", 0.25f);
            ARENA_HP_ANIM_DURATION = (float) settings.optDouble("arenaHpAnimDuration", 0.4f);
            ARENA_FLASH_COUNT = settings.optInt("arenaFlashCount", 2);

            // Arena base stats from JSON
            if (settings.has("arenaBaseStats")) {
                JSONObject baseStats = settings.getJSONObject("arenaBaseStats");
                for (HullSize size : ARENA_BASE_STATS.keySet()) {
                    String key = size.toString().toLowerCase();
                    if (baseStats.has(key)) {
                        JSONObject stat = baseStats.getJSONObject(key);
                        ArenaStat arenaStat = ARENA_BASE_STATS.get(size);
                        if (stat.has("hp")) arenaStat.hp = stat.getInt("hp");
                        if (stat.has("power")) arenaStat.power = stat.getInt("power");
                        if (stat.has("agility")) arenaStat.agility = (float) stat.getDouble("agility");
                    }
                }
            }

            // Credit ceiling settings
            MAX_DEBT_MULTIPLIER = (float) settings.optDouble("maxDebtMultiplier", 2.0);
            OVERDRAFT_CEILING_LEVEL_MULTIPLIER = (float) settings.optDouble("overdraftCeilingLevelMultiplier", 1000.0);
            DEBT_COLLECTOR_THRESHOLD_PERCENT = (float) settings.optDouble("debtCollectorThresholdPercent", 0.0);

            // Market settings
            MARKET_SIZE_MIN_FOR_PLAYER_CASINO = settings.optInt("marketSizeMinForPlayerCasino", 4);
            MARKET_SIZE_MIN_FOR_GENERAL_CASINO = settings.optInt("marketSizeMinForGeneralCasino", 3);
            STARGEM_EXCHANGE_RATE = (float) settings.optDouble("stargemExchangeRate", 1000.0);

            if (settings.has("gemPackages")) {
                JSONArray packages = settings.getJSONArray("gemPackages");
                GEM_PACKAGES.clear();
                for (int i = 0; i < packages.length(); i++) {
                    JSONObject pkg = packages.getJSONObject(i);
                    GEM_PACKAGES.add(new GemPackage(pkg.getInt("gems"), pkg.getInt("cost")));
                }
            }

            // Sync alias
            NON_VIP_DAILY_INTEREST_RATE = NORMAL_DAILY_INTEREST_RATE;

            log.info("Casino configuration loaded successfully");

        } catch (IOException | JSONException e) {
            log.error("Error loading casino configuration: " + e.getMessage());
            log.info("Using default configuration values");
        }

        loadGachaShipsBlacklist();
    }

    private static void loadGachaShipsBlacklist() {
        try {
            GACHA_SHIP_BLACKLIST_CSV.clear();
            JSONArray csv = Global.getSettings().getMergedSpreadsheetDataForMod("id", GACHA_SHIPS_BLACKLIST_CSV, MOD_ID);
            for (int i = 0; i < csv.length(); i++) {
                JSONObject row = csv.getJSONObject(i);
                String hullId = row.getString("id");
                if (hullId != null && !hullId.isEmpty()) {
                    GACHA_SHIP_BLACKLIST_CSV.add(hullId);
                }
            }
            log.info("Loaded " + GACHA_SHIP_BLACKLIST_CSV.size() + " blacklisted ships from CSV");
        } catch (Exception e) {
            log.warn("Could not load gacha ship blacklist CSV, using empty set.", e);
        }
    }

    static {
        ARENA_BASE_STATS.put(HullSize.FRIGATE, new ArenaStat(80, 25, 0.35f));
        ARENA_BASE_STATS.put(HullSize.DESTROYER, new ArenaStat(120, 35, 0.25f));
        ARENA_BASE_STATS.put(HullSize.CRUISER, new ArenaStat(180, 50, 0.15f));
        ARENA_BASE_STATS.put(HullSize.CAPITAL_SHIP, new ArenaStat(250, 70, 0.10f));
    }
}
