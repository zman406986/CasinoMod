package data.scripts.casino.interaction;

/**
 * Functional interface for handling string-based menu options in casino dialogs.
 * 
 * PATTERN USAGE:
 * This interface enables the command pattern for handling UI menu selections.
 * Each handler class (GachaHandler, PokerHandler, etc.) maintains a Map<String, OptionHandler>
 * that maps option IDs to their handling logic.
 * 
 * EXAMPLE:
 * <pre>
 * handlers.put("gacha_menu", option -> showGachaMenu());
 * handlers.put("pull_1", option -> performPull(1));
 * </pre>
 * 
 * PREDICATE HANDLERS:
 * For dynamic options (like "poker_raise_100"), use Map<Predicate<String>, OptionHandler>
 * to match option patterns rather than exact strings.
 * 
 * AI_AGENT NOTES:
 * - Always use lambda expressions for simple handlers
 * - For complex logic, delegate to private methods rather than inline
 * - Option IDs should follow naming convention: {feature}_{action}_{detail}
 * - Example: "poker_raise_100", "gacha_pull_10", "arena_bet_hammerhead"
 * - Handler lookup order: exact match first, then predicate handlers
 * 
 * @see CasinoInteraction
 * @see GachaHandler
 * @see PokerHandler
 * @see ArenaHandler
 * @see FinHandler
 * @see HelpHandler
 */
@FunctionalInterface
public interface OptionHandler {
    
    /**
     * Handles the selected menu option.
     * 
     * @param option The option ID string that was selected by the player
     *               This is the same string used as the key in the handlers map
     *               
     * AI_AGENT NOTE: The option parameter is passed for context but typically
     * not needed when using lambda expressions since the handler already knows
     * which option it handles. However, predicate handlers use this to extract
     * dynamic values (e.g., parsing "poker_raise_100" to get amount 100).
     */
    void handle(String option);
}
