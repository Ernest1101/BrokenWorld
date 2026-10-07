package com.brokenworld.client;

import com.brokenworld.BrokenWorld;
import com.brokenworld.Config;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The real Minecraft Alpha textures for the hallucinations - taken from the game itself, never shipped with this mod:
 * the Alpha 1.2.6 client the player already has (launcher "historical versions": versions/a1.2.6/a1.2.6.jar), or
 * else downloaded once from Mojang's own servers (like the launcher does) into brokenworld/alpha/. Only which tile is
 * which block lives here. Without it (offline, or config alphaTextures = false) the hallucination uses the game's
 * built-in "Programmer Art".
 */
public final class AlphaAssets {
    private static final String VERSION = "a1.2.6";
    private static final String MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    /** terrain.png tile (16x16 grid) -> block sprite names that tile becomes. */
    public static final Map<Integer, String[]> BLOCKS = new LinkedHashMap<>();
    /** gui/items.png tile -> item sprite name. */
    public static final Map<Integer, String> ITEMS = new LinkedHashMap<>();
    /** mob/... in the Alpha jar -> the mob texture it becomes. */
    public static final Map<String, String> MOBS = new LinkedHashMap<>();

    static {
        block(0, "grass_block_top"); block(1, "stone"); block(2, "dirt"); block(3, "grass_block_side");
        block(4, "oak_planks"); block(5, "smooth_stone_slab_side"); block(6, "smooth_stone"); block(7, "bricks");
        block(8, "tnt_side"); block(9, "tnt_top"); block(10, "tnt_bottom"); block(11, "cobweb"); block(12, "poppy");
        block(13, "dandelion"); block(15, "oak_sapling"); block(16, "cobblestone"); block(17, "bedrock");
        block(18, "sand"); block(19, "gravel"); block(20, "oak_log"); block(21, "oak_log_top"); block(22, "iron_block");
        block(23, "gold_block"); block(24, "diamond_block"); block(28, "red_mushroom"); block(29, "brown_mushroom");
        block(32, "gold_ore"); block(33, "iron_ore"); block(34, "coal_ore"); block(35, "bookshelf");
        block(36, "mossy_cobblestone"); block(37, "obsidian"); block(43, "crafting_table_top"); block(44, "furnace_front");
        block(45, "furnace_side"); block(48, "sponge"); block(49, "glass"); block(50, "diamond_ore");
        block(51, "redstone_ore"); block(52, "oak_leaves"); block(59, "crafting_table_side");
        block(60, "crafting_table_front"); block(61, "furnace_front_on"); block(64, "white_wool"); block(65, "spawner");
        block(66, "snow"); block(67, "ice"); block(68, "grass_block_snow"); block(69, "cactus_top");
        block(70, "cactus_side"); block(71, "cactus_bottom"); block(72, "clay"); block(73, "sugar_cane");
        block(74, "jukebox_side"); block(75, "jukebox_top"); block(80, "torch"); block(81, "oak_door_top");
        block(82, "iron_door_top"); block(83, "ladder"); block(86, "farmland_moist"); block(87, "farmland");
        for (int i = 0; i < 8; i++) block(88 + i, "wheat_stage" + i);
        block(96, "lever"); block(97, "oak_door_bottom"); block(98, "iron_door_bottom"); block(99, "redstone_torch");
        block(102, "pumpkin_top"); block(103, "netherrack"); block(104, "soul_sand"); block(105, "glowstone");
        block(112, "rail_corner"); block(115, "redstone_torch_off"); block(118, "pumpkin_side");
        block(119, "carved_pumpkin"); block(120, "jack_o_lantern"); block(128, "rail");

        String[] items = {
                "1:chainmail_helmet", "2:iron_helmet", "3:diamond_helmet", "4:golden_helmet", "5:flint_and_steel",
                "6:flint", "7:coal", "8:string", "9:wheat_seeds", "10:apple", "11:golden_apple", "12:egg",
                "14:snowball", "17:chainmail_chestplate", "18:iron_chestplate", "19:diamond_chestplate",
                "20:golden_chestplate", "21:bow", "22:brick", "23:iron_ingot", "24:feather", "25:wheat", "26:painting",
                "27:sugar_cane", "30:slime_ball", "33:chainmail_leggings", "34:iron_leggings", "35:diamond_leggings",
                "36:golden_leggings", "37:arrow", "39:gold_ingot", "40:gunpowder", "41:bread", "42:oak_sign",
                "43:oak_door", "44:iron_door", "49:chainmail_boots", "50:iron_boots", "51:diamond_boots",
                "52:golden_boots", "53:stick", "55:diamond", "56:redstone", "57:clay_ball", "58:paper", "59:book",
                "64:wooden_sword", "65:stone_sword", "66:iron_sword", "67:diamond_sword", "68:golden_sword",
                "69:fishing_rod", "71:bowl", "72:mushroom_stew", "73:glowstone_dust", "74:bucket", "75:water_bucket",
                "76:lava_bucket", "77:milk_bucket", "80:wooden_shovel", "81:stone_shovel", "82:iron_shovel",
                "83:diamond_shovel", "84:golden_shovel", "87:porkchop", "88:cooked_porkchop", "89:cod", "90:cooked_cod",
                "96:wooden_pickaxe", "97:stone_pickaxe", "98:iron_pickaxe", "99:diamond_pickaxe", "100:golden_pickaxe",
                "103:leather", "104:saddle", "112:wooden_axe", "113:stone_axe", "114:iron_axe", "115:diamond_axe",
                "116:golden_axe", "128:wooden_hoe", "129:stone_hoe", "130:iron_hoe", "131:diamond_hoe",
                "132:golden_hoe", "135:minecart", "136:oak_boat", "151:chest_minecart", "167:furnace_minecart",
                "240:music_disc_13", "241:music_disc_cat"};
        for (String s : items) {
            int c = s.indexOf(':');
            ITEMS.put(Integer.parseInt(s.substring(0, c)), s.substring(c + 1));
        }

        MOBS.put("mob/char.png", "textures/entity/player/wide/steve.png");
        MOBS.put("mob/pig.png", "textures/entity/pig/pig.png");
        MOBS.put("mob/cow.png", "textures/entity/cow/cow.png");
        MOBS.put("mob/chicken.png", "textures/entity/chicken.png");
        MOBS.put("mob/sheep.png", "textures/entity/sheep/sheep.png");
        MOBS.put("mob/sheep_fur.png", "textures/entity/sheep/sheep_fur.png");
        MOBS.put("mob/creeper.png", "textures/entity/creeper/creeper.png");
        MOBS.put("mob/skeleton.png", "textures/entity/skeleton/skeleton.png");
        MOBS.put("mob/zombie.png", "textures/entity/zombie/zombie.png");
        MOBS.put("mob/spider.png", "textures/entity/spider/spider.png");
        MOBS.put("mob/spider_eyes.png", "textures/entity/spider_eyes.png");
        MOBS.put("mob/slime.png", "textures/entity/slime/slime.png");
        MOBS.put("mob/ghast.png", "textures/entity/ghast/ghast.png");
        MOBS.put("mob/ghast_fire.png", "textures/entity/ghast/ghast_shooting.png");
    }

