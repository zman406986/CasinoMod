package data.scripts.casino;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import java.util.*;

/**
 * SpiralAbyssArena
 * 
 * ROLE: This class handles the logic for the "Spaceship Battle Royale" minigame.
 * It simulates combat between multiple ships using a simplified turn-based system.
 * 
 * LEARNERS: This is a great example of how to make a "Game within a Game".
 * It doesn't use the actual Starsector combat engine, but instead simulates
 * results through math and "Flavor Text".
 * 
 * MOD DEVELOPMENT NOTES FOR BEGINNERS:
 * - This creates a battle simulation without using the real combat engine
 * - Uses random events and calculations to determine outcomes
 * - Generates flavorful text descriptions for battle events
 * - Keeps track of multiple units in combat simultaneously
 */
public class SpiralAbyssArena {
    private final Random random = new Random();
    
    // Tracking flavor text history to avoid repeating the same line twice in a row.
    private String lastAttack = "";
    private String lastMiss = "";
    private String lastCrit = "";
    private String lastKill = "";

    /**
     * Chaos Events are random modifiers that spice up the battle.
     * 
     * MOD DEVELOPMENT NOTES FOR BEGINNERS:
     * - Adds randomness and excitement to battles
     * - Different event types affect combat in various ways
     * - Provides narrative variety to keep players engaged
     */
    public enum ChaosEventType {
        SOLAR_FLARE("Solar Flare", "High radioactivity reduces all combatants' Agility!"),
        HULL_BREACH("Hull Breach", "Sudden decompression damages the hull!"),
        POWER_SURGE("Power Surge", "Critical energy overflow boosts weapon systems!");

        public final String name;      // Display name of the event
        public final String description; // Description of what the event does
        ChaosEventType(String n, String d) { name=n; description=d; }
    }

    /**
     * Represents a single instance of a Chaos Event currently happening in the arena.
     * 
     * MOD DEVELOPMENT NOTES FOR BEGINNERS:
     * - Tracks active events and their remaining duration
     * - Events expire after a certain number of rounds
     * - Multiple events can be active simultaneously
     */
    public static class ActiveEvent {
        public ChaosEventType type;    // Type of event
        public int duration;           // Remaining rounds for the event
        public ActiveEvent(ChaosEventType t, int d) { type=t; duration=d; }
    }

    private final List<ActiveEvent> activeEvents = new ArrayList<>(); // Currently active events
    private String lastEventMessage = ""; // Last event message displayed

    /**
     * Helper to pick a random flavor text while ensuring variety.
     * Ensures that the config lists are properly loaded before accessing them.
     */
    private String getFlavor(List<String> source, String last) {
        // Ensure the config has been loaded properly
        if (source == null || source.isEmpty()) {
            // Log a warning to help with debugging
            Global.getLogger(this.getClass()).warn("Flavor text list is empty or null, using fallback.");
            // Return a default string based on the intended list type
            // Since we can't identify which list was intended when it's null, use generic defaults
            return "$attacker hits $target for $dmg!";
        }
        if (source.size() <= 1) return source.get(0);
        
        String next;
        do {
            next = source.get(random.nextInt(source.size()));
        } while (next.equals(last));
        return next;
    }
    
