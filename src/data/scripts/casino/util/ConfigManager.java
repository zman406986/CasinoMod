package data.scripts.casino.util;

import data.scripts.casino.CasinoConfig;

/**
 * Manager class for accessing and organizing configuration values
 */
public class ConfigManager {
    
    // Arena configuration values
    public static final int ARENA_ENTRY_FEE = CasinoConfig.ARENA_ENTRY_FEE;
    public static final float ARENA_SURVIVAL_REWARD_MULT = CasinoConfig.ARENA_SURVIVAL_REWARD_MULT;
    public static final float ARENA_SURVIVAL_BONUS_PER_TURN = CasinoConfig.ARENA_SURVIVAL_BONUS_PER_TURN;
    public static final float ARENA_KILL_BONUS_PER_KILL = CasinoConfig.ARENA_KILL_BONUS_PER_KILL;
    public static final int ARENA_SHIP_COUNT = CasinoConfig.ARENA_SHIP_COUNT;
    public static final float ARENA_AGILITY_CAP = CasinoConfig.ARENA_AGILITY_CAP;
    public static final float ARENA_CHAOS_EVENT_CHANCE = CasinoConfig.ARENA_CHAOS_EVENT_CHANCE;
    public static final float ARENA_HULL_BREACH_DAMAGE_PERCENT = CasinoConfig.ARENA_HULL_BREACH_DAMAGE_PERCENT;
    
    // Arena modifiers
    public static final float ARENA_PREFIX_MULT_STRONG = CasinoConfig.ARENA_PREFIX_MULT_STRONG;
    public static final float ARENA_PREFIX_MULT_WEAK = CasinoConfig.ARENA_PREFIX_MULT_WEAK;
    public static final float ARENA_PREFIX_AGILITY_BONUS = CasinoConfig.ARENA_PREFIX_AGILITY_BONUS;
    public static final float ARENA_PREFIX_BRAVERY_BONUS = CasinoConfig.ARENA_PREFIX_BRAVERY_BONUS;
    public static final float ARENA_AFFIX_MULT_STRONG = CasinoConfig.ARENA_AFFIX_MULT_STRONG;
    public static final float ARENA_AFFIX_MULT_WEAK = CasinoConfig.ARENA_AFFIX_MULT_WEAK;
    public static final float ARENA_AFFIX_AGILITY_BONUS = CasinoConfig.ARENA_AFFIX_AGILITY_BONUS;
    public static final float ARENA_AFFIX_BRAVERY_BONUS = CasinoConfig.ARENA_AFFIX_BRAVERY_BONUS;
    
    // Poker configuration values
    public static final int POKER_SMALL_BLIND = CasinoConfig.POKER_SMALL_BLIND;
    public static final int POKER_BIG_BLIND = CasinoConfig.POKER_BIG_BLIND;
    public static final int[] POKER_RAISE_AMOUNTS = CasinoConfig.POKER_RAISE_AMOUNTS;
    public static final float POKER_AI_BLUFF_CHANCE = CasinoConfig.POKER_AI_BLUFF_CHANCE;
    
    // Poker AI configuration
    public static final float POKER_AI_TIGHT_THRESHOLD_FOLD = CasinoConfig.POKER_AI_TIGHT_THRESHOLD_FOLD;
    public static final float POKER_AI_TIGHT_THRESHOLD_RAISE = CasinoConfig.POKER_AI_TIGHT_THRESHOLD_RAISE;
    public static final float POKER_AI_NORMAL_THRESHOLD_FOLD = CasinoConfig.POKER_AI_NORMAL_THRESHOLD_FOLD;
    public static final float POKER_AI_NORMAL_THRESHOLD_RAISE = CasinoConfig.POKER_AI_NORMAL_THRESHOLD_RAISE;
    public static final float POKER_AI_AGGRESSIVE_THRESHOLD_FOLD = CasinoConfig.POKER_AI_AGGRESSIVE_THRESHOLD_FOLD;
    public static final float POKER_AI_AGGRESSIVE_THRESHOLD_RAISE = CasinoConfig.POKER_AI_AGGRESSIVE_THRESHOLD_RAISE;
    public static final int POKER_AI_MIN_RAISE_VALUE = CasinoConfig.POKER_AI_MIN_RAISE_VALUE;
    public static final int POKER_AI_MAX_RAISE_RANDOM_ADDITION = CasinoConfig.POKER_AI_MAX_RAISE_RANDOM_ADDITION;
    public static final float POKER_AI_STACK_PERCENT_FOLD_TIGHT = CasinoConfig.POKER_AI_STACK_PERCENT_FOLD_TIGHT;
    public static final float POKER_AI_STACK_PERCENT_CALL_LIMIT = CasinoConfig.POKER_AI_STACK_PERCENT_CALL_LIMIT;
    public static final float POKER_AI_STRENGTH_HIGH = CasinoConfig.POKER_AI_STRENGTH_HIGH;
    public static final float POKER_AI_STRENGTH_MEDIUM = CasinoConfig.POKER_AI_STRENGTH_MEDIUM;
    public static final float POKER_AI_STACK_PERCENT_CALL_MEDIUM = CasinoConfig.POKER_AI_STACK_PERCENT_CALL_MEDIUM;
    public static final float POKER_AI_POT_PERCENT_CALL_DRAW = CasinoConfig.POKER_AI_POT_PERCENT_CALL_DRAW;
    public static final float POKER_AI_BLUFF_STRENGTH_BOOST = CasinoConfig.POKER_AI_BLUFF_STRENGTH_BOOST;
    public static final float POKER_AI_DRAW_CHANCE = CasinoConfig.POKER_AI_DRAW_CHANCE;
    public static final float POKER_AI_CHECK_RAISE_CHANCE = CasinoConfig.POKER_AI_CHECK_RAISE_CHANCE;
    public static final float POKER_AI_BLUFF_FOLD_CHANCE = CasinoConfig.POKER_AI_BLUFF_FOLD_CHANCE;
    
