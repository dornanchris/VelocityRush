# Starshower Run for Android

A Kotlin and Jetpack Compose port of the iOS game. Gameplay, progression, economy, cosmetics and the synthesised audio match the iOS app, and the Daily Run is the same on both platforms (same seed, twist and missions each day).

## Project layout

```
android/
├── core/        Pure Kotlin (JVM) module, unit tested; mirrors StarshowerRun/Core
│   ├── engine/        GameEngine, modes, SplitMix64 RNG, entities
│   ├── progression/   Profile, achievements, missions, cosmetics, leveling, persistence
│   └── audio/         Sound effect and music synthesiser (raw samples)
└── app/         Android app (Compose)
    ├── game/          Canvas renderer, sprites, particles, avatar and trail, HUD, pause, results
    ├── services/      Preferences, ProgressStore, sound, haptics, toasts, daily reminder, global boards
    └── ui/            Design system, icon mapping and every menu screen
```

Like iOS, the engine knows nothing about rendering. `GameScene` steps `GameEngine` once per display frame, then draws its state on a Compose `Canvas`. The engine uses y-up coordinates, as SpriteKit does, and `WorldFrame` maps them to screen pixels.

## Build and run

Requirements:
- Android Studio (Ladybug or newer), or JDK 17+ for the command line.
- Android SDK 35.
- The app supports Android 8.0 (API 26) and newer.

```bash
cd android
./gradlew :core:test           # engine/progression/audio unit tests (plain JVM)
./gradlew :app:assembleDebug   # debug APK in app/build/outputs/apk/debug/
./gradlew :app:installDebug    # install on a connected device or emulator
```

In Android Studio, open the `android/` folder (not the repo root) and run the `app` configuration.

The `Android` GitHub Actions workflow runs the unit tests, builds the debug APK and runs lint on every push that touches `android/`. The APK is attached to each run as an artifact.

## Platform differences

| iOS | Android |
| --- | --- |
| SpriteKit scene | Compose `Canvas`, with sprites pre-rendered once into bitmaps (`Sprites`) and a small SKEmitter-style particle system (`Particles`) |
| SF Symbols | Material icons. The shared catalog keeps SF Symbol names, and `ui/SymbolIcons.kt` maps them to Material icons in one place |
| AVAudioEngine | `SoundPool` for effects (WAVs are synthesised into the cache on first launch) and a looping static `AudioTrack` for music |
| UIFeedbackGenerator | `VibrationEffect` presets (API 29+), with one-shot fallbacks on older devices |
| UNUserNotificationCenter | An inexact repeating alarm at 6pm, a notification channel, the Android 13 notification permission, and re-arming after a reboot |
| UserDefaults + JSON | SharedPreferences + kotlinx.serialization. Saves tolerate missing and unknown fields |
| Game Center | `GlobalGames` interface. It ships with an offline implementation (see below) |
| Swipe back | The system Back button and gesture. In a run, Back pauses; pressing it again resumes |

The prototype-stats migration from iOS is not ported, because no Android prototype ever shipped.

## Global leaderboards (Google Play Games)

Personal leaderboards work offline. Global boards go through `services/GlobalGames.kt`, which today uses `OfflineGlobalGames`. To enable Play Games Services v2:

1. In the Play Console, add Play Games Services to the app.
2. Create three leaderboards, one each for Endless, Time Attack and Daily (Daily resets daily).
3. Create achievements for the ids listed in the root README.
4. Add `com.google.android.gms:play-services-games-v2` and the `com.google.android.gms.games.APP_ID` manifest meta-data.
5. Implement `GlobalGames` with `PlayGames.getLeaderboardsClient` and `getAchievementsClient`. Map `GameMode.leaderboardID` and `AchievementDefinition.globalID` to the Play Console ids.
6. Return that implementation from `AppGraph.globalGames`.

## Tests

`core/src/test` is a straight port of the iOS Swift Testing suite, plus a few Android-specific checks: the canonical SplitMix64 output for cross-platform Daily Run parity, catalog sizes, tolerance of corrupt or future saves, and deep-copy isolation. It covers the same ground: the engine, daily seeds and missions, leveling, run recording, leaderboards, streaks, gated purchases, the economy guard, catalog integrity, persistence and the audio synth.
