package cz.cvut.fel.pjv.arimaa.ui;

import cz.cvut.fel.pjv.arimaa.controller.GameController;
import cz.cvut.fel.pjv.arimaa.logging.LoggingSupport;
import cz.cvut.fel.pjv.arimaa.model.DefaultRuleEngine;
import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.GameState;
import cz.cvut.fel.pjv.arimaa.model.Move;
import cz.cvut.fel.pjv.arimaa.model.Piece;
import cz.cvut.fel.pjv.arimaa.model.PieceType;
import cz.cvut.fel.pjv.arimaa.model.PlayerSide;
import cz.cvut.fel.pjv.arimaa.model.Position;
import cz.cvut.fel.pjv.arimaa.model.Step;
import cz.cvut.fel.pjv.arimaa.model.StepKind;
import cz.cvut.fel.pjv.arimaa.util.ArimaaNotation;
import cz.cvut.fel.pjv.arimaa.util.BoardConstants;
import cz.cvut.fel.pjv.arimaa.util.HomeTerritory;
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
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextArea;
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
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeType;
import javafx.scene.text.Font;
import javafx.scene.transform.Scale;
import javafx.stage.Stage;

import ch.qos.logback.classic.Level;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Primary window: board and setup controls (manual, random, chess layout, finish setup).
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
    private PieceSkin pieceSkin = PieceSkin.DEFAULT;

    /** When selected, „Zrušit rozpracovaný tah“ is disabled after the mover's own piece is trapped in the current prefix. */
    private CheckMenuItem forbidCancelAfterOwnTrapItem;

    /** Home square currently hovered during setup (ghost placement); both null if none. */
    private Integer hoverFileIndex;
    private Integer hoverRankIndex;

    /** In {@link GameState#PLAY}: steps not yet committed; origin for the next step. */
    private final Move playPartialMove = new Move();
    private Position playNextFrom;
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
    private final TextArea notationHistoryArea = new TextArea();

    private enum PieceSkin {
        /** Letter abbreviations only (no piece art). */
        NONE,
        /** Rasterized SVGs from classpath {@code /images/figure_sets/default/}. */
        DEFAULT
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
        randomButton.setOnAction(e -> {
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
        });

        chessButton = new Button("Šachová rozestavení");
        chessButton.setMaxWidth(Double.MAX_VALUE);
        chessButton.setOnAction(e -> {
            Game g = game();
            if (g == null) {
                return;
            }
            PlayerSide side = g.getSideToMove();
            if (g.applyChessMappedSetup(side)) {
                setStatus("Použita pevná šachová rozestavení.");
                recordTimeline();
            } else {
                setStatus("Šachovou rozestavení nelze použít.");
            }
            refreshAll();
        });

        doneButton = new Button("Hotovo (ukončit rozestavení)");
        doneButton.setMaxWidth(Double.MAX_VALUE);
        doneButton.setOnAction(e -> {
            Game g = game();
            if (g == null) {
                return;
            }
            PlayerSide side = g.getSideToMove();
            if (g.tryCompleteSetup(side)) {
                setStatus("Rozestavení dokončeno.");
                recordTimeline();
            } else {
                setStatus("Rozestavení nelze dokončit (rezerva, multiset, 16 figurek na domově…).");
            }
            refreshAll();
        });

        playEndTurnButton = new Button("Konec tahu");
        playEndTurnButton.setMaxWidth(Double.MAX_VALUE);
        playEndTurnButton.setOnAction(e -> tryEndPlayTurn());

        playCancelTurnButton = new Button("Zrušit rozpracovaný tah");
        playCancelTurnButton.setMaxWidth(Double.MAX_VALUE);
        playCancelTurnButton.setOnAction(e -> {
            if (!playPartialMove.getSteps().isEmpty() || playNextFrom != null) {
                clearPlayTurnUi();
                setStatus("Rozpracovaný tah zrušen.");
                refreshAll();
            }
        });

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

        notationHistoryArea.setEditable(false);
        notationHistoryArea.setWrapText(true);
        notationHistoryArea.setFont(Font.font("Consolas", 11));
        notationHistoryArea.setPrefRowCount(10);
        notationHistoryArea.setMaxHeight(220);
        notationHistoryArea.setMinHeight(72);

        notationBox.getChildren().addAll(new Label("Notace tahů:"), notationHistoryArea);
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
        MenuItem ukoncitItem = new MenuItem("Ukončit");
        ukoncitItem.setAccelerator(new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN));
        ukoncitItem.setOnAction(e -> Platform.exit());
        menuHra.getItems().addAll(novaHraItem, new SeparatorMenuItem(), ukoncitItem);

        Menu menuTah = new Menu("Tah");
        undoMenuItem = new MenuItem("Zpět");
        undoMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN));
        undoMenuItem.setOnAction(e -> {
            if (gameController != null && gameController.undo()) {
                clearPlayTurnUi();
                setStatus("Zpět — vrácen předchozí stav.");
                refreshAll();
            }
        });
        redoMenuItem = new MenuItem("Vpřed");
        redoMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN));
        redoMenuItem.setOnAction(e -> {
            if (gameController != null && gameController.redo()) {
                clearPlayTurnUi();
                setStatus("Vpřed — obnoven stav.");
                refreshAll();
            }
        });
        menuTah.getItems().addAll(undoMenuItem, redoMenuItem);

        Menu menuGameplay = new Menu("Gameplay");
        Menu menuSkins = new Menu("Skins");
        ToggleGroup skinToggleGroup = new ToggleGroup();
        RadioMenuItem skinDefaultItem = new RadioMenuItem("Default");
        skinDefaultItem.setToggleGroup(skinToggleGroup);
        skinDefaultItem.setUserData(PieceSkin.DEFAULT);
        RadioMenuItem skinNoneItem = new RadioMenuItem("None");
        skinNoneItem.setToggleGroup(skinToggleGroup);
        skinNoneItem.setUserData(PieceSkin.NONE);
        skinDefaultItem.setSelected(true);
        skinToggleGroup.selectedToggleProperty().addListener((obs, prev, toggled) -> {
            if (toggled == null || !(toggled.getUserData() instanceof PieceSkin selected)) {
                return;
            }
            pieceSkin = selected;
            refreshAll();
        });
        menuSkins.getItems().addAll(skinDefaultItem, skinNoneItem);
        menuGameplay.getItems().add(menuSkins);
        menuGameplay.getItems().add(new SeparatorMenuItem());
        forbidCancelAfterOwnTrapItem = new CheckMenuItem(
                "Po pádu vlastní figury do pasti nelze zrušit rozpracovaný tah");
        forbidCancelAfterOwnTrapItem.setSelected(false);
        forbidCancelAfterOwnTrapItem.selectedProperty().addListener((obs, prev, now) -> refreshAll());
        menuGameplay.getItems().add(forbidCancelAfterOwnTrapItem);

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
        scene.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                Game g = game();
                if (g != null && g.getState() == GameState.PLAY) {
                    tryEndPlayTurn();
                    e.consume();
                }
            }
        });
        primaryStage.setTitle("Arimaa – rozestavení");
        primaryStage.setScene(scene);
        primaryStage.show();

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
            surface.add(coordLabel(String.valueOf((char) ('a' + f)), CELL, COORD, true), f + 1, 0);
            surface.add(coordLabel(String.valueOf((char) ('a' + f)), CELL, COORD, true), f + 1, 9);
        }
        for (int visualRow = 0; visualRow < BoardConstants.BOARD_SIZE; visualRow++) {
            String rankText = Integer.toString(BoardConstants.BOARD_SIZE - visualRow);
            int gridRow = visualRow + 1;
            surface.add(coordLabel(rankText, COORD, CELL, false), 0, gridRow);
            surface.add(coordLabel(rankText, COORD, CELL, false), 9, gridRow);
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

    private StackPane createCell(int fileIndex, int gridRow) {
        Rectangle bg = new Rectangle(CELL, CELL);
        bg.setStrokeType(StrokeType.INSIDE);
        int rankIndex = BoardConstants.BOARD_SIZE - 1 - gridRow;
        Position pos = Position.of(fileIndex, rankIndex);
        Color baseFill;
        if (HomeTerritory.contains(PlayerSide.GOLD, pos, false)
                || HomeTerritory.contains(PlayerSide.SILVER, pos, false)) {
            baseFill = Color.color(0.75, 0.82, 0.95);
        } else {
            baseFill = gridRow % 2 == fileIndex % 2 ? Color.color(0.93, 0.88, 0.78) : Color.color(0.85, 0.78, 0.65);
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
        cell.setUserData(new CellData(fileIndex, rankIndex, bg, baseFill, baseStroke, baseStrokeWidth,
                pieceLbl, pieceImg, hoverImg, hoverLbl));

        final int fi = fileIndex;
        final int ri = rankIndex;
        cell.hoverProperty().addListener((obs, was, hovering) -> {
            if (Boolean.TRUE.equals(hovering)) {
                setHoverCell(fi, ri);
            } else {
                clearHoverCellIf(fi, ri);
            }
        });

        cell.setOnMouseClicked(e -> onBoardCellClick(fileIndex, rankIndex));
        return cell;
    }

    private void setHoverCell(int fileIndex, int rankIndex) {
        hoverFileIndex = fileIndex;
        hoverRankIndex = rankIndex;
        paintHoverOverlay(game());
    }

    private void clearHoverCellIf(int fileIndex, int rankIndex) {
        if (hoverFileIndex != null && hoverFileIndex == fileIndex && hoverRankIndex != null && hoverRankIndex == rankIndex) {
            hoverFileIndex = null;
            hoverRankIndex = null;
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
                Position pos = Position.of(data.fileIndex(), data.rankIndex());
                boolean overHere = setup && hand != null
                        && hoverFileIndex != null && hoverRankIndex != null
                        && data.fileIndex() == hoverFileIndex && data.rankIndex() == hoverRankIndex;
                boolean show = overHere && g.isLegalSetupHandPlacementTarget(pos);
                if (!show) {
                    hImg.setVisible(false);
                    hLbl.setVisible(false);
                    continue;
                }
                if (pieceSkin == PieceSkin.DEFAULT) {
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

    private void onBoardCellClick(int fileIndex, int rankIndex) {
        Game g = game();
        if (g == null || g.getBoard() == null) {
            return;
        }
        GameState st = g.getState();
        if (st == GameState.PLAY) {
            onBoardCellClickPlay(fileIndex, rankIndex);
            return;
        }
        if (st != GameState.SETUP_GOLD && st != GameState.SETUP_SILVER) {
            return;
        }
        PlayerSide side = g.getSideToMove();
        Position pos = Position.of(fileIndex, rankIndex);
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
            notationHistoryArea.setText("");
            refreshNotationPanelVisibility(null);
            return;
        }
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
     * Resets each cell’s background to its base style, then in {@link GameState#PLAY} highlights the
     * selected origin square and legal step targets (orthogonal empty squares).
     */
    private void paintPlayHighlights(Game g) {
        for (int row = 0; row < BoardConstants.BOARD_SIZE; row++) {
            for (int col = 0; col < BoardConstants.BOARD_SIZE; col++) {
                StackPane cell = boardCells[row][col];
                CellData data = (CellData) cell.getUserData();
                Rectangle bg = data.background();
                bg.setFill(data.baseFill());
                bg.setStroke(data.baseStroke());
                bg.setStrokeWidth(data.baseStrokeWidth());
            }
        }
        if (g == null || g.getState() != GameState.PLAY || playNextFrom == null) {
            return;
        }
        CellData selected = cellDataAt(playNextFrom);
        Rectangle selBg = selected.background();
        selBg.setFill(selected.baseFill().interpolate(Color.web("#ffec99"), 0.42));
        selBg.setStroke(Color.web("#b8860b"));
        selBg.setStrokeWidth(3);
        if (playPartialMove.getSteps().size() >= 4) {
            return;
        }
        for (Position to : computeLegalPlayTargetsForSelection(g)) {
            CellData tdata = cellDataAt(to);
            Rectangle tbg = tdata.background();
            tbg.setFill(tdata.baseFill().interpolate(Color.web("#a8f0c0"), 0.48));
            tbg.setStroke(Color.web("#1e7a3a"));
            tbg.setStrokeWidth(2.5);
        }
        for (Position opp : computePullDragTargets(g)) {
            CellData odata = cellDataAt(opp);
            Rectangle obg = odata.background();
            obg.setStroke(Color.web("#b030c0"));
            obg.setStrokeWidth(3);
        }
    }

    private CellData cellDataAt(Position pos) {
        int row = BoardConstants.BOARD_SIZE - 1 - pos.getRankIndex();
        int col = pos.getFileIndex();
        return (CellData) boardCells[row][col].getUserData();
    }

    private Set<Position> computeLegalPlayTargetsForSelection(Game g) {
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
            occ = DefaultRuleEngine.simulatePlayPrefix(g, playPartialMove);
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
                Move trial = copyMove(playPartialMove);
                trial.getSteps().add(copyStep(step));
                if (DefaultRuleEngine.isValidPlayPrefix(g, trial)) {
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
                Move trial = copyMove(playPartialMove);
                for (Step st : bundle) {
                    trial.getSteps().add(copyStep(st));
                }
                if (DefaultRuleEngine.isValidPlayPrefix(g, trial)) {
                    out.add(bundle.get(0).getTo());
                }
            }
        }
        return out;
    }

    /**
     * Opponent squares whose piece may complete a pull-drag after the last step was a slide vacating
     * {@link Step#getFrom()}.
     */
    private Set<Position> computePullDragTargets(Game g) {
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
            Move trial = copyMove(playPartialMove);
            trial.getSteps().add(copyStep(drag));
            if (DefaultRuleEngine.isValidPlayPrefix(g, trial)) {
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
                Position pos = Position.of(data.fileIndex(), data.rankIndex());
                Piece p = effectivePieceAt(g, pos);
                if (pieceSkin == PieceSkin.NONE) {
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
            if (pieceSkin == PieceSkin.DEFAULT && setup && n > 0) {
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
            playEndTurnButton.setDisable(!play || playPartialMove.getSteps().isEmpty());
        }
        if (playCancelTurnButton != null) {
            boolean canCancelNormally = play
                    && (!playPartialMove.getSteps().isEmpty() || playNextFrom != null);
            boolean trapBlocksCancel = forbidCancelAfterOwnTrapItem != null
                    && forbidCancelAfterOwnTrapItem.isSelected()
                    && partialTurnOwnPieceTrapped(g);
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
            notationHistoryArea.setText("");
            return;
        }
        List<String> lines = new ArrayList<>(gameController.notationLinesVisible());
        Game g = game();
        if (g != null && g.getState() == GameState.PLAY && !playPartialMove.getSteps().isEmpty()) {
            String prefix = gameController.nextPlayNotationPrefix();
            String draft = ArimaaNotation.formatPartialTurnLine(g.getBoard(), playPartialMove, prefix);
            lines.add(draft);
        }
        notationHistoryArea.setText(String.join("\n", lines));
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
            if (pieceSkin == PieceSkin.DEFAULT) {
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
     * Whether the current partial move prefix removes at least one friendly piece via trap (preview on board copy).
     */
    private boolean partialTurnOwnPieceTrapped(Game g) {
        if (g.getState() != GameState.PLAY || playPartialMove.getSteps().isEmpty()) {
            return false;
        }
        DefaultRuleEngine.TrapCapturePreview p = DefaultRuleEngine.trapCapturesIfPrefixApplied(g, playPartialMove);
        PlayerSide side = g.getSideToMove();
        return side == PlayerSide.GOLD ? !p.bySilver().isEmpty() : !p.byGold().isEmpty();
    }

    /** Committed trap captures plus victims from the in-progress turn prefix (same ordering as events). */
    private List<PieceType> effectiveTrapCapturesForDisplay(Game g, PlayerSide capturer) {
        List<PieceType> out = new ArrayList<>(g.getTrapCapturesSnapshot(capturer));
        if (g.getState() == GameState.PLAY && !playPartialMove.getSteps().isEmpty()) {
            DefaultRuleEngine.TrapCapturePreview p = DefaultRuleEngine.trapCapturesIfPrefixApplied(g, playPartialMove);
            out.addAll(capturer == PlayerSide.GOLD ? p.byGold() : p.bySilver());
        }
        return out;
    }

    private void refreshHandLabel(Game g) {
        if (g.getState() == GameState.PLAY) {
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
        if (pieceSkin == PieceSkin.DEFAULT) {
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
            g.startNewGame();
            if (gameController != null) {
                gameController.resetTimeline();
            }
            setStatus("Nová hra — rozestavuje Gold.");
            refreshAll();
        }
    }

    private void recordTimeline() {
        recordTimeline(null);
    }

    private void recordTimeline(String playNotationLineOrNull) {
        if (gameController != null) {
            gameController.recordAfterMutation(playNotationLineOrNull);
        }
    }

    private void refreshHistoryMenus() {
        if (undoMenuItem != null) {
            undoMenuItem.setDisable(gameController == null || !gameController.canUndo());
        }
        if (redoMenuItem != null) {
            redoMenuItem.setDisable(gameController == null || !gameController.canRedo());
        }
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

    private void clearPlayTurnUi() {
        playPartialMove.getSteps().clear();
        playNextFrom = null;
    }

    private Piece effectivePieceAt(Game g, Position pos) {
        if (g.getState() != GameState.PLAY || playPartialMove.getSteps().isEmpty()) {
            return g.getBoard().getPiece(pos);
        }
        try {
            Map<Position, Piece> occ = DefaultRuleEngine.simulatePlayPrefix(g, playPartialMove);
            return occ.get(pos);
        } catch (IllegalArgumentException ex) {
            return g.getBoard().getPiece(pos);
        }
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

    private void onBoardCellClickPlay(int fileIndex, int rankIndex) {
        Game g = game();
        if (g == null) {
            return;
        }
        Position pos = Position.of(fileIndex, rankIndex);
        PlayerSide side = g.getSideToMove();
        Piece at = effectivePieceAt(g, pos);
        if (at != null) {
            if (at.getSide() == side) {
                playNextFrom = pos;
                setStatus("Vybrána figura — volné pole = krok; po uvolnění můžete kliknout na fialově označenou soupeřovu figuru (tahnutí).");
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
                    Move trial = copyMove(playPartialMove);
                    trial.getSteps().add(copyStep(drag));
                    if (DefaultRuleEngine.isValidPlayPrefix(g, trial)) {
                        playPartialMove.getSteps().add(copyStep(drag));
                        playNextFrom = last.getTo();
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
            Move trialSlide = copyMove(playPartialMove);
            trialSlide.getSteps().add(copyStep(slide));
            if (DefaultRuleEngine.isValidPlayPrefix(g, trialSlide)) {
                playPartialMove.getSteps().add(copyStep(slide));
                playNextFrom = pos;
                setStatus("Krok přidán (" + playPartialMove.getSteps().size() + "/4). Konec tahu nebo další krok.");
                refreshAll();
                return;
            }
        }
        if (remaining >= 2) {
            Map<Position, Piece> occ;
            try {
                occ = DefaultRuleEngine.simulatePlayPrefix(g, playPartialMove);
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
                Move trial = copyMove(playPartialMove);
                for (Step st : bundle) {
                    trial.getSteps().add(copyStep(st));
                }
                if (!DefaultRuleEngine.isValidPlayPrefix(g, trial)) {
                    continue;
                }
                for (Step st : bundle) {
                    playPartialMove.getSteps().add(copyStep(st));
                }
                playNextFrom = endOwnSquareAfterBundle(bundle);
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
        if (playPartialMove.getSteps().isEmpty()) {
            setStatus("Přidejte aspoň jeden krok.");
            return;
        }
        String prefix = gameController.nextPlayNotationPrefix();
        Move submit = copyMove(playPartialMove);
        // Board unchanged until submit; formatFullTurn copies internally via buildArimaaNotationBody.
        String notationLine = ArimaaNotation.formatFullTurn(g.getBoard(), submit, prefix);
        log.debug("submitting play turn: {} steps", submit.getSteps().size());
        if (!gameController.submitHumanMove(submit)) {
            log.info("submitHumanMove rejected (illegal or invalid state)");
            setStatus("Tah není platný.");
            return;
        }
        clearPlayTurnUi();
        if (g.getState() == GameState.GAME_OVER) {
            PlayerSide w = g.getMatchWinner();
            log.info("play turn ended: GAME_OVER winner={}", w);
            setStatus(w == null ? "Konec hry." : ("Konec hry — vyhrál " + sideName(w) + "."));
        } else {
            log.info("play turn ended: state={} sideToMove={}", g.getState(), g.getSideToMove());
            setStatus("Tah proveden.");
        }
        recordTimeline(notationLine);
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
            int fileIndex,
            int rankIndex,
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
