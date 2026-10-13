/**
 * Fills the page in from the two endpoints and keeps the download counters honest.
 *
 * Everything here is progressive enhancement. The markup ships with a working download link and
 * sensible placeholder text, so a failed fetch or a blocked script degrades to "the button still
 * downloads the app" rather than a broken page.
 */

const RELEASES_URL = "https://github.com/workingpayload/Hymn/releases";

const el = (id) => document.getElementById(id);
const prefersReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

const numberFormat = new Intl.NumberFormat();

function formatBytes(bytes) {
  if (!Number.isFinite(bytes) || bytes <= 0) return null;
  const mb = bytes / (1024 * 1024);
  return mb >= 1024 ? `${(mb / 1024).toFixed(2)} GB` : `${mb.toFixed(1)} MB`;
}

function formatDate(iso) {
  if (!iso) return null;
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return null;
  return new Intl.DateTimeFormat(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
  }).format(date);
}

/**
 * Counts up to [value], easing out.
 *
 * The animation is the only reason these numbers draw attention at all, but it is also the sort
 * of thing that makes a page feel cheap if it runs when somebody has asked for less motion — so
 * it snaps instead.
 */
function setCount(node, value) {
  if (!node || !Number.isFinite(value)) return;

  const from = Number(node.dataset.count || 0);
  node.dataset.count = String(value);

  if (prefersReducedMotion || from === value) {
    node.textContent = numberFormat.format(value);
    return;
  }

  const duration = 900;
  const start = performance.now();

  const tick = (now) => {
    const t = Math.min((now - start) / duration, 1);
    const eased = 1 - Math.pow(1 - t, 3);
    node.textContent = numberFormat.format(Math.round(from + (value - from) * eased));
    if (t < 1) requestAnimationFrame(tick);
  };

  requestAnimationFrame(tick);
}

/** Bumps a counter by one without waiting for the server, so the click feels acknowledged. */
function bump(node) {
  if (!node) return;
  setCount(node, Number(node.dataset.count || 0) + 1);
}

function showNoRelease(message) {
  el("release-pill").textContent = message;
  el("download-meta").textContent =
    "The download will appear here as soon as a build is published.";

  const button = el("download-btn");
  // Still a live link — it just points at the releases page instead of a file, so somebody who
  // arrived expecting a download has somewhere useful to go.
  button.classList.add("is-muted");
  button.href = RELEASES_URL;
  button.target = "_blank";
  button.rel = "noopener noreferrer";
  el("download-label").textContent = "Watch for releases";
}

function renderRelease(data) {
  const release = data.release;
  const primary = release.assets[0];

  el("release-pill").textContent = `${release.version} · free and open source`;
  el("release-link").href = release.htmlUrl || RELEASES_URL;
  el("stat-version").textContent = release.version;
  el("footer-version").textContent = release.version;

  const released = formatDate(release.publishedAt);
  el("stat-released").textContent = released ? `Released ${released}` : "";

  if (!primary) {
    showNoRelease(`${release.version} · no APK attached`);
    return;
  }

  el("download-btn").href = primary.href;
  el("download-btn").classList.remove("is-muted");
  el("download-label").textContent = "Download for Android";

  const size = formatBytes(primary.size);
  el("download-meta").textContent = [
    primary.name,
    size,
    released && `released ${released}`,
    "Android 7.0+",
  ]
    .filter(Boolean)
    .join(" · ");

  // More than one build (per-ABI splits, say) — offer the rest quietly rather than making the
  // visitor choose before they can do the obvious thing.
  const alternatives = release.assets.slice(1);
  const list = el("alt-builds");
  list.innerHTML = "";
  for (const asset of alternatives) {
    const assetSize = formatBytes(asset.size);
    const item = document.createElement("li");
    const link = document.createElement("a");
    link.href = asset.href;
    link.textContent = assetSize ? `${asset.name} · ${assetSize}` : asset.name;
    item.appendChild(link);
    list.appendChild(item);
  }
}

/** The GitHub total covers both apps; the note says so, and hovering the card shows the split. */
function describeSplit(downloads) {
  const card = el("stat-github")?.parentElement;
  if (!card || !downloads || !Number.isFinite(downloads.android)) return;
  const android = Number.isFinite(downloads.wear)
    ? `Android ${numberFormat.format(downloads.android)} (Wear OS ${numberFormat.format(downloads.wear)})`
    : `Android ${numberFormat.format(downloads.android)}`;
  const parts = [android];
  if (Number.isFinite(downloads.desktop)) {
    parts.push(`Desktop ${numberFormat.format(downloads.desktop)}`);
  }
  card.title = parts.join(" · ");

  const note = card.querySelector(".note");
  if (note && downloads.desktop > 0) {
    note.textContent = "Android and desktop, every release, all time, counted by GitHub itself";
  }
}

