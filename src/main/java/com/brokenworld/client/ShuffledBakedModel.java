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
        if (!TextureShuffle.isShuffled(state, pos)) return data;
        return data.derive().with(POS, pos.immutable()).build();
    }

    @Override
    public @NotNull ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand,
                                                      @NotNull ModelData data) {
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
}
