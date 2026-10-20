"""TerraWrath procedural sound designer.

Synthesises every sound effect (mono OGG Vorbis) with numpy + soundfile.
Loops are built in the frequency domain so they wrap seamlessly.
Run:  python tools/gen_sounds.py
"""
import os
import sys

import numpy as np
import soundfile as sf

SR = 44100
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "terrawrath", "sounds")
rng = np.random.default_rng(4242)


def t_axis(dur):
    return np.arange(int(dur * SR)) / SR


def spectral_noise(dur, lo=20, hi=20000, slope=0.0, seed=None):
    """Band-limited coloured noise (circular, so it loops). slope: dB/oct tilt (-3 pink, -6 brown)."""
    r = np.random.default_rng(seed) if seed is not None else rng
    n = int(dur * SR)
    spec = np.fft.rfft(r.standard_normal(n))
    f = np.fft.rfftfreq(n, 1 / SR)
    f[0] = 1
    gain = (f / 1000.0) ** (slope / 6.02)
    lo_w = 1 / (1 + (lo / f) ** 4)
    hi_w = 1 / (1 + (f / hi) ** 4)
    spec *= gain * lo_w * hi_w
    spec[0] = 0
    x = np.fft.irfft(spec, n)
    return x / (np.max(np.abs(x)) + 1e-9)


def band(x, lo, hi):
    n = len(x)
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1 / SR)
    f[0] = 1
    spec *= (1 / (1 + (lo / f) ** 4)) * (1 / (1 + (f / hi) ** 4))
    return np.fft.irfft(spec, n)


def lfo(dur, cycles, phase=0.0):
    """Sine LFO with an integer number of cycles over dur (loop-safe), range 0..1."""
    t = t_axis(dur)
    return 0.5 + 0.5 * np.sin(2 * np.pi * cycles * t / dur + phase)


def env_adsr(n, a, d, s_level, r):
    e = np.ones(n) * s_level
    ia, idd, ir = int(a * SR), int(d * SR), int(r * SR)
    ia = max(ia, 1)
    e[:ia] = np.linspace(0, 1, ia)
    e[ia:ia + idd] = np.linspace(1, s_level, len(e[ia:ia + idd]))
    if ir > 0:
        e[-ir:] = e[-ir:] * np.linspace(1, 0, ir) ** 2
    return e


def exp_decay(n, tau, delay=0.0):
    t = np.arange(n) / SR - delay
    e = np.exp(-np.maximum(t, 0) / tau)
    e[t < 0] = 0
    return e


def reverb(x, size=2.0, wet=0.35, damp=4000, seed=7):
    n_ir = int(size * SR)
    ir = spectral_noise(size, 80, damp, -1.5, seed=seed) * np.exp(-np.arange(n_ir) / SR / (size / 5))
    ir /= np.sqrt(np.sum(ir ** 2))
    n = len(x) + n_ir
    y = np.fft.irfft(np.fft.rfft(x, n) * np.fft.rfft(ir, n), n)[: len(x)]
    y /= np.max(np.abs(y)) + 1e-9
    return (1 - wet) * x / (np.max(np.abs(x)) + 1e-9) + wet * y


def circ_reverb(x, size=1.5, wet=0.3, damp=4000, seed=7):
    """Reverb with circular convolution, for loops."""
    n = len(x)
    n_ir = int(size * SR)
    ir = spectral_noise(size, 80, damp, -1.5, seed=seed) * np.exp(-np.arange(n_ir) / SR / (size / 5))
    ir = np.pad(ir, (0, n - n_ir)) if n_ir < n else ir[:n]
    y = np.fft.irfft(np.fft.rfft(x) * np.fft.rfft(ir), n)
    y /= np.max(np.abs(y)) + 1e-9
    return (1 - wet) * x / (np.max(np.abs(x)) + 1e-9) + wet * y


def sat(x, drive=2.0):
    return np.tanh(x * drive) / np.tanh(drive)


def fade(x, fin=0.01, fout=0.05):
    a, b = int(fin * SR), int(fout * SR)
    if a:
        x[:a] *= np.linspace(0, 1, a)
    if b:
        x[-b:] *= np.linspace(1, 0, b)
    return x


def norm(x, peak=0.89):
    return x / (np.max(np.abs(x)) + 1e-9) * peak


def sweep_sine(freqs, harmonics=((1, 1.0),)):
    """freqs: per-sample instantaneous frequency array."""
    ph = 2 * np.pi * np.cumsum(freqs) / SR
    return sum(a * np.sin(ph * h) for h, a in harmonics)


