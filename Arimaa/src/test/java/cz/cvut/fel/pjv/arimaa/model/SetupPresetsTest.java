package cz.cvut.fel.pjv.arimaa.model;

import cz.cvut.fel.pjv.arimaa.model.enums.PieceType;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetupPresetsTest {

    @Test
    void classicSilverMatchesMirrorOfClassicGold() {
        List<SetupPresets.Slot> silver = SetupPresets.classicSilver();
        List<SetupPresets.Slot> mirror = SetupPresets.mirrorGoldHomeToSilver(SetupPresets.classicGold());
        assertSlotMultisetsEqual(silver, mirror);
        assertTrue(SetupPresets.isValidSilverHomeOfficialMultiset(silver));
    }

    @Test
    void mirrorTwiceOnGoldRestoresRanks() {
        List<SetupPresets.Slot> gold = SetupPresets.classicGold();
        List<SetupPresets.Slot> silver = SetupPresets.mirrorGoldHomeToSilver(gold);
        List<SetupPresets.Slot> back = SetupPresets.mirrorSilverHomeToGold(silver);
        assertSlotMultisetsEqual(gold, back);
    }

    @Test
    void rotatingSilver0MatchesMirrorOfGoldReversed() {
        List<SetupPresets.Slot> a = SetupPresets.rotatingSilver(0);
        List<SetupPresets.Slot> b = SetupPresets.mirrorGoldHomeToSilver(SetupPresets.rotatingGold(0));
        assertSlotMultisetsEqual(a, b);
    }

    @Test
    void invalidMultisetRejected() {
        List<SetupPresets.Slot> bad = List.of(
                new SetupPresets.Slot(Position.of(0, 0), PieceType.ELEPHANT),
                new SetupPresets.Slot(Position.of(1, 0), PieceType.ELEPHANT));
        assertTrue(bad.size() < 16);
        assertThrows(IllegalArgumentException.class, () -> SetupPresets.requireValidGoldHomeOfficialMultiset(bad));
    }

    private static void assertSlotMultisetsEqual(List<SetupPresets.Slot> a, List<SetupPresets.Slot> b) {
        assertEquals(a.size(), b.size());
        assertEqualsMultisetKeys(a, b);
    }

    private static void assertEqualsMultisetKeys(List<SetupPresets.Slot> a, List<SetupPresets.Slot> b) {
        assertEquals(toKeys(a), toKeys(b));
    }

    private static Set<String> toKeys(List<SetupPresets.Slot> slots) {
        Set<String> s = new HashSet<>();
        for (SetupPresets.Slot sl : slots) {
            Position p = sl.position();
            s.add(p.getFileIndex() + "," + p.getRankIndex() + "=" + sl.type());
        }
        assertEquals(slots.size(), s.size(), "duplicate positions in list");
        return s;
    }
}
