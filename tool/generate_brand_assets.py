"""Package VetrOFF launcher/splash assets from the restored transparent logo.

Run from the repository root with Python and Pillow installed.
"""
import re
import math
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "android/app/src/main/res"
LOGO = ROOT / "assets/images/source/vetroff-logo.png"
ICON = ROOT / "assets/images/source/vetroff-launcher-mark.png"
BACKGROUND = "#020506"


def icon_mark(size, color=None):
    image = Image.open(ICON).convert("RGBA")
    if color is not None:
        alpha = image.getchannel("A")
        image = Image.new("RGBA", image.size, color)
        image.putalpha(alpha)
    # Center the visible emblem, rather than the source canvas. Short breeze
    # accents stay inside the circular footprint of the dedicated launcher mark.
    bounds = image.getchannel("A").point(lambda value: 255 if value > 8 else 0).getbbox()
    if bounds is None:
        raise ValueError("Launcher mark is empty")
    image = image.crop(bounds)
    scale = size * .88 / max(image.size)
    image = image.resize((round(image.width * scale), round(image.height * scale)), Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (size, size))
    canvas.alpha_composite(image, ((size - image.width) // 2, (size - image.height) // 2))
    return canvas


def icon_background(size):
    # A smooth iridescent blue/cyan/violet surface, shared by legacy/adaptive icons.
    edge = 256
    image = Image.new("RGB", (edge, edge))
    pixels = image.load()
    stops = [(13, 126, 153), (44, 73, 169), (115, 57, 180)]
    for y in range(edge):
        for x in range(edge):
            u, v = x / (edge - 1), y / (edge - 1)
            position = (u + v) / 2
            a, b = (stops[0], stops[1]) if position < .5 else (stops[1], stops[2])
            t = (position % .5) * 2 if position < 1 else 1
            rgb = [a[k] * (1 - t) + b[k] * t for k in range(3)]
            # Gentle pearl-like highlights, not a black backing or harsh stripes.
            cyan = .32 * math.exp(-((u - .18) ** 2 + (v - .22) ** 2) / .12)
            violet = .22 * math.exp(-((u - .83) ** 2 + (v - .78) ** 2) / .15)
            rgb = [rgb[k] * (1 - cyan) + (69, 224, 236)[k] * cyan for k in range(3)]
            rgb = [rgb[k] * (1 - violet) + (216, 153, 247)[k] * violet for k in range(3)]
            pixels[x, y] = tuple(round(c) for c in rgb)
    return image.resize((size, size), Image.Resampling.LANCZOS).convert("RGBA")


def mark(size, color=None):
    image = Image.open(LOGO).convert("RGBA")
    if color is not None:
        alpha = image.getchannel("A")
        image = Image.new("RGBA", image.size, color)
        image.putalpha(alpha)
    return image.resize((size, size), Image.Resampling.LANCZOS)


def save(image, path):
    path = ROOT / path
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, lossless=True) if path.suffix == ".webp" else image.save(path)


def launcher(size, rounded=False):
    canvas = icon_background(size * 4)
    logo = icon_mark(int(size * 4 * .84))
    offset = (canvas.width - logo.width) // 2
    canvas.alpha_composite(logo, (offset, offset))
    if rounded:
        mask = Image.new("L", canvas.size)
        ImageDraw.Draw(mask).ellipse((0, 0, canvas.width - 1, canvas.height - 1), fill=255)
        canvas.putalpha(mask)
    return canvas.resize((size, size), Image.Resampling.LANCZOS)


def drawable(size=108, mark_size=64, source="vetroff_fan"):
    return f'''<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:width="{size}dp" android:height="{size}dp" android:gravity="center">
      <shape><solid android:color="@android:color/transparent"/></shape>
    </item>
    <item android:width="{mark_size}dp" android:height="{mark_size}dp" android:gravity="center">
      <bitmap android:src="@drawable/{source}" android:gravity="fill"/>
    </item>
</layer-list>
'''


def generate():
    for density, size in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
        for rounded in [False, True]:
            suffix = "_round" if rounded else ""
            save(launcher(size, rounded), f"android/app/src/main/res/mipmap-{density}/ic_launcher{suffix}.webp")
    save(launcher(512), "android/app/src/main/ic_launcher-playstore.png")
    save(mark(512), "assets/images/logo.png")
    save(mark(512), "android/app/src/main/res/drawable-nodpi/vetroff_fan.png")
    save(icon_mark(512), "android/app/src/main/res/drawable-nodpi/vetroff_launcher.png")
    save(icon_background(512), "android/app/src/main/res/drawable-nodpi/vetroff_launcher_background.png")
    save(icon_mark(512, "#FFFFFF"), "android/app/src/main/res/drawable-nodpi/vetroff_monochrome.png")
    (RES / "drawable/ic_launcher_monochrome.xml").write_text(drawable(mark_size=72, source="vetroff_monochrome"))
    for name in ["ic_launcher", "ic_launcher_round"]:
        folder = RES / "mipmap-anydpi-v33"
        folder.mkdir(parents=True, exist_ok=True)
        (folder / f"{name}.xml").write_text('''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
    <monochrome android:drawable="@drawable/ic_launcher_monochrome"/>
</adaptive-icon>
''')
    save(mark(256, "#FFFFFF"), "android/app/src/main/res/drawable-nodpi/vetroff_notification.png")
    (RES / "drawable/ic_launcher_foreground.xml").write_text(drawable(mark_size=72, source="vetroff_launcher"))
    for name in ["ic_launcher", "ic_launcher_round"]:
        (RES / "mipmap-anydpi-v26" / f"{name}.xml").write_text('''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
''')
    (RES / "drawable/android12splash.xml").write_text(drawable())
    (RES / "drawable/ic_stat_logo.xml").write_text(drawable(24, 24, "vetroff_notification"))
    for density in ["mdpi", "hdpi"]:
        (RES / f"drawable-{density}/ic_stat_logo.png").unlink(missing_ok=True)
    (RES / "drawable/ic_launcher_background.xml").write_text(
        '<bitmap xmlns:android="http://schemas.android.com/apk/res/android" android:src="@drawable/vetroff_launcher_background" android:gravity="fill"/>\n'
    )
    for resource in ["ic_launcher_background", "ic_banner_background"]:
        (RES / f"values/{resource}.xml").write_text(f'<resources><color name="{resource}">{BACKGROUND}</color></resources>\n')
    for folder in ["drawable", "drawable-v21"]:
        save(Image.new("RGB", (1, 1), BACKGROUND), f"android/app/src/main/res/{folder}/background.png")
    save(mark(324), "android/app/src/main/res/drawable-xxxhdpi/splash.png")
    save(mark(512), "assets/images/source/ic_launcher_splash.png")
    # flutter_native_splash's Android 12 source includes the adaptive safe area.
    foreground = Image.new("RGBA", (108 * 4, 108 * 4))
    foreground.alpha_composite(mark(64 * 4), (22 * 4, 22 * 4))
    save(foreground, "assets/images/source/ic_launcher_foreground.png")
    banner = Image.new("RGBA", (320 * 4, 180 * 4), BACKGROUND)
    banner.alpha_composite(mark(170 * 4), (75 * 4, 5 * 4))
    banner = banner.resize((320, 180), Image.Resampling.LANCZOS)
    save(banner, "android/app/src/main/res/mipmap-xhdpi/ic_banner.png")
    save(banner, "android/app/src/main/res/drawable-nodpi/vetroff_tv_banner.png")
    (RES / "drawable/ic_banner_foreground.xml").write_text(
        '<bitmap xmlns:android="http://schemas.android.com/apk/res/android" android:src="@drawable/vetroff_tv_banner" android:gravity="fill"/>\n'
    )
    (RES / "mipmap-anydpi-v26/ic_banner.xml").write_text(
        '<bitmap xmlns:android="http://schemas.android.com/apk/res/android" android:src="@drawable/vetroff_tv_banner" android:gravity="fill"/>\n'
    )
    for folder in ["values-v31", "values-night-v31"]:
        p = RES / folder / "styles.xml"
        text = re.sub(r'(<item name="android:windowSplashScreenBackground">).*?(</item>)', r'\g<1>#020506\2', p.read_text())
        p.write_text(text)


if __name__ == "__main__":
    generate()
