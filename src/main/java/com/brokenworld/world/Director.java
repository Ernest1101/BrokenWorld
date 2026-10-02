package com.brokenworld.world;

import com.brokenworld.BrokenWorld;
import com.brokenworld.Config;
import com.brokenworld.entity.SilhouetteEntity;
import com.brokenworld.entity.SilhouetteEntity.Mode;
import com.brokenworld.network.ModNetwork;
import com.brokenworld.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Decides when and where the silhouette shows up and when events happen, and keeps clients'
 * texture corruption in sync. Only runs in the overworld.
 */
public final class Director {
    private static final Map<UUID, PlayerTimers> TIMERS = new HashMap<>();
    /** WINDOW: how high above its feet its face is (leaning on the glass) - the face goes to the middle of the glass. */
    private static final double WINDOW_FACE_HEIGHT = 2.15;
    /** WINDOW: how far in front of its feet its face and palms are (the "window" pose, measured in Blender). */
    public static final double WINDOW_FACE_FORWARD = 0.87;
    private static final RandomSource RANDOM = RandomSource.create();
    private static float lastSentCorruption = -1F;
    private static long lastDayTime = -1;
    /** Mobs without faces: whether an episode is on, and when it starts / ends. */
    private static boolean faceless;
    private static long facelessSwitch = -1;

    private static final class PlayerTimers {
        long nextAppearance;
        long nextEvent;
        long nextScreamer;
        long nextGlitch;
        long nextChat;
        long nextVisit;
        long nextCrash;
        @Nullable SilhouetteEntity active;
    }

    private Director() {}

    public static void reset() {
        TIMERS.clear();
        Scheduler.clear();
        lastSentCorruption = -1F;
        lastDayTime = -1;
        faceless = false;
        facelessSwitch = -1;
        falseDay = -1;
        FakeChat.reset();
        FakeCrash.reset();
        Finale.reset();
        House.reset();
    }

    /** Per-world salt so every world gets its own texture mix. */
    public static long salt(MinecraftServer server) {
        long seed = server.getWorldData().worldGenOptions().seed();
        return seed * 0x9E3779B97F4A7C15L ^ 0x42524F4B454EL;
    }

    public static void syncTo(ServerPlayer player) {
        ModNetwork.sendCorruption(player, BrokenWorldState.get(player.server).getCorruption(), salt(player.server));
        syncFaceless(player);
    }

    public static void tick(MinecraftServer server) {
        Scheduler.tick();
        ServerLevel level = server.getLevel(Level.OVERWORLD);
        if (level == null) return;

        BrokenWorldState state = BrokenWorldState.get(server);
        // Time passes with the overworld clock (so sleeping counts); if the clock is stopped, with real ticks.
        tickSunsetGlitch(level, state);
        long dayTime = level.getDayTime();
        long delta = lastDayTime < 0 ? 1 : Math.max(1, Math.min(24000, dayTime - lastDayTime));
        lastDayTime = dayTime;
        state.tick(RANDOM, delta);
        int stage = state.getStage();
        long now = level.getGameTime();

        float corruption = state.getCorruption();
        if (corruption != lastSentCorruption) {
            lastSentCorruption = corruption;
            ModNetwork.broadcastCorruption(server, corruption, salt(server));
        }

        if (state.consumeStageChange()) onStageChanged(level, now);
        House.tick(server, stage);
        if (stage == BrokenWorldState.FINALE_STAGE) {
            // The silhouette no longer appears; the finale takes over.
            if (!Finale.active()) {
                setFaceless(server, false);
                Finale.start(server);
            }
            Finale.tick(server);
            return;
        }
        if (stage == 0) return; // (faceless episodes only start from stage 1; the command can still force one)

        tickFaceless(server, stage, now);
        if (now % 1200 == 0) PlayerLikeAnimals.tick(level, stage);

        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || !player.isAlive()) continue;
            PlayerTimers t = TIMERS.computeIfAbsent(player.getUUID(), id -> newTimers(now, stage));

            if (t.active != null && t.active.isRemoved()) t.active = null;

            if (t.active == null && now >= t.nextAppearance) {
                if (spawn(level, player, pickMode(level, player, stage), stage) != null) {
                    t.nextAppearance = now + scaled(interval(stage, 3600, 7200, 2400, 4800, 1200, 3000));
                } else {
                    t.nextAppearance = now + 200; // nowhere to stand; try again in 10 s
                }
            }

            if (stage >= 2 && Config.WORLD_EVENTS.get() && now >= t.nextEvent) {
                runRandomEvent(level, player);
                t.nextEvent = now + scaled(interval(stage, 0, 0, 2400, 6000, 1200, 3600));
            }

