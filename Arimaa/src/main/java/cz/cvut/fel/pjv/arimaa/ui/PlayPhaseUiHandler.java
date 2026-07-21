package cz.cvut.fel.pjv.arimaa.ui;

import cz.cvut.fel.pjv.arimaa.model.DefaultRuleEngine;
import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.GameHistoryEvent;
import cz.cvut.fel.pjv.arimaa.model.Move;
import cz.cvut.fel.pjv.arimaa.model.Piece;
import cz.cvut.fel.pjv.arimaa.model.Position;
import cz.cvut.fel.pjv.arimaa.model.Step;
import cz.cvut.fel.pjv.arimaa.model.enums.GameState;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;
import cz.cvut.fel.pjv.arimaa.model.enums.StepKind;
import cz.cvut.fel.pjv.arimaa.util.ArimaaNotation;
import cz.cvut.fel.pjv.arimaa.util.BoardConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * PLAY phase: board activation, end turn, Tab / Space keyboard targets.
 */
final class PlayPhaseUiHandler {

    private static final Logger log = LoggerFactory.getLogger(PlayPhaseUiHandler.class);

    /* One Tab ring stop: either a pull weak square or a concrete push-first option. */
    private record PullPushTabEntry(Position pullPos, PushFirstOption pushOpt) {
        static PullPushTabEntry pull(Position p) {
            return new PullPushTabEntry(Objects.requireNonNull(p), null);
        }

        static PullPushTabEntry push(PushFirstOption o) {
            return new PullPushTabEntry(null, Objects.requireNonNull(o));
        }

        boolean isPull() {
            return pullPos != null;
        }
    }

    private final MainController main;

    PlayPhaseUiHandler(MainController main) {
        this.main = main;
    }

    /* Sort key for on-screen row (bottom-up for mover) then file left-to-right. */
    private int comparePositionVisual(Position a, Position b, Game g) {
        int ra = main.visualRowFromModelRank(a.getRankIndex(), g);
        int rb = main.visualRowFromModelRank(b.getRankIndex(), g);
        int cmp = Integer.compare(ra, rb);
        if (cmp != 0) {
            return cmp;
        }
        return Integer.compare(
                main.visualColFromModelFile(a.getFileIndex(), g), main.visualColFromModelFile(b.getFileIndex(), g));
    }

    /* Tab order along keyboard “snake”: from mover’s home edge when mover is not visually at bottom, else visual order. */
    private int comparePositionKeyboardCycle(Position a, Position b, Game g) {
        if (moverVisualBottom(g)) {
            return comparePositionVisual(a, b, g);
        }
        int ra = main.visualRowFromModelRank(a.getRankIndex(), g);
        int rb = main.visualRowFromModelRank(b.getRankIndex(), g);
        int cmp = Integer.compare(rb, ra);
        if (cmp != 0) {
            return cmp;
        }
        return Integer.compare(
                main.visualColFromModelFile(b.getFileIndex(), g), main.visualColFromModelFile(a.getFileIndex(), g));
    }

    /* True when the side to move’s home ranks appear toward the bottom of the view (orientation helper). */
    private boolean moverVisualBottom(Game g) {
        PlayerSide m = g.getSideToMove();
        if (m == PlayerSide.GOLD) {
            return main.boardOrientation.boardGoldVisualBottom(g);
        }
        return !main.boardOrientation.boardGoldVisualBottom(g);
    }

    /* All own pieces on the board sorted for Ctrl+Tab / Tab cycling (type then home-ward geometry). */
    private List<Position> ownPiecesInPlayTabOrder(Game g) {
        PlayerSide side = g.getSideToMove();
        List<Position> all = new ArrayList<>();
        for (int f = 0; f < BoardConstants.BOARD_SIZE; f++) {
            for (int r = 0; r < BoardConstants.BOARD_SIZE; r++) {
                Position p = Position.of(f, r);
                Piece pc = main.effectivePieceAt(g, p);
                if (pc != null && pc.getSide() == side) {
                    all.add(p);
                }
            }
        }
        if (all.size() <= 1) {
            return all;
        }
        all.sort((a, b) -> compareOwnPiecesTabOrder(a, b, g, side));
        return all;
    }

