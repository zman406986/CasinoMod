package data.scripts.casino;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.impl.campaign.DebugFlags;
import com.fs.starfarer.api.impl.campaign.fleets.AutoDespawnScript;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.util.Random;

public class CasinoDebtScript implements EveryFrameScript {

    private static final String MEM_COLLECTOR_STATE = "$ipc_debt_collector_state";
    private static final String MEM_LAST_SPAWN_TIMESTAMP = "$ipc_debt_collector_last_spawn_timestamp";
    private static final String MEM_COLLECTOR_FLEET_ID = "$ipc_debt_collector_fleet_id";
    private static final String MEM_PENDING_START_TIME = "$ipc_debt_collector_pending_start";
    private static final String MEM_EXITED_SYSTEM_LOC = "$ipc_debt_collector_exited_system_loc";
    private static final String MEM_WARNED_90_PERCENT = "$ipc_debt_warned_90_percent";
    private static final String MEM_WARNED_SPAWNED = "$ipc_debt_warned_spawned";
    
    private static final String STATE_NONE = "none";
    private static final String STATE_PENDING = "pending";
    private static final String STATE_ACTIVE = "active";
    private static final String STATE_DEFEATED = "defeated";
    
    private static final float SPAWN_COOLDOWN_MONTHS = 1f;
    private static final float PENDING_DELAY_DAYS = 3f;
    private static final float DAYS_IN_SYSTEM_THRESHOLD = 7f;
    private static final float MAX_DIST_FROM_CORE = 30000f;
    private static final float SPAWN_DIST_FROM_SYSTEM = 3000f;
    
    private StarSystemAPI systemPlayerIsIn = null;
    private float daysInSystem = 0f;
    private boolean shouldSpawnCollector = false;
    private final long seed;

    public CasinoDebtScript() {
        seed = Misc.genRandomSeed();
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (Global.getSector().isPaused()) {
            return;
        }

        float days = Misc.getDays(amount);
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        if (playerFleet == null) return;

        updateSystemTracking(playerFleet, days);
        updateCollectorState();
    }
    
    private void updateSystemTracking(CampaignFleetAPI playerFleet, float days) {
        float distFromCore = playerFleet.getLocationInHyperspace().length();

        if (distFromCore > MAX_DIST_FROM_CORE) {
            daysInSystem = 0f;
            systemPlayerIsIn = null;
            shouldSpawnCollector = false;
            return;
        }

        if (!(playerFleet.getContainingLocation() instanceof StarSystemAPI)) {
            if (systemPlayerIsIn != null && daysInSystem >= DAYS_IN_SYSTEM_THRESHOLD) {
                float dist = Misc.getDistance(systemPlayerIsIn.getLocation(), playerFleet.getLocationInHyperspace());
                if (dist < SPAWN_DIST_FROM_SYSTEM || DebugFlags.BAR_DEBUG) {
                    Vector2f systemLoc = systemPlayerIsIn.getLocation();
                    Global.getSector().getMemoryWithoutUpdate().set(MEM_EXITED_SYSTEM_LOC, systemLoc);
                    shouldSpawnCollector = true;
                }
            }
            daysInSystem = 0f;
            systemPlayerIsIn = null;
            return;
        }

        systemPlayerIsIn = (StarSystemAPI) playerFleet.getContainingLocation();
        daysInSystem += days;
    }
    
    private void updateCollectorState() {
        String state = getCollectorState();
        int currentBalance = CasinoVIPManager.getBalance();
        int ceiling = CasinoVIPManager.getCreditCeiling();
        
        boolean debtAboveThreshold = false;
        boolean debtNearThreshold = false;
        if (currentBalance < 0 && ceiling > 0) {
            int debtAmount = -currentBalance;
            int debtAboveCeiling = Math.max(0, debtAmount - ceiling);
            float percentAboveCeiling = (float) debtAboveCeiling / ceiling;
            debtAboveThreshold = percentAboveCeiling >= CasinoConfig.DEBT_COLLECTOR_THRESHOLD_PERCENT;
            
            float thresholdPercent = CasinoConfig.DEBT_COLLECTOR_THRESHOLD_PERCENT;
            float nearThresholdPercent = thresholdPercent * 0.9f;
            debtNearThreshold = percentAboveCeiling >= nearThresholdPercent && !debtAboveThreshold;
        }
        
        checkNearThresholdWarning(debtNearThreshold);
        
        switch (state) {
            case STATE_NONE:
                handleNoneState(debtAboveThreshold);
                break;
                
            case STATE_PENDING:
                handlePendingState(debtAboveThreshold);
                break;
                
            case STATE_ACTIVE:
                handleActiveState();
                break;
                
            case STATE_DEFEATED:
                handleDefeatedState(debtAboveThreshold);
                break;
        }
    }
    
