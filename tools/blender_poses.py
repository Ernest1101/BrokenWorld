"""Run inside Blender (with blender/silhouette.blend open) after blender_body.py / blender_face.py.

1. Rigs the silhouette: an Empty "root" at the feet; torso, spine, arms, legs and head are its children; eyes, teeth
   and jaw are children of the head; teeth_lower is a child of the jaw. (Parented "keep transform", so nothing moves.)
2. Builds the poses as keyframed Actions, one Action per object and pose, named "<pose>__<object>". Each Action has a
   custom property "frames" listing its key frames. Change them in Blender (Dope Sheet / Action Editor) and run
   tools/blender_export.py again: it writes models/entity/silhouette/poses.json for the mod.

Poses:  peek (leans out from behind a tree, gripping the bark), crawl (on its belly, pulling itself with its arms,
        looping), hang (upside down from the ceiling, arms dangling), jaw_open (the mouth opens wide),
        spider (bent over backwards, belly up, running on hands and feet, head upside down - looping),
        window (leaning against a window, face and both hands pressed to the glass).
Preview one in Blender:  show("crawl", 13)   - and  show(None)  to go back to the rest pose."""
import math

import bpy
from mathutils import Euler, Matrix, Vector

COLLECTION = "Silhouette"
HIERARCHY = {  # child: parent
    "torso": "root", "spine": "root", "right_arm": "root", "left_arm": "root",
    "right_leg": "root", "left_leg": "root", "head": "root",
    "eyes": "head", "teeth": "head", "jaw": "head", "teeth_lower": "jaw",
    "right_forearm": "right_arm", "left_forearm": "left_arm", "right_shin": "right_leg", "left_shin": "left_leg",
}
PI = math.pi

# Limbs can also be aimed: a direction (in the body's own space: +x its left, -y its front, +z up) for the upper and
# the lower part; the rotations are worked out from where they point in the rest pose.
REST = {  # rest direction of each limb part, for its side s (+1 left, -1 right)
    "arm": lambda s: (0.08 * s, 0.02, -0.6), "forearm": lambda s: (0.05 * s, -0.15, -0.69),
    "leg": lambda s: (0.05 * s, -0.08, -0.74), "shin": lambda s: (0.01 * s, 0.1, -0.63),
}


def aim(rest, target, parent=None):
    """Euler rotation turning a part from its rest direction to point at target (seen from its parent's rotation)."""
    t = Vector(target).normalized()
    if parent is not None:
        t = Euler(parent, 'XYZ').to_quaternion().inverted() @ t
    e = Vector(rest).normalized().rotation_difference(t).to_euler('XYZ')
    return (e.x, e.y, e.z)


def limb(side, s, upper_dir, lower_dir):
    """Keys for a whole arm ("arm") or leg ("leg") on one side ("right" / "left", s = -1 / +1)."""
    upper, lower = ("arm", "forearm") if side.endswith("arm") else ("leg", "shin")
    name = "right" if s < 0 else "left"
    e1 = aim(REST[upper](s), upper_dir)
    e2 = aim(REST[lower](s), lower_dir, e1)
    return {f"{name}_{upper}": (e1, None), f"{name}_{lower}": (e2, None)}


def spider_frame(phase):
    """phase +1 / -1: which diagonal pair (right arm + left leg, or left arm + right leg) is reaching ahead."""
    keys = {
        # flipped belly up, head first (bent over backwards), body about as high as a knee
        "root": ((PI / 2, PI, 0.0), (0.0, 1.8, SPIDER_HEIGHT)),
        "head": ((-1.35, 0.0, 0.0), None),  # upside down, staring ahead
    }
    for s in (-1, 1):
        reach = 0.35 * phase * (-s)  # the right arm (s=-1) reaches when phase is +1
        # body space while flipped: its back (+y) is the ground, its head end (+z) is ahead
        # elbows far out to the sides like a spider's, forearms down to the ground
        keys.update(limb("arm", s, (0.8 * s, 0.45, 0.3 + reach), (0.3 * s, 1.0, 0.6 + reach * 0.5)))
        keys.update(limb("leg", s, (0.45 * s, 0.6, -0.45 - reach), (0.1 * s, 1.0, -0.1 - reach * 0.5)))
    return keys


def window_frame():
    keys = {"root": ((0.12, 0.0, 0.0), None),  # leaning on the glass
            "head": ((0.05, 0.0, 0.12), None)}
    for s in (-1, 1):
        # elbows out and down, forearms up: both palms flat on the glass beside its face, claws spread up
        keys.update(limb("arm", s, (0.28 * s, -0.25, -0.35), (-0.2 * s, -0.02, 0.7)))
    return keys


SPIDER_HEIGHT = 1.1

