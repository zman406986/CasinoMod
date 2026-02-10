package data.scripts.casino;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import java.util.*;

public class SpiralAbyssArena {
    private final Random random = new Random();
    
    // Tracking flavor text history to avoid repeating the same line within the last 3 occurrences.
    private final Queue<String> lastAttackHistory = new LinkedList<>();
    private final Queue<String> lastMissHistory = new LinkedList<>();
    private final Queue<String> lastCritHistory = new LinkedList<>();
    private final Queue<String> lastKillHistory = new LinkedList<>();
    private static final int MAX_HISTORY_SIZE = 3;

    public enum ChaosEventType {
        SINGLE_SHIP_DAMAGE,  // Damages a single random ship (maintenance accident, asteroid impact, etc.)
        MULTI_SHIP_DAMAGE    // Damages multiple ships (collision, area explosion, etc.)
    }

    public static class ActiveEvent {
        public ChaosEventType type;    // Type of event
        public String description;     // Event description from config
        public ActiveEvent(ChaosEventType t, String desc) { type=t; description=desc; }
    }

    private String getFlavor(List<String> source, Queue<String> history) {
        // Ensure the config has been loaded properly
        if (source == null || source.isEmpty()) {
            // Log a warning to help with debugging
            Global.getLogger(this.getClass()).warn("Flavor text list is empty or null, using fallback.");
            // Return a default string based on the intended list type
            // Since we can't identify which list was intended when it's null, use generic defaults
            return "$attacker hits $target for $dmg!";
        }
        if (source.size() == 1) return source.get(0);
        
        String next;
        int attempts = 0;
        do {
            next = source.get(random.nextInt(source.size()));
            attempts++;
        } while (history.contains(next) && attempts < 10);
        
        // Add to history and maintain max size
        history.offer(next);
        if (history.size() > MAX_HISTORY_SIZE) {
            history.poll();
        }
        
        return next;
    }
    
    public static class SpiralGladiator {
        public String prefix;    // e.g., "Mighty"
        public String hullName;  // e.g., "Hammerhead"
        public String affix;     // e.g., "of the Void"
        public String fullName;
        public String shortName; // Name without prefix and affix
        public int hp;
        public int maxHp;
        public int power;        // Base damage
        public float agility;    // Dodge/Hit chance
        public float bravery;    // Crit/Retaliate chance
        public boolean isDead = false;
        public int kills = 0;
        public int turnsSurvived = 0;
        public float baseOdds;   // Base betting odds from perks (e.g., 1:5.0)
        public SpiralGladiator retaliateTarget = null;
        public boolean isEnraged = false;  // Whether the ship is in an enraged state
        public SpiralGladiator targetOfRage = null;  // The ship the current ship is angry at
        
        public SpiralGladiator(String prefix, String hullName, String affix, int hp, int power, float agility, float bravery) {
            this.prefix = prefix;
            this.hullName = hullName;
            this.affix = affix;
            this.fullName = prefix + " " + hullName + " " + affix;
            this.shortName = hullName; // Just the hull name without prefix/affix
            this.hp = hp;
            this.maxHp = hp;
            this.power = power;
            this.agility = Math.min(agility, CasinoConfig.ARENA_AGILITY_CAP);
            this.bravery = bravery;
            this.baseOdds = calculateBaseOdds(prefix, affix); // Calculate base odds based on perks
        }
        
        private float calculateBaseOdds(String prefix, String affix) {
            // Base odds from config (default 5.0 = 1:5.0 return)
            float odds = CasinoConfig.ARENA_BASE_ODDS;

            // Count positive and negative prefixes
            boolean isPrefixPositive = CasinoConfig.ARENA_PREFIX_STRONG_POS.contains(prefix);
            boolean isPrefixNegative = CasinoConfig.ARENA_PREFIX_STRONG_NEG.contains(prefix);

            // Count positive and negative affixes
            boolean isAffixPositive = CasinoConfig.ARENA_AFFIX_POS.contains(affix);
            boolean isAffixNegative = CasinoConfig.ARENA_AFFIX_NEG.contains(affix);

            // Adjust odds based on perks using config multipliers
            if (isPrefixPositive || isAffixPositive) {
                // Positive perks make the ship stronger, so lower the odds (less return)
                odds *= CasinoConfig.ARENA_POSITIVE_PERK_MULTIPLIER;
            }
            if (isPrefixNegative || isAffixNegative) {
                // Negative perks make the ship weaker, so increase the odds (higher return)
                odds *= CasinoConfig.ARENA_NEGATIVE_PERK_MULTIPLIER;
            }

            // Ensure minimum odds from config
            return Math.max(CasinoConfig.ARENA_MIN_ODDS, odds);
        }
        
