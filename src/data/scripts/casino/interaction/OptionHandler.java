package data.scripts.casino.interaction;

/**
 * Functional interface for handling menu option selections.
 * Used with Map&lt;String, OptionHandler&gt; for exact matches and
 * Map&lt;Predicate&lt;String&gt;, OptionHandler&gt; for pattern matching.
 */
@FunctionalInterface
public interface OptionHandler {
    
    void handle(String option);
}
