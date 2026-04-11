package cz.cvut.fel.pjv.arimaa.controller;

import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.Move;

/**
 * Thin MVC layer between JavaFX views and the domain model.
 */
public class GameController {

    private Game game;

    public Game getGame() {
        return game;
    }

    public void setGame(Game game) {
        this.game = game;
    }

    /**
     * No-op in CP2.
     */
    public void submitHumanMove(Move move) {
    }
}
