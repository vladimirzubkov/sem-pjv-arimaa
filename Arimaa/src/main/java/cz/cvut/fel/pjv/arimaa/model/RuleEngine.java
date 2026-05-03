package cz.cvut.fel.pjv.arimaa.model;

/**
 * Applies legal moves and side effects (traps, freezing) to the game state.
 */
public interface RuleEngine {

    /**
     * Executes a full PLAY turn (1–4 steps), resolves traps, checks goal/freeze/endgame, then switches
     * {@link Game#getSideToMove()}.
     */
    void applyMove(Game game, Move move);

    /**
     * Applies an incomplete PLAY-phase prefix (1–3 step entries in {@link Move#getSteps()}) without switching
     * {@link Game#getSideToMove()}; used when restoring a match whose saved notation ended mid-turn.
     */
    void applyPlayPrefix(Game game, Move prefix);
}
