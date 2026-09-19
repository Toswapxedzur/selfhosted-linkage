package net.blopybox.selfhosted_linkage.mixin;

import net.blopybox.selfhosted_linkage.client.LinkageUi;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.EditServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EditServerScreen.class)
public abstract class EditServerScreenMixin extends Screen {
    @Shadow private EditBox ipEdit;
    @Shadow private EditBox nameEdit;
    @Shadow private Button addButton;
    @Shadow @Final private ServerData serverData;
    @Shadow private void updateAddButtonStatus() {}

    @Unique private LinkageUi linkage$ui;

    protected EditServerScreenMixin(Component title) { super(title); }

    @Inject(method = "init", at = @At("TAIL"))
    private void linkage$init(CallbackInfo ci) {
        linkage$ui = new LinkageUi(font, ipEdit, 94, serverData.ip, w -> this.addRenderableWidget(w), () -> this.updateAddButtonStatus());
        updateAddButtonStatus();
    }

    @Inject(method = "onAdd", at = @At("HEAD"))
    private void linkage$onAdd(CallbackInfo ci) {
        if (linkage$ui != null) linkage$ui.commit();
    }

    @Inject(method = "updateAddButtonStatus", at = @At("TAIL"))
    private void linkage$status(CallbackInfo ci) {
        if (linkage$ui != null && linkage$ui.on()) addButton.active = !nameEdit.getValue().isEmpty() && linkage$ui.valid();
    }
}
