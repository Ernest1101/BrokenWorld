"""Background Blender: renders the silhouette's outline (standing, from the front) for the broken main menu, where it
stands in the panorama. Run: blender -b blender/silhouette.blend --python tools/render_menu_silhouette.py
Writes src/main/resources/assets/brokenworld/textures/gui/menu_silhouette.png (black on transparent)."""
import os

import bpy
from mathutils import Vector

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "src", "main", "resources", "assets", "brokenworld", "textures", "gui", "menu_silhouette.png")

scene = bpy.data.scenes.new("MenuSilhouette")
col = bpy.data.collections["Silhouette"]
scene.collection.children.link(col)
cam_data = bpy.data.cameras.new("MenuCam")
cam_data.type = 'ORTHO'
cam_data.ortho_scale = 3.0
cam = bpy.data.objects.new("MenuCam", cam_data)
scene.collection.objects.link(cam)
cam.location = Vector((0.0, -12.0, 1.35))
cam.rotation_euler = (Vector((0.0, 0.0, 1.35)) - cam.location).to_track_quat('-Z', 'Y').to_euler()
scene.camera = cam

r = scene.render
try:
    r.engine = 'BLENDER_WORKBENCH'
except TypeError as e:
    print(e)
r.film_transparent = True
r.resolution_x = 128
r.resolution_y = 256
r.resolution_percentage = 100
r.image_settings.file_format = 'PNG'
r.image_settings.color_mode = 'RGBA'
scene.display.shading.light = 'FLAT'
scene.display.shading.color_type = 'SINGLE'
scene.display.shading.single_color = (0.0, 0.0, 0.0)
r.filepath = OUT
bpy.ops.render.render(write_still=True, scene=scene.name)
print("wrote", OUT)
