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
import java.util.Set;

/**
 * PLAY phase: board activation, end turn, Tab / Space keyboard targets.
 */
final class PlayPhaseUiHandler {

    private static final Logger log = LoggerFactory.getLogger(PlayPhaseUiHandler.class);

    private final MainController main;

    PlayPhaseUiHandler(MainController main) {
        this.main = main;
    }

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

    private boolean moverVisualBottom(Game g) {
        PlayerSide m = g.getSideToMove();
        if (m == PlayerSide.GOLD) {
            return main.boardOrientation.boardGoldVisualBottom(g);
        }
        return !main.boardOrientation.boardGoldVisualBottom(g);
    }

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
        selectNextOwnPieceForPlayKeyboard(g, reverse, "Ctrl+Tab");
    }

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

    void advancePlayTabFocus(Game g, boolean reverse) {
        PlayTargetBundle tabTargets = main.playTargetBundle(g);
        Set<Position> pulls = tabTargets.pullWeakSquares();
        if (!pulls.isEmpty()) {
            main.playDraft.keyboardPushFocus = null;
            List<Position> list = new ArrayList<>(pulls);
            list.sort((a, b) -> comparePositionKeyboardCycle(a, b, g));
            if (main.playDraft.keyboardPullFocus != null && !pulls.contains(main.playDraft.keyboardPullFocus)) {
                main.playDraft.keyboardPullFocus = null;
            }
            int idx;
            if (main.playDraft.keyboardPullFocus == null) {
                idx = reverse ? list.size() - 1 : 0;
            } else {
                int cur = list.indexOf(main.playDraft.keyboardPullFocus);
                if (cur < 0) {
                    cur = 0;
                }
                idx = reverse ? (cur - 1 + list.size()) % list.size() : (cur + 1) % list.size();
            }
            main.playDraft.keyboardPullFocus = list.get(idx);
            main.setStatus("Tahnutí — mezerník dokončí výběr soupeře (Tab = další figura).");
            main.refreshAll();
            return;
        }
        main.playDraft.keyboardPullFocus = null;
        Set<Position> pushT = tabTargets.pushFirstStepTargets();
        if (!pushT.isEmpty()) {
            List<Position> plist = new ArrayList<>(pushT);
            plist.sort((a, b) -> comparePositionKeyboardCycle(a, b, g));
            if (main.playDraft.keyboardPushFocus != null && !pushT.contains(main.playDraft.keyboardPushFocus)) {
                main.playDraft.keyboardPushFocus = null;
            }
            int pidx;
            if (main.playDraft.keyboardPushFocus == null) {
                pidx = reverse ? plist.size() - 1 : 0;
            } else {
                int cur = plist.indexOf(main.playDraft.keyboardPushFocus);
                if (cur < 0) {
                    cur = 0;
                }
                pidx = reverse ? (cur - 1 + plist.size()) % plist.size() : (cur + 1) % plist.size();
            }
            main.playDraft.keyboardPushFocus = plist.get(pidx);
            main.setStatus("Tlačení — mezerník provede výběr (Tab = další oranžový cíl).");
            main.refreshAll();
            return;
        }
        main.playDraft.keyboardPushFocus = null;
        selectNextOwnPieceForPlayKeyboard(g, reverse, "Tab");
    }

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
        Set<Position> pushT = main.playTargetBundle(g).pushFirstStepTargets();
        if (pushT.isEmpty()) {
            return;
        }
        List<Position> sorted = new ArrayList<>(pushT);
        sorted.sort((a, b) -> comparePositionKeyboardCycle(a, b, g));
        Position pos = main.playDraft.keyboardPushFocus != null && pushT.contains(main.playDraft.keyboardPushFocus)
                ? main.playDraft.keyboardPushFocus
                : sorted.get(0);
        handlePlayBoardActivation(pos.getFileIndex(), pos.getRankIndex());
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

    void tryEndPlayTurn() {
        Game g = main.game();
        if (g == null || g.getState() != GameState.PLAY || main.gameController == null) {
            return;
        }
        main.reconcilePlayPartialWithHistoryView();
        if (main.playDraft.partial.getSteps().isEmpty()) {
            main.setStatus("Přidejte aspoň jeden krok.");
            return;
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
            return;
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
        } else {
            log.info("play turn ended: state={} sideToMove={}", g.getState(), g.getSideToMove());
            main.setStatus("Tah proveden.");
        }
        main.appendHistory(new GameHistoryEvent.TurnCommitted(notationLine));
        main.refreshAll();
    }
}
