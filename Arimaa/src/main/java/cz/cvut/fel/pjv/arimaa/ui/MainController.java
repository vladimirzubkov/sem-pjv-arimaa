package cz.cvut.fel.pjv.arimaa.ui;

import cz.cvut.fel.pjv.arimaa.ai.RandomTrapAvoidingMoveChooser;
import cz.cvut.fel.pjv.arimaa.controller.GameController;
import cz.cvut.fel.pjv.arimaa.logging.LoggingSupport;
import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.GameHistoryEvent;
import cz.cvut.fel.pjv.arimaa.model.GameMemento;
import cz.cvut.fel.pjv.arimaa.model.enums.GameState;
import cz.cvut.fel.pjv.arimaa.model.Move;
import cz.cvut.fel.pjv.arimaa.model.Piece;
import cz.cvut.fel.pjv.arimaa.model.enums.PieceType;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerControllerKind;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;
import cz.cvut.fel.pjv.arimaa.model.Position;
import cz.cvut.fel.pjv.arimaa.model.Step;
import cz.cvut.fel.pjv.arimaa.model.PlayTurnHistory;
import cz.cvut.fel.pjv.arimaa.model.PlayHalfTurn;
import cz.cvut.fel.pjv.arimaa.util.ArimaaNotation;
import cz.cvut.fel.pjv.arimaa.util.BoardConstants;
import javafx.animation.PauseTransition;
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

