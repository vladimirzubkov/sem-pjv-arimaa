package cz.cvut.fel.pjv.arimaa.exception;

import cz.cvut.fel.pjv.arimaa.model.enums.GameState;

/**
 * Thrown when play-phase API is used while the game is not in {@link GameState#PLAY}.
 */
public class GamePhaseException extends IllegalStateException {

    public GamePhaseException(String message) {
        super(message);
    }
}
