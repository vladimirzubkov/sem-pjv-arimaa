package cz.cvut.fel.pjv.arimaa.util;

import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;
import cz.cvut.fel.pjv.arimaa.model.Position;

/**
 * Arimaa setup: each side’s first two ranks in canonical model space (rank {@code '1'} → index {@code 0}).
 */
public final class HomeTerritory {

    private HomeTerritory() {
    }

    /**
     * @param rankIndex board rank index ({@code 0} = algebraic rank {@code 1})
     * @param ranksMirrored if {@code true}, rank index is mirrored before testing Gold/Silver bands
     */
    public static boolean contains(PlayerSide side, int rankIndex, boolean ranksMirrored) {
        int r = rankIndex;
        if (ranksMirrored) {
            r = BoardConstants.BOARD_SIZE - 1 - r;
        }
        return switch (side) {
            case GOLD -> r == 0 || r == 1;
            case SILVER -> r == BoardConstants.BOARD_SIZE - 2 || r == BoardConstants.BOARD_SIZE - 1;
        };
    }

    /** @see #contains(PlayerSide, int, boolean) */
    public static boolean contains(PlayerSide side, Position position, boolean ranksMirrored) {
        return contains(side, position.getRankIndex(), ranksMirrored);
    }
}
