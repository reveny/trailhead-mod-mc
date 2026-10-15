package com.reveny.trailhead.worldgen.terrain;

import com.reveny.trailhead.noise.NoiseHelper;
import com.reveny.trailhead.noise.SimplexNoise;

import static com.reveny.trailhead.noise.NoiseConfig.*;

public final class HeightField {
    public static final class Column {
        public double height;
        public double water = Double.NaN;
        public double coastDist;
        public double cliffiness;
        public double plateau;
        public double mountain;
        public double canyon;
        public double moisture;
        public double glacial;
        public double massif;
        public double apron;
        public double talus;
        public double riverDist = Double.MAX_VALUE;
        public Province province = Province.TEMPERATE_HILLS;
        public double temperature = 0.5, rain = 0.5;
        public double continent;
        public double iceDepth;
        public double dune;
        public double volcanic, atoll;
        public double karst;
        public double trough, cone;

        public boolean inRiver() {
            return !Double.isNaN(water);
        }

        Column copy() {
            Column c = new Column();
            c.height = height;
            c.water = water;
            c.coastDist = coastDist;
            c.cliffiness = cliffiness;
            c.plateau = plateau;
            c.mountain = mountain;
            c.canyon = canyon;
            c.moisture = moisture;
            c.glacial = glacial;
            c.massif = massif;
            c.apron = apron;
            c.talus = talus;
            c.riverDist = riverDist;
            c.province = province;
            c.temperature = temperature;
            c.rain = rain;
            c.continent = continent;
            c.iceDepth = iceDepth;
            c.dune = dune;
            c.volcanic = volcanic;
            c.atoll = atoll;
            c.karst = karst;
            c.trough = trough;
            c.cone = cone;
            return c;
        }
    }

    private static final ThreadLocal<Province.Profile> PROFILE = ThreadLocal.withInitial(Province.Profile::new);
    private static final double RANGE_SMOOTHING_RADIUS = 1500.0;

    private final long seed;
    private final Climate climate;
    private final ProvinceMap provinces;
    private final GraniteValleys graniteValleys;
    private final Stratovolcanoes stratovolcanoes;
    private RiverNetwork rivers;

    public HeightField(long seed, Climate.Mode mode) {
        this.seed = seed;
        this.climate = new Climate(seed, mode);
        this.provinces = new ProvinceMap(seed, this);
        this.graniteValleys = new GraniteValleys(seed);
        this.stratovolcanoes = new Stratovolcanoes(seed, new Stratovolcanoes.Probe() {
            @Override
            public double cascadiaWeight(double x, double z) {
                Province.Profile profile = new Province.Profile();
                provinces.profile(x, z, profile);
                return profile.weight(Province.CASCADIA);
            }

            @Override
            public double relief(double x, double z) {
                return computeBase(x, z, new Column(), false) - SEA_LEVEL;
            }

            @Override
            public double coastDistance(double x, double z) {
                return HeightField.this.coastDistance(x, z);
            }
        });
    }

    public Climate climate() {
        return climate;
    }

    void setRivers(RiverNetwork rivers) {
        this.rivers = rivers;
    }

    public double rainAt(double x, double z) {
        return climate.precipitation(x, z, this);
    }

    double continentForClimate(double x, double z) {
        return baseContinent(x, z);
    }

    public double continent(double x, double z) {
        double base = baseContinent(x, z);
        if (base > 0.02) return base;
        return base + islands(x, z, base, null);
    }

