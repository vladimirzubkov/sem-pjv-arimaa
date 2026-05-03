package cz.cvut.fel.pjv.arimaa.ui;

import cz.cvut.fel.pjv.arimaa.model.PieceType;
import cz.cvut.fel.pjv.arimaa.model.PlayerSide;
import javafx.scene.image.Image;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FigureSvgRasterCacheTest {

    @Test
    void defaultSkinGoldCamelLoadsAsPng() {
        FigureSvgRasterCache cache = new FigureSvgRasterCache();
        cache.setSkinDirectory("default");
        Image img = cache.getRasterized(PlayerSide.GOLD, PieceType.CAMEL, 40);
        assertNotNull(img, "classpath /images/figure_sets/default/gCamel.png");
        assertFalse(img.isError(), () -> String.valueOf(img.getException()));
    }

    @Test
    void defaultSkinGoldDogLoadsAsPng() {
        FigureSvgRasterCache cache = new FigureSvgRasterCache();
        cache.setSkinDirectory("default");
        Image img = cache.getRasterized(PlayerSide.GOLD, PieceType.DOG, 40);
        assertNotNull(img, "classpath /images/figure_sets/default/gHounds.png");
        assertFalse(img.isError(), () -> String.valueOf(img.getException()));
    }

    @Test
    void classicSkinGoldCamelRasterizesWithoutJavafxImageError() {
        FigureSvgRasterCache cache = new FigureSvgRasterCache();
        cache.setSkinDirectory("classic");
        Image img = cache.getRasterized(PlayerSide.GOLD, PieceType.CAMEL, 40);
        assertNotNull(img, "classpath /images/figure_sets/classic/gCamel.svg via Batik");
        assertFalse(img.isError(), () -> String.valueOf(img.getException()));
    }

    @Test
    void discoverSkinsIncludesKnownFolders() {
        assertTrue(FigureSvgRasterCache.discoverSkinDirectoryNames().size() >= 1);
    }
}
