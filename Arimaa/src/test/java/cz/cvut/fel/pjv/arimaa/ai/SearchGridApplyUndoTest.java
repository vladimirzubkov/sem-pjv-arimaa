package cz.cvut.fel.pjv.arimaa.ai;

import cz.cvut.fel.pjv.arimaa.model.DefaultRuleEngine;
import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.GameMemento;
import cz.cvut.fel.pjv.arimaa.model.Move;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchGridApplyUndoTest {

    private static Game playChessOpening() {
        Game g = new Game();
        g.startNewGame();
        g.applyChessMappedSetup(PlayerSide.GOLD);
        g.tryCompleteSetup(PlayerSide.GOLD);
        g.applyChessMappedSetup(PlayerSide.SILVER);
        g.tryCompleteSetup(PlayerSide.SILVER);
        return g;
    }

    @Test
    void applyUndo_restoresBoardAndMatchFields() {
        Game g = playChessOpening();
        GameMemento before = GameMemento.fromGame(g);
        List<Move> moves = DefaultRuleEngine.enumerateLegalCompleteMoves(g);
        Move move = moves.get(0);

        SearchSession session = SearchSession.fromGame(g);
        UndoRecord undo = session.applyTurn(move);
        session.undoTurn(move, undo);
        session.writeBackTo(g);

        GameMemento after = GameMemento.fromGame(g);
        assertMementoBoardAndPlayFieldsEqual(before, after);
    }

    @Test
    void applyTurn_matchesGameApplyMove() {
        Game g = playChessOpening();
        List<Move> moves = DefaultRuleEngine.enumerateLegalCompleteMoves(g);
        Random rnd = new Random(42);
        for (int i = 0; i < Math.min(12, moves.size()); i++) {
            Move move = moves.get(rnd.nextInt(moves.size()));

            Game engine = Game.restoredFromMemento(GameMemento.fromGame(g));
            engine.applyMove(copyMove(move));
            GameMemento afterEngine = GameMemento.fromGame(engine);

            Game sessionGame = Game.restoredFromMemento(GameMemento.fromGame(g));
            SearchSession session = SearchSession.fromGame(sessionGame);
            session.applyTurn(move);
            session.writeBackTo(sessionGame);
            GameMemento afterSession = GameMemento.fromGame(sessionGame);

            assertMementoBoardAndPlayFieldsEqual(afterEngine, afterSession);
        }
    }

    private static Move copyMove(Move src) {
        Move m = new Move();
        for (var s : src.getSteps()) {
            var t = new cz.cvut.fel.pjv.arimaa.model.Step();
            t.setFrom(s.getFrom());
            t.setTo(s.getTo());
            t.setKind(s.getKind());
            m.getSteps().add(t);
        }
        return m;
    }

    private static void assertMementoBoardAndPlayFieldsEqual(GameMemento a, GameMemento b) {
        assertEquals(a.state(), b.state());
        assertEquals(a.sideToMove(), b.sideToMove());
        assertEquals(a.matchWinner(), b.matchWinner());
        for (int r = 0; r < 8; r++) {
            for (int f = 0; f < 8; f++) {
                assertEquals(a.grid()[r][f], b.grid()[r][f], "cell %d,%d".formatted(f, r));
            }
        }
    }
}
