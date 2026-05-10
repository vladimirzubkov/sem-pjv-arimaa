package cz.cvut.fel.pjv.arimaa.ui;

import cz.cvut.fel.pjv.arimaa.model.Position;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Legal PLAY highlight / keyboard targets derived together from the current draft and history view (one reconcile +
 * occupancy snapshot per {@link PlayLegalTargetsSupport#compute} call).
 */
public record PlayTargetBundle(
        Set<Position> legalSlideTargets,
        List<PushFirstOption> pushFirstOptions,
        Set<Position> pullWeakSquares) {

    public static PlayTargetBundle empty() {
        return new PlayTargetBundle(Set.of(), List.of(), Set.of());
    }

    /**
     * Unique first-step cells for push highlighting (several bundles may share the same {@link PushFirstOption#firstStepTo}).
     */
    public Set<Position> pushFirstStepTargets() {
        return pushFirstOptions.stream()
                .map(PushFirstOption::firstStepTo)
                .collect(Collectors.toUnmodifiableSet());
    }

    public PlayTargetBundle {
        legalSlideTargets = Collections.unmodifiableSet(legalSlideTargets);
        pushFirstOptions = List.copyOf(pushFirstOptions);
        pullWeakSquares = Collections.unmodifiableSet(pullWeakSquares);
    }
}
