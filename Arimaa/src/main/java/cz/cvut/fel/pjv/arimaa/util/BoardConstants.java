package cz.cvut.fel.pjv.arimaa.util;

import cz.cvut.fel.pjv.arimaa.model.Position;

/**
 * Static board geometry (trap squares in standard notation: c3, c6, f3, f6).
 */
public final class BoardConstants {

    public static final int BOARD_SIZE = 8;

    private BoardConstants() {
    }

    /**
     * Trap squares as rank/file indices (0-based). Not used in CP2 logic.
     */
    public static Position[] trapSquares() {
        return new Position[0];
    }
}
