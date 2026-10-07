package com.brokenworld.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.util.function.Function;

/** The game's private parts the broken font and menu need (by reflection; SRG names for the production game). */
final class ClientAccess {
    private static final Field FONTS = ObfuscationReflectionHelper.findField(Font.class, "f_92713_");
    private static final Field SPLASH = ObfuscationReflectionHelper.findField(TitleScreen.class, "f_96721_");

    private ClientAccess() {}

    @SuppressWarnings("unchecked")
    static Function<ResourceLocation, FontSet> fonts(Font font) {
        try {
            return (Function<ResourceLocation, FontSet>) FONTS.get(font);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    static void fonts(Font font, Function<ResourceLocation, FontSet> fonts) {
        try {
            FONTS.set(font, fonts);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    static void splash(TitleScreen screen, SplashRenderer splash) {
        try {
            SPLASH.set(screen, splash);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
