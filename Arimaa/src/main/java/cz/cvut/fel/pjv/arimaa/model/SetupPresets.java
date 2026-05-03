package cz.cvut.fel.pjv.arimaa.model;

import cz.cvut.fel.pjv.arimaa.util.BoardConstants;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Named setup layouts for the placement phase: classic chess mapping, reversed chess, Wikibooks-style diagrams (99of9,
 * Fritzlein, MH, HH). Silver home layouts for indices {@code 0,2,3} are the vertical mirror of the matching Gold
 * layout (ranks {@code 0–1} ↔ {@code 7–6}); index {@code 1} is Silver-only Fritzlein. Used by
 * {@link Game#applyChessMappedSetup(PlayerSide, int)}.
 *
 * @see <a href="https://en.wikibooks.org/wiki/Arimaa/Setup">Arimaa/Setup</a>
 */
public final class SetupPresets {

    /** Number of rotating presets (indices {@code 0..ROTATION_COUNT-1}). */
    public static final int ROTATION_COUNT = 4;

    /** One target square and official piece type for setup placement. */
    public record Slot(Position position, PieceType type) {}

    private SetupPresets() {}

    /**
     * @return {@code true} if {@code slots} has sixteen entries, unique squares, all on Gold home ranks ({@code 0–1}),
     *     and the official multiset (1E 1M 2H 2D 2C 8R).
     */
    public static boolean isValidGoldHomeOfficialMultiset(List<Slot> slots) {
        return validateLayout(slots, true, false);
    }

    /**
     * Same as {@link #isValidGoldHomeOfficialMultiset(List)} for Silver home ranks ({@code 6–7}).
     */
    public static boolean isValidSilverHomeOfficialMultiset(List<Slot> slots) {
        return validateLayout(slots, false, true);
    }

    /**
     * Throws if {@link #isValidGoldHomeOfficialMultiset(List)} would return {@code false}.
     */
    public static void requireValidGoldHomeOfficialMultiset(List<Slot> slots) {
        if (!isValidGoldHomeOfficialMultiset(slots)) {
            throw new IllegalArgumentException("invalid Gold home preset: need 16 unique cells on ranks 0–1 with official multiset");
        }
    }

    /**
     * Throws if {@link #isValidSilverHomeOfficialMultiset(List)} would return {@code false}.
     */
    public static void requireValidSilverHomeOfficialMultiset(List<Slot> slots) {
        if (!isValidSilverHomeOfficialMultiset(slots)) {
            throw new IllegalArgumentException("invalid Silver home preset: need 16 unique cells on ranks 6–7 with official multiset");
        }
    }

    /**
     * Adapts a Gold-side home layout (ranks {@code 0–1}) to Silver’s home (ranks {@code 6–7}): same file,
     * rank {@code 7 - r}, same piece type — so Silver presets {@code 0,2,3} need not be duplicated in source.
     */
    public static List<Slot> mirrorGoldHomeToSilver(List<Slot> goldHomeSlots) {
        requireValidGoldHomeOfficialMultiset(goldHomeSlots);
        List<Slot> out = new ArrayList<>(goldHomeSlots.size());
        for (Slot s : goldHomeSlots) {
            Position p = s.position();
            int nr = BoardConstants.BOARD_SIZE - 1 - p.getRankIndex();
            out.add(new Slot(Position.of(p.getFileIndex(), nr), s.type()));
        }
        requireValidSilverHomeOfficialMultiset(out);
        return out;
    }

    /**
     * Inverse of {@link #mirrorGoldHomeToSilver(List)}: maps Silver home ranks {@code 6–7} to Gold ranks {@code 0–1}
     * (same file, rank {@code 7 - r}).
     */
    public static List<Slot> mirrorSilverHomeToGold(List<Slot> silverHomeSlots) {
        requireValidSilverHomeOfficialMultiset(silverHomeSlots);
        List<Slot> out = new ArrayList<>(silverHomeSlots.size());
        for (Slot s : silverHomeSlots) {
            Position p = s.position();
            int nr = BoardConstants.BOARD_SIZE - 1 - p.getRankIndex();
            out.add(new Slot(Position.of(p.getFileIndex(), nr), s.type()));
        }
        requireValidGoldHomeOfficialMultiset(out);
        return out;
    }

    /** Classic “chess” mapping: strong pieces on the rear home rank, eight rabbits forward (Gold ranks 0–1). */
    public static List<Slot> classicGold() {
        List<Slot> slots = new ArrayList<>(16);
        PieceType[] back = {
                PieceType.HORSE, PieceType.CAT, PieceType.DOG, PieceType.CAMEL,
                PieceType.ELEPHANT, PieceType.DOG, PieceType.CAT, PieceType.HORSE
        };
        for (int f = 0; f < BoardConstants.BOARD_SIZE; f++) {
            slots.add(new Slot(Position.of(f, 0), back[f]));
        }
        for (int f = 0; f < BoardConstants.BOARD_SIZE; f++) {
            slots.add(new Slot(Position.of(f, 1), PieceType.RABBIT));
        }
        return sealGold(slots);
    }

    /** Classic chess mapping for Silver (rabbits rank 6, pieces rank 7) — mirror of {@link #classicGold()}. */
    public static List<Slot> classicSilver() {
        return mirrorGoldHomeToSilver(classicGold());
    }

    /**
     * Rotating preset for Gold / {@code presetIndex} wrapped with {@link #ROTATION_COUNT}.
     *
     * @param presetIndex {@code 0} reversed chess; {@code 1} 99of9; {@code 2} MH; {@code 3} HH
     */
    public static List<Slot> rotatingGold(int presetIndex) {
        int v = Math.floorMod(presetIndex, ROTATION_COUNT);
        return switch (v) {
            case 0 -> sealGold(goldReversedSlots());
            case 1 -> sealGold(goldSymmetric99of9Slots());
            case 2 -> sealGold(goldMhDiagramSlots());
            case 3 -> sealGold(goldHhDiagramSlots());
            default -> sealGold(goldReversedSlots());
        };
    }

    /**
     * Rotating preset for Silver: {@code 0} mirror of Gold reversed chess; {@code 1} Fritzlein; {@code 2} mirror of Gold
     * MH; {@code 3} mirror of Gold HH.
     */
    public static List<Slot> rotatingSilver(int presetIndex) {
        int v = Math.floorMod(presetIndex, ROTATION_COUNT);
        return switch (v) {
            case 0 -> mirrorGoldHomeToSilver(goldReversedSlots());
            case 1 -> sealSilver(silverFritzleinSlots());
            case 2 -> mirrorGoldHomeToSilver(goldMhDiagramSlots());
            case 3 -> mirrorGoldHomeToSilver(goldHhDiagramSlots());
            default -> mirrorGoldHomeToSilver(goldReversedSlots());
        };
    }

    private static List<Slot> sealGold(List<Slot> slots) {
        requireValidGoldHomeOfficialMultiset(slots);
        return slots;
    }

    private static List<Slot> sealSilver(List<Slot> slots) {
        requireValidSilverHomeOfficialMultiset(slots);
        return slots;
    }

    private static boolean validateLayout(List<Slot> slots, boolean goldHome, boolean silverHome) {
        if (slots == null || slots.size() != 16) {
            return false;
        }
        Set<Position> seen = new HashSet<>();
        for (Slot s : slots) {
            if (s == null || s.type() == null || s.position() == null) {
                return false;
            }
            int r = s.position().getRankIndex();
            if (goldHome && (r != 0 && r != 1)) {
                return false;
            }
            if (silverHome && (r != BoardConstants.BOARD_SIZE - 2 && r != BoardConstants.BOARD_SIZE - 1)) {
                return false;
            }
            if (!seen.add(s.position())) {
                return false;
            }
        }
        EnumMap<PieceType, Integer> c = countTypes(slots);
        return c.getOrDefault(PieceType.ELEPHANT, 0) == 1
                && c.getOrDefault(PieceType.CAMEL, 0) == 1
                && c.getOrDefault(PieceType.HORSE, 0) == 2
                && c.getOrDefault(PieceType.DOG, 0) == 2
                && c.getOrDefault(PieceType.CAT, 0) == 2
                && c.getOrDefault(PieceType.RABBIT, 0) == 8;
    }

    private static EnumMap<PieceType, Integer> countTypes(List<Slot> slots) {
        EnumMap<PieceType, Integer> m = new EnumMap<>(PieceType.class);
        for (PieceType t : PieceType.values()) {
            m.put(t, 0);
        }
        for (Slot s : slots) {
            m.merge(s.type(), 1, Integer::sum);
        }
        return m;
    }

    private static PieceType[] chessBackRankGoldStyle() {
        return new PieceType[] {
                PieceType.HORSE, PieceType.CAT, PieceType.DOG, PieceType.CAMEL,
                PieceType.ELEPHANT, PieceType.DOG, PieceType.CAT, PieceType.HORSE
        };
    }

    private static void addRankSlots(List<Slot> slots, int rankIndex, PieceType[] filesAtoH) {
        for (int f = 0; f < BoardConstants.BOARD_SIZE; f++) {
            slots.add(new Slot(Position.of(f, rankIndex), filesAtoH[f]));
        }
    }

    /** Eight rabbits on the rear rank, chess multiset row toward the center. */
    private static List<Slot> goldReversedSlots() {
        List<Slot> slots = new ArrayList<>(16);
        for (int f = 0; f < BoardConstants.BOARD_SIZE; f++) {
            slots.add(new Slot(Position.of(f, 0), PieceType.RABBIT));
        }
        addRankSlots(slots, 1, chessBackRankGoldStyle());
        return slots;
    }

    /** Gold “99of9” (Wikibooks symmetric diagram). */
    private static List<Slot> goldSymmetric99of9Slots() {
        List<Slot> slots = new ArrayList<>(16);
        PieceType[] rank1 = {
                PieceType.RABBIT,
                PieceType.RABBIT,
                PieceType.RABBIT,
                PieceType.DOG,
                PieceType.DOG,
                PieceType.RABBIT,
                PieceType.RABBIT,
                PieceType.RABBIT
        };
        PieceType[] rank2 = {
                PieceType.RABBIT,
                PieceType.HORSE,
                PieceType.CAT,
                PieceType.CAMEL,
                PieceType.ELEPHANT,
                PieceType.CAT,
                PieceType.HORSE,
                PieceType.RABBIT
        };
        addRankSlots(slots, 0, rank1);
        addRankSlots(slots, 1, rank2);
        return slots;
    }

    /** Silver “Fritzlein” (camel d8, elephant e8) — not a mirror of Gold index {@code 1}. */
    private static List<Slot> silverFritzleinSlots() {
        List<Slot> slots = new ArrayList<>(16);
        PieceType[] rank8 = {
                PieceType.RABBIT,
                PieceType.CAT,
                PieceType.RABBIT,
                PieceType.RABBIT,
                PieceType.DOG,
                PieceType.RABBIT,
                PieceType.RABBIT,
                PieceType.RABBIT,
        };
        PieceType[] rank7 = {
                PieceType.RABBIT,
                PieceType.CAMEL,
                PieceType.DOG,
                PieceType.HORSE,
                PieceType.ELEPHANT,
                PieceType.CAT,
                PieceType.HORSE,
                PieceType.RABBIT
        };
        addRankSlots(slots, 6, rank7);
        addRankSlots(slots, 7, rank8);
        return slots;
    }

    /** MH diagram Gold (EMH west answer). */
    private static List<Slot> goldMhDiagramSlots() {
        List<Slot> slots = new ArrayList<>(16);
        PieceType[] rank1 = {
                PieceType.RABBIT,
                PieceType.RABBIT,
                PieceType.RABBIT,
                PieceType.DOG,
                PieceType.RABBIT,
                PieceType.RABBIT,
                PieceType.RABBIT,
                PieceType.RABBIT
        };
        PieceType[] rank2 = {
                PieceType.HORSE,
                PieceType.CAMEL,
                PieceType.CAT,
                PieceType.ELEPHANT,
                PieceType.DOG,
                PieceType.CAT,
                PieceType.HORSE,
                PieceType.RABBIT
        };
        addRankSlots(slots, 0, rank1);
        addRankSlots(slots, 1, rank2);
        return slots;
    }

    /** HH diagram Gold. */
    private static List<Slot> goldHhDiagramSlots() {
        List<Slot> slots = new ArrayList<>(16);
        PieceType[] rank1 = {
                PieceType.RABBIT,
                PieceType.RABBIT,
                PieceType.CAT,
                PieceType.RABBIT,
                PieceType.RABBIT,
                PieceType.RABBIT,
                PieceType.RABBIT,
                PieceType.RABBIT
        };
        PieceType[] rank2 = {
                PieceType.HORSE,
                PieceType.HORSE,
                PieceType.ELEPHANT,
                PieceType.CAT,
                PieceType.DOG,
                PieceType.DOG,
                PieceType.CAMEL,
                PieceType.RABBIT
        };
        addRankSlots(slots, 0, rank1);
        addRankSlots(slots, 1, rank2);
        return slots;
    }
}
