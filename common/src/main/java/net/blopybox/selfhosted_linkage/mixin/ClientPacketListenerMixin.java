package net.blopybox.selfhosted_linkage.mixin;

import net.blopybox.selfhosted_linkage.core.LinkageSession;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void linkage$joined(ClientboundLoginPacket packet, CallbackInfo ci) {
        LinkageSession s = LinkageSession.active();
        if (s != null) s.markJoined();
    }
}
