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

    /** Pole 64 figur z desky. Použití: {@link DefaultRuleEngine} delegace, AI {@link SearchGrid}. */
    public static Piece[] snapshotFromBoard(Board board) {
        Piece[] cells = new Piece[CELL_COUNT];
        for (int r = 0; r < BoardConstants.BOARD_SIZE; r++) {
            for (int f = 0; f < BoardConstants.BOARD_SIZE; f++) {
                cells[index(f, r)] = board.getPiece(Position.of(f, r));
            }
        }
        return cells;
    }

    /** Existence legálního tahu na mřížce. Použití: {@link DefaultRuleEngine#existsLegalTurn}. */
    public static boolean existsLegalTurn(Piece[] cells, PlayerSide sideToMove) {
        if (cells == null || cells.length != CELL_COUNT) {
            return false;
        }
        /* DFS existence tahu. Použití: existsLegalTurn. */
        return dfsAnyLegalTurn(sideToMove, new Move(), cells);
    }

    /** Všechny legální tahy na mřížce. Použití: DefaultRuleEngine, AI. */
    public static List<Move> enumerateLegalCompleteMoves(Piece[] cells, PlayerSide sideToMove) {
        if (cells == null || cells.length != CELL_COUNT) {
            return List.of();
        }
        List<Move> out = new ArrayList<>();
      /* DFS sběr tahů. Použití: enumerateLegalCompleteMoves. */
        dfsCollectLegalCompleteMoves(sideToMove, new Move(), cells, out);
        return out;
    }

    /** Randomized DFS sample of one legal turn; used by {@link DefaultRuleEngine#sampleRandomLegalCompleteMove}. */
    public static Optional<Move> sampleRandomLegalCompleteMove(Piece[] cells, PlayerSide sideToMove, Random rnd) {
        if (cells == null || cells.length != CELL_COUNT) {
            return Optional.empty();
        }
        /* DFS náhodný tah na Piece[]. Použití: sampleRandomLegalCompleteMove. */
        return dfsSampleRandomLegalCompleteMove(sideToMove, new Move(), cells, rnd);
    }

    /* Random DFS over step bundles on flat occupancy (shared with {@link DefaultRuleEngine} logic). */
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

    /* DFS listing of every legal complete turn extending {@code prefix} (can be large). */
    /* DFS sběr tahů. Použití: enumerateLegalCompleteMoves. */
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
          /* DFS sběr tahů. Použití: enumerateLegalCompleteMoves. */
            dfsCollectLegalCompleteMoves(side, extended, root, out);
        }
    }

    /* Early exit: any legal completion from {@code prefix} on {@code root}. */
    /* DFS existence tahu. Použití: existsLegalTurn. */
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

    /** Balíčky kroků na Piece[]. Použití: DefaultRuleEngine (nepřímo přes vlastní kopii). */
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
              /* Push balíčky na mřížce. Použití: enumerateStepBundles. */
                addPushBundles(occ, side, from, p, out);
            }
        }
      /* Pull-drag balíčky na mřížce. Použití: enumerateStepBundles. */
        addPullDragBundles(occ, side, out);
        return out;
    }

    /* Pull-drag second steps after a slide vacated next to a capturable weaker enemy (flat board). */
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

    /* Two-step push candidates from {@code strong} at {@code strongPos} on flat {@code occ}. */
    /* Push balíčky na mřížce. Použití: enumerateStepBundles. */
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
                  /* Jeden krok na Piece[]. Použití: tryApply*. */
                    applyOneStepOnOccupancy(t, d);
                  /* Pasti na Piece[]. Použití: tryValidateSequentialSteps. */
                    resolveTrapsOnOccupancy(t);
                    if (canPushAdvanceOnOcc(t, side, a, weakPos, strongPos)) {
                        out.add(List.of(copyStep(d), copyStep(a)));
                    }
                }
            }
        }
    }

    /* Simulates full/partial turn on a cell clone; returns final cells or empty if illegal (CPU + enumeration). */
    /* Validace kroků na Piece[]. Použití: DFS generátory. */
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
            Optional<Integer> next =
                    switch (kindOf(steps.get(i))) {
                        case SLIDE -> tryApplySlideWithOptionalPullDrag(occ, side, steps, i, n);
                        case PUSH_DISPLACE_WEAKER -> tryApplyPushPair(occ, side, steps, i, n);
                        case PULL_VACATE_STRONGER -> tryApplyPullPair(occ, side, steps, i, n);
                        case PUSH_ADVANCE_STRONGER, PULL_DRAG_WEAKER -> Optional.empty();
                        default -> Optional.empty();
                    };
            if (next.isEmpty()) {
                return Optional.empty();
            }
            i = next.get();
        }
        return Optional.of(occ);
    }

    /* Applies slide plus optional pull-drag on mutable {@code occ}; returns next step index or empty. */
    /* Slide+pull na mřížce. Použití: tryValidateSequentialSteps. */
    private static Optional<Integer> tryApplySlideWithOptionalPullDrag(
            Piece[] occ, PlayerSide side, List<Step> steps, int slideIndex, int stepCount) {
        Step slide = steps.get(slideIndex);
        if (!canSlideOnOcc(occ, side, slide)) {
            return Optional.empty();
        }
      /* Jeden krok na Piece[]. Použití: tryApply*. */
        applyOneStepOnOccupancy(occ, slide);
      /* Pasti na Piece[]. Použití: tryValidateSequentialSteps. */
        resolveTrapsOnOccupancy(occ);
        int next = slideIndex + 1;
        if (next < stepCount && kindOf(steps.get(next)) == StepKind.PULL_DRAG_WEAKER) {
            Step drag = steps.get(next);
            if (!canPullDragOnOcc(occ, side, drag, slide.getFrom(), slide.getTo())) {
                return Optional.empty();
            }
          /* Jeden krok na Piece[]. Použití: tryApply*. */
            applyOneStepOnOccupancy(occ, drag);
          /* Pasti na Piece[]. Použití: tryValidateSequentialSteps. */
            resolveTrapsOnOccupancy(occ);
            next++;
        }
        return Optional.of(next);
    }

    /* Push displace + advance pair on {@code occ}; returns index after both steps or empty. */
    /* Push pár na mřížce. Použití: tryValidateSequentialSteps. */
    private static Optional<Integer> tryApplyPushPair(
            Piece[] occ, PlayerSide side, List<Step> steps, int displaceIndex, int stepCount) {
        Step displace = steps.get(displaceIndex);
        if (!canPushDisplaceOnOcc(occ, side, displace, steps.subList(0, displaceIndex))) {
            return Optional.empty();
        }
      /* Jeden krok na Piece[]. Použití: tryApply*. */
        applyOneStepOnOccupancy(occ, displace);
      /* Pasti na Piece[]. Použití: tryValidateSequentialSteps. */
        resolveTrapsOnOccupancy(occ);
        int advanceIndex = displaceIndex + 1;
        if (advanceIndex >= stepCount) {
            return Optional.empty();
        }
        Step advance = steps.get(advanceIndex);
        if (kindOf(advance) != StepKind.PUSH_ADVANCE_STRONGER) {
            return Optional.empty();
        }
        if (!canPushAdvanceOnOcc(occ, side, advance, displace.getFrom(), null)) {
            return Optional.empty();
        }
      /* Jeden krok na Piece[]. Použití: tryApply*. */
        applyOneStepOnOccupancy(occ, advance);
      /* Pasti na Piece[]. Použití: tryValidateSequentialSteps. */
        resolveTrapsOnOccupancy(occ);
        return Optional.of(advanceIndex + 1);
    }

    /* Pull vacate + drag pair on {@code occ}; returns index after both steps or empty. */
    /* Pull pár na mřížce. Použití: tryValidateSequentialSteps. */
    private static Optional<Integer> tryApplyPullPair(
            Piece[] occ, PlayerSide side, List<Step> steps, int vacateIndex, int stepCount) {
        Step vacate = steps.get(vacateIndex);
        if (!canPullVacateOnOcc(occ, side, vacate, steps.subList(0, vacateIndex))) {
            return Optional.empty();
        }
        Position strongOld = vacate.getFrom();
      /* Jeden krok na Piece[]. Použití: tryApply*. */
        applyOneStepOnOccupancy(occ, vacate);
      /* Pasti na Piece[]. Použití: tryValidateSequentialSteps. */
        resolveTrapsOnOccupancy(occ);
        int dragIndex = vacateIndex + 1;
        if (dragIndex >= stepCount) {
            return Optional.empty();
        }
        Step drag = steps.get(dragIndex);
        if (kindOf(drag) != StepKind.PULL_DRAG_WEAKER) {
            return Optional.empty();
        }
        if (!canPullDragOnOcc(occ, side, drag, strongOld, vacate.getTo())) {
            return Optional.empty();
        }
      /* Jeden krok na Piece[]. Použití: tryApply*. */
        applyOneStepOnOccupancy(occ, drag);
      /* Pasti na Piece[]. Použití: tryValidateSequentialSteps. */
        resolveTrapsOnOccupancy(occ);
        return Optional.of(dragIndex + 1);
    }

    /* Slide legality on {@code Piece[64]} (mirrors {@link DefaultRuleEngine} map rules). */
    /* Legálnost slide na Piece[]. Použití: enumerateStepBundles. */
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

    /* Push displace legality on flat occupancy (stronger neighbor unfrozen). */
    /* Push displace na Piece[]. */
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

    /* Second half of push: strong piece enters {@code weakOld} if empty. */
    /* Push advance na Piece[]. */
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

    /* Pull vacate: own stronger slides to empty square from non-frozen departure. */
    /* Pull vacate na Piece[]. */
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

    /* Pull drag: weaker enters square the strong vacated; strength check vs piece at {@code strongNew}. */
    /* Pull drag na Piece[]. */
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

    /**
     * Frozen if no friendly orth neighbor and at least one strictly stronger enemy orth neighbor (flat board).
     * Used by movegen and {@link SearchGrid}-style logic.
     */
    /** Zmrazení na indexu mřížky. Použití: enumerateStepBundles, SearchGrid. */
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

    /* Orthogonally adjacent stronger friendly for push/pull on {@code occ}. */
    /* Silnější soused pro push. Použití: canPushDisplaceOnOcc. */
    private static Position findStrongOrthNeighbor(Piece[] occ, PlayerSide side, Position weakPos, Piece weak) {
        for (Position n : orthogonalNeighbors(weakPos)) {
            Piece q = occ[index(n)];
            if (q != null && q.getSide() == side && PieceStrength.isStrictlyStronger(q.getType(), weak.getType())) {
                return n;
            }
        }
        return null;
    }

    /* Clears unsupported trap squares on {@code occ} after a step (no capture bookkeeping). */
    /* Pasti na Piece[]. Použití: tryValidateSequentialSteps. */
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

    /* Trap support on flat grid: any same-side piece orthogonally adjacent to {@code squareIndex}. */
    /* Vlastní soused na mřížce. Použití: resolveTrapsOnOccupancy. */
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

    /* Moves piece between cell indices on the search array (shared with {@link SearchGrid} semantics). */
    /* Jeden krok na Piece[]. Použití: tryApply*. */
    private static void applyOneStepOnOccupancy(Piece[] occ, Step step) {
        int from = index(step.getFrom());
        int to = index(step.getTo());
        Piece moving = occ[from];
        if (moving != null) {
            occ[from] = null;
            occ[to] = moving;
        }
    }

    /* In-bounds orthogonal neighbors of {@code pos} for step generation. */
    /* Sousedé pole. Použití: generování tahů. */
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

    /* Manhattan distance 1 on the board. */
    /* Ortogonální sousedství. Použití: can* metody. */
    private static boolean isOrthogonalNeighbor(Position a, Position b) {
        int df = Math.abs(a.getFileIndex() - b.getFileIndex());
        int dr = Math.abs(a.getRankIndex() - b.getRankIndex());
        return df + dr == 1;
    }

    /* Rabbit backward move check in model rank coordinates. */
    /* Králík pozpátku. Použití: canSlideOnOcc. */
    private static boolean isRabbitBackward(PlayerSide side, Position from, Position to) {
        int fromR = from.getRankIndex();
        int toR = to.getRankIndex();
        return switch (side) {
            case GOLD -> toR < fromR;
            case SILVER -> toR > fromR;
        };
    }
    /** Index pole v poli 64. Použití: celá třída GridMoveRules. */
    static int index(Position p) {
        return index(p.getFileIndex(), p.getRankIndex());
    }

    static int index(int file, int rank) {
        return rank * BoardConstants.BOARD_SIZE + file;
    }

    /* Shallow array clone before mutating occupancy in validation (piece references preserved). */
    /* Kopie pole figur. Použití: tryValidate, enumerate. */
    private static Piece[] cloneCells(Piece[] cells) {
        return cells.clone();
    }

    /* Maps standard trap squares to linear indices for {@link #resolveTrapsOnOccupancy}. */
    /* Indexy pastí. Použití: resolveTrapsOnOccupancy. */
    private static int[] trapIndices() {
        Position[] traps = BoardConstants.trapSquares();
        int[] out = new int[traps.length];
        for (int i = 0; i < traps.length; i++) {
            out[i] = index(traps[i]);
        }
        return out;
    }

    /* Deep copy of steps for DFS branches on {@code Piece[64]}. */
    /* Kopie Move. Použití: DFS. */
    private static Move copyMove(Move src) {
        Move m = new Move();
        for (Step s : src.getSteps()) {
            m.getSteps().add(copyStep(s));
        }
        return m;
    }

    /* Step copy for flat-board simulation (positions + kind). */
    /* Kopie Step. Použití: copyMove. */
    private static Step copyStep(Step s) {
        Step t = new Step();
        t.setFrom(s.getFrom());
        t.setTo(s.getTo());
        t.setKind(s.getKind());
        return t;
    }
}
