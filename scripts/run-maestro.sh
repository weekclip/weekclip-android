#!/usr/bin/env bash
#
# PRD-0008 Phase 4 — drive the installed app on a real device and report back.
#
# Why this exists: unit tests never construct the Activity, so nothing in CI
# today can tell you the app actually launches. `WeekclipRoutesTest` asserts the
# route table's shape; it cannot assert that Hilt injected, that Compose drew,
# or that the start destination is on screen. This script closes that gap, and
# does it in a form an agent can read: JUnit XML for the verdict, plus a
# screenshot, the failing step's view hierarchy as JSON, and the device logcat
# for every failure.
#
# Usage:
#   scripts/run-maestro.sh [--require-device] [--no-install] [flow-or-dir ...]
#
#   --require-device  Treat "no device attached" as a failure. Any automated
#                     caller MUST pass this. Without it the script skips, and a
#                     check that can only pass is a check that tests nothing —
#                     this repo already shipped one of those (`ktlintCheck ||
#                     true`) and it hid a broken build for weeks.
#   --no-install      Run against whatever build is already on the device.
#   flow-or-dir       Defaults to the whole `maestro/` directory.
#
# Exit: 0 pass (or skipped without --require-device), 1 failure.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

require_device=0
install=1

# Options first, then flow paths. Flows stay in "$@" rather than an array so
# this works on bash 3.2, where expanding an empty array under `set -u` is an
# error — macOS ships 3.2 and /usr/bin/env finds it.
while [ $# -gt 0 ]; do
  case "$1" in
    --require-device) require_device=1; shift ;;
    --no-install)     install=0; shift ;;
    -h|--help)        sed -n '2,26p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    -*)               echo "unknown option: $1" >&2; exit 1 ;;
    *)                break ;;
  esac
done

[ $# -eq 0 ] && set -- maestro

# Maestro is bundled telemetry-on; this repo opts out everywhere it runs.
export MAESTRO_CLI_NO_ANALYTICS=1
export MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true

# --- tools -------------------------------------------------------------------
# A missing tool is a setup error, not a skip: skipping here would silently
# report success on a machine that never ran anything.
MAESTRO="$(command -v maestro || true)"
[ -z "$MAESTRO" ] && [ -x "$HOME/.maestro/bin/maestro" ] && MAESTRO="$HOME/.maestro/bin/maestro"
if [ -z "$MAESTRO" ]; then
  echo "FAIL — maestro CLI not found." >&2
  echo "  curl -fsSL \"https://get.maestro.mobile.dev\" | bash" >&2
  exit 1
fi

ADB="$(command -v adb || true)"
for candidate in "${ANDROID_HOME:-}/platform-tools/adb" "$HOME/Library/Android/sdk/platform-tools/adb"; do
  [ -n "$ADB" ] && break
  [ -x "$candidate" ] && ADB="$candidate"
done
if [ -z "$ADB" ]; then
  echo "FAIL — adb not found. Set ANDROID_HOME or add platform-tools to PATH." >&2
  exit 1
fi

# --- device ------------------------------------------------------------------
# Only `device` state counts. `offline` and `unauthorized` both look like a
# connection and behave like nothing: unauthorized means the RSA prompt on the
# phone was never accepted.
# Read into an array without `mapfile` — macOS still ships bash 3.2, and
# /usr/bin/env bash finds it on any Mac without a newer bash installed.
serial_count=0
serials=""
while IFS= read -r line; do
  [ -z "$line" ] && continue
  serials="${serials}${line} "
  serial_count=$((serial_count + 1))
done < <("$ADB" devices | awk '$2 == "device" {print $1}')

if [ "$serial_count" -eq 0 ]; then
  unauth=$("$ADB" devices | awk '$2 == "unauthorized" {print $1}')
  echo
  echo "No Android device in 'device' state."
  if [ -n "$unauth" ]; then
    echo "  $unauth is attached but UNAUTHORIZED — accept the USB debugging"
    echo "  prompt on the phone (tick 'always allow')."
  else
    echo "  Attach a device over USB with developer options + USB debugging on."
    echo "  Emulators do not work on the current dev Mac — see maestro/README.md."
  fi
  if [ "$require_device" -eq 1 ]; then
    echo "FAIL — --require-device was passed." >&2
    exit 1
  fi
  echo "SKIP — no device (pass --require-device to make this a failure)."
  exit 0
fi

serial="${ANDROID_SERIAL:-$(printf '%s' "$serials" | awk '{print $1}')}"
if [ "$serial_count" -gt 1 ]; then
  echo "note: $serial_count devices attached, using $serial (set ANDROID_SERIAL to choose)"
fi
model="$("$ADB" -s "$serial" shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
release="$("$ADB" -s "$serial" shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')"
echo "device: $serial  $model  Android $release"

# --- wake --------------------------------------------------------------------
# A sleeping or locked phone does not fail loudly — it fails as an assertion
# about your app. The first real run of this script died on `id: screen-title`
# with a 26KB hierarchy that was entirely `keyguard` and always-on-display
# clock; the app was never in front. So wake it, try to dismiss a swipe lock,
# and refuse to run if the screen still is not ours.
"$ADB" -s "$serial" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
"$ADB" -s "$serial" shell input keyevent 82 >/dev/null 2>&1 || true
sleep 1

wakefulness=$("$ADB" -s "$serial" shell dumpsys power 2>/dev/null \
  | grep -m1 'mWakefulness=' | tr -d '\r' | sed 's/.*mWakefulness=//')
focus=$("$ADB" -s "$serial" shell dumpsys window 2>/dev/null \
  | grep -m1 'mCurrentFocus' | tr -d '\r')

locked=0
case "$focus" in
  *Keyguard*|*Bouncer*|*NotificationShade*) locked=1 ;;
esac

if [ "$wakefulness" != "Awake" ] || [ "$locked" -eq 1 ]; then
  echo "FAIL — the device screen is asleep or locked." >&2
  echo "  wakefulness: ${wakefulness:-unknown}" >&2
  echo "  focus:       ${focus:-unknown}" >&2
  echo "  Unlock it by hand. A PIN or pattern cannot be dismissed from adb," >&2
  echo "  and running anyway would report a keyguard as an app failure." >&2
  exit 1
fi

# --- install -----------------------------------------------------------------
if [ "$install" -eq 1 ]; then
  echo
  echo "== installing debug build =="
  ANDROID_SERIAL="$serial" ./gradlew --console=plain installDebug
fi

# --- run ---------------------------------------------------------------------
out="build/maestro"
rm -rf "$out"
mkdir -p "$out"

echo
echo "== running flows: $* =="
status=0
"$MAESTRO" --device "$serial" test \
  --format junit \
  --output "$out/report.xml" \
  --test-output-dir "$out/artifacts" \
  "$@" || status=$?

echo
if [ "$status" -ne 0 ]; then
  echo "FAIL — see $out/report.xml for the assertion that broke, and" >&2
  echo "$out/artifacts/ for the screenshot, that step's view hierarchy," >&2
  echo "and the device logcat." >&2
  exit 1
fi

echo "PASS — $out/report.xml"
