package com.quranwidget.hafalan.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Portable JSON backup of memorization progress.
 *
 * Minimum restore payload: remembered ayah set + current hafalan target
 * (surah/ayah pointer and related activity flags). Script edition is optional.
 */
data class ProgressBackup(
    val rememberedAyahs: Set<String>,
    val surahNumber: Int,
    val ayahNumber: Int,
    val lastActivityDate: String = "",
    val rememberedForCurrent: Boolean = false,
    val scriptEditionId: String? = null,
    val exportedAt: String = Instant.now().toString(),
) {
    fun toJson(): String {
        val root = JSONObject()
        root.put(KEY_FORMAT, FORMAT_ID)
        root.put(KEY_SCHEMA, SCHEMA_VERSION)
        root.put(KEY_EXPORTED_AT, exportedAt)
        root.put(
            KEY_REMEMBERED,
            JSONArray().also { arr ->
                rememberedAyahs.sorted().forEach { arr.put(it) }
            },
        )
        root.put(
            KEY_CURRENT,
            JSONObject().apply {
                put(KEY_SURAH, surahNumber)
                put(KEY_AYAH, ayahNumber)
                put(KEY_LAST_ACTIVITY, lastActivityDate)
                put(KEY_REMEMBERED_FOR_CURRENT, rememberedForCurrent)
            },
        )
        if (!scriptEditionId.isNullOrBlank()) {
            root.put(KEY_SCRIPT, scriptEditionId)
        }
        return root.toString(2)
    }

    companion object {
        const val FORMAT_ID = "quran-widget-hafalan-backup"
        const val SCHEMA_VERSION = 1
        const val MIME_TYPE = "application/json"
        const val SUGGESTED_FILE_NAME = "hafalan-progress-backup.json"

        private const val KEY_FORMAT = "format"
        private const val KEY_SCHEMA = "schemaVersion"
        private const val KEY_EXPORTED_AT = "exportedAt"
        private const val KEY_REMEMBERED = "rememberedAyahs"
        private const val KEY_CURRENT = "current"
        private const val KEY_SURAH = "surahNumber"
        private const val KEY_AYAH = "ayahNumber"
        private const val KEY_LAST_ACTIVITY = "lastActivityDate"
        private const val KEY_REMEMBERED_FOR_CURRENT = "rememberedForCurrent"
        private const val KEY_SCRIPT = "scriptEdition"

        fun fromJson(raw: String): ProgressBackup {
            val root = JSONObject(raw.trim())
            val format = root.optString(KEY_FORMAT, "")
            if (format.isNotEmpty() && format != FORMAT_ID) {
                throw IllegalArgumentException("Not a Hafalan progress backup file")
            }
            val remembered = linkedSetOf<String>()
            val array = root.optJSONArray(KEY_REMEMBERED)
                ?: throw IllegalArgumentException("Backup is missing remembered ayahs")
            for (i in 0 until array.length()) {
                val key = array.optString(i).trim()
                if (isValidAyahKey(key)) {
                    remembered += key
                }
            }
            val current = root.optJSONObject(KEY_CURRENT)
                ?: throw IllegalArgumentException("Backup is missing current target")
            val surah = current.optInt(KEY_SURAH, 0)
            val ayah = current.optInt(KEY_AYAH, 0)
            if (surah !in 1..114) {
                throw IllegalArgumentException("Backup has an invalid surah")
            }
            val surahInfo = SurahCatalog.get(surah)
            if (ayah !in 1..surahInfo.ayahCount) {
                throw IllegalArgumentException("Backup has an invalid ayah")
            }
            val script = root.optString(KEY_SCRIPT, "").ifBlank { null }
            return ProgressBackup(
                rememberedAyahs = remembered,
                surahNumber = surah,
                ayahNumber = ayah,
                lastActivityDate = current.optString(KEY_LAST_ACTIVITY, ""),
                rememberedForCurrent = current.optBoolean(KEY_REMEMBERED_FOR_CURRENT, false),
                scriptEditionId = script,
                exportedAt = root.optString(KEY_EXPORTED_AT, ""),
            )
        }

        private fun isValidAyahKey(key: String): Boolean {
            val parts = key.split(":")
            if (parts.size != 2) return false
            val s = parts[0].toIntOrNull() ?: return false
            val a = parts[1].toIntOrNull() ?: return false
            if (s !in 1..114) return false
            val surah = SurahCatalog.get(s)
            return a in 1..surah.ayahCount
        }
    }
}
