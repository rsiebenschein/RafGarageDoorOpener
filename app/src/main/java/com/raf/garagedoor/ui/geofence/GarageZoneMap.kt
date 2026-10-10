package com.raf.garagedoor.ui.geofence

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.raf.garagedoor.R
import com.raf.garagedoor.geofence.GeoPoint
import com.raf.garagedoor.geofence.GeofenceDraft
import kotlin.math.cos

/** Zone colors are fixed rather than themed because they sit on map imagery, not app surfaces. */
val OpenZoneColor = Color(0xFF34C759)
val CloseZoneColor = Color(0xFFFFB020)

private val CONTINENTAL_US_CENTER = LatLng(39.5, -98.35)
private const val OVERVIEW_ZOOM = 3.5f
private const val GARAGE_ZOOM = 19f
private const val FIT_RADIUS_MARGIN = 1.25
private const val FIT_PADDING_PX = 48
private const val ZONE_STROKE_WIDTH_PX = 5f
private const val OPEN_ZONE_FILL_ALPHA = 0.2f
private const val METERS_PER_DEGREE_LATITUDE = 111_320.0

@Composable
fun GarageZoneMap(
    draft: GeofenceDraft,
    isSatellite: Boolean,
    showMyLocation: Boolean,
    cameraFitRequest: Int,
    onMapTap: (GeoPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cameraState = rememberCameraPositionState { position = initialCameraPosition(draft.center) }
    var isMapLoaded by remember { mutableStateOf(false) }
    CameraFitEffect(cameraState, draft, cameraFitRequest, isMapLoaded)
    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraState,
        properties = MapProperties(
            mapType = if (isSatellite) MapType.HYBRID else MapType.NORMAL,
            isMyLocationEnabled = showMyLocation,
        ),
        uiSettings = MapUiSettings(myLocationButtonEnabled = false, zoomControlsEnabled = false, mapToolbarEnabled = false),
        onMapClick = { onMapTap(GeoPoint(it.latitude, it.longitude)) },
        onMapLoaded = { isMapLoaded = true },
    ) {
        draft.center?.let { GarageZoneOverlay(it.toLatLng(), draft) }
    }
}

@Composable
private fun CameraFitEffect(cameraState: CameraPositionState, draft: GeofenceDraft, request: Int, isMapLoaded: Boolean) {
    val latestDraft by rememberUpdatedState(draft)
    LaunchedEffect(request, isMapLoaded) {
        val center = latestDraft.center ?: return@LaunchedEffect
        if (!isMapLoaded || request == 0) return@LaunchedEffect
        val bounds = boundsAround(center, latestDraft.closeRadiusMeters * FIT_RADIUS_MARGIN)
        cameraState.animate(CameraUpdateFactory.newLatLngBounds(bounds, FIT_PADDING_PX))
    }
}

@Composable
private fun GarageZoneOverlay(center: LatLng, draft: GeofenceDraft) {
    Circle(
        center = center,
        radius = draft.closeRadiusMeters,
        strokeColor = CloseZoneColor,
        strokeWidth = ZONE_STROKE_WIDTH_PX,
        strokePattern = listOf(Dash(30f), Gap(20f)),
        fillColor = Color.Transparent,
    )
    Circle(
        center = center,
        radius = draft.openRadiusMeters,
        strokeColor = OpenZoneColor,
        strokeWidth = ZONE_STROKE_WIDTH_PX,
        fillColor = OpenZoneColor.copy(alpha = OPEN_ZONE_FILL_ALPHA),
    )
    Marker(state = rememberUpdatedMarkerState(position = center), title = stringResource(R.string.geofence_marker_title))
}

private fun initialCameraPosition(center: GeoPoint?): CameraPosition = center
    ?.let { CameraPosition.fromLatLngZoom(it.toLatLng(), GARAGE_ZOOM) }
    ?: CameraPosition.fromLatLngZoom(CONTINENTAL_US_CENTER, OVERVIEW_ZOOM)

private fun boundsAround(center: GeoPoint, radiusMeters: Double): LatLngBounds {
    val latitudeDelta = radiusMeters / METERS_PER_DEGREE_LATITUDE
    val longitudeDelta = radiusMeters / (METERS_PER_DEGREE_LATITUDE * cos(Math.toRadians(center.latitude)))
    return LatLngBounds(
        LatLng(center.latitude - latitudeDelta, center.longitude - longitudeDelta),
        LatLng(center.latitude + latitudeDelta, center.longitude + longitudeDelta),
    )
}

private fun GeoPoint.toLatLng() = LatLng(latitude, longitude)
