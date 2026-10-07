package com.brokenworld.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/** The game's private parts the broken font and menu need (opened by brokenworld.accesswidener). */
final class ClientAccess {
    private ClientAccess() {}

    static Function<ResourceLocation, FontSet> fonts(Font font) {
        return font.fonts;
    }

    static void fonts(Font font, Function<ResourceLocation, FontSet> fonts) {
        font.fonts = fonts;
    }

    static void splash(TitleScreen screen, SplashRenderer splash) {
        screen.splash = splash;
    }
}