    /**
     * SpiralGladiator
     * Represents a ship participating in the arena.
     */
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
        public float odds;       // Betting odds (e.g., 1:2.5)
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
            this.odds = calculateOdds(prefix, affix); // Calculate odds based on perks
        }
        
        /**
         * Calculates the betting odds for this gladiator based on their perks.
         * More positive perks = lower returns (lower odds), more negative perks = higher returns (higher odds)
         */
        private float calculateOdds(String prefix, String affix) {
            // Base odds is 1:2 (meaning you get 2x your bet back if you win)
            float baseOdds = 2.0f;
            
            // Count positive and negative prefixes
            boolean isPrefixPositive = CasinoConfig.ARENA_PREFIX_STRONG_POS.contains(prefix);
            boolean isPrefixNegative = CasinoConfig.ARENA_PREFIX_STRONG_NEG.contains(prefix);
            
            // Count positive and negative affixes
            boolean isAffixPositive = CasinoConfig.ARENA_AFFIX_POS.contains(affix);
            boolean isAffixNegative = CasinoConfig.ARENA_AFFIX_NEG.contains(affix);
            
            // Adjust odds based on perks
            if (isPrefixPositive || isAffixPositive) {
                // Positive perks make the ship stronger, so lower the odds (less return)
                baseOdds *= 0.8f; // Reduce odds by 20% for each positive perk
            }
            if (isPrefixNegative || isAffixNegative) {
                // Negative perks make the ship weaker, so increase the odds (higher return)
                baseOdds *= 1.3f; // Increase odds by 30% for each negative perk
            }
            
            // Ensure minimum odds of 1.2 (you always get at least 1.2x return)
            return Math.max(1.2f, baseOdds);
        }
        
        /**
         * Gets the formatted odds string for display (e.g., "1:2.5")
         */
        public String getOddsString() {
            return "1:" + String.format("%.1f", odds);
        }
        
        /**
         * Gets the status string for this gladiator showing HP and enraged state
         */
        public String getStatusString() {
            StringBuilder status = new StringBuilder();
            status.append(shortName).append(": ").append(hp).append("/").append(maxHp).append(" HP");
            
            if (isEnraged && targetOfRage != null) {
                status.append(" (angry at ").append(targetOfRage.shortName).append(")");
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
    
    /**
     * Executes one "Step" or "Round" of the battle.
     * @param combatants The list of ships currently in the arena.
     * @return A list of strings describing the events that happened in this round.
     */
    public List<String> simulateStep(List<SpiralGladiator> combatants) {
        List<String> log = new ArrayList<>();
        lastEventMessage = "";
        
        // 1. Check for unpredictable Chaos Events
        if (random.nextFloat() < CasinoConfig.ARENA_CHAOS_EVENT_CHANCE) { // Chance per step from config
            ChaosEventType type = ChaosEventType.values()[random.nextInt(ChaosEventType.values().length)];
            activeEvents.add(new ActiveEvent(type, 1)); 
            lastEventMessage = "⚠️ [EVENT] " + type.name + ": " + type.description;
            log.add(lastEventMessage);
        }

        // Cleanup
        activeEvents.removeIf(e -> e.duration <= 0);

        // Filter for ships that aren't scrap metal yet
        List<SpiralGladiator> alive = new ArrayList<>();
        for (SpiralGladiator g : combatants) if (!g.isDead) alive.add(g);
        
        if (alive.size() < 2) return log; // Match is over
        
        // 3. Process Active Global Modifiers
        float agiMod = 0f;
        float pwrMod = 1f;
        for (ActiveEvent e : activeEvents) {
            if (e.type == ChaosEventType.SOLAR_FLARE) agiMod -= 0.3f; // Harder to dodge
        }

        // Process multiple attacks in this step
        // Allow each ship to potentially attack once per step (or a subset based on alive count)
        int attacksThisStep = Math.max(1, alive.size() / 2); // Allow roughly half the alive ships to attack per step
        
        for (int i = 0; i < attacksThisStep; i++) {
            // Only continue if we still have at least 2 ships alive
            List<SpiralGladiator> currentAlive = new ArrayList<>();
            for (SpiralGladiator g : combatants) if (!g.isDead) currentAlive.add(g);
            if (currentAlive.size() < 2) break; // Stop if less than 2 ships remain
            
            // 2. Determine Attacker and Target
            SpiralGladiator attacker = currentAlive.get(random.nextInt(currentAlive.size()));
            SpiralGladiator target = currentAlive.get(random.nextInt(currentAlive.size()));
            while (target == attacker) target = currentAlive.get(random.nextInt(currentAlive.size()));
            
            // Retaliation Logic: If a ship was hit recently, it might focus its fire on the aggressor.
            if (attacker.retaliateTarget != null && !attacker.retaliateTarget.isDead) {
                target = attacker.retaliateTarget;
                attacker.isEnraged = true;
                attacker.targetOfRage = attacker.retaliateTarget;
                attacker.retaliateTarget = null;
            } else if (attacker.retaliateTarget != null && attacker.retaliateTarget.isDead) {
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

            // 4. Detailed Event Logic for this specific attacker
            for (ActiveEvent e : activeEvents) {
                if (e.type == ChaosEventType.HULL_BREACH) {
                    int dmg = (int)(attacker.maxHp * CasinoConfig.ARENA_HULL_BREACH_DAMAGE_PERCENT);
                    attacker.hp -= dmg;
                    log.add("💥 " + attacker.shortName + " suffered a Hull Breach! (-" + dmg + " HP)");
                    if (attacker.hp <= 0) {
                        attacker.isDead = true;
                        attacker.isEnraged = false;
                        attacker.targetOfRage = null;
                        log.add("💀 " + attacker.shortName + " was lost to space decompression.");
                        break; // Skip attack if attacker died from hull breach
                    }
                }
                if (e.type == ChaosEventType.POWER_SURGE) {
                    pwrMod = 2.0f; // Double damage!
                }
            }

            // Skip attack if attacker died from hull breach event
            if (attacker.isDead) continue;

            // 5. Final Attack Simulation
            float hitChance = 0.7f + (attacker.agility + agiMod) - (target.agility + agiMod);
            if (random.nextFloat() < hitChance) {
                boolean crit = random.nextFloat() < attacker.bravery;
                int dmg = (int)(attacker.power * pwrMod * (crit ? 1.5f : 1.0f));
                target.hp -= dmg;
                
                String flavor = crit ? getFlavor(CasinoConfig.ARENA_CRIT_FLAVOR_TEXTS, lastCrit) : getFlavor(CasinoConfig.ARENA_FLAVOR_TEXTS, lastAttack);
                if (crit) lastCrit = flavor; else lastAttack = flavor;
                
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
                    String kill = getFlavor(CasinoConfig.ARENA_KILL_FLAVOR_TEXTS, lastKill);
                    lastKill = kill;
                    log.add(kill.replace("$attacker", attacker.shortName).replace("$target", target.shortName));
                }
            } else {
                // It's a miss!
                String miss = getFlavor(CasinoConfig.ARENA_MISS_FLAVOR_TEXTS, lastMiss);
                lastMiss = miss;
                log.add(miss.replace("$attacker", attacker.shortName).replace("$target", target.shortName));
            }
        }

        // Duration tick
        for (ActiveEvent e : activeEvents) e.duration--;
        
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
}