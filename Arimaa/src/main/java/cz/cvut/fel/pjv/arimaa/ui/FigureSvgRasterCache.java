package cz.cvut.fel.pjv.arimaa.ui;

import cz.cvut.fel.pjv.arimaa.model.PieceType;
import cz.cvut.fel.pjv.arimaa.model.PlayerSide;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads Arimaa piece SVGs from the classpath and rasterizes them to {@link Image} for JavaFX.
 * <p>
 * OpenJFX does not decode SVG directly; Batik writes a PNG with alpha. Rasterization uses a higher
 * pixel size than the on-screen {@code maxSide} so {@link javafx.scene.image.ImageView} downscaling looks sharper.
 */
public final class FigureSvgRasterCache {

    private static final Logger LOG = LoggerFactory.getLogger(FigureSvgRasterCache.class);

    /** Classpath directory (under {@code src/main/resources}). */
    public static final String RESOURCE_PREFIX = "/images/figure_sets/default/";

    /**
     * Batik raster size = {@code maxSide * this} so {@link javafx.scene.image.ImageView} can downscale with less blur.
     * Higher = sharper but more memory/CPU on first load.
     */
    private static final double RASTER_SUPER_SAMPLING = 3.0;

    private final ConcurrentHashMap<String, Image> cache = new ConcurrentHashMap<>();

    /**
     * Returns a cached raster image for the given side and type, or {@code null} if missing or transcoding fails.
     *
     * @param maxSide approximate width/height hint in pixels (square output)
     */
    public Image getRasterized(PlayerSide side, PieceType type, double maxSide) {
        String file = resourceFileName(side, type);
        String key = file + "@" + (int) maxSide;
        return cache.computeIfAbsent(key, k -> load(file, maxSide));
    }

    public void clear() {
        cache.clear();
    }

    private static String resourceFileName(PlayerSide side, PieceType type) {
        String p = side == PlayerSide.GOLD ? "g" : "s";
        String stem = switch (type) {
            case ELEPHANT -> "Jumbo";
            case CAMEL -> "Camel";
            case HORSE -> "Horse";
            case DOG -> "Hounds";
            case CAT -> "Mainkoon";
            case RABBIT -> "Hare";
        };
        return p + stem + ".svg";
    }

    private Image load(String fileName, double maxSide) {
        URL url = FigureSvgRasterCache.class.getResource(RESOURCE_PREFIX + fileName);
        if (url == null) {
            LOG.warn("Missing figure resource: {}{}", RESOURCE_PREFIX, fileName);
            return null;
        }
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
                LOG.warn("Batik produced empty PNG for {}", fileName);
                return null;
            }
            Image im = new Image(new ByteArrayInputStream(pngBytes));
            if (im.isError()) {
                LOG.warn("JavaFX could not decode raster for {}: {}", fileName, im.getException());
                return null;
            }
            return im;
        } catch (TranscoderException | IOException e) {
            LOG.warn("Failed to rasterize {}", fileName, e);
            return null;
        }
    }
}
