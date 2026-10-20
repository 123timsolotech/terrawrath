"""Dev tool: launches vanilla Minecraft 26.3 under Fabric Loader 0.19.5 with the TerraWrath jar, to verify the
Fabric side of the single jar works (the user's CurseForge instance runs Fabric).

usage: python tools/fabric_launch.py <autotest-list> [--fabric-api]
"""
import json
import os
import shutil
import subprocess
import sys
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
PROJ = os.path.abspath(os.path.join(HERE, ".."))
GH = os.path.join(PROJ, ".gradle-home")
TASKS = os.path.join(GH, "caches", "minecraftforge", "forgegradle", "mavenizer", "caches", "minecraft_tasks", "26.3")
FAB = os.path.join(PROJ, "dl", "fabric")
LIBDIR = os.path.join(FAB, "libraries")
GAME = os.path.join(FAB, "game")
JAVA = os.path.join(PROJ, "jdk", "jdk-25.0.4.1+1", "bin", "java.exe")
ASSETS = os.path.join(os.environ["APPDATA"], ".minecraft", "assets")

FABRIC_LIBS = [
    "org.ow2.asm:asm:9.10.1", "org.ow2.asm:asm-analysis:9.10.1", "org.ow2.asm:asm-commons:9.10.1",
    "org.ow2.asm:asm-tree:9.10.1", "org.ow2.asm:asm-util:9.10.1",
    "net.fabricmc:sponge-mixin:0.17.4+mixin.0.8.7", "net.fabricmc:fabric-loader:0.19.5",
]


def maven_path(coord):
    g, a, v = coord.split(":")[:3]
    return f"{g.replace('.', '/')}/{a}/{v}/{a}-{v}.jar"


def fetch(url, dest):
    if os.path.exists(dest) and os.path.getsize(dest) > 0:
        return dest
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    print("download", url)
    with urllib.request.urlopen(url) as r, open(dest + ".part", "wb") as f:
        shutil.copyfileobj(r, f)
    os.replace(dest + ".part", dest)
    return dest


def find_in_gradle(filename):
    root = os.path.join(GH, "caches", "modules-2", "files-2.1")
    for dirpath, _, files in os.walk(root):
        if filename in files:
            return os.path.join(dirpath, filename)
    return None


def allowed(lib):
    rules = lib.get("rules")
    if not rules:
        return True
    ok = False
    for r in rules:
        os_name = r.get("os", {}).get("name")
        if os_name in (None, "windows"):
            ok = r["action"] == "allow"
    return ok


def main():
    autotest = sys.argv[1] if len(sys.argv) > 1 else "tornado"
    with_api = "--fabric-api" in sys.argv
    version = json.load(open(os.path.join(TASKS, "version.json"), encoding="utf-8"))
    cp = []
    for lib in version["libraries"]:
        if not allowed(lib):
            continue
        art = lib.get("downloads", {}).get("artifact")
        if not art:
            continue
        name = os.path.basename(art["path"])
        local = find_in_gradle(name) or fetch(art["url"], os.path.join(LIBDIR, art["path"]))
        cp.append(local)
    for coord in FABRIC_LIBS:
        cp.append(fetch("https://maven.fabricmc.net/" + maven_path(coord), os.path.join(LIBDIR, maven_path(coord))))
    cp.append(os.path.join(TASKS, "client.jar"))
    mods = os.path.join(GAME, "mods")
    os.makedirs(mods, exist_ok=True)
    for f in os.listdir(mods):
        os.remove(os.path.join(mods, f))
    shutil.copy(os.path.join(PROJ, "build", "libs", "terrawrath-1.1.1.jar"), mods)
    if with_api:
        api = fetch("https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.164.0+26.3/fabric-api-0.164.0+26.3.jar",
                    os.path.join(LIBDIR, "fabric-api-0.164.0+26.3.jar"))
        shutil.copy(api, mods)
    # --shaders: Sodium + Iris + a shader pack from dl/shaders (downloaded from Modrinth) to test shader support
    shader_dir = os.path.join(PROJ, "dl", "shaders")
    packs = os.path.join(GAME, "shaderpacks")
    if "--shaders" in sys.argv:
        os.makedirs(packs, exist_ok=True)
        pack = None
        for f in os.listdir(shader_dir):
            if f.endswith(".jar"):
                shutil.copy(os.path.join(shader_dir, f), mods)
            elif f.endswith(".zip"):
                shutil.copy(os.path.join(shader_dir, f), packs)
                pack = f
        os.makedirs(os.path.join(GAME, "config"), exist_ok=True)
        with open(os.path.join(GAME, "config", "iris.properties"), "w", encoding="utf-8") as fh:
            fh.write(f"enableShaders=true\nshaderPack={pack}\n")
    else:
        with open(os.path.join(GAME, "config", "iris.properties"), "w", encoding="utf-8") if os.path.isdir(os.path.join(GAME, "config")) else open(os.devnull, "w") as fh:
            fh.write("enableShaders=false\n")
    opts = os.path.join(GAME, "options.txt")
    if not os.path.exists(opts):
        shutil.copy(os.path.join(PROJ, "run", "options.txt"), opts)
    args = [JAVA, "-Xmx4G", "-DFabricMcEmu= net.minecraft.client.main.Main ",
            f"-Dterrawrath.autotest={autotest}", "-Dterrawrath.debug=true", "-cp", os.pathsep.join(cp),
            "net.fabricmc.loader.impl.launch.knot.KnotClient",
            "--username", "Dev", "--version", "26.3", "--gameDir", GAME, "--assetsDir", ASSETS,
            "--assetIndex", version["assetIndex"]["id"], "--accessToken", "0", "--uuid", "00000000-0000-0000-0000-000000000000",
            "--width", "1280", "--height", "720"]
    print("launching with", len(cp), "classpath entries")
    sys.exit(subprocess.call(args, cwd=GAME))


if __name__ == "__main__":
    main()