        /**
         * Calculates current odds based on base odds, current HP, and round number.
         * Higher HP = lower odds (worse payout), Lower HP = higher odds (better payout)
         * 
         * @param currentRound The current round number (0 = pre-battle)
         * @return The current odds multiplier
         */
        public float getCurrentOdds(int currentRound) {
            if (isDead) {
                return 0.0f; // Dead ships have no odds
            }
            
            // Start with base odds from perks
            float currentOdds = baseOdds;
            
            // Apply HP-based adjustment (higher HP = lower odds, lower HP = higher odds)
            float hpRatio = (float) hp / (float) maxHp;
            // Invert the ratio so low HP gives higher odds
            float hpFactor = 1.0f + (CasinoConfig.ARENA_HP_ODDS_FACTOR - 1.0f) * (1.0f - hpRatio);
            // Clamp to min/max limits
            hpFactor = Math.max(CasinoConfig.ARENA_MIN_HP_ODDS_MULT, 
                      Math.min(CasinoConfig.ARENA_MAX_HP_ODDS_MULT, hpFactor));
            
            currentOdds *= hpFactor;
            
            // Apply diminishing returns for mid-round bets
            if (currentRound > 0) {
                float diminishingReturns = 1.0f - (currentRound * CasinoConfig.ARENA_DIMINISHING_RETURNS_PER_ROUND);
                diminishingReturns = Math.max(CasinoConfig.ARENA_DIMINISHING_RETURNS_MIN, diminishingReturns);
                currentOdds *= diminishingReturns;
            }
            
            // Ensure minimum odds
            return Math.max(CasinoConfig.ARENA_MIN_ODDS, currentOdds);
        }
        
        /**
         * Gets the current odds string for display purposes.
         * Uses current HP status.
         */
        public String getCurrentOddsString(int currentRound) {
            return "1:" + String.format("%.1f", getCurrentOdds(currentRound));
        }
        
        /**
         * Gets the base odds string (pre-battle odds).
         */
        public String getBaseOddsString() {
            return "1:" + String.format("%.1f", baseOdds);
        }
        
        /**
         * @deprecated Use getBaseOddsString() for pre-battle odds or getCurrentOddsString(round) for current odds
         */
        @Deprecated
        public String getOddsString() {
            return getBaseOddsString();
        }
        
        public String getStatusString() {
            StringBuilder status = new StringBuilder();
            status.append(hullName).append(": ").append(hp).append("/").append(maxHp).append(" HP");
            
            if (isEnraged && targetOfRage != null) {
                status.append(" (angry at ").append(targetOfRage.hullName).append(")");
            }
            
            return status.toString();
        }
    }
    
