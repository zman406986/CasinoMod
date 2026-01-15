package data.scripts.casino;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import java.util.*;

/**
 * Handles the gacha system for obtaining ships/weapons.
 * Implements pity system and featured item rotation.
 */
public class CasinoGachaManager {
    
    private static final String DATA_KEY = "CasinoGachaData";
    
    /**
     * Rotation period for featured items in days
     */
    private static final long ROTATION_PERIOD_DAYS = 14;
    
    private final Random random = new Random();
    
    // Genshin Rates - Now using values from CasinoConfig
    private static final float RATE_5_STAR_BASE = CasinoConfig.PROB_5_STAR; // 0.6%
    private static final float RATE_4_STAR_BASE = CasinoConfig.PROB_4_STAR; // 5.1%
    
    /**
     * Persistent data for gacha system including pity counters and auto-convert settings
     */
    public static class GachaData {
        public long lastRotationTimestamp;  // When the last featured rotation occurred
        public int pity5;                   // Counter for 5-star pity
        public int pity4;                   // Counter for 4-star pity
        public boolean guaranteedFeatured5; // True if next 5-star is guaranteed to be featured
        public boolean guaranteedFeatured4; // True if next 4-star is guaranteed to be featured
        
        public String featuredCapital;      // Featured 5-star capital ship
        public List<String> featuredCruisers = new ArrayList<>(); // Featured 4-star cruisers
        public List<String> autoConvertIds = new ArrayList<>();   // IDs of ships to auto-convert
    }
    
    public GachaData getData() {
        Map<String, Object> data = Global.getSector().getPersistentData();
        if (!data.containsKey(DATA_KEY)) {
            data.put(DATA_KEY, new GachaData());
            rotatePool((GachaData)data.get(DATA_KEY));
        }
        return (GachaData) data.get(DATA_KEY);
    }
    
    /**
     * Checks if a ship hull spec is allowed in the gacha pool
     * Based on similar logic to Nexerelin's Prism Shop
     */
    public boolean isShipAllowed(ShipHullSpecAPI spec) {
        // Skip fighters
        if (spec.getHullSize() == ShipAPI.HullSize.FIGHTER) return false;
        
        // Skip station modules
        if (spec.getHints().contains(ShipHullSpecAPI.ShipTypeHints.STATION)) return false;
        
        // Skip civilian ships
        if (spec.getHints().contains(ShipHullSpecAPI.ShipTypeHints.CIVILIAN)) return false;
        
        // Skip d-hulls
        if (spec.isDHull()) return false;
        
        // Skip ships with no-sell tags
        if (spec.hasTag("NoSell") || spec.hasTag("Restricted") || spec.hasTag("NoDealer")) return false;
        
        // Skip ships that start with restricted prefixes
        String hullId = spec.getHullId();
        if (hullId.startsWith("tem_")) return false;
        
        // Skip boss ships (secret ships) - typically very high FP ships
        if (spec.getFleetPoints() > 50) return false; // Adjust this value as needed
        
        return true;
    }
    
    /**
     * Returns list of all possible ship hulls that can drop from gacha
     */
    public List<FleetMemberAPI> getPotentialDrops() {
         List<FleetMemberAPI> list = new ArrayList<>();
         Set<String> uniqueHullIds = new HashSet<>(); // Track unique hull IDs to avoid duplicates
         
         for (ShipHullSpecAPI spec : Global.getSettings().getAllShipHullSpecs()) {
             // Apply restrictions similar to Nexerelin Prism Shop
             if (!isShipAllowed(spec)) continue;
             
             // Use the hull ID to identify unique ships (not the hull name)
             String hullId = spec.getHullId();
             if (!uniqueHullIds.contains(hullId) && list.size() < 20) { // Limit to 20 ships
                 uniqueHullIds.add(hullId);
                 list.add(Global.getFactory().createFleetMember(FleetMemberType.SHIP, spec.getHullId() + "_Hull")); 
             }
         }
         return list;
    }

