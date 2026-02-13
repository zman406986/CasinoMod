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

/**
 * Manages debt collector (bounty hunter) spawning for casino debt.
 * 
 * Based on TriTachLoanIncentiveScript - follows base game patterns for bounty hunter spawning.
 * 
 * Key behaviors:
 * - Collectors spawn when debt exceeds threshold (configurable, default -10000)
 * - Spawn has a 1-month cooldown between attempts
 * - If debt drops below threshold before spawn, the spawn is cancelled
 * - Collectors spawn when player exits a star system after being in it for 7+ days
 * - Defeating a collector does NOT clear debt - debt continues to accrue interest
 * - Only one collector can be active at a time
 */
public class CasinoDebtScript implements EveryFrameScript {

    // Memory keys for persistence
    private static final String MEM_COLLECTOR_STATE = "$ipc_debt_collector_state";
    private static final String MEM_LAST_SPAWN_TIMESTAMP = "$ipc_debt_collector_last_spawn_timestamp";
    private static final String MEM_COLLECTOR_FLEET_ID = "$ipc_debt_collector_fleet_id";
    private static final String MEM_PENDING_START_TIME = "$ipc_debt_collector_pending_start";
    private static final String MEM_EXITED_SYSTEM_LOC = "$ipc_debt_collector_exited_system_loc";
    private static final String MEM_WARNED_90_PERCENT = "$ipc_debt_warned_90_percent";
    private static final String MEM_WARNED_SPAWNED = "$ipc_debt_warned_spawned";
    
    // State values
    private static final String STATE_NONE = "none";
    private static final String STATE_PENDING = "pending"; // Waiting for cooldown/conditions
    private static final String STATE_ACTIVE = "active"; // Fleet spawned
    private static final String STATE_DEFEATED = "defeated"; // Fleet defeated
    
    // Timing constants
    private static final float SPAWN_COOLDOWN_MONTHS = 1f; // 1 month between spawns
    private static final float PENDING_DELAY_DAYS = 3f; // 3 day delay before spawn after threshold crossed
    private static final float DAYS_IN_SYSTEM_THRESHOLD = 7f; // Must be in system for 7 days
    private static final float MAX_DIST_FROM_CORE = 30000f; // Must be within 30k of core
    private static final float SPAWN_DIST_FROM_SYSTEM = 3000f; // Spawn within 3k of exited system
    
    // Instance variables (not persisted, recalculated)
    private StarSystemAPI systemPlayerIsIn = null;
    private float daysInSystem = 0f;
    private boolean shouldSpawnCollector = false; // Flag set when spawn conditions are met
    private final long seed;

    public CasinoDebtScript() {
        seed = Misc.genRandomSeed();
    }

    @Override
    public boolean isDone() {
        return false; // This script runs for the entire game
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

        // Update system tracking for spawn conditions
        updateSystemTracking(playerFleet, days);

        // Update collector state machine
        updateCollectorState(days, playerFleet);
    }
    
    /**
     * Track player location for spawn conditions
     * Following TriTachLoanIncentiveScript pattern
     */
    private void updateSystemTracking(CampaignFleetAPI playerFleet, float days) {
        float distFromCore = playerFleet.getLocationInHyperspace().length();

        // If too far from core, reset tracking
        if (distFromCore > MAX_DIST_FROM_CORE) {
            daysInSystem = 0f;
            systemPlayerIsIn = null;
            shouldSpawnCollector = false;
            return;
        }

        // Check if player is in hyperspace (exiting a system)
        if (!(playerFleet.getContainingLocation() instanceof StarSystemAPI)) {
            // Player is in hyperspace - check spawn conditions BEFORE clearing tracking
            // This follows the TriTachLoanIncentiveScript pattern
            if (systemPlayerIsIn != null && daysInSystem >= DAYS_IN_SYSTEM_THRESHOLD) {
                float dist = Misc.getDistance(systemPlayerIsIn.getLocation(), playerFleet.getLocationInHyperspace());
                if (dist < SPAWN_DIST_FROM_SYSTEM || DebugFlags.BAR_DEBUG) {
                    // Spawn conditions met! Store the system location BEFORE clearing tracking
                    Vector2f systemLoc = systemPlayerIsIn.getLocation();
                    Global.getSector().getMemoryWithoutUpdate().set(MEM_EXITED_SYSTEM_LOC, systemLoc);
                    shouldSpawnCollector = true;
                }
            }
            // NOW clear tracking after checking conditions
            daysInSystem = 0f;
            systemPlayerIsIn = null;
            return;
        }

        // Player is in a system - track time
        systemPlayerIsIn = (StarSystemAPI) playerFleet.getContainingLocation();
        daysInSystem += days;
    }
    
