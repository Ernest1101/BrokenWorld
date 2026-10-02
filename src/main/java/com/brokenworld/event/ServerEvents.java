package com.brokenworld.event;

import com.brokenworld.command.BrokenWorldCommand;
import com.brokenworld.world.Director;
import com.brokenworld.world.BrokenWorldState;
import com.brokenworld.world.FakeChat;
import com.brokenworld.world.Finale;
import com.brokenworld.world.PlayerLikeAnimals;
import com.brokenworld.world.House;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ServerEvents {
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Director.tick(event.getServer());
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        Director.reset();
    }

    /** No suffocation / fall damage while sinking through the ground or waiting in the dark. */
    @SubscribeEvent
    public void onAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && (Finale.isProtected(player) || com.brokenworld.world.FakeCrash.isProtected(player))) {
            event.setCanceled(true);
        }
    }

    /** Animals that behave like players keep doing it after reloading; new ones may become one. */
    @SubscribeEvent
    public void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getLevel().getServer() != null && PlayerLikeAnimals.eligible(event.getEntity())) {
            PlayerLikeAnimals.onJoin(event.getEntity(), BrokenWorldState.get(event.getLevel().getServer()).getStage());
        }
    }

    /** The visitor in chat writes back whatever you say. */
    @SubscribeEvent
    public void onChat(ServerChatEvent event) {
        FakeChat.onChat(event.getPlayer(), event.getRawText());
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            House.onRespawn(player);
            if (Finale.isParticipant(player)) Finale.onRespawn(player);
        }
    }

    /** Breaking the walls of the house that is bigger on the inside only takes you back to the small one. */
    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && House.onBreak(player, event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        Director.reset();
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Director.syncTo(player);
            Finale.onLogin(player);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        BrokenWorldCommand.register(event.getDispatcher());
    }
}
