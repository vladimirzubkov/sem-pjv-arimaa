package cz.cvut.fel.pjv.arimaa.model;

import cz.cvut.fel.pjv.arimaa.model.enums.PieceType;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;
import cz.cvut.fel.pjv.arimaa.model.enums.StepKind;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.Consumer;
import java.util.stream.Stream;

import static cz.cvut.fel.pjv.arimaa.model.PlayRuleTestFixtures.place;
import static cz.cvut.fel.pjv.arimaa.model.PlayRuleTestFixtures.playOnEmptyBoard;
import static cz.cvut.fel.pjv.arimaa.model.PlayRuleTestFixtures.pull;
import static cz.cvut.fel.pjv.arimaa.model.PlayRuleTestFixtures.push;
import static cz.cvut.fel.pjv.arimaa.model.PlayRuleTestFixtures.slide;
import static cz.cvut.fel.pjv.arimaa.model.PlayRuleTestFixtures.step;
import static cz.cvut.fel.pjv.arimaa.model.PlayRuleTestFixtures.stepOf;
import static cz.cvut.fel.pjv.arimaa.model.PlayRuleTestFixtures.steps;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parameterized coverage of PLAY step validation (slide, push, pull) via {@link DefaultRuleEngine}.
 */
class PlayStepValidationTest {

  private static final Position A1 = Position.of(0, 0);
  private static final Position A2 = Position.of(0, 1);
  private static final Position B2 = Position.of(1, 1);
  private static final Position B3 = Position.of(1, 2);
  private static final Position B4 = Position.of(1, 3);
  private static final Position C3 = Position.of(2, 2);
  private static final Position C4 = Position.of(2, 3);
  private static final Position D3 = Position.of(3, 2);
  private static final Position D4 = Position.of(3, 3);
  private static final Position E3 = Position.of(4, 3);
  private static final Position E4 = Position.of(4, 4);
  private static final Position E5 = Position.of(4, 5);
  private static final Position GOLD_RABBIT_RANK4 = Position.of(0, 3);
  private static final Position GOLD_RABBIT_RANK3 = Position.of(0, 2);
  private static final Position SILVER_RABBIT_RANK4 = Position.of(0, 4);
  private static final Position SILVER_RABBIT_RANK5 = Position.of(0, 5);

  @ParameterizedTest(name = "{0}")
  @MethodSource("prefixValidityCases")
  void isValidPlayPrefix_matchesExpected(
      String name, Consumer<Game> setup, Move move, boolean expectedValid) {
    Game g = playOnEmptyBoard(PlayerSide.GOLD);
    setup.accept(g);
    assertEquals(
        expectedValid,
        DefaultRuleEngine.isValidPlayPrefix(g, move),
        () -> "case: " + name);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("illegalMoveMessageCases")
  void applyMove_rejectsWithMessage(
      String name, Consumer<Game> setup, Move move, String expectedSubstring) {
    Game g = playOnEmptyBoard(PlayerSide.GOLD);
    setup.accept(g);
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> g.applyMove(move),
            () -> "case: " + name);
    assertTrue(
        ex.getMessage().contains(expectedSubstring),
        () -> "expected substring '%s' in '%s'".formatted(expectedSubstring, ex.getMessage()));
  }

