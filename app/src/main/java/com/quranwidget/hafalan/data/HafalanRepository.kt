package com.quranwidget.hafalan.data

import android.content.Context
import com.quranwidget.hafalan.widget.HafalanWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate

class HafalanRepository(
    context: Context,
    private val preferences: HafalanPreferences = HafalanPreferences(context),
    private val api: QuranApi = QuranApi(),
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    val state: StateFlow<HafalanState> = preferences.stateFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = HafalanState(),
    )

    init {
        scope.launch {
            applyDailyAdvanceIfNeeded()
            refreshAyahText(force = false)
        }
    }

    suspend fun currentState(): HafalanState = preferences.stateFlow.first()

    suspend fun selectSurah(surahNumber: Int) = mutex.withLock {
        val surah = SurahCatalog.get(surahNumber)
        val today = LocalDate.now().toString()
        val next = HafalanState(
            surahNumber = surah.number,
            ayahNumber = 1,
            lastActivityDate = today,
            rememberedForCurrent = false,
            cachedAyahText = "",
        )
        preferences.save(next)
        refreshAyahTextLocked(next, force = true)
        notifyWidget()
    }

    suspend fun markRemembered() = mutex.withLock {
        val current = applyDailyAdvanceLocked(preferences.stateFlow.first())
        val surah = SurahCatalog.get(current.surahNumber)
        val nextAyah = (current.ayahNumber + 1).coerceAtMost(surah.ayahCount)
        val today = LocalDate.now().toString()
        val next = current.copy(
            ayahNumber = nextAyah,
            lastActivityDate = today,
            rememberedForCurrent = true,
            cachedAyahText = if (nextAyah == current.ayahNumber) current.cachedAyahText else "",
        )
        preferences.save(next)
        refreshAyahTextLocked(next, force = nextAyah != current.ayahNumber)
        notifyWidget()
    }

    suspend fun applyDailyAdvanceIfNeeded(): HafalanState = mutex.withLock {
        val updated = applyDailyAdvanceLocked(preferences.stateFlow.first())
        preferences.save(updated)
        if (updated.cachedAyahText.isBlank()) {
            refreshAyahTextLocked(updated, force = true)
        }
        notifyWidget()
        updated
    }

    suspend fun refreshAyahText(force: Boolean = false): AyahContent = mutex.withLock {
        refreshAyahTextLocked(preferences.stateFlow.first(), force)
    }

    suspend fun loadAyahForPlayback(): AyahContent {
        val current = applyDailyAdvanceIfNeeded()
        return withContext(Dispatchers.IO) {
            api.fetchAyahOrFallback(current.surahNumber, current.ayahNumber)
        }
    }

    private suspend fun refreshAyahTextLocked(state: HafalanState, force: Boolean): AyahContent {
        if (!force && state.cachedAyahText.isNotBlank()) {
            return AyahContent(
                surahNumber = state.surahNumber,
                ayahNumber = state.ayahNumber,
                textArabic = state.cachedAyahText,
                audioUrl = null,
            )
        }
        val ayah = withContext(Dispatchers.IO) {
            api.fetchAyahOrFallback(state.surahNumber, state.ayahNumber)
        }
        preferences.save(state.copy(cachedAyahText = ayah.textArabic))
        notifyWidget()
        return ayah
    }

    /**
     * Daily advance rule:
     * - For each local calendar day since [HafalanState.lastActivityDate], if the current ayah
     *   was not marked remembered, advance one ayah (daily target rotation).
     * - After a remembered advance, that day does not also daily-advance; later missed days do.
     * - Stops at the last ayah of the surah.
     */
    private fun applyDailyAdvanceLocked(state: HafalanState): HafalanState {
        val today = LocalDate.now()
        if (state.lastActivityDate.isBlank()) {
            return state.copy(lastActivityDate = today.toString())
        }
        var date = LocalDate.parse(state.lastActivityDate)
        if (!date.isBefore(today)) return state

        var ayah = state.ayahNumber
        var remembered = state.rememberedForCurrent
        val surah = SurahCatalog.get(state.surahNumber)
        var textChanged = false

        while (date.isBefore(today)) {
            date = date.plusDays(1)
            if (!remembered && ayah < surah.ayahCount) {
                ayah += 1
                textChanged = true
            }
            remembered = false
        }

        return state.copy(
            ayahNumber = ayah,
            lastActivityDate = today.toString(),
            rememberedForCurrent = remembered,
            cachedAyahText = if (textChanged) "" else state.cachedAyahText,
        )
    }

    private fun notifyWidget() {
        HafalanWidgetProvider.requestUpdate(appContext)
    }
}
