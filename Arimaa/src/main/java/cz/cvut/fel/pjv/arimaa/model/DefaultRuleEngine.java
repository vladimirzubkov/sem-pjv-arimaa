package cz.cvut.fel.pjv.arimaa.model;

import cz.cvut.fel.pjv.arimaa.exception.GamePhaseException;
import cz.cvut.fel.pjv.arimaa.exception.IllegalMoveException;
import cz.cvut.fel.pjv.arimaa.util.BoardConstants;
import cz.cvut.fel.pjv.arimaa.util.PieceStrength;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Full Arimaa play rules: slides, push/pull, freezing, traps after each step, and game-end detection.
 */
public final class DefaultRuleEngine implements RuleEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultRuleEngine.class);

    @Override
    public void applyMove(Game game, Move move) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(move, "move");
        if (game.getState() != GameState.PLAY) {
            throw new GamePhaseException("applyMove only in PLAY");
        }
        PlayerSide mover = game.getSideToMove();
        log.info("applyMove: mover={} stepCount={}", mover, move.getSteps().size());
        log.debug("applyMove detail: {}", describeMove(move));
        validateSequentialSteps(game, move, true, null);
        applyMoveToBoard(game.getBoard(), move, game::recordTrapRemoval);
        Board board = game.getBoard();
        log.debug(
                "after applyMoveToBoard: goldRabbits={} silverRabbits={}",
                countRabbits(board, PlayerSide.GOLD),
                countRabbits(board, PlayerSide.SILVER));
        TerminalEvaluation terminal = evaluateTerminalWithReason(board);
        if (terminal.winner() != null) {
            log.info("game over: winner={} reason={} lastMover={}", terminal.winner(), terminal.reason(), mover);
            game.setMatchWinner(terminal.winner());
            game.setState(GameState.GAME_OVER);
            return;
        }
        game.setSideToMove(opponent(mover));
        boolean opponentCanMove = existsLegalTurn(game);
        log.debug("after side switch: sideToMove={} opponentHasLegalTurn={}", game.getSideToMove(), opponentCanMove);
        if (!opponentCanMove) {
            log.info("game over: immobilization winner={} immobilized={}", mover, game.getSideToMove());
            game.setMatchWinner(mover);
            game.setState(GameState.GAME_OVER);
        }
    }

    /**
     * Applies the move steps to {@code board} (mutation). Caller validates first.
     *
     * @param onTrapVictim invoked for each piece removed by a trap before the square is cleared; may be {@code null}
     */
    static void applyMoveToBoard(Board board, Move move, Consumer<Piece> onTrapVictim) {
        List<Step> steps = move.getSteps();
        for (int i = 0; i < steps.size(); i++) {
            Step s = steps.get(i);
            StepKind k = kindOf(s);
            if (k == StepKind.SLIDE) {
                applyOneStep(board, s);
                resolveTraps(board, onTrapVictim);
            } else if (k == StepKind.PUSH_DISPLACE_WEAKER) {
                applyOneStep(board, s);
                resolveTraps(board, onTrapVictim);
                i++;
                applyOneStep(board, steps.get(i));
                resolveTraps(board, onTrapVictim);
            } else if (k == StepKind.PULL_VACATE_STRONGER) {
                applyOneStep(board, s);
                resolveTraps(board, onTrapVictim);
                i++;
                applyOneStep(board, steps.get(i));
                resolveTraps(board, onTrapVictim);
            }
        }
    }

    /**
     * Trap victims from applying {@code prefix} on a copy of the current board (does not mutate {@link Game}).
     * Empty or invalid prefix yields empty lists.
     */
    public record TrapCapturePreview(List<PieceType> byGold, List<PieceType> bySilver) {
    }

    public static TrapCapturePreview trapCapturesIfPrefixApplied(Game game, Move prefix) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(prefix, "prefix");
        if (prefix.getSteps().isEmpty() || game.getState() != GameState.PLAY) {
            return new TrapCapturePreview(List.of(), List.of());
        }
        if (!isValidPlayPrefix(game, prefix)) {
            return new TrapCapturePreview(List.of(), List.of());
        }
        Board scratch = game.getBoard().copy();
        List<PieceType> byGold = new ArrayList<>();
        List<PieceType> bySilver = new ArrayList<>();
        applyMoveToBoard(scratch, prefix, victim -> {
            if (victim.getSide() == PlayerSide.SILVER) {
                byGold.add(victim.getType());
            } else {
                bySilver.add(victim.getType());
            }
        });
        return new TrapCapturePreview(List.copyOf(byGold), List.copyOf(bySilver));
    }

    public static boolean isValidPlayPrefix(Game game, Move move) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(move, "move");
        if (game.getState() != GameState.PLAY) {
            return false;
        }
        try {
            validateSequentialSteps(game, move, false, null);
            return true;
        } catch (IllegalArgumentException ex) {
            log.debug("invalid play prefix: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * Occupancy after legally applying {@code prefix} (same rules as the engine, including traps after each step).
     * Empty prefix returns a copy of the current board occupancy.
     *
     * @throws GamePhaseException if {@code game} is not in {@link GameState#PLAY}
     * @throws IllegalMoveException if {@code prefix} is illegal
     */
    public static Map<Position, Piece> simulatePlayPrefix(Game game, Move prefix) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(prefix, "prefix");
        if (game.getState() != GameState.PLAY) {
            throw new GamePhaseException("simulatePlayPrefix only in PLAY");
        }
        Map<Position, Piece> root = snapshotOccupancy(game.getBoard());
        if (prefix.getSteps().isEmpty()) {
            return copyOcc(root);
        }
        try {
            return validateSequentialSteps(game, copyMove(prefix), false, root);
        } catch (IllegalArgumentException ex) {
            log.debug("simulatePlayPrefix rejected: {}", ex.getMessage());
            throw ex;
        }
    }

    /**
     * Whether {@code side} has at least one legal full turn (1–4 steps) from {@code game}'s current board.
     */
    public static boolean existsLegalTurn(Game game) {
        Objects.requireNonNull(game, "game");
        if (game.getState() != GameState.PLAY) {
            return false;
        }
        Map<Position, Piece> root = snapshotOccupancy(game.getBoard());
        return dfsAnyLegalTurn(game, new Move(), root);
    }

    private static boolean dfsAnyLegalTurn(Game game, Move prefix, Map<Position, Piece> root) {
        int len = prefix.getSteps().size();
        if (len >= 1 && len <= 4) {
            try {
                validateSequentialSteps(game, copyMove(prefix), true, root);
                return true;
            } catch (IllegalArgumentException ignored) {
                // not a complete legal turn
            }
        }
        if (len >= 4) {
            return false;
        }
        Map<Position, Piece> occAfter;
        try {
            occAfter = validateSequentialSteps(game, copyMove(prefix), false, root);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        for (List<Step> bundle : enumerateStepBundles(occAfter, game.getSideToMove())) {
            Move extended = copyMove(prefix);
            for (Step st : bundle) {
                extended.getSteps().add(copyStep(st));
            }
            try {
                validateSequentialSteps(game, copyMove(extended), false, root);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            if (dfsAnyLegalTurn(game, extended, root)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Enumerates one-step or two-step bundles (push/pull) legal as the next extension from {@code occ}.
     */
    public static List<List<Step>> enumerateStepBundles(Map<Position, Piece> occ, PlayerSide side) {
        List<List<Step>> out = new ArrayList<>();
        for (int r = 0; r < BoardConstants.BOARD_SIZE; r++) {
            for (int f = 0; f < BoardConstants.BOARD_SIZE; f++) {
                Position from = Position.of(f, r);
                Piece p = occ.get(from);
                if (p == null || p.getSide() != side) {
                    continue;
                }
                if (!isFrozenOccupancy(occ, from)) {
                    for (Position to : orthogonalNeighbors(from)) {
                        if (occ.get(to) != null) {
                            continue;
                        }
                        Step slide = new Step();
                        slide.setKind(StepKind.SLIDE);
                        slide.setFrom(from);
                        slide.setTo(to);
                        try {
                            Map<Position, Piece> t = copyOcc(occ);
                            validateSlideOnOcc(t, side, slide);
                            out.add(List.of(copyStep(slide)));
                        } catch (IllegalArgumentException ignored) {
                            // skip
                        }
                    }
                    if (!isFrozenOccupancy(occ, from)) {
                        addPullBundles(occ, side, from, p, out);
                    }
                }
                addPushBundles(occ, side, from, p, out);
            }
        }
        return out;
    }

    private static void addPushBundles(Map<Position, Piece> occ, PlayerSide side, Position strongPos, Piece strong, List<List<Step>> out) {
        if (strong.getSide() != side || isFrozenOccupancy(occ, strongPos)) {
            return;
        }
        for (Position weakPos : orthogonalNeighbors(strongPos)) {
            Piece weak = occ.get(weakPos);
            if (weak == null || weak.getSide() == side) {
                continue;
            }
            if (!PieceStrength.isStrictlyStronger(strong.getType(), weak.getType())) {
                continue;
            }
            for (Position dest : orthogonalNeighbors(weakPos)) {
                if (dest.equals(strongPos) || occ.get(dest) != null) {
                    continue;
                }
                Step d = new Step();
                d.setKind(StepKind.PUSH_DISPLACE_WEAKER);
                d.setFrom(weakPos);
                d.setTo(dest);
                Step a = new Step();
                a.setKind(StepKind.PUSH_ADVANCE_STRONGER);
                a.setFrom(strongPos);
                a.setTo(weakPos);
                try {
                    Map<Position, Piece> t = copyOcc(occ);
                    List<Step> buf = new ArrayList<>();
                    validatePushDisplaceOnOcc(t, side, d, buf);
                    applyOneStepOnOccupancy(t, d);
                    resolveTrapsOnOccupancy(t);
                    buf.add(d);
                    validatePushAdvanceOnOcc(t, side, a, weakPos, strongPos);
                    out.add(List.of(copyStep(d), copyStep(a)));
                } catch (IllegalArgumentException ignored) {
                    // skip
                }
            }
        }
    }

    private static void addPullBundles(Map<Position, Piece> occ, PlayerSide side, Position strongPos, Piece strong, List<List<Step>> out) {
        for (Position vac : orthogonalNeighbors(strongPos)) {
            if (occ.get(vac) != null) {
                continue;
            }
            Step vacStep = new Step();
            vacStep.setKind(StepKind.PULL_VACATE_STRONGER);
            vacStep.setFrom(strongPos);
            vacStep.setTo(vac);
            for (Position weakPos : orthogonalNeighbors(strongPos)) {
                if (weakPos.equals(vac)) {
                    continue;
                }
                Piece weak = occ.get(weakPos);
                if (weak == null || weak.getSide() == side) {
                    continue;
                }
                if (!PieceStrength.isStrictlyStronger(strong.getType(), weak.getType())) {
                    continue;
                }
                Step drag = new Step();
                drag.setKind(StepKind.PULL_DRAG_WEAKER);
                drag.setFrom(weakPos);
                drag.setTo(strongPos);
                try {
                    Map<Position, Piece> t = copyOcc(occ);
                    List<Step> buf = new ArrayList<>();
                    validatePullVacateOnOcc(t, side, vacStep, buf);
                    applyOneStepOnOccupancy(t, vacStep);
                    resolveTrapsOnOccupancy(t);
                    buf.add(vacStep);
                    validatePullDragOnOcc(t, side, drag, strongPos, vac);
                    out.add(List.of(copyStep(vacStep), copyStep(drag)));
                } catch (IllegalArgumentException ignored) {
                    // skip
                }
            }
        }
    }


    private static Map<Position, Piece> validateSequentialSteps(Game game, Move move, boolean requireFullTurn, Map<Position, Piece> initialOcc) {
        List<Step> steps = move.getSteps();
        int n = steps.size();
        if (requireFullTurn) {
            if (n < 1 || n > 4) {
                throw new IllegalMoveException("Turn must have 1–4 steps, got " + n);
            }
        } else {
            if (n < 0 || n > 4) {
                throw new IllegalMoveException("Prefix may have at most 4 steps, got " + n);
            }
        }
        PlayerSide side = game.getSideToMove();
        Map<Position, Piece> occ = initialOcc != null ? copyOcc(initialOcc) : snapshotOccupancy(game.getBoard());
        log.debug("validateSequentialSteps requireFullTurn={} stepCount={} side={}", requireFullTurn, n, side);
        for (int i = 0; i < n; ) {
            Step s = steps.get(i);
            StepKind k = kindOf(s);
            switch (k) {
                case SLIDE -> {
                    validateSlideOnOcc(occ, side, s);
                    applyOneStepOnOccupancy(occ, s);
                    resolveTrapsOnOccupancy(occ);
                    i++;
                }
                case PUSH_DISPLACE_WEAKER -> {
                    validatePushDisplaceOnOcc(occ, side, s, steps.subList(0, i));
                    applyOneStepOnOccupancy(occ, s);
                    resolveTrapsOnOccupancy(occ);
                    i++;
                    if (i >= n) {
                        throw new IllegalMoveException("Push missing advance step");
                    }
                    Step s2 = steps.get(i);
                    if (kindOf(s2) != StepKind.PUSH_ADVANCE_STRONGER) {
                        throw new IllegalMoveException("Push must be followed by PUSH_ADVANCE_STRONGER");
                    }
                    validatePushAdvanceOnOcc(occ, side, s2, s.getFrom(), null);
                    applyOneStepOnOccupancy(occ, s2);
                    resolveTrapsOnOccupancy(occ);
                    i++;
                }
                case PUSH_ADVANCE_STRONGER -> throw new IllegalMoveException("PUSH_ADVANCE without PUSH_DISPLACE");
                case PULL_VACATE_STRONGER -> {
                    validatePullVacateOnOcc(occ, side, s, steps.subList(0, i));
                    Position strongOld = s.getFrom();
                    applyOneStepOnOccupancy(occ, s);
                    resolveTrapsOnOccupancy(occ);
                    i++;
                    if (i >= n) {
                        throw new IllegalMoveException("Pull missing drag step");
                    }
                    Step s2 = steps.get(i);
                    if (kindOf(s2) != StepKind.PULL_DRAG_WEAKER) {
                        throw new IllegalMoveException("Pull must be followed by PULL_DRAG_WEAKER");
                    }
                    validatePullDragOnOcc(occ, side, s2, strongOld, s.getTo());
                    applyOneStepOnOccupancy(occ, s2);
                    resolveTrapsOnOccupancy(occ);
                    i++;
                }
                case PULL_DRAG_WEAKER -> throw new IllegalMoveException("PULL_DRAG without PULL_VACATE");
                default -> throw new IllegalMoveException("Unknown kind");
            }
        }
        return occ;
    }

    private static String describeMove(Move move) {
        StringBuilder sb = new StringBuilder();
        List<Step> steps = move.getSteps();
        for (int i = 0; i < steps.size(); i++) {
            Step s = steps.get(i);
            if (i > 0) {
                sb.append("; ");
            }
            sb.append(kindOf(s)).append(' ').append(s.getFrom()).append("->").append(s.getTo());
        }
        return sb.toString();
    }

    private record TerminalEvaluation(PlayerSide winner, String reason) {
    }

    private static TerminalEvaluation evaluateTerminalWithReason(Board board) {
        if (hasRabbitOnRank(board, PlayerSide.GOLD, BoardConstants.BOARD_SIZE - 1)) {
            return new TerminalEvaluation(PlayerSide.GOLD, "gold_rabbit_goal_rank");
        }
        if (hasRabbitOnRank(board, PlayerSide.SILVER, 0)) {
            return new TerminalEvaluation(PlayerSide.SILVER, "silver_rabbit_goal_rank");
        }
        if (countRabbits(board, PlayerSide.GOLD) == 0) {
            return new TerminalEvaluation(PlayerSide.SILVER, "gold_has_no_rabbits");
        }
        if (countRabbits(board, PlayerSide.SILVER) == 0) {
            return new TerminalEvaluation(PlayerSide.GOLD, "silver_has_no_rabbits");
        }
        // Note: „opponent rabbit on goal row“ end-of-turn rule is not evaluated here — Silver may legally
        // occupy the top rank in the opening; detecting illegal *push* onto the goal row needs per-turn context.
        return new TerminalEvaluation(null, "none");
    }

    private static void validateSlideOnOcc(Map<Position, Piece> occ, PlayerSide side, Step step) {
        Position from = step.getFrom();
        Position to = step.getTo();
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (!isOrthogonalNeighbor(from, to)) {
            throw new IllegalMoveException("Slide must be orthogonal");
        }
        Piece moving = occ.get(from);
        if (moving == null || moving.getSide() != side) {
            throw new IllegalMoveException("Slide must move own piece");
        }
        if (isFrozenOccupancy(occ, from)) {
            throw new IllegalMoveException("Frozen piece cannot slide");
        }
        if (occ.get(to) != null) {
            throw new IllegalMoveException("Slide destination must be empty");
        }
        if (moving.getType() == PieceType.RABBIT && isRabbitBackward(moving.getSide(), from, to)) {
            throw new IllegalMoveException("Rabbit cannot move backward");
        }
    }

    private static void validatePushDisplaceOnOcc(Map<Position, Piece> occ, PlayerSide side, Step step, List<Step> ignored) {
        Position weakFrom = step.getFrom();
        Position weakTo = step.getTo();
        if (!isOrthogonalNeighbor(weakFrom, weakTo)) {
            throw new IllegalMoveException("Push displace must be orthogonal");
        }
        Piece weak = occ.get(weakFrom);
        if (weak == null || weak.getSide() == side) {
            throw new IllegalMoveException("Push must displace opponent");
        }
        if (occ.get(weakTo) != null) {
            throw new IllegalMoveException("Push target must be empty");
        }
        Position strongSquare = findStrongOrthNeighbor(occ, side, weakFrom, weak);
        if (strongSquare == null) {
            throw new IllegalMoveException("No stronger adjacent piece to push");
        }
        if (isFrozenOccupancy(occ, strongSquare)) {
            throw new IllegalMoveException("Frozen piece cannot push");
        }
    }

    private static void validatePushAdvanceOnOcc(Map<Position, Piece> occ, PlayerSide side, Step step, Position weakOld, Position ignoredStrongOld) {
        Position from = step.getFrom();
        Position to = step.getTo();
        if (!isOrthogonalNeighbor(from, to)) {
            throw new IllegalMoveException("Push advance must be orthogonal");
        }
        Piece strong = occ.get(from);
        if (strong == null || strong.getSide() != side) {
            throw new IllegalMoveException("Push advance must move own piece");
        }
        if (!to.equals(weakOld)) {
            throw new IllegalMoveException("Push advance must land on weak piece origin");
        }
        if (occ.get(to) != null) {
            throw new IllegalMoveException("Push advance destination must be empty");
        }
    }

    private static void validatePullVacateOnOcc(Map<Position, Piece> occ, PlayerSide side, Step step, List<Step> ignored) {
        Position from = step.getFrom();
        Position to = step.getTo();
        if (!isOrthogonalNeighbor(from, to)) {
            throw new IllegalMoveException("Pull vacate must be orthogonal");
        }
        Piece strong = occ.get(from);
        if (strong == null || strong.getSide() != side) {
            throw new IllegalMoveException("Pull must move own piece first");
        }
        if (isFrozenOccupancy(occ, from)) {
            throw new IllegalMoveException("Frozen piece cannot pull");
        }
        if (occ.get(to) != null) {
            throw new IllegalMoveException("Pull vacate target must be empty");
        }
    }

    private static void validatePullDragOnOcc(
            Map<Position, Piece> occ, PlayerSide side, Step step, Position strongOld, Position strongNew) {
        Position from = step.getFrom();
        Position to = step.getTo();
        if (!isOrthogonalNeighbor(from, to)) {
            throw new IllegalMoveException("Pull drag must be orthogonal");
        }
        Piece weak = occ.get(from);
        if (weak == null || weak.getSide() == side) {
            throw new IllegalMoveException("Pull drag must move opponent");
        }
        if (!to.equals(strongOld)) {
            throw new IllegalMoveException("Pull drag must target vacated strong square");
        }
        if (occ.get(to) != null) {
            throw new IllegalMoveException("Pull drag destination must be empty");
        }
        Piece strong = occ.get(strongNew);
        if (strong == null || strong.getSide() != side) {
            throw new IllegalMoveException("Pull drag requires pulling piece on vacate square");
        }
        if (!PieceStrength.isStrictlyStronger(strong.getType(), weak.getType())) {
            throw new IllegalMoveException("Pull requires stronger piece");
        }
    }

    private static Position findStrongOrthNeighbor(Map<Position, Piece> occ, PlayerSide side, Position weakPos, Piece weak) {
        for (Position n : orthogonalNeighbors(weakPos)) {
            Piece q = occ.get(n);
            if (q != null && q.getSide() == side && PieceStrength.isStrictlyStronger(q.getType(), weak.getType())) {
                return n;
            }
        }
        return null;
    }

    public static StepKind kindOf(Step s) {
        StepKind k = s.getKind();
        return k == null ? StepKind.SLIDE : k;
    }

    public static boolean isFrozen(Board board, Position pos) {
        return isFrozenOccupancy(snapshotOccupancy(board), pos);
    }

    public static boolean isFrozenOccupancy(Map<Position, Piece> occ, Position pos) {
        Piece p = occ.get(pos);
        if (p == null) {
            return false;
        }
        boolean strongerEnemy = false;
        for (Position n : orthogonalNeighbors(pos)) {
            Piece q = occ.get(n);
            if (q == null) {
                continue;
            }
            if (q.getSide() == p.getSide()) {
                return false;
            }
            if (PieceStrength.isStrictlyStronger(q.getType(), p.getType())) {
                strongerEnemy = true;
            }
        }
        return strongerEnemy;
    }

    private static List<Position> orthogonalNeighbors(Position pos) {
        List<Position> list = new ArrayList<>(4);
        int f = pos.getFileIndex();
        int r = pos.getRankIndex();
        if (f + 1 < BoardConstants.BOARD_SIZE) {
            list.add(Position.of(f + 1, r));
        }
        if (f - 1 >= 0) {
            list.add(Position.of(f - 1, r));
        }
        if (r + 1 < BoardConstants.BOARD_SIZE) {
            list.add(Position.of(f, r + 1));
        }
        if (r - 1 >= 0) {
            list.add(Position.of(f, r - 1));
        }
        return list;
    }

    private static void resolveTrapsOnOccupancy(Map<Position, Piece> occ) {
        for (Position trap : BoardConstants.trapSquares()) {
            Piece victim = occ.get(trap);
            if (victim == null) {
                continue;
            }
            if (!hasOrthogonalFriendlyOcc(occ, victim.getSide(), trap)) {
                occ.remove(trap);
            }
        }
    }

    private static boolean hasOrthogonalFriendlyOcc(Map<Position, Piece> occ, PlayerSide side, Position pos) {
        for (Position n : orthogonalNeighbors(pos)) {
            Piece p = occ.get(n);
            if (p != null && p.getSide() == side) {
                return true;
            }
        }
        return false;
    }

    private static void resolveTraps(Board board, Consumer<Piece> onTrapVictim) {
        for (Position trap : BoardConstants.trapSquares()) {
            Piece victim = board.getPiece(trap);
            if (victim == null) {
                continue;
            }
            if (!hasOrthogonalFriendly(board, victim.getSide(), trap)) {
                if (onTrapVictim != null) {
                    onTrapVictim.accept(victim);
                }
                board.setPiece(trap, null);
            }
        }
    }

    private static boolean hasOrthogonalFriendly(Board board, PlayerSide side, Position pos) {
        for (Position n : orthogonalNeighbors(pos)) {
            Piece p = board.getPiece(n);
            if (p != null && p.getSide() == side) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasRabbitOnRank(Board board, PlayerSide side, int rankIndex) {
        for (int f = 0; f < BoardConstants.BOARD_SIZE; f++) {
            Piece p = board.getPiece(Position.of(f, rankIndex));
            if (p != null && p.getSide() == side && p.getType() == PieceType.RABBIT) {
                return true;
            }
        }
        return false;
    }

    private static int countRabbits(Board board, PlayerSide side) {
        int n = 0;
        for (int r = 0; r < BoardConstants.BOARD_SIZE; r++) {
            for (int f = 0; f < BoardConstants.BOARD_SIZE; f++) {
                Piece p = board.getPiece(Position.of(f, r));
                if (p != null && p.getSide() == side && p.getType() == PieceType.RABBIT) {
                    n++;
                }
            }
        }
        return n;
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

    private static Map<Position, Piece> copyOcc(Map<Position, Piece> occ) {
        return new HashMap<>(occ);
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

    private static boolean isRabbitBackward(PlayerSide side, Position from, Position to) {
        int fromR = from.getRankIndex();
        int toR = to.getRankIndex();
        return switch (side) {
            case GOLD -> toR < fromR;
            case SILVER -> toR > fromR;
        };
    }

    private static PlayerSide opponent(PlayerSide side) {
        return side == PlayerSide.GOLD ? PlayerSide.SILVER : PlayerSide.GOLD;
    }

    private static Move copyMove(Move src) {
        Move m = new Move();
        for (Step s : src.getSteps()) {
            m.getSteps().add(copyStep(s));
        }
        return m;
    }

    private static Step copyStep(Step s) {
        Step t = new Step();
        t.setFrom(s.getFrom());
        t.setTo(s.getTo());
        t.setKind(s.getKind());
        return t;
    }
}
