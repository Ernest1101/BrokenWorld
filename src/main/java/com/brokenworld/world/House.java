package com.brokenworld.world;

import com.brokenworld.BrokenWorld;
import com.brokenworld.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * "Bigger on the inside": after a player has been far away from home, walking back into the house puts them into a
 * copy of it that is three times wider and twice as tall (built in the void of the tunnel dimension) - the same walls,
 * windows and floor, with the furniture standing lost in the middle of huge rooms. From outside it is the same house.
 * <ul>
 *   <li>Leaving through a door puts the player outside that door of the real house.</li>
 *   <li>Breaking the walls from inside does not work: the player suddenly stands in the small real house again.</li>
 * </ul>
 * The house is the enclosed space around the player's bed (found by flood fill; doors count as walls).
 */
public final class House {
    private static final int ARM_DISTANCE = 48;
    private static final int SCALE_XZ = 3;
    private static final int SCALE_Y = 2;
    private static final int MAX_INTERIOR = 2500;
    private static final int MAX_RADIUS = 24;
    private static final int POCKET_X = 30000;
    private static final int POCKET_Y = 80;
    private static final int POCKET_SPACING = 512;

    private static final class Home {
        boolean armed;
        boolean inside;
        BlockPos bed;
        Set<BlockPos> interior;
        BlockPos min;
        BlockPos max;
        BlockPos pocket;           // pocket origin (maps to `min`)
        @Nullable BlockPos builtMin;
        @Nullable BlockPos builtMax;
        int index;
    }

    private static final Map<UUID, Home> HOMES = new HashMap<>();
    private static int nextIndex;

    private House() {}

    public static void reset() {
        HOMES.clear();
        nextIndex = 0;
    }

    public static boolean isInside(ServerPlayer player) {
        Home h = HOMES.get(player.getUUID());
        return h != null && h.inside;
    }