    public double coastDistance(double x, double z) {
        double base = baseContinent(x, z);
        double c0 = base > 0.02 ? base : base + islands(x, z, base, null);
        double local = c0 / continentGradient(x, z, c0, COAST_GRADIENT_STEP);
        double far = c0 / continentGradient(x, z, c0, COAST_FAR_GRADIENT_STEP);
        double isletLand = c0 > 0 ? 1.0 - NoiseHelper.smoothstep(-0.02, 0.04, base) : 0.0;
        double farWeight = NoiseHelper.smoothstep(0.008, 0.03, Math.abs(c0)) * (1.0 - isletLand);
        double distance = NoiseHelper.clamp(NoiseHelper.lerp(local, far, farWeight), -2000.0, 2000.0);
        distance += COAST_JAG_STRENGTH * SimplexNoise.noise2(seed + SEED_COAST_JAG, x / COAST_JAG_SCALE, z / COAST_JAG_SCALE);
        distance += COAST_FINE_STRENGTH * SimplexNoise.noise2(seed + SEED_COAST_JAG + 1, x / COAST_FINE_SCALE, z / COAST_FINE_SCALE);
        return distance;
    }

    private double continentGradient(double x, double z, double c0, double step) {
        double gx = (continent(x + step, z) - c0) / step;
        double gz = (continent(x, z + step) - c0) / step;
        return Math.sqrt(gx * gx + gz * gz + COAST_GRADIENT_FLOOR * COAST_GRADIENT_FLOOR);
    }

    public double baseHeight(double x, double z) {
        return computeBase(x, z, new Column(), true);
    }

    Column baseColumn(double x, double z) {
        Column col = new Column();
        computeBase(x, z, col, true);
        return col;
    }

    public Column sample(double x, double z) {
        Column col = sampleWithoutRivers(x, z);
        if (rivers != null && col.coastDist > -RIVER_INFLUENCE) {
            rivers.carve(x, z, col);
        }
        return col;
    }

    public Column sampleWithoutRivers(double x, double z) {
        Column col = new Column();
        double height = computeBase(x, z, col, true);
        double detailAmplitude = (col.coastDist > 0 ? 0.6 : 0.3) * PROFILE.get().detail;
        height += detailAmplitude * NoiseHelper.fbm2(seed + SEED_DETAIL, x, z, DETAIL_SCALE, DETAIL_OCTAVES);
        height += rockRoughness(x, z, col);
        col.height = height;
        return col;
    }

    private double rockRoughness(double x, double z, Column col) {
        double rugged = Math.max(NoiseHelper.smoothstep(0.15, 0.6, col.mountain), col.massif);
        if (rugged <= 0.0 || col.iceDepth > 1.0) return 0.0;
        return rugged * ROCK_ROUGHNESS * NoiseHelper.fbm2(seed + SEED_DETAIL + 7, x, z, ROCK_ROUGHNESS_SCALE, 2);
    }

    private double baseContinent(double x, double z) {
        double warpScale = CONTINENT_WARP_SCALE;
        double fineScale = warpScale * 0.3;
        double fine = CONTINENT_FINE_WARP * CONTINENT_WARP_STRENGTH;
        double wx = x + CONTINENT_WARP_STRENGTH * SimplexNoise.noise2(seed + SEED_CONTINENT_WARP_X, x / warpScale, z / warpScale)
                + fine * SimplexNoise.noise2(seed + SEED_CONTINENT_WARP_X + 7, x / fineScale, z / fineScale);
        double wz = z + CONTINENT_WARP_STRENGTH * SimplexNoise.noise2(seed + SEED_CONTINENT_WARP_Z, x / warpScale, z / warpScale)
                + fine * SimplexNoise.noise2(seed + SEED_CONTINENT_WARP_Z + 7, x / fineScale, z / fineScale);
        double broad = NoiseHelper.fbm2(seed + SEED_CONTINENT, wx, wz, CONTINENT_SCALE, 4);
        double detail = NoiseHelper.fbm2(seed + SEED_CONTINENT + 50, wx, wz, CONTINENT_SCALE / 16.0, CONTINENT_OCTAVES - 4);
        double nearCoast = 1.0 - NoiseHelper.smoothstep(0.05, 0.25, Math.abs(broad - CONTINENT_THRESHOLD));
        double spawnLand = 0.3 * Math.exp(-(x * x + z * z) / (9000.0 * 9000.0));
        return broad + spawnLand + 0.22 * detail * (0.3 + 0.7 * nearCoast) - CONTINENT_THRESHOLD;
    }

