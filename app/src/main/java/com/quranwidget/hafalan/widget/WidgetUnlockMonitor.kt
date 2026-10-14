package com.quranwidget.hafalan.widget

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.SystemClock
import com.quranwidget.hafalan.HafalanApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Listens for lock and unlock while this process is alive, and arms a backup
 * alarm so a killed process still conceals the widget on the next wake.
 */
object WidgetUnlockMonitor {
    const val ACTION_PRIVACY_CHECK = "com.quranwidget.hafalan.ACTION_PRIVACY_CHECK"

    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private const val CHECK_DELAY_MS = 3 * 60 * 1000L

    fun register(context: Context) {
        val app = context.applicationContext
        if (started.compareAndSet(false, true)) {
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val keyguard = app.getSystemService(KeyguardManager::class.java)
                if (keyguard != null) {
                    var wasLocked = keyguard.isKeyguardLocked
                    var skipInitial = true
                    keyguard.addKeyguardLockedStateListener(app.mainExecutor) { locked ->
                        if (skipInitial) {
                            skipInitial = false
                            wasLocked = locked
                            return@addKeyguardLockedStateListener
                        }
                        val changed = locked != wasLocked
                        wasLocked = locked
                        if (changed) concealAsync(app)
                    }
                }
            }
        }
        schedule(app)
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

    private fun concealAsync(context: Context) {
        val app = context.applicationContext as? HafalanApp ?: return
        scope.launch {
            app.repository.concealWidget()
        }
    }
}
