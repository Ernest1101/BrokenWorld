package com.brokenworld.world;

import com.brokenworld.BrokenWorld;
import com.brokenworld.Config;
import com.brokenworld.entity.SilhouetteEntity;
import com.brokenworld.item.NoteItem;
import com.brokenworld.network.ModNetwork;
import com.brokenworld.network.ModNetwork.ScreenFxPacket;
import com.brokenworld.registry.ModEntities;
import com.brokenworld.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The end of the broken world (stage 4, day 3). All of it happens in the empty dimension brokenworld:tunnel.
 * <ol>
 *   <li><b>Falling.</b> The silhouette is gone. The next time a player walks, they sink through the ground (the camera
 *       goes through the block textures) into black. Everyone falls within a few seconds of each other.</li>
 *   <li><b>Tunnel.</b> They wake up together at the closed end of an endless lit tunnel; far ahead it stands and
 *       watches. After 200 blocks it appears far behind them and follows, slowly, then faster than anyone can run.</li>
 *   <li><b>Maze.</b> Whoever it catches gets a screamer and wakes up in a dark maze. Five notes lie in its dead ends;
 *       thrown into the pit in the middle, they vanish. Now and then it is seen at the end of a corridor.</li>
 *   <li><b>Hall.</b> When all five are in the pit, everyone is taken to a very long hall, where they cannot move.
 *       Far away in the middle it stands behind a lectern, reading a book, for 30 seconds. Then the lectern bursts
 *       into a storm of particles and is gone, it grows twice as big, and walks straight at them: slowly, then
 *       faster and faster.</li>
 *   <li><b>The end.</b> When it reaches someone: the screamer, creaking, and the game leaves the world - which is then
 *       deleted (singleplayer, config deleteWorldAtEnd). On a server everyone is disconnected.</li>
 * </ol>
 */
public final class Finale {
    public static final ResourceKey<Level> TUNNEL =
            ResourceKey.create(Registries.DIMENSION, new ResourceLocation(BrokenWorld.MODID, "tunnel"));
    private static final String PREVIOUS_MODE_TAG = BrokenWorld.MODID + "_previous_mode";

    private static final int START_X = 0;
    private static final int FLOOR_Y = 63;
    /** Blocks the players have to walk before it comes. */
    private static final int CHASE_AFTER = 200;
    /** ...or it comes anyway after this long. */
    private static final int CHASE_TIMEOUT = 20 * 180;
    private static final int FALL_TICKS = 45;
    /** Everyone else falls at most this long after the first player. */
    private static final int GATHER_TIMEOUT = 20 * 12;
    private static final int SCREAMER_TICKS = 30;
    /** Screamer + a moment of silence and black, then the maze. */
    private static final int CAUGHT_TICKS = 70;
    /** How long it stands at the lectern, reading. */
    private static final int HALL_WAIT = 20 * 30;
    /** The lectern bursting and it growing. */
    private static final int GROW_TICKS = 50;
    private static final float GROWN = 2.0F;
    private static final float READING_PITCH = 50.0F;
    /** The final screamer + creaking, then the world closes. */
    private static final int ENDING_TICKS = 90;
    private static final RandomSource RANDOM = RandomSource.create();

    private enum Phase { NONE, FALLING, WALK, CHASE, MAZE, HALL_WAIT, HALL_CHASE, ENDING }

    private enum State { WAITING, FALLING, ARRIVED, PLAYING, CAUGHT, MAZE, HALL }

    private static final class Participant {
        State state = State.WAITING;
        int timer;
        Vec3 lastPos;
        /** In the hall: the spot the player is held on. */
        @Nullable Vec3 anchor;

        Participant(Vec3 pos) {
            this.lastPos = pos;
        }
    }

    private static Phase phase = Phase.NONE;
    private static final Map<UUID, Participant> PARTICIPANTS = new LinkedHashMap<>();
    private static int finaleNumber;
    private static int tunnelZ;
    private static int builtTo;
    private static int phaseTicks;
    private static int firstFallTick = -1;
    private static int arrivedCount;
    private static int quietTicks;
    @Nullable private static SilhouetteEntity lure;
    @Nullable private static SilhouetteEntity chaser;
    @Nullable private static SilhouetteEntity glimpse;
    private static double chaserX;
    private static double lastStepX;
    @Nullable private static FinaleAreas.Maze maze;
    @Nullable private static FinaleAreas.Hall hall;
    private static final Set<Integer> BURNED = new HashSet<>();
    /** The item entity of each note currently lying in the maze. */
    private static final UUID[] noteIds = new UUID[NoteItem.COUNT];
    private static int nextGlimpse;
    private static Vec3 monsterPos = Vec3.ZERO;
    private static double walked;

