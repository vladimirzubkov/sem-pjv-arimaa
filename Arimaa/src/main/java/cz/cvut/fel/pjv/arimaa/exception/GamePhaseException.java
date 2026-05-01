package cz.cvut.fel.pjv.arimaa.exception;

/**
 * Thrown when play-phase API is used while the game is not in {@link cz.cvut.fel.pjv.arimaa.model.GameState#PLAY}.
 */
public class GamePhaseException extends IllegalStateException {

    public GamePhaseException(String message) {
        super(message);
    }
}
