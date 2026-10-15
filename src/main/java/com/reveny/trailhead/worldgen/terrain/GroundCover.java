package com.reveny.trailhead.worldgen.terrain;

import com.reveny.trailhead.noise.NoiseHelper;
import com.reveny.trailhead.noise.SimplexNoise;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import static com.reveny.trailhead.noise.NoiseConfig.SEED_SURFACE;

final class GroundCover {
    record Plant(BlockState lower, BlockState upper) {
        static Plant single(Block block) {
            return new Plant(block.defaultBlockState(), null);
        }

        static Plant tall(Block block) {
            return new Plant(block.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER),
                    block.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
        }
    }

    private static final Plant SHORT_GRASS = Plant.single(Blocks.SHORT_GRASS);
    private static final Plant TALL_GRASS = Plant.tall(Blocks.TALL_GRASS);
    private static final Plant FERN = Plant.single(Blocks.FERN);
    private static final Plant LARGE_FERN = Plant.tall(Blocks.LARGE_FERN);
    private static final Plant BUSH = Plant.single(Blocks.BUSH);
    private static final Plant DRY_GRASS = Plant.single(Blocks.SHORT_DRY_GRASS);
    private static final Plant TALL_DRY_GRASS = Plant.single(Blocks.TALL_DRY_GRASS);
    private static final Plant DEAD_BUSH = Plant.single(Blocks.DEAD_BUSH);
    private static final Plant[] FLOWERS = {
            Plant.single(Blocks.DANDELION), Plant.single(Blocks.AZURE_BLUET), Plant.single(Blocks.OXEYE_DAISY),
            Plant.single(Blocks.CORNFLOWER), Plant.single(Blocks.POPPY), Plant.single(Blocks.ALLIUM)
    };

    private GroundCover() {}

    static boolean canHost(BlockState ground) {
        return ground == SurfacePalette.GRASS || ground == SurfacePalette.COARSE_DIRT || ground == SurfacePalette.PODZOL
                || ground == SurfacePalette.MOSS || ground == SurfacePalette.RED_SAND || ground == SurfacePalette.SAND;
    }

    static Plant pick(long seed, int x, int z, HeightField.Column col, BlockState ground, double slope) {
        if (!canHost(ground)) return null;
        double patch = SimplexNoise.noise2(seed + SEED_SURFACE + 5, x / 22.0, z / 22.0) * 0.5 + 0.5;
        double roll = NoiseHelper.hash01(seed + SEED_SURFACE + 6, x, z);
        double kind = NoiseHelper.hash01(seed + SEED_SURFACE + 7, x, z);
        boolean dryGround = ground == SurfacePalette.RED_SAND || ground == SurfacePalette.COARSE_DIRT;
        Province province = col.province;

        double density = density(col, patch);
        boolean stepped = slope > 0.8;
        if (stepped) density = Math.max(density, 0.85);
        if (roll > density) return null;
        if (stepped && kind < 0.55 && province != Province.CANYONLANDS && !dryGround) {
            return province == Province.CASCADIA || province == Province.FJORDLAND ? LARGE_FERN : TALL_GRASS;
        }

        if (ground == SurfacePalette.SAND) {
            if (province != Province.DESERT || col.dune > 0.5 || roll > density * 0.5) return null;
            return kind < 0.7 ? DRY_GRASS : DEAD_BUSH;
        }

        Plant regional = regionalPlant(seed, x, z, col, kind);
        if (regional != null) return regional;

        if (dryGround || province == Province.CANYONLANDS) {
            if (ground == SurfacePalette.GRASS && kind < 0.4) return SHORT_GRASS;
            return kind < 0.55 ? DRY_GRASS : kind < 0.85 ? TALL_DRY_GRASS : DEAD_BUSH;
        }
        return temperatePlant(seed, x, z, province, kind);
    }

    private static double density(HeightField.Column col, double patch) {
        return switch (col.province) {
            case CANYONLANDS -> 0.18 + 0.25 * patch;
            case TETON -> 0.45 + 0.35 * patch;
            case CASCADIA -> 0.6 + 0.35 * patch;
            case SEA_CLIFFS -> 0.35 + 0.35 * patch;
            case DESERT -> col.riverDist < 8 ? 0.7 : 0.03 + 0.06 * patch;
            case STEPPE -> 0.4 + 0.35 * patch;
            case SAVANNA -> 0.55 + 0.4 * patch;
            case PRAIRIE, JUNGLE -> 0.75 + 0.25 * patch;
            case BOREAL -> 0.35 + 0.4 * patch;
            case TUNDRA -> 0.25 + 0.3 * patch;
            case ICE_SHEET -> 0;
            default -> 0.45 + 0.45 * patch;
        };
    }

