package data.scripts.casino;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI.ShipTypeHints;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class CasinoGachaManager {
    
    private static final String DATA_KEY = "CasinoGachaData";
    
    private static final long ROTATION_PERIOD_DAYS = 14;
    
    private final Random random = new Random();
    
    // Genshin Rates - Using values from CasinoConfig
    
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
    
    private static final Set<String> DISALLOWED_PREFIXES = new HashSet<>(Arrays.asList(
        "tem_",      // Templars (special faction)
        "vayra_",    // Vayra's Sector unique bounty ships
        "swp_",      // Ship/Weapon Pack boss ships
        "apex_",     // Apex Design Collective
        "tahlan_",   // Tahlan Shipworks special ships
        "uw_",       // Underworld special ships
        "rat_",      // Roider special
        "dcp_",      // Domain Convergence Project
        "mso_",      // Mysterious Ship Overhaul
        "xiv_"       // XIV Battlegroup (special Hegemony ships)
    ));
    
    private int getMinimumFleetPoints(ShipAPI.HullSize size) {
        switch (size) {
            case CAPITAL_SHIP: return 20;
            case CRUISER: return 14;
            case DESTROYER: return 8;
            case FRIGATE: return 4;
            default: return 5;
        }
    }
    
    public boolean isShipAllowed(ShipHullSpecAPI spec) {
        // Safety check for null spec
        if (spec == null) return false;
        
        // Skip d-hulls (damaged hulls)
        if (spec.isDHull()) return false;
        
        // Skip ships with no-sell tags (using same tags as Prism Shop)
        if (spec.hasTag(Tags.NO_SELL)) return false;
        if (spec.hasTag(Tags.RESTRICTED)) return false;
        if (spec.hasTag(Items.TAG_NO_DEALER)) return false;
        
        // Skip station/module hulls - these are not standalone ships
        if (spec.getHints().contains(ShipTypeHints.STATION)) return false;
        if (spec.getHints().contains(ShipTypeHints.MODULE)) return false;
        
        // Skip hulls that should be hidden (usually secret/special ships)
        if (spec.getHints().contains(ShipTypeHints.HIDE_IN_CODEX)) return false;
        
        // Skip fighters - not valid for gacha
        if (spec.getHullSize() == ShipAPI.HullSize.FIGHTER) return false;
        
        // Check hull ID against disallowed prefixes
        String hullId = spec.getHullId();
        if (hullId == null) return false;
        
        for (String prefix : DISALLOWED_PREFIXES) {
            if (hullId.startsWith(prefix)) return false;
        }
        
        // Check for ships in the configured blacklist
        if (CasinoConfig.GACHA_SHIP_BLACKLIST.contains(hullId)) return false;
        if (CasinoConfig.GACHA_SHIP_BLACKLIST.contains(spec.getBaseHullId())) return false;
        
        // Skip extremely high FP ships (likely bosses/uniques)
        if (spec.getFleetPoints() > 60) return false;
        
        // Quality check: Skip very low FP ships for their size (poor quality)
        int minFP = getMinimumFleetPoints(spec.getHullSize());
        if (spec.getFleetPoints() < minFP) return false;
        
        // Only allow base hulls (not skins/variants) to avoid duplicates
        // Skins have a different base hull ID
        if (!spec.isBaseHull() && !spec.getHullId().equals(spec.getBaseHullId())) {
            // This is a skin - skip it, we'll use the base hull instead
            return false;
        }
        
        // Skip Remnant ships (automated, usually dangerous/special)
        if (spec.hasTag(Tags.SHIP_REMNANTS)) return false;
        
        // Skip ships marked as unrecoverable (usually very special)
        if (spec.hasTag(Tags.UNRECOVERABLE)) return false;
        
        // Skip Omega ships (extremely rare/special)
        if (spec.hasTag(Tags.OMEGA)) return false;
        
        // Skip Threat ships (abyssal entities)
        if (spec.hasTag(Tags.THREAT)) return false;
        
        // Skip Dweller ships (abyssal entities)
        if (spec.hasTag(Tags.DWELLER)) return false;
        
        // Skip Fragment ships (abyssal entities)
        if (spec.hasTag(Tags.FRAGMENT)) return false;
        
        return true;
    }
    
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
                 
                 // Create a fleet member with a proper variant to ensure names are populated
                 FleetMemberAPI member = createShip(spec.getHullId());
                 if (member != null) {
                     list.add(member);
                 }
             }
         }
         return list;
    }

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
    
    public void checkRotation() {
        GachaData data = getData();
        float days = Global.getSector().getClock().getElapsedDaysSince(data.lastRotationTimestamp);
        if (days >= ROTATION_PERIOD_DAYS) {
            rotatePool(data);
        }
    }
    
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
             
        // Auto Convert Check - still do this immediately with null safety
        if (s != null && data.autoConvertIds.contains(s)) {
            ShipHullSpecAPI hullSpec = Global.getSettings().getHullSpec(s);
            if (hullSpec != null) {
                int val = (int)(hullSpec.getBaseValue() / CasinoConfig.SHIP_TRADE_RATE);
                CasinoVIPManager.addStargems(val);
                return "Auto-Converted: " + hullSpec.getHullName() + " (+" + val + " Gems)";
            }
        }
             
        FleetMemberAPI m = createShip(s);
        if (m != null) {
           // Add to collected ships but don't add to fleet yet - let the handler decide
           collectedShips.add(m);
           
           String shipName = m.getShipName();
           if (shipName == null || shipName.isEmpty()) {
               shipName = m.getHullSpec().getHullName();
           }
           return shipName + " (" + m.getHullSpec().getHullName() + ")";
        } else {
           return "Error: Blueprint corrupted.";
        }
    }
    
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
        
        String shipName = member.getShipName();
        if (shipName == null || shipName.isEmpty()) {
            shipName = member.getHullSpec().getHullName();
        }
        return shipName + " (" + member.getHullSpec().getHullName() + ") " + (isFeatured ? "[FEATURED 5*]" : "[5*]");
    }
    
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
        
        String shipName = member.getShipName();
        if (shipName == null || shipName.isEmpty()) {
            shipName = member.getHullSpec().getHullName();
        }
        return shipName + " (" + member.getHullSpec().getHullName() + ") " + (isFeatured ? "[FEATURED 4*]" : "[4*]");
    }
    
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
        
        String shipName = member.getShipName();
        if (shipName == null || shipName.isEmpty()) {
            shipName = member.getHullSpec().getHullName();
        }
        return shipName + " (" + member.getHullSpec().getHullName() + ") " + (isFeatured ? "[FEATURED 5*]" : "[5*]");
    }
    
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
        
        String shipName = member.getShipName();
        if (shipName == null || shipName.isEmpty()) {
            shipName = member.getHullSpec().getHullName();
        }
        return shipName + " (" + member.getHullSpec().getHullName() + ") " + (isFeatured ? "[FEATURED 4*]" : "[4*]");
    }
    
    public FleetMemberAPI createShip(String hullId) {
        try {
            // First try to create with the standard method
            FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, hullId + "_Hull");
            
            // If ship name is null or empty, try to set a proper name
            if (member.getShipName() == null || member.getShipName().isEmpty()) {
                ShipHullSpecAPI spec = Global.getSettings().getHullSpec(hullId);
                if (spec != null) {
                    // Use hull name as the ship name if it's empty
                    member.setShipName(spec.getHullName());
                }
            }
            
            return member;
        } catch (Exception e) {
            try {
                ShipHullSpecAPI spec = Global.getSettings().getHullSpec(hullId);
                if (spec == null) throw new RuntimeException("Hull Spec not found: " + hullId);
                
                // Apply a basic loadout to the variant
                com.fs.starfarer.api.combat.ShipVariantAPI variant = Global.getSettings().getVariant(hullId + "_Hull");
                if (variant == null) {
                    // If the default variant doesn't exist, create an empty one
                    variant = Global.getSettings().createEmptyVariant(hullId + "_Hull", spec);
                }
                
                FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);
                
                // Make sure the ship has a proper name
                if (member.getShipName() == null || member.getShipName().isEmpty()) {
                    member.setShipName(spec.getHullName());
                }
                
                return member;
            } catch (Exception ex) {
                Global.getLogger(this.getClass()).error("CRITICAL: specific hullId " + hullId + " does not exist.", ex);
                // Create a fallback ship with proper name
                FleetMemberAPI fallback = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "kite_Support_Hull");
                if (fallback.getShipName() == null || fallback.getShipName().isEmpty()) {
                    fallback.setShipName("Kite");
                }
                return fallback;
            }
        }
    }

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