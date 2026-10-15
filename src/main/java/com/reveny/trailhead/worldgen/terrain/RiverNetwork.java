package com.reveny.trailhead.worldgen.terrain;

import com.reveny.trailhead.noise.NoiseHelper;
import com.reveny.trailhead.noise.SimplexNoise;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.reveny.trailhead.noise.NoiseConfig.*;

public final class RiverNetwork {
    private static final int SUBDIVISIONS = 4;
    private static final int BUCKET_SIZE = 64;
    private static final int MAX_TILES = 32;
    private static final double SEAM_BAND = 192.0;
    private static final double MAX_LEVEE_HEIGHT = 3.0;
    private static final double TROUGH_FLOOR_LAKE_LIMIT = 0.3;

    private final long seed;
    private final HeightField field;
    private final Map<Long, CompletableFuture<Tile>> tiles = new LinkedHashMap<>(16, 0.75f, true);

    public RiverNetwork(long seed, HeightField field) {
        this.seed = seed;
        this.field = field;
    }

    void carve(double x, double z, HeightField.Column col) {
        long tileX = Math.floorDiv((long) Math.floor(x), RIVER_TILE_SIZE);
        long tileZ = Math.floorDiv((long) Math.floor(z), RIVER_TILE_SIZE);
        double localX = x - tileX * (double) RIVER_TILE_SIZE, localZ = z - tileZ * (double) RIVER_TILE_SIZE;

        long neighbourX = tileX, neighbourZ = tileZ;
        double ownWeightX = 1.0, ownWeightZ = 1.0;
        if (localX < SEAM_BAND) {
            neighbourX = tileX - 1;
            ownWeightX = seamWeight(localX);
        } else if (localX > RIVER_TILE_SIZE - SEAM_BAND) {
            neighbourX = tileX + 1;
            ownWeightX = seamWeight(RIVER_TILE_SIZE - localX);
        }
        if (localZ < SEAM_BAND) {
            neighbourZ = tileZ - 1;
            ownWeightZ = seamWeight(localZ);
        } else if (localZ > RIVER_TILE_SIZE - SEAM_BAND) {
            neighbourZ = tileZ + 1;
            ownWeightZ = seamWeight(RIVER_TILE_SIZE - localZ);
        }

        if (ownWeightX >= 1.0 && ownWeightZ >= 1.0) {
            carveWith(tile(tileX, tileZ), x, z, col);
            return;
        }
        long[][] blended = {{tileX, tileZ}, {neighbourX, tileZ}, {tileX, neighbourZ}, {neighbourX, neighbourZ}};
        double[] weights = {
                ownWeightX * ownWeightZ, (1 - ownWeightX) * ownWeightZ,
                ownWeightX * (1 - ownWeightZ), (1 - ownWeightX) * (1 - ownWeightZ)
        };
        double base = col.height;
        double height = 0, nearest = Double.MAX_VALUE, wetWeight = 0, wetLevel = 0;
        for (int k = 0; k < 4; k++) {
            if (weights[k] <= 0) continue;
            HeightField.Column carved = col.copy();
            carved.height = base;
            carveWith(tile(blended[k][0], blended[k][1]), x, z, carved);
            height += weights[k] * carved.height;
            nearest = Math.min(nearest, carved.riverDist);
            if (carved.inRiver()) {
                wetWeight += weights[k];
                wetLevel += weights[k] * carved.water;
            }
        }
        col.height = height;
        col.riverDist = nearest;
        double water = wetWeight >= 0.25 ? Math.floor(wetLevel / wetWeight) : Double.NaN;
        col.water = !Double.isNaN(water) && height < water ? water : Double.NaN;
    }

    private static double seamWeight(double distanceToBorder) {
        return 0.5 + 0.5 * NoiseHelper.smoothstep(0, SEAM_BAND, distanceToBorder);
    }

    public List<double[]> waterfalls(double x, double z) {
        Tile tile = tile(Math.floorDiv((long) Math.floor(x), RIVER_TILE_SIZE), Math.floorDiv((long) Math.floor(z), RIVER_TILE_SIZE));
        List<double[]> falls = new ArrayList<>();
        for (int p = 0; p < tile.count; p++) {
            if (tile.fall[p]) {
                falls.add(new double[]{tile.startX[p], tile.startZ[p], tile.endX[p], tile.endZ[p], tile.startLevel[p], tile.fallDrop[p]});
            }
        }
        return falls;
    }

