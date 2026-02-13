package data.scripts.casino.util;

import com.fs.starfarer.api.campaign.TextPanelAPI;
import data.scripts.casino.CasinoConfig;
import data.scripts.casino.SpiralAbyssArena;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Formats arena battle log entries with multi-color highlighting for the text panel.
 * Dispatches to specialized processors based on log entry prefix/pattern.
 */
public class LogFormatter {

    private static final Color SHIP_NAME_COLOR = Color.CYAN;
    private static final Color DAMAGE_COLOR = Color.ORANGE;
    private static final Color CRIT_DAMAGE_COLOR = Color.YELLOW;

    public static void processLogEntry(String logEntry, TextPanelAPI textPanel, List<SpiralAbyssArena.SpiralGladiator> combatants, List<data.scripts.casino.interaction.ArenaHandler.BetInfo> bets) {
        if (logEntry == null || logEntry.isEmpty()) {
            return;
        }

        if (logEntry.startsWith("[EVENT]")) {
            processEventLog(logEntry, textPanel);
            return;
        }

        if (logEntry.startsWith("[HIT]")) {
            processEventDamageLog(logEntry, textPanel, combatants);
            return;
        }

        if (logEntry.startsWith("[KILL]")) {
            processKillLog(logEntry, textPanel, combatants);
            return;
        }

        if (logEntry.equals("--- SHIP STATUS ---")) {
            textPanel.addPara(logEntry, Color.WHITE);
            return;
        }

        if (logEntry.contains(": ") && logEntry.contains("HP")) {
            processStatusLog(logEntry, textPanel, combatants, bets);
            return;
        }

        if (isCritMessage(logEntry)) {
            processCritLog(logEntry, textPanel, combatants);
            return;
        }

        if (containsDamage(logEntry)) {
            processAttackLog(logEntry, textPanel, combatants);
            return;
        }

        if (isMissMessage(logEntry)) {
            processMissLog(logEntry, textPanel, combatants);
            return;
        }

        textPanel.addPara(logEntry, Color.WHITE);
    }

    private static void processEventLog(String logEntry, TextPanelAPI textPanel) {
        textPanel.addPara(logEntry, Color.MAGENTA);
    }

    private static void processEventDamageLog(String logEntry, TextPanelAPI textPanel, List<SpiralAbyssArena.SpiralGladiator> combatants) {
        textPanel.addPara(logEntry, Color.MAGENTA);

        List<String> highlights = new ArrayList<>();
        List<Color> highlightColors = new ArrayList<>();

        String shipName = extractShipName(logEntry, combatants);
        if (shipName != null) {
            highlights.add(shipName);
            highlightColors.add(SHIP_NAME_COLOR);
        }

        String damage = extractDamageNumber(logEntry);
        if (damage != null) {
            highlights.add(damage);
            highlightColors.add(DAMAGE_COLOR);
        }

        if (!highlights.isEmpty()) {
            textPanel.setHighlightColorsInLastPara(highlightColors.toArray(new Color[0]));
            textPanel.highlightInLastPara(highlights.toArray(new String[0]));
        }
    }

    private static void processKillLog(String logEntry, TextPanelAPI textPanel, List<SpiralAbyssArena.SpiralGladiator> combatants) {
        String displayText = logEntry.startsWith("[KILL] ") ? logEntry.substring(7) : logEntry;
        textPanel.addPara(displayText);

        String shipName = extractShipName(logEntry, combatants);

        List<String> highlights = new ArrayList<>();
        List<Color> highlightColors = new ArrayList<>();

        highlights.add(displayText);
        highlightColors.add(Color.RED);

        if (shipName != null) {
            highlights.add(shipName);
            highlightColors.add(Color.YELLOW);
        }

        textPanel.setHighlightColorsInLastPara(highlightColors.toArray(new Color[0]));
        textPanel.highlightInLastPara(highlights.toArray(new String[0]));
    }

