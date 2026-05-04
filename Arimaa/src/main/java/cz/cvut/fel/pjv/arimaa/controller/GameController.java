package cz.cvut.fel.pjv.arimaa.controller;

import cz.cvut.fel.pjv.arimaa.exception.GamePhaseException;
import cz.cvut.fel.pjv.arimaa.exception.IllegalMoveException;
import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.GameHistory;
import cz.cvut.fel.pjv.arimaa.model.GameHistoryEvent;
import cz.cvut.fel.pjv.arimaa.model.GameMemento;
import cz.cvut.fel.pjv.arimaa.model.enums.GameState;
import cz.cvut.fel.pjv.arimaa.model.GameTimeline;
import cz.cvut.fel.pjv.arimaa.model.Move;
import cz.cvut.fel.pjv.arimaa.model.PlayTurnHistory;
import cz.cvut.fel.pjv.arimaa.persistence.GameSerializer;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thin MVC layer: {@link GameTimeline} for SETUP undo/redo only; {@link PlayTurnHistory} for PLAY half-turns,
 * scrubbing, and notation; {@link GameHistory} for the event log.
 */
public class GameController {

    private static final Logger log = LoggerFactory.getLogger(GameController.class);

    private Game game;
    private final GameTimeline timeline = new GameTimeline();
    private final PlayTurnHistory playHistory = new PlayTurnHistory();
    private final GameHistory gameHistory = new GameHistory();

    public Game getGame() {
        return game;
    }

    public void setGame(Game game) {
        this.game = game;
    }

    public GameTimeline getTimeline() {
        return timeline;
    }

    public PlayTurnHistory getPlayHistory() {
        return playHistory;
    }

    /**
     * Clears PLAY history and replaces the SETUP timeline with a single snapshot of the current game (e.g. after
     * {@link Game#startNewGame()}). If the game is already in PLAY or GAME_OVER, bootstraps {@link PlayTurnHistory}
     * from the current board (interactive match or tests that begin in PLAY).
     */
    public void resetTimeline() {
        if (game != null) {
            timeline.reset(game);
            playHistory.clear();
            if (game.getState() == GameState.PLAY || game.getState() == GameState.GAME_OVER) {
                playHistory.bootstrapAtPlayStart(game);
            }
        }
        gameHistory.clear();
    }

    public GameHistory getGameHistory() {
        return gameHistory;
    }

    public void appendHistory(GameHistoryEvent event) {
        gameHistory.append(event);
    }

    public void clearGameHistory() {
        gameHistory.clear();
    }

    /**
     * Call once when Silver’s setup completes and the model enters PLAY (no-op if not PLAY).
     */
    public void enterPlayPhaseBootstrap() {
        if (game != null && game.getState() == GameState.PLAY && !playHistory.isBootstrapped()) {
            playHistory.bootstrapAtPlayStart(game);
        }
    }

    public GameSerializer.LoadOutcome loadFromTxtGame(GameMemento setup, List<String> moveLines) {
        GameSerializer gs = new GameSerializer();
        return gs.loadIntoController(this, new GameSerializer.ParsedTxtGame(setup, moveLines));
    }

    public void recordAfterMutation() {
        recordAfterMutation(null);
    }

    /**
     * Records a SETUP mutation on the timeline, or a no-op during PLAY (committed PLAY lines use
     * {@link #recordCommittedPlayTurn(Move, String)}).
     */
    public void recordAfterMutation(String playNotationLineOrNull) {
        if (game == null) {
            return;
        }
        if (game.getState() == GameState.SETUP_GOLD || game.getState() == GameState.SETUP_SILVER) {
            timeline.recordAfterMutation(game, null);
            return;
        }
        if (playNotationLineOrNull != null && !playNotationLineOrNull.isBlank()) {
            throw new IllegalStateException("use recordCommittedPlayTurn during PLAY");
        }
    }

    /** After a successful {@link Game#applyMove(Move)} in PLAY (or GAME_OVER from the last apply). */
    public void recordCommittedPlayTurn(Move submittedMove, String notationLine) {
        if (game == null) {
            return;
        }
        if (game.getState() != GameState.PLAY && game.getState() != GameState.GAME_OVER) {
            throw new IllegalStateException("recordCommittedPlayTurn only after PLAY apply");
        }
        if (!playHistory.isBootstrapped()) {
            throw new IllegalStateException("PLAY history not bootstrapped");
        }
        playHistory.finalizeCommittedDraft(game, notationLine, submittedMove);
    }

    /** Applies the current history view (cursor + prefix) to {@link #getGame()}. */
    public void applyPlayHistoryViewToGame() {
        if (game != null && playHistory.isBootstrapped()) {
            playHistory.applyViewToGame(game);
        }
    }

    public void restoreTrailingDraftTurnStartForSubmit() {
        if (game != null && playHistory.isBootstrapped()) {
            playHistory.restoreTrailingDraftTurnStart(game);
        }
    }

    public List<String> notationLinesVisible() {
        if (game != null && playHistory.isBootstrapped()) {
            return playHistory.committedNotationLinesInOrder();
        }
        return List.of();
    }

    public String nextPlayNotationPrefix() {
        if (game != null && playHistory.isBootstrapped()) {
            return playHistory.nextPlayNotationPrefix();
        }
        return "1g";
    }

    public boolean canUndo() {
        if (game == null) {
            return false;
        }
        if (game.getState() == GameState.SETUP_GOLD || game.getState() == GameState.SETUP_SILVER) {
            return timeline.canUndo();
        }
        return playHistory.isBootstrapped() && playHistory.canStepViewBack();
    }

    public boolean canRedo() {
        if (game == null) {
            return false;
        }
        if (game.getState() == GameState.SETUP_GOLD || game.getState() == GameState.SETUP_SILVER) {
            return timeline.canRedo();
        }
        return playHistory.isBootstrapped() && playHistory.canStepViewForwardWithinHalf();
    }

    public boolean undo() {
        if (game == null) {
            return false;
        }
        if (game.getState() == GameState.SETUP_GOLD || game.getState() == GameState.SETUP_SILVER) {
            return timeline.undo(game);
        }
        if (!playHistory.isBootstrapped() || !playHistory.canStepViewBack()) {
            return false;
        }
        playHistory.stepViewBack();
        playHistory.applyViewToGame(game);
        return true;
    }

    public boolean redo() {
        if (game == null) {
            return false;
        }
        if (game.getState() == GameState.SETUP_GOLD || game.getState() == GameState.SETUP_SILVER) {
            return timeline.redo(game);
        }
        if (!playHistory.isBootstrapped() || !playHistory.canStepViewForwardWithinHalf()) {
            return false;
        }
        playHistory.stepViewForwardWithinHalf();
        playHistory.applyViewToGame(game);
        return true;
    }

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
