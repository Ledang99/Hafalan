# Quran Hafalan Widget — product plan (MVP)

## Goal

Help someone memorize one ayah at a time from a chosen surah, with a home-screen widget that always shows the current daily target and one-tap Al-Afasy audio.

## User flow

1. Open app → pick surah (defaults to Al-Fatihah)
2. See ayah 1 in Arabic (Uthmani by default; switch to Simple in **Arabic script**)
3. Optional: choose **1× / 1.5× / 2×** speed and **Repeat ayah**, then tap ayah / Listen / widget to hear Sheikh Mishari Rashid Al-Afasy (widget plays via foreground service — stays on the home screen)
4. Tap **Remembered** → ayah recorded in progress history and target advances (widget updates)
5. Open **Progress** (chart icon) for per-surah and overall remembered %
6. If they skip a day without Remembered, the target advances one ayah per missed local day (does not add to remembered history)

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

- Persistence: DataStore Preferences (progress pointer, remembered ayah set, speed, script, repeat)
- Text: Quran.com Uthmani / uthmani_simple; fallback Al-Fatihah bundle
- Audio: Quran.com recitation id 7 (Al-Afasy) + verses.quran.com CDN; MediaPlayer `PlaybackParams` for 1×/1.5×/2×; `isLooping` when repeat is on
- Widget: `AppWidgetProvider` + RemoteViews; tap starts `AyahPlaybackService` (mediaPlayback FGS), not `MainActivity`
