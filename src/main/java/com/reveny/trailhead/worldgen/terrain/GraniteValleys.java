package com.reveny.trailhead.worldgen.terrain;

import com.reveny.trailhead.noise.NoiseHelper;
import com.reveny.trailhead.noise.SimplexNoise;

import static com.reveny.trailhead.noise.NoiseConfig.*;

final class GraniteValleys {
    private static final double GRADIENT_STEP = 8.0;
    private static final double GRADIENT_FLOOR = 1.0e-4;
    private static final double MAX_DISTANCE = 3000.0;
    private static final double TALUS_REACH = 30.0;
    private static final double BEYOND_RIM_SLOPE = 2.5;

    private final long seed;

    GraniteValleys(long seed) {
        this.seed = seed;
    }

    double shape(double x, double z, double relief, double inland, double weight, HeightField.Column col) {
        double floor = 12.0 + 14.0 * inland + 6.0 * SimplexNoise.noise2(seed + SEED_GRANITE + 1, x / 1500.0, z / 1500.0);
        double wallHeight = NoiseHelper.lerp(TROUGH_WALL_MIN, TROUGH_WALL_MAX, unit(SEED_GRANITE + 2, x, z, 700.0));
        double rim = floor + wallHeight;
        double halfWidth = TROUGH_FLOOR_HALF_WIDTH * (0.8 + 0.5 * unit(SEED_GRANITE + 3, x, z, 900.0));
        double wallEnd = halfWidth + TROUGH_WALL_WIDTH;

        double distance = centrelineDistance(SEED_GRANITE, x, z, TROUGH_SCALE, 300.0);
        double rimLevel = rim + 20.0 * NoiseHelper.smoothstep(wallEnd, wallEnd + 300.0, distance)
                + 10.0 * unit(SEED_GRANITE + 4, x, z, 260.0);
        double upland = Math.max(relief + inland * domes(x, z), NoiseHelper.lerp(relief, rimLevel, inland));
        double mainTrough = floor + wallHeight * wallProfile(distance, halfWidth, wallEnd)
                + BEYOND_RIM_SLOPE * Math.max(0.0, distance - wallEnd);
        double carved = Math.min(upland, mainTrough);
        carved = Math.min(carved, hangingTrough(x, z, floor, wallHeight, distance - wallEnd));

        col.trough = weight * (1.0 - NoiseHelper.smoothstep(halfWidth - 10.0, halfWidth + 8.0, distance));
        double talus = NoiseHelper.smoothstep(halfWidth - TALUS_REACH, halfWidth + 2.0, distance)
                * (1.0 - NoiseHelper.smoothstep(halfWidth + 2.0, halfWidth + 10.0, distance));
        col.talus = Math.max(col.talus, weight * talus);
        return NoiseHelper.lerp(relief, carved, weight);
    }

    private static double wallProfile(double distance, double halfWidth, double wallEnd) {
        return 0.85 * NoiseHelper.smoothstep(halfWidth, wallEnd, distance)
                + 0.15 * NoiseHelper.smoothstep(halfWidth - TALUS_REACH, wallEnd, distance);
    }

    private double hangingTrough(double x, double z, double floor, double wallHeight, double beyondMainWall) {
        double distance = centrelineDistance(SEED_GRANITE + 10, x, z, HANGING_TROUGH_SCALE, 120.0);
        double halfWidth = HANGING_TROUGH_HALF_WIDTH;
        double wallEnd = halfWidth + HANGING_TROUGH_WALL_WIDTH;
        double hangingFloor = floor + wallHeight * (0.45 + 0.2 * unit(SEED_GRANITE + 11, x, z, 800.0))
                + 0.05 * Math.max(0.0, beyondMainWall);
        double sides = (wallHeight * 0.75) * NoiseHelper.smoothstep(halfWidth, wallEnd, distance);
        return hangingFloor + sides + BEYOND_RIM_SLOPE * Math.max(0.0, distance - wallEnd);
    }

    private double domes(double x, double z) {
        double lift = 0;
        long cellX = (long) Math.floor(x / DOME_CELL), cellZ = (long) Math.floor(z / DOME_CELL);
        for (long cx = cellX - 1; cx <= cellX + 1; cx++) {
            for (long cz = cellZ - 1; cz <= cellZ + 1; cz++) {
                if (NoiseHelper.hash01(seed + SEED_GRANITE + 20, cx, cz) > DOME_CHANCE) continue;
                double size = NoiseHelper.hash01(seed + SEED_GRANITE + 23, cx, cz);
                double domeX = (cx + 0.2 + 0.6 * NoiseHelper.hash01(seed + SEED_GRANITE + 21, cx, cz)) * DOME_CELL;
                double domeZ = (cz + 0.2 + 0.6 * NoiseHelper.hash01(seed + SEED_GRANITE + 22, cx, cz)) * DOME_CELL;
                double radius = 110.0 + 90.0 * size;
                double height = 50.0 + 60.0 * size;
                double r = Math.hypot(x - domeX, z - domeZ) / radius;
                if (r >= 1.0) continue;
                lift = Math.max(lift, height * Math.pow(1.0 - r * r, 0.6));
            }
        }
        return lift;
    }

    private double centrelineDistance(long salt, double x, double z, double scale, double warp) {
        double c = centreline(salt, x, z, scale, warp);
        double gx = (centreline(salt, x + GRADIENT_STEP, z, scale, warp) - centreline(salt, x - GRADIENT_STEP, z, scale, warp)) / (2 * GRADIENT_STEP);
        double gz = (centreline(salt, x, z + GRADIENT_STEP, scale, warp) - centreline(salt, x, z - GRADIENT_STEP, scale, warp)) / (2 * GRADIENT_STEP);
        double gradient = Math.max(Math.sqrt(gx * gx + gz * gz), GRADIENT_FLOOR);
        return Math.min(MAX_DISTANCE, Math.abs(c) / gradient);
    }

    private double centreline(long salt, double x, double z, double scale, double warp) {
        double warpScale = scale * 0.7;
        double wx = x + warp * SimplexNoise.noise2(seed + salt + 5, x / warpScale, z / warpScale);
        double wz = z + warp * SimplexNoise.noise2(seed + salt + 6, x / warpScale, z / warpScale);
        return NoiseHelper.fbm2(seed + salt, wx, wz, scale, 2);
    }

    private double unit(long salt, double x, double z, double scale) {
        return SimplexNoise.noise2(seed + salt, x / scale, z / scale) * 0.5 + 0.5;
    }
}
