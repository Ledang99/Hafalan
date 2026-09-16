package com.quranwidget.hafalan.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.hafalanDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "hafalan_prefs",
)

class HafalanPreferences(private val context: Context) {
    private object Keys {
        val surah = intPreferencesKey("surah_number")
        val ayah = intPreferencesKey("ayah_number")
        val lastActivityDate = stringPreferencesKey("last_activity_date")
        val rememberedForCurrent = booleanPreferencesKey("remembered_for_current")
        val cachedAyahText = stringPreferencesKey("cached_ayah_text")
        val playbackSpeed = floatPreferencesKey("playback_speed")
        val scriptEdition = stringPreferencesKey("script_edition")
        val repeatAyah = booleanPreferencesKey("repeat_ayah")
        val rememberedAyahs = stringSetPreferencesKey("remembered_ayahs")
        val appTheme = stringPreferencesKey("app_theme")
        val widgetBgColor = intPreferencesKey("widget_bg_color")
        val widgetBgOpacity = intPreferencesKey("widget_bg_opacity")
        val widgetBgCustomized = booleanPreferencesKey("widget_bg_customized")
    }

    val stateFlow: Flow<HafalanState> = context.hafalanDataStore.data.map { prefs ->
        HafalanState(
            surahNumber = prefs[Keys.surah] ?: 1,
            ayahNumber = prefs[Keys.ayah] ?: 1,
            lastActivityDate = prefs[Keys.lastActivityDate].orEmpty(),
            rememberedForCurrent = prefs[Keys.rememberedForCurrent] ?: false,
            cachedAyahText = prefs[Keys.cachedAyahText].orEmpty(),
        )
    }

    val playbackSpeedFlow: Flow<Float> = context.hafalanDataStore.data.map { prefs ->
        PlaybackSpeeds.normalize(prefs[Keys.playbackSpeed] ?: PlaybackSpeeds.NORMAL)
    }

    val scriptEditionFlow: Flow<ScriptEdition> = context.hafalanDataStore.data.map { prefs ->
        ScriptEdition.fromId(prefs[Keys.scriptEdition])
    }

    val repeatAyahFlow: Flow<Boolean> = context.hafalanDataStore.data.map { prefs ->
        prefs[Keys.repeatAyah] ?: false
    }

    val rememberedAyahsFlow: Flow<Set<String>> = context.hafalanDataStore.data.map { prefs ->
        prefs[Keys.rememberedAyahs] ?: emptySet()
    }

    val appThemeFlow: Flow<AppThemeMode> = context.hafalanDataStore.data.map { prefs ->
        AppThemeMode.fromId(prefs[Keys.appTheme])
    }

    val widgetAppearanceFlow: Flow<WidgetAppearance> = context.hafalanDataStore.data.map { prefs ->
        val theme = AppThemeMode.fromId(prefs[Keys.appTheme])
        val customized = prefs[Keys.widgetBgCustomized] ?: false
        val default = WidgetAppearance.defaultFor(theme)
        WidgetAppearance(
            backgroundColorRgb = if (customized) {
                prefs[Keys.widgetBgColor] ?: default.backgroundColorRgb
            } else {
                default.backgroundColorRgb
            },
            opacityPercent = (prefs[Keys.widgetBgOpacity] ?: 100).coerceIn(0, 100),
        )
    }

    suspend fun save(state: HafalanState) {
        context.hafalanDataStore.edit { prefs ->
            prefs[Keys.surah] = state.surahNumber
            prefs[Keys.ayah] = state.ayahNumber
            prefs[Keys.lastActivityDate] = state.lastActivityDate
            prefs[Keys.rememberedForCurrent] = state.rememberedForCurrent
            prefs[Keys.cachedAyahText] = state.cachedAyahText
        }
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        context.hafalanDataStore.edit { prefs ->
            prefs[Keys.playbackSpeed] = PlaybackSpeeds.normalize(speed)
        }
    }

    suspend fun setScriptEdition(edition: ScriptEdition) {
        context.hafalanDataStore.edit { prefs ->
            prefs[Keys.scriptEdition] = edition.id
            // Force text refresh for the new script.
            prefs[Keys.cachedAyahText] = ""
        }
    }

    suspend fun setRepeatAyah(repeat: Boolean) {
        context.hafalanDataStore.edit { prefs ->
            prefs[Keys.repeatAyah] = repeat
        }
    }

    suspend fun addRememberedAyah(surahNumber: Int, ayahNumber: Int) {
        val key = ayahKey(surahNumber, ayahNumber)
        context.hafalanDataStore.edit { prefs ->
            val current = prefs[Keys.rememberedAyahs] ?: emptySet()
            prefs[Keys.rememberedAyahs] = current + key
        }
    }

    /** Replace the entire remembered set (used by restore). */
    suspend fun replaceRememberedAyahs(keys: Set<String>) {
        context.hafalanDataStore.edit { prefs ->
            prefs[Keys.rememberedAyahs] = keys
        }
    }

    /**
     * Apply a progress backup: replace remembered set and current hafalan target.
     * Clears cached ayah text so the restored pointer is refreshed.
     * Optionally restores script edition when [scriptEditionId] is present.
     */
    suspend fun restoreProgress(
        rememberedKeys: Set<String>,
        state: HafalanState,
        scriptEditionId: String? = null,
    ) {
        context.hafalanDataStore.edit { prefs ->
            prefs[Keys.rememberedAyahs] = rememberedKeys
            prefs[Keys.surah] = state.surahNumber
            prefs[Keys.ayah] = state.ayahNumber
            prefs[Keys.lastActivityDate] = state.lastActivityDate
            prefs[Keys.rememberedForCurrent] = state.rememberedForCurrent
            prefs[Keys.cachedAyahText] = ""
            if (!scriptEditionId.isNullOrBlank()) {
                prefs[Keys.scriptEdition] = ScriptEdition.fromId(scriptEditionId).id
            }
        }
    }

    suspend fun setAppTheme(mode: AppThemeMode) {
        context.hafalanDataStore.edit { prefs ->
            prefs[Keys.appTheme] = mode.id
            // When theme changes and user has not customized bg, reset color key
            // so flow defaults track the new theme.
            if (prefs[Keys.widgetBgCustomized] != true) {
                prefs.remove(Keys.widgetBgColor)
            }
        }
    }

    suspend fun setWidgetBackgroundColor(rgb: Int) {
        context.hafalanDataStore.edit { prefs ->
            prefs[Keys.widgetBgColor] = rgb and 0x00FFFFFF
            prefs[Keys.widgetBgCustomized] = true
        }
    }

    suspend fun setWidgetBackgroundOpacity(percent: Int) {
        context.hafalanDataStore.edit { prefs ->
            prefs[Keys.widgetBgOpacity] = percent.coerceIn(0, 100)
        }
    }

    suspend fun playbackSpeed(): Float = playbackSpeedFlow.first()

    suspend fun scriptEdition(): ScriptEdition = scriptEditionFlow.first()

    suspend fun repeatAyah(): Boolean = repeatAyahFlow.first()

    suspend fun rememberedAyahs(): Set<String> = rememberedAyahsFlow.first()

    suspend fun appTheme(): AppThemeMode = appThemeFlow.first()

    suspend fun widgetAppearance(): WidgetAppearance = widgetAppearanceFlow.first()
}
