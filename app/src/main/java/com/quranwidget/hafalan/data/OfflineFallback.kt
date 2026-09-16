package com.quranwidget.hafalan.data

/**
 * Offline Arabic text for Al-Fatihah when the network/API is unavailable.
 * Audio URL still points at Quran.com Al-Afasy CDN; playback needs network.
 */
object OfflineFallback {
    val fatihah: Map<Int, String> = mapOf(
        1 to "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ",
        2 to "ٱلْحَمْدُ لِلَّهِ رَبِّ ٱلْعَٰلَمِينَ",
        3 to "ٱلرَّحْمَٰنِ ٱلرَّحِيمِ",
        4 to "مَٰلِكِ يَوْمِ ٱلدِّينِ",
        5 to "إِيَّاكَ نَعْبُدُ وَإِيَّاكَ نَسْتَعِينُ",
        6 to "ٱهْدِنَا ٱلصِّرَٰطَ ٱلْمُسْتَقِيمَ",
        7 to "صِرَٰطَ ٱلَّذِينَ أَنْعَمْتَ عَلَيْهِمْ غَيْرِ ٱلْمَغْضُوبِ عَلَيْهِمْ وَلَا ٱلضَّآلِّينَ",
    )

    fun ayah(surahNumber: Int, ayahNumber: Int): AyahContent? {
        if (surahNumber != 1) return null
        val text = fatihah[ayahNumber] ?: return null
        val s = surahNumber.toString().padStart(3, '0')
        val a = ayahNumber.toString().padStart(3, '0')
        return AyahContent(
            surahNumber = 1,
            ayahNumber = ayahNumber,
            textArabic = text,
            audioUrl = "${QuranApi.VERSES_CDN_BASE}/Alafasy/mp3/$s$a.mp3",
        )
    }
}
