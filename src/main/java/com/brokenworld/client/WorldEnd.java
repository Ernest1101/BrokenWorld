package com.brokenworld.client;

import com.brokenworld.BrokenWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.file.Path;

/**
 * The very end: the game leaves the world, and (singleplayer, if the config allows) deletes it.
 * Done from a client tick, not inside the packet handler, so the connection can be closed safely.
 */
@Mod.EventBusSubscriber(modid = BrokenWorld.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class WorldEnd {
    private static int countdown = -1;
    private static boolean delete;
    private static String levelId = "";

    private WorldEnd() {}

    public static void schedule(boolean deleteWorld, String id) {
        delete = deleteWorld;
        levelId = id;
        countdown = 5;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || countdown < 0 || --countdown > 0) return;
        countdown = -1;
        Minecraft mc = Minecraft.getInstance();

        // Only ever delete the world this game itself is hosting, and only if it is the one the server named.
        boolean deleteIt = false;
        if (delete && mc.getSingleplayerServer() != null) {
            Path root = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            deleteIt = root.getFileName() != null && root.getFileName().toString().equals(levelId);
        }

        boolean local = mc.isLocalServer();
        if (mc.level != null) mc.level.disconnect();
        if (local) {
            mc.clearLevel(new GenericDirtMessageScreen(Component.translatable("finale.brokenworld.leaving")));
        } else {
            mc.clearLevel();
        }
        ScreenFx.reset();
        MenuMemory.worldEnded(levelId); // the menu will remember this one

        if (deleteIt) {
            try (LevelStorageSource.LevelStorageAccess access = mc.getLevelSource().createAccess(levelId)) {
                access.deleteLevel();
                BrokenWorld.LOGGER.info("[BrokenWorld] world '{}' deleted", levelId);
            } catch (IOException e) {
                BrokenWorld.LOGGER.error("[BrokenWorld] could not delete world '{}'", levelId, e);
            }
        }
        mc.setScreen(new TitleScreen());
    }
}
