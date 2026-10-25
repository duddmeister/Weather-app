#!/usr/bin/env python3
"""Generates the launcher icon's adaptive-icon layers from one description of the artwork.

    python3 tools/gen_launcher_icon.py                    # writes app/src/main/res/drawable/ic_launcher_*.xml
    python3 tools/gen_launcher_icon.py --preview out.html # also writes an HTML preview of the masks

The canvas is the adaptive icon's 108×108 grid. Launchers show the middle 72×72 through their mask
and only the circle of radius 33 around the centre is safe, so the sun and cloud stay inside it;
only soft glows reach further.
"""
import argparse
import math
import os

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res", "drawable")


def circle(cx, cy, r):
    return f"M{cx - r:.2f},{cy:.2f}A{r:.2f},{r:.2f} 0,1 1,{cx + r:.2f},{cy:.2f}A{r:.2f},{r:.2f} 0,1 1,{cx - r:.2f},{cy:.2f}Z"


def rounded_rect(x, y, w, h, r):
    return (f"M{x + r:.2f},{y:.2f}H{x + w - r:.2f}A{r:.2f},{r:.2f} 0,0 1,{x + w:.2f},{y + r:.2f}V{y + h - r:.2f}"
            f"A{r:.2f},{r:.2f} 0,0 1,{x + w - r:.2f},{y + h:.2f}H{x + r:.2f}A{r:.2f},{r:.2f} 0,0 1,{x:.2f},{y + h - r:.2f}"
            f"V{y + r:.2f}A{r:.2f},{r:.2f} 0,0 1,{x + r:.2f},{y:.2f}Z")


def ellipse(cx, cy, rx, ry):
    return f"M{cx - rx:.2f},{cy:.2f}A{rx:.2f},{ry:.2f} 0,1 1,{cx + rx:.2f},{cy:.2f}A{rx:.2f},{ry:.2f} 0,1 1,{cx - rx:.2f},{cy:.2f}Z"


# Paints: ("solid", "#AARRGGBB") | ("linear", x1, y1, x2, y2, stops) | ("radial", cx, cy, r, stops),
# with stops as [(offset, "#AARRGGBB"), ...].

def linear(x1, y1, x2, y2, *stops):
    return ("linear", x1, y1, x2, y2, list(stops))


def radial(cx, cy, r, *stops):
    return ("radial", cx, cy, r, list(stops))


# --- The artwork -----------------------------------------------------------------------------

SUN = (42.5, 43.5, 11.0)

# The cloud's outline, left to right over the top: three lobes on a flat base, the outer two
# resting on it. (x, y, radius)
CLOUD_CIRCLES = [(45.0, 64.0, 10.0), (58.5, 55.0, 13.5), (71.5, 65.0, 9.0)]


def upper_intersection(a, b):
    (x1, y1, r1), (x2, y2, r2) = a, b
    d = math.hypot(x2 - x1, y2 - y1)
    along = (r1 * r1 - r2 * r2 + d * d) / (2 * d)
    h = math.sqrt(r1 * r1 - along * along)
    mx, my = x1 + along * (x2 - x1) / d, y1 + along * (y2 - y1) / d
    points = [(mx + h * (y2 - y1) / d, my - h * (x2 - x1) / d), (mx - h * (y2 - y1) / d, my + h * (x2 - x1) / d)]
    return min(points, key=lambda p: p[1])


def arc_to(c, start, end):
    """Clockwise (on screen) along circle c from start to end."""
    cx, cy, r = c
    a0 = math.atan2(start[1] - cy, start[0] - cx)
    a1 = math.atan2(end[1] - cy, end[0] - cx)
    sweep = (a1 - a0) % (2 * math.pi)
    return f"A{r:.2f},{r:.2f} 0,{1 if sweep > math.pi else 0} 1,{end[0]:.2f},{end[1]:.2f}"


def cloud(dx=0.0, dy=0.0):
    """One smooth outline, so the cloud fills, clips and lights as a single shape."""
    circles = [(x + dx, y + dy, r) for x, y, r in CLOUD_CIRCLES]
    first, last = circles[0], circles[-1]
    start = (first[0], first[1] + first[2])
    path = [f"M{start[0]:.2f},{start[1]:.2f}"]
    point = start
    for a, b in zip(circles, circles[1:]):
        meet = upper_intersection(a, b)
        path.append(arc_to(a, point, meet))
        point = meet
    bottom = (last[0], last[1] + last[2])
    path.append(arc_to(last, point, bottom))
    path.append("Z")
    return "".join(path)


CLOUD = cloud()

