package cz.cvut.fel.pjv.arimaa.model;

/**
 * A piece instance on the board.
 */
public class Piece {

    private PieceType type;
    private PlayerSide side;
    private Position position;

    public PieceType getType() {
        return type;
    }

    public void setType(PieceType type) {
        this.type = type;
    }

    public PlayerSide getSide() {
        return side;
    }

    public void setSide(PlayerSide side) {
        this.side = side;
    }

    public Position getPosition() {
        return position;
    }

    public void setPosition(Position position) {
        this.position = position;
    }
}
