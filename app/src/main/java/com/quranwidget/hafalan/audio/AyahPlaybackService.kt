package com.quranwidget.hafalan.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.quranwidget.hafalan.HafalanApp
import com.quranwidget.hafalan.MainActivity
import com.quranwidget.hafalan.R
import com.quranwidget.hafalan.data.RepeatCounts
import com.quranwidget.hafalan.data.SurahCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground media playback for widget taps — stays on the home screen with a quiet
 * notification; never opens a fullscreen/black Activity.
 */
class AyahPlaybackService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: MediaPlayer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopPlayback()
                return START_NOT_STICKY
            }
            else -> startPlayback()
        }
        return START_NOT_STICKY
    }

    private fun startPlayback() {
        ensureChannel()
        val placeholder = buildNotification(
            title = getString(R.string.app_name),
            text = getString(R.string.playback_preparing),
        )
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            placeholder,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            },
        )

        scope.launch {
            try {
                val (ayah, speed, repeatCount) = withContext(Dispatchers.IO) {
                    val repo = HafalanApp.get().repository
                    Triple(
                        repo.loadAyahForPlayback(),
                        repo.playbackSpeed(),
                        repo.repeatCount(),
                    )
                }
                val url = ayah.audioUrl
                if (url.isNullOrBlank()) {
                    Toast.makeText(
                        this@AyahPlaybackService,
                        "Audio needs a connection to listen.",
                        Toast.LENGTH_SHORT,
                    ).show()
                    stopPlayback()
                    return@launch
                }

                val surah = SurahCatalog.get(ayah.surahNumber)
                val label = "${surah.nameTransliterated} · Ayah ${ayah.ayahNumber}"
                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(
                    NOTIFICATION_ID,
                    buildNotification(
                        title = label,
                        text = if (RepeatCounts.isRepeating(repeatCount)) {
                            getString(
                                R.string.playback_repeating_count_alafasy,
                                RepeatCounts.label(repeatCount),
                            )
                        } else {
                            getString(R.string.playback_playing_alafasy)
                        },
                    ),
                )

                releasePlayer()
                val counted = CountedAyahPlayer(repeatCount) {
                    stopPlayback()
                }
                val mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                    setDataSource(url)
                    counted.attach(this, speed)
                    setOnErrorListener { _, _, _ ->
                        Toast.makeText(
                            this@AyahPlaybackService,
                            "Playback failed",
                            Toast.LENGTH_SHORT,
                        ).show()
                        stopPlayback()
                        true
                    }
                    prepareAsync()
                }
                player = mediaPlayer
            } catch (e: Exception) {
                Toast.makeText(
                    this@AyahPlaybackService,
                    e.message ?: "Could not start audio",
                    Toast.LENGTH_SHORT,
                ).show()
                stopPlayback()
            }
        }
    }

    private fun stopPlayback() {
        releasePlayer()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releasePlayer() {
        player?.run {
            runCatching { stop() }
            runCatching { release() }
        }
        player = null
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.playback_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.playback_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(title: String, text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, AyahPlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, getString(R.string.playback_stop), stop)
            .build()
    }

    override fun onDestroy() {
        releasePlayer()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_PLAY = "com.quranwidget.hafalan.action.PLAY_AYAH"
        const val ACTION_STOP = "com.quranwidget.hafalan.action.STOP_AYAH"
        private const val CHANNEL_ID = "ayah_playback"
        private const val NOTIFICATION_ID = 41

        fun playIntent(context: Context): Intent =
            Intent(context, AyahPlaybackService::class.java).setAction(ACTION_PLAY)
    }
}
