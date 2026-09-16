package com.quranwidget.hafalan.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.quranwidget.hafalan.HafalanApp
import com.quranwidget.hafalan.MainActivity
import com.quranwidget.hafalan.R
import com.quranwidget.hafalan.audio.AudioPlayerActivity
import com.quranwidget.hafalan.data.SurahCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HafalanWidgetProvider : AppWidgetProvider() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        scope.launch {
            runCatching { HafalanApp.get().repository.applyDailyAdvanceIfNeeded() }
            appWidgetIds.forEach { id ->
                updateWidget(context, appWidgetManager, id)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_WIDGET_REFRESH, AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(
                    ComponentName(context, HafalanWidgetProvider::class.java),
                )
                onUpdate(context, manager, ids)
            }
            ACTION_WIDGET_PLAY -> {
                val play = Intent(context, AudioPlayerActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(play)
            }
        }
    }

    companion object {
        const val ACTION_WIDGET_REFRESH = "com.quranwidget.hafalan.ACTION_WIDGET_REFRESH"
        const val ACTION_WIDGET_PLAY = "com.quranwidget.hafalan.ACTION_WIDGET_PLAY"

        fun requestUpdate(context: Context) {
            val intent = Intent(context, HafalanWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_REFRESH
            }
            context.sendBroadcast(intent)
        }

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_hafalan)
            val state = runCatching {
                // Blocking read is OK on IO dispatcher callers; for sync path use cache.
                kotlinx.coroutines.runBlocking {
                    HafalanApp.get().repository.currentState()
                }
            }.getOrNull()

            if (state == null) {
                views.setTextViewText(R.id.widget_surah, context.getString(R.string.widget_empty))
                views.setTextViewText(R.id.widget_ayah_meta, "")
                views.setTextViewText(R.id.widget_ayah_text, "…")
            } else {
                val surah = SurahCatalog.get(state.surahNumber)
                views.setTextViewText(
                    R.id.widget_surah,
                    "${surah.nameTransliterated} · ${surah.nameArabic}",
                )
                views.setTextViewText(
                    R.id.widget_ayah_meta,
                    "Ayah ${state.ayahNumber} / ${surah.ayahCount}",
                )
                views.setTextViewText(
                    R.id.widget_ayah_text,
                    state.cachedAyahText.ifBlank { "…" },
                )
            }

            val playIntent = Intent(context, AudioPlayerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val playPending = PendingIntent.getActivity(
                context,
                appWidgetId,
                playIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, playPending)
            views.setOnClickPendingIntent(R.id.widget_ayah_text, playPending)

            // Long-press / empty area also opens app as fallback via title tap alternate:
            val openApp = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPending = PendingIntent.getActivity(
                context,
                appWidgetId + 1000,
                openApp,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_surah, openPending)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
