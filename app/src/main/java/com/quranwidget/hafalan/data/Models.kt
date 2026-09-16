package com.quranwidget.hafalan.data

data class SurahInfo(
    val number: Int,
    val nameArabic: String,
    val nameTransliterated: String,
    val ayahCount: Int,
)

data class AyahContent(
    val surahNumber: Int,
    val ayahNumber: Int,
    val textArabic: String,
    val audioUrl: String?,
)

data class HafalanState(
    val surahNumber: Int = 1,
    val ayahNumber: Int = 1,
    /** Local calendar date (yyyy-MM-dd) of the last remembered or daily advance. */
    val lastActivityDate: String = "",
    /** True if the current ayah was marked remembered since it became current. */
    val rememberedForCurrent: Boolean = false,
    /** Cached Arabic text for the widget / offline display. */
    val cachedAyahText: String = "",
)

/** Supported Al-Afasy playback rates (persisted and applied for app + widget). */
object PlaybackSpeeds {
    const val NORMAL = 1.0f
    const val FAST = 1.5f
    const val FASTER = 2.0f

    val all: List<Float> = listOf(NORMAL, FAST, FASTER)

    fun normalize(speed: Float): Float = when {
        speed >= 1.75f -> FASTER
        speed >= 1.25f -> FAST
        else -> NORMAL
    }

    fun label(speed: Float): String = when (normalize(speed)) {
        FAST -> "1.5×"
        FASTER -> "2×"
        else -> "1×"
    }
}

/**
 * Arabic script editions from Quran.com API v4.
 *
 * - Uthmani: QPC Hafs Unicode (`text_qpc_hafs`) rendered with shipped KFGQPC
 *   Uthmanic Hafs (King Fahad Complex) font.
 * - Tajweed: `text_uthmani_tajweed` with HTML color tags (best-effort in app + widget).
 *
 * Legacy ids `simple` / `uthmani_simple` map to Uthmani.
 */
enum class ScriptEdition(
    val id: String,
    val label: String,
    val apiSegment: String,
    val jsonField: String,
    val usesTajweedMarkup: Boolean = false,
) {
    UTHMANI("uthmani", "Uthmani", "qpc_hafs", "text_qpc_hafs"),
    TAJWEED(
        id = "tajweed",
        label = "Tajweed",
        apiSegment = "uthmani_tajweed",
        jsonField = "text_uthmani_tajweed",
        usesTajweedMarkup = true,
    ),
    ;

    companion object {
        fun fromId(id: String?): ScriptEdition = when (id) {
            TAJWEED.id -> TAJWEED
            "simple", "uthmani_simple" -> UTHMANI
            else -> entries.firstOrNull { it.id == id } ?: UTHMANI
        }
    }
}

/** Verse key format used in remembered history: "surah:ayah". */
fun ayahKey(surahNumber: Int, ayahNumber: Int): String = "$surahNumber:$ayahNumber"

data class SurahProgress(
    val surah: SurahInfo,
    val rememberedCount: Int,
) {
    val percent: Float
        get() = if (surah.ayahCount == 0) 0f else rememberedCount.toFloat() / surah.ayahCount
}

data class ProgressSummary(
    val rememberedKeys: Set<String> = emptySet(),
    val perSurah: List<SurahProgress> = emptyList(),
    val overallRemembered: Int = 0,
) {
    val overallPercent: Float
        get() = overallRemembered.toFloat() / TOTAL_QURAN_AYAHS

    companion object {
        const val TOTAL_QURAN_AYAHS = 6236
    }
}

