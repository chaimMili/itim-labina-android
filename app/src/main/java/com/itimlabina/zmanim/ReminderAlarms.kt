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

/** מופעל בזמן ההתראה של תזכורת. הנתונים נטענים מהאחסון, כך שתזכורת שנמחקה או נערכה לא תופעל בטעות. */
class CalendarReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(ReminderScheduler.EXTRA_ID) ?: return
        val key = intent.getStringExtra(ReminderScheduler.EXTRA_DATE_KEY) ?: return
        val reminder = ReminderStore.load(context, key).firstOrNull { it.id == id } ?: return
        NotificationHelper.showReminder(context, reminder)
    }
}

/** מחדש את כל ההתראות אחרי הפעלה מחדש של המכשיר או עדכון האפליקציה. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            ReminderScheduler.rescheduleAll(context)
            ZmanAlarmReceiver.scheduleAllEnabledAlarms(context)
        }
    }
}

object ReminderScheduler {
    const val EXTRA_ID = "reminder_id"
    const val EXTRA_DATE_KEY = "reminder_date_key"

    private fun intentFor(context: Context, id: String, dateKey: String): Intent =
        Intent(context, CalendarReminderReceiver::class.java).apply {
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_DATE_KEY, dateKey)
        }

    fun schedule(context: Context, r: CalendarReminder) {
        val triggerAt = r.timestamp - r.minutesBefore * 60_000L
        if (triggerAt <= System.currentTimeMillis()) {
            cancel(context, r.id)
            return
        }
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context,
            r.id.hashCode(),
            intentFor(context, r.id, r.dateKey),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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
    }

    fun cancel(context: Context, id: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            Intent(context, CalendarReminderReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pi != null) {
            am.cancel(pi)
            pi.cancel()
        }
    }

    fun rescheduleAll(context: Context) {
        for (r in ReminderStore.all(context)) schedule(context, r)
    }
}

object NotificationHelper {
    fun showReminder(context: Context, r: CalendarReminder) {
        val prefs = context.getSharedPreferences("ZmanimPrefs", Context.MODE_PRIVATE)
        val vibrate = prefs.getBoolean("vibrate_enabled", true)

        val soundUri: Uri? = when (r.soundMode) {
            "silent" -> null
            "custom" -> r.soundUri?.let { Uri.parse(it) }
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }
        if (r.soundMode == "custom" && soundUri != null) SoundStore.grantToSystem(context, soundUri)

        val soundKey = when (r.soundMode) {
            "silent" -> "silent"
            "custom" -> "c${(r.soundUri ?: "").hashCode()}"
            else -> "default"
        }
        val channelId = "reminder_${soundKey}_$vibrate"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "תזכורות מהיומן", NotificationManager.IMPORTANCE_HIGH).apply {
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

        val body = if (r.minutesBefore <= 0) {
            "עכשיו • ${r.timeStr}"
        } else {
            "בעוד ${formatDuration(r.minutesBefore)} • בשעה ${r.timeStr}"
        }

        val open = PendingIntent.getActivity(
            context,
            r.id.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                putExtra(IntentKeys.OPEN_TAB, 1)
                putExtra(IntentKeys.DAY_MILLIS, r.timestamp)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(r.title)
            .setContentText(body)
            .setColor(r.color)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)

        if (r.notes.isNotBlank()) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body + "\n" + r.notes))
        }
        if (soundUri != null) builder.setSound(soundUri)
        if (vibrate) builder.setVibrate(longArrayOf(0, 400, 200, 400))

        nm.notify(r.id.hashCode(), builder.build())
    }
}
