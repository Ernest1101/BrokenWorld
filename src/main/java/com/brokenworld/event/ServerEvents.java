package com.brokenworld.event;

import com.brokenworld.command.BrokenWorldCommand;
import com.brokenworld.world.BrokenWorldState;
import com.brokenworld.world.Director;
import com.brokenworld.world.FakeChat;
import com.brokenworld.world.FakeCrash;
import com.brokenworld.world.Finale;
import com.brokenworld.world.House;
import com.brokenworld.world.PlayerLikeAnimals;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

public final class ServerEvents {
    private ServerEvents() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Director::tick);
        ServerLifecycleEvents.SERVER_STARTING.register(server -> Director.reset());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> Director.reset());

        // No suffocation / fall damage while sinking through the ground, waiting in the dark, or "disconnected".
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(entity instanceof ServerPlayer player && (Finale.isProtected(player) || FakeCrash.isProtected(player))));

        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            // a dropped maze note never despawns
            if (entity instanceof net.minecraft.world.entity.item.ItemEntity item
                    && item.getItem().is(com.brokenworld.registry.ModItems.NOTE.get())) {
                item.setUnlimitedLifetime();
            }
            // animals that behave like players keep doing it after reloading; new ones may become one
            if (PlayerLikeAnimals.eligible(entity)) {
                PlayerLikeAnimals.onJoin(entity, BrokenWorldState.get(level.getServer()).getStage());
            }
        });

        // The visitor in chat writes back whatever you say.
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> FakeChat.onChat(sender, message.signedContent()));

        // the mod's data stays with a player when they respawn
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, player, alive) ->
                com.brokenworld.util.ModData.of(player).merge(com.brokenworld.util.ModData.of(oldPlayer)));

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, player, alive) -> {
            House.onRespawn(player);
            if (Finale.isParticipant(player)) Finale.onRespawn(player);
        });

        // Breaking the walls of the house that is bigger on the inside only takes you back to the small one.
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
                !(player instanceof ServerPlayer sp && House.onBreak(sp, pos)));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            Director.syncTo(handler.player);
            Finale.onLogin(handler.player);
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                BrokenWorldCommand.register(dispatcher));
    }
}
