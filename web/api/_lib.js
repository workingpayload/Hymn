/**
 * Shared helpers for the three endpoints.
 *
 * Files prefixed with `_` are not routed by Vercel, so this stays a private module.
 *
 * Two counters are deliberately kept side by side:
 *
 * - **GitHub's own `download_count`** is the ground truth for the file. It counts every fetch of
 *   the asset however it was reached, so it can never be reset or inflated by this site.
 * - **The page counter** in Redis counts clicks that actually went through this landing page.
 *
 * Neither is a superset of the other, which is why the page shows both.
 */

const KV_URL = process.env.KV_REST_API_URL;
const KV_TOKEN = process.env.KV_REST_API_TOKEN;

export const REPO = process.env.GITHUB_REPO || 'workingpayload/Hymn';
export const COUNTER_KEY = 'musicsm:downloads:total';

// MusicSM Desktop ships from its own repo. Its installers' downloads are added to the GitHub total,
// so the page counts both apps. DESKTOP_GITHUB_REPO=none leaves them out.
const desktopRepoEnv = (process.env.DESKTOP_GITHUB_REPO || '').trim();
export const DESKTOP_REPO =
  desktopRepoEnv.toLowerCase() === 'none' ? null : desktopRepoEnv || 'workingpayload/MusicSM-Desktop';

// GitHub resets an asset's `download_count` to 0 whenever the APK is deleted and re-uploaded, or a
// release is recreated — so a naive sum across releases drops on every such publish. These two keys
// let us keep a durable lifetime figure that never goes backwards: BASELINE is the running sum of
// every GitHub total that was later wiped, and LAST_SEEN is the most recent GitHub total observed.
export const BASELINE_KEY = 'musicsm:downloads:baseline';
export const LAST_SEEN_KEY = 'musicsm:downloads:githubseen';
// The same pair for the desktop installers, kept apart so a reset in one repo can't be mistaken for
// (or hidden by) growth in the other.
export const DESKTOP_BASELINE_KEY = 'musicsm:desktop:downloads:baseline';
export const DESKTOP_LAST_SEEN_KEY = 'musicsm:desktop:downloads:githubseen';

/**
 * Whether a Redis store is wired up.
 *
 * The site is designed to work with no environment variables at all — without a store the page
 * simply shows GitHub's count and hides its own. Deploying should never be blocked on setting
 * up a database.
 */
export const kvConfigured = Boolean(KV_URL && KV_TOKEN);

