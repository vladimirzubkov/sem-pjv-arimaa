package cz.cvut.fel.pjv.arimaa.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Caretaker for {@link GameMemento}: linear timeline with undo/redo cursor (Memento pattern).
 */
public final class GameTimeline {

    private final List<GameMemento> states = new ArrayList<>();
    private int pos = -1;

    /**
     * Clears history and stores one snapshot (call after {@link Game#startNewGame()} or initial attach).
     */
    public void reset(Game game) {
        states.clear();
        states.add(GameMemento.fromGame(game));
        pos = 0;
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
        return true;
    }

    public boolean redo(Game game) {
        if (!canRedo()) {
            return false;
        }
        pos++;
        game.restoreMemento(states.get(pos));
        return true;
    }
}
