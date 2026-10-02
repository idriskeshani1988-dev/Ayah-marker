"""Build all Android launcher icons from icon-512.png (run from the repo root, after `cap add android`)."""
import os, sys
from PIL import Image, ImageDraw

SRC = 'icon-512.png'
RES = 'android/app/src/main/res'

if not os.path.exists(SRC):
    print('!! icon-512.png not found in repo root, keeping default icon')
    sys.exit(0)
if not os.path.isdir(RES):
    print('!! res folder not found:', RES)
    sys.exit(1)

src = Image.open(SRC).convert('RGBA')
print('source icon:', src.size)

legacy = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}
foreground = {'mdpi': 108, 'hdpi': 162, 'xhdpi': 216, 'xxhdpi': 324, 'xxxhdpi': 432}

def on_white(img, size):
    plate = Image.new('RGBA', (size, size), (255, 255, 255, 255))
    plate.alpha_composite(img.resize((size, size), Image.LANCZOS))
    return plate

for dens in legacy:
    d = os.path.join(RES, 'mipmap-' + dens)
    os.makedirs(d, exist_ok=True)

    # legacy square icon
    sq = legacy[dens]
    on_white(src, sq).save(os.path.join(d, 'ic_launcher.png'))

    # legacy round icon
    rnd = on_white(src, sq)
    mask = Image.new('L', (sq * 4, sq * 4), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, sq * 4 - 1, sq * 4 - 1], fill=255)
    mask = mask.resize((sq, sq), Image.LANCZOS)
    out = Image.new('RGBA', (sq, sq), (0, 0, 0, 0))
    out.paste(rnd, (0, 0), mask)
    out.save(os.path.join(d, 'ic_launcher_round.png'))

    # adaptive foreground: artwork inside the 66% safe zone, transparent around it
    fg = foreground[dens]
    art = int(fg * 0.66)
    layer = Image.new('RGBA', (fg, fg), (0, 0, 0, 0))
    layer.alpha_composite(src.resize((art, art), Image.LANCZOS), ((fg - art) // 2, (fg - art) // 2))
    layer.save(os.path.join(d, 'ic_launcher_foreground.png'))
    print('wrote mipmap-%s: icon %dpx, foreground %dpx' % (dens, sq, fg))

# adaptive icon definitions (white background + our foreground)
any_dir = os.path.join(RES, 'mipmap-anydpi-v26')
os.makedirs(any_dir, exist_ok=True)
xml = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@android:color/white"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>
'''
for name in ('ic_launcher.xml', 'ic_launcher_round.xml'):
    with open(os.path.join(any_dir, name), 'w') as f:
        f.write(xml)
print('adaptive icon xml written')
