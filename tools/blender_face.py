"""Run inside Blender (with blender/silhouette.blend open): (re)builds the silhouette's head, eyes and teeth
in the "Silhouette" collection. Tweak the constants below to change the face, then run tools/blender_export.py.

Face: long skull hanging forward, deep black eye sockets (one lower and smaller), skull-like nose hole,
hollow cheeks, brow ridge, a long hanging jaw with a huge gaping mouth and thin uneven needle teeth.

The lower part of the head below the mouth line is a separate object "jaw" (with the lower teeth in "teeth_lower"),
hinged at the back, so the mouth can open (pose "jaw_open", see tools/blender_poses.py)."""
import math
import random

import bmesh
import bpy
from mathutils import Matrix, Vector

COLLECTION = "Silhouette"
PIVOT = Vector((0.0, -0.33, 2.44))       # neck: the head turns around this point
CENTER = Vector((0.0, -0.45, 2.59))
RADII = Vector((0.125, 0.15, 0.215))
TILT = 0.4                                 # radians, head hangs forward
MOUTH_C, MOUTH_W, MOUTH_H = -0.46, 0.38, 0.5
# (center on the unit sphere, radius, depth); the right one is lower
EYES = [(Vector((0.37, -0.85, 0.25)), 0.36, 0.3), (Vector((-0.37, -0.85, 0.15)), 0.33, 0.3)]
TEETH = 34
JAW_SPLIT = -0.46                          # cut between head and jaw: right through the middle of the mouth
JAW_BACK = 0.1                              # only the front part below the cut is jaw; the back of the skull stays
JAW_WIDTH = 0.55                            # ...and only the middle: the cheeks stay on the head
JAW_HINGE_UNIT = Vector((0.0, 0.1, -0.44))  # where the jaw turns: behind the corners of the mouth
# dark inside, seen when the mouth opens - kept inside the head (it used to stick out under the chin)
THROAT_UNIT = (Vector((0.0, -0.3, -0.45)), Vector((0.46, 0.48, 0.42)))


def mat(name, color):
    m = bpy.data.materials.get(name) or bpy.data.materials.new(name)
    m.diffuse_color = color
    return m


def falloff(d, r):
    """Smooth bump: 1 at the centre, 0 at radius r, flat at both ends (no folds)."""
    if d >= r:
        return 0.0
    t = 1 - d / r
    return t * t * (3 - 2 * t)


def sculpt(p):
    """Unit sphere point -> (deformed point, how much it is a hole 0..1)."""
    n = p.normalized()
    q = p.copy()
    hole = 0.0
    for c, r, depth in EYES:
        f = falloff((p - c).length, r)
        q -= n * depth * f
        hole = max(hole, f)
    for sx in (1, -1):
        q += n * 0.09 * falloff((p - Vector((0.37 * sx, -0.78, 0.52))).length, 0.26)   # brow ridge
        q -= n * 0.18 * falloff((p - Vector((0.64 * sx, -0.6, -0.2))).length, 0.36)   # hollow cheeks
    f = falloff((p - Vector((0.0, -0.97, -0.03))).length, 0.17)                        # nose hole
    q -= n * 0.2 * f
    hole = max(hole, f)
    if p.y < -0.15:                                                                    # gaping mouth
        e = math.hypot(p.x / MOUTH_W, (p.z - MOUTH_C) / MOUTH_H)
        if e < 1:
            f = (1 - e * e) ** 0.5
            q -= n * 0.95 * f
            hole = max(hole, min(1.0, f * 1.6))
    return q, hole


def place(q):
    """Unit-sphere space -> world."""
    v = Vector((q.x * RADII.x, q.y * RADII.y, q.z * RADII.z))
    if v.z < -0.02:                                  # long jaw hanging down
        v.z = -0.02 + (v.z + 0.02) * 1.5
    return CENTER + Matrix.Rotation(TILT, 3, 'X') @ v


def replace(name, bm, materials, pivot=None):
    col = bpy.data.collections[COLLECTION]
    old = bpy.data.objects.get(name)
    if old:
        old_mesh = old.data
        bpy.data.objects.remove(old, do_unlink=True)
        if old_mesh.users == 0:
            bpy.data.meshes.remove(old_mesh)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    ob = bpy.data.objects.new(name, me)
    ob.location = PIVOT if pivot is None else pivot
    col.objects.link(ob)
    for m in materials:
        me.materials.append(m)
    return ob


def merge_into(bm, tmp):
    me_tmp = bpy.data.meshes.new("tmp")
    tmp.to_mesh(me_tmp)
    tmp.free()
    bm.from_mesh(me_tmp)
    bpy.data.meshes.remove(me_tmp)


