package com.raf.garagedoor.ui.door

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.raf.garagedoor.door.DoorState
import com.raf.garagedoor.ui.theme.GaragePalette
import com.raf.garagedoor.ui.theme.LocalGaragePalette

private const val PANEL_COUNT = 4
private const val MOVING_ANIMATION_MILLIS = 12_000
private const val SETTLE_ANIMATION_MILLIS = 600

/**
 * House with a sectional door that rolls up as it opens; travel is animated at roughly real door
 * speed. A door first shown mid-move (e.g. the app opened while the widget closes it) starts from
 * the far end, so the movement is still visible.
 */
@Composable
fun GarageDoorIllustration(state: DoorState, modifier: Modifier = Modifier) {
    val openFraction = remember { Animatable(startFractionFor(state)) }
    LaunchedEffect(state) {
        val duration = if (state.isMoving) MOVING_ANIMATION_MILLIS else SETTLE_ANIMATION_MILLIS
        openFraction.animateTo(openFractionFor(state), tween(durationMillis = duration, easing = LinearEasing))
    }
    val palette = LocalGaragePalette.current
    Canvas(modifier.aspectRatio(1f)) { drawGarage(openFraction.value, palette) }
}

private fun startFractionFor(state: DoorState): Float = when (state) {
    DoorState.CLOSING -> 1f
    DoorState.OPENING -> 0f
    else -> openFractionFor(state)
}

private fun openFractionFor(state: DoorState): Float = when (state) {
    DoorState.OPEN, DoorState.OPENING -> 1f
    DoorState.STOPPED, DoorState.MOVING -> 0.5f
    DoorState.CLOSED, DoorState.CLOSING, DoorState.UNKNOWN -> 0f
}

private fun DrawScope.drawGarage(openFraction: Float, palette: GaragePalette) {
    val opening = garageOpening()
    drawHouse(palette.house)
    drawRect(palette.garageInterior, opening.topLeft, opening.size)
    drawDoor(opening, openFraction, palette)
}

private fun DrawScope.drawHouse(color: Color) {
    val width = size.width
    val height = size.height
    val outline = Path().apply {
        moveTo(width * 0.5f, height * 0.06f)
        lineTo(width * 0.96f, height * 0.38f)
        lineTo(width * 0.96f, height * 0.96f)
        lineTo(width * 0.04f, height * 0.96f)
        lineTo(width * 0.04f, height * 0.38f)
        close()
    }
    drawPath(outline, color)
}

private fun DrawScope.garageOpening(): Rect =
    Rect(left = size.width * 0.2f, top = size.height * 0.46f, right = size.width * 0.8f, bottom = size.height * 0.96f)

private fun DrawScope.drawDoor(opening: Rect, openFraction: Float, palette: GaragePalette) {
    val visibleHeight = opening.height * (1f - openFraction)
    if (visibleHeight <= 0f) return
    drawRect(palette.door, opening.topLeft, Size(opening.width, visibleHeight))
    drawPanelSeams(opening, visibleHeight, palette.doorSeam)
}

/** Seams are anchored to the door's bottom edge so panels appear to slide up into the ceiling. */
private fun DrawScope.drawPanelSeams(opening: Rect, visibleHeight: Float, color: Color) {
    val panelHeight = opening.height / PANEL_COUNT
    val strokeWidth = size.minDimension * 0.012f
    val doorBottom = opening.top + visibleHeight
    for (panel in 1 until PANEL_COUNT) {
        val seamY = doorBottom - panel * panelHeight
        if (seamY <= opening.top) break
        drawLine(color, Offset(opening.left, seamY), Offset(opening.right, seamY), strokeWidth)
    }
}
