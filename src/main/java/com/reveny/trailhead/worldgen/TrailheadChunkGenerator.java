package com.reveny.trailhead.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.reveny.trailhead.TrailheadMod;
import com.reveny.trailhead.noise.NoiseConfig;
import com.reveny.trailhead.worldgen.terrain.ChunkBuilder;
import com.reveny.trailhead.worldgen.terrain.Climate;
import com.reveny.trailhead.worldgen.terrain.HeightField;
import com.reveny.trailhead.worldgen.terrain.TerrainContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

public class TrailheadChunkGenerator extends ChunkGenerator {
    public static final MapCodec<TrailheadChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> generator.biomeSource),
                    Codec.STRING.optionalFieldOf("climate", "latitude").forGetter(generator -> generator.mode.id())
            ).apply(instance, (source, climate) -> new TrailheadChunkGenerator(source, Climate.Mode.parse(climate)))
    );

    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final int TIMING_INTERVAL = 500;

    private static final AtomicLong CHUNKS = new AtomicLong();
    private static final AtomicLong NANOS = new AtomicLong();

    private final Climate.Mode mode;

    public TrailheadChunkGenerator(BiomeSource biomeSource, Climate.Mode mode) {
        super(biomeSource);
        this.mode = mode;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    private TerrainContext terrain() {
        return TerrainContext.get(TrailheadMod.worldSeed, mode);
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState,
                                                         StructureManager structureManager, ChunkAccess chunk) {
        long start = System.nanoTime();
        ChunkPos pos = chunk.getPos();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        new ChunkBuilder(terrain(), pos.getMinBlockX(), pos.getMinBlockZ()).build(new ChunkBuilder.BlockSink() {
            @Override
            public void set(int localX, int y, int localZ, BlockState state) {
                LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
                section.setBlockState(localX, y & 15, localZ, state, false);
            }

            @Override
            public void scheduleFluid(int localX, int y, int localZ) {
                chunk.markPosForPostprocessing(mutable.set(pos.getMinBlockX() + localX, y, pos.getMinBlockZ() + localZ));
            }
        });
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
        logTiming(System.nanoTime() - start);
        return CompletableFuture.completedFuture(chunk);
    }

    private static void logTiming(long nanos) {
        long total = NANOS.addAndGet(nanos);
        long count = CHUNKS.incrementAndGet();
        if (count % TIMING_INTERVAL == 0) {
            TrailheadMod.LOGGER.debug("{} chunks, avg fill {} ms", count, String.format("%.2f", total / 1e6 / count));
        }
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager,
                             RandomState randomState, ChunkAccess chunk) {
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState,
                             BiomeManager biomeManager, StructureManager structureManager,
                             ChunkAccess chunk) {
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        ChunkPos chunkPos = region.getCenter();
        Holder<Biome> biome = region.getBiome(chunkPos.getWorldPosition().atY(region.getMaxY()));
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
        random.setDecorationSeed(region.getSeed(), chunkPos.getMinBlockX(), chunkPos.getMinBlockZ());
        NaturalSpawner.spawnMobsForChunkGeneration(region, biome, chunkPos, random);
    }

    private static int solidTop(HeightField.Column col) {
        return (int) Math.ceil(col.height - 0.5);
    }

    private static int waterTop(HeightField.Column col) {
        double water = col.inRiver() ? Math.max(col.water, NoiseConfig.SEA_LEVEL) : NoiseConfig.SEA_LEVEL;
        return (int) Math.floor(water) + 1;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types heightmapType,
                             LevelHeightAccessor level, RandomState randomState) {
        HeightField.Column col = terrain().field.sample(x, z);
        int top = solidTop(col);
        if (heightmapType == Heightmap.Types.OCEAN_FLOOR_WG || heightmapType == Heightmap.Types.OCEAN_FLOOR) {
            return top;
        }
        return Math.max(top, waterTop(col));
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        HeightField.Column col = terrain().field.sample(x, z);
        int top = solidTop(col);
        int water = waterTop(col);
        int minY = level.getMinY();
        BlockState[] states = new BlockState[level.getHeight()];
        for (int i = 0; i < states.length; i++) {
            int y = minY + i;
            states[i] = y < top ? STONE : y < water ? WATER : AIR;
        }
        return new NoiseColumn(minY, states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos) {
        HeightField.Column col = terrain().field.sample(pos.getX(), pos.getZ());
        info.add(String.format("Trailhead %s temp=%.2f rain=%.2f coast=%.0f mtn=%.2f river=%.1f",
                col.province, col.temperature, col.rain, col.coastDist, col.mountain, col.riverDist));
    }

    @Override
    public int getSeaLevel() {
        return NoiseConfig.SEA_LEVEL;
    }

    @Override
    public int getMinY() {
        return NoiseConfig.MIN_Y;
    }

    @Override
    public int getGenDepth() {
        return NoiseConfig.GEN_DEPTH;
    }
}