    private double islands(double x, double z, double base, Column col) {
        double signal = 0;
        double continental = NoiseHelper.fbm2(seed + SEED_ISLAND, x, z, ISLAND_CONTINENTAL_SCALE, 4);
        signal += Math.max(0.0, continental - 0.36) * 0.7 * NoiseHelper.smoothstep(-0.32, -0.08, base)
                * (1.0 - NoiseHelper.smoothstep(-0.04, 0.02, base));

        double temperature = climate.temperature(climate.latitude(x, z));
        double coolness = 1.0 - NoiseHelper.smoothstep(0.5, 0.62, temperature);
        double coastalBand = NoiseHelper.smoothstep(-0.16, -0.06, base) * (1.0 - NoiseHelper.smoothstep(-0.02, 0.01, base));
        if (coolness > 0 && coastalBand > 0) {
            double region = NoiseHelper.smoothstep(0.5, 0.62,
                    NoiseHelper.fbm2(seed + SEED_ISLAND + 1, x, z, 3000.0, 2) * 0.5 + 0.5);
            double islets = NoiseHelper.fbm2(seed + SEED_ISLAND + 2, x, z, 140.0, 3);
            signal += Math.max(0.0, islets - 0.12) * 0.55 * coastalBand * region * coolness;
        }

        double openOcean = 1.0 - NoiseHelper.smoothstep(-0.14, -0.06, base);
        if (openOcean <= 0) return signal;

        double[] volcano = volcanoChains(x, z);
        double volcanicSlope = volcano[0];
        double cone = volcano[1];
        signal += cone * 0.5 * openOcean;

        double warm = NoiseHelper.smoothstep(0.7, 0.78, temperature);
        double reef = 0;
        if (warm > 0) {
            long cellX = (long) Math.floor(x / ATOLL_CELL), cellZ = (long) Math.floor(z / ATOLL_CELL);
            for (long cx = cellX - 1; cx <= cellX + 1; cx++) {
                for (long cz = cellZ - 1; cz <= cellZ + 1; cz++) {
                    if (NoiseHelper.hash01(seed + SEED_ISLAND + 8, cx, cz) > ATOLL_CHANCE) continue;
                    double atollX = (cx + 0.25 + 0.5 * NoiseHelper.hash01(seed + SEED_ISLAND + 9, cx, cz)) * ATOLL_CELL;
                    double atollZ = (cz + 0.25 + 0.5 * NoiseHelper.hash01(seed + SEED_ISLAND + 10, cx, cz)) * ATOLL_CELL;
                    double radius = 170.0 + 160.0 * NoiseHelper.hash01(seed + SEED_ISLAND + 11, cx, cz);
                    double distance = Math.hypot(x - atollX, z - atollZ)
                            + 18.0 * SimplexNoise.noise2(seed + SEED_ISLAND + 12, x / 60.0, z / 60.0);
                    double ring = 1.0 - Math.abs(distance - radius) / 28.0;
                    double passes = NoiseHelper.smoothstep(-0.35, -0.1,
                            SimplexNoise.noise2(seed + SEED_ISLAND + 13, x / 90.0, z / 90.0));
                    signal += Math.max(0.0, ring) * 0.55 * passes * warm * openOcean;
                    reef = Math.max(reef, (1.0 - NoiseHelper.smoothstep(radius + 30.0, radius + 110.0, distance)) * warm);
                }
            }
        }
        if (col != null) {
            col.volcanic = volcanicSlope * openOcean;
            col.atoll = reef * openOcean;
        }
        return signal;
    }

