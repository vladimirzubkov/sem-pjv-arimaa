package cz.cvut.fel.pjv.arimaa.network;

import java.net.Inet4Address;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.LinkedHashSet;

/** Local IPv4 addresses useful for telling a peer how to connect. */
public final class NetworkLocalAddresses {

    private NetworkLocalAddresses() {}

    /**
     * Human-readable list of this machine’s non-loopback IPv4 addresses (host dialog “your IP” block).
     */
    public static String ipv4TextBlock() {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    if (ia.getAddress() instanceof Inet4Address a && !a.isLoopbackAddress()) {
                        seen.add(a.getHostAddress());
                    }
                }
            }
        } catch (Exception ignored) {
            // fall through to empty / message below
        }
        if (seen.isEmpty()) {
            return "(nenalezena žádná aktivní ne-smyčková IPv4 adresa)";
        }
        return String.join("\n", seen);
    }
}
