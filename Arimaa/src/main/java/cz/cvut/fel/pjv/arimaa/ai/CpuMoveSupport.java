package cz.cvut.fel.pjv.arimaa.ai;

import cz.cvut.fel.pjv.arimaa.model.DefaultRuleEngine;
import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.GameMemento;
import cz.cvut.fel.pjv.arimaa.model.Move;
import cz.cvut.fel.pjv.arimaa.model.PlayHalfTurn;

import java.util.List;
import java.util.Random;

/** Shared helpers for CPU move selection after {@link SearchSession} mutates shared pieces. */
final class CpuMoveSupport {

    private CpuMoveSupport() {}

    static GameMemento baseline(Game game) {
        return GameMemento.fromGame(game);
    }

    static void restoreBaseline(Game game, GameMemento baseline) {
        game.restoreMemento(baseline);
    }

    /**
     * Returns a deep copy of {@code choice} that {@link Game#applyMove} accepts on {@code baseline}, or the first
     * {@link DefaultRuleEngine} legal turn if the search picked a grid-only candidate.
     */
    static Move engineLegalCopy(GameMemento baseline, Move choice, Random random) {
        Move copy = PlayHalfTurn.copyMove(choice);
        Game check = Game.restoredFromMemento(baseline);
        if (canApplyFullTurn(check, copy)) {
            return copy;
        }
        Game probe = Game.restoredFromMemento(baseline);
        List<Move> legal = DefaultRuleEngine.enumerateLegalCompleteMoves(probe);
        if (legal.isEmpty()) {
            throw new IllegalStateException("no legal complete moves");
        }
        return PlayHalfTurn.copyMove(legal.get(random.nextInt(legal.size())));
    }

    private static boolean canApplyFullTurn(Game game, Move move) {
        try {
            Game trial = Game.restoredFromMemento(GameMemento.fromGame(game));
            trial.applyMove(move);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
