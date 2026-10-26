package com.itimlabina.zmanim

import android.content.Context
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

data class CityLocation(
    val name: String,
    val lat: Double,
    val lng: Double,
    val elevation: Double
)

data class ZmanItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val time: Date?,
    val formattedTime: String
)

object ZmanimHelper {
    val cities = listOf(
        CityLocation("קרית מלאכי", 31.7294, 34.7462, 65.0),
        CityLocation("ירושלים", 31.7683, 35.2137, 795.0),
        CityLocation("בני ברק", 32.0849, 34.8352, 50.0),
        CityLocation("מודיעין עילית", 31.9333, 35.0417, 300.0),
        CityLocation("בית שמש", 31.7470, 34.9881, 250.0),
        CityLocation("אשדוד", 31.8044, 34.6553, 25.0),
        CityLocation("צפת", 32.9646, 35.4960, 845.0),
        CityLocation("תל אביב", 32.0853, 34.7818, 15.0),
        CityLocation("חיפה", 32.7940, 34.9896, 290.0),
        CityLocation("באר שבע", 31.2518, 34.7913, 280.0)
    )

    fun getSavedLocation(context: Context): CityLocation {
        val prefs = context.getSharedPreferences("ZmanimPrefs", Context.MODE_PRIVATE)
        val name = prefs.getString("city_name", "קרית מלאכי") ?: "קרית מלאכי"
        val lat = prefs.getFloat("lat", 31.7294f).toDouble()
        val lng = prefs.getFloat("lng", 34.7462f).toDouble()
        val elev = prefs.getFloat("elevation", 65.0f).toDouble()
        return CityLocation(name, lat, lng, elev)
    }

    fun saveLocation(context: Context, loc: CityLocation) {
        val prefs = context.getSharedPreferences("ZmanimPrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("city_name", loc.name)
            .putFloat("lat", loc.lat.toFloat())
            .putFloat("lng", loc.lng.toFloat())
            .putFloat("elevation", loc.elevation.toFloat())
            .apply()
    }

    fun calculateDailyZmanim(loc: CityLocation, calendar: Calendar): List<ZmanItem> {
        val tz = TimeZone.getTimeZone("Asia/Jerusalem")
        val geo = GeoLocation(loc.name, loc.lat, loc.lng, loc.elevation, tz)
        val zc = ComplexZmanimCalendar(geo).apply {
            astronomicalCalculator = NOAACalculator()
            this.calendar = calendar
        }
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault()).apply { timeZone = tz }
        fun f(d: Date?): String = if (d != null) fmt.format(d) else "--:--"

        return listOf(
            ZmanItem("alot", "עלות השחר (עיתים לבינה)", "16.1° מעלות מתחת לאופק", zc.alos16Point1Degrees, f(zc.alos16Point1Degrees)),
            ZmanItem("misheyakir", "זמן טלית ותפילין", "משיכיר - 11.5° מעלות", zc.misheyakir11Point5Degrees, f(zc.misheyakir11Point5Degrees)),
            ZmanItem("netz_elev", "הנץ החמה (לפי גובה העיר)", "בהתחשב בגובה ${loc.elevation.toInt()} מ׳", zc.sunrise, f(zc.sunrise)),
            ZmanItem("netz_sea", "הנץ החמה במישור", "גובה פני הים (0 מ׳)", zc.seaLevelSunrise, f(zc.seaLevelSunrise)),
            ZmanItem("szks_mga", "סוף זמן קריאת שמע (מג״א)", "מעלות השחר (16.1°) עד צאת הכוכבים", zc.sofZmanShmaMGA16Point1Degrees, f(zc.sofZmanShmaMGA16Point1Degrees)),
            ZmanItem("szks_gra", "סוף זמן קריאת שמע (גר״א)", "מהנץ החמה עד השקיעה", zc.sofZmanShmaGRA, f(zc.sofZmanShmaGRA)),
            ZmanItem("szt_mga", "סוף זמן תפילה (מג״א)", "4 שעות זמניות למג״א", zc.sofZmanTfilaMGA16Point1Degrees, f(zc.sofZmanTfilaMGA16Point1Degrees)),
            ZmanItem("szt_gra", "סוף זמן תפילה (גר״א)", "4 שעות זמניות לגר״א", zc.sofZmanTfilaGRA, f(zc.sofZmanTfilaGRA)),
            ZmanItem("chatzot", "חצות היום והלילה", "אמצע היום ההלכתי", zc.chatzos, f(zc.chatzos)),
            ZmanItem("mincha_gedola", "מנחה גדולה", "חצי שעה זמנית אחר חצות", zc.minchaGedola, f(zc.minchaGedola)),
            ZmanItem("mincha_ketana", "מנחה קטנה", "9.5 שעות זמניות", zc.minchaKetana, f(zc.minchaKetana)),
            ZmanItem("plag", "פלג המנחה (גר״א)", "שעה ורבע זמנית לפני השקיעה", zc.plagHamincha, f(zc.plagHamincha)),
            ZmanItem("shkia", "שקיעת החמה", "לפי גובה טופוגרפי", zc.sunset, f(zc.sunset)),
            ZmanItem("tzeit_geonim", "צאת הכוכבים (הגאונים)", "4.61° מעלות מתחת לאופק", zc.tzaisGeonim4Point61Degrees, f(zc.tzaisGeonim4Point61Degrees)),
            ZmanItem("tzeit_itim", "צאת הכוכבים לחומרא (עיתים לבינה)", "6.45° מעלות / 24 דק׳ זמניות", zc.tzaisGeonim6Point45Degrees, f(zc.tzaisGeonim6Point45Degrees)),
            ZmanItem("tzeit_rt", "צאת הכוכבים (רבינו תם)", "72 דקות זמניות אחר השקיעה", zc.tzais72Zmanis, f(zc.tzais72Zmanis))
        )
    }

    fun getHebrewDateTitle(calendar: Calendar): Pair<String, String> {
        val jCal = JewishCalendar(calendar).apply { inIsrael = true }
        val hf = HebrewDateFormatter().apply { isHebrewFormat = true }
        val dateStr = hf.format(jCal)
        val parasha = hf.formatYomTov(jCal).ifEmpty { hf.formatParsha(jCal) }
        return Pair(dateStr, parasha)
    }
}
