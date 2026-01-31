package data.scripts.casino;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.impl.campaign.fleets.AutoDespawnScript;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.util.Random;

public class CasinoDebtScript implements EveryFrameScript {

    private float elapsedDays = 0f;
    private static final float CHECK_INTERVAL = 1f; // Check every day
    private boolean debtCollectorSent = false; // Track if debt collector has been sent
    private float lastSpawnCheckTime = 0f; // Time of last spawn check
    private static final float SPAWN_COOLDOWN_MONTHS = 4f; // 4-month cooldown for debt collector spawning

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

        float days = Global.getSector().getClock().convertToDays(amount);
        elapsedDays += days;

        // Check if it's time to apply interest or perform other daily tasks
        if (elapsedDays >= CHECK_INTERVAL) {
            applyDailyTasks();
            elapsedDays = 0f; // Reset counter
        }
    }

    private void applyDailyTasks() {
        // Apply monthly interest on the 15th of each month
        int dayOfMonth = Global.getSector().getClock().getDay();
        if (dayOfMonth == 15) {
            CasinoVIPManager.applyInterest();
        }
        
        // Check if debt threshold is exceeded to trigger debt collectors
        int debtAmount = CasinoVIPManager.getDebt();
        int availableCredit = CasinoVIPManager.getAvailableCredit();
        
        // Calculate current time in months
        float currentTimeMonths = Global.getSector().getClock().getTimestamp() / 30f; // Approximate months
        
        // Use configurable debt threshold for triggering collectors
        // The threshold is negative because it represents a debt value (e.g., -10000 means 10000 in debt)
        int debtThreshold = CasinoConfig.VIP_DEBT_HUNTER_THRESHOLD;
        
        // Only check for spawning if cooldown period has passed
        if (debtAmount <= debtThreshold && (!debtCollectorSent || (currentTimeMonths - lastSpawnCheckTime) >= SPAWN_COOLDOWN_MONTHS)) {
            // Player has exceeded debt threshold, spawn debt collectors
            maybeSpawnDebtCollectors();
            debtCollectorSent = true; // Prevent spawning multiple times
            lastSpawnCheckTime = currentTimeMonths; // Record time of spawn check
        }
    }

    private void maybeSpawnDebtCollectors() {
        // Check if debt collector spawning is already active
        if (Global.getSector().getMemoryWithoutUpdate().getBoolean("$ipc_debt_collectors_active")) {
            return; // Already active, don't spawn more
        }
        
        // Check if player is in a system where debt collectors should spawn
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        if (playerFleet == null) return;
        
        // Mark debt collectors as active to prevent constant spawning
        Global.getSector().getMemoryWithoutUpdate().set("$ipc_debt_collectors_active", true);
        
        // Create and send the debt collector fleet
        CampaignFleetAPI debtCollectorFleet = createDebtCollectorFleet();
        if (debtCollectorFleet != null) {
            // Add the fleet to hyperspace
            Global.getSector().getHyperspace().addEntity(debtCollectorFleet);
            
            // Position the fleet near the player
            Vector2f collectorLoc = Misc.getPointAtRadius(playerFleet.getLocationInHyperspace(), 500f);
            debtCollectorFleet.setLocation(collectorLoc.x, collectorLoc.y);
            
            // Set the fleet to intercept the player
            debtCollectorFleet.getAI().addAssignmentAtStart(FleetAssignment.INTERCEPT, playerFleet, 1000f, null);
            
            // Give standard return to source assignments
            Misc.giveStandardReturnToSourceAssignments(debtCollectorFleet, false);
            
            // Set memory flags to identify this as a debt collector fleet
            debtCollectorFleet.getMemoryWithoutUpdate().set("$ipc_debt_collector", true);
            
            Global.getLogger(this.getClass()).info("Debt collector fleet spawned and sent to intercept player");
        }
    }

    private CampaignFleetAPI createDebtCollectorFleet() {
        // Create a bounty hunter style fleet for collecting debt
        float fleetPoints = 300f; // Size of the fleet based on debt amount or other factors
        Random random = new Random(System.currentTimeMillis()); // Use current time as seed
        
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
        
        // Configure officer settings
        params.officerNumberBonus = 4;
        params.officerLevelBonus = 2;
        
        // Set doctrine override for more aggressive behavior
        params.doctrineOverride = Global.getSector().getFaction(Factions.TRITACHYON).getDoctrine().clone();
        params.doctrineOverride.setWarships(2);
        params.doctrineOverride.setPhaseShips(2);
        params.doctrineOverride.setCarriers(1);
        
        params.random = random;
        
        // Create the fleet using the factory
        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet == null || fleet.isEmpty()) {
            return null;
        }
        
        // Set the fleet to independent faction so it can be hostile
        fleet.setFaction(Factions.INDEPENDENT, true);
        
        // Make the fleet despawn after some time if not engaged
        fleet.addScript(new AutoDespawnScript(fleet));
        
        // Set memory flags to make the fleet hostile
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true);
        
        return fleet;
    }
    
}