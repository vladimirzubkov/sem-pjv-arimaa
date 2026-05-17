package cz.cvut.fel.pjv.arimaa.model;

import cz.cvut.fel.pjv.arimaa.model.enums.PieceType;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;
import cz.cvut.fel.pjv.arimaa.model.enums.StepKind;
import cz.cvut.fel.pjv.arimaa.util.BoardConstants;
import cz.cvut.fel.pjv.arimaa.util.PieceStrength;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static cz.cvut.fel.pjv.arimaa.model.DefaultRuleEngine.kindOf;

/**
 * Legal-turn generation and validation on a flat {@code Piece[64]} board (no {@link java.util.Map} snapshots).
 * Used by CPU search and by {@link DefaultRuleEngine} after a single board snapshot.
 */
public final class GridMoveRules {

    public static final int CELL_COUNT = BoardConstants.BOARD_SIZE * BoardConstants.BOARD_SIZE;

    private static final int[] TRAP_INDICES = trapIndices();

    private GridMoveRules() {}

    public static Piece[] snapshotFromBoard(Board board) {
        Piece[] cells = new Piece[CELL_COUNT];
        for (int r = 0; r < BoardConstants.BOARD_SIZE; r++) {
            for (int f = 0; f < BoardConstants.BOARD_SIZE; f++) {
                cells[index(f, r)] = board.getPiece(Position.of(f, r));
            }
        }
        return cells;
    }

    public static boolean existsLegalTurn(Piece[] cells, PlayerSide sideToMove) {
        if (cells == null || cells.length != CELL_COUNT) {
            return false;
        }
        return dfsAnyLegalTurn(sideToMove, new Move(), cells);
    }

    public static List<Move> enumerateLegalCompleteMoves(Piece[] cells, PlayerSide sideToMove) {
        if (cells == null || cells.length != CELL_COUNT) {
            return List.of();
        }
        List<Move> out = new ArrayList<>();
        dfsCollectLegalCompleteMoves(sideToMove, new Move(), cells, out);
        return out;
    }

    public static Optional<Move> sampleRandomLegalCompleteMove(Piece[] cells, PlayerSide sideToMove, Random rnd) {
        if (cells == null || cells.length != CELL_COUNT) {
            return Optional.empty();
        }
        return dfsSampleRandomLegalCompleteMove(sideToMove, new Move(), cells, rnd);
    }

    private static Optional<Move> dfsSampleRandomLegalCompleteMove(
            PlayerSide side, Move prefix, Piece[] root, Random rnd) {
        int len = prefix.getSteps().size();
        if (len > 4) {
            return Optional.empty();
        }
        if (len == 4) {
            if (tryValidateSequentialSteps(side, copyMove(prefix), true, root).isPresent()) {
                return Optional.of(copyMove(prefix));
            }
            return Optional.empty();
        }
        Optional<Piece[]> occAfterOpt = tryValidateSequentialSteps(side, copyMove(prefix), false, root);
        if (occAfterOpt.isEmpty()) {
            return Optional.empty();
        }
        Piece[] occAfter = occAfterOpt.get();
        List<List<Step>> bundles = new ArrayList<>(enumerateStepBundles(occAfter, side));
        Collections.shuffle(bundles, rnd);
        for (List<Step> bundle : bundles) {
            Move extended = copyMove(prefix);
            for (Step st : bundle) {
                extended.getSteps().add(copyStep(st));
            }
            if (tryValidateSequentialSteps(side, copyMove(extended), false, root).isEmpty()) {
                continue;
            }
            Optional<Move> fromChild = dfsSampleRandomLegalCompleteMove(side, extended, root, rnd);
            if (fromChild.isPresent()) {
                return fromChild;
            }
        }
        if (len >= 1 && tryValidateSequentialSteps(side, copyMove(prefix), true, root).isPresent()) {
            return Optional.of(copyMove(prefix));
        }
        return Optional.empty();
    }

