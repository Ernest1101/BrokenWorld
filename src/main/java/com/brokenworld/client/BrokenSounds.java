package com.brokenworld.client;

import com.brokenworld.BrokenWorld;
import com.brokenworld.Config;
import com.brokenworld.util.SoundBreaker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Sounds break along with the textures (same corruption value, same per-world salt):
 * <ul>
 *   <li>sounds are swapped for other vanilla sounds ({@link SoundBreaker}): in stage 1 more and more of them, from
 *       stage 2 every one - mobs, blocks, steps, items (throwing an egg...), always the same swap in a world;</li>
 *   <li>now and then a sound stutters (plays two more times right after), drops out completely, or comes out slowed
 *       down.</li>
 * </ul>
 * Left alone: the menu, the music, other mods' sounds, and sounds that follow something around while they last
 * (a minecart rolling, bees buzzing, the ambience loops) - those cannot be swapped without breaking them for real.
 */
@Mod.EventBusSubscriber(modid = BrokenWorld.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BrokenSounds {
    private static final RandomSource RANDOM = RandomSource.create();
    /** Repeats still to play for stuttering sounds: the sound and in how many ticks. */
    private static final List<Object[]> ECHOES = new ArrayList<>();
    private static boolean replaying;

    private BrokenSounds() {}

    @SubscribeEvent
    public static void onPlay(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null || replaying || !Config.BROKEN_SOUNDS.get()) return;
        float corruption = TextureShuffle.corruption();
        if (corruption <= 0F || sound.isLooping() || sound instanceof TickableSoundInstance) return;
        ResourceLocation id = sound.getLocation();
        if (!SoundBreaker.eligible(id)) return;
        if (sound.resolve(Minecraft.getInstance().getSoundManager()) == null) return; // (volume and pitch need it)

        ResourceLocation other = SoundBreaker.swap(id, corruption, TextureShuffle.salt());
        ResourceLocation playing = other != null ? other : id;
        SoundInstance result = other != null ? copy(sound, other, null) : sound;
        float r = RANDOM.nextFloat();
        if (r < corruption * 0.03F) {
            result = null; // it just does not make a sound
        } else if (r < corruption * 0.07F) {
            ECHOES.add(new Object[]{copy(sound, playing, null), 3});
            ECHOES.add(new Object[]{copy(sound, playing, null), 6});
        } else if (r < corruption * 0.11F) {
            result = copy(sound, playing, sound.getPitch() * 0.5F); // slowed down, deep
        }
        if (result != sound) event.setSound(result);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ECHOES.isEmpty()) return;
        Iterator<Object[]> it = ECHOES.iterator();
        List<SoundInstance> due = new ArrayList<>();
        while (it.hasNext()) {
            Object[] e = it.next();
            int left = (Integer) e[1] - 1;
            if (left <= 0) {
                due.add((SoundInstance) e[0]);
                it.remove();
            } else {
                e[1] = left;
            }
        }
        replaying = true;
        try {
            for (SoundInstance s : due) Minecraft.getInstance().getSoundManager().play(s);
        } finally {
            replaying = false;
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ECHOES.clear();
    }

    private static SoundInstance copy(SoundInstance s, ResourceLocation id, @Nullable Float pitch) {
        return new SimpleSoundInstance(id, s.getSource(), s.getVolume(), pitch != null ? pitch : s.getPitch(),
                RandomSource.create(RANDOM.nextLong()), false, 0, s.getAttenuation(), s.getX(), s.getY(), s.getZ(),
                s.isRelative());
    }
}
