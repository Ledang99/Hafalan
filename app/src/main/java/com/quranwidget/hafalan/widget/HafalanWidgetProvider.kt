package com.quranwidget.hafalan.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.quranwidget.hafalan.HafalanApp
import com.quranwidget.hafalan.R
import com.quranwidget.hafalan.audio.AyahPlaybackService
import com.quranwidget.hafalan.data.ScriptEdition
import com.quranwidget.hafalan.data.SurahCatalog
import com.quranwidget.hafalan.ui.TajweedMarkup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_WIDGET_REFRESH -> {
                // Immediate sync push from the app (script chip changes, etc.).
                pushUpdate(context.applicationContext)
            }
            ACTION_WIDGET_PLAY -> {
                startPlayback(context.applicationContext)
            }
            else -> super.onReceive(context, intent)
        }
    }

    companion object {
        const val ACTION_WIDGET_REFRESH = "com.quranwidget.hafalan.ACTION_WIDGET_REFRESH"
        const val ACTION_WIDGET_PLAY = "com.quranwidget.hafalan.ACTION_WIDGET_PLAY"

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

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_hafalan)
            val snapshot = runCatching {
                kotlinx.coroutines.runBlocking {
                    val repo = HafalanApp.get().repository
                    repo.currentState() to repo.scriptEdition()
                }
            }.getOrNull()

            if (snapshot == null) {
                views.setTextViewText(R.id.widget_surah, context.getString(R.string.widget_empty))
                views.setTextViewText(R.id.widget_ayah_meta, "")
                views.setTextViewText(R.id.widget_ayah_text, "…")
            } else {
                val (state, script) = snapshot
                val surah = SurahCatalog.get(state.surahNumber)
                views.setTextViewText(
                    R.id.widget_surah,
                    "${surah.nameTransliterated} · ${surah.nameArabic}  ·  " +
                        "Ayah ${state.ayahNumber}/${surah.ayahCount}",
                )
                views.setTextViewText(R.id.widget_ayah_meta, "")
                views.setTextViewText(
                    R.id.widget_ayah_text,
                    ayahDisplayText(context, state.cachedAyahText, script),
                )
            }

            val playPending = playPendingIntent(context, appWidgetId)
            views.setOnClickPendingIntent(R.id.widget_root, playPending)
            views.setOnClickPendingIntent(R.id.widget_surah, playPending)
            views.setOnClickPendingIntent(R.id.widget_ayah_meta, playPending)
            views.setOnClickPendingIntent(R.id.widget_ayah_text, playPending)
            views.setOnClickPendingIntent(R.id.widget_hint, playPending)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        /**
         * Uthmani → plain QPC Hafs text (strip any leftover tajweed tags).
         * Tajweed → colored spans on the dark widget surface.
         * Font itself comes from `widget_hafalan.xml` (`@font/uthmanic_hafs`).
         */
        private fun ayahDisplayText(
            context: Context,
            cached: String,
            script: ScriptEdition,
        ): CharSequence {
            val raw = cached.ifBlank { "…" }
            val defaultColor = ContextCompat.getColor(context, R.color.widget_text)
            return when (script) {
                ScriptEdition.TAJWEED -> {
                    if (TajweedMarkup.looksLikeMarkup(raw)) {
                        TajweedMarkup.toSpanned(raw, defaultColor, forDarkSurface = true)
                    } else {
                        raw
                    }
                }
                ScriptEdition.UTHMANI -> {
                    if (TajweedMarkup.looksLikeMarkup(raw)) {
                        TajweedMarkup.plainText(raw)
                    } else {
                        raw
                    }
                }
            }
        }
    }
}
