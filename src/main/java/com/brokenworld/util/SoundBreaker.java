package com.brokenworld.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Which vanilla sound plays instead of which, in a broken world (used by the client's BrokenSounds; kept free of
 * client classes so the tests can check it). Like the textures: decided by the world's salt, so the same sound always
 * breaks the same way in a world; all sounds of one thing break together (a door's opening and closing).
 * From corruption 0.4 on (stage 2) every sound is broken; in stage 1 more and more of them.
 */
public final class SoundBreaker {
    /** Loud, long or game-changing sounds that must never be what something else sounds like. */
    private static final String[] NEVER = {"explode", "wither", "ender_dragon", "elder_guardian.curse", "raid", "horn",
            "thunder", "portal", "warden", "lightning", "totem", "beacon", "end_gateway", "conduit", "bell", "loop",
            "music", "sonic", "respawn_anchor", "ui.", "ambient", "underwater"};

    private static List<ResourceLocation> pool;

    private SoundBreaker() {}

    /** Every vanilla sound breaks - except the menu clicks and the music. */
    public static boolean eligible(ResourceLocation id) {
        String p = id.getPath();
        return "minecraft".equals(id.getNamespace()) && !p.startsWith("ui.") && !p.startsWith("music");
    }

    /** How many of the things' sounds are broken: all of them from corruption 0.4 on. */
    public static float share(float corruption) {
        return corruption <= 0F ? 0F : Math.min(1F, corruption / 0.4F);
    }

    /** The sound that plays instead of `id`, or null if it plays as it is. */
    @Nullable
    public static ResourceLocation swap(ResourceLocation id, float corruption, long salt) {
        if (!eligible(id) || corruption <= 0F) return null;
        String path = id.getPath();
        int dot = path.lastIndexOf('.');
        String thing = dot > 0 ? path.substring(0, dot) : path; // "block.wooden_door.open" -> "block.wooden_door"
        if (unit(mix(thing.hashCode() * 0x9E3779B97F4A7C15L ^ salt)) >= share(corruption)) return null;
        List<ResourceLocation> pool = pool();
        if (pool.size() < 2) return null;
        long hash = mix(mix(path.hashCode() * 0x9E3779B97F4A7C15L ^ salt) ^ 0x5157L);
        ResourceLocation other = pool.get((int) Math.floorMod(hash, (long) pool.size()));
        if (other.equals(id)) other = pool.get((int) Math.floorMod(hash + 1, (long) pool.size()));
        return other;
    }

    /** What broken sounds are picked from: short sounds of mobs, blocks and items. */
    public static List<ResourceLocation> pool() {
        if (pool == null) {
            List<ResourceLocation> list = new ArrayList<>();
            for (ResourceLocation key : ForgeRegistries.SOUND_EVENTS.getKeys()) {
                String p = key.getPath();
                if (!"minecraft".equals(key.getNamespace())
                        || !(p.startsWith("entity.") || p.startsWith("block.") || p.startsWith("item."))) continue;
                boolean bad = false;
                for (String n : NEVER) bad |= p.contains(n);
                if (!bad) list.add(key);
            }
            list.sort(ResourceLocation::compareTo); // the same order everywhere, so the same swaps
            pool = list;
        }
        return pool;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static double unit(long hash) {
        return (hash >>> 11) * 0x1.0p-53;
    }
}
