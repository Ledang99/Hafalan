package com.quranwidget.hafalan.data

import android.content.Context
import com.quranwidget.hafalan.widget.HafalanWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
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

    val playbackSpeed: StateFlow<Float> = preferences.playbackSpeedFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = PlaybackSpeeds.NORMAL,
    )

    val scriptEdition: StateFlow<ScriptEdition> = preferences.scriptEditionFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = ScriptEdition.UTHMANI,
    )

    val repeatAyah: StateFlow<Boolean> = preferences.repeatAyahFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = false,
    )

    val rememberedAyahs: StateFlow<Set<String>> = preferences.rememberedAyahsFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = emptySet(),
    )

    val progressSummary: StateFlow<ProgressSummary> = rememberedAyahs.map { keys ->
        buildProgressSummary(keys)
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = ProgressSummary(),
    )

    val appTheme: StateFlow<AppThemeMode> = preferences.appThemeFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = AppThemeMode.DAY,
    )

    val widgetAppearance: StateFlow<WidgetAppearance> = preferences.widgetAppearanceFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = WidgetAppearance.defaultFor(AppThemeMode.DAY),
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
        // Persist this ayah in remembered history before advancing the active target.
        preferences.addRememberedAyah(current.surahNumber, current.ayahNumber)
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
        val script = preferences.scriptEdition()
        return withContext(Dispatchers.IO) {
            api.fetchAyahOrFallback(current.surahNumber, current.ayahNumber, script)
        }
    }

    suspend fun playbackSpeed(): Float = preferences.playbackSpeed()

    suspend fun setPlaybackSpeed(speed: Float) {
        preferences.setPlaybackSpeed(speed)
    }

    suspend fun scriptEdition(): ScriptEdition = preferences.scriptEdition()

    suspend fun setScriptEdition(edition: ScriptEdition) {
        mutex.withLock {
            preferences.setScriptEdition(edition)
            refreshAyahTextLocked(preferences.stateFlow.first(), force = true, notify = false)
        }
        // Push RemoteViews after the lock so the widget always sees the new script + text.
        notifyWidget()
    }

    suspend fun repeatAyah(): Boolean = preferences.repeatAyah()

    suspend fun setRepeatAyah(repeat: Boolean) {
        preferences.setRepeatAyah(repeat)
    }

    suspend fun appTheme(): AppThemeMode = preferences.appTheme()

    suspend fun setAppTheme(mode: AppThemeMode) {
        preferences.setAppTheme(mode)
        notifyWidget()
    }

    suspend fun widgetAppearance(): WidgetAppearance = preferences.widgetAppearance()

    suspend fun setWidgetBackgroundColor(rgb: Int) {
        preferences.setWidgetBackgroundColor(rgb)
        notifyWidget()
    }

    suspend fun setWidgetBackgroundOpacity(percent: Int) {
        preferences.setWidgetBackgroundOpacity(percent)
        notifyWidget()
    }

    fun progressFor(keys: Set<String> = rememberedAyahs.value): ProgressSummary =
        buildProgressSummary(keys)

    /** Build a JSON backup of remembered ayahs + current hafalan target (+ optional script). */
    suspend fun exportProgressBackup(): ProgressBackup {
        val state = preferences.stateFlow.first()
        val remembered = preferences.rememberedAyahs()
        val script = preferences.scriptEdition()
        return ProgressBackup(
            rememberedAyahs = remembered,
            surahNumber = state.surahNumber,
            ayahNumber = state.ayahNumber,
            lastActivityDate = state.lastActivityDate,
            rememberedForCurrent = state.rememberedForCurrent,
            scriptEditionId = script.id,
        )
    }

    suspend fun writeProgressBackup(output: OutputStream) {
        val json = exportProgressBackup().toJson()
        withContext(Dispatchers.IO) {
            output.bufferedWriter(Charsets.UTF_8).use { it.write(json) }
        }
    }

    /**
     * Replace local progress with the backup file contents, then refresh ayah text + widget.
     */
    suspend fun restoreProgressBackup(input: InputStream) = mutex.withLock {
        val raw = withContext(Dispatchers.IO) {
            input.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
        val backup = ProgressBackup.fromJson(raw)
        val restored = HafalanState(
            surahNumber = backup.surahNumber,
            ayahNumber = backup.ayahNumber,
            lastActivityDate = backup.lastActivityDate.ifBlank { LocalDate.now().toString() },
            rememberedForCurrent = backup.rememberedForCurrent,
            cachedAyahText = "",
        )
        preferences.restoreProgress(
            rememberedKeys = backup.rememberedAyahs,
            state = restored,
            scriptEditionId = backup.scriptEditionId,
        )
        refreshAyahTextLocked(restored, force = true, notify = false)
        notifyWidget()
    }

    private fun buildProgressSummary(keys: Set<String>): ProgressSummary {
        val perSurah = SurahCatalog.all.map { surah ->
            val count = keys.count { key ->
                val parts = key.split(":")
                parts.size == 2 && parts[0].toIntOrNull() == surah.number
            }
            SurahProgress(surah = surah, rememberedCount = count)
        }
        return ProgressSummary(
            rememberedKeys = keys,
            perSurah = perSurah,
            overallRemembered = keys.size,
        )
    }

    private suspend fun refreshAyahTextLocked(
        state: HafalanState,
        force: Boolean,
        notify: Boolean = true,
    ): AyahContent {
        if (!force && state.cachedAyahText.isNotBlank()) {
            return AyahContent(
                surahNumber = state.surahNumber,
                ayahNumber = state.ayahNumber,
                textArabic = state.cachedAyahText,
                audioUrl = null,
            )
        }
        val script = preferences.scriptEdition()
        val ayah = withContext(Dispatchers.IO) {
            api.fetchAyahOrFallback(state.surahNumber, state.ayahNumber, script)
        }
        preferences.save(state.copy(cachedAyahText = ayah.textArabic))
        if (notify) {
            notifyWidget()
        }
        return ayah
    }

    /**
     * Daily advance rule:
     * - For each local calendar day since [HafalanState.lastActivityDate], if the current ayah
     *   was not marked remembered, advance one ayah (daily target rotation).
     * - After a remembered advance, that day does not also daily-advance; later missed days do.
     * - Stops at the last ayah of the surah.
     * - Does NOT add to remembered history (only explicit Remembered does).
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
        // Direct RemoteViews push (no ephemeral provider coroutine race).
        HafalanWidgetProvider.pushUpdate(appContext)
    }
}
