package com.reveny.trailhead.worldgen.terrain;

import com.reveny.trailhead.noise.NoiseHelper;
import com.reveny.trailhead.noise.SimplexNoise;

import java.util.concurrent.ConcurrentHashMap;

import static com.reveny.trailhead.noise.NoiseConfig.*;

public final class ProvinceMap {
    private static final int NEIGHBOUR_CELLS = 9;
    private static final double NEIGHBOURHOOD_RADIUS = 4500.0;
    private static final int NEIGHBOURHOOD_SAMPLES = 8;

    private final long seed;
    private final HeightField field;
    private final ConcurrentHashMap<Long, Province> cellProvince = new ConcurrentHashMap<>();

    ProvinceMap(long seed, HeightField field) {
        this.seed = seed;
        this.field = field;
    }

    public void profile(double x, double z, Province.Profile out) {
        out.clear();
        double warpScale = PROVINCE_WARP_SCALE;
        double fineScale = warpScale * 0.25;
        double wx = x + PROVINCE_WARP * SimplexNoise.noise2(seed + SEED_PROVINCE, x / warpScale, z / warpScale)
                + PROVINCE_WARP * 0.3 * SimplexNoise.noise2(seed + SEED_PROVINCE + 2, x / fineScale, z / fineScale);
        double wz = z + PROVINCE_WARP * SimplexNoise.noise2(seed + SEED_PROVINCE + 1, x / warpScale, z / warpScale)
                + PROVINCE_WARP * 0.3 * SimplexNoise.noise2(seed + SEED_PROVINCE + 3, x / fineScale, z / fineScale);

        long centerX = (long) Math.floor(wx / PROVINCE_SIZE), centerZ = (long) Math.floor(wz / PROVINCE_SIZE);
        double[] distances = new double[NEIGHBOUR_CELLS];
        Province[] provinces = new Province[NEIGHBOUR_CELLS];
        double nearest = Double.MAX_VALUE;
        int count = 0;
        for (long cx = centerX - 1; cx <= centerX + 1; cx++) {
            for (long cz = centerZ - 1; cz <= centerZ + 1; cz++) {
                double siteX = (cx + 0.15 + 0.7 * NoiseHelper.hash01(seed + SEED_PROVINCE, cx, cz)) * PROVINCE_SIZE;
                double siteZ = (cz + 0.15 + 0.7 * NoiseHelper.hash01(seed + SEED_PROVINCE + 1, cx, cz)) * PROVINCE_SIZE;
                double distance = Math.hypot(wx - siteX, wz - siteZ);
                distances[count] = distance;
                provinces[count] = province(cx, cz, siteX, siteZ);
                nearest = Math.min(nearest, distance);
                count++;
            }
        }

        double total = 0;
        double[] weights = new double[NEIGHBOUR_CELLS];
        for (int i = 0; i < count; i++) {
            weights[i] = Math.exp(-(distances[i] - nearest) / PROVINCE_BLEND);
            total += weights[i];
        }
        for (int i = 0; i < count; i++) {
            out.add(provinces[i], weights[i] / total);
        }
        out.dominant = ditheredDominant(x, z, out.weights);
    }

    private Province ditheredDominant(double x, double z, double[] weights) {
        double pick = NoiseHelper.hash01(seed + SEED_PROVINCE + 7, (long) Math.floor(x), (long) Math.floor(z)) * 0.35
                + 0.65 * (SimplexNoise.noise2(seed + SEED_PROVINCE + 8, x / 24.0, z / 24.0) * 0.5 + 0.5);
        double sharpTotal = 0;
        double[] sharp = new double[weights.length];
        for (int i = 0; i < sharp.length; i++) {
            sharp[i] = Math.pow(weights[i], 4);
            sharpTotal += sharp[i];
        }
        double cumulative = 0;
        for (int i = 0; i < sharp.length; i++) {
            cumulative += sharp[i] / sharpTotal;
            if (pick <= cumulative) return Province.byOrdinal(i);
        }
        return Province.TEMPERATE_HILLS;
    }

