package cz.cvut.fel.pjv.arimaa.ui;

import cz.cvut.fel.pjv.arimaa.controller.GameController;
import cz.cvut.fel.pjv.arimaa.logging.LoggingSupport;
import cz.cvut.fel.pjv.arimaa.model.DefaultRuleEngine;
import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.GameHistoryEvent;
import cz.cvut.fel.pjv.arimaa.model.enums.GameState;
import cz.cvut.fel.pjv.arimaa.model.Move;
import cz.cvut.fel.pjv.arimaa.model.Piece;
import cz.cvut.fel.pjv.arimaa.model.enums.PieceType;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;
import cz.cvut.fel.pjv.arimaa.model.Position;
import cz.cvut.fel.pjv.arimaa.model.Step;
import cz.cvut.fel.pjv.arimaa.model.PlayHalfTurn;
import cz.cvut.fel.pjv.arimaa.model.PlayTurnHistory;
import cz.cvut.fel.pjv.arimaa.util.BoardConstants;
import cz.cvut.fel.pjv.arimaa.util.HomeTerritory;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ListView;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.Background;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import ch.qos.logback.classic.Level;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Primary window: board, setup controls (manual placement, presets via {@link cz.cvut.fel.pjv.arimaa.model.SetupPresets}),
 * PLAY interaction (draft turns, notation panel, save/load). Scene keyboard is installed by {@link PlaySceneKeyHandler}.
 * Layout construction: {@link MainWindowLayoutBuilder}; SETUP vs PLAY actions: {@link SetupPhaseUiHandler}, {@link PlayPhaseUiHandler}.
 * Undo / redo: menu Tah (Ctrl+Z / Ctrl+Y) and timeline / draft stack.
 * Gameplay → Skin: subfolders of {@code images/figure_sets/} (see {@link FigureSvgRasterCache#discoverSkinDirectoryNames()}).
 */
public class MainController {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    private static final double RESERVE_ICON_MAX = 26;
    private static final double HAND_ICON_MAX = 28;
    private static final double CAPTURE_ICON_MAX = 36;

    GameController gameController;

    Stage stage;
    File lastUsedDir;

    /** Pixel size of the framed board (coordinates + frame); used for scaling. */
    private double framedOuterSize = 1.0;

    void setFramedOuterSize(double outer) {
        framedOuterSize = outer;
    }

    final Label statusLabel = new Label();
    private BoardGridView boardGrid;
    ArimaaSaveLoadSupport saveLoad;
    final Map<PieceType, Button> reserveButtons = new EnumMap<>(PieceType.class);
    Button cancelHandButton;
    Button randomButton;
    Button chessButton;
    Button doneButton;
    final Label handLabel = new Label();
    final ImageView handPieceGraphic = new ImageView();
    MenuItem undoMenuItem;
    MenuItem redoMenuItem;

    ToggleGroup logLevelToggleGroup;
    /** Avoid feedback when programmatically selecting the log-level radio matching {@link LoggingSupport#getCurrentLevel()}. */
    boolean suppressLogLevelSync;

    CheckMenuItem logToFileItem;
    /** Avoid firing {@link #logToFileItem} action when syncing from {@link LoggingSupport#isFileLoggingEnabled()}. */
    boolean suppressFileLogSync;

    final FigureSvgRasterCache figureRasterCache = new FigureSvgRasterCache();
    /** {@code null} = no piece images (letters only); otherwise subdirectory of {@value FigureSvgRasterCache#FIGURE_SETS_ROOT}. */
    String figureSkinFolder = FigureSvgRasterCache.FALLBACK_SKIN_NAME;

    /** When selected, draft cancel / in-turn Undo·Redo are disabled after any trap removal in the current prefix. */
    CheckMenuItem forbidCancelAfterTrapItem;

    /** Home square currently hovered during setup (ghost placement); both null if none. Visual row 0 = top of board. */
    Integer hoverFileIndex;
    Integer hoverVisualRow;

    /** Rank digits beside the board (columns 0 and 9); updated when board orientation changes. */
    final Label[] rankCoordLabelsLeft = new Label[BoardConstants.BOARD_SIZE];
    final Label[] rankCoordLabelsRight = new Label[BoardConstants.BOARD_SIZE];
    /** File letters above/below the board (rows 0 and 9); updated when board orientation changes. */
    final Label[] fileCoordLabelsTop = new Label[BoardConstants.BOARD_SIZE];
    final Label[] fileCoordLabelsBottom = new Label[BoardConstants.BOARD_SIZE];

    /** When selected, during PLAY/GAME_OVER the board orients so the side to move (or winner) is at the bottom edge. */
    CheckMenuItem rotateBoardToMoverItem;
    /** In {@link GameState#PLAY}: draft steps and cursors ({@link PlayTurnDraftState}). */
    final PlayTurnDraftState playDraft = new PlayTurnDraftState();

    private final PlayDraftUiCoordinator draftUi = new PlayDraftUiCoordinator(this);
    private final SetupPhaseUiHandler setupPhase = new SetupPhaseUiHandler(this);
    private final PlayPhaseUiHandler playPhase = new PlayPhaseUiHandler(this);

    BoardViewOrientation boardOrientation;

    Button playEndTurnButton;
    Button playCancelTurnButton;

    /** Setup reserve tray + piece-type buttons; hidden during PLAY. */
    VBox reserveBox;
    /** Trap captures display; visible in PLAY and GAME_OVER. */
    final VBox capturesBox = new VBox(6);
    /** Move notation history; visible only after setup (PLAY / GAME_OVER). */
    final VBox notationBox = new VBox(6);
    final FlowPane goldCapturesPane = new FlowPane(4, 4);
    final FlowPane silverCapturesPane = new FlowPane(4, 4);
    boolean suppressHistoryListEvents;
    private final ObservableList<String> notationHistoryItems = FXCollections.observableArrayList();
    final ListView<String> notationHistoryList = new ListView<>(notationHistoryItems);

    boolean pieceSkinUsesFigureArt() {
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

        boardOrientation = new BoardViewOrientation(
                () -> rotateBoardToMoverItem != null && rotateBoardToMoverItem.isSelected());

        boardGrid = new BoardGridView(this);
        saveLoad = new ArimaaSaveLoadSupport(this);

        MainWindowLayoutBuilder.MainWindowLayoutResult layout =
                MainWindowLayoutBuilder.buildSidePanelAndMenus(this);

        ScrollPane scroll = new ScrollPane(layout.sidePanel());
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setMinViewportWidth(240);
        scroll.setBackground(Background.EMPTY);
        scroll.setStyle("-fx-background-color: transparent;");

        StackPane framedBoard = boardGrid.buildFramedBoardWithPerimeterCoordinates();
        BoardGridView.BoardHostPane boardHost = new BoardGridView.BoardHostPane(framedBoard, framedOuterSize);
        boardHost.setMinWidth(0);
        boardHost.setMinHeight(0);
        boardHost.setMaxWidth(Double.MAX_VALUE);
        boardHost.setMaxHeight(Double.MAX_VALUE);
        HBox.setHgrow(boardHost, Priority.ALWAYS);

        HBox body = new HBox();
        body.setFillHeight(true);
        body.setAlignment(Pos.CENTER);
        body.getChildren().addAll(boardHost, scroll);

        BorderPane root = new BorderPane();
        root.setTop(layout.menuBar());
        root.setCenter(body);

        Scene scene = new Scene(root, 920, 640);
        scene.setFill(Color.rgb(236, 236, 238));
        PlaySceneKeyHandler.install(scene, this);
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

    void setHoverCell(int fileIndex, int visualRow) {
        hoverFileIndex = fileIndex;
        hoverVisualRow = visualRow;
        if (boardGrid != null) {
            boardGrid.paintHoverOverlay(game());
        }
    }

    void clearHoverCellIf(int fileIndex, int visualRow) {
        if (hoverFileIndex != null && hoverFileIndex == fileIndex
                && hoverVisualRow != null && hoverVisualRow == visualRow) {
            hoverFileIndex = null;
            hoverVisualRow = null;
            if (boardGrid != null) {
                boardGrid.paintHoverOverlay(game());
            }
        }
    }

    void onBoardCellClick(int fileIndex, int visualRow) {
        Game g = game();
        if (g == null || g.getBoard() == null) {
            return;
        }
        int modelFile = modelFileFromVisualCol(fileIndex, g);
        int modelRank = modelRankFromVisualRow(visualRow, g);
        GameState st = g.getState();
        if (st == GameState.PLAY) {
            playPhase.handlePlayBoardActivation(modelFile, modelRank);
            return;
        }
        if (st != GameState.SETUP_GOLD && st != GameState.SETUP_SILVER) {
            return;
        }
        setupPhase.onBoardCellClick(modelFile, modelRank);
    }

    void onPickReserve(PieceType type) {
        setupPhase.onPickReserve(type);
    }

    void refreshAll() {
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
        boardGrid.paintBoard(g);
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
        boardGrid.paintHoverOverlay(g);
    }

    void syncLogLevelMenuSelection() {
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

    void syncLogToFileMenuSelection() {
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

    int modelRankFromVisualRow(int visualRow, Game g) {
        return boardOrientation.modelRankFromVisualRow(visualRow, g);
    }

    int visualRowFromModelRank(int modelRank, Game g) {
        return boardOrientation.visualRowFromModelRank(modelRank, g);
    }

    /** Maps grid column from left ({@code 0}) to model file index ({@code a} = {@code 0}). */
    int modelFileFromVisualCol(int visualCol, Game g) {
        return boardOrientation.modelFileFromVisualCol(visualCol, g);
    }

    int visualColFromModelFile(int modelFile, Game g) {
        return boardOrientation.visualColFromModelFile(modelFile, g);
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

    private void paintPlayHighlights(Game g) {
        boardGrid.refreshAllSquareDecorations(g);
        if (g == null || g.getState() != GameState.PLAY || playDraft.nextFrom == null) {
            return;
        }
        PlayTargetBundle targets = playTargetBundle(g);
        PlayBoardHighlighter.paint(g, playDraft, targets, this::cellDataAt);
    }

    PlayTargetBundle playTargetBundle(Game g) {
        return PlayLegalTargetsSupport.compute(
                g, playDraft, this::reconcilePlayPartialWithHistoryView, this::isValidPlaySuffixFromViewHalfStart);
    }

    private BoardGridView.CellData cellDataAt(Position pos) {
        Game g = game();
        int visualRow = visualRowFromModelRank(pos.getRankIndex(), g);
        int visualCol = visualColFromModelFile(pos.getFileIndex(), g);
        return (BoardGridView.CellData) boardGrid.cells[visualRow][visualCol].getUserData();
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
                    && !playDraft.partial.getSteps().isEmpty();
            playEndTurnButton.setDisable(!canEnd);
        }
        if (playCancelTurnButton != null) {
            boolean canCancelNormally = play
                    && (!playDraft.partial.getSteps().isEmpty() || playDraft.nextFrom != null);
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
    boolean partialTurnAnyTrapRemoval(Game g) {
        if (g.getState() != GameState.PLAY || gameController == null || !gameController.getPlayHistory().isBootstrapped()) {
            return false;
        }
        PlayTurnHistory ph = gameController.getPlayHistory();
        if (ph.appliedPrefixSteps() <= 0) {
            return false;
        }
        PlayHalfTurn ht = ph.halfAt(ph.viewHalfIndex());
        Game probe = PlayDraftNotationSupport.probeGameFromMemento(ht.startSnap());
        Move m = new Move();
        for (int i = 0; i < ph.appliedPrefixSteps(); i++) {
            m.getSteps().add(PlayDraftNotationSupport.copyStep(ht.steps().get(i)));
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
            int n = playDraft.partial.getSteps().size();
            String mover = sideName(g.getSideToMove());
            handLabel.setText(
                    n == 0
                            ? "Tah (%s): žádné kroky (vyberte figuru)".formatted(mover)
                            : "Tah (%s): %d krok(ů)".formatted(mover, n));
            return;
        }
        Piece h = g.getSetupHand();
        if (h == null) {
            handLabel.setGraphic(null);
            handLabel.setContentDisplay(ContentDisplay.LEFT);
            handLabel.setText("V ruce: —");
            return;
        }
        String text = "V ruce: %s (%s)".formatted(abbrev(h), sideName(h.getSide()));
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
                yield w == null ? "konec hry" : "výhra %s".formatted(sideName(w));
            }
            default -> String.valueOf(g.getState());
        };
        if (g.getState() == GameState.GAME_OVER) {
            stage.setTitle("Arimaa – %s".formatted(phase));
        } else {
            stage.setTitle("Arimaa – %s | na tahu: %s".formatted(phase, sideName(g.getSideToMove())));
        }
    }

    void startNewGameAction() {
        Game g = game();
        if (g != null) {
            log.info("user action: new game");
            clearPlayTurnUi();
            setupPhase.resetChessPresetRotation();
            g.startNewGame();
            if (gameController != null) {
                gameController.resetTimeline();
            }
            setStatus("Nová hra — rozestavuje Gold.");
            refreshAll();
        }
    }

    /**
     * After a successful load the controller already restored the final position; refresh the UI.
     */
    void playbackLoadedHistory() {
        syncPlayPartialFromHistory();
        refreshAll();
        setStatus("Hra načtena ze souboru.");
    }

    void recordTimeline() {
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
            boolean canDraftMutationRedo = !trapLock && isTrailingDraftAtLiveEnd() && !draftUi.draftRedoSteps.isEmpty();
            boolean canNavRedo = !trapLock && gameController.canRedo();
            redo = canDraftMutationRedo || canNavRedo || (!trapLock && draftUi.hasCancelledDraftSnapshot());
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

    void appendHistory(GameHistoryEvent event) {
        if (gameController != null) {
            gameController.appendHistory(event);
        }
    }

    /** Clears draft-line redo and the snapshot used by Vpřed after a full draft cancel. */
    void discardDraftRedoBranch() {
        draftUi.discardRedoBranch();
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

    void performUndo() {
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
                    draftUi.draftRedoSteps.push(popped);
                    gameController.applyPlayHistoryViewToGame();
                    syncPlayPartialFromHistory();
                    appendHistory(new GameHistoryEvent.DraftStepUndone(playDraft.partial.getSteps().size()));
                    setStatus("Zpět — odstraněn poslední krok rozpracovaného tahu.");
                    refreshAll();
                    return;
                }
            }
            if (gameController.undo()) {
                draftUi.draftRedoSteps.clear();
                syncPlayPartialFromHistory();
                setStatus("Zpět — krok zpět v rámci tahu (náhled).");
                refreshAll();
            } else {
                setStatus("Začátek tahu — další Zpět: klikněte na předchozí řádek v Historii tahů.");
            }
        }
    }

    void performRedo() {
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
        if (isTrailingDraftAtLiveEnd() && draftUi.tryRedoDraftFromRedoStack()) {
            setStatus("Vpřed — krok obnoven.");
            refreshAll();
            return;
        }
        if (gameController.redo()) {
            draftUi.draftRedoSteps.clear();
            syncPlayPartialFromHistory();
            setStatus("Vpřed — krok vpřed v rámci tahu (náhled).");
            refreshAll();
            return;
        }
        if (draftUi.hasCancelledDraftSnapshot() && draftUi.redoCancelledDraft()) {
            setStatus("Vpřed — obnoven rozpracovaný tah.");
            refreshAll();
        }
    }

    void setStatus(String text) {
        statusLabel.setText(text);
    }

    Game game() {
        return gameController != null ? gameController.getGame() : null;
    }

    /**
     * {@link PlayTurnDraftState#partial} must always mirror {@link PlayTurnHistory#copyViewPrefixStepsTo}; if anything
     * mutates the history view without a matching sync, UI labels / legality checks drift from the real board.
     */
    void reconcilePlayPartialWithHistoryView() {
        if (gameController == null || !gameController.getPlayHistory().isBootstrapped()) {
            return;
        }
        Move expected = new Move();
        gameController.getPlayHistory().copyViewPrefixStepsTo(expected);
        if (playDraft.partial.getSteps().size() != expected.getSteps().size()) {
            syncPlayPartialFromHistory();
        }
    }

    /**
     * {@link GameController#applyPlayHistoryViewToGame()} already applied {@link PlayTurnDraftState#partial} to {@code g}'s
     * board — do not pass the full draft again to {@link DefaultRuleEngine#isValidPlayPrefix(Game, Move)} on {@code g}
     * (that would re-apply steps). Validate {@code appended} as the next leg(s) from the viewed half-turn start.
     */
    boolean isValidPlaySuffixFromViewHalfStart(Game g, List<Step> appended) {
        if (gameController == null) {
            return false;
        }
        return PlayDraftNotationSupport.isValidPlaySuffixFromViewHalfStart(
                gameController.getPlayHistory(), g, playDraft.partial, appended);
    }

    void syncPlayPartialFromHistory() {
        if (gameController == null || !gameController.getPlayHistory().isBootstrapped()) {
            return;
        }
        gameController.getPlayHistory().copyViewPrefixStepsTo(playDraft.partial);
        Game g = game();
        if (g != null) {
            playDraft.nextFrom = PlayDraftNotationSupport.playNextFromAfterPrefixSteps(playDraft.partial.getSteps());
        } else {
            playDraft.nextFrom = null;
        }
        if (playDraft.partial.getSteps().isEmpty()) {
            playDraft.activeSegmentOrigin = null;
        }
    }

    void beginPlayDraftMutationBeforeNewSteps() {
        if (gameController == null) {
            return;
        }
        PlayTurnHistory ph = gameController.getPlayHistory();
        if (ph.isBootstrapped() && ph.isViewOnTrailingDraftHalf()) {
            if (ph.appliedPrefixSteps() < ph.trailingUncommittedStepCount()) {
                ph.truncateTrailingDraftFrom(ph.appliedPrefixSteps());
            }
            ph.copyViewPrefixStepsTo(playDraft.partial);
        }
        discardDraftRedoBranch();
    }

    void syncPlayDraftFromPartialToHistoryAndApply() {
        if (gameController == null || !gameController.getPlayHistory().isBootstrapped()) {
            return;
        }
        gameController.getPlayHistory().replaceTrailingDraftStepsFromMove(playDraft.partial);
        gameController.applyPlayHistoryViewToGame();
    }

    private List<String> buildNotationHistoryLines() {
        if (gameController == null) {
            return new ArrayList<>();
        }
        return PlayDraftNotationSupport.buildNotationHistoryLines(
                gameController.getPlayHistory(), game(), gameController::nextPlayNotationPrefix);
    }

    void clearPlayTurnUi() {
        playDraft.clearPartialAndTurnPositions();
        draftUi.clearForNewTurnUi();
    }

    /** Same as tlačítko „Náhodně …“ — náhodné doplnění nebo přeřazení na domovských řadách. */
    void performRandomSetupPlacementAction() {
        setupPhase.performRandomSetupPlacementAction();
    }

    /** Same as „Šachová rozestavení“ — rotates among reversed / symmetric / MH / HH presets. */
    void applyChessMappedSetupFromUi() {
        setupPhase.applyChessMappedSetupFromUi();
    }

    /** Same as „Hotovo (ukončit rozestavení)“. */
    void tryCompleteSetupFromUi() {
        setupPhase.tryCompleteSetupFromUi();
    }

    /**
     * Clears the in-progress PLAY turn draft (same as „Zrušit rozpracovaný tah“ / Esc): all steps revert to „start of
     * turn“. Unlike step-by-step Zpět, this does not keep a partial prefix — the whole draft is cleared; Vpřed can
     * restore the snapshot held by {@link PlayDraftUiCoordinator}.
     *
     * @return {@code true} if the draft was cleared; {@code false} if nothing to cancel, wrong phase, or trap lock
     */
    boolean tryCancelPlayDraftFromUi() {
        return draftUi.tryCancelPlayDraftFromUi();
    }

    Piece effectivePieceAt(Game g, Position pos) {
        return g.getBoard().getPiece(pos);
    }

    /**
     * Ctrl+Tab / Ctrl+Shift+Tab: cycle own pieces only (clears pull/push keyboard focus). Use during push or pull to pick
     * another friendly piece without cycling orange / purple targets.
     */
    void advancePlayTabFocusOwnPiecesOnly(Game g, boolean reverse) {
        playPhase.advancePlayTabFocusOwnPiecesOnly(g, reverse);
    }

    /**
     * Tab / Shift+Tab: cycle pull targets, else push first-step targets, else own pieces.
     */
    void advancePlayTabFocus(Game g, boolean reverse) {
        playPhase.advancePlayTabFocus(g, reverse);
    }

    /** Space: complete pull using keyboard focus or first pull target in visual order. */
    void activatePlayPullFromKeyboard(Game g) {
        playPhase.activatePlayPullFromKeyboard(g);
    }

    /** Space: apply push bundle for keyboard focus or first push target in visual order. */
    void activatePlayPushFromKeyboard(Game g) {
        playPhase.activatePlayPushFromKeyboard(g);
    }

    /**
     * Model square (file/rank indices) activated during PLAY — same behaviour as a board click.
     * New steps are only accepted on the trailing draft row at the current history position (see
     * {@link PlayTurnHistory#isViewOnTrailingDraftHalf()}).
     */
    void handlePlayBoardActivation(int modelFile, int modelRank) {
        playPhase.handlePlayBoardActivation(modelFile, modelRank);
    }

    void tryEndPlayTurn() {
        playPhase.tryEndPlayTurn();
    }

    private static Map<PieceType, Integer> countReserve(Game g, PlayerSide side) {
        Map<PieceType, Integer> m = new EnumMap<>(PieceType.class);
        for (Piece p : g.getSetupReserveSnapshot(side)) {
            m.merge(p.getType(), 1, Integer::sum);
        }
        return m;
    }

    static String labelForReserveButton(PieceType type, int count) {
        return "%s × %d".formatted(abbrevType(type), count);
    }

    public static String abbrev(Piece p) {
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

    static String sideName(PlayerSide s) {
        return s == PlayerSide.GOLD ? "Gold" : "Silver";
    }
}
