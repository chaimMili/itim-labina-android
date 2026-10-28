package com.itimlabina.zmanim

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CalendarReminder(
    val id: String,
    val dateKey: String,
    val title: String,
    val notes: String = "",
    val timeStr: String,
    val timestamp: Long,
    val color: Int = ReminderColors.DEFAULT,
    val minutesBefore: Int = 0,
    val soundMode: String = "default", // default | silent | custom
    val soundUri: String? = null,
    val soundName: String? = null
)

object ReminderColors {
    val palette: List<Int> = listOf(
        0xFFEF4444.toInt(),
        0xFFF97316.toInt(),
        0xFFFBBF24.toInt(),
        0xFF22C55E.toInt(),
        0xFF14B8A6.toInt(),
        0xFF38BDF8.toInt(),
        0xFF6366F1.toInt(),
        0xFFA855F7.toInt(),
        0xFFEC4899.toInt()
    )
    val DEFAULT: Int = 0xFF38BDF8.toInt()
}

/** שמירת תזכורות: מפתח לכל יום (yyyy_MM_dd) ← מערך JSON. תואם לאחור לנתונים שכבר נשמרו. */
object ReminderStore {
    private const val PREFS = "ZmanimReminders"
    private val KEY_REGEX = Regex("\\d{4}_\\d{2}_\\d{2}")

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun newId(): String = System.currentTimeMillis().toString() + "_" + (0..999).random()

    fun dateKey(millis: Long): String {
        val fmt = SimpleDateFormat("yyyy_MM_dd", Locale.US)
        fmt.timeZone = JERUSALEM
        return fmt.format(Date(millis))
    }

    fun load(context: Context, dateKey: String): List<CalendarReminder> {
        val json = prefs(context).getString(dateKey, "[]") ?: "[]"
        val out = mutableListOf<CalendarReminder>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    CalendarReminder(
                        id = o.getString("id"),
                        dateKey = o.optString("dateKey", dateKey),
                        title = o.optString("title", ""),
                        notes = o.optString("notes", ""),
                        timeStr = o.optString("timeStr", "09:00"),
                        timestamp = o.optLong("timestamp", 0L),
                        color = o.optInt("color", ReminderColors.DEFAULT),
                        minutesBefore = o.optInt("minutesBefore", 0),
                        soundMode = o.optString("soundMode", "default"),
                        soundUri = if (o.has("soundUri") && !o.isNull("soundUri")) o.getString("soundUri") else null,
                        soundName = if (o.has("soundName") && !o.isNull("soundName")) o.getString("soundName") else null
                    )
                )
            }
        } catch (_: Exception) {
        }
        return out.sortedBy { it.timestamp }
    }

    private fun save(context: Context, dateKey: String, list: List<CalendarReminder>) {
        val editor = prefs(context).edit()
        if (list.isEmpty()) {
            editor.remove(dateKey)
        } else {
            val arr = JSONArray()
            for (r in list) {
                val o = JSONObject()
                o.put("id", r.id)
                o.put("dateKey", r.dateKey)
                o.put("title", r.title)
                o.put("notes", r.notes)
                o.put("timeStr", r.timeStr)
                o.put("timestamp", r.timestamp)
                o.put("color", r.color)
                o.put("minutesBefore", r.minutesBefore)
                o.put("soundMode", r.soundMode)
                o.put("soundUri", r.soundUri ?: JSONObject.NULL)
                o.put("soundName", r.soundName ?: JSONObject.NULL)
                arr.put(o)
            }
            editor.putString(dateKey, arr.toString())
        }
        editor.apply()
    }

    /** יצירה או עדכון; אם התאריך השתנה, התזכורת מוסרת מהיום הקודם. */
    fun upsert(context: Context, r: CalendarReminder, previousDateKey: String? = null) {
        if (previousDateKey != null && previousDateKey != r.dateKey) {
            save(context, previousDateKey, load(context, previousDateKey).filter { it.id != r.id })
        }
        save(context, r.dateKey, load(context, r.dateKey).filter { it.id != r.id } + r)
    }

    fun delete(context: Context, r: CalendarReminder) {
        save(context, r.dateKey, load(context, r.dateKey).filter { it.id != r.id })
    }

    fun colorsForDay(context: Context, dateKey: String): List<Int> =
        load(context, dateKey).map { it.color }.distinct().take(3)

    fun all(context: Context): List<CalendarReminder> {
        val result = mutableListOf<CalendarReminder>()
        for ((key, value) in prefs(context).all) {
            if (value is String && KEY_REGEX.matches(key)) result.addAll(load(context, key))
        }
        return result
    }
}
