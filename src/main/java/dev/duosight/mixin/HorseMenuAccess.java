package dev.duosight.mixin;

import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.inventory.HorseInventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = HorseInventoryMenu.class, remap = false)
public interface HorseMenuAccess {
    @Accessor("horse")
    AbstractHorse duosight$horse();
}