    private void checkNearThresholdWarning(boolean debtNearThreshold) {
        MemoryAPI memory = Global.getSector().getMemoryWithoutUpdate();
        boolean alreadyWarned = memory.getBoolean(MEM_WARNED_90_PERCENT);
        
        if (debtNearThreshold && !alreadyWarned) {
            int currentDebt = -CasinoVIPManager.getBalance();
            int ceiling = CasinoVIPManager.getCreditCeiling();
            
            Global.getSector().getCampaignUI().addMessage(
                Strings.format("debt.near_threshold", currentDebt, ceiling),
                Misc.getHighlightColor()
            );
            
            memory.set(MEM_WARNED_90_PERCENT, true);
        } else if (!debtNearThreshold && alreadyWarned) {
            memory.set(MEM_WARNED_90_PERCENT, false);
        }
    }
    
    private void handleNoneState(boolean debtAboveThreshold) {
        if (!debtAboveThreshold) {
            return;
        }
        
        if (canSpawnCollector()) {
            setCollectorState(STATE_PENDING);
            Global.getSector().getMemoryWithoutUpdate().set(MEM_PENDING_START_TIME, 
                    Global.getSector().getClock().getTimestamp());
            Global.getLogger(this.getClass()).info("Debt collector spawn pending - debt threshold exceeded");
            
            MemoryAPI memory = Global.getSector().getMemoryWithoutUpdate();
            boolean alreadyWarnedSpawn = memory.getBoolean(MEM_WARNED_SPAWNED);
            
            if (!alreadyWarnedSpawn) {
                Global.getSector().getCampaignUI().addMessage(
                    Strings.format("debt.dispatched", (int)PENDING_DELAY_DAYS),
                    Misc.getNegativeHighlightColor()
                );
                memory.set(MEM_WARNED_SPAWNED, true);
            }
        }
    }
    
    private void handlePendingState(boolean debtAboveThreshold) {
        MemoryAPI memory = Global.getSector().getMemoryWithoutUpdate();
        
        if (!debtAboveThreshold) {
            setCollectorState(STATE_NONE);
            memory.unset(MEM_PENDING_START_TIME);
            memory.unset(MEM_EXITED_SYSTEM_LOC);
            shouldSpawnCollector = false;
            memory.set(MEM_WARNED_90_PERCENT, false);
            memory.set(MEM_WARNED_SPAWNED, false);
            Global.getLogger(this.getClass()).info("Debt collector spawn cancelled - debt paid below threshold");
            Global.getSector().getCampaignUI().addMessage(
                Strings.get("debt.recalled"),
                Misc.getPositiveHighlightColor()
            );
            return;
        }

        Long pendingStart = null;
        if (memory.contains(MEM_PENDING_START_TIME)) {
            pendingStart = memory.getLong(MEM_PENDING_START_TIME);
        }
        
        if (pendingStart == null || pendingStart == 0) {
            pendingStart = Global.getSector().getClock().getTimestamp();
            memory.set(MEM_PENDING_START_TIME, pendingStart);
        }

        float daysSincePending = Global.getSector().getClock().getElapsedDaysSince(pendingStart);
        if (daysSincePending < PENDING_DELAY_DAYS && !DebugFlags.BAR_DEBUG) {
            return;
        }

        if (shouldSpawnCollector) {
            shouldSpawnCollector = false;

            Vector2f storedSystemLoc = (Vector2f) memory.get(MEM_EXITED_SYSTEM_LOC);
            memory.unset(MEM_EXITED_SYSTEM_LOC);

            if (spawnDebtCollector(storedSystemLoc)) {
                setCollectorState(STATE_ACTIVE);
                recordSpawnTime();
                memory.unset(MEM_PENDING_START_TIME);
            } else {
                Global.getLogger(this.getClass()).warn("Debt collector fleet spawn failed, will retry");
            }
        }
    }

