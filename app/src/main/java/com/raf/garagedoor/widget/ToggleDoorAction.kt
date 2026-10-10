package com.raf.garagedoor.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.raf.garagedoor.GarageApplication

/** Glance entry point for a tap on the widget's door control. */
class ToggleDoorAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        (context.applicationContext as GarageApplication).container.widgetDoorToggle.onTap()
    }
}
