package net.blopybox.selfhosted_linkage.mixin;

import net.blopybox.selfhosted_linkage.client.LinkageUi;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DirectJoinServerScreen.class)
public abstract class DirectJoinServerScreenMixin extends Screen {
    @Shadow private EditBox ipEdit;
    @Shadow private Button selectButton;
    @Shadow private void updateSelectButtonStatus() {}

    @Unique private LinkageUi linkage$ui;

    protected DirectJoinServerScreenMixin(Component title) { super(title); }

    @Inject(method = "init", at = @At("TAIL"))
    private void linkage$init(CallbackInfo ci) {
        linkage$ui = new LinkageUi(font, ipEdit, 100, ipEdit.getValue(), w -> this.addRenderableWidget(w), () -> this.updateSelectButtonStatus());
        updateSelectButtonStatus();
    }

    @Inject(method = "onSelect", at = @At("HEAD"))
    private void linkage$onSelect(CallbackInfo ci) {
        if (linkage$ui != null) linkage$ui.commit();
    }

    @Inject(method = "updateSelectButtonStatus", at = @At("TAIL"))
    private void linkage$status(CallbackInfo ci) {
        if (linkage$ui != null && linkage$ui.on()) selectButton.active = linkage$ui.valid();
    }
}
