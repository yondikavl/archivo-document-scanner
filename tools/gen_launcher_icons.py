from pathlib import Path

from PIL import Image

root = Path("app/src/main/res")
logo = Image.open(root / "drawable" / "logo_archivo.png").convert("RGBA")
green = (15, 157, 88, 255)

logo.save(root / "drawable" / "ic_launcher_foreground.png", optimize=True)

mono = Image.new("RGBA", logo.size, (0, 0, 0, 0))
px = logo.load()
out = mono.load()
for y in range(logo.height):
    for x in range(logo.width):
        r, g, b, a = px[x, y]
        if a < 16:
            continue
        if abs(r - 15) < 30 and abs(g - 157) < 40 and abs(b - 88) < 35:
            continue
        out[x, y] = (0, 0, 0, 255)
mono.save(root / "drawable" / "ic_launcher_monochrome.png", optimize=True)

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


def resize(img: Image.Image, size: int) -> Image.Image:
    return img.resize((size, size), Image.Resampling.LANCZOS)


for folder, size in legacy.items():
    d = root / folder
    d.mkdir(exist_ok=True)
    icon = resize(logo, size)
    icon.save(d / "ic_launcher.png", optimize=True)
    icon.save(d / "ic_launcher_round.png", optimize=True)
    Image.new("RGBA", (size, size), green).save(d / "ic_launcher_background.png", optimize=True)

for folder, size in fg.items():
    d = root / folder
    resize(logo, size).save(d / "ic_launcher_foreground.png", optimize=True)
    resize(mono, size).save(d / "ic_launcher_monochrome.png", optimize=True)
    Image.new("RGBA", (size, size), green).save(d / "ic_launcher_background.png", optimize=True)

print("generated launcher icons from", logo.size)
