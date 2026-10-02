package com.brokenworld.client;

import com.brokenworld.BrokenWorld;
import com.brokenworld.registry.ModEntities;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

@Mod.EventBusSubscriber(modid = BrokenWorld.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    /** Wrap every block-state model so it can be drawn with another block's textures. */
    @SubscribeEvent
    public static void wrapBlockModels(ModelEvent.ModifyBakingResult event) {
        for (Map.Entry<ResourceLocation, BakedModel> entry : event.getModels().entrySet()) {
            if (entry.getKey() instanceof ModelResourceLocation mrl && !"inventory".equals(mrl.getVariant())
                    && !(entry.getValue() instanceof ShuffledBakedModel)) {
                entry.setValue(new ShuffledBakedModel(entry.getValue()));
            }
        }
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SILHOUETTE.get(), SilhouetteRenderer::new);
    }

    /** Edited silhouette meshes are picked up on F3+T. */
    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            SilhouetteRenderer.clearCache();
            FacelessMobs.reset();
        });
    }
}
