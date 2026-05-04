package cz.cvut.fel.pjv.arimaa.ui;

import cz.cvut.fel.pjv.arimaa.model.Position;

import java.util.Collections;
import java.util.Set;

/**
 * Legal PLAY highlight / keyboard targets derived together from the current draft and history view (one reconcile +
 * occupancy snapshot per {@link PlayLegalTargetsSupport#compute} call).
 */
public record PlayTargetBundle(
        Set<Position> legalSlideTargets,
        Set<Position> pushFirstStepTargets,
        Set<Position> pullWeakSquares) {

    public static PlayTargetBundle empty() {
        return new PlayTargetBundle(Set.of(), Set.of(), Set.of());
    }

    public PlayTargetBundle {
        legalSlideTargets = Collections.unmodifiableSet(legalSlideTargets);
        pushFirstStepTargets = Collections.unmodifiableSet(pushFirstStepTargets);
        pullWeakSquares = Collections.unmodifiableSet(pullWeakSquares);
    }
}
