package cz.cvut.fel.pjv.arimaa.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A full player turn: a sequence of up to four {@link Step} values.
 */
public class Move {

    private final List<Step> steps = new ArrayList<>();

    public List<Step> getSteps() {
        return steps;
    }
}
