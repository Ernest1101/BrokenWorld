package com.brokenworld.client;

import com.brokenworld.BrokenWorld;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The silhouette's poses, made in Blender (tools/blender_poses.py) and exported by tools/blender_export.py to
 * models/entity/silhouette/poses.json: key frames of local rotations per part (and the root's rotation and offset),
 * plus which part hangs on which (the jaw on the head, ...).
 */
public final class PoseLibrary {
    /** A pose at one moment: rotation per part, and the whole body's rotation/offset (may be null). */
    public record Sample(Map<String, Quaternionf> parts, @Nullable Quaternionf rootRotation, @Nullable Vector3f rootOffset) {
        public Quaternionf part(String name) {
            Quaternionf q = parts.get(name);
            return q == null ? new Quaternionf() : q;
        }
    }

    private record Frame(float time, Map<String, Quaternionf> parts, @Nullable Quaternionf rootQ, @Nullable Vector3f rootP) {}

    private record Pose(float length, List<Frame> frames) {}

    private final Map<String, String> parents;
    private final Map<String, Pose> poses;

    private PoseLibrary(Map<String, String> parents, Map<String, Pose> poses) {
        this.parents = parents;
        this.poses = poses;
    }

    @Nullable
    public String parent(String part) {
        return parents.get(part);
    }

    public boolean has(String pose) {
        return poses.containsKey(pose);
    }

    /** The pose at the given time (seconds); looping poses wrap around. Null if there is no such pose. */
    @Nullable
    public Sample sample(String name, float time) {
        Pose pose = poses.get(name);
        if (pose == null || pose.frames.isEmpty()) return null;
        List<Frame> frames = pose.frames;
        if (frames.size() == 1 || pose.length <= 0) return toSample(frames.get(0), frames.get(0), 0);
        float t = time % pose.length;
        if (t < 0) t += pose.length;
        for (int i = 0; i < frames.size() - 1; i++) {
            Frame a = frames.get(i), b = frames.get(i + 1);
            if (t >= a.time && t <= b.time) {
                float span = Math.max(1.0E-4F, b.time - a.time);
                return toSample(a, b, (t - a.time) / span);
            }
        }
        Frame last = frames.get(frames.size() - 1);
        return toSample(last, last, 0);
    }

    private static Sample toSample(Frame a, Frame b, float alpha) {
        Map<String, Quaternionf> parts = new HashMap<>();
        for (String name : a.parts.keySet()) {
            Quaternionf qa = a.parts.get(name);
            Quaternionf qb = b.parts.getOrDefault(name, qa);
            parts.put(name, new Quaternionf(qa).slerp(qb, alpha));
        }
        Quaternionf rq = a.rootQ == null ? null : new Quaternionf(a.rootQ).slerp(b.rootQ == null ? a.rootQ : b.rootQ, alpha);
        Vector3f rp = a.rootP == null ? null : new Vector3f(a.rootP).lerp(b.rootP == null ? a.rootP : b.rootP, alpha);
        return new Sample(parts, rq, rp);
    }

    public static PoseLibrary load(ResourceLocation location) {
        Map<String, String> parents = new HashMap<>();
        Map<String, Pose> poses = new HashMap<>();
        try (Reader reader = Minecraft.getInstance().getResourceManager().getResourceOrThrow(location).openAsReader()) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            json.getAsJsonObject("parents").entrySet().forEach(e -> parents.put(e.getKey(), e.getValue().getAsString()));
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("poses").entrySet()) {
                JsonObject pose = e.getValue().getAsJsonObject();
                List<Frame> frames = new ArrayList<>();
                for (JsonElement f : pose.getAsJsonArray("frames")) {
                    JsonObject fo = f.getAsJsonObject();
                    Map<String, Quaternionf> parts = new HashMap<>();
                    fo.getAsJsonObject("parts").entrySet().forEach(p -> parts.put(p.getKey(), quat(p.getValue().getAsJsonArray())));
                    Quaternionf rq = null;
                    Vector3f rp = null;
                    if (fo.has("root")) {
                        JsonObject root = fo.getAsJsonObject("root");
                        rq = quat(root.getAsJsonArray("q"));
                        JsonArray p = root.getAsJsonArray("p");
                        rp = new Vector3f(p.get(0).getAsFloat(), p.get(1).getAsFloat(), p.get(2).getAsFloat());
                    }
                    frames.add(new Frame(fo.get("t").getAsFloat(), parts, rq, rp));
                }
                poses.put(e.getKey(), new Pose(pose.get("length").getAsFloat(), frames));
            }
        } catch (Exception e) {
            BrokenWorld.LOGGER.error("[BrokenWorld] could not read poses {}", location, e);
        }
        return new PoseLibrary(parents, poses);
    }

    private static Quaternionf quat(JsonArray a) {
        return new Quaternionf(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat(), a.get(3).getAsFloat());
    }
}
