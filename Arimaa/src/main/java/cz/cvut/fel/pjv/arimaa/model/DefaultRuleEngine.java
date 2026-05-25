package cz.cvut.fel.pjv.arimaa.model;

import cz.cvut.fel.pjv.arimaa.exception.GamePhaseException;
import cz.cvut.fel.pjv.arimaa.exception.IllegalMoveException;
import cz.cvut.fel.pjv.arimaa.model.enums.GameState;
import cz.cvut.fel.pjv.arimaa.model.enums.PieceType;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;
import cz.cvut.fel.pjv.arimaa.model.enums.StepKind;
import cz.cvut.fel.pjv.arimaa.util.BoardConstants;
import cz.cvut.fel.pjv.arimaa.util.PieceStrength;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Full Arimaa play rules implementation: slide/push/pull generation, freezing, trap resolution after each step,
 * notation tokens compatible with {@link cz.cvut.fel.pjv.arimaa.util.ArimaaNotation}. Used as the sole {@link RuleEngine}
 * implementation in this project.
 */
public final class DefaultRuleEngine implements RuleEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultRuleEngine.class);

    @Override
    /** Provede celý PLAY tah včetně pastí a přepnutí strany. Použití: {@link Game#applyMove}. */
    public void applyMove(Game game, Move move) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(move, "move");
        if (game.getState() != GameState.PLAY) {
            throw new GamePhaseException("applyMove only in PLAY");
        }
        PlayerSide mover = game.getSideToMove();
        if (move.getSteps().isEmpty()) {
            log.info("applyMove: pass (no steps)");
          /* Po mutaci desky: konec hry nebo přepnutí strany a imobilizace. Použití: applyMove. */
            finishPlayTurnAfterBoardMutation(game, mover);
            return;
        }
        log.info("applyMove: mover={} stepCount={}", mover, move.getSteps().size());
        log.debug("applyMove detail: {}", describeMove(move));
      /* Validuje kroky a vrátí obsazení nebo hodí výjimku. Použití: applyMove. */
        validateSequentialSteps(game, move, true, null);
      /** Aplikuje kroky na desku (volající musí validovat). Použití: applyMove, trapCapturesIfPrefixApplied. */
        applyMoveToBoard(game.getBoard(), move, game::recordTrapRemoval);
        Board board = game.getBoard();
        log.debug(
                "after applyMoveToBoard: goldRabbits={} silverRabbits={}",
              /* Počet králíků strany. Použití: evaluateTerminalWithReason, log. */
                countRabbits(board, PlayerSide.GOLD),
                countRabbits(board, PlayerSide.SILVER));
      /* Po mutaci desky: konec hry nebo přepnutí strany a imobilizace. Použití: applyMove. */
        finishPlayTurnAfterBoardMutation(game, mover);
    }

    private void finishPlayTurnAfterBoardMutation(Game game, PlayerSide mover) {
        Board board = game.getBoard();
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

    @Override
    /** Aplikuje legální prefix bez ukončení tahu. Použití: {@link Game#applyPlayPrefix}. */
    public void applyPlayPrefix(Game game, Move prefix) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(prefix, "prefix");
        if (game.getState() != GameState.PLAY) {
            throw new GamePhaseException("applyPlayPrefix only in PLAY");
        }
        PlayerSide mover = game.getSideToMove();
        log.info("applyPlayPrefix: mover={} stepCount={}", mover, prefix.getSteps().size());
      /* Validuje kroky a vrátí obsazení nebo hodí výjimku. Použití: applyMove. */
        validateSequentialSteps(game, prefix, false, null);
      /** Aplikuje kroky na desku (volající musí validovat). Použití: applyMove, trapCapturesIfPrefixApplied. */
        applyMoveToBoard(game.getBoard(), prefix, game::recordTrapRemoval);
        Board board = game.getBoard();
        TerminalEvaluation terminal = evaluateTerminalWithReason(board);
        if (terminal.winner() != null) {
            log.info("game over after prefix: winner={} reason={}", terminal.winner(), terminal.reason());
            game.setMatchWinner(terminal.winner());
            game.setState(GameState.GAME_OVER);
        }
    }

    /**
     * Applies the move steps to {@code board} (mutation). Caller validates first.
     *
     * @param onTrapVictim invoked for each piece removed by a trap before the square is cleared; may be {@code null}
     */
    /** Aplikuje kroky na desku (volající musí validovat). Použití: applyMove, trapCapturesIfPrefixApplied. */
    static void applyMoveToBoard(Board board, Move move, Consumer<Piece> onTrapVictim) {
        List<Step> steps = move.getSteps();
        for (int i = 0; i < steps.size(); i++) {
            Step s = steps.get(i);
            StepKind k = kindOf(s);
            if (k == StepKind.SLIDE) {
              /* Jeden krok na {@link Board}. Použití: applyMoveToBoard, notace. */
                applyOneStep(board, s);
              /* Pasti na {@link Board} s callbackem oběti. Použití: applyMoveToBoard, notace. */
                resolveTraps(board, onTrapVictim);
                if (i + 1 < steps.size() && kindOf(steps.get(i + 1)) == StepKind.PULL_DRAG_WEAKER) {
                    i++;
                    applyOneStep(board, steps.get(i));
                    resolveTraps(board, onTrapVictim);
                }
            } else if (k == StepKind.PUSH_DISPLACE_WEAKER) {
                applyOneStep(board, s);
              /* Pasti na {@link Board} s callbackem oběti. Použití: applyMoveToBoard, notace. */
                resolveTraps(board, onTrapVictim);
                i++;
                applyOneStep(board, steps.get(i));
                resolveTraps(board, onTrapVictim);
            } else if (k == StepKind.PULL_VACATE_STRONGER) {
                applyOneStep(board, s);
                resolveTraps(board, onTrapVictim);
                i++;
              /* Jeden krok na {@link Board}. Použití: applyMoveToBoard, notace. */
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
    /** Náhled obětí v pasti po prefixu na kopii desky. Použití: {@link PlayTurnHistory#viewPrefixRemovesPieceViaTrap}, AI. */
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
      /** Aplikuje kroky na desku (volající musí validovat). Použití: applyMove, trapCapturesIfPrefixApplied. */
        applyMoveToBoard(scratch, prefix, victim -> {
            if (victim.getSide() == PlayerSide.SILVER) {
                byGold.add(victim.getType());
            } else {
                bySilver.add(victim.getType());
            }
        });
        return new TrapCapturePreview(List.copyOf(byGold), List.copyOf(bySilver));
    }
    /** Zda je prefix legální bez výjimky. Použití: UI draft, {@link PlayDraftUiCoordinator}. */
    public static boolean isValidPlayPrefix(Game game, Move move) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(move, "move");
        if (game.getState() != GameState.PLAY) {
            return false;
        }
        boolean ok = tryValidateSequentialSteps(game, move, false, null).isPresent();
        if (!ok) {
            log.debug("invalid play prefix");
        }
        return ok;
    }

    /**
     * Occupancy after legally applying {@code prefix} (same rules as the engine, including traps after each step).
     * Empty prefix returns a copy of the current board occupancy.
     *
     * @throws GamePhaseException if {@code game} is not in {@link GameState#PLAY}
     * @throws IllegalMoveException if {@code prefix} is illegal
     */
    /** Obsazení po legálním prefixu. Použití: {@link PlayPhaseUiHandler}, testy. */
    public static Map<Position, Piece> simulatePlayPrefix(Game game, Move prefix) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(prefix, "prefix");
        if (game.getState() != GameState.PLAY) {
            throw new GamePhaseException("simulatePlayPrefix only in PLAY");
        }
        Map<Position, Piece> root = snapshotOccupancy(game.getBoard());
        if (prefix.getSteps().isEmpty()) {
            /* Kopie mapy obsazení. Použití: validace, enumerateStepBundles. */
            return copyOcc(root);
        }
        /* Ne-hodící validace kroků pro generování tahů. Použití: isValidPlayPrefix, simulatePlayPrefix. */
        return tryValidateSequentialSteps(game, copyMove(prefix), false, root)
                .orElseThrow(
                        () -> {
                            log.debug("simulatePlayPrefix rejected");
                            return new IllegalMoveException("Illegal play prefix");
                        });
    }

    /**
     * Whether {@code side} has at least one legal full turn (1–4 steps) from {@code game}'s current board.
     */
    /** Zda má strana na tahu alespoň jeden legální tah. Použití: AI, konec hry. */
    public static boolean existsLegalTurn(Game game) {
        Objects.requireNonNull(game, "game");
        if (game.getState() != GameState.PLAY) {
            return false;
        }
        return GridMoveRules.existsLegalTurn(
                GridMoveRules.snapshotFromBoard(game.getBoard()), game.getSideToMove());
    }

    /**
     * All legal full turns (1–4 steps) from the current position in {@link GameState#PLAY}.
     *
     * @return mutable list (may be large); empty if not in PLAY
     */
    /** Všechny legální plné tahy (1–4 kroky). Použití: AI, testy. */
    public static List<Move> enumerateLegalCompleteMoves(Game game) {
        Objects.requireNonNull(game, "game");
        if (game.getState() != GameState.PLAY) {
            return new ArrayList<>();
        }
        return GridMoveRules.enumerateLegalCompleteMoves(
                GridMoveRules.snapshotFromBoard(game.getBoard()), game.getSideToMove());
    }

    /**
     * One legal full turn sampled by randomized DFS (shuffled step bundles at each depth). Tries extending the
     * prefix before accepting a complete turn, so multi-step turns are not skipped whenever a one-step completion
     * exists. Not uniformly random over all legal turns. Empty if not in {@link GameState#PLAY} or no legal turn exists.
     */
    /** Náhodný legální tah (DFS se shuffle). Použití: CPU level 0, GreedyComputerMove. */
    public static Optional<Move> sampleRandomLegalCompleteMove(Game game, Random rnd) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(rnd, "rnd");
        if (game.getState() != GameState.PLAY) {
            return Optional.empty();
        }
        return GridMoveRules.sampleRandomLegalCompleteMove(
                GridMoveRules.snapshotFromBoard(game.getBoard()), game.getSideToMove(), rnd);
    }
    /* DFS výběr náhodného tahu na mapě obsazení. Použití: sampleRandomLegalCompleteMove (nepoužito — delegace na GridMoveRules). */
    private static Optional<Move> dfsSampleRandomLegalCompleteMove(Game game, Move prefix, Map<Position, Piece> root, Random rnd) {
        int len = prefix.getSteps().size();
        if (len > 4) {
            return Optional.empty();
        }
        if (len == 4) {
            if (tryValidateSequentialSteps(game, copyMove(prefix), true, root).isPresent()) {
                return Optional.of(copyMove(prefix));
            }
            return Optional.empty();
        }
        Optional<Map<Position, Piece>> occAfterOpt =
              /* Ne-hodící validace kroků pro generování tahů. Použití: isValidPlayPrefix, simulatePlayPrefix. */
                tryValidateSequentialSteps(game, copyMove(prefix), false, root);
        if (occAfterOpt.isEmpty()) {
            return Optional.empty();
        }
        Map<Position, Piece> occAfter = occAfterOpt.get();
        List<List<Step>> bundles = new ArrayList<>(enumerateStepBundles(occAfter, game.getSideToMove()));
        Collections.shuffle(bundles, rnd);
        for (List<Step> bundle : bundles) {
            Move extended = copyMove(prefix);
            for (Step st : bundle) {
                extended.getSteps().add(copyStep(st));
            }
            if (tryValidateSequentialSteps(game, copyMove(extended), false, root).isEmpty()) {
                continue;
            }
            Optional<Move> fromChild = dfsSampleRandomLegalCompleteMove(game, extended, root, rnd);
            if (fromChild.isPresent()) {
                return fromChild;
            }
        }
        if (len >= 1 && tryValidateSequentialSteps(game, copyMove(prefix), true, root).isPresent()) {
            return Optional.of(copyMove(prefix));
        }
        return Optional.empty();
    }
    /* DFS sběr všech tahů. Použití: enumerateLegalCompleteMoves (nepoužito). */
    private static void dfsCollectLegalCompleteMoves(Game game, Move prefix, Map<Position, Piece> root, List<Move> out) {
        int len = prefix.getSteps().size();
        if (len >= 1 && len <= 4 && tryValidateSequentialSteps(game, copyMove(prefix), true, root).isPresent()) {
            out.add(copyMove(prefix));
        }
        if (len >= 4) {
            return;
        }
        Optional<Map<Position, Piece>> occAfterOpt =
              /* Ne-hodící validace kroků pro generování tahů. Použití: isValidPlayPrefix, simulatePlayPrefix. */
                tryValidateSequentialSteps(game, copyMove(prefix), false, root);
        if (occAfterOpt.isEmpty()) {
            return;
        }
        Map<Position, Piece> occAfter = occAfterOpt.get();
        for (List<Step> bundle : enumerateStepBundles(occAfter, game.getSideToMove())) {
            Move extended = copyMove(prefix);
            for (Step st : bundle) {
                extended.getSteps().add(copyStep(st));
            }
            if (tryValidateSequentialSteps(game, copyMove(extended), false, root).isEmpty()) {
                continue;
            }
          /* DFS sběr všech tahů. Použití: enumerateLegalCompleteMoves (nepoužito). */
            dfsCollectLegalCompleteMoves(game, extended, root, out);
        }
    }
    /* DFS existence tahu. Použití: existsLegalTurn (nepoužito). */
    private static boolean dfsAnyLegalTurn(Game game, Move prefix, Map<Position, Piece> root) {
        int len = prefix.getSteps().size();
        if (len >= 1 && len <= 4 && tryValidateSequentialSteps(game, copyMove(prefix), true, root).isPresent()) {
            return true;
        }
        if (len >= 4) {
            return false;
        }
        Optional<Map<Position, Piece>> occAfterOpt =
              /* Ne-hodící validace kroků pro generování tahů. Použití: isValidPlayPrefix, simulatePlayPrefix. */
                tryValidateSequentialSteps(game, copyMove(prefix), false, root);
        if (occAfterOpt.isEmpty()) {
            return false;
        }
        Map<Position, Piece> occAfter = occAfterOpt.get();
        for (List<Step> bundle : enumerateStepBundles(occAfter, game.getSideToMove())) {
            Move extended = copyMove(prefix);
            for (Step st : bundle) {
                extended.getSteps().add(copyStep(st));
            }
            if (tryValidateSequentialSteps(game, copyMove(extended), false, root).isEmpty()) {
                continue;
            }
            if (dfsAnyLegalTurn(game, extended, root)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Enumerates single-slide bundles, two-step push bundles, and single-step pull-drag continuations.
     */
    /** Seznam možných balíčků kroků z pozice. Použití: {@link PlayPhaseUiHandler}, generování tahů. */
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
                        if (canSlideOnOcc(occ, side, slide)) {
                            out.add(List.of(copyStep(slide)));
                        }
                    }
                }
              /* Generuje dvoukrokové push balíčky. Použití: enumerateStepBundles. */
                addPushBundles(occ, side, from, p, out);
            }
        }
      /* Doplňuje pull-drag pokračování po slide. Použití: enumerateStepBundles. */
        addPullDragBundles(occ, side, out);
        return out;
    }

    /**
     * Single-step {@link StepKind#PULL_DRAG_WEAKER} continuations (after a prior slide vacated next to a weaker piece).
     * Used by {@link #existsLegalTurn}; interactive pull completion uses the same validation.
     */
    /* Doplňuje pull-drag pokračování po slide. Použití: enumerateStepBundles. */
    private static void addPullDragBundles(Map<Position, Piece> occ, PlayerSide side, List<List<Step>> out) {
        for (int r = 0; r < BoardConstants.BOARD_SIZE; r++) {
            for (int f = 0; f < BoardConstants.BOARD_SIZE; f++) {
                Position weakPos = Position.of(f, r);
                Piece weak = occ.get(weakPos);
                if (weak == null || weak.getSide() == side) {
                    continue;
                }
                for (Position vacated : orthogonalNeighbors(weakPos)) {
                    if (occ.get(vacated) != null) {
                        continue;
                    }
                    Step drag = new Step();
                    drag.setKind(StepKind.PULL_DRAG_WEAKER);
                    drag.setFrom(weakPos);
                    drag.setTo(vacated);
                    // The strong piece vacated `vacated` and is now at one of its other orthogonal
                    // neighbours (strongNew).  Search neighbours of `vacated` (not weakPos) because
                    // after the slide the strong piece may no longer be adjacent to weakPos.
                    for (Position strongNew : orthogonalNeighbors(vacated)) {
                        if (strongNew.equals(weakPos)) {
                            continue;
                        }
                        Piece strong = occ.get(strongNew);
                        if (strong == null || strong.getSide() != side) {
                            continue;
                        }
                        if (!PieceStrength.isStrictlyStronger(strong.getType(), weak.getType())) {
                            continue;
                        }
                        Map<Position, Piece> t = copyOcc(occ);
                        if (canPullDragOnOcc(t, side, drag, vacated, strongNew)) {
                            out.add(List.of(copyStep(drag)));
                        }
                    }
                }
            }
        }
    }
    /* Generuje dvoukrokové push balíčky. Použití: enumerateStepBundles. */
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
                Map<Position, Piece> t = copyOcc(occ);
                List<Step> buf = new ArrayList<>();
                if (canPushDisplaceOnOcc(t, side, d, buf)) {
                  /* Jeden krok na mapě. Použití: tryValidateSequentialSteps. */
                    applyOneStepOnOccupancy(t, d);
                  /* Odstraní oběti pastí na mapě. Použití: tryValidateSequentialSteps. */
                    resolveTrapsOnOccupancy(t);
                    if (canPushAdvanceOnOcc(t, side, a, weakPos, strongPos)) {
                        out.add(List.of(copyStep(d), copyStep(a)));
                    }
                }
            }
        }
    }
    /* Validuje kroky a vrátí obsazení nebo hodí výjimku. Použití: applyMove. */
    private static Map<Position, Piece> validateSequentialSteps(
            Game game, Move move, boolean requireFullTurn, Map<Position, Piece> initialOcc) {
        /* Ne-hodící validace kroků pro generování tahů. Použití: isValidPlayPrefix, simulatePlayPrefix. */
        return tryValidateSequentialSteps(game, move, requireFullTurn, initialOcc)
                .orElseThrow(
                        () ->
                              /* Validace s konkrétní IllegalMoveException. Použití: validateSequentialSteps. */
                                validateSequentialStepsWithMessage(game, move, requireFullTurn, initialOcc));
    }

    /**
     * Non-throwing validation for move generation and search (expected illegal prefixes return empty).
     */
    /* Ne-hodící validace kroků pro generování tahů. Použití: isValidPlayPrefix, simulatePlayPrefix. */
    private static Optional<Map<Position, Piece>> tryValidateSequentialSteps(
            Game game, Move move, boolean requireFullTurn, Map<Position, Piece> initialOcc) {
        List<Step> steps = move.getSteps();
        int n = steps.size();
        if (requireFullTurn) {
            if (n < 1 || n > 4) {
                return Optional.empty();
            }
        } else if (n < 0 || n > 4) {
            return Optional.empty();
        }
        PlayerSide side = game.getSideToMove();
        Map<Position, Piece> occ = initialOcc != null ? copyOcc(initialOcc) : snapshotOccupancy(game.getBoard());
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

    /* Slide + volitelný pull-drag na mapě. Použití: tryValidateSequentialSteps. */
    private static Optional<Integer> tryApplySlideWithOptionalPullDrag(
            Map<Position, Piece> occ,
            PlayerSide side,
            List<Step> steps,
            int slideIndex,
            int stepCount) {
        Step slide = steps.get(slideIndex);
        if (!canSlideOnOcc(occ, side, slide)) {
            return Optional.empty();
        }
      /* Jeden krok na mapě. Použití: tryValidateSequentialSteps. */
        applyOneStepOnOccupancy(occ, slide);
      /* Odstraní oběti pastí na mapě. Použití: tryValidateSequentialSteps. */
        resolveTrapsOnOccupancy(occ);
        int next = slideIndex + 1;
        if (next < stepCount && kindOf(steps.get(next)) == StepKind.PULL_DRAG_WEAKER) {
            Step drag = steps.get(next);
            if (!canPullDragOnOcc(occ, side, drag, slide.getFrom(), slide.getTo())) {
                return Optional.empty();
            }
          /* Jeden krok na mapě. Použití: tryValidateSequentialSteps. */
            applyOneStepOnOccupancy(occ, drag);
          /* Odstraní oběti pastí na mapě. Použití: tryValidateSequentialSteps. */
            resolveTrapsOnOccupancy(occ);
            next++;
        }
        return Optional.of(next);
    }

    /* Push pár na mapě. Použití: tryValidateSequentialSteps. */
    private static Optional<Integer> tryApplyPushPair(
            Map<Position, Piece> occ,
            PlayerSide side,
            List<Step> steps,
            int displaceIndex,
            int stepCount) {
        Step displace = steps.get(displaceIndex);
        if (!canPushDisplaceOnOcc(occ, side, displace, steps.subList(0, displaceIndex))) {
            return Optional.empty();
        }
      /* Jeden krok na mapě. Použití: tryValidateSequentialSteps. */
        applyOneStepOnOccupancy(occ, displace);
      /* Odstraní oběti pastí na mapě. Použití: tryValidateSequentialSteps. */
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
      /* Jeden krok na mapě. Použití: tryValidateSequentialSteps. */
        applyOneStepOnOccupancy(occ, advance);
      /* Odstraní oběti pastí na mapě. Použití: tryValidateSequentialSteps. */
        resolveTrapsOnOccupancy(occ);
        return Optional.of(advanceIndex + 1);
    }

    /* Pull pár na mapě. Použití: tryValidateSequentialSteps. */
    private static Optional<Integer> tryApplyPullPair(
            Map<Position, Piece> occ,
            PlayerSide side,
            List<Step> steps,
            int vacateIndex,
            int stepCount) {
        Step vacate = steps.get(vacateIndex);
        if (!canPullVacateOnOcc(occ, side, vacate, steps.subList(0, vacateIndex))) {
            return Optional.empty();
        }
        Position strongOld = vacate.getFrom();
      /* Jeden krok na mapě. Použití: tryValidateSequentialSteps. */
        applyOneStepOnOccupancy(occ, vacate);
      /* Odstraní oběti pastí na mapě. Použití: tryValidateSequentialSteps. */
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
      /* Jeden krok na mapě. Použití: tryValidateSequentialSteps. */
        applyOneStepOnOccupancy(occ, drag);
      /* Odstraní oběti pastí na mapě. Použití: tryValidateSequentialSteps. */
        resolveTrapsOnOccupancy(occ);
        return Optional.of(dragIndex + 1);
    }

    /* Validace s konkrétní IllegalMoveException. Použití: validateSequentialSteps. */
    private static IllegalMoveException validateSequentialStepsWithMessage(
            Game game, Move move, boolean requireFullTurn, Map<Position, Piece> initialOcc) {
        List<Step> steps = move.getSteps();
        int n = steps.size();
        if (requireFullTurn) {
            if (n < 1 || n > 4) {
                return new IllegalMoveException("Turn must have 1–4 steps, got %d".formatted(n));
            }
        } else if (n < 0 || n > 4) {
            return new IllegalMoveException("Prefix may have at most 4 steps, got %d".formatted(n));
        }
        PlayerSide side = game.getSideToMove();
        Map<Position, Piece> occ = initialOcc != null ? copyOcc(initialOcc) : snapshotOccupancy(game.getBoard());
        log.debug("validateSequentialSteps requireFullTurn={} stepCount={} side={}", requireFullTurn, n, side);
        for (int i = 0; i < n; ) {
            try {
                i =
                        switch (kindOf(steps.get(i))) {
                            case SLIDE -> applySlideWithOptionalPullDragOrThrow(occ, side, steps, i, n);
                            case PUSH_DISPLACE_WEAKER -> applyPushPairOrThrow(occ, side, steps, i, n);
                            case PULL_VACATE_STRONGER -> applyPullPairOrThrow(occ, side, steps, i, n);
                            case PUSH_ADVANCE_STRONGER -> throw new IllegalMoveException("PUSH_ADVANCE without PUSH_DISPLACE");
                            case PULL_DRAG_WEAKER -> throw new IllegalMoveException("PULL_DRAG must follow SLIDE or PULL_VACATE");
                            default -> throw new IllegalMoveException("Unknown kind");
                        };
            } catch (IllegalMoveException ex) {
                return ex;
            }
        }
        throw new IllegalStateException("unreachable");
    }
    /* Slide (+ pull) s výjimkou. Použití: validateSequentialStepsWithMessage. */
    private static int applySlideWithOptionalPullDragOrThrow(
            Map<Position, Piece> occ,
            PlayerSide side,
            List<Step> steps,
            int slideIndex,
            int stepCount) {
        Step slide = steps.get(slideIndex);
      /* Slide validace s výjimkou. Použití: applySlideWithOptionalPullDragOrThrow. */
        validateSlideOnOcc(occ, side, slide);
      /* Jeden krok na mapě. Použití: tryValidateSequentialSteps. */
        applyOneStepOnOccupancy(occ, slide);
      /* Odstraní oběti pastí na mapě. Použití: tryValidateSequentialSteps. */
        resolveTrapsOnOccupancy(occ);
        int next = slideIndex + 1;
        if (next < stepCount && kindOf(steps.get(next)) == StepKind.PULL_DRAG_WEAKER) {
            Step drag = steps.get(next);
          /* Pull drag s výjimkou. Použití: applyPullPairOrThrow. */
            validatePullDragOnOcc(occ, side, drag, slide.getFrom(), slide.getTo());
            applyOneStepOnOccupancy(occ, drag);
            resolveTrapsOnOccupancy(occ);
            next++;
        }
        return next;
    }
    /* Push pár s výjimkou. Použití: validateSequentialStepsWithMessage. */
    private static int applyPushPairOrThrow(
            Map<Position, Piece> occ,
            PlayerSide side,
            List<Step> steps,
            int displaceIndex,
            int stepCount) {
        Step displace = steps.get(displaceIndex);
      /* Push displace s výjimkou. Použití: applyPushPairOrThrow. */
        validatePushDisplaceOnOcc(occ, side, displace, steps.subList(0, displaceIndex));
      /* Jeden krok na mapě. Použití: tryValidateSequentialSteps. */
        applyOneStepOnOccupancy(occ, displace);
      /* Odstraní oběti pastí na mapě. Použití: tryValidateSequentialSteps. */
        resolveTrapsOnOccupancy(occ);
        int advanceIndex = displaceIndex + 1;
        if (advanceIndex >= stepCount) {
            throw new IllegalMoveException("Push missing advance step");
        }
        Step advance = steps.get(advanceIndex);
        if (kindOf(advance) != StepKind.PUSH_ADVANCE_STRONGER) {
            throw new IllegalMoveException("Push must be followed by PUSH_ADVANCE_STRONGER");
        }
      /* Push advance s výjimkou. Použití: applyPushPairOrThrow. */
        validatePushAdvanceOnOcc(occ, side, advance, displace.getFrom(), null);
      /* Jeden krok na mapě. Použití: tryValidateSequentialSteps. */
        applyOneStepOnOccupancy(occ, advance);
      /* Odstraní oběti pastí na mapě. Použití: tryValidateSequentialSteps. */
        resolveTrapsOnOccupancy(occ);
        return advanceIndex + 1;
    }
    /* Pull pár s výjimkou. Použití: validateSequentialStepsWithMessage. */
    private static int applyPullPairOrThrow(
            Map<Position, Piece> occ,
            PlayerSide side,
            List<Step> steps,
            int vacateIndex,
            int stepCount) {
        Step vacate = steps.get(vacateIndex);
      /* Pull vacate s výjimkou. Použití: applyPullPairOrThrow. */
        validatePullVacateOnOcc(occ, side, vacate, steps.subList(0, vacateIndex));
        Position strongOld = vacate.getFrom();
      /* Jeden krok na mapě. Použití: tryValidateSequentialSteps. */
        applyOneStepOnOccupancy(occ, vacate);
      /* Odstraní oběti pastí na mapě. Použití: tryValidateSequentialSteps. */
        resolveTrapsOnOccupancy(occ);
        int dragIndex = vacateIndex + 1;
        if (dragIndex >= stepCount) {
            throw new IllegalMoveException("Pull missing drag step");
        }
        Step drag = steps.get(dragIndex);
        if (kindOf(drag) != StepKind.PULL_DRAG_WEAKER) {
            throw new IllegalMoveException("Pull must be followed by PULL_DRAG_WEAKER");
        }
      /* Pull drag s výjimkou. Použití: applyPullPairOrThrow. */
        validatePullDragOnOcc(occ, side, drag, strongOld, vacate.getTo());
      /* Jeden krok na mapě. Použití: tryValidateSequentialSteps. */
        applyOneStepOnOccupancy(occ, drag);
      /* Odstraní oběti pastí na mapě. Použití: tryValidateSequentialSteps. */
        resolveTrapsOnOccupancy(occ);
        return dragIndex + 1;
    }
    /* Debug řetězec tahu. Použití: applyMove log. */
    private static String describeMove(Move move) {
        return move.getSteps().stream()
                .map(s -> "%s %s->%s".formatted(kindOf(s), s.getFrom(), s.getTo()))
                .collect(Collectors.joining("; "));
    }

    private record TerminalEvaluation(PlayerSide winner, String reason) {
    }
    /* Vyhodnotí vítěze (cíl, králíci). Použití: finishPlayTurnAfterBoardMutation. */
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
    /* Legálnost slide na mapě obsazení. Použití: tryApplySlide, enumerateStepBundles. */
    private static boolean canSlideOnOcc(Map<Position, Piece> occ, PlayerSide side, Step step) {
        Position from = step.getFrom();
        Position to = step.getTo();
        if (from == null || to == null) {
            return false;
        }
        if (!isOrthogonalNeighbor(from, to)) {
            return false;
        }
        Piece moving = occ.get(from);
        if (moving == null || moving.getSide() != side) {
            return false;
        }
        if (isFrozenOccupancy(occ, from)) {
            return false;
        }
        if (occ.get(to) != null) {
            return false;
        }
        return moving.getType() != PieceType.RABBIT || !isRabbitBackward(moving.getSide(), from, to);
    }
    /* Slide validace s výjimkou. Použití: applySlideWithOptionalPullDragOrThrow. */
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
    /* První krok push na mapě. Použití: tryApplyPushPair, addPushBundles. */
    private static boolean canPushDisplaceOnOcc(Map<Position, Piece> occ, PlayerSide side, Step step, List<Step> ignored) {
        Position weakFrom = step.getFrom();
        Position weakTo = step.getTo();
        if (weakFrom == null || weakTo == null || !isOrthogonalNeighbor(weakFrom, weakTo)) {
            return false;
        }
        Piece weak = occ.get(weakFrom);
        if (weak == null || weak.getSide() == side) {
            return false;
        }
        if (occ.get(weakTo) != null) {
            return false;
        }
        Position strongSquare = findStrongOrthNeighbor(occ, side, weakFrom, weak);
        if (strongSquare == null) {
            return false;
        }
        return !isFrozenOccupancy(occ, strongSquare);
    }
    /* Push displace s výjimkou. Použití: applyPushPairOrThrow. */
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
    /* Druhý krok push na mapě. Použití: tryApplyPushPair. */
    private static boolean canPushAdvanceOnOcc(
            Map<Position, Piece> occ, PlayerSide side, Step step, Position weakOld, Position ignoredStrongOld) {
        Position from = step.getFrom();
        Position to = step.getTo();
        if (from == null || to == null || !isOrthogonalNeighbor(from, to)) {
            return false;
        }
        Piece strong = occ.get(from);
        if (strong == null || strong.getSide() != side) {
            return false;
        }
        if (!to.equals(weakOld)) {
            return false;
        }
        return occ.get(to) == null;
    }
    /* Push advance s výjimkou. Použití: applyPushPairOrThrow. */
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
    /* Pull vacate na mapě. Použití: tryApplyPullPair. */
    private static boolean canPullVacateOnOcc(Map<Position, Piece> occ, PlayerSide side, Step step, List<Step> ignored) {
        Position from = step.getFrom();
        Position to = step.getTo();
        if (from == null || to == null || !isOrthogonalNeighbor(from, to)) {
            return false;
        }
        Piece strong = occ.get(from);
        if (strong == null || strong.getSide() != side) {
            return false;
        }
        if (isFrozenOccupancy(occ, from)) {
            return false;
        }
        return occ.get(to) == null;
    }
    /* Pull vacate s výjimkou. Použití: applyPullPairOrThrow. */
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
    /* Pull drag na mapě. Použití: tryApplyPullPair, addPullDragBundles. */
    private static boolean canPullDragOnOcc(
            Map<Position, Piece> occ, PlayerSide side, Step step, Position strongOld, Position strongNew) {
        Position from = step.getFrom();
        Position to = step.getTo();
        if (from == null || to == null || !isOrthogonalNeighbor(from, to)) {
            return false;
        }
        Piece weak = occ.get(from);
        if (weak == null || weak.getSide() == side) {
            return false;
        }
        if (!to.equals(strongOld)) {
            return false;
        }
        if (occ.get(to) != null) {
            return false;
        }
        Piece strong = occ.get(strongNew);
        if (strong == null || strong.getSide() != side) {
            return false;
        }
        return PieceStrength.isStrictlyStronger(strong.getType(), weak.getType());
    }
    /* Pull drag s výjimkou. Použití: applyPullPairOrThrow. */
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
    /* Silnější vlastní soused slabšího pro push/pull. Použití: canPushDisplaceOnOcc. */
    private static Position findStrongOrthNeighbor(Map<Position, Piece> occ, PlayerSide side, Position weakPos, Piece weak) {
        for (Position n : orthogonalNeighbors(weakPos)) {
            Piece q = occ.get(n);
            if (q != null && q.getSide() == side && PieceStrength.isStrictlyStronger(q.getType(), weak.getType())) {
                return n;
            }
        }
        return null;
    }
    /** Druh kroku; null → SLIDE. Použití: celý engine, {@link SearchGrid}, notace. */
    public static StepKind kindOf(Step s) {
        StepKind k = s.getKind();
        return k == null ? StepKind.SLIDE : k;
    }
    /** Zmrazení figurky na desce. Použití: {@link BoardGridView}, testy. */
    public static boolean isFrozen(Board board, Position pos) {
        /** Zmrazení dle mapy obsazení. Použití: enumerateStepBundles, GridMoveRules. */
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
    /* Čtyři ortogonální sousedy pole. Použití: zmrazení, generování tahů. */
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
    /* Odstraní oběti pastí na mapě. Použití: tryValidateSequentialSteps. */
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
    /* Sousední vlastní na mapě (past). Použití: resolveTrapsOnOccupancy. */
    private static boolean hasOrthogonalFriendlyOcc(Map<Position, Piece> occ, PlayerSide side, Position pos) {
        for (Position n : orthogonalNeighbors(pos)) {
            Piece p = occ.get(n);
            if (p != null && p.getSide() == side) {
                return true;
            }
        }
        return false;
    }
    /* Pasti na {@link Board} s callbackem oběti. Použití: applyMoveToBoard, notace. */
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

    /**
     * Arimaa game notation body (space-separated tokens) for a completed legal turn — slides, trap removals ({@code …​x}),
     * push/pull pairs — matching trap resolution order in {@link #applyMoveToBoard}.
     */
    /** Tělo notace tahu včetně pastí. Použití: {@link ArimaaNotation}. */
    public static String buildArimaaNotationBody(Board before, Move move) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(move, "move");
        Board board = before.copy();
        StringBuilder sb = new StringBuilder();
        List<Step> steps = move.getSteps();
        for (int i = 0; i < steps.size(); i++) {
            Step s = steps.get(i);
            StepKind k = kindOf(s);
            if (k == StepKind.SLIDE) {
              /* Přidá token kroku do StringBuilder. Použití: buildArimaaNotationBody. */
                appendStepNotation(sb, board, s);
              /* Jeden krok na {@link Board}. Použití: applyMoveToBoard, notace. */
                applyOneStep(board, s);
              /* Přidá tokeny pastí po kroku. Použití: buildArimaaNotationBody. */
                appendTrapNotation(sb, board);
                if (i + 1 < steps.size() && kindOf(steps.get(i + 1)) == StepKind.PULL_DRAG_WEAKER) {
                    i++;
                    Step s2 = steps.get(i);
                    appendStepNotation(sb, board, s2);
                    applyOneStep(board, s2);
                    appendTrapNotation(sb, board);
                }
            } else if (k == StepKind.PUSH_DISPLACE_WEAKER) {
              /* Přidá token kroku do StringBuilder. Použití: buildArimaaNotationBody. */
                appendStepNotation(sb, board, s);
                applyOneStep(board, s);
                appendTrapNotation(sb, board);
                i++;
                Step s2 = steps.get(i);
                appendStepNotation(sb, board, s2);
                applyOneStep(board, s2);
                appendTrapNotation(sb, board);
            } else if (k == StepKind.PULL_VACATE_STRONGER) {
              /* Přidá token kroku do StringBuilder. Použití: buildArimaaNotationBody. */
                appendStepNotation(sb, board, s);
                applyOneStep(board, s);
                appendTrapNotation(sb, board);
                i++;
                Step s2 = steps.get(i);
                appendStepNotation(sb, board, s2);
                applyOneStep(board, s2);
                appendTrapNotation(sb, board);
            }
        }
        return sb.toString().trim();
    }
    /* Přidá token kroku do StringBuilder. Použití: buildArimaaNotationBody. */
    private static void appendStepNotation(StringBuilder sb, Board board, Step step) {
        Piece p = board.getPiece(step.getFrom());
        if (p == null) {
            throw new IllegalStateException("notation: empty from square");
        }
        if (sb.length() > 0) {
            sb.append(' ');
        }
        sb.append(pieceNotationLetter(p))
                .append(step.getFrom().toAlgebraic())
                .append(directionLetter(step.getFrom(), step.getTo()));
    }
    /* Přidá tokeny pastí po kroku. Použití: buildArimaaNotationBody. */
    private static void appendTrapNotation(StringBuilder sb, Board board) {
      /* Pasti na {@link Board} s callbackem oběti. Použití: applyMoveToBoard, notace. */
        resolveTraps(board, victim -> {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            Position at = victim.getPosition();
            if (at == null) {
                throw new IllegalStateException("notation: trap victim without position");
            }
            sb.append(pieceNotationLetter(victim)).append(at.toAlgebraic()).append('x');
        });
    }
    /* Písmeno figurky pro notaci. Použití: appendStepNotation. */
    private static String pieceNotationLetter(Piece p) {
        char c = switch (p.getType()) {
            case ELEPHANT -> 'E';
            case CAMEL -> 'M';
            case HORSE -> 'H';
            case DOG -> 'D';
            case CAT -> 'C';
            case RABBIT -> 'R';
        };
        return String.valueOf(p.getSide() == PlayerSide.GOLD ? c : Character.toLowerCase(c));
    }
    /* Směr n/e/s/w mezi poli. Použití: appendStepNotation. */
    private static char directionLetter(Position from, Position to) {
        int df = to.getFileIndex() - from.getFileIndex();
        int dr = to.getRankIndex() - from.getRankIndex();
        if (df == 1) {
            return 'e';
        }
        if (df == -1) {
            return 'w';
        }
        if (dr == 1) {
            return 'n';
        }
        if (dr == -1) {
            return 's';
        }
        throw new IllegalArgumentException("notation: non-orthogonal step");
    }
    /* Sousední vlastní na desce. Použití: resolveTraps. */
    private static boolean hasOrthogonalFriendly(Board board, PlayerSide side, Position pos) {
        for (Position n : orthogonalNeighbors(pos)) {
            Piece p = board.getPiece(n);
            if (p != null && p.getSide() == side) {
                return true;
            }
        }
        return false;
    }
    /* Králík strany na dané řadě. Použití: evaluateTerminalWithReason. */
    private static boolean hasRabbitOnRank(Board board, PlayerSide side, int rankIndex) {
        for (int f = 0; f < BoardConstants.BOARD_SIZE; f++) {
            Piece p = board.getPiece(Position.of(f, rankIndex));
            if (p != null && p.getSide() == side && p.getType() == PieceType.RABBIT) {
                return true;
            }
        }
        return false;
    }
    /* Počet králíků strany. Použití: evaluateTerminalWithReason, log. */
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
    /* Mapa obsazení z desky. Použití: validace, simulatePlayPrefix. */
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
    /* Kopie mapy obsazení. Použití: validace, enumerateStepBundles. */
    private static Map<Position, Piece> copyOcc(Map<Position, Piece> occ) {
        return new HashMap<>(occ);
    }
    /* Jeden krok na mapě. Použití: tryValidateSequentialSteps. */
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
    /* Zda jsou pole ortogonální sousedé. Použití: slide/push/pull pravidla. */
    private static boolean isOrthogonalNeighbor(Position a, Position b) {
        int df = Math.abs(a.getFileIndex() - b.getFileIndex());
        int dr = Math.abs(a.getRankIndex() - b.getRankIndex());
        return df + dr == 1;
    }
    /* Zda králík jde pozpátku. Použití: canSlideOnOcc. */
    private static boolean isRabbitBackward(PlayerSide side, Position from, Position to) {
        int fromR = from.getRankIndex();
        int toR = to.getRankIndex();
        return switch (side) {
            case GOLD -> toR < fromR;
            case SILVER -> toR > fromR;
        };
    }
    /* Protistrana. Použití: finishPlayTurnAfterBoardMutation. */
    private static PlayerSide opponent(PlayerSide side) {
        return side == PlayerSide.GOLD ? PlayerSide.SILVER : PlayerSide.GOLD;
    }
    /* Kopie {@link Move}. Použití: DFS generování tahů. */
    private static Move copyMove(Move src) {
        Move m = new Move();
        for (Step s : src.getSteps()) {
            m.getSteps().add(copyStep(s));
        }
        return m;
    }
    /* Kopie {@link Step}. Použití: copyMove, applyMoveToBoard. */
    private static Step copyStep(Step s) {
        Step t = new Step();
        t.setFrom(s.getFrom());
        t.setTo(s.getTo());
        t.setKind(s.getKind());
        return t;
    }
}
