package cz.cvut.fel.pjv.arimaa.ui;

import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.Piece;
import cz.cvut.fel.pjv.arimaa.model.Position;
import javafx.scene.control.Label;

/**
 * Narrow contract for {@link BoardGridView}: board painting and clicks without a dependency on {@link MainController}.
 */
public interface BoardViewHost {

    void setHoverCell(int fileIndex, int visualRow);

    void clearHoverCellIf(int fileIndex, int visualRow);

    void onBoardCellClick(int fileIndex, int visualRow);

    void registerFileCoordLabels(int fileIndex, Label top, Label bottom);

    void registerRankCoordLabels(int visualRow, Label left, Label right);

    void setFramedOuterSize(double outer);

    int modelFileFromVisualCol(int visualCol, Game g);

    int modelRankFromVisualRow(int visualRow, Game g);

    Piece effectivePieceAt(Game g, Position pos);

    boolean pieceSkinUsesFigureArt();

    FigureSvgRasterCache figureRasterCache();

    Integer getHoverFileIndex();

    Integer getHoverVisualRow();
}