    private void handleActiveState() {
        String fleetId = Global.getSector().getMemoryWithoutUpdate().getString(MEM_COLLECTOR_FLEET_ID);
        if (fleetId != null) {
            CampaignFleetAPI fleet = findFleetById(fleetId);
            if (fleet != null && fleet.isAlive() && !fleet.isDespawning()) {
                return;
            }
        }
        
        setCollectorState(STATE_DEFEATED);
        Global.getSector().getMemoryWithoutUpdate().unset(MEM_COLLECTOR_FLEET_ID);
        Global.getSector().getMemoryWithoutUpdate().set(MEM_WARNED_90_PERCENT, false);
        Global.getSector().getMemoryWithoutUpdate().set(MEM_WARNED_SPAWNED, false);
        Global.getLogger(this.getClass()).info("Debt collector fleet defeated or despawned - debt continues");
        
        Global.getSector().getCampaignUI().addMessage(
            Strings.get("debt.defeated"),
            Misc.getHighlightColor()
        );
    }
    
    private CampaignFleetAPI findFleetById(String fleetId) {
        for (CampaignFleetAPI fleet : Global.getSector().getHyperspace().getFleets()) {
            if (fleetId.equals(fleet.getId())) {
                return fleet;
            }
        }
        
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            for (CampaignFleetAPI fleet : system.getFleets()) {
                if (fleetId.equals(fleet.getId())) {
                    return fleet;
                }
            }
        }
        