    /* Tie-break for two own cells: piece strength, rank toward home, then visual column/row order. */
    private int compareOwnPiecesTabOrder(Position a, Position b, Game g, PlayerSide side) {
        Piece pa = main.effectivePieceAt(g, a);
        Piece pb = main.effectivePieceAt(g, b);
        int c = Integer.compare(pa.getType().ordinal(), pb.getType().ordinal());
        if (c != 0) {
            return c;
        }
        int ra = a.getRankIndex();
        int rb = b.getRankIndex();
        if (side == PlayerSide.GOLD) {
            c = Integer.compare(rb, ra);
        } else {
            c = Integer.compare(ra, rb);
        }
        if (c != 0) {
            return c;
        }
        int ca = main.visualColFromModelFile(a.getFileIndex(), g);
        int cb = main.visualColFromModelFile(b.getFileIndex(), g);
        int va = main.visualRowFromModelRank(ra, g);
        int vb = main.visualRowFromModelRank(rb, g);
        if (moverVisualBottom(g)) {
            c = Integer.compare(ca, cb);
            if (c != 0) {
                return c;
            }
            return Integer.compare(va, vb);
        }
        c = Integer.compare(cb, ca);
        if (c != 0) {
            return c;
        }
        return Integer.compare(vb, va);
    }

    void advancePlayTabFocusOwnPiecesOnly(Game g, boolean reverse) {
        main.playDraft.keyboardPullFocus = null;
        main.playDraft.keyboardPushFocus = null;
        main.playDraft.keyboardPushWeakFrom = null;
        selectNextOwnPieceForPlayKeyboard(g, reverse, "Ctrl+Tab");
    }

    /* Ctrl+Tab: cycles nextFrom among own pieces in tab order and refreshes status/hand highlights. */
    private void selectNextOwnPieceForPlayKeyboard(Game g, boolean reverse, String keyboardLabel) {
        List<Position> own = ownPiecesInPlayTabOrder(g);
        if (own.isEmpty()) {
            return;
        }
        int start = main.playDraft.nextFrom == null ? -1 : own.indexOf(main.playDraft.nextFrom);
        int idx;
        if (start < 0) {
            idx = reverse ? own.size() - 1 : 0;
        } else {
            idx = reverse ? (start - 1 + own.size()) % own.size() : (start + 1) % own.size();
        }
        Position next = own.get(idx);
        main.discardDraftRedoBranch();
        main.playDraft.nextFrom = next;
        main.playDraft.activeSegmentOrigin = next;
        setStatusOwnPieceSelected(g, keyboardLabel);
        main.refreshAll();
    }

    /*
     * Pull weak squares first, then push options whose first step is not also a pull cell (pull wins Tab order).
     */
    private List<PullPushTabEntry> buildUnifiedPullPushTabRing(PlayTargetBundle tabTargets, Game g) {
        Set<Position> pulls = tabTargets.pullWeakSquares();
        List<PullPushTabEntry> ring = new ArrayList<>();
        List<Position> pullList = new ArrayList<>(pulls);
        pullList.sort((a, b) -> comparePositionKeyboardCycle(a, b, g));
        for (Position p : pullList) {
            ring.add(PullPushTabEntry.pull(p));
        }
        List<PushFirstOption> pushOrdered = new ArrayList<>(tabTargets.pushFirstOptions());
        pushOrdered.sort((a, b) -> {
            int c = comparePositionKeyboardCycle(a.firstStepTo(), b.firstStepTo(), g);
            if (c != 0) {
                return c;
            }
            return comparePositionKeyboardCycle(a.weakerFrom(), b.weakerFrom(), g);
        });
        for (PushFirstOption po : pushOrdered) {
            if (!pulls.contains(po.firstStepTo())) {
                ring.add(PullPushTabEntry.push(po));
            }
        }
        return ring;
    }

