package com.pritom.nova

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        notify(ctx, "⏰ Nova রিমাইন্ডার", intent.getStringExtra("text") ?: "সময় হয়েছে!")
    }

    companion object {
        private const val CH = "nova"

        fun notify(ctx: Context, title: String, text: String) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CH, "Nova", NotificationManager.IMPORTANCE_HIGH))
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )
            val n = Notification.Builder(ctx, CH)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            nm.notify((System.currentTimeMillis() % 100000).toInt(), n)
        }

        fun schedule(ctx: Context, minutes: Int, text: String): Long {
            val at = System.currentTimeMillis() + minutes.coerceAtLeast(1) * 60_000L
            val pi = PendingIntent.getBroadcast(
                ctx, (at % 100000).toInt(),
                Intent(ctx, ReminderReceiver::class.java).putExtra("text", text),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val am = ctx.getSystemService(AlarmManager::class.java)
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } catch (e: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
            return at
        }
    }
}
