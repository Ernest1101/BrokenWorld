"""Run inside Blender (with blender/silhouette.blend open): renders the mod's cover art.

A separate scene "Cover" (the silhouette's collection is linked into it, the cover's own blocks live in "CoverSet"):
a night forest of Minecraft blocks - some of them with the wrong vanilla textures, like the broken world - in fog
and cold moonlight; the silhouette peeks out from behind a trunk, eyes glowing.

Background:  blender -b blender/silhouette.blend --python tools/render_cover.py
Writes cover/cover_raw.png (1920x1080) and cover/icon_raw.png (512x512); tools/cover_text.py adds the title.
Textures: blender/cover_textures (vanilla block textures taken from the game's jar)."""
import math
import os
import random

import bpy
from mathutils import Vector

TEX = r"D:\BrokenWorld\blender\cover_textures"
OUT_DIR = r"D:\BrokenWorld\cover"
B = 2.796 / 2.5          # one block in Blender units (the silhouette is 2.5 blocks tall)
GRASS = (0.42, 0.66, 0.27)
LEAVES = (0.30, 0.55, 0.20)
HEAD = Vector((0.85, -0.45, 2.45))   # its head while peeking (pose "peek")


# ---------------------------------------------------------------- scene

def cover_scene():
    sc = bpy.data.scenes.get("Cover") or bpy.data.scenes.new("Cover")
    sil = bpy.data.collections["Silhouette"]
    if sil.name not in [c.name for c in sc.collection.children]:
        sc.collection.children.link(sil)
    col = bpy.data.collections.get("CoverSet")
    if col is None:
        col = bpy.data.collections.new("CoverSet")
        sc.collection.children.link(col)
    for ob in list(col.objects):
        bpy.data.objects.remove(ob, do_unlink=True)
    return sc, col


# ---------------------------------------------------------------- block materials

def image(name):
    img = bpy.data.images.get(name + ".png")
    if img is None:
        img = bpy.data.images.load(os.path.join(TEX, name + ".png"))
    return img


def block_material(name, tint=None, cutout=False):
    key = "Cover_" + name + ("_t" if tint else "")
    mat = bpy.data.materials.get(key)
    if mat is not None:
        return mat
    mat = bpy.data.materials.new(key)
    mat.use_nodes = True
    nodes, links = mat.node_tree.nodes, mat.node_tree.links
    bsdf = next(n for n in nodes if n.type == "BSDF_PRINCIPLED")
    tex = nodes.new("ShaderNodeTexImage")
    tex.image = image(name)
    tex.interpolation = 'Closest'   # crisp Minecraft pixels
    color = tex.outputs["Color"]
    if tint:
        mix = nodes.new("ShaderNodeMix")
        mix.data_type = 'RGBA'
        mix.blend_type = 'MULTIPLY'
        mix.inputs["Factor"].default_value = 1.0
        links.new(color, mix.inputs[6])
        mix.inputs[7].default_value = (*tint, 1.0)
        color = mix.outputs[2]
    links.new(color, bsdf.inputs["Base Color"])
    bsdf.inputs["Roughness"].default_value = 0.9
    if cutout:
        links.new(tex.outputs["Alpha"], bsdf.inputs["Alpha"])
        if hasattr(mat, "surface_render_method"):
            mat.surface_render_method = 'DITHERED'
        else:
            mat.blend_method = 'CLIP'
    return mat


def block_mesh(key, top, side, bottom):
    """A 1-block cube (centered, size B) with a full texture on every face: materials top / side / bottom."""
    me = bpy.data.meshes.get("CoverBlock_" + key)
    if me is not None:
        return me
    h = B / 2
    v = [(-h, -h, -h), (h, -h, -h), (h, h, -h), (-h, h, -h), (-h, -h, h), (h, -h, h), (h, h, h), (-h, h, h)]
    faces = [(4, 5, 6, 7), (3, 2, 1, 0), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)]
    me = bpy.data.meshes.new("CoverBlock_" + key)
    me.from_pydata(v, [], faces)
    uv = me.uv_layers.new()
    corners = [(0, 0), (1, 0), (1, 1), (0, 1)]
    for poly in me.polygons:
        for i, li in enumerate(poly.loop_indices):
            uv.data[li].uv = corners[i]
        poly.material_index = 0 if poly.index == 0 else (2 if poly.index == 1 else 1)
    for m in (top, side, bottom):
        me.materials.append(m)
    me.update()
    return me


