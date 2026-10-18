package com.example.hebdate

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

open class DateWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Updater.refresh(context)
    }

    override fun onEnabled(context: Context) {
        Updater.refresh(context)
    }
}

class DateWidget3x2 : DateWidget()
class DateWidget2x2 : DateWidget()
class DateWidget4x1 : DateWidget()
