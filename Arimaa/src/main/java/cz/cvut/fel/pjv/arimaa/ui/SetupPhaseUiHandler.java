package cz.cvut.fel.pjv.arimaa.ui;

import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.Piece;
import cz.cvut.fel.pjv.arimaa.model.Position;
import cz.cvut.fel.pjv.arimaa.model.enums.GameState;
import cz.cvut.fel.pjv.arimaa.model.enums.PieceType;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;

/**
 * SETUP phase: reserve picks, board placement/return, presets, and completing setup.
 */
final class SetupPhaseUiHandler {

    private final MainController main;
    /** Next preset for „Šachová rozestavení“ rotation ({@link Game#CHESS_SETUP_ROTATION_COUNT} templates). */
    private int chessSetupRotateIndex;

    SetupPhaseUiHandler(MainController main) {
        this.main = main;
    }

    void resetChessPresetRotation() {
        chessSetupRotateIndex = 0;
    }

    void onPickReserve(PieceType type) {
        Game g = main.game();
        if (g == null) {
            return;
        }
        GameState st = g.getState();
        if (st != GameState.SETUP_GOLD && st != GameState.SETUP_SILVER) {
            return;
        }
        PlayerSide side = g.getSideToMove();
        if (g.beginPlacingPieceFromReserve(side, type)) {
            main.setStatus("Máte figuru v ruce — klikněte na volné domovské pole.");
            main.recordTimeline();
        } else {
            main.setStatus("Tento typ v rezervě není nebo nejste ve fázi rozestavení.");
        }
        main.refreshAll();
    }

    /**
     * Board activation during SETUP (model coordinates); caller must ensure {@link Game#getState()} is a setup state.
     */
    void onBoardCellClick(int modelFile, int modelRank) {
        Game g = main.game();
        if (g == null) {
            return;
        }
        PlayerSide side = g.getSideToMove();
        Position pos = Position.of(modelFile, modelRank);
        Piece hand = g.getSetupHand();

        if (hand != null) {
            if (g.confirmSetupHandPlacement(pos)) {
                main.setStatus("Figura umístěna.");
                main.recordTimeline();
            } else {
                main.setStatus("Sem nelze umístit (domov, kapacita typu nebo obsazené pole).");
            }
        } else {
            if (g.returnPieceFromBoardToReserve(side, pos)) {
                main.setStatus("Figura vrácena do rezervy.");
                main.recordTimeline();
            } else {
                main.setStatus("Vyberte figuru z rezervy nebo klikněte na svou figuru na domovském poli.");
            }
        }
        main.refreshAll();
    }

    void performRandomSetupPlacementAction() {
        Game g = main.game();
        if (g == null) {
            return;
        }
        PlayerSide side = g.getSideToMove();
        if (g.allSetupPiecesOnBoard(side)) {
            if (g.shuffleSetupPiecesOnHomeRandomly(side)) {
                main.setStatus("Figury na domovských řadách náhodně přeřazeny.");
                main.recordTimeline();
            } else {
                main.setStatus("Náhodné přeřazení se nepovedlo.");
            }
        } else if (g.placeRemainingPiecesRandomly(side)) {
            main.setStatus("Zbývající figury umístěny náhodně.");
            main.recordTimeline();
        } else {
            main.setStatus("Náhodné umístění se nepovedlo (musí sedět počet figurek a volných polí).");
        }
        main.refreshAll();
    }

    void applyChessMappedSetupFromUi() {
        Game g = main.game();
        if (g == null) {
            return;
        }
        PlayerSide side = g.getSideToMove();
        int preset = Math.floorMod(chessSetupRotateIndex++, Game.CHESS_SETUP_ROTATION_COUNT);
        if (g.applyChessMappedSetup(side, preset)) {
            String[] names = {"opačné šachy", "symetrické", "MH", "HH"};
            main.setStatus(
                    "Šachová rozestavení — %s (%d/%d)."
                            .formatted(names[preset], preset + 1, Game.CHESS_SETUP_ROTATION_COUNT));
            main.recordTimeline();
        } else {
            chessSetupRotateIndex--;
            main.setStatus("Šachovou rozestavení nelze použít.");
        }
        main.refreshAll();
    }

    void tryCompleteSetupFromUi() {
        Game g = main.game();
        if (g == null) {
            return;
        }
        PlayerSide side = g.getSideToMove();
        if (g.tryCompleteSetup(side)) {
            main.setStatus("Rozestavení dokončeno.");
            if (g.getState() == GameState.PLAY) {
                if (main.gameController != null) {
                    main.gameController.enterPlayPhaseBootstrap();
                }
            } else {
                main.recordTimeline();
            }
        } else {
            main.setStatus("Rozestavení nelze dokončit (rezerva, multiset, 16 figurek na domově…).");
        }
        main.refreshAll();
    }
}
