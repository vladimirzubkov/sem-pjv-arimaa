package cz.cvut.fel.pjv.arimaa.network;

/**
 * Accepts client connections and forwards game messages (planned after CP2).
 */
public interface NetworkServer {

    /** Binds to {@code port} and accepts clients (implementation pending). */
    void listen(int port);

    /** Stops accepting connections and shuts down the server socket. */
    void stop();
}
