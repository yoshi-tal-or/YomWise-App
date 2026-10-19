package com.example.hebdate

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.util.TimeZone

data class City(val name: String, val lat: Double, val lon: Double, val tz: String, val israel: Boolean, val candle: Int, val elev: Double)

class MainActivity : Activity() {
    private lateinit var p: Prefs
    private lateinit var preview: TextView

    private val cities = listOf(
        City("Jerusalem", 31.7780, 35.2350, "Asia/Jerusalem", true, 40, 754.0),
        City("Tel Aviv", 32.0853, 34.7818, "Asia/Jerusalem", true, 18, 5.0),
        City("Ra'anana", 32.1840, 34.8710, "Asia/Jerusalem", true, 18, 40.0),
        City("Haifa", 32.7940, 34.9896, "Asia/Jerusalem", true, 30, 30.0),
        City("Be'er Sheva", 31.2530, 34.7915, "Asia/Jerusalem", true, 18, 280.0),
        City("Modi'in", 31.8980, 35.0100, "Asia/Jerusalem", true, 18, 230.0),
        City("Beit Shemesh", 31.7470, 34.9880, "Asia/Jerusalem", true, 18, 300.0),
        City("Tzfat", 32.9646, 35.4960, "Asia/Jerusalem", true, 18, 850.0),
        City("Eilat", 29.5577, 34.9519, "Asia/Jerusalem", true, 18, 10.0),
        City("Karnei Shomron", 32.1717, 35.0994, "Asia/Jerusalem", true, 18, 400.0),
        City("New York", 40.7128, -74.0060, "America/New_York", false, 18, 10.0),
        City("Lakewood NJ", 40.0979, -74.2176, "America/New_York", false, 18, 20.0),
        City("Miami", 25.7617, -80.1918, "America/New_York", false, 18, 2.0),
        City("Chicago", 41.8781, -87.6298, "America/Chicago", false, 18, 180.0),
        City("Los Angeles", 34.0522, -118.2437, "America/Los_Angeles", false, 18, 90.0),
        City("Toronto", 43.6532, -79.3832, "America/Toronto", false, 18, 76.0),
        City("London", 51.5072, -0.1276, "Europe/London", false, 18, 11.0),
        City("Paris", 48.8566, 2.3522, "Europe/Paris", false, 18, 35.0),
        City("Melbourne", -37.8136, 144.9631, "Australia/Melbourne", false, 18, 31.0)
    )

    private lateinit var elevE: EditText

