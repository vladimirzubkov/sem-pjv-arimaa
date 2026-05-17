package cz.cvut.fel.pjv.arimaa.ai;

import cz.cvut.fel.pjv.arimaa.model.Piece;
import cz.cvut.fel.pjv.arimaa.model.enums.GameState;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;

import java.util.List;

/**
 * Minimal delta to revert one full PLAY turn on a {@link SearchGrid}: trap removals plus match metadata
 * before {@link SearchSession#applyTurn}.
 */
public record UndoRecord(
        List<TrapCapture> trapCaptures,
        PlayerSide sideToMoveBefore,
        GameState stateBefore,
        PlayerSide matchWinnerBefore) {

    /** Piece removed from a trap square during the turn (restored on undo). */
    public record TrapCapture(int squareIndex, Piece piece) {}
}
