package dev.duosight.mixin;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(value = AbstractContainerMenu.class, remap = false)
public interface MenuAccess {
    @Accessor("dataSlots")
    List<DataSlot> duosight$data();
}
