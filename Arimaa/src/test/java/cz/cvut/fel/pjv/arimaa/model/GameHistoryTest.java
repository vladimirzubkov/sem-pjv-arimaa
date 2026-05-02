package cz.cvut.fel.pjv.arimaa.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GameHistoryTest {

    @Test
    void appendPreservesOrderClearEmptiesAndEventsIsUnmodifiable() {
        GameHistory h = new GameHistory();
        h.append(new GameHistoryEvent.DraftStepAdded(1));
        h.append(new GameHistoryEvent.DraftStepUndone(0));
        assertEquals(2, h.size());

        List<GameHistoryEvent> snap = h.events();
        assertEquals(2, snap.size());
        assertEquals(new GameHistoryEvent.DraftStepAdded(1), snap.get(0));
        assertThrows(UnsupportedOperationException.class, () -> snap.remove(0));

        h.clear();
        assertEquals(0, h.size());
        assertEquals(0, h.events().size());
    }
}
