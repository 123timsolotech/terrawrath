"""Writes TerraWrath's JSON assets: particle definitions, sounds.json, item models and recipes."""
import json
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
A = os.path.join(ROOT, "assets", "terrawrath")
D = os.path.join(ROOT, "data", "terrawrath")


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


# particles: name -> sprite list
PARTICLES = {
    "glow": ["glow"],
    "ember": [f"ember_{i}" for i in range(4)],
    "spark": [f"spark_{i}" for i in range(2)],
    "smoke": [f"smoke_{i}" for i in range(8)],
    "dust": [f"dust_{i}" for i in range(4)],
    "ash": [f"ash_{i}" for i in range(4)],
    "rain": ["rain"],
    "snow": [f"snow_{i}" for i in range(4)],
    "spray": [f"spray_{i}" for i in range(4)],
    "flame": [f"flame_{i}" for i in range(6)],
    "shard": [f"shard_{i}" for i in range(3)],
}
for name, sprites in PARTICLES.items():
    write(os.path.join(A, "particles", name + ".json"), {"textures": [f"terrawrath:{s}" for s in sprites]})

# sounds: event -> (files, attenuation distance, subtitle, stream)
SOUNDS = {
    "wind_loop": (["wind_loop"], 64, "subtitles.terrawrath.wind", True),
    "rumble_loop": (["rumble_loop"], 64, "subtitles.terrawrath.rumble", True),
    "blizzard_loop": (["blizzard_loop"], 64, "subtitles.terrawrath.blizzard", True),
    "storm_loop": (["storm_loop"], 64, "subtitles.terrawrath.storm", True),
    "wave_roar_loop": (["wave_roar_loop"], 64, "subtitles.terrawrath.wave", True),
    "lava_loop": (["lava_loop"], 64, "subtitles.terrawrath.lava", True),
    "hole_loop": (["hole_loop"], 64, "subtitles.terrawrath.hole", True),
    "siren": (["siren"], 48, "subtitles.terrawrath.siren", False),
    "warning": (["warning"], 48, "subtitles.terrawrath.warning", False),
    "meteor_whistle": (["meteor_whistle"], 140, "subtitles.terrawrath.meteor", False),
    "eruption": (["eruption"], 260, "subtitles.terrawrath.eruption", False),
    "wave_crash": (["wave_crash"], 220, "subtitles.terrawrath.wave_crash", False),
    "collapse": (["collapse"], 120, "subtitles.terrawrath.collapse", False),
    "supernova": (["supernova"], 400, "subtitles.terrawrath.supernova", False),
    "shockwave": (["shockwave"], 300, "subtitles.terrawrath.shockwave", False),
    "ice_burst": (["ice_burst"], 100, "subtitles.terrawrath.ice", False),
    "beep": (["beep"], 16, "subtitles.terrawrath.beep", False),
    "ui_click": (["ui_click"], 16, None, False),
    "whoosh": (["whoosh"], 32, "subtitles.terrawrath.whoosh", False),
    "thunder": (["thunder_0", "thunder_1", "thunder_2"], 260, "subtitles.terrawrath.thunder", False),
    "impact": (["impact_0", "impact_1"], 240, "subtitles.terrawrath.impact", False),
    "crack": (["crack_0", "crack_1"], 90, "subtitles.terrawrath.crack", False),
}
sounds_json = {}
for event, (files, dist, subtitle, stream) in SOUNDS.items():
    entry = {"sounds": [{"name": f"terrawrath:{f}", "attenuation_distance": dist, "stream": stream} for f in files]}
    if subtitle:
        entry["subtitle"] = subtitle
    sounds_json[event] = entry
write(os.path.join(A, "sounds.json"), sounds_json)

# items
for item in ["control_panel", "disaster_detector"]:
    write(os.path.join(A, "items", item + ".json"), {"model": {"type": "minecraft:model", "model": f"terrawrath:item/{item}"}})
    write(os.path.join(A, "models", "item", item + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"terrawrath:item/{item}"}})

# recipes
write(os.path.join(D, "recipe", "disaster_detector.json"), {
    "type": "minecraft:crafting_shaped",
    "category": "equipment",
    "key": {"I": "minecraft:iron_ingot", "R": "minecraft:redstone", "C": "minecraft:copper_ingot", "G": "minecraft:glass_pane"},
    "pattern": [" C ", "IGI", "IRI"],
    "result": {"id": "terrawrath:disaster_detector"},
})
write(os.path.join(D, "recipe", "control_panel.json"), {
    "type": "minecraft:crafting_shaped",
    "category": "equipment",
    "key": {"D": "terrawrath:disaster_detector", "O": "minecraft:obsidian", "E": "minecraft:ender_eye", "R": "minecraft:redstone_block"},
    "pattern": ["OEO", "RDR", "OOO"],
    "result": {"id": "terrawrath:control_panel"},
})
print("json assets written")
