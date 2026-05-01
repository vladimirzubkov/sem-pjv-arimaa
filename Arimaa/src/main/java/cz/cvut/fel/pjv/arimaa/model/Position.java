package cz.cvut.fel.pjv.arimaa.model;

import cz.cvut.fel.pjv.arimaa.util.BoardConstants;

import java.util.Objects;

/**
 * Square coordinates on an 8×8 board (file a–h, rank 1–8 in human notation;
 * internally stored as zero-based indices consistent with {@link Board}).
 */
public class Position {

    private int fileIndex;
    private int rankIndex;

    /**
     * No-arg constructor for beans, deserialization, or legacy code that sets
     * indices via {@link #setFileIndex(int)} / {@link #setRankIndex(int)}.
     * <p>
     * Prefer {@link #Position(int, int)} or {@link #of(int, int)} so instances are
     * always in-bounds at construction time.
     */
    public Position() {
    }

    /**
     * Creates a position from zero-based indices after validating board bounds.
     * <p>
     * Needed so every {@link Position} used by {@link Board} and {@link Step} can
     * rely on in-range indices; callers should prefer this (or {@link #of(int, int)})
     * over an empty instance plus setters.
     *
     * @param fileIndex column index in {@code [0, {@link BoardConstants#BOARD_SIZE})}
     * @param rankIndex row index in {@code [0, {@link BoardConstants#BOARD_SIZE})}
     * @throws IllegalArgumentException if either index is outside the board
     */
    public Position(int fileIndex, int rankIndex) {
        validateIndices(fileIndex, rankIndex);
        this.fileIndex = fileIndex;
        this.rankIndex = rankIndex;
    }

    /**
     * Factory alias for {@link #Position(int, int)}; keeps call sites readable
     * ({@code Position.of(0, 0)} vs {@code new} in streams).
     *
     * @param fileIndex same as constructor
     * @param rankIndex same as constructor
     * @return a validated position instance
     * @throws IllegalArgumentException if indices are out of range
     */
    public static Position of(int fileIndex, int rankIndex) {
        return new Position(fileIndex, rankIndex);
    }

    /**
     * Parses standard algebraic square notation (e.g. {@code "a1"}, {@code "h8"}).
     * <p>
     * Needed for PGN-like I/O, tests written in human notation, and UI text fields.
     * Must agree with the same rank/file convention as {@link Board} indexing.
     *
     * @param notation non-null, non-empty string (typically length 2: file letter + rank digit);
     *                 invalid format should fail fast with a clear exception
     * @return the corresponding board position
     * @throws IllegalArgumentException if the string is not a legal square on this board
     * @throws NullPointerException     if {@code notation} is {@code null}
     */
    public static Position fromAlgebraic(String notation) {
        if (notation == null || notation.length() != 2) {
            throw new IllegalArgumentException("Invalid notation: " + notation);
        }
        char file = notation.charAt(0);
        char rank = notation.charAt(1);
        if (file < 'a' || file > 'h' || rank < '1' || rank > '8') {
            throw new IllegalArgumentException("Invalid notation: " + notation);
        }
        return new Position(file - 'a', rank - '1');
    }

    /**
     * Ensures both indices lie on the board; shared by constructor and setters.
     *
     * @param fileIndex candidate file index
     * @param rankIndex candidate rank index
     * @throws IllegalArgumentException if either index is outside {@code [0, BOARD_SIZE)}
     */
    private static void validateIndices(int fileIndex, int rankIndex) {
        if (fileIndex < 0 || fileIndex >= BoardConstants.BOARD_SIZE
                || rankIndex < 0 || rankIndex >= BoardConstants.BOARD_SIZE) {
            throw new IllegalArgumentException(
                    "Indices out of board: fileIndex=" + fileIndex + ", rankIndex=" + rankIndex);
        }
    }

    public int getFileIndex() {
        return fileIndex;
    }

    /**
     * Updates the file index, keeping the pair in-bounds together with the current rank.
     *
     * @param fileIndex new file index in {@code [0, {@link BoardConstants#BOARD_SIZE})}
     * @throws IllegalArgumentException if the resulting pair would be off the board
     */
    public void setFileIndex(int fileIndex) {
        validateIndices(fileIndex, this.rankIndex);
        this.fileIndex = fileIndex;
    }

    public int getRankIndex() {
        return rankIndex;
    }

    /**
     * Standard square text (e.g. {@code a1}, {@code h8}) matching {@link #fromAlgebraic(String)}.
     */
    public String toAlgebraic() {
        return String.valueOf((char) ('a' + fileIndex)) + (char) ('1' + rankIndex);
    }

    /**
     * Updates the rank index; same contract as {@link #setFileIndex(int)}.
     *
     * @param rankIndex new rank index in {@code [0, {@link BoardConstants#BOARD_SIZE})}
     * @throws IllegalArgumentException if the resulting pair would be off the board
     */
    public void setRankIndex(int rankIndex) {
        validateIndices(this.fileIndex, rankIndex);
        this.rankIndex = rankIndex;
    }

    /**
     * Value equality by coordinates; required for comparing squares in collections,
     * trap lists, and test assertions without manually comparing two integers.
     *
     * @param obj other object, often another {@code Position}
     * @return {@code true} if the same file and rank indices
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Position other = (Position) obj;
        return fileIndex == other.fileIndex && rankIndex == other.rankIndex;
    }

    /**
     * Consistent with {@link #equals(Object)} so {@code Position} can be used in
     * {@link java.util.HashMap} / {@link java.util.HashSet}.
     *
     * @return hash code combining file and rank
     */
    @Override
    public int hashCode() {
        return Objects.hash(fileIndex, rankIndex);
    }
}
