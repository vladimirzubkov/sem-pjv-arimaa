package cz.cvut.fel.pjv.arimaa.model;

/**
 * Validates a single step or a composed move against Arimaa rules (typically delegated to {@link DefaultRuleEngine}).
 */
public interface MoveValidator {

    /** Whether {@code step} is legal in {@code game}'s current PLAY position (phase-dependent). */
    boolean isValidStep(Step step, Game game);

    /** Whether the full {@code move} is legal as a committed turn in {@code game}. */
    boolean isValidMove(Move move, Game game);
}
