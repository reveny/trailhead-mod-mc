package com.reveny.trailhead.debug;

import com.reveny.trailhead.noise.NoiseConfig;
import com.reveny.trailhead.worldgen.terrain.ChunkBuilder;
import com.reveny.trailhead.worldgen.terrain.HeightField;
import com.reveny.trailhead.worldgen.terrain.SurfacePalette;
import com.reveny.trailhead.worldgen.terrain.TerrainContext;
import net.minecraft.world.level.block.Blocks;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.IntStream;

import static com.reveny.trailhead.debug.PreviewColors.mix;
import static com.reveny.trailhead.debug.PreviewColors.scale;

final class PreviewImages {
    private static final int VIEW_WIDTH = 960, VIEW_HEIGHT = 540;
    private static final double FOV_Y = Math.toRadians(70);
    private static final double VIEW_DISTANCE = 900;
    private static final double SUN_X = -0.55, SUN_Y = 0.45, SUN_Z = 0.70;
    private static final int FACE_ROWS = 220;

    private PreviewImages() {}

    static void writeMap(TerrainDebugPreview.Config config, PreviewArea area) throws IOException {
        BufferedImage image = new BufferedImage(area.width, area.depth, BufferedImage.TYPE_INT_RGB);
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, water = 0;
        for (int z = 0; z < area.depth; z++) {
            for (int x = 0; x < area.width; x++) {
                int i = z * area.width + x;
                int y = area.topY[i];
                min = Math.min(min, area.floorY[i]);
                max = Math.max(max, y);
                double shade;
                if (area.water[i]) {
                    water++;
                    shade = 1.0 - Math.min(0.5, (y - area.floorY[i]) / 60.0);
                } else {
                    int west = x > 0 ? area.topY[i - 1] : y;
                    int north = z > 0 ? area.topY[i - area.width] : y;
                    shade = Math.max(0.45, Math.min(1.35, 1.0 + ((y - west) + (y - north)) * 0.08));
                }
                image.setRGB(x, z, scale(area.topRgb[i], shade));
            }
        }
        ImageIO.write(image, "png", config.out().resolve("surface.png").toFile());
        String summary = String.format(Locale.ROOT, "seed=%d%narea=%d,%d %dx%d%nminFloor=%d%nmaxTop=%d%nwaterFraction=%.3f%n",
                config.seed(), area.x0, area.z0, area.width, area.depth, min, max, water / (double) (area.width * area.depth));
        Files.writeString(config.out().resolve("summary.txt"), summary, StandardCharsets.UTF_8);
        System.out.print(summary);
    }

    static void writeFields(TerrainDebugPreview.Config config, TerrainContext ctx) throws IOException {
        int width = config.width() / 2, depth = config.depth() / 2;
        BufferedImage fields = new BufferedImage(width, depth, BufferedImage.TYPE_INT_RGB);
        IntStream.range(0, depth).parallel().forEach(j -> {
            for (int i = 0; i < width; i++) {
                double x = config.x() - config.width() / 2.0 + i * 2, z = config.z() - config.depth() / 2.0 + j * 2;
                HeightField.Column col = ctx.field.sample(x, z);
                double base = ctx.field.baseHeight(x, z);
                int r = (int) Math.max(0, Math.min(255, 128 + col.coastDist * 0.5));
                int g = (int) Math.max(0, Math.min(255, (base - col.height) * 4));
                int b = (int) Math.max(0, Math.min(255, (base - NoiseConfig.MIN_Y) * 255 / NoiseConfig.GEN_DEPTH));
                fields.setRGB(i, j, (r << 16) | (g << 8) | b);
            }
        });
        ImageIO.write(fields, "png", config.out().resolve("fields.png").toFile());

        int size = 768;
        BufferedImage provinces = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        IntStream.range(0, size).parallel().forEach(j -> {
            for (int i = 0; i < size; i++) {
                double x = config.x() + (i - size / 2) * 8.0, z = config.z() + (j - size / 2) * 8.0;
                HeightField.Column col = ctx.field.sampleWithoutRivers(x, z);
                provinces.setRGB(i, j, col.height < NoiseConfig.SEA_LEVEL ? 0x14243C : PreviewColors.of(col.province));
            }
        });
        ImageIO.write(provinces, "png", config.out().resolve("provinces.png").toFile());
    }

