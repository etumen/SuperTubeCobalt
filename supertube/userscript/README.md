# SuperTube userscript control

This directory is the control boundary between the SuperTube Cobalt runtime and the upstream TizenTube userscript.

## Goal

Production devices must never execute an unversioned `latest` TizenTube userscript or load the upstream npm package directly at runtime. Upstream changes first enter the SuperTube `staging` channel, are built and audited by our downstream builder, are tested on a test TV, and only then are promoted to `stable`.

The upstream userscript remains a separate GPL-3.0-only component. Cobalt engine/internal technical names are not renamed just for branding. User-visible SuperTube branding is handled in the SuperTube userscript/branding layer.

## Channels

`bootstrap.js` defaults to `stable`. Each populated channel points to an immutable SuperTube artifact stored in this repository at an exact Git commit, not to `@foxreis/tizentube` on npm.

Until the first TV-tested artifact is promoted, `stable` intentionally has no artifact and fails closed. A test device can opt in to staging with:

```js
localStorage.setItem('supertube.userscript.channel', 'staging')
```

Then reload SuperTube. Return the device to production behavior with:

```js
localStorage.removeItem('supertube.userscript.channel')
```

If localStorage is unavailable, the loader always falls back to `stable`.

## Update procedure

1. Check the new upstream TizenTube release/commit and diff it against `upstream.lock.json`.
2. Pin the selected upstream commit and build `supertube/userscript/dist/staging/userScript.js` with `tools/supertube/build_userscript_fork.py --channel staging`.
3. Verify the generated `build.json` SHA-256 and commit the exact staging artifact without changing `stable`.
4. Update the staging entry in `bootstrap.js` to the immutable artifact commit and recorded SHA-256.
5. Test that exact staging artifact on the SuperTube test device: startup, playback, sign-in, YouTube account QR, SponsorBlock/ad-block features, remote keys, settings, QR utilities, and visible branding.
6. If the test fails, leave `stable` unchanged and fix/reject the candidate.
7. If the test passes, promote the exact tested bytes to `dist/stable`, record the same SHA-256, and point the stable bootstrap entry to that immutable commit.
8. Rollback means repointing `stable` to the previous known-good immutable artifact; it never falls back to upstream latest.

## QR ownership

There are two independent QR flows:

- YouTube/Google account QR: owned by YouTube inside SuperTube. It must remain so the viewer can use their own YouTube account, history, subscriptions, library, and recommendations.
- IŞINNET Hub device-code QR: owned by the SuperTube/Agent + Hub authorization layer. It proves device/subscription entitlement; it does not replace YouTube login.

Managed IŞINNET devices can be enrolled by Agent so Hub authorization is normally silent. Customer-owned Android/Google TV devices without Agent can use the Hub device-code QR on first SuperTube activation.

## Branding rule

User-visible product surfaces must say **SuperTube**. Technical Cobalt identifiers and upstream compatibility symbols such as `h5vcc_tizentube` are left untouched unless changing them is technically necessary. Settings/About/social links supplied by our downstream userscript must point to SuperTube/IŞINNET, not TizenTube upstream branding.
