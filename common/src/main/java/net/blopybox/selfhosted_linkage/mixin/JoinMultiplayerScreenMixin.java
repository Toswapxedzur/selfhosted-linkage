package net.blopybox.selfhosted_linkage.mixin;

import net.blopybox.selfhosted_linkage.client.LinkageClient;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Back on the server list with a link still open = the attempt failed or was abandoned: close it. */
@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin {
    @Inject(method = "init", at = @At("TAIL"))
    private void linkage$cleanup(CallbackInfo ci) {
        LinkageClient.onServerListShown();
    }
}
