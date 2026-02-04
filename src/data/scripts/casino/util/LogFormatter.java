package data.scripts.casino.util;

import com.fs.starfarer.api.campaign.TextPanelAPI;
import data.scripts.casino.SpiralAbyssArena;

import java.awt.Color;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for formatting arena battle log entries.
 * Processes log entries and adds them to the text panel with appropriate colors.
 */
public class LogFormatter {

    private static final Pattern ATTACK_PATTERN = Pattern.compile("(.+?): Attacks (.+?) for (\\d+) damage");
    private static final Pattern KILL_PATTERN = Pattern.compile("(.+?): Kills (.+?)");
    private static final Pattern MISS_PATTERN = Pattern.compile("(.+?): Misses (.+?)");

    /**
     * Processes a log entry and adds it to the text panel with appropriate formatting.
     *
     * @param logEntry The log entry string to process
     * @param textPanel The text panel to add the formatted entry to
     * @param combatants The list of combatants for context
     */
    public static void processLogEntry(String logEntry, TextPanelAPI textPanel, List<SpiralAbyssArena.SpiralGladiator> combatants) {
        if (logEntry == null || logEntry.isEmpty()) {
            return;
        }

        // Check for kill message
        Matcher killMatcher = KILL_PATTERN.matcher(logEntry);
        if (killMatcher.find()) {
            String attacker = killMatcher.group(1);
            String target = killMatcher.group(2);
            textPanel.addPara(attacker + ": ", Color.CYAN);
            textPanel.highlightInLastPara(Color.RED, "Kills " + target);
            return;
        }

        // Check for attack message
        Matcher attackMatcher = ATTACK_PATTERN.matcher(logEntry);
        if (attackMatcher.find()) {
            String attacker = attackMatcher.group(1);
            String target = attackMatcher.group(2);
            String damage = attackMatcher.group(3);

            textPanel.addPara(attacker + ": Attacks " + target + " for ", Color.CYAN);
            textPanel.highlightInLastPara(Color.RED, damage + " damage");
            return;
        }

        // Check for miss message
        Matcher missMatcher = MISS_PATTERN.matcher(logEntry);
        if (missMatcher.find()) {
            String attacker = missMatcher.group(1);
            String target = missMatcher.group(2);
            textPanel.addPara(attacker + ": ", Color.CYAN);
            textPanel.highlightInLastPara(Color.GRAY, "Misses " + target);
            return;
        }

        // Default: just add the entry as-is
        textPanel.addPara(logEntry, Color.WHITE);
    }
}
