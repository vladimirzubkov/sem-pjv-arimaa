package cz.cvut.fel.pjv.arimaa.network;

import cz.cvut.fel.pjv.arimaa.model.enums.PlayerControllerKind;

/**
 * Application callbacks for {@link ArimaaNetworkCoordinator}: game/session state and UI updates.
 * Implemented by the main window controller on the JavaFX thread.
 */
public interface NetworkGameBridge {

    void setStatus(String message);

    void clearNetworkSessionAfterDisconnect();

    void prepareNetworkSessionAsHost(PlayerControllerKind peerSilverAssignment);

    void prepareNetworkSessionAsClient(PlayerControllerKind peerGoldAssignment);

    void startNewGameAfterNetworkHostReady();

    String buildNetworkSnapshotSaveText();

    void applyNetworkSnapshotSaveText(String text);

    boolean applyHostIntentFromNetwork(WireMessages.IntentMessage intent);

    void applyNetworkPeerSilverSeatFromWire(String wire);

    void applyNetworkPeerGoldSeatFromWire(String wire);

    PlayerControllerKind getGoldPlayerKind();

    PlayerControllerKind getSilverPlayerKind();

    boolean isApplyingNetworkSnapshot();

    /**
     * Clears client-side „waiting for host snapshot“ after {@code error} or disconnect; restores a clean trailing
     * draft when an intent was rejected so CPU/UI can recover without a half-applied local animation.
     */
    void clearNetworkClientAwaitingHostSync();
}
