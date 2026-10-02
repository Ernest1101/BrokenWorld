package com.brokenworld.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Helpers for "is the player looking at this?" checks. */
public final class Sight {
    /** Roughly the middle of the screen: the player has clearly noticed it. */
    public static final double NOTICE_COS = 0.90;
    /** Anywhere on screen (a bit generous for wide monitors / high FOV). */
    public static final double ON_SCREEN_COS = 0.45;

    private Sight() {}

    /** True if the player is looking roughly at the entity and nothing blocks the view. */
    public static boolean isLookingAt(Player player, Entity entity, double cosThreshold) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        double[] heights = {entity.getBbHeight() * 0.9, entity.getBbHeight() * 0.5, entity.getBbHeight() * 0.15};
        for (double h : heights) {
            Vec3 target = new Vec3(entity.getX(), entity.getY() + h, entity.getZ());
            Vec3 to = target.subtract(eye);
            double dist = to.length();
            if (dist < 0.5) return true;
            // Small targets far away need a slightly tighter cone to count as "noticed".
            if (look.dot(to.scale(1.0 / dist)) > cosThreshold && hasClearView(player, eye, target)) {
                return true;
            }
        }
        return false;
    }

    /**
     * "Has the player noticed it?" The further away it is, the smaller it looks,
     * so the closer to the crosshair it has to be.
     */
    public static boolean isNoticing(Player player, Entity entity) {
        double dist = player.distanceTo(entity);
        double degrees = Math.max(9.0, Math.min(25.0, 300.0 / Math.max(dist, 1.0)));
        return isLookingAt(player, entity, Math.cos(Math.toRadians(degrees)));
    }

    /** True if the position is inside the player's view cone (ignores obstacles). */
    public static boolean isInViewCone(Player player, Vec3 pos, double cosThreshold) {
        Vec3 to = pos.subtract(player.getEyePosition());
        double dist = to.length();
        if (dist < 0.001) return true;
        return player.getViewVector(1.0F).dot(to.scale(1.0 / dist)) > cosThreshold;
    }

    public static boolean isInViewCone(Player player, BlockPos pos, double cosThreshold) {
        return isInViewCone(player, Vec3.atCenterOf(pos), cosThreshold);
    }

    /** Blocks by their visual shape: glass and glass panes do not stop the view (a face at the window is seen). */
    public static boolean hasClearView(Player player, Vec3 from, Vec3 to) {
        HitResult hit = player.level().clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS;
    }

    /** Can the player see this spot at all right now (in cone and unobstructed)? */
    public static boolean canSee(Player player, Vec3 pos) {
        return isInViewCone(player, pos, ON_SCREEN_COS) && hasClearView(player, player.getEyePosition(), pos);
    }
}
