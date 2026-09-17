#!/usr/bin/env python3
"""Route Cobalt's injected userscript through the SuperTube-controlled bootstrap."""

from pathlib import Path

TARGET = Path("third_party/blink/renderer/core/dom/document.cc")
OLD = 'std::string("https://cdn.jsdelivr.net/npm/@foxreis/tizentube/dist/userScript.js?v=")'
NEW = (
    'std::string("https://cdn.jsdelivr.net/gh/etumen/SuperTubeCobalt@30a3b025558c298b832946ddd0805e38faee1a7a/'
    'supertube/userscript/bootstrap.js?v=")'
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
    print("Patched Cobalt userscript loader to SuperTube bootstrap.")


if __name__ == "__main__":
    main()
