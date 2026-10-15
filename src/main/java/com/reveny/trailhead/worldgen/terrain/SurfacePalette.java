package com.reveny.trailhead.worldgen.terrain;

import com.reveny.trailhead.noise.NoiseHelper;
import com.reveny.trailhead.noise.SimplexNoise;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static com.reveny.trailhead.noise.NoiseConfig.*;

public final class SurfacePalette {
    static final BlockState STONE = Blocks.STONE.defaultBlockState();
    static final BlockState DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();
    static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
    static final BlockState WATER = Blocks.WATER.defaultBlockState();
    static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
    static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
    static final BlockState COARSE_DIRT = Blocks.COARSE_DIRT.defaultBlockState();
    static final BlockState PODZOL = Blocks.PODZOL.defaultBlockState();
    static final BlockState MOSS = Blocks.MOSS_BLOCK.defaultBlockState();
    static final BlockState SNOW = Blocks.SNOW_BLOCK.defaultBlockState();
    static final BlockState SAND = Blocks.SAND.defaultBlockState();
    static final BlockState RED_SAND = Blocks.RED_SAND.defaultBlockState();
    static final BlockState GRAVEL = Blocks.GRAVEL.defaultBlockState();
    static final BlockState CLAY = Blocks.CLAY.defaultBlockState();
    static final BlockState MOSSY_COBBLE = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
    static final BlockState TERRACOTTA = Blocks.TERRACOTTA.defaultBlockState();
    static final BlockState MUD = Blocks.MUD.defaultBlockState();
    static final BlockState PACKED_ICE = Blocks.PACKED_ICE.defaultBlockState();
    static final BlockState BLUE_ICE = Blocks.BLUE_ICE.defaultBlockState();
    static final BlockState SMOOTH_BASALT = Blocks.SMOOTH_BASALT.defaultBlockState();
    static final BlockState TUFF = Blocks.TUFF.defaultBlockState();
    static final BlockState LAVA = Blocks.LAVA.defaultBlockState();
    static final BlockState ICE = Blocks.ICE.defaultBlockState();

    private static final Strata GREY = Strata.of(
            Blocks.STONE, 0.47, Blocks.ANDESITE, 0.24, Blocks.TUFF, 0.16, Blocks.DEEPSLATE, 0.06,
            Blocks.CALCITE, 0.015, Blocks.DRIPSTONE_BLOCK, 0.055);
    private static final Strata BASALT = Strata.of(
            Blocks.BASALT, 0.35, Blocks.SMOOTH_BASALT, 0.2, Blocks.TUFF, 0.2, Blocks.DEEPSLATE, 0.15, Blocks.STONE, 0.1);
    private static final Strata DOLOMITE = Strata.of(
            Blocks.CALCITE, 0.33, Blocks.DIORITE, 0.25, Blocks.ANDESITE, 0.2, Blocks.STONE, 0.17, Blocks.TUFF, 0.05);
    private static final Strata GRANITE = Strata.of(
            Blocks.STONE, 0.4, Blocks.ANDESITE, 0.2, Blocks.DIORITE, 0.15, Blocks.GRANITE, 0.13, Blocks.DEEPSLATE, 0.12);
    private static final Strata RED_ROCK = Strata.of(
            Blocks.TERRACOTTA, 0.35, Blocks.RED_TERRACOTTA, 0.25, Blocks.GRANITE, 0.2,
            Blocks.BROWN_TERRACOTTA, 0.1, Blocks.LIGHT_GRAY_TERRACOTTA, 0.1);
    private static final Strata WHITE_ROCK = Strata.of(
            Blocks.SMOOTH_SANDSTONE, 0.35, Blocks.SANDSTONE, 0.3, Blocks.WHITE_TERRACOTTA, 0.15,
            Blocks.CUT_SANDSTONE, 0.1, Blocks.TERRACOTTA, 0.1);
    private static final Strata DESERT_ROCK = Strata.of(
            Blocks.SANDSTONE, 0.3, Blocks.TERRACOTTA, 0.25, Blocks.BROWN_TERRACOTTA, 0.1,
            Blocks.GRANITE, 0.15, Blocks.SMOOTH_SANDSTONE, 0.2);
    private static final Strata KARST = Strata.of(
            Blocks.STONE, 0.35, Blocks.CALCITE, 0.2, Blocks.ANDESITE, 0.2, Blocks.TUFF, 0.15, Blocks.DIORITE, 0.1);
    private static final Strata PALE_GRANITE = Strata.of(
            Blocks.STONE, 0.34, Blocks.ANDESITE, 0.3, Blocks.DIORITE, 0.24, Blocks.SMOOTH_STONE, 0.07, Blocks.CALCITE, 0.05);
    private static final Strata SHIELD = Strata.of(
            Blocks.GRANITE, 0.3, Blocks.STONE, 0.3, Blocks.DIORITE, 0.15, Blocks.ANDESITE, 0.15, Blocks.DEEPSLATE, 0.1);

