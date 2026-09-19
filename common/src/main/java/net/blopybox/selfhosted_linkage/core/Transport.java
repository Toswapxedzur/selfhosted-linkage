package net.blopybox.selfhosted_linkage.core;

import java.io.IOException;
import java.nio.file.Path;

/** One way of reaching a server. {@link #open} blocks until a localhost port is ready for Minecraft to join. */
public interface Transport extends AutoCloseable {
    /** @return the local port on 127.0.0.1 that tunnels to the target server */
    int open(LinkageTarget target, Path stateDir) throws IOException;

    @Override
    void close();

    /** Some helpers announce "listening" just before they bind: wait until the port really accepts. */
    static void awaitListening(int port, long millis) throws IOException {
        long deadline = System.currentTimeMillis() + millis;
        while (true) {
            try (java.net.Socket probe = new java.net.Socket()) {
                probe.connect(new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), port), 500);
                return;
            } catch (IOException e) {
                if (System.currentTimeMillis() > deadline) throw new IOException("helper never opened 127.0.0.1:" + port, e);
                try { Thread.sleep(100); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw new IOException(ie); }
            }
        }
    }

    static int freePort() throws IOException {
        try (java.net.ServerSocket s = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            return s.getLocalPort();
        }
    }
}
