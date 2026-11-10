from pathlib import Path

from PIL import Image

root = Path("app/src/main/res")
logo = Image.open(root / "drawable" / "logo_archivo.png").convert("RGBA")
green = (15, 157, 88, 255)


def make_mono(src: Image.Image) -> Image.Image:
    mono = Image.new("RGBA", src.size, (0, 0, 0, 0))
    px = src.load()
    out = mono.load()
    for y in range(src.height):
        for x in range(src.width):
            r, g, b, a = px[x, y]
            if a < 16:
                continue
            if abs(r - 15) < 30 and abs(g - 157) < 40 and abs(b - 88) < 35:
                continue
            out[x, y] = (0, 0, 0, 255)
    return mono


def padded(src: Image.Image, size: int, scale: float, fill=(0, 0, 0, 0)) -> Image.Image:
    """Keep 1:1 canvas; place logo centered smaller to avoid adaptive zoom crop."""
    canvas = Image.new("RGBA", (size, size), fill)
    side = max(1, int(round(size * scale)))
    icon = src.resize((side, side), Image.Resampling.LANCZOS)
    x = (size - side) // 2
    y = (size - side) // 2
    canvas.paste(icon, (x, y), icon)
    return canvas


mono_src = make_mono(logo)

# Adaptive foreground / monochrome: mid scale (not over-zoomed / not too small)
padded(logo, 512, 0.66, green).save(root / "drawable" / "ic_launcher_foreground.png", optimize=True)
padded(mono_src, 512, 0.66).save(root / "drawable" / "ic_launcher_monochrome.png", optimize=True)

legacy = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}
fg = {
    "mipmap-mdpi": 108,
    "mipmap-hdpi": 162,
    "mipmap-xhdpi": 216,
    "mipmap-xxhdpi": 324,
    "mipmap-xxxhdpi": 432,
}

for folder, size in legacy.items():
    d = root / folder
    d.mkdir(exist_ok=True)
    # Full icon for pre-API 26: keep logo readable, still 1:1
    icon = padded(logo, size, 0.82, green)
    icon.save(d / "ic_launcher.png", optimize=True)
    icon.save(d / "ic_launcher_round.png", optimize=True)
    Image.new("RGBA", (size, size), green).save(d / "ic_launcher_background.png", optimize=True)

for folder, size in fg.items():
    d = root / folder
    padded(logo, size, 0.66, green).save(d / "ic_launcher_foreground.png", optimize=True)
    padded(mono_src, size, 0.66).save(d / "ic_launcher_monochrome.png", optimize=True)
    Image.new("RGBA", (size, size), green).save(d / "ic_launcher_background.png", optimize=True)

print("generated launcher icons from", logo.size, "1:1 padded")