    private static void dfsCollectLegalCompleteMoves(PlayerSide side, Move prefix, Piece[] root, List<Move> out) {
        int len = prefix.getSteps().size();
        if (len >= 1 && len <= 4 && tryValidateSequentialSteps(side, copyMove(prefix), true, root).isPresent()) {
            out.add(copyMove(prefix));
        }
        if (len >= 4) {
            return;
        }
        Optional<Piece[]> occAfterOpt = tryValidateSequentialSteps(side, copyMove(prefix), false, root);
        if (occAfterOpt.isEmpty()) {
            return;
        }
        Piece[] occAfter = occAfterOpt.get();
        for (List<Step> bundle : enumerateStepBundles(occAfter, side)) {
            Move extended = copyMove(prefix);
            for (Step st : bundle) {
                extended.getSteps().add(copyStep(st));
            }
            if (tryValidateSequentialSteps(side, copyMove(extended), false, root).isEmpty()) {
                continue;
            }
            dfsCollectLegalCompleteMoves(side, extended, root, out);
        }
    }

    private static boolean dfsAnyLegalTurn(PlayerSide side, Move prefix, Piece[] root) {
        int len = prefix.getSteps().size();
        if (len >= 1 && len <= 4 && tryValidateSequentialSteps(side, copyMove(prefix), true, root).isPresent()) {
            return true;
        }
        if (len >= 4) {
            return false;
        }
        Optional<Piece[]> occAfterOpt = tryValidateSequentialSteps(side, copyMove(prefix), false, root);
        if (occAfterOpt.isEmpty()) {
            return false;
        }
        Piece[] occAfter = occAfterOpt.get();
        for (List<Step> bundle : enumerateStepBundles(occAfter, side)) {
            Move extended = copyMove(prefix);
            for (Step st : bundle) {
                extended.getSteps().add(copyStep(st));
            }
            if (tryValidateSequentialSteps(side, copyMove(extended), false, root).isEmpty()) {
                continue;
            }
            if (dfsAnyLegalTurn(side, extended, root)) {
                return true;
            }
        }
        return false;
    }

    static List<List<Step>> enumerateStepBundles(Piece[] occ, PlayerSide side) {
        List<List<Step>> out = new ArrayList<>();
        for (int r = 0; r < BoardConstants.BOARD_SIZE; r++) {
            for (int f = 0; f < BoardConstants.BOARD_SIZE; f++) {
                int fromIdx = index(f, r);
                Piece p = occ[fromIdx];
                if (p == null || p.getSide() != side) {
                    continue;
                }
                Position from = Position.of(f, r);
                if (!isFrozenOccupancy(occ, fromIdx)) {
                    for (Position to : orthogonalNeighbors(from)) {
                        if (occ[index(to)] != null) {
                            continue;
                        }
                        Step slide = new Step();
                        slide.setKind(StepKind.SLIDE);
                        slide.setFrom(from);
                        slide.setTo(to);
                        if (canSlideOnOcc(occ, side, slide)) {
                            out.add(List.of(copyStep(slide)));
                        }
                    }
                }
                addPushBundles(occ, side, from, p, out);
            }
        }
        addPullDragBundles(occ, side, out);
        return out;
    }

    private static void addPullDragBundles(Piece[] occ, PlayerSide side, List<List<Step>> out) {
        for (int r = 0; r < BoardConstants.BOARD_SIZE; r++) {
            for (int f = 0; f < BoardConstants.BOARD_SIZE; f++) {
                int weakIdx = index(f, r);
                Piece weak = occ[weakIdx];
                if (weak == null || weak.getSide() == side) {
                    continue;
                }
                Position weakPos = Position.of(f, r);
                for (Position vacated : orthogonalNeighbors(weakPos)) {
                    if (occ[index(vacated)] != null) {
                        continue;
                    }
                    Step drag = new Step();
                    drag.setKind(StepKind.PULL_DRAG_WEAKER);
                    drag.setFrom(weakPos);
                    drag.setTo(vacated);
                    for (Position strongNew : orthogonalNeighbors(vacated)) {
                        if (strongNew.equals(weakPos)) {
                            continue;
                        }
                        Piece strong = occ[index(strongNew)];
                        if (strong == null || strong.getSide() != side) {
                            continue;
                        }
                        if (!PieceStrength.isStrictlyStronger(strong.getType(), weak.getType())) {
                            continue;
                        }
                        Piece[] t = cloneCells(occ);
                        if (canPullDragOnOcc(t, side, drag, vacated, strongNew)) {
                            out.add(List.of(copyStep(drag)));
                        }
                    }
                }
            }
        }
    }

