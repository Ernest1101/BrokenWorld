package com.brokenworld.client;

import com.brokenworld.BrokenWorld;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.sounds.SoundEvents;

import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;

/**
 * "You are playing Minecraft Alpha now": for a while the world looks like the old game - old block, item and mob
 * textures, "Minecraft Alpha v1.2.6" in the corner, an old C418 track - and none of it is broken: no shuffled
 * textures, no wrong sounds. Then it snaps back.
 *
 * The old textures come from the game, never from this mod: the real Minecraft Alpha 1.2.6 textures where the
 * player's game has Alpha (see AlphaAssets), on top of the built-in "Programmer Art" pack (the pre-2019 textures
 * that ship with every Minecraft). Blocks and items are painted straight into the block atlas (and painted
 * back afterwards); mob textures are swapped like the faceless mobs do.
 */
public final class Hallucination {
    public static final String VERSION_TEXT = "Minecraft Alpha v1.2.6";
    private static final String PACK = "programmer_art";

    private static volatile int ticksLeft; // (read by the chunk builders too)
    private static final Set<TextureAtlasSprite> PAINTED = new LinkedHashSet<>();
    private static final Set<ResourceLocation> SWAPPED_ENTITIES = new LinkedHashSet<>();
    /** Whether this one is the real Alpha (or only the built-in old textures). */
    private static boolean alphaUsed;
    private static SoundInstance music;
    /** The player's smooth lighting setting, while Alpha's flat light is on. */
    private static Boolean savedSmoothLight;
    /** Alpha's rippling water and boiling lava, painted over today's every tick while it lasts. */
    private static AlphaFluids water;
    private static AlphaFluids lava;
    /** Every block sprite the game draws with (worked out once). */
    private static Set<ResourceLocation> blockSprites;

    private Hallucination() {}

    public static boolean active() {
        return ticksLeft > 0;
    }

    public static void start(int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        boolean wasActive = active();
        ticksLeft = Math.max(ticksLeft, ticks);
        if (wasActive) return;
        alphaUsed = false;
        // Alpha's light: flat and hard, no soft shadows in the corners (the chunks are rebuilt right below)
        savedSmoothLight = mc.options.ambientOcclusion().get();
        mc.options.ambientOcclusion().set(false);
        paintOldTextures(mc);
        TextureShuffle.rebuildVisibleChunks(); // the world is whole again, for now
        mc.getMusicManager().stopPlaying();
        music = SimpleSoundInstance.forMusic(SoundEvents.MUSIC_GAME.value());
        mc.getSoundManager().play(music);
        BrokenWorld.LOGGER.info("[BrokenWorld] hallucination ({}): {} textures, {} mobs",
                alphaUsed ? "Minecraft Alpha" : "Programmer Art", PAINTED.size(), SWAPPED_ENTITIES.size());
    }

    public static void tick() {
        devShot();
        if (ticksLeft > 0) paintFluids();
        if (ticksLeft > 0 && --ticksLeft == 0) stop();
    }

    /** (After the game's own animation step of this tick, so ours is the frame that shows.) */
    private static void paintFluids() {
        if (water == null) return;
        Minecraft mc = Minecraft.getInstance();
        TextureAtlas atlas = mc.getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
        int mips = Math.max(0, mc.options.mipmapLevels().get());
        water.tick();
        lava.tick();
        paint(atlas, new ResourceLocation("minecraft", "block/water_still"), water.frame(16), mips);
        paint(atlas, new ResourceLocation("minecraft", "block/water_flow"), water.frame(32), mips);
        paint(atlas, new ResourceLocation("minecraft", "block/lava_still"), lava.frame(16), mips);
        paint(atlas, new ResourceLocation("minecraft", "block/lava_flow"), lava.frame(32), mips);
    }

    /** Development only (-Dbrokenworld.shot): screenshots 3 s into a hallucination and 3 s after it, to compare. */
    private static int devTicks = -1;

