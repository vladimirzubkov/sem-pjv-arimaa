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
        if (game.getState() == GameState.GAME_OVER) {
            PlayerSide w = game.getMatchWinner();
            if (w == root) {
                return WIN_SCORE;
            }
            if (w != null) {
                return -WIN_SCORE;
            }
            return 0.0;
        }
        Board board = game.getBoard();
        boolean mirrored = game.isRanksMirroredForHomeCheck();
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
                    score += sign * developmentValue(p, pos, mirrored);
                }
            }
        }
        return score;
    }

    private static double developmentValue(Piece p, Position pos, boolean ranksMirrored) {
        if (HomeTerritory.contains(p.getSide(), pos, ranksMirrored)) {
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
