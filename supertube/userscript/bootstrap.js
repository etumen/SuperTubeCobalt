/*
 * SuperTube userscript bootstrap.
 *
 * Purpose:
 * - Never load the unversioned/latest TizenTube userscript directly.
 * - Keep normal users on a pinned stable version.
 * - Let test devices opt in to a pinned staging version before promotion.
 *
 * Upstream TizenTube userscript is GPL-3.0-only:
 * https://github.com/reisxd/TizenTube
 */
(() => {
  'use strict';

  const CHANNEL_KEY = 'supertube.userscript.channel';

  // Promotion rule:
  // 1) change staging only,
  // 2) test on a SuperTube test TV,
  // 3) after approval, set stable to the tested staging version.
  const VERSIONS = Object.freeze({
    stable: '1.15.0',
    staging: '1.15.0',
  });

  let requestedChannel = 'stable';
  try {
    requestedChannel = window.localStorage.getItem(CHANNEL_KEY) || 'stable';
  } catch (_) {
    // Some TV runtimes may block localStorage. Stable is always the fallback.
  }

  const channel = requestedChannel === 'staging' ? 'staging' : 'stable';
  const version = VERSIONS[channel];

  if (!/^\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?$/.test(version)) {
    console.error('[SuperTube] Refusing invalid userscript version:', version);
    return;
  }

  const script = document.createElement('script');
  script.async = true;
  script.src = `https://cdn.jsdelivr.net/npm/@foxreis/tizentube@${encodeURIComponent(version)}/dist/userScript.js`;
  script.dataset.supertubeChannel = channel;
  script.dataset.supertubeUpstreamVersion = version;
  script.onerror = () => {
    console.error(`[SuperTube] userscript load failed: channel=${channel} version=${version}`);
  };

  const target = document.head || document.documentElement;
  if (!target) {
    console.error('[SuperTube] No DOM target available for userscript bootstrap.');
    return;
  }

  target.appendChild(script);
})();
