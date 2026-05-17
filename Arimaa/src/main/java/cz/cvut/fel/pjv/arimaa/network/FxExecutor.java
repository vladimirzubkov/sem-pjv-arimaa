package cz.cvut.fel.pjv.arimaa.network;

/**
 * Runs UI-bound work on the JavaFX application thread (injected from the UI layer).
 */
@FunctionalInterface
public interface FxExecutor {

    void runOnUiThread(Runnable action);
}
