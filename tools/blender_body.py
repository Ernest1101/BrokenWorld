"""Run inside Blender (with blender/silhouette.blend open): (re)builds the silhouette's torso, spine,
arms and legs in the "Silhouette" collection. Each part is a "skeleton" of points with a Skin + Subdivision
modifier, so it stays editable: move the points / change skin radii (Ctrl+A in edit mode) in Blender.
Then run tools/blender_export.py.

World units = blocks, Z up, the creature faces -Y. The object origin is the part's pivot in game."""
import bmesh
import bpy
from mathutils import Matrix, Vector

COLLECTION = "Silhouette"


def collection():
    col = bpy.data.collections.get(COLLECTION)
    if col is None:
        col = bpy.data.collections.new(COLLECTION)
        bpy.context.scene.collection.children.link(col)
    return col


def skin_material():
    m = bpy.data.materials.get("SilhouetteBlack") or bpy.data.materials.new("SilhouetteBlack")
    m.diffuse_color = (0.07, 0.066, 0.075, 1)
    return m


def remove(name):
    old = bpy.data.objects.get(name)
    if old:
        me = old.data
        bpy.data.objects.remove(old, do_unlink=True)
        if me and me.users == 0:
            bpy.data.meshes.remove(me)


def skin_part(name, pivot, points, edges, radii, subsurf=1):
    remove(name)
    me = bpy.data.meshes.new(name)
    me.from_pydata([(p[0] - pivot[0], p[1] - pivot[1], p[2] - pivot[2]) for p in points], edges, [])
    me.update()
    ob = bpy.data.objects.new(name, me)
    ob.location = pivot
    collection().objects.link(ob)
    me.materials.append(skin_material())
    skin = ob.modifiers.new("Skin", 'SKIN')
    skin.use_smooth_shade = True
    for i, r in enumerate(radii):
        me.skin_vertices[0].data[i].radius = r if isinstance(r, tuple) else (r, r)
    me.skin_vertices[0].data[0].use_root = True
    sub = ob.modifiers.new("Subdivision", 'SUBSURF')
    sub.levels = subsurf
    sub.render_levels = subsurf
    return ob


def torso():
    # Starved body: narrow pelvis and waist, ribcage, shoulders sloping down like a coat hanger that bent.
    # The pelvis reaches down to 1.3 so the tops of the legs sit inside it (no visible seams).
    return skin_part("torso", (0, 0, 1.375),
        [(0, 0.02, 1.3), (0, 0.0, 1.45), (0, -0.01, 1.63), (0, -0.06, 1.86), (0, -0.14, 2.06),
         (0, -0.2, 2.2), (0, -0.28, 2.35), (0, -0.33, 2.44),
         (0.13, -0.2, 2.17), (0.24, -0.21, 2.1), (-0.13, -0.2, 2.17), (-0.24, -0.21, 2.1)],
        [(0, 1), (1, 2), (2, 3), (3, 4), (4, 5), (5, 6), (6, 7), (5, 8), (8, 9), (5, 10), (10, 11)],
        [(0.13, 0.09), (0.135, 0.095), (0.09, 0.07), (0.16, 0.1), (0.17, 0.1), (0.09, 0.08), (0.05, 0.05),
         (0.045, 0.045), (0.06, 0.055), (0.045, 0.042), (0.06, 0.055), (0.045, 0.042)], subsurf=2)


def back_surface(z):
    """Y of the torso's back surface (the creature faces -Y, so the back is +Y) at height z: a ray shot at the
    back from behind, against the real (skinned, subdivided) mesh."""
    torso = bpy.data.objects["torso"]
    bpy.context.view_layer.update()
    depsgraph = bpy.context.evaluated_depsgraph_get()
    inv = torso.matrix_world.inverted()
    origin = inv @ Vector((0.0, 1.0, z))
    direction = (inv.to_3x3() @ Vector((0.0, -1.0, 0.0))).normalized()
    hit, location, _normal, _index = torso.ray_cast(origin, direction, depsgraph=depsgraph)
    return (torso.matrix_world @ location).y if hit else None


def spine():
    """Vertebrae sticking out of the hunched back, half sunk into it (exported together with the torso)."""
    remove("spine")
    heights = [1.5, 1.62, 1.74, 1.86, 1.97, 2.07, 2.16, 2.24, 2.31]
    pivot = Vector((0, 0, 1.375))
    bm = bmesh.new()
    for i, z in enumerate(heights):
        back = back_surface(z)
        if back is None:
            continue
        y = back - 0.012  # sunk into the skin: a bump, not a ball
        tmp = bmesh.new()
        bmesh.ops.create_uvsphere(tmp, u_segments=8, v_segments=6, radius=1.0)
        s = 0.022 + 0.006 * (i % 2)
        m = Matrix.Translation(Vector((0, y, z)) - pivot) @ Matrix.Diagonal((s * 1.3, s, s * 0.8, 1))
        bmesh.ops.transform(tmp, matrix=m, verts=tmp.verts)
        me_tmp = bpy.data.meshes.new("tmp")
        tmp.to_mesh(me_tmp)
        tmp.free()
        bm.from_mesh(me_tmp)
        bpy.data.meshes.remove(me_tmp)
    me = bpy.data.meshes.new("spine")
    bm.to_mesh(me)
    bm.free()
    for p in me.polygons:
        p.use_smooth = True
    ob = bpy.data.objects.new("spine", me)
    ob.location = pivot
    collection().objects.link(ob)
    me.materials.append(skin_material())
    ob["part"] = "torso"
    return ob