    private double[] volcanoChains(double x, double z) {
        double slope = 0, cone = 0;
        long cellX = (long) Math.floor(x / VOLCANO_CELL), cellZ = (long) Math.floor(z / VOLCANO_CELL);
        for (long cx = cellX - 1; cx <= cellX + 1; cx++) {
            for (long cz = cellZ - 1; cz <= cellZ + 1; cz++) {
                if (NoiseHelper.hash01(seed + SEED_ISLAND + 3, cx, cz) > VOLCANO_CHANCE) continue;
                double hotspotX = (cx + 0.2 + 0.6 * NoiseHelper.hash01(seed + SEED_ISLAND + 4, cx, cz)) * VOLCANO_CELL;
                double hotspotZ = (cz + 0.2 + 0.6 * NoiseHelper.hash01(seed + SEED_ISLAND + 5, cx, cz)) * VOLCANO_CELL;
                double direction = NoiseHelper.hash01(seed + SEED_ISLAND + 6, cx, cz) * Math.PI * 2;
                int cones = 3 + (int) (NoiseHelper.hash01(seed + SEED_ISLAND + 7, cx, cz) * 3);
                for (int i = 0; i < cones; i++) {
                    double coneX = hotspotX + Math.cos(direction) * i * 650.0;
                    double coneZ = hotspotZ + Math.sin(direction) * i * 650.0;
                    double radius = 420.0 * (1.0 - 0.15 * i);
                    double c = Math.max(0.0, 1.0 - Math.hypot(x - coneX, z - coneZ) / radius);
                    if (c <= 0) continue;
                    double age = 1.0 - 0.2 * i;
                    if (c * age > cone) cone = c * age;
                    slope = Math.max(slope, c);
                }
            }
        }
        return new double[]{slope, cone};
    }

    private double computeBase(double x, double z, Column col, boolean withStratovolcanoes) {
        Stratovolcanoes.Site volcano = withStratovolcanoes ? stratovolcanoes.siteNear(x, z) : null;
        Province.Profile profile = PROFILE.get();
        provinces.profile(x, z, profile);
        col.province = profile.dominant;
        col.glacial = profile.glacial;
        col.karst = profile.karst;

        double coastDist = coastDistance(x, z);
        col.coastDist = coastDist;
        double base = baseContinent(x, z);
        col.continent = base > 0.02 ? base : base + islands(x, z, base, col);
        col.temperature = climate.temperature(climate.latitude(x, z));
        col.rain = climate.precipitation(x, z, this);

        double inland = NoiseHelper.smoothstep(0.0, INLAND_RAMP, coastDist);
        double hills = NoiseHelper.erodedFbm2(seed + SEED_HILLS, x, z, HILL_SCALE, HILL_OCTAVES, HILL_EROSION);
        double hills01 = NoiseHelper.clamp(hills * 0.5 + 0.5, 0.0, 1.0);
        double relief = LOWLAND_HEIGHT + profile.inlandLift * inland
                + hills01 * NoiseHelper.lerp(profile.hillCoast, profile.hillInland, inland);

        relief = addMountains(x, z, col, profile, relief, inland, hills01, coastDist);
        double mountainMask = col.mountain;
        relief = addMassifs(x, z, col, profile, relief, coastDist);
        if (relief > 0) {
            relief = RELIEF_SCALE * HYPSOMETRIC_REF * Math.pow(relief / HYPSOMETRIC_REF, HYPSOMETRIC_GAMMA);
        }
        relief = addDunes(x, z, col, profile, relief, inland, mountainMask);
        relief = addIceSheet(x, z, col, profile, relief, coastDist);
        relief = addIslandShapes(col, relief, hills01);
        double graniteWeight = profile.weight(Province.GRANITE_VALLEY);
        if (graniteWeight > 0.0) {
            relief = graniteValleys.shape(x, z, relief, inland, graniteWeight, col);
        }
        relief = stratovolcanoes.shape(volcano, x, z, relief, col);

        double cliffiness = NoiseHelper.smoothstep(0.42, 0.6,
                NoiseHelper.fbm2(seed + SEED_CLIFF, x, z, CLIFF_MASK_SCALE, 3) * 0.5 + 0.5 + 0.1 * mountainMask + profile.cliffBias)
                * (1.0 - NoiseHelper.smoothstep(0.0, 0.2, col.volcanic))
                * profile.rockyCoast;
        col.cliffiness = cliffiness;
        double cliffTop = NoiseHelper.lerp(CLIFF_HEIGHT_MIN, CLIFF_HEIGHT_MAX, NoiseHelper.clamp(
                (NoiseHelper.fbm2(seed + SEED_CLIFF + 100, x, z, CLIFF_HEIGHT_SCALE, 3) * 0.5 + 0.5 - 0.25) / 0.5, 0.0, 1.0));
        double mainland = Math.max(NoiseHelper.smoothstep(-0.02, 0.03, base), profile.weight(Province.SEA_CLIFFS));
        relief = liftCoastalCliffs(relief, cliffiness * mainland, cliffTop, coastDist);
        double plateau = addPlateaus(x, z, col, profile, mountainMask);
        if (plateau > 0.0) {
            double terraced = terrace(x, z, relief, profile.terraceStep, profile.riserStart);
            relief = NoiseHelper.lerp(relief, terraced, plateau * PLATEAU_STRENGTH);
        }

        double height = softCap(SEA_LEVEL + relief);
        double seaFloor = seaFloor(x, z, col, coastDist);
        double faceWidth = NoiseHelper.lerp(BLUFF_FACE_WIDTH, CLIFF_FACE_WIDTH, NoiseHelper.smoothstep(0.5, 1.0, profile.rockyCoast));
        double result = shapeCoast(x, z, height, seaFloor, coastDist, cliffiness, cliffTop, faceWidth);

        double canyonNoise = NoiseHelper.smoothstep(0.52, 0.68,
                NoiseHelper.fbm2(seed + SEED_CANYON, x, z, CANYON_SCALE, 2) * 0.5 + 0.5);
        double slotCanyons = canyonNoise * 0.7 * (1.0 - profile.glacial) * profile.aridity;
        col.canyon = Math.max(Math.max(slotCanyons, profile.canyon), plateau * 0.85);
        col.moisture = NoiseHelper.clamp(NoiseHelper.fbm2(seed + SEED_MOISTURE, x, z, 1400.0, 3) * 0.5 + 0.5, 0.0, 1.0);
        col.height = result;
        return result;
    }

