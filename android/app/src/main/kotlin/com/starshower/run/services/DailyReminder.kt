//
//  DailyReminder.kt
//  Starshower Run
//
//  A single, friendly daily reminder at 6pm that a new Daily Run is live.
//  Uses an inexact repeating alarm (no exact-alarm permission needed) and
//  re-arms itself after a reboot.
//

package com.starshower.run.services

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.starshower.run.MainActivity
import com.starshower.run.R
import com.starshower.run.core.progression.SettingsKey
import java.time.LocalTime
import java.time.ZonedDateTime

object DailyReminder {
    private const val CHANNEL_ID = "daily_run"
    private const val NOTIFICATION_ID = 6_000
    private val reminderTime: LocalTime = LocalTime.of(18, 0)

    /** True if the app may post notifications (always before Android 13). */
    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Schedules (or cancels) the reminder. */
    fun setEnabled(context: Context, enabled: Boolean) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = pendingIntent(context)
        alarms.cancel(intent)
        if (!enabled) return
        ensureChannel(context)
        alarms.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            nextTrigger().toInstant().toEpochMilli(),
            AlarmManager.INTERVAL_DAY,
            intent,
        )
    }

    private fun nextTrigger(): ZonedDateTime {
        val now = ZonedDateTime.now()
        val today = now.with(reminderTime)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, DailyReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.reminder_channel_description) }
        manager.createNotificationChannel(channel)
    }

    internal fun post(context: Context) {
        if (!hasPermission(context)) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(context.getString(R.string.reminder_body))
            .setStyle(Notification.BigTextStyle().bigText(context.getString(R.string.reminder_body)))
            .setColor(0xFF38E1FF.toInt())
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification)
    }

    internal fun isEnabledInPreferences(context: Context): Boolean =
        context.getSharedPreferences(Preferences.FILE_NAME, Context.MODE_PRIVATE).getBoolean(SettingsKey.NOTIFICATIONS, false)
}

class DailyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (DailyReminder.isEnabledInPreferences(context)) DailyReminder.post(context)
    }
}

/** Alarms don't survive a reboot, so re-arm the reminder if it's on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && DailyReminder.isEnabledInPreferences(context)) {
            DailyReminder.setEnabled(context, true)
        }
    }
}
