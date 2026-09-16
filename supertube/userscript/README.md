# SuperTube userscript control

This directory is the control boundary between the SuperTube Cobalt runtime and the upstream TizenTube userscript.

## Goal

Production devices must never execute an unversioned `latest` TizenTube userscript. Upstream changes first enter the SuperTube `staging` channel, are tested on a test TV, and only then are promoted to `stable`.

The upstream userscript remains a separate GPL-3.0-only component. Cobalt engine/internal technical names are not renamed just for branding. User-visible SuperTube branding is handled in the SuperTube userscript/branding layer.

## Channels

`bootstrap.js` defaults to `stable` and loads an exact npm version.

For a test device, set:

```js
localStorage.setItem('supertube.userscript.channel', 'staging')
```

Then reload SuperTube. Return the device to production behavior with:

```js
localStorage.removeItem('supertube.userscript.channel')
```

If localStorage is unavailable, the loader always falls back to `stable`.

## Update procedure

1. Check the new upstream TizenTube release and diff it against the currently pinned version.
2. Update only `staging` in `bootstrap.js` and `stagingVersion` in `upstream.lock.json`.
3. Test on the SuperTube test device: startup, playback, sign-in, YouTube account QR, SponsorBlock/ad-block features, remote keys, settings, QR utilities, and visible branding.
4. If the test fails, leave `stable` unchanged and fix/reject the candidate.
5. If the test passes, promote the exact tested version by changing `stable` to match `staging`.
6. Rollback is an immediate stable-version revert to the last known-good version.

## QR ownership

There are two independent QR flows:

- YouTube/Google account QR: owned by YouTube inside SuperTube. It must remain so the viewer can use their own YouTube account, history, subscriptions, library, and recommendations.
- IŞINNET Hub device-code QR: owned by the SuperTube/Agent + Hub authorization layer. It proves device/subscription entitlement; it does not replace YouTube login.

Managed IŞINNET devices can be enrolled by Agent so Hub authorization is normally silent. Customer-owned Android/Google TV devices without Agent can use the Hub device-code QR on first SuperTube activation.

## Branding rule

User-visible product surfaces must say **SuperTube**. Technical Cobalt identifiers and upstream compatibility symbols such as `h5vcc_tizentube` are left untouched unless changing them is technically necessary. Settings/About/social links supplied by our downstream userscript must point to SuperTube/IŞINNET, not TizenTube upstream branding.
