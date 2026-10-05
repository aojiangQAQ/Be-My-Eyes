package dev.duosight.mixin;

import dev.duosight.client.ContainerView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BeaconScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = BeaconScreen.class, remap = false)
public abstract class BeaconScreenMixin implements ContainerView {
    @Shadow Holder<MobEffect> primary;
    @Shadow Holder<MobEffect> secondary;
    @Shadow abstract void updateButtons();

    @Override
    public CompoundTag duosight$capture() {
        CompoundTag data = new CompoundTag();
        data.putString("primary", key(primary));
        data.putString("secondary", key(secondary));
        return data;
    }

    private static String key(Holder<MobEffect> effect) {
        return effect == null ? "" : effect.unwrapKey().map(key -> key.location().toString()).orElse("");
    }

    private static Holder<MobEffect> effect(String key) {
        ResourceLocation id = ResourceLocation.tryParse(key);
        return id == null ? null : Minecraft.getInstance().level.registryAccess()
                .registryOrThrow(Registries.MOB_EFFECT).getHolder(id).orElse(null);
    }

    @Override
    public void duosight$apply(CompoundTag data) {
        primary = effect(data.getString("primary"));
        secondary = effect(data.getString("secondary"));
        updateButtons();
    }
}
