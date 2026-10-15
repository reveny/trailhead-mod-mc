package com.reveny.trailhead.worldgen.terrain;

import com.reveny.trailhead.noise.SimplexNoise;

import static com.reveny.trailhead.noise.NoiseConfig.SEED_CAVE;

public enum CaveType {
    SURFACE, LUSH, DRIPSTONE, DEEP_DARK, DESERT, ICE;

    private static final double SURFACE_DEPTH = 10.0;

    public static CaveType at(long seed, HeightField.Column col, int x, int y, int z) {
        return at(seed, col, col.height, x, y, z);
    }

    public static CaveType at(long seed, HeightField.Column col, double surface, int x, int y, int z) {
        if (surface - y < SURFACE_DEPTH) return SURFACE;
        if (y < -16 && patch(seed, 1, x, z, 320.0) > 0.42) return DEEP_DARK;
        Province province = col.province;
        boolean cold = province == Province.ICE_SHEET || province == Province.TUNDRA || col.temperature < 0.2;
        if (cold) return y > -20 ? ICE : SURFACE;
        if (province == Province.DESERT || province == Province.CANYONLANDS) return DESERT;
        if (col.rain > 0.55 && col.temperature > 0.4 && patch(seed, 2, x, z, 260.0) > -0.3) return LUSH;
        double dripstone = patch(seed, 3, x, z, 240.0);
        if (col.karst > 0.5 && dripstone > -0.1) return DRIPSTONE;
        if (col.rain < 0.35 && dripstone > 0.25) return DRIPSTONE;
        return SURFACE;
    }

    private static double patch(long seed, int layer, int x, int z, double scale) {
        return SimplexNoise.noise2(seed + SEED_CAVE + layer, x / scale, z / scale);
    }
}
