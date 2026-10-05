package dev.duosight.mixin;

import dev.duosight.client.DuoClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Minecraft.class, remap = false)
public abstract class MinecraftMixin {
    @Inject(method = "setScreen", at = @At("HEAD"))
    private void duosight$screen(Screen next, CallbackInfo info) {
        DuoClient.screenChange(next);
    }

    @Inject(method = "isWindowActive", at = @At("HEAD"), cancellable = true)
    private void duosight$focus(CallbackInfoReturnable<Boolean> info) {
        if (DuoClient.remoteBody()) {
            info.setReturnValue(true);
        }
    }
}
