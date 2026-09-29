# Velocity Rush ⚡️

A neon arcade dodger for iPhone and iPad, built with SwiftUI and SpriteKit. Drag your dot, weave through falling hazards, skim them for near-miss points, and chase the leaderboards.

Everything is generated in code: sprites, particles, sound effects and music. The only image in the project is the app icon.

## Game modes

| Mode | Rules |
| --- | --- |
| **Endless** | One life. Levels get longer as you go (20s, 25s, 30s … 60s). There's a quick warm-up, a long demanding middle, and then it goes wild after about 5 minutes. |
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

- **Coins and XP:** earned from every run and from missions. XP levels up your player.
- **46 achievements:** spread over five categories and four tiers. They pay modest coins (25 to 400).
- **Daily missions:** three a day, paying coins and XP. Clearing all three pays an all-clear bonus automatically and extends your **all-clear streak**.
- **Login streak:** a 7-day reward calendar.
- **Armory (shop):** 15 skins, 13 blade trails and 8 themes, sorted by rarity: Common, Rare, Epic, Legendary and Mythic.
  - **Skins** have animated effects: pulses, orbiting moons, spinning halos, auras, sparkles, glitches and colour cycling.
  - **Trails** are Fruit Ninja-style tapered blades with colour gradients, stripes, rainbows, electric flicker and afterimages, plus particles such as sparks, embers, snow, stars and zaps.
  - **Unlocking:** commons cost coins. Rarer items also need a level, a personal best (for example surviving 3:00 in Endless, 40,000 in Time Attack, or 90s on Expert) or a daily streak (7 or 30 days of all-clears). Legendary and mythic items can't be bought at all.
  - **Preview:** a live preview shows any item in motion before you unlock it.
  - **Pacing:** the whole Armory costs about 120,000 coins, so collecting everything takes months, not a few games.
- **Leaderboards:** personal top-10 boards for each mode (they work offline) and global Game Center boards.
- **Profile:** personal bests, lifetime stats and achievement progress.
- **Migration:** stats from the original prototype carry over, with a welcome-back bonus. Save files load safely across versions: new fields are filled with defaults. The v3 economy update re-locks cosmetics unlocked under the old pricing.

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
   `survive_30 survive_60 survive_90 survive_120 survive_180 total_10min total_1hour purist_60 expert_60 streak_3 level_6 level_9 endless_1k endless_5k endless_15k ta_5k ta_15k ta_35k daily_3k nearmiss_run_15 nearmiss_100 nearmiss_1000 perfect_50 multiplier_3 multiplier_6 nova_12 dodge_1000 dodge_10000 stars_run_40 stars_250 stars_2500 powerups_50 shop_1 shop_10 games_10 games_100 games_500 explorer daily_1 daily_30 login_7 allclear_1 allclear_streak_7 allclear_30 missions_10 missions_100`

## Tests

`VelocityRushTests` uses Swift Testing to cover the core: the engine (countdown, determinism, mode end conditions), daily seeds and missions, the leveling curve, the level table and difficulty curve, run recording, leaderboards, login and all-clear streaks, gated purchases, automatic unlocks, an economy guard (one great run can't unlock the shop), catalog integrity, persistence, legacy migration and the audio synth. Run them with **⌘U**.