    private static Plant regionalPlant(long seed, int x, int z, HeightField.Column col, double kind) {
        return switch (col.province) {
            case DESERT -> {
                if (col.riverDist < 8) yield kind < 0.5 ? TALL_GRASS : SHORT_GRASS;
                yield kind < 0.6 ? DRY_GRASS : DEAD_BUSH;
            }
            case STEPPE -> kind < 0.45 ? DRY_GRASS : kind < 0.7 ? SHORT_GRASS : kind < 0.88 ? TALL_DRY_GRASS : BUSH;
            case SAVANNA -> kind < 0.4 ? TALL_DRY_GRASS : kind < 0.65 ? TALL_GRASS : kind < 0.9 ? DRY_GRASS : BUSH;
            case PRAIRIE -> {
                double flowerPatch = SimplexNoise.noise2(seed + SEED_SURFACE + 8, x / 18.0, z / 18.0);
                if (flowerPatch > 0.45 && kind < 0.35) yield flower(kind, 3);
                yield kind < 0.6 ? TALL_GRASS : SHORT_GRASS;
            }
            case JUNGLE -> kind < 0.35 ? LARGE_FERN : kind < 0.6 ? FERN : kind < 0.85 ? TALL_GRASS : BUSH;
            case BOREAL -> kind < 0.5 ? FERN : kind < 0.75 ? SHORT_GRASS : kind < 0.9 ? LARGE_FERN : BUSH;
            case TUNDRA -> kind < 0.6 ? SHORT_GRASS : kind < 0.85 ? DRY_GRASS : DEAD_BUSH;
            default -> null;
        };
    }

    private static Plant temperatePlant(long seed, int x, int z, Province province, double kind) {
        return switch (province) {
            case CASCADIA -> kind < 0.45 ? FERN : kind < 0.65 ? LARGE_FERN : kind < 0.9 ? SHORT_GRASS : BUSH;
            case TETON -> kind < 0.35 ? SHORT_GRASS : kind < 0.6 ? DRY_GRASS : kind < 0.88 ? BUSH : TALL_DRY_GRASS;
            case DOLOMITES, ALPINE_VALLEY -> {
                double flowerPatch = SimplexNoise.noise2(seed + SEED_SURFACE + 8, x / 12.0, z / 12.0);
                if (flowerPatch > 0.35 && kind < 0.55) yield wildflowers(seed, x, z);
                if (flowerPatch > 0.2 && kind < 0.25) yield flower(kind, 4);
                yield kind < 0.82 ? SHORT_GRASS : kind < 0.95 ? TALL_GRASS : BUSH;
            }
            case FJORDLAND -> kind < 0.6 ? SHORT_GRASS : kind < 0.8 ? FERN : kind < 0.92 ? TALL_GRASS : BUSH;
            default -> {
                double flowerPatch = SimplexNoise.noise2(seed + SEED_SURFACE + 8, x / 16.0, z / 16.0);
                if (flowerPatch > 0.5 && kind < 0.2) yield flower(kind, 5);
                yield kind < 0.7 ? SHORT_GRASS : kind < 0.9 ? TALL_GRASS : BUSH;
            }
        };
    }

    private static Plant flower(double kind, int spread) {
        return FLOWERS[(int) (kind * spread * FLOWERS.length) % FLOWERS.length];
    }

    private static Plant wildflowers(long seed, int x, int z) {
        int amount = 1 + (int) (NoiseHelper.hash01(seed + SEED_SURFACE + 9, x, z) * 4);
        Direction facing = Direction.from2DDataValue((int) (NoiseHelper.hash01(seed + SEED_SURFACE + 10, x, z) * 4));
        return new Plant(Blocks.WILDFLOWERS.defaultBlockState()
                .setValue(BlockStateProperties.FLOWER_AMOUNT, amount)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, facing), null);
    }
}