            // Somebody else "joins" and writes in chat.
            if (Config.FAKE_CHAT.get() && now >= t.nextChat) {
                FakeChat.visit(player, stage);
                t.nextChat = now + scaled(interval(stage, 24000, 42000, 12000, 24000, 7200, 14400));
            }

            // Screamers in normal survival: at night or in the dark, out of nowhere.
            if (stage >= 2 && Config.JUMPSCARES.get() && now >= t.nextScreamer) {
                boolean dark = level.isNight() || level.getMaxLocalRawBrightness(player.blockPosition()) < 6;
                if (dark) {
                    screamer(player);
                    t.nextScreamer = now + scaled(interval(stage, 0, 0, 18000, 30000, 8400, 16800));
                } else {
                    t.nextScreamer = now + 600;
                }
            }

            // Somebody was in the house while you were away.
            if (stage >= 2 && Config.HOME_VISITS.get() && now >= t.nextVisit) {
                BlockPos bed = HomeVisit.bed(level, player);
                double away = bed == null ? 0 : Math.sqrt(bed.distSqr(player.blockPosition()));
                if (bed != null && away > 40 && away < 160 && level.isAreaLoaded(bed, 10)) {
                    HomeVisit.visit(level, player, bed, RANDOM, 2 + RANDOM.nextInt(2));
                    t.nextVisit = now + scaled(interval(stage, 0, 0, 12000, 24000, 7200, 14400));
                } else {
                    t.nextVisit = now + 400; // not away from home (or no home): later
                }
            }

            // Once per stage the game "loses its connection" - preferably at home or in the dark.
            if (stage >= 2 && Config.FAKE_CRASH.get() && now >= t.nextCrash) {
                BrokenWorldState st = BrokenWorldState.get(level.getServer());
                boolean fitting = level.isNight() || !level.canSeeSky(player.blockPosition());
                if (st.getFakeCrashes() < stage - 1 && fitting && !player.isCreative()) {
                    FakeCrash.start(player, RANDOM);
                    st.addFakeCrash();
                }
                t.nextCrash = now + (fitting ? scaled(interval(stage, 0, 0, 6000, 12000, 3600, 7200)) : 600);
            }

