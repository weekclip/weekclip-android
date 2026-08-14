# Maestro flows

UI flows that drive the installed app on a real device and report back
machine-readably. This is the Android half of the agent feedback loop that
Playwright provides for weekclip-web.

## Run them

```bash
./scripts/run-maestro.sh                 # build + install + run every flow
./scripts/run-maestro.sh maestro/smoke.yaml   # one flow
./scripts/run-maestro.sh --require-device     # fail instead of skip when no device
```

Requires the Maestro CLI (`curl -fsSL "https://get.maestro.mobile.dev" | bash`)
and JDK 17+. Artifacts land in `build/maestro/` (gitignored): JUnit XML plus,
for every failing step, a screenshot, that step's view hierarchy as JSON, and
the device logcat.

## Use a real device, not an emulator

The flows themselves do not care, but on the current dev Mac the Android
emulator cannot start: `hvf is not enabled on this aarch64 host` sends QEMU to
software emulation, its CPU threads stop responding for 15s, and the process
takes SIGSEGV before `sys.boot_completed`. Measured four ways on 2026-08-14
(headless/windowed × cold/snapshot boot) — all four died the same way.

A USB device sidesteps that entirely, and PRD-0008 needs one regardless: the
148.5 deep-link DoD and the D8 background-upload DoD are both written against
real hardware. Verified against a Galaxy Note20 (SM-N981N, Android 13).

## Selectors: text works, `id` does not — yet

Compose emits accessibility nodes with `text` but with an **empty
`resource-id`**, so `id:` selectors match nothing. `MainActivity` opts into
`testTagsAsResourceId` so that `Modifier.testTag("...")` surfaces as an id; a
composable that wants a stable selector has to set that tag. Without a tag,
match on visible text and accept that copy changes break the flow.

Prefer, in order: `testTag` → visible text → `point`/coordinates (never — the
device resolution is not a contract).

## Reading the hierarchy by hand

`maestro hierarchy` on a Samsung device returns ~59KB, nearly all of it
`cocktailbarservice`, `sidegesturepad` and `systemui` chrome. The MCP
`inspect_screen` tool returns a compact tree instead and is the better way in
for an agent; see the repo README.