    /**
     * Randomly selects featured items for the current rotation
     */
    public void rotatePool(GachaData data) {
        data.lastRotationTimestamp = Global.getSector().getClock().getTimestamp();
        
        List<ShipHullSpecAPI> allHulls = Global.getSettings().getAllShipHullSpecs();
        List<String> capitals = new ArrayList<>();
        List<String> cruisers = new ArrayList<>();
        
        for (ShipHullSpecAPI spec : allHulls) {
            // Apply the same filtering as isShipAllowed
            if (!isShipAllowed(spec)) continue;
            
            if (spec.getHullSize() == ShipAPI.HullSize.CAPITAL_SHIP) capitals.add(spec.getHullId());
            if (spec.getHullSize() == ShipAPI.HullSize.CRUISER) cruisers.add(spec.getHullId());
        }
        
        if (!capitals.isEmpty()) {
            data.featuredCapital = capitals.get(random.nextInt(capitals.size()));
        }
        
        data.featuredCruisers.clear();
        Collections.shuffle(cruisers);
        for (int i=0; i<3 && i<cruisers.size(); i++) {
            data.featuredCruisers.add(cruisers.get(i));
        }
    }
    
    /**
     * Checks if featured items need to be rotated based on time elapsed
     */
    public void checkRotation() {
        GachaData data = getData();
        float days = Global.getSector().getClock().getElapsedDaysSince(data.lastRotationTimestamp);
        if (days >= ROTATION_PERIOD_DAYS) {
            rotatePool(data);
        }
    }
    
    /**
     * Performs a single gacha pull with normal fleet addition behavior
     */
    public String performPull() {
        GachaData data = getData();
        data.pity5++;
        data.pity4++;
        
        // --- 1. Check 5* (Capital) ---
        float currentRate5 = CasinoConfig.PROB_5_STAR;
        
        if (data.pity5 >= CasinoConfig.PITY_SOFT_START_5) {
            currentRate5 = CasinoConfig.PROB_5_STAR + (data.pity5 - (CasinoConfig.PITY_SOFT_START_5 - 1)) * 0.06f;
        }
        if (data.pity5 >= CasinoConfig.PITY_HARD_5) currentRate5 = 10.0f; 
        
        float roll = random.nextFloat();
        
        if (roll < currentRate5) {
            data.pity5 = 0; 
            return handle5StarForNormalUsage(data);
        }
        
        // --- 2. Check 4* (Cruiser) ---
        float currentRate4 = CasinoConfig.PROB_4_STAR;
        if (data.pity4 >= CasinoConfig.PITY_HARD_4) currentRate4 = 10.0f;
        
        float roll4 = random.nextFloat();
        
        if (roll4 < currentRate4) {
             data.pity4 = 0;
             return handle4StarForNormalUsage(data);
        }
        
        // --- 3. Trash (3*) ---
        String s = getRandomHull(random.nextBoolean() ? ShipAPI.HullSize.DESTROYER : ShipAPI.HullSize.FRIGATE);
             
        // Auto Convert Check
        if (data.autoConvertIds.contains(s)) {
            int val = (int)(Global.getSettings().getHullSpec(s).getBaseValue() / CasinoConfig.SHIP_TRADE_RATE);
            CasinoVIPManager.addStargems(val);
            return "Auto-Converted: " + Global.getSettings().getHullSpec(s).getHullName() + " (+" + val + " Gems)";
        }
             
        FleetMemberAPI m = createShip(s);
        if (m != null) {
           Global.getSector().getPlayerFleet().getFleetData().addFleetMember(m);
           return m.getShipName() + " (" + m.getHullSpec().getHullName() + ")";
        } else {
           return "Error: Blueprint corrupted.";
        }
    }
    
