package com.brokenworld.client;

import com.mojang.blaze3d.platform.NativeImage;

import java.util.Random;

/**
 * Water and lava the way Minecraft Alpha drew them: not pictures but a tiny simulation, recomputed every tick - water a
 * soft rippling field, lava a slowly boiling one. (Written from how they looked, no game files involved.)
 */
public final class AlphaFluids {
    private static final Random RANDOM = new Random();

    private final boolean lava;
    private float[] current = new float[256];
    private float[] next = new float[256];
    private final float[] heat = new float[256];
    private final float[] heatSpeed = new float[256];
    private int ticks;

    public AlphaFluids(boolean lava) {
        this.lava = lava;
    }

    /** One step of the simulation. */
    public void tick() {
        ticks++;
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                float sum = 0;
                if (lava) {
                    int wobbleX = (int) (Math.sin(y * Math.PI * 2 / 16) * 1.2);
                    int wobbleY = (int) (Math.sin(x * Math.PI * 2 / 16) * 1.2);
                    for (int dx = x - 1; dx <= x + 1; dx++)
                        for (int dy = y - 1; dy <= y + 1; dy++)
                            sum += current[((dx + wobbleX) & 15) + ((dy + wobbleY) & 15) * 16];
                    next[x + y * 16] = sum / 10.0F + (heat[(x & 15) + ((y & 15) * 16)]
                            + heat[((x + 1) & 15) + (y & 15) * 16] + heat[((x + 1) & 15) + ((y + 1) & 15) * 16]
                            + heat[(x & 15) + ((y + 1) & 15) * 16]) / 4.0F * 0.8F;
                } else {
                    for (int dx = x - 1; dx <= x + 1; dx++) sum += current[(dx & 15) + (y & 15) * 16];
                    next[x + y * 16] = sum / 3.3F + heat[x + y * 16] * 0.8F;
                }
            }
        }
        for (int i = 0; i < 256; i++) {
            if (lava) {
                heat[i] += heatSpeed[i] * 0.01F;
                if (heat[i] < 0) heat[i] = 0;
                heatSpeed[i] -= 0.06F;
                if (RANDOM.nextDouble() < 0.005) heatSpeed[i] = 1.5F;
            } else {
                heat[i] += heatSpeed[i] * 0.05F;
                if (heat[i] < 0) heat[i] = 0;
                heatSpeed[i] -= 0.1F;
                if (RANDOM.nextDouble() < 0.05) heatSpeed[i] = 0.5F;
            }
        }
        float[] t = current;
        current = next;
        next = t;
    }

    /**
     * The current frame, `size` pixels square (16 for still fluids, 32 for flowing ones - the 16 tile repeated).
     * Water is Alpha's deep blue (its biome tint is switched off meanwhile, see AlphaColors); lava in its colours.
     */
    public NativeImage frame(int size) {
        NativeImage img = new NativeImage(size, size, true);
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                float f = Math.max(0, Math.min(1, current[(x & 15) + (y & 15) * 16]));
                int r, g, b, a;
                if (lava) {
                    r = (int) (f * 100 + 155);
                    g = (int) (f * f * 255);
                    b = (int) (f * f * f * f * 128);
                    a = 255;
                } else {
                    float f2 = f * f;
                    r = (int) (32 + f2 * 32);
                    g = (int) (50 + f2 * 64);
                    b = 255;
                    a = (int) (146 + f2 * 50);
                }
                img.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
        return img;
    }
}
