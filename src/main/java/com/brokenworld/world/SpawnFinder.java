package com.brokenworld.world;

import com.brokenworld.entity.SilhouetteEntity.Mode;
import com.brokenworld.registry.ModEntities;
import com.brokenworld.util.Sight;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.tag.convention.v1.ConventionalBlockTags;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Picks a spot for the silhouette depending on its mode. */
public final class SpawnFinder {
    private SpawnFinder() {}

    @Nullable
    public static BlockPos find(Mode mode, ServerLevel level, ServerPlayer player, RandomSource random) {
        return switch (mode) {
            case FAR -> far(level, player, random, 34, 62);
            case RUSH -> far(level, player, random, 22, 34);
            case TREE_SIDE -> treeSide(level, player, random);
            case TREE_TOP -> treeTop(level, player, random);
            case BEHIND -> behind(level, player, random);
            case FOLLOW -> follow(level, player, random);
            case CAVE -> cave(level, player, random);
            case HOUSE -> house(level, player, random);
            case CEILING -> ceiling(level, player, random);
            case WINDOW -> window(level, player, random);
        };
    }

    // ------------------------------------------------------------------ modes

    /** On the surface, far away, somewhere the player could see it but is not looking right now. */
    @Nullable
    private static BlockPos far(ServerLevel level, ServerPlayer player, RandomSource random, double minD, double maxD) {
        float baseYaw = player.getYRot();
        for (int i = 0; i < 40; i++) {
            float yaw = baseYaw + (random.nextFloat() * 200F - 100F);
            double dist = minD + random.nextDouble() * (maxD - minD);
            int x = Mth.floor(player.getX() - Mth.sin(yaw * Mth.DEG_TO_RAD) * dist);
            int z = Mth.floor(player.getZ() + Mth.cos(yaw * Mth.DEG_TO_RAD) * dist);
            BlockPos pos = surface(level, x, z);
            if (pos == null) continue;
            Vec3 head = headOf(pos);
            if (Sight.isInViewCone(player, head, Sight.NOTICE_COS)) continue;
            if (!Sight.hasClearView(player, player.getEyePosition(), head)) continue;
            return pos;
        }
        return null;
    }

    /** Half-hidden next to a tree trunk, on the far side from the player. */
    @Nullable
    private static BlockPos treeSide(ServerLevel level, ServerPlayer player, RandomSource random) {
        for (int i = 0; i < 8; i++) {
            BlockPos trunk = Trees.findTrunkBase(level, player, random, 10, 30, Sight.NOTICE_COS, 10);
            if (trunk == null) continue;
            double dx = trunk.getX() + 0.5 - player.getX();
            double dz = trunk.getZ() + 0.5 - player.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            dx /= len;
            dz /= len;
            int side = random.nextBoolean() ? 1 : -1;
            // Behind the trunk and one block to the side: it "peeks" out.
            int[][] offsets = {{1, side}, {1, -side}, {0, side}, {0, -side}};
            for (int[] o : offsets) {
                double px = trunk.getX() + 0.5 + dx * o[0] + (-dz) * o[1];
                double pz = trunk.getZ() + 0.5 + dz * o[0] + dx * o[1];
                BlockPos pos = ground(level, Mth.floor(px), trunk.getY(), Mth.floor(pz), 3);
                if (pos != null && !pos.equals(trunk)) return pos;
            }
        }
        return null;
    }

    /** Standing on top of a tree's canopy. */
    @Nullable
    private static BlockPos treeTop(ServerLevel level, ServerPlayer player, RandomSource random) {
        for (int i = 0; i < 8; i++) {
            BlockPos base = Trees.findTrunkBase(level, player, random, 12, 42, Sight.NOTICE_COS, 10);
            if (base == null) continue;
            BlockPos p = base;
            while (level.getBlockState(p.above()).is(BlockTags.LOGS)) p = p.above();
            // Climb through the canopy.
            int guard = 0;
            while (guard++ < 10 && (level.getBlockState(p.above()).is(BlockTags.LEAVES)
                    || level.getBlockState(p.above()).is(BlockTags.LOGS))) {
                p = p.above();
            }
            BlockPos stand = p.above();
            if (!level.getBlockState(p).is(BlockTags.LEAVES)) continue;
            if (isStandable(level, stand, true)) return stand;
        }
        return null;
    }

