"""Puts the title on the cover renders (tools/blender_cover.py): python tools/cover_text.py

The title "BROKEN WORLD" in Rubik Glitch (cover/fonts, SIL OFL), with a red/cyan split. (block_title() builds it
from Minecraft blocks instead, like the game's logo - the first version of the cover.)
Writes cover/cover.png (1920x1080), cover/icon.png (512x512) and the mod's own icon
(src/main/resources/assets/brokenworld/icon.png, shown in the mod list)."""
import os
import random

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = r"D:\BrokenWorld"
TEX = os.path.join(ROOT, "blender", "cover_textures")
OUT = os.path.join(ROOT, "cover")

FONT = {  # 5x7 pixel letters
    "B": ["11110", "10001", "10001", "11110", "10001", "10001", "11110"],
    "R": ["11110", "10001", "10001", "11110", "10100", "10010", "10001"],
    "O": ["01110", "10001", "10001", "10001", "10001", "10001", "01110"],
    "K": ["10001", "10010", "10100", "11000", "10100", "10010", "10001"],
    "E": ["11111", "10000", "10000", "11110", "10000", "10000", "11111"],
    "N": ["10001", "11001", "10101", "10011", "10001", "10001", "10001"],
    "W": ["10001", "10001", "10001", "10101", "10101", "11011", "10001"],
    "L": ["10000", "10000", "10000", "10000", "10000", "10000", "11111"],
    "D": ["11110", "10001", "10001", "10001", "10001", "10001", "11110"],
    " ": ["000"] * 7,
}
WRONG = ["gold_block", "diamond_ore", "netherrack", "redstone_ore", "oak_log", "sand", "bricks"]


def texture(name, size):
    return Image.open(os.path.join(TEX, name + ".png")).convert("RGBA").resize((size, size), Image.NEAREST)