# pose -> frame -> object -> (euler xyz radians, location or None). Blender space: Z up, the creature faces -Y.
# Elbows bend with -X (the hand comes forward), knees with +X (the foot goes back) - standing. Lying on its belly with
# the arms above the head, +X brings the hands down to the ground.
POSES = {
    "peek": {
        1: {
            "root": ((0.0, 0.38, 0.0), None),                     # leaning out sideways
            "head": ((-0.1, 0.32, 0.0), None),                    # the head tipped even further
            "right_arm": ((-1.95, 0.0, -0.35), None),              # reaching up around the trunk
            "right_forearm": ((-0.9, 0.0, 0.0), None),             # ...the long fingers hooked into the bark
            "left_arm": ((-0.25, 0.0, 0.1), None),
            "left_forearm": ((-0.45, 0.0, 0.0), None),
            "right_shin": ((0.2, 0.0, 0.0), None),
        },
    },
    "crawl": {
        1: {
            "root": ((1.45, 0.0, 0.0), (0.0, 0.0, 0.14)),         # flat on its belly
            "head": ((-1.15, 0.0, 0.0), None),                    # but looking up at you
            "right_arm": ((PI + 0.3, -0.35, 0.08), None),          # arms reaching far ahead, wider than the head
            "right_forearm": ((0.2, 0.0, 0.0), None),
            "left_arm": ((PI - 0.3, 0.35, -0.08), None),
            "left_forearm": ((1.25, 0.0, 0.0), None),              # elbow up, claws dug into the ground
            "right_leg": ((-0.15, 0.0, 0.0), None),                # legs dragged along the ground
            "right_shin": ((0.1, 0.0, 0.0), None),
            "left_leg": ((-0.3, 0.0, 0.0), None),
            "left_shin": ((0.45, 0.0, 0.0), None),                 # ...one knee pushing
        },
        13: {
            "root": ((1.45, 0.0, 0.03), (0.0, 0.0, 0.14)),
            "head": ((-1.15, 0.0, -0.08), None),
            "right_arm": ((PI - 0.3, -0.35, 0.08), None),          # ...pulling itself along
            "right_forearm": ((1.25, 0.0, 0.0), None),
            "left_arm": ((PI + 0.3, 0.35, -0.08), None),
            "left_forearm": ((0.2, 0.0, 0.0), None),
            "right_leg": ((-0.3, 0.0, 0.0), None),
            "right_shin": ((0.45, 0.0, 0.0), None),
            "left_leg": ((-0.15, 0.0, 0.0), None),
            "left_shin": ((0.1, 0.0, 0.0), None),
        },
        25: "1",  # back to frame 1: a loop
    },
    "hang": {
        1: {
            "root": ((0.0, PI, 0.0), (0.0, 0.0, 2.85)),           # upside down, feet on the ceiling
            "head": ((0.35, 0.0, 0.0), None),
            "right_arm": ((PI - 0.1, 0.0, 0.15), None),            # arms dangling down to you
            "right_forearm": ((-0.5, 0.0, 0.0), None),             # ...claws curled, reaching
            "left_arm": ((PI - 0.1, 0.0, -0.15), None),
            "left_forearm": ((-0.3, 0.0, 0.0), None),
            "right_shin": ((0.25, 0.0, 0.0), None),                # knees a little bent, gripping the ceiling
            "left_shin": ((0.15, 0.0, 0.0), None),
        },
    },
    "spider": {
        1: spider_frame(1),
        9: spider_frame(-1),
        17: "1",  # a loop
    },
    "window": {
        1: window_frame(),
    },
    "jaw_open": {
        1: {"jaw": ((-0.6, 0.0, 0.0), None)},                      # the mouth gapes open (jaw swings in and down)
    },
}


def objects():
    col = bpy.data.collections[COLLECTION]
    found = {o.name: o for o in col.objects}
    root = bpy.data.objects.get("root")
    if root is None:
        root = bpy.data.objects.new("root", None)
        root.empty_display_type = 'ARROWS'
        root.empty_display_size = 0.3
        col.objects.link(root)
    found["root"] = root
    return found


def rig(obs):
    for o in obs.values():
        o.rotation_mode = 'QUATERNION'
    # objects just (re)built by blender_body.py / blender_face.py have no world matrix until the scene updates
    bpy.context.view_layer.update()
    for child, parent in HIERARCHY.items():
        c, p = obs.get(child), obs.get(parent)
        if c is None or p is None or c.parent == p:
            continue
        world = c.matrix_world.copy()
        c.parent = p
        c.matrix_parent_inverse = p.matrix_world.inverted()
        c.matrix_world = world
        bpy.context.view_layer.update()


def rest(obs):
    for o in obs.values():
        if o.animation_data:
            o.animation_data.action = None
        o.rotation_quaternion = (1, 0, 0, 0)
    obs["root"].location = (0, 0, 0)


def build_actions(obs):
    for act in [a for a in bpy.data.actions if "__" in a.name]:
        bpy.data.actions.remove(act)
    for pose, frames in POSES.items():
        resolved = {f: (frames[int(v)] if isinstance(v, str) else v) for f, v in frames.items()}
        names = sorted({n for keys in resolved.values() for n in keys})
        for name in names:
            o = obs[name]
            act = bpy.data.actions.new(f"{pose}__{name}")
            act.use_fake_user = True
            act["frames"] = sorted(resolved)
            o.animation_data_create()
            o.animation_data.action = act
            for frame, keys in sorted(resolved.items()):
                euler, loc = keys.get(name, ((0.0, 0.0, 0.0), None))
                o.rotation_quaternion = Euler(euler, 'XYZ').to_quaternion()
                o.keyframe_insert(data_path="rotation_quaternion", frame=frame)
                if name == "root":
                    o.location = loc if loc is not None else (0.0, 0.0, 0.0)
                    o.keyframe_insert(data_path="location", frame=frame)
            o.animation_data.action = None
    rest(obs)


def show(pose, frame=1):
    """Preview a pose in the viewport (None = rest pose)."""
    obs = objects()
    rest(obs)
    if pose is None:
        return
    for name, o in obs.items():
        act = bpy.data.actions.get(f"{pose}__{name}")
        if act:
            o.animation_data_create()
            o.animation_data.action = act
    bpy.context.scene.frame_set(frame)


obs = objects()
rig(obs)
build_actions(obs)
print("poses:", sorted({a.name.split("__")[0] for a in bpy.data.actions if "__" in a.name}))
