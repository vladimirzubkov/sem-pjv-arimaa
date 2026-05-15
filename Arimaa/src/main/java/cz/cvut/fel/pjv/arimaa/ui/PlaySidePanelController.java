package cz.cvut.fel.pjv.arimaa.ui;

import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.PlayTurnHistory;
import cz.cvut.fel.pjv.arimaa.model.enums.PieceType;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;

import java.util.ArrayList;
import java.util.List;

/**
 * Captures tray, notation list, and PLAY action buttons (delegated from {@link MainController}).
 */
final class PlaySidePanelController {

    private static final double CAPTURE_ICON_MAX = 36;

    private final MainController main;

    PlaySidePanelController(MainController main) {
        this.main = main;
    }

    void refreshCapturedPanel(Game g) {
        if (!MainUiLayoutPhase.showCapturesAndNotationHistory(g)) {
            return;
        }
        fillCaptureFlow(main.goldCapturesPane, g, PlayerSide.GOLD);
        fillCaptureFlow(main.silverCapturesPane, g, PlayerSide.SILVER);
    }

    void refreshNotationHistory(boolean autoScrollToSelected) {
        if (main.gameController == null) {
            main.notationHistoryItems.clear();
            return;
        }
        List<String> lines = main.buildNotationHistoryLinesForSidePanel();
        main.suppressHistoryListEvents = true;
        main.notationHistoryItems.setAll(lines);
        PlayTurnHistory ph = main.gameController.getPlayHistory();
        if (ph.isBootstrapped()) {
            List<Integer> vis = ph.visibleHalfIndicesForDisplay(true);
            int sel = vis.indexOf(ph.viewHalfIndex());
            if (sel >= 0) {
                main.notationHistoryList.getSelectionModel().select(sel);
            } else {
                main.notationHistoryList.getSelectionModel().clearSelection();
            }
        }
        main.suppressHistoryListEvents = false;
        if (autoScrollToSelected) {
            Platform.runLater(
                    () -> {
                        int i = main.notationHistoryList.getSelectionModel().getSelectedIndex();
                        if (i >= 0 && i < main.notationHistoryItems.size()) {
                            main.scrollNotationHistoryToShowIndex(i);
                        } else if (!main.notationHistoryItems.isEmpty()) {
                            main.scrollNotationHistoryToShowIndex(main.notationHistoryItems.size() - 1);
                        }
                    });
        }
    }

    void refreshPlayActionButtons(Game g) {
        if (main.playEndTurnButton == null) {
            return;
        }
        boolean play = MainUiLayoutPhase.isPlay(g);
        boolean blockedPlay = play && !main.isLocalInteractiveTurn(g);
        if (main.playEndTurnButton != null) {
            boolean canEnd =
                    play
                            && main.gameController != null
                            && main.gameController.getPlayHistory().isBootstrapped()
                            && main.gameController.getPlayHistory().isAtEditableDraftTail()
                            && !main.playDraft.partial.getSteps().isEmpty();
            main.playEndTurnButton.setDisable(!canEnd || blockedPlay);
        }
        if (main.playCancelTurnButton != null) {
            boolean canCancelNormally =
                    play
                            && main.gameController != null
                            && main.gameController.getPlayHistory().isBootstrapped()
                            && main.gameController.getPlayHistory().isAtEditableDraftTail()
                            && (!main.playDraft.partial.getSteps().isEmpty() || main.playDraft.nextFrom != null);
            boolean trapBlocksCancel =
                    main.forbidCancelAfterTrapItem != null
                            && main.forbidCancelAfterTrapItem.isSelected()
                            && main.viewPrefixRemovesPieceViaTrap();
            main.playCancelTurnButton.setDisable(!canCancelNormally || trapBlocksCancel || blockedPlay);
        }
    }

    private void fillCaptureFlow(FlowPane pane, Game g, PlayerSide capturer) {
        pane.getChildren().clear();
        List<PieceType> types = new ArrayList<>(g.getTrapCapturesSnapshot(capturer));
        if (types.isEmpty()) {
            pane.getChildren().add(new Label("—"));
            return;
        }
        PlayerSide victimSide = capturer == PlayerSide.GOLD ? PlayerSide.SILVER : PlayerSide.GOLD;
        for (PieceType t : types) {
            if (main.pieceSkinUsesFigureArt()) {
                Image img = main.figureRasterCache.getRasterized(victimSide, t, CAPTURE_ICON_MAX);
                if (img != null) {
                    ImageView iv = new ImageView(img);
                    iv.setFitWidth(CAPTURE_ICON_MAX);
                    iv.setFitHeight(CAPTURE_ICON_MAX);
                    iv.setPreserveRatio(true);
                    iv.setSmooth(true);
                    pane.getChildren().add(iv);
                } else {
                    pane.getChildren().add(new Label(String.valueOf(t.notationChar())));
                }
            } else {
                pane.getChildren().add(new Label(String.valueOf(t.notationChar())));
            }
        }
    }
}
