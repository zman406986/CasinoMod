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
    
    public static class GachaData {
        public long lastRotationTimestamp;
        public int pity5;
        public int pity4;
        public boolean guaranteedFeatured5;
        public boolean guaranteedFeatured4;
        
        public String featuredCapital;
        public List<String> featuredCruisers = new ArrayList<>();
        
        public List<String> poolCapitals = new ArrayList<>();
        public List<String> poolCruisers = new ArrayList<>();
        public List<String> poolDestroyers = new ArrayList<>();
        public List<String> poolFrigates = new ArrayList<>();
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
        "tem_",
        "vayra_",
        "swp_",
        "apex_",
        "tahlan_",
        "uw_",
        "rat_",
        "dcp_",
        "mso_",
        "xiv_"
    ));
    
    private int getMinimumFleetPoints(ShipAPI.HullSize size) {
        return switch (size) {
            case CAPITAL_SHIP -> 20;
            case CRUISER -> 14;
            case DESTROYER -> 8;
            case FRIGATE -> 4;
            default -> 5;
        };
    }
    
    public boolean isShipAllowed(ShipHullSpecAPI spec) {
        if (spec == null) return false;
        
        if (spec.isDHull()) return false;
        
        if (spec.hasTag(Tags.NO_SELL)) return false;
        if (spec.hasTag(Tags.RESTRICTED)) return false;
        if (spec.hasTag(Items.TAG_NO_DEALER)) return false;
        
        if (spec.getHints().contains(ShipTypeHints.STATION)) return false;
        if (spec.getHints().contains(ShipTypeHints.MODULE)) return false;
        if (spec.getHints().contains(ShipTypeHints.UNBOARDABLE)) return false;
        if (spec.getHints().contains(ShipTypeHints.HIDE_IN_CODEX)) return false;
        
        if (spec.getHullSize() == ShipAPI.HullSize.FIGHTER) return false;
        
        String hullId = spec.getHullId();
        if (hullId == null) return false;
        
        for (String prefix : DISALLOWED_PREFIXES) {
            if (hullId.startsWith(prefix)) return false;
        }

        if (spec.getFleetPoints() > 60) return false;
        
        int minFP = getMinimumFleetPoints(spec.getHullSize());
        if (spec.getFleetPoints() < minFP) return false;
        
        if (!spec.isBaseHull() && !spec.getHullId().equals(spec.getBaseHullId())) {
            return false;
        }

        if (spec.hasTag("no_dealer")) return false;
        if (spec.hasTag("omega")) return false;
        if (spec.hasTag("derelict")) return false;
        if (spec.hasTag("remnant")) return false;
        if (spec.hasTag("no_sim")) return false;
        if (spec.hasTag("dweller")) return false;
        if (spec.hasTag("restricted")) return false;
        if (spec.hasTag("threat_swarm_ai")) return false;
        if (spec.hasTag("swarm_fighter")) return false;
        
        return true;
    }
    
    private List<String> getAllowedHullIdsBySize(ShipAPI.HullSize size) {
        List<String> hullIds = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        
        for (ShipHullSpecAPI spec : Global.getSettings().getAllShipHullSpecs()) {
            if (!isShipAllowed(spec)) continue;
            if (spec.getHullSize() != size) continue;
            
            String hullId = spec.getHullId();
            if (!seen.contains(hullId)) {
                seen.add(hullId);
                hullIds.add(hullId);
            }
        }
        
        return hullIds;
    }
    
    public List<FleetMemberAPI> getPotentialDrops() {
        List<FleetMemberAPI> list = new ArrayList<>();
        GachaData data = getData();
        
        for (String hullId : data.poolCapitals) {
            FleetMemberAPI member = createShip(hullId);
            if (member != null) list.add(member);
        }
        for (String hullId : data.poolCruisers) {
            FleetMemberAPI member = createShip(hullId);
            if (member != null) list.add(member);
        }
        for (String hullId : data.poolDestroyers) {
            FleetMemberAPI member = createShip(hullId);
            if (member != null) list.add(member);
        }
        for (String hullId : data.poolFrigates) {
            FleetMemberAPI member = createShip(hullId);
            if (member != null) list.add(member);
        }
        
        return list;
    }

    public void rotatePool(GachaData data) {
        data.lastRotationTimestamp = Global.getSector().getClock().getTimestamp();
        
        List<String> allCapitals = getAllowedHullIdsBySize(ShipAPI.HullSize.CAPITAL_SHIP);
        List<String> allCruisers = getAllowedHullIdsBySize(ShipAPI.HullSize.CRUISER);
        List<String> allDestroyers = getAllowedHullIdsBySize(ShipAPI.HullSize.DESTROYER);
        List<String> allFrigates = getAllowedHullIdsBySize(ShipAPI.HullSize.FRIGATE);
        
        Collections.shuffle(allCapitals, random);
        Collections.shuffle(allCruisers, random);
        Collections.shuffle(allDestroyers, random);
        Collections.shuffle(allFrigates, random);
        
        data.poolCapitals.clear();
        data.poolCruisers.clear();
        data.poolDestroyers.clear();
        data.poolFrigates.clear();
        
        for (int i = 0; i < CasinoConfig.GACHA_POOL_CAPITALS && i < allCapitals.size(); i++) {
            data.poolCapitals.add(allCapitals.get(i));
        }
        for (int i = 0; i < CasinoConfig.GACHA_POOL_CRUISERS && i < allCruisers.size(); i++) {
            data.poolCruisers.add(allCruisers.get(i));
        }
        for (int i = 0; i < CasinoConfig.GACHA_POOL_DESTROYERS && i < allDestroyers.size(); i++) {
            data.poolDestroyers.add(allDestroyers.get(i));
        }
        for (int i = 0; i < CasinoConfig.GACHA_POOL_FRIGATES && i < allFrigates.size(); i++) {
            data.poolFrigates.add(allFrigates.get(i));
        }
        
        if (!data.poolCapitals.isEmpty()) {
            data.featuredCapital = data.poolCapitals.get(random.nextInt(data.poolCapitals.size()));
        } else if (!allCapitals.isEmpty()) {
            data.featuredCapital = allCapitals.get(random.nextInt(allCapitals.size()));
        }
        
        data.featuredCruisers.clear();
        Collections.shuffle(data.poolCruisers, random);
        for (int i = 0; i < 3 && i < data.poolCruisers.size(); i++) {
            data.featuredCruisers.add(data.poolCruisers.get(i));
        }
        
        Global.getLogger(this.getClass()).info("Gacha pool rotated: " +
            data.poolCapitals.size() + " capitals, " +
            data.poolCruisers.size() + " cruisers, " +
            data.poolDestroyers.size() + " destroyers, " +
            data.poolFrigates.size() + " frigates");
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
        
        float currentRate4 = CasinoConfig.PROB_4_STAR;
        if (data.pity4 >= CasinoConfig.PITY_HARD_4) currentRate4 = 10.0f;
        
        float roll4 = random.nextFloat();
        
        if (roll4 < currentRate4) {
             data.pity4 = 0;
             return handle4StarDetailed(data, collectedShips);
        }
        
        String s = getRandomHullFromPool(random.nextBoolean() ? ShipAPI.HullSize.DESTROYER : ShipAPI.HullSize.FRIGATE);
             
        FleetMemberAPI m = createShip(s);
        if (m != null) {
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
                 resultId = getRandomHullFromPool(ShipAPI.HullSize.CAPITAL_SHIP);
                 if (resultId == null) {
                     resultId = data.featuredCapital;
                     isFeatured = true;
                 } else {
                     data.guaranteedFeatured5 = true;
                 }
             }
        }
        
        if (resultId == null) {
            resultId = getFallbackHullId(ShipAPI.HullSize.CAPITAL_SHIP);
        }
        
        FleetMemberAPI member = createShip(resultId);
        if (member != null) {
            collectedShips.add(member);
        }
        
        if (member == null) {
            return "Error: Could not create ship.";
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
            if (!data.featuredCruisers.isEmpty()) {
                resultId = data.featuredCruisers.get(random.nextInt(data.featuredCruisers.size()));
            } else {
                resultId = getRandomHullFromPool(ShipAPI.HullSize.CRUISER);
            }
            data.guaranteedFeatured4 = false;
            isFeatured = true;
        } else {
            if (random.nextBoolean() && !data.featuredCruisers.isEmpty()) {
                resultId = data.featuredCruisers.get(random.nextInt(data.featuredCruisers.size()));
                isFeatured = true;
            } else {
                resultId = getRandomHullFromPool(ShipAPI.HullSize.CRUISER);
                if (resultId != null) {
                    data.guaranteedFeatured4 = true;
                } else if (!data.featuredCruisers.isEmpty()) {
                    resultId = data.featuredCruisers.get(random.nextInt(data.featuredCruisers.size()));
                    isFeatured = true;
                }
            }
        }
        
        if (resultId == null) {
            resultId = getFallbackHullId(ShipAPI.HullSize.CRUISER);
        }
        
        FleetMemberAPI member = createShip(resultId);
        if (member != null) {
            collectedShips.add(member);
        }
        
        if (member == null) {
            return "Error: Could not create ship.";
        }
        
        String shipName = member.getShipName();
        if (shipName == null || shipName.isEmpty()) {
            shipName = member.getHullSpec().getHullName();
        }
        return shipName + " (" + member.getHullSpec().getHullName() + ") " + (isFeatured ? "[FEATURED 4*]" : "[4*]");
    }
    
    public FleetMemberAPI createShip(String hullId) {
        try {
            FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, hullId + "_Hull");
            
            if (member.getShipName() == null || member.getShipName().isEmpty()) {
                ShipHullSpecAPI spec = Global.getSettings().getHullSpec(hullId);
                if (spec != null) {
                    member.setShipName(spec.getHullName());
                }
            }
            
            return member;
        } catch (Exception e) {
            try {
                ShipHullSpecAPI spec = Global.getSettings().getHullSpec(hullId);
                if (spec == null) throw new RuntimeException("Hull Spec not found: " + hullId);
                
                com.fs.starfarer.api.combat.ShipVariantAPI variant = Global.getSettings().getVariant(hullId + "_Hull");
                if (variant == null) {
                    variant = Global.getSettings().createEmptyVariant(hullId + "_Hull", spec);
                }
                
                FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);
                
                if (member.getShipName() == null || member.getShipName().isEmpty()) {
                    member.setShipName(spec.getHullName());
                }
                
                return member;
            } catch (Exception ex) {
                Global.getLogger(this.getClass()).error("CRITICAL: specific hullId " + hullId + " does not exist.", ex);
                FleetMemberAPI fallback = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "kite_Support_Hull");
                if (fallback.getShipName() == null || fallback.getShipName().isEmpty()) {
                    fallback.setShipName("Kite");
                }
                return fallback;
            }
        }
    }

    private String getRandomHullFromPool(ShipAPI.HullSize size) {
        List<String> pool;
        switch (size) {
            case CAPITAL_SHIP -> pool = getData().poolCapitals;
            case CRUISER -> pool = getData().poolCruisers;
            case DESTROYER -> pool = getData().poolDestroyers;
            case FRIGATE -> pool = getData().poolFrigates;
            default -> pool = new ArrayList<>();
        }
        
        if (pool.isEmpty()) {
            return getFallbackHullId(size);
        }
        
        return pool.get(random.nextInt(pool.size()));
    }
    
    private String getFallbackHullId(ShipAPI.HullSize size) {
        return switch (size) {
            case CAPITAL_SHIP -> "onslaught";
            case CRUISER -> "eagle";
            case DESTROYER -> "hammerhead";
            case FRIGATE -> "lasher";
            default -> "lasher";
        };
    }
    
    public String getRandomStandardHull(ShipAPI.HullSize size, String excludeId) {
        List<String> list = new ArrayList<>();
        for (ShipHullSpecAPI spec : Global.getSettings().getAllShipHullSpecs()) {
            if (spec.getHullSize() == size && isShipAllowed(spec) && !spec.getHullId().equals(excludeId)) {
                list.add(spec.getHullId());
            }
        }
        if (list.isEmpty()) {
            return getFallbackHullId(size);
        }
        return list.get(random.nextInt(list.size()));
    }
}