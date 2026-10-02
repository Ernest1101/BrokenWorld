package com.brokenworld.entity;

import com.brokenworld.util.Sight;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * The black silhouette. It only exists for one player ("owner"), watches them and disappears
 * when it has been noticed. It never attacks directly.
 */
public class SilhouetteEntity extends PathfinderMob {

    public enum Mode {
        /** Stands far away on the horizon, staring. */
        FAR(24.0, 60, 12, 20 * 90),
        /** Peeks out from behind a tree trunk. */
        TREE_SIDE(7.0, 25, 6, 20 * 75),
        /** Stands on top of a tree canopy, looking down. */
        TREE_TOP(6.0, 40, 10, 20 * 75),
        /** Right behind the player. Gone the instant they turn around. */
        BEHIND(2.0, 2, 0, 20 * 20),
        /** Follows the player while they are not looking, freezes when they are. */
        FOLLOW(7.0, 40, 30, 20 * 120),
        /** Stands deep in a dark cave passage. */
        CAVE(9.0, 40, 8, 20 * 90),
        /** At night, stands outside near the player's bed/home. */
        HOUSE(8.0, 80, 15, 20 * 150),
        /** Stage 3 only: once noticed it runs straight at you. */
        RUSH(2.5, 10000, 10000, 20 * 25),
        /** Hangs upside down from a ceiling (caves, houses), head turning after you. */
        CEILING(5.0, 60, 10, 20 * 90),
        /** Outside a window of the room you are in, face and hands on the glass, tapping it with its claws. */
        WINDOW(2.0, 30, 10, 20 * 120);

        public final double vanishDistance;
        /** How many ticks it tolerates being stared at before vanishing. */
        public final int stareLimit;
        /** How many ticks after being noticed and then un-looked-at it vanishes. */
        public final int lookAwayGrace;
        public final int maxLife;

        Mode(double vanishDistance, int stareLimit, int lookAwayGrace, int maxLife) {
            this.vanishDistance = vanishDistance;
            this.stareLimit = stareLimit;
            this.lookAwayGrace = lookAwayGrace;
            this.maxLife = maxLife;
        }
    }

    private static final EntityDataAccessor<Boolean> GLOWING_EYES =
            SynchedEntityData.defineId(SilhouetteEntity.class, EntityDataSerializers.BOOLEAN);
    /** How much bigger than normal it is drawn (the finale's hall: 1 -> 2). */
    private static final EntityDataAccessor<Float> GROWTH =
            SynchedEntityData.defineId(SilhouetteEntity.class, EntityDataSerializers.FLOAT);
    /** Which pose from the Blender poses it is in (see {@link Stance}). */
    private static final EntityDataAccessor<Byte> STANCE =
            SynchedEntityData.defineId(SilhouetteEntity.class, EntityDataSerializers.BYTE);
    /** Mouth wide open. */
    private static final EntityDataAccessor<Boolean> JAW_OPEN =
            SynchedEntityData.defineId(SilhouetteEntity.class, EntityDataSerializers.BOOLEAN);
    /** Moves in jerks, like a lagging player, with its head twitching. */
    private static final EntityDataAccessor<Boolean> JERKY =
            SynchedEntityData.defineId(SilhouetteEntity.class, EntityDataSerializers.BOOLEAN);

    /** Poses made in Blender (tools/blender_poses.py). NONE = standing. */
    public enum Stance {
        NONE(null), PEEK("peek"), CRAWL("crawl"), HANG("hang"), SPIDER("spider"), WINDOW("window");

        public final String pose;

        Stance(String pose) {
            this.pose = pose;
        }
    }

    @Nullable
    private UUID ownerId;
    private Mode mode = Mode.FAR;
    private int life;
    private int seenTicks;
    private int unseenTicks;
    private boolean noticed;
    private boolean rushing;
    /** For looking at it up close (/brokenworld inspect): never vanishes, only turns to the nearest player. */
    private boolean stay;
    /** Driven entirely from outside (the finale's chaser): no own logic, cannot be hit away. */
    private boolean scripted;
    /** Only the head follows the player; the body keeps facing where it faced at first - past 90°, it looks wrong. */
    private boolean headFree;
    private float fixedBodyYaw = Float.NaN;
    /** /brokenworld freeze: silhouettes put up with inspect / pose stop turning after you (to walk around them). */
    private static boolean lookFrozen;
    private boolean facedOnce;
    /** WINDOW: the glass it presses itself against, and ticks since it started backing away from it (-1: not yet). */
    @Nullable
    private BlockPos windowGlass;
    private int retreat = -1;

