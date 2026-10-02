package com.brokenworld.world;

import com.brokenworld.BrokenWorld;
import com.brokenworld.util.Sight;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * "Somebody was here." While the player is away from home (or while their game seems to have crashed), things in and
 * around the house change: a sign by the bed, a diary in a chest written in the player's name, the chest gone through,
 * torches standing somewhere else, doors left open, a trail of footprints through the grass up to the door.
 * Nothing is destroyed: blocks are only moved, items only shuffled, and the book is added to a free slot.
 */
public final class HomeVisit {
    public enum Action { SIGN, BOOK, CHEST, TORCHES, DOORS, TRAIL }

    /** How far from the bed things are looked for. */
    private static final int RADIUS = 8;

    private HomeVisit() {}

    /** Does up to `count` different things around `home`; returns the ones that could be done. */
    public static List<Action> visit(ServerLevel level, ServerPlayer player, BlockPos home, RandomSource random, int count) {
        List<Action> actions = new ArrayList<>(List.of(Action.values()));
        Collections.shuffle(actions, new java.util.Random(random.nextLong()));
        List<Action> done = new ArrayList<>();
        for (Action a : actions) {
            if (done.size() >= count) break;
            if (run(a, level, player, home, random)) done.add(a);
        }
        if (!done.isEmpty()) {
            BrokenWorld.LOGGER.info("[BrokenWorld] somebody was in {}'s house: {}", player.getGameProfile().getName(), done);
        }
        return done;
    }

    public static boolean run(Action action, ServerLevel level, ServerPlayer player, BlockPos home, RandomSource random) {
        return switch (action) {
            case SIGN -> sign(level, player, home, random, "brokenworld.visit.sign." + (1 + random.nextInt(5)), null) != null;
            case BOOK -> book(level, player, home);
            case CHEST -> shuffleChest(level, home, random);
            case TORCHES -> moveTorches(level, null, home, random, 1 + random.nextInt(3)) > 0;
            case DOORS -> openDoors(level, home) > 0;
            case TRAIL -> trail(level, home, random);
        };
    }

    // ------------------------------------------------------------------ a sign

