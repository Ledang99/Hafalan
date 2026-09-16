package com.quranwidget.hafalan.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Arabic text + verse audio for Sheikh Mishari Rashid Al-Afasy.
 *
 * Primary: Quran.com API v4
 * - Text: /quran/verses/uthmani or /quran/verses/uthmani_simple
 * - Audio: recitation id 7 (Mishari Rashid al-`Afasy) via /recitations/7/by_ayah/{s}:{a}
 *   CDN base: https://verses.quran.com/
 *
 * Fallback: alquran.cloud edition `ar.alafasy` (same reciter), then bundled Al-Fatihah text.
 */
class QuranApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) {
    fun fetchAyah(
        surahNumber: Int,
        ayahNumber: Int,
        script: ScriptEdition = ScriptEdition.UTHMANI,
    ): AyahContent {
        return try {
            fetchFromQuranCom(surahNumber, ayahNumber, script)
        } catch (_: Exception) {
            fetchFromAlQuranCloudAlafasy(surahNumber, ayahNumber)
        }
    }

    fun fetchAyahOrFallback(
        surahNumber: Int,
        ayahNumber: Int,
        script: ScriptEdition = ScriptEdition.UTHMANI,
    ): AyahContent {
        return try {
            fetchAyah(surahNumber, ayahNumber, script)
        } catch (_: Exception) {
            OfflineFallback.ayah(surahNumber, ayahNumber)
                ?: AyahContent(
                    surahNumber = surahNumber,
                    ayahNumber = ayahNumber,
                    textArabic = "تعذّر تحميل الآية. تحقق من الاتصال.",
                    audioUrl = null,
                )
        }
    }

    private fun fetchFromQuranCom(
        surahNumber: Int,
        ayahNumber: Int,
        script: ScriptEdition,
    ): AyahContent {
        val verseKey = "$surahNumber:$ayahNumber"
        val text = fetchScriptText(surahNumber, ayahNumber, script)
        val audioUrl = fetchAlafasyAudioUrl(verseKey)
            ?: buildAlafasyCdnUrl(surahNumber, ayahNumber)
        return AyahContent(
            surahNumber = surahNumber,
            ayahNumber = ayahNumber,
            textArabic = text,
            audioUrl = audioUrl,
        )
    }

    private fun fetchScriptText(
        surahNumber: Int,
        ayahNumber: Int,
        script: ScriptEdition,
    ): String {
        val url =
            "https://api.quran.com/api/v4/quran/verses/${script.apiSegment}?verse_key=$surahNumber:$ayahNumber"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Quran.com text HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            val verses = JSONObject(body).getJSONArray("verses")
            if (verses.length() == 0) {
                throw IllegalStateException("No verse for $surahNumber:$ayahNumber")
            }
            val verse = verses.getJSONObject(0)
            return verse.optString(script.jsonField).takeIf { it.isNotBlank() }
                ?: verse.optString("text_uthmani").takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Missing text field for ${script.id}")
        }
    }

    /**
     * Quran.com recitation id 7 = Mishari Rashid al-`Afasy (verse-by-verse).
     * Relative paths are served from https://verses.quran.com/
     */
    private fun fetchAlafasyAudioUrl(verseKey: String): String? {
        val url = "https://api.quran.com/api/v4/recitations/$ALAFASY_RECITATION_ID/by_ayah/$verseKey"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            val files = JSONObject(body).optJSONArray("audio_files") ?: return null
            if (files.length() == 0) return null
            val relative = files.getJSONObject(0).optString("url").takeIf { it.isNotBlank() }
                ?: return null
            return if (relative.startsWith("http")) {
                relative
            } else {
                "$VERSES_CDN_BASE/${relative.trimStart('/')}"
            }
        }
    }

    /** Deterministic CDN path used by Quran.com for Alafasy verse clips. */
    private fun buildAlafasyCdnUrl(surahNumber: Int, ayahNumber: Int): String {
        val s = surahNumber.toString().padStart(3, '0')
        val a = ayahNumber.toString().padStart(3, '0')
        return "$VERSES_CDN_BASE/Alafasy/mp3/$s$a.mp3"
    }

    /** Same reciter via public edition identifier `ar.alafasy`. */
    private fun fetchFromAlQuranCloudAlafasy(surahNumber: Int, ayahNumber: Int): AyahContent {
        val url = "https://api.alquran.cloud/v1/ayah/$surahNumber:$ayahNumber/ar.alafasy"
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("alquran.cloud HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            val data = JSONObject(body).getJSONObject("data")
            val text = data.getString("text")
            val audio = data.optString("audio").takeIf { it.isNotBlank() }
                ?: buildAlafasyCdnUrl(surahNumber, ayahNumber)
            return AyahContent(
                surahNumber = surahNumber,
                ayahNumber = ayahNumber,
                textArabic = text,
                audioUrl = audio,
            )
        }
    }

    companion object {
        /** Quran.com: Mishari Rashid al-`Afasy */
        const val ALAFASY_RECITATION_ID = 7
        const val VERSES_CDN_BASE = "https://verses.quran.com"
        const val RECITER_DISPLAY_NAME = "Sheikh Mishari Rashid Al-Afasy"
    }
}
