package com.reveny.trailhead.worldgen.terrain;

import com.reveny.trailhead.noise.NoiseHelper;

import java.util.concurrent.ConcurrentHashMap;

import static com.reveny.trailhead.noise.NoiseConfig.*;

public final class Groundwater {
    public record Cell(int level, int bottom) {
        boolean floods(int y, int floorShift) {
            return y <= level && y > bottom + floorShift;
        }

        boolean isFloor(int y, int floorShift) {
            int floor = bottom + floorShift;
            return y <= floor && y > floor - 2;
        }
    }

    private static final double COASTAL_REACH = 300.0;
    private static final double DRY_DEPTH = 45.0;
    private static final double WET_DEPTH = 4.0;
    private static final double THIN_LENS = 8.0;
    private static final double THICK_LENS = 30.0;
    private static final int LOWEST_INLAND_TABLE = SEA_LEVEL - 40;

    private final HeightField field;
    private final ConcurrentHashMap<Long, Cell> cells = new ConcurrentHashMap<>();

    Groundwater(HeightField field) {
        this.field = field;
    }

    public Cell cellAt(int x, int z) {
        return cell(Math.floorDiv(x, GROUNDWATER_CELL), Math.floorDiv(z, GROUNDWATER_CELL));
    }

    Cell cell(long cellX, long cellZ) {
        long key = (cellX << 32) ^ (cellZ & 0xFFFFFFFFL);
        Cell cached = cells.get(key);
        if (cached != null) return cached;
        Cell cell = compute((cellX + 0.5) * GROUNDWATER_CELL, (cellZ + 0.5) * GROUNDWATER_CELL);
        cells.put(key, cell);
        return cell;
    }

    private Cell compute(double x, double z) {
        HeightField.Column col = field.sampleWithoutRivers(x, z);
        double wetness = NoiseHelper.clamp((col.rain - 0.2) / 0.6, 0.0, 1.0);
        double lens = NoiseHelper.lerp(THIN_LENS, THICK_LENS, Math.min(1.0, wetness * col.karst * 1.4));
        if (col.height < SEA_LEVEL + 2) {
            return new Cell(SEA_LEVEL, (int) Math.floor(SEA_LEVEL - lens));
        }
        double table = col.height - NoiseHelper.lerp(DRY_DEPTH, WET_DEPTH, wetness);
        double coastal = 1.0 - NoiseHelper.smoothstep(0.0, COASTAL_REACH, col.coastDist);
        double floor = NoiseHelper.lerp(LOWEST_INLAND_TABLE, SEA_LEVEL, coastal);
        table = Math.min(Math.max(table, floor), col.height - 3.0);
        int level = (int) Math.floor(table);
        return new Cell(level, (int) Math.floor(level - lens));
    }
}
