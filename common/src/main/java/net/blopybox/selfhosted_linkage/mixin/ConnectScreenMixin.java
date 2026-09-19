package net.blopybox.selfhosted_linkage.mixin;

import net.blopybox.selfhosted_linkage.client.LinkageClient;
import net.blopybox.selfhosted_linkage.client.LinkageConnectingScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A linkage-mode server never gets dialled directly: open the link first, then let vanilla connect to localhost. */
@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin {
    @Inject(method = "startConnecting", at = @At("HEAD"), cancellable = true)
    private static void linkage$intercept(Screen parent, Minecraft minecraft, ServerAddress address, ServerData data,
                                          boolean quickPlay, TransferState transfer, CallbackInfo ci) {
        if (LinkageClient.bypass || data == null || !LinkageClient.store().isLinkage(data.ip)) return;
        ci.cancel();
        minecraft.setScreen(new LinkageConnectingScreen(parent, data, quickPlay, transfer));
    }
}
