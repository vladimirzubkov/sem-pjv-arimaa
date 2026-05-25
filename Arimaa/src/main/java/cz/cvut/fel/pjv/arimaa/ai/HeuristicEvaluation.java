package cz.cvut.fel.pjv.arimaa.ai;

import cz.cvut.fel.pjv.arimaa.model.Board;
import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.Move;
import cz.cvut.fel.pjv.arimaa.model.Piece;
import cz.cvut.fel.pjv.arimaa.model.Position;
import cz.cvut.fel.pjv.arimaa.model.enums.GameState;
import cz.cvut.fel.pjv.arimaa.model.enums.PieceType;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;
import cz.cvut.fel.pjv.arimaa.util.BoardConstants;
import cz.cvut.fel.pjv.arimaa.util.HomeTerritory;

/**
 * Static board heuristic from {@code root}'s perspective (higher is better for {@code root}).
 */
public final class HeuristicEvaluation {

    public static final double WIN_SCORE = 1_000_000.0;
    private static final double RABBIT_RANK_WEIGHT = 5.0;
    private static final double TURN_STEP_WEIGHT = 11.0;

    private HeuristicEvaluation() {}

    /** Piece weights for static evaluation; used by {@link #evaluateForRoot} and tests. */
    static int pieceMaterial(PieceType type) {
        return switch (type) {
            case ELEPHANT -> 600;
            case CAMEL -> 450;
            case HORSE -> 320;
            case DOG -> 220;
            case CAT -> 160;
            case RABBIT -> 85;
        };
    }

    /** Small tie-break / preference so multi-step turns are not always discarded against equal static eval. */
    public static double turnShapeBonus(Move move) {
        return TURN_STEP_WEIGHT * move.getSteps().size();
    }

    /**
     * Heuristic utility for {@code root}: material, modest rabbit advance, development (non-rabbits off home), terminal
     * wins.
     */
    public static double evaluateForRoot(Game game, PlayerSide root) {
        return evaluateForRoot(
                game.getState(), game.getMatchWinner(), game.getBoard(), game.isRanksMirroredForHomeCheck(), root);
    }

    /** Same heuristic as {@link #evaluateForRoot(Game, PlayerSide)} on a flat {@link SearchGrid} (CPU search). */
    static double evaluateForRoot(SearchGrid grid, PlayerSide root, boolean ranksMirrored) {
        return evaluateForRoot(grid.state, grid.matchWinner, grid.cells, ranksMirrored, root);
    }

    /* Core static eval over {@code Piece[64]}; shared by {@link #evaluateForRoot(SearchGrid, PlayerSide, boolean)}. */
    private static double evaluateForRoot(
            GameState state,
            PlayerSide matchWinner,
            Piece[] cells,
            boolean ranksMirrored,
            PlayerSide root) {
        if (state == GameState.GAME_OVER) {
            if (matchWinner == root) {
                return WIN_SCORE;
            }
            if (matchWinner != null) {
                return -WIN_SCORE;
            }
            return 0.0;
        }
        double score = 0.0;
        // Flat cell scan: same material/rabbit/development terms as the Board overload, fewer allocations.
        for (int idx = 0; idx < SearchGrid.CELL_COUNT; idx++) {
            Piece p = cells[idx];
            if (p == null) {
                continue;
            }
            int r = idx / BoardConstants.BOARD_SIZE;
            int sign = p.getSide() == root ? 1 : -1;
            score += sign * pieceMaterial(p.getType());
            if (p.getType() == PieceType.RABBIT) {
                score += sign * rabbitAdvanceBonus(p.getSide(), r);
            } else {
                score += sign * developmentValue(p, r, ranksMirrored);
            }
        }
        return score;
    }

    /* Core static eval over a live {@link Board}; used by {@link #evaluateForRoot(Game, PlayerSide)}. */
    private static double evaluateForRoot(
            GameState state,
            PlayerSide matchWinner,
            Board board,
            boolean ranksMirrored,
            PlayerSide root) {
        if (state == GameState.GAME_OVER) {
            if (matchWinner == root) {
                return WIN_SCORE;
            }
            if (matchWinner != null) {
                return -WIN_SCORE;
            }
            return 0.0;
        }
        double score = 0.0;
        for (int r = 0; r < BoardConstants.BOARD_SIZE; r++) {
            for (int f = 0; f < BoardConstants.BOARD_SIZE; f++) {
                Position pos = Position.of(f, r);
                Piece p = board.getPiece(pos);
                if (p == null) {
                    continue;
                }
                int sign = p.getSide() == root ? 1 : -1;
                score += sign * pieceMaterial(p.getType());
                if (p.getType() == PieceType.RABBIT) {
                    score += sign * rabbitAdvanceBonus(p.getSide(), r);
                } else {
                    score += sign * developmentValue(p, r, ranksMirrored);
                }
            }
        }
        return score;
    }

    /* Encourages strong pieces to leave home rows; slight home penalty for elephants/camel/horse/dog/cat. */
    private static double developmentValue(Piece p, int rankIndex, boolean ranksMirrored) {
        if (HomeTerritory.contains(p.getSide(), rankIndex, ranksMirrored)) {
            return switch (p.getType()) {
                case ELEPHANT -> -5;
                case CAMEL, HORSE -> -22;
                case DOG, CAT -> -16;
                default -> 0;
            };
        }
        return switch (p.getType()) {
            case ELEPHANT -> 8;
            case CAMEL, HORSE -> 26;
            case DOG, CAT -> 16;
            default -> 0;
        };
    }

    /** Rank index 0 = algebraic rank 1 (Gold home); rabbits advance toward the opposite edge. */
    static double rabbitAdvanceBonus(PlayerSide side, int rankIndex) {
        int progress =
                switch (side) {
                    case GOLD -> rankIndex;
                    case SILVER -> BoardConstants.BOARD_SIZE - 1 - rankIndex;
                };
        return RABBIT_RANK_WEIGHT * progress;
    }
}
