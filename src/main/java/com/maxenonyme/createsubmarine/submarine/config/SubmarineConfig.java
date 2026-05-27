package com.maxenonyme.createsubmarine.submarine.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class SubmarineConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue DISABLE_IMPLOSION;
    public static final ModConfigSpec.IntValue GLOBAL_MAX_DEPTH_CAP;
    public static final ModConfigSpec.DoubleValue IMPLOSION_CHANCE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue MAX_DEPTH_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue BALLAST_FORCE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue BALLAST_TRANSFER_RATE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue WATER_THRUSTER_POWER_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue ENABLE_PERMANENT_WATER_CULLING_TEST;
    public static final ModConfigSpec.BooleanValue ENABLE_ABYSS_GENERATION;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("gameplay");
        DISABLE_IMPLOSION = builder
                .comment("Disable all hull implosion damage from pressure.")
                .define("disableImplosion", false);
        builder.pop();

        builder.push("hullStrength");
        GLOBAL_MAX_DEPTH_CAP = builder
                .comment("Cap applied to maxWaterDepth of all non-create_submarine blocks.",
                        "Per-block values are stored in config/submarine_hull.json.")
                .defineInRange("globalMaxDepthCap", 100, 1, 10000);
        MAX_DEPTH_MULTIPLIER = builder
                .comment("Multiplier on every block's effective maxWaterDepth at runtime.",
                        "Lower = more fragile hulls, higher = tougher hulls.")
                .defineInRange("maxDepthMultiplier", 1.0, 0.01, 100.0);
        IMPLOSION_CHANCE_MULTIPLIER = builder
                .comment("Multiplier on every block's implosionChance at runtime.",
                        "Lower = slower cracking, higher = faster cracking.")
                .defineInRange("implosionChanceMultiplier", 1.0, 0.0, 10.0);
        builder.pop();

        builder.push("propulsion");
        BALLAST_FORCE_MULTIPLIER = builder
                .comment("Multiplier on the vertical force ballast tanks apply.",
                        "Lower = slower dive/ascend, higher = snappier.")
                .defineInRange("ballastForceMultiplier", 1.0, 0.1, 10.0);
        BALLAST_TRANSFER_RATE_MULTIPLIER = builder
                .comment("Multiplier on the ballast vent fill/drain transfer rate.",
                        "Lower = slower filling/emptying, higher = faster.")
                .defineInRange("ballastTransferRateMultiplier", 2.0, 0.1, 20.0);
        WATER_THRUSTER_POWER_MULTIPLIER = builder
                .comment("Multiplier on water thruster thrust output.",
                        "Lower = weaker propulsion, higher = stronger.")
                .defineInRange("waterThrusterPowerMultiplier", 6.0, 0.1, 50.0);
        builder.pop();

        builder.push("experimental");
        ENABLE_PERMANENT_WATER_CULLING_TEST = builder
                .comment("Enable the experimental Permanent Water Culling test for submarines and boats")
                .define("enablePermanentWaterCullingTest", false);
        ENABLE_ABYSS_GENERATION = builder
                .comment("Generate Abyss biome pockets and the deeper ocean trenches that go with them.",
                        "Off = vanilla ocean depth, no Abyss biome.")
                .define("enableAbyssGeneration", false);
        builder.pop();

        SPEC = builder.build();
    }
}
