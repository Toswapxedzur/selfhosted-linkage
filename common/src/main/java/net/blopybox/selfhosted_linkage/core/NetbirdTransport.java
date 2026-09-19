package net.blopybox.selfhosted_linkage.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * NetBird mode: the stock {@code netbird} client in NETSTACK mode (userspace WireGuard — no TUN, no admin)
 * joins the owner's overlay and offers a local SOCKS5 proxy; a tiny forwarder turns that into a plain localhost port.
 * The enrolment (config.json with the peer's key) is kept per management URL so a friend stays ONE peer across sessions.
 */
public final class NetbirdTransport implements Transport {
    private final Consumer<String> log;
    private HelperProcess helper;
    private SocksForwarder forwarder;

    public NetbirdTransport(Consumer<String> log) { this.log = log; }

    @Override
    public int open(LinkageTarget t, Path stateDir) throws IOException {
        if (t.managementUrl() == null || t.peer() == null) throw new IOException("registry entry is missing management_url / peer");
        Path bin = HelperBinaries.locate("netbird", stateDir);
        Path dir = stateDir.resolve("netbird").resolve(Integer.toHexString(t.managementUrl().hashCode()));
        Files.createDirectories(dir);
        int socks = Transport.freePort();
        int wg = Transport.freePort();

        List<String> cmd = new ArrayList<>(List.of(bin.toString(), "up", "-F",
                "--config", dir.resolve("config.json").toString(),
                "--daemon-addr", "tcp://127.0.0.1:" + Transport.freePort(),
                "--log-file", "console",
                "--management-url", t.managementUrl(),
                "--hostname", "linkage-" + System.getProperty("user.name", "player"),
                "--wireguard-port", Integer.toString(wg),
                "--disable-dns", "--disable-firewall"));
        if (t.setupKey() != null && !t.setupKey().isBlank()) { cmd.add("--setup-key"); cmd.add(t.setupKey()); }

        helper = new HelperProcess(cmd,
                Map.of("NB_USE_NETSTACK_MODE", "true", "NB_SOCKS5_LISTENER_PORT", Integer.toString(socks)),
                line -> line.contains("engine started"), l -> { if (l.contains("ERRO") || l.contains("engine started") || l.contains("ICE to active")) log.accept("[netbird] " + l); });
        if (!helper.awaitReady(45)) throw new IOException("NetBird helper did not connect in time");

        // "engine started" only means an IP was assigned; the peer path (hole-punch or relay) may still be
        // coming up. Wait until the SOCKS5 proxy is listening AND a real connection to the target succeeds,
        // so Minecraft never hits a half-open tunnel on the very first dial.
        try {
            warmPath(socks, t.peer(), t.port(), 25000);
        } catch (IOException e) {
            // A brand-new peer's link can lag; don't fail here — the forwarder retries per connection
            // and Minecraft shows "Connecting" until the tunnel converges.
            log.accept("[netbird] peer link still warming (" + e.getMessage() + "); continuing");
        }

        forwarder = new SocksForwarder(socks, t.peer(), t.port());
        return forwarder.localPort();
    }

    /** Poll the tunnel until a SOCKS5 connection to the target actually completes (peer link is up). */
    private void warmPath(int socks, String peerHost, int peerPort, long millis) throws IOException {
        java.net.Proxy proxy = new java.net.Proxy(java.net.Proxy.Type.SOCKS,
                new java.net.InetSocketAddress(java.net.InetAddress.getByName("127.0.0.1"), socks));
        long deadline = System.currentTimeMillis() + millis;
        IOException last = null;
        while (System.currentTimeMillis() < deadline) {
            if (!helper.isAlive()) throw new IOException("NetBird helper exited while establishing the peer link");
            try (java.net.Socket probe = new java.net.Socket(proxy)) {
                probe.connect(new java.net.InetSocketAddress(peerHost, peerPort), 4000);
                log.accept("[netbird] peer link ready to " + peerHost + ":" + peerPort);
                return;
            } catch (IOException e) {
                last = e;
                try { Thread.sleep(600); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw new IOException(ie); }
            }
        }
        throw new IOException("peer link to " + peerHost + ":" + peerPort + " not ready in time"
                + (last != null ? " (" + last.getMessage() + ")" : ""));
    }

    @Override
    public void close() {
        if (forwarder != null) forwarder.close();
        if (helper != null) helper.stop();
        forwarder = null;
        helper = null;
    }
}
