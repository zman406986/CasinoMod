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
 * - Ship Names: White (bold highlight)
 * - Positive Prefixes/Affixes: Green (bright green for major, muted for minor)
 * - Negative Prefixes/Affixes: Red/Pink (red for major, pink for minor)
 * - Damage Numbers: Orange
 * - Miss Messages: Gray
 * - Kill Messages: Red
 * - Random Events: Magenta
 * - Ship Status: Cyan
 * - Headers: White
 */
public class LogFormatter {

    // Color definitions for different effect intensities
    private static final Color MAJOR_POSITIVE_COLOR = new Color(50, 255, 50);    // Bright green
    private static final Color MINOR_POSITIVE_COLOR = new Color(100, 200, 100);  // Muted green
    private static final Color MAJOR_NEGATIVE_COLOR = new Color(255, 50, 50);    // Bright red
    private static final Color MINOR_NEGATIVE_COLOR = new Color(255, 150, 150);  // Pink/light red
    private static final Color DAMAGE_COLOR = Color.ORANGE;
    private static final Color CRIT_DAMAGE_COLOR = Color.YELLOW;
    private static final Color SHIP_NAME_COLOR = Color.WHITE;

    /**
     * Processes a log entry and adds it to the text panel with multi-color formatting.
     *
     * @param logEntry The log entry string to process
     * @param textPanel The text panel to add the formatted entry to
     * @param combatants The list of combatants for context
     */
    public static void processLogEntry(String logEntry, TextPanelAPI textPanel, List<SpiralAbyssArena.SpiralGladiator> combatants) {
        if (logEntry == null || logEntry.isEmpty()) {
            return;
        }

        // Random Events - Purple/Magenta (entire line)
        if (logEntry.startsWith("⚠️ [EVENT]")) {
            processEventLog(logEntry, textPanel);
            return;
        }

        // Event damage - Purple/Magenta with ship names highlighted
        if (logEntry.startsWith("💥")) {
            processEventDamageLog(logEntry, textPanel, combatants);
            return;
        }

        // Kill messages - Multi-color with ship names
        if (logEntry.startsWith("💀")) {
            processKillLog(logEntry, textPanel, combatants);
            return;
        }

        // Ship Status header - White
        if (logEntry.equals("--- SHIP STATUS ---")) {
            textPanel.addPara(logEntry, Color.WHITE);
            return;
        }

        // Ship status lines - Multi-color with HP values
        if (logEntry.contains(": ") && logEntry.contains("HP")) {
            processStatusLog(logEntry, textPanel);
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

        // Highlight ship name
        String shipName = extractShipName(logEntry, combatants);
        if (shipName != null) {
            textPanel.highlightInLastPara(SHIP_NAME_COLOR, shipName);
        }

        // Highlight damage number
        String damage = extractDamageNumber(logEntry);
        if (damage != null) {
            textPanel.highlightInLastPara(DAMAGE_COLOR, damage);
        }
    }

    /**
     * Processes kill log entries with ship name highlighting.
     */
    private static void processKillLog(String logEntry, TextPanelAPI textPanel, List<SpiralAbyssArena.SpiralGladiator> combatants) {
        // Format: "💀 ShipName was destroyed..." or "💀 ShipName was destroyed by the incident!"
        textPanel.addPara(logEntry, Color.RED);

        // Highlight ship name
        String shipName = extractShipName(logEntry, combatants);
        if (shipName != null) {
            textPanel.highlightInLastPara(SHIP_NAME_COLOR, shipName);
        }
    }

    /**
     * Processes status log entries with HP value coloring.
     */
    private static void processStatusLog(String logEntry, TextPanelAPI textPanel) {
        // Format: "HullName: X/Y HP (optional status)"
        textPanel.addPara(logEntry, Color.CYAN);

        // Highlight ship name (before the colon)
        int colonIndex = logEntry.indexOf(":");
        if (colonIndex > 0) {
            String shipName = logEntry.substring(0, colonIndex).trim();
            textPanel.highlightInLastPara(SHIP_NAME_COLOR, shipName);
        }
    }

    /**
     * Processes crit log entries with yellow damage highlighting.
     */
    private static void processCritLog(String logEntry, TextPanelAPI textPanel, List<SpiralAbyssArena.SpiralGladiator> combatants) {
        // Add the full text first in yellow for crit
        textPanel.addPara(logEntry, Color.YELLOW);

        List<String> highlights = new ArrayList<>();
        List<Color> highlightColors = new ArrayList<>();

        // Extract and highlight attacker ship name with prefix/affix coloring
        SpiralAbyssArena.SpiralGladiator attacker = findAttacker(logEntry, combatants);
        if (attacker != null) {
            highlights.add(attacker.shortName);
            highlightColors.add(SHIP_NAME_COLOR);

            // Highlight prefix with appropriate color
            if (attacker.prefix != null && !attacker.prefix.isEmpty()) {
                Color prefixColor = getPrefixColor(attacker.prefix);
                if (prefixColor != null) {
                    highlights.add(attacker.prefix);
                    highlightColors.add(prefixColor);
                }
            }

            // Highlight affix with appropriate color
            if (attacker.affix != null && !attacker.affix.isEmpty()) {
                Color affixColor = getAffixColor(attacker.affix);
                if (affixColor != null) {
                    highlights.add(attacker.affix);
                    highlightColors.add(affixColor);
                }
            }
        }

        // Extract and highlight target ship name
        SpiralAbyssArena.SpiralGladiator target = findTarget(logEntry, combatants);
        if (target != null) {
            highlights.add(target.shortName);
            highlightColors.add(SHIP_NAME_COLOR);

            // Highlight target prefix
            if (target.prefix != null && !target.prefix.isEmpty()) {
                Color prefixColor = getPrefixColor(target.prefix);
                if (prefixColor != null) {
                    highlights.add(target.prefix);
                    highlightColors.add(prefixColor);
                }
            }

            // Highlight target affix
            if (target.affix != null && !target.affix.isEmpty()) {
                Color affixColor = getAffixColor(target.affix);
                if (affixColor != null) {
                    highlights.add(target.affix);
                    highlightColors.add(affixColor);
                }
            }
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
     * Processes attack/damage log entries with full multi-color support.
     */
    private static void processAttackLog(String logEntry, TextPanelAPI textPanel, List<SpiralAbyssArena.SpiralGladiator> combatants) {
        // Add the full text first
        textPanel.addPara(logEntry, Color.WHITE);

        List<String> highlights = new ArrayList<>();
        List<Color> highlightColors = new ArrayList<>();

        // Extract and highlight attacker ship name with prefix/affix coloring
        SpiralAbyssArena.SpiralGladiator attacker = findAttacker(logEntry, combatants);
        if (attacker != null) {
            // Highlight the full attacker name
            highlights.add(attacker.shortName);
            highlightColors.add(SHIP_NAME_COLOR);

            // Highlight prefix with appropriate color
            if (attacker.prefix != null && !attacker.prefix.isEmpty()) {
                Color prefixColor = getPrefixColor(attacker.prefix);
                if (prefixColor != null) {
                    highlights.add(attacker.prefix);
                    highlightColors.add(prefixColor);
                }
            }

            // Highlight affix with appropriate color
            if (attacker.affix != null && !attacker.affix.isEmpty()) {
                Color affixColor = getAffixColor(attacker.affix);
                if (affixColor != null) {
                    highlights.add(attacker.affix);
                    highlightColors.add(affixColor);
                }
            }
        }

        // Extract and highlight target ship name
        SpiralAbyssArena.SpiralGladiator target = findTarget(logEntry, combatants);
        if (target != null) {
            highlights.add(target.shortName);
            highlightColors.add(SHIP_NAME_COLOR);

            // Highlight target prefix
            if (target.prefix != null && !target.prefix.isEmpty()) {
                Color prefixColor = getPrefixColor(target.prefix);
                if (prefixColor != null) {
                    highlights.add(target.prefix);
                    highlightColors.add(prefixColor);
                }
            }

            // Highlight target affix
            if (target.affix != null && !target.affix.isEmpty()) {
                Color affixColor = getAffixColor(target.affix);
                if (affixColor != null) {
                    highlights.add(target.affix);
                    highlightColors.add(affixColor);
                }
            }
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
     */
    private static void processMissLog(String logEntry, TextPanelAPI textPanel, List<SpiralAbyssArena.SpiralGladiator> combatants) {
        // Add the full text in gray
        textPanel.addPara(logEntry, Color.GRAY);

        List<String> highlights = new ArrayList<>();
        List<Color> highlightColors = new ArrayList<>();

        // Extract and highlight attacker ship name
        SpiralAbyssArena.SpiralGladiator attacker = findAttacker(logEntry, combatants);
        if (attacker != null) {
            highlights.add(attacker.shortName);
            highlightColors.add(SHIP_NAME_COLOR);

            // Highlight prefix
            if (attacker.prefix != null && !attacker.prefix.isEmpty()) {
                Color prefixColor = getPrefixColor(attacker.prefix);
                if (prefixColor != null) {
                    highlights.add(attacker.prefix);
                    highlightColors.add(prefixColor);
                }
            }

            // Highlight affix
            if (attacker.affix != null && !attacker.affix.isEmpty()) {
                Color affixColor = getAffixColor(attacker.affix);
                if (affixColor != null) {
                    highlights.add(attacker.affix);
                    highlightColors.add(affixColor);
                }
            }
        }

        // Extract and highlight target ship name
        SpiralAbyssArena.SpiralGladiator target = findTarget(logEntry, combatants);
        if (target != null) {
            highlights.add(target.shortName);
            highlightColors.add(SHIP_NAME_COLOR);

            // Highlight target prefix
            if (target.prefix != null && !target.prefix.isEmpty()) {
                Color prefixColor = getPrefixColor(target.prefix);
                if (prefixColor != null) {
                    highlights.add(target.prefix);
                    highlightColors.add(prefixColor);
                }
            }

            // Highlight target affix
            if (target.affix != null && !target.affix.isEmpty()) {
                Color affixColor = getAffixColor(target.affix);
                if (affixColor != null) {
                    highlights.add(target.affix);
                    highlightColors.add(affixColor);
                }
            }
        }

        // Apply all highlights
        if (!highlights.isEmpty()) {
            textPanel.setHighlightColorsInLastPara(highlightColors.toArray(new Color[0]));
            textPanel.highlightInLastPara(highlights.toArray(new String[0]));
        }
    }

    /**
     * Gets the color for a prefix based on whether it's positive or negative.
     */
    private static Color getPrefixColor(String prefix) {
        if (CasinoConfig.ARENA_PREFIX_STRONG_POS.contains(prefix)) {
            return MAJOR_POSITIVE_COLOR;
        }
        if (CasinoConfig.ARENA_PREFIX_STRONG_NEG.contains(prefix)) {
            return MAJOR_NEGATIVE_COLOR;
        }
        return null;
    }

    /**
     * Gets the color for an affix based on whether it's positive or negative.
     */
    private static Color getAffixColor(String affix) {
        if (CasinoConfig.ARENA_AFFIX_POS.contains(affix)) {
            return MINOR_POSITIVE_COLOR;
        }
        if (CasinoConfig.ARENA_AFFIX_NEG.contains(affix)) {
            return MINOR_NEGATIVE_COLOR;
        }
        return null;
    }

    /**
     * Extracts ship name from a log entry by matching against known combatants.
     */
    private static String extractShipName(String logEntry, List<SpiralAbyssArena.SpiralGladiator> combatants) {
        for (SpiralAbyssArena.SpiralGladiator ship : combatants) {
            if (logEntry.contains(ship.shortName)) {
                return ship.shortName;
            }
        }
        return null;
    }

    /**
     * Finds the attacker ship from a log entry.
     */
    private static SpiralAbyssArena.SpiralGladiator findAttacker(String logEntry, List<SpiralAbyssArena.SpiralGladiator> combatants) {
        // In flavor text, $attacker is replaced with the ship's shortName
        // We need to find which ship appears first in the text (usually the attacker)
        for (SpiralAbyssArena.SpiralGladiator ship : combatants) {
            if (logEntry.contains(ship.shortName)) {
                // Check if this ship could be the attacker based on context
                // For now, return the first match as a heuristic
                return ship;
            }
        }
        return null;
    }

    /**
     * Finds the target ship from a log entry.
     */
    private static SpiralAbyssArena.SpiralGladiator findTarget(String logEntry, List<SpiralAbyssArena.SpiralGladiator> combatants) {
        // Find the second ship mentioned (usually the target)
        SpiralAbyssArena.SpiralGladiator firstMatch = null;
        for (SpiralAbyssArena.SpiralGladiator ship : combatants) {
            if (logEntry.contains(ship.shortName)) {
                if (firstMatch == null) {
                    firstMatch = ship;
                } else {
                    // Return the second match as the target
                    return ship;
                }
            }
        }
        // If only one ship found, it might be both attacker and target (self-damage)
        // or we couldn't identify properly
        return firstMatch;
    }

    /**
     * Extracts damage number from a log entry.
     */
    private static String extractDamageNumber(String logEntry) {
        // Look for patterns like "for X damage" or "takes X damage"
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+) damage");
        java.util.regex.Matcher matcher = pattern.matcher(logEntry);
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
        // Check for common damage indicators in flavor text
        if (logEntry.matches(".*for \\d+ damage.*")) {
            return true;
        }

        // Check for crit-related keywords
        String lower = logEntry.toLowerCase();
        if (lower.contains("crit") || lower.contains("critical") || lower.contains("devastating")) {
            return true;
        }

        // Check for attack-related keywords that indicate a hit
        if (lower.contains("hits") || lower.contains("strikes") || lower.contains("blasts") ||
            lower.contains("slashes") || lower.contains("fires") || lower.contains("unleashes")) {
            return true;
        }

        return false;
    }

    /**
     * Checks if a log entry is a miss message
     */
    private static boolean isMissMessage(String logEntry) {
        String lower = logEntry.toLowerCase();
        return lower.contains("miss") || lower.contains("dodges") ||
               lower.contains("evades") || lower.contains("misses") ||
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
        return lower.contains("critical") || lower.contains("crit!") ||
               lower.contains("nowhere to hide") || lower.contains("witness the stars shatter") ||
               lower.contains("disappear among the sea") || lower.contains("rules are made to be broken") ||
               lower.contains("feel the weight of a thousand failed gacha");
    }
}
