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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Primary window: board and setup controls (manual, random, chess layout, finish setup).
 */
public class MainController {

    private static final int CELL = 52;
    private static final Font CELL_FONT = Font.font(18);

    private GameController gameController;

    private Stage stage;
    private final Label statusLabel = new Label();
    private final GridPane boardGrid = new GridPane();
    private final StackPane[][] boardCells = new StackPane[BoardConstants.BOARD_SIZE][BoardConstants.BOARD_SIZE];
    private final Map<PieceType, Button> reserveButtons = new EnumMap<>(PieceType.class);
    private Button cancelHandButton;
    private Button randomButton;
    private Button chessButton;
    private Button doneButton;
    private final Label handLabel = new Label();

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
        boardGrid.setHgap(1);
        boardGrid.setVgap(1);
        boardGrid.setPadding(new Insets(8));

        for (int row = 0; row < BoardConstants.BOARD_SIZE; row++) {
            for (int col = 0; col < BoardConstants.BOARD_SIZE; col++) {
                StackPane cell = createCell(col, row);
                boardCells[row][col] = cell;
                boardGrid.add(cell, col, row);
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
                g.cancelPendingSetupPlacement();
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
            } else {
                setStatus("Rozestavení nelze dokončit (rezerva, multiset, 16 figurek na domově…).");
            }
            refreshAll();
        });

        Button newGameButton = new Button("Nová hra");
        newGameButton.setMaxWidth(Double.MAX_VALUE);
        newGameButton.setOnAction(e -> {
            Game g = game();
            if (g != null) {
                g.startNewGame();
                setStatus("Nová hra — rozestavuje Gold.");
                refreshAll();
            }
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
                doneButton,
                newGameButton);
        sidePanel.setPadding(new Insets(12));
        sidePanel.setPrefWidth(260);

        ScrollPane scroll = new ScrollPane(sidePanel);
        scroll.setFitToWidth(true);
        scroll.setMinViewportWidth(240);

        BorderPane root = new BorderPane();
        root.setCenter(boardGrid);
        root.setRight(scroll);
        BorderPane.setAlignment(boardGrid, Pos.CENTER);

        Scene scene = new Scene(root, 920, 640);
        primaryStage.setTitle("Arimaa – rozestavení");
        primaryStage.setScene(scene);
        primaryStage.show();

        refreshAll();
    }

    private StackPane createCell(int fileIndex, int gridRow) {
        Rectangle bg = new Rectangle(CELL, CELL);
        bg.setStroke(Color.gray(0.35));
        int rankIndex = BoardConstants.BOARD_SIZE - 1 - gridRow;
        Position pos = Position.of(fileIndex, rankIndex);
        if (HomeTerritory.contains(PlayerSide.GOLD, pos, false)
                || HomeTerritory.contains(PlayerSide.SILVER, pos, false)) {
            bg.setFill(Color.color(0.75, 0.82, 0.95));
        } else {
            bg.setFill(gridRow % 2 == fileIndex % 2 ? Color.color(0.93, 0.88, 0.78) : Color.color(0.85, 0.78, 0.65));
        }
        Game g0 = game();
        if (g0 != null && g0.getBoard() != null && g0.getBoard().isTrapSquare(pos)) {
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
            } else {
                setStatus("Sem nelze umístit (domov, kapacita typu nebo obsazené pole).");
            }
        } else {
            if (g.returnPieceFromBoardToReserve(side, pos)) {
                setStatus("Figura vrácena do rezervy.");
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
}
