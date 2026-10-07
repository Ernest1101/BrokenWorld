package com.brokenworld.client;

import com.brokenworld.BrokenWorld;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.biome.AmbientAdditionsSettings;
import net.minecraft.world.level.biome.AmbientParticleSettings;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The biomes of Minecraft Alpha, for a hallucination. Alpha's biomes differed only in the colour of the grass and the
 * leaves and of the sky (both from the temperature): so grass and leaves are coloured from Alpha's own colour maps
 * (misc/grasscolor.png, misc/foliagecolor.png from its jar), the sky as Alpha worked it out, the fog and the water fog
 * Alpha's, water takes no tint at all (Alpha's water had its deep blue in the texture itself, see AlphaFluids), and
 * none of today's biome extras: no swamp or badlands colours, no floating ash or spores, no biome sounds or music.
 */
public final class AlphaColors {
    /** Everything a biome looks and sounds like (as it was, to put it back). */
    private record Effects(BiomeSpecialEffects fx, int fog, int water, int waterFog, int sky,
                           Optional<Integer> foliage, Optional<Integer> grass,
                           BiomeSpecialEffects.GrassColorModifier modifier, Optional<AmbientParticleSettings> particles,
                           Optional<Holder<SoundEvent>> loop, Optional<AmbientAdditionsSettings> additions,
                           Optional<Music> music) {}

    private static final List<Effects> SAVED = new ArrayList<>();
    private static boolean colorMaps;

    private AlphaColors() {}

    public static void apply(Minecraft mc, @Nullable NativeImage grass, @Nullable NativeImage foliage) {
        if (mc.level == null || !SAVED.isEmpty()) return;
        for (Biome biome : mc.level.registryAccess().registryOrThrow(Registries.BIOME)) {
            BiomeSpecialEffects fx = biome.getSpecialEffects();
            SAVED.add(new Effects(fx, fx.getFogColor(), fx.getWaterColor(), fx.getWaterFogColor(), fx.getSkyColor(),
                    fx.getFoliageColorOverride(), fx.getGrassColorOverride(), fx.getGrassColorModifier(),
                    fx.getAmbientParticleSettings(), fx.getAmbientLoopSoundEvent(), fx.getAmbientAdditionsSettings(),
                    fx.getBackgroundMusic()));
            float t = Mth.clamp(biome.getBaseTemperature() / 3.0F, -1.0F, 1.0F);
            int sky = Mth.hsvToRgb(0.62222224F - t * 0.05F, 0.5F + t * 0.1F, 1.0F);
            set(new Effects(fx, 0xC0D8FF, 0xFFFFFF, 0x050533, sky, Optional.empty(), Optional.empty(),
                    BiomeSpecialEffects.GrassColorModifier.NONE, Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty()));
        }
        if (grass != null && foliage != null) {
            GrassColor.init(grass.makePixelArray());
            FoliageColor.init(foliage.makePixelArray());
            colorMaps = true;
        }
        mc.level.clearTintCaches();
    }

    /** Today's biomes again (the chunks are rebuilt by the caller). */
    public static void restore(Minecraft mc) {
        for (Effects e : SAVED) set(e);
        SAVED.clear();
        if (colorMaps) {
            colorMaps = false;
            ResourceManager rm = mc.getResourceManager();
            try {
                GrassColor.init(pixels(rm, new ResourceLocation("minecraft", "textures/colormap/grass.png")));
                FoliageColor.init(pixels(rm, new ResourceLocation("minecraft", "textures/colormap/foliage.png")));
            } catch (Exception e) {
                BrokenWorld.LOGGER.warn("[BrokenWorld] could not restore the grass colours: {}", e.toString());
            }
        }
        if (mc.level != null) mc.level.clearTintCaches();
    }

    private static void set(Effects e) {
        BiomeSpecialEffects fx = e.fx;
        fx.fogColor = e.fog;
        fx.waterColor = e.water;
        fx.waterFogColor = e.waterFog;
        fx.skyColor = e.sky;
        fx.foliageColorOverride = e.foliage;
        fx.grassColorOverride = e.grass;
        fx.grassColorModifier = e.modifier;
        fx.ambientParticleSettings = e.particles;
        fx.ambientLoopSoundEvent = e.loop;
        fx.ambientAdditionsSettings = e.additions;
        fx.backgroundMusic = e.music;
    }

    private static int[] pixels(ResourceManager rm, ResourceLocation loc) throws Exception {
        try (InputStream in = rm.open(loc); NativeImage img = NativeImage.read(in)) {
            return img.makePixelArray();
        }
    }
}
