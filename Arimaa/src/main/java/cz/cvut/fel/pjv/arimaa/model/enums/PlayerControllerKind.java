package cz.cvut.fel.pjv.arimaa.model.enums;

/**
 * Who controls a side during setup and play.
 */
public enum PlayerControllerKind {
    HUMAN,
    /** Random legal move; prefers turns that do not remove own pieces via traps. */
    COMPUTER_LEVEL_0
}