    /**
     * Performs a single gacha pull but collects ships without adding to fleet
     */
    public String performPullDetailed(List<FleetMemberAPI> collectedShips) {
        GachaData data = getData();
        data.pity5++;
        data.pity4++;
        
        // --- 1. Check 5* (Capital) ---
        float currentRate5 = CasinoConfig.PROB_5_STAR;
        
        if (data.pity5 >= CasinoConfig.PITY_SOFT_START_5) {
            currentRate5 = CasinoConfig.PROB_5_STAR + (data.pity5 - (CasinoConfig.PITY_SOFT_START_5 - 1)) * 0.06f;
        }
        if (data.pity5 >= CasinoConfig.PITY_HARD_5) currentRate5 = 10.0f; 
        
        float roll = random.nextFloat();
        
        if (roll < currentRate5) {
            data.pity5 = 0; 
            return handle5StarDetailed(data, collectedShips);
        }
        
        // --- 2. Check 4* (Cruiser) ---
        float currentRate4 = CasinoConfig.PROB_4_STAR;
        if (data.pity4 >= CasinoConfig.PITY_HARD_4) currentRate4 = 10.0f;
        
        float roll4 = random.nextFloat();
        
        if (roll4 < currentRate4) {
             data.pity4 = 0;
             return handle4StarDetailed(data, collectedShips);
        }
        
        // --- 3. Trash (3*) ---
        String s = getRandomHull(random.nextBoolean() ? ShipAPI.HullSize.DESTROYER : ShipAPI.HullSize.FRIGATE);
             
        // Auto Convert Check - still do this immediately
        if (data.autoConvertIds.contains(s)) {
            int val = (int)(Global.getSettings().getHullSpec(s).getBaseValue() / CasinoConfig.SHIP_TRADE_RATE);
            CasinoVIPManager.addStargems(val);
            return "Auto-Converted: " + Global.getSettings().getHullSpec(s).getHullName() + " (+" + val + " Gems)";
        }
             
        FleetMemberAPI m = createShip(s);
        if (m != null) {
           // Add to collected ships but don't add to fleet yet - let the handler decide
           collectedShips.add(m);
           return m.getShipName() + " (" + m.getHullSpec().getHullName() + ")";
        } else {
           return "Error: Blueprint corrupted.";
        }
    }
    
    /**
     * Handles 5-star pull result with fleet addition
     */
    private String handle5StarForNormalUsage(GachaData data) {
        String resultId;
        boolean isFeatured = false;
        
        if (data.guaranteedFeatured5) {
            resultId = data.featuredCapital;
            data.guaranteedFeatured5 = false; 
            isFeatured = true;
        } else {
             if (random.nextBoolean()) {
                 resultId = data.featuredCapital;
                 isFeatured = true;
             } else {
                 resultId = getRandomStandardHull(ShipAPI.HullSize.CAPITAL_SHIP, data.featuredCapital);
                 data.guaranteedFeatured5 = true; 
             }
        }
        
        FleetMemberAPI member = createShip(resultId);
        Global.getSector().getPlayerFleet().getFleetData().addFleetMember(member);
        return member.getShipName() + " (" + member.getHullSpec().getHullName() + ") " + (isFeatured ? "[FEATURED 5*]" : "[5*]");
    }
    
    /**
     * Handles 4-star pull result with fleet addition
     */
    private String handle4StarForNormalUsage(GachaData data) {
        String resultId;
        boolean isFeatured = false;
        
        if (data.guaranteedFeatured4) {
            resultId = data.featuredCruisers.get(random.nextInt(data.featuredCruisers.size()));
            data.guaranteedFeatured4 = false;
            isFeatured = true;
        } else {
            if (random.nextBoolean()) {
                resultId = data.featuredCruisers.get(random.nextInt(data.featuredCruisers.size()));
                isFeatured = true;
            } else {
                resultId = getRandomStandardHull(ShipAPI.HullSize.CRUISER, null); 
                data.guaranteedFeatured4 = true;
            }
        }
        
        FleetMemberAPI member = createShip(resultId);
        Global.getSector().getPlayerFleet().getFleetData().addFleetMember(member);
        return member.getShipName() + " (" + member.getHullSpec().getHullName() + ") " + (isFeatured ? "[FEATURED 4*]" : "[4*]");
    }
    
    /**
     * Handles 5-star pull result without fleet addition
     */
    private String handle5StarDetailed(GachaData data, List<FleetMemberAPI> collectedShips) {
        String resultId;
        boolean isFeatured = false;
        
        if (data.guaranteedFeatured5) {
            resultId = data.featuredCapital;
            data.guaranteedFeatured5 = false; 
            isFeatured = true;
        } else {
             if (random.nextBoolean()) {
                 resultId = data.featuredCapital;
                 isFeatured = true;
             } else {
                 resultId = getRandomStandardHull(ShipAPI.HullSize.CAPITAL_SHIP, data.featuredCapital);
                 data.guaranteedFeatured5 = true; 
             }
        }
        
        FleetMemberAPI member = createShip(resultId);
        if (member != null) {
            collectedShips.add(member);
        }
        return member.getShipName() + " (" + member.getHullSpec().getHullName() + ") " + (isFeatured ? "[FEATURED 5*]" : "[5*]");
    }
    
