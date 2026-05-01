package cz.cvut.fel.pjv.arimaa.util;

import cz.cvut.fel.pjv.arimaa.model.PieceType;

/**
 * Official Arimaa strength order is reflected by {@link PieceType} declaration order (strongest first).
 */
public final class PieceStrength {

    private PieceStrength() {
    }

    /**
     * @return {@code true} if {@code a} can push, pull, or freeze {@code b} (strict inequality).
     */
    public static boolean isStrictlyStronger(PieceType a, PieceType b) {
        return a.ordinal() < b.ordinal();
    }
}
