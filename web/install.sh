#!/bin/bash
# Hymn (formerly MusicSM Desktop) for macOS, installed with one command:
#
#   curl -fsSL https://music-sm.vercel.app/install.sh | bash
#
# Downloads the newest release, puts Hymn in Applications and opens it. The app isn't
# signed by Apple yet, so a DMG downloaded in a browser gets the "can't be opened" prompt; files
# fetched with curl aren't marked as downloaded from the internet, so this way it just opens.
# A copy installed under the old name is closed and removed, so updating leaves a single app.
#
# Everything runs inside main(), called on the last line, so a half-downloaded script does nothing.

set -euo pipefail

# The bundle ID both names ship with; only an app carrying it is treated as an old copy to remove.
BUNDLE_ID="com.example.musicsmd"
LEGACY_NAME="MusicSM Desktop"

main() {
  local app_name="Hymn"
  local site="https://music-sm.vercel.app"

  if [ "$(uname -s)" != "Darwin" ]; then
    fail "This installer is for macOS. On Windows, get $app_name from the Microsoft Store: winget install --id XPFFD9P6NJSHH5 --source msstore"
  fi
  # hw.optional.arm64 is 1 on Apple silicon, even when this shell runs under Rosetta.
  if [ "$(sysctl -n hw.optional.arm64 2>/dev/null || echo 0)" != "1" ]; then
    fail "$app_name needs a Mac with Apple silicon (M1 or newer)."
  fi

  local tmp
  tmp="$(mktemp -d "${TMPDIR:-/tmp}/hymn.XXXXXX")"
  local dmg="$tmp/Hymn.dmg"
  local mnt="$tmp/mount"
  # Unmount and delete the download however the script ends.
  trap 'hdiutil detach "'"$mnt"'" -quiet >/dev/null 2>&1 || true; rm -rf "'"$tmp"'"' EXIT

  say "Downloading $app_name..."
  # The same link as the site's "Download for Mac" button: the newest DMG, counted like a click.
  curl -fL --progress-bar "$site/api/download?desktop=mac" -o "$dmg" \
    || fail "The download failed. Check your connection and try again."

  mkdir -p "$mnt"
  hdiutil attach -nobrowse -readonly -noautoopen -mountpoint "$mnt" "$dmg" >/dev/null 2>&1 \
    || fail "That wasn't a usable disk image. Try again later, or download it from $site."

  local src=""
  for candidate in "$mnt"/*.app; do
    if [ -d "$candidate" ]; then
      src="$candidate"
      break
    fi
  done
  [ -n "$src" ] || fail "The disk image had no app in it."
  # The DMG's own name, so an older release still installs under the name it was built with.
  app_name="$(basename "$src" .app)"

  # /Applications is writable for admin accounts; a standard account gets its own Applications.
  local dest_dir="/Applications"
  if [ ! -w "$dest_dir" ]; then
    dest_dir="$HOME/Applications"
    mkdir -p "$dest_dir"
  fi
  local dest="$dest_dir/$app_name.app"
  local staging="$dest_dir/.$app_name.app.installing"

  local name
  for name in "$app_name" "$LEGACY_NAME"; do
    if pgrep -xq "$name"; then
      say "Closing $name..."
      osascript -e "quit app \"$name\"" >/dev/null 2>&1 || true
      sleep 2
    fi
  done

  say "Installing to $dest_dir..."
  # Copied next to the old version first, so a failed copy leaves the installed app alone.
  rm -rf "$staging"
  ditto "$src" "$staging" || { rm -rf "$staging"; fail "Couldn't copy the app into $dest_dir."; }
  rm -rf "$dest"
  mv "$staging" "$dest"
  xattr -dr com.apple.quarantine "$dest" 2>/dev/null || true

  remove_legacy "$app_name"

  say "Done. Opening $app_name."
  open "$dest"
}

# Removes copies installed under the old name, so updating doesn't leave two apps sharing one
# bundle ID. Only apps that really carry our bundle ID are touched.
remove_legacy() {
  local installed_name="$1"
  [ "$LEGACY_NAME" != "$installed_name" ] || return 0
  local dir old id
  for dir in "/Applications" "$HOME/Applications"; do
    old="$dir/$LEGACY_NAME.app"
    [ -d "$old" ] || continue
    id="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$old/Contents/Info.plist" 2>/dev/null || true)"
    [ "$id" = "$BUNDLE_ID" ] || continue
    if rm -rf "$old" 2>/dev/null && [ ! -e "$old" ]; then
      say "Removed the old $LEGACY_NAME from $dir (your music and settings are kept)."
    else
      say "Couldn't remove the old $old; you can drag it to the Trash."
    fi
  done
}

say() {
  printf '%s\n' "$*"
}

fail() {
  printf 'Error: %s\n' "$*" >&2
  exit 1
}

main