BACKGROUND = [
    # Deep blue at the top shading to a bright sky, like the app's clear day.
    ("path", "M0,0h108v108h-108z", linear(30, 14, 78, 98, (0, "#FF0F3DA6"), (0.55, "#FF2876E3"), (1, "#FF5EC0F8"))),
]

FOREGROUND = [
    # Sunlight brightening the sky around the sun (near white: yellow over blue would turn grey).
    # It fades out before the splash screen's circular mask, which would otherwise cut it off.
    ("path", circle(SUN[0], SUN[1], 30), radial(SUN[0], SUN[1], 30, (0, "#8CFFFFFF"), (0.35, "#2EFFFBEF"), (0.68, "#08FFFFFF"), (1, "#00FFFFFF"))),
    ("path", circle(SUN[0], SUN[1], 17), radial(SUN[0], SUN[1], 17, (0, "#B3FFF4D0"), (0.7, "#40FFF4D0"), (1, "#00FFF4D0"))),
    # The sun, lit from the top left.
    ("path", circle(*SUN), radial(SUN[0] - 4, SUN[1] - 4.5, 17, (0, "#FFFFFBE3"), (0.45, "#FFFFD54A"), (1, "#FFFF9F1C"))),
    ("clip", CLOUD, [
        # A rim of sunlight along the edges facing the sun, left where the body below doesn't reach.
        ("path", CLOUD, ("solid", "#FFFFF6DC")),
        # Bright on top, cooler underneath.
        ("path", cloud(0.7, 0.9), linear(0, 41, 0, 74, (0, "#FFFFFFFF"), (0.6, "#FFF1F6FC"), (1, "#FFD3E1F3"))),
        # Warm light on the side facing the sun.
        ("path", CLOUD, radial(SUN[0] + 1, SUN[1] + 1, 24, (0, "#B3FFF0C8"), (0.6, "#33FFF0C8"), (1, "#00FFF0C8"))),
        # Shade along the underside.
        ("path", CLOUD, linear(0, 64, 0, 74, (0, "#006F8FBE"), (1, "#596F8FBE"))),
    ]),
]

# Themed icons tint one layer with the wallpaper colour; alpha keeps the sun lighter than the cloud.
MONOCHROME = [
    ("path", circle(*SUN), ("solid", "#8CFFFFFF")),
    ("path", CLOUD, ("solid", "#FFFFFFFF")),
]


# --- Android vector drawables ----------------------------------------------------------------

def xml_paint(paint, indent):
    kind = paint[0]
    if kind == "solid":
        return f' android:fillColor="{paint[1]}"', ""
    pad = " " * indent
    if kind == "linear":
        _, x1, y1, x2, y2, stops = paint
        head = f'<gradient android:type="linear" android:startX="{x1}" android:startY="{y1}" android:endX="{x2}" android:endY="{y2}">'
    else:
        _, cx, cy, r, stops = paint
        head = f'<gradient android:type="radial" android:centerX="{cx}" android:centerY="{cy}" android:gradientRadius="{r}">'
    items = "".join(f'\n{pad}            <item android:offset="{o}" android:color="{c}" />' for o, c in stops)
    body = (f'\n{pad}    <aapt:attr name="android:fillColor">\n{pad}        {head}{items}\n{pad}        </gradient>'
            f'\n{pad}    </aapt:attr>\n{pad}')
    return "", body


def xml_items(items, indent=4):
    pad = " " * indent
    out = []
    for item in items:
        if item[0] == "path":
            _, d, paint = item
            attr, body = xml_paint(paint, indent)
            if body:
                out.append(f'{pad}<path android:pathData="{d}"{attr}>{body}</path>')
            else:
                out.append(f'{pad}<path android:pathData="{d}"{attr} />')
        else:
            _, clip, children = item
            out.append(f'{pad}<group>\n{pad}    <clip-path android:pathData="{clip}" />\n'
                       + xml_items(children, indent + 4) + f'\n{pad}</group>')
    return "\n".join(out)


def vector(items, comment):
    body = xml_items(items)
    # Gradients are inline aapt attributes; only declare that namespace where one is used.
    aapt = '\n    xmlns:aapt="http://schemas.android.com/aapt"' if "aapt:attr" in body else ""
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            f'<!-- {comment} Generated by tools/gen_launcher_icon.py; edit that, not this. -->\n'
            f'<vector xmlns:android="http://schemas.android.com/apk/res/android"{aapt}\n'
            '    android:width="108dp"\n    android:height="108dp"\n'
            '    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
            + body + "\n</vector>\n")


# --- SVG preview -----------------------------------------------------------------------------

def svg_color(c):
    a = int(c[1:3], 16) / 255
    return f"#{c[3:]}", a


