package cz.cvut.fel.pjv.arimaa.ui;

import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.enums.GameState;
import cz.cvut.fel.pjv.arimaa.model.Move;
import cz.cvut.fel.pjv.arimaa.model.PlayHalfTurn;
import cz.cvut.fel.pjv.arimaa.model.PlayTurnHistory;
import cz.cvut.fel.pjv.arimaa.model.Step;
import cz.cvut.fel.pjv.arimaa.persistence.GameRepository;
import cz.cvut.fel.pjv.arimaa.persistence.GameSerializer;
import cz.cvut.fel.pjv.arimaa.util.ArimaaNotation;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Save / load game text files via {@link FileChooser}; updates {@link MainController} last-used directory and status.
 */
public final class ArimaaSaveLoadSupport {

    private static final Logger log = LoggerFactory.getLogger(ArimaaSaveLoadSupport.class);

    private final MainController ui;

    /** Binds save/load dialogs to the main window controller (file chooser + status updates). */
    public ArimaaSaveLoadSupport(MainController ui) {
        this.ui = ui;
    }

    /**
     * Writes current game via {@link GameSerializer} (includes trailing PLAY draft line when present);
     * invoked from the File menu.
     */
    public void saveGameToFile() {
        if (ui.gameController == null || ui.stage == null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Uložit hru");
        chooser.getExtensionFilters().add(new ExtensionFilter("Arimaa (*.txt)", "*.txt"));
        if (ui.lastUsedDir != null && ui.lastUsedDir.isDirectory()) {
            chooser.setInitialDirectory(ui.lastUsedDir);
        }
        File file = chooser.showSaveDialog(ui.stage);
        if (file == null) {
            return;
        }
        ui.lastUsedDir = file.getParentFile();
        Path path = file.toPath();
        Game g = ui.game();
        String draft = null;
        if (g != null && g.getState() == GameState.PLAY && ui.gameController.getPlayHistory().isBootstrapped()) {
            PlayTurnHistory ph = ui.gameController.getPlayHistory();
            List<PlayHalfTurn> halves = ph.halfTurnsUnmodifiable();
            PlayHalfTurn tail = halves.get(halves.size() - 1);
            if (!tail.committed() && !tail.steps().isEmpty()) {
                Game probe = PlayDraftNotationSupport.probeGameFromMemento(tail.startSnap());
                String prefix = ui.gameController.nextPlayNotationPrefix();
                Move m = new Move();
                for (Step s : tail.steps()) {
                    m.getSteps().add(PlayDraftNotationSupport.copyStep(s));
                }
                draft = ArimaaNotation.formatPartialTurnLine(probe.getBoard(), m, prefix);
            }
        }
        GameSerializer ser = new GameSerializer();
        String text = ser.serialize(ui.gameController, draft);
        try {
            new GameRepository().saveUtf8(path, text);
            ui.setStatus("Hra uložena do souboru.");
            log.info("user action: game saved to {}", path);
        } catch (IOException ex) {
            log.warn("save failed", ex);
            ui.setStatus("Uložení se nepovedlo", ex.getMessage());
        }
    }

    /** Loads a .txt snapshot + moves into {@link MainController#gameController} and refreshes PLAY UI state. */
    public void loadGameFromFile() {
        if (ui.gameController == null || ui.stage == null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Načíst hru");
        chooser.getExtensionFilters().add(new ExtensionFilter("Arimaa (*.txt)", "*.txt"));
        if (ui.lastUsedDir != null && ui.lastUsedDir.isDirectory()) {
            chooser.setInitialDirectory(ui.lastUsedDir);
        }
        File file = chooser.showOpenDialog(ui.stage);
        if (file == null) {
            return;
        }
        ui.lastUsedDir = file.getParentFile();
        Path path = file.toPath();
        try {
            String text = new GameRepository().loadUtf8(path);
            GameSerializer ser = new GameSerializer();
            GameSerializer.ParsedTxtGame p = ser.parse(text);
            ui.clearPlayTurnUi();
            ui.gameController.loadFromTxtGame(p.playStartSnapshot(), p.moveLines());
            ui.syncPlayPartialFromHistory();
            log.info("user action: game loaded from {}", path);
            ui.playbackLoadedHistory();
        } catch (IOException ex) {
            log.warn("load failed", ex);
            ui.setStatus("Načtení se nepovedlo", ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("load parse/replay failed", ex);
            ui.setStatus("Soubor nelze načíst", ex.getMessage());
        }
    }
}
