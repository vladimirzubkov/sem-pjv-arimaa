package cz.cvut.fel.pjv.arimaa.model;

import cz.cvut.fel.pjv.arimaa.model.enums.GameState;
import cz.cvut.fel.pjv.arimaa.model.enums.PieceType;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;
import cz.cvut.fel.pjv.arimaa.model.enums.StepKind;

/** Builders for minimal PLAY positions and moves in rule-engine unit tests. */
final class PlayRuleTestFixtures {

    private PlayRuleTestFixtures() {}

    static Game playOnEmptyBoard(PlayerSide sideToMove) {
        Game g = new Game();
        g.startNewGame();
        g.getBoard().clear();
        g.setState(GameState.PLAY);
        g.setSideToMove(sideToMove);
        return g;
    }

    static void place(Board board, Position pos, PieceType type, PlayerSide side) {
        board.setPiece(pos, new Piece(type, side));
    }

    static Move slide(Position from, Position to) {
        Move m = new Move();
        Step s = new Step();
        s.setKind(StepKind.SLIDE);
        s.setFrom(from);
        s.setTo(to);
        m.getSteps().add(s);
        return m;
    }

    static Move push(Position weakFrom, Position weakTo, Position strongFrom, Position strongTo) {
        Move m = new Move();
        Step displace = new Step();
        displace.setKind(StepKind.PUSH_DISPLACE_WEAKER);
        displace.setFrom(weakFrom);
        displace.setTo(weakTo);
        Step advance = new Step();
        advance.setKind(StepKind.PUSH_ADVANCE_STRONGER);
        advance.setFrom(strongFrom);
        advance.setTo(strongTo);
        m.getSteps().add(displace);
        m.getSteps().add(advance);
        return m;
    }

    static Move pull(Position strongFrom, Position strongTo, Position weakFrom, Position weakTo) {
        Move m = new Move();
        Step vacate = new Step();
        vacate.setKind(StepKind.PULL_VACATE_STRONGER);
        vacate.setFrom(strongFrom);
        vacate.setTo(strongTo);
        Step drag = new Step();
        drag.setKind(StepKind.PULL_DRAG_WEAKER);
        drag.setFrom(weakFrom);
        drag.setTo(weakTo);
        m.getSteps().add(vacate);
        m.getSteps().add(drag);
        return m;
    }

    static Move step(StepKind kind, Position from, Position to) {
        Move m = new Move();
        Step s = new Step();
        s.setKind(kind);
        s.setFrom(from);
        s.setTo(to);
        m.getSteps().add(s);
        return m;
    }

    static Move steps(Step... steps) {
        Move m = new Move();
        for (Step s : steps) {
            m.getSteps().add(s);
        }
        return m;
    }

    static Step stepOf(StepKind kind, Position from, Position to) {
        Step s = new Step();
        s.setKind(kind);
        s.setFrom(from);
        s.setTo(to);
        return s;
    }
}
