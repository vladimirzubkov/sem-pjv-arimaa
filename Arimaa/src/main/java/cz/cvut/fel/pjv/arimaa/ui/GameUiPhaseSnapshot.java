package cz.cvut.fel.pjv.arimaa.ui;

import cz.cvut.fel.pjv.arimaa.model.Game;

/**
 * Immutable snapshot of which coarse UI phase the game is in (derived from {@link Game#getState()}).
 */
public record GameUiPhaseSnapshot(boolean setup, boolean play, boolean gameOver) {

    public static GameUiPhaseSnapshot from(Game g) {
        if (g == null) {
            return new GameUiPhaseSnapshot(false, false, false);
        }
        return new GameUiPhaseSnapshot(
                MainUiLayoutPhase.isSetup(g),
                MainUiLayoutPhase.isPlay(g),
                MainUiLayoutPhase.isGameOver(g));
    }
}
