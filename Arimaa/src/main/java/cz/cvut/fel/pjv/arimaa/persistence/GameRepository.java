package cz.cvut.fel.pjv.arimaa.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Thin UTF-8 file I/O for save/load strings produced by {@link GameSerializer}.
 */
public final class GameRepository {

    /** Writes entire text to {@code path} (overwrite). */
    public void saveUtf8(Path path, String text) throws IOException {
        Files.writeString(path, text, StandardCharsets.UTF_8);
    }

    /** Reads whole file as a single UTF-8 string. */
    public String loadUtf8(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