    /**
     * Generates the roster of ships for a new arena match.
     * Uses the current Gacha rotation to feature prominent ships.
     */
    public List<SpiralGladiator> generateCombatants(CasinoGachaManager gacha) {
        List<SpiralGladiator> list = new ArrayList<>();
        List<String> pool = new ArrayList<>();
        
        // Always include the current featured ships to promote them!
        CasinoGachaManager.GachaData data = gacha.getData();
        if (data.featuredCapital != null) pool.add(data.featuredCapital);
        pool.addAll(data.featuredCruisers);
        
        // Fill the rest with random smaller hulls
        while (pool.size() < CasinoConfig.ARENA_SHIP_COUNT) {
            String randomHull = gacha.getRandomStandardHull(ShipAPI.HullSize.DESTROYER, null);
            if (randomHull != null) pool.add(randomHull);
        }
        
        for (String hullId : pool) {
            if (hullId == null) continue; // Skip null hull IDs
            
            ShipHullSpecAPI spec = Global.getSettings().getHullSpec(hullId);
            if (spec == null) continue; // Skip invalid hull IDs
            
            CasinoConfig.ArenaStat base = CasinoConfig.ARENA_BASE_STATS.get(spec.getHullSize());
            if (base == null) base = CasinoConfig.ARENA_BASE_STATS.get(ShipAPI.HullSize.FRIGATE);
            
            int hp = base.hp;
            int power = base.power;
            float agility = base.agility;
            float bravery = 0.20f;
            
            // Randomly assign a Prefix (Strong/Weak) which modifies stats.
            int prefixIdx = random.nextInt(CasinoConfig.ARENA_PREFIX_STRONG_POS.size());
            boolean posPrefix = random.nextBoolean();
            String prefix = posPrefix ? CasinoConfig.ARENA_PREFIX_STRONG_POS.get(prefixIdx) : CasinoConfig.ARENA_PREFIX_STRONG_NEG.get(prefixIdx);
            
            // Apply prefix effects based on index
            float multP = posPrefix ? CasinoConfig.ARENA_PREFIX_MULT_STRONG : CasinoConfig.ARENA_PREFIX_MULT_WEAK;
            if (prefixIdx == 0) hp = (int)(hp * multP);  // "Giant"/"Tiny" affects HP
            else if (prefixIdx == 1) power = (int)(power * multP);  // "Strong"/"Weak" affects Power
            else if (prefixIdx == 2) agility = posPrefix ? agility + CasinoConfig.ARENA_PREFIX_AGILITY_BONUS : Math.max(0, agility - CasinoConfig.ARENA_PREFIX_AGILITY_BONUS);  // "Swift"/"Clumsy" affects Agility
            else bravery = posPrefix ? bravery + CasinoConfig.ARENA_PREFIX_BRAVERY_BONUS : Math.max(0, bravery - CasinoConfig.ARENA_PREFIX_BRAVERY_BONUS);  // "Fierce"/"Cowardly" affects Bravery
            
            // Randomly assign an Affix for even more variety.
            int affixIdx = random.nextInt(CasinoConfig.ARENA_AFFIX_POS.size());
            boolean posAffix = random.nextBoolean();
            String affix = posAffix ? CasinoConfig.ARENA_AFFIX_POS.get(affixIdx) : CasinoConfig.ARENA_AFFIX_NEG.get(affixIdx);
            
            // Validate that the affix is from the expected list (defensive programming)
            List<String> allValidAffixes = new ArrayList<>();
            allValidAffixes.addAll(CasinoConfig.ARENA_AFFIX_POS);
            allValidAffixes.addAll(CasinoConfig.ARENA_AFFIX_NEG);
            
            // If somehow we got an invalid affix, fall back to a default
            if (!allValidAffixes.contains(affix)) {
                // Log error and use a default
                Global.getLogger(this.getClass()).warn("Invalid affix detected: " + affix + ", falling back to default");
                affix = posAffix ? CasinoConfig.ARENA_AFFIX_POS.get(0) : CasinoConfig.ARENA_AFFIX_NEG.get(0);
            }
            
            // Apply affix effects based on index
            float multA = posAffix ? CasinoConfig.ARENA_AFFIX_MULT_STRONG : CasinoConfig.ARENA_AFFIX_MULT_WEAK;
            if (affixIdx == 0) hp = (int)(hp * multA);  // "Large"/"Small" affects HP
            else if (affixIdx == 1) power = (int)(power * multA);  // "of Might"/"of Frailty" affects Power
            else if (affixIdx == 2) agility = posAffix ? agility + CasinoConfig.ARENA_AFFIX_AGILITY_BONUS : Math.max(0, agility - CasinoConfig.ARENA_AFFIX_AGILITY_BONUS);  // "of Speed"/"of Slowness" affects Agility
            else bravery = posAffix ? bravery + CasinoConfig.ARENA_AFFIX_BRAVERY_BONUS : Math.max(0, bravery - CasinoConfig.ARENA_AFFIX_BRAVERY_BONUS);  // "of Courage"/"of Fear" affects Bravery
            
            agility = Math.min(agility, CasinoConfig.ARENA_AGILITY_CAP);
            list.add(new SpiralGladiator(prefix, spec.getHullName(), affix, hp, power, agility, bravery));
        }
        return list;
    }
    