    public SilhouetteEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setSilent(true);
        this.setPersistenceRequired();
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.FOLLOW_RANGE, 96.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(GLOWING_EYES, false);
        this.entityData.define(GROWTH, 1.0F);
        this.entityData.define(STANCE, (byte) 0);
        this.entityData.define(JAW_OPEN, false);
        this.entityData.define(JERKY, false);
    }

    public void setup(Player owner, Mode mode, boolean glowingEyes) {
        this.ownerId = owner.getUUID();
        this.mode = mode;
        this.entityData.set(GLOWING_EYES, glowingEyes);
        boolean underground = !this.level().canSeeSky(this.blockPosition());
        switch (mode) {
            case TREE_SIDE -> setStance(Stance.PEEK);
            case CEILING -> {
                setStance(Stance.HANG);
                this.setNoGravity(true);
            }
            case FOLLOW -> {
                // in caves it crawls after you - on its belly, or bent over backwards like a spider
                if (underground) setStance(this.random.nextBoolean() ? Stance.CRAWL : Stance.SPIDER);
                setJerky(true);
            }
            case WINDOW -> {
                setStance(Stance.WINDOW);
                this.setNoGravity(true);
                this.noPhysics = true;
            }
            case RUSH -> setJerky(true);
            default -> {
            }
        }
        headFree = mode == Mode.FAR || mode == Mode.TREE_TOP || mode == Mode.HOUSE || mode == Mode.CEILING
                || mode == Mode.TREE_SIDE || mode == Mode.CAVE || mode == Mode.WINDOW;
        faceOwner(owner);
    }

    /** WINDOW: presses itself against this glass, facing it (call before setup). */
    public void setWindow(BlockPos glass, Direction toGlass) {
        this.windowGlass = glass;
        this.fixedBodyYaw = toGlass.toYRot();
    }

    public void setStance(Stance stance) {
        this.entityData.set(STANCE, (byte) stance.ordinal());
    }

    public Stance getStance() {
        int i = this.entityData.get(STANCE);
        return i >= 0 && i < Stance.values().length ? Stance.values()[i] : Stance.NONE;
    }

    public void setJawOpen(boolean open) {
        this.entityData.set(JAW_OPEN, open);
    }

    public boolean isJawOpen() {
        return this.entityData.get(JAW_OPEN);
    }

    public void setJerky(boolean jerky) {
        this.entityData.set(JERKY, jerky);
    }

    public boolean isJerky() {
        return this.entityData.get(JERKY);
    }

    public void setStay(boolean glowingEyes) {
        this.stay = true;
        this.entityData.set(GLOWING_EYES, glowingEyes);
    }

    public void setScripted(boolean glowingEyes) {
        this.scripted = true;
        this.setNoGravity(true);
        this.noPhysics = true;
        this.entityData.set(GLOWING_EYES, glowingEyes);
    }

    public static void setLookFrozen(boolean frozen) {
        lookFrozen = frozen;
    }

    public static boolean isLookFrozen() {
        return lookFrozen;
    }

    /** Turns it to look along the given yaw (degrees). */
    public void setFacing(float yaw) {
        setFacing(yaw, 0.0F);
    }

    /** Turns it to look along the given yaw, head tilted by pitch (degrees, positive = down). */
    public void setFacing(float yaw, float pitch) {
        this.setYRot(yaw);
        this.setYHeadRot(yaw);
        this.yBodyRot = yaw;
        this.setXRot(pitch);
    }

    public void setGrowth(float growth) {
        this.entityData.set(GROWTH, growth);
    }

    public float getGrowth() {
        return this.entityData.get(GROWTH);
    }

    public boolean hasGlowingEyes() {
        return this.entityData.get(GLOWING_EYES);
    }

    public Mode getMode() {
        return mode;
    }

    @Nullable
    public UUID getOwnerId() {
        return ownerId;
    }

    @Override
    protected void registerGoals() {
        // No goals: everything is driven from tick().
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || scripted) return;

        if (stay) {
            Player nearest = this.level().getNearestPlayer(this, 64.0);
            // frozen: it still turns to you once when it appears, then holds that pose
            if (nearest != null && (!lookFrozen || !facedOnce)) {
                faceOwner(nearest);
                facedOnce = true;
            }
            return;
        }

        life++;
        Player owner = findOwner();
        if (owner == null || !owner.isAlive() || owner.isSpectator() || this.distanceToSqr(owner) > 160 * 160
                || life > mode.maxLife) {
            vanish(false);
            return;
        }

        if (!(mode == Mode.FOLLOW || (mode == Mode.RUSH && rushing))) {
            faceOwner(owner);
        }

        double dist = this.distanceTo(owner);
        boolean looking = Sight.isNoticing(owner, this);

        if (looking) {
            seenTicks++;
            unseenTicks = 0;
            if (seenTicks >= 5) noticed = true;
        } else {
            unseenTicks++;
            if (seenTicks > 0 && seenTicks < 5) seenTicks--; // a quick glance does not count
        }

        switch (mode) {
            case BEHIND -> {
                if (seenTicks >= mode.stareLimit || dist < mode.vanishDistance) {
                    vanish(true);
                    return;
                }
            }
            case FOLLOW -> {
                tickFollow(owner, looking, dist);
                if (!this.isAlive()) return;
            }
            case RUSH -> {
                tickRush(owner, dist);
                return;
            }
            case WINDOW -> {
                tickWindow(looking, dist);
                return;
            }
            default -> {
            }
        }

        if (dist < mode.vanishDistance
                || (looking && seenTicks >= mode.stareLimit)
                || (noticed && !looking && unseenTicks >= mode.lookAwayGrace)) {
            vanish(false);
        }
    }

    /** Taps on the glass until you look; stared at, it slowly steps back from the window into the dark. */
    private void tickWindow(boolean looking, double dist) {
        if (retreat >= 0) {
            Vec3 back = Vec3.directionFromRotation(0.0F, fixedBodyYaw).scale(-0.03);
            this.setPos(this.getX() + back.x, this.getY(), this.getZ() + back.z);
            if (++retreat > 45) vanish(false);
            return;
        }
        if (!noticed && windowGlass != null && (life % 50 == 17 || life % 50 == 22)) {
            // claws on the glass: tap... tap
            this.level().playSound(null, windowGlass, SoundEvents.GLASS_HIT, SoundSource.HOSTILE,
                    0.45F, 0.5F + this.random.nextFloat() * 0.2F);
        }
        if (dist < mode.vanishDistance || (noticed && !looking && unseenTicks >= mode.lookAwayGrace)) {
            vanish(false);
        } else if (looking && seenTicks >= mode.stareLimit) {
            retreat = 0;
        }
    }

    private void tickFollow(Player owner, boolean looking, double dist) {
        boolean visible = looking || (Sight.isInViewCone(owner, this.getEyePosition(), Sight.ON_SCREEN_COS)
                && Sight.hasClearView(owner, owner.getEyePosition(), this.getEyePosition()));
        if (visible) {
            // Frozen like a statue while visible.
            this.getNavigation().stop();
            this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
            faceOwner(owner);
        } else if (dist > 11.0) {
            this.getNavigation().moveTo(owner, 1.15);
        } else {
            this.getNavigation().stop();
            faceOwner(owner);
        }
    }

    private void tickRush(Player owner, double dist) {
        if (!rushing) {
            faceOwner(owner);
            if (noticed) {
                rushing = true;
                setJawOpen(true); // it comes at you with its mouth wide open
                if (this.random.nextBoolean()) setStance(Stance.SPIDER); // ...sometimes bent over backwards, on all fours
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.AMBIENT_CAVE.value(), SoundSource.HOSTILE, 3.0F, 0.5F);
            } else if (dist < 6.0) {
                vanish(false);
            }
            return;
        }
        this.getNavigation().moveTo(owner, 2.6);
        this.getLookControl().setLookAt(owner, 90.0F, 90.0F);
        if (dist < 2.2 || (this.getNavigation().isDone() && dist > 4.0 && life % 20 == 0)) {
            if (dist < 2.2) {
                owner.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false));
                owner.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 25, 0, false, false));
                this.level().playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                        SoundEvents.ENDERMAN_SCREAM, SoundSource.HOSTILE, 1.6F, 0.45F);
            }
            vanish(false);
        }
    }

    @Nullable
    private Player findOwner() {
        if (ownerId == null) {
            // Spawned with an egg or a command: adopt the nearest player.
            Player nearest = this.level().getNearestPlayer(this, 64.0);
            if (nearest != null) ownerId = nearest.getUUID();
            return nearest;
        }
        return this.level().getPlayerByUUID(ownerId);
    }

    private void faceOwner(Entity owner) {
        double dx = owner.getX() - this.getX();
        double dz = owner.getZ() - this.getZ();
        double dy = owner.getEyeY() - this.getEyeY();
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        float pitch = (float) (-(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * (180.0 / Math.PI)));
        float body = yaw;
        if (headFree) {
            // the body stays where it was; only the head keeps turning after you, as far as it takes
            if (Float.isNaN(fixedBodyYaw)) fixedBodyYaw = yaw;
            body = fixedBodyYaw;
        }
        this.setYRot(body);
        this.yRotO = body;
        this.setYHeadRot(yaw);
        this.yHeadRotO = yaw;
        this.yBodyRot = body;
        this.yBodyRotO = body;
        this.setXRot(Mth.clamp(pitch, -60.0F, 60.0F));
    }

    public void vanish(boolean withSound) {
        if (withSound) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 1.2F, 0.6F);
        }
        this.discard();
    }

    // ---- It cannot be killed, pushed, or saved. ----

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (scripted || (stay && !(source.getEntity() instanceof Player))) return false;
        if (!this.level().isClientSide) {
            // Hitting it only makes it disappear.
            vanish(source.getEntity() instanceof Player);
        }
        return false;
    }

    /**
     * Vanilla stops drawing an entity this thin after ~80 blocks; it has to be seen on the horizon
     * and far down the tunnel.
     */
    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        return distanceSqr < 190.0 * 190.0;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void checkDespawn() {
        // Lifetime is managed in tick().
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Mode", mode.name());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        try {
            mode = Mode.valueOf(tag.getString("Mode"));
        } catch (IllegalArgumentException ignored) {
            mode = Mode.FAR;
        }
    }
}
