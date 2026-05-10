package cz.cvut.fel.pjv.arimaa.ui;

import javafx.application.Platform;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.scene.control.Label;
import javafx.util.Duration;

/**
 * Periodically formats {@link PlayChessClockModel} on a worker thread and pushes label updates onto the JavaFX
 * application thread via {@link Platform#runLater(Runnable)}.
 */
public final class PlayChessClockTicker {

    private final PlayChessClockModel model;
    private final Label goldTotalLabel;
    private final Label goldAvgLabel;
    private final Label silverTotalLabel;
    private final Label silverAvgLabel;

    private final ScheduledService<Void> service =
            new ScheduledService<>() {
                @Override
                protected Task<Void> createTask() {
                    return new Task<>() {
                        @Override
                        protected Void call() {
                            PlayChessClockModel.ClockTableSnapshot snap = model.snapshotClockTable();
                            Platform.runLater(
                                    () -> {
                                        goldTotalLabel.setText(snap.goldTotal());
                                        goldAvgLabel.setText(snap.goldAvg());
                                        silverTotalLabel.setText(snap.silverTotal());
                                        silverAvgLabel.setText(snap.silverAvg());
                                    });
                            return null;
                        }
                    };
                }
            };

    public PlayChessClockTicker(
            PlayChessClockModel model,
            Label goldTotalLabel,
            Label goldAvgLabel,
            Label silverTotalLabel,
            Label silverAvgLabel) {
        this.model = model;
        this.goldTotalLabel = goldTotalLabel;
        this.goldAvgLabel = goldAvgLabel;
        this.silverTotalLabel = silverTotalLabel;
        this.silverAvgLabel = silverAvgLabel;
        service.setPeriod(Duration.millis(200));
    }

    public void start() {
        service.start();
    }

    public void stop() {
        service.cancel();
    }
}
