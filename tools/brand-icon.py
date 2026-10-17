"""Generates the Bottlepost launcher icon as Android VectorDrawables + an SVG preview from one shape list."""
import os
RES = "/home/user/bottlepost/android/app/src/main/res/drawable"
OUT = os.environ.get("ICON_PREVIEW_DIR", os.path.dirname(__file__))

ROT = -20  # bottle tilt, degrees, around the icon centre
SCALE = 1.12
DROP = 4  # pushes the bottle down into the waves

BOTTLE = "M49,30 L59,30 L59,40 C59,42 68,44 68,50 L68,74 Q68,80 62,80 L46,80 Q40,80 40,74 L40,50 C40,44 49,42 49,40 Z"
WATER = "M40.8,73 C45,71.5 49,74.5 54,73 C59,71.5 63,74.5 67.2,73 L67.2,74 Q67.2,79.2 62,79.2 L46,79.2 Q40.8,79.2 40.8,74 Z"
LIP = "M48,30 L60,30 L60,32.6 L48,32.6 Z"
CORK = "M50,21.5 L58,21.5 Q59,21.5 59,22.5 L59,30.5 L49,30.5 L49,22.5 Q49,21.5 50,21.5 Z"
CORK_BAND = "M49,25.2 L59,25.2 L59,26.4 L49,26.4 Z"
SCROLL = "M46,55.5 L62,55.5 Q63.5,55.5 63.5,57 L63.5,67 Q63.5,68.5 62,68.5 L46,68.5 Q44.5,68.5 44.5,67 L44.5,57 Q44.5,55.5 46,55.5 Z"
SCROLL_END_L = "M44.5,57 Q42.6,62 44.5,67 Q46.4,62 44.5,57 Z"
SCROLL_END_R = "M63.5,57 Q61.6,62 63.5,67 Q65.4,62 63.5,57 Z"
SCROLL_LINES = "M47.5,59.2 L52,59.2 M47.5,62 L52,62 M47.5,64.8 L51,64.8 M56.5,59.2 L60.5,59.2 M56.5,62 L60.5,62 M56.5,64.8 L59.5,64.8"
RIBBON = "M53,55.5 L55.4,55.5 L55.4,68.5 L53,68.5 Z"
BOW = "M54.2,62 L50.8,59.6 L50.8,64.4 Z M54.2,62 L57.6,59.6 L57.6,64.4 Z"
HIGHLIGHT = "M43.6,52 L43.6,71"
NECK_SHINE = "M51,33.5 L51,39"

STARS = [(24, 24, 1.1), (34, 15, 0.7), (78, 16, 0.9), (88, 34, 0.7), (20, 44, 0.6), (86, 52, 0.6)]
WAVE_BACK = "M0,80 C12,76 22,84 36,80 C50,76 60,84 74,80 C88,76 98,84 108,80 L108,108 L0,108 Z"
WAVE_FRONT = "M0,88 C14,84 24,92 40,88 C56,84 66,92 82,88 C94,85 102,90 108,88 L108,108 L0,108 Z"

def circle(cx, cy, r):
    return f"M{cx - r},{cy} a{r},{r} 0 1,0 {2 * r},0 a{r},{r} 0 1,0 {-2 * r},0 Z"

# ---- shape lists: (path, fill, fillAlpha, stroke, strokeWidth, strokeAlpha) ----
FG = [
    (BOTTLE, "#FFFFFF", 0.16, None, 0, 0),
    (WATER, "#7FD1D1", 0.45, None, 0, 0),
    (SCROLL, "#F4E4C1", 1, None, 0, 0),
    (SCROLL_END_L, "#D9BF8C", 1, None, 0, 0),
    (SCROLL_END_R, "#D9BF8C", 1, None, 0, 0),
    (SCROLL_LINES, None, 0, "#B89A63", 0.7, 0.8),
    (RIBBON, "#C8483B", 1, None, 0, 0),
    (BOW, "#E0574A", 1, None, 0, 0),
    (BOTTLE, None, 0, "#D8F3F5", 1.6, 1),
    (HIGHLIGHT, None, 0, "#FFFFFF", 1.6, 0.6),
    (NECK_SHINE, None, 0, "#FFFFFF", 1.1, 0.5),
    (LIP, "#E6F7F8", 0.9, None, 0, 0),
    (CORK, "#C9A06A", 1, None, 0, 0),
    (CORK_BAND, "#A87F4C", 1, None, 0, 0),
]
MONO = [
    (BOTTLE, "#FFFFFF", 0.35, "#FFFFFF", 1.8, 1),
    (SCROLL, "#FFFFFF", 1, None, 0, 0),
    (CORK, "#FFFFFF", 1, None, 0, 0),
]

def vd_path(p, fill, fa, stroke, sw, sa):
    a = [f'android:pathData="{p}"']
    if fill:
        a.append(f'android:fillColor="{fill}"')
        if fa != 1: a.append(f'android:fillAlpha="{fa}"')
    if stroke:
        a += [f'android:strokeColor="{stroke}"', f'android:strokeWidth="{sw}"', 'android:strokeLineCap="round"', 'android:strokeLineJoin="round"']
        if sa != 1: a.append(f'android:strokeAlpha="{sa}"')
    return "        <path " + " ".join(a) + " />"

