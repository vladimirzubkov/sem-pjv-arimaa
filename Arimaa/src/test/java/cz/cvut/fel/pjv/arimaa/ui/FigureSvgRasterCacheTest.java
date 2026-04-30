package cz.cvut.fel.pjv.arimaa.ui;

import cz.cvut.fel.pjv.arimaa.model.PieceType;
import cz.cvut.fel.pjv.arimaa.model.PlayerSide;
import javafx.scene.image.Image;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FigureSvgRasterCacheTest {

    @Test
    void defaultSetGoldCamelRasterizesWithoutJavafxImageError() {
        FigureSvgRasterCache cache = new FigureSvgRasterCache();
        Image img = cache.getRasterized(PlayerSide.GOLD, PieceType.CAMEL, 40);
        assertNotNull(img, "Batik + batik-codec should produce a PNG JavaFX can load (check classpath /images/figure_sets/default/gCamel.svg)");
        assertFalse(img.isError(), () -> String.valueOf(img.getException()));
    }
}
