package com.reveny.trailhead.worldgen.terrain;

import java.util.Arrays;

public enum Province {
    TEMPERATE_HILLS(hills(14, 30, 18), mountains(0.00, 150, 1.35, 0.20), plateau(-0.12, 18, 0.5),
            massif(0.0, 0), relief(0.0, 0.0, 0.00, 2.5), placement(0.5, 1.0), climate(0.42, 0.72, 0.40, 0.85)),
    SEA_CLIFFS(hills(10, 20, 15), mountains(-0.30, 80, 1.30, 0.20), plateau(0.12, 12, 0.80),
            massif(0.0, 0), relief(0.3, 0.0, 0.35, 2.0), placement(1.3, 0.0), climate(0.30, 0.65, 0.30, 1.00)),
    FJORDLAND(hills(15, 35, 25), mountains(0.25, 185, 1.20, 0.20), plateau(-0.10, 18, 0.5),
            massif(0.0, 0), relief(0.2, 1.0, 0.25, 3.0), placement(1.0, 0.0), climate(0.22, 0.50, 0.45, 1.00)),
    DOLOMITES(hills(12, 22, 25), mountains(-0.35, 60, 1.30, 0.20), plateau(-0.20, 18, 0.5),
            massif(1.0, 100), relief(0.2, 0.5, 0.00, 2.0), placement(0.0, 1.0), climate(0.40, 0.62, 0.35, 0.75)),
    ALPINE_VALLEY(hills(15, 32, 25), mountains(0.30, 195, 1.25, 0.20), plateau(-0.20, 18, 0.5),
            massif(0.3, 80), relief(0.1, 1.0, 0.00, 3.0), placement(0.0, 1.0), climate(0.38, 0.62, 0.35, 0.80)),
    TETON(hills(4, 8, 16), mountains(0.16, 185, 1.2, 0.12), plateau(-0.30, 18, 0.5),
            massif(0.0, 0), relief(0.0, 0.6, -0.20, 1.5), placement(0.0, 0.8), climate(0.38, 0.60, 0.18, 0.45)),
    CASCADIA(hills(18, 40, 25), mountains(0.20, 185, 1.15, 0.20), plateau(-0.25, 18, 0.5),
            massif(0.0, 0), relief(0.1, 1.0, 0.00, 3.5), placement(0.6, 1.2), climate(0.35, 0.62, 0.60, 1.00)),
    CANYONLANDS(hills(6, 12, 50), mountains(-0.40, 40, 1.30, 0.20), plateau(0.60, 22, 0.5),
            massif(0.8, 48), relief(1.0, 0.0, 0.10, 1.0), placement(0.0, 1.0), climate(0.58, 0.80, 0.05, 0.28)),
    PRAIRIE(hills(3, 6, 10), mountains(-0.45, 60, 1.30, 0.20), plateau(-0.40, 18, 0.5),
            massif(0.0, 0), relief(0.0, 0.0, -0.25, 0.8), placement(0.8, 1.4), climate(0.42, 0.72, 0.22, 0.50)),
    STEPPE(hills(6, 12, 20), mountains(-0.15, 110, 1.40, 0.20), plateau(-0.15, 18, 0.5),
            massif(0.2, 35), relief(0.1, 0.0, -0.10, 1.5), placement(0.6, 1.0), climate(0.38, 0.68, 0.08, 0.30)),
    SAVANNA(hills(5, 12, 16), mountains(-0.25, 100, 1.30, 0.20), plateau(-0.25, 18, 0.5),
            massif(0.25, 30), relief(0.0, 0.0, -0.10, 1.5), placement(0.8, 1.0), climate(0.68, 1.00, 0.25, 0.68)),
    DESERT(hills(4, 8, 22), mountains(0.00, 130, 1.50, 0.12), plateau(-0.10, 18, 0.5),
            massif(0.25, 50), relief(0.3, 0.0, 0.10, 1.0), placement(1.0, 1.0), climate(0.62, 1.00, 0.00, 0.20)),
    JUNGLE(hills(14, 30, 18), mountains(0.00, 140, 1.20, 0.25), plateau(-0.30, 18, 0.5),
            massif(0.2, 70), relief(0.1, 0.0, 0.00, 3.0), placement(1.0, 1.0), climate(0.76, 1.00, 0.62, 1.00)),
    BOREAL(hills(6, 14, 14), mountains(-0.10, 130, 1.20, 0.25), plateau(-0.30, 18, 0.5),
            massif(0.0, 0), relief(0.0, 0.8, -0.10, 1.5), placement(0.8, 1.0), climate(0.22, 0.42, 0.25, 1.00)),
    TUNDRA(hills(4, 8, 10), mountains(-0.10, 120, 1.20, 0.25), plateau(-0.30, 18, 0.5),
            massif(0.0, 0), relief(0.0, 1.0, -0.10, 1.0), placement(0.8, 1.0), climate(0.08, 0.26, 0.00, 1.00)),
    ICE_SHEET(hills(6, 14, 45), mountains(0.10, 150, 1.20, 0.25), plateau(-0.40, 18, 0.5),
            massif(0.0, 0), relief(0.0, 1.0, 0.30, 0.6), placement(1.0, 1.0), climate(0.00, 0.12, 0.00, 1.00));

    private static final Province[] VALUES = values();

