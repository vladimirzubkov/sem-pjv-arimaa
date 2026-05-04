package cz.cvut.fel.pjv.arimaa.model;

import cz.cvut.fel.pjv.arimaa.model.enums.StepKind;

/**
 * One atomic step within a full turn (a turn contains up to four steps in Arimaa).
 */
public class Step {

    private Position from;
    private Position to;
    /** When {@code null}, treated as {@link StepKind#SLIDE} for backward compatibility. */
    private StepKind kind;

    public Position getFrom() {
        return from;
    }

    public void setFrom(Position from) {
        this.from = from;
    }

    public Position getTo() {
        return to;
    }

    public void setTo(Position to) {
        this.to = to;
    }

    public StepKind getKind() {
        return kind;
    }

    public void setKind(StepKind kind) {
        this.kind = kind;
    }
}
