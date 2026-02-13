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
        public int finalPosition = -1;  // Final position: 0 = winner, 1 = 2nd place, etc. -1 = not set
        public SpiralGladiator retaliateTarget = null;
        public boolean isEnraged = false;  // Whether the ship is in an enraged state
        public SpiralGladiator targetOfRage = null;  // The ship the current ship is angry at
        
        // Reference to arena and combatants for simulation-based odds calculation
        private transient SpiralAbyssArena arenaRef;
        private transient List<SpiralGladiator> combatantsRef;
        private transient int combatantIndex = -1;
        
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
            // Base odds will be calculated after arena reference is set
            this.baseOdds = CasinoConfig.ARENA_BASE_ODDS;
        }
        

        
        /**
         * Calculates current odds based on simulation-based win probability.
         * Uses Monte Carlo simulation to determine actual win chances, then applies house edge.
         * Mid-round bets receive additional penalties to prevent information exploitation.
         *
         * @param currentRound The current round number (0 = pre-battle)
         * @return The current odds multiplier (already includes house edge and mid-round penalties)
         */
        public float getCurrentOdds(int currentRound) {
            if (isDead) {
                return 0.0f; // Dead ships have no odds
            }

            // If we have arena reference and combatants, use simulation-based calculation
            if (arenaRef != null && combatantsRef != null && combatantIndex >= 0) {
                return arenaRef.calculateCurrentOdds(combatantsRef, combatantIndex, currentRound);
            }

            // Fallback to legacy HP-based calculation if no arena reference available
            // This should only happen in edge cases
            float currentOdds = baseOdds;

            // Apply HP-based adjustment (higher HP = lower odds, lower HP = higher odds)
            float hpRatio = (float) hp / (float) maxHp;
            float hpFactor = 1.0f + (CasinoConfig.ARENA_HP_ODDS_FACTOR - 1.0f) * (1.0f - hpRatio);
            hpFactor = Math.max(CasinoConfig.ARENA_MIN_HP_ODDS_MULT,
                      Math.min(CasinoConfig.ARENA_MAX_HP_ODDS_MULT, hpFactor));

            currentOdds *= hpFactor;

            // Apply mid-round betting penalties
            if (currentRound > 0) {
                float basePenalty = CasinoConfig.ARENA_MID_ROUND_BASE_PENALTY;
                float progressivePenalty = 1.0f - (currentRound * CasinoConfig.ARENA_MID_ROUND_PROGRESSIVE_PENALTY);
                progressivePenalty = Math.max(CasinoConfig.ARENA_DIMINISHING_RETURNS_MIN, progressivePenalty);
                currentOdds *= basePenalty * progressivePenalty;
            }

            // Apply house edge
            currentOdds *= (1.0f - CasinoConfig.ARENA_HOUSE_EDGE);

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
        
        /**
         * Creates a deep copy of this gladiator for simulation purposes.
         */
        public SpiralGladiator copyForSimulation() {
            SpiralGladiator copy = new SpiralGladiator(prefix, hullName, affix, maxHp, power, agility, bravery);
            copy.hp = this.hp;
            copy.isDead = this.isDead;
            copy.kills = this.kills;
            copy.turnsSurvived = this.turnsSurvived;
            copy.baseOdds = this.baseOdds;
            copy.finalPosition = this.finalPosition;
            copy.isEnraged = this.isEnraged;
            copy.targetOfRage = null;
            copy.retaliateTarget = null;
            return copy;
        }
        
        /**
         * Sets the arena and combatants reference for simulation-based odds calculation.
         * This is called by the arena when generating combatants.
         * Also recalculates base odds using Monte Carlo simulation.
         */
        public void setArenaReference(SpiralAbyssArena arena, List<SpiralGladiator> combatants, int index) {
            this.arenaRef = arena;
            this.combatantsRef = combatants;
            this.combatantIndex = index;
            // Recalculate base odds using Monte Carlo simulation now that we have the reference
            this.baseOdds = arena.calculateCurrentOdds(combatants, index, 0);
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
            SpiralGladiator gladiator = new SpiralGladiator(prefix, spec.getHullName(), affix, hp, power, agility, bravery);
            list.add(gladiator);
        }
        
        // Set arena references for all gladiators to enable simulation-based odds
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setArenaReference(this, list, i);
        }
        
        return list;
    }
    
    public List<String> simulateStep(List<SpiralGladiator> combatants, int currentRound) {
        List<String> log = new ArrayList<>();
        
        // Filter for ships that aren't scrap metal yet
        List<SpiralGladiator> alive = new ArrayList<>();
        for (SpiralGladiator g : combatants) if (!g.isDead) alive.add(g);
        
        if (alive.size() < 2) return log; // Match is over

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
                    log.add("[KILL] " + kill.replace("$attacker", attacker.shortName).replace("$target", target.shortName));
                }
            } else {
                // It's a miss!
                String miss = getFlavor(CasinoConfig.ARENA_MISS_FLAVOR_TEXTS, lastMissHistory);
                log.add(miss.replace("$attacker", attacker.shortName).replace("$target", target.shortName));
            }
        }

        // Update alive list after ship attacks
        alive.clear();
        for (SpiralGladiator g : combatants) if (!g.isDead) alive.add(g);

        // Check for unpredictable Chaos Events (happen after ship actions)
        // Chaos events cannot happen on first round (round 0) to give players a fair chance
        if (currentRound > 0 && alive.size() >= 2 && random.nextFloat() < CasinoConfig.ARENA_CHAOS_EVENT_CHANCE) {
            ChaosEventType type = ChaosEventType.values()[random.nextInt(ChaosEventType.values().length)];

            if (type == ChaosEventType.SINGLE_SHIP_DAMAGE) {
                // Single ship damage event - pick one random ship
                SpiralGladiator target = alive.get(random.nextInt(alive.size()));
                int dmg = (int)(target.maxHp * CasinoConfig.ARENA_SINGLE_SHIP_DAMAGE_PERCENT);
                target.hp -= dmg;

                // Get random description from config
                String description = getRandomDescription(CasinoConfig.ARENA_SINGLE_SHIP_DAMAGE_DESCRIPTIONS);
                log.add("[EVENT] " + description.replace("$ship", target.shortName) + " (-" + dmg + " HP)");

                // Check if ship died from event
                if (target.hp <= 0) {
                    target.isDead = true;
                    target.isEnraged = false;
                    target.targetOfRage = null;
                    log.add("[KILL] " + target.shortName + " was destroyed by the incident!");
                }
            } else if (type == ChaosEventType.MULTI_SHIP_DAMAGE) {
                // Multi ship damage event - damage multiple ships
                int shipsToDamage = Math.min(alive.size(), 2 + random.nextInt(Math.min(3, alive.size() - 1))); // 2-4 ships or all if less
                List<SpiralGladiator> shuffled = new ArrayList<>(alive);
                Collections.shuffle(shuffled, random);

                // Get random description from config
                String description = getRandomDescription(CasinoConfig.ARENA_MULTI_SHIP_DAMAGE_DESCRIPTIONS);
                log.add("[EVENT] " + description);

                for (int i = 0; i < shipsToDamage && i < shuffled.size(); i++) {
                    SpiralGladiator target = shuffled.get(i);
                    int dmg = (int)(target.maxHp * CasinoConfig.ARENA_MULTI_SHIP_DAMAGE_PERCENT);
                    target.hp -= dmg;
                    log.add("[HIT] " + target.shortName + " takes " + dmg + " damage!");

                    // Check if ship died from event
                    if (target.hp <= 0) {
                        target.isDead = true;
                        target.isEnraged = false;
                        target.targetOfRage = null;
                        log.add("[KILL] " + target.shortName + " was destroyed!");
                    }
                }
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
    
    /**
     * Calculates win probabilities for all alive combatants using Monte Carlo simulation.
     * This provides accurate odds based on actual combat dynamics rather than simple HP ratios.
     * 
     * @param combatants The list of current combatants
     * @return Map of combatant index to win probability (0.0 - 1.0)
     */
    public Map<Integer, Float> calculateWinProbabilities(List<SpiralGladiator> combatants) {
        Map<Integer, Map<Integer, Float>> positionProbs = calculatePositionProbabilities(combatants);
        Map<Integer, Float> winProbabilities = new HashMap<>();
        
        for (Map.Entry<Integer, Map<Integer, Float>> entry : positionProbs.entrySet()) {
            Float winProb = entry.getValue().get(0);
            winProbabilities.put(entry.getKey(), winProb != null ? winProb : 0.0f);
        }
        
        return winProbabilities;
    }
    
    /**
     * Calculates position probabilities for all alive combatants using Monte Carlo simulation.
     * Position 0 = winner, 1 = 2nd place, 2 = 3rd place, etc.
     * 
     * This is essential for calculating odds that account for consolation rewards.
     * 
     * @param combatants The list of current combatants
     * @return Map of combatant index to Map of position to probability
     */
    public Map<Integer, Map<Integer, Float>> calculatePositionProbabilities(List<SpiralGladiator> combatants) {
        Map<Integer, Map<Integer, Float>> positionProbabilities = new HashMap<>();
        int aliveCount = 0;
        
        for (int i = 0; i < combatants.size(); i++) {
            if (!combatants.get(i).isDead) {
                aliveCount++;
                positionProbabilities.put(i, new HashMap<>());
            }
        }
        
        if (aliveCount == 0) {
            return positionProbabilities;
        }
        if (aliveCount == 1) {
            for (int i = 0; i < combatants.size(); i++) {
                if (!combatants.get(i).isDead) {
                    positionProbabilities.get(i).put(0, 1.0f);
                    return positionProbabilities;
                }
            }
        }
        
        int simulations = CasinoConfig.ARENA_SIMULATION_COUNT;
        Map<Integer, Map<Integer, Integer>> positionCounts = new HashMap<>();
        
        for (int i = 0; i < combatants.size(); i++) {
            if (!combatants.get(i).isDead) {
                positionCounts.put(i, new HashMap<>());
            }
        }
        
        for (int sim = 0; sim < simulations; sim++) {
            List<SpiralGladiator> simCombatants = new ArrayList<>();
            Map<Integer, Integer> originalToSimIndex = new HashMap<>();
            int simIndex = 0;
            
            for (int i = 0; i < combatants.size(); i++) {
                if (!combatants.get(i).isDead) {
                    simCombatants.add(combatants.get(i).copyForSimulation());
                    originalToSimIndex.put(i, simIndex);
                    simIndex++;
                }
            }
            
            Map<Integer, Integer> positionResults = runSimulationToCompletionWithPositions(simCombatants, originalToSimIndex);
            
            for (Map.Entry<Integer, Integer> entry : positionResults.entrySet()) {
                int originalIndex = entry.getKey();
                int position = entry.getValue();
                Map<Integer, Integer> counts = positionCounts.get(originalIndex);
                counts.put(position, counts.getOrDefault(position, 0) + 1);
            }
        }
        
        for (Map.Entry<Integer, Map<Integer, Integer>> shipEntry : positionCounts.entrySet()) {
            int shipIndex = shipEntry.getKey();
            Map<Integer, Float> probs = positionProbabilities.get(shipIndex);
            for (Map.Entry<Integer, Integer> posEntry : shipEntry.getValue().entrySet()) {
                float probability = (float) posEntry.getValue() / simulations;
                probs.put(posEntry.getKey(), probability);
            }
        }
        
        for (int i = 0; i < combatants.size(); i++) {
            if (!combatants.get(i).isDead) {
                Map<Integer, Float> probs = positionProbabilities.get(i);
                if (!probs.containsKey(0)) {
                    probs.put(0, 0.001f);
                }
            }
        }
        
        return positionProbabilities;
    }
    
    /**
     * Runs a combat simulation to completion and returns positions for all ships.
     * Position 0 = winner, 1 = 2nd place (last eliminated), etc.
     * 
     * @param simCombatants List of combatant copies for simulation
     * @param originalToSimIndex Map from original index to simulation index
     * @return Map from original index to final position (0 = winner)
     */
    private Map<Integer, Integer> runSimulationToCompletionWithPositions(List<SpiralGladiator> simCombatants, Map<Integer, Integer> originalToSimIndex) {
        Random simRandom = new Random();
        Map<Integer, Integer> simToOriginalIndex = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : originalToSimIndex.entrySet()) {
            simToOriginalIndex.put(entry.getValue(), entry.getKey());
        }
        
        Map<Integer, Integer> finalPositions = new HashMap<>();
        int currentPosition = simCombatants.size() - 1;
        
        int maxRounds = 1000;
        for (int round = 0; round < maxRounds; round++) {
            List<SpiralGladiator> alive = new ArrayList<>();
            for (SpiralGladiator g : simCombatants) {
                if (!g.isDead) alive.add(g);
            }
            
            if (alive.size() <= 1) {
                break;
            }
            
            int attacksThisRound = Math.max(alive.size(), (int)(alive.size() * CasinoConfig.ARENA_ACTION_MULTIPLIER));
            Set<SpiralGladiator> hasAttackedThisRound = new HashSet<>();
            
            List<SpiralGladiator> diedThisRound = new ArrayList<>();
            
            for (int attack = 0; attack < attacksThisRound; attack++) {
                alive.clear();
                for (SpiralGladiator g : simCombatants) {
                    if (!g.isDead) alive.add(g);
                }
                if (alive.size() < 2) break;
                
                List<SpiralGladiator> availableAttackers = new ArrayList<>();
                for (SpiralGladiator g : alive) {
                    if (!hasAttackedThisRound.contains(g)) {
                        availableAttackers.add(g);
                    }
                }
                
                if (availableAttackers.isEmpty()) {
                    hasAttackedThisRound.clear();
                    availableAttackers.addAll(alive);
                }
                
                SpiralGladiator attacker = availableAttackers.get(simRandom.nextInt(availableAttackers.size()));
                hasAttackedThisRound.add(attacker);
                
                SpiralGladiator target = alive.get(simRandom.nextInt(alive.size()));
                while (target == attacker) {
                    target = alive.get(simRandom.nextInt(alive.size()));
                }
                
                if (attacker.retaliateTarget != null && !attacker.retaliateTarget.isDead) {
                    target = attacker.retaliateTarget;
                }
                
                float hitChance = 0.7f + attacker.agility - target.agility;
                if (simRandom.nextFloat() < hitChance) {
                    boolean crit = simRandom.nextFloat() < attacker.bravery;
                    int dmg = (int)(attacker.power * (crit ? 1.5f : 1.0f));
                    target.hp -= dmg;
                    
                    if (simRandom.nextFloat() < target.bravery) {
                        target.retaliateTarget = attacker;
                    }
                    
                    if (target.hp <= 0) {
                        target.isDead = true;
                        attacker.kills++;
                        diedThisRound.add(target);
                    }
                }
            }
            
            for (SpiralGladiator dead : diedThisRound) {
                int simIndex = simCombatants.indexOf(dead);
                int originalIndex = simToOriginalIndex.get(simIndex);
                finalPositions.put(originalIndex, currentPosition);
                currentPosition--;
            }
        }
        
        for (int i = 0; i < simCombatants.size(); i++) {
            if (!simCombatants.get(i).isDead) {
                int originalIndex = simToOriginalIndex.get(i);
                finalPositions.put(originalIndex, 0);
            }
        }
        
        return finalPositions;
    }
    
    /**
     * Calculates current odds for a specific ship based on simulation-based probabilities.
     * Uses position probabilities to account for consolation rewards, ensuring the
     * configured house edge is maintained regardless of consolation settings.
     * 
     * Formula: odds = (1 - houseEdge) / (P_win + Σ P_position × positionFactor × consolationMult)
     * 
     * This guarantees that betting on all ships equally results in exactly the
     * configured house edge, while still providing meaningful consolation payouts.
     * 
     * @param combatants The list of all combatants
     * @param shipIndex The index of the ship to calculate odds for
     * @param currentRound The current round number (0 = pre-battle)
     * @return The current odds multiplier
     */
    public float calculateCurrentOdds(List<SpiralGladiator> combatants, int shipIndex, int currentRound) {
        SpiralGladiator ship = combatants.get(shipIndex);
        
        if (ship.isDead) {
            return 0.0f;
        }
        
        Map<Integer, Map<Integer, Float>> positionProbabilities = calculatePositionProbabilities(combatants);
        Map<Integer, Float> shipPositionProbs = positionProbabilities.get(shipIndex);
        
        if (shipPositionProbs == null || shipPositionProbs.isEmpty()) {
            return CasinoConfig.ARENA_MIN_ODDS;
        }
        
        Float winProbability = shipPositionProbs.get(0);
        if (winProbability == null || winProbability <= 0.0f) {
            return CasinoConfig.ARENA_MIN_ODDS;
        }
        
        float consolationMult = CasinoConfig.ARENA_DEFEATED_CONSOLATION_MULT;
        float[] positionFactors = CasinoConfig.ARENA_CONSOLATION_POSITION_FACTORS;
        
        float expectedConsolationReturn = 0.0f;
        for (Map.Entry<Integer, Float> entry : shipPositionProbs.entrySet()) {
            int position = entry.getKey();
            float probability = entry.getValue();
            
            if (position > 0) {
                int factorIndex = position - 1;
                float positionFactor = factorIndex < positionFactors.length 
                    ? positionFactors[factorIndex] 
                    : positionFactors[positionFactors.length - 1];
                expectedConsolationReturn += probability * positionFactor * consolationMult;
            }
        }
        
        float totalExpectedReturn = winProbability + expectedConsolationReturn;
        
        float fairOdds = (1.0f - CasinoConfig.ARENA_HOUSE_EDGE) / totalExpectedReturn;
        
        float midRoundMultiplier = 1.0f;
        if (currentRound > 0) {
            float basePenalty = CasinoConfig.ARENA_MID_ROUND_BASE_PENALTY;
            float progressivePenalty = 1.0f - (currentRound * CasinoConfig.ARENA_MID_ROUND_PROGRESSIVE_PENALTY);
            progressivePenalty = Math.max(CasinoConfig.ARENA_DIMINISHING_RETURNS_MIN, progressivePenalty);
            midRoundMultiplier = basePenalty * progressivePenalty;
        }
        
        float finalOdds = fairOdds * midRoundMultiplier;
        
        return Math.max(CasinoConfig.ARENA_MIN_ODDS, finalOdds);
    }
}