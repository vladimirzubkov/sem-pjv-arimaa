package cz.cvut.fel.pjv.arimaa.model;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Caretaker for {@link GameMemento}: linear timeline with undo/redo cursor (Memento pattern).
 */
public final class GameTimeline {

    private static final Logger log = LoggerFactory.getLogger(GameTimeline.class);

    private final List<GameMemento> states = new ArrayList<>();
    private int pos = -1;

    /**
     * Clears history and stores one snapshot (call after {@link Game#startNewGame()} or initial attach).
     */
    public void reset(Game game) {
        states.clear();
        states.add(GameMemento.fromGame(game));
        pos = 0;
        log.debug("timeline reset: single snapshot pos=0");
    }

    /**
     * Appends a snapshot of the game <em>after</em> a successful mutation; truncates any redo branch.
     */
    public void recordAfterMutation(Game game) {
        while (states.size() > pos + 1) {
            states.removeLast();
        }
        states.add(GameMemento.fromGame(game));
        pos = states.size() - 1;
        log.debug("timeline record: pos={} size={}", pos, states.size());
    }

    public boolean canUndo() {
        return pos > 0;
    }

    public boolean canRedo() {
        return pos < states.size() - 1;
    }

    public boolean undo(Game game) {
        if (!canUndo()) {
            return false;
        }
        pos--;
        game.restoreMemento(states.get(pos));
        log.debug("timeline undo: pos={}", pos);
        return true;
    }

    public boolean redo(Game game) {
        if (!canRedo()) {
            return false;
        }
        pos++;
        game.restoreMemento(states.get(pos));
        log.debug("timeline redo: pos={}", pos);
        return true;
    }
}
