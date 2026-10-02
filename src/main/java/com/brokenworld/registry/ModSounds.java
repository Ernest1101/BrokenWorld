package com.brokenworld.registry;

import com.brokenworld.BrokenWorld;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, BrokenWorld.MODID);

    /** The screamer when it catches you (sounds/scream.ogg, made by tools/make_scream.py). */
    public static final RegistryObject<SoundEvent> SCREAM = SOUNDS.register("scream",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(BrokenWorld.MODID, "scream")));
}
