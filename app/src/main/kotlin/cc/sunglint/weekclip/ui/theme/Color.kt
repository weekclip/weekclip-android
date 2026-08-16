package cc.sunglint.weekclip.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Brand palette, transcribed from `weekclip-design-system`.
 *
 * This file used to open with "Placeholder palette. The real tokens come from
 * weekclip-design-system when feature work starts (Phase 5)" and an accent of
 * `#3B6FE0` — a blue that appears nowhere in the product. The real accent is
 * `#5B53FF`, and it had already leaked into the shipped launcher icon.
 *
 * Source of truth: `weekclip-design-system/packages/tokens/tokens.json`, whose
 * own `$meta` records that every value there was extracted verbatim from
 * weekclip-web's production CSS on 2026-07-21. Nothing here is invented; each
 * constant names the token it came from. If a value below disagrees with
 * `tokens.json`, `tokens.json` wins.
 *
 * ## Scope — a subset, on purpose
 *
 * Only the tokens Material 3's `ColorScheme` actually consumes are transcribed:
 * backgrounds, text, border, accent, and the one status colour that maps to
 * `error`. The other status colours (`review`/`approved`/`answered`), the
 * avatar ramp and the overlay scrims arrive with the screens that need them —
 * a constant no composable reads is a claim nobody checks.
 *
 * ## Alpha
 *
 * Several web tokens are `rgba()` over a base. Compose wants `0xAARRGGBB`, so
 * the fraction is folded into the leading byte: 0.94 -> 0xF0, 0.88 -> 0xE0,
 * 0.74 -> 0xBD, 0.12 -> 0x1F, 0.22 -> 0x38, 0.92 -> 0xEB, 0.60 -> 0x99,
 * 0.38 -> 0x61, 0.35 -> 0x59, 0.45 -> 0x73. Each is `round(fraction * 255)`.
 */

// --- accent (theme-independent in the token set) -----------------------------
/** `color.accent.default` — the brand violet. Same value in both themes. */
val WeekclipAccent = Color(0xFF5B53FF)

/** `color.accent.hover` */
val WeekclipAccentHover = Color(0xFF4A42F0)

/** `color.text.onAccent` */
val WeekclipOnAccent = Color(0xFFFFFFFF)

// --- light -------------------------------------------------------------------
/** `color.bg.app` (light) */
val WeekclipBgAppLight = Color(0xFFFFFFFF)

/** `color.bg.panel` (light) */
val WeekclipBgPanelLight = Color(0xFFF7F8FA)

/** `color.bg.surface` (light) */
val WeekclipBgSurfaceLight = Color(0xFFF2F3F6)

/** `color.bg.surface2` (light) */
val WeekclipBgSurface2Light = Color(0xFFE7E9EE)

/** `color.bg.surface3` (light) */
val WeekclipBgSurface3Light = Color(0xFFD9DCE3)

/** `color.text.1` (light) — rgba(20,21,28,0.94) */
val WeekclipText1Light = Color(0xF014151C)

/** `color.text.2` (light) — rgba(20,21,28,0.88) */
val WeekclipText2Light = Color(0xE014151C)

/** `color.text.3` (light) — rgba(20,21,28,0.74) */
val WeekclipText3Light = Color(0xBD14151C)

/** `color.border.default` (light) — rgba(20,21,28,0.12) */
val WeekclipBorderLight = Color(0x1F14151C)

/** `color.border.strong` (light) — rgba(20,21,28,0.22) */
val WeekclipBorderStrongLight = Color(0x3814151C)

/** `color.status.rejected` (light) */
val WeekclipErrorLight = Color(0xFFD42840)

// --- dark --------------------------------------------------------------------
/** `color.bg.app` (dark) — pure black, deliberately (frame.io V4) */
val WeekclipBgAppDark = Color(0xFF000000)

/** `color.bg.panel` (dark). Also the launcher icon's background. */
val WeekclipBgPanelDark = Color(0xFF0C0E16)

/** `color.bg.surface` (dark) */
val WeekclipBgSurfaceDark = Color(0xFF12141E)

/** `color.bg.surface2` (dark) */
val WeekclipBgSurface2Dark = Color(0xFF1F2332)

/** `color.bg.surface3` (dark) */
val WeekclipBgSurface3Dark = Color(0xFF262A3B)

/** `color.text.1` (dark) — rgba(243,243,247,0.92) */
val WeekclipText1Dark = Color(0xEBF3F3F7)

/** `color.text.2` (dark) — rgba(243,243,247,0.60) */
val WeekclipText2Dark = Color(0x99F3F3F7)

/** `color.text.3` (dark) — rgba(243,243,247,0.38) */
val WeekclipText3Dark = Color(0x61F3F3F7)

/** `color.border.default` (dark) — rgba(57,61,79,0.35) */
val WeekclipBorderDark = Color(0x59393D4F)

/** `color.border.strong` (dark) — rgba(85,88,110,0.45) */
val WeekclipBorderStrongDark = Color(0x7355586E)

/** `color.status.rejected` (dark) */
val WeekclipErrorDark = Color(0xFFF43350)
