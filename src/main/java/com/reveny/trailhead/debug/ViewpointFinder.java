package com.reveny.trailhead.debug;

import com.reveny.trailhead.noise.NoiseConfig;
import com.reveny.trailhead.worldgen.terrain.HeightField;
import com.reveny.trailhead.worldgen.terrain.Province;
import com.reveny.trailhead.worldgen.terrain.TerrainContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

final class ViewpointFinder {
    private static final int LANDFORM_SEARCH_RADIUS = 6000;
    private static final int LANDFORM_STEP = 24;
    private static final int[][] DIAGONAL_AXES = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private ViewpointFinder() {}

    static List<String> find(TerrainContext ctx, int centerX, int centerZ, int radius) {
        HeightField field = ctx.field;
        int landformRadius = Math.min(radius, LANDFORM_SEARCH_RADIUS);
        double sea = NoiseConfig.SEA_LEVEL;
        double bestCliff = -1, bestCanyon = -1, bestPeak = -1;
        String cliff = null, canyon = null, peak = null;
        for (int x = centerX - landformRadius; x <= centerX + landformRadius; x += LANDFORM_STEP) {
            for (int z = centerZ - landformRadius; z <= centerZ + landformRadius; z += LANDFORM_STEP) {
                HeightField.Column col = field.sample(x, z);

                if (col.coastDist > 2 && col.coastDist < 30 && col.cliffiness > 0.75 && col.height - sea > bestCliff) {
                    double gx = field.coastDistance(x + 16, z) - field.coastDistance(x - 16, z);
                    double gz = field.coastDistance(x, z + 16) - field.coastDistance(x, z - 16);
                    double length = Math.hypot(gx, gz);
                    if (length > 1e-6) {
                        bestCliff = col.height - sea;
                        cliff = frame("cliff", x, col.height, z, -gx / length, -gz / length, 170, 70);
                    }
                }

                if (col.inRiver() && col.water > sea + 4) {
                    double rim = 0;
                    int[] axis = null;
                    double axisLow = Double.MAX_VALUE;
                    for (int[] d : DIAGONAL_AXES) {
                        double h1 = field.sample(x + d[0] * 40, z + d[1] * 40).height;
                        double h2 = field.sample(x - d[0] * 40, z - d[1] * 40).height;
                        rim = Math.max(rim, Math.min(h1, h2) - col.water);
                        if (Math.max(h1, h2) < axisLow) {
                            axisLow = Math.max(h1, h2);
                            axis = d;
                        }
                    }
                    double score = rim * (0.5 + col.canyon);
                    if (score > bestCanyon && axis != null) {
                        bestCanyon = score;
                        double length = Math.hypot(axis[0], axis[1]);
                        canyon = frame("canyon", x, col.water, z, axis[0] / length, axis[1] / length, 130, rim + 30);
                    }
                }

                if (col.height > bestPeak) {
                    bestPeak = col.height;
                    peak = frame("mountain", x, col.height, z, 0.6, 0.8, 420, 20 - (col.height - 180));
                }
            }
        }
        List<String> views = new ArrayList<>();
        String waterfall = biggestWaterfall(ctx, centerX, centerZ, landformRadius);
        for (String view : new String[]{cliff, canyon, waterfall, peak}) {
            if (view != null) views.add(clearTerrain(field, view));
        }
        List<String> regional = regionalViews(field, centerX, centerZ, radius);
        views.addAll(regional);
        views.addAll(CaveViewFinder.find(ctx, regional, centerX, centerZ));
        return views;
    }

