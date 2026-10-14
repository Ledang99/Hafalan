package com.quranwidget.hafalan.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.quranwidget.hafalan.HafalanApp
import com.quranwidget.hafalan.R
import com.quranwidget.hafalan.audio.AyahPlaybackService
import com.quranwidget.hafalan.data.AppThemeMode
import com.quranwidget.hafalan.data.HafalanPreferences
import com.quranwidget.hafalan.data.SurahCatalog
import com.quranwidget.hafalan.data.WidgetAppearance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class HafalanWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        // Keep the broadcast alive — AppWidgetProvider instances are ephemeral, so an
        // instance-scoped coroutine can die before RemoteViews are pushed.
        val pending = goAsync()
        appScope.launch {
            try {
                runCatching { HafalanApp.get().repository.applyDailyAdvanceIfNeeded() }
                appWidgetIds.forEach { id ->
                    updateWidget(context.applicationContext, appWidgetManager, id)
                }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?,
    ) {
        // Re-push RemoteViews so Arabic auto-size remeasures for the new tile size.
        updateWidget(context.applicationContext, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_WIDGET_REFRESH -> {
                // Immediate sync push from the app (script chip changes, etc.).
                pushUpdate(context.applicationContext)
            }
            ACTION_WIDGET_PLAY -> {
                startPlayback(context.applicationContext)
            }
            ACTION_WIDGET_STOP -> {
                stopPlayback(context.applicationContext)
            }
            ACTION_WIDGET_MIDDLE -> {
                val pending = goAsync()
                appScope.launch {
                    try {
                        val repo = HafalanApp.get().repository
                        if (repo.isWidgetConcealed()) {
                            repo.revealWidget()
                        } else {
                            repo.toggleWidgetTranslation()
                        }
                    } finally {
                        pending.finish()
                    }
                }
            }
            else -> super.onReceive(context, intent)
        }
    }

    companion object {
        const val ACTION_WIDGET_REFRESH = "com.quranwidget.hafalan.ACTION_WIDGET_REFRESH"
        const val ACTION_WIDGET_PLAY = "com.quranwidget.hafalan.ACTION_WIDGET_PLAY"
        const val ACTION_WIDGET_STOP = "com.quranwidget.hafalan.ACTION_WIDGET_STOP"
        const val ACTION_WIDGET_MIDDLE = "com.quranwidget.hafalan.ACTION_WIDGET_MIDDLE"

        private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Preferred path from the running app — updates RemoteViews immediately. */
        fun pushUpdate(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = manager.getAppWidgetIds(
                ComponentName(appContext, HafalanWidgetProvider::class.java),
            )
            ids.forEach { id -> updateWidget(appContext, manager, id) }
        }

        fun requestUpdate(context: Context) {
            pushUpdate(context)
        }

        fun startPlayback(context: Context) {
            val intent = AyahPlaybackService.playIntent(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Cancels counted repeats / stops [AyahPlaybackService] (same as notification Stop). */
        fun stopPlayback(context: Context) {
            context.startService(AyahPlaybackService.stopIntent(context))
        }

        fun playPendingIntent(context: Context, appWidgetId: Int): PendingIntent {
            val intent = AyahPlaybackService.playIntent(context)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(
                    context,
                    appWidgetId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            } else {
                PendingIntent.getService(
                    context,
                    appWidgetId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }
        }

        fun stopPendingIntent(context: Context, appWidgetId: Int): PendingIntent {
            // Distinct requestCode from play so left/right targets do not collide.
            return PendingIntent.getService(
                context,
                appWidgetId + STOP_REQUEST_OFFSET,
                AyahPlaybackService.stopIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private const val STOP_REQUEST_OFFSET = 10_000
        private const val MIDDLE_REQUEST_OFFSET = 20_000

        fun middlePendingIntent(context: Context, appWidgetId: Int): PendingIntent {
            val intent = Intent(context, HafalanWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_MIDDLE
            }
            // Mutable so AdapterViewFlipper item fill-in intents can merge (API 31+).
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            return PendingIntent.getBroadcast(
                context,
                appWidgetId + MIDDLE_REQUEST_OFFSET,
                intent,
                flags,
            )
        }

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_hafalan)
            val snapshot = runCatching {
                runBlocking(Dispatchers.IO) {
                    val prefs = HafalanPreferences(context.applicationContext)
                    WidgetSnapshot(
                        state = prefs.stateFlow.first(),
                        script = prefs.scriptEdition(),
                        appearance = prefs.widgetAppearance(),
                        showMalay = prefs.widgetShowMalay(),
                        concealed = prefs.widgetConcealed(),
                    )
                }
            }.getOrNull()

            val appearance = snapshot?.appearance ?: WidgetAppearance.defaultFor(AppThemeMode.DARK)
            val darkSurface = appearance.isDarkSurface()
            val textColor = if (darkSurface) 0xFFF8F4EC.toInt() else 0xFF102A1F.toInt()
            val mutedColor = if (darkSurface) 0xFFB7C4B8.toInt() else 0xFF5A6B5E.toInt()
            val concealed = snapshot?.concealed == true

            views.setInt(R.id.widget_root, "setBackgroundColor", appearance.argb())
            views.setTextColor(R.id.widget_surah, mutedColor)
            views.setTextColor(R.id.widget_ayah_meta, mutedColor)
            views.setTextColor(R.id.widget_ayah_text, textColor)
            views.setTextColor(R.id.widget_stop, mutedColor)
            views.setTextColor(R.id.widget_middle, mutedColor)
            views.setTextColor(R.id.widget_hint, mutedColor)
            views.setTextColor(R.id.widget_privacy_title, textColor)
            views.setTextColor(R.id.widget_privacy_hint, mutedColor)
            views.setInt(R.id.widget_privacy_mark, "setColorFilter", textColor)

            if (snapshot == null) {
                views.setTextViewText(R.id.widget_surah, context.getString(R.string.widget_empty))
            } else if (concealed) {
                views.setTextViewText(R.id.widget_surah, context.getString(R.string.app_name))
            } else {
                val surah = SurahCatalog.get(snapshot.state.surahNumber)
                views.setTextViewText(
                    R.id.widget_surah,
                    "${surah.nameTransliterated} · ${surah.nameArabic}  ·  " +
                        "Ayah ${snapshot.state.ayahNumber}/${surah.ayahCount}",
                )
            }
            views.setTextViewText(R.id.widget_ayah_meta, "")
            views.setTextViewText(
                R.id.widget_middle,
                if (concealed) {
                    context.getString(R.string.widget_show_label)
                } else {
                    context.getString(R.string.widget_translation)
                },
            )

            val playPending = playPendingIntent(context, appWidgetId)
            val stopPending = stopPendingIntent(context, appWidgetId)
            val middlePending = middlePendingIntent(context, appWidgetId)

            // Do not bind play on the root — that swallows the left Stop target.
            views.setOnClickPendingIntent(R.id.widget_stop, stopPending)
            views.setOnClickPendingIntent(R.id.widget_hint, playPending)
            views.setOnClickPendingIntent(R.id.widget_surah, playPending)
            views.setOnClickPendingIntent(R.id.widget_middle, middlePending)
            views.setOnClickPendingIntent(R.id.widget_privacy, middlePending)
            views.setOnClickPendingIntent(R.id.widget_privacy_mark, middlePending)
            views.setOnClickPendingIntent(R.id.widget_privacy_title, middlePending)
            views.setOnClickPendingIntent(R.id.widget_privacy_hint, middlePending)

            if (concealed || snapshot == null) {
                views.setViewVisibility(R.id.widget_privacy, if (concealed) View.VISIBLE else View.GONE)
                views.setViewVisibility(R.id.widget_ayah_flipper, View.GONE)
                views.setViewVisibility(R.id.widget_ayah_image, View.VISIBLE)
                views.setViewVisibility(R.id.widget_ayah_text, View.GONE)
                if (snapshot == null) {
                    val metrics = ayahTileMetrics(appWidgetManager, appWidgetId)
                    WidgetAyahFont.apply(
                        context = context,
                        views = views,
                        text = "…",
                        textColor = textColor,
                        widthDp = metrics.widthDp,
                        heightDp = metrics.heightDp,
                        maxLines = metrics.maxLines,
                        maxSp = metrics.maxSp,
                    )
                } else {
                    views.setImageViewResource(R.id.widget_ayah_image, R.drawable.ic_hafalan_mark)
                    views.setViewVisibility(R.id.widget_ayah_image, View.GONE)
                }
            } else {
                views.setViewVisibility(R.id.widget_privacy, View.GONE)
                views.setViewVisibility(R.id.widget_ayah_image, View.GONE)
                views.setViewVisibility(R.id.widget_ayah_text, View.GONE)
                views.setViewVisibility(R.id.widget_ayah_flipper, View.VISIBLE)
                val serviceIntent = Intent(context, WidgetAyahScrollService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    data = Uri.parse("hafalan://widget/ayah/$appWidgetId")
                }
                @Suppress("DEPRECATION")
                views.setRemoteAdapter(appWidgetId, R.id.widget_ayah_flipper, serviceIntent)
                views.setPendingIntentTemplate(R.id.widget_ayah_flipper, middlePending)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
            if (!concealed && snapshot != null) {
                appWidgetManager.notifyAppWidgetViewDataChanged(
                    intArrayOf(appWidgetId),
                    R.id.widget_ayah_flipper,
                )
            }
        }
    }
}
