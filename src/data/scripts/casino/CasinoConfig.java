package data.scripts.casino;

import com.fs.starfarer.api.Global;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;

/**
 * Configuration for Interastral Peace Casino mod. All settings loaded from data/config/casino_settings.json.
 * Never modify values at runtime - use CasinoConfig.XXX to reference.
 */
public class CasinoConfig {

    // VIP System
    public static int VIP_DAILY_REWARD = 100;
    public static int VIP_PASS_DAYS = 30;
    public static float VIP_DAILY_INTEREST_RATE = 0.02f;
    public static float NORMAL_DAILY_INTEREST_RATE = 0.05f;
    public static float NON_VIP_DAILY_INTEREST_RATE = 0.05f;
    public static int BASE_DEBT_CEILING = 5000;
    public static int CEILING_INCREASE_PER_VIP = 10000;
    public static int VIP_PASS_COST = 100000;

    // Poker
    public static int POKER_SMALL_BLIND = 50;
    public static int POKER_BIG_BLIND = 100;
    public static int POKER_DEFAULT_OPPONENT_STACK = 10000;
    public static int[] POKER_STACK_SIZES = {1000, 2500, 5000, 10000};
    public static int POKER_AI_MAX_RAISE_RANDOM_ADDITION = 200;
    public static int POKER_AI_MIN_RAISE_VALUE = 200;
    public static int POKER_MONTE_CARLO_SAMPLES = 2000;

    // Gacha
    public static int GACHA_COST = 160;
    public static int GACHA_POOL_SIZE = 40;
    public static int GACHA_POOL_CAPITALS = 3;
    public static int GACHA_POOL_CRUISERS = 9;
    public static int GACHA_POOL_DESTROYERS = 12;
    public static int GACHA_POOL_FRIGATES = 16;
    public static int PITY_HARD_5 = 90;
    public static int PITY_SOFT_START_5 = 73;
    public static int PITY_HARD_4 = 10;
    public static int GACHA_ROTATION_DAYS = 14;
    public static float SHIP_TRADE_RATE = 1000f;
    public static float SHIP_SELL_MULTIPLIER = 0.9f;
    public static float PROB_5_STAR = 0.006f;
    public static float PROB_4_STAR = 0.051f;

    // Arena - Core
    public static int ARENA_SHIP_COUNT = 5;
    public static float ARENA_AGILITY_CAP = 0.75f;
    public static float ARENA_BASE_ODDS = 5.0f;
    public static float ARENA_MIN_ODDS = 1.01f;
    public static float ARENA_HOUSE_EDGE = 0.10f;
    public static int ARENA_ENTRY_FEE = 100;

    // Arena - Bonuses
    public static float ARENA_SURVIVAL_BONUS_PER_TURN = 0.05f;
    public static float ARENA_KILL_BONUS_PER_KILL = 0.1f;
    public static float ARENA_DEFEATED_CONSOLATION_MULT = 0.5f;
    public static float[] ARENA_CONSOLATION_POSITION_FACTORS = {0.50f, 0.25f, 0.10f, 0.05f};
    public static float ARENA_ACTION_MULTIPLIER = 1.5f;
    public static float ARENA_DIMINISHING_RETURNS_PER_ROUND = 0.20f;
    public static float ARENA_DIMINISHING_RETURNS_MIN = 0.25f;

    // Arena - Simulation
    public static int ARENA_SIMULATION_COUNT = 500;
    public static float ARENA_MID_ROUND_BASE_PENALTY = 0.5f;
    public static float ARENA_MID_ROUND_PROGRESSIVE_PENALTY = 0.15f;
    public static int ARENA_MAX_BET_PER_CHAMPION = 10000;
    public static float ARENA_HP_ODDS_FACTOR = 2.0f;
    public static float ARENA_MAX_HP_ODDS_MULT = 3.0f;
    public static float ARENA_MIN_HP_ODDS_MULT = 0.5f;