    public List<String> simulateStep(List<SpiralGladiator> combatants) {
        List<String> log = new ArrayList<>();
        
        // Filter for ships that aren't scrap metal yet
        List<SpiralGladiator> alive = new ArrayList<>();
        for (SpiralGladiator g : combatants) if (!g.isDead) alive.add(g);
        
        if (alive.size() < 2) return log; // Match is over
        
        // 1. Check for unpredictable Chaos Events
        if (random.nextFloat() < CasinoConfig.ARENA_CHAOS_EVENT_CHANCE) { // Chance per step from config
            ChaosEventType type = ChaosEventType.values()[random.nextInt(ChaosEventType.values().length)];
            
            if (type == ChaosEventType.SINGLE_SHIP_DAMAGE) {
                // Single ship damage event - pick one random ship
                SpiralGladiator target = alive.get(random.nextInt(alive.size()));
                int dmg = (int)(target.maxHp * CasinoConfig.ARENA_SINGLE_SHIP_DAMAGE_PERCENT);
                target.hp -= dmg;
                
                // Get random description from config
                String description = getRandomDescription(CasinoConfig.ARENA_SINGLE_SHIP_DAMAGE_DESCRIPTIONS);
                log.add("⚠️ [EVENT] " + description.replace("$ship", target.shortName) + " (-" + dmg + " HP)");
                
                // Check if ship died from event
                if (target.hp <= 0) {
                    target.isDead = true;
                    target.isEnraged = false;
                    target.targetOfRage = null;
                    log.add("💀 " + target.shortName + " was destroyed by the incident!");
                }
            } else if (type == ChaosEventType.MULTI_SHIP_DAMAGE) {
                // Multi ship damage event - damage multiple ships
                int shipsToDamage = Math.min(alive.size(), 2 + random.nextInt(Math.min(3, alive.size() - 1))); // 2-4 ships or all if less
                List<SpiralGladiator> shuffled = new ArrayList<>(alive);
                Collections.shuffle(shuffled, random);
                
                // Get random description from config
                String description = getRandomDescription(CasinoConfig.ARENA_MULTI_SHIP_DAMAGE_DESCRIPTIONS);
                log.add("⚠️ [EVENT] " + description);
                
                for (int i = 0; i < shipsToDamage && i < shuffled.size(); i++) {
                    SpiralGladiator target = shuffled.get(i);
                    int dmg = (int)(target.maxHp * CasinoConfig.ARENA_MULTI_SHIP_DAMAGE_PERCENT);
                    target.hp -= dmg;
                    log.add("💥 " + target.shortName + " takes " + dmg + " damage!");
                    
                    // Check if ship died from event
                    if (target.hp <= 0) {
                        target.isDead = true;
                        target.isEnraged = false;
                        target.targetOfRage = null;
                        log.add("💀 " + target.shortName + " was destroyed!");
                    }
                }
            }
        }

        // Update alive list after events
        alive.clear();
        for (SpiralGladiator g : combatants) if (!g.isDead) alive.add(g);
        
        if (alive.size() < 2) return log; // Match is over after events

        // Process multiple attacks in this step
        // Calculate number of attacks based on action multiplier, but each ship can only attack once per round
        int baseAttacks = alive.size();
        int attacksThisStep = Math.max(baseAttacks, (int)(baseAttacks * CasinoConfig.ARENA_ACTION_MULTIPLIER));
        
        // Track which ships have already attacked this step
        Set<SpiralGladiator> hasAttackedThisStep = new HashSet<>();
        
        for (int i = 0; i < attacksThisStep; i++) {
            // Only continue if we still have at least 2 ships alive
            List<SpiralGladiator> currentAlive = new ArrayList<>();
            for (SpiralGladiator g : combatants) if (!g.isDead) currentAlive.add(g);
            if (currentAlive.size() < 2) break; // Stop if less than 2 ships remain
            
            // Find available attackers (ships that haven't attacked this step yet)
            List<SpiralGladiator> availableAttackers = new ArrayList<>();
            for (SpiralGladiator g : currentAlive) {
                if (!hasAttackedThisStep.contains(g)) {
                    availableAttackers.add(g);
                }
            }
            
            // If all ships have attacked, reset the tracking to allow re-attacks
            if (availableAttackers.isEmpty()) {
                hasAttackedThisStep.clear();
                availableAttackers.addAll(currentAlive);
            }
            
            // 2. Determine Attacker and Target
            SpiralGladiator attacker = availableAttackers.get(random.nextInt(availableAttackers.size()));
            hasAttackedThisStep.add(attacker);
            
            SpiralGladiator target = currentAlive.get(random.nextInt(currentAlive.size()));
            while (target == attacker) target = currentAlive.get(random.nextInt(currentAlive.size()));
            
            // Retaliation Logic: If a ship was hit recently, it might focus its fire on the aggressor.
            if (attacker.retaliateTarget != null && !attacker.retaliateTarget.isDead) {
                target = attacker.retaliateTarget;
                attacker.isEnraged = true;
                attacker.targetOfRage = attacker.retaliateTarget;
                attacker.retaliateTarget = null;
            } else if (attacker.retaliateTarget != null) {
                // Clear retaliation target if it's dead
                attacker.retaliateTarget = null;
                attacker.isEnraged = false;
                attacker.targetOfRage = null;
            }
            
            // Also check if the rage target is dead and clear it
            if (attacker.targetOfRage != null && attacker.targetOfRage.isDead) {
                attacker.isEnraged = false;
                attacker.targetOfRage = null;
            }

            // 3. Final Attack Simulation
            float hitChance = 0.7f + attacker.agility - target.agility;
            if (random.nextFloat() < hitChance) {
                boolean crit = random.nextFloat() < attacker.bravery;
                int dmg = (int)(attacker.power * (crit ? 1.5f : 1.0f));
                target.hp -= dmg;
                
                String flavor = crit ? getFlavor(CasinoConfig.ARENA_CRIT_FLAVOR_TEXTS, lastCritHistory) : getFlavor(CasinoConfig.ARENA_FLAVOR_TEXTS, lastAttackHistory);
                
                // LEARNERS: We use .replace() to inject variables into our flavor text templates.
                log.add(flavor.replace("$attacker", attacker.shortName).replace("$target", target.shortName).replace("$dmg", ""+dmg));
                
                // Mark for retaliation
                if (random.nextFloat() < target.bravery) {
                    target.retaliateTarget = attacker;
                    target.isEnraged = true;
                    target.targetOfRage = attacker;
                }
                
                // Check for death
                if (target.hp <= 0) {
                    target.isDead = true;
                    target.isEnraged = false;
                    target.targetOfRage = null;
                    attacker.kills++;
                    String kill = getFlavor(CasinoConfig.ARENA_KILL_FLAVOR_TEXTS, lastKillHistory);
                    log.add(kill.replace("$attacker", attacker.shortName).replace("$target", target.shortName));
                }
            } else {
                // It's a miss!
                String miss = getFlavor(CasinoConfig.ARENA_MISS_FLAVOR_TEXTS, lastMissHistory);
                log.add(miss.replace("$attacker", attacker.shortName).replace("$target", target.shortName));
            }
        }
        
        // Add status of all remaining ships to the log
        List<SpiralGladiator> remainingShips = new ArrayList<>();
        for (SpiralGladiator g : combatants) {
            if (!g.isDead) {
                remainingShips.add(g);
            }
        }
        
        if (!remainingShips.isEmpty()) {
            log.add("--- SHIP STATUS ---");
            for (SpiralGladiator g : remainingShips) {
                log.add(g.getStatusString());
            }
        }
        
        return log;
    }
    
    /**
     * Gets a random description from the provided list.
     */
    private String getRandomDescription(List<String> descriptions) {
        if (descriptions == null || descriptions.isEmpty()) {
            return "An incident occurs!";
        }
        return descriptions.get(random.nextInt(descriptions.size()));
    }
}