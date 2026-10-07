package com.brokenworld.client;

import com.brokenworld.BrokenWorld;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * "Mobs have no face": while active, the textures of the mobs around get swapped for copies where the front of the
 * head is painted over with the colour of the rest of the head - a smooth, blank face. Turning it off puts the real
 * textures back. Works for any mob whose model has a findable head (humanoids, quadrupeds, chickens, wolves,
 * creepers, villagers...), vanilla or modded.
 *
 * The real texture of every swapped location is restored simply by releasing it: the texture manager then loads the
 * original from the resources again the next time it is used.
 */
@Mod.EventBusSubscriber(modid = BrokenWorld.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FacelessMobs {
    private static boolean active;
    private static final Set<ResourceLocation> SWAPPED = new HashSet<>();
    /** Textures we looked at and could not (or need not) change. */
    private static final Set<ResourceLocation> SKIPPED = new HashSet<>();
    private static final Map<Class<?>, Optional<Method>> HEAD_PARTS = new HashMap<>();

    // Model internals are private; these are their SRG names (Forge maps them in the dev environment).
    private static Field cubesField;
    private static Field polygonsField;
    private static Field verticesField;
    private static Field normalField;
    private static Field uField;
    private static Field vField;
    private static boolean reflectionBroken;

    private FacelessMobs() {}

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean on) {
        if (on == active) return;
        active = on;
        BrokenWorld.LOGGER.info("[BrokenWorld] faceless mobs {}", on ? "on" : "off");
        if (!on) restoreAll();
    }

    public static void reset() {
        active = false;
        restoreAll();
        SKIPPED.clear();
    }

    private static void restoreAll() {
        Minecraft mc = Minecraft.getInstance();
        for (ResourceLocation loc : SWAPPED) mc.getTextureManager().release(loc);
        SWAPPED.clear();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || !active || mc.level == null || mc.player == null
                || Hallucination.active()) return;
        if (mc.level.getGameTime() % 20 != 0 || reflectionBroken) return;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity living) || e instanceof Player || e.distanceToSqr(mc.player) > 96 * 96) continue;
            EntityRenderer<? super Entity> renderer = mc.getEntityRenderDispatcher().getRenderer(e);
            if (!(renderer instanceof LivingEntityRenderer<?, ?> lr)) continue;
            @SuppressWarnings({"unchecked", "rawtypes"})
            ResourceLocation tex = ((EntityRenderer) renderer).getTextureLocation(living);
            if (tex == null || SWAPPED.contains(tex) || SKIPPED.contains(tex)) continue;
            try {
                if (swap(mc, tex, lr.getModel())) {
                    BrokenWorld.LOGGER.info("[BrokenWorld] faceless: {}", tex);
                } else {
                    BrokenWorld.LOGGER.info("[BrokenWorld] faceless: no head/face found for {} ({})", tex,
                            lr.getModel().getClass().getSimpleName());
                    SKIPPED.add(tex);
                }
            } catch (Exception ex) {
                BrokenWorld.LOGGER.warn("[BrokenWorld] could not blank the face of {}: {}", tex, ex.toString());
                SKIPPED.add(tex);
            }
        }
    }

    /** Builds the faceless copy of one texture and registers it in place of the original. */
    private static boolean swap(Minecraft mc, ResourceLocation tex, EntityModel<?> model) throws Exception {
        ModelPart head = findHead(model);
        if (head == null) return false;
        List<float[]> front = new ArrayList<>(); // u0, v0, u1, v1 (0..1)
        List<float[]> others = new ArrayList<>();
        collectFaces(head, front, others);
        if (front.isEmpty()) return false;

        Optional<Resource> resource = mc.getResourceManager().getResource(tex);
        if (resource.isEmpty()) return false;
        NativeImage image;
        try (InputStream in = resource.get().open()) {
            image = NativeImage.read(in);
        }
        int w = image.getWidth(), h = image.getHeight();
        // the "skin" colour: average of the other faces of the head
        long r = 0, g = 0, b = 0, n = 0;
        for (float[] f : others) {
            for (int y = (int) (f[1] * h); y < (int) Math.ceil(f[3] * h) && y < h; y++)
                for (int x = (int) (f[0] * w); x < (int) Math.ceil(f[2] * w) && x < w; x++) {
                    int c = image.getPixelRGBA(x, y); // ABGR
                    if ((c >>> 24) < 128) continue;
                    r += c & 0xFF;
                    g += (c >> 8) & 0xFF;
                    b += (c >> 16) & 0xFF;
                    n++;
                }
        }
        if (n == 0) {
            image.close();
            return false;
        }
        int ar = (int) (r / n), ag = (int) (g / n), ab = (int) (b / n);
        Random noise = new Random(tex.hashCode());
        for (float[] f : front) {
            for (int y = (int) (f[1] * h); y < (int) Math.ceil(f[3] * h) && y < h; y++)
                for (int x = (int) (f[0] * w); x < (int) Math.ceil(f[2] * w) && x < w; x++) {
                    int c = image.getPixelRGBA(x, y);
                    if ((c >>> 24) == 0) continue; // keep holes holes
                    int d = noise.nextInt(9) - 4;
                    int cr = clamp(ar + d), cg = clamp(ag + d), cb = clamp(ab + d);
                    image.setPixelRGBA(x, y, (c & 0xFF000000) | (cb << 16) | (cg << 8) | cr);
                }
        }
        mc.getTextureManager().register(tex, new DynamicTexture(image));
        SWAPPED.add(tex);
        return true;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    @Nullable
    private static ModelPart findHead(EntityModel<?> model) throws Exception {
        if (model instanceof HumanoidModel<?> humanoid) return humanoid.head;
        if (model instanceof HierarchicalModel<?> hierarchical) {
            ModelPart root = hierarchical.root();
            if (root.hasChild("head")) return root.getChild("head");
            if (root.hasChild("body") && root.getChild("body").hasChild("head")) return root.getChild("body").getChild("head");
            return null;
        }
        if (model instanceof AgeableListModel<?>) {
            Optional<Method> m = HEAD_PARTS.computeIfAbsent(model.getClass(), c -> {
                try {
                    Method method = ObfuscationReflectionHelper.findMethod(AgeableListModel.class, "m_5607_"); // headParts
                    return Optional.of(method);
                } catch (Exception e) {
                    return Optional.empty();
                }
            });
            if (m.isEmpty()) return null;
            Object parts = m.get().invoke(model);
            if (parts instanceof Iterable<?> it) {
                for (Object o : it) if (o instanceof ModelPart p) return p;
            }
        }
        return null;
    }

    /** Splits the head's cube faces into the front ones (facing -Z, the face) and the rest. */
    private static void collectFaces(ModelPart head, List<float[]> front, List<float[]> others) throws Exception {
        if (cubesField == null) {
            try {
                cubesField = ObfuscationReflectionHelper.findField(ModelPart.class, "f_104212_");
                Class<?> cube = Class.forName("net.minecraft.client.model.geom.ModelPart$Cube");
                Class<?> polygon = Class.forName("net.minecraft.client.model.geom.ModelPart$Polygon");
                Class<?> vertex = Class.forName("net.minecraft.client.model.geom.ModelPart$Vertex");
                polygonsField = ObfuscationReflectionHelper.findField(cube, "f_104341_");
                verticesField = ObfuscationReflectionHelper.findField(polygon, "f_104359_");
                normalField = ObfuscationReflectionHelper.findField(polygon, "f_104360_");
                uField = ObfuscationReflectionHelper.findField(vertex, "f_104372_");
                vField = ObfuscationReflectionHelper.findField(vertex, "f_104373_");
            } catch (Exception e) {
                reflectionBroken = true;
                throw e;
            }
        }
        for (Object cube : (List<?>) cubesField.get(head)) {
            for (Object polygon : (Object[]) polygonsField.get(cube)) {
                Vector3f normal = (Vector3f) normalField.get(polygon);
                float u0 = 1, v0 = 1, u1 = 0, v1 = 0;
                for (Object vertex : (Object[]) verticesField.get(polygon)) {
                    float u = uField.getFloat(vertex), v = vField.getFloat(vertex);
                    u0 = Math.min(u0, u);
                    v0 = Math.min(v0, v);
                    u1 = Math.max(u1, u);
                    v1 = Math.max(v1, v);
                }
                float[] rect = {u0, v0, u1, v1};
                if (normal.z() < -0.9F) front.add(rect);
                else others.add(rect);
            }
        }
    }
}
