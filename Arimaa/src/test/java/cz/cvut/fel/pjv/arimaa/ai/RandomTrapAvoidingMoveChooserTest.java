package cz.cvut.fel.pjv.arimaa.ai;

import cz.cvut.fel.pjv.arimaa.model.DefaultRuleEngine;
import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.GameMemento;
import cz.cvut.fel.pjv.arimaa.model.Move;
import cz.cvut.fel.pjv.arimaa.model.Piece;
import cz.cvut.fel.pjv.arimaa.model.Position;
import cz.cvut.fel.pjv.arimaa.model.Step;
import cz.cvut.fel.pjv.arimaa.model.enums.GameState;
import cz.cvut.fel.pjv.arimaa.model.enums.PieceType;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomTrapAvoidingMoveChooserTest {

    private static Game playOnEmptyBoard(PlayerSide sideToMove) {
        Game g = new Game();
        g.startNewGame();
        g.getBoard().clear();
        g.setState(GameState.PLAY);
        g.setSideToMove(sideToMove);
        return g;
    }

    @Test
    void enumerateLegalCompleteMoves_includesTrapSacrificeTurn() {
        Game g = playOnEmptyBoard(PlayerSide.GOLD);
        Position b3 = Position.fromAlgebraic("b3");
        Position c3 = Position.fromAlgebraic("c3");
        g.getBoard().setPiece(b3, new Piece(PieceType.RABBIT, PlayerSide.GOLD));
        g.getBoard().setPiece(c3, new Piece(PieceType.RABBIT, PlayerSide.GOLD));
        Move trapSacrifice = new Move();
        Step s = new Step();
        s.setFrom(b3);
        s.setTo(Position.of(1, 3));
        trapSacrifice.getSteps().add(s);
        List<Move> all = DefaultRuleEngine.enumerateLegalCompleteMoves(g);
        assertTrue(all.stream()
                .anyMatch(m -> m.getSteps().size() == 1
                        && m.getSteps().get(0).getFrom().equals(b3)
                        && m.getSteps().get(0).getTo().equals(Position.of(1, 3))));
        DefaultRuleEngine.TrapCapturePreview p = DefaultRuleEngine.trapCapturesIfPrefixApplied(g, trapSacrifice);
        assertFalse(p.bySilver().isEmpty(), "Gold rabbit on trap should be credited to Silver");
    }

    @Test
    void trapAvoidingPool_excludesSacrificeWhenSafeAlternativesExist() {
        Game g = playOnEmptyBoard(PlayerSide.GOLD);
        Position b3 = Position.fromAlgebraic("b3");
        Position c3 = Position.fromAlgebraic("c3");
        Position a1 = Position.fromAlgebraic("a1");
        g.getBoard().setPiece(b3, new Piece(PieceType.RABBIT, PlayerSide.GOLD));
        g.getBoard().setPiece(c3, new Piece(PieceType.RABBIT, PlayerSide.GOLD));
        g.getBoard().setPiece(a1, new Piece(PieceType.RABBIT, PlayerSide.GOLD));
        List<Move> all = DefaultRuleEngine.enumerateLegalCompleteMoves(g);
        List<Move> safe = all.stream()
                .filter(m -> !RandomTrapAvoidingMoveChooser.losesOwnPieceToTrap(
                        PlayerSide.GOLD, DefaultRuleEngine.trapCapturesIfPrefixApplied(g, m)))
                .toList();
        assertFalse(safe.isEmpty());
        assertTrue(safe.size() < all.size());
    }

    @Test
    void sampleRandomLegalCompleteMove_returnsApplicableFullTurn() {
        Game g = playOnEmptyBoard(PlayerSide.GOLD);
        g.getBoard().setPiece(Position.fromAlgebraic("a1"), new Piece(PieceType.RABBIT, PlayerSide.GOLD));
        Optional<Move> opt = DefaultRuleEngine.sampleRandomLegalCompleteMove(g, new Random(123));
        assertTrue(opt.isPresent());
        Move mv = opt.get();
        assertFalse(mv.getSteps().isEmpty());
        Game probe = Game.restoredFromMemento(GameMemento.fromGame(g));
        assertDoesNotThrow(() -> probe.applyMove(mv));
    }

    @Test
    void sampleRandomLegalCompleteMove_oftenMultiStepFromStandardOpening() {
        Game g = new Game();
        g.startNewGame();
        assertTrue(g.applyChessMappedSetup(PlayerSide.GOLD));
        assertTrue(g.tryCompleteSetup(PlayerSide.GOLD));
        assertTrue(g.applyChessMappedSetup(PlayerSide.SILVER));
        assertTrue(g.tryCompleteSetup(PlayerSide.SILVER));
        assertEquals(GameState.PLAY, g.getState());
        int multiStep = 0;
        for (int seed = 0; seed < 400; seed++) {
            Optional<Move> opt = DefaultRuleEngine.sampleRandomLegalCompleteMove(g, new Random(seed));
            assertTrue(opt.isPresent());
            if (opt.get().getSteps().size() > 1) {
                multiStep++;
            }
        }
        assertTrue(multiStep >= 30, "sampler should explore extensions, not stop at one-step completions only");
    }

    @Test
    void chooseMove_prefersTrapSafeWhenAvailable() {
        Game g = playOnEmptyBoard(PlayerSide.GOLD);
        g.getBoard().setPiece(Position.fromAlgebraic("b3"), new Piece(PieceType.RABBIT, PlayerSide.GOLD));
        g.getBoard().setPiece(Position.fromAlgebraic("c3"), new Piece(PieceType.RABBIT, PlayerSide.GOLD));
        g.getBoard().setPiece(Position.fromAlgebraic("a1"), new Piece(PieceType.RABBIT, PlayerSide.GOLD));
        Random r = new Random(42);
        for (int i = 0; i < 80; i++) {
            Move m = RandomTrapAvoidingMoveChooser.chooseMove(g, r);
            assertFalse(
                    RandomTrapAvoidingMoveChooser.losesOwnPieceToTrap(
                            PlayerSide.GOLD, DefaultRuleEngine.trapCapturesIfPrefixApplied(g, m)));
        }
    }
}
