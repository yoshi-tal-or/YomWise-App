package com.example.hebdate

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun gb(k: String, d: Boolean) = sp.getBoolean(k, d)
    private fun sb(k: String, v: Boolean) { sp.edit().putBoolean(k, v).apply() }
    private fun gi(k: String, d: Int) = sp.getInt(k, d)
    private fun si(k: String, v: Int) { sp.edit().putInt(k, v).apply() }
    private fun gs(k: String, d: String) = sp.getString(k, d) ?: d
    private fun ss(k: String, v: String) { sp.edit().putString(k, v).apply() }

    var lat: Double
        get() = gs("lat", "32.0853").toDouble()
        set(v) = ss("lat", v.toString())
    var lon: Double
        get() = gs("lon", "34.7818").toDouble()
        set(v) = ss("lon", v.toString())
    var name: String
        get() = gs("name", "Tel Aviv")
        set(v) = ss("name", v)
    var cityIdx: Int
        get() = gi("cityIdx", 1)
        set(v) = si("cityIdx", v)
    var tzId: String
        get() = gs("tzId", "Asia/Jerusalem")
        set(v) = ss("tzId", v)
    var useDeviceTz: Boolean
        get() = gb("useDeviceTz", false)
        set(v) = sb("useDeviceTz", v)
    var israel: Boolean
        get() = gb("israel", true)
        set(v) = sb("israel", v)
    var candleMin: Int
        get() = gi("candleMin", 18)
        set(v) = si("candleMin", v)
    var hebrew: Boolean
        get() = gb("hebrew", true)
        set(v) = sb("hebrew", v)
    var showYear: Boolean
        get() = gb("showYear", false)
        set(v) = sb("showYear", v)
    /** 0 = sunset, 1 = nightfall, 2 = midnight */
    var flip: Int
        get() = gi("flip", 0)
        set(v) = si("flip", v)
    var showParsha: Boolean
        get() = gb("showParsha", true)
        set(v) = sb("showParsha", v)
    var showChag: Boolean
        get() = gb("showChag", true)
        set(v) = sb("showChag", v)
    var showDaf: Boolean
        get() = gb("showDaf", true)
        set(v) = sb("showDaf", v)
    var showCandle: Boolean
        get() = gb("showCandle", true)
        set(v) = sb("showCandle", v)
    var showZman: Boolean
        get() = gb("showZman", true)
        set(v) = sb("showZman", v)
    var notifOn: Boolean
        get() = gb("notifOn", true)
        set(v) = sb("notifOn", v)
    var stackedIcon: Boolean
        get() = gb("stackedIcon", true)
        set(v) = sb("stackedIcon", v)
}
