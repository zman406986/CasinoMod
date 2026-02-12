package data.scripts.casino.util;

import com.fs.starfarer.api.campaign.TextPanelAPI;
import data.scripts.casino.CasinoConfig;
import data.scripts.casino.SpiralAbyssArena;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for formatting arena battle log entries.
 * Processes log entries and adds them to the text panel with appropriate colors.
 *
 * Enhanced Color Coding:
 * - Ship Names: Cyan (for visibility in battle logs)
 * - Damage Numbers: Orange
 * - Crit Damage: Yellow
 * - Miss Messages: Gray
 * - Kill Messages: Red background with yellow ship name
 * - Random Events: Magenta
 * - Ship Status HP: Color-coded by percentage (green/yellow/red)
 * - Headers: White
 */
public class LogFormatter {

    // Color definitions for battle log
    private static final Color SHIP_NAME_COLOR = Color.CYAN;                     // Cyan for ship names
    private static final Color DAMAGE_COLOR = Color.ORANGE;                      // Orange for damage
    private static final Color CRIT_DAMAGE_COLOR = Color.YELLOW;                 // Yellow for crit damage

    /**
     * Processes a log entry and adds it to the text panel with multi-color formatting.
     *
     * @param logEntry The log entry string to process
     * @param textPanel The text panel to add the formatted entry to
     * @param combatants The list of combatants for context
     * @param bets The list of bets placed by the player (for displaying bet amounts in status)
     */
    public static void processLogEntry(String logEntry, TextPanelAPI textPanel, List<SpiralAbyssArena.SpiralGladiator> combatants, List<data.scripts.casino.interaction.ArenaHandler.BetInfo> bets) {
        if (logEntry == null || logEntry.isEmpty()) {
            return;
        }

        // Random Events - Purple/Magenta (entire line)
        if (logEntry.startsWith("[EVENT]")) {
            processEventLog(logEntry, textPanel);
            return;
        }

        // Event damage - Purple/Magenta with ship names highlighted
        if (logEntry.startsWith("[HIT]")) {
            processEventDamageLog(logEntry, textPanel, combatants);
            return;
        }

        // Kill messages - Multi-color with ship names
        if (logEntry.startsWith("[KILL]")) {
            processKillLog(logEntry, textPanel, combatants);
            return;
        }

        // Ship Status header - White
        if (logEntry.equals("--- SHIP STATUS ---")) {
            textPanel.addPara(logEntry, Color.WHITE);
            return;
        }

        // Ship status lines - Multi-color with HP values and bet amounts
        if (logEntry.contains(": ") && logEntry.contains("HP")) {
            processStatusLog(logEntry, textPanel, combatants, bets);
            return;
        }

        // Crit messages - Yellow damage color
        if (isCritMessage(logEntry)) {
            processCritLog(logEntry, textPanel, combatants);
            return;
        }

        // Attack/Damage messages - Multi-color with ship names, damage, prefixes/affixes
        if (containsDamage(logEntry)) {
            processAttackLog(logEntry, textPanel, combatants);
            return;
        }

        // Miss messages - Multi-color with ship names
        if (isMissMessage(logEntry)) {
            processMissLog(logEntry, textPanel, combatants);
            return;
        }

        // Default: white for anything else
        textPanel.addPara(logEntry, Color.WHITE);
    }

    /**
     * Processes event log entries with magenta color.
     */
    private static void processEventLog(String logEntry, TextPanelAPI textPanel) {
        textPanel.addPara(logEntry, Color.MAGENTA);
    }

    /**
     * Processes event damage log entries with ship name highlighting.
     */
    private static void processEventDamageLog(String logEntry, TextPanelAPI textPanel, List<SpiralAbyssArena.SpiralGladiator> combatants) {
        // Format: "💥 ShipName takes X damage!"
        textPanel.addPara(logEntry, Color.MAGENTA);

        List<String> highlights = new ArrayList<>();
        List<Color> highlightColors = new ArrayList<>();

        // Highlight ship name
        String shipName = extractShipName(logEntry, combatants);
        if (shipName != null) {
            highlights.add(shipName);
            highlightColors.add(SHIP_NAME_COLOR);
        }

        // Highlight damage number
        String damage = extractDamageNumber(logEntry);
        if (damage != null) {
            highlights.add(damage);
            highlightColors.add(DAMAGE_COLOR);
        }

        // Apply all highlights
        if (!highlights.isEmpty()) {
            textPanel.setHighlightColorsInLastPara(highlightColors.toArray(new Color[0]));
            textPanel.highlightInLastPara(highlights.toArray(new String[0]));
        }
    }

