package com.reveny.trailhead;

import com.reveny.trailhead.worldgen.TrailheadBiomeSource;
import com.reveny.trailhead.worldgen.TrailheadChunkGenerator;
import com.reveny.trailhead.worldgen.feature.ConiferFeature;
import com.reveny.trailhead.worldgen.feature.SnowIceSpikeFeature;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrailheadMod implements ModInitializer {
    public static final String MOD_ID = "trailhead";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static volatile long worldSeed = 0;

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, id("terrain"), TrailheadChunkGenerator.CODEC);
        Registry.register(BuiltInRegistries.BIOME_SOURCE, id("terrain"), TrailheadBiomeSource.CODEC);
        Registry.register(BuiltInRegistries.FEATURE, id("snow_ice_spike"), new SnowIceSpikeFeature(NoneFeatureConfiguration.CODEC));
        Registry.register(BuiltInRegistries.FEATURE, id("conifer"), new ConiferFeature(ConiferFeature.Config.CODEC));

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            worldSeed = server.getWorldData().worldGenOptions().seed();
            LOGGER.info("World seed {}", worldSeed);
        });
    }
}
