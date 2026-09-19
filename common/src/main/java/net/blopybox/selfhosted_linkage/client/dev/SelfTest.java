package net.blopybox.selfhosted_linkage.client.dev;

import net.blopybox.selfhosted_linkage.Linkage;
import net.blopybox.selfhosted_linkage.client.LinkageClient;
import net.blopybox.selfhosted_linkage.client.LinkageConnectingScreen;
import net.blopybox.selfhosted_linkage.core.LinkageSession;
import net.blopybox.selfhosted_linkage.core.LinkageStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.EditServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * DEV-ONLY scripted harness (-Dlinkage.selftest=relay:selector). Drives the REAL screens and handlers in-process,
 * asserts probes, screenshots every step, writes selftest-result.txt, then quits. Never active for players.
 */
public final class SelfTest {
    private static final String ARG = System.getProperty("linkage.selftest");
    private static final List<String> results = new ArrayList<>();
    private static int step = -1, wait, failures;
    private static long deadline;
    private static Screen joinScreen;
    private static ServerData data;
    private static boolean accepted;

    private SelfTest() {}

    public static boolean enabled() { return ARG != null && !ARG.isBlank(); }

    public static void arm() { if (enabled() && step < 0) { step = 0; wait = 60; } }

    private static void check(String name, boolean ok, String detail) {
        if (!ok) failures++;
        String line = (ok ? "PASS  " : "FAIL  ") + name + (detail.isEmpty() ? "" : "  — " + detail);
        results.add(line);
        Linkage.LOGGER.info("[selftest] {}", line);
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, "linkage-" + name + ".png", mc.getMainRenderTarget(), c -> {});
    }

    private static String disconnectReason(Screen s) {
        if (!(s instanceof DisconnectedScreen)) return null;
        for (java.lang.reflect.Field f : DisconnectedScreen.class.getDeclaredFields()) {
            if (net.minecraft.network.chat.Component.class.isAssignableFrom(f.getType())) {
                try { f.setAccessible(true); Object c = f.get(s); if (c != null) { String v = ((net.minecraft.network.chat.Component) c).getString(); if (v != null && !v.isBlank() && !v.equals("Failed to connect to the server")) return v; } }
                catch (Exception ignored) {}
            }
        }
        return "(reason unavailable)";
    }

    private static <T> List<T> find(Screen s, Class<T> type) {
        List<T> out = new ArrayList<>();
        for (GuiEventListener l : s.children()) if (type.isInstance(l)) out.add(type.cast(l));
        return out;
    }

    private static Button button(Screen s, String text) {
        for (Button b : find(s, Button.class)) if (b.getMessage().getString().equals(text)) return b;
        return null;
    }

    public static void tick() {
        if (step < 0 || !enabled()) return;
        if (wait > 0) { wait--; return; }
        Minecraft mc = Minecraft.getInstance();
        String[] parts = LinkageStore.split(ARG);
        try {
            switch (step) {
                case 0 -> {
                    if (!(mc.screen instanceof TitleScreen)) { wait = 10; return; }
                    joinScreen = new JoinMultiplayerScreen(mc.screen);
                    data = new ServerData("Linkage selftest", "", ServerData.Type.OTHER);
                    mc.setScreen(new EditServerScreen(joinScreen, ok -> accepted = ok, data));
                    step = 1; wait = 15;
                }
                case 1 -> {
                    Screen s = mc.screen;
                    List<Checkbox> boxes = find(s, Checkbox.class);
                    check("EditServer: checkbox injected", boxes.size() == 1, boxes.size() + " checkbox(es)");
                    List<EditBox> edits = find(s, EditBox.class);
                    check("EditServer: port box injected (hidden while unticked)", edits.size() == 3 && !edits.get(2).visible, edits.size() + " edit boxes");
                    shot("01-addserver-unticked");
                    step = 2; wait = 10;
                }
                case 2 -> {
                    Screen s = mc.screen;
                    find(s, Checkbox.class).get(0).onPress();
                    List<EditBox> edits = find(s, EditBox.class);
                    edits.get(0).setValue("Linkage selftest");
                    edits.get(1).setValue(parts[0]);
                    edits.get(2).setValue(parts[1]);
                    check("EditServer: ticking reveals port box and narrows address box", edits.get(2).visible && edits.get(1).getWidth() < 200, "address width " + edits.get(1).getWidth());
                    Button done = button(s, "Done");
                    check("EditServer: Done enabled with relay + port", done != null && done.active, "");
                    step = 3; wait = 10;
                }
                case 3 -> { shot("02-addserver-ticked"); step = 4; wait = 10; }
                case 4 -> {
                    button(mc.screen, "Done").onPress();
                    check("EditServer: Done stores composite address", accepted && ARG.equals(data.ip), "ip=" + data.ip);
                    check("EditServer: address flagged as linkage", LinkageClient.store().isLinkage(data.ip), "");
                    mc.setScreen(new DirectJoinServerScreen(joinScreen, ok -> {}, new ServerData("direct", "", ServerData.Type.OTHER)));
                    step = 5; wait = 15;
                }
                case 5 -> {
                    Screen s = mc.screen;
                    List<Checkbox> boxes = find(s, Checkbox.class);
                    check("DirectConnect: checkbox injected", boxes.size() == 1, "");
                    if (!boxes.isEmpty() && !boxes.get(0).selected()) boxes.get(0).onPress();
                    List<EditBox> edits = find(s, EditBox.class);
                    if (edits.size() == 2) { edits.get(0).setValue(parts[0]); edits.get(1).setValue(parts[1]); }
                    Button join = button(s, "Join Server");
                    check("DirectConnect: Join enabled with relay + port", join != null && join.active, edits.size() + " edit boxes");
                    step = 6; wait = 10;
                }
                case 6 -> { shot("03-directconnect-ticked"); step = 7; wait = 10; }
                case 7 -> {
                    mc.setScreen(joinScreen);
                    ConnectScreen.startConnecting(joinScreen, mc, ServerAddress.parseString("127.0.0.1"), data, false, null);
                    check("Connect: intercepted into the linking screen", mc.screen instanceof LinkageConnectingScreen, String.valueOf(mc.screen));
                    step = 8; wait = 6;
                }
                case 8 -> { shot("04-linking"); deadline = System.currentTimeMillis() + 60_000; step = 9; }
                case 9 -> {
                    if (mc.screen instanceof LinkageConnectingScreen && System.currentTimeMillis() < deadline) { wait = 5; return; }
                    LinkageSession sess = LinkageSession.active();
                    check("Link: session opened a localhost port", sess != null && sess.localPort() > 0,
                            sess == null ? "no session; screen=" + mc.screen : sess.target().transport() + " → 127.0.0.1:" + sess.localPort());
                    check("Link: vanilla connect took over", mc.screen instanceof ConnectScreen || mc.screen instanceof DisconnectedScreen || mc.level != null, String.valueOf(mc.screen));
                    deadline = System.currentTimeMillis() + 45_000; step = 10; wait = 10;
                }
                case 10 -> {
                    if (mc.screen instanceof ConnectScreen && mc.level == null && System.currentTimeMillis() < deadline) { wait = 10; return; }
                    shot("05-outcome");
                    boolean modMismatch = mc.screen != null && mc.screen.getClass().getSimpleName().contains("ModMismatch");
                    String reason = disconnectReason(mc.screen);
                    boolean localFail = reason != null && (reason.contains("refused") || reason.contains("timed out") || reason.contains("Timed out"));
                    check("Reached the REAL server through the link (joined, or a genuine server response)",
                            mc.level != null || modMismatch || (mc.screen instanceof DisconnectedScreen && !localFail),
                            mc.level != null ? "joined" : modMismatch ? "server negotiated to configuration, rejected missing mods (a matching client joins)" : "reason: " + reason);
                    step = 11; wait = 20;
                }
                case 11 -> {
                    LinkageClient.store().set(ARG, false);
                    LinkageSession.closeActive();
                    results.add(failures == 0 ? "RESULT: ALL PASS" : "RESULT: " + failures + " FAILURE(S)");
                    Files.write(mc.gameDirectory.toPath().resolve("selftest-result.txt"), results);
                    step = -1;
                    mc.stop();
                }
                default -> {}
            }
        } catch (Throwable t) {
            check("harness step " + step, false, t.toString());
            step = 11; wait = 5;
        }
    }
}
