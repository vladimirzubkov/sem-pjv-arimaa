package cz.cvut.fel.pjv.arimaa.ui;

import ch.qos.logback.classic.Level;
import cz.cvut.fel.pjv.arimaa.logging.LoggingSupport;
import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.PlayTurnHistory;
import cz.cvut.fel.pjv.arimaa.model.enums.PieceType;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerControllerKind;
import javafx.beans.binding.Bindings;
import javafx.application.Platform;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
        main.cancelHandButton.setOnAction(e -> main.onCancelSetupHandFromUi());

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
        main.notationHistoryList.setPrefHeight(176);
        main.notationHistoryList.setMaxHeight(176);
        main.notationHistoryList.setMinHeight(72);
        main.notationHistoryList.setStyle("-fx-font-family: Consolas; -fx-font-size: 11px;");
        main.notationHistoryList.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        main.notationHistoryList.getSelectionModel().selectedIndexProperty().addListener((obs, o, n) -> {
            if (main.suppressHistoryListEvents
                    || n == null
                    || n.intValue() < 0
                    || main.gameController == null
                    || main.isComputerPlayPending()
                    || main.isNetworkClient()) {
                return;
            }
            Game g = main.game();
            if (!MainUiLayoutPhase.showCapturesAndNotationHistory(g)) {
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
            main.cancelComputerPlayForHistoryScrub();
            ph.navigateToVisibleLine(idx, true);
            main.gameController.applyPlayHistoryViewToGame();
            main.syncPlayPartialFromHistory();
            main.notifyPlayChessClockHistoryNavigation();
            main.refreshAll();
            main.hostBroadcastSnapshotIfNeeded();
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

        String monoSmall = "-fx-font-family: Consolas; -fx-font-size: 11px;";
        Label clockTitle = new Label("Časovač");
        clockTitle.setStyle("-fx-font-weight: bold;");
        Label hCelkem = new Label("Celkem");
        Label hPrumer = new Label("Průměr na tah");
        hCelkem.setStyle(monoSmall);
        hPrumer.setStyle(monoSmall);
        main.clockGoldTotalLabel.setStyle(monoSmall);
        main.clockGoldAvgLabel.setStyle(monoSmall);
        main.clockSilverTotalLabel.setStyle(monoSmall);
        main.clockSilverAvgLabel.setStyle(monoSmall);
        GridPane clockGrid = new GridPane();
        clockGrid.setHgap(12);
        clockGrid.setVgap(4);
        clockGrid.add(clockTitle, 0, 0);
        clockGrid.add(hCelkem, 1, 0);
        clockGrid.add(hPrumer, 2, 0);
        GridPane.setHalignment(hCelkem, HPos.RIGHT);
        GridPane.setHalignment(hPrumer, HPos.RIGHT);
        clockGrid.add(new Label("Gold"), 0, 1);
        clockGrid.add(main.clockGoldTotalLabel, 1, 1);
        clockGrid.add(main.clockGoldAvgLabel, 2, 1);
        clockGrid.add(new Label("Silver"), 0, 2);
        clockGrid.add(main.clockSilverTotalLabel, 1, 2);
        clockGrid.add(main.clockSilverAvgLabel, 2, 2);
        GridPane.setHalignment(main.clockGoldTotalLabel, HPos.RIGHT);
        GridPane.setHalignment(main.clockGoldAvgLabel, HPos.RIGHT);
        GridPane.setHalignment(main.clockSilverTotalLabel, HPos.RIGHT);
        GridPane.setHalignment(main.clockSilverAvgLabel, HPos.RIGHT);

        main.notationBox.getChildren().addAll(clockGrid, new Label("Historie tahů:"), main.notationHistoryList);
        main.notationBox.setVisible(false);
        main.notationBox.setManaged(false);
    }

    static VBox buildSidePanel(MainController main) {
        main.handLabel.setWrapText(true);
        main.handLabel.setMaxWidth(220);
        main.handLabel.setMinHeight(Region.USE_PREF_SIZE);
        main.handLabel.setMaxHeight(Region.USE_PREF_SIZE);
        main.handPieceGraphic.setPreserveRatio(true);
        main.handPieceGraphic.setSmooth(true);

        main.statusLabel.setWrapText(true);
        main.statusLabel.setMaxWidth(240);
        main.playersAssignmentLabel.setWrapText(true);
        main.playersAssignmentLabel.setMaxWidth(240);
        main.setStatus("Rozestavte Gold; pak Hotovo. Silver totéž.");

        VBox sidePanel = new VBox(10);
        sidePanel.getChildren()
                .addAll(
                        new Label("Stav:"),
                        main.statusLabel,
                        new Label("Hráči:"),
                        main.playersAssignmentLabel,
                        main.handLabel,
                        main.capturesBox,
                        main.notationBox,
                        main.reserveBox,
                        main.cancelHandButton,
                        main.randomButton,
                        main.chessButton,
                        main.doneButton,
                        main.playEndTurnButton,
                        main.playCancelTurnButton);
        Region bottomFill = new Region();
        VBox.setVgrow(bottomFill, Priority.ALWAYS);
        sidePanel.getChildren().add(bottomFill);
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

        Menu menuGameplay = new Menu("Ga_meplay");
        menuGameplay.setMnemonicParsing(true);
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

        Menu menuGoldPlayer = new Menu("G_old hráč");
        menuGoldPlayer.setMnemonicParsing(true);
        ToggleGroup goldPlayerGroup = new ToggleGroup();
        RadioMenuItem goldHuman = new RadioMenuItem("Č_lověk");
        goldHuman.setMnemonicParsing(true);
        goldHuman.setToggleGroup(goldPlayerGroup);
        goldHuman.setUserData(PlayerControllerKind.HUMAN);
        RadioMenuItem goldCpu0 = new RadioMenuItem("Počítač — úroveň _0");
        goldCpu0.setMnemonicParsing(true);
        goldCpu0.setToggleGroup(goldPlayerGroup);
        goldCpu0.setUserData(PlayerControllerKind.COMPUTER_LEVEL_0);
        RadioMenuItem goldCpu1 = new RadioMenuItem("Počítač — úroveň _1");
        goldCpu1.setMnemonicParsing(true);
        goldCpu1.setToggleGroup(goldPlayerGroup);
        goldCpu1.setUserData(PlayerControllerKind.COMPUTER_LEVEL_1);
        RadioMenuItem goldCpu2 = new RadioMenuItem("Počítač — úroveň _2");
        goldCpu2.setMnemonicParsing(true);
        goldCpu2.setToggleGroup(goldPlayerGroup);
        goldCpu2.setUserData(PlayerControllerKind.COMPUTER_LEVEL_2);
        goldHuman.setSelected(true);
        main.goldPlayerMenuGroup = goldPlayerGroup;
        goldPlayerGroup.selectedToggleProperty().addListener((obs, prev, toggled) -> {
            if (main.suppressGameplayPlayerMenuCallback) {
                return;
            }
            if (!(toggled instanceof RadioMenuItem r) || !(r.getUserData() instanceof PlayerControllerKind k)) {
                return;
            }
            main.setGoldPlayerKind(k);
            Platform.runLater(main::refreshAll);
        });
        menuGoldPlayer.getItems().addAll(goldHuman, goldCpu0, goldCpu1, goldCpu2);

        Menu menuSilverPlayer = new Menu("S_ilver hráč");
        menuSilverPlayer.setMnemonicParsing(true);
        ToggleGroup silverPlayerGroup = new ToggleGroup();
        RadioMenuItem silverHuman = new RadioMenuItem("Č_lověk");
        silverHuman.setMnemonicParsing(true);
        silverHuman.setToggleGroup(silverPlayerGroup);
        silverHuman.setUserData(PlayerControllerKind.HUMAN);
        RadioMenuItem silverCpu0 = new RadioMenuItem("Počítač — úroveň _0");
        silverCpu0.setMnemonicParsing(true);
        silverCpu0.setToggleGroup(silverPlayerGroup);
        silverCpu0.setUserData(PlayerControllerKind.COMPUTER_LEVEL_0);
        RadioMenuItem silverCpu1 = new RadioMenuItem("Počítač — úroveň _1");
        silverCpu1.setMnemonicParsing(true);
        silverCpu1.setToggleGroup(silverPlayerGroup);
        silverCpu1.setUserData(PlayerControllerKind.COMPUTER_LEVEL_1);
        RadioMenuItem silverCpu2 = new RadioMenuItem("Počítač — úroveň _2");
        silverCpu2.setMnemonicParsing(true);
        silverCpu2.setToggleGroup(silverPlayerGroup);
        silverCpu2.setUserData(PlayerControllerKind.COMPUTER_LEVEL_2);
        silverHuman.setSelected(true);
        main.silverPlayerMenuGroup = silverPlayerGroup;
        silverPlayerGroup.selectedToggleProperty().addListener((obs, prev, toggled) -> {
            if (main.suppressGameplayPlayerMenuCallback) {
                return;
            }
            if (!(toggled instanceof RadioMenuItem r) || !(r.getUserData() instanceof PlayerControllerKind k)) {
                return;
            }
            main.setSilverPlayerKind(k);
            Platform.runLater(main::refreshAll);
        });
        menuSilverPlayer.getItems().addAll(silverHuman, silverCpu0, silverCpu1, silverCpu2);
        menuGameplay.getItems().addAll(menuGoldPlayer, menuSilverPlayer);
        menuGameplay.getItems().add(new SeparatorMenuItem());

        /* Slider 0–MAX ms; model clamps to MIN_COMPUTER_STEP_DELAY_MS via MainController.setComputerStepDelayMs. */
        Slider computerStepDelaySlider = new Slider(0, MainController.MAX_COMPUTER_STEP_DELAY_MS, 0);
        computerStepDelaySlider.setValue(
                Math.min(
                        MainController.MAX_COMPUTER_STEP_DELAY_MS,
                        Math.max(0, main.getComputerStepDelayMs())));
        computerStepDelaySlider.setFocusTraversable(false);
        computerStepDelaySlider.setShowTickMarks(true);
        computerStepDelaySlider.setMajorTickUnit(500);
        computerStepDelaySlider.setMinorTickCount(0);
        computerStepDelaySlider.setBlockIncrement(50);
        computerStepDelaySlider.setSnapToTicks(false);
        computerStepDelaySlider.setMaxWidth(100);
        Label computerDelayCaption = new Label("Pauza tahu počítače na krok:");
        computerDelayCaption.setFocusTraversable(false);
        Label computerDelayHint = new Label("Jen prodleva animace kroků tahu počítače (úrovně 1–2 mají fixní hloubku hledání).");
        computerDelayHint.setFocusTraversable(false);
        computerDelayHint.setWrapText(true);
        computerDelayHint.setMaxWidth(280);
        computerDelayHint.setStyle("-fx-font-size: 10px;");
        Label computerDelayValueLabel = new Label();
        computerDelayValueLabel.setFocusTraversable(false);
        /* Fixed width for longest "2.00 s"; left-aligned so gap after slider matches delayRowGap. */
        final double delayValueCellWidth = 52;
        computerDelayValueLabel.setMinWidth(delayValueCellWidth);
        computerDelayValueLabel.setPrefWidth(delayValueCellWidth);
        computerDelayValueLabel.setMaxWidth(delayValueCellWidth);
        computerDelayValueLabel.setAlignment(Pos.CENTER_LEFT);
        /* CustomMenuItem content is outside the usual menu-item label subtree; Modena leaves label text
         * effectively invisible until hover. Explicit fill keeps captions readable. */
        Color menuCustomItemText = Color.color(0.13, 0.13, 0.13);
        computerDelayCaption.setTextFill(menuCustomItemText);
        computerDelayHint.setTextFill(menuCustomItemText);
        computerDelayValueLabel.setTextFill(menuCustomItemText);
        computerDelayValueLabel.textProperty()
                .bind(Bindings.createStringBinding(
                        () ->
                                String.format(
                                        Locale.US,
                                        "%.2f s",
                                        Math.max(
                                                        MainController.MIN_COMPUTER_STEP_DELAY_MS,
                                                        computerStepDelaySlider.getValue())
                                                / 1000.0),
                        computerStepDelaySlider.valueProperty()));
        computerStepDelaySlider
                .valueProperty()
                .addListener(
                        (obs, o, n) -> {
                            main.setComputerStepDelayMs(n.doubleValue());
                            double clamped = main.getComputerStepDelayMs();
                            if (Math.abs(computerStepDelaySlider.getValue() - clamped) > 0.5) {
                                computerStepDelaySlider.setValue(clamped);
                            }
                        });
        main.setComputerStepDelayMs(computerStepDelaySlider.getValue());
        computerStepDelaySlider.setValue(main.getComputerStepDelayMs());
        final int delayRowGap = 8;
        HBox computerDelayRow = new HBox(delayRowGap, computerDelayCaption, computerStepDelaySlider, computerDelayValueLabel);
        computerDelayRow.setAlignment(Pos.CENTER_LEFT);
        computerDelayRow.setFocusTraversable(false);
        VBox computerDelayBlock = new VBox(4, computerDelayHint, computerDelayRow);
        computerDelayBlock.setFocusTraversable(false);
        Tooltip.install(
                computerDelayBlock,
                new Tooltip("Nastaví pauzu mezi jednotlivými kroky při animaci tahu počítače (0,1–2,0 s)."));
        CustomMenuItem computerDelayMenuItem = new CustomMenuItem(computerDelayBlock);
        computerDelayMenuItem.setHideOnClick(false);
        computerDelayMenuItem.setMnemonicParsing(false);
        menuGameplay.getItems().add(computerDelayMenuItem);
        menuGameplay.getItems().add(new SeparatorMenuItem());

        main.forbidCancelAfterTrapItem = new CheckMenuItem(
                "Po pádu figury do pasti nelze zrušit rozpracovaný tah");
        main.forbidCancelAfterTrapItem.setSelected(false);
        main.forbidCancelAfterTrapItem.selectedProperty().addListener((obs, prev, now) -> Platform.runLater(main::refreshAll));
        menuGameplay.getItems().add(main.forbidCancelAfterTrapItem);

        main.showComputerTurnStepsInNotationItem =
                new CheckMenuItem("Zobrazit jednotlivé kroky počítače při tahu");
        main.showComputerTurnStepsInNotationItem.setSelected(true);
        main.showComputerTurnStepsInNotationItem
                .selectedProperty()
                .addListener((obs, prev, now) -> Platform.runLater(main::refreshAll));
        menuGameplay.getItems().add(main.showComputerTurnStepsInNotationItem);

        main.rotateBoardToMoverItem = new CheckMenuItem("Otáčet desku — hráč na tahu dole");
        main.rotateBoardToMoverItem.setSelected(false);
        main.rotateBoardToMoverItem.selectedProperty().addListener((obs, prev, now) -> Platform.runLater(main::refreshAll));
        menuGameplay.getItems().add(main.rotateBoardToMoverItem);

        Menu menuSit = new Menu("S_íť");
        menuSit.setMnemonicParsing(true);
        main.networkHostMenuItem = new MenuItem("Hostovat…");
        main.networkHostMenuItem.setOnAction(e -> main.startNetworkHostDialog());
        main.networkConnectMenuItem = new MenuItem("Připojit se…");
        main.networkConnectMenuItem.setOnAction(e -> main.startNetworkClientDialog());
        main.networkDisconnectMenuItem = new MenuItem("Odpojit");
        main.networkDisconnectMenuItem.setDisable(true);
        main.networkDisconnectMenuItem.setOnAction(e -> main.disconnectNetwork());
        menuSit.getItems()
                .addAll(
                        main.networkHostMenuItem,
                        main.networkConnectMenuItem,
                        new SeparatorMenuItem(),
                        main.networkDisconnectMenuItem);

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
        menuBar.getMenus().addAll(menuHra, menuTah, menuGameplay, menuSit, menuLog);
        return menuBar;
    }
}
