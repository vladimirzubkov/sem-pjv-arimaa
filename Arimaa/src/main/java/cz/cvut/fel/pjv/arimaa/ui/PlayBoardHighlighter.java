package cz.cvut.fel.pjv.arimaa.ui;

import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.Position;
import cz.cvut.fel.pjv.arimaa.model.Step;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeType;

import java.util.Set;
import java.util.function.Function;

/**
 * PLAY-mode cell highlights (selection, legal slides, push first steps, pull targets, keyboard focus rings).
 */
final class PlayBoardHighlighter {

    private static final Color ORIGIN_GOLD_TINT = Color.web("#fff4d6");
    private static final Color ORIGIN_GOLD_STROKE = Color.web("#d9b24a");
    private static final Color ORIGIN_SILVER_TINT = Color.web("#e8eef2");
    private static final Color ORIGIN_SILVER_STROKE = Color.web("#8b97a3");
    private static final Color LAST_FROM_SALAD = Color.web("#dff3dc");
    private static final Color SELECTED_FILL_TINT = Color.web("#ffec99");
    private static final Color SELECTED_STROKE = Color.web("#b8860b");
    private static final Color SLIDE_TINT = Color.web("#a8f0c0");
    private static final Color SLIDE_STROKE = Color.web("#1e7a3a");
    private static final Color PUSH_TINT = Color.web("#ffd4a8");
    private static final Color PUSH_STROKE = Color.web("#c45c19");
    private static final Color PULL_RING = Color.web("#b030c0");
    private static final Color KEYBOARD_FOCUS_RING = Color.web("#ffcc33");

    private static final double ORIGIN_STROKE_W = 2.5;
    private static final double TARGET_STROKE_W = 2.5;
    private static final double SELECTED_STROKE_W = 3;
    private static final double PULL_STROKE_W = 3;
    private static final double FOCUS_STROKE_W = 4;

    private PlayBoardHighlighter() {}

    static void paint(
            Game g,
            PlayTurnDraftState draft,
            PlayTargetBundle targets,
            Function<Position, BoardGridView.CellData> cellAt) {
        Set<Position> pullTargets = targets.pullWeakSquares();
        Set<Position> pushTargets = targets.pushFirstStepTargets();
        if (draft.keyboardPullFocus != null && (pullTargets.isEmpty() || !pullTargets.contains(draft.keyboardPullFocus))) {
            draft.keyboardPullFocus = null;
        }
        if (draft.keyboardPushFocus != null && (pushTargets.isEmpty() || !pushTargets.contains(draft.keyboardPushFocus))) {
            draft.keyboardPushFocus = null;
        }
        PlayerSide mover = g.getSideToMove();
        Position origin = draft.activeSegmentOrigin;
        if (origin != null && !origin.equals(draft.nextFrom)) {
            BoardGridView.CellData originData = cellAt.apply(origin);
            Rectangle obg = originData.background();
            Color fill;
            Color stroke;
            if (mover == PlayerSide.GOLD) {
                fill = cellBaseFill(originData).interpolate(ORIGIN_GOLD_TINT, 0.55);
                stroke = ORIGIN_GOLD_STROKE;
            } else {
                fill = cellBaseFill(originData).interpolate(ORIGIN_SILVER_TINT, 0.5);
                stroke = ORIGIN_SILVER_STROKE;
            }
            obg.setFill(fill);
            obg.setStroke(stroke);
            obg.setStrokeWidth(ORIGIN_STROKE_W);
            obg.getStrokeDashArray().clear();
            obg.setStrokeType(StrokeType.INSIDE);
        }
        if (!draft.partial.getSteps().isEmpty()) {
            Step lastStep = draft.partial.getSteps().get(draft.partial.getSteps().size() - 1);
            Position lastFrom = lastStep.getFrom();
            BoardGridView.CellData fromData = cellAt.apply(lastFrom);
            Rectangle fromBg = fromData.background();
            Paint fp = fromBg.getFill();
            Color baseTint = fp instanceof Color fc ? fc : cellBaseFill(fromData);
            fromBg.setFill(baseTint.interpolate(LAST_FROM_SALAD, 0.38));
            fromBg.setStrokeType(StrokeType.INSIDE);
        }
        BoardGridView.CellData selected = cellAt.apply(draft.nextFrom);
        Rectangle selBg = selected.background();
        selBg.setFill(cellBaseFill(selected).interpolate(SELECTED_FILL_TINT, 0.42));
        selBg.setStroke(SELECTED_STROKE);
        selBg.setStrokeWidth(SELECTED_STROKE_W);
        selBg.setStrokeType(StrokeType.INSIDE);
        if (draft.partial.getSteps().size() >= 4) {
            return;
        }
        for (Position to : targets.legalSlideTargets()) {
            BoardGridView.CellData tdata = cellAt.apply(to);
            Rectangle tbg = tdata.background();
            boolean isPush = pushTargets.contains(to);
            Color tint = isPush ? PUSH_TINT : SLIDE_TINT;
            Color stroke = isPush ? PUSH_STROKE : SLIDE_STROKE;
            tbg.setFill(cellBaseFill(tdata).interpolate(tint, isPush ? 0.5 : 0.48));
            tbg.setStroke(stroke);
            tbg.setStrokeWidth(TARGET_STROKE_W);
            tbg.setStrokeType(StrokeType.INSIDE);
        }
        for (Position opp : pullTargets) {
            BoardGridView.CellData odata = cellAt.apply(opp);
            Rectangle obg = odata.background();
            obg.setStroke(PULL_RING);
            obg.setStrokeWidth(PULL_STROKE_W);
            obg.setStrokeType(StrokeType.INSIDE);
        }
        if (draft.keyboardPullFocus != null && pullTargets.contains(draft.keyboardPullFocus)) {
            BoardGridView.CellData kdata = cellAt.apply(draft.keyboardPullFocus);
            Rectangle kbg = kdata.background();
            kbg.setStroke(KEYBOARD_FOCUS_RING);
            kbg.setStrokeWidth(FOCUS_STROKE_W);
            kbg.getStrokeDashArray().clear();
            kbg.setStrokeType(StrokeType.INSIDE);
        }
        if (draft.keyboardPushFocus != null && pushTargets.contains(draft.keyboardPushFocus)) {
            BoardGridView.CellData pdata = cellAt.apply(draft.keyboardPushFocus);
            Rectangle pbg = pdata.background();
            pbg.setStroke(KEYBOARD_FOCUS_RING);
            pbg.setStrokeWidth(FOCUS_STROKE_W);
            pbg.getStrokeDashArray().clear();
            pbg.setStrokeType(StrokeType.INSIDE);
        }
    }

    private static Color cellBaseFill(BoardGridView.CellData d) {
        Paint p = d.background().getFill();
        return p instanceof Color c ? c : d.baseFill();
    }
}