    public final double hillCoast, hillInland, inlandLift;
    public final double mountainBias, mountainHeight, mountainSharpness, mountainEdge;
    public final double plateauBias, terraceStep, riserStart;
    public final double massif, massifHeight;
    public final double canyon, glacial, cliffBias, detail;
    public final double weightCoast, weightInland;
    public final double tMin, tMax, pMin, pMax;

    Province(Hills hills, Mountains mountains, Plateau plateau, Massif massif, Relief relief,
             Placement placement, ClimateEnvelope climate) {
        this.hillCoast = hills.coast();
        this.hillInland = hills.inland();
        this.inlandLift = hills.inlandLift();
        this.mountainBias = mountains.bias();
        this.mountainHeight = mountains.height();
        this.mountainSharpness = mountains.sharpness();
        this.mountainEdge = mountains.edge();
        this.plateauBias = plateau.bias();
        this.terraceStep = plateau.step();
        this.riserStart = plateau.riserStart();
        this.massif = massif.strength();
        this.massifHeight = massif.height();
        this.canyon = relief.canyon();
        this.glacial = relief.glacial();
        this.cliffBias = relief.cliffBias();
        this.detail = relief.detail();
        this.weightCoast = placement.coast();
        this.weightInland = placement.inland();
        this.tMin = climate.minTemperature();
        this.tMax = climate.maxTemperature();
        this.pMin = climate.minRain();
        this.pMax = climate.maxRain();
    }

    public double karst() {
        return switch (this) {
            case DOLOMITES -> 1.0;
            case JUNGLE -> 0.9;
            case ALPINE_VALLEY -> 0.8;
            case CANYONLANDS -> 0.7;
            case DESERT -> 0.6;
            case TEMPERATE_HILLS -> 0.5;
            case PRAIRIE -> 0.45;
            case STEPPE, ICE_SHEET -> 0.4;
            case SAVANNA -> 0.35;
            case SEA_CLIFFS, CASCADIA, TUNDRA -> 0.3;
            case FJORDLAND, BOREAL -> 0.25;
            case TETON -> 0.2;
        };
    }

    public double envelopeDistance(double temperature, double rain) {
        double dt = Math.max(0, Math.max(tMin - temperature, temperature - tMax));
        double dp = Math.max(0, Math.max(pMin - rain, rain - pMax));
        return Math.hypot(dt, dp);
    }

    public static Province byOrdinal(int ordinal) {
        return VALUES[ordinal];
    }

    private record Hills(double coast, double inland, double inlandLift) {}

    private record Mountains(double bias, double height, double sharpness, double edge) {}

    private record Plateau(double bias, double step, double riserStart) {}

    private record Massif(double strength, double height) {}

    private record Relief(double canyon, double glacial, double cliffBias, double detail) {}

    private record Placement(double coast, double inland) {}

    private record ClimateEnvelope(double minTemperature, double maxTemperature, double minRain, double maxRain) {}

    private static Hills hills(double coast, double inland, double inlandLift) {
        return new Hills(coast, inland, inlandLift);
    }

    private static Mountains mountains(double bias, double height, double sharpness, double edge) {
        return new Mountains(bias, height, sharpness, edge);
    }

    private static Plateau plateau(double bias, double step, double riserStart) {
        return new Plateau(bias, step, riserStart);
    }

    private static Massif massif(double strength, double height) {
        return new Massif(strength, height);
    }

    private static Relief relief(double canyon, double glacial, double cliffBias, double detail) {
        return new Relief(canyon, glacial, cliffBias, detail);
    }

    private static Placement placement(double coast, double inland) {
        return new Placement(coast, inland);
    }

    private static ClimateEnvelope climate(double minTemperature, double maxTemperature, double minRain, double maxRain) {
        return new ClimateEnvelope(minTemperature, maxTemperature, minRain, maxRain);
    }

    public static final class Profile {
        public double hillCoast, hillInland, inlandLift;
        public double mountainBias, mountainHeight, mountainSharpness, mountainEdge;
        public double plateauBias, terraceStep, riserStart;
        public double massif, massifHeight;
        public double canyon, glacial, cliffBias, detail;
        public double karst;
        public Province dominant = TEMPERATE_HILLS;
        public final double[] weights = new double[VALUES.length];

        void clear() {
            hillCoast = hillInland = inlandLift = 0;
            mountainBias = mountainHeight = mountainSharpness = mountainEdge = 0;
            plateauBias = terraceStep = riserStart = 0;
            massif = massifHeight = 0;
            canyon = glacial = cliffBias = detail = 0;
            karst = 0;
            Arrays.fill(weights, 0);
        }

        public double weight(Province province) {
            return weights[province.ordinal()];
        }

        void add(Province province, double weight) {
            hillCoast += province.hillCoast * weight;
            hillInland += province.hillInland * weight;
            inlandLift += province.inlandLift * weight;
            mountainBias += province.mountainBias * weight;
            mountainHeight += province.mountainHeight * weight;
            mountainSharpness += province.mountainSharpness * weight;
            mountainEdge += province.mountainEdge * weight;
            plateauBias += province.plateauBias * weight;
            terraceStep += province.terraceStep * weight;
            riserStart += province.riserStart * weight;
            massif += province.massif * weight;
            massifHeight += province.massifHeight * weight;
            canyon += province.canyon * weight;
            glacial += province.glacial * weight;
            cliffBias += province.cliffBias * weight;
            detail += province.detail * weight;
            karst += province.karst() * weight;
            weights[province.ordinal()] += weight;
        }
    }
}