  private static Stream<Arguments> prefixValidityCases() {
    return Stream.of(
        Arguments.of(
            "slide legal",
            setupBoard(b -> place(b, A1, PieceType.RABBIT, PlayerSide.GOLD)),
            slide(A1, A2),
            true),
        Arguments.of(
            "slide frozen",
            setupBoard(
                b -> {
                  place(b, C4, PieceType.RABBIT, PlayerSide.GOLD);
                  place(b, Position.of(2, 4), PieceType.DOG, PlayerSide.SILVER);
                }),
            slide(C4, Position.of(1, 3)),
            false),
        Arguments.of(
            "slide gold rabbit backward",
            setupBoard(b -> place(b, GOLD_RABBIT_RANK4, PieceType.RABBIT, PlayerSide.GOLD)),
            slide(GOLD_RABBIT_RANK4, GOLD_RABBIT_RANK3),
            false),
        Arguments.of(
            "slide silver rabbit backward",
            silverRabbitSetup(),
            slide(SILVER_RABBIT_RANK4, SILVER_RABBIT_RANK5),
            false),
        Arguments.of(
            "slide opponent piece",
            setupBoard(b -> place(b, A1, PieceType.RABBIT, PlayerSide.SILVER)),
            slide(A1, A2),
            false),
        Arguments.of(
            "slide occupied destination",
            setupBoard(
                b -> {
                  place(b, A1, PieceType.CAT, PlayerSide.GOLD);
                  place(b, A2, PieceType.CAT, PlayerSide.GOLD);
                }),
            slide(A1, A2),
            false),
        Arguments.of(
            "slide diagonal",
            setupBoard(b -> place(b, A1, PieceType.CAT, PlayerSide.GOLD)),
            slide(A1, B2),
            false),
        Arguments.of(
            "push legal",
            setupBoard(
                b -> {
                  place(b, E3, PieceType.CAT, PlayerSide.GOLD);
                  place(b, E4, PieceType.RABBIT, PlayerSide.SILVER);
                }),
            push(E4, E5, E3, E4),
            true),
        Arguments.of(
            "push frozen pusher",
            setupBoard(
                b -> {
                  place(b, E4, PieceType.CAT, PlayerSide.GOLD);
                  place(b, E5, PieceType.ELEPHANT, PlayerSide.SILVER);
                  place(b, D4, PieceType.RABBIT, PlayerSide.SILVER);
                }),
            push(D4, E3, E4, D4),
            false),
        Arguments.of(
            "push no stronger neighbor",
            setupBoard(
                b -> {
                  place(b, E4, PieceType.RABBIT, PlayerSide.GOLD);
                  place(b, E5, PieceType.ELEPHANT, PlayerSide.SILVER);
                }),
            push(E5, Position.of(4, 6), E4, E5),
            false),
        Arguments.of(
            "push advance without displace",
            setupBoard(b -> place(b, E3, PieceType.CAT, PlayerSide.GOLD)),
            step(StepKind.PUSH_ADVANCE_STRONGER, E3, E4),
            false),
        Arguments.of(
            "push displace without advance",
            setupBoard(
                b -> {
                  place(b, E3, PieceType.CAT, PlayerSide.GOLD);
                  place(b, E4, PieceType.RABBIT, PlayerSide.SILVER);
                }),
            step(StepKind.PUSH_DISPLACE_WEAKER, E4, E5),
            false),
        Arguments.of(
            "pull legal",
            setupBoard(
                b -> {
                  place(b, D4, PieceType.CAT, PlayerSide.GOLD);
                  place(b, E3, PieceType.RABBIT, PlayerSide.SILVER);
                }),
            pull(D4, D3, E3, D4),
            true),
        Arguments.of(
            "pull weak cannot drag strong",
            setupBoard(
                b -> {
                  place(b, C4, PieceType.CAMEL, PlayerSide.GOLD);
                  place(b, C3, PieceType.RABBIT, PlayerSide.GOLD);
                  place(b, D4, PieceType.ELEPHANT, PlayerSide.SILVER);
                }),
            pull(C4, B4, D4, C4),
            false),
        Arguments.of(
            "pull frozen vacater",
            setupBoard(
                b -> {
                  place(b, C4, PieceType.CAT, PlayerSide.GOLD);
                  place(b, Position.of(2, 4), PieceType.ELEPHANT, PlayerSide.SILVER);
                  place(b, D4, PieceType.RABBIT, PlayerSide.SILVER);
                }),
            pull(C4, C3, D4, C4),
            false),
        Arguments.of(
            "pull drag without vacate",
            setupBoard(
                b -> {
                  place(b, D4, PieceType.CAT, PlayerSide.GOLD);
                  place(b, E3, PieceType.RABBIT, PlayerSide.SILVER);
                }),
            step(StepKind.PULL_DRAG_WEAKER, E3, D4),
            false),
        Arguments.of(
            "pull vacate without drag",
            setupBoard(
                b -> {
                  place(b, D4, PieceType.CAT, PlayerSide.GOLD);
                  place(b, E3, PieceType.RABBIT, PlayerSide.SILVER);
                }),
            step(StepKind.PULL_VACATE_STRONGER, D4, D3),
            false),
        Arguments.of(
            "slide then pull drag legal",
            setupBoard(
                b -> {
                  place(b, D4, PieceType.CAT, PlayerSide.GOLD);
                  place(b, E3, PieceType.RABBIT, PlayerSide.SILVER);
                }),
            steps(stepOf(StepKind.SLIDE, D4, D3), stepOf(StepKind.PULL_DRAG_WEAKER, E3, D4)),
            true),
        Arguments.of(
            "slide then pull drag wrong target",
            setupBoard(
                b -> {
                  place(b, D4, PieceType.CAT, PlayerSide.GOLD);
                  place(b, E3, PieceType.RABBIT, PlayerSide.SILVER);
                }),
            steps(stepOf(StepKind.SLIDE, D4, D3), stepOf(StepKind.PULL_DRAG_WEAKER, E3, E4)),
            false));
  }

