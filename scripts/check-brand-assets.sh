#!/usr/bin/env bash
#
# Brand assets — the checks that need the filesystem, not the JVM.
#
# `app/src/test/.../BrandAssetsTest.kt` does the geometry (is the mark inside
# the adaptive-icon keyline, do the three drawables still draw the same thing).
# This script covers what a unit test is the wrong tool for: whether the files
# are wired up, licensed, and free of the placeholder palette.
#
# Why bother. The launcher icon that shipped from the first skeleton commit
# until 2026-08-16 was a blue square in #3B6FE0 — a colour that appears in no
# design token — and `Color.kt` said "Placeholder palette" in a comment while
# the build happily emitted it. Nothing failed, because nothing was looking.
#
# What this checks:
#   1. Every R.font.* the code names exists in res/font/  (and nothing else does)
#   2. Every bundled font family carries its SIL OFL licence in the APK
#   3. The retired placeholder accent #3B6FE0 is gone
#   4. The launcher background is the design-system token, not a near-miss
#
# Usage:  scripts/check-brand-assets.sh [repo-root]
# Exit:   0 clean, 1 a check failed

set -euo pipefail

ROOT="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
cd "$ROOT"

FONT_DIR="app/src/main/res/font"
LICENSE_DIR="app/src/main/assets/licenses"
fail=0

echo "== 1/4  R.font.* references resolve, and every font file is referenced =="
# Comments are stripped first: this file's own prose names fonts, and so does
# Type.kt's header. A gate that fires on the documentation explaining it is a
# gate people switch off.
referenced=$(find app/src/main app/src/debug -name '*.kt' 2>/dev/null -print0 \
  | xargs -0 perl -0777 -pe 's{/\*.*?\*/}{}gs' \
  | sed 's://.*::' \
  | grep -oE 'R\.font\.[a-z0-9_]+' \
  | sed 's/R\.font\.//' | sort -u)

if [ -z "$referenced" ]; then
  echo "   FAIL: no R.font.* reference anywhere — the bundled fonts are dead weight" >&2
  fail=1
else
  for name in $referenced; do
    if [ ! -f "$FONT_DIR/$name.ttf" ]; then
      echo "   FAIL: code references R.font.$name but $FONT_DIR/$name.ttf is missing" >&2
      fail=1
    fi
  done

  # The other direction. A 400 KB face nobody names is 400 KB in every install.
  for f in "$FONT_DIR"/*.ttf; do
    [ -e "$f" ] || continue
    base=$(basename "$f" .ttf)
    if ! printf '%s\n' "$referenced" | grep -qx "$base"; then
      echo "   FAIL: $f is bundled but no code references R.font.$base" >&2
      fail=1
    fi
  done
  [ "$fail" -eq 0 ] && echo "   clean ($(printf '%s\n' "$referenced" | wc -l | tr -d ' ') faces)"
fi

echo "== 2/4  SIL OFL licences ship with the fonts =="
# OFL 1.1 clause 2: the licence must travel with the font. Shipping it in
# assets/ puts it inside the APK rather than only in the repo, which is what
# "distributed" means for an app.
#
# "<res/font file prefix>:<licence file>". Adding a family means adding a line
# here, which is the point — vendoring a face and forgetting its licence should
# take a deliberate edit to make the build pass.
missing_license=0
for entry in "inter_:Inter-OFL.txt" "space_grotesk_:SpaceGrotesk-OFL.txt"; do
  prefix="${entry%%:*}"
  license="${entry##*:}"
  # Not bundled -> nothing to license.
  ls "$FONT_DIR"/"$prefix"*.ttf >/dev/null 2>&1 || continue
  if [ ! -s "$LICENSE_DIR/$license" ]; then
    echo "   FAIL: $FONT_DIR/$prefix* is bundled but $LICENSE_DIR/$license is missing or empty" >&2
    missing_license=1
  fi
done
if [ "$missing_license" -ne 0 ]; then
  fail=1
else
  echo "   clean"
fi

echo "== 3/4  retired placeholder accent =="
# #3B6FE0 was the skeleton's stand-in accent. The real one is #5B53FF
# (color.accent.default). Case-insensitive: XML and Kotlin spell hex differently.
#
# Comments are stripped first, and that is not a nicety — the first thing the
# un-stripped version flagged was Color.kt's own header explaining WHY #3B6FE0
# is retired, and ic_launcher_foreground.xml's note on what it replaced. Neither
# is compiled into anything. check-no-payment-strings.sh has the same paragraph
# for the same reason; this is that lesson, not a rediscovery of it.
placeholder=""
while IFS= read -r f; do
  case "$f" in
    *.kt)  stripped=$(perl -0777 -pe 's{/\*.*?\*/}{}gs' "$f" | sed 's://.*::') ;;
    *.xml) stripped=$(perl -0777 -pe 's{<!--.*?-->}{}gs' "$f") ;;
    *)     continue ;;
  esac
  if match=$(printf '%s\n' "$stripped" | grep -niE '#(FF)?3B6FE0' || true); then
    [ -n "$match" ] && placeholder="${placeholder}${f}: ${match}"$'\n'
  fi
done < <(find app/src \( -name '*.kt' -o -name '*.xml' \) 2>/dev/null)

if [ -n "$placeholder" ]; then
  printf '%s' "$placeholder" | sed 's/^/   FOUND: /' >&2
  echo "   FAIL: the placeholder accent is back. Use WeekclipAccent / #5B53FF." >&2
  fail=1
else
  echo "   clean"
fi

echo "== 4/4  launcher background is a token =="
# color.bg.panel (dark) — the same plate the design system's favicon.svg uses.
if grep -q '#0C0E16' app/src/main/res/values/ic_launcher_background.xml; then
  echo "   clean"
else
  echo "   FAIL: ic_launcher_background is not #0C0E16 (color.bg.panel, dark)" >&2
  fail=1
fi

echo
if [ "$fail" -ne 0 ]; then
  echo "FAIL — brand assets are not wired up correctly." >&2
  exit 1
fi

echo "PASS — brand assets bundled, licensed, and free of placeholder colours."
