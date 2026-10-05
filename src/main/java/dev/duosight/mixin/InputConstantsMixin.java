package dev.duosight.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.duosight.client.DuoClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = InputConstants.class, remap = false)
public abstract class InputConstantsMixin {
    @Inject(method = "isKeyDown", at = @At("HEAD"), cancellable = true)
    private static void duosight$keyDown(long window, int key, CallbackInfoReturnable<Boolean> info) {
        if (DuoClient.personalMenu()) {
            return;
        }
        if (DuoClient.remoteBody()) {
            info.setReturnValue(DuoClient.remoteKey(key));
        } else if (DuoClient.active() && !DuoClient.driver()) {
            info.setReturnValue(false);
        }
    }
}
