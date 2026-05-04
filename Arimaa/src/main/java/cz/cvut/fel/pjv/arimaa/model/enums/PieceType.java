package cz.cvut.fel.pjv.arimaa.model.enums;

/**
 * Piece strengths follow official Arimaa ordering (strongest to weakest).
 * {@link #notationChar()} is the single Latin letter used on the board and in the UI (not full Arimaa move notation).
 */
public enum PieceType {
    ELEPHANT('E'),
    CAMEL('M'),
    HORSE('H'),
    DOG('D'),
    CAT('K'),
    RABBIT('R');

    private final char notationChar;

    PieceType(char notationChar) {
        this.notationChar = notationChar;
    }

    /** One-letter label for this piece type (board cells, reserve, trap captures). */
    public char notationChar() {
        return notationChar;
    }
}
