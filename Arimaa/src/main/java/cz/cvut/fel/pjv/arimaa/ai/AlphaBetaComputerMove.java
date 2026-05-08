package cz.cvut.fel.pjv.arimaa.ai;

import cz.cvut.fel.pjv.arimaa.model.DefaultRuleEngine;
import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.GameMemento;
import cz.cvut.fel.pjv.arimaa.model.Move;
import cz.cvut.fel.pjv.arimaa.model.PlayHalfTurn;
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

    private AlphaBetaComputerMove() {}

    public static Move chooseMove(Game game, Random random) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(random, "random");
        if (game.getState() != GameState.PLAY) {
            throw new IllegalStateException("PLAY only");
        }
        List<Move> moves = DefaultRuleEngine.enumerateLegalCompleteMoves(game);
        if (moves.isEmpty()) {
            throw new IllegalStateException("no legal complete moves");
        }
        PlayerSide root = game.getSideToMove();
        int depth = moves.size() <= 28 ? MAX_DEPTH_FULL_TURNS : 1;
        SearchBudget budget = new SearchBudget(SEARCH_BUDGET_MS);
        List<Move> ordered = orderMovesForNode(moves, game.getSideToMove() == root);
        Move best = searchRootAtDepth(game, ordered, root, depth, random, budget);
        return PlayHalfTurn.copyMove(best);
    }

    /** Prefer longer compound turns first to improve alpha-beta cutoffs (cheap ordering). */
    private static List<Move> orderMovesForNode(List<Move> moves, boolean maximizing) {
        List<Move> copy = new ArrayList<>(moves);
        Comparator<Move> byLen =
                maximizing
                        ? Comparator.comparingInt((Move m) -> m.getSteps().size()).reversed()
                        : Comparator.comparingInt(m -> m.getSteps().size());
        copy.sort(byLen.thenComparingInt(m -> System.identityHashCode(m) & 0x7fff));
        return copy;
    }

    private static Move searchRootAtDepth(
            Game game,
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
            Move trial = PlayHalfTurn.copyMove(raw);
            GameMemento snap = game.createMemento();
            double score;
            try {
                game.applyMove(trial);
                score = minimax(game, depthFullTurns - 1, alpha, beta, root, budget);
            } finally {
                game.restoreMemento(snap);
            }
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
            Game game, int depthRemaining, double alpha, double beta, PlayerSide root, SearchBudget budget) {
        if (budget.isExpired()) {
            return HeuristicEvaluation.evaluateForRoot(game, root);
        }
        if (game.getState() == GameState.GAME_OVER) {
            return HeuristicEvaluation.evaluateForRoot(game, root);
        }
        if (depthRemaining <= 0) {
            return HeuristicEvaluation.evaluateForRoot(game, root);
        }
        List<Move> moves = DefaultRuleEngine.enumerateLegalCompleteMoves(game);
        if (moves.isEmpty()) {
            return HeuristicEvaluation.evaluateForRoot(game, root);
        }
        boolean maximizing = game.getSideToMove() == root;
        List<Move> ordered = orderMovesForNode(moves, maximizing);
        if (maximizing) {
            double v = -Double.MAX_VALUE;
            for (Move raw : ordered) {
                if (budget.isExpired()) {
                    return HeuristicEvaluation.evaluateForRoot(game, root);
                }
                Move trial = PlayHalfTurn.copyMove(raw);
                GameMemento snap = game.createMemento();
                try {
                    game.applyMove(trial);
                    v = Math.max(v, minimax(game, depthRemaining - 1, alpha, beta, root, budget));
                    alpha = Math.max(alpha, v);
                    if (beta <= alpha) {
                        break;
                    }
                } finally {
                    game.restoreMemento(snap);
                }
            }
            return v;
        }
        double v = Double.MAX_VALUE;
        for (Move raw : ordered) {
            if (budget.isExpired()) {
                return HeuristicEvaluation.evaluateForRoot(game, root);
            }
            Move trial = PlayHalfTurn.copyMove(raw);
            GameMemento snap = game.createMemento();
            try {
                game.applyMove(trial);
                v = Math.min(v, minimax(game, depthRemaining - 1, alpha, beta, root, budget));
                beta = Math.min(beta, v);
                if (beta <= alpha) {
                    break;
                }
            } finally {
                game.restoreMemento(snap);
            }
        }
        return v;
    }
}
