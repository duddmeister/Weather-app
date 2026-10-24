"""Generates the widget sky effects: still vector drawables, looping animated versions of them, and
the layouts that play those animations on a home screen.

    python3 tools/gen_widget_fx.py [res dir, default app/src/main/res]

Each effect (stars, light and heavy clouds, fog, rain, heavy rain, snow, lightning) comes in three
shapes, picked by the widget's aspect ratio and stretched to fit (see SkyEffect): wide (400x110),
standard (400x220) and square (300x300). For each one this writes:

  drawable/fx_<effect>[_wide|_square].xml       the still image, shown when animation is off
  drawable/fx_<effect>[_wide|_square]_anim.xml  an animated-vector of it that loops seamlessly
  layout/fx_<effect>[_wide|_square].xml         a ProgressBar that plays the animation

Widgets can't run app code, but an indeterminate ProgressBar starts its animated drawable by itself
while it's on screen (that's how loading spinners spin on widgets) and stops it when it isn't.

Moving layers include copies one loop's travel apart, so each loop ends exactly where it began.
The sun and moon glows are still 180x180 images for the widget's top-end corner. Positions are
seeded, so the output is stable.
"""
import random
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

RES = Path(sys.argv[1] if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent / "app/src/main/res")
SHAPES = {"_wide": (400, 110), "": (400, 220), "_square": (300, 300)}
# Lint flags path data longer than 800 characters, so many shapes are spread over several paths.
MAX_PATH = 760
LINEAR = "@android:interpolator/linear"
EASE = "@android:interpolator/accelerate_decelerate"


def f(v):
    return f"{v:.2f}".rstrip("0").rstrip(".")


def circle(cx, cy, r):
    return f"M{f(cx - r)},{f(cy)}a{f(r)},{f(r)} 0 1,0 {f(2 * r)},0a{f(r)},{f(r)} 0 1,0 {f(-2 * r)},0"


def chunks(shapes):
    """Joins shape path data into strings of at most MAX_PATH characters."""
    out, current = [], ""
    for shape in shapes:
        if current and len(current) + len(shape) > MAX_PATH:
            out.append(current)
            current = ""
        current += shape
    return out + [current] if current else out


def argb(color, alpha):
    return f"#{int(round(max(0.0, min(1.0, alpha)) * 255)):02X}{color}"


def radial_fill(cx, cy, r, stops):
    """An inline radial gradient; stops are (offset, color, alpha)."""
    items = "".join(f'<item android:offset="{o}" android:color="{argb(c, a)}" />' for o, c, a in stops)
    return (f'<aapt:attr name="android:fillColor"><gradient android:type="radial" android:centerX="{f(cx)}" '
            f'android:centerY="{f(cy)}" android:gradientRadius="{f(r)}">{items}</gradient></aapt:attr>')


# Animators. A loop runs from 0 to its travel and starts again; copies of the layer one travel
# apart make the restart invisible.
def loop(prop, travel, ms):
    return (f'<objectAnimator android:propertyName="{prop}" android:valueFrom="0" android:valueTo="{f(travel)}" '
            f'android:valueType="floatType" android:duration="{int(ms)}" android:repeatCount="infinite" '
            f'android:interpolator="{LINEAR}" />')


def swing(prop, frm, to, ms, offset=0):
    return (f'<objectAnimator android:propertyName="{prop}" android:valueFrom="{f(frm)}" android:valueTo="{f(to)}" '
            f'android:valueType="floatType" android:duration="{int(ms)}" android:startOffset="{int(offset)}" '
            f'android:repeatCount="infinite" android:repeatMode="reverse" android:interpolator="{EASE}" />')


def keyframes(prop, ms, frames):
    # Fractions need more precision than coordinates: a flash's steps are milliseconds apart.
    kf = "".join(f'<keyframe android:fraction="{fr:.5f}" android:value="{f(v)}" />' for fr, v in frames)
    return (f'<objectAnimator android:duration="{int(ms)}" android:repeatCount="infinite">'
            f'<propertyValuesHolder android:propertyName="{prop}" android:valueType="floatType">{kf}'
            f'</propertyValuesHolder></objectAnimator>')


