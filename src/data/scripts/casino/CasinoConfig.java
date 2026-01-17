package data.scripts.casino;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

/**
 * CasinoConfig - Centralizes all configurable values for the casino mod
 */
public class CasinoConfig {
    public static final String SETTINGS_FILE = "data/config/casino_settings.json";

    // Economy
    public static float STARGEM_EXCHANGE_RATE = 10.0f; // Credits per stargem
    public static float SHIP_TRADE_RATE = 10.0f;       // Stargems per credit value of ship
    public static int VIP_PASS_COST = 9999;            // Cost in credits
    public static int VIP_PASS_DAYS = 30;              // Duration in days
    public static int VIP_DAILY_REWARD = 100;          // Daily stargems for VIP
    
    public static String MUSIC_POKER = "casino_poker_track";
    public static String MUSIC_ARENA = "casino_arena_track";
    public static String MUSIC_GACHA = "casino_gacha_track";
    public static String MUSIC_AMBIENT = "casino_ambient_track";

    public static class GemPackage {
        public int gems;  // Number of stargems in the package
        public int cost; // Cost of the package in credits
        public GemPackage(int g, int c) { gems=g; cost=c; }
    }
    
    public static List<GemPackage> GEM_PACKAGES = new ArrayList<>();

    // Gacha
    public static int GACHA_COST = 160;              // Cost per pull
    public static float PROB_5_STAR = 0.006f;        // 5-star probability
    public static float PROB_4_STAR = 0.051f;        // 4-star probability
    public static int PITY_HARD_5 = 90;              // Guaranteed 5-star after this many pulls
    public static int PITY_HARD_4 = 10;              // Guaranteed 4-star after this many pulls
    public static int PITY_SOFT_START_5 = 74;        // Soft pity starts at this pull count

    // Poker
    public static int POKER_SMALL_BLIND = 100;
    public static int POKER_BIG_BLIND = 200;
    public static int[] POKER_RAISE_AMOUNTS = {200, 400, 1000, 5000};
    public static float POKER_AI_BLUFF_CHANCE = 0.1f;

    // Arena
    public static int ARENA_ENTRY_FEE = 100;                   // Cost to enter arena
    public static float ARENA_SURVIVAL_REWARD_MULT = 2.0f;     // Survival reward multiplier
    public static int ARENA_SURVIVAL_REWARD_PER_TURN = 10;     // Gems earned per turn survived
    public static int ARENA_KILL_REWARD_PER_KILL = 20;         // Gems earned per kill
    public static int ARENA_SHIP_COUNT = 6;                    // Ships per arena battle
    public static float ARENA_AGILITY_CAP = 0.9f;              // Max agility to prevent unhittable ships

    // Arena Modifiers (loaded from config)
    public static float ARENA_PREFIX_MULT_STRONG = 1.2f;       // Strong prefix multiplier
    public static float ARENA_PREFIX_MULT_WEAK = 0.8f;         // Weak prefix multiplier
    public static float ARENA_PREFIX_AGILITY_BONUS = 0.2f;     // Agility bonus for positive prefixes
    public static float ARENA_PREFIX_BRAVERY_BONUS = 0.2f;     // Bravery bonus for positive prefixes
    public static float ARENA_AFFIX_MULT_STRONG = 1.1f;        // Strong affix multiplier
    public static float ARENA_AFFIX_MULT_WEAK = 0.9f;          // Weak affix multiplier
    public static float ARENA_AFFIX_AGILITY_BONUS = 0.1f;      // Agility bonus for positive affixes
    public static float ARENA_AFFIX_BRAVERY_BONUS = 0.1f;      // Bravery bonus for positive affixes

    public static class ArenaStat {
        public int hp;
        public int power;
        public float agility;
        public ArenaStat(int h, int p, float a) { hp=h; power=p; agility=a; }
    }
    public static Map<ShipAPI.HullSize, ArenaStat> ARENA_BASE_STATS = new HashMap<>();

