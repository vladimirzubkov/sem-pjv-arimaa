package cz.cvut.fel.pjv.arimaa.ai;

import cz.cvut.fel.pjv.arimaa.model.Board;
import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.Move;
import cz.cvut.fel.pjv.arimaa.model.Piece;
import cz.cvut.fel.pjv.arimaa.model.Position;
import cz.cvut.fel.pjv.arimaa.model.Step;
import cz.cvut.fel.pjv.arimaa.model.enums.GameState;
import cz.cvut.fel.pjv.arimaa.model.enums.PieceType;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;
import cz.cvut.fel.pjv.arimaa.model.enums.StepKind;
import cz.cvut.fel.pjv.arimaa.util.BoardConstants;

import java.util.ArrayList;
import java.util.List;

import static cz.cvut.fel.pjv.arimaa.model.DefaultRuleEngine.kindOf;

/**
 * Single mutable 8×8 search board as {@code Piece[64]} (one allocation per search). Shares {@link Piece}
 * references with the live {@link Game} board when loaded via {@link #fromGame}.
 */
final class SearchGrid {

    static final int CELL_COUNT = BoardConstants.BOARD_SIZE * BoardConstants.BOARD_SIZE;

    private static final int[] TRAP_INDICES = trapIndices();

    final Piece[] cells = new Piece[CELL_COUNT];

    PlayerSide sideToMove;
    GameState state;
    PlayerSide matchWinner;

    private SearchGrid() {}

    static SearchGrid fromGame(Game game) {
        SearchGrid g = new SearchGrid();
        Board board = game.getBoard();
        for (int r = 0; r < BoardConstants.BOARD_SIZE; r++) {
            for (int f = 0; f < BoardConstants.BOARD_SIZE; f++) {
                g.cells[index(f, r)] = board.getPiece(Position.of(f, r));
            }
        }
        g.sideToMove = game.getSideToMove();
        g.state = game.getState();
        g.matchWinner = game.getMatchWinner();
        return g;
    }

    void writeBoardTo(Board board) {
        board.clear();
        for (int idx = 0; idx < CELL_COUNT; idx++) {
            Piece p = cells[idx];
            if (p != null) {
                board.setPiece(position(idx), p);
            }
        }
    }

    void applyMoveSteps(Move move, List<UndoRecord.TrapCapture> trapOut) {
        List<Step> steps = move.getSteps();
        for (int i = 0; i < steps.size(); i++) {
            Step s = steps.get(i);
            StepKind k = kindOf(s);
            if (k == StepKind.SLIDE) {
                movePiece(s.getFrom(), s.getTo());
                resolveTraps(trapOut);
                if (i + 1 < steps.size() && kindOf(steps.get(i + 1)) == StepKind.PULL_DRAG_WEAKER) {
                    i++;
                    Step pullDrag = steps.get(i);
                    movePiece(pullDrag.getFrom(), pullDrag.getTo());
                    resolveTraps(trapOut);
                }
            } else if (k == StepKind.PUSH_DISPLACE_WEAKER) {
                movePiece(s.getFrom(), s.getTo());
                resolveTraps(trapOut);
                i++;
                movePiece(steps.get(i).getFrom(), steps.get(i).getTo());
                resolveTraps(trapOut);
            } else if (k == StepKind.PULL_VACATE_STRONGER) {
                movePiece(s.getFrom(), s.getTo());
                resolveTraps(trapOut);
                i++;
                movePiece(steps.get(i).getFrom(), steps.get(i).getTo());
                resolveTraps(trapOut);
            }
        }
    }

    void undoMoveSteps(Move move) {
        List<Step> steps = move.getSteps();
        for (int i = steps.size() - 1; i >= 0; i--) {
            Step s = steps.get(i);
            movePiece(s.getTo(), s.getFrom());
        }
    }

