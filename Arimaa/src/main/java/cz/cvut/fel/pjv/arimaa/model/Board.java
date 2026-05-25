package cz.cvut.fel.pjv.arimaa.model;

import cz.cvut.fel.pjv.arimaa.util.BoardConstants;

/**
 * Mutable 8×8 grid of {@link Piece} references (Arimaa board geometry).
 * <p>
 * The model layer uses this type to answer “what is on square X?” and to
 * mutate setup or moves. Trap detection is delegated to static geometry in
 * {@link cz.cvut.fel.pjv.arimaa.util.BoardConstants} so board rules stay in one place.
 */
public class Board {

    private final Piece[][] squares = 
            new Piece[BoardConstants.BOARD_SIZE][BoardConstants.BOARD_SIZE];

    /**
     * Resets the board to an empty position (no pieces on any square).
     * <p>
     * Needed when starting a new game, loading a position, or replaying from a
     * known state so callers do not have to manually clear every coordinate.
     */
    public void clear() {
        for (int r = 0; r < BoardConstants.BOARD_SIZE; r++) {
            for (int f = 0; f < BoardConstants.BOARD_SIZE; f++) {
                Piece previous = squares[r][f];
                if (previous != null) {
                    previous.setPosition(null);
                }
                squares[r][f] = null;
            }
        }
    }

    /**
     * Returns the piece occupying the given square, or {@code null} if the square is empty.
     * <p>
     * Needed by move generation, capture logic, and the UI to render the current
     * position without duplicating the internal array representation.
     *
     * @param position board coordinates (rank/file indices); must not be {@code null}
     * @return the piece at that square, or {@code null} if none
     */
    public Piece getPiece(Position position) {
        if (!isInsideBoard(position.getRankIndex(), position.getFileIndex())) {
            throw new IllegalArgumentException("Position is outside the board");
        }
        return squares[position.getRankIndex()][position.getFileIndex()];
    }

    /**
     * Places a piece on a square, or clears the square if {@code piece} is {@code null}.
     * <p>
     * Needed when applying moves, promotions, or initial setup: the board is the
     * single source of truth for occupancy, so all mutations should go through here
     * (or future higher-level APIs that delegate here).
     *
     * @param position target square; must not be {@code null}
     * @param piece      piece to place, or {@code null} to clear the square
     */
    public void setPiece(Position position, Piece piece) {
        if (!isInsideBoard(position.getRankIndex(), position.getFileIndex())) {
            throw new IllegalArgumentException("Position is outside the board");
        }
        if (piece != null) {
            Piece previous = squares[position.getRankIndex()][position.getFileIndex()];
            if (previous != null) {
                previous.setPosition(null);
            }
            squares[position.getRankIndex()][position.getFileIndex()] = piece;
            piece.setPosition(position); // Update the piece's position
        } else {
            Piece previous = squares[position.getRankIndex()][position.getFileIndex()];
            if (previous != null) {
                previous.setPosition(null);
            }
            squares[position.getRankIndex()][position.getFileIndex()] = null;
        }
    }

    /**
     * Reports whether a square is a trap square (where a piece can be captured if
     * not adjacent to a friendly piece).
     * <p>
     * Needed by rule checks after moves and by tutorials; geometry matches
     * {@link cz.cvut.fel.pjv.arimaa.util.BoardConstants#trapSquares()}.
     *
     * @param position square to test; must not be {@code null}
     * @return {@code true} if that square is a trap under the project’s geometry
     */
    public boolean isTrapSquare(Position position) {
        for (Position trap : BoardConstants.trapSquares()) {
            if (trap.getRankIndex() == position.getRankIndex()
                    && trap.getFileIndex() == position.getFileIndex()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Convenience for “no piece here” without forcing callers to compare against {@code null}.
     * <p>
     * Needed for readability in move validation and search code; typically implemented
     * in terms of {@link #getPiece(Position)}.
     *
     * @param position square to test; must not be {@code null}
     * @return {@code true} if no piece occupies the square
     */
    public boolean isEmpty(Position position) {
        return getPiece(position) == null;
    }

    /**
     * Deep copy of the grid (new {@link Piece} instances per occupied square).
     * Used for rule previews without mutating match state.
     */
    public Board copy() {
        Board b = new Board();
        for (int r = 0; r < BoardConstants.BOARD_SIZE; r++) {
            for (int f = 0; f < BoardConstants.BOARD_SIZE; f++) {
                Position p = Position.of(f, r);
                Piece piece = squares[r][f];
                if (piece != null) {
                    b.setPiece(p, new Piece(piece.getType(), piece.getSide()));
                }
            }
        }
        return b;
    }

    /* Bounds check for rank/file before array access in {@link #getPiece} / {@link #setPiece}. */
    private static boolean isInsideBoard(int rankIndex, int fileIndex) {
    return rankIndex >= 0 && rankIndex < BoardConstants.BOARD_SIZE
            && fileIndex >= 0 && fileIndex < BoardConstants.BOARD_SIZE;
    }
}