    public static List<String> ARENA_PREFIX_STRONG_POS = new ArrayList<>(Arrays.asList("impenetrable", "Strong", "Swift", "Brave"));
    public static List<String> ARENA_PREFIX_STRONG_NEG = new ArrayList<>(Arrays.asList("Vulnerable", "Weak", "Slow", "Cowardly"));
    public static List<String> ARENA_AFFIX_POS = new ArrayList<>(Arrays.asList("of Sturdiness", "of Power", "of Speed", "of Courage"));
    public static List<String> ARENA_AFFIX_NEG = new ArrayList<>(Arrays.asList("of Frailty", "of Weakness", "of Sluggishness", "of Timidness"));
    
    // VIP System
    public static float VIP_INTEREST_RATE = 0.05f;                           // 5% monthly interest
    public static int VIP_DEBT_HUNTER_THRESHOLD = -10000;                    // Debt threshold for spawning hunters
    
    // Market Interaction
    public static int MARKET_SIZE_MIN_FOR_PLAYER_CASINO = 6;                 // Minimum size for player markets
    public static int MARKET_SIZE_MIN_FOR_GENERAL_CASINO = 7;                // Minimum size for general markets
    
    // Gacha Ship Filtering
    public static Set<String> GACHA_SHIP_BLACKLIST = new HashSet<>();        // Ship hull IDs to exclude from gacha pool
    
    // Poker AI Configuration
    public static float POKER_AI_TIGHT_THRESHOLD_FOLD = 6f;                  // Tight AI fold threshold
    public static float POKER_AI_TIGHT_THRESHOLD_RAISE = 12f;                // Tight AI raise threshold
    public static float POKER_AI_NORMAL_THRESHOLD_FOLD = 4f;                 // Normal AI fold threshold
    public static float POKER_AI_NORMAL_THRESHOLD_RAISE = 10f;               // Normal AI raise threshold
    public static float POKER_AI_AGGRESSIVE_THRESHOLD_FOLD = 2f;             // Aggressive AI fold threshold
    public static float POKER_AI_AGGRESSIVE_THRESHOLD_RAISE = 8f;            // Aggressive AI raise threshold
    public static int POKER_AI_MIN_RAISE_VALUE = 200;                        // Minimum raise value
    public static int POKER_AI_MAX_RAISE_RANDOM_ADDITION = 200;              // Max random addition to raise
    public static float POKER_AI_STACK_PERCENT_FOLD_TIGHT = 0.3f;            // Tight AI stack percentage for folding
    public static float POKER_AI_STACK_PERCENT_CALL_LIMIT = 0.05f;           // Call limit percentage of stack
    public static float POKER_AI_STRENGTH_HIGH = 80f;                        // High strength threshold
    public static float POKER_AI_STRENGTH_MEDIUM = 40f;                      // Medium strength threshold
    public static float POKER_AI_STACK_PERCENT_CALL_MEDIUM = 0.5f;           // Medium strength call percentage
    public static float POKER_AI_POT_PERCENT_CALL_DRAW = 0.4f;               // Draw call percentage of pot
    public static float POKER_AI_BLUFF_STRENGTH_BOOST = 30f;                 // Bluff strength boost
    public static float POKER_AI_DRAW_CHANCE = 0.2f;                         // Draw play chance
    public static float POKER_AI_CHECK_RAISE_CHANCE = 0.3f;                  // Check-raise chance
    public static float POKER_AI_BLUFF_FOLD_CHANCE = 0.1f;                   // Bluff-fold chance on river
    
    // Arena Configuration
    public static float ARENA_CHAOS_EVENT_CHANCE = 0.15f;                    // Chance of chaos event per step
    public static float ARENA_HULL_BREACH_DAMAGE_PERCENT = 0.2f;             // Hull breach damage percentage

    // Strings
    public static List<String> VIP_ADS = new ArrayList<>();
    public static List<String> ARENA_FLAVOR_TEXTS = new ArrayList<>();
    public static List<String> ARENA_MISS_FLAVOR_TEXTS = new ArrayList<>();
    public static List<String> ARENA_CRIT_FLAVOR_TEXTS = new ArrayList<>();
    public static List<String> ARENA_KILL_FLAVOR_TEXTS = new ArrayList<>();

    static {
        loadSettings();
    }

