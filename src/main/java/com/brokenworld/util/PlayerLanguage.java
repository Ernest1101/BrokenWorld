package com.brokenworld.util;

import net.minecraft.server.level.ServerPlayer;

/** The language a player's game is set to ("ru_ru", "en_us"...), added to players by mixin.ServerPlayerLanguageMixin. */
public interface PlayerLanguage {
    String brokenworld$language();

    static String of(ServerPlayer player) {
        return ((PlayerLanguage) player).brokenworld$language();
    }
}
