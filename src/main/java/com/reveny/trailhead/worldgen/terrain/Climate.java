package com.reveny.trailhead.worldgen.terrain;

import com.reveny.trailhead.noise.NoiseHelper;
import com.reveny.trailhead.noise.SimplexNoise;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import static com.reveny.trailhead.noise.NoiseConfig.*;

public final class Climate {
    public enum Mode {
        LATITUDE, ZONES;

        public static Mode parse(String name) {
            return "zones".equalsIgnoreCase(name) ? ZONES : LATITUDE;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private static final double SPAWN_LATITUDE = 0.52;
    private static final double RAIN_CELL = 512.0;
    private static final double CONTINENTALITY_RADIUS = 3000.0;
    private static final int CONTINENTALITY_SAMPLES = 5;
    private static final double RAIN_SHADOW_STEP = 5000.0;

    private final long seed;
    private final Mode mode;
    private final double zonesOrigin;
    private final ConcurrentHashMap<Long, Float> rainGrid = new ConcurrentHashMap<>();

    Climate(long seed, Mode mode) {
        this.seed = seed;
        this.mode = mode;
        this.zonesOrigin = zonesNoise(0, 0);
    }

    public double latitude(double x, double z) {
        return mode == Mode.LATITUDE ? bandedLatitude(x, z) : zonedLatitude(x, z);
    }

    private double bandedLatitude(double x, double z) {
        double wobble = CLIMATE_WOBBLE * SimplexNoise.noise2(seed + SEED_CLIMATE, x / 40000.0, z / 40000.0)
                + CLIMATE_WOBBLE * 0.12 * SimplexNoise.noise2(seed + SEED_CLIMATE + 1, x / 9000.0, z / 9000.0);
        double u = (-z + wobble) / CLIMATE_POLE_DISTANCE + SPAWN_LATITUDE;
        double wrapped = u - 2.0 * Math.floor(u / 2.0);
        return wrapped <= 1.0 ? wrapped : 2.0 - wrapped;
    }

    private double zonedLatitude(double x, double z) {
        double spawnOffset = atanh(2 * SPAWN_LATITUDE - 1);
        double r = 1.3 * (zonesNoise(x, z) - zonesOrigin) + spawnOffset;
        return 0.5 + 0.5 * Math.tanh(r);
    }

    private static double atanh(double a) {
        return 0.5 * Math.log((1 + a) / (1 - a));
    }

    private double zonesNoise(double x, double z) {
        return 1.3 * SimplexNoise.noise2(seed + SEED_CLIMATE + 2, x / CLIMATE_ZONE_SCALE, z / CLIMATE_ZONE_SCALE)
                + 0.2 * SimplexNoise.noise2(seed + SEED_CLIMATE + 3, x / (CLIMATE_ZONE_SCALE * 0.4), z / (CLIMATE_ZONE_SCALE * 0.4));
    }

    public double temperature(double latitude) {
        return NoiseHelper.clamp(1.0 - latitude, 0.0, 1.0);
    }

    public double precipitation(double x, double z, HeightField field) {
        double gx = x / RAIN_CELL, gz = z / RAIN_CELL;
        long i = (long) Math.floor(gx), j = (long) Math.floor(gz);
        double tx = smooth(gx - i);
        double tz = smooth(gz - j);
        double r00 = rainNode(i, j, field), r10 = rainNode(i + 1, j, field);
        double r01 = rainNode(i, j + 1, field), r11 = rainNode(i + 1, j + 1, field);
        return NoiseHelper.lerp(NoiseHelper.lerp(r00, r10, tx), NoiseHelper.lerp(r01, r11, tx), tz);
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    private double rainNode(long i, long j, HeightField field) {
        long key = (i << 32) ^ (j & 0xFFFFFFFFL);
        Float cached = rainGrid.get(key);
        if (cached != null) return cached;
        double x = i * RAIN_CELL, z = j * RAIN_CELL;
        double continentality = 0;
        for (int k = 0; k < CONTINENTALITY_SAMPLES; k++) {
            double angle = k * Math.PI * 2 / CONTINENTALITY_SAMPLES;
            continentality += field.continentForClimate(x + Math.cos(angle) * CONTINENTALITY_RADIUS,
                    z + Math.sin(angle) * CONTINENTALITY_RADIUS) / (double) CONTINENTALITY_SAMPLES;
        }
        double rain = rainAt(x, z, latitude(x, z), continentality);
        rainGrid.put(key, (float) rain);
        return rain;
    }

    private double rainAt(double x, double z, double latitude, double continentality) {
        double circulation = circulationRain(latitude);
        double regional = 0.22 * NoiseHelper.fbm2(seed + SEED_CLIMATE + 4, x, z, CLIMATE_RAIN_SCALE, 3);
        double interior = 0.18 * NoiseHelper.smoothstep(0.04, 0.35, continentality);
        double shadow = 0.28 * upwindMountains(x, z);
        return NoiseHelper.clamp(circulation + regional - interior - shadow, 0.0, 1.0);
    }

    private static double circulationRain(double latitude) {
        if (latitude < 0.18) return NoiseHelper.lerp(1.0, 0.72, latitude / 0.18);
        if (latitude < 0.36) return NoiseHelper.lerp(0.72, 0.08, NoiseHelper.smoothstep(0.18, 0.36, latitude));
        if (latitude < 0.58) return NoiseHelper.lerp(0.08, 0.7, NoiseHelper.smoothstep(0.36, 0.58, latitude));
        return NoiseHelper.lerp(0.7, 0.3, NoiseHelper.smoothstep(0.58, 1.0, latitude));
    }

    private double upwindMountains(double x, double z) {
        double strongest = 0;
        for (int step = 1; step <= 4; step++) {
            strongest = Math.max(strongest, mountainBelt(x - step * RAIN_SHADOW_STEP, z) * (1.0 - 0.12 * step));
        }
        return strongest;
    }

    public double mountainBelt(double x, double z) {
        double n = SimplexNoise.noise2(seed + SEED_CLIMATE + 6, x / MOUNTAIN_BELT_SCALE, z / (MOUNTAIN_BELT_SCALE * 2.5));
        return NoiseHelper.smoothstep(0.15, 0.55, n);
    }

    public static double atHeight(double seaLevelTemperature, double y) {
        return seaLevelTemperature - Math.max(0.0, y - SEA_LEVEL) * CLIMATE_LAPSE;
    }
}
