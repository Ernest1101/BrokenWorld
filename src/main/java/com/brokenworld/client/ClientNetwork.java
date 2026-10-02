package com.brokenworld.client;

import com.brokenworld.network.ModNetwork;
import com.brokenworld.network.ModNetwork.CorruptionPacket;
import com.brokenworld.network.ModNetwork.EndWorldPacket;
import com.brokenworld.network.ModNetwork.ScreenFxPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** What the client does with the server's messages (read on the network thread, done on the game's thread). */
public final class ClientNetwork {
    private ClientNetwork() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ModNetwork.CORRUPTION, (client, handler, buf, sender) -> {
            CorruptionPacket p = CorruptionPacket.decode(buf);
            client.execute(() -> TextureShuffle.update(p.corruption(), p.salt()));
        });
        ClientPlayNetworking.registerGlobalReceiver(ModNetwork.SCREEN_FX, (client, handler, buf, sender) -> {
            ScreenFxPacket p = ScreenFxPacket.decode(buf);
            client.execute(() -> ScreenFx.handle(p.type(), p.ticks()));
        });
        ClientPlayNetworking.registerGlobalReceiver(ModNetwork.END_WORLD, (client, handler, buf, sender) -> {
            EndWorldPacket p = EndWorldPacket.decode(buf);
            client.execute(() -> WorldEnd.schedule(p.delete(), p.levelId()));
        });
    }
}