    private Finale() {}

    /** For the GameTest: where the notes lie. */
    public static java.util.List<BlockPos> noteSpotsForTests() {
        return maze == null ? java.util.List.of() : maze.notes();
    }

    /** For the GameTest: the middle of the maze's pit, once the maze exists. */
    public static Optional<Vec3> pitForTests() {
        return maze == null ? Optional.empty() : Optional.of(Vec3.atCenterOf(maze.pitTop().below(2)));
    }

    public static boolean active() {
        return phase != Phase.NONE;
    }

    public static boolean isParticipant(Player player) {
        return PARTICIPANTS.containsKey(player.getUUID());
    }

    /** True while the player must not take damage (anything but walking the tunnel on their own feet). */
    public static boolean isProtected(Player player) {
        Participant p = PARTICIPANTS.get(player.getUUID());
        return p != null && (p.state == State.FALLING || p.state == State.ARRIVED || p.state == State.CAUGHT
                || phase == Phase.ENDING);
    }

    public static void reset() {
        phase = Phase.NONE;
        PARTICIPANTS.clear();
        lure = null;
        chaser = null;
        glimpse = null;
        maze = null;
        hall = null;
        BURNED.clear();
        firstFallTick = -1;
    }

    public static void start(MinecraftServer server) {
        if (active()) return;
        ServerLevel tunnel = server.getLevel(TUNNEL);
        if (tunnel == null) {
            BrokenWorld.LOGGER.error("[BrokenWorld] tunnel dimension is missing, skipping the finale");
            BrokenWorldState.get(server).setStage(0, RANDOM);
            return;
        }
        reset();
        finaleNumber = BrokenWorldState.get(server).beginFinale();
        tunnelZ = finaleNumber * 64 + 8; // every finale gets fresh places
        builtTo = START_X - 2;
        buildTunnel(tunnel, START_X + 96);

        // It is gone.
        server.overworld().getEntities(ModEntities.SILHOUETTE.get(), e -> true).forEach(e -> e.vanish(false));

        phase = Phase.FALLING;
        phaseTicks = 0;
        arrivedCount = 0;
        quietTicks = 0;
        BrokenWorld.LOGGER.info("[BrokenWorld] finale #{} started", finaleNumber);
    }

