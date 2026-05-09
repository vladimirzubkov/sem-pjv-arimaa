package cz.cvut.fel.pjv.arimaa.network;

import cz.cvut.fel.pjv.arimaa.model.enums.PlayerControllerKind;

/** Encodes who controls a seat in handshake / {@code seat_control} messages (not {@link PlayerControllerKind#NETWORK_PEER}). */
public final class NetworkAssignmentCodec {

    private NetworkAssignmentCodec() {}

    public static String encode(PlayerControllerKind k) {
        return switch (k) {
            case HUMAN -> "HUMAN";
            case COMPUTER_LEVEL_0 -> "CPU0";
            case COMPUTER_LEVEL_1 -> "CPU1";
            case COMPUTER_LEVEL_2 -> "CPU2";
            case NETWORK_PEER -> throw new IllegalArgumentException("cannot encode NETWORK_PEER");
        };
    }

    public static PlayerControllerKind decode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("missing assignment");
        }
        return switch (raw.trim()) {
            case "HUMAN" -> PlayerControllerKind.HUMAN;
            case "CPU0" -> PlayerControllerKind.COMPUTER_LEVEL_0;
            case "CPU1" -> PlayerControllerKind.COMPUTER_LEVEL_1;
            case "CPU2" -> PlayerControllerKind.COMPUTER_LEVEL_2;
            default -> throw new IllegalArgumentException("unknown assignment: " + raw);
        };
    }
}