    private SurfacePalette() {}

    private record Strata(BlockState[] blocks, double[] weights) {
        static Strata of(Object... blockWeightPairs) {
            BlockState[] blocks = new BlockState[blockWeightPairs.length / 2];
            double[] weights = new double[blockWeightPairs.length / 2];
            for (int i = 0; i < blocks.length; i++) {
                blocks[i] = ((Block) blockWeightPairs[i * 2]).defaultBlockState();
                weights[i] = (Double) blockWeightPairs[i * 2 + 1];
            }
            return new Strata(blocks, weights);
        }

        BlockState pick(double roll) {
            for (int i = 0; i < blocks.length; i++) {
                roll -= weights[i];
                if (roll < 0) return blocks[i];
            }
            return blocks[0];
        }
    }

    public static double strataOffset(long seed, int x, int z) {
        return 5.0 * SimplexNoise.noise2(seed + SEED_STRATA, x / 80.0, z / 80.0)
                + 1.5 * SimplexNoise.noise2(seed + SEED_STRATA + 1, x / 19.0, z / 19.0);
    }

    public static BlockState strata(long seed, int x, int y, int z, double offset, Province province) {
        double shiftedY = y + offset;
        long band = (long) Math.floor(shiftedY / STRATA_UNIT);
        double withinBand = shiftedY - band * STRATA_UNIT;
        double roll = NoiseHelper.hash01(seed + SEED_STRATA, band, province.ordinal());
        boolean interbed = withinBand > STRATA_UNIT - 2.0
                && NoiseHelper.hash01(seed + SEED_STRATA + 2, band, province.ordinal()) < 0.45;
        if (interbed) {
            roll = NoiseHelper.hash01(seed + SEED_STRATA + 3, band, province.ordinal());
        }
        return palette(seed, x, y, z, offset, province).pick(roll);
    }

    public static BlockState weathered(long seed, int x, int y, int z, double offset, Province province) {
        double patches = SimplexNoise.noise3(seed + SEED_STRATA + 20, x / 36.0, y / 48.0, z / 36.0) * 0.5 + 0.5;
        double grain = 0.08 * SimplexNoise.noise2(seed + SEED_STRATA + 21, x / 9.0, z / 9.0);
        double roll = NoiseHelper.clamp(patches + grain, 0.0, 0.999);
        return palette(seed, x, y, z, offset, province).pick(roll);
    }

    private static Strata palette(long seed, int x, int y, int z, double offset, Province province) {
        return switch (province) {
            case SEA_CLIFFS -> BASALT;
            case DOLOMITES -> DOLOMITE;
            case TETON -> GRANITE;
            case GRANITE_VALLEY -> PALE_GRANITE;
            case CANYONLANDS -> {
                double contact = SEA_LEVEL + 62 + 10 * SimplexNoise.noise2(seed + SEED_STRATA + 5, x / 300.0, z / 300.0);
                yield y + offset * 0.4 > contact ? WHITE_ROCK : RED_ROCK;
            }
            case DESERT, STEPPE -> DESERT_ROCK;
            case JUNGLE -> KARST;
            case SAVANNA, BOREAL, TUNDRA, PRAIRIE -> SHIELD;
            default -> GREY;
        };
    }

    static BlockState volcanic(long seed, int y, double offset) {
        long band = (long) Math.floor((y + offset) / 4.0);
        return BASALT.pick(NoiseHelper.hash01(seed + SEED_STRATA + 9, band, 0));
    }

