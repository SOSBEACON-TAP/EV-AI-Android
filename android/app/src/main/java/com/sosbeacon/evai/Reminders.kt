package com.sosbeacon.evai

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra("text") ?: "Reminder"
        val id = intent.getIntExtra("id", 0)
        Reminders.notify(context, text, id)
        Store.removeReminder(id)
    }
}

object Reminders {
    private const val CH = "evai_reminders"

    fun notify(c: Context, text: String, id: Int) {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CH, "Reminders and timers", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(c, CH)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("EV AI")
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try { nm.notify(id, n) } catch (_: SecurityException) { }
    }

    /** Schedules a notification at [atMillis]. Returns the reminder id. */
    fun schedule(c: Context, atMillis: Long, text: String): Int {
        val id = (System.currentTimeMillis() % 1_000_000).toInt()
        val pi = PendingIntent.getBroadcast(
            c, id, Intent(c, ReminderReceiver::class.java).putExtra("text", text).putExtra("id", id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= 31 && am.canScheduleExactAlarms() || Build.VERSION.SDK_INT < 31) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        Store.addReminder(Store.Reminder(id, atMillis, text))
        return id
    }

    fun cancel(c: Context, id: Int) {
        val pi = PendingIntent.getBroadcast(
            c, id, Intent(c, ReminderReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi)
        Store.removeReminder(id)
    }
}
