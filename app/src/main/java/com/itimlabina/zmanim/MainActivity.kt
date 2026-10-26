package com.itimlabina.zmanim

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme(
                    colorScheme = darkColorScheme(
                        primary = Color(0xFFFBBF24),
                        background = Color(0xFF0F172A),
                        surface = Color(0xFF1E293B)
                    )
                ) {
                    ItimLaBinaMainScreen()
                }
            }
        }
    }
}

// מבנה נתונים לתזכורת/אירוע ביומן
data class CalendarReminder(
    val id: String,
    val dateKey: String,
    val title: String,
    val timeStr: String, // HH:mm
    val timestamp: Long
)

class CalendarReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("reminder_title") ?: "תזכורת מיומן עיתים לבינה"
        val timeStr = intent.getStringExtra("reminder_time") ?: ""
        ZmanAlarmReceiver.triggerNotification(context, title, timeStr, 0)
    }

    companion object {
        fun scheduleReminder(context: Context, reminder: CalendarReminder) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, CalendarReminderReceiver::class.java).apply {
                putExtra("reminder_title", reminder.title)
                putExtra("reminder_time", reminder.timeStr)
            }
            val pi = PendingIntent.getBroadcast(
                context,
                reminder.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (reminder.timestamp > System.currentTimeMillis()) {
                try {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.timestamp, pi)
                } catch (_: SecurityException) {
                    am.set(AlarmManager.RTC_WAKEUP, reminder.timestamp, pi)
                }
            }
        }

        fun cancelReminder(context: Context, reminderId: String) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, CalendarReminderReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                context,
                reminderId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.cancel(pi)
        }
    }
}

