# Hymn download page

A single static page plus three serverless functions. No framework, no build step and no npm
dependencies — it runs on Node's built-in `fetch`.

```
web/
├── index.html        the page
├── styles.css        palette, typography and glass lifted from ui/theme/Palette.kt
├── app.js            fills the page in from the API; pure progressive enhancement
├── icon.png          the launcher icon, used for the favicon and nav
├── icon-large.jpg    the hero artwork, cropped from the 1024px source icon
├── install.sh        one-command Mac install of Hymn (curl … | bash)
└── api/
    ├── _lib.js       shared GitHub + Redis helpers (the _ prefix keeps it off the router)
    ├── release.js    latest release + lifetime totals, edge-cached for 5 minutes
    ├── stats.js      the live page-click counter, never cached
    └── download.js   counts the click, then redirects to the APK, Wear OS APK or desktop installer on GitHub
```

## Deploying

1. Push this repo to GitHub.
2. In Vercel, **Add New → Project**, import the repo, and set **Root Directory** to `web`.
3. Leave the framework preset as **Other**. There is nothing to build.
4. Deploy.

That is the whole setup. With no environment variables at all the page works and shows GitHub's
own download count — the click counter simply stays hidden.

## Download counting

Two numbers are shown, because neither one is a superset of the other:

| Counter | Source | What it means |
| --- | --- | --- |
| **Downloads from GitHub** | GitHub's `download_count` | Every APK fetch across **every release ever published**, however it was reached — plus every MusicSM Desktop installer (`.msi`, `.dmg`) from the desktop repo's releases (see below). Ground truth for the files, and impossible to reset or inflate from here. Counted lifetime rather than per-version deliberately: scoping it to the newest release would reset the number to zero on every publish. |
| **Downloads from this page** | `INCR` in Redis | Clicks that actually started on this page, all time. |

`/api/download` increments the counter and then **302s to GitHub** rather than proxying the file.
Streaming ~6 MB through a function for every download would be slow and costly when GitHub's CDN
already does it well, and proxying would hide the download from GitHub's own counter.

`?id=` is matched against the assets of *every* release, so a link to an older version still
resolves to that exact build. An unrecognised id falls back to the newest phone APK rather than
erroring.

### Wear OS app

The watch app is attached to the **same release** as the phone APK. Any `.apk` with `wear` in its
name (e.g. `MusicSM.Wear.v3.0.0.apk`) is treated as the watch build:

- It never becomes the main **Download for Android** file, whatever order the assets were uploaded
  in. The app's own updater skips it the same way.
