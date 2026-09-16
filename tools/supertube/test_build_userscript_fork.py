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


class SuperTubeUserscriptBuilderTests(unittest.TestCase):
    def test_translation_patch_updates_real_upstream_branding_and_support_links(self):
        with tempfile.TemporaryDirectory() as temp:
            mods = Path(temp)
            resources = mods / "translations" / "resources"
            resources.mkdir(parents=True)
            fixture = {
                "settings": {
                    "options": {
                        "misc": {"options": {"ttWelcomeMsg": "Afficher le message TT"}},
                    },
                    "ttSettings": {
                        "title": "Paramètres TizenTube",
                        "madeByText": "Fait par Reis Can",
                    },
                    "supportTT": {
                        "title": "Soutenir TizenTube",
                        "content": {
                            "5": "- Buy Me A Coffee : https://www.buymeacoffee.com/reisxd",
                            "6": "- GitHub Sponsors : https://github.com/sponsors/reisxd",
                        },
                    },
                    "player": {"withTizenTube": "avec TizenTube"},
                }
            }
            path = resources / "en.json"
            path.write_text(json.dumps(fixture), encoding="utf-8")

            builder.patch_translations(mods)

            patched = json.loads(path.read_text(encoding="utf-8"))
            settings = patched["settings"]
            self.assertEqual(settings["ttSettings"]["madeByText"], "SuperTube • IŞINNET")
            support = settings["supportTT"]
            serialized = json.dumps(support, ensure_ascii=False)
            self.assertNotIn("buymeacoffee.com/reisxd", serialized)
            self.assertNotIn("github.com/sponsors/reisxd", serialized)
            self.assertIn("https://isinnet.net", serialized)
            self.assertIn("https://github.com/etumen/SuperTubeCobalt", serialized)
            self.assertIn("withTizenTube", settings["player"])
            self.assertEqual(settings["player"]["withTizenTube"], "avec SuperTube")

    def test_translation_patch_updates_french_nested_locale_variant(self):
        with tempfile.TemporaryDirectory() as temp:
            mods = Path(temp)
            resources = mods / "translations" / "resources"
            resources.mkdir(parents=True)
            fixture = {
                "settings": {
                    "options": {
                        "misc": {"options": {"ttWelcomeMsg": "Afficher le message TT"}},
                        "ttSettings": {
                            "title": "Paramètres TizenTube",
                            "madeByText": "Fait par Reis Can (reisxd) avec ❤️",
                            "summary": "Ouvrir les paramètres TizenTube",
                        },
                        "supportTT": {
                            "title": "Soutenir TizenTube",
                            "subtitle": "❤️ Montrez votre soutien à TizenTube et à son développement",
                            "content": {
                                "5": "- Buy Me A Coffee : https://www.buymeacoffee.com/reisxd (de préférence)",
                                "6": "- GitHub Sponsors : https://github.com/sponsors/reisxd",
                            },
                        },
                    }
                }
            }
            path = resources / "fr.json"
            path.write_text(json.dumps(fixture), encoding="utf-8")

            builder.patch_translations(mods)

            patched = json.loads(path.read_text(encoding="utf-8"))
            options = patched["settings"]["options"]
            self.assertEqual(options["ttSettings"]["madeByText"], "SuperTube • IŞINNET")
            support = options["supportTT"]
            serialized = json.dumps(support, ensure_ascii=False)
            self.assertNotIn("buymeacoffee.com/reisxd", serialized)
            self.assertNotIn("github.com/sponsors/reisxd", serialized)
            self.assertIn("https://isinnet.net", serialized)
            self.assertIn("https://github.com/etumen/SuperTubeCobalt", serialized)

    def test_js_brand_patch_rebrands_literals_but_preserves_native_api_identifier(self):
        with tempfile.TemporaryDirectory() as temp:
            mods = Path(temp)
            (mods / "features").mkdir(parents=True)
            (mods / "ui").mkdir(parents=True)

            resolve = mods / "resolveCommand.js"
            resolve.write_text(
                "window.h5vcc.tizentube.EnterPIP();\nshowToast('TizenTube', 'ok');\n",
                encoding="utf-8",
            )
            ui = mods / "ui" / "ui.js"
            ui.write_text("const title = 'TizenTube Theme Configuration';\n", encoding="utf-8")
            updater = mods / "features" / "updater.js"
            updater.write_text(
                "console.info('You are using the latest version of TizenTube.');\n",
                encoding="utf-8",
            )
            subtitles = mods / "features" / "moreSubtitles.js"
            subtitles.write_text(
                "console.log('TizenTube Subtitle Localization: Module loaded');\n",
                encoding="utf-8",
            )

            builder.patch_brand_literals(mods)

            combined = "\n".join(
                p.read_text(encoding="utf-8") for p in (resolve, ui, updater, subtitles)
            )
            self.assertNotIn("'TizenTube'", combined)
            self.assertNotIn("TizenTube Theme Configuration", combined)
            self.assertNotIn("latest version of TizenTube", combined)
            self.assertNotIn("TizenTube Subtitle Localization", combined)
            self.assertIn("SuperTube", combined)
            self.assertIn("window.h5vcc.tizentube", combined)

    def test_artifact_validation_rejects_legacy_links(self):
        with tempfile.TemporaryDirectory() as temp:
            artifact = Path(temp) / "userScript.js"
            artifact.write_text(
                "SuperTube IŞINNET https://isinnet.net "
                "https://github.com/etumen/SuperTubeCobalt "
                "https://www.buymeacoffee.com/reisxd",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(RuntimeError, "forbidden legacy strings"):
                builder.validate_built_userscript(artifact)


if __name__ == "__main__":
    unittest.main()
