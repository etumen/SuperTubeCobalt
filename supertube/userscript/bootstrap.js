/*
 * SuperTube userscript bootstrap.
 *
 * Purpose:
 * - Never load TizenTube directly from npm/latest at runtime.
 * - Keep normal users on a promoted stable SuperTube artifact.
 * - Let test devices opt in to an immutable staging artifact before promotion.
 *
 * Upstream TizenTube userscript is GPL-3.0-only:
 * https://github.com/reisxd/TizenTube
 */
(() => {
  'use strict';

  const CHANNEL_KEY = 'supertube.userscript.channel';

  // Promotion rule:
  // 1) publish a new immutable staging artifact,
  // 2) test that exact artifact on a SuperTube test TV,
  // 3) only after approval, promote the exact tested bytes to stable.
  //
  // Stable intentionally remains empty until the first TV-tested artifact is
  // promoted. Failing closed is safer than silently falling back to upstream.
  const ARTIFACTS = Object.freeze({
    stable: null,
    staging: Object.freeze({
      upstreamVersion: '1.15.0',
      upstreamCommit: '893b663d35efa558d8bdf9f54f0c4f9a31ab6a07',
      artifactCommit: '12bcc3ebc7dda3178129a206e786c43ea41bd4de',
      sha256: 'f4e010cdb260880b8b32d7b7d70b5dcce3d0cf4d6cf6e3b6128541f3a1641e08',
      url: 'https://cdn.jsdelivr.net/gh/etumen/SuperTubeCobalt@12bcc3ebc7dda3178129a206e786c43ea41bd4de/supertube/userscript/dist/staging/userScript.js',
    }),
  });

  let requestedChannel = 'stable';
  try {
    requestedChannel = window.localStorage.getItem(CHANNEL_KEY) || 'stable';
  } catch (_) {
    // Some TV runtimes may block localStorage. Stable is always the fallback.
  }

  const channel = requestedChannel === 'staging' ? 'staging' : 'stable';
  const artifact = ARTIFACTS[channel];

  if (!artifact) {
    console.error(`[SuperTube] No ${channel} userscript artifact has been promoted.`);
    return;
  }

  const script = document.createElement('script');
  script.async = true;
  script.src = artifact.url;
  script.dataset.supertubeChannel = channel;
  script.dataset.supertubeUpstreamVersion = artifact.upstreamVersion;
  script.dataset.supertubeUpstreamCommit = artifact.upstreamCommit;
  script.dataset.supertubeArtifactCommit = artifact.artifactCommit;
  script.dataset.supertubeSha256 = artifact.sha256;
  script.onerror = () => {
    console.error(
      `[SuperTube] userscript load failed: channel=${channel} artifact=${artifact.artifactCommit}`,
    );
  };

  const target = document.head || document.documentElement;
  if (!target) {
    console.error('[SuperTube] No DOM target available for userscript bootstrap.');
    return;
  }

  target.appendChild(script);
})();
