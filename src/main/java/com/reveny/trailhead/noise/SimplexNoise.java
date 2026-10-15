package com.reveny.trailhead.noise;

public final class SimplexNoise {
    private static final long PRIME_X = 0x5205402B9270C86FL;
    private static final long PRIME_Y = 0x598CD327003817B5L;
    private static final long PRIME_Z = 0x5BCC226E9FA0BACBL;
    private static final long HASH_MULTIPLIER = 0x53A3F72DEEC546F5L;

    private static final double F2 = 0.5 * (Math.sqrt(3.0) - 1.0);
    private static final double G2 = (3.0 - Math.sqrt(3.0)) / 6.0;
    private static final double F3 = 1.0 / 3.0;
    private static final double G3 = 1.0 / 6.0;

    private static final int GRADIENT_COUNT_2D = 32;
    private static final double[] GRADIENTS_2D = new double[GRADIENT_COUNT_2D * 2];
    private static final double[] GRADIENTS_3D = {
            1, 1, 0, -1, 1, 0, 1, -1, 0, -1, -1, 0,
            1, 0, 1, -1, 0, 1, 1, 0, -1, -1, 0, -1,
            0, 1, 1, 0, -1, 1, 0, 1, -1, 0, -1, -1,
            1, 1, 0, -1, 1, 0, 0, -1, 1, 0, -1, -1,
    };

    private static final double NORMALIZE_2D = 99.2;
    private static final double NORMALIZE_3D = 32.0;

    static {
        for (int i = 0; i < GRADIENT_COUNT_2D; i++) {
            double angle = (i + 0.5) * 2.0 * Math.PI / GRADIENT_COUNT_2D;
            GRADIENTS_2D[i * 2] = Math.cos(angle);
            GRADIENTS_2D[i * 2 + 1] = Math.sin(angle);
        }
    }

    private SimplexNoise() {}

    public static float noise2(long seed, double x, double y) {
        double skew = (x + y) * F2;
        long i = (long) Math.floor(x + skew);
        long j = (long) Math.floor(y + skew);
        double unskew = (i + j) * G2;
        double x0 = x - (i - unskew), y0 = y - (j - unskew);
        int i1 = x0 > y0 ? 1 : 0, j1 = 1 - i1;
        double x1 = x0 - i1 + G2, y1 = y0 - j1 + G2;
        double x2 = x0 - 1 + 2 * G2, y2 = y0 - 1 + 2 * G2;

        double n = corner2(seed, i, j, x0, y0)
                + corner2(seed, i + i1, j + j1, x1, y1)
                + corner2(seed, i + 1, j + 1, x2, y2);
        return (float) (n * NORMALIZE_2D);
    }

    public static float noise3(long seed, double x, double y, double z) {
        double skew = (x + y + z) * F3;
        long i = (long) Math.floor(x + skew), j = (long) Math.floor(y + skew), k = (long) Math.floor(z + skew);
        double unskew = (i + j + k) * G3;
        double x0 = x - (i - unskew), y0 = y - (j - unskew), z0 = z - (k - unskew);
        int i1, j1, k1, i2, j2, k2;
        if (x0 >= y0) {
            if (y0 >= z0) { i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 1; k2 = 0; }
            else if (x0 >= z0) { i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 0; k2 = 1; }
            else { i1 = 0; j1 = 0; k1 = 1; i2 = 1; j2 = 0; k2 = 1; }
        } else {
            if (y0 < z0) { i1 = 0; j1 = 0; k1 = 1; i2 = 0; j2 = 1; k2 = 1; }
            else if (x0 < z0) { i1 = 0; j1 = 1; k1 = 0; i2 = 0; j2 = 1; k2 = 1; }
            else { i1 = 0; j1 = 1; k1 = 0; i2 = 1; j2 = 1; k2 = 0; }
        }
        double x1 = x0 - i1 + G3, y1 = y0 - j1 + G3, z1 = z0 - k1 + G3;
        double x2 = x0 - i2 + 2 * G3, y2 = y0 - j2 + 2 * G3, z2 = z0 - k2 + 2 * G3;
        double x3 = x0 - 1 + 3 * G3, y3 = y0 - 1 + 3 * G3, z3 = z0 - 1 + 3 * G3;

        double n = corner3(seed, i, j, k, x0, y0, z0)
                + corner3(seed, i + i1, j + j1, k + k1, x1, y1, z1)
                + corner3(seed, i + i2, j + j2, k + k2, x2, y2, z2)
                + corner3(seed, i + 1, j + 1, k + 1, x3, y3, z3);
        return (float) (n * NORMALIZE_3D);
    }

    private static double corner2(long seed, long i, long j, double x, double y) {
        double falloff = 0.5 - x * x - y * y;
        if (falloff <= 0) return 0;
        falloff *= falloff;
        return falloff * falloff * gradient2(seed, i, j, x, y);
    }

    private static double corner3(long seed, long i, long j, long k, double x, double y, double z) {
        double falloff = 0.6 - x * x - y * y - z * z;
        if (falloff <= 0) return 0;
        falloff *= falloff;
        return falloff * falloff * gradient3(seed, i, j, k, x, y, z);
    }

    private static long hash(long seed, long a, long b, long c) {
        long h = seed ^ (a * PRIME_X) ^ (b * PRIME_Y) ^ (c * PRIME_Z);
        h *= HASH_MULTIPLIER;
        h ^= h >>> 29;
        h *= HASH_MULTIPLIER;
        return h ^ (h >>> 32);
    }

    private static double gradient2(long seed, long i, long j, double x, double y) {
        int g = (int) ((hash(seed, i, j, 0) >>> 3) & (GRADIENT_COUNT_2D - 1)) * 2;
        return GRADIENTS_2D[g] * x + GRADIENTS_2D[g + 1] * y;
    }

    private static double gradient3(long seed, long i, long j, long k, double x, double y, double z) {
        int g = (int) ((hash(seed, i, j, k) >>> 3) & 15) * 3;
        return GRADIENTS_3D[g] * x + GRADIENTS_3D[g + 1] * y + GRADIENTS_3D[g + 2] * z;
    }
}
