package cz.cvut.fel.pjv.arimaa.ui;

import cz.cvut.fel.pjv.arimaa.controller.GameController;
import cz.cvut.fel.pjv.arimaa.logging.LoggingSupport;
import cz.cvut.fel.pjv.arimaa.model.DefaultRuleEngine;
import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.GameHistoryEvent;
import cz.cvut.fel.pjv.arimaa.model.GameMemento;
import cz.cvut.fel.pjv.arimaa.model.GameState;
import cz.cvut.fel.pjv.arimaa.model.Move;
import cz.cvut.fel.pjv.arimaa.model.Piece;
import cz.cvut.fel.pjv.arimaa.model.PieceType;
import cz.cvut.fel.pjv.arimaa.model.PlayerSide;
import cz.cvut.fel.pjv.arimaa.model.Position;
import cz.cvut.fel.pjv.arimaa.model.Step;
import cz.cvut.fel.pjv.arimaa.model.StepKind;
import cz.cvut.fel.pjv.arimaa.model.PlayHalfTurn;
import cz.cvut.fel.pjv.arimaa.model.PlayTurnHistory;
import cz.cvut.fel.pjv.arimaa.persistence.GameRepository;
import cz.cvut.fel.pjv.arimaa.persistence.GameSerializer;
import cz.cvut.fel.pjv.arimaa.util.ArimaaNotation;
import cz.cvut.fel.pjv.arimaa.util.BoardConstants;
import cz.cvut.fel.pjv.arimaa.util.HomeTerritory;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeType;
import javafx.scene.text.Font;
import javafx.scene.transform.Scale;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;

