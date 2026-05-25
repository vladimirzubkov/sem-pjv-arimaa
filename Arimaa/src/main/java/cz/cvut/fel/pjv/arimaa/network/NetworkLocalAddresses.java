package cz.cvut.fel.pjv.arimaa.network;

import java.net.Inet4Address;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/** Local IPv4 addresses useful for telling a peer how to connect. */
public final class NetworkLocalAddresses {

    private NetworkLocalAddresses() {}

    /**
     * Human-readable list of this machine’s non-loopback IPv4 addresses (host dialog “your IP” block).
     * LAN {@code 192.168.x.x} / {@code 10.x.x.x} addresses are listed first — clients should try those before
     * virtual adapters (Hyper-V, VMware, Tailscale, …).
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
        List<String> lan = new ArrayList<>();
        List<String> other = new ArrayList<>();
        for (String ip : seen) {
            if (isTypicalLanIpv4(ip)) {
                lan.add(ip);
            } else {
                other.add(ip);
            }
        }
        StringBuilder sb = new StringBuilder();
        if (!lan.isEmpty()) {
            sb.append("Doporučené (LAN — zadejte u klienta jednu z těchto):\n");
            for (String ip : lan) {
                sb.append(ip).append('\n');
            }
        }
        if (!other.isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append("Ostatní adaptéry (Hyper-V, VPN, … — jen když LAN nefunguje):\n");
            for (String ip : other) {
                sb.append(ip).append('\n');
            }
        }
        return sb.toString().stripTrailing();
    }

    private static boolean isTypicalLanIpv4(String ip) {
        return ip.startsWith("192.168.") || ip.startsWith("10.");
    }
}
