package com.quranwidget.hafalan

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quranwidget.hafalan.data.AppThemeMode
import com.quranwidget.hafalan.data.HafalanRepository
import com.quranwidget.hafalan.data.HafalanState
import com.quranwidget.hafalan.data.PlaybackSpeeds
import com.quranwidget.hafalan.data.ProgressSummary
import com.quranwidget.hafalan.data.RepeatCounts
import com.quranwidget.hafalan.data.ScriptEdition
import com.quranwidget.hafalan.data.SurahCatalog
import com.quranwidget.hafalan.data.SurahInfo
import com.quranwidget.hafalan.data.WidgetAppearance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val repeatCount: Int = RepeatCounts.OFF,
    val progress: ProgressSummary = ProgressSummary(),
    val screen: AppScreen = AppScreen.Home,
    val appTheme: AppThemeMode = AppThemeMode.DAY,
    val widgetAppearance: WidgetAppearance = WidgetAppearance.defaultFor(AppThemeMode.DAY),
    val backupMessage: String? = null,
    val backupBusy: Boolean = false,
)

class MainViewModel(
    private val repository: HafalanRepository,
) : ViewModel() {
    private val loading = MutableStateFlow(true)
    private val error = MutableStateFlow<String?>(null)
    private val pickerOpen = MutableStateFlow(false)
    private val screen = MutableStateFlow(AppScreen.Home)
    private val backupMessage = MutableStateFlow<String?>(null)
    private val backupBusy = MutableStateFlow(false)

    private data class PrefBundle(
        val hafalan: HafalanState,
        val speed: Float,
        val script: ScriptEdition,
        val repeat: Int,
        val progress: ProgressSummary,
        val theme: AppThemeMode,
        val widget: WidgetAppearance,
    )

    private data class CorePrefs(
        val hafalan: HafalanState,
        val speed: Float,
        val script: ScriptEdition,
        val repeat: Int,
        val progress: ProgressSummary,
    )

    private val corePrefsFlow = combine(
        repository.state,
        repository.playbackSpeed,
        repository.scriptEdition,
        repository.repeatCount,
        repository.progressSummary,
    ) { hafalan, speed, script, repeat, progress ->
        CorePrefs(hafalan, speed, script, repeat, progress)
    }

    private val prefsFlow = combine(
        corePrefsFlow,
        repository.appTheme,
        repository.widgetAppearance,
    ) { core, theme, widget ->
        PrefBundle(
            hafalan = core.hafalan,
            speed = core.speed,
            script = core.script,
            repeat = core.repeat,
            progress = core.progress,
            theme = theme,
            widget = widget,
        )
    }

    private data class ScreenBundle(
        val prefs: PrefBundle,
        val isLoading: Boolean,
        val err: String?,
        val open: Boolean,
        val currentScreen: AppScreen,
    )

    private val screenFlow = combine(
        prefsFlow,
        loading,
        error,
        pickerOpen,
        screen,
    ) { prefs, isLoading, err, open, currentScreen ->
        ScreenBundle(prefs, isLoading, err, open, currentScreen)
    }

    val uiState: StateFlow<MainUiState> = combine(
        screenFlow,
        backupMessage,
        backupBusy,
    ) { core, message, busy ->
        MainUiState(
            hafalan = core.prefs.hafalan,
            surah = SurahCatalog.get(core.prefs.hafalan.surahNumber),
            ayahText = core.prefs.hafalan.cachedAyahText,
            loading = core.isLoading && core.prefs.hafalan.cachedAyahText.isBlank(),
            error = core.err,
            surahPickerOpen = core.open,
            playbackSpeed = core.prefs.speed,
            scriptEdition = core.prefs.script,
            repeatCount = core.prefs.repeat,
            progress = core.prefs.progress,
            screen = core.currentScreen,
            appTheme = core.prefs.theme,
            widgetAppearance = core.prefs.widget,
            backupMessage = message,
            backupBusy = busy,
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

    fun setRepeatCount(count: Int) {
        viewModelScope.launch {
            repository.setRepeatCount(count)
        }
    }

    fun setAppTheme(mode: AppThemeMode) {
        viewModelScope.launch {
            repository.setAppTheme(mode)
        }
    }

    fun setWidgetBackgroundColor(rgb: Int) {
        viewModelScope.launch {
            repository.setWidgetBackgroundColor(rgb)
        }
    }

    fun setWidgetBackgroundOpacity(percent: Int) {
        viewModelScope.launch {
            repository.setWidgetBackgroundOpacity(percent)
        }
    }

    fun clearBackupMessage() {
        backupMessage.value = null
    }

    fun exportProgressBackup(uri: Uri, resolver: ContentResolver) {
        viewModelScope.launch {
            backupBusy.value = true
            backupMessage.value = null
            runCatching {
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri)?.use { output ->
                        repository.writeProgressBackup(output)
                    } ?: error("Could not open file to write")
                }
            }.onSuccess {
                backupMessage.value = "Backup saved."
            }.onFailure {
                backupMessage.value = it.message ?: "Could not save backup"
            }
            backupBusy.value = false
        }
    }

    fun restoreProgressBackup(uri: Uri, resolver: ContentResolver) {
        viewModelScope.launch {
            backupBusy.value = true
            backupMessage.value = null
            loading.value = true
            runCatching {
                withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)?.use { input ->
                        repository.restoreProgressBackup(input)
                    } ?: error("Could not open backup file")
                }
            }.onSuccess {
                backupMessage.value = "Progress restored."
            }.onFailure {
                backupMessage.value = it.message ?: "Could not restore backup"
            }
            loading.value = false
            backupBusy.value = false
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
