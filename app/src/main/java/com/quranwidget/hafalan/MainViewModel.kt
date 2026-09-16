package com.quranwidget.hafalan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quranwidget.hafalan.data.HafalanRepository
import com.quranwidget.hafalan.data.HafalanState
import com.quranwidget.hafalan.data.PlaybackSpeeds
import com.quranwidget.hafalan.data.ProgressSummary
import com.quranwidget.hafalan.data.ScriptEdition
import com.quranwidget.hafalan.data.SurahCatalog
import com.quranwidget.hafalan.data.SurahInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppScreen { Home, Progress }

data class MainUiState(
    val hafalan: HafalanState = HafalanState(),
    val surah: SurahInfo = SurahCatalog.get(1),
    val ayahText: String = "",
    val loading: Boolean = true,
    val error: String? = null,
    val surahPickerOpen: Boolean = false,
    val playbackSpeed: Float = PlaybackSpeeds.NORMAL,
    val scriptEdition: ScriptEdition = ScriptEdition.UTHMANI,
    val repeatAyah: Boolean = false,
    val progress: ProgressSummary = ProgressSummary(),
    val screen: AppScreen = AppScreen.Home,
)

class MainViewModel(
    private val repository: HafalanRepository,
) : ViewModel() {
    private val loading = MutableStateFlow(true)
    private val error = MutableStateFlow<String?>(null)
    private val pickerOpen = MutableStateFlow(false)
    private val screen = MutableStateFlow(AppScreen.Home)

    private data class PrefBundle(
        val hafalan: HafalanState,
        val speed: Float,
        val script: ScriptEdition,
        val repeat: Boolean,
        val progress: ProgressSummary,
    )

    private val prefsFlow = combine(
        repository.state,
        repository.playbackSpeed,
        repository.scriptEdition,
        repository.repeatAyah,
        repository.progressSummary,
    ) { hafalan, speed, script, repeat, progress ->
        PrefBundle(hafalan, speed, script, repeat, progress)
    }

    val uiState: StateFlow<MainUiState> = combine(
        prefsFlow,
        loading,
        error,
        pickerOpen,
        screen,
    ) { prefs, isLoading, err, open, currentScreen ->
        MainUiState(
            hafalan = prefs.hafalan,
            surah = SurahCatalog.get(prefs.hafalan.surahNumber),
            ayahText = prefs.hafalan.cachedAyahText,
            loading = isLoading && prefs.hafalan.cachedAyahText.isBlank(),
            error = err,
            surahPickerOpen = open,
            playbackSpeed = prefs.speed,
            scriptEdition = prefs.script,
            repeatAyah = prefs.repeat,
            progress = prefs.progress,
            screen = currentScreen,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    init {
        viewModelScope.launch {
            runCatching {
                repository.applyDailyAdvanceIfNeeded()
                repository.refreshAyahText(force = false)
            }.onFailure {
                error.value = it.message ?: "Failed to load ayah"
            }
            loading.value = false
        }
    }

    fun openSurahPicker() {
        pickerOpen.value = true
    }

    fun closeSurahPicker() {
        pickerOpen.value = false
    }

    fun showProgress() {
        screen.value = AppScreen.Progress
    }

    fun showHome() {
        screen.value = AppScreen.Home
    }

    fun selectSurah(number: Int) {
        viewModelScope.launch {
            loading.value = true
            error.value = null
            runCatching {
                repository.selectSurah(number)
            }.onFailure {
                error.value = it.message ?: "Could not change surah"
            }
            pickerOpen.value = false
            loading.value = false
        }
    }

    fun markRemembered() {
        viewModelScope.launch {
            loading.value = true
            error.value = null
            runCatching {
                repository.markRemembered()
            }.onFailure {
                error.value = it.message ?: "Could not advance"
            }
            loading.value = false
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        viewModelScope.launch {
            repository.setPlaybackSpeed(speed)
        }
    }

    fun setScriptEdition(edition: ScriptEdition) {
        viewModelScope.launch {
            loading.value = true
            error.value = null
            runCatching {
                repository.setScriptEdition(edition)
            }.onFailure {
                error.value = it.message ?: "Could not change script"
            }
            loading.value = false
        }
    }

    fun setRepeatAyah(repeat: Boolean) {
        viewModelScope.launch {
            repository.setRepeatAyah(repeat)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            loading.value = true
            error.value = null
            runCatching {
                repository.applyDailyAdvanceIfNeeded()
                repository.refreshAyahText(force = true)
            }.onFailure {
                error.value = it.message ?: "Refresh failed"
            }
            loading.value = false
        }
    }

    class Factory(
        private val repository: HafalanRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(repository) as T
        }
    }
}