    private void carveWith(Tile tile, double queryX, double queryZ, HeightField.Column col) {
        double x = queryX + 6.0 * SimplexNoise.noise2(seed + SEED_RIVER + 25, queryX / 28.0, queryZ / 28.0);
        double z = queryZ + 6.0 * SimplexNoise.noise2(seed + SEED_RIVER + 26, queryX / 28.0, queryZ / 28.0);
        int[] bucket = tile.bucket(x, z);
        if (bucket == null) return;

        double fjord = (1.0 - NoiseHelper.smoothstep(0.0, FJORD_REACH, col.coastDist)) * col.cliffiness;
        double steep = Math.max(col.canyon, fjord);
        Carving carving = new Carving(col.height);
        for (int i = 1; i <= bucket[0]; i++) {
            carveSegment(tile, bucket[i], x, z, col, fjord, steep, carving);
        }

        double height = carving.best;
        double water = carving.water;
        if (Double.isNaN(water) && carving.levee > height && carving.levee - height <= MAX_LEVEE_HEIGHT) {
            height = carving.levee;
        }
        if (carving.tooth > height && !Double.isNaN(water) && Math.abs(water - carving.toothLevel) < 0.5) {
            height = carving.tooth;
            water = Double.NaN;
        }
        col.height = height;
        col.water = water;
        col.riverDist = carving.nearest;
        applyLake(tile, x, z, col);
    }

    private void carveSegment(Tile tile, int p, double x, double z, HeightField.Column col,
                              double fjord, double steep, Carving carving) {
        double ax = tile.startX[p], az = tile.startZ[p];
        double dx = tile.endX[p] - ax, dz = tile.endZ[p] - az;
        double lengthSquared = dx * dx + dz * dz;
        double t = lengthSquared > 0 ? NoiseHelper.clamp(((x - ax) * dx + (z - az) * dz) / lengthSquared, 0.0, 1.0) : 0.0;
        double ex = x - (ax + dx * t), ez = z - (az + dz * t);
        double distance = Math.sqrt(ex * ex + ez * ez);
        if (distance > RIVER_INFLUENCE) return;

        double level = Math.floor(NoiseHelper.lerp(tile.startLevel[p], tile.endLevel[p], t));
        double drowned = fjord * (1.0 - NoiseHelper.smoothstep(SEA_LEVEL + 2.0, SEA_LEVEL + 12.0, level));
        double halfWidth = NoiseHelper.lerp(tile.startHalfWidth[p], tile.endHalfWidth[p], t) * (1.0 + 1.5 * drowned);
        double edge = distance - halfWidth;
        carving.nearest = Math.min(carving.nearest, edge);

        double candidate;
        boolean channel = edge < 0;
        if (channel) {
            double depth = 1.5 + halfWidth * 0.18 + drowned * FJORD_DEPTH;
            double r = distance / halfWidth;
            candidate = level - depth * Math.sqrt(Math.max(0.0, 1.0 - r * r));
        } else {
            candidate = level + 1.0 + bankHeight(edge, halfWidth, col.glacial, steep);
            if (edge < 4.0) carving.levee = Math.max(carving.levee, level + 1.0);
        }

        if (tile.lip[p] && t > 0.7 && edge < 0) {
            double length = Math.sqrt(lengthSquared);
            double lateral = length > 0 ? ((x - ax) * dz - (z - az) * dx) / length : 0;
            long cell = (long) Math.floor((lateral + 64.0) / 2.0);
            if (NoiseHelper.hash01(seed + SEED_RIVER + 9, cell, p) < 0.18) {
                carving.tooth = Math.max(carving.tooth, level + 1.0 + Math.floor(NoiseHelper.hash01(seed + SEED_RIVER + 10, cell, p) * 2));
                carving.toothLevel = level;
            }
        }

        if (tile.fall[p]) {
            double poolDistance = Math.sqrt((x - ax) * (x - ax) + (z - az) * (z - az));
            double drop = tile.fallDrop[p];
            double radius = halfWidth + 6.0 + Math.min(8.0, drop * 0.12);
            if (poolDistance < radius) {
                double r = poolDistance / radius;
                double pool = level - Math.min(14.0, 3.0 + drop * 0.3) * Math.sqrt(Math.max(0.0, 1.0 - r * r));
                if (pool < candidate) {
                    candidate = pool;
                    channel = true;
                }
            }
        }

        if (candidate < carving.best) {
            carving.best = candidate;
            carving.water = channel ? level : Double.NaN;
        }
    }

