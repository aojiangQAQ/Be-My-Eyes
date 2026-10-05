package dev.duosight.mixin;

import dev.duosight.client.DuoClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GameRenderer.class, remap = false)
public abstract class GameRendererMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/toasts/ToastComponent;render(Lnet/minecraft/client/gui/GuiGraphics;)V"))
    private void duosight$chatToasts(ToastComponent toasts, GuiGraphics graphics) {
        if (!DuoClient.driver() || !(Minecraft.getInstance().screen instanceof ChatScreen)) {
            toasts.render(graphics);
        }
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/client/ForgeHooksClient;drawScreen(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
    private void duosight$beforeScreen(DeltaTracker delta, boolean renderLevel, CallbackInfo info) {
        DuoClient.beforeScreen();
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;flush()V", shift = At.Shift.AFTER))
    private void duosight$frame(DeltaTracker delta, boolean renderLevel, CallbackInfo info) {
        DuoClient.finishFrame();
    }
}
