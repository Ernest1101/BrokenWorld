package com.brokenworld.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Client side of the "broken world": decides which blocks are drawn with another vanilla block's
 * model/textures. Everything is deterministic (position + world salt), so the same spot always
 * looks the same and nothing flickers.
 *
 * <ul>
 *   <li>Trees (logs, leaves) break first — from stage 1.</li>
 *   <li>Everything else (ground, stone, ores, plants...) follows from stage 2.</li>
 *   <li>Past 0.55 corruption, single blocks get different textures on different faces.</li>
 * </ul>
 */
public final class TextureShuffle {
    /** Size of a "broken patch" in blocks. */
    private static final int CELL = 5;

    private static volatile float corruption;
    private static volatile long salt;

    private static List<BlockState> solidPool;
    private static List<BlockState> leavesPool;
    private static List<BlockState> logPool;
    private static List<BlockState> plantPool;

    private TextureShuffle() {}

    public static boolean active() {
        return corruption > 0F;
    }

    public static float corruption() {
        return corruption;
    }

    /** This world's salt (the sounds break with it too). */
    public static long salt() {
        return salt;
    }

    /** Called when the server sends a new corruption value. */
    public static void update(float newCorruption, long newSalt) {
        boolean changed = newCorruption != corruption || newSalt != salt;
        if (newCorruption > 0F) AlphaAssets.prefetch(); // the world is breaking: get the old game ready for later
        corruption = newCorruption;
        salt = newSalt;
        MenuMemory.sawCorruption(newCorruption); // (the main menu remembers)
        if (changed) rebuildVisibleChunks();
    }

    public static void reset() {
        corruption = 0F;
    }

    /** Re-mesh loaded chunk sections. Old meshes stay visible until the new ones are ready. */
    public static void rebuildVisibleChunks() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        int radius = mc.options.getEffectiveRenderDistance() + 1;
        SectionPos center = SectionPos.of(mc.player.blockPosition());
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = mc.level.getMinSection(); y < mc.level.getMaxSection(); y++) {
                    mc.levelRenderer.setSectionDirty(center.x() + x, y, center.z() + z);
                }
            }
        }
    }

    // ------------------------------------------------------------------ decisions

    private static float treeThreshold() {
        return Math.min(0.9F, corruption * 1.3F);
    }

    private static float otherThreshold() {
        return Math.max(0F, corruption - 0.35F) * 0.7F;
    }

    private static boolean perFace() {
        return corruption >= 0.55F;
    }

    /** Is this block at this position drawn wrong at all? */
    public static boolean isShuffled(BlockState state, BlockPos pos) {
        if (!active() || Hallucination.active()) return false;
        Kind kind = kindOf(state);
        if (kind == Kind.NONE) return false;
        float threshold = (kind == Kind.LOG || kind == Kind.LEAVES) ? treeThreshold() : otherThreshold();
        if (threshold <= 0F) return false;
        long cell = cellHash(pos);
        // Mostly decided by the patch, a bit by the single block, so patch edges are ragged.
        double value = unit(cell) * 0.8 + unit(mix(pos.asLong() ^ salt)) * 0.2;
        return value < threshold;
    }

    /**
     * The block whose model is used instead, for the given face (null side = unculled quads).
     * Returns null if the block is drawn normally.
     */
    @Nullable
    public static BlockState substitute(BlockState state, BlockPos pos, @Nullable Direction side) {
        if (!isShuffled(state, pos)) return null;
        ensurePools();
        Kind kind = kindOf(state);
        long key = mix(cellHash(pos) ^ state.getBlock().hashCode() * 31L);
        // Mixing faces on see-through leaves leaves single opaque faces floating in the canopy, so only cubes do it.
        if (perFace() && side != null && (kind == Kind.SOLID || kind == Kind.LOG)) {
            key = mix(key ^ (side.ordinal() + 1) * 0x51ED27L);
        }

        List<BlockState> pool = switch (kind) {
            case LOG -> unit(mix(key ^ 7)) < 0.6 ? logPool : solidPool;
            case LEAVES -> corruption < 0.5F || unit(mix(key ^ 11)) < 0.7 ? leavesPool : solidPool;
            case PLANT -> plantPool;
            default -> solidPool;
        };
        if (pool.isEmpty()) return null;
        BlockState sub = pool.get((int) Math.floorMod(key, (long) pool.size()));
        if (sub.getBlock() == state.getBlock()) {
            sub = pool.get((int) Math.floorMod(key + 1, (long) pool.size()));
        }
        // Keep log orientation so trunks still look like trunks.
        if (state.hasProperty(BlockStateProperties.AXIS) && sub.hasProperty(BlockStateProperties.AXIS)) {
            sub = sub.setValue(BlockStateProperties.AXIS, state.getValue(BlockStateProperties.AXIS));
        }
        return sub;
    }

    // ------------------------------------------------------------------ classification

    private enum Kind { NONE, LOG, LEAVES, SOLID, PLANT }

    private static Kind kindOf(BlockState state) {
        if (state.getRenderShape() != RenderShape.MODEL || state.getBlock() instanceof EntityBlock) return Kind.NONE;
        if (state.is(BlockTags.LEAVES)) return Kind.LEAVES;
        if (state.is(BlockTags.LOGS)) return Kind.LOG;
        if (isPlant(state)) return Kind.PLANT;
        // Only full opaque cubes may be swapped with each other, otherwise we could see through the world.
        if (state.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) return Kind.SOLID;
        return Kind.NONE;
    }

    private static boolean isPlant(BlockState state) {
        return state.getBlock() instanceof BushBlock && !(state.getBlock() instanceof DoublePlantBlock)
                && !state.hasProperty(BlockStateProperties.AGE_7) && !state.hasProperty(BlockStateProperties.AGE_3);
    }

    private static synchronized void ensurePools() {
        if (solidPool != null) return;
        List<BlockState> solid = new ArrayList<>();
        List<BlockState> leaves = new ArrayList<>();
        List<BlockState> logs = new ArrayList<>();
        List<BlockState> plants = new ArrayList<>();
        for (var block : ForgeRegistries.BLOCKS.getValues()) {
            var id = ForgeRegistries.BLOCKS.getKey(block);
            if (id == null || !id.getNamespace().equals("minecraft")) continue;
            String path = id.getPath();
            if (path.contains("command_block") || path.contains("structure") || path.contains("jigsaw")
                    || path.contains("infested")) continue;
            BlockState state = block.defaultBlockState();
            switch (kindOf(state)) {
                case LOG -> logs.add(state);
                case LEAVES -> leaves.add(state);
                case PLANT -> plants.add(state);
                case SOLID -> solid.add(state);
                default -> {
                }
            }
        }
        solid.addAll(logs); // a trunk can look like stone, and stone like a trunk
        logPool = List.copyOf(logs);
        leavesPool = List.copyOf(leaves);
        plantPool = List.copyOf(plants);
        solidPool = List.copyOf(solid);
    }

    // ------------------------------------------------------------------ hashing

    private static long cellHash(BlockPos pos) {
        long cx = Math.floorDiv(pos.getX(), CELL);
        long cy = Math.floorDiv(pos.getY(), CELL);
        long cz = Math.floorDiv(pos.getZ(), CELL);
        return mix(cx * 0x9E3779B97F4A7C15L ^ cy * 0xC2B2AE3D27D4EB4FL ^ cz * 0x165667B19E3779F9L ^ salt);
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static double unit(long hash) {
        return (hash >>> 11) * 0x1.0p-53;
    }
}