    /* Writes keyboard pull/push focus fields from one unified Tab ring entry. */
    private void applyPullPushTabEntry(PullPushTabEntry entry) {
        Objects.requireNonNull(entry, "entry");
        if (entry.isPull()) {
            main.playDraft.keyboardPullFocus = entry.pullPos();
            main.playDraft.keyboardPushFocus = null;
            main.playDraft.keyboardPushWeakFrom = null;
        } else {
            PushFirstOption o = Objects.requireNonNull(entry.pushOpt(), "pushOpt");
            main.playDraft.keyboardPullFocus = null;
            main.playDraft.keyboardPushFocus = o.firstStepTo();
            main.playDraft.keyboardPushWeakFrom = o.weakerFrom();
        }
    }

    /* Czech status hint when Tab lands on pull vs push segment of the unified ring. */
    private void setStatusForPullPushTabFocus(boolean pull) {
        if (pull) {
            main.setStatus(
                    "Tahnutí (fialová) — mezerník dokončí výběr soupeře; Tab / Shift+Tab = další cíl (tahnutí nebo tlačení).");
        } else {
            main.setStatus(
                    "Tlačení (oranžová) — tlustý žlutý rámeček = cílové pole prvního kroku, tenčí = tlačená soupeřova figura; mezerník provede výběr; Tab = další varianta.");
        }
    }

    void advancePlayTabFocus(Game g, boolean reverse) {
        PlayTargetBundle tabTargets = main.playTargetBundle(g);
        List<PullPushTabEntry> ring = buildUnifiedPullPushTabRing(tabTargets, g);
        if (ring.isEmpty()) {
            main.playDraft.keyboardPullFocus = null;
            main.playDraft.keyboardPushFocus = null;
            main.playDraft.keyboardPushWeakFrom = null;
            selectNextOwnPieceForPlayKeyboard(g, reverse, "Tab");
            return;
        }
        int curIdx = -1;
        if (main.playDraft.keyboardPullFocus != null) {
            for (int i = 0; i < ring.size(); i++) {
                PullPushTabEntry e = ring.get(i);
                if (e.isPull() && e.pullPos().equals(main.playDraft.keyboardPullFocus)) {
                    curIdx = i;
                    break;
                }
            }
        } else if (main.playDraft.keyboardPushFocus != null) {
            for (int i = 0; i < ring.size(); i++) {
                PullPushTabEntry e = ring.get(i);
                if (e.isPull()) {
                    continue;
                }
                PushFirstOption o = e.pushOpt();
                if (o.firstStepTo().equals(main.playDraft.keyboardPushFocus)
                        && Objects.equals(main.playDraft.keyboardPushWeakFrom, o.weakerFrom())) {
                    curIdx = i;
                    break;
                }
            }
        }
        int nextIdx;
        if (curIdx < 0) {
            nextIdx = reverse ? ring.size() - 1 : 0;
        } else {
            nextIdx = reverse ? (curIdx - 1 + ring.size()) % ring.size() : (curIdx + 1) % ring.size();
        }
        PullPushTabEntry next = ring.get(nextIdx);
        applyPullPushTabEntry(next);
        setStatusForPullPushTabFocus(next.isPull());
        main.refreshAll();
    }

    /* True when keyboard push focus matches some legal PushFirstOption in the current target bundle. */
    private boolean keyboardPushDraftMatches(PlayTargetBundle targets) {
        if (main.playDraft.keyboardPushFocus == null) {
            return false;
        }
        Position f = main.playDraft.keyboardPushFocus;
        Position w = main.playDraft.keyboardPushWeakFrom;
        return targets.pushFirstOptions().stream()
                .anyMatch(o -> o.firstStepTo().equals(f) && (w == null || o.weakerFrom().equals(w)));
    }

