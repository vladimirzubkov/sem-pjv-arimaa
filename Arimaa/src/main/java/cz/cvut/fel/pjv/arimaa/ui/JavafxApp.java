package cz.cvut.fel.pjv.arimaa.ui;

import cz.cvut.fel.pjv.arimaa.controller.GameController;
import cz.cvut.fel.pjv.arimaa.model.Game;
import javafx.application.Application;
import javafx.stage.Stage;

/**
 * JavaFX {@link javafx.application.Application}: builds {@link Game}, {@link GameController}, attaches {@link MainController}
 * to the primary stage (setup + PLAY UI).
 */
public class JavafxApp extends Application {

    private MainController mainController;

    /** Builds model + controller, wires {@link MainController} to {@code primaryStage} (app entry from {@link cz.cvut.fel.pjv.arimaa.ArimaaApp}). */
    @Override
    public void start(Stage primaryStage) {
        Game game = new Game();
        game.startNewGame();

        GameController gameController = new GameController();
        gameController.setGame(game);

        mainController = new MainController();
        mainController.setGameController(gameController);
        mainController.attachToStage(primaryStage);
    }

    /** Stops background tickers (chess clock) when the JavaFX runtime shuts down. */
    @Override
    public void stop() {
        if (mainController != null) {
            mainController.shutdownPlayChessClock();
        }
    }
}
