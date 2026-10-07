package com.brokenworld.client;

import com.brokenworld.BrokenWorld;
import com.brokenworld.Config;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The main menu remembers (MenuMemory): the further a world of this game ever broke, the more broken the menu is. The
 * logo tears and loses letters, the panorama goes grey and dark (red, once a world has ended) and the silhouette
 * stands in it, the splash text says other things, buttons twitch and now and then say something else, the screen
 * tears, a cave sound plays far away - and at its worst, for a moment, it stands right there. The buttons still do
 * what they say. Off with brokenMenu = false; deleting brokenworld/memory.properties mends it.
 */
public final class BrokenMenu {
    private static final RandomSource RANDOM = RandomSource.create();
    private static final ResourceLocation LOGO = new ResourceLocation("minecraft", "textures/gui/title/minecraft.png");
    private static final ResourceLocation SILHOUETTE = new ResourceLocation(BrokenWorld.MODID, "textures/gui/menu_silhouette.png");
    private static final int SPLASHES = 8;
    private static final int LINES = 5;

    private static float swappedLevel = -1F;
    private static final List<ResourceLocation> SWAPPED = new ArrayList<>();
    private static NativeImage logoOriginal;
    private static DynamicTexture logo;
    private static int logoTicks;
    private static final Map<Button, Component> RENAMED = new HashMap<>();
    private static final Map<Button, Integer> MOVED = new HashMap<>();
    private static int renameTicks;
    private static int moveTicks;
    private static int flashFrames;
    private static int nextSound = 400;

    private BrokenMenu() {}

    private static float level() {
        return Config.BROKEN_MENU.get() ? MenuMemory.level() : 0F;
    }

    /** A title screen was (re)built. */
    public static void onTitle(Minecraft mc, TitleScreen screen) {
        RENAMED.clear();
        MOVED.clear();
        renameTicks = moveTicks = 0;
        float l = level();
        ensureSwapped(mc, l);
        if (l > 0F) ClientAccess.splash(screen, new SplashRenderer(splash(l)));
    }

    /**
     * Our logo and panorama in place (again). Not while the game is still loading: its own loading of the same
     * textures would land on top of ours.
     */
    private static void ensureSwapped(Minecraft mc, float l) {
        if (mc.getOverlay() != null) return;
        boolean lost = logo != null && mc.getTextureManager().getTexture(LOGO, null) != logo;
        if (l == swappedLevel && !lost) return;
        restore(mc);
        if (l > 0F) swap(mc, l);
        swappedLevel = l;
    }

    private static String splash(float l) {
        String ended = MenuMemory.ended();
        if (!ended.isEmpty() && RANDOM.nextInt(3) == 0) return I18n.get("brokenworld.menu.ended", ended);
        int lines = l >= 0.6F ? SPLASHES : SPLASHES / 2; // the worse ones later
        return I18n.get("brokenworld.menu.splash." + (1 + RANDOM.nextInt(lines)));
    }

    /** Development only (-Dbrokenworld.shot): screenshots of the menu, to look at it. */
    private static int devTicks;

