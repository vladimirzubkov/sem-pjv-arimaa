package cz.cvut.fel.pjv.arimaa.network;

/**
 * Outbound connection to a remote host (TCP/IP planned after CP2).
 */
public interface NetworkClient {

    void connect(String host, int port);

    void send(GameMessage message);

    void disconnect();
}
