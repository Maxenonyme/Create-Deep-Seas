package com.maxenonyme.highseas.wind;

public final class WindConfig {
    private WindConfig() {
    }

    public static final int ZONE_SIZE = 32;

    public static final double BASE_WIND = 1.0;

    public static final double NOISE_SCALE = 0.012;
    public static final double TIME_SCALE = 0.015;
    public static final double GUST_AMPLITUDE = 0.25;
    public static final double PERTURB_MAX = Math.toRadians(12.0);

    public static final double PREVAILING_TURN_SCALE = 0.0002;

    public static final double THERMAL_MAX = Math.toRadians(20.0);

    public static final double RAIN_BOOST = 0.8;
    public static final double THUNDER_BOOST = 1.6;
    public static final double THUNDER_CHAOS = 3.0;

    public static final double FRICTION_OCEAN = 1.0;
    public static final double FRICTION_BEACH = 0.85;
    public static final double FRICTION_PLAINS = 0.7;
    public static final double FRICTION_FOREST = 0.5;
    public static final double FRICTION_MOUNTAIN = 0.35;
    public static final double FRICTION_DEFAULT = 0.7;

    public static final double ALT_MULT_SEA = 0.6;
    public static final double ALT_MULT_HIGH = 1.4;
    public static final double ALT_FULL_HEIGHT = 96.0;

    public static final int OCCLUSION_UPWIND = 12;
    public static final double OCCLUSION_MAX_REDUCTION = 0.5;

    public static final double SAIL_FORCE_K = 2.0;
    public static final double SAIL_BASE_THRUST = 1.6;
    public static final double SAIL_WIND_GAIN = 3.2;
    public static final double SAIL_MALUS = 0.4;
    public static final double SAIL_MAX_SPEED = 7.0;
    public static final double SAIL_SPEED_RAMP = 1.5;
    public static final int SAIL_SCAN_INTERVAL = 20;
    public static final int SAIL_SCAN_MAX_VOLUME = 500_000;
}
