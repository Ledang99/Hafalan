package com.quranwidget.hafalan.widget

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.quranwidget.hafalan.HafalanApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Hides the widget ayah when the phone locks or the user is present again
 * (keyguard gone). The main app is not involved.
 */
class UnlockPrivacyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val pending = goAsync()
        scope.launch {
            try {
                val app = context.applicationContext as HafalanApp
                when (action) {
                    Intent.ACTION_SCREEN_OFF,
                    Intent.ACTION_USER_PRESENT,
                    -> app.repository.concealWidget()
                    WidgetUnlockMonitor.ACTION_PRIVACY_CHECK -> {
                        val power = context.getSystemService(PowerManager::class.java)
                        val keyguard = context.getSystemService(KeyguardManager::class.java)
                        val asleep = power != null && !power.isInteractive
                        val locked = keyguard != null && keyguard.isKeyguardLocked
                        if (asleep || locked) {
                            app.repository.concealWidget()
                            // If the flag was already set but RemoteViews were stale, push again.
                            HafalanWidgetProvider.pushUpdate(app)
                        }
                        WidgetUnlockMonitor.schedule(context.applicationContext)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
