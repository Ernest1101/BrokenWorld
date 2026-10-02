"""Run inside Blender (Text editor -> Run Script, or through Blender MCP) with blender/silhouette.blend open.

Exports every object of the "Silhouette" collection as an OBJ part for the mod:
  src/main/resources/assets/brokenworld/models/entity/silhouette/<part>.obj  (+ pivots.json)

- Part name = object's "part" custom property, or the object name. Objects with the same part are merged.
- The object's origin is the part's pivot (the head turns around it, arms/legs swing around it).
- Blender is Z-up facing -Y; the mod wants Y-up facing -Z, so (x, y, z) -> (-x, z, y).
- The whole figure is scaled to TARGET_HEIGHT blocks.
- The parts are exported in the rest pose; the parent of every part goes to poses.json ("parents"), and so do the
  poses: every Action named "<pose>__<object>" (see tools/blender_poses.py), sampled at the key frames listed in its
  "frames" property, as local rotations (quaternions) per part plus the root's offset.
- silhouette.png fades from charcoal (left) to pure black (right). A float vertex attribute "hole"
  (0..1, see tools/blender_face.py) picks the column, so eye sockets and the mouth darken smoothly.
  Teeth and eyes use their own textures.
- A simplified copy of every part (Decimate, LOD_RATIO) goes to models/entity/silhouette/lod/ - drawn far away.
"""
import json
import math
import os

import bpy

ROOT = r"D:\BrokenWorld"
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "brokenworld", "models", "entity", "silhouette")
TARGET_HEIGHT = 2.5
COLLECTION = "Silhouette"
# Far away the mod draws a simplified copy (models/entity/silhouette/lod/): same parts, about a quarter of the faces.
LOD_DIR = os.path.join(OUT, "lod")
LOD_RATIO = 0.22


def to_mod(v):
    return (-v[0], v[2], v[1])


def frac(x):
    return x - math.floor(x)


def uv_for(material_name, p, hole):
    if material_name == "SilhouetteEyes":
        return 0.5, 0.5
    # grain: spread UVs by position so the noise texture shows a little
    u = frac(p[0] * 7.3 + p[2] * 3.1)
    v = frac(p[1] * 5.7 + p[2] * 6.9)
    if material_name == "SilhouetteTeeth":
        return 0.05 + u * 0.9, 0.05 + v * 0.9
    # skin: one texture row, so the grain never smears into stripes; the column is the darkness
    return 0.03 + hole * 0.94, 0.5


def to_mod_quat(q):
    """A Blender rotation as a rotation in the mod's space, as [x, y, z, w]."""
    return [round(-q.x, 6), round(q.z, 6), round(q.y, 6), round(q.w, 6)]


def rest_pose():
    for ob in bpy.data.collections[COLLECTION].objects:
        if ob.animation_data:
            ob.animation_data.action = None
        if ob.rotation_mode == 'QUATERNION':
            ob.rotation_quaternion = (1, 0, 0, 0)
        if ob.name == "root":
            ob.location = (0, 0, 0)
    bpy.context.view_layer.update()


def export_poses(scale):
    col = {o.name: o for o in bpy.data.collections[COLLECTION].objects}
    parents = {o.name: o.parent.name for o in col.values() if o.parent and o.type == 'MESH' and o.parent.name != "root"}
    by_pose = {}
    for act in bpy.data.actions:
        if "__" in act.name:
            pose, obj = act.name.split("__", 1)
            if obj in col:
                by_pose.setdefault(pose, {})[obj] = act
    fps = bpy.context.scene.render.fps
    poses = {}
    for pose, acts in sorted(by_pose.items()):
        frames = sorted({int(f) for a in acts.values() for f in a.get("frames", [1])})
        rest_pose()
        for obj, act in acts.items():
            col[obj].animation_data_create()
            col[obj].animation_data.action = act
        samples = []
        for f in frames:
            bpy.context.scene.frame_set(f)
            parts = {name: to_mod_quat(col[name].rotation_quaternion) for name in acts if name != "root"}
            sample = {"t": round((f - frames[0]) / fps, 4), "parts": parts}
            if "root" in acts:
                r = col["root"]
                sample["root"] = {"q": to_mod_quat(r.rotation_quaternion),
                                  "p": [round(c * scale, 5) for c in to_mod(r.location)]}
            samples.append(sample)
        poses[pose] = {"length": round((frames[-1] - frames[0]) / fps, 4), "frames": samples}
    rest_pose()
    bpy.context.scene.frame_set(1)
    with open(os.path.join(OUT, "poses.json"), "w", encoding="utf-8") as fh:
        json.dump({"parents": parents, "poses": poses}, fh, indent=1)
    return sorted(poses)


