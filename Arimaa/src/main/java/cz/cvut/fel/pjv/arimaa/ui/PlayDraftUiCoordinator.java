package cz.cvut.fel.pjv.arimaa.ui;

import cz.cvut.fel.pjv.arimaa.model.DefaultRuleEngine;
import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.GameHistoryEvent;
import cz.cvut.fel.pjv.arimaa.model.Move;
import cz.cvut.fel.pjv.arimaa.model.PlayHalfTurn;
import cz.cvut.fel.pjv.arimaa.model.Position;
import cz.cvut.fel.pjv.arimaa.model.Step;
import cz.cvut.fel.pjv.arimaa.model.PlayTurnHistory;
import cz.cvut.fel.pjv.arimaa.model.enums.GameState;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Draft cancel snapshot, step redo stack, and redo/restore helpers for PLAY mode (menu + keyboard undo branch).
 */
final class PlayDraftUiCoordinator {

    private record CancelledDraftSnapshot(
            Move move,
            Position playNextFromOrNull,
            Position playActiveSegmentOriginOrNull) {}

    private final MainController main;

    /** Steps removed by Zpět at the end of the open draft only; Vpřed reapplies them. */
    final Deque<Step> draftRedoSteps = new ArrayDeque<>();

    /**
     * Snapshot after Esc / „Zrušit rozpracovaný tah“; Vpřed restores that draft once. Unlike Zpět, this is not
     * step-by-step.
     */
    private CancelledDraftSnapshot cancelledDraftOrNull;

    PlayDraftUiCoordinator(MainController main) {
        this.main = main;
    }

    boolean hasCancelledDraftSnapshot() {
        return cancelledDraftOrNull != null;
    }

    void discardRedoBranch() {
        draftRedoSteps.clear();
        cancelledDraftOrNull = null;
    }

    void clearForNewTurnUi() {
        draftRedoSteps.clear();
        cancelledDraftOrNull = null;
    }

    boolean tryCancelPlayDraftFromUi() {
        Game g = main.game();
        if (g == null || g.getState() != GameState.PLAY || main.gameController == null) {
            return false;
        }
        if (main.playDraft.partial.getSteps().isEmpty() && main.playDraft.nextFrom == null) {
            return false;
        }
        boolean trapBlocksCancel = main.forbidCancelAfterTrapItem != null
                && main.forbidCancelAfterTrapItem.isSelected()
                && main.gameController.getPlayHistory().viewPrefixRemovesPieceViaTrap(g);
        if (trapBlocksCancel) {
            main.setStatus("Nelze zrušit rozpracovaný tah — v rozpracovaném tahu padla figura do pasti (Gameplay).");
            return false;
        }
        discardRedoBranch();
        cancelledDraftOrNull = new CancelledDraftSnapshot(
                main.gameController.getPlayHistory().pendingDraftAsMoveCopy(),
                main.playDraft.nextFrom,
                main.playDraft.activeSegmentOrigin);
        main.gameController.getPlayHistory().clearTrailingDraftSteps();
        main.gameController.applyPlayHistoryViewToGame();
        main.playDraft.partial.getSteps().clear();
        main.playDraft.nextFrom = null;
        main.playDraft.activeSegmentOrigin = null;
        main.playDraft.keyboardPullFocus = null;
        main.playDraft.keyboardPushFocus = null;
        main.appendHistory(new GameHistoryEvent.DraftCleared());
        main.setStatus("Rozpracovaný tah zrušen (Vpřed obnoví).");
        main.refreshAll();
        return true;
    }

    /**
     * Vpřed after Zpět removed steps at the draft tail: re-append one leg, or two if the redo stack supplies a
     * push/pull bundle.
     */
    boolean tryRedoDraftFromRedoStack() {
        if (draftRedoSteps.isEmpty() || main.gameController == null) {
            return false;
        }
        PlayTurnHistory ph = main.gameController.getPlayHistory();
        if (!ph.isBootstrapped() || !ph.isViewOnTrailingDraftHalf()) {
            return false;
        }
        List<PlayHalfTurn> halves = ph.halfTurnsUnmodifiable();
        PlayHalfTurn tail = halves.get(halves.size() - 1);
        if (tail.committed()) {
            return false;
        }
        Game probe = PlayDraftNotationSupport.probeGameFromMemento(tail.startSnap());
        Step s = draftRedoSteps.pop();
        Move trial1 = new Move();
        for (Step st : tail.steps()) {
            trial1.getSteps().add(PlayDraftNotationSupport.copyStep(st));
        }
        trial1.getSteps().add(PlayDraftNotationSupport.copyStep(s));
        if (DefaultRuleEngine.isValidPlayPrefix(probe, trial1)) {
            ph.replaceTrailingDraftStepsFromMove(trial1);
            main.gameController.applyPlayHistoryViewToGame();
            main.syncPlayPartialFromHistory();
            main.appendHistory(new GameHistoryEvent.DraftStepRedone(main.playDraft.partial.getSteps().size()));
            return true;
        }
        if (!draftRedoSteps.isEmpty()) {
            Step s2 = draftRedoSteps.pop();
            Move trial2 = PlayDraftNotationSupport.copyMove(trial1);
            trial2.getSteps().add(PlayDraftNotationSupport.copyStep(s2));
            if (DefaultRuleEngine.isValidPlayPrefix(probe, trial2)) {
                ph.replaceTrailingDraftStepsFromMove(trial2);
                main.gameController.applyPlayHistoryViewToGame();
                main.syncPlayPartialFromHistory();
                main.appendHistory(new GameHistoryEvent.DraftStepRedone(main.playDraft.partial.getSteps().size()));
                return true;
            }
            draftRedoSteps.push(s2);
        }
        draftRedoSteps.push(s);
        return false;
    }

    boolean redoCancelledDraft() {
        Game g = main.game();
        if (g == null || cancelledDraftOrNull == null || main.gameController == null) {
            return false;
        }
        Move m = PlayDraftNotationSupport.copyMove(cancelledDraftOrNull.move());
        PlayTurnHistory ph = main.gameController.getPlayHistory();
        if (!ph.isBootstrapped()) {
            return false;
        }
        List<PlayHalfTurn> halves = ph.halfTurnsUnmodifiable();
        PlayHalfTurn tail = halves.get(halves.size() - 1);
        if (tail.committed()) {
            return false;
        }
        Game probe = PlayDraftNotationSupport.probeGameFromMemento(tail.startSnap());
        if (!DefaultRuleEngine.isValidPlayPrefix(probe, m)) {
            return false;
        }
        main.playDraft.partial.getSteps().clear();
        for (Step st : m.getSteps()) {
            main.playDraft.partial.getSteps().add(PlayDraftNotationSupport.copyStep(st));
        }
        main.playDraft.nextFrom = cancelledDraftOrNull.playNextFromOrNull();
        main.playDraft.activeSegmentOrigin = cancelledDraftOrNull.playActiveSegmentOriginOrNull();
        cancelledDraftOrNull = null;
        draftRedoSteps.clear();
        main.gameController.getPlayHistory().replaceTrailingDraftStepsFromMove(main.playDraft.partial);
        main.gameController.applyPlayHistoryViewToGame();
        main.appendHistory(new GameHistoryEvent.DraftRestoredAfterClear());
        main.syncPlayPartialFromHistory();
        return true;
    }
}
