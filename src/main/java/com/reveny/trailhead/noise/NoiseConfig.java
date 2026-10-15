package com.reveny.trailhead.noise;

public final class NoiseConfig {
    public static final int SEA_LEVEL = 80;
    public static final int MIN_Y = -64;
    public static final int GEN_DEPTH = 384;
    public static final int MAX_Y = MIN_Y + GEN_DEPTH;
    public static final int HEIGHT_CAP = MAX_Y - 8;

    public static final double CONTINENT_SCALE = 60000.0;
    public static final int CONTINENT_OCTAVES = 9;
    public static final double CONTINENT_THRESHOLD = -0.08;
    public static final double CONTINENT_WARP_SCALE = 1200.0;
    public static final double CONTINENT_WARP_STRENGTH = 300.0;
    public static final double CONTINENT_FINE_WARP = 0.2;
    public static final double COAST_GRADIENT_STEP = 8.0;
    public static final double COAST_GRADIENT_FLOOR = 1.2e-4;
    public static final double COAST_JAG_SCALE = 90.0;
    public static final double COAST_JAG_STRENGTH = 16.0;
    public static final double COAST_FINE_SCALE = 22.0;
    public static final double COAST_FINE_STRENGTH = 4.0;

    public static final double ISLAND_CONTINENTAL_SCALE = 2600.0;
    public static final double VOLCANO_CELL = 5000.0;
    public static final double VOLCANO_CHANCE = 0.3;
    public static final double VOLCANO_HEIGHT = 150.0;
    public static final double ATOLL_CELL = 2400.0;
    public static final double ATOLL_CHANCE = 0.35;
    public static final double OCEAN_SHELF_DEPTH = 18.0;
    public static final double OCEAN_ABYSS_DEPTH = 52.0;

    public static final double DUNE_PERIOD = 120.0;
    public static final double DUNE_HEIGHT = 20.0;

    public static final double CLIMATE_POLE_DISTANCE = 120000.0;
    public static final double CLIMATE_WOBBLE = 9000.0;
    public static final double CLIMATE_ZONE_SCALE = 170000.0;
    public static final double MOUNTAIN_BELT_SCALE = 30000.0;
    public static final double CLIMATE_RAIN_SCALE = 18000.0;
    public static final double CLIMATE_LAPSE = 0.0019;
    public static final double SNOW_TEMPERATURE = 0.13;

    public static final double PROVINCE_SIZE = 3500.0;
    public static final double PROVINCE_BLEND = 160.0;
    public static final double PROVINCE_WARP = 420.0;
    public static final double PROVINCE_WARP_SCALE = 1600.0;
    public static final double PROVINCE_COAST_SIGNAL = 0.03;
    public static final double PROVINCE_CLIMATE_MARGIN = 0.04;
    public static final double MASSIF_SCALE = 450.0;
    public static final double MASSIF_WALL_WIDTH = 70.0;
    public static final double MASSIF_GRADIENT_STEP = 16.0;
    public static final double MASSIF_GRADIENT_FLOOR = 1.5e-3;
    public static final double MASSIF_WARP = 45.0;
    public static final double MESA_WALL_WIDTH = 26.0;
    public static final double CANYON_WALL_SLOPE = 2.5;
    public static final double MOUNTAIN_CREST_ROUNDNESS = 0.3;

    public static final double INLAND_RAMP = 650.0;
    public static final double HILL_SCALE = 950.0;
    public static final int HILL_OCTAVES = 7;
    public static final double HILL_EROSION = 1.6;
    public static final double LOWLAND_HEIGHT = 8.0;

    public static final double HYPSOMETRIC_REF = 200.0;
    public static final double HYPSOMETRIC_GAMMA = 1.4;

    public static final double MOUNTAIN_MIN_EDGE = 0.3;
    public static final double MOUNTAIN_MASK_SCALE = 3200.0;
    public static final double MOUNTAIN_SCALE = 2200.0;
    public static final int MOUNTAIN_OCTAVES = 5;
    public static final double HEIGHT_KNEE = 70.0;

    public static final double PLATEAU_MASK_SCALE = 1500.0;
    public static final double PLATEAU_STRENGTH = 0.92;

