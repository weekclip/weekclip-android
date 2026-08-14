# Agent skills — provenance and local rules

## Where these came from

17 skills vendored from **[new-silvermoon/awesome-android-agent-skills](https://github.com/new-silvermoon/awesome-android-agent-skills)**
at commit `82900ea` (2026-07-27), MIT licensed.

They were **flattened**: upstream nests them under category folders
(`.github/skills/ui/compose-ui/`), but Claude Code discovers skills at
`.claude/skills/<name>/SKILL.md` — one level only. Category folders would make
them invisible. Each skill's own `SKILL.md` and `scripts/` are otherwise
verbatim.

## What was deliberately NOT copied

Upstream also ships an **`Agent.md`** for the project root. It is not here, and
should not be added, because it contradicts this project's recorded decisions:

| Upstream `Agent.md` says | This project (ADR-0002) |
|---|---|
| Target SDK 34 | **37** |
| Minimum SDK 24 | **28** |
| "Hilt/Dagger (preferred) **or Koin**" | **Hilt**, with the reason recorded |
| Navigation: generic | **Navigation 2**, because Nav3 is still alpha |

A generic guidance file that disagrees with the ADR is worse than no file: an
agent reading both has no way to know which wins. **`ADR-0002` is the stack SSOT**
(`weekclip-harness/wiki/adr/ADR-0002-native-app-stack.md`), and `README.md` in
this repo summarises it.

## Rules that override any vendored skill

1. **No payment code.** PRD-0008 D3 requires zero payment-related strings in the
   binary; `scripts/check-no-payment-strings.sh` gates it in CI. No vendored
   skill knows this.
2. **The route table is a contract.** `ui/navigation/WeekclipRoutes.kt` mirrors
   weekclip-web's URLs because App Links resolve against them. Do not "clean up"
   route strings.
3. **Stack choices come from ADR-0002**, not from a skill's default advice.

## A caveat on `android-emulator-skill`

Its `emulator_manage.py` boots the emulator with a plain `subprocess.Popen`.
**Do not rely on that from an agent shell.** Measured on macOS 15.1 / Apple
Silicon, 2026-08-14:

| Launch method | Result |
|---|---|
| `subprocess.Popen` / `nohup … &` from the agent shell | `hvf is not enabled`, `mprotect: Permission denied` → never reaches `device` |
| same, with the tool sandbox disabled | identical |
| `agent-device boot --platform android --headless` | **reached `device`** — then its 90s client timeout force-killed the daemon, taking the emulator (its child) with it |
| `launchctl submit` | survived the shell, but **SIGSEGV crash-loop** (`launchctl list` status `-11`, pid respawning) |

`emulator -accel-check` reports Hypervisor.Framework as available, yet the
running emulator says otherwise — the agent's process tree does not get the
hypervisor/JIT permissions the emulator needs.

**Known-good path: boot the emulator from Android Studio's Device Manager (or a
human terminal), then attach.** `adb` and `agent-device` drive it fine once it is
up and owned by something outside the agent shell. If you use `agent-device`,
let it *attach* — never let it own the emulator process.

The skill's other scripts (`navigator.py`, `log_monitor.py`, `gesture.py`, …)
act on an already-running device and are unaffected.

> This table is a record of what was actually tried, not a list of options.
> If you find a launch method that works from an agent shell, replace it.