def write(name, x, loop=False):
    os.makedirs(OUT, exist_ok=True)
    x = norm(x)
    if not loop:
        x = fade(x, 0.002, 0.08)
    sf.write(os.path.join(OUT, name + ".ogg"), x.astype(np.float32), SR, format="OGG", subtype="VORBIS")


# ----------------------------------------------------------------------------- loops

def wind_loop():
    d = 8.0
    roar = spectral_noise(d, 40, 900, -4.5, seed=1) * (0.55 + 0.45 * lfo(d, 3))
    howls = 0
    for i, (c, cyc) in enumerate([(260, 2), (380, 3), (520, 5), (700, 4)]):
        nb = band(spectral_noise(d, 20, 20000, 0, seed=10 + i), c * 0.85, c * 1.15)
        howls = howls + nb / (np.max(np.abs(nb)) + 1e-9) * lfo(d, cyc, i * 1.3) ** 2
    sub = band(spectral_noise(d, 15, 90, -6, seed=3), 20, 70)
    hiss = spectral_noise(d, 1500, 9000, -2, seed=4) * (0.4 + 0.6 * lfo(d, 5, 2.0))
    x = roar * 1.0 + howls * 0.32 + norm(sub) * 0.7 + hiss * 0.12
    write("wind_loop", sat(norm(circ_reverb(x, 1.2, 0.2)), 1.6), loop=True)


def rumble_loop():
    d = 8.0
    deep = spectral_noise(d, 18, 140, -6, seed=21)
    t = t_axis(d)
    sub = np.sin(2 * np.pi * 32 * t) * (0.5 + 0.5 * lfo(d, 4))
    grit = band(spectral_noise(d, 20, 20000, 0, seed=22), 150, 600) * lfo(d, 6, 1.0) ** 3
    x = deep * 1.0 + sub * 0.55 + norm(grit) * 0.18
    write("rumble_loop", sat(norm(x) * (0.75 + 0.25 * lfo(d, 2)), 2.0), loop=True)


def blizzard_loop():
    d = 8.0
    x = spectral_noise(d, 300, 6000, -2.5, seed=31) * (0.5 + 0.5 * lfo(d, 3, 0.4))
    howl = 0
    for i, (c, cyc) in enumerate([(620, 2), (880, 3), (1150, 5), (1500, 4), (420, 6)]):
        nb = band(spectral_noise(d, 20, 20000, 0, seed=40 + i), c * 0.93, c * 1.07)
        howl = howl + nb / (np.max(np.abs(nb)) + 1e-9) * lfo(d, cyc, i * 0.9) ** 3
    low = spectral_noise(d, 40, 300, -5, seed=33) * 0.5
    write("blizzard_loop", circ_reverb(x + howl * 0.4 + low, 1.6, 0.3), loop=True)


def storm_loop():
    d = 8.0
    rain = spectral_noise(d, 900, 14000, -1.0, seed=51)
    drops = np.zeros(int(d * SR))
    idx = rng.integers(0, len(drops), 2200)
    drops[idx] = rng.uniform(-1, 1, len(idx))
    drops = band(drops, 1500, 9000)
    gale = spectral_noise(d, 50, 1100, -4.5, seed=52) * (0.45 + 0.55 * lfo(d, 2, 0.5))
    x = norm(rain) * 0.45 + norm(drops) * 0.3 + norm(gale) * 0.9
    write("storm_loop", circ_reverb(x, 1.2, 0.2), loop=True)


def wave_roar_loop():
    d = 8.0
    x = spectral_noise(d, 30, 2200, -3.5, seed=61) * (0.55 + 0.45 * lfo(d, 2, 1.0))
    surf = spectral_noise(d, 1200, 9000, -2, seed=62) * lfo(d, 4, 0.2) ** 2
    sub = spectral_noise(d, 18, 80, -6, seed=63)
    write("wave_roar_loop", circ_reverb(norm(x) + norm(surf) * 0.3 + norm(sub) * 0.6, 1.5, 0.25), loop=True)


