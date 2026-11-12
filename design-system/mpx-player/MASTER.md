# MPx Player — Design System Master

> **Source of Truth:** This document defines the actual design tokens and patterns implemented in `app/src/main/java/com/mpx/player/ui/theme/`.
> MPx Player is a native Kotlin Android application built with Jetpack Compose and Material 3 (not a web/CSS app).

---

**Project:** MPx Player  
**Platform:** Android (Jetpack Compose, Material 3)  
**Theme Implementation:** `com.mpx.player.ui.theme` (`Color.kt`, `Theme.kt`, `Type.kt`, `Shape.kt`, `Spacing.kt`, `Motion.kt`, `MpxPalette.kt`)  
**Core Design Philosophy:** Instagram/Facebook level polish — clean, uncrowded layout, standard Android placements, subtle physical motion, with ONE accent color (MPx identity blue by default, used sparingly for selection, progress, and primary actions).

---

## 1. Surfaces & Themes

MPx supports **System**, **Dark**, and **Light** modes (`Theme.kt`). Flipping between Dark and Light retains the selected color family. The video player route (`PlayerScreen`) is always rendered in Dark mode.

### Default Surfaces (True Black AMOLED vs Clean White)

| Token (`MpxColors`) | Dark Mode (Default) | Light Mode | Usage |
|---------------------|---------------------|------------|-------|
| `black` | `#000000` | `#FFFFFF` | Canvas base, dialog background |
| `surface` | `#000000` | `#FFFFFF` | Root screens, lists |
| `surfaceRaised` | `#0A0A0A` | `#F6F6F6` | Cards, list rows |
| `surfaceVariant` | `#111111` | `#EDEDED` | Elevated cards, chip containers |
| `surfaceElevated` | `#1A1A1A` | `#E2E2E2` | Sheets, popups, elevated dialogs |
| `border` | `#222222` | `#E0E0E0` | Hairline dividers, card outlines |
| `borderSubtle` | `#3394A3B8` | `#1F000000` | Subtle row separators |

### Theme Color Families (`mpxThemeColorsFor`)

The user can choose between 6 surface families (one color, two brightnesses):
1. **True Black / White** (Default): Pure `#000000` AMOLED base / `#FFFFFF` Light base.
2. **Charcoal / Warm Light**: `#121212` softer AMOLED base / `#FAF7F2` warm-paper light base.
3. **Graphite**: `#1E1E1E` dark base.
4. **Midnight / Cool Light**: `#0A0F1E` navy dark base / `#F4F7FA` cool-mist light base.
5. **Forest**: `#0C1510` dark base.
6. **Plum**: `#16111F` dark base.

---

## 2. Accents & Semantics

### Identity Accent (`primary`, `primaryHover`, `primaryVariant`)

- Default: **MPx Identity Blue** (`primary #3B82F6`, `primaryHover #2563EB`, `primaryVariant #1D4ED8`).
- Accent is applied over surfaces via `MpxTheme.colors.primary` and `MaterialTheme.colorScheme.primary`.
- **12 User-Selectable Palettes:** Blue, Emerald, Violet, Amber, Rose, Teal, Sky, Orange, Pink, Lime, Indigo, Cyan (`MpxPalette.kt`). Palettes modify *only* the accent tokens; surface backgrounds and contrast rules remain untouched.

### Semantic Tokens

| Token | Dark Mode | Light Mode | Usage |
|-------|-----------|------------|-------|
| `destructive` | `#EF4444` | `#DC2626` | Destructive actions, fatal error states (never decorative) |
| `warning` / `warningContainer` | `#FFB74D` / `0x33FFB74D` | `#D97706` / `0x26D97706` | Non-fatal warnings (e.g. decoder fallbacks) |
| `success` | `#10B981` | `#059669` | Success confirmation, completed states |
| `textPrimary` | `#FFFFFF` | `#111111` | High-emphasis headings, titles |
| `textSecondary` | `#CBD5E1` | `#475569` | Body copy, secondary metadata |
| `textMuted` | `#94A3B8` | `#64748B` | Subtle captions, timestamps, placeholder text |

---

## 3. Typography (`Type.kt`)

All typography tokens use the system font (`FontFamily.Default`) via Material 3 `Typography`.
No external fonts (Inter, Playfair Display) are bundled. All sizes use `.sp` to support system font-scaling accessibility.