- **Download for Wear OS** links to `/api/download?wear=1`, which serves the newest stable release
  that has a watch build (`&id=` isn't needed; the page pins the exact file). Before one exists the
  button is muted and opens the releases page.
- `/api/release` describes it under `wear` (`null` before the first one). Its downloads are part of
  the Android total; `downloads.wear` carries its share for the hover split.
- Sign it with the **same key** as the phone APK, or the watch and phone can't talk.

### Hymn for desktop

The desktop app is released from its own repo (`DESKTOP_GITHUB_REPO`, default
`workingpayload/Hymn-Desktop`).

**Download buttons.** Under the Android button sit **Download for Windows** and **Download for Mac**,
plain links to `/api/download?desktop=windows|mac`. That picks the `.msi` / `.dmg` from the newest
stable desktop release that has one (`&id=` pins a specific file, as for the APK), counts the click,
and 302s to GitHub. With no desktop release yet — or GitHub unreachable — it sends the visitor to
the desktop releases page instead. `/api/release` describes both builds under `desktop` (`null`
before the first release), and the page mutes the buttons and says "coming soon" until then.

**In the total.** `/api/release` adds the desktop installers' downloads to the GitHub figure.
`downloads: { android, desktop }` in the response carries the split, which the page shows when you
hover the card. Desktop clicks count towards "Downloads from this page" too.

- Until that repo exists (or while it is private) GitHub answers 404, which counts as 0.
- If it can't be read (rate limit, outage), the Android figure is served alone with a one-minute
  cache instead of five, so the total catches up quickly.
- The desktop count has its own reset protection keys (below), so the Android history is untouched.
- `DOWNLOADS_OVERRIDE` still sets the exact number shown, desktop included.
- `DESKTOP_GITHUB_REPO=none` hides the desktop buttons and leaves desktop out of the total.

**One-command Mac install.** The Mac app isn't signed by Apple yet, so a DMG downloaded in a browser
gets macOS's "can't be opened" prompt. Under the buttons, the page offers
`curl -fsSL https://music-sm.vercel.app/install.sh | bash` instead: `install.sh` fetches the newest
DMG through `/api/download?desktop=mac` (so it's counted like a click), copies the app into
Applications (or `~/Applications` for a standard account), and opens it. Files fetched with curl
aren't marked as downloaded from the internet, so macOS opens the app without the prompt. It checks
for Apple silicon first, closes a running copy before replacing it, and puts nothing in place until
the copy has finished. It's served as `text/plain` (see `vercel.json`) so "See what it does" shows
it in the browser, and `.gitattributes` keeps it LF, since a CRLF script breaks in bash. The command
stays hidden until there is a Mac build.

**Microsoft Store (Windows).** MusicSM Desktop is also on the
[Microsoft Store](https://apps.microsoft.com/detail/xpffd9p6njshh5) (product id `XPFFD9P6NJSHH5`).
The desktop row has a **Get it from Microsoft Store** button, and the page offers
`winget install --id XPFFD9P6NJSHH5 --source msstore` with a Copy button. Store builds are signed
and update themselves, so there's no SmartScreen prompt. Store installs go through Microsoft, not
`/api/download`, so they aren't in the GitHub or page counts. Both stay visible even before there is
a GitHub desktop build.

### Turning on the page counter

It needs any Redis with an Upstash-compatible REST API:

1. In your Vercel project: **Storage → Create Database → Upstash for Redis** (there is a free tier).
2. Connect it to the project. Vercel injects `KV_REST_API_URL` and `KV_REST_API_TOKEN`
   automatically — those are the only two names the code looks for.
3. Redeploy.

The counter card appears on its own once those variables exist. If Redis is ever unreachable the
functions swallow the error and still serve the download: a broken counter must never stop
somebody installing the app.

Keys used:

- `musicsm:downloads:total`
- `musicsm:downloads:asset:<file name>`
- `musicsm:downloads:baseline`, `musicsm:downloads:githubseen` — keep the APK total from dropping
  when GitHub resets an asset's count
- `musicsm:desktop:downloads:baseline`, `musicsm:desktop:downloads:githubseen` — the same for the
  desktop installers

## Environment variables

All optional.

| Variable | Default | Why you might set it |
| --- | --- | --- |
| `GITHUB_REPO` | `workingpayload/Hymn` | Point the page at a different repo. |
| `DESKTOP_GITHUB_REPO` | `workingpayload/Hymn-Desktop` | Where the Hymn desktop app is released, if you name that repo differently; `none` hides the desktop buttons and leaves desktop downloads out of the total. |
| `GITHUB_TOKEN` | — | Lifts GitHub's 60-requests-per-hour unauthenticated limit, which serverless functions share across a region. The 5-minute edge cache normally keeps usage far below it, so this is only worth adding if you see `502`s from `/api/release`. |
| `KV_REST_API_URL` | — | Set for you by the Upstash integration. |
| `KV_REST_API_TOKEN` | — | Set for you by the Upstash integration. |

## Publishing a new version

The page always reads `releases/latest`, so shipping an update is just:

1. Build the release APK (and the Wear OS APK, signed with the same key).
2. Create a GitHub release tagged `vX.Y.Z` and attach the `.apk` (plus the watch `.apk`, with
   `Wear` in its file name).

Version, file size, release date and download count all update on their own within five minutes.
Attach more than one APK (per-ABI splits, for instance) and the extras appear as secondary chips
under the main button.

## Caching, and the one rule to remember

`index.html` is never cached, so a deploy is visible immediately. The assets it references are
cached differently on purpose:

| Asset | Policy | Why |
| --- | --- | --- |
| `index.html`, `/api/stats` | no cache | must always be current |
| `styles.css`, `app.js` | `no-cache` (revalidate every load) | a few KB, and a stale copy paired with fresh HTML breaks the page |
| `icon.png`, `icon-large.jpg` | `immutable`, one year | heavy, and rarely change |

**If you change an image, bump its `?v=` in `index.html`.** The images are served `immutable`, so
browsers will not re-request them otherwise — the query string is what makes the URL new.

This matters because the two are not independent. The HTML and the CSS/JS are written against
each other, and an earlier version of this page cached all three for an hour: returning visitors
got new HTML with hour-old CSS and JS, which stretched the hero image and left the download
counter showing a placeholder dash. `no-cache` on the two text assets removes that whole class of
bug for the cost of one 304 per visit.

## Local development

```bash
npm i -g vercel
cd web
vercel dev
```

`vercel dev` is required rather than any static server, because `/api/*` needs the functions
runtime.