    private static void devShot() {
        if (System.getProperty("brokenworld.shot") == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (active() && devTicks < 0) devTicks = 0;
        if (devTicks < 0) return;
        devTicks++;
        if (devTicks == 60 || (!active() && devTicks > 60 && devTicks % 1000 == 0)) {
            net.minecraft.client.Screenshot.grab(mc.gameDirectory, mc.getMainRenderTarget(), msg -> { });
        }
        if (!active() && devTicks < 1000) devTicks = 1000 - 60; // 3 s after the end
    }

    /** Back to now, all at once. */
    public static void stop() {
        ticksLeft = 0;
        Minecraft mc = Minecraft.getInstance();
        if (!PAINTED.isEmpty()) {
            TextureAtlas atlas = mc.getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
            atlas.bind();
            for (TextureAtlasSprite sprite : PAINTED) sprite.uploadFirstFrame();
            PAINTED.clear();
        }
        for (ResourceLocation tex : SWAPPED_ENTITIES) mc.getTextureManager().release(tex);
        SWAPPED_ENTITIES.clear();
        water = null;
        lava = null;
        AlphaColors.restore(mc);
        if (savedSmoothLight != null) {
            mc.options.ambientOcclusion().set(savedSmoothLight);
            savedSmoothLight = null;
        }
        if (music != null) {
            mc.getSoundManager().stop(music);
            music = null;
        }
        if (mc.level != null) TextureShuffle.rebuildVisibleChunks();
    }

    /** The textures were just reloaded (F3+T): the atlas is new and already right; only the mobs need putting back. */
    public static void onResourcesReloaded() {
        PAINTED.clear();
        blockSprites = null;
        if (active()) stop();
    }

    /** The version text the old game showed in the top left corner. */
    public static void render(GuiGraphics g) {
        if (!active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        g.drawString(mc.font, VERSION_TEXT, 2, 2, 0xFFFFFF, true);
    }

    // ------------------------------------------------------------------ the old textures

    private static void paintOldTextures(Minecraft mc) {
        TextureAtlas atlas = mc.getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
        int mips = Math.max(0, mc.options.mipmapLevels().get());
        atlas.bind();
        // 1. the game's built-in old textures for everything they cover
        Pack pack = mc.getResourcePackRepository().getPack(PACK);
        if (pack != null) {
            try (PackResources res = pack.open()) {
                for (String folder : new String[]{"block", "item"}) {
                    for (Map.Entry<ResourceLocation, NativeImage> e : read(res, "textures/" + folder).entrySet()) {
                        String path = e.getKey().getPath(); // textures/block/stone.png -> block/stone
                        paint(atlas, new ResourceLocation(e.getKey().getNamespace(),
                                path.substring("textures/".length(), path.length() - ".png".length())), e.getValue(), mips);
                    }
                }
                if (!FacelessMobs.isActive()) { // (the faceless mobs own the mob textures while they are on)
                    for (Map.Entry<ResourceLocation, NativeImage> e : read(res, "textures/entity").entrySet()) {
                        swapEntity(mc, e.getKey(), e.getValue());
                    }
                }
            } catch (Exception e) {
                BrokenWorld.LOGGER.warn("[BrokenWorld] could not read the old textures: {}", e.toString());
            }
        }
        // 2. on top: the real Minecraft Alpha textures, if the game has them (see AlphaAssets)
        Map<String, NativeImage> alpha = AlphaAssets.read();
        try {
            NativeImage terrain = alpha.get("terrain.png");
            if (terrain != null) {
                for (Map.Entry<Integer, String[]> e : AlphaAssets.BLOCKS.entrySet()) {
                    for (String name : e.getValue()) {
                        paint(atlas, new ResourceLocation("minecraft", "block/" + name), AlphaAssets.tile(terrain, e.getKey()), mips);
                    }
                }
                // every other block: as its Alpha "ancestor" (or gone, if it is a plant that did not exist yet)
                Set<String> done = new java.util.HashSet<>();
                for (String[] names : AlphaAssets.BLOCKS.values()) done.addAll(List.of(names));
                for (ResourceLocation sprite : blockSprites(mc)) {
                    String name = sprite.getPath().startsWith("block/") ? sprite.getPath().substring(6) : null;
                    if (name == null || done.contains(name) || name.startsWith("water_") || name.startsWith("lava_")) continue;
                    int fallback = AlphaAssets.fallbackTile(name);
                    if (fallback == AlphaAssets.CLEAR) {
                        paint(atlas, sprite, new NativeImage(16, 16, true), mips);
                    } else if (fallback >= 0) {
                        paint(atlas, sprite, AlphaAssets.tile(terrain, fallback), mips);
                    }
                }
                water = new AlphaFluids(false);
                lava = new AlphaFluids(true);
                for (int i = 0; i < 40; i++) { // let them settle into ripples first
                    water.tick();
                    lava.tick();
                }
                // Alpha's grass side is green all by itself: today's tinted grass edge on top of it has to go
                for (String overlay : new String[]{"grass_block_side_overlay"}) {
                    paint(atlas, new ResourceLocation("minecraft", "block/" + overlay), new NativeImage(16, 16, true), mips);
                }
            }
            NativeImage items = alpha.get("gui/items.png");
            if (items != null) {
                for (Map.Entry<Integer, String> e : AlphaAssets.ITEMS.entrySet()) {
                    paint(atlas, new ResourceLocation("minecraft", "item/" + e.getValue()), AlphaAssets.tile(items, e.getKey()), mips);
                }
            }
            if (!FacelessMobs.isActive()) {
                for (Map.Entry<String, String> e : AlphaAssets.MOBS.entrySet()) {
                    NativeImage img = alpha.remove(e.getKey());
                    if (img == null) continue;
                    if ((e.getKey().equals("mob/zombie.png") || e.getKey().equals("mob/char.png")) && img.getHeight() == 32) {
                        NativeImage wide = AlphaAssets.widenHumanoid(img);
                        img.close();
                        img = wide;
                    }
                    swapEntity(mc, new ResourceLocation("minecraft", e.getValue()), img);
                }
            }
            alphaScreen(mc, alpha);
            if (terrain != null) {
                alphaUsed = true;
                AlphaColors.apply(mc, alpha.get("misc/grasscolor.png"), alpha.get("misc/foliagecolor.png"));
            }
        } finally {
            for (NativeImage img : alpha.values()) img.close();
        }
    }

    /**
     * The hotbar, the buttons, hearts, armour, air and crosshair from Alpha - and no hunger or experience bars (Alpha
     * had none: they are simply not drawn). Also Alpha's clouds, sun, rain and snow.
     */
    private static void alphaScreen(Minecraft mc, Map<String, NativeImage> alpha) {
        NativeImage icons = alpha.get("gui/icons.png");
        if (icons != null) {
            NativeImage img = new NativeImage(icons.getWidth(), icons.getHeight(), true);
            for (int x = 0; x < img.getWidth(); x++)
                for (int y = 0; y < img.getHeight(); y++) {
                    int c = icons.getPixelRGBA(x, y);
                    img.setPixelRGBA(x, y, AlphaAssets.isPlaceholder(c) ? 0 : c);
                }
            swapEntity(mc, new ResourceLocation("minecraft", "textures/gui/icons.png"), img);
        }
        NativeImage gui = alpha.get("gui/gui.png");
        if (gui != null) {
            // today's sheet, with Alpha's hotbar, selection frame and buttons on it (the rest of Alpha's is blank)
            try (InputStream in = mc.getResourceManager().getResourceOrThrow(
                    new ResourceLocation("minecraft", "textures/gui/widgets.png")).open()) {
                NativeImage img = NativeImage.read(in);
                int[][] parts = {{0, 0, 182, 22}, {0, 22, 24, 24}, {0, 46, 200, 60}};
                for (int[] r : parts) gui.copyRect(img, r[0], r[1], r[0], r[1], r[2], r[3], false, false);
                swapEntity(mc, new ResourceLocation("minecraft", "textures/gui/widgets.png"), img);
            } catch (Exception e) {
                BrokenWorld.LOGGER.warn("[BrokenWorld] no Alpha hotbar: {}", e.toString());
            }
        }
        String[][] sky = {{"environment/clouds.png", "textures/environment/clouds.png"},
                {"terrain/sun.png", "textures/environment/sun.png"},
                {"environment/rain.png", "textures/environment/rain.png"},
                {"environment/snow.png", "textures/environment/snow.png"}};
        for (String[] e : sky) {
            NativeImage img = alpha.remove(e[0]);
            if (img != null) swapEntity(mc, new ResourceLocation("minecraft", e[1]), img);
        }
    }

    /** Every sprite any block state is drawn with (the models' faces), worked out once. */
    private static Set<ResourceLocation> blockSprites(Minecraft mc) {
        if (blockSprites != null) return blockSprites;
        Set<ResourceLocation> names = new LinkedHashSet<>();
        net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(1);
        net.minecraft.core.Direction[] sides = {null, net.minecraft.core.Direction.DOWN, net.minecraft.core.Direction.UP,
                net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.WEST,
                net.minecraft.core.Direction.EAST};
        for (net.minecraft.world.level.block.Block block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
            for (net.minecraft.world.level.block.state.BlockState state : block.getStateDefinition().getPossibleStates()) {
                net.minecraft.client.resources.model.BakedModel model = mc.getBlockRenderer().getBlockModel(state);
                names.add(model.getParticleIcon().contents().name());
                for (net.minecraft.core.Direction side : sides) {
                    for (net.minecraft.client.renderer.block.model.BakedQuad q : model.getQuads(state, side, random)) {
                        names.add(q.getSprite().contents().name());
                    }
                }
            }
        }
        blockSprites = names;
        return names;
    }

    /** Paints one sprite of the block atlas (all its mip levels); closes the image. Skips animated / other sizes. */
    private static void paint(TextureAtlas atlas, ResourceLocation name, NativeImage img, int mips) {
        try {
            TextureAtlasSprite sprite = atlas.getSprite(name);
            if (sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation())
                    || img.getWidth() != sprite.contents().width() || img.getHeight() != sprite.contents().height()) {
                return;
            }
            // (bound every time: making a mob texture binds that one instead)
            atlas.bind();
            NativeImage[] levels = pointMips(img, mips);
            for (int i = 0; i < levels.length; i++) {
                int w = img.getWidth() >> i, h = img.getHeight() >> i;
                if (w < 1 || h < 1) break;
                levels[i].upload(i, sprite.getX() >> i, sprite.getY() >> i, 0, 0, w, h, false, false);
            }
            for (int i = 1; i < levels.length; i++) levels[i].close();
            PAINTED.add(sprite);
        } finally {
            img.close();
        }
    }

    /**
     * Mip levels the way Alpha looked from afar - it had no mipmaps: every level keeps whole pixels of the texture
     * (one in 2, 4, 8...) instead of today's blend, so distant leaves keep their holes and blocks stay sharp and grainy.
     */
    private static NativeImage[] pointMips(NativeImage img, int mips) {
        NativeImage[] levels = new NativeImage[mips + 1];
        levels[0] = img;
        for (int l = 1; l <= mips; l++) {
            int w = Math.max(1, img.getWidth() >> l), h = Math.max(1, img.getHeight() >> l);
            NativeImage m = new NativeImage(w, h, false);
            for (int x = 0; x < w; x++)
                for (int y = 0; y < h; y++) m.setPixelRGBA(x, y, img.getPixelRGBA(x << l, y << l));
            levels[l] = m;
        }
        return levels;
    }

    private static void swapEntity(Minecraft mc, ResourceLocation texture, NativeImage img) {
        mc.getTextureManager().register(texture, new DynamicTexture(img));
        SWAPPED_ENTITIES.add(texture);
    }

    private static Map<ResourceLocation, NativeImage> read(PackResources res, String folder) {
        Map<ResourceLocation, NativeImage> out = new LinkedHashMap<>();
        res.listResources(PackType.CLIENT_RESOURCES, "minecraft", folder, (loc, supplier) -> {
            if (!loc.getPath().endsWith(".png")) return;
            try (InputStream in = supplier.get()) {
                out.put(loc, NativeImage.read(in));
            } catch (Exception ignored) {
                // a broken file: skip it
            }
        });
        return out;
    }
}
