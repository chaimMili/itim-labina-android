package com.itimlabina.zmanim

import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File

/** מעתיק קובץ שמע שנבחר לאחסון פנימי של האפליקציה, כדי שהמערכת תוכל לנגן אותו בהתראה. */
object SoundStore {
    private const val MAX_BYTES = 20L * 1024 * 1024

    fun importSound(context: Context, source: Uri): Pair<String, String>? {
        return try {
            val resolver = context.contentResolver
            var name = "צליל מותאם"
            resolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) c.getString(idx)?.let { name = it }
                }
            }
            val ext = name.substringAfterLast('.', "mp3").filter { it.isLetterOrDigit() }.ifEmpty { "mp3" }
            val dir = File(context.filesDir, "sounds").apply { mkdirs() }
            val dest = File(dir, "snd_${System.currentTimeMillis()}.$ext")
            val input = resolver.openInputStream(source) ?: return null
            input.use { ins -> dest.outputStream().use { out -> ins.copyTo(out) } }
            if (dest.length() > MAX_BYTES || dest.length() == 0L) {
                dest.delete()
                return null
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dest)
            Pair(uri.toString(), name)
        } catch (e: Exception) {
            null
        }
    }

    /** חלק ממכשירים דורשים הרשאת קריאה מפורשת ל-SystemUI כדי לנגן צליל התראה מקובץ של האפליקציה. */
    fun grantToSystem(context: Context, uri: Uri) {
        try {
            context.grantUriPermission("com.android.systemui", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
    }
}

object SoundPreview {
    private var current: Ringtone? = null

    fun play(context: Context, uri: Uri?) {
        stop()
        if (uri == null) return
        try {
            val r = RingtoneManager.getRingtone(context.applicationContext, uri)
            current = r
            r?.play()
        } catch (_: Exception) {
        }
    }

    fun stop() {
        try {
            current?.stop()
        } catch (_: Exception) {
        }
        current = null
    }
}
