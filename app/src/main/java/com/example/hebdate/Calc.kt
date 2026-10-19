package com.example.hebdate

import com.kosherjava.zmanim.ComplexZmanimCalendar
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.hebrewcalendar.YomiCalculator
import com.kosherjava.zmanim.util.GeoLocation
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class Info(
    val iconDay: String,
    val iconMonth: String,
    val title: String,
    val parsha: String,
    val chag: String,
    val daf: String,
    val candle: String,
    val nextZman: String,
    val nextChange: Long
)

object Calc {
    private fun zc(geo: GeoLocation, cal: Calendar, candle: Int): ComplexZmanimCalendar {
        val z = ComplexZmanimCalendar(geo)
        z.setCalendar(cal.clone() as Calendar)
        z.setCandleLightingOffset(candle.toDouble())
        return z
    }

    fun compute(p: Prefs, now: Date = Date()): Info {
        val he = p.hebrew
        val tz = if (p.useDeviceTz) TimeZone.getDefault() else TimeZone.getTimeZone(p.tzId)
        val geo = GeoLocation(p.name, p.lat, p.lon, p.elevation.coerceIn(0.0, 9000.0), tz)

        val civil = Calendar.getInstance(tz)
        civil.time = now
        val zToday = zc(geo, civil, p.candleMin)

        // When does the Hebrew date flip?
        val flipTime: Date? = when (p.flip) {
            0 -> zToday.getSeaLevelSunset()
            1 -> zToday.getTzaisGeonim8Point5Degrees()
            else -> null
        }
        val flipped = flipTime != null && now.after(flipTime)
        val dateCal = civil.clone() as Calendar
        if (flipped) dateCal.add(Calendar.DATE, 1)

        val jc = JewishCalendar(dateCal)
        jc.setInIsrael(p.israel)

        val hdfHe = HebrewDateFormatter()
        hdfHe.setHebrewFormat(true)
        val hdf = HebrewDateFormatter()
        hdf.setHebrewFormat(he)

        // Date strings
        val dayNum = jc.getJewishDayOfMonth()
        val dayFull = if (he) hdfHe.formatHebrewNumber(dayNum) else dayNum.toString()
        val iconDay = dayFull.replace("\u05F4", "").replace("\u05F3", "").replace("\"", "").replace("'", "")
        val month = hdf.formatMonth(jc)
        val yr = jc.getJewishYear()
        val yearStr = if (he) hdfHe.formatHebrewNumber(yr / 1000) + hdfHe.formatHebrewNumber(yr % 1000) else yr.toString()
        val title = if (p.showYear) "$dayFull $month, $yearStr" else "$dayFull $month"

        // Parsha (upcoming Shabbat)
        val sat = dateCal.clone() as Calendar
        while (sat.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) sat.add(Calendar.DATE, 1)
        val jcSat = JewishCalendar(sat)
        jcSat.setInIsrael(p.israel)
        var parshaName = hdf.formatParsha(jcSat)
        val parsha = if (parshaName.isNotBlank()) {
            (if (he) "פרשת " else "Parsha ") + parshaName
        } else {
            val yt = hdf.formatYomTov(jcSat)
            if (yt.isNotBlank()) (if (he) "שבת: " else "Shabbat: ") + yt else ""
        }

        // Next chag / special day
        var chag = ""
        for (i in 0..60) {
            val c = dateCal.clone() as Calendar
            c.add(Calendar.DATE, i)
            val j = JewishCalendar(c)
            j.setInIsrael(p.israel)
            if (j.getYomTovIndex() != -1) {
                val nm = try { hdf.formatYomTov(j) } catch (e: Exception) { "" }
                if (nm.isNotBlank()) {
                    val whenTxt = when (i) {
                        0 -> if (he) "היום" else "Today"
                        1 -> if (he) "מחר" else "Tomorrow"
                        else -> if (he) "בעוד $i ימים" else "in $i days"
                    }
                    chag = "$nm — $whenTxt"
                    break
                }
            }
        }

        // Daf Yomi (by civil date)
        val daf = try {
            val jcCivil = JewishCalendar(civil)
            (if (he) "דף יומי: " else "Daf Yomi: ") + hdf.formatDafYomiBavli(YomiCalculator.getDafYomiBavli(jcCivil))
        } catch (e: Exception) { "" }

        // Candle lighting / Havdalah for the civil day of the Hebrew date shown
        val fmt = SimpleDateFormat("HH:mm", Locale.US)
        fmt.timeZone = tz
        val zd = zc(geo, dateCal, p.candleMin)
        var candle = ""
        try {
            if (jc.hasCandleLighting()) {
                candle = (if (he) "הדלקת נרות: " else "Candle lighting: ") + fmt.format(zd.getCandleLighting())
            } else if (jc.isAssurBemelacha()) {
                candle = (if (he) "הבדלה: " else "Havdalah: ") + fmt.format(zd.getTzaisGeonim8Point5Degrees())
            }
        } catch (e: Exception) { candle = "" }

        // Next zman
        val tomorrow = civil.clone() as Calendar
        tomorrow.add(Calendar.DATE, 1)
        val zt = zc(geo, tomorrow, p.candleMin)
        fun list(z: ComplexZmanimCalendar) = listOf(
            (if (he) "עלות השחר" else "Alot") to z.getAlosHashachar(),
            (if (he) "הנץ" else "Sunrise") to z.getSunrise(),
            (if (he) "סוף זמן שמע" else "Sof zman Shema") to z.getSofZmanShmaGRA(),
            (if (he) "חצות" else "Chatzos") to z.getChatzos(),
            (if (he) "שקיעה" else "Sunset") to (if (p.elevSunset) z.getSunset() else z.getSeaLevelSunset()),
            (if (he) "צאת הכוכבים" else "Nightfall") to z.getTzaisGeonim8Point5Degrees()
        )
        val next = (list(zToday) + list(zt))
            .filter { it.second != null && it.second.after(now) }
            .minByOrNull { it.second.time }
        val nextZman = if (next != null)
            (if (he) "הבא: " else "Next: ") + next.first + " " + fmt.format(next.second) else ""

        // When to refresh next
        val midnight = civil.clone() as Calendar
        midnight.add(Calendar.DATE, 1)
        midnight.set(Calendar.HOUR_OF_DAY, 0)
        midnight.set(Calendar.MINUTE, 0)
        midnight.set(Calendar.SECOND, 30)
        midnight.set(Calendar.MILLISECOND, 0)
        val candidates = mutableListOf(midnight.timeInMillis, now.time + 30 * 60 * 1000L)
        if (flipTime != null && flipTime.after(now)) candidates.add(flipTime.time + 5000L)
        if (next != null) candidates.add(next.second.time + 5000L)
        val nextChange = candidates.minOrNull() ?: (now.time + 30 * 60 * 1000L)

        return Info(iconDay, month, title, parsha, chag, daf, candle, nextZman, nextChange)
    }
}