    /**
     * Space during PLAY when pull and/or push targets exist: uses keyboard focus if set; otherwise first pull, else
     * first push.
     */
    void activatePlayPullOrPushFromKeyboard(Game g) {
        PlayTargetBundle targets = main.playTargetBundle(g);
        Set<Position> pulls = targets.pullWeakSquares();
        Set<Position> pushT = targets.pushFirstStepTargets();
        if (main.playDraft.keyboardPullFocus != null && pulls.contains(main.playDraft.keyboardPullFocus)) {
            activatePlayPullFromKeyboard(g);
            return;
        }
        if (keyboardPushDraftMatches(targets)) {
            activatePlayPushFromKeyboard(g);
            return;
        }
        if (!pulls.isEmpty()) {
            activatePlayPullFromKeyboard(g);
            return;
        }
        if (!pushT.isEmpty()) {
            activatePlayPushFromKeyboard(g);
        }
    }

    /* Status after selecting own piece: mentions pull squares when any exist (Czech). */
    private void setStatusOwnPieceSelected(Game g, String keyboardLabelOrNull) {
        String head =
                keyboardLabelOrNull == null
                        ? "Vybrána figura"
                        : "Vybrána figura (%s)".formatted(keyboardLabelOrNull);
        if (main.playTargetBundle(g).pullWeakSquares().isEmpty()) {
            main.setStatus("%s — volné pole = krok.".formatted(head));
        } else {
            main.setStatus(
                    "%s — volné pole = krok; po uvolnění můžete kliknout na fialově označenou soupeřovu figuru (tahnutí)."
                            .formatted(head));
        }
    }

    void activatePlayPullFromKeyboard(Game g) {
        Set<Position> pulls = main.playTargetBundle(g).pullWeakSquares();
        if (pulls.isEmpty()) {
            return;
        }
        List<Position> sorted = new ArrayList<>(pulls);
        sorted.sort((a, b) -> comparePositionKeyboardCycle(a, b, g));
        Position pos = main.playDraft.keyboardPullFocus != null && pulls.contains(main.playDraft.keyboardPullFocus)
                ? main.playDraft.keyboardPullFocus
                : sorted.get(0);
        handlePlayBoardActivation(pos.getFileIndex(), pos.getRankIndex());
    }

    void activatePlayPushFromKeyboard(Game g) {
        List<PushFirstOption> opts = sortedPushFirstOptions(main.playTargetBundle(g), g);
        if (opts.isEmpty()) {
            return;
        }
        PushFirstOption chosen = resolveChosenPushOption(opts, main.playDraft);
        main.playDraft.keyboardPushFocus = chosen.firstStepTo();
        main.playDraft.keyboardPushWeakFrom = chosen.weakerFrom();
        handlePlayBoardActivation(chosen.firstStepTo().getFileIndex(), chosen.firstStepTo().getRankIndex());
    }

    /* Push-first options sorted for keyboard activation (first step, then weaker-from cell). */
    private List<PushFirstOption> sortedPushFirstOptions(PlayTargetBundle tabTargets, Game g) {
        List<PushFirstOption> out = new ArrayList<>(tabTargets.pushFirstOptions());
        out.sort((a, b) -> {
            int c = comparePositionKeyboardCycle(a.firstStepTo(), b.firstStepTo(), g);
            if (c != 0) {
                return c;
            }
            return comparePositionKeyboardCycle(a.weakerFrom(), b.weakerFrom(), g);
        });
        return out;
    }

    /* Picks push option matching keyboard focus, else first sorted option (Space on push). */
    private static PushFirstOption resolveChosenPushOption(List<PushFirstOption> opts, PlayTurnDraftState draft) {
        Position f = draft.keyboardPushFocus;
        Position w = draft.keyboardPushWeakFrom;
        if (f != null) {
            for (PushFirstOption o : opts) {
                if (o.firstStepTo().equals(f) && (w == null || o.weakerFrom().equals(w))) {
                    return o;
                }
            }
        }
        return opts.get(0);
    }

