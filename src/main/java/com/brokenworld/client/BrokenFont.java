package com.brokenworld.client;

import com.brokenworld.BrokenWorld;
import com.brokenworld.Config;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

/**
 * The game's font breaks along with the world (and, in the main menu, as far as a world ever broke - see MenuMemory):
 * some letters are drawn as other letters (all of one letter at once, as if the font file itself were damaged, and
 * a different one every few seconds), some as the enchanting table's runes, now and then one flickers into a block
 * of noise, letters shake, and sometimes for a moment all text turns to garbage. Only the drawing: what the text
 * says, its width and layout stay as they are. Nothing in a Minecraft Alpha hallucination.
 */
public final class BrokenFont {
    private static final RandomSource RANDOM = RandomSource.create();
    private static final ResourceLocation RUNES = new ResourceLocation("minecraft", "alt");
    private static final int[] NOISE = "░▒▓█▌▐■□▪¿?#%&@§¶ЖЯ"
            .codePoints().toArray();
    private static final Map<FontSet, Broken> WRAPPED = new WeakHashMap<>();

    private static boolean installed;
    private static volatile float intensity;
    private static long burstUntil;
    /** Development only (-Dbrokenworld.shot): one screenshot of broken text in a world. */
    private static int devTicks;

    private BrokenFont() {}

    public static void tick(Minecraft mc) {
        if (!installed) install(mc);
        float f;
        if (!Config.BROKEN_FONTS.get() || Hallucination.active()) f = 0F;
        else if (mc.level != null) f = TextureShuffle.corruption();
        else f = Config.BROKEN_MENU.get() ? MenuMemory.level() : 0F;
        intensity = f;
        if (System.getProperty("brokenworld.shot") != null && mc.level != null && f >= 0.5F && ++devTicks == 300) {
            net.minecraft.client.Screenshot.grab(mc.gameDirectory, mc.getMainRenderTarget(), msg -> { });
        }
        long now = System.currentTimeMillis();
        if (f > 0.3F && now > burstUntil && RANDOM.nextFloat() < f * 0.004F) {
            burstUntil = now + 120 + RANDOM.nextInt(400);
        }
    }

    private static void install(Minecraft mc) {
        installed = true;
        wrap(mc.font);
        wrap(mc.fontFilterFishy);
    }

    private static void wrap(Font font) {
        Function<ResourceLocation, FontSet> real = ClientAccess.fonts(font);
        ClientAccess.fonts(font, id -> {
            FontSet set = real.apply(id);
            if (intensity <= 0F || id.equals(RUNES)) return set;
            return WRAPPED.computeIfAbsent(set, s -> new Broken(s, real));
        });
    }

    private static int mix(long x) {
        x = (x ^ (x >>> 33)) * 0xff51afd7ed558ccdL;
        x = (x ^ (x >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return (int) (x ^ (x >>> 33));
    }

    /** Another character of the same kind (a letter for a letter, a digit for a digit). */
    private static int swap(int cp, int h) {
        int r = (h >>> 16) & 0x7FFF;
        if (cp >= 'a' && cp <= 'z') return 'a' + r % 26;
        if (cp >= 'A' && cp <= 'Z') return 'A' + r % 26;
        if (cp >= 0x430 && cp <= 0x44F) return 0x430 + r % 32; // а-я
        if (cp >= 0x410 && cp <= 0x42F) return 0x410 + r % 32; // А-Я
        if (cp >= '0' && cp <= '9') return '0' + r % 10;
        return NOISE[r % NOISE.length];
    }

    /** One font of the game, drawing some of its letters wrong. */
    private static final class Broken extends FontSet {
        private final FontSet real;
        private final Function<ResourceLocation, FontSet> fonts;

        Broken(FontSet real, Function<ResourceLocation, FontSet> fonts) {
            super(Minecraft.getInstance().getTextureManager(), new ResourceLocation(BrokenWorld.MODID, "broken"));
            this.real = real;
            this.fonts = fonts;
        }

        @Override
        public GlyphInfo getGlyphInfo(int cp, boolean filterFishyGlyphs) {
            return real.getGlyphInfo(cp, filterFishyGlyphs); // (the widths stay right: the text keeps its place)
        }

        @Override
        public BakedGlyph getRandomGlyph(GlyphInfo info) {
            return real.getRandomGlyph(info);
        }

        @Override
        public BakedGlyph whiteGlyph() {
            return real.whiteGlyph();
        }

        @Override
        public void close() {
            // the real font belongs to the game
        }

        @Override
        public BakedGlyph getGlyph(int cp) {
            float f = intensity;
            if (f <= 0F || cp <= ' ') return real.getGlyph(cp);
            long now = System.currentTimeMillis();
            if (now < burstUntil) return shake(real.getGlyph(swap(cp, RANDOM.nextInt())), 1.5F);
            int h = mix(cp * 31L + now / 2500);
            float u = (h & 0xFFFF) / 65536F;
            BakedGlyph glyph;
            if (u < f * 0.12F) {
                glyph = real.getGlyph(swap(cp, h));
            } else if (f > 0.35F && u < f * 0.2F) {
                glyph = fonts.apply(RUNES).getGlyph('a' + ((h >>> 16) & 0x7FFF) % 26);
            } else if (RANDOM.nextFloat() < f * 0.01F) {
                glyph = real.getGlyph(NOISE[RANDOM.nextInt(NOISE.length)]);
            } else {
                glyph = real.getGlyph(cp);
            }
            if (f > 0.5F && RANDOM.nextFloat() < (f - 0.5F) * 0.25F) glyph = shake(glyph, 1F);
            return glyph;
        }
    }

    private static BakedGlyph shake(BakedGlyph glyph, float amount) {
        if (glyph instanceof net.minecraft.client.gui.font.glyphs.EmptyGlyph) return glyph;
        return new Shaken(glyph, (RANDOM.nextFloat() - 0.5F) * 2F * amount, (RANDOM.nextFloat() - 0.5F) * 2F * amount);
    }

    /** A letter drawn a little off its place. */
    private static final class Shaken extends BakedGlyph {
        private final BakedGlyph glyph;
        private final float dx, dy;

        Shaken(BakedGlyph glyph, float dx, float dy) {
            super(null, 0, 0, 0, 0, 0, 0, 0, 0);
            this.glyph = glyph;
            this.dx = dx;
            this.dy = dy;
        }

        @Override
        public void render(boolean italic, float x, float y, Matrix4f matrix, VertexConsumer buffer, float r, float g,
                           float b, float a, int light) {
            glyph.render(italic, x + dx, y + dy, matrix, buffer, r, g, b, a, light);
        }

        @Override
        public RenderType renderType(Font.DisplayMode mode) {
            return glyph.renderType(mode);
        }
    }
}
