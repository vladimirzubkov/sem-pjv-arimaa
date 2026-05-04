package cz.cvut.fel.pjv.arimaa.model;

import cz.cvut.fel.pjv.arimaa.exception.GamePhaseException;
import cz.cvut.fel.pjv.arimaa.exception.IllegalMoveException;
import cz.cvut.fel.pjv.arimaa.model.enums.GameState;
import cz.cvut.fel.pjv.arimaa.model.enums.PieceType;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;
import cz.cvut.fel.pjv.arimaa.util.BoardConstants;
import cz.cvut.fel.pjv.arimaa.util.HomeTerritory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Root aggregate for match state: board, side to move, lifecycle, setup flow (incl. {@link SetupPresets} one-click
 * layouts), and trap-capture book-keeping.
 */
public class Game {

    private static final Logger log = LoggerFactory.getLogger(Game.class);

    private GameState state;
    private Board board;
    private PlayerSide sideToMove;
    /** Non-null only when {@link #state} is {@link GameState#GAME_OVER}. */
    private PlayerSide matchWinner;

    private final RuleEngine ruleEngine = new DefaultRuleEngine();

    private boolean ranksMirroredForHomeCheck;
    private final List<Piece> goldReserve = new ArrayList<>();
    private final List<Piece> silverReserve = new ArrayList<>();

    /** Piece taken from reserve, awaiting {@link #confirmSetupHandPlacement(Position)}. */
    private Piece setupHand;

    /**
     * Types of Silver pieces removed by traps (credited as captures for Gold).
     */
    private final List<PieceType> trapCapturesByGold = new ArrayList<>();
    /**
     * Types of Gold pieces removed by traps (credited as captures for Silver).
     */
    private final List<PieceType> trapCapturesBySilver = new ArrayList<>();

    /**
     * @return the piece currently held for setup placement, or {@code null}
     */
    public Piece getSetupHand() {
        return setupHand;
    }

    /** Current lifecycle phase ({@link GameState#SETUP_GOLD}, PLAY, …). */
    public GameState getState() {
        return state;
    }

    /** Assigns lifecycle phase (used when restoring mementos or tests). */
    public void setState(GameState state) {
        this.state = state;
    }

    /** Live 8×8 grid; never {@code null} after {@link #startNewGame()}. */
    public Board getBoard() {
        return board;
    }

    /** Rebinds the board (serialization / tests). */
    public void setBoard(Board board) {
        this.board = board;
    }

    /** Side allowed to act next (setup placement or PLAY turn). */
    public PlayerSide getSideToMove() {
        return sideToMove;
    }

    /** Overrides mover (setup transitions, PLAY, restore). */
    public void setSideToMove(PlayerSide sideToMove) {
        this.sideToMove = sideToMove;
    }

    /**
     * Winner once {@link GameState#GAME_OVER}; {@code null} while the match is ongoing.
     */
    public PlayerSide getMatchWinner() {
        return matchWinner;
    }

    /** Declares match winner when entering {@link GameState#GAME_OVER}. */
    public void setMatchWinner(PlayerSide matchWinner) {
        this.matchWinner = matchWinner;
    }

    /**
     * When {@code true}, {@link HomeTerritory#contains(PlayerSide, Position, boolean)} mirrors rank index first.
     */
    public boolean isRanksMirroredForHomeCheck() {
        return ranksMirroredForHomeCheck;
    }

