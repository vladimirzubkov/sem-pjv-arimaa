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
     * Launches the JavaFX runtime (rozestavení v UI; tahy ve fázi PLAY v modelu zatím ne).
     *
     * @param args command-line arguments (unused)
     */
    public static void main(String[] args) {
        Application.launch(JavafxApp.class, args);
    }
}
