import json
import tempfile
import unittest
import zipfile
from pathlib import Path

import patcher
from tools.build_mod import load_manifest


class ManifestTests(unittest.TestCase):
    def load(self, value):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            (folder / "manifest.json").write_text(json.dumps(value), encoding="utf-8")
            return load_manifest(folder)

    def test_valid_manifest(self):
        manifest = self.load({"id": "hello.mod", "entry": "com.example.Hello", "api": 1})
        self.assertEqual("hello.mod", manifest["id"])

    def test_rejects_invalid_fields(self):
        invalid = [
            [],
            {"id": "x", "entry": "com.example.Hello"},
            {"id": "Hello", "entry": "com.example.Hello"},
            {"id": "hello", "entry": "Hello"},
            {"id": "hello", "entry": "com.example.Hello", "api": True},
            {"id": "hello", "entry": "com.example.Hello", "api": 0},
            {"id": "hello", "entry": "com.example.Hello", "settings": {}},
        ]
        for manifest in invalid:
            with self.subTest(manifest=manifest):
                with self.assertRaises(patcher.PatchError):
                    self.load(manifest)


class MergeInputTests(unittest.TestCase):
    def test_accepts_uppercase_apk(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            apk = root / "Claude.APK"
            apk.write_bytes(b"apk")
            self.assertEqual(apk, patcher.merge_input(apk, root / "work", root))

    def test_extracts_only_root_apks(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "bundle.xapk"
            with zipfile.ZipFile(source, "w") as archive:
                archive.writestr("base.APK", b"base")
                archive.writestr("nested/ignored.apk", b"nested")
            result = patcher.merge_input(source, root / "work", root)
            self.assertEqual(b"base", result.read_bytes())

    def test_reports_invalid_archive(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "broken.xapk"
            source.write_bytes(b"not a zip")
            with self.assertRaisesRegex(patcher.PatchError, "не удалось прочитать"):
                patcher.merge_input(source, root / "work", root)


if __name__ == "__main__":
    unittest.main()