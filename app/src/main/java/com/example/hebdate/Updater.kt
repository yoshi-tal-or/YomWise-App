package com.example.hebdate

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.view.View
import android.widget.RemoteViews

object Updater {
    const val ACTION = "com.example.hebdate.UPDATE"
    private const val CH = "hebdate"

    fun lines(p: Prefs, i: Info): List<String> = listOfNotNull(
        i.parsha.takeIf { p.showParsha && it.isNotBlank() },
        i.chag.takeIf { p.showChag && it.isNotBlank() },
        i.daf.takeIf { p.showDaf && it.isNotBlank() },
        i.candle.takeIf { p.showCandle && it.isNotBlank() },
        i.nextZman.takeIf { p.showZman && it.isNotBlank() }
    )

    fun refresh(ctx: Context) {
        val p = Prefs(ctx)
        val info = try { Calc.compute(p) } catch (e: Exception) { null } ?: return
        try { notification(ctx, p, info) } catch (e: Exception) { }
        try { widgets(ctx, p, info) } catch (e: Exception) { }
        schedule(ctx, info.nextChange)
    }

    private fun openApp(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun notification(ctx: Context, p: Prefs, i: Info) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!p.notifOn) { nm.cancel(1); return }
        val ch = NotificationChannel(CH, "YomWise date", NotificationManager.IMPORTANCE_LOW)
        ch.setShowBadge(false)
        nm.createNotificationChannel(ch)

        val del = PendingIntent.getBroadcast(
            ctx, 1, Intent(ctx, UpdateReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val body = lines(p, i)
        val bmp = IconMaker.make(i.iconDay, i.iconMonth, p.stackedIcon)
        val n = Notification.Builder(ctx, CH)
            .setSmallIcon(Icon.createWithBitmap(bmp))
            .setContentTitle(i.title)
            .setContentText(body.firstOrNull() ?: "")
            .setStyle(Notification.BigTextStyle().bigText(body.joinToString("\n")))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(openApp(ctx))
            .setDeleteIntent(del)
            .build()
        nm.notify(1, n)
    }

    private fun widgets(ctx: Context, p: Prefs, i: Info) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val specs = listOf(
            DateWidget::class.java to R.layout.widget,
            DateWidget3x2::class.java to R.layout.widget_3x2,
            DateWidget2x2::class.java to R.layout.widget_2x2,
            DateWidget4x1::class.java to R.layout.widget_4x1
        )
        for ((cls, layout) in specs) {
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, cls))
            if (ids.isEmpty()) continue
            val rv = RemoteViews(ctx.packageName, layout)
            rv.setTextViewText(R.id.w_title, i.title)
            if (layout == R.layout.widget_4x1) {
                val text = lines(p, i).take(2).joinToString("  ·  ")
                rv.setTextViewText(R.id.w_compact, text)
                rv.setViewVisibility(R.id.w_compact, if (text.isBlank()) View.GONE else View.VISIBLE)
            } else {
                fun line(id: Int, show: Boolean, text: String) {
                    rv.setTextViewText(id, text)
                    rv.setViewVisibility(id, if (show && text.isNotBlank()) View.VISIBLE else View.GONE)
                }
                line(R.id.w_parsha, p.showParsha, i.parsha)
                line(R.id.w_chag, p.showChag, i.chag)
                line(R.id.w_daf, p.showDaf, i.daf)
                line(R.id.w_candle, p.showCandle, i.candle)
                line(R.id.w_zman, p.showZman, i.nextZman)
            }
            rv.setOnClickPendingIntent(R.id.root, openApp(ctx))
            mgr.updateAppWidget(ids, rv)
        }
    }

    private fun schedule(ctx: Context, at: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, 2, Intent(ctx, UpdateReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }
}
