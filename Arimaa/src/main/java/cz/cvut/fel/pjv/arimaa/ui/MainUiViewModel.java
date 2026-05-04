package cz.cvut.fel.pjv.arimaa.ui;

import cz.cvut.fel.pjv.arimaa.model.Game;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

/**
 * JavaFX properties for side-panel visibility; kept in sync from {@link MainController#refreshAll()}.
 */
final class MainUiViewModel {

    private final BooleanProperty setupPanelActive = new SimpleBooleanProperty(false);
    private final BooleanProperty capturesAndNotationActive = new SimpleBooleanProperty(false);

    void syncFromGame(Game g) {
        setupPanelActive.set(MainUiLayoutPhase.isSetup(g));
        capturesAndNotationActive.set(MainUiLayoutPhase.showCapturesAndNotationHistory(g));
    }

    void bindSidePanelVisibility(MainController m) {
        m.reserveBox.visibleProperty().bind(setupPanelActive);
        m.reserveBox.managedProperty().bind(setupPanelActive);
        m.cancelHandButton.visibleProperty().bind(setupPanelActive);
        m.cancelHandButton.managedProperty().bind(setupPanelActive);
        m.randomButton.visibleProperty().bind(setupPanelActive);
        m.randomButton.managedProperty().bind(setupPanelActive);
        m.chessButton.visibleProperty().bind(setupPanelActive);
        m.chessButton.managedProperty().bind(setupPanelActive);
        m.doneButton.visibleProperty().bind(setupPanelActive);
        m.doneButton.managedProperty().bind(setupPanelActive);

        m.capturesBox.visibleProperty().bind(capturesAndNotationActive);
        m.capturesBox.managedProperty().bind(capturesAndNotationActive);
        m.notationBox.visibleProperty().bind(capturesAndNotationActive);
        m.notationBox.managedProperty().bind(capturesAndNotationActive);
    }
}