def vector(name, comment, body):
    xml = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- {comment} Generated from one shape list together with docs/brand/icon.svg. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
{body}
</vector>
'''
    open(os.path.join(RES, name), "w").write(xml)

def group(shapes, front_wave=False):
    # VectorDrawable applies scale, then rotate, then translate (all around the pivot).
    g = (f'    <group android:rotation="{ROT}" android:scaleX="{SCALE}" android:scaleY="{SCALE}" '
         f'android:translateY="{DROP}" android:pivotX="54" android:pivotY="54">\n'
         + "\n".join(vd_path(*s) for s in shapes) + "\n    </group>")
    if front_wave:
        g += f'\n    <path android:pathData="{WAVE_FRONT}" android:fillColor="#FF5FB8BE" android:fillAlpha="0.55" />'
    return g

bg_gradient = '''    <path android:pathData="M0,0 L108,0 L108,108 L0,108 Z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear" android:startX="54" android:startY="0" android:endX="54" android:endY="108"
                android:startColor="#FF13283F" android:centerColor="#FF1F4A73" android:endColor="#FF2F7A86" />
        </aapt:attr>
    </path>'''
moon = '    <path android:pathData="' + circle(80, 27, 6) + '" android:fillColor="#FFF6E7C1" android:fillAlpha="0.9" />\n' \
       '    <path android:pathData="' + circle(82.8, 25.4, 5.2) + '" android:fillColor="#FF16304B" />'
stars = "\n".join(f'    <path android:pathData="{circle(x, y, r)}" android:fillColor="#FFFFFFFF" android:fillAlpha="0.8" />' for x, y, r in STARS)
# The front wave lives in the foreground layer so the bottle floats between the two waves.
waves = f'    <path android:pathData="{WAVE_BACK}" android:fillColor="#FF3B93A0" android:fillAlpha="0.55" />'

vector("ic_launcher_background.xml", "Night sea behind the bottle.", "\n".join([bg_gradient, moon, stars, waves]))
vector("ic_launcher_foreground.xml", "Glass bottle with a rolled, ribboned letter inside, floating in a wave.", group(FG, front_wave=True))
vector("bottle_art.xml", "Bottle alone (no waves) for the in-app launch screen, which draws its own sea.", group(FG))
vector("ic_launcher_monochrome.xml", "Single-colour silhouette for Android 13+ themed icons.", group(MONO))

# ---- SVG preview (same geometry) ----
def svg_path(p, fill, fa, stroke, sw, sa):
    a = [f'd="{p}"', f'fill="{fill}"' if fill else 'fill="none"']
    if fill and fa != 1: a.append(f'fill-opacity="{fa}"')
    if stroke:
        a += [f'stroke="{stroke}"', f'stroke-width="{sw}"', 'stroke-linecap="round"', 'stroke-linejoin="round"']
        if sa != 1: a.append(f'stroke-opacity="{sa}"')
    return "<path " + " ".join(a) + "/>"

def svg(shape_mask, fname, mono=False):
    bg = "" if mono else f'''<defs><linearGradient id="g" x1="0" y1="0" x2="0" y2="1">
<stop offset="0" stop-color="#13283F"/><stop offset="0.5" stop-color="#1F4A73"/><stop offset="1" stop-color="#2F7A86"/></linearGradient></defs>
<rect width="108" height="108" fill="url(#g)"/>
<circle cx="80" cy="27" r="6" fill="#F6E7C1" fill-opacity="0.9"/><circle cx="82.8" cy="25.4" r="5.2" fill="#16304B"/>
''' + "".join(f'<circle cx="{x}" cy="{y}" r="{r}" fill="#fff" fill-opacity="0.8"/>' for x, y, r in STARS) + \
        f'<path d="{WAVE_BACK}" fill="#3B93A0" fill-opacity="0.55"/>'
    shapes = MONO if mono else FG
    tf = f"translate(0 {DROP}) rotate({ROT} 54 54) translate(54 54) scale({SCALE}) translate(-54 -54)"
    body = f'<g transform="{tf}">' + "".join(svg_path(*s) for s in shapes) + "</g>"
    if not mono:
        body += f'<path d="{WAVE_FRONT}" fill="#5FB8BE" fill-opacity="0.55"/>'
    mask = f'<clipPath id="m">{shape_mask}</clipPath>' if shape_mask else ""
    clip = ' clip-path="url(#m)"' if shape_mask else ""
    fill_bg = '<rect width="108" height="108" fill="#555"/>' if mono else ""
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="432" height="432"><defs>{mask}</defs><g{clip}>{fill_bg}{bg}{body}</g></svg>'

open(os.path.join(OUT, "icon_circle.svg"), "w").write(svg('<circle cx="54" cy="54" r="54"/>', ""))
open(os.path.join(OUT, "icon_squircle.svg"), "w").write(svg('<rect x="0" y="0" width="108" height="108" rx="28"/>', ""))
open(os.path.join(OUT, "icon_mono.svg"), "w").write(svg('<circle cx="54" cy="54" r="54"/>', "", mono=True))
os.makedirs("/home/user/bottlepost/docs/brand", exist_ok=True)
open("/home/user/bottlepost/docs/brand/icon.svg", "w").write(svg('<rect x="0" y="0" width="108" height="108" rx="28"/>', ""))
print("ok")