    private static List<String> regionalViews(HeightField field, int centerX, int centerZ, int radius) {
        Province[] provinces = Province.values();
        int step = Math.max(256, radius / 180);
        int n = 2 * radius / step + 1;
        int[] grid = new int[n * n];
        double[] volcanic = new double[n * n], atoll = new double[n * n];
        IntStream.range(0, n).parallel().forEach(j -> {
            for (int i = 0; i < n; i++) {
                HeightField.Column col = field.sampleWithoutRivers(centerX - radius + i * step, centerZ - radius + j * step);
                grid[j * n + i] = col.height < NoiseConfig.SEA_LEVEL ? -1 : col.province.ordinal();
                volcanic[j * n + i] = col.height > NoiseConfig.SEA_LEVEL + 40 ? col.volcanic : 0;
                atoll[j * n + i] = col.height >= NoiseConfig.SEA_LEVEL - 1 ? col.atoll : 0;
            }
        });

        List<String> views = new ArrayList<>();
        for (int p = 0; p < provinces.length; p++) {
            int cell = nearestPureCell(grid, n, p);
            if (cell < 0) continue;
            int x = centerX - radius + (cell % n) * step, z = centerZ - radius + (cell / n) * step;
            String prefix = "p_" + provinces[p].name().toLowerCase(Locale.ROOT) + " ";
            for (String view : perProvince(field, x, z, 1600)) {
                if (view.startsWith(prefix)) views.add(view);
            }
        }

        int volcanoCell = nearestAbove(volcanic, n, 0.8);
        if (volcanoCell >= 0) {
            int x = centerX - radius + (volcanoCell % n) * step, z = centerZ - radius + (volcanoCell / n) * step;
            double top = field.sampleWithoutRivers(x, z).height;
            views.add(clearTerrain(field, frame("volcano", x, top - 40, z, 0.7, 0.7, 700, -10)));
        }
        int atollCell = nearestAbove(atoll, n, 0.6);
        if (atollCell >= 0) {
            int x = centerX - radius + (atollCell % n) * step, z = centerZ - radius + (atollCell / n) * step;
            views.add(frame("atoll", x, NoiseConfig.SEA_LEVEL, z, 0.6, 0.8, 260, 110));
        }
        return views;
    }

