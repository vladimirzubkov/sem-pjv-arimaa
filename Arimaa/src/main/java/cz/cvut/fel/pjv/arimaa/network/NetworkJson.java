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

    /** Shared ObjectMapper for wire JSON (single-threaded read/write per connection in practice). */
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /** Serializes one JSON object to a single line (newline-terminated records on the wire). */
    public static String writeLine(ObjectNode node) {
        try {
            return MAPPER.writeValueAsString(node);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Parses one incoming line and extracts the {@code type} discriminator plus the JSON root. */
    public static ParsedLine parseLine(String line) throws IOException {
        JsonNode root = MAPPER.readTree(line);
        JsonNode t = root.get("type");
        if (t == null || !t.isTextual()) {
            throw new IOException("missing string field 'type'");
        }
        return new ParsedLine(t.asText(), root);
    }

    public record ParsedLine(String type, JsonNode node) {}

    /** Deserializes client {@code hello} payload after TCP connect. */
    public static WireMessages.HelloMessage readHello(JsonNode n) throws IOException {
        return MAPPER.treeToValue(n, WireMessages.HelloMessage.class);
    }

    /** Deserializes server {@code welcome} (side assignment + Gold seat encoding). */
    public static WireMessages.WelcomeMessage readWelcome(JsonNode n) throws IOException {
        return MAPPER.treeToValue(n, WireMessages.WelcomeMessage.class);
    }

    /** Deserializes gameplay {@code intent} from peer (setup clicks, moves, CPU intents). */
    public static WireMessages.IntentMessage readIntent(JsonNode n) throws IOException {
        return MAPPER.treeToValue(n, WireMessages.IntentMessage.class);
    }

    /** Deserializes full game snapshot text from host resync messages. */
    public static WireMessages.StateSnapshotMessage readSnapshot(JsonNode n) throws IOException {
        return MAPPER.treeToValue(n, WireMessages.StateSnapshotMessage.class);
    }

    /** Deserializes protocol {@code error} line from peer. */
    public static WireMessages.ErrorMessage readError(JsonNode n) throws IOException {
        return MAPPER.treeToValue(n, WireMessages.ErrorMessage.class);
    }

    /** Deserializes mid-game seat control update (human/CPU assignment string). */
    public static WireMessages.SeatControlMessage readSeatControl(JsonNode n) throws IOException {
        return MAPPER.treeToValue(n, WireMessages.SeatControlMessage.class);
    }

    /** Client→server first line: protocol version and Silver seat control token. */
    public static String helloLine(int protocolVersion, String silverSeatControl) {
        return writeLine(
                MAPPER.createObjectNode()
                        .put("type", "hello")
                        .put("protocolVersion", protocolVersion)
                        .put("silverSeatControl", silverSeatControl));
    }

    /** Server→client handshake: mapped local side and Gold seat control token. */
    public static String welcomeLine(int protocolVersion, PlayerSide yourLocalSide, String goldSeatControl) {
        return writeLine(
                MAPPER.createObjectNode()
                        .put("type", "welcome")
                        .put("protocolVersion", protocolVersion)
                        .put("yourLocalSide", yourLocalSide.name())
                        .put("goldSeatControl", goldSeatControl));
    }

    /** Announces updated seat control for one side without full snapshot. */
    public static String seatControlLine(String seatControl) {
        return writeLine(
                MAPPER.createObjectNode().put("type", "seat_control").put("seatControl", seatControl));
    }

    /** Serializes a gameplay intent for TCP (used by host and client via {@link ArimaaNetworkCoordinator}). */
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

    /** Host→client or bidirectional save-game sync: embedded .txt snapshot in one line. */
    public static String snapshotLine(String saveText) {
        return writeLine(
                MAPPER.createObjectNode().put("type", "state_snapshot").put("saveText", saveText));
    }

    /** Protocol error line for user-visible disconnect / reject reasons. */
    public static String errorLine(String message) {
        return writeLine(MAPPER.createObjectNode().put("type", "error").put("message", message));
    }

    /** Graceful shutdown token on the wire. */
    public static String byeLine() {
        return writeLine(MAPPER.createObjectNode().put("type", "bye"));
    }

    /** Host keepalive probe (answered with {@link #pongLine()}). */
    public static String pingLine() {
        return writeLine(MAPPER.createObjectNode().put("type", "ping"));
    }

    /** Client response to {@link #pingLine()}. */
    public static String pongLine() {
        return writeLine(MAPPER.createObjectNode().put("type", "pong"));
    }
}
