package com.brokenworld;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The mod's settings: config/brokenworld-common.toml ("key = value" lines, "#" comments). Missing keys get their
 * defaults and the file is written back with every setting and its explanation, so it always shows all of them.
 */
public final class Config {
    /** One setting. */
    public abstract static class Value<T> {
        final String key;
        final String comment;
        final T defaultValue;
        volatile T value;

        Value(String key, T defaultValue, String comment) {
            this.key = key;
            this.defaultValue = defaultValue;
            this.value = defaultValue;
            this.comment = comment;
            ALL.add(this);
        }

        public T get() {
            return value;
        }

        abstract void parse(String text);

        String write() {
            return String.valueOf(value);
        }
    }

    public static final class BooleanValue extends Value<Boolean> {
        BooleanValue(String key, boolean defaultValue, String comment) {
            super(key, defaultValue, comment);
        }

        @Override
        void parse(String text) {
            if (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false")) value = Boolean.parseBoolean(text);
        }
    }

    public static final class DoubleValue extends Value<Double> {
        final double min, max;

        DoubleValue(String key, double defaultValue, double min, double max, String comment) {
            super(key, defaultValue, comment + "\nRange: " + min + " ~ " + max);
            this.min = min;
            this.max = max;
        }

        @Override
        void parse(String text) {
            try {
                value = Math.max(min, Math.min(max, Double.parseDouble(text)));
            } catch (NumberFormatException ignored) {
                // keep the default
            }
        }
    }

    private static final List<Value<?>> ALL = new ArrayList<>();

    // Defaults: the world breaks on day 2 and the finale comes on day 3.
    // Days are in-game days: sleeping through the night counts too.

    public static final DoubleValue MIN_DAYS_BEFORE_CHANGE = new DoubleValue("minDaysBeforeChange", 1.0, 0.0, 100.0,
            "Minimum in-game days of normal survival before the world 'breaks' (textures mix up, the silhouette appears)");

    public static final DoubleValue MAX_DAYS_BEFORE_CHANGE = new DoubleValue("maxDaysBeforeChange", 1.4, 0.0, 100.0,
            "Maximum in-game days before the world 'breaks'");

    public static final DoubleValue DAYS_TO_STAGE_2 = new DoubleValue("daysToStage2", 0.4, 0.0, 100.0,
            "In-game days from stage 1 to stage 2 (it gets closer)");

    public static final DoubleValue DAYS_TO_STAGE_3 = new DoubleValue("daysToStage3", 0.4, 0.0, 100.0,
            "In-game days from stage 2 to stage 3 (it stops hiding)");

    public static final DoubleValue DAYS_TO_FINALE = new DoubleValue("daysToFinale", 0.4, 0.0, 100.0,
            "In-game days from stage 3 to the finale (players fall through the world into the tunnel)");

    public static final BooleanValue FINALE = new BooleanValue("finale", true,
            "Enable the finale. If false, stage 3 simply goes on forever");

    public static final BooleanValue DELETE_WORLD_AT_END = new BooleanValue("deleteWorldAtEnd", true,
            "When the monster reaches you at the very end, the game leaves the world and DELETES it (singleplayer only).\n"
                    + "Set to false to only leave the world. On a server players are just disconnected.");

    public static final BooleanValue FAKE_CHAT = new BooleanValue("fakeChat", true,
            "Once the world is broken: somebody else 'joins' your world now and then and writes in chat");

    public static final BooleanValue BIGGER_HOUSE = new BooleanValue("biggerHouse", true,
            "Once the world is broken: after going far from home, your house is bigger on the inside");

    public static final DoubleValue APPEARANCE_FREQUENCY = new DoubleValue("appearanceFrequency", 1.0, 0.1, 20.0,
            "Multiplier for how often the silhouette and events happen (2.0 = twice as often)");

    public static final BooleanValue JUMPSCARES = new BooleanValue("jumpscares", true,
            "Allow the rare 'rush' jumpscare in stage 3");

    public static final BooleanValue WORLD_EVENTS = new BooleanValue("worldEvents", true,
            "Allow world events: torches going out, doors opening, footsteps, mining sounds");

    public static final BooleanValue FAKE_CRASH = new BooleanValue("fakeCrash", true,
            "From stage 2: once per stage the game seems to lose its connection - and comes back with things moved around");

    public static final BooleanValue HOME_VISITS = new BooleanValue("homeVisits", true,
            "From stage 2: while you are away, somebody visits your house (a sign, a diary, moved torches, open doors, footprints)");

    public static final BooleanValue BROKEN_SOUNDS = new BooleanValue("brokenSounds", true,
            "Sounds break along with the textures: they sound like other things, stutter, drop out, slow down");

    public static final BooleanValue HALLUCINATIONS = new BooleanValue("hallucinations", true,
            "From stage 2: now and then, for half a minute, the world looks like Minecraft Alpha (the game's own old textures) - then it snaps back");

    public static final BooleanValue ALPHA_TEXTURES = new BooleanValue("alphaTextures", true,
            "Hallucinations use the real Minecraft Alpha 1.2.6 textures: from your launcher's a1.2.6 if you have it, else\n"
                    + "downloaded once (about 1 MB) from Mojang's own servers into brokenworld/alpha/. false = only the built-in Programmer Art");

    public static final BooleanValue BROKEN_FONTS = new BooleanValue("brokenFonts", true,
            "The game's font breaks along with the world: letters swapped, turned to runes, flickering and shaking");

    public static final BooleanValue BROKEN_MENU = new BooleanValue("brokenMenu", true,
            "The main menu remembers how far a world broke (brokenworld/memory.properties) and breaks too: the logo, the\n"
                    + "panorama, the splash text, the buttons. Delete that file to mend it");

    private Config() {}

    /** Reads the file (if there is one) and writes it back complete. */
    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("brokenworld-common.toml");
        Map<String, String> read = new HashMap<>();
        if (Files.exists(file)) {
            try {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    String l = line.trim();
                    int eq = l.indexOf('=');
                    if (l.isEmpty() || l.startsWith("#") || l.startsWith("[") || eq < 0) continue;
                    String v = l.substring(eq + 1).trim();
                    int hash = v.indexOf('#');
                    if (hash >= 0) v = v.substring(0, hash).trim();
                    read.put(l.substring(0, eq).trim(), v.replace("\"", ""));
                }
            } catch (IOException e) {
                BrokenWorld.LOGGER.warn("[BrokenWorld] could not read {}: {}", file, e.toString());
            }
        }
        StringBuilder out = new StringBuilder("# Broken World settings\n");
        for (Value<?> v : ALL) {
            String text = read.get(v.key);
            if (text != null) v.parse(text.toLowerCase(Locale.ROOT));
            out.append('\n');
            for (String c : v.comment.split("\n")) out.append("#").append(c).append('\n');
            out.append(v.key).append(" = ").append(v.write()).append('\n');
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            BrokenWorld.LOGGER.warn("[BrokenWorld] could not write {}: {}", file, e.toString());
        }
    }
}