  private static Stream<Arguments> illegalMoveMessageCases() {
    return Stream.of(
        Arguments.of(
            "slide frozen message",
            setupBoard(
                b -> {
                  place(b, C4, PieceType.RABBIT, PlayerSide.GOLD);
                  place(b, Position.of(2, 4), PieceType.DOG, PlayerSide.SILVER);
                }),
            slide(C4, Position.of(1, 3)),
            "Frozen"),
        Arguments.of(
            "slide gold rabbit backward message",
            setupBoard(b -> place(b, GOLD_RABBIT_RANK4, PieceType.RABBIT, PlayerSide.GOLD)),
            slide(GOLD_RABBIT_RANK4, GOLD_RABBIT_RANK3),
            "Rabbit cannot move backward"),
        Arguments.of(
            "slide silver rabbit backward message",
            silverRabbitSetup(),
            slide(SILVER_RABBIT_RANK4, SILVER_RABBIT_RANK5),
            "Rabbit cannot move backward"),
        Arguments.of(
            "slide opponent piece message",
            setupBoard(b -> place(b, A1, PieceType.RABBIT, PlayerSide.SILVER)),
            slide(A1, A2),
            "own piece"),
        Arguments.of(
            "slide occupied destination message",
            setupBoard(
                b -> {
                  place(b, A1, PieceType.CAT, PlayerSide.GOLD);
                  place(b, A2, PieceType.CAT, PlayerSide.GOLD);
                }),
            slide(A1, A2),
            "empty"),
        Arguments.of(
            "slide diagonal message",
            setupBoard(b -> place(b, A1, PieceType.CAT, PlayerSide.GOLD)),
            slide(A1, B2),
            "orthogonal"),
        Arguments.of(
            "push frozen pusher message",
            setupBoard(
                b -> {
                  place(b, E4, PieceType.CAT, PlayerSide.GOLD);
                  place(b, E5, PieceType.ELEPHANT, PlayerSide.SILVER);
                  place(b, D4, PieceType.RABBIT, PlayerSide.SILVER);
                }),
            push(D4, E3, E4, D4),
            "No stronger adjacent piece to push"),
        Arguments.of(
            "push no stronger message",
            setupBoard(
                b -> {
                  place(b, E4, PieceType.RABBIT, PlayerSide.GOLD);
                  place(b, E5, PieceType.ELEPHANT, PlayerSide.SILVER);
                }),
            push(E5, Position.of(4, 6), E4, E5),
            "stronger"),
        Arguments.of(
            "push advance without displace message",
            setupBoard(b -> place(b, E3, PieceType.CAT, PlayerSide.GOLD)),
            step(StepKind.PUSH_ADVANCE_STRONGER, E3, E4),
            "PUSH_DISPLACE"),
        Arguments.of(
            "push displace without advance message",
            setupBoard(
                b -> {
                  place(b, E3, PieceType.CAT, PlayerSide.GOLD);
                  place(b, E4, PieceType.RABBIT, PlayerSide.SILVER);
                }),
            step(StepKind.PUSH_DISPLACE_WEAKER, E4, E5),
            "missing advance"),
        Arguments.of(
            "pull weak vs strong message",
            setupBoard(
                b -> {
                  place(b, C4, PieceType.CAMEL, PlayerSide.GOLD);
                  place(b, C3, PieceType.RABBIT, PlayerSide.GOLD);
                  place(b, D4, PieceType.ELEPHANT, PlayerSide.SILVER);
                }),
            pull(C4, B4, D4, C4),
            "stronger"),
        Arguments.of(
            "pull frozen message",
            setupBoard(
                b -> {
                  place(b, C4, PieceType.CAT, PlayerSide.GOLD);
                  place(b, Position.of(2, 4), PieceType.ELEPHANT, PlayerSide.SILVER);
                  place(b, D4, PieceType.RABBIT, PlayerSide.SILVER);
                }),
            pull(C4, C3, D4, C4),
            "Frozen"),
        Arguments.of(
            "pull drag without vacate message",
            setupBoard(
                b -> {
                  place(b, D4, PieceType.CAT, PlayerSide.GOLD);
                  place(b, E3, PieceType.RABBIT, PlayerSide.SILVER);
                }),
            step(StepKind.PULL_DRAG_WEAKER, E3, D4),
            "PULL_VACATE"),
        Arguments.of(
            "pull vacate without drag message",
            setupBoard(
                b -> {
                  place(b, D4, PieceType.CAT, PlayerSide.GOLD);
                  place(b, E3, PieceType.RABBIT, PlayerSide.SILVER);
                }),
            step(StepKind.PULL_VACATE_STRONGER, D4, D3),
            "missing drag"),
        Arguments.of(
            "slide pull drag wrong target message",
            setupBoard(
                b -> {
                  place(b, D4, PieceType.CAT, PlayerSide.GOLD);
                  place(b, E3, PieceType.RABBIT, PlayerSide.SILVER);
                }),
            steps(stepOf(StepKind.SLIDE, D4, D3), stepOf(StepKind.PULL_DRAG_WEAKER, E3, E4)),
            "vacated"));
  }

  private static Consumer<Game> setupBoard(Consumer<Board> boardSetup) {
    return g -> boardSetup.accept(g.getBoard());
  }

  private static Consumer<Game> silverRabbitSetup() {
    return g -> {
      g.setSideToMove(PlayerSide.SILVER);
      place(g.getBoard(), SILVER_RABBIT_RANK4, PieceType.RABBIT, PlayerSide.SILVER);
    };
  }
}