    /** Called every tick; does real work every 5 ticks. */
    public static void tick(MinecraftServer server, int stage) {
        if (server.getTickCount() % 5 != 0 || !Config.BIGGER_HOUSE.get()) return;
        ServerLevel overworld = server.overworld();
        ServerLevel pocketLevel = server.getLevel(Finale.TUNNEL);
        if (pocketLevel == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator()) continue;
            Home h = HOMES.get(player.getUUID());
            if (h != null && h.inside) {
                if (player.level() == pocketLevel) tickInside(player, h, overworld, pocketLevel);
                else h.inside = false; // died, used a portal, ...
                continue;
            }
            if (stage < 1 || Finale.active() || player.level() != overworld) continue;
            BlockPos bed = player.getRespawnPosition();
            if (bed == null || player.getRespawnDimension() != Level.OVERWORLD) continue;
            if (h == null) {
                h = new Home();
                h.index = nextIndex++;
                HOMES.put(player.getUUID(), h);
            }
            double dist = Math.sqrt(player.distanceToSqr(Vec3.atCenterOf(bed)));
            if (dist > ARM_DISTANCE) {
                h.armed = true;
            } else if (h.armed && dist < MAX_RADIUS) {
                if (h.interior == null || !bed.equals(h.bed) || server.getTickCount() % 100 == 0) {
                    if (!scan(overworld, bed, h)) continue;
                }
                if (h.interior.contains(player.blockPosition())) enter(player, h, overworld, pocketLevel);
            }
        }
    }

    // ------------------------------------------------------------------ finding the house

    private static boolean isWall(BlockState s) {
        return s.getBlock() instanceof DoorBlock || s.getBlock() instanceof TrapDoorBlock
                || s.getBlock() instanceof FenceGateBlock;
    }

    private static boolean isOpen(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return !isWall(s) && s.getCollisionShape(level, pos).isEmpty() && s.getFluidState().isEmpty();
    }

    /** Flood-fills the air around the bed. Fails if it is not enclosed (too big, or open to the sky). */
    private static boolean scan(ServerLevel level, BlockPos bed, Home h) {
        h.interior = null;
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        BlockState bedState = level.getBlockState(bed);
        List<BlockPos> starts = new ArrayList<>(List.of(bed.above()));
        if (bedState.getBlock() instanceof BedBlock) {
            starts.add(bed.relative(BedBlock.getConnectedDirection(bedState)).above());
        }
        for (BlockPos s : starts) {
            if (isOpen(level, s) && seen.add(s)) queue.add(s);
        }
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            if (seen.size() > MAX_INTERIOR || p.distManhattan(bed) > MAX_RADIUS * 2 || level.canSeeSky(p)) return false;
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (!seen.contains(n) && isOpen(level, n)) {
                    seen.add(n);
                    queue.add(n);
                }
            }
        }
        if (seen.size() < 4) return false;
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (BlockPos p : seen) {
            x0 = Math.min(x0, p.getX()); y0 = Math.min(y0, p.getY()); z0 = Math.min(z0, p.getZ());
            x1 = Math.max(x1, p.getX()); y1 = Math.max(y1, p.getY()); z1 = Math.max(z1, p.getZ());
        }
        h.interior = seen;
        h.bed = bed;
        h.min = new BlockPos(x0 - 1, y0 - 1, z0 - 1);
        h.max = new BlockPos(x1 + 1, y1 + 1, z1 + 1);
        return true;
    }

    // ------------------------------------------------------------------ the pocket

    private static void enter(ServerPlayer player, Home h, ServerLevel overworld, ServerLevel pocketLevel) {
        h.pocket = new BlockPos(POCKET_X + h.index * POCKET_SPACING, POCKET_Y, POCKET_X);
        build(h, overworld, pocketLevel);
        Vec3 target = toPocket(h, player.position());
        player.teleportTo(pocketLevel, target.x, target.y, target.z, player.getYRot(), player.getXRot());
        h.inside = true;
        h.armed = false;
        BrokenWorld.LOGGER.info("[BrokenWorld] {}'s house is bigger on the inside", player.getName().getString());
    }

    private static Vec3 toPocket(Home h, Vec3 real) {
        return new Vec3(h.pocket.getX() + (real.x - h.min.getX()) * SCALE_XZ,
                h.pocket.getY() + (real.y - h.min.getY()) * SCALE_Y,
                h.pocket.getZ() + (real.z - h.min.getZ()) * SCALE_XZ);
    }

    private static BlockPos toReal(Home h, Vec3 pocketPos) {
        return BlockPos.containing(h.min.getX() + (pocketPos.x - h.pocket.getX()) / SCALE_XZ,
                h.min.getY() + (pocketPos.y - h.pocket.getY()) / SCALE_Y,
                h.min.getZ() + (pocketPos.z - h.pocket.getZ()) / SCALE_XZ);
    }

    /** Copies the house into the pocket, stretched: every block becomes a 3x2x3 block of the same kind. */
    private static void build(Home h, ServerLevel overworld, ServerLevel pocket) {
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        BlockState air = Blocks.AIR.defaultBlockState();
        if (h.builtMin != null) {
            for (BlockPos p : BlockPos.betweenClosed(h.builtMin, h.builtMax)) pocket.setBlock(p, air, flags);
        }
        BlockPos size = h.max.subtract(h.min);
        for (int cx = 0; cx <= (size.getX() + 1) * SCALE_XZ >> 4; cx++) {
            for (int cz = 0; cz <= (size.getZ() + 1) * SCALE_XZ >> 4; cz++) {
                pocket.getChunk((h.pocket.getX() >> 4) + cx, (h.pocket.getZ() >> 4) + cz);
            }
        }
        for (BlockPos real : BlockPos.betweenClosed(h.min, h.max)) {
            BlockState s = overworld.getBlockState(real);
            BlockPos base = new BlockPos(h.pocket.getX() + (real.getX() - h.min.getX()) * SCALE_XZ,
                    h.pocket.getY() + (real.getY() - h.min.getY()) * SCALE_Y,
                    h.pocket.getZ() + (real.getZ() - h.min.getZ()) * SCALE_XZ);
            boolean inside = h.interior.contains(real);
            if (s.getBlock() instanceof DoorBlock) {
                if (s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) placeDoor(pocket, base, s, wallNextTo(overworld, real), flags);
                continue;
            }
            if (s.getBlock() instanceof BedBlock) {
                fillRegion(pocket, base, SCALE_Y, air, flags);
                if (s.getValue(BedBlock.PART) == BedPart.FOOT) placeBed(pocket, base, s, flags); // both halves in here
                continue;
            }
            if (inside || s.isAir()) {
                fillRegion(pocket, base, SCALE_Y, air, flags);
                if (!s.isAir()) pocket.setBlock(anchor(base, s), s, flags); // furniture, lost in the middle
            } else if (s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) || isPaneLike(s)) {
                fillRegion(pocket, base, SCALE_Y, s, flags); // walls, floor, ceiling, windows
            } else {
                fillRegion(pocket, base, SCALE_Y, air, flags);
                pocket.setBlock(anchor(base, s), s, flags);
            }
        }
        h.builtMin = h.pocket;
        h.builtMax = h.pocket.offset((size.getX() + 1) * SCALE_XZ - 1, (size.getY() + 1) * SCALE_Y - 1,
                (size.getZ() + 1) * SCALE_XZ - 1);
    }

    private static boolean isPaneLike(BlockState s) {
        return s.hasProperty(BlockStateProperties.NORTH) && s.hasProperty(BlockStateProperties.EAST)
                && !(s.getBlock() instanceof FenceGateBlock); // panes, iron bars, fences, walls
    }

    private static void fillRegion(ServerLevel level, BlockPos base, int height, BlockState s, int flags) {
        for (int x = 0; x < SCALE_XZ; x++)
            for (int y = 0; y < height; y++)
                for (int z = 0; z < SCALE_XZ; z++)
                    level.setBlock(base.offset(x, y, z), s, flags);
    }

    /** Where a single (unscaled) block goes inside its stretched cell. */
    private static BlockPos anchor(BlockPos base, BlockState s) {
        int mid = SCALE_XZ / 2;
        if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && s.is(net.minecraft.tags.BlockTags.WALL_SIGNS)
                || s.is(Blocks.WALL_TORCH) || s.is(Blocks.SOUL_WALL_TORCH) || s.is(Blocks.LADDER)) {
            // hangs on a wall: put it against the wall it hangs on
            Direction facing = s.getValue(BlockStateProperties.HORIZONTAL_FACING);
            return base.offset(mid - facing.getStepX() * mid, 1, mid - facing.getStepZ() * mid);
        }
        if (s.hasProperty(BlockStateProperties.HANGING) && s.getValue(BlockStateProperties.HANGING)) {
            return base.offset(mid, SCALE_Y - 1, mid);
        }
        return base.offset(mid, 0, mid);
    }

    private static BlockState wallNextTo(ServerLevel level, BlockPos door) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockState s = level.getBlockState(door.relative(d));
            if (s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) return s;
        }
        return Blocks.OAK_PLANKS.defaultBlockState();
    }

    /** A normal-size door in the middle of a wall that grew around it. */
    private static void placeDoor(ServerLevel level, BlockPos base, BlockState lower, BlockState wall, int flags) {
        fillRegion(level, base, SCALE_Y * 2, wall, flags);
        Direction facing = lower.getValue(DoorBlock.FACING);
        // opening straight through the thickened wall
        for (int i = 0; i < SCALE_XZ; i++) {
            BlockPos col = facing.getAxis() == Direction.Axis.X
                    ? base.offset(i, 0, SCALE_XZ / 2) : base.offset(SCALE_XZ / 2, 0, i);
            level.setBlock(col, Blocks.AIR.defaultBlockState(), flags);
            level.setBlock(col.above(), Blocks.AIR.defaultBlockState(), flags);
        }
        BlockPos p = base.offset(SCALE_XZ / 2, 0, SCALE_XZ / 2);
        BlockState closed = lower.setValue(DoorBlock.OPEN, false);
        level.setBlock(p, closed.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), flags);
        level.setBlock(p.above(), closed.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), flags);
    }

    private static void placeBed(ServerLevel level, BlockPos base, BlockState foot, int flags) {
        Direction facing = foot.getValue(BedBlock.FACING);
        BlockPos p = base.offset(SCALE_XZ / 2, 0, SCALE_XZ / 2);
        level.setBlock(p, foot, flags);
        level.setBlock(p.relative(facing), foot.setValue(BedBlock.PART, BedPart.HEAD), flags);
    }

    // ------------------------------------------------------------------ leaving

    private static void tickInside(ServerPlayer player, Home h, ServerLevel overworld, ServerLevel pocket) {
        BlockPos real = toReal(h, player.position());
        BlockState atReal = overworld.getBlockState(real);
        Vec3 p = player.position();
        boolean outOfPocket = p.x < h.builtMin.getX() - 1 || p.x > h.builtMax.getX() + 2
                || p.z < h.builtMin.getZ() - 1 || p.z > h.builtMax.getZ() + 2 || p.y < h.builtMin.getY() - 4;
        if (atReal.getBlock() instanceof DoorBlock || outOfPocket) {
            // Walked out through a door (or somehow out of the pocket): back outside that door.
            BlockPos door = atReal.getBlock() instanceof DoorBlock ? real : nearestDoor(overworld, h, real);
            Vec3 out;
            if (door != null) {
                BlockState d = overworld.getBlockState(door);
                Direction facing = d.getValue(DoorBlock.FACING);
                BlockPos lower = d.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? door.below() : door;
                BlockPos outside = h.interior.contains(lower.relative(facing)) ? lower.relative(facing.getOpposite()) : lower.relative(facing);
                out = Vec3.atBottomCenterOf(outside);
            } else {
                out = Vec3.atBottomCenterOf(h.bed.above());
            }
            player.teleportTo(overworld, out.x, out.y, out.z, player.getYRot(), player.getXRot());
            h.inside = false;
        }
    }

    @Nullable
    private static BlockPos nearestDoor(ServerLevel level, Home h, BlockPos near) {
        BlockPos best = null;
        for (BlockPos p : BlockPos.betweenClosed(h.min, h.max)) {
            if (level.getBlockState(p).getBlock() instanceof DoorBlock
                    && (best == null || p.distSqr(near) < best.distSqr(near))) best = p.immutable();
        }
        return best;
    }

    /**
     * Trying to break out of the big house: the block stays, and the player finds themselves back in the small house.
     * @return true if the break must be cancelled.
     */
    public static boolean onBreak(ServerPlayer player, BlockPos pos) {
        Home h = HOMES.get(player.getUUID());
        if (h == null || !h.inside || h.builtMin == null) return false;
        if (pos.getX() < h.builtMin.getX() || pos.getX() > h.builtMax.getX()
                || pos.getZ() < h.builtMin.getZ() || pos.getZ() > h.builtMax.getZ()) return false;
        ServerLevel overworld = player.server.overworld();
        Vec3 back = Vec3.atBottomCenterOf(h.bed.above());
        player.teleportTo(overworld, back.x, back.y, back.z, player.getYRot(), player.getXRot());
        overworld.playSound(null, back.x, back.y, back.z, SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.BLOCKS, 0.6F, 0.5F);
        h.inside = false;
        return true;
    }

    public static void onRespawn(ServerPlayer player) {
        Home h = HOMES.get(player.getUUID());
        if (h != null) h.inside = false;
    }
}
