package dev.duosight.mixin;

import dev.duosight.client.DuoClient;
import dev.duosight.net.Packets;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Camera.class, remap = false)
public abstract class CameraMixin {
    @Shadow protected abstract void setPosition(Vec3 position);

    @Inject(method = "setup", at = @At("TAIL"))
    private void duosight$pose(BlockGetter level, Entity entity, boolean detached, boolean reverse,
                               float partialTick, CallbackInfo info) {
        Packets.Pose pose = DuoClient.cameraPose();
        if (pose != null) {
            setPosition(new Vec3(pose.x(), pose.y(), pose.z()));
            ((Camera) (Object) this).setRotation(pose.yaw(), pose.pitch(), 0);
        }
    }
}
