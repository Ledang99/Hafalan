# Quran-Widget — project context

## Product
Android home-screen widget for daily Quran hafalan, driven by a small companion app.

## Decisions
- Platform: Android App Widget + main app
- Content/audio source: Quran.com / public Quran API
- Reciter: Sheikh Mishari Rashid Al-Afasy
- Text: Arabic script toggle — **Uthmani** (default, Quran.com `uthmani`) vs **Simple** (`uthmani_simple`); persisted; refreshes app + widget text
- Progress: user picks surah; “Remembered” advances ayah **and** adds that ayah to a persisted remembered set; Progress screen shows per-surah % and overall % vs 6236; daily target still advances when not marked
- Playback speed: 1× / 1.5× / 2× chips in the app (near Listen); persisted in DataStore and applied to MediaPlayer for in-app Listen and widget playback
- Repeat ayah: toggle loops current ayah during Listen + widget/service playback; persisted
- Widget tap: starts `AyahPlaybackService` (foreground media notification) — stays on home screen; does **not** open `MainActivity` or a black/fullscreen Activity
- UX reference: Tasmi ([App Store](https://apps.apple.com/us/app/tasmi/id6770039955), [tasmi.cloud](https://tasmi.cloud/)) — calm ayah-by-ayah listen+read; not a clone
- Out of MVP scope: spaced repetition decks, multi-stage challenges, streaks/journey, accounts/sync
