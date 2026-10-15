package com.reveny.trailhead.debug;

import com.reveny.trailhead.noise.NoiseConfig;
import com.reveny.trailhead.worldgen.terrain.CaveType;
import com.reveny.trailhead.worldgen.terrain.ChunkBuilder;
import com.reveny.trailhead.worldgen.terrain.HeightField;
import com.reveny.trailhead.worldgen.terrain.TerrainContext;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class CaveViewFinder {
    private static final int HEIGHT = NoiseConfig.GEN_DEPTH;
    private static final int SEARCH_RADIUS = 640;
    private static final int SEARCH_STEP = 64;
    private static final Map<CaveType, String> ANCHORS = Map.of(
            CaveType.DRIPSTONE, "p_dolomites",
            CaveType.LUSH, "p_jungle",
            CaveType.DESERT, "p_desert",
            CaveType.ICE, "p_tundra",
            CaveType.DEEP_DARK, "");

    private CaveViewFinder() {}

    static List<String> find(TerrainContext ctx, List<String> regionalViews, int centerX, int centerZ) {
        List<String> views = new ArrayList<>();
        for (Map.Entry<CaveType, String> anchor : ANCHORS.entrySet()) {
            int[] origin = anchorPosition(regionalViews, anchor.getValue(), centerX, centerZ);
            String view = bestChamber(ctx, anchor.getKey(), origin[0], origin[1]);
            if (view != null) views.add(view);
        }
        return views;
    }

    private static int[] anchorPosition(List<String> regionalViews, String name, int centerX, int centerZ) {
        for (String view : regionalViews) {
            String[] parts = view.split(" ");
            if (parts[0].equals(name)) return new int[]{(int) Double.parseDouble(parts[1]), (int) Double.parseDouble(parts[3])};
        }
        return new int[]{centerX, centerZ};
    }

    private static String bestChamber(TerrainContext ctx, CaveType type, int originX, int originZ) {
        int bestScore = 0;
        int[] best = null;
        for (int x = originX - SEARCH_RADIUS; x <= originX + SEARCH_RADIUS; x += SEARCH_STEP) {
            for (int z = originZ - SEARCH_RADIUS; z <= originZ + SEARCH_RADIUS; z += SEARCH_STEP) {
                int chunkX = Math.floorDiv(x, 16) * 16, chunkZ = Math.floorDiv(z, 16) * 16;
                int[] chamber = largestChamber(ctx, type, chunkX, chunkZ);
                if (chamber != null && chamber[3] > bestScore) {
                    bestScore = chamber[3];
                    best = chamber;
                }
            }
        }
        if (best == null) return null;
        return String.format(Locale.ROOT, "cave_%s %.1f %.1f %.1f %.1f %.1f 6000 nv",
                type.name().toLowerCase(Locale.ROOT), best[0] + 0.5, best[1] + 0.2, best[2] + 0.5, (float) best[4], 10.0f);
    }

    private static int[] largestChamber(TerrainContext ctx, CaveType type, int chunkX, int chunkZ) {
        BlockState[] blocks = new BlockState[16 * 16 * HEIGHT];
        ChunkBuilder builder = new ChunkBuilder(ctx, chunkX, chunkZ);
        builder.build((lx, y, lz, state) -> blocks[(lx * 16 + lz) * HEIGHT + (y - NoiseConfig.MIN_Y)] = state);
        int[] top = new int[256];
        for (int c = 0; c < 256; c++) {
            top[c] = -1;
            for (int i = HEIGHT - 1; i >= 0; i--) {
                BlockState state = blocks[c * HEIGHT + i];
                if (state != null && state.isSolid()) {
                    top[c] = i;
                    break;
                }
            }
        }
        int[] best = null;
        for (int lx = 4; lx < 12; lx += 2) {
            for (int lz = 4; lz < 12; lz += 2) {
                HeightField.Column col = builder.column(lx, lz);
                for (int i = 8; i < top[lx * 16 + lz] - 12; i += 2) {
                    if (!isAir(blocks, lx, i, lz) || !floorBelow(blocks, lx, i, lz)) continue;
                    int y = i + NoiseConfig.MIN_Y;
                    if (CaveType.at(ctx.seed, col, chunkX + lx, y, chunkZ + lz) != type) continue;
                    int score = openVolume(blocks, lx, i, lz);
                    if (best == null || score > best[3]) {
                        best = new int[]{chunkX + lx, y + 1, chunkZ + lz, score, longestView(blocks, lx, i + 1, lz)};
                    }
                }
            }
        }
        return best;
    }

    private static boolean isAir(BlockState[] blocks, int lx, int i, int lz) {
        if (lx < 0 || lz < 0 || lx > 15 || lz > 15 || i < 0 || i >= HEIGHT) return false;
        return blocks[(lx * 16 + lz) * HEIGHT + i] == null;
    }

    private static boolean floorBelow(BlockState[] blocks, int lx, int i, int lz) {
        for (int k = 1; k <= 3; k++) {
            if (i - k < 0) return false;
            BlockState below = blocks[(lx * 16 + lz) * HEIGHT + i - k];
            if (below == null) continue;
            return below.isSolid();
        }
        return false;
    }

    private static int openVolume(BlockState[] blocks, int lx, int i, int lz) {
        int open = 0;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = 0; dy <= 5; dy++) {
                    if (isAir(blocks, lx + dx, i + dy, lz + dz)) open++;
                }
            }
        }
        return open;
    }

    private static int longestView(BlockState[] blocks, int lx, int i, int lz) {
        int bestYaw = 0, bestRun = -1;
        for (int d = 0; d < 8; d++) {
            double angle = d * Math.PI / 4;
            double dx = -Math.sin(angle), dz = Math.cos(angle);
            int run = 0;
            while (run < 16 && isAir(blocks, (int) Math.round(lx + dx * run), i, (int) Math.round(lz + dz * run))) run++;
            if (run > bestRun) {
                bestRun = run;
                bestYaw = d * 45;
            }
        }
        return bestYaw;
    }
}
