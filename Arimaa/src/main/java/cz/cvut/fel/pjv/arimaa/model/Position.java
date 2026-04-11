package cz.cvut.fel.pjv.arimaa.model;

/**
 * Square coordinates on an 8×8 board (file a–h, rank 1–8).
 */
public class Position {

    private int fileIndex;
    private int rankIndex;

    public int getFileIndex() {
        return fileIndex;
    }

    public void setFileIndex(int fileIndex) {
        this.fileIndex = fileIndex;
    }

    public int getRankIndex() {
        return rankIndex;
    }

    public void setRankIndex(int rankIndex) {
        this.rankIndex = rankIndex;
    }
}
