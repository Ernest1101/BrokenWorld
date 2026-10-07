package com.brokenworld.client;

import com.brokenworld.BrokenWorld;
import net.minecraft.client.Minecraft;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * What the game remembers outside any world, for the main menu: how far a world of this game ever broke (the highest
 * texture corruption seen, 0..1) and the name of the last world that reached the end. Kept in
 * brokenworld/memory.properties - delete it (or set brokenMenu = false) to mend the menu.
 */
public final class MenuMemory {
    private static boolean loaded;
    private static float level;
    private static String ended = "";

    private MenuMemory() {}

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("brokenworld").resolve("memory.properties");
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        Path f = file();
        if (!Files.isRegularFile(f)) return;
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(f)) {
            p.load(r);
            level = Math.max(0F, Math.min(1F, Float.parseFloat(p.getProperty("level", "0"))));
            ended = p.getProperty("ended", "");
        } catch (Exception e) {
            BrokenWorld.LOGGER.warn("[BrokenWorld] could not read {}: {}", f, e.toString());
        }
    }

    private static void save() {
        Path f = file();
        Properties p = new Properties();
        p.setProperty("level", Float.toString(level));
        p.setProperty("ended", ended);
        try {
            Files.createDirectories(f.getParent());
            try (Writer w = Files.newBufferedWriter(f)) {
                p.store(w, null);
            }
        } catch (Exception e) {
            BrokenWorld.LOGGER.warn("[BrokenWorld] could not write {}: {}", f, e.toString());
        }
    }

    /** How broken the menu is, 0..1. */
    public static float level() {
        load();
        return level;
    }

    /** The last world that reached the end ("" if none did). */
    public static String ended() {
        load();
        return ended;
    }

    /** A world is this broken now (remembered in steps, so the file is not written all the time). */
    public static void sawCorruption(float corruption) {
        load();
        float c = Math.min(1F, corruption);
        if (c >= level + 0.05F || (c >= 1F && level < 1F)) {
            level = c;
            save();
        }
    }

    /** A world reached the very end. */
    public static void worldEnded(String name) {
        load();
        level = 1F;
        ended = name == null ? "" : name;
        save();
    }
}
