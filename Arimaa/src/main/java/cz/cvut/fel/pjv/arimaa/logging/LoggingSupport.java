package cz.cvut.fel.pjv.arimaa.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.FileAppender;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Objects;

/**
 * Programmatic Logback configuration for the Arimaa application: root logger level, optional file
 * appender, CLI and JVM overrides, and runtime changes from the UI.
 */
public final class LoggingSupport {

    /**
     * JVM system property read when no {@code --log-level=} argument is present (e.g.
     * {@code -Dcz.cvut.fel.pjv.arimaa.log.level=DEBUG}).
     */
    public static final String LOG_LEVEL_PROPERTY = "cz.cvut.fel.pjv.arimaa.log.level";

    /**
     * JVM system property: non-empty path enables file logging at startup (same as {@code --log-file=}).
     */
    public static final String LOG_FILE_PROPERTY = "cz.cvut.fel.pjv.arimaa.log.file";

    private static final String CLI_LOG_LEVEL_PREFIX = "--log-level=";
    private static final String CLI_LOG_FILE_PREFIX = "--log-file=";

    /** Name of the programmatic file appender on the root logger. */
    public static final String FILE_APPENDER_NAME = "ARO_FILE";

    private static final String LOG_PATTERN = "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n";

    /** Relative file name in the module root (next to {@code src}) when that root can be resolved. */
    private static final String DEFAULT_LOG_FILE_NAME = "arimaa.log";

    private static volatile Path lastFileLogPath;

    private LoggingSupport() {
    }

    /**
     * Ensures Logback is loaded, sets the root logger level from CLI or {@value #LOG_LEVEL_PROPERTY},
     * then optionally attaches a file appender from {@code --log-file=} or {@value #LOG_FILE_PROPERTY}.
     *
     * @param args raw {@code main} arguments (may be {@code null})
     */
    public static void bootstrapFromArgs(String[] args) {
        Level fromCli = parseLogLevelFromArgs(args);
        final Level chosen;
        if (fromCli != null) {
            chosen = fromCli;
        } else {
            String prop = System.getProperty(LOG_LEVEL_PROPERTY);
            Level fromProp = parseLevel(prop);
            if (prop != null && !prop.isBlank() && fromProp == null) {
                System.err.println("Unknown log level for " + LOG_LEVEL_PROPERTY + "=" + prop + " — using OFF");
            }
            chosen = fromProp != null ? fromProp : Level.OFF;
        }
        LoggerFactory.getLogger(LoggingSupport.class);
        setLevel(chosen);

        String filePath = parseLogFileFromArgs(args);
        if (filePath == null || filePath.isBlank()) {
            filePath = System.getProperty(LOG_FILE_PROPERTY);
        }
        if (filePath != null && !filePath.isBlank()) {
            try {
                enableFileLogging(Path.of(filePath.trim()));
            } catch (Exception ex) {
                System.err.println("Could not enable file logging from startup: " + ex.getMessage());
            }
        }
    }

    /**
     * Sets the Logback root logger level. Passing {@code null} is treated as {@link Level#OFF}.
     *
     * @param level target level, or {@code null} for OFF
     */
    public static void setLevel(Level level) {
        Level effective = level != null ? level : Level.OFF;
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.setLevel(effective);
    }

