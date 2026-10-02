package com.brokenworld.client;

import com.brokenworld.BrokenWorld;
import com.brokenworld.mixin.client.AgeableListModelAccessor;
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
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
public final class FacelessMobs {
    private static boolean active;
    private static final Set<ResourceLocation> SWAPPED = new HashSet<>();
    /** Textures we looked at and could not (or need not) change. */
    private static final Set<ResourceLocation> SKIPPED = new HashSet<>();


    private FacelessMobs() {}

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

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!active || mc.level == null || mc.player == null) return;
        if (mc.level.getGameTime() % 20 != 0) return;
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
    private static ModelPart findHead(EntityModel<?> model) {
        if (model instanceof HumanoidModel<?> humanoid) return humanoid.head;
        if (model instanceof HierarchicalModel<?> hierarchical) {
            ModelPart root = hierarchical.root();
            if (root.hasChild("head")) return root.getChild("head");
            if (root.hasChild("body") && root.getChild("body").hasChild("head")) return root.getChild("body").getChild("head");
            return null;
        }
        if (model instanceof AgeableListModel<?>) {
            for (ModelPart p : ((AgeableListModelAccessor) model).brokenworld$headParts()) return p;
        }
        return null;
    }

    /** Splits the head's cube faces into the front ones (facing -Z, the face) and the rest. */
    private static void collectFaces(ModelPart head, List<float[]> front, List<float[]> others) {
        for (ModelPart.Cube cube : head.cubes) {
            for (ModelPart.Polygon polygon : cube.polygons) {
                Vector3f normal = polygon.normal;
                float u0 = 1, v0 = 1, u1 = 0, v1 = 0;
                for (ModelPart.Vertex vertex : polygon.vertices) {
                    u0 = Math.min(u0, vertex.u);
                    v0 = Math.min(v0, vertex.v);
                    u1 = Math.max(u1, vertex.u);
                    v1 = Math.max(v1, vertex.v);
                }
                float[] rect = {u0, v0, u1, v1};
                if (normal.z() < -0.9F) front.add(rect);
                else others.add(rect);
            }
        }
    }
}
