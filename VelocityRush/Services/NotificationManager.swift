//
//  NotificationManager.swift
//  VelocityRush
//
//  A single, friendly daily reminder that a new Daily Run is live.
//

import UserNotifications

enum NotificationManager {
    private static let dailyIdentifier = "vr.daily.reminder"

    /// Schedules (or cancels) the reminder. Calls back with whether it's enabled.
    static func setDailyReminder(enabled: Bool, completion: ((Bool) -> Void)? = nil) {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: [dailyIdentifier])
        guard enabled else {
            completion?(false)
            return
        }

        center.requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
            guard granted else {
                DispatchQueue.main.async { completion?(false) }
                return
            }
            let content = UNMutableNotificationContent()
            content.title = "A new Daily Run is live ⚡️"
            content.body = "Fresh twist, fresh missions, fresh leaderboard. Can you top today's board?"
            content.sound = .default

            var components = DateComponents()
            components.hour = 18
            components.minute = 0
            let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: true)
            let request = UNNotificationRequest(identifier: dailyIdentifier, content: content, trigger: trigger)
            center.add(request)
            DispatchQueue.main.async { completion?(true) }
        }
    }
}
