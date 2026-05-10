package cz.cvut.fel.pjv.arimaa.ui;

import cz.cvut.fel.pjv.arimaa.model.Move;
import cz.cvut.fel.pjv.arimaa.model.Position;

/**
 * Mutable bundle for the in-progress PLAY turn draft: partial steps and keyboard/cursor positions.
 */
public final class PlayTurnDraftState {

    public final Move partial = new Move();
    public Position nextFrom;
    public Position activeSegmentOrigin;
    public Position keyboardPullFocus;
    public Position keyboardPushFocus;
    /** When non-null with {@link #keyboardPushFocus}, disambiguates multiple push bundles to the same first-step cell. */
    public Position keyboardPushWeakFrom;

    public void clearTurnPositions() {
        nextFrom = null;
        activeSegmentOrigin = null;
        keyboardPullFocus = null;
        keyboardPushFocus = null;
        keyboardPushWeakFrom = null;
    }

    public void clearPartialAndTurnPositions() {
        partial.getSteps().clear();
        clearTurnPositions();
    }
}
