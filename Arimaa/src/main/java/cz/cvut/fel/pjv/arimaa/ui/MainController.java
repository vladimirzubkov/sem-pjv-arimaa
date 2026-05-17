package cz.cvut.fel.pjv.arimaa.ui;

import cz.cvut.fel.pjv.arimaa.ai.ComputerPlayMove;
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
import cz.cvut.fel.pjv.arimaa.network.ArimaaNetworkCoordinator;
import cz.cvut.fel.pjv.arimaa.network.FxExecutor;
import cz.cvut.fel.pjv.arimaa.network.NetworkGameBridge;
import cz.cvut.fel.pjv.arimaa.network.IntentKind;
import cz.cvut.fel.pjv.arimaa.network.NetworkAssignmentCodec;
import cz.cvut.fel.pjv.arimaa.network.NetworkLocalAddresses;
import cz.cvut.fel.pjv.arimaa.network.NetworkRole;
import cz.cvut.fel.pjv.arimaa.network.WireMessages;
import cz.cvut.fel.pjv.arimaa.persistence.GameSerializer;
import cz.cvut.fel.pjv.arimaa.persistence.PlayNotationParser;
import cz.cvut.fel.pjv.arimaa.util.ArimaaNotation;
import cz.cvut.fel.pjv.arimaa.util.BoardConstants;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ListView;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;

import ch.qos.logback.classic.Level;


