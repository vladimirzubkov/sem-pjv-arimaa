package cz.cvut.fel.pjv.arimaa.ui;

import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;
import javafx.application.Platform;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Optional short victory clip from {@code assets/gold-victory.mp4} or {@code assets/silver-victory.mp4} next to the
 * working directory. Playback is skipped when the folder or file is missing or unreadable.
 */
final class PlayVictoryMediaSfx {

    private static final Logger log = LoggerFactory.getLogger(PlayVictoryMediaSfx.class);
    private static final String ASSETS_DIR = "assets";

    private PlayVictoryMediaSfx() {}

    static void playWinnerIfPresent(PlayerSide winner) {
        if (winner == null) {
            return;
        }
        Path dir = Path.of(ASSETS_DIR);
        if (!Files.isDirectory(dir)) {
            return;
        }
        String name = winner == PlayerSide.GOLD ? "gold-victory.mp4" : "silver-victory.mp4";
        Path file = dir.resolve(name);
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (InputStream in = Files.newInputStream(file)) {
            if (in.read() < 0) {
                return;
            }
        } catch (Exception ex) {
            log.debug("victory media: cannot open {}: {}", file.toAbsolutePath(), ex.toString());
            return;
        }
        String uri = file.toAbsolutePath().toUri().toString();
        Platform.runLater(() -> playUriOnFxThread(uri));
    }

    private static void playUriOnFxThread(String uri) {
        try {
            Media media = new Media(uri);
            MediaPlayer player = new MediaPlayer(media);
            player.setOnEndOfMedia(
                    () -> {
                        try {
                            player.dispose();
                        } catch (Exception ignored) {
                            // ignore
                        }
                    });
            player.setOnError(
                    () -> {
                        try {
                            player.dispose();
                        } catch (Exception ignored) {
                            // ignore
                        }
                    });
            player.play();
        } catch (RuntimeException ex) {
            log.debug("victory media: {}", ex.toString());
        }
    }
}
