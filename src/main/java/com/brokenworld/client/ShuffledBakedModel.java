package com.brokenworld.client;

import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Wraps every block model. When {@link TextureShuffle} says a block is broken, its faces are taken from another
 * vanilla block's model instead (Fabric Renderer API: needs Fabric's own renderer, or Indium next to Sodium).
 */
public class ShuffledBakedModel extends ForwardingBakedModel {
    private static final Direction[] SIDES = {null, Direction.DOWN, Direction.UP, Direction.NORTH,
            Direction.SOUTH, Direction.WEST, Direction.EAST};
    private static final Map<BlendMode, RenderMaterial> MATERIALS = new EnumMap<>(BlendMode.class);

    public ShuffledBakedModel(BakedModel original) {
        this.wrapped = original;
    }

    public BakedModel original() {
        return wrapped;
    }

    private static BakedModel modelOf(BlockState state) {
        BakedModel m = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        return m instanceof ShuffledBakedModel s ? s.original() : m;
    }

    /** Draw a face of another block in that block's own layer (leaves cut out, glass see-through...). */
    private static RenderMaterial material(Renderer renderer, BlockState state) {
        BlendMode mode = BlendMode.fromRenderLayer(ItemBlockRenderTypes.getChunkRenderType(state));
        return MATERIALS.computeIfAbsent(mode, m -> renderer.materialFinder().blendMode(0, m).find());
    }

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }

    @Override
    public void emitBlockQuads(BlockAndTintGetter level, BlockState state, BlockPos pos,
                               Supplier<RandomSource> randomSupplier, RenderContext context) {
        Renderer renderer = RendererAccess.INSTANCE.getRenderer();
        if (renderer != null && Hallucination.active()) {
            BlockState alpha = AlphaBlocks.of(state);
            if (alpha != state) {
                emitAlpha(renderer, level, alpha, pos, randomSupplier, context);
                return;
            }
        }
        if (renderer == null || !TextureShuffle.isShuffled(state, pos)) {
            super.emitBlockQuads(level, state, pos, randomSupplier, context);
            return;
        }
        QuadEmitter emitter = context.getEmitter();
        for (Direction side : SIDES) {
            BlockState sub = TextureShuffle.substitute(state, pos, side);
            BlockState drawn = sub != null ? sub : state;
            BakedModel model = sub != null ? modelOf(sub) : wrapped;
            RenderMaterial material = material(renderer, drawn);
            for (BakedQuad quad : model.getQuads(drawn, side, randomSupplier.get())) {
                emitter.fromVanilla(quad, material, side);
                emitter.emit();
            }
        }
    }

    /** The block drawn as its Alpha ancestor (see AlphaBlocks), coloured as that block, or not at all. */
    private static void emitAlpha(Renderer renderer, BlockAndTintGetter level, BlockState alpha, BlockPos pos,
                                  Supplier<RandomSource> randomSupplier, RenderContext context) {
        if (alpha.isAir()) return;
        BakedModel model = modelOf(alpha);
        RenderMaterial material = material(renderer, alpha);
        BlockColors colors = Minecraft.getInstance().getBlockColors();
        QuadEmitter emitter = context.getEmitter();
        for (Direction side : SIDES) {
            for (BakedQuad quad : model.getQuads(alpha, side, randomSupplier.get())) {
                emitter.fromVanilla(quad, material, side);
                if (quad.isTinted()) { // (the game would tint it as the real block: a birch leaf birch-coloured)
                    int c = 0xFF000000 | colors.getColor(alpha, level, pos, quad.getTintIndex());
                    for (int i = 0; i < 4; i++) emitter.color(i, c);
                    emitter.colorIndex(-1);
                }
                emitter.emit();
            }
        }
    }
}
