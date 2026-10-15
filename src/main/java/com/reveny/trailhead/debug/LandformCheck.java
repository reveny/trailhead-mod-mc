package com.reveny.trailhead.debug;

import com.reveny.trailhead.noise.NoiseConfig;
import com.reveny.trailhead.noise.NoiseHelper;
import com.reveny.trailhead.worldgen.terrain.ChunkBuilder;
import com.reveny.trailhead.worldgen.terrain.HeightField;
import com.reveny.trailhead.worldgen.terrain.TerrainContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

final class LandformCheck {
    private static final int HEIGHT = NoiseConfig.GEN_DEPTH;

    private LandformCheck() {}

    static boolean run(TerrainContext ctx, long seed, int chunks) {
        int[][] origins = randomChunkOrigins(seed, chunks);
        boolean noSpikes = checkShapes(ctx, origins);
        checkAbruptRises(ctx, seed, chunks * 20);
        boolean seamless = checkSeams(ctx, origins);
        return noSpikes && seamless;
    }

    private static int[][] randomChunkOrigins(long seed, int chunks) {
        Random random = new Random(seed ^ 0x5EED);
        int[][] origins = new int[chunks][2];
        for (int c = 0; c < chunks; c++) {
            origins[c][0] = (random.nextInt(1500) - 750) * 16;
            origins[c][1] = (random.nextInt(1500) - 750) * 16;
        }
        return origins;
    }

    private static BlockState[] buildChunk(TerrainContext ctx, int x, int z) {
        BlockState[] blocks = new BlockState[16 * 16 * HEIGHT];
        new ChunkBuilder(ctx, x, z).build((lx, y, lz, state) -> blocks[(lx * 16 + lz) * HEIGHT + (y - NoiseConfig.MIN_Y)] = state);
        return blocks;
    }