def lava_loop():
    d = 6.0
    n = int(d * SR)
    x = spectral_noise(d, 25, 400, -5, seed=71) * 0.5
    for _ in range(60):
        start = rng.integers(0, n - 8000)
        L = rng.integers(1500, 6000)
        tt = np.arange(L) / SR
        f0 = rng.uniform(60, 180)
        blub = np.sin(2 * np.pi * (f0 * tt + f0 * 3 * tt * tt)) * np.exp(-tt * rng.uniform(25, 60))
        x[start:start + L] += blub * rng.uniform(0.3, 1.0)
    crack = np.zeros(n)
    idx = rng.integers(0, n, 400)
    crack[idx] = rng.uniform(-1, 1, len(idx))
    x += norm(band(crack, 1500, 7000)) * 0.25
    write("lava_loop", circ_reverb(x, 0.8, 0.2), loop=True)


def hole_loop():
    d = 8.0
    t = t_axis(d)
    sub = np.sin(2 * np.pi * 36 * t) * 0.6 + np.sin(2 * np.pi * 36.5 * t) * 0.6
    choir = 0
    for i, f in enumerate([110, 130.8, 164.8, 220, 246.9, 329.6]):
        for det in (-0.6, 0.6):
            choir = choir + np.sin(2 * np.pi * (f + det) * t + i) * (0.6 + 0.4 * lfo(d, i + 1, i))
    cosmic = band(spectral_noise(d, 20, 20000, 0, seed=81), 600, 2400) * lfo(d, 3) ** 4
    grav = spectral_noise(d, 15, 60, -6, seed=82)
    x = sub * 0.9 + norm(choir) * 0.35 + norm(cosmic) * 0.25 + norm(grav) * 0.6
    write("hole_loop", circ_reverb(sat(norm(x), 1.5), 2.0, 0.35), loop=True)


# ----------------------------------------------------------------------------- one shots

def siren():
    d = 9.0
    t = t_axis(d)
    f = np.interp(t, [0, 3.2, 5.2, 9.0], [160, 620, 620, 140]) * (1 + 0.006 * np.sin(2 * np.pi * 5.5 * t))
    x = sweep_sine(f, ((1, 1.0), (2, 0.45), (3, 0.3), (4, 0.15), (5, 0.08)))
    x = sat(x * 0.7, 2.2) * env_adsr(len(t), 0.4, 0.1, 1.0, 1.6)
    x = band(x, 120, 4000)
    write("siren", reverb(x, 2.5, 0.45, 3000))


def warning():
    """Emergency-broadcast alert (original synthesis): three AFSK data bursts, then the two-tone
    853 + 960 Hz attention signal, band-limited and lightly overdriven like a TV/radio broadcast."""
    rng = np.random.default_rng(2026)
    baud = 520.83
    mark, space = 2083.3, 1562.5
    parts = []

    def burst(n_bits):
        bits = rng.integers(0, 2, n_bits)
        spb = SR / baud
        n = int(n_bits * spb)
        idx = np.minimum((np.arange(n) / spb).astype(int), n_bits - 1)
        f = np.where(bits[idx] == 1, mark, space)
        ph = 2 * np.pi * np.cumsum(f) / SR
        return np.sin(ph) * 0.55

    gap = np.zeros(int(0.32 * SR))
    for _ in range(3):
        parts.append(burst(330))
        parts.append(gap)
    parts.append(np.zeros(int(0.25 * SR)))
    d = 4.4
    t = t_axis(d)
    tone = np.sin(2 * np.pi * 853 * t) + np.sin(2 * np.pi * 960 * t)
    tone = tone * env_adsr(len(t), 0.01, 0.05, 1.0, 0.35) * 0.5
    parts.append(tone)
    x = np.concatenate(parts)
    x = sat(x * 1.4, 1.6)
    x = band(x, 280, 3600)
    hiss = band(spectral_noise(len(x) / SR, 20, 20000, 0, seed=203), 300, 4000) * 0.012
    x = x + hiss
    write("warning", circ_reverb(x, 0.6, 0.12, 3500))


def thunder(i):
    d = 7.0
    n = int(d * SR)
    crack = spectral_noise(d, 600, 5000, -2, seed=90 + i) * exp_decay(n, 0.05 + 0.02 * i)
    rumble = spectral_noise(d, 22, 420, -6, seed=95 + i)
    bumps = np.ones(n) * 0.3
    for _ in range(9):
        c = rng.uniform(0.1, 3.5)
        bumps += np.exp(-((np.arange(n) / SR - c) ** 2) / 0.06) * rng.uniform(0.4, 1.0)
    rumble *= bumps * exp_decay(n, 1.8, 0.02)
    snap = np.zeros(n)
    for k in range(6):
        p = int(rng.uniform(0, 0.12) * SR)
        snap[p:p + 300] += rng.uniform(-1, 1, 300) * np.exp(-np.arange(300) / 40)
    x = norm(crack) * 0.45 + norm(rumble) * 1.25 + norm(band(snap, 300, 6000)) * 0.35
    write(f"thunder_{i}", reverb(sat(x, 2.2), 3.0, 0.35, 2200))