class Art:
    """One effect at one size: the vector's content plus animations of its named groups and paths."""

    def __init__(self, w, h):
        self.w, self.h = w, h
        self.body = []
        self.targets = []
        self.count = 0

    def name(self, prefix):
        self.count += 1
        return f"{prefix}{self.count}"

    def animate(self, name, *animators):
        self.targets.append((name, "".join(animators)))

    def fills(self, shapes, alpha, color="FFFFFF", animate=None):
        """Filled shapes, spread over as many paths as needed; animate(name) adds each path's animation."""
        out = ""
        for d in chunks(shapes):
            name = self.name("p")
            out += f'<path android:name="{name}" android:fillColor="#{color}" android:fillAlpha="{f(alpha)}" android:pathData="{d}" />'
            if animate:
                animate(name)
        return out

    def strokes(self, shapes, alpha, width, animate=None):
        out = ""
        for d in chunks(shapes):
            name = self.name("p")
            out += (f'<path android:name="{name}" android:strokeColor="#FFFFFF" android:strokeAlpha="{f(alpha)}" '
                    f'android:strokeWidth="{f(width)}" android:strokeLineCap="round" android:strokeLineJoin="round" '
                    f'android:pathData="{d}" />')
            if animate:
                animate(name)
        return out

    def soft(self, cx, cy, r, alpha, color="FFFFFF", mid=0.85, name=None):
        """A circle with a feathered edge; overlapping ones build up into soft clouds and fog."""
        attr = f' android:name="{name}"' if name else ""
        stops = [(0, color, alpha), (0.6, color, alpha * mid), (1, color, 0)]
        return f'<path{attr} android:pathData="{circle(cx, cy, r)}">{radial_fill(cx, cy, r, stops)}</path>'

    def group(self, content, name=None, **attrs):
        a = f' android:name="{name}"' if name else ""
        a += "".join(f' android:{k}="{f(v)}"' for k, v in attrs.items())
        return f"<group{a}>{content}</group>"


ANDROID = "http://schemas.android.com/apk/res/android"
AAPT = "http://schemas.android.com/aapt"
ET.register_namespace("android", ANDROID)
ET.register_namespace("aapt", AAPT)


def save(path, comment, xml):
    """Writes xml indented, one element per line, so changes to the effects read well in a diff."""
    root = ET.fromstring(xml)
    ET.indent(root, "    ")
    path.write_text(f'<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated by tools/gen_widget_fx.py: {comment} -->\n'
                    + ET.tostring(root, encoding="unicode") + "\n")


