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

    val repeatCount: StateFlow<Int> = preferences.repeatCountFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = RepeatCounts.OFF,
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
            cachedMalayText = "",
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
        val sameAyah = nextAyah == current.ayahNumber
        val next = current.copy(
            ayahNumber = nextAyah,
            lastActivityDate = today,
            rememberedForCurrent = true,
            cachedAyahText = if (sameAyah) current.cachedAyahText else "",
            cachedMalayText = if (sameAyah) current.cachedMalayText else "",
        )
        preferences.save(next)
        refreshAyahTextLocked(next, force = nextAyah != current.ayahNumber)
        notifyWidget()
    }

    /**
     * Move the active target to the previous or next ayah inside the current surah.
     * Does not add to the remembered set — only [markRemembered] does that.
     * Widget follows because the shared pointer is what it renders.
     */
    suspend fun stepAyah(delta: Int) = mutex.withLock {
        if (delta == 0) return@withLock
        val advanced = applyDailyAdvanceLocked(preferences.stateFlow.first())
        val surah = SurahCatalog.get(advanced.surahNumber)
        val target = (advanced.ayahNumber + delta).coerceIn(1, surah.ayahCount)
        val moved = target != advanced.ayahNumber
        val today = LocalDate.now().toString()
        val next = advanced.copy(
            ayahNumber = target,
            lastActivityDate = today,
            rememberedForCurrent = if (moved) false else advanced.rememberedForCurrent,
            cachedAyahText = if (moved) "" else advanced.cachedAyahText,
            cachedMalayText = if (moved) "" else advanced.cachedMalayText,
        )
        preferences.save(next)
        if (moved || next.cachedAyahText.isBlank() || next.cachedMalayText.isBlank()) {
            refreshAyahTextLocked(next, force = moved || next.cachedAyahText.isBlank(), notify = false)
        }
        notifyWidget()
    }

    suspend fun isWidgetConcealed(): Boolean = preferences.widgetConcealed()

    /** Hide the widget ayah (logo only). Main app is unchanged. */
    suspend fun concealWidget() {
        val changed = !preferences.widgetConcealed()
        if (changed) {
            preferences.setWidgetConcealed(true)
            notifyWidget()
        }
    }

    /** Middle-tap after unlock: show the ayah again. */
    suspend fun revealWidget() {
        if (preferences.widgetConcealed()) {
            preferences.setWidgetConcealed(false)
        }
        notifyWidget()
    }

    /** Flip the widget ayah area between Arabic and Bahasa Melayu. */
    suspend fun toggleWidgetTranslation() {
        preferences.toggleWidgetShowMalay()
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

    suspend fun repeatCount(): Int = preferences.repeatCount()

    suspend fun setRepeatCount(count: Int) {
        preferences.setRepeatCount(count)
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
        if (!force && state.cachedAyahText.isNotBlank() && state.cachedMalayText.isNotBlank()) {
            return AyahContent(
                surahNumber = state.surahNumber,
                ayahNumber = state.ayahNumber,
                textArabic = state.cachedAyahText,
                audioUrl = null,
                textMalay = state.cachedMalayText,
            )
        }
        if (!force && state.cachedAyahText.isNotBlank()) {
            val malay = withContext(Dispatchers.IO) {
                api.fetchMalayOrEmpty(state.surahNumber, state.ayahNumber)
            }
            val saved = if (malay.isNotBlank()) {
                val updated = state.copy(cachedMalayText = malay)
                preferences.save(updated)
                updated
            } else {
                state
            }
            return AyahContent(
                surahNumber = saved.surahNumber,
                ayahNumber = saved.ayahNumber,
                textArabic = saved.cachedAyahText,
                audioUrl = null,
                textMalay = saved.cachedMalayText.ifBlank { null },
            )
        }
        val script = preferences.scriptEdition()
        val ayah = withContext(Dispatchers.IO) {
            api.fetchAyahOrFallback(state.surahNumber, state.ayahNumber, script)
        }
        val malay = ayah.textMalay?.takeIf { it.isNotBlank() } ?: state.cachedMalayText
        preferences.save(
            state.copy(
                cachedAyahText = ayah.textArabic,
                cachedMalayText = malay,
            ),
        )
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
            cachedMalayText = if (textChanged) "" else state.cachedMalayText,
        )
    }

    private fun notifyWidget() {
        // Direct RemoteViews push (no ephemeral provider coroutine race).
        HafalanWidgetProvider.pushUpdate(appContext)
    }
}
