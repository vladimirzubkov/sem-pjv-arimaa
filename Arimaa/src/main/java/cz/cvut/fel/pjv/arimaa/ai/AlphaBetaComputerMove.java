package cz.cvut.fel.pjv.arimaa.ai;

import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.GameMemento;
import cz.cvut.fel.pjv.arimaa.model.Move;
import cz.cvut.fel.pjv.arimaa.model.enums.GameState;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Level-2 CPU: minimax with alpha-beta over full Arimaa turns. Depth is at most {@value #MAX_DEPTH_FULL_TURNS}; when
 * the root has many legal turns, depth is reduced to keep runtime reasonable. A hard wall-clock cap aborts deep
 * subtrees early (returns static eval) so a single move does not stall for tens of seconds.
 */
public final class AlphaBetaComputerMove {

    /** Maximum full-turn plies from the root (never more than this). */
    private static final int MAX_DEPTH_FULL_TURNS = 2;

    /** Do not spend longer than this on one search (worker thread); avoids multi‑second hangs in dense positions. */
    private static final long SEARCH_BUDGET_MS = 3500;

    private static final Comparator<Move> BY_LEN_MAX =
            Comparator.<Move>comparingInt(m -> m.getSteps().size())
                    .reversed()
                    .thenComparingInt(m -> System.identityHashCode(m) & 0x7fff);

    private static final Comparator<Move> BY_LEN_MIN =
            Comparator.comparingInt((Move m) -> m.getSteps().size())
                    .thenComparingInt(m -> System.identityHashCode(m) & 0x7fff);

    private AlphaBetaComputerMove() {}

    public static Move chooseMove(Game game, Random random) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(random, "random");
        if (game.getState() != GameState.PLAY) {
            throw new IllegalStateException("PLAY only");
        }

        GameMemento baseline = CpuMoveSupport.baseline(game);
        SearchSession session = SearchSession.fromGame(game);
        List<Move> moves = session.enumerateLegalMoves();
        if (moves.isEmpty()) {
            throw new IllegalStateException("no legal complete moves");
        }

        PlayerSide root = game.getSideToMove();
        int depth = moves.size() <= 28 ? MAX_DEPTH_FULL_TURNS : 1;
        SearchBudget budget = new SearchBudget(SEARCH_BUDGET_MS);
        orderMovesForNodeInPlace(moves, game.getSideToMove() == root);
        Move best = searchRootAtDepth(session, moves, root, depth, random, budget);
        CpuMoveSupport.restoreBaseline(game, baseline);
        return CpuMoveSupport.engineLegalCopy(baseline, best, random);
    }

    /** Prefer longer compound turns first to improve alpha-beta cutoffs (cheap ordering). */
    private static void orderMovesForNodeInPlace(List<Move> moves, boolean maximizing) {
        moves.sort(maximizing ? BY_LEN_MAX : BY_LEN_MIN);
    }

    private static Move searchRootAtDepth(
            SearchSession session,
            List<Move> moves,
            PlayerSide root,
            int depthFullTurns,
            Random random,
            SearchBudget budget) {
        double alpha = -Double.MAX_VALUE;
        double beta = Double.MAX_VALUE;
        double bestScore = -Double.MAX_VALUE;
        List<Move> tied = new ArrayList<>();
        for (Move raw : moves) {
            if (budget.isExpired()) {
                break;
            }
            UndoRecord undo = session.applyTurn(raw);
            double score = minimax(session, depthFullTurns - 1, alpha, beta, root, budget);
            session.undoTurn(raw, undo);
            score += HeuristicEvaluation.turnShapeBonus(raw);
            int cmp = Double.compare(score, bestScore);
            if (cmp > 0) {
                bestScore = score;
                tied.clear();
                tied.add(raw);
                alpha = Math.max(alpha, bestScore);
            } else if (cmp == 0) {
                tied.add(raw);
            }
        }
        if (tied.isEmpty()) {
            return moves.get(0);
        }
        return tied.get(random.nextInt(tied.size()));
    }

    private static double minimax(
            SearchSession session,
            int depthRemaining,
            double alpha,
            double beta,
            PlayerSide root,
            SearchBudget budget) {
        if (budget.isExpired()) {
            return session.evaluateForRoot(root);
        }
        if (session.state() == GameState.GAME_OVER) {
            return session.evaluateForRoot(root);
        }
        if (depthRemaining <= 0) {
            return session.evaluateForRoot(root);
        }

        List<Move> moves = session.enumerateLegalMoves();
        if (moves.isEmpty()) {
            return session.evaluateForRoot(root);
        }

        boolean maximizing = session.sideToMove() == root;
        orderMovesForNodeInPlace(moves, maximizing);
        if (maximizing) {
            double v = -Double.MAX_VALUE;
            for (Move raw : moves) {
                if (budget.isExpired()) {
                    return session.evaluateForRoot(root);
                }
                UndoRecord undo = session.applyTurn(raw);
                v = Math.max(v, minimax(session, depthRemaining - 1, alpha, beta, root, budget));
                session.undoTurn(raw, undo);
                alpha = Math.max(alpha, v);
                if (beta <= alpha) {
                    break;
                }
            }
            return v;
        }

        double v = Double.MAX_VALUE;
        for (Move raw : moves) {
            if (budget.isExpired()) {
                return session.evaluateForRoot(root);
            }
            UndoRecord undo = session.applyTurn(raw);
            v = Math.min(v, minimax(session, depthRemaining - 1, alpha, beta, root, budget));
            session.undoTurn(raw, undo);
            beta = Math.min(beta, v);
            if (beta <= alpha) {
                break;
            }
        }
        return v;
    }
}
