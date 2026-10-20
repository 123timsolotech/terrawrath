"""TerraWrath procedural texture generator.

Generates every texture the mod uses (particles, disaster geometry, GUI icons,
items, overlays) with numpy + PIL so the palette stays consistent.
Run:  python tools/gen_textures.py
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "terrawrath", "textures")
RNG = np.random.default_rng(1337)

EMBER = (255, 90, 31)
GOLD = (255, 194, 61)
ASH = (27, 26, 31)
VIOLET = (123, 63, 228)
CYAN = (127, 232, 255)
BONE = (244, 238, 230)


def out(path, img):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    img.save(full)


def to_img(rgba):
    return Image.fromarray(np.clip(rgba, 0, 255).astype(np.uint8), "RGBA")


def fbm(w, h, beta=2.0, aniso=(1.0, 1.0), seed=None):
    """Periodic (tileable) fractal noise via spectral synthesis, normalised to 0..1."""
    rng = np.random.default_rng(seed) if seed is not None else RNG
    noise = rng.standard_normal((h, w))
    fy = np.fft.fftfreq(h)[:, None] * aniso[1]
    fx = np.fft.fftfreq(w)[None, :] * aniso[0]
    f = np.sqrt(fx * fx + fy * fy)
    f[0, 0] = 1.0
    spec = np.fft.fft2(noise) / (f ** (beta / 2.0))
    spec[0, 0] = 0
    r = np.real(np.fft.ifft2(spec))
    r -= r.min()
    r /= max(r.max(), 1e-9)
    return r


def radial(size, cx=None, cy=None):
    cx = (size - 1) / 2 if cx is None else cx
    cy = (size - 1) / 2 if cy is None else cy
    y, x = np.mgrid[0:size, 0:size]
    return np.sqrt((x - cx) ** 2 + (y - cy) ** 2) / (size / 2)


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def lerp_color(stops, t):
    """stops: list of (pos, (r,g,b)); t: array 0..1 -> HxWx3."""
    t = np.clip(t, 0, 1)
    res = np.zeros(t.shape + (3,))
    for i in range(len(stops) - 1):
        p0, c0 = stops[i]
        p1, c1 = stops[i + 1]
        m = (t >= p0) & (t <= p1)
        k = ((t - p0) / max(p1 - p0, 1e-9))[..., None]
        seg = np.array(c0)[None, None, :] * (1 - k) + np.array(c1)[None, None, :] * k
        res = np.where(m[..., None], seg, res)
    return res


# ----------------------------------------------------------------------------- particles

def particle_glow():
    s = 32
    d = radial(s)
    a = np.clip(1 - d, 0, 1) ** 2.2
    core = np.clip(1 - d * 3.0, 0, 1) ** 1.5
    a = np.clip(a + core, 0, 1)
    rgba = np.zeros((s, s, 4))
    rgba[..., :3] = 255
    rgba[..., 3] = a * 255
    out("particle/glow.png", to_img(rgba))


def particle_embers():
    s = 16
    for i in range(4):
        d = radial(s, 7.5 + RNG.uniform(-0.8, 0.8), 7.5 + RNG.uniform(-0.8, 0.8))
        r = 0.42 + 0.16 * i / 3
        core = np.clip(1 - d / r, 0, 1)
        halo = np.clip(1 - d, 0, 1) ** 1.6
        a = np.clip(core * 1.4 + halo * 0.7, 0, 1)
        col = lerp_color([(0, (255, 70, 10)), (0.5, (255, 160, 40)), (1, (255, 250, 220))], np.clip(core * 1.3, 0, 1))
        rgba = np.dstack([col, a * 255])
        out(f"particle/ember_{i}.png", to_img(rgba))


def particle_sparks():
    s = 16
    y, x = np.mgrid[0:s, 0:s]
    cx = cy = 7.5
    for i in range(2):
        ang = i * math.pi / 4
        dx, dy = x - cx, y - cy
        u = dx * math.cos(ang) + dy * math.sin(ang)
        v = -dx * math.sin(ang) + dy * math.cos(ang)
        star = np.exp(-(u ** 2) / 0.9) * np.exp(-(v ** 2) / 20) + np.exp(-(v ** 2) / 0.9) * np.exp(-(u ** 2) / 20)
        glow = np.exp(-(dx ** 2 + dy ** 2) / 6)
        a = np.clip(star + glow, 0, 1)
        rgba = np.zeros((s, s, 4))
        rgba[..., :3] = 255
        rgba[..., 3] = a * 255
        out(f"particle/spark_{i}.png", to_img(rgba))


def puff(s, seed, rough=1.0, hole=False):
    n = fbm(s, s, beta=3.0, seed=seed)
    d = radial(s)
    shape = np.clip(1 - d * (1.0 + 0.55 * (n - 0.5) * rough * 2), 0, 1)
    a = smoothstep(0.0, 0.55, shape) * (0.55 + 0.45 * n)
    if hole:
        a *= 0.6 + 0.4 * n
    shade = 0.72 + 0.28 * (n * 0.6 + (1 - radial(s, s * 0.35, s * 0.3)) * 0.4)
    return np.clip(a, 0, 1), np.clip(shade, 0, 1)


def particle_smoke():
    s = 32
    for i in range(8):
        a, shade = puff(s, 100 + i)
        rgba = np.zeros((s, s, 4))
        rgba[..., :3] = (shade * 255)[..., None]
        rgba[..., 3] = a * 255
        out(f"particle/smoke_{i}.png", to_img(rgba))


def particle_dust():
    s = 32
    for i in range(4):
        a, shade = puff(s, 200 + i, rough=1.6, hole=True)
        rgba = np.zeros((s, s, 4))
        rgba[..., :3] = (shade * 255)[..., None]
        rgba[..., 3] = a * 210
        out(f"particle/dust_{i}.png", to_img(rgba))


def particle_ash():
    s = 8
    for i in range(4):
        img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
        dr = ImageDraw.Draw(img)
        pts = []
        for k in range(5):
            ang = k / 5 * math.tau + RNG.uniform(-0.4, 0.4)
            r = RNG.uniform(1.5, 3.4)
            pts.append((3.5 + math.cos(ang) * r, 3.5 + math.sin(ang) * r))
        g = int(RNG.uniform(160, 220))
        dr.polygon(pts, fill=(g, g, g, 235))
        out(f"particle/ash_{i}.png", img)


def particle_rain():
    s = 16
    y, x = np.mgrid[0:s, 0:s]
    a = np.exp(-((x - 7.5) ** 2) / 0.45) * np.clip(np.sin((y + 0.5) / s * math.pi), 0, 1) ** 0.7
    rgba = np.zeros((s, s, 4))
    rgba[..., 0] = 200
    rgba[..., 1] = 220
    rgba[..., 2] = 255
    rgba[..., 3] = a * 210
    out("particle/rain.png", to_img(rgba))


def particle_snow():
    s = 16
    for i in range(4):
        d = radial(s, 7.5 + RNG.uniform(-1, 1), 7.5 + RNG.uniform(-1, 1))
        r = 0.18 + 0.1 * i
        a = np.clip(1 - d / (r * 2.2), 0, 1) ** 1.3
        n = fbm(s, s, seed=300 + i)
        a = a * (0.75 + 0.25 * n)
        rgba = np.zeros((s, s, 4))
        rgba[..., 0] = 245
        rgba[..., 1] = 250
        rgba[..., 2] = 255
        rgba[..., 3] = np.clip(a * 1.3, 0, 1) * 255
        out(f"particle/snow_{i}.png", to_img(rgba))


def particle_spray():
    s = 32
    for i in range(4):
        rgba = np.zeros((s, s, 4))
        acc = np.zeros((s, s))
        y, x = np.mgrid[0:s, 0:s]
        for _ in range(14):
            cx, cy = RNG.normal(15.5, 5, 2)
            r = RNG.uniform(1.0, 3.5)
            acc += np.exp(-((x - cx) ** 2 + (y - cy) ** 2) / (r * r))
        mist, _ = puff(s, 400 + i)
        a = np.clip(acc * 0.8 + mist * 0.5, 0, 1)
        rgba[..., 0] = 235
        rgba[..., 1] = 248
        rgba[..., 2] = 255
        rgba[..., 3] = a * 235
        out(f"particle/spray_{i}.png", to_img(rgba))


def particle_flame():
    s = 32
    for i in range(6):
        n = fbm(s, s, beta=2.0, aniso=(1.0, 0.45), seed=500 + i)
        y, x = np.mgrid[0:s, 0:s]
        yy = y / (s - 1)
        width = 0.15 + 0.32 * (yy ** 0.8)
        dx = np.abs((x - 15.5) / 16 + (n - 0.5) * 0.35)
        body = np.clip(1 - dx / width, 0, 1) * smoothstep(0.02, 0.35, yy) * (1 - smoothstep(0.85, 1.0, yy))
        body = body * (0.6 + 0.6 * n)
        t = np.clip(body * 1.2, 0, 1)
        col = lerp_color([(0, (200, 30, 5)), (0.4, (255, 110, 20)), (0.75, (255, 200, 70)), (1, (255, 255, 230))], t)
        rgba = np.dstack([col, np.clip(body * 1.6, 0, 1) * 255])
        out(f"particle/flame_{i}.png", to_img(rgba))


def particle_shard():
    s = 16
    for i in range(3):
        img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
        dr = ImageDraw.Draw(img)
        ang = RNG.uniform(0, math.tau)
        pts = []
        for k, r in enumerate([7, 2.5, 6, 2]):
            a = ang + k * math.pi / 2 + RNG.uniform(-0.3, 0.3)
            pts.append((7.5 + math.cos(a) * r, 7.5 + math.sin(a) * r))
        dr.polygon(pts, fill=(205, 240, 255, 240), outline=(255, 255, 255, 255))
        out(f"particle/shard_{i}.png", img)


# ----------------------------------------------------------------------------- disaster geometry

def tex_funnel():
    w, h = 256, 256
    n1 = fbm(w, h, beta=2.6, aniso=(1.6, 0.22), seed=11)
    n2 = fbm(w, h, beta=1.8, aniso=(1.0, 0.35), seed=12)
    v = np.clip(n1 * 0.72 + n2 * 0.28, 0, 1)
    v = smoothstep(0.32, 0.72, v)
    shade = 0.32 + 0.68 * v
    a = 0.28 + 0.72 * smoothstep(0.25, 0.75, n1 * 0.6 + n2 * 0.4)
    rgba = np.zeros((h, w, 4))
    rgba[..., 0] = shade * 235
    rgba[..., 1] = shade * 228
    rgba[..., 2] = shade * 220
    rgba[..., 3] = a * 255
    out("entity/funnel.png", to_img(rgba))


def tex_cloud():
    w = h = 256
    n = fbm(w, h, beta=3.0, seed=21)
    n2 = fbm(w, h, beta=2.2, seed=22)
    v = np.clip(n * 0.75 + n2 * 0.25, 0, 1)
    a = smoothstep(0.25, 0.75, v)
    shade = 0.6 + 0.4 * smoothstep(0.3, 1.0, v)
    rgba = np.zeros((h, w, 4))
    rgba[..., :3] = (shade * 255)[..., None]
    rgba[..., 3] = (0.25 + 0.75 * a) * 255
    out("entity/cloud.png", to_img(rgba))


def tex_swirl():
    """Rotating cloud deck texture: polar spiral arms (u = angle, v = radius)."""
    w, h = 512, 128
    n = fbm(w, h, beta=2.8, aniso=(0.3, 1.0), seed=31)
    n2 = fbm(w, h, beta=2.0, aniso=(0.5, 1.0), seed=33)
    y, x = np.mgrid[0:h, 0:w]
    arms = 0.5 + 0.5 * np.sin((x / w) * math.tau * 2 + (y / h) * 6.0 + n * 5)
    v = np.clip(n * 0.6 + n2 * 0.25 + arms * 0.15, 0, 1)
    shade = 0.45 + 0.55 * smoothstep(0.2, 0.95, v)
    a = 0.35 + 0.65 * smoothstep(0.15, 0.8, v)
    rgba = np.zeros((h, w, 4))
    rgba[..., :3] = (shade * 255)[..., None]
    rgba[..., 3] = a * 255
    out("entity/swirl.png", to_img(rgba))


def tex_water():
    w, h = 256, 256
    n = fbm(w, h, beta=3.0, aniso=(0.6, 1.4), seed=41)
    n2 = fbm(w, h, beta=2.6, seed=42)
    caustic = np.abs(np.sin((n * 9 + n2 * 4) * math.pi))
    caustic = smoothstep(0.86, 1.0, caustic)
    y = np.mgrid[0:h, 0:w][0] / (h - 1)
    base = lerp_color([(0, (40, 150, 190)), (0.5, (20, 95, 150)), (1, (10, 45, 95))], np.clip(y * 0.6 + n * 0.4, 0, 1))
    col = base + caustic[..., None] * np.array([150, 200, 210]) * 0.6
    rgba = np.dstack([col, np.full((h, w), 225)])
    out("entity/water.png", to_img(rgba))


def tex_foam():
    w, h = 256, 128
    n = fbm(w, h, beta=1.8, aniso=(0.5, 1.0), seed=51)
    n2 = fbm(w, h, beta=1.2, seed=52)
    y = np.mgrid[0:h, 0:w][0] / (h - 1)
    a = smoothstep(0.35, 0.7, n * 0.7 + n2 * 0.3) * (1 - smoothstep(0.55, 1.0, y)) + (1 - smoothstep(0.0, 0.35, y)) * 0.8
    a = np.clip(a, 0, 1)
    shade = 0.88 + 0.12 * n2
    rgba = np.zeros((h, w, 4))
    rgba[..., 0] = shade * 245
    rgba[..., 1] = shade * 252
    rgba[..., 2] = shade * 255
    rgba[..., 3] = a * 255
    out("entity/foam.png", to_img(rgba))


def tex_disk():
    w, h = 512, 128
    n = fbm(w, h, beta=2.4, aniso=(0.12, 1.0), seed=61)
    n2 = fbm(w, h, beta=1.8, aniso=(0.25, 1.0), seed=62)
    v = np.mgrid[0:h, 0:w][0] / (h - 1)  # 0 = inner edge, 1 = outer edge
    streak = smoothstep(0.25, 0.85, n * 0.7 + n2 * 0.3)
    heat = np.clip((1 - v) ** 1.4 * 0.85 + streak * 0.35 - 0.1, 0, 1)
    col = lerp_color([(0, (90, 30, 170)), (0.25, (200, 50, 110)), (0.5, (255, 100, 30)), (0.75, (255, 190, 80)), (0.92, (255, 245, 210)), (1, (255, 255, 255))], heat)
    edge_in = smoothstep(0.0, 0.06, v)
    edge_out = 1 - smoothstep(0.55, 1.0, v)
    a = np.clip((0.35 + 0.65 * streak) * edge_in * edge_out * (0.55 + 0.6 * (1 - v)), 0, 1)
    out("entity/disk.png", to_img(np.dstack([col, a * 255])))


def tex_rock():
    s = 64
    n = fbm(s, s, beta=2.0, seed=71)
    pts = RNG.uniform(0, s, (18, 2))
    y, x = np.mgrid[0:s, 0:s]
    d = np.full((s, s), 1e9)
    d2 = np.full((s, s), 1e9)
    for px, py in pts:
        for ox in (-s, 0, s):
            for oy in (-s, 0, s):
                dd = np.sqrt((x - px - ox) ** 2 + (y - py - oy) ** 2)
                upd = dd < d
                d2 = np.where(upd, d, np.minimum(d2, dd))
                d = np.where(upd, dd, d)
    edge = d2 - d
    crack = 1 - smoothstep(0.0, 2.2, edge)
    base = lerp_color([(0, (22, 18, 18)), (1, (70, 58, 52))], n)
    hot = lerp_color([(0, (255, 80, 10)), (1, (255, 220, 120))], crack)
    col = base * (1 - crack[..., None]) + hot * crack[..., None]
    out("entity/rock.png", to_img(np.dstack([col, np.full((s, s), 255)])))
    glow = np.dstack([hot, crack * 255])
    out("entity/rock_glow.png", to_img(glow))


def tex_lava():
    s = 128
    n = fbm(s, s, beta=3.2, seed=81)
    n2 = fbm(s, s, beta=2.4, seed=82)
    v = np.clip(n * 0.7 + n2 * 0.3, 0, 1)
    veins = smoothstep(0.55, 0.9, np.abs(np.sin(v * math.pi * 5)))
    t = np.clip(0.35 + veins * 0.65 - (1 - v) * 0.2, 0, 1)
    col = lerp_color([(0, (120, 20, 5)), (0.4, (230, 70, 10)), (0.7, (255, 150, 30)), (1, (255, 240, 170))], t)
    out("entity/lava.png", to_img(np.dstack([col, np.full((s, s), 255)])))


def tex_snow_wall():
    w = h = 256
    n = fbm(w, h, beta=2.0, aniso=(0.15, 1.0), seed=91)
    n2 = fbm(w, h, beta=1.3, aniso=(0.3, 1.0), seed=92)
    v = np.clip(n * 0.6 + n2 * 0.4, 0, 1)
    a = 0.3 + 0.7 * smoothstep(0.2, 0.85, v)
    rgba = np.zeros((h, w, 4))
    rgba[..., 0] = 236
    rgba[..., 1] = 244
    rgba[..., 2] = 255
    rgba[..., 3] = a * 255
    out("entity/snow_wall.png", to_img(rgba))


def tex_white():
    out("entity/white.png", to_img(np.full((16, 16, 4), 255)))


def tex_ring():
    w, h = 256, 32
    n = fbm(w, h, beta=1.8, aniso=(0.3, 1.0), seed=101)
    v = np.mgrid[0:h, 0:w][0] / (h - 1)
    prof = np.exp(-((v - 0.5) ** 2) / 0.03)
    a = np.clip(prof * (0.6 + 0.5 * n), 0, 1)
    rgba = np.zeros((h, w, 4))
    rgba[..., :3] = 255
    rgba[..., 3] = a * 255
    out("entity/ring.png", to_img(rgba))


def tex_beam():
    w, h = 64, 256
    n = fbm(w, h, beta=2.0, aniso=(1.0, 0.2), seed=111)
    u = np.mgrid[0:h, 0:w][1] / (w - 1)
    prof = np.exp(-((u - 0.5) ** 2) / 0.04)
    a = np.clip(prof * (0.55 + 0.6 * n), 0, 1)
    rgba = np.zeros((h, w, 4))
    rgba[..., :3] = 255
    rgba[..., 3] = a * 255
    out("entity/beam.png", to_img(rgba))


# ----------------------------------------------------------------------------- overlays

def overlay_frost():
    w, h = 512, 288
    y, x = np.mgrid[0:h, 0:w]
    ex = np.minimum(x, w - 1 - x) / (w * 0.5)
    ey = np.minimum(y, h - 1 - y) / (h * 0.5)
    edge = np.minimum(ex * 1.0, ey * 1.0)
    n = fbm(w, h, beta=1.6, seed=121)
    n2 = fbm(w, h, beta=2.4, seed=122)
    crystals = np.abs(np.sin((n * 22) * math.pi)) ** 8
    f = 1 - smoothstep(0.0, 0.42 + 0.25 * n2, edge + (n - 0.5) * 0.25)
    a = np.clip(f * (0.65 + 0.35 * crystals) + f * f * 0.35, 0, 1)
    col = lerp_color([(0, (150, 200, 235)), (0.6, (215, 238, 255)), (1, (255, 255, 255))], np.clip(crystals * 0.6 + f * 0.5, 0, 1))
    out("misc/frost.png", to_img(np.dstack([col, a * 235])))


def overlay_vignette():
    w, h = 256, 144
    y, x = np.mgrid[0:h, 0:w]
    dx = (x - (w - 1) / 2) / (w / 2)
    dy = (y - (h - 1) / 2) / (h / 2)
    d = np.sqrt(dx * dx * 0.8 + dy * dy)
    a = smoothstep(0.45, 1.25, d) ** 1.3
    rgba = np.zeros((h, w, 4))
    rgba[..., :3] = 255
    rgba[..., 3] = a * 255
    out("misc/vignette.png", to_img(rgba))


def overlay_haze():
    w, h = 256, 256
    n = fbm(w, h, beta=1.5, aniso=(1.0, 0.35), seed=131)
    a = smoothstep(0.35, 1.0, n) * 0.85
    rgba = np.zeros((h, w, 4))
    rgba[..., 0] = 255
    rgba[..., 1] = 140
    rgba[..., 2] = 60
    rgba[..., 3] = a * 255
    out("misc/haze.png", to_img(rgba))


def overlay_hazard():
    w, h = 64, 16
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    dr = ImageDraw.Draw(img)
    for i in range(-2, 10):
        x0 = i * 8
        dr.polygon([(x0, h), (x0 + 4, h), (x0 + 4 + h, 0), (x0 + h, 0)], fill=(255, 90, 31, 255))
    for i in range(-2, 10):
        x0 = i * 8 + 4
        dr.polygon([(x0, h), (x0 + 4, h), (x0 + 4 + h, 0), (x0 + h, 0)], fill=(20, 18, 22, 255))
    out("misc/hazard.png", img)


def overlay_hazard_red():
    """Red/black emergency stripes for the panel header and alert banners."""
    w, h = 64, 16
    img = Image.new("RGBA", (w * 4, h * 4), (14, 10, 12, 255))
    dr = ImageDraw.Draw(img)
    for i in range(-4, 12):
        x0 = i * 32
        dr.polygon([(x0, h * 4), (x0 + 16, h * 4), (x0 + 16 + h * 4, 0), (x0 + h * 4, 0)], fill=(214, 34, 40, 255))
    img = img.resize((w, h), Image.LANCZOS)
    out("misc/hazard_red.png", img)


def overlay_iris():
    """Soft circular hole: transparent inside, opaque outside (closing-circle darkness)."""
    s = 256
    r = radial(s)
    a = smoothstep(0.62, 1.0, r)
    rgba = np.zeros((s, s, 4))
    rgba[..., 0] = 5
    rgba[..., 1] = 3
    rgba[..., 2] = 9
    rgba[..., 3] = a * 255
    out("misc/iris.png", to_img(rgba))


def overlay_edges():
    """Horizontal darkness that creeps in from the screen sides."""
    w, h = 128, 4
    x = np.mgrid[0:h, 0:w][1] / (w - 1)
    a = (1 - x) ** 1.6
    rgba = np.zeros((h, w, 4))
    rgba[..., 0] = 5
    rgba[..., 1] = 3
    rgba[..., 2] = 9
    rgba[..., 3] = a * 255
    out("misc/edge_l.png", to_img(rgba))
    out("misc/edge_r.png", to_img(rgba[:, ::-1]))


def overlay_ripple():
    """Soft circular ring for the time-dilation ripples."""
    s = 256
    r = radial(s)
    a = np.exp(-((r - 0.82) ** 2) / 0.006) * (1 - smoothstep(0.97, 1.0, r))
    rgba = np.zeros((s, s, 4))
    rgba[..., :3] = 255
    rgba[..., 3] = np.clip(a, 0, 1) * 255
    out("misc/ripple.png", to_img(rgba))


def tex_accretion():
    """Smooth swirling accretion disk: u = angle (tileable), v = radius. Soft long streaks, low contrast."""
    w, h = 1024, 256
    n = fbm(w, h, beta=3.2, aniso=(1.0, 0.07), seed=91)
    n2 = fbm(w, h, beta=2.4, aniso=(1.0, 0.14), seed=92)
    v = smoothstep(0.0, 1.0, n * 0.7 + n2 * 0.3)
    b = 0.62 + 0.38 * v
    rgba = np.zeros((h, w, 4))
    rgba[..., :3] = 255
    rgba[..., 3] = b * 255
    img = to_img(rgba).filter(ImageFilter.GaussianBlur(1.2))
    out("entity/accretion.png", img)


def tex_aurora():
    """Aurora curtain: vertical rays (u along the curtain), v = 0 bottom edge .. 1 top."""
    w, h = 512, 128
    rays = fbm(w, 1, beta=1.4, seed=95)[0]
    rays2 = fbm(w, 1, beta=2.6, seed=96)[0]
    ray = np.clip(0.35 + 0.9 * (rays * 0.6 + rays2 * 0.4) - 0.3, 0, 1)
    v = np.mgrid[0:h, 0:w][0] / (h - 1)
    v = 1 - v  # image row 0 = top of the curtain
    body = smoothstep(0.0, 0.06, v) * (1 - v) ** 1.25
    slow = fbm(w, h, beta=3.0, aniso=(0.5, 2.0), seed=97)
    a = np.clip(body * (0.45 + 0.75 * ray[None, :]) * (0.7 + 0.5 * slow), 0, 1)
    rgba = np.zeros((h, w, 4))
    rgba[..., :3] = 255
    rgba[..., 3] = a * 255
    img = to_img(rgba).filter(ImageFilter.GaussianBlur(0.8))
    out("entity/aurora.png", img)


# ----------------------------------------------------------------------------- icons (GUI, 32x32)

def icon_canvas(bg1, bg2):
    s = 64
    y = np.mgrid[0:s, 0:s][0] / (s - 1)
    col = lerp_color([(0, bg1), (1, bg2)], y)
    a = np.full((s, s), 255.0)
    return col, a


def finish_icon(name, col, a, ring=EMBER):
    s = col.shape[0]
    rgba = np.dstack([col, a])
    img = to_img(rgba)
    mask = Image.new("L", (s, s), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, s - 1, s - 1], radius=12, fill=255)
    img.putalpha(Image.fromarray(np.minimum(np.array(img.split()[3]), np.array(mask))))
    dr = ImageDraw.Draw(img)
    dr.rounded_rectangle([0, 0, s - 1, s - 1], radius=12, outline=ring + (255,), width=2)
    out(f"gui/icon/{name}.png", img)


def draw_layer(size, fn):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    fn(ImageDraw.Draw(img))
    return np.array(img).astype(float)


def blend(col, layer):
    a = layer[..., 3:4] / 255
    return col * (1 - a) + layer[..., :3] * a


def icon_tornado():
    col, a = icon_canvas((70, 70, 85), (150, 130, 105))
    def f(dr):
        for i in range(9):
            t = i / 8
            w = 26 - 22 * t
            cx = 32 + math.sin(t * 3.2) * 6
            yy = 10 + t * 44
            dr.ellipse([cx - w, yy - 4, cx + w, yy + 4], fill=(60, 55, 60, 255) if i % 2 else (95, 88, 90, 255))
        dr.ellipse([12, 50, 52, 60], fill=(140, 110, 80, 200))
    col = blend(col, draw_layer(64, f))
    finish_icon("tornado", col, a)


def icon_volcano():
    col, a = icon_canvas((60, 20, 15), (255, 120, 40))
    def f(dr):
        dr.polygon([(4, 62), (24, 22), (40, 22), (60, 62)], fill=(40, 28, 26, 255))
        dr.polygon([(26, 24), (38, 24), (34, 40), (36, 62), (28, 62), (30, 40)], fill=(255, 110, 20, 255))
        for k in range(6):
            dr.ellipse([20 + k * 4 - 6, 4 + (k % 3) * 3, 20 + k * 4 + 6, 16 + (k % 3) * 3], fill=(50, 45, 48, 230))
        dr.ellipse([29, 14, 35, 20], fill=(255, 220, 90, 255))
        dr.ellipse([14, 10, 18, 14], fill=(255, 170, 40, 255))
        dr.ellipse([44, 8, 48, 12], fill=(255, 170, 40, 255))
    col = blend(col, draw_layer(64, f))
    finish_icon("volcano", col, a)


def icon_tsunami():
    col, a = icon_canvas((120, 190, 230), (20, 60, 120))
    def f(dr):
        dr.pieslice([0, 4, 60, 64], 180, 300, fill=(25, 100, 170, 255))
        dr.rectangle([0, 34, 64, 64], fill=(20, 80, 150, 255))
        dr.arc([2, 6, 58, 62], 190, 300, fill=(245, 252, 255, 255), width=5)
        for k in range(5):
            dr.ellipse([40 + k * 3, 6 + k * 2, 46 + k * 3, 12 + k * 2], fill=(250, 255, 255, 220))
    col = blend(col, draw_layer(64, f))
    finish_icon("tsunami", col, a)


def icon_earthquake():
    col, a = icon_canvas((110, 85, 65), (50, 38, 32))
    def f(dr):
        pts = [(30, 0), (36, 14), (26, 26), (38, 38), (28, 50), (34, 64)]
        dr.line(pts, fill=(255, 110, 25, 255), width=7, joint="curve")
        dr.line(pts, fill=(255, 230, 140, 255), width=2, joint="curve")
        dr.line([(36, 14), (52, 18), (62, 12)], fill=(255, 110, 25, 255), width=4)
        dr.line([(26, 26), (12, 32), (2, 28)], fill=(255, 110, 25, 255), width=4)
    col = blend(col, draw_layer(64, f))
    finish_icon("earthquake", col, a)


def icon_meteor():
    col, a = icon_canvas((20, 60, 55), (90, 200, 160))
    def f(dr):
        for k in range(12):
            t = k / 11
            r = 3 + 9 * t
            x = 8 + t * 34
            y = 6 + t * 34
            c = (255, int(240 - 140 * t), int(160 - 140 * t), int(80 + 175 * t))
            dr.ellipse([x - r, y - r, x + r, y + r], fill=c)
        dr.ellipse([36, 36, 54, 54], fill=(60, 40, 35, 255), outline=(255, 140, 40, 255), width=2)
    col = blend(col, draw_layer(64, f))
    finish_icon("meteor", col, a)


def icon_storm():
    col, a = icon_canvas((20, 10, 35), (80, 40, 130))
    def f(dr):
        for k in range(5):
            dr.ellipse([k * 13 - 6, 2, k * 13 + 20, 22], fill=(40, 30, 55, 255))
        dr.polygon([(34, 18), (22, 38), (32, 38), (24, 62), (44, 32), (34, 32), (42, 18)], fill=(230, 220, 255, 255))
        dr.line([(30, 30), (16, 44)], fill=(200, 180, 255, 200), width=2)
    col = blend(col, draw_layer(64, f))
    finish_icon("storm", col, a, ring=VIOLET)


def icon_blizzard():
    col, a = icon_canvas((235, 245, 255), (140, 180, 215))
    def f(dr):
        cx, cy = 32, 32
        for k in range(6):
            ang = k * math.pi / 3
            x2, y2 = cx + math.cos(ang) * 24, cy + math.sin(ang) * 24
            dr.line([(cx, cy), (x2, y2)], fill=(60, 120, 190, 255), width=4)
            for s in (0.55, 0.8):
                bx, by = cx + math.cos(ang) * 24 * s, cy + math.sin(ang) * 24 * s
                for sgn in (-1, 1):
                    a2 = ang + sgn * 0.7
                    dr.line([(bx, by), (bx + math.cos(a2) * 7, by + math.sin(a2) * 7)], fill=(60, 120, 190, 255), width=3)
        dr.ellipse([27, 27, 37, 37], fill=(127, 232, 255, 255))
    col = blend(col, draw_layer(64, f))
    finish_icon("blizzard", col, a, ring=CYAN)


def icon_sinkhole():
    col, a = icon_canvas((110, 140, 70), (90, 70, 50))
    def f(dr):
        for k in range(5):
            r = 28 - k * 5
            c = int(70 - k * 14)
            dr.ellipse([32 - r, 36 - r * 0.55, 32 + r, 36 + r * 0.55], fill=(c + 20, c, max(c - 10, 0), 255))
        dr.ellipse([24, 32, 40, 40], fill=(0, 0, 0, 255))
    col = blend(col, draw_layer(64, f))
    finish_icon("sinkhole", col, a)


def icon_horizon():
    s = 64
    y, x = np.mgrid[0:s, 0:s]
    dx, dy = (x - 31.5), (y - 31.5)
    col = np.zeros((s, s, 3))
    col[...] = (10, 5, 25)
    # accretion disk (ellipse ring)
    ell = np.sqrt((dx / 30) ** 2 + (dy / 9) ** 2)
    ringm = np.exp(-((ell - 0.75) ** 2) / 0.04)
    heat = np.clip(1 - np.abs(ell - 0.6) * 1.4, 0, 1)
    rc = lerp_color([(0, VIOLET), (0.5, EMBER), (0.8, GOLD), (1, (255, 255, 240))], heat)
    col = col * (1 - ringm[..., None]) + rc * ringm[..., None]
    # photon ring + hole
    d = np.sqrt(dx * dx + dy * dy)
    halo = np.exp(-((d - 12) ** 2) / 5)
    col = col + halo[..., None] * np.array([255, 220, 160]) * 0.9
    hole = d < 11
    front = (dy > 0) & (ell < 1.0) & (np.abs(dy) < 6)
    col = np.where((hole & ~front)[..., None], 0, col)
    # jets
    jet = np.exp(-(dx ** 2) / 3) * np.clip(np.abs(dy) / 32, 0, 1)
    col = col + jet[..., None] * np.array(CYAN) * 0.9
    finish_icon("horizon", col, np.full((s, s), 255.0), ring=VIOLET)


def icon_random():
    col, a = icon_canvas((60, 50, 60), (25, 20, 28))
    def f(dr):
        dr.rounded_rectangle([12, 12, 52, 52], radius=8, fill=(245, 240, 232, 255), outline=(255, 90, 31, 255), width=3)
        for (px, py) in [(22, 22), (42, 22), (32, 32), (22, 42), (42, 42)]:
            dr.ellipse([px - 4, py - 4, px + 4, py + 4], fill=(27, 26, 31, 255))
    col = blend(col, draw_layer(64, f))
    finish_icon("random", col, a)


def icon_stop():
    col, a = icon_canvas((120, 20, 20), (60, 10, 10))
    def f(dr):
        pts = [(32 + math.cos(k * math.pi / 4 + math.pi / 8) * 26, 32 + math.sin(k * math.pi / 4 + math.pi / 8) * 26) for k in range(8)]
        dr.polygon(pts, fill=(220, 30, 30, 255), outline=(255, 255, 255, 255))
        dr.rectangle([18, 28, 46, 36], fill=(255, 255, 255, 255))
    col = blend(col, draw_layer(64, f))
    finish_icon("stop", col, a, ring=(255, 60, 60))


def icon_camera():
    col, a = icon_canvas((40, 40, 50), (15, 15, 20))
    def f(dr):
        dr.rounded_rectangle([8, 20, 44, 46], radius=5, fill=(200, 200, 210, 255))
        dr.polygon([(44, 28), (58, 20), (58, 46), (44, 38)], fill=(200, 200, 210, 255))
        dr.ellipse([12, 8, 26, 22], outline=(255, 90, 31, 255), width=3)
        dr.ellipse([26, 8, 40, 22], outline=(255, 90, 31, 255), width=3)
        dr.ellipse([14, 26, 22, 34], fill=(255, 40, 40, 255))
    col = blend(col, draw_layer(64, f))
    finish_icon("camera", col, a)


# ----------------------------------------------------------------------------- items (16x16)

def item_control_panel():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    dr = ImageDraw.Draw(img)
    dr.rectangle([1, 2, 14, 13], fill=(38, 36, 44, 255), outline=(14, 12, 16, 255))
    dr.rectangle([2, 3, 13, 4], fill=(255, 90, 31, 255))
    for i in range(2, 14, 2):
        dr.point((i, 3), fill=(20, 18, 22, 255))
    dr.rectangle([3, 6, 8, 11], fill=(10, 40, 30, 255), outline=(60, 200, 150, 255))
    dr.point((5, 8), fill=(255, 220, 90, 255))
    dr.point((6, 9), fill=(255, 90, 31, 255))
    dr.rectangle([10, 6, 12, 7], fill=(220, 40, 40, 255))
    dr.rectangle([10, 9, 12, 10], fill=(127, 232, 255, 255))
    dr.line([(3, 12), (12, 12)], fill=(90, 86, 96, 255))
    out("item/control_panel.png", img)


def item_detector():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    dr = ImageDraw.Draw(img)
    dr.line([(11, 0), (11, 4)], fill=(150, 150, 160, 255))
    dr.point((11, 0), fill=(255, 60, 40, 255))
    dr.rounded_rectangle([3, 4, 12, 15], radius=2, fill=(240, 180, 40, 255), outline=(120, 70, 10, 255))
    dr.rectangle([5, 6, 10, 10], fill=(15, 30, 25, 255), outline=(60, 50, 20, 255))
    dr.line([(5, 9), (6, 8), (7, 9), (8, 7), (9, 9), (10, 8)], fill=(80, 255, 160, 255))
    dr.point((6, 12), fill=(255, 60, 40, 255))
    dr.point((9, 12), fill=(40, 40, 40, 255))
    dr.point((9, 13), fill=(40, 40, 40, 255))
    out("item/disaster_detector.png", img)


def logo():
    w, h = 256, 128
    col = lerp_color([(0, (40, 12, 8)), (0.6, (120, 30, 10)), (1, (255, 120, 40))], np.mgrid[0:h, 0:w][0] / (h - 1))
    n = fbm(w, h, beta=2.2, seed=777)
    col = col * (0.75 + 0.35 * n[..., None])
    img = to_img(np.dstack([col, np.full((h, w), 255)]))
    dr = ImageDraw.Draw(img)
    dr.polygon([(0, 128), (70, 60), (95, 70), (128, 30), (160, 72), (190, 58), (256, 128)], fill=(20, 16, 18, 255))
    dr.polygon([(122, 36), (134, 36), (140, 128), (116, 128)], fill=(255, 110, 20, 255))
    img = img.filter(ImageFilter.SMOOTH)
    out("../terrawrath_logo_tmp.png", img)


ALL = [
    particle_glow, particle_embers, particle_sparks, particle_smoke, particle_dust, particle_ash, particle_rain,
    particle_snow, particle_spray, particle_flame, particle_shard,
    tex_funnel, tex_cloud, tex_swirl, tex_water, tex_foam, tex_disk, tex_rock, tex_lava, tex_snow_wall, tex_white,
    tex_ring, tex_beam,
    overlay_frost, overlay_vignette, overlay_haze, overlay_hazard, overlay_hazard_red, overlay_iris, overlay_edges,
    tex_accretion, tex_aurora, overlay_ripple,
    icon_tornado, icon_volcano, icon_tsunami, icon_earthquake, icon_meteor, icon_storm, icon_blizzard, icon_sinkhole,
    icon_horizon, icon_random, icon_stop, icon_camera,
    item_control_panel, item_detector, logo,
]

if __name__ == "__main__":
    only = set(sys.argv[1:])
    for fn in ALL:
        if not only or fn.__name__ in only:
            fn()
    # mod logo lives at the jar root
    logo_src = os.path.join(ROOT, "..", "terrawrath_logo_tmp.png")
    if os.path.exists(logo_src):
        dst = os.path.join(ROOT, "..", "..", "..", "terrawrath_logo.png")
        os.replace(logo_src, dst)
    print("textures written to", os.path.abspath(ROOT))
