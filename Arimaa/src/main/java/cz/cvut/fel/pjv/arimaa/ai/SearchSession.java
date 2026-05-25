package cz.cvut.fel.pjv.arimaa.ai;

import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.GridMoveRules;
import cz.cvut.fel.pjv.arimaa.model.Move;
import cz.cvut.fel.pjv.arimaa.model.enums.GameState;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

/**
 * CPU search over one {@link SearchGrid}: apply/undo full turns without board snapshots.
 * Move generation reads {@code Piece[64]} directly. Callers must restore the source {@link Game} from a
 * memento after search ({@link CpuMoveSupport}) because grid apply/undo mutates shared {@link
 * cz.cvut.fel.pjv.arimaa.model.Piece} references.
 */
public final class SearchSession {

    private final SearchGrid grid;
    private final boolean ranksMirroredForHomeCheck;

    private SearchSession(SearchGrid grid, boolean ranksMirroredForHomeCheck) {
        this.grid = grid;
        this.ranksMirroredForHomeCheck = ranksMirroredForHomeCheck;
    }

    /** Wraps a live {@link Game} in a mutable search grid sharing piece references (CPU move search). */
    public static SearchSession fromGame(Game game) {
        Objects.requireNonNull(game, "game");
        return new SearchSession(SearchGrid.fromGame(game), game.isRanksMirroredForHomeCheck());
    }

    /**
     * Applies a legal full turn on the search grid and returns the delta for {@link #undoTurn}.
     */
    public UndoRecord applyTurn(Move move) {
        Objects.requireNonNull(move, "move");
        List<UndoRecord.TrapCapture> traps = new ArrayList<>();
        PlayerSide sideBefore = grid.sideToMove;
        GameState stateBefore = grid.state;
        PlayerSide winnerBefore = grid.matchWinner;

        PlayerSide mover = grid.sideToMove;
        if (!move.getSteps().isEmpty()) {
            grid.applyMoveSteps(move, traps);
        }
        finishTurnAfterSteps(mover);

        return new UndoRecord(List.copyOf(traps), sideBefore, stateBefore, winnerBefore);
    }

    /** Reverts {@code move} using the delta from {@link #applyTurn}. */
    public void undoTurn(Move move, UndoRecord undo) {
        Objects.requireNonNull(move, "move");
        Objects.requireNonNull(undo, "undo");

        grid.sideToMove = undo.sideToMoveBefore();
        grid.state = undo.stateBefore();
        grid.matchWinner = undo.matchWinnerBefore();

        grid.restoreTrapCaptures(undo.trapCaptures());
        if (!move.getSteps().isEmpty()) {
            grid.undoMoveSteps(move);
        }
    }

    /** All legal full turns from the search grid (CPU greedy / alpha-beta); delegates to {@link GridMoveRules}. */
    public List<Move> enumerateLegalMoves() {
        if (grid.state != GameState.PLAY) {
            return List.of();
        }
        return GridMoveRules.enumerateLegalCompleteMoves(grid.cells, grid.sideToMove);
    }

    /** One random legal full turn on the grid; used by {@link GreedyComputerMove} sampling loops. */
    public Optional<Move> sampleRandomLegalMove(Random random) {
        Objects.requireNonNull(random, "random");
        if (grid.state != GameState.PLAY) {
            return Optional.empty();
        }
        return GridMoveRules.sampleRandomLegalCompleteMove(grid.cells, grid.sideToMove, random);
    }

    public double evaluateForRoot(PlayerSide root) {
        return HeuristicEvaluation.evaluateForRoot(grid, root, ranksMirroredForHomeCheck);
    }

    public PlayerSide sideToMove() {
        return grid.sideToMove;
    }

    public GameState state() {
        return grid.state;
    }

    /** Copies the search grid into a live {@link Game} (tests and post-search UI sync). */
    public void writeBackTo(Game game) {
        Objects.requireNonNull(game, "game");
        grid.writeBoardTo(game.getBoard());
        game.setSideToMove(grid.sideToMove);
        game.setState(grid.state);
        game.setMatchWinner(grid.matchWinner);
    }

    /* Updates side to move, terminal state, and immobilization loss after steps are applied on the grid. */
    private void finishTurnAfterSteps(PlayerSide mover) {
        PlayerSide terminal = grid.evaluateTerminalWinner();
        if (terminal != null) {
            grid.state = GameState.GAME_OVER;
            grid.matchWinner = terminal;
            return;
        }
        grid.sideToMove = opponent(mover);
        grid.state = GameState.PLAY;
        grid.matchWinner = null;

        if (!GridMoveRules.existsLegalTurn(grid.cells, grid.sideToMove)) {
            grid.state = GameState.GAME_OVER;
            grid.matchWinner = mover;
        }
    }

    private static PlayerSide opponent(PlayerSide side) {
        return side == PlayerSide.GOLD ? PlayerSide.SILVER : PlayerSide.GOLD;
    }
}
