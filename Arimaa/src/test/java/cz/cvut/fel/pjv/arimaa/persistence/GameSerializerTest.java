package cz.cvut.fel.pjv.arimaa.persistence;

import cz.cvut.fel.pjv.arimaa.controller.GameController;
import cz.cvut.fel.pjv.arimaa.model.Game;
import cz.cvut.fel.pjv.arimaa.model.Move;
import cz.cvut.fel.pjv.arimaa.model.enums.GameState;
import cz.cvut.fel.pjv.arimaa.model.enums.PieceType;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;
import cz.cvut.fel.pjv.arimaa.model.Position;
import cz.cvut.fel.pjv.arimaa.model.Step;
import cz.cvut.fel.pjv.arimaa.model.enums.StepKind;
import cz.cvut.fel.pjv.arimaa.util.ArimaaNotation;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameSerializerTest {

    @Test
    void roundTripTxtAfterOneGoldMove() {
        Game game = new Game();
        game.startNewGame();
        assertTrue(game.applyChessMappedSetup(PlayerSide.GOLD));
        assertTrue(game.tryCompleteSetup(PlayerSide.GOLD));
        assertTrue(game.applyChessMappedSetup(PlayerSide.SILVER));
        assertTrue(game.tryCompleteSetup(PlayerSide.SILVER));

        Move m = new Move();
        Step s = new Step();
        s.setKind(StepKind.SLIDE);
        s.setFrom(Position.of(0, 1));
        s.setTo(Position.of(0, 2));
        m.getSteps().add(s);

        GameController c = new GameController();
        c.setGame(game);
        c.resetTimeline();

        String line = ArimaaNotation.formatFullTurn(game.getBoard(), m, "1g");
        assertTrue(c.submitHumanMove(m));
        c.recordCommittedPlayTurn(m, line);

        GameSerializer ser = new GameSerializer();
        String text = ser.serialize(c);

        Game game2 = new Game();
        game2.startNewGame();
        GameController c2 = new GameController();
        c2.setGame(game2);
        c2.resetTimeline();

        GameSerializer.ParsedTxtGame p = ser.parse(text);
        c2.loadFromTxtGame(p.playStartSnapshot(), p.moveLines());

        assertEquals(line, c2.notationLinesVisible().getFirst());
        assertEquals(PieceType.RABBIT, game2.getBoard().getPiece(Position.of(0, 2)).getType());
        assertEquals(PlayerSide.GOLD, game2.getBoard().getPiece(Position.of(0, 2)).getSide());
    }

    @Test
    void loadSavedGameThroughTurnWithTrapRemovalTokensInNotation() throws Exception {
        String text;
        try (InputStream in =
                GameSerializerTest.class.getResourceAsStream("tested_game_1.txt")) {
            if (in == null) {
                throw new IllegalStateException("missing test resource tested_game_1.txt");
            }
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        GameSerializer ser = new GameSerializer();
        GameSerializer.ParsedTxtGame p = ser.parse(text);
        Game game = new Game();
        game.startNewGame();
        GameController c = new GameController();
        c.setGame(game);
        c.resetTimeline();
        int through6sInclusive = 12;
        assertDoesNotThrow(
                () -> c.loadFromTxtGame(p.playStartSnapshot(), p.moveLines().subList(0, through6sInclusive)));
        assertEquals(GameState.PLAY, c.getGame().getState());
    }
}