    private static double liftCoastalCliffs(double relief, double cliffiness, double cliffTop, double coastDist) {
        if (cliffiness <= 0.0) return relief;
        double nearCoast = 1.0 - NoiseHelper.smoothstep(CLIFF_INLAND_FADE_START, CLIFF_INLAND_FADE_END, coastDist);
        double lifted = Math.max(relief, NoiseHelper.lerp(relief, cliffTop + 0.25 * relief, nearCoast));
        return NoiseHelper.lerp(relief, lifted, cliffiness);
    }

    private double addPlateaus(double x, double z, Column col, Province.Profile profile, double mountainMask) {
        double plateau = NoiseHelper.smoothstep(0.6, 0.63,
                NoiseHelper.fbm2(seed + SEED_PLATEAU, x, z, PLATEAU_MASK_SCALE, 3) * 0.5 + 0.5 + profile.plateauBias)
                * (1.0 - NoiseHelper.smoothstep(0.2, 0.4, mountainMask))
                * (1.0 - col.apron)
                * (1.0 - profile.weight(Province.GRANITE_VALLEY));
        col.plateau = plateau;
        return plateau;
    }

    private double addMountains(double x, double z, Column col, Province.Profile profile, double relief,
                                double inland, double hills01, double coastDist) {
        double belt = climate.mountainBelt(x, z);
        double mountainInput = rangeNoise(x, z) + 0.12 * inland + profile.mountainBias + 0.22 * (belt - 0.4);
        double edge = Math.max(MOUNTAIN_MIN_EDGE, profile.mountainEdge);
        double mask = NoiseHelper.smoothstep(0.52, 0.52 + edge, mountainInput);
        col.mountain = mask;
        if (mask <= 0.0) return relief;

        double wx = x + 140.0 * SimplexNoise.noise2(seed + SEED_MOUNTAIN_WARP, x / 500.0, z / 500.0);
        double wz = z + 140.0 * SimplexNoise.noise2(seed + SEED_MOUNTAIN_WARP + 1, x / 500.0, z / 500.0);
        double ridge = NoiseHelper.softRidged2(seed + SEED_MOUNTAIN, wx, wz, MOUNTAIN_SCALE, MOUNTAIN_OCTAVES, MOUNTAIN_CREST_ROUNDNESS);
        double peaks = Math.pow(NoiseHelper.clamp((ridge - 0.36) / 0.55, 0.0, 1.0), profile.mountainSharpness);
        double foothill = Math.pow(mask, 1.6);
        double uplift = 0.26 * (0.6 + 0.4 * hills01);
        relief += foothill * (uplift + 0.74 * peaks * (0.7 + 0.3 * hills01)) * profile.mountainHeight
                * NoiseHelper.smoothstep(-60.0, 260.0, coastDist);
        relief += (mask - foothill) * 25.0 * hills01;
        return relief;
    }

