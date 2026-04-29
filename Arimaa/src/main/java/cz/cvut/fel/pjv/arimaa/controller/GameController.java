package cz.cvut.fel.pjv.arimaa.controller;

import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.GameTimeline;
import cz.cvut.fel.pjv.arimaa.model.Move;

/**
 * Thin MVC layer between JavaFX views and the domain model.
 */
public class GameController {

    private Game game;
    private final GameTimeline timeline = new GameTimeline();

    public Game getGame() {
        return game;
    }

    public void setGame(Game game) {
        this.game = game;
    }

    /**
     * Replaces the timeline with a single snapshot of the current game (e.g. after {@link Game#startNewGame()}).
     */
    public void resetTimeline() {
        if (game != null) {
            timeline.reset(game);
        }
    }

    /**
     * Records the current game state after a successful mutation (truncates redo branch).
     */
    public void recordAfterMutation() {
        if (game != null) {
            timeline.recordAfterMutation(game);
        }
    }

    public boolean canUndo() {
        return game != null && timeline.canUndo();
    }

    public boolean canRedo() {
        return game != null && timeline.canRedo();
    }

    public boolean undo() {
        return game != null && timeline.undo(game);
    }

    public boolean redo() {
        return game != null && timeline.redo(game);
    }

    /**
     * No-op until full move application exists.
     */
    public void submitHumanMove(Move move) {
    }
}
