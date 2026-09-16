package com.quranwidget.hafalan.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.quranwidget.hafalan.R

/** King Fahad Complex (KFGQPC) Uthmanic Hafs — bundled TrueType. */
object QuranFonts {
    val UthmanicHafs: FontFamily = FontFamily(
        Font(R.font.uthmanic_hafs, weight = FontWeight.Normal),
    )
}

@Composable
fun rememberUthmanicFontFamily(): FontFamily = remember { QuranFonts.UthmanicHafs }
