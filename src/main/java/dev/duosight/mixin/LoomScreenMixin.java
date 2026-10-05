package dev.duosight.mixin;

import dev.duosight.client.ContainerView;
import net.minecraft.client.gui.screens.inventory.LoomScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = LoomScreen.class, remap = false)
public abstract class LoomScreenMixin implements ContainerView {
    @Shadow private float scrollOffs;
    @Shadow private int startRow;
    @Shadow private void containerChanged() {}

    @Override
    public CompoundTag duosight$capture() {
        CompoundTag data = new CompoundTag();
        data.putFloat("scroll", scrollOffs);
        data.putInt("start", startRow);
        return data;
    }

    @Override
    public void duosight$apply(CompoundTag data) {
        containerChanged();
        scrollOffs = Mth.clamp(data.getFloat("scroll"), 0, 1);
        int max = Math.max(0, (((LoomScreen) (Object) this).getMenu().getSelectablePatterns().size() + 3) / 4 - 4);
        startRow = Mth.clamp(data.getInt("start"), 0, max);
    }
}
