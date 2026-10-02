"""Generates the silhouette textures (no dependencies). Run: python tools/gen_textures.py
All are 16x16 and used by the Blender mesh (blender/silhouette.blend, see tools/blender_export.py):
- silhouette.png: dark charcoal skin with grain (left) fading to pure black (right) for sockets and the mouth
- silhouette_eyes.png: glowing eye slits
- silhouette_teeth.png: dirty bone for the needle teeth
- item/note.png: the finale's notes"""
import os
import random
import struct
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources",
                    "assets", "brokenworld", "textures")


def write_png(path, w, h, px):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    raw = b"".join(b"\x00" + bytes(v for x in range(w) for v in px[y * w + x]) for y in range(h))

    def chunk(t, d):
        c = struct.pack(">I", len(d)) + t + d
        return c + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)

    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) \
        + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(data)


def silhouette():
    r = random.Random(7)
    px = []
    for y in range(16):
        for x in range(16):
            # charcoal shows the shape up close but reads as black from afar
            v = round(r.randint(10, 24) * (1 - x / 15) ** 1.5)
            px.append((v, max(0, v - 1), v + (2 if v else 0), 255))
    return px


def silhouette_eyes():
    """White, brightest in the middle, fading out at the edges."""
    px = []
    for y in range(16):
        for x in range(16):
            dx, dy = abs(x - 7.5) / 8, abs(y - 7.5) / 8
            a = max(0.0, 1.0 - (dx ** 2 + dy ** 2) ** 0.5)
            v = int(255 * min(1.0, a * 1.6))
            px.append((v, v, v, 255))
    return px


def silhouette_teeth():
    r = random.Random(11)
    px = []
    for y in range(16):
        for x in range(16):
            v = r.randint(-22, 10)
            px.append((178 + v, 168 + v, 140 + v, 255))
    return px


def note():
    """A torn scrap of paper with scribbled lines and a dark smudge."""
    r = random.Random(5)
    px = [(0, 0, 0, 0)] * 256
    for y in range(2, 14):
        for x in range(3, 13):
            if (x in (3, 12) and r.random() < 0.3) or (y in (2, 13) and r.random() < 0.3):
                continue  # ragged edge
            v = r.randint(205, 232)
            px[y * 16 + x] = (v, v - 6, v - 24, 255)
    for y in (4, 6, 8, 10):
        for x in range(4, 4 + r.randint(5, 8)):
            if r.random() < 0.85:
                px[y * 16 + x] = (40, 30, 30, 255)
    for (x, y) in ((9, 11), (10, 11), (10, 12), (9, 10)):
        px[y * 16 + x] = (90, 10, 10, 255)
    return px


write_png(os.path.join(ROOT, "item", "note.png"), 16, 16, note())
e = os.path.join(ROOT, "entity")
write_png(os.path.join(e, "silhouette.png"), 16, 16, silhouette())
write_png(os.path.join(e, "silhouette_eyes.png"), 16, 16, silhouette_eyes())
write_png(os.path.join(e, "silhouette_teeth.png"), 16, 16, silhouette_teeth())
print("ok")
