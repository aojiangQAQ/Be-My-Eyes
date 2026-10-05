package dev.duosight.mixin;

import dev.duosight.client.ContainerView;
import net.minecraft.client.gui.screens.inventory.StonecutterScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = StonecutterScreen.class, remap = false)
public abstract class StonecutterScreenMixin implements ContainerView {
    @Shadow private float scrollOffs;
    @Shadow private int startIndex;
    @Shadow private void containerChanged() {}

    @Override
    public CompoundTag duosight$capture() {
        CompoundTag data = new CompoundTag();
        data.putFloat("scroll", scrollOffs);
        data.putInt("start", startIndex);
        return data;
    }

    @Override
    public void duosight$apply(CompoundTag data) {
        containerChanged();
        scrollOffs = Mth.clamp(data.getFloat("scroll"), 0, 1);
        int max = Math.max(0, (((StonecutterScreen) (Object) this).getMenu().getNumRecipes() + 3) / 4 - 3) * 4;
        startIndex = Mth.clamp(data.getInt("start") / 4 * 4, 0, max);
    }
}