async function kvFetch(path, body) {
  const res = await fetch(`${KV_URL}${path}`, {
    method: body ? 'POST' : 'GET',
    headers: {
      Authorization: `Bearer ${KV_TOKEN}`,
      ...(body ? { 'Content-Type': 'application/json' } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) throw new Error(`KV responded ${res.status}`);
  return res.json();
}

/** Page-driven download total, or `null` when there is no store or it is unreachable. */
export async function readTotal() {
  if (!kvConfigured) return null;
  try {
    const { result } = await kvFetch(`/get/${encodeURIComponent(COUNTER_KEY)}`);
    return Number(result ?? 0);
  } catch {
    return null;
  }
}

/**
 * Bumps the running total and a per-asset tally in one round trip.
 *
 * Never throws: a broken counter must not stop somebody downloading the app.
 */
export async function recordDownload(assetName) {
  if (!kvConfigured) return null;
  try {
    const out = await kvFetch('/pipeline', [
      ['INCR', COUNTER_KEY],
      ['INCR', `musicsm:downloads:asset:${assetName}`],
    ]);
    return Number(out?.[0]?.result ?? 0);
  } catch {
    return null;
  }
}

/**
 * Turns GitHub's (resettable) summed download count into the figure the page shows.
 *
 * A fixed offset from the `DOWNLOADS_BASELINE` env var is always added, so the number can be topped
 * up with **no database at all** — set it in the Vercel dashboard and redeploy. On top of that, when
 * a Redis store is configured the count also survives GitHub asset resets: if GitHub's total drops
 * below what we last saw, the old value is folded into a persistent baseline. Any error or missing
 * store degrades gracefully to `rawGitHubTotal + envOffset` — a counter must never break the page.
 */
export async function reconcileGithubTotal(currentTotal) {
  // Manual bump that needs no store: just an env var. e.g. DOWNLOADS_BASELINE=133 shows 133 more.
  const envOffset = Number(process.env.DOWNLOADS_BASELINE || 0) || 0;
  return reconcile(currentTotal, BASELINE_KEY, LAST_SEEN_KEY, envOffset);
}

/** [reconcileGithubTotal] for the desktop installers, on their own keys and with no env offset. */
export function reconcileDesktopTotal(currentTotal) {
  return reconcile(currentTotal, DESKTOP_BASELINE_KEY, DESKTOP_LAST_SEEN_KEY, 0);
}

async function reconcile(currentTotal, baselineKey, lastSeenKey, envOffset) {
  if (!kvConfigured) return currentTotal + envOffset;
  try {
    const out = await kvFetch('/pipeline', [
      ['GET', baselineKey],
      ['GET', lastSeenKey],
    ]);
    const baseline = Number(out?.[0]?.result ?? 0) || 0;
    const lastSeen = Number(out?.[1]?.result ?? 0) || 0;

    const writes = [];
    let newBaseline = baseline;
    if (currentTotal < lastSeen) {
      // A reset happened since last time: preserve the count that GitHub just threw away.
      newBaseline = baseline + lastSeen;
      writes.push(['SET', baselineKey, String(newBaseline)]);
    }
    if (currentTotal !== lastSeen) {
      writes.push(['SET', lastSeenKey, String(currentTotal)]);
    }
    if (writes.length) await kvFetch('/pipeline', writes);

    return newBaseline + currentTotal + envOffset;
  } catch {
    return currentTotal + envOffset;
  }
}

/**
 * Every release, newest first.
 *
 * One call rather than hitting `releases/latest` separately: the page needs the newest build for
 * the download button *and* the download totals from every release ever published, and both come
 * out of this single response.
 *
 * Capped at 100 releases, which is GitHub's maximum page size. Beyond that the totals would
 * silently undercount and this would need real pagination.
 */
export async function fetchReleases(repo = REPO) {
  const headers = {
    Accept: 'application/vnd.github+json',
    'User-Agent': 'musicsm-landing-page',
  };
  // Optional: lifts the unauthenticated 60-requests-per-hour limit, which serverless functions
  // share across a whole region. The edge cache on /api/release normally keeps us well under it.
  if (process.env.GITHUB_TOKEN) headers.Authorization = `Bearer ${process.env.GITHUB_TOKEN}`;

  const res = await fetch(
    `https://api.github.com/repos/${repo}/releases?per_page=100`,
    { headers },
  );
  if (res.status === 404) return [];
  if (!res.ok) throw new Error(`GitHub responded ${res.status}`);

  const body = await res.json();
  return Array.isArray(body) ? body : [];
}

/**
 * The build to actually offer people.
 *
 * Mirrors what `releases/latest` would have returned — drafts and pre-releases are skipped, so a
 * release candidate sitting at the top of the list never becomes the download everyone gets.
 */
export function latestStable(releases) {
  return releases.find((r) => !r.draft && !r.prerelease) ?? null;
}

const APK = /\.apk$/i;
const WEAR = /wear/i;

/** Just the installable builds — release notes, mapping files and checksums are not downloads. */
export function apkAssets(release) {
  return (release?.assets ?? []).filter((a) => APK.test(a.name));
}

/**
 * The phone builds. The Wear OS app ships in the same release (its file name says "Wear"), and
 * must never be what the main Android button — or the app's own updater — hands out.
 */
export function phoneApks(release) {
  return apkAssets(release).filter((a) => !WEAR.test(a.name));
}

/** The Wear OS builds in a release. */
export function wearApks(release) {
  return apkAssets(release).filter((a) => WEAR.test(a.name));
}

/**
 * The watch app to offer: from the newest stable release that has one, so a phone-only release
 * doesn't leave the Wear OS button with nothing to download.
 */
export function newestWearAsset(releases) {
  for (const release of releases) {
    if (release.draft || release.prerelease) continue;
    const asset = wearApks(release)[0];
    if (asset) return { release, asset };
  }
  return null;
}

/** Lifetime Wear OS APK downloads (already part of [totalApkDownloads]; shown as a split). */
export function totalWearDownloads(releases) {
  return sumDownloads(releases, wearApks);
}

/**
 * Lifetime APK downloads across every release.
 *
 * Deliberately not scoped to the current version: the interesting number is how many people have
 * installed MusicSM, and counting only the newest release would reset that to zero on every
 * single publish.
 */
export function totalApkDownloads(releases) {
  return sumDownloads(releases, apkAssets);
}

// The Windows MSI and macOS DMG today, plus the other formats a desktop build could add.
const INSTALLER = /\.(msi|exe|dmg|pkg|deb|rpm|appimage)$/i;

/** MusicSM Desktop's installers in a release of its repo. */
export function desktopAssets(release) {
  return (release?.assets ?? []).filter((a) => INSTALLER.test(a.name));
}

/** Lifetime desktop installer downloads across every release of the desktop repo. */
export function totalDesktopDownloads(releases) {
  return sumDownloads(releases, desktopAssets);
}

/** The installers each desktop download button offers. */
const PLATFORM_INSTALLER = { windows: /\.(msi|exe)$/i, mac: /\.(dmg|pkg)$/i };

export const isDesktopPlatform = (platform) => Object.hasOwn(PLATFORM_INSTALLER, platform);

/** A release's installers for one platform (`windows` or `mac`). */
export function platformAssets(release, platform) {
  const pattern = PLATFORM_INSTALLER[platform];
  return pattern ? (release?.assets ?? []).filter((a) => pattern.test(a.name)) : [];
}

/**
 * The installer to offer for a platform: from the newest stable release that has one, so a release
 * that shipped without, say, the Mac build doesn't leave that button with nothing to download.
 */
export function newestPlatformAsset(releases, platform) {
  for (const release of releases) {
    if (release.draft || release.prerelease) continue;
    const asset = platformAssets(release, platform)[0];
    if (asset) return { release, asset };
  }
  return null;
}

/** Where the desktop buttons go when there's no installer to hand out (yet). */
export const desktopReleasesUrl = () =>
  DESKTOP_REPO ? `https://github.com/${DESKTOP_REPO}/releases` : null;

function sumDownloads(releases, assetsOf) {
  return releases.reduce(
    (total, release) => total + assetsOf(release).reduce((n, a) => n + (a.download_count || 0), 0),
    0,
  );
}

/**
 * MusicSM Desktop's releases and lifetime downloads, the count reset-proofed like the APK total.
 * `downloads` is 0 when no desktop repo is configured or it has no releases yet (GitHub 404s a repo
 * that doesn't exist), and `null` when it can't be read right now. Never throws — the Android side
 * of the page must not depend on the desktop repo.
 */
export async function loadDesktop() {
  if (!DESKTOP_REPO) return { downloads: 0, releases: [] };
  try {
    const releases = await fetchReleases(DESKTOP_REPO);
    return { downloads: await reconcileDesktopTotal(totalDesktopDownloads(releases)), releases };
  } catch {
    return { downloads: null, releases: [] };
  }
}