    private static double bankHeight(double edge, double halfWidth, double glacial, double steep) {
        double vSlope = NoiseHelper.lerp(1.15, 0.38, NoiseHelper.clamp(halfWidth / 12.0, 0.0, 1.0));
        double vBank = vSlope * edge + 0.003 * edge * edge;
        double troughFloor = halfWidth * 2.5 + 12.0;
        double troughRise = Math.max(0.0, edge - troughFloor);
        double uBank = 0.02 * edge + 0.12 * troughRise * Math.sqrt(troughRise);
        double canyonBank = Math.max(0.0, edge - halfWidth * 0.6) * CANYON_WALL_SLOPE;
        double bank = NoiseHelper.lerp(vBank, uBank, glacial * (1.0 - steep));
        return NoiseHelper.lerp(bank, canyonBank, steep);
    }

    private void applyLake(Tile tile, double x, double z, HeightField.Column col) {
        double[] lake = tile.lakeAt(x, z);
        if (lake == null) return;
        double level = lake[0], lakeness = lake[1];
        if (lakeness >= 0.5 && col.height < level - 0.5) {
            double depth = Math.min(LAKE_MAX_DEPTH, level - col.height);
            col.height = level - 1.0 - depth * NoiseHelper.smoothstep(0.5, 1.0, lakeness);
            col.water = Double.isNaN(col.water) ? level : Math.max(col.water, level);
        } else if (col.height < level + 1.0) {
            double rim = NoiseHelper.smoothstep(0.0, 0.5, lakeness);
            col.height = Math.max(col.height, NoiseHelper.lerp(col.height, level + 1.0, rim));
            if (rim > 0.5) col.water = Double.NaN;
        }
    }

    private Tile tile(long tileX, long tileZ) {
        long key = (tileX << 32) ^ (tileZ & 0xFFFFFFFFL);
        CompletableFuture<Tile> future;
        boolean owner = false;
        synchronized (tiles) {
            future = tiles.get(key);
            if (future == null) {
                future = new CompletableFuture<>();
                tiles.put(key, future);
                owner = true;
                if (tiles.size() > MAX_TILES) {
                    var oldest = tiles.entrySet().iterator();
                    oldest.next();
                    oldest.remove();
                }
            }
        }
        if (owner) {
            try {
                future.complete(buildTile(tileX, tileZ));
            } catch (Throwable error) {
                synchronized (tiles) {
                    tiles.remove(key);
                }
                future.completeExceptionally(error);
            }
        }
        return future.join();
    }

    private Tile buildTile(long tileX, long tileZ) {
        Tile tile = new Tile(tileX, tileZ);
        new TileSolver(tile).solve();
        tile.buildBuckets();
        return tile;
    }

    private static double halfWidth(double accumulation) {
        return Math.min(RIVER_HALF_WIDTH_MAX, RIVER_HALF_WIDTH_BASE + RIVER_HALF_WIDTH_FLOW * Math.sqrt(accumulation));
    }

    private static double catmullRom(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t, t3 = t2 * t;
        return 0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }

    private static final class Carving {
        double best;
        double water = Double.NaN;
        double levee = Double.NEGATIVE_INFINITY;
        double nearest = Double.MAX_VALUE;
        double tooth = Double.NEGATIVE_INFINITY;
        double toothLevel = Double.NaN;

        Carving(double terrain) {
            this.best = terrain;
        }
    }

    private final class Tile {
        final int grid;
        final int cellCount;
        final double originX, originZ;
        final long gridOffsetX, gridOffsetZ;
        final float[] lake;

        float[] startX, startZ, endX, endZ, startLevel, endLevel, startHalfWidth, endHalfWidth, fallDrop;
        boolean[] fall, lip;
        int count;

        private int bucketsPerSide;
        private double bucketOriginX, bucketOriginZ;
        private int[][] buckets;

