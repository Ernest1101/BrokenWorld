package com.brokenworld.network;

import com.brokenworld.BrokenWorld;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public final class ModNetwork {
    private static final String VERSION = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(BrokenWorld.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private ModNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(CorruptionPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CorruptionPacket::encode)
                .decoder(CorruptionPacket::decode)
                .consumerMainThread(CorruptionPacket::handle)
                .add();
        CHANNEL.messageBuilder(ScreenFxPacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ScreenFxPacket::encode)
                .decoder(ScreenFxPacket::decode)
                .consumerMainThread(ScreenFxPacket::handle)
                .add();
        CHANNEL.messageBuilder(EndWorldPacket.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(EndWorldPacket::encode)
                .decoder(EndWorldPacket::decode)
                .consumerMainThread(EndWorldPacket::handle)
                .add();
    }

    /** Sends to one player; skips fake players (e.g. GameTest mock players) that have no network channel. */
    private static void send(ServerPlayer player, Object message) {
        if (player.connection == null || player.connection.connection.channel() == null) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static void sendFx(ServerPlayer player, ScreenFxPacket.Type type, int ticks) {
        send(player, new ScreenFxPacket(type, ticks));
    }

    /** Tells the host's game to leave the world (and delete it if `delete`). */
    public static void sendEndWorld(ServerPlayer player, boolean delete, String levelId) {
        send(player, new EndWorldPacket(delete, levelId));
    }

    public record EndWorldPacket(boolean delete, String levelId) {
        static void encode(EndWorldPacket p, FriendlyByteBuf buf) {
            buf.writeBoolean(p.delete);
            buf.writeUtf(p.levelId);
        }

        static EndWorldPacket decode(FriendlyByteBuf buf) {
            return new EndWorldPacket(buf.readBoolean(), buf.readUtf());
        }

        static void handle(EndWorldPacket p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    com.brokenworld.client.WorldEnd.schedule(p.delete, p.levelId));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Full-screen effects for the finale. */
    public record ScreenFxPacket(Type type, int ticks) {
        public enum Type {
            /** Fade to black over `ticks` and stay black. */
            BLACK_ON,
            /** Fade from black back to the game over `ticks`. */
            BLACK_OFF,
            /** Its face fills the screen with a scream for `ticks`, then silence and black. */
            SCREAMER,
            /** The last screamer: like SCREAMER, with creaking that goes on in the black. */
            FINAL_SCREAMER,
            /** Mobs around lose their faces. */
            FACELESS_ON,
            /** ...and get them back. */
            FACELESS_OFF,
            /** The real "Connection Lost" screen for `ticks`, then "Loading terrain...", then back to the game. */
            FAKE_CRASH,
            /** "Minecraft Alpha" for `ticks`: the old textures, nothing broken (client/Hallucination). */
            HALLUCINATION
        }

        static void encode(ScreenFxPacket p, FriendlyByteBuf buf) {
            buf.writeEnum(p.type);
            buf.writeVarInt(p.ticks);
        }

        static ScreenFxPacket decode(FriendlyByteBuf buf) {
            return new ScreenFxPacket(buf.readEnum(Type.class), buf.readVarInt());
        }

        static void handle(ScreenFxPacket p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    com.brokenworld.client.ScreenFx.handle(p.type, p.ticks));
            ctx.get().setPacketHandled(true);
        }
    }

    public static void sendCorruption(ServerPlayer player, float corruption, long salt) {
        send(player, new CorruptionPacket(corruption, salt));
    }

    public static void broadcastCorruption(MinecraftServer server, float corruption, long salt) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) sendCorruption(player, corruption, salt);
    }

    /** How "broken" the world looks (0..1) and a per-world salt so every world is mixed differently. */
    public record CorruptionPacket(float corruption, long salt) {
        static void encode(CorruptionPacket p, FriendlyByteBuf buf) {
            buf.writeFloat(p.corruption);
            buf.writeLong(p.salt);
        }

        static CorruptionPacket decode(FriendlyByteBuf buf) {
            return new CorruptionPacket(buf.readFloat(), buf.readLong());
        }

        static void handle(CorruptionPacket p, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    com.brokenworld.client.TextureShuffle.update(p.corruption, p.salt));
            ctx.get().setPacketHandled(true);
        }
    }
}
