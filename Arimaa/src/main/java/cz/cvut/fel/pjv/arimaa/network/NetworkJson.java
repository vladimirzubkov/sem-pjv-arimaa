package cz.cvut.fel.pjv.arimaa.network;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Line-oriented JSON codec for the Arimaa TCP protocol.
 */
public final class NetworkJson {

    public static final int PROTOCOL_VERSION = 2;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private NetworkJson() {}

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static String writeLine(ObjectNode node) {
        try {
            return MAPPER.writeValueAsString(node);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static ParsedLine parseLine(String line) throws IOException {
        JsonNode root = MAPPER.readTree(line);
        JsonNode t = root.get("type");
        if (t == null || !t.isTextual()) {
            throw new IOException("missing string field 'type'");
        }
        return new ParsedLine(t.asText(), root);
    }

    public record ParsedLine(String type, JsonNode node) {}

    public static WireMessages.HelloMessage readHello(JsonNode n) throws IOException {
        return MAPPER.treeToValue(n, WireMessages.HelloMessage.class);
    }

    public static WireMessages.WelcomeMessage readWelcome(JsonNode n) throws IOException {
        return MAPPER.treeToValue(n, WireMessages.WelcomeMessage.class);
    }

    public static WireMessages.IntentMessage readIntent(JsonNode n) throws IOException {
        return MAPPER.treeToValue(n, WireMessages.IntentMessage.class);
    }

    public static WireMessages.StateSnapshotMessage readSnapshot(JsonNode n) throws IOException {
        return MAPPER.treeToValue(n, WireMessages.StateSnapshotMessage.class);
    }

    public static WireMessages.ErrorMessage readError(JsonNode n) throws IOException {
        return MAPPER.treeToValue(n, WireMessages.ErrorMessage.class);
    }

    public static WireMessages.SeatControlMessage readSeatControl(JsonNode n) throws IOException {
        return MAPPER.treeToValue(n, WireMessages.SeatControlMessage.class);
    }

    public static String helloLine(int protocolVersion, String silverSeatControl) {
        return writeLine(
                MAPPER.createObjectNode()
                        .put("type", "hello")
                        .put("protocolVersion", protocolVersion)
                        .put("silverSeatControl", silverSeatControl));
    }

    public static String welcomeLine(int protocolVersion, PlayerSide yourLocalSide, String goldSeatControl) {
        return writeLine(
                MAPPER.createObjectNode()
                        .put("type", "welcome")
                        .put("protocolVersion", protocolVersion)
                        .put("yourLocalSide", yourLocalSide.name())
                        .put("goldSeatControl", goldSeatControl));
    }

    public static String seatControlLine(String seatControl) {
        return writeLine(
                MAPPER.createObjectNode().put("type", "seat_control").put("seatControl", seatControl));
    }

    public static String intentLine(WireMessages.IntentMessage intent) {
        ObjectNode o = MAPPER.createObjectNode();
        o.put("type", "intent");
        o.put("kind", intent.kind().name());
        if (intent.pieceType() != null) {
            o.put("pieceType", intent.pieceType().name());
        }
        if (intent.file() != null) {
            o.put("file", intent.file());
        }
        if (intent.rank() != null) {
            o.put("rank", intent.rank());
        }
        if (intent.presetIndex() != null) {
            o.put("presetIndex", intent.presetIndex());
        }
        if (intent.notationLine() != null && !intent.notationLine().isBlank()) {
            o.put("notationLine", intent.notationLine());
        }
        return writeLine(o);
    }

    public static String snapshotLine(String saveText) {
        return writeLine(
                MAPPER.createObjectNode().put("type", "state_snapshot").put("saveText", saveText));
    }

    public static String errorLine(String message) {
        return writeLine(MAPPER.createObjectNode().put("type", "error").put("message", message));
    }

    public static String byeLine() {
        return writeLine(MAPPER.createObjectNode().put("type", "bye"));
    }

    public static String pingLine() {
        return writeLine(MAPPER.createObjectNode().put("type", "ping"));
    }

    public static String pongLine() {
        return writeLine(MAPPER.createObjectNode().put("type", "pong"));
    }
}
