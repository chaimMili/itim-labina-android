package com.itimlabina.zmanim

import android.Manifest
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

private val LEAD_PRESETS = listOf(0, 5, 10, 15, 30, 60, 120, 1440)

/**
 * עורך תזכורת במסך מלא, בסגנון Google Calendar:
 * כותרת, תאריך, שעה, מתי להתריע, צבע, צליל (כולל קובץ מהמכשיר) ותיאור.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReminderEditorDialog(
    initial: CalendarReminder?,
    initialDateMillis: Long,
    onDismiss: () -> Unit,
    onSave: (CalendarReminder) -> Unit,
    onDelete: (CalendarReminder) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf(initial?.title ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var dateTimeMillis by remember { mutableLongStateOf(initial?.timestamp ?: defaultStartMillis(initialDateMillis)) }
    var minutesBefore by remember { mutableIntStateOf(initial?.minutesBefore ?: 10) }
    var color by remember { mutableIntStateOf(initial?.color ?: ReminderColors.DEFAULT) }
    var soundMode by remember { mutableStateOf(initial?.soundMode ?: "default") }
    var soundUri by remember { mutableStateOf(initial?.soundUri) }
    var soundName by remember { mutableStateOf(initial?.soundName) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showLeadList by remember { mutableStateOf(false) }
    var showCustomLead by remember { mutableStateOf(false) }

    val cal = remember(dateTimeMillis) { calendarOf(dateTimeMillis) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (initial == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    DisposableEffect(Unit) {
        onDispose { SoundPreview.stop() }
    }

    val pickSoundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) { SoundStore.importSound(context, uri) }
                if (result != null) {
                    soundUri = result.first
                    soundName = result.second
                    soundMode = "custom"
                    SoundPreview.play(context, Uri.parse(result.first))
                } else {
                    Toast.makeText(context, "לא ניתן להשתמש בקובץ הזה (עד 20MB, קובץ שמע בלבד)", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun save() {
        val c = calendarOf(dateTimeMillis)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        val isCustom = soundMode == "custom" && soundUri != null
        onSave(
            CalendarReminder(
                id = initial?.id ?: ReminderStore.newId(),
                dateKey = ReminderStore.dateKey(c.timeInMillis),
                title = title.trim().ifEmpty { "תזכורת" },
                notes = notes.trim(),
                timeStr = formatClock(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)),
                timestamp = c.timeInMillis,
                color = color,
                minutesBefore = minutesBefore,
                soundMode = if (isCustom) "custom" else if (soundMode == "silent") "silent" else "default",
                soundUri = if (isCustom) soundUri else null,
                soundName = if (isCustom) soundName else null
            )
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "סגירה")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(onClick = { save() }) { Text("שמירה", maxLines = 1) }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 32.dp)
                ) {
                    TextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = {
                            Text(
                                "הוספת כותרת",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        textStyle = MaterialTheme.typography.headlineSmall,
                        maxLines = 3,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                            unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    EditorRow(
                        onClick = { showDatePicker = true },
                        icon = { Icon(Icons.Default.DateRange, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    ) {
                        Text(
                            gregorianLongDate(dateTimeMillis),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            hebrewDateString(dateTimeMillis),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    EditorRow(
                        onClick = { showTimePicker = true },
                        icon = { ClockIcon(tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    ) {
                        Text(
                            formatClock(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    EditorRow(
                        onClick = { showLeadList = true },
                        icon = { Icon(Icons.Default.Notifications, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    ) {
                        Text(
                            formatLeadBefore(minutesBefore),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "מתי לקבל את ההתראה",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }

                    EditorRow(
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(Color(color))
                            )
                        }
                    ) {
                        Text("צבע", style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        Spacer(modifier = Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ReminderColors.palette.forEach { c ->
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color(c))
                                        .clickable { color = c },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (color == c) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "נבחר",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    EditorRow(
                        icon = {
                            Text("♪", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    ) {
                        Text("צליל התראה", style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        Spacer(modifier = Modifier.height(4.dp))
                        SoundOptionRow(
                            label = "ברירת מחדל של המערכת",
                            selected = soundMode == "default",
                            onClick = {
                                soundMode = "default"
                                SoundPreview.play(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                            }
                        )
                        SoundOptionRow(
                            label = "שקט",
                            selected = soundMode == "silent",
                            onClick = {
                                soundMode = "silent"
                                SoundPreview.stop()
                            }
                        )
                        SoundOptionRow(
                            label = if (soundName != null) "קובץ: $soundName" else "קובץ מהמכשיר…",
                            selected = soundMode == "custom",
                            onClick = {
                                val saved = soundUri
                                if (saved != null) {
                                    soundMode = "custom"
                                    SoundPreview.play(context, Uri.parse(saved))
                                } else {
                                    pickSoundLauncher.launch(arrayOf("audio/*"))
                                }
                            },
                            trailing = {
                                if (soundUri != null) {
                                    TextButton(onClick = { pickSoundLauncher.launch(arrayOf("audio/*")) }) {
                                        Text("החלפה", maxLines = 1)
                                    }
                                }
                            }
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    EditorRow(
                        icon = { Icon(Icons.Default.Menu, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    ) {
                        TextField(
                            value = notes,
                            onValueChange = { notes = it },
                            placeholder = { Text("הוספת תיאור", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            minLines = 2,
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (initial != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = { onDelete(initial) },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("מחיקת התזכורת", color = MaterialTheme.colorScheme.error, maxLines = 1)
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = localMillisToPickerUtc(dateTimeMillis))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let {
                        dateTimeMillis = pickerUtcToLocalMillis(it, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
                    }
                    showDatePicker = false
                }) { Text("אישור") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("ביטול") } }
        ) {
            DatePicker(state = dateState)
        }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = true
        )
        Dialog(
            onDismissRequest = { showTimePicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .width(IntrinsicSize.Min)
                    .height(IntrinsicSize.Min)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "בחירת שעה",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    )
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        TimePicker(state = timeState)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showTimePicker = false }) { Text("ביטול") }
                        TextButton(onClick = {
                            dateTimeMillis = withTime(dateTimeMillis, timeState.hour, timeState.minute)
                            showTimePicker = false
                        }) { Text("אישור") }
                    }
                }
            }
        }
    }

    if (showLeadList) {
        AlertDialog(
            onDismissRequest = { showLeadList = false },
            title = { Text("מתי להתריע") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    val options = if (minutesBefore in LEAD_PRESETS) LEAD_PRESETS else listOf(minutesBefore) + LEAD_PRESETS
                    options.forEach { m ->
                        SoundOptionRow(
                            label = formatLeadBefore(m),
                            selected = minutesBefore == m,
                            onClick = {
                                minutesBefore = m
                                showLeadList = false
                            }
                        )
                    }
                    TextButton(onClick = {
                        showLeadList = false
                        showCustomLead = true
                    }) { Text("מותאם אישית…") }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLeadList = false }) { Text("סגירה") }
            }
        )
    }

    if (showCustomLead) {
        CustomLeadDialog(
            initialMinutes = minutesBefore,
            onDismiss = { showCustomLead = false },
            onConfirm = {
                minutesBefore = it
                showCustomLead = false
            }
        )
    }
}

@Composable
private fun EditorRow(
    onClick: (() -> Unit)? = null,
    icon: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val base = if (onClick != null) Modifier.fillMaxWidth().clickable(onClick = onClick) else Modifier.fillMaxWidth()
    Row(
        modifier = base.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(44.dp), contentAlignment = Alignment.CenterStart) { icon() }
        Column(modifier = Modifier.weight(1f), content = content)
    }
}

@Composable
fun SoundOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(end = 12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 10.dp)
        )
        trailing()
    }
}

/** בחירת זמן התראה מותאם אישית: מספר + יחידה (דקות / שעות / ימים). */
@Composable
fun CustomLeadDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val initialUnit = when {
        initialMinutes > 0 && initialMinutes % 1440 == 0 -> 1440
        initialMinutes > 0 && initialMinutes % 60 == 0 -> 60
        else -> 1
    }
    var unit by remember { mutableIntStateOf(initialUnit) }
    var amountText by remember { mutableStateOf(if (initialMinutes > 0) (initialMinutes / initialUnit).toString() else "") }

    val amount = amountText.toIntOrNull()
    val total = if (amount != null) amount * unit else null
    val valid = total != null && total in 0..MAX_LEAD_MINUTES

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("זמן התראה מותאם אישית") },
        text = {
            Column {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { s -> amountText = s.filter { it.isDigit() }.take(5) },
                    label = { Text("כמה זמן לפני") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = amountText.isNotEmpty() && !valid,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to "דקות", 60 to "שעות", 1440 to "ימים").forEach { (value, label) ->
                        FilterChip(
                            selected = unit == value,
                            onClick = { unit = value },
                            label = { Text(label, maxLines = 1) }
                        )
                    }
                }
                if (amountText.isNotEmpty() && !valid) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "אפשר להגדיר עד 30 ימים לפני",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { if (total != null) onConfirm(total) }
            ) { Text("אישור") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } }
    )
}

const val MAX_LEAD_MINUTES = 30 * 24 * 60

/** שורת צ'יפים לבחירת "כמה דקות לפני", כולל אפשרות "אחר…" לזמן מותאם אישית. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LeadTimeChips(
    selected: Int,
    onSelect: (Int) -> Unit,
    presets: List<Int> = listOf(0, 5, 10, 15, 30, 40, 60)
) {
    var showCustom by remember { mutableStateOf(false) }
    val isCustom = selected !in presets

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        presets.forEach { m ->
            FilterChip(
                selected = selected == m,
                onClick = { onSelect(m) },
                label = { Text(if (m == 0) "בזמן" else formatDuration(m), maxLines = 1) }
            )
        }
        FilterChip(
            selected = isCustom,
            onClick = { showCustom = true },
            label = { Text(if (isCustom) formatDuration(selected) else "אחר…", maxLines = 1) }
        )
    }

    if (showCustom) {
        CustomLeadDialog(
            initialMinutes = selected,
            onDismiss = { showCustom = false },
            onConfirm = {
                onSelect(it)
                showCustom = false
            }
        )
    }
}
