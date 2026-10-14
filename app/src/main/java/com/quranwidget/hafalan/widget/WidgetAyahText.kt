package com.quranwidget.hafalan.widget

import com.quranwidget.hafalan.data.ScriptEdition
import com.quranwidget.hafalan.ui.TajweedMarkup

/**
 * Arabic stays KFGQPC (plain or tajweed spans). Malay is plain Latin text.
 * @return text to draw, and whether to use the Uthmanic typeface.
 */
internal fun widgetAyahCharSequence(
    cachedArabic: String,
    cachedMalay: String,
    showMalay: Boolean,
    script: ScriptEdition,
    textColor: Int,
    darkSurface: Boolean,
): Pair<CharSequence, Boolean> {
    if (showMalay) {
        val malay = cachedMalay.ifBlank { "Terjemahan belum dimuat" }
        return malay to false
    }
    val raw = cachedArabic.ifBlank { "…" }
    val text = when (script) {
        ScriptEdition.TAJWEED -> {
            if (TajweedMarkup.looksLikeMarkup(raw)) {
                TajweedMarkup.toSpanned(raw, textColor, forDarkSurface = darkSurface)
            } else {
                raw
            }
        }
        ScriptEdition.UTHMANI -> {
            if (TajweedMarkup.looksLikeMarkup(raw)) TajweedMarkup.plainText(raw) else raw
        }
    }
    return text to true
}
