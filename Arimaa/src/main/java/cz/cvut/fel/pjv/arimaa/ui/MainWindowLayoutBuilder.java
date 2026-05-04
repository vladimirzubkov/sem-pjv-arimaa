package cz.cvut.fel.pjv.arimaa.ui;

import ch.qos.logback.classic.Level;
import cz.cvut.fel.pjv.arimaa.logging.LoggingSupport;
import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.PlayTurnHistory;
import cz.cvut.fel.pjv.arimaa.model.enums.GameState;
import cz.cvut.fel.pjv.arimaa.model.enums.PieceType;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the side panel, menus, and setup/play control nodes for {@link MainController#attachToStage(javafx.stage.Stage)}.
 */
public final class MainWindowLayoutBuilder {

    /**
     * References needed by {@link MainController#attachToStage(javafx.stage.Stage)} after layout construction.
     */
    public record MainWindowLayoutResult(VBox sidePanel, MenuBar menuBar) {}

    private MainWindowLayoutBuilder() {}

    /** Wires reserve/setup/play controls, captures and notation boxes, list listener, side panel, and menu bar on {@code main}. */
    public static MainWindowLayoutResult buildSidePanelAndMenus(MainController main) {
        setupReserveAndPlayButtons(main);
        buildCapturesAndNotationBoxes(main);
        wireNotationHistoryList(main);
        VBox side = buildSidePanel(main);
        MenuBar bar = buildMenuBar(main);
        return new MainWindowLayoutResult(side, bar);
    }

    static void setupReserveAndPlayButtons(MainController main) {
        main.reserveBox = new VBox(6, new Label("Rezerva (klik = vzít figuru):"));
        main.reserveBox.setPadding(new Insets(0, 0, 8, 0));
        for (PieceType type : PieceType.values()) {
            Button b = new Button(MainController.labelForReserveButton(type, 0));
            b.setMaxWidth(Double.MAX_VALUE);
            b.setOnAction(e -> main.onPickReserve(type));
            main.reserveButtons.put(type, b);
            main.reserveBox.getChildren().add(b);
        }

        main.cancelHandButton = new Button("Zrušit výběr z ruky");
        main.cancelHandButton.setMaxWidth(Double.MAX_VALUE);
        main.cancelHandButton.setOnAction(e -> {
            Game g = main.game();
            if (g != null) {
                boolean changed = g.getSetupHand() != null;
                g.cancelPendingSetupPlacement();
                if (changed) {
                    main.recordTimeline();
                }
                main.refreshAll();
            }
        });

        main.randomButton = new Button("Náhodně doplnit zbytek");
        main.randomButton.setMaxWidth(Double.MAX_VALUE);
        main.randomButton.setOnAction(e -> main.performRandomSetupPlacementAction());

        main.chessButton = new Button("Šachová rozestavení");
        main.chessButton.setMaxWidth(Double.MAX_VALUE);
        main.chessButton.setOnAction(e -> main.applyChessMappedSetupFromUi());

        main.doneButton = new Button("Hotovo (ukončit rozestavení)");
        main.doneButton.setMaxWidth(Double.MAX_VALUE);
        main.doneButton.setOnAction(e -> main.tryCompleteSetupFromUi());

        main.playEndTurnButton = new Button("Konec tahu");
        main.playEndTurnButton.setMaxWidth(Double.MAX_VALUE);
        main.playEndTurnButton.setOnAction(e -> main.tryEndPlayTurn());

        main.playCancelTurnButton = new Button("Zrušit rozpracovaný tah");
        main.playCancelTurnButton.setMaxWidth(Double.MAX_VALUE);
        main.playCancelTurnButton.setFocusTraversable(false);
        main.playCancelTurnButton.setOnAction(e -> main.tryCancelPlayDraftFromUi());
    }

    static void wireNotationHistoryList(MainController main) {
        main.notationHistoryList.setFocusTraversable(false);
        main.notationHistoryList.setFixedCellSize(22);
        main.notationHistoryList.setPrefHeight(220);
        main.notationHistoryList.setMaxHeight(220);
        main.notationHistoryList.setMinHeight(72);
        main.notationHistoryList.setStyle("-fx-font-family: Consolas; -fx-font-size: 11px;");
        main.notationHistoryList.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        main.notationHistoryList.getSelectionModel().selectedIndexProperty().addListener((obs, o, n) -> {
            if (main.suppressHistoryListEvents || n == null || n.intValue() < 0 || main.gameController == null) {
                return;
            }
            Game g = main.game();
            if (g == null || g.getState() != GameState.PLAY) {
                return;
            }
            PlayTurnHistory ph = main.gameController.getPlayHistory();
            if (!ph.isBootstrapped()) {
                return;
            }
            List<Integer> vis = ph.visibleHalfIndicesForDisplay(true);
            int idx = n.intValue();
            if (idx >= vis.size()) {
                return;
            }
            ph.navigateToVisibleLine(idx, true);
            main.gameController.applyPlayHistoryViewToGame();
            main.syncPlayPartialFromHistory();
            main.refreshAll();
        });
    }

    static void buildCapturesAndNotationBoxes(MainController main) {
        main.goldCapturesPane.setPrefWrapLength(220);
        main.silverCapturesPane.setPrefWrapLength(220);
        main.capturesBox.getChildren().addAll(
                new Label("Zajaté (Gold):"),
                main.goldCapturesPane,
                new Label("Zajaté (Silver):"),
                main.silverCapturesPane);

        main.notationBox.getChildren().addAll(new Label("Historie tahů:"), main.notationHistoryList);
        main.notationBox.setVisible(false);
        main.notationBox.setManaged(false);
    }

    static VBox buildSidePanel(MainController main) {
        main.handLabel.setWrapText(true);
        main.handLabel.setMaxWidth(220);
        main.handPieceGraphic.setPreserveRatio(true);
        main.handPieceGraphic.setSmooth(true);

        main.statusLabel.setWrapText(true);
        main.statusLabel.setMaxWidth(240);
        main.setStatus("Rozestavte Gold; pak Hotovo. Silver totéž.");

        VBox sidePanel = new VBox(10,
                new Label("Stav:"),
                main.statusLabel,
                main.handLabel,
                spacer(8),
                main.capturesBox,
                main.notationBox,
                main.reserveBox,
                main.cancelHandButton,
                main.randomButton,
                main.chessButton,
                main.doneButton,
                main.playEndTurnButton,
                main.playCancelTurnButton);
        sidePanel.setPadding(new Insets(12));
        sidePanel.setPrefWidth(260);
        sidePanel.setMaxHeight(Double.MAX_VALUE);
        sidePanel.setBackground(new Background(new BackgroundFill(
                Color.rgb(250, 250, 252), CornerRadii.EMPTY, Insets.EMPTY)));
        return sidePanel;
    }

    static MenuBar buildMenuBar(MainController main) {
        Menu menuHra = new Menu("Hra");
        MenuItem novaHraItem = new MenuItem("Nová hra");
        novaHraItem.setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN));
        novaHraItem.setOnAction(e -> main.startNewGameAction());
        MenuItem ulozitHruItem = new MenuItem("Uložit hru…");
        ulozitHruItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN));
        ulozitHruItem.setOnAction(e -> main.saveLoad.saveGameToFile());
        MenuItem nacistHruItem = new MenuItem("Načíst hru…");
        nacistHruItem.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN));
        nacistHruItem.setOnAction(e -> main.saveLoad.loadGameFromFile());
        MenuItem ukoncitItem = new MenuItem("Ukončit");
        ukoncitItem.setAccelerator(new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN));
        ukoncitItem.setOnAction(e -> Platform.exit());
        menuHra.getItems()
                .addAll(
                        novaHraItem,
                        new SeparatorMenuItem(),
                        ulozitHruItem,
                        nacistHruItem,
                        new SeparatorMenuItem(),
                        ukoncitItem);

        Menu menuTah = new Menu("Tah");
        main.undoMenuItem = new MenuItem("Zpět");
        main.undoMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN));
        main.undoMenuItem.setOnAction(e -> main.performUndo());
        main.redoMenuItem = new MenuItem("Vpřed");
        main.redoMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN));
        main.redoMenuItem.setOnAction(e -> main.performRedo());
        menuTah.getItems().addAll(main.undoMenuItem, main.redoMenuItem);

        Menu menuGameplay = new Menu("Gameplay");
        Menu menuSkin = new Menu("Skin");
        ToggleGroup skinToggleGroup = new ToggleGroup();
        RadioMenuItem skinNoneItem = new RadioMenuItem("None");
        skinNoneItem.setToggleGroup(skinToggleGroup);
        skinNoneItem.setUserData(null);
        List<String> skinDirs = FigureSvgRasterCache.discoverSkinDirectoryNames();
        List<RadioMenuItem> skinDirItems = new ArrayList<>();
        for (String dir : skinDirs) {
            RadioMenuItem it = new RadioMenuItem(dir);
            it.setToggleGroup(skinToggleGroup);
            it.setUserData(dir);
            skinDirItems.add(it);
        }
        String initialSkin =
                skinDirs.stream()
                        .filter(d -> FigureSvgRasterCache.FALLBACK_SKIN_NAME.equalsIgnoreCase(d))
                        .findFirst()
                        .orElseGet(() -> skinDirs.isEmpty() ? FigureSvgRasterCache.FALLBACK_SKIN_NAME : skinDirs.get(0));
        main.figureRasterCache.setSkinDirectory(initialSkin);
        main.figureSkinFolder = initialSkin;
        for (RadioMenuItem it : skinDirItems) {
            if (it.getUserData() instanceof String dir && dir.equalsIgnoreCase(initialSkin)) {
                it.setSelected(true);
                break;
            }
        }
        skinToggleGroup.selectedToggleProperty().addListener((obs, prev, toggled) -> {
            if (!(toggled instanceof RadioMenuItem r)) {
                return;
            }
            Object ud = r.getUserData();
            if (ud == null) {
                main.figureSkinFolder = null;
            } else if (ud instanceof String dir) {
                main.figureSkinFolder = dir;
                main.figureRasterCache.setSkinDirectory(dir);
            }
            main.refreshAll();
        });
        List<MenuItem> skinMenuItems = new ArrayList<>();
        skinMenuItems.add(skinNoneItem);
        skinMenuItems.add(new SeparatorMenuItem());
        skinMenuItems.addAll(skinDirItems);
        menuSkin.getItems().addAll(skinMenuItems);
        menuGameplay.getItems().add(menuSkin);
        menuGameplay.getItems().add(new SeparatorMenuItem());
        main.forbidCancelAfterTrapItem = new CheckMenuItem(
                "Po pádu figury do pasti nelze zrušit rozpracovaný tah");
        main.forbidCancelAfterTrapItem.setSelected(false);
        main.forbidCancelAfterTrapItem.selectedProperty().addListener((obs, prev, now) -> main.refreshAll());
        menuGameplay.getItems().add(main.forbidCancelAfterTrapItem);

        main.rotateBoardToMoverItem = new CheckMenuItem("Otáčet desku — hráč na tahu dole");
        main.rotateBoardToMoverItem.setSelected(false);
        main.rotateBoardToMoverItem.selectedProperty().addListener((obs, prev, now) -> main.refreshAll());
        menuGameplay.getItems().add(main.rotateBoardToMoverItem);

        Menu menuLog = new Menu("Log");
        Menu menuLogLevel = new Menu("Logback Level");
        main.logLevelToggleGroup = new ToggleGroup();
        for (Level lvl : List.of(Level.OFF, Level.ERROR, Level.WARN, Level.INFO, Level.DEBUG, Level.TRACE)) {
            RadioMenuItem item = new RadioMenuItem(lvl.toString());
            item.setToggleGroup(main.logLevelToggleGroup);
            item.setUserData(lvl);
            menuLogLevel.getItems().add(item);
        }
        main.logLevelToggleGroup.selectedToggleProperty().addListener((obs, prev, toggled) -> {
            if (main.suppressLogLevelSync || toggled == null) {
                return;
            }
            if (toggled instanceof RadioMenuItem r && r.getUserData() instanceof Level selected) {
                LoggingSupport.setLevel(selected);
            }
        });
        menuLog.getItems().add(menuLogLevel);

        main.logToFileItem = new CheckMenuItem("Zapisovat do souboru");
        main.logToFileItem.setOnAction(e -> {
            if (main.suppressFileLogSync) {
                return;
            }
            if (main.logToFileItem.isSelected()) {
                if (!LoggingSupport.enableFileLogging(LoggingSupport.defaultLogFilePath())) {
                    main.suppressFileLogSync = true;
                    try {
                        main.logToFileItem.setSelected(false);
                    } finally {
                        main.suppressFileLogSync = false;
                    }
                    main.setStatus("Log do souboru: zapnutí se nepodařilo (viz konzole).");
                } else {
                    main.setStatus("Log do souboru zapnut.");
                }
            } else {
                LoggingSupport.disableFileLogging();
                main.setStatus("Log do souboru vypnut.");
            }
        });
        menuLog.getItems().add(main.logToFileItem);
        menuLog.setOnShowing(e -> {
            main.syncLogLevelMenuSelection();
            main.syncLogToFileMenuSelection();
        });

        MenuBar menuBar = new MenuBar();
        menuBar.getMenus().addAll(menuHra, menuTah, menuGameplay, menuLog);
        return menuBar;
    }

    private static Region spacer(int h) {
        Region r = new Region();
        r.setMinHeight(h);
        return r;
    }
}
