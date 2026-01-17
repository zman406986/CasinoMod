package data.scripts.casino.interaction;

/**
 * Functional interface for handling string-based options
 */
@FunctionalInterface
public interface OptionHandler {
    void handle(String option);
}