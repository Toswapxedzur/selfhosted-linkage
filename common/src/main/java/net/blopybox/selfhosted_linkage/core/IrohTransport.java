package net.blopybox.selfhosted_linkage.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * iroh mode: the bundled {@code linkage-iroh} helper (a dumbpipe fork) dials the server agent by key and
 * exposes a localhost port. When the registry gives a {@code relay_url}, both ends use that self-hosted
 * relay (n0's public relays are unreachable from China); direct hole-punching still happens when possible.
 */
public final class IrohTransport implements Transport {
    private final Consumer<String> log;
    private HelperProcess helper;

    public IrohTransport(Consumer<String> log) { this.log = log; }

    @Override
    public int open(LinkageTarget target, Path stateDir) throws IOException {
        if (target.ticket() == null || target.ticket().isBlank()) throw new IOException("registry entry has no iroh ticket");
        Path bin = HelperBinaries.locate("linkage-iroh", stateDir);
        int port = Transport.freePort();
        List<String> cmd = new ArrayList<>(List.of(bin.toString(), "connect-tcp", "--addr", "127.0.0.1:" + port));
        if (target.relayUrl() != null && !target.relayUrl().isBlank()) {
            cmd.add("--relay-url");
            cmd.add(target.relayUrl());
        }
        cmd.add(target.ticket());
        helper = new HelperProcess(cmd, Map.of("RUST_LOG", "dumbpipe=info"),
                line -> line.contains("tcp listening"), l -> log.accept("[iroh] " + l));
        if (!helper.awaitReady(20)) throw new IOException("iroh helper did not become ready in time");
        Transport.awaitListening(port, 5000);
        return port;
    }

    @Override
    public void close() {
        if (helper != null) helper.stop();
        helper = null;
    }
}