            // The world itself coming apart: holes to the bottom of the world, pieces of ground in the air.
            if (stage >= 2 && Config.WORLD_EVENTS.get() && now >= t.nextGlitch) {
                WorldGlitches.Type type = RANDOM.nextInt(3) == 0 ? WorldGlitches.Type.HOLE : WorldGlitches.Type.FLOATING;
                boolean done = WorldGlitches.run(type, level, player, RANDOM, stage);
                t.nextGlitch = now + (done ? scaled(interval(stage, 0, 0, 2400, 4800, 1200, 2400)) : 400);
            }
        }
    }

    /** Ticks left of the broken evening at the first sunset, or -1. */
    private static int falseDay = -1;
    /** Ticks until day and night switch again. */
    private static int nextFlip;
    private static boolean showingDay;

    /**
     * The first sign, on the first evening (while the world is still normal): as the sun touches the horizon, day and
     * night start flickering - morning, night, morning, night, at uneven quick intervals - for ten seconds, and then it
     * stays night. Once per world, no sound, no message. The jumps do not count as time passing for the stages.
     */
    private static void tickSunsetGlitch(ServerLevel level, BrokenWorldState state) {
        if (falseDay > 0) {
            if (--falseDay == 0) {
                setTime(level, 14000L); // and now it is night
                falseDay = -1;
            } else if (--nextFlip <= 0) {
                showingDay = !showingDay;
                setTime(level, showingDay ? 1000L + RANDOM.nextInt(4000) : 14000L + RANDOM.nextInt(6000));
                nextFlip = 2 + RANDOM.nextInt(showingDay ? 8 : 12);
            }
            return;
        }
        if (state.isSunsetGlitchDone() || state.getStage() != 0
                || !level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DAYLIGHT)) return;
        long time = level.getDayTime() % 24000L;
        if (time >= 12200L && time < 12800L) { // the sun is touching the horizon
            sunsetGlitchNow(level);
            state.setSunsetGlitchDone();
            BrokenWorld.LOGGER.info("[BrokenWorld] the first sunset goes wrong");
        }
    }

    /** Starts the flickering now (also for the command). */
    public static void sunsetGlitchNow(ServerLevel level) {
        showingDay = true;
        setTime(level, 1000L); // morning, suddenly
        nextFlip = 6 + RANDOM.nextInt(10);
        falseDay = 200; // ten seconds
    }

    /** Sets the time of day today and tells every player in the overworld right away (normally once a second). */
    private static void setTime(ServerLevel level, long timeOfDay) {
        long day = level.getDayTime() / 24000L;
        level.setDayTime(day * 24000L + timeOfDay);
        lastDayTime = level.getDayTime();
        boolean cycle = level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DAYLIGHT);
        level.getServer().getPlayerList().broadcastAll(new net.minecraft.network.protocol.game.ClientboundSetTimePacket(
                level.getGameTime(), level.getDayTime(), cycle), level.dimension());
    }

    public static boolean isFalseDay() {
        return falseDay > 0;
    }

    /** Its face and the hit, then the picture comes back. */
    public static void screamer(ServerPlayer player) {
        ModNetwork.sendFx(player, ModNetwork.ScreenFxPacket.Type.SCREAMER, 30);
        Scheduler.schedule(30 + 30, () -> ModNetwork.sendFx(player, ModNetwork.ScreenFxPacket.Type.BLACK_OFF, 40));
    }

    /** Mobs without faces now and then - more often and longer as the world breaks. */
    private static void tickFaceless(MinecraftServer server, int stage, long now) {
        if (facelessSwitch < 0) facelessSwitch = now + scaled(interval(stage, 9600, 16800, 6000, 10800, 3600, 7200));
        if (now < facelessSwitch) return;
        if (faceless) {
            setFaceless(server, false);
            facelessSwitch = now + scaled(interval(stage, 9600, 16800, 6000, 10800, 3600, 7200));
        } else {
            setFaceless(server, true);
            facelessSwitch = now + interval(stage, 800, 1400, 1200, 1800, 1200, 2400);
        }
    }

    public static void setFaceless(MinecraftServer server, boolean on) {
        if (faceless == on) return;
        faceless = on;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) syncFaceless(p);
    }

    public static boolean isFaceless() {
        return faceless;
    }

    private static void syncFaceless(ServerPlayer player) {
        ModNetwork.sendFx(player, faceless ? ModNetwork.ScreenFxPacket.Type.FACELESS_ON
                : ModNetwork.ScreenFxPacket.Type.FACELESS_OFF, 0);
    }

    private static PlayerTimers newTimers(long now, int stage) {
        PlayerTimers t = new PlayerTimers();
        t.nextAppearance = now + scaled(interval(stage, 600, 2400, 600, 1800, 300, 1200));
        t.nextEvent = now + scaled(interval(stage, 0, 0, 1200, 3600, 600, 2400));
        t.nextScreamer = now + scaled(interval(stage, 0, 0, 12000, 24000, 6000, 12000));
        t.nextGlitch = now + scaled(interval(stage, 0, 0, 1200, 3600, 600, 1800));
        t.nextChat = now + scaled(interval(stage, 6000, 18000, 4800, 12000, 2400, 7200));
        t.nextVisit = now + scaled(interval(stage, 0, 0, 6000, 12000, 3600, 7200));
        t.nextCrash = now + scaled(interval(stage, 0, 0, 7200, 14400, 3600, 9600));
        return t;
    }

    /** A new stage began (textures just got more mixed up): it shows up soon after. */
    private static void onStageChanged(ServerLevel level, long now) {
        for (ServerPlayer player : level.players()) {
            PlayerTimers t = TIMERS.computeIfAbsent(player.getUUID(), id -> new PlayerTimers());
            t.nextAppearance = now + 600 + RANDOM.nextInt(1200);
            t.nextEvent = now + 1200 + RANDOM.nextInt(2400);
            t.nextScreamer = now + 6000 + RANDOM.nextInt(12000);
            t.nextGlitch = now + 600 + RANDOM.nextInt(1800);
            t.nextChat = now + 2400 + RANDOM.nextInt(6000);
            t.nextVisit = now + 3600 + RANDOM.nextInt(6000);
            t.nextCrash = now + 4800 + RANDOM.nextInt(9600);
        }
    }

    /** Picks a behaviour that fits the stage and where the player is. */
    public static Mode pickMode(ServerLevel level, ServerPlayer player, int stage) {
        boolean underground = !level.canSeeSky(player.blockPosition()) && player.getY() < level.getSeaLevel() - 6;
        boolean night = level.isNight();
        BlockPos bed = player.getRespawnPosition();
        boolean nearHome = bed != null && bed.distSqr(player.blockPosition()) < 28 * 28;

        List<Mode> pool = new ArrayList<>();
        if (underground) {
            add(pool, Mode.CAVE, 6);
            if (stage >= 2) add(pool, Mode.FOLLOW, 3);
            if (stage >= 2) add(pool, Mode.CEILING, 3);
            if (stage >= 3) add(pool, Mode.BEHIND, 3);
        } else {
            add(pool, Mode.FAR, stage == 1 ? 4 : 2);
            add(pool, Mode.TREE_SIDE, 4);
            add(pool, Mode.TREE_TOP, stage == 1 ? 2 : 3);
            if (stage >= 2) {
                add(pool, Mode.FOLLOW, 3);
                add(pool, Mode.BEHIND, stage >= 3 ? 3 : 1);
                if (night && nearHome) add(pool, Mode.HOUSE, 6);
                if (nearHome && !level.canSeeSky(player.blockPosition())) {
                    add(pool, Mode.CEILING, 4); // in your house
                    add(pool, Mode.WINDOW, night ? 6 : 4); // ...or at its window
                }
            }
            if (stage >= 3 && Config.JUMPSCARES.get()) add(pool, Mode.RUSH, 1);
        }
        return pool.get(RANDOM.nextInt(pool.size()));
    }

    private static void add(List<Mode> pool, Mode mode, int weight) {
        for (int i = 0; i < weight; i++) pool.add(mode);
    }

    /**
     * Spawns the silhouette for the player. If the preferred mode has no valid spot,
     * falls back to other quiet modes.
     */
    @Nullable
    public static SilhouetteEntity spawn(ServerLevel level, ServerPlayer player, Mode preferred, int stage) {
        Mode[] order = {preferred, Mode.TREE_SIDE, Mode.FAR, Mode.TREE_TOP, Mode.CAVE};
        for (Mode mode : order) {
            BlockPos pos = SpawnFinder.find(mode, level, player, RANDOM);
            if (pos == null) continue;
            SilhouetteEntity e = ModEntities.SILHOUETTE.get().create(level);
            if (e == null) return null;
            // hanging: its feet touch the ceiling block, the rest of it (2.5 blocks) hangs below
            double y = mode == Mode.CEILING ? pos.getY() - 2.55 : pos.getY();
            double x = pos.getX() + 0.5, z = pos.getZ() + 0.5;
            if (mode == Mode.WINDOW) {
                Direction toGlass = SpawnFinder.windowFacing(level, pos);
                if (toGlass == null) continue;
                BlockPos glass = pos.relative(toGlass);
                // face and palms flat on the glass, the face in its middle (a pane stands in the middle of its block,
                // a glass block's outer side is the block's edge); the legs go down into the ground under the window,
                // where nobody inside can see them
                double glassSide = level.getBlockState(glass).is(net.minecraftforge.common.Tags.Blocks.GLASS_PANES) ? 0.94 : 0.5;
                double close = glassSide - WINDOW_FACE_FORWARD;
                x += toGlass.getStepX() * close;
                z += toGlass.getStepZ() * close;
                y = glass.getY() + 0.5 - WINDOW_FACE_HEIGHT;
                e.setWindow(glass, toGlass);
            }
            e.moveTo(x, y, z, 0, 0);
            e.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
            boolean eyes = switch (mode) {
                case HOUSE, RUSH, WINDOW -> true;
                case CAVE -> stage >= 2;
                default -> stage >= 3 || (stage >= 2 && level.isNight());
            };
            e.setup(player, mode, eyes);
            level.addFreshEntity(e);
            PlayerTimers t = TIMERS.computeIfAbsent(player.getUUID(), id -> new PlayerTimers());
            t.active = e;
            return e;
        }
        return null;
    }

    private static void runRandomEvent(ServerLevel level, ServerPlayer player) {
        boolean underground = !level.canSeeSky(player.blockPosition());
        List<WorldEvents.Type> pool = new ArrayList<>();
        pool.add(WorldEvents.Type.FOOTSTEPS);
        pool.add(WorldEvents.Type.FOOTSTEPS);
        pool.add(WorldEvents.Type.TORCH_OUT);
        pool.add(WorldEvents.Type.CAVE_SOUND);
        if (level.isNight()) pool.add(WorldEvents.Type.DOOR);
        pool.add(WorldEvents.Type.DOOR);
        if (underground) {
            pool.add(WorldEvents.Type.MINING);
            pool.add(WorldEvents.Type.MINING);
        }
        for (int i = 0; i < 4 && !pool.isEmpty(); i++) {
            WorldEvents.Type type = pool.remove(RANDOM.nextInt(pool.size()));
            if (WorldEvents.run(type, level, player, RANDOM)) return;
        }
    }

    /** min/max ticks for stage 1, 2, 3. */
    private static long interval(int stage, int min1, int max1, int min2, int max2, int min3, int max3) {
        int min, max;
        switch (stage) {
            case 1 -> { min = min1; max = max1; }
            case 2 -> { min = min2; max = max2; }
            default -> { min = min3; max = max3; }
        }
        return min + (max > min ? RANDOM.nextInt(max - min) : 0);
    }

    private static long scaled(long ticks) {
        return Math.max(40, (long) (ticks / Config.APPEARANCE_FREQUENCY.get()));
    }
}
