package cz.cvut.fel.pjv.arimaa.network;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import cz.cvut.fel.pjv.arimaa.model.enums.PieceType;
import cz.cvut.fel.pjv.arimaa.model.enums.PlayerSide;

/** NDJSON wire payloads (one JSON object per line, UTF-8). */
public final class WireMessages {

    private WireMessages() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HelloMessage(int protocolVersion, String silverSeatControl) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WelcomeMessage(int protocolVersion, PlayerSide yourLocalSide, String goldSeatControl) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IntentMessage(
            IntentKind kind,
            PieceType pieceType,
            Integer file,
            Integer rank,
            Integer presetIndex,
            String notationLine) {
        public IntentMessage(
                IntentKind kind, PieceType pieceType, Integer file, Integer rank, Integer presetIndex) {
            this(kind, pieceType, file, rank, presetIndex, null);
        }
    }

    /** Host announces Gold seat control; client announces Silver seat control. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeatControlMessage(String seatControl) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StateSnapshotMessage(String saveText) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorMessage(String message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ByeMessage() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PingMessage() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PongMessage() {}
}
