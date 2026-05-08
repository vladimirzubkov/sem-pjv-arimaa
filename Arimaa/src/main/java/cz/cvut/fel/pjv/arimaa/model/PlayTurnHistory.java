package cz.cvut.fel.pjv.arimaa.model;

import cz.cvut.fel.pjv.arimaa.model.enums.GameState;
import cz.cvut.fel.pjv.arimaa.persistence.PlayNotationParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PLAY-phase half-turn history: anchor snapshot at index 0, then each committed line and a trailing draft entry.
 * Supports scrubbing within a half-turn via {@link #appliedPrefixSteps()} and {@link #applyViewToGame(Game)}.
 */
public final class PlayTurnHistory {

    private static final Pattern PREFIX_HEAD = Pattern.compile("^\\s*(\\d+)([gs])\\b");

    private final ArrayList<PlayHalfTurn> halfTurns = new ArrayList<>();
    private int viewHalfIndex;
    private int appliedPrefixSteps;

    public PlayTurnHistory() {}

    public void clear() {
        halfTurns.clear();
        viewHalfIndex = 0;
        appliedPrefixSteps = 0;
    }

    /** {@code true} after {@link #bootstrapAtPlayStart(Game)} or {@link #rebuildFromLoadedGame}. */
    public boolean isBootstrapped() {
        return !halfTurns.isEmpty();
    }

    /** Anchor + empty draft; call when entering PLAY from setup or after {@link Game#startNewGame()} + setup done. */
    public void bootstrapAtPlayStart(Game game) {
        clear();
        GameMemento atPlay = GameMemento.fromGame(game);
        PlayHalfTurn anchor = new PlayHalfTurn(atPlay, true);
        anchor.setEndSnap(atPlay);
        anchor.setNotationLineOrNull(null);
        halfTurns.add(anchor);
        halfTurns.add(new PlayHalfTurn(atPlay, false));
        viewHalfIndex = 1;
        appliedPrefixSteps = 0;
    }

    public GameMemento anchorStartSnap() {
        return halfTurns.getFirst().startSnap();
    }

    public List<PlayHalfTurn> halfTurnsUnmodifiable() {
        return Collections.unmodifiableList(halfTurns);
    }

    public PlayHalfTurn halfAt(int index) {
        return halfTurns.get(index);
    }

    /** Number of steps stored on the trailing uncommitted half-turn, or {@code 0} if the last half is committed. */
    public int trailingUncommittedStepCount() {
        if (halfTurns.isEmpty()) {
            return 0;
        }
        PlayHalfTurn last = halfTurns.getLast();
        if (last.committed()) {
            return 0;
        }
        return last.steps().size();
    }

    public int viewHalfIndex() {
        return viewHalfIndex;
    }

    public int appliedPrefixSteps() {
        return appliedPrefixSteps;
    }

    /** Half indices (into {@link #halfTurnsUnmodifiable()}) that correspond to visible history lines, in order. */
    public List<Integer> visibleHalfIndicesForDisplay(boolean includeNonEmptyDraft) {
        ArrayList<Integer> out = new ArrayList<>();
        for (int h = 1; h < halfTurns.size(); h++) {
            PlayHalfTurn ht = halfTurns.get(h);
            if (ht.committed()) {
                String n = ht.notationLineOrNull();
                if (n != null && !n.isBlank()) {
                    out.add(h);
                }
            } else if (includeNonEmptyDraft && !ht.steps().isEmpty()) {
                out.add(h);
            } else if (includeNonEmptyDraft
                    && ht.notationLineOrNull() != null
                    && !ht.notationLineOrNull().isBlank()) {
                out.add(h);
            }
        }
        if (includeNonEmptyDraft && !halfTurns.isEmpty()) {
            int tailIdx = halfTurns.size() - 1;
            PlayHalfTurn tail = halfTurns.get(tailIdx);
            if (!tail.committed() && !out.contains(tailIdx)) {
                out.add(tailIdx);
            }
        }
        return out;
    }

    public void navigateToVisibleLine(int visibleIndex, boolean includeNonEmptyDraft) {
        List<Integer> vis = visibleHalfIndicesForDisplay(includeNonEmptyDraft);
        if (visibleIndex < 0 || visibleIndex >= vis.size()) {
            return;
        }
        int halfIndex = vis.get(visibleIndex);
        PlayHalfTurn ht = halfTurns.get(halfIndex);
        if (ht.committed()) {
            if (halfIndex + 1 < halfTurns.size()) {
                viewHalfIndex = halfIndex + 1;
                appliedPrefixSteps = 0;
            } else {
                navigateToEndOfHalf(halfIndex);
            }
        } else {
            navigateToEndOfHalf(halfIndex);
        }
    }

    public void navigateToEndOfHalf(int halfIndex) {
        if (halfIndex < 1 || halfIndex >= halfTurns.size()) {
            return;
        }
        viewHalfIndex = halfIndex;
        appliedPrefixSteps = halfTurns.get(halfIndex).steps().size();
    }

    public void setViewPrefix(int halfIndex, int prefixSteps) {
        if (halfIndex < 1 || halfIndex >= halfTurns.size()) {
            return;
        }
        PlayHalfTurn ht = halfTurns.get(halfIndex);
        int max = ht.steps().size();
        viewHalfIndex = halfIndex;
        appliedPrefixSteps = Math.max(0, Math.min(prefixSteps, max));
    }

    /** Restores {@code game} to the start snapshot of the trailing (uncommitted) half-turn (no prefix applied). */
    public void restoreTrailingDraftTurnStart(Game game) {
        if (halfTurns.isEmpty()) {
            return;
        }
        PlayHalfTurn last = halfTurns.getLast();
        if (last.committed()) {
            return;
        }
        game.restoreMemento(last.startSnap());
    }

    /** Restores {@code game} to {@link #viewHalfIndex()} / {@link #appliedPrefixSteps()}. */
    public void applyViewToGame(Game game) {
        if (halfTurns.isEmpty()) {
            return;
        }
        PlayHalfTurn ht = halfTurns.get(viewHalfIndex);
        game.restoreMemento(ht.startSnap());
        if (ht.committed()
                && appliedPrefixSteps >= ht.steps().size()
                && ht.endSnap() != null) {
            game.restoreMemento(ht.endSnap());
            return;
        }
        if (appliedPrefixSteps <= 0) {
            return;
        }
        Move m = new Move();
        for (int i = 0; i < appliedPrefixSteps; i++) {
            m.getSteps().add(PlayHalfTurn.copyStep(ht.steps().get(i)));
        }
        game.applyPlayPrefix(m);
    }

    public boolean isAtEditableDraftTail() {
        if (halfTurns.size() < 2) {
            return false;
        }
        PlayHalfTurn last = halfTurns.getLast();
        if (last.committed()) {
            return false;
        }
        int lastIdx = halfTurns.size() - 1;
        return viewHalfIndex == lastIdx && appliedPrefixSteps == last.steps().size();
    }

    public void syncViewToDraftTail() {
        if (halfTurns.isEmpty()) {
            return;
        }
        viewHalfIndex = halfTurns.size() - 1;
        appliedPrefixSteps = halfTurns.getLast().steps().size();
    }

    /** Copies steps from {@code draft} into the trailing draft half-turn and moves the view to its end. */
    public void replaceTrailingDraftStepsFromMove(Move draft) {
        PlayHalfTurn tail = halfTurns.getLast();
        if (tail.committed()) {
            return;
        }
        tail.clearSteps();
        tail.setNotationLineOrNull(null);
        for (Step s : draft.getSteps()) {
            tail.steps().add(PlayHalfTurn.copyStep(s));
        }
        syncViewToDraftTail();
    }

    /** Removes draft steps from {@code fromStepIndexInclusive} onward (indices 0-based); view jumps to draft tail. */
    public void truncateTrailingDraftFrom(int fromStepIndexInclusive) {
        PlayHalfTurn last = halfTurns.getLast();
        if (last.committed()) {
            return;
        }
        while (last.steps().size() > fromStepIndexInclusive) {
            last.removeLastStep();
        }
        last.setNotationLineOrNull(null);
        syncViewToDraftTail();
    }

    public void appendStepCopyToTrailingDraft(Step s) {
        PlayHalfTurn last = halfTurns.getLast();
        if (last.committed()) {
            throw new IllegalStateException("no trailing draft");
        }
        last.steps().add(PlayHalfTurn.copyStep(s));
        syncViewToDraftTail();
    }

    /**
     * Removes the last step from the trailing draft (live edit), moves the view to the new end, and returns a copy
     * of the removed step (or {@code null} if there was nothing to pop).
     */
    public Step popLastStepCopyFromTrailingDraft() {
        PlayHalfTurn last = halfTurns.getLast();
        if (last.committed() || last.steps().isEmpty()) {
            return null;
        }
        int sz = last.steps().size();
        Step removed = PlayHalfTurn.copyStep(last.steps().get(sz - 1));
        last.removeLastStep();
        last.setNotationLineOrNull(null);
        viewHalfIndex = halfTurns.size() - 1;
        appliedPrefixSteps = last.steps().size();
        return removed;
    }

    /** Copy of trailing uncommitted steps for save-load / UI bootstrap (empty if last half is committed). */
    public Move pendingDraftAsMoveCopy() {
        PlayHalfTurn last = halfTurns.getLast();
        Move m = new Move();
        if (last.committed()) {
            return m;
        }
        for (Step st : last.steps()) {
            m.getSteps().add(PlayHalfTurn.copyStep(st));
        }
        return m;
    }

    public void finalizeCommittedDraft(Game gameAfterFullMove, String notationLine, Move submittedMove) {
        PlayHalfTurn tail = halfTurns.getLast();
        if (tail.committed()) {
            throw new IllegalStateException("no open draft");
        }
        tail.clearSteps();
        for (Step s : submittedMove.getSteps()) {
            tail.steps().add(PlayHalfTurn.copyStep(s));
        }
        tail.setCommitted(true);
        tail.setNotationLineOrNull(notationLine);
        tail.setEndSnap(GameMemento.fromGame(gameAfterFullMove));
        if (gameAfterFullMove.getState() == GameState.PLAY) {
            halfTurns.add(new PlayHalfTurn(GameMemento.fromGame(gameAfterFullMove), false));
        }
        viewHalfIndex = halfTurns.size() - 1;
        appliedPrefixSteps = halfTurns.getLast().steps().size();
    }

    public void copyViewPrefixStepsTo(Move target) {
        target.getSteps().clear();
        if (halfTurns.isEmpty()) {
            return;
        }
        PlayHalfTurn ht = halfTurns.get(viewHalfIndex);
        for (int i = 0; i < appliedPrefixSteps; i++) {
            target.getSteps().add(PlayHalfTurn.copyStep(ht.steps().get(i)));
        }
    }

    public boolean isViewOnTrailingDraftHalf() {
        if (halfTurns.isEmpty()) {
            return false;
        }
        PlayHalfTurn last = halfTurns.getLast();
        return !last.committed() && viewHalfIndex == halfTurns.size() - 1;
    }

    public void clearTrailingDraftSteps() {
        PlayHalfTurn last = halfTurns.getLast();
        if (last.committed()) {
            return;
        }
        last.clearSteps();
        last.setNotationLineOrNull(null);
        syncViewToDraftTail();
    }

    public boolean canStepViewBack() {
        return appliedPrefixSteps > 0;
    }

    public boolean canStepViewForwardWithinHalf() {
        PlayHalfTurn ht = halfTurns.get(viewHalfIndex);
        return appliedPrefixSteps < ht.steps().size();
    }

    public void stepViewBack() {
        if (canStepViewBack()) {
            appliedPrefixSteps--;
        }
    }

    public void stepViewForwardWithinHalf() {
        if (canStepViewForwardWithinHalf()) {
            appliedPrefixSteps++;
        }
    }

    /**
     * Whether the scrubbed prefix on the viewed half-turn removes at least one piece via trap (any colour), evaluated
     * from that half-turn’s start snapshot.
     */
    public boolean viewPrefixRemovesPieceViaTrap(Game game) {
        if (game == null || game.getState() != GameState.PLAY || !isBootstrapped() || appliedPrefixSteps <= 0) {
            return false;
        }
        PlayHalfTurn ht = halfAt(viewHalfIndex);
        Game probe = Game.restoredFromMemento(ht.startSnap());
        Move m = new Move();
        for (int i = 0; i < appliedPrefixSteps; i++) {
            m.getSteps().add(PlayHalfTurn.copyStep(ht.steps().get(i)));
        }
        DefaultRuleEngine.TrapCapturePreview p = DefaultRuleEngine.trapCapturesIfPrefixApplied(probe, m);
        return !p.byGold().isEmpty() || !p.bySilver().isEmpty();
    }

    public List<String> committedNotationLinesInOrder() {
        ArrayList<String> out = new ArrayList<>();
        for (int h = 1; h < halfTurns.size(); h++) {
            PlayHalfTurn ht = halfTurns.get(h);
            if (ht.committed()) {
                String n = ht.notationLineOrNull();
                if (n != null && !n.isBlank()) {
                    out.add(n);
                }
            }
        }
        return out;
    }

    public String nextPlayNotationPrefix() {
        boolean nextGold = true;
        int goldNum = 1;
        int silverNum = 1;
        for (int h = 1; h < halfTurns.size(); h++) {
            PlayHalfTurn ht = halfTurns.get(h);
            if (!ht.committed()) {
                continue;
            }
            String line = ht.notationLineOrNull();
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

    /**
     * Rebuilds history from a loaded file: replay full lines on {@code game}, append a draft half-turn when the file
     * ended mid-move.
     */
    public void rebuildFromLoadedGame(Game game, GameMemento playStart, List<String> moveLines) {
        clear();
        PlayHalfTurn anchor = new PlayHalfTurn(playStart, true);
        anchor.setEndSnap(playStart);
        halfTurns.add(anchor);
        game.restoreMemento(playStart);
        for (int i = 0; i < moveLines.size(); i++) {
            String raw = moveLines.get(i);
            boolean last = i == moveLines.size() - 1;
            PlayNotationParser.ParsedLine pl = PlayNotationParser.parseLine(game, raw);
            Move mv = pl.move();
            boolean earlyPass = pl.hasEarlyPassSuffix();
            boolean isDraft = last && !earlyPass && mv.getSteps().size() < 4;
            if (!last && !earlyPass && mv.getSteps().size() < 4) {
                throw new IllegalArgumentException(
                        "line " + i + " has fewer than 4 steps without '... pass' but is not the last line: " + raw);
            }
            if (!isDraft) {
                GameMemento before = GameMemento.fromGame(game);
                game.applyMove(mv);
                PlayHalfTurn ht = new PlayHalfTurn(before, true);
                for (Step s : mv.getSteps()) {
                    ht.steps().add(PlayHalfTurn.copyStep(s));
                }
                ht.setNotationLineOrNull(raw);
                ht.setEndSnap(GameMemento.fromGame(game));
                halfTurns.add(ht);
            } else {
                GameMemento before = GameMemento.fromGame(game);
                PlayHalfTurn d = new PlayHalfTurn(before, false);
                for (Step s : mv.getSteps()) {
                    d.steps().add(PlayHalfTurn.copyStep(s));
                }
                d.setNotationLineOrNull(raw);
                halfTurns.add(d);
            }
        }
        if (halfTurns.getLast().committed()) {
            halfTurns.add(new PlayHalfTurn(GameMemento.fromGame(game), false));
        }
        viewHalfIndex = halfTurns.size() - 1;
        appliedPrefixSteps = halfTurns.getLast().steps().size();
    }
}