    private double rangeNoise(double x, double z) {
        double radius = RANGE_SMOOTHING_RADIUS;
        double sum = 2.0 * NoiseHelper.fbm2(seed + SEED_MOUNTAIN_MASK, x, z, MOUNTAIN_MASK_SCALE, 2);
        for (int k = 0; k < 6; k++) {
            double angle = k * Math.PI / 3.0;
            sum += NoiseHelper.fbm2(seed + SEED_MOUNTAIN_MASK, x + radius * Math.cos(angle), z + radius * Math.sin(angle),
                    MOUNTAIN_MASK_SCALE, 2);
        }
        return NoiseHelper.clamp(sum / 8.0 * 0.62 + 0.5, 0.0, 1.0);
    }

    private double addMassifs(double x, double z, Column col, Province.Profile profile, double relief, double coastDist) {
        if (profile.massif <= 0.01) return relief;

        double threshold = 0.75 - 0.1 * Math.min(1.0, profile.massif);
        double n = massifNoise(x, z);
        double gradient = Math.max(massifGradient(x, z, 2.0), massifGradient(x, z, MASSIF_GRADIENT_STEP));
        double floor = MASSIF_GRADIENT_FLOOR;
        double inside = NoiseHelper.clamp((n - threshold) / Math.sqrt(gradient * gradient + floor * floor), -300.0, 300.0);
        double strength = Math.min(1.0, profile.massif * 1.5);
        double mesaWeight = profile.weights[Province.CANYONLANDS.ordinal()];
        double wallBlocks = NoiseHelper.lerp(MASSIF_WALL_WIDTH, MESA_WALL_WIDTH, mesaWeight);
        double wall = NoiseHelper.smoothstep(0.0, wallBlocks, inside) * strength;
        double apron = NoiseHelper.smoothstep(-100.0, 0.0, inside) * strength;
        col.apron = apron;
        col.talus = NoiseHelper.smoothstep(0.35, 0.8, apron) * (1.0 - wall);
        relief += apron * apron * (8.0 + 0.33 * profile.massifHeight);
        if (wall > 0.0) {
            double depth = NoiseHelper.smoothstep(0.0, 220.0, inside);
            double crag = NoiseHelper.softRidged2(seed + SEED_MASSIF + 1, x, z, 320.0, 3, 0.15);
            double peaked = (0.2 + 0.8 * depth) * (0.8 + 0.22 * crag);
            double mesa = 0.7 + 0.3 * NoiseHelper.smoothstep(90.0, 90.0 + MESA_WALL_WIDTH, inside)
                    + 0.03 * SimplexNoise.noise2(seed + SEED_MASSIF + 4, x / 60.0, z / 60.0);
            double shape = NoiseHelper.lerp(peaked, mesa, NoiseHelper.clamp(profile.canyon, 0.0, 1.0) * mesaWeight);
            relief += profile.massifHeight * 0.67 * wall * shape * NoiseHelper.smoothstep(0.0, 200.0, coastDist);
        }
        col.massif = wall;
        return relief;
    }

    private double massifGradient(double x, double z, double step) {
        double gx = (massifNoise(x + step, z) - massifNoise(x - step, z)) / (2 * step);
        double gz = (massifNoise(x, z + step) - massifNoise(x, z - step)) / (2 * step);
        return Math.sqrt(gx * gx + gz * gz);
    }