    // Arena - Prefix/Affix Multipliers
    public static float ARENA_PREFIX_MULT_STRONG = 1.3f;
    public static float ARENA_PREFIX_MULT_WEAK = 0.7f;
    public static float ARENA_PREFIX_AGILITY_BONUS = 0.15f;
    public static float ARENA_PREFIX_BRAVERY_BONUS = 0.10f;
    public static float ARENA_AFFIX_MULT_STRONG = 1.2f;
    public static float ARENA_AFFIX_MULT_WEAK = 0.8f;
    public static float ARENA_AFFIX_AGILITY_BONUS = 0.10f;
    public static float ARENA_AFFIX_BRAVERY_BONUS = 0.08f;

    // Arena - Chaos Events
    public static float ARENA_CHAOS_EVENT_CHANCE = 0.1f;
    public static float ARENA_SINGLE_SHIP_DAMAGE_PERCENT = 0.15f;
    public static float ARENA_MULTI_SHIP_DAMAGE_PERCENT = 0.10f;
    public static List<String> ARENA_SINGLE_SHIP_DAMAGE_DESCRIPTIONS = new ArrayList<>();
    public static List<String> ARENA_MULTI_SHIP_DAMAGE_DESCRIPTIONS = new ArrayList<>();

    // Credit Ceiling
    public static float MAX_DEBT_MULTIPLIER = 2.0f;
    public static float OVERDRAFT_CEILING_LEVEL_MULTIPLIER = 1000f;
    public static float DEBT_COLLECTOR_THRESHOLD_PERCENT = 0.0f;

    // Market & Exchange
    public static int MARKET_SIZE_MIN_FOR_PLAYER_CASINO = 4;
    public static int MARKET_SIZE_MIN_FOR_GENERAL_CASINO = 3;
    public static float STARGEM_EXCHANGE_RATE = 1000f;

    // Dynamic Lists
    public static List<String> VIP_ADS = new ArrayList<>();
    public static List<GemPackage> GEM_PACKAGES = new ArrayList<>();

    public static class GemPackage {
        public int gems;
        public int cost;
        public GemPackage(int gems, int cost) { this.gems = gems; this.cost = cost; }
    }

    public static class ArenaStat {
        public int hp;
        public int power;
        public float agility;
        public ArenaStat(int hp, int power, float agility) { this.hp = hp; this.power = power; this.agility = agility; }
    }

    public static Map<com.fs.starfarer.api.combat.ShipAPI.HullSize, ArenaStat> ARENA_BASE_STATS = new HashMap<>();

    // Flavor Text Lists
    public static List<String> ARENA_ATTACK_LINES = new ArrayList<>();
    public static List<String> ARENA_FLAVOR_TEXTS = ARENA_ATTACK_LINES;
    public static List<String> ARENA_MISS_LINES = new ArrayList<>();
    public static List<String> ARENA_MISS_FLAVOR_TEXTS = ARENA_MISS_LINES;
    public static List<String> ARENA_CRIT_LINES = new ArrayList<>();
    public static List<String> ARENA_CRIT_FLAVOR_TEXTS = ARENA_CRIT_LINES;
    public static List<String> ARENA_KILL_LINES = new ArrayList<>();
    public static List<String> ARENA_KILL_FLAVOR_TEXTS = ARENA_KILL_LINES;
    public static List<String> ARENA_PREFIX_STRONG_POS = new ArrayList<>();
    public static List<String> ARENA_PREFIX_STRONG_NEG = new ArrayList<>();
    public static List<String> ARENA_AFFIX_POS = new ArrayList<>();
    public static List<String> ARENA_AFFIX_NEG = new ArrayList<>();

    // Gacha Blacklist
    public static Set<String> GACHA_SHIP_BLACKLIST_CSV = new HashSet<>();
    private static final String GACHA_SHIPS_BLACKLIST_CSV = "data/config/gacha_ships_blacklist.csv";

