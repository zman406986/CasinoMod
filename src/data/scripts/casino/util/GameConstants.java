package data.scripts.casino.util;

/**
 * Constants used across the casino mod for improved readability and maintainability
 */
public class GameConstants {
    
    // Arena-related constants
    public static final int MAX_ARENA_SHIPS = 12;  // Maximum number of ships in arena
    public static final int MIN_ARENA_SHIPS = 2;   // Minimum number of ships for a valid battle
    
    // Poker-related constants
    public static final int MAX_PLAYERS_POKER = 6; // Maximum players in a poker game
    public static final int MIN_PLAYERS_POKER = 2; // Minimum players for a poker game
    public static final int HAND_SIZE_HOLDEM = 2;  // Cards per player in Texas Hold'em
    
    // Gacha-related constants
    public static final int MAX_PULLS_BEFORE_GUARANTEE = 90; // Max pulls before guaranteed 5-star
    public static final int MAX_PULLS_BEFORE_4STAR_GUARANTEE = 10; // Max pulls before guaranteed 4-star
    
    // Betting-related constants
    public static final int MIN_BET_AMOUNT = 1;    // Minimum bet amount allowed
    public static final int MAX_BET_PERCENTAGE = 100; // Maximum bet as percentage of stack
    
    // UI-related constants
    public static final int UI_PANEL_WIDTH_DEFAULT = 800;  // Default UI panel width
    public static final int UI_PANEL_HEIGHT_DEFAULT = 600; // Default UI panel height
    
    // Color constants for UI consistency
    public static final java.awt.Color POSITIVE_COLOR = java.awt.Color.GREEN;
    public static final java.awt.Color NEGATIVE_COLOR = java.awt.Color.RED;
    public static final java.awt.Color NEUTRAL_COLOR = java.awt.Color.WHITE;
    public static final java.awt.Color HIGHLIGHT_COLOR = java.awt.Color.YELLOW;
    public static final java.awt.Color DAMAGE_COLOR = java.awt.Color.RED;
    
    // Game state constants
    public static final String STATE_ACTIVE = "ACTIVE";
    public static final String STATE_FINISHED = "FINISHED";
    public static final String STATE_PAUSED = "PAUSED";
    public static final String STATE_WAITING = "WAITING";
    
    // Arena event constants
    public static final float DEFAULT_CHAOS_EVENT_CHANCE = 0.15f;  // 15% chance of chaos event
    public static final int DEFAULT_EVENT_DURATION = 1;            // Default duration of events
    
    // Card game constants
    public static final int TOTAL_RANKS = 13;     // Number of card ranks (A, 2-10, J, Q, K)
    public static final int SUITS_COUNT = 4;      // Number of suits (hearts, diamonds, clubs, spades)
    public static final int CARDS_IN_DECK = 52;   // Total cards in a standard deck
    public static final int COMMUNITY_CARDS_MAX = 5; // Max community cards in Texas Hold'em
    
    // Performance-related constants
    public static final int MAX_MONTE_CARLO_SAMPLES = 1000; // Max samples for Monte Carlo simulations
    public static final int DEFAULT_MONTE_CARLO_SAMPLES = 50; // Default samples for Monte Carlo simulations
}