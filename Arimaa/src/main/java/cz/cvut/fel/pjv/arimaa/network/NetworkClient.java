package cz.cvut.fel.pjv.arimaa.network;

/**
 * Outbound connection to a remote host (TCP/IP planned after CP2).
 */
public interface NetworkClient {

    /** Opens a TCP session to {@code host}:{@code port} (implementation pending). */
    void connect(String host, int port);

    /** Sends one protocol message to the peer. */
    void send(GameMessage message);

    /** Closes the connection and releases resources. */
    void disconnect();
}
