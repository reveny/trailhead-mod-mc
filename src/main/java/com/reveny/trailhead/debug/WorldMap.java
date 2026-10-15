package com.reveny.trailhead.debug;

import com.reveny.trailhead.noise.NoiseConfig;
import com.reveny.trailhead.worldgen.terrain.HeightField;
import com.reveny.trailhead.worldgen.terrain.Province;
import com.reveny.trailhead.worldgen.terrain.TerrainContext;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.IntStream;

final class WorldMap {
    private static final int SIZE = 800;
    private static final int FORBIDDEN_DISTANCE = 2000;
    private static final Province[][] FORBIDDEN_PAIRS = {
            {Province.DESERT, Province.ICE_SHEET}, {Province.DESERT, Province.TUNDRA}, {Province.DESERT, Province.BOREAL},
            {Province.DESERT, Province.JUNGLE}, {Province.DESERT, Province.CASCADIA}, {Province.DESERT, Province.FJORDLAND},
            {Province.JUNGLE, Province.ICE_SHEET}, {Province.JUNGLE, Province.TUNDRA}, {Province.JUNGLE, Province.BOREAL},
            {Province.JUNGLE, Province.STEPPE},
            {Province.ICE_SHEET, Province.SAVANNA}, {Province.ICE_SHEET, Province.CANYONLANDS},
            {Province.ICE_SHEET, Province.STEPPE}, {Province.ICE_SHEET, Province.PRAIRIE},
            {Province.SAVANNA, Province.TUNDRA}, {Province.SAVANNA, Province.BOREAL}, {Province.CANYONLANDS, Province.TUNDRA}};

    private WorldMap() {}

    static void write(TerrainDebugPreview.Config config, TerrainContext ctx) throws IOException {
        int step = config.worldMap();
        int[] provinceAt = new int[SIZE * SIZE];
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        IntStream.range(0, SIZE).parallel().forEach(j -> {
            for (int i = 0; i < SIZE; i++) {
                double x = config.x() + (i - SIZE / 2) * (double) step, z = config.z() + (j - SIZE / 2) * (double) step;
                HeightField.Column col = ctx.field.sampleWithoutRivers(x, z);
                int rgb;
                if (col.height < NoiseConfig.SEA_LEVEL) {
                    provinceAt[j * SIZE + i] = -1;
                    rgb = col.temperature < 0.14 ? 0xC8DCEB : PreviewColors.mix(0x0B2A5A, 0x2E8FB0, col.temperature);
                } else {
                    provinceAt[j * SIZE + i] = col.province.ordinal();
                    double elevation = Math.min(1.0, (col.height - NoiseConfig.SEA_LEVEL) / 150.0);
                    rgb = PreviewColors.scale(PreviewColors.of(col.province), 0.8 + 0.4 * elevation);
                }
                image.setRGB(i, j, rgb);
            }
        });
        ImageIO.write(image, "png", config.out().resolve("worldmap_" + ctx.mode.id() + ".png").toFile());
        printProvinceShares(provinceAt);
        Map<String, Integer> hits = forbiddenNeighbours(provinceAt, Math.max(1, FORBIDDEN_DISTANCE / step));
        System.out.println("forbidden neighbours within 2 km: " + (hits.isEmpty() ? "none" : hits));
    }

    private static void printProvinceShares(int[] provinceAt) {
        Province[] provinces = Province.values();
        int[] counts = new int[provinces.length];
        int land = 0;
        for (int p : provinceAt) {
            if (p < 0) continue;
            counts[p]++;
            land++;
        }
        StringBuilder line = new StringBuilder("province share of land:");
        for (int k = 0; k < provinces.length; k++) {
            line.append(String.format(Locale.ROOT, " %s=%.1f%%", provinces[k], 100.0 * counts[k] / Math.max(1, land)));
        }
        System.out.println(line);
    }

    private static Map<String, Integer> forbiddenNeighbours(int[] provinceAt, int radius) {
        Province[] provinces = Province.values();
        Set<String> forbidden = new HashSet<>();
        for (Province[] pair : FORBIDDEN_PAIRS) {
            forbidden.add(pair[0] + "|" + pair[1]);
            forbidden.add(pair[1] + "|" + pair[0]);
        }
        int stride = Math.max(1, radius / 4);
        Map<String, Integer> hits = new TreeMap<>();
        for (int j = 0; j < SIZE; j += 2) {
            for (int i = 0; i < SIZE; i += 2) {
                int a = provinceAt[j * SIZE + i];
                if (a < 0) continue;
                for (int dj = -radius; dj <= radius; dj += stride) {
                    for (int di = -radius; di <= radius; di += stride) {
                        int ni = i + di, nj = j + dj;
                        if (ni < 0 || nj < 0 || ni >= SIZE || nj >= SIZE) continue;
                        int b = provinceAt[nj * SIZE + ni];
                        if (b < 0 || b == a) continue;
                        String key = provinces[a] + "|" + provinces[b];
                        if (forbidden.contains(key)) hits.merge(key, 1, Integer::sum);
                    }
                }
            }
        }
        return hits;
    }
}
