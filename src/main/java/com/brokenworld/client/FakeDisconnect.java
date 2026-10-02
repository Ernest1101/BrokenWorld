package com.brokenworld.client;

import com.brokenworld.world.FakeCrash;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.network.chat.Component;

/**
 * The client side of {@link FakeCrash}: the game's own "Connection Lost" screen (the real one, not a copy), then its
 * own "Loading terrain..." screen, then back to the game. Clicking the button only skips to the loading screen.
 */
public final class FakeDisconnect {
    private static int screenTicks = -1;
    private static int loadingTicks = -1;
    private static ReceivingLevelScreen loading;

    private FakeDisconnect() {}

    public static void show(int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        mc.getSoundManager().stop(); // a dead connection is silent
        loading = new ReceivingLevelScreen();
        mc.setScreen(new DisconnectedScreen(loading, Component.translatable("disconnect.lost"),
                Component.translatable("disconnect.timeout"), Component.translatable("gui.toTitle")));
        screenTicks = Math.max(1, ticks);
        loadingTicks = -1;
    }

    public static void tick() {
        if (loading == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (screenTicks > 0) {
            boolean skipped = mc.screen == loading; // the button was clicked
            if (skipped || --screenTicks == 0) {
                screenTicks = -1;
                if (!skipped) mc.setScreen(loading);
                loadingTicks = FakeCrash.LOADING_TICKS;
            }
        } else if (loadingTicks > 0 && --loadingTicks == 0) {
            if (mc.screen == loading) mc.setScreen(null);
            loading = null;
        }
    }

    public static void reset() {
        screenTicks = -1;
        loadingTicks = -1;
        loading = null;
    }
}