def block_title(text, cell, rnd):
    """The title as an RGBA image: stone blocks with a darker side under them, some blocks wrong."""
    cols = sum(len(FONT[c][0]) + 1 for c in text) - 1
    depth = max(3, cell // 5)
    img = Image.new("RGBA", (cols * cell + depth, 7 * cell + depth), (0, 0, 0, 0))
    stone = texture("stone", cell)
    cells = []
    x0 = 0
    for c in text:
        glyph = FONT[c]
        for row, line in enumerate(glyph):
            for col, bit in enumerate(line):
                if bit == "1":
                    cells.append((x0 + col, row))
        x0 += len(glyph[0]) + 1
    # the side of the letters: the same blocks, darker, a little down and to the right
    for cx, cy in cells:
        side = stone.point(lambda v: int(v * 0.35))
        img.alpha_composite(side, (cx * cell + depth, cy * cell + depth))
    for cx, cy in cells:
        tex = texture(rnd.choice(WRONG), cell) if rnd.random() < 0.11 else stone
        img.alpha_composite(tex, (cx * cell, cy * cell))
    # a thin dark outline so it reads on any background
    alpha = img.getchannel("A")
    outline = Image.new("RGBA", img.size, (5, 5, 10, 255))
    outline.putalpha(alpha.filter(ImageFilter.MaxFilter(5)))
    framed = Image.new("RGBA", img.size, (0, 0, 0, 0))
    framed.alpha_composite(outline)
    framed.alpha_composite(img)
    return framed


TITLE_FONT = os.path.join(OUT, "fonts", "RubikGlitch-Regular.ttf")  # Rubik Glitch, SIL OFL (cover/fonts/OFL.txt)


def font_title(text, size):
    """The title in Rubik Glitch - letters already cut and shifted - white, with a dark glow behind."""
    font = ImageFont.truetype(TITLE_FONT, size)
    d = ImageDraw.Draw(Image.new("RGBA", (1, 1)))
    left, top, right, bottom = d.textbbox((0, 0), text, font=font)
    pad = size // 3
    img = Image.new("RGBA", (right - left + pad * 2, bottom - top + pad * 2), (0, 0, 0, 0))
    glow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(glow).text((pad - left, pad - top), text, font=font, fill=(0, 0, 0, 230))
    glow = glow.filter(ImageFilter.GaussianBlur(size // 10))
    img.alpha_composite(glow)
    ImageDraw.Draw(img).text((pad - left, pad - top), text, font=font, fill=(236, 238, 245, 255))
    return img


def glitch(img, rnd, slices=2, split=4):
    """Horizontal slices pushed sideways and a red/cyan split - the picture is breaking too."""
    w, h = img.size
    out = img.copy()
    for _ in range(slices):
        y = rnd.randint(0, h - 8)
        hh = rnd.randint(4, max(5, h // 14))
        dx = rnd.choice((-1, 1)) * rnd.randint(8, 16)
        band = img.crop((0, y, w, y + hh))
        out.paste((0, 0, 0, 0), (0, y, w, y + hh))
        out.alpha_composite(band, (dx, y)) if dx >= 0 else out.alpha_composite(band.crop((-dx, 0, w, hh)), (0, y))
    r, g, b, a = out.split()
    red = Image.merge("RGBA", (r, Image.new("L", out.size, 0), Image.new("L", out.size, 0), a.point(lambda v: v // 3)))
    cyan = Image.merge("RGBA", (Image.new("L", out.size, 0), g, b, a.point(lambda v: v // 3)))
    final = Image.new("RGBA", (w + split * 2, h), (0, 0, 0, 0))
    final.alpha_composite(red, (0, 0))
    final.alpha_composite(cyan, (split * 2, 0))
    final.alpha_composite(out, (split, 0))
    return final


def vignette(img, strength=0.65):
    w, h = img.size
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).ellipse((-w * 0.15, -h * 0.25, w * 1.15, h * 1.25), fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(min(w, h) // 6))
    dark = Image.new("RGB", (w, h), (0, 0, 0))
    return Image.composite(img, Image.blend(img, dark, strength), mask)


def bottom_shade(img, start=0.55, alpha=235):
    w, h = img.size
    shade = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(shade)
    y0 = int(h * start)
    for y in range(y0, h):
        d.line((0, y, w, y), fill=int(alpha * ((y - y0) / (h - y0)) ** 1.3))
    black = Image.new("RGB", (w, h), (2, 3, 8))
    return Image.composite(black, img, shade)


def subtitle_font(size):
    path = r"C:\Windows\Fonts\bahnschrift.ttf"
    font = ImageFont.truetype(path, size)
    try:
        font.set_variation_by_name("SemiBold")
    except Exception:
        pass
    return font


def spaced(draw, xy, text, font, fill, spacing):
    x, y = xy
    for ch in text:
        draw.text((x, y), ch, font=font, fill=fill)
        x += draw.textlength(ch, font=font) + spacing


def text_width(draw, text, font, spacing):
    return sum(draw.textlength(ch, font=font) + spacing for ch in text) - spacing


def make_cover():
    rnd = random.Random(7)
    img = Image.open(os.path.join(OUT, "cover_raw.png")).convert("RGB")
    img = bottom_shade(vignette(img, 0.55))
    canvas = img.convert("RGBA")
    title = glitch(font_title("BROKEN WORLD", 150), rnd, slices=1, split=5)
    x = (canvas.width - title.width) // 2
    y = canvas.height - title.height - 95
    canvas.alpha_composite(title, (x, y))
    d = ImageDraw.Draw(canvas)
    font = subtitle_font(30)
    sub = "SOMETHING IS WATCHING YOU"
    w = text_width(d, sub, font, 9)
    spaced(d, ((canvas.width - w) / 2, y + title.height - 18), sub, font, (170, 180, 205, 255), 9)
    small = subtitle_font(22)
    tag = "Fabric  ·  Forge  1.20.1"
    w = text_width(d, tag, small, 4)
    spaced(d, ((canvas.width - w) / 2, y + title.height + 30), tag, small, (110, 118, 140, 255), 4)
    canvas.convert("RGB").save(os.path.join(OUT, "cover.png"))


def make_icon():
    img = Image.open(os.path.join(OUT, "icon_raw.png")).convert("RGB")
    # closer on its face: the icon is shown small in the mod list
    img = img.crop((130, 80, 470, 420)).resize((512, 512), Image.LANCZOS)
    img = vignette(img, 0.6)
    # mod sites want icons under 100 KB: 256 colours (dithered) are plenty for a dark picture like this
    small_png(img, os.path.join(OUT, "icon.png"))
    mod_icon = os.path.join(ROOT, "src", "main", "resources", "assets", "brokenworld", "icon.png")
    small_png(img.resize((256, 256), Image.LANCZOS), mod_icon)


def small_png(img, path, limit=100 * 1024):
    # MAXCOVERAGE keeps the few pure-white eye pixels in the palette (MEDIANCUT drops them: the eyes go dull)
    for colors in (256, 192, 128, 96, 64):
        img.quantize(colors=colors, method=Image.Quantize.MAXCOVERAGE, dither=Image.Dither.FLOYDSTEINBERG).save(
            path, optimize=True)
        if os.path.getsize(path) < limit:
            return


if __name__ == "__main__":
    make_cover()
    make_icon()
    print("ok")
