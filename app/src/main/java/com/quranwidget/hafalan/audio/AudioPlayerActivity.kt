package com.quranwidget.hafalan.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quranwidget.hafalan.HafalanApp
import com.quranwidget.hafalan.data.AyahContent
import com.quranwidget.hafalan.data.SurahCatalog
import com.quranwidget.hafalan.ui.HafalanTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
}

private sealed interface PlayerUi {
    data object Loading : PlayerUi
    data class Ready(val ayah: AyahContent, val playing: Boolean) : PlayerUi
    data class Error(val message: String, val ayah: AyahContent?) : PlayerUi
}

@Composable
private fun AudioPlayerScreen(
    onClose: () -> Unit,
    onError: (String) -> Unit,
) {
    var ui by remember { mutableStateOf<PlayerUi>(PlayerUi.Loading) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    LaunchedEffect(Unit) {
        val ayah = withContext(Dispatchers.IO) {
            HafalanApp.get().repository.loadAyahForPlayback()
        }
        val url = ayah.audioUrl
        if (url.isNullOrBlank()) {
            ui = PlayerUi.Error(
                message = "Audio needs a connection. The ayah text is still here to read.",
                ayah = ayah,
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
                setOnPreparedListener {
                    start()
                    ui = PlayerUi.Ready(ayah, playing = true)
                }
                setOnCompletionListener {
                    ui = PlayerUi.Ready(ayah, playing = false)
                }
                setOnErrorListener { _, _, _ ->
                    ui = PlayerUi.Error("Playback failed", ayah)
                    true
                }
                prepareAsync()
            }
            mediaPlayer = player
            ui = PlayerUi.Ready(ayah, playing = false)
        } catch (e: Exception) {
            ui = PlayerUi.Error(e.message ?: "Could not start audio", ayah)
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
                    Text(
                        text = state.ayah.textArabic,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 28.sp,
                            lineHeight = 44.sp,
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = if (state.playing) {
                            "Listening · Sheikh Mishari Rashid Al-Afasy"
                        } else {
                            "Paused at the end — listen again anytime"
                        },
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                        Text("Close")
                    }
                }
                is PlayerUi.Error -> {
                    state.ayah?.let { ayah ->
                        Text(
                            text = ayah.textArabic,
                            style = MaterialTheme.typography.headlineSmall.copy(
                            ),
                            textAlign = TextAlign.Center,
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
