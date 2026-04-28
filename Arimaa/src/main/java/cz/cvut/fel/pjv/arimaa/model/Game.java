package cz.cvut.fel.pjv.arimaa.model;

import cz.cvut.fel.pjv.arimaa.util.BoardConstants;
import cz.cvut.fel.pjv.arimaa.util.HomeTerritory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Root aggregate for match state: board, side to move, lifecycle state, and setup flow.
 */
public class Game {

    private GameState state;
    private Board board;
    private PlayerSide sideToMove;

    private boolean ranksMirroredForHomeCheck;
    private final List<Piece> goldReserve = new ArrayList<>();
    private final List<Piece> silverReserve = new ArrayList<>();

    /** Piece taken from reserve, awaiting {@link #confirmSetupHandPlacement(Position)}. */
    private Piece setupHand;

    public GameState getState() {
        return state;
    }

    public void setState(GameState state) {
        this.state = state;
    }

    public Board getBoard() {
        return board;
    }

    public void setBoard(Board board) {
        this.board = board;
    }

    public PlayerSide getSideToMove() {
        return sideToMove;
    }

    public void setSideToMove(PlayerSide sideToMove) {
        this.sideToMove = sideToMove;
    }

    /**
     * When {@code true}, {@link HomeTerritory#contains(PlayerSide, Position, boolean)} mirrors rank index first.
     */
    public boolean isRanksMirroredForHomeCheck() {
        return ranksMirroredForHomeCheck;
    }

    public void setRanksMirroredForHomeCheck(boolean ranksMirroredForHomeCheck) {
        this.ranksMirroredForHomeCheck = ranksMirroredForHomeCheck;
    }

    /**
     * Clears the board, builds legal 16-piece reserves per side (1E 1C 2H 2D 2C 8R), and starts Gold setup.
     */
    public void startNewGame() {
        if (board == null) {
            board = new Board();
        } else {
            board.clear();
        }
        setupHand = null;
        goldReserve.clear();
        silverReserve.clear();
        goldReserve.addAll(createReserve(PlayerSide.GOLD));
        silverReserve.addAll(createReserve(PlayerSide.SILVER));
        Collections.shuffle(goldReserve);
        Collections.shuffle(silverReserve);
        state = GameState.SETUP_GOLD;
        sideToMove = PlayerSide.GOLD;
        ranksMirroredForHomeCheck = false;
    }

    /**
     * Unmodifiable copy of pieces still in the tray for {@code side}.
     */
    public List<Piece> getSetupReserveSnapshot(PlayerSide side) {
        Objects.requireNonNull(side, "side");
        return side == PlayerSide.GOLD
                ? Collections.unmodifiableList(new ArrayList<>(goldReserve))
                : Collections.unmodifiableList(new ArrayList<>(silverReserve));
    }

    /**
     * Picks one piece of {@code type} from the reserve into {@link #setupHand}. Returns any prior hand piece to the tray.
     */
    public boolean beginPlacingPieceFromReserve(PlayerSide side, PieceType type) {
        if (side == null || type == null || !isSetupPhaseForSide(side)) {
            return false;
        }
        List<Piece> reserve = reserveList(side);
        Piece chosen = removeFirstOfType(reserve, type);
        if (chosen == null) {
            return false;
        }
        if (setupHand != null) {
            returnToReserve(setupHand);
        }
        setupHand = chosen;
        return true;
    }

    /**
     * Places {@link #setupHand} on an empty home square if type quotas allow.
     */
    public boolean confirmSetupHandPlacement(Position position) {
        if (setupHand == null || position == null || board == null) {
            return false;
        }
        if (!isSetupPhaseForSide(setupHand.getSide())) {
            return false;
        }
        PlayerSide side = setupHand.getSide();
        if (!board.isEmpty(position) || !homeContains(side, position)) {
            return false;
        }
        if (countTypeOnHome(side, setupHand.getType()) >= maxPerType(setupHand.getType())) {
            return false;
        }
        board.setPiece(position, setupHand);
        setupHand = null;
        return true;
    }

    /** Returns {@link #setupHand} to the reserve without touching the board. */
    public void cancelPendingSetupPlacement() {
        if (setupHand != null) {
            returnToReserve(setupHand);
            setupHand = null;
        }
    }

    /**
     * Lifts a friendly piece from a home square back into the reserve during setup.
     */
    public boolean returnPieceFromBoardToReserve(PlayerSide side, Position position) {
        if (side == null || position == null || board == null || !isSetupPhaseForSide(side)) {
            return false;
        }
        if (!homeContains(side, position)) {
            return false;
        }
        Piece onBoard = board.getPiece(position);
        if (onBoard == null || onBoard.getSide() != side) {
            return false;
        }
        board.setPiece(position, null);
        reserveList(side).add(onBoard);
        return true;
    }

    /**
     * Applies a full turn during {@link GameState#PLAY}; not implemented yet (use {@link Move#getSteps()} later).
     */
    public void applyMove(Move move) {
    }

    private boolean homeContains(PlayerSide side, Position position) {
        return HomeTerritory.contains(side, position, ranksMirroredForHomeCheck);
    }

    private boolean isSetupPhaseForSide(PlayerSide side) {
        if (state == null) {
            return false;
        }
        return (state == GameState.SETUP_GOLD && side == PlayerSide.GOLD)
                || (state == GameState.SETUP_SILVER && side == PlayerSide.SILVER);
    }

    private List<Piece> reserveList(PlayerSide side) {
        return side == PlayerSide.GOLD ? goldReserve : silverReserve;
    }

    private void returnToReserve(Piece piece) {
        reserveList(piece.getSide()).add(piece);
    }

    private static Piece removeFirstOfType(List<Piece> reserve, PieceType type) {
        for (int i = 0; i < reserve.size(); i++) {
            if (reserve.get(i).getType() == type) {
                return reserve.remove(i);
            }
        }
        return null;
    }

    private int countTypeOnHome(PlayerSide side, PieceType type) {
        int n = 0;
        for (int r = 0; r < BoardConstants.BOARD_SIZE; r++) {
            for (int f = 0; f < BoardConstants.BOARD_SIZE; f++) {
                Position p = Position.of(f, r);
                if (!homeContains(side, p)) {
                    continue;
                }
                Piece piece = board.getPiece(p);
                if (piece != null && piece.getSide() == side && piece.getType() == type) {
                    n++;
                }
            }
        }
        return n;
    }

    private static int maxPerType(PieceType type) {
        return switch (type) {
            case ELEPHANT, CAMEL -> 1;
            case HORSE, DOG, CAT -> 2;
            case RABBIT -> 8;
        };
    }

    private static List<Piece> createReserve(PlayerSide side) {
        List<Piece> out = new ArrayList<>(16);
        for (int i = 0; i < 8; i++) {
            out.add(new Piece(PieceType.RABBIT, side));
        }
        for (int i = 0; i < 2; i++) {
            out.add(new Piece(PieceType.CAT, side));
        }
        for (int i = 0; i < 2; i++) {
            out.add(new Piece(PieceType.DOG, side));
        }
        for (int i = 0; i < 2; i++) {
            out.add(new Piece(PieceType.HORSE, side));
        }
        out.add(new Piece(PieceType.CAMEL, side));
        out.add(new Piece(PieceType.ELEPHANT, side));
        return out;
    }
}
