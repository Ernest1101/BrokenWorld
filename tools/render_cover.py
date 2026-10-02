"""Renders the cover without the Blender window:
blender -b blender/silhouette.blend --python tools/render_cover.py   (the .blend is not saved)"""
T = r"D:\BrokenWorld\tools"


def load(name, cut=None):
    src = open(T + "\\" + name, encoding="utf-8").read()
    if cut:
        src = src.split(cut)[0]
    ns = {}
    exec(compile(src, name, "exec"), ns)
    return ns


poses = load("blender_poses.py", "\nobs = objects()")
screamer = load("blender_screamer.py", "\n\ndef render():")
cover = load("blender_cover.py")
print("cover rendered:", cover["run"](poses, screamer["setup_materials"]))
