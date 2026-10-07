package com.brokenworld.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What a block looked like in Minecraft Alpha, for a hallucination: blocks Alpha had stay; today's blocks are drawn
 * as their Alpha "ancestor" (every log an oak log, every stone stone, every stair an oak or a cobblestone stair...);
 * what did not exist at all - tall grass, ferns, bamboo, vines, kelp, coral, lanterns, trapdoors, most flowers - is
 * not drawn. Only the drawing changes: the blocks are still there.
 */
public final class AlphaBlocks {
    /** Every block of Alpha 1.2.6, by today's name. */
    private static final Set<String> ALPHA = Set.of("air", "cave_air", "void_air", "stone", "grass_block", "dirt",
            "cobblestone", "oak_planks", "oak_sapling", "bedrock", "water", "lava", "sand", "gravel", "gold_ore",
            "iron_ore", "coal_ore", "oak_log", "oak_leaves", "sponge", "glass", "white_wool", "dandelion", "poppy",
            "brown_mushroom", "red_mushroom", "gold_block", "iron_block", "smooth_stone_slab", "bricks", "tnt",
            "bookshelf", "mossy_cobblestone", "obsidian", "torch", "wall_torch", "fire", "spawner", "oak_stairs",
            "chest", "redstone_wire", "diamond_ore", "diamond_block", "crafting_table", "wheat", "farmland", "furnace",
            "oak_sign", "oak_wall_sign", "oak_door", "ladder", "rail", "cobblestone_stairs", "lever",
            "stone_pressure_plate", "iron_door", "oak_pressure_plate", "redstone_ore", "redstone_torch",
            "redstone_wall_torch", "stone_button", "snow", "ice", "snow_block", "cactus", "clay", "sugar_cane",
            "jukebox", "oak_fence", "pumpkin", "carved_pumpkin", "netherrack", "soul_sand", "glowstone",
            "nether_portal", "jack_o_lantern");
    /** The mobs of Alpha 1.2.6. */
    private static final Set<String> ALPHA_MOBS = Set.of("pig", "cow", "sheep", "chicken", "creeper", "skeleton",
            "zombie", "spider", "slime", "ghast", "zombified_piglin", "giant");
    private static final String[] WOODS = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove",
            "cherry", "bamboo", "crimson", "warped"};
    private static final String HIDE = "";
    private static final Map<BlockState, BlockState> CACHE = new ConcurrentHashMap<>();

    private AlphaBlocks() {}

    /** The block as Alpha would draw it: the same state, another block's state, or air (not drawn). */
    public static BlockState of(BlockState state) {
        return CACHE.computeIfAbsent(state, AlphaBlocks::compute);
    }

    /** Whether this entity did not exist in Alpha (and is not drawn meanwhile). Players and the silhouette stay. */
    public static boolean hidden(Entity entity) {
        if (!(entity instanceof LivingEntity) || entity instanceof Player) return false;
        ResourceLocation key = EntityType.getKey(entity.getType());
        return key.getNamespace().equals("minecraft") && !ALPHA_MOBS.contains(key.getPath());
    }

    private static BlockState compute(BlockState state) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!key.getNamespace().equals("minecraft")) return state;
        String n = key.getPath();
        if (ALPHA.contains(n)) return state;
        String to = ancestor(n);
        if (to == null) {
            if (state.getRenderShape() != RenderShape.MODEL) return state; // invisible, or drawn by its block entity
            to = state.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) ? "stone" : HIDE;
        }
        if (to.equals(HIDE)) return Blocks.AIR.defaultBlockState();
        return copy(state, BuiltInRegistries.BLOCK.get(new ResourceLocation("minecraft", to)));
    }

    private static boolean wooden(String n) {
        for (String w : WOODS) if (n.startsWith(w + "_")) return true;
        return false;
    }

    private static boolean any(String n, String... names) {
        for (String s : names) if (n.equals(s)) return true;
        return false;
    }

    private static boolean contains(String n, String... parts) {
        for (String s : parts) if (n.contains(s)) return true;
        return false;
    }

    /** Today's block name -> its Alpha ancestor's, HIDE, or null (stone if it is a full block, else hidden). */
    private static String ancestor(String n) {
        // did not exist
        if (any(n, "grass", "tall_grass", "fern", "large_fern", "dead_bush", "seagrass", "tall_seagrass", "kelp",
                "kelp_plant", "bamboo", "bamboo_sapling", "vine", "glow_lichen", "lily_pad", "sweet_berry_bush",
                "hanging_roots", "spore_blossom", "azalea", "flowering_azalea", "small_dripleaf", "big_dripleaf",
                "big_dripleaf_stem", "pointed_dripstone", "sea_pickle", "pink_petals", "pitcher_plant", "pitcher_crop",
                "torchflower", "torchflower_crop", "mangrove_propagule", "mangrove_roots", "sculk_vein", "cobweb",
                "scaffolding", "chain", "lantern", "soul_lantern", "campfire", "soul_campfire", "frogspawn",
                "sunflower", "lilac", "rose_bush", "peony", "brown_mushroom_block", "red_mushroom_block",
                "mushroom_stem", "nether_sprouts", "crimson_roots", "warped_roots", "crimson_fungus", "warped_fungus",
                "beetroots", "carrots", "potatoes", "melon_stem", "attached_melon_stem", "pumpkin_stem",
                "attached_pumpkin_stem", "cocoa", "flower_pot", "repeater", "comparator", "tripwire",
                "tripwire_hook", "lightning_rod", "end_rod", "bell", "grindstone", "stonecutter", "cauldron",
                "water_cauldron", "lava_cauldron", "powder_snow_cauldron", "hopper", "brewing_stand", "anvil",
                "chipped_anvil", "damaged_anvil", "iron_bars", "turtle_egg", "sniffer_egg", "sculk_sensor",
                "calibrated_sculk_sensor", "sculk_shrieker", "composter", "lectern", "enchanting_table")
                || contains(n, "coral", "cave_vines", "weeping_vines", "twisting_vines", "_carpet", "_trapdoor",
                "_fence_gate", "candle", "amethyst_cluster", "_bud", "_hanging_sign")
                || n.startsWith("potted_")) return HIDE;
        // the two flowers Alpha had
        if (any(n, "allium", "azure_bluet", "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy",
                "cornflower", "lily_of_the_valley", "wither_rose", "blue_orchid")) {
            return (n.hashCode() & 1) == 0 ? "dandelion" : "poppy";
        }
        if (n.endsWith("_sapling")) return "oak_sapling";
        if (n.endsWith("_leaves")) return "oak_leaves";
        if (n.endsWith("_log") || n.endsWith("_wood") || n.endsWith("_hyphae") || n.endsWith("_stem")
                || n.startsWith("stripped_") || n.equals("bamboo_block")) return "oak_log";
        if (n.endsWith("_planks") || n.equals("bamboo_mosaic")) return "oak_planks";
        if (n.endsWith("_stairs")) return wooden(n) ? "oak_stairs" : "cobblestone_stairs";
        if (n.endsWith("_slab")) return "smooth_stone_slab";
        if (n.endsWith("_fence")) return "oak_fence";
        if (n.endsWith("_door")) return wooden(n) ? "oak_door" : "iron_door";
        if (n.endsWith("_pressure_plate")) return wooden(n) ? "oak_pressure_plate" : "stone_pressure_plate";
        if (n.endsWith("_button")) return "stone_button";
        if (n.endsWith("_wall_sign")) return "oak_wall_sign";
        if (n.endsWith("_sign")) return "oak_sign";
        if (n.endsWith("_wall_torch")) return "wall_torch";
        if (n.endsWith("_torch")) return "torch";
        if (n.endsWith("_fire")) return "fire";
        if (n.endsWith("_wall")) return n.contains("mossy") ? "mossy_cobblestone" : "cobblestone";
        if (n.contains("rail")) return "rail";
        if (n.endsWith("_ore")) {
            if (n.startsWith("nether_")) return "netherrack";
            if (n.contains("coal") || n.contains("lapis")) return "coal_ore";
            if (n.contains("iron") || n.contains("copper")) return "iron_ore";
            if (n.contains("gold")) return "gold_ore";
            if (n.contains("redstone")) return "redstone_ore";
            if (n.contains("diamond") || n.contains("emerald")) return "diamond_ore";
        }
        if (any(n, "emerald_block", "lapis_block")) return "diamond_block";
        if (n.contains("copper") || any(n, "raw_iron_block", "netherite_block")) return "iron_block";
        if (n.equals("raw_gold_block")) return "gold_block";
        if (any(n, "coal_block", "crying_obsidian")) return "obsidian";
        if (n.equals("redstone_block")) return "bricks";
        if (any(n, "coarse_dirt", "rooted_dirt", "mud", "packed_mud", "dirt_path", "muddy_mangrove_roots")) return "dirt";
        if (any(n, "podzol", "mycelium", "moss_block")) return "grass_block";
        if (any(n, "red_sand", "suspicious_sand") || n.contains("sandstone")) return "sand";
        if (n.equals("suspicious_gravel")) return "gravel";
        if (n.contains("terracotta")) return "stone";
        if (n.contains("wool") || n.contains("concrete")) return "white_wool";
        if (n.contains("glass")) return "glass";
        if (any(n, "packed_ice", "blue_ice", "frosted_ice")) return "ice";
        if (n.equals("powder_snow")) return "snow_block";
        if (any(n, "shroomlight", "sea_lantern") || n.endsWith("froglight")) return "glowstone";
        if (n.equals("soul_soil")) return "soul_sand";
        if (any(n, "magma_block", "crimson_nylium", "warped_nylium", "nether_wart_block", "warped_wart_block")
                || n.contains("nether_brick") || n.contains("blackstone") || n.contains("basalt")) return "netherrack";
        if (n.contains("mossy")) return "mossy_cobblestone";
        if (n.equals("cobbled_deepslate")) return "cobblestone";
        if (any(n, "blast_furnace", "smoker", "dispenser", "dropper", "observer")) return "furnace";
        if (any(n, "smithing_table", "fletching_table", "cartography_table", "loom")) return "crafting_table";
        if (n.equals("note_block")) return "jukebox";
        if (n.equals("chiseled_bookshelf")) return "bookshelf";
        if (any(n, "barrel", "beehive", "bee_nest")) return "oak_planks";
        if (n.equals("melon")) return "pumpkin";
        if (n.equals("hay_block")) return "sponge";
        return null;
    }

    /** `to` with every property `from` shares with it (facing, half, axis, waterlogged...). */
    private static BlockState copy(BlockState from, Block to) {
        BlockState out = to.defaultBlockState();
        for (Property<?> p : out.getProperties()) {
            for (Property<?> q : from.getProperties()) {
                if (q.getName().equals(p.getName())) {
                    out = set(out, p, name(from, q));
                    break;
                }
            }
        }
        return out;
    }

    private static <T extends Comparable<T>> String name(BlockState state, Property<T> p) {
        return p.getName(state.getValue(p));
    }

    private static <T extends Comparable<T>> BlockState set(BlockState state, Property<T> p, String value) {
        return p.getValue(value).map(v -> state.setValue(p, v)).orElse(state);
    }
}
