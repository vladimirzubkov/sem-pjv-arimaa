package cz.cvut.fel.pjv.arimaa.ui;

import cz.cvut.fel.pjv.arimaa.model.DefaultRuleEngine;
import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.Move;
import cz.cvut.fel.pjv.arimaa.model.Piece;
import cz.cvut.fel.pjv.arimaa.model.Position;
import cz.cvut.fel.pjv.arimaa.model.Step;
import cz.cvut.fel.pjv.arimaa.model.enums.StepKind;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;
import cz.cvut.fel.pjv.arimaa.util.BoardConstants;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Computes PLAY legal target sets for the current selection in one pass (single reconcile + single occupancy snapshot).
 */
public final class PlayLegalTargetsSupport {

    private PlayLegalTargetsSupport() {}

    public static boolean bundleStartsFromPlayNext(Position playNextFrom, List<Step> bundle) {
        if (playNextFrom == null || bundle.isEmpty()) {
            return false;
        }
        Step s0 = bundle.get(0);
        StepKind k = DefaultRuleEngine.kindOf(s0);
        return switch (k) {
            case SLIDE -> playNextFrom.equals(s0.getFrom());
            case PUSH_DISPLACE_WEAKER ->
                    bundle.size() >= 2 && playNextFrom.equals(bundle.get(1).getFrom());
            case PULL_VACATE_STRONGER -> playNextFrom.equals(s0.getFrom());
            default -> false;
        };
    }

    /**
     * @param reconcile run once before reading draft (e.g. mirror partial to history view)
     * @param isValidSuffix validate appended steps from the viewed half-turn start
     */
    public static PlayTargetBundle compute(
            Game g,
            PlayTurnDraftState draft,
            Runnable reconcile,
            BiPredicate<Game, List<Step>> isValidSuffix) {
        reconcile.run();
        if (draft.nextFrom == null) {
            return PlayTargetBundle.empty();
        }
        int remaining = 4 - draft.partial.getSteps().size();
        if (remaining < 1) {
            return PlayTargetBundle.empty();
        }
        Map<Position, Piece> occ;
        try {
            occ = DefaultRuleEngine.simulatePlayPrefix(g, new Move());
        } catch (IllegalArgumentException ex) {
            return PlayTargetBundle.empty();
        }
        PlayerSide side = g.getSideToMove();
        Position nextFrom = draft.nextFrom;

        Set<Position> slides = new HashSet<>();
        if (remaining >= 1) {
            int f = nextFrom.getFileIndex();
            int r = nextFrom.getRankIndex();
            int[][] deltas = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : deltas) {
                int nf = f + d[0];
                int nr = r + d[1];
                if (nf < 0 || nf >= BoardConstants.BOARD_SIZE || nr < 0 || nr >= BoardConstants.BOARD_SIZE) {
                    continue;
                }
                Position to = Position.of(nf, nr);
                if (occ.get(to) != null) {
                    continue;
                }
                Step step = new Step();
                step.setKind(StepKind.SLIDE);
                step.setFrom(nextFrom);
                step.setTo(to);
                if (isValidSuffix.test(g, List.of(step))) {
                    slides.add(to);
                }
            }
        }
        if (remaining >= 2) {
            for (List<Step> bundle : DefaultRuleEngine.enumerateStepBundles(occ, side)) {
                if (bundle.size() > remaining) {
                    continue;
                }
                if (!bundleStartsFromPlayNext(nextFrom, bundle)) {
                    continue;
                }
                if (isValidSuffix.test(g, bundle)) {
                    slides.add(bundle.get(0).getTo());
                }
            }
        }

        Set<Position> pushFirst = new HashSet<>();
        if (remaining >= 2) {
            for (List<Step> bundle : DefaultRuleEngine.enumerateStepBundles(occ, side)) {
                if (bundle.size() > remaining) {
                    continue;
                }
                if (!bundleStartsFromPlayNext(nextFrom, bundle)) {
                    continue;
                }
                if (DefaultRuleEngine.kindOf(bundle.get(0)) != StepKind.PUSH_DISPLACE_WEAKER) {
                    continue;
                }
                if (isValidSuffix.test(g, bundle)) {
                    pushFirst.add(bundle.get(0).getTo());
                }
            }
        }

        Set<Position> pulls = new HashSet<>();
        List<Step> steps = draft.partial.getSteps();
        if (!steps.isEmpty() && steps.size() < 4) {
            Step last = steps.get(steps.size() - 1);
            if (DefaultRuleEngine.kindOf(last) == StepKind.SLIDE) {
                Position vacated = last.getFrom();
                int vf = vacated.getFileIndex();
                int vr = vacated.getRankIndex();
                int[][] deltas = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                for (int[] d : deltas) {
                    int nf = vf + d[0];
                    int nr = vr + d[1];
                    if (nf < 0 || nf >= BoardConstants.BOARD_SIZE || nr < 0 || nr >= BoardConstants.BOARD_SIZE) {
                        continue;
                    }
                    Position weakPos = Position.of(nf, nr);
                    Step drag = new Step();
                    drag.setKind(StepKind.PULL_DRAG_WEAKER);
                    drag.setFrom(weakPos);
                    drag.setTo(vacated);
                    if (isValidSuffix.test(g, List.of(drag))) {
                        pulls.add(weakPos);
                    }
                }
            }
        }

        return new PlayTargetBundle(slides, pushFirst, pulls);
    }
}
