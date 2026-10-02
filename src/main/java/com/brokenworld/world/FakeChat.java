package com.brokenworld.world;

import com.brokenworld.BrokenWorld;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Somebody writes to you who should not be there.
 * <ul>
 *   <li><b>Alone in the world:</b> a stranger "joins" - the yellow join message, a name in the TAB list, a few lines
 *       in chat (creepier with every stage), echoing back whatever you write, then leaving again. In stage 3 it
 *       sometimes writes with your own name.</li>
 *   <li><b>With other players:</b> it pretends to be one of them. It whispers to you only (like /msg), in that
 *       player's own way of writing - lower case or not, full stops or not, the ")" they put everywhere - and
 *       sometimes repeats things they really wrote before, out of place. Answer, and it answers with their words.
 *       Your friend never wrote any of it, and nobody else sees it.</li>
 * </ul>
 */
public final class FakeChat {
    private static final String[] NAMES = {"Hollow_", "nobody", "xX_null_Xx", "Player_0", "your_friend", "Wanderer",
            "lost_one", "Seeker", "m1rror", "unknown", "Watcher", "the_other"};
    /** Conversations per stage: keys of lines (lang files: fakechat.brokenworld.*). */
    private static final String[][][] SCRIPTS = {
            {}, // stage 0: nobody
            {{"hi", "you_too"}, {"anyone"}, {"trees"}, {"hi", "where"}},
            {{"see_you", "dont_turn"}, {"why_silent", "at_house"}, {"behind"}, {"hi", "you_too", "not_alone"}},
            {{"me_too", "deleted"}, {"soon"}, {"name"}, {"behind", "dont_turn"}, {"pit", "lies"}},
    };
    /** What the impersonated friend answers when you write, if they have no old words to use. */
    private static final String[] ANSWERS = {"dots", "what", "didnt_write", "look_back"};
    private static final int MEMORY = 40;
    private static final int MAX_ANSWERS = 2;
    private static final RandomSource RANDOM = RandomSource.create();

    private static final class Visit {
        final String name;
        final long until;
        @Nullable final UUID impersonated;
        int answers;

        Visit(String name, long until, @Nullable UUID impersonated) {
            this.name = name;
            this.until = until;
            this.impersonated = impersonated;
        }
    }

    private static final Map<UUID, Visit> VISITS = new HashMap<>();
    /** What every real player wrote lately, to copy how they write. */
    private static final Map<UUID, Deque<String>> SAID = new HashMap<>();
    private static Map<String, Map<String, String>> langs;

    private FakeChat() {}

    public static void reset() {
        VISITS.clear();
        SAID.clear();
    }

    /** The same stranger in a world. */
    public static String nameFor(MinecraftServer server) {
        long salt = Director.salt(server);
        return NAMES[(int) Math.floorMod(salt, (long) NAMES.length)];
    }

    public static boolean isVisiting(ServerPlayer player) {
        Visit v = VISITS.get(player.getUUID());
        return v != null && player.server.overworld().getGameTime() < v.until;
    }

    public static void visit(ServerPlayer player, int stage) {
        if (isVisiting(player)) return;
        List<ServerPlayer> others = new ArrayList<>();
        for (ServerPlayer p : player.server.getPlayerList().getPlayers()) {
            if (p != player && !p.isSpectator()) others.add(p);
        }
        if (others.isEmpty()) stranger(player, stage);
        else impersonate(player, pickFriend(others), stage);
    }

    // ------------------------------------------------------------------ alone: a stranger joins

    private static void stranger(ServerPlayer player, int stage) {
        MinecraftServer server = player.server;
        boolean asYou = stage >= 3 && RANDOM.nextInt(4) == 0;
        String name = asYou ? player.getGameProfile().getName() : nameFor(server);
        GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(("brokenworld:" + name).getBytes()), name);
        String[] lines = script(stage);

