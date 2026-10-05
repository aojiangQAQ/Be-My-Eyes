package dev.duosight;

import net.minecraftforge.common.ForgeConfigSpec;

public final class DuoConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue SWAP_SECONDS;
    public static final ForgeConfigSpec.IntValue INVITE_SECONDS;

    static {
        var builder = new ForgeConfigSpec.Builder();
        SWAP_SECONDS = builder.comment("Seconds between automatic role swaps.")
                .defineInRange("swapSeconds", 120, 15, 3600);
        INVITE_SECONDS = builder.comment("Lifetime of a pairing invitation.")
                .defineInRange("inviteSeconds", 60, 10, 300);
        SPEC = builder.build();
    }

    private DuoConfig() {}
}