    private double massifNoise(double x, double z) {
        double wx = x + MASSIF_WARP * SimplexNoise.noise2(seed + SEED_MASSIF + 2, x / 240.0, z / 240.0);
        double wz = z + MASSIF_WARP * SimplexNoise.noise2(seed + SEED_MASSIF + 3, x / 240.0, z / 240.0);
        return NoiseHelper.fbm2(seed + SEED_MASSIF, wx, wz, MASSIF_SCALE, 2) * 0.5 + 0.5;
    }

    private double addDunes(double x, double z, Column col, Province.Profile profile, double relief,
                            double inland, double mountainMask) {
        double desertWeight = profile.weight(Province.DESERT);
        if (desertWeight <= 0) return relief;

        double erg = NoiseHelper.smoothstep(0.48, 0.6,
                NoiseHelper.fbm2(seed + SEED_DUNE, x, z, 5000.0, 3) * 0.5 + 0.5) * desertWeight
                * (1.0 - mountainMask);
        if (erg > 0) {
            double u = (x + 40.0 * SimplexNoise.noise2(seed + SEED_DUNE + 1, x / 260.0, z / 260.0)) / DUNE_PERIOD;
            double phase = u - Math.floor(u);
            double profileShape = phase < 0.78 ? phase / 0.78 : (1.0 - phase) / 0.22;
            profileShape = profileShape * profileShape * (3 - 2 * profileShape);
            double crestHeight = 0.55 + 0.45 * SimplexNoise.noise2(seed + SEED_DUNE + 2, x / 400.0, z / 150.0);
            double ripples = 1.2 * SimplexNoise.noise2(seed + SEED_DUNE + 3, x / 14.0, z / 30.0);
            double basin = LOWLAND_HEIGHT + profile.inlandLift * inland;
            relief = NoiseHelper.lerp(relief, basin + DUNE_HEIGHT * crestHeight * profileShape + ripples, erg);
        }
        col.dune = erg;
        return relief;
    }

    private double addIceSheet(double x, double z, Column col, Province.Profile profile, double relief, double coastDist) {
        double iceWeight = profile.weight(Province.ICE_SHEET);
        if (iceWeight <= 0) return relief;

        double thickness = iceWeight * (12.0 + 45.0 * NoiseHelper.smoothstep(0.0, 1500.0, coastDist))
                * (0.85 + 0.15 * SimplexNoise.noise2(seed + SEED_ICE, x / 900.0, z / 900.0));
        col.iceDepth = thickness;
        return relief * (1.0 - 0.45 * iceWeight) + thickness;
    }

    private static double addIslandShapes(Column col, double relief, double hills01) {
        if (col.volcanic > 0) {
            double crater = NoiseHelper.smoothstep(0.9, 0.97, col.volcanic) * 0.12;
            relief += VOLCANO_HEIGHT * (Math.pow(col.volcanic, 1.8) - crater) * (0.6 + 0.4 * hills01);
        }
        if (col.atoll > 0) {
            relief = NoiseHelper.lerp(relief, Math.min(relief, 2.0), col.atoll);
        }
        return relief;
    }

    private static double softCap(double height) {
        double knee = HEIGHT_CAP - HEIGHT_KNEE;
        if (height <= knee) return height;
        return knee + HEIGHT_KNEE * Math.tanh((height - knee) / HEIGHT_KNEE);
    }