        int t = 0;
        if (!asYou) {
            showInTab(player, profile, true);
            send(player, Component.translatable("multiplayer.player.joined", name).withStyle(ChatFormatting.YELLOW));
            t += 60 + RANDOM.nextInt(80);
        }
        for (String line : lines) {
            String key = "fakechat.brokenworld." + line;
            Scheduler.schedule(t, () -> say(player, name, Component.translatable(key, player.getGameProfile().getName())));
            t += 60 + RANDOM.nextInt(120);
        }
        int end = t + 200 + RANDOM.nextInt(400); // stays a little, so you can answer
        if (!asYou) {
            Scheduler.schedule(end, () -> {
                send(player, Component.translatable("multiplayer.player.left", name).withStyle(ChatFormatting.YELLOW));
                showInTab(player, profile, false);
            });
        }
        VISITS.put(player.getUUID(), new Visit(name, server.overworld().getGameTime() + end, null));
        BrokenWorld.LOGGER.info("[BrokenWorld] {} has a visitor in chat: {}", player.getName().getString(), name);
    }

    // ------------------------------------------------------------------ with others: it is your friend

    /** Prefers someone whose way of writing we know. */
    private static ServerPlayer pickFriend(List<ServerPlayer> others) {
        List<ServerPlayer> talkers = others.stream().filter(p -> SAID.containsKey(p.getUUID())).toList();
        List<ServerPlayer> pool = talkers.isEmpty() ? others : talkers;
        return pool.get(RANDOM.nextInt(pool.size()));
    }

    private static void impersonate(ServerPlayer player, ServerPlayer friend, int stage) {
        String name = friend.getGameProfile().getName();
        List<String> theirs = new ArrayList<>(SAID.getOrDefault(friend.getUUID(), new ArrayDeque<>()));
        WritingStyle style = WritingStyle.of(theirs);
        String lang = player.getLanguage();

        List<String> lines = new ArrayList<>();
        for (String key : script(stage)) {
            lines.add(style.apply(line(lang, "fakechat.brokenworld." + key, player.getGameProfile().getName()), RANDOM));
        }
        if (!theirs.isEmpty() && RANDOM.nextBoolean()) {
            // something they really said, out of place
            lines.add(RANDOM.nextInt(lines.size() + 1), theirs.get(RANDOM.nextInt(theirs.size())));
        }
        int t = 20 + RANDOM.nextInt(60);
        for (String text : lines) {
            Scheduler.schedule(t, () -> whisper(player, name, text));
            t += 50 + RANDOM.nextInt(110);
        }
        int end = t + 200 + RANDOM.nextInt(400);
        VISITS.put(player.getUUID(), new Visit(name, player.server.overworld().getGameTime() + end, friend.getUUID()));
        BrokenWorld.LOGGER.info("[BrokenWorld] {} gets whispers 'from' {}", player.getName().getString(), name);
    }

    /** "<name> whispers to you: ..." - only the player sees it. */
    private static void whisper(ServerPlayer player, String name, String text) {
        send(player, Component.translatable("commands.message.display.incoming", name, text)
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    // ------------------------------------------------------------------ chat

    /** Remembers what real players write, and answers for the visitor. */
    public static void onChat(ServerPlayer player, String message) {
        remember(player, message);
        Visit v = VISITS.get(player.getUUID());
        if (v == null || !isVisiting(player)) return;
        if (v.impersonated == null) {
            // the stranger writes your own words back to you
            Scheduler.schedule(40 + RANDOM.nextInt(60), () -> say(player, v.name, Component.literal(message)));
            return;
        }
        if (v.answers++ >= MAX_ANSWERS) return; // then it goes quiet
        List<String> theirs = new ArrayList<>(SAID.getOrDefault(v.impersonated, new ArrayDeque<>()));
        theirs.remove(message);
        String answer;
        if (!theirs.isEmpty() && RANDOM.nextBoolean()) {
            answer = theirs.get(RANDOM.nextInt(theirs.size())); // their own words
        } else {
            String key = "fakechat.brokenworld.answer." + ANSWERS[RANDOM.nextInt(ANSWERS.length)];
            answer = WritingStyle.of(theirs).apply(line(player.getLanguage(), key, player.getGameProfile().getName()), RANDOM);
        }
        String reply = answer;
        Scheduler.schedule(40 + RANDOM.nextInt(80), () -> whisper(player, v.name, reply));
    }

    private static void remember(ServerPlayer player, String message) {
        Deque<String> said = SAID.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        said.addLast(message);
        while (said.size() > MEMORY) said.removeFirst();
    }

    /** For tests and commands: pretend the player wrote this. */
    public static void rememberForTests(ServerPlayer player, String message) {
        remember(player, message);
    }

    private static String[] script(int stage) {
        String[][] scripts = SCRIPTS[Math.max(1, Math.min(3, stage))];
        return scripts[RANDOM.nextInt(scripts.length)];
    }

    private static void say(ServerPlayer player, String name, Component text) {
        send(player, Component.translatable("chat.type.text", name, text));
    }

    private static void send(ServerPlayer player, Component message) {
        if (player.hasDisconnected()) return;
        player.sendSystemMessage(message);
    }

    /** Adds / removes a TAB list entry for the stranger (a player object that is never put into the world). */
    private static void showInTab(ServerPlayer player, GameProfile profile, boolean show) {
        if (player.hasDisconnected()) return;
        if (show) {
            ServerPlayer ghost = new ServerPlayer(player.server, player.server.overworld(), profile);
            player.connection.send(new ClientboundPlayerInfoUpdatePacket(EnumSet.of(
                    ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY), List.of(ghost)));
        } else {
            player.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(profile.getId())));
        }
    }

    // ------------------------------------------------------------------ text in the reader's language

    /**
     * A line as text in the given language. The server cannot translate on the client's side here (the text gets
     * restyled first), so it reads the mod's own lang files from the jar - this works on dedicated servers too.
     */
    /** A line from the lang files in this player's language ("%s" = their name). */
    public static String text(ServerPlayer player, String key) {
        return line(player.getLanguage(), key, player.getGameProfile().getName());
    }

    private static String line(String language, String key, String playerName) {
        if (langs == null) langs = loadLangs();
        String lang = language != null && language.toLowerCase(Locale.ROOT).startsWith("ru") ? "ru_ru" : "en_us";
        String text = langs.getOrDefault(lang, Map.of()).getOrDefault(key,
                langs.getOrDefault("en_us", Map.of()).getOrDefault(key, "..."));
        return text.replace("%s", playerName);
    }

    private static Map<String, Map<String, String>> loadLangs() {
        Map<String, Map<String, String>> result = new HashMap<>();
        for (String lang : new String[]{"en_us", "ru_ru"}) {
            Map<String, String> map = new HashMap<>();
            try {
                Path path = ModList.get().getModFileById(BrokenWorld.MODID).getFile()
                        .findResource("assets", BrokenWorld.MODID, "lang", lang + ".json");
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    json.entrySet().forEach(e -> map.put(e.getKey(), e.getValue().getAsString()));
                }
            } catch (Exception e) {
                BrokenWorld.LOGGER.warn("[BrokenWorld] could not read lang {}: {}", lang, e.toString());
            }
            result.put(lang, map);
        }
        return result;
    }

    // ------------------------------------------------------------------ copying how somebody writes

    /** How a player writes, guessed from their messages. */
    private record WritingStyle(boolean lowerCase, boolean noFullStops, String smiley) {
        static WritingStyle of(List<String> messages) {
            if (messages.isEmpty()) return new WritingStyle(true, true, ""); // how most people chat
            int upper = 0, stops = 0, smileys = 0, doubles = 0;
            for (String m : messages) {
                if (!m.equals(m.toLowerCase(Locale.ROOT))) upper++;
                if (m.endsWith(".") || m.endsWith("!") || m.endsWith("?")) stops++;
                if (m.contains(")")) smileys++;
                if (m.contains("))")) doubles++;
            }
            int n = messages.size();
            String smiley = smileys * 3 >= n ? (doubles * 2 >= smileys ? "))" : ")") : "";
            return new WritingStyle(upper * 3 < n, stops * 3 < n, smiley);
        }

        String apply(String text, RandomSource random) {
            String t = text;
            if (lowerCase) {
                t = t.toLowerCase(Locale.ROOT);
            } else if (!t.isEmpty()) {
                t = Character.toUpperCase(t.charAt(0)) + t.substring(1);
            }
            if (noFullStops) {
                while (!t.isEmpty() && ".!".indexOf(t.charAt(t.length() - 1)) >= 0) t = t.substring(0, t.length() - 1);
            }
            if (!smiley.isEmpty() && random.nextInt(3) == 0 && !t.endsWith("?")) t = t + smiley;
            return t;
        }
    }
}
