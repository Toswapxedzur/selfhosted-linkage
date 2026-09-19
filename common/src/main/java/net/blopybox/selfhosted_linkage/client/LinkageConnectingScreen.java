package net.blopybox.selfhosted_linkage.client;

import net.blopybox.selfhosted_linkage.Linkage;
import net.blopybox.selfhosted_linkage.core.LinkageSession;
import net.blopybox.selfhosted_linkage.core.LinkageStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Shown for the second or two it takes to look the server up and open the link; then vanilla connecting takes over. */
public final class LinkageConnectingScreen extends Screen {
    static volatile boolean busy;
    private final Screen parent;
    private final ServerData data;
    private final boolean quickPlay;
    private final TransferState transfer;
    private volatile Component status = Component.literal("Looking up the server on the relay…");
    private volatile boolean cancelled;
    private boolean started;

    public LinkageConnectingScreen(Screen parent, ServerData data, boolean quickPlay, TransferState transfer) {
        super(Component.literal("Selfhosted Linkage"));
        this.parent = parent;
        this.data = data;
        this.quickPlay = quickPlay;
        this.transfer = transfer;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> cancel())
                .bounds(width / 2 - 100, height / 4 + 120 + 12, 200, 20).build());
        if (started) return;
        started = true;
        busy = true;
        String[] parts = LinkageStore.split(data.ip);
        Thread t = new Thread(() -> open(parts[0], parts[1]), "Linkage-connect");
        t.setDaemon(true);
        t.start();
    }

    private void open(String relay, String selector) {
        Minecraft mc = Minecraft.getInstance();
        try {
            LinkageSession session = LinkageSession.start(relay, selector, LinkageClient.stateDir(), line -> {
                Linkage.LOGGER.info(line);
                if (line.startsWith("linkage ready")) status = Component.literal("Link open — joining…");
            });
            if (cancelled) { LinkageSession.closeActive(); return; }
            mc.execute(() -> {
                busy = false;
                if (cancelled) { LinkageSession.closeActive(); return; }
                LinkageClient.bypass = true;
                try {
                    ConnectScreen.startConnecting(parent, mc, new ServerAddress("127.0.0.1", session.localPort()), data, quickPlay, transfer);
                } finally {
                    LinkageClient.bypass = false;
                }
            });
        } catch (Exception e) {
            Linkage.LOGGER.warn("linkage failed: {}", e.toString());
            mc.execute(() -> {
                busy = false;
                if (!cancelled) mc.setScreen(new DisconnectedScreen(parent, Component.literal("Selfhosted Linkage"),
                        Component.literal(e.getMessage() == null ? e.toString() : e.getMessage())));
            });
        }
    }

    private void cancel() {
        cancelled = true;
        busy = false;
        LinkageSession.closeActive();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, height / 2 - 50, 0xFFFFFF);
        g.drawCenteredString(font, status, width / 2, height / 2 - 30, 0xA0A0A0);
    }
}
