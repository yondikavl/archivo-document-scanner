"""Regenerate launcher assets from drawable/logo_archivo.png."""
from __future__ import annotations

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
LOGO = RES / "drawable" / "logo_archivo.png"

# Legacy density sizes for ic_launcher / ic_launcher_round
MIPMAP_SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

# Adaptive icons crop the outer ~18%. Keep the full logo inside ~72% so it
# doesn't feel zoomed after the launcher mask is applied.
FOREGROUND_FILL = 0.60


def is_green(r: int, g: int, b: int) -> bool:
    return g > r + 15 and g > b + 15 and g > 60


def extract_mark(logo: Image.Image) -> Image.Image:
    """Keep the white/gray mark; make green background transparent."""
    src = logo.convert("RGBA")
    pixels = src.load()
    w, h = src.size
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    out_px = out.load()
    for y in range(h):
        for x in range(w):
            r, g, b, a = pixels[x, y]
            if a < 20 or is_green(r, g, b):
                continue
            out_px[x, y] = (r, g, b, a)
    return out


def padded_logo(logo: Image.Image, canvas: int, fill: float) -> Image.Image:
    """Center the full logo with transparent padding.

    Adaptive background (radial gradient) shows in the inset ring, so the
    mark stays smaller after the launcher mask crops the outer edges.
    """
    plate = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    inner = max(1, int(canvas * fill))
    scaled = logo.resize((inner, inner), Image.Resampling.LANCZOS)
    plate.paste(scaled, ((canvas - inner) // 2, (canvas - inner) // 2), scaled)
    return plate


def legacy_icon(logo: Image.Image, canvas: int, fill: float) -> Image.Image:
    """Legacy mipmaps need an opaque plate; use a soft upscaled logo behind."""
    plate = logo.resize((canvas, canvas), Image.Resampling.LANCZOS)
    inner = max(1, int(canvas * fill))
    scaled = logo.resize((inner, inner), Image.Resampling.LANCZOS)
    # Slight blur on the full-bleed plate softens any edge mismatch.
    from PIL import ImageFilter

    plate = plate.filter(ImageFilter.GaussianBlur(radius=max(1, canvas // 24)))
    plate.paste(scaled, ((canvas - inner) // 2, (canvas - inner) // 2), scaled)
    return plate


def make_monochrome(mark: Image.Image, canvas: int = 1024, safe_ratio: float = 0.42) -> Image.Image:
    """White silhouette centered for themed / monochrome icons."""
    bbox = mark.getbbox()
    if bbox is None:
        return Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    cropped = mark.crop(bbox)
    safe = int(canvas * safe_ratio)
    cw, ch = cropped.size
    scale = min(safe / cw, safe / ch)
    nw, nh = max(1, int(cw * scale)), max(1, int(ch * scale))
    resized = cropped.resize((nw, nh), Image.Resampling.LANCZOS)
    pixels = resized.load()
    mono = Image.new("RGBA", (nw, nh), (0, 0, 0, 0))
    mono_px = mono.load()
    for y in range(nh):
        for x in range(nw):
            r, g, b, a = pixels[x, y]
            if a < 20:
                continue
            lum = (r + g + b) / 3.0
            alpha = int(min(255, a * (lum / 255.0) if lum < 250 else a))
            mono_px[x, y] = (255, 255, 255, alpha)
    canvas_img = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    canvas_img.paste(mono, ((canvas - nw) // 2, (canvas - nh) // 2), mono)
    return canvas_img


def main() -> None:
    logo = Image.open(LOGO).convert("RGBA")
    print(f"logo size={logo.size}")

    # Full logo with inset so adaptive masks don't crop/zoom the mark.
    foreground = padded_logo(logo, canvas=1024, fill=FOREGROUND_FILL)
    foreground_path = RES / "drawable" / "ic_launcher_foreground.png"
    foreground.save(foreground_path, optimize=True)
    print(f"wrote {foreground_path} fill={FOREGROUND_FILL}")

    mark = extract_mark(logo)
    print(f"mark bbox={mark.getbbox()}")
    mono = make_monochrome(mark)
    mono_path = RES / "drawable" / "ic_launcher_monochrome.png"
    mono.save(mono_path, optimize=True)
    print(f"wrote {mono_path}")

    for folder, size in MIPMAP_SIZES.items():
        out_dir = RES / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        full = legacy_icon(logo, canvas=size, fill=FOREGROUND_FILL)
        full.save(out_dir / "ic_launcher.png", optimize=True)
        full.save(out_dir / "ic_launcher_round.png", optimize=True)
        print(f"wrote {folder} {size}px")


if __name__ == "__main__":
    main()