    private Province province(long cx, long cz, double siteX, double siteZ) {
        long key = (cx << 32) ^ (cz & 0xFFFFFFFFL);
        Province cached = cellProvince.get(key);
        if (cached != null) return cached;
        Province chosen = chooseProvince(cx, cz, siteX, siteZ);
        cellProvince.put(key, chosen);
        return chosen;
    }

    private Province chooseProvince(long cx, long cz, double siteX, double siteZ) {
        Climate climate = field.climate();
        boolean coastal = field.continent(siteX, siteZ) < PROVINCE_COAST_SIGNAL;
        double temperature = climate.temperature(climate.latitude(siteX, siteZ));
        double rain = climate.precipitation(siteX, siteZ, field);
        Neighbourhood around = neighbourhood(siteX, siteZ, temperature, rain);

        Province[] all = Province.values();
        double[] weights = new double[all.length];
        double totalWeight = 0;
        Province closest = Province.TEMPERATE_HILLS;
        double closestDistance = Double.MAX_VALUE;
        for (Province candidate : all) {
            if (!around.allows(candidate)) continue;
            double distance = candidate.envelopeDistance(temperature, rain);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = candidate;
            }
            double fit = distance <= 0 ? 1.0
                    : distance < PROVINCE_CLIMATE_MARGIN ? 1.0 - distance / PROVINCE_CLIMATE_MARGIN : 0.0;
            weights[candidate.ordinal()] = fit * (coastal ? candidate.weightCoast : candidate.weightInland);
            totalWeight += weights[candidate.ordinal()];
        }
        if (totalWeight <= 0) return closest;

        double remaining = NoiseHelper.hash01(seed + SEED_PROVINCE + 5, cx, cz) * totalWeight;
        for (Province candidate : all) {
            remaining -= weights[candidate.ordinal()];
            if (remaining < 0) return candidate;
        }
        return closest;
    }

    private Neighbourhood neighbourhood(double x, double z, double temperature, double rain) {
        Climate climate = field.climate();
        Neighbourhood n = new Neighbourhood(temperature, rain);
        for (int k = 0; k < NEIGHBOURHOOD_SAMPLES; k++) {
            double angle = k * Math.PI / 4;
            double qx = x + Math.cos(angle) * NEIGHBOURHOOD_RADIUS, qz = z + Math.sin(angle) * NEIGHBOURHOOD_RADIUS;
            n.include(climate.temperature(climate.latitude(qx, qz)), climate.precipitation(qx, qz, field));
        }
        return n;
    }

    private static final class Neighbourhood {
        double minTemperature, maxTemperature, minRain, maxRain;

        Neighbourhood(double temperature, double rain) {
            minTemperature = maxTemperature = temperature;
            minRain = maxRain = rain;
        }

        void include(double temperature, double rain) {
            minTemperature = Math.min(minTemperature, temperature);
            maxTemperature = Math.max(maxTemperature, temperature);
            minRain = Math.min(minRain, rain);
            maxRain = Math.max(maxRain, rain);
        }

        boolean allows(Province province) {
            return switch (province) {
                case DESERT -> maxRain <= 0.32 && minTemperature >= 0.5;
                case JUNGLE -> minRain >= 0.48 && minTemperature >= 0.66;
                case ICE_SHEET -> maxTemperature <= 0.22;
                case TUNDRA, BOREAL -> maxTemperature <= province.tMax + 0.12;
                case FJORDLAND, CASCADIA -> !(maxTemperature > 0.58 && minRain < 0.25);
                case SAVANNA, CANYONLANDS -> minTemperature >= province.tMin - 0.12;
                case STEPPE -> minTemperature >= province.tMin - 0.12 && maxRain <= 0.5;
                default -> true;
            };
        }
    }
}
