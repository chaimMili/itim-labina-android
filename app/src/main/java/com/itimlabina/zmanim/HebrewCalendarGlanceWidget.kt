package com.itimlabina.zmanim

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.kosherjava.zmanim.ComplexZmanimCalendar
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.util.GeoLocation
import com.kosherjava.zmanim.util.NOAACalculator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val MONTH_OFFSET_KEY = intPreferencesKey("month_offset")
private val SELECTED_TIME_KEY = longPreferencesKey("selected_time_millis")
private val PARAM_DELTA = ActionParameters.Key<Int>("delta")
private val PARAM_DAY_MILLIS = ActionParameters.Key<Long>("day_millis")

enum class WidgetDisplayMode {
    COMBINED,
    CALENDAR_ONLY,
    ZMANIM_ONLY
}

class HebrewCalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BaseItimWidget(WidgetDisplayMode.COMBINED)
}

class CalendarOnlyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BaseItimWidget(WidgetDisplayMode.CALENDAR_ONLY)
}

class ZmanimOnlyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BaseItimWidget(WidgetDisplayMode.ZMANIM_ONLY)
}

class BaseItimWidget(private val mode: WidgetDisplayMode = WidgetDisplayMode.COMBINED) : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                val prefs = currentState<Preferences>()
                val monthOffset = prefs[MONTH_OFFSET_KEY] ?: 0
                val selectedMillis = prefs[SELECTED_TIME_KEY] ?: System.currentTimeMillis()

                WidgetRootContent(
                    context = context,
                    mode = mode,
                    monthOffset = monthOffset,
                    selectedMillis = selectedMillis
                )
            }
        }
    }

    @Composable
    private fun WidgetRootContent(
        context: Context,
        mode: WidgetDisplayMode,
        monthOffset: Int,
        selectedMillis: Long
    ) {
        val timeZone = TimeZone.getTimeZone("Asia/Jerusalem")
        val hebFormatter = HebrewDateFormatter().apply { isHebrewFormat = true }
        val selectedCal = Calendar.getInstance(timeZone).apply { timeInMillis = selectedMillis }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color(0xFF0F172A)))
                .cornerRadius(20.dp)
                .padding(10.dp)
        ) {
            when (mode) {
                WidgetDisplayMode.ZMANIM_ONLY -> {
                    ZmanimOnlyFullView(context, Calendar.getInstance(timeZone), hebFormatter)
                }
                WidgetDisplayMode.CALENDAR_ONLY -> {
                    ResponsiveCalendarGrid(
                        monthOffset = monthOffset,
                        selectedCal = selectedCal,
                        hebFormatter = hebFormatter,
                        modifier = GlanceModifier.defaultWeight().fillMaxWidth()
                    )
                }
                WidgetDisplayMode.COMBINED -> {
                    ResponsiveCalendarGrid(
                        monthOffset = monthOffset,
                        selectedCal = selectedCal,
                        hebFormatter = hebFormatter,
                        modifier = GlanceModifier.defaultWeight().fillMaxWidth()
                    )
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    SelectedDayZmanimFooter(context, selectedCal, hebFormatter)
                }
            }
        }
    }

    @Composable
    private fun ResponsiveCalendarGrid(
        monthOffset: Int,
        selectedCal: Calendar,
        hebFormatter: HebrewDateFormatter,
        modifier: GlanceModifier
    ) {
        val timeZone = TimeZone.getTimeZone("Asia/Jerusalem")
        val displayCal = Calendar.getInstance(timeZone).apply {
            add(Calendar.MONTH, monthOffset)
        }
        val baseJewish = JewishCalendar(displayCal).apply { inIsrael = true }

        val firstDayCal = (displayCal.clone() as Calendar).apply {
            add(Calendar.DAY_OF_MONTH, -(baseJewish.jewishDayOfMonth - 1))
        }
        val firstDayJewish = JewishCalendar(firstDayCal).apply { inIsrael = true }
        val daysInHebrewMonth = firstDayJewish.daysInJewishMonth
        val startDayOfWeek = firstDayCal.get(Calendar.DAY_OF_WEEK)

        val gregMonthFmt = SimpleDateFormat("MMMM yyyy", Locale("he"))
        val monthTitle = "${hebFormatter.formatMonth(firstDayJewish)} ${hebFormatter.formatHebrewNumber(firstDayJewish.jewishYear)} • ${gregMonthFmt.format(displayCal.time)}"
        val todayCal = Calendar.getInstance(timeZone)

        val totalCellsUsed = (startDayOfWeek - 1) + daysInHebrewMonth
        val weeksCount = ((totalCellsUsed + 6) / 7).coerceIn(4, 6)

        Column(modifier = modifier) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(32.dp)
                        .background(ColorProvider(Color(0xFF1E293B)))
                        .cornerRadius(8.dp)
                        .clickable(
                            actionRunCallback<ChangeMonthAction>(
                                actionParametersOf(PARAM_DELTA to 1)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("◀", style = TextStyle(color = ColorProvider(Color.White), fontSize = 14.sp))
                }

                Column(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .clickable(
                            actionRunCallback<ChangeMonthAction>(
                                actionParametersOf(PARAM_DELTA to 0)
                            )
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = monthTitle,
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFFBBF24)),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = if (monthOffset == 0) "לוח עיתים לבינה" else "לחץ לחזרה להיום",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF94A3B8)),
                            fontSize = 10.sp
                        )
                    )
                }

                Box(
                    modifier = GlanceModifier
                        .size(32.dp)
                        .background(ColorProvider(Color(0xFF1E293B)))
                        .cornerRadius(8.dp)
                        .clickable(
                            actionRunCallback<ChangeMonthAction>(
                                actionParametersOf(PARAM_DELTA to -1)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("▶", style = TextStyle(color = ColorProvider(Color.White), fontSize = 14.sp))
                }
            }

            val weekDays = listOf("ש׳", "ו׳", "ה׳", "ד׳", "ג׳", "ב׳", "א׳")
            Row(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 2.dp)) {
                for (dayLabel in weekDays) {
                    Text(
                        text = dayLabel,
                        modifier = GlanceModifier.defaultWeight(),
                        style = TextStyle(
                            color = ColorProvider(if (dayLabel == "ש׳") Color(0xFFFBBF24) else Color(0xFF94A3B8)),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    )
                }
            }

            for (week in 0 until weeksCount) {
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight()
                        .padding(vertical = 1.dp)
                ) {
                    for (col in 6 downTo 0) {
                        val cellIndex = week * 7 + col
                        val hebDayNumber = cellIndex - (startDayOfWeek - 1) + 1

                        if (hebDayNumber in 1..daysInHebrewMonth) {
                            val cellCal = (firstDayCal.clone() as Calendar).apply {
                                add(Calendar.DAY_OF_MONTH, hebDayNumber - 1)
                            }
                            val cellJewish = JewishCalendar(cellCal).apply { inIsrael = true }

                            val isToday = isSameDay(cellCal, todayCal)
                            val isSelected = isSameDay(cellCal, selectedCal)
                            val isShabbatOrYomTov = cellCal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || cellJewish.isYomTovAssurBemelacha

                            val bgColor = when {
                                isSelected -> Color(0xFFD97706)
                                isToday -> Color(0xFF1E3A8A)
                                else -> Color(0xFF1E293B)
                            }

                            val textColor = when {
                                isSelected || isToday -> Color.White
                                isShabbatOrYomTov -> Color(0xFFFBBF24)
                                else -> Color(0xFFE2E8F0)
                            }

                            Box(
                                modifier = GlanceModifier
                                    .defaultWeight()
                                    .fillMaxHeight()
                                    .padding(1.dp)
                                    .background(ColorProvider(bgColor))
                                    .cornerRadius(6.dp)
                                    .clickable(
                                        actionRunCallback<SelectDayAction>(
                                            actionParametersOf(PARAM_DAY_MILLIS to cellCal.timeInMillis)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = hebFormatter.formatHebrewNumber(hebDayNumber),
                                        style = TextStyle(
                                            color = ColorProvider(textColor),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Text(
                                        text = "${cellCal.get(Calendar.DAY_OF_MONTH)}/${cellCal.get(Calendar.MONTH) + 1}",
                                        style = TextStyle(
                                            color = ColorProvider(Color(0xFF94A3B8)),
                                            fontSize = 9.sp
                                        )
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = GlanceModifier
                                    .defaultWeight()
                                    .fillMaxHeight()
                                    .padding(1.dp)
                            ) {}
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ZmanimOnlyFullView(
        context: Context,
        cal: Calendar,
        hebFormatter: HebrewDateFormatter
    ) {
        val loc = ZmanimHelper.getSavedLocation(context)
        val timeZone = TimeZone.getTimeZone("Asia/Jerusalem")
        val geo = GeoLocation(loc.name, loc.lat, loc.lng, loc.elevation, timeZone)
        val zmanim = ComplexZmanimCalendar(geo).apply {
            astronomicalCalculator = NOAACalculator()
            calendar = cal
        }
        val jCal = JewishCalendar(cal).apply { inIsrael = true }
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault()).apply { this.timeZone = timeZone }
        fun fmt(d: Date?): String = if (d != null) timeFmt.format(d) else "--:--"

        val yomTov = hebFormatter.formatYomTov(jCal)
        val parasha = if (yomTov.isNotEmpty()) yomTov else hebFormatter.formatParsha(jCal)

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .clickable(actionStartActivity<MainActivity>())
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (parasha.isNotEmpty()) "$parasha • ${loc.name}" else loc.name,
                    style = TextStyle(color = ColorProvider(Color(0xFFCBD5E1)), fontSize = 12.sp)
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = hebFormatter.format(jCal),
                    style = TextStyle(color = ColorProvider(Color(0xFFFBBF24)), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                )
            }

            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                ZmanCell("סזק״ש גר״א", fmt(zmanim.sofZmanShmaGRA), GlanceModifier.defaultWeight())
                ZmanCell("סזק״ש מג״א", fmt(zmanim.sofZmanShmaMGA16Point1Degrees), GlanceModifier.defaultWeight())
                ZmanCell("הנץ החמה", fmt(zmanim.sunrise), GlanceModifier.defaultWeight())
                ZmanCell("עלות (16.1°)", fmt(zmanim.alos16Point1Degrees), GlanceModifier.defaultWeight())
            }

            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                ZmanCell("רבינו תם", fmt(zmanim.tzais72Zmanis), GlanceModifier.defaultWeight())
                ZmanCell("צאת הכוכבים", fmt(zmanim.tzaisGeonim4Point61Degrees), GlanceModifier.defaultWeight())
                ZmanCell("שקיעה", fmt(zmanim.sunset), GlanceModifier.defaultWeight())
                ZmanCell("חצות היום", fmt(zmanim.chatzos), GlanceModifier.defaultWeight())
            }
        }
    }

    @Composable
    private fun SelectedDayZmanimFooter(
        context: Context,
        selectedCal: Calendar,
        hebFormatter: HebrewDateFormatter
    ) {
        val loc = ZmanimHelper.getSavedLocation(context)
        val timeZone = TimeZone.getTimeZone("Asia/Jerusalem")
        val geo = GeoLocation(loc.name, loc.lat, loc.lng, loc.elevation, timeZone)

        val zmanim = ComplexZmanimCalendar(geo).apply {
            astronomicalCalculator = NOAACalculator()
            calendar = selectedCal
        }
        val jCal = JewishCalendar(selectedCal).apply { inIsrael = true }

        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
            this.timeZone = timeZone
        }
        fun fmt(d: Date?): String = if (d != null) timeFmt.format(d) else "--:--"

        val yomTov = hebFormatter.formatYomTov(jCal)
        val parashaOrHoliday = if (yomTov.isNotEmpty()) yomTov else hebFormatter.formatParsha(jCal)

        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(ColorProvider(Color(0xFF1E293B)))
                .cornerRadius(12.dp)
                .padding(8.dp)
                .clickable(actionStartActivity<MainActivity>())
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (parashaOrHoliday.isNotEmpty()) "$parashaOrHoliday • ${loc.name}" else loc.name,
                    style = TextStyle(color = ColorProvider(Color(0xFF94A3B8)), fontSize = 11.sp)
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = hebFormatter.format(jCal),
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFFBBF24)),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Row(modifier = GlanceModifier.fillMaxWidth()) {
                ZmanCell("צאת הכוכבים", fmt(zmanim.tzaisGeonim4Point61Degrees), GlanceModifier.defaultWeight())
                ZmanCell("שקיעה", fmt(zmanim.sunset), GlanceModifier.defaultWeight())
                ZmanCell("חצות", fmt(zmanim.chatzos), GlanceModifier.defaultWeight())
                ZmanCell("הנץ החמה", fmt(zmanim.sunrise), GlanceModifier.defaultWeight())
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            Row(modifier = GlanceModifier.fillMaxWidth()) {
                ZmanCell("רבינו תם", fmt(zmanim.tzais72Zmanis), GlanceModifier.defaultWeight())
                ZmanCell("מנחה גדולה", fmt(zmanim.minchaGedola), GlanceModifier.defaultWeight())
                ZmanCell("סזק״ש גר״א", fmt(zmanim.sofZmanShmaGRA), GlanceModifier.defaultWeight())
                ZmanCell("עלות (16.1°)", fmt(zmanim.alos16Point1Degrees), GlanceModifier.defaultWeight())
            }
        }
    }

    @Composable
    private fun ZmanCell(label: String, time: String, modifier: GlanceModifier) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = TextStyle(color = ColorProvider(Color(0xFF94A3B8)), fontSize = 9.sp)
            )
            Text(
                text = time,
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }

    private fun isSameDay(c1: Calendar, c2: Calendar): Boolean {
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
               c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }

    companion object {
        suspend fun updateAllWidgets(context: Context) {
            BaseItimWidget(WidgetDisplayMode.COMBINED).updateAll(context)
            BaseItimWidget(WidgetDisplayMode.CALENDAR_ONLY).updateAll(context)
            BaseItimWidget(WidgetDisplayMode.ZMANIM_ONLY).updateAll(context)
        }
    }
}

class ChangeMonthAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val delta = parameters[PARAM_DELTA] ?: 0
        updateAppWidgetState(context, glanceId) { prefs ->
            if (delta == 0) {
                prefs[MONTH_OFFSET_KEY] = 0
                prefs[SELECTED_TIME_KEY] = System.currentTimeMillis()
            } else {
                val current = prefs[MONTH_OFFSET_KEY] ?: 0
                prefs[MONTH_OFFSET_KEY] = current + delta
            }
        }
        BaseItimWidget.updateAllWidgets(context)
    }
}

class SelectDayAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val dayMillis = parameters[PARAM_DAY_MILLIS] ?: return
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[SELECTED_TIME_KEY] = dayMillis
        }
        BaseItimWidget.updateAllWidgets(context)
    }
}
