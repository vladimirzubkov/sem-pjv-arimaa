package cz.cvut.fel.pjv.arimaa;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Application release label from Git (Maven {@code git-commit-id-maven-plugin} → {@code git.properties}) or
 * {@code git describe} when running from the IDE without a packaged manifest.
 */
public final class AppVersion {

    private static final String GIT_PROPERTIES = "git.properties";
    private static final String FALLBACK_LABEL = "dev";

    private AppVersion() {}

    /** Tag or describe string for UI (e.g. {@code 0.9.32}). */
    public static String displayTag() {
        String fromProps = readGitProperty("git.closest.tag.name");
        if (isUsableTag(fromProps)) {
            return normalizeTag(fromProps);
        }
        String fromManifest = readManifestImplementationVersion();
        if (isUsableTag(fromManifest)) {
            return normalizeTag(fromManifest);
        }
        String fromDescribe = tryGitDescribeTag();
        if (isUsableTag(fromDescribe)) {
            return normalizeTag(fromDescribe);
        }
        return FALLBACK_LABEL;
    }

    /** First line in the About dialog (Czech). */
    public static String aboutVersionLine() {
        String tag = displayTag();
        String abbrev = readGitProperty("git.commit.id.abbrev");
        String dirty = readGitProperty("git.dirty");
        if (abbrev != null && !abbrev.isBlank()) {
            String suffix = "true".equalsIgnoreCase(dirty) ? " (modifikováno)" : "";
            return "Verze: %s (git %s%s)".formatted(tag, abbrev, suffix);
        }
        return "Verze: " + tag;
    }

    private static boolean isUsableTag(String value) {
        return value != null && !value.isBlank() && !"null".equalsIgnoreCase(value);
    }

    private static String normalizeTag(String raw) {
        String t = raw.trim();
        if (t.regionMatches(true, 0, "v", 0, 1) && t.length() > 1 && Character.isDigit(t.charAt(1))) {
            return t.substring(1);
        }
        return t;
    }

    private static String readGitProperty(String key) {
        try (InputStream in = AppVersion.class.getClassLoader().getResourceAsStream(GIT_PROPERTIES)) {
            if (in == null) {
                return null;
            }
            Properties p = new Properties();
            p.load(in);
            return p.getProperty(key);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String readManifestImplementationVersion() {
        Package pkg = ArimaaApp.class.getPackage();
        if (pkg == null) {
            return null;
        }
        return pkg.getImplementationVersion();
    }

    private static String tryGitDescribeTag() {
        Path gitRoot = findGitRepositoryRoot();
        if (gitRoot == null) {
            return null;
        }
        try {
            ProcessBuilder pb =
                    new ProcessBuilder("git", "describe", "--tags", "--abbrev=0")
                            .directory(gitRoot.toFile())
                            .redirectErrorStream(true);
            Process process = pb.start();
            String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.waitFor() == 0 && !out.isBlank()) {
                int dash = out.indexOf('-');
                return dash > 0 ? out.substring(0, dash) : out;
            }
        } catch (Exception ignored) {
            // IDE / no git on PATH
        }
        return null;
    }

    private static Path findGitRepositoryRoot() {
        Path dir = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        for (int depth = 0; depth < 10 && dir != null; depth++) {
            if (Files.isDirectory(dir.resolve(".git"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        return null;
    }
}
