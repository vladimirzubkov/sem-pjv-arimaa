package cz.cvut.fel.pjv.arimaa.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One PLAY half-turn: board before the turn ({@link #startSnap()}), optional committed line text, steps (possibly
 * incomplete for the trailing draft), and after a commit the memento at the end of the turn ({@link #endSnap()}).
 */
public final class PlayHalfTurn {

    private final GameMemento startSnap;
    private GameMemento endSnap;
    private final ArrayList<Step> steps = new ArrayList<>();
    private String notationLineOrNull;
    private boolean committed;

    /**
     * @param startSnap board + match flags immediately before applying this half-turn’s steps
     * @param committed {@code true} for the synthetic anchor row (index 0) or a finished half-turn
     */
    public PlayHalfTurn(GameMemento startSnap, boolean committed) {
        this.startSnap = startSnap;
        this.committed = committed;
    }

    public GameMemento startSnap() {
        return startSnap;
    }

    public GameMemento endSnap() {
        return endSnap;
    }

    public void setEndSnap(GameMemento endSnap) {
        this.endSnap = endSnap;
    }

    public List<Step> steps() {
        return steps;
    }

    public String notationLineOrNull() {
        return notationLineOrNull;
    }

    public void setNotationLineOrNull(String notationLineOrNull) {
        this.notationLineOrNull = notationLineOrNull;
    }

    public boolean committed() {
        return committed;
    }

    public void setCommitted(boolean committed) {
        this.committed = committed;
    }

    public List<Step> stepsUnmodifiable() {
        return Collections.unmodifiableList(steps);
    }

    public void clearSteps() {
        steps.clear();
    }

    public void addStep(Step s) {
        steps.add(s);
    }

    public void removeLastStep() {
        if (!steps.isEmpty()) {
            steps.removeLast();
        }
    }

    public static Step copyStep(Step s) {
        return Step.copyOf(s);
    }

    public static Move copyMove(Move src) {
        Move m = new Move();
        for (Step s : src.getSteps()) {
            m.getSteps().add(copyStep(s));
        }
        return m;
    }
}
