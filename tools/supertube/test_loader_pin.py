#!/usr/bin/env python3

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BOOTSTRAP_COMMIT = "65903d03854e94e474c34e7d9c78a60e658eff02"

class SuperTubeLoaderPinTests(unittest.TestCase):
    def test_native_loader_is_pinned_to_bootstrap_commit(self):
        text = (ROOT / "third_party/blink/renderer/core/dom/document.cc").read_text(
            encoding="utf-8"
        )
        self.assertIn(
            f"SuperTubeCobalt@{BOOTSTRAP_COMMIT}/supertube/userscript/bootstrap.js",
            text,
        )
        self.assertNotIn(
            "SuperTubeCobalt@main/supertube/userscript/bootstrap.js",
            text,
        )

    def test_loader_patcher_cannot_restore_main_branch(self):
        text = (ROOT / "tools/supertube/patch_userscript_loader.py").read_text(
            encoding="utf-8"
        )
        self.assertIn(BOOTSTRAP_COMMIT, text)
        self.assertNotIn("SuperTubeCobalt@main/", text)

if __name__ == "__main__":
    unittest.main()
