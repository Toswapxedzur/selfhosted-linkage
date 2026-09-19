package net.blopybox.selfhosted_linkage.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/** One active linkage: resolve relay+selector, start the right transport, hand back the localhost port. */
public final class LinkageSession implements AutoCloseable {
    private static LinkageSession active;

    private final Transport transport;
    private final int localPort;
    private final LinkageTarget target;
    private volatile boolean joined;

    private LinkageSession(Transport transport, int localPort, LinkageTarget target) {
        this.transport = transport;
        this.localPort = localPort;
        this.target = target;
    }

    /**
     * Blocking (seconds). Call off the render thread. Replaces any previous session.
     * Two modes, chosen by the Address field:
     *  - PUBLIC mode: Address is a bare endpoint public key → dial it by key over iroh's public
     *    infrastructure (n0 relays + discovery). No registry, no relay to run.
     *  - SELF-HOSTED mode: Address is a relay hostname → fetch its registry and try the listed
     *    transports in order.
     */
    public static synchronized LinkageSession start(String relay, String selector, Path stateDir, Consumer<String> log) throws IOException {
        closeActive();
        List<LinkageTarget> targets = isPublicKey(relay)
                ? List.of(new LinkageTarget("public", "iroh", relay.trim(), null, null, null, null, parsePort(selector)))
                : RegistryClient.resolveAll(relay, selector);
        IOException last = null;
        for (int i = 0; i < targets.size(); i++) {
            LinkageTarget target = targets.get(i);
            Transport transport = transportFor(target, log);
            if (transport == null) {
                last = new IOException("unknown transport '" + target.transport() + "' in registry");
                continue;
            }
            try {
                int port = transport.open(target, stateDir);
                active = new LinkageSession(transport, port, target);
                log.accept("linkage ready: " + relay + " #" + selector + " (" + target.transport()
                        + (targets.size() > 1 ? ", choice " + (i + 1) + "/" + targets.size() : "")
                        + ") → 127.0.0.1:" + port);
                return active;
            } catch (IOException | RuntimeException e) {
                transport.close();
                last = (e instanceof IOException io) ? io : new IOException(e);
                if (i + 1 < targets.size())
                    log.accept("linkage: " + target.transport() + " failed (" + e.getMessage()
                            + "), trying next transport…");
            }
        }
        throw last != null ? last : new IOException("no usable transport for #" + selector);
    }

    private static Transport transportFor(LinkageTarget target, Consumer<String> log) {
        return target.isIroh() ? new IrohTransport(log)
                : target.isNetbird() ? new NetbirdTransport(log) : null;
    }

    /**
     * Public mode iff the Address is a bare endpoint key, not a relay hostname. A key has no dot
     * or slash (hostnames/URLs do) and is a run of hex/base32 — a 64-hex endpoint id, or ≥40 chars.
     */
    static boolean isPublicKey(String s) {
        if (s == null) return false;
        String t = s.trim();
        if (t.isEmpty() || t.contains(".") || t.contains("/") || t.contains(":")) return false;
        return t.matches("(?i)[0-9a-f]{64}") || t.matches("[a-zA-Z0-9]{40,}");
    }

    private static int parsePort(String selector) {
        try {
            return Integer.parseInt(selector.trim());
        } catch (NumberFormatException e) {
            return 25565;
        }
    }

    public int localPort() { return localPort; }
    public LinkageTarget target() { return target; }
    public void markJoined() { joined = true; }
    public boolean joined() { return joined; }

    public static synchronized LinkageSession active() { return active; }

    public static synchronized void closeActive() {
        if (active != null) {
            active.close();
            active = null;
        }
    }

    @Override
    public void close() { transport.close(); }
}