    private static void addPushBundles(
            Piece[] occ, PlayerSide side, Position strongPos, Piece strong, List<List<Step>> out) {
        if (strong.getSide() != side || isFrozenOccupancy(occ, index(strongPos))) {
            return;
        }
        for (Position weakPos : orthogonalNeighbors(strongPos)) {
            Piece weak = occ[index(weakPos)];
            if (weak == null || weak.getSide() == side) {
                continue;
            }
            if (!PieceStrength.isStrictlyStronger(strong.getType(), weak.getType())) {
                continue;
            }
            for (Position dest : orthogonalNeighbors(weakPos)) {
                if (dest.equals(strongPos) || occ[index(dest)] != null) {
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
                Piece[] t = cloneCells(occ);
                List<Step> buf = new ArrayList<>();
                if (canPushDisplaceOnOcc(t, side, d, buf)) {
                    applyOneStepOnOccupancy(t, d);
                    resolveTrapsOnOccupancy(t);
                    if (canPushAdvanceOnOcc(t, side, a, weakPos, strongPos)) {
                        out.add(List.of(copyStep(d), copyStep(a)));
                    }
                }
            }
        }
    }

    private static Optional<Piece[]> tryValidateSequentialSteps(
            PlayerSide side, Move move, boolean requireFullTurn, Piece[] initialCells) {
        List<Step> steps = move.getSteps();
        int n = steps.size();
        if (requireFullTurn) {
            if (n < 1 || n > 4) {
                return Optional.empty();
            }
        } else if (n < 0 || n > 4) {
            return Optional.empty();
        }
        Piece[] occ = initialCells != null ? cloneCells(initialCells) : new Piece[CELL_COUNT];
        for (int i = 0; i < n; ) {
            Step s = steps.get(i);
            StepKind k = kindOf(s);
            switch (k) {
                case SLIDE -> {
                    if (!canSlideOnOcc(occ, side, s)) {
                        return Optional.empty();
                    }
                    applyOneStepOnOccupancy(occ, s);
                    resolveTrapsOnOccupancy(occ);
                    i++;
                    if (i < n && kindOf(steps.get(i)) == StepKind.PULL_DRAG_WEAKER) {
                        Step drag = steps.get(i);
                        Step vacate = steps.get(i - 1);
                        if (!canPullDragOnOcc(occ, side, drag, vacate.getFrom(), vacate.getTo())) {
                            return Optional.empty();
                        }
                        applyOneStepOnOccupancy(occ, drag);
                        resolveTrapsOnOccupancy(occ);
                        i++;
                    }
                }
                case PUSH_DISPLACE_WEAKER -> {
                    if (!canPushDisplaceOnOcc(occ, side, s, steps.subList(0, i))) {
                        return Optional.empty();
                    }
                    applyOneStepOnOccupancy(occ, s);
                    resolveTrapsOnOccupancy(occ);
                    i++;
                    if (i >= n) {
                        return Optional.empty();
                    }
                    Step s2 = steps.get(i);
                    if (kindOf(s2) != StepKind.PUSH_ADVANCE_STRONGER) {
                        return Optional.empty();
                    }
                    if (!canPushAdvanceOnOcc(occ, side, s2, s.getFrom(), null)) {
                        return Optional.empty();
                    }
                    applyOneStepOnOccupancy(occ, s2);
                    resolveTrapsOnOccupancy(occ);
                    i++;
                }
                case PUSH_ADVANCE_STRONGER, PULL_DRAG_WEAKER -> {
                    return Optional.empty();
                }
                case PULL_VACATE_STRONGER -> {
                    if (!canPullVacateOnOcc(occ, side, s, steps.subList(0, i))) {
                        return Optional.empty();
                    }
                    Position strongOld = s.getFrom();
                    applyOneStepOnOccupancy(occ, s);
                    resolveTrapsOnOccupancy(occ);
                    i++;
                    if (i >= n) {
                        return Optional.empty();
                    }
                    Step s2 = steps.get(i);
                    if (kindOf(s2) != StepKind.PULL_DRAG_WEAKER) {
                        return Optional.empty();
                    }
                    if (!canPullDragOnOcc(occ, side, s2, strongOld, s.getTo())) {
                        return Optional.empty();
                    }
                    applyOneStepOnOccupancy(occ, s2);
                    resolveTrapsOnOccupancy(occ);
                    i++;
                }
                default -> {
                    return Optional.empty();
                }
            }
        }
        return Optional.of(occ);
    }

    private static boolean canSlideOnOcc(Piece[] occ, PlayerSide side, Step step) {
        Position from = step.getFrom();
        Position to = step.getTo();
        if (from == null || to == null) {
            return false;
        }
        if (!isOrthogonalNeighbor(from, to)) {
            return false;
        }
        int fromIdx = index(from);
        Piece moving = occ[fromIdx];
        if (moving == null || moving.getSide() != side) {
            return false;
        }
        if (isFrozenOccupancy(occ, fromIdx)) {
            return false;
        }
        if (occ[index(to)] != null) {
            return false;
        }
        return moving.getType() != PieceType.RABBIT || !isRabbitBackward(moving.getSide(), from, to);
    }

    private static boolean canPushDisplaceOnOcc(Piece[] occ, PlayerSide side, Step step, List<Step> ignored) {
        Position weakFrom = step.getFrom();
        Position weakTo = step.getTo();
        if (weakFrom == null || weakTo == null || !isOrthogonalNeighbor(weakFrom, weakTo)) {
            return false;
        }
        Piece weak = occ[index(weakFrom)];
        if (weak == null || weak.getSide() == side) {
            return false;
        }
        if (occ[index(weakTo)] != null) {
            return false;
        }
        Position strongSquare = findStrongOrthNeighbor(occ, side, weakFrom, weak);
        if (strongSquare == null) {
            return false;
        }
        return !isFrozenOccupancy(occ, index(strongSquare));
    }

    private static boolean canPushAdvanceOnOcc(
            Piece[] occ, PlayerSide side, Step step, Position weakOld, Position ignoredStrongOld) {
        Position from = step.getFrom();
        Position to = step.getTo();
        if (from == null || to == null || !isOrthogonalNeighbor(from, to)) {
            return false;
        }
        Piece strong = occ[index(from)];
        if (strong == null || strong.getSide() != side) {
            return false;
        }
        if (!to.equals(weakOld)) {
            return false;
        }
        return occ[index(to)] == null;
    }

    private static boolean canPullVacateOnOcc(Piece[] occ, PlayerSide side, Step step, List<Step> ignored) {
        Position from = step.getFrom();
        Position to = step.getTo();
        if (from == null || to == null || !isOrthogonalNeighbor(from, to)) {
            return false;
        }
        int fromIdx = index(from);
        Piece strong = occ[fromIdx];
        if (strong == null || strong.getSide() != side) {
            return false;
        }
        if (isFrozenOccupancy(occ, fromIdx)) {
            return false;
        }
        return occ[index(to)] == null;
    }

    private static boolean canPullDragOnOcc(
            Piece[] occ, PlayerSide side, Step step, Position strongOld, Position strongNew) {
        Position from = step.getFrom();
        Position to = step.getTo();
        if (from == null || to == null || !isOrthogonalNeighbor(from, to)) {
            return false;
        }
        Piece weak = occ[index(from)];
        if (weak == null || weak.getSide() == side) {
            return false;
        }
        if (!to.equals(strongOld)) {
            return false;
        }
        if (occ[index(to)] != null) {
            return false;
        }
        Piece strong = occ[index(strongNew)];
        if (strong == null || strong.getSide() != side) {
            return false;
        }
        return PieceStrength.isStrictlyStronger(strong.getType(), weak.getType());
    }

    static boolean isFrozenOccupancy(Piece[] occ, int squareIndex) {
        Piece p = occ[squareIndex];
        if (p == null) {
            return false;
        }
        boolean strongerEnemy = false;
        int f = squareIndex % BoardConstants.BOARD_SIZE;
        int r = squareIndex / BoardConstants.BOARD_SIZE;
        if (f + 1 < BoardConstants.BOARD_SIZE) {
            Piece q = occ[index(f + 1, r)];
            if (q != null) {
                if (q.getSide() == p.getSide()) {
                    return false;
                }
                if (PieceStrength.isStrictlyStronger(q.getType(), p.getType())) {
                    strongerEnemy = true;
                }
            }
        }
        if (f - 1 >= 0) {
            Piece q = occ[index(f - 1, r)];
            if (q != null) {
                if (q.getSide() == p.getSide()) {
                    return false;
                }
                if (PieceStrength.isStrictlyStronger(q.getType(), p.getType())) {
                    strongerEnemy = true;
                }
            }
        }
        if (r + 1 < BoardConstants.BOARD_SIZE) {
            Piece q = occ[index(f, r + 1)];
            if (q != null) {
                if (q.getSide() == p.getSide()) {
                    return false;
                }
                if (PieceStrength.isStrictlyStronger(q.getType(), p.getType())) {
                    strongerEnemy = true;
                }
            }
        }
        if (r - 1 >= 0) {
            Piece q = occ[index(f, r - 1)];
            if (q != null) {
                if (q.getSide() == p.getSide()) {
                    return false;
                }
                if (PieceStrength.isStrictlyStronger(q.getType(), p.getType())) {
                    strongerEnemy = true;
                }
            }
        }
        return strongerEnemy;
    }

    private static Position findStrongOrthNeighbor(Piece[] occ, PlayerSide side, Position weakPos, Piece weak) {
        for (Position n : orthogonalNeighbors(weakPos)) {
            Piece q = occ[index(n)];
            if (q != null && q.getSide() == side && PieceStrength.isStrictlyStronger(q.getType(), weak.getType())) {
                return n;
            }
        }
        return null;
    }

    private static void resolveTrapsOnOccupancy(Piece[] occ) {
        for (int trapIdx : TRAP_INDICES) {
            Piece victim = occ[trapIdx];
            if (victim == null) {
                continue;
            }
            if (!hasOrthogonalFriendlyOcc(occ, victim.getSide(), trapIdx)) {
                occ[trapIdx] = null;
            }
        }
    }

    private static boolean hasOrthogonalFriendlyOcc(Piece[] occ, PlayerSide side, int squareIndex) {
        int f = squareIndex % BoardConstants.BOARD_SIZE;
        int r = squareIndex / BoardConstants.BOARD_SIZE;
        if (f + 1 < BoardConstants.BOARD_SIZE) {
            Piece p = occ[index(f + 1, r)];
            if (p != null && p.getSide() == side) {
                return true;
            }
        }
        if (f - 1 >= 0) {
            Piece p = occ[index(f - 1, r)];
            if (p != null && p.getSide() == side) {
                return true;
            }
        }
        if (r + 1 < BoardConstants.BOARD_SIZE) {
            Piece p = occ[index(f, r + 1)];
            if (p != null && p.getSide() == side) {
                return true;
            }
        }
        if (r - 1 >= 0) {
            Piece p = occ[index(f, r - 1)];
            if (p != null && p.getSide() == side) {
                return true;
            }
        }
        return false;
    }

    private static void applyOneStepOnOccupancy(Piece[] occ, Step step) {
        int from = index(step.getFrom());
        int to = index(step.getTo());
        Piece moving = occ[from];
        if (moving != null) {
            occ[from] = null;
            occ[to] = moving;
        }
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

    static int index(Position p) {
        return index(p.getFileIndex(), p.getRankIndex());
    }

    static int index(int file, int rank) {
        return rank * BoardConstants.BOARD_SIZE + file;
    }

    private static Piece[] cloneCells(Piece[] cells) {
        return cells.clone();
    }

    private static int[] trapIndices() {
        Position[] traps = BoardConstants.trapSquares();
        int[] out = new int[traps.length];
        for (int i = 0; i < traps.length; i++) {
            out[i] = index(traps[i]);
        }
        return out;
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