    /**
     * Main state machine for debt collector management
     */
    private void updateCollectorState(float days, CampaignFleetAPI playerFleet) {
        String state = getCollectorState();
        int currentBalance = CasinoVIPManager.getBalance();
        int ceiling = CasinoVIPManager.getCreditCeiling();
        
        // Calculate how much debt exceeds the ceiling as a percentage
        // Debt is negative balance, so -currentBalance is the debt amount
        boolean debtAboveThreshold = false;
        boolean debtNearThreshold = false;
        if (currentBalance < 0 && ceiling > 0) {
            int debtAmount = -currentBalance; // Convert negative balance to positive debt
            int debtAboveCeiling = Math.max(0, debtAmount - ceiling); // How much above ceiling
            float percentAboveCeiling = (float) debtAboveCeiling / ceiling;
            debtAboveThreshold = percentAboveCeiling >= CasinoConfig.DEBT_COLLECTOR_THRESHOLD_PERCENT;
            
            // Check if debt is at 90% of threshold (warning zone)
            float thresholdPercent = CasinoConfig.DEBT_COLLECTOR_THRESHOLD_PERCENT;
            float nearThresholdPercent = thresholdPercent * 0.9f;
            debtNearThreshold = percentAboveCeiling >= nearThresholdPercent && !debtAboveThreshold;
        }
        
        // Warn player if debt is approaching threshold
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
    
    /**
     * Warns player when debt is at 90% of the collector spawn threshold.
     * Only warns once per cycle (resets when collector is defeated/despawned).
     * This is a critical warning - always shown regardless of notification preference.
     */
    private void checkNearThresholdWarning(boolean debtNearThreshold) {
        MemoryAPI memory = Global.getSector().getMemoryWithoutUpdate();
        boolean alreadyWarned = memory.getBoolean(MEM_WARNED_90_PERCENT);
        
        if (debtNearThreshold && !alreadyWarned) {
            int currentDebt = -CasinoVIPManager.getBalance();
            int ceiling = CasinoVIPManager.getCreditCeiling();
            
            Global.getSector().getCampaignUI().addMessage(
                "WARNING: Your debt of " + currentDebt + " Stargems is at 90% of the collection threshold! " +
                "Credit ceiling: " + ceiling + ". Reduce your debt to avoid Corporate Reconciliation Teams.",
                Misc.getHighlightColor()
            );
            
            memory.set(MEM_WARNED_90_PERCENT, true);
        } else if (!debtNearThreshold && alreadyWarned) {
            // Reset warning flag if debt drops below 90% threshold
            memory.set(MEM_WARNED_90_PERCENT, false);
        }
    }
    
    /**
     * STATE_NONE: No collector activity
     * Transition to PENDING if debt exceeds threshold and cooldown has passed
     */
    private void handleNoneState(boolean debtAboveThreshold) {
        if (!debtAboveThreshold) {
            return;
        }
        
        if (canSpawnCollector()) {
            setCollectorState(STATE_PENDING);
            Global.getSector().getMemoryWithoutUpdate().set(MEM_PENDING_START_TIME, 
                    Long.valueOf(Global.getSector().getClock().getTimestamp()));
            Global.getLogger(this.getClass()).info("Debt collector spawn pending - debt threshold exceeded");
            
            // Show spawn warning only once per cycle - critical alert, always shown
            MemoryAPI memory = Global.getSector().getMemoryWithoutUpdate();
            boolean alreadyWarnedSpawn = memory.getBoolean(MEM_WARNED_SPAWNED);
            
            if (!alreadyWarnedSpawn) {
                Global.getSector().getCampaignUI().addMessage(
                    "A Corporate Reconciliation Team has been dispatched to collect your debt. " +
                    "They will arrive in " + (int)PENDING_DELAY_DAYS + " days.",
                    Misc.getNegativeHighlightColor()
                );
                memory.set(MEM_WARNED_SPAWNED, true);
            }
        }
    }
    
    /**
     * STATE_PENDING: Collector spawn is queued but not yet spawned
     * - Wait for delay and location conditions
     * - Cancel if debt drops below threshold
     * - Spawn when conditions are met
     */
    private void handlePendingState(boolean debtAboveThreshold) {
        MemoryAPI memory = Global.getSector().getMemoryWithoutUpdate();
        
        if (!debtAboveThreshold) {
            setCollectorState(STATE_NONE);
            memory.unset(MEM_PENDING_START_TIME);
            memory.unset(MEM_EXITED_SYSTEM_LOC);
            shouldSpawnCollector = false;
            // Reset warning flags when collector is recalled
            memory.set(MEM_WARNED_90_PERCENT, false);
            memory.set(MEM_WARNED_SPAWNED, false);
            Global.getLogger(this.getClass()).info("Debt collector spawn cancelled - debt paid below threshold");
            Global.getSector().getCampaignUI().addMessage(
                "The Corporate Reconciliation Team has been recalled - your debt is below the collection threshold.",
                Misc.getPositiveHighlightColor()
            );
            return;
        }

        Long pendingStart = null;
        if (memory.contains(MEM_PENDING_START_TIME)) {
            pendingStart = memory.getLong(MEM_PENDING_START_TIME);
        }
        
        if (pendingStart == null || pendingStart == 0) {
            pendingStart = Long.valueOf(Global.getSector().getClock().getTimestamp());
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

    /**
     * STATE_ACTIVE: Collector fleet is spawned and active
     * - Check if fleet is still alive by looking it up by ID
     * - If destroyed/defeated, transition to DEFEATED state
     * - Debt continues to accrue regardless
     */
    private void handleActiveState() {
        // Look up the fleet by ID (survives save/load)
        String fleetId = Global.getSector().getMemoryWithoutUpdate().getString(MEM_COLLECTOR_FLEET_ID);
        if (fleetId != null) {
            CampaignFleetAPI fleet = findFleetById(fleetId);
            if (fleet != null && fleet.isAlive() && !fleet.isDespawning()) {
                // Fleet still active, do nothing
                return;
            }
        }
        
        // Fleet is no longer active (defeated or despawned)
        // Note: We do NOT clear debt here - debt continues until paid
        setCollectorState(STATE_DEFEATED);
        Global.getSector().getMemoryWithoutUpdate().unset(MEM_COLLECTOR_FLEET_ID);
        // Reset warning flags for next cycle
        Global.getSector().getMemoryWithoutUpdate().set(MEM_WARNED_90_PERCENT, false);
        Global.getSector().getMemoryWithoutUpdate().set(MEM_WARNED_SPAWNED, false);
        Global.getLogger(this.getClass()).info("Debt collector fleet defeated or despawned - debt continues");
        
        // Notify player
        Global.getSector().getCampaignUI().addMessage(
            "The Corporate Reconciliation Team has been defeated. However, your debt remains and continues to accrue interest.",
            Misc.getHighlightColor()
        );
    }
    
    /**
     * Find a fleet by its ID - searches all locations (hyperspace and star systems)
     * Following the pattern from base game fleet tracking
     */
    private CampaignFleetAPI findFleetById(String fleetId) {
        // Check hyperspace first
        for (CampaignFleetAPI fleet : Global.getSector().getHyperspace().getFleets()) {
            if (fleetId.equals(fleet.getId())) {
                return fleet;
            }
        }
        
        // Check all star systems
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            for (CampaignFleetAPI fleet : system.getFleets()) {
                if (fleetId.equals(fleet.getId())) {
                    return fleet;
                }
            }
        }
        
        return null;
    }
    
    /**
     * STATE_DEFEATED: Collector was defeated
     * - Stay in this state until debt drops below threshold OR cooldown expires
     * - Once cooldown expires with debt still above threshold, reset to NONE for new spawn attempt
     * - This allows monthly spawn checks for players with persistent high debt
     */
    private void handleDefeatedState(boolean debtAboveThreshold) {
        MemoryAPI memory = Global.getSector().getMemoryWithoutUpdate();
        
        if (!debtAboveThreshold) {
            // Debt has been paid off, reset system
            setCollectorState(STATE_NONE);
            memory.set(MEM_WARNED_90_PERCENT, false);
            memory.set(MEM_WARNED_SPAWNED, false);
            Global.getLogger(this.getClass()).info("Debt resolved - collector system reset");
            return;
        }
        
        // Debt is still above threshold - check if cooldown has expired for next spawn attempt
        if (canSpawnCollector()) {
            // Cooldown expired and debt still high - reset to NONE to allow new spawn cycle
            setCollectorState(STATE_NONE);
            Global.getLogger(this.getClass()).info("Debt collector cooldown expired - resetting for new spawn attempt");
        }
        // If cooldown hasn't expired, stay in DEFEATED state
    }
    
    /**
     * Check if enough time has passed to spawn a new collector
     * Uses proper timestamp-based calculation following CasinoVIPManager pattern
     */
    private boolean canSpawnCollector() {
        MemoryAPI memory = Global.getSector().getMemoryWithoutUpdate();
        
        if (!memory.contains(MEM_LAST_SPAWN_TIMESTAMP)) {
            return true;
        }
        
        Long lastSpawnTimestamp = memory.getLong(MEM_LAST_SPAWN_TIMESTAMP);
        if (lastSpawnTimestamp == null || lastSpawnTimestamp == 0) {
            return true;
        }
        
        float daysSinceLastSpawn = Global.getSector().getClock().getElapsedDaysSince(lastSpawnTimestamp);
        float cooldownDays = SPAWN_COOLDOWN_MONTHS * 30f;
        
        return daysSinceLastSpawn >= cooldownDays;
    }
    
    private void recordSpawnTime() {
        Global.getSector().getMemoryWithoutUpdate().set(MEM_LAST_SPAWN_TIMESTAMP, 
                Long.valueOf(Global.getSector().getClock().getTimestamp()));
    }
    
    /**
     * Get current collector state from memory
     */
    private String getCollectorState() {
        String state = Global.getSector().getMemoryWithoutUpdate().getString(MEM_COLLECTOR_STATE);
        if (state == null || state.isEmpty()) {
            state = STATE_NONE;
        }
        return state;
    }
    
    /**
     * Set collector state in memory
     */
    private void setCollectorState(String state) {
        Global.getSector().getMemoryWithoutUpdate().set(MEM_COLLECTOR_STATE, state);
    }
    
    /**
     * Spawn the debt collector fleet
     * @param spawnLocation The location to spawn near (from the system player exited)
     * @return true if spawn was successful
     */
    private boolean spawnDebtCollector(Vector2f spawnLocation) {
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        if (playerFleet == null) {
            return false;
        }
        
        // Create and send the debt collector fleet
        CampaignFleetAPI debtCollectorFleet = createDebtCollectorFleet();
        if (debtCollectorFleet == null) {
            return false;
        }
        
        // Add the fleet to hyperspace
        Global.getSector().getHyperspace().addEntity(debtCollectorFleet);
        
        // Position the fleet near the spawn location (following TriTachLoanIncentiveScript pattern)
        Vector2f spawnLoc;
        if (spawnLocation != null) {
            // Spawn near the stored system location
            spawnLoc = Misc.getPointAtRadius(spawnLocation, 500f);
        } else {
            // Fallback: spawn near player
            spawnLoc = Misc.getPointAtRadius(playerFleet.getLocationInHyperspace(), 500f);
        }
        debtCollectorFleet.setLocation(spawnLoc.x, spawnLoc.y);
        
        // Set the fleet to intercept the player
        debtCollectorFleet.getAI().addAssignmentAtStart(FleetAssignment.INTERCEPT, playerFleet, 1000f, null);
        
        // Give standard return to source assignments
        Misc.giveStandardReturnToSourceAssignments(debtCollectorFleet, false);
        
        // Set memory flags to identify this as a debt collector fleet
        debtCollectorFleet.getMemoryWithoutUpdate().set("$ipc_debt_collector", true);
        
        // Track the fleet by ID (survives save/load)
        Global.getSector().getMemoryWithoutUpdate().set(MEM_COLLECTOR_FLEET_ID, debtCollectorFleet.getId());
        
        // Activate emergency burn (following TriTachLoanIncentiveScript)
        AbilityPlugin eb = debtCollectorFleet.getAbility(Abilities.EMERGENCY_BURN);
        if (eb != null) eb.activate();
        
        // Send notification to player
        Global.getSector().getCampaignUI().addMessage(
            "A Corporate Reconciliation Team has arrived to collect your debt!",
            Misc.getNegativeHighlightColor()
        );
        
        Global.getLogger(this.getClass()).info("Debt collector fleet spawned successfully");
        return true;
    }

    private CampaignFleetAPI createDebtCollectorFleet() {
        // Create a bounty hunter style fleet for collecting debt
        // Following TriTachLoanIncentiveScript pattern
        FleetParamsV3 params = createDebtCollectorFleetParams();
        
        // Create the fleet using the factory
        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet == null || fleet.isEmpty()) {
            return null;
        }
        
        // Set the fleet to independent faction so it can be hostile (following TriTach pattern)
        fleet.setFaction(Factions.INDEPENDENT, true);
        Misc.makeLowRepImpact(fleet, "ipc_debt");
        
        // Make the fleet despawn after some time if not engaged
        fleet.addScript(new AutoDespawnScript(fleet));
        
        // Set memory flags to make the fleet hostile
        MemoryAPI memory = fleet.getMemoryWithoutUpdate();
        memory.set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true);
        
        return fleet;
    }
    
