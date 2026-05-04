package cz.cvut.fel.pjv.arimaa.ui;

import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.enums.GameState;

/**
 * Derives coarse UI layout flags from {@link Game#getState()} so panels do not repeat the same switch logic.
 */
public final class MainUiLayoutPhase {

    private MainUiLayoutPhase() {}

    public static boolean isSetup(Game g) {
        return g != null
                && (g.getState() == GameState.SETUP_GOLD || g.getState() == GameState.SETUP_SILVER);
    }

    public static boolean isPlay(Game g) {
        return g != null && g.getState() == GameState.PLAY;
    }

    public static boolean isGameOver(Game g) {
        return g != null && g.getState() == GameState.GAME_OVER;
    }

    public static boolean showCapturesAndNotationHistory(Game g) {
        return g != null && (g.getState() == GameState.PLAY || g.getState() == GameState.GAME_OVER);
    }
}
