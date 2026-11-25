# SuperTube Cobalt — mandatory repository instructions

Read ROADMAP.md before making changes. These rules apply to Codex, ChatGPT and all other development agents.

## One canonical build workspace

- WSL distro: CobaltBuild.
- Source directory: /home/cobalt/work/cobalt/src.
- Persistent Android ARM GOLD output directory: out/android-arm_gold.
- Do not create a second clone, worktree, build output directory, or replace the current source/build tree for routine SuperTube development.
- Before editing, inspect git status, branch, remote, existing GN arguments, running build processes and disk space. Preserve unrelated local changes.
- If Ninja or another build is running, do not modify build inputs, switch branches, regenerate GN, clean files, update dependencies/toolchains or launch another concurrent build in that output tree. Wait for completion or obtain explicit permission to stop it.

## Incremental build is mandatory

- Reuse the same GOLD output directory and existing GN/Ninja configuration. Normal app/Java/native changes are incremental.
- Never routinely run gn clean, ninja -t clean, git clean -xfd, rm -rf out, flutter/Gradle-style cleanup on this tree, or delete .ninja_log, .ninja_deps, object files, generated sources, dependency caches, SDK/JDK or downloaded toolchains.
- Never discard the WSL distro or build disk to fix an ordinary build error.
- For native-only resumption, use the existing Ninja target cobalt/android:libchrobalt. For the final signed APK, use cobalt/android:cobalt_apk after verifying the current GOLD signing configuration. Use the already installed third_party/ninja/ninja, existing output directory, and conservative worker count; do not restart a running build.
- Run GN generation in the SAME output directory only when build configuration or GN inputs actually require it, after recording the previous args and inspecting the expected rebuild impact. Do not force reuse of invalid objects or suppress required dependency rebuilds.
- A failed build is diagnosed from its actual error/log. Fix the smallest proven cause, then resume the same incremental build. No speculative resets, source replacement or repeated identical failed attempts.
- A clean/full rebuild or separate output tree requires documented technical necessity, impact on time/storage, a recovery plan, and explicit user approval first.
- Preserve the build cache across version bumps and reboot. Never promise a fixed completion time based solely on Ninja step count; link and packaging may be expensive.

## Release invariants

- Keep Android package net.isinnet.supertube and the approved permanent production signing identity. Do not replace signer keys, downgrade versions or publish a debug APK disguised as GOLD.
- Production requires genuine GOLD build, verified APK signature, debuggable=false, expected package/version/build/ABI, SHA-256 and size. Keep credentials private; never commit or expose passwords, keystores, tokens or raw signing environment.
- Use Hub OAuth Device Code and Agent signer-verified app approval; preserve authoritative Hub entitlements and secure token storage. Do not restore legacy apph handoff or bypass membership checks.
- No R2 publication, catalog changes, broad deployment or live service changes outside the user's authorization. Physical Mi Box acceptance is distinct from build success.
- Commit and push completed changes, including roadmap updates. Do not merge main without the project's approval process. Report actual tests, failures, exact commit and unresolved blockers. Keep unrelated projects intact.
