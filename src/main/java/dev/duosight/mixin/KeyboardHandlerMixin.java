package dev.duosight.mixin;

import dev.duosight.client.DuoClient;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = KeyboardHandler.class, remap = false)
public abstract class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void duosight$key(long window, int key, int scan, int action, int modifiers, CallbackInfo info) {
        if (DuoClient.key(key, scan, action, modifiers)) {
            info.cancel();
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void duosight$char(long window, int code, int modifiers, CallbackInfo info) {
        if (DuoClient.character(code, modifiers)) {
            info.cancel();
        }
    }
}