import ch.qos.logback.classic.Level;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Primary window: board, setup controls (manual placement, presets via {@link cz.cvut.fel.pjv.arimaa.model.SetupPresets}),
 * PLAY interaction (draft turns, notation panel, save/load). Scene keyboard: PLAY — Tab / Ctrl+Tab / arrows / WASD / Space / Enter;
 * SETUP — Space / Ctrl+Space / Ctrl+Enter. Undo / redo: menu Tah (Ctrl+Z / Ctrl+Y) and timeline / draft stack.
 * Gameplay → Skin: subfolders of {@code images/figure_sets/} (see {@link FigureSvgRasterCache#discoverSkinDirectoryNames()}).
 */
public class MainController {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    private static final int CELL = 52;
    /** Gap between adjacent columns/rows on the unified board {@link GridPane}. */
    private static final int BOARD_GAP = 1;
    /** Coordinate strip width/height outside the 8×8 cells. */
    private static final int COORD = 24;
    /** Space between outer frame and coordinate grid. */
    private static final int FRAME_INSET = 10;
    private static final Font CELL_FONT = Font.font(18);
    private static final Font COORD_FONT = Font.font(12);
    /** Max width/height for piece {@link ImageView} inside a cell ({@link #CELL} minus margin). */
    private static final double PIECE_IMAGE_MAX = Math.max(16, CELL - 8);
    private static final double RESERVE_ICON_MAX = 26;
    private static final double HAND_ICON_MAX = 28;
    private static final double CAPTURE_ICON_MAX = 36;

    /** Equal inset from {@link BoardHostPane} edges to the scaled board block. */
    private static final double BOARD_VIEW_MARGIN = 14;

    private GameController gameController;

    private Stage stage;
    private File lastUsedDir;

    /** Next preset for „Šachová rozestavení“ rotation ({@link Game#CHESS_SETUP_ROTATION_COUNT} templates). */
    private int chessSetupRotateIndex;
    /** Pixel size of the framed board (coordinates + frame); used for scaling. */
    private double framedOuterSize = 1.0;
    private final Label statusLabel = new Label();
    private final StackPane[][] boardCells = new StackPane[BoardConstants.BOARD_SIZE][BoardConstants.BOARD_SIZE];
    private final Map<PieceType, Button> reserveButtons = new EnumMap<>(PieceType.class);
    private Button cancelHandButton;
    private Button randomButton;
    private Button chessButton;
    private Button doneButton;
    private final Label handLabel = new Label();
    private final ImageView handPieceGraphic = new ImageView();
    private MenuItem undoMenuItem;
    private MenuItem redoMenuItem;

    private ToggleGroup logLevelToggleGroup;
    /** Avoid feedback when programmatically selecting the log-level radio matching {@link LoggingSupport#getCurrentLevel()}. */
    private boolean suppressLogLevelSync;

    private CheckMenuItem logToFileItem;
    /** Avoid firing {@link #logToFileItem} action when syncing from {@link LoggingSupport#isFileLoggingEnabled()}. */
    private boolean suppressFileLogSync;

    private final FigureSvgRasterCache figureRasterCache = new FigureSvgRasterCache();
    /** {@code null} = no piece images (letters only); otherwise subdirectory of {@value FigureSvgRasterCache#FIGURE_SETS_ROOT}. */
    private String figureSkinFolder = FigureSvgRasterCache.FALLBACK_SKIN_NAME;

    /** When selected, draft cancel / in-turn Undo·Redo are disabled after any trap removal in the current prefix. */
    private CheckMenuItem forbidCancelAfterTrapItem;

    /** Home square currently hovered during setup (ghost placement); both null if none. Visual row 0 = top of board. */
    private Integer hoverFileIndex;
    private Integer hoverVisualRow;

    /** Rank digits beside the board (columns 0 and 9); updated when board orientation changes. */
    private final Label[] rankCoordLabelsLeft = new Label[BoardConstants.BOARD_SIZE];
    private final Label[] rankCoordLabelsRight = new Label[BoardConstants.BOARD_SIZE];
    /** File letters above/below the board (rows 0 and 9); updated when board orientation changes. */
    private final Label[] fileCoordLabelsTop = new Label[BoardConstants.BOARD_SIZE];
    private final Label[] fileCoordLabelsBottom = new Label[BoardConstants.BOARD_SIZE];

    /** When selected, during PLAY/GAME_OVER the board orients so the side to move (or winner) is at the bottom edge. */
    private CheckMenuItem rotateBoardToMoverItem;
    /** In {@link GameState#PLAY}: steps not yet committed; origin for the next step. */
    private final Move playPartialMove = new Move();
    private Position playNextFrom;
    /**
     * Steps removed by Zpět at the <em>end</em> of the open draft only; Vpřed reapplies them. Cleared on navigation
     * Zpět/Vpřed within a half or when the draft diverges ({@link #beginPlayDraftMutationBeforeNewSteps()}).
     */
    private final Deque<Step> draftRedoSteps = new ArrayDeque<>();
    /**
     * Snapshot after Esc / „Zrušit rozpracovaný tah“ (whole turn cleared); Vpřed restores that draft once. Unlike Zpět,
     * this is not step-by-step.
     */
    private CancelledDraftSnapshot cancelledDraftOrNull = null;

    private record CancelledDraftSnapshot(
            Move move,
            Position playNextFromOrNull,
            Position playActiveSegmentOriginOrNull) {}

    /**
     * Board square where the currently selected piece started its segment of the draft turn (updates when
     * the player picks another own piece).
     */
    private Position playActiveSegmentOrigin;

    /**
     * During pull-drag (after a slide), Tab cycles opponent squares; Space completes pull for this target
     * (or the first target in visual order if none focused yet).
     */
    private Position playKeyboardPullFocus;

    /**
     * When a push bundle is legal, Tab cycles first-step destinations; Space applies the focused (or first) push.
     * Ctrl+Tab / Ctrl+Shift+Tab still cycle own pieces (see {@link #advancePlayTabFocusOwnPiecesOnly}).
     */
    private Position playKeyboardPushFocus;

    private Button playEndTurnButton;
    private Button playCancelTurnButton;

    /** Setup reserve tray + piece-type buttons; hidden during PLAY. */
    private VBox reserveBox;
    /** Trap captures display; visible in PLAY and GAME_OVER. */
    private final VBox capturesBox = new VBox(6);
    /** Move notation history; visible only after setup (PLAY / GAME_OVER). */
    private final VBox notationBox = new VBox(6);
    private final FlowPane goldCapturesPane = new FlowPane(4, 4);
    private final FlowPane silverCapturesPane = new FlowPane(4, 4);
    private boolean suppressHistoryListEvents;
    private final ObservableList<String> notationHistoryItems = FXCollections.observableArrayList();
    private final ListView<String> notationHistoryList = new ListView<>(notationHistoryItems);

    private boolean pieceSkinUsesFigureArt() {
        return figureSkinFolder != null;
    }

    public GameController getGameController() {
        return gameController;
    }

    public void setGameController(GameController gameController) {
        this.gameController = gameController;
    }

    /**
     * Builds the scene on {@code primaryStage} and shows it.
     */
    public void attachToStage(Stage primaryStage) {
        this.stage = primaryStage;

        for (int row = 0; row < BoardConstants.BOARD_SIZE; row++) {
            for (int col = 0; col < BoardConstants.BOARD_SIZE; col++) {
                StackPane cell = createCell(col, row);
                boardCells[row][col] = cell;
            }
        }

        reserveBox = new VBox(6, new Label("Rezerva (klik = vzít figuru):"));
        reserveBox.setPadding(new Insets(0, 0, 8, 0));
        for (PieceType type : PieceType.values()) {
            Button b = new Button(labelForReserveButton(type, 0));
            b.setMaxWidth(Double.MAX_VALUE);
            b.setOnAction(e -> onPickReserve(type));
            reserveButtons.put(type, b);
            reserveBox.getChildren().add(b);
        }

        cancelHandButton = new Button("Zrušit výběr z ruky");
        cancelHandButton.setMaxWidth(Double.MAX_VALUE);
        cancelHandButton.setOnAction(e -> {
            Game g = game();
            if (g != null) {
                boolean changed = g.getSetupHand() != null;
                g.cancelPendingSetupPlacement();
                if (changed) {
                    recordTimeline();
                }
                refreshAll();
            }
        });

        randomButton = new Button("Náhodně doplnit zbytek");
        randomButton.setMaxWidth(Double.MAX_VALUE);
        randomButton.setOnAction(e -> performRandomSetupPlacementAction());

        chessButton = new Button("Šachová rozestavení");
        chessButton.setMaxWidth(Double.MAX_VALUE);
        chessButton.setOnAction(e -> applyChessMappedSetupFromUi());

        doneButton = new Button("Hotovo (ukončit rozestavení)");
        doneButton.setMaxWidth(Double.MAX_VALUE);
        doneButton.setOnAction(e -> tryCompleteSetupFromUi());

        playEndTurnButton = new Button("Konec tahu");
        playEndTurnButton.setMaxWidth(Double.MAX_VALUE);
        playEndTurnButton.setOnAction(e -> tryEndPlayTurn());

        playCancelTurnButton = new Button("Zrušit rozpracovaný tah");
        playCancelTurnButton.setMaxWidth(Double.MAX_VALUE);
        playCancelTurnButton.setFocusTraversable(false);
        playCancelTurnButton.setOnAction(e -> tryCancelPlayDraftFromUi());

        handLabel.setWrapText(true);
        handLabel.setMaxWidth(220);
        handPieceGraphic.setPreserveRatio(true);
        handPieceGraphic.setSmooth(true);

        goldCapturesPane.setPrefWrapLength(220);
        silverCapturesPane.setPrefWrapLength(220);
        capturesBox.getChildren().addAll(
                new Label("Zajaté (Gold):"),
                goldCapturesPane,
                new Label("Zajaté (Silver):"),
                silverCapturesPane);

        notationHistoryList.setFocusTraversable(false);
        notationHistoryList.setFixedCellSize(22);
        notationHistoryList.setPrefHeight(220);
        notationHistoryList.setMaxHeight(220);
        notationHistoryList.setMinHeight(72);
        notationHistoryList.setStyle("-fx-font-family: Consolas; -fx-font-size: 11px;");
        notationHistoryList.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        notationHistoryList.getSelectionModel().selectedIndexProperty().addListener((obs, o, n) -> {
            if (suppressHistoryListEvents || n == null || n.intValue() < 0 || gameController == null) {
                return;
            }
            Game g = game();
            if (g == null || g.getState() != GameState.PLAY) {
                return;
            }
            PlayTurnHistory ph = gameController.getPlayHistory();
            if (!ph.isBootstrapped()) {
                return;
            }
            List<Integer> vis = ph.visibleHalfIndicesForDisplay(true);
            int idx = n.intValue();
            if (idx >= vis.size()) {
                return;
            }
            ph.navigateToVisibleLine(idx, true);
            gameController.applyPlayHistoryViewToGame();
            syncPlayPartialFromHistory();
            refreshAll();
        });

        notationBox.getChildren().addAll(new Label("Historie tahů:"), notationHistoryList);
        notationBox.setVisible(false);
        notationBox.setManaged(false);

        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(240);
        setStatus("Rozestavte Gold; pak Hotovo. Silver totéž.");

        VBox sidePanel = new VBox(10,
                new Label("Stav:"),
                statusLabel,
                handLabel,
                spacer(8),
                capturesBox,
                notationBox,
                reserveBox,
                cancelHandButton,
                randomButton,
                chessButton,
                doneButton,
                playEndTurnButton,
                playCancelTurnButton);
        sidePanel.setPadding(new Insets(12));
        sidePanel.setPrefWidth(260);
        sidePanel.setMaxHeight(Double.MAX_VALUE);
        sidePanel.setBackground(new Background(new BackgroundFill(
                Color.rgb(250, 250, 252), CornerRadii.EMPTY, Insets.EMPTY)));

        ScrollPane scroll = new ScrollPane(sidePanel);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setMinViewportWidth(240);
        scroll.setBackground(Background.EMPTY);
        scroll.setStyle("-fx-background-color: transparent;");

        StackPane framedBoard = buildFramedBoardWithPerimeterCoordinates();
        BoardHostPane boardHost = new BoardHostPane(framedBoard, framedOuterSize);
        boardHost.setMinWidth(0);
        boardHost.setMinHeight(0);
        boardHost.setMaxWidth(Double.MAX_VALUE);
        boardHost.setMaxHeight(Double.MAX_VALUE);
        HBox.setHgrow(boardHost, Priority.ALWAYS);

        HBox body = new HBox();
        body.setFillHeight(true);
        body.setAlignment(Pos.CENTER);
        body.getChildren().addAll(boardHost, scroll);

        Menu menuHra = new Menu("Hra");
        MenuItem novaHraItem = new MenuItem("Nová hra");
        novaHraItem.setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN));
        novaHraItem.setOnAction(e -> startNewGameAction());
        MenuItem ulozitHruItem = new MenuItem("Uložit hru…");
        ulozitHruItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN));
        ulozitHruItem.setOnAction(e -> saveGameToFileAction());
        MenuItem nacistHruItem = new MenuItem("Načíst hru…");
        nacistHruItem.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN));
        nacistHruItem.setOnAction(e -> loadGameFromFileAction());
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
        undoMenuItem = new MenuItem("Zpět");
        undoMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN));
        undoMenuItem.setOnAction(e -> performUndo());
        redoMenuItem = new MenuItem("Vpřed");
        redoMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN));
        redoMenuItem.setOnAction(e -> performRedo());
        menuTah.getItems().addAll(undoMenuItem, redoMenuItem);

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
        figureRasterCache.setSkinDirectory(initialSkin);
        figureSkinFolder = initialSkin;
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
                figureSkinFolder = null;
            } else if (ud instanceof String dir) {
                figureSkinFolder = dir;
                figureRasterCache.setSkinDirectory(dir);
            }
            refreshAll();
        });
        List<MenuItem> skinMenuItems = new ArrayList<>();
        skinMenuItems.add(skinNoneItem);
        skinMenuItems.add(new SeparatorMenuItem());
        skinMenuItems.addAll(skinDirItems);
        menuSkin.getItems().addAll(skinMenuItems);
        menuGameplay.getItems().add(menuSkin);
        menuGameplay.getItems().add(new SeparatorMenuItem());
        forbidCancelAfterTrapItem = new CheckMenuItem(
                "Po pádu figury do pasti nelze zrušit rozpracovaný tah");
        forbidCancelAfterTrapItem.setSelected(false);
        forbidCancelAfterTrapItem.selectedProperty().addListener((obs, prev, now) -> refreshAll());
        menuGameplay.getItems().add(forbidCancelAfterTrapItem);

        rotateBoardToMoverItem = new CheckMenuItem("Otáčet desku — hráč na tahu dole");
        rotateBoardToMoverItem.setSelected(false);
        rotateBoardToMoverItem.selectedProperty().addListener((obs, prev, now) -> refreshAll());
        menuGameplay.getItems().add(rotateBoardToMoverItem);

        Menu menuLog = new Menu("Log");
        Menu menuLogLevel = new Menu("Logback Level");
        logLevelToggleGroup = new ToggleGroup();
        for (Level lvl : List.of(Level.OFF, Level.ERROR, Level.WARN, Level.INFO, Level.DEBUG, Level.TRACE)) {
            RadioMenuItem item = new RadioMenuItem(lvl.toString());
            item.setToggleGroup(logLevelToggleGroup);
            item.setUserData(lvl);
            menuLogLevel.getItems().add(item);
        }
        logLevelToggleGroup.selectedToggleProperty().addListener((obs, prev, toggled) -> {
            if (suppressLogLevelSync || toggled == null) {
                return;
            }
            if (toggled instanceof RadioMenuItem r && r.getUserData() instanceof Level selected) {
                LoggingSupport.setLevel(selected);
            }
        });
        menuLog.getItems().add(menuLogLevel);

        logToFileItem = new CheckMenuItem("Zapisovat do souboru");
        logToFileItem.setOnAction(e -> {
            if (suppressFileLogSync) {
                return;
            }
            if (logToFileItem.isSelected()) {
                if (!LoggingSupport.enableFileLogging(LoggingSupport.defaultLogFilePath())) {
                    suppressFileLogSync = true;
                    try {
                        logToFileItem.setSelected(false);
                    } finally {
                        suppressFileLogSync = false;
                    }
                    setStatus("Log do souboru: zapnutí se nepodařilo (viz konzole).");
                } else {
                    setStatus("Log do souboru zapnut.");
                }
            } else {
                LoggingSupport.disableFileLogging();
                setStatus("Log do souboru vypnut.");
            }
        });
        menuLog.getItems().add(logToFileItem);
        menuLog.setOnShowing(e -> {
            syncLogLevelMenuSelection();
            syncLogToFileMenuSelection();
        });

        MenuBar menuBar = new MenuBar();
        menuBar.getMenus().addAll(menuHra, menuTah, menuGameplay, menuLog);

        BorderPane root = new BorderPane();
        root.setTop(menuBar);
        root.setCenter(body);

        Scene scene = new Scene(root, 920, 640);
        scene.setFill(Color.rgb(236, 236, 238));
        /**
         * Capture phase: Esc (cancel draft), arrows / WASD (when a piece is selected), Tab / Shift+Tab (pull targets, else push
         * targets, else own pieces), Ctrl+Tab / Ctrl+Shift+Tab (always own pieces; e.g. during push), Space (complete pull), Enter / Ctrl+Enter (end turn); SETUP: Space / Ctrl+Space / Ctrl+Enter.
         * Runs before {@link ScrollPane} consumes arrow keys.
         */
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getTarget() instanceof TextInputControl t && t.isEditable()) {
                return;
            }
            Game g = game();
            if (g != null && (g.getState() == GameState.SETUP_GOLD || g.getState() == GameState.SETUP_SILVER)) {
                if (e.getCode() == KeyCode.SPACE && e.isControlDown() && !e.isAltDown()) {
                    if (chessButton != null && !chessButton.isDisabled()) {
                        applyChessMappedSetupFromUi();
                        e.consume();
                    }
                    return;
                }
                if (e.getCode() == KeyCode.SPACE && !e.isControlDown() && !e.isAltDown()) {
                    if (randomButton != null && !randomButton.isDisabled()) {
                        performRandomSetupPlacementAction();
                        e.consume();
                    }
                    return;
                }
                if (e.getCode() == KeyCode.ENTER && e.isControlDown() && !e.isAltDown()) {
                    if (doneButton != null && !doneButton.isDisabled()) {
                        tryCompleteSetupFromUi();
                        e.consume();
                    }
                    return;
                }
                return;
            }
            if (g == null || g.getState() != GameState.PLAY) {
                return;
            }
            if (e.getCode() == KeyCode.ESCAPE) {
                if (tryCancelPlayDraftFromUi()) {
                    e.consume();
                }
                return;
            }
            if (e.getCode() == KeyCode.ENTER) {
                tryEndPlayTurn();
                e.consume();
                return;
            }
            if (e.getCode() == KeyCode.TAB) {
                if (e.isControlDown() && !e.isAltDown()) {
                    advancePlayTabFocusOwnPiecesOnly(g, e.isShiftDown());
                    e.consume();
                } else if (!e.isControlDown()) {
                    advancePlayTabFocus(g, e.isShiftDown());
                    e.consume();
                }
                return;
            }
            if (e.getCode() == KeyCode.SPACE && !e.isControlDown() && !e.isAltDown()) {
                if (!computePullDragTargets(g).isEmpty()) {
                    activatePlayPullFromKeyboard(g);
                    e.consume();
                    return;
                }
                if (!computePushBundleFirstStepTargets(g).isEmpty()) {
                    activatePlayPushFromKeyboard(g);
                    e.consume();
                }
                return;
            }
            if (e.isShortcutDown()) {
                return;
            }
            int[] dVis = visualDeltaForPlayNavigation(e.getCode());
            if (dVis == null || playNextFrom == null) {
                return;
            }
            Position target = modelNeighborFromVisualDelta(g, playNextFrom, dVis[0], dVis[1]);
            handlePlayBoardActivation(target.getFileIndex(), target.getRankIndex());
            e.consume();
        });
        primaryStage.setTitle("Arimaa – rozestavení");
        primaryStage.setScene(scene);
        primaryStage.show();
        Platform.runLater(() -> scene.getRoot().requestFocus());

        if (gameController != null) {
            gameController.resetTimeline();
        }
        refreshAll();
        syncLogLevelMenuSelection();
        syncLogToFileMenuSelection();
    }

    /**
     * Side length of the 8×8 playing area including gaps between the eight cells in a row/column.
     */
    private static int boardBlockPixels() {
        return BoardConstants.BOARD_SIZE * CELL + (BoardConstants.BOARD_SIZE - 1) * BOARD_GAP;
    }

    /**
     * Side length of the full 10×10 perimeter (coordinates + board): two rank strips, eight files, and
     * {@code 9} horizontal (and vertical) {@link #BOARD_GAP}s — must match {@link GridPane} layout math.
     */
    private static int perimeterSpanPixels() {
        return 2 * COORD + boardBlockPixels() + 2 * BOARD_GAP;
    }

    /**
     * Single {@link GridPane} for coordinates and cells so gaps are not counted twice; light outline only.
     */
    private StackPane buildFramedBoardWithPerimeterCoordinates() {
        int inner = perimeterSpanPixels();
        int outer = inner + 2 * FRAME_INSET;

        GridPane surface = new GridPane();
        surface.setHgap(BOARD_GAP);
        surface.setVgap(BOARD_GAP);
        for (int i = 0; i < 10; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            if (i == 0 || i == 9) {
                cc.setPrefWidth(COORD);
                cc.setMinWidth(COORD);
                cc.setMaxWidth(COORD);
            } else {
                cc.setPrefWidth(CELL);
                cc.setMinWidth(CELL);
                cc.setMaxWidth(CELL);
            }
            cc.setHgrow(Priority.NEVER);
            surface.getColumnConstraints().add(cc);
        }
        for (int i = 0; i < 10; i++) {
            RowConstraints rc = new RowConstraints();
            if (i == 0 || i == 9) {
                rc.setPrefHeight(COORD);
                rc.setMinHeight(COORD);
                rc.setMaxHeight(COORD);
            } else {
                rc.setPrefHeight(CELL);
                rc.setMinHeight(CELL);
                rc.setMaxHeight(CELL);
            }
            rc.setVgrow(Priority.NEVER);
            surface.getRowConstraints().add(rc);
        }

        for (int c : new int[] {0, 9}) {
            for (int r : new int[] {0, 9}) {
                surface.add(cornerSpacer(), c, r);
            }
        }
        for (int f = 0; f < BoardConstants.BOARD_SIZE; f++) {
            Label top = coordLabel(String.valueOf((char) ('a' + f)), CELL, COORD, true);
            Label bottom = coordLabel(String.valueOf((char) ('a' + f)), CELL, COORD, true);
            fileCoordLabelsTop[f] = top;
            fileCoordLabelsBottom[f] = bottom;
            surface.add(top, f + 1, 0);
            surface.add(bottom, f + 1, 9);
        }
        for (int visualRow = 0; visualRow < BoardConstants.BOARD_SIZE; visualRow++) {
            int gridRow = visualRow + 1;
            Label left = coordLabel("8", COORD, CELL, false);
            Label right = coordLabel("8", COORD, CELL, false);
            rankCoordLabelsLeft[visualRow] = left;
            rankCoordLabelsRight[visualRow] = right;
            surface.add(left, 0, gridRow);
            surface.add(right, 9, gridRow);
        }
        for (int row = 0; row < BoardConstants.BOARD_SIZE; row++) {
            for (int col = 0; col < BoardConstants.BOARD_SIZE; col++) {
                surface.add(boardCells[row][col], col + 1, row + 1);
            }
        }

        StackPane framed = new StackPane();
        Rectangle frame = new Rectangle(outer, outer);
        frame.setFill(Color.TRANSPARENT);
        frame.setStroke(Color.rgb(160, 160, 168));
        frame.setStrokeWidth(1);
        frame.setArcWidth(6);
        frame.setArcHeight(6);
        framed.getChildren().addAll(frame, surface);
        StackPane.setAlignment(surface, Pos.CENTER);
        framed.setMinSize(outer, outer);
        framed.setPrefSize(outer, outer);
        framed.setMaxSize(outer, outer);
        StackPane.setMargin(surface, new Insets(FRAME_INSET));
        framedOuterSize = outer;
        return framed;
    }

    private static Region cornerSpacer() {
        Region r = new Region();
        r.setPrefSize(COORD, COORD);
        r.setMinSize(COORD, COORD);
        return r;
    }

    private static Label coordLabel(String text, double prefW, double prefH, boolean fileRow) {
        Label lab = new Label(text);
        lab.setFont(COORD_FONT);
        lab.setPrefSize(prefW, prefH);
        lab.setMinSize(prefW, prefH);
        lab.setMaxSize(prefW, prefH);
        lab.setAlignment(Pos.CENTER);
        if (fileRow) {
            lab.setMaxWidth(prefW);
        } else {
            lab.setMaxHeight(prefH);
        }
        return lab;
    }

    private StackPane createCell(int fileIndex, int visualRow) {
        Rectangle bg = new Rectangle(CELL, CELL);
        bg.setStrokeType(StrokeType.INSIDE);
        int rankIndex = BoardConstants.BOARD_SIZE - 1 - visualRow;
        Position pos = Position.of(fileIndex, rankIndex);
        Color baseFill;
        if (HomeTerritory.contains(PlayerSide.GOLD, pos, false)
                || HomeTerritory.contains(PlayerSide.SILVER, pos, false)) {
            baseFill = Color.color(0.75, 0.82, 0.95);
        } else {
            baseFill = visualRow % 2 == fileIndex % 2 ? Color.color(0.93, 0.88, 0.78) : Color.color(0.85, 0.78, 0.65);
        }
        Color baseStroke;
        double baseStrokeWidth;
        if (isStaticTrapSquare(pos)) {
            baseStroke = Color.DARKRED;
            baseStrokeWidth = 2;
        } else {
            baseStroke = Color.gray(0.35);
            baseStrokeWidth = 1;
        }
        bg.setFill(baseFill);
        bg.setStroke(baseStroke);
        bg.setStrokeWidth(baseStrokeWidth);

        ImageView pieceImg = new ImageView();
        pieceImg.setFitWidth(PIECE_IMAGE_MAX);
        pieceImg.setFitHeight(PIECE_IMAGE_MAX);
        pieceImg.setPreserveRatio(true);
        pieceImg.setSmooth(true);
        pieceImg.setVisible(false);

        Label pieceLbl = new Label("");
        pieceLbl.setFont(CELL_FONT);

        ImageView hoverImg = new ImageView();
        hoverImg.setFitWidth(PIECE_IMAGE_MAX);
        hoverImg.setFitHeight(PIECE_IMAGE_MAX);
        hoverImg.setPreserveRatio(true);
        hoverImg.setSmooth(true);
        hoverImg.setMouseTransparent(true);
        hoverImg.setVisible(false);

        Label hoverLbl = new Label("");
        hoverLbl.setFont(CELL_FONT);
        hoverLbl.setMouseTransparent(true);
        hoverLbl.setVisible(false);
        hoverLbl.setOpacity(0.5);

        StackPane cell = new StackPane(bg, pieceLbl, pieceImg, hoverImg, hoverLbl);
        cell.setUserData(new CellData(fileIndex, visualRow, bg, baseFill, baseStroke, baseStrokeWidth,
                pieceLbl, pieceImg, hoverImg, hoverLbl));

        final int fi = fileIndex;
        final int vr = visualRow;
        cell.hoverProperty().addListener((obs, was, hovering) -> {
            if (Boolean.TRUE.equals(hovering)) {
                setHoverCell(fi, vr);
            } else {
                clearHoverCellIf(fi, vr);
            }
        });

        cell.setOnMouseClicked(e -> onBoardCellClick(fi, vr));
        return cell;
    }

    private void setHoverCell(int fileIndex, int visualRow) {
        hoverFileIndex = fileIndex;
        hoverVisualRow = visualRow;
        paintHoverOverlay(game());
    }

    private void clearHoverCellIf(int fileIndex, int visualRow) {
        if (hoverFileIndex != null && hoverFileIndex == fileIndex
                && hoverVisualRow != null && hoverVisualRow == visualRow) {
            hoverFileIndex = null;
            hoverVisualRow = null;
            paintHoverOverlay(game());
        }
    }

    /**
     * Half-transparent ghost of the piece in hand on the hovered home square (setup only).
     */
    private void paintHoverOverlay(Game g) {
        for (int row = 0; row < BoardConstants.BOARD_SIZE; row++) {
            for (int col = 0; col < BoardConstants.BOARD_SIZE; col++) {
                StackPane cell = boardCells[row][col];
                CellData data = (CellData) cell.getUserData();
                ImageView hImg = data.hoverImage();
                Label hLbl = data.hoverLabel();
                hImg.setOpacity(0.5);
                hLbl.setOpacity(0.5);
                if (g == null || g.getBoard() == null) {
                    hImg.setVisible(false);
                    hLbl.setVisible(false);
                    continue;
                }
                GameState st = g.getState();
                boolean setup = st == GameState.SETUP_GOLD || st == GameState.SETUP_SILVER;
                Piece hand = g.getSetupHand();
                int mf = modelFileFromVisualCol(data.fileIndex(), g);
                int mr = modelRankFromVisualRow(data.visualRow(), g);
                Position pos = Position.of(mf, mr);
                boolean overHere = setup && hand != null
                        && hoverFileIndex != null && hoverVisualRow != null
                        && data.fileIndex() == hoverFileIndex && data.visualRow() == hoverVisualRow;
                boolean show = overHere && g.isLegalSetupHandPlacementTarget(pos);
                if (!show) {
                    hImg.setVisible(false);
                    hLbl.setVisible(false);
                    continue;
                }
                if (pieceSkinUsesFigureArt()) {
                    Image im = figureRasterCache.getRasterized(hand.getSide(), hand.getType(), PIECE_IMAGE_MAX);
                    hImg.setImage(im);
                    hImg.setOpacity(0.5);
                    hImg.setVisible(im != null);
                    hLbl.setText(im == null ? abbrev(hand) : "");
                    hLbl.setTextFill(hand.getSide() == PlayerSide.GOLD
                            ? Color.color(0.55, 0.35, 0.05)
                            : Color.color(0.25, 0.25, 0.35));
                    hLbl.setVisible(im == null);
                } else {
                    hImg.setVisible(false);
                    hLbl.setText(abbrev(hand));
                    hLbl.setTextFill(hand.getSide() == PlayerSide.GOLD
                            ? Color.color(0.55, 0.35, 0.05)
                            : Color.color(0.25, 0.25, 0.35));
                    hLbl.setVisible(true);
                }
            }
        }
    }

    private void onBoardCellClick(int fileIndex, int visualRow) {
        Game g = game();
        if (g == null || g.getBoard() == null) {
            return;
        }
        int modelFile = modelFileFromVisualCol(fileIndex, g);
        int modelRank = modelRankFromVisualRow(visualRow, g);
        GameState st = g.getState();
        if (st == GameState.PLAY) {
            handlePlayBoardActivation(modelFile, modelRank);
            return;
        }
        if (st != GameState.SETUP_GOLD && st != GameState.SETUP_SILVER) {
            return;
        }
        PlayerSide side = g.getSideToMove();
        Position pos = Position.of(modelFile, modelRank);
        Piece hand = g.getSetupHand();

        if (hand != null) {
            if (g.confirmSetupHandPlacement(pos)) {
                setStatus("Figura umístěna.");
                recordTimeline();
            } else {
                setStatus("Sem nelze umístit (domov, kapacita typu nebo obsazené pole).");
            }
        } else {
            if (g.returnPieceFromBoardToReserve(side, pos)) {
                setStatus("Figura vrácena do rezervy.");
                recordTimeline();
            } else {
                setStatus("Vyberte figuru z rezervy nebo klikněte na svou figuru na domovském poli.");
            }
        }
        refreshAll();
    }

    private void onPickReserve(PieceType type) {
        Game g = game();
        if (g == null) {
            return;
        }
        GameState st = g.getState();
        if (st != GameState.SETUP_GOLD && st != GameState.SETUP_SILVER) {
            return;
        }
        PlayerSide side = g.getSideToMove();
        if (g.beginPlacingPieceFromReserve(side, type)) {
            setStatus("Máte figuru v ruce — klikněte na volné domovské pole.");
            recordTimeline();
        } else {
            setStatus("Tento typ v rezervě není nebo nejste ve fázi rozestavení.");
        }
        refreshAll();
    }

    private void refreshAll() {
        Game g = game();
        if (g != null && g.getState() != GameState.PLAY) {
            clearPlayTurnUi();
        }
        if (g == null || stage == null) {
            notationHistoryItems.clear();
            refreshNotationPanelVisibility(null);
            return;
        }
        updateRankCoordLabels(g);
        updateFileCoordLabels(g);
        paintBoard(g);
        paintPlayHighlights(g);
        refreshReserveButtons(g);
        refreshActionButtons(g);
        refreshSetupSectionVisibility(g);
        refreshCapturedPanel(g);
        refreshNotationPanelVisibility(g);
        refreshNotationHistory();
        refreshHandLabel(g);
        updateWindowTitle(g);
        refreshHistoryMenus();
        paintHoverOverlay(g);
    }

    private void syncLogLevelMenuSelection() {
        if (logLevelToggleGroup == null) {
            return;
        }
        Level current = LoggingSupport.getCurrentLevel();
        suppressLogLevelSync = true;
        try {
            for (var t : logLevelToggleGroup.getToggles()) {
                if (t instanceof RadioMenuItem r && r.getUserData() instanceof Level l) {
                    if (l.toInt() == current.toInt()) {
                        logLevelToggleGroup.selectToggle(r);
                        return;
                    }
                }
            }
        } finally {
            suppressLogLevelSync = false;
        }
    }

    private void syncLogToFileMenuSelection() {
        if (logToFileItem == null) {
            return;
        }
        suppressFileLogSync = true;
        try {
            logToFileItem.setSelected(LoggingSupport.isFileLoggingEnabled());
        } finally {
            suppressFileLogSync = false;
        }
    }

    /**
     * When the gameplay option is on: PLAY shows mover's side at the bottom with a full 180° view (ranks and
     * files mirrored relative to the Gold-at-bottom layout); GAME_OVER shows winner at the bottom the same way.
     * Setup always uses Gold at the bottom (canonical coordinates).
     */
    private boolean boardGoldVisualBottom(Game g) {
        if (rotateBoardToMoverItem == null || !rotateBoardToMoverItem.isSelected()) {
            return true;
        }
        if (g == null) {
            return true;
        }
        GameState st = g.getState();
        if (st == GameState.SETUP_GOLD || st == GameState.SETUP_SILVER) {
            return true;
        }
        if (st == GameState.GAME_OVER) {
            PlayerSide w = g.getMatchWinner();
            if (w != null) {
                return w == PlayerSide.GOLD;
            }
            return true;
        }
        if (st == GameState.PLAY) {
            return g.getSideToMove() == PlayerSide.GOLD;
        }
        return true;
    }

    /** Maps board row index from top ({@code 0}) to model rank; Gold-at-bottom flips vertically; mover-at-bottom uses identity (180° total with files). */
    private int modelRankFromVisualRow(int visualRow, Game g) {
        if (boardGoldVisualBottom(g)) {
            return BoardConstants.BOARD_SIZE - 1 - visualRow;
        }
        return visualRow;
    }

    private int visualRowFromModelRank(int modelRank, Game g) {
        if (boardGoldVisualBottom(g)) {
            return BoardConstants.BOARD_SIZE - 1 - modelRank;
        }
        return modelRank;
    }

    /** Maps grid column from left ({@code 0}) to model file index ({@code a} = {@code 0}). */
    private int modelFileFromVisualCol(int visualCol, Game g) {
        if (boardGoldVisualBottom(g)) {
            return visualCol;
        }
        return BoardConstants.BOARD_SIZE - 1 - visualCol;
    }

    private int visualColFromModelFile(int modelFile, Game g) {
        if (boardGoldVisualBottom(g)) {
            return modelFile;
        }
        return BoardConstants.BOARD_SIZE - 1 - modelFile;
    }

    /** {@code [dVisualCol, dVisualRow]} for arrow keys / WASD; visual row 0 = top of the grid. */
    private static int[] visualDeltaForPlayNavigation(KeyCode code) {
        return switch (code) {
            case UP, W, KP_UP -> new int[] {0, -1};
            case DOWN, S, KP_DOWN -> new int[] {0, 1};
            case LEFT, A, KP_LEFT -> new int[] {-1, 0};
            case RIGHT, D, KP_RIGHT -> new int[] {1, 0};
            default -> null;
        };
    }

    private Position modelNeighborFromVisualDelta(Game g, Position from, int dVisualCol, int dVisualRow) {
        int vc = visualColFromModelFile(from.getFileIndex(), g);
        int vr = visualRowFromModelRank(from.getRankIndex(), g);
        int nvc = Math.min(BoardConstants.BOARD_SIZE - 1, Math.max(0, vc + dVisualCol));
        int nvr = Math.min(BoardConstants.BOARD_SIZE - 1, Math.max(0, vr + dVisualRow));
        return Position.of(modelFileFromVisualCol(nvc, g), modelRankFromVisualRow(nvr, g));
    }

    private void updateFileCoordLabels(Game g) {
        if (fileCoordLabelsTop[0] == null) {
            return;
        }
        for (int vc = 0; vc < BoardConstants.BOARD_SIZE; vc++) {
            int mf = modelFileFromVisualCol(vc, g);
            String letter = String.valueOf((char) ('a' + mf));
            fileCoordLabelsTop[vc].setText(letter);
            fileCoordLabelsBottom[vc].setText(letter);
        }
    }

    private void updateRankCoordLabels(Game g) {
        if (rankCoordLabelsLeft[0] == null) {
            return;
        }
        for (int vr = 0; vr < BoardConstants.BOARD_SIZE; vr++) {
            int mr = modelRankFromVisualRow(vr, g);
            String t = Integer.toString(mr + 1);
            rankCoordLabelsLeft[vr].setText(t);
            rankCoordLabelsRight[vr].setText(t);
        }
    }

    private void refreshAllSquareDecorations(Game g) {
        for (int visualRow = 0; visualRow < BoardConstants.BOARD_SIZE; visualRow++) {
            for (int col = 0; col < BoardConstants.BOARD_SIZE; col++) {
                StackPane cell = boardCells[visualRow][col];
                CellData data = (CellData) cell.getUserData();
                int mf = modelFileFromVisualCol(col, g);
                int mr = modelRankFromVisualRow(visualRow, g);
                Position pos = Position.of(mf, mr);
                Rectangle bg = data.background();
                Color baseFill;
                if (HomeTerritory.contains(PlayerSide.GOLD, pos, false)
                        || HomeTerritory.contains(PlayerSide.SILVER, pos, false)) {
                    baseFill = Color.color(0.75, 0.82, 0.95);
                } else {
                    baseFill = (mf + mr) % 2 == 0
                            ? Color.color(0.93, 0.88, 0.78)
                            : Color.color(0.85, 0.78, 0.65);
                }
                boolean trap = isStaticTrapSquare(pos);
                bg.setFill(baseFill);
                bg.setStroke(trap ? Color.DARKRED : Color.gray(0.35));
                bg.setStrokeWidth(trap ? 2 : 1);
                bg.getStrokeDashArray().clear();
                bg.setStrokeType(StrokeType.INSIDE);
            }
        }
    }

    private static Color cellBaseFillForHighlight(CellData d) {
        Paint p = d.background().getFill();
        return p instanceof Color c ? c : d.baseFill();
    }

    /**
     * Resets each cell’s background to its base style, then in {@link GameState#PLAY} highlights the
     * selected origin square and legal step targets: green tint for ordinary moves, peach/orange for
     * push-bundle first-step cells.
     */
    private void paintPlayHighlights(Game g) {
        refreshAllSquareDecorations(g);
        if (g == null || g.getState() != GameState.PLAY || playNextFrom == null) {
            return;
        }
        Set<Position> pullTargets = computePullDragTargets(g);
        Set<Position> pushTargets = computePushBundleFirstStepTargets(g);
        if (playKeyboardPullFocus != null && (pullTargets.isEmpty() || !pullTargets.contains(playKeyboardPullFocus))) {
            playKeyboardPullFocus = null;
        }
        if (playKeyboardPushFocus != null && (pushTargets.isEmpty() || !pushTargets.contains(playKeyboardPushFocus))) {
            playKeyboardPushFocus = null;
        }
        PlayerSide mover = g.getSideToMove();
        Position origin = playActiveSegmentOrigin;
        if (origin != null && !origin.equals(playNextFrom)) {
            CellData originData = cellDataAt(origin);
            Rectangle obg = originData.background();
            Color fill;
            Color stroke;
            if (mover == PlayerSide.GOLD) {
                fill = cellBaseFillForHighlight(originData).interpolate(Color.web("#fff4d6"), 0.55);
                stroke = Color.web("#d9b24a");
            } else {
                fill = cellBaseFillForHighlight(originData).interpolate(Color.web("#e8eef2"), 0.5);
                stroke = Color.web("#8b97a3");
            }
            obg.setFill(fill);
            obg.setStroke(stroke);
            obg.setStrokeWidth(2.5);
            obg.getStrokeDashArray().clear();
            obg.setStrokeType(StrokeType.INSIDE);
        }
        if (!playPartialMove.getSteps().isEmpty()) {
            Step lastStep = playPartialMove.getSteps().get(playPartialMove.getSteps().size() - 1);
            Position lastFrom = lastStep.getFrom();
            CellData fromData = cellDataAt(lastFrom);
            Rectangle fromBg = fromData.background();
            Paint fp = fromBg.getFill();
            Color baseTint = fp instanceof Color fc ? fc : cellBaseFillForHighlight(fromData);
            Color salad = Color.web("#dff3dc");
            fromBg.setFill(baseTint.interpolate(salad, 0.38));
            fromBg.setStrokeType(StrokeType.INSIDE);
        }
        CellData selected = cellDataAt(playNextFrom);
        Rectangle selBg = selected.background();
        selBg.setFill(cellBaseFillForHighlight(selected).interpolate(Color.web("#ffec99"), 0.42));
        selBg.setStroke(Color.web("#b8860b"));
        selBg.setStrokeWidth(3);
        selBg.setStrokeType(StrokeType.INSIDE);
        if (playPartialMove.getSteps().size() >= 4) {
            return;
        }
        for (Position to : computeLegalPlayTargetsForSelection(g)) {
            CellData tdata = cellDataAt(to);
            Rectangle tbg = tdata.background();
            boolean isPush = pushTargets.contains(to);
            Color tint = isPush ? Color.web("#ffd4a8") : Color.web("#a8f0c0");
            Color stroke = isPush ? Color.web("#c45c19") : Color.web("#1e7a3a");
            tbg.setFill(cellBaseFillForHighlight(tdata).interpolate(tint, isPush ? 0.5 : 0.48));
            tbg.setStroke(stroke);
            tbg.setStrokeWidth(2.5);
            tbg.setStrokeType(StrokeType.INSIDE);
        }
        for (Position opp : pullTargets) {
            CellData odata = cellDataAt(opp);
            Rectangle obg = odata.background();
            obg.setStroke(Color.web("#b030c0"));
            obg.setStrokeWidth(3);
            obg.setStrokeType(StrokeType.INSIDE);
        }
        if (playKeyboardPullFocus != null && pullTargets.contains(playKeyboardPullFocus)) {
            CellData kdata = cellDataAt(playKeyboardPullFocus);
            Rectangle kbg = kdata.background();
            kbg.setStroke(Color.web("#ffcc33"));
            kbg.setStrokeWidth(4);
            kbg.getStrokeDashArray().clear();
            kbg.setStrokeType(StrokeType.INSIDE);
        }
        if (playKeyboardPushFocus != null && pushTargets.contains(playKeyboardPushFocus)) {
            CellData pdata = cellDataAt(playKeyboardPushFocus);
            Rectangle pbg = pdata.background();
            pbg.setStroke(Color.web("#ffcc33"));
            pbg.setStrokeWidth(4);
            pbg.getStrokeDashArray().clear();
            pbg.setStrokeType(StrokeType.INSIDE);
        }
    }

    private CellData cellDataAt(Position pos) {
        Game g = game();
        int visualRow = visualRowFromModelRank(pos.getRankIndex(), g);
        int visualCol = visualColFromModelFile(pos.getFileIndex(), g);
        return (CellData) boardCells[visualRow][visualCol].getUserData();
    }

    private Set<Position> computeLegalPlayTargetsForSelection(Game g) {
        reconcilePlayPartialWithHistoryView();
        Set<Position> out = new HashSet<>();
        if (playNextFrom == null) {
            return out;
        }
        int remaining = 4 - playPartialMove.getSteps().size();
        if (remaining < 1) {
            return out;
        }
        Map<Position, Piece> occ;
        try {
            occ = DefaultRuleEngine.simulatePlayPrefix(g, new Move());
        } catch (IllegalArgumentException ex) {
            return out;
        }
        PlayerSide side = g.getSideToMove();
        if (remaining >= 1) {
            int f = playNextFrom.getFileIndex();
            int r = playNextFrom.getRankIndex();
            int[][] deltas = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : deltas) {
                int nf = f + d[0];
                int nr = r + d[1];
                if (nf < 0 || nf >= BoardConstants.BOARD_SIZE || nr < 0 || nr >= BoardConstants.BOARD_SIZE) {
                    continue;
                }
                Position to = Position.of(nf, nr);
                if (occ.get(to) != null) {
                    continue;
                }
                Step step = new Step();
                step.setKind(StepKind.SLIDE);
                step.setFrom(playNextFrom);
                step.setTo(to);
                if (isValidPlaySuffixFromViewHalfStart(g, List.of(step))) {
                    out.add(to);
                }
            }
        }
        if (remaining >= 2) {
            for (List<Step> bundle : DefaultRuleEngine.enumerateStepBundles(occ, side)) {
                if (bundle.size() > remaining) {
                    continue;
                }
                if (!bundleStartsFromPlayNext(bundle)) {
                    continue;
                }
                if (isValidPlaySuffixFromViewHalfStart(g, bundle)) {
                    out.add(bundle.get(0).getTo());
                }
            }
        }
        return out;
    }

    /**
     * Squares that are the destination of the first atomic step of a legal <strong>push</strong> bundle
     * (displace weaker), for keyboard Tab / Space and distinct highlighting.
     */
    private Set<Position> computePushBundleFirstStepTargets(Game g) {
        reconcilePlayPartialWithHistoryView();
        Set<Position> out = new HashSet<>();
        if (playNextFrom == null) {
            return out;
        }
        int remaining = 4 - playPartialMove.getSteps().size();
        if (remaining < 2) {
            return out;
        }
        Map<Position, Piece> occ;
        try {
            occ = DefaultRuleEngine.simulatePlayPrefix(g, new Move());
        } catch (IllegalArgumentException ex) {
            return out;
        }
        PlayerSide side = g.getSideToMove();
        for (List<Step> bundle : DefaultRuleEngine.enumerateStepBundles(occ, side)) {
            if (bundle.size() > remaining) {
                continue;
            }
            if (!bundleStartsFromPlayNext(bundle)) {
                continue;
            }
            if (DefaultRuleEngine.kindOf(bundle.get(0)) != StepKind.PUSH_DISPLACE_WEAKER) {
                continue;
            }
            if (isValidPlaySuffixFromViewHalfStart(g, bundle)) {
                out.add(bundle.get(0).getTo());
            }
        }
        return out;
    }

    /**
     * Opponent squares whose piece may complete a pull-drag after the last step was a slide vacating
     * {@link Step#getFrom()}.
     */
    private Set<Position> computePullDragTargets(Game g) {
        reconcilePlayPartialWithHistoryView();
        Set<Position> out = new HashSet<>();
        List<Step> steps = playPartialMove.getSteps();
        if (steps.isEmpty() || steps.size() >= 4) {
            return out;
        }
        Step last = steps.get(steps.size() - 1);
        if (DefaultRuleEngine.kindOf(last) != StepKind.SLIDE) {
            return out;
        }
        Position vacated = last.getFrom();
        int vf = vacated.getFileIndex();
        int vr = vacated.getRankIndex();
        int[][] deltas = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : deltas) {
            int nf = vf + d[0];
            int nr = vr + d[1];
            if (nf < 0 || nf >= BoardConstants.BOARD_SIZE || nr < 0 || nr >= BoardConstants.BOARD_SIZE) {
                continue;
            }
            Position weakPos = Position.of(nf, nr);
            Step drag = new Step();
            drag.setKind(StepKind.PULL_DRAG_WEAKER);
            drag.setFrom(weakPos);
            drag.setTo(vacated);
            if (isValidPlaySuffixFromViewHalfStart(g, List.of(drag))) {
                out.add(weakPos);
            }
        }
        return out;
    }

    private boolean bundleStartsFromPlayNext(List<Step> bundle) {
        Step s0 = bundle.get(0);
        StepKind k = DefaultRuleEngine.kindOf(s0);
        return switch (k) {
            case SLIDE -> playNextFrom.equals(s0.getFrom());
            case PUSH_DISPLACE_WEAKER ->
                    bundle.size() >= 2 && playNextFrom.equals(bundle.get(1).getFrom());
            case PULL_VACATE_STRONGER -> playNextFrom.equals(s0.getFrom());
            default -> false;
        };
    }

    private static Position endOwnSquareAfterBundle(List<Step> bundle) {
        Step s0 = bundle.get(0);
        StepKind k = DefaultRuleEngine.kindOf(s0);
        if (bundle.size() == 2 && k == StepKind.PUSH_DISPLACE_WEAKER) {
            return bundle.get(1).getTo();
        }
        if (bundle.size() == 2 && k == StepKind.PULL_VACATE_STRONGER) {
            return bundle.get(0).getTo();
        }
        return bundle.get(0).getTo();
    }

    private void paintBoard(Game g) {
        for (int row = 0; row < BoardConstants.BOARD_SIZE; row++) {
            for (int col = 0; col < BoardConstants.BOARD_SIZE; col++) {
                StackPane cell = boardCells[row][col];
                CellData data = (CellData) cell.getUserData();
                int mf = modelFileFromVisualCol(data.fileIndex(), g);
                int mr = modelRankFromVisualRow(row, g);
                Position pos = Position.of(mf, mr);
                Piece p = effectivePieceAt(g, pos);
                if (!pieceSkinUsesFigureArt()) {
                    data.pieceImage.setImage(null);
                    data.pieceImage.setVisible(false);
                    data.pieceLabel.setVisible(true);
                    data.pieceLabel.setMouseTransparent(false);
                    data.pieceLabel.setText(p == null ? "" : abbrev(p));
                    data.pieceLabel.setTextFill(p == null ? Color.BLACK
                            : (p.getSide() == PlayerSide.GOLD ? Color.color(0.55, 0.35, 0.05) : Color.color(0.25, 0.25, 0.35)));
                } else {
                    if (p == null) {
                        data.pieceImage.setImage(null);
                        data.pieceImage.setVisible(false);
                        data.pieceLabel.setText("");
                        data.pieceLabel.setVisible(false);
                        data.pieceLabel.setMouseTransparent(true);
                    } else {
                        Image img = figureRasterCache.getRasterized(p.getSide(), p.getType(), PIECE_IMAGE_MAX);
                        data.pieceImage.setImage(img);
                        boolean showImg = img != null;
                        data.pieceImage.setVisible(showImg);
                        if (showImg) {
                            data.pieceLabel.setText("");
                            data.pieceLabel.setVisible(false);
                            data.pieceLabel.setMouseTransparent(true);
                        } else {
                            data.pieceLabel.setText(abbrev(p));
                            data.pieceLabel.setVisible(true);
                            data.pieceLabel.setMouseTransparent(false);
                            data.pieceLabel.setTextFill(p.getSide() == PlayerSide.GOLD
                                    ? Color.color(0.55, 0.35, 0.05)
                                    : Color.color(0.25, 0.25, 0.35));
                        }
                    }
                }
            }
        }
    }

    private void refreshReserveButtons(Game g) {
        boolean setup = g.getState() == GameState.SETUP_GOLD || g.getState() == GameState.SETUP_SILVER;
        PlayerSide side = g.getSideToMove();
        Map<PieceType, Integer> counts = countReserve(g, side);
        for (PieceType type : PieceType.values()) {
            Button b = reserveButtons.get(type);
            int n = counts.getOrDefault(type, 0);
            b.setText(labelForReserveButton(type, n));
            b.setDisable(!setup || n == 0);
            if (pieceSkinUsesFigureArt() && setup && n > 0) {
                Image icon = figureRasterCache.getRasterized(side, type, RESERVE_ICON_MAX);
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

    private void refreshActionButtons(Game g) {
        boolean setup = g.getState() == GameState.SETUP_GOLD || g.getState() == GameState.SETUP_SILVER;
        boolean play = g.getState() == GameState.PLAY;
        PlayerSide side = g.getSideToMove();
        cancelHandButton.setDisable(!setup || g.getSetupHand() == null);
        chessButton.setDisable(!setup);
        doneButton.setDisable(!setup || !g.allSetupPiecesOnBoard(side));
        boolean canRandomFill = setup && reserveSizesMatchEmptyHome(g, side);
        boolean canRandomShuffle = setup && g.allSetupPiecesOnBoard(side);
        randomButton.setDisable(!setup || (!canRandomFill && !canRandomShuffle));
        if (setup) {
            randomButton.setText(canRandomShuffle ? "Náhodně rozestavit" : "Náhodně doplnit zbytek");
        }
        if (playEndTurnButton != null) {
            boolean canEnd = play
                    && gameController != null
                    && gameController.getPlayHistory().isBootstrapped()
                    && gameController.getPlayHistory().isAtEditableDraftTail()
                    && !playPartialMove.getSteps().isEmpty();
            playEndTurnButton.setDisable(!canEnd);
        }
        if (playCancelTurnButton != null) {
            boolean canCancelNormally = play
                    && (!playPartialMove.getSteps().isEmpty() || playNextFrom != null);
            boolean trapBlocksCancel = forbidCancelAfterTrapItem != null
                    && forbidCancelAfterTrapItem.isSelected()
                    && partialTurnAnyTrapRemoval(g);
            playCancelTurnButton.setDisable(!canCancelNormally || trapBlocksCancel);
        }
    }

    /**
     * Same condition as {@link Game#placeRemainingPiecesRandomly(PlayerSide)} needs to succeed.
     */
    private static boolean reserveSizesMatchEmptyHome(Game g, PlayerSide side) {
        List<Piece> res = g.getSetupReserveSnapshot(side);
        int empty = 0;
        for (int r = 0; r < BoardConstants.BOARD_SIZE; r++) {
            for (int f = 0; f < BoardConstants.BOARD_SIZE; f++) {
                Position p = Position.of(f, r);
                if (HomeTerritory.contains(side, p, g.isRanksMirroredForHomeCheck()) && g.getBoard().isEmpty(p)) {
                    empty++;
                }
            }
        }
        return !res.isEmpty() && res.size() == empty;
    }

    private void refreshSetupSectionVisibility(Game g) {
        boolean setup = g.getState() == GameState.SETUP_GOLD || g.getState() == GameState.SETUP_SILVER;
        reserveBox.setVisible(setup);
        reserveBox.setManaged(setup);
        cancelHandButton.setVisible(setup);
        cancelHandButton.setManaged(setup);
        randomButton.setVisible(setup);
        randomButton.setManaged(setup);
        chessButton.setVisible(setup);
        chessButton.setManaged(setup);
        doneButton.setVisible(setup);
        doneButton.setManaged(setup);
    }

    private void refreshCapturedPanel(Game g) {
        boolean show = g.getState() == GameState.PLAY || g.getState() == GameState.GAME_OVER;
        capturesBox.setVisible(show);
        capturesBox.setManaged(show);
        if (!show) {
            return;
        }
        fillCaptureFlow(goldCapturesPane, g, PlayerSide.GOLD);
        fillCaptureFlow(silverCapturesPane, g, PlayerSide.SILVER);
    }

    private void refreshNotationPanelVisibility(Game g) {
        boolean show = g != null && (g.getState() == GameState.PLAY || g.getState() == GameState.GAME_OVER);
        notationBox.setVisible(show);
        notationBox.setManaged(show);
    }

    private void refreshNotationHistory() {
        if (gameController == null) {
            notationHistoryItems.clear();
            return;
        }
        List<String> lines = buildNotationHistoryLines();
        suppressHistoryListEvents = true;
        notationHistoryItems.setAll(lines);
        PlayTurnHistory ph = gameController.getPlayHistory();
        if (ph.isBootstrapped()) {
            List<Integer> vis = ph.visibleHalfIndicesForDisplay(true);
            int sel = vis.indexOf(ph.viewHalfIndex());
            if (sel >= 0) {
                notationHistoryList.getSelectionModel().select(sel);
            } else {
                notationHistoryList.getSelectionModel().clearSelection();
            }
        }
        suppressHistoryListEvents = false;
    }

    private void fillCaptureFlow(FlowPane pane, Game g, PlayerSide capturer) {
        pane.getChildren().clear();
        List<PieceType> types = effectiveTrapCapturesForDisplay(g, capturer);
        if (types.isEmpty()) {
            pane.getChildren().add(new Label("—"));
            return;
        }
        PlayerSide victimSide = capturer == PlayerSide.GOLD ? PlayerSide.SILVER : PlayerSide.GOLD;
        for (PieceType t : types) {
            if (pieceSkinUsesFigureArt()) {
                Image img = figureRasterCache.getRasterized(victimSide, t, CAPTURE_ICON_MAX);
                if (img != null) {
                    ImageView iv = new ImageView(img);
                    iv.setFitWidth(CAPTURE_ICON_MAX);
                    iv.setFitHeight(CAPTURE_ICON_MAX);
                    iv.setPreserveRatio(true);
                    iv.setSmooth(true);
                    pane.getChildren().add(iv);
                } else {
                    pane.getChildren().add(new Label(abbrevType(t)));
                }
            } else {
                pane.getChildren().add(new Label(abbrevType(t)));
            }
        }
    }

    /**
     * Whether the currently displayed move prefix (history scrub position) removes at least one piece via trap
     * (any colour), evaluated from that half-turn’s start snapshot.
     */
    private boolean partialTurnAnyTrapRemoval(Game g) {
        if (g.getState() != GameState.PLAY || gameController == null || !gameController.getPlayHistory().isBootstrapped()) {
            return false;
        }
        PlayTurnHistory ph = gameController.getPlayHistory();
        if (ph.appliedPrefixSteps() <= 0) {
            return false;
        }
        PlayHalfTurn ht = ph.halfAt(ph.viewHalfIndex());
        Game probe = probeGameFromMemento(ht.startSnap());
        Move m = new Move();
        for (int i = 0; i < ph.appliedPrefixSteps(); i++) {
            m.getSteps().add(copyStep(ht.steps().get(i)));
        }
        DefaultRuleEngine.TrapCapturePreview p = DefaultRuleEngine.trapCapturesIfPrefixApplied(probe, m);
        return !p.byGold().isEmpty() || !p.bySilver().isEmpty();
    }

    /** Gameplay option: block cancel / draft undo·redo while this holds. */
    private boolean draftEditsBlockedByTrapMenuOption(Game g) {
        return forbidCancelAfterTrapItem != null
                && forbidCancelAfterTrapItem.isSelected()
                && partialTurnAnyTrapRemoval(g);
    }

    /**
     * Trap captures for the position currently in {@link Game} (already includes the displayed prefix after
     * {@link GameController#applyPlayHistoryViewToGame()}). No second simulation — that used to double-count.
     */
    private List<PieceType> effectiveTrapCapturesForDisplay(Game g, PlayerSide capturer) {
        return new ArrayList<>(g.getTrapCapturesSnapshot(capturer));
    }

    private void refreshHandLabel(Game g) {
        if (g.getState() == GameState.PLAY) {
            reconcilePlayPartialWithHistoryView();
            handLabel.setGraphic(null);
            handLabel.setContentDisplay(ContentDisplay.LEFT);
            int n = playPartialMove.getSteps().size();
            String mover = sideName(g.getSideToMove());
            handLabel.setText(n == 0
                    ? ("Tah (" + mover + "): žádné kroky (vyberte figuru)")
                    : ("Tah (" + mover + "): " + n + " krok(ů)"));
            return;
        }
        Piece h = g.getSetupHand();
        if (h == null) {
            handLabel.setGraphic(null);
            handLabel.setContentDisplay(ContentDisplay.LEFT);
            handLabel.setText("V ruce: —");
            return;
        }
        String text = "V ruce: " + abbrev(h) + " (" + sideName(h.getSide()) + ")";
        if (pieceSkinUsesFigureArt()) {
            Image hi = figureRasterCache.getRasterized(h.getSide(), h.getType(), HAND_ICON_MAX);
            if (hi != null) {
                handPieceGraphic.setImage(hi);
                handPieceGraphic.setFitWidth(HAND_ICON_MAX);
                handPieceGraphic.setFitHeight(HAND_ICON_MAX);
                handLabel.setGraphic(handPieceGraphic);
                handLabel.setContentDisplay(ContentDisplay.LEFT);
                handLabel.setText(text);
                return;
            }
        }
        handLabel.setGraphic(null);
        handLabel.setContentDisplay(ContentDisplay.LEFT);
        handLabel.setText(text);
    }

    private void updateWindowTitle(Game g) {
        String phase = switch (g.getState()) {
            case SETUP_GOLD -> "rozestavení Gold";
            case SETUP_SILVER -> "rozestavení Silver";
            case PLAY -> "hra";
            case GAME_OVER -> {
                PlayerSide w = g.getMatchWinner();
                yield w == null ? "konec hry" : ("výhra " + sideName(w));
            }
            default -> String.valueOf(g.getState());
        };
        if (g.getState() == GameState.GAME_OVER) {
            stage.setTitle("Arimaa – " + phase);
        } else {
            stage.setTitle("Arimaa – " + phase + " | na tahu: " + sideName(g.getSideToMove()));
        }
    }

    private void startNewGameAction() {
        Game g = game();
        if (g != null) {
            log.info("user action: new game");
            clearPlayTurnUi();
            chessSetupRotateIndex = 0;
            g.startNewGame();
            if (gameController != null) {
                gameController.resetTimeline();
            }
            setStatus("Nová hra — rozestavuje Gold.");
            refreshAll();
        }
    }

    private void saveGameToFileAction() {
        if (gameController == null || stage == null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Uložit hru");
        chooser.getExtensionFilters().add(new ExtensionFilter("Arimaa (*.txt)", "*.txt"));
        if (lastUsedDir != null && lastUsedDir.isDirectory()) {
            chooser.setInitialDirectory(lastUsedDir);
        }
        File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }
        lastUsedDir = file.getParentFile();
        Path path = file.toPath();
        Game g = game();
        String draft = null;
        if (g != null && g.getState() == GameState.PLAY && gameController.getPlayHistory().isBootstrapped()) {
            PlayTurnHistory ph = gameController.getPlayHistory();
            List<PlayHalfTurn> halves = ph.halfTurnsUnmodifiable();
            PlayHalfTurn tail = halves.get(halves.size() - 1);
            if (!tail.committed() && !tail.steps().isEmpty()) {
                Game probe = probeGameFromMemento(tail.startSnap());
                String prefix = gameController.nextPlayNotationPrefix();
                Move m = new Move();
                for (Step s : tail.steps()) {
                    m.getSteps().add(copyStep(s));
                }
                draft = ArimaaNotation.formatPartialTurnLine(probe.getBoard(), m, prefix);
            }
        }
        GameSerializer ser = new GameSerializer();
        String text = ser.serialize(gameController, draft);
        try {
            new GameRepository().saveUtf8(path, text);
            setStatus("Hra uložena do souboru.");
            log.info("user action: game saved to {}", path);
        } catch (IOException ex) {
            log.warn("save failed", ex);
            setStatus("Uložení se nepovedlo: " + ex.getMessage());
        }
    }

    /**
     * After a successful load the controller already restored the final position; refresh the UI.
     */
    private void playbackLoadedHistory() {
        syncPlayPartialFromHistory();
        refreshAll();
        setStatus("Hra načtena ze souboru.");
    }

    private void loadGameFromFileAction() {
        if (gameController == null || stage == null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Načíst hru");
        chooser.getExtensionFilters().add(new ExtensionFilter("Arimaa (*.txt)", "*.txt"));
        if (lastUsedDir != null && lastUsedDir.isDirectory()) {
            chooser.setInitialDirectory(lastUsedDir);
        }
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }
        lastUsedDir = file.getParentFile();
        Path path = file.toPath();
        try {
            String text = new GameRepository().loadUtf8(path);
            GameSerializer ser = new GameSerializer();
            GameSerializer.ParsedTxtGame p = ser.parse(text);
            clearPlayTurnUi();
            gameController.loadFromTxtGame(p.playStartSnapshot(), p.moveLines());
            syncPlayPartialFromHistory();
            log.info("user action: game loaded from {}", path);
            playbackLoadedHistory();
        } catch (IOException ex) {
            log.warn("load failed", ex);
            setStatus("Načtení se nepovedlo: " + ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("load parse/replay failed", ex);
            setStatus("Soubor nelze načíst: " + ex.getMessage());
        }
    }

    private void recordTimeline() {
        if (gameController != null) {
            gameController.recordAfterMutation();
        }
    }

    private void refreshHistoryMenus() {
        Game g = game();
        boolean undo;
        boolean redo;
        if (g == null || gameController == null) {
            undo = false;
            redo = false;
        } else if (g.getState() == GameState.SETUP_GOLD || g.getState() == GameState.SETUP_SILVER) {
            undo = gameController.canUndo();
            redo = gameController.canRedo();
        } else if (g.getState() == GameState.PLAY) {
            boolean trapLock = draftEditsBlockedByTrapMenuOption(g);
            boolean canDraftMutationUndo =
                    !trapLock
                            && isTrailingDraftAtLiveEnd()
                            && gameController.getPlayHistory().trailingUncommittedStepCount() > 0;
            boolean canNavUndo = !trapLock && gameController.canUndo();
            undo = canDraftMutationUndo || canNavUndo;
            boolean canDraftMutationRedo = !trapLock && isTrailingDraftAtLiveEnd() && !draftRedoSteps.isEmpty();
            boolean canNavRedo = !trapLock && gameController.canRedo();
            redo = canDraftMutationRedo || canNavRedo || (!trapLock && cancelledDraftOrNull != null);
        } else {
            undo = false;
            redo = false;
        }
        if (undoMenuItem != null) {
            undoMenuItem.setDisable(!undo);
        }
        if (redoMenuItem != null) {
            redoMenuItem.setDisable(!redo);
        }
    }

    private void appendHistory(GameHistoryEvent event) {
        if (gameController != null) {
            gameController.appendHistory(event);
        }
    }

    /** Clears draft-line redo and the snapshot used by Vpřed after a full draft cancel. */
    private void discardDraftRedoBranch() {
        draftRedoSteps.clear();
        cancelledDraftOrNull = null;
    }

    /** Trailing open draft, view at its end (not scrubbing inside the prefix). */
    private boolean isTrailingDraftAtLiveEnd() {
        if (gameController == null) {
            return false;
        }
        PlayTurnHistory ph = gameController.getPlayHistory();
        return ph.isBootstrapped()
                && ph.isViewOnTrailingDraftHalf()
                && ph.appliedPrefixSteps() == ph.trailingUncommittedStepCount();
    }

    /**
     * Vpřed after Zpět removed steps at the draft tail: re-append one leg, or two if the redo stack supplies a
     * push/pull bundle (same idea as pre–0.7.14 draft redo).
     */
    private boolean tryRedoDraftFromRedoStack() {
        if (draftRedoSteps.isEmpty() || gameController == null) {
            return false;
        }
        PlayTurnHistory ph = gameController.getPlayHistory();
        if (!ph.isBootstrapped() || !ph.isViewOnTrailingDraftHalf()) {
            return false;
        }
        List<PlayHalfTurn> halves = ph.halfTurnsUnmodifiable();
        PlayHalfTurn tail = halves.get(halves.size() - 1);
        if (tail.committed()) {
            return false;
        }
        Game probe = probeGameFromMemento(tail.startSnap());
        Step s = draftRedoSteps.pop();
        Move trial1 = new Move();
        for (Step st : tail.steps()) {
            trial1.getSteps().add(copyStep(st));
        }
        trial1.getSteps().add(copyStep(s));
        if (DefaultRuleEngine.isValidPlayPrefix(probe, trial1)) {
            ph.replaceTrailingDraftStepsFromMove(trial1);
            gameController.applyPlayHistoryViewToGame();
            syncPlayPartialFromHistory();
            appendHistory(new GameHistoryEvent.DraftStepRedone(playPartialMove.getSteps().size()));
            return true;
        }
        if (!draftRedoSteps.isEmpty()) {
            Step s2 = draftRedoSteps.pop();
            Move trial2 = copyMove(trial1);
            trial2.getSteps().add(copyStep(s2));
            if (DefaultRuleEngine.isValidPlayPrefix(probe, trial2)) {
                ph.replaceTrailingDraftStepsFromMove(trial2);
                gameController.applyPlayHistoryViewToGame();
                syncPlayPartialFromHistory();
                appendHistory(new GameHistoryEvent.DraftStepRedone(playPartialMove.getSteps().size()));
                return true;
            }
            draftRedoSteps.push(s2);
        }
        draftRedoSteps.push(s);
        return false;
    }

    private void performUndo() {
        Game g = game();
        if (g == null || gameController == null) {
            return;
        }
        if (g.getState() == GameState.SETUP_GOLD || g.getState() == GameState.SETUP_SILVER) {
            if (gameController.undo()) {
                setStatus("Zpět — vrácen předchozí stav.");
                refreshAll();
            }
            return;
        }
        if (g.getState() == GameState.PLAY) {
            if (draftEditsBlockedByTrapMenuOption(g)) {
                setStatus("Nelze vrátit krok — v rozpracovaném tahu padla figura do pasti (Gameplay).");
                return;
            }
            if (isTrailingDraftAtLiveEnd() && gameController.getPlayHistory().trailingUncommittedStepCount() > 0) {
                Step popped = gameController.getPlayHistory().popLastStepCopyFromTrailingDraft();
                if (popped != null) {
                    draftRedoSteps.push(popped);
                    gameController.applyPlayHistoryViewToGame();
                    syncPlayPartialFromHistory();
                    appendHistory(new GameHistoryEvent.DraftStepUndone(playPartialMove.getSteps().size()));
                    setStatus("Zpět — odstraněn poslední krok rozpracovaného tahu.");
                    refreshAll();
                    return;
                }
            }
            if (gameController.undo()) {
                draftRedoSteps.clear();
                syncPlayPartialFromHistory();
                setStatus("Zpět — krok zpět v rámci tahu (náhled).");
                refreshAll();
            } else {
                setStatus("Začátek tahu — další Zpět: klikněte na předchozí řádek v Historii tahů.");
            }
        }
    }

    private void performRedo() {
        Game g = game();
        if (g == null || gameController == null) {
            return;
        }
        if (g.getState() == GameState.SETUP_GOLD || g.getState() == GameState.SETUP_SILVER) {
            if (gameController.redo()) {
                setStatus("Vpřed — obnoven stav.");
                refreshAll();
            }
            return;
        }
        if (g.getState() != GameState.PLAY) {
            return;
        }
        if (draftEditsBlockedByTrapMenuOption(g)) {
            setStatus("Nelze vpřed — rozpracovaný tah obsahuje pád do pasti (Gameplay).");
            return;
        }
        if (isTrailingDraftAtLiveEnd() && tryRedoDraftFromRedoStack()) {
            setStatus("Vpřed — krok obnoven.");
            refreshAll();
            return;
        }
        if (gameController.redo()) {
            draftRedoSteps.clear();
            syncPlayPartialFromHistory();
            setStatus("Vpřed — krok vpřed v rámci tahu (náhled).");
            refreshAll();
            return;
        }
        if (cancelledDraftOrNull != null && redoCancelledDraft()) {
            setStatus("Vpřed — obnoven rozpracovaný tah.");
            refreshAll();
        }
    }

    private boolean redoCancelledDraft() {
        Game g = game();
        if (g == null || cancelledDraftOrNull == null || gameController == null) {
            return false;
        }
        Move m = copyMove(cancelledDraftOrNull.move());
        PlayTurnHistory ph = gameController.getPlayHistory();
        if (!ph.isBootstrapped()) {
            return false;
        }
        List<PlayHalfTurn> halves = ph.halfTurnsUnmodifiable();
        PlayHalfTurn tail = halves.get(halves.size() - 1);
        if (tail.committed()) {
            return false;
        }
        Game probe = probeGameFromMemento(tail.startSnap());
        if (!DefaultRuleEngine.isValidPlayPrefix(probe, m)) {
            return false;
        }
        playPartialMove.getSteps().clear();
        for (Step st : m.getSteps()) {
            playPartialMove.getSteps().add(copyStep(st));
        }
        playNextFrom = cancelledDraftOrNull.playNextFromOrNull();
        playActiveSegmentOrigin = cancelledDraftOrNull.playActiveSegmentOriginOrNull();
        cancelledDraftOrNull = null;
        draftRedoSteps.clear();
        gameController.getPlayHistory().replaceTrailingDraftStepsFromMove(playPartialMove);
        gameController.applyPlayHistoryViewToGame();
        appendHistory(new GameHistoryEvent.DraftRestoredAfterClear());
        syncPlayPartialFromHistory();
        return true;
    }

    /**
     * Square where the side to move's active piece stands after the given prefix (same convention as when adding steps).
     */
    private static Position playNextFromAfterPrefixSteps(List<Step> steps) {
        if (steps.isEmpty()) {
            return null;
        }
        int n = steps.size();
        Step last = steps.get(n - 1);
        StepKind lk = DefaultRuleEngine.kindOf(last);
        if (lk == StepKind.PULL_DRAG_WEAKER && n >= 2) {
            Step prev = steps.get(n - 2);
            if (DefaultRuleEngine.kindOf(prev) == StepKind.SLIDE) {
                return prev.getTo();
            }
        }
        if (lk == StepKind.SLIDE) {
            return last.getTo();
        }
        if (n >= 2) {
            Step prev = steps.get(n - 2);
            StepKind pk = DefaultRuleEngine.kindOf(prev);
            if ((pk == StepKind.PUSH_DISPLACE_WEAKER && lk == StepKind.PUSH_ADVANCE_STRONGER)
                    || (pk == StepKind.PULL_VACATE_STRONGER && lk == StepKind.PULL_DRAG_WEAKER)) {
                return endOwnSquareAfterBundle(List.of(prev, last));
            }
        }
        return last.getTo();
    }

    private static boolean isStaticTrapSquare(Position pos) {
        for (Position trap : BoardConstants.trapSquares()) {
            if (trap.equals(pos)) {
                return true;
            }
        }
        return false;
    }

    private void setStatus(String text) {
        statusLabel.setText(text);
    }

    private Game game() {
        return gameController != null ? gameController.getGame() : null;
    }

    /**
     * {@link #playPartialMove} must always mirror {@link PlayTurnHistory#copyViewPrefixStepsTo}; if anything
     * mutates the history view without a matching sync, UI labels / legality checks drift from the real board.
     */
    private void reconcilePlayPartialWithHistoryView() {
        if (gameController == null || !gameController.getPlayHistory().isBootstrapped()) {
            return;
        }
        Move expected = new Move();
        gameController.getPlayHistory().copyViewPrefixStepsTo(expected);
        if (playPartialMove.getSteps().size() != expected.getSteps().size()) {
            syncPlayPartialFromHistory();
        }
    }

    /**
     * {@link GameController#applyPlayHistoryViewToGame()} already applied {@link #playPartialMove} to {@code g}'s
     * board — do not pass the full draft again to {@link DefaultRuleEngine#isValidPlayPrefix(Game, Move)} on {@code g}
     * (that would re-apply steps). Validate {@code appended} as the next leg(s) from the viewed half-turn start.
     */
    private boolean isValidPlaySuffixFromViewHalfStart(Game g, List<Step> appended) {
        if (gameController == null || !gameController.getPlayHistory().isBootstrapped() || g == null) {
            return false;
        }
        PlayHalfTurn ht = gameController.getPlayHistory().halfAt(gameController.getPlayHistory().viewHalfIndex());
        Game probe = probeGameFromMemento(ht.startSnap());
        Move full = new Move();
        for (Step s : playPartialMove.getSteps()) {
            full.getSteps().add(copyStep(s));
        }
        for (Step s : appended) {
            full.getSteps().add(copyStep(s));
        }
        return DefaultRuleEngine.isValidPlayPrefix(probe, full);
    }

    private void syncPlayPartialFromHistory() {
        if (gameController == null || !gameController.getPlayHistory().isBootstrapped()) {
            return;
        }
        gameController.getPlayHistory().copyViewPrefixStepsTo(playPartialMove);
        Game g = game();
        if (g != null) {
            playNextFrom = playNextFromAfterPrefixSteps(playPartialMove.getSteps());
        } else {
            playNextFrom = null;
        }
        if (playPartialMove.getSteps().isEmpty()) {
            playActiveSegmentOrigin = null;
        }
    }

    private void beginPlayDraftMutationBeforeNewSteps() {
        if (gameController == null) {
            return;
        }
        PlayTurnHistory ph = gameController.getPlayHistory();
        if (ph.isBootstrapped() && ph.isViewOnTrailingDraftHalf()) {
            if (ph.appliedPrefixSteps() < ph.trailingUncommittedStepCount()) {
                ph.truncateTrailingDraftFrom(ph.appliedPrefixSteps());
            }
            ph.copyViewPrefixStepsTo(playPartialMove);
        }
        discardDraftRedoBranch();
    }

    private void syncPlayDraftFromPartialToHistoryAndApply() {
        if (gameController == null || !gameController.getPlayHistory().isBootstrapped()) {
            return;
        }
        gameController.getPlayHistory().replaceTrailingDraftStepsFromMove(playPartialMove);
        gameController.applyPlayHistoryViewToGame();
    }

    private List<String> buildNotationHistoryLines() {
        ArrayList<String> lines = new ArrayList<>();
        Game g = game();
        if (g == null || gameController == null || !gameController.getPlayHistory().isBootstrapped()) {
            return lines;
        }
        PlayTurnHistory ph = gameController.getPlayHistory();
        for (int h : ph.visibleHalfIndicesForDisplay(true)) {
            PlayHalfTurn ht = ph.halfAt(h);
            if (ht.committed()) {
                String n = ht.notationLineOrNull();
                if (n != null && !n.isBlank()) {
                    lines.add(n);
                }
            } else {
                String raw = ht.notationLineOrNull();
                if (raw != null && !raw.isBlank() && ht.steps().isEmpty()) {
                    lines.add(raw);
                } else {
                    Game probe = probeGameFromMemento(ht.startSnap());
                    Move m = new Move();
                    for (Step s : ht.steps()) {
                        m.getSteps().add(copyStep(s));
                    }
                    String prefix = gameController.nextPlayNotationPrefix();
                    lines.add(ArimaaNotation.formatPartialTurnLine(probe.getBoard(), m, prefix));
                }
            }
        }
        return lines;
    }

    private void clearPlayTurnUi() {
        playPartialMove.getSteps().clear();
        playNextFrom = null;
        playActiveSegmentOrigin = null;
        playKeyboardPullFocus = null;
        playKeyboardPushFocus = null;
        draftRedoSteps.clear();
        cancelledDraftOrNull = null;
    }

    /** Same as tlačítko „Náhodně …“ — náhodné doplnění nebo přeřazení na domovských řadách. */
    private void performRandomSetupPlacementAction() {
        Game g = game();
        if (g == null) {
            return;
        }
        PlayerSide side = g.getSideToMove();
        if (g.allSetupPiecesOnBoard(side)) {
            if (g.shuffleSetupPiecesOnHomeRandomly(side)) {
                setStatus("Figury na domovských řadách náhodně přeřazeny.");
                recordTimeline();
            } else {
                setStatus("Náhodné přeřazení se nepovedlo.");
            }
        } else if (g.placeRemainingPiecesRandomly(side)) {
            setStatus("Zbývající figury umístěny náhodně.");
            recordTimeline();
        } else {
            setStatus("Náhodné umístění se nepovedlo (musí sedět počet figurek a volných polí).");
        }
        refreshAll();
    }

    /** Same as „Šachová rozestavení“ — rotates among reversed / symmetric / MH / HH presets. */
    private void applyChessMappedSetupFromUi() {
        Game g = game();
        if (g == null) {
            return;
        }
        PlayerSide side = g.getSideToMove();
        int preset = Math.floorMod(chessSetupRotateIndex++, Game.CHESS_SETUP_ROTATION_COUNT);
        if (g.applyChessMappedSetup(side, preset)) {
            String[] names = {"opačné šachy", "symetrické", "MH", "HH"};
            setStatus(
                    "Šachová rozestavení — "
                            + names[preset]
                            + " ("
                            + (preset + 1)
                            + "/"
                            + Game.CHESS_SETUP_ROTATION_COUNT
                            + ").");
            recordTimeline();
        } else {
            chessSetupRotateIndex--;
            setStatus("Šachovou rozestavení nelze použít.");
        }
        refreshAll();
    }

    /** Same as „Hotovo (ukončit rozestavení)“. */
    private void tryCompleteSetupFromUi() {
        Game g = game();
        if (g == null) {
            return;
        }
        PlayerSide side = g.getSideToMove();
        if (g.tryCompleteSetup(side)) {
            setStatus("Rozestavení dokončeno.");
            if (g.getState() == GameState.PLAY) {
                if (gameController != null) {
                    gameController.enterPlayPhaseBootstrap();
                }
            } else {
                recordTimeline();
            }
        } else {
            setStatus("Rozestavení nelze dokončit (rezerva, multiset, 16 figurek na domově…).");
        }
        refreshAll();
    }

    /**
     * Clears the in-progress PLAY turn draft (same as „Zrušit rozpracovaný tah“ / Esc): all steps revert to „start of
     * turn“. Unlike step-by-step Zpět, this does not keep a partial prefix — the whole draft is cleared; Vpřed can
     * restore the snapshot via {@link #cancelledDraftOrNull}.
     *
     * @return {@code true} if the draft was cleared; {@code false} if nothing to cancel, wrong phase, or trap lock
     */
    private boolean tryCancelPlayDraftFromUi() {
        Game g = game();
        if (g == null || g.getState() != GameState.PLAY || gameController == null) {
            return false;
        }
        if (playPartialMove.getSteps().isEmpty() && playNextFrom == null) {
            return false;
        }
        boolean trapBlocksCancel = forbidCancelAfterTrapItem != null
                && forbidCancelAfterTrapItem.isSelected()
                && partialTurnAnyTrapRemoval(g);
        if (trapBlocksCancel) {
            setStatus("Nelze zrušit rozpracovaný tah — v rozpracovaném tahu padla figura do pasti (Gameplay).");
            return false;
        }
        discardDraftRedoBranch();
        cancelledDraftOrNull = new CancelledDraftSnapshot(
                gameController.getPlayHistory().pendingDraftAsMoveCopy(),
                playNextFrom,
                playActiveSegmentOrigin);
        gameController.getPlayHistory().clearTrailingDraftSteps();
        gameController.applyPlayHistoryViewToGame();
        playPartialMove.getSteps().clear();
        playNextFrom = null;
        playActiveSegmentOrigin = null;
        playKeyboardPullFocus = null;
        playKeyboardPushFocus = null;
        appendHistory(new GameHistoryEvent.DraftCleared());
        setStatus("Rozpracovaný tah zrušen (Vpřed obnoví).");
        refreshAll();
        return true;
    }

    private Piece effectivePieceAt(Game g, Position pos) {
        return g.getBoard().getPiece(pos);
    }

    private static Move copyMove(Move src) {
        Move m = new Move();
        for (Step s : src.getSteps()) {
            m.getSteps().add(copyStep(s));
        }
        return m;
    }

    private static Step copyStep(Step s) {
        Step t = new Step();
        t.setFrom(s.getFrom());
        t.setTo(s.getTo());
        t.setKind(s.getKind());
        return t;
    }

    private static Game probeGameFromMemento(GameMemento m) {
        Game g = new Game();
        g.startNewGame();
        g.restoreMemento(m);
        return g;
    }

    private int comparePositionVisual(Position a, Position b, Game g) {
        int ra = visualRowFromModelRank(a.getRankIndex(), g);
        int rb = visualRowFromModelRank(b.getRankIndex(), g);
        int cmp = Integer.compare(ra, rb);
        if (cmp != 0) {
            return cmp;
        }
        return Integer.compare(visualColFromModelFile(a.getFileIndex(), g), visualColFromModelFile(b.getFileIndex(), g));
    }

    /**
     * Keyboard cycling order for pull/push targets: mirrored when the mover sits at the visual top (swap vertical and
     * horizontal precedence vs {@link #comparePositionVisual}).
     */
    private int comparePositionKeyboardCycle(Position a, Position b, Game g) {
        if (moverVisualBottom(g)) {
            return comparePositionVisual(a, b, g);
        }
        int ra = visualRowFromModelRank(a.getRankIndex(), g);
        int rb = visualRowFromModelRank(b.getRankIndex(), g);
        int cmp = Integer.compare(rb, ra);
        if (cmp != 0) {
            return cmp;
        }
        return Integer.compare(
                visualColFromModelFile(b.getFileIndex(), g), visualColFromModelFile(a.getFileIndex(), g));
    }

    /** {@code true} when the side to move’s home half is toward the bottom of the screen. */
    private boolean moverVisualBottom(Game g) {
        PlayerSide m = g.getSideToMove();
        if (m == PlayerSide.GOLD) {
            return boardGoldVisualBottom(g);
        }
        return !boardGoldVisualBottom(g);
    }

    /**
     * Own pieces for Tab: strongest type first ({@link PieceType} order), then toward the side’s goal rank (Gold:
     * higher model rank is closer; Silver: lower model rank is closer). Equal strength and same advancement distance:
     * team drawn at the visual bottom — left→right, top→bottom; team at the visual top — right→left, bottom→top.
     */
    private List<Position> ownPiecesInPlayTabOrder(Game g) {
        PlayerSide side = g.getSideToMove();
        List<Position> all = new ArrayList<>();
        for (int f = 0; f < BoardConstants.BOARD_SIZE; f++) {
            for (int r = 0; r < BoardConstants.BOARD_SIZE; r++) {
                Position p = Position.of(f, r);
                Piece pc = effectivePieceAt(g, p);
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

    /**
     * Tab order: piece strength, then goal-side proximity in model ranks, then visual scan (see
     * {@link #ownPiecesInPlayTabOrder}).
     */
    private int compareOwnPiecesTabOrder(Position a, Position b, Game g, PlayerSide side) {
        Piece pa = effectivePieceAt(g, a);
        Piece pb = effectivePieceAt(g, b);
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
        int ca = visualColFromModelFile(a.getFileIndex(), g);
        int cb = visualColFromModelFile(b.getFileIndex(), g);
        int va = visualRowFromModelRank(ra, g);
        int vb = visualRowFromModelRank(rb, g);
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

    /**
     * Ctrl+Tab / Ctrl+Shift+Tab: cycle own pieces only (clears pull/push keyboard focus). Use during push or pull to pick
     * another friendly piece without cycling orange / purple targets.
     */
    private void advancePlayTabFocusOwnPiecesOnly(Game g, boolean reverse) {
        playKeyboardPullFocus = null;
        playKeyboardPushFocus = null;
        selectNextOwnPieceForPlayKeyboard(g, reverse, "Ctrl+Tab");
    }

    /**
     * @param keyboardLabel e.g. {@code "Tab"} / {@code "Ctrl+Tab"} for status wording; {@code null} for mouse selection
     */
    private void selectNextOwnPieceForPlayKeyboard(Game g, boolean reverse, String keyboardLabel) {
        List<Position> own = ownPiecesInPlayTabOrder(g);
        if (own.isEmpty()) {
            return;
        }
        int start = playNextFrom == null ? -1 : own.indexOf(playNextFrom);
        int idx;
        if (start < 0) {
            idx = reverse ? own.size() - 1 : 0;
        } else {
            idx = reverse ? (start - 1 + own.size()) % own.size() : (start + 1) % own.size();
        }
        Position next = own.get(idx);
        discardDraftRedoBranch();
        playNextFrom = next;
        playActiveSegmentOrigin = next;
        setStatusOwnPieceSelected(g, keyboardLabel);
        refreshAll();
    }

    /**
     * Tab / Shift+Tab: cycle pull targets, else push first-step targets, else own pieces.
     */
    private void advancePlayTabFocus(Game g, boolean reverse) {
        Set<Position> pulls = computePullDragTargets(g);
        if (!pulls.isEmpty()) {
            playKeyboardPushFocus = null;
            List<Position> list = new ArrayList<>(pulls);
            list.sort((a, b) -> comparePositionKeyboardCycle(a, b, g));
            if (playKeyboardPullFocus != null && !pulls.contains(playKeyboardPullFocus)) {
                playKeyboardPullFocus = null;
            }
            int idx;
            if (playKeyboardPullFocus == null) {
                idx = reverse ? list.size() - 1 : 0;
            } else {
                int cur = list.indexOf(playKeyboardPullFocus);
                if (cur < 0) {
                    cur = 0;
                }
                idx = reverse ? (cur - 1 + list.size()) % list.size() : (cur + 1) % list.size();
            }
            playKeyboardPullFocus = list.get(idx);
            setStatus("Tahnutí — mezerník dokončí výběr soupeře (Tab = další figura).");
            refreshAll();
            return;
        }
        playKeyboardPullFocus = null;
        Set<Position> pushT = computePushBundleFirstStepTargets(g);
        if (!pushT.isEmpty()) {
            List<Position> plist = new ArrayList<>(pushT);
            plist.sort((a, b) -> comparePositionKeyboardCycle(a, b, g));
            if (playKeyboardPushFocus != null && !pushT.contains(playKeyboardPushFocus)) {
                playKeyboardPushFocus = null;
            }
            int pidx;
            if (playKeyboardPushFocus == null) {
                pidx = reverse ? plist.size() - 1 : 0;
            } else {
                int cur = plist.indexOf(playKeyboardPushFocus);
                if (cur < 0) {
                    cur = 0;
                }
                pidx = reverse ? (cur - 1 + plist.size()) % plist.size() : (cur + 1) % plist.size();
            }
            playKeyboardPushFocus = plist.get(pidx);
            setStatus("Tlačení — mezerník provede výběr (Tab = další oranžový cíl).");
            refreshAll();
            return;
        }
        playKeyboardPushFocus = null;
        selectNextOwnPieceForPlayKeyboard(g, reverse, "Tab");
    }

    /** Status after choosing own piece: mention pull only when purple pull targets exist. */
    private void setStatusOwnPieceSelected(Game g, String keyboardLabelOrNull) {
        String head =
                keyboardLabelOrNull == null
                        ? "Vybrána figura"
                        : "Vybrána figura (" + keyboardLabelOrNull + ")";
        if (computePullDragTargets(g).isEmpty()) {
            setStatus(head + " — volné pole = krok.");
        } else {
            setStatus(
                    head
                            + " — volné pole = krok; po uvolnění můžete kliknout na fialově označenou soupeřovu figuru (tahnutí).");
        }
    }

    /** Space: complete pull using keyboard focus or first pull target in visual order. */
    private void activatePlayPullFromKeyboard(Game g) {
        Set<Position> pulls = computePullDragTargets(g);
        if (pulls.isEmpty()) {
            return;
        }
        List<Position> sorted = new ArrayList<>(pulls);
        sorted.sort((a, b) -> comparePositionKeyboardCycle(a, b, g));
        Position pos = playKeyboardPullFocus != null && pulls.contains(playKeyboardPullFocus)
                ? playKeyboardPullFocus
                : sorted.get(0);
        handlePlayBoardActivation(pos.getFileIndex(), pos.getRankIndex());
    }

    /** Space: apply push bundle for keyboard focus or first push target in visual order. */
    private void activatePlayPushFromKeyboard(Game g) {
        Set<Position> pushT = computePushBundleFirstStepTargets(g);
        if (pushT.isEmpty()) {
            return;
        }
        List<Position> sorted = new ArrayList<>(pushT);
        sorted.sort((a, b) -> comparePositionKeyboardCycle(a, b, g));
        Position pos = playKeyboardPushFocus != null && pushT.contains(playKeyboardPushFocus)
                ? playKeyboardPushFocus
                : sorted.get(0);
        handlePlayBoardActivation(pos.getFileIndex(), pos.getRankIndex());
    }

    /**
     * Model square (file/rank indices) activated during PLAY — same behaviour as a board click.
     * New steps are only accepted on the trailing draft row at the current history position (see
     * {@link PlayTurnHistory#isViewOnTrailingDraftHalf()}).
     */
    private void handlePlayBoardActivation(int modelFile, int modelRank) {
        Game g = game();
        if (g == null || g.getState() != GameState.PLAY || gameController == null) {
            return;
        }
        if (!gameController.getPlayHistory().isBootstrapped()
                || !gameController.getPlayHistory().isViewOnTrailingDraftHalf()) {
            setStatus("Prohlížíte starší tah — klikněte na poslední řádek v „Historie tahů“ pro pokračování.");
            return;
        }
        reconcilePlayPartialWithHistoryView();
        Position pos = Position.of(modelFile, modelRank);
        PlayerSide side = g.getSideToMove();
        Piece at = effectivePieceAt(g, pos);
        if (at != null) {
            if (at.getSide() == side) {
                discardDraftRedoBranch();
                playNextFrom = pos;
                playActiveSegmentOrigin = pos;
                setStatusOwnPieceSelected(g, null);
                refreshAll();
                return;
            }
            if (!playPartialMove.getSteps().isEmpty() && playPartialMove.getSteps().size() < 4) {
                Step last = playPartialMove.getSteps().get(playPartialMove.getSteps().size() - 1);
                if (DefaultRuleEngine.kindOf(last) == StepKind.SLIDE && computePullDragTargets(g).contains(pos)) {
                    Position vacated = last.getFrom();
                    Step drag = new Step();
                    drag.setKind(StepKind.PULL_DRAG_WEAKER);
                    drag.setFrom(pos);
                    drag.setTo(vacated);
                    if (isValidPlaySuffixFromViewHalfStart(g, List.of(drag))) {
                        beginPlayDraftMutationBeforeNewSteps();
                        playPartialMove.getSteps().add(copyStep(drag));
                        playNextFrom = last.getTo();
                        appendHistory(new GameHistoryEvent.DraftStepAdded(playPartialMove.getSteps().size()));
                        syncPlayDraftFromPartialToHistoryAndApply();
                        setStatus("Tahnutí dokončeno (" + playPartialMove.getSteps().size() + "/4). Konec tahu nebo další krok.");
                        refreshAll();
                        return;
                    }
                }
            }
            setStatus("Tuto soupeřovu figuru teď táhnout nelze.");
            return;
        }
        if (playNextFrom == null) {
            setStatus("Nejdřív vyberte svou figuru.");
            return;
        }
        if (playPartialMove.getSteps().size() >= 4) {
            setStatus("Maximálně 4 kroky — stiskněte Konec tahu.");
            return;
        }
        int remaining = 4 - playPartialMove.getSteps().size();
        if (remaining >= 1) {
            Step slide = new Step();
            slide.setKind(StepKind.SLIDE);
            slide.setFrom(playNextFrom);
            slide.setTo(pos);
            if (isValidPlaySuffixFromViewHalfStart(g, List.of(slide))) {
                beginPlayDraftMutationBeforeNewSteps();
                playPartialMove.getSteps().add(copyStep(slide));
                playNextFrom = pos;
                appendHistory(new GameHistoryEvent.DraftStepAdded(playPartialMove.getSteps().size()));
                syncPlayDraftFromPartialToHistoryAndApply();
                setStatus("Krok přidán (" + playPartialMove.getSteps().size() + "/4). Konec tahu nebo další krok.");
                refreshAll();
                return;
            }
        }
        if (remaining >= 2) {
            Map<Position, Piece> occ;
            try {
                occ = DefaultRuleEngine.simulatePlayPrefix(g, new Move());
            } catch (IllegalArgumentException ex) {
                setStatus("Neplatný krok.");
                return;
            }
            for (List<Step> bundle : DefaultRuleEngine.enumerateStepBundles(occ, side)) {
                if (bundle.size() > remaining) {
                    continue;
                }
                if (!bundleStartsFromPlayNext(bundle)) {
                    continue;
                }
                if (!bundle.get(0).getTo().equals(pos)) {
                    continue;
                }
                if (!isValidPlaySuffixFromViewHalfStart(g, bundle)) {
                    continue;
                }
                beginPlayDraftMutationBeforeNewSteps();
                for (Step st : bundle) {
                    playPartialMove.getSteps().add(copyStep(st));
                }
                playNextFrom = endOwnSquareAfterBundle(bundle);
                appendHistory(new GameHistoryEvent.DraftStepAdded(playPartialMove.getSteps().size()));
                syncPlayDraftFromPartialToHistoryAndApply();
                setStatus("Krok přidán (" + playPartialMove.getSteps().size() + "/4). Konec tahu nebo další krok.");
                refreshAll();
                return;
            }
        }
        setStatus("Neplatný krok.");
    }

    private void tryEndPlayTurn() {
        Game g = game();
        if (g == null || g.getState() != GameState.PLAY || gameController == null) {
            return;
        }
        reconcilePlayPartialWithHistoryView();
        if (playPartialMove.getSteps().isEmpty()) {
            setStatus("Přidejte aspoň jeden krok.");
            return;
        }
        gameController.restoreTrailingDraftTurnStartForSubmit();
        String prefix = gameController.nextPlayNotationPrefix();
        Move submit = copyMove(playPartialMove);
        String notationLine = ArimaaNotation.formatFullTurn(g.getBoard(), submit, prefix);
        log.debug("submitting play turn: {} steps", submit.getSteps().size());
        if (!gameController.submitHumanMove(submit)) {
            log.info("submitHumanMove rejected (illegal or invalid state)");
            setStatus("Tah není platný.");
            gameController.applyPlayHistoryViewToGame();
            refreshAll();
            return;
        }
        gameController.recordCommittedPlayTurn(submit, notationLine);
        clearPlayTurnUi();
        syncPlayPartialFromHistory();
        if (g.getState() == GameState.GAME_OVER) {
            PlayerSide w = g.getMatchWinner();
            log.info("play turn ended: GAME_OVER winner={}", w);
            setStatus(w == null ? "Konec hry." : ("Konec hry — vyhrál " + sideName(w) + "."));
        } else {
            log.info("play turn ended: state={} sideToMove={}", g.getState(), g.getSideToMove());
            setStatus("Tah proveden.");
        }
        appendHistory(new GameHistoryEvent.TurnCommitted(notationLine));
        refreshAll();
    }

    private static Map<PieceType, Integer> countReserve(Game g, PlayerSide side) {
        Map<PieceType, Integer> m = new EnumMap<>(PieceType.class);
        for (Piece p : g.getSetupReserveSnapshot(side)) {
            m.merge(p.getType(), 1, Integer::sum);
        }
        return m;
    }

    private static String labelForReserveButton(PieceType type, int count) {
        return abbrevType(type) + " × " + count;
    }

    private static String abbrev(Piece p) {
        return abbrevType(p.getType());
    }

    private static String abbrevType(PieceType t) {
        return switch (t) {
            case ELEPHANT -> "E";
            case CAMEL -> "M";
            case HORSE -> "H";
            case DOG -> "D";
            case CAT -> "K";
            case RABBIT -> "R";
        };
    }

    private static String sideName(PlayerSide s) {
        return s == PlayerSide.GOLD ? "Gold" : "Silver";
    }

    private record CellData(
            /** Visual column index ({@code 0} = left edge of the grid). */
            int fileIndex,
            int visualRow,
            Rectangle background,
            Color baseFill,
            Color baseStroke,
            double baseStrokeWidth,
            Label pieceLabel,
            ImageView pieceImage,
            ImageView hoverImage,
            Label hoverLabel) {
    }

    private static Region spacer(int h) {
        Region r = new Region();
        r.setMinHeight(h);
        return r;
    }

    /**
     * Centers the framed board and scales it uniformly: {@link Scale} with pivot (0,0) so layout matches
     * painted pixels (unlike {@code setScaleX}, which uses the node centre as pivot and breaks {@code relocate}).
     */
    private static final class BoardHostPane extends Pane {

        private final StackPane board;
        private final double outer;
        private final Scale scaleTf = new Scale(1, 1, 0, 0);

        BoardHostPane(StackPane framedBoard, double outer) {
            this.board = framedBoard;
            this.outer = outer;
            board.getTransforms().setAll(scaleTf);
            getChildren().add(board);
            setBackground(Background.EMPTY);
            setMinSize(0, 0);
            setMaxWidth(Double.MAX_VALUE);
            setMaxHeight(Double.MAX_VALUE);
        }

        @Override
        protected double computePrefWidth(double height) {
            return 0;
        }

        @Override
        protected double computePrefHeight(double width) {
            return 0;
        }

        @Override
        protected void layoutChildren() {
            double w = getWidth();
            double h = getHeight();
            if (w <= 0 || h <= 0 || outer <= 0) {
                return;
            }
            double avail = Math.max(0.0, Math.min(w, h) - 2.0 * BOARD_VIEW_MARGIN);
            double scale = avail / outer;
            if (!Double.isFinite(scale)) {
                scale = 1.0;
            }
            scale = Math.clamp(scale, 0.08, 40.0);
            scaleTf.setX(scale);
            scaleTf.setY(scale);
            double x = (w - avail) / 2.0;
            double y = (h - avail) / 2.0;
            board.resize(outer, outer);
            board.relocate(snapPositionX(x), snapPositionY(y));
        }
    }
}
