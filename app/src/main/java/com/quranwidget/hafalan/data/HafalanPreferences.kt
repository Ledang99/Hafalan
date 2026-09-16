package com.quranwidget.hafalan.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
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

    suspend fun save(state: HafalanState) {
        context.hafalanDataStore.edit { prefs ->
            prefs[Keys.surah] = state.surahNumber
            prefs[Keys.ayah] = state.ayahNumber
            prefs[Keys.lastActivityDate] = state.lastActivityDate
            prefs[Keys.rememberedForCurrent] = state.rememberedForCurrent
            prefs[Keys.cachedAyahText] = state.cachedAyahText
        }
    }
}
