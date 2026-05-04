package cz.cvut.fel.pjv.arimaa.persistence;

import cz.cvut.fel.pjv.arimaa.model.GameMemento;
import cz.cvut.fel.pjv.arimaa.model.enums.GameState;
import cz.cvut.fel.pjv.arimaa.model.enums.PieceType;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;
import cz.cvut.fel.pjv.arimaa.util.BoardConstants;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameMementoTextCodecTest {

    @Test
    void encodeOmitsEmptyRowsAndNoGridMarker() {
        GameMemento.CellSnap[][] grid = new GameMemento.CellSnap[BoardConstants.BOARD_SIZE][BoardConstants.BOARD_SIZE];
        for (int f = 0; f < BoardConstants.BOARD_SIZE; f++) {
            grid[0][f] = new GameMemento.CellSnap(PieceType.RABBIT, PlayerSide.SILVER);
            grid[7][f] = new GameMemento.CellSnap(PieceType.RABBIT, PlayerSide.GOLD);
        }
        GameMemento mem =
                new GameMemento(
                        GameState.PLAY,
                        PlayerSide.GOLD,
                        null,
                        false,
                        grid,
                        List.of(),
                        List.of(),
                        null,
                        List.of(),
                        List.of());

        List<String> lines = GameMementoTextCodec.encode(mem);
        assertFalse(lines.contains("GRID"));
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("R1 ")));
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("R8 ")));
        assertFalse(lines.stream().anyMatch(l -> l.startsWith("R2 ")));
        assertFalse(lines.stream().anyMatch(l -> l.startsWith("R3 ")));

        GameMemento back = GameMementoTextCodec.decode(lines);
        assertGridsEqual(mem.grid(), back.grid());
        assertEquals(mem.state(), back.state());
        assertEquals(mem.sideToMove(), back.sideToMove());
    }

    private static void assertGridsEqual(GameMemento.CellSnap[][] a, GameMemento.CellSnap[][] b) {
        for (int r = 0; r < BoardConstants.BOARD_SIZE; r++) {
            for (int f = 0; f < BoardConstants.BOARD_SIZE; f++) {
                assertEquals(a[r][f], b[r][f], "cell r=" + r + " f=" + f);
            }
        }
    }
}
