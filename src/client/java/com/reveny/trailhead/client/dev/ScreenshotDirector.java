package com.reveny.trailhead.client.dev;

import com.reveny.trailhead.TrailheadMod;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ScreenshotDirector {
    public static final String PROPERTY = "trailhead.autoshot";

    private static final int MIN_WAIT_TICKS = Integer.getInteger(PROPERTY + ".wait", 160);
    private static final int STABLE_TICKS = 60;
    private static final int MAX_WAIT_TICKS = 2400;
    private static final int QUIT_DELAY_TICKS = 40;
    private static final int NIGHT_VISION_TICKS = 20 * 600;

    private record Viewpoint(String name, double x, double y, double z, float yaw, float pitch, long dayTime,
                             boolean nightVision) {}

    private final List<Viewpoint> viewpoints;
    private final String tag;
    private int index = -1;
    private int waited;
    private int stable;
    private int quitDelay = -1;

    private ScreenshotDirector(List<Viewpoint> viewpoints, String tag) {
        this.viewpoints = viewpoints;
        this.tag = tag;
    }

    public static void install(String file) {
        List<Viewpoint> viewpoints;
        try {
            viewpoints = readViewpoints(Path.of(file));
        } catch (IOException e) {
            TrailheadMod.LOGGER.error("Autoshot: cannot read {}", file, e);
            return;
        }
        String tag = System.getProperty(PROPERTY + ".tag", "capture");
        ScreenshotDirector director = new ScreenshotDirector(viewpoints, tag);
        ClientTickEvents.END_CLIENT_TICK.register(director::tick);
        TrailheadMod.LOGGER.info("Autoshot: {} viewpoints loaded, tag={}", viewpoints.size(), tag);
    }

    private static List<Viewpoint> readViewpoints(Path file) throws IOException {
        List<Viewpoint> viewpoints = new ArrayList<>();
        for (String line : Files.readAllLines(file)) {
            line = line.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("\\s+");
            viewpoints.add(new Viewpoint(parts[0],
                    Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]),
                    Float.parseFloat(parts[4]), Float.parseFloat(parts[5]),
                    parts.length > 6 ? Long.parseLong(parts[6]) : 1000L,
                    parts.length > 7 && parts[7].equals("nv")));
        }
        return viewpoints;
    }

    private void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;

        if (quitDelay >= 0) {
            if (quitDelay-- == 0) mc.stop();
            return;
        }

        if (index < 0) {
            mc.options.hideGui = true;
            mc.options.pauseOnLostFocus = false;
            mc.options.cloudStatus().set(CloudStatus.OFF);
            mc.options.renderDistance().set(16);
            advance(mc);
            return;
        }

        if (mc.screen instanceof PauseScreen) {
            mc.setScreen(null);
            stable = 0;
        }
        waited++;
        stable = mc.levelRenderer.hasRenderedAllSections() ? stable + 1 : 0;
        boolean ready = waited >= MIN_WAIT_TICKS && stable >= STABLE_TICKS;
        if (!ready && waited < MAX_WAIT_TICKS) return;

        Viewpoint viewpoint = viewpoints.get(index);
        logGround(mc, viewpoint);
        Screenshot.grab(mc.gameDirectory, tag + "_" + viewpoint.name() + ".png", mc.getMainRenderTarget(), 1,
                message -> TrailheadMod.LOGGER.info("Autoshot: {} -> {}", viewpoint.name(), message.getString()));
        TrailheadMod.LOGGER.info("Autoshot: captured {} after {} ticks (settled={})", viewpoint.name(), waited, ready);
        advance(mc);
    }

    private static void logGround(Minecraft mc, Viewpoint viewpoint) {
        MinecraftServer server = mc.getSingleplayerServer();
        server.execute(() -> {
            ServerLevel level = server.overworld();
            int x = (int) Math.floor(viewpoint.x()), z = (int) Math.floor(viewpoint.z());
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            Holder<Biome> biome = level.getBiome(pos);
            TrailheadMod.LOGGER.info("Autoshot water {}: flowing blocks (1/4 sampled) = {}",
                    viewpoint.name(), countFlowingWater(level, x, (int) viewpoint.y(), z));
            TrailheadMod.LOGGER.info("Autoshot ground {}: y={} top={} below={} biome={} temp={} precip={}",
                    viewpoint.name(), y, level.getBlockState(pos).getBlock(), level.getBlockState(pos.below()).getBlock(),
                    biome.unwrapKey().map(key -> key.location().toString()).orElse("?"),
                    biome.value().getBaseTemperature(),
                    biome.value().getPrecipitationAt(pos, level.getSeaLevel()));
        });
    }

    private static int countFlowingWater(ServerLevel level, int x, int y, int z) {
        int flowing = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -64; dx <= 64; dx += 2) {
            for (int dz = -64; dz <= 64; dz += 2) {
                for (int dy = -80; dy <= 20; dy++) {
                    FluidState fluid = level.getFluidState(pos.set(x + dx, y + dy, z + dz));
                    if (!fluid.isEmpty() && !fluid.isSource()) flowing++;
                }
            }
        }
        return flowing;
    }

    private void advance(Minecraft mc) {
        index++;
        waited = 0;
        stable = 0;
        if (index >= viewpoints.size()) {
            TrailheadMod.LOGGER.info("Autoshot: done");
            quitDelay = QUIT_DELAY_TICKS;
            return;
        }
        Viewpoint viewpoint = viewpoints.get(index);
        MinecraftServer server = mc.getSingleplayerServer();
        UUID playerId = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) return;
            ServerLevel level = server.overworld();
            player.setGameMode(GameType.SPECTATOR);
            if (viewpoint.nightVision()) {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, NIGHT_VISION_TICKS, 0, false, false));
            } else {
                player.removeEffect(MobEffects.NIGHT_VISION);
            }
            level.setDayTime(viewpoint.dayTime());
            level.setWeatherParameters(12000, 0, false, false);
            player.teleportTo(level, viewpoint.x(), viewpoint.y(), viewpoint.z(), Set.of(), viewpoint.yaw(), viewpoint.pitch(), false);
        });
    }
}
