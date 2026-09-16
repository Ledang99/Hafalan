# Quran Hafalan Widget — product plan (MVP)

## Goal

Help someone memorize one ayah at a time from a chosen surah, with a home-screen widget that always shows the current daily target and one-tap Al-Afasy audio.

## User flow

1. Open app → pick surah (defaults to Al-Fatihah)
2. See ayah 1 in Arabic
3. Optional: tap ayah / Play / widget to hear Sheikh Mishari Rashid Al-Afasy for that ayah only
4. Tap **Remembered** → ayah 2 (widget updates)
5. If they skip a day without Remembered, the target advances one ayah per missed local day

## Tone (inspired by Tasmi, not a clone)

Calm, quiet companion energy: one ayah at a time, listen + read together (Al-Afasy), clear **Remembered** advance, no streak-shaming. Widget-first on Android.

## Out of scope (MVP)

- Auth, cloud sync, multiple users
- Spaced-repetition decks, Journey/streak pages, multi-stage challenges
- Translation UI (Arabic-only for now)
- Reciter picker (fixed to Al-Afasy)
- Word-level highlight while playing (needs timing data; deferred)
- Full mushaf browser, bookmarks
- iOS / Wear OS

## Technical notes

- Persistence: DataStore Preferences
- Text: Quran.com Uthmani; fallback Al-Fatihah bundle
- Audio: Quran.com recitation id 7 (Al-Afasy) + verses.quran.com CDN
- Widget: `AppWidgetProvider` + RemoteViews; tap opens `AudioPlayerActivity`
