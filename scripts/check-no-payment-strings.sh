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
#   1. String resources  — app/src/main/res/values*/strings.xml, comments stripped
#   2. Kotlin string literals in app/src/main and app/src/debug, comments stripped
#
# Scope is the code that becomes the APK. `app/src/test` and `app/src/androidTest`
# are excluded on purpose (2026-08-15): they are not in any binary a reviewer can
# see, and a test asserting that the app does NOT own a payment path has to name
# that path to assert it — `WeekclipDeepLinkTest` requires `/billing/products` to
# resolve to null, which is *evidence for* D3, not a violation of it. The iOS
# gate reached the same conclusion first and limited itself to `Sources/`+`App/`
# for exactly this reason; this is that correction, applied here.
#
# The comment-stripping matters: this repo's source deliberately *discusses*
# billing in comments (explaining why it is absent). Those must not trip the
# gate, and the compiler drops them anyway.
#
# That applies to XML too, and used not to: `<!-- ... -->` was scanned as if it
# shipped. It does not — aapt2 drops XML comments exactly as kotlinc drops
# Kotlin ones — and the first thing the un-stripped version ever flagged was
# strings.xml's own header comment explaining this rule. A gate that fires on
# its own documentation trains people to disable it.
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
#
# `upgrade.{0,20}plan` and `\bplans?\b` were added on 2026-08-15 after this
# gate was tested against the sentence that was actually living in
# weekclip-ios (`AppError.insufficientCapacity`'s recovery suggestion):
#
#     "Please upgrade your plan or delete some content"
#
# The old pattern had `upgrade[ _-]?plan`, which does not match "upgrade YOUR
# plan", and nothing else in the list matched either — so both gates passed a
# real Guideline 3.1.1 string. A gate is only worth what you have watched it
# reject.
FORBIDDEN='checkout|polar|purchase|subscription|subscribe|top[ _-]?up|billing|payment|credit[ _-]?card|paywall|upgrade.{0,20}plan|\bplans?\b|refund|invoice|price|pricing|\$[0-9]'

fail=0

echo "== 1/2  string resources (comments stripped) =="
res_files=$(find app/src/main/res -name 'strings.xml' 2>/dev/null || true)
if [ -z "$res_files" ]; then
  echo "   (no strings.xml found)"
else
  xml_hits=""
  while IFS= read -r f; do
    # -0777 slurps so a comment spanning lines is removed whole. Line numbers
    # are recomputed after stripping, so they point into the stripped view; the
    # matched text is printed alongside, which is what you actually search for.
    stripped=$(perl -0777 -pe 's{<!--.*?-->}{}gs' "$f")
    if match=$(printf '%s\n' "$stripped" | grep -niE "$FORBIDDEN" || true); then
      if [ -n "$match" ]; then
        xml_hits="${xml_hits}${f}: ${match}"$'\n'
      fi
    fi
  done < <(printf '%s\n' "$res_files")

  if [ -n "$xml_hits" ]; then
    printf '%s' "$xml_hits" | sed 's/^/   FORBIDDEN: /'
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
done < <(find app/src/main app/src/debug -name '*.kt' 2>/dev/null)

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