        Tile(long tileX, long tileZ) {
            grid = (RIVER_TILE_SIZE + 2 * RIVER_TILE_PAD) / RIVER_CELL;
            cellCount = grid * grid;
            originX = tileX * (double) RIVER_TILE_SIZE - RIVER_TILE_PAD;
            originZ = tileZ * (double) RIVER_TILE_SIZE - RIVER_TILE_PAD;
            gridOffsetX = Math.round(originX / RIVER_CELL);
            gridOffsetZ = Math.round(originZ / RIVER_CELL);
            lake = new float[cellCount];
            allocate(1024);
        }

        double coreMinX() {
            return originX + RIVER_TILE_PAD - RIVER_TILE_MARGIN;
        }

        double coreMaxX() {
            return originX + RIVER_TILE_PAD + RIVER_TILE_SIZE + RIVER_TILE_MARGIN;
        }

        double coreMinZ() {
            return originZ + RIVER_TILE_PAD - RIVER_TILE_MARGIN;
        }

        double coreMaxZ() {
            return originZ + RIVER_TILE_PAD + RIVER_TILE_SIZE + RIVER_TILE_MARGIN;
        }

        boolean inCore(double x, double z) {
            return x >= coreMinX() && x <= coreMaxX() && z >= coreMinZ() && z <= coreMaxZ();
        }

        int addSegment(double fromX, double fromZ, double toX, double toZ) {
            if (count == startX.length) grow(startX.length * 2);
            int p = count++;
            startX[p] = (float) fromX;
            startZ[p] = (float) fromZ;
            endX[p] = (float) toX;
            endZ[p] = (float) toZ;
            return p;
        }

        void buildBuckets() {
            bucketOriginX = coreMinX();
            bucketOriginZ = coreMinZ();
            bucketsPerSide = (int) Math.ceil((coreMaxX() - coreMinX()) / BUCKET_SIZE);
            buckets = new int[bucketsPerSide * bucketsPerSide][];
            for (int p = 0; p < count; p++) {
                double reach = RIVER_INFLUENCE + Math.max(startHalfWidth[p], endHalfWidth[p]) * 2.5;
                int minI = clampBucket((Math.min(startX[p], endX[p]) - reach - bucketOriginX) / BUCKET_SIZE);
                int maxI = clampBucket((Math.max(startX[p], endX[p]) + reach - bucketOriginX) / BUCKET_SIZE);
                int minJ = clampBucket((Math.min(startZ[p], endZ[p]) - reach - bucketOriginZ) / BUCKET_SIZE);
                int maxJ = clampBucket((Math.max(startZ[p], endZ[p]) + reach - bucketOriginZ) / BUCKET_SIZE);
                for (int bj = minJ; bj <= maxJ; bj++) {
                    for (int bi = minI; bi <= maxI; bi++) {
                        addToBucket(bj * bucketsPerSide + bi, p);
                    }
                }
            }
        }

        private void addToBucket(int b, int segment) {
            int[] list = buckets[b];
            if (list == null) {
                list = new int[9];
            } else if (list[0] + 1 == list.length) {
                list = Arrays.copyOf(list, list.length * 2);
            }
            list[++list[0]] = segment;
            buckets[b] = list;
        }

        int[] bucket(double x, double z) {
            int bi = (int) Math.floor((x - bucketOriginX) / BUCKET_SIZE);
            int bj = (int) Math.floor((z - bucketOriginZ) / BUCKET_SIZE);
            if (bi < 0 || bj < 0 || bi >= bucketsPerSide || bj >= bucketsPerSide) return null;
            return buckets[bj * bucketsPerSide + bi];
        }

        private int clampBucket(double value) {
            return (int) NoiseHelper.clamp(Math.floor(value), 0, bucketsPerSide - 1);
        }

        double[] lakeAt(double x, double z) {
            double wx = x + 14.0 * SimplexNoise.noise2(seed + SEED_RIVER + 20, x / 45.0, z / 45.0)
                    + 1.5 * SimplexNoise.noise2(seed + SEED_RIVER + 22, x / 11.0, z / 11.0);
            double wz = z + 14.0 * SimplexNoise.noise2(seed + SEED_RIVER + 21, x / 45.0, z / 45.0)
                    + 1.5 * SimplexNoise.noise2(seed + SEED_RIVER + 23, x / 11.0, z / 11.0);
            double[] result = lakeAtGrid(wx, wz);
            if (result != null) {
                double shore = NoiseHelper.smoothstep(0.15, 0.85, result[1]);
                result[1] = NoiseHelper.clamp(shore + 0.04 * SimplexNoise.noise2(seed + SEED_RIVER + 24, x / 30.0, z / 30.0), 0.0, 1.0);
            }
            return result;
        }

