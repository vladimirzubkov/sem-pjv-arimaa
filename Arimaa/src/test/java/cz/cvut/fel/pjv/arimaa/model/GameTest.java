package cz.cvut.fel.pjv.arimaa.model;

import cz.cvut.fel.pjv.arimaa.model.enums.GameState;
import cz.cvut.fel.pjv.arimaa.model.enums.PieceType;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;
import cz.cvut.fel.pjv.arimaa.model.enums.StepKind;
import cz.cvut.fel.pjv.arimaa.util.BoardConstants;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameTest {

    @Test
    void startNewGameSetsStateBoardAndSixteenPiecesPerSideWithOfficialMultiset() {
        Game game = new Game();
        game.startNewGame();

        assertEquals(GameState.SETUP_GOLD, game.getState());
        assertEquals(PlayerSide.GOLD, game.getSideToMove());
        assertFalse(game.isRanksMirroredForHomeCheck());
        assertNotNull(game.getBoard());

        assertReserveMultiset(game.getSetupReserveSnapshot(PlayerSide.GOLD), PlayerSide.GOLD);
        assertReserveMultiset(game.getSetupReserveSnapshot(PlayerSide.SILVER), PlayerSide.SILVER);
    }

    @Test
    void getSetupReserveSnapshotRejectsNullSide() {
        Game game = new Game();
        game.startNewGame();
        assertThrows(NullPointerException.class, () -> game.getSetupReserveSnapshot(null));
    }

    @Test
    void beginPlacingSilverDuringGoldSetupFails() {
        Game game = new Game();
        game.startNewGame();
        assertFalse(game.beginPlacingPieceFromReserve(PlayerSide.SILVER, PieceType.RABBIT));
    }

    @Test
    void beginAndConfirmPlacesPieceOnEmptyGoldHomeSquare() {
        Game game = new Game();
        game.startNewGame();
        int reserveSize = game.getSetupReserveSnapshot(PlayerSide.GOLD).size();

        assertTrue(game.beginPlacingPieceFromReserve(PlayerSide.GOLD, PieceType.RABBIT));
        assertEquals(reserveSize - 1, game.getSetupReserveSnapshot(PlayerSide.GOLD).size());

        Position a1 = Position.of(0, 0);
        assertTrue(game.confirmSetupHandPlacement(a1));

        Piece onBoard = game.getBoard().getPiece(a1);
        assertNotNull(onBoard);
        assertEquals(PieceType.RABBIT, onBoard.getType());
        assertEquals(PlayerSide.GOLD, onBoard.getSide());
        assertEquals(reserveSize - 2, game.getSetupReserveSnapshot(PlayerSide.GOLD).size());
        Piece nextHand = game.getSetupHand();
        assertNotNull(nextHand);
        assertEquals(PieceType.RABBIT, nextHand.getType());
        assertEquals(PlayerSide.GOLD, nextHand.getSide());
    }

    @Test
    void confirmWithoutPriorBeginFails() {
        Game game = new Game();
        game.startNewGame();
        assertFalse(game.confirmSetupHandPlacement(Position.of(0, 0)));
    }

    @Test
    void confirmOutsideGoldHomeFails() {
        Game game = new Game();
        game.startNewGame();
        assertTrue(game.beginPlacingPieceFromReserve(PlayerSide.GOLD, PieceType.RABBIT));
        assertFalse(game.confirmSetupHandPlacement(Position.of(0, 4)));
    }

    @Test
    void cancelPendingSetupReturnsPieceToReserve() {
        Game game = new Game();
        game.startNewGame();
        int n = game.getSetupReserveSnapshot(PlayerSide.GOLD).size();
        assertTrue(game.beginPlacingPieceFromReserve(PlayerSide.GOLD, PieceType.CAMEL));
        assertEquals(n - 1, game.getSetupReserveSnapshot(PlayerSide.GOLD).size());
        game.cancelPendingSetupPlacement();
        assertEquals(n, game.getSetupReserveSnapshot(PlayerSide.GOLD).size());
    }

    @Test
    void returnPieceFromBoardToReserveRestoresTray() {
        Game game = new Game();
        game.startNewGame();
        assertTrue(game.beginPlacingPieceFromReserve(PlayerSide.GOLD, PieceType.DOG));
        Position pos = Position.of(2, 1);
        assertTrue(game.confirmSetupHandPlacement(pos));
        assertFalse(game.getBoard().isEmpty(pos));

        assertTrue(game.returnPieceFromBoardToReserve(PlayerSide.GOLD, pos));
        assertTrue(game.getBoard().isEmpty(pos));
        long dogsInTray = game.getSetupReserveSnapshot(PlayerSide.GOLD).stream()
                .filter(p -> p.getType() == PieceType.DOG).count();
        assertEquals(1L, dogsInTray);
        Piece hand = game.getSetupHand();
        assertNotNull(hand);
        assertEquals(PieceType.DOG, hand.getType());
    }

    @Test
    void afterLastRabbitPlacedAutoSelectsStrongestInReserve() {
        Game game = new Game();
        game.startNewGame();
        for (int i = 0; i < BoardConstants.BOARD_SIZE; i++) {
            if (i == 0) {
                assertTrue(game.beginPlacingPieceFromReserve(PlayerSide.GOLD, PieceType.RABBIT));
            }
            assertTrue(game.confirmSetupHandPlacement(Position.of(i, 0)));
        }
        Piece h = game.getSetupHand();
        assertNotNull(h);
        assertEquals(PieceType.ELEPHANT, h.getType());
        assertEquals(PlayerSide.GOLD, h.getSide());
    }

    @Test
    void beginSecondPieceTypeReturnsFirstToReserve() {
        Game game = new Game();
        game.startNewGame();
        assertTrue(game.beginPlacingPieceFromReserve(PlayerSide.GOLD, PieceType.RABBIT));
        assertTrue(game.beginPlacingPieceFromReserve(PlayerSide.GOLD, PieceType.ELEPHANT));
        long rabbits = game.getSetupReserveSnapshot(PlayerSide.GOLD).stream()
                .filter(p -> p.getType() == PieceType.RABBIT).count();
        assertEquals(8L, rabbits);
    }

    @Test
    void placeRemainingPiecesRandomlyFillsFromFullReserve() {
        Game game = new Game();
        game.startNewGame();
        assertTrue(game.placeRemainingPiecesRandomly(PlayerSide.GOLD));
        assertEquals(0, game.getSetupReserveSnapshot(PlayerSide.GOLD).size());
        assertEquals(16, countGoldPiecesOnHome(game));
    }

    @Test
    void placeRemainingPiecesRandomlyAfterOneManualPlacement() {
        Game game = new Game();
        game.startNewGame();
        assertTrue(game.beginPlacingPieceFromReserve(PlayerSide.GOLD, PieceType.RABBIT));
        assertTrue(game.confirmSetupHandPlacement(Position.of(0, 0)));
        assertTrue(game.placeRemainingPiecesRandomly(PlayerSide.GOLD));
        assertEquals(0, game.getSetupReserveSnapshot(PlayerSide.GOLD).size());
        assertEquals(16, countGoldPiecesOnHome(game));
    }

    @Test
    void applyChessMappedSetupPlacesElephantOnE1() {
        Game game = new Game();
        game.startNewGame();
        assertTrue(game.applyChessMappedSetup(PlayerSide.GOLD));
        Piece e1 = game.getBoard().getPiece(Position.of(4, 0));
        assertNotNull(e1);
        assertEquals(PieceType.ELEPHANT, e1.getType());
        assertEquals(PlayerSide.GOLD, e1.getSide());
        assertEquals(0, game.getSetupReserveSnapshot(PlayerSide.GOLD).size());
    }

    @Test
    void tryCompleteSetupAfterChessGoldAdvancesToSilverSetup() {
        Game game = new Game();
        game.startNewGame();
        assertTrue(game.applyChessMappedSetup(PlayerSide.GOLD));
        assertTrue(game.tryCompleteSetup(PlayerSide.GOLD));
        assertEquals(GameState.SETUP_SILVER, game.getState());
        assertEquals(PlayerSide.SILVER, game.getSideToMove());
    }

    @Test
    void tryCompleteSetupFailsWhenReserveNotEmpty() {
        Game game = new Game();
        game.startNewGame();
        assertFalse(game.tryCompleteSetup(PlayerSide.GOLD));
    }

    @Test
    void tryCompleteSetupFailsWhenPieceHeldInHand() {
        Game game = new Game();
        game.startNewGame();
        assertTrue(game.applyChessMappedSetup(PlayerSide.GOLD));
        assertTrue(game.returnPieceFromBoardToReserve(PlayerSide.GOLD, Position.of(0, 1)));
        assertTrue(game.beginPlacingPieceFromReserve(PlayerSide.GOLD, PieceType.RABBIT));
        assertFalse(game.tryCompleteSetup(PlayerSide.GOLD));
    }

    @Test
    void fullChessSetupBothSidesThenPlay() {
        Game game = new Game();
        game.startNewGame();
        assertTrue(game.applyChessMappedSetup(PlayerSide.GOLD));
        assertTrue(game.tryCompleteSetup(PlayerSide.GOLD));
        assertTrue(game.applyChessMappedSetup(PlayerSide.SILVER));
        assertTrue(game.tryCompleteSetup(PlayerSide.SILVER));
        assertEquals(GameState.PLAY, game.getState());
        assertEquals(PlayerSide.GOLD, game.getSideToMove());
    }

    @Test
    void applyMoveOneOrthogonalStepSwitchesSide() {
        Game g = playOnEmptyBoard(PlayerSide.GOLD);
        g.getBoard().setPiece(Position.of(0, 0), new Piece(PieceType.RABBIT, PlayerSide.GOLD));
        g.getBoard().setPiece(Position.of(3, 3), new Piece(PieceType.RABBIT, PlayerSide.SILVER));
        Move m = new Move();
        Step s = new Step();
        s.setFrom(Position.of(0, 0));
        s.setTo(Position.of(0, 1));
        m.getSteps().add(s);
        g.applyMove(m);
        assertEquals(PlayerSide.SILVER, g.getSideToMove());
        assertNull(g.getBoard().getPiece(Position.of(0, 0)));
        assertEquals(PieceType.RABBIT, g.getBoard().getPiece(Position.of(0, 1)).getType());
    }

    @Test
    void applyMoveRejectsGoldRabbitBackward() {
        Game g = playOnEmptyBoard(PlayerSide.GOLD);
        g.getBoard().setPiece(Position.of(0, 3), new Piece(PieceType.RABBIT, PlayerSide.GOLD));
        Move m = new Move();
        Step s = new Step();
        s.setFrom(Position.of(0, 3));
        s.setTo(Position.of(0, 2));
        m.getSteps().add(s);
        assertThrows(IllegalArgumentException.class, () -> g.applyMove(m));
    }

    @Test
    void applyMoveRejectsDiagonalStep() {
        Game g = playOnEmptyBoard(PlayerSide.GOLD);
        g.getBoard().setPiece(Position.of(0, 0), new Piece(PieceType.CAT, PlayerSide.GOLD));
        Move m = new Move();
        Step s = new Step();
        s.setFrom(Position.of(0, 0));
        s.setTo(Position.of(1, 1));
        m.getSteps().add(s);
        assertThrows(IllegalArgumentException.class, () -> g.applyMove(m));
    }

    @Test
    void applyMoveRejectsMoreThanFourSteps() {
        Game g = playOnEmptyBoard(PlayerSide.GOLD);
        g.getBoard().setPiece(Position.of(3, 3), new Piece(PieceType.ELEPHANT, PlayerSide.GOLD));
        Move m = new Move();
        for (int i = 0; i < 5; i++) {
            Step s = new Step();
            if (i % 2 == 0) {
                s.setFrom(Position.of(3, 3));
                s.setTo(Position.of(4, 3));
            } else {
                s.setFrom(Position.of(4, 3));
                s.setTo(Position.of(3, 3));
            }
            m.getSteps().add(s);
        }
        assertThrows(IllegalArgumentException.class, () -> g.applyMove(m));
    }

    @Test
    void applyMoveRejectsMovingOpponentPiece() {
        Game g = playOnEmptyBoard(PlayerSide.GOLD);
        g.getBoard().setPiece(Position.of(0, 0), new Piece(PieceType.RABBIT, PlayerSide.SILVER));
        Move m = new Move();
        Step s = new Step();
        s.setFrom(Position.of(0, 0));
        s.setTo(Position.of(0, 1));
        m.getSteps().add(s);
        assertThrows(IllegalArgumentException.class, () -> g.applyMove(m));
    }

    @Test
    void trapRemovesGoldPieceOnC3WhenNoFriendlyNeighborAfterTurn() {
        Game g = playOnEmptyBoard(PlayerSide.GOLD);
        Position b3 = Position.fromAlgebraic("b3");
        Position c3 = Position.fromAlgebraic("c3");
        g.getBoard().setPiece(b3, new Piece(PieceType.RABBIT, PlayerSide.GOLD));
        g.getBoard().setPiece(c3, new Piece(PieceType.RABBIT, PlayerSide.GOLD));
        Move m = new Move();
        Step s = new Step();
        s.setFrom(b3);
        s.setTo(Position.of(1, 3));
        m.getSteps().add(s);
        g.applyMove(m);
        assertNull(g.getBoard().getPiece(c3));
        assertEquals(PieceType.RABBIT, g.getBoard().getPiece(Position.of(1, 3)).getType());
    }

    @Test
    void isValidPlayPrefixFalseForIllegalRabbitStep() {
        Game g = playOnEmptyBoard(PlayerSide.GOLD);
        g.getBoard().setPiece(Position.of(0, 3), new Piece(PieceType.RABBIT, PlayerSide.GOLD));
        Move m = new Move();
        Step s = new Step();
        s.setFrom(Position.of(0, 3));
        s.setTo(Position.of(0, 2));
        m.getSteps().add(s);
        assertFalse(DefaultRuleEngine.isValidPlayPrefix(g, m));
    }

    @Test
    void simulatePlayPrefixRemovesTrapVictimAfterMidTurnStep() {
        Game g = playOnEmptyBoard(PlayerSide.GOLD);
        Position b3 = Position.fromAlgebraic("b3");
        Position c3 = Position.fromAlgebraic("c3");
        g.getBoard().setPiece(b3, new Piece(PieceType.RABBIT, PlayerSide.GOLD));
        g.getBoard().setPiece(c3, new Piece(PieceType.RABBIT, PlayerSide.GOLD));
        Move prefix = new Move();
        Step s = new Step();
        s.setKind(StepKind.SLIDE);
        s.setFrom(b3);
        s.setTo(Position.of(1, 3));
        prefix.getSteps().add(s);
        Map<Position, Piece> occ = DefaultRuleEngine.simulatePlayPrefix(g, prefix);
        assertNull(occ.get(c3));
        assertNotNull(occ.get(Position.of(1, 3)));
        assertEquals(PieceType.RABBIT, occ.get(Position.of(1, 3)).getType());
    }

    @Test
    void applyMovePushAdvancesStrongerOntoWeakerOrigin() {
        Game g = playOnEmptyBoard(PlayerSide.GOLD);
        Position cat = Position.of(4, 3);
        Position rab = Position.of(4, 4);
        Position dest = Position.of(4, 5);
        g.getBoard().setPiece(cat, new Piece(PieceType.CAT, PlayerSide.GOLD));
        g.getBoard().setPiece(rab, new Piece(PieceType.RABBIT, PlayerSide.SILVER));
        Move m = new Move();
        Step d = new Step();
        d.setKind(StepKind.PUSH_DISPLACE_WEAKER);
        d.setFrom(rab);
        d.setTo(dest);
        Step a = new Step();
        a.setKind(StepKind.PUSH_ADVANCE_STRONGER);
        a.setFrom(cat);
        a.setTo(rab);
        m.getSteps().add(d);
        m.getSteps().add(a);
        g.applyMove(m);
        assertNull(g.getBoard().getPiece(cat));
        Piece onWeakSquare = g.getBoard().getPiece(rab);
        assertNotNull(onWeakSquare);
        assertEquals(PieceType.CAT, onWeakSquare.getType());
        assertEquals(PlayerSide.GOLD, onWeakSquare.getSide());
        Piece pushed = g.getBoard().getPiece(dest);
        assertNotNull(pushed);
        assertEquals(PieceType.RABBIT, pushed.getType());
        assertEquals(PlayerSide.SILVER, pushed.getSide());
    }

    @Test
    void applyMovePullVacatesThenDragsWeaker() {
        Game g = playOnEmptyBoard(PlayerSide.GOLD);
        Position cat = Position.of(3, 3);
        Position rab = Position.of(4, 3);
        Position vac = Position.of(3, 2);
        g.getBoard().setPiece(cat, new Piece(PieceType.CAT, PlayerSide.GOLD));
        g.getBoard().setPiece(rab, new Piece(PieceType.RABBIT, PlayerSide.SILVER));
        Move m = new Move();
        Step v = new Step();
        v.setKind(StepKind.PULL_VACATE_STRONGER);
        v.setFrom(cat);
        v.setTo(vac);
        Step drag = new Step();
        drag.setKind(StepKind.PULL_DRAG_WEAKER);
        drag.setFrom(rab);
        drag.setTo(cat);
        m.getSteps().add(v);
        m.getSteps().add(drag);
        g.applyMove(m);
        assertNull(g.getBoard().getPiece(rab));
        assertEquals(PieceType.CAT, g.getBoard().getPiece(vac).getType());
        Piece onOldCat = g.getBoard().getPiece(cat);
        assertNotNull(onOldCat);
        assertEquals(PieceType.RABBIT, onOldCat.getType());
        assertEquals(PlayerSide.SILVER, onOldCat.getSide());
    }

    @Test
    void applyMoveSlideThenPullDragSameAsPullVacatePair() {
        Game g = playOnEmptyBoard(PlayerSide.GOLD);
        Position cat = Position.of(3, 3);
        Position rab = Position.of(4, 3);
        Position vac = Position.of(3, 2);
        g.getBoard().setPiece(cat, new Piece(PieceType.CAT, PlayerSide.GOLD));
        g.getBoard().setPiece(rab, new Piece(PieceType.RABBIT, PlayerSide.SILVER));
        Move m = new Move();
        Step slide = new Step();
        slide.setKind(StepKind.SLIDE);
        slide.setFrom(cat);
        slide.setTo(vac);
        Step drag = new Step();
        drag.setKind(StepKind.PULL_DRAG_WEAKER);
        drag.setFrom(rab);
        drag.setTo(cat);
        m.getSteps().add(slide);
        m.getSteps().add(drag);
        g.applyMove(m);
        assertNull(g.getBoard().getPiece(rab));
        assertEquals(PieceType.CAT, g.getBoard().getPiece(vac).getType());
        Piece onOldCat = g.getBoard().getPiece(cat);
        assertNotNull(onOldCat);
        assertEquals(PieceType.RABBIT, onOldCat.getType());
        assertEquals(PlayerSide.SILVER, onOldCat.getSide());
    }

    @Test
    void frozenPieceCannotSlide() {
        Game g = playOnEmptyBoard(PlayerSide.GOLD);
        Position r = Position.of(2, 3);
        g.getBoard().setPiece(r, new Piece(PieceType.RABBIT, PlayerSide.GOLD));
        g.getBoard().setPiece(Position.of(2, 4), new Piece(PieceType.DOG, PlayerSide.SILVER));
        Move m = new Move();
        Step s = new Step();
        s.setKind(StepKind.SLIDE);
        s.setFrom(r);
        s.setTo(Position.of(1, 3));
        m.getSteps().add(s);
        assertFalse(DefaultRuleEngine.isValidPlayPrefix(g, m));
        assertTrue(DefaultRuleEngine.isFrozen(g.getBoard(), r));
    }

    @Test
    void goldRabbitReachingGoalRankEndsGameWithGoldWinner() {
        Game g = playOnEmptyBoard(PlayerSide.GOLD);
        g.getBoard().setPiece(Position.of(0, 6), new Piece(PieceType.RABBIT, PlayerSide.GOLD));
        Move m = new Move();
        Step s = new Step();
        s.setKind(StepKind.SLIDE);
        s.setFrom(Position.of(0, 6));
        s.setTo(Position.of(0, 7));
        m.getSteps().add(s);
        g.applyMove(m);
        assertEquals(GameState.GAME_OVER, g.getState());
        assertEquals(PlayerSide.GOLD, g.getMatchWinner());
    }

    @Test
    void sideWithoutRabbitsLosesWhenOpponentStillHasRabbit() {
        Game g = playOnEmptyBoard(PlayerSide.GOLD);
        g.getBoard().setPiece(Position.of(0, 0), new Piece(PieceType.CAT, PlayerSide.GOLD));
        g.getBoard().setPiece(Position.of(7, 7), new Piece(PieceType.RABBIT, PlayerSide.SILVER));
        Move m = new Move();
        Step s = new Step();
        s.setKind(StepKind.SLIDE);
        s.setFrom(Position.of(0, 0));
        s.setTo(Position.of(0, 1));
        m.getSteps().add(s);
        g.applyMove(m);
        assertEquals(GameState.GAME_OVER, g.getState());
        assertEquals(PlayerSide.SILVER, g.getMatchWinner());
    }

    /**
     * Silver rabbits may sit on rank 8 (index 7) like in the opening; that must not end the game as a false
     * „illegal on goal row“ win.
     */
    @Test
    void goldMoveWithSilverRabbitOnBackRankDoesNotInstantlyEndGame() {
        Game g = playOnEmptyBoard(PlayerSide.GOLD);
        g.getBoard().setPiece(Position.of(0, 7), new Piece(PieceType.RABBIT, PlayerSide.SILVER));
        g.getBoard().setPiece(Position.of(0, 6), new Piece(PieceType.RABBIT, PlayerSide.GOLD));
        Move m = new Move();
        Step s = new Step();
        s.setKind(StepKind.SLIDE);
        s.setFrom(Position.of(0, 6));
        s.setTo(Position.of(1, 6));
        m.getSteps().add(s);
        g.applyMove(m);
        assertEquals(GameState.PLAY, g.getState());
        assertEquals(PlayerSide.SILVER, g.getSideToMove());
    }

    @Test
    void immobilizedSideToMoveLosesAfterOpponentTurn() {
        Game g = playOnEmptyBoard(PlayerSide.GOLD);
        g.getBoard().setPiece(Position.of(4, 4), new Piece(PieceType.RABBIT, PlayerSide.SILVER));
        g.getBoard().setPiece(Position.of(4, 3), new Piece(PieceType.DOG, PlayerSide.GOLD));
        g.getBoard().setPiece(Position.of(0, 0), new Piece(PieceType.RABBIT, PlayerSide.GOLD));
        assertTrue(DefaultRuleEngine.existsLegalTurn(g));
        Move m = new Move();
        Step s = new Step();
        s.setKind(StepKind.SLIDE);
        s.setFrom(Position.of(0, 0));
        s.setTo(Position.of(0, 1));
        m.getSteps().add(s);
        g.applyMove(m);
        assertEquals(GameState.GAME_OVER, g.getState());
        assertEquals(PlayerSide.GOLD, g.getMatchWinner());
    }

    @Test
    void applyMoveThrowsWhenGameAlreadyOver() {
        Game g = playOnEmptyBoard(PlayerSide.GOLD);
        g.setState(GameState.GAME_OVER);
        g.setMatchWinner(PlayerSide.SILVER);
        Move m = new Move();
        Step s = new Step();
        s.setFrom(Position.of(0, 0));
        s.setTo(Position.of(0, 1));
        m.getSteps().add(s);
        assertThrows(IllegalStateException.class, () -> g.applyMove(m));
    }

    private static Game playOnEmptyBoard(PlayerSide sideToMove) {
        Game g = new Game();
        g.startNewGame();
        g.getBoard().clear();
        g.setState(GameState.PLAY);
        g.setSideToMove(sideToMove);
        return g;
    }

    private static int countGoldPiecesOnHome(Game game) {
        int n = 0;
        for (int r = 0; r < 2; r++) {
            for (int f = 0; f < 8; f++) {
                Piece p = game.getBoard().getPiece(Position.of(f, r));
                if (p != null && p.getSide() == PlayerSide.GOLD) {
                    n++;
                }
            }
        }
        return n;
    }

    @Test
    void canFillRemainingReserveRandomly_trueBeforeFill_falseAfterSuccessfulFill() {
        Game game = new Game();
        game.startNewGame();
        assertTrue(game.canFillRemainingReserveRandomly(PlayerSide.GOLD));
        assertTrue(game.placeRemainingPiecesRandomly(PlayerSide.GOLD));
        assertFalse(game.canFillRemainingReserveRandomly(PlayerSide.GOLD));
        assertTrue(game.getSetupReserveSnapshot(PlayerSide.GOLD).isEmpty());
    }

    @Test
    void setupReserveCountsByType_sumsToReserveSizeAtStart() {
        Game game = new Game();
        game.startNewGame();
        Map<PieceType, Integer> m = game.setupReserveCountsByType(PlayerSide.GOLD);
        int sum = m.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(game.getSetupReserveSnapshot(PlayerSide.GOLD).size(), sum);
        assertEquals(8, m.get(PieceType.RABBIT).intValue());
    }

    private static void assertReserveMultiset(List<Piece> reserve, PlayerSide expectedSide) {
        assertEquals(16, reserve.size());
        for (Piece p : reserve) {
            assertEquals(expectedSide, p.getSide());
            assertNull(p.getPosition());
        }
        Map<PieceType, Long> counts = reserve.stream()
                .collect(Collectors.groupingBy(Piece::getType, Collectors.counting()));
        assertEquals(1L, counts.get(PieceType.ELEPHANT));
        assertEquals(1L, counts.get(PieceType.CAMEL));
        assertEquals(2L, counts.get(PieceType.HORSE));
        assertEquals(2L, counts.get(PieceType.DOG));
        assertEquals(2L, counts.get(PieceType.CAT));
        assertEquals(8L, counts.get(PieceType.RABBIT));
    }
}
