package com.brokenworld.network;

import com.brokenworld.BrokenWorld;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server -> client messages. The client side (what each one does) is in client/ClientNetwork.
 */
public final class ModNetwork {
    public static final ResourceLocation CORRUPTION = new ResourceLocation(BrokenWorld.MODID, "corruption");
    public static final ResourceLocation SCREEN_FX = new ResourceLocation(BrokenWorld.MODID, "screen_fx");
    public static final ResourceLocation END_WORLD = new ResourceLocation(BrokenWorld.MODID, "end_world");

    private ModNetwork() {}

    public static void register() {
        // (nothing to register on the server: the messages only go to clients)
    }

    /**
     * Sends to one player. (Not checked with canSend: right after joining, the server does not know the client's
     * channels yet, and the corruption has to arrive then. A game without the mod just ignores the message.)
     */
    private static void send(ServerPlayer player, ResourceLocation id, FriendlyByteBuf buf) {
        if (player.connection == null) return;
        ServerPlayNetworking.send(player, id, buf);
    }

    private static FriendlyByteBuf buf() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    public static void sendFx(ServerPlayer player, ScreenFxPacket.Type type, int ticks) {
        FriendlyByteBuf buf = buf();
        new ScreenFxPacket(type, ticks).encode(buf);
        send(player, SCREEN_FX, buf);
    }

    /** Tells the host's game to leave the world (and delete it if `delete`). */
    public static void sendEndWorld(ServerPlayer player, boolean delete, String levelId) {
        FriendlyByteBuf buf = buf();
        new EndWorldPacket(delete, levelId).encode(buf);
        send(player, END_WORLD, buf);
    }

    public static void sendCorruption(ServerPlayer player, float corruption, long salt) {
        FriendlyByteBuf buf = buf();
        new CorruptionPacket(corruption, salt).encode(buf);
        send(player, CORRUPTION, buf);
    }

    public static void broadcastCorruption(MinecraftServer server, float corruption, long salt) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) sendCorruption(player, corruption, salt);
    }

    public record EndWorldPacket(boolean delete, String levelId) {
        void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(delete);
            buf.writeUtf(levelId);
        }

        public static EndWorldPacket decode(FriendlyByteBuf buf) {
            return new EndWorldPacket(buf.readBoolean(), buf.readUtf());
        }
    }

    /** Full-screen effects (and a few other things the client has to do right away). */
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

        void encode(FriendlyByteBuf buf) {
            buf.writeEnum(type);
            buf.writeVarInt(ticks);
        }

        public static ScreenFxPacket decode(FriendlyByteBuf buf) {
            return new ScreenFxPacket(buf.readEnum(Type.class), buf.readVarInt());
        }
    }

    /** How "broken" the world looks (0..1) and a per-world salt so every world is mixed differently. */
    public record CorruptionPacket(float corruption, long salt) {
        void encode(FriendlyByteBuf buf) {
            buf.writeFloat(corruption);
            buf.writeLong(salt);
        }

        public static CorruptionPacket decode(FriendlyByteBuf buf) {
            return new CorruptionPacket(buf.readFloat(), buf.readLong());
        }
    }
}
