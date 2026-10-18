package com.example.hebdate

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface

object IconMaker {
    private const val S = 96

    fun make(day: String, month: String, stacked: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.WHITE
        p.typeface = Typeface.DEFAULT_BOLD
        if (stacked) {
            fit(c, p, day, 0f, 0f, S.toFloat(), 56f)
            p.typeface = Typeface.DEFAULT
            fit(c, p, month, 0f, 60f, S.toFloat(), 36f)
        } else {
            fit(c, p, day, 0f, 0f, S.toFloat(), S.toFloat())
        }
        return bmp
    }

    private fun fit(c: Canvas, p: Paint, t: String, x: Float, y: Float, w: Float, h: Float) {
        if (t.isBlank()) return
        val m = 4f
        p.textSize = 100f
        val b = Rect()
        p.getTextBounds(t, 0, t.length, b)
        if (b.width() <= 0 || b.height() <= 0) return
        val scale = minOf((w - 2 * m) / b.width(), (h - 2 * m) / b.height())
        p.textSize = 100f * scale
        p.getTextBounds(t, 0, t.length, b)
        val dx = x + w / 2f - b.exactCenterX()
        val dy = y + h / 2f - b.exactCenterY()
        c.drawText(t, dx, dy, p)
    }
}
