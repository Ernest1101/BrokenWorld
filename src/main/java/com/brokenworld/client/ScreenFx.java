package com.brokenworld.client;

import com.brokenworld.BrokenWorld;
import com.brokenworld.network.ModNetwork.ScreenFxPacket;
import com.brokenworld.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

/**
 * Full-screen effects for the finale: fading to/from black, and the screamer (its face + a scream) when the
 * monster catches you, cut off by sudden silence and black.
 */
public final class ScreenFx {
    private static final RandomSource RANDOM = RandomSource.create();

    private static float black;        // current opacity 0..1
    private static float blackTarget;  // where it is fading to
    private static float fadeStep = 1F;
    private static int screamerTicks;
    private static int screamerTotal = 1;
    /** The last screamer: creaking goes on for a while in the black. */
    private static int creakTicks;
    private static final ResourceLocation SCREAMER_IMAGE =
            new ResourceLocation(BrokenWorld.MODID, "textures/gui/screamer.png"); // 1280x720, tools/blender_screamer.py

    private ScreenFx() {}

    public static void handle(ScreenFxPacket.Type type, int ticks) {
        switch (type) {
            case BLACK_ON -> fadeTo(1F, ticks);
            case BLACK_OFF -> fadeTo(0F, ticks);
            case FACELESS_ON -> FacelessMobs.setActive(true);
            case FACELESS_OFF -> FacelessMobs.setActive(false);
            case FAKE_CRASH -> FakeDisconnect.show(ticks);
            case SCREAMER, FINAL_SCREAMER -> {
                Minecraft mc = Minecraft.getInstance();
                if (type == ScreenFxPacket.Type.FINAL_SCREAMER) creakTicks = ticks + 70;
                mc.getSoundManager().stop(); // everything else cuts out
                mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.SCREAM.get(), 1.0F, 1.0F));
                screamerTicks = ticks;
                screamerTotal = Math.max(1, ticks);
                black = 0F;
                blackTarget = 0F;
            }
        }
    }

    private static void fadeTo(float target, int ticks) {
        blackTarget = target;
        fadeStep = ticks <= 0 ? 1F : 1F / ticks;
        if (ticks <= 0) black = target;
    }

    public static void reset() {
        black = 0F;
        blackTarget = 0F;
        screamerTicks = 0;
        creakTicks = 0;
    }

    public static void tick() {
        if (black < blackTarget) black = Math.min(blackTarget, black + fadeStep);
        else if (black > blackTarget) black = Math.max(blackTarget, black - fadeStep);
        if (screamerTicks > 0 && --screamerTicks == 0) {
            // Cut: dead silence and black.
            Minecraft.getInstance().getSoundManager().stop();
            fadeTo(1F, 0);
        }
        if (creakTicks > 0) {
            creakTicks--;
            if (RANDOM.nextInt(5) == 0) creak();
        }
    }

    /** Old wood straining somewhere close: slowed-down doors and trapdoors. */
    private static void creak() {
        net.minecraft.sounds.SoundEvent[] sounds = {
                net.minecraft.sounds.SoundEvents.WOODEN_DOOR_OPEN, net.minecraft.sounds.SoundEvents.WOODEN_DOOR_CLOSE,
                net.minecraft.sounds.SoundEvents.WOODEN_TRAPDOOR_OPEN, net.minecraft.sounds.SoundEvents.LADDER_STEP,
                net.minecraft.sounds.SoundEvents.CHEST_OPEN};
        float pitch = 0.3F + RANDOM.nextFloat() * 0.35F;
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(sounds[RANDOM.nextInt(sounds.length)], pitch, 1.0F));
    }

    /** Drawn over everything (HudRenderCallback). */
    public static void render(GuiGraphics g, float partialTick) {
        int width = g.guiWidth(), height = g.guiHeight();
        if (screamerTicks > 0) renderScreamer(g, partialTick, width, height);
        if (black > 0F) {
            int alpha = Math.round(Math.min(1F, black) * 255F);
            g.fill(0, 0, width, height, alpha << 24);
        }
    }

    /** Its face fills the screen, lunges closer, shakes and flickers. */
    private static void renderScreamer(GuiGraphics g, float partialTick, int width, int height) {
        float progress = 1F - (screamerTicks - partialTick) / screamerTotal; // 0 -> 1
        g.fill(0, 0, width, height, 0xFF000000);
        // A couple of frames of nothing now and then: it flickers like a broken bulb.
        if (progress > 0.15F && RANDOM.nextInt(7) == 0) return;
        float zoom = 1.05F + progress * progress * 0.9F;
        int shake = Math.round(4 + progress * 14);
        // cover the screen, keep 16:9
        int w = Math.round(Math.max(width, height * 16F / 9F) * zoom);
        int h = Math.round(w * 9F / 16F);
        int x = (width - w) / 2 + RANDOM.nextInt(shake * 2 + 1) - shake;
        int y = (height - h) / 2 + RANDOM.nextInt(shake * 2 + 1) - shake + Math.round(h * 0.06F);
        g.blit(SCREAMER_IMAGE, x, y, w, h, 0, 0, 1280, 720, 1280, 720);
        if (RANDOM.nextInt(4) == 0) g.fill(0, 0, width, height, 0x55AA0000); // red flash
    }

}
