package com.raf.garagedoor.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.raf.garagedoor.GarageApplication
import com.raf.garagedoor.MainActivity
import com.raf.garagedoor.R
import java.time.Instant

/**
 * Home-screen widget: 1x1 shows an icon and one word, and the whole tile toggles the door; 2x1 and
 * wider add the time of the last change and a separate button, while the rest opens the app.
 * Every toggle needs a second tap within a few seconds, so a stray touch cannot move the door.
 */
class GarageDoorWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(COMPACT_SIZE, WIDE_SIZE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = (context.applicationContext as GarageApplication).container.widgetDoorStatus
        val initial = store.current()
        provideContent {
            val status by store.status.collectAsState(initial)
            WidgetContent(status, Instant.now())
        }
    }

    companion object {
        val COMPACT_SIZE = DpSize(57.dp, 57.dp)
        val WIDE_SIZE = DpSize(180.dp, 57.dp)
    }
}

class GarageDoorWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GarageDoorWidget()

    /** Android's own updates (widget added, every 30 minutes, after a reboot) also fetch a fresh status. */
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        DoorStatusWorker.enqueue(context, follow = null)
    }
}

@Composable
private fun WidgetContent(status: WidgetDoorStatus, now: Instant) {
    val look = WidgetLook.of(LocalContext.current, status, now)
    val toggle = actionRunCallback<ToggleDoorAction>()
    if (LocalSize.current.width < GarageDoorWidget.WIDE_SIZE.width) CompactWidget(look, toggle) else WideWidget(look, toggle)
}

@Composable
private fun CompactWidget(look: WidgetLook, toggle: Action) {
    Box(cardModifier().clickable(toggle), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GarageIcon(look.color)
            Text(look.shortLabel, style = TextStyle(color = ColorProvider(look.color), fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        }
    }
}

@Composable
private fun WideWidget(look: WidgetLook, toggle: Action) {
    val openApp = actionStartActivity<MainActivity>()
    Row(cardModifier().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.clickable(openApp)) { GarageIcon(look.color) }
        Column(GlanceModifier.defaultWeight().padding(horizontal = 10.dp).clickable(openApp)) {
            Text(LocalContext.current.getString(R.string.widget_door_title), style = TextStyle(color = ColorProvider(TITLE_COLOR), fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Text(look.detailLabel, style = TextStyle(color = ColorProvider(look.color), fontSize = 13.sp), maxLines = 1)
        }
        ActionPill(look.actionLabel, toggle)
    }
}

@Composable
private fun ActionPill(label: String, toggle: Action) {
    Box(
        GlanceModifier.background(PILL_COLOR).cornerRadius(18.dp).padding(horizontal = 14.dp, vertical = 8.dp).clickable(toggle),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = TextStyle(color = ColorProvider(TITLE_COLOR), fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1)
    }
}

@Composable
private fun GarageIcon(color: Color) {
    Image(
        provider = ImageProvider(R.drawable.ic_stat_garage),
        contentDescription = null,
        modifier = GlanceModifier.size(30.dp),
        colorFilter = ColorFilter.tint(ColorProvider(color)),
    )
}

private fun cardModifier(): GlanceModifier =
    GlanceModifier.fillMaxSize().background(CARD_COLOR).cornerRadius(20.dp)

private val CARD_COLOR = Color(0xEB132030)
private val PILL_COLOR = Color(0xFF184A85)
private val TITLE_COLOR = Color(0xFFE6ECF3)
