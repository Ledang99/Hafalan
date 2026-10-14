package com.quranwidget.hafalan

import android.app.Application
import com.quranwidget.hafalan.data.HafalanRepository
import com.quranwidget.hafalan.widget.WidgetUnlockMonitor

class HafalanApp : Application() {
    lateinit var repository: HafalanRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        repository = HafalanRepository(this)
        // Privacy listener is optional. A failure here must not block the UI.
        runCatching { WidgetUnlockMonitor.register(this) }
    }

    companion object {
        @Volatile
        private var instance: HafalanApp? = null

        fun get(): HafalanApp =
            instance ?: throw IllegalStateException("HafalanApp not initialized")
    }
}
