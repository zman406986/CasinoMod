package data.scripts.casino.util;

import com.fs.starfarer.api.campaign.TextPanelAPI;
import data.scripts.casino.SpiralAbyssArena;
import java.awt.Color;
import java.util.List;

/**
 * Utility class for formatting and displaying log entries consistently across the casino mod
 */
public class LogFormatter {
    
    /**
     * Processes and formats a log entry for display in the UI
     * @param logEntry The raw log entry text
     * @param textPanel The UI panel to add the formatted text to (TextPanelAPI version)
     * @param arenaCombatants The list of arena combatants for identifying ship names
     */
    public static void processLogEntry(String logEntry, TextPanelAPI textPanel, List<SpiralAbyssArena.SpiralGladiator> arenaCombatants) {
        // Parse the log entry to identify attacker, target, and damage values
        // Use regex to find patterns like "$attacker hits $target for $dmg!"
        
        // First, restore font to default
        textPanel.setFontInsignia();
        
        if (logEntry.contains("$attacker") || logEntry.contains("$target") || logEntry.contains("$dmg")) {
            // This shouldn't happen since the replacement should already occur in the simulation
            textPanel.addPara(logEntry);
            return;
        }
        
        // Check if this is a ship status line
        if (logEntry.startsWith("--- SHIP STATUS ---")) {
            textPanel.addPara(logEntry, Color.YELLOW);
            return;
        }
        
        // Check if this is a status line for a specific ship
        if (logEntry.contains(": ") && (logEntry.contains("HP") || logEntry.contains("angry at"))) {
            // This is a status line like "Hammerhead: 45/60 HP (angry at Manticore)"
            textPanel.addPara(logEntry, Color.GRAY);
            
            // Highlight the ship name
            String[] parts = logEntry.split(": ");
            if (parts.length > 0) {
                String shipName = parts[0].trim();
                textPanel.highlightInLastPara(Color.WHITE, shipName);
            }
            
            // Highlight HP values
            if (logEntry.contains("/")) {
                String[] hpParts = logEntry.split(" ");
                for (String part : hpParts) {
                    if (part.contains("/")) {
                        textPanel.highlightInLastPara(Color.GREEN, part);
                    }
                }
            }
            
            // Highlight the angry text
            if (logEntry.contains("(angry at")) {
                int start = logEntry.indexOf("(angry at");
                int end = logEntry.indexOf(")", start);
                if (end > start) {
                    String angryText = logEntry.substring(start, end + 1);
                    textPanel.highlightInLastPara(Color.RED, angryText);
                }
            }
            return;
        }
        
        // Look for damage patterns: "shipName hits shipName for X HP!"
        // We'll use a simple approach looking for "hits" and "for" keywords
        String[] parts = logEntry.split("hits | for |!");
        if (parts.length >= 3 && logEntry.contains("hits") && logEntry.contains("for")) {
            // Format: "Attacker hits Target for Damage!"
            String attacker = parts[0].trim();
            String target = parts[1].trim();
            String damagePart = parts[2].trim() + (logEntry.endsWith("!") ? "!" : "");
            
            // Combine the text into a single paragraph to avoid excessive line breaks
            String combinedText = attacker + " hits " + target + " for " + damagePart;
            textPanel.addPara(combinedText, Color.WHITE);
            
            // Highlight attacker in one color
            textPanel.highlightInLastPara(Color.CYAN, attacker);
            
            // Highlight target in another color
            textPanel.highlightInLastPara(Color.ORANGE, target);
            
            // Highlight damage values in red/yellow to indicate damage
            textPanel.highlightInLastPara(Color.RED, damagePart.replaceAll("[^0-9]", "").trim());
        } else if (logEntry.contains("suffered a Hull Breach") || logEntry.contains("was lost to space decompression")) {
            // Handle hull breach events: "shipName suffered a Hull Breach! (-X HP)"
            String[] parts2 = logEntry.split(" suffered a| was lost");
            if (parts2.length >= 1) {
                String shipName = parts2[0].trim();
                String eventDesc = logEntry.substring(shipName.length()).trim();
                
                // Combine into a single paragraph to avoid excessive line breaks
                String combinedText = shipName + eventDesc;
                textPanel.addPara(combinedText, Color.WHITE);
                
                // Highlight the ship name
                textPanel.highlightInLastPara(Color.MAGENTA, shipName);
            } else {
                textPanel.addPara(logEntry);
            }
        } else if (logEntry.contains("馃拃") || logEntry.contains("馃挜") || logEntry.contains("鈿狅笍")) {
            // Handle special events with emojis
            if (logEntry.contains(":")) {
                String[] parts3 = logEntry.split(":", 2);
                if (parts3.length >= 2) {
                    // Combine into a single paragraph to avoid excessive line breaks
                    String combinedText = parts3[0] + ": " + parts3[1];
                    textPanel.addPara(combinedText, Color.WHITE);
                    
                    // Highlight important parts differently
                    textPanel.highlightInLastPara(Color.YELLOW, parts3[0]);
                    textPanel.highlightInLastPara(Color.CYAN, parts3[1]);
                } else {
                    textPanel.addPara(logEntry);
                }
            } else {
                textPanel.addPara(logEntry);
            }
        } else {
            // For other log entries, try to identify ship names
            boolean foundFormatted = false;
            for (SpiralAbyssArena.SpiralGladiator gladiator : arenaCombatants) {
                if (logEntry.contains(gladiator.shortName)) {
                    // Use the original log entry but with consistent coloring to avoid excessive line breaks
                    textPanel.addPara(logEntry);
                    
                    // Extract and highlight different parts of the ship name separately
                    String prefixText = gladiator.prefix;
                    String hullNameText = gladiator.hullName;
                    String affixText = gladiator.affix;
                    
                    // Highlight the hull name in white separately from affixes/prefixes
                    textPanel.highlightInLastPara(Color.WHITE, hullNameText);
                    
                    // Highlight the prefix in green/red based on whether it's positive or negative
                    Color prefixHighlightColor = isPositiveAffix(gladiator.prefix);
                    textPanel.highlightInLastPara(prefixHighlightColor, prefixText);
                    
                    // Highlight the affix in green/red based on whether it's positive or negative
                    Color affixHighlightColor = isPositiveAffix(gladiator.affix);
                    textPanel.highlightInLastPara(affixHighlightColor, affixText);
                    
                    // Highlight any numeric values (damage, HP, etc.) in red
                    String[] numericParts = logEntry.split("[^0-9]+");
                    for (String numPart : numericParts) {
                        if (!numPart.trim().isEmpty()) {
                            textPanel.highlightInLastPara(Color.RED, numPart);
                        }
                    }
                    
                    foundFormatted = true;
                    break;
                }
            }
            if (!foundFormatted) {
                textPanel.addPara(logEntry);
            }
        }
    }
    
    /**
     * Determines if an affix or prefix is positive or negative based on the configuration
     */
    private static Color isPositiveAffix(String affixOrPrefix) {
        // Check if it's in the positive affix list
        if (data.scripts.casino.util.ConfigManager.ARENA_AFFIX_POS.contains(affixOrPrefix)) {
            return Color.GREEN;
        }
        
        // Check if it's in the negative affix list
        if (data.scripts.casino.util.ConfigManager.ARENA_AFFIX_NEG.contains(affixOrPrefix)) {
            return Color.RED;
        }
        
        // Check if it's in the positive prefix list
        if (data.scripts.casino.util.ConfigManager.ARENA_PREFIX_STRONG_POS.contains(affixOrPrefix)) {
            return Color.GREEN;
        }
        
        // Check if it's in the negative prefix list
        if (data.scripts.casino.util.ConfigManager.ARENA_PREFIX_STRONG_NEG.contains(affixOrPrefix)) {
            return Color.RED;
        }
        
        // Default to green if not found in either list (positive assumption)
        return Color.GREEN;
    }
}