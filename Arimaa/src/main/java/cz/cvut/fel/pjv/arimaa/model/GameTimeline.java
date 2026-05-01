package cz.cvut.fel.pjv.arimaa.model;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Caretaker for {@link GameMemento}: linear timeline with undo/redo cursor (Memento pattern).
 * Parallel {@link #arrivalNotation} holds optional Arimaa notation line for PLAY moves (aligned by index with states).
 */
public final class GameTimeline {

    private static final Logger log = LoggerFactory.getLogger(GameTimeline.class);

    private static final Pattern PREFIX_HEAD = Pattern.compile("^\\s*(\\d+)([gs])\\b");

    private final List<GameMemento> states = new ArrayList<>();
    /** Same length as {@link #states}; {@code null} at index 0 and for non-PLAY mutations. */
    private final List<String> arrivalNotation = new ArrayList<>();
    private int pos = -1;

    /**
     * Clears history and stores one snapshot (call after {@link Game#startNewGame()} or initial attach).
     */
    public void reset(Game game) {
        states.clear();
        arrivalNotation.clear();
        states.add(GameMemento.fromGame(game));
        arrivalNotation.add(null);
        pos = 0;
        log.debug("timeline reset: single snapshot pos=0");
    }

    /**
     * Appends a snapshot after a successful mutation; truncates any redo branch.
     *
     * @param notationLineOrNull full Arimaa line for a PLAY turn (e.g. {@code "1g Ea2n ..."}), or {@code null}
     */
    public void recordAfterMutation(Game game, String notationLineOrNull) {
        while (states.size() > pos + 1) {
            states.removeLast();
            arrivalNotation.removeLast();
        }
        states.add(GameMemento.fromGame(game));
        arrivalNotation.add(notationLineOrNull);
        pos = states.size() - 1;
        log.debug("timeline record: pos={} size={}", pos, states.size());
    }

    /**
     * Appends a snapshot without PLAY notation (setup and other phases).
     */
    public void recordAfterMutation(Game game) {
        recordAfterMutation(game, null);
    }

    /**
     * Non-null notation lines for indices {@code 1..pos} (visible branch).
     */
    public List<String> notationLinesVisible() {
        List<String> out = new ArrayList<>();
        int last = Math.min(pos, arrivalNotation.size() - 1);
        for (int i = 1; i <= last; i++) {
            String line = arrivalNotation.get(i);
            if (line != null && !line.isBlank()) {
                out.add(line);
            }
        }
        return out;
    }

    /**
     * Prefix for the next PLAY line ({@code Ng} / {@code Ns}) from notation already on the timeline up to {@link #pos}.
     */
    public String nextPlayNotationPrefix() {
        boolean nextGold = true;
        int goldNum = 1;
        int silverNum = 1;
        int last = Math.min(pos, arrivalNotation.size() - 1);
        for (int i = 1; i <= last; i++) {
            String line = arrivalNotation.get(i);
            if (line == null || line.isBlank()) {
                continue;
            }
            Matcher m = PREFIX_HEAD.matcher(line);
            if (!m.find()) {
                continue;
            }
            int n = Integer.parseInt(m.group(1));
            String gs = m.group(2);
            if ("g".equals(gs)) {
                silverNum = n;
                nextGold = false;
            } else {
                goldNum = n + 1;
                nextGold = true;
            }
        }
        if (nextGold) {
            return goldNum + "g";
        }
        return silverNum + "s";
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