    /**
     * Create fleet parameters for debt collector fleet
     */
    private FleetParamsV3 createDebtCollectorFleetParams() {
        float fleetPoints = 200f; // Size of the fleet (matching TriTach)
        
        FleetParamsV3 params = new FleetParamsV3(
                null, // market
                Global.getSector().getPlayerFleet().getLocationInHyperspace(), // location
                Factions.TRITACHYON, // faction to create the fleet for initially
                1f, // qualityMod
                FleetTypes.MERC_BOUNTY_HUNTER, // fleetType
                fleetPoints, // combatPts
                0f, // freighterPts 
                fleetPoints * 0.1f, // tankerPts
                0f, // transportPts
                0f, // linerPts
                0f, // utilityPts
                0f // qualityMod
        );
        
        // Configure officer settings (matching TriTachLoanIncentiveScript)
        params.officerNumberBonus = 4;
        params.officerLevelBonus = 3;
        
        // Set doctrine override for more aggressive behavior
        params.doctrineOverride = Global.getSector().getFaction(Factions.TRITACHYON).getDoctrine().clone();
        params.doctrineOverride.setWarships(3);
        params.doctrineOverride.setPhaseShips(3);
        params.doctrineOverride.setCarriers(1);
        
        params.random = new Random(seed);
        
        return params;
    }
    
