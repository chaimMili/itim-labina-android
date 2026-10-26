package com.itimlabina.zmanim

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import java.util.Calendar

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

@Composable
fun ItimLaBinaMainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentTab by remember { mutableIntStateOf(0) }
    var currentLocation by remember { mutableStateOf(ZmanimHelper.getSavedLocation(context)) }
    var dayOffset by remember { mutableIntStateOf(0) }

    val selectedCal = remember(dayOffset) {
        Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, dayOffset) }
    }
    val zmanimList = remember(currentLocation, dayOffset) {
        ZmanimHelper.calculateDailyZmanim(currentLocation, selectedCal)
    }
    val (hebrewDateStr, parashaStr) = remember(dayOffset) {
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
                scope.launch { HebrewCalendarGlanceWidget().updateAll(context) }
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
                    onClick = {
                        currentTab = 1
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
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    label = { Text("מיקום ו-GPS") },
                    icon = { Text("📍", fontSize = 18.sp) }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Header Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = hebrewDateStr,
                                color = Color(0xFFFBBF24),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (parashaStr.isNotEmpty()) "$parashaStr • ${currentLocation.name}" else currentLocation.name,
                                color = Color(0xFFCBD5E1),
                                fontSize = 14.sp
                            )
                        }
                        Text(
                            text = "גובה ${currentLocation.elevation.toInt()} מ׳",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(onClick = { dayOffset -= 1 }) { Text("▶ יום קודם") }
                        Button(
                            onClick = { dayOffset = 0 },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                        ) { Text("היום") }
                        OutlinedButton(onClick = { dayOffset += 1 }) { Text("יום הבא ◀") }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (currentTab) {
                0 -> ZmanimListTab(zmanimList)
                1 -> NotificationsTab(context, zmanimList)
                2 -> LocationTab(
                    currentLocation = currentLocation,
                    onSelectCity = { loc ->
                        currentLocation = loc
                        ZmanimHelper.saveLocation(context, loc)
                        ZmanAlarmReceiver.scheduleAllEnabledAlarms(context)
                        scope.launch { HebrewCalendarGlanceWidget().updateAll(context) }
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
fun LocationTab(
    currentLocation: CityLocation,
    onSelectCity: (CityLocation) -> Unit,
    onRequestGps: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Button(
            onClick = onRequestGps,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("📡 איתור מיקום אוטומטי באמצעות GPS (כולל גובה)", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text("או בחר עיר ידנית מרשימת ערי ישראל:", color = Color(0xFFCBD5E1), fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            "גובה: ${city.elevation.toInt()} מ׳ (${city.lat}, ${city.lng})",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
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
