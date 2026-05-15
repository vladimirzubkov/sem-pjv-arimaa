package cz.cvut.fel.pjv.arimaa.ui;

import cz.cvut.fel.pjv.arimaa.model.DefaultRuleEngine;
import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.enums.GameState;
import cz.cvut.fel.pjv.arimaa.model.Piece;
import cz.cvut.fel.pjv.arimaa.model.enums.PieceType;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;
import cz.cvut.fel.pjv.arimaa.model.Position;
import cz.cvut.fel.pjv.arimaa.util.BoardConstants;
import cz.cvut.fel.pjv.arimaa.util.HomeTerritory;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeType;
import javafx.scene.text.Font;
import javafx.scene.transform.Scale;

/**
 * 8×8 board cells, coordinate frame, scaling host, and piece/hover painting. Wired via {@link BoardViewHost} for
 * game state and orientation mapping (same package).
 */
public final class BoardGridView {

    static final int CELL = 52;
    static final int BOARD_GAP = 1;
    static final int COORD = 24;
    static final int FRAME_INSET = 10;
    static final Font CELL_FONT = Font.font(18);
    static final Font COORD_FONT = Font.font(12);
    static final double PIECE_IMAGE_MAX = Math.max(16, CELL - 8);
    private static final double BOARD_VIEW_MARGIN = 14;
    /** Opacity for pieces frozen under Arimaa rules ({@link DefaultRuleEngine#isFrozen}). */
    private static final double FROZEN_PIECE_OPACITY = 0.38;