    /**
     * A sign on the floor next to `near` (a bed, or the player), its text facing `near` - or facing `facing` if given.
     * The text is a lang key; its lines are separated by "|". Returns where it stands.
     */
    @Nullable
    public static BlockPos sign(ServerLevel level, ServerPlayer player, BlockPos near, RandomSource random, String key,
                                @Nullable Vec3 facing) {
        List<BlockPos> spots = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(near.offset(-2, -1, -2), near.offset(2, 1, 2))) {
            if (p.equals(near) || !level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir()) continue;
            if (!Blocks.OAK_SIGN.defaultBlockState().canSurvive(level, p)) continue;
            spots.add(p.immutable());
        }
        if (spots.isEmpty()) return null;
        // the closest free spot: right next to the bed
        spots.sort((a, b) -> Double.compare(a.distSqr(near), b.distSqr(near)));
        BlockPos pos = spots.get(Math.min(spots.size() - 1, random.nextInt(2)));
        Vec3 reader = facing != null ? facing : Vec3.atCenterOf(near);
        // a sign's text faces the way it was placed from: from the reader, looking at it
        double dx = pos.getX() + 0.5 - reader.x, dz = pos.getZ() + 0.5 - reader.z;
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        level.setBlock(pos, Blocks.OAK_SIGN.defaultBlockState()
                .setValue(net.minecraft.world.level.block.StandingSignBlock.ROTATION,
                        RotationSegment.convertToSegment(yaw + 180.0F)), 3);
        if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            String[] lines = FakeChat.text(player, key).split("\\|");
            SignText text = new SignText();
            for (int i = 0; i < Math.min(4, lines.length); i++) text = text.setMessage(i, Component.literal(lines[i]));
            sign.setText(text, true);
            sign.setChanged();
            level.sendBlockUpdated(pos, sign.getBlockState(), sign.getBlockState(), 3);
        }
        return pos;
    }

    // ------------------------------------------------------------------ chests

    private static List<Container> chests(ServerLevel level, BlockPos home) {
        List<Container> found = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(home.offset(-RADIUS, -4, -RADIUS), home.offset(RADIUS, 4, RADIUS))) {
            if (!(level.getBlockState(p).getBlock() instanceof ChestBlock)) continue;
            BlockEntity be = level.getBlockEntity(p);
            if (be instanceof ChestBlockEntity chest) found.add(chest);
        }
        return found;
    }

    /** A diary in one of the chests, "written" by the player: what it saw. */
    public static boolean book(ServerLevel level, ServerPlayer player, BlockPos home) {
        for (Container chest : chests(level, home)) {
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                if (!chest.getItem(slot).isEmpty()) continue;
                chest.setItem(slot, diary(player));
                chest.setChanged();
                return true;
            }
        }
        return false;
    }

    public static ItemStack diary(ServerPlayer player) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.getOrCreateTag().putString("title", FakeChat.text(player, "brokenworld.visit.book.title"));
        book.getOrCreateTag().putString("author", player.getGameProfile().getName());
        ListTag pages = new ListTag();
        for (int i = 1; i <= 4; i++) {
            pages.add(StringTag.valueOf(Component.Serializer.toJson(
                    Component.literal(FakeChat.text(player, "brokenworld.visit.book.page" + i)))));
        }
        book.getOrCreateTag().put("pages", pages);
        book.getOrCreateTag().putBoolean("resolved", true);
        return book;
    }

    /** Somebody went through a chest: everything is still there, in different slots. */
    public static boolean shuffleChest(ServerLevel level, BlockPos home, RandomSource random) {
        List<Container> chests = chests(level, home);
        Collections.shuffle(chests, new java.util.Random(random.nextLong()));
        for (Container chest : chests) {
            List<ItemStack> items = new ArrayList<>();
            boolean any = false;
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                items.add(chest.getItem(slot).copy());
                any |= !chest.getItem(slot).isEmpty();
            }
            if (!any) continue;
            Collections.shuffle(items, new java.util.Random(random.nextLong()));
            for (int slot = 0; slot < items.size(); slot++) chest.setItem(slot, items.get(slot));
            chest.setChanged();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ torches and doors

    private static boolean isTorch(BlockState s) {
        return s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH) || s.is(Blocks.SOUL_TORCH) || s.is(Blocks.SOUL_WALL_TORCH);
    }

    /**
     * Up to `count` torches around `center` stand somewhere else (a few blocks away, on the floor). With a watcher,
     * only torches they cannot see right now are moved. Returns how many moved.
     */
    public static int moveTorches(ServerLevel level, @Nullable ServerPlayer watcher, BlockPos center, RandomSource random,
                                  int count) {
        List<BlockPos> torches = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-RADIUS, -4, -RADIUS), center.offset(RADIUS, 4, RADIUS))) {
            if (isTorch(level.getBlockState(p)) && (watcher == null || !Sight.canSee(watcher, Vec3.atCenterOf(p)))) {
                torches.add(p.immutable());
            }
        }
        Collections.shuffle(torches, new java.util.Random(random.nextLong()));
        int moved = 0;
        for (BlockPos from : torches) {
            if (moved >= count) break;
            BlockState old = level.getBlockState(from);
            boolean soul = old.is(Blocks.SOUL_TORCH) || old.is(Blocks.SOUL_WALL_TORCH);
            BlockState torch = (soul ? Blocks.SOUL_TORCH : Blocks.TORCH).defaultBlockState();
            for (int tries = 0; tries < 60; tries++) {
                BlockPos to = from.offset(random.nextInt(9) - 4, random.nextInt(3) - 1, random.nextInt(9) - 4);
                // far enough to notice that it is not where you put it
                if (to.distSqr(from) < 9 || !level.getBlockState(to).isAir() || !torch.canSurvive(level, to)) continue;
                if (watcher != null && Sight.canSee(watcher, Vec3.atCenterOf(to))) continue;
                level.removeBlock(from, false);
                level.setBlock(to, torch, 3);
                moved++;
                break;
            }
        }
        return moved;
    }

    /** All closed wooden doors around `center` are left open. Returns how many. */
    public static int openDoors(ServerLevel level, BlockPos center) {
        int opened = 0;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-RADIUS, -4, -RADIUS), center.offset(RADIUS, 4, RADIUS))) {
            BlockState s = level.getBlockState(p);
            if (s.is(BlockTags.WOODEN_DOORS) && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                    && !s.getValue(DoorBlock.OPEN) && s.getBlock() instanceof DoorBlock door) {
                door.setOpen(null, level, s, p.immutable(), true);
                opened++;
            }
        }
        return opened;
    }

    // ------------------------------------------------------------------ footprints

    /**
     * Footprints from the woods up to the house: every other block of grass along a line becomes a path block,
     * ending a few blocks from the bed (at the door, more or less).
     */
    public static boolean trail(ServerLevel level, BlockPos home, RandomSource random) {
        double angle = random.nextDouble() * Math.PI * 2;
        int length = 16 + random.nextInt(8);
        int made = 0;
        for (int i = length; i >= 3; i--) {
            if ((i & 1) == 1) continue; // footprints, not a road
            double wobble = Math.sin(i * 0.7) * 0.8;
            int x = Mth.floor(home.getX() + 0.5 + Math.cos(angle) * i - Math.sin(angle) * wobble);
            int z = Mth.floor(home.getZ() + 0.5 + Math.sin(angle) * i + Math.cos(angle) * wobble);
            if (!level.isLoaded(new BlockPos(x, home.getY(), z))) continue;
            // the ground near the house's height (under trees too): the first grass with air above, from above down
            for (int y = home.getY() + 4; y >= home.getY() - 5; y--) {
                BlockPos ground = new BlockPos(x, y, z);
                if (level.getBlockState(ground).is(Blocks.GRASS_BLOCK) && level.getBlockState(ground.above()).isAir()) {
                    level.setBlock(ground, Blocks.DIRT_PATH.defaultBlockState(), 3);
                    made++;
                    break;
                }
            }
        }
        return made >= 3;
    }

    /** The bed's head end, if the player has a bed in the overworld. */
    @Nullable
    public static BlockPos bed(ServerLevel overworld, ServerPlayer player) {
        BlockPos bed = player.getRespawnPosition();
        if (bed == null || player.getRespawnDimension() != net.minecraft.world.level.Level.OVERWORLD) return null;
        return overworld.getBlockState(bed).getBlock() instanceof BedBlock ? bed : null;
    }
}
