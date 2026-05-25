package cz.cvut.fel.pjv.arimaa.ui;

import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Optional;

/**
 * Optional victory clip from {@code assets/gold-victory.mp4} or {@code assets/silver-victory.mp4}, played as an overlay
 * on the 8×8 cell area only ({@link #installOnCellArea}), scaled with the playing field (coordinates stay visible).
 *
 * <p>Resolution order: (1) {@code assets/} next to the running fat JAR if present; (2) same files embedded in the JAR
 * (built from module {@code assets/} at package time). External files override bundled clips without repackaging.
 *
 * <p>Recommended format: <strong>H.264 video + AAC audio</strong> in MP4. <strong>MP3 audio inside MP4</strong> often
 * triggers {@code ERROR_MEDIA_INVALID} with JavaFX Media (GStreamer) on Windows.
 */
final class PlayVictoryMediaSfx {

    private static final Logger log = LoggerFactory.getLogger(PlayVictoryMediaSfx.class);
    private static final String ASSETS_DIR = "assets";

    private static StackPane overlayRoot;
    private static Region sizeHost;
    private static MediaView mediaView;
    private static MediaPlayer activePlayer;
    private static Path activeTempCopy;

    private PlayVictoryMediaSfx() {}

    /**
     * Adds an overlay on the 8×8 cell grid only (file/rank labels remain visible above and below). Call once from
     * {@link MainController#attachToStage} after {@link BoardGridView#buildFramedBoardWithPerimeterCoordinates()}.
     */
    static void installOnCellArea(StackPane cellAreaHost) {
        overlayRoot = new StackPane();
        overlayRoot.setAlignment(Pos.CENTER);
        overlayRoot.setStyle("-fx-background-color: rgba(0, 0, 0, 0.72);");
        overlayRoot.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        overlayRoot.setVisible(false);
        overlayRoot.setManaged(false);
        overlayRoot.setMouseTransparent(true);

        sizeHost = cellAreaHost;
        mediaView = new MediaView();
        mediaView.setPreserveRatio(true);
        mediaView.fitWidthProperty().bind(sizeHost.widthProperty());
        mediaView.fitHeightProperty().bind(sizeHost.heightProperty());
        overlayRoot.getChildren().add(mediaView);
        cellAreaHost.getChildren().add(overlayRoot);
    }

    static void playWinnerIfPresent(Stage alertOwner, PlayerSide winner) {
        if (winner == null || overlayRoot == null || mediaView == null) {
            return;
        }
        String name = winner == PlayerSide.GOLD ? "gold-victory.mp4" : "silver-victory.mp4";
        Optional<PlaybackTarget> target = resolveVictoryPlaybackTarget(name);
        if (target.isEmpty()) {
            return;
        }
        PlaybackTarget t = target.get();
        Platform.runLater(() -> playOnBoardOverlay(alertOwner, t.playbackUri(), t.tempCopy()));
    }

    /** Stops playback and hides the board overlay (e.g. new game). */
    static void dismiss() {
        Platform.runLater(PlayVictoryMediaSfx::dismissOnFxThread);
    }

    /**
     * When the victory overlay is visible: Space pauses/resumes, Escape stops. Call from a scene {@code KEY_PRESSED}
     * filter (capture phase) before gameplay shortcuts.
     *
     * @return {@code true} if the event was consumed
     */
    static boolean interceptVictoryMediaKeyPress(KeyEvent e) {
        if (overlayRoot == null || !overlayRoot.isVisible() || activePlayer == null) {
            return false;
        }
        if (e.getCode() == KeyCode.SPACE && !e.isControlDown() && !e.isAltDown()) {
            toggleVictoryPauseResume();
            e.consume();
            return true;
        }
        if (e.getCode() == KeyCode.ESCAPE) {
            dismissOnFxThread();
            e.consume();
            return true;
        }
        return false;
    }

    /* URI string plus optional temp file to delete after playback (non-ASCII paths / embedded extract). */
    private record PlaybackTarget(String playbackUri, Path tempCopy) {}

