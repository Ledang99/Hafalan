package com.quranwidget.hafalan.ui

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/**
 * Parses Quran.com `text_uthmani_tajweed` HTML into colored spans.
 * Unknown tags are stripped; plain Uthmani text passes through unchanged.
 */
object TajweedMarkup {
    private val tajweedTag = Regex(
        """<tajweed\s+class=([a-zA-Z0-9_]+)>(.*?)</tajweed>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val endSpan = Regex(
        """<span\s+class=end>(.*?)</span>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val anyTag = Regex("""<[^>]+>""")

    /** Standard Quran.com tajweed palette (light / in-app). */
    private val classColors = mapOf(
        "ham_wasl" to 0xFF9E9E9E.toInt(),
        "laam_shamsiyah" to 0xFF9E9E9E.toInt(),
        "slnt" to 0xFF9E9E9E.toInt(),
        "madda_normal" to 0xFF537FFF.toInt(),
        "madda_permissible" to 0xFF4050FF.toInt(),
        "madda_necessary" to 0xFF000EBC.toInt(),
        "madda_obligatory" to 0xFF2144C1.toInt(),
        "qlq" to 0xFFDD0008.toInt(),
        "ikhf" to 0xFFD500B7.toInt(),
        "ikhf_shfw" to 0xFFD500B7.toInt(),
        "iqlab" to 0xFF26BFFD.toInt(),
        "idghm_shfw" to 0xFF169200.toInt(),
        "idgh_ghn" to 0xFF169200.toInt(),
        "idgh_mus" to 0xFF169200.toInt(),
        "idgh_mut" to 0xFF169200.toInt(),
        "idgh_wo_ghn" to 0xFF169200.toInt(),
        "ghn" to 0xFFFF7E1E.toInt(),
    )

    /** Brighter tajweed colors for the dark green home-screen widget. */
    private val classColorsDarkSurface = mapOf(
        "ham_wasl" to 0xFFBDBDBD.toInt(),
        "laam_shamsiyah" to 0xFFBDBDBD.toInt(),
        "slnt" to 0xFFBDBDBD.toInt(),
        "madda_normal" to 0xFF8CB4FF.toInt(),
        "madda_permissible" to 0xFFA0ABFF.toInt(),
        "madda_necessary" to 0xFFC5D0FF.toInt(),
        "madda_obligatory" to 0xFF9EB6FF.toInt(),
        "qlq" to 0xFFFF8A80.toInt(),
        "ikhf" to 0xFFFF7AD9.toInt(),
        "ikhf_shfw" to 0xFFFF7AD9.toInt(),
        "iqlab" to 0xFF7ADFFF.toInt(),
        "idghm_shfw" to 0xFF7DFF9A.toInt(),
        "idgh_ghn" to 0xFF7DFF9A.toInt(),
        "idgh_mus" to 0xFF7DFF9A.toInt(),
        "idgh_mut" to 0xFF7DFF9A.toInt(),
        "idgh_wo_ghn" to 0xFF7DFF9A.toInt(),
        "ghn" to 0xFFFFB347.toInt(),
    )

    fun plainText(markup: String): String {
        if (!markup.contains('<')) return markup
        return markup
            .replace(tajweedTag) { it.groupValues[2] }
            .replace(endSpan) { it.groupValues[1] }
            .replace(anyTag, "")
            .trim()
    }

    fun toAnnotatedString(
        markup: String,
        defaultColor: Color,
    ): AnnotatedString {
        if (!markup.contains('<')) {
            return AnnotatedString(markup)
        }
        return buildAnnotatedString {
            var remaining = markup
            while (remaining.isNotEmpty()) {
                val tajweed = tajweedTag.find(remaining)
                val end = endSpan.find(remaining)
                val next = listOfNotNull(tajweed, end).minByOrNull { it.range.first }
                if (next == null) {
                    append(anyTag.replace(remaining, ""))
                    break
                }
                val before = remaining.substring(0, next.range.first)
                if (before.isNotEmpty()) {
                    withStyle(SpanStyle(color = defaultColor)) {
                        append(anyTag.replace(before, ""))
                    }
                }
                if (next === tajweed || (tajweed != null && next.range == tajweed.range)) {
                    val cls = next.groupValues[1].lowercase()
                    val body = next.groupValues[2]
                    val argb = classColors[cls]
                    withStyle(
                        SpanStyle(color = if (argb != null) Color(argb) else defaultColor),
                    ) {
                        append(body)
                    }
                } else {
                    withStyle(
                        SpanStyle(
                            color = defaultColor.copy(alpha = 0.45f),
                            fontSize = 18.sp,
                        ),
                    ) {
                        append(next.groupValues[1])
                    }
                }
                remaining = remaining.substring(next.range.last + 1)
            }
        }
    }

    /** Spannable for App Widget RemoteViews / TextView. */
    fun toSpanned(
        markup: String,
        defaultColor: Int,
        forDarkSurface: Boolean = false,
    ): CharSequence {
        if (!markup.contains('<')) return markup
        val colors = if (forDarkSurface) classColorsDarkSurface else classColors
        val builder = SpannableStringBuilder()
        var remaining = markup
        while (remaining.isNotEmpty()) {
            val tajweed = tajweedTag.find(remaining)
            val end = endSpan.find(remaining)
            val next = listOfNotNull(tajweed, end).minByOrNull { it.range.first }
            if (next == null) {
                builder.append(anyTag.replace(remaining, ""))
                break
            }
            val before = remaining.substring(0, next.range.first)
            if (before.isNotEmpty()) {
                val start = builder.length
                builder.append(anyTag.replace(before, ""))
                builder.setSpan(
                    ForegroundColorSpan(defaultColor),
                    start,
                    builder.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            if (tajweed != null && next.range == tajweed.range) {
                val cls = next.groupValues[1].lowercase()
                val body = next.groupValues[2]
                val start = builder.length
                builder.append(body)
                builder.setSpan(
                    ForegroundColorSpan(colors[cls] ?: defaultColor),
                    start,
                    builder.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            } else {
                val start = builder.length
                builder.append(next.groupValues[1])
                builder.setSpan(
                    ForegroundColorSpan(0xFFB0B0B0.toInt()),
                    start,
                    builder.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                builder.setSpan(
                    RelativeSizeSpan(0.7f),
                    start,
                    builder.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            remaining = remaining.substring(next.range.last + 1)
        }
        return builder
    }

    fun looksLikeMarkup(text: String): Boolean =
        text.contains("<tajweed", ignoreCase = true) || text.contains("<span", ignoreCase = true)
}