    static void writeFace(TerrainDebugPreview.Config config, TerrainContext ctx) throws IOException {
        HeightField field = ctx.field;
        double steepest = 0;
        int faceX = config.x(), faceZ = config.z(), facing = 1;
        for (int x = config.x() - 400; x <= config.x() + 400; x += 8) {
            for (int z = config.z() - 400; z <= config.z() + 400; z += 8) {
                double drop = field.sampleWithoutRivers(x + 6, z).height - field.sampleWithoutRivers(x - 6, z).height;
                if (Math.abs(drop) > steepest) {
                    steepest = Math.abs(drop);
                    faceX = x;
                    faceZ = z;
                    facing = drop > 0 ? -1 : 1;
                }
            }
        }
        System.out.printf(Locale.ROOT, "face at %d,%d drop %.0f facing %s%n", faceX, faceZ, steepest, facing > 0 ? "+X" : "-X");

        int width = 160, depth = 48;
        int x0 = Math.floorDiv(faceX - depth / 2, 16) * 16, z0 = Math.floorDiv(faceZ - width / 2, 16) * 16;
        int spanX = ((depth + 31) / 16) * 16;
        int height = NoiseConfig.GEN_DEPTH;
        boolean[] solid = new boolean[spanX * width * height];
        int[] color = new int[spanX * width * height];
        int chunksX = spanX / 16, chunksZ = width / 16;
        IntStream.range(0, chunksX * chunksZ).parallel().forEach(c -> {
            int cx = c % chunksX, cz = c / chunksX;
            new ChunkBuilder(ctx, x0 + cx * 16, z0 + cz * 16).build((lx, y, lz, state) -> {
                if (state.isAir() || state.is(Blocks.WATER) || !state.isSolid()) return;
                int i = ((cx * 16 + lx) * width + (cz * 16 + lz)) * height + (y - NoiseConfig.MIN_Y);
                solid[i] = true;
                color[i] = PreviewColors.of(state);
            });
        });

        int yBase = (int) Math.max(NoiseConfig.MIN_Y, field.sampleWithoutRivers(faceX, faceZ).height - 140);
        BufferedImage face = new BufferedImage(width * 3, FACE_ROWS * 3, BufferedImage.TYPE_INT_RGB);
        for (int u = 0; u < width; u++) {
            for (int v = 0; v < FACE_ROWS; v++) {
                int y = yBase + FACE_ROWS - 1 - v;
                int rgb = 0x9CC3E6;
                for (int k = 0; k < spanX; k++) {
                    int xi = facing > 0 ? spanX - 1 - k : k;
                    int i = (xi * width + u) * height + (y - NoiseConfig.MIN_Y);
                    if (y >= NoiseConfig.MIN_Y && y < NoiseConfig.MAX_Y && solid[i]) {
                        rgb = scale(color[i], Math.max(0.3, 1.2 - 0.06 * k));
                        break;
                    }
                }
                fillBlock(face, u * 3, v * 3, 3, rgb);
            }
        }
        ImageIO.write(face, "png", config.out().resolve("face.png").toFile());

        BufferedImage profiles = new BufferedImage(spanX * 4 * 4, FACE_ROWS * 4, BufferedImage.TYPE_INT_RGB);
        for (int s = 0; s < 4; s++) {
            int u = width / 2 + (s - 2) * 12;
            for (int xi = 0; xi < spanX; xi++) {
                for (int v = 0; v < FACE_ROWS; v++) {
                    int y = yBase + FACE_ROWS - 1 - v;
                    int i = (xi * width + u) * height + (y - NoiseConfig.MIN_Y);
                    boolean filled = y >= NoiseConfig.MIN_Y && y < NoiseConfig.MAX_Y && solid[i];
                    fillBlock(profiles, (s * spanX + xi) * 4, v * 4, 4, filled ? 0x606060 : 0xD8E6F2);
                }
            }
        }
        ImageIO.write(profiles, "png", config.out().resolve("profile.png").toFile());
    }

    private static void fillBlock(BufferedImage image, int x, int y, int size, int rgb) {
        for (int a = 0; a < size; a++) {
            for (int b = 0; b < size; b++) image.setRGB(x + a, y + b, rgb);
        }
    }

    static void renderView(TerrainContext ctx, Path out, String name, double cameraX, double cameraY, double cameraZ,
                           float yawDegrees, float pitchDegrees) throws IOException {
        long start = System.nanoTime();
        double yaw = Math.toRadians(yawDegrees), pitch = Math.toRadians(pitchDegrees);
        double forwardX = -Math.sin(yaw) * Math.cos(pitch), forwardY = -Math.sin(pitch), forwardZ = Math.cos(yaw) * Math.cos(pitch);
        double rightX = -Math.cos(yaw), rightZ = -Math.sin(yaw);
        double upX = -rightZ * forwardY, upY = rightZ * forwardX - rightX * forwardZ, upZ = rightX * forwardY;

        double headingX = -Math.sin(yaw), headingZ = Math.cos(yaw);
        double half = VIEW_DISTANCE * 0.75;
        double centerX = cameraX + headingX * VIEW_DISTANCE * 0.45, centerZ = cameraZ + headingZ * VIEW_DISTANCE * 0.45;
        PreviewArea area = PreviewArea.build(ctx, (int) (centerX - half), (int) (centerZ - half), (int) (2 * half), (int) (2 * half));

        BufferedImage image = new BufferedImage(VIEW_WIDTH, VIEW_HEIGHT, BufferedImage.TYPE_INT_RGB);
        double tanY = Math.tan(FOV_Y / 2), tanX = tanY * VIEW_WIDTH / VIEW_HEIGHT;
        long seed = ctx.seed;
        IntStream.range(0, VIEW_HEIGHT).parallel().forEach(py -> {
            for (int px = 0; px < VIEW_WIDTH; px++) {
                double u = (2.0 * (px + 0.5) / VIEW_WIDTH - 1) * tanX;
                double v = (1 - 2.0 * (py + 0.5) / VIEW_HEIGHT) * tanY;
                double dx = forwardX + rightX * u + upX * v, dy = forwardY + upY * v, dz = forwardZ + rightZ * u + upZ * v;
                double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
                image.setRGB(px, py, trace(area, seed, cameraX, cameraY, cameraZ, dx / length, dy / length, dz / length));
            }
        });
        ImageIO.write(image, "png", out.resolve("view_" + name + ".png").toFile());
        System.out.printf(Locale.ROOT, "view %s rendered in %.1fs%n", name, (System.nanoTime() - start) / 1e9);
    }

