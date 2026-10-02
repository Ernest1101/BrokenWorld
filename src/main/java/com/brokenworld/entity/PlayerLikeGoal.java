package com.brokenworld.entity;

import com.brokenworld.util.Sight;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.EnumSet;

/**
 * An animal that behaves like a player:
 * <ul>
 *   <li>never takes its eyes off you;</li>
 *   <li>stands perfectly still while you look at it, and sprint-jumps closer while you don't;</li>
 *   <li>now and then mines a block next to it (crack animation, hit sounds, the block drops);</li>
 *   <li>now and then pillars up: jumps and puts a dirt block under itself, three times.</li>
 * </ul>
 */
public class PlayerLikeGoal extends Goal {
    private enum Action { NONE, MINE, PILLAR }

    private final PathfinderMob mob;
    @Nullable private Player target;
    private Action action = Action.NONE;
    private int actionTicks;
    private int nextAction;
    @Nullable private BlockPos mining;
    private int pillarsLeft;
    private int repath;
    private boolean direct;
    private double pillarStartY;

    public PlayerLikeGoal(PathfinderMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        this.nextAction = 200 + mob.getRandom().nextInt(400);
    }

    @Override
    public boolean canUse() {
        target = mob.level().getNearestPlayer(mob, 28.0);
        return target != null && !target.isSpectator();
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && target.isAlive() && !target.isSpectator() && mob.distanceToSqr(target) < 32 * 32;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        if (mining != null) mob.level().destroyBlockProgress(mob.getId(), mining, -1);
        action = Action.NONE;
        mining = null;
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (target == null) return;
        mob.getLookControl().setLookAt(target, 60.0F, 60.0F);
        if (action != Action.NONE) {
            tickAction();
            return;
        }
        boolean watched = Sight.isLookingAt(target, mob, Sight.NOTICE_COS);
        double dist = mob.distanceTo(target);
        if (watched) {
            mob.getNavigation().stop(); // freezes like a statue
        } else if (dist > 6.0) {
            // a path every half second; if there is none (or it goes nowhere), straight at you
            if (--repath <= 0) {
                repath = 10;
                direct = !mob.getNavigation().moveTo(target, 1.7) || mob.getNavigation().isDone();
            }
            if (direct) mob.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), 1.7);
            // sprint-jumping: a hop now and then while running, not bouncing on the spot
            if (mob.onGround() && mob.getDeltaMovement().horizontalDistanceSqr() > 0.01 && mob.getRandom().nextInt(12) == 0) {
                mob.getJumpControl().jump();
            }
        } else {
            mob.getNavigation().stop();
        }
        if (--nextAction <= 0 && mob.onGround()) {
            nextAction = 300 + mob.getRandom().nextInt(500);
            startAction(mob.getRandom().nextBoolean() ? Action.MINE : Action.PILLAR);
        }
    }

    private void startAction(Action next) {
        mob.getNavigation().stop();
        if (next == Action.MINE) {
            mining = findBlockToMine();
            if (mining == null) return;
        } else {
            pillarsLeft = 3;
            pillarStartY = mob.getY();
        }
        action = next;
        actionTicks = 0;
    }

    @Nullable
    private BlockPos findBlockToMine() {
        BlockPos feet = mob.blockPosition();
        Direction facing = mob.getDirection();
        BlockPos[] candidates = {feet.relative(facing), feet.relative(facing).above(), feet.below()};
        for (BlockPos p : candidates) {
            BlockState s = mob.level().getBlockState(p);
            if (s.is(BlockTags.DIRT) || s.is(BlockTags.LOGS) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.SAND)) {
                return p;
            }
        }
        return null;
    }

    private void tickAction() {
        actionTicks++;
        ServerLevel level = (ServerLevel) mob.level();
        if (action == Action.MINE && mining != null) {
            BlockState s = level.getBlockState(mining);
            if (s.isAir()) {
                finishAction();
                return;
            }
            mob.getLookControl().setLookAt(mining.getX() + 0.5, mining.getY() + 0.5, mining.getZ() + 0.5);
            level.destroyBlockProgress(mob.getId(), mining, Math.min(9, actionTicks / 4));
            if (actionTicks % 4 == 0) {
                SoundType sound = s.getSoundType();
                level.playSound(null, mining, sound.getHitSound(), SoundSource.BLOCKS, 0.5F, 0.8F);
            }
            if (actionTicks >= 40) {
                level.destroyBlockProgress(mob.getId(), mining, -1);
                level.destroyBlock(mining, true, mob);
                finishAction();
            }
        } else if (action == Action.PILLAR) {
            // jump, and at the top of the jump put a block where the feet were
            if (mob.onGround() && actionTicks % 12 == 1) mob.getJumpControl().jump();
            if (actionTicks % 12 == 7) {
                BlockPos below = BlockPos.containing(mob.getX(), mob.getY() - 0.5, mob.getZ());
                if (mob.getY() > pillarStartY + 0.4 && level.getBlockState(below).canBeReplaced()) {
                    level.setBlock(below, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
                    level.playSound(null, below, SoundType.GRAVEL.getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
                    pillarStartY = below.getY() + 1;
                    if (--pillarsLeft <= 0) finishAction();
                }
            }
            if (actionTicks > 120) finishAction();
        } else {
            finishAction();
        }
    }

    private void finishAction() {
        action = Action.NONE;
        mining = null;
    }
}
