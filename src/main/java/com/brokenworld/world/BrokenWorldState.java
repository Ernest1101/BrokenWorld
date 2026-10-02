package com.brokenworld.world;

import com.brokenworld.BrokenWorld;
import com.brokenworld.Config;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Per-world progress of the "breaking".
 * Stage 0: normal survival. Stage 1: trees change, it watches from afar.
 * Stage 2: it comes closer, follows you, events start. Stage 3: it stops hiding.
 * Stage 4: the finale (see {@link Finale}); afterwards the world starts over at stage 0.
 */
public class BrokenWorldState extends SavedData {
    private static final String NAME = BrokenWorld.MODID + "_state";
    public static final int MAX_STAGE = 4;
    public static final int FINALE_STAGE = 4;
    private static final long DAY = 24000L;

    private int stage;
    private long ticksInStage;
    private long ticksToNextStage = -1;
    /** Set when a stage was just entered, cleared once the director has reacted. */
    private boolean stageJustChanged;
    /** Manual override of the texture corruption (from the command), or -1. */
    private float corruptionOverride = -1F;
    /** How many finales happened in this world; every finale gets a fresh tunnel. */
    private int finaleCount;
    /** The first sunset went wrong already (it happens once per world). */
    private boolean sunsetGlitchDone;
    /** How many times the game has "lost its connection" (once per stage from stage 2). */
    private int fakeCrashes;

    public static BrokenWorldState get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage()
                .computeIfAbsent(BrokenWorldState::load, BrokenWorldState::new, NAME);
    }

    private static BrokenWorldState load(CompoundTag tag) {
        BrokenWorldState state = new BrokenWorldState();
        state.stage = tag.getInt("Stage");
        state.ticksInStage = tag.getLong("TicksInStage");
        state.ticksToNextStage = tag.contains("TicksToNextStage") ? tag.getLong("TicksToNextStage") : -1;
        state.corruptionOverride = tag.contains("CorruptionOverride") ? tag.getFloat("CorruptionOverride") : -1F;
        state.finaleCount = tag.getInt("FinaleCount");
        state.sunsetGlitchDone = tag.getBoolean("SunsetGlitchDone");
        state.fakeCrashes = tag.getInt("FakeCrashes");
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("Stage", stage);
        tag.putLong("TicksInStage", ticksInStage);
        tag.putLong("TicksToNextStage", ticksToNextStage);
        tag.putFloat("CorruptionOverride", corruptionOverride);
        tag.putInt("FinaleCount", finaleCount);
        tag.putBoolean("SunsetGlitchDone", sunsetGlitchDone);
        tag.putInt("FakeCrashes", fakeCrashes);
        return tag;
    }

    /**
     * Called once per server tick with how far the overworld clock moved, so sleeping through
     * the night also counts as time passing.
     */
    public void tick(RandomSource random, long dayTimeDelta) {
        if (ticksToNextStage < 0) {
            ticksToNextStage = rollDuration(stage, random);
            setDirty();
        }
        long before = ticksInStage;
        ticksInStage += dayTimeDelta;
        int next = stage + 1;
        boolean nextAllowed = next < FINALE_STAGE || (next == FINALE_STAGE && Config.FINALE.get());
        if (stage < MAX_STAGE && nextAllowed && ticksInStage >= ticksToNextStage) {
            setStage(next, random);
        }
        if (ticksInStage / 200 != before / 200) setDirty();
    }

    public void setStage(int newStage, RandomSource random) {
        int old = stage;
        stage = Math.max(0, Math.min(MAX_STAGE, newStage));
        ticksInStage = 0;
        ticksToNextStage = rollDuration(stage, random);
        stageJustChanged = stage > old;
        corruptionOverride = -1F;
        setDirty();
        BrokenWorld.LOGGER.info("[BrokenWorld] stage {} -> {}", old, stage);
    }

    private static long rollDuration(int stage, RandomSource random) {
        double days = switch (stage) {
            case 0 -> {
                double min = Config.MIN_DAYS_BEFORE_CHANGE.get();
                double max = Math.max(min, Config.MAX_DAYS_BEFORE_CHANGE.get());
                yield min + random.nextDouble() * (max - min);
            }
            case 1 -> Config.DAYS_TO_STAGE_2.get() * (0.8 + random.nextDouble() * 0.4);
            case 2 -> Config.DAYS_TO_STAGE_3.get() * (0.8 + random.nextDouble() * 0.4);
            case 3 -> Config.DAYS_TO_FINALE.get() * (0.8 + random.nextDouble() * 0.4);
            default -> Double.MAX_VALUE / DAY / 2;
        };
        return (long) (days * DAY);
    }

    /**
     * How mixed-up the block textures are, 0..1, rounded to 0.05 so clients only rebuild
     * chunks occasionally. Grows slowly within every stage.
     */
    public float getCorruption() {
        if (corruptionOverride >= 0F) return corruptionOverride;
        double progress = ticksToNextStage > 0 ? Math.min(1.0, (double) ticksInStage / ticksToNextStage) : 0.0;
        double c = switch (stage) {
            case 0 -> 0.0;
            case 1 -> 0.15 + 0.25 * progress;
            case 2 -> 0.40 + 0.30 * progress;
            case 3 -> 0.70 + 0.30 * progress;
            default -> 1.0;
        };
        return Math.round(c * 20.0) / 20.0F;
    }

    public void setCorruptionOverride(float value) {
        corruptionOverride = value;
        setDirty();
    }

    public int getFakeCrashes() {
        return fakeCrashes;
    }

    public void addFakeCrash() {
        fakeCrashes++;
        setDirty();
    }

    public boolean isSunsetGlitchDone() {
        return sunsetGlitchDone;
    }

    public void setSunsetGlitchDone() {
        sunsetGlitchDone = true;
        setDirty();
    }

    /** Starts a new finale; returns its number (used to place its own tunnel). */
    public int beginFinale() {
        finaleCount++;
        setDirty();
        return finaleCount;
    }

    public int getStage() {
        return stage;
    }

    public long getTicksInStage() {
        return ticksInStage;
    }

    public long getTicksToNextStage() {
        return ticksToNextStage;
    }

    public boolean consumeStageChange() {
        boolean changed = stageJustChanged;
        stageJustChanged = false;
        return changed;
    }
}
