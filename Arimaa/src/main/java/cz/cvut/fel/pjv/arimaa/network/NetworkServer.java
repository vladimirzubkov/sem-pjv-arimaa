package cz.cvut.fel.pjv.arimaa.network;

/**
 * Accepts client connections and forwards game messages (planned after CP2).
 */
public interface NetworkServer {

    void listen(int port);

    void stop();
}