import javafx.util.Duration;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Primary window: board, setup controls (manual placement, presets via {@link cz.cvut.fel.pjv.arimaa.model.SetupPresets}),
 * PLAY interaction (draft turns, notation panel, save/load). Scene keyboard is installed by {@link PlaySceneKeyHandler}.
 * Layout construction: {@link MainWindowLayoutBuilder}; SETUP vs PLAY actions: {@link SetupPhaseUiHandler}, {@link PlayPhaseUiHandler}.
 * Undo / redo: menu Tah (Ctrl+Z / Ctrl+Y) and timeline / draft stack.
 * Gameplay → Skin: subfolders of {@code images/figure_sets/} (see {@link FigureSvgRasterCache#discoverSkinDirectoryNames()}).
 */
public class MainController implements BoardViewHost {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    private static final double HAND_ICON_MAX = 28;
    GameController gameController;

    Stage stage;
    File lastUsedDir;

    /** Pixel size of the framed board (coordinates + frame); used for scaling. */
    private double framedOuterSize = 1.0;

    @Override
    public void setFramedOuterSize(double outer) {
        framedOuterSize = outer;
    }

    @Override
    public void registerFileCoordLabels(int fileIndex, Label top, Label bottom) {
        fileCoordLabelsTop[fileIndex] = top;
        fileCoordLabelsBottom[fileIndex] = bottom;
    }

    @Override
    public void registerRankCoordLabels(int visualRow, Label left, Label right) {
        rankCoordLabelsLeft[visualRow] = left;
        rankCoordLabelsRight[visualRow] = right;
    }

    @Override
    public FigureSvgRasterCache figureRasterCache() {
        return figureRasterCache;
    }

    @Override
    public Integer getHoverFileIndex() {
        return hoverFileIndex;
    }

    @Override
    public Integer getHoverVisualRow() {
        return hoverVisualRow;
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

    private final MainUiViewModel uiViewModel = new MainUiViewModel();
    private SetupSidePanelController setupSidePanel;
    private PlaySidePanelController playSidePanel;

    private final PlayDraftUiCoordinator draftUi = new PlayDraftUiCoordinator(this);
    private final SetupPhaseUiHandler setupPhase = new SetupPhaseUiHandler(this);
    private final PlayPhaseUiHandler playPhase = new PlayPhaseUiHandler(this);

    private PlayerControllerKind goldPlayerKind = PlayerControllerKind.HUMAN;
    private PlayerControllerKind silverPlayerKind = PlayerControllerKind.HUMAN;
    private final Random computerRandom = new Random();
    private boolean computerActionPending;
    private final ExecutorService computerMoveExecutor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "arimaa-computer-move");
                t.setDaemon(true);
                return t;
            });
    /** Delay between animated computer steps (ms), 0–2000 from Gameplay menu. */
    private double computerStepDelayMs = 1000.0;

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
    final ObservableList<String> notationHistoryItems = FXCollections.observableArrayList();
    final ListView<String> notationHistoryList = new ListView<>(notationHistoryItems);

    @Override
    public boolean pieceSkinUsesFigureArt() {
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

        setupSidePanel = new SetupSidePanelController(this);
        playSidePanel = new PlaySidePanelController(this);
        uiViewModel.bindSidePanelVisibility(this);

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
        scene.getStylesheets()
                .add(Objects.requireNonNull(
                                MainController.class.getResource("/cz/cvut/fel/pjv/arimaa/arimaa-menus.css"),
                                "classpath:/cz/cvut/fel/pjv/arimaa/arimaa-menus.css")
                        .toExternalForm());
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

    @Override
    public void setHoverCell(int fileIndex, int visualRow) {
        hoverFileIndex = fileIndex;
        hoverVisualRow = visualRow;
        if (boardGrid != null) {
            boardGrid.paintHoverOverlay(game());
        }
    }

    @Override
    public void clearHoverCellIf(int fileIndex, int visualRow) {
        if (hoverFileIndex != null && hoverFileIndex == fileIndex
                && hoverVisualRow != null && hoverVisualRow == visualRow) {
            hoverFileIndex = null;
            hoverVisualRow = null;
            if (boardGrid != null) {
                boardGrid.paintHoverOverlay(game());
            }
        }
    }

    @Override
    public void onBoardCellClick(int fileIndex, int visualRow) {
        Game g = game();
        if (g == null || g.getBoard() == null) {
            return;
        }
        int modelFile = modelFileFromVisualCol(fileIndex, g);
        int modelRank = modelRankFromVisualRow(visualRow, g);
        GameState st = g.getState();
        if (st == GameState.PLAY) {
            if (isComputerControlled(g.getSideToMove())) {
                return;
            }
            playPhase.handlePlayBoardActivation(modelFile, modelRank);
            return;
        }
        if (!MainUiLayoutPhase.isSetup(g)) {
            return;
        }
        if (isComputerControlled(g.getSideToMove())) {
            return;
        }
        setupPhase.onBoardCellClick(modelFile, modelRank);
    }

    void onPickReserve(PieceType type) {
        Game g = game();
        if (g != null && isComputerControlled(g.getSideToMove())) {
            return;
        }
        setupPhase.onPickReserve(type);
    }

    void refreshAll() {
        Game g = game();
        uiViewModel.syncFromGame(g);
        if (g != null && !MainUiLayoutPhase.isPlay(g)) {
            clearPlayTurnUi();
        }
        if (g == null || stage == null) {
            notationHistoryItems.clear();
            return;
        }
        updateRankCoordLabels(g);
        updateFileCoordLabels(g);
        boardGrid.paintBoard(g);
        paintPlayHighlights(g);
        setupSidePanel.refreshReserveButtons(g);
        setupSidePanel.refreshSetupActionButtons(g);
        playSidePanel.refreshPlayActionButtons(g);
        playSidePanel.refreshCapturedPanel(g);
        playSidePanel.refreshNotationHistory();
        refreshHandLabel(g);
        updateWindowTitle(g);
        refreshHistoryMenus();
        boardGrid.paintHoverOverlay(g);
        scheduleComputerTurnIfNeeded();
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

    @Override
    public int modelRankFromVisualRow(int visualRow, Game g) {
        return boardOrientation.modelRankFromVisualRow(visualRow, g);
    }

    int visualRowFromModelRank(int modelRank, Game g) {
        return boardOrientation.visualRowFromModelRank(modelRank, g);
    }

    /** Maps grid column from left ({@code 0}) to model file index ({@code a} = {@code 0}). */
    @Override
    public int modelFileFromVisualCol(int visualCol, Game g) {
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
        if (g == null || !MainUiLayoutPhase.isPlay(g) || playDraft.nextFrom == null) {
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

    /**
     * Whether the displayed PLAY prefix removes a piece via trap (for gameplay / cancel rules).
     */
    boolean viewPrefixRemovesPieceViaTrap() {
        Game g = game();
        if (g == null || gameController == null) {
            return false;
        }
        return gameController.getPlayHistory().viewPrefixRemovesPieceViaTrap(g);
    }

    /** Gameplay option: block cancel / draft undo·redo while this holds. */
    private boolean draftEditsBlockedByTrapMenuOption() {
        return forbidCancelAfterTrapItem != null
                && forbidCancelAfterTrapItem.isSelected()
                && viewPrefixRemovesPieceViaTrap();
    }

    private void refreshHandLabel(Game g) {
        if (g.getState() == GameState.PLAY) {
            reconcilePlayPartialWithHistoryView();
            int n = playDraft.partial.getSteps().size();
            String mover = sideName(g.getSideToMove());
            String text =
                    n == 0
                            ? "Tah (%s): žádné kroky (vyberte figuru)".formatted(mover)
                            : "Tah (%s): %d krok(ů)".formatted(mover, n);
            updateHandLabel(null, text);
            return;
        }
        Piece h = g.getSetupHand();
        if (h == null) {
            updateHandLabel(null, "V ruce: —");
            return;
        }
        String text =
                "V ruce: %s (%s)"
                        .formatted(String.valueOf(h.getType().notationChar()), sideName(h.getSide()));
        if (pieceSkinUsesFigureArt()) {
            Image hi = figureRasterCache.getRasterized(h.getSide(), h.getType(), HAND_ICON_MAX);
            updateHandLabel(hi, text);
        } else {
            updateHandLabel(null, text);
        }
    }

    private void updateHandLabel(Image imageOrNull, String text) {
        if (imageOrNull != null) {
            handPieceGraphic.setImage(imageOrNull);
            handPieceGraphic.setFitWidth(HAND_ICON_MAX);
            handPieceGraphic.setFitHeight(HAND_ICON_MAX);
            handLabel.setGraphic(handPieceGraphic);
        } else {
            handLabel.setGraphic(null);
        }
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
        } else {
            GameUiPhaseSnapshot phase = GameUiPhaseSnapshot.from(g);
            if (phase.setup()) {
                undo = gameController.canUndo();
                redo = gameController.canRedo();
            } else if (phase.play()) {
                boolean trapLock = draftEditsBlockedByTrapMenuOption();
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
        if (MainUiLayoutPhase.isSetup(g)) {
            if (gameController.undo()) {
                setStatus("Zpět — vrácen předchozí stav.");
                refreshAll();
            }
            return;
        }
        if (MainUiLayoutPhase.isPlay(g)) {
            if (draftEditsBlockedByTrapMenuOption()) {
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
        if (MainUiLayoutPhase.isSetup(g)) {
            if (gameController.redo()) {
                setStatus("Vpřed — obnoven stav.");
                refreshAll();
            }
            return;
        }
        if (!MainUiLayoutPhase.isPlay(g)) {
            return;
        }
        if (draftEditsBlockedByTrapMenuOption()) {
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

    PlayerControllerKind playerControllerKind(PlayerSide side) {
        return side == PlayerSide.GOLD ? goldPlayerKind : silverPlayerKind;
    }

    void setGoldPlayerKind(PlayerControllerKind kind) {
        goldPlayerKind = Objects.requireNonNull(kind, "kind");
    }

    void setSilverPlayerKind(PlayerControllerKind kind) {
        silverPlayerKind = Objects.requireNonNull(kind, "kind");
    }

    boolean isComputerControlled(PlayerSide side) {
        return playerControllerKind(side) == PlayerControllerKind.COMPUTER_LEVEL_0;
    }

    public double getComputerStepDelayMs() {
        return computerStepDelayMs;
    }

    public void setComputerStepDelayMs(double ms) {
        computerStepDelayMs = Math.max(0, Math.min(2000, ms));
    }

    private void scheduleComputerTurnIfNeeded() {
        Game g = game();
        if (g == null || stage == null || computerActionPending) {
            return;
        }
        if (!shouldOfferComputerStep(g)) {
            return;
        }
        computerActionPending = true;
        if (g.getState() == GameState.SETUP_GOLD || g.getState() == GameState.SETUP_SILVER) {
            Platform.runLater(() -> {
                try {
                    Game g2 = game();
                    if (g2 != null && shouldOfferComputerStep(g2)) {
                        runComputerSetupStep(g2);
                    }
                } finally {
                    computerActionPending = false;
                }
                refreshAll();
            });
            return;
        }
        if (g.getState() != GameState.PLAY || gameController == null) {
            computerActionPending = false;
            return;
        }
        PlayTurnHistory ph = gameController.getPlayHistory();
        if (!ph.isBootstrapped()) {
            computerActionPending = false;
            return;
        }
        PlayHalfTurn last = ph.halfAt(ph.halfTurnsUnmodifiable().size() - 1);
        if (last.committed()) {
            computerActionPending = false;
            return;
        }
        final GameMemento startSnap = last.startSnap();
        computerMoveExecutor.execute(() -> {
            try {
                Game probe = Game.restoredFromMemento(startSnap);
                Move chosen = RandomTrapAvoidingMoveChooser.chooseMove(probe, computerRandom);
                Platform.runLater(() -> beginComputerPlayAnimation(chosen));
            } catch (IllegalStateException ex) {
                log.warn("computer play: no legal moves ({})", ex.getMessage());
                Platform.runLater(() -> {
                    computerActionPending = false;
                    setStatus("Počítač — žádný platný tah.");
                    refreshAll();
                });
            } catch (Exception ex) {
                log.warn("computer play: move selection failed", ex);
                Platform.runLater(() -> {
                    computerActionPending = false;
                    setStatus("Počítač — výběr tahu selhal.");
                    refreshAll();
                });
            }
        });
    }

    private boolean shouldOfferComputerStep(Game g) {
        return switch (g.getState()) {
            case SETUP_GOLD -> isComputerControlled(PlayerSide.GOLD);
            case SETUP_SILVER -> isComputerControlled(PlayerSide.SILVER);
            case PLAY -> isComputerControlled(g.getSideToMove());
            default -> false;
        };
    }

    /**
     * Fills the mover's home from reserve: with probability {@code 0.2} applies one chess-mapped preset
     * ({@link Game#applyChessMappedSetup(PlayerSide, int)} — classic or one of {@link Game#CHESS_SETUP_ROTATION_COUNT}
     * rotating layouts), otherwise {@link Game#placeRemainingPiecesRandomly(PlayerSide)}. If the preset fails,
     * falls back to random placement.
     */
    private void runComputerSetupStep(Game g) {
        PlayerSide side = g.getSideToMove();
        boolean placed;
        if (ThreadLocalRandom.current().nextDouble() < 0.2) {
            int r = ThreadLocalRandom.current().nextInt(Game.CHESS_SETUP_ROTATION_COUNT + 1);
            int preset = (r == Game.CHESS_SETUP_ROTATION_COUNT) ? -1 : r;
            placed = g.applyChessMappedSetup(side, preset);
            if (!placed) {
                placed = g.placeRemainingPiecesRandomly(side);
            }
        } else {
            placed = g.placeRemainingPiecesRandomly(side);
        }
        if (!placed) {
            setStatus("Počítač — rozestavení se nepovedlo.");
            return;
        }
        recordTimeline();
        if (!g.tryCompleteSetup(side)) {
            setStatus("Počítač — rozestavení nelze dokončit (pravidla multisetu).");
            return;
        }
        if (g.getState() == GameState.PLAY) {
            gameController.enterPlayPhaseBootstrap();
            setStatus("Hra — na tahu %s.".formatted(sideName(g.getSideToMove())));
        } else {
            recordTimeline();
            setStatus("Nová hra — rozestavuje %s.".formatted(sideName(g.getSideToMove())));
        }
    }

    private void beginComputerPlayAnimation(Move chosen) {
        Game g = game();
        if (g == null
                || gameController == null
                || g.getState() != GameState.PLAY
                || !isComputerControlled(g.getSideToMove())) {
            computerActionPending = false;
            refreshAll();
            return;
        }
        int n = chosen.getSteps().size();
        if (n == 0) {
            finishComputerPlayCommit(chosen);
            return;
        }
        animateComputerPlayStep(chosen, 1, n);
    }

    private void animateComputerPlayStep(Move full, int k, int n) {
        Game g = game();
        if (g == null || gameController == null) {
            computerActionPending = false;
            refreshAll();
            return;
        }
        PlayTurnHistory ph = gameController.getPlayHistory();
        int tailIdx = ph.halfTurnsUnmodifiable().size() - 1;
        if (k == 1) {
            gameController.restoreTrailingDraftTurnStartForSubmit();
            ph.replaceTrailingDraftStepsFromMove(full);
        }
        ph.setViewPrefix(tailIdx, k);
        gameController.applyPlayHistoryViewToGame();
        syncPlayPartialFromHistory();
        refreshAll();
        long delayMs = Math.round(Math.max(0, Math.min(2000, computerStepDelayMs)));
        PauseTransition pause = new PauseTransition(Duration.millis(delayMs));
        if (k < n) {
            pause.setOnFinished(e -> animateComputerPlayStep(full, k + 1, n));
        } else {
            pause.setOnFinished(e -> finishComputerPlayCommit(full));
        }
        pause.play();
    }

    private void finishComputerPlayCommit(Move full) {
        try {
            Game g = game();
            if (g == null || gameController == null) {
                return;
            }
            gameController.restoreTrailingDraftTurnStartForSubmit();
            Move submit = PlayDraftNotationSupport.copyMove(full);
            String prefix = gameController.nextPlayNotationPrefix();
            String notationLine = ArimaaNotation.formatFullTurn(g.getBoard(), submit, prefix);
            if (!gameController.submitHumanMove(submit)) {
                log.info("computer play: submit rejected");
                setStatus("Počítač — tah nebyl přijat.");
                gameController.applyPlayHistoryViewToGame();
                return;
            }
            gameController.recordCommittedPlayTurn(submit, notationLine);
            clearPlayTurnUi();
            syncPlayPartialFromHistory();
            if (g.getState() == GameState.GAME_OVER) {
                PlayerSide w = g.getMatchWinner();
                setStatus(w == null ? "Konec hry." : "Konec hry — vyhrál %s.".formatted(sideName(w)));
            } else {
                setStatus("Tah počítače proveden.");
            }
            appendHistory(new GameHistoryEvent.TurnCommitted(notationLine));
        } finally {
            computerActionPending = false;
        }
        refreshAll();
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
     * board — do not pass the full draft again to {@code DefaultRuleEngine.isValidPlayPrefix} on {@code g}
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

    List<String> buildNotationHistoryLinesForSidePanel() {
        return buildNotationHistoryLines();
    }

    void clearPlayTurnUi() {
        playDraft.clearPartialAndTurnPositions();
        draftUi.clearForNewTurnUi();
    }

    /** Same as tlačítko „Náhodně …“ — náhodné doplnění nebo přeřazení na domovských řadách. */
    void performRandomSetupPlacementAction() {
        Game g = game();
        if (g != null && isComputerControlled(g.getSideToMove())) {
            return;
        }
        setupPhase.performRandomSetupPlacementAction();
    }

    /** Same as „Šachová rozestavení“ — rotates among reversed / symmetric / MH / HH presets. */
    void applyChessMappedSetupFromUi() {
        Game g = game();
        if (g != null && isComputerControlled(g.getSideToMove())) {
            return;
        }
        setupPhase.applyChessMappedSetupFromUi();
    }

    /** Same as „Hotovo (ukončit rozestavení)“. */
    void tryCompleteSetupFromUi() {
        Game g = game();
        if (g != null && isComputerControlled(g.getSideToMove())) {
            return;
        }
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

    @Override
    public Piece effectivePieceAt(Game g, Position pos) {
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
        Game g = game();
        if (g != null
                && g.getState() == GameState.PLAY
                && isComputerControlled(g.getSideToMove())) {
            return;
        }
        playPhase.handlePlayBoardActivation(modelFile, modelRank);
    }

    void tryEndPlayTurn() {
        Game g = game();
        if (g != null && isComputerControlled(g.getSideToMove())) {
            return;
        }
        playPhase.tryEndPlayTurn();
    }

    static String labelForReserveButton(PieceType type, int count) {
        return "%s × %d".formatted(String.valueOf(type.notationChar()), count);
    }

    static String sideName(PlayerSide s) {
        return s == PlayerSide.GOLD ? "Gold" : "Silver";
    }
}