    /**
     * Trap squares: diagonal “pit” — board tone at top-left, deep shadow toward bottom-right.
     */
    static LinearGradient trapChasmFill(Color boardTone) {
        Color pitOpaque = boardTone.interpolate(Color.rgb(10, 6, 14), 0.9);
        Color pitCorner = pitOpaque.deriveColor(0, 1, 1, 0.5);
        return new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, boardTone),
                new Stop(0.45, boardTone.interpolate(pitOpaque, 0.38)),
                new Stop(1, pitCorner));
    }

    public record CellData(
            int fileIndex,
            int visualRow,
            Rectangle background,
            Color baseFill,
            Color baseStroke,
            double baseStrokeWidth,
            Label pieceLabel,
            ImageView pieceImage,
            ImageView hoverImage,
            Label hoverLabel) {}

    private final BoardViewHost host;
    final StackPane[][] cells = new StackPane[BoardConstants.BOARD_SIZE][BoardConstants.BOARD_SIZE];

    public BoardGridView(BoardViewHost host) {
        this.host = host;
        for (int row = 0; row < BoardConstants.BOARD_SIZE; row++) {
            for (int col = 0; col < BoardConstants.BOARD_SIZE; col++) {
                cells[row][col] = createCell(col, row);
            }
        }
    }

    private static int boardBlockPixels() {
        return BoardConstants.BOARD_SIZE * CELL + (BoardConstants.BOARD_SIZE - 1) * BOARD_GAP;
    }

    private static int perimeterSpanPixels() {
        return 2 * COORD + boardBlockPixels() + 2 * BOARD_GAP;
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
        boolean trap = PlayDraftNotationSupport.isStaticTrapSquare(pos);
        if (trap) {
            baseStroke = Color.DARKRED;
            baseStrokeWidth = 2;
        } else {
            baseStroke = Color.gray(0.35);
            baseStrokeWidth = 1;
        }
        bg.setFill(trap ? trapChasmFill(baseFill) : baseFill);
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
                host.setHoverCell(fi, vr);
            } else {
                host.clearHoverCellIf(fi, vr);
            }
        });

        cell.setOnMouseClicked(e -> host.onBoardCellClick(fi, vr));
        return cell;
    }

    StackPane buildFramedBoardWithPerimeterCoordinates() {
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
            host.registerFileCoordLabels(f, top, bottom);
            surface.add(top, f + 1, 0);
            surface.add(bottom, f + 1, 9);
        }
        for (int visualRow = 0; visualRow < BoardConstants.BOARD_SIZE; visualRow++) {
            int gridRow = visualRow + 1;
            Label left = coordLabel("8", COORD, CELL, false);
            Label right = coordLabel("8", COORD, CELL, false);
            host.registerRankCoordLabels(visualRow, left, right);
            surface.add(left, 0, gridRow);
            surface.add(right, 9, gridRow);
        }
        for (int row = 0; row < BoardConstants.BOARD_SIZE; row++) {
            for (int col = 0; col < BoardConstants.BOARD_SIZE; col++) {
                surface.add(cells[row][col], col + 1, row + 1);
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
        host.setFramedOuterSize(outer);
        return framed;
    }

    void paintBoard(Game g) {
        for (int row = 0; row < BoardConstants.BOARD_SIZE; row++) {
            for (int col = 0; col < BoardConstants.BOARD_SIZE; col++) {
                StackPane cell = cells[row][col];
                CellData data = (CellData) cell.getUserData();
                int mf = host.modelFileFromVisualCol(data.fileIndex(), g);
                int mr = host.modelRankFromVisualRow(row, g);
                Position pos = Position.of(mf, mr);
                Piece p = host.effectivePieceAt(g, pos);
                boolean frozen = p != null && isFrozenForDisplay(g, pos);
                if (!host.pieceSkinUsesFigureArt()) {
                    data.pieceImage().setImage(null);
                    data.pieceImage().setVisible(false);
                    data.pieceLabel().setVisible(true);
                    data.pieceLabel().setMouseTransparent(false);
                    data.pieceLabel().setText(p == null ? "" : String.valueOf(p.getType().notationChar()));
                    data.pieceLabel().setTextFill(p == null ? Color.BLACK
                            : (p.getSide() == PlayerSide.GOLD ? Color.color(0.55, 0.35, 0.05) : Color.color(0.25, 0.25, 0.35)));
                } else {
                    if (p == null) {
                        data.pieceImage().setImage(null);
                        data.pieceImage().setVisible(false);
                        data.pieceLabel().setText("");
                        data.pieceLabel().setVisible(false);
                        data.pieceLabel().setMouseTransparent(true);
                    } else {
                        Image img = host.figureRasterCache().getRasterized(p.getSide(), p.getType(), PIECE_IMAGE_MAX);
                        data.pieceImage().setImage(img);
                        boolean showImg = img != null;
                        data.pieceImage().setVisible(showImg);
                        if (showImg) {
                            data.pieceLabel().setText("");
                            data.pieceLabel().setVisible(false);
                            data.pieceLabel().setMouseTransparent(true);
                        } else {
                            data.pieceLabel().setText(String.valueOf(p.getType().notationChar()));
                            data.pieceLabel().setVisible(true);
                            data.pieceLabel().setMouseTransparent(false);
                            data.pieceLabel().setTextFill(p.getSide() == PlayerSide.GOLD
                                    ? Color.color(0.55, 0.35, 0.05)
                                    : Color.color(0.25, 0.25, 0.35));
                        }
                    }
                }
                applyFrozenPieceOpacity(data, frozen && p != null);
            }
        }
    }

    private static boolean isFrozenForDisplay(Game g, Position pos) {
        if (g == null || g.getBoard() == null) {
            return false;
        }
        GameState st = g.getState();
        if (st != GameState.PLAY && st != GameState.GAME_OVER) {
            return false;
        }
        return DefaultRuleEngine.isFrozen(g.getBoard(), pos);
    }

    private static void applyFrozenPieceOpacity(CellData data, boolean frozen) {
        double o = frozen ? FROZEN_PIECE_OPACITY : 1.0;
        data.pieceImage().setOpacity(o);
        data.pieceLabel().setOpacity(o);
    }

    void paintHoverOverlay(Game g) {
        for (int row = 0; row < BoardConstants.BOARD_SIZE; row++) {
            for (int col = 0; col < BoardConstants.BOARD_SIZE; col++) {
                StackPane cell = cells[row][col];
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
                int mf = host.modelFileFromVisualCol(data.fileIndex(), g);
                int mr = host.modelRankFromVisualRow(data.visualRow(), g);
                Position pos = Position.of(mf, mr);
                Integer hfi = host.getHoverFileIndex();
                Integer hvr = host.getHoverVisualRow();
                boolean overHere = setup && hand != null
                        && hfi != null && hvr != null
                        && data.fileIndex() == hfi && data.visualRow() == hvr;
                boolean show = overHere && g.isLegalSetupHandPlacementTarget(pos);
                if (!show) {
                    hImg.setVisible(false);
                    hLbl.setVisible(false);
                    continue;
                }
                if (host.pieceSkinUsesFigureArt()) {
                    Image im = host.figureRasterCache().getRasterized(hand.getSide(), hand.getType(), PIECE_IMAGE_MAX);
                    hImg.setImage(im);
                    hImg.setOpacity(0.5);
                    hImg.setVisible(im != null);
                    hLbl.setText(im == null ? String.valueOf(hand.getType().notationChar()) : "");
                    hLbl.setTextFill(hand.getSide() == PlayerSide.GOLD
                            ? Color.color(0.55, 0.35, 0.05)
                            : Color.color(0.25, 0.25, 0.35));
                    hLbl.setVisible(im == null);
                } else {
                    hImg.setVisible(false);
                    hLbl.setText(String.valueOf(hand.getType().notationChar()));
                    hLbl.setTextFill(hand.getSide() == PlayerSide.GOLD
                            ? Color.color(0.55, 0.35, 0.05)
                            : Color.color(0.25, 0.25, 0.35));
                    hLbl.setVisible(true);
                }
            }
        }
    }

    void refreshAllSquareDecorations(Game g) {
        for (int visualRow = 0; visualRow < BoardConstants.BOARD_SIZE; visualRow++) {
            for (int col = 0; col < BoardConstants.BOARD_SIZE; col++) {
                StackPane cell = cells[visualRow][col];
                CellData data = (CellData) cell.getUserData();
                int mf = host.modelFileFromVisualCol(col, g);
                int mr = host.modelRankFromVisualRow(visualRow, g);
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
                boolean trap = PlayDraftNotationSupport.isStaticTrapSquare(pos);
                bg.setFill(trap ? trapChasmFill(baseFill) : baseFill);
                bg.setStroke(trap ? Color.DARKRED : Color.gray(0.35));
                bg.setStrokeWidth(trap ? 2 : 1);
                bg.getStrokeDashArray().clear();
                bg.setStrokeType(StrokeType.INSIDE);
            }
        }
    }

    /**
     * Centers the framed board and scales it uniformly: {@link Scale} with pivot (0,0) so layout matches
     * painted pixels (unlike {@code setScaleX}, which uses the node centre as pivot and breaks {@code relocate}).
     */
    static final class BoardHostPane extends Pane {

        private final StackPane board;
        private final double outer;
        private final Scale scaleTf = new Scale(1, 1, 0, 0);

        BoardHostPane(StackPane framedBoard, double outer) {
            this.board = framedBoard;
            this.outer = outer;
            board.getTransforms().setAll(scaleTf);
            getChildren().add(board);
            setBackground(javafx.scene.layout.Background.EMPTY);
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
