package cz.cvut.fel.pjv.arimaa.model;

import cz.cvut.fel.pjv.arimaa.model.enums.PieceType;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;

/**
 * A piece instance on the board.
 */
public class Piece {

    private PieceType type;
    private PlayerSide side;
    private Position position;

    public Piece() {
    }

    public Piece(PieceType type, PlayerSide side) {
        this.type = type;
        this.side = side;
        this.position = null;
    }

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
