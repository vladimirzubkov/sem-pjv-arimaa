package cz.cvut.fel.pjv.arimaa.model;

import cz.cvut.fel.pjv.arimaa.util.BoardConstants;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Play-phase rules: simple orthogonal slides onto empty squares (no push/pull),
 * rabbit backward ban, trap resolution at end of turn, then side switch.
 */
public final class DefaultRuleEngine implements RuleEngine {

    @Override
    public void applyMove(Game game, Move move) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(move, "move");
        if (game.getState() != GameState.PLAY) {
            throw new IllegalStateException("applyMove only in PLAY");
        }
        validateSequentialSteps(game, move, true);
        Board board = game.getBoard();
        List<Step> steps = move.getSteps();
        for (Step step : steps) {
            applyOneStep(board, step);
        }
        resolveTraps(board);
        game.setSideToMove(opponent(game.getSideToMove()));
    }

    /**
     * Whether {@code move} is a legal prefix of a turn (0–4 steps): each step is orthogonal
     * onto an empty square, own piece, rabbit rule; no trap resolution or side flip.
     */
    public static boolean isValidPlayPrefix(Game game, Move move) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(move, "move");
        if (game.getState() != GameState.PLAY) {
            return false;
        }
        try {
            validateSequentialSteps(game, move, false);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static void validateSequentialSteps(Game game, Move move, boolean requireFullTurn) {
        List<Step> steps = move.getSteps();
        int n = steps.size();
        if (requireFullTurn) {
            if (n < 1 || n > 4) {
                throw new IllegalArgumentException("Turn must have 1–4 steps, got " + n);
            }
        } else {
            if (n < 0 || n > 4) {
                throw new IllegalArgumentException("Prefix may have at most 4 steps, got " + n);
            }
        }
        PlayerSide side = game.getSideToMove();
        Map<Position, Piece> occ = snapshotOccupancy(game.getBoard());
        for (Step step : steps) {
            validateOneStepOnOccupancy(occ, side, step);
            applyOneStepOnOccupancy(occ, step);
        }
    }

    private static Map<Position, Piece> snapshotOccupancy(Board board) {
        Map<Position, Piece> occ = new HashMap<>();
        for (int r = 0; r < BoardConstants.BOARD_SIZE; r++) {
            for (int f = 0; f < BoardConstants.BOARD_SIZE; f++) {
                Position p = Position.of(f, r);
                Piece piece = board.getPiece(p);
                if (piece != null) {
                    occ.put(p, piece);
                }
            }
        }
        return occ;
    }

    private static void validateOneStepOnOccupancy(Map<Position, Piece> occ, PlayerSide side, Step step) {
        Position from = step.getFrom();
        Position to = step.getTo();
        if (from == null || to == null) {
            throw new IllegalArgumentException("Step must have from and to");
        }
        if (!isOrthogonalNeighbor(from, to)) {
            throw new IllegalArgumentException("Step must be to an orthogonal neighbor");
        }
        Piece moving = occ.get(from);
        if (moving == null || moving.getSide() != side) {
            throw new IllegalArgumentException("No own piece at step origin");
        }
        if (occ.containsKey(to)) {
            throw new IllegalArgumentException("Destination must be empty");
        }
        if (moving.getType() == PieceType.RABBIT && isRabbitBackward(moving.getSide(), from, to)) {
            throw new IllegalArgumentException("Rabbit cannot move backward");
        }
    }

    private static void applyOneStepOnOccupancy(Map<Position, Piece> occ, Step step) {
        Piece moving = occ.remove(step.getFrom());
        if (moving != null) {
            occ.put(step.getTo(), moving);
        }
    }

    private static void applyOneStep(Board board, Step step) {
        Piece moving = board.getPiece(step.getFrom());
        board.setPiece(step.getFrom(), null);
        board.setPiece(step.getTo(), moving);
    }

    private static boolean isOrthogonalNeighbor(Position a, Position b) {
        int df = Math.abs(a.getFileIndex() - b.getFileIndex());
        int dr = Math.abs(a.getRankIndex() - b.getRankIndex());
        return df + dr == 1;
    }

    /**
     * Gold advances toward higher rank index; Silver toward lower.
     */
    private static boolean isRabbitBackward(PlayerSide side, Position from, Position to) {
        int fromR = from.getRankIndex();
        int toR = to.getRankIndex();
        return switch (side) {
            case GOLD -> toR < fromR;
            case SILVER -> toR > fromR;
        };
    }

    private static void resolveTraps(Board board) {
        for (Position trap : BoardConstants.trapSquares()) {
            Piece victim = board.getPiece(trap);
            if (victim == null) {
                continue;
            }
            if (!hasOrthogonalFriendly(board, victim.getSide(), trap)) {
                board.setPiece(trap, null);
            }
        }
    }

    private static boolean hasOrthogonalFriendly(Board board, PlayerSide side, Position pos) {
        int f = pos.getFileIndex();
        int r = pos.getRankIndex();
        int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] delta : d) {
            int nf = f + delta[0];
            int nr = r + delta[1];
            if (nf < 0 || nf >= BoardConstants.BOARD_SIZE || nr < 0 || nr >= BoardConstants.BOARD_SIZE) {
                continue;
            }
            Position n = Position.of(nf, nr);
            Piece p = board.getPiece(n);
            if (p != null && p.getSide() == side) {
                return true;
            }
        }
        return false;
    }

    private static PlayerSide opponent(PlayerSide side) {
        return side == PlayerSide.GOLD ? PlayerSide.SILVER : PlayerSide.GOLD;
    }
}
