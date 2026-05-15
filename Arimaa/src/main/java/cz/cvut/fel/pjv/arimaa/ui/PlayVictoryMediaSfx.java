package cz.cvut.fel.pjv.arimaa.ui;

import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Optional victory clip from {@code assets/gold-victory.mp4} or {@code assets/silver-victory.mp4}. Resolved from the
 * working directory, Maven module {@code assets/} or {@code target/assets/}, or {@code assets/} next to the running
 * JAR. Shown in a small modal window with {@link MediaView}.
 *
 * <p>Recommended format: <strong>H.264 video + AAC audio</strong> in MP4. <strong>MP3 audio inside MP4</strong> often
 * triggers {@code ERROR_MEDIA_INVALID} with JavaFX Media (GStreamer) on Windows. Non-ASCII characters in the file
 * path are worked around by copying to a short temp path under {@code %TEMP%} before playback.
 */
final class PlayVictoryMediaSfx {

    private static final Logger log = LoggerFactory.getLogger(PlayVictoryMediaSfx.class);
    private static final String ASSETS_DIR = "assets";
    private static final double VIEW_WIDTH = 720;
    private static final double VIEW_HEIGHT = 405;

    private static Stage activeVictoryStage;

    private PlayVictoryMediaSfx() {}

    static void playWinnerIfPresent(Stage owner, PlayerSide winner) {
        if (winner == null) {
            return;
        }
        String name = winner == PlayerSide.GOLD ? "gold-victory.mp4" : "silver-victory.mp4";
        Optional<Path> sourcePath = resolveVictoryMediaPath(name);
        if (sourcePath.isEmpty()) {
            return;
        }
        PlaybackTarget target = preparePlaybackTarget(sourcePath.get());
        Platform.runLater(() -> playVideoOnFxThread(owner, target.playbackUri(), winner, target.tempCopy()));
    }

    private record PlaybackTarget(String playbackUri, Path tempCopy) {}

    private static Optional<Path> resolveVictoryMediaPath(String fileName) {
        List<Path> candidates = new ArrayList<>();
        candidates.add(Path.of(ASSETS_DIR, fileName));
        String userDir = System.getProperty("user.dir", ".");
        candidates.add(Path.of(userDir, ASSETS_DIR, fileName));
        candidates.add(Path.of(userDir, "target", ASSETS_DIR, fileName));
        Path moduleRoot = tryResolveMavenModuleRoot();
        if (moduleRoot != null) {
            candidates.add(moduleRoot.resolve(ASSETS_DIR).resolve(fileName));
            candidates.add(moduleRoot.resolve("target").resolve(ASSETS_DIR).resolve(fileName));
        }
        Path jarAdjacent = tryJarAdjacentAsset(fileName);
        if (jarAdjacent != null) {
            candidates.add(jarAdjacent);
        }
        for (Path candidate : candidates) {
            Path abs = candidate.toAbsolutePath().normalize();
            if (isReadableMediaFile(abs)) {
                return Optional.of(abs);
            }
        }
        return Optional.empty();
    }

    /**
     * JavaFX Media on Windows often rejects {@code file:} URLs that contain non-ASCII in the path (e.g. {@code á} in
     * folder names). Copy to an ASCII-only temp file when needed.
     */
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

    private static boolean pathHasNonAscii(Path path) {
        return path.toString().chars().anyMatch(ch -> ch > 127);
    }

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

    private static Path tryResolveMavenModuleRoot() {
        try {
            var codeSource = PlayVictoryMediaSfx.class.getProtectionDomain().getCodeSource();
            if (codeSource == null || codeSource.getLocation() == null) {
                return null;
            }
            Path location = Paths.get(codeSource.getLocation().toURI());
            if (Files.isRegularFile(location)) {
                Path targetDir = location.getParent();
                if (targetDir != null && "target".equals(targetDir.getFileName().toString())) {
                    Path mod = targetDir.getParent();
                    if (mod != null && Files.isDirectory(mod.resolve("src"))) {
                        return mod;
                    }
                }
                return null;
            }
            if (Files.isDirectory(location)) {
                Path leaf = location.getFileName();
                if (leaf != null && "classes".equals(leaf.toString())) {
                    Path targetDir = location.getParent();
                    if (targetDir != null && "target".equals(targetDir.getFileName().toString())) {
                        Path mod = targetDir.getParent();
                        if (mod != null && Files.isDirectory(mod.resolve("src"))) {
                            return mod;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        return null;
    }

    private static void playVideoOnFxThread(Stage owner, String uri, PlayerSide winner, Path tempCopy) {
        dismissActiveVictoryStage();
        try {
            Media media = new Media(uri);
            MediaPlayer player = new MediaPlayer(media);
            MediaView view = new MediaView(player);
            view.setPreserveRatio(true);

            StackPane root = new StackPane(view);
            root.setAlignment(Pos.CENTER);
            root.setStyle("-fx-background-color: black;");

            Scene scene = new Scene(root, VIEW_WIDTH + 40, VIEW_HEIGHT + 80);
            view.fitWidthProperty().bind(scene.widthProperty());
            view.fitHeightProperty().bind(scene.heightProperty());

            Stage victoryStage = new Stage(StageStyle.DECORATED);
            victoryStage.initOwner(owner);
            victoryStage.initModality(Modality.NONE);
            victoryStage.setTitle(winner == PlayerSide.GOLD ? "Vítězství — Gold" : "Vítězství — Silver");
            victoryStage.setScene(scene);
            victoryStage.setMinWidth(320);
            victoryStage.setMinHeight(200);
            Runnable closeAndDispose =
                    () -> {
                        view.fitWidthProperty().unbind();
                        view.fitHeightProperty().unbind();
                        dismissActiveVictoryStage();
                        disposeVictoryPlayer(player);
                        if (tempCopy != null) {
                            try {
                                Files.deleteIfExists(tempCopy);
                            } catch (Exception e) {
                                log.debug("victory media: delete temp {}", e.toString());
                            }
                        }
                    };

            victoryStage.setOnHidden(ev -> closeAndDispose.run());

            player.setOnEndOfMedia(() -> Platform.runLater(closeAndDispose));
            player.setOnError(
                    () -> {
                        MediaException err = player.getError();
                        log.warn("victory media playback failed: {}", err != null ? err.getMessage() : "unknown");
                        Platform.runLater(
                                () -> {
                                    showVictoryDecodeErrorAlert(owner, err);
                                    closeAndDispose.run();
                                });
                    });

            activeVictoryStage = victoryStage;
            victoryStage.show();
            player.play();
        } catch (RuntimeException ex) {
            log.debug("victory media: {}", ex.toString());
            if (tempCopy != null) {
                try {
                    Files.deleteIfExists(tempCopy);
                } catch (Exception ignored) {
                    // ignore
                }
            }
        }
    }

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

    private static void dismissActiveVictoryStage() {
        Stage s = activeVictoryStage;
        activeVictoryStage = null;
        if (s != null) {
            try {
                s.close();
            } catch (Exception ignored) {
                // ignore
            }
        }
    }

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
