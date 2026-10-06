"""Render all VetrOFF launcher/splash assets from the transparent centered fan rotor.

Run from the repository root with Python and Pillow installed.
"""
import re
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "android/app/src/main/res"
LOGO = ROOT / "assets/images/source/vetroff-rotor.png"
BACKGROUND = "#020506"


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
    canvas = Image.new("RGBA", (size * 4, size * 4))
    logo = mark(int(size * 4 * .82))
    offset = (canvas.width - logo.width) // 2
    canvas.alpha_composite(logo, (offset, offset))
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
    save(mark(256, "#FFFFFF"), "android/app/src/main/res/drawable-nodpi/vetroff_notification.png")
    (RES / "drawable/ic_launcher_foreground.xml").write_text(drawable())
    (RES / "drawable/android12splash.xml").write_text(drawable())
    (RES / "drawable/ic_stat_logo.xml").write_text(drawable(24, 24, "vetroff_notification"))
    for density in ["mdpi", "hdpi"]:
        (RES / f"drawable-{density}/ic_stat_logo.png").unlink(missing_ok=True)
    (RES / "drawable/ic_launcher_background.xml").write_text(
        '<shape xmlns:android="http://schemas.android.com/apk/res/android"><solid android:color="#020506"/></shape>\n'
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
    banner.alpha_composite(mark(72 * 4), (124 * 4, 26 * 4))
    font = ImageFont.truetype(str(ROOT / "assets/fonts/Manrope.ttf"), 25 * 4)
    ImageDraw.Draw(banner).text((160 * 4, 122 * 4), "VetrOFF Client", fill="#FFFFFF", font=font, anchor="mm")
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