    /**
     * Handles 4-star pull result without fleet addition
     */
    private String handle4StarDetailed(GachaData data, List<FleetMemberAPI> collectedShips) {
        String resultId;
        boolean isFeatured = false;
        
        if (data.guaranteedFeatured4) {
            resultId = data.featuredCruisers.get(random.nextInt(data.featuredCruisers.size()));
            data.guaranteedFeatured4 = false;
            isFeatured = true;
        } else {
            if (random.nextBoolean()) {
                resultId = data.featuredCruisers.get(random.nextInt(data.featuredCruisers.size()));
                isFeatured = true;
            } else {
                resultId = getRandomStandardHull(ShipAPI.HullSize.CRUISER, null); 
                data.guaranteedFeatured4 = true;
            }
        }
        
        FleetMemberAPI member = createShip(resultId);
        if (member != null) {
            collectedShips.add(member);
        }
        return member.getShipName() + " (" + member.getHullSpec().getHullName() + ") " + (isFeatured ? "[FEATURED 4*]" : "[4*]");
    }
    
    /**
     * Creates a fleet member from a hull ID with error handling
     */
    public FleetMemberAPI createShip(String hullId) {
        String variantId = hullId + "_Hull"; 
        try {
            return Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId);
        } catch (Exception e) {
            try {
                ShipHullSpecAPI spec = Global.getSettings().getHullSpec(hullId);
                if (spec == null) throw new RuntimeException("Hull Spec not found: " + hullId);
                com.fs.starfarer.api.combat.ShipVariantAPI variant = Global.getSettings().createEmptyVariant(hullId + "_temp", spec);
                return Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);
            } catch (Exception ex) {
                Global.getLogger(this.getClass()).error("CRITICAL: specific hullId " + hullId + " does not exist.", ex);
                return Global.getFactory().createFleetMember(FleetMemberType.SHIP, "kite_Support_Hull"); // Changed to a more commonly available fallback
            }
        }
    }

    /**
     * Gets a random standard hull of specified size from the allowed pool
     */
    public String getRandomStandardHull(ShipAPI.HullSize size, String excludeId) {
        List<String> list = new ArrayList<>();
        for (ShipHullSpecAPI spec : Global.getSettings().getAllShipHullSpecs()) {
            if (spec.getHullSize() == size && isShipAllowed(spec) && !spec.getHullId().equals(excludeId)) {
                list.add(spec.getHullId());
            }
        }
        if (list.isEmpty()) {
            // If no eligible ships found, return a fallback
            if (size == ShipAPI.HullSize.CAPITAL_SHIP) {
                return "eagle_Balanced"; // Common capital ship
            } else if (size == ShipAPI.HullSize.CRUISER) {
                return "cerberus_Balanced"; // Common cruiser
            } else if (size == ShipAPI.HullSize.DESTROYER) {
                return "wolf_Balanced"; // Common destroyer
            } else { // FRIGATE
                return "lasher_Balanced"; // Common frigate
            }
        }
        return list.get(random.nextInt(list.size()));
    }
    
    /**
     * Gets a random hull of specified size from the allowed pool
     */
    private String getRandomHull(ShipAPI.HullSize size) {
        List<String> list = new ArrayList<>();
        for (ShipHullSpecAPI spec : Global.getSettings().getAllShipHullSpecs()) {
             if (spec.getHullSize() == size && isShipAllowed(spec)) {
                list.add(spec.getHullId());
            }
        }
        if (list.isEmpty()) {
            // If no eligible ships found, return a fallback
            if (size == ShipAPI.HullSize.DESTROYER) {
                return "wolf_Balanced"; // Common destroyer
            } else { // FRIGATE
                return "lasher_Balanced"; // Common frigate
            }
        }
        return list.get(random.nextInt(list.size()));
    }


}