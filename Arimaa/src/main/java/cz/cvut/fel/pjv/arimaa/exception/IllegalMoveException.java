package cz.cvut.fel.pjv.arimaa.exception;

/**
 * Thrown when a move or turn prefix violates Arimaa play rules (slides, push/pull, step count, freezing, etc.).
 */
public class IllegalMoveException extends IllegalArgumentException {

    public IllegalMoveException(String message) {
        super(message);
    }
}
