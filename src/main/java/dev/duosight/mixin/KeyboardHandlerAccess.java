package dev.duosight.mixin;

import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = KeyboardHandler.class, remap = false)
public interface KeyboardHandlerAccess {
    @Invoker("keyPress")
    void duosight$key(long window, int key, int scan, int action, int modifiers);

    @Invoker("charTyped")
    void duosight$character(long window, int code, int modifiers);
}
