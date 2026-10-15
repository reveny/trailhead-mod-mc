package com.reveny.trailhead.worldgen.terrain;

import com.reveny.trailhead.noise.NoiseHelper;
import com.reveny.trailhead.noise.SimplexNoise;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;

import static com.reveny.trailhead.noise.NoiseConfig.*;

public final class ChunkBuilder {
    public interface BlockSink {
        void set(int localX, int y, int localZ, BlockState state);

        default void scheduleFluid(int localX, int y, int localZ) {}
    }

    private static final int BORDER = FACE_WARP_RADIUS + 1;
    private static final int PADDED = 16 + 2 * BORDER;
    private static final int SHELL = 18;
    private static final int HEIGHT = GEN_DEPTH;
    private static final int CELL_WIDTH = 4;
    private static final int CELL_HEIGHT = 8;
    private static final int CORNERS = 7;
    private static final int CORNER_ORIGIN = -4;
    private static final int[][] SIDE_OFFSETS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] VINE_OFFSETS = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
    private static final Direction[] VINE_SIDES = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    private static final BlockState SEAGRASS = Blocks.SEAGRASS.defaultBlockState();
    private static final BlockState LILY_PAD = Blocks.LILY_PAD.defaultBlockState();

    private static final byte CAVE_NONE = 0;
    private static final byte CAVE_AIR = 1;
    private static final byte CAVE_WATER = 2;
    private static final byte CAVE_LAVA = 3;
    private static final byte CAVE_ICE = 4;

    private final TerrainContext ctx;
    private final long seed;
    private final int minX, minZ;
    private final HeightField.Column[][] cols = new HeightField.Column[PADDED][PADDED];
    private final double[][] heights = new double[PADDED][PADDED];
    private final boolean[] solid = new boolean[SHELL * SHELL * HEIGHT];
    private final byte[] cave = new byte[SHELL * SHELL * HEIGHT];
    private final BlockState[] veins = new BlockState[SHELL * SHELL * HEIGHT];
    private final int[] top = new int[SHELL * SHELL];
    private final double[] faceStrength = new double[SHELL * SHELL];

    public ChunkBuilder(TerrainContext ctx, int minX, int minZ) {
        this.ctx = ctx;
        this.seed = ctx.seed;
        this.minX = minX;
        this.minZ = minZ;
    }

    public HeightField.Column column(int localX, int localZ) {
        return cols[localX + BORDER][localZ + BORDER];
    }

    public void build(BlockSink sink) {
        for (int i = 0; i < PADDED; i++) {
            for (int j = 0; j < PADDED; j++) {
                HeightField.Column col = ctx.field.sample(minX - BORDER + i, minZ - BORDER + j);
                cols[i][j] = col;
                heights[i][j] = col.height;
            }
        }
        shape();
        removeFragments();
        carveCaves();
        paint(sink);
    }

    private static int index(int sx, int sz, int y) {
        return ((sx * SHELL + sz) * HEIGHT) + (y - MIN_Y);
    }

    private HeightField.Column shellColumn(int sx, int sz) {
        return cols[sx - 1 + BORDER][sz - 1 + BORDER];
    }

    private boolean isSolid(int sx, int sz, int y) {
        if (y < MIN_Y) return true;
        if (y >= MAX_Y) return false;
        return solid[index(sx, sz, y)];
    }

    private boolean isCave(int sx, int sz, int y) {
        if (y < MIN_Y || y >= MAX_Y) return false;
        return cave[index(sx, sz, y)] != CAVE_NONE;
    }

    private void shape() {
        double[][] dilated1 = new double[SHELL][SHELL];
        double[][] dilated2 = new double[SHELL][SHELL];
        double[][] eroded1 = new double[SHELL][SHELL];
        for (int sx = 0; sx < SHELL; sx++) {
            for (int sz = 0; sz < SHELL; sz++) {
                int i = sx - 1 + BORDER, j = sz - 1 + BORDER;
                double max1 = -Double.MAX_VALUE, max2 = -Double.MAX_VALUE, min1 = Double.MAX_VALUE;
                for (int di = -2; di <= 2; di++) {
                    for (int dj = -2; dj <= 2; dj++) {
                        int distanceSquared = di * di + dj * dj;
                        if (distanceSquared > 5) continue;
                        double h = heights[i + di][j + dj];
                        max2 = Math.max(max2, h);
                        if (distanceSquared <= 2) {
                            max1 = Math.max(max1, h);
                            min1 = Math.min(min1, h);
                        }
                    }
                }
                dilated1[sx][sz] = max1;
                dilated2[sx][sz] = max2;
                eroded1[sx][sz] = min1;
            }
        }
        for (int sx = 0; sx < SHELL; sx++) {
            for (int sz = 0; sz < SHELL; sz++) {
                shapeColumn(sx, sz, dilated1[sx][sz], dilated2[sx][sz], eroded1[sx][sz]);
            }
        }
    }

    private void shapeColumn(int sx, int sz, double dilated1, double dilated2, double eroded1) {
        int radius = FACE_WARP_RADIUS;
        int i = sx - 1 + BORDER, j = sz - 1 + BORDER;
        int wx = minX + sx - 1, wz = minZ + sz - 1;

        double low = Double.MAX_VALUE, high = -Double.MAX_VALUE;
        for (int di = -radius; di <= radius; di++) {
            for (int dj = -radius; dj <= radius; dj++) {
                double h = heights[i + di][j + dj];
                low = Math.min(low, h);
                high = Math.max(high, h);
            }
        }
        double meanGradient = (high - low) / (2.0 * radius);
        double faceMask = NoiseHelper.smoothstep(FACE_GRADIENT_START, FACE_GRADIENT_FULL, meanGradient);
        faceStrength[sx * SHELL + sz] = faceMask;
        double own = heights[i][j];
        double strataOffset = faceMask > 0 ? SurfacePalette.strataOffset(seed, wx, wz) : 0;
        boolean sandstone = cols[i][j].province == Province.CANYONLANDS;

        int yTop = Math.min(MAX_Y - 1, (int) Math.ceil(faceMask > 0 ? high : own) + 1);
        int yFace = (int) Math.floor(faceMask > 0 ? low : own) - 1;
        int highest = MIN_Y - 1;
        for (int y = MIN_Y; y <= yTop; y++) {
            boolean filled;
            if (y < yFace) {
                filled = true;
            } else if (faceMask <= 0) {
                filled = y + 0.5 < own;
            } else {
                double surface = faceSurface(wx, y, wz, strataOffset, faceMask, own, high, sandstone,
                        dilated1, dilated2, eroded1);
                filled = y + 0.5 < surface;
            }
            solid[index(sx, sz, y)] = filled;
            if (filled) highest = y;
        }
        top[sx * SHELL + sz] = highest;
    }

    private double faceSurface(int wx, int y, int wz, double strataOffset, double faceMask, double own, double high,
                               boolean sandstone, double dilated1, double dilated2, double eroded1) {
        double shiftedY = y + strataOffset;
        long band = (long) Math.floor(shiftedY / STRATA_UNIT);
        double withinBand = (shiftedY - band * STRATA_UNIT) / STRATA_UNIT;
        double hardness = NoiseHelper.hash01(seed + SEED_FACE + 5, band, 0) * 2 - 1;
        double along = SimplexNoise.noise3(seed + SEED_FACE + 3,
                wx / FACE_LEDGE_SCALE_XZ, y / FACE_LEDGE_SCALE_Y, wz / FACE_LEDGE_SCALE_XZ);
        boolean inBand = withinBand > 0.2 && withinBand < 0.8;
        if (faceMask <= 0.5 || !inBand || high - y < 2) return own;

        double prominence = hardness * (0.65 + 0.35 * along) * (sandstone ? 0.5 : 1.0);
        if (prominence > 0.55) return dilated2;
        if (prominence > 0.2) return dilated1;
        if (!sandstone && hardness < -0.5 && along > 0.1) return eroded1;
        return own;
    }

    private void removeFragments() {
        boolean[] before = solid.clone();
        for (int sx = 1; sx < SHELL - 1; sx++) {
            for (int sz = 1; sz < SHELL - 1; sz++) {
                int columnTop = top[sx * SHELL + sz];
                for (int y = columnTop; y > MIN_Y + 1; y--) {
                    if (!before[index(sx, sz, y)]) continue;
                    boolean airBelow = !before[index(sx, sz, y - 1)];
                    boolean airAbove = y + 1 >= MAX_Y || !before[index(sx, sz, y + 1)];
                    if (!airBelow && !airAbove) continue;
                    int neighbours = (before[index(sx - 1, sz, y)] ? 1 : 0) + (before[index(sx + 1, sz, y)] ? 1 : 0)
                            + (before[index(sx, sz - 1, y)] ? 1 : 0) + (before[index(sx, sz + 1, y)] ? 1 : 0);
                    if (neighbours == 0 || (airBelow && neighbours <= 1)) solid[index(sx, sz, y)] = false;
                }
                int y = columnTop;
                while (y >= MIN_Y && !solid[index(sx, sz, y)]) y--;
                top[sx * SHELL + sz] = y;
            }
        }
    }

    private void carveCaves() {
        int highestSurface = MIN_Y;
        for (double[] row : heights) {
            for (double h : row) highestSurface = Math.max(highestSurface, (int) Math.ceil(h));
        }
        int layers = (Math.min(MAX_Y - 1, highestSurface + CELL_HEIGHT) - MIN_Y) / CELL_HEIGHT + 2;
        double[][][] density = new double[CORNERS][CORNERS][layers];
        double[][][] toggle = new double[CORNERS][CORNERS][layers];
        sampleCorners(density, toggle, layers);

        for (int sx = 0; sx < SHELL; sx++) {
            for (int sz = 0; sz < SHELL; sz++) {
                carveColumn(sx, sz, density, toggle);
            }
        }
        sealCaves();
    }

    private void sampleCorners(double[][][] density, double[][][] toggle, int layers) {
        for (int ci = 0; ci < CORNERS; ci++) {
            for (int cj = 0; cj < CORNERS; cj++) {
                int lx = CORNER_ORIGIN + ci * CELL_WIDTH, lz = CORNER_ORIGIN + cj * CELL_WIDTH;
                double surface = heights[lx + BORDER][lz + BORDER];
                for (int k = 0; k < layers; k++) {
                    int y = MIN_Y + k * CELL_HEIGHT;
                    if (y > surface + CELL_HEIGHT) {
                        density[ci][cj][k] = 1.0;
                        continue;
                    }
                    density[ci][cj][k] = ctx.caves.density(minX + lx, y, minZ + lz, surface - y);
                    if (y <= 58) toggle[ci][cj][k] = ctx.caves.veinToggle(minX + lx, y, minZ + lz);
                }
            }
        }
    }

    private static double interpolate(double[][][] grid, int lx, int y, int lz) {
        int gx = lx - CORNER_ORIGIN, gy = y - MIN_Y, gz = lz - CORNER_ORIGIN;
        int ci = Math.floorDiv(gx, CELL_WIDTH), ck = Math.floorDiv(gy, CELL_HEIGHT), cj = Math.floorDiv(gz, CELL_WIDTH);
        double tx = (gx - ci * CELL_WIDTH) / (double) CELL_WIDTH;
        double ty = (gy - ck * CELL_HEIGHT) / (double) CELL_HEIGHT;
        double tz = (gz - cj * CELL_WIDTH) / (double) CELL_WIDTH;
        double x00 = NoiseHelper.lerp(grid[ci][cj][ck], grid[ci + 1][cj][ck], tx);
        double x10 = NoiseHelper.lerp(grid[ci][cj + 1][ck], grid[ci + 1][cj + 1][ck], tx);
        double x01 = NoiseHelper.lerp(grid[ci][cj][ck + 1], grid[ci + 1][cj][ck + 1], tx);
        double x11 = NoiseHelper.lerp(grid[ci][cj + 1][ck + 1], grid[ci + 1][cj + 1][ck + 1], tx);
        return NoiseHelper.lerp(NoiseHelper.lerp(x00, x10, tz), NoiseHelper.lerp(x01, x11, tz), ty);
    }

    private void carveColumn(int sx, int sz, double[][][] density, double[][][] toggle) {
        HeightField.Column col = shellColumn(sx, sz);
        int lx = sx - 1, lz = sz - 1;
        int x = minX + lx, z = minZ + lz;
        int surfaceTop = top[sx * SHELL + sz];
        boolean painted = sx >= 1 && sx <= 16 && sz >= 1 && sz <= 16;
        boolean underWater = col.inRiver() || col.height < SEA_LEVEL + 0.5;
        boolean wall = faceStrength[sx * SHELL + sz] > 0.5 || col.canyon > 0.6 || col.cliffiness >= 0.6;
        double lowestNearby = wall ? lowestNearbySurface(lx, lz) : Double.MAX_VALUE;
        boolean sinkhole = col.karst > 0.6
                && SimplexNoise.noise2(seed + SEED_CAVE + 10, x / 90.0, z / 90.0) > 0.8;
        int cover = underWater ? CAVE_MIN_COVER_UNDER_WATER : sinkhole ? 0 : CAVE_MIN_COVER;
        double geologyBias = CAVE_BASE_BIAS + (0.5 - col.karst) * CAVE_KARST_BIAS;

        for (int y = surfaceTop; y > MIN_Y + 4; y--) {
            int idx = index(sx, sz, y);
            if (!solid[idx]) continue;
            double depth = Math.min(surfaceTop - y, lowestNearby - y);
            if (depth < cover) continue;
            double closeness = Math.max(0.0, (cover + CAVE_CEILING_FADE - depth) / CAVE_CEILING_FADE);
            double d = interpolate(density, lx, y, lz) + geologyBias + closeness * closeness * CAVE_CEILING_STRENGTH;
            if (d >= 0) {
                if (painted && y <= 50 && y >= -60) {
                    double t = interpolate(toggle, lx, y, lz);
                    if (Math.abs(t) >= 0.2) veins[idx] = ctx.caves.vein(x, y, z, t);
                }
                continue;
            }
            byte fluid = caveFluid(col, x, y, z);
            if (fluid == CAVE_NONE) continue;
            solid[idx] = false;
            cave[idx] = fluid;
        }
    }

    private double lowestNearbySurface(int lx, int lz) {
        double lowest = Double.MAX_VALUE;
        for (int di = -CAVE_WALL_REACH; di <= CAVE_WALL_REACH; di++) {
            for (int dj = -CAVE_WALL_REACH; dj <= CAVE_WALL_REACH; dj++) {
                lowest = Math.min(lowest, heights[lx + BORDER + di][lz + BORDER + dj]);
            }
        }
        return lowest;
    }

    private byte caveFluid(HeightField.Column col, int x, int y, int z) {
        if (y <= LAVA_LEVEL) return CAVE_LAVA;
        int wx = (int) Math.floor(x + GROUNDWATER_WARP * SimplexNoise.noise2(seed + SEED_CAVE + 30, x / 48.0, z / 48.0));
        int wz = (int) Math.floor(z + GROUNDWATER_WARP * SimplexNoise.noise2(seed + SEED_CAVE + 31, x / 48.0, z / 48.0));
        int floorShift = (int) Math.round(3.0 * SimplexNoise.noise2(seed + SEED_CAVE + 32, x / 24.0, z / 24.0));
        long cellX = Math.floorDiv(wx, GROUNDWATER_CELL), cellZ = Math.floorDiv(wz, GROUNDWATER_CELL);
        int offsetX = Math.floorMod(wx, GROUNDWATER_CELL), offsetZ = Math.floorMod(wz, GROUNDWATER_CELL);
        long neighbourX = offsetX < 3 ? cellX - 1 : offsetX >= GROUNDWATER_CELL - 3 ? cellX + 1 : cellX;
        long neighbourZ = offsetZ < 3 ? cellZ - 1 : offsetZ >= GROUNDWATER_CELL - 3 ? cellZ + 1 : cellZ;
        Groundwater.Cell own = ctx.groundwater.cell(cellX, cellZ);
        if (own.isFloor(y, floorShift)) return CAVE_NONE;
        boolean flooded = own.floods(y, floorShift);
        if (neighbourX != cellX || neighbourZ != cellZ) {
            Groundwater.Cell[] neighbours = {
                    ctx.groundwater.cell(neighbourX, cellZ),
                    ctx.groundwater.cell(cellX, neighbourZ),
                    ctx.groundwater.cell(neighbourX, neighbourZ)};
            for (Groundwater.Cell neighbour : neighbours) {
                if (neighbour.isFloor(y, floorShift) || neighbour.floods(y, floorShift) != flooded) return CAVE_NONE;
            }
        }
        if (!flooded) return CAVE_AIR;
        return CaveType.at(seed, col, x, y, z) == CaveType.ICE ? CAVE_ICE : CAVE_WATER;
    }

    private void sealCaves() {
        byte[] before = cave.clone();
        boolean[] solidBefore = solid.clone();
        for (int sx = 1; sx < SHELL - 1; sx++) {
            for (int sz = 1; sz < SHELL - 1; sz++) {
                for (int y = top[sx * SHELL + sz]; y > MIN_Y + 4; y--) {
                    int idx = index(sx, sz, y);
                    byte code = before[idx];
                    if (code == CAVE_NONE) continue;
                    if (conflictsWithOutside(sx, sz, y, code, before, solidBefore)) {
                        cave[idx] = CAVE_NONE;
                        solid[idx] = true;
                    }
                }
            }
        }
    }

    private boolean conflictsWithOutside(int sx, int sz, int y, byte code, byte[] caveBefore, boolean[] solidBefore) {
        for (int[] side : SIDE_OFFSETS) {
            int nx = sx + side[0], nz = sz + side[1];
            int n = index(nx, nz, y);
            if (solidBefore[n] || caveBefore[n] != CAVE_NONE) continue;
            HeightField.Column neighbour = shellColumn(nx, nz);
            boolean outsideWet = y <= SEA_LEVEL || (neighbour.inRiver() && y <= neighbour.water);
            if (code == CAVE_AIR ? outsideWet : (code == CAVE_LAVA || !outsideWet)) return true;
        }
        return false;
    }

    private void paint(BlockSink sink) {
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                paintColumn(sink, lx, lz);
                placeVines(sink, lx, lz, cols[lx + BORDER][lz + BORDER]);
            }
        }
    }

    private void paintColumn(BlockSink sink, int lx, int lz) {
        int sx = lx + 1, sz = lz + 1;
        int i = lx + BORDER, j = lz + BORDER;
        int x = minX + lx, z = minZ + lz;
        HeightField.Column col = cols[i][j];
        double slope = Math.max(Math.abs(heights[i + 2][j] - heights[i - 2][j]), Math.abs(heights[i][j + 2] - heights[i][j - 2])) * 0.25;
        double strataOffset = SurfacePalette.strataOffset(seed, x, z);
        double soilNoise = SimplexNoise.noise2(seed + SEED_SURFACE + 1, x / 14.0, z / 14.0);
        int bedrockTop = MIN_Y + (int) (NoiseHelper.hash01(seed, x, z) * 4);
        int columnTop = top[sx * SHELL + sz];

        double waterTop = col.inRiver() ? Math.max(SEA_LEVEL, col.water) : SEA_LEVEL;
        int yTop = Math.max(columnTop, (int) Math.floor(waterTop));

        int depth = 0;
        boolean underWater = false;
        boolean openAbove = true;
        boolean inCave = false;
        int waterSurface = Integer.MIN_VALUE;
        BlockState soil = null;
        for (int y = yTop; y >= MIN_Y; y--) {
            byte caveCode = y < MAX_Y ? cave[index(sx, sz, y)] : CAVE_NONE;
            if (caveCode != CAVE_NONE) {
                BlockState fluid = caveFluidState(caveCode);
                if (fluid != null) sink.set(lx, y, lz, fluid);
                depth = 0;
                soil = null;
                underWater = false;
                openAbove = false;
                inCave = true;
                continue;
            }
            if (!isSolid(sx, sz, y)) {
                depth = 0;
                soil = null;
                inCave = false;
                boolean wet = y <= SEA_LEVEL || (col.inRiver() && y <= col.water);
                underWater = wet;
                openAbove = !wet;
                if (wet) {
                    if (waterSurface == Integer.MIN_VALUE) waterSurface = y;
                    sink.set(lx, y, lz, SurfacePalette.WATER);
                    if ((col.inRiver() && y == (int) col.water && needsFlow(i, j, y)) || spills(sx, sz, i, j, y)) {
                        sink.scheduleFluid(lx, y, lz);
                    }
                }
                continue;
            }

            BlockState state;
            if (y <= bedrockTop) {
                state = SurfacePalette.BEDROCK;
            } else if (depth == 0 && inCave) {
                state = caveFloor(x, y, z, col, sx, sz, strataOffset);
                soil = state == SurfacePalette.SAND ? SurfacePalette.SAND : null;
            } else if (depth == 0) {
                state = surfaceBlock(x, y, z, col, slope, underWater, soilNoise, sx, sz, strataOffset);
                soil = SurfacePalette.subsoil(state, col);
                if (underWater && waterSurface != Integer.MIN_VALUE) {
                    waterPlants(sink, lx, lz, x, z, col, y, waterSurface);
                }
                if (openAbove) decorateSurface(sink, lx, lz, sx, sz, x, z, y, col, state, slope, heights[i][j]);
                openAbove = false;
            } else {
                state = groundBlock(x, y, z, sx, sz, col, soil, depth, columnTop - y, strataOffset);
            }
            sink.set(lx, y, lz, state);
            depth++;
        }
    }

    private static BlockState caveFluidState(byte code) {
        return switch (code) {
            case CAVE_WATER -> SurfacePalette.WATER;
            case CAVE_LAVA -> SurfacePalette.LAVA;
            case CAVE_ICE -> SurfacePalette.ICE;
            default -> null;
        };
    }

    private void decorateSurface(BlockSink sink, int lx, int lz, int sx, int sz, int x, int z, int y,
                                 HeightField.Column col, BlockState ground, double slope, double height) {
        if (y + 1 < MAX_Y && ground == SurfacePalette.SNOW) {
            int layers = (int) ((height - Math.floor(height)) * 8.0);
            if (layers > 0) {
                sink.set(lx, y + 1, lz, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers));
            }
        } else if (y + 2 < MAX_Y) {
            GroundCover.Plant plant = GroundCover.pick(seed, x, z, col, ground, slope);
            if (plant != null && (plant.upper() == null || !isSolid(sx, sz, y + 2))) {
                sink.set(lx, y + 1, lz, plant.lower());
                if (plant.upper() != null) sink.set(lx, y + 2, lz, plant.upper());
            }
        }
    }

    private BlockState groundBlock(int x, int y, int z, int sx, int sz, HeightField.Column col, BlockState soil,
                                   int depth, int belowTop, double strataOffset) {
        if (col.iceDepth > 1 && belowTop <= col.iceDepth && y > SEA_LEVEL - 6) {
            return exposedSide(sx, sz, y) && depth > 4 ? SurfacePalette.BLUE_ICE : SurfacePalette.PACKED_ICE;
        }
        if (soil == SurfacePalette.SAND && col.dune > 0.15 && depth <= 4 + col.dune * 24) {
            return SurfacePalette.SAND;
        }
        if ((soil == SurfacePalette.SAND || soil == SurfacePalette.RED_SAND) && col.province == Province.DESERT && depth <= 3) {
            return soil;
        }
        if (soil != null && depth <= DIRT_DEPTH && !(exposedSide(sx, sz, y) && depth >= 2)) {
            return soil == SurfacePalette.DIRT && exposedSide(sx, sz, y) ? SurfacePalette.COARSE_DIRT : soil;
        }
        BlockState vein = veins[index(sx, sz, y)];
        if (vein != null) return vein;
        if (caveAdjacent(sx, sz, y)) return caveWall(x, y, z, col, strataOffset);
        if (y < 0) return SurfacePalette.DEEPSLATE;
        if (col.volcanic > 0.3) return SurfacePalette.volcanic(seed, y, strataOffset);
        if (depth <= 4 || exposedSide(sx, sz, y)) {
            return exposedSide(sx, sz, y) && SurfacePalette.mossyFace(seed, x, y, z, col)
                    ? SurfacePalette.MOSS
                    : surfaceRock(sx, sz, x, y, z, strataOffset, col.province);
        }
        return SurfacePalette.STONE;
    }

    private boolean caveAdjacent(int sx, int sz, int y) {
        return isCave(sx, sz, y - 1) || isCave(sx, sz, y + 1)
                || isCave(sx - 1, sz, y) || isCave(sx + 1, sz, y) || isCave(sx, sz - 1, y) || isCave(sx, sz + 1, y);
    }

    private BlockState caveWall(int x, int y, int z, HeightField.Column col, double strataOffset) {
        if (CaveType.at(seed, col, x, y, z) == CaveType.ICE) {
            double glaze = SimplexNoise.noise3(seed + SEED_CAVE + 20, x / 12.0, y / 8.0, z / 12.0);
            if (glaze > 0.4) return SurfacePalette.BLUE_ICE;
            if (glaze > -0.4) return SurfacePalette.PACKED_ICE;
        }
        if (y < 0) return SurfacePalette.DEEPSLATE;
        if (col.volcanic > 0.3) return SurfacePalette.volcanic(seed, y, strataOffset);
        return SurfacePalette.strata(seed, x, y, z, strataOffset, col.province);
    }

    private BlockState caveFloor(int x, int y, int z, HeightField.Column col, int sx, int sz, double strataOffset) {
        BlockState vein = veins[index(sx, sz, y)];
        if (vein != null) return vein;
        CaveType type = CaveType.at(seed, col, x, y, z);
        double patch = SimplexNoise.noise2(seed + SEED_CAVE + 21, x / 10.0, z / 10.0);
        if (type == CaveType.DESERT) return SurfacePalette.SAND;
        if (type == CaveType.ICE && patch > -0.3) return SurfacePalette.PACKED_ICE;
        if (y < 0) return SurfacePalette.DEEPSLATE;
        if (patch > 0.5) return SurfacePalette.GRAVEL;
        return SurfacePalette.strata(seed, x, y, z, strataOffset, col.province);
    }

    private BlockState surfaceBlock(int x, int y, int z, HeightField.Column col, double slope,
                                    boolean underWater, double soilNoise, int sx, int sz, double strataOffset) {
        if (underWater) return bedBlock(x, y, z, col, slope, soilNoise, sx, sz, strataOffset);

        Province province = col.province;
        if (col.cliffiness >= 0.6 && col.coastDist < 1 && y <= SEA_LEVEL + 3 && col.temperature > 0.2) {
            return soilNoise > 0.1 ? SurfacePalette.MOSSY_COBBLE : surfaceRock(sx, sz, x, y, z, strataOffset, province);
        }
        if (col.iceDepth > 1) return slope > 3.0 ? SurfacePalette.BLUE_ICE : SurfacePalette.SNOW;
        double localTemperature = Climate.atHeight(col.temperature, y);
        if (localTemperature < SNOW_TEMPERATURE + soilNoise * 0.02 && slope < 3.5) return SurfacePalette.SNOW;
        if (col.dune > 0.3) return SurfacePalette.SAND;
        if (col.atoll > 0.2) return SurfacePalette.SAND;
        if (col.coastDist < 8 && y <= SEA_LEVEL + 2 && col.cliffiness < 0.5) {
            if (col.volcanic > 0.1 || col.temperature < 0.25) return SurfacePalette.GRAVEL;
            return SurfacePalette.SAND;
        }
        if (col.volcanic > 0.55 && y > SEA_LEVEL + 40) {
            return soilNoise > 0.3 ? SurfacePalette.TUFF : soilNoise > -0.3 ? SurfacePalette.SMOOTH_BASALT : SurfacePalette.COARSE_DIRT;
        }
        if (col.riverDist < 1.5 && col.riverDist > -1 && y <= col.height + 1 && soilNoise > 0.2) return SurfacePalette.GRAVEL;
        if (rimDrop(sx, sz, y)) {
            return SurfacePalette.dampCliffs(col) && soilNoise > -0.5 ? SurfacePalette.MOSS : surfaceRock(sx, sz, x, y, z, strataOffset, province);
        }
        if (slope > 0.5 && col.talus > 0.3 + soilNoise * 0.2) {
            return soilNoise > 0.3 ? surfaceRock(sx, sz, x, y, z, strataOffset, province) : SurfacePalette.GRAVEL;
        }
        if (province == Province.DESERT && col.riverDist >= 6 && slope <= MOSS_MAX_SLOPE + 0.8 + soilNoise * 0.3) {
            return desertSurface(sx, sz, x, y, z, slope, soilNoise, strataOffset);
        }
        return slopeSurface(x, y, z, col, slope, soilNoise, sx, sz, strataOffset);
    }

    private BlockState bedBlock(int x, int y, int z, HeightField.Column col, double slope, double soilNoise,
                                int sx, int sz, double strataOffset) {
        Province province = col.province;
        if (slope > 2.5 && col.canyon > 0.6) return surfaceRock(sx, sz, x, y, z, strataOffset, province);
        if (col.inRiver()) {
            if (province == Province.CANYONLANDS) return soilNoise > -0.2 ? SurfacePalette.RED_SAND : SurfacePalette.GRAVEL;
            if (soilNoise > 0.25) return SurfacePalette.GRAVEL;
            if (soilNoise < -0.45) return SurfacePalette.CLAY;
            return SurfacePalette.DIRT;
        }
        if (col.atoll > 0.2) return SurfacePalette.SAND;
        if (col.volcanic > 0.1) return soilNoise > 0.3 ? SurfacePalette.SMOOTH_BASALT : SurfacePalette.GRAVEL;
        if (col.cliffiness >= 0.6 && col.coastDist > -40) {
            return soilNoise > 0.2 ? SurfacePalette.GRAVEL : surfaceRock(sx, sz, x, y, z, strataOffset, province);
        }
        if (y < SEA_LEVEL - 35) return soilNoise > 0.2 ? SurfacePalette.GRAVEL : SurfacePalette.CLAY;
        if (col.temperature < 0.3) return soilNoise > 0.4 ? SurfacePalette.SAND : SurfacePalette.GRAVEL;
        return soilNoise > -0.1 && col.cliffiness < 0.6 ? SurfacePalette.SAND : SurfacePalette.GRAVEL;
    }

    private BlockState desertSurface(int sx, int sz, int x, int y, int z, double slope, double soilNoise, double strataOffset) {
        double tone = SimplexNoise.noise2(seed + SEED_SURFACE + 13, x / 90.0, z / 90.0) + 0.1 * soilNoise;
        double outcrop = SimplexNoise.noise2(seed + SEED_SURFACE + 14, x / 60.0, z / 60.0);
        if (slope > 2.2 && outcrop > 0.35) return surfaceRock(sx, sz, x, y, z, strataOffset, Province.DESERT);
        return tone < -0.55 ? SurfacePalette.RED_SAND : SurfacePalette.SAND;
    }

    private BlockState slopeSurface(int x, int y, int z, HeightField.Column col, double slope, double soilNoise,
                                    int sx, int sz, double strataOffset) {
        Province province = col.province;
        boolean dry = province == Province.CANYONLANDS || province == Province.DESERT;
        if (slope <= GRASS_MAX_SLOPE + soilNoise * 0.3) {
            return SurfacePalette.topsoil(col, soilNoise);
        }
        if (slope <= MOSS_MAX_SLOPE + soilNoise * 0.5) {
            if (dry) {
                BlockState loose = province == Province.DESERT ? SurfacePalette.SAND : SurfacePalette.RED_SAND;
                return soilNoise > 0.35 ? loose : surfaceRock(sx, sz, x, y, z, strataOffset, province);
            }
            double outcrop = SimplexNoise.noise2(seed + SEED_SURFACE + 11, x / 45.0, z / 45.0);
            boolean rocky = slope > 2.6 && outcrop > 0.4;
            return rocky ? surfaceRock(sx, sz, x, y, z, strataOffset, province) : SurfacePalette.topsoil(col, soilNoise);
        }
        if (!dry && !exposedSide(sx, sz, y) && soilNoise > -0.2) {
            return SurfacePalette.GRASS;
        }
        if (SurfacePalette.dampCliffs(col) && soilNoise > -0.4) return SurfacePalette.MOSS;
        if (SurfacePalette.mossyFace(seed, x, y, z, col)) return SurfacePalette.MOSS;
        return surfaceRock(sx, sz, x, y, z, strataOffset, province);
    }

    private BlockState surfaceRock(int sx, int sz, int x, int y, int z, double strataOffset, Province province) {
        if (faceStrength[sx * SHELL + sz] >= 0.5) return SurfacePalette.strata(seed, x, y, z, strataOffset, province);
        return SurfacePalette.weathered(seed, x, y, z, strataOffset, province);
    }

    private void waterPlants(BlockSink sink, int lx, int lz, int x, int z, HeightField.Column col, int bed, int surface) {
        int depth = surface - bed;
        if (depth < 1 || !col.inRiver()) return;
        boolean lake = col.riverDist >= 0;
        double patch = SimplexNoise.noise2(seed + SEED_SURFACE + 14, x / 10.0, z / 10.0);
        double roll = NoiseHelper.hash01(seed + SEED_SURFACE + 15, x, z);
        if (depth >= 2 && depth <= 6 && patch > 0.1 && roll < 0.6) {
            sink.set(lx, bed + 1, lz, SEAGRASS);
        }
        if (lake && depth <= 3 && col.province != Province.CANYONLANDS && patch > 0.35 && roll > 0.8 && surface + 1 < MAX_Y) {
            sink.set(lx, surface + 1, lz, LILY_PAD);
        }
    }

    private void placeVines(BlockSink sink, int lx, int lz, HeightField.Column col) {
        Province province = col.province;
        boolean dampRegion = province == Province.CASCADIA || province == Province.FJORDLAND || province == Province.SEA_CLIFFS;
        boolean gorge = col.riverDist < 30 && province != Province.CANYONLANDS;
        if (!dampRegion && !gorge) return;
        int sx = lx + 1, sz = lz + 1, x = minX + lx, z = minZ + lz;
        double strand = SimplexNoise.noise2(seed + SEED_SURFACE + 12, x / 4.0, z / 4.0);
        if (strand < (dampRegion ? 0.25 : 0.45)) return;
        int own = top[sx * SHELL + sz];
        for (int d = 0; d < 4; d++) {
            int nx = sx + VINE_OFFSETS[d][0], nz = sz + VINE_OFFSETS[d][1];
            int neighbourTop = top[nx * SHELL + nz];
            if (neighbourTop - own < 6) continue;
            BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.PROPERTY_BY_DIRECTION.get(VINE_SIDES[d]), true);
            int start = neighbourTop - 2;
            int length = 4 + (int) ((SimplexNoise.noise2(seed + SEED_SURFACE + 13, x / 9.0, z / 9.0) * 0.5 + 0.5) * 18);
            for (int y = start; y > Math.max(own, start - length); y--) {
                if (isSolid(sx, sz, y) || isCave(sx, sz, y) || !isSolid(nx, nz, y)) break;
                if (y <= SEA_LEVEL || (col.inRiver() && y <= col.water)) break;
                if (SurfacePalette.mossiness(seed, x, y, z, col) < 0.55) continue;
                sink.set(lx, y, lz, vine);
            }
            return;
        }
    }

    private boolean rimDrop(int sx, int sz, int y) {
        for (int[] side : SIDE_OFFSETS) {
            boolean open = true;
            for (int k = 0; k < 4 && open; k++) open = !isSolid(sx + side[0], sz + side[1], y - k);
            if (open) return true;
        }
        return false;
    }

    private boolean exposedSide(int sx, int sz, int y) {
        return !isSolid(sx - 1, sz, y) || !isSolid(sx + 1, sz, y)
                || !isSolid(sx, sz - 1, y) || !isSolid(sx, sz + 1, y);
    }

    private boolean spills(int sx, int sz, int i, int j, int y) {
        for (int[] side : SIDE_OFFSETS) {
            if (isSolid(sx + side[0], sz + side[1], y) || isCave(sx + side[0], sz + side[1], y)) continue;
            HeightField.Column neighbour = cols[i + side[0]][j + side[1]];
            boolean wet = y <= SEA_LEVEL || (neighbour.inRiver() && y <= neighbour.water);
            if (!wet) return true;
        }
        return false;
    }

    private boolean needsFlow(int i, int j, int y) {
        for (int[] side : SIDE_OFFSETS) {
            HeightField.Column neighbour = cols[i + side[0]][j + side[1]];
            double neighbourWater = neighbour.inRiver() ? neighbour.water
                    : (neighbour.height < SEA_LEVEL ? SEA_LEVEL : Double.NEGATIVE_INFINITY);
            if (neighbourWater < y - 0.5 && neighbour.height < y) return true;
        }
        return false;
    }
}
