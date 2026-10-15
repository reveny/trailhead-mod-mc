package com.reveny.trailhead.worldgen.terrain;

import com.reveny.trailhead.noise.NoiseHelper;
import com.reveny.trailhead.noise.SimplexNoise;

import java.util.concurrent.ConcurrentHashMap;

import static com.reveny.trailhead.noise.NoiseConfig.*;

final class Stratovolcanoes {
    interface Probe {
        double cascadiaWeight(double x, double z);

        double relief(double x, double z);

        double coastDistance(double x, double z);
    }

    record Site(double x, double z, double radius, double height, double baseRelief) {}

    private static final Site NONE = new Site(0, 0, 0, 0, 0);
    private static final double MIN_CENTRE_WEIGHT = 0.9;
    private static final double MIN_FLANK_WEIGHT = 0.75;
    private static final double MIN_BASE_RELIEF = 15.0;
    private static final double MAX_BASE_RELIEF = 110.0;
    private static final int RING_SAMPLES = 8;
    private static final double CANDIDATE_SPACING = 400.0;
    private static final double CELL_MARGIN = 0.15;

    private final long seed;
    private final Probe probe;
    private final ConcurrentHashMap<Long, Site> sites = new ConcurrentHashMap<>();

    Stratovolcanoes(long seed, Probe probe) {
        this.seed = seed;
        this.probe = probe;
    }

    Site siteNear(double x, double z) {
        long cellX = (long) Math.floor(x / STRATOVOLCANO_CELL), cellZ = (long) Math.floor(z / STRATOVOLCANO_CELL);
        long key = (cellX << 32) ^ (cellZ & 0xFFFFFFFFL);
        Site site = sites.get(key);
        if (site == null) {
            site = chooseSite(cellX, cellZ);
            sites.putIfAbsent(key, site);
        }
        return site == NONE ? null : site;
    }

    private Site chooseSite(long cellX, long cellZ) {
        if (NoiseHelper.hash01(seed + SEED_STRATOVOLCANO, cellX, cellZ) > STRATOVOLCANO_CHANCE) return NONE;
        double radius = 520.0 + 150.0 * NoiseHelper.hash01(seed + SEED_STRATOVOLCANO + 3, cellX, cellZ);
        double start = CELL_MARGIN * STRATOVOLCANO_CELL;
        double end = (1.0 - CELL_MARGIN) * STRATOVOLCANO_CELL;
        Site best = NONE;
        double bestScore = -1;
        for (double u = start; u <= end; u += CANDIDATE_SPACING) {
            for (double v = start; v <= end; v += CANDIDATE_SPACING) {
                double siteX = cellX * STRATOVOLCANO_CELL + u, siteZ = cellZ * STRATOVOLCANO_CELL + v;
                if (probe.cascadiaWeight(siteX, siteZ) < MIN_CENTRE_WEIGHT) continue;
                double flank = weakestFlank(siteX, siteZ, radius);
                if (flank < MIN_FLANK_WEIGHT || flank <= bestScore) continue;
                Site site = validSite(siteX, siteZ, radius);
                if (site == null) continue;
                best = site;
                bestScore = flank;
            }
        }
        return best;
    }

    private Site validSite(double siteX, double siteZ, double radius) {
        if (probe.coastDistance(siteX, siteZ) < 0.8 * radius) return null;
        double baseRelief = probe.relief(siteX, siteZ);
        if (baseRelief < MIN_BASE_RELIEF || baseRelief > MAX_BASE_RELIEF) return null;
        double height = NoiseHelper.clamp(STRATOVOLCANO_SUMMIT - baseRelief, 90.0, 160.0);
        return new Site(siteX, siteZ, radius, height, baseRelief);
    }

    private double weakestFlank(double x, double z, double radius) {
        double weakest = 1.0;
        for (int k = 0; k < RING_SAMPLES; k++) {
            double angle = k * 2 * Math.PI / RING_SAMPLES;
            weakest = Math.min(weakest, probe.cascadiaWeight(x + Math.cos(angle) * radius, z + Math.sin(angle) * radius));
        }
        return weakest;
    }

    double shape(Site site, double x, double z, double relief, HeightField.Column col) {
        if (site == null) return relief;
        double dx = x - site.x(), dz = z - site.z();
        double r = Math.hypot(dx, dz) + 20.0 * SimplexNoise.noise2(seed + SEED_STRATOVOLCANO + 4, x / 160.0, z / 160.0);
        if (r >= site.radius()) return relief;

        double t = 1.0 - Math.max(0.0, r) / site.radius();
        double cone = t * t;
        col.cone = cone;
        double smoothed = NoiseHelper.lerp(relief, site.baseRelief(), NoiseHelper.smoothstep(0.0, 0.35, cone));
        return smoothed + site.height() * cone + flankRidges(dx, dz, r, site.radius(), cone) - crater(r);
    }

    private double flankRidges(double dx, double dz, double r, double radius, double cone) {
        double angle = Math.atan2(dz, dx);
        double n = SimplexNoise.noise3(seed + SEED_STRATOVOLCANO + 5, Math.cos(angle) * 3.0, Math.sin(angle) * 3.0, r / 220.0);
        double ridge = 1.0 - Math.abs(n);
        double amplitude = 16.0 * NoiseHelper.smoothstep(0.08, 0.35, r / radius) * Math.sqrt(cone);
        return amplitude * (ridge - 0.6);
    }

    private static double crater(double r) {
        return 14.0 * (1.0 - NoiseHelper.smoothstep(18.0, 50.0, r));
    }
}
