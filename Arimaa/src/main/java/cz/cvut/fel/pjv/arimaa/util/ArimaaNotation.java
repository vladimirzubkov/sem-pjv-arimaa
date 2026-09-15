package cz.cvut.fel.pjv.arimaa.util;

import cz.cvut.fel.pjv.arimaa.model.Board;
import cz.cvut.fel.pjv.arimaa.model.DefaultRuleEngine;
import cz.cvut.fel.pjv.arimaa.model.Move;

import java.util.Objects;

/**
 * Full-line Arimaa game notation (see <a href="https://arimaa.com/arimaa/learn/notation.html">official spec</a>).
 */
public final class ArimaaNotation {

    /**
     * Marker appended to a persisted (save / network snapshot) uncommitted line that already has four steps.
     * Without it, a 4-step last line is indistinguishable from a committed full turn ({@code ... pass} is only used
     * for shorter turns).
     */
    public static final String UNCOMMITTED_DRAFT_MARKER = "... draft";

    private ArimaaNotation() {
    }

    /**
     * One line: {@code prefix + body}, with {@code ... pass} when fewer than four steps were taken.
     * Does not mutate {@code before} (notation walks a copy).
     *
     * @param prefix move prefix such as {@code "1g"} or {@code "2s"} (no trailing space)
     */
    public static String formatFullTurn(Board before, Move move, String prefix) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(move, "move");
        Objects.requireNonNull(prefix, "prefix");
        String body = DefaultRuleEngine.buildArimaaNotationBody(before, move);
        if (move.getSteps().size() < 4) {
            body = body.isEmpty() ? "... pass" : body + " ... pass";
        }
        return prefix + " " + body;
    }

    /**
     * Live preview during an unfinished turn: same body tokens as {@link #formatFullTurn} but never appends
     * {@code ... pass}. Does not mutate {@code before}.
     */
    public static String formatPartialTurnLine(Board before, Move partialMove, String prefix) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(partialMove, "partialMove");
        Objects.requireNonNull(prefix, "prefix");
        String body = DefaultRuleEngine.buildArimaaNotationBody(before, partialMove);
        return prefix + " " + body;
    }

    /**
     * Persist form of an unfinished turn for save files and network {@code state_snapshot}. Four-step drafts get
     * {@link #UNCOMMITTED_DRAFT_MARKER} so reload keeps them uncommitted (the player still has to press End turn).
     */
    public static String formatPartialTurnLineForPersist(Board before, Move partialMove, String prefix) {
        String line = formatPartialTurnLine(before, partialMove, prefix);
        if (partialMove.getSteps().size() >= 4) {
            return line + " " + UNCOMMITTED_DRAFT_MARKER;
        }
        return line;
    }
}