    /**
     * Jar-adjacent {@code assets/<file>} overrides clips embedded from {@code Arimaa/assets/} at build time.
     */
    private static Optional<PlaybackTarget> resolveVictoryPlaybackTarget(String fileName) {
        Path jarAdjacent = tryJarAdjacentAsset(fileName);
        if (jarAdjacent != null) {
            Path abs = jarAdjacent.toAbsolutePath().normalize();
            if (isReadableMediaFile(abs)) {
                log.debug("victory media: jar-adjacent {}", abs);
                return Optional.of(preparePlaybackTarget(abs));
            }
        }
        return materializeEmbeddedPlaybackTarget(fileName);
    }

    /* Space handler: pause/resume the victory MediaPlayer when overlay is visible. */
    private static void toggleVictoryPauseResume() {
        MediaPlayer player = activePlayer;
        if (player == null) {
            return;
        }
        MediaPlayer.Status st = player.getStatus();
        if (st == MediaPlayer.Status.PLAYING) {
            player.pause();
        } else if (st == MediaPlayer.Status.PAUSED) {
            player.play();
        }
    }

    /* Returns file URI or temp-file URI when path contains non-ASCII (JavaFX Media quirk on Windows). */
    private static PlaybackTarget preparePlaybackTarget(Path sourceAbs) {
        Path abs = sourceAbs.toAbsolutePath().normalize();
        if (!pathHasNonAscii(abs)) {
            return new PlaybackTarget(abs.toUri().toString(), null);
        }
        try {
            Path tmp = Files.createTempFile("arimaa-victory-", ".mp4");
            Files.copy(abs, tmp, StandardCopyOption.REPLACE_EXISTING);
            return new PlaybackTarget(tmp.toUri().toString(), tmp);
        } catch (Exception ex) {
            log.debug("victory media: temp copy failed, using source path: {}", ex.toString());
            return new PlaybackTarget(abs.toUri().toString(), null);
        }
    }

    /* Copies bundled classpath resource to a temp file (JavaFX Media needs a real file URI). */
    private static Optional<PlaybackTarget> materializeEmbeddedPlaybackTarget(String fileName) {
        String resourcePath = ASSETS_DIR + "/" + fileName;
        ClassLoader loader = PlayVictoryMediaSfx.class.getClassLoader();
        try (InputStream in = loader.getResourceAsStream(resourcePath)) {
            if (in == null) {
                log.warn(
                        "victory media: embedded {} missing (rebuild with Arimaa/assets/ or place assets/ next to JAR).",
                        resourcePath);
                return Optional.empty();
            }
            Path tmp = Files.createTempFile("arimaa-victory-", ".mp4");
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            log.debug("victory media: embedded {} → {}", resourcePath, tmp);
            return Optional.of(new PlaybackTarget(tmp.toUri().toString(), tmp));
        } catch (Exception ex) {
            log.warn("victory media: embedded {} failed: {}", resourcePath, ex.toString());
            return Optional.empty();
        }
    }

    /* True if the path string has code points above ASCII (triggers temp copy for Media). */
    private static boolean pathHasNonAscii(Path path) {
        return path.toString().chars().anyMatch(ch -> ch > 127);
    }

    /* Quick readability probe: regular file and at least one byte readable. */
    private static boolean isReadableMediaFile(Path file) {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        try (InputStream in = Files.newInputStream(file)) {
            return in.read() >= 0;
        } catch (Exception ex) {
            log.debug("victory media: cannot open {}: {}", file, ex.toString());
            return false;
        }
    }

