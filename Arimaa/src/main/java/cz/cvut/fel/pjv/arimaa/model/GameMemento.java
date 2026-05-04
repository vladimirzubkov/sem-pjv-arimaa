package cz.cvut.fel.pjv.arimaa.model;

import cz.cvut.fel.pjv.arimaa.model.enums.GameState;
import cz.cvut.fel.pjv.arimaa.model.enums.PieceType;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;
import cz.cvut.fel.pjv.arimaa.util.BoardConstants;

import java.util.ArrayList;
import java.util.List;

/**
 * Memento (GoF) for {@link Game}: immutable snapshot of board, reserves, hand, and match flags.
 */
public record GameMemento(
        GameState state,
        PlayerSide sideToMove,
        PlayerSide matchWinner,
        boolean ranksMirroredForHomeCheck,
        CellSnap[][] grid,
        List<CellSnap> goldReserve,
        List<CellSnap> silverReserve,
        CellSnap setupHand,
        List<PieceType> trapCapturesByGold,
        List<PieceType> trapCapturesBySilver) {

    public record CellSnap(PieceType type, PlayerSide side) {
    }

    /**
     * Captures the current {@link Game} state into a new memento (new {@link Piece} instances are not stored;
     * only type/side per square and reserve entry).
     */
    public static GameMemento fromGame(Game game) {
        CellSnap[][] grid = new CellSnap[BoardConstants.BOARD_SIZE][BoardConstants.BOARD_SIZE];
        for (int r = 0; r < BoardConstants.BOARD_SIZE; r++) {
            for (int f = 0; f < BoardConstants.BOARD_SIZE; f++) {
                Position p = Position.of(f, r);
                Piece piece = game.getBoard().getPiece(p);
                grid[r][f] = piece == null ? null : new CellSnap(piece.getType(), piece.getSide());
            }
        }
        List<CellSnap> gold = new ArrayList<>();
        for (Piece p : game.getSetupReserveSnapshot(PlayerSide.GOLD)) {
            gold.add(new CellSnap(p.getType(), p.getSide()));
        }
        List<CellSnap> silver = new ArrayList<>();
        for (Piece p : game.getSetupReserveSnapshot(PlayerSide.SILVER)) {
            silver.add(new CellSnap(p.getType(), p.getSide()));
        }
        Piece hand = game.getSetupHand();
        CellSnap handSnap = hand == null ? null : new CellSnap(hand.getType(), hand.getSide());
        return new GameMemento(
                game.getState(),
                game.getSideToMove(),
                game.getMatchWinner(),
                game.isRanksMirroredForHomeCheck(),
                grid,
                List.copyOf(gold),
                List.copyOf(silver),
                handSnap,
                List.copyOf(game.getTrapCapturesSnapshot(PlayerSide.GOLD)),
                List.copyOf(game.getTrapCapturesSnapshot(PlayerSide.SILVER)));
    }
}
