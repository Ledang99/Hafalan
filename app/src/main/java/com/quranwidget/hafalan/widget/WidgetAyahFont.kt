package com.quranwidget.hafalan.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.res.ResourcesCompat
import com.quranwidget.hafalan.R

/**
 * Rasterizes ayah text into the widget image. RemoteViews cannot apply the
 * bundled KFGQPC typeface in the launcher process.
 *
 * Text that fits at a readable size stays still and centered. Longer text is
 * drawn at that minimum size and [WidgetAutoScroll] pages through it.
 * Font padding stays on so fathah, kasrah, and dammah are not clipped.
 */
object WidgetAyahFont {

    data class RenderInfo(
        val overflows: Boolean,
        val contentHeightPx: Int,
        val viewportHeightPx: Int,
    )

    fun uthmanicTypeface(context: Context): Typeface =
        ResourcesCompat.getFont(context, R.font.uthmanic_hafs) ?: Typeface.DEFAULT

    fun apply(
        context: Context,
        views: RemoteViews,
        text: CharSequence,
        textColor: Int,
        widthDp: Int,
        heightDp: Int,
        maxLines: Int,
        maxSp: Float,
        minReadableSp: Float,
        rtl: Boolean,
        typeface: Typeface,
        scrollOffsetPx: Int,
    ): RenderInfo {
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

        val readableMin = minReadableSp.coerceIn(12f, 32f)
        val readableMax = maxSp.coerceIn(readableMin, 72f)
        val fitsAtMin = layoutFits(
            text, typeface, textColor, readableMin, widthPx, heightPx, maxLines, density, rtl,
        )
        val sizeSp = if (fitsAtMin) {
            largestFittingSize(
                text, typeface, textColor, readableMin, readableMax,
                widthPx, heightPx, maxLines, density, rtl,
            )
        } else {
            readableMin
        }
        val overflows = !fitsAtMin
        val layout = buildLayout(
            text = text,
            typeface = typeface,
            defaultColor = textColor,
            sizeSp = sizeSp,
            widthPx = widthPx,
            maxLines = if (overflows) UNBOUNDED_LINES else maxLines,
            density = density,
            rtl = rtl,
            ellipsize = !overflows,
        )
        val maxOffset = (layout.height - heightPx).coerceAtLeast(0)
        val offset = if (overflows) scrollOffsetPx.coerceIn(0, maxOffset) else 0

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.save()
        canvas.clipRect(0f, 0f, widthPx.toFloat(), heightPx.toFloat())
        val dy = if (overflows) {
            -offset.toFloat()
        } else {
            ((heightPx - layout.height) / 2f).coerceAtLeast(0f)
        }
        canvas.translate(0f, dy)
        layout.draw(canvas)
        canvas.restore()
        views.setImageViewBitmap(R.id.widget_ayah_image, bitmap)
        return RenderInfo(
            overflows = overflows && maxOffset > 0,
            contentHeightPx = layout.height,
            viewportHeightPx = heightPx,
        )
    }

    private fun largestFittingSize(
        text: CharSequence,
        typeface: Typeface,
        defaultColor: Int,
        minSp: Float,
        maxSp: Float,
        widthPx: Int,
        heightPx: Int,
        maxLines: Int,
        density: Float,
        rtl: Boolean,
    ): Float {
        var lo = minSp
        var hi = maxSp
        var best = minSp
        repeat(12) {
            val mid = (lo + hi) / 2f
            if (layoutFits(text, typeface, defaultColor, mid, widthPx, heightPx, maxLines, density, rtl)) {
                best = mid
                lo = mid
            } else {
                hi = mid
            }
        }
        return best
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
        rtl: Boolean,
    ): Boolean {
        val layout = buildLayout(
            text, typeface, defaultColor, sizeSp, widthPx, maxLines, density, rtl, ellipsize = true,
        )
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
        rtl: Boolean,
        ellipsize: Boolean,
    ): StaticLayout {
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            this.color = defaultColor
            textSize = sizeSp * density
            isSubpixelText = true
        }
        val builder = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, widthPx.coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setTextDirection(if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR)
            .setIncludePad(true)
            .setLineSpacing(0f, if (rtl) 1.2f else 1.15f)
            .setMaxLines(maxLines.coerceAtLeast(1))
        if (ellipsize) {
            builder.setEllipsize(TextUtils.TruncateAt.END)
        }
        return builder.build()
    }

    private const val UNBOUNDED_LINES = 200
}
