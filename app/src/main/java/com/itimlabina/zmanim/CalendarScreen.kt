package com.itimlabina.zmanim

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private data class DayInfo(
    val millis: Long,
    val hebDay: String,
    val gregLabel: String,
    val special: String,
    val isShabbat: Boolean,
    val isYomTov: Boolean,
    val key: String
)

@Composable
fun CalendarScreen(
    selectedCal: Calendar,
    remindersVersion: Int,
    onSelectDay: (Long) -> Unit,
    onOpenZmanim: () -> Unit,
    onEditReminder: (CalendarReminder) -> Unit
) {
    val context = LocalContext.current
    val hebFormatter = remember { HebrewDateFormatter().apply { isHebrewFormat = true } }
    var monthOffset by remember { mutableIntStateOf(0) }

    val displayCal = remember(monthOffset) {
        Calendar.getInstance(JERUSALEM).apply { add(Calendar.MONTH, monthOffset) }
    }
    val baseJewish = remember(monthOffset) { JewishCalendar(displayCal).apply { inIsrael = true } }
    val firstDayCal = remember(monthOffset) {
        (displayCal.clone() as Calendar).apply {
            add(Calendar.DAY_OF_MONTH, -(baseJewish.jewishDayOfMonth - 1))
        }
    }
    val firstDayJewish = remember(monthOffset) { JewishCalendar(firstDayCal).apply { inIsrael = true } }
    val daysInMonth = firstDayJewish.daysInJewishMonth
    val startDayOfWeek = firstDayCal.get(Calendar.DAY_OF_WEEK)

    val hebMonthTitle = "${hebFormatter.formatMonth(firstDayJewish)} ${hebFormatter.formatHebrewNumber(firstDayJewish.jewishYear)}"
    val gregMonthTitle = SimpleDateFormat("MMMM yyyy", Locale("he", "IL")).apply { timeZone = JERUSALEM }.format(displayCal.time)

    val days = remember(monthOffset) {
        (1..daysInMonth).map { n ->
            val c = (firstDayCal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, n - 1) }
            val j = JewishCalendar(c).apply { inIsrael = true }
            val yomTov = hebFormatter.formatYomTov(j)
            DayInfo(
                millis = c.timeInMillis,
                hebDay = hebFormatter.formatHebrewNumber(n),
                gregLabel = "${c.get(Calendar.DAY_OF_MONTH)}/${c.get(Calendar.MONTH) + 1}",
                special = if (yomTov.isNotEmpty()) yomTov else if (j.isRoshChodesh) "ר״ח" else "",
                isShabbat = c.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY,
                isYomTov = j.isYomTovAssurBemelacha,
                key = ReminderStore.dateKey(c.timeInMillis)
            )
        }
    }

    val dotsByKey = remember(monthOffset, remindersVersion) {
        val map = HashMap<String, List<Int>>()
        for (d in days) {
            val colors = ReminderStore.colorsForDay(context, d.key)
            if (colors.isNotEmpty()) map[d.key] = colors
        }
        map
    }

    val todayCal = remember { Calendar.getInstance(JERUSALEM) }
    val selectedKey = ReminderStore.dateKey(selectedCal.timeInMillis)
    val dayReminders = remember(selectedKey, remindersVersion) { ReminderStore.load(context, selectedKey) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 96.dp)
    ) {
        // כותרת חודש וניווט
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { monthOffset -= 1 }) {
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "חודש קודם")
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        monthOffset = 0
                        onSelectDay(System.currentTimeMillis())
                    }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    hebMonthTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (monthOffset == 0) gregMonthTitle else "$gregMonthTitle • חזרה להיום",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { monthOffset += 1 }) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "חודש הבא")
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("א׳", "ב׳", "ג׳", "ד׳", "ה׳", "ו׳", "ש׳").forEach { d ->
                Text(
                    text = d,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (d == "ש׳") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))

        val totalCells = (startDayOfWeek - 1) + daysInMonth
        val weeks = (totalCells + 6) / 7
        for (week in 0 until weeks) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val dayNumber = week * 7 + col - (startDayOfWeek - 1) + 1
                    if (dayNumber in 1..daysInMonth) {
                        val info = days[dayNumber - 1]
                        val cellCal = calendarOf(info.millis)
                        DayCell(
                            info = info,
                            dots = dotsByKey[info.key] ?: emptyList(),
                            isToday = isSameDay(cellCal, todayCal),
                            isSelected = isSameDay(cellCal, selectedCal),
                            onClick = { onSelectDay(info.millis) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // יום נבחר: תזכורות ואירועים
        SurfaceCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        SectionTitle("תזכורות")
                        Text(
                            gregorianShortDate(selectedCal.timeInMillis),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(onClick = onOpenZmanim) {
                        Text("זמני היום", maxLines = 1)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (dayReminders.isEmpty()) {
                    Text(
                        "אין תזכורות ליום הזה. לחצו על \"תזכורת\" כדי להוסיף.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    dayReminders.forEach { r ->
                        ReminderRow(r = r, onClick = { onEditReminder(r) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    info: DayInfo,
    dots: List<Int>,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    val bg = if (isSelected) scheme.primary else Color.Transparent
    val numberColor = when {
        isSelected -> scheme.onPrimary
        info.isShabbat || info.isYomTov -> scheme.secondary
        else -> scheme.onSurface
    }
    val subColor = if (isSelected) scheme.onPrimary.copy(alpha = 0.8f) else scheme.onSurfaceVariant

    Box(
        modifier = modifier
            .aspectRatio(0.78f)
            .padding(2.dp)
            .clip(shape)
            .background(bg)
            .then(
                if (isToday && !isSelected) Modifier.border(BorderStroke(1.5.dp, scheme.primary), shape) else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                info.hebDay,
                color = numberColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(info.gregLabel, color = subColor, fontSize = 10.sp, maxLines = 1)
            Box(modifier = Modifier.height(12.dp), contentAlignment = Alignment.Center) {
                if (dots.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        dots.forEach { c ->
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) scheme.onPrimary else Color(c))
                            )
                        }
                    }
                } else if (info.special.isNotEmpty()) {
                    Text(
                        info.special,
                        color = if (isSelected) scheme.onPrimary else scheme.secondary,
                        fontSize = 8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ReminderRow(r: CalendarReminder, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(Color(r.color))
        )
        Spacer(modifier = Modifier.padding(horizontal = 6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                r.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "התראה: ${formatLeadBefore(r.minutesBefore)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.padding(horizontal = 6.dp))
        Text(
            r.timeStr,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            softWrap = false
        )
    }
}
