package cz.cvut.fel.pjv.arimaa.ui;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/**
 * Development-only: reload {@code arimaa-menus.css} from classpath when the user presses F5.
 *
 * <p>Enable with JVM flag {@code -Darimaa.devMenuCssF5=true}. Uses a cache-busting query on the stylesheet URL.
 */
public final class DevMenuCssHotReload {

    private DevMenuCssHotReload() {}

    /**
     * Registers an F5 {@link KeyEvent#KEY_PRESSED} filter on {@code scene} when the system property
     * {@code arimaa.devMenuCssF5} is {@code true}.
     *
     * @param scene      main window scene
     * @param menuCssUrl classpath external form of {@code arimaa-menus.css} (no query string)
     */
    public static void installIfEnabled(Scene scene, String menuCssUrl) {
        if (!Boolean.getBoolean("arimaa.devMenuCssF5")) {
            return;
        }
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() != KeyCode.F5) {
                return;
            }
            if (e.getTarget() instanceof TextInputControl t && t.isEditable()) {
                return;
            }
            e.consume();
            Platform.runLater(() -> {
                scene.getStylesheets().removeIf(u -> u.equals(menuCssUrl) || u.startsWith(menuCssUrl + "?"));
                scene.getStylesheets().add(menuCssUrl + "?v=" + System.nanoTime());
            });
        });
    }
}
