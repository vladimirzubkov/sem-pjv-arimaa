package cz.cvut.fel.pjv.arimaa;

import cz.cvut.fel.pjv.arimaa.logging.LoggingSupport;
import cz.cvut.fel.pjv.arimaa.ui.JavafxApp;
import javafx.application.Application;

/**
 * Entry point for the desktop application (JavaFX).
 */
public final class ArimaaApp {

    private ArimaaApp() {
    }

    /**
     * Launches the JavaFX runtime (rozestavení a tahy ve fázi PLAY v UI a modelu).
     *
     * @param args command-line arguments; optional {@code --log-level=LEVEL} and {@code --log-file=PATH}
     *             (see {@link LoggingSupport})
     */
    public static void main(String[] args) {
        LoggingSupport.bootstrapFromArgs(args);
        Application.launch(JavafxApp.class, args);
    }
}
