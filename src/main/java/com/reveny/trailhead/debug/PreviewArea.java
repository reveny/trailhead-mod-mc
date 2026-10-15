package com.reveny.trailhead.debug;

import com.reveny.trailhead.worldgen.terrain.ChunkBuilder;
import com.reveny.trailhead.worldgen.terrain.Province;
import com.reveny.trailhead.worldgen.terrain.TerrainContext;
import net.minecraft.world.level.block.Blocks;

import java.util.Arrays;
import java.util.stream.IntStream;

final class PreviewArea {
    final int x0, z0, width, depth;
    final int[] topY;
    final int[] topRgb;
    final int[] floorY;
    final boolean[] water;
    final Province[] province;

    private PreviewArea(int x0, int z0, int width, int depth) {
        this.x0 = x0;
        this.z0 = z0;
        this.width = width;
        this.depth = depth;
        int size = width * depth;
        topY = new int[size];
        topRgb = new int[size];
        floorY = new int[size];
        water = new boolean[size];
        province = new Province[size];
        Arrays.fill(topY, Integer.MIN_VALUE);
        Arrays.fill(floorY, Integer.MIN_VALUE);
    }

    static PreviewArea build(TerrainContext ctx, int x0, int z0, int width, int depth) {
        PreviewArea area = new PreviewArea(Math.floorDiv(x0, 16) * 16, Math.floorDiv(z0, 16) * 16,
                (width + 15) / 16 * 16, (depth + 15) / 16 * 16);
        int chunksX = area.width / 16, chunksZ = area.depth / 16;
        IntStream.range(0, chunksX * chunksZ).parallel().forEach(c -> area.buildChunk(ctx, c % chunksX, c / chunksX));
        return area;
    }

    private void buildChunk(TerrainContext ctx, int cx, int cz) {
        ChunkBuilder builder = new ChunkBuilder(ctx, x0 + cx * 16, z0 + cz * 16);
        builder.build((lx, y, lz, state) -> {
            if (!state.is(Blocks.WATER) && !state.isSolid()) return;
            int i = (cz * 16 + lz) * width + cx * 16 + lx;
            if (topY[i] == Integer.MIN_VALUE) {
                topY[i] = y;
                topRgb[i] = PreviewColors.of(state);
                water[i] = state.is(Blocks.WATER);
            }
            if (floorY[i] == Integer.MIN_VALUE && !state.is(Blocks.WATER)) {
                floorY[i] = y;
            }
        });
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                province[(cz * 16 + lz) * width + cx * 16 + lx] = builder.column(lx, lz).province;
            }
        }
    }

    boolean contains(int x, int z) {
        return x >= x0 && z >= z0 && x < x0 + width && z < z0 + depth;
    }

    int index(int x, int z) {
        return (z - z0) * width + (x - x0);
    }
}