    void handlePlayBoardActivation(int modelFile, int modelRank) {
        Game g = main.game();
        if (g == null || g.getState() != GameState.PLAY || main.gameController == null) {
            return;
        }
        if (!main.gameController.getPlayHistory().isBootstrapped()
                || !main.gameController.getPlayHistory().isViewOnTrailingDraftHalf()) {
            main.setStatus("Prohlížíte starší tah — klikněte na poslední řádek v „Historie tahů“ pro pokračování.");
            return;
        }
        PlayTargetBundle boardTargets = main.playTargetBundle(g);
        Position pos = Position.of(modelFile, modelRank);
        PlayerSide side = g.getSideToMove();
        Piece at = main.effectivePieceAt(g, pos);
        if (at != null) {
            if (at.getSide() == side) {
                main.discardDraftRedoBranch();
                main.playDraft.nextFrom = pos;
                main.playDraft.activeSegmentOrigin = pos;
                main.playDraft.keyboardPullFocus = null;
                main.playDraft.keyboardPushFocus = null;
                main.playDraft.keyboardPushWeakFrom = null;
                setStatusOwnPieceSelected(g, null);
                main.refreshAll();
                return;
            }
            if (!main.playDraft.partial.getSteps().isEmpty() && main.playDraft.partial.getSteps().size() < 4) {
                Step last = main.playDraft.partial.getSteps().get(main.playDraft.partial.getSteps().size() - 1);
                if (DefaultRuleEngine.kindOf(last) == StepKind.SLIDE && boardTargets.pullWeakSquares().contains(pos)) {
                    Position vacated = last.getFrom();
                    Step drag = new Step();
                    drag.setKind(StepKind.PULL_DRAG_WEAKER);
                    drag.setFrom(pos);
                    drag.setTo(vacated);
                    if (main.isValidPlaySuffixFromViewHalfStart(g, List.of(drag))) {
                        main.beginPlayDraftMutationBeforeNewSteps();
                        main.playDraft.partial.getSteps().add(PlayDraftNotationSupport.copyStep(drag));
                        main.playDraft.nextFrom = last.getTo();
                        main.appendHistory(new GameHistoryEvent.DraftStepAdded(main.playDraft.partial.getSteps().size()));
                        main.syncPlayDraftFromPartialToHistoryAndApply();
                        main.setStatus(
                                "Tahnutí dokončeno (%d/4). Konec tahu nebo další krok."
                                        .formatted(main.playDraft.partial.getSteps().size()));
                        main.refreshAll();
                        return;
                    }
                }
            }
            main.setStatus("Tuto soupeřovu figuru teď táhnout nelze.");
            return;
        }
        if (main.playDraft.nextFrom == null) {
            main.setStatus("Nejdřív vyberte svou figuru.");
            return;
        }
        if (main.playDraft.partial.getSteps().size() >= 4) {
            main.setStatus("Maximálně 4 kroky — stiskněte Konec tahu.");
            return;
        }
        int remaining = 4 - main.playDraft.partial.getSteps().size();
        if (remaining >= 1) {
            Step slide = new Step();
            slide.setKind(StepKind.SLIDE);
            slide.setFrom(main.playDraft.nextFrom);
            slide.setTo(pos);
            if (main.isValidPlaySuffixFromViewHalfStart(g, List.of(slide))) {
                main.beginPlayDraftMutationBeforeNewSteps();
                main.playDraft.partial.getSteps().add(PlayDraftNotationSupport.copyStep(slide));
                main.playDraft.nextFrom = pos;
                main.appendHistory(new GameHistoryEvent.DraftStepAdded(main.playDraft.partial.getSteps().size()));
                main.syncPlayDraftFromPartialToHistoryAndApply();
                main.setStatus(
                        "Krok přidán (%d/4). Konec tahu nebo další krok."
                                .formatted(main.playDraft.partial.getSteps().size()));
                main.refreshAll();
                return;
            }
        }
        if (remaining >= 2) {
            Map<Position, Piece> occ;
            try {
                occ = DefaultRuleEngine.simulatePlayPrefix(g, new Move());
            } catch (IllegalArgumentException ex) {
                main.setStatus("Neplatný krok.");
                return;
            }
            for (List<Step> bundle : DefaultRuleEngine.enumerateStepBundles(occ, side)) {
                if (bundle.size() > remaining) {
                    continue;
                }
                if (!PlayLegalTargetsSupport.bundleStartsFromPlayNext(main.playDraft.nextFrom, bundle)) {
                    continue;
                }
                if (!bundle.get(0).getTo().equals(pos)) {
                    continue;
                }
                Position weakFilter = main.playDraft.keyboardPushWeakFrom;
                if (weakFilter != null && !weakFilter.equals(bundle.get(0).getFrom())) {
                    continue;
                }
                if (!main.isValidPlaySuffixFromViewHalfStart(g, bundle)) {
                    continue;
                }
                main.beginPlayDraftMutationBeforeNewSteps();
                for (Step st : bundle) {
                    main.playDraft.partial.getSteps().add(PlayDraftNotationSupport.copyStep(st));
                }
                main.playDraft.nextFrom = PlayDraftNotationSupport.endOwnSquareAfterBundle(bundle);
                main.appendHistory(new GameHistoryEvent.DraftStepAdded(main.playDraft.partial.getSteps().size()));
                main.syncPlayDraftFromPartialToHistoryAndApply();
                main.setStatus(
                        "Krok přidán (%d/4). Konec tahu nebo další krok."
                                .formatted(main.playDraft.partial.getSteps().size()));
                main.refreshAll();
                return;
            }
        }
        main.setStatus("Neplatný krok.");
    }

