package com.reveny.trailhead.worldgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.IceSpikeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class SnowIceSpikeFeature extends IceSpikeFeature {
    public SnowIceSpikeFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos.MutableBlockPos pos = context.origin().mutable();
        int minY = level.getMinY() + 2;
        while (pos.getY() > minY && level.isEmptyBlock(pos)) {
            pos.move(0, -1, 0);
        }
        if (level.getBlockState(pos).is(Blocks.SNOW) && level.getBlockState(pos.below()).is(Blocks.SNOW_BLOCK)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
        return super.place(context);
    }
}