    public static final double CLIFF_MASK_SCALE = 1700.0;
    public static final double CLIFF_HEIGHT_SCALE = 650.0;
    public static final double CLIFF_HEIGHT_MIN = 30.0;
    public static final double CLIFF_HEIGHT_MAX = 95.0;
    public static final double CLIFF_FACE_WIDTH = 3.0;
    public static final double CLIFF_INLAND_FADE_START = 220.0;
    public static final double CLIFF_INLAND_FADE_END = 800.0;
    public static final double GENTLE_COAST_RAMP = 140.0;
    public static final double STACK_SCALE = 46.0;
    public static final double STACK_REGION_SCALE = 900.0;

    public static final double DETAIL_SCALE = 60.0;
    public static final int DETAIL_OCTAVES = 4;
    public static final double ROCK_ROUGHNESS = 3.0;
    public static final double ROCK_ROUGHNESS_SCALE = 11.0;

    public static final int RIVER_TILE_SIZE = 4096;
    public static final int RIVER_TILE_PAD = 1024;
    public static final int RIVER_CELL = 32;
    public static final int RIVER_MIN_ACCUMULATION = 44;
    public static final int RIVER_TILE_MARGIN = 256;
    public static final double RIVER_JITTER = 11.0;
    public static final double RIVER_INCISION = 3.0;
    public static final double RIVER_HALF_WIDTH_BASE = 1.4;
    public static final double RIVER_HALF_WIDTH_FLOW = 0.34;
    public static final double RIVER_HALF_WIDTH_MAX = 22.0;
    public static final double RIVER_INFLUENCE = 200.0;
    public static final double WATERFALL_MIN_DROP = 5.0;
    public static final double CANYON_SCALE = 1300.0;
    public static final double FJORD_REACH = 320.0;
    public static final double FJORD_DEPTH = 28.0;
    public static final double LAKE_MIN_DEPTH = 3.0;
    public static final double LAKE_MAX_DEPTH = 16.0;

    public static final int FACE_WARP_RADIUS = 5;
    public static final double FACE_GRADIENT_START = 2.0;
    public static final double FACE_GRADIENT_FULL = 4.0;
    public static final double FACE_LEDGE_SCALE_XZ = 45.0;
    public static final double FACE_LEDGE_SCALE_Y = 30.0;

    public static final double GRASS_MAX_SLOPE = 1.2;
    public static final double MOSS_MAX_SLOPE = 3.2;
    public static final int DIRT_DEPTH = 3;
    public static final double STRATA_UNIT = 11.0;

    public static final long SEED_CONTINENT = 0L;
    public static final long SEED_CONTINENT_WARP_X = 1000L;
    public static final long SEED_CONTINENT_WARP_Z = 2000L;
    public static final long SEED_MOUNTAIN = 3000L;
    public static final long SEED_MOUNTAIN_WARP = 3500L;
    public static final long SEED_HILLS = 4000L;
    public static final long SEED_DETAIL = 6000L;
    public static final long SEED_FACE = 7000L;
    public static final long SEED_STRATA = 8000L;
    public static final long SEED_COAST_JAG = 9000L;
    public static final long SEED_MOISTURE = 10000L;
    public static final long SEED_OCEAN_FLOOR = 11000L;
    public static final long SEED_MOUNTAIN_MASK = 13000L;
    public static final long SEED_PLATEAU = 16000L;
    public static final long SEED_CLIFF = 17000L;
    public static final long SEED_STACK = 18000L;
    public static final long SEED_RIVER = 19000L;
    public static final long SEED_CANYON = 20000L;
    public static final long SEED_SURFACE = 21000L;
    public static final long SEED_PROVINCE = 22000L;
    public static final long SEED_MASSIF = 23000L;
    public static final long SEED_CLIMATE = 24000L;
    public static final long SEED_ISLAND = 25000L;
    public static final long SEED_DUNE = 26000L;
    public static final long SEED_ICE = 27000L;
    public static final long SEED_CAVE = 28000L;

    public static final int CAVE_MIN_COVER = 8;
    public static final int CAVE_MIN_COVER_UNDER_WATER = 14;
    public static final double CAVE_CEILING_FADE = 18.0;
    public static final double CAVE_CEILING_STRENGTH = 1.0;
    public static final int CAVE_WALL_REACH = 4;
    public static final double CAVE_KARST_BIAS = 0.09;
    public static final double CAVE_BASE_BIAS = -0.01;
    public static final double GROUNDWATER_WARP = 20.0;
    public static final int GROUNDWATER_CELL = 64;
    public static final int LAVA_LEVEL = -55;

    private NoiseConfig() {}
}
