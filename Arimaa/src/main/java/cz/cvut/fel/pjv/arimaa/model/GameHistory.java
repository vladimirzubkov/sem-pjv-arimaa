package cz.cvut.fel.pjv.arimaa.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ordered, append-only log of {@link GameHistoryEvent} values for the current match (replay / debugging).
 */
public final class GameHistory {

    private final List<GameHistoryEvent> events = new ArrayList<>();

    public void append(GameHistoryEvent event) {
        events.add(event);
    }

    /**
     * Clears all entries (e.g. new game).
     */
    public void clear() {
        events.clear();
    }

    public List<GameHistoryEvent> events() {
        return Collections.unmodifiableList(events);
    }

    public int size() {
        return events.size();
    }
}
