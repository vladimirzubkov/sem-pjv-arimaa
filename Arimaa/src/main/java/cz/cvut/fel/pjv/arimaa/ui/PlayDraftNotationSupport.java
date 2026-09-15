package cz.cvut.fel.pjv.arimaa.ui;

import cz.cvut.fel.pjv.arimaa.model.DefaultRuleEngine;
import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.GameMemento;
import cz.cvut.fel.pjv.arimaa.model.Move;
import cz.cvut.fel.pjv.arimaa.model.PlayHalfTurn;
import cz.cvut.fel.pjv.arimaa.model.PlayTurnHistory;
import cz.cvut.fel.pjv.arimaa.model.Position;
import cz.cvut.fel.pjv.arimaa.model.Step;
import cz.cvut.fel.pjv.arimaa.model.enums.StepKind;
import cz.cvut.fel.pjv.arimaa.util.ArimaaNotation;
import cz.cvut.fel.pjv.arimaa.util.BoardConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Stateless helpers for PLAY draft notation, step copying, probe games from mementos, and suffix legality checks.
 * Keeps {@link cz.cvut.fel.pjv.arimaa.controller.GameController} free of JavaFX; used from {@link MainController}.
 */
public final class PlayDraftNotationSupport {

    private PlayDraftNotationSupport() {}

    public static Move copyMove(Move src) {
        Move m = new Move();
        for (Step s : src.getSteps()) {
            m.getSteps().add(copyStep(s));
        }
        return m;
    }

    public static Step copyStep(Step s) {
        return Step.copyOf(s);
    }

    public static Game probeGameFromMemento(GameMemento m) {
        return Game.restoredFromMemento(m);
    }

    /**
     * Notation line for save / network snapshot of the trailing uncommitted PLAY draft, or {@code null} if there is
     * none. Four-step drafts include {@link ArimaaNotation#UNCOMMITTED_DRAFT_MARKER}.
     */
    public static String trailingDraftPersistLineOrNull(PlayTurnHistory ph, String prefix) {
        if (ph == null || !ph.isBootstrapped()) {
            return null;
        }
        var halves = ph.halfTurnsUnmodifiable();
        PlayHalfTurn tail = halves.get(halves.size() - 1);
        if (tail.committed() || tail.steps().isEmpty()) {
            return null;
        }
        Game probe = probeGameFromMemento(tail.startSnap());
        Move m = new Move();
        for (Step s : tail.steps()) {
            m.getSteps().add(copyStep(s));
        }
        return ArimaaNotation.formatPartialTurnLineForPersist(probe.getBoard(), m, prefix);
    }

    /**
     * Square where the side to move's active piece stands after the given prefix (same convention as when adding steps).
     */
    public static Position playNextFromAfterPrefixSteps(List<Step> steps) {
        if (steps.isEmpty()) {
            return null;
        }
        int n = steps.size();
        Step last = steps.get(n - 1);
        StepKind lk = DefaultRuleEngine.kindOf(last);
        if (lk == StepKind.PULL_DRAG_WEAKER && n >= 2) {
            Step prev = steps.get(n - 2);
            if (DefaultRuleEngine.kindOf(prev) == StepKind.SLIDE) {
                return prev.getTo();
            }
        }
        if (lk == StepKind.SLIDE) {
            return last.getTo();
        }
        if (n >= 2) {
            Step prev = steps.get(n - 2);
            StepKind pk = DefaultRuleEngine.kindOf(prev);
            if ((pk == StepKind.PUSH_DISPLACE_WEAKER && lk == StepKind.PUSH_ADVANCE_STRONGER)
                    || (pk == StepKind.PULL_VACATE_STRONGER && lk == StepKind.PULL_DRAG_WEAKER)) {
                return endOwnSquareAfterBundle(List.of(prev, last));
            }
        }
        return last.getTo();
    }

    public static Position endOwnSquareAfterBundle(List<Step> bundle) {
        Step s0 = bundle.get(0);
        StepKind k = DefaultRuleEngine.kindOf(s0);
        if (bundle.size() == 2 && k == StepKind.PUSH_DISPLACE_WEAKER) {
            return bundle.get(1).getTo();
        }
        if (bundle.size() == 2 && k == StepKind.PULL_VACATE_STRONGER) {
            return bundle.get(0).getTo();
        }
        return bundle.get(0).getTo();
    }

    public static boolean isStaticTrapSquare(Position pos) {
        for (Position trap : BoardConstants.trapSquares()) {
            if (trap.equals(pos)) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code liveGame}'s board already reflects the applied draft view — validate {@code appended} from the viewed
     * half-turn start, not by re-applying the prefix on {@code liveGame}.
     */
    public static boolean isValidPlaySuffixFromViewHalfStart(
            PlayTurnHistory ph, Game liveGame, Move partial, List<Step> appended) {
        if (ph == null || !ph.isBootstrapped() || liveGame == null) {
            return false;
        }
        PlayHalfTurn ht = ph.halfAt(ph.viewHalfIndex());
        Game probe = probeGameFromMemento(ht.startSnap());
        Move full = new Move();
        for (Step s : partial.getSteps()) {
            full.getSteps().add(copyStep(s));
        }
        for (Step s : appended) {
            full.getSteps().add(copyStep(s));
        }
        return DefaultRuleEngine.isValidPlayPrefix(probe, full);
    }

    /**
     * One visible line per half-turn; committed lines use stored notation. For the trailing draft, builds a partial
     * line from steps on the half-turn.
     *
     * @param showCpuStepsProgressively when {@code true} and {@code computerMovePending}, the last visible draft line
     *     uses only the first {@link PlayTurnHistory#appliedPrefixSteps()} steps (matches board during CPU step
     *     animation); otherwise all draft steps are shown in that line.
     */
    public static List<String> buildNotationHistoryLines(
            PlayTurnHistory ph,
            Game g,
            Supplier<String> notationPrefix,
            boolean showCpuStepsProgressively,
            boolean computerMovePending) {
        ArrayList<String> lines = new ArrayList<>();
        if (g == null || ph == null || !ph.isBootstrapped()) {
            return lines;
        }
        List<Integer> visible = ph.visibleHalfIndicesForDisplay(true);
        for (int i = 0; i < visible.size(); i++) {
            int h = visible.get(i);
            PlayHalfTurn ht = ph.halfAt(h);
            if (ht.committed()) {
                String n = ht.notationLineOrNull();
                if (n != null && !n.isBlank()) {
                    lines.add(n);
                }
            } else {
                String raw = ht.notationLineOrNull();
                if (raw != null && !raw.isBlank() && ht.steps().isEmpty()) {
                    lines.add(raw);
                } else {
                    Game probe = probeGameFromMemento(ht.startSnap());
                    Move m = new Move();
                    int total = ht.steps().size();
                    int toCopy = total;
                    if (showCpuStepsProgressively
                            && computerMovePending
                            && i == visible.size() - 1) {
                        toCopy = Math.min(Math.max(0, ph.appliedPrefixSteps()), total);
                    }
                    for (int si = 0; si < toCopy; si++) {
                        m.getSteps().add(copyStep(ht.steps().get(si)));
                    }
                    String prefix = notationPrefix.get();
                    lines.add(ArimaaNotation.formatPartialTurnLine(probe.getBoard(), m, prefix));
                }
            }
        }
        return lines;
    }
}