    /**
     * Processes kill log entries with red background and yellow ship name highlighting.
     * Removes the [KILL] tag from display but keeps the line red.
     */
    private static void processKillLog(String logEntry, TextPanelAPI textPanel, List<SpiralAbyssArena.SpiralGladiator> combatants) {
        // Format: "[KILL] ShipName was destroyed..." or "[KILL] ShipName was destroyed by the incident!"
        // Remove the [KILL] tag for display
        String displayText = logEntry.startsWith("[KILL] ") ? logEntry.substring(7) : logEntry;

        // Add with default color first
        textPanel.addPara(displayText);

        // Extract ship name from the original log entry
        String shipName = extractShipName(logEntry, combatants);

        // Highlight the entire line in red
        List<String> highlights = new ArrayList<>();
        List<Color> highlightColors = new ArrayList<>();

        highlights.add(displayText);
        highlightColors.add(Color.RED);

        // Then highlight ship name in yellow (overrides red for ship name)
        if (shipName != null) {
            highlights.add(shipName);
            highlightColors.add(Color.YELLOW);
        }

        // Apply all highlights
        textPanel.setHighlightColorsInLastPara(highlightColors.toArray(new Color[0]));
        textPanel.highlightInLastPara(highlights.toArray(new String[0]));
    }

    /**
     * Processes status log entries with HP value coloring and bet amount display.
     * Ship names are highlighted in cyan, HP values are color-coded by percentage.
     */
    private static void processStatusLog(String logEntry, TextPanelAPI textPanel, List<SpiralAbyssArena.SpiralGladiator> combatants, List<data.scripts.casino.interaction.ArenaHandler.BetInfo> bets) {
        // Find the ship from combatants list based on the ship name in the log entry
        int colonIndex = logEntry.indexOf(":");
        String shipName = "";
        if (colonIndex > 0) {
            shipName = logEntry.substring(0, colonIndex).trim();
        }

        // Find the ship in combatants list
        SpiralAbyssArena.SpiralGladiator ship = null;
        for (SpiralAbyssArena.SpiralGladiator gladiator : combatants) {
            if (gladiator.shortName.equals(shipName) || gladiator.hullName.equals(shipName)) {
                ship = gladiator;
                break;
            }
        }

        // Calculate bet amount for this ship
        int betAmount = 0;
        if (ship != null && bets != null) {
            for (data.scripts.casino.interaction.ArenaHandler.BetInfo bet : bets) {
                if (bet.ship == ship) {
                    betAmount += bet.amount;
                }
            }
        }

        // If there's a bet, append it to the log entry
        String displayEntry = logEntry;
        if (betAmount > 0) {
            displayEntry = logEntry + " [Bet: " + betAmount + "]";
        }

        // Format: "HullName: X/Y HP (optional status) [Bet: Z]"
        // Use white as base color so all ship names are consistent
        textPanel.addPara(displayEntry, Color.WHITE);

        List<String> highlights = new ArrayList<>();
        List<Color> highlightColors = new ArrayList<>();

        // Highlight ship name in cyan
        if (colonIndex > 0) {
            highlights.add(shipName);
            highlightColors.add(SHIP_NAME_COLOR);
        }

        // Extract and highlight HP values with color coding
        String[] hpValues = extractHpValues(logEntry);
        if (hpValues != null) {
            String currentHp = hpValues[0];
            String maxHp = hpValues[1];

            // Color current HP based on health percentage
            if (ship != null) {
                Color hpColor = getHpColor(ship.hp, ship.maxHp);
                highlights.add(currentHp);
                highlightColors.add(hpColor);
            }

            // Max HP in gray
            highlights.add(maxHp);
            highlightColors.add(Color.GRAY);
        }

        // Highlight bet amount in yellow if present
        if (betAmount > 0) {
            String betText = "[Bet: " + betAmount + "]";
            highlights.add(betText);
            highlightColors.add(Color.YELLOW);
        }

        // Apply all highlights
        if (!highlights.isEmpty()) {
            textPanel.setHighlightColorsInLastPara(highlightColors.toArray(new Color[0]));
            textPanel.highlightInLastPara(highlights.toArray(new String[0]));
        }
    }

