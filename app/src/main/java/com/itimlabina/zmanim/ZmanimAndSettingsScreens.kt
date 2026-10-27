package com.itimlabina.zmanim

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

// ───────────────────────── זמני היום ─────────────────────────

@Composable
fun ZmanimScreen(
    zmanim: List<ZmanItem>,
    selectedMillis: Long,
    isToday: Boolean,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit
) {
    val nowMs = System.currentTimeMillis()
    val nextId = if (isToday) {
        zmanim.filter { (it.time?.time ?: 0L) > nowMs }
            .minByOrNull { it.time?.time ?: Long.MAX_VALUE }?.id
    } else null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevDay) {
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "יום קודם")
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    gregorianLongDate(selectedMillis),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!isToday) {
                    TextButton(onClick = onToday) { Text("חזרה להיום", maxLines = 1) }
                }
            }
            IconButton(onClick = onNextDay) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "יום הבא")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        SurfaceCard {
            zmanim.forEachIndexed { index, item ->
                val highlighted = item.id == nextId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            item.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (highlighted) {
                            Text(
                                "הזמן הבא",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        }
                    }
                    Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                    Text(
                        item.formattedTime,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                if (index < zmanim.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }
}

// ───────────────────────── התראות זמני היום ─────────────────────────

@Composable
fun AlertsScreen(context: Context, zmanim: List<ZmanItem>) {
    val prefs = remember { context.getSharedPreferences("ZmanimPrefs", Context.MODE_PRIVATE) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(zmanim) { item ->
            var enabled by remember(item.id) { mutableStateOf(prefs.getBoolean("alert_${item.id}_enabled", false)) }
            var minsBefore by remember(item.id) { mutableIntStateOf(prefs.getInt("alert_${item.id}_mins", 10)) }

            SurfaceCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "שעת הזמן: ${item.formattedTime}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        }
                        Spacer(modifier = Modifier.padding(horizontal = 6.dp))
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
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "מתי להתריע לפני הזמן?",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LeadTimeChips(
                            selected = minsBefore,
                            onSelect = { m ->
                                minsBefore = m
                                prefs.edit().putInt("alert_${item.id}_mins", m).apply()
                                ZmanAlarmReceiver.scheduleAllEnabledAlarms(context)
                            }
                        )
                    }
                }
            }
        }
    }
}

// ───────────────────────── הגדרות ─────────────────────────

@Composable
fun SettingsScreen(
    context: Context,
    currentLocation: CityLocation,
    showSeconds: Boolean,
    onToggleSeconds: (Boolean) -> Unit,
    onSelectCity: (CityLocation) -> Unit,
    onRequestGps: () -> Unit
) {
    val prefs = remember { context.getSharedPreferences("ZmanimPrefs", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var soundType by remember { mutableStateOf(prefs.getString("sound_type", "itim_custom") ?: "itim_custom") }
    var vibrateEnabled by remember { mutableStateOf(prefs.getBoolean("vibrate_enabled", true)) }
    var customName by remember { mutableStateOf(prefs.getString("custom_sound_name", null)) }

    DisposableEffect(Unit) {
        onDispose { SoundPreview.stop() }
    }

    fun selectSound(key: String) {
        soundType = key
        prefs.edit().putString("sound_type", key).apply()
        SoundPreview.stop()
        if (key != "silent") {
            SoundPreview.play(context, ZmanAlarmReceiver.getSelectedSoundUri(context, key))
        }
    }

    val pickSoundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) { SoundStore.importSound(context, uri) }
                if (result != null) {
                    prefs.edit()
                        .putString("custom_sound_uri", result.first)
                        .putString("custom_sound_name", result.second)
                        .apply()
                    customName = result.second
                    selectSound("custom_file")
                } else {
                    Toast.makeText(context, "לא ניתן להשתמש בקובץ הזה (עד 20MB, קובץ שמע בלבד)", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val soundOptions = listOf(
        "itim_custom" to "צליל התראה של עיתים לבינה",
        "notification" to "צליל התראה רגיל של המערכת",
        "alarm" to "צליל שעון מעורר",
        "ringtone" to "צלצול שיחה",
        "silent" to "שקט (ללא צליל)"
    )

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SurfaceCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionTitle("צליל התראות זמני היום")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "לחיצה על צליל משמיעה אותו מיד",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    soundOptions.forEach { (key, label) ->
                        SoundOptionRow(label = label, selected = soundType == key, onClick = { selectSound(key) })
                    }
                    SoundOptionRow(
                        label = if (customName != null) "קובץ: $customName" else "קובץ מהמכשיר…",
                        selected = soundType == "custom_file",
                        onClick = {
                            if (customName != null) selectSound("custom_file")
                            else pickSoundLauncher.launch(arrayOf("audio/*"))
                        },
                        trailing = {
                            if (customName != null) {
                                TextButton(onClick = { pickSoundLauncher.launch(arrayOf("audio/*")) }) {
                                    Text("החלפה", maxLines = 1)
                                }
                            }
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    SettingSwitchRow("רטט בעת התראה", vibrateEnabled) {
                        vibrateEnabled = it
                        prefs.edit().putBoolean("vibrate_enabled", it).apply()
                    }
                    SettingSwitchRow("הצגת שניות בזמני היום", showSeconds, onToggleSeconds)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            ZmanAlarmReceiver.triggerNotification(
                                context = context,
                                title = "בדיקת התראה וצליל",
                                timeStr = "12:00",
                                minutesBefore = 10
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("שליחת התראת בדיקה", maxLines = 1) }
                }
            }
        }

        item {
            SurfaceCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionTitle("מיקום")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "זמני היום מחושבים לפי המיקום והגובה",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onRequestGps, modifier = Modifier.fillMaxWidth()) {
                        Text("איתור אוטומטי לפי GPS", maxLines = 1)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "או בחירת עיר ידנית",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ZmanimHelper.cities.forEach { city ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectCity(city) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = city.name == currentLocation.name, onClick = null, modifier = Modifier.padding(end = 12.dp))
                            Text(
                                city.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${city.elevation.toInt()} מ׳",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    if (currentLocation.name !in ZmanimHelper.cities.map { it.name }) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "מיקום נוכחי: ${currentLocation.name} (גובה ${currentLocation.elevation.toInt()} מ׳)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.padding(horizontal = 6.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
