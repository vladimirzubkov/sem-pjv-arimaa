package cz.cvut.fel.pjv.arimaa.model;

/**
 * Root aggregate for match state: board, side to move, and lifecycle state.
 */
public class Game {

    private GameState state;
    private Board board;
    private PlayerSide sideToMove;

    public GameState getState() {
        return state;
    }

    public void setState(GameState state) {
        this.state = state;
    }

    public Board getBoard() {
        return board;
    }

    public void setBoard(Board board) {
        this.board = board;
    }

    public PlayerSide getSideToMove() {
        return sideToMove;
    }

    public void setSideToMove(PlayerSide sideToMove) {
        this.sideToMove = sideToMove;
    }

    /**
     * No-op in CP2; will apply a full turn later.
     */
    public void applyMove(Move move) {
    }
}
