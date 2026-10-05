package dev.duosight.mixin;

import dev.duosight.client.ContainerView;
import dev.duosight.client.DuoClient;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AnvilScreen.class, remap = false)
public abstract class AnvilScreenMixin implements ContainerView {
    @Shadow private EditBox name;

    @Inject(method = "onNameChanged", at = @At("HEAD"), cancellable = true)
    private void duosight$mirrorName(String value, CallbackInfo info) {
        if (DuoClient.active() && (!DuoClient.isBody() || DuoClient.restoringScreen())) {
            info.cancel();
        }
    }

    @Override
    public CompoundTag duosight$capture() {
        CompoundTag data = new CompoundTag();
        data.putString("name", name.getValue());
        data.putBoolean("focused", name.isFocused());
        data.putInt("caret", name.getCursorPosition());
        data.putInt("highlight", ((EditBoxAccess) name).duosight$highlight());
        data.putInt("display", ((EditBoxAccess) name).duosight$display());
        return data;
    }

    @Override
    public void duosight$apply(CompoundTag data) {
        if (!name.getValue().equals(data.getString("name"))) {
            name.setValue(data.getString("name"));
        }
        name.setEditable(((AnvilScreen) (Object) this).getMenu().getSlot(0).hasItem());
        name.setFocused(data.getBoolean("focused"));
        name.setCursorPosition(Math.max(0, Math.min(data.getInt("caret"), name.getValue().length())));
        name.setHighlightPos(Math.max(0, Math.min(data.getInt("highlight"), name.getValue().length())));
        ((EditBoxAccess) name).duosight$display(Math.max(0, Math.min(data.getInt("display"), name.getValue().length())));
    }
}
