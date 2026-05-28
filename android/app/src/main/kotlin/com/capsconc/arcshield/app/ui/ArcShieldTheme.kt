/*
 * Intellectual Property and Trademark Notice
 *
 * mp4Real™, ArcShield™, CIAER™, and CIAER+™ are trademarks of Capps Consulting
 * Company LLC. The multi-track cyber-physical capture architecture, the
 * application of log-likelihood ratio (LLR) gating to multimodal industrial
 * decision events, and the behavioral codebook discretization methods described
 * in this document are the proprietary intellectual property of Kahn Capps and
 * Capps Consulting Company LLC. Unauthorized commercial use, reproduction, or
 * implementation of the mp4Real™ container architecture or the CIAER™ and CIAER+™
 * schemas without explicit licensing is prohibited. All rights reserved.
 */
package com.capsconc.arcshield.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// PWA-matched dark palette (#07090D background, #4B8EFF blue, #34D399 green, etc.)
object ArcShieldColors {
    val Background      = Color(0xFF07090D)
    val Surface         = Color(0xFF0E1117)
    val SurfaceVariant  = Color(0xFF151921)
    val Card            = Color(0xFF1C2030)
    val Outline         = Color(0xFF252A3A)
    val OnBackground    = Color(0xFFE8EAF0)
    val OnSurface       = Color(0xFFE8EAF0)
    val OnSurfaceVariant = Color(0xFF8A8FA8)
    val Blue            = Color(0xFF4B8EFF)
    val Green           = Color(0xFF34D399)
    val Amber           = Color(0xFFFBBF24)
    val Red             = Color(0xFFF87171)
    val Violet          = Color(0xFFA78BFA)
}

private val ArcShieldColorScheme = darkColorScheme(
    primary             = ArcShieldColors.Blue,
    onPrimary           = Color(0xFF07090D),
    primaryContainer    = Color(0xFF1A2A4A),
    onPrimaryContainer  = ArcShieldColors.Blue,
    secondary           = ArcShieldColors.Green,
    onSecondary         = Color(0xFF07090D),
    tertiary            = ArcShieldColors.Amber,
    onTertiary          = Color(0xFF07090D),
    error               = ArcShieldColors.Red,
    onError             = Color(0xFF07090D),
    background          = ArcShieldColors.Background,
    onBackground        = ArcShieldColors.OnBackground,
    surface             = ArcShieldColors.Surface,
    onSurface           = ArcShieldColors.OnSurface,
    surfaceVariant      = ArcShieldColors.SurfaceVariant,
    onSurfaceVariant    = ArcShieldColors.OnSurfaceVariant,
    outline             = ArcShieldColors.Outline,
    outlineVariant      = Color(0xFF1C2030),
)

@Composable
fun ArcShieldTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ArcShieldColorScheme,
        content     = content,
    )
}
