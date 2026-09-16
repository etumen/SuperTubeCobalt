#!/usr/bin/env python3

import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("build_userscript_fork.py")
spec = importlib.util.spec_from_file_location("build_userscript_fork", MODULE_PATH)
builder = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(builder)


class SuperTubeSecurityControlTests(unittest.TestCase):
    def test_translation_patch_rebrands_cyrillic_legacy_names(self):
        with tempfile.TemporaryDirectory() as temp:
            mods = Path(temp)
            resources = mods / "translations" / "resources"
            resources.mkdir(parents=True)
            fixture = {
                "settings": {
                    "options": {
                        "misc": {
                            "options": {
                                "ttWelcomeMsg": "Прикажи ТизенТубе поруку добродошлице"
                            }
                        }
                    },
                    "ttSettings": {
                        "title": "ТизенТубе подешавања",
                        "madeByText": "ТизенТубе Цобалт",
                    },
                    "supportTT": {
                        "title": "Подржи ТизенТубе",
                        "subtitle": "ТизенТубе Цобалт",
                        "content": {},
                    },
                }
            }
            path = resources / "sr-Cyrl.json"
            path.write_text(json.dumps(fixture, ensure_ascii=False), encoding="utf-8")

            builder.patch_translations(mods)

            serialized = path.read_text(encoding="utf-8")
            self.assertNotIn("ТизенТубе", serialized)
            self.assertNotIn("Цобалт", serialized)
            self.assertIn("SuperTube", serialized)

    def test_runtime_patch_removes_direct_apk_update_capability(self):
        with tempfile.TemporaryDirectory() as temp:
            mods = Path(temp)
            (mods / "features").mkdir(parents=True)

            (mods / "config.js").write_text(
                "const config = { enableUpdater: true, };\n",
                encoding="utf-8",
            )
            (mods / "features" / "updater.js").write_text(
                "const endpoint = 'https://api.github.com/repos/reisxd/TizenTubeCobalt/releases/latest';\n",
                encoding="utf-8",
            )
            (mods / "resolveCommand.js").write_text(
                "import checkForUpdates from './features/updater.js';\n"
                "switch (action) {\n"
                "case 'UPDATE_DOWNLOAD':\n"
                "    window.h5vcc.tizentube.InstallAppFromURL(parameters);\n"
                "    showToast(t('settings.options.updater.downloading.title'), t('settings.options.updater.downloading.subtitle'));\n"
                "    break;\n"
                "case 'CHECK_FOR_UPDATES':\n"
                "    checkForUpdates(true);\n"
                "    break;\n"
                "}\n",
                encoding="utf-8",
            )
            (mods / "userScript.js").write_text(
                'import "./features/updater.js";\nimport "./features/pictureInPicture.js";\n',
                encoding="utf-8",
            )

            builder.patch_runtime(mods)

            resolve_text = (mods / "resolveCommand.js").read_text(encoding="utf-8")
            entry_text = (mods / "userScript.js").read_text(encoding="utf-8")
            combined = resolve_text + "\n" + entry_text

            self.assertNotIn("InstallAppFromURL", combined)
            self.assertNotIn("checkForUpdates(true)", combined)
            self.assertNotIn("import checkForUpdates from './features/updater.js'", combined)
            self.assertNotIn('import "./features/updater.js"', combined)
            self.assertIn("Native self-update is disabled", resolve_text)


if __name__ == "__main__":
    unittest.main()
