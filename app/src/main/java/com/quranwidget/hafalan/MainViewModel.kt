package com.quranwidget.hafalan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quranwidget.hafalan.data.HafalanRepository
import com.quranwidget.hafalan.data.HafalanState
import com.quranwidget.hafalan.data.SurahCatalog
import com.quranwidget.hafalan.data.SurahInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val hafalan: HafalanState = HafalanState(),
    val surah: SurahInfo = SurahCatalog.get(1),
    val ayahText: String = "",
    val loading: Boolean = true,
    val error: String? = null,
    val surahPickerOpen: Boolean = false,
)

class MainViewModel(
    private val repository: HafalanRepository,
) : ViewModel() {
    private val loading = MutableStateFlow(true)
    private val error = MutableStateFlow<String?>(null)
    private val pickerOpen = MutableStateFlow(false)

    val uiState: StateFlow<MainUiState> = combine(
        repository.state,
        loading,
        error,
        pickerOpen,
    ) { hafalan, isLoading, err, open ->
        MainUiState(
            hafalan = hafalan,
            surah = SurahCatalog.get(hafalan.surahNumber),
            ayahText = hafalan.cachedAyahText,
            loading = isLoading && hafalan.cachedAyahText.isBlank(),
            error = err,
            surahPickerOpen = open,
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
