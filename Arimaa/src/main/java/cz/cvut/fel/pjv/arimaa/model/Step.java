package cz.cvut.fel.pjv.arimaa.model;

/**
 * One atomic step within a full turn (a turn contains up to four steps in Arimaa).
 */
public class Step {

    private Position from;
    private Position to;

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
}
