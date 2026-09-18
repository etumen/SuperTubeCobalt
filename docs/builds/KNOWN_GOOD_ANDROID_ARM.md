# SuperTube Known-Good Android ARM Build

Date: 2026-09-18

## Source
- Commit: `988ab80be5e2af8b84f73c955d1cdd7cd6b410c0`
- Branch: `feat/supertube-userscript-control`

## APK
- Package: `net.isinnet.supertube`
- Version: `2.0.2`
- Version code: `202`
- ABI: `armeabi-v7a`
- SHA-256: `91fee93b8d49a577702d701e1203f4eb2d93ff1dd4e9c3cef5c01b5d2cbfa742`

## Stable Userscript
- Artifact commit: `1200a9643f141e28d6db3db5de9eb8304fa262f3`
- SHA-256: `f4e010cdb260880b8b32d7b7d70b5dcce3d0cf4d6cf6e3b6128541f3a1641e08`
- Runtime loader: direct immutable stable artifact

## Mi Box Verification
- Application startup: PASS
- Video playback: PASS
- First-video preroll ad test: PASS
- Timeline ad markers: not observed
- Multiple-video ad test: PASS
- Volume delay: confirmed device-wide, not SuperTube-specific

This build is the rollback/reference point for subsequent Android TV development.
