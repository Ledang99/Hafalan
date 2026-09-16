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

    suspend fun playbackSpeed(): Float = playbackSpeedFlow.first()

    suspend fun scriptEdition(): ScriptEdition = scriptEditionFlow.first()

    suspend fun repeatAyah(): Boolean = repeatAyahFlow.first()

    suspend fun rememberedAyahs(): Set<String> = rememberedAyahsFlow.first()
}