    static boolean dampCliffs(HeightField.Column col) {
        if (col.rain < 0.3 || col.temperature < 0.15) return false;
        Province p = col.province;
        return p == Province.SEA_CLIFFS || p == Province.FJORDLAND || p == Province.CASCADIA
                || p == Province.JUNGLE || (col.riverDist < 24 && p != Province.CANYONLANDS);
    }

    static boolean mossyFace(long seed, int x, int y, int z, HeightField.Column col) {
        if (!dampCliffs(col)) return false;
        double belowTop = col.height - y;
        double creep = 0.25 * (1.0 - NoiseHelper.smoothstep(0.0, 25.0, belowTop));
        double spray = col.riverDist < 24 ? 0.15 : 0.0;
        return mossiness(seed, x, y, z, col) + creep + spray > 0.72;
    }

    static double mossiness(long seed, int x, int y, int z, HeightField.Column col) {
        double patches = SimplexNoise.noise3(seed + SEED_SURFACE, x / 32.0, y / 16.0, z / 32.0) * 0.5 + 0.5;
        double damp = (col.moisture - 0.5) * 0.4;
        double gorge = (1.0 - NoiseHelper.smoothstep(0.0, 40.0, col.riverDist)) * (0.2 + 0.3 * col.canyon);
        double climate = switch (col.province) {
            case CASCADIA -> 0.2;
            case FJORDLAND, SEA_CLIFFS -> 0.08;
            case CANYONLANDS -> -0.6;
            case TETON, DOLOMITES, GRANITE_VALLEY -> -0.15;
            default -> 0.0;
        };
        return patches + damp + gorge + climate;
    }

    static BlockState topsoil(HeightField.Column col, double soilNoise, double slope) {
        BlockState soil = flatTopsoil(col, soilNoise);
        boolean bareSoil = soil == PODZOL || soil == COARSE_DIRT;
        boolean arid = col.province == Province.CANYONLANDS || col.province == Province.DESERT;
        return bareSoil && !arid && slope > 0.7 ? GRASS : soil;
    }

    private static BlockState flatTopsoil(HeightField.Column col, double soilNoise) {
        return switch (col.province) {
            case CANYONLANDS -> {
                double green = col.moisture + (col.riverDist < 30 ? 0.4 : 0.0) + soilNoise * 0.25;
                yield green > 0.62 ? GRASS : soilNoise > 0.1 ? RED_SAND : COARSE_DIRT;
            }
            case CASCADIA -> soilNoise < -0.75 ? PODZOL : GRASS;
            case TETON -> soilNoise < -0.75 ? COARSE_DIRT : GRASS;
            case DESERT -> {
                if (col.riverDist < 6) yield GRASS;
                yield soilNoise > 0.25 ? SAND : soilNoise > -0.3 ? GRAVEL : COARSE_DIRT;
            }
            case STEPPE -> soilNoise < -0.35 ? COARSE_DIRT : GRASS;
            case SAVANNA -> soilNoise < -0.5 ? COARSE_DIRT : soilNoise > 0.7 ? RED_SAND : GRASS;
            case JUNGLE -> soilNoise < -0.65 ? PODZOL : col.riverDist < 4 ? MUD : GRASS;
            case BOREAL -> soilNoise < -0.55 ? PODZOL : soilNoise > 0.6 ? MOSS : GRASS;
            case TUNDRA -> soilNoise < -0.4 ? COARSE_DIRT : soilNoise > 0.55 ? MOSS : GRASS;
            default -> soilNoise < -0.8 ? COARSE_DIRT : GRASS;
        };
    }

    static BlockState subsoil(BlockState surface, HeightField.Column col) {
        boolean canyon = col.province == Province.CANYONLANDS;
        if (surface == RED_SAND) return canyon ? TERRACOTTA : RED_SAND;
        if (surface == GRASS || surface == COARSE_DIRT || surface == SNOW || surface == PODZOL) {
            return canyon ? COARSE_DIRT : DIRT;
        }
        if (surface == SAND) return SAND;
        if (surface == MUD) return MUD;
        if (surface == MOSS) return DIRT;
        if (surface == GRAVEL) return GRAVEL;
        if (surface == CLAY) return CLAY;
        return null;
    }
}