const DESKTOP_BUTTONS = { windows: "desktop-windows", mac: "desktop-mac" };

/**
 * The Wear OS button. Before a watch build is published it stays a muted link to the releases
 * page (which is where /api/download?wear=1 sends people then too), so it's never a dead end.
 */
function renderWear(data) {
  const button = el("wear-btn");
  if (!button || !("wear" in data)) return;
  const build = data.wear;
  button.classList.toggle("is-muted", !build);
  if (!build) {
    button.href = RELEASES_URL;
    button.target = "_blank";
    button.rel = "noopener noreferrer";
    button.title = "Not published yet — opens the releases page";
    el("wear-meta").textContent = "Hymn for Wear OS is coming soon.";
    return;
  }
  button.href = build.href;
  const size = formatBytes(build.size);
  button.title = size ? `${build.name} · ${size}` : build.name;
  const released = formatDate(build.publishedAt);
  el("wear-meta").textContent = [
    `Hymn for Wear OS ${build.version}`,
    size,
    released && `released ${released}`,
    "Wear OS 3+",
    "needs the phone app",
  ]
    .filter(Boolean)
    .join(" · ");
}

/**
 * The Windows and Mac buttons, for MusicSM Desktop. Before its first release they stay muted links
 * to the desktop releases page, so they're never dead ends.
 */
function renderDesktop(data) {
  // Absent when the desktop repo couldn't be read: the static links still resolve server-side.
  if (!("desktop" in data)) return;
  const section = document.querySelector(".desktop-cta");
  if (!section) return;
  if (!data.desktop && !data.desktopReleasesUrl) {
    section.hidden = true; // desktop turned off (DESKTOP_GITHUB_REPO=none)
    return;
  }

  for (const [platform, id] of Object.entries(DESKTOP_BUTTONS)) {
    const button = el(id);
    const build = data.desktop?.[platform];
    button.classList.toggle("is-muted", !build);
    if (build) {
      button.href = build.href;
      const size = formatBytes(build.size);
      button.title = size ? `${build.name} · ${size}` : build.name;
    } else {
      button.href = data.desktopReleasesUrl;
      button.target = "_blank";
      button.rel = "noopener noreferrer";
      button.title = "Not published yet — opens the desktop releases page";
    }
  }

  const newest = [data.desktop?.windows, data.desktop?.mac]
    .filter(Boolean)
    .sort((a, b) => String(b.publishedAt).localeCompare(String(a.publishedAt)))[0];
  // The Mac command installs the newest Mac build, so it waits until there is one.
  const macInstall = el("mac-install");
  if (macInstall) macInstall.hidden = !data.desktop?.mac;
  if (!newest) {
    el("desktop-meta").textContent = "MusicSM Desktop for Windows and Mac is coming soon.";
    return;
  }
  const released = formatDate(newest.publishedAt);
  el("desktop-meta").textContent = [
    `MusicSM Desktop ${newest.version}`,
    released && `released ${released}`,
    "Windows 10/11 (64-bit)",
    "Macs with Apple silicon",
  ]
    .filter(Boolean)
    .join(" · ");
}

async function loadRelease() {
  try {
    const res = await fetch("/api/release");
    if (!res.ok) throw new Error(`release endpoint returned ${res.status}`);

    const data = await res.json();

    // A lifetime figure across every release, so it survives each new version rather than
    // resetting to zero. Shown even when there is no downloadable build right now.
    setCount(el("stat-github"), data.githubDownloads);
    describeSplit(data.downloads);
    renderDesktop(data);
    renderWear(data);

    if (!data.release) {
      showNoRelease("No build published yet");
      return;
    }
    renderRelease(data);
  } catch {
    // The static link already points at /api/download, which resolves the asset server-side, so
    // the button keeps working even though we could not describe the build.
    el("release-pill").textContent = "Latest build";
    el("download-meta").textContent =
      "Couldn't load release details. The download button still works.";
  }
}

async function loadStats() {
  try {
    const res = await fetch("/api/stats");
    if (!res.ok) return;

    const data = await res.json();
    if (!data.enabled || data.page === null) return;

    el("stat-page-card").hidden = false;
    setCount(el("stat-page"), data.page);
  } catch {
    /* No counter configured, or it is briefly unreachable — the card just stays hidden. */
  }
}

