package net.blopybox.selfhosted_linkage.client;

import net.blopybox.selfhosted_linkage.core.LinkageStore;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * The whole user-facing surface of the mod: one checkbox. Ticked, the vanilla address box becomes "relay address"
 * and a small "port" box appears beside it. Nothing else is ever shown.
 */
public final class LinkageUi {
    private static final int PORT_W = 54, GAP = 6;
    public final Checkbox box;
    public final EditBox port;
    private final EditBox ip;
    private final int fullWidth;

    public LinkageUi(Font font, EditBox ipEdit, int labelY, String currentAddress, Consumer<AbstractWidget> adder, Runnable onChange) {
        this.ip = ipEdit;
        this.fullWidth = ipEdit.getWidth();
        boolean linked = LinkageClient.store().isLinkage(currentAddress);

        port = new EditBox(font, ipEdit.getX() + fullWidth - PORT_W, ipEdit.getY(), PORT_W, ipEdit.getHeight(), Component.literal("Port"));
        port.setMaxLength(5);
        port.setFilter(s -> s.matches("\\d{0,5}"));
        port.setHint(Component.literal("port"));
        if (linked) {
            String[] parts = LinkageStore.split(currentAddress);
            ipEdit.setValue(parts[0]);
            port.setValue(parts[1]);
        }
        port.setResponder(s -> onChange.run());

        box = Checkbox.builder(Component.literal("Selfhosted Linkage"), font)
                .pos(0, labelY - 5)
                .selected(linked)
                .tooltip(Tooltip.create(Component.literal("Join through a self-hosted relay: enter the relay address and the server's port number.")))
                .onValueChange((c, on) -> { apply(on); onChange.run(); })
                .build();
        box.setX(ipEdit.getX() + fullWidth - box.getWidth());

        adder.accept(box);
        adder.accept(port);
        apply(linked);
    }

    private void apply(boolean on) {
        port.visible = on;
        ip.setWidth(on ? fullWidth - PORT_W - GAP : fullWidth);
        ip.setHint(on ? Component.literal("relay address") : Component.empty());
    }

    public boolean on() { return box.selected(); }

    public boolean valid() {
        if (ip.getValue().isBlank() || port.getValue().isEmpty()) return false;
        int p = Integer.parseInt(port.getValue());
        return p >= 1 && p <= 65535;
    }

    /** What is stored as the server's address: "relay:port". */
    public String compose() { return ip.getValue().trim() + ":" + port.getValue(); }

    /** Call at the HEAD of the vanilla confirm handler: stores the composite address and the linkage flag. */
    public void commit() {
        if (on()) {
            String composite = compose();
            ip.setValue(composite);
            LinkageClient.store().set(composite, true);
        } else {
            LinkageClient.store().set(ip.getValue(), false);
        }
    }
}