        private double[] lakeAtGrid(double x, double z) {
            double gx = (x - originX) / RIVER_CELL - 0.5, gz = (z - originZ) / RIVER_CELL - 0.5;
            int i = (int) Math.floor(gx), j = (int) Math.floor(gz);
            if (i < 1 || j < 1 || i >= grid - 2 || j >= grid - 2) return null;
            double tx = gx - i, tz = gz - j;
            double level = Double.NaN, wet = 0;
            for (int corner = 0; corner < 4; corner++) {
                int ci = i + (corner & 1), cj = j + (corner >> 1);
                float cellLevel = lake[cj * grid + ci];
                if (Float.isNaN(cellLevel)) continue;
                double weight = ((corner & 1) == 1 ? tx : 1 - tx) * ((corner >> 1) == 1 ? tz : 1 - tz);
                wet += weight;
                level = Double.isNaN(level) ? cellLevel : Math.max(level, cellLevel);
            }
            if (Double.isNaN(level)) return null;
            return new double[]{Math.floor(level), wet};
        }

        private void allocate(int capacity) {
            startX = new float[capacity];
            startZ = new float[capacity];
            endX = new float[capacity];
            endZ = new float[capacity];
            startLevel = new float[capacity];
            endLevel = new float[capacity];
            startHalfWidth = new float[capacity];
            endHalfWidth = new float[capacity];
            fallDrop = new float[capacity];
            fall = new boolean[capacity];
            lip = new boolean[capacity];
        }

        private void grow(int capacity) {
            startX = Arrays.copyOf(startX, capacity);
            startZ = Arrays.copyOf(startZ, capacity);
            endX = Arrays.copyOf(endX, capacity);
            endZ = Arrays.copyOf(endZ, capacity);
            startLevel = Arrays.copyOf(startLevel, capacity);
            endLevel = Arrays.copyOf(endLevel, capacity);
            startHalfWidth = Arrays.copyOf(startHalfWidth, capacity);
            endHalfWidth = Arrays.copyOf(endHalfWidth, capacity);
            fallDrop = Arrays.copyOf(fallDrop, capacity);
            fall = Arrays.copyOf(fall, capacity);
            lip = Arrays.copyOf(lip, capacity);
        }
    }

    private final class TileSolver {
        private final Tile tile;
        private final int grid;
        private final int cellCount;
        private final double[] cellX, cellZ, elevation, filled, rain, accumulation, riverLevel;
        private final int[] receiver, floodOrder, mainTributary;
        private final boolean[] river, troughFloor;
        private int floodCount;

        TileSolver(Tile tile) {
            this.tile = tile;
            this.grid = tile.grid;
            this.cellCount = tile.cellCount;
            cellX = new double[cellCount];
            cellZ = new double[cellCount];
            elevation = new double[cellCount];
            filled = new double[cellCount];
            rain = new double[cellCount];
            accumulation = new double[cellCount];
            riverLevel = new double[cellCount];
            receiver = new int[cellCount];
            floodOrder = new int[cellCount];
            mainTributary = new int[cellCount];
            river = new boolean[cellCount];
            troughFloor = new boolean[cellCount];
        }

        void solve() {
            sampleTerrain();
            priorityFlood();
            accumulateFlow();
            findLakes();
            settleRiverLevels();
            buildSegments();
        }

        private void sampleTerrain() {
            for (int j = 0; j < grid; j++) {
                for (int i = 0; i < grid; i++) {
                    int k = j * grid + i;
                    long gi = tile.gridOffsetX + i, gj = tile.gridOffsetZ + j;
                    cellX[k] = tile.originX + (i + 0.5) * RIVER_CELL + (NoiseHelper.hash01(seed + SEED_RIVER, gi, gj) - 0.5) * 2 * RIVER_JITTER;
                    cellZ[k] = tile.originZ + (j + 0.5) * RIVER_CELL + (NoiseHelper.hash01(seed + SEED_RIVER + 1, gi, gj) - 0.5) * 2 * RIVER_JITTER;
                    HeightField.Column col = field.baseColumn(cellX[k], cellZ[k]);
                    elevation[k] = col.height;
                    troughFloor[k] = col.trough > TROUGH_FLOOR_LAKE_LIMIT;
                }
            }
        }

