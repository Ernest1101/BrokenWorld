package com.brokenworld.client;

import com.brokenworld.BrokenWorld;
import com.brokenworld.entity.SilhouetteEntity;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Draws the silhouette from the Blender meshes in models/entity/silhouette/*.obj (blender/silhouette.blend, exported
 * with tools/blender_export.py), posed with the poses from poses.json (tools/blender_poses.py).
 * <ul>
 *   <li>Parts hang on each other (jaw on head, ...); each turns around its own pivot.</li>
 *   <li>Stances from Blender: peek, crawl (animated by its movement), hang upside down.</li>
 *   <li>The head follows the player - when only the head turns, it can turn much too far.</li>
 *   <li>The jaw falls open (pose "jaw_open") when the entity says so.</li>
 *   <li>"Jerky" ones move in jumps, like a lagging player, the head twitching at every jump.</li>
 * </ul>
 */
public class SilhouetteRenderer extends EntityRenderer<SilhouetteEntity> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(BrokenWorld.MODID, "textures/entity/silhouette.png");
    private static final ResourceLocation EYES_TEXTURE =
            new ResourceLocation(BrokenWorld.MODID, "textures/entity/silhouette_eyes.png");
    private static final ResourceLocation TEETH_TEXTURE =
            new ResourceLocation(BrokenWorld.MODID, "textures/entity/silhouette_teeth.png");
    private static final String MESH_DIR = "models/entity/silhouette/";
    private static final String[] BODY_PARTS = {"torso", "right_arm", "left_arm", "right_forearm", "left_forearm",
            "right_leg", "left_leg", "right_shin", "left_shin", "head", "jaw"};
    private static final String[] TEETH_PARTS = {"teeth", "teeth_lower"};
    private static final String[] ALL_PARTS = {"torso", "right_arm", "left_arm", "right_forearm", "left_forearm",
            "right_leg", "left_leg", "right_shin", "left_shin", "head", "jaw",
            "teeth", "teeth_lower", "eyes"};
    private static final RandomSource RANDOM = RandomSource.create();

    /** Farther than this (in blocks, divided by its growth) the simplified meshes from lod/ are drawn. */
    private static final float LOD_DISTANCE = 20.0F;

    private static Map<String, ObjMesh> meshes;
    /** About a quarter of the faces (tools/blender_export.py, LOD_RATIO); null parts fall back to the full mesh. */
    private static Map<String, ObjMesh> lodMeshes;
    private static Map<String, float[]> pivots;
    private static PoseLibrary poses;

    /** Per entity: the jerky "last shown" position, and how open the mouth is. */
    private static final class State {
        Vec3 shown;
        int nextJump;
        float twitch;
        float jaw;
        long lastNanos;
    }

    private static final Map<SilhouetteEntity, State> STATES = new WeakHashMap<>();

    public SilhouetteRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    /** Called on resource reload so edited meshes and poses are picked up. */
    public static void clearCache() {
        meshes = null;
        lodMeshes = null;
        pivots = null;
        poses = null;
    }

    private static void ensureLoaded() {
        if (meshes != null) return;
        Map<String, ObjMesh> m = new HashMap<>();
        for (String part : ALL_PARTS) {
            m.put(part, ObjMesh.load(new ResourceLocation(BrokenWorld.MODID, MESH_DIR + part + ".obj")));
        }
        Map<String, ObjMesh> lod = new HashMap<>();
        for (String part : ALL_PARTS) {
            ResourceLocation loc = new ResourceLocation(BrokenWorld.MODID, MESH_DIR + "lod/" + part + ".obj");
            if (Minecraft.getInstance().getResourceManager().getResource(loc).isPresent()) lod.put(part, ObjMesh.load(loc));
        }
        Map<String, float[]> p = new HashMap<>();
        ResourceLocation pivotFile = new ResourceLocation(BrokenWorld.MODID, MESH_DIR + "pivots.json");
        try (Reader reader = Minecraft.getInstance().getResourceManager().getResourceOrThrow(pivotFile).openAsReader()) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            for (String part : ALL_PARTS) {
                JsonArray a = json.getAsJsonArray(part);
                if (a != null) p.put(part, new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()});
            }
        } catch (Exception e) {
            BrokenWorld.LOGGER.error("[BrokenWorld] could not read {}", pivotFile, e);
        }
        for (String part : ALL_PARTS) p.putIfAbsent(part, new float[3]);
        poses = PoseLibrary.load(new ResourceLocation(BrokenWorld.MODID, MESH_DIR + "poses.json"));
        meshes = m;
        lodMeshes = lod;
        pivots = p;
    }

    /** Everything that decides how one frame of it looks. */
    private record Frame(float headYaw, float pitch, float tilt, float swing, @Nullable PoseLibrary.Sample stance,
                         @Nullable PoseLibrary.Sample jawOpen, float jaw) {}

    @Override
    public void render(SilhouetteEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight) {
        ensureLoaded();
        State state = STATES.computeIfAbsent(entity, e -> new State());
        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        float headYaw = Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot) - bodyYaw;
        float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
        float age = entity.tickCount + partialTick;
        float walkPos = entity.walkAnimation.position(partialTick);
        float walkSpeed = Math.min(1.0F, entity.walkAnimation.speed(partialTick));

        SilhouetteEntity.Stance stanceType = entity.getStance();
        PoseLibrary.Sample stance = null;
        if (stanceType.pose != null && poses.has(stanceType.pose)) {
            // crawling moves its arms as it moves forward; still poses are just held
            float t = switch (stanceType) {
                case CRAWL -> walkPos * 0.18F + age * 0.004F;
                case SPIDER -> walkPos * 0.14F + age * 0.002F; // quick little steps
                default -> 0.0F;
            };
            stance = poses.sample(stanceType.pose, t);
        }
        boolean posed = stanceType == SilhouetteEntity.Stance.CRAWL || stanceType == SilhouetteEntity.Stance.HANG
                || stanceType == SilhouetteEntity.Stance.SPIDER || stanceType == SilhouetteEntity.Stance.WINDOW;
        float swing = posed ? 0.0F : Mth.cos(walkPos * 0.45F) * 1.1F * walkSpeed;

        // the mouth opens and closes smoothly
        long now = System.nanoTime();
        float dt = state.lastNanos == 0 ? 0 : Math.min(0.1F, (now - state.lastNanos) / 1.0E9F);
        state.lastNanos = now;
        state.jaw = Mth.approach(state.jaw, entity.isJawOpen() ? 1.0F : 0.0F, dt * 4.0F);

        // jerky: show it where it was a moment ago, then jump - with the head twitching each time
        Vec3 offset = Vec3.ZERO;
        if (entity.isJerky()) {
            Vec3 now3 = entity.getPosition(partialTick);
            if (state.shown == null || entity.tickCount >= state.nextJump || state.shown.distanceToSqr(now3) > 9) {
                state.shown = now3;
                state.nextJump = entity.tickCount + 3 + RANDOM.nextInt(6);
                state.twitch = (RANDOM.nextFloat() - 0.5F) * 0.9F;
            }
            offset = state.shown.subtract(now3);
        } else {
            state.twitch = 0;
        }

        float tilt = 0.22F + Mth.sin(age * 0.02F) * 0.03F + state.twitch;
        Frame frame = new Frame(headYaw, pitch, tilt, swing, stance, poses.sample("jaw_open", 0), state.jaw);

        // Stays pitch black even next to a torch: keep only the sky light.
        int light = LightTexture.pack(0, LightTexture.sky(packedLight));

        poseStack.pushPose();
        poseStack.translate(offset.x, offset.y, offset.z);
        // Model faces -Z; this turns it to face where the entity looks (same as vanilla mobs).
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        float growth = entity.getGrowth();
        if (growth != 1.0F) poseStack.scale(growth, growth, growth);
        if (stance != null && stance.rootOffset() != null) {
            Vector3f p = stance.rootOffset();
            poseStack.translate(p.x(), p.y(), p.z());
        }
        if (stance != null && stance.rootRotation() != null) poseStack.mulPose(stance.rootRotation());

        float dist = (float) Math.sqrt(this.entityRenderDispatcher.distanceToSqr(entity));
        Map<String, ObjMesh> set = dist / Math.max(1.0F, growth) > LOD_DISTANCE ? lodMeshes : meshes;

        // One texture per pass: asking for another buffer invalidates the previous one.
        VertexConsumer body = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        for (String part : BODY_PARTS) drawPart(set, poseStack, part, frame, body, light, 1, 1, 1);
        // the teeth only show when the mouth opens: closed, they would stick out of the slit like something in its mouth
        if (state.jaw > 0.05F) {
            VertexConsumer teeth = buffers.getBuffer(RenderType.entityCutoutNoCull(TEETH_TEXTURE));
            for (String part : TEETH_PARTS) drawPart(set, poseStack, part, frame, teeth, light, 1, 1, 1);
        }

        if (entity.hasGlowingEyes()) {
            // Only a little bigger and dimmer far away: two small points, not a lamp.
            // (When it has grown, the whole model - eyes included - is already bigger.)
            float grow = Math.max(1.0F, Mth.clamp(dist / 24.0F, 1.0F, 3.0F) / Math.max(1.0F, growth * 0.75F));
            float glow = Mth.clamp(1.0F - (dist - 16.0F) / 120.0F, 0.45F, 1.0F);
            VertexConsumer eyes = buffers.getBuffer(RenderType.eyes(EYES_TEXTURE));
            drawPart(set, poseStack, "eyes", frame, eyes, LightTexture.FULL_BRIGHT, grow, Math.max(1.0F, grow * 1.5F), glow);
        }
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    private static void drawPart(Map<String, ObjMesh> set, PoseStack poseStack, String part, Frame frame,
                                 VertexConsumer consumer, int light, float grow, float growY, float brightness) {
        ObjMesh mesh = set.getOrDefault(part, meshes.get(part));
        if (mesh == null) return;
        poseStack.pushPose();
        applyChain(poseStack, part, frame);
        mesh.renderInflated(poseStack, consumer, light, OverlayTexture.NO_OVERLAY,
                brightness, brightness, brightness, 1.0F, grow, growY);
        poseStack.popPose();
    }

    /** Moves to the part's pivot through all its parents, turning each one on the way. */
    private static void applyChain(PoseStack poseStack, String part, Frame frame) {
        String parent = poses.parent(part);
        float[] pivot = pivots.get(part);
        if (parent != null && pivots.containsKey(parent)) {
            applyChain(poseStack, parent, frame);
            float[] pp = pivots.get(parent);
            poseStack.translate(pivot[0] - pp[0], pivot[1] - pp[1], pivot[2] - pp[2]);
        } else {
            poseStack.translate(pivot[0], pivot[1], pivot[2]);
        }
        localTurn(poseStack, part, frame);
    }

    private static void localTurn(PoseStack poseStack, String part, Frame f) {
        if (f.stance() != null && f.stance().parts().containsKey(part)) poseStack.mulPose(f.stance().part(part));
        switch (part) {
            case "head" -> {
                poseStack.mulPose(Axis.YP.rotation(-f.headYaw() * Mth.DEG_TO_RAD));
                poseStack.mulPose(Axis.XP.rotation(-f.pitch() * Mth.DEG_TO_RAD));
                poseStack.mulPose(Axis.ZP.rotation(f.tilt()));
            }
            case "jaw" -> {
                if (f.jaw() > 0 && f.jawOpen() != null) {
                    poseStack.mulPose(new Quaternionf().slerp(f.jawOpen().part("jaw"), f.jaw()));
                }
            }
            case "right_arm" -> turn(poseStack, -f.swing() * 0.35F, 0.03F);
            case "left_arm" -> turn(poseStack, f.swing() * 0.35F, -0.03F);
            case "right_leg" -> turn(poseStack, f.swing(), 0);
            case "left_leg" -> turn(poseStack, -f.swing(), 0);
            // walking: the elbows hang a little bent and bend more as the arm swings forward,
            // the knee bends as the leg goes back and comes forward again
            case "right_forearm" -> turn(poseStack, elbow(-f.swing() * 0.35F, f), 0);
            case "left_forearm" -> turn(poseStack, elbow(f.swing() * 0.35F, f), 0);
            case "right_shin" -> turn(poseStack, knee(f.swing(), f), 0);
            case "left_shin" -> turn(poseStack, knee(-f.swing(), f), 0);
            default -> {
            }
        }
    }

    /** Elbow bend for an arm swung forward by armSwing (only while walking upright: poses bring their own). */
    private static float elbow(float armSwing, Frame f) {
        if (f.stance() != null && f.stance().parts().size() > 0 && f.swing() == 0) return 0;
        return 0.12F + Math.max(0, armSwing) * 0.8F;
    }

    private static float knee(float legSwing, Frame f) {
        if (f.stance() != null && f.stance().parts().size() > 0 && f.swing() == 0) return 0;
        return -(Math.max(0, -legSwing) * 0.9F + Math.abs(f.swing()) * 0.15F);
    }

    private static void turn(PoseStack poseStack, float x, float z) {
        if (x != 0) poseStack.mulPose(Axis.XP.rotation(x));
        if (z != 0) poseStack.mulPose(Axis.ZP.rotation(z));
    }

    @Override
    public ResourceLocation getTextureLocation(SilhouetteEntity entity) {
        return TEXTURE;
    }
}