def meteor_whistle():
    d = 4.5
    t = t_axis(d)
    f = np.interp(t, [0, 4.5], [2400, 260]) ** 1.0
    tone = sweep_sine(f, ((1, 1.0), (2, 0.2)))
    whoosh = spectral_noise(d, 200, 7000, -2, seed=111)
    env = np.clip(t / 4.5, 0, 1) ** 1.6
    rumb = spectral_noise(d, 20, 200, -6, seed=112) * env ** 2
    x = (tone * 0.35 + whoosh * 0.6) * (0.15 + env) + rumb * 0.8
    write("meteor_whistle", reverb(sat(x, 1.5), 1.5, 0.25))


def impact(i):
    d = 6.0
    n = int(d * SR)
    t = t_axis(d)
    trans = spectral_noise(d, 40, 4500, -3, seed=120 + i) * exp_decay(n, 0.12)
    boom = np.sin(2 * np.pi * (70 * t - 6 * t * t)) * exp_decay(n, 0.7)
    boom += np.sin(2 * np.pi * (38 * t)) * exp_decay(n, 1.4) * 0.8
    debris = spectral_noise(d, 40, 3000, -3, seed=125 + i) * exp_decay(n, 1.6, 0.1)
    x = norm(trans) * 0.6 + norm(boom) * 1.5 + norm(band(debris, 30, 1800)) * 0.6
    write(f"impact_{i}", reverb(sat(x, 3.0), 3.5, 0.35, 1800))


def eruption():
    d = 7.0
    n = int(d * SR)
    t = t_axis(d)
    boom = np.sin(2 * np.pi * (55 * t - 5 * t * t)) * exp_decay(n, 1.1)
    roar = spectral_noise(d, 30, 2500, -3.5, seed=131) * env_adsr(n, 0.05, 1.0, 0.6, 3.0)
    crackle = np.zeros(n)
    idx = rng.integers(0, n, 900)
    crackle[idx] = rng.uniform(-1, 1, len(idx))
    crackle = band(crackle, 1200, 8000) * exp_decay(n, 2.5)
    x = norm(boom) * 1.2 + norm(roar) * 0.9 + norm(crackle) * 0.4
    write("eruption", reverb(sat(x, 2.5), 3.0, 0.35, 3000))


def wave_crash():
    d = 7.0
    n = int(d * SR)
    body = spectral_noise(d, 25, 2500, -3.5, seed=141) * env_adsr(n, 0.35, 0.8, 0.5, 4.0)
    hiss = spectral_noise(d, 1500, 7000, -2, seed=142) * env_adsr(n, 0.6, 1.5, 0.4, 3.5)
    thud = np.sin(2 * np.pi * 40 * t_axis(d)) * exp_decay(n, 0.8, 0.3)
    x = norm(body) + norm(hiss) * 0.2 + norm(thud) * 0.9
    write("wave_crash", reverb(sat(x, 1.8), 2.5, 0.3))


def crack(i):
    d = 2.5
    n = int(d * SR)
    x = np.zeros(n)
    pos = 0
    for k in range(rng.integers(6, 12)):
        pos += int(rng.uniform(0.01, 0.12) * SR)
        if pos > n - 3000:
            break
        L = 2500
        burst = rng.standard_normal(L) * np.exp(-np.arange(L) / rng.uniform(80, 300))
        x[pos:pos + L] += burst * rng.uniform(0.5, 1.0)
    x = band(x, 200, 9000)
    thud = np.sin(2 * np.pi * (60 * t_axis(d))) * exp_decay(n, 0.35)
    rumble = spectral_noise(d, 25, 300, -5, seed=150 + i) * exp_decay(n, 0.9)
    write(f"crack_{i}", reverb(sat(norm(x) + norm(thud) * 0.8 + norm(rumble) * 0.6, 2.0), 1.5, 0.25))


