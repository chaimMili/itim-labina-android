package com.itimlabina.zmanim

import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

val JERUSALEM: TimeZone = TimeZone.getTimeZone("Asia/Jerusalem")
private val UTC_ZONE: TimeZone = TimeZone.getTimeZone("UTC")

/** מפתחות Intent משותפים בין הווידג'ט, ההתראות והאפליקציה. */
object IntentKeys {
    const val OPEN_TAB = "open_tab"
    const val DAY_MILLIS = "day_millis"
}

fun calendarOf(millis: Long): Calendar =
    Calendar.getInstance(JERUSALEM).apply { timeInMillis = millis }

fun isSameDay(a: Calendar, b: Calendar): Boolean =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

fun hebrewDateString(millis: Long): String {
    val j = JewishCalendar(calendarOf(millis)).apply { inIsrael = true }
    return HebrewDateFormatter().apply { isHebrewFormat = true }.format(j)
}

fun gregorianLongDate(millis: Long): String {
    val fmt = SimpleDateFormat("EEEE, d 'ב'MMMM yyyy", Locale("he", "IL"))
    fmt.timeZone = JERUSALEM
    return fmt.format(Date(millis))
}

fun gregorianShortDate(millis: Long): String {
    val fmt = SimpleDateFormat("d 'ב'MMMM yyyy", Locale("he", "IL"))
    fmt.timeZone = JERUSALEM
    return fmt.format(Date(millis))
}

fun formatClock(hour: Int, minute: Int): String =
    String.format(Locale.US, "%02d:%02d", hour, minute)

/** "5 דקות", "שעה", "שעתיים", "יום", "שעה ו־30 דקות" וכו'. */
fun formatDuration(minutes: Int): String {
    if (minutes <= 0) return "0 דקות"
    val days = minutes / 1440
    val hours = (minutes % 1440) / 60
    val mins = minutes % 60
    val parts = mutableListOf<String>()
    if (days > 0) parts.add(
        when (days) {
            1 -> "יום"
            2 -> "יומיים"
            else -> "$days ימים"
        }
    )
    if (hours > 0) parts.add(
        when (hours) {
            1 -> "שעה"
            2 -> "שעתיים"
            else -> "$hours שעות"
        }
    )
    if (mins > 0) parts.add(
        when (mins) {
            1 -> "דקה"
            2 -> "שתי דקות"
            else -> "$mins דקות"
        }
    )
    val sb = StringBuilder(parts[0])
    for (i in 1 until parts.size) {
        val next = parts[i]
        sb.append(if (next.first().isDigit()) " ו־" else " ו")
        sb.append(next)
    }
    return sb.toString()
}

fun formatLeadBefore(minutes: Int): String =
    if (minutes <= 0) "בזמן האירוע" else "${formatDuration(minutes)} לפני"

/** ברירת מחדל לתזכורת חדשה: 09:00, או השעה העגולה הבאה אם התאריך הוא היום. */
fun defaultStartMillis(dateMillis: Long): Long {
    val target = calendarOf(dateMillis)
    val now = Calendar.getInstance(JERUSALEM)
    var hour = 9
    if (isSameDay(target, now)) hour = (now.get(Calendar.HOUR_OF_DAY) + 1).coerceAtMost(23)
    target.set(Calendar.HOUR_OF_DAY, hour)
    target.set(Calendar.MINUTE, 0)
    target.set(Calendar.SECOND, 0)
    target.set(Calendar.MILLISECOND, 0)
    return target.timeInMillis
}

fun withTime(millis: Long, hour: Int, minute: Int): Long {
    val c = calendarOf(millis)
    c.set(Calendar.HOUR_OF_DAY, hour)
    c.set(Calendar.MINUTE, minute)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

/** ה-DatePicker של Material עובד ב-UTC חצות; ממירים אליו ובחזרה לשעון ישראל. */
fun localMillisToPickerUtc(millis: Long): Long {
    val local = calendarOf(millis)
    val utc = Calendar.getInstance(UTC_ZONE)
    utc.clear()
    utc.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    return utc.timeInMillis
}

fun pickerUtcToLocalMillis(utcMillis: Long, hour: Int, minute: Int): Long {
    val utc = Calendar.getInstance(UTC_ZONE)
    utc.timeInMillis = utcMillis
    val local = Calendar.getInstance(JERUSALEM)
    local.clear()
    local.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), hour, minute, 0)
    return local.timeInMillis
}
