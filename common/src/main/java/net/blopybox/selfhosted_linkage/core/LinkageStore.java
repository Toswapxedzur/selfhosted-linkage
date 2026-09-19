package net.blopybox.selfhosted_linkage.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

/**
 * Remembers which saved server addresses are "linkage mode". Vanilla servers.dat keeps the address itself
 * ("relay.example.com:25565" = relay address + selector); this side-file only flags it, so removing the mod
 * leaves a normal (if unreachable) server entry instead of corrupting the list.
 */
public final class LinkageStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private final Set<String> flagged = new TreeSet<>();

    public LinkageStore(Path stateDir) {
        this.file = stateDir.resolve("linkage-servers.json");
        try {
            if (Files.isRegularFile(file)) {
                Set<String> s = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), new TypeToken<TreeSet<String>>() {}.getType());
                if (s != null) flagged.addAll(s);
            }
        } catch (IOException | RuntimeException ignored) {
        }
    }

    public static String key(String address) { return address == null ? "" : address.trim().toLowerCase(java.util.Locale.ROOT); }

    public synchronized boolean isLinkage(String address) { return flagged.contains(key(address)); }

    public synchronized void set(String address, boolean linkage) {
        boolean changed = linkage ? flagged.add(key(address)) : flagged.remove(key(address));
        if (!changed) return;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(flagged), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    /** "relay.example.com:25565" → {relay, selector}; the LAST colon splits, so "host:8099:25565" keeps a relay port. */
    public static String[] split(String address) {
        String a = address.trim();
        int i = a.lastIndexOf(':');
        if (i <= 0 || i == a.length() - 1) return new String[]{a, "25565"};
        return new String[]{a.substring(0, i), a.substring(i + 1)};
    }
}
