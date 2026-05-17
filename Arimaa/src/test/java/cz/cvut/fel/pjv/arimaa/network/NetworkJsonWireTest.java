package cz.cvut.fel.pjv.arimaa.network;

import cz.cvut.fel.pjv.arimaa.model.enums.PieceType;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NetworkJsonWireTest {

    @Test
    void intentRoundTripIgnoresTypeProperty() throws IOException {
        WireMessages.IntentMessage original =
                new WireMessages.IntentMessage(IntentKind.SETUP_PICK, PieceType.RABBIT, null, null, null);
        String line = NetworkJson.intentLine(original);
        NetworkJson.ParsedLine p = NetworkJson.parseLine(line);
        assertEquals("intent", p.type());
        WireMessages.IntentMessage back = NetworkJson.readIntent(p.node());
        assertEquals(IntentKind.SETUP_PICK, back.kind());
        assertEquals(PieceType.RABBIT, back.pieceType());
    }

    @Test
    void welcomeRoundTripV2() throws IOException {
        String line = NetworkJson.welcomeLine(2, PlayerSide.SILVER, "CPU1");
        NetworkJson.ParsedLine p = NetworkJson.parseLine(line);
        assertEquals("welcome", p.type());
        WireMessages.WelcomeMessage w = NetworkJson.readWelcome(p.node());
        assertEquals(2, w.protocolVersion());
        assertEquals(PlayerSide.SILVER, w.yourLocalSide());
        assertEquals("CPU1", w.goldSeatControl());
    }

    @Test
    void helloRoundTripV2() throws IOException {
        String line = NetworkJson.helloLine(2, "HUMAN");
        NetworkJson.ParsedLine p = NetworkJson.parseLine(line);
        assertEquals("hello", p.type());
        WireMessages.HelloMessage h = NetworkJson.readHello(p.node());
        assertEquals(2, h.protocolVersion());
        assertEquals("HUMAN", h.silverSeatControl());
    }

    @Test
    void intentNotationRoundTrip() throws IOException {
        WireMessages.IntentMessage original =
                new WireMessages.IntentMessage(
                        IntentKind.PLAY_SUBMIT_NOTATION, null, null, null, null, "1g Ra1 Rb1 Rc1 Rd1");
        String line = NetworkJson.intentLine(original);
        NetworkJson.ParsedLine p = NetworkJson.parseLine(line);
        WireMessages.IntentMessage back = NetworkJson.readIntent(p.node());
        assertEquals(IntentKind.PLAY_SUBMIT_NOTATION, back.kind());
        assertEquals("1g Ra1 Rb1 Rc1 Rd1", back.notationLine());
    }

    @Test
    void snapshotRoundTrip() throws IOException {
        String line = NetworkJson.snapshotLine("ArimaaTxtSave 1\n");
        NetworkJson.ParsedLine p = NetworkJson.parseLine(line);
        assertEquals("state_snapshot", p.type());
        WireMessages.StateSnapshotMessage s = NetworkJson.readSnapshot(p.node());
        assertEquals("ArimaaTxtSave 1\n", s.saveText());
    }

    @Test
    void snapshotIgnoresUnknownTopLevelJsonFields() throws IOException {
        String line =
                NetworkJson.mapper()
                        .createObjectNode()
                        .put("type", "state_snapshot")
                        .put("saveText", "x")
                        .put("futureField", 42)
                        .toString();
        WireMessages.StateSnapshotMessage s = NetworkJson.readSnapshot(NetworkJson.parseLine(line).node());
        assertEquals("x", s.saveText());
    }

    @Test
    void pingPongLines() throws IOException {
        assertEquals("ping", NetworkJson.parseLine(NetworkJson.pingLine()).type());
        assertEquals("pong", NetworkJson.parseLine(NetworkJson.pongLine()).type());
    }
}
