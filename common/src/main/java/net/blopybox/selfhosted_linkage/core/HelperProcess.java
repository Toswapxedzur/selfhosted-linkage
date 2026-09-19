package net.blopybox.selfhosted_linkage.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** A helper subprocess whose merged output is scanned for a "ready" line. Secrets in its output are never logged. */
final class HelperProcess {
    private final Process process;
    private final CountDownLatch ready = new CountDownLatch(1);
    private volatile String lastLine = "";

    HelperProcess(List<String> command, Map<String, String> env, Predicate<String> readyWhen, Consumer<String> log) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true);
        pb.environment().putAll(env);
        this.process = pb.start();
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (!line.toLowerCase().contains("secret")) { lastLine = line; log.accept(line); }
                    if (readyWhen.test(line)) ready.countDown();
                }
            } catch (IOException ignored) {
            } finally {
                ready.countDown();
            }
        }, "Linkage-helper-output");
        t.setDaemon(true);
        t.start();
    }

    /** @return true if the ready line appeared and the process is still alive */
    boolean awaitReady(long seconds) throws IOException {
        try {
            boolean signalled = ready.await(seconds, TimeUnit.SECONDS);
            if (!process.isAlive()) throw new IOException("helper exited early: " + lastLine);
            return signalled;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while starting helper", e);
        }
    }

    boolean isAlive() { return process.isAlive(); }

    void stop() {
        process.destroy();
        try {
            if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }
}