| Style | Weight | Size | Line Height |
|-------|--------|------|-------------|
| `headlineLarge` | Bold (700) | 28sp | 34sp |
| `headlineMedium` | Bold (700) | 22sp | 28sp |
| `titleLarge` | SemiBold (600) | 19sp | 24sp |
| `titleMedium` | Medium (500) | 16sp | 22sp |
| `titleSmall` | SemiBold (600) | 14sp | 20sp |
| `bodyLarge` | Normal (400) | 15sp | 22sp |
| `bodyMedium` | Normal (400) | 14sp | 20sp |
| `bodySmall` | Normal (400) | 12sp | 16sp |
| `labelMedium` | Medium (500) | 12sp | 16sp |
| `labelSmall` | SemiBold (600) | 11sp | 14sp |

Diagnostics and config text use `FontFamily.Monospace`.

---

## 4. Spacing & Sizing Grid (`Spacing.kt`)

Based on an 8dp grid (with 2dp/4dp/12dp half-steps):

| Token | Value | Standard Usage |
|-------|-------|----------------|
| `MpxSpaceXxs` | `2.dp` | Hairline gaps, micro-badges |
| `MpxSpaceXs` | `4.dp` | Inline icon-to-text spacing |
| `MpxSpaceS` | `8.dp` | Standard padding, item spacing, card gaps |
| `MpxSpaceM` | `12.dp` | Screen padding (compact), list row vertical padding |
| `MpxSpaceL` | `16.dp` | Standard screen horizontal padding, card content padding |
| `MpxSpaceXl` | `24.dp` | Section spacing, header margins |
| `MpxSpaceXxl` | `32.dp` | Major section separators |

### Component Sizing Constants

- `MpxTouchTarget = 48.dp`: Minimum touch target height/width per Android accessibility guidelines.
- `MpxIconSize = 24.dp`: Standard navigation and action icon dimensions.
- `MpxDockHeight = 64.dp`: Bottom navigation dock height.
- `MpxCardGap = 8.dp`: Spacing between adjacent cards.

---

## 5. Shapes (`Shape.kt`)

Restrained radii; soft rectangles and pills only. No decorative asymmetric shapes.

| Token | Value | Usage |
|-------|-------|-------|
| `MpxShapes.extraSmall` | `4.dp` | Tooltips, micro-indicators |
| `MpxShapes.small` | `8.dp` | Input fields, small buttons, tags |
| `MpxShapes.medium` | `12.dp` | Tiles, dialog content boxes |
| `MpxShapes.large` | `16.dp` | Standard cards (`MpxRadiusCard`), dialog frames |
| `MpxShapes.extraLarge` | `24.dp` | Large sheets, chips (`MpxRadiusChip`) |
| `MpxRadiusPill` | `100.dp` | Capsule buttons, pill FABs, filter chips |
| `MpxHairline` | `0.5.dp` | Hairline divider stroke |

---

## 6. Motion (`Motion.kt`)

Quick, physical, subtle animations. No springy, bouncy, or gratuitous motion.

- `MpxMotionFast = 150ms`: State toggles, button presses.
- `MpxMotionStandard = 250ms`: Sheet expansions, dialog enter/exit.
- `MpxMotionSlow = 350ms`: Major screen route crossfades.
- Easings:
  - `MpxEasingStandard`: `FastOutSlowInEasing` (standard transitions)
  - `MpxEasingDecelerate`: `LinearOutSlowInEasing` (incoming overlays)
  - `MpxEasingEmphasized`: `CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)`
- Dock tab switches are tap-only (no horizontal swipe token).

---

## 7. Android UI Guidelines & Rules

1. **Tokens over Literals:** Always use `MpxTheme.colors`, `MpxShapes`, `MpxTypography`, and `MpxSpace*` tokens. Never introduce raw `Color(0x...)` or hardcoded `.dp`/`.sp` values in UI components.
2. **Theme Awareness:** Never hardcode `Color.White` or `Color.Black` for text or icons inside composables; use `colors.textPrimary` or rely on `LocalContentColor`. Dark mode icons over dark media thumbnails may use white with explicit context.
3. **Accessibility:**
   - Minimum 48dp touch targets on all interactive elements (`MpxTouchTarget`).
   - Every interactive icon must supply a localized `contentDescription` (or `null` if purely decorative beside a text label).
   - Text sizes must always use `.sp` to respect system font scaling.
4. **RTL Support:**
   - MPx supports LTR and RTL (e.g. Arabic) using `LocalLayoutDirection`.
   - Never use absolute Left/Right layout paddings or alignments; use start/end.