def main():
    rest_pose()
    depsgraph = bpy.context.evaluated_depsgraph_get()
    objects = [o for o in bpy.data.collections[COLLECTION].objects if o.type == 'MESH']

    # figure height -> scale
    top = 0.0
    for ob in objects:
        ev = ob.evaluated_get(depsgraph)
        me = ev.to_mesh()
        top = max(top, max((ob.matrix_world @ v.co).z for v in me.vertices))
        ev.to_mesh_clear()
    scale = TARGET_HEIGHT / top

    counts = export_meshes(objects, scale, OUT, None)
    lod = export_meshes(objects, scale, LOD_DIR, LOD_RATIO)
    poses = export_poses(scale)
    return counts, {n: c[1] for n, c in lod.items()}, round(top, 3), round(scale, 4), poses


def export_meshes(objects, scale, out_dir, ratio):
    """Writes every part as <out_dir>/<part>.obj; with a ratio, through a temporary Decimate modifier."""
    temp = []
    if ratio is not None:
        for ob in objects:
            if ob.get("part", ob.name) not in ("eyes", "teeth", "teeth_lower"):
                m = ob.modifiers.new("LOD", 'DECIMATE')
                m.ratio = ratio
                temp.append((ob, m))
    bpy.context.view_layer.update()
    depsgraph = bpy.context.evaluated_depsgraph_get()
    try:
        parts, pivots = collect(objects, scale, depsgraph)
    finally:
        for ob, m in temp:
            ob.modifiers.remove(m)
        bpy.context.view_layer.update()
    write(parts, out_dir)
    if ratio is None:
        with open(os.path.join(out_dir, "pivots.json"), "w", encoding="utf-8") as fh:
            json.dump(pivots, fh, indent=2)
    return {n: (len(p["v"]), len(p["f"])) for n, p in parts.items()}


def collect(objects, scale, depsgraph):
    parts = {}
    pivots = {}
    for ob in objects:
        name = ob.get("part", ob.name)
        piv_world = (bpy.data.objects[name] if name in bpy.data.objects else ob).matrix_world.translation.copy()
        if name == ob.name:
            pivots[name] = [round(c * scale, 5) for c in to_mod(piv_world)]
        part = parts.setdefault(name, {"v": [], "vt": [], "vn": [], "f": []})

        ev = ob.evaluated_get(depsgraph)
        me = ev.to_mesh()
        mw = ob.matrix_world
        nm = mw.to_3x3().inverted().transposed()
        base_v = len(part["v"])
        for v in me.vertices:
            w = mw @ v.co
            part["v"].append(to_mod(((w - piv_world) * scale)))
        normals = me.corner_normals
        hole_attr = me.attributes.get("hole")
        holes = [d.value for d in hole_attr.data] if hole_attr else None
        for poly in me.polygons:
            mat = me.materials[poly.material_index].name if me.materials else ""
            face = []
            for li in poly.loop_indices:
                vi = me.loops[li].vertex_index
                n = (nm @ normals[li].vector).normalized()
                part["vn"].append(to_mod(n))
                part["vt"].append(uv_for(mat, mw @ me.vertices[vi].co, holes[vi] if holes else 0.0))
                face.append((base_v + vi + 1, len(part["vt"]), len(part["vn"])))
            part["f"].append(face)
        ev.to_mesh_clear()

    return parts, pivots


def write(parts, out_dir):
    os.makedirs(out_dir, exist_ok=True)
    for old in os.listdir(out_dir):
        if old.endswith(".obj"):
            os.remove(os.path.join(out_dir, old))
    for name, p in parts.items():
        lines = [f"# {name} - exported from blender/silhouette.blend by tools/blender_export.py", f"o {name}"]
        lines += [f"v {x:.5f} {y:.5f} {z:.5f}" for x, y, z in p["v"]]
        lines += [f"vt {u:.4f} {1 - v:.4f}" for u, v in p["vt"]]
        lines += [f"vn {x:.4f} {y:.4f} {z:.4f}" for x, y, z in p["vn"]]
        lines += ["f " + " ".join(f"{a}/{b}/{c}" for a, b, c in f) for f in p["f"]]
        with open(os.path.join(out_dir, f"{name}.obj"), "w", encoding="utf-8") as fh:
            fh.write("\n".join(lines) + "\n")


result = main()
print(result)
