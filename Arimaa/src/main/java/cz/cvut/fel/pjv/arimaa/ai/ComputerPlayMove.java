package cz.cvut.fel.pjv.arimaa.ai;

import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.Move;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerControllerKind;

import java.util.Objects;
import java.util.Random;

/** Dispatches PLAY move selection by CPU difficulty. */
public final class ComputerPlayMove {

    private ComputerPlayMove() {}

    /**
     * Selects a full legal turn for the side to move in {@code game}.
     *
     * @param cpuKind must be a {@link PlayerControllerKind#isComputer() computer} kind
     */
    public static Move selectPlayMove(PlayerControllerKind cpuKind, Game game, Random random) {
        Objects.requireNonNull(cpuKind, "cpuKind");
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(random, "random");
        if (!cpuKind.isComputer()) {
            throw new IllegalArgumentException("not a computer kind: " + cpuKind);
        }
        return switch (cpuKind) {
            case COMPUTER_LEVEL_0 -> RandomTrapAvoidingMoveChooser.chooseMove(game, random);
            case COMPUTER_LEVEL_1 -> GreedyComputerMove.chooseMove(game, random);
            case COMPUTER_LEVEL_2 -> AlphaBetaComputerMove.chooseMove(game, random);
            case HUMAN -> throw new IllegalArgumentException("not a computer kind");
        };
    }
}