@Composable
fun ItimLaBinaMainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("ZmanimPrefs", Context.MODE_PRIVATE) }

    var currentTab by remember { mutableIntStateOf(0) }
    var currentLocation by remember { mutableStateOf(ZmanimHelper.getSavedLocation(context)) }
    var showSeconds by remember { mutableStateOf(prefs.getBoolean("show_seconds", false)) }
    var selectedTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val selectedCal = remember(selectedTimeMillis) {
        Calendar.getInstance(TimeZone.getTimeZone("Asia/Jerusalem")).apply {
            timeInMillis = selectedTimeMillis
        }
    }
    val zmanimList = remember(currentLocation, selectedTimeMillis, showSeconds) {
        ZmanimHelper.calculateDailyZmanim(currentLocation, selectedCal, showSeconds)
    }
    val (hebrewDateStr, parashaStr) = remember(selectedTimeMillis) {
        ZmanimHelper.getHebrewDateTitle(selectedCal)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val gpsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            fetchGpsLocation(context) { newLoc ->
                currentLocation = newLoc
                ZmanimHelper.saveLocation(context, newLoc)
                ZmanAlarmReceiver.scheduleAllEnabledAlarms(context)
                scope.launch { BaseItimWidget.updateAllWidgets(context) }
            }
        } else {
            Toast.makeText(context, "הרשאת מיקום נדחתה", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = Color(0xFF0F172A),
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF1E293B)) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    label = { Text("זמני היום") },
                    icon = { Text("☀️", fontSize = 18.sp) }
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    label = { Text("לוח שנה") },
                    icon = { Text("📅", fontSize = 18.sp) }
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = {
                        currentTab = 2
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    },
                    label = { Text("התראות") },
                    icon = { Text("🔔", fontSize = 18.sp) }
                )
                NavigationBarItem(
                    selected = currentTab == 3,
                    onClick = { currentTab = 3 },
                    label = { Text("הגדרות") },
                    icon = { Text("⚙️", fontSize = 18.sp) }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(14.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = hebrewDateStr,
                                color = Color(0xFFFBBF24),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (parashaStr.isNotEmpty()) "$parashaStr • ${currentLocation.name}" else currentLocation.name,
                                color = Color(0xFFCBD5E1),
                                fontSize = 13.sp
                            )
                        }
                        Text(
                            text = "גובה ${currentLocation.elevation.toInt()} מ׳",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }

                    if (currentTab == 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            OutlinedButton(onClick = { selectedTimeMillis -= 86_400_000L }) { Text("▶ יום קודם") }
                            Button(
                                onClick = { selectedTimeMillis = System.currentTimeMillis() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                            ) { Text("היום") }
                            OutlinedButton(onClick = { selectedTimeMillis += 86_400_000L }) { Text("יום הבא ◀") }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (currentTab) {
                0 -> ZmanimListTab(zmanimList)
                1 -> FullHebrewCalendarTab(
                    context = context,
                    selectedCal = selectedCal,
                    zmanimList = zmanimList,
                    onSelectDay = { newMillis -> selectedTimeMillis = newMillis },
                    onOpenFullDayZmanim = { currentTab = 0 }
                )
                2 -> NotificationsTab(context, zmanimList)
                3 -> SettingsAndLocationTab(
                    context = context,
                    currentLocation = currentLocation,
                    showSeconds = showSeconds,
                    onToggleSeconds = { enabled ->
                        showSeconds = enabled
                        prefs.edit().putBoolean("show_seconds", enabled).apply()
                    },
                    onSelectCity = { loc ->
                        currentLocation = loc
                        ZmanimHelper.saveLocation(context, loc)
                        ZmanAlarmReceiver.scheduleAllEnabledAlarms(context)
                        scope.launch { BaseItimWidget.updateAllWidgets(context) }
                    },
                    onRequestGps = {
                        gpsPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun FullHebrewCalendarTab(
    context: Context,
    selectedCal: Calendar,
    zmanimList: List<ZmanItem>,
    onSelectDay: (Long) -> Unit,
    onOpenFullDayZmanim: () -> Unit
) {
    val timeZone = TimeZone.getTimeZone("Asia/Jerusalem")
    val hebFormatter = remember { HebrewDateFormatter().apply { isHebrewFormat = true } }
    var monthOffset by remember { mutableIntStateOf(0) }
    val prefs = remember { context.getSharedPreferences("ZmanimReminders", Context.MODE_PRIVATE) }

    val displayCal = remember(monthOffset) {
        Calendar.getInstance(timeZone).apply { add(Calendar.MONTH, monthOffset) }
    }
    val baseJewish = remember(monthOffset) {
        JewishCalendar(displayCal).apply { inIsrael = true }
    }
    val firstDayCal = remember(monthOffset) {
        (displayCal.clone() as Calendar).apply {
            add(Calendar.DAY_OF_MONTH, -(baseJewish.jewishDayOfMonth - 1))
        }
    }
    val firstDayJewish = remember(monthOffset) {
        JewishCalendar(firstDayCal).apply { inIsrael = true }
    }

    val daysInHebrewMonth = firstDayJewish.daysInJewishMonth
    val startDayOfWeek = firstDayCal.get(Calendar.DAY_OF_WEEK)
    
    val gregMonthFmt = SimpleDateFormat("MMMM yyyy", Locale("he"))
    val monthTitle = "${hebFormatter.formatMonth(firstDayJewish)} ${hebFormatter.formatHebrewNumber(firstDayJewish.jewishYear)} • ${gregMonthFmt.format(displayCal.time)}"
    val todayCal = remember { Calendar.getInstance(timeZone) }

    val dateKeyFormat = SimpleDateFormat("yyyy_MM_dd", Locale.getDefault()).apply { this.timeZone = timeZone }
    val selectedDateKey = dateKeyFormat.format(selectedCal.time)

    // שליפת רשימת תזכורות ליום הנבחר מ-JSON
    fun loadReminders(dateKey: String): List<CalendarReminder> {
        val jsonStr = prefs.getString(dateKey, "[]") ?: "[]"
        val list = mutableListOf<CalendarReminder>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    CalendarReminder(
                        id = obj.getString("id"),
                        dateKey = obj.getString("dateKey"),
                        title = obj.getString("title"),
                        timeStr = obj.getString("timeStr"),
                        timestamp = obj.getLong("timestamp")
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun saveReminders(dateKey: String, reminders: List<CalendarReminder>) {
        val arr = JSONArray()
        for (r in reminders) {
            val obj = JSONObject().apply {
                put("id", r.id)
                put("dateKey", r.dateKey)
                put("title", r.title)
                put("timeStr", r.timeStr)
                put("timestamp", r.timestamp)
            }
            arr.put(obj)
        }
        prefs.edit().putString(dateKey, arr.toString()).apply()
    }

    var remindersList by remember(selectedDateKey) {
        mutableStateOf(loadReminders(selectedDateKey))
    }

    var newTitle by remember { mutableStateOf("") }
    var newTimeStr by remember { mutableStateOf("09:00") }

    Column(modifier = Modifier.fillMaxSize()) {
        // שורת כותרת חודש וניווט מסודרת שלא תימרח
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { monthOffset -= 1 },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) { Text("▶ קודם", color = Color.White, fontSize = 12.sp) }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        monthOffset = 0
                        onSelectDay(System.currentTimeMillis())
                    }
                ) {
                    Text(monthTitle, color = Color(0xFFFBBF24), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    if (monthOffset != 0) {
                        Text("חזור להיום", color = Color(0xFF94A3B8), fontSize = 9.sp)
                    }
                }

                Button(
                    onClick = { monthOffset += 1 },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) { Text("הבא ◀", color = Color.White, fontSize = 12.sp) }
            }
        }

        val weekDays = listOf("א׳", "ב׳", "ג׳", "ד׳", "ה׳", "ו׳", "ש׳")
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
            weekDays.forEach { d ->
                Text(
                    text = d,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = if (d == "ש׳") Color(0xFFFBBF24) else Color(0xFF94A3B8),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        val totalCells = (startDayOfWeek - 1) + daysInHebrewMonth
        val weeksCount = ((totalCells + 6) / 7).coerceIn(4, 6)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.0f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            for (week in 0 until weeksCount) {
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    for (col in 0 until 7) {
                        val cellIndex = week * 7 + col
                        val hebDayNumber = cellIndex - (startDayOfWeek - 1) + 1

                        if (hebDayNumber in 1..daysInHebrewMonth) {
                            val cellCal = (firstDayCal.clone() as Calendar).apply {
                                add(Calendar.DAY_OF_MONTH, hebDayNumber - 1)
                            }
                            val cellJewish = JewishCalendar(cellCal).apply { inIsrael = true }
                            val yomTov = hebFormatter.formatYomTov(cellJewish)
                            val cellKey = dateKeyFormat.format(cellCal.time)
                            val dayReminders = loadReminders(cellKey)
                            val hasReminders = dayReminders.isNotEmpty()

                            val isToday = cellCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                                          cellCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
                            val isSelected = cellCal.get(Calendar.YEAR) == selectedCal.get(Calendar.YEAR) &&
                                             cellCal.get(Calendar.DAY_OF_YEAR) == selectedCal.get(Calendar.DAY_OF_YEAR)
                            val isShabbat = cellCal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY

                            val bgColor = when {
                                isSelected -> Color(0xFFD97706)
                                isToday -> Color(0xFF1E3A8A)
                                else -> Color(0xFF1E293B)
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(bgColor, RoundedCornerShape(6.dp))
                                    .border(
                                        width = if (isSelected || isToday) 1.5.dp else 0.dp,
                                        color = if (isSelected) Color(0xFFFBBF24) else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable { onSelectDay(cellCal.timeInMillis) }
                                    .padding(1.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = hebFormatter.formatHebrewNumber(hebDayNumber),
                                            color = if (isShabbat && !isSelected) Color(0xFFFBBF24) else Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (hasReminders) {
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text("⏰", fontSize = 7.sp)
                                        }
                                    }
                                    Text(
                                        text = "${cellCal.get(Calendar.DAY_OF_MONTH)}/${cellCal.get(Calendar.MONTH) + 1}",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 8.sp
                                    )
                                    if (yomTov.isNotEmpty() || cellJewish.isRoshChodesh) {
                                        Text(
                                            text = if (yomTov.isNotEmpty()) yomTov else "ר״ח",
                                            color = Color(0xFFFDE68A),
                                            fontSize = 7.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // פאנל יומן ותזכורות לפי שעות ליום הנבחר
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().weight(1.1f)
        ) {
            Column(modifier = Modifier.padding(8.dp).fillMaxHeight()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val gregDateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(selectedCal.time)
                    Text("תזכורות ואירועים ($gregDateStr):", color = Color(0xFFFBBF24), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onOpenFullDayZmanim, contentPadding = PaddingValues(0.dp)) {
                        Text("זמני היום ◀", color = Color(0xFF38BDF8), fontSize = 11.sp)
                    }
                }

                // שורת הוספת אירוע חדש עם שעה
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newTimeStr,
                        onValueChange = { newTimeStr = it },
                        label = { Text("שעה", fontSize = 9.sp) },
                        modifier = Modifier.width(65.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFBBF24),
                            unfocusedBorderColor = Color(0xFF475569)
                        ),
                        textStyle = TextStyle(fontSize = 11.sp)
                    )

                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        placeholder = { Text("הוסף אירוע/תזכורת...", color = Color(0xFF64748B), fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFBBF24),
                            unfocusedBorderColor = Color(0xFF475569)
                        ),
                        textStyle = TextStyle(fontSize = 11.sp)
                    )

                    Button(
                        onClick = {
                            if (newTitle.isNotBlank()) {
                                // חישוב טיימסטאפ לפי השעה שנבחרה
                                val parts = newTimeStr.split(":")
                                val hour = parts.getOrNull(0)?.toIntOrNull() ?: 9
                                val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

                                val targetCal = (selectedCal.clone() as Calendar).apply {
                                    set(Calendar.HOUR_OF_DAY, hour)
                                    set(Calendar.MINUTE, minute)
                                    set(Calendar.SECOND, 0)
                                }

                                val reminderId = System.currentTimeMillis().toString() + "_" + (0..999).random()
                                val reminder = CalendarReminder(
                                    id = reminderId,
                                    dateKey = selectedDateKey,
                                    title = newTitle.trim(),
                                    timeStr = newTimeStr,
                                    timestamp = targetCal.timeInMillis
                                )

                                val updated = remindersList + reminder
                                remindersList = updated
                                saveReminders(selectedDateKey, updated)
                                CalendarReminderReceiver.scheduleReminder(context, reminder)

                                newTitle = ""
                                Toast.makeText(context, "התזכורת נקבעה בהצלחה!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Text("הוסף", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // רשימת התזכורות שנקבעו ליום זה
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    items(remindersList) { rem ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF334155)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(text = rem.timeStr, color = Color(0xFFFBBF24), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text(text = rem.title, color = Color.White, fontSize = 11.sp)
                                }
                                TextButton(
                                    onClick = {
                                        CalendarReminderReceiver.cancelReminder(context, rem.id)
                                        val updated = remindersList.filter { it.id != rem.id }
                                        remindersList = updated
                                        saveReminders(selectedDateKey, updated)
                                    },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("מחק", color = Color(0xFFFCA5A5), fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ZmanimListTab(zmanim: List<ZmanItem>) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(zmanim) { item ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = item.subtitle,
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                    Text(
                        text = item.formattedTime,
                        color = Color(0xFFFBBF24),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationsTab(context: Context, zmanim: List<ZmanItem>) {
    val prefs = remember { context.getSharedPreferences("ZmanimPrefs", Context.MODE_PRIVATE) }
    val presetMinutes = listOf(0, 5, 10, 15, 30, 40, 60)

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(zmanim) { item ->
            var enabled by remember(item.id) {
                mutableStateOf(prefs.getBoolean("alert_${item.id}_enabled", false))
            }
            var minsBefore by remember(item.id) {
                mutableIntStateOf(prefs.getInt("alert_${item.id}_mins", 10))
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("שעת הזמן: ${item.formattedTime}", color = Color(0xFFFBBF24), fontSize = 12.sp)
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { isChecked ->
                                enabled = isChecked
                                prefs.edit().putBoolean("alert_${item.id}_enabled", isChecked).apply()
                                ZmanAlarmReceiver.scheduleAllEnabledAlarms(context)
                            }
                        )
                    }

                    if (enabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("כמה דקות לפני הזמן לשלוח התראה?", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            presetMinutes.forEach { preset ->
                                val selected = minsBefore == preset
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (selected) Color(0xFFD97706) else Color(0xFF0F172A),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            minsBefore = preset
                                            prefs.edit().putInt("alert_${item.id}_mins", preset).apply()
                                            ZmanAlarmReceiver.scheduleAllEnabledAlarms(context)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = if (preset == 0) "בזמן" else "${preset} דק׳",
                                        color = Color.White,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsAndLocationTab(
    context: Context,
    currentLocation: CityLocation,
    showSeconds: Boolean,
    onToggleSeconds: (Boolean) -> Unit,
    onSelectCity: (CityLocation) -> Unit,
    onRequestGps: () -> Unit
) {
    val prefs = remember { context.getSharedPreferences("ZmanimPrefs", Context.MODE_PRIVATE) }
    var soundType by remember { mutableStateOf(prefs.getString("sound_type", "itim_custom") ?: "itim_custom") }
    var vibrateEnabled by remember { mutableStateOf(prefs.getBoolean("vibrate_enabled", true)) }

    val soundOptions = listOf(
        "itim_custom" to "🎶 צליל מיוחד: פעימת עיתים לבינה",
        "notification" to "🔔 צליל התראה רגיל במערכת",
        "alarm" to "⏰ צליל שעון מעורר",
        "ringtone" to "📞 צלצול שיחה",
        "silent" to "שקט (ללא צליל)"
    )

    val currentMediaPlayer = remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            currentMediaPlayer.value?.release()
            currentMediaPlayer.value = null
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("🔊 הגדרות צלילי התראות ותצוגה", color = Color(0xFFFBBF24), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))

                    Text("בחר צליל התראה (לחיצה תשמע את הצליל מיד):", color = Color(0xFFCBD5E1), fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))

                    soundOptions.forEach { (key, label) ->
                        val isSelected = soundType == key
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(
                                    if (isSelected) Color(0xFF1E3A8A) else Color(0xFF0F172A),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    soundType = key
                                    prefs.edit().putString("sound_type", key).apply()

                                    try {
                                        currentMediaPlayer.value?.stop()
                                        currentMediaPlayer.value?.release()
                                        currentMediaPlayer.value = null
                                    } catch (_: Exception) {}

                                    if (key != "silent") {
                                        val uri = ZmanAlarmReceiver.getSelectedSoundUri(context, key)
                                        if (uri != null) {
                                            try {
                                                val mp = MediaPlayer.create(context, uri)
                                                mp?.start()
                                                currentMediaPlayer.value = mp
                                            } catch (_: Exception) {}
                                        }
                                    }
                                }
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, color = Color.White, fontSize = 14.sp)
                            Text(if (isSelected) "✅ נבחר" else "השמע", color = Color(0xFFFBBF24), fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("רטט בעת קבלת התראה", color = Color.White, fontSize = 14.sp)
                        Switch(
                            checked = vibrateEnabled,
                            onCheckedChange = {
                                vibrateEnabled = it
                                prefs.edit().putBoolean("vibrate_enabled", it).apply()
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("הצג שניות בזמני היום (HH:mm:ss)", color = Color.White, fontSize = 14.sp)
                        Switch(
                            checked = showSeconds,
                            onCheckedChange = onToggleSeconds
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            ZmanAlarmReceiver.triggerNotification(
                                context = context,
                                title = "בדיקת התראה וצליל",
                                timeStr = "12:00",
                                minutesBefore = 10
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("🔔 שלח התראת בדיקה עם הצליל שנבחר", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Button(
                onClick = onRequestGps,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("📡 איתור מיקום אוטומטי באמצעות GPS (כולל גובה)", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }

        item {
            Text("או בחר עיר ידנית מרשימת ערי ישראל:", color = Color(0xFFCBD5E1), fontSize = 14.sp)
        }

        items(ZmanimHelper.cities) { city ->
            val isSelected = city.name == currentLocation.name
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) Color(0xFF1E3A8A) else Color(0xFF1E293B)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectCity(city) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(city.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "גובה: ${city.elevation.toInt()} מ׳",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
fun fetchGpsLocation(context: Context, onResult: (CityLocation) -> Unit) {
    val client = LocationServices.getFusedLocationProviderClient(context)
    client.lastLocation.addOnSuccessListener { location ->
        if (location != null) {
            val elev = if (location.hasAltitude() && location.altitude > 0) location.altitude else 65.0
            onResult(
                CityLocation(
                    name = "מיקום GPS נוכחי",
                    lat = location.latitude,
                    lng = location.longitude,
                    elevation = elev
                )
            )
            Toast.makeText(context, "המיקום עודכן לפי ה-GPS", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "לא ניתן היה לקבל מיקום GPS כעת, ודא שהמיקום פועל", Toast.LENGTH_LONG).show()
        }
    }
}
