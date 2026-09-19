package net.blopybox.selfhosted_linkage.mixin;

import net.blopybox.selfhosted_linkage.client.dev.SelfTest;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dev harness trigger only; inert unless -Dlinkage.selftest is set. */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {
    @Inject(method = "init", at = @At("TAIL"))
    private void linkage$selftest(CallbackInfo ci) {
        SelfTest.arm();
    }
}
