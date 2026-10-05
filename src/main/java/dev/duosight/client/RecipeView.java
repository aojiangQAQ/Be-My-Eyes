package dev.duosight.client;

import net.minecraft.nbt.CompoundTag;

public interface RecipeView {
    boolean duosight$editing();
    CompoundTag duosight$capture();
    void duosight$apply(CompoundTag data);
}
