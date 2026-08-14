#!/usr/bin/env bash
#
# PRD-0008 D3 / N6 — the app binary must contain ZERO payment-related strings.
#
# Why a gate and not a note: D3 is the whole reason the app can exist without
# in-app purchase. Apple Guideline 3.1.1 judges what the binary says, so the
# rule has to be checked mechanically, not remembered. A capacity shortfall is
# reported as a plain fact ("Not enough capacity") — no price, no top-up path,
# no "buy it on the web".
#
# What this checks:
#   1. String resources  — app/src/main/res/values*/strings.xml
#   2. Kotlin string literals, with comments stripped first
#
# The comment-stripping matters: this repo's source deliberately *discusses*
# billing in comments (explaining why it is absent). Those must not trip the
# gate, and the compiler drops them anyway.
#
# What this does NOT check: third-party library internals. Nothing in the
# dependency set (Compose, Retrofit, OkHttp, Media3, Hilt) ships a billing SDK;
# if one is ever added, that is a review-time decision, not a grep.
#
# Usage:  scripts/check-no-payment-strings.sh [repo-root]
# Exit:   0 clean, 1 forbidden strings found

set -euo pipefail

ROOT="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
cd "$ROOT"

# Words that would signal a purchase path to a store reviewer. Deliberately
# narrow: "capacity" and "storage" are fine — the app must be able to say the
# user is out of room. What it must never do is name a way to pay.
FORBIDDEN='checkout|polar|purchase|subscription|subscribe|top[ _-]?up|billing|payment|credit[ _-]?card|paywall|upgrade[ _-]?plan|refund|invoice|price|pricing|\$[0-9]'

fail=0

echo "== 1/2  string resources =="
res_files=$(find app/src/main/res -name 'strings.xml' 2>/dev/null || true)
if [ -z "$res_files" ]; then
  echo "   (no strings.xml found)"
else
  # shellcheck disable=SC2086
  if hits=$(grep -rniE "$FORBIDDEN" $res_files); then
    echo "$hits" | sed 's/^/   FORBIDDEN: /'
    fail=1
  else
    echo "   clean"
  fi
fi

echo "== 2/2  Kotlin string literals (comments stripped) =="
kt_hits=""
while IFS= read -r f; do
  # Strip /* */ blocks, then // line comments, then keep only double-quoted
  # literals. perl -0777 slurps the file so multi-line block comments go too.
  literals=$(perl -0777 -pe 's{/\*.*?\*/}{}gs' "$f" \
    | sed 's://.*::' \
    | grep -oE '"[^"]*"' || true)
  if [ -n "$literals" ]; then
    if match=$(printf '%s\n' "$literals" | grep -niE "$FORBIDDEN" || true); then
      if [ -n "$match" ]; then
        kt_hits="${kt_hits}${f}: ${match}"$'\n'
      fi
    fi
  fi
done < <(find app/src -name '*.kt' 2>/dev/null)

if [ -n "$kt_hits" ]; then
  printf '%s' "$kt_hits" | sed 's/^/   FORBIDDEN: /'
  fail=1
else
  echo "   clean"
fi

echo
if [ "$fail" -ne 0 ]; then
  echo "FAIL — payment-related strings found. PRD-0008 D3 requires zero." >&2
  echo "If a match is a false positive, narrow the pattern in this script and" >&2
  echo "say why in the commit message. Do not add a blanket skip." >&2
  exit 1
fi

echo "PASS — no payment-related strings (PRD-0008 D3)."
