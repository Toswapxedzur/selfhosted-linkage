package net.blopybox.selfhosted_linkage.client;

import net.blopybox.selfhosted_linkage.Linkage;
import net.blopybox.selfhosted_linkage.core.LinkageSession;
import net.blopybox.selfhosted_linkage.core.LinkageStore;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;

/** Client-side singletons: where state lives, which saved servers are linkage-mode, and the re-entry guard. */
public final class LinkageClient {
    /** true while WE call ConnectScreen.startConnecting with the localhost address, so the mixin lets it through. */
    public static boolean bypass;
    private static LinkageStore store;

    private LinkageClient() {}

    public static Path stateDir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(Linkage.MOD_ID);
    }

    public static synchronized LinkageStore store() {
        if (store == null) store = new LinkageStore(stateDir());
        return store;
    }

    /** Leaving a world (or a failed attempt) ends the link; a fresh, not-yet-joined session is left alone. */
    public static void onDisconnect() {
        LinkageSession s = LinkageSession.active();
        if (s != null && s.joined()) LinkageSession.closeActive();
    }

    public static void onServerListShown() {
        LinkageSession s = LinkageSession.active();
        if (s != null && !LinkageConnectingScreen.busy) LinkageSession.closeActive();
    }
}
