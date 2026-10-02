package com.brokenworld.world;

import com.brokenworld.BrokenWorld;
import com.brokenworld.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The game seems to lose its connection: the player's screen becomes the real "Connection Lost" screen, then
 * "Loading terrain...", and they are back - but while they were "gone", things around them moved: torches stand
 * elsewhere, the doors are open, a sign behind them says to turn around, and they are facing the other way.
 * (In single player the game pauses behind the screen, just like a real disconnect would stop it.)
 */
public final class FakeCrash {
    /** How long the "Connection Lost" screen stays, then "Loading terrain..." for LOADING_TICKS. */
    public static final int SCREEN_TICKS = 100;
    public static final int LOADING_TICKS = 30;
    /** Nothing can hurt them while they cannot play. */
    private static final Map<UUID, Long> PROTECTED_UNTIL = new HashMap<>();

    private FakeCrash() {}

    /** What was changed while the player was "gone" (for the command and the log). */
    public record Result(int torches, int doors, boolean chest, boolean sign) {}

    public static Result start(ServerPlayer player, RandomSource random) {
        ServerLevel level = player.serverLevel();
        BlockPos at = player.blockPosition();
        PROTECTED_UNTIL.put(player.getUUID(), level.getGameTime() + SCREEN_TICKS + LOADING_TICKS + 60);
        // first the screen, so none of it is seen happening
        ModNetwork.sendFx(player, ModNetwork.ScreenFxPacket.Type.FAKE_CRASH, SCREEN_TICKS);

        int torches = HomeVisit.moveTorches(level, null, at, random, 2 + random.nextInt(3));
        int doors = HomeVisit.openDoors(level, at);
        boolean chest = HomeVisit.shuffleChest(level, at, random);
        // "turn around" on a sign right behind them...
        Vec3 look = player.getViewVector(1.0F);
        Vec3 behind = player.position().subtract(look.x * 2.0, 0, look.z * 2.0);
        boolean sign = HomeVisit.sign(level, player, BlockPos.containing(behind), random, "brokenworld.crash.sign",
                player.position()) != null;
        // ...and they come back facing it
        player.teleportTo(level, player.getX(), player.getY(), player.getZ(), player.getYRot() + 180.0F, player.getXRot());
        player.setYHeadRot(player.getYRot());
        Result result = new Result(torches, doors, chest, sign);
        BrokenWorld.LOGGER.info("[BrokenWorld] {}'s game \"lost the connection\": {}", player.getGameProfile().getName(), result);
        return result;
    }

    public static boolean isProtected(Player player) {
        Long until = PROTECTED_UNTIL.get(player.getUUID());
        return until != null && player.level().getGameTime() < until;
    }

    public static void reset() {
        PROTECTED_UNTIL.clear();
    }
}