KINDS = {}


def kind(key):
    """Block kinds by name: 'grass', 'oak_log', 'oak_leaves', or any plain texture name."""
    if key in KINDS:
        return KINDS[key]
    if key == "grass":
        me = block_mesh(key, block_material("grass_block_top", GRASS), block_material("grass_block_side"),
                        block_material("dirt"))
    elif key.endswith("_log"):
        top = block_material("oak_log_top")
        me = block_mesh(key, top, block_material(key), top)
    elif key == "oak_leaves":
        m = block_material("oak_leaves", LEAVES, cutout=True)
        me = block_mesh(key, m, m, m)
    elif key == "cherry_leaves":
        m = block_material("cherry_leaves", cutout=True)
        me = block_mesh(key, m, m, m)
    else:
        m = block_material(key)
        me = block_mesh(key, m, m, m)
    KINDS[key] = me
    return me


def place(col, key, x, y, z):
    """A block at block coordinates x, y, z (z = 0 is the first layer above the ground)."""
    ob = bpy.data.objects.new(f"b_{key}", kind(key))
    ob.location = (x * B, y * B, z * B + B / 2)
    col.objects.link(ob)
    return ob


# ---------------------------------------------------------------- the broken forest

WRONG_GROUND = ["stone", "sand", "netherrack", "diamond_ore", "mossy_cobblestone", "redstone_ore", "dirt",
                "gold_block"]
WRONG_LOG = ["birch_log", "stone", "diamond_ore", "bricks", "crafting_table_front", "note_block", "spruce_log"]
WRONG_LEAVES = ["cherry_leaves", "netherrack", "gold_block", "sand"]


def tree(col, rnd, x, y, height=5, broken=0.0, trunk="oak_log"):
    log = trunk
    if rnd.random() < broken:
        log = rnd.choice(WRONG_LOG)
    for z in range(height):
        # a broken trunk is broken in patches, not all of it
        place(col, log if (log == trunk or rnd.random() < 0.8) else trunk, x, y, z)
    leaves = "oak_leaves"
    if rnd.random() < broken * 0.7:
        leaves = rnd.choice(WRONG_LEAVES)
    top = height
    for dz, r in ((-2, 2), (-1, 2), (0, 1), (1, 1)):
        for dx in range(-r, r + 1):
            for dy in range(-r, r + 1):
                if dx == 0 and dy == 0 and dz < 0:
                    continue
                if r == 2 and abs(dx) == 2 and abs(dy) == 2 and rnd.random() < 0.6:
                    continue
                if dz == 1 and abs(dx) + abs(dy) > 1:
                    continue
                place(col, leaves, x + dx, y + dy, top + dz)


def build_world(col):
    rnd = random.Random(1408)
    # ground: grass, here and there a block that is not what it should be
    for x in range(-16, 17):
        for y in range(-14, 34):
            wrong = rnd.random() < 0.16
            place(col, rnd.choice(WRONG_GROUND) if wrong else "grass", x, y, -1)
    # the tree it hides behind, right in front of it (its own trunk is fine - it is the forest that is broken)
    tree(col, rnd, -0.2, -0.75, height=6, broken=0.0)
    # the forest around: further away, more and more of it wrong
    spots = [(-6, 4), (-9, 10), (5, 7), (9, 3), (-4, 15), (3, 17), (10, 13), (-12, 5), (12, 21), (-8, 23), (0, 27),
             (6, 29), (-14, 17), (15, 9), (-16, 27), (16, 31)]
    for i, (x, y) in enumerate(spots):
        tree(col, rnd, x, y, height=rnd.randint(4, 7), broken=0.45 + min(0.45, y / 40))


# ---------------------------------------------------------------- light, fog, camera

def look_at(ob, target):
    ob.rotation_euler = (Vector(target) - ob.location).to_track_quat('-Z', 'Y').to_euler()