        private void priorityFlood() {
            Arrays.fill(receiver, -1);
            boolean[] visited = new boolean[cellCount];
            IntHeap heap = new IntHeap(cellCount, filled);
            for (int j = 0; j < grid; j++) {
                for (int i = 0; i < grid; i++) {
                    int k = j * grid + i;
                    boolean border = i == 0 || j == 0 || i == grid - 1 || j == grid - 1;
                    if (border || elevation[k] <= SEA_LEVEL) {
                        filled[k] = Math.min(elevation[k], SEA_LEVEL);
                        visited[k] = true;
                        heap.push(k);
                    }
                }
            }
            while (!heap.isEmpty()) {
                int cell = heap.pop();
                floodOrder[floodCount++] = cell;
                int ci = cell % grid, cj = cell / grid;
                for (int dj = -1; dj <= 1; dj++) {
                    for (int di = -1; di <= 1; di++) {
                        if (di == 0 && dj == 0) continue;
                        int ni = ci + di, nj = cj + dj;
                        if (ni < 0 || nj < 0 || ni >= grid || nj >= grid) continue;
                        int neighbour = nj * grid + ni;
                        if (visited[neighbour]) continue;
                        visited[neighbour] = true;
                        filled[neighbour] = Math.max(elevation[neighbour], filled[cell] + 1e-3);
                        receiver[neighbour] = cell;
                        heap.push(neighbour);
                    }
                }
            }
        }

        private void accumulateFlow() {
            for (int k = 0; k < cellCount; k++) rain[k] = field.rainAt(cellX[k], cellZ[k]);
            for (int k = 0; k < cellCount; k++) accumulation[k] = 0.15 + 1.5 * Math.pow(rain[k], 1.3);
            for (int o = floodCount - 1; o >= 0; o--) {
                int k = floodOrder[o];
                if (receiver[k] >= 0) accumulation[receiver[k]] += accumulation[k];
            }
        }

        private void findLakes() {
            float[] lake = tile.lake;
            Arrays.fill(lake, Float.NaN);
            for (int k = 0; k < cellCount; k++) {
                if (elevation[k] <= SEA_LEVEL || filled[k] - elevation[k] <= LAKE_MIN_DEPTH || troughFloor[k]) continue;
                boolean dry = rain[k] < 0.22;
                long gi = tile.gridOffsetX + k % grid, gj = tile.gridOffsetZ + k / grid;
                if (dry && NoiseHelper.hash01(seed + SEED_RIVER + 30, gi / 8, gj / 8) > 0.12) continue;
                lake[k] = (float) filled[k];
            }
            for (int k = 0; k < cellCount; k++) {
                if (!Float.isNaN(lake[k]) && wetNeighbours(lake, k) < 3) lake[k] = Float.NaN;
            }
        }

        private int wetNeighbours(float[] lake, int k) {
            int ci = k % grid, cj = k / grid, wet = 0;
            for (int dj = -1; dj <= 1; dj++) {
                for (int di = -1; di <= 1; di++) {
                    int ni = ci + di, nj = cj + dj;
                    if (ni >= 0 && nj >= 0 && ni < grid && nj < grid && !Float.isNaN(lake[nj * grid + ni])) wet++;
                }
            }
            return wet;
        }

        private void settleRiverLevels() {
            Arrays.fill(mainTributary, -1);
            for (int k = 0; k < cellCount; k++) {
                river[k] = receiver[k] >= 0 && elevation[k] > SEA_LEVEL && accumulation[k] >= RIVER_MIN_ACCUMULATION;
                riverLevel[k] = river[k] ? Math.max(SEA_LEVEL, elevation[k] - RIVER_INCISION) : SEA_LEVEL;
            }
            for (int o = floodCount - 1; o >= 0; o--) {
                int k = floodOrder[o];
                if (!river[k]) continue;
                int down = receiver[k];
                if (river[down]) riverLevel[down] = Math.min(riverLevel[down], riverLevel[k]);
                if (mainTributary[down] < 0 || accumulation[k] > accumulation[mainTributary[down]]) mainTributary[down] = k;
            }
        }

