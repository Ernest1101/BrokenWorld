package com.brokenworld.client;

import com.brokenworld.BrokenWorld;
import com.brokenworld.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

/** Everything the mod does in the game client: drawing, screens, sounds, and what the server tells it. */
public class BrokenWorldClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.SILHOUETTE.get(), SilhouetteRenderer::new);

        // Wrap every block-state model so it can be drawn with another block's textures.
        ModelLoadingPlugin.register(plugin -> plugin.modifyModelAfterBake().register((model, context) -> {
            if (model != null && context.id() instanceof ModelResourceLocation mrl && !"inventory".equals(mrl.getVariant())
                    && !(model instanceof ShuffledBakedModel)) {
                return new ShuffledBakedModel(model);
            }
            return model;
        }));

        HudRenderCallback.EVENT.register((g, partialTick) -> {
            Hallucination.render(g);
            ScreenFx.render(g, partialTick);
        });
        ClientNetwork.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ScreenFx.tick();
            Hallucination.tick();
            FacelessMobs.tick();
            FakeDisconnect.tick();
            BrokenSounds.tick();
            WorldEnd.tick();
            BrokenFont.tick(client);
            BrokenMenu.tick(client);
        });

        // The main menu breaks as far as a world ever did.
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof TitleScreen title) {
                BrokenMenu.onTitle(client, title);
                ScreenEvents.afterRender(screen).register((s, g, mouseX, mouseY, delta) ->
                        BrokenMenu.render(g, s.width, s.height));
            }
        });

        // Leaving a broken world must not leave the next world broken.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            if (Hallucination.active()) Hallucination.stop();
            TextureShuffle.reset();
            FacelessMobs.reset();
            ScreenFx.reset();
            FakeDisconnect.reset();
            BrokenSounds.reset();
        }));

        // Edited silhouette meshes are picked up on F3+T.
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return new ResourceLocation(BrokenWorld.MODID, "silhouette_meshes");
            }

            @Override
            public void onResourceManagerReload(ResourceManager manager) {
                SilhouetteRenderer.clearCache();
                FacelessMobs.reset();
                Hallucination.onResourcesReloaded();
            }
        });
    }
}
