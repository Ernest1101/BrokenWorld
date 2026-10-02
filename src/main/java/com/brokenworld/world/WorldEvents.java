package com.brokenworld.world;

import com.brokenworld.util.Sight;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Small creepy things that happen around the player. */
public final class WorldEvents {
    public enum Type { FOOTSTEPS, TORCH_OUT, DOOR, MINING, CAVE_SOUND }

    private WorldEvents() {}

    public static boolean run(Type type, ServerLevel level, ServerPlayer player, RandomSource random) {
        return switch (type) {
            case FOOTSTEPS -> footsteps(level, player, random);
            case TORCH_OUT -> torchOut(level, player, random);
            case DOOR -> door(level, player, random);
            case MINING -> mining(level, player, random);
            case CAVE_SOUND -> caveSound(level, player, random);
        };
    }

    /** A few footsteps right behind the player. */
    private static boolean footsteps(ServerLevel level, ServerPlayer player, RandomSource random) {
        Vec3 look = player.getViewVector(1.0F);
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
        int steps = 3 + random.nextInt(4);
        int delay = 0;
        for (int i = 0; i < steps; i++) {
            double dist = 6.0 - i * 0.8; // getting closer...
            double x = player.getX() - flat.x * dist;
            double z = player.getZ() - flat.z * dist;
            BlockPos under = BlockPos.containing(x, player.getY() - 0.5, z);
            SoundEvent sound = level.getBlockState(under).getSoundType().getStepSound();
            delay += 7 + random.nextInt(3);
            Scheduler.schedule(delay, () -> level.playSound(null, x, player.getY(), z, sound,
                    SoundSource.HOSTILE, 0.5F, 0.85F));
        }
        return true;
    }

    /** A torch the player is not looking at goes out. */
    private static boolean torchOut(ServerLevel level, ServerPlayer player, RandomSource random) {
        List<BlockPos> torches = new ArrayList<>();
        BlockPos center = player.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-12, -5, -12), center.offset(12, 5, 12))) {
            BlockState s = level.getBlockState(p);
            if (s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH) || s.is(Blocks.SOUL_TORCH) || s.is(Blocks.SOUL_WALL_TORCH)) {
                if (!Sight.canSee(player, Vec3.atCenterOf(p))) torches.add(p.immutable());
            }
        }
        if (torches.isEmpty()) return false;
        BlockPos p = torches.get(random.nextInt(torches.size()));
        level.removeBlock(p, false);
        level.playSound(null, p, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4F, 0.6F);
        return true;
    }

    /** A wooden door nearby opens (or closes) on its own. */
    private static boolean door(ServerLevel level, ServerPlayer player, RandomSource random) {
        List<BlockPos> doors = new ArrayList<>();
        BlockPos center = player.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-10, -4, -10), center.offset(10, 4, 10))) {
            BlockState s = level.getBlockState(p);
            if (s.is(BlockTags.WOODEN_DOORS) && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                    && !Sight.canSee(player, Vec3.atCenterOf(p.above()))) {
                doors.add(p.immutable());
            }
        }
        if (doors.isEmpty()) return false;
        BlockPos p = doors.get(random.nextInt(doors.size()));
        BlockState s = level.getBlockState(p);
        if (s.getBlock() instanceof DoorBlock door) {
            door.setOpen(null, level, s, p, !s.getValue(DoorBlock.OPEN));
            return true;
        }
        return false;
    }

    /** Somebody is digging inside the stone nearby. */
    private static boolean mining(ServerLevel level, ServerPlayer player, RandomSource random) {
        if (level.canSeeSky(player.blockPosition())) return false;
        double angle = random.nextDouble() * Math.PI * 2;
        double dist = 10 + random.nextDouble() * 8;
        double x = player.getX() + Math.cos(angle) * dist;
        double z = player.getZ() + Math.sin(angle) * dist;
        double y = player.getY() + random.nextInt(5) - 2;
        int hits = 4 + random.nextInt(5);
        int delay = 0;
        for (int i = 0; i < hits; i++) {
            delay += 5 + random.nextInt(4);
            Scheduler.schedule(delay, () -> level.playSound(null, x, y, z, SoundEvents.STONE_HIT,
                    SoundSource.HOSTILE, 0.9F, 0.7F));
        }
        Scheduler.schedule(delay + 6, () -> level.playSound(null, x, y, z, SoundEvents.STONE_BREAK,
                SoundSource.HOSTILE, 1.0F, 0.8F));
        return true;
    }

    /** The vanilla cave sound, even on the surface in daylight. */
    private static boolean caveSound(ServerLevel level, ServerPlayer player, RandomSource random) {
        double angle = random.nextDouble() * Math.PI * 2;
        double x = player.getX() + Math.cos(angle) * 8;
        double z = player.getZ() + Math.sin(angle) * 8;
        level.playSound(null, x, player.getY(), z, SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT,
                0.9F, Mth.nextFloat(random, 0.5F, 0.9F));
        return true;
    }
}
