package com.brokenworld.registry;

import com.brokenworld.BrokenWorld;
import com.brokenworld.entity.SilhouetteEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, BrokenWorld.MODID);

    // MISC category: never spawns naturally, only through the Director.
    public static final RegistryObject<EntityType<SilhouetteEntity>> SILHOUETTE = ENTITIES.register("silhouette",
            () -> EntityType.Builder.of(SilhouetteEntity::new, MobCategory.MISC)
                    .sized(0.6F, 2.5F)
                    .clientTrackingRange(12)
                    .fireImmune()
                    .build(BrokenWorld.MODID + ":silhouette"));

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(SILHOUETTE.get(), SilhouetteEntity.createAttributes().build());
    }
}