    private static boolean checkShapes(TerrainContext ctx, int[][] origins) {
        AtomicLong spikes = new AtomicLong(), overhangs = new AtomicLong();
        AtomicLong dirtFaces = new AtomicLong(), mossFaces = new AtomicLong(), nanos = new AtomicLong();
        IntStream.range(0, origins.length).parallel().forEach(c -> {
            long start = System.nanoTime();
            BlockState[] blocks = buildChunk(ctx, origins[c][0], origins[c][1]);
            nanos.addAndGet(System.nanoTime() - start);
            int[] top = new int[256];
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    int t = -1;
                    for (int i = HEIGHT - 1; i >= 0; i--) {
                        if (rock(blocks[(lx * 16 + lz) * HEIGHT + i])) {
                            t = i;
                            break;
                        }
                    }
                    top[lx * 16 + lz] = t;
                }
            }
            for (int lx = 1; lx < 15; lx++) {
                for (int lz = 1; lz < 15; lz++) {
                    int t = top[lx * 16 + lz];
                    int neighbourMax = Math.max(Math.max(top[(lx - 1) * 16 + lz], top[(lx + 1) * 16 + lz]),
                            Math.max(top[lx * 16 + lz - 1], top[lx * 16 + lz + 1]));
                    if (t - neighbourMax >= 8) spikes.incrementAndGet();
                    int base = (lx * 16 + lz) * HEIGHT;
                    int air = 0;
                    for (int i = 1; i <= t; i++) {
                        BlockState state = blocks[base + i];
                        if (!rock(state)) {
                            air++;
                        } else {
                            if (air >= 4 && i >= t - NoiseConfig.CAVE_MIN_COVER) overhangs.incrementAndGet();
                            air = 0;
                        }
                        if (state != null && (state.is(Blocks.DIRT) || state.is(Blocks.MOSS_BLOCK))) {
                            boolean exposed = open(blocks[((lx - 1) * 16 + lz) * HEIGHT + i]) || open(blocks[((lx + 1) * 16 + lz) * HEIGHT + i])
                                    || open(blocks[(lx * 16 + lz - 1) * HEIGHT + i]) || open(blocks[(lx * 16 + lz + 1) * HEIGHT + i]);
                            if (exposed) (state.is(Blocks.DIRT) ? dirtFaces : mossFaces).incrementAndGet();
                        }
                    }
                }
            }
        });
        double columns = origins.length * 196 / 10000.0;
        System.out.printf(Locale.ROOT, "check %d chunks: spikes=%.1f overhangs=%.1f exposedDirt=%.1f exposedMoss=%.1f per 10k columns; %.2f ms/chunk%n",
                origins.length, spikes.get() / columns, overhangs.get() / columns, dirtFaces.get() / columns,
                mossFaces.get() / columns, nanos.get() / 1e6 / origins.length);
        return spikes.get() == 0;
    }

    private static void checkAbruptRises(TerrainContext ctx, long seed, int samples) {
        AtomicLong abrupt = new AtomicLong();
        IntStream.range(0, samples).parallel().forEach(k -> {
            double x = (NoiseHelper.hash01(seed + 77, k, 0) - 0.5) * 48000;
            double z = (NoiseHelper.hash01(seed + 78, k, 0) - 0.5) * 48000;
            HeightField.Column col = ctx.field.sampleWithoutRivers(x, z);
            if (col.height < NoiseConfig.SEA_LEVEL + 2 || col.coastDist < 60) return;
            double highest = col.height;
            for (int a = 0; a < 8; a++) {
                double angle = a * Math.PI / 4;
                highest = Math.max(highest, ctx.field.sampleWithoutRivers(x + Math.cos(angle) * 48, z + Math.sin(angle) * 48).height);
            }
            if (highest - col.height >= 70) abrupt.incrementAndGet();
        });
        System.out.printf(Locale.ROOT, "abrupt rises (70+ blocks within 48): %.1f per 10k inland samples%n", abrupt.get() * 10000.0 / samples);
    }

    private static boolean checkSeams(TerrainContext ctx, int[][] origins) {
        AtomicLong differing = new AtomicLong(), shared = new AtomicLong();
        IntStream.range(0, Math.min(origins.length, 300)).parallel().forEach(c -> {
            BlockState[] a = buildChunk(ctx, origins[c][0], origins[c][1]);
            BlockState[] b = buildChunk(ctx, origins[c][0] + 8, origins[c][1] + 8);
            for (int lx = 8; lx < 16; lx++) {
                for (int lz = 8; lz < 16; lz++) {
                    int ia = (lx * 16 + lz) * HEIGHT, ib = ((lx - 8) * 16 + (lz - 8)) * HEIGHT;
                    boolean differs = false;
                    for (int i = 0; i < HEIGHT && !differs; i++) differs = rock(a[ia + i]) != rock(b[ib + i]);
                    shared.incrementAndGet();
                    if (differs) differing.incrementAndGet();
                }
            }
        });
        System.out.printf(Locale.ROOT, "seam: %d of %d shared columns differ between overlapping builds%n", differing.get(), shared.get());
        return differing.get() == 0;
    }

    static void inspectWaterfall(TerrainContext ctx, int x, int z) {
        double[] tallest = null;
        for (double[] fall : ctx.rivers.waterfalls(x, z)) {
            if (tallest == null || fall[5] > tallest[5]) tallest = fall;
        }
        if (tallest == null) {
            System.out.println("no waterfall");
            return;
        }
        System.out.printf(Locale.ROOT, "fall a=(%.0f,%.0f) b=(%.0f,%.0f) level=%.0f drop=%.1f%n",
                tallest[0], tallest[1], tallest[2], tallest[3], tallest[4], tallest[5]);
        double dx = tallest[2] - tallest[0], dz = tallest[3] - tallest[1], length = Math.hypot(dx, dz);
        dx /= length;
        dz /= length;
        for (int s = -24; s <= 24; s += 2) {
            HeightField.Column col = ctx.field.sample(tallest[0] + dx * s, tallest[1] + dz * s);
            System.out.printf(Locale.ROOT, "  s=%3d h=%6.1f water=%6.1f riverDist=%5.1f%n", s, col.height, col.water, col.riverDist);
        }
        int cx = Math.floorDiv((int) Math.floor(tallest[0]), 16) * 16, cz = Math.floorDiv((int) Math.floor(tallest[1]), 16) * 16;
        int[] counts = new int[2];
        new ChunkBuilder(ctx, cx, cz).build(new ChunkBuilder.BlockSink() {
            @Override
            public void set(int lx, int y, int lz, BlockState state) {
                if (state.is(Blocks.WATER)) counts[0]++;
            }

            @Override
            public void scheduleFluid(int lx, int y, int lz) {
                counts[1]++;
            }
        });
        System.out.printf(Locale.ROOT, "chunk %d,%d: water blocks=%d, scheduled to flow=%d%n", cx, cz, counts[0], counts[1]);
    }

    private static boolean open(BlockState state) {
        return state == null || state.isAir() || (!state.isSolid() && !state.is(Blocks.WATER));
    }

    private static boolean rock(BlockState state) {
        return state != null && !state.isAir() && !state.is(Blocks.WATER) && state.isSolid();
    }
}
