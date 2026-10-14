package com.quranwidget.hafalan.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.quranwidget.hafalan.R
import com.quranwidget.hafalan.data.HafalanPreferences
import com.quranwidget.hafalan.data.HafalanState
import com.quranwidget.hafalan.data.ScriptEdition
import com.quranwidget.hafalan.data.WidgetAppearance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Supplies ayah bitmaps to the widget [android.widget.AdapterViewFlipper].
 * Binding this service while the home screen is visible keeps the process
 * alive so lock / unlock can hide the ayah.
 */
class WidgetAyahScrollService : RemoteViewsService() {
    override fun onCreate() {
        super.onCreate()
        WidgetUnlockMonitor.register(this)
    }

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        return AyahFramesFactory(applicationContext, widgetId)
    }

    override fun onDestroy() {
        val power = getSystemService(PowerManager::class.java)
        val screenOff = power != null && !power.isInteractive
        if (screenOff) {
            val transitioned = runBlocking(Dispatchers.IO) {
                val prefs = HafalanPreferences(applicationContext)
                if (prefs.widgetConcealed()) {
                    false
                } else {
                    prefs.setWidgetConcealed(true)
                    true
                }
            }
            if (transitioned) {
                HafalanWidgetProvider.pushUpdate(applicationContext)
            }
        }
        super.onDestroy()
    }
}

private class AyahFramesFactory(
    private val context: Context,
    private val appWidgetId: Int,
) : RemoteViewsService.RemoteViewsFactory {
    private var frames: List<android.graphics.Bitmap> = emptyList()

    override fun onCreate() {
        WidgetUnlockMonitor.register(context)
        load()
    }

    override fun onDataSetChanged() {
        load()
    }

    override fun onDestroy() {
        frames = emptyList()
    }

    override fun getCount(): Int = frames.size

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_ayah_frame)
        frames.getOrNull(position)?.let { bitmap ->
            views.setImageViewBitmap(R.id.widget_ayah_frame_image, bitmap)
        }
        // Merged into the mutable template — middle tap toggles Malay / reveals.
        views.setOnClickFillInIntent(R.id.widget_ayah_frame_image, Intent())
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true

    private fun load() {
        val prefs = HafalanPreferences(context)
        val snapshot = runBlocking(Dispatchers.IO) {
            WidgetSnapshot(
                state = prefs.stateFlow.first(),
                script = prefs.scriptEdition(),
                appearance = prefs.widgetAppearance(),
                showMalay = prefs.widgetShowMalay(),
                concealed = prefs.widgetConcealed(),
            )
        }
        if (snapshot.concealed || appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            frames = emptyList()
            return
        }
        val dark = snapshot.appearance.isDarkSurface()
        val textColor = if (dark) 0xFFF8F4EC.toInt() else 0xFF102A1F.toInt()
        val (text, uthmanic) = widgetAyahCharSequence(
            cachedArabic = snapshot.state.cachedAyahText,
            cachedMalay = snapshot.state.cachedMalayText,
            showMalay = snapshot.showMalay,
            script = snapshot.script,
            textColor = textColor,
            darkSurface = dark,
        )
        val metrics = ayahTileMetrics(
            AppWidgetManager.getInstance(context),
            appWidgetId,
        )
        frames = WidgetAyahFont.renderFrames(
            context = context,
            text = text,
            textColor = textColor,
            widthDp = metrics.widthDp,
            heightDp = metrics.heightDp,
            maxSp = metrics.maxSp,
            useUthmanic = uthmanic,
        )
    }
}

internal data class WidgetSnapshot(
    val state: HafalanState,
    val script: ScriptEdition,
    val appearance: WidgetAppearance,
    val showMalay: Boolean,
    val concealed: Boolean,
)

internal data class AyahTileMetrics(
    val widthDp: Int,
    val heightDp: Int,
    val maxSp: Float,
    val maxLines: Int,
)

internal fun ayahTileMetrics(
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
): AyahTileMetrics {
    val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
    val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
    val maxH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minH)
    val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180)
    val maxW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minW)
    val heightDp = maxOf(minH, maxH).coerceAtLeast(40)
    val widthDp = maxOf(minW, maxW).coerceAtLeast(48)
    val ayahHeightDp = (heightDp - 33).coerceAtLeast(36)
    val ayahWidthDp = (widthDp - 12).coerceAtLeast(40)
    val maxSp = (heightDp / 2.6f).coerceIn(28f, 72f)
    val maxLines = when {
        heightDp >= 220 -> 8
        heightDp >= 160 -> 6
        else -> 5
    }
    return AyahTileMetrics(ayahWidthDp, ayahHeightDp, maxSp, maxLines)
}
