package com.giraffe.mizanapp.today

import androidx.compose.ui.text.font.FontFamily

/**
 * The one place the Arabic typeface is chosen.
 *
 * Task and section labels are Arabic **content**, not interface strings, and
 * must render in an Arabic-appropriate face with correct bidirectional
 * handling (constitution v1.1.1, FR-025).
 *
 * ### Outstanding
 *
 * The design specifies **IBM Plex Sans Arabic** (see `CLAUDE.md`, Design). The
 * font file is not in the repository, so this currently falls back to the
 * platform default — which does render Arabic correctly, but not in the
 * specified face.
 *
 * To finish: drop `ibm_plex_sans_arabic_regular.ttf` and
 * `ibm_plex_sans_arabic_medium.ttf` into `app/src/main/res/font/`, then replace
 * the body of [arabic] with:
 *
 * ```kotlin
 * FontFamily(
 *     Font(R.font.ibm_plex_sans_arabic_regular, FontWeight.Normal),
 *     Font(R.font.ibm_plex_sans_arabic_medium, FontWeight.Medium),
 * )
 * ```
 *
 * Nothing else in the app changes — every Arabic string already routes through
 * here.
 */
object MizanTypography {

    val arabic: FontFamily = FontFamily.Default
}
