package cz.cvut.fel.pjv.arimaa.util;

import cz.cvut.fel.pjv.arimaa.model.Board;
import cz.cvut.fel.pjv.arimaa.model.DefaultRuleEngine;
import cz.cvut.fel.pjv.arimaa.model.Move;
import cz.cvut.fel.pjv.arimaa.model.Piece;
import cz.cvut.fel.pjv.arimaa.model.enums.PieceType;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;
import cz.cvut.fel.pjv.arimaa.model.Position;
import cz.cvut.fel.pjv.arimaa.model.Step;
import cz.cvut.fel.pjv.arimaa.model.enums.StepKind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArimaaNotationTest {

    @Test
    void positionToAlgebraic_roundTrip() {
        assertEquals("a1", Position.of(0, 0).toAlgebraic());
        assertEquals("h8", Position.of(7, 7).toAlgebraic());
        assertEquals(Position.of(3, 4), Position.fromAlgebraic("d5"));
    }

    @Test
    void buildBody_goldRabbitSlideNorth() {
        Board b = new Board();
        b.setPiece(Position.fromAlgebraic("a2"), new Piece(PieceType.RABBIT, PlayerSide.GOLD));
        Move m = new Move();
        Step s = new Step();
        s.setKind(StepKind.SLIDE);
        s.setFrom(Position.fromAlgebraic("a2"));
        s.setTo(Position.fromAlgebraic("a3"));
        m.getSteps().add(s);
        assertEquals("Ra2n", DefaultRuleEngine.buildArimaaNotationBody(b, m));
    }

    @Test
    void formatFullTurn_oneStep_hasPassSuffix() {
        Board b = new Board();
        b.setPiece(Position.fromAlgebraic("a2"), new Piece(PieceType.RABBIT, PlayerSide.GOLD));
        Move m = new Move();
        Step s = new Step();
        s.setKind(StepKind.SLIDE);
        s.setFrom(Position.fromAlgebraic("a2"));
        s.setTo(Position.fromAlgebraic("a3"));
        m.getSteps().add(s);
        String line = ArimaaNotation.formatFullTurn(b, m, "1g");
        assertTrue(line.startsWith("1g "));
        assertTrue(line.contains("... pass"), line);
        assertTrue(line.contains("Ra2n"));
    }

    @Test
    void formatPartialTurnLine_oneStep_noPassSuffix() {
        Board b = new Board();
        b.setPiece(Position.fromAlgebraic("a2"), new Piece(PieceType.RABBIT, PlayerSide.GOLD));
        Move m = new Move();
        Step s = new Step();
        s.setKind(StepKind.SLIDE);
        s.setFrom(Position.fromAlgebraic("a2"));
        s.setTo(Position.fromAlgebraic("a3"));
        m.getSteps().add(s);
        String line = ArimaaNotation.formatPartialTurnLine(b, m, "1g");
        assertEquals("1g Ra2n", line);
    }

    @Test
    void formatPartialTurnLineForPersist_fourSteps_hasDraftMarker() {
        Board b = new Board();
        b.setPiece(Position.fromAlgebraic("a2"), new Piece(PieceType.RABBIT, PlayerSide.GOLD));
        b.setPiece(Position.fromAlgebraic("b2"), new Piece(PieceType.RABBIT, PlayerSide.GOLD));
        b.setPiece(Position.fromAlgebraic("c2"), new Piece(PieceType.RABBIT, PlayerSide.GOLD));
        b.setPiece(Position.fromAlgebraic("d2"), new Piece(PieceType.RABBIT, PlayerSide.GOLD));
        Move m = new Move();
        addSlide(m, "a2", "a3");
        addSlide(m, "b2", "b3");
        addSlide(m, "c2", "c3");
        addSlide(m, "d2", "d3");
        String line = ArimaaNotation.formatPartialTurnLineForPersist(b, m, "1g");
        assertTrue(line.contains(ArimaaNotation.UNCOMMITTED_DRAFT_MARKER), line);
        assertTrue(line.startsWith("1g "));
        String preview = ArimaaNotation.formatPartialTurnLine(b, m, "1g");
        assertFalse(preview.contains("draft"), preview);
    }

    @Test
    void formatPartialTurnLineForPersist_oneStep_noDraftMarker() {
        Board b = new Board();
        b.setPiece(Position.fromAlgebraic("a2"), new Piece(PieceType.RABBIT, PlayerSide.GOLD));
        Move m = new Move();
        addSlide(m, "a2", "a3");
        String line = ArimaaNotation.formatPartialTurnLineForPersist(b, m, "1g");
        assertEquals("1g Ra2n", line);
    }

    private static void addSlide(Move m, String from, String to) {
        Step s = new Step();
        s.setKind(StepKind.SLIDE);
        s.setFrom(Position.fromAlgebraic(from));
        s.setTo(Position.fromAlgebraic(to));
        m.getSteps().add(s);
    }
}
