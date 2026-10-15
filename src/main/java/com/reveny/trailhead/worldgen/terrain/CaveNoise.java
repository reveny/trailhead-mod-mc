package com.reveny.trailhead.worldgen.terrain;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

final class CaveNoise {
    private static final double CHEESE_DEPTH_OFFSET = 1.2;
    private static final double CHEESE_DEPTH_SCALE = 24.0;
    private static final BlockState COPPER_ORE = Blocks.COPPER_ORE.defaultBlockState();
    private static final BlockState RAW_COPPER = Blocks.RAW_COPPER_BLOCK.defaultBlockState();
    private static final BlockState COPPER_FILLER = Blocks.GRANITE.defaultBlockState();
    private static final BlockState IRON_ORE = Blocks.DEEPSLATE_IRON_ORE.defaultBlockState();
    private static final BlockState RAW_IRON = Blocks.RAW_IRON_BLOCK.defaultBlockState();
    private static final BlockState IRON_FILLER = Blocks.TUFF.defaultBlockState();

    private static volatile HolderLookup.Provider registries;

    private final DensityFunction caveLayer;
    private final DensityFunction cheese;
    private final DensityFunction entrances;
    private final DensityFunction spaghetti2d;
    private final DensityFunction spaghettiRoughness;
    private final DensityFunction pillars;
    private final DensityFunction noodle;
    private final DensityFunction veinToggle;
    private final DensityFunction veinRidged;
    private final DensityFunction veinGap;
    private final PositionalRandomFactory oreRandom;

    CaveNoise(long seed) {
        HolderLookup.Provider lookup = registries();
        RandomState randomState = RandomState.create(lookup, NoiseGeneratorSettings.OVERWORLD, seed);
        HolderGetter<NormalNoise.NoiseParameters> noises = lookup.lookupOrThrow(Registries.NOISE);
        HolderGetter<DensityFunction> functions = lookup.lookupOrThrow(Registries.DENSITY_FUNCTION);
        Wiring wiring = new Wiring(randomState);

        caveLayer = wiring.wire(DensityFunctions.noise(noises.getOrThrow(Noises.CAVE_LAYER), 8.0));
        cheese = wiring.wire(DensityFunctions.noise(noises.getOrThrow(Noises.CAVE_CHEESE), 0.6666666666666666));
        entrances = wiring.wire(registered(functions, "overworld/caves/entrances"));
        spaghetti2d = wiring.wire(registered(functions, "overworld/caves/spaghetti_2d"));
        spaghettiRoughness = wiring.wire(registered(functions, "overworld/caves/spaghetti_roughness_function"));
        pillars = wiring.wire(registered(functions, "overworld/caves/pillars"));
        noodle = wiring.wire(registered(functions, "overworld/caves/noodle"));

        NoiseRouter router = randomState.router();
        veinToggle = router.veinToggle();
        veinRidged = router.veinRidged();
        veinGap = router.veinGap();
        oreRandom = randomState.oreRandom();
    }

    private static HolderLookup.Provider registries() {
        HolderLookup.Provider lookup = registries;
        if (lookup == null) {
            synchronized (CaveNoise.class) {
                lookup = registries;
                if (lookup == null) {
                    lookup = VanillaRegistries.createLookup();
                    registries = lookup;
                }
            }
        }
        return lookup;
    }

    private static DensityFunction registered(HolderGetter<DensityFunction> functions, String path) {
        ResourceKey<DensityFunction> key = ResourceKey.create(Registries.DENSITY_FUNCTION, ResourceLocation.withDefaultNamespace(path));
        return new DensityFunctions.HolderHolder(functions.getOrThrow(key));
    }

    double density(int x, int y, int z, double depthBelowSurface) {
        DensityFunction.SinglePointContext at = new DensityFunction.SinglePointContext(x, y, z);
        double layer = caveLayer.compute(at);
        double depthTerm = CHEESE_DEPTH_OFFSET + Math.max(0.0, depthBelowSurface) / CHEESE_DEPTH_SCALE;
        double caverns = 4.0 * layer * layer
                + Mth.clamp(0.27 + cheese.compute(at), -1.0, 1.0)
                + Mth.clamp(1.5 - 0.64 * depthTerm, 0.0, 0.5);
        double tunnels = spaghetti2d.compute(at) + spaghettiRoughness.compute(at);
        double carved = Math.min(Math.min(caverns, entrances.compute(at)), tunnels);
        double pillar = pillars.compute(at);
        double underground = pillar >= 0.03 ? Math.max(carved, pillar) : carved;
        return Math.min(underground, noodle.compute(at));
    }

    double veinToggle(int x, int y, int z) {
        return veinToggle.compute(new DensityFunction.SinglePointContext(x, y, z));
    }

    BlockState vein(int x, int y, int z, double toggle) {
        boolean copper = toggle > 0.0;
        int minY = copper ? 0 : -60;
        int maxY = copper ? 50 : -8;
        int fromTop = maxY - y, fromBottom = y - minY;
        if (fromBottom < 0 || fromTop < 0) return null;
        double edge = Mth.clampedMap(Math.min(fromTop, fromBottom), 0.0, 20.0, -0.2, 0.0);
        double strength = Math.abs(toggle);
        if (strength + edge < 0.4) return null;
        RandomSource random = oreRandom.at(x, y, z);
        if (random.nextFloat() > 0.7F) return null;
        DensityFunction.SinglePointContext at = new DensityFunction.SinglePointContext(x, y, z);
        if (veinRidged.compute(at) >= 0.0) return null;
        double richness = Mth.clampedMap(strength, 0.4, 0.6, 0.1, 0.3);
        if (random.nextFloat() < richness && veinGap.compute(at) > -0.3) {
            if (random.nextFloat() < 0.02F) return copper ? RAW_COPPER : RAW_IRON;
            return copper ? COPPER_ORE : IRON_ORE;
        }
        return copper ? COPPER_FILLER : IRON_FILLER;
    }

    private static final class Wiring implements DensityFunction.Visitor {
        private final RandomState randomState;

        Wiring(RandomState randomState) {
            this.randomState = randomState;
        }

        DensityFunction wire(DensityFunction function) {
            return function.mapAll(this);
        }

        @Override
        public DensityFunction apply(DensityFunction function) {
            if (function instanceof DensityFunctions.HolderHolder holder) return holder.function().value();
            return function;
        }

        @Override
        public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder holder) {
            ResourceKey<NormalNoise.NoiseParameters> key = holder.noiseData().unwrapKey().orElseThrow();
            return new DensityFunction.NoiseHolder(holder.noiseData(), randomState.getOrCreateNoise(key));
        }
    }
}
