package cz.cvut.fel.pjv.arimaa.ui;

import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.enums.GameState;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/**
 * Scene-level {@link KeyEvent#KEY_PRESSED} filter for SETUP shortcuts and PLAY navigation (runs before {@link javafx.scene.control.ScrollPane} consumes arrows).
 */
public final class PlaySceneKeyHandler {

    private PlaySceneKeyHandler() {}

    /**
     * Capture phase: Esc (cancel draft), arrows / WASD (when a piece is selected), Tab / Shift+Tab (pull targets, else push
     * targets, else own pieces), Ctrl+Tab / Ctrl+Shift+Tab (always own pieces; e.g. during push), Space (complete pull), Enter;
     * SETUP: Space / Ctrl+Space / Ctrl+Enter; when the mover is the computer: Space toggles autoplay pause.
     */
    public static void install(Scene scene, MainController main) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getTarget() instanceof TextInputControl t && t.isEditable()) {
                return;
            }
            Game g = main.game();
            if (g != null && (g.getState() == GameState.SETUP_GOLD || g.getState() == GameState.SETUP_SILVER)) {
                if (!main.isLocalInteractiveTurn(g)) {
                    if (main.isComputerControlled(g.getSideToMove())) {
                        if (e.getCode() == KeyCode.SPACE && !e.isControlDown() && !e.isAltDown()) {
                            main.toggleComputerAutoplayPauseFromKeyboard();
                            e.consume();
                        }
                    }
                    return;
                }
                if (e.getCode() == KeyCode.SPACE && e.isControlDown() && !e.isAltDown()) {
                    Button chess = main.chessButton;
                    if (chess != null && !chess.isDisabled()) {
                        main.applyChessMappedSetupFromUi();
                        e.consume();
                    }
                    return;
                }
                if (e.getCode() == KeyCode.SPACE && !e.isControlDown() && !e.isAltDown()) {
                    Button random = main.randomButton;
                    if (random != null && !random.isDisabled()) {
                        main.performRandomSetupPlacementAction();
                        e.consume();
                    }
                    return;
                }
                if (e.getCode() == KeyCode.ENTER && e.isControlDown() && !e.isAltDown()) {
                    Button done = main.doneButton;
                    if (done != null && !done.isDisabled()) {
                        main.tryCompleteSetupFromUi();
                        e.consume();
                    }
                    return;
                }
                return;
            }
            if (g == null || g.getState() != GameState.PLAY) {
                return;
            }
            if (!main.isLocalInteractiveTurn(g)) {
                if (main.isComputerControlled(g.getSideToMove())) {
                    if (e.getCode() == KeyCode.SPACE && !e.isControlDown() && !e.isAltDown()) {
                        main.toggleComputerAutoplayPauseFromKeyboard();
                        e.consume();
                    }
                }
                return;
            }
            if (e.getCode() == KeyCode.ESCAPE) {
                if (main.tryCancelPlayDraftFromUi()) {
                    e.consume();
                }
                return;
            }
            if (e.getCode() == KeyCode.ENTER) {
                main.tryEndPlayTurn();
                e.consume();
                return;
            }
            if (e.getCode() == KeyCode.TAB) {
                if (e.isControlDown() && !e.isAltDown()) {
                    main.advancePlayTabFocusOwnPiecesOnly(g, e.isShiftDown());
                    e.consume();
                } else if (!e.isControlDown()) {
                    main.advancePlayTabFocus(g, e.isShiftDown());
                    e.consume();
                }
                return;
            }
            if (e.getCode() == KeyCode.SPACE && !e.isControlDown() && !e.isAltDown()) {
                PlayTargetBundle targets = main.playTargetBundle(g);
                if (!targets.pullWeakSquares().isEmpty()) {
                    main.activatePlayPullFromKeyboard(g);
                    e.consume();
                    return;
                }
                if (!targets.pushFirstStepTargets().isEmpty()) {
                    main.activatePlayPushFromKeyboard(g);
                    e.consume();
                }
                return;
            }
            if (e.isShortcutDown()) {
                return;
            }
            int[] dVis = BoardViewOrientation.visualDeltaForPlayNavigation(e.getCode());
            if (dVis == null || main.playDraft.nextFrom == null) {
                return;
            }
            var pos = main.boardOrientation.modelNeighborFromVisualDelta(g, main.playDraft.nextFrom, dVis[0], dVis[1]);
            main.handlePlayBoardActivation(pos.getFileIndex(), pos.getRankIndex());
            e.consume();
        });
    }
}