    /** Paint the sprite fully transparent (it did not exist then: the short grass, the ferns). */
    public static final int CLEAR = -2;
    private static final String[] FLOWERS = {"allium", "azure_bluet", "tulip", "oxeye_daisy", "cornflower",
            "lily_of_the_valley", "wither_rose", "sunflower", "lilac", "rose_bush", "peony", "pink_petals", "torchflower",
            "pitcher", "spore_blossom", "flowering_azalea"};

    /**
     * For the blocks Alpha never had: the Alpha tile of their "ancestor" (every log is the oak log, deepslate and the
     * other stones are stone, every ore is the nearest old ore...), {@link #CLEAR} for plants that did not exist, or -1
     * to leave it. Takes the sprite name without "block/".
     */
    public static int fallbackTile(String n) {
        if (n.contains("pumpkin_stem") || n.contains("melon_stem")) return CLEAR;
        if (n.equals("bamboo_stalk") || n.endsWith("bamboo_leaves") || n.equals("bamboo_singleleaf")
                || n.startsWith("bamboo_stage") || n.startsWith("kelp") || n.equals("vine")
                || n.contains("cave_vines") || n.startsWith("weeping_vines") || n.startsWith("twisting_vines")) return CLEAR;
        if (n.equals("grass") || n.equals("short_grass") || n.startsWith("tall_grass") || n.contains("fern")
                || n.equals("seagrass") || n.startsWith("tall_seagrass") || n.startsWith("dead_bush")) return CLEAR;
        if (n.endsWith("_log_top") || n.endsWith("_stem_top")) return 21;
        if (n.endsWith("_log") || n.endsWith("_stem") || n.startsWith("stripped_") || n.endsWith("_wood")
                || n.endsWith("_hyphae")) return 20;
        if (n.endsWith("_leaves") || n.startsWith("azalea") || n.equals("moss_block")) return 52;
        if (n.endsWith("_planks") || n.contains("_trapdoor") || n.equals("barrel_side")) return 4;
        if (n.contains("ore")) {
            if (n.contains("coal") || n.contains("lapis")) return 34;
            if (n.contains("iron") || n.contains("copper")) return 33;
            if (n.contains("gold")) return 32;
            if (n.contains("diamond") || n.contains("emerald")) return 50;
            if (n.contains("redstone")) return 51;
            if (n.contains("quartz")) return 103;
        }
        for (String f : FLOWERS) if (n.contains(f)) return (n.hashCode() & 1) == 0 ? 12 : 13;
        if (n.endsWith("_door_top")) return 81;
        if (n.endsWith("_door_bottom")) return 97;
        if (n.contains("torch")) return n.contains("redstone") ? 99 : 80;
        if (n.contains("rail")) return n.contains("corner") ? 112 : 128;
        if (n.contains("cobble")) return n.contains("mossy") ? 36 : 16;
        if (n.contains("deepslate") || n.contains("andesite") || n.contains("diorite") || n.contains("granite")
                || n.contains("tuff") || n.contains("calcite") || n.contains("dripstone") || n.contains("stone_brick")
                || n.equals("smooth_basalt") || n.startsWith("stone_") || n.endsWith("_stone")) return 1;
        if (n.contains("sandstone") || n.contains("sand")) return 18;
        if (n.contains("gravel")) return 19;
        if (n.contains("podzol") || n.contains("mycelium") || n.contains("dirt") || n.contains("mud")
                || n.contains("rooted")) return 2;
        if (n.contains("wool") || n.contains("carpet")) return 64;
        if (n.contains("concrete") || n.contains("terracotta")) return 72;
        if (n.contains("glass")) return 49;
        if (n.contains("ice")) return 67;
        if (n.contains("snow")) return 66;
        if (n.contains("netherrack") || n.contains("nylium")) return 103;
        if (n.contains("obsidian")) return 37;
        if (n.contains("bookshelf")) return 35;
        if (n.contains("brick")) return 7;
        if (n.contains("bamboo") || n.contains("kelp")) return 73;
        if (n.contains("pumpkin")) return 118;
        if (n.contains("melon")) return 118;
        return -1;
    }