    // Gacha configuration values
    public static final int GACHA_COST = CasinoConfig.GACHA_COST;
    public static final float PROB_5_STAR = CasinoConfig.PROB_5_STAR;
    public static final float PROB_4_STAR = CasinoConfig.PROB_4_STAR;
    public static final int PITY_HARD_5 = CasinoConfig.PITY_HARD_5;
    public static final int PITY_HARD_4 = CasinoConfig.PITY_HARD_4;
    public static final int PITY_SOFT_START_5 = CasinoConfig.PITY_SOFT_START_5;
    
    // Economy configuration values
    public static final float STARGEM_EXCHANGE_RATE = CasinoConfig.STARGEM_EXCHANGE_RATE;
    public static final float SHIP_TRADE_RATE = CasinoConfig.SHIP_TRADE_RATE;
    public static final int VIP_PASS_COST = CasinoConfig.VIP_PASS_COST;
    public static final int VIP_PASS_DAYS = CasinoConfig.VIP_PASS_DAYS;
    public static final int VIP_DAILY_REWARD = CasinoConfig.VIP_DAILY_REWARD;
    
    // Debt system configuration
    public static final int BASE_DEBT_CEILING = CasinoConfig.BASE_DEBT_CEILING;
    public static final int VIP_PASS_CEILING_INCREASE = CasinoConfig.VIP_PASS_CEILING_INCREASE;
    public static final float TOPUP_CEILING_MULTIPLIER = CasinoConfig.TOPUP_CEILING_MULTIPLIER;
    
    // VIP system configuration
    public static final float VIP_INTEREST_RATE = CasinoConfig.VIP_INTEREST_RATE;
    public static final int VIP_DEBT_HUNTER_THRESHOLD = CasinoConfig.VIP_DEBT_HUNTER_THRESHOLD;
    
    // Market interaction configuration
    public static final int MARKET_SIZE_MIN_FOR_PLAYER_CASINO = CasinoConfig.MARKET_SIZE_MIN_FOR_PLAYER_CASINO;
    public static final int MARKET_SIZE_MIN_FOR_GENERAL_CASINO = CasinoConfig.MARKET_SIZE_MIN_FOR_GENERAL_CASINO;
    
    // Arena Lists (need to access directly from CasinoConfig)
    public static java.util.List<String> ARENA_PREFIX_STRONG_POS = CasinoConfig.ARENA_PREFIX_STRONG_POS;
    public static java.util.List<String> ARENA_PREFIX_STRONG_NEG = CasinoConfig.ARENA_PREFIX_STRONG_NEG;
    public static java.util.List<String> ARENA_AFFIX_POS = CasinoConfig.ARENA_AFFIX_POS;
    public static java.util.List<String> ARENA_AFFIX_NEG = CasinoConfig.ARENA_AFFIX_NEG;
    public static java.util.Map<com.fs.starfarer.api.combat.ShipAPI.HullSize, data.scripts.casino.CasinoConfig.ArenaStat> ARENA_BASE_STATS = CasinoConfig.ARENA_BASE_STATS;
    
    // Gacha Lists
    public static java.util.Set<String> GACHA_SHIP_BLACKLIST = CasinoConfig.GACHA_SHIP_BLACKLIST;
    
    // VIP Lists
    public static java.util.List<String> VIP_ADS = CasinoConfig.VIP_ADS;
    
    // Arena Flavor Texts
    public static java.util.List<String> ARENA_FLAVOR_TEXTS = CasinoConfig.ARENA_FLAVOR_TEXTS;
    public static java.util.List<String> ARENA_MISS_FLAVOR_TEXTS = CasinoConfig.ARENA_MISS_FLAVOR_TEXTS;
    public static java.util.List<String> ARENA_CRIT_FLAVOR_TEXTS = CasinoConfig.ARENA_CRIT_FLAVOR_TEXTS;
    public static java.util.List<String> ARENA_KILL_FLAVOR_TEXTS = CasinoConfig.ARENA_KILL_FLAVOR_TEXTS;
    
    // Gacha Packages
    public static java.util.List<data.scripts.casino.CasinoConfig.GemPackage> GEM_PACKAGES = CasinoConfig.GEM_PACKAGES;
    
    /**
     * Reloads the configuration settings
     */
    public static void reloadSettings() {
        CasinoConfig.loadSettings();
    }
}