package com.brokenworld.registry;

import com.brokenworld.BrokenWorld;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

public class ModSounds {
    private static final ResourceLocation SCREAM_ID = new ResourceLocation(BrokenWorld.MODID, "scream");

    /** The screamer when it catches you (sounds/scream.ogg, made by tools/make_scream.py). */
    public static final Registered<SoundEvent> SCREAM = new Registered<>(Registry.register(BuiltInRegistries.SOUND_EVENT,
            SCREAM_ID, SoundEvent.createVariableRangeEvent(SCREAM_ID)));

    public static void register() {
        // (registered when the class loads)
    }
}
