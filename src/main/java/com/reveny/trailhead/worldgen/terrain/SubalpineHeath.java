package com.reveny.trailhead.worldgen.terrain;

import com.reveny.trailhead.noise.NoiseHelper;
import com.reveny.trailhead.noise.SimplexNoise;

import static com.reveny.trailhead.noise.NoiseConfig.SNOW_TEMPERATURE;

public final class SubalpineHeath {
    public enum Tint { CRIMSON, EMBER, GOLD }

    public enum Zone { MONTANE, SUBALPINE, HEATH, ALPINE }

    private static final double ALPINE_MARGIN = 0.03;
    private static final double HEATH_MARGIN = 0.085;
    private static final double SUBALPINE_MARGIN = 0.125;
    private static final long SEED_PATCH = 31000L;
    private static final long SEED_TINT = 31100L;

    private SubalpineHeath() {}

    public static double patch(long seed, double x, double z) {
        return NoiseHelper.fbm2(seed + SEED_PATCH, x, z, 260.0, 3) * 0.5 + 0.5;
    }

    public static Zone zone(HeightField.Column col) {
        double margin = Climate.atHeight(col.temperature, col.height) - SNOW_TEMPERATURE;
        if (margin < ALPINE_MARGIN) return Zone.ALPINE;
        if (margin < HEATH_MARGIN) return Zone.HEATH;
        return margin < SUBALPINE_MARGIN ? Zone.SUBALPINE : Zone.MONTANE;
    }

    public static boolean covers(HeightField.Column col, double patch) {
        if (col.massif > 0.4 || col.cone > 0.2) return false;
        return switch (col.province) {
            case CASCADIA -> switch (zone(col)) {
                case HEATH -> patch <= 0.7;
                case SUBALPINE -> patch < 0.45;
                default -> false;
            };
            case ALPINE_VALLEY -> zone(col) == Zone.HEATH && patch < 0.6;
            default -> false;
        };
    }

    public static Tint tint(long seed, double x, double z) {
        double n = SimplexNoise.noise2(seed + SEED_TINT, x / 70.0, z / 70.0)
                + 0.35 * SimplexNoise.noise2(seed + SEED_TINT + 1, x / 25.0, z / 25.0);
        if (n < -0.2) return Tint.CRIMSON;
        return n < 0.25 ? Tint.EMBER : Tint.GOLD;
    }
}
