package cz.cvut.fel.pjv.arimaa.model;

/**
 * Validates a single step or a composed move against Arimaa rules.
 */
public interface MoveValidator {

    boolean isValidStep(Step step, Game game);

    boolean isValidMove(Move move, Game game);
}