/**
 * Counting the click.
 *
 * The increment happens server-side inside `/api/download`, which is what makes it trustworthy.
 * This only does two things on top: paint the optimistic +1 immediately, and re-read the real
 * value a moment later so the displayed number settles on the truth.
 */
function watchDownloads() {
  el("download-btn").addEventListener("click", (event) => {
    // While there is no APK the button is a link to the releases page, not a download.
    if (event.currentTarget.classList.contains("is-muted")) return;
    onDownload();
  });
  el("alt-builds").addEventListener("click", (event) => {
    if (event.target.closest("a")) onDownload();
  });
  for (const id of [...Object.values(DESKTOP_BUTTONS), "wear-btn"]) {
    el(id)?.addEventListener("click", (event) => {
      // Muted, it's a link to the releases page rather than a download.
      if (!event.currentTarget.classList.contains("is-muted")) onDownload();
    });
  }
}

function onDownload() {
  bump(el("stat-github"));
  if (!el("stat-page-card").hidden) bump(el("stat-page"));
  // GitHub's own count lags by a minute or so, so only the page counter is worth re-reading.
  setTimeout(loadStats, 2500);
}

/**
 * Support: the "Support me" tab opens a chooser dialog that asks which UPI app to pay with, then
 * deep-links into it. Each app has its own URL scheme; "Any UPI app" uses the generic `upi://`
 * intent so Android shows its own picker.
 */
function setupSupport() {
  const dialog = el("support-dialog");
  if (!dialog) return;

  const vpaEl = el("upi-vpa");
  const copyButton = el("copy-upi");
  const closeButton = el("support-close");
  const id = (dialog.dataset.vpa || vpaEl?.textContent || "").trim();
  const name = dialog.dataset.name || "";

  // pa/pn/cu build the standard UPI payment request; only the scheme differs per app.
  const params = `pa=${encodeURIComponent(id)}&pn=${encodeURIComponent(name)}&cu=INR`;
  const schemes = {
    gpay: `tez://upi/pay?${params}`,
    phonepe: `phonepe://pay?${params}`,
    paytm: `paytmmp://pay?${params}`,
    any: `upi://pay?${params}`,
  };

  const openDialog = (event) => {
    if (event) event.preventDefault();
    if (typeof dialog.showModal === "function") dialog.showModal();
    else dialog.setAttribute("open", "");
  };
  const closeDialog = () => {
    if (typeof dialog.close === "function") dialog.close();
    else dialog.removeAttribute("open");
  };

  // Any link pointing at #support (nav tab + footer) opens the chooser instead of scrolling.
  document.querySelectorAll('a[href="#support"]').forEach((link) => {
    link.addEventListener("click", openDialog);
  });

  closeButton?.addEventListener("click", closeDialog);
  // Click on the backdrop (outside the inner card) closes it.
  dialog.addEventListener("click", (event) => {
    if (event.target === dialog) closeDialog();
  });

  dialog.querySelectorAll(".app-btn").forEach((btn) => {
    btn.addEventListener("click", () => {
      const url = schemes[btn.dataset.app] || schemes.any;
      window.location.href = url;
    });
  });

  if (copyButton && vpaEl) {
    copyButton.addEventListener("click", async () => {
      let ok = true;
      try {
        await navigator.clipboard.writeText(id);
      } catch {
        ok = false;
        const range = document.createRange();
        range.selectNodeContents(vpaEl);
        const selection = window.getSelection();
        selection.removeAllRanges();
        selection.addRange(range);
      }
      const original = copyButton.textContent;
      copyButton.textContent = ok ? "Copied" : "Select it";
      copyButton.classList.add("is-done");
      setTimeout(() => {
        copyButton.textContent = original;
        copyButton.classList.remove("is-done");
      }, 1800);
    });
  }
}

/** Copies an install command, falling back to selecting it where the clipboard is blocked. */
function setupInstallCommands() {
  document.querySelectorAll(".copy-btn[data-copy]").forEach((button) => {
    const command = el(button.dataset.copy);
    if (!command) return;

    button.addEventListener("click", async () => {
      let ok = true;
      try {
        await navigator.clipboard.writeText(command.textContent.trim());
      } catch {
        ok = false;
        const range = document.createRange();
        range.selectNodeContents(command);
        const selection = window.getSelection();
        selection.removeAllRanges();
        selection.addRange(range);
      }
      button.textContent = ok ? "Copied" : "Select it";
      button.classList.add("is-done");
      setTimeout(() => {
        button.textContent = "Copy";
        button.classList.remove("is-done");
      }, 1800);
    });
  });
}

loadRelease();
loadStats();
watchDownloads();
setupSupport();
setupInstallCommands();
