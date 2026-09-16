# Quran Hafalan Widget

Android MVP: home-screen widget + small app for daily Quran hafalan (memorization).

Pick a surah, see the current ayah in Arabic (Uthmani/Hafs via Quran.com), listen with Sheikh **Mishari Rashid Al-Afasy**, mark **Remembered** when it sits with you, and keep a calm home-screen widget in sync.

Tone is intentionally quiet — one small piece at a time (feel inspired by companions like Tasmi; this app stays Android widget-first and does not clone streak/journey flows).

## Features (MVP)

- Choose any of the 114 surahs
- Show current ayah (Arabic Uthmani text)
- **Remembered** advances to the next ayah and updates the widget
- Daily advance when an ayah is left unmarked (see rule below)
- Tap widget or ayah to play Al-Afasy audio for that ayah
- Local persistence via DataStore (no auth, no backend)
- Offline fallback text for Al-Fatihah if the network fails

## Daily advance vs remembered advance

- **Remembered:** advances to the next ayah immediately (you can advance multiple times in one day).
- **Daily:** for each local calendar day since the last activity, if the current ayah was **not** marked remembered, the target advances by one ayah. After a remembered advance, that same calendar day does not also daily-advance; later missed days still rotate the daily target.
- Progress stops on the last ayah of the selected surah.

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
| `app/src/main/java/.../ui/MainScreen.kt` | Surah picker, ayah, Remembered, Play |
| `app/src/main/java/.../widget/HafalanWidgetProvider.kt` | Home-screen App Widget |
| `app/src/main/java/.../data/HafalanRepository.kt` | Progress + daily advance |
| `app/src/main/java/.../data/QuranApi.kt` | Quran.com text + Al-Afasy audio |
| `app/src/main/java/.../audio/AudioPlayerActivity.kt` | MediaPlayer playback |
| `app/src/main/res/layout/widget_hafalan.xml` | Widget RemoteViews layout |

## Stack

Kotlin, Jetpack Compose (app UI), classic App Widget `RemoteViews`, DataStore Preferences, OkHttp, MediaPlayer.