    void restoreTrapCaptures(List<UndoRecord.TrapCapture> captures) {
        for (int i = captures.size() - 1; i >= 0; i--) {
            UndoRecord.TrapCapture c = captures.get(i);
            placePiece(c.squareIndex(), c.piece());
        }
    }

    /** Goal / no-rabbits terminal from current grid (null if play continues). */
    PlayerSide evaluateTerminalWinner() {
        if (hasRabbitOnRank(PlayerSide.GOLD, BoardConstants.BOARD_SIZE - 1)) {
            return PlayerSide.GOLD;
        }
        if (hasRabbitOnRank(PlayerSide.SILVER, 0)) {
            return PlayerSide.SILVER;
        }
        if (countRabbits(PlayerSide.GOLD) == 0) {
            return PlayerSide.SILVER;
        }
        if (countRabbits(PlayerSide.SILVER) == 0) {
            return PlayerSide.GOLD;
        }
        return null;
    }

    static int index(Position p) {
        return index(p.getFileIndex(), p.getRankIndex());
    }

    static int index(int file, int rank) {
        return rank * BoardConstants.BOARD_SIZE + file;
    }

    static Position position(int idx) {
        return Position.of(idx % BoardConstants.BOARD_SIZE, idx / BoardConstants.BOARD_SIZE);
    }

    private void movePiece(Position from, Position to) {
        movePiece(index(from), index(to));
    }

    private void movePiece(int from, int to) {
        Piece moving = cells[from];
        if (moving == null) {
            return;
        }
        cells[from] = null;
        cells[to] = moving;
        moving.setPosition(position(to));
    }

    private void placePiece(int idx, Piece piece) {
        cells[idx] = piece;
        piece.setPosition(position(idx));
    }

    private void resolveTraps(List<UndoRecord.TrapCapture> trapOut) {
        for (int trapIdx : TRAP_INDICES) {
            Piece victim = cells[trapIdx];
            if (victim == null) {
                continue;
            }
            if (!hasOrthogonalFriendly(trapIdx, victim.getSide())) {
                trapOut.add(new UndoRecord.TrapCapture(trapIdx, victim));
                cells[trapIdx] = null;
                victim.setPosition(null);
            }
        }
    }

    private boolean hasOrthogonalFriendly(int squareIndex, PlayerSide side) {
        int f = squareIndex % BoardConstants.BOARD_SIZE;
        int r = squareIndex / BoardConstants.BOARD_SIZE;
        if (f + 1 < BoardConstants.BOARD_SIZE) {
            if (isFriendly(cells[index(f + 1, r)], side)) {
                return true;
            }
        }
        if (f - 1 >= 0) {
            if (isFriendly(cells[index(f - 1, r)], side)) {
                return true;
            }
        }
        if (r + 1 < BoardConstants.BOARD_SIZE) {
            if (isFriendly(cells[index(f, r + 1)], side)) {
                return true;
            }
        }
        if (r - 1 >= 0) {
            if (isFriendly(cells[index(f, r - 1)], side)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFriendly(Piece p, PlayerSide side) {
        return p != null && p.getSide() == side;
    }

    private boolean hasRabbitOnRank(PlayerSide side, int rankIndex) {
        for (int f = 0; f < BoardConstants.BOARD_SIZE; f++) {
            Piece p = cells[index(f, rankIndex)];
            if (p != null && p.getSide() == side && p.getType() == PieceType.RABBIT) {
                return true;
            }
        }
        return false;
    }

    private int countRabbits(PlayerSide side) {
        int n = 0;
        for (int idx = 0; idx < CELL_COUNT; idx++) {
            Piece p = cells[idx];
            if (p != null && p.getSide() == side && p.getType() == PieceType.RABBIT) {
                n++;
            }
        }
        return n;
    }

    private static int[] trapIndices() {
        Position[] traps = BoardConstants.trapSquares();
        int[] out = new int[traps.length];
        for (int i = 0; i < traps.length; i++) {
            out[i] = index(traps[i]);
        }
        return out;
    }
}