    /**
     * Extracts current and max HP values from a status log entry.
     * Format: "HullName: X/Y HP"
     * Returns array [currentHp, maxHp] or null if not found.
     */
    private static String[] extractHpValues(String logEntry) {
        // Look for pattern "X/Y HP"
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+)/(\\d+) HP");
        java.util.regex.Matcher matcher = pattern.matcher(logEntry);
        if (matcher.find()) {
            return new String[]{matcher.group(1), matcher.group(2)};
        }
        return null;
    }

    /**
     * Gets the color for HP value based on health percentage.
     * > 70%: Green, 30-70%: Yellow, < 30%: Red
     */
    private static Color getHpColor(int currentHp, int maxHp) {
        if (maxHp <= 0) return Color.WHITE;
        float percentage = (float) currentHp / (float) maxHp;
        if (percentage > 0.7f) {
            return new Color(50, 255, 50); // Bright green
        } else if (percentage > 0.3f) {
            return Color.YELLOW; // Yellow
        } else {
            return new Color(255, 50, 50); // Bright red
        }
    }

    /**
     * Processes crit log entries with yellow damage highlighting.
     * Highlights ship names in cyan and damage numbers in yellow.
     */
    private static void processCritLog(String logEntry, TextPanelAPI textPanel, List<SpiralAbyssArena.SpiralGladiator> combatants) {
        // Add the full text first in yellow for crit
        textPanel.addPara(logEntry, Color.YELLOW);

        List<String> highlights = new ArrayList<>();
        List<Color> highlightColors = new ArrayList<>();

        // Extract and highlight attacker ship name (first ship in text)
        SpiralAbyssArena.SpiralGladiator attacker = findAttacker(logEntry, combatants);
        if (attacker != null) {
            highlights.add(attacker.shortName);
            highlightColors.add(SHIP_NAME_COLOR);
        }

        // Extract and highlight target ship name (second ship in text)
        SpiralAbyssArena.SpiralGladiator target = findTarget(logEntry, combatants);
        if (target != null) {
            highlights.add(target.shortName);
            highlightColors.add(SHIP_NAME_COLOR);
        }

        // Highlight damage number with crit color
        String damage = extractDamageNumber(logEntry);
        if (damage != null) {
            highlights.add(damage);
            highlightColors.add(CRIT_DAMAGE_COLOR);
        }

        // Apply all highlights
        if (!highlights.isEmpty()) {
            textPanel.setHighlightColorsInLastPara(highlightColors.toArray(new Color[0]));
            textPanel.highlightInLastPara(highlights.toArray(new String[0]));
        }
    }

    /**
     * Processes attack/damage log entries with multi-color support.
     * Highlights ship names in cyan and damage numbers in orange.
     */
    private static void processAttackLog(String logEntry, TextPanelAPI textPanel, List<SpiralAbyssArena.SpiralGladiator> combatants) {
        // Add the full text first
        textPanel.addPara(logEntry, Color.WHITE);

        List<String> highlights = new ArrayList<>();
        List<Color> highlightColors = new ArrayList<>();

        // Extract and highlight attacker ship name (first ship in text)
        SpiralAbyssArena.SpiralGladiator attacker = findAttacker(logEntry, combatants);
        if (attacker != null) {
            highlights.add(attacker.shortName);
            highlightColors.add(SHIP_NAME_COLOR);
        }

        // Extract and highlight target ship name (second ship in text)
        SpiralAbyssArena.SpiralGladiator target = findTarget(logEntry, combatants);
        if (target != null) {
            highlights.add(target.shortName);
            highlightColors.add(SHIP_NAME_COLOR);
        }

        // Highlight damage number
        String damage = extractDamageNumber(logEntry);
        if (damage != null) {
            highlights.add(damage);
            highlightColors.add(DAMAGE_COLOR);
        }

        // Apply all highlights
        if (!highlights.isEmpty()) {
            textPanel.setHighlightColorsInLastPara(highlightColors.toArray(new Color[0]));
            textPanel.highlightInLastPara(highlights.toArray(new String[0]));
        }
    }

    /**
     * Processes miss log entries with ship name highlighting.
     * Highlights ship names in cyan.
     */
    private static void processMissLog(String logEntry, TextPanelAPI textPanel, List<SpiralAbyssArena.SpiralGladiator> combatants) {
        // Add the full text in gray
        textPanel.addPara(logEntry, Color.GRAY);

        List<String> highlights = new ArrayList<>();
        List<Color> highlightColors = new ArrayList<>();

        // Extract and highlight attacker ship name (first ship in text)
        SpiralAbyssArena.SpiralGladiator attacker = findAttacker(logEntry, combatants);
        if (attacker != null) {
            highlights.add(attacker.shortName);
            highlightColors.add(SHIP_NAME_COLOR);
        }

        // Extract and highlight target ship name (second ship in text)
        SpiralAbyssArena.SpiralGladiator target = findTarget(logEntry, combatants);
        if (target != null) {
            highlights.add(target.shortName);
            highlightColors.add(SHIP_NAME_COLOR);
        }

        // Apply all highlights
        if (!highlights.isEmpty()) {
            textPanel.setHighlightColorsInLastPara(highlightColors.toArray(new Color[0]));
            textPanel.highlightInLastPara(highlights.toArray(new String[0]));
        }
    }

    /**
     * Extracts ship name from a log entry by matching against known combatants.
     * Returns the first ship name found in the text (by position).
     */
    private static String extractShipName(String logEntry, List<SpiralAbyssArena.SpiralGladiator> combatants) {
        String foundShipName = null;
        int firstIndex = Integer.MAX_VALUE;

        for (SpiralAbyssArena.SpiralGladiator ship : combatants) {
            int index = logEntry.indexOf(ship.shortName);
            if (index >= 0 && index < firstIndex) {
                firstIndex = index;
                foundShipName = ship.shortName;
            }
        }

        return foundShipName;
    }

    /**
     * Finds the attacker ship from a log entry by finding the first ship name occurrence.
     * In flavor text patterns like "ShipA hits ShipB for X damage!", ShipA is the attacker.
     */
    private static SpiralAbyssArena.SpiralGladiator findAttacker(String logEntry, List<SpiralAbyssArena.SpiralGladiator> combatants) {
        // Find the ship whose name appears first in the log entry
        SpiralAbyssArena.SpiralGladiator firstShip = null;
        int firstIndex = Integer.MAX_VALUE;

        for (SpiralAbyssArena.SpiralGladiator ship : combatants) {
            int index = logEntry.indexOf(ship.shortName);
            if (index >= 0 && index < firstIndex) {
                firstIndex = index;
                firstShip = ship;
            }
        }

        return firstShip;
    }

    /**
     * Finds the target ship from a log entry by finding the second ship name occurrence.
     * In flavor text patterns like "ShipA hits ShipB for X damage!", ShipB is the target.
     */
    private static SpiralAbyssArena.SpiralGladiator findTarget(String logEntry, List<SpiralAbyssArena.SpiralGladiator> combatants) {
        // Find all ship name occurrences and their positions
        SpiralAbyssArena.SpiralGladiator firstShip = null;
        SpiralAbyssArena.SpiralGladiator secondShip = null;
        int firstIndex = Integer.MAX_VALUE;
        int secondIndex = Integer.MAX_VALUE;

        for (SpiralAbyssArena.SpiralGladiator ship : combatants) {
            int index = logEntry.indexOf(ship.shortName);
            if (index >= 0) {
                if (index < firstIndex) {
                    // Shift current first to second
                    secondIndex = firstIndex;
                    secondShip = firstShip;
                    // Set new first
                    firstIndex = index;
                    firstShip = ship;
                } else if (index < secondIndex && index != firstIndex) {
                    secondIndex = index;
                    secondShip = ship;
                }
            }
        }

        // Return the second ship found (the target), or first ship if only one found
        return secondShip != null ? secondShip : firstShip;
    }

    /**
     * Extracts damage number from a log entry.
     */
    private static String extractDamageNumber(String logEntry) {
        // Look for "X CRIT damage" pattern (e.g., "75 CRIT damage to Legion!")
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+) CRIT damage");
        java.util.regex.Matcher matcher = pattern.matcher(logEntry);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // Look for patterns like "for X damage" or "takes X damage"
        pattern = java.util.regex.Pattern.compile("(\\d+) damage");
        matcher = pattern.matcher(logEntry);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // Look for "dealing X to" pattern (e.g., "dealing 60 to Mora")
        pattern = java.util.regex.Pattern.compile("dealing (\\d+) to");
        matcher = pattern.matcher(logEntry);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // Look for "takes X CRIT" pattern (e.g., "takes 97 CRIT!")
        pattern = java.util.regex.Pattern.compile("takes (\\d+) CRIT");
        matcher = pattern.matcher(logEntry);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // Look for "with X damage" pattern (e.g., "sends Doom to the stars with 42 damage")
        pattern = java.util.regex.Pattern.compile("with (\\d+) damage");
        matcher = pattern.matcher(logEntry);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // Look for "for X!" or "for X." pattern (without "damage" word)
        pattern = java.util.regex.Pattern.compile("for (\\d+)[!\\.]");
        matcher = pattern.matcher(logEntry);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // Look for standalone "for X" pattern as fallback
        pattern = java.util.regex.Pattern.compile("for (\\d+)");
        matcher = pattern.matcher(logEntry);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    /**
     * Checks if a log entry contains damage information (attack hit)
     */
    private static boolean containsDamage(String logEntry) {
        String lower = logEntry.toLowerCase();

        // Check for common damage indicators in flavor text
        if (logEntry.matches(".*for \\d+ damage.*")) {
            return true;
        }

        // Check for "with X damage" pattern (e.g., "sends Doom to the stars with 42 damage")
        if (logEntry.matches(".*with \\d+ damage.*")) {
            return true;
        }

        // Check for "dealt X emotional damage" pattern
        if (lower.contains("emotional damage")) {
            return true;
        }

        // Check for crit-related keywords
        if (lower.contains("crit") || lower.contains("critical") || lower.contains("devastating")) {
            return true;
        }

        // Check for attack-related keywords that indicate a hit
        if (lower.contains("hits") || lower.contains("strikes") || lower.contains("blasts") ||
            lower.contains("slashes") || lower.contains("fires") || lower.contains("unleashes") ||
            lower.contains("rams") || lower.contains("sends") || lower.contains("dealt")) {
            return true;
        }

        return false;
    }

    /**
     * Checks if a log entry is a miss message
     */
    private static boolean isMissMessage(String logEntry) {
        String lower = logEntry.toLowerCase();
        return lower.contains("miss") || lower.contains("dodges") || lower.contains("dodged") ||
               lower.contains("evades") || lower.contains("evaded") ||
               lower.contains("misses") || lower.contains("missed") ||
               lower.contains("shot went wide") || lower.contains("too slow") ||
               lower.contains("frame perfect") || lower.contains("is that all") ||
               lower.contains("think you can get away") || lower.contains("your aim is as bad") ||
               lower.contains("busted");
    }

    /**
     * Checks if a log entry is a crit message
     */
    private static boolean isCritMessage(String logEntry) {
        String lower = logEntry.toLowerCase();
        return lower.contains("critical") || lower.contains("crit!") || lower.contains("crit damage") ||
               lower.contains("nowhere to hide") || lower.contains("witness the stars shatter") ||
               lower.contains("disappear among the sea") || lower.contains("rules are made to be broken") ||
               lower.contains("feel the weight of a thousand failed gacha");
    }
}