    /* assets/ next to the running JAR (distribution override). */
    private static Path tryJarAdjacentAsset(String fileName) {
        try {
            var codeSource = PlayVictoryMediaSfx.class.getProtectionDomain().getCodeSource();
            if (codeSource == null || codeSource.getLocation() == null) {
                return null;
            }
            Path location = Paths.get(codeSource.getLocation().toURI());
            if (!Files.isRegularFile(location)) {
                return null;
            }
            String jarName = location.getFileName().toString().toLowerCase(Locale.ROOT);
            if (!jarName.endsWith(".jar")) {
                return null;
            }
            Path jarDir = location.getParent();
            if (jarDir == null) {
                return null;
            }
            return jarDir.resolve(ASSETS_DIR).resolve(fileName);
        } catch (Exception ex) {
            log.debug("victory media: jar-adjacent lookup failed: {}", ex.toString());
            return null;
        }
    }

    /* FX thread: shows overlay, wires end/error handlers, plays Media (dismissOnFxThread clears prior state). */
    private static void playOnBoardOverlay(Stage alertOwner, String uri, Path tempCopy) {
        dismissOnFxThread();
        try {
            Media media = new Media(uri);
            MediaPlayer player = new MediaPlayer(media);
            mediaView.setMediaPlayer(player);
            activePlayer = player;
            activeTempCopy = tempCopy;

            overlayRoot.setVisible(true);
            overlayRoot.setManaged(true);

            Runnable closeAndDispose =
                    () -> {
                        dismissOnFxThread();
                        if (tempCopy != null) {
                            try {
                                Files.deleteIfExists(tempCopy);
                            } catch (Exception e) {
                                log.debug("victory media: delete temp {}", e.toString());
                            }
                        }
                    };

            player.setOnEndOfMedia(() -> Platform.runLater(closeAndDispose));
            player.setOnError(
                    () -> {
                        MediaException err = player.getError();
                        log.warn("victory media playback failed: {}", err != null ? err.getMessage() : "unknown");
                        Platform.runLater(
                                () -> {
                                    showVictoryDecodeErrorAlert(alertOwner, err);
                                    closeAndDispose.run();
                                });
                    });

            player.play();
        } catch (RuntimeException ex) {
            log.debug("victory media: {}", ex.toString());
            dismissOnFxThread();
            if (tempCopy != null) {
                try {
                    Files.deleteIfExists(tempCopy);
                } catch (Exception ignored) {
                    // ignore
                }
            }
        }
    }

    /* Hides overlay, detaches MediaView, stops/disposes player, deletes temp copy if any. */
    private static void dismissOnFxThread() {
        if (overlayRoot != null) {
            overlayRoot.setVisible(false);
            overlayRoot.setManaged(false);
        }
        if (mediaView != null) {
            mediaView.setMediaPlayer(null);
        }
        MediaPlayer player = activePlayer;
        activePlayer = null;
        Path tmp = activeTempCopy;
        activeTempCopy = null;
        disposeVictoryPlayer(player);
        if (tmp != null) {
            try {
                Files.deleteIfExists(tmp);
            } catch (Exception ignored) {
                // ignore
            }
        }
    }

    /* Warning dialog when GStreamer cannot decode the victory clip (codec hints in Czech). */
    private static void showVictoryDecodeErrorAlert(Stage owner, MediaException err) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.setTitle("Výherní video");
        alert.setHeaderText("Soubor nelze přehrát (JavaFX / GStreamer).");
        StringBuilder body = new StringBuilder();
        body.append(
                "Doporučený formát: MP4 s videem H.264 a zvukem AAC. Zvuk MP3 uvnitř MP4 na Windows často končí chybou ERROR_MEDIA_INVALID.\n\n");
        body.append("Příklad (ffmpeg): … -c:v libx264 -crf 28 -c:a aac -b:a 128k -movflags +faststart výstup.mp4\n\n");
        if (err != null && err.getMessage() != null) {
            body.append("Technicky: ").append(err.getMessage());
        }
        alert.setContentText(body.toString());
        alert.showAndWait();
    }

    /* Best-effort stop/dispose so native player resources are released before a new clip. */
    private static void disposeVictoryPlayer(MediaPlayer player) {
        if (player == null) {
            return;
        }
        try {
            player.stop();
        } catch (Exception ignored) {
            // ignore
        }
        try {
            player.dispose();
        } catch (Exception ignored) {
            // ignore
        }
    }
}
