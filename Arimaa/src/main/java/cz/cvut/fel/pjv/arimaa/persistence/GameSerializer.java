package cz.cvut.fel.pjv.arimaa.persistence;

import cz.cvut.fel.pjv.arimaa.controller.GameController;
import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.GameMemento;
import cz.cvut.fel.pjv.arimaa.model.Move;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Line-oriented save format: {@link GameMementoTextCodec} snapshot (non-empty {@code R#} board lines only),
 * then one move notation line per row (each starts with
 * {@code Ng}/{@code Ns}). Blank lines are ignored; legacy {@code ---} lines are skipped when loading.
 * <p>
 * Loading replays {@code moveLines} through {@link Game#applyMove(Move)} / {@link cz.cvut.fel.pjv.arimaa.model.PlayTurnHistory}
 * so the restore matches interactive play.
 */
public final class GameSerializer {

    private static final Pattern PLAY_PREFIX_HEAD = Pattern.compile("^\\s*(\\d+)([gs])\\b");

    /**
     * Frozen snapshot before the first committed PLAY line plus visible notation (including optional trailing draft).
     */
    public record ParsedTxtGame(GameMemento playStartSnapshot, List<String> moveLines) {
    }

    /**
     * Encodes {@link GameController}'s current branch: layout snapshot (initial PLAY posture or entire SETUP state),
     * then every notation line shown in the UI (plus optional {@code draftNotationLineOrNull} as the last line).
     */
    public String serialize(GameController controller, String draftNotationLineOrNull) {
        GameMemento setup = computePlayBaseSnapshot(controller);
        List<String> lines = new ArrayList<>(GameMementoTextCodec.encode(setup));
        lines.addAll(controller.notationLinesVisible());
        if (draftNotationLineOrNull != null && !draftNotationLineOrNull.isBlank()) {
            lines.add(draftNotationLineOrNull.trim());
        }
        return String.join(System.lineSeparator(), lines) + System.lineSeparator();
    }

    /** Convenience overload without a trailing in-memory draft line ({@link #serialize(GameController, String)}). */
    public String serialize(GameController controller) {
        return serialize(controller, null);
    }

    /**
     * Normalizes line separators to {@code \\n} for wire transport (NDJSON / cross-platform peers).
     */
    public String serializeForNetwork(GameController controller, String draftNotationLineOrNull) {
        return serialize(controller, draftNotationLineOrNull)
                .replace("\r\n", "\n")
                .replace("\r", "\n");
    }

    /**
     * Parses text produced by {@link #serialize(GameController, String)}.
     */
    public ParsedTxtGame parse(String text) {
        ArrayList<String> cleaned = new ArrayList<>();
        text.lines().forEach(line -> {
            String t = line.trim();
            if (t.isEmpty() || "---".equals(t)) {
                return;
            }
            cleaned.add(t);
        });

        int firstMove = -1;
        for (int i = 0; i < cleaned.size(); i++) {
            if (PLAY_PREFIX_HEAD.matcher(cleaned.get(i)).find()) {
                firstMove = i;
                break;
            }
        }

        List<String> setupTrimmed =
                firstMove < 0 ? cleaned : new ArrayList<>(cleaned.subList(0, firstMove));
        List<String> moves =
                firstMove < 0 ? List.of() : new ArrayList<>(cleaned.subList(firstMove, cleaned.size()));

        GameMemento mem = GameMementoTextCodec.decode(setupTrimmed);
        return new ParsedTxtGame(mem, List.copyOf(moves));
    }

    /**
     * State immediately before the first committed PLAY notation line; otherwise the current SETUP timeline tip.
     */
    static GameMemento computePlayBaseSnapshot(GameController controller) {
        if (controller.getPlayHistory().isBootstrapped()) {
            return controller.getPlayHistory().anchorStartSnap();
        }
        var states = controller.getTimeline().statesSnapshot();
        int pos = controller.getTimeline().timelinePosition();
        return states.get(pos);
    }

    /** Result of replaying stored notation on top of {@link ParsedTxtGame#playStartSnapshot()}. */
    public record LoadOutcome(Move pendingPartialTurn) {}

    /**
     * Restores {@link GameController#getGame()} by replaying {@code moveLines}; returns draft steps when the file
     * ended mid-turn.
     */
    public LoadOutcome loadIntoController(GameController controller, ParsedTxtGame parsed) {
        Game game = controller.getGame();
        game.startNewGame();
        game.restoreMemento(parsed.playStartSnapshot());
        controller.resetTimeline();
        controller.getPlayHistory().rebuildFromLoadedGame(game, parsed.playStartSnapshot(), parsed.moveLines());
        Move pending = controller.getPlayHistory().pendingDraftAsMoveCopy();
        controller.applyPlayHistoryViewToGame();
        return new LoadOutcome(pending);
    }
}
