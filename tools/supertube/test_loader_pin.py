#!/usr/bin/env python3

import importlib.util
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
STABLE_ARTIFACT_COMMIT = "1200a9643f141e28d6db3db5de9eb8304fa262f3"
STABLE_ARTIFACT_PATH = "supertube/userscript/dist/stable/userScript.js"

PATCHER_PATH = ROOT / "tools/supertube/patch_userscript_loader.py"
spec = importlib.util.spec_from_file_location("patch_userscript_loader", PATCHER_PATH)
patcher = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(patcher)


class SuperTubeLoaderPinTests(unittest.TestCase):
    def test_native_loader_is_pinned_directly_to_stable_artifact(self):
        text = (ROOT / "third_party/blink/renderer/core/dom/document.cc").read_text(
            encoding="utf-8"
        )
        expected = f"SuperTubeCobalt@{STABLE_ARTIFACT_COMMIT}/{STABLE_ARTIFACT_PATH}"
        self.assertIn(expected, text)
        self.assertNotIn("SuperTubeCobalt@main/", text)
        self.assertNotIn("/supertube/userscript/bootstrap.js", text)

    def test_loader_patcher_uses_same_immutable_stable_artifact(self):
        expected = f"SuperTubeCobalt@{STABLE_ARTIFACT_COMMIT}/{STABLE_ARTIFACT_PATH}"
        self.assertIn(expected, patcher.NEW)
        self.assertNotIn("SuperTubeCobalt@main/", patcher.NEW)
        self.assertNotIn("/supertube/userscript/bootstrap.js", patcher.NEW)


if __name__ == "__main__":
    unittest.main()
