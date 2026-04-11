package cz.cvut.fel.pjv.arimaa.model;

/**
 * Applies legal moves and side effects (traps, freezing) to the game state.
 */
public interface RuleEngine {

    void applyMove(Game game, Move move);
}