    static {
        ARENA_BASE_STATS.put(com.fs.starfarer.api.combat.ShipAPI.HullSize.FRIGATE, new ArenaStat(80, 25, 0.35f));
        ARENA_BASE_STATS.put(com.fs.starfarer.api.combat.ShipAPI.HullSize.DESTROYER, new ArenaStat(120, 35, 0.25f));
        ARENA_BASE_STATS.put(com.fs.starfarer.api.combat.ShipAPI.HullSize.CRUISER, new ArenaStat(180, 50, 0.15f));
        ARENA_BASE_STATS.put(com.fs.starfarer.api.combat.ShipAPI.HullSize.CAPITAL_SHIP, new ArenaStat(250, 70, 0.10f));

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

        ARENA_PREFIX_STRONG_POS.add("Durable");
        ARENA_PREFIX_STRONG_POS.add("Mightly");
        ARENA_PREFIX_STRONG_POS.add("Swift");
        ARENA_PREFIX_STRONG_POS.add("Fierce");

        ARENA_PREFIX_STRONG_NEG.add("Brittle");
        ARENA_PREFIX_STRONG_NEG.add("Feeble");
        ARENA_PREFIX_STRONG_NEG.add("Sluggish");
        ARENA_PREFIX_STRONG_NEG.add("Timid");

        ARENA_AFFIX_POS.add("of Sturdiness");
        ARENA_AFFIX_POS.add("of Strength");
        ARENA_AFFIX_POS.add("of Speed");
        ARENA_AFFIX_POS.add("of Courage");

        ARENA_AFFIX_NEG.add("of Fragility");
        ARENA_AFFIX_NEG.add("of Weakness");
        ARENA_AFFIX_NEG.add("of Clumsiness");
        ARENA_AFFIX_NEG.add("of Cowardice");

        ARENA_SINGLE_SHIP_DAMAGE_DESCRIPTIONS.add("Maintenance accident damages $ship");
        ARENA_SINGLE_SHIP_DAMAGE_DESCRIPTIONS.add("Asteroid impact hits $ship");
        ARENA_SINGLE_SHIP_DAMAGE_DESCRIPTIONS.add("Micro-meteorite swarm strikes $ship");
        ARENA_SINGLE_SHIP_DAMAGE_DESCRIPTIONS.add("Reactor fluctuation damages $ship");
        ARENA_SINGLE_SHIP_DAMAGE_DESCRIPTIONS.add("Structural fatigue weakens $ship");

        ARENA_MULTI_SHIP_DAMAGE_DESCRIPTIONS.add("Collision between ships causes damage");
        ARENA_MULTI_SHIP_DAMAGE_DESCRIPTIONS.add("Explosive debris strikes the arena");
        ARENA_MULTI_SHIP_DAMAGE_DESCRIPTIONS.add("Chain reaction explosion spreads across ships");
        ARENA_MULTI_SHIP_DAMAGE_DESCRIPTIONS.add("Electromagnetic pulse damages multiple systems");
        ARENA_MULTI_SHIP_DAMAGE_DESCRIPTIONS.add("Arena hazard activation damages combatants");
    }

