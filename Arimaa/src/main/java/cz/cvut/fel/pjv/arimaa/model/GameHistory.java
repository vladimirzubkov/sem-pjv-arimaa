package cz.cvut.fel.pjv.arimaa.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ordered, append-only log of {@link GameHistoryEvent} values for the current match (draft steps, undo/redo of draft,
 * committed turns — used by the UI history panel / debugging).
 */
public final class GameHistory {

    private final List<GameHistoryEvent> events = new ArrayList<>();

    /** Records one UI/model event at the end of the log. */
    public void append(GameHistoryEvent event) {
        events.add(event);
    }

    /**
     * Clears all entries (e.g. new game).
     */
    public void clear() {
        events.clear();
    }

    /** Immutable snapshot of all events (may be empty). */
    public List<GameHistoryEvent> events() {
        return Collections.unmodifiableList(events);
    }

    /** Number of stored events. */
    public int size() {
        return events.size();
    }
}
