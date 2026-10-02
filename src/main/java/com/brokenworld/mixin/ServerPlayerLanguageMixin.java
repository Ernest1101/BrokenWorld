package com.brokenworld.mixin;

import com.brokenworld.util.PlayerLanguage;
import net.minecraft.network.protocol.game.ServerboundClientInformationPacket;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Remembers the language each player's game is set to (the fake chat and the signs write in it). */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerLanguageMixin implements PlayerLanguage {
    @Unique
    private String brokenworld$language = "en_us";

    @Override
    public String brokenworld$language() {
        return brokenworld$language;
    }

    @Inject(method = "updateOptions", at = @At("HEAD"))
    private void brokenworld$rememberLanguage(ServerboundClientInformationPacket packet, CallbackInfo ci) {
        if (packet.language() != null) brokenworld$language = packet.language();
    }
}