    public static void loadSettings() {
        try {
            JSONObject settings = Global.getSettings().loadJSON("data/config/casino_settings.json", "interastral_peace_casino");

            if (settings.has("vipDailyReward")) VIP_DAILY_REWARD = settings.getInt("vipDailyReward");
            if (settings.has("vipPassDays")) VIP_PASS_DAYS = settings.getInt("vipPassDays");
            if (settings.has("vipDailyInterestRate")) VIP_DAILY_INTEREST_RATE = (float) settings.getDouble("vipDailyInterestRate");
            if (settings.has("normalDailyInterestRate")) NORMAL_DAILY_INTEREST_RATE = (float) settings.getDouble("normalDailyInterestRate");
            if (settings.has("baseDebtCeiling")) BASE_DEBT_CEILING = settings.getInt("baseDebtCeiling");
            if (settings.has("ceilingIncreasePerVIP")) CEILING_INCREASE_PER_VIP = settings.getInt("ceilingIncreasePerVIP");
            if (settings.has("vipPassCost")) VIP_PASS_COST = settings.getInt("vipPassCost");

            if (settings.has("pokerSmallBlind")) POKER_SMALL_BLIND = settings.getInt("pokerSmallBlind");
            if (settings.has("pokerBigBlind")) POKER_BIG_BLIND = settings.getInt("pokerBigBlind");
            if (settings.has("pokerDefaultOpponentStack")) POKER_DEFAULT_OPPONENT_STACK = settings.getInt("pokerDefaultOpponentStack");
            if (settings.has("pokerStackSizes")) {
                JSONArray stackSizes = settings.getJSONArray("pokerStackSizes");
                POKER_STACK_SIZES = new int[stackSizes.length()];
                for (int i = 0; i < stackSizes.length(); i++) POKER_STACK_SIZES[i] = stackSizes.getInt(i);
            }
            if (settings.has("pokerAIMaxRaiseRandomAddition")) POKER_AI_MAX_RAISE_RANDOM_ADDITION = settings.getInt("pokerAIMaxRaiseRandomAddition");
            if (settings.has("pokerAIMinRaiseValue")) POKER_AI_MIN_RAISE_VALUE = settings.getInt("pokerAIMinRaiseValue");
            if (settings.has("pokerMonteCarloSamples")) POKER_MONTE_CARLO_SAMPLES = settings.getInt("pokerMonteCarloSamples");

            if (settings.has("gachaCost")) GACHA_COST = settings.getInt("gachaCost");
            if (settings.has("gachaPoolSize")) GACHA_POOL_SIZE = settings.getInt("gachaPoolSize");
            if (settings.has("gachaPoolCapitals")) GACHA_POOL_CAPITALS = settings.getInt("gachaPoolCapitals");
            if (settings.has("gachaPoolCruisers")) GACHA_POOL_CRUISERS = settings.getInt("gachaPoolCruisers");
            if (settings.has("gachaPoolDestroyers")) GACHA_POOL_DESTROYERS = settings.getInt("gachaPoolDestroyers");
            if (settings.has("gachaPoolFrigates")) GACHA_POOL_FRIGATES = settings.getInt("gachaPoolFrigates");
            if (settings.has("pityHard5")) PITY_HARD_5 = settings.getInt("pityHard5");
            if (settings.has("pitySoftStart5")) PITY_SOFT_START_5 = settings.getInt("pitySoftStart5");
            if (settings.has("pityHard4")) PITY_HARD_4 = settings.getInt("pityHard4");
            if (settings.has("gachaRotationDays")) GACHA_ROTATION_DAYS = settings.getInt("gachaRotationDays");
            if (settings.has("shipTradeRate")) SHIP_TRADE_RATE = (float) settings.getDouble("shipTradeRate");
            if (settings.has("shipSellMultiplier")) SHIP_SELL_MULTIPLIER = (float) settings.getDouble("shipSellMultiplier");

            if (settings.has("arenaShipCount")) ARENA_SHIP_COUNT = settings.getInt("arenaShipCount");
            if (settings.has("arenaAgilityCap")) ARENA_AGILITY_CAP = (float) settings.getDouble("arenaAgilityCap");
            if (settings.has("arenaBaseOdds")) ARENA_BASE_ODDS = (float) settings.getDouble("arenaBaseOdds");
            if (settings.has("arenaMinOdds")) ARENA_MIN_ODDS = (float) settings.getDouble("arenaMinOdds");
            if (settings.has("arenaHouseEdge")) ARENA_HOUSE_EDGE = (float) settings.getDouble("arenaHouseEdge");
            if (settings.has("arenaEntryFee")) ARENA_ENTRY_FEE = settings.getInt("arenaEntryFee");
            if (settings.has("arenaSurvivalBonusPerTurn")) ARENA_SURVIVAL_BONUS_PER_TURN = (float) settings.getDouble("arenaSurvivalBonusPerTurn");
            if (settings.has("arenaKillBonusPerKill")) ARENA_KILL_BONUS_PER_KILL = (float) settings.getDouble("arenaKillBonusPerKill");
            if (settings.has("arenaDefeatedConsolationMult")) ARENA_DEFEATED_CONSOLATION_MULT = (float) settings.getDouble("arenaDefeatedConsolationMult");
            if (settings.has("arenaConsolationPositionFactors")) {
                try {
                    JSONArray factors = settings.getJSONArray("arenaConsolationPositionFactors");
                    ARENA_CONSOLATION_POSITION_FACTORS = new float[factors.length()];
                    for (int i = 0; i < factors.length(); i++) ARENA_CONSOLATION_POSITION_FACTORS[i] = (float) factors.getDouble(i);
                } catch (JSONException e) {
                    Global.getLogger(CasinoConfig.class).warn("Error loading arenaConsolationPositionFactors: " + e.getMessage());
                }
            }
            if (settings.has("arenaActionMultiplier")) ARENA_ACTION_MULTIPLIER = (float) settings.getDouble("arenaActionMultiplier");
            if (settings.has("arenaDiminishingReturnsPerRound")) ARENA_DIMINISHING_RETURNS_PER_ROUND = (float) settings.getDouble("arenaDiminishingReturnsPerRound");
            if (settings.has("arenaDiminishingReturnsMin")) ARENA_DIMINISHING_RETURNS_MIN = (float) settings.getDouble("arenaDiminishingReturnsMin");
            if (settings.has("arenaSimulationCount")) ARENA_SIMULATION_COUNT = settings.getInt("arenaSimulationCount");
            if (settings.has("arenaMidRoundBasePenalty")) ARENA_MID_ROUND_BASE_PENALTY = (float) settings.getDouble("arenaMidRoundBasePenalty");
            if (settings.has("arenaMidRoundProgressivePenalty")) ARENA_MID_ROUND_PROGRESSIVE_PENALTY = (float) settings.getDouble("arenaMidRoundProgressivePenalty");
            if (settings.has("arenaMaxBetPerChampion")) ARENA_MAX_BET_PER_CHAMPION = settings.getInt("arenaMaxBetPerChampion");
            if (settings.has("arenaHpOddsFactor")) ARENA_HP_ODDS_FACTOR = (float) settings.getDouble("arenaHpOddsFactor");
            if (settings.has("arenaMaxHpOddsMult")) ARENA_MAX_HP_ODDS_MULT = (float) settings.getDouble("arenaMaxHpOddsMult");
            if (settings.has("arenaMinHpOddsMult")) ARENA_MIN_HP_ODDS_MULT = (float) settings.getDouble("arenaMinHpOddsMult");
            if (settings.has("arenaPrefixMultStrong")) ARENA_PREFIX_MULT_STRONG = (float) settings.getDouble("arenaPrefixMultStrong");
            if (settings.has("arenaPrefixMultWeak")) ARENA_PREFIX_MULT_WEAK = (float) settings.getDouble("arenaPrefixMultWeak");
            if (settings.has("arenaPrefixAgilityBonus")) ARENA_PREFIX_AGILITY_BONUS = (float) settings.getDouble("arenaPrefixAgilityBonus");
            if (settings.has("arenaPrefixBraveryBonus")) ARENA_PREFIX_BRAVERY_BONUS = (float) settings.getDouble("arenaPrefixBraveryBonus");
            if (settings.has("arenaAffixMultStrong")) ARENA_AFFIX_MULT_STRONG = (float) settings.getDouble("arenaAffixMultStrong");
            if (settings.has("arenaAffixMultWeak")) ARENA_AFFIX_MULT_WEAK = (float) settings.getDouble("arenaAffixMultWeak");
            if (settings.has("arenaAffixAgilityBonus")) ARENA_AFFIX_AGILITY_BONUS = (float) settings.getDouble("arenaAffixAgilityBonus");
            if (settings.has("arenaAffixBraveryBonus")) ARENA_AFFIX_BRAVERY_BONUS = (float) settings.getDouble("arenaAffixBraveryBonus");
            if (settings.has("arenaChaosEventChance")) ARENA_CHAOS_EVENT_CHANCE = (float) settings.getDouble("arenaChaosEventChance");
            if (settings.has("arenaSingleShipDamagePercent")) ARENA_SINGLE_SHIP_DAMAGE_PERCENT = (float) settings.getDouble("arenaSingleShipDamagePercent");
            if (settings.has("arenaMultiShipDamagePercent")) ARENA_MULTI_SHIP_DAMAGE_PERCENT = (float) settings.getDouble("arenaMultiShipDamagePercent");

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

            if (settings.has("maxDebtMultiplier")) MAX_DEBT_MULTIPLIER = (float) settings.getDouble("maxDebtMultiplier");
            if (settings.has("overdraftCeilingLevelMultiplier")) OVERDRAFT_CEILING_LEVEL_MULTIPLIER = (float) settings.getDouble("overdraftCeilingLevelMultiplier");
            if (settings.has("debtCollectorThresholdPercent")) DEBT_COLLECTOR_THRESHOLD_PERCENT = (float) settings.getDouble("debtCollectorThresholdPercent");
            if (settings.has("marketSizeMinForPlayerCasino")) MARKET_SIZE_MIN_FOR_PLAYER_CASINO = settings.getInt("marketSizeMinForPlayerCasino");
            if (settings.has("marketSizeMinForGeneralCasino")) MARKET_SIZE_MIN_FOR_GENERAL_CASINO = settings.getInt("marketSizeMinForGeneralCasino");
            if (settings.has("stargemExchangeRate")) STARGEM_EXCHANGE_RATE = (float) settings.getDouble("stargemExchangeRate");

            loadStringList(settings, "vipAds", VIP_ADS);

            if (settings.has("gemPackages")) {
                try {
                    JSONArray packages = settings.getJSONArray("gemPackages");
                    GEM_PACKAGES.clear();
                    for (int i = 0; i < packages.length(); i++) {
                        JSONObject pkg = packages.getJSONObject(i);
                        GEM_PACKAGES.add(new GemPackage(pkg.getInt("gems"), pkg.getInt("cost")));
                    }
                } catch (JSONException e) {
                    Global.getLogger(CasinoConfig.class).warn("Error loading gemPackages: " + e.getMessage());
                }
            }

            NON_VIP_DAILY_INTEREST_RATE = NORMAL_DAILY_INTEREST_RATE;

            loadStringList(settings, "arenaAttackLines", ARENA_ATTACK_LINES);
            loadStringList(settings, "arenaMissLines", ARENA_MISS_LINES);
            loadStringList(settings, "arenaCritLines", ARENA_CRIT_LINES);
            loadStringList(settings, "arenaKillLines", ARENA_KILL_LINES);
            loadStringList(settings, "arenaPrefixStrongPos", ARENA_PREFIX_STRONG_POS);
            loadStringList(settings, "arenaPrefixStrongNeg", ARENA_PREFIX_STRONG_NEG);
            loadStringList(settings, "arenaAffixPos", ARENA_AFFIX_POS);
            loadStringList(settings, "arenaAffixNeg", ARENA_AFFIX_NEG);
            loadStringList(settings, "arenaSingleShipDamageDescriptions", ARENA_SINGLE_SHIP_DAMAGE_DESCRIPTIONS);
            loadStringList(settings, "arenaMultiShipDamageDescriptions", ARENA_MULTI_SHIP_DAMAGE_DESCRIPTIONS);

            if (settings.has("prob5Star")) PROB_5_STAR = (float) settings.getDouble("prob5Star");
            if (settings.has("prob4Star")) PROB_4_STAR = (float) settings.getDouble("prob4Star");

            Global.getLogger(CasinoConfig.class).info("Casino configuration loaded successfully");

        } catch (IOException | JSONException e) {
            Global.getLogger(CasinoConfig.class).error("Error loading casino configuration: " + e.getMessage());
            Global.getLogger(CasinoConfig.class).info("Using default configuration values");
        }

        loadGachaShipsBlacklist();
    }