def collapse():
    d = 5.0
    n = int(d * SR)
    x = spectral_noise(d, 25, 400, -5, seed=161) * env_adsr(n, 0.2, 1.0, 0.6, 2.5)
    for _ in range(80):
        p = rng.integers(0, n - 6000)
        L = rng.integers(800, 5000)
        b = rng.standard_normal(L) * np.exp(-np.arange(L) / (L / 5))
        x[p:p + L] += band(b, rng.uniform(100, 400), rng.uniform(1500, 6000)) * rng.uniform(0.2, 0.8)
    write("collapse", reverb(sat(x, 1.8), 2.0, 0.3))


def supernova():
    d = 9.0
    n = int(d * SR)
    t = t_axis(d)
    swell_end = 1.8
    sw = np.clip(t / swell_end, 0, 1) ** 3 * (t < swell_end)
    rise = spectral_noise(d, 100, 12000, -1, seed=171) * sw
    tone = sweep_sine(np.interp(t, [0, swell_end], [80, 900]), ((1, 1), (2, 0.5), (3, 0.25))) * sw
    tb = t - swell_end
    blast = spectral_noise(d, 20, 3500, -4, seed=172) * exp_decay(n, 1.6, swell_end)
    boom = np.sin(2 * np.pi * (60 * np.maximum(tb, 0) - 4 * np.maximum(tb, 0) ** 2)) * exp_decay(n, 2.0, swell_end)
    shimmer = 0
    for f in [523, 659, 784, 1046, 1318]:
        shimmer = shimmer + np.sin(2 * np.pi * f * t) * exp_decay(n, 3.0, swell_end)
    x = norm(rise) * 0.7 + norm(tone) * 0.3 + norm(blast) * 1.0 + norm(boom) * 1.2 + norm(shimmer) * 0.15
    write("supernova", reverb(sat(x, 2.5), 4.0, 0.4, 2500))


def shockwave():
    d = 3.5
    n = int(d * SR)
    t = t_axis(d)
    whoomp = np.sin(2 * np.pi * (90 * t - 12 * t * t)) * exp_decay(n, 0.6)
    air = spectral_noise(d, 80, 3000, -3, seed=181) * env_adsr(n, 0.02, 0.4, 0.3, 2.0)
    write("shockwave", reverb(sat(norm(whoomp) + norm(air) * 0.7, 2.0), 2.0, 0.3))


def ice_burst():
    d = 2.0
    n = int(d * SR)
    x = np.zeros(n)
    for _ in range(25):
        p = rng.integers(0, int(0.4 * SR))
        L = 1200
        f = rng.uniform(1500, 6000)
        tt = np.arange(L) / SR
        x[p:p + L] += np.sin(2 * np.pi * f * tt) * np.exp(-tt * 60) * rng.uniform(0.3, 1)
    x += band(spectral_noise(d, 20, 20000, 0, seed=191), 2000, 12000) * exp_decay(n, 0.08) * 0.5
    thud = np.sin(2 * np.pi * 70 * t_axis(d)) * exp_decay(n, 0.25)
    write("ice_burst", reverb(norm(x) + norm(thud) * 0.6, 1.5, 0.35, 8000))


def beep():
    d = 0.18
    t = t_axis(d)
    x = np.sign(np.sin(2 * np.pi * 1760 * t)) * 0.4 + np.sin(2 * np.pi * 1760 * t) * 0.6
    write("beep", band(x, 400, 8000) * env_adsr(len(t), 0.005, 0.02, 0.8, 0.04))


def ui_click():
    d = 0.12
    n = int(d * SR)
    t = t_axis(d)
    x = np.sin(2 * np.pi * 1200 * t) * np.exp(-t * 60) + spectral_noise(d, 2000, 10000, 0, seed=9) * np.exp(-t * 120) * 0.4
    write("ui_click", x)


def whoosh():
    d = 1.6
    n = int(d * SR)
    t = t_axis(d)
    env = np.sin(np.pi * np.clip(t / d, 0, 1)) ** 2
    x = band(spectral_noise(d, 20, 20000, -1, seed=201), 300, 3000) * env
    write("whoosh", x)


ALL = [wind_loop, rumble_loop, blizzard_loop, storm_loop, wave_roar_loop, lava_loop, hole_loop,
       siren, warning, meteor_whistle, eruption, wave_crash, collapse, supernova, shockwave, ice_burst, beep,
       ui_click, whoosh]

if __name__ == "__main__":
    only = set(sys.argv[1:])
    for fn in ALL:
        if not only or fn.__name__ in only:
            fn()
    if not only:
        for i in range(3):
            thunder(i)
        for i in range(2):
            impact(i)
            crack(i)
    print("sounds written to", os.path.abspath(OUT))
