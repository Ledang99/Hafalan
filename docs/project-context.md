# Quran-Widget — project context

## Product
Android home-screen widget for daily Quran hafalan, driven by a small companion app.

## Version
- `versionName` **1.06** · `versionCode` **106**
- Progress screen footer shows `Version 1.06`

## Decisions
- Platform: Android App Widget + main app
- Content/audio source: Quran.com / public Quran API
- Reciter: Sheikh Mishari Rashid Al-Afasy
- Text / script chips (2 only):
  - **Uthmani** (default) — Quran.com `qpc_hafs` (`text_qpc_hafs`) rendered with bundled **KFGQPC Uthmanic Hafs** (King Fahad Complex) font (`res/font/uthmanic_hafs.ttf`)
  - **Tajweed** — Quran.com `uthmani_tajweed` with colored HTML tags (best-effort in app Compose + widget Spannable); same KFGQPC font
  - **Simple / IndoPak removed**; legacy pref ids `simple` / `uthmani_simple` map to Uthmani; choice persisted and refreshes app + widget
- Appearance: **Day / Dark** chips (persisted); app Material theme + widget default background follow the choice until the user customizes widget color
- Widget background: color swatches + opacity 0–100% on Home (near script/theme); ARGB applied via RemoteViews; text + tajweed palette follow background luminance (readable on light or dark)
- Widget resize: `resizeMode=horizontal|vertical` with min/max resize; `onAppWidgetOptionsChanged` re-pushes RemoteViews; Arabic auto-size (12–56sp XML + size hint from tile height) fills the new size; meta stays secondary
- Widget typography: Arabic uses KFGQPC font, auto-sizes to fill the tile; surah/ayah meta collapsed to one small header line; tight padding; tap still plays via `AyahPlaybackService` (no black screen)
- Widget script sync: chip changes refetch ayah text for the selected edition and **push RemoteViews immediately** (`pushUpdate`); widget reads `scriptEdition` and renders Uthmani (plain QPC Hafs) vs Tajweed (colored spans)
- Progress: user picks surah; “Remembered” advances ayah **and** adds that ayah to a persisted remembered set; Progress screen shows per-surah % and overall % vs 6236; daily target still advances when not marked
- Progress backup/restore: Progress screen **Backup** / **Restore** near the footer; exports JSON (`rememberedAyahs` + current surah/ayah pointer + optional script) via SAF CreateDocument; Restore confirms then replaces local progress via OpenDocument; percentages and current target match after restore
- Playback speed: 1× / 1.5× / 2× chips in the app (near Listen); persisted in DataStore and applied to MediaPlayer for in-app Listen and widget playback
- Repeat ayah: toggle loops current ayah during Listen + widget/service playback; persisted
- Widget tap: starts `AyahPlaybackService` (foreground media notification) — stays on home screen; does **not** open `MainActivity` or a black/fullscreen Activity
- UX reference: Tasmi ([App Store](https://apps.apple.com/us/app/tasmi/id6770039955), [tasmi.cloud](https://tasmi.cloud/)) — calm ayah-by-ayah listen+read; not a clone
- Out of MVP scope: spaced repetition decks, multi-stage challenges, streaks/journey, accounts/sync
