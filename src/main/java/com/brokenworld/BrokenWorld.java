package com.brokenworld;

import com.brokenworld.network.ModNetwork;
import com.brokenworld.registry.ModEntities;
import com.brokenworld.registry.ModItems;
import com.brokenworld.registry.ModSounds;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(BrokenWorld.MODID)
public class BrokenWorld {
    public static final String MODID = "brokenworld";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BrokenWorld() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModItems.ITEMS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModSounds.SOUNDS.register(modBus);

        modBus.addListener(ModEntities::registerAttributes);
        modBus.addListener(this::addCreative);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        ModNetwork.register();

        MinecraftForge.EVENT_BUS.register(new com.brokenworld.event.ServerEvents());
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.SILHOUETTE_SPAWN_EGG);
        }
    }
}
