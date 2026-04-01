package data.scripts.casino.arena;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import data.scripts.casino.CasinoConfig;
import data.scripts.casino.gacha.CasinoGachaManager;
import java.util.*;

public class SpiralAbyssArena {
    private final Random random = new Random();
    private final Queue<String> lastAttackHistory = new LinkedList<>();
    private final Queue<String> lastMissHistory = new LinkedList<>();
    private final Queue<String> lastCritHistory = new LinkedList<>();
    private final Queue<String> lastKillHistory = new LinkedList<>();
    private static final int MAX_HISTORY_SIZE = 3;
    
    private Map<Integer, Map<Integer, Float>> cachedPositionProbabilities = null;
    private Map<Integer, Float> cachedExpectedKills = null;
    private int cachedPositionRound = -1;
    private List<Integer> cachedHpValues = null;
    
    public enum ChaosEventType {
        SINGLE_SHIP_DAMAGE,
        MULTI_SHIP_DAMAGE
    }

    private String getFlavor(List<String> source, Queue<String> history) {
        if (source == null || source.isEmpty()) {
            Global.getLogger(this.getClass()).warn("Flavor text list is empty or null, using fallback.");
            return "$attacker hits $target for $dmg!";
        }
        if (source.size() == 1) return source.get(0);
        
        String next;
        int attempts = 0;
        do {
            next = source.get(random.nextInt(source.size()));
            attempts++;
        } while (history.contains(next) && attempts < 10);
        
        history.offer(next);
        if (history.size() > MAX_HISTORY_SIZE) {
            history.poll();
        }
        
        return next;
    }
    
    public static class SpiralGladiator {
        public String prefix;
        public String hullName;
        public String hullId;
        public String affix;
        public String fullName;
        public String shortName;
        public int hp;
        public int maxHp;
        public int power;
        public float agility;
        public float bravery;
        public boolean isDead = false;
        public int kills = 0;
        public int turnsSurvived = 0;
        public int totalAttacks = 0;
        public float baseOdds;
        public int finalPosition = -1;
        public SpiralGladiator retaliateTarget = null;
        public boolean isEnraged = false;
        public SpiralGladiator targetOfRage = null;
        
        private transient SpiralAbyssArena arenaRef;
        private transient List<SpiralGladiator> combatantsRef;
        private transient int combatantIndex = -1;
        
public SpiralGladiator(String hullId, String prefix, String hullName, String affix, int hp, int power, float agility, float bravery) {
            this.hullId = hullId;
            this.prefix = prefix;
            this.hullName = hullName;
            this.affix = affix;
            this.fullName = prefix + " " + hullName + " " + affix;
            this.shortName = hullName;
            this.hp = hp;
            this.maxHp = hp;
            this.power = power;
            this.agility = Math.min(agility, CasinoConfig.ARENA_AGILITY_CAP);
            this.bravery = bravery;
            this.baseOdds = CasinoConfig.ARENA_BASE_ODDS;
        }
        
        public float getCurrentOdds(int currentRound) {
            if (isDead) return 0.0f;

            if (arenaRef != null && combatantsRef != null && combatantIndex >= 0) {
                return arenaRef.calculateCurrentOdds(combatantsRef, combatantIndex, currentRound);
            }

            float currentOdds = baseOdds;
            float hpRatio = (float) hp / (float) maxHp;
            float hpFactor = 1.0f + (CasinoConfig.ARENA_HP_ODDS_FACTOR - 1.0f) * (1.0f - hpRatio);
            hpFactor = Math.max(CasinoConfig.ARENA_MIN_HP_ODDS_MULT,
                      Math.min(CasinoConfig.ARENA_MAX_HP_ODDS_MULT, hpFactor));
            currentOdds *= hpFactor;

            if (currentRound > 0) {
                float basePenalty = CasinoConfig.ARENA_MID_ROUND_BASE_PENALTY;
                float progressivePenalty = 1.0f - (currentRound * CasinoConfig.ARENA_MID_ROUND_PROGRESSIVE_PENALTY);
                progressivePenalty = Math.max(CasinoConfig.ARENA_DIMINISHING_RETURNS_MIN, progressivePenalty);
                currentOdds *= basePenalty * progressivePenalty;
            }

            currentOdds *= (1.0f - CasinoConfig.ARENA_HOUSE_EDGE);
            return Math.max(CasinoConfig.ARENA_MIN_ODDS, currentOdds);
        }
        
