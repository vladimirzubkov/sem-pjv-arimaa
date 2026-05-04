package cz.cvut.fel.pjv.arimaa.ai;

import cz.cvut.fel.pjv.arimaa.model.DefaultRuleEngine;
import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.Move;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Level-0 computer: uniform random among legal turns that do not trap-remove own pieces; if none, among all legal turns.
 */
public final class RandomTrapAvoidingMoveChooser {

    private RandomTrapAvoidingMoveChooser() {}

    /**
     * @param mover {@link Game#getSideToMove()} for {@code game}
     */
    public static boolean losesOwnPieceToTrap(PlayerSide mover, DefaultRuleEngine.TrapCapturePreview p) {
        Objects.requireNonNull(mover, "mover");
        Objects.requireNonNull(p, "p");
        return mover == PlayerSide.GOLD ? !p.bySilver().isEmpty() : !p.byGold().isEmpty();
    }

    public static Move chooseMove(Game game, Random random) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(random, "random");
        List<Move> legal = DefaultRuleEngine.enumerateLegalCompleteMoves(game);
        if (legal.isEmpty()) {
            throw new IllegalStateException("no legal complete moves");
        }
        PlayerSide mover = game.getSideToMove();
        List<Move> safe = new ArrayList<>();
        for (Move m : legal) {
            if (!losesOwnPieceToTrap(mover, DefaultRuleEngine.trapCapturesIfPrefixApplied(game, m))) {
                safe.add(m);
            }
        }
        List<Move> pool = safe.isEmpty() ? legal : safe;
        return pool.get(random.nextInt(pool.size()));
    }
}
