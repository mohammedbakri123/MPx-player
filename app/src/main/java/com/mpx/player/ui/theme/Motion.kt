package com.mpx.player.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing

/**
 * M2.5 motion tokens (#105). Subtle and standard only — quick, physical
 * motion. No bouncy, springy, or showy flourishes (UX law).
 */
val MpxMotionFast = 150
val MpxMotionStandard = 250
val MpxMotionSlow = 350

val MpxEasingStandard: Easing = FastOutSlowInEasing
val MpxEasingDecelerate: Easing = LinearOutSlowInEasing
val MpxEasingEmphasized: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

// Dock-tab switches are TAP-driven, never swipe-driven (UX law), so
// there is deliberately no swipe/slide distance token here.
