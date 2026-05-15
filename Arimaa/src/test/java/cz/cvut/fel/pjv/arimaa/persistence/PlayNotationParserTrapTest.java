package cz.cvut.fel.pjv.arimaa.persistence;

import cz.cvut.fel.pjv.arimaa.controller.GameController;
import cz.cvut.fel.pjv.arimaa.model.Game;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayNotationParserTrapTest {

    @Test
    void parseTurnWithTrailingTrapToken_hasFourStepsNotFive() throws Exception {
        String text;
        try (InputStream in = PlayNotationParserTrapTest.class.getResourceAsStream("tested_game_1.txt")) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        GameSerializer ser = new GameSerializer();
        var p = ser.parse(text);
        Game game = new Game();
        game.startNewGame();
        GameController c = new GameController();
        c.setGame(game);
        c.resetTimeline();
        c.loadFromTxtGame(p.playStartSnapshot(), p.moveLines().subList(0, 11));

        var pl = PlayNotationParser.parseLine(c.getGame(), "6s mb6n Rb5n cc6s Rb6e Rc6x");
        assertEquals(4, pl.move().getSteps().size(), "trap token must not become a fifth step");
        assertTrue(PlayNotationParser.isTrapRemovalNotation("Rc6x"));
    }
}