    /** /brokenworld finale stop: everyone goes home, the world starts over. */
    public static void stop(MinecraftServer server) {
        if (!active()) return;
        discardMonsters();
        for (UUID id : PARTICIPANTS.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) returnHome(player);
        }
        reset();
        BrokenWorldState.get(server).setStage(0, RANDOM);
        BrokenWorld.LOGGER.info("[BrokenWorld] finale stopped");
    }

    public static void tick(MinecraftServer server) {
        if (!active()) return;
        phaseTicks++;
        ServerLevel tunnel = server.getLevel(TUNNEL);
        if (tunnel == null) {
            stop(server);
            return;
        }
        PARTICIPANTS.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);

        switch (phase) {
            case FALLING -> tickFalling(server, tunnel);
            case WALK -> tickWalk(server, tunnel);
            case CHASE -> tickChase(server, tunnel);
            case MAZE -> tickMaze(server, tunnel);
            case HALL_WAIT -> tickHallWait(server, tunnel);
            case HALL_CHASE -> tickHallChase(server, tunnel);
            case ENDING -> tickEnding(server);
            default -> {
            }
        }
        if (active() && phase != Phase.FALLING && PARTICIPANTS.isEmpty()) {
            discardMonsters();
            reset();
            BrokenWorldState.get(server).setStage(0, RANDOM);
        }
    }

    private static void setPhase(Phase next) {
        phase = next;
        phaseTicks = 0;
        BrokenWorld.LOGGER.info("[BrokenWorld] finale: {}", next);
    }

    // ------------------------------------------------------------------ 1. falling through the world

    private static void tickFalling(MinecraftServer server, ServerLevel tunnel) {
        ServerLevel overworld = server.overworld();
        if (firstFallTick < 0) { // anyone in the overworld joins until the first one has fallen
            for (ServerPlayer p : overworld.players()) {
                if (!p.isSpectator() && p.isAlive()) {
                    PARTICIPANTS.computeIfAbsent(p.getUUID(), id -> new Participant(p.position()));
                }
            }
        }
        boolean forced = firstFallTick >= 0 && phaseTicks - firstFallTick > GATHER_TIMEOUT;
        boolean allArrived = !PARTICIPANTS.isEmpty();
        for (Map.Entry<UUID, Participant> entry : PARTICIPANTS.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Participant p = entry.getValue();
            if (player == null) continue;
            switch (p.state) {
                case WAITING -> {
                    allArrived = false;
                    Vec3 pos = player.position();
                    double moved = Math.hypot(pos.x - p.lastPos.x, pos.z - p.lastPos.z);
                    p.lastPos = pos;
                    boolean walking = player.onGround() && moved > 0.04 && player.level() == overworld;
                    if (walking || forced) {
                        p.state = State.FALLING;
                        p.timer = 0;
                        if (firstFallTick < 0) firstFallTick = phaseTicks;
                    }
                }
                case FALLING -> {
                    allArrived = false;
                    tickFall(player, p, tunnel);
                }
                default -> {
                }
            }
        }
        if (allArrived) {
            if (++quietTicks > 40) beginWalk(server, tunnel);
        } else {
            quietTicks = 0;
        }
    }

    /** Sinks the player through the ground, then drops them into the tunnel. */
    private static void tickFall(ServerPlayer player, Participant p, ServerLevel tunnel) {
        p.timer++;
        player.fallDistance = 0;
        if (p.timer == 1) {
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0, false, false));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 1.0F, 0.5F);
        }
        if (p.timer < FALL_TICKS) {
            double speed = 0.06 + p.timer * 0.01; // slowly at first, then quicker
            player.teleportTo(player.getX(), player.getY() - speed, player.getZ());
            player.setDeltaMovement(Vec3.ZERO);
            if (p.timer == FALL_TICKS - 14) ModNetwork.sendFx(player, ScreenFxPacket.Type.BLACK_ON, 12);
            return;
        }
        int i = arrivedCount++;
        rememberMode(player);
        player.setGameMode(GameType.ADVENTURE);
        player.removeEffect(MobEffects.DARKNESS);
        player.teleportTo(tunnel, START_X + 1.5 + (i / 3), FLOOR_Y + 1, tunnelZ + (i % 3) - 1 + 0.5, -90.0F, 0.0F);
        player.fallDistance = 0;
        ModNetwork.sendFx(player, ScreenFxPacket.Type.BLACK_ON, 0);
        p.state = State.ARRIVED;
        BrokenWorld.LOGGER.info("[BrokenWorld] {} fell into the tunnel", player.getName().getString());
    }

    // ------------------------------------------------------------------ 2. the tunnel

    private static void beginWalk(MinecraftServer server, ServerLevel tunnel) {
        setPhase(Phase.WALK);
        for (Map.Entry<UUID, Participant> entry : PARTICIPANTS.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;
            entry.getValue().state = State.PLAYING;
            ModNetwork.sendFx(player, ScreenFxPacket.Type.BLACK_OFF, 60);
        }
        lure = spawnMonster(tunnel, new Vec3(START_X + 64.5, FLOOR_Y + 1, tunnelZ + 0.5), 90.0F, false);
    }

    private static void tickWalk(MinecraftServer server, ServerLevel tunnel) {
        double maxX = furthest(server, tunnel, true);
        buildTunnel(tunnel, (int) maxX + 96);
        if (lure != null && !lure.isRemoved() && anyWithin(tunnel, lure.position(), 20)) lure.vanish(false);
        if (maxX - START_X >= CHASE_AFTER || phaseTicks > CHASE_TIMEOUT) beginChase(server, tunnel);
    }

    private static void beginChase(MinecraftServer server, ServerLevel tunnel) {
        setPhase(Phase.CHASE);
        if (lure != null && !lure.isRemoved()) lure.vanish(false);
        chaserX = Math.max(START_X + 0.5, furthest(server, tunnel, false) - 90);
        lastStepX = chaserX;
        chaser = spawnMonster(tunnel, new Vec3(chaserX, FLOOR_Y + 1, tunnelZ + 0.5), -90.0F, true);
        chaser.setJerky(true);
        chaser.setJawOpen(true);
        tunnel.playSound(null, chaserX, FLOOR_Y + 2, tunnelZ + 0.5, SoundEvents.AMBIENT_CAVE.value(),
                SoundSource.HOSTILE, 6.0F, 0.5F);
        // Get the next places ready while they run.
        maze = FinaleAreas.buildMaze(tunnel, new BlockPos(2000, FLOOR_Y, finaleNumber * 256), RANDOM);
        java.util.Arrays.fill(noteIds, null);
        hall = FinaleAreas.buildHall(tunnel, new BlockPos(4000, FLOOR_Y, finaleNumber * 256));
        BrokenWorld.LOGGER.info("[BrokenWorld] finale: it follows them from x={}", (int) chaserX);
    }

    private static void tickChase(MinecraftServer server, ServerLevel tunnel) {
        // Walking pace at first; faster than a sprint after ~22 s; unstoppable after that.
        double speed = Math.min(0.6, 0.035 + phaseTicks * 0.0006);
        chaserX += speed;
        if (chaser == null || chaser.isRemoved()) {
            chaser = spawnMonster(tunnel, new Vec3(chaserX, FLOOR_Y + 1, tunnelZ + 0.5), -90.0F, true);
            chaser.setJerky(true);
            chaser.setJawOpen(true);
        }
        chaser.setPos(chaserX, FLOOR_Y + 1, tunnelZ + 0.5);
        chaser.setFacing(-90.0F);
        if (chaserX - lastStepX >= 2.2) {
            lastStepX = chaserX;
            footstep(tunnel, chaser.position(), speed);
        }
        buildTunnel(tunnel, (int) furthest(server, tunnel, true) + 96);

        boolean anyoneLeft = false;
        for (Map.Entry<UUID, Participant> entry : PARTICIPANTS.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Participant p = entry.getValue();
            if (player == null) continue;
            if (p.state == State.PLAYING && player.level() == tunnel && player.getX() <= chaserX + 1.0) {
                catchPlayer(player, p);
            } else if (p.state == State.CAUGHT && ++p.timer >= CAUGHT_TICKS) {
                sendToMaze(player, p);
            }
            if (p.state == State.PLAYING || p.state == State.CAUGHT) anyoneLeft = true;
        }
        if (!anyoneLeft) {
            if (chaser != null) chaser.discard();
            chaser = null;
            setPhase(Phase.MAZE);
            nextGlimpse = 20 * 25;
        }
    }

    private static void catchPlayer(ServerPlayer player, Participant p) {
        p.state = State.CAUGHT;
        p.timer = 0;
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, CAUGHT_TICKS + 20, 255, false, false));
        ModNetwork.sendFx(player, ScreenFxPacket.Type.SCREAMER, SCREAMER_TICKS);
        BrokenWorld.LOGGER.info("[BrokenWorld] finale: {} was caught after {} ticks of chase",
                player.getName().getString(), phaseTicks);
    }

    // ------------------------------------------------------------------ 3. the maze

    private static void sendToMaze(ServerPlayer player, Participant p) {
        ServerLevel tunnel = (ServerLevel) player.level();
        BlockPos s = maze.start();
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        player.teleportTo(tunnel, s.getX() + 0.5, s.getY(), s.getZ() + 0.5, -45.0F, 0.0F);
        player.fallDistance = 0;
        p.state = State.MAZE;
        ModNetwork.sendFx(player, ScreenFxPacket.Type.BLACK_OFF, 60);
        player.displayClientMessage(Component.translatable("finale.brokenworld.maze"), true);
    }

    private static void tickMaze(MinecraftServer server, ServerLevel tunnel) {
        placeNotes(server, tunnel);
        // Notes thrown into the pit vanish.
        for (ItemEntity item : tunnel.getEntitiesOfClass(ItemEntity.class, maze.pit())) {
            if (!item.getItem().is(ModItems.NOTE.get())) continue;
            BURNED.add(NoteItem.index(item.getItem()));
            item.discard();
            Vec3 c = Vec3.atCenterOf(maze.pitTop());
            tunnel.playSound(null, c.x, c.y, c.z, SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 2.0F, 0.5F);
            tunnel.sendParticles(ParticleTypes.SOUL, c.x, c.y, c.z, 30, 0.6, 0.4, 0.6, 0.02);
            forEachPlayer(server, pl -> pl.displayClientMessage(
                    Component.translatable("finale.brokenworld.burned", BURNED.size(), NoteItem.COUNT), true));
        }
        // Whoever falls into the pit climbs back out.
        for (ServerPlayer player : tunnel.players()) {
            if (isParticipant(player) && maze.pit().contains(player.position()) && player.getY() < maze.pitTop().getY() - 0.5) {
                Vec3 edge = Vec3.atBottomCenterOf(maze.pitTop().offset(2, 1, 0));
                player.teleportTo(edge.x, edge.y, edge.z);
            }
        }
        tickGlimpse(tunnel);
        if (BURNED.size() >= NoteItem.COUNT) beginHall(server, tunnel);
    }

    /**
     * Each note appears in its dead end once a player is near and the entities of its chunk are loaded - an item
     * added to a chunk whose entities are not loaded yet can get lost when they load from disk. A note that went missing (not burned, not carried,
     * not lying anywhere loaded) appears again in its place, so the maze can always be finished.
     */
    private static void placeNotes(MinecraftServer server, ServerLevel tunnel) {
        if (phaseTicks % 10 != 0) return;
        for (int i = 0; i < maze.notes().size(); i++) {
            if (BURNED.contains(i)) continue;
            if (noteIds[i] != null) {
                net.minecraft.world.entity.Entity e = tunnel.getEntity(noteIds[i]);
                if (e != null && e.isAlive()) continue;
            }
            ItemStack note = NoteItem.create(ModItems.NOTE.get(), i);
            boolean carried = false;
            for (UUID id : PARTICIPANTS.keySet()) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null && (p.getInventory().contains(note)
                        || ItemStack.isSameItemSameTags(p.containerMenu.getCarried(), note))) {
                    carried = true;
                }
            }
            BlockPos spot = maze.notes().get(i);
            // Only once the entities of that chunk are loaded: adding one before that can lose it.
            boolean loaded = tunnel.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(spot));
            if (carried || !loaded || !anyWithin(tunnel, Vec3.atCenterOf(spot), 48)) continue;
            ItemEntity entity = new ItemEntity(tunnel, spot.getX() + 0.5, spot.getY() + 0.1, spot.getZ() + 0.5, note);
            entity.setDeltaMovement(Vec3.ZERO);
            entity.setUnlimitedLifetime();
            tunnel.addFreshEntity(entity);
            noteIds[i] = entity.getUUID();
        }
    }

    /** Now and then it stands at the end of a corridor, and is gone when you come closer. */
    private static void tickGlimpse(ServerLevel tunnel) {
        if (glimpse != null && !glimpse.isRemoved()) {
            if (anyWithin(tunnel, glimpse.position(), 7) || glimpse.tickCount > 20 * 12) glimpse.vanish(false);
            return;
        }
        if (--nextGlimpse > 0 || tunnel.players().isEmpty()) return;
        nextGlimpse = 20 * (25 + RANDOM.nextInt(25));
        ServerPlayer target = tunnel.players().get(RANDOM.nextInt(tunnel.players().size()));
        int cx = Mth.clamp((target.getBlockX() - maze.origin().getX()) / 4 + RANDOM.nextInt(7) - 3, 0, FinaleAreas.MAZE_CELLS - 1);
        int cz = Mth.clamp((target.getBlockZ() - maze.origin().getZ()) / 4 + RANDOM.nextInt(7) - 3, 0, FinaleAreas.MAZE_CELLS - 1);
        BlockPos p = maze.cell(cx, cz);
        if (p.distSqr(target.blockPosition()) < 8 * 8) return;
        boolean hang = RANDOM.nextInt(3) == 0;
        glimpse = spawnMonster(tunnel, Vec3.atBottomCenterOf(p).add(0, hang ? 4 - 2.55 : 0, 0), 0.0F, false);
        if (hang) {
            glimpse.setStance(SilhouetteEntity.Stance.HANG);
            glimpse.setNoGravity(true);
        } else if (RANDOM.nextBoolean()) {
            glimpse.setStance(SilhouetteEntity.Stance.CRAWL);
        }
    }

    // ------------------------------------------------------------------ 4. the hall

    private static void beginHall(MinecraftServer server, ServerLevel tunnel) {
        if (glimpse != null) glimpse.discard();
        glimpse = null;
        setPhase(Phase.HALL_WAIT);
        int i = 0;
        for (Map.Entry<UUID, Participant> entry : PARTICIPANTS.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;
            BlockPos s = hall.start();
            player.teleportTo(tunnel, s.getX() + 0.5 + (i / 3), s.getY(), s.getZ() + (i % 3) - 1 + 0.5, -90.0F, 0.0F);
            player.fallDistance = 0;
            freeze(player, entry.getValue());
            entry.getValue().state = State.HALL;
            ModNetwork.sendFx(player, ScreenFxPacket.Type.BLACK_ON, 0);
            ModNetwork.sendFx(player, ScreenFxPacket.Type.BLACK_OFF, 50);
            i++;
        }
        // Far away, in the middle, it stands behind the lectern, reading.
        monsterPos = Vec3.atBottomCenterOf(hall.monster());
        chaser = spawnMonster(tunnel, monsterPos, 90.0F, true);
        chaser.setFacing(90.0F, READING_PITCH);
    }

    /**
     * Players cannot move in the hall - they can only look around. The effects stop walking and jumping;
     * {@link #holdInPlace} catches everything else (a sprint-jump still pushes you forward).
     */
    private static void freeze(ServerPlayer player, Participant p) {
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 600, 255, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.JUMP, 20 * 600, 200, false, false)); // wraps negative
        p.anchor = player.position();
    }

    private static final java.util.Set<RelativeMovement> KEEP_LOOK = EnumSet.of(RelativeMovement.X_ROT, RelativeMovement.Y_ROT);

    /** Puts anyone who moved off their spot straight back, without touching where they are looking. */
    private static void holdInPlace(MinecraftServer server) {
        for (Map.Entry<UUID, Participant> entry : PARTICIPANTS.entrySet()) {
            Participant p = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (p.state != State.HALL || p.anchor == null || player == null) continue;
            Vec3 pos = player.position();
            if (Math.abs(pos.x - p.anchor.x) > 0.05 || Math.abs(pos.z - p.anchor.z) > 0.05 || pos.y - p.anchor.y > 0.05) {
                player.connection.teleport(p.anchor.x, p.anchor.y, p.anchor.z, 0.0F, 0.0F, KEEP_LOOK);
                player.setDeltaMovement(Vec3.ZERO);
            }
        }
    }

    private static void unfreeze(ServerPlayer player) {
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        player.removeEffect(MobEffects.JUMP);
    }

    private static void tickHallWait(MinecraftServer server, ServerLevel tunnel) {
        holdInPlace(server);
        if (chaser == null || chaser.isRemoved()) chaser = spawnMonster(tunnel, monsterPos, 90.0F, true);
        chaser.setPos(monsterPos.x, monsterPos.y, monsterPos.z);
        Vec3 c = Vec3.atCenterOf(hall.lectern());

        if (phaseTicks < HALL_WAIT) {
            // reading: head down, swaying slightly; now and then a page turns
            chaser.setFacing(90.0F, READING_PITCH + Mth.sin(phaseTicks * 0.05F) * 4.0F);
            if (phaseTicks % 70 == 35) {
                tunnel.playSound(null, c.x, c.y, c.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.HOSTILE, 12.0F, 0.7F);
            }
            return;
        }

        // The lectern bursts into a storm of particles and is gone - and it grows to twice its size.
        int t = phaseTicks - HALL_WAIT;
        if (t < GROW_TICKS) {
            burst(tunnel, ParticleTypes.SOUL_FIRE_FLAME, c, 60, 1.2, 1.5, 0.08);
            burst(tunnel, ParticleTypes.LARGE_SMOKE, c, 50, 1.5, 2.0, 0.06);
            burst(tunnel, ParticleTypes.REVERSE_PORTAL, c, 120, 2.0, 2.5, 0.4);
            burst(tunnel, ParticleTypes.SQUID_INK, c, 30, 1.0, 1.5, 0.08);
            burst(tunnel, ParticleTypes.SOUL, monsterPos.add(0, 2, 0), 25, 1.0, 2.0, 0.05);
            burst(tunnel, ParticleTypes.ASH, c, 200, 5.0, 3.5, 0.02);
        }
        if (t == 0) {
            tunnel.playSound(null, c.x, c.y, c.z, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 12.0F, 0.6F);
            tunnel.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 12.0F, 0.5F);
        }
        if (t == 12) {
            tunnel.setBlock(hall.lectern(), Blocks.AIR.defaultBlockState(), 3);
            burst(tunnel, ParticleTypes.EXPLOSION_EMITTER, c, 3, 0.5, 0.5, 0.0);
            burst(tunnel, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, c, 40, 1.5, 0.5, 0.05);
            tunnel.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 10.0F, 0.5F);
            tunnel.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 12.0F, 0.5F);
        }
        float grow = Mth.clamp((t - 12) / (float) (GROW_TICKS - 12), 0.0F, 1.0F);
        grow = grow * grow * (3 - 2 * grow);
        chaser.setGrowth(1.0F + (GROWN - 1.0F) * grow);
        chaser.setFacing(90.0F, READING_PITCH * (1.0F - grow)); // looks up at them
        if (t >= GROW_TICKS + 10) {
            setPhase(Phase.HALL_CHASE);
            walked = 0;
            chaser.setJawOpen(true);
        }
    }

    /** Grown, it walks straight down the hall toward them: slowly, then faster and faster. */
    private static void tickHallChase(MinecraftServer server, ServerLevel tunnel) {
        holdInPlace(server);
        if (chaser == null || chaser.isRemoved()) {
            chaser = spawnMonster(tunnel, monsterPos, 90.0F, true);
            chaser.setGrowth(GROWN);
        }
        double speed = Math.min(0.6, 0.02 + phaseTicks * 0.0005);
        monsterPos = monsterPos.add(-speed, 0, 0);
        chaser.setPos(monsterPos.x, monsterPos.y, monsterPos.z);
        chaser.setFacing(90.0F, 0.0F);
        walked += speed;
        if (walked >= 3.6) { // twice the size, twice the stride
            walked = 0;
            tunnel.playSound(null, monsterPos.x, monsterPos.y, monsterPos.z, SoundEvents.WARDEN_STEP, SoundSource.HOSTILE,
                    4.0F + (float) speed * 8.0F, 0.4F);
        }
        ServerPlayer reached = null;
        for (UUID id : PARTICIPANTS.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && p.level() == tunnel && p.getX() >= monsterPos.x - 1.8) reached = p;
        }
        if (reached != null || monsterPos.x < hall.start().getX() - 3) {
            setPhase(Phase.ENDING);
            forEachPlayer(server, pl -> ModNetwork.sendFx(pl, ScreenFxPacket.Type.FINAL_SCREAMER, SCREAMER_TICKS));
            BrokenWorld.LOGGER.info("[BrokenWorld] finale: it reached {}", reached == null ? "the end of the hall" : reached.getName().getString());
        }
    }

    /** Particles everyone in the hall can see, even 150 blocks away (normal ones only reach 32 blocks). */
    private static void burst(ServerLevel level, net.minecraft.core.particles.ParticleOptions particle, Vec3 pos,
                              int count, double spread, double height, double speed) {
        for (ServerPlayer player : level.players()) {
            level.sendParticles(player, particle, true, pos.x, pos.y, pos.z, count, spread, height, spread, speed);
        }
    }

    // ------------------------------------------------------------------ 5. the end

    private static void tickEnding(MinecraftServer server) {
        if (phaseTicks < ENDING_TICKS) return;
        boolean delete = Config.DELETE_WORLD_AT_END.get();
        String levelId = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString();
        discardMonsters();
        for (UUID id : new HashSet<>(PARTICIPANTS.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) continue;
            restoreMode(player);
            unfreeze(player);
            if (!server.isDedicatedServer() && server.isSingleplayerOwner(player.getGameProfile())) {
                // The host's game leaves the world (and deletes it, on the client, once it is closed).
                ModNetwork.sendEndWorld(player, delete, levelId);
            } else {
                player.connection.disconnect(Component.translatable("finale.brokenworld.kicked"));
            }
        }
        reset();
        BrokenWorldState.get(server).setStage(0, RANDOM); // in case the world is kept
        BrokenWorld.LOGGER.info("[BrokenWorld] finale over, world {} {}", levelId, delete ? "will be deleted" : "is closed");
    }

    // ------------------------------------------------------------------ helpers

    private static SilhouetteEntity spawnMonster(ServerLevel level, Vec3 pos, float yaw, boolean scripted) {
        SilhouetteEntity e = ModEntities.SILHOUETTE.get().create(level);
        if (e == null) throw new IllegalStateException("cannot create the silhouette");
        e.moveTo(pos.x, pos.y, pos.z, yaw, 0.0F);
        if (scripted) {
            e.setScripted(true);
            e.setFacing(yaw);
        } else {
            e.setStay(true);
        }
        level.addFreshEntity(e);
        return e;
    }

    private static void discardMonsters() {
        for (SilhouetteEntity e : new SilhouetteEntity[]{lure, chaser, glimpse}) {
            if (e != null && !e.isRemoved()) e.discard();
        }
    }

    private static void footstep(ServerLevel level, Vec3 pos, double speed) {
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.WARDEN_STEP, SoundSource.HOSTILE,
                2.0F + (float) speed * 5.0F, 0.55F);
    }

    private static float yawTowards(Vec3 from, Vec3 to) {
        return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * (180.0 / Math.PI)) - 90.0F;
    }

    private static boolean anyWithin(ServerLevel level, Vec3 pos, double dist) {
        for (ServerPlayer player : level.players()) {
            if (isParticipant(player) && player.position().distanceTo(pos) < dist) return true;
        }
        return false;
    }

    private static void forEachPlayer(MinecraftServer server, java.util.function.Consumer<ServerPlayer> action) {
        for (UUID id : PARTICIPANTS.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) action.accept(p);
        }
    }

    /** X of the furthest (or the last) participant in the tunnel. */
    private static double furthest(MinecraftServer server, ServerLevel tunnel, boolean max) {
        double result = max ? START_X : Double.MAX_VALUE;
        for (UUID id : PARTICIPANTS.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null || player.level() != tunnel || Math.abs(player.getZ() - tunnelZ) > 4) continue;
            result = max ? Math.max(result, player.getX()) : Math.min(result, player.getX());
        }
        return result == Double.MAX_VALUE ? START_X : result;
    }

    private static void buildTunnel(ServerLevel level, int toX) {
        if (toX <= builtTo) return;
        for (int x = builtTo + 1; x <= toX; x++) FinaleAreas.tunnelSlice(level, x, FLOOR_Y, tunnelZ, x == START_X - 1);
        builtTo = toX;
    }

    /** Puts the player back at their bed (or world spawn), restores their game mode and fades the screen in. */
    public static void returnHome(ServerPlayer player) {
        MinecraftServer server = player.server;
        ServerLevel home = server.getLevel(player.getRespawnDimension());
        BlockPos bed = player.getRespawnPosition();
        Optional<Vec3> spot = home != null && bed != null
                ? Player.findRespawnPositionAndUseSpawnBlock(home, bed, player.getRespawnAngle(), player.isRespawnForced(), true)
                : Optional.empty();
        if (spot.isEmpty()) {
            home = server.overworld();
            BlockPos spawn = home.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, home.getSharedSpawnPos());
            spot = Optional.of(Vec3.atBottomCenterOf(spawn));
        }
        restoreMode(player);
        player.removeEffect(MobEffects.DARKNESS);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        player.removeEffect(MobEffects.JUMP);
        player.removeEffect(MobEffects.BLINDNESS);
        Vec3 v = spot.get();
        player.teleportTo(home, v.x, v.y, v.z, player.getYRot(), 0.0F);
        player.fallDistance = 0;
        ModNetwork.sendFx(player, ScreenFxPacket.Type.BLACK_OFF, 80);
    }

    /**
     * Dying during the finale: in the tunnel you are out (back home); in the maze or the hall there is no way out,
     * you come back where you were.
     */
    public static void onRespawn(ServerPlayer player) {
        Participant p = PARTICIPANTS.get(player.getUUID());
        if (p == null) return;
        MinecraftServer server = player.server;
        ServerLevel tunnel = server.getLevel(TUNNEL);
        if (tunnel != null && p.state == State.MAZE && maze != null) {
            BlockPos s = maze.start();
            player.setGameMode(GameType.ADVENTURE);
            player.teleportTo(tunnel, s.getX() + 0.5, s.getY(), s.getZ() + 0.5, -45.0F, 0.0F);
        } else if (tunnel != null && p.state == State.HALL && hall != null) {
            BlockPos s = hall.start();
            player.setGameMode(GameType.ADVENTURE);
            player.teleportTo(tunnel, s.getX() + 0.5, s.getY(), s.getZ() + 0.5, -90.0F, 0.0F);
            freeze(player, p);
        } else {
            PARTICIPANTS.remove(player.getUUID());
            restoreMode(player);
        }
        ModNetwork.sendFx(player, ScreenFxPacket.Type.BLACK_OFF, 20);
    }

    /** Someone logging in inside the tunnel dimension without a running finale (e.g. after a server restart). */
    public static void onLogin(ServerPlayer player) {
        if (player.level().dimension() == TUNNEL && !isParticipant(player) && !House.isInside(player)) {
            returnHome(player);
        }
    }

    private static void rememberMode(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(PREVIOUS_MODE_TAG)) {
            data.putString(PREVIOUS_MODE_TAG, player.gameMode.getGameModeForPlayer().getName());
        }
    }

    private static void restoreMode(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (data.contains(PREVIOUS_MODE_TAG)) {
            player.setGameMode(GameType.byName(data.getString(PREVIOUS_MODE_TAG), GameType.SURVIVAL));
            data.remove(PREVIOUS_MODE_TAG);
        }
    }
}
