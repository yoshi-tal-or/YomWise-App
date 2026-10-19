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
    val omer: String,
    val parsha: String,
    val chag: String,
    val daf: String,
    val candle: String,
    val nextZman: String,
    val nextChange: Long
)

object Calc {
    private fun ordinal(n: Int): String {
        val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
        return "$n$suffix"
    }

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

        // Omer count
        val omerDay = jc.getDayOfOmer()
        val omer = if (omerDay in 1..49) {
            if (he) "עומר: יום ${hdfHe.formatHebrewNumber(omerDay)}" else "Omer: ${ordinal(omerDay)} day"
        } else ""

        // Parsha (upcoming Shabbat), then special Shabbat / Shabbat Mevorchim after commas
        val sat = dateCal.clone() as Calendar
        while (sat.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) sat.add(Calendar.DATE, 1)
        val jcSat = JewishCalendar(sat)
        jcSat.setInIsrael(p.israel)
        val parshaName = hdf.formatParsha(jcSat)
        val parshaBase = if (parshaName.isNotBlank()) {
            (if (he) "פרשת " else "Parsha ") + parshaName
        } else {
            val yt = hdf.formatYomTov(jcSat)
            if (yt.isNotBlank()) (if (he) "שבת: " else "Shabbat: ") + yt else ""
        }
        val sm = jcSat.getJewishMonth()
        val sd = jcSat.getJewishDayOfMonth()
        val leap = jcSat.isJewishLeapYear()
        val adar = if (leap) 13 else 12
        val prevAdar = if (leap) 12 else 11
        var special = when {
            (sm == prevAdar && sd in 25..30) || (sm == adar && sd == 1) -> if (he) "שקלים" else "Shekalim"
            sm == adar && sd in 8..13 -> if (he) "זכור" else "Zachor"
            sm == adar && sd in 17..23 -> if (he) "פרה" else "Parah"
            (sm == adar && sd in 24..29) || (sm == 1 && sd == 1) -> if (he) "החודש" else "HaChodesh"
            sm == 1 && sd in 8..14 -> if (he) "הגדול" else "HaGadol"
            sm == 5 && sd in 4..9 -> if (he) "חזון" else "Chazon"
            sm == 5 && sd in 11..16 -> if (he) "נחמו" else "Nachamu"
            sm == 7 && sd in 3..9 -> if (he) "שובה" else "Shuva"
            else -> ""
        }
        if (special.isEmpty() && (parshaName.contains("בשלח") || parshaName.contains("beshalach", ignoreCase = true))) {
            special = if (he) "שירה" else "Shira"
        }
        var mevorchim = ""
        if (sm != 6 && sd in 23..29) {
            val nx = sat.clone() as Calendar
            nx.add(Calendar.DATE, jcSat.getDaysInJewishMonth() - sd + 1)
            mevorchim = (if (he) "שבת מברכים " else "Shabbat Mevorchim ") + hdf.formatMonth(JewishCalendar(nx))
        }
        val parsha = listOfNotNull(
            parshaBase.takeIf { it.isNotBlank() },
            special.takeIf { it.isNotEmpty() }?.let { (if (he) "שבת " else "Shabbat ") + it },
            mevorchim.takeIf { it.isNotEmpty() }
        ).joinToString(", ")

        // Next chag / special day, including today. Rosh Chodesh is added after a comma if it coincides.
        var chag = ""
        for (i in 0..60) {
            val c = dateCal.clone() as Calendar
            c.add(Calendar.DATE, i)
            val j = JewishCalendar(c)
            j.setInIsrael(p.israel)
            val parts = mutableListOf<String>()
            val ytName = if (j.getYomTovIndex() != -1) {
                try { hdf.formatYomTov(j) } catch (e: Exception) { "" }
            } else ""
            val ytIsRc = ytName.contains("חודש") || ytName.contains("chodesh", ignoreCase = true)
            if (ytName.isNotBlank() && !ytIsRc) {
                var n1 = ytName
                val isHoshana = ytName.contains("הושענא") || ytName.contains("hoshana", ignoreCase = true)
                if (j.isCholHamoed() && !isHoshana) {
                    val n = j.getJewishDayOfMonth() - (if (p.israel) 15 else 16)
                    if (n >= 1) n1 += if (he) " (יום ${hdfHe.formatHebrewNumber(n)})" else " (day $n)"
                }
                parts.add(n1)
            }
            if (j.isRoshChodesh()) {
                val dom = j.getJewishDayOfMonth()
                val monthCal = if (dom == 30) {
                    val c2 = c.clone() as Calendar
                    c2.add(Calendar.DATE, 1)
                    JewishCalendar(c2)
                } else j
                var rc = (if (he) "ראש חודש " else "Rosh Chodesh ") + hdf.formatMonth(monthCal)
                if (dom == 30) {
                    rc += if (he) " (יום א׳)" else " (day 1)"
                } else {
                    val c0 = c.clone() as Calendar
                    c0.add(Calendar.DATE, -1)
                    if (JewishCalendar(c0).getJewishDayOfMonth() == 30) {
                        rc += if (he) " (יום ב׳)" else " (day 2)"
                    }
                }
                parts.add(rc)
            }
            if (parts.isNotEmpty()) {
                val whenTxt = when (i) {
                    0 -> if (he) "היום" else "Today"
                    1 -> if (he) "מחר" else "Tomorrow"
                    else -> if (he) "בעוד $i ימים" else "in $i days"
                }
                chag = parts.joinToString(", ") + " — " + whenTxt
                break
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

        return Info(iconDay, month, title, omer, parsha, chag, daf, candle, nextZman, nextChange)
    }
}
