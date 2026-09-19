package net.blopybox.selfhosted_linkage.mixin;

import net.blopybox.selfhosted_linkage.client.LinkageClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V", at = @At("TAIL"))
    private void linkage$disconnected(Screen screen, boolean keepResourcePacks, CallbackInfo ci) {
        LinkageClient.onDisconnect();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void linkage$tick(CallbackInfo ci) {
        net.blopybox.selfhosted_linkage.client.dev.SelfTest.tick();
    }
}
