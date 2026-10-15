package com.reveny.trailhead.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.reveny.trailhead.TrailheadMod;
import com.reveny.trailhead.worldgen.terrain.CaveType;
import com.reveny.trailhead.worldgen.terrain.Climate;
import com.reveny.trailhead.worldgen.terrain.HeightField;
import com.reveny.trailhead.worldgen.terrain.Province;
import com.reveny.trailhead.worldgen.terrain.SubalpineHeath;
import com.reveny.trailhead.worldgen.terrain.TerrainContext;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;

import java.util.Arrays;
import java.util.stream.Stream;

import static com.reveny.trailhead.noise.NoiseConfig.SEA_LEVEL;

public class TrailheadBiomeSource extends BiomeSource {
    public static final MapCodec<TrailheadBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    RegistryOps.retrieveGetter(Registries.BIOME),
                    Codec.STRING.optionalFieldOf("climate", "latitude").forGetter(source -> source.mode.id())
            ).apply(instance, (getter, climate) -> new TrailheadBiomeSource(getter, Climate.Mode.parse(climate)))
    );

    static final ResourceKey<Biome> COASTAL_GRASSLAND = key("coastal_grassland");
    static final ResourceKey<Biome> FJORD_FOREST = key("fjord_forest");
    static final ResourceKey<Biome> FJORD_HIGHLAND = key("fjord_highland");
    static final ResourceKey<Biome> ALPINE_MEADOW = key("alpine_meadow");
    static final ResourceKey<Biome> SUBALPINE_FOREST = key("subalpine_forest");
    static final ResourceKey<Biome> ALPINE_SCREE = key("alpine_scree");
    static final ResourceKey<Biome> ALPINE_SUMMIT = key("alpine_summit");
    static final ResourceKey<Biome> VALLEY_FOREST = key("valley_forest");
    static final ResourceKey<Biome> AUTUMN_FOREST = key("autumn_forest");
    static final ResourceKey<Biome> TEMPERATE_MEADOW = key("temperate_meadow");
    static final ResourceKey<Biome> TEMPERATE_WOODLAND = key("temperate_woodland");
    static final ResourceKey<Biome> SAGEBRUSH_FLATS = key("sagebrush_flats");
    static final ResourceKey<Biome> ASPEN_FOREST = key("aspen_forest");
    static final ResourceKey<Biome> TEMPERATE_RAINFOREST = key("temperate_rainforest");
    static final ResourceKey<Biome> LARCH_BASIN = key("larch_basin");
    static final ResourceKey<Biome> CANYON_RIM = key("canyon_rim");
    static final ResourceKey<Biome> CANYON_FLOOR = key("canyon_floor");
    static final ResourceKey<Biome> PRAIRIE = key("prairie");
    static final ResourceKey<Biome> STEPPE = key("steppe");
    static final ResourceKey<Biome> SAVANNA = key("savanna");
    static final ResourceKey<Biome> SAND_SEA = key("sand_sea");
    static final ResourceKey<Biome> ROCKY_DESERT = key("rocky_desert");
    static final ResourceKey<Biome> TROPICAL_RAINFOREST = key("tropical_rainforest");
    static final ResourceKey<Biome> MANGROVE_COAST = key("mangrove_coast");
    static final ResourceKey<Biome> TROPICAL_ISLAND = key("tropical_island");
    static final ResourceKey<Biome> VOLCANIC_HIGHLAND = key("volcanic_highland");
    static final ResourceKey<Biome> BOREAL_FOREST = key("boreal_forest");
    static final ResourceKey<Biome> TUNDRA = key("tundra");
    static final ResourceKey<Biome> ICE_SHEET = key("ice_sheet");
    static final ResourceKey<Biome> DESERT_CAVES = key("desert_caves");
    static final ResourceKey<Biome> ICE_CAVES = key("ice_caves");
    static final ResourceKey<Biome> HEATH_CRIMSON = key("subalpine_heath_crimson");
    static final ResourceKey<Biome> HEATH_EMBER = key("subalpine_heath_ember");
    static final ResourceKey<Biome> HEATH_GOLD = key("subalpine_heath_gold");
    static final ResourceKey<Biome> GRANITE_VALLEY_FLOOR = key("granite_valley_floor");
    static final ResourceKey<Biome> GRANITE_HIGHLAND = key("granite_highland");

    private static final ResourceKey<Biome>[] ALL_BIOMES = biomes(
            Biomes.LUSH_CAVES, Biomes.DRIPSTONE_CAVES, Biomes.DEEP_DARK, DESERT_CAVES, ICE_CAVES,
            Biomes.WARM_OCEAN, Biomes.LUKEWARM_OCEAN, Biomes.DEEP_LUKEWARM_OCEAN, Biomes.OCEAN,
            Biomes.DEEP_OCEAN, Biomes.COLD_OCEAN, Biomes.DEEP_COLD_OCEAN, Biomes.FROZEN_OCEAN,
            Biomes.DEEP_FROZEN_OCEAN, Biomes.BEACH, Biomes.SNOWY_BEACH,
            COASTAL_GRASSLAND, FJORD_FOREST, FJORD_HIGHLAND, ALPINE_MEADOW, SUBALPINE_FOREST,
            ALPINE_SCREE, ALPINE_SUMMIT, VALLEY_FOREST, AUTUMN_FOREST, TEMPERATE_MEADOW,
            TEMPERATE_WOODLAND, SAGEBRUSH_FLATS, ASPEN_FOREST, TEMPERATE_RAINFOREST, LARCH_BASIN,
            CANYON_RIM, CANYON_FLOOR, PRAIRIE, STEPPE, SAVANNA, SAND_SEA, ROCKY_DESERT,
            TROPICAL_RAINFOREST, MANGROVE_COAST, TROPICAL_ISLAND, VOLCANIC_HIGHLAND,
            BOREAL_FOREST, TUNDRA, ICE_SHEET, HEATH_CRIMSON, HEATH_EMBER, HEATH_GOLD,
            GRANITE_VALLEY_FLOOR, GRANITE_HIGHLAND);

    private static final int CACHE_SIZE = 4096;
    private static final int[][] WALL_PROBES = {{12, 0}, {-12, 0}, {0, 12}, {0, -12}};

    private final HolderGetter<Biome> biomeRegistry;
    private final Climate.Mode mode;
    private final ThreadLocal<Cache> cache = ThreadLocal.withInitial(Cache::new);

    public TrailheadBiomeSource(HolderGetter<Biome> biomeRegistry, Climate.Mode mode) {
        this.biomeRegistry = biomeRegistry;
        this.mode = mode;
    }

    private static ResourceKey<Biome> key(String name) {
        return ResourceKey.create(Registries.BIOME, TrailheadMod.id(name));
    }

    @SafeVarargs
    private static ResourceKey<Biome>[] biomes(ResourceKey<Biome>... keys) {
        return keys;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Arrays.stream(ALL_BIOMES).map(biomeRegistry::getOrThrow);
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, net.minecraft.world.level.biome.Climate.Sampler sampler) {
        long seed = TrailheadMod.worldSeed;
        Cache c = cache.get();
        c.resetFor(seed);
        long key = ((long) quartX << 32) ^ (quartZ & 0xFFFFFFFFL);
        int slot = (int) ((key * 0x9E3779B97F4A7C15L) >>> 52);
        int x = quartX << 2, z = quartZ << 2;
        if (c.surface[slot] == null || c.keys[slot] != key) {
            HeightField.Column col = TerrainContext.get(seed, mode).field.sampleWithoutRivers(x, z);
            double patch = SubalpineHeath.patch(seed, x, z);
            c.keys[slot] = key;
            c.columns[slot] = col;
            c.lowestSurface[slot] = lowestSurface(TerrainContext.get(seed, mode).field, x, z, col.height);
            c.surface[slot] = biomeRegistry.getOrThrow(pick(col, patch, SubalpineHeath.tint(seed, x, z)));
        }
        CaveType caveType = CaveType.at(seed, c.columns[slot], c.lowestSurface[slot], x, quartY << 2, z);
        if (caveType == CaveType.SURFACE) return c.surface[slot];
        return biomeRegistry.getOrThrow(cave(caveType));
    }

    private static double lowestSurface(HeightField field, int x, int z, double own) {
        double lowest = own;
        for (int[] offset : WALL_PROBES) {
            lowest = Math.min(lowest, field.sampleWithoutRivers(x + offset[0], z + offset[1]).height);
        }
        return lowest;
    }

    static ResourceKey<Biome> cave(CaveType type) {
        return switch (type) {
            case LUSH -> Biomes.LUSH_CAVES;
            case DRIPSTONE -> Biomes.DRIPSTONE_CAVES;
            case DEEP_DARK -> Biomes.DEEP_DARK;
            case DESERT -> DESERT_CAVES;
            case ICE -> ICE_CAVES;
            case SURFACE -> throw new IllegalArgumentException("surface is not a cave biome");
        };
    }

    static ResourceKey<Biome> ocean(HeightField.Column col) {
        double temperature = col.temperature;
        boolean deep = col.height < SEA_LEVEL - 30;
        if (temperature > 0.78) return Biomes.WARM_OCEAN;
        if (temperature > 0.6) return deep ? Biomes.DEEP_LUKEWARM_OCEAN : Biomes.LUKEWARM_OCEAN;
        if (temperature > 0.32) return deep ? Biomes.DEEP_OCEAN : Biomes.OCEAN;
        if (temperature > 0.14) return deep ? Biomes.DEEP_COLD_OCEAN : Biomes.COLD_OCEAN;
        return deep ? Biomes.DEEP_FROZEN_OCEAN : Biomes.FROZEN_OCEAN;
    }

    static ResourceKey<Biome> heath(SubalpineHeath.Tint tint) {
        return switch (tint) {
            case CRIMSON -> HEATH_CRIMSON;
            case EMBER -> HEATH_EMBER;
            case GOLD -> HEATH_GOLD;
        };
    }

    static ResourceKey<Biome> pick(HeightField.Column col, double patch, SubalpineHeath.Tint tint) {
        if (col.height < SEA_LEVEL - 0.5) return ocean(col);
        double elevation = col.height - SEA_LEVEL;
        double temperature = Climate.atHeight(col.temperature, col.height);
        if (col.height > SEA_LEVEL + 175 || (temperature < 0.02 && col.province != Province.ICE_SHEET)) {
            return ALPINE_SUMMIT;
        }
        boolean shore = col.coastDist < 10 && elevation < 4;
        if (col.atoll > 0.3) return TROPICAL_ISLAND;
        if (col.volcanic > 0.55 && elevation > 40) return VOLCANIC_HIGHLAND;
        double moisture = col.moisture;
        if (col.cone > 0.2) return ALPINE_SUMMIT;
        if (SubalpineHeath.covers(col, patch)) return heath(tint);
        return switch (col.province) {
            case SEA_CLIFFS -> shore ? Biomes.BEACH : COASTAL_GRASSLAND;
            case FJORDLAND -> {
                if (col.coastDist < 6 && elevation < 4) yield col.temperature < 0.3 ? Biomes.SNOWY_BEACH : Biomes.BEACH;
                yield elevation < 70 + moisture * 50 ? FJORD_FOREST : FJORD_HIGHLAND;
            }
            case DOLOMITES -> {
                if (col.massif > 0.4 || col.talus > 0.5) yield ALPINE_SCREE;
                if (patch > 0.62 || (elevation < 60 && patch > 0.48)) yield SUBALPINE_FOREST;
                yield ALPINE_MEADOW;
            }
            case ALPINE_VALLEY -> {
                if (col.massif > 0.4) yield ALPINE_SCREE;
                if (elevation < 75) yield patch > 0.55 ? AUTUMN_FOREST : VALLEY_FOREST;
                if (elevation < 175 && SubalpineHeath.zone(col) != SubalpineHeath.Zone.ALPINE) {
                    yield patch < 0.35 ? ALPINE_MEADOW : SUBALPINE_FOREST;
                }
                yield ALPINE_SCREE;
            }
            case TETON -> {
                if (col.mountain > 0.5) yield elevation < 130 ? SUBALPINE_FOREST : ALPINE_SCREE;
                yield moisture > 0.55 && patch > 0.5 ? ASPEN_FOREST : SAGEBRUSH_FLATS;
            }
            case CASCADIA -> {
                yield switch (SubalpineHeath.zone(col)) {
                    case MONTANE -> TEMPERATE_RAINFOREST;
                    case SUBALPINE -> SUBALPINE_FOREST;
                    case HEATH -> LARCH_BASIN;
                    case ALPINE -> ALPINE_SCREE;
                };
            }
            case GRANITE_VALLEY -> {
                if (col.trough > 0.5) yield GRANITE_VALLEY_FLOOR;
                yield elevation > 190 ? ALPINE_SCREE : GRANITE_HIGHLAND;
            }
            case CANYONLANDS -> elevation < 45 ? CANYON_FLOOR : CANYON_RIM;
            case TEMPERATE_HILLS -> {
                if (shore) yield Biomes.BEACH;
                if (moisture > 0.6 || patch > 0.7) yield patch > 0.6 && moisture < 0.5 ? AUTUMN_FOREST : TEMPERATE_WOODLAND;
                yield TEMPERATE_MEADOW;
            }
            case PRAIRIE -> {
                if (shore) yield Biomes.BEACH;
                yield col.mountain > 0.5 && elevation > 90 ? SUBALPINE_FOREST : PRAIRIE;
            }
            case STEPPE -> {
                if (shore) yield Biomes.BEACH;
                if (col.mountain > 0.5 && elevation > 110) yield ALPINE_SCREE;
                yield STEPPE;
            }
            case SAVANNA -> shore ? Biomes.BEACH : SAVANNA;
            case DESERT -> {
                if (shore) yield Biomes.BEACH;
                yield col.dune > 0.4 ? SAND_SEA : ROCKY_DESERT;
            }
            case JUNGLE -> {
                if (col.coastDist < 40 && elevation < 5) yield MANGROVE_COAST;
                yield TROPICAL_RAINFOREST;
            }
            case BOREAL -> {
                if (shore) yield Biomes.SNOWY_BEACH;
                if (col.mountain > 0.5 && elevation > 120) yield TUNDRA;
                yield BOREAL_FOREST;
            }
            case TUNDRA -> shore ? Biomes.SNOWY_BEACH : TUNDRA;
            case ICE_SHEET -> col.iceDepth > 4 ? ICE_SHEET : TUNDRA;
        };
    }

    private static final class Cache {
        final long[] keys = new long[CACHE_SIZE];
        final HeightField.Column[] columns = new HeightField.Column[CACHE_SIZE];
        final double[] lowestSurface = new double[CACHE_SIZE];
        @SuppressWarnings("unchecked")
        final Holder<Biome>[] surface = new Holder[CACHE_SIZE];
        long seed = Long.MIN_VALUE;

        void resetFor(long worldSeed) {
            if (seed == worldSeed) return;
            Arrays.fill(surface, null);
            Arrays.fill(columns, null);
            seed = worldSeed;
        }
    }
}
