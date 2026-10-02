package com.brokenworld.registry;

import com.brokenworld.BrokenWorld;
import com.brokenworld.entity.SilhouetteEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public class ModEntities {
    // MISC category: never spawns naturally, only through the Director.
    public static final Registered<EntityType<SilhouetteEntity>> SILHOUETTE = new Registered<>(Registry.register(
            BuiltInRegistries.ENTITY_TYPE, new ResourceLocation(BrokenWorld.MODID, "silhouette"),
            EntityType.Builder.of(SilhouetteEntity::new, MobCategory.MISC)
                    .sized(0.6F, 2.5F)
                    .clientTrackingRange(12)
                    .fireImmune()
                    .build(BrokenWorld.MODID + ":silhouette")));

    public static void register() {
        FabricDefaultAttributeRegistry.register(SILHOUETTE.get(), SilhouetteEntity.createAttributes());
    }
}
