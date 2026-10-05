package dev.duosight.mixin;

import dev.duosight.client.DuoClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MouseHandler.class, remap = false)
public abstract class MouseHandlerMixin {
    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void duosight$press(long window, int button, int action, int modifiers, CallbackInfo info) {
        if (DuoClient.button(button, action, modifiers)) {
            info.cancel();
        }
    }

    @Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
    private void duosight$move(long window, double x, double y, CallbackInfo info) {
        if (DuoClient.move(x, y)) {
            info.cancel();
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void duosight$scroll(long window, double x, double y, CallbackInfo info) {
        if (DuoClient.scroll(x, y)) {
            info.cancel();
        }
    }
}
