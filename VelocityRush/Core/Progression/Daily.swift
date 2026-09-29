//
//  Daily.swift
//  VelocityRush
//
//  Daily Run seeds, daily missions and the login streak calendar.
//

import Foundation

// MARK: - Daily Run

enum DailyChallenge {
    /// "yyyy-MM-dd" in the player's calendar.
    static func dayKey(for date: Date, calendar: Calendar = .current) -> String {
        let parts = calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", parts.year ?? 0, parts.month ?? 0, parts.day ?? 0)
    }

    static func previousDayKey(before date: Date, calendar: Calendar = .current) -> String {
        let yesterday = calendar.date(byAdding: .day, value: -1, to: date) ?? date.addingTimeInterval(-86_400)
        return dayKey(for: yesterday, calendar: calendar)
    }

    static func seed(for dayKey: String) -> UInt64 {
        StableHash.fnv1a("velocity-rush-daily-" + dayKey)
    }

    static func modifier(for dayKey: String) -> RunModifier {
        let pool = RunModifier.dailyPool
        let index = Int(StableHash.fnv1a("modifier-" + dayKey) % UInt64(pool.count))
        return pool[index]
    }

    /// Daily Runs always use Normal difficulty so scores are comparable.
    static func config(for date: Date, calendar: Calendar = .current) -> RunConfig {
        let key = dayKey(for: date, calendar: calendar)
        return RunConfig(mode: .daily, difficulty: .normal, modifier: modifier(for: key),
                         seed: seed(for: key), dailyKey: key)
    }

    static func timeUntilReset(from date: Date, calendar: Calendar = .current) -> TimeInterval {
        let startOfToday = calendar.startOfDay(for: date)
        let tomorrow = calendar.date(byAdding: .day, value: 1, to: startOfToday) ?? date.addingTimeInterval(86_400)
        return max(0, tomorrow.timeIntervalSince(date))
    }
}

// MARK: - Login rewards

enum LoginRewards {
    static let schedule = [25, 50, 75, 100, 150, 200, 400]

    static func reward(forStreakDay day: Int) -> Int {
        schedule[(max(day, 1) - 1) % schedule.count]
    }
}

// MARK: - Missions

enum MissionKind: String, Codable, CaseIterable {
    case collectStars
    case nearMisses
    case dodge
    case playRuns
    case powerUps
    case surviveEndless
    case scoreEndless
    case scoreTimeAttack
    case reachMultiplier
    case playDaily
    case starsInRun

    /// Goals for easy / medium / hard slots.
    var goals: [Int] {
        switch self {
        case .collectStars: return [30, 75, 150]
        case .nearMisses: return [10, 25, 50]
        case .dodge: return [150, 400, 900]
        case .playRuns: return [3, 6, 10]
        case .powerUps: return [4, 10, 20]
        case .surviveEndless: return [30, 60, 90]
        case .scoreEndless: return [1_000, 2_500, 5_000]
        case .scoreTimeAttack: return [4_000, 9_000, 16_000]
        case .reachMultiplier: return [3, 4, 6]
        case .playDaily: return [1, 1, 1]
        case .starsInRun: return [15, 30, 45]
        }
    }

    /// Kinds that only make sense as the easy slot.
    var easyOnly: Bool { self == .playDaily }

    /// True when progress is the best single run rather than a running total.
    var isSingleRun: Bool {
        switch self {
        case .surviveEndless, .scoreEndless, .scoreTimeAttack, .reachMultiplier, .starsInRun: return true
        default: return false
        }
    }

    var icon: String {
        switch self {
        case .collectStars, .starsInRun: return "star.fill"
        case .nearMisses: return "scope"
        case .dodge: return "arrow.left.and.right"
        case .playRuns: return "play.fill"
        case .powerUps: return "bolt.fill"
        case .surviveEndless: return "heart.fill"
        case .scoreEndless: return "infinity"
        case .scoreTimeAttack: return "stopwatch.fill"
        case .reachMultiplier: return "multiply.circle.fill"
        case .playDaily: return "calendar"
        }
    }

    func title(goal: Int) -> String {
        switch self {
        case .collectStars: return "Collect \(goal) stars"
        case .nearMisses: return "Get \(goal) near misses"
        case .dodge: return "Dodge \(goal) hazards"
        case .playRuns: return "Play \(goal) runs"
        case .powerUps: return "Grab \(goal) power-ups"
        case .surviveEndless: return "Survive \(goal)s in Endless"
        case .scoreEndless: return "Score \(goal.formatted()) in Endless"
        case .scoreTimeAttack: return "Score \(goal.formatted()) in Time Attack"
        case .reachMultiplier: return "Reach a x\(goal) multiplier"
        case .playDaily: return "Play today's Daily Run"
        case .starsInRun: return "Collect \(goal) stars in one run"
        }
    }

    /// Value contributed by a single run.
    func value(from run: RunResult) -> Int {
        switch self {
        case .collectStars, .starsInRun: return run.stars
        case .nearMisses: return run.nearMisses
        case .dodge: return run.dodged
        case .playRuns: return 1
        case .powerUps: return run.powerUps
        case .surviveEndless: return (run.mode == .endless || run.mode == .daily) ? run.survivedSeconds : 0
        case .scoreEndless: return run.mode == .endless ? run.score : 0
        case .scoreTimeAttack: return run.mode == .timeAttack ? run.score : 0
        case .reachMultiplier: return run.maxMultiplier
        case .playDaily: return run.mode == .daily ? 1 : 0
        }
    }
}

struct MissionProgress: Codable, Equatable, Identifiable {
    var id: String
    var kind: MissionKind
    var goal: Int
    var progress: Int = 0
    var reward: Int
    var claimed = false

    var isComplete: Bool { progress >= goal }
    var title: String { kind.title(goal: goal) }
    var fraction: Double { goal > 0 ? min(Double(progress) / Double(goal), 1) : 1 }

    mutating func apply(_ run: RunResult) {
        guard !claimed else { return }
        let value = kind.value(from: run)
        if kind.isSingleRun {
            progress = max(progress, min(value, goal))
        } else {
            progress = min(goal, progress + value)
        }
    }
}

enum MissionGenerator {
    static let rewards = [60, 120, 220]
    static let allCompleteBonus = 200

    static func missions(for dayKey: String) -> [MissionProgress] {
        var rng = SeededRandom(seed: StableHash.fnv1a("missions-" + dayKey))
        var available = MissionKind.allCases
        var result: [MissionProgress] = []

        for slot in 0..<3 {
            let candidates = available.filter { slot == 0 || !$0.easyOnly }
            guard !candidates.isEmpty else { break }
            let kind = candidates[rng.int(0...(candidates.count - 1))]
            available.removeAll { $0 == kind }
            // Avoid two near-identical star missions on one day.
            if kind == .collectStars { available.removeAll { $0 == .starsInRun } }
            if kind == .starsInRun { available.removeAll { $0 == .collectStars } }
            result.append(MissionProgress(id: "\(dayKey)-\(slot)", kind: kind,
                                          goal: kind.goals[slot], reward: rewards[slot]))
        }
        return result
    }
}
