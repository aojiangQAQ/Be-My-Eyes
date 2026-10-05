package dev.duosight;

import dev.duosight.net.Packets;
import dev.duosight.server.Sessions;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(DuoSight.ID)
public final class DuoSight {
    public static final String ID = "duosight";

    public DuoSight(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.SERVER, DuoConfig.SPEC);
        Packets.register();
        MinecraftForge.EVENT_BUS.register(new Sessions());
    }
}
