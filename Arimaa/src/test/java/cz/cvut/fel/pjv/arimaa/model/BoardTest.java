package cz.cvut.fel.pjv.arimaa.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardTest {

    private Board board;
    private Position center;

    @BeforeEach
    void setUp() {
        board = new Board();
        center = Position.of(3, 3);
    }

    @Test
    void clearEmptiesAllSquares() {
        Piece p = new Piece(PieceType.RABBIT, PlayerSide.GOLD);
        board.setPiece(center, p);
        board.clear();
        assertTrue(board.isEmpty(center));
        assertNull(p.getPosition());
    }

    @Test
    void setPieceThenGetPieceReturnsSameInstance() {
        Piece piece = new Piece(PieceType.ELEPHANT, PlayerSide.GOLD);
        board.setPiece(center, piece);
        assertSame(piece, board.getPiece(center));
        assertSame(center, piece.getPosition());
    }

    @Test
    void setPieceNullClearsSquareAndPiecePosition() {
        Piece piece = new Piece(PieceType.CAT, PlayerSide.SILVER);
        board.setPiece(center, piece);
        board.setPiece(center, null);
        assertTrue(board.isEmpty(center));
        assertNull(piece.getPosition());
    }

    @Test
    void setPieceReplacingPreviousClearsOldPiecePosition() {
        Piece first = new Piece(PieceType.DOG, PlayerSide.GOLD);
        Piece second = new Piece(PieceType.HORSE, PlayerSide.GOLD);
        board.setPiece(center, first);
        board.setPiece(center, second);
        assertSame(second, board.getPiece(center));
        assertNull(first.getPosition());
        assertSame(center, second.getPosition());
    }

    @Test
    void isEmptyTrueForUnoccupiedSquare() {
        assertTrue(board.isEmpty(center));
    }

    @Test
    void isTrapSquareDoesNotThrow() {
        assertDoesNotThrow(() -> board.isTrapSquare(center));
    }
}
