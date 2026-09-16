package com.quranwidget.hafalan

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quranwidget.hafalan.audio.AudioPlayerActivity
import com.quranwidget.hafalan.ui.HafalanTheme
import com.quranwidget.hafalan.ui.MainScreen

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(HafalanApp.get().repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handlePlayIntent(intent)
        setContent {
            HafalanTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                MainScreen(
                    state = state,
                    onChooseSurah = viewModel::openSurahPicker,
                    onDismissSurahPicker = viewModel::closeSurahPicker,
                    onSurahSelected = viewModel::selectSurah,
                    onRemembered = viewModel::markRemembered,
                    onPlay = ::openPlayer,
                    onPlaybackSpeed = viewModel::setPlaybackSpeed,
                    onScriptEdition = viewModel::setScriptEdition,
                    onRepeatAyah = viewModel::setRepeatAyah,
                    onShowProgress = viewModel::showProgress,
                    onShowHome = viewModel::showHome,
                    onRefresh = viewModel::refresh,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handlePlayIntent(intent)
    }

    private fun handlePlayIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_PLAY, false) == true) {
            openPlayer()
        }
    }

    private fun openPlayer() {
        startActivity(Intent(this, AudioPlayerActivity::class.java))
    }

    companion object {
        const val EXTRA_PLAY = "extra_play"
    }
}
