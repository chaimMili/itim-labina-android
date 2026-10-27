package com.itimlabina.zmanim

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import java.util.Calendar

class MainActivity : ComponentActivity() {
    private var openTab by mutableIntStateOf(0)
    private var openDay by mutableLongStateOf(0L)
    private var intentVersion by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeIntent(intent)
        addOnNewIntentListener { newIntent -> consumeIntent(newIntent) }
        setContent {
            ItimTheme {
                ItimLaBinaMainScreen(
                    openTab = openTab,
                    openDay = openDay,
                    intentVersion = intentVersion
                )
            }
        }
    }

    /** פתיחה מהווידג'ט או מהתראה: עוברים ללשונית שנבחרה וליום שנבחר. */
    private fun consumeIntent(i: Intent?) {
        if (i != null && i.hasExtra(IntentKeys.OPEN_TAB)) {
            openTab = i.getIntExtra(IntentKeys.OPEN_TAB, 0)
            openDay = i.getLongExtra(IntentKeys.DAY_MILLIS, 0L)
            intentVersion += 1
        }
    }
}

private data class EditorRequest(val reminder: CalendarReminder?, val dateMillis: Long)

@Composable
fun ItimLaBinaMainScreen(openTab: Int, openDay: Long, intentVersion: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("ZmanimPrefs", Context.MODE_PRIVATE) }

    var currentTab by remember { mutableIntStateOf(openTab) }
    var currentLocation by remember { mutableStateOf(ZmanimHelper.getSavedLocation(context)) }
    var showSeconds by remember { mutableStateOf(prefs.getBoolean("show_seconds", false)) }
    var selectedTimeMillis by remember { mutableLongStateOf(if (openDay != 0L) openDay else System.currentTimeMillis()) }
    var remindersVersion by remember { mutableIntStateOf(0) }
    var editorRequest by remember { mutableStateOf<EditorRequest?>(null) }

    LaunchedEffect(intentVersion) {
        if (intentVersion > 0) {
            currentTab = openTab
            if (openDay != 0L) selectedTimeMillis = openDay
        }
    }

    val selectedCal = remember(selectedTimeMillis) { calendarOf(selectedTimeMillis) }
    val zmanimList = remember(currentLocation, selectedTimeMillis, showSeconds) {
        ZmanimHelper.calculateDailyZmanim(currentLocation, selectedCal, showSeconds)
    }
    val (hebrewDateStr, parashaStr) = remember(selectedTimeMillis) {
        ZmanimHelper.getHebrewDateTitle(selectedCal)
    }
    val isToday = isSameDay(selectedCal, Calendar.getInstance(JERUSALEM))

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
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (currentTab == 1) {
                ExtendedFloatingActionButton(
                    onClick = { editorRequest = EditorRequest(null, selectedTimeMillis) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("תזכורת", maxLines = 1) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    label = { Text("זמני היום", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    icon = { SunIcon() }
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    label = { Text("לוח שנה", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    icon = { Icon(Icons.Default.DateRange, contentDescription = null) }
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = {
                        currentTab = 2
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    label = { Text("התראות", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    icon = { Icon(Icons.Default.Notifications, contentDescription = null) }
                )
                NavigationBarItem(
                    selected = currentTab == 3,
                    onClick = { currentTab = 3 },
                    label = { Text("הגדרות", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
        ) {
            ScreenHeader(
                title = when (currentTab) {
                    2 -> "התראות"
                    3 -> "הגדרות"
                    else -> hebrewDateStr
                },
                subtitle = when (currentTab) {
                    2 -> "קבלו התראה לפני כל זמן הלכתי"
                    3 -> "מיקום, צלילים ותצוגה"
                    else -> listOf(
                        parashaStr,
                        gregorianShortDate(selectedTimeMillis),
                        currentLocation.name
                    ).filter { it.isNotBlank() }.joinToString(" • ")
                }
            )

            when (currentTab) {
                0 -> ZmanimScreen(
                    zmanim = zmanimList,
                    selectedMillis = selectedTimeMillis,
                    isToday = isToday,
                    onPrevDay = { selectedTimeMillis -= 86_400_000L },
                    onNextDay = { selectedTimeMillis += 86_400_000L },
                    onToday = { selectedTimeMillis = System.currentTimeMillis() }
                )
                1 -> CalendarScreen(
                    selectedCal = selectedCal,
                    remindersVersion = remindersVersion,
                    onSelectDay = { selectedTimeMillis = it },
                    onOpenZmanim = { currentTab = 0 },
                    onEditReminder = { editorRequest = EditorRequest(it, it.timestamp) }
                )
                2 -> AlertsScreen(context, zmanimList)
                3 -> SettingsScreen(
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

    editorRequest?.let { req ->
        ReminderEditorDialog(
            initial = req.reminder,
            initialDateMillis = req.dateMillis,
            onDismiss = { editorRequest = null },
            onSave = { r ->
                ReminderStore.upsert(context, r, req.reminder?.dateKey)
                ReminderScheduler.schedule(context, r)
                selectedTimeMillis = r.timestamp
                remindersVersion += 1
                editorRequest = null
                scope.launch { BaseItimWidget.updateAllWidgets(context) }
                Toast.makeText(context, "התזכורת נשמרה", Toast.LENGTH_SHORT).show()
            },
            onDelete = { r ->
                ReminderScheduler.cancel(context, r.id)
                ReminderStore.delete(context, r)
                remindersVersion += 1
                editorRequest = null
                scope.launch { BaseItimWidget.updateAllWidgets(context) }
            }
        )
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
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
