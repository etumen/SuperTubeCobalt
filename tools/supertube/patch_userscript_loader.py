#!/usr/bin/env python3
"""Route Cobalt's injected userscript to the immutable SuperTube stable artifact."""

from pathlib import Path

TARGET = Path("third_party/blink/renderer/core/dom/document.cc")
OLD = 'std::string("https://cdn.jsdelivr.net/npm/@foxreis/tizentube/dist/userScript.js?v=")'
NEW = (
    'std::string("https://cdn.jsdelivr.net/gh/etumen/SuperTubeCobalt@1200a9643f141e28d6db3db5de9eb8304fa262f3/'
    'supertube/userscript/dist/stable/userScript.js?v=")'
)


def main() -> None:
    text = TARGET.read_text(encoding="utf-8")

    old_count = text.count(OLD)
    new_count = text.count(NEW)

    if old_count == 0 and new_count == 1:
        print("SuperTube userscript loader already patched.")
        return

    if old_count != 1 or new_count != 0:
        raise SystemExit(
            "Refusing unsafe patch: expected exactly one upstream loader URL "
            f"(old={old_count}, new={new_count})."
        )

    TARGET.write_text(text.replace(OLD, NEW, 1), encoding="utf-8")
    print("Patched Cobalt userscript loader to immutable SuperTube stable artifact.")


if __name__ == "__main__":
    main()