    /** The old game's "unused" fill (purple, with a darker grid) - read as transparent. */
    public static boolean isPlaceholder(int abgr) {
        int r = abgr & 0xFF, g = (abgr >> 8) & 0xFF, b = (abgr >> 16) & 0xFF;
        return (r == 214 && g == 127 && b == 255) || (r == 107 && g == 63 && b == 127);
    }

    private static void block(int tile, String... names) {
        BLOCKS.put(tile, names);
    }

    private static volatile boolean fetching;

    private AlphaAssets() {}

    private static Path gameDir() {
        return Minecraft.getInstance().gameDirectory.toPath();
    }

    /** The Alpha client jar, if the player has it (or it was downloaded before). */
    @Nullable
    public static Path jar() {
        Path[] places = {
                gameDir().resolve("versions").resolve(VERSION).resolve(VERSION + ".jar"),
                gameDir().resolve("brokenworld").resolve("alpha").resolve(VERSION + ".jar")};
        for (Path p : places) if (Files.isRegularFile(p)) return p;
        return null;
    }

    /** Gets the Alpha jar ready in the background (once), so it is there when the world first slips. */
    public static void prefetch() {
        if (fetching || !Config.ALPHA_TEXTURES.get() || jar() != null) return;
        fetching = true;
        Thread t = new Thread(AlphaAssets::download, "BrokenWorld Alpha download");
        t.setDaemon(true);
        t.start();
    }

