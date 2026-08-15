#!/usr/bin/env bash
#
# Lend this Mac's network — and with it the WireGuard tunnel — to a USB-attached
# phone, so the phone can reach the dev tier.
#
# Why this exists (#148, measured 2026-08-15):
#
#   The dev tier is behind a Cloudflare WAF that allows exactly one address, the
#   WireGuard egress (superrepo `docs/ops/security-topology-161.md` §3). The Mac
#   has a VPN peer; **the phone does not** — peers are leo + one colleague, and
#   nobody is going to burn one on a test device. So a phone on ordinary Wi-Fi
#   gets `403` and a Cloudflare HTML page from every `*.weekclip.dev` call.
#
#   That failure is worse than it sounds, because the app folds 403 into
#   `AppError.Unauthorized` and renders "Your session has ended". A network
#   problem looks exactly like a session problem, and the session code is the
#   thing you were trying to test.
#
#   `adb reverse` + a local CONNECT proxy fixes it without touching the phone's
#   Wi-Fi or installing anything: the phone's proxy points at localhost, adb
#   forwards that to this Mac, and the Mac's traffic already exits through the
#   tunnel.
#
# Usage:
#   scripts/device-net-via-mac.sh          # set it up and hold it open (Ctrl-C to stop)
#   scripts/device-net-via-mac.sh --stop   # clean up after a crash
#   scripts/device-net-via-mac.sh --check   # report state and exit
#
# ⚠️ This sets a **device-wide** HTTP proxy, not an app-scoped one. While it runs,
# EVERYTHING on the phone goes through this Mac and out the VPN — not just the
# app under test. Observed in one short run: Samsung account sync, Naver, and
# image CDNs all came through the proxy log. On a personal phone that is worth
# knowing before you leave it running.
#
# It is also why the normal mode holds the terminal and cleans up on exit: a
# phone left pointing at a proxy that is gone has no internet at all, and the
# person holding it will not connect that to a script someone ran an hour ago.
# If it ever does get left behind, `--stop` fixes it.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

PORT="${WEEKCLIP_DEVICE_PROXY_PORT:-8899}"
# The only address the dev WAF lets through. Kept here so the check below can
# say something useful rather than just "it did not work".
VPN_EGRESS="158.247.237.200"

mode="run"
case "${1:-}" in
  --stop) mode="stop" ;;
  --check) mode="check" ;;
  "") ;;
  *) echo "unknown option: $1" >&2; exit 2 ;;
esac

ADB="$(command -v adb || true)"
for candidate in "${ANDROID_HOME:-}/platform-tools/adb" "$HOME/Library/Android/sdk/platform-tools/adb"; do
  [ -n "$ADB" ] && break
  [ -x "$candidate" ] && ADB="$candidate"
done
if [ -z "$ADB" ]; then
  echo "FAIL — adb not found. Set ANDROID_HOME or add platform-tools to PATH." >&2
  exit 1
fi

device_count=$("$ADB" devices | awk 'NR>1 && $2=="device"' | wc -l | tr -d ' ')
if [ "$device_count" -eq 0 ]; then
  echo "FAIL — no device attached. Plug the phone in and accept the USB-debugging prompt." >&2
  exit 1
fi

cleanup() {
  # Order matters: unset the phone's proxy FIRST. If this script dies between
  # tearing down the tunnel and clearing the setting, the phone is left pointing
  # at a port that answers nothing.
  "$ADB" shell settings put global http_proxy :0 >/dev/null 2>&1 || true
  "$ADB" reverse --remove "tcp:$PORT" >/dev/null 2>&1 || true
  if [ -n "${proxy_pid:-}" ]; then
    kill "$proxy_pid" >/dev/null 2>&1 || true
  fi
}

report() {
  echo "  phone http_proxy : $("$ADB" shell settings get global http_proxy | tr -d '\r')"
  echo "  adb reverse      : $("$ADB" reverse --list | grep -c "tcp:$PORT" || true) entry(ies) for tcp:$PORT"
  echo "  mac egress       : $(curl -s --max-time 10 https://api.ipify.org || echo '(unreachable)')"
}

if [ "$mode" = "stop" ]; then
  cleanup
  echo "stopped."
  report
  exit 0
fi

if [ "$mode" = "check" ]; then
  report
  exit 0
fi

mac_egress="$(curl -s --max-time 10 https://api.ipify.org || true)"
if [ "$mac_egress" != "$VPN_EGRESS" ]; then
  # Not fatal: a phone borrowing a Mac that is off the VPN still gets working
  # internet, which is useful on its own. But it will NOT get the dev tier, and
  # saying so now saves the next hour.
  echo "⚠️  this Mac egresses as '${mac_egress:-unknown}', not the VPN's $VPN_EGRESS."
  echo "    The phone will get internet but still 403 on *.weekclip.dev."
  echo "    Turn WireGuard on first if that is what you are here for."
  echo
fi

trap cleanup EXIT
# Ctrl-C is how this is meant to end, so it must not look like a failure.
# Without this, `wait` returns the killed child's status and the script exits 1,
# which anything wrapping it would read as "the tunnel broke".
trap 'exit 0' INT TERM

python3 "$ROOT/scripts/lib/connect-proxy.py" "$PORT" &
proxy_pid=$!

# Wait for the listener rather than sleeping a guessed interval.
for _ in $(seq 1 40); do
  if nc -z 127.0.0.1 "$PORT" >/dev/null 2>&1; then break; fi
  sleep 0.25
done
if ! nc -z 127.0.0.1 "$PORT" >/dev/null 2>&1; then
  echo "FAIL — the local proxy did not come up on port $PORT." >&2
  exit 1
fi

"$ADB" reverse "tcp:$PORT" "tcp:$PORT" >/dev/null
"$ADB" shell settings put global http_proxy "localhost:$PORT" >/dev/null

# Checked with an EXPLICIT `-x`, and that detail is the whole reason this block
# is commented. `settings put global http_proxy` is read by Android's Java
# networking stack — which is what the app uses, via OkHttp — but **not** by the
# `curl` binary in `adb shell`. A bare `adb shell curl` therefore reports the
# phone's ordinary Wi-Fi egress and a 403 from the dev tier, while the app
# sitting next to it is getting 200s.
#
# The first version of this script checked exactly that way and reported failure
# while everything worked (measured 2026-08-15). A check that measures the wrong
# thing is worse than no check: it sends you to debug the part that was fine.
phone_egress="$("$ADB" shell "curl -s -x localhost:$PORT --max-time 10 https://api.ipify.org" 2>/dev/null | tr -d '\r' || true)"
echo "phone is now routed through this Mac."
echo "  phone egress     : ${phone_egress:-(could not read — the phone may have no curl)}"
if [ -n "$phone_egress" ] && [ "$phone_egress" = "$mac_egress" ]; then
  echo "  ✅ matches this Mac — the phone inherits its network, WireGuard and all"
elif [ -n "$phone_egress" ]; then
  echo "  ⚠️  does NOT match this Mac ($mac_egress) — the tunnel is not carrying traffic"
fi
echo
echo "  Note: apps pick this up (Android's Java stack reads the global proxy);"
echo "  \`adb shell curl\` does NOT unless you pass -x localhost:$PORT yourself."
echo
echo "Ctrl-C to stop and restore the phone's settings."
echo

wait "$proxy_pid"