    private static void loadGachaShipsBlacklist() {
        try {
            GACHA_SHIP_BLACKLIST_CSV.clear();
            JSONArray csv = Global.getSettings().getMergedSpreadsheetDataForMod("id", GACHA_SHIPS_BLACKLIST_CSV, "interastral_peace_casino");
            for (int i = 0; i < csv.length(); i++) {
                JSONObject row = csv.getJSONObject(i);
                String hullId = row.getString("id");
                if (hullId != null && !hullId.isEmpty()) GACHA_SHIP_BLACKLIST_CSV.add(hullId);
            }
            Global.getLogger(CasinoConfig.class).info("Loaded " + GACHA_SHIP_BLACKLIST_CSV.size() + " blacklisted ships from CSV");
        } catch (Exception e) {
            Global.getLogger(CasinoConfig.class).warn("Could not load gacha ship blacklist CSV, using empty set.", e);
        }
    }

    private static void loadStringList(JSONObject settings, String key, List<String> list) {
        if (settings.has(key)) {
            try {
                JSONArray array = settings.getJSONArray(key);
                list.clear();
                for (int i = 0; i < array.length(); i++) list.add(array.getString(i));
            } catch (JSONException e) {
                Global.getLogger(CasinoConfig.class).warn("Error loading " + key + ": " + e.getMessage());
            }
        }
    }
}