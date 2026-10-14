package com.quranwidget.hafalan.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Listens for lock and unlock while this process is alive, and arms a backup
 * alarm so a killed process still conceals the widget on the next wake.
 */
object WidgetUnlockMonitor {
    const val ACTION_PRIVACY_CHECK = "com.quranwidget.hafalan.ACTION_PRIVACY_CHECK"

    private val started = AtomicBoolean(false)
    private const val CHECK_DELAY_MS = 3 * 60 * 1000L

    fun register(context: Context) {
        val app = context.applicationContext
        if (started.compareAndSet(false, true)) {
            // SCREEN_OFF and USER_PRESENT are enough. Do not call
            // KeyguardManager.addKeyguardLockedStateListener: it needs the signature
            // permission SUBSCRIBE_TO_KEYGUARD_LOCKED_STATE and throws SecurityException
            // on API 33+, which killed Application.onCreate before the first screen.
            runCatching {
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_USER_PRESENT)
                    addAction(Intent.ACTION_SCREEN_OFF)
                }
                val receiver = UnlockPrivacyReceiver()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    @Suppress("UnspecifiedRegisterReceiverFlag")
                    app.registerReceiver(receiver, filter)
                }
            }
        }
        // Alarm setup must not take the process down if the platform rejects it.
        runCatching { schedule(app) }
    }

    fun schedule(context: Context) {
        val app = context.applicationContext
        val alarm = app.getSystemService(AlarmManager::class.java) ?: return
        val pending = checkPendingIntent(app)
        val trigger = SystemClock.elapsedRealtime() + CHECK_DELAY_MS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pending)
        } else {
            alarm.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pending)
        }
    }

    private fun checkPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, UnlockPrivacyReceiver::class.java).apply {
            action = ACTION_PRIVACY_CHECK
        }
        return PendingIntent.getBroadcast(
            context,
            42,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
