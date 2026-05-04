package cz.cvut.fel.pjv.arimaa.model.enums;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PieceTypeTest {

    @Test
    void notationCharsMatchUiLegend() {
        Map<PieceType, Character> expected =
                Map.of(
                        PieceType.ELEPHANT, 'E',
                        PieceType.CAMEL, 'M',
                        PieceType.HORSE, 'H',
                        PieceType.DOG, 'D',
                        PieceType.CAT, 'K',
                        PieceType.RABBIT, 'R');
        for (PieceType t : PieceType.values()) {
            assertEquals(expected.get(t).charValue(), t.notationChar(), t.name());
        }
    }
}
