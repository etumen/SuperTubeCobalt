#!/usr/bin/env python3
"""Build the SuperTube userscript from a pinned TizenTube upstream commit."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LOCK = ROOT / "supertube/userscript/upstream.lock.json"
DIST_ROOT = ROOT / "supertube/userscript/dist"


def run(*args: str, cwd: Path | None = None) -> None:
    subprocess.run(args, cwd=cwd, check=True)


def run_npm(mods: Path, *args: str) -> None:
    """Use native Linux npm when available; otherwise use Windows npm from WSL."""
    if shutil.which("node"):
        run("npm", *args, cwd=mods)
        return

    cmd = Path("/mnt/c/Windows/System32/cmd.exe")
    if not cmd.is_file():
        raise RuntimeError("No Linux node and Windows cmd.exe is unavailable")

    win_mods = subprocess.check_output(
        ["wslpath", "-w", str(mods)], text=True
    ).strip()
    command = f'cd /d "{win_mods}" && npm {" ".join(args)}'
    subprocess.run([str(cmd), "/d", "/s", "/c", command], check=True)


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)


def replace_brand_strings(value):
    if isinstance(value, str):
        return (
            value.replace("TizenTube Cobalt", "SuperTube")
            .replace("TizenTube", "SuperTube")
            .replace("TT Welcome Message", "SuperTube Welcome Message")
        )
    if isinstance(value, list):
        return [replace_brand_strings(item) for item in value]
    if isinstance(value, dict):
        return {key: replace_brand_strings(item) for key, item in value.items()}
    return value


def patch_translations(mods: Path) -> None:
    resources = mods / "translations/resources"
    for path in resources.glob("*.json"):
        data = json.loads(path.read_text(encoding="utf-8"))
        data = replace_brand_strings(data)

        settings = data.get("settings", {})
        tt_settings = settings.get("ttSettings", {})
        tt_settings["madeByText"] = "SuperTube • IŞINNET"

        support = settings.get("supportTT", {})
        if path.name == "tr.json":
            support["title"] = "SuperTube Hakkında"
            support["subtitle"] = "SuperTube • IŞINNET"
            support["content"] = {
                "1": "SuperTube, IŞINNET'in Cobalt tabanlı TV deneyimidir.",
                "2": "Yeni özellikler ve güncellemeler kullanıcıya sunulmadan önce test edilir.",
                "3": "YouTube hesabınız doğrudan YouTube içinde bağlı kalır.",
                "4": "Web: https://isinnet.net",
                "5": "Kaynak: https://github.com/etumen/SuperTubeCobalt",
                "6": ""
            }
            misc = settings.get("options", {}).get("misc", {}).get("options", {})
            misc["ttWelcomeMsg"] = "SuperTube Karşılama Mesajını Göster"
        else:
            support["title"] = "About SuperTube"
            support["subtitle"] = "SuperTube • IŞINNET"
            support["content"] = {
                "1": "SuperTube is the IŞINNET Cobalt-based TV experience.",
                "2": "New features and updates are tested before they are released to users.",
                "3": "Your YouTube account remains connected directly inside YouTube.",
                "4": "Web: https://isinnet.net",
                "5": "Source: https://github.com/etumen/SuperTubeCobalt",
                "6": ""
            }
            misc = settings.get("options", {}).get("misc", {}).get("options", {})
            misc["ttWelcomeMsg"] = "Show SuperTube Welcome Message"

        path.write_text(json.dumps(data, ensure_ascii=False, indent=4) + "\n", encoding="utf-8")


def patch_settings(mods: Path) -> None:
    path = mods / "ui/settings.js"
    text = path.read_text(encoding="utf-8")

    social = re.compile(
        r"(name:\s*t\('settings\.options\.socialMedia\.title'\),.*?options:\s*)"
        r"\[(.*?)\](\.map\(\(option\) => \{)",
        re.S,
    )
    matches = list(social.finditer(text))
    if len(matches) != 1:
        raise RuntimeError(f"social links: expected 1 block, found {len(matches)}")
    replacement = (
        matches[0].group(1)
        + "[\n"
        + "                { name: 'GitHub', link: 'https://github.com/etumen/SuperTubeCobalt' },\n"
        + "                { name: 'IŞINNET', link: 'https://isinnet.net' }\n"
        + "            ]"
        + matches[0].group(3)
    )
    text = text[: matches[0].start()] + replacement + text[matches[0].end() :]

    updater_gate = "window.h5vcc && window.h5vcc.tizentube ?\n            {\n                name: t('settings.options.updater.title')"
    text = replace_once(
        text,
        updater_gate,
        "false ?\n            {\n                name: t('settings.options.updater.title')",
        "updater menu gate",
    )
    path.write_text(text, encoding="utf-8")


def patch_runtime(mods: Path) -> None:
    config = mods / "config.js"
    text = config.read_text(encoding="utf-8")
    text = replace_once(text, "enableUpdater: true,", "enableUpdater: false,", "default updater")
    config.write_text(text, encoding="utf-8")

    updater = mods / "features/updater.js"
    text = updater.read_text(encoding="utf-8")
    text = replace_once(
        text,
        "https://api.github.com/repos/reisxd/TizenTubeCobalt/releases/latest",
        "https://api.github.com/repos/etumen/SuperTubeCobalt/releases/latest",
        "APK updater endpoint",
    )
    updater.write_text(text, encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--channel", choices=("stable", "staging"), required=True)
    args = parser.parse_args()

    lock = json.loads(LOCK.read_text(encoding="utf-8"))
    upstream = lock["upstreamRepository"]
    commit = lock["upstreamCommit"]

    windows_npm_mode = shutil.which("node") is None
    temp_parent = None
    if windows_npm_mode:
        temp_parent = Path("/mnt/c/Temp")
        temp_parent.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory(prefix="supertube-userscript-", dir=temp_parent) as temp:
        source = Path(temp) / "TizenTube"
        run("git", "clone", "--filter=blob:none", "--no-checkout", upstream, str(source))
        run("git", "checkout", commit, cwd=source)
        mods = source / "mods"

        patch_translations(mods)
        patch_settings(mods)
        patch_runtime(mods)

        run_npm(mods, "ci")
        run_npm(mods, "run", "build")

        built = source / "dist/userScript.js"
        if not built.is_file() or built.stat().st_size < 10_000:
            raise RuntimeError("userscript build output missing or unexpectedly small")

        output_dir = DIST_ROOT / args.channel
        output_dir.mkdir(parents=True, exist_ok=True)
        output = output_dir / "userScript.js"
        shutil.copy2(built, output)

        digest = hashlib.sha256(output.read_bytes()).hexdigest()
        metadata = {
            "channel": args.channel,
            "upstreamRepository": upstream,
            "upstreamCommit": commit,
            "sha256": digest,
            "license": "GPL-3.0-only",
        }
        (output_dir / "build.json").write_text(
            json.dumps(metadata, indent=2) + "\n", encoding="utf-8"
        )
        print(f"Built {args.channel}: {output} sha256={digest}")


if __name__ == "__main__":
    main()
