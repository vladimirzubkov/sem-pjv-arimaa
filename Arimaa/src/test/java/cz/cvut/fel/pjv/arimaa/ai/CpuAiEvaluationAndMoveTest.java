package cz.cvut.fel.pjv.arimaa.ai;

import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.GameMemento;
import cz.cvut.fel.pjv.arimaa.model.Move;
import cz.cvut.fel.pjv.arimaa.model.Piece;
import cz.cvut.fel.pjv.arimaa.model.Position;
import cz.cvut.fel.pjv.arimaa.model.enums.GameState;
import cz.cvut.fel.pjv.arimaa.model.enums.PieceType;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerControllerKind;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CpuAiEvaluationAndMoveTest {

    private static Game playChessOpening() {
        Game g = new Game();
        g.startNewGame();
        assertTrue(g.applyChessMappedSetup(PlayerSide.GOLD));
        assertTrue(g.tryCompleteSetup(PlayerSide.GOLD));
        assertTrue(g.applyChessMappedSetup(PlayerSide.SILVER));
        assertTrue(g.tryCompleteSetup(PlayerSide.SILVER));
        assertEquals(GameState.PLAY, g.getState());
        return g;
    }

    private static Game emptyPlay(PlayerSide side) {
        Game g = new Game();
        g.startNewGame();
        g.getBoard().clear();
        g.setState(GameState.PLAY);
        g.setSideToMove(side);
        return g;
    }

    @Test
    void heuristic_gameOver_favorsWinner() {
        Game g = new Game();
        g.startNewGame();
        g.setState(GameState.GAME_OVER);
        g.setMatchWinner(PlayerSide.GOLD);
        assertEquals(HeuristicEvaluation.WIN_SCORE, HeuristicEvaluation.evaluateForRoot(g, PlayerSide.GOLD), 1e-6);
        assertEquals(-HeuristicEvaluation.WIN_SCORE, HeuristicEvaluation.evaluateForRoot(g, PlayerSide.SILVER), 1e-6);
    }

    @Test
    void heuristic_goldRabbit_advanced_rankScoresHigher() {
        Game back = emptyPlay(PlayerSide.GOLD);
        back.getBoard().setPiece(Position.fromAlgebraic("a1"), new Piece(PieceType.RABBIT, PlayerSide.GOLD));
        Game front = emptyPlay(PlayerSide.GOLD);
        front.getBoard().setPiece(Position.fromAlgebraic("a8"), new Piece(PieceType.RABBIT, PlayerSide.GOLD));
        double sBack = HeuristicEvaluation.evaluateForRoot(back, PlayerSide.GOLD);
        double sFront = HeuristicEvaluation.evaluateForRoot(front, PlayerSide.GOLD);
        assertTrue(sFront > sBack);
    }

    @Test
    void greedy_returnsApplicableMove_onOpening() {
        Game g = playChessOpening();
        Game probe = Game.restoredFromMemento(GameMemento.fromGame(g));
        Move m = assertDoesNotThrow(() -> GreedyComputerMove.chooseMove(probe, new Random(7)));
        assertFalse(m.getSteps().isEmpty());
        Game trial = Game.restoredFromMemento(GameMemento.fromGame(g));
        assertDoesNotThrow(() -> trial.applyMove(m));
    }

    @Test
    void alphaBeta_returnsApplicableMove_onOpening() {
        Game g = playChessOpening();
        Game probe = Game.restoredFromMemento(GameMemento.fromGame(g));
        Move m = assertDoesNotThrow(() -> AlphaBetaComputerMove.chooseMove(probe, new Random(11)));
        assertFalse(m.getSteps().isEmpty());
        Game trial = Game.restoredFromMemento(GameMemento.fromGame(g));
        assertDoesNotThrow(() -> trial.applyMove(m));
    }

    @Test
    void computerPlayMove_rejectsHumanKind() {
        Game g = playChessOpening();
        assertThrows(
                IllegalArgumentException.class,
                () -> ComputerPlayMove.selectPlayMove(PlayerControllerKind.HUMAN, g, new Random(1)));
    }

    @Test
    void computerPlayMove_level1_dispatchesGreedy() {
        Game g = playChessOpening();
        Game probe = Game.restoredFromMemento(GameMemento.fromGame(g));
        Move m =
                ComputerPlayMove.selectPlayMove(PlayerControllerKind.COMPUTER_LEVEL_1, probe, new Random(3));
        assertFalse(m.getSteps().isEmpty());
    }

    @Test
    void computerPlayMove_level2_runsSearch() {
        Game g = playChessOpening();
        Game probe = Game.restoredFromMemento(GameMemento.fromGame(g));
        Move m =
                ComputerPlayMove.selectPlayMove(PlayerControllerKind.COMPUTER_LEVEL_2, probe, new Random(5));
        assertFalse(m.getSteps().isEmpty());
    }
}