    /** Flips how home ranks are interpreted for {@link HomeTerritory} checks (setup edge cases). */
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
        matchWinner = null;
        ranksMirroredForHomeCheck = false;
        trapCapturesByGold.clear();
        trapCapturesBySilver.clear();
    }

    /**
     * Snapshot of piece types removed by traps and credited to {@code capturer} (opponent's types).
     */
    public List<PieceType> getTrapCapturesSnapshot(PlayerSide capturer) {
        Objects.requireNonNull(capturer, "capturer");
        List<PieceType> src = capturer == PlayerSide.GOLD ? trapCapturesByGold : trapCapturesBySilver;
        return Collections.unmodifiableList(new ArrayList<>(src));
    }

    /**
     * Records a trap removal before the piece is cleared from the board; credits the opponent of the victim.
     */
    void recordTrapRemoval(Piece victim) {
        Objects.requireNonNull(victim, "victim");
        PlayerSide capturer = victim.getSide() == PlayerSide.GOLD ? PlayerSide.SILVER : PlayerSide.GOLD;
        if (capturer == PlayerSide.GOLD) {
            trapCapturesByGold.add(victim.getType());
        } else {
            trapCapturesBySilver.add(victim.getType());
        }
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
     * Counts pieces still in {@code side}'s tray during setup (same multiset as {@link #getSetupReserveSnapshot}).
     */
    public Map<PieceType, Integer> setupReserveCountsByType(PlayerSide side) {
        Objects.requireNonNull(side, "side");
        Map<PieceType, Integer> m = new EnumMap<>(PieceType.class);
        for (Piece p : getSetupReserveSnapshot(side)) {
            m.merge(p.getType(), 1, Integer::sum);
        }
        return m;
    }

    /**
     * Whether {@link #placeRemainingPiecesRandomly(PlayerSide)} can succeed right now: setup phase for {@code side},
     * non-empty effective reserve after the same hand-cancel rule as that method, and matching empty home squares.
     */
    public boolean canFillRemainingReserveRandomly(PlayerSide side) {
        if (side == null || board == null || !isSetupPhaseForSide(side)) {
            return false;
        }
        int remaining = reserveList(side).size();
        if (setupHand != null && setupHand.getSide() == side) {
            remaining++;
        }
        if (remaining <= 0) {
            return false;
        }
        return remaining == listEmptyHomeSquares(side).size();
    }

    /**
     * New game with default start state, then {@link #restoreMemento(GameMemento)} (for notation / replay probes).
     */
    public static Game restoredFromMemento(GameMemento m) {
        Objects.requireNonNull(m, "m");
        Game g = new Game();
        g.startNewGame();
        g.restoreMemento(m);
        return g;
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
     * Chooses the next tray piece for continuous placement UX after {@link #confirmSetupHandPlacement(Position)}: same type
     * if available, else strongest remaining (official {@link PieceType} order).
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
     * Randomly permutes this side's sixteen pieces among home squares (same multiset, new arrangement).
     * Requires empty reserve, nothing in hand for this side, and exactly sixteen friendly pieces on home.
     *
     * @param side the player whose home ranks are shuffled
     * @return {@code true} if pieces were reshuffled
     */
    public boolean shuffleSetupPiecesOnHomeRandomly(PlayerSide side) {
        if (side == null || board == null || !isSetupPhaseForSide(side)) {
            return false;
        }
        if (setupHand != null && setupHand.getSide() == side) {
            cancelPendingSetupPlacement();
        }
        if (!reserveList(side).isEmpty()) {
            return false;
        }
        List<Position> homes = listHomeSquares(side);
        List<Piece> pieces = new ArrayList<>(homes.size());
        for (Position p : homes) {
            Piece pc = board.getPiece(p);
            if (pc == null || pc.getSide() != side) {
                return false;
            }
            pieces.add(pc);
        }
        if (pieces.size() != 16) {
            return false;
        }
        for (Position p : homes) {
            board.setPiece(p, null);
        }
        Collections.shuffle(pieces);
        List<Position> slotOrder = new ArrayList<>(homes);
        Collections.shuffle(slotOrder);
        for (int i = 0; i < pieces.size(); i++) {
            board.setPiece(slotOrder.get(i), pieces.get(i));
        }
        return true;
    }

    /** Rotating presets: {@link SetupPresets#ROTATION_COUNT}. */
    public static final int CHESS_SETUP_ROTATION_COUNT = SetupPresets.ROTATION_COUNT;

    /**
     * Clears this side’s home rows onto the tray, then places the official multiset using {@link SetupPresets}: classic
     * chess mapping ({@link SetupPresets#classicGold()} / {@link SetupPresets#classicSilver()}).
     *
     * @param side gold or silver (must be in that side’s setup phase)
     * @return {@code false} if the combined tray + home pieces are not exactly sixteen with the legal multiset
     */
    public boolean applyChessMappedSetup(PlayerSide side) {
        return applyChessMappedSetup(side, -1);
    }

    /**
     * Same as {@link #applyChessMappedSetup(PlayerSide)} but {@code presetIndex} selects a rotating layout from
     * {@link SetupPresets#rotatingGold(int)} / {@link SetupPresets#rotatingSilver(int)} (see {@link SetupPresets}).
     *
     * @param presetIndex {@code -1} classic chess; Gold: {@code 0} reversed chess, {@code 1} 99of9, {@code 2} MH,
     *     {@code 3} HH. Silver: {@code 0} mirror of Gold reversed, {@code 1} Fritzlein only, {@code 2–3} mirrors of Gold
     *     MH / HH ({@link SetupPresets#mirrorGoldHomeToSilver}).
     */
    public boolean applyChessMappedSetup(PlayerSide side, int presetIndex) {
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
        List<SetupPresets.Slot> layout;
        if (presetIndex < 0) {
            layout = side == PlayerSide.GOLD ? SetupPresets.classicGold() : SetupPresets.classicSilver();
        } else {
            int v = Math.floorMod(presetIndex, CHESS_SETUP_ROTATION_COUNT);
            layout = side == PlayerSide.GOLD ? SetupPresets.rotatingGold(v) : SetupPresets.rotatingSilver(v);
        }
        for (SetupPresets.Slot slot : layout) {
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
     * Whether {@code side}'s tray is empty, no piece is held in hand for that side, and exactly 16 friendly pieces
     * sit on home squares. Does not verify multiset legality — {@link #tryCompleteSetup(PlayerSide)} still applies
     * the full rules.
     */
    public boolean allSetupPiecesOnBoard(PlayerSide side) {
        if (side == null || board == null || !isSetupPhaseForSide(side)) {
            return false;
        }
        if (setupHand != null && setupHand.getSide() == side) {
            return false;
        }
        if (!reserveList(side).isEmpty()) {
            return false;
        }
        return countPiecesOnHome(side) == 16;
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
     * Applies a full turn during {@link GameState#PLAY}: 1–4 simple steps, trap resolution, then switches {@link #sideToMove}.
     *
     * @param move non-null turn with {@link Move#getSteps()} size 1–4
     * @throws GamePhaseException if not in {@link GameState#PLAY}
     * @throws IllegalMoveException if the move is illegal
     */
    public void applyMove(Move move) {
        Objects.requireNonNull(move, "move");
        if (state != GameState.PLAY) {
            throw new GamePhaseException("applyMove only in PLAY");
        }
        log.debug("Game.applyMove: sideToMove={} stepCount={}", sideToMove, move.getSteps().size());
        ruleEngine.applyMove(this, move);
        if (state == GameState.GAME_OVER) {
            log.info("Game.applyMove finished: GAME_OVER matchWinner={}", matchWinner);
        }
    }

    /**
     * Applies a legal in-turn prefix during PLAY without ending the turn (used when restoring saved mid-turn state).
     */
    public void applyPlayPrefix(Move prefix) {
        Objects.requireNonNull(prefix, "prefix");
        if (state != GameState.PLAY) {
            throw new GamePhaseException("applyPlayPrefix only in PLAY");
        }
        ruleEngine.applyPlayPrefix(this, prefix);
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
        this.matchWinner = m.matchWinner();
        this.ranksMirroredForHomeCheck = m.ranksMirroredForHomeCheck();
        trapCapturesByGold.clear();
        trapCapturesByGold.addAll(m.trapCapturesByGold());
        trapCapturesBySilver.clear();
        trapCapturesBySilver.addAll(m.trapCapturesBySilver());
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

    /** All squares in {@code side}'s home territory (sixteen coordinates). */
    private List<Position> listHomeSquares(PlayerSide side) {
        List<Position> out = new ArrayList<>(16);
        for (int r = 0; r < BoardConstants.BOARD_SIZE; r++) {
            for (int f = 0; f < BoardConstants.BOARD_SIZE; f++) {
                Position p = Position.of(f, r);
                if (homeContains(side, p)) {
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
