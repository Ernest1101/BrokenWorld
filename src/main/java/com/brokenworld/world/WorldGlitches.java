package com.brokenworld.world;

import com.brokenworld.util.Sight;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * The world itself slowly coming apart, somewhere the player is not looking:
 * <ul>
 *   <li>HOLE - a square shaft straight down to the bottom of the world, as if a piece were missing.</li>
 *   <li>FLOATING - a few blocks of the ground copied into the air, hanging there.</li>
 * </ul>
 * Only natural ground is touched (grass, dirt, stone, sand...), never near the player's bed.
 */
public final class WorldGlitches {
    public enum Type { HOLE, FLOATING }

    private WorldGlitches() {}

    public static boolean run(Type type, ServerLevel level, ServerPlayer player, RandomSource random, int stage) {
        BlockPos ground = findGround(level, player, random);
        if (ground == null) return false;
        return switch (type) {
            case HOLE -> hole(level, ground, stage >= 3 ? 3 + random.nextInt(2) : 2);
            case FLOATING -> floating(level, ground, random);
        };
    }

    private static boolean isNatural(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.SAND)
                || s.is(Blocks.GRAVEL) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.SNOW) || s.is(BlockTags.TERRACOTTA)
                || s.is(Blocks.DIRT_PATH) || s.is(BlockTags.STONE_ORE_REPLACEABLES) || s.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)
                || s.is(BlockTags.COAL_ORES) || s.is(BlockTags.IRON_ORES) || s.is(BlockTags.COPPER_ORES)
                || s.is(BlockTags.GOLD_ORES) || s.is(BlockTags.REDSTONE_ORES) || s.is(BlockTags.LAPIS_ORES)
                || s.is(BlockTags.DIAMOND_ORES) || s.is(BlockTags.EMERALD_ORES) || s.is(Blocks.CLAY) || s.is(Blocks.TUFF)
                || s.is(Blocks.CALCITE) || s.is(Blocks.DRIPSTONE_BLOCK) || s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.SMOOTH_BASALT);
    }

    /** A natural ground block 24-56 blocks away that the player is not looking at and that is far from home. */
    @Nullable
    private static BlockPos findGround(ServerLevel level, ServerPlayer player, RandomSource random) {
        BlockPos bed = player.getRespawnPosition();
        for (int i = 0; i < 30; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = 24 + random.nextDouble() * 32;
            int x = Mth.floor(player.getX() + Math.cos(angle) * dist);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * dist);
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) continue;
            BlockPos top = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z)).below();
            if (!isNatural(level.getBlockState(top))) continue;
            if (bed != null && bed.distSqr(top) < 32 * 32) continue;
            if (Sight.isInViewCone(player, Vec3.atCenterOf(top), Sight.ON_SCREEN_COS)) continue;
            return top;
        }
        return null;
    }

    /** A size x size shaft down to the bottom of the world. Gives up if it would cut into anything built. */
    public static boolean hole(ServerLevel level, BlockPos top, int size) {
        int bottom = level.getMinBuildHeight(); // down to the bedrock, which stays
        for (int y = top.getY(); y > bottom; y--) {
            for (int dx = 0; dx < size; dx++)
                for (int dz = 0; dz < size; dz++) {
                    BlockState s = level.getBlockState(new BlockPos(top.getX() + dx, y, top.getZ() + dz));
                    if (!s.isAir() && s.getFluidState().isEmpty() && !isNatural(s) && !s.is(Blocks.BEDROCK)) return false;
                }
        }
        for (int y = top.getY() + 1; y > bottom; y--) {
            for (int dx = 0; dx < size; dx++)
                for (int dz = 0; dz < size; dz++) {
                    BlockPos p = new BlockPos(top.getX() + dx, y, top.getZ() + dz);
                    BlockState s = level.getBlockState(p);
                    if (!s.isAir() && !s.is(Blocks.BEDROCK)) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
        }
        return true;
    }

    /** A handful of ground blocks hanging in the air above, a little apart, like torn-off pieces. */
    private static boolean floating(ServerLevel level, BlockPos top, RandomSource random) {
        int count = 3 + random.nextInt(6);
        int height = 6 + random.nextInt(9);
        for (int i = 0; i < count; i++) {
            BlockPos from = top.offset(random.nextInt(5) - 2, -random.nextInt(2), random.nextInt(5) - 2);
            BlockState s = level.getBlockState(from);
            if (!isNatural(s)) s = level.getBlockState(top);
            if (s.getBlock() instanceof FallingBlock) s = Blocks.DIRT.defaultBlockState(); // sand would just fall
            BlockPos to = top.offset(random.nextInt(5) - 2, height + random.nextInt(3), random.nextInt(5) - 2);
            if (level.getBlockState(to).isAir()) level.setBlock(to, s, Block.UPDATE_CLIENTS);
        }
        return true;
    }
}