    private double seaFloor(double x, double z, Column col, double coastDist) {
        double shelfDepth = 3.0 + (OCEAN_SHELF_DEPTH - 3.0) * NoiseHelper.smoothstep(0.0, 350.0, -coastDist);
        double deep = OCEAN_ABYSS_DEPTH * NoiseHelper.smoothstep(-0.05, -0.28, col.continent);
        double floor = SEA_LEVEL - shelfDepth - deep
                + 3.0 * NoiseHelper.fbm2(seed + SEED_OCEAN_FLOOR, x, z, 200.0, 3)
                + 6.0 * NoiseHelper.fbm2(seed + SEED_OCEAN_FLOOR + 1, x, z, 1500.0, 2)
                * NoiseHelper.smoothstep(0.0, 1.0, deep / OCEAN_ABYSS_DEPTH);
        if (col.atoll > 0) {
            double lagoon = SEA_LEVEL - 4.0 + 1.5 * SimplexNoise.noise2(seed + SEED_ISLAND + 14, x / 25.0, z / 25.0);
            floor = NoiseHelper.lerp(floor, lagoon, col.atoll);
        }
        return floor;
    }

    private double shapeCoast(double x, double z, double height, double seaFloor, double coastDist,
                              double cliffiness, double cliffTop, double faceWidth) {
        double gentle = coastDist >= 0
                ? SEA_LEVEL + 0.5 + (height - SEA_LEVEL - 0.5) * NoiseHelper.smoothstep(0.0, GENTLE_COAST_RAMP, coastDist)
                : NoiseHelper.lerp(seaFloor, SEA_LEVEL + 0.5, NoiseHelper.smoothstep(-24.0, 0.0, coastDist));
        if (cliffiness <= 0.0) return gentle;

        double shelfWidth = 10.0 + 8.0 * SimplexNoise.noise2(seed + SEED_CLIFF + 200, x / 120.0, z / 120.0);
        double boulders = NoiseHelper.smoothstep(0.35, 0.6,
                SimplexNoise.noise2(seed + SEED_CLIFF + 201, x / 5.0, z / 5.0)) * 3.0;
        double shelf = SEA_LEVEL - 1.6 + boulders;
        double cliff = coastDist >= 0
                ? NoiseHelper.lerp(SEA_LEVEL + 1.0, height, NoiseHelper.smoothstep(0.0, faceWidth, coastDist))
                : NoiseHelper.lerp(seaFloor, shelf, NoiseHelper.smoothstep(-shelfWidth - 14.0, -shelfWidth, coastDist));
        double result = NoiseHelper.lerp(gentle, cliff, cliffiness);
        return addSeaStacks(x, z, result, coastDist, cliffiness, cliffTop);
    }

    private double addSeaStacks(double x, double z, double height, double coastDist, double cliffiness, double cliffTop) {
        double band = NoiseHelper.smoothstep(-150.0, -50.0, coastDist) * (1.0 - NoiseHelper.smoothstep(-26.0, -8.0, coastDist));
        if (band <= 0.0) return height;
        double region = NoiseHelper.smoothstep(0.62, 0.75,
                NoiseHelper.fbm2(seed + SEED_STACK + 1, x, z, STACK_REGION_SCALE, 2) * 0.5 + 0.5);
        if (region <= 0.0) return height;
        double stackNoise = NoiseHelper.fbm2(seed + SEED_STACK, x, z, STACK_SCALE, 3);
        double stack = NoiseHelper.smoothstep(0.44, 0.5, stackNoise) * band * region * cliffiness;
        double top = SEA_LEVEL + cliffTop * 0.75;
        return Math.max(height, NoiseHelper.lerp(height, top, stack));
    }

    private double terrace(double x, double z, double relief, double baseStep, double riserStart) {
        double stepNoise = SimplexNoise.noise2(seed + SEED_PLATEAU + 1, x / 900.0, z / 900.0) * 0.5 + 0.5;
        double step = baseStep * (0.7 + 0.6 * stepNoise);
        double warped = relief
                + 0.45 * step * SimplexNoise.noise2(seed + SEED_PLATEAU + 2, x / 160.0, z / 160.0)
                + 2.5 * SimplexNoise.noise2(seed + SEED_PLATEAU + 3, x / 40.0, z / 40.0);
        double tier = Math.floor(warped / step);
        double fraction = warped / step - tier;
        double riser = NoiseHelper.smoothstep(riserStart, 1.0, fraction);
        return (tier + riser * 0.85 + fraction * 0.15) * step;
    }
}
