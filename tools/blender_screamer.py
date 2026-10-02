"""Run inside Blender (with blender/silhouette.blend open): renders the screamer image - the silhouette's face
in close-up from slightly below, lit from underneath - into
src/main/resources/assets/brokenworld/textures/gui/screamer.png.

Camera and lights live in a separate "Screamer" collection, so tools/blender_export.py ignores them."""
import os

import bpy
from mathutils import Vector

OUT = r"D:\BrokenWorld\src\main\resources\assets\brokenworld\textures\gui\screamer.png"
HEAD_CENTRE = Vector((0.0, -0.47, 2.57))


def collection(name):
    col = bpy.data.collections.get(name)
    if col is None:
        col = bpy.data.collections.new(name)
        bpy.context.scene.collection.children.link(col)
    return col


def obj(name, data, col):
    ob = bpy.data.objects.get(name)
    if ob is None:
        ob = bpy.data.objects.new(name, data)
        col.objects.link(ob)
    return ob


def look_at(ob, target):
    direction = Vector(target) - ob.location
    ob.rotation_euler = direction.to_track_quat('-Z', 'Y').to_euler()


def light(name, kind, location, energy, color, col, size=0.05):
    data = bpy.data.lights.get(name) or bpy.data.lights.new(name, kind)
    data.energy = energy
    data.color = color
    if hasattr(data, "shadow_soft_size"):
        data.shadow_soft_size = size
    ob = obj(name, data, col)
    ob.location = location
    look_at(ob, HEAD_CENTRE)
    return ob


def principled(mat):
    mat.use_nodes = True
    nodes = mat.node_tree.nodes
    return next(n for n in nodes if n.type == "BSDF_PRINCIPLED"), nodes, mat.node_tree.links


def setup_materials():
    skin = bpy.data.materials["SilhouetteBlack"]
    bsdf, nodes, links = principled(skin)
    attr = next((n for n in nodes if n.type == "ATTRIBUTE"), None) or nodes.new("ShaderNodeAttribute")
    attr.attribute_type = 'GEOMETRY'
    attr.attribute_name = "hole"
    ramp = next((n for n in nodes if n.type == "VALTORGB"), None) or nodes.new("ShaderNodeValToRGB")
    ramp.color_ramp.elements[0].color = (0.022, 0.019, 0.022, 1)
    ramp.color_ramp.elements[1].color = (0.0, 0.0, 0.0, 1)
    links.new(attr.outputs["Fac"], ramp.inputs["Fac"])
    links.new(ramp.outputs["Color"], bsdf.inputs["Base Color"])
    bsdf.inputs["Roughness"].default_value = 0.62

    eyes = bpy.data.materials["SilhouetteEyes"]
    bsdf, nodes, links = principled(eyes)
    bsdf.inputs["Base Color"].default_value = (1, 1, 1, 1)
    bsdf.inputs["Emission Color"].default_value = (1, 1, 1, 1)
    bsdf.inputs["Emission Strength"].default_value = 25.0

    teeth = bpy.data.materials["SilhouetteTeeth"]
    bsdf, nodes, links = principled(teeth)
    bsdf.inputs["Base Color"].default_value = (0.78, 0.72, 0.58, 1)
    bsdf.inputs["Roughness"].default_value = 0.35


def render():
    scene = bpy.context.scene
    col = collection("Screamer")

    cam_data = bpy.data.cameras.get("ScreamerCam") or bpy.data.cameras.new("ScreamerCam")
    cam_data.lens = 30
    cam = obj("ScreamerCam", cam_data, col)
    cam.location = (0.03, -1.02, 2.43)       # close, a little below: it looms over you
    look_at(cam, HEAD_CENTRE + Vector((0, 0, 0.02)))

    light("ScreamerUnder", 'SPOT', (0.12, -0.85, 2.05), 35.0, (1.0, 0.85, 0.75), col)     # torch from below
    light("ScreamerRim", 'POINT', (-0.25, -0.1, 2.85), 40.0, (0.6, 0.7, 1.0), col, 0.02)  # cold rim light
    light("ScreamerFill", 'POINT', (-0.35, -0.9, 2.6), 4.0, (0.9, 0.3, 0.3), col)          # faint red

    setup_materials()

    world = scene.world or bpy.data.worlds.new("World")
    scene.world = world
    world.use_nodes = True
    bg = next(n for n in world.node_tree.nodes if n.type == "BACKGROUND")
    bg.inputs["Color"].default_value = (0, 0, 0, 1)
    bg.inputs["Strength"].default_value = 0.0

    default_light = bpy.data.objects.get("Light")
    hidden_before = default_light.hide_render if default_light else None
    if default_light:
        default_light.hide_render = True

    for engine in ("BLENDER_EEVEE_NEXT", "BLENDER_EEVEE", scene.render.engine):
        try:
            scene.render.engine = engine
            break
        except TypeError:
            continue
    scene.camera = cam
    scene.render.resolution_x = 1280
    scene.render.resolution_y = 720
    scene.render.resolution_percentage = 100
    scene.render.film_transparent = False
    scene.render.image_settings.file_format = 'PNG'
    scene.render.image_settings.color_mode = 'RGB'
    scene.render.filepath = OUT
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    bpy.ops.render.render(write_still=True)

    if default_light is not None:
        default_light.hide_render = hidden_before
    return scene.render.engine


print("rendered with", render(), "->", OUT)