import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Primary window: board, setup controls (manual placement, presets via {@link cz.cvut.fel.pjv.arimaa.model.SetupPresets}),
 * PLAY interaction (draft turns, notation panel, save/load). Scene keyboard is installed by {@link PlaySceneKeyHandler}.
 * Layout construction: {@link MainWindowLayoutBuilder}; SETUP vs PLAY actions: {@link SetupPhaseUiHandler}, {@link PlayPhaseUiHandler}.
 * Undo / redo: menu Tah (Ctrl+Z / Ctrl+Y) and timeline / draft stack.
 * Gameplay → Skin: subfolders of {@code images/figure_sets/} (see {@link FigureSvgRasterCache#discoverSkinDirectoryNames()}).
 */
public class MainController implements BoardViewHost, NetworkGameBridge {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    private static final double HAND_ICON_MAX = 28;

    /** Minimum pause between animated computer steps (ms); enforced by UI slider and {@link #setComputerStepDelayMs}. */
    public static final double MIN_COMPUTER_STEP_DELAY_MS = 100.0;

    /** Maximum pause between animated computer steps (ms). */
    public static final double MAX_COMPUTER_STEP_DELAY_MS = 2000.0;

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

    /** Main status line under „Stav:“. */
    final Label statusLabel = new Label();
    /** Optional detail line below {@link #statusLabel} (e.g. load error reason). */
    final Label statusDetailLabel = new Label();
    /** Gold/Silver controller summary (human vs CPU level). */
    final Label playersAssignmentLabel = new Label();
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
    /** Gameplay: procedural wood / trap sounds during PLAY (default on). */
    CheckMenuItem gameplaySoundEnabledItem;
    /** Gameplay: during computer PLAY turn animation, grow the last notation line step-by-step (prefix length). */
    CheckMenuItem showComputerTurnStepsInNotationItem;

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
    /** When &gt; 0, {@link #recordTimeline()} / draft sync skip host echo; {@link #applyHostIntentFromNetwork} broadcasts once in {@code finally}. */
    private int networkIntentApplyDepth;

    /** When true, {@link #applyNetworkSnapshotSaveText} is rebuilding state — host must not echo snapshots. */
    private boolean applyingNetworkSnapshot;
    /** Lazily created; {@link #clearNetworkSessionAfterDisconnect} leaves the instance but role is {@link NetworkRole#NONE}. */
    private ArimaaNetworkCoordinator arimaaNetworkCoordinator;
    ToggleGroup goldPlayerMenuGroup;
    ToggleGroup silverPlayerMenuGroup;
    MenuItem networkHostMenuItem;
    MenuItem networkCancelHostWaitMenuItem;
    MenuItem networkConnectMenuItem;
    MenuItem networkDisconnectMenuItem;

    public static final int DEFAULT_NETWORK_PORT = 7788;

    /** In {@link GameState#PLAY}: draft steps and cursors ({@link PlayTurnDraftState}). */
    final PlayTurnDraftState playDraft = new PlayTurnDraftState();

    private final MainUiViewModel uiViewModel = new MainUiViewModel();
    private SetupSidePanelController setupSidePanel;
    private PlaySidePanelController playSidePanel;

    private final PlayDraftUiCoordinator draftUi = new PlayDraftUiCoordinator(this);
    private final SetupPhaseUiHandler setupPhase = new SetupPhaseUiHandler(this);
    private final PlayPhaseUiHandler playPhase = new PlayPhaseUiHandler(this);

    private PlayerControllerKind goldPlayerKind = PlayerControllerKind.HUMAN;
    private PlayerControllerKind silverPlayerKind = PlayerControllerKind.COMPUTER_LEVEL_1;
    /** Host UI: Silver assignment chosen by the peer. */
    private PlayerControllerKind networkPeerSilverKind;
    /** Client UI: Gold assignment chosen by the peer. */
    private PlayerControllerKind networkPeerGoldKind;
    /** Window title middle segment during síťová hra: {@code Server} / {@code Klient} ({@code null} when offline). */
    private String networkWindowPeerLabel;
    /** When true, Gameplay player {@link ToggleGroup} listeners skip broadcasting seat changes. */
    boolean suppressGameplayPlayerMenuCallback;
    private final Random computerRandom = new Random();
    private boolean computerActionPending;
    /** When true, do not start new computer setup / choose-move work; PLAY step animation uses {@link #computerPlayTurnTimeline} pause/play. */
    private boolean computerAutoplayPaused;
    /** Incremented when pausing without an active step timeline (move selection) or when clearing autoplay — invalidates in-flight {@code runLater} from {@link #computerMoveExecutor}. */
    private final AtomicLong computerPlayInvalidateGen = new AtomicLong();
    /**
     * One JavaFX {@link Timeline} per computer PLAY turn: step previews at cumulative delays, then commit. Delays do not
     * use a background scheduler, so step timing cannot race FX-thread state the way chained {@code ScheduledFuture}s can.
     */
    private Timeline computerPlayTurnTimeline;
    private Label computerPauseOverlay;
    private final ExecutorService computerMoveExecutor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "arimaa-computer-move");
                t.setDaemon(true);
                return t;
            });
    /** Delay between animated computer steps (ms), clamped 100–2000 from Gameplay menu. */
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
    /**
     * When {@code true}, the next {@link PlaySidePanelController#refreshNotationHistory(boolean)} inside
     * {@link #refreshAll()} scrolls the notation list to the selected row. Cleared before
     * {@link #applyNotationHistoryListSelection(int)} so clicks and Page Up/Down do not jump the scroll position.
     */
    private boolean notationHistoryAutoScrollOnNextRefresh = true;
    final ObservableList<String> notationHistoryItems = FXCollections.observableArrayList();
    final ListView<String> notationHistoryList = new ListView<>(notationHistoryItems);
    final Label clockGoldTotalLabel = new Label("00:00");
    final Label clockGoldAvgLabel = new Label("—");
    final Label clockSilverTotalLabel = new Label("00:00");
    final Label clockSilverAvgLabel = new Label("—");
    private PlayChessClockModel playChessClockModel;
    private PlayChessClockTicker playChessClockTicker;

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
                () -> rotateBoardToMoverItem != null && rotateBoardToMoverItem.isSelected(),
                this::isNetworkSessionActive,
                () -> isNetworkSessionActive() && isNetworkHost());

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
        scroll.setMinViewportWidth(200);
        scroll.setPrefViewportWidth(272);
        scroll.setMaxWidth(400);
        wireSidePanelTextWrapToViewport(layout.sidePanel());
        scroll.setBackground(Background.EMPTY);
        scroll.setStyle("-fx-background-color: transparent;");

        StackPane framedBoard = boardGrid.buildFramedBoardWithPerimeterCoordinates();
        PlayVictoryMediaSfx.installOnCellArea(boardGrid.victoryOverlayHost());
        BoardGridView.BoardHostPane boardHost = new BoardGridView.BoardHostPane(framedBoard, framedOuterSize);
        boardHost.setMinWidth(0);
        boardHost.setMinHeight(0);
        boardHost.setMaxWidth(Double.MAX_VALUE);
        boardHost.setMaxHeight(Double.MAX_VALUE);

        computerPauseOverlay = new Label("Pauza — mezerník pokračuje");
        computerPauseOverlay.setAlignment(Pos.CENTER);
        computerPauseOverlay.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        computerPauseOverlay.setWrapText(true);
        computerPauseOverlay.setMouseTransparent(true);
        computerPauseOverlay.setVisible(false);
        computerPauseOverlay.setManaged(false);
        computerPauseOverlay.setTextFill(Color.WHITE);
        computerPauseOverlay.setPadding(new Insets(16, 24, 16, 24));
        computerPauseOverlay.setBackground(new Background(new BackgroundFill(
                Color.color(0, 0, 0, 0.42), CornerRadii.EMPTY, Insets.EMPTY)));

        StackPane boardStack = new StackPane();
        boardStack.getChildren().addAll(boardHost, computerPauseOverlay);
        HBox.setHgrow(boardStack, Priority.ALWAYS);

        HBox body = new HBox();
        body.setFillHeight(true);
        body.setAlignment(Pos.CENTER);
        body.getChildren().addAll(boardStack, scroll);

        BorderPane root = new BorderPane();
        root.setTop(layout.menuBar());
        root.setCenter(body);

        Scene scene = new Scene(root, 920, 640);
        scene.setFill(Color.rgb(236, 236, 238));
        String menuCssUrl = Objects.requireNonNull(
                        MainController.class.getResource("/cz/cvut/fel/pjv/arimaa/arimaa-menus.css"),
                        "classpath:/cz/cvut/fel/pjv/arimaa/arimaa-menus.css")
                .toExternalForm();
        scene.getStylesheets().add(menuCssUrl);
        DevMenuCssHotReload.installIfEnabled(scene, menuCssUrl);
        PlaySceneKeyHandler.install(scene, this);
        primaryStage.setTitle("Arimaa – rozestavení");
        primaryStage.setScene(scene);
        primaryStage.show();
        Platform.runLater(() -> scene.getRoot().requestFocus());

        playChessClockModel = new PlayChessClockModel();
        playChessClockTicker =
                new PlayChessClockTicker(
                        playChessClockModel,
                        clockGoldTotalLabel,
                        clockGoldAvgLabel,
                        clockSilverTotalLabel,
                        clockSilverAvgLabel);
        playChessClockTicker.start();

        if (gameController != null) {
            gameController.resetTimeline();
        }
        refreshAll();
        syncLogLevelMenuSelection();
        syncLogToFileMenuSelection();
        syncNetworkMenuState();
    }

    /** Stops the background chess-clock ticker (e.g. on application exit). */
    public void shutdownPlayChessClock() {
        if (playChessClockTicker != null) {
            playChessClockTicker.stop();
            playChessClockTicker = null;
        }
        playChessClockModel = null;
    }

    void resetPlayChessClockToSetup() {
        if (playChessClockModel != null) {
            playChessClockModel.resetToSetup();
        }
    }

    /** Called when PLAY begins after setup (Gold moves first). */
    public void notifyPlayChessClockEnterPlay() {
        Game g = game();
        if (playChessClockModel == null || g == null || g.getState() != GameState.PLAY) {
            return;
        }
        playChessClockModel.enterPlayPhase(g.getSideToMove());
    }

    /** {@code mover} is the side that completed the turn (before {@code sideToMove} advances). */
    public void notifyPlayChessClockAfterCommittedTurn(PlayerSide mover) {
        Game g = game();
        if (playChessClockModel == null || g == null) {
            return;
        }
        playChessClockModel.onTurnCommitted(mover, g.getState() == GameState.GAME_OVER);
    }

    /** After loading a game or scrubbing the notation list: restart local clocks from the viewed position. */
    public void notifyPlayChessClockHistoryNavigation() {
        Game g = game();
        if (playChessClockModel == null || g == null) {
            return;
        }
        playChessClockModel.resetForHistoryOrLoad(g);
    }

    private void syncPlayChessClockCpuPause() {
        if (playChessClockModel == null) {
            return;
        }
        Game g = game();
        boolean suspend =
                g != null
                        && g.getState() == GameState.PLAY
                        && computerAutoplayPaused
                        && shouldOfferComputerStep(g);
        playChessClockModel.setCpuChargeSuspended(suspend);
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
            if (!isLocalInteractiveTurn(g)) {
                return;
            }
            if (isNetworkClient()) {
                arimaaNetwork()
                        .sendIntent(
                                new WireMessages.IntentMessage(
                                        IntentKind.PLAY_ACTIVATE, null, modelFile, modelRank, null));
                return;
            }
            playPhase.handlePlayBoardActivation(modelFile, modelRank);
            return;
        }
        if (!MainUiLayoutPhase.isSetup(g)) {
            return;
        }
        if (!isLocalInteractiveTurn(g)) {
            return;
        }
        if (isNetworkClient()) {
            arimaaNetwork()
                    .sendIntent(
                            new WireMessages.IntentMessage(
                                    IntentKind.SETUP_BOARD_CLICK, null, modelFile, modelRank, null));
            return;
        }
        setupPhase.onBoardCellClick(modelFile, modelRank);
    }

    void onPickReserve(PieceType type) {
        Game g = game();
        if (g != null && !isLocalInteractiveTurn(g)) {
            return;
        }
        if (isNetworkClient()) {
            arimaaNetwork().sendIntent(new WireMessages.IntentMessage(IntentKind.SETUP_PICK, type, null, null, null));
            return;
        }
        setupPhase.onPickReserve(type);
    }

    void onCancelSetupHandFromUi() {
        Game g = game();
        if (g != null && !isLocalInteractiveTurn(g)) {
            return;
        }
        if (isNetworkClient()) {
            arimaaNetwork().sendIntent(new WireMessages.IntentMessage(IntentKind.SETUP_CANCEL_HAND, null, null, null, null));
            return;
        }
        if (g != null) {
            boolean changed = g.getSetupHand() != null;
            g.cancelPendingSetupPlacement();
            if (changed) {
                recordTimeline();
            }
            refreshAll();
        }
    }

    private void refreshPlayersAssignmentLabel() {
        String goldLine =
                goldPlayerKind == PlayerControllerKind.NETWORK_PEER
                        ? (networkPeerGoldKind == null
                                ? "protihráč (síť)"
                                : "protihráč (síť) — " + networkPeerGoldKind.assignmentDescriptionCs())
                        : goldPlayerKind.assignmentDescriptionCs();
        String silverLine =
                silverPlayerKind == PlayerControllerKind.NETWORK_PEER
                        ? (networkPeerSilverKind == null
                                ? "protihráč (síť)"
                                : "protihráč (síť) — " + networkPeerSilverKind.assignmentDescriptionCs())
                        : silverPlayerKind.assignmentDescriptionCs();
        playersAssignmentLabel.setText("Gold: %s%nSilver: %s".formatted(goldLine, silverLine));
    }

    void refreshAll() {
        refreshPlayersAssignmentLabel();
        Game g = game();
        uiViewModel.syncFromGame(g);
        if (g != null && MainUiLayoutPhase.isSetup(g)) {
            clearPlayTurnUi();
        }
        if (g == null || stage == null) {
            notationHistoryItems.clear();
            return;
        }
        if (!shouldOfferComputerStep(g) && !computerActionPending) {
            clearComputerAutoplayPauseState();
        }
        updateRankCoordLabels(g);
        updateFileCoordLabels(g);
        boardGrid.paintBoard(g);
        paintPlayHighlights(g);
        setupSidePanel.refreshReserveButtons(g);
        setupSidePanel.refreshSetupActionButtons(g);
        playSidePanel.refreshPlayActionButtons(g);
        playSidePanel.refreshCapturedPanel(g);
        boolean nhScroll = notationHistoryAutoScrollOnNextRefresh;
        notationHistoryAutoScrollOnNextRefresh = true;
        playSidePanel.refreshNotationHistory(nhScroll);
        refreshHandLabel(g);
        updateWindowTitle(g);
        refreshHistoryMenus();
        boardGrid.paintHoverOverlay(g);
        updateComputerPauseOverlay();
        scheduleComputerTurnIfNeeded();
        syncGameplayPlayerMenuDisabled();
        syncNetworkMenuState();
        if (g.getState() == GameState.GAME_OVER) {
            PlayerSide w = g.getMatchWinner();
            setStatus(w == null ? "Konec hry." : "Konec hry — vyhrál %s.".formatted(sideName(w)));
        }
        syncPlayChessClockCpuPause();
    }

    /** {@code true} while automated computer move (executor or animated steps) holds {@link #computerActionPending}. */
    public boolean isComputerPlayPending() {
        return computerActionPending;
    }

    /**
     * Stops CPU step animation and invalidates any in-flight executor callback so scrubbing „Historie tahů“ cannot race
     * with automated play.
     */
    void cancelComputerPlayForHistoryScrub() {
        computerPlayInvalidateGen.incrementAndGet();
        stopComputerPlayTurnTimelineIfAny();
        computerActionPending = false;
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
        if (g.getState() == GameState.GAME_OVER) {
            PlayerSide w = g.getMatchWinner();
            String text =
                    w == null
                            ? "Konec hry."
                            : "Výhra: %s".formatted(sideName(w));
            updateHandLabel(null, text);
            return;
        }
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
        if (isNetworkSessionActive()) {
            String peer =
                    networkWindowPeerLabel != null && !networkWindowPeerLabel.isBlank()
                            ? networkWindowPeerLabel
                            : "síť";
            if (g.getState() == GameState.GAME_OVER) {
                stage.setTitle("Hra Arimaa | %s | %s".formatted(peer, phase));
            } else {
                stage.setTitle("Hra Arimaa | %s | Na tahu: %s".formatted(peer, sideName(g.getSideToMove())));
            }
            return;
        }
        if (g.getState() == GameState.GAME_OVER) {
            stage.setTitle("Arimaa – %s".formatted(phase));
        } else {
            stage.setTitle("Arimaa – %s | na tahu: %s".formatted(phase, sideName(g.getSideToMove())));
        }
    }

    public void startNewGameAction() {
        Game g = game();
        if (g != null) {
            log.info("user action: new game");
            PlayVictoryMediaSfx.dismiss();
            clearComputerAutoplayPauseState();
            clearPlayTurnUi();
            setupPhase.resetChessPresetRotation();
            resetPlayChessClockToSetup();
            g.startNewGame();
            if (gameController != null) {
                gameController.resetTimeline();
            }
            setStatus("Nová hra — rozestavuje Gold.");
            refreshAll();
            hostBroadcastSnapshotIfNeeded();
        }
    }

    /**
     * After a successful load the controller already restored the final position; refresh the UI.
     */
    void playbackLoadedHistory() {
        clearComputerAutoplayPauseState();
        syncPlayPartialFromHistory();
        Game gLoad = game();
        if (playChessClockModel != null && gLoad != null) {
            playChessClockModel.resetForHistoryOrLoad(gLoad);
        } else if (playChessClockModel != null) {
            playChessClockModel.resetToSetup();
        }
        refreshAll();
        setStatus("Hra načtena ze souboru.");
    }

    void recordTimeline() {
        if (gameController != null) {
            gameController.recordAfterMutation();
        }
        if (networkIntentApplyDepth == 0) {
            hostBroadcastSnapshotIfNeeded();
        }
    }

    private void refreshHistoryMenus() {
        Game g = game();
        if (isNetworkClient()) {
            if (undoMenuItem != null) {
                undoMenuItem.setDisable(true);
            }
            if (redoMenuItem != null) {
                redoMenuItem.setDisable(true);
            }
            return;
        }
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
            } else if (MainUiLayoutPhase.isPlayOrGameOver(g)) {
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
                hostBroadcastSnapshotIfNeeded();
            }
            return;
        }
        if (MainUiLayoutPhase.isPlayOrGameOver(g)) {
            if (draftEditsBlockedByTrapMenuOption()) {
                setStatus("Nelze vrátit krok — v rozpracovaném tahu padla figura do pasti (Nastavení).");
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
                    hostBroadcastSnapshotIfNeeded();
                    return;
                }
            }
            if (gameController.undo()) {
                draftUi.draftRedoSteps.clear();
                syncPlayPartialFromHistory();
                setStatus("Zpět — krok zpět v rámci tahu (náhled).");
                refreshAll();
                hostBroadcastSnapshotIfNeeded();
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
                hostBroadcastSnapshotIfNeeded();
            }
            return;
        }
        if (!MainUiLayoutPhase.isPlayOrGameOver(g)) {
            return;
        }
        if (draftEditsBlockedByTrapMenuOption()) {
            setStatus("Nelze vpřed — rozpracovaný tah obsahuje pád do pasti (Nastavení).");
            return;
        }
        if (isTrailingDraftAtLiveEnd() && draftUi.tryRedoDraftFromRedoStack()) {
            setStatus("Vpřed — krok obnoven.");
            refreshAll();
            hostBroadcastSnapshotIfNeeded();
            return;
        }
        if (gameController.redo()) {
            draftUi.draftRedoSteps.clear();
            syncPlayPartialFromHistory();
            setStatus("Vpřed — krok vpřed v rámci tahu (náhled).");
            refreshAll();
            hostBroadcastSnapshotIfNeeded();
            return;
        }
        if (draftUi.hasCancelledDraftSnapshot() && draftUi.redoCancelledDraft()) {
            setStatus("Vpřed — obnoven rozpracovaný tah.");
            refreshAll();
            hostBroadcastSnapshotIfNeeded();
        }
    }

    public void setStatus(String text) {
        if (text == null) {
            setStatus("", null);
            return;
        }
        int nl = text.indexOf('\n');
        if (nl >= 0) {
            setStatus(text.substring(0, nl).trim(), text.substring(nl + 1).trim());
            return;
        }
        setStatus(text, null);
    }

    /**
     * @param headline main line under „Stav:“
     * @param detail optional second line; hidden when {@code null} or blank
     */
    public void setStatus(String headline, String detail) {
        statusLabel.setText(headline == null ? "" : headline);
        boolean hasDetail = detail != null && !detail.isBlank();
        statusDetailLabel.setText(hasDetail ? detail : "");
        statusDetailLabel.setVisible(hasDetail);
        statusDetailLabel.setManaged(hasDetail);
    }

    PlayerControllerKind playerControllerKind(PlayerSide side) {
        return side == PlayerSide.GOLD ? goldPlayerKind : silverPlayerKind;
    }

    public PlayerControllerKind getGoldPlayerKind() {
        return goldPlayerKind;
    }

    public PlayerControllerKind getSilverPlayerKind() {
        return silverPlayerKind;
    }

    void setGoldPlayerKind(PlayerControllerKind kind) {
        goldPlayerKind = Objects.requireNonNull(kind, "kind");
        if (isNetworkHost() && isNetworkSessionActive()) {
            arimaaNetwork().sendSeatControlFromHost(NetworkAssignmentCodec.encode(kind));
        }
    }

    void setSilverPlayerKind(PlayerControllerKind kind) {
        silverPlayerKind = Objects.requireNonNull(kind, "kind");
        if (isNetworkClient() && isNetworkSessionActive()) {
            arimaaNetwork().sendSeatControlFromClient(NetworkAssignmentCodec.encode(kind));
        }
    }

    boolean isComputerControlled(PlayerSide side) {
        return playerControllerKind(side).isComputer();
    }

    ArimaaNetworkCoordinator arimaaNetwork() {
        if (arimaaNetworkCoordinator == null) {
            FxExecutor fx = Platform::runLater;
            arimaaNetworkCoordinator = new ArimaaNetworkCoordinator(this, fx);
        }
        return arimaaNetworkCoordinator;
    }

    @Override
    public void startNewGameAfterNetworkHostReady() {
        startNewGameAction();
    }

    public boolean isNetworkHost() {
        return arimaaNetworkCoordinator != null && arimaaNetworkCoordinator.isHost();
    }

    public boolean isNetworkClient() {
        return arimaaNetworkCoordinator != null
                && arimaaNetworkCoordinator.getRole() == NetworkRole.CLIENT;
    }

    public boolean isNetworkSessionActive() {
        return arimaaNetworkCoordinator != null && arimaaNetworkCoordinator.isActive();
    }

    /**
     * Local board / panel input is allowed for the current mover: not CPU, and in a network game the mover must be
     * {@link PlayerControllerKind#HUMAN} (local seat), not {@link PlayerControllerKind#NETWORK_PEER}.
     */
    boolean isLocalInteractiveTurn(Game g) {
        if (g == null) {
            return false;
        }
        if (isComputerControlled(g.getSideToMove())) {
            return false;
        }
        return playerControllerKind(g.getSideToMove()) == PlayerControllerKind.HUMAN;
    }

    public boolean isApplyingNetworkSnapshot() {
        return applyingNetworkSnapshot;
    }

    public void prepareNetworkSessionAsHost(PlayerControllerKind peerSilverAssignment) {
        networkWindowPeerLabel = "Server";
        /* Gold: člověk/počítač z menu Gameplay (jako v lokální hře). */
        silverPlayerKind = PlayerControllerKind.NETWORK_PEER;
        networkPeerSilverKind = Objects.requireNonNull(peerSilverAssignment);
        networkPeerGoldKind = null;
        syncNetworkMenuState();
        syncGameplayPlayerMenuDisabled();
        syncGameplayPlayerMenuSelectionFromKinds();
        setStatus("Síť — host (Gold), klient hraje Silver.");
    }

    public void prepareNetworkSessionAsClient(PlayerControllerKind peerGoldAssignment) {
        networkWindowPeerLabel = "Klient";
        goldPlayerKind = PlayerControllerKind.NETWORK_PEER;
        /* Silver: člověk/počítač z menu Gameplay (jako v lokální hře). */
        networkPeerGoldKind = Objects.requireNonNull(peerGoldAssignment);
        networkPeerSilverKind = null;
        syncNetworkMenuState();
        syncGameplayPlayerMenuDisabled();
        syncGameplayPlayerMenuSelectionFromKinds();
        setStatus("Síť — připojeno jako Silver; čekám na stav ze serveru…");
    }

    public void clearNetworkSessionAfterDisconnect() {
        goldPlayerKind = PlayerControllerKind.HUMAN;
        silverPlayerKind = PlayerControllerKind.COMPUTER_LEVEL_1;
        networkPeerGoldKind = null;
        networkPeerSilverKind = null;
        networkWindowPeerLabel = null;
        syncNetworkMenuState();
        syncGameplayPlayerMenuDisabled();
        syncGameplayPlayerMenuSelectionFromKinds();
        refreshPlayersAssignmentLabel();
        Game g = game();
        if (g != null && stage != null) {
            updateWindowTitle(g);
        }
    }

    void syncGameplayPlayerMenuDisabled() {
        boolean net = isNetworkSessionActive();
        boolean host = isNetworkHost();
        if (goldPlayerMenuGroup != null) {
            for (var t : goldPlayerMenuGroup.getToggles()) {
                if (t instanceof RadioMenuItem r) {
                    r.setDisable(net && !host);
                }
            }
        }
        if (silverPlayerMenuGroup != null) {
            for (var t : silverPlayerMenuGroup.getToggles()) {
                if (t instanceof RadioMenuItem r) {
                    r.setDisable(net && host);
                }
            }
        }
    }

    void syncGameplayPlayerMenuSelectionFromKinds() {
        suppressGameplayPlayerMenuCallback = true;
        try {
            selectPlayerKindInMenuGroup(goldPlayerMenuGroup, goldPlayerKind);
            selectPlayerKindInMenuGroup(silverPlayerMenuGroup, silverPlayerKind);
        } finally {
            suppressGameplayPlayerMenuCallback = false;
        }
    }

    private static void selectPlayerKindInMenuGroup(ToggleGroup group, PlayerControllerKind kind) {
        if (group == null || kind == PlayerControllerKind.NETWORK_PEER) {
            return;
        }
        for (var t : group.getToggles()) {
            if (t instanceof RadioMenuItem r && r.getUserData() == kind) {
                r.setSelected(true);
                return;
            }
        }
    }

    public void applyNetworkPeerSilverSeatFromWire(String wire) {
        if (!isNetworkHost()) {
            return;
        }
        try {
            networkPeerSilverKind = NetworkAssignmentCodec.decode(wire);
            refreshPlayersAssignmentLabel();
        } catch (IllegalArgumentException ex) {
            log.warn("seat_control (Silver) ignored: {}", ex.getMessage());
        }
    }

    public void applyNetworkPeerGoldSeatFromWire(String wire) {
        if (!isNetworkClient()) {
            return;
        }
        try {
            networkPeerGoldKind = NetworkAssignmentCodec.decode(wire);
            refreshPlayersAssignmentLabel();
        } catch (IllegalArgumentException ex) {
            log.warn("seat_control (Gold) ignored: {}", ex.getMessage());
        }
    }

    void syncNetworkMenuState() {
        boolean on = isNetworkSessionActive();
        boolean hostWait =
                arimaaNetworkCoordinator != null && arimaaNetworkCoordinator.isHostBeforeWelcomeDone();
        if (networkHostMenuItem != null) {
            boolean showHost = !on;
            networkHostMenuItem.setVisible(showHost);
        }
        if (networkConnectMenuItem != null) {
            boolean showConnect = !on;
            networkConnectMenuItem.setVisible(showConnect);
        }
        if (networkCancelHostWaitMenuItem != null) {
            networkCancelHostWaitMenuItem.setVisible(hostWait);
        }
        if (networkDisconnectMenuItem != null) {
            boolean showDisconnect = on && !hostWait;
            networkDisconnectMenuItem.setVisible(showDisconnect);
        }
    }

    /**
     * Wraps long „Stav:“ lines by binding label {@code maxWidth} to the side {@link VBox} width. Avoid binding the
     * VBox {@code prefWidth} to the {@link ScrollPane} viewport — that can inflate the pane and hide the board.
     */
    private void wireSidePanelTextWrapToViewport(VBox sidePanel) {
        DoubleBinding textMax =
                Bindings.createDoubleBinding(
                        () -> Math.max(40, sidePanel.getWidth() - 24), sidePanel.widthProperty());
        statusLabel.setMinWidth(0);
        statusDetailLabel.setMinWidth(0);
        playersAssignmentLabel.setMinWidth(0);
        handLabel.setMinWidth(0);
        statusLabel.maxWidthProperty().bind(textMax);
        statusDetailLabel.maxWidthProperty().bind(textMax);
        playersAssignmentLabel.maxWidthProperty().bind(textMax);
        handLabel.maxWidthProperty().bind(textMax);
        if (!sidePanel.getChildren().isEmpty()
                && sidePanel.getChildren().getFirst() instanceof Region statusBlock) {
            statusBlock.setMinWidth(0);
            statusBlock.maxWidthProperty().bind(sidePanel.widthProperty());
        }
    }

    public String buildNetworkSnapshotSaveText() {
        if (gameController == null) {
            return "";
        }
        String draft = null;
        Game g = game();
        if (g != null && g.getState() == GameState.PLAY && gameController.getPlayHistory().isBootstrapped()) {
            PlayTurnHistory ph = gameController.getPlayHistory();
            var halves = ph.halfTurnsUnmodifiable();
            PlayHalfTurn tail = halves.get(halves.size() - 1);
            if (!tail.committed() && !tail.steps().isEmpty()) {
                Game probe = PlayDraftNotationSupport.probeGameFromMemento(tail.startSnap());
                String prefix = gameController.nextPlayNotationPrefix();
                Move m = new Move();
                for (Step s : tail.steps()) {
                    m.getSteps().add(PlayDraftNotationSupport.copyStep(s));
                }
                draft = ArimaaNotation.formatPartialTurnLine(probe.getBoard(), m, prefix);
            }
        }
        return new GameSerializer().serializeForNetwork(gameController, draft);
    }

    @Override
    public void applyNetworkSnapshotSaveText(String text) {
        if (gameController == null || stage == null) {
            return;
        }
        applyingNetworkSnapshot = true;
        try {
            GameSerializer ser = new GameSerializer();
            GameSerializer.ParsedTxtGame p = ser.parse(text);
            clearPlayTurnUi();
            gameController.loadFromTxtGame(p.playStartSnapshot(), p.moveLines());
            syncPlayPartialFromHistory();
            clearComputerAutoplayPauseState();
            Game gSync = game();
            if (playChessClockModel != null && gSync != null) {
                if (gSync.getState() == GameState.PLAY || gSync.getState() == GameState.GAME_OVER) {
                    notifyPlayChessClockHistoryNavigation();
                } else {
                    resetPlayChessClockToSetup();
                }
            }
            refreshAll();
            if (isNetworkClient()) {
                setStatus("Síť — stav synchronizován.");
            }
        } catch (RuntimeException ex) {
            log.warn("network snapshot load failed", ex);
            setStatus("Síť — nelze načíst stav: %s".formatted(ex.getMessage()));
        } finally {
            applyingNetworkSnapshot = false;
            if (isNetworkClient()) {
                computerActionPending = false;
            }
        }
    }

    void hostBroadcastSnapshotIfNeeded() {
        if (arimaaNetworkCoordinator != null) {
            arimaaNetworkCoordinator.broadcastSnapshotFromHostMainThread();
        }
    }

    /**
     * Applies a Silver-side intent from the network client on the host JavaFX thread.
     *
     * @return {@code false} if the intent is illegal in the current phase
     */
    public boolean applyHostIntentFromNetwork(WireMessages.IntentMessage in) {
        networkIntentApplyDepth++;
        try {
            Game g = game();
            if (g == null || gameController == null) {
                return false;
            }
            if (g.getSideToMove() != PlayerSide.SILVER) {
                return false;
            }
            boolean ok =
                    switch (in.kind()) {
                        case SETUP_PICK -> {
                            if (g.getState() != GameState.SETUP_SILVER || in.pieceType() == null) {
                                yield false;
                            }
                            setupPhase.onPickReserve(in.pieceType());
                            yield true;
                        }
                        case SETUP_BOARD_CLICK -> {
                            if (g.getState() != GameState.SETUP_SILVER || in.file() == null || in.rank() == null) {
                                yield false;
                            }
                            setupPhase.onBoardCellClick(in.file(), in.rank());
                            yield true;
                        }
                        case SETUP_RANDOM -> {
                            if (!MainUiLayoutPhase.isSetup(g) || g.getState() != GameState.SETUP_SILVER) {
                                yield false;
                            }
                            setupPhase.performRandomSetupPlacementAction();
                            yield true;
                        }
                        case SETUP_CHESS -> {
                            if (g.getState() != GameState.SETUP_SILVER || in.presetIndex() == null) {
                                yield false;
                            }
                            if (g.applyChessMappedSetup(PlayerSide.SILVER, in.presetIndex())) {
                                recordTimeline();
                                setStatus("Šachová rozestavení (síť).");
                                refreshAll();
                                yield true;
                            }
                            yield false;
                        }
                        case SETUP_COMPLETE -> {
                            if (g.getState() != GameState.SETUP_SILVER) {
                                yield false;
                            }
                            setupPhase.tryCompleteSetupFromUi();
                            yield true;
                        }
                        case SETUP_CANCEL_HAND -> {
                            if (!MainUiLayoutPhase.isSetup(g)) {
                                yield false;
                            }
                            boolean changed = g.getSetupHand() != null;
                            g.cancelPendingSetupPlacement();
                            if (changed) {
                                recordTimeline();
                            }
                            refreshAll();
                            yield true;
                        }
                        case SETUP_SILVER_CPU_AUTOFILL -> {
                            if (g.getState() != GameState.SETUP_SILVER) {
                                yield false;
                            }
                            runComputerSetupStep(g);
                            yield true;
                        }
                        case PLAY_ACTIVATE -> {
                            if (g.getState() != GameState.PLAY || in.file() == null || in.rank() == null) {
                                yield false;
                            }
                            playPhase.handlePlayBoardActivation(in.file(), in.rank());
                            yield true;
                        }
                        case PLAY_END_TURN -> {
                            if (g.getState() != GameState.PLAY) {
                                yield false;
                            }
                            playPhase.tryEndPlayTurn();
                            yield true;
                        }
                        case PLAY_CANCEL_DRAFT -> {
                            if (g.getState() != GameState.PLAY) {
                                yield false;
                            }
                            yield draftUi.tryCancelPlayDraftFromUi();
                        }
                        case PLAY_SUBMIT_NOTATION -> {
                            if (g.getState() != GameState.PLAY
                                    || in.notationLine() == null
                                    || in.notationLine().isBlank()) {
                                yield false;
                            }
                            try {
                                PlayNotationParser.ParsedLine pl =
                                        PlayNotationParser.parseLine(g, in.notationLine().trim());
                                if (pl.hasEarlyPassSuffix()) {
                                    yield false;
                                }
                                Move submit = PlayDraftNotationSupport.copyMove(pl.move());
                                gameController.restoreTrailingDraftTurnStartForSubmit();
                                PlayerSide mover = g.getSideToMove();
                                int trapsBeforeSfx = PlayProceduralSfx.totalTrapCaptures(g);
                                if (!gameController.submitHumanMove(submit)) {
                                    yield false;
                                }
                                playSfxAfterBoardMutationIfEnabled(
                                        trapsBeforeSfx, Math.max(1, submit.getSteps().size()));
                                gameController.recordCommittedPlayTurn(submit, in.notationLine().trim());
                                notifyPlayChessClockAfterCommittedTurn(mover);
                                clearPlayTurnUi();
                                syncPlayPartialFromHistory();
                                if (g.getState() == GameState.GAME_OVER) {
                                    PlayerSide w = g.getMatchWinner();
                                    setStatus(
                                            w == null
                                                    ? "Konec hry."
                                                    : "Konec hry — vyhrál %s.".formatted(sideName(w)));
                                    playVictoryWinnerMediaIfEnabled(w);
                                } else {
                                    setStatus(statusPlayerOnTurn(g.getSideToMove()));
                                }
                                appendHistory(new GameHistoryEvent.TurnCommitted(in.notationLine().trim()));
                                refreshAll();
                                yield true;
                            } catch (IllegalArgumentException ex) {
                                log.debug("PLAY_SUBMIT_NOTATION: {}", ex.getMessage());
                                yield false;
                            }
                        }
                    };
            if (ok) {
                hostBroadcastSnapshotIfNeeded();
            }
            return ok;
        } finally {
            networkIntentApplyDepth--;
        }
    }

    void startNetworkHostDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Hostovat");
        dialog.setHeaderText(
                "Server — Gold (vy), klient Silver. Člověk/počítač pro Gold nastavte v menu Nastavení → Gold hráč.");
        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        TextField portField = new TextField(String.valueOf(DEFAULT_NETWORK_PORT));
        TextArea ipArea = new TextArea(NetworkLocalAddresses.ipv4TextBlock());
        ipArea.setEditable(false);
        ipArea.setPrefRowCount(5);
        ipArea.setWrapText(true);
        int r = 0;
        grid.add(new Label("Port (> 1024):"), 0, r);
        grid.add(portField, 1, r++);
        grid.add(new Label("Vaše IPv4:"), 0, r);
        grid.add(ipArea, 1, r);
        pane.setContent(grid);

        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isEmpty() || answer.get() != ButtonType.OK) {
            return;
        }
        try {
            int port = Integer.parseInt(portField.getText().trim());
            if (port <= 1024 || port > 65535) {
                setStatus("Neplatný port.");
                return;
            }
            setStatus("Síť — zakládám server na portu %d…".formatted(port));
            arimaaNetwork().startHost(port);
            syncNetworkMenuState();
        } catch (NumberFormatException ex) {
            setStatus("Port musí být číslo.");
        }
    }

    void startNetworkClientDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Připojit se");
        dialog.setHeaderText(
                "Klient — Silver (vy), server Gold. Člověk/počítač pro Silver nastavte v menu Nastavení → Silver hráč.");
        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        TextField hostField = new TextField("localhost");
        TextField portField = new TextField(String.valueOf(DEFAULT_NETWORK_PORT));
        int r = 0;
        grid.add(new Label("Host:"), 0, r);
        grid.add(hostField, 1, r++);
        grid.add(new Label("Port:"), 0, r);
        grid.add(portField, 1, r);
        pane.setContent(grid);

        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isEmpty() || answer.get() != ButtonType.OK) {
            return;
        }
        try {
            int port = Integer.parseInt(portField.getText().trim());
            if (port <= 1024 || port > 65535) {
                setStatus("Neplatný port.");
                return;
            }
            String host = hostField.getText().trim();
            setStatus("Síť — připojuji se k %s:%d…".formatted(host, port));
            arimaaNetwork().startClient(host, port);
            syncNetworkMenuState();
        } catch (NumberFormatException ex) {
            setStatus("Port musí být číslo.");
        }
    }

    void disconnectNetwork() {
        if (arimaaNetworkCoordinator != null) {
            arimaaNetworkCoordinator.stopSession();
        }
    }

    public double getComputerStepDelayMs() {
        return computerStepDelayMs;
    }

    public void setComputerStepDelayMs(double ms) {
        computerStepDelayMs = Math.max(MIN_COMPUTER_STEP_DELAY_MS, Math.min(MAX_COMPUTER_STEP_DELAY_MS, ms));
    }

    private void scheduleComputerTurnIfNeeded() {
        Game g = game();
        if (g == null || stage == null) {
            return;
        }
        if (computerActionPending) {
            return;
        }
        if (!shouldOfferComputerStep(g)) {
            return;
        }
        if (computerAutoplayPaused) {
            return;
        }
        if (g.getState() == GameState.SETUP_SILVER
                && isNetworkClient()
                && isComputerControlled(PlayerSide.SILVER)) {
            computerActionPending = true;
            arimaaNetwork()
                    .sendIntent(
                            new WireMessages.IntentMessage(
                                    IntentKind.SETUP_SILVER_CPU_AUTOFILL, null, null, null, null));
            return;
        }
        computerActionPending = true;
        if (g.getState() == GameState.SETUP_GOLD || g.getState() == GameState.SETUP_SILVER) {
            Platform.runLater(() -> {
                try {
                    Game g2 = game();
                    if (g2 != null && shouldOfferComputerStep(g2) && !computerAutoplayPaused) {
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
        if (!ph.isViewOnTrailingDraftHalf()) {
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
            final long execToken = computerPlayInvalidateGen.get();
            try {
                Game probe = Game.restoredFromMemento(startSnap);
                PlayerControllerKind cpuKind = playerControllerKind(probe.getSideToMove());
                Move chosen = ComputerPlayMove.selectPlayMove(cpuKind, probe, computerRandom);
                Platform.runLater(() -> {
                    long genNow = computerPlayInvalidateGen.get();
                    if (genNow != execToken) {
                        computerActionPending = false;
                        refreshAll();
                        return;
                    }
                    if (computerAutoplayPaused) {
                        computerActionPending = false;
                        refreshAll();
                        return;
                    }
                    Game gNow = game();
                    if (isNetworkClient()
                            && gNow != null
                            && gNow.getSideToMove() == PlayerSide.SILVER
                            && isComputerControlled(PlayerSide.SILVER)) {
                        submitNetworkClientSilverCpuPlayTurn(chosen);
                        return;
                    }
                    beginComputerPlayAnimation(chosen);
                });
            } catch (IllegalStateException ex) {
                log.warn("computer play: no legal moves ({})", ex.getMessage());
                Platform.runLater(() -> {
                    if (computerPlayInvalidateGen.get() != execToken) {
                        computerActionPending = false;
                        return;
                    }
                    computerActionPending = false;
                    setStatus("Počítač — žádný platný tah.");
                    refreshAll();
                });
            } catch (Exception ex) {
                log.warn("computer play: move selection failed", ex);
                Platform.runLater(() -> {
                    if (computerPlayInvalidateGen.get() != execToken) {
                        computerActionPending = false;
                        return;
                    }
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

    private void stopComputerPlayTurnTimelineIfAny() {
        Timeline t = computerPlayTurnTimeline;
        if (t != null) {
            t.stop();
            computerPlayTurnTimeline = null;
        }
    }

    private void clearComputerAutoplayPauseState() {
        computerAutoplayPaused = false;
        computerPlayInvalidateGen.incrementAndGet();
        stopComputerPlayTurnTimelineIfAny();
        computerActionPending = false;
        updateComputerPauseOverlay();
    }

    private void updateComputerPauseOverlay() {
        if (computerPauseOverlay == null) {
            return;
        }
        Game g = game();
        boolean show = g != null && computerAutoplayPaused && shouldOfferComputerStep(g);
        computerPauseOverlay.setVisible(show);
        computerPauseOverlay.setManaged(show);
    }

    /**
     * Toggles autoplay pause for the side to move when it is any {@link PlayerControllerKind#isComputer() computer}
     * level; bound to
     * Space from {@link PlaySceneKeyHandler}.
     */
    public void toggleComputerAutoplayPauseFromKeyboard() {
        Game g = game();
        if (g == null || g.getState() == GameState.GAME_OVER) {
            return;
        }
        if (!shouldOfferComputerStep(g)) {
            return;
        }
        computerAutoplayPaused = !computerAutoplayPaused;
        if (computerAutoplayPaused) {
            if (computerPlayTurnTimeline != null) {
                computerPlayTurnTimeline.pause();
            } else {
                computerPlayInvalidateGen.incrementAndGet();
                stopComputerPlayTurnTimelineIfAny();
            }
            setStatus("Pauza — mezerník pokračuje.");
        } else {
            setStatus("Pokračuje tah počítače.");
            if (computerPlayTurnTimeline != null
                    && computerPlayTurnTimeline.getStatus() == Animation.Status.PAUSED) {
                computerPlayTurnTimeline.play();
            } else {
                refreshAll();
            }
        }
        updateComputerPauseOverlay();
        syncPlayChessClockCpuPause();
    }

    /**
     * Fills the mover's home from reserve: tries chess-mapped presets (classic {@code -1} and rotations) in random
     * order, then {@link Game#placeRemainingPiecesRandomly(PlayerSide)} if none apply.
     */
    private void runComputerSetupStep(Game g) {
        PlayerSide side = g.getSideToMove();
        List<Integer> presets = new ArrayList<>();
        presets.add(-1);
        for (int i = 0; i < Game.CHESS_SETUP_ROTATION_COUNT; i++) {
            presets.add(i);
        }
        Collections.shuffle(presets, ThreadLocalRandom.current());
        boolean placed = false;
        for (int preset : presets) {
            if (g.applyChessMappedSetup(side, preset)) {
                placed = true;
                break;
            }
        }
        if (!placed) {
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
            notifyPlayChessClockEnterPlay();
            setStatus(statusPlayerOnTurn(g.getSideToMove()));
        } else {
            recordTimeline();
            setStatus("Nová hra — rozestavuje %s.".formatted(sideName(g.getSideToMove())));
        }
    }

    private void submitNetworkClientSilverCpuPlayTurn(Move chosen) {
        try {
            Game g = game();
            if (g == null || gameController == null) {
                return;
            }
            gameController.restoreTrailingDraftTurnStartForSubmit();
            String prefix = gameController.nextPlayNotationPrefix();
            Move submit = PlayDraftNotationSupport.copyMove(chosen);
            String notationLine = ArimaaNotation.formatFullTurn(g.getBoard(), submit, prefix);
            arimaaNetwork()
                    .sendIntent(
                            new WireMessages.IntentMessage(
                                    IntentKind.PLAY_SUBMIT_NOTATION, null, null, null, null, notationLine));
        } finally {
            computerActionPending = false;
        }
        refreshAll();
    }

    private void beginComputerPlayAnimation(Move chosen) {
        stopComputerPlayTurnTimelineIfAny();
        Game g = game();
        if (g == null
                || gameController == null
                || g.getState() != GameState.PLAY
                || !isComputerControlled(g.getSideToMove())) {
            computerActionPending = false;
            refreshAll();
            return;
        }
        if (computerAutoplayPaused) {
            computerActionPending = false;
            refreshAll();
            return;
        }
        int n = chosen.getSteps().size();
        if (n == 0) {
            finishComputerPlayCommit(chosen);
            return;
        }
        long delayMs =
                Math.round(Math.max(MIN_COMPUTER_STEP_DELAY_MS, Math.min(MAX_COMPUTER_STEP_DELAY_MS, computerStepDelayMs)));
        buildAndStartComputerPlayTurnTimeline(chosen, n, delayMs);
    }

    /**
     * Applies step {@code k} of {@code n} of computer move {@code full} to play history view and game model.
     *
     * @param refreshUi when {@code true}, runs {@link #refreshAll()}; when {@code false}, only updates model/history
     *     view state. {@link #finishComputerPlayCommit} ends with a full {@link #refreshAll()}.
     */
    private void applyComputerPlayStepView(Move full, int k, int n, boolean refreshUi) {
        Game g = game();
        if (g == null || gameController == null) {
            stopComputerPlayTurnTimelineIfAny();
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
        int trapsBeforeSfx = PlayProceduralSfx.totalTrapCaptures(g);
        gameController.applyPlayHistoryViewToGame();
        playSfxAfterBoardMutationIfEnabled(trapsBeforeSfx, 1);
        syncPlayPartialFromHistory();
        if (refreshUi) {
            refreshAll();
        }
    }

    private void buildAndStartComputerPlayTurnTimeline(Move full, int n, long delayMs) {
        stopComputerPlayTurnTimelineIfAny();
        Timeline tl = new Timeline();
        for (int k = 1; k <= n; k++) {
            final int fk = k;
            tl.getKeyFrames()
                    .add(new KeyFrame(Duration.millis((fk - 1L) * delayMs), e -> applyComputerPlayStepView(full, fk, n, true)));
        }
        tl.getKeyFrames().add(new KeyFrame(Duration.millis((long) n * delayMs), e -> finishComputerPlayCommit(full)));
        tl.setOnFinished(e -> {
            if (computerPlayTurnTimeline == tl) {
                computerPlayTurnTimeline = null;
            }
        });
        computerPlayTurnTimeline = tl;
        tl.play();
    }

    private void finishComputerPlayCommit(Move full) {
        try {
            Game g = game();
            if (g == null || gameController == null) {
                return;
            }
            PlayerSide mover = g.getSideToMove();
            gameController.restoreTrailingDraftTurnStartForSubmit();
            Move submit = PlayDraftNotationSupport.copyMove(full);
            String prefix = gameController.nextPlayNotationPrefix();
            String notationLine = ArimaaNotation.formatFullTurn(g.getBoard(), submit, prefix);
            if (!gameController.submitHumanMove(submit)) {
                log.info("computer play: submit rejected");
                setStatus("Počítač — tah nebyl přijat.");
                gameController.restoreTrailingDraftTurnStartForSubmit();
                gameController.getPlayHistory().replaceTrailingDraftStepsFromMove(new Move());
                gameController.applyPlayHistoryViewToGame();
                return;
            }
            /* SFX already played per animated step in applyComputerPlayStepView; avoid second trap wail on submit. */
            gameController.recordCommittedPlayTurn(submit, notationLine);
            notifyPlayChessClockAfterCommittedTurn(mover);
            clearPlayTurnUi();
            syncPlayPartialFromHistory();
            if (g.getState() == GameState.GAME_OVER) {
                PlayerSide w = g.getMatchWinner();
                setStatus(w == null ? "Konec hry." : "Konec hry — vyhrál %s.".formatted(sideName(w)));
                playVictoryWinnerMediaIfEnabled(w);
            } else {
                setStatus(statusPlayerOnTurn(g.getSideToMove()));
            }
            appendHistory(new GameHistoryEvent.TurnCommitted(notationLine));
        } finally {
            stopComputerPlayTurnTimelineIfAny();
            computerActionPending = false;
        }
        refreshAll();
        hostBroadcastSnapshotIfNeeded();
    }

    Game game() {
        return gameController != null ? gameController.getGame() : null;
    }

    /** After {@link GameController#applyPlayHistoryViewToGame()} or successful {@link GameController#submitHumanMove}. */
    void playSfxAfterBoardMutationIfEnabled(int trapsBefore, int woodCues) {
        if (gameplaySoundEnabledItem == null || !gameplaySoundEnabledItem.isSelected() || woodCues <= 0) {
            return;
        }
        PlayProceduralSfx.playAfterBoardMutation(trapsBefore, game(), woodCues);
    }

    /** Applies „Historie tahů“ line selection: view prefix, board, draft sync, refresh (mouse or {@link #navigateNotationHistoryByPage}). */
    void applyNotationHistoryListSelection(int idx) {
        if (idx < 0 || gameController == null || isComputerPlayPending() || isNetworkClient()) {
            return;
        }
        Game g = game();
        if (!MainUiLayoutPhase.showCapturesAndNotationHistory(g)) {
            return;
        }
        PlayTurnHistory ph = gameController.getPlayHistory();
        if (!ph.isBootstrapped()) {
            return;
        }
        List<Integer> vis = ph.visibleHalfIndicesForDisplay(true);
        if (idx >= vis.size()) {
            return;
        }
        notationHistoryAutoScrollOnNextRefresh = false;
        cancelComputerPlayForHistoryScrub();
        ph.navigateToVisibleLine(idx, true);
        gameController.applyPlayHistoryViewToGame();
        syncPlayPartialFromHistory();
        notifyPlayChessClockHistoryNavigation();
        refreshAll();
        hostBroadcastSnapshotIfNeeded();
    }

    /** Page Down ({@code directionSign > 0}) / Page Up: previous or next line in „Historie tahů“. */
    void navigateNotationHistoryByPage(int directionSign) {
        if (directionSign == 0 || gameController == null || !gameController.getPlayHistory().isBootstrapped() || isNetworkClient()) {
            return;
        }
        Game g = game();
        if (!MainUiLayoutPhase.showCapturesAndNotationHistory(g)) {
            return;
        }
        int n = notationHistoryItems.size();
        if (n == 0) {
            return;
        }
        int cur = notationHistoryList.getSelectionModel().getSelectedIndex();
        if (cur < 0) {
            List<Integer> vis = gameController.getPlayHistory().visibleHalfIndicesForDisplay(true);
            cur = vis.indexOf(gameController.getPlayHistory().viewHalfIndex());
            if (cur < 0) {
                cur = directionSign > 0 ? 0 : n - 1;
            }
        } else {
            cur = Math.min(n - 1, Math.max(0, cur + Integer.signum(directionSign)));
        }
        selectNotationHistoryLine(cur);
    }

    private void selectNotationHistoryLine(int cur) {
        suppressHistoryListEvents = true;
        notationHistoryList.getSelectionModel().select(cur);
        suppressHistoryListEvents = false;
        applyNotationHistoryListSelection(cur);
        int scrollTarget = cur;
        Platform.runLater(() -> scrollNotationHistoryToShowIndex(scrollTarget));
    }

    /**
     * Scrolls the notation list so {@code index} sits near the vertical center of the viewport. Plain
     * {@link javafx.scene.control.ListView#scrollTo(int)} aligns the row to the top, which feels wrong for Page Down.
     */
    void scrollNotationHistoryToShowIndex(int index) {
        if (index < 0 || notationHistoryItems.isEmpty()) {
            return;
        }
        int n = notationHistoryItems.size();
        index = Math.min(n - 1, Math.max(0, index));
        double cell = notationHistoryList.getFixedCellSize();
        if (cell <= 0) {
            cell = 22;
        }
        double h = notationHistoryList.getHeight();
        if (h <= 0) {
            h = notationHistoryList.getPrefHeight();
        }
        int visibleRows = Math.max(1, (int) Math.floor(h / cell));
        if (n <= visibleRows) {
            notationHistoryList.scrollTo(0);
            return;
        }
        int firstVisible = index - visibleRows / 2;
        firstVisible = Math.max(0, Math.min(firstVisible, n - visibleRows));
        notationHistoryList.scrollTo(firstVisible);
    }

    void playVictoryWinnerMediaIfEnabled(PlayerSide winner) {
        if (winner == null || gameplaySoundEnabledItem == null || !gameplaySoundEnabledItem.isSelected()) {
            return;
        }
        if (stage != null) {
            PlayVictoryMediaSfx.playWinnerIfPresent(stage, winner);
        }
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
        PlayTurnHistory ph = gameController.getPlayHistory();
        int stepsBefore = ph.appliedPrefixSteps();
        int trapsBeforeSfx = PlayProceduralSfx.totalTrapCaptures(game());
        ph.replaceTrailingDraftStepsFromMove(playDraft.partial);
        int woodCues = Math.max(1, ph.appliedPrefixSteps() - stepsBefore);
        gameController.applyPlayHistoryViewToGame();
        playSfxAfterBoardMutationIfEnabled(trapsBeforeSfx, woodCues);
        if (networkIntentApplyDepth == 0) {
            hostBroadcastSnapshotIfNeeded();
        }
    }

    private List<String> buildNotationHistoryLines() {
        if (gameController == null) {
            return new ArrayList<>();
        }
        return PlayDraftNotationSupport.buildNotationHistoryLines(
                gameController.getPlayHistory(),
                game(),
                gameController::nextPlayNotationPrefix,
                showComputerTurnStepsInNotationItem != null
                        && showComputerTurnStepsInNotationItem.isSelected(),
                isComputerPlayPending());
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
        if (g != null && !isLocalInteractiveTurn(g)) {
            return;
        }
        if (isNetworkClient()) {
            arimaaNetwork().sendIntent(new WireMessages.IntentMessage(IntentKind.SETUP_RANDOM, null, null, null, null));
            return;
        }
        setupPhase.performRandomSetupPlacementAction();
    }

    /** Same as „Šachová rozestavení“ — rotates among reversed / symmetric / MH / HH presets. */
    void applyChessMappedSetupFromUi() {
        Game g = game();
        if (g != null && !isLocalInteractiveTurn(g)) {
            return;
        }
        if (isNetworkClient()) {
            int preset = setupPhase.consumeNextChessPresetIndexForNetwork();
            arimaaNetwork()
                    .sendIntent(
                            new WireMessages.IntentMessage(IntentKind.SETUP_CHESS, null, null, null, preset));
            return;
        }
        setupPhase.applyChessMappedSetupFromUi();
    }

    /** Same as „Hotovo (ukončit rozestavení)“. */
    void tryCompleteSetupFromUi() {
        Game g = game();
        if (g != null && !isLocalInteractiveTurn(g)) {
            return;
        }
        if (isNetworkClient()) {
            arimaaNetwork().sendIntent(new WireMessages.IntentMessage(IntentKind.SETUP_COMPLETE, null, null, null, null));
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
        Game g = game();
        if (g != null && isNetworkClient() && isLocalInteractiveTurn(g)) {
            arimaaNetwork()
                    .sendIntent(
                            new WireMessages.IntentMessage(
                                    IntentKind.PLAY_CANCEL_DRAFT, null, null, null, null));
            setStatus("Zrušení rozpracovaného tahu (síť)…");
            return true;
        }
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
     * Tab / Shift+Tab: one ring of pull targets (sorted), then each legal push option (sorted; same destination kept as
     * separate stops when different weaker pieces can be displaced there; pull wins on overlapping cell), else own pieces.
     */
    void advancePlayTabFocus(Game g, boolean reverse) {
        playPhase.advancePlayTabFocus(g, reverse);
    }

    /**
     * Space when pull and/or push targets exist: uses {@code keyboardPullFocus} or push focus pair
     * ({@code keyboardPushFocus}, {@code keyboardPushWeakFrom}) when valid; otherwise first pull, else first push.
     */
    void activatePlayPullOrPushFromKeyboard(Game g) {
        playPhase.activatePlayPullOrPushFromKeyboard(g);
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
        if (g != null && g.getState() == GameState.PLAY && !isLocalInteractiveTurn(g)) {
            return;
        }
        if (g != null && g.getState() == GameState.PLAY && isNetworkClient()) {
            arimaaNetwork()
                    .sendIntent(
                            new WireMessages.IntentMessage(
                                    IntentKind.PLAY_ACTIVATE, null, modelFile, modelRank, null));
            return;
        }
        playPhase.handlePlayBoardActivation(modelFile, modelRank);
    }

    void tryEndPlayTurn() {
        Game g = game();
        if (g != null && !isLocalInteractiveTurn(g)) {
            return;
        }
        if (isNetworkClient()) {
            arimaaNetwork().sendIntent(new WireMessages.IntentMessage(IntentKind.PLAY_END_TURN, null, null, null, null));
            return;
        }
        playPhase.tryEndPlayTurn();
        hostBroadcastSnapshotIfNeeded();
    }

    static String labelForReserveButton(PieceType type, int count) {
        return "%s × %d".formatted(String.valueOf(type.notationChar()), count);
    }

    static String sideName(PlayerSide s) {
        return s == PlayerSide.GOLD ? "Gold" : "Silver";
    }

    static String statusPlayerOnTurn(PlayerSide sideToMove) {
        return "Na tahu hráč %s.".formatted(sideName(sideToMove));
    }
}
