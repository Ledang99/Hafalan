package com.quranwidget.hafalan.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quranwidget.hafalan.HafalanApp
import com.quranwidget.hafalan.data.AyahContent
import com.quranwidget.hafalan.data.PlaybackSpeeds
import com.quranwidget.hafalan.data.ScriptEdition
import com.quranwidget.hafalan.data.SurahCatalog
import com.quranwidget.hafalan.ui.AyahArabicText
import com.quranwidget.hafalan.ui.HafalanTheme
import com.quranwidget.hafalan.ui.QuranFonts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * In-app Listen screen with ayah text + Al-Afasy playback.
 * Widget taps use [AyahPlaybackService] instead (no Activity / no black screen).
 */
class AudioPlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HafalanTheme {
                AudioPlayerScreen(
                    onClose = { finish() },
                    onError = { message ->
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    },
                )
            }
        }
    }

    companion object {
        /** Kept for older PendingIntents; widget now starts [AyahPlaybackService]. */
        const val EXTRA_HEADLESS = "extra_headless"
    }
}

internal fun applyPlaybackSpeed(player: MediaPlayer, speed: Float) {
    val rate = PlaybackSpeeds.normalize(speed)
    runCatching {
        player.playbackParams = PlaybackParams().setSpeed(rate)
    }
}

private sealed interface PlayerUi {
    data object Loading : PlayerUi
    data class Ready(
        val ayah: AyahContent,
        val playing: Boolean,
        val speed: Float,
        val repeating: Boolean,
        val script: ScriptEdition,
    ) : PlayerUi

    data class Error(
        val message: String,
        val ayah: AyahContent?,
        val script: ScriptEdition = ScriptEdition.UTHMANI,
    ) : PlayerUi
}

@Composable
private fun AudioPlayerScreen(
    onClose: () -> Unit,
    onError: (String) -> Unit,
) {
    var ui by remember { mutableStateOf<PlayerUi>(PlayerUi.Loading) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    LaunchedEffect(Unit) {
        data class Load(
            val ayah: AyahContent,
            val speed: Float,
            val repeat: Boolean,
            val script: ScriptEdition,
        )
        val loaded = withContext(Dispatchers.IO) {
            val repo = HafalanApp.get().repository
            Load(
                ayah = repo.loadAyahForPlayback(),
                speed = repo.playbackSpeed(),
                repeat = repo.repeatAyah(),
                script = repo.scriptEdition(),
            )
        }
        val ayah = loaded.ayah
        val speed = loaded.speed
        val repeat = loaded.repeat
        val script = loaded.script
        val url = ayah.audioUrl
        if (url.isNullOrBlank()) {
            ui = PlayerUi.Error(
                message = "Audio needs a connection. The ayah text is still here to read.",
                ayah = ayah,
                script = script,
            )
            onError("No audio URL (offline or API fallback)")
            return@LaunchedEffect
        }
        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                setDataSource(url)
                isLooping = repeat
                setOnPreparedListener {
                    applyPlaybackSpeed(this, speed)
                    start()
                    ui = PlayerUi.Ready(
                        ayah = ayah,
                        playing = true,
                        speed = speed,
                        repeating = repeat,
                        script = script,
                    )
                }
                setOnCompletionListener {
                    if (!isLooping) {
                        ui = PlayerUi.Ready(
                            ayah = ayah,
                            playing = false,
                            speed = speed,
                            repeating = repeat,
                            script = script,
                        )
                    }
                }
                setOnErrorListener { _, _, _ ->
                    ui = PlayerUi.Error("Playback failed", ayah, script)
                    true
                }
                prepareAsync()
            }
            mediaPlayer = player
            ui = PlayerUi.Ready(
                ayah = ayah,
                playing = false,
                speed = speed,
                repeating = repeat,
                script = script,
            )
        } catch (e: Exception) {
            ui = PlayerUi.Error(e.message ?: "Could not start audio", ayah, script)
            onError(e.message ?: "Playback error")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.run {
                runCatching { stop() }
                release()
            }
            mediaPlayer = null
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (val state = ui) {
                PlayerUi.Loading -> CircularProgressIndicator()
                is PlayerUi.Ready -> {
                    val surah = SurahCatalog.get(state.ayah.surahNumber)
                    Text(
                        text = "${surah.nameTransliterated} · Ayah ${state.ayah.ayahNumber}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(20.dp))
                    AyahArabicText(
                        text = state.ayah.textArabic,
                        script = state.script,
                        modifier = Modifier.fillMaxWidth(),
                        style = TextStyle(
                            fontFamily = QuranFonts.UthmanicHafs,
                            fontSize = 30.sp,
                            lineHeight = 50.sp,
                            textAlign = TextAlign.Center,
                            textDirection = TextDirection.Rtl,
                            color = MaterialTheme.colorScheme.onBackground,
                        ),
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = when {
                            state.playing && state.repeating ->
                                "Looping · Al-Afasy · ${PlaybackSpeeds.label(state.speed)}"
                            state.playing ->
                                "Listening · Al-Afasy · ${PlaybackSpeeds.label(state.speed)}"
                            else ->
                                "Paused at the end — listen again anytime"
                        },
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                        Text("Close")
                    }
                }
                is PlayerUi.Error -> {
                    state.ayah?.let { ayah ->
                        AyahArabicText(
                            text = ayah.textArabic,
                            script = state.script,
                            modifier = Modifier.fillMaxWidth(),
                            style = TextStyle(
                                fontFamily = QuranFonts.UthmanicHafs,
                                fontSize = 26.sp,
                                lineHeight = 42.sp,
                                textAlign = TextAlign.Center,
                                textDirection = TextDirection.Rtl,
                                color = MaterialTheme.colorScheme.onBackground,
                            ),
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                    Text(text = state.message, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                        Text("Close")
                    }
                }
            }
        }
    }
}
