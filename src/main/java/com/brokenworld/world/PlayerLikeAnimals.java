package com.brokenworld.world;

import com.brokenworld.BrokenWorld;
import com.brokenworld.entity.PlayerLikeGoal;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Some cows, pigs, sheep and chickens start behaving like players (see {@link PlayerLikeGoal}). Nothing gives them
 * away but how they move - no name, no marks. More of them as the world breaks: new animals roll a chance once per stage, and near every player a loaded
 * animal is converted now and then, up to a few per player.
 */
public final class PlayerLikeAnimals {
    private static final String TAG = BrokenWorld.MODID + "_playerlike";
    private static final String ROLLED = BrokenWorld.MODID + "_rolled_stage";
    /** Names older versions put over their heads; removed again so nothing gives them away. */
    private static final java.util.Set<String> OLD_NAMES = java.util.Set.of("Player", "Steve", "Alex", "Player29", "you",
            "null", "???", "Player1", "friend", "not_a_cow", "Dev", "player");
    private static final RandomSource RANDOM = RandomSource.create();

    private PlayerLikeAnimals() {}

    public static boolean eligible(Entity e) {
        return e instanceof Cow || e instanceof Pig || e instanceof Sheep || e instanceof Chicken;
    }

    public static boolean isPlayerLike(Entity e) {
        return com.brokenworld.util.ModData.of(e).getBoolean(TAG);
    }

    private static double chance(int stage) {
        return switch (stage) {
            case 1 -> 0.03;
            case 2 -> 0.07;
            default -> stage >= 3 ? 0.12 : 0.0;
        };
    }

    private static int perPlayer(int stage) {
        return Math.min(3, stage);
    }

    /** An animal loaded into the world: give its behaviour back, or maybe make it one of them. */
    public static void onJoin(Entity e, int stage) {
        if (!eligible(e) || !(e instanceof PathfinderMob mob)) return;
        CompoundTag data = com.brokenworld.util.ModData.of(mob);
        if (data.getBoolean(TAG)) {
            if (mob.hasCustomName() && OLD_NAMES.contains(mob.getCustomName().getString())) {
                mob.setCustomName(null);
                mob.setCustomNameVisible(false);
            }
            addGoal(mob);
            return;
        }
        if (stage >= 1 && data.getInt(ROLLED) < stage) {
            data.putInt(ROLLED, stage);
            if (RANDOM.nextDouble() < chance(stage)) convert(mob);
        }
    }

    public static void convert(PathfinderMob mob) {
        com.brokenworld.util.ModData.of(mob).putBoolean(TAG, true);
        mob.setPersistenceRequired();
        addGoal(mob);
    }

    private static void addGoal(PathfinderMob mob) {
        mob.goalSelector.addGoal(0, new PlayerLikeGoal(mob));
    }

    /** Called about once a minute: near each player, maybe one more of them. */
    public static void tick(ServerLevel level, int stage) {
        if (stage < 1) return;
        for (ServerPlayer player : level.players()) {
            List<PathfinderMob> around = level.getEntitiesOfClass(PathfinderMob.class,
                    player.getBoundingBox().inflate(48), PlayerLikeAnimals::eligible);
            long already = around.stream().filter(PlayerLikeAnimals::isPlayerLike).count();
            if (already >= perPlayer(stage) || RANDOM.nextInt(3) != 0) continue;
            List<PathfinderMob> normal = around.stream().filter(m -> !isPlayerLike(m)).toList();
            if (!normal.isEmpty()) convert(normal.get(RANDOM.nextInt(normal.size())));
        }
    }

    /** For the command: the nearest animal that can become one. */
    @Nullable
    public static PathfinderMob nearest(ServerPlayer player) {
        List<PathfinderMob> around = player.serverLevel().getEntitiesOfClass(PathfinderMob.class,
                player.getBoundingBox().inflate(16), m -> eligible(m) && !isPlayerLike(m));
        PathfinderMob best = null;
        for (PathfinderMob m : around) {
            if (best == null || m.distanceToSqr(player) < best.distanceToSqr(player)) best = m;
        }
        return best;
    }
}