        public String getCurrentOddsString(int currentRound) {
            return "1:" + String.format("%.1f", getCurrentOdds(currentRound));
        }
        
        public String getBaseOddsString() {
            return "1:" + String.format("%.1f", baseOdds);
        }
        
        /**
         * @deprecated Use getBaseOddsString() or getCurrentOddsString(round)
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
        
        public SpiralGladiator copyForSimulation() {
            SpiralGladiator copy = new SpiralGladiator(hullId, prefix, hullName, affix, maxHp, power, agility, bravery);
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
        
        public void setArenaReference(SpiralAbyssArena arena, List<SpiralGladiator> combatants, int index) {
            this.arenaRef = arena;
            this.combatantsRef = combatants;
            this.combatantIndex = index;
            this.baseOdds = arena.calculateCurrentOdds(combatants, index, 0);
        }
    }
    
    private static class SimulationResult {
        Map<Integer, Integer> positions;
        Map<Integer, Integer> kills;
        
        SimulationResult(Map<Integer, Integer> positions, Map<Integer, Integer> kills) {
            this.positions = positions;
            this.kills = kills;
        }
    }
    
    private SimulationResult runSimulationToCompletionWithKills(List<SpiralGladiator> simCombatants, Map<Integer, Integer> originalToSimIndex) {
        Random simRandom = new Random();
        Map<Integer, Integer> simToOriginalIndex = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : originalToSimIndex.entrySet()) {
            simToOriginalIndex.put(entry.getValue(), entry.getKey());
        }
        
        Map<Integer, Integer> finalPositions = new HashMap<>();
        Map<Integer, Integer> finalKills = new HashMap<>();
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
            int attacksDoneThisRound = 0;
            List<SpiralGladiator> diedThisRound = new ArrayList<>();
            
            while (true) {
                alive.clear();
                for (SpiralGladiator g : simCombatants) {
                    if (!g.isDead) alive.add(g);
                }
                if (alive.size() < 2) break;
                
                if (attacksDoneThisRound >= attacksThisRound) break;
                
                int minAttacks = Integer.MAX_VALUE;
                for (SpiralGladiator g : alive) {
                    if (g.totalAttacks < minAttacks) minAttacks = g.totalAttacks;
                }
                
                List<SpiralGladiator> eligibleAttackers = new ArrayList<>();
                for (SpiralGladiator g : alive) {
                    if (g.totalAttacks == minAttacks) {
                        eligibleAttackers.add(g);
                    }
                }
                
                SpiralGladiator attacker = eligibleAttackers.get(simRandom.nextInt(eligibleAttackers.size()));
                attacker.totalAttacks++;
                attacksDoneThisRound++;
                
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
            SpiralGladiator g = simCombatants.get(i);
            int originalIndex = simToOriginalIndex.get(i);
            finalKills.put(originalIndex, g.kills);
            if (!g.isDead) {
                finalPositions.put(originalIndex, 0);
            }
        }
        
        return new SimulationResult(finalPositions, finalKills);
    }
    
    public List<SpiralGladiator> generateCombatants(CasinoGachaManager gacha) {
        List<SpiralGladiator> list = new ArrayList<>();
        List<String> pool = new ArrayList<>();
        Set<String> usedHullIds = new HashSet<>();
        
        String randomCapital = gacha.getRandomStandardHull(ShipAPI.HullSize.CAPITAL_SHIP, null);
        if (randomCapital != null) {
            pool.add(randomCapital);
            usedHullIds.add(randomCapital);
        }
        
        int attempts = 0;
        while (pool.size() < 3 && attempts < 20) {
            String randomCruiser = gacha.getRandomStandardHull(ShipAPI.HullSize.CRUISER, null);
            if (randomCruiser != null && !usedHullIds.contains(randomCruiser)) {
                pool.add(randomCruiser);
                usedHullIds.add(randomCruiser);
            }
            attempts++;
        }
        
        attempts = 0;
        while (pool.size() < CasinoConfig.ARENA_SHIP_COUNT && attempts < 50) {
            String randomHull = gacha.getRandomStandardHull(ShipAPI.HullSize.DESTROYER, null);
            if (randomHull != null && !usedHullIds.contains(randomHull)) {
                pool.add(randomHull);
                usedHullIds.add(randomHull);
            }
            attempts++;
        }
        
        for (String hullId : pool) {
            if (hullId == null) continue;
            
            ShipHullSpecAPI spec = Global.getSettings().getHullSpec(hullId);
            if (spec == null) continue;
            
            CasinoConfig.ArenaStat base = CasinoConfig.ARENA_BASE_STATS.get(spec.getHullSize());
            if (base == null) base = CasinoConfig.ARENA_BASE_STATS.get(ShipAPI.HullSize.FRIGATE);
            
            int hp = base.hp;
            int power = base.power;
            float agility = base.agility;
            float bravery = 0.20f;
            
            List<String> prefixPos = Strings.getList("arena_prefixes.positive");
            List<String> prefixNeg = Strings.getList("arena_prefixes.negative");
            int prefixIdx = random.nextInt(prefixPos.size());
            boolean posPrefix = random.nextBoolean();
            String prefix = posPrefix ? prefixPos.get(prefixIdx) : prefixNeg.get(prefixIdx);
            
            float multP = posPrefix ? CasinoConfig.ARENA_PREFIX_MULT_STRONG : CasinoConfig.ARENA_PREFIX_MULT_WEAK;
            if (prefixIdx == 0) hp = (int)(hp * multP);
            else if (prefixIdx == 1) power = (int)(power * multP);
            else if (prefixIdx == 2) agility = posPrefix ? agility + CasinoConfig.ARENA_PREFIX_AGILITY_BONUS : Math.max(0, agility - CasinoConfig.ARENA_PREFIX_AGILITY_BONUS);
            else bravery = posPrefix ? bravery + CasinoConfig.ARENA_PREFIX_BRAVERY_BONUS : Math.max(0, bravery - CasinoConfig.ARENA_PREFIX_BRAVERY_BONUS);
            
            List<String> affixPos = Strings.getList("arena_affixes.positive");
            List<String> affixNeg = Strings.getList("arena_affixes.negative");
            int affixIdx = random.nextInt(affixPos.size());
            boolean posAffix = random.nextBoolean();
            String affix = posAffix ? affixPos.get(affixIdx) : affixNeg.get(affixIdx);
            
            List<String> allValidAffixes = new ArrayList<>();
            allValidAffixes.addAll(affixPos);
            allValidAffixes.addAll(affixNeg);
            
            if (!allValidAffixes.contains(affix)) {
                Global.getLogger(this.getClass()).warn("Invalid affix detected: " + affix + ", falling back to default");
                affix = posAffix ? affixPos.get(0) : affixNeg.get(0);
            }
            
            float multA = posAffix ? CasinoConfig.ARENA_AFFIX_MULT_STRONG : CasinoConfig.ARENA_AFFIX_MULT_WEAK;
            if (affixIdx == 0) hp = (int)(hp * multA);
            else if (affixIdx == 1) power = (int)(power * multA);
            else if (affixIdx == 2) agility = posAffix ? agility + CasinoConfig.ARENA_AFFIX_AGILITY_BONUS : Math.max(0, agility - CasinoConfig.ARENA_AFFIX_AGILITY_BONUS);
            else bravery = posAffix ? bravery + CasinoConfig.ARENA_AFFIX_BRAVERY_BONUS : Math.max(0, bravery - CasinoConfig.ARENA_AFFIX_BRAVERY_BONUS);
            
            agility = Math.min(agility, CasinoConfig.ARENA_AGILITY_CAP);
            SpiralGladiator gladiator = new SpiralGladiator(hullId, prefix, spec.getHullName(), affix, hp, power, agility, bravery);
            list.add(gladiator);
        }
        
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setArenaReference(this, list, i);
        }
        
        return list;
    }
    
    public List<String> simulateStep(List<SpiralGladiator> combatants, int currentRound) {
        List<String> log = new ArrayList<>();
        
        List<SpiralGladiator> alive = new ArrayList<>();
        for (SpiralGladiator g : combatants) if (!g.isDead) alive.add(g);
        
        if (alive.size() < 2) return log;
        
        log.add("[ROUND] Round " + (currentRound + 1));

        int attacksThisStep = Math.max(alive.size(), (int)(alive.size() * CasinoConfig.ARENA_ACTION_MULTIPLIER));
        int attacksDoneThisStep = 0;
        
        while (true) {
            List<SpiralGladiator> currentAlive = new ArrayList<>();
            for (SpiralGladiator g : combatants) if (!g.isDead) currentAlive.add(g);
            if (currentAlive.size() < 2) break;
            
            if (attacksDoneThisStep >= attacksThisStep) break;
            
            int minAttacks = Integer.MAX_VALUE;
            for (SpiralGladiator g : currentAlive) {
                if (g.totalAttacks < minAttacks) minAttacks = g.totalAttacks;
            }
            
            List<SpiralGladiator> eligibleAttackers = new ArrayList<>();
            for (SpiralGladiator g : currentAlive) {
                if (g.totalAttacks == minAttacks) {
                    eligibleAttackers.add(g);
                }
            }
            
            SpiralGladiator attacker = eligibleAttackers.get(random.nextInt(eligibleAttackers.size()));
            attacker.totalAttacks++;
            attacksDoneThisStep++;
            
            SpiralGladiator target = currentAlive.get(random.nextInt(currentAlive.size()));
            while (target == attacker) target = currentAlive.get(random.nextInt(currentAlive.size()));
            
            if (attacker.retaliateTarget != null && !attacker.retaliateTarget.isDead) {
                target = attacker.retaliateTarget;
                attacker.isEnraged = true;
                attacker.targetOfRage = attacker.retaliateTarget;
                attacker.retaliateTarget = null;
            } else if (attacker.retaliateTarget != null) {
                attacker.retaliateTarget = null;
                attacker.isEnraged = false;
                attacker.targetOfRage = null;
            }
            
            if (attacker.targetOfRage != null && attacker.targetOfRage.isDead) {
                attacker.isEnraged = false;
                attacker.targetOfRage = null;
            }

            float hitChance = 0.7f + attacker.agility - target.agility;
            if (random.nextFloat() < hitChance) {
                boolean crit = random.nextFloat() < attacker.bravery;
                int dmg = (int)(attacker.power * (crit ? 1.5f : 1.0f));
                target.hp -= dmg;
                
                String flavor = crit ? getFlavor(Strings.getList("arena_flavor.crit"), lastCritHistory) : getFlavor(Strings.getList("arena_flavor.attack"), lastAttackHistory);
                String prefix = crit ? "[CRIT] " : "[HIT] ";
                log.add(prefix + flavor.replace("$attacker", attacker.shortName).replace("$target", target.shortName).replace("$dmg", ""+dmg));
                
                if (random.nextFloat() < target.bravery) {
                    target.retaliateTarget = attacker;
                    target.isEnraged = true;
                    target.targetOfRage = attacker;
                }
                
                if (target.hp <= 0) {
                    target.isDead = true;
                    target.isEnraged = false;
                    target.targetOfRage = null;
                    attacker.kills++;
                    String kill = getFlavor(Strings.getList("arena_flavor.kill"), lastKillHistory);
                    log.add("[KILL] " + kill.replace("$attacker", attacker.shortName).replace("$target", target.shortName));
                }
            } else {
                String miss = getFlavor(Strings.getList("arena_flavor.miss"), lastMissHistory);
                log.add("[MISS] " + miss.replace("$attacker", attacker.shortName).replace("$target", target.shortName));
            }
        }

        alive.clear();
        for (SpiralGladiator g : combatants) if (!g.isDead) alive.add(g);

        if (currentRound > 0 && alive.size() >= 2 && random.nextFloat() < CasinoConfig.ARENA_CHAOS_EVENT_CHANCE) {
            ChaosEventType type = ChaosEventType.values()[random.nextInt(ChaosEventType.values().length)];

            if (type == ChaosEventType.SINGLE_SHIP_DAMAGE) {
                SpiralGladiator target = alive.get(random.nextInt(alive.size()));
                int dmg = (int)(target.maxHp * CasinoConfig.ARENA_SINGLE_SHIP_DAMAGE_PERCENT);
                target.hp -= dmg;

                String description = getRandomDescription(Strings.getList("arena_damage.single"));
                log.add("[EVENT] " + description.replace("$ship", target.shortName) + " (-" + dmg + " HP)");

                if (target.hp <= 0) {
                    target.isDead = true;
                    target.isEnraged = false;
                    target.targetOfRage = null;
                    log.add("[KILL] " + target.shortName + " was destroyed by the incident!");
                }
            } else if (type == ChaosEventType.MULTI_SHIP_DAMAGE) {
                int shipsToDamage = Math.min(alive.size(), 2 + random.nextInt(Math.min(3, alive.size() - 1)));
                List<SpiralGladiator> shuffled = new ArrayList<>(alive);
                Collections.shuffle(shuffled, random);

                String description = getRandomDescription(Strings.getList("arena_damage.multi"));
                log.add("[EVENT] " + description);

                for (int i = 0; i < shipsToDamage && i < shuffled.size(); i++) {
                    SpiralGladiator target = shuffled.get(i);
                    int dmg = (int)(target.maxHp * CasinoConfig.ARENA_MULTI_SHIP_DAMAGE_PERCENT);
                    target.hp -= dmg;
                    log.add("[HIT] " + target.shortName + " takes " + dmg + " damage!");

                    if (target.hp <= 0) {
                        target.isDead = true;
                        target.isEnraged = false;
                        target.targetOfRage = null;
                        log.add("[KILL] " + target.shortName + " was destroyed!");
                    }
                }
            }
        }

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
    
    public void invalidateOddsCache() {
        cachedPositionProbabilities = null;
        cachedPositionRound = -1;
        cachedHpValues = null;
    }
    
    private String getRandomDescription(List<String> descriptions) {
        if (descriptions == null || descriptions.isEmpty()) {
            return Strings.get("arena_panel_rewards.incident_occurs");
        }
        return descriptions.get(random.nextInt(descriptions.size()));
    }
    
    private boolean isPositionCacheValid(List<SpiralGladiator> combatants, int currentRound) {
        if (cachedPositionProbabilities == null || cachedHpValues == null) return false;
        if (cachedPositionRound != currentRound) return false;
        if (cachedHpValues.size() != combatants.size()) return false;
        for (int i = 0; i < combatants.size(); i++) {
            if (combatants.get(i).hp != cachedHpValues.get(i)) return false;
        }
        return true;
    }
    
    private void updatePositionCache(List<SpiralGladiator> combatants, int currentRound, 
                                      Map<Integer, Map<Integer, Float>> probabilities,
                                      Map<Integer, Float> expectedKills) {
        cachedPositionRound = currentRound;
        cachedHpValues = new ArrayList<>();
        for (SpiralGladiator g : combatants) {
            cachedHpValues.add(g.hp);
        }
        cachedPositionProbabilities = new HashMap<>();
        for (Map.Entry<Integer, Map<Integer, Float>> entry : probabilities.entrySet()) {
            cachedPositionProbabilities.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        cachedExpectedKills = new HashMap<>(expectedKills);
    }
    
    public Map<Integer, Map<Integer, Float>> calculatePositionProbabilities(List<SpiralGladiator> combatants, int currentRound) {
        if (isPositionCacheValid(combatants, currentRound)) {
            return cachedPositionProbabilities;
        }
        
        Map<Integer, Map<Integer, Float>> positionProbabilities = new HashMap<>();
        Map<Integer, Float> expectedKills = new HashMap<>();
        int aliveCount = 0;
        
        for (int i = 0; i < combatants.size(); i++) {
            if (!combatants.get(i).isDead) {
                aliveCount++;
                positionProbabilities.put(i, new HashMap<>());
                expectedKills.put(i, 0.0f);
            }
        }
        
        if (aliveCount == 0) {
            return positionProbabilities;
        }
        if (aliveCount == 1) {
            for (int i = 0; i < combatants.size(); i++) {
                if (!combatants.get(i).isDead) {
                    positionProbabilities.get(i).put(0, 1.0f);
                    expectedKills.put(i, 0.0f);
                    updatePositionCache(combatants, currentRound, positionProbabilities, expectedKills);
                    return positionProbabilities;
                }
            }
        }
        
        int simulations = CasinoConfig.ARENA_SIMULATION_COUNT;
        Map<Integer, Map<Integer, Integer>> positionCounts = new HashMap<>();
        Map<Integer, Integer> totalKills = new HashMap<>();
        
        for (int i = 0; i < combatants.size(); i++) {
            if (!combatants.get(i).isDead) {
                positionCounts.put(i, new HashMap<>());
                totalKills.put(i, 0);
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
            
            SimulationResult result = runSimulationToCompletionWithKills(simCombatants, originalToSimIndex);
            
            for (Map.Entry<Integer, Integer> entry : result.positions.entrySet()) {
                int originalIndex = entry.getKey();
                int position = entry.getValue();
                Map<Integer, Integer> counts = positionCounts.get(originalIndex);
                counts.put(position, counts.getOrDefault(position, 0) + 1);
            }
            
            for (Map.Entry<Integer, Integer> entry : result.kills.entrySet()) {
                int originalIndex = entry.getKey();
                totalKills.put(originalIndex, totalKills.get(originalIndex) + entry.getValue());
            }
        }
        
        for (Map.Entry<Integer, Map<Integer, Integer>> shipEntry : positionCounts.entrySet()) {
            int shipIndex = shipEntry.getKey();
            Map<Integer, Float> probs = positionProbabilities.get(shipIndex);
            for (Map.Entry<Integer, Integer> posEntry : shipEntry.getValue().entrySet()) {
                float probability = (float) posEntry.getValue() / simulations;
                probs.put(posEntry.getKey(), probability);
            }
            expectedKills.put(shipIndex, (float) totalKills.get(shipIndex) / simulations);
        }
        
        for (int i = 0; i < combatants.size(); i++) {
            if (!combatants.get(i).isDead) {
                Map<Integer, Float> probs = positionProbabilities.get(i);
                if (!probs.containsKey(0)) {
                    probs.put(0, 0.001f);
                }
            }
        }
        
        updatePositionCache(combatants, currentRound, positionProbabilities, expectedKills);
        
        return positionProbabilities;
    }
    
    @Deprecated
    public Map<Integer, Map<Integer, Float>> calculatePositionProbabilities(List<SpiralGladiator> combatants) {
        return calculatePositionProbabilities(combatants, 0);
    }
    
    public static float getPositionFactor(int finalPosition) {
        if (finalPosition <= 0) return 0.0f;
        
        float[] factors = CasinoConfig.ARENA_CONSOLATION_POSITION_FACTORS;
        int index = finalPosition - 1;
        
        if (index < factors.length) {
            return factors[index];
        }
        return factors[factors.length - 1];
    }
    
    public float calculateCurrentOdds(List<SpiralGladiator> combatants, int shipIndex, int currentRound) {
        SpiralGladiator ship = combatants.get(shipIndex);
        
        if (ship.isDead) return 0.0f;
        
        Map<Integer, Map<Integer, Float>> positionProbabilities = calculatePositionProbabilities(combatants, currentRound);
        Map<Integer, Float> shipPositionProbs = positionProbabilities.get(shipIndex);
        
        if (shipPositionProbs == null || shipPositionProbs.isEmpty()) {
            return CasinoConfig.ARENA_MIN_ODDS;
        }
        
        Float winProbability = shipPositionProbs.get(0);
        if (winProbability == null || winProbability <= 0.0f) {
            return CasinoConfig.ARENA_MIN_ODDS;
        }
        
        float expectedConsolationRate = 0.0f;
        for (Map.Entry<Integer, Float> posEntry : shipPositionProbs.entrySet()) {
            int position = posEntry.getKey();
            float probability = posEntry.getValue();
            if (position > 0) {
                float positionFactor = getPositionFactor(position);
                expectedConsolationRate += probability * CasinoConfig.ARENA_CONSOLATION_BASE * positionFactor;
            }
        }
        
        float expectedKillBonus = 0.0f;
        if (currentRound == 0 && cachedExpectedKills != null) {
            Float expKills = cachedExpectedKills.get(shipIndex);
            if (expKills != null && expKills > 0) {
                expectedKillBonus = winProbability * expKills * CasinoConfig.ARENA_KILL_BONUS_PER_KILL;
            }
        }
        
        float availableForWinPayout = 1.0f - CasinoConfig.ARENA_HOUSE_EDGE - expectedConsolationRate - expectedKillBonus;
        if (availableForWinPayout <= 0.0f) {
            return CasinoConfig.ARENA_MIN_ODDS;
        }
        
        float fairOdds = availableForWinPayout / winProbability;
        
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