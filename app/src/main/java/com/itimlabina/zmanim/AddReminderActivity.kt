package com.itimlabina.zmanim

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/** נפתח מהווידג'ט (כפתור +) ומציג את אותו עורך תזכורת כמו באפליקציה. */
class AddReminderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dayMillis = intent.getLongExtra(IntentKeys.DAY_MILLIS, System.currentTimeMillis())
        setContent {
            ItimTheme {
                ReminderEditorDialog(
                    initial = null,
                    initialDateMillis = dayMillis,
                    onDismiss = { finish() },
                    onSave = { r ->
                        ReminderStore.upsert(this@AddReminderActivity, r)
                        ReminderScheduler.schedule(this@AddReminderActivity, r)
                        lifecycleScope.launch { BaseItimWidget.updateAllWidgets(applicationContext) }
                        Toast.makeText(this@AddReminderActivity, "התזכורת נשמרה", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                )
            }
        }
    }
}
