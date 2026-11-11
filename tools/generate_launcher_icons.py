"""Generate launcher mipmaps/drawables from logo_archivo.png.

Adaptive foreground is inset so launcher masks do not over-zoom the mark.
"""

from __future__ import annotations

import re
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "res"
SRC_PATH = ROOT / "drawable" / "logo_archivo.png"

# Adaptive safe zone is ~66%; keep a bit larger so the logo still reads clearly.
ADAPTIVE_SCALE = 0.70

LEGACY = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}
ADAPTIVE = {
    "mipmap-mdpi": 108,
    "mipmap-hdpi": 162,
    "mipmap-xhdpi": 216,
    "mipmap-xxhdpi": 324,
    "mipmap-xxxhdpi": 432,
}


def resize_exact(im: Image.Image, size: int) -> Image.Image:
    return im.resize((size, size), Image.Resampling.LANCZOS)


def make_round(im: Image.Image) -> Image.Image:
    out = im.copy()
    mask = Image.new("L", out.size, 0)
    ImageDraw.Draw(mask).ellipse((0, 0, out.size[0] - 1, out.size[1] - 1), fill=255)
    out.putalpha(mask)
    return out


def solid_bg(size: int, rgba: tuple[int, int, int, int]) -> Image.Image:
    return Image.new("RGBA", (size, size), rgba)


def place_inset(
    im: Image.Image,
    size: int,
    scale: float,
    fill: tuple[int, int, int, int],
) -> Image.Image:
    """Center original composition at `scale` of the canvas — no crop/zoom-in."""
    canvas = solid_bg(size, fill)
    inner = max(1, int(round(size * scale)))
    resized = resize_exact(im, inner)
    offset = (size - inner) // 2
    canvas.paste(resized, (offset, offset), resized)
    return canvas


def make_monochrome(im: Image.Image) -> Image.Image:
    px = im.load()
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    opx = out.load()
    w, h = im.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a < 16:
                continue
            lum = 0.299 * r + 0.587 * g + 0.114 * b
            is_greenish = g > r + 15 and g > b + 15 and lum < 230
            if is_greenish:
                continue
            if lum >= 170:
                opx[x, y] = (0, 0, 0, 255)
    return out


def sample_bg(im: Image.Image) -> tuple[int, int, int, int]:
    pts = [
        (20, 20),
        (im.width - 21, 20),
        (20, im.height - 21),
        (im.width - 21, im.height - 21),
        (im.width // 2, 20),
    ]
    samples = [im.getpixel(p)[:3] for p in pts]
    avg = tuple(sum(c[i] for c in samples) // len(samples) for i in range(3))
    return (avg[0], avg[1], avg[2], 255)


def main() -> None:
    src = Image.open(SRC_PATH).convert("RGBA")
    bg = sample_bg(src)
    mono = make_monochrome(src)
    opaque = sum(1 for p in mono.getdata() if p[3] > 0)
    print(
        f"source={src.size} bg=#{bg[0]:02X}{bg[1]:02X}{bg[2]:02X} "
        f"mono_px={opaque} adaptive_scale={ADAPTIVE_SCALE}"
    )

    for folder, size in LEGACY.items():
        d = ROOT / folder
        d.mkdir(parents=True, exist_ok=True)
        # Slight inset so round/squircle launcher masks keep original padding.
        icon = place_inset(src, size, scale=0.86, fill=bg)
        icon.save(d / "ic_launcher.png", optimize=True)
        make_round(icon).save(d / "ic_launcher_round.png", optimize=True)

    for folder, size in ADAPTIVE.items():
        d = ROOT / folder
        d.mkdir(parents=True, exist_ok=True)
        place_inset(src, size, ADAPTIVE_SCALE, bg).save(
            d / "ic_launcher_foreground.png", optimize=True
        )
        solid_bg(size, bg).save(d / "ic_launcher_background.png", optimize=True)
        place_inset(mono, size, ADAPTIVE_SCALE, (0, 0, 0, 0)).save(
            d / "ic_launcher_monochrome.png", optimize=True
        )

    drawable = ROOT / "drawable"
    master = 1080
    place_inset(src, master, ADAPTIVE_SCALE, bg).save(
        drawable / "ic_launcher_foreground.png", optimize=True
    )
    place_inset(mono, master, ADAPTIVE_SCALE, (0, 0, 0, 0)).save(
        drawable / "ic_launcher_monochrome.png", optimize=True
    )

    colors = ROOT / "values" / "colors.xml"
    text = colors.read_text(encoding="utf-8")
    hex_bg = f"#FF{bg[0]:02X}{bg[1]:02X}{bg[2]:02X}"
    text2, n = re.subn(
        r'(<color name="ic_launcher_background">)#FF[0-9A-Fa-f]{6}(</color>)',
        rf"\g<1>{hex_bg}\2",
        text,
        count=1,
    )
    if n:
        colors.write_text(text2, encoding="utf-8")
        print(f"updated ic_launcher_background -> {hex_bg}")

    print("done")


if __name__ == "__main__":
    main()
