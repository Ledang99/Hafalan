# Quran Hafalan Widget

Android MVP: home-screen widget + small app for daily Quran hafalan (memorization).

Pick a surah, see the current ayah in Arabic (Uthmani or Simple via Quran.com), listen with Sheikh **Mishari Rashid Al-Afasy**, mark **Remembered** when it sits with you, track Progress, and keep a calm home-screen widget in sync.

Tone is intentionally quiet — one small piece at a time (feel inspired by companions like Tasmi; this app stays Android widget-first and does not clone streak/journey flows).

## Download APK (latest)

**v1.08** debug APK:

- [Download hafalan-v1.08.apk](https://github.com/Ledang99/Hafalan/raw/main/releases/hafalan-v1.08.apk)

On GitHub: open [`releases/hafalan-v1.08.apk`](https://github.com/Ledang99/Hafalan/blob/main/releases/hafalan-v1.08.apk) → **Download raw file**.

Install: allow install from this source if Android asks, then add the **Hafalan ayah** widget from the home-screen widget picker.

## Features (MVP)

- Choose any of the 114 surahs
- Show current ayah with **Uthmani / Simple** script toggle (persisted; updates app + widget)
- **Remembered** advances the active target, records the ayah in history, and updates the widget
- **Progress** screen: per-surah % and overall % vs 6236 ayahs
- **Backup / Restore** on Progress: export or replace memorization progress via a JSON file (SAF)
- Daily advance when an ayah is left unmarked (see rule below)
- **Reciter speed:** 1× / 1.5× / 2× chips near Listen (persisted; used by app + widget)
- **Repeat ayah:** loop the current ayah while listening (app + widget service)
- Tap widget to play Al-Afasy via a **foreground media notification** (stays on home screen; no black Activity)
- Tap ayah / Listen in the app for the same audio (simple player screen)
- Local persistence via DataStore (no auth, no backend)
- Offline fallback text for Al-Fatihah if the network fails

## Daily advance vs remembered advance

- **Remembered:** records the current ayah in progress history and advances to the next ayah immediately (you can advance multiple times in one day).
- **Daily:** for each local calendar day since the last activity, if the current ayah was **not** marked remembered, the target advances by one ayah. Daily advance does **not** add to remembered history. After a remembered advance, that same calendar day does not also daily-advance; later missed days still rotate the daily target.
- Progress pointer stops on the last ayah of the selected surah (last ayah can still be marked Remembered once).

## Reciter

Playback always uses **Sheikh Mishari Rashid Al-Afasy**:

- Quran.com API recitation id `7` (`Mishari Rashid al-\`Afasy`), audio from `https://verses.quran.com/…`
- Fallback edition `ar.alafasy` on alquran.cloud / islamic.network CDN (same reciter)

## Build & run

### Requirements

- Android Studio Ladybug+ (or SDK 35 + JDK 17)
- Android device or emulator (API 26+)

### From Android Studio

1. Open this repository root in Android Studio
2. Let Gradle sync
3. Run the `app` configuration on a device/emulator
4. Long-press the home screen → **Widgets** → add **Hafalan ayah**

### From command line

```bash
# Ensure Android SDK is installed, then:
echo "sdk.dir=$ANDROID_HOME" > local.properties   # if needed
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Widget preview requires a physical device or emulator home screen; CI/headless environments can only assemble the APK.

## Key files

| Path | Role |
|------|------|
| `app/src/main/java/.../MainActivity.kt` | Compose app entry |
| `app/src/main/java/.../ui/MainScreen.kt` | Surah picker, ayah, script/speed/repeat, Progress, Remembered, Play |
| `app/src/main/java/.../widget/HafalanWidgetProvider.kt` | Home-screen App Widget (tap → playback service) |
| `app/src/main/java/.../audio/AyahPlaybackService.kt` | Foreground MediaPlayer for widget taps |
| `app/src/main/java/.../data/HafalanRepository.kt` | Progress + remembered set + prefs |
| `app/src/main/java/.../data/QuranApi.kt` | Quran.com text editions + Al-Afasy audio |
| `app/src/main/java/.../audio/AudioPlayerActivity.kt` | In-app Listen MediaPlayer UI |
| `app/src/main/res/layout/widget_hafalan.xml` | Widget RemoteViews layout |

## Stack

Kotlin, Jetpack Compose (app UI), classic App Widget `RemoteViews`, DataStore Preferences, OkHttp, MediaPlayer + foreground service.
