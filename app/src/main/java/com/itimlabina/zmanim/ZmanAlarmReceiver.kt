package com.itimlabina.zmanim

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Calendar

class ZmanAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("zman_title") ?: "זמן הלכתי מתקרב"
        val timeStr = intent.getStringExtra("zman_time") ?: ""
        val minutesBefore = intent.getIntExtra("minutes_before", 10)

        val channelId = "itim_labina_alerts"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "התראות זמני היום - עיתים לבינה",
                NotificationManager.IMPORTANCE_HIGH
            )
            nm.createNotificationChannel(channel)
        }

        val bodyText = if (minutesBefore == 0) {
            "כעת הגיע זמן $title ($timeStr)"
        } else {
            "בעוד $minutesBefore דקות יגיע זמן $title (בשעה $timeStr)"
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("תזכורת לוח עיתים לבינה: $title")
            .setContentText(bodyText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        nm.notify(title.hashCode(), notification)
    }

    companion object {
        fun scheduleAllEnabledAlarms(context: Context) {
            val prefs = context.getSharedPreferences("ZmanimPrefs", Context.MODE_PRIVATE)
            val loc = ZmanimHelper.getSavedLocation(context)
            val zmanim = ZmanimHelper.calculateDailyZmanim(loc, Calendar.getInstance())
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val now = System.currentTimeMillis()

            for (z in zmanim) {
                val enabled = prefs.getBoolean("alert_${z.id}_enabled", false)
                val minsBefore = prefs.getInt("alert_${z.id}_mins", 10)
                val intent = Intent(context, ZmanAlarmReceiver::class.java).apply {
                    putExtra("zman_title", z.title)
                    putExtra("zman_time", z.formattedTime)
                    putExtra("minutes_before", minsBefore)
                }
                val pi = PendingIntent.getBroadcast(
                    context,
                    z.id.hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                if (enabled && z.time != null) {
                    val triggerAt = z.time.time - (minsBefore * 60_000L)
                    if (triggerAt > now) {
                        try {
                            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                        } catch (_: SecurityException) {
                            am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                        }
                    }
                } else {
                    am.cancel(pi)
                }
            }
        }
    }
}