    private fun lookupElevation() {
        val la = p.lat
        val lo = p.lon
        Toast.makeText(this, "Looking up elevation...", Toast.LENGTH_SHORT).show()
        Thread {
            val result: Double? = try {
                val url = java.net.URL("https://api.open-meteo.com/v1/elevation?latitude=$la&longitude=$lo")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val txt = conn.inputStream.bufferedReader().use { it.readText() }
                org.json.JSONObject(txt).getJSONArray("elevation").getDouble(0)
            } catch (e: Exception) { null }
            runOnUiThread {
                if (result == null) {
                    Toast.makeText(this, "Lookup failed (no internet?). Type the elevation instead.", Toast.LENGTH_LONG).show()
                } else {
                    val e = Math.round(result).toDouble().coerceAtLeast(0.0)
                    p.elevation = e
                    if (::elevE.isInitialized) elevE.setText(e.toString())
                    changed()
                    Toast.makeText(this, "Elevation set to ${e.toInt()} m", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun changed() {
        Updater.refresh(this)
        refreshPreview()
    }

    private fun refreshPreview() {
        preview.text = try {
            val i = Calc.compute(p)
            (listOf(i.title) + Updater.lines(p, i)).joinToString("\n")
        } catch (e: Exception) {
            "Could not calculate: " + e.message
        }
    }

    private fun LinearLayout.header(t: String) {
        val v = TextView(context)
        v.text = t
        v.textSize = 18f
        v.setTypeface(null, android.graphics.Typeface.BOLD)
        v.setPadding(0, dp(22), 0, dp(4))
        addView(v)
    }

    private fun LinearLayout.check(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
        val c = CheckBox(context)
        c.text = label
        c.isChecked = value
        c.setOnCheckedChangeListener { _, v -> onChange(v); changed() }
        addView(c)
    }

    private fun LinearLayout.spin(label: String, items: List<String>, sel: Int, onPick: (Int) -> Unit) {
        val t = TextView(context)
        t.text = label
        t.setPadding(0, dp(8), 0, 0)
        addView(t)
        val s = Spinner(context)
        s.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, items)
        s.setSelection(sel)
        s.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(a: AdapterView<*>?, v: View?, pos: Int, id: Long) { onPick(pos) }
            override fun onNothingSelected(a: AdapterView<*>?) {}
        }
        addView(s)
    }

    private fun decimalField(hint: String, value: Double): EditText {
        val e = EditText(this)
        e.hint = hint
        e.setText(value.toString())
        e.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        return e
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        p = Prefs(this)

        actionBar?.let {
            it.setIcon(R.drawable.yw_logo)
            it.setDisplayUseLogoEnabled(false)
            it.setDisplayShowHomeEnabled(true)
            it.setDisplayShowTitleEnabled(true)
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(16), dp(16), dp(40))
        val sv = ScrollView(this)
        sv.addView(root)
        setContentView(sv)

        preview = TextView(this)
        preview.textSize = 17f
        preview.setPadding(dp(12), dp(12), dp(12), dp(12))
        preview.setBackgroundColor(0xFFE8EEF8.toInt())
        root.addView(preview)

        // ---- Location ----
        root.header("Location")
        root.spin("City", listOf("Custom / GPS") + cities.map { it.name }, p.cityIdx + 1) { pos ->
            if (pos > 0 && pos - 1 != p.cityIdx) {
                val c = cities[pos - 1]
                p.cityIdx = pos - 1
                p.name = c.name
                p.lat = c.lat
                p.lon = c.lon
                p.tzId = c.tz
                p.israel = c.israel
                p.candleMin = c.candle
                p.elevation = c.elev
                changed()
                recreate()
            }
        }

        val latE = decimalField("Latitude", p.lat)
        val lonE = decimalField("Longitude", p.lon)
        root.addView(latE)
        root.addView(lonE)
        val saveCoords = Button(this)
        saveCoords.text = "Save coordinates"
        saveCoords.setOnClickListener {
            val la = latE.text.toString().toDoubleOrNull()
            val lo = lonE.text.toString().toDoubleOrNull()
            if (la == null || lo == null || la < -90 || la > 90 || lo < -180 || lo > 180) {
                Toast.makeText(this, "Invalid coordinates", Toast.LENGTH_SHORT).show()
            } else {
                p.lat = la; p.lon = lo; p.name = "Custom"; p.cityIdx = -1
                changed()
                Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(saveCoords)
        val gps = Button(this)
        gps.text = "Use my phone's location (GPS)"
        gps.setOnClickListener { useGps() }
        root.addView(gps)

        // ---- Elevation ----
        root.header("Elevation")
        elevE = EditText(this)
        elevE.hint = "Meters above sea level"
        elevE.setText(p.elevation.toString())
        elevE.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        root.addView(elevE)
        val saveElev = Button(this)
        saveElev.text = "Save elevation"
        saveElev.setOnClickListener {
            val e = elevE.text.toString().toDoubleOrNull()
            if (e == null || e < 0 || e > 9000) {
                Toast.makeText(this, "Enter meters above sea level (0 or more)", Toast.LENGTH_SHORT).show()
            } else {
                p.elevation = e
                changed()
                Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(saveElev)
        val lookup = Button(this)
        lookup.text = "Look up elevation online"
        lookup.setOnClickListener { lookupElevation() }
        root.addView(lookup)
        root.check("Also use elevation for sunset", p.elevSunset) { p.elevSunset = it }
        val elevNote = TextView(this)
        elevNote.text = "Elevation makes sunrise (hanetz) earlier. Candle lighting, nightfall and the date change always use sea level. " +
            "The online lookup sends only your coordinates to open-meteo.com, once, when you tap the button."
        elevNote.textSize = 13f
        root.addView(elevNote)

        // ---- Timezone ----
        root.header("Timezone")
        val tzE = EditText(this)
        tzE.setText(p.tzId)
        tzE.hint = "e.g. Asia/Jerusalem"
        root.check("Use the phone's timezone", p.useDeviceTz) { p.useDeviceTz = it }
        root.addView(tzE)
        val saveTz = Button(this)
        saveTz.text = "Save timezone"
        saveTz.setOnClickListener {
            val id = tzE.text.toString().trim()
            if (TimeZone.getAvailableIDs().contains(id)) {
                p.tzId = id
                changed()
                Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Unknown timezone ID", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(saveTz)

        // ---- Calendar rules ----
        root.header("Rules and times")
        root.check("Israel (one day of yom tov)", p.israel) { p.israel = it }
        val mins = listOf(18, 20, 22, 30, 40)
        root.spin("Candle lighting (minutes before sunset)", mins.map { "$it" }, mins.indexOf(p.candleMin).coerceAtLeast(0)) { pos ->
            if (mins[pos] != p.candleMin) { p.candleMin = mins[pos]; changed() }
        }
        root.spin("Hebrew date changes at", listOf("Sunset", "Nightfall (tzeis)", "Midnight"), p.flip) { pos ->
            if (pos != p.flip) { p.flip = pos; changed() }
        }
        root.spin("Language", listOf("עברית", "English"), if (p.hebrew) 0 else 1) { pos ->
            val he = pos == 0
            if (he != p.hebrew) { p.hebrew = he; changed() }
        }

        root.check("Show Hebrew year after the month", p.showYear) { p.showYear = it }

        // ---- Status bar ----
        root.header("Status bar")
        root.check("Show date in status bar", p.notifOn) { p.notifOn = it }
        root.check("Stack month under day in icon", p.stackedIcon) { p.stackedIcon = it }

        // ---- Widget / details ----
        root.header("Show in widget and notification")
        root.check("Omer count (during the Omer)", p.showOmer) { p.showOmer = it }
        root.check("Parsha", p.showParsha) { p.showParsha = it }
        root.check("Upcoming chag / special day", p.showChag) { p.showChag = it }
        root.check("Daf Yomi", p.showDaf) { p.showDaf = it }
        root.check("Candle lighting / Havdalah", p.showCandle) { p.showCandle = it }
        root.check("Next zman", p.showZman) { p.showZman = it }

        val note = TextView(this)
        note.text = "\nTimes are calculated on your phone using the elevation you set. " +
            "Please compare with a trusted luach before relying on them. " +
            "Add the widget from your home screen's widget picker."
        note.textSize = 13f
        root.addView(note)

        refreshPreview()
        Updater.refresh(this)
    }

    private fun useGps() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION), 2)
            return
        }
        val lm = getSystemService(LocationManager::class.java)
        var best: Location? = null
        for (prov in lm.getProviders(true)) {
            val l = try { lm.getLastKnownLocation(prov) } catch (e: SecurityException) { null }
            if (l != null && (best == null || l.time > best.time)) best = l
        }
        val b = best
        if (b == null) {
            Toast.makeText(this, "No location yet. Open a maps app for a moment, then try again, or type coordinates.", Toast.LENGTH_LONG).show()
            return
        }
        p.lat = b.latitude
        p.lon = b.longitude
        p.name = "GPS"
        p.cityIdx = -1
        if (Build.VERSION.SDK_INT >= 34 && b.hasMslAltitude()) {
            p.elevation = Math.round(b.mslAltitudeMeters).toDouble().coerceAtLeast(0.0)
        } else {
            lookupElevation()
        }
        val id = TimeZone.getDefault().id
        p.tzId = id
        p.israel = (id == "Asia/Jerusalem" || id == "Asia/Tel_Aviv")
        changed()
        recreate()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 2 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) useGps()
        if (requestCode == 1) Updater.refresh(this)
    }
}
