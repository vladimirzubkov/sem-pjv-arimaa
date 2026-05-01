package cz.cvut.fel.pjv.arimaa.controller;

import cz.cvut.fel.pjv.arimaa.exception.GamePhaseException;
import cz.cvut.fel.pjv.arimaa.exception.IllegalMoveException;
import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.GameTimeline;
import cz.cvut.fel.pjv.arimaa.model.Move;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thin MVC layer between JavaFX views and the domain model.
 */
public class GameController {

    private static final Logger log = LoggerFactory.getLogger(GameController.class);

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
     * Applies a full play-phase turn via {@link Game#applyMove(Move)}.
     *
     * @return {@code true} if the move was legal and applied
     */
    public boolean submitHumanMove(Move move) {
        if (game == null || move == null) {
            return false;
        }
        try {
            game.applyMove(move);
            log.info("submitHumanMove: applied {} steps", move.getSteps().size());
            return true;
        } catch (IllegalMoveException | GamePhaseException ex) {
            log.debug("submitHumanMove failed ({}): {}", ex.getClass().getSimpleName(), ex.getMessage());
            return false;
        }
    }
}
