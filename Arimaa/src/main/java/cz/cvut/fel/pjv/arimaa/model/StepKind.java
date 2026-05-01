package cz.cvut.fel.pjv.arimaa.model;

/**
 * How a {@link Step} mutates the board. Push and pull each use two {@link Step} entries per Arimaa rules.
 */
public enum StepKind {
    /** Move own piece to an empty orthogonal neighbour (subject to rabbit and freezing rules). */
    SLIDE,
    /** First half of a push: move the weaker opponent to an empty square (two steps total). */
    PUSH_DISPLACE_WEAKER,
    /** Second half of a push: move the stronger own piece into the square the weaker piece left. */
    PUSH_ADVANCE_STRONGER,
    /** First half of a pull: move the stronger own piece to an empty square. */
    PULL_VACATE_STRONGER,
    /** Second half of a pull: move the weaker opponent into the square the stronger piece left. */
    PULL_DRAG_WEAKER
}
