package com.reveny.trailhead.debug;

import com.reveny.trailhead.worldgen.terrain.Climate;
import com.reveny.trailhead.worldgen.terrain.TerrainContext;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TerrainDebugPreview {
    private TerrainDebugPreview() {}

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Config config = Config.parse(args);
        Files.createDirectories(config.out());
        TerrainContext ctx = TerrainContext.get(config.seed(), config.climate());

        if (config.worldMap() > 0) {
            WorldMap.write(config, ctx);
        }
        List<String> shots = new ArrayList<>();
        if (config.findRadius() > 0) {
            shots.addAll(ViewpointFinder.find(ctx, config.x(), config.z(), config.findRadius()));
            Files.write(config.out().resolve("shots.txt"), shots, StandardCharsets.UTF_8);
            shots.forEach(shot -> System.out.println("viewpoint " + shot));
        }
        if (config.shotsFile() != null) {
            for (String line : Files.readAllLines(config.shotsFile())) {
                if (!line.isBlank() && !line.startsWith("#")) shots.add(line.strip());
            }
        }
        if (config.fields()) {
            PreviewImages.writeFields(config, ctx);
        }
        if (config.face()) {
            PreviewImages.writeFace(config, ctx);
        }
        if (config.check() > 0 && !LandformCheck.run(ctx, config.seed(), config.check())) {
            System.err.println("terrain check failed: spikes or chunk seams found");
            System.exit(1);
        }
        if (config.fall()) {
            LandformCheck.inspectWaterfall(ctx, config.x(), config.z());
        }
        if (config.map()) {
            long start = System.nanoTime();
            PreviewArea area = PreviewArea.build(ctx, config.x() - config.width() / 2, config.z() - config.depth() / 2,
                    config.width(), config.depth());
            long elapsed = System.nanoTime() - start;
            System.out.printf(Locale.ROOT, "map built in %.1fs (%.2f ms/chunk)%n", elapsed / 1e9,
                    elapsed / 1e6 / (area.width / 16 * (area.depth / 16)));
            PreviewImages.writeMap(config, area);
        }
        for (String shot : shots) {
            if (shot.startsWith("cave_")) continue;
            String[] parts = shot.split("\\s+");
            PreviewImages.renderView(ctx, config.out(), parts[0],
                    Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]),
                    Float.parseFloat(parts[4]), Float.parseFloat(parts[5]));
        }
    }

    record Config(long seed, int x, int z, int width, int depth, Path out, Path shotsFile, int findRadius,
                  boolean map, boolean fields, boolean face, int check, boolean fall,
                  Climate.Mode climate, int worldMap) {
        static Config parse(String[] args) {
            long seed = 12345L;
            int x = 0, z = 0, width = 1024, depth = 1024, find = 0, check = 0, worldMap = 0;
            Path out = Path.of("build", "terrain-debug", "latest");
            Path shots = null;
            boolean map = true, fields = false, face = false, fall = false;
            Climate.Mode climate = Climate.Mode.LATITUDE;
            for (String arg : args) {
                if (!arg.startsWith("--") || !arg.contains("=")) continue;
                String[] keyValue = arg.substring(2).split("=", 2);
                String value = keyValue[1];
                switch (keyValue[0]) {
                    case "seed" -> seed = Long.parseLong(value);
                    case "x" -> x = Integer.parseInt(value);
                    case "z" -> z = Integer.parseInt(value);
                    case "width" -> width = Integer.parseInt(value);
                    case "depth" -> depth = Integer.parseInt(value);
                    case "out" -> out = Path.of(value);
                    case "shots" -> shots = Path.of(value);
                    case "find" -> find = Integer.parseInt(value);
                    case "map" -> map = Boolean.parseBoolean(value);
                    case "fields" -> fields = Boolean.parseBoolean(value);
                    case "face" -> face = Boolean.parseBoolean(value);
                    case "check" -> check = Integer.parseInt(value);
                    case "fall" -> fall = Boolean.parseBoolean(value);
                    case "climate" -> climate = Climate.Mode.parse(value);
                    case "worldmap" -> worldMap = Integer.parseInt(value);
                    default -> {}
                }
            }
            return new Config(seed, x, z, width, depth, out, shots, find, map, fields, face, check, fall, climate, worldMap);
        }
    }
}