        private void buildSegments() {
            for (int k = 0; k < cellCount; k++) {
                if (river[k] && tile.inCore(cellX[k], cellZ[k])) buildReach(k);
            }
        }

        private void buildReach(int k) {
            int down = receiver[k];
            int up = mainTributary[k];
            int further = receiver[down];
            double p0x = up >= 0 ? cellX[up] : 2 * cellX[k] - cellX[down];
            double p0z = up >= 0 ? cellZ[up] : 2 * cellZ[k] - cellZ[down];
            double p3x = further >= 0 ? cellX[further] : 2 * cellX[down] - cellX[k];
            double p3z = further >= 0 ? cellZ[further] : 2 * cellZ[down] - cellZ[k];
            double upperLevel = riverLevel[k];
            double lowerLevel = river[down] ? riverLevel[down] : SEA_LEVEL;
            double drop = upperLevel - lowerLevel;
            boolean waterfall = drop >= WATERFALL_MIN_DROP;
            double upperHalfWidth = halfWidth(accumulation[k]);
            double lowerHalfWidth = river[down] ? halfWidth(accumulation[down]) : upperHalfWidth;

            double previousX = cellX[k], previousZ = cellZ[k];
            for (int s = 1; s <= SUBDIVISIONS; s++) {
                double t = (double) s / SUBDIVISIONS;
                double t0 = (double) (s - 1) / SUBDIVISIONS;
                double nextX = catmullRom(p0x, cellX[k], cellX[down], p3x, t);
                double nextZ = catmullRom(p0z, cellZ[k], cellZ[down], p3z, t);
                int p = tile.addSegment(previousX, previousZ, nextX, nextZ);
                double narrowing = 1.0;
                if (waterfall) {
                    shapeWaterfall(p, s, upperLevel, lowerLevel, drop);
                    narrowing = s >= 2 ? 0.5 : 1.0;
                } else {
                    tile.startLevel[p] = (float) NoiseHelper.lerp(upperLevel, lowerLevel, t0);
                    tile.endLevel[p] = (float) NoiseHelper.lerp(upperLevel, lowerLevel, t);
                }
                tile.startHalfWidth[p] = (float) (NoiseHelper.lerp(upperHalfWidth, lowerHalfWidth, t0) * narrowing);
                tile.endHalfWidth[p] = (float) (NoiseHelper.lerp(upperHalfWidth, lowerHalfWidth, t) * narrowing);
                previousX = nextX;
                previousZ = nextZ;
            }
        }

        private void shapeWaterfall(int p, int subdivision, double upperLevel, double lowerLevel, double drop) {
            boolean twoTier = drop >= 12.0;
            double middleLevel = upperLevel - drop * 0.55;
            double level = switch (subdivision) {
                case 1, 2 -> upperLevel;
                case 3 -> twoTier ? middleLevel : lowerLevel;
                default -> lowerLevel;
            };
            tile.startLevel[p] = (float) level;
            tile.endLevel[p] = (float) level;
            tile.fall[p] = subdivision == 3 || (twoTier && subdivision == 4);
            tile.lip[p] = subdivision == 2 || (twoTier && subdivision == 3);
            tile.fallDrop[p] = (float) (twoTier ? (subdivision == 3 ? drop * 0.55 : drop * 0.45) : drop);
        }
    }

    private static final class IntHeap {
        private final int[] data;
        private final double[] priority;
        private int size;

        IntHeap(int capacity, double[] priority) {
            this.data = new int[capacity];
            this.priority = priority;
        }

        boolean isEmpty() {
            return size == 0;
        }

        void push(int value) {
            int i = size++;
            data[i] = value;
            while (i > 0) {
                int parent = (i - 1) >> 1;
                if (priority[data[parent]] <= priority[data[i]]) break;
                swap(parent, i);
                i = parent;
            }
        }

        int pop() {
            int top = data[0];
            data[0] = data[--size];
            int i = 0;
            while (true) {
                int left = 2 * i + 1, right = left + 1, smallest = i;
                if (left < size && priority[data[left]] < priority[data[smallest]]) smallest = left;
                if (right < size && priority[data[right]] < priority[data[smallest]]) smallest = right;
                if (smallest == i) break;
                swap(smallest, i);
                i = smallest;
            }
            return top;
        }

        private void swap(int a, int b) {
            int tmp = data[a];
            data[a] = data[b];
            data[b] = tmp;
        }
    }
}
