package cz.cvut.fel.pjv.arimaa.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A full player turn: a sequence of up to four {@link Step} values.
 */
public class Move {

    private final List<Step> steps = new ArrayList<>();

    /** Mutable list of steps forming this turn (up to four in PLAY). */
    public List<Step> getSteps() {
        return steps;
    }
}