    public static void loadSettings() {
        try {
            JSONObject json = Global.getSettings().loadJSON(SETTINGS_FILE);
                 
                 // Economy
                 JSONObject eco = json.getJSONObject("economy");
                 STARGEM_EXCHANGE_RATE = (float) eco.optDouble("stargemExchangeRate", 10.0);
                 SHIP_TRADE_RATE = (float) eco.optDouble("shipTradeRate", 10.0);
                 VIP_PASS_COST = eco.optInt("vipPassCost", 9999);
                 VIP_PASS_DAYS = eco.optInt("vipPassDays", 30);
                 VIP_DAILY_REWARD = eco.optInt("vipDailyGemReward", 100);
                 
                 GEM_PACKAGES.clear();
                 JSONArray packs = eco.getJSONArray("gemPackages");
                 for (int i=0; i<packs.length(); i++) {
                     JSONObject p = packs.getJSONObject(i);
                     GEM_PACKAGES.add(new GemPackage(p.getInt("gems"), p.getInt("cost")));
                 }
                 
                 // Gacha
                 JSONObject gacha = json.getJSONObject("gacha");
                 GACHA_COST = gacha.optInt("pullCost", 160);
                 PROB_5_STAR = (float) gacha.optDouble("prob5Star", 0.006);
                 PROB_4_STAR = (float) gacha.optDouble("prob4Star", 0.051);
                 PITY_HARD_5 = gacha.optInt("pityHard5", 90);
                 PITY_HARD_4 = gacha.optInt("pityHard4", 10);
                 PITY_SOFT_START_5 = gacha.optInt("pitySoftStart5", 74);
                                 
                 // Load gacha ship blacklist
                 GACHA_SHIP_BLACKLIST.clear();
                 if (gacha.has("shipBlacklist")) {
                     JSONArray blacklist = gacha.getJSONArray("shipBlacklist");
                     for (int i = 0; i < blacklist.length(); i++) {
                         GACHA_SHIP_BLACKLIST.add(blacklist.getString(i));
                     }
                 }
                 
                 // Poker
                 JSONObject poker = json.getJSONObject("poker");
                 POKER_SMALL_BLIND = poker.optInt("smallBlind", 100);
                 POKER_BIG_BLIND = poker.optInt("bigBlind", 200);
                 
                 // Load poker raise amounts if they exist in config
                 if (poker.has("raiseAmounts")) {
                     JSONArray raiseArray = poker.getJSONArray("raiseAmounts");
                     POKER_RAISE_AMOUNTS = new int[raiseArray.length()];
                     for (int i = 0; i < raiseArray.length(); i++) {
                         POKER_RAISE_AMOUNTS[i] = raiseArray.getInt(i);
                     }
                 }
                 POKER_AI_BLUFF_CHANCE = (float) poker.optDouble("aiBluffChance", 0.1);
                 
                 // Strings
                 VIP_ADS.clear();
                 JSONArray ads = json.getJSONArray("vipAds");
                 for (int i=0; i<ads.length(); i++) {
                     VIP_ADS.add(ads.getString(i));
                 }
                 
                 ARENA_FLAVOR_TEXTS.clear();
                 if (json.has("arenaFlavorTexts")) {
                     JSONArray flavor = json.getJSONArray("arenaFlavorTexts");
                     for (int i=0; i<flavor.length(); i++) {
                         ARENA_FLAVOR_TEXTS.add(flavor.getString(i));
                     }
                 }
                 if (ARENA_FLAVOR_TEXTS.isEmpty()) {
                     ARENA_FLAVOR_TEXTS.addAll(Arrays.asList(
                         "$attacker hits $target for $dmg!",
                         "$attacker pummels $target ($dmg damage)!",
                         "$attacker's tactical strike deals $dmg to $target!"
                     ));
                 }
                 
                 ARENA_MISS_FLAVOR_TEXTS.clear();
                 if (json.has("arenaMissFlavorTexts")) {
                     JSONArray flavor = json.getJSONArray("arenaMissFlavorTexts");
                     for (int i=0; i<flavor.length(); i++) ARENA_MISS_FLAVOR_TEXTS.add(flavor.getString(i));
                 }
                 if (ARENA_MISS_FLAVOR_TEXTS.isEmpty()) ARENA_MISS_FLAVOR_TEXTS.add("$attacker misses $target!");
                 
                 ARENA_CRIT_FLAVOR_TEXTS.clear();
                 if (json.has("arenaCritFlavorTexts")) {
                     JSONArray flavor = json.getJSONArray("arenaCritFlavorTexts");
                     for (int i=0; i<flavor.length(); i++) ARENA_CRIT_FLAVOR_TEXTS.add(flavor.getString(i));
                 }
                 if (ARENA_CRIT_FLAVOR_TEXTS.isEmpty()) ARENA_CRIT_FLAVOR_TEXTS.add("$attacker CRITS $target for $dmg!");
                 
                 ARENA_KILL_FLAVOR_TEXTS.clear();
                 if (json.has("arenaKillFlavorTexts")) {
                     JSONArray flavor = json.getJSONArray("arenaKillFlavorTexts");
                     for (int i=0; i<flavor.length(); i++) ARENA_KILL_FLAVOR_TEXTS.add(flavor.getString(i));
                 }
                 if (ARENA_KILL_FLAVOR_TEXTS.isEmpty()) ARENA_KILL_FLAVOR_TEXTS.add("$target was destroyed by $attacker!");
                 
                 // Arena Modifiers
                 if (json.has("arenaModifiers")) {
                     JSONObject mods = json.getJSONObject("arenaModifiers");
                     ARENA_AGILITY_CAP = (float) mods.optDouble("agilityCap", 0.9);
                     ARENA_PREFIX_MULT_STRONG = (float) mods.optDouble("prefixMultStrong", 1.2);
                     ARENA_PREFIX_MULT_WEAK = (float) mods.optDouble("prefixMultWeak", 0.8);
                     ARENA_PREFIX_AGILITY_BONUS = (float) mods.optDouble("prefixAgilityBonus", 0.2);
                     ARENA_PREFIX_BRAVERY_BONUS = (float) mods.optDouble("prefixBraveryBonus", 0.2);
                     ARENA_AFFIX_MULT_STRONG = (float) mods.optDouble("affixMultStrong", 1.1);
                     ARENA_AFFIX_MULT_WEAK = (float) mods.optDouble("affixMultWeak", 0.9);
                     ARENA_AFFIX_AGILITY_BONUS = (float) mods.optDouble("affixAgilityBonus", 0.1);
                     ARENA_AFFIX_BRAVERY_BONUS = (float) mods.optDouble("affixBraveryBonus", 0.1);
                     
                     if (mods.has("prefixStrongPos")) {
                         ARENA_PREFIX_STRONG_POS.clear();
                         JSONArray arr = mods.getJSONArray("prefixStrongPos");
                         for (int i = 0; i < arr.length(); i++) ARENA_PREFIX_STRONG_POS.add(arr.getString(i));
                     }
                     if (mods.has("prefixStrongNeg")) {
                         ARENA_PREFIX_STRONG_NEG.clear();
                         JSONArray arr = mods.getJSONArray("prefixStrongNeg");
                         for (int i = 0; i < arr.length(); i++) ARENA_PREFIX_STRONG_NEG.add(arr.getString(i));
                     }
                     if (mods.has("affixPos")) {
                         ARENA_AFFIX_POS.clear();
                         JSONArray arr = mods.getJSONArray("affixPos");
                         for (int i = 0; i < arr.length(); i++) ARENA_AFFIX_POS.add(arr.getString(i));
                     }
                     if (mods.has("affixNeg")) {
                         ARENA_AFFIX_NEG.clear();
                         JSONArray arr = mods.getJSONArray("affixNeg");
                         for (int i = 0; i < arr.length(); i++) ARENA_AFFIX_NEG.add(arr.getString(i));
                     }
                 }

                 // Music
                 if (json.has("music")) {
                     JSONObject music = json.getJSONObject("music");
                     MUSIC_POKER = music.optString("poker", MUSIC_POKER);
                     MUSIC_ARENA = music.optString("arena", MUSIC_ARENA);
                     MUSIC_GACHA = music.optString("gacha", MUSIC_GACHA);
                     MUSIC_AMBIENT = music.optString("ambient", MUSIC_AMBIENT);
                 }
                 
                 // VIP System
                 if (json.has("vipSystem")) {
                     JSONObject vip = json.getJSONObject("vipSystem");
                     VIP_INTEREST_RATE = (float) vip.optDouble("interestRate", 0.05f);
                     VIP_DEBT_HUNTER_THRESHOLD = vip.optInt("debtHunterThreshold", -5000);
                 }
                 
                 // Market Interaction
                 if (json.has("marketInteraction")) {
                     JSONObject market = json.getJSONObject("marketInteraction");
                     MARKET_SIZE_MIN_FOR_PLAYER_CASINO = market.optInt("minSizeForPlayerCasino", 6);
                     MARKET_SIZE_MIN_FOR_GENERAL_CASINO = market.optInt("minSizeForGeneralCasino", 7);
                 }
                 
                 // Poker AI Configuration
                 if (json.has("pokerAI")) {
                     JSONObject ai = json.getJSONObject("pokerAI");
                     POKER_AI_TIGHT_THRESHOLD_FOLD = (float) ai.optDouble("tightThresholdFold", 6.0);
                     POKER_AI_TIGHT_THRESHOLD_RAISE = (float) ai.optDouble("tightThresholdRaise", 12.0);
                     POKER_AI_NORMAL_THRESHOLD_FOLD = (float) ai.optDouble("normalThresholdFold", 4.0);
                     POKER_AI_NORMAL_THRESHOLD_RAISE = (float) ai.optDouble("normalThresholdRaise", 10.0);
                     POKER_AI_AGGRESSIVE_THRESHOLD_FOLD = (float) ai.optDouble("aggressiveThresholdFold", 2.0);
                     POKER_AI_AGGRESSIVE_THRESHOLD_RAISE = (float) ai.optDouble("aggressiveThresholdRaise", 8.0);
                     POKER_AI_MIN_RAISE_VALUE = ai.optInt("minRaiseValue", 200);
                     POKER_AI_MAX_RAISE_RANDOM_ADDITION = ai.optInt("maxRaiseRandomAddition", 200);
                     POKER_AI_STACK_PERCENT_FOLD_TIGHT = (float) ai.optDouble("stackPercentFoldTight", 0.3);
                     POKER_AI_STACK_PERCENT_CALL_LIMIT = (float) ai.optDouble("stackPercentCallLimit", 0.05);
                     POKER_AI_STRENGTH_HIGH = (float) ai.optDouble("strengthHigh", 80.0);
                     POKER_AI_STRENGTH_MEDIUM = (float) ai.optDouble("strengthMedium", 40.0);
                     POKER_AI_STACK_PERCENT_CALL_MEDIUM = (float) ai.optDouble("stackPercentCallMedium", 0.5);
                     POKER_AI_POT_PERCENT_CALL_DRAW = (float) ai.optDouble("potPercentCallDraw", 0.4);
                     POKER_AI_BLUFF_STRENGTH_BOOST = (float) ai.optDouble("bluffStrengthBoost", 30.0);
                     POKER_AI_DRAW_CHANCE = (float) ai.optDouble("drawChance", 0.2);
                     POKER_AI_CHECK_RAISE_CHANCE = (float) ai.optDouble("checkRaiseChance", 0.3);
                     POKER_AI_BLUFF_FOLD_CHANCE = (float) ai.optDouble("bluffFoldChance", 0.1);
                 }
                 
                 // Arena Configuration
                 if (json.has("arena")) {
                     JSONObject arena = json.getJSONObject("arena");
                     ARENA_CHAOS_EVENT_CHANCE = (float) arena.optDouble("chaosEventChance", 0.15);
                     ARENA_HULL_BREACH_DAMAGE_PERCENT = (float) arena.optDouble("hullBreachDamagePercent", 0.2);
                     ARENA_ENTRY_FEE = arena.optInt("entryFee", 100);
                     ARENA_SURVIVAL_REWARD_MULT = (float) arena.optDouble("survivalRewardMult", 2.0);
                     ARENA_SURVIVAL_REWARD_PER_TURN = arena.optInt("survivalRewardPerTurn", 10);
                     ARENA_KILL_REWARD_PER_KILL = arena.optInt("killRewardPerKill", 20);
                     
                     // Load Arena Base Stats if available
                     if (arena.has("baseStats")) {
                         JSONObject baseStats = arena.getJSONObject("baseStats");
                         ARENA_BASE_STATS.clear();
                         
                         if (baseStats.has("frigate")) {
                             JSONObject frigate = baseStats.getJSONObject("frigate");
                             ARENA_BASE_STATS.put(ShipAPI.HullSize.FRIGATE, 
                                 new ArenaStat(frigate.getInt("hp"), frigate.getInt("power"), (float) frigate.getDouble("agility")));
                         } else {
                             ARENA_BASE_STATS.put(ShipAPI.HullSize.FRIGATE, new ArenaStat(56, 25, 0.60f));
                         }
                         
                         if (baseStats.has("destroyer")) {
                             JSONObject destroyer = baseStats.getJSONObject("destroyer");
                             ARENA_BASE_STATS.put(ShipAPI.HullSize.DESTROYER, 
                                 new ArenaStat(destroyer.getInt("hp"), destroyer.getInt("power"), (float) destroyer.getDouble("agility")));
                         } else {
                             ARENA_BASE_STATS.put(ShipAPI.HullSize.DESTROYER, new ArenaStat(84, 29, 0.40f));
                         }
                         
                         if (baseStats.has("cruiser")) {
                             JSONObject cruiser = baseStats.getJSONObject("cruiser");
                             ARENA_BASE_STATS.put(ShipAPI.HullSize.CRUISER, 
                                 new ArenaStat(cruiser.getInt("hp"), cruiser.getInt("power"), (float) cruiser.getDouble("agility")));
                         } else {
                             ARENA_BASE_STATS.put(ShipAPI.HullSize.CRUISER, new ArenaStat(112, 33, 0.20f));
                         }
                         
                         if (baseStats.has("capital")) {
                             JSONObject capital = baseStats.getJSONObject("capital");
                             ARENA_BASE_STATS.put(ShipAPI.HullSize.CAPITAL_SHIP, 
                                 new ArenaStat(capital.getInt("hp"), capital.getInt("power"), (float) capital.getDouble("agility")));
                         } else {
                             ARENA_BASE_STATS.put(ShipAPI.HullSize.CAPITAL_SHIP, new ArenaStat(140, 40, 0.00f));
                         }
                     } else {
                         // Default values if baseStats section is missing
                         ARENA_BASE_STATS.put(ShipAPI.HullSize.FRIGATE, new ArenaStat(56, 25, 0.60f));
                         ARENA_BASE_STATS.put(ShipAPI.HullSize.DESTROYER, new ArenaStat(84, 29, 0.40f));
                         ARENA_BASE_STATS.put(ShipAPI.HullSize.CRUISER, new ArenaStat(112, 33, 0.20f));
                         ARENA_BASE_STATS.put(ShipAPI.HullSize.CAPITAL_SHIP, new ArenaStat(140, 40, 0.00f));
                     }
                 } else {
                     // Default values if arena section is missing
                     ARENA_BASE_STATS.put(ShipAPI.HullSize.FRIGATE, new ArenaStat(56, 25, 0.60f));
                     ARENA_BASE_STATS.put(ShipAPI.HullSize.DESTROYER, new ArenaStat(84, 29, 0.40f));
                     ARENA_BASE_STATS.put(ShipAPI.HullSize.CRUISER, new ArenaStat(112, 33, 0.20f));
                     ARENA_BASE_STATS.put(ShipAPI.HullSize.CAPITAL_SHIP, new ArenaStat(140, 40, 0.00f));
                 }

        } catch (Exception e) {
            Global.getLogger(CasinoConfig.class).warn("Could not load casino_settings.json, using defaults.", e);
            // Set default values in case of exception
            GEM_PACKAGES.clear();
            // Add default gem packages if loading fails
            GEM_PACKAGES.add(new GemPackage(1980, 29999));
            GEM_PACKAGES.add(new GemPackage(3280, 49999));
            GEM_PACKAGES.add(new GemPackage(6480, 99999));
            
            ARENA_SURVIVAL_REWARD_PER_TURN = 5;
            ARENA_KILL_REWARD_PER_KILL = 10;
            ARENA_BASE_STATS.put(ShipAPI.HullSize.FRIGATE, new ArenaStat(56, 25, 0.60f));
            ARENA_BASE_STATS.put(ShipAPI.HullSize.DESTROYER, new ArenaStat(84, 29, 0.40f));
            ARENA_BASE_STATS.put(ShipAPI.HullSize.CRUISER, new ArenaStat(112, 33, 0.20f));
            ARENA_BASE_STATS.put(ShipAPI.HullSize.CAPITAL_SHIP, new ArenaStat(140, 40, 0.00f));
        }
    }
}