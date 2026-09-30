package com.quranwidget.hafalan.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.res.ResourcesCompat
import com.quranwidget.hafalan.R

/**
 * Applies the same bundled KFGQPC Uthmanic Hafs font the Compose app uses
 * ([R.font.uthmanic_hafs]) to widget ayah text.
 *
 * App Widget hosts run RemoteViews in another process and typically ignore
 * `android:fontFamily` / cannot call `TextView.setTypeface` (not remotable;
 * there is no public `RemoteViews.setTextViewTypeface`). Custom TypefaceSpans
 * also fail to carry font-file bytes across the binder.
 *
 * Fix: rasterize the ayah (plain or tajweed Spannable) with
 * [ResourcesCompat.getFont] → [R.font.uthmanic_hafs] into
 * [R.id.widget_ayah_image], matching in-app Compose typography.
 */
object WidgetAyahFont {

    fun uthmanicTypeface(context: Context): Typeface =
        ResourcesCompat.getFont(context, R.font.uthmanic_hafs) ?: Typeface.DEFAULT

    /**
     * Bind ayah [text] (plain or tajweed Spannable) with KFGQPC Uthmanic Hafs.
     * [widthDp]/[heightDp] are the ayah tile interior in dp (padding already removed).
     */
    fun apply(
        context: Context,
        views: RemoteViews,
        text: CharSequence,
        textColor: Int,
        widthDp: Int,
        heightDp: Int,
        maxLines: Int,
        maxSp: Float,
    ) {
        val typeface = uthmanicTypeface(context)
        // TextView kept in layout for id stability / editor preview; bitmap is what shows.
        views.setViewVisibility(R.id.widget_ayah_text, View.GONE)
        views.setViewVisibility(R.id.widget_ayah_image, View.VISIBLE)
        views.setContentDescription(R.id.widget_ayah_image, text.toString())

        val metrics = context.resources.displayMetrics
        val density = metrics.density * context.resources.configuration.fontScale
        val widthPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            widthDp.coerceAtLeast(48).toFloat(),
            metrics,
        ).toInt().coerceAtLeast(1)
        val heightPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            heightDp.coerceAtLeast(40).toFloat(),
            metrics,
        ).toInt().coerceAtLeast(1)

        val bitmap = renderBitmap(
            text = text,
            typeface = typeface,
            defaultColor = textColor,
            widthPx = widthPx,
            heightPx = heightPx,
            maxLines = maxLines,
            minSp = 12f,
            maxSp = maxSp.coerceIn(12f, 72f),
            density = density,
        )
        views.setImageViewBitmap(R.id.widget_ayah_image, bitmap)
    }

    private fun renderBitmap(
        text: CharSequence,
        typeface: Typeface,
        defaultColor: Int,
        widthPx: Int,
        heightPx: Int,
        maxLines: Int,
        minSp: Float,
        maxSp: Float,
        density: Float,
    ): Bitmap {
        var lo = minSp
        var hi = maxSp
        var best = minSp
        // Binary-search the largest size that still fits (mirrors TextView auto-size).
        repeat(14) {
            val mid = (lo + hi) / 2f
            if (layoutFits(text, typeface, defaultColor, mid, widthPx, heightPx, maxLines, density)) {
                best = mid
                lo = mid
            } else {
                hi = mid
            }
        }
        val layout = buildLayout(text, typeface, defaultColor, best, widthPx, maxLines, density)
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val dy = ((heightPx - layout.height) / 2f).coerceAtLeast(0f)
        canvas.save()
        canvas.translate(0f, dy)
        layout.draw(canvas)
        canvas.restore()
        return bitmap
    }

    private fun layoutFits(
        text: CharSequence,
        typeface: Typeface,
        defaultColor: Int,
        sizeSp: Float,
        widthPx: Int,
        heightPx: Int,
        maxLines: Int,
        density: Float,
    ): Boolean {
        val layout = buildLayout(text, typeface, defaultColor, sizeSp, widthPx, maxLines, density)
        if (layout.height > heightPx) return false
        if (layout.lineCount > maxLines) return false
        if (layout.lineCount >= maxLines) {
            val last = layout.lineCount - 1
            if (layout.getEllipsisCount(last) > 0) return false
            if (layout.getLineEnd(last) < text.length) return false
        }
        return true
    }

    private fun buildLayout(
        text: CharSequence,
        typeface: Typeface,
        defaultColor: Int,
        sizeSp: Float,
        widthPx: Int,
        maxLines: Int,
        density: Float,
    ): StaticLayout {
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            this.color = defaultColor
            textSize = sizeSp * density
            isSubpixelText = true
        }
        return StaticLayout.Builder
            .obtain(text, 0, text.length, paint, widthPx.coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setLineSpacing(0f, 1.05f)
            .setMaxLines(maxLines)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
    }
}