    /**
     * Commits the current draft as a full turn.
     *
     * @return {@code true} if the turn was accepted and recorded
     */
    boolean tryEndPlayTurn() {
        Game g = main.game();
        if (g == null || g.getState() != GameState.PLAY || main.gameController == null) {
            return false;
        }
        main.reconcilePlayPartialWithHistoryView();
        if (main.playDraft.partial.getSteps().isEmpty()) {
            main.setStatus("Přidejte aspoň jeden krok.");
            return false;
        }
        main.gameController.restoreTrailingDraftTurnStartForSubmit();
        String prefix = main.gameController.nextPlayNotationPrefix();
        Move submit = PlayDraftNotationSupport.copyMove(main.playDraft.partial);
        String notationLine = ArimaaNotation.formatFullTurn(g.getBoard(), submit, prefix);
        log.debug("submitting play turn: {} steps", submit.getSteps().size());
        PlayerSide mover = g.getSideToMove();
        if (!main.gameController.submitHumanMove(submit)) {
            log.info("submitHumanMove rejected (illegal or invalid state)");
            main.setStatus("Tah není platný.");
            main.gameController.applyPlayHistoryViewToGame();
            main.refreshAll();
            return false;
        }
        /* SFX already played on each draft step; avoid repeating trap wail on turn submit. */
        main.gameController.recordCommittedPlayTurn(submit, notationLine);
        main.notifyPlayChessClockAfterCommittedTurn(mover);
        main.clearPlayTurnUi();
        main.syncPlayPartialFromHistory();
        if (g.getState() == GameState.GAME_OVER) {
            PlayerSide w = g.getMatchWinner();
            log.info("play turn ended: GAME_OVER winner={}", w);
            main.setStatus(w == null ? "Konec hry." : "Konec hry — vyhrál %s.".formatted(MainController.sideName(w)));
            main.playVictoryWinnerMediaIfEnabled(w);
        } else {
            log.info("play turn ended: state={} sideToMove={}", g.getState(), g.getSideToMove());
            main.setStatus(MainController.statusPlayerOnTurn(g.getSideToMove()));
        }
        main.appendHistory(new GameHistoryEvent.TurnCommitted(notationLine));
        main.refreshAll();
        return true;
    }
}