    private static void download() {
        try {
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
                    .followRedirects(HttpClient.Redirect.NORMAL).build();
            JsonObject manifest = JsonParser.parseString(get(http, MANIFEST)).getAsJsonObject();
            String versionUrl = null;
            for (var v : manifest.getAsJsonArray("versions")) {
                if (VERSION.equals(v.getAsJsonObject().get("id").getAsString())) {
                    versionUrl = v.getAsJsonObject().get("url").getAsString();
                }
            }
            if (versionUrl == null) throw new IllegalStateException(VERSION + " not in the version manifest");
            JsonObject client = JsonParser.parseString(get(http, versionUrl)).getAsJsonObject()
                    .getAsJsonObject("downloads").getAsJsonObject("client");
            Path target = gameDir().resolve("brokenworld").resolve("alpha").resolve(VERSION + ".jar");
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling(VERSION + ".jar.part");
            HttpResponse<Path> r = http.send(HttpRequest.newBuilder(URI.create(client.get("url").getAsString()))
                    .timeout(Duration.ofSeconds(60)).build(), HttpResponse.BodyHandlers.ofFile(tmp));
            if (r.statusCode() != 200) throw new IllegalStateException("HTTP " + r.statusCode());
            String sha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(tmp)));
            if (!sha1.equalsIgnoreCase(client.get("sha1").getAsString())) {
                Files.deleteIfExists(tmp);
                throw new IllegalStateException("checksum mismatch");
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            BrokenWorld.LOGGER.info("[BrokenWorld] Minecraft {} textures ready ({})", VERSION, target);
        } catch (Exception e) {
            BrokenWorld.LOGGER.info("[BrokenWorld] no Minecraft {} textures, using Programmer Art: {}", VERSION, e.toString());
        } finally {
            fetching = false;
        }
    }

    private static String get(HttpClient http, String url) throws Exception {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).build(),
                HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) throw new IllegalStateException("HTTP " + r.statusCode() + " for " + url);
        return r.body();
    }

    /** The jar's images by path (terrain.png, gui/items.png, mob/...), or an empty map. Caller closes them. */
    public static Map<String, NativeImage> read() {
        Map<String, NativeImage> out = new LinkedHashMap<>();
        Path jar = jar();
        if (jar == null || !Config.ALPHA_TEXTURES.get()) return out;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (String name : new String[]{"terrain.png", "gui/items.png", "gui/gui.png", "gui/icons.png",
                    "environment/clouds.png", "terrain/sun.png", "environment/rain.png", "environment/snow.png",
                    "misc/grasscolor.png", "misc/foliagecolor.png"}) {
                readEntry(zip, name, out);
            }
            for (String name : MOBS.keySet()) readEntry(zip, name, out);
        } catch (Exception e) {
            BrokenWorld.LOGGER.warn("[BrokenWorld] could not read {}: {}", jar, e.toString());
        }
        return out;
    }

    private static void readEntry(ZipFile zip, String name, Map<String, NativeImage> out) throws Exception {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) return;
        try (InputStream in = zip.getInputStream(entry)) {
            out.put(name, NativeImage.read(in));
        }
    }

    /** One 16x16 tile of a 256x256 sheet. */
    public static NativeImage tile(NativeImage sheet, int index) {
        int size = sheet.getWidth() / 16;
        NativeImage t = new NativeImage(size, size, true);
        sheet.copyRect(t, (index % 16) * size, (index / 16) * size, 0, 0, size, size, false, false);
        return t;
    }

    /**
     * Old 64x32 humanoid skins (Alpha's zombie) in today's 64x64 layout: the top half as it was, and the left arm and
     * leg (which the old game drew from the right ones) copied into their own places; the rest stays transparent.
     */
    public static NativeImage widenHumanoid(NativeImage old) {
        NativeImage img = new NativeImage(64, 64, true);
        old.copyRect(img, 0, 0, 0, 0, 64, 32, false, false);
        old.copyRect(img, 0, 16, 16, 48, 16, 16, false, false);  // right leg -> left leg
        old.copyRect(img, 40, 16, 32, 48, 16, 16, false, false); // right arm -> left arm
        return img;
    }
}
