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
import os
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
ENTRY_RE = re.compile(r"(?:[A-Za-z_$][A-Za-z0-9_$]*\.)+[A-Za-z_$][A-Za-z0-9_$]*")


def load_manifest(folder):
    path = folder / "manifest.json"
    try:
        manifest = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        raise patcher.PatchError(f"{path} not found")
    except (OSError, UnicodeError, json.JSONDecodeError) as e:
        raise patcher.PatchError(f"cannot read {path}: {e}")
    if not isinstance(manifest, dict):
        raise patcher.PatchError("manifest.json: the top-level value must be an object")

    mod_id = manifest.get("id")
    if not isinstance(mod_id, str) or not re.fullmatch(r"[a-z0-9][a-z0-9._-]{1,63}", mod_id):
        raise patcher.PatchError("manifest.json: id must be 2-64 lowercase letters, digits, . _ -")
    entry = manifest.get("entry")
    if not isinstance(entry, str) or not ENTRY_RE.fullmatch(entry):
        raise patcher.PatchError("manifest.json: entry must be a fully qualified Java class name")
    api = manifest.get("api", 1)
    if isinstance(api, bool) or not isinstance(api, int) or api < 1:
        raise patcher.PatchError("manifest.json: api must be a positive integer")
    settings = manifest.get("settings", [])
    if not isinstance(settings, list):
        raise patcher.PatchError("manifest.json: settings must be an array")
    return manifest


def tools():
    for exe in ("java", "javac"):
        if not shutil.which(exe):
            raise patcher.PatchError(f"{exe} not found, JDK 17+ is needed")
    d = patcher.tools_dir()
    if not (d / "r8.jar").exists():
        patcher.download(patcher.TOOLS["r8.jar"], d / "r8.jar")
    if not (d / "android.jar").exists():
        z = d / "platform.zip"
        patcher.download(patcher.PLATFORM_ZIP, z)
        partial = d / "android.jar.part"
        try:
            with zipfile.ZipFile(z) as zf:
                entry = next((n for n in zf.namelist() if n.endswith("/android.jar")), None)
                if entry is None:
                    raise patcher.PatchError("downloaded Android platform has no android.jar")
                partial.write_bytes(zf.read(entry))
            os.replace(partial, d / "android.jar")
        finally:
            partial.unlink(missing_ok=True)
            z.unlink(missing_ok=True)
    return d


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("folder", help="mod folder with manifest.json and src/")
    ap.add_argument("-o", "--output", help="output .mcmod (default: <id>.mcmod)")
    a = ap.parse_args()

    folder = pathlib.Path(a.folder).resolve()
    try:
        if not folder.is_dir():
            raise patcher.PatchError(f"mod folder not found: {folder}")
        manifest = load_manifest(folder)
        mod_id = manifest["id"]
        sources = [str(p) for p in sorted((folder / "src").rglob("*.java"))]
        if not sources:
            raise patcher.PatchError(f"no Java sources in {folder / 'src'}")
        libs = [str(p) for p in sorted((folder / "libs").glob("*.jar"))] if (folder / "libs").exists() else []

        t = tools()
        android = t / "android.jar"
        with tempfile.TemporaryDirectory() as tmp:
            tmp = pathlib.Path(tmp)
            api = tmp / "api"
            patcher.run(["javac", "--release", "8", "-nowarn", "-encoding", "UTF-8", "-cp", android, "-d", api,
                         *map(str, API_SOURCES.glob("*.java"))])
            classes = tmp / "classes"
            cp = os.pathsep.join([str(android), str(api), *libs])
            patcher.run(["javac", "--release", "8", "-nowarn", "-encoding", "UTF-8", "-cp", cp, "-d", classes, *sources])
            dex = tmp / "dex"
            dex.mkdir()
            # API classes are in MargyC itself: classpath only, not packed into the mod
            patcher.run(["java", "-cp", t / "r8.jar", "com.android.tools.r8.D8", "--release", "--min-api", "29",
                         "--lib", android, "--classpath", api, "--output", dex,
                         *map(str, classes.rglob("*.class")), *libs])
            out = pathlib.Path(a.output or f"{mod_id}.mcmod").resolve()
            out.parent.mkdir(parents=True, exist_ok=True)
            tmp_out = out.with_name(out.name + ".tmp")
            tmp_out.unlink(missing_ok=True)
            try:
                with zipfile.ZipFile(tmp_out, "w", zipfile.ZIP_DEFLATED) as z:
                    z.write(folder / "manifest.json", "manifest.json")
                    for d in sorted(dex.glob("classes*.dex")):
                        z.write(d, d.name)
                    if (folder / "icon.png").exists():
                        z.write(folder / "icon.png", "icon.png")
                os.replace(tmp_out, out)
            finally:
                tmp_out.unlink(missing_ok=True)
        print(f"built {out}")
    except (patcher.PatchError, OSError, zipfile.BadZipFile) as e:
        sys.exit(f"error: {e}")


if __name__ == "__main__":
    main()
