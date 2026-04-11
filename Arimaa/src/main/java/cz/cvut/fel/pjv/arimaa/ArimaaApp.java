package cz.cvut.fel.pjv.arimaa;

import cz.cvut.fel.pjv.arimaa.ui.JavafxApp;
import javafx.application.Application;

/**
 * Entry point for the desktop application (JavaFX).
 */
public final class ArimaaApp {

    private ArimaaApp() {
    }

    /**
     * Launches the JavaFX runtime. Game logic is not implemented in CP2.
     *
     * @param args command-line arguments (unused in skeleton)
     */
    public static void main(String[] args) {
        Application.launch(JavafxApp.class, args);
    }
}
