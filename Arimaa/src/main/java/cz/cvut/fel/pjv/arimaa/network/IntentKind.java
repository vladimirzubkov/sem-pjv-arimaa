package cz.cvut.fel.pjv.arimaa.network;

/**
 * Player action forwarded to the host in a network game (Silver side).
 */
public enum IntentKind {
    SETUP_PICK,
    SETUP_BOARD_CLICK,
    SETUP_RANDOM,
    SETUP_CHESS,
    SETUP_COMPLETE,
    SETUP_CANCEL_HAND,
    /** Client asks host to run one-shot Silver setup autofill (CPU seat). */
    SETUP_SILVER_CPU_AUTOFILL,
    PLAY_ACTIVATE,
    PLAY_END_TURN,
    PLAY_CANCEL_DRAFT,
    /** Silver CPU submits a full committed turn as one notation line (host parses & applies). */
    PLAY_SUBMIT_NOTATION
}
