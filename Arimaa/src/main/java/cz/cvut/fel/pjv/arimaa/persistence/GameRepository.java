package cz.cvut.fel.pjv.arimaa.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * UTF-8 text persistence for {@link GameSerializer} output.
 */
public final class GameRepository {

    public void saveUtf8(Path path, String text) throws IOException {
        Files.writeString(path, text, StandardCharsets.UTF_8);
    }

    public String loadUtf8(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
