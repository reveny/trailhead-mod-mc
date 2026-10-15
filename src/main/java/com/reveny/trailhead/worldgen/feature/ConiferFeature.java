package com.reveny.trailhead.worldgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

public class ConiferFeature extends Feature<ConiferFeature.Config> {
    private static final int FLAGS = 19;
    private static final int MAX_FOOTING = 8;

    public record Config(BlockState log, BlockState leaves, IntProvider height, boolean giant, float crown,
                         float radius, float belly, int whorl, int droop, float density, boolean dead)
            implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockState.CODEC.fieldOf("log").forGetter(Config::log),
                BlockState.CODEC.fieldOf("leaves").forGetter(Config::leaves),
                IntProvider.CODEC.fieldOf("height").forGetter(Config::height),
                Codec.BOOL.optionalFieldOf("giant", false).forGetter(Config::giant),
                Codec.floatRange(0.0f, 1.0f).optionalFieldOf("crown", 0.8f).forGetter(Config::crown),
                Codec.floatRange(0.5f, 8.0f).optionalFieldOf("radius", 3.0f).forGetter(Config::radius),
                Codec.floatRange(0.1f, 1.0f).optionalFieldOf("belly", 0.85f).forGetter(Config::belly),
                Codec.intRange(1, 4).optionalFieldOf("whorl", 2).forGetter(Config::whorl),
                Codec.intRange(0, 2).optionalFieldOf("droop", 0).forGetter(Config::droop),
                Codec.floatRange(0.0f, 1.0f).optionalFieldOf("density", 0.8f).forGetter(Config::density),
                Codec.BOOL.optionalFieldOf("dead", false).forGetter(Config::dead)
        ).apply(i, Config::new));
    }

    public ConiferFeature(Codec<Config> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        Config config = context.config();
        int height = config.height().sample(random);
        int width = config.giant() ? 2 : 1;
        if (origin.getY() + height + 3 > level.getMaxY()) return false;
        for (int y = 0; y <= height; y++) {
            for (int dx = 0; dx < width; dx++) {
                for (int dz = 0; dz < width; dz++) {
                    if (y < 2 && (dx > 0 || dz > 0)) continue;
                    if (!TreeFeature.validTreePos(level, origin.offset(dx, y, dz))) return false;
                }
            }
        }
        BlockState log = config.log();
        int narrow = config.giant() ? Math.round(height * 0.72f) : height;
        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < width; dz++) {
                footing(level, origin.offset(dx, 0, dz), log);
            }
        }
        if (config.giant()) roots(level, random, origin, log);
        int trunkTop = config.dead() ? height - random.nextInt(Math.max(1, height / 4)) : height - 1;
        for (int y = 0; y < trunkTop; y++) {
            for (int dx = 0; dx < width; dx++) {
                for (int dz = 0; dz < width; dz++) {
                    if (y >= narrow && (dx > 0 || dz > 0)) continue;
                    setIfFree(level, origin.offset(dx, y, dz), log);
                }
            }
        }
        if (config.dead()) {
            stubs(level, random, origin, trunkTop, log);
            return true;
        }
        crown(level, random, origin, config, height, narrow);
        return true;
    }

    private void crown(WorldGenLevel level, RandomSource random, BlockPos origin, Config config, int height, int narrow) {
        BlockState leaves = config.leaves().hasProperty(LeavesBlock.PERSISTENT)
                ? config.leaves().setValue(LeavesBlock.PERSISTENT, true)
                : config.leaves();
        int crownHeight = Math.max(3, Math.round(height * config.crown()));
        int bottom = height - crownHeight;
        int whorlOffset = random.nextInt(config.whorl());
        float belly = config.belly();
        for (int y = height + 1; y >= bottom; y--) {
            int depth = height + 1 - y;
            float t = depth / (float) (crownHeight + 1);
            float profile = t < belly ? t / belly : 1.0f - (t - belly) / (1.0f - belly) * 0.4f;
            float radius = 0.5f + (config.radius() - 0.5f) * profile;
            boolean full = (depth + whorlOffset) % config.whorl() == 0;
            if (!full) radius *= 0.62f;
            radius += (random.nextFloat() - 0.5f) * 0.6f;
            boolean thick = config.giant() && y < narrow;
            float centre = thick ? 0.5f : 0.0f;
            radius = Math.max(radius, (thick ? 1.6f : 1.05f) + (full ? 0.45f : 0.0f));
            if (depth <= 2) radius = 0.4f;
            disc(level, random, origin.getX(), y + origin.getY(), origin.getZ(), centre, radius, full, config, leaves);
            if (config.giant() && full && radius >= 3.5f && y < narrow && random.nextFloat() < 0.6f) {
                branch(level, random, origin.above(y), (int) (radius * 0.55f), config.log());
            }
        }
    }

    private void disc(WorldGenLevel level, RandomSource random, int x, int y, int z, float centre, float radius,
                      boolean full, Config config, BlockState leaves) {
        int reach = (int) Math.ceil(radius + centre) + 1;
        float phase = random.nextFloat() * (float) (Math.PI * 2.0);
        int lobes = 2 + random.nextInt(3);
        float wobble = Math.min(0.2f, 0.05f * radius);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                float fx = dx - centre, fz = dz - centre;
                float dist = (float) Math.sqrt(fx * fx + fz * fz);
                float angle = (float) Math.atan2(fz, fx);
                float edge = radius * (1.0f + wobble * (float) Math.sin(lobes * angle + phase));
                if (dist > edge + 0.25f) continue;
                boolean rim = dist > edge - 0.9f;
                if (rim && radius > 1.0f && random.nextFloat() > config.density()) continue;
                pos.set(x + dx, y, z + dz);
                setIfFree(level, pos, leaves);
                if (full && rim && radius >= 2.2f) {
                    for (int k = 1; k <= config.droop(); k++) {
                        if (k > 1 && random.nextFloat() < 0.5f) break;
                        pos.set(x + dx, y - k, z + dz);
                        setIfFree(level, pos, leaves);
                    }
                }
            }
        }
    }

    private void branch(WorldGenLevel level, RandomSource random, BlockPos at, int length, BlockState log) {
        Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        BlockState state = sideways(log, direction);
        BlockPos start = at.offset(direction.getStepX() > 0 ? 1 : 0, 0, direction.getStepZ() > 0 ? 1 : 0);
        for (int i = 1; i <= length; i++) {
            BlockPos p = start.relative(direction, i);
            if (!TreeFeature.validTreePos(level, p)) return;
            level.setBlock(p, state, FLAGS);
        }
    }

    private void stubs(WorldGenLevel level, RandomSource random, BlockPos origin, int top, BlockState log) {
        for (int y = top * 2 / 5; y < top - 1; y += 2 + random.nextInt(2)) {
            if (random.nextFloat() > 0.45f) continue;
            Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            BlockState state = sideways(log, direction);
            int length = 1 + (y < top * 0.7f && random.nextBoolean() ? 1 : 0);
            for (int i = 1; i <= length; i++) {
                BlockPos p = origin.above(y).relative(direction, i);
                if (!TreeFeature.validTreePos(level, p)) break;
                level.setBlock(p, state, FLAGS);
            }
        }
    }

    private void roots(WorldGenLevel level, RandomSource random, BlockPos origin, BlockState log) {
        for (int dx = -1; dx <= 2; dx++) {
            for (int dz = -1; dz <= 2; dz++) {
                boolean inside = dx >= 0 && dx <= 1 && dz >= 0 && dz <= 1;
                boolean corner = (dx == -1 || dx == 2) && (dz == -1 || dz == 2);
                if (inside || corner || random.nextFloat() > 0.35f) continue;
                BlockPos p = origin.offset(dx, 0, dz);
                if (!TreeFeature.validTreePos(level, p)) continue;
                level.setBlock(p, log, FLAGS);
                footing(level, p, log);
            }
        }
    }

    private void footing(WorldGenLevel level, BlockPos base, BlockState log) {
        BlockPos.MutableBlockPos pos = base.below().mutable();
        for (int i = 0; i < MAX_FOOTING && TreeFeature.validTreePos(level, pos); i++) {
            level.setBlock(pos, log, FLAGS);
            pos.move(Direction.DOWN);
        }
        if (level.getBlockState(pos).is(Blocks.GRASS_BLOCK)) level.setBlock(pos, Blocks.DIRT.defaultBlockState(), FLAGS);
    }

    private static BlockState sideways(BlockState log, Direction direction) {
        return log.hasProperty(RotatedPillarBlock.AXIS) ? log.setValue(RotatedPillarBlock.AXIS, direction.getAxis()) : log;
    }

    private static void setIfFree(WorldGenLevel level, BlockPos pos, BlockState state) {
        if (TreeFeature.validTreePos(level, pos)) level.setBlock(pos, state, FLAGS);
    }
}
