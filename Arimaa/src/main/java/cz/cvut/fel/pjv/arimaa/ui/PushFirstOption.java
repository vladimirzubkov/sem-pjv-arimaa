package cz.cvut.fel.pjv.arimaa.ui;

import cz.cvut.fel.pjv.arimaa.model.Position;

import java.util.Objects;

/**
 * One legal push bundle's first displacement: the weaker piece moves from {@code weakerFrom} onto empty
 * {@code firstStepTo}.
 */
public record PushFirstOption(Position firstStepTo, Position weakerFrom) {

    public PushFirstOption {
        firstStepTo = Objects.requireNonNull(firstStepTo, "firstStepTo");
        weakerFrom = Objects.requireNonNull(weakerFrom, "weakerFrom");
    }
}
