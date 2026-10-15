package com.reveny.trailhead.debug;

import com.reveny.trailhead.worldgen.terrain.Province;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumMap;
import java.util.Map;

final class PreviewColors {
    static final int UNKNOWN = 0xB040B0;

    private static final Map<Province, Integer> PROVINCES = new EnumMap<>(Province.class);
    private static final Map<Block, Integer> BLOCKS = Map.ofEntries(
            Map.entry(Blocks.GRASS_BLOCK, 0x5E9A3E),
            Map.entry(Blocks.MOSS_BLOCK, 0x58782E),
            Map.entry(Blocks.DIRT, 0x7A583C),
            Map.entry(Blocks.COARSE_DIRT, 0x70563A),
            Map.entry(Blocks.SNOW_BLOCK, 0xF0F5FA),
            Map.entry(Blocks.SAND, 0xD4C48E),
            Map.entry(Blocks.GRAVEL, 0x807C7A),
            Map.entry(Blocks.WATER, 0x2E64AE),
            Map.entry(Blocks.TUFF, 0x6C6E66),
            Map.entry(Blocks.ANDESITE, 0x888889),
            Map.entry(Blocks.DEEPSLATE, 0x4E4E54),
            Map.entry(Blocks.CALCITE, 0xDCDCD6),
            Map.entry(Blocks.DRIPSTONE_BLOCK, 0x866B5C),
            Map.entry(Blocks.STONE, 0x7E7E80),
            Map.entry(Blocks.BEDROCK, 0x333333),
            Map.entry(Blocks.DIORITE, 0xBDBDBD),
            Map.entry(Blocks.GRANITE, 0x9A6B5A),
            Map.entry(Blocks.PODZOL, 0x5E4423),
            Map.entry(Blocks.BASALT, 0x505056),
            Map.entry(Blocks.SMOOTH_BASALT, 0x48484E),
            Map.entry(Blocks.RED_SAND, 0xBE6621),
            Map.entry(Blocks.TERRACOTTA, 0x985E43),
            Map.entry(Blocks.RED_TERRACOTTA, 0x8F3D2E),
            Map.entry(Blocks.ORANGE_TERRACOTTA, 0xA25426),
            Map.entry(Blocks.WHITE_TERRACOTTA, 0xD1B2A1),
            Map.entry(Blocks.BROWN_TERRACOTTA, 0x4D3324),
            Map.entry(Blocks.LIGHT_GRAY_TERRACOTTA, 0x876B62),
            Map.entry(Blocks.CLAY, 0xA0A6B3),
            Map.entry(Blocks.RED_SANDSTONE, 0xB5621F),
            Map.entry(Blocks.SMOOTH_RED_SANDSTONE, 0xB5621F),
            Map.entry(Blocks.SANDSTONE, 0xD8CB9B),
            Map.entry(Blocks.SMOOTH_SANDSTONE, 0xD8CB9B),
            Map.entry(Blocks.CUT_SANDSTONE, 0xD8CB9B));

    static {
        int[] colors = {0x6FA552, 0x4F7FA8, 0x2F5F78, 0xD8D2C0, 0x8FB070, 0xB89070, 0x1F5F3A, 0xC0502A,
                0xC8D66A, 0xB8A860, 0xD2B04A, 0xF0D890, 0x0F7A2A, 0x3C6E4A, 0x9AA890, 0xF4F8FC};
        Province[] provinces = Province.values();
        for (int i = 0; i < provinces.length; i++) PROVINCES.put(provinces[i], colors[i % colors.length]);
    }

    private PreviewColors() {}

    static int of(BlockState state) {
        return BLOCKS.getOrDefault(state.getBlock(), UNKNOWN);
    }

    static int of(Province province) {
        return PROVINCES.get(province);
    }

    static int scale(int rgb, double factor) {
        int r = (int) Math.min(255, ((rgb >> 16) & 255) * factor);
        int g = (int) Math.min(255, ((rgb >> 8) & 255) * factor);
        int b = (int) Math.min(255, (rgb & 255) * factor);
        return (r << 16) | (g << 8) | b;
    }

    static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int blue = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | blue;
    }
}
