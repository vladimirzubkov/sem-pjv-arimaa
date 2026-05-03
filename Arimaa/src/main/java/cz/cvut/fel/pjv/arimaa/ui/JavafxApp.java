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

    @Override
    public void start(Stage primaryStage) {
        Game game = new Game();
        game.startNewGame();

        GameController gameController = new GameController();
        gameController.setGame(game);

        MainController mainController = new MainController();
        mainController.setGameController(gameController);
        mainController.attachToStage(primaryStage);
    }
}
