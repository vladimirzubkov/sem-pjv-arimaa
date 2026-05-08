package cz.cvut.fel.pjv.arimaa.model.enums;

/**
 * Who controls a side during setup and play.
 */
public enum PlayerControllerKind {
    HUMAN,
    /** Random legal move; prefers turns that do not remove own pieces via traps. */
    COMPUTER_LEVEL_0,
    /** Maximizes a static heuristic after one full legal turn (greedy one-ply). */
    COMPUTER_LEVEL_1,
    /** Bounded minimax with alpha-beta over full turns at fixed depth (see {@link AlphaBetaComputerMove}). */
    COMPUTER_LEVEL_2;

    /** @return {@code true} if this side is driven by any CPU level */
    public boolean isComputer() {
        return this != HUMAN;
    }

    /**
     * @return CPU level index {@code 0}, {@code 1}, or {@code 2}
     * @throws IllegalStateException if {@link #HUMAN}
     */
    public int computerLevelOrThrow() {
        return switch (this) {
            case COMPUTER_LEVEL_0 -> 0;
            case COMPUTER_LEVEL_1 -> 1;
            case COMPUTER_LEVEL_2 -> 2;
            case HUMAN -> throw new IllegalStateException("not a computer controller");
        };
    }

    /**
     * Lowercase Czech phrase for side-assignment panel ("člověk" / "počítač — úroveň n").
     */
    public String assignmentDescriptionCs() {
        return switch (this) {
            case HUMAN -> "člověk";
            case COMPUTER_LEVEL_0, COMPUTER_LEVEL_1, COMPUTER_LEVEL_2 ->
                    "počítač — úroveň " + computerLevelOrThrow();
        };
    }
}
