package com.brokenworld.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps every block model. When {@link TextureShuffle} says a block is broken, its faces are
 * taken from another vanilla block's model instead.
 */
public class ShuffledBakedModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<BlockPos> POS = new ModelProperty<>();
    /** During a "Minecraft Alpha" hallucination: the block as Alpha draws it (see AlphaBlocks), and its colour. */
    private static final ModelProperty<BlockState> ALPHA = new ModelProperty<>();
    private static final ModelProperty<Integer> ALPHA_TINT = new ModelProperty<>();

    public ShuffledBakedModel(BakedModel original) {
        super(original);
    }

    public BakedModel original() {
        return originalModel;
    }

    private static BakedModel modelOf(BlockState state) {
        BakedModel m = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        return m instanceof ShuffledBakedModel s ? s.original() : m;
    }

    @Override
    public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos,
                                           @NotNull BlockState state, @NotNull ModelData modelData) {
        ModelData data = super.getModelData(level, pos, state, modelData);
        if (Hallucination.active()) {
            BlockState alpha = AlphaBlocks.of(state);
            if (alpha != state) {
                int tint = alpha.isAir() ? -1 : Minecraft.getInstance().getBlockColors().getColor(alpha, level, pos, 0);
                return data.derive().with(ALPHA, alpha).with(ALPHA_TINT, tint).build();
            }
        }
        if (!TextureShuffle.isShuffled(state, pos)) return data;
        return data.derive().with(POS, pos.immutable()).build();
    }

    @Override
    public @NotNull ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand,
                                                      @NotNull ModelData data) {
        BlockState alpha = data.get(ALPHA);
        if (alpha != null) {
            return alpha.isAir() ? ChunkRenderTypeSet.none() : modelOf(alpha).getRenderTypes(alpha, rand, ModelData.EMPTY);
        }
        BlockPos pos = data.get(POS);
        if (pos == null) return super.getRenderTypes(state, rand, data);
        List<ChunkRenderTypeSet> sets = new ArrayList<>();
        sets.add(super.getRenderTypes(state, rand, data));
        for (Direction side : SIDES) {
            BlockState sub = TextureShuffle.substitute(state, pos, side);
            if (sub != null) sets.add(modelOf(sub).getRenderTypes(sub, rand, ModelData.EMPTY));
        }
        return ChunkRenderTypeSet.union(sets);
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                                             @NotNull RandomSource rand, @NotNull ModelData data,
                                             @Nullable RenderType renderType) {
        BlockState alpha = data.get(ALPHA);
        if (alpha != null) return alphaQuads(alpha, side, rand, data, renderType);
        BlockPos pos = data.get(POS);
        if (pos == null || state == null) return super.getQuads(state, side, rand, data, renderType);
        BlockState sub = TextureShuffle.substitute(state, pos, side);
        if (sub == null) {
            // This face is fine; only draw it in the layers the original model actually uses.
            if (renderType != null && !super.getRenderTypes(state, rand, data).contains(renderType)) return List.of();
            return super.getQuads(state, side, rand, data, renderType);
        }
        BakedModel subModel = modelOf(sub);
        if (renderType != null && !subModel.getRenderTypes(sub, rand, ModelData.EMPTY).contains(renderType)) {
            return List.of();
        }
        return subModel.getQuads(sub, side, rand, ModelData.EMPTY, renderType);
    }

    private static final Direction[] SIDES = {null, Direction.DOWN, Direction.UP, Direction.NORTH,
            Direction.SOUTH, Direction.WEST, Direction.EAST};

    /** The Alpha ancestor's faces, coloured as that block (the game would tint them as the real one). */
    private static List<BakedQuad> alphaQuads(BlockState alpha, @Nullable Direction side, RandomSource rand,
                                              ModelData data, @Nullable RenderType renderType) {
        if (alpha.isAir()) return List.of();
        List<BakedQuad> quads = modelOf(alpha).getQuads(alpha, side, rand, ModelData.EMPTY, renderType);
        Integer tint = data.get(ALPHA_TINT);
        if (tint == null || quads.stream().noneMatch(BakedQuad::isTinted)) return quads;
        // vertex colour, ABGR (DefaultVertexFormat.BLOCK: 8 ints a vertex, the colour is the 4th)
        int abgr = 0xFF000000 | (tint & 0xFF) << 16 | (tint & 0xFF00) | (tint >> 16 & 0xFF);
        List<BakedQuad> out = new ArrayList<>(quads.size());
        for (BakedQuad q : quads) {
            if (!q.isTinted()) {
                out.add(q);
                continue;
            }
            int[] v = q.getVertices().clone();
            for (int i = 3; i < v.length; i += 8) v[i] = abgr;
            out.add(new BakedQuad(v, -1, q.getDirection(), q.getSprite(), q.isShade()));
        }
        return out;
    }
}