# Elbows and knees: every limb is two parts. The upper one ends in a bony knob around the joint; the lower one starts
# inside that knob and turns around its centre, so bending never opens a gap.
ELBOW = (0.33, -0.19, 1.5)
KNEE = (0.14, -0.07, 0.72)
ABOVE_ELBOW = (0.29, -0.19, 1.82)
ABOVE_KNEE = (0.12, -0.02, 1.08)
OVERLAP = 0.05  # how far each part reaches past the joint into the other one (skin ends get rounded off)


def past(joint, before, s, amount):
    """The point `amount` beyond the joint, going on in the direction from `before` to the joint (mirrored by s)."""
    j = Vector((joint[0] * s, joint[1], joint[2]))
    d = (j - Vector((before[0] * s, before[1], before[2]))).normalized()
    return tuple(j + d * amount)


def arm(name, s):
    """Very long, thin upper arm from inside the sloped shoulder down to a knobby elbow."""
    pivot = (0.25 * s, -0.21, 2.1)
    elbow = (ELBOW[0] * s, ELBOW[1], ELBOW[2])
    # starts deep inside the shoulder and its joint is thicker than the collarbone's end, which it swallows:
    # the arm grows out of the shoulder instead of hanging below a knob with a gap
    pts = [(0.2 * s, -0.21, 2.125), (0.25 * s, -0.21, 2.1), (ABOVE_ELBOW[0] * s, ABOVE_ELBOW[1], ABOVE_ELBOW[2]),
           elbow, past(ELBOW, ABOVE_ELBOW, s, OVERLAP)]
    edges = [(0, 1), (1, 2), (2, 3), (3, 4)]
    radii = [0.045, 0.062, 0.04, 0.052, 0.042]
    return skin_part(name, pivot, pts, edges, radii)


def forearm(name, s):
    """From inside the elbow knob down to a bony hand with three long claw fingers. Turns around the elbow."""
    pivot = (ELBOW[0] * s, ELBOW[1], ELBOW[2])
    # starts a little above the elbow, inside the upper arm: bent, that end sticks out behind like an elbow bone
    pts = [past(ELBOW, ABOVE_ELBOW, s, -OVERLAP), pivot, (0.35 * s, -0.24, 1.2), (0.37 * s, -0.3, 0.93),
           (0.38 * s, -0.34, 0.81)]
    edges = [(0, 1), (1, 2), (2, 3), (3, 4)]
    radii = [0.032, 0.04, 0.034, 0.029, 0.038]
    palm = 4
    for dx, dy, length in ((-0.035, 0.0, 0.5), (0.0, -0.03, 0.6), (0.035, 0.005, 0.46)):
        base = (0.38 * s + dx * s, -0.35 + dy, 0.77)
        mid = (base[0] + 0.01 * s, base[1] - 0.05, 0.77 - length * 0.55)
        tip = (base[0] + 0.005 * s, base[1] - 0.1, 0.77 - length)
        i = len(pts)
        pts += [base, mid, tip]
        edges += [(palm, i), (i, i + 1), (i + 1, i + 2)]
        radii += [0.016, 0.011, 0.004]
    return skin_part(name, pivot, pts, edges, radii)


def leg(name, s):
    """Long thin thigh, down to a knobby knee that sticks forward."""
    pivot = (0.1 * s, 0.0, 1.375)
    pts = [(0.09 * s, 0.01, 1.46), (ABOVE_KNEE[0] * s, ABOVE_KNEE[1], ABOVE_KNEE[2]), (KNEE[0] * s, KNEE[1], KNEE[2]),
           past(KNEE, ABOVE_KNEE, s, OVERLAP)]
    edges = [(0, 1), (1, 2), (2, 3)]
    radii = [0.085, 0.06, 0.06, 0.048]
    return skin_part(name, pivot, pts, edges, radii)


def shin(name, s):
    """From inside the knee down to a long narrow bony foot ending in a point. Turns around the knee."""
    pivot = (KNEE[0] * s, KNEE[1], KNEE[2])
    pts = [past(KNEE, ABOVE_KNEE, s, -OVERLAP), pivot, (0.15 * s, -0.01, 0.38), (0.15 * s, 0.03, 0.09),
           (0.15 * s, -0.06, 0.03), (0.155 * s, -0.2, 0.015), (0.16 * s, -0.3, 0.008)]
    edges = [(i, i + 1) for i in range(len(pts) - 1)]
    radii = [0.04, 0.048, 0.04, 0.034, (0.032, 0.02), (0.022, 0.012), (0.008, 0.005)]
    return skin_part(name, pivot, pts, edges, radii)


def build():
    torso()
    spine()
    arm("right_arm", -1)
    arm("left_arm", 1)
    forearm("right_forearm", -1)
    forearm("left_forearm", 1)
    leg("right_leg", -1)
    leg("left_leg", 1)
    shin("right_shin", -1)
    shin("left_shin", 1)
    return [o.name for o in collection().objects]


print(build())