    public static void tick(Minecraft mc) {
        if (!(mc.screen instanceof TitleScreen screen)) return;
        if (System.getProperty("brokenworld.shot") != null && ++devTicks % 120 == 0 && devTicks <= 360) {
            net.minecraft.client.Screenshot.grab(mc.gameDirectory, mc.getMainRenderTarget(), msg -> { });
        }
        float l = level();
        ensureSwapped(mc, l);
        if (l <= 0F) return;
        // the logo: a little wrong all the time, very wrong for a moment now and then
        if (logo != null) {
            if (logoTicks > 0) {
                if (--logoTicks == 0) logoFrame(l * 0.35F);
            } else if (RANDOM.nextFloat() < 0.02F + l * 0.05F) {
                logoFrame(l * (0.6F + RANDOM.nextFloat()));
                logoTicks = 2 + RANDOM.nextInt(6);
            }
        }
        List<Button> buttons = new ArrayList<>();
        for (var child : screen.children()) if (child instanceof Button b) buttons.add(b);
        // a button says something else for a second
        if (renameTicks > 0) {
            if (--renameTicks == 0) {
                RENAMED.forEach(Button::setMessage);
                RENAMED.clear();
            }
        } else if (l >= 0.4F && !buttons.isEmpty() && RANDOM.nextFloat() < l * 0.01F) {
            Button b = buttons.get(RANDOM.nextInt(buttons.size()));
            RENAMED.put(b, b.getMessage());
            b.setMessage(Component.translatable("brokenworld.menu.button." + (1 + RANDOM.nextInt(LINES))));
            renameTicks = 15 + RANDOM.nextInt(25);
        }
        // a button twitches
        if (moveTicks > 0) {
            if (--moveTicks == 0) {
                MOVED.forEach(Button::setX);
                MOVED.clear();
            }
        } else if (l >= 0.6F && !buttons.isEmpty() && RANDOM.nextFloat() < l * 0.02F) {
            Button b = buttons.get(RANDOM.nextInt(buttons.size()));
            MOVED.put(b, b.getX());
            b.setX(b.getX() + (RANDOM.nextBoolean() ? 1 : -1) * (2 + RANDOM.nextInt(6)));
            moveTicks = 2 + RANDOM.nextInt(4);
        }
        if (l >= 0.8F && flashFrames == 0 && RANDOM.nextFloat() < 0.0015F) flashFrames = 3;
        if (l >= 0.5F && --nextSound <= 0) {
            nextSound = 900 + RANDOM.nextInt(1500);
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMBIENT_CAVE.value(),
                    0.6F + RANDOM.nextFloat() * 0.4F, 0.5F));
        }
    }

    /** Over the finished menu: darkness from below, tears across the screen, and sometimes it. */
    public static void render(GuiGraphics g, int width, int height) {
        float l = level();
        if (l <= 0F) return;
        g.fillGradient(0, 0, width, height, 0, (int) (l * 0x70) << 24);
        if (RANDOM.nextFloat() < l * 0.25F) {
            for (int i = 0, n = 1 + RANDOM.nextInt(3); i < n; i++) {
                int y = RANDOM.nextInt(Math.max(1, height));
                int color = RANDOM.nextBoolean() ? 0x000000 : 0xFFFFFF;
                g.fill(0, y, width, y + 1 + RANDOM.nextInt(3), ((20 + RANDOM.nextInt(60)) << 24) | color);
            }
        }
        if (flashFrames > 0) {
            flashFrames--;
            int sh = height * 9 / 10;
            int sw = sh * 76 / 240;
            RenderSystem.enableBlend();
            g.blit(SILHOUETTE, width - sw - width / 10, height - sh, sw, sh, 0, 0, 76, 240, 76, 240);
            RenderSystem.disableBlend();
        }
    }

    // ------------------------------------------------------------------ the textures

    private static void swap(Minecraft mc, float l) {
        boolean ended = !MenuMemory.ended().isEmpty();
        try {
            logoOriginal = read(mc, LOGO);
            logo = new DynamicTexture(glitch(logoOriginal, l * 0.35F));
            mc.getTextureManager().register(LOGO, logo);
            SWAPPED.add(LOGO);
        } catch (Exception e) {
            BrokenWorld.LOGGER.warn("[BrokenWorld] could not break the logo: {}", e.toString());
        }
        NativeImage figure = null;
        try {
            figure = read(mc, SILHOUETTE);
            for (int i = 0; i < 6; i++) {
                ResourceLocation loc = new ResourceLocation("minecraft", "textures/gui/title/background/panorama_" + i + ".png");
                NativeImage img = read(mc, loc);
                darken(img, l, ended);
                if (l >= 0.5F && (i == 0 || (ended && i == 2))) stand(img, figure, l, ended);
                mc.getTextureManager().register(loc, new DynamicTexture(img));
                SWAPPED.add(loc);
            }
        } catch (Exception e) {
            BrokenWorld.LOGGER.warn("[BrokenWorld] could not break the panorama: {}", e.toString());
        } finally {
            if (figure != null) figure.close();
        }
    }

    private static void restore(Minecraft mc) {
        for (ResourceLocation loc : SWAPPED) mc.getTextureManager().release(loc);
        SWAPPED.clear();
        logo = null;
        if (logoOriginal != null) logoOriginal.close();
        logoOriginal = null;
    }

    private static NativeImage read(Minecraft mc, ResourceLocation loc) throws Exception {
        try (InputStream in = mc.getResourceManager().open(loc)) {
            return NativeImage.read(in);
        }
    }

    private static void logoFrame(float strength) {
        NativeImage frame = glitch(logoOriginal, strength);
        logo.getPixels().copyFrom(frame);
        frame.close();
        logo.upload();
    }

    /** The logo, torn: bands of it slid sideways, the colours split, at the worst whole letters gone. */
    private static NativeImage glitch(NativeImage src, float s) {
        int w = src.getWidth(), h = src.getHeight();
        NativeImage out = new NativeImage(w, h, true);
        out.copyFrom(src);
        for (int band = 0, bands = (int) (s * 12); band < bands; band++) {
            int y0 = RANDOM.nextInt(h), bh = 1 + RANDOM.nextInt(1 + (int) (s * 8));
            int shift = (int) ((RANDOM.nextFloat() - 0.5F) * 2F * s * 24F);
            for (int y = y0; y < Math.min(h, y0 + bh); y++)
                for (int x = 0; x < w; x++) out.setPixelRGBA(x, y, src.getPixelRGBA(Math.floorMod(x - shift, w), y));
        }
        if (s > 0.4F) { // red slips to the side
            int dx = 1 + RANDOM.nextInt(3);
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w - dx; x++) {
                    int p = out.getPixelRGBA(x, y), q = out.getPixelRGBA(x + dx, y);
                    if ((p >>> 24) > 0) out.setPixelRGBA(x, y, (p & 0xFFFFFF00) | (q & 0xFF));
                }
        }
        if (s > 0.7F) { // a letter gone
            for (int i = 0, n = 1 + RANDOM.nextInt(2); i < n; i++) {
                int x0 = RANDOM.nextInt(w), cw = 8 + RANDOM.nextInt(20);
                for (int x = x0; x < Math.min(w, x0 + cw); x++)
                    for (int y = 0; y < h; y++) out.setPixelRGBA(x, y, 0);
            }
        }
        return out;
    }

    /** Grey and dark; red once a world has ended. */
    private static void darken(NativeImage img, float l, boolean ended) {
        float keep = 1F - 0.45F * l, grey = Math.min(1F, l * 0.8F);
        for (int y = 0; y < img.getHeight(); y++)
            for (int x = 0; x < img.getWidth(); x++) {
                int p = img.getPixelRGBA(x, y);
                float r = p & 0xFF, g = (p >> 8) & 0xFF, b = (p >> 16) & 0xFF;
                float lum = r * 0.3F + g * 0.59F + b * 0.11F;
                r = (r + (lum - r) * grey) * keep;
                g = (g + (lum - g) * grey) * keep;
                b = (b + (lum - b) * grey) * keep;
                if (ended) {
                    g *= 0.55F;
                    b *= 0.5F;
                }
                img.setPixelRGBA(x, y, (p & 0xFF000000) | ((int) b << 16) | ((int) g << 8) | (int) r);
            }
    }

    /** The silhouette standing in the panorama, far away (nearer once a world has ended). */
    private static void stand(NativeImage img, NativeImage figure, float l, boolean ended) {
        int w = img.getWidth(), h = img.getHeight();
        int fh = (int) (h * (0.05F + 0.1F * l) * (ended ? 1.8F : 1F));
        int fw = Math.max(1, fh * figure.getWidth() / figure.getHeight());
        int x0 = (int) (w * 0.58F), y0 = (int) (h * 0.6F) - fh;
        for (int y = 0; y < fh; y++)
            for (int x = 0; x < fw; x++) {
                int tx = x0 + x, ty = y0 + y;
                if (tx < 0 || ty < 0 || tx >= w || ty >= h) continue;
                int a = figure.getPixelRGBA(x * figure.getWidth() / fw, y * figure.getHeight() / fh) >>> 24;
                if (a == 0) continue;
                int p = img.getPixelRGBA(tx, ty);
                float k = 1F - a / 255F;
                int r = (int) ((p & 0xFF) * k + 8 * (1 - k)), g = (int) (((p >> 8) & 0xFF) * k + 8 * (1 - k));
                int b = (int) (((p >> 16) & 0xFF) * k + 10 * (1 - k));
                img.setPixelRGBA(tx, ty, 0xFF000000 | (b << 16) | (g << 8) | r);
            }
    }
}
