package cz.cvut.fel.pjv.arimaa.model;

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
        assertEquals(reserveSize, game.getSetupReserveSnapshot(PlayerSide.GOLD).size() + 1);
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
        assertEquals(2L, game.getSetupReserveSnapshot(PlayerSide.GOLD).stream()
                .filter(p -> p.getType() == PieceType.DOG).count());
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
