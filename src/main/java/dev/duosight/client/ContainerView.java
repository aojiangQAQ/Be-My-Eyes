package dev.duosight.client;

import net.minecraft.nbt.CompoundTag;

public interface ContainerView {
    CompoundTag duosight$capture();
    void duosight$apply(CompoundTag data);
}
