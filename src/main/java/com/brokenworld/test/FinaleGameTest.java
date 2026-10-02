package com.brokenworld.test;

import com.brokenworld.BrokenWorld;
import com.brokenworld.entity.SilhouetteEntity;
import com.brokenworld.registry.ModItems;
import com.brokenworld.world.BrokenWorldState;
import com.brokenworld.world.Finale;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Plays the mod's big scripted parts with a test player. Run with: gradlew runGameTestServer
 */
@GameTestHolder(BrokenWorld.MODID)
@PrefixGameTestTemplate(false)
public final class FinaleGameTest {
    private FinaleGameTest() {}

    /**
     * GameTestHelper.makeMockServerPlayerInLevel() crashes on Forge 1.20.1 (its fake connection has no channel),
     * so make our own player on an in-memory channel.
     */
    private static ServerPlayer player(MinecraftServer server, ServerLevel level, String name) {
        return player(server, level, name, new EmbeddedChannel[1]);
    }

    /** Same, and hands back the channel, to look at what the server sent to that player. */
    private static ServerPlayer player(MinecraftServer server, ServerLevel level, String name, EmbeddedChannel[] channel) {
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        channel[0] = new EmbeddedChannel(connection);
        ServerPlayer player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), name));
        server.getPlayerList().placeNewPlayer(connection, player);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    /**
     * walk -> fall into the tunnel -> walk 200 blocks -> caught -> maze -> the 5 notes into the pit -> hall ->
     * 30 s at the lectern -> it comes -> the end (the test player is disconnected; the world starts over at stage 0).
     */
    @GameTest(template = "empty", timeoutTicks = 6000, batch = "finale")
    public static void finaleRunsToTheEnd(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel();
        MinecraftServer server = overworld.getServer();
        ServerPlayer player = player(server, overworld, "finale-test");
        BlockPos start = helper.absolutePos(BlockPos.ZERO);
        player.teleportTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);

        int[] step = {0};
        int[] timer = {0};
        Vec3[] held = {Vec3.ZERO};
        helper.onEachTick(() -> {
            timer[0]++;
            ServerLevel tunnel = server.getLevel(Finale.TUNNEL);
            // A real client reports its movement and so gets the chunks around it loaded; the test player must
            // tell the chunk map itself after being teleported around (e.g. for the notes in the maze to load).
            ((ServerLevel) player.level()).getChunkSource().move(player);
            switch (step[0]) {
                case 0 -> { // the third day comes
                    BrokenWorldState.get(server).setStage(BrokenWorldState.FINALE_STAGE, RandomSource.create());
                    next(step, timer);
                }
                case 1 -> { // the player walks... and sinks through the ground
                    if (player.level().dimension() == Level.OVERWORLD && timer[0] < 5) {
                        player.teleportTo(player.getX() + 0.15, player.getY(), player.getZ());
                        player.setOnGround(true);
                    } else if (player.level().dimension() == Finale.TUNNEL) {
                        helper.assertTrue(player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE,
                                "player should be in adventure mode in the tunnel");
                        next(step, timer);
                    }
                    if (timer[0] > 400) helper.fail("player never fell into the tunnel");
                }
                case 2 -> { // the tunnel is there; walk 210 blocks along it
                    if (timer[0] == 80) {
                        BlockPos feet = player.blockPosition();
                        BlockState floor = tunnel.getBlockState(feet.below());
                        helper.assertTrue(floor.is(Blocks.DEEPSLATE_TILES) || floor.is(Blocks.CRACKED_DEEPSLATE_TILES),
                                "no tunnel floor under the player: " + floor);
                        helper.assertTrue(!tunnel.getBlockState(feet.above(4)).isAir(), "no tunnel ceiling");
                        player.teleportTo(tunnel, 210.5, player.getY(), player.getZ(), -90F, 0F);
                        next(step, timer);
                    }
                }
                case 3 -> { // it catches the player, who wakes up in the maze
                    if (player.getX() > 1900 && player.getX() < 2100) next(step, timer);
                    else if (timer[0] > 2000) helper.fail("the player never got to the maze");
                }
                case 4 -> { // find the notes and throw them into the pit
                    Vec3 pit = Finale.pitForTests().orElseThrow();
                    List<BlockPos> spots = Finale.noteSpotsForTests();
                    helper.assertTrue(spots.size() == 5, "expected 5 note spots, found " + spots.size());
                    // walk to each dead end (a note appears there) and drop the note into the pit
                    // the test player does not load chunks like a real client does: keep the maze loaded
                    if (timer[0] == 1) {
                        for (BlockPos sp : spots) tunnel.setChunkForced(sp.getX() >> 4, sp.getZ() >> 4, true);
                    }
                    int visit = timer[0] / 80, t = timer[0] % 80;
                    if (visit < spots.size()) {
                        BlockPos spot = spots.get(visit);
                        if (t == 1) player.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 1.5);
                        if (t == 79) {
                            List<ItemEntity> here = tunnel.getEntitiesOfClass(ItemEntity.class, new AABB(spot).inflate(2),
                                    e -> e.getItem().is(ModItems.NOTE.get()));
                            helper.assertTrue(here.size() == 1, "no note in dead end " + visit + " (" + here.size()
                                    + ") ticking=" + tunnel.isPositionEntityTicking(spot) + " spot=" + spot
                                    + " player=" + player.blockPosition() + " in " + player.level().dimension().location());
                            here.get(0).setPos(pit.x, pit.y, pit.z);
                        }
                    }
                    if (player.getX() > 3990 && player.getX() < 4200) { // taken to the hall
                        for (BlockPos sp : spots) tunnel.setChunkForced(sp.getX() >> 4, sp.getZ() >> 4, false);
                        next(step, timer);
                    }
                    else if (timer[0] > 600) helper.fail("burning the notes did not open the hall");
                }
                case 5 -> { // frozen in the hall; it reads, grows, comes; when it arrives the world closes
                    if (timer[0] == 10) {
                        held[0] = player.position();
                        player.teleportTo(player.getX() + 3, player.getY(), player.getZ()); // try to move away
                    }
                    if (timer[0] == 13) {
                        helper.assertTrue(player.position().distanceTo(held[0]) < 0.2,
                                "players must not be able to move in the hall (moved " + player.position().distanceTo(held[0]) + ")");
                    }
                    if (player.hasDisconnected() || server.getPlayerList().getPlayer(player.getUUID()) == null) next(step, timer);
                    else if (timer[0] > 3000) helper.fail("the hall never ended");
                }
                default -> {
                }
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(step[0] == 6, "finale not finished (step " + step[0] + ")");
            helper.assertTrue(!Finale.active(), "finale should be over");
            helper.assertTrue(BrokenWorldState.get(server).getStage() == 0, "world should start over at stage 0");
        });
    }

    /**
     * A 5x5 house with a bed and a door: go far away, come back in, and it is bigger on the inside.
     * Breaking its wall from inside puts you back into the small real house.
     */
    @GameTest(template = "empty", timeoutTicks = 600, batch = "house")
    public static void houseIsBiggerOnTheInside(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel();
        MinecraftServer server = overworld.getServer();
        Finale.stop(server); // in case a previous test left it running
        BrokenWorldState.get(server).setStage(1, RandomSource.create());
        BlockPos o = helper.absolutePos(new BlockPos(6, 0, 6)); // floor corner, away from the test markers

        // 5x5 outside, 3x3 inside, 3 high, stone, with a roof, a door on the north side and a bed
        for (int x = 0; x < 5; x++)
            for (int z = 0; z < 5; z++)
                for (int y = 0; y <= 4; y++) {
                    boolean shell = y == 0 || y == 4 || x == 0 || x == 4 || z == 0 || z == 4;
                    overworld.setBlock(o.offset(x, y, z), shell ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
                }
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
        overworld.setBlock(o.offset(2, 1, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 3);
        overworld.setBlock(o.offset(2, 2, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
        BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
        BlockPos bedFoot = o.offset(3, 1, 2);
        overworld.setBlock(bedFoot, bed.setValue(BedBlock.PART, BedPart.FOOT), 3);
        overworld.setBlock(bedFoot.south(), bed.setValue(BedBlock.PART, BedPart.HEAD), 3);

        ServerPlayer player = player(server, overworld, "house-test");
        player.setRespawnPosition(Level.OVERWORLD, bedFoot, 0, true, false);
        player.teleportTo(o.getX() + 70.5, o.getY() + 1, o.getZ() + 2.5); // far from home

        int[] step = {0};
        int[] timer = {0};
        helper.onEachTick(() -> {
            timer[0]++;
            switch (step[0]) {
                case 0 -> { // after a while away, come back and step inside
                    if (timer[0] == 20) {
                        player.teleportTo(overworld, o.getX() + 1.5, o.getY() + 1, o.getZ() + 2.5, 0F, 0F);
                        next(step, timer);
                    }
                }
                case 1 -> {
                    if (player.level().dimension() == Finale.TUNNEL) {
                        // 3x wider: the room around the player must be much bigger than 3 blocks
                        ServerLevel pocket = (ServerLevel) player.level();
                        BlockPos feet = player.blockPosition();
                        int free = 0;
                        for (int dx = 1; dx < 12; dx++) if (pocket.getBlockState(feet.offset(dx, 0, 0)).isAir()) free++; else break;
                        helper.assertTrue(free >= 4, "the house is not bigger inside (free blocks: " + free + ")");
                        next(step, timer);
                    } else if (timer[0] > 60) {
                        helper.fail("the house did not get bigger on the inside");
                    }
                }
                case 2 -> { // try to break out through the floor
                    if (timer[0] == 5) {
                        boolean broken = player.gameMode.destroyBlock(player.blockPosition().below());
                        helper.assertTrue(!broken, "the wall should not break");
                    }
                    if (timer[0] > 5) {
                        helper.assertTrue(player.level().dimension() == Level.OVERWORLD, "should be back in the small house");
                        helper.assertTrue(player.blockPosition().closerThan(bedFoot, 4), "should be inside the real house");
                        next(step, timer);
                    }
                }
                default -> {
                }
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(step[0] == 3, "house test not finished (step " + step[0] + ")");
            server.getPlayerList().remove(player);
            BrokenWorldState.get(server).setStage(0, RandomSource.create());
        });
    }

    /** The world coming apart: a hole down to the bottom of the world; built things are never cut into. */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "glitch")
    public static void worldComesApart(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos o = helper.absolutePos(new BlockPos(3, 0, 3));
        int bottom = level.getMinBuildHeight();
        // a column of natural ground from the bottom of the world up to here
        for (int y = bottom; y <= o.getY(); y++)
            for (int dx = 0; dx < 2; dx++)
                for (int dz = 0; dz < 2; dz++)
                    level.setBlock(new BlockPos(o.getX() + dx, y, o.getZ() + dz),
                            (y == bottom ? Blocks.BEDROCK : y == o.getY() ? Blocks.GRASS_BLOCK : Blocks.STONE).defaultBlockState(), 2);
        helper.assertTrue(com.brokenworld.world.WorldGlitches.hole(level, o, 2), "the hole was refused");
        for (int y = bottom + 1; y <= o.getY(); y++) {
            helper.assertTrue(level.getBlockState(new BlockPos(o.getX(), y, o.getZ())).isAir(), "not dug at y=" + y);
        }
        helper.assertTrue(level.getBlockState(new BlockPos(o.getX(), bottom, o.getZ())).is(Blocks.BEDROCK), "bedrock must stay");

        // something built in the way: the hole is not made
        BlockPos o2 = o.offset(4, 0, 0);
        for (int y = bottom; y <= o2.getY(); y++)
            level.setBlock(o2.atY(y), (y == o2.getY() - 3 ? Blocks.OAK_PLANKS : Blocks.STONE).defaultBlockState(), 2);
        helper.assertTrue(!com.brokenworld.world.WorldGlitches.hole(level, o2, 1), "it must not cut into planks");
        helper.succeed();
    }

    /**
     * The face at the window: in a small stone room with one glass window, it appears outside the glass (while you look
     * the other way), face in the middle of the glass, facing in; you turn round and see it through the glass - it
     * backs away and is gone.
     */
    @GameTest(template = "empty", timeoutTicks = 300, batch = "window")
    public static void faceAtTheWindow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos o = helper.absolutePos(new BlockPos(4, 0, 2));
        int floor = o.getY() + 1; // where the player's feet are
        for (int x = -3; x <= 6; x++)
            for (int z = -1; z <= 7; z++)
                level.setBlock(new BlockPos(o.getX() + x, o.getY(), o.getZ() + z), Blocks.STONE.defaultBlockState(), 2);
        for (int x = 0; x <= 6; x++)
            for (int z = 0; z <= 6; z++)
                for (int y = floor; y <= floor + 3; y++) {
                    boolean wall = x == 0 || x == 6 || z == 0 || z == 6 || y == floor + 3;
                    level.setBlock(new BlockPos(o.getX() + x, y, o.getZ() + z),
                            (wall ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 2);
                }
        // (the tests run underground: dig out the yard outside the west wall)
        for (int x = -3; x <= -1; x++)
            for (int z = -1; z <= 7; z++)
                for (int y = floor; y <= floor + 4; y++)
                    level.setBlock(new BlockPos(o.getX() + x, y, o.getZ() + z), Blocks.AIR.defaultBlockState(), 2);
        BlockPos glass = new BlockPos(o.getX(), floor + 1, o.getZ() + 3); // west wall, at eye level
        level.setBlock(glass, Blocks.GLASS.defaultBlockState(), 2);

        ServerPlayer player = player(level.getServer(), level, "window_test");
        player.teleportTo(level, o.getX() + 4.5, floor, o.getZ() + 3.5, -90.0F, 0.0F); // looking east, away from it
        player.setYHeadRot(-90.0F);
        level.getChunkSource().move(player);

        SilhouetteEntity e = com.brokenworld.world.Director.spawn(level, player, SilhouetteEntity.Mode.WINDOW, 2);
        if (e == null || e.getMode() != SilhouetteEntity.Mode.WINDOW) {
            BlockPos out = glass.west();
            player.discard();
            helper.fail("it did not come to the window: " + (e == null ? "nothing" : e.getMode())
                    + " | player " + player.blockPosition() + " sky " + level.canSeeSky(player.blockPosition())
                    + " | outside sky " + level.canSeeSky(out) + " glass " + com.brokenworld.world.SpawnFinder.isGlass(level.getBlockState(glass))
                    + " | in view " + com.brokenworld.util.Sight.isInViewCone(player, Vec3.atCenterOf(glass), com.brokenworld.util.Sight.ON_SCREEN_COS)
                    + " clear " + com.brokenworld.util.Sight.hasClearView(player, player.getEyePosition(), Vec3.atCenterOf(out))
                    + " look " + player.getViewVector(1.0F)
                    + " hit " + level.clip(new net.minecraft.world.level.ClipContext(player.getEyePosition(), Vec3.atCenterOf(out),
                            net.minecraft.world.level.ClipContext.Block.VISUAL, net.minecraft.world.level.ClipContext.Fluid.NONE, player)).getLocation()
                    + " eye " + player.getEyePosition() + " out " + out + " = " + level.getBlockState(out)
                    + " / below " + level.getBlockState(out.below()) + " / above " + level.getBlockState(out.above()));
            return;
        }
        helper.assertTrue(e.getStance() == SilhouetteEntity.Stance.WINDOW, "not leaning on the glass");
        // its face touches the outer (west) side of the glass block
        helper.assertTrue(Math.abs(e.getX() + com.brokenworld.world.Director.WINDOW_FACE_FORWARD - glass.getX()) < 0.01
                && Math.abs(e.getZ() - (glass.getZ() + 0.5)) < 0.01, "not right outside the glass: " + e.position());
        helper.assertTrue(Math.abs(e.getY() + 2.15 - (glass.getY() + 0.5)) < 0.01, "its face is not in the glass: " + e.getY());
        helper.assertTrue(Math.abs(Mth.wrapDegrees(e.yBodyRot + 90.0F)) < 1.0F, "not facing in: " + e.yBodyRot);

        int[] t = {0};
        helper.onEachTick(() -> {
            t[0]++;
            if (t[0] == 20) { // turn round: it is right there in the window
                player.teleportTo(level, player.getX(), player.getY(), player.getZ(), 90.0F, 0.0F);
                player.setYHeadRot(90.0F);
            }
            if (t[0] > 20 && e.isRemoved()) {
                helper.assertTrue(t[0] > 20 + 30, "gone too fast - it should stay and stare for a moment");
                player.discard();
                helper.succeed();
            }
            if (t[0] > 220) helper.fail("seen through the glass, it did not back away and vanish");
        });
    }

    /**
     * Every batch puts its test at the same spot, while longer tests of earlier batches may still be running there:
     * tests that build a lot build further away, each in its own place.
     */
    private static BlockPos away(GameTestHelper helper, int slot) {
        return helper.absolutePos(new BlockPos(2, 0, 2)).offset(0, 0, 80 * slot);
    }

    /** A small stone house underground (the tests run inside deepslate): floor, walls, roof, a bed, a chest, torches, a door. */
    private static BlockPos[] testHouse(ServerLevel level, BlockPos o) {
        int floor = o.getY() + 1;
        for (int x = -6; x <= 12; x++)
            for (int z = -6; z <= 12; z++)
                for (int y = o.getY(); y <= floor + 4; y++) {
                    boolean wall = (x == 0 || x == 6 || z == 0 || z == 6) && x >= 0 && x <= 6 && z >= 0 && z <= 6;
                    boolean roof = y == floor + 3 && x >= 0 && x <= 6 && z >= 0 && z <= 6;
                    level.setBlock(new BlockPos(o.getX() + x, y, o.getZ() + z),
                            (y == o.getY() || wall && y < floor + 3 || roof ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 2);
                }
        BlockPos bedHead = new BlockPos(o.getX() + 2, floor, o.getZ() + 2);
        level.setBlock(bedHead, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH)
                .setValue(BedBlock.PART, BedPart.HEAD), 3);
        level.setBlock(bedHead.south(), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH)
                .setValue(BedBlock.PART, BedPart.FOOT), 3);
        BlockPos chest = new BlockPos(o.getX() + 5, floor, o.getZ() + 1);
        level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
        BlockPos torch1 = new BlockPos(o.getX() + 1, floor, o.getZ() + 5);
        BlockPos torch2 = new BlockPos(o.getX() + 5, floor, o.getZ() + 5);
        level.setBlock(torch1, Blocks.TORCH.defaultBlockState(), 3);
        level.setBlock(torch2, Blocks.TORCH.defaultBlockState(), 3);
        BlockPos door = new BlockPos(o.getX() + 3, floor, o.getZ() + 6);
        level.setBlock(door, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 3);
        level.setBlock(door.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
        return new BlockPos[]{bedHead, chest, torch1, torch2, door};
    }

    private static int countTorches(ServerLevel level, BlockPos o) {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-6, 0, -6), o.offset(12, 6, 12)))
            if (level.getBlockState(p).is(Blocks.TORCH)) n++;
        return n;
    }

    /** Somebody was in the house: sign by the bed, a diary in the chest, the chest gone through, torches moved, door open. */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "visit")
    public static void somebodyWasInTheHouse(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos o = away(helper, 1);
        BlockPos[] h = testHouse(level, o);
        BlockPos bed = h[0];
        var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(h[1]);
        net.minecraft.world.item.Item[] things = {net.minecraft.world.item.Items.COAL, net.minecraft.world.item.Items.BREAD,
                net.minecraft.world.item.Items.IRON_INGOT, net.minecraft.world.item.Items.STICK,
                net.minecraft.world.item.Items.APPLE, net.minecraft.world.item.Items.COBBLESTONE};
        for (int i = 0; i < things.length; i++) chest.setItem(i, new net.minecraft.world.item.ItemStack(things[i], 1 + i));
        ServerPlayer player = player(level.getServer(), level, "visit_test");
        RandomSource random = RandomSource.create(7);
        try {
            BlockPos sign = com.brokenworld.world.HomeVisit.sign(level, player, bed, random, "brokenworld.visit.sign.1", null);
            helper.assertTrue(sign != null && sign.distSqr(bed) <= 8, "no sign by the bed: " + sign);
            var text = ((net.minecraft.world.level.block.entity.SignBlockEntity) level.getBlockEntity(sign)).getFrontText();
            helper.assertTrue(!text.getMessage(0, false).getString().isEmpty(), "the sign is blank");

            helper.assertTrue(com.brokenworld.world.HomeVisit.book(level, player, bed), "no diary");
            boolean diary = false;
            for (int i = 0; i < chest.getContainerSize(); i++) {
                var it = chest.getItem(i);
                diary |= it.is(net.minecraft.world.item.Items.WRITTEN_BOOK) && it.getTag() != null
                        && "visit_test".equals(it.getTag().getString("author")) && it.getTag().getList("pages", 8).size() == 4;
            }
            helper.assertTrue(diary, "the diary is not in the chest, in the player's name, 4 pages");

            int before = 0;
            for (int i = 0; i < chest.getContainerSize(); i++) before += chest.getItem(i).getCount();
            helper.assertTrue(com.brokenworld.world.HomeVisit.shuffleChest(level, bed, random), "the chest was not gone through");
            int after = 0;
            for (int i = 0; i < chest.getContainerSize(); i++) after += chest.getItem(i).getCount();
            helper.assertTrue(before == after, "items lost while shuffling: " + before + " -> " + after);

            int torches = countTorches(level, o);
            int moved = com.brokenworld.world.HomeVisit.moveTorches(level, null, bed, random, 2);
            helper.assertTrue(moved >= 1, "no torch moved");
            helper.assertTrue(countTorches(level, o) == torches, "torches lost: " + torches + " -> " + countTorches(level, o));
            helper.assertTrue(!level.getBlockState(h[2]).is(Blocks.TORCH) || !level.getBlockState(h[3]).is(Blocks.TORCH),
                    "the torches stand where they were");

            helper.assertTrue(com.brokenworld.world.HomeVisit.openDoors(level, bed) == 1, "the door was not left open");
            helper.assertTrue(level.getBlockState(h[4]).getValue(DoorBlock.OPEN), "door closed");
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    /** Like a player's test: flat grass, a torch they placed, no chest, no door - the crash and the visit still show. */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "bare")
    public static void bareGround(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos o = away(helper, 2);
        for (int x = -12; x <= 12; x++)
            for (int z = -12; z <= 12; z++)
                for (int y = 0; y <= 8; y++)
                    level.setBlock(o.offset(x, y, z), (y == 0 ? Blocks.GRASS_BLOCK : Blocks.AIR).defaultBlockState(), 2);
        BlockPos torch = o.offset(2, 1, 0);
        level.setBlock(torch, Blocks.TORCH.defaultBlockState(), 3);
        ServerPlayer player = player(level.getServer(), level, "bare_test");
        try {
            player.teleportTo(level, o.getX() + 0.5, o.getY() + 1, o.getZ() + 0.5, 0.0F, 0.0F);
            level.getChunkSource().move(player);
            StringBuilder log = new StringBuilder();
            for (com.brokenworld.world.HomeVisit.Action a : com.brokenworld.world.HomeVisit.Action.values()) {
                log.append(a).append('=').append(com.brokenworld.world.HomeVisit.run(a, level, player, o.above(),
                        RandomSource.create(a.ordinal()))).append(' ');
            }
            helper.assertTrue(!level.getBlockState(torch).is(Blocks.TORCH), "the torch did not move: " + log);
            int paths = 0;
            for (BlockPos p : BlockPos.betweenClosed(o.offset(-12, 0, -12), o.offset(12, 0, 12)))
                if (level.getBlockState(p).is(Blocks.DIRT_PATH)) paths++;
            helper.assertTrue(paths >= 3, "no footprints: " + paths + " | " + log);
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    /** Every vanilla sound (but the menu and the music) is broken from stage 2 on - and always the same way. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "sounds")
    public static void everySoundBreaks(GameTestHelper helper) {
        long salt = 0x1234_5678_9ABCL;
        int eligible = 0, swappedLow = 0;
        List<String> notBroken = new java.util.ArrayList<>();
        for (net.minecraft.resources.ResourceLocation id : net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getKeys()) {
            boolean ok = com.brokenworld.util.SoundBreaker.eligible(id);
            String p = id.getPath();
            if (p.startsWith("ui.") || p.startsWith("music")) {
                helper.assertTrue(!ok, "menu/music must stay: " + id);
                continue;
            }
            if (!"minecraft".equals(id.getNamespace())) continue;
            eligible++;
            var other = com.brokenworld.util.SoundBreaker.swap(id, 0.8F, salt);
            if (other == null || other.equals(id)) notBroken.add(p);
            else helper.assertTrue(other.equals(com.brokenworld.util.SoundBreaker.swap(id, 0.8F, salt)), "not always the same: " + id);
            if (com.brokenworld.util.SoundBreaker.swap(id, 0.15F, salt) != null) swappedLow++;
        }
        helper.assertTrue(notBroken.isEmpty(), notBroken.size() + " of " + eligible + " sounds are not broken: " + notBroken);
        helper.assertTrue(com.brokenworld.util.SoundBreaker.swap(new net.minecraft.resources.ResourceLocation("entity.egg.throw"), 0.8F, salt) != null,
                "throwing an egg sounds normal");
        helper.assertTrue(swappedLow > 0 && swappedLow < eligible, "stage 1 should break some, not all: " + swappedLow + "/" + eligible);
        BrokenWorld.LOGGER.info("[BrokenWorld] sounds: {} broken at stage 2+, {} at the start of stage 1", eligible, swappedLow);
        helper.succeed();
    }

    /** The fake crash: they come back turned around, facing a sign, unhurt, with the door open. */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "crash")
    public static void connectionLost(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos o = away(helper, 3);
        BlockPos[] h = testHouse(level, o);
        EmbeddedChannel[] channel = new EmbeddedChannel[1];
        ServerPlayer player = player(level.getServer(), level, "crash_test", channel);
        try {
            player.teleportTo(level, o.getX() + 3.5, o.getY() + 1, o.getZ() + 3.5, 0.0F, 0.0F); // looking south
            player.setYHeadRot(0.0F);
            level.getChunkSource().move(player);
            com.brokenworld.world.FakeCrash.start(player, RandomSource.create(3));
            helper.assertTrue(Math.abs(Mth.wrapDegrees(player.getYRot() - 180.0F)) < 1.0F, "not turned around: " + player.getYRot());
            helper.assertTrue(com.brokenworld.world.FakeCrash.isProtected(player), "can be hurt while 'disconnected'");
            helper.assertTrue(level.getBlockState(h[4]).getValue(DoorBlock.OPEN), "the door is not open");
            boolean sign = false;
            for (BlockPos p : BlockPos.betweenClosed(o.offset(1, 1, 1), o.offset(5, 2, 5)))
                sign |= level.getBlockState(p).is(Blocks.OAK_SIGN);
            helper.assertTrue(sign, "no sign behind them");
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    /** An animal that behaves like a player: no name to give it away, freezes while you look at it, comes when you don't. */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "playerlike")
    public static void animalActsLikeAPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        // a clear stone path, away from the test's own markers, so nothing stands between them
        BlockPos o = helper.absolutePos(new BlockPos(4, 1, 2));
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 13; dz++) {
                level.setBlock(o.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 2);
                for (int dy = 0; dy <= 3; dy++) level.setBlock(o.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
            }
        net.minecraft.world.entity.animal.Cow cow = net.minecraft.world.entity.EntityType.COW.create(level);
        cow.moveTo(o.getX() + 0.5, o.getY(), o.getZ() + 10.5, 0, 0);
        level.addFreshEntity(cow);
        com.brokenworld.world.PlayerLikeAnimals.convert(cow);
        helper.assertTrue(!cow.hasCustomName() && !cow.isCustomNameVisible(), "nothing should give it away");
        helper.assertTrue(com.brokenworld.world.PlayerLikeAnimals.isPlayerLike(cow), "it should be marked");

        ServerPlayer player = player(server, level, "cow-test");
        player.teleportTo(o.getX() + 0.5, o.getY(), o.getZ() + 0.5);
        // look straight at the cow (where a player looks is the direction of its head)
        player.setYRot(0);
        player.setYHeadRot(0);
        player.setXRot(0);
        int[] timer = {0};
        Vec3[] start = {null};
        helper.onEachTick(() -> {
            timer[0]++;
            if (timer[0] == 20) start[0] = cow.position();
            if (timer[0] == 80) {
                double moved = Math.hypot(cow.getX() - start[0].x, cow.getZ() - start[0].z);
                helper.assertTrue(moved < 0.6, "it should freeze while watched (moved " + moved + ")");
                player.setYRot(180); // look away
                player.setYHeadRot(180);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(timer[0] > 160, "waiting");
            // with the player looking away it comes closer
            helper.assertTrue(cow.distanceTo(player) < 9.0, "it should have come closer (" + cow.distanceTo(player) + ")");
            server.getPlayerList().remove(player);
            cow.discard();
        });
    }

    /** Somebody else "joins" in chat: join message, a TAB entry, and your own words written back to you. */
    @GameTest(template = "empty", timeoutTicks = 400, batch = "chat")
    public static void strangerInChat(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        EmbeddedChannel[] channel = new EmbeddedChannel[1];
        ServerPlayer player = player(server, level, "chat-test", channel);
        channel[0].outboundMessages().clear();
        com.brokenworld.world.FakeChat.visit(player, 1);
        java.util.List<Object> sent = new java.util.ArrayList<>();
        int[] timer = {0};
        helper.onEachTick(() -> {
            timer[0]++;
            Object m;
            while ((m = channel[0].readOutbound()) != null) sent.add(m);
            if (timer[0] == 20) com.brokenworld.world.FakeChat.onChat(player, "test-echo-123");
        });
        helper.succeedWhen(() -> {
            boolean joined = sent.stream().anyMatch(o -> o instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket c
                    && c.content().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                    && t.getKey().equals("multiplayer.player.joined"));
            boolean inTab = sent.stream().anyMatch(o -> o instanceof net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket);
            boolean echoed = sent.stream().anyMatch(o -> o instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket c
                    && c.content().getString().contains("test-echo-123"));
            helper.assertTrue(joined, "no join message");
            helper.assertTrue(inTab, "not in the TAB list");
            helper.assertTrue(echoed, "your words were not written back");
            server.getPlayerList().remove(player);
        });
    }

    /** With another player online it pretends to be them: whispers only to you, in their way of writing. */
    @GameTest(template = "empty", timeoutTicks = 600, batch = "chat2")
    public static void friendIsImpersonated(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        EmbeddedChannel[] youCh = new EmbeddedChannel[1];
        EmbeddedChannel[] friendCh = new EmbeddedChannel[1];
        ServerPlayer you = player(server, level, "you-test", youCh);
        ServerPlayer friend = player(server, level, "friend-test", friendCh);
        // the friend writes in lower case, no full stops, with ")"
        for (String m : new String[]{"привет всем)", "го в шахту", "я тут)", "ок"}) {
            com.brokenworld.world.FakeChat.rememberForTests(friend, m);
        }
        youCh[0].outboundMessages().clear();
        friendCh[0].outboundMessages().clear();
        com.brokenworld.world.FakeChat.visit(you, 2);
        java.util.List<String> toYou = new java.util.ArrayList<>();
        java.util.List<Object> toFriend = new java.util.ArrayList<>();
        int[] timer = {0};
        helper.onEachTick(() -> {
            timer[0]++;
            Object m;
            while ((m = youCh[0].readOutbound()) != null) {
                if (m instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket c
                        && c.content().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                        && t.getKey().equals("commands.message.display.incoming")) {
                    toYou.add(String.valueOf(t.getArgs()[0]) + "|" + t.getArgs()[1]);
                }
            }
            while ((m = friendCh[0].readOutbound()) != null) {
                if (m instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket) toFriend.add(m);
            }
            if (timer[0] == 200) com.brokenworld.world.FakeChat.onChat(you, "кто это?");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(timer[0] > 400, "waiting");
            helper.assertTrue(toYou.size() >= 2, "no whispers 'from' the friend: " + toYou);
            for (String w : toYou) {
                helper.assertTrue(w.startsWith("friend-test|"), "whisper not in the friend's name: " + w);
                String text = w.substring(w.indexOf('|') + 1);
                helper.assertTrue(text.equals(text.toLowerCase(java.util.Locale.ROOT)), "not in their lower case: " + text);
                helper.assertTrue(!text.endsWith("."), "they never use full stops: " + text);
            }
            helper.assertTrue(toFriend.isEmpty(), "the friend must not see any of it");
            server.getPlayerList().remove(you);
            server.getPlayerList().remove(friend);
        });
    }

    /** The first sunset: day and night flicker for 10 seconds, then it stays night - once per world. */
    @GameTest(template = "empty", timeoutTicks = 400, batch = "sunset")
    public static void firstSunsetGoesWrong(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BrokenWorldState.get(server).setStage(0, RandomSource.create());
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(true, server);
        long day = level.getDayTime() / 24000L;
        level.setDayTime(day * 24000L + 12300L); // the sun is setting
        int[] timer = {0};
        int[] flips = {0};
        boolean[] wasDay = {false};
        helper.onEachTick(() -> {
            timer[0]++;
            long time = level.getDayTime() % 24000L;
            boolean isDay = time < 12000;
            if (timer[0] > 2 && timer[0] < 200 && isDay != wasDay[0]) flips[0]++;
            wasDay[0] = isDay;
            if (timer[0] == 100) helper.assertTrue(com.brokenworld.world.Director.isFalseDay(), "should still be flickering");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(timer[0] > 230, "waiting");
            helper.assertTrue(flips[0] >= 8, "day and night should switch quickly many times, switched " + flips[0]);
            long time = level.getDayTime() % 24000L;
            helper.assertTrue(time >= 13000 && time < 23000, "should be night afterwards, time=" + time);
            helper.assertTrue(BrokenWorldState.get(server).isSunsetGlitchDone(), "should only happen once");
        });
    }

    private static void next(int[] step, int[] timer) {
        step[0]++;
        timer[0] = 0;
    }
}