    /**
     * Check if a debt collector is currently active (for external queries)
     */
    public static boolean isCollectorActive() {
        String state = Global.getSector().getMemoryWithoutUpdate().getString(MEM_COLLECTOR_STATE);
        return STATE_ACTIVE.equals(state);
    }
    
    /**
     * Check if a debt collector is pending spawn
     */
    public static boolean isCollectorPending() {
        String state = Global.getSector().getMemoryWithoutUpdate().getString(MEM_COLLECTOR_STATE);
        return STATE_PENDING.equals(state);
    }
    
    /**
     * Force reset the debt collector system (for admin/debug purposes)
     * This is a public API method intended for:
     * - Console commands (if mod has console integration)
     * - Debug/testing scenarios
     * - External mod integration
     * Similar public reset methods exist in base game for various systems.
     */
    @SuppressWarnings("unused")
    public static void resetCollectorSystem() {
        Global.getSector().getMemoryWithoutUpdate().set(MEM_COLLECTOR_STATE, STATE_NONE);
        Global.getSector().getMemoryWithoutUpdate().unset(MEM_COLLECTOR_FLEET_ID);
        Global.getSector().getMemoryWithoutUpdate().unset(MEM_PENDING_START_TIME);
        Global.getLogger(CasinoDebtScript.class).info("Debt collector system manually reset");
    }
    
    /**
     * Initialize the debt collector system for a new player or save.
     * Sets default values for all memory keys if not already present.
     * Called from CasinoModPlugin.onGameLoad().
     * 
     * This ensures proper handling when loading the mod onto an existing game
     * where the player may already have debt exceeding the threshold.
     */
    public static void initializeSystem() {
        MemoryAPI memory = Global.getSector().getMemoryWithoutUpdate();
        
        if (!memory.contains(MEM_COLLECTOR_STATE)) {
            memory.set(MEM_COLLECTOR_STATE, STATE_NONE);
        }
        
        if (!memory.contains(MEM_LAST_SPAWN_TIMESTAMP)) {
            memory.set(MEM_LAST_SPAWN_TIMESTAMP, Long.valueOf(0L));
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
