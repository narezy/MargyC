#!/usr/bin/env python3
"""Build a MargyC mod (.mcmod) from a folder.

    python3 tools/build_mod.py examples/hello            # -> hello.mcmod
    python3 tools/build_mod.py my-mod -o my-mod.mcmod

The folder holds manifest.json, src/**/*.java, an optional icon.png and optional libs/*.jar
(bundled into the dex). Needs Python 3.8+ and JDK 17+; d8 and android.jar are downloaded to
~/.cache/claude-mods on first run. See docs/plugins.md.
"""
import argparse
import json
import pathlib
import re
import shutil
import sys
import tempfile
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
import patcher  # noqa: E402  (tool downloads and running commands)

API_SOURCES = ROOT / "src" / "cat" / "narezany" / "mods" / "api"


def tools():
    d = patcher.tools_dir()
    if not (d / "r8.jar").exists():
        patcher.download(patcher.TOOLS["r8.jar"], d / "r8.jar")
    if not (d / "android.jar").exists():
        z = d / "platform.zip"
        patcher.download(patcher.PLATFORM_ZIP, z)
        with zipfile.ZipFile(z) as zf:
            entry = next(n for n in zf.namelist() if n.endswith("/android.jar"))
            (d / "android.jar").write_bytes(zf.read(entry))
        z.unlink()
    for exe in ("java", "javac"):
        if not shutil.which(exe):
            raise patcher.PatchError(f"{exe} not found, JDK 17+ is needed")
    return d


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("folder", help="mod folder with manifest.json and src/")
    ap.add_argument("-o", "--output", help="output .mcmod (default: <id>.mcmod)")
    a = ap.parse_args()

    folder = pathlib.Path(a.folder)
    try:
        manifest = json.loads((folder / "manifest.json").read_text(encoding="utf-8"))
        mod_id = manifest.get("id", "")
        if not re.fullmatch(r"[a-z0-9][a-z0-9._-]{1,63}", mod_id):
            raise patcher.PatchError("manifest.json: id must be lowercase letters, digits, . _ -")
        if not manifest.get("entry"):
            raise patcher.PatchError("manifest.json: entry (the mod class) is missing")
        sources = [str(p) for p in (folder / "src").rglob("*.java")]
        if not sources:
            raise patcher.PatchError(f"no Java sources in {folder / 'src'}")
        libs = [str(p) for p in (folder / "libs").glob("*.jar")] if (folder / "libs").exists() else []

        t = tools()
        android = t / "android.jar"
        with tempfile.TemporaryDirectory() as tmp:
            tmp = pathlib.Path(tmp)
            api = tmp / "api"
            patcher.run(["javac", "--release", "8", "-nowarn", "-encoding", "UTF-8", "-cp", android, "-d", api,
                         *map(str, API_SOURCES.glob("*.java"))])
            classes = tmp / "classes"
            cp = ":".join([str(android), str(api), *libs])
            patcher.run(["javac", "--release", "8", "-nowarn", "-encoding", "UTF-8", "-cp", cp, "-d", classes, *sources])
            dex = tmp / "dex"
            dex.mkdir()
            # API classes are in MargyC itself: classpath only, not packed into the mod
            patcher.run(["java", "-cp", t / "r8.jar", "com.android.tools.r8.D8", "--release", "--min-api", "29",
                         "--lib", android, "--classpath", api, "--output", dex,
                         *map(str, classes.rglob("*.class")), *libs])
            out = pathlib.Path(a.output or f"{mod_id}.mcmod")
            with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
                z.write(folder / "manifest.json", "manifest.json")
                for d in sorted(dex.glob("classes*.dex")):
                    z.write(d, d.name)
                if (folder / "icon.png").exists():
                    z.write(folder / "icon.png", "icon.png")
        print(f"built {out}")
    except patcher.PatchError as e:
        sys.exit(f"error: {e}")


if __name__ == "__main__":
    main()
