package com.itimlabina.zmanim

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Calendar

class ZmanAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("zman_title") ?: "זמן הלכתי מתקרב"
        val timeStr = intent.getStringExtra("zman_time") ?: ""
        val minutesBefore = intent.getIntExtra("minutes_before", 10)
        triggerNotification(context, title, timeStr, minutesBefore)
    }

    companion object {
        fun getSelectedSoundUri(soundType: String): Uri? {
            return when (soundType) {
                "alarm" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                "ringtone" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                "silent" -> null
                else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }
        }

        fun triggerNotification(context: Context, title: String, timeStr: String, minutesBefore: Int) {
            val prefs = context.getSharedPreferences("ZmanimPrefs", Context.MODE_PRIVATE)
            val soundType = prefs.getString("sound_type", "notification") ?: "notification"
            val vibrate = prefs.getBoolean("vibrate_enabled", true)

            val soundUri = getSelectedSoundUri(soundType)
            val channelId = "itim_labina_channel_${soundType}_${vibrate}"
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "התראות זמני היום ($soundType)",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    enableVibration(vibrate)
                    if (soundUri != null) {
                        val attrs = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                        setSound(soundUri, attrs)
                    } else {
                        setSound(null, null)
                    }
                }
                nm.createNotificationChannel(channel)
            }

            val bodyText = if (minutesBefore == 0) {
                "כעת הגיע זמן $title ($timeStr)"
            } else {
                "בעוד $minutesBefore דקות יגיע זמן $title (בשעה $timeStr)"
            }

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("לוח עיתים לבינה: $title")
                .setContentText(bodyText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)

            if (soundUri != null) builder.setSound(soundUri)
            if (vibrate) builder.setVibrate(longArrayOf(0, 400, 200, 400))

            nm.notify(title.hashCode(), builder.build())
        }

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
