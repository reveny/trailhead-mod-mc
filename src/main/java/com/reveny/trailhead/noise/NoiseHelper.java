package com.reveny.trailhead.noise;

public final class NoiseHelper {
    private static final long OCTAVE_SEED_STEP = 31L;

    private NoiseHelper() {}

    public static float fbm2(long seed, double x, double z, double scale, int octaves) {
        float sum = 0;
        float amplitude = 1.0f;
        float frequency = 1.0f;
        float totalAmplitude = 0;
        for (int octave = 0; octave < octaves; octave++) {
            long octaveSeed = seed + octave * OCTAVE_SEED_STEP;
            sum += amplitude * SimplexNoise.noise2(octaveSeed, x * frequency / scale, z * frequency / scale);
            totalAmplitude += amplitude;
            amplitude *= 0.5f;
            frequency *= 2.0f;
        }
        return sum / totalAmplitude;
    }

    public static double erodedFbm2(long seed, double x, double z, double scale, int octaves, double erosion) {
        final double epsilon = 0.01;
        double sum = 0;
        double amplitude = 1.0;
        double totalAmplitude = 0;
        double slopeX = 0;
        double slopeZ = 0;
        double px = x / scale;
        double pz = z / scale;
        for (int octave = 0; octave < octaves; octave++) {
            long octaveSeed = seed + octave * OCTAVE_SEED_STEP;
            double n = SimplexNoise.noise2(octaveSeed, px, pz);
            double dx = (SimplexNoise.noise2(octaveSeed, px + epsilon, pz) - n) / epsilon;
            double dz = (SimplexNoise.noise2(octaveSeed, px, pz + epsilon) - n) / epsilon;
            slopeX += dx * amplitude;
            slopeZ += dz * amplitude;
            sum += amplitude * n / (1.0 + erosion * (slopeX * slopeX + slopeZ * slopeZ));
            totalAmplitude += amplitude;
            amplitude *= 0.5;
            double rotatedX = 1.6 * px - 1.2 * pz;
            double rotatedZ = 1.2 * px + 1.6 * pz;
            px = rotatedX;
            pz = rotatedZ;
        }
        return sum / totalAmplitude * 1.6;
    }

    public static float softRidged2(long seed, double x, double z, double scale, int octaves, double roundness) {
        float sum = 0;
        float amplitude = 1.0f;
        float frequency = 1.0f;
        float totalAmplitude = 0;
        double roundnessSquared = roundness * roundness;
        for (int octave = 0; octave < octaves; octave++) {
            long octaveSeed = seed + octave * OCTAVE_SEED_STEP;
            double n = SimplexNoise.noise2(octaveSeed, x * frequency / scale, z * frequency / scale);
            sum += amplitude * (float) (1.0 - Math.sqrt(n * n + roundnessSquared) + roundness);
            totalAmplitude += amplitude;
            amplitude *= 0.45f;
            frequency *= 2.0f;
        }
        return sum / totalAmplitude;
    }

    public static double hash01(long seed, long a, long b) {
        long h = seed * 0x9E3779B97F4A7C15L + a * 0xC2B2AE3D27D4EB4FL + b * 0x165667B19E3779F9L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) * 0x1.0p-53;
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    public static double smoothstep(double edge0, double edge1, double x) {
        double t = clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }
}
