package com.brokenworld.world;

import com.brokenworld.util.Sight;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;

/** Finding natural tree trunks around the player (for the silhouette to hide behind / stand on). */
public final class Trees {
    private Trees() {}

    /**
     * Finds the bottom log of a tree trunk standing on dirt/grass, somewhere in the ring
     * [minDist, maxDist] around the player.
     *
     * @param maxViewCos reject trunks whose base is inside this view cone (use 2 to allow any).
     */
    @Nullable
    public static BlockPos findTrunkBase(ServerLevel level, ServerPlayer player, RandomSource random,
                                         double minDist, double maxDist, double maxViewCos, int attempts) {
        for (int i = 0; i < attempts; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = minDist + random.nextDouble() * (maxDist - minDist);
            int x = Mth.floor(player.getX() + Math.cos(angle) * dist);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * dist);
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) continue;

            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, top, z);
            BlockPos found = null;
            for (int dy = 0; dy < 36 && pos.getY() > level.getMinBuildHeight(); dy++) {
                if (level.getBlockState(pos).is(BlockTags.LOGS)) {
                    found = pos.immutable();
                    break;
                }
                pos.move(0, -1, 0);
            }
            if (found == null) continue;

            BlockPos base = found;
            while (level.getBlockState(base.below()).is(BlockTags.LOGS)) {
                base = base.below();
            }
            if (!level.getBlockState(base.below()).is(BlockTags.DIRT)) continue;
            if (Sight.isInViewCone(player, base.above(), maxViewCos)) continue;
            return base;
        }
        return null;
    }
}