    private static void processStatusLog(String logEntry, TextPanelAPI textPanel, List<SpiralAbyssArena.SpiralGladiator> combatants, List<data.scripts.casino.interaction.ArenaHandler.BetInfo> bets) {
        int colonIndex = logEntry.indexOf(":");
        String shipName = "";
        if (colonIndex > 0) {
            shipName = logEntry.substring(0, colonIndex).trim();
        }

        SpiralAbyssArena.SpiralGladiator ship = null;
        for (SpiralAbyssArena.SpiralGladiator gladiator : combatants) {
            if (gladiator.shortName.equals(shipName) || gladiator.hullName.equals(shipName)) {
                ship = gladiator;
                break;
            }
        }

        int betAmount = 0;
        if (ship != null && bets != null) {
            for (data.scripts.casino.interaction.ArenaHandler.BetInfo bet : bets) {
                if (bet.ship == ship) {
                    betAmount += bet.amount;
                }
            }
        }

        String displayEntry = logEntry;
        if (betAmount > 0) {
            displayEntry = logEntry + " [Bet: " + betAmount + "]";
        }

        textPanel.addPara(displayEntry, Color.WHITE);

        List<String> highlights = new ArrayList<>();
        List<Color> highlightColors = new ArrayList<>();

        if (colonIndex > 0) {
            highlights.add(shipName);
            highlightColors.add(SHIP_NAME_COLOR);
        }

        String[] hpValues = extractHpValues(logEntry);
        if (hpValues != null) {
            String currentHp = hpValues[0];
            String maxHp = hpValues[1];

            if (ship != null) {
                Color hpColor = getHpColor(ship.hp, ship.maxHp);
                highlights.add(currentHp);
                highlightColors.add(hpColor);
            }

            highlights.add(maxHp);
            highlightColors.add(Color.GRAY);
        }

        if (betAmount > 0) {
            String betText = "[Bet: " + betAmount + "]";
            highlights.add(betText);
            highlightColors.add(Color.YELLOW);
        }

        if (!highlights.isEmpty()) {
            textPanel.setHighlightColorsInLastPara(highlightColors.toArray(new Color[0]));
            textPanel.highlightInLastPara(highlights.toArray(new String[0]));
        }
    }

