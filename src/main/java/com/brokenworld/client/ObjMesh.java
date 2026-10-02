package com.brokenworld.client;

import com.brokenworld.BrokenWorld;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A minimal OBJ mesh (positions, UVs, normals, polygon faces) that renders through an entity VertexConsumer.
 * Used for the silhouette, whose meshes are made in Blender (see tools/blender_export.py).
 */
public final class ObjMesh {
    /** Each face is drawn as quads; triangles repeat their last vertex. x, y, z, u, v per vertex. */
    private final float[] vertices;
    /** nx, ny, nz per vertex: smooth normals from the file, or the face normal if it has none. */
    private final float[] normals;
    private float[] halfCentres;

    private ObjMesh(float[] vertices, float[] normals) {
        this.vertices = vertices;
        this.normals = normals;
    }

    public static ObjMesh load(ResourceLocation location) {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
        if (resource.isEmpty()) {
            BrokenWorld.LOGGER.error("[BrokenWorld] missing mesh {}", location);
            return new ObjMesh(new float[0], new float[0]);
        }
        List<float[]> positions = new ArrayList<>();
        List<float[]> uvs = new ArrayList<>();
        List<float[]> fileNormals = new ArrayList<>();
        List<Float> vertexData = new ArrayList<>();
        List<Float> normalData = new ArrayList<>();
        try (BufferedReader reader = resource.get().openAsReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] t = line.trim().split("\\s+");
                switch (t[0]) {
                    case "v" -> positions.add(floats(t));
                    case "vt" -> uvs.add(new float[]{Float.parseFloat(t[1]), 1.0F - Float.parseFloat(t[2])});
                    case "vn" -> fileNormals.add(floats(t));
                    case "f" -> addFace(t, positions, uvs, fileNormals, vertexData, normalData);
                    default -> {
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            BrokenWorld.LOGGER.error("[BrokenWorld] could not read mesh {}", location, e);
        }
        return new ObjMesh(toArray(vertexData), toArray(normalData));
    }

    private static float[] floats(String[] t) {
        return new float[]{Float.parseFloat(t[1]), Float.parseFloat(t[2]), Float.parseFloat(t[3])};
    }

    private static void addFace(String[] t, List<float[]> positions, List<float[]> uvs, List<float[]> fileNormals,
                                List<Float> vertexData, List<Float> normalData) {
        int n = t.length - 1;
        float[][] p = new float[n][];
        float[][] uv = new float[n][];
        float[][] vn = new float[n][];
        for (int i = 0; i < n; i++) {
            String[] refs = t[i + 1].split("/");
            p[i] = positions.get(Integer.parseInt(refs[0]) - 1);
            uv[i] = refs.length > 1 && !refs[1].isEmpty() ? uvs.get(Integer.parseInt(refs[1]) - 1) : new float[]{0, 0};
            vn[i] = refs.length > 2 && !refs[2].isEmpty() ? fileNormals.get(Integer.parseInt(refs[2]) - 1) : null;
        }
        // Fan-split n-gons into quads/triangles.
        for (int start = 1; start + 1 < n; start += 2) {
            int[] ids = {0, start, start + 1, Math.min(start + 2, n - 1)};
            Vector3f a = new Vector3f(p[ids[1]][0] - p[0][0], p[ids[1]][1] - p[0][1], p[ids[1]][2] - p[0][2]);
            Vector3f b = new Vector3f(p[ids[2]][0] - p[0][0], p[ids[2]][1] - p[0][1], p[ids[2]][2] - p[0][2]);
            Vector3f faceNormal = a.cross(b);
            if (faceNormal.lengthSquared() > 1.0E-12F) faceNormal.normalize();
            for (int id : ids) {
                vertexData.add(p[id][0]);
                vertexData.add(p[id][1]);
                vertexData.add(p[id][2]);
                vertexData.add(uv[id][0]);
                vertexData.add(uv[id][1]);
                float[] normal = vn[id] != null ? vn[id] : new float[]{faceNormal.x, faceNormal.y, faceNormal.z};
                normalData.add(normal[0]);
                normalData.add(normal[1]);
                normalData.add(normal[2]);
            }
        }
    }

    /** Average position of the x &lt; 0 half and of the x &ge; 0 half: {lx, ly, lz, rx, ry, rz}. */
    private float[] computeHalfCentres() {
        float[] sum = new float[6];
        int[] n = new int[2];
        for (int o = 0; o < vertices.length; o += 5) {
            int side = vertices[o] < 0 ? 0 : 1;
            sum[side * 3] += vertices[o];
            sum[side * 3 + 1] += vertices[o + 1];
            sum[side * 3 + 2] += vertices[o + 2];
            n[side]++;
        }
        for (int i = 0; i < 6; i++) sum[i] /= Math.max(1, n[i / 3]);
        return sum;
    }

    private static float[] toArray(List<Float> list) {
        float[] out = new float[list.size()];
        for (int i = 0; i < out.length; i++) out[i] = list.get(i);
        return out;
    }

    public void render(PoseStack poseStack, VertexConsumer consumer, int light, int overlay,
                       float r, float g, float b, float a) {
        renderInflated(poseStack, consumer, light, overlay, r, g, b, a, 1.0F, 1.0F);
    }

    /**
     * Renders with the left (x &lt; 0) and right (x &ge; 0) halves each scaled around their own centre
     * (scaleY for height), e.g. to make both eyes bigger without moving them apart.
     */
    public void renderInflated(PoseStack poseStack, VertexConsumer consumer, int light, int overlay,
                               float r, float g, float b, float a, float scale, float scaleY) {
        boolean inflate = scale != 1.0F || scaleY != 1.0F;
        if (inflate && halfCentres == null) halfCentres = computeHalfCentres();
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normalMatrix = poseStack.last().normal();
        int count = normals.length / 3;
        for (int i = 0; i < count; i++) {
            int o = i * 5;
            float x = vertices[o], y = vertices[o + 1], z = vertices[o + 2];
            if (inflate) {
                int c = x < 0 ? 0 : 3;
                x = halfCentres[c] + (x - halfCentres[c]) * scale;
                y = halfCentres[c + 1] + (y - halfCentres[c + 1]) * scaleY;
                z = halfCentres[c + 2] + (z - halfCentres[c + 2]) * scale;
            }
            consumer.vertex(pose, x, y, z)
                    .color(r, g, b, a)
                    .uv(vertices[o + 3], vertices[o + 4])
                    .overlayCoords(overlay)
                    .uv2(light)
                    .normal(normalMatrix, normals[i * 3], normals[i * 3 + 1], normals[i * 3 + 2])
                    .endVertex();
        }
    }
}
