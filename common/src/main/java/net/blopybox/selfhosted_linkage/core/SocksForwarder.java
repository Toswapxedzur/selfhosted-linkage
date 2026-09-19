package net.blopybox.selfhosted_linkage.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;

/** 127.0.0.1:localPort → (SOCKS5 on 127.0.0.1:socksPort) → peer:port. Minecraft's Netty client cannot use SOCKS itself. */
final class SocksForwarder implements AutoCloseable {
    private final ServerSocket server;
    private final Proxy proxy;
    private final InetSocketAddress peer;
    private volatile boolean open = true;

    private static InetAddress ipv4Loopback() {
        try { return InetAddress.getByName("127.0.0.1"); }
        catch (UnknownHostException e) { return InetAddress.getLoopbackAddress(); }
    }

    SocksForwarder(int socksPort, String peerHost, int peerPort) throws IOException {
        // MUST be IPv4 127.0.0.1: Minecraft dials 127.0.0.1, and getLoopbackAddress() can be ::1 in the game JVM.
        this.server = new ServerSocket(0, 16, ipv4Loopback());
        this.proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(ipv4Loopback(), socksPort));
        this.peer = InetSocketAddress.createUnresolved(peerHost, peerPort);
        Thread t = new Thread(this::acceptLoop, "Linkage-forwarder");
        t.setDaemon(true);
        t.start();
    }

    int localPort() { return server.getLocalPort(); }

    private void acceptLoop() {
        while (open) {
            try {
                Socket in = server.accept();
                Thread t = new Thread(() -> bridge(in), "Linkage-forwarder-conn");
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                if (open) continue;
            }
        }
    }

    private void bridge(Socket in) {
        Socket out = connectWithRetry();
        if (out == null) { try { in.close(); } catch (IOException ignored) {} return; }
        try (in; Socket o = out) {
            in.setTcpNoDelay(true);
            o.setTcpNoDelay(true);
            Thread up = new Thread(() -> pump(in, o), "Linkage-forwarder-up");
            up.setDaemon(true);
            up.start();
            pump(o, in);
            up.join(2000);
        } catch (IOException | InterruptedException ignored) {
        }
    }

    /** The peer link may still be converging on a first connection: retry the SOCKS5 dial up to ~25 s. */
    private Socket connectWithRetry() {
        long deadline = System.currentTimeMillis() + 25000;
        while (open && System.currentTimeMillis() < deadline) {
            Socket out = new Socket(proxy);
            try {
                out.connect(new InetSocketAddress(peer.getHostString(), peer.getPort()), 4000);
                return out;
            } catch (IOException e) {
                try { out.close(); } catch (IOException ignored) {}
                try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return null; }
            }
        }
        return null;
    }

    /** Copies until EOF, then half-closes the far side instead of killing the whole connection. */
    private static void pump(Socket from, Socket to) {
        byte[] buf = new byte[16384];
        try {
            InputStream i = from.getInputStream();
            OutputStream o = to.getOutputStream();
            int n;
            while ((n = i.read(buf)) >= 0) { o.write(buf, 0, n); o.flush(); }
            to.shutdownOutput();
        } catch (IOException ignored) {
        }
    }

    @Override
    public void close() {
        open = false;
        try { server.close(); } catch (IOException ignored) {}
    }
}