object SurahCatalog {
    val all: List<SurahInfo> = listOf(
        SurahInfo(1, "الفاتحة", "Al-Fatihah", 7),
        SurahInfo(2, "البقرة", "Al-Baqarah", 286),
        SurahInfo(3, "آل عمران", "Ali 'Imran", 200),
        SurahInfo(4, "النساء", "An-Nisa", 176),
        SurahInfo(5, "المائدة", "Al-Ma'idah", 120),
        SurahInfo(6, "الأنعام", "Al-An'am", 165),
        SurahInfo(7, "الأعراف", "Al-A'raf", 206),
        SurahInfo(8, "الأنفال", "Al-Anfal", 75),
        SurahInfo(9, "التوبة", "At-Tawbah", 129),
        SurahInfo(10, "يونس", "Yunus", 109),
        SurahInfo(11, "هود", "Hud", 123),
        SurahInfo(12, "يوسف", "Yusuf", 111),
        SurahInfo(13, "الرعد", "Ar-Ra'd", 43),
        SurahInfo(14, "إبراهيم", "Ibrahim", 52),
        SurahInfo(15, "الحجر", "Al-Hijr", 99),
        SurahInfo(16, "النحل", "An-Nahl", 128),
        SurahInfo(17, "الإسراء", "Al-Isra", 111),
        SurahInfo(18, "الكهف", "Al-Kahf", 110),
        SurahInfo(19, "مريم", "Maryam", 98),
        SurahInfo(20, "طه", "Ta-Ha", 135),
        SurahInfo(21, "الأنبياء", "Al-Anbiya", 112),
        SurahInfo(22, "الحج", "Al-Hajj", 78),
        SurahInfo(23, "المؤمنون", "Al-Mu'minun", 118),
        SurahInfo(24, "النور", "An-Nur", 64),
        SurahInfo(25, "الفرقان", "Al-Furqan", 77),
        SurahInfo(26, "الشعراء", "Ash-Shu'ara", 227),
        SurahInfo(27, "النمل", "An-Naml", 93),
        SurahInfo(28, "القصص", "Al-Qasas", 88),
        SurahInfo(29, "العنكبوت", "Al-'Ankabut", 69),
        SurahInfo(30, "الروم", "Ar-Rum", 60),
        SurahInfo(31, "لقمان", "Luqman", 34),
        SurahInfo(32, "السجدة", "As-Sajdah", 30),
        SurahInfo(33, "الأحزاب", "Al-Ahzab", 73),
        SurahInfo(34, "سبأ", "Saba", 54),
        SurahInfo(35, "فاطر", "Fatir", 45),
        SurahInfo(36, "يس", "Ya-Sin", 83),
        SurahInfo(37, "الصافات", "As-Saffat", 182),
        SurahInfo(38, "ص", "Sad", 88),
        SurahInfo(39, "الزمر", "Az-Zumar", 75),
        SurahInfo(40, "غافر", "Ghafir", 85),
        SurahInfo(41, "فصلت", "Fussilat", 54),
        SurahInfo(42, "الشورى", "Ash-Shura", 53),
        SurahInfo(43, "الزخرف", "Az-Zukhruf", 89),
        SurahInfo(44, "الدخان", "Ad-Dukhan", 59),
        SurahInfo(45, "الجاثية", "Al-Jathiyah", 37),
        SurahInfo(46, "الأحقاف", "Al-Ahqaf", 35),
        SurahInfo(47, "محمد", "Muhammad", 38),
        SurahInfo(48, "الفتح", "Al-Fath", 29),
        SurahInfo(49, "الحجرات", "Al-Hujurat", 18),
        SurahInfo(50, "ق", "Qaf", 45),
        SurahInfo(51, "الذاريات", "Adh-Dhariyat", 60),
        SurahInfo(52, "الطور", "At-Tur", 49),
        SurahInfo(53, "النجم", "An-Najm", 62),
        SurahInfo(54, "القمر", "Al-Qamar", 55),
        SurahInfo(55, "الرحمن", "Ar-Rahman", 78),
        SurahInfo(56, "الواقعة", "Al-Waqi'ah", 96),
        SurahInfo(57, "الحديد", "Al-Hadid", 29),
        SurahInfo(58, "المجادلة", "Al-Mujadila", 22),
        SurahInfo(59, "الحشر", "Al-Hashr", 24),
        SurahInfo(60, "الممتحنة", "Al-Mumtahanah", 13),
        SurahInfo(61, "الصف", "As-Saff", 14),
        SurahInfo(62, "الجمعة", "Al-Jumu'ah", 11),
        SurahInfo(63, "المنافقون", "Al-Munafiqun", 11),
        SurahInfo(64, "التغابن", "At-Taghabun", 18),
        SurahInfo(65, "الطلاق", "At-Talaq", 12),
        SurahInfo(66, "التحريم", "At-Tahrim", 12),
        SurahInfo(67, "الملك", "Al-Mulk", 30),
        SurahInfo(68, "القلم", "Al-Qalam", 52),
        SurahInfo(69, "الحاقة", "Al-Haqqah", 52),
        SurahInfo(70, "المعارج", "Al-Ma'arij", 44),
        SurahInfo(71, "نوح", "Nuh", 28),
        SurahInfo(72, "الجن", "Al-Jinn", 28),
        SurahInfo(73, "المزمل", "Al-Muzzammil", 20),
        SurahInfo(74, "المدثر", "Al-Muddaththir", 56),
        SurahInfo(75, "القيامة", "Al-Qiyamah", 40),
        SurahInfo(76, "الإنسان", "Al-Insan", 31),
        SurahInfo(77, "المرسلات", "Al-Mursalat", 50),
        SurahInfo(78, "النبأ", "An-Naba", 40),
        SurahInfo(79, "النازعات", "An-Nazi'at", 46),
        SurahInfo(80, "عبس", "'Abasa", 42),
        SurahInfo(81, "التكوير", "At-Takwir", 29),
        SurahInfo(82, "الإنفطار", "Al-Infitar", 19),
        SurahInfo(83, "المطففين", "Al-Mutaffifin", 36),
        SurahInfo(84, "الإنشقاق", "Al-Inshiqaq", 25),
        SurahInfo(85, "البروج", "Al-Buruj", 22),
        SurahInfo(86, "الطارق", "At-Tariq", 17),
        SurahInfo(87, "الأعلى", "Al-A'la", 19),
        SurahInfo(88, "الغاشية", "Al-Ghashiyah", 26),
        SurahInfo(89, "الفجر", "Al-Fajr", 30),
        SurahInfo(90, "البلد", "Al-Balad", 20),
        SurahInfo(91, "الشمس", "Ash-Shams", 15),
        SurahInfo(92, "الليل", "Al-Layl", 21),
        SurahInfo(93, "الضحى", "Ad-Duhaa", 11),
        SurahInfo(94, "الشرح", "Ash-Sharh", 8),
        SurahInfo(95, "التين", "At-Tin", 8),
        SurahInfo(96, "العلق", "Al-'Alaq", 19),
        SurahInfo(97, "القدر", "Al-Qadr", 5),
        SurahInfo(98, "البينة", "Al-Bayyinah", 8),
        SurahInfo(99, "الزلزلة", "Az-Zalzalah", 8),
        SurahInfo(100, "العاديات", "Al-'Adiyat", 11),
        SurahInfo(101, "القارعة", "Al-Qari'ah", 11),
        SurahInfo(102, "التكاثر", "At-Takathur", 8),
        SurahInfo(103, "العصر", "Al-'Asr", 3),
        SurahInfo(104, "الهمزة", "Al-Humazah", 9),
        SurahInfo(105, "الفيل", "Al-Fil", 5),
        SurahInfo(106, "قريش", "Quraysh", 4),
        SurahInfo(107, "الماعون", "Al-Ma'un", 7),
        SurahInfo(108, "الكوثر", "Al-Kawthar", 3),
        SurahInfo(109, "الكافرون", "Al-Kafirun", 6),
        SurahInfo(110, "النصر", "An-Nasr", 3),
        SurahInfo(111, "المسد", "Al-Masad", 5),
        SurahInfo(112, "الإخلاص", "Al-Ikhlas", 4),
        SurahInfo(113, "الفلق", "Al-Falaq", 5),
        SurahInfo(114, "الناس", "An-Nas", 6),
    )

    fun get(number: Int): SurahInfo =
        all.firstOrNull { it.number == number } ?: all.first()
}