    /**
     * @return the current root logger level, or {@link Level#OFF} if unset
     */
    public static Level getCurrentLevel() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        Level level = root.getLevel();
        return level != null ? level : Level.OFF;
    }

    /**
     * Default log file used when the user enables file logging from the UI without choosing a path.
     * Resolves to {@code arimaa.log} in the Maven module directory (sibling of {@code src})
     * when the code runs from {@code target/classes} or a {@code .jar} under {@code target/}; otherwise
     * {@code user.dir/arimaa.log}.
     *
     * @return absolute path to the default log file
     */
    public static Path defaultLogFilePath() {
        Path dir = resolveDefaultLogDirectory();
        return dir.resolve(DEFAULT_LOG_FILE_NAME).toAbsolutePath().normalize();
    }

    private static Path resolveDefaultLogDirectory() {
        Path moduleRoot = tryResolveMavenModuleRoot();
        if (moduleRoot != null) {
            return moduleRoot;
        }
        return Path.of(System.getProperty("user.dir"));
    }

    /**
     * Finds {@code …/Arimaa} when classpath is {@code …/Arimaa/target/classes} or a jar in {@code …/Arimaa/target/}.
     */
    private static Path tryResolveMavenModuleRoot() {
        try {
            var codeSource = LoggingSupport.class.getProtectionDomain().getCodeSource();
            if (codeSource == null || codeSource.getLocation() == null) {
                return null;
            }
            Path location = Paths.get(codeSource.getLocation().toURI());
            if (Files.isRegularFile(location)) {
                String name = location.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!name.endsWith(".jar")) {
                    return null;
                }
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
            // use user.dir
        }
        return null;
    }

    /**
     * Attaches a {@link FileAppender} named {@value #FILE_APPENDER_NAME} to the root logger. If an appender
     * with that name already exists, it is replaced when the path differs; if the path is the same, no-op.
     *
     * @param path destination log file (parent directories are created if missing)
     * @return {@code true} if the appender is attached and started
     */
    public static boolean enableFileLogging(Path path) {
        Objects.requireNonNull(path, "path");
        Path absolute = path.toAbsolutePath().normalize();
        try {
            Path parent = absolute.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (Exception ex) {
            System.err.println("Could not create log directory for " + absolute + ": " + ex.getMessage());
            return false;
        }

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);

        Appender existing = root.getAppender(FILE_APPENDER_NAME);
        if (existing != null) {
            Path current = lastFileLogPath;
            if (current != null && current.equals(absolute)) {
                return true;
            }
            disableFileLogging();
        }

        try {
            PatternLayoutEncoder encoder = new PatternLayoutEncoder();
            encoder.setContext(context);
            encoder.setPattern(LOG_PATTERN);
            encoder.start();

            FileAppender<ILoggingEvent> appender = new FileAppender<>();
            appender.setContext(context);
            appender.setName(FILE_APPENDER_NAME);
            appender.setFile(absolute.toString());
            appender.setAppend(true);
            appender.setEncoder(encoder);
            appender.start();

            root.addAppender(appender);
            lastFileLogPath = absolute;
            return true;
        } catch (Exception ex) {
            System.err.println("Could not enable file logging at " + absolute + ": " + ex.getMessage());
            return false;
        }
    }

    /**
     * Removes and stops the file appender {@value #FILE_APPENDER_NAME} from the root logger, if present.
     */
    public static void disableFileLogging() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        Appender app = root.getAppender(FILE_APPENDER_NAME);
        if (app != null) {
            root.detachAppender(FILE_APPENDER_NAME);
            app.stop();
        }
        lastFileLogPath = null;
    }

    /**
     * @return {@code true} if the root logger has the {@value #FILE_APPENDER_NAME} appender
     */
    public static boolean isFileLoggingEnabled() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        return root.getAppender(FILE_APPENDER_NAME) != null;
    }

    /**
     * @return last successfully enabled file path, or {@code null}
     */
    public static Path getFileLogPath() {
        return lastFileLogPath;
    }

    /**
     * Parses a level name (case-insensitive). Recognizes OFF, NONE, ERROR, WARN, INFO, DEBUG, TRACE, ALL.
     *
     * @param name level token, may be blank
     * @return matching {@link Level}, or {@code null} if unknown or blank
     */
    public static Level parseLevel(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return switch (name.trim().toUpperCase()) {
            case "OFF", "NONE" -> Level.OFF;
            case "ERROR" -> Level.ERROR;
            case "WARN", "WARNING" -> Level.WARN;
            case "INFO" -> Level.INFO;
            case "DEBUG" -> Level.DEBUG;
            case "TRACE" -> Level.TRACE;
            case "ALL" -> Level.ALL;
            default -> null;
        };
    }

    private static Level parseLogLevelFromArgs(String[] args) {
        if (args == null) {
            return null;
        }
        for (String a : args) {
            if (a != null && a.startsWith(CLI_LOG_LEVEL_PREFIX)) {
                String raw = a.substring(CLI_LOG_LEVEL_PREFIX.length());
                Level l = parseLevel(raw);
                if (l == null) {
                    System.err.println("Unknown log level for --log-level=" + raw + " — using OFF");
                    return Level.OFF;
                }
                return l;
            }
        }
        return null;
    }

    private static String parseLogFileFromArgs(String[] args) {
        if (args == null) {
            return null;
        }
        for (String a : args) {
            if (a != null && a.startsWith(CLI_LOG_FILE_PREFIX)) {
                return a.substring(CLI_LOG_FILE_PREFIX.length());
            }
        }
        return null;
    }
}