def lights(col):
    def add(name, kind, loc, energy, color, size=0.1, target=None):
        data = bpy.data.lights.new(name, kind)
        data.energy = energy
        data.color = color
        if hasattr(data, "shadow_soft_size"):
            data.shadow_soft_size = size
        ob = bpy.data.objects.new(name, data)
        ob.location = loc
        col.objects.link(ob)
        if target is not None:
            look_at(ob, target)
        return ob

    moon = add("CoverMoon", 'SUN', (0, 0, 20), 2.6, (0.55, 0.66, 1.0), target=(4, -6, 0))
    moon.data.angle = math.radians(2.0)
    moon.location = (-20, 40, 30)
    look_at(moon, (0, 0, 0))
    add("CoverRim", 'POINT', (2.2, 1.6, 3.0), 120.0, (0.6, 0.72, 1.0), 0.3)          # outlines its head from behind
    glow = add("CoverEyesGlow", 'POINT', HEAD + Vector((0, -0.5, 0)), 6.0, (1.0, 0.95, 0.9), 0.05)
    glow.data.volume_factor = 0.0   # lights the face, not the fog around it
    add("CoverFill", 'AREA', (6.0, -14.0, 6.0), 900.0, (0.45, 0.55, 0.9), 6.0, target=(0, 4, 1))   # cold night fill


def world(sc):
    w = bpy.data.worlds.get("CoverWorld") or bpy.data.worlds.new("CoverWorld")
    sc.world = w
    w.use_nodes = True
    nt = w.node_tree
    for n in list(nt.nodes):
        nt.nodes.remove(n)
    out = nt.nodes.new("ShaderNodeOutputWorld")
    bg = nt.nodes.new("ShaderNodeBackground")
    bg.inputs["Color"].default_value = (0.012, 0.018, 0.04, 1)
    bg.inputs["Strength"].default_value = 1.0
    vol = nt.nodes.new("ShaderNodeVolumePrincipled")
    vol.inputs["Color"].default_value = (0.6, 0.68, 0.9, 1)
    vol.inputs["Density"].default_value = 0.022
    nt.links.new(bg.outputs["Background"], out.inputs["Surface"])
    nt.links.new(vol.outputs["Volume"], out.inputs["Volume"])


def camera(col, name, loc, target, lens):
    data = bpy.data.cameras.new(name)
    data.lens = lens
    ob = bpy.data.objects.new(name, data)
    ob.location = loc
    col.objects.link(ob)
    look_at(ob, target)
    return ob


# ---------------------------------------------------------------- render

def run(ns_poses, setup_materials):
    sc, col = cover_scene()
    window = bpy.context.window   # None when Blender runs in the background (blender -b)
    before = window.scene if window else None
    if window:
        window.scene = sc
    try:
        build_world(col)
        lights(col)
        world(sc)
        setup_materials()
        ns_poses["show"]("peek", 1)
        # its mouth is closed: no teeth (like in the game)
        hidden = [bpy.data.objects[n] for n in ("teeth", "teeth_lower")]
        for ob in hidden:
            ob.hide_render = True
        for engine in ("BLENDER_EEVEE_NEXT", "BLENDER_EEVEE"):
            try:
                sc.render.engine = engine
                break
            except TypeError:
                continue
        if hasattr(sc.eevee, "volumetric_end"):
            sc.eevee.volumetric_end = 80.0
        if hasattr(sc.eevee, "taa_render_samples"):
            sc.eevee.taa_render_samples = 64
        try:
            sc.view_settings.view_transform = 'AgX'
        except TypeError:
            pass
        sc.render.image_settings.file_format = 'PNG'
        sc.render.image_settings.color_mode = 'RGB'
        sc.render.film_transparent = False
        os.makedirs(OUT_DIR, exist_ok=True)
        shots = [
            ("cover_raw.png", 1920, 1080, camera(col, "CoverCam", (4.0, -10.0, 1.8), (0.3, 2.0, 2.45), 30)),
            ("icon_raw.png", 512, 512, camera(col, "IconCam", (1.9, -3.4, 2.55), HEAD + Vector((-0.15, 0, -0.1)), 45)),
        ]
        for name, w, h, cam in shots:
            sc.camera = cam
            sc.render.resolution_x, sc.render.resolution_y = w, h
            sc.render.resolution_percentage = 100
            sc.render.filepath = os.path.join(OUT_DIR, name)
            bpy.ops.render.render(write_still=True, scene=sc.name)
        return [s[0] for s in shots]
    finally:
        for n in ("teeth", "teeth_lower"):
            bpy.data.objects[n].hide_render = False
        ns_poses["show"](None)
        if window:
            window.scene = before