    private static int nearestPureCell(int[] grid, int n, int province) {
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int j = 2; j < n - 2; j++) {
            for (int i = 2; i < n - 2; i++) {
                boolean pure = true;
                for (int dj = -2; dj <= 2 && pure; dj++) {
                    for (int di = -2; di <= 2 && pure; di++) pure = grid[(j + dj) * n + i + di] == province;
                }
                if (!pure) continue;
                double distance = Math.hypot(i - n / 2.0, j - n / 2.0);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = j * n + i;
                }
            }
        }
        return best;
    }

    private static int nearestAbove(double[] values, int n, double threshold) {
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int k = 0; k < n * n; k++) {
            double distance = Math.hypot(k % n - n / 2.0, k / n - n / 2.0);
            if (values[k] > threshold && distance < bestDistance) {
                bestDistance = distance;
                best = k;
            }
        }
        return best;
    }

    private static List<String> perProvince(HeightField field, int centerX, int centerZ, int radius) {
        int count = Province.values().length;
        double[] best = new double[count];
        String[] views = new String[count];
        Arrays.fill(best, -1);
        int step = 128;
        for (int x = centerX - radius; x <= centerX + radius; x += step) {
            for (int z = centerZ - radius; z <= centerZ + radius; z += step) {
                HeightField.Column col = field.sampleWithoutRivers(x, z);
                if (col.height < NoiseConfig.SEA_LEVEL + 3) continue;
                if (field.sample(x, z).inRiver()) continue;
                double gx = field.sampleWithoutRivers(x + 12, z).height - field.sampleWithoutRivers(x - 12, z).height;
                double gz = field.sampleWithoutRivers(x, z + 12).height - field.sampleWithoutRivers(x, z - 12).height;
                if (Math.hypot(gx, gz) > 14) continue;
                if (!deepInside(field, x, z, col.province)) continue;

                double topRise = -1, topX = 0, topZ = 0, topHeight = 0;
                for (int a = 0; a < 16; a++) {
                    double angle = a * Math.PI / 8;
                    for (int distance = 220; distance <= 520; distance += 40) {
                        double tx = x + Math.cos(angle) * distance, tz = z + Math.sin(angle) * distance;
                        double targetHeight = field.sampleWithoutRivers(tx, tz).height;
                        double rise = targetHeight - col.height;
                        if (rise / distance < 0.47 && rise > topRise
                                && clearView(field, x, col.height + 20, z, tx, col.height + rise * 0.45, tz)) {
                            topRise = rise;
                            topX = tx;
                            topZ = tz;
                            topHeight = targetHeight;
                        }
                    }
                }
                int p = col.province.ordinal();
                if (topRise > best[p]) {
                    best[p] = topRise;
                    double eye = col.height + 20;
                    double targetY = col.height + (topHeight - col.height) * 0.45;
                    double dx = topX - x, dz = topZ - z;
                    float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                    float pitch = (float) Math.max(-16.0, Math.toDegrees(-Math.atan2(targetY - eye, Math.hypot(dx, dz))));
                    views[p] = String.format(Locale.ROOT, "p_%s %.1f %.1f %.1f %.1f %.1f",
                            col.province.name().toLowerCase(Locale.ROOT), (double) x, eye, (double) z, yaw, pitch);
                }
            }
        }
        List<String> result = new ArrayList<>();
        for (String view : views) {
            if (view != null) result.add(clearTerrain(field, view));
        }
        return result;
    }

    private static boolean deepInside(HeightField field, int x, int z, Province province) {
        for (int[] side : SIDES) {
            if (field.sampleWithoutRivers(x + side[0] * 300, z + side[1] * 300).province != province) return false;
        }
        return true;
    }

    private static String biggestWaterfall(TerrainContext ctx, int centerX, int centerZ, int radius) {
        double[] best = null;
        int step = NoiseConfig.RIVER_TILE_SIZE;
        for (int x = centerX - radius; x <= centerX + radius; x += step) {
            for (int z = centerZ - radius; z <= centerZ + radius; z += step) {
                for (double[] fall : ctx.rivers.waterfalls(x, z)) {
                    if (Math.abs(fall[0] - centerX) > radius || Math.abs(fall[1] - centerZ) > radius) continue;
                    if (best == null || fall[5] > best[5]) best = fall;
                }
            }
        }
        if (best == null) return null;
        double dx = best[2] - best[0], dz = best[3] - best[1], length = Math.hypot(dx, dz);
        if (length < 1e-6) return null;
        System.out.printf(Locale.ROOT, "waterfall at %.0f,%.0f drop %.1f%n", best[0], best[1], best[5]);
        double targetY = best[4] + best[5] * 0.5;
        return frame("waterfall", best[0], targetY, best[1], dx / length, dz / length, 45 + best[5], 6 + best[5] * 0.3);
    }

    private static boolean clearView(HeightField field, double ex, double ey, double ez, double tx, double ty, double tz) {
        double distance = Math.hypot(tx - ex, tz - ez);
        for (double s = 6; s < distance * 0.85; s += 6) {
            double t = s / distance;
            double height = field.sampleWithoutRivers(ex + (tx - ex) * t, ez + (tz - ez) * t).height;
            if (height > ey + (ty - ey) * t - 2) return false;
        }
        return true;
    }

    private static String clearTerrain(HeightField field, String view) {
        String[] parts = view.split(" ");
        double x = Double.parseDouble(parts[1]), y = Double.parseDouble(parts[2]), z = Double.parseDouble(parts[3]);
        double ground = -Double.MAX_VALUE;
        for (int dx = -8; dx <= 8; dx += 4) {
            for (int dz = -8; dz <= 8; dz += 4) {
                HeightField.Column col = field.sample(x + dx, z + dz);
                ground = Math.max(ground, col.inRiver() ? Math.max(col.height, col.water) : col.height);
            }
        }
        if (y >= ground + 12) return view;
        return String.format(Locale.ROOT, "%s %.1f %.1f %.1f %s %s", parts[0], x, ground + 12, z, parts[4], parts[5]);
    }

    private static String frame(String name, double tx, double ty, double tz, double offsetX, double offsetZ,
                                double distance, double up) {
        double cameraX = tx + offsetX * distance, cameraZ = tz + offsetZ * distance;
        double cameraY = Math.min(NoiseConfig.MAX_Y + 40, Math.max(ty + up, NoiseConfig.SEA_LEVEL + 6));
        double dx = tx - cameraX, dz = tz - cameraZ, dy = ty - cameraY;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.hypot(dx, dz)));
        return String.format(Locale.ROOT, "%s %.1f %.1f %.1f %.1f %.1f", name, cameraX, cameraY, cameraZ, yaw, pitch);
    }
}
