package com.quranwidget.hafalan.audio

import android.media.MediaPlayer
import com.quranwidget.hafalan.data.RepeatCounts

/**
 * Finite ayah repeats: plays [repeatCount] times then invokes [onFinished].
 * Never uses [MediaPlayer.isLooping] (no infinite loop).
 */
class CountedAyahPlayer(
    repeatCount: Int,
    private val onStarted: (() -> Unit)? = null,
    private val onFinished: () -> Unit,
) {
    private val targetPlays = RepeatCounts.normalize(repeatCount)
    private var completedPlays = 0

    fun attach(player: MediaPlayer, speed: Float) {
        player.isLooping = false
        bindCompletion(player, speed)
        player.setOnPreparedListener {
            applyPlaybackSpeed(player, speed)
            player.start()
            onStarted?.invoke()
        }
    }

    /** Restart from the beginning for another full counted run (Listen Repeat). */
    fun restart(player: MediaPlayer, speed: Float) {
        completedPlays = 0
        player.isLooping = false
        bindCompletion(player, speed)
        runCatching {
            player.seekTo(0)
            applyPlaybackSpeed(player, speed)
            player.start()
            onStarted?.invoke()
        }.onFailure {
            onFinished()
        }
    }

    private fun bindCompletion(player: MediaPlayer, speed: Float) {
        player.setOnCompletionListener {
            completedPlays++
            if (completedPlays < targetPlays) {
                runCatching {
                    player.seekTo(0)
                    applyPlaybackSpeed(player, speed)
                    player.start()
                }.onFailure {
                    onFinished()
                }
            } else {
                onFinished()
            }
        }
    }

    val isRepeating: Boolean get() = RepeatCounts.isRepeating(targetPlays)
    val repeatCount: Int get() = targetPlays
}
