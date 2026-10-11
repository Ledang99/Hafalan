package com.quranwidget.hafalan.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Pages a widget ayah that is taller than the tile: shows the top, then shifts
 * down every few seconds and returns to the top. Runs only while the screen is on.
 */
object WidgetAutoScroll {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()

    private var job: Job? = null
    private var offsetPx: Int = 0
    private var contentHeightPx: Int = 0
    private var viewportHeightPx: Int = 0
    private var overflows: Boolean = false
    private var screenOn: Boolean = true
    private var receiverRegistered: Boolean = false

    fun offsetPx(): Int = synchronized(lock) { offsetPx }

    /** Call when the ayah, script, or language changes so the next draw starts at the top. */
    fun reset() {
        synchronized(lock) { offsetPx = 0 }
        job?.cancel()
        job = null
    }

    fun onRendered(
        context: Context,
        contentHeightPx: Int,
        viewportHeightPx: Int,
        overflows: Boolean,
    ) {
        val appContext = context.applicationContext
        ensureScreenReceiver(appContext)
        synchronized(lock) {
            this.contentHeightPx = contentHeightPx
            this.viewportHeightPx = viewportHeightPx
            this.overflows = overflows
            val max = (contentHeightPx - viewportHeightPx).coerceAtLeast(0)
            if (!overflows) offsetPx = 0 else offsetPx = offsetPx.coerceIn(0, max)
        }
        if (overflows && screenOn) start(appContext) else stop()
    }

    private fun start(context: Context) {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                delay(PAGE_INTERVAL_MS)
                val draw = synchronized(lock) {
                    if (!overflows || !screenOn) return@synchronized false
                    val max = (contentHeightPx - viewportHeightPx).coerceAtLeast(0)
                    if (max == 0) return@synchronized false
                    val step = (viewportHeightPx * 0.72f).toInt().coerceAtLeast(24)
                    offsetPx = when {
                        offsetPx >= max -> 0
                        offsetPx + step >= max -> max
                        else -> offsetPx + step
                    }
                    true
                }
                if (!draw) break
                val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(
                    ComponentName(context, HafalanWidgetProvider::class.java),
                )
                if (ids.isEmpty()) break
                HafalanWidgetProvider.pushUpdate(context)
            }
        }
    }

    private fun stop() {
        job?.cancel()
        job = null
    }

    private fun ensureScreenReceiver(context: Context) {
        if (receiverRegistered) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        screenOn = false
                        stop()
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        screenOn = true
                        val resume = synchronized(lock) { overflows }
                        if (resume) start(ctx.applicationContext)
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        receiverRegistered = true
    }

    private const val PAGE_INTERVAL_MS = 4_500L
}