def write(base, art, header):
    namespaces = f'xmlns:android="{ANDROID}" xmlns:aapt="{AAPT}"'
    save(RES / "drawable" / f"{base}.xml", f"{header} ({base}).",
         f'<vector {namespaces} android:width="{art.w}dp" android:height="{art.h}dp" android:viewportWidth="{art.w}" '
         f'android:viewportHeight="{art.h}">{"".join(art.body)}</vector>')
    if not art.targets:
        return
    targets = "".join(f'<target android:name="{n}"><aapt:attr name="android:animation"><set>{a}</set></aapt:attr></target>'
                      for n, a in art.targets)
    save(RES / "drawable" / f"{base}_anim.xml", f"{header} ({base}), animated.",
         f'<animated-vector {namespaces} android:drawable="@drawable/{base}">{targets}</animated-vector>')
    (RES / "layout" / f"{base}.xml").write_text(f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by tools/gen_widget_fx.py: plays {base}_anim on a widget. An indeterminate ProgressBar
     starts its animated drawable by itself while visible; not indeterminateOnly, so it fills the view. -->
<ProgressBar xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:importantForAccessibility="no"
    android:indeterminate="true"
    android:indeterminateDrawable="@drawable/{base}_anim"
    android:indeterminateOnly="false"
    android:mirrorForRtl="false" />
''')


def area(w, h):
    return w * h / (400 * 220)


def stars(art, rnd):
    w, h = art.w, art.h
    # Twinkling: each set of stars fades down and back at its own pace, like the app's.
    paces = [(1300, 0), (1900, 400), (2600, 900), (3400, 250)]
    sets = {}
    for _ in range(round(64 * area(w, h))):
        x, y, u = rnd.uniform(6, w - 6), rnd.uniform(5, h - 5), rnd.random()
        alpha = min((0.3, 0.45, 0.65, 0.8), key=lambda a: abs(a - (0.25 + 0.6 * u)))
        sets.setdefault((alpha, rnd.randrange(len(paces))), []).append(circle(x, y, 0.45 + 1.0 * u * u))
    halos = []
    for i in range(max(2, round(5 * area(w, h)))):  # a few brighter stars with a halo
        x, y = rnd.uniform(20, w - 20), rnd.uniform(12, h - 12)
        name = art.name("halo")
        halos.append(art.soft(x, y, 6, 0.28, mid=0.4, name=name))
        art.animate(name, swing("fillAlpha", 1, 0.45, 2300 + 500 * i, offset=300 * i))
        sets.setdefault((0.8, i % len(paces)), []).append(circle(x, y, 1.6))
    art.body += halos
    for (alpha, pace), shapes in sorted(sets.items()):
        ms, offset = paces[pace]
        art.body.append(art.fills(shapes, alpha, animate=lambda n: art.animate(n, swing("fillAlpha", alpha, alpha * 0.3, ms, offset))))
    # Now and then a shooting star crosses the top: a bright head and a fainter tail on one track.
    x0, y0, length = 0.3 * w, 0.08 * h, 0.45 * w
    track = f"M{f(x0)},{f(y0)}l{f(0.9 * length)},{f(0.42 * length)}"
    cycle = 12000
    for width, peak, lag in ((1.2, 0.35, 0.02), (1.7, 0.9, 0.006)):
        name = art.name("meteor")
        art.body.append(f'<path android:name="{name}" android:strokeColor="#FFFFFF" android:strokeAlpha="0" '
                        f'android:strokeWidth="{width}" android:strokeLineCap="round" android:trimPathEnd="0" android:pathData="{track}" />')
        art.animate(name,
                    keyframes("trimPathEnd", cycle, [(0, 0), (0.86, 0), (0.93, 1), (1, 1)]),
                    keyframes("trimPathStart", cycle, [(0, 0), (0.86 + lag, 0), (0.93 + lag, 1), (1, 1)]),
                    keyframes("strokeAlpha", cycle, [(0, 0), (0.86, 0), (0.865, peak), (0.92, peak * 0.7), (0.94, 0), (1, 0)]))


# The app's cloud: five soft circles (x, y, radius) around its centre, in dp at scale 1.
LOBES = [(-70, 12, 46), (-28, -10, 58), (26, -4, 54), (72, 12, 42), (0, 22, 60)]


def cloud(art, cx, cy, scale, alpha):
    return "".join(art.soft(cx + dx * scale, cy + dy * scale, r * scale, alpha) for dx, dy, r in LOBES)


def wraps(x, extent, w):
    """Copies of something at x, a width apart, that come into view while drifting left by a width."""
    return [x + k * w for k in (-1, 0, 1, 2) if x + k * w + extent > 0 and x + k * w - extent < 2 * w]


def drifting(art, clouds, alpha, speed):
    """Clouds that drift left forever, moved a width each loop; the copies make the loop seamless."""
    w = art.w
    content = "".join(cloud(art, cx, y, s, alpha) for x, y, s in clouds for cx in wraps(x, 116 * s, w))
    name = art.name("drift")
    art.body.append(art.group(content, name))
    art.animate(name, loop("translateX", -w, 1000 * w / speed))


def light_clouds(art, rnd):
    # One cloud along the top and one low along the bottom, starting in opposite corners.
    w, h = art.w, art.h
    s = min(h / 220, 1.0) * 0.75 + 0.15
    drifting(art, [(w - 85 * s - 10, 6, s)], 0.2, speed=6)
    drifting(art, [(60 * s + 10, h + 4, s * 0.9)], 0.2, speed=4.5)


def heavy_clouds(art, rnd):
    # A deck along the top and a lower bank along the bottom, drifting at different speeds.
    w, h = art.w, art.h
    s = min(h / 220, 1.0) * 0.7 + 0.25
    n = 2 * max(1, round(w / (200 * s)))  # even, so alternating heights repeat across the loop
    deck = [(i * w / n - 20, -4 + 10 * (i % 2), s * (1.0 if i % 2 else 0.9)) for i in range(n)]
    m = max(2, round(w / (170 * s)))
    bank = [(j * w / m + 40 * s, h + 14, s * 0.8) for j in range(m)]
    drifting(art, deck, 0.16, speed=7)
    drifting(art, bank, 0.16, speed=4.5)


def fog(art, rnd):
    # Rows of stretched soft puffs, like the app's fog, sliding past at different speeds.
    w, h = art.w, art.h
    r = 0.2 * min(h, 220)
    for row, (yf, puffs, speed, alpha) in enumerate(((0.3, 2, 5, 0.17), (0.56, 3, 7, 0.2), (0.82, 2, 4, 0.15))):
        y = yf * h
        content = ""
        for k in range(puffs):
            for cx in wraps((k + 0.5 * (row % 2)) * w / puffs, 3 * r, w):
                content += art.group(art.soft(cx, y, r, alpha), pivotX=cx, pivotY=y, scaleX=3.0, scaleY=0.8)
        name = art.name("fog")
        art.body.append(art.group(content, name))
        art.animate(name, loop("translateX", -w, 1000 * w / speed))


SLANT = 0.22  # rain leans left, as if blown by a light wind: dx per unit of fall


def rain(art, rnd, heavy):
    # Two layers, far (dimmer, shorter, slower) and near, each falling along its slant.
    w, h = art.w, art.h
    margin = 15 + SLANT * h
    layers = (
        # density per unit², streak lengths, alphas, width, speed (units/s)
        (0.001 if heavy else 0.00055, (11, 16) if heavy else (8, 12), (0.13, 0.2), 0.8, 330 if heavy else 270),
        (0.0008 if heavy else 0.0004, (16, 26) if heavy else (12, 18), (0.22, 0.3), 1.1 if heavy else 1.0, 480 if heavy else 380),
    )
    for density, lengths, alphas, width, speed in layers:
        streaks = {a: [] for a in alphas}
        for _ in range(round(density * (w + 2 * margin) * h)):
            x, y, ln = rnd.uniform(-margin, w + margin), rnd.uniform(0, h), rnd.uniform(*lengths)
            a = rnd.choice(alphas)
            for k in (0, 1, 2):  # copies one and two loops up the slant, where they reach into view
                if y - k * h + ln > -h:
                    streaks[a].append(f"M{f(x + k * SLANT * h)},{f(y - k * h)}l{f(-SLANT * ln)},{f(ln)}")
        content = "".join(art.strokes(s, a, width) for a, s in streaks.items())
        name = art.name("rain")
        art.body.append(art.group(content, name))
        ms = 1000 * h / speed
        art.animate(name, loop("translateY", h, ms), loop("translateX", -SLANT * h, ms))


def snow(art, rnd):
    # Two layers falling at different speeds; flakes sway in two rhythms per layer.
    w, h = art.w, art.h
    layers = (
        # density, radii, alphas, speed (units/s), sways (amplitude, ms)
        (0.0005, (0.7, 1.3), (0.35, 0.5), 22, ((5, 4200), (7, 5600))),
        (0.00032, (1.2, 2.2), (0.55, 0.75), 34, ((6, 3700), (8, 4900))),
    )
    for density, radii, alphas, speed, sways in layers:
        flakes = [[] for _ in sways]
        for _ in range(round(density * (w + 20) * h)):
            x, y, r = rnd.uniform(-10, w + 10), rnd.uniform(0, h), rnd.uniform(*radii)
            flakes[rnd.randrange(len(sways))].append((x, y, r, rnd.choice(alphas)))
        content = ""
        for (amp, ms), group in zip(sways, flakes):
            shapes = {}
            for x, y, r, a in group:
                for k in (0, 1, 2):  # copies one and two loops up, where they reach into view
                    if y - k * h + r > -h:
                        shapes.setdefault(a, []).append(circle(x, y - k * h, r))
            name = art.name("sway")
            content += art.group("".join(art.fills(s, a) for a, s in shapes.items()), name)
            art.animate(name, swing("translateX", -amp, amp, ms, offset=rnd.randrange(1500)))
        name = art.name("snow")
        art.body.append(art.group(content, name))
        art.animate(name, loop("translateY", h, 1000 * h / speed))


def bolt(art, rnd, x0, depth):
    """A jagged stroke from the top edge, like the app's lightning."""
    w, h = art.w, art.h
    x, y, d = x0, -2.0, f"M{f(x0)},-2"
    for _ in range(8):
        y += depth * h / 8
        x += rnd.uniform(-0.06, 0.06) * w
        d += f"L{f(x)},{f(y)}"
    return d


def flash(start, peak):
    """The app's flash: bright, a flicker, bright again, then a fade; times in an 11 s cycle."""
    t = lambda s: start + s / 11  # seconds after the strike, as a fraction of the cycle
    return [(t(0), 0), (t(0.005), peak), (t(0.06), peak), (t(0.065), 0.3 * peak), (t(0.12), 0.3 * peak),
            (t(0.125), 0.85 * peak), (t(0.2), 0.85 * peak), (t(0.65), 0)]


def lightning(art, rnd):
    # Two strikes per 11 s cycle in different places. The still image shows the first bolt faintly.
    w, h = art.w, art.h
    cycle = 11000
    strikes = ((0.3, 0.62 * w, 0.62), (0.74, 0.3 * w, 0.5))
    sky = art.name("sky")
    art.body.append(f'<path android:name="{sky}" android:fillColor="#FFFFFF" android:fillAlpha="0" android:pathData="M0,0h{w}v{h}h-{w}z" />')
    art.animate(sky, keyframes("fillAlpha", cycle, [(0, 0)] + flash(0.3, 0.22) + flash(0.74, 0.18) + [(1, 0)]))
    for i, (start, x0, depth) in enumerate(strikes):
        d = bolt(art, rnd, x0, depth)
        for width, still, peak in ((6, 0.16, 0.35), (1.8, 0.5, 0.95)):
            name = art.name("bolt")
            art.body.append(f'<path android:name="{name}" android:strokeColor="#F4F2FF" android:strokeAlpha="{f(still if i == 0 else 0)}" '
                            f'android:strokeWidth="{width}" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="{d}" />')
            visible = [(fr, a) for fr, a in flash(start, peak) if fr <= start + 0.2 / 11] + [(start + 0.22 / 11, 0)]
            art.animate(name, keyframes("strokeAlpha", cycle, [(0, 0)] + visible + [(1, 0)]))


EFFECTS = {
    "fx_stars": (stars, "twinkling stars"),
    "fx_clouds_light": (light_clouds, "a few drifting clouds"),
    "fx_clouds_heavy": (heavy_clouds, "drifting cloud banks"),
    "fx_fog": (fog, "drifting fog"),
    "fx_rain": (lambda art, rnd: rain(art, rnd, heavy=False), "rain"),
    "fx_rain_heavy": (lambda art, rnd: rain(art, rnd, heavy=True), "heavy rain"),
    "fx_snow": (snow, "snow"),
    "fx_lightning": (lightning, "lightning"),
}


def glow(name, color, alpha, core):
    # Anchored in the widget's top-end corner by the layout; the light sits up in that corner.
    art = Art(180, 180)
    art.body.append(f'<path android:pathData="M0,0h180v180h-180z">{radial_fill(156, 18, 124, [(0, color, alpha), (1, color, 0)])}</path>')
    if core:
        art.body.append(f'<path android:pathData="M0,0h180v180h-180z">{radial_fill(156, 18, 44, [(0, "FFF4D0", core), (1, "FFF4D0", 0)])}</path>')
    write(name, art, "sun or moon glow")


for (seed, (base, (draw, header))) in enumerate(EFFECTS.items()):
    for suffix, (w, h) in SHAPES.items():
        art = Art(w, h)
        draw(art, random.Random(f"{seed}{suffix}"))
        write(base + suffix, art, header)
glow("fx_sun_glow", "FFE6A3", 0.48, 0.34)
glow("fx_moon_glow", "DDE6FF", 0.22, 0.0)
print(f"Wrote widget effects to {RES}")
