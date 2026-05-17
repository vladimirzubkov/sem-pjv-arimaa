package cz.cvut.fel.pjv.arimaa.ui;

import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.Position;
import cz.cvut.fel.pjv.arimaa.model.enums.GameState;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;
import cz.cvut.fel.pjv.arimaa.util.BoardConstants;
import javafx.scene.input.KeyCode;

import java.util.function.BooleanSupplier;

/**
 * Maps between model coordinates (file/rank) and visual grid indices for the board view, depending on whether Gold is
 * shown at the bottom, optional “rotate board to mover”, and in an active network session (with rotation off) which
 * seat is local (host = Gold at bottom, client = Silver at bottom).
 */
public final class BoardViewOrientation {

    private final BooleanSupplier rotateBoardToMoverSelected;
    private final BooleanSupplier networkSessionActive;
    /** When {@link #networkSessionActive} is true: {@code true} if this app is the network host (Gold seat). */
    private final BooleanSupplier networkLocalIsGoldSeat;

    public BoardViewOrientation(
            BooleanSupplier rotateBoardToMoverSelected,
            BooleanSupplier networkSessionActive,
            BooleanSupplier networkLocalIsGoldSeat) {
        this.rotateBoardToMoverSelected = rotateBoardToMoverSelected;
        this.networkSessionActive = networkSessionActive;
        this.networkLocalIsGoldSeat = networkLocalIsGoldSeat;
    }

    /**
     * Whether the canonical “Gold at bottom” layout is used for coordinate mapping.
     *
     * <p>Priority: (1) If “rotate board to mover” is on: same as before — SETUP keeps Gold at bottom; PLAY puts the
     * side to move at the bottom (180°); GAME_OVER puts the winner at the bottom. (2) Else if a network session is
     * active and the game is in SETUP_GOLD, SETUP_SILVER, PLAY, or GAME_OVER: host sees Gold at bottom, client sees
     * Silver at bottom (each player’s own side toward them). (3) Else: Gold at bottom (local hotseat default).
     */
    public boolean boardGoldVisualBottom(Game g) {
        if (g == null) {
            return true;
        }
        if (rotateBoardToMoverSelected.getAsBoolean()) {
            GameState st = g.getState();
            if (st == GameState.SETUP_GOLD || st == GameState.SETUP_SILVER) {
                return true;
            }
            if (st == GameState.GAME_OVER) {
                PlayerSide w = g.getMatchWinner();
                if (w != null) {
                    return w == PlayerSide.GOLD;
                }
                return true;
            }
            if (st == GameState.PLAY) {
                return g.getSideToMove() == PlayerSide.GOLD;
            }
            return true;
        }
        if (networkSessionActive.getAsBoolean()) {
            GameState st = g.getState();
            if (st == GameState.SETUP_GOLD
                    || st == GameState.SETUP_SILVER
                    || st == GameState.PLAY
                    || st == GameState.GAME_OVER) {
                return networkLocalIsGoldSeat.getAsBoolean();
            }
            return true;
        }
        return true;
    }

    /** Maps board row index from top ({@code 0}) to model rank; Gold-at-bottom flips vertically; mover-at-bottom uses identity (180° total with files). */
    public int modelRankFromVisualRow(int visualRow, Game g) {
        if (boardGoldVisualBottom(g)) {
            return BoardConstants.BOARD_SIZE - 1 - visualRow;
        }
        return visualRow;
    }

    public int visualRowFromModelRank(int modelRank, Game g) {
        if (boardGoldVisualBottom(g)) {
            return BoardConstants.BOARD_SIZE - 1 - modelRank;
        }
        return modelRank;
    }

    /** Maps grid column from left ({@code 0}) to model file index ({@code a} = {@code 0}). */
    public int modelFileFromVisualCol(int visualCol, Game g) {
        if (boardGoldVisualBottom(g)) {
            return visualCol;
        }
        return BoardConstants.BOARD_SIZE - 1 - visualCol;
    }

    public int visualColFromModelFile(int modelFile, Game g) {
        if (boardGoldVisualBottom(g)) {
            return modelFile;
        }
        return BoardConstants.BOARD_SIZE - 1 - modelFile;
    }

    /** {@code [dVisualCol, dVisualRow]} for arrow keys / WASD; visual row 0 = top of the grid. */
    public static int[] visualDeltaForPlayNavigation(KeyCode code) {
        return switch (code) {
            case UP, W, KP_UP -> new int[] {0, -1};
            case DOWN, S, KP_DOWN -> new int[] {0, 1};
            case LEFT, A, KP_LEFT -> new int[] {-1, 0};
            case RIGHT, D, KP_RIGHT -> new int[] {1, 0};
            default -> null;
        };
    }

    public Position modelNeighborFromVisualDelta(Game g, Position from, int dVisualCol, int dVisualRow) {
        int vc = visualColFromModelFile(from.getFileIndex(), g);
        int vr = visualRowFromModelRank(from.getRankIndex(), g);
        int nvc = Math.min(BoardConstants.BOARD_SIZE - 1, Math.max(0, vc + dVisualCol));
        int nvr = Math.min(BoardConstants.BOARD_SIZE - 1, Math.max(0, vr + dVisualRow));
        return Position.of(modelFileFromVisualCol(nvc, g), modelRankFromVisualRow(nvr, g));
    }
}