    private static int trace(PreviewArea area, long seed, double ox, double oy, double oz, double dx, double dy, double dz) {
        int x = (int) Math.floor(ox), z = (int) Math.floor(oz);
        int stepX = dx > 0 ? 1 : -1, stepZ = dz > 0 ? 1 : -1;
        double deltaX = Math.abs(1 / dx), deltaZ = Math.abs(1 / dz);
        double nextX = dx > 0 ? (x + 1 - ox) / dx : (ox - x) / -dx;
        double nextZ = dz > 0 ? (z + 1 - oz) / dz : (oz - z) / -dz;
        if (Double.isInfinite(nextX) || Double.isNaN(nextX)) nextX = Double.MAX_VALUE;
        if (Double.isInfinite(nextZ) || Double.isNaN(nextZ)) nextZ = Double.MAX_VALUE;
        double t = 0;
        int side = -1;
        while (t < VIEW_DISTANCE) {
            if (area.contains(x, z)) {
                int i = area.index(x, z);
                double topHeight = area.topY[i] + 1;
                double yEnter = oy + dy * t;
                if (yEnter < topHeight && side >= 0) {
                    return shadeSide(area, seed, i, x, (int) Math.floor(yEnter), z, side, t);
                }
                double yExit = oy + dy * Math.min(nextX, nextZ);
                if (yExit < topHeight && dy < 0) {
                    return shadeTop(area, i, (topHeight - oy) / dy, dy);
                }
            }
            if (nextX < nextZ) {
                t = nextX;
                nextX += deltaX;
                x += stepX;
                side = 0;
            } else {
                t = nextZ;
                nextZ += deltaZ;
                z += stepZ;
                side = 1;
            }
            if (oy + dy * t < NoiseConfig.MIN_Y) break;
        }
        return sky(dy);
    }

    private static int shadeTop(PreviewArea area, int i, double t, double dy) {
        int rgb = area.topRgb[i];
        double light = 0.55 + 0.45 * SUN_Y;
        if (area.water[i]) {
            double depth = area.topY[i] - area.floorY[i];
            rgb = mix(0x3C78B4, 0x123A66, Math.min(1, depth / 25.0));
            rgb = mix(rgb, 0xC8D8E8, Math.max(0, 1 + dy * 3) * 0.35);
            light = 1.0;
        }
        return fog(scale(rgb, light), t);
    }

    private static int shadeSide(PreviewArea area, long seed, int i, int x, int y, int z, int side, double t) {
        int top = area.topY[i];
        int rgb;
        if (area.water[i] && y > area.floorY[i]) {
            rgb = 0x2E6AA6;
        } else if (top - y < 1) {
            rgb = area.topRgb[i];
        } else {
            int topBlock = area.topRgb[i];
            boolean soil = topBlock == PreviewColors.of(Blocks.GRASS_BLOCK.defaultBlockState())
                    || topBlock == PreviewColors.of(Blocks.SNOW_BLOCK.defaultBlockState());
            rgb = soil && top - y <= 3 ? PreviewColors.of(Blocks.DIRT.defaultBlockState())
                    : PreviewColors.of(SurfacePalette.strata(seed, x, y, z, SurfacePalette.strataOffset(seed, x, z), area.province[i]));
        }
        double normalX = side == 0 ? 1 : 0, normalZ = side == 1 ? 1 : 0;
        double lambert = Math.abs(normalX * SUN_X + normalZ * SUN_Z);
        return fog(scale(rgb, 0.42 + 0.5 * lambert), t);
    }

    private static int sky(double dy) {
        return mix(0xE9D8B8, 0x6E8DB8, Math.max(0, Math.min(1, dy * 2.5)));
    }

    private static int fog(int rgb, double t) {
        return mix(rgb, 0xC9D3DD, 1 - Math.exp(-t / 700.0));
    }
}
