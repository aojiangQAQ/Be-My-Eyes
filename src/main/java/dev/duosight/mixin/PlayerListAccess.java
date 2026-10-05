package dev.duosight.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = PlayerList.class, remap = false)
public interface PlayerListAccess {
    @Invoker("save")
    void duosight$save(ServerPlayer player);
}
