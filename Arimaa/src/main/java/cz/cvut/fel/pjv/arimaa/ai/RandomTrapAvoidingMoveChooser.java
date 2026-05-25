package cz.cvut.fel.pjv.arimaa.ai;

import cz.cvut.fel.pjv.arimaa.model.DefaultRuleEngine;
import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.Move;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;

import java.util.Objects;
import java.util.Optional;
import java.util.Random;

/**
 * Level-0 computer: randomized DFS for one legal turn, preferring moves that do not trap-remove own pieces; falls back
 * to any sampled legal turn after several attempts.
 */
public final class RandomTrapAvoidingMoveChooser {

    /** Attempts to sample a trap-safe move before accepting a trap-loss move. */
    public static final int TRAP_SAFE_SAMPLE_ATTEMPTS = 64;

    private RandomTrapAvoidingMoveChooser() {}

    /**
     * @param mover {@link Game#getSideToMove()} for {@code game}
     */
    public static boolean losesOwnPieceToTrap(PlayerSide mover, DefaultRuleEngine.TrapCapturePreview p) {
        Objects.requireNonNull(mover, "mover");
        Objects.requireNonNull(p, "p");
        return mover == PlayerSide.GOLD ? !p.bySilver().isEmpty() : !p.byGold().isEmpty();
    }

    /**
     * Samples trap-safe legal turns first, then any legal turn; used by {@link ComputerPlayMove} for level-0 CPU.
     */
    public static Move chooseMove(Game game, Random random) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(random, "random");
        PlayerSide mover = game.getSideToMove();
        Optional<Move> fallback = Optional.empty();
        for (int i = 0; i < TRAP_SAFE_SAMPLE_ATTEMPTS; i++) {
            Optional<Move> sampled = DefaultRuleEngine.sampleRandomLegalCompleteMove(game, random);
            if (sampled.isEmpty()) {
                break;
            }
            Move m = sampled.get();
            fallback = Optional.of(m);
            if (!losesOwnPieceToTrap(mover, DefaultRuleEngine.trapCapturesIfPrefixApplied(game, m))) {
                return m;
            }
        }
        return fallback.orElseThrow(() -> new IllegalStateException("no legal complete moves"));
    }
}
