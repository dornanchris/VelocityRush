# Velocity Rush ⚡️

A neon arcade dodger for iPhone and iPad, built with SwiftUI and SpriteKit. Drag your dot, weave through falling hazards, skim them for near-miss points, and chase the leaderboards.

Everything is generated in code: sprites, particles, sound effects and music. The only image in the project is the app icon.

## Game modes

| Mode | Rules |
| --- | --- |
| **Endless** | One life. The rush speeds up every 15 seconds (levels). How long can you last? |
| **Time Attack** | A 60-second clock. Stars are worth big points, time crystals add +3s, and each hit costs 5s. |
| **Daily Run** | A seeded run that's the same for everyone each day, with a daily twist (Giant Dots, Speed Demon, Blackout, Star Storm, Wobble World, Tiny Hero, Meteor Shower, Purist). Always played on Normal. |
| **Zen** | Hits never end the run. You collect stars at your own pace and bank them when you finish. |

## Gameplay

- **Hazards:** drops, wobblers that sway, meteors that are fast and flagged by a warning lane first, giants, splitters that break into fragments, and wall formations with a gap to thread.
- **Near misses:** skimming past a hazard scores points, and a *PERFECT* is a very close pass.
- **Combo multiplier:** near misses and stars build a multiplier up to ×6. It decays if you stop.
- **Power-ups:** Shield (absorbs one hit), Slow-Mo, Magnet (pulls stars), Shrink, and Nova (clears the screen).
- **Difficulty:** Easy, Normal, Hard or Expert, with score multipliers of ×0.75 to ×1.6.
- **Feedback:** particles, screen shake, floating score text, haptics, synthesised sound effects and a looping synthwave track.

## Progression and meta

- **Coins and XP:** earned from every run. XP levels up your player, and each level-up pays out coins.
- **42 achievements:** spread over five categories and four tiers. Each pays coins, and some unlock exclusive cosmetics.
- **Daily missions:** three a day (easy, medium, hard), plus an all-clear bonus.
- **Login streak:** a 7-day reward calendar.
- **Shop:** 10 skins, 7 trails and 7 themes. Some are bought with coins, others are earned through levels or achievements.
- **Leaderboards:** personal top-10 boards for each mode (they work offline) and global Game Center boards.
- **Profile:** personal bests, lifetime stats and achievement progress.
- **Migration:** stats from the original prototype carry over, with a welcome-back bonus.

## Settings

Everything in Settings affects the game:

- Sound effects, music and haptics
- Difficulty and drag sensitivity
- An FPS overlay
- Colour-blind friendly hazards, high contrast and reduced motion (no shake, fewer particles)
- A daily reminder notification
- Reset all progress

## Project layout

```
VelocityRush/
├── Core/                    Pure Swift (Foundation only) – unit tested
│   ├── Engine/              GameEngine simulation, modes, seeded RNG, entities
│   ├── Progression/         Profile, achievements, missions, cosmetics, leveling, persistence
│   └── Audio/               Sound effect and music synthesiser (raw samples)
├── Game/                    SpriteKit renderer, texture factory, in-game SwiftUI (HUD, pause, results)
├── Services/                ProgressStore, Game Center, sound, haptics, notifications, toasts
├── UI/                      Design system and screens (Missions, Leaderboards, Shop)
├── ContentView.swift        Home screen
├── ProfileView.swift
└── SettingsView.swift
```

The **engine is completely separate from rendering.** `GameEngine` takes a frame delta and a target position, and exposes hazards, pickups and events. `GameScene` only draws that state and turns events into effects. This split is what makes Daily Runs deterministic (the same seed gives the same hazards on every device) and lets the gameplay rules be unit tested.

## Setup

1. Open `VelocityRush.xcodeproj` in Xcode 16.3 or later. The deployment target is iOS 18.4.
2. Build and run on an iPhone or the iOS Simulator. No extra setup is needed; the game runs fully offline.

### Enabling Game Center (optional)

Without Game Center the game uses local leaderboards only. To turn on global leaderboards and achievements:

1. In **Signing & Capabilities**, choose **+ Capability**, then **Game Center**.
2. In App Store Connect, under **Game Center**, create these leaderboards:
   - `vr.leaderboard.endless`: classic, high score is best
   - `vr.leaderboard.timeattack`: classic, high score is best
   - `vr.leaderboard.daily`: **recurring**, resets daily, high score is best
3. Also create achievements named `vr.achievement.<id>`, where `<id>` is each id in `Core/Progression/Achievements.swift`:
   `survive_30 survive_60 survive_90 survive_120 survive_180 total_10min total_1hour purist_60 expert_60 streak_3 level_8 endless_1k endless_5k endless_15k ta_5k ta_15k ta_35k daily_3k nearmiss_run_15 nearmiss_100 nearmiss_1000 perfect_50 multiplier_3 multiplier_6 nova_12 dodge_1000 dodge_10000 stars_run_40 stars_250 stars_2500 powerups_50 shop_1 shop_10 games_10 games_100 games_500 explorer daily_1 daily_30 login_7 missions_10 missions_100`

## Tests

`VelocityRushTests` uses Swift Testing to cover the core: the engine (countdown, determinism, mode end conditions), daily seeds and missions, the leveling curve, run recording, leaderboards, login streaks, purchases, mission claims, catalog integrity, persistence, legacy migration and the audio synth. Run them with **⌘U**.
