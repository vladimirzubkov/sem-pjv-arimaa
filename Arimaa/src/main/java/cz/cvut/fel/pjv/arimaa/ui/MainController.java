package cz.cvut.fel.pjv.arimaa.ui;

import cz.cvut.fel.pjv.arimaa.controller.GameController;
import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.GameState;
import cz.cvut.fel.pjv.arimaa.model.Piece;
import cz.cvut.fel.pjv.arimaa.model.PieceType;
import cz.cvut.fel.pjv.arimaa.model.PlayerSide;
import cz.cvut.fel.pjv.arimaa.model.Position;
import cz.cvut.fel.pjv.arimaa.util.BoardConstants;
import cz.cvut.fel.pjv.arimaa.util.HomeTerritory;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeType;
import javafx.scene.text.Font;
import javafx.scene.transform.Scale;
import javafx.stage.Stage;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Primary window: board and setup controls (manual, random, chess layout, finish setup).
 */
public class MainController {

    private static final int CELL = 52;
    /** Gap between adjacent columns/rows on the unified board {@link GridPane}. */
    private static final int BOARD_GAP = 1;
    /** Coordinate strip width/height outside the 8×8 cells. */
    private static final int COORD = 24;
    /** Space between outer frame and coordinate grid. */
    private static final int FRAME_INSET = 10;
    private static final Font CELL_FONT = Font.font(18);
    private static final Font COORD_FONT = Font.font(12);

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
    private MenuItem undoMenuItem;
    private MenuItem redoMenuItem;

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

        VBox reserveBox = new VBox(6, new Label("Rezerva (klik = vzít figuru):"));
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
            if (g.placeRemainingPiecesRandomly(side)) {
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

        handLabel.setWrapText(true);
        handLabel.setMaxWidth(220);

        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(240);
        setStatus("Rozestavte Gold; pak Hotovo. Silver totéž.");

        VBox sidePanel = new VBox(10,
                new Label("Stav:"),
                statusLabel,
                handLabel,
                spacer(8),
                reserveBox,
                cancelHandButton,
                randomButton,
                chessButton,
                doneButton);
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
                setStatus("Zpět — vrácen předchozí stav.");
                refreshAll();
            }
        });
        redoMenuItem = new MenuItem("Vpřed");
        redoMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN));
        redoMenuItem.setOnAction(e -> {
            if (gameController != null && gameController.redo()) {
                setStatus("Vpřed — obnoven stav.");
                refreshAll();
            }
        });
        menuTah.getItems().addAll(undoMenuItem, redoMenuItem);

        MenuBar menuBar = new MenuBar();
        menuBar.getMenus().addAll(menuHra, menuTah);

        BorderPane root = new BorderPane();
        root.setTop(menuBar);
        root.setCenter(body);

        Scene scene = new Scene(root, 920, 640);
        scene.setFill(Color.rgb(236, 236, 238));
        primaryStage.setTitle("Arimaa – rozestavení");
        primaryStage.setScene(scene);
        primaryStage.show();

        if (gameController != null) {
            gameController.resetTimeline();
        }
        refreshAll();
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
        bg.setStroke(Color.gray(0.35));
        bg.setStrokeWidth(1);
        int rankIndex = BoardConstants.BOARD_SIZE - 1 - gridRow;
        Position pos = Position.of(fileIndex, rankIndex);
        if (HomeTerritory.contains(PlayerSide.GOLD, pos, false)
                || HomeTerritory.contains(PlayerSide.SILVER, pos, false)) {
            bg.setFill(Color.color(0.75, 0.82, 0.95));
        } else {
            bg.setFill(gridRow % 2 == fileIndex % 2 ? Color.color(0.93, 0.88, 0.78) : Color.color(0.85, 0.78, 0.65));
        }
        if (isStaticTrapSquare(pos)) {
            bg.setStroke(Color.DARKRED);
            bg.setStrokeWidth(2);
        }

        Label pieceLbl = new Label("");
        pieceLbl.setFont(CELL_FONT);
        StackPane cell = new StackPane(bg, pieceLbl);
        cell.setUserData(new CellData(fileIndex, rankIndex, pieceLbl));

        cell.setOnMouseClicked(e -> onBoardCellClick(fileIndex, rankIndex));
        return cell;
    }

    private void onBoardCellClick(int fileIndex, int rankIndex) {
        Game g = game();
        if (g == null || g.getBoard() == null) {
            return;
        }
        GameState st = g.getState();
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
        if (g == null || stage == null) {
            return;
        }
        paintBoard(g);
        refreshReserveButtons(g);
        refreshActionButtons(g);
        refreshHandLabel(g);
        updateWindowTitle(g);
        refreshHistoryMenus();
    }

    private void paintBoard(Game g) {
        for (int row = 0; row < BoardConstants.BOARD_SIZE; row++) {
            for (int col = 0; col < BoardConstants.BOARD_SIZE; col++) {
                StackPane cell = boardCells[row][col];
                CellData data = (CellData) cell.getUserData();
                Position pos = Position.of(data.fileIndex, data.rankIndex);
                Piece p = g.getBoard().getPiece(pos);
                data.pieceLabel.setText(p == null ? "" : abbrev(p));
                data.pieceLabel.setTextFill(p == null ? Color.BLACK
                        : (p.getSide() == PlayerSide.GOLD ? Color.color(0.55, 0.35, 0.05) : Color.color(0.25, 0.25, 0.35)));
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
        }
    }

    private void refreshActionButtons(Game g) {
        boolean setup = g.getState() == GameState.SETUP_GOLD || g.getState() == GameState.SETUP_SILVER;
        PlayerSide side = g.getSideToMove();
        cancelHandButton.setDisable(!setup || g.getSetupHand() == null);
        chessButton.setDisable(!setup);
        doneButton.setDisable(!setup);
        boolean canRandom = setup && reserveSizesMatchEmptyHome(g, side);
        randomButton.setDisable(!setup || !canRandom);
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

    private void refreshHandLabel(Game g) {
        Piece h = g.getSetupHand();
        if (h == null) {
            handLabel.setText("V ruce: —");
        } else {
            handLabel.setText("V ruce: " + abbrev(h) + " (" + sideName(h.getSide()) + ")");
        }
    }

    private void updateWindowTitle(Game g) {
        String phase = switch (g.getState()) {
            case SETUP_GOLD -> "rozestavení Gold";
            case SETUP_SILVER -> "rozestavení Silver";
            case PLAY -> "hra (tahy zatím v modelu)";
            default -> String.valueOf(g.getState());
        };
        stage.setTitle("Arimaa – " + phase + " | na tahu: " + sideName(g.getSideToMove()));
    }

    private void startNewGameAction() {
        Game g = game();
        if (g != null) {
            g.startNewGame();
            if (gameController != null) {
                gameController.resetTimeline();
            }
            setStatus("Nová hra — rozestavuje Gold.");
            refreshAll();
        }
    }

    private void recordTimeline() {
        if (gameController != null) {
            gameController.recordAfterMutation();
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

    private record CellData(int fileIndex, int rankIndex, Label pieceLabel) {
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
