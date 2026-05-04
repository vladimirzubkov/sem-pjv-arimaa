package cz.cvut.fel.pjv.arimaa.ui;

import cz.cvut.fel.pjv.arimaa.model.enums.PieceType;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;
import javafx.scene.image.Image;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.ImageTranscoder;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Piece images from {@value #FIGURE_SETS_ROOT}{@code <skin>/}. Per stem ({@code gCamel}, …): if {@code stem.svg}
 * exists, Batik rasterizes it; else the first of {@code .png}, {@code .gif}, {@code .jpg}, {@code .jpeg} is loaded
 * with JavaFX. {@linkplain #discoverSkinDirectoryNames()} lists subfolders of {@value #FIGURE_SETS_ROOT} from the same
 * classpath URL that serves resources (not a whole-process classpath scan). If the active skin is not {@value #FALLBACK_SKIN_NAME}, a missing piece falls back to that folder.
 */
public final class FigureSvgRasterCache {

    private static final Logger LOG = LoggerFactory.getLogger(FigureSvgRasterCache.class);

    /** Classpath root for skins (under {@code src/main/resources}). */
    public static final String FIGURE_SETS_ROOT = "/images/figure_sets/";

    /** Preferred skin when present; also used as asset fallback for other skins. */
    public static final String FALLBACK_SKIN_NAME = FigureSkinDirectoryDiscovery.FALLBACK_SKIN_NAME;

    private static final String[] RASTER_EXTENSIONS = {".png", ".gif", ".jpg", ".jpeg"};

    private static final double RASTER_SUPER_SAMPLING = 3.0;

    private volatile String skinDirectoryName = FALLBACK_SKIN_NAME;

    private final ConcurrentHashMap<String, Image> cache = new ConcurrentHashMap<>();

    /**
     * Subdirectory names under {@value #FIGURE_SETS_ROOT} (from {@code target/classes}, run JAR, etc.). Names are sorted;
     * {@value #FALLBACK_SKIN_NAME} is listed first when it exists.
     */
    public static List<String> discoverSkinDirectoryNames() {
        return FigureSkinDirectoryDiscovery.discoverSkinDirectoryNames();
    }

    /** Safe folder segment for a skin name (no path traversal). */
    public static boolean isValidSkinDirectoryName(String name) {
        return FigureSkinDirectoryDiscovery.isValidSkinDirectoryName(name);
    }

    public String getSkinDirectoryName() {
        return skinDirectoryName;
    }

    /**
     * Active skin: {@value #FIGURE_SETS_ROOT}{@code <name>/}. Clears cache when the name changes.
     *
     * @throws IllegalArgumentException if {@code name} is not a valid directory token
     */
    public void setSkinDirectory(String name) {
        if (name == null || !isValidSkinDirectoryName(name)) {
            throw new IllegalArgumentException("invalid skin directory name: " + name);
        }
        if (!name.equals(skinDirectoryName)) {
            skinDirectoryName = name;
            clear();
        }
    }

    private static String prefixForSkin(String skinName) {
        return FIGURE_SETS_ROOT + skinName + "/";
    }

    /**
     * Returns a cached image for the given side and type, or {@code null} if missing or load/transcode fails.
     *
     * @param maxSide approximate width/height hint in pixels (square output)
     */
    public Image getRasterized(PlayerSide side, PieceType type, double maxSide) {
        String stem = resourceStem(side, type);
        String key = skinDirectoryName + "|" + stem + "@" + (int) maxSide;
        return cache.computeIfAbsent(key, k -> loadFigure(stem, maxSide));
    }

    public void clear() {
        cache.clear();
    }

    private static String resourceStem(PlayerSide side, PieceType type) {
        String p = side == PlayerSide.GOLD ? "g" : "s";
        String piece = switch (type) {
            case ELEPHANT -> "Jumbo";
            case CAMEL -> "Camel";
            case HORSE -> "Horse";
            case DOG -> "Hounds";
            case CAT -> "Mainkoon";
            case RABBIT -> "Hare";
        };
        return p + piece;
    }

    private Image loadFigure(String stem, double maxSide) {
        String primary = prefixForSkin(skinDirectoryName);
        Image img = loadFromDirectory(primary, stem, maxSide);
        if (img != null) {
            return img;
        }
        if (!FALLBACK_SKIN_NAME.equalsIgnoreCase(skinDirectoryName)) {
            Image fb = loadFromDirectory(prefixForSkin(FALLBACK_SKIN_NAME), stem, maxSide);
            if (fb != null) {
                LOG.debug("Skin {}: no asset for {}, used {}", skinDirectoryName, stem, FALLBACK_SKIN_NAME);
                return fb;
            }
        }
        LOG.warn("Missing figure for stem {} under skin {}", stem, skinDirectoryName);
        return null;
    }

    private static Image loadFromDirectory(String prefix, String stem, double maxSide) {
        String svgPath = prefix + stem + ".svg";
        URL svgUrl = FigureSvgRasterCache.class.getResource(svgPath);
        if (svgUrl != null) {
            Image fromSvg = rasterizeSvgFromUrl(svgUrl, maxSide);
            if (fromSvg != null) {
                return fromSvg;
            }
        }
        for (String ext : RASTER_EXTENSIONS) {
            URL url = FigureSvgRasterCache.class.getResource(prefix + stem + ext);
            if (url != null) {
                Image im = loadRaster(url, maxSide);
                if (im != null) {
                    return im;
                }
            }
        }
        return null;
    }

    private static Image rasterizeSvgFromUrl(URL url, double maxSide) {
        try (InputStream in = url.openStream()) {
            PNGTranscoder t = new PNGTranscoder();
            float raster = (float) (maxSide * RASTER_SUPER_SAMPLING);
            t.addTranscodingHint(ImageTranscoder.KEY_WIDTH, raster);
            t.addTranscodingHint(ImageTranscoder.KEY_HEIGHT, raster);
            TranscoderInput input = new TranscoderInput(in);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            t.transcode(input, new TranscoderOutput(out));
            byte[] pngBytes = out.toByteArray();
            if (pngBytes.length == 0) {
                LOG.warn("Batik produced empty PNG for {}", url);
                return null;
            }
            Image im = new Image(new ByteArrayInputStream(pngBytes));
            if (im.isError()) {
                LOG.warn("JavaFX could not decode raster for {}: {}", url, im.getException());
                return null;
            }
            return im;
        } catch (TranscoderException | IOException e) {
            LOG.warn("Failed to rasterize {}", url, e);
            return null;
        }
    }

    private static Image loadRaster(URL url, double maxSide) {
        try (InputStream in = url.openStream()) {
            double w = maxSide * RASTER_SUPER_SAMPLING;
            double h = maxSide * RASTER_SUPER_SAMPLING;
            Image im = new Image(in, w, h, true, true);
            if (im.isError()) {
                LOG.warn("JavaFX could not decode image {}: {}", url, im.getException());
                return null;
            }
            return im;
        } catch (IOException e) {
            LOG.warn("Failed to read image {}", url, e);
            return null;
        }
    }
}