    /** A few blocks directly behind the player. */
    @Nullable
    private static BlockPos behind(ServerLevel level, ServerPlayer player, RandomSource random) {
        Vec3 look = player.getViewVector(1.0F);
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1.0E-4) flat = new Vec3(0, 0, 1);
        flat = flat.normalize();
        for (int i = 0; i < 20; i++) {
            double dist = 4.5 + random.nextDouble() * 4.0;
            double sideways = (random.nextDouble() - 0.5) * 3.0;
            double x = player.getX() - flat.x * dist + (-flat.z) * sideways;
            double z = player.getZ() - flat.z * dist + flat.x * sideways;
            BlockPos pos = ground(level, Mth.floor(x), player.getBlockY(), Mth.floor(z), 4);
            if (pos == null) continue;
            if (Sight.isInViewCone(player, headOf(pos), Sight.ON_SCREEN_COS)) continue;
            return pos;
        }
        return null;
    }

    /** Some distance behind the player, from where it can start following. */
    @Nullable
    private static BlockPos follow(ServerLevel level, ServerPlayer player, RandomSource random) {
        float baseYaw = player.getYRot() + 180F;
        for (int i = 0; i < 30; i++) {
            float yaw = baseYaw + (random.nextFloat() * 110F - 55F);
            double dist = 20 + random.nextDouble() * 12;
            int x = Mth.floor(player.getX() - Mth.sin(yaw * Mth.DEG_TO_RAD) * dist);
            int z = Mth.floor(player.getZ() + Mth.cos(yaw * Mth.DEG_TO_RAD) * dist);
            BlockPos pos = ground(level, x, player.getBlockY(), z, 8);
            if (pos == null) continue;
            if (Sight.canSee(player, headOf(pos))) continue;
            return pos;
        }
        return null;
    }

    /** In a dark spot of the cave the player is in, where the player can see it if they turn. */
    @Nullable
    private static BlockPos cave(ServerLevel level, ServerPlayer player, RandomSource random) {
        BlockPos fallback = null;
        for (int i = 0; i < 80; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = 12 + random.nextDouble() * 18;
            int x = Mth.floor(player.getX() + Math.cos(angle) * dist);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * dist);
            int y = player.getBlockY() + random.nextInt(9) - 4;
            BlockPos pos = ground(level, x, y, z, 3);
            if (pos == null || level.canSeeSky(pos)) continue;
            if (level.getBrightness(LightLayer.BLOCK, pos) > 3) continue;
            Vec3 head = headOf(pos);
            if (Sight.isInViewCone(player, head, Sight.NOTICE_COS)) continue;
            if (Sight.hasClearView(player, player.getEyePosition(), head)) return pos;
            if (fallback == null) fallback = pos;
        }
        return fallback;
    }

    /** Outside, near the player's bed, at night. Ideally visible through a window. */
    @Nullable
    private static BlockPos house(ServerLevel level, ServerPlayer player, RandomSource random) {
        BlockPos bed = player.getRespawnPosition();
        if (bed == null || player.getRespawnDimension() != Level.OVERWORLD) return null;
        if (bed.distSqr(player.blockPosition()) > 28 * 28) return null;
        BlockPos fallback = null;
        for (int i = 0; i < 60; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = 6 + random.nextDouble() * 8;
            int x = Mth.floor(bed.getX() + 0.5 + Math.cos(angle) * dist);
            int z = Mth.floor(bed.getZ() + 0.5 + Math.sin(angle) * dist);
            BlockPos pos = ground(level, x, bed.getY(), z, 5);
            if (pos == null || !level.canSeeSky(pos)) continue;
            if (pos.distSqr(player.blockPosition()) < 5 * 5) continue;
            Vec3 head = headOf(pos);
            if (Sight.isInViewCone(player, head, Sight.NOTICE_COS)) continue;
            if (Sight.hasClearView(player, player.getEyePosition(), head)) return pos;
            if (fallback == null) fallback = pos;
        }
        return fallback;
    }

    /**
     * A ceiling to hang from (cave or house): returns the ceiling block itself; the silhouette hangs below it,
     * feet up. Needs at least 3 blocks of air under the ceiling and floor below that.
     */
    @Nullable
    private static BlockPos ceiling(ServerLevel level, ServerPlayer player, RandomSource random) {
        BlockPos fallback = null;
        for (int i = 0; i < 80; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = 6 + random.nextDouble() * 14;
            int x = Mth.floor(player.getX() + Math.cos(angle) * dist);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * dist);
            BlockPos floor = ground(level, x, player.getBlockY(), z, 4);
            if (floor == null) continue;
            BlockPos top = null;
            for (int up = 3; up <= 7; up++) {
                BlockPos p = floor.above(up);
                if (!level.getBlockState(p).isAir()) {
                    if (level.getBlockState(p).isFaceSturdy(level, p, Direction.DOWN)) top = p;
                    break;
                }
            }
            if (top == null) continue;
            Vec3 head = Vec3.atCenterOf(top.below(3));
            if (Sight.isInViewCone(player, head, Sight.NOTICE_COS)) continue;
            if (Sight.hasClearView(player, player.getEyePosition(), head)) return top;
            if (fallback == null) fallback = top;
        }
        return fallback;
    }

    // ------------------------------------------------------------------ helpers

    /**
     * The player is indoors: the air just outside one of the room's windows (next to the glass, at its height), where
     * the player could see it but is not looking right now.
     */
    @Nullable
    private static BlockPos window(ServerLevel level, ServerPlayer player, RandomSource random) {
        BlockPos p = player.blockPosition();
        // indoors: there is a roof over their head (not by the light, which lags behind freshly built houses)
        if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, p.getX(), p.getZ()) <= p.getY() + 1) return null;
        List<BlockPos> spots = new ArrayList<>();
        for (BlockPos g : BlockPos.betweenClosed(p.offset(-10, 0, -10), p.offset(10, 2, 10))) {
            if (!isGlass(level.getBlockState(g))) continue;
            Vec3 centre = Vec3.atCenterOf(g);
            if (centre.distanceToSqr(player.getEyePosition()) < 9) continue;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                Vec3 toPlayer = player.position().subtract(centre);
                if (toPlayer.x * d.getStepX() + toPlayer.z * d.getStepZ() > -1.0) continue; // must be outside, the player inside
                BlockPos out = g.relative(d);
                if (!isFree(level, out) || !isFree(level, out.below()) || !isFree(level, out.above())) continue;
                if (Sight.isInViewCone(player, centre, Sight.ON_SCREEN_COS)) continue; // it comes when you are not looking
                if (!Sight.hasClearView(player, player.getEyePosition(), Vec3.atCenterOf(out))) continue;
                spots.add(out.immutable());
            }
        }
        return spots.isEmpty() ? null : spots.get(random.nextInt(spots.size()));
    }

    /** Which way the window glass is from a spot found by {@link #window} (null if none is next to it). */
    @Nullable
    public static Direction windowFacing(ServerLevel level, BlockPos out) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (isGlass(level.getBlockState(out.relative(d)))) return d;
        }
        return null;
    }

    public static boolean isGlass(BlockState state) {
        return state.is(ConventionalBlockTags.GLASS_BLOCKS) || state.is(ConventionalBlockTags.GLASS_PANES);
    }

    private static boolean isFree(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    public static Vec3 headOf(BlockPos feet) {
        return new Vec3(feet.getX() + 0.5, feet.getY() + 2.3, feet.getZ() + 0.5);
    }

    @Nullable
    private static BlockPos surface(ServerLevel level, int x, int z) {
        if (!level.hasChunkAt(new BlockPos(x, 0, z))) return null;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos pos = new BlockPos(x, y, z);
        return isStandable(level, pos, false) ? pos : null;
    }

    /** Scans up/down from y for a spot to stand on. */
    @Nullable
    private static BlockPos ground(ServerLevel level, int x, int y, int z, int range) {
        if (!level.hasChunkAt(new BlockPos(x, y, z))) return null;
        for (int d = 0; d <= range; d++) {
            BlockPos down = new BlockPos(x, y - d, z);
            if (isStandable(level, down, false)) return down;
            if (d > 0) {
                BlockPos up = new BlockPos(x, y + d, z);
                if (isStandable(level, up, false)) return up;
            }
        }
        return null;
    }

    public static boolean isStandable(ServerLevel level, BlockPos pos, boolean allowLeaves) {
        if (pos.getY() <= level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight() - 3) return false;
        BlockPos below = pos.below();
        BlockState floor = level.getBlockState(below);
        boolean solid = floor.isFaceSturdy(level, below, Direction.UP)
                || (allowLeaves && floor.is(BlockTags.LEAVES));
        if (!solid) return false;
        if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()) return false;
        return level.noCollision(ModEntities.SILHOUETTE.get().getAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
    }
}