def build():
    skin = mat("SilhouetteBlack", (0.07, 0.066, 0.075, 1))
    glow = mat("SilhouetteEyes", (1, 1, 1, 1))
    bone = mat("SilhouetteTeeth", (0.72, 0.68, 0.58, 1))

    # head; per-vertex "hole" (0 = skin, 1 = pitch black) makes sockets and mouth fade smoothly to black
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=72, v_segments=48, radius=1.0)
    hole_layer = bm.verts.layers.float.new("hole")
    unit = {}
    for v in bm.verts:
        unit[v.index] = v.co.copy()
        q, h = sculpt(v.co.copy())
        v[hole_layer] = min(1.0, h * 1.3)
        v.co = place(q) - PIVOT
    bm.faces.ensure_lookup_table()
    jaw_faces = set()
    for f in bm.faces:
        cz = sum(unit[v.index].z for v in f.verts) / len(f.verts)
        cy = sum(unit[v.index].y for v in f.verts) / len(f.verts)
        cx = sum(unit[v.index].x for v in f.verts) / len(f.verts)
        if cz < JAW_SPLIT and cy < JAW_BACK and abs(cx) < JAW_WIDTH:
            jaw_faces.add(f.index)
    for f in bm.faces:
        f.smooth = True
    hinge = place(JAW_HINGE_UNIT)

    def part(keep_jaw):
        b = bm.copy()
        b.faces.ensure_lookup_table()
        doomed = [f for f in b.faces if (f.index in jaw_faces) != keep_jaw]
        bmesh.ops.delete(b, geom=doomed, context='FACES')
        loose = [v for v in b.verts if not v.link_faces]
        bmesh.ops.delete(b, geom=loose, context='VERTS')
        return b

    head_bm = part(False)
    # the throat: a pitch-black hollow inside, so an open mouth shows darkness instead of the empty head
    throat = bmesh.new()
    bmesh.ops.create_uvsphere(throat, u_segments=24, v_segments=14, radius=1.0)
    for f in throat.faces:
        f.smooth = True
    centre, radii = THROAT_UNIT
    for v in throat.verts:
        u = Vector((centre.x + v.co.x * radii.x, centre.y + v.co.y * radii.y, centre.z + v.co.z * radii.z))
        v.co = place(u) - PIVOT
    throat_me = bpy.data.meshes.new("throat_tmp")
    throat.to_mesh(throat_me)
    throat.free()
    before = len(head_bm.verts)
    head_bm.from_mesh(throat_me)
    bpy.data.meshes.remove(throat_me)
    layer = head_bm.verts.layers.float.get("hole")
    head_bm.verts.ensure_lookup_table()
    for i in range(before, len(head_bm.verts)):
        head_bm.verts[i][layer] = 1.0
    head = replace("head", head_bm, [skin])
    jaw_bm = part(True)
    # a black inner skin for the jaw, so its inside reads as the dark of an open mouth, not as more skin
    jaw_bm.normal_update()
    hole_l = jaw_bm.verts.layers.float.get("hole")
    centre = place(Vector((0, 0, 0))) - PIVOT
    inner = bmesh.ops.duplicate(jaw_bm, geom=list(jaw_bm.faces))
    inner_verts = [g for g in inner["geom"] if isinstance(g, bmesh.types.BMVert)]
    inner_faces = [g for g in inner["geom"] if isinstance(g, bmesh.types.BMFace)]
    for v in inner_verts:
        v.co = v.co + (centre - v.co).normalized() * 0.012
        v[hole_l] = 1.0
    bmesh.ops.reverse_faces(jaw_bm, faces=inner_faces)
    bmesh.ops.translate(jaw_bm, vec=PIVOT - hinge, verts=jaw_bm.verts)  # relative to the hinge
    replace("jaw", jaw_bm, [skin], pivot=hinge)
    bm.free()

    # eyes: slits deep in the sockets
    bm = bmesh.new()
    for (c, r, depth), size, slant in zip(EYES, (1.0, 0.75), (0.4, -0.4)):
        tmp = bmesh.new()
        bmesh.ops.create_uvsphere(tmp, u_segments=10, v_segments=6, radius=1.0)
        socket = place(sculpt(c.normalized())[0])
        out = (socket - place(Vector((0, 0, 0)))).normalized()
        m = (Matrix.Translation(socket + out * 0.006 - PIVOT) @ Matrix.Rotation(TILT, 4, 'X')
             @ Matrix.Rotation(slant, 4, 'Y') @ Matrix.Diagonal((0.026 * size, 0.006, 0.0075 * size, 1)))
        bmesh.ops.transform(tmp, matrix=m, verts=tmp.verts)
        merge_into(bm, tmp)
    replace("eyes", bm, [glow])

    # teeth: thin uneven needles on both jaws, pointing into the mouth (lower ones move with the jaw)
    bm = bmesh.new()
    bm_lower = bmesh.new()
    rnd = random.Random(21)
    half = TEETH // 2
    for i in range(TEETH):
        upper = i < half
        k = i if upper else i - half
        th = math.radians(14 + k * (152 / (half - 1))) * (1 if upper else -1)
        base_u = Vector((MOUTH_W * math.cos(th) * 0.9, 0, MOUTH_C + MOUTH_H * math.sin(th) * 0.9))
        base_u.y = -math.sqrt(max(0.0, 1 - base_u.x ** 2 - base_u.z ** 2))
        target = Vector((base_u.x * 0.25, -0.5, MOUTH_C))
        length = rnd.uniform(0.3, 0.75) * (1.0 if abs(math.cos(th)) < 0.8 else 0.6)
        base = place(sculpt(base_u)[0])
        tip_u = base_u.lerp(target, length)
        tip = place(Vector((tip_u.x, -math.sqrt(max(0.0, 1 - tip_u.x ** 2 - tip_u.z ** 2)) * 0.8, tip_u.z)))
        axis = (tip - base).normalized()
        side = axis.cross(Vector((0, 1, 0))).normalized() * rnd.uniform(0.0035, 0.006)
        up = axis.cross(side).normalized() * side.length
        target_bm, origin = (bm, PIVOT) if upper else (bm_lower, hinge)
        ring = [target_bm.verts.new(base + side * math.cos(a) + up * math.sin(a) - origin) for a in (0, 1.57, 3.14, 4.71)]
        t = target_bm.verts.new(tip - origin)
        for j in range(4):
            target_bm.faces.new((ring[j], ring[(j + 1) % 4], t))
        target_bm.faces.new(list(reversed(ring)))
    replace("teeth", bm, [bone])
    replace("teeth_lower", bm_lower, [bone], pivot=hinge)
    return len(head.data.polygons)


print("head faces:", build())