        return null;
    }
    
    private void handleDefeatedState(boolean debtAboveThreshold) {
        MemoryAPI memory = Global.getSector().getMemoryWithoutUpdate();
        
        if (!debtAboveThreshold) {
            setCollectorState(STATE_NONE);
            memory.set(MEM_WARNED_90_PERCENT, false);
            memory.set(MEM_WARNED_SPAWNED, false);
            Global.getLogger(this.getClass()).info("Debt resolved - collector system reset");
            return;
        }
        
        if (canSpawnCollector()) {
            setCollectorState(STATE_NONE);
            Global.getLogger(this.getClass()).info("Debt collector cooldown expired - resetting for new spawn attempt");
        }
    }
    
    private boolean canSpawnCollector() {
        MemoryAPI memory = Global.getSector().getMemoryWithoutUpdate();
        
        if (!memory.contains(MEM_LAST_SPAWN_TIMESTAMP)) {
            return true;
        }
        
        long lastSpawnTimestamp = memory.getLong(MEM_LAST_SPAWN_TIMESTAMP);
        if (lastSpawnTimestamp == 0) {
            return true;
        }
        
        float daysSinceLastSpawn = Global.getSector().getClock().getElapsedDaysSince(lastSpawnTimestamp);
        float cooldownDays = SPAWN_COOLDOWN_MONTHS * 30f;
        
        return daysSinceLastSpawn >= cooldownDays;
    }
    
    private void recordSpawnTime() {
        Global.getSector().getMemoryWithoutUpdate().set(MEM_LAST_SPAWN_TIMESTAMP, 
                Global.getSector().getClock().getTimestamp());
    }
    
    private String getCollectorState() {
        String state = Global.getSector().getMemoryWithoutUpdate().getString(MEM_COLLECTOR_STATE);
        if (state == null || state.isEmpty()) {
            state = STATE_NONE;
        }
        return state;
    }
    
    private void setCollectorState(String state) {
        Global.getSector().getMemoryWithoutUpdate().set(MEM_COLLECTOR_STATE, state);
    }
    
    private boolean spawnDebtCollector(Vector2f spawnLocation) {
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        if (playerFleet == null) {
            return false;
        }
        
        CampaignFleetAPI debtCollectorFleet = createDebtCollectorFleet();
        if (debtCollectorFleet == null) {
            return false;
        }
        
        Global.getSector().getHyperspace().addEntity(debtCollectorFleet);
        
        Vector2f spawnLoc;
        if (spawnLocation != null) {
            spawnLoc = Misc.getPointAtRadius(spawnLocation, 500f);
        } else {
            spawnLoc = Misc.getPointAtRadius(playerFleet.getLocationInHyperspace(), 500f);
        }
        debtCollectorFleet.setLocation(spawnLoc.x, spawnLoc.y);
        
        debtCollectorFleet.getAI().addAssignmentAtStart(FleetAssignment.INTERCEPT, playerFleet, 1000f, null);
        
        Misc.giveStandardReturnToSourceAssignments(debtCollectorFleet, false);
        
        debtCollectorFleet.getMemoryWithoutUpdate().set("$ipc_debt_collector", true);
        
        Global.getSector().getMemoryWithoutUpdate().set(MEM_COLLECTOR_FLEET_ID, debtCollectorFleet.getId());
        
        AbilityPlugin eb = debtCollectorFleet.getAbility(Abilities.EMERGENCY_BURN);
        if (eb != null) eb.activate();
        
        Global.getSector().getCampaignUI().addMessage(
            Strings.get("debt.arrived"),
            Misc.getNegativeHighlightColor()
        );
        
        Global.getLogger(this.getClass()).info("Debt collector fleet spawned successfully");
        return true;
    }

    private CampaignFleetAPI createDebtCollectorFleet() {
        FleetParamsV3 params = createDebtCollectorFleetParams();
        
        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet == null || fleet.isEmpty()) {
            return null;
        }
        
        fleet.setFaction(Factions.INDEPENDENT, true);
        Misc.makeLowRepImpact(fleet, "ipc_debt");
        
        fleet.addScript(new AutoDespawnScript(fleet));
        
        MemoryAPI memory = fleet.getMemoryWithoutUpdate();
        memory.set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true);
        
        return fleet;
    }
    
    private FleetParamsV3 createDebtCollectorFleetParams() {
        float fleetPoints = 200f;
        
        FleetParamsV3 params = new FleetParamsV3(
                null,
                Global.getSector().getPlayerFleet().getLocationInHyperspace(),
                Factions.TRITACHYON,
                1f,
                FleetTypes.MERC_BOUNTY_HUNTER,
                fleetPoints,
                0f, 
                fleetPoints * 0.1f,
                0f,
                0f,
                0f,
                0f
        );
        
        params.officerNumberBonus = 4;
        params.officerLevelBonus = 3;
        
        params.doctrineOverride = Global.getSector().getFaction(Factions.TRITACHYON).getDoctrine().clone();
        params.doctrineOverride.setWarships(3);
        params.doctrineOverride.setPhaseShips(3);
        params.doctrineOverride.setCarriers(1);
        
        params.random = new Random(seed);
        
        return params;
    }
    
    public static boolean isCollectorActive() {
        String state = Global.getSector().getMemoryWithoutUpdate().getString(MEM_COLLECTOR_STATE);
        return STATE_ACTIVE.equals(state);
    }
    
    public static boolean isCollectorPending() {
        String state = Global.getSector().getMemoryWithoutUpdate().getString(MEM_COLLECTOR_STATE);
        return STATE_PENDING.equals(state);
    }
    
    @SuppressWarnings("unused")
    public static void resetCollectorSystem() {
        Global.getSector().getMemoryWithoutUpdate().set(MEM_COLLECTOR_STATE, STATE_NONE);
        Global.getSector().getMemoryWithoutUpdate().unset(MEM_COLLECTOR_FLEET_ID);
        Global.getSector().getMemoryWithoutUpdate().unset(MEM_PENDING_START_TIME);
        Global.getLogger(CasinoDebtScript.class).info("Debt collector system manually reset");
    }
    
    public static void initializeSystem() {
        MemoryAPI memory = Global.getSector().getMemoryWithoutUpdate();
        
        if (!memory.contains(MEM_COLLECTOR_STATE)) {
            memory.set(MEM_COLLECTOR_STATE, STATE_NONE);
        }
        
        if (!memory.contains(MEM_LAST_SPAWN_TIMESTAMP)) {
            memory.set(MEM_LAST_SPAWN_TIMESTAMP, 0L);
        }
        
        if (!memory.contains(MEM_WARNED_90_PERCENT)) {
            memory.set(MEM_WARNED_90_PERCENT, false);
        }
        
        if (!memory.contains(MEM_WARNED_SPAWNED)) {
            memory.set(MEM_WARNED_SPAWNED, false);
        }
        
        Global.getLogger(CasinoDebtScript.class).info("Debt collector system initialized");
    }
}
