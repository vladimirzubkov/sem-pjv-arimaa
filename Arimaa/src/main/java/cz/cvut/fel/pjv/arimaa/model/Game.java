package cz.cvut.fel.pjv.arimaa.model;

import cz.cvut.fel.pjv.arimaa.util.BoardConstants;
import cz.cvut.fel.pjv.arimaa.util.HomeTerritory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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

    /**
     * @return the piece currently held for setup placement, or {@code null}
     */
    public Piece getSetupHand() {
        return setupHand;
    }

    private record Slot(Position position, PieceType type) {
    }

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
        Piece handPiece = setupHand;
        board.setPiece(position, handPiece);
        setupHand = null;
        beginAutoPickNextInHandAfterPlacement(handPiece.getSide(), handPiece.getType());
        return true;
    }

    /**
     * Whether the current {@link #setupHand} could be legally placed on {@code position} (empty home square, quotas).
     */
    public boolean isLegalSetupHandPlacementTarget(Position position) {
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
        return countTypeOnHome(side, setupHand.getType()) < maxPerType(setupHand.getType());
    }

    /**
     * After a successful placement: take another piece of the same type from the tray if any; otherwise the
     * strongest available type (official order {@link PieceType} enum order). Does nothing if setup is not active.
     */
    private void beginAutoPickNextInHandAfterPlacement(PlayerSide side, PieceType placedType) {
        if (!isSetupPhaseForSide(side)) {
            return;
        }
        if (beginPlacingPieceFromReserve(side, placedType)) {
            return;
        }
        for (PieceType t : PieceType.values()) {
            if (beginPlacingPieceFromReserve(side, t)) {
                return;
            }
        }
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
     * Randomly places every piece still in {@code side}'s reserve onto empty squares in that side’s home rows.
     * <p>
     * May be called at any time during that side’s setup; any held {@link #setupHand} piece for the same side is
     * returned to the tray first. Fails if the number of empty home squares does not match the reserve size.
     *
     * @param side the player whose setup is being filled
     * @return {@code true} if all reserve pieces were placed
     */
    public boolean placeRemainingPiecesRandomly(PlayerSide side) {
        if (side == null || board == null || !isSetupPhaseForSide(side)) {
            return false;
        }
        if (setupHand != null && setupHand.getSide() == side) {
            cancelPendingSetupPlacement();
        }
        List<Piece> remaining = new ArrayList<>(reserveList(side));
        List<Position> empties = listEmptyHomeSquares(side);
        if (remaining.size() != empties.size()) {
            return false;
        }
        Collections.shuffle(remaining);
        Collections.shuffle(empties);
        reserveList(side).clear();
        for (int i = 0; i < remaining.size(); i++) {
            board.setPiece(empties.get(i), remaining.get(i));
        }
        return true;
    }

    /**
     * Clears this side’s home rows onto the tray, then places the official multiset in a fixed “chess mapping” layout:
     * back rank a–h = Horse, Cat, Dog, Camel, Elephant, Dog, Cat, Horse (R,N,B,Q,K…); forward rank = eight rabbits.
     * Silver uses rabbits on rank 6 (index 6) and the same back rank on rank 7.
     *
     * @param side gold or silver (must be in that side’s setup phase)
     * @return {@code false} if the combined tray + home pieces are not exactly sixteen with the legal multiset
     */
    public boolean applyChessMappedSetup(PlayerSide side) {
        if (side == null || board == null || !isSetupPhaseForSide(side)) {
            return false;
        }
        if (setupHand != null && setupHand.getSide() == side) {
            cancelPendingSetupPlacement();
        }
        liftAllFriendlyPiecesFromHomeToReserve(side);
        List<Piece> pool = new ArrayList<>(reserveList(side));
        reserveList(side).clear();
        if (!isOfficialMultiset(pool) || pool.size() != 16) {
            reserveList(side).addAll(pool);
            return false;
        }
        List<Slot> layout = side == PlayerSide.GOLD ? goldChessSlots() : silverChessSlots();
        for (Slot slot : layout) {
            Piece piece = removeFirstOfType(pool, slot.type());
            if (piece == null) {
                liftAllFriendlyPiecesFromHomeToReserve(side);
                reserveList(side).addAll(pool);
                return false;
            }
            board.setPiece(slot.position(), piece);
        }
        return true;
    }

    /**
     * Validates that {@code side} has finished setup (sixteen pieces on home ranks, correct multiset, empty tray,
     * nothing in hand) and advances {@link GameState}: Gold → {@link GameState#SETUP_SILVER}, Silver → {@link GameState#PLAY}
     * with Gold to move first.
     */
    public boolean tryCompleteSetup(PlayerSide side) {
        if (side == null || board == null || !isSetupPhaseForSide(side)) {
            return false;
        }
        if (setupHand != null && setupHand.getSide() == side) {
            return false;
        }
        if (!reserveList(side).isEmpty()) {
            return false;
        }
        if (countPiecesOnHome(side) != 16) {
            return false;
        }
        if (!allOccupantsOnHomeAreFriendly(side) || !multisetOnHomeMatchesOfficial(side)) {
            return false;
        }
        if (state == GameState.SETUP_GOLD && side == PlayerSide.GOLD) {
            state = GameState.SETUP_SILVER;
            sideToMove = PlayerSide.SILVER;
            return true;
        }
        if (state == GameState.SETUP_SILVER && side == PlayerSide.SILVER) {
            state = GameState.PLAY;
            sideToMove = PlayerSide.GOLD;
            return true;
        }
        return false;
    }

    /**
     * Applies a full turn during {@link GameState#PLAY}; not implemented yet (use {@link Move#getSteps()} later).
     */
    public void applyMove(Move move) {
    }

    /**
     * Captures full match state for undo/redo (Memento).
     */
    public GameMemento createMemento() {
        return GameMemento.fromGame(this);
    }

    /**
     * Restores board, both reserves, piece in hand, and lifecycle fields from a memento (new {@link Piece} instances).
     */
    public void restoreMemento(GameMemento m) {
        Objects.requireNonNull(m, "memento");
        board.clear();
        goldReserve.clear();
        silverReserve.clear();
        for (GameMemento.CellSnap s : m.goldReserve()) {
            goldReserve.add(new Piece(s.type(), s.side()));
        }
        for (GameMemento.CellSnap s : m.silverReserve()) {
            silverReserve.add(new Piece(s.type(), s.side()));
        }
        GameMemento.CellSnap[][] grid = m.grid();
        for (int r = 0; r < BoardConstants.BOARD_SIZE; r++) {
            for (int f = 0; f < BoardConstants.BOARD_SIZE; f++) {
                GameMemento.CellSnap snap = grid[r][f];
                if (snap != null) {
                    board.setPiece(Position.of(f, r), new Piece(snap.type(), snap.side()));
                }
            }
        }
        GameMemento.CellSnap hand = m.setupHand();
        setupHand = hand == null ? null : new Piece(hand.type(), hand.side());
        this.state = m.state();
        this.sideToMove = m.sideToMove();
        this.ranksMirroredForHomeCheck = m.ranksMirroredForHomeCheck();
    }

    private static List<Slot> goldChessSlots() {
        List<Slot> slots = new ArrayList<>(16);
        PieceType[] back = {
                PieceType.HORSE, PieceType.CAT, PieceType.DOG, PieceType.CAMEL,
                PieceType.ELEPHANT, PieceType.DOG, PieceType.CAT, PieceType.HORSE
        };
        for (int f = 0; f < BoardConstants.BOARD_SIZE; f++) {
            slots.add(new Slot(Position.of(f, 0), back[f]));
        }
        for (int f = 0; f < BoardConstants.BOARD_SIZE; f++) {
            slots.add(new Slot(Position.of(f, 1), PieceType.RABBIT));
        }
        return slots;
    }

    private static List<Slot> silverChessSlots() {
        List<Slot> slots = new ArrayList<>(16);
        for (int f = 0; f < BoardConstants.BOARD_SIZE; f++) {
            slots.add(new Slot(Position.of(f, 6), PieceType.RABBIT));
        }
        PieceType[] back = {
                PieceType.HORSE, PieceType.CAT, PieceType.DOG, PieceType.CAMEL,
                PieceType.ELEPHANT, PieceType.DOG, PieceType.CAT, PieceType.HORSE
        };
        for (int f = 0; f < BoardConstants.BOARD_SIZE; f++) {
            slots.add(new Slot(Position.of(f, 7), back[f]));
        }
        return slots;
    }

    private void liftAllFriendlyPiecesFromHomeToReserve(PlayerSide side) {
        for (int r = 0; r < BoardConstants.BOARD_SIZE; r++) {
            for (int f = 0; f < BoardConstants.BOARD_SIZE; f++) {
                Position p = Position.of(f, r);
                if (!homeContains(side, p)) {
                    continue;
                }
                Piece onBoard = board.getPiece(p);
                if (onBoard != null && onBoard.getSide() == side) {
                    board.setPiece(p, null);
                    reserveList(side).add(onBoard);
                }
            }
        }
    }

    private List<Position> listEmptyHomeSquares(PlayerSide side) {
        List<Position> out = new ArrayList<>();
        for (int r = 0; r < BoardConstants.BOARD_SIZE; r++) {
            for (int f = 0; f < BoardConstants.BOARD_SIZE; f++) {
                Position p = Position.of(f, r);
                if (homeContains(side, p) && board.isEmpty(p)) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    private int countPiecesOnHome(PlayerSide side) {
        int n = 0;
        for (int r = 0; r < BoardConstants.BOARD_SIZE; r++) {
            for (int f = 0; f < BoardConstants.BOARD_SIZE; f++) {
                Position p = Position.of(f, r);
                if (!homeContains(side, p)) {
                    continue;
                }
                Piece piece = board.getPiece(p);
                if (piece != null && piece.getSide() == side) {
                    n++;
                }
            }
        }
        return n;
    }

    private boolean allOccupantsOnHomeAreFriendly(PlayerSide side) {
        for (int r = 0; r < BoardConstants.BOARD_SIZE; r++) {
            for (int f = 0; f < BoardConstants.BOARD_SIZE; f++) {
                Position p = Position.of(f, r);
                if (!homeContains(side, p)) {
                    continue;
                }
                Piece piece = board.getPiece(p);
                if (piece != null && piece.getSide() != side) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean multisetOnHomeMatchesOfficial(PlayerSide side) {
        List<Piece> onHome = new ArrayList<>();
        for (int r = 0; r < BoardConstants.BOARD_SIZE; r++) {
            for (int f = 0; f < BoardConstants.BOARD_SIZE; f++) {
                Position p = Position.of(f, r);
                if (!homeContains(side, p)) {
                    continue;
                }
                Piece piece = board.getPiece(p);
                if (piece != null && piece.getSide() == side) {
                    onHome.add(piece);
                }
            }
        }
        return isOfficialMultiset(onHome);
    }

    private static boolean isOfficialMultiset(List<Piece> pieces) {
        if (pieces.size() != 16) {
            return false;
        }
        Map<PieceType, Integer> counts = new EnumMap<>(PieceType.class);
        for (Piece p : pieces) {
            counts.merge(p.getType(), 1, Integer::sum);
        }
        return counts.getOrDefault(PieceType.ELEPHANT, 0) == 1
                && counts.getOrDefault(PieceType.CAMEL, 0) == 1
                && counts.getOrDefault(PieceType.HORSE, 0) == 2
                && counts.getOrDefault(PieceType.DOG, 0) == 2
                && counts.getOrDefault(PieceType.CAT, 0) == 2
                && counts.getOrDefault(PieceType.RABBIT, 0) == 8;
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
