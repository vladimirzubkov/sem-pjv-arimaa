package cz.cvut.fel.pjv.arimaa.ui;

import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.enums.PieceType;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.util.Map;

/**
 * Setup reserve tray and setup-phase action buttons (delegated from {@link MainController}).
 */
final class SetupSidePanelController {

    private static final double RESERVE_ICON_MAX = 26;

    private final MainController main;

    SetupSidePanelController(MainController main) {
        this.main = main;
    }

    void refreshReserveButtons(Game g) {
        boolean setup = MainUiLayoutPhase.isSetup(g);
        PlayerSide side = g.getSideToMove();
        Map<PieceType, Integer> counts = g.setupReserveCountsByType(side);
        for (PieceType type : PieceType.values()) {
            Button b = main.reserveButtons.get(type);
            int n = counts.getOrDefault(type, 0);
            b.setText(MainController.labelForReserveButton(type, n));
            b.setDisable(!setup || n == 0);
            if (main.pieceSkinUsesFigureArt() && setup && n > 0) {
                Image icon = main.figureRasterCache.getRasterized(side, type, RESERVE_ICON_MAX);
                if (icon != null) {
                    ImageView iv = new ImageView(icon);
                    iv.setFitWidth(RESERVE_ICON_MAX);
                    iv.setFitHeight(RESERVE_ICON_MAX);
                    iv.setPreserveRatio(true);
                    iv.setSmooth(true);
                    b.setGraphic(iv);
                    b.setContentDisplay(ContentDisplay.LEFT);
                } else {
                    b.setGraphic(null);
                    b.setContentDisplay(ContentDisplay.LEFT);
                }
            } else {
                b.setGraphic(null);
                b.setContentDisplay(ContentDisplay.LEFT);
            }
        }
    }

    void refreshSetupActionButtons(Game g) {
        boolean setup = MainUiLayoutPhase.isSetup(g);
        PlayerSide side = g.getSideToMove();
        main.cancelHandButton.setDisable(!setup || g.getSetupHand() == null);
        main.chessButton.setDisable(!setup);
        main.doneButton.setDisable(!setup || !g.allSetupPiecesOnBoard(side));
        boolean canRandomFill = setup && g.canFillRemainingReserveRandomly(side);
        boolean canRandomShuffle = setup && g.allSetupPiecesOnBoard(side);
        main.randomButton.setDisable(!setup || (!canRandomFill && !canRandomShuffle));
        if (setup) {
            main.randomButton.setText(canRandomShuffle ? "Náhodně rozestavit" : "Náhodně doplnit zbytek");
        }
    }
}
