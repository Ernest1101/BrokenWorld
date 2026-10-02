package com.brokenworld.mixin.client;

import com.brokenworld.client.BrokenSounds;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every sound passes through BrokenSounds before it plays: it may come out as another sound, or not at all. */
@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At("HEAD"), cancellable = true)
    private void brokenworld$breakSound(SoundInstance sound, CallbackInfo ci) {
        if (BrokenSounds.isReplaying()) return;
        SoundInstance result = BrokenSounds.filter(sound);
        if (result == sound) return;
        ci.cancel();
        if (result != null) BrokenSounds.playUnbroken((SoundEngine) (Object) this, result);
    }
}
