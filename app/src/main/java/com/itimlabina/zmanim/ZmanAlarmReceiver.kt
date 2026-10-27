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
        // קובעים מחדש את ההתראה הבאה (למחר), כי כל התראה נקבעת פעם אחת בלבד
        scheduleAllEnabledAlarms(context)
    }

    companion object {
        fun getSelectedSoundUri(context: Context, soundType: String): Uri? {
            return when (soundType) {
                "alarm" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                "ringtone" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                "silent" -> null
                "custom_file" -> {
                    val saved = context.getSharedPreferences("ZmanimPrefs", Context.MODE_PRIVATE)
                        .getString("custom_sound_uri", null)
                    if (saved != null) {
                        val uri = Uri.parse(saved)
                        SoundStore.grantToSystem(context, uri)
                        uri
                    } else {
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    }
                }
                "itim_custom" -> {
                    // ברירת מחדל לאזעקת שעון מעורר של המכשיר אם מותאם אישית
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                }
                else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }
        }

        fun triggerNotification(context: Context, title: String, timeStr: String, minutesBefore: Int) {
            val prefs = context.getSharedPreferences("ZmanimPrefs", Context.MODE_PRIVATE)
            val soundType = prefs.getString("sound_type", "itim_custom") ?: "itim_custom"
            val vibrate = prefs.getBoolean("vibrate_enabled", true)

            val soundUri = getSelectedSoundUri(context, soundType)
            val soundKey = if (soundType == "custom_file") {
                "custom_file_${prefs.getString("custom_sound_uri", "")?.hashCode() ?: 0}"
            } else soundType
            val channelId = "itim_labina_channel_${soundKey}_${vibrate}"
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "התראות זמני היום",
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
                .setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        0,
                        Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .setAutoCancel(true)

            if (soundUri != null) builder.setSound(soundUri)
            if (vibrate) builder.setVibrate(longArrayOf(0, 400, 200, 400))

            nm.notify(title.hashCode(), builder.build())
        }

        fun scheduleAllEnabledAlarms(context: Context) {
            val prefs = context.getSharedPreferences("ZmanimPrefs", Context.MODE_PRIVATE)
            val loc = ZmanimHelper.getSavedLocation(context)
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val now = System.currentTimeMillis()

            // מחשבים שלושה ימים קדימה: אם ההתראה של היום כבר עברה, נקבעת זו של מחר
            val perDay = (0..2).map { offset ->
                val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offset) }
                ZmanimHelper.calculateDailyZmanim(loc, cal)
            }

            for (idx in perDay[0].indices) {
                val base = perDay[0][idx]
                val enabled = prefs.getBoolean("alert_${base.id}_enabled", false)
                val minsBefore = prefs.getInt("alert_${base.id}_mins", 10)

                var chosen: ZmanItem? = null
                var triggerAt = 0L
                if (enabled) {
                    for (day in perDay) {
                        val z = day[idx]
                        val t = z.time ?: continue
                        val candidate = t.time - minsBefore * 60_000L
                        if (candidate > now) {
                            chosen = z
                            triggerAt = candidate
                            break
                        }
                    }
                }

                val target = chosen ?: base
                val intent = Intent(context, ZmanAlarmReceiver::class.java).apply {
                    putExtra("zman_title", target.title)
                    putExtra("zman_time", target.formattedTime)
                    putExtra("minutes_before", minsBefore)
                }
                val pi = PendingIntent.getBroadcast(
                    context,
                    base.id.hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                if (chosen != null) {
                    try {
                        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
                        if (canExact) {
                            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                        } else {
                            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                        }
                    } catch (_: SecurityException) {
                        am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                    }
                } else {
                    am.cancel(pi)
                }
            }
        }
    }
}
