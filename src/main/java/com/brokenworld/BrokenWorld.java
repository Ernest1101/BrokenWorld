package com.brokenworld;

import com.brokenworld.event.ServerEvents;
import com.brokenworld.network.ModNetwork;
import com.brokenworld.registry.ModEntities;
import com.brokenworld.registry.ModItems;
import com.brokenworld.registry.ModSounds;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;

public class BrokenWorld implements ModInitializer {
    public static final String MODID = "brokenworld";
    public static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        Config.load();
        ModEntities.register();
        ModItems.register();
        ModSounds.register();
        ModNetwork.register();
        ServerEvents.register();
    }
}