class Svg:
    def __init__(self, prefix):
        self.prefix, self.defs, self.count = prefix, [], 0

    def paint(self, paint):
        if paint[0] == "solid":
            color, alpha = svg_color(paint[1])
            return f'fill="{color}" fill-opacity="{alpha:.3f}"'
        self.count += 1
        gid = f"{self.prefix}{self.count}"
        stops = "".join(f'<stop offset="{o}" stop-color="{svg_color(c)[0]}" stop-opacity="{svg_color(c)[1]:.3f}"/>' for o, c in paint[-1])
        if paint[0] == "linear":
            _, x1, y1, x2, y2, _ = paint
            self.defs.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}">{stops}</linearGradient>')
        else:
            _, cx, cy, r, _ = paint
            self.defs.append(f'<radialGradient id="{gid}" gradientUnits="userSpaceOnUse" cx="{cx}" cy="{cy}" r="{r}">{stops}</radialGradient>')
        return f'fill="url(#{gid})"'

    def items(self, items):
        out = []
        for item in items:
            if item[0] == "path":
                out.append(f'<path d="{item[1]}" {self.paint(item[2])}/>')
            else:
                self.count += 1
                cid = f"{self.prefix}c{self.count}"
                self.defs.append(f'<clipPath id="{cid}"><path d="{item[1]}"/></clipPath>')
                out.append(f'<g clip-path="url(#{cid})">{self.items(item[2])}</g>')
        return "".join(out)


def preview(path):
    def icon(mask, size, prefix):
        svg = Svg(prefix)
        body = svg.items(BACKGROUND) + svg.items(FOREGROUND)
        return (f'<svg viewBox="18 18 72 72" width="{size}" height="{size}"><defs>{"".join(svg.defs)}'
                f'<clipPath id="{prefix}m">{mask}</clipPath></defs><g clip-path="url(#{prefix}m)">{body}</g></svg>')

    def themed(size, prefix):
        svg = Svg(prefix)
        body = svg.items([(k, d, ("solid", "#FF1B2A4E" if p[1] == "#FFFFFFFF" else "#8C1B2A4E")) for k, d, p in MONOCHROME])
        return (f'<svg viewBox="18 18 72 72" width="{size}" height="{size}"><defs>{"".join(svg.defs)}</defs>'
                f'<circle cx="54" cy="54" r="36" fill="#D6E3FF"/>{body}</svg>')

    def splash(size, prefix):
        svg = Svg(prefix)
        body = svg.items(FOREGROUND)
        return (f'<svg viewBox="0 0 108 108" width="{size}" height="{size}"><defs>{"".join(svg.defs)}</defs>'
                f'<rect width="108" height="108" fill="#0F2A5C"/>{body}'
                '<circle cx="54" cy="54" r="33" fill="none" stroke="#ff5555" stroke-width="0.4" stroke-dasharray="1.5"/></svg>')

    masks = {
        "circle": '<circle cx="54" cy="54" r="36"/>',
        "squircle": '<rect x="18" y="18" width="72" height="72" rx="22"/>',
        "rounded": '<rect x="18" y="18" width="72" height="72" rx="12"/>',
    }
    large = "".join(icon(mask, 220, f"b{i}") for i, mask in enumerate(masks.values()))
    light = icon(masks["circle"], 56, "l1") + icon(masks["squircle"], 56, "l2")
    dark = icon(masks["circle"], 48, "d1") + icon(masks["squircle"], 48, "d2")
    page = ('<html><body style="margin:0;background:#1f2430">'
            f'<div style="display:flex;gap:28px;padding:28px;align-items:center">{large}{themed(220, "t")}</div>'
            f'<div style="display:flex;gap:28px;padding:0 28px 28px;align-items:center">{splash(220, "sp")}'
            f'<div style="background:#e9eef6;padding:18px;border-radius:16px;display:flex;gap:18px">{light}</div>'
            f'<div style="background:#2b2f36;padding:18px;border-radius:16px;display:flex;gap:18px">{dark}</div></div>'
            '</body></html>')
    with open(path, "w") as f:
        f.write(page)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--preview", help="also write an HTML preview here")
    args = parser.parse_args()
    files = {
        "ic_launcher_background.xml": vector(BACKGROUND, "The launcher icon's sky."),
        "ic_launcher_foreground.xml": vector(FOREGROUND, "The launcher icon's sun and cloud (also the splash screen icon)."),
        "ic_launcher_monochrome.xml": vector(MONOCHROME, "The launcher icon for themed icons."),
    }
    for name, content in files.items():
        with open(os.path.join(RES, name), "w") as f:
            f.write(content)
    if args.preview:
        preview(args.preview)


if __name__ == "__main__":
    main()
