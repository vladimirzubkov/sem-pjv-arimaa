package cz.cvut.fel.pjv.arimaa.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Lists skin subdirectory names under {@value #RESOURCE_ROOT} on the same classpath root that actually loads piece
 * assets (no whole-classpath scan — avoids phantom folders from other modules or duplicate entries).
 */
final class FigureSkinDirectoryDiscovery {

    private static final Logger LOG = LoggerFactory.getLogger(FigureSkinDirectoryDiscovery.class);

    /** Same root as {@link FigureSvgRasterCache#FIGURE_SETS_ROOT} for one consistent lookup. */
    static final String RESOURCE_ROOT = "/images/figure_sets/";

    /** Preferred skin when present; listed first in the menu. */
    static final String FALLBACK_SKIN_NAME = "default";

    private FigureSkinDirectoryDiscovery() {}

    static boolean isValidSkinDirectoryName(String name) {
        if (name == null || name.isEmpty() || name.startsWith(".")) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '-')) {
                return false;
            }
        }
        return true;
    }

    /**
     * Subdirectory names under {@value #RESOURCE_ROOT} for this module’s resource tree. Sorted;
     * {@value #FALLBACK_SKIN_NAME} first when present.
     */
    static List<String> discoverSkinDirectoryNames() {
        URL url = FigureSkinDirectoryDiscovery.class.getResource(RESOURCE_ROOT);
        if (url == null) {
            return List.of(FALLBACK_SKIN_NAME);
        }
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        try {
            String protocol = url.getProtocol();
            if ("file".equals(protocol)) {
                collectFromFileSystem(url, names);
            } else if ("jar".equals(protocol)) {
                collectFromJar(url, names);
            } else {
                LOG.warn("Unknown protocol for {}: {}", RESOURCE_ROOT, url);
                return List.of(FALLBACK_SKIN_NAME);
            }
        } catch (Exception e) {
            LOG.warn("Could not list {}", RESOURCE_ROOT, e);
            return List.of(FALLBACK_SKIN_NAME);
        }
        if (names.isEmpty()) {
            return List.of(FALLBACK_SKIN_NAME);
        }
        ArrayList<String> out = new ArrayList<>(names);
        int def = indexOfIgnoreCase(out, FALLBACK_SKIN_NAME);
        if (def > 0) {
            out.add(0, out.remove(def));
        }
        return List.copyOf(out);
    }

    private static void collectFromFileSystem(URL url, Set<String> names) throws Exception {
        Path base = Paths.get(url.toURI());
        if (!Files.isDirectory(base)) {
            return;
        }
        try (Stream<Path> stream = Files.list(base)) {
            stream.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .filter(FigureSkinDirectoryDiscovery::isValidSkinDirectoryName)
                    .forEach(names::add);
        }
    }

    private static void collectFromJar(URL url, Set<String> names) throws java.io.IOException {
        URLConnection conn = url.openConnection();
        if (!(conn instanceof JarURLConnection jarConn)) {
            return;
        }
        String prefix = jarConn.getEntryName();
        if (prefix == null || prefix.isEmpty()) {
            return;
        }
        if (!prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        try (JarFile jar = jarConn.getJarFile()) {
            Enumeration<JarEntry> en = jar.entries();
            while (en.hasMoreElements()) {
                JarEntry je = en.nextElement();
                String n = je.getName();
                if (!n.startsWith(prefix) || n.length() <= prefix.length()) {
                    continue;
                }
                String tail = n.substring(prefix.length());
                int slash = tail.indexOf('/');
                if (slash > 0) {
                    String dir = tail.substring(0, slash);
                    if (isValidSkinDirectoryName(dir)) {
                        names.add(dir);
                    }
                }
            }
        }
    }

    private static int indexOfIgnoreCase(List<String> list, String target) {
        for (int i = 0; i < list.size(); i++) {
            if (target.equalsIgnoreCase(list.get(i))) {
                return i;
            }
        }
        return -1;
    }
}