    private static String[] extractHpValues(String logEntry) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+)/(\\d+) HP");
        java.util.regex.Matcher matcher = pattern.matcher(logEntry);
        if (matcher.find()) {
            return new String[]{matcher.group(1), matcher.group(2)};
        }
        return null;
    }

    private static Color getHpColor(int currentHp, int maxHp) {
        if (maxHp <= 0) return Color.WHITE;
        float percentage = (float) currentHp / (float) maxHp;
        if (percentage > 0.7f) {
            return new Color(50, 255, 50);
        } else if (percentage > 0.3f) {
            return Color.YELLOW;
        } else {
            return new Color(255, 50, 50);
        }
    }

    private static void processCritLog(String logEntry, TextPanelAPI textPanel, List<SpiralAbyssArena.SpiralGladiator> combatants) {
        textPanel.addPara(logEntry, Color.YELLOW);

        List<String> highlights = new ArrayList<>();
        List<Color> highlightColors = new ArrayList<>();

        SpiralAbyssArena.SpiralGladiator attacker = findAttacker(logEntry, combatants);
        if (attacker != null) {
            highlights.add(attacker.shortName);
            highlightColors.add(SHIP_NAME_COLOR);
        }

        SpiralAbyssArena.SpiralGladiator target = findTarget(logEntry, combatants);
        if (target != null) {
            highlights.add(target.shortName);
            highlightColors.add(SHIP_NAME_COLOR);
        }

        String damage = extractDamageNumber(logEntry);
        if (damage != null) {
            highlights.add(damage);
            highlightColors.add(CRIT_DAMAGE_COLOR);
        }

        if (!highlights.isEmpty()) {
            textPanel.setHighlightColorsInLastPara(highlightColors.toArray(new Color[0]));
            textPanel.highlightInLastPara(highlights.toArray(new String[0]));
        }
    }

    private static void processAttackLog(String logEntry, TextPanelAPI textPanel, List<SpiralAbyssArena.SpiralGladiator> combatants) {
        textPanel.addPara(logEntry, Color.WHITE);

        List<String> highlights = new ArrayList<>();
        List<Color> highlightColors = new ArrayList<>();

        SpiralAbyssArena.SpiralGladiator attacker = findAttacker(logEntry, combatants);
        if (attacker != null) {
            highlights.add(attacker.shortName);
            highlightColors.add(SHIP_NAME_COLOR);
        }

        SpiralAbyssArena.SpiralGladiator target = findTarget(logEntry, combatants);
        if (target != null) {
            highlights.add(target.shortName);
            highlightColors.add(SHIP_NAME_COLOR);
        }

        String damage = extractDamageNumber(logEntry);
        if (damage != null) {
            highlights.add(damage);
            highlightColors.add(DAMAGE_COLOR);
        }

        if (!highlights.isEmpty()) {
            textPanel.setHighlightColorsInLastPara(highlightColors.toArray(new Color[0]));
            textPanel.highlightInLastPara(highlights.toArray(new String[0]));
        }
    }

    private static void processMissLog(String logEntry, TextPanelAPI textPanel, List<SpiralAbyssArena.SpiralGladiator> combatants) {
        textPanel.addPara(logEntry, Color.GRAY);

        List<String> highlights = new ArrayList<>();
        List<Color> highlightColors = new ArrayList<>();

        SpiralAbyssArena.SpiralGladiator attacker = findAttacker(logEntry, combatants);
        if (attacker != null) {
            highlights.add(attacker.shortName);
            highlightColors.add(SHIP_NAME_COLOR);
        }

        SpiralAbyssArena.SpiralGladiator target = findTarget(logEntry, combatants);
        if (target != null) {
            highlights.add(target.shortName);
            highlightColors.add(SHIP_NAME_COLOR);
        }

        if (!highlights.isEmpty()) {
            textPanel.setHighlightColorsInLastPara(highlightColors.toArray(new Color[0]));
            textPanel.highlightInLastPara(highlights.toArray(new String[0]));
        }
    }

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

    private static SpiralAbyssArena.SpiralGladiator findAttacker(String logEntry, List<SpiralAbyssArena.SpiralGladiator> combatants) {
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

    private static SpiralAbyssArena.SpiralGladiator findTarget(String logEntry, List<SpiralAbyssArena.SpiralGladiator> combatants) {
        SpiralAbyssArena.SpiralGladiator firstShip = null;
        SpiralAbyssArena.SpiralGladiator secondShip = null;
        int firstIndex = Integer.MAX_VALUE;
        int secondIndex = Integer.MAX_VALUE;

        for (SpiralAbyssArena.SpiralGladiator ship : combatants) {
            int index = logEntry.indexOf(ship.shortName);
            if (index >= 0) {
                if (index < firstIndex) {
                    secondIndex = firstIndex;
                    secondShip = firstShip;
                    firstIndex = index;
                    firstShip = ship;
                } else if (index < secondIndex && index != firstIndex) {
                    secondIndex = index;
                    secondShip = ship;
                }
            }
        }

        return secondShip != null ? secondShip : firstShip;
    }

    private static String extractDamageNumber(String logEntry) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+) CRIT damage");
        java.util.regex.Matcher matcher = pattern.matcher(logEntry);
        if (matcher.find()) {
            return matcher.group(1);
        }

        pattern = java.util.regex.Pattern.compile("(\\d+) damage");
        matcher = pattern.matcher(logEntry);
        if (matcher.find()) {
            return matcher.group(1);
        }

        pattern = java.util.regex.Pattern.compile("dealing (\\d+) to");
        matcher = pattern.matcher(logEntry);
        if (matcher.find()) {
            return matcher.group(1);
        }

        pattern = java.util.regex.Pattern.compile("takes (\\d+) CRIT");
        matcher = pattern.matcher(logEntry);
        if (matcher.find()) {
            return matcher.group(1);
        }

        pattern = java.util.regex.Pattern.compile("with (\\d+) damage");
        matcher = pattern.matcher(logEntry);
        if (matcher.find()) {
            return matcher.group(1);
        }

        pattern = java.util.regex.Pattern.compile("for (\\d+)[!\\.]");
        matcher = pattern.matcher(logEntry);
        if (matcher.find()) {
            return matcher.group(1);
        }

        pattern = java.util.regex.Pattern.compile("for (\\d+)");
        matcher = pattern.matcher(logEntry);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    private static boolean containsDamage(String logEntry) {
        String lower = logEntry.toLowerCase();

        if (logEntry.matches(".*for \\d+ damage.*")) {
            return true;
        }

        if (logEntry.matches(".*with \\d+ damage.*")) {
            return true;
        }

        if (lower.contains("emotional damage")) {
            return true;
        }

        if (lower.contains("crit") || lower.contains("critical") || lower.contains("devastating")) {
            return true;
        }

        if (lower.contains("hits") || lower.contains("strikes") || lower.contains("blasts") ||
            lower.contains("slashes") || lower.contains("fires") || lower.contains("unleashes") ||
            lower.contains("rams") || lower.contains("sends") || lower.contains("dealt")) {
            return true;
        }

        return false;
    }

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

    private static boolean isCritMessage(String logEntry) {
        String lower = logEntry.toLowerCase();
        return lower.contains("critical") || lower.contains("crit!") || lower.contains("crit damage") ||
               lower.contains("nowhere to hide") || lower.contains("witness the stars shatter") ||
               lower.contains("disappear among the sea") || lower.contains("rules are made to be broken") ||
               lower.contains("feel the weight of a thousand failed gacha");
    }
}
