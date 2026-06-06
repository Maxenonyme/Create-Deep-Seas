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
    public static final ModConfigSpec.DoubleValue SUBMARINE_PROPELLER_POWER_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue PULLEY_MAX_SLIDE_SPEED;
    public static final ModConfigSpec.BooleanValue ENABLE_PERMANENT_WATER_CULLING_TEST;
    public static final ModConfigSpec.BooleanValue ENABLE_DEEPER_OCEANS;
    public static final ModConfigSpec.BooleanValue ENABLE_ABYSS_DIMENSION;
    public static final ModConfigSpec.IntValue DEEPER_OCEANS_DEPTH;
    public static final ModConfigSpec.BooleanValue DISABLE_STARTUP_SCREENS;
    public static ModConfigSpec.BooleanValue WELCOME_SCREEN_SEEN;
    public static ModConfigSpec.ConfigValue<String> IGNORED_UPDATE_VERSION;

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
        SUBMARINE_PROPELLER_POWER_MULTIPLIER = builder
                .comment("Multiplier on Submarine Propeller thrust and airflow output.",
                        "Lower = weaker propulsion, higher = stronger.")
                .defineInRange("submarinePropellerPowerMultiplier", 3.0, 0.1, 50.0);
        PULLEY_MAX_SLIDE_SPEED = builder
                .comment("Maximum sliding speed of a pulley along a steel cable (blocks/s).",
                        "Above this speed the pulley starts overheating.")
                .defineInRange("pulleyMaxSlideSpeed", 24.0, 1.0, 200.0);
        builder.pop();

        builder.push("experimental");
        ENABLE_PERMANENT_WATER_CULLING_TEST = builder
                .comment("Enable the experimental Permanent Water Culling test for submarines and boats")
                .define("enablePermanentWaterCullingTest", false);
        ENABLE_DEEPER_OCEANS = builder
                .comment("Deepen the ocean floor below vanilla.",
                        "Off = vanilla ocean depth. Set the amount with deeperOceansDepth.",
                        "Requires the 'Lithostitched' mod to be installed.")
                .define("enableDeeperOceans", false);
        ENABLE_ABYSS_DIMENSION = builder
                .comment("Enable the Abyss dimension.",
                        "Requires the 'Lithostitched' mod to be installed.")
                .define("enableAbyssDimension", false);
        DEEPER_OCEANS_DEPTH = builder
                .comment("How many blocks deeper to push the ocean floor when enableDeeperOceans is on.",
                        "WARNING: large values generate and render far more terrain below the sea floor",
                        "and can badly hurt world-generation and rendering performance. Raise it carefully.")
                .defineInRange("deeperOceansDepth", 10, 1, 256);
        builder.pop();

        builder.push("client");
        DISABLE_STARTUP_SCREENS = builder
                .comment("Disable all Deep Seas startup UI screens (Welcome screen and Update notifications).",
                        "Highly recommended to set this to TRUE if you are creating a modpack to avoid annoying your players.")
                .define("disableStartupScreens", false);
        if (!net.neoforged.fml.loading.FMLEnvironment.production) {
            WELCOME_SCREEN_SEEN = builder
                    .comment("Internal: set to true once the Deep Seas welcome screen has been acknowledged.",
                            "Set back to false to show the welcome screen again on the next main menu.")
                    .define("welcomeScreenSeen", false);
            IGNORED_UPDATE_VERSION = builder
                    .comment("Internal: stores the version string of the last update notification dismissed by the user.",
                            "If the online version matches this, the update screen will not be shown.")
                    .define("ignoredUpdateVersion", "");
        } else {
            WELCOME_SCREEN_SEEN = null;
            IGNORED_UPDATE_VERSION = null;
        }
        builder.pop();

        SPEC = builder.build();
    }
}
